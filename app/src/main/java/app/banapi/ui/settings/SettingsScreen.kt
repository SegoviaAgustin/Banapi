package app.banapi.ui.settings

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.banapi.BuildConfig
import app.banapi.R
import app.banapi.data.ThemeMode
import app.banapi.ui.components.BanapiCard
import app.banapi.ui.components.CardHeader
import app.banapi.ui.components.ConfirmDialog
import app.banapi.ui.components.ScreenTitle
import app.banapi.ui.components.StatusChip
import app.banapi.ui.components.filledFieldColors
import app.banapi.ui.theme.BanapiFonts
import app.banapi.ui.theme.BanapiSizes
import app.banapi.ui.theme.BanapiSpacing

private const val AI_STUDIO_KEY_URL = "https://aistudio.google.com/apikey"

private enum class PendingConfirmation { RemoveKey, DeleteAllImages, DeleteAllChats }

@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmation by remember { mutableStateOf<PendingConfirmation?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(context.getString(it)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = BanapiSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BanapiSpacing.md),
    ) {
        ScreenTitle(R.string.nav_settings)

        if (state.isLoaded) {
            ApiKeyCard(
                state = state,
                onSave = viewModel::saveApiKey,
                onInputChange = viewModel::clearKeyError,
                onRemove = { confirmation = PendingConfirmation.RemoveKey },
            )
            UsageCard(
                usage = state.usage,
                onSetBalance = viewModel::setBalance,
                onClearBalance = viewModel::clearBalance,
            )
        }

        BanapiCard {
            CardHeader(icon = Icons.Outlined.Palette, title = R.string.settings_theme_title)
            ThemeSelector(selected = state.themeMode, onSelect = viewModel::setThemeMode)
        }

        BanapiCard {
            CardHeader(
                icon = Icons.Outlined.PhotoLibrary,
                title = R.string.settings_storage_title,
                trailing = {
                    Text(
                        text = Formatter.formatShortFileSize(context, state.imagesBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(R.plurals.settings_storage_count, state.imageCount, state.imageCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.imageCount > 0) {
                    TextButton(
                        onClick = { confirmation = PendingConfirmation.DeleteAllImages },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.settings_storage_delete_all))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(R.plurals.settings_chat_count, state.chatCount, state.chatCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.chatCount > 0) {
                    TextButton(
                        onClick = { confirmation = PendingConfirmation.DeleteAllChats },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.settings_storage_delete_chats))
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_about, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = BanapiSpacing.md),
        )
    }

    when (confirmation) {
        PendingConfirmation.RemoveKey -> ConfirmDialog(
            title = R.string.settings_remove_key_title,
            body = R.string.settings_remove_key_body,
            onConfirm = viewModel::removeApiKey,
            onDismiss = { confirmation = null },
        )
        PendingConfirmation.DeleteAllImages -> ConfirmDialog(
            title = R.string.settings_delete_all_title,
            body = R.string.settings_delete_all_body,
            onConfirm = viewModel::deleteAllImages,
            onDismiss = { confirmation = null },
        )
        PendingConfirmation.DeleteAllChats -> ConfirmDialog(
            title = R.string.settings_delete_chats_title,
            body = R.string.settings_delete_chats_body,
            onConfirm = viewModel::deleteAllChats,
            onDismiss = { confirmation = null },
        )
        null -> Unit
    }
}

@Composable
private fun ApiKeyCard(
    state: SettingsUiState,
    onSave: (String) -> Unit,
    onInputChange: () -> Unit,
    onRemove: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }

    // Al guardar o eliminar la key se vuelve a la vista normal.
    LaunchedEffect(state.savedApiKey) { editing = false }

    val savedKey = state.savedApiKey
    BanapiCard {
        if (savedKey != null && !editing) {
            CardHeader(
                icon = Icons.Outlined.Key,
                title = R.string.settings_api_title,
                trailing = { StatusChip(R.string.settings_api_active) },
            )
            Text(
                text = maskApiKey(savedKey),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = BanapiFonts.mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.sm)) {
                Button(
                    onClick = { editing = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(stringResource(R.string.settings_api_change))
                }
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        } else {
            ApiKeyForm(
                isChecking = state.isCheckingKey,
                error = state.keyError,
                onSave = onSave,
                onInputChange = onInputChange,
                onCancel = if (savedKey != null) {
                    {
                        onInputChange()
                        editing = false
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ApiKeyForm(
    isChecking: Boolean,
    error: KeyError?,
    onSave: (String) -> Unit,
    onInputChange: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    // La key no se guarda en el estado de la pantalla para que no quede fuera del cifrado.
    var input by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val canSave = input.isNotBlank() && !isChecking
    val colors = MaterialTheme.colorScheme

    CardHeader(
        icon = Icons.Outlined.Key,
        title = R.string.settings_api_title,
        trailing = {
            TextButton(
                onClick = { uriHandler.openUri(AI_STUDIO_KEY_URL) },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer),
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
            ) {
                Text(stringResource(R.string.settings_get_key))
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

    Text(
        text = stringResource(R.string.settings_api_description),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
    )

    OutlinedTextField(
        value = input,
        onValueChange = {
            input = it.trim()
            onInputChange()
        },
        placeholder = { Text(stringResource(R.string.settings_api_placeholder)) },
        singleLine = true,
        enabled = !isChecking,
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it.message)) } },
        shape = MaterialTheme.shapes.medium,
        colors = filledFieldColors(),
        trailingIcon = {
            IconButton(onClick = { keyVisible = !keyVisible }) {
                Icon(
                    imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (keyVisible) R.string.hide_key else R.string.show_key),
                )
            }
        },
        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(input) }),
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.sm)) {
        Button(onClick = { onSave(input) }, enabled = canSave) {
            if (isChecking) {
                CircularProgressIndicator(
                    color = LocalContentColor.current,
                    modifier = Modifier.size(BanapiSizes.buttonProgressSize),
                    strokeWidth = BanapiSizes.buttonProgressStroke,
                )
            } else {
                Text(stringResource(R.string.action_save))
            }
        }
        if (onCancel != null) {
            TextButton(
                onClick = onCancel,
                enabled = !isChecking,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

/** Selector Sistema / Claro / Oscuro en forma de píldora. */
@Composable
private fun ThemeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(BanapiSpacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.xs),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val container by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "themeOptionContainer",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "themeOptionContent",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(container)
                    .selectable(selected = isSelected, onClick = { onSelect(mode) }, role = Role.RadioButton)
                    .padding(vertical = BanapiSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(mode.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                )
            }
        }
    }
}

private val ThemeMode.label: Int
    @StringRes get() = when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }

/** Muestra solo el principio y el final de la key, por ejemplo "AIza••••x9Qw". */
private fun maskApiKey(key: String): String =
    if (key.length <= 8) "••••••••" else key.take(4) + "••••" + key.takeLast(4)
