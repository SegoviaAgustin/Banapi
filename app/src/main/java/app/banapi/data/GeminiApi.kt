package app.banapi.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class KeyCheckResult { Valid, Invalid, NoConnection, Failed }

/** Llamadas directas a la Gemini API, sin servidores intermedios. */
object GeminiApi {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    private const val TIMEOUT_MS = 15_000

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
}
