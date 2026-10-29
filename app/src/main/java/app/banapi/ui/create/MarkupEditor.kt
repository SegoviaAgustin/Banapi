package app.banapi.ui.create

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.banapi.R
import app.banapi.ui.theme.BanapiColors
import app.banapi.ui.theme.BanapiSizes
import app.banapi.ui.theme.BanapiSpacing
import java.io.File
import kotlin.math.min

/** Un trazo, en píxeles de la imagen (no de la pantalla), para poder dibujarlo después sobre ella. */
private class MarkStroke(val points: List<Offset>, val color: Color, val width: Float)

/** Dónde se ve la imagen dentro de la pantalla. */
private class ImageFrame(val scale: Float, val offset: Offset) {
    fun toImage(point: Offset) = (point - offset) / scale
    fun toScreen(point: Offset) = point * scale + offset
}

/** Pantalla para marcar a mano una imagen adjunta, por ejemplo, encerrar lo que hay que cambiar. */
@Composable
fun MarkupEditor(
    file: File,
    loadBitmap: suspend (File) -> Bitmap,
    onDone: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(null, file) { value = loadBitmap(file) }
    val strokes = remember { mutableStateListOf<MarkStroke>() }
    var colorIndex by remember { mutableIntStateOf(0) }
    var widthIndex by remember { mutableIntStateOf(1) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BanapiColors.Viewer.background),
        ) {
            val image = bitmap
            if (image == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                DrawingArea(
                    bitmap = image,
                    strokes = strokes,
                    color = BanapiColors.markup[colorIndex],
                    width = BanapiSizes.markupWidths[widthIndex],
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(vertical = BanapiSizes.markupEdgeSpace),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(BanapiSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.sm),
            ) {
                ViewerIconButton(Icons.Outlined.Close, R.string.action_close, onClick = onDismiss)
                Box(Modifier.weight(1f))
                ViewerIconButton(
                    icon = Icons.AutoMirrored.Outlined.Undo,
                    label = R.string.markup_undo,
                    enabled = strokes.isNotEmpty(),
                    onClick = { strokes.removeAt(strokes.lastIndex) },
                )
                ViewerIconButton(
                    icon = Icons.Outlined.LayersClear,
                    label = R.string.markup_clear,
                    enabled = strokes.isNotEmpty(),
                    onClick = { strokes.clear() },
                )
                Button(
                    onClick = {
                        image?.let { onDone(renderMarks(it, strokes)) }
                        onDismiss()
                    },
                    enabled = image != null && strokes.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.markup_done))
                }
            }

            Toolbar(
                colorIndex = colorIndex,
                onColor = { colorIndex = it },
                widthIndex = widthIndex,
                onWidth = { widthIndex = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = BanapiSizes.navBarBottomMargin),
            )
        }
    }
}

@Composable
private fun DrawingArea(
    bitmap: Bitmap,
    strokes: MutableList<MarkStroke>,
    color: Color,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val frame = remember(size, bitmap) {
        val scale = min(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height)
        val offset = Offset((size.width - bitmap.width * scale) / 2f, (size.height - bitmap.height * scale) / 2f)
        ImageFrame(scale, offset)
    }
    val current = remember { mutableStateListOf<Offset>() }

    Box(modifier = modifier.onSizeChanged { size = it }) {
        Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = stringResource(R.string.create_attachment_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(frame, color, width) {
                    val strokeWidth = width.toPx() / frame.scale
                    detectDragGestures(
                        onDragStart = { current.add(frame.toImage(it)) },
                        onDrag = { change, _ -> current.add(frame.toImage(change.position)) },
                        onDragEnd = {
                            strokes.add(MarkStroke(current.toList(), color, strokeWidth))
                            current.clear()
                        },
                        onDragCancel = { current.clear() },
                    )
                },
        ) {
            strokes.forEach { stroke ->
                drawPath(
                    path = stroke.points.toPath(frame),
                    color = stroke.color,
                    style = Stroke(width = stroke.width * frame.scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            if (current.isNotEmpty()) {
                drawPath(
                    path = current.toPath(frame),
                    color = color,
                    style = Stroke(width = width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

private fun List<Offset>.toPath(frame: ImageFrame) = Path().apply {
    forEachIndexed { index, point ->
        val screen = frame.toScreen(point)
        if (index == 0) moveTo(screen.x, screen.y) else lineTo(screen.x, screen.y)
    }
}

/** Dibuja los trazos sobre una copia de la imagen, en su tamaño real. */
private fun renderMarks(base: Bitmap, strokes: List<MarkStroke>): Bitmap {
    val result = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(result)
    strokes.forEach { stroke ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = stroke.color.toArgb()
            strokeWidth = stroke.width
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = android.graphics.Path()
        stroke.points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        // Un toque sin arrastrar deja un punto.
        if (stroke.points.size == 1) path.lineTo(stroke.points[0].x + 0.1f, stroke.points[0].y)
        canvas.drawPath(path, paint)
    }
    return result
}

/** Colores y grosores del trazo. */
@Composable
private fun Toolbar(
    colorIndex: Int,
    onColor: (Int) -> Unit,
    widthIndex: Int,
    onWidth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(BanapiColors.Viewer.controlsScrim)
            .padding(horizontal = BanapiSpacing.lg, vertical = BanapiSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.sm)) {
            BanapiColors.markup.forEachIndexed { index, color ->
                Swatch(
                    selected = index == colorIndex,
                    description = stringResource(R.string.markup_color, index + 1),
                    onClick = { onColor(index) },
                ) {
                    Box(
                        Modifier
                            .size(BanapiSizes.markupSwatch - BanapiSpacing.sm)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
        }
        Box(
            Modifier
                .size(BanapiSizes.navBarBorder, BanapiSizes.markupSwatch)
                .background(BanapiColors.Viewer.content.copy(alpha = 0.3f)),
        )
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.sm)) {
            BanapiSizes.markupWidths.forEachIndexed { index, width ->
                Swatch(
                    selected = index == widthIndex,
                    description = stringResource(R.string.markup_width, index + 1),
                    onClick = { onWidth(index) },
                ) {
                    Box(
                        Modifier
                            .size(width)
                            .clip(CircleShape)
                            .background(BanapiColors.Viewer.content),
                    )
                }
            }
        }
    }
}

@Composable
private fun Swatch(
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(BanapiSizes.markupSwatch)
            .clip(CircleShape)
            .border(
                BorderStroke(
                    BanapiSizes.markupSwatchBorder,
                    if (selected) BanapiColors.Viewer.content else Color.Transparent,
                ),
                CircleShape,
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ViewerIconButton(
    icon: ImageVector,
    @StringRes label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = BanapiColors.Viewer.controlsScrim,
            contentColor = BanapiColors.Viewer.content,
            disabledContainerColor = BanapiColors.Viewer.controlsScrim,
            disabledContentColor = BanapiColors.Viewer.content.copy(alpha = 0.38f),
        ),
    ) {
        Icon(icon, contentDescription = stringResource(label))
    }
}
