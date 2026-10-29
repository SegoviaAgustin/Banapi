package app.banapi.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Prepara las imágenes para enviarlas: más chicas viajan más rápido y gastan menos tokens. */
internal object ImageEncoder {
    const val MIME_TYPE = "image/jpeg"
    const val MAX_SIDE = 2048
    const val JPEG_QUALITY = 90

    fun toBase64(file: File): String {
        val bitmap = decode(file)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        bitmap.recycle()
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    /** Carga la imagen con su lado mayor limitado a [maxSide]. */
    fun decode(file: File, maxSide: Int = MAX_SIDE): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longestSide = max(bounds.outWidth, bounds.outHeight)
        check(longestSide > 0) { "No se pudo leer la imagen" }

        // Primero se decodifica reducida para no cargar la imagen entera en memoria.
        var sampleSize = 1
        while (longestSide / (sampleSize * 2) >= maxSide) sampleSize *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: error("No se pudo leer la imagen")

        val scale = maxSide.toFloat() / max(decoded.width, decoded.height)
        if (scale >= 1f) return decoded
        return Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt(),
            (decoded.height * scale).roundToInt(),
            true,
        ).also { decoded.recycle() }
    }
}
