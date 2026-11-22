package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Single floating particle data
 */
data class FloatingReactionItem(
    val id: Long,
    val emoji: String,
    val startOffsetX: Float, // -120 to +120 dp range
    val driftX: Float,       // horizontal wobble
    val rotation: Float,     // random rotation degree
    val durationMs: Int = 1800
)

/**
 * Overlay canvas that renders animated floating reactions ascending from the bottom
 */
@Composable
fun FloatingReactionsOverlay(
    reactions: List<FloatingReactionItem>,
    onReactionFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        reactions.forEach { item ->
            FloatingParticleView(
                item = item,
                onFinished = { onReactionFinished(item.id) }
            )
        }
    }
}

@Composable
private fun FloatingParticleView(
    item: FloatingReactionItem,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(item.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = item.durationMs,
                easing = FastOutSlowInEasing
            )
        )
        onFinished()
    }

    val currentProgress = progress.value

    // Upward displacement from bottom (e.g. 0 to 520dp)
    val offsetY = -(currentProgress * 520).dp

    // Sine-like horizontal wave movement
    val horizontalWobble = (item.driftX * kotlin.math.sin(currentProgress * Math.PI.toFloat() * 2f)).dp
    val startX = item.startOffsetX.dp

    // Scale effect: starts at 0.5, expands to 1.35 around midway, then stays prominent
    val scale = when {
        currentProgress < 0.2f -> 0.4f + (currentProgress / 0.2f) * 0.9f
        currentProgress < 0.65f -> 1.3f
        else -> 1.3f - ((currentProgress - 0.65f) / 0.35f) * 0.4f
    }

    // Alpha fade-out near the top
    val alpha = when {
        currentProgress < 0.1f -> currentProgress / 0.1f
        currentProgress > 0.6f -> 1f - ((currentProgress - 0.6f) / 0.4f)
        else -> 1f
    }

    // Rotation sway
    val currentRotation = item.rotation * kotlin.math.sin(currentProgress * Math.PI.toFloat())

    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = item.emoji,
            fontSize = 36.sp,
            modifier = Modifier
                .offset(x = startX + horizontalWobble, y = offsetY)
                .scale(scale)
                .alpha(alpha.coerceIn(0f, 1f))
                .rotate(currentRotation)
        )
    }
}
