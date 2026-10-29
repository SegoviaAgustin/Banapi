package app.banapi.data

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Una imagen de la galería y su proporción (ancho / alto). */
data class GalleryImage(val file: File, val aspectRatio: Float)

/**
 * Imágenes creadas con la app. Se guardan en el almacenamiento privado de Banapi
 * y el usuario decide cuáles copiar a la galería del teléfono.
 */
class ImageStore(private val context: Context) {

    private val directory = File(context.filesDir, "images")

    private val _images = MutableStateFlow<List<GalleryImage>>(emptyList())

    /** Imágenes de la más nueva a la más vieja. */
    val images: StateFlow<List<GalleryImage>> = _images.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _images.value = directory
            .listFiles { file -> file.isFile && file.extension.lowercase() in MIME_TYPES }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { GalleryImage(it, aspectRatioOf(it)) }
    }

    /** Lee solo el tamaño de la imagen (sin cargarla) para reservar su espacio en la galería. */
    private fun aspectRatioOf(file: File): Float {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return 1f
        return (options.outWidth.toFloat() / options.outHeight).coerceIn(0.5f, 2f)
    }

    suspend fun delete(file: File) {
        withContext(Dispatchers.IO) { file.delete() }
        refresh()
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) { directory.listFiles()?.forEach { it.delete() } }
        refresh()
    }

    /** Copia la imagen a Imágenes/Banapi en el teléfono. */
    suspend fun saveToDevice(file: File): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, mimeTypeOf(file))
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Banapi")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return@withContext false

        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { it.copyTo(output) }
            } ?: error("No se pudo abrir el destino")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }.onFailure {
            resolver.delete(uri, null, null)
        }.isSuccess
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeTypeOf(file))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        return Intent.createChooser(send, null)
    }

    private fun mimeTypeOf(file: File): String = MIME_TYPES[file.extension.lowercase()] ?: "image/*"

    private companion object {
        val MIME_TYPES = mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "webp" to "image/webp",
        )
    }
}
