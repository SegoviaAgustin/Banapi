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
import app.banapi.data.chat.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToLong

enum class KeyError(@param:StringRes val message: Int) {
    Invalid(R.string.error_key_invalid),
    NoConnection(R.string.error_key_no_connection),
    Failed(R.string.error_key_failed),
}

/** Gasto estimado según los pedidos hechos desde Banapi. */
data class UsageState(
    val monthCostMicros: Long = 0,
    val monthImages: Int = 0,
    val totalCostMicros: Long = 0,
    val totalImages: Int = 0,
    val balanceMicros: Long? = null,
    val remainingMicros: Long = 0,
)

data class SettingsUiState(
    val isLoaded: Boolean = false,
    val savedApiKey: String? = null,
    val isCheckingKey: Boolean = false,
    val keyError: KeyError? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val imageCount: Int = 0,
    val imagesBytes: Long = 0,
    val chatCount: Int = 0,
    val usage: UsageState = UsageState(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val images: ImageStore,
    private val chats: ChatRepository,
) : ViewModel() {

    private val isCheckingKey = MutableStateFlow(false)
    private val keyError = MutableStateFlow<KeyError?>(null)

    private val storage = images.images
        .map { images -> images.size to images.sumOf { it.file.length() } }
        .flowOn(Dispatchers.IO)
        .combine(chats.chatCount) { (count, bytes), chatCount -> Triple(count, bytes, chatCount) }

    private val usage: Flow<UsageState> = combine(
        chats.usageSince(startOfMonth()),
        chats.usageSince(0),
        settings.balance.flatMapLatest { balance ->
            if (balance == null) flowOf(null) else chats.usageSince(balance.since).map { balance to it }
        },
    ) { month, total, balance ->
        UsageState(
            monthCostMicros = month.costMicros,
            monthImages = month.images,
            totalCostMicros = total.costMicros,
            totalImages = total.images,
            balanceMicros = balance?.first?.amountMicros,
            remainingMicros = balance?.let { (it.first.amountMicros - it.second.costMicros).coerceAtLeast(0) } ?: 0,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.apiKey,
        settings.themeMode,
        storage.combine(usage, ::Pair),
        isCheckingKey,
        keyError,
    ) { apiKey, themeMode, (storage, usage), checking, error ->
        val (count, bytes, chatCount) = storage
        SettingsUiState(
            isLoaded = true,
            savedApiKey = apiKey,
            isCheckingKey = checking,
            keyError = error,
            themeMode = themeMode,
            imageCount = count,
            imagesBytes = bytes,
            chatCount = chatCount,
            usage = usage,
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

    /** [amount] en dólares, tal como lo escribe el usuario (acepta coma o punto). Devuelve false si no es válido. */
    fun setBalance(amount: String): Boolean {
        val dollars = amount.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return false
        viewModelScope.launch { settings.setBalance((dollars * 1_000_000).roundToLong()) }
        return true
    }

    fun clearBalance() {
        viewModelScope.launch { settings.clearBalance() }
    }

    fun deleteAllChats() {
        chats.deleteAll()
        viewModelScope.launch { _messages.send(R.string.msg_chats_deleted) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BanapiApplication
                SettingsViewModel(app.settings, app.images, app.chats)
            }
        }
    }
}

/** Primer instante del mes actual, en milisegundos. */
private fun startOfMonth(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
