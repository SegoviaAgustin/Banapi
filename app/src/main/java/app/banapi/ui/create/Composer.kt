package app.banapi.ui.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.banapi.R
import app.banapi.ui.components.pressScale
import app.banapi.ui.theme.BanapiColors
import app.banapi.ui.theme.BanapiMotion
import app.banapi.ui.theme.BanapiSizes
import app.banapi.ui.theme.BanapiSpacing
import coil3.compose.AsyncImage
import java.io.File

private const val MAX_LINES = 6

// Cómo cambia el botón de Enviar a Detener.
private const val SWAP_SCALE = 0.6f
private const val SWAP_ROTATION = -90f

/** Caja para escribir, con las imágenes que se van a enviar encima. */
@Composable
fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<File>,
    onRemoveAttachment: (File) -> Unit,
    onMarkAttachment: (File) -> Unit,
    onAttachFromPhone: () -> Unit,
    onAttachFromGallery: () -> Unit,
    hasCustomOptions: Boolean,
    onOpenOptions: () -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BanapiSpacing.md, vertical = BanapiSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(BanapiSpacing.sm),
    ) {
        if (attachments.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.sm)) {
                items(attachments, key = { it.path }) { file ->
                    AttachmentThumbnail(
                        modifier = Modifier.animateItem(),
                        file = file,
                        onMark = { onMarkAttachment(file) },
                        onRemove = { onRemoveAttachment(file) },
                    )
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(BanapiSizes.navBarBorder, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(BanapiSpacing.xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                AttachButton(
                    enabled = attachments.size < MAX_ATTACHMENTS,
                    onFromPhone = onAttachFromPhone,
                    onFromGallery = onAttachFromGallery,
                    modifier = Modifier.padding(bottom = BanapiSpacing.xs),
                )
                OptionsButton(
                    hasCustomOptions = hasCustomOptions,
                    onClick = onOpenOptions,
                    modifier = Modifier.padding(bottom = BanapiSpacing.xs),
                )
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text(stringResource(R.string.create_placeholder)) },
                    maxLines = MAX_LINES,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )

                // Mismo margen abajo y a la derecha: queda centrado cuando hay una sola línea.
                SendButton(
                    isGenerating = isGenerating,
                    enabled = text.isNotBlank() || attachments.isNotEmpty(),
                    onSend = onSend,
                    onStop = onStop,
                    modifier = Modifier.padding(bottom = BanapiSpacing.xs, end = BanapiSpacing.xs),
                )
            }
        }
    }
}

/** Enviar se transforma en Detener (y al revés) con un giro. */
@Composable
private fun SendButton(
    isGenerating: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = isGenerating,
        transitionSpec = {
            (fadeIn(tween(BanapiMotion.fast)) + scaleIn(initialScale = SWAP_SCALE)) togetherWith
                (fadeOut(tween(BanapiMotion.fast)) + scaleOut(targetScale = SWAP_SCALE))
        },
        modifier = modifier,
        label = "sendButton",
    ) { generating ->
        val rotation by transition.animateFloat(label = "sendRotation") { state ->
            if (state == EnterExitState.Visible) 0f else SWAP_ROTATION
        }
        val interaction = remember { MutableInteractionSource() }
        FilledIconButton(
            onClick = if (generating) onStop else onSend,
            enabled = generating || enabled,
            interactionSource = interaction,
            modifier = Modifier
                .pressScale(interaction)
                .graphicsLayer { rotationZ = rotation },
        ) {
            if (generating) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.action_stop))
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.action_send))
            }
        }
    }
}

@Composable
private fun AttachButton(
    enabled: Boolean,
    onFromPhone: () -> Unit,
    onFromGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = stringResource(R.string.create_attach))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.create_attach_phone)) },
                leadingIcon = { Icon(Icons.Outlined.Smartphone, contentDescription = null) },
                onClick = {
                    expanded = false
                    onFromPhone()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.create_attach_gallery)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                onClick = {
                    expanded = false
                    onFromGallery()
                },
            )
        }
    }
}

/** Muestra un punto amarillo cuando el chat tiene alguna opción fuera de Auto. */
@Composable
private fun OptionsButton(hasCustomOptions: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        BadgedBox(
            badge = {
                if (hasCustomOptions) Badge(containerColor = MaterialTheme.colorScheme.primary)
            },
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.options_open))
        }
    }
}

@Composable
private fun AttachmentThumbnail(file: File, onMark: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(BanapiSizes.composerThumbnail)) {
        AsyncImage(
            model = file,
            contentDescription = stringResource(R.string.create_attachment_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = stringResource(R.string.markup_open), onClick = onMark),
        )
        // Lápiz: tocar la miniatura abre el editor para marcarla.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(BanapiSpacing.xs)
                .size(BanapiSizes.composerActionButton)
                .clip(CircleShape)
                .background(BanapiColors.Viewer.controlsScrim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Draw,
                contentDescription = null,
                tint = BanapiColors.Viewer.content,
                modifier = Modifier.size(BanapiSizes.composerActionIcon),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(BanapiSpacing.xs)
                .size(BanapiSizes.composerActionButton)
                .clip(CircleShape)
                .background(BanapiColors.Viewer.controlsScrim)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.action_remove),
                tint = BanapiColors.Viewer.content,
                modifier = Modifier.size(BanapiSizes.composerActionIcon),
            )
        }
    }
}
