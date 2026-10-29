package app.banapi.ui.create

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.banapi.R
import app.banapi.data.GenerationError
import app.banapi.data.chat.ChatImage
import app.banapi.data.chat.ChatMessage
import app.banapi.data.chat.MessageStatus
import app.banapi.data.chat.Role
import app.banapi.ui.components.BoundsHolder
import app.banapi.ui.components.appear
import app.banapi.ui.components.formatUsd
import app.banapi.ui.components.pressScale
import app.banapi.ui.components.rememberReducedMotion
import app.banapi.ui.components.trackBounds
import app.banapi.ui.theme.BanapiMotion
import app.banapi.ui.theme.BanapiSizes
import app.banapi.ui.theme.BanapiSpacing
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File
import java.text.NumberFormat

// Decimales del costo de cada respuesta (las imágenes cuestan centavos).
private const val COST_DECIMALS = 3

// Intensidad del brillo amarillo mientras se genera.
private const val SHIMMER_ALPHA = 0.18f

/** "≈ US$0,044 · 2.517 tokens"; sin el costo en las respuestas guardadas antes de calcularlo. */
@Composable
fun usageLabel(costMicros: Long, tokens: Int, decimals: Int): String {
    val formattedTokens = NumberFormat.getIntegerInstance().format(tokens)
    return if (costMicros > 0) {
        stringResource(R.string.create_cost_tokens, formatUsd(costMicros, decimals), formattedTokens)
    } else {
        stringResource(R.string.create_tokens, formattedTokens)
    }
}

/** Proporciones límite al mostrar una imagen en el chat; las panorámicas se ven enteras, más chicas. */
private const val MIN_DISPLAY_RATIO = 0.5f
private const val MAX_DISPLAY_RATIO = 4f

private fun displayRatio(ratio: Float) = ratio.coerceIn(MIN_DISPLAY_RATIO, MAX_DISPLAY_RATIO)

/** Acciones sobre una imagen del chat. [onOpen] recibe dónde está la imagen, para abrir el visor desde ahí. */
class ImageActions(
    val onOpen: (File, Rect?) -> Unit,
    val onEdit: (File) -> Unit,
    val onSave: (File) -> Unit,
    val onShare: (File) -> Unit,
)

/**
 * Mensajes del chat, de abajo hacia arriba. Los mensajes que llegan con el chat abierto
 * (después de [openedAt]) entran animados; los que ya estaban se muestran directo.
 */
@Composable
fun MessageList(
    messages: List<ChatMessage>,
    openedAt: Long,
    pendingAspectRatio: Float,
    listState: LazyListState,
    imageActions: ImageActions,
    onRetry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Para animar cada cosa una sola vez, aunque el mensaje salga y vuelva a entrar en pantalla.
    val appeared = remember(openedAt) { mutableSetOf<Long>() }
    val seenPending = remember(openedAt) { mutableSetOf<Long>() }
    val revealed = remember(openedAt) { mutableSetOf<Long>() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = BanapiSpacing.md, vertical = BanapiSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BanapiSpacing.lg),
    ) {
        items(messages.asReversed(), key = { it.id }) { message ->
            val animateIn = message.createdAt >= openedAt && message.id !in appeared
            val reveal = message.status == MessageStatus.Done && message.id in seenPending && message.id !in revealed
            SideEffect {
                appeared += message.id
                if (message.status == MessageStatus.Pending) seenPending += message.id
                if (reveal) revealed += message.id
            }

            val itemModifier = Modifier
                .animateItem()
                .appear(animate = animateIn)
            when (message.role) {
                Role.User -> UserMessage(message, imageActions.onOpen, itemModifier)
                Role.Model -> ModelMessage(
                    message = message,
                    pendingAspectRatio = pendingAspectRatio,
                    reveal = reveal,
                    imageActions = imageActions,
                    onRetry = { onRetry(message.id) },
                    modifier = itemModifier,
                )
            }
        }
    }
}

@Composable
private fun UserMessage(message: ChatMessage, onOpenImage: (File, Rect?) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(BanapiSpacing.xs),
    ) {
        if (message.images.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.xs, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(BanapiSpacing.xs),
            ) {
                message.images.forEach { image ->
                    Thumbnail(image, BanapiSizes.messageThumbnail, onOpenImage)
                }
            }
        }
        if (message.text.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.widthIn(max = BanapiSizes.chatBubbleMaxWidth),
            ) {
                SelectionContainer {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = BanapiSpacing.md, vertical = BanapiSpacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelMessage(
    message: ChatMessage,
    pendingAspectRatio: Float,
    reveal: Boolean,
    imageActions: ImageActions,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BanapiSpacing.sm),
    ) {
        when (message.status) {
            MessageStatus.Pending -> GeneratingPlaceholder(pendingAspectRatio, startedAt = message.createdAt)

            MessageStatus.Failed -> FailedMessage(message, onRetry)

            MessageStatus.Done -> {
                message.images.forEach { image -> GeneratedImage(image, reveal, imageActions) }
                if (message.text.isNotBlank()) {
                    SelectionContainer {
                        Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (message.tokens > 0) {
                    Text(
                        text = usageLabel(message.costMicros, message.tokens, COST_DECIMALS),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Mientras se genera: un recuadro del tamaño de la imagen con un brillo que lo recorre. */
@Composable
private fun GeneratingPlaceholder(aspectRatio: Float, startedAt: Long) {
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "generating")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BanapiMotion.shimmer, easing = LinearEasing)),
        label = "shimmer",
    )
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = BanapiMotion.pulseScale,
        animationSpec = infiniteRepeatable(tween(BanapiMotion.pulse), RepeatMode.Reverse),
        label = "pulse",
    )

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val seconds = ((now - startedAt) / 1_000).coerceAtLeast(0)
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = SHIMMER_ALPHA)

    Box(
        modifier = Modifier
            .widthIn(max = BanapiSizes.chatImageMaxWidth)
            .fillMaxWidth()
            .aspectRatio(displayRatio(aspectRatio))
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .drawWithContent {
                drawContent()
                if (!reducedMotion) {
                    // Una franja diagonal que cruza de izquierda a derecha.
                    val band = size.width
                    val x = -band + shimmer * (size.width + band * 2)
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, highlight, Color.Transparent),
                            start = Offset(x, 0f),
                            end = Offset(x + band, size.height),
                        ),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BanapiSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(BanapiSizes.emptyStateIcon)
                    .graphicsLayer {
                        val scale = if (reducedMotion) 1f else pulse
                        scaleX = scale
                        scaleY = scale
                    },
            )
            Text(
                text = stringResource(R.string.create_generating, seconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Imagen generada. Con [reveal], aparece de borrosa a nítida (la que acaba de terminar). */
@Composable
private fun GeneratedImage(image: ChatImage, reveal: Boolean, actions: ImageActions) {
    val modifier = Modifier
        .widthIn(max = BanapiSizes.chatImageMaxWidth)
        .fillMaxWidth()
        .aspectRatio(displayRatio(image.aspectRatio))
        .clip(MaterialTheme.shapes.large)
        .background(MaterialTheme.colorScheme.surfaceContainer)

    if (!image.exists) {
        DeletedImage(modifier)
        return
    }

    val reducedMotion = rememberReducedMotion()
    val progress = remember { Animatable(if (reveal && !reducedMotion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, tween(BanapiMotion.slow, easing = FastOutSlowInEasing))
    }
    val bounds = remember { BoundsHolder() }
    val interaction = remember { MutableInteractionSource() }

    Column {
        AsyncImage(
            model = image.file,
            contentDescription = stringResource(R.string.gallery_image_description),
            contentScale = ContentScale.Fit,
            modifier = modifier
                .trackBounds(bounds)
                .pressScale(interaction)
                .clickable(interactionSource = interaction, indication = null) {
                    actions.onOpen(image.file, bounds.value)
                }
                .blur(BanapiMotion.revealBlur * (1f - progress.value))
                .graphicsLayer { alpha = progress.value },
        )
        Row(modifier = Modifier.graphicsLayer { alpha = progress.value }) {
            TextButton(onClick = { actions.onEdit(image.file) }) {
                Icon(
                    imageVector = Icons.Outlined.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(
                    text = stringResource(R.string.create_edit_image),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
            IconButton(onClick = { actions.onSave(image.file) }) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = stringResource(R.string.action_save),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { actions.onShare(image.file) }) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.action_share),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(image: ChatImage, size: Dp, onOpenImage: (File, Rect?) -> Unit) {
    val modifier = Modifier
        .size(size)
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceContainer)

    if (image.exists) {
        val bounds = remember { BoundsHolder() }
        AsyncImage(
            model = image.file,
            contentDescription = stringResource(R.string.create_attachment_description),
            contentScale = ContentScale.Crop,
            modifier = modifier
                .trackBounds(bounds)
                .clickable { onOpenImage(image.file, bounds.value) },
        )
    } else {
        DeletedImage(modifier, showLabel = false)
    }
}

@Composable
private fun DeletedImage(modifier: Modifier, showLabel: Boolean = true) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BanapiSpacing.xs),
        ) {
            Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = stringResource(R.string.create_image_deleted),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showLabel) {
                Text(
                    text = stringResource(R.string.create_image_deleted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FailedMessage(message: ChatMessage, onRetry: () -> Unit) {
    val cancelled = message.error == GenerationError.Cancelled
    val color = if (cancelled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error

    Row(horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.sm)) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = color)
        Column(verticalArrangement = Arrangement.spacedBy(BanapiSpacing.xs)) {
            Text(
                text = stringResource(errorMessage(message.error)),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
            // Detalle técnico de Google (por ejemplo, el motivo de un bloqueo).
            message.errorDetail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    TextButton(onClick = onRetry) {
        Icon(Icons.Outlined.Refresh, contentDescription = null)
        Text(
            text = stringResource(R.string.action_retry),
            modifier = Modifier.padding(start = BanapiSpacing.sm),
        )
    }
}

@StringRes
private fun errorMessage(error: GenerationError?): Int = when (error) {
    GenerationError.NoApiKey -> R.string.error_gen_no_key
    GenerationError.InvalidKey -> R.string.error_gen_invalid_key
    GenerationError.NoConnection -> R.string.error_gen_no_connection
    GenerationError.Timeout -> R.string.error_gen_timeout
    GenerationError.QuotaExceeded -> R.string.error_gen_quota
    GenerationError.NoCredits -> R.string.error_gen_no_credits
    GenerationError.Blocked -> R.string.error_gen_blocked
    GenerationError.NoImage -> R.string.error_gen_no_image
    GenerationError.ServerError -> R.string.error_gen_server
    GenerationError.Interrupted -> R.string.error_gen_interrupted
    GenerationError.Cancelled -> R.string.error_gen_cancelled
    GenerationError.Failed, null -> R.string.error_gen_failed
}
