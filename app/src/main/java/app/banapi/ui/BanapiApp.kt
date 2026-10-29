package app.banapi.ui

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.banapi.R
import app.banapi.ui.gallery.GalleryScreen
import app.banapi.ui.settings.SettingsScreen
import app.banapi.ui.theme.BanapiSizes
import app.banapi.ui.theme.BanapiSpacing

/** Pestañas de la barra de navegación. */
enum class Destination(@param:StringRes val label: Int, val icon: ImageVector) {
    Gallery(R.string.nav_gallery, Icons.Outlined.PhotoLibrary),
    Settings(R.string.nav_settings, Icons.Outlined.Settings),
}

@Composable
fun BanapiApp() {
    var current by rememberSaveable { mutableStateOf(Destination.Gallery) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Espacio libre para las barras del sistema y la barra de navegación.
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val contentPadding = PaddingValues(
        top = systemBars.calculateTopPadding(),
        bottom = systemBars.calculateBottomPadding() + BanapiSizes.navBarReservedSpace,
    )

    // Surface para que los textos tomen el color del tema.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (current) {
                Destination.Gallery -> GalleryScreen(snackbarHostState, contentPadding)
                Destination.Settings -> SettingsScreen(snackbarHostState, contentPadding)
            }

            // Fondo detrás de la barra de estado.
            Spacer(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background),
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding()),
            )

            FloatingNavBar(
                current = current,
                onSelect = { current = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = BanapiSizes.navBarBottomMargin),
            )
        }
    }
}

/** Barra de navegación flotante. */
@Composable
private fun FloatingNavBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(BanapiSizes.navBarBorder, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(BanapiSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(BanapiSpacing.xs),
        ) {
            Destination.entries.forEach { destination ->
                NavItem(
                    destination = destination,
                    selected = destination == current,
                    onClick = { onSelect(destination) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) colors.primary else Color.Transparent,
        label = "navItemContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        label = "navItemContent",
    )

    Box(
        modifier = Modifier
            .size(BanapiSizes.navItemWidth, BanapiSizes.navItemHeight)
            .clip(CircleShape)
            .background(container)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = stringResource(destination.label),
            tint = content,
        )
    }
}
