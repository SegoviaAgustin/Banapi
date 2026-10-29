package app.banapi.ui.create

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.banapi.R
import app.banapi.ui.components.ConfirmDialog
import app.banapi.ui.components.filledFieldColors
import app.banapi.ui.theme.BanapiSizes
import app.banapi.ui.theme.BanapiSpacing

/** Panel lateral con todos los chats. */
@Composable
fun ChatListDrawer(
    chats: List<ChatSummary>,
    currentChatId: Long?,
    onOpen: (Long) -> Unit,
    onNewChat: () -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long, Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ChatSummary?>(null) }
    var deleting by remember { mutableStateOf<ChatSummary?>(null) }
    val untitled = stringResource(R.string.create_untitled)
    val filtered = chats.filter { it.title.contains(query.trim(), ignoreCase = true) }

    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = BanapiSpacing.xl, end = BanapiSpacing.sm, top = BanapiSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chats_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNewChat) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.create_new_chat))
            }
        }

        if (chats.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.chats_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = filledFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BanapiSpacing.md, vertical = BanapiSpacing.md),
            )
        }

        when {
            chats.isEmpty() -> EmptyListText(R.string.chats_empty)
            filtered.isEmpty() -> EmptyListText(R.string.chats_no_results)
            else -> LazyColumn(
                modifier = Modifier.padding(horizontal = BanapiSpacing.md),
                verticalArrangement = Arrangement.spacedBy(BanapiSpacing.xs),
            ) {
                items(filtered, key = { it.id }) { chat ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = chat.title.ifBlank { untitled },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        selected = chat.id == currentChatId,
                        onClick = { onOpen(chat.id) },
                        badge = {
                            if (chat.isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(BanapiSizes.buttonProgressSize),
                                    strokeWidth = BanapiSizes.buttonProgressStroke,
                                )
                            } else {
                                ChatMenu(
                                    onRename = { renaming = chat },
                                    onDelete = { deleting = chat },
                                )
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }
    }

    renaming?.let { chat ->
        RenameDialog(
            initial = chat.title,
            onConfirm = { onRename(chat.id, it) },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { chat ->
        var deleteImages by remember { mutableStateOf(false) }
        ConfirmDialog(
            title = R.string.chat_delete_title,
            body = R.string.chat_delete_body,
            onConfirm = { onDelete(chat.id, deleteImages) },
            onDismiss = { deleting = null },
            extra = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deleteImages = !deleteImages },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = deleteImages, onCheckedChange = { deleteImages = it })
                    Text(stringResource(R.string.chat_delete_images))
                }
            },
        )
    }
}

@Composable
private fun EmptyListText(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = BanapiSpacing.xl, vertical = BanapiSpacing.lg),
    )
}

@Composable
private fun ChatMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.chat_rename_title)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = filledFieldColors(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(title)
                    onDismiss()
                },
                enabled = title.isNotBlank(),
            ) {
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
