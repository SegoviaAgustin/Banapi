package app.banapi.data

import android.content.Context
import androidx.datastore.preferences.core.edit
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

/** Ajustes guardados en el teléfono. La API key se guarda cifrada. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val themeMode = stringPreferencesKey("theme_mode")
    }

    val apiKey: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[Keys.apiKey]?.let(ApiKeyCipher::decrypt) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map { prefs -> ThemeMode.entries.firstOrNull { it.name == prefs[Keys.themeMode] } ?: ThemeMode.System }
        .distinctUntilChanged()

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
