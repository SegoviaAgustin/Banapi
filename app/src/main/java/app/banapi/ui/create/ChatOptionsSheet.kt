package app.banapi.ui.create

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.banapi.R
import app.banapi.data.chat.ChatOptions
import app.banapi.data.chat.ResponseMode
import app.banapi.ui.components.filledFieldColors
import app.banapi.ui.theme.BanapiSpacing

private const val INSTRUCTIONS_MAX_LINES = 4

/** Una opción de un selector: el valor que se manda a la API (null = Auto) y su texto. */
private class Choice<T>(val value: T, @param:StringRes val label: Int)

/** Opciones avanzadas del chat. Se guardan al cerrar la hoja. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatOptionsSheet(
    initial: ChatOptions,
    totalTokens: Int,
    totalCostMicros: Long,
    onSave: (ChatOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var options by remember { mutableStateOf(initial) }
    val currentOptions by rememberUpdatedState(options)

    ModalBottomSheet(
        onDismissRequest = {
            if (currentOptions != initial) onSave(currentOptions)
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BanapiSpacing.xl)
                .padding(bottom = BanapiSpacing.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(BanapiSpacing.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.options_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (!options.isDefault) {
                    TextButton(onClick = { options = ChatOptions() }) {
                        Text(stringResource(R.string.options_reset))
                    }
                }
            }

            Section(R.string.options_response) {
                Segmented(
                    choices = listOf(
                        Choice(ResponseMode.ImagesOnly, R.string.options_images_only),
                        Choice(ResponseMode.ImagesAndText, R.string.options_images_text),
                    ),
                    selected = options.responseMode,
                    onSelect = { options = options.copy(responseMode = it) },
                )
            }

            Section(R.string.options_aspect_ratio) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.sm),
                ) {
                    (listOf<String?>(null) + ChatOptions.ASPECT_RATIOS).forEach { ratio ->
                        FilterChip(
                            selected = options.aspectRatio == ratio,
                            onClick = { options = options.copy(aspectRatio = ratio) },
                            label = { Text(ratio ?: stringResource(R.string.options_auto)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            Section(R.string.options_resolution, note = R.string.options_resolution_note) {
                Segmented(
                    choices = listOf(
                        Choice(null, R.string.options_resolution_auto),
                        Choice(ChatOptions.SIZE_2K, R.string.options_size_2k),
                        Choice(ChatOptions.SIZE_4K, R.string.options_size_4k),
                    ),
                    selected = options.imageSize,
                    onSelect = { options = options.copy(imageSize = it) },
                )
            }

            Section(R.string.options_thinking, note = R.string.options_thinking_note) {
                Segmented(
                    choices = listOf(
                        Choice(null, R.string.options_thinking_auto),
                        Choice(ChatOptions.THINKING_MINIMAL, R.string.options_thinking_minimal),
                        Choice(ChatOptions.THINKING_HIGH, R.string.options_thinking_high),
                    ),
                    selected = options.thinkingLevel,
                    onSelect = { options = options.copy(thinkingLevel = it) },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.options_search), style = MaterialTheme.typography.titleSmall)
                    Note(R.string.options_search_note)
                }
                Switch(
                    checked = options.googleSearch,
                    onCheckedChange = { options = options.copy(googleSearch = it) },
                )
            }

            Section(R.string.options_instructions) {
                OutlinedTextField(
                    value = options.instructions,
                    onValueChange = { options = options.copy(instructions = it) },
                    placeholder = { Text(stringResource(R.string.options_instructions_placeholder)) },
                    maxLines = INSTRUCTIONS_MAX_LINES,
                    shape = MaterialTheme.shapes.medium,
                    colors = filledFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (totalTokens > 0) {
                Note(
                    text = R.string.options_usage,
                    arg = usageLabel(totalCostMicros, totalTokens, decimals = 2),
                )
            }
        }
    }
}

@Composable
private fun Section(
    @StringRes title: Int,
    @StringRes note: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BanapiSpacing.sm)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        content()
        note?.let { Note(it) }
    }
}

@Composable
private fun Note(@StringRes text: Int, arg: String? = null) {
    Text(
        text = if (arg == null) stringResource(text) else stringResource(text, arg),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun <T> Segmented(
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice.value == selected,
                onClick = { onSelect(choice.value) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                label = { Text(stringResource(choice.label)) },
            )
        }
    }
}
