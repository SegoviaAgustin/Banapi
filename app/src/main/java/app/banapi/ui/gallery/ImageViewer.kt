package app.banapi.ui.gallery

import androidx.annotation.StringRes
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.banapi.R
import app.banapi.ui.components.ConfirmDialog
import app.banapi.ui.theme.BanapiColors
import app.banapi.ui.theme.BanapiSizes
import app.banapi.ui.theme.BanapiSpacing
import coil3.compose.AsyncImage
import java.io.File

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Imagen a pantalla completa con zoom y acciones. */
@Composable
fun ImageViewer(
    file: File,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onOpenChat: (() -> Unit)? = null,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BanapiColors.Viewer.background),
        ) {
            ZoomableImage(file = file, modifier = Modifier.fillMaxSize())

            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = BanapiColors.Viewer.controlsScrim,
                    contentColor = BanapiColors.Viewer.content,
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(BanapiSpacing.sm),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = BanapiSizes.navBarBottomMargin),
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

/** Zoom con pellizco o doble toque. */
@Composable
private fun ZoomableImage(file: File, modifier: Modifier = Modifier) {
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
