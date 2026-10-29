package app.banapi.ui.create

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.banapi.BanapiApplication
import app.banapi.R
import app.banapi.data.GalleryImage
import app.banapi.data.GeminiApi
import app.banapi.data.ImageStore
import app.banapi.data.SettingsRepository
import app.banapi.data.chat.ChatEntity
import app.banapi.data.chat.ChatMessage
import app.banapi.data.chat.ChatOptions
import app.banapi.data.chat.ChatRepository
import app.banapi.data.chat.MessageStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Un chat en la lista lateral. */
data class ChatSummary(val id: Long, val title: String, val isGenerating: Boolean)

data class CreateUiState(
    val isLoaded: Boolean = false,
    val chatId: Long? = null,
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),
    /** Imágenes que se van a enviar con el próximo mensaje. */
    val attachments: List<File> = emptyList(),
    val hasApiKey: Boolean = true,
    val showLongChatNotice: Boolean = false,
    val options: ChatOptions = ChatOptions(),
    /** Cuándo se abrió el chat: lo que llega después se muestra animado. */
    val openedAt: Long = 0,
) {
    /** Tokens y gasto de todo el chat. */
    val totalTokens: Int get() = messages.sumOf { it.tokens }
    val totalCostMicros: Long get() = messages.sumOf { it.costMicros }

    val isGenerating: Boolean get() = messages.any { it.status == MessageStatus.Pending }
}

/** A partir de esta cantidad de mensajes se sugiere empezar un chat nuevo. */
private const val LONG_CHAT_MESSAGES = 30

const val MAX_ATTACHMENTS = GeminiApi.MAX_INPUT_IMAGES

private data class Flags(
    val isLoaded: Boolean,
    val hasApiKey: Boolean,
    val longChatDismissed: Set<Long>,
    val openedAt: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CreateViewModel(
    private val chats: ChatRepository,
    private val store: ImageStore,
    settings: SettingsRepository,
) : ViewModel() {

    /** null = chat nuevo, todavía sin mensajes. */
    private val chatId = MutableStateFlow<Long?>(null)
    private val isLoaded = MutableStateFlow(false)
    private val openedAt = MutableStateFlow(System.currentTimeMillis())
    private val attachments = MutableStateFlow<List<File>>(emptyList())

    // Adjuntos que son copias marcadas con el dedo.
    private var markedFiles = emptySet<File>()

    // Opciones elegidas antes de enviar el primer mensaje de un chat nuevo.
    private val newChatOptions = MutableStateFlow(ChatOptions())

    // Chats en los que el usuario cerró el aviso de "chat largo".
    private val longChatDismissed = MutableStateFlow<Set<Long>>(emptySet())

    private val messages: Flow<List<ChatMessage>> = chatId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else chats.messages(id) }

    private val chat: Flow<ChatEntity?> = chatId
        .flatMapLatest { id -> if (id == null) flowOf(null) else chats.chat(id) }

    private val hasApiKey: Flow<Boolean> = settings.apiKey.map { it != null }

    private val flags: Flow<Flags> = combine(isLoaded, hasApiKey, longChatDismissed, openedAt, ::Flags)

    val uiState: StateFlow<CreateUiState> = combine(
        flags,
        chat,
        messages,
        attachments,
        newChatOptions,
    ) { (isLoaded, hasApiKey, dismissed, openedAt), chat, messages, attachments, newChatOptions ->
        CreateUiState(
            isLoaded = isLoaded,
            chatId = chat?.id,
            title = chat?.title.orEmpty(),
            messages = messages,
            attachments = attachments,
            hasApiKey = hasApiKey,
            showLongChatNotice = chat != null && chat.id !in dismissed && messages.size >= LONG_CHAT_MESSAGES,
            options = chat?.options ?: newChatOptions,
            openedAt = openedAt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreateUiState())

    val chatList: StateFlow<List<ChatSummary>> = combine(chats.chats, chats.generating) { list, generating ->
        list.map { ChatSummary(it.id, it.title, it.id in generating) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val galleryImages: StateFlow<List<GalleryImage>> = store.images

    private val _messages = Channel<Int>(Channel.BUFFERED)

    /** Avisos cortos para mostrar en un snackbar (ids de strings). */
    val snackbarMessages: Flow<Int> = _messages.receiveAsFlow()

    init {
        // Al abrir la app se retoma el último chat.
        viewModelScope.launch {
            chatId.value = chats.latestChatId()
            isLoaded.value = true
        }
    }

    /** Devuelve false si no había nada para enviar. */
    fun send(text: String): Boolean {
        val files = attachments.value
        if ((text.isBlank() && files.isEmpty()) || uiState.value.isGenerating) return false
        val marked = markedFiles
        attachments.value = emptyList()
        markedFiles = emptySet()
        viewModelScope.launch {
            chatId.value = chats.send(chatId.value, text, files, marked, newChatOptions.value)
        }
        return true
    }

    fun stop() {
        chatId.value?.let(chats::cancel)
    }

    fun retry(messageId: Long) {
        chatId.value?.let { chats.retry(it, messageId) }
    }

    fun removeAttachment(file: File) {
        attachments.value -= file
    }

    /** Imágenes elegidas del teléfono: se copian a la app antes de adjuntarlas. */
    fun attachFromPhone(uris: List<Uri>) {
        viewModelScope.launch {
            val files = uris.mapNotNull { store.importAttachment(it) }
            if (files.size < uris.size) _messages.send(R.string.create_attach_failed)
            attach(files)
        }
    }

    /** Imágenes de la galería o "Editar" en una imagen del chat. */
    fun attach(files: List<File>) {
        val current = attachments.value
        val added = (current + files).distinct()
        if (added.size > MAX_ATTACHMENTS) {
            viewModelScope.launch { _messages.send(R.string.create_attach_limit) }
        }
        attachments.value = added.take(MAX_ATTACHMENTS)
    }

    suspend fun loadForMarkup(file: File): Bitmap = store.loadForMarkup(file)

    /** Reemplaza el adjunto por su copia marcada, en el mismo lugar. */
    fun saveMarked(original: File, bitmap: Bitmap) {
        viewModelScope.launch {
            val marked = store.saveMarked(bitmap)
            bitmap.recycle()
            markedFiles = markedFiles + marked
            attachments.update { list -> list.map { if (it == original) marked else it } }
        }
    }

    fun refreshGallery() {
        viewModelScope.launch { store.refresh() }
    }

    fun newChat() = openChat(null)

    fun updateOptions(options: ChatOptions) {
        val id = chatId.value
        if (id == null) newChatOptions.value = options else chats.updateOptions(id, options)
    }

    fun openChat(id: Long?) {
        if (id != null && id == chatId.value) return
        chatId.value = id
        openedAt.value = System.currentTimeMillis()
        attachments.value = emptyList()
        markedFiles = emptySet()
        newChatOptions.value = ChatOptions()
    }

    fun renameChat(id: Long, title: String) {
        if (title.isNotBlank()) chats.rename(id, title)
    }

    fun deleteChat(id: Long, deleteGeneratedImages: Boolean) {
        if (id == chatId.value) newChat()
        chats.delete(id, deleteGeneratedImages)
        viewModelScope.launch { _messages.send(R.string.msg_chat_deleted) }
    }

    fun dismissLongChatNotice() {
        chatId.value?.let { id -> longChatDismissed.update { it + id } }
    }

    fun saveToDevice(file: File) {
        viewModelScope.launch {
            val saved = store.saveToDevice(file)
            _messages.send(if (saved) R.string.msg_image_saved else R.string.msg_image_save_failed)
        }
    }

    fun delete(file: File) {
        attachments.value -= file
        viewModelScope.launch {
            store.delete(file)
            _messages.send(R.string.msg_image_deleted)
        }
    }

    fun shareIntent(file: File): Intent = store.shareIntent(file)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BanapiApplication
                CreateViewModel(app.chats, app.images, app.settings)
            }
        }
    }
}
