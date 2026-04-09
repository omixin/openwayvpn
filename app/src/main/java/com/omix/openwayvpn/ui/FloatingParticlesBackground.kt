package com.omix.openwayvpn.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Фон с парящими частицами.
 * Много мелких точек, медленно парящих вверх, как пылинки в луче света.
 * При connected=true частицы ярче и быстрее.
 * Узлы VPN рисуются поверх при connected для демонстрации соединения.
 */
@Composable
fun FloatingParticlesBackground(
    connected: Boolean,
    modifier: Modifier = Modifier,
    particleColor: Color = Color.White,
    particleCount: Int = 80,
) {
    // Создаём частицы
    val particles = remember { List(particleCount) { FloatingParticle() } }

    // Инициализация
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!initialized) {
            particles.forEach { it.initialize() }
            initialized = true
        }
    }
    LaunchedEffect(connected) {
        if (connected) {
            particles.forEach { particle ->
                if (Random.nextFloat() > 0.6f) {
                    particle.accelerate()
                }
            }
        } else {
            particles.forEach { it.decelerate() }
        }
    }

    // Анимация частиц
    LaunchedEffect(Unit) {
        while (true) {
            val frameTime = withFrameMillis { it }
            particles.forEach { it.update(frameTime, connected) }
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        // Рисуем частицы и их следы
        particles.forEach { particle ->
            // Рисуем след если есть
            if (particle.trailPositions.isNotEmpty()) {
                particle.trailPositions.forEachIndexed { index, (tx, ty) ->
                    val trailAlpha = (index + 1).toFloat() / particle.trailPositions.size * 0.4f
                    val trailRadius = particle.radius.value * (0.4f + 0.6f * (index + 1) / particle.trailPositions.size)
                    drawCircle(
                        color = particleColor.copy(alpha = trailAlpha),
                        radius = trailRadius,
                        center = Offset(tx, ty),
                    )
                }
            }
            
            // Рисуем саму частицу
            drawCircle(
                color = particleColor.copy(alpha = particle.alpha.value),
                radius = particle.radius.value,
                center = Offset(particle.x.value, particle.y.value),
            )
        }
    }
}

/**
 * Одна парящая частица
 */
class FloatingParticle {
    val x = Animatable(0f)
    val y = Animatable(0f)
    val alpha = Animatable(0f)
    val radius = Animatable(0f)

    // След за частицей (предыдущие позиции)
    val trailPositions = mutableListOf<Pair<Float, Float>>()
    private var lastBoosted = false

    private var baseSpeed = 0f
    private var wobbleAngle = Random.nextFloat() * 360f
    private var wobbleSpeed = Random.nextFloat() * 0.03f + 0.02f
    private var boosted = false
    private var boostTimer = 0L

    suspend fun initialize() {
        x.snapTo(Random.nextFloat() * 1500f)
        y.snapTo(Random.nextFloat() * 2500f)
        alpha.snapTo(Random.nextFloat() * 0.5f + 0.15f)
        radius.snapTo(Random.nextFloat() * 3.5f + 1.5f)
        baseSpeed = Random.nextFloat() * 1.2f + 0.6f
    }

    suspend fun update(frameTime: Long, connected: Boolean) {
        val currentSpeed = if (boosted) baseSpeed * 3f else baseSpeed
        val currentAlphaBoost = if (connected) 0.15f else 0f

        // Сохраняем позицию в след если ускорена
        if (boosted) {
            trailPositions.add(x.value to y.value)
            if (trailPositions.size > 8) {
                trailPositions.removeAt(0)
            }
        } else if (trailPositions.isNotEmpty()) {
            trailPositions.clear()
        }

        // Двигаемся вверх с покачиванием
        wobbleAngle += wobbleSpeed
        val wobble = cos(Math.toRadians(wobbleAngle.toDouble())).toFloat() * 0.6f
        
        val newY = y.value - currentSpeed
        val newX = x.value + wobble

        // Если ушла за верх экрана - возвращаем вниз
        if (newY < -20f) {
            y.snapTo(2500f + Random.nextFloat() * 200f)
            x.snapTo(Random.nextFloat() * 1500f)
            alpha.snapTo(Random.nextFloat() * 0.5f + 0.15f)
        } else {
            y.snapTo(newY)
            x.snapTo(newX.coerceIn(-20f, 1520f))
        }

        // Пульсация прозрачности
        val pulse = sin(Math.toRadians(frameTime * 0.002 + Random.nextFloat() * 360)).toFloat() * 0.12f
        val targetAlpha = (alpha.value + pulse).coerceIn(0.08f, 0.7f + currentAlphaBoost)
        alpha.snapTo(targetAlpha)

        // Таймер буста
        if (boosted) {
            boostTimer -= 16
            if (boostTimer <= 0) {
                boosted = false
            }
        }
    }

    fun accelerate() {
        boosted = true
        boostTimer = 3000 + Random.nextLong(2000)
    }

    fun decelerate() {
        boosted = false
        boostTimer = 0
    }
}
