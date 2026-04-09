package com.omix.openwayvpn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Интерактивный фон с анимацией VPN-узлов.
 * Узлы плавают отдельно в offline режиме,
 * при connected=true они притягиваются и соединяются в цепь.
 */
@Composable
fun InteractiveVpnBackground(
    connected: Boolean,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    nodeColor: Color = Color.White,
    nodeBorderColor: Color = Color.Black,
    lineColor: Color = Color.White.copy(alpha = 0.25f),
    connectionLineColor: Color = Color(0xFF00E676).copy(alpha = 0.8f),
    antennaCount: Int = 4,
    antennaLength: Dp = 14.dp,
    nodeRadius: Dp = 10.dp,
    nodeCount: Int = 5,
) {
    val density = LocalDensity.current
    val antennaLengthPx = with(density) { antennaLength.toPx() }
    val nodeRadiusPx = with(density) { nodeRadius.toPx() }

    // Создаём узлы
    val nodes = remember { List(nodeCount) { VpnNode() } }

    // Инициализация начальных позиций
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!initialized) {
            nodes.forEachIndexed { index, node ->
                node.positionX.updateBounds(0f, 1000f)
                node.positionY.updateBounds(0f, 1400f)
                node.positionX.snapTo(Random.nextFloat() * 600 + 100f)
                node.positionY.snapTo(Random.nextFloat() * 800 + 100f)
                node.baseAngle = Random.nextFloat() * 360f
                node.speed = Random.nextFloat() * 1.2f + 0.8f
            }
            initialized = true
        }
    }

    // Анимация при изменении состояния connected
    LaunchedEffect(connected) {
        if (connected) {
            // Притягиваем все узлы в линию
            val centerX = 500f
            val centerY = 600f
            val spacing = 100f
            
            nodes.forEachIndexed { index, node ->
                val offset = (index - (nodeCount - 1) / 2) * spacing
                node.positionX.animateTo(
                    targetValue = centerX + offset,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessVeryLow
                    )
                )
                node.positionY.animateTo(
                    targetValue = centerY,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessVeryLow
                    )
                )
            }
        }
    }

    // Анимация движения
    LaunchedEffect(Unit) {
        while (true) {
            val frameTime = withFrameMillis { it }
            if (!connected) {
                nodes.forEach { it.updateFloat(frameTime) }
            }
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        // Фон
        if (backgroundColor != Color.Transparent) {
            drawRect(color = backgroundColor)
        }

        // Рисуем соединения если connected
        if (connected && nodes.size > 1) {
            for (i in 0 until nodes.size - 1) {
                drawConnectionLine(
                    start = Offset(nodes[i].positionX.value, nodes[i].positionY.value),
                    end = Offset(nodes[i + 1].positionX.value, nodes[i + 1].positionY.value),
                    color = connectionLineColor,
                )
            }
        }

        // Рисуем узлы
        nodes.forEach { node ->
            drawVpnNode(
                position = Offset(node.positionX.value, node.positionY.value),
                color = nodeColor,
                borderColor = nodeBorderColor,
                lineColor = lineColor,
                antennaCount = antennaCount,
                antennaLength = antennaLengthPx,
                nodeRadius = nodeRadiusPx,
                connected = connected,
            )
        }
    }
}

/**
 * Класс, представляющий один VPN-узел
 */
class VpnNode {
    val positionX = Animatable(0f)
    val positionY = Animatable(0f)

    // Параметры плавания
    var baseAngle = Random.nextFloat() * 360f
    var speed = Random.nextFloat() * 1.2f + 0.8f
    private var directionChangeTimer = 0L

    fun randomizePosition() {
        positionX.updateBounds(0f, 1000f)
        positionY.updateBounds(0f, 1400f)
        baseAngle = Random.nextFloat() * 360f
        speed = Random.nextFloat() * 1.2f + 0.8f
    }

    suspend fun updateFloat(frameTime: Long) {
        // Меняем направление каждые 1.5-3 секунды
        if (frameTime - directionChangeTimer > 1500 + Random.nextLong(1500)) {
            baseAngle += Random.nextFloat() * 120f - 60f
            speed = Random.nextFloat() * 1.2f + 0.8f
            directionChangeTimer = frameTime
        }

        val radians = Math.toRadians(baseAngle.toDouble()).toFloat()
        val dx = cos(radians) * speed
        val dy = sin(radians) * speed
        
        val newX = positionX.value + dx
        val newY = positionY.value + dy

        // Отскок от стен (инвертируем угол)
        var newAngle = baseAngle
        var finalX = newX
        var finalY = newY
        
        if (newX <= 40f || newX >= 960f) {
            newAngle = 180f - baseAngle
            finalX = newX.coerceIn(40f, 960f)
        }
        if (newY <= 40f || newY >= 1360f) {
            newAngle = -baseAngle
            finalY = newY.coerceIn(40f, 1360f)
        }
        
        baseAngle = newAngle
        positionX.updateBounds(40f, 960f)
        positionY.updateBounds(40f, 1360f)
        
        positionX.snapTo(finalX)
        positionY.snapTo(finalY)
    }
}

/**
 * Рисует VPN-узел: круг с торчащими линиями-антеннами
 */
private fun DrawScope.drawVpnNode(
    position: Offset,
    color: Color,
    borderColor: Color,
    lineColor: Color,
    antennaCount: Int,
    antennaLength: Float,
    nodeRadius: Float,
    connected: Boolean,
) {
    // Рисуем антенны
    for (i in 0 until antennaCount) {
        val angle = (360f / antennaCount) * i
        val radians = Math.toRadians(angle.toDouble())
        val startX = position.x + cos(radians).toFloat() * nodeRadius
        val startY = position.y + sin(radians).toFloat() * nodeRadius
        val endX = position.x + cos(radians).toFloat() * (nodeRadius + antennaLength)
        val endY = position.y + sin(radians).toFloat() * (nodeRadius + antennaLength)

        drawLine(
            color = lineColor,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 2f,
        )

        // Маленькая точка на конце антенны
        drawCircle(
            color = lineColor.copy(alpha = 0.5f),
            radius = 2.5f,
            center = Offset(endX, endY),
        )
    }

    // Внешнее свечение
    if (connected) {
        drawCircle(
            color = Color(0xFF00E676).copy(alpha = 0.15f),
            radius = nodeRadius * 2.8f,
            center = position,
        )
    }

    // Основной круг узла с обводкой
    drawCircle(
        color = borderColor,
        radius = nodeRadius + 2f,
        center = position,
    )
    drawCircle(
        color = color,
        radius = nodeRadius,
        center = position,
    )

    // Внутренний круг для эффекта
    drawCircle(
        color = color.copy(alpha = 0.2f),
        radius = nodeRadius * 1.6f,
        center = position,
    )
}

/**
 * Рисует линию соединения между двумя узлами
 */
private fun DrawScope.drawConnectionLine(
    start: Offset,
    end: Offset,
    color: Color,
) {
    // Основная линия
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = 3f,
    )

    // Параллельные линии для эффекта "цепи"
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length > 0) {
        val offsetX = -dy / length * 6f
        val offsetY = dx / length * 6f

        drawLine(
            color = color.copy(alpha = 0.3f),
            start = Offset(start.x + offsetX, start.y + offsetY),
            end = Offset(end.x + offsetX, end.y + offsetY),
            strokeWidth = 1.5f,
        )
        drawLine(
            color = color.copy(alpha = 0.3f),
            start = Offset(start.x - offsetX, start.y - offsetY),
            end = Offset(end.x - offsetX, end.y - offsetY),
            strokeWidth = 1.5f,
        )
    }

    // Точки на концах соединения
    drawCircle(
        color = color,
        radius = 5f,
        center = start,
    )
    drawCircle(
        color = color,
        radius = 5f,
        center = end,
    )
}
