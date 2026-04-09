package com.omix.openwayvpn.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omix.openwayvpn.LanguageManager
import com.omix.openwayvpn.R

/**
 * Экран настроек. Пока только переключение языка.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentLang = remember { mutableStateOf(LanguageManager.getLanguage(context)) }

    val centerColor = Color(0xFF1A1A24)
    val edgeColor = Color(0xFF0D0D12)
    val ambientColor = Color(0xFF2A2A35).copy(alpha = 0.35f)

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.settings),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFFF5F7FF)
                )
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = context.getString(R.string.back_to_profiles),
                        tint = Color(0xFF00D4FF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Language section
            Text(
                text = context.getString(R.string.language),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFB6BCD1),
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Language options
            val languages = listOf(
                LanguageOption(
                    code = LanguageManager.LANG_RU,
                    flagRes = R.drawable.ic_flag_ru,
                    label = context.getString(R.string.language_ru)
                ),
                LanguageOption(
                    code = LanguageManager.LANG_EN,
                    flagRes = R.drawable.ic_flag_us,
                    label = context.getString(R.string.language_en)
                )
            )

            languages.forEach { lang ->
                val isSelected = currentLang.value == lang.code
                val cardColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF00D4FF).copy(alpha = 0.14f) else Color(0xFF222230),
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    label = "cardColor_${lang.code}"
                )
                val cardBorderColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF00D4FF).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f),
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    label = "cardBorderColor_${lang.code}"
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Haptic feedback
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                            if (vibrator?.hasVibrator() == true) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                                } else {
                                    vibrator.vibrate(10)
                                }
                            }

                            LanguageManager.setLanguage(context, lang.code)
                            currentLang.value = lang.code
                            // Soft restart - just recreate the activity without clearing stack
                            LanguageManager.restartActivity(context)
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = cardColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Flag icon
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = lang.flagRes),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified
                            )
                        }

                        // Language label
                        Text(
                            text = lang.label,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFE8ECFB),
                            modifier = Modifier.weight(1f)
                        )

                        // Checkmark
                        if (isSelected) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF00D4FF),
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About link
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAbout() },
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF222230),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = null,
                        tint = Color(0xFFB6BCD1),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFE8ECFB),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private data class LanguageOption(
    val code: String,
    val flagRes: Int,
    val label: String
)
