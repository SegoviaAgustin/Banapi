package app.banapi

import android.app.Application
import app.banapi.data.ImageStore
import app.banapi.data.SettingsRepository

/** Crea una sola vez los repositorios que comparte toda la app. */
class BanapiApplication : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var images: ImageStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        images = ImageStore(this)
    }
}
