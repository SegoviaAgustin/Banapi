package app.banapi.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.banapi.R
import app.banapi.ui.components.rememberReducedMotion
import app.banapi.ui.create.ChatListDrawer
import app.banapi.ui.create.CreateScreen
import app.banapi.ui.create.CreateViewModel
import app.banapi.ui.gallery.GalleryScreen
import app.banapi.ui.settings.SettingsScreen
import app.banapi.ui.theme.BanapiMotion
import app.banapi.ui.theme.BanapiSizes
import app.banapi.ui.theme.BanapiSpacing
import kotlinx.coroutines.launch

/** Pestañas de la barra de navegación. */
enum class Destination(@param:StringRes val label: Int, val icon: ImageVector) {
    Create(R.string.nav_create, Icons.Outlined.AutoAwesome),
    Gallery(R.string.nav_gallery, Icons.Outlined.PhotoLibrary),
    Settings(R.string.nav_settings, Icons.Outlined.Settings),
}

@Composable
fun BanapiApp() {
    var current by rememberSaveable { mutableStateOf(Destination.Create) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Espacio libre para las barras del sistema y la barra de navegación.
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val contentPadding = PaddingValues(
        top = systemBars.calculateTopPadding(),
        bottom = systemBars.calculateBottomPadding() + BanapiSizes.navBarReservedSpace,
    )

    // Lista de chats de Crear: va acá para que cubra también la barra de navegación.
    val createViewModel: CreateViewModel = viewModel(factory = CreateViewModel.Factory)
    val createState by createViewModel.uiState.collectAsStateWithLifecycle()
    val chatList by createViewModel.chatList.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    fun closeDrawer() = scope.launch { drawerState.close() }

    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Se abre con el botón de menú; con gestos solo se cierra.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ChatListDrawer(
                chats = chatList,
                currentChatId = createState.chatId,
                onOpen = {
                    createViewModel.openChat(it)
                    closeDrawer()
                },
                onNewChat = {
                    createViewModel.newChat()
                    closeDrawer()
                },
                onRename = createViewModel::renameChat,
                onDelete = createViewModel::deleteChat,
            )
        },
    ) {
        AppContent(
            current = current,
            onSelect = { current = it },
            snackbarHostState = snackbarHostState,
            contentPadding = contentPadding,
            createViewModel = createViewModel,
            onOpenChats = { scope.launch { drawerState.open() } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppContent(
    current: Destination,
    onSelect: (Destination) -> Unit,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    createViewModel: CreateViewModel,
    onOpenChats: () -> Unit,
) {
    // Surface para que los textos tomen el color del tema.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // La Galería oculta la barra mientras se eligen imágenes, para mostrar sus acciones.
        var gallerySelecting by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    fadeIn(tween(BanapiMotion.medium)) togetherWith fadeOut(tween(BanapiMotion.fast))
                },
                label = "screen",
            ) { destination ->
                when (destination) {
                    Destination.Create -> CreateScreen(
                        contentPadding = contentPadding,
                        onOpenChats = onOpenChats,
                        onOpenSettings = { onSelect(Destination.Settings) },
                        viewModel = createViewModel,
                    )
                    Destination.Gallery -> GalleryScreen(
                        snackbarHostState = snackbarHostState,
                        contentPadding = contentPadding,
                        onOpenChat = {
                            createViewModel.openChat(it)
                            onSelect(Destination.Create)
                        },
                        onUseInCreate = {
                            createViewModel.attach(it)
                            onSelect(Destination.Create)
                        },
                        onSelectionModeChange = { gallerySelecting = it },
                    )
                    Destination.Settings -> SettingsScreen(snackbarHostState, contentPadding)
                }
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

            // Con el teclado abierto se oculta, para dejar lugar a lo que se escribe.
            AnimatedVisibility(
                visible = !WindowInsets.isImeVisible && !gallerySelecting,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = BanapiSizes.navBarBottomMargin),
            ) {
                FloatingNavBar(current = current, onSelect = onSelect)
            }
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
        // La píldora amarilla se desliza hasta la pestaña elegida.
        val indicatorOffset by animateDpAsState(
            targetValue = (BanapiSizes.navItemWidth + BanapiSpacing.xs) * current.ordinal,
            animationSpec = BanapiMotion.smooth(),
            label = "navIndicator",
        )
        Box(modifier = Modifier.padding(BanapiSpacing.xs)) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .size(BanapiSizes.navItemWidth, BanapiSizes.navItemHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Row(
                modifier = Modifier.selectableGroup(),
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
}

@Composable
private fun NavItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        animationSpec = tween(BanapiMotion.fast),
        label = "navItemContent",
    )

    // Rebote del ícono al elegir la pestaña.
    val iconScale = remember { Animatable(1f) }
    val reducedMotion = rememberReducedMotion()
    LaunchedEffect(selected) {
        if (selected && !reducedMotion) {
            iconScale.snapTo(BanapiMotion.navBounceScale)
            iconScale.animateTo(1f, BanapiMotion.bouncy())
        }
    }

    Box(
        modifier = Modifier
            .size(BanapiSizes.navItemWidth, BanapiSizes.navItemHeight)
            .clip(CircleShape)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = stringResource(destination.label),
            tint = content,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale.value
                scaleY = iconScale.value
            },
        )
    }
}
