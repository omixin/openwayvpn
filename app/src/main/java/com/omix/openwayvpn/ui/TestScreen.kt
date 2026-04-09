package com.omix.openwayvpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Экран для тестирования интерактивной анимации VPN-узлов.
 * Позволяет переключать режимы Offline/Online для демонстрации.
 */
@Composable
fun TestScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var connected by remember { mutableStateOf(false) }

    val centerColor by animateColorAsState(
        targetValue = if (connected) Color(0xFF0A1A0F) else Color(0xFF1A1A24),
        animationSpec = tween(560),
        label = "centerColor"
    )
    val edgeColor by animateColorAsState(
        targetValue = if (connected) Color(0xFF05100A) else Color(0xFF0D0D12),
        animationSpec = tween(560),
        label = "edgeColor"
    )
    val ambientColor by animateColorAsState(
        targetValue = if (connected) Color(0xFF00E676).copy(alpha = 0.18f) else Color(0xFF2A2A35).copy(alpha = 0.48f),
        animationSpec = tween(560),
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
        // Интерактивный фон с парящими частицами и узлами
        FloatingParticlesBackground(
            connected = connected,
            modifier = Modifier.fillMaxSize(),
        )

        // UI элементы
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Заголовок
            Text(
                text = "Test Animation",
                style = androidx.compose.material3.MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = Color(0xFFF5F7FF)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Переключай режим для демонстрации",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9DA4B9)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Индикатор статуса
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                color = if (connected) Color(0xFF00E676).copy(alpha = 0.14f) else Color(0xFF222230),
                border = BorderStroke(
                    1.dp,
                    if (connected) Color(0xFF00E676).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f)
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (connected) Color(0xFF00E676) else Color(0xFF2A2A35))
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (connected) "CONNECTED — Узлы соединены" else "OFFLINE — Узлы плавают",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        color = if (connected) Color(0xFF00E676) else Color(0xFFB6BCD1)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Кнопка переключения
            Button(
                onClick = { connected = !connected },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connected) Color(0xFF00E676) else Color(0xFF00D4FF)
                ),
            ) {
                Text(
                    text = if (connected) "Отключить (Offline)" else "Подключить (Online)",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (connected) Color(0xFF0A1A0F) else Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Кнопка назад
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF151A27)
                ),
                border = BorderStroke(1.dp, Color(0xFF2A2A35)),
            ) {
                Text(
                    text = "← Назад",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFFE8ECFB)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
