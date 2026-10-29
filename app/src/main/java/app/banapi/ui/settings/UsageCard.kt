package app.banapi.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.banapi.R
import app.banapi.ui.components.BanapiCard
import app.banapi.ui.components.CardHeader
import app.banapi.ui.components.filledFieldColors
import app.banapi.ui.components.formatUsd
import app.banapi.ui.theme.BanapiMotion
import app.banapi.ui.theme.BanapiSpacing

private const val AI_STUDIO_BILLING_URL = "https://aistudio.google.com/billing"

/** Gasto estimado del mes y total, y cuánto queda del saldo que anotó el usuario. */
@Composable
fun UsageCard(
    usage: UsageState,
    onSetBalance: (String) -> Boolean,
    onClearBalance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingBalance by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme

    BanapiCard(modifier) {
        CardHeader(
            icon = Icons.Outlined.AccountBalanceWallet,
            title = R.string.settings_usage_title,
            trailing = {
                TextButton(
                    onClick = { uriHandler.openUri(AI_STUDIO_BILLING_URL) },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer),
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                ) {
                    Text(stringResource(R.string.settings_balance_real))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = ButtonDefaults.IconSpacing)
                            .size(ButtonDefaults.IconSize),
                    )
                }
            },
        )

        UsageRow(R.string.settings_usage_month, usage.monthCostMicros, usage.monthImages)
        UsageRow(R.string.settings_usage_total, usage.totalCostMicros, usage.totalImages)

        HorizontalDivider(color = colors.outlineVariant)

        val balance = usage.balanceMicros
        if (balance == null) {
            Text(
                text = stringResource(R.string.settings_balance_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            TextButton(onClick = { editingBalance = true }) {
                Text(stringResource(R.string.settings_balance_set))
            }
        } else {
            Text(
                text = stringResource(
                    R.string.settings_balance_remaining,
                    formatUsd(usage.remainingMicros),
                    formatUsd(balance),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            // La barra se llena animada hasta lo que queda.
            val remaining by animateFloatAsState(
                targetValue = (usage.remainingMicros.toFloat() / balance).coerceIn(0f, 1f),
                animationSpec = tween(BanapiMotion.slow),
                label = "balance",
            )
            LinearProgressIndicator(
                progress = { remaining },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.sm)) {
                TextButton(onClick = { editingBalance = true }) {
                    Text(stringResource(R.string.settings_balance_change))
                }
                TextButton(
                    onClick = onClearBalance,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant),
                ) {
                    Text(stringResource(R.string.settings_balance_remove))
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_usage_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }

    if (editingBalance) {
        BalanceDialog(onSave = onSetBalance, onDismiss = { editingBalance = false })
    }
}

@Composable
private fun UsageRow(@StringRes label: Int, costMicros: Long, images: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = pluralStringResource(R.plurals.settings_usage_images, images, formatUsd(costMicros), images),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BalanceDialog(onSave: (String) -> Boolean, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.settings_balance_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BanapiSpacing.md)) {
                Text(stringResource(R.string.settings_balance_body))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    placeholder = { Text(stringResource(R.string.settings_balance_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.medium,
                    colors = filledFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (onSave(amount)) onDismiss() }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
