package app.banapi.data

import kotlin.math.roundToLong

/**
 * Precios de Nano Banana 2.1 en US$ por millón de tokens (pago estándar, octubre de 2026).
 * Si Google los cambia, se actualizan aquí: https://ai.google.dev/gemini-api/docs/pricing
 */
object Pricing {
    private const val INPUT_PER_MILLION = 1.50
    private const val TEXT_OUTPUT_PER_MILLION = 7.50 // texto y razonamiento
    private const val IMAGE_OUTPUT_PER_MILLION = 30.00

    /** Costo estimado en millonésimas de dólar (1.000.000 = US$1). */
    fun costMicros(inputTokens: Int, textOutputTokens: Int, imageOutputTokens: Int): Long =
        (inputTokens * INPUT_PER_MILLION +
            textOutputTokens * TEXT_OUTPUT_PER_MILLION +
            imageOutputTokens * IMAGE_OUTPUT_PER_MILLION).roundToLong()
}
