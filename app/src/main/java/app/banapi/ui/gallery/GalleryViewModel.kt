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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File

class GalleryViewModel(private val store: ImageStore) : ViewModel() {

    val images: StateFlow<List<GalleryImage>> = store.images

    private val _messages = Channel<Int>(Channel.BUFFERED)

    /** Avisos cortos para mostrar en un snackbar (ids de strings). */
    val messages: Flow<Int> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { store.refresh() }
    }

    fun saveToDevice(file: File) {
        viewModelScope.launch {
            val saved = store.saveToDevice(file)
            _messages.send(if (saved) R.string.msg_image_saved else R.string.msg_image_save_failed)
        }
    }

    fun delete(file: File) {
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
                GalleryViewModel(app.images)
            }
        }
    }
}
