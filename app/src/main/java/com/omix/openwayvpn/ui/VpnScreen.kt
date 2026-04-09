package com.omix.openwayvpn.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.omix.openwayvpn.R
import com.omix.openwayvpn.vpn.MyVpnService
import com.omix.openwayvpn.vpn.VlessUriParser
import kotlinx.coroutines.delay

@Composable
fun VpnScreen(
    status: VpnUiStatus,
    activeUri: String?,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onConnectRequested: () -> Unit,
    onDisconnectRequested: () -> Unit,
) {
    val context = LocalContext.current
    val isRunning = status == VpnUiStatus.ONLINE
    val connectionQuality by MyVpnService.connectionQuality.collectAsState()
    val connectionStartTime by MyVpnService.connectionStartTime.collectAsState()
    val offlineColor = Color(0xFF2A2A35)
    val connectingColor = Color(0xFF00D4FF)
    val onlineColor = Color(0xFF00E676)

    // Timer for connection duration (updates every second when online)
    var connectionDurationText by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(connectionStartTime, isRunning) {
        if (isRunning && connectionStartTime > 0) {
            while (true) {
                connectionDurationText = MyVpnService.formatConnectionDuration(connectionStartTime)
                delay(1000)
            }
        } else {
            connectionDurationText = "00:00:00"
        }
    }

    var pendingVpnStartAfterNotificationPermission by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(DEBUG_TAG, "vpnPermission resultCode=${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            sendVpnCommand(context, MyVpnService.ACTION_CONNECT)
        } else {
            MyVpnService.setPermissionDeniedState(context)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(DEBUG_TAG, "notificationPermission granted=$granted")
        if (pendingVpnStartAfterNotificationPermission) {
            pendingVpnStartAfterNotificationPermission = false
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                sendVpnCommand(context, MyVpnService.ACTION_CONNECT)
            }
        }
    }

    val buttonColor by animateColorAsState(
        targetValue = when (status) {
            VpnUiStatus.ONLINE -> onlineColor
            VpnUiStatus.CONNECTING -> connectingColor
            VpnUiStatus.OFFLINE -> offlineColor
        },
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "buttonColor"
    )
    val glowColor by animateColorAsState(
        targetValue = when (status) {
            VpnUiStatus.ONLINE -> onlineColor
            VpnUiStatus.CONNECTING -> connectingColor
            VpnUiStatus.OFFLINE -> offlineColor
        },
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "glowColor"
    )

    val statusWordUi = when (status) {
        VpnUiStatus.ONLINE -> StatusWordUi(
            text = context.getString(R.string.vpn_online).removePrefix("VPN "),
            color = onlineColor
        )
        VpnUiStatus.CONNECTING -> StatusWordUi(
            text = context.getString(R.string.vpn_connecting).removePrefix("VPN "),
            color = connectingColor
        )
        VpnUiStatus.OFFLINE -> StatusWordUi(
            text = context.getString(R.string.vpn_offline).removePrefix("VPN "),
            color = Color(0xFFB6BCD1)
        )
    }

    val activeLabel = activeUri?.let { uri ->
        val parsed = VlessUriParser.parse(uri).getOrNull()
        parsed?.name ?: parsed?.host ?: context.getString(R.string.no_profile_selected)
    } ?: context.getString(R.string.no_profile_selected)

    val neonPulseTransition = rememberInfiniteTransition(label = "neonPulseTransition")
    val connectingPulseScale by neonPulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "connectingPulseScale"
    )
    val connectingPulseAlpha by neonPulseTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "connectingPulseAlpha"
    )
    val staticGlowScale by animateFloatAsState(
        targetValue = when (status) {
            VpnUiStatus.ONLINE -> 1.02f
            VpnUiStatus.CONNECTING -> 1f
            VpnUiStatus.OFFLINE -> 0.88f
        },
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "staticGlowScale"
    )
    val staticGlowAlpha by animateFloatAsState(
        targetValue = when (status) {
            VpnUiStatus.ONLINE -> 0.52f
            VpnUiStatus.CONNECTING -> 0.5f
            VpnUiStatus.OFFLINE -> 0f
        },
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "staticGlowAlpha"
    )
    val glowScale = if (status == VpnUiStatus.CONNECTING) connectingPulseScale else staticGlowScale
    val glowAlpha = if (status == VpnUiStatus.CONNECTING) connectingPulseAlpha else staticGlowAlpha

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = 0.42f,
            stiffness = 640f
        ),
        label = "buttonScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        // Settings button top-right
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = context.getString(R.string.settings),
                tint = Color(0xFFB6BCD1)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "VPN ",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFFF6F7FF)
                )
                AnimatedContent(
                    targetState = statusWordUi,
                    transitionSpec = {
                        (slideInVertically(
                            animationSpec = tween(500, easing = LinearOutSlowInEasing),
                            initialOffsetY = { it / 3 }
                        ) + fadeIn(tween(460, easing = LinearOutSlowInEasing))).togetherWith(
                            slideOutVertically(
                                animationSpec = tween(500, easing = LinearOutSlowInEasing),
                                targetOffsetY = { -it / 4 }
                            ) + fadeOut(tween(420, easing = LinearOutSlowInEasing))
                        ).using(SizeTransform(clip = false))
                    },
                    label = "statusWord"
                ) { wordUi ->
                    Text(
                        text = wordUi.text,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = wordUi.color
                    )
                }
            }

            // Индикатор качества соединения
            if (status == VpnUiStatus.ONLINE) {
                val qualityData = when (connectionQuality) {
                    com.omix.openwayvpn.vpn.MyVpnService.ConnectionQuality.Good ->
                        "Excellent" to Color(0xFF00E676)
                    com.omix.openwayvpn.vpn.MyVpnService.ConnectionQuality.Fair ->
                        "Good" to Color(0xFFB4FF00)
                    com.omix.openwayvpn.vpn.MyVpnService.ConnectionQuality.Poor ->
                        "Poor" to Color(0xFFFFB74D)
                    else -> "Checking..." to Color(0xFFB6BCD1)
                }
                
                AnimatedContent(
                    targetState = qualityData,
                    transitionSpec = {
                        (slideInVertically(
                            animationSpec = tween(400, easing = LinearOutSlowInEasing),
                            initialOffsetY = { it / 4 }
                        ) + fadeIn(tween(360, easing = LinearOutSlowInEasing))).togetherWith(
                            slideOutVertically(
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                targetOffsetY = { -it / 4 }
                            ) + fadeOut(tween(260, easing = FastOutSlowInEasing))
                        ).using(SizeTransform(clip = false))
                    },
                    label = "connectionQuality"
                ) { (text, color) ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = color,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Время подключения
            if (isRunning) {
                Text(
                    text = connectionDurationText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    ),
                    color = Color(0xFFB6BCD1),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(glowColor.copy(alpha = 0.92f), CircleShape)
                    )
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        tint = Color(0xFFE8ECFB),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = activeLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFE8ECFB),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val baseRadius = size.minDimension * 0.29f
                            val center = center
                            drawCircle(
                                color = glowColor.copy(alpha = glowAlpha * 0.95f),
                                radius = baseRadius * glowScale,
                                center = center
                            )
                            drawCircle(
                                color = glowColor.copy(alpha = glowAlpha * 0.32f),
                                radius = baseRadius * glowScale * 1.55f,
                                center = center
                            )
                        }
                )
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(buttonScale)
                        .clip(CircleShape)
                        .background(buttonColor)
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            // Haptic feedback
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                            if (vibrator?.hasVibrator() == true) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(10)
                                }
                            }

                            if (isRunning || status == VpnUiStatus.CONNECTING) {
                                onDisconnectRequested()
                                sendVpnCommand(context, MyVpnService.ACTION_DISCONNECT)
                            } else {
                                onConnectRequested()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    pendingVpnStartAfterNotificationPermission = true
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val prepareIntent = VpnService.prepare(context)
                                    if (prepareIntent != null) {
                                        vpnPermissionLauncher.launch(prepareIntent)
                                    } else {
                                        sendVpnCommand(context, MyVpnService.ACTION_CONNECT)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = "Toggle VPN",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = context.getString(R.string.tap_to_activate),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9FA6BB)
            )
        }

        // TODO/TEST: Test button - Interactive background animation feature
        /*
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onOpenTest, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp), color = Color(0xFF151A27),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.45f)),
            ) { Text(text = "Test", ...) }
            Surface(onClick = onOpenProfiles, ...) { Text(text = "Profiles", ...) }
        }
        */

        Surface(
            onClick = onOpenProfiles,
            modifier = Modifier.align(Alignment.BottomCenter),
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFF151A27),
            border = BorderStroke(1.dp, Color(0xFF00D4FF).copy(alpha = 0.55f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Text(
                text = context.getString(R.string.profiles),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFEAF0FF),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp)
            )
        }
    }
}
