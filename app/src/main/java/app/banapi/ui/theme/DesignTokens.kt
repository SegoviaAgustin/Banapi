package app.banapi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Diseño de Banapi: todo lo visual de la app se cambia en este archivo.
 * Fuera de aquí solo quedan el fondo del ícono y el fondo al abrir la app (res/values/colors.xml).
 */

// ---------------------------------------------------------------------------
// 1. COLORES
// ---------------------------------------------------------------------------
//   primary                 -> botón principal, pestaña activa, opción elegida
//   primaryContainer        -> etiquetas como "Activa"; onPrimaryContainer = íconos de acento
//   background              -> fondo de las pantallas
//   surfaceContainer        -> tarjetas
//   surfaceContainerHigh    -> barra de navegación
//   surfaceContainerHighest -> campos de texto, botones secundarios, selector de tema
//   onSurface               -> texto principal; onSurfaceVariant = texto secundario

object BanapiColors {

    /** true = usa los colores del fondo de pantalla del teléfono (Android 12+). */
    const val USE_DYNAMIC_COLOR = false

    object Light {
        val primary = Color(0xFFF3CF40)
        val onPrimary = Color(0xFF1A1500)
        val primaryContainer = Color(0xFFF6EDC4)
        val onPrimaryContainer = Color(0xFF5C4B00)

        val secondary = Color(0xFF6B675C)
        val onSecondary = Color(0xFFFFFFFF)
        val secondaryContainer = Color(0xFFEEEBE3)
        val onSecondaryContainer = Color(0xFF1A1A17)

        val tertiary = Color(0xFF43664E)
        val onTertiary = Color(0xFFFFFFFF)
        val tertiaryContainer = Color(0xFFC5ECCE)
        val onTertiaryContainer = Color(0xFF00210F)

        val error = Color(0xFFBA1A1A)
        val onError = Color(0xFFFFFFFF)
        val errorContainer = Color(0xFFFFDAD6)
        val onErrorContainer = Color(0xFF93000A)

        val background = Color(0xFFF5F3EE)
        val onBackground = Color(0xFF1A1A17)
        val surface = Color(0xFFF5F3EE)
        val onSurface = Color(0xFF1A1A17)
        val surfaceVariant = Color(0xFFEEEBE3)
        val onSurfaceVariant = Color(0xFF75736B)
        val outline = Color(0xFFA8A59C)
        val outlineVariant = Color(0xFFE4E1D9)

        val surfaceContainerLowest = Color(0xFFFFFFFF)
        val surfaceContainerLow = Color(0xFFFBFAF7)
        val surfaceContainer = Color(0xFFFFFFFF)
        val surfaceContainerHigh = Color(0xFFFFFFFF)
        val surfaceContainerHighest = Color(0xFFEEEBE3)
    }

    object Dark {
        val primary = Color(0xFFF3CF40)
        val onPrimary = Color(0xFF1A1500)
        val primaryContainer = Color(0xFF2A2716)
        val onPrimaryContainer = Color(0xFFF3CF40)

        val secondary = Color(0xFFC9C5B9)
        val onSecondary = Color(0xFF1A1A17)
        val secondaryContainer = Color(0xFF2A2A26)
        val onSecondaryContainer = Color(0xFFEDEBE4)

        val tertiary = Color(0xFFA9D0B3)
        val onTertiary = Color(0xFF143723)
        val tertiaryContainer = Color(0xFF2C4E38)
        val onTertiaryContainer = Color(0xFFC5ECCE)

        val error = Color(0xFFF2B8B5)
        val onError = Color(0xFF601410)
        val errorContainer = Color(0xFF8C1D18)
        val onErrorContainer = Color(0xFFF9DEDC)

        val background = Color(0xFF0E0E0C)
        val onBackground = Color(0xFFEDEBE4)
        val surface = Color(0xFF0E0E0C)
        val onSurface = Color(0xFFEDEBE4)
        val surfaceVariant = Color(0xFF2A2A26)
        val onSurfaceVariant = Color(0xFF8C8A82)
        val outline = Color(0xFF5A5850)
        val outlineVariant = Color(0xFF2A2A26)

        val surfaceContainerLowest = Color(0xFF0A0A09)
        val surfaceContainerLow = Color(0xFF141412)
        val surfaceContainer = Color(0xFF1A1A17)
        val surfaceContainerHigh = Color(0xFF1F1F1B)
        val surfaceContainerHighest = Color(0xFF2A2A26)
    }

    /** Visor de imagen (igual en modo claro y oscuro). */
    object Viewer {
        val background = Color(0xFF000000)
        val content = Color(0xFFFFFFFF)
        val controlsScrim = Color(0xCC1A1A17) // fondo de los botones
    }
}

// ---------------------------------------------------------------------------
// 2. FUENTES
// ---------------------------------------------------------------------------
// Fuente propia: copia los .ttf a res/font/ y usa, por ejemplo:
//   FontFamily(Font(R.font.mi_fuente_regular), Font(R.font.mi_fuente_bold, FontWeight.Bold))

object BanapiFonts {
    /** Títulos y encabezados. */
    val headings: FontFamily = FontFamily.Default

    /** Textos normales, botones y etiquetas. */
    val body: FontFamily = FontFamily.Default

    /** Textos técnicos (por ejemplo, la API key). */
    val mono: FontFamily = FontFamily.Monospace
}

// ---------------------------------------------------------------------------
// 3. TEXTOS
// ---------------------------------------------------------------------------
// Cada estilo parte del de Material 3. Para cambiarlo, agrega parámetros al copy(),
// por ejemplo: fontSize = 24.sp, fontWeight = FontWeight.Bold

private val defaultTypography = Typography()

val BanapiTypography = Typography(
    // Títulos muy grandes
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = BanapiFonts.headings),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = BanapiFonts.headings),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = BanapiFonts.headings),
    // Encabezados (headlineMedium = título grande de cada pantalla)
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = BanapiFonts.headings),
    headlineMedium = defaultTypography.headlineMedium.copy(
        fontFamily = BanapiFonts.headings,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = BanapiFonts.headings),
    // Títulos de tarjetas y secciones
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = BanapiFonts.headings),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = BanapiFonts.headings),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = BanapiFonts.headings),
    // Texto normal
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = BanapiFonts.body),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = BanapiFonts.body),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = BanapiFonts.body),
    // Botones, pestañas y etiquetas
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = BanapiFonts.body),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = BanapiFonts.body),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = BanapiFonts.body),
)

// ---------------------------------------------------------------------------
// 4. BORDES
// ---------------------------------------------------------------------------
// Radio de las esquinas: 0.dp = rectas; más alto = más redondeado.

val BanapiShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),  // elementos muy chicos
    small = RoundedCornerShape(10.dp),      // avisos
    medium = RoundedCornerShape(14.dp),     // campos de texto, selector de tema
    large = RoundedCornerShape(20.dp),      // tarjetas, miniaturas de la galería
    extraLarge = RoundedCornerShape(28.dp), // barra de navegación, diálogos, botones del visor
)

// ---------------------------------------------------------------------------
// 5. ESPACIOS
// ---------------------------------------------------------------------------

object BanapiSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp   // entre tarjetas y a los lados de la pantalla
    val lg = 16.dp   // dentro de las tarjetas
    val xl = 24.dp
    val xxl = 32.dp
}

// ---------------------------------------------------------------------------
// 6. TAMAÑOS
// ---------------------------------------------------------------------------

object BanapiSizes {
    // Barra de navegación
    val navItemWidth = 64.dp
    val navItemHeight = 40.dp
    val navBarBottomMargin = 16.dp
    val navBarBorder = 1.dp
    val navBarReservedSpace = 88.dp // espacio libre abajo para que la barra no tape el contenido

    // Estado vacío (por ejemplo, Galería sin imágenes)
    val emptyStateCircle = 88.dp
    val emptyStateIcon = 36.dp

    /** Ancho mínimo de cada columna de la Galería. */
    val galleryColumnMinWidth = 150.dp

    // Indicador de carga dentro de un botón
    val buttonProgressSize = 18.dp
    val buttonProgressStroke = 2.dp
}
