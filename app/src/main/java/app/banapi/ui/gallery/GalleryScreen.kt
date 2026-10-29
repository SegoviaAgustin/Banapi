package app.banapi.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.banapi.R
import app.banapi.data.GalleryImage
import app.banapi.ui.components.ScreenTitle
import app.banapi.ui.theme.BanapiSizes
import app.banapi.ui.theme.BanapiSpacing
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun GalleryScreen(
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    viewModel: GalleryViewModel = viewModel(factory = GalleryViewModel.Factory),
) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var openImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    val openImage = images.firstOrNull { it.file.path == openImagePath }?.file

    // El visor tiene su propio snackbar porque se muestra por encima de la app.
    val viewerSnackbarHostState = remember { SnackbarHostState() }
    val isViewerOpen by rememberUpdatedState(openImage != null)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val host = if (isViewerOpen) viewerSnackbarHostState else snackbarHostState
            host.showSnackbar(context.getString(message))
        }
    }

    if (images.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = BanapiSpacing.md),
        ) {
            ScreenTitle(R.string.nav_gallery)
            EmptyGallery(Modifier.weight(1f))
        }
    } else {
        ImageGrid(
            images = images,
            contentPadding = contentPadding,
            onOpen = { openImagePath = it.path },
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

/** Cuadrícula tipo mosaico: cada imagen conserva su proporción original. */
@Composable
private fun ImageGrid(
    images: List<GalleryImage>,
    contentPadding: PaddingValues,
    onOpen: (File) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
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
        items(images, key = { it.file.path }) { image ->
            AsyncImage(
                model = image.file,
                contentDescription = stringResource(R.string.gallery_image_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(image.aspectRatio)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onOpen(image.file) },
            )
        }
    }
}

@Composable
private fun EmptyGallery(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BanapiSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(BanapiSizes.emptyStateCircle)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(BanapiSizes.emptyStateIcon),
            )
        }
        Spacer(Modifier.height(BanapiSpacing.lg))
        Text(
            text = stringResource(R.string.gallery_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BanapiSpacing.sm))
        Text(
            text = stringResource(R.string.gallery_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
