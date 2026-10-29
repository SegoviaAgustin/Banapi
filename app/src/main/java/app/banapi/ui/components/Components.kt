package app.banapi.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.banapi.ui.theme.BanapiSpacing

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
