package app.banapi.ui.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.banapi.R
import app.banapi.ui.components.EmptyState
import app.banapi.ui.gallery.ImageViewer
import app.banapi.ui.theme.BanapiSpacing
import java.io.File

@Composable
fun CreateScreen(
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    onOpenChats: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CreateViewModel = viewModel(factory = CreateViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var draft by rememberSaveable { mutableStateOf("") }

    var openImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    val openImage = state.messages
        .flatMap { it.images }
        .firstOrNull { it.exists && it.file.path == openImagePath }
        ?.file

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS),
    ) { uris -> if (uris.isNotEmpty()) viewModel.attachFromPhone(uris) }
    var showGalleryPicker by rememberSaveable { mutableStateOf(false) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var markingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val galleryImages by viewModel.galleryImages.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Con cada mensaje nuevo, y cuando una respuesta termina, se baja hasta el final del chat.
    val listState = rememberLazyListState()
    val newest = state.messages.lastOrNull()
    LaunchedEffect(newest?.id, newest?.status) {
        if (newest != null) listState.animateScrollToItem(0)
    }

    // El visor tiene su propio snackbar porque se muestra por encima de la app.
    val viewerSnackbarHostState = remember { SnackbarHostState() }
    val isViewerOpen by rememberUpdatedState(openImage != null)
    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            val host = if (isViewerOpen) viewerSnackbarHostState else snackbarHostState
            host.showSnackbar(context.getString(message))
        }
    }

    // Abajo se deja el espacio que sea mayor: el de la barra de navegación o el del teclado.
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottomPadding = max(contentPadding.calculateBottomPadding(), imeBottom)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = bottomPadding),
    ) {
        ChatTopBar(
            title = when {
                state.title.isNotBlank() -> state.title
                state.messages.isEmpty() -> stringResource(R.string.create_new_chat)
                else -> stringResource(R.string.create_untitled)
            },
            showNewChat = state.messages.isNotEmpty(),
            onOpenChats = onOpenChats,
            onNewChat = viewModel::newChat,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                !state.isLoaded -> Unit
                state.messages.isEmpty() -> EmptyChat(
                    hasApiKey = state.hasApiKey,
                    onOpenSettings = onOpenSettings,
                )
                else -> MessageList(
                    messages = state.messages,
                    listState = listState,
                    imageActions = ImageActions(
                        onOpen = { openImagePath = it.path },
                        onEdit = { viewModel.attach(listOf(it)) },
                        onSave = viewModel::saveToDevice,
                        onShare = { context.startActivity(viewModel.shareIntent(it)) },
                    ),
                    onRetry = viewModel::retry,
                )
            }
        }

        if (state.showLongChatNotice) {
            LongChatNotice(onNewChat = viewModel::newChat, onDismiss = viewModel::dismissLongChatNotice)
        }

        Composer(
            text = draft,
            onTextChange = { draft = it },
            attachments = state.attachments,
            onRemoveAttachment = viewModel::removeAttachment,
            onMarkAttachment = { markingPath = it.path },
            onAttachFromPhone = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onAttachFromGallery = {
                viewModel.refreshGallery()
                showGalleryPicker = true
            },
            hasCustomOptions = !state.options.isDefault,
            onOpenOptions = { showOptions = true },
            isGenerating = state.isGenerating,
            onSend = {
                if (viewModel.send(draft)) {
                    draft = ""
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
            },
            onStop = viewModel::stop,
        )
    }

    markingPath?.let { path ->
        val original = File(path)
        MarkupEditor(
            file = original,
            loadBitmap = viewModel::loadForMarkup,
            onDone = { viewModel.saveMarked(original, it) },
            onDismiss = { markingPath = null },
        )
    }

    if (showOptions) {
        ChatOptionsSheet(
            initial = state.options,
            totalTokens = state.totalTokens,
            totalCostMicros = state.totalCostMicros,
            onSave = viewModel::updateOptions,
            onDismiss = { showOptions = false },
        )
    }

    if (showGalleryPicker) {
        GalleryPickerSheet(
            images = galleryImages,
            maxSelection = MAX_ATTACHMENTS - state.attachments.size,
            onConfirm = viewModel::attach,
            onDismiss = { showGalleryPicker = false },
        )
    }

    if (openImage != null) {
        ImageViewer(
            file = openImage,
            snackbarHostState = viewerSnackbarHostState,
            onDismiss = { openImagePath = null },
            onShare = { context.startActivity(viewModel.shareIntent(openImage)) },
            onSave = { viewModel.saveToDevice(openImage) },
            onDelete = {
                openImagePath = null
                viewModel.delete(openImage)
            },
        )
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    showNewChat: Boolean,
    onOpenChats: () -> Unit,
    onNewChat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BanapiSpacing.xs, vertical = BanapiSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenChats) {
            Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.chats_open_list))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showNewChat) {
            IconButton(onClick = onNewChat) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.create_new_chat))
            }
        }
    }
}

@Composable
private fun EmptyChat(hasApiKey: Boolean, onOpenSettings: () -> Unit) {
    if (hasApiKey) {
        EmptyState(
            icon = Icons.Outlined.AutoAwesome,
            title = R.string.create_empty_title,
            body = R.string.create_empty_body,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        EmptyState(
            icon = Icons.Outlined.AutoAwesome,
            title = R.string.create_empty_title,
            body = R.string.create_empty_no_key,
            modifier = Modifier.fillMaxSize(),
            action = {
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.create_go_to_settings))
                }
            },
        )
    }
}

/** Sugerencia de empezar otro chat cuando este ya tiene muchos mensajes. */
@Composable
private fun LongChatNotice(onNewChat: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BanapiSpacing.md),
    ) {
        Row(
            modifier = Modifier.padding(start = BanapiSpacing.lg, end = BanapiSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.create_long_chat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onNewChat) {
                Text(stringResource(R.string.create_new_chat))
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
