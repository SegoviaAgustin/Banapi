package app.banapi

import android.app.Application
import app.banapi.data.ImageStore
import app.banapi.data.SettingsRepository
import app.banapi.data.chat.ChatDatabase
import app.banapi.data.chat.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Crea una sola vez los repositorios que comparte toda la app. */
class BanapiApplication : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var images: ImageStore
        private set
    lateinit var chats: ChatRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        images = ImageStore(this)

        // Vive mientras la app esté abierta: una generación sigue aunque se cambie de pantalla.
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        chats = ChatRepository(ChatDatabase.create(this).chatDao(), images, settings, appScope)
    }
}
