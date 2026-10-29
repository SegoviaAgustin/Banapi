package app.banapi.ui.gallery

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.banapi.R
import app.banapi.ui.components.ConfirmDialog
import app.banapi.ui.components.rememberReducedMotion
import app.banapi.ui.theme.BanapiColors
import app.banapi.ui.theme.BanapiMotion
import app.banapi.ui.theme.BanapiSizes
import app.banapi.ui.theme.BanapiSpacing
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

// Si no se sabe de dónde viene la imagen, crece desde este tamaño.
private const val OPEN_START_SCALE = 0.92f

/**
 * Imagen a pantalla completa con zoom y acciones. Si se conoce [origin] (dónde estaba la
 * miniatura), la imagen crece desde ahí al abrirse y vuelve ahí al cerrarse.
 */
@Composable
fun ImageViewer(
    file: File,
    origin: Rect?,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onOpenChat: (() -> Unit)? = null,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()

    // 0 = en la miniatura, 1 = a pantalla completa. Empieza cuando la imagen ya cargó.
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    var imageSize by remember { mutableStateOf<Size?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(loaded) {
        if (loaded) progress.animateTo(1f, BanapiMotion.smooth())
    }

    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, tween(BanapiMotion.medium))
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .background(BanapiColors.Viewer.background.copy(alpha = progress.value)),
        ) {
            ZoomableImage(
                file = file,
                onLoaded = {
                    imageSize = it
                    loaded = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        val start = startTransform(origin, imageSize, containerSize)
                        if (start == null) {
                            alpha = p
                            scaleX = lerp(OPEN_START_SCALE, 1f, p)
                            scaleY = scaleX
                        } else {
                            scaleX = lerp(start.scale, 1f, p)
                            scaleY = scaleX
                            translationX = lerp(start.offset.x, 0f, p)
                            translationY = lerp(start.offset.y, 0f, p)
                        }
                    },
            )

            IconButton(
                onClick = close,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = BanapiColors.Viewer.controlsScrim,
                    contentColor = BanapiColors.Viewer.content,
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(BanapiSpacing.sm)
                    .graphicsLayer { alpha = progress.value },
            ) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = BanapiSizes.navBarBottomMargin)
                    .graphicsLayer { alpha = progress.value },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SnackbarHost(snackbarHostState)
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(BanapiColors.Viewer.controlsScrim)
                        .padding(horizontal = BanapiSpacing.sm, vertical = BanapiSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.xs),
                ) {
                    onOpenChat?.let { ViewerAction(Icons.AutoMirrored.Outlined.Chat, R.string.gallery_open_chat, it) }
                    ViewerAction(Icons.Outlined.Share, R.string.action_share, onShare)
                    ViewerAction(Icons.Outlined.Download, R.string.action_save, onSave)
                    ViewerAction(Icons.Outlined.Delete, R.string.action_delete) { confirmDelete = true }
                }
            }
        }

        if (confirmDelete) {
            ConfirmDialog(
                title = R.string.gallery_delete_title,
                body = R.string.gallery_delete_body,
                onConfirm = onDelete,
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

/** Escala y desplazamiento que ubican la imagen sobre su miniatura (null si no se sabe dónde estaba). */
private class StartTransform(val scale: Float, val offset: Offset)

private fun startTransform(origin: Rect?, imageSize: Size?, container: IntSize): StartTransform? {
    if (origin == null || imageSize == null || container == IntSize.Zero) return null
    if (imageSize.width <= 0f || imageSize.height <= 0f) return null
    // Tamaño de la imagen ya ajustada a la pantalla.
    val fit = min(container.width / imageSize.width, container.height / imageSize.height)
    val fittedWidth = imageSize.width * fit
    val fittedHeight = imageSize.height * fit
    return StartTransform(
        scale = max(origin.width / fittedWidth, origin.height / fittedHeight),
        offset = Offset(origin.center.x - container.width / 2f, origin.center.y - container.height / 2f),
    )
}

/** Zoom con pellizco o doble toque. [onLoaded] avisa el tamaño real de la imagen (null si no cargó). */
@Composable
private fun ZoomableImage(file: File, onLoaded: (Size?) -> Unit, modifier: Modifier = Modifier) {
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offset by remember(file) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(value: Offset, currentScale: Float): Offset {
        val maxX = size.width * (currentScale - 1f) / 2f
        val maxY = size.height * (currentScale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    AsyncImage(
        model = file,
        contentDescription = stringResource(R.string.gallery_image_description),
        contentScale = ContentScale.Fit,
        onSuccess = { onLoaded(it.painter.intrinsicSize) },
        onError = { onLoaded(null) },
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(file) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_ZOOM
                        }
                    },
                )
            }
            .pointerInput(file) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                    offset = clamp(offset + pan, scale)
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

@Composable
private fun ViewerAction(
    icon: ImageVector,
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = BanapiSpacing.lg, vertical = BanapiSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BanapiSpacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = BanapiColors.Viewer.content)
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = BanapiColors.Viewer.content,
        )
    }
}
