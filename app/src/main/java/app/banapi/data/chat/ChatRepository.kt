package app.banapi.data.chat

import app.banapi.data.GeminiApi
import app.banapi.data.GenerationError
import app.banapi.data.GenerationException
import app.banapi.data.GenerationTurn
import app.banapi.data.ImageStore
import app.banapi.data.Pricing
import app.banapi.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Un mensaje listo para mostrar. */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val status: MessageStatus,
    val text: String,
    val error: GenerationError?,
    val errorDetail: String?,
    val tokens: Int,
    val costMicros: Long,
    val images: List<ChatImage>,
)

/** [exists] es false si la imagen se borró desde la galería. */
data class ChatImage(val file: File, val aspectRatio: Float, val exists: Boolean)

/**
 * Chats guardados en el teléfono y generación de imágenes.
 * Las generaciones corren en [scope] (de la app) para no cortarse al cambiar de pantalla.
 */
class ChatRepository(
    private val dao: ChatDao,
    private val imageStore: ImageStore,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val jobs = ConcurrentHashMap<Long, Job>()

    private val _generating = MutableStateFlow<Set<Long>>(emptySet())

    /** Chats que están generando en este momento. */
    val generating: StateFlow<Set<Long>> = _generating.asStateFlow()

    // Al abrir la app: se marcan como interrumpidas las generaciones que quedaron a medias
    // y se borran los adjuntos que no llegaron a enviarse.
    private val ready = scope.async {
        dao.failPendingMessages()
        imageStore.deleteAttachmentsExcept(dao.allImagePaths().toSet())
    }

    val chats: Flow<List<ChatEntity>> = dao.observeChats()

    val chatCount: Flow<Int> = dao.observeChatCount()

    /** Gasto y cantidad de imágenes desde [since] (milisegundos). */
    fun usageSince(since: Long): Flow<UsageSummary> = dao.observeUsage(since)

    suspend fun chatIdForImage(file: File): Long? = dao.chatIdForImage(imageStore.pathOf(file))

    suspend fun latestChatId(): Long? = dao.latestChatId()

    fun chat(id: Long): Flow<ChatEntity?> = dao.observeChat(id)

    /** Se actualiza también cuando cambia la galería, para mostrar las imágenes borradas. */
    fun messages(chatId: Long): Flow<List<ChatMessage>> =
        combine(dao.observeMessages(chatId), imageStore.images) { messages, _ ->
            messages.map { it.toChatMessage() }
        }.flowOn(Dispatchers.IO)

    /**
     * Guarda el mensaje del usuario y empieza a generar. Devuelve el id del chat:
     * si [chatId] es null se crea uno nuevo con [newChatOptions].
     */
    suspend fun send(
        chatId: Long?,
        text: String,
        attachments: List<File>,
        marked: Set<File>,
        newChatOptions: ChatOptions,
    ): Long = withContext(Dispatchers.IO) {
        ready.await()
        val now = System.currentTimeMillis()
        // Si el chat se borró (por ejemplo, desde Ajustes), se crea uno nuevo.
        val id = chatId?.takeIf { dao.getChat(it) != null } ?: dao.insertChat(
            ChatEntity(title = titleFrom(text), createdAt = now, updatedAt = now, options = newChatOptions),
        )
        val messageId = dao.insertMessage(
            MessageEntity(chatId = id, role = Role.User, status = MessageStatus.Done, text = text.trim(), createdAt = now),
        )
        dao.insertImages(
            attachments.mapIndexed { index, file ->
                val origin = if (file in marked) ImageOrigin.Marked else ImageOrigin.Attachment
                imageEntity(messageId, file, origin, index)
            },
        )
        startGeneration(id)
        id
    }

    /** Borra la respuesta fallida y la vuelve a pedir. */
    fun retry(chatId: Long, messageId: Long) {
        if (jobs.containsKey(chatId)) return
        scope.launch {
            dao.deleteMessage(messageId)
            startGeneration(chatId)
        }
    }

    fun cancel(chatId: Long) {
        jobs[chatId]?.cancel()
    }

    fun updateOptions(chatId: Long, options: ChatOptions) {
        scope.launch {
            dao.getChat(chatId)?.let { dao.updateChat(it.copy(options = options)) }
        }
    }

    fun rename(chatId: Long, title: String) {
        scope.launch { dao.renameChat(chatId, title.trim()) }
    }

    /** Borra todos los chats y lo que se adjuntó en ellos. Las imágenes generadas quedan en la galería. */
    fun deleteAll() {
        jobs.values.forEach { it.cancel() }
        scope.launch {
            dao.deleteAllChats()
            imageStore.deleteAttachmentsExcept(emptySet())
        }
    }

    /** Lo que se adjuntó se borra siempre; las imágenes generadas, solo si se pide. */
    fun delete(chatId: Long, deleteGeneratedImages: Boolean) {
        cancel(chatId)
        scope.launch {
            val chatImages = dao.imagesOfChat(chatId)
            dao.deleteChat(chatId)
            withContext(Dispatchers.IO) {
                chatImages
                    .filter { imageStore.isAttachment(it.path) || (deleteGeneratedImages && it.origin == ImageOrigin.Generated) }
                    .forEach { imageStore.fileOf(it.path).delete() }
            }
            imageStore.refresh()
        }
    }

    private fun startGeneration(chatId: Long) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val now = System.currentTimeMillis()
            val pending = MessageEntity(chatId = chatId, role = Role.Model, status = MessageStatus.Pending, createdAt = now)
            val message = pending.copy(id = dao.insertMessage(pending))
            dao.touchChat(chatId, now)
            try {
                generate(chatId, message)
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    dao.updateMessage(message.copy(status = MessageStatus.Failed, error = GenerationError.Cancelled))
                }
                throw e
            } catch (e: GenerationException) {
                dao.updateMessage(message.copy(status = MessageStatus.Failed, error = e.error, errorDetail = e.detail))
            } catch (_: Exception) {
                dao.updateMessage(message.copy(status = MessageStatus.Failed, error = GenerationError.Failed))
            } finally {
                if (jobs.remove(chatId, coroutineContext.job)) _generating.update { it - chatId }
            }
        }
        jobs[chatId] = job
        _generating.update { it + chatId }
        job.start()
    }

    private suspend fun generate(chatId: Long, message: MessageEntity) {
        val apiKey = settings.apiKey.first() ?: throw GenerationException(GenerationError.NoApiKey)
        val chat = dao.getChat(chatId) ?: return
        val history = dao.getMessages(chatId).filter { it.message.id != message.id }

        val result = GeminiApi.generate(apiKey, turnsFrom(history), chat.options)

        val saved = result.images.map { imageStore.saveGenerated(it.bytes, it.mimeType) }
        val outputTokens = result.textOutputTokens + result.imageOutputTokens
        val cost = Pricing.costMicros(result.inputTokens, result.textOutputTokens, result.imageOutputTokens)
        dao.insertUsage(
            UsageEntity(
                createdAt = System.currentTimeMillis(),
                inputTokens = result.inputTokens,
                outputTokens = outputTokens,
                imageCount = saved.size,
                costMicros = cost,
            ),
        )
        dao.updateMessage(
            message.copy(
                status = MessageStatus.Done,
                text = result.text,
                inputTokens = result.inputTokens,
                outputTokens = outputTokens,
                costMicros = cost,
            ),
        )
        dao.insertImages(
            saved.mapIndexed { index, file -> imageEntity(message.id, file, ImageOrigin.Generated, index) },
        )
        imageStore.refresh()
    }

    /**
     * Arma el historial que se envía, como en AI Studio: todo el texto del chat y las imágenes
     * de los últimos mensajes. Las imágenes más viejas no se reenvían, para no gastar de más.
     */
    private fun turnsFrom(history: List<MessageWithImages>): List<GenerationTurn> {
        val valid = history.filter { it.message.role == Role.User || it.message.status == MessageStatus.Done }
        val lastUser = valid.lastOrNull { it.message.role == Role.User }

        // Se recorre desde el final para quedarse con las imágenes más nuevas si hay demasiadas.
        var imageBudget = GeminiApi.MAX_INPUT_IMAGES
        val filesByMessage = valid.takeLast(HISTORY_IMAGE_MESSAGES).asReversed().associate { item ->
            val files = item.images
                .sortedBy { it.position }
                .map { imageStore.fileOf(it.path) }
                .filter { it.exists() }
                .take(imageBudget)
            imageBudget -= files.size
            item.message.id to files
        }

        val turns = mutableListOf<GenerationTurn>()
        valid.forEach { item ->
            val message = item.message
            val files = filesByMessage[message.id].orEmpty()
            val text = when {
                message.role == Role.User -> message.text
                message.text.isNotBlank() -> message.text
                files.isEmpty() && item.images.isNotEmpty() -> GENERATED_IMAGE_NOTE
                else -> ""
            }
            val hasMarks = item === lastUser && item.images.any { it.origin == ImageOrigin.Marked }
            val fullText = if (hasMarks) listOf(text, MARKS_NOTE).filter { it.isNotBlank() }.joinToString("\n\n") else text

            // La API espera que se alternen usuario y modelo: se unen los seguidos del mismo rol.
            val previous = turns.lastOrNull()
            if (previous != null && previous.role == message.role) {
                val joined = listOf(previous.text, fullText).filter { it.isNotBlank() }.joinToString("\n\n")
                turns[turns.lastIndex] = previous.copy(text = joined, images = previous.images + files)
            } else {
                turns += GenerationTurn(message.role, fullText, files)
            }
        }
        return turns
    }

    private fun MessageWithImages.toChatMessage(): ChatMessage = ChatMessage(
        id = message.id,
        role = message.role,
        status = message.status,
        text = message.text,
        error = message.error,
        errorDetail = message.errorDetail,
        tokens = message.inputTokens + message.outputTokens,
        costMicros = message.costMicros,
        images = images.sortedBy { it.position }.map { image ->
            val file = imageStore.fileOf(image.path)
            val ratio = if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else 1f
            ChatImage(file = file, aspectRatio = ratio, exists = file.exists())
        },
    )

    private fun imageEntity(messageId: Long, file: File, origin: ImageOrigin, position: Int): MessageImageEntity {
        val (width, height) = imageStore.sizeOf(file)
        return MessageImageEntity(
            messageId = messageId,
            path = imageStore.pathOf(file),
            origin = origin,
            width = width,
            height = height,
            position = position,
        )
    }

    private fun titleFrom(text: String): String {
        val firstLine = text.trim().lineSequence().firstOrNull().orEmpty()
        return if (firstLine.length <= TITLE_MAX_LENGTH) firstLine else firstLine.take(TITLE_MAX_LENGTH).trimEnd() + "…"
    }

    private companion object {
        const val TITLE_MAX_LENGTH = 40

        // Mensajes recientes cuyas imágenes se reenvían (3 pedidos con sus respuestas).
        const val HISTORY_IMAGE_MESSAGES = 6

        // Lo que ve el modelo en lugar de una imagen vieja que ya no se reenvía.
        const val GENERATED_IMAGE_NOTE = "[Imagen generada]"

        // Se agrega cuando el usuario marcó una imagen con el dedo.
        const val MARKS_NOTE =
            "Las marcas dibujadas a mano sobre la imagen son solo indicaciones: no las incluyas en el resultado."
    }
}
