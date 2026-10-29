package app.banapi.ui.gallery

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.banapi.BanapiApplication
import app.banapi.R
import app.banapi.data.GalleryImage
import app.banapi.data.ImageStore
import app.banapi.data.chat.ChatRepository
import app.banapi.ui.components.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File

class GalleryViewModel(
    private val store: ImageStore,
    private val chats: ChatRepository,
) : ViewModel() {

    val images: StateFlow<List<GalleryImage>> = store.images

    private val _messages = Channel<UiText>(Channel.BUFFERED)

    /** Avisos cortos para mostrar en un snackbar. */
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { store.refresh() }
    }

    fun saveToDevice(files: List<File>) {
        viewModelScope.launch {
            val saved = files.count { store.saveToDevice(it) }
            _messages.send(
                when {
                    saved < files.size -> UiText(R.string.msg_image_save_failed)
                    saved == 1 -> UiText(R.string.msg_image_saved)
                    else -> UiText(R.plurals.msg_images_saved_count, saved)
                },
            )
        }
    }

    fun delete(files: List<File>) {
        viewModelScope.launch {
            store.delete(files)
            _messages.send(
                if (files.size == 1) UiText(R.string.msg_image_deleted) else UiText(R.plurals.msg_images_deleted_count, files.size),
            )
        }
    }

    fun shareIntent(files: List<File>): Intent = store.shareIntent(files)

    suspend fun chatIdOf(file: File): Long? = chats.chatIdForImage(file)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BanapiApplication
                GalleryViewModel(app.images, app.chats)
            }
        }
    }
}
