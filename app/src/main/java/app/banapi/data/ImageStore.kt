package app.banapi.data

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

    // Lo que el usuario adjunta en un chat; no aparece en la galería.
    private val attachmentsDirectory = File(context.filesDir, "attachments")

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

    private fun aspectRatioOf(file: File): Float {
        val (width, height) = sizeOf(file)
        if (width <= 0 || height <= 0) return 1f
        return (width.toFloat() / height).coerceIn(0.5f, 2f)
    }

    /** Lee solo el tamaño de la imagen (sin cargarla) para reservar su espacio en pantalla. */
    fun sizeOf(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        return options.outWidth to options.outHeight
    }

    /** Las rutas se guardan relativas a la carpeta privada de la app. */
    fun fileOf(path: String): File = File(context.filesDir, path)

    fun pathOf(file: File): String = file.relativeTo(context.filesDir).invariantSeparatorsPath

    fun isAttachment(path: String): Boolean = fileOf(path).parentFile == attachmentsDirectory

    /** Copia una imagen elegida del teléfono a la carpeta de adjuntos. Devuelve null si no se pudo leer. */
    suspend fun importAttachment(uri: Uri): File? = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri)
        val extension = MIME_TYPES.entries.firstOrNull { it.value == mimeType }?.key ?: "jpg"
        attachmentsDirectory.mkdirs()
        val file = File(attachmentsDirectory, "adjunto_${System.nanoTime()}.$extension")
        runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            file
        }.getOrElse {
            file.delete()
            null
        }
    }

    /** Carga una imagen para marcarla con el dedo. */
    suspend fun loadForMarkup(file: File): Bitmap = withContext(Dispatchers.IO) { ImageEncoder.decode(file) }

    /** Guarda una imagen marcada como un adjunto nuevo; la original no se toca. */
    suspend fun saveMarked(bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        attachmentsDirectory.mkdirs()
        File(attachmentsDirectory, "marcada_${System.nanoTime()}.jpg").apply {
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, ImageEncoder.JPEG_QUALITY, it) }
        }
    }

    /** Borra los adjuntos que ningún mensaje usa (por ejemplo, los que se quitaron antes de enviar). */
    suspend fun deleteAttachmentsExcept(usedPaths: Set<String>) = withContext(Dispatchers.IO) {
        attachmentsDirectory.listFiles()?.forEach { file ->
            if (pathOf(file) !in usedPaths) file.delete()
        }
    }

    /** Guarda una imagen generada; queda visible en la galería. */
    suspend fun saveGenerated(bytes: ByteArray, mimeType: String): File = withContext(Dispatchers.IO) {
        val extension = MIME_TYPES.entries.firstOrNull { it.value == mimeType }?.key ?: "png"
        directory.mkdirs()
        File(directory, "banapi_${System.nanoTime()}.$extension").apply { writeBytes(bytes) }
    }

    suspend fun delete(file: File) = delete(listOf(file))

    suspend fun delete(files: List<File>) {
        withContext(Dispatchers.IO) { files.forEach { it.delete() } }
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

    fun shareIntent(file: File): Intent = shareIntent(listOf(file))

    /** Compartir una o varias imágenes con otras apps. */
    fun shareIntent(files: List<File>): Intent {
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
        val mimeType = files.map(::mimeTypeOf).distinct().singleOrNull() ?: "image/*"
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.setType(mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Sin ClipData, algunas apps no reciben el permiso para leer las imágenes.
        send.clipData = ClipData.newRawUri(null, uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
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
