package app.banapi.data

import android.util.Base64
import app.banapi.data.chat.ChatOptions
import app.banapi.data.chat.ResponseMode
import app.banapi.data.chat.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

enum class KeyCheckResult { Valid, Invalid, NoConnection, Failed }

/** Motivo por el que no se pudo generar; se guarda en el mensaje para mostrarlo en el chat. */
enum class GenerationError {
    NoApiKey, InvalidKey, NoConnection, Timeout, QuotaExceeded, NoCredits, Blocked, NoImage, ServerError, Failed,
    Interrupted, Cancelled,
}

class GenerationException(val error: GenerationError, val detail: String? = null) : Exception(detail)

/** Una vuelta de la conversación. Las imágenes son archivos locales. */
data class GenerationTurn(val role: Role, val text: String, val images: List<File> = emptyList())

class GeneratedImage(val mimeType: String, val bytes: ByteArray)

class GenerationResult(
    val text: String,
    val images: List<GeneratedImage>,
    val inputTokens: Int,
    val textOutputTokens: Int, // texto y razonamiento
    val imageOutputTokens: Int,
)

/** Llamadas directas a la Gemini API, sin servidores intermedios. */
object GeminiApi {
    /** Máximo de imágenes que acepta Nano Banana 2.1 en un pedido. */
    const val MAX_INPUT_IMAGES = 14

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    private const val IMAGE_MODEL = "gemini-nano-banana-2.1"
    private const val TIMEOUT_MS = 15_000
    private const val GENERATION_TIMEOUT_MS = 180_000

    // Las vueltas del modelo se reenvían solo como texto, sin su firma original.
    // Google documenta este valor para que la API no rechace esas vueltas.
    private const val SKIP_SIGNATURE = "skip_thought_signature_validator"

    /** Comprueba que Google acepte la key pidiendo la lista de modelos (no consume cuota de imágenes). */
    suspend fun checkApiKey(apiKey: String): KeyCheckResult = withContext(Dispatchers.IO) {
        val connection = URL("$BASE_URL/models?pageSize=1").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("x-goog-api-key", apiKey)
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> KeyCheckResult.Valid
                HttpURLConnection.HTTP_BAD_REQUEST,
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> KeyCheckResult.Invalid
                else -> KeyCheckResult.Failed
            }
        } catch (_: IOException) {
            KeyCheckResult.NoConnection
        } finally {
            connection.disconnect()
        }
    }

    /** Genera con Nano Banana 2.1. Lanza [GenerationException] si falla. */
    suspend fun generate(
        apiKey: String,
        turns: List<GenerationTurn>,
        options: ChatOptions,
    ): GenerationResult = withContext(Dispatchers.IO) {
        val body = buildRequest(turns, options).toByteArray()
        val connection = URL("$BASE_URL/models/$IMAGE_MODEL:generateContent").openConnection() as HttpURLConnection

        coroutineScope {
            // Si se cancela (botón Detener), cortar la conexión destraba la lectura.
            val watcher = launch {
                try {
                    awaitCancellation()
                } finally {
                    connection.disconnect()
                }
            }
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = GENERATION_TIMEOUT_MS
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-goog-api-key", apiKey)
                connection.outputStream.use { it.write(body) }

                val code = connection.responseCode
                val ok = code in 200..299
                val response = (if (ok) connection.inputStream else connection.errorStream)
                    ?.use { it.readBytes().decodeToString() }
                    .orEmpty()
                if (!ok) throw httpError(code, response)
                parseResponse(response)
            } catch (_: SocketTimeoutException) {
                ensureActive()
                throw GenerationException(GenerationError.Timeout)
            } catch (_: IOException) {
                ensureActive()
                throw GenerationException(GenerationError.NoConnection)
            } catch (_: JSONException) {
                throw GenerationException(GenerationError.Failed)
            } finally {
                watcher.cancel()
            }
        }
    }

    private fun buildRequest(turns: List<GenerationTurn>, options: ChatOptions): String {
        val contents = JSONArray()
        turns.forEach { turn ->
            val parts = JSONArray()
            if (turn.text.isNotBlank()) parts.put(JSONObject().put("text", turn.text))
            turn.images.forEach { file ->
                val image = JSONObject()
                    .put("mimeType", ImageEncoder.MIME_TYPE)
                    .put("data", ImageEncoder.toBase64(file))
                parts.put(JSONObject().put("inlineData", image))
            }
            if (parts.length() == 0) return@forEach
            if (turn.role == Role.Model) parts.getJSONObject(0).put("thoughtSignature", SKIP_SIGNATURE)
            contents.put(
                JSONObject()
                    .put("role", if (turn.role == Role.User) "user" else "model")
                    .put("parts", parts),
            )
        }

        val config = JSONObject().put(
            "responseModalities",
            JSONArray(
                when (options.responseMode) {
                    ResponseMode.ImagesOnly -> listOf("IMAGE")
                    ResponseMode.ImagesAndText -> listOf("TEXT", "IMAGE")
                },
            ),
        )
        val imageConfig = JSONObject()
        options.aspectRatio?.let { imageConfig.put("aspectRatio", it) }
        options.imageSize?.let { imageConfig.put("imageSize", it) }
        if (imageConfig.length() > 0) config.put("imageConfig", imageConfig)
        options.thinkingLevel?.let { config.put("thinkingConfig", JSONObject().put("thinkingLevel", it)) }

        val request = JSONObject()
            .put("contents", contents)
            .put("generationConfig", config)
        if (options.instructions.isNotBlank()) {
            val text = JSONObject().put("text", options.instructions)
            request.put("systemInstruction", JSONObject().put("parts", JSONArray().put(text)))
        }
        if (options.googleSearch) {
            request.put("tools", JSONArray().put(JSONObject().put("googleSearch", JSONObject())))
        }
        return request.toString()
    }

    private fun parseResponse(response: String): GenerationResult {
        val root = JSONObject(response)
        root.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }?.let {
            throw GenerationException(GenerationError.Blocked, it)
        }

        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
        val parts = candidate?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val texts = mutableListOf<String>()
        val images = mutableListOf<GeneratedImage>()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            // Los borradores que el modelo genera mientras piensa no son parte del resultado.
            if (part.optBoolean("thought")) continue
            part.optString("text").takeIf { it.isNotBlank() }?.let { texts += it.trim() }
            part.optJSONObject("inlineData")?.let { data ->
                images += GeneratedImage(
                    mimeType = data.optString("mimeType", "image/png"),
                    bytes = Base64.decode(data.getString("data"), Base64.DEFAULT),
                )
            }
        }

        if (images.isEmpty() && texts.isEmpty()) {
            val reason = candidate?.optString("finishReason").orEmpty()
            val error = if (reason.isEmpty() || reason == "STOP") GenerationError.NoImage else GenerationError.Blocked
            throw GenerationException(error, reason.takeIf { error == GenerationError.Blocked })
        }

        // Las imágenes se cobran más caras que el texto, así que se separan.
        val usage = root.optJSONObject("usageMetadata") ?: JSONObject()
        val candidatesTokens = usage.optInt("candidatesTokenCount")
        val details = usage.optJSONArray("candidatesTokensDetails")
        val imageTokens = if (details != null) {
            (0 until details.length())
                .map { details.getJSONObject(it) }
                .filter { it.optString("modality") == "IMAGE" }
                .sumOf { it.optInt("tokenCount") }
        } else if (images.isNotEmpty()) {
            candidatesTokens
        } else {
            0
        }
        return GenerationResult(
            text = texts.joinToString("\n\n"),
            images = images,
            inputTokens = usage.optInt("promptTokenCount"),
            textOutputTokens = candidatesTokens - imageTokens + usage.optInt("thoughtsTokenCount"),
            imageOutputTokens = imageTokens,
        )
    }

    private fun httpError(code: Int, response: String): GenerationException {
        val error = runCatching { JSONObject(response).getJSONObject("error") }.getOrNull()
        val message = error?.optString("message")?.takeIf { it.isNotBlank() }
        val type = when {
            code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN ->
                GenerationError.InvalidKey
            code == HttpURLConnection.HTTP_BAD_REQUEST && message?.contains("API key", ignoreCase = true) == true ->
                GenerationError.InvalidKey
            // Sin saldo prepago Google responde 402, y a veces 429 con un mensaje sobre créditos.
            code == 402 -> GenerationError.NoCredits
            code == 429 && message?.contains("credit", ignoreCase = true) == true -> GenerationError.NoCredits
            code == 429 -> GenerationError.QuotaExceeded
            code >= 500 -> GenerationError.ServerError
            else -> GenerationError.Failed
        }
        return GenerationException(type, message)
    }
}
