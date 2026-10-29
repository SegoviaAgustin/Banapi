package app.banapi.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.banapi.R
import app.banapi.ui.theme.BanapiMotion
import app.banapi.ui.theme.BanapiSizes
import app.banapi.ui.theme.BanapiSpacing
import java.util.Locale

/** Texto de un aviso: un string, o un plural con su cantidad (por ejemplo, "3 imágenes eliminadas"). */
data class UiText(val id: Int, val quantity: Int? = null) {
    fun resolve(context: Context): String =
        if (quantity == null) context.getString(id) else context.resources.getQuantityString(id, quantity, quantity)
}

/** Dinero en dólares a partir de millonésimas, por ejemplo "US$0,044". */
fun formatUsd(micros: Long, decimals: Int = 2): String =
    String.format(Locale.getDefault(), "US$%.${decimals}f", micros / 1_000_000.0)

/** Título grande, alineado a la izquierda, al comienzo de cada pantalla. */
@Composable
fun ScreenTitle(@StringRes title: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(
            start = BanapiSpacing.sm,
            end = BanapiSpacing.sm,
            top = BanapiSpacing.xl,
            bottom = BanapiSpacing.sm,
        ),
    )
}

/** Tarjeta base del estilo de la app. */
@Composable
fun BanapiCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(BanapiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(BanapiSpacing.md),
            content = content,
        )
    }
}

/** Encabezado de una tarjeta: ícono de acento, título y, opcionalmente, algo a la derecha. */
@Composable
fun CardHeader(
    icon: ImageVector,
    @StringRes title: Int,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.sm),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Etiqueta pequeña de estado, por ejemplo "Activa". */
@Composable
fun StatusChip(@StringRes label: Int) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = BanapiSpacing.sm, vertical = BanapiSpacing.xs),
        )
    }
}

/** Pantalla vacía: ícono en un círculo, título, texto y, opcionalmente, una acción. */
@Composable
fun EmptyState(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes body: Int,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BanapiSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val reducedMotion = rememberReducedMotion()
        val float by rememberInfiniteTransition(label = "emptyState").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(BanapiMotion.float / 2, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "float",
        )
        Box(
            modifier = Modifier
                .graphicsLayer {
                    if (!reducedMotion) translationY = float * BanapiMotion.floatOffset.toPx()
                }
                .size(BanapiSizes.emptyStateCircle)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(BanapiSizes.emptyStateIcon),
            )
        }
        Spacer(Modifier.height(BanapiSpacing.lg))
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BanapiSpacing.sm))
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(BanapiSpacing.lg))
            action()
        }
    }
}

/** Diálogo para confirmar una acción que borra algo. [extra] va debajo del texto (por ejemplo, una casilla). */
@Composable
fun ConfirmDialog(
    @StringRes title: Int,
    @StringRes body: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) = ConfirmDialog(stringResource(title), stringResource(body), onConfirm, onDismiss, extra)

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BanapiSpacing.md)) {
                Text(body)
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Colores de los campos de texto: relleno suave y borde solo al escribir. */
@Composable
fun filledFieldColors(): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerHighest,
        disabledContainerColor = colors.surfaceContainerHighest,
        errorContainerColor = colors.surfaceContainerHighest,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
    )
}
