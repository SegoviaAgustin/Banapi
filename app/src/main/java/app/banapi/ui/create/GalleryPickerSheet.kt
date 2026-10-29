package app.banapi.ui.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import app.banapi.R
import app.banapi.data.GalleryImage
import app.banapi.ui.theme.BanapiSizes
import app.banapi.ui.theme.BanapiSpacing
import coil3.compose.AsyncImage
import java.io.File

private const val COLUMNS = 3

/** Hoja para elegir imágenes de la galería de Banapi y adjuntarlas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryPickerSheet(
    images: List<GalleryImage>,
    maxSelection: Int,
    onConfirm: (List<File>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(emptyList<File>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = BanapiSpacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(BanapiSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.create_attach_gallery),
                style = MaterialTheme.typography.titleLarge,
            )

            if (images.isEmpty()) {
                Text(
                    text = stringResource(R.string.gallery_empty_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = BanapiSpacing.lg),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(BanapiSpacing.xs),
                    modifier = Modifier.heightIn(max = BanapiSizes.pickerMaxHeight),
                ) {
                    items(images, key = { it.file.path }) { image ->
                        val isSelected = image.file in selected
                        PickerItem(
                            file = image.file,
                            isSelected = isSelected,
                            onClick = {
                                selected = when {
                                    isSelected -> selected - image.file
                                    selected.size < maxSelection -> selected + image.file
                                    else -> selected
                                }
                            },
                        )
                    }
                }

                Button(
                    onClick = {
                        onConfirm(selected)
                        onDismiss()
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = BanapiSpacing.lg),
                ) {
                    Text(stringResource(R.string.create_attach_confirm, selected.size))
                }
            }
        }
    }
}

@Composable
private fun PickerItem(file: File, isSelected: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .then(
                if (isSelected) {
                    Modifier.border(BorderStroke(BanapiSizes.pickerSelectedBorder, MaterialTheme.colorScheme.primary), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = file,
            contentDescription = stringResource(R.string.gallery_image_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(BanapiSpacing.xs)
                    .size(BanapiSizes.pickerCheckIcon),
            )
        }
    }
}
