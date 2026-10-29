package app.banapi.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.banapi.R
import app.banapi.data.GalleryImage
import app.banapi.ui.components.BoundsHolder
import app.banapi.ui.components.ConfirmDialog
import app.banapi.ui.components.EmptyState
import app.banapi.ui.components.ScreenTitle
import app.banapi.ui.components.appear
import app.banapi.ui.components.trackBounds
import app.banapi.ui.theme.BanapiColors
import app.banapi.ui.theme.BanapiMotion
import app.banapi.ui.theme.BanapiSizes
import app.banapi.ui.theme.BanapiSpacing
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

/**
 * Galería de imágenes creadas. Mantener presionada una imagen activa la selección múltiple;
 * mientras dura, [onSelectionModeChange] avisa para ocultar la barra de navegación.
 */
@Composable
fun GalleryScreen(
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    onOpenChat: (Long) -> Unit,
    onUseInCreate: (List<File>) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    viewModel: GalleryViewModel = viewModel(factory = GalleryViewModel.Factory),
) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var openImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var openImageOrigin by remember { mutableStateOf<Rect?>(null) }
    val openImage = images.firstOrNull { it.file.path == openImagePath }?.file

    // Selección múltiple: rutas de las imágenes elegidas (las borradas se descartan solas).
    var selectedPaths by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selected = images.filter { it.file.path in selectedPaths }.map { it.file }
    val selecting = selected.isNotEmpty()
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    fun toggle(file: File) {
        selectedPaths = if (file.path in selectedPaths) selectedPaths - file.path else selectedPaths + file.path
    }
    fun clearSelection() {
        selectedPaths = emptyList()
    }

    BackHandler(enabled = selecting) { clearSelection() }
    LaunchedEffect(selecting) { onSelectionModeChange(selecting) }
    DisposableEffect(Unit) { onDispose { onSelectionModeChange(false) } }

    // El visor tiene su propio snackbar porque se muestra por encima de la app.
    val viewerSnackbarHostState = remember { SnackbarHostState() }
    val isViewerOpen by rememberUpdatedState(openImage != null)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val host = if (isViewerOpen) viewerSnackbarHostState else snackbarHostState
            host.showSnackbar(message.resolve(context))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (images.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = BanapiSpacing.md),
            ) {
                ScreenTitle(R.string.nav_gallery)
                EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = R.string.gallery_empty_title,
                    body = R.string.gallery_empty_body,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            ImageGrid(
                images = images,
                contentPadding = contentPadding,
                selectedPaths = selectedPaths,
                selecting = selecting,
                onOpen = { file, origin ->
                    openImageOrigin = origin
                    openImagePath = file.path
                },
                onToggle = ::toggle,
            )
        }

        AnimatedVisibility(
            visible = selecting,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            SelectionTopBar(
                count = selected.size,
                allSelected = selected.size == images.size,
                onClose = ::clearSelection,
                onSelectAll = { selectedPaths = images.map { it.file.path } },
            )
        }

        AnimatedVisibility(
            visible = selecting,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = BanapiSizes.navBarBottomMargin),
        ) {
            SelectionActions(
                onShare = { context.startActivity(viewModel.shareIntent(selected)) },
                onSave = {
                    viewModel.saveToDevice(selected)
                    clearSelection()
                },
                onUseInCreate = {
                    onUseInCreate(selected)
                    clearSelection()
                },
                onDelete = { confirmDeleteSelected = true },
            )
        }
    }

    if (confirmDeleteSelected) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.gallery_delete_many_title, selected.size, selected.size),
            body = stringResource(R.string.gallery_delete_body),
            onConfirm = {
                viewModel.delete(selected)
                clearSelection()
            },
            onDismiss = { confirmDeleteSelected = false },
        )
    }

    // Chat donde se creó la imagen abierta, si todavía existe.
    val openImageChat by produceState<Long?>(null, openImage) {
        value = openImage?.let { viewModel.chatIdOf(it) }
    }

    if (openImage != null) {
        ImageViewer(
            file = openImage,
            origin = openImageOrigin,
            snackbarHostState = viewerSnackbarHostState,
            onDismiss = { openImagePath = null },
            onShare = { context.startActivity(viewModel.shareIntent(listOf(openImage))) },
            onSave = { viewModel.saveToDevice(listOf(openImage)) },
            onDelete = {
                openImagePath = null
                viewModel.delete(listOf(openImage))
            },
            onOpenChat = openImageChat?.let { chatId ->
                {
                    openImagePath = null
                    onOpenChat(chatId)
                }
            },
        )
    }
}

/** Cuadrícula tipo mosaico: cada imagen conserva su proporción. Al abrir la Galería entran en cascada. */
@Composable
private fun ImageGrid(
    images: List<GalleryImage>,
    contentPadding: PaddingValues,
    selectedPaths: List<String>,
    selecting: Boolean,
    onOpen: (File, Rect?) -> Unit,
    onToggle: (File) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current

    // Solo las que se ven al entrar aparecen en cascada; las demás, directo.
    var initialLoad by remember { mutableStateOf(true) }
    val appeared = remember { mutableSetOf<String>() }
    LaunchedEffect(Unit) {
        delay((BanapiMotion.staggerMaxItems * BanapiMotion.stagger + BanapiMotion.medium).toLong())
        initialLoad = false
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(BanapiSizes.galleryColumnMinWidth),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + BanapiSpacing.md,
            end = contentPadding.calculateEndPadding(layoutDirection) + BanapiSpacing.md,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.sm),
        verticalItemSpacing = BanapiSpacing.sm,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            ScreenTitle(R.string.nav_gallery)
        }
        itemsIndexed(images, key = { _, image -> image.file.path }) { index, image ->
            val path = image.file.path
            val animateIn = initialLoad && path !in appeared
            SideEffect { appeared += path }
            GridImage(
                image = image,
                isSelected = path in selectedPaths,
                selecting = selecting,
                onOpen = onOpen,
                onToggle = onToggle,
                modifier = Modifier
                    .animateItem()
                    .appear(animate = animateIn, index = index),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridImage(
    image: GalleryImage,
    isSelected: Boolean,
    selecting: Boolean,
    onOpen: (File, Rect?) -> Unit,
    onToggle: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val bounds = remember { BoundsHolder() }
    val scale by animateFloatAsState(
        targetValue = if (isSelected) BanapiMotion.selectedScale else 1f,
        animationSpec = BanapiMotion.smooth(),
        label = "selectedScale",
    )
    val shape = MaterialTheme.shapes.large

    Box(modifier = modifier) {
        AsyncImage(
            model = image.file,
            contentDescription = stringResource(R.string.gallery_image_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(image.aspectRatio)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .border(
                    BorderStroke(
                        BanapiSizes.pickerSelectedBorder,
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ),
                    shape,
                )
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .trackBounds(bounds)
                .combinedClickable(
                    onClick = { if (selecting) onToggle(image.file) else onOpen(image.file, bounds.value) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle(image.file)
                    },
                ),
        )
        AnimatedVisibility(
            visible = selecting,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(BanapiSpacing.sm),
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else BanapiColors.Viewer.content,
                modifier = Modifier
                    .size(BanapiSizes.pickerCheckIcon)
                    .clip(CircleShape)
                    .background(BanapiColors.Viewer.controlsScrim),
            )
        }
    }
}

@Composable
private fun SelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = BanapiSpacing.xs, vertical = BanapiSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
            }
            Text(
                text = pluralStringResource(R.plurals.gallery_selected_count, count, count),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (!allSelected) {
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.gallery_select_all))
                }
            }
        }
    }
}

/** Acciones para las imágenes elegidas, en el lugar de la barra de navegación. */
@Composable
private fun SelectionActions(
    onShare: () -> Unit,
    onSave: () -> Unit,
    onUseInCreate: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(BanapiSizes.navBarBorder, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(BanapiSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.xs),
        ) {
            SelectionAction(Icons.Outlined.Share, R.string.action_share, onClick = onShare)
            SelectionAction(Icons.Outlined.Download, R.string.action_save, onClick = onSave)
            SelectionAction(Icons.Outlined.AutoAwesome, R.string.gallery_use_in_create, onClick = onUseInCreate)
            SelectionAction(
                icon = Icons.Outlined.Delete,
                label = R.string.action_delete,
                color = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    @StringRes label: Int,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = BanapiSpacing.md, vertical = BanapiSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BanapiSpacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Text(text = stringResource(label), style = MaterialTheme.typography.labelSmall, color = color)
    }
}
