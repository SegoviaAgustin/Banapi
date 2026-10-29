package app.banapi.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import app.banapi.ui.theme.BanapiMotion

/** true si el usuario quitó las animaciones del teléfono (opción de accesibilidad). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Se achica apenas mientras se toca. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) BanapiMotion.pressedScale else 1f,
        animationSpec = BanapiMotion.smooth(),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Aparece subiendo, con un fundido. Con [animate] en false se muestra directo
 * (por ejemplo, los mensajes que ya estaban al abrir un chat).
 */
@Composable
fun Modifier.appear(animate: Boolean = true, index: Int = 0): Modifier {
    val reducedMotion = rememberReducedMotion()
    val progress = remember { Animatable(if (animate && !reducedMotion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            val delay = index.coerceAtMost(BanapiMotion.staggerMaxItems) * BanapiMotion.stagger
            progress.animateTo(1f, tween(BanapiMotion.medium, delay, FastOutSlowInEasing))
        }
    }
    val offset = with(LocalDensity.current) { BanapiMotion.appearOffset.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offset
    }
}

/** Guarda dónde está un elemento en pantalla, para que el visor se abra desde ahí. */
class BoundsHolder {
    var value: Rect? = null
}

fun Modifier.trackBounds(holder: BoundsHolder): Modifier =
    onGloballyPositioned { holder.value = it.boundsInWindow() }
