package app.banapi.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.banapi.BanapiApplication
import app.banapi.R
import app.banapi.data.GeminiApi
import app.banapi.data.ImageStore
import app.banapi.data.KeyCheckResult
import app.banapi.data.SettingsRepository
import app.banapi.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class KeyError(@param:StringRes val message: Int) {
    Invalid(R.string.error_key_invalid),
    NoConnection(R.string.error_key_no_connection),
    Failed(R.string.error_key_failed),
}

data class SettingsUiState(
    val isLoaded: Boolean = false,
    val savedApiKey: String? = null,
    val isCheckingKey: Boolean = false,
    val keyError: KeyError? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val imageCount: Int = 0,
    val imagesBytes: Long = 0,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val images: ImageStore,
) : ViewModel() {

    private val isCheckingKey = MutableStateFlow(false)
    private val keyError = MutableStateFlow<KeyError?>(null)

    private val storage = images.images
        .map { images -> images.size to images.sumOf { it.file.length() } }
        .flowOn(Dispatchers.IO)

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.apiKey,
        settings.themeMode,
        storage,
        isCheckingKey,
        keyError,
    ) { apiKey, themeMode, (count, bytes), checking, error ->
        SettingsUiState(
            isLoaded = true,
            savedApiKey = apiKey,
            isCheckingKey = checking,
            keyError = error,
            themeMode = themeMode,
            imageCount = count,
            imagesBytes = bytes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _messages = Channel<Int>(Channel.BUFFERED)

    /** Avisos cortos para mostrar en un snackbar (ids de strings). */
    val messages: Flow<Int> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { images.refresh() }
    }

    fun saveApiKey(apiKey: String) {
        if (isCheckingKey.value) return
        viewModelScope.launch {
            isCheckingKey.value = true
            keyError.value = null
            when (GeminiApi.checkApiKey(apiKey)) {
                KeyCheckResult.Valid -> {
                    settings.saveApiKey(apiKey)
                    _messages.send(R.string.msg_key_saved)
                }
                KeyCheckResult.Invalid -> keyError.value = KeyError.Invalid
                KeyCheckResult.NoConnection -> keyError.value = KeyError.NoConnection
                KeyCheckResult.Failed -> keyError.value = KeyError.Failed
            }
            isCheckingKey.value = false
        }
    }

    fun clearKeyError() {
        keyError.value = null
    }

    fun removeApiKey() {
        viewModelScope.launch {
            settings.removeApiKey()
            _messages.send(R.string.msg_key_removed)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun deleteAllImages() {
        viewModelScope.launch {
            images.deleteAll()
            _messages.send(R.string.msg_images_deleted)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BanapiApplication
                SettingsViewModel(app.settings, app.images)
            }
        }
    }
}
