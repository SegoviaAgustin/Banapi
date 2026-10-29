package app.banapi.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { System, Light, Dark }

/** Saldo que el usuario cargó en Google y desde cuándo se cuenta lo gastado. */
data class Balance(val amountMicros: Long, val since: Long)

/** Ajustes guardados en el teléfono. La API key se guarda cifrada. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val themeMode = stringPreferencesKey("theme_mode")
        val balanceMicros = longPreferencesKey("balance_micros")
        val balanceSince = longPreferencesKey("balance_since")
    }

    val apiKey: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[Keys.apiKey]?.let(ApiKeyCipher::decrypt) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map { prefs -> ThemeMode.entries.firstOrNull { it.name == prefs[Keys.themeMode] } ?: ThemeMode.System }
        .distinctUntilChanged()

    val balance: Flow<Balance?> = context.dataStore.data
        .map { prefs ->
            val amount = prefs[Keys.balanceMicros] ?: return@map null
            Balance(amount, prefs[Keys.balanceSince] ?: 0L)
        }
        .distinctUntilChanged()

    /** El gasto se empieza a descontar desde este momento. */
    suspend fun setBalance(amountMicros: Long) {
        context.dataStore.edit {
            it[Keys.balanceMicros] = amountMicros
            it[Keys.balanceSince] = System.currentTimeMillis()
        }
    }

    suspend fun clearBalance() {
        context.dataStore.edit {
            it.remove(Keys.balanceMicros)
            it.remove(Keys.balanceSince)
        }
    }

    suspend fun saveApiKey(apiKey: String) {
        val encrypted = withContext(Dispatchers.IO) { ApiKeyCipher.encrypt(apiKey) }
        context.dataStore.edit { it[Keys.apiKey] = encrypted }
    }

    suspend fun removeApiKey() {
        context.dataStore.edit { it.remove(Keys.apiKey) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }
}
