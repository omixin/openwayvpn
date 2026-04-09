package com.omix.openwayvpn.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.omix.openwayvpn.R
import com.omix.openwayvpn.ui.theme.OpenwayvpnTheme
import com.omix.openwayvpn.ui.FloatingParticlesBackground
import com.omix.openwayvpn.vpn.MyVpnService
import com.omix.openwayvpn.vpn.ProfileImportHelper
import com.omix.openwayvpn.vpn.VlessProfileStore
import kotlinx.coroutines.delay

@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by MyVpnService.state.collectAsState()

    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.VPN) }
    val backStack = remember { mutableListOf<AppScreen>() }

    fun navigateTo(screen: AppScreen) {
        backStack.add(currentScreen)
        currentScreen = screen
    }

    fun goBack() {
        if (backStack.isNotEmpty()) {
            currentScreen = backStack.removeAt(backStack.lastIndex)
        }
    }

    // Handle system back button
    BackHandler(enabled = backStack.isNotEmpty()) {
        goBack()
    }

    var presetUris by remember { mutableStateOf(VlessProfileStore.getAllUris(context)) }
    var activeUri by remember { mutableStateOf(VlessProfileStore.getActiveUri(context)) }
    var pendingConnectUi by rememberSaveable { mutableStateOf(false) }
    var connectUiStartedAtMs by rememberSaveable { mutableLongStateOf(0L) }

    val refreshPresets: () -> Unit = {
        presetUris = VlessProfileStore.getAllUris(context)
        activeUri = VlessProfileStore.getActiveUri(context)
    }

    LaunchedEffect(state) {
        val isRunning = state == context.getString(R.string.vpn_state_running)
        if (isRunning && pendingConnectUi) {
            val minVisibleMs = 700L
            val elapsed = System.currentTimeMillis() - connectUiStartedAtMs
            if (elapsed in 0 until minVisibleMs) {
                delay(minVisibleMs - elapsed)
            }
            pendingConnectUi = false
        } else if (state == context.getString(R.string.vpn_state_stopped) ||
            state == context.getString(R.string.vpn_state_error) ||
            state == context.getString(R.string.vpn_state_permission_denied)
        ) {
            pendingConnectUi = false
        }
    }

    val status = when {
        state == context.getString(R.string.vpn_state_running) -> VpnUiStatus.ONLINE
        state == context.getString(R.string.vpn_state_connecting) -> VpnUiStatus.CONNECTING
        pendingConnectUi -> VpnUiStatus.CONNECTING
        else -> VpnUiStatus.OFFLINE
    }

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }

    val centerColor by animateColorAsState(
        targetValue = when (currentScreen) {
            AppScreen.PROFILES -> Color(0xFF2A3042)
            AppScreen.SETTINGS -> Color(0xFF1A1A24)
            AppScreen.ABOUT -> Color(0xFF1A1A24)
            // TODO/TEST: AppScreen.TEST -> Color(0xFF1A2420)
            else -> Color(0xFF1A1A24)
        },
        animationSpec = tween(560, easing = FastOutSlowInEasing),
        label = "centerColor"
    )
    val edgeColor by animateColorAsState(
        targetValue = when (currentScreen) {
            AppScreen.PROFILES -> Color(0xFF121726)
            AppScreen.SETTINGS -> Color(0xFF0D0D12)
            AppScreen.ABOUT -> Color(0xFF0D0D12)
            // TODO/TEST: AppScreen.TEST -> Color(0xFF0F1512)
            else -> Color(0xFF0D0D12)
        },
        animationSpec = tween(560, easing = FastOutSlowInEasing),
        label = "edgeColor"
    )
    val ambientColor by animateColorAsState(
        targetValue = when (currentScreen) {
            AppScreen.VPN -> when (status) {
                VpnUiStatus.ONLINE -> Color(0xFF00E676).copy(alpha = 0.22f)
                VpnUiStatus.CONNECTING -> Color(0xFF00D4FF).copy(alpha = 0.26f)
                VpnUiStatus.OFFLINE -> Color(0xFF2A2A35).copy(alpha = 0.48f)
            }
            AppScreen.PROFILES -> Color(0xFF67A0FF).copy(alpha = 0.24f)
            AppScreen.SETTINGS -> Color(0xFF2A2A35).copy(alpha = 0.35f)
            AppScreen.ABOUT -> Color(0xFF2A2A35).copy(alpha = 0.35f)
            // TODO/TEST: AppScreen.TEST -> Color(0xFF00E676).copy(alpha = 0.18f)
        },
        animationSpec = tween(560, easing = FastOutSlowInEasing),
        label = "ambientColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(ambientColor, centerColor, edgeColor),
                    radius = 1500f
                )
            )
    ) {
        FloatingParticlesBackground(
            connected = status == VpnUiStatus.ONLINE,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    when {
                        // VPN -> PROFILES
                        initialState == AppScreen.VPN && targetState == AppScreen.PROFILES -> {
                            (slideInVertically(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                initialOffsetY = { it }
                            ) + fadeIn(tween(360)) + scaleIn(
                                initialScale = 0.98f,
                                animationSpec = tween(420, easing = FastOutSlowInEasing)
                            )).togetherWith(
                                slideOutVertically(
                                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                                    targetOffsetY = { -it / 3 }
                                ) + fadeOut(tween(300)) + scaleOut(
                                    targetScale = 0.95f,
                                    animationSpec = tween(360, easing = FastOutSlowInEasing)
                                )
                            ).using(SizeTransform(clip = false))
                        }
                        // PROFILES -> VPN
                        initialState == AppScreen.PROFILES && targetState == AppScreen.VPN -> {
                            (slideInVertically(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                initialOffsetY = { -it }
                            ) + fadeIn(tween(360)) + scaleIn(
                                initialScale = 0.95f,
                                animationSpec = tween(420, easing = FastOutSlowInEasing)
                            )).togetherWith(
                                slideOutVertically(
                                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                                    targetOffsetY = { it / 3 }
                                ) + fadeOut(tween(300)) + scaleOut(
                                    targetScale = 0.98f,
                                    animationSpec = tween(360, easing = FastOutSlowInEasing)
                                )
                            ).using(SizeTransform(clip = false))
                        }
                        // SETTINGS -> VPN (settings slides UP)
                        initialState == AppScreen.SETTINGS && targetState == AppScreen.VPN -> {
                            (slideInVertically(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                initialOffsetY = { it }
                            ) + fadeIn(tween(360)) + scaleIn(
                                initialScale = 0.98f,
                                animationSpec = tween(420, easing = FastOutSlowInEasing)
                            )).togetherWith(
                                slideOutVertically(
                                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                                    targetOffsetY = { -it }
                                ) + fadeOut(tween(300)) + scaleOut(
                                    targetScale = 0.98f,
                                    animationSpec = tween(360, easing = FastOutSlowInEasing)
                                )
                            ).using(SizeTransform(clip = false))
                        }
                        // VPN -> SETTINGS (settings slides in from TOP)
                        initialState == AppScreen.VPN && targetState == AppScreen.SETTINGS -> {
                            (slideInVertically(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                initialOffsetY = { -it }
                            ) + fadeIn(tween(360)) + scaleIn(
                                initialScale = 0.98f,
                                animationSpec = tween(420, easing = FastOutSlowInEasing)
                            )).togetherWith(
                                slideOutVertically(
                                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                                    targetOffsetY = { it }
                                ) + fadeOut(tween(300)) + scaleOut(
                                    targetScale = 0.98f,
                                    animationSpec = tween(360, easing = FastOutSlowInEasing)
                                )
                            ).using(SizeTransform(clip = false))
                        }
                        // SETTINGS -> PROFILES
                        initialState == AppScreen.SETTINGS && targetState == AppScreen.PROFILES -> {
                            (slideInVertically(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                initialOffsetY = { it }
                            ) + fadeIn(tween(360)) + scaleIn(
                                initialScale = 0.95f,
                                animationSpec = tween(420, easing = FastOutSlowInEasing)
                            )).togetherWith(
                                slideOutVertically(
                                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                                    targetOffsetY = { -it }
                                ) + fadeOut(tween(300)) + scaleOut(
                                    targetScale = 0.98f,
                                    animationSpec = tween(360, easing = FastOutSlowInEasing)
                                )
                            ).using(SizeTransform(clip = false))
                        }
                        // SETTINGS -> ABOUT (slides in from LEFT)
                        initialState == AppScreen.SETTINGS && targetState == AppScreen.ABOUT -> {
                            (slideInHorizontally(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                initialOffsetX = { -it }
                            ) + fadeIn(tween(360)) + scaleIn(
                                initialScale = 0.98f,
                                animationSpec = tween(420, easing = FastOutSlowInEasing)
                            )).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                                    targetOffsetX = { it }
                                ) + fadeOut(tween(300)) + scaleOut(
                                    targetScale = 0.98f,
                                    animationSpec = tween(360, easing = FastOutSlowInEasing)
                                )
                            ).using(SizeTransform(clip = false))
                        }
                        // ABOUT -> SETTINGS (slides back to LEFT)
                        initialState == AppScreen.ABOUT && targetState == AppScreen.SETTINGS -> {
                            (slideInHorizontally(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                initialOffsetX = { it }
                            ) + fadeIn(tween(360)) + scaleIn(
                                initialScale = 0.98f,
                                animationSpec = tween(420, easing = FastOutSlowInEasing)
                            )).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                                    targetOffsetX = { -it }
                                ) + fadeOut(tween(300)) + scaleOut(
                                    targetScale = 0.98f,
                                    animationSpec = tween(360, easing = FastOutSlowInEasing)
                                )
                            ).using(SizeTransform(clip = false))
                        }
                        // PROFILES -> SETTINGS (default fallback)
                        else -> {
                            (slideInVertically(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                initialOffsetY = { -it }
                            ) + fadeIn(tween(360)) + scaleIn(
                                initialScale = 0.98f,
                                animationSpec = tween(420, easing = FastOutSlowInEasing)
                            )).togetherWith(
                                slideOutVertically(
                                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                                    targetOffsetY = { it }
                                ) + fadeOut(tween(300)) + scaleOut(
                                    targetScale = 0.95f,
                                    animationSpec = tween(360, easing = FastOutSlowInEasing)
                                )
                            ).using(SizeTransform(clip = false))
                        }
                    }
                    // TODO/TEST: Additional transitions for TEST screen
                },
                label = "screenTransition"
            ) { screen ->
                when (screen) {
                    AppScreen.VPN -> VpnScreen(
                        status = status,
                        activeUri = activeUri,
                        onOpenProfiles = { navigateTo(AppScreen.PROFILES) },
                        onOpenSettings = { navigateTo(AppScreen.SETTINGS) },
                        // TODO/TEST: onOpenTest = { navigateTo(AppScreen.TEST) },
                        onConnectRequested = {
                            pendingConnectUi = true
                            connectUiStartedAtMs = System.currentTimeMillis()
                        },
                        onDisconnectRequested = { pendingConnectUi = false },
                    )

                    AppScreen.PROFILES -> ProfilesScreen(
                        presets = presetUris,
                        activeUri = activeUri,
                        onBack = { goBack() },
                        onImportFromClipboard = {
                            if (ProfileImportHelper.importFromClipboard(context)) {
                                refreshPresets()
                            }
                        },
                        onActivate = { uri ->
                            VlessProfileStore.setActiveUri(context, uri)
                            refreshPresets()
                        },
                        onDelete = { uri ->
                            VlessProfileStore.removePreset(context, uri)
                            refreshPresets()
                        },
                    )

                    AppScreen.SETTINGS -> SettingsScreen(
                        onBack = { goBack() },
                        onOpenAbout = { navigateTo(AppScreen.ABOUT) }
                    )

                    AppScreen.ABOUT -> AboutScreen(
                        onBack = { goBack() }
                    )

                    // TODO/TEST: AppScreen.TEST -> TestScreen(onBack = { currentScreen = AppScreen.VPN })
                }
            }
        }
    }
}
