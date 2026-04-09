package com.omix.openwayvpn.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omix.openwayvpn.R
import com.omix.openwayvpn.vpn.VlessProfileStore
import com.omix.openwayvpn.vpn.VlessUriParser

@Composable
fun ProfilesScreen(
    presets: List<String>,
    activeUri: String?,
    onBack: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onActivate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    var uriToDelete by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.profiles),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Color(0xFFF5F7FF)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onImportFromClipboard) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = context.getString(R.string.import_clipboard),
                        tint = Color(0xFF00D4FF)
                    )
                }
                TextButton(onClick = onBack) {
                    Text(
                        text = context.getString(R.string.back_to_vpn),
                        color = Color(0xFFE4E8F8)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = context.getString(R.string.tap_to_activate),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF9DA4B9)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (presets.isEmpty()) {
            Text(
                text = context.getString(R.string.no_presets),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFC7CDDF)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                presets.forEachIndexed { index, uri ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = 400,
                                delayMillis = index * 80,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = 400,
                                delayMillis = index * 80,
                                easing = FastOutSlowInEasing
                            ),
                            initialOffsetY = { it / 4 }
                        ),
                        label = "profile_card_$index"
                    ) {
                    val parsed = VlessUriParser.parse(uri).getOrNull()
                    val label = parsed?.name ?: parsed?.host ?: "Profile"
                    val subtitle = "${parsed?.host ?: "unknown"}:${parsed?.port ?: 0}"
                    val isActive = activeUri == uri

                    val cardColor by animateColorAsState(
                        targetValue = if (isActive) {
                            Color(0xFF00E676).copy(alpha = 0.14f)
                        } else {
                            Color(0xFF222230)
                        },
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        label = "cardColor_$uri"
                    )
                    val cardBorderColor by animateColorAsState(
                        targetValue = if (isActive) {
                            Color(0xFF00E676).copy(alpha = 0.9f)
                        } else {
                            Color.White.copy(alpha = 0.08f)
                        },
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        label = "cardBorderColor_$uri"
                    )

                    // Haptic feedback для долгого нажатия
                    val triggerHaptic: () -> Unit = {
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        if (vibrator?.hasVibrator() == true) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                            } else {
                                vibrator.vibrate(30)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onActivate(uri) },
                                onLongClick = {
                                    triggerHaptic()
                                    uriToDelete = uri
                                }
                            ),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            width = if (isActive) 1.2.dp else 1.dp,
                            color = cardBorderColor
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = cardColor
                        )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFFF2F5FF)
                                )
                                if (isActive) {
                                    Text(
                                        text = context.getString(R.string.current_profile_mark),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF00E676)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFA8AEC2)
                            )
                        }
                    }
                    } // AnimatedVisibility
                }
            }
        }
    }

    // Диалог подтверждения удаления
    uriToDelete?.let { uri ->
        AlertDialog(
            onDismissRequest = { uriToDelete = null },
            title = {
                Text(
                    text = context.getString(R.string.delete_profile),
                    color = Color(0xFFF5F7FF)
                )
            },
            text = {
                val parsed = VlessUriParser.parse(uri).getOrNull()
                val name = parsed?.name ?: parsed?.host ?: context.getString(R.string.this_profile)
                Text(
                    text = context.getString(R.string.delete_profile_confirm, name),
                    color = Color(0xFFB6BCD1)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(uri)
                        uriToDelete = null
                    }
                ) {
                    Text(
                        text = context.getString(R.string.delete),
                        color = Color(0xFFFF5252)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { uriToDelete = null }) {
                    Text(
                        text = context.getString(R.string.cancel),
                        color = Color(0xFFB6BCD1)
                    )
                }
            }
        )
    }
}
