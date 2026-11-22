package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GiftItem
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Model representing an active particle explosion or floating badge in the gift overlay.
 */
data class GiftParticle(
    val id: Long,
    val char: String,
    val initialAngleRad: Float,
    val maxRadius: Float,
    val sizeSp: Float,
    val durationMs: Int
)

/**
 * High-impact, engaging Floating Gift Animation Overlay designed for Live Broadcast video player.
 * Triggers vivid visual effects when a viewer sends a virtual gift:
 * - Floating ascending mini-gift balloons / trails with natural drift
 * - Radiant pulsing celestial aura / spotlight rays behind center stage
 * - Dynamic 3D gift avatar pop-in with bounce, sparkling particle bursts, and diamond shockwave rings
 * - Luxury VIP glassmorphic banner showing gift title, sound effect, and coin value
 */
@Composable
fun LiveGiftAnimationOverlay(
    activeGift: GiftItem?,
    senderName: String? = "أحد المشاهدين",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("floating_gift_overlay"),
        contentAlignment = Alignment.Center
    ) {
        if (activeGift != null) {
            // 1. Surrounding Floating Gift Confetti & Mini Emojis (Ascending trails)
            FloatingGiftTrails(gift = activeGift)

            // 2. Center Stage Hero Banner & Particle Shockwave
            CenterGiftHeroStage(
                gift = activeGift,
                senderName = senderName
            )
        }
    }
}

/**
 * Ascending multi-path floating mini gifts and sparkles that drift upwards over the video.
 */
@Composable
private fun FloatingGiftTrails(gift: GiftItem) {
    val items = remember(gift.id) {
        val count = when (gift.id) {
            "gift_rocket", "gift_car" -> 14
            "gift_crown", "gift_diamond" -> 12
            else -> 8
        }
        List(count) { index ->
            GiftTrailItem(
                id = System.currentTimeMillis() + index * 100L,
                emoji = if (index % 3 == 0) "✨" else if (index % 4 == 0) "🌟" else gift.emoji,
                startX = (Random.nextFloat() * 320f) - 160f,
                driftFactor = (Random.nextFloat() * 70f) - 35f,
                durationMs = 2000 + Random.nextInt(700),
                delayMs = index * 110L,
                maxHeightDp = 480f + Random.nextInt(180),
                baseScale = if (index % 3 == 0) 0.8f else 1.25f
            )
        }
    }

    items.forEach { trailItem ->
        AscendingGiftParticle(trailItem)
    }
}

private data class GiftTrailItem(
    val id: Long,
    val emoji: String,
    val startX: Float,
    val driftFactor: Float,
    val durationMs: Int,
    val delayMs: Long,
    val maxHeightDp: Float,
    val baseScale: Float
)

@Composable
private fun AscendingGiftParticle(item: GiftTrailItem) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(item.id) {
        delay(item.delayMs)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = item.durationMs,
                easing = FastOutSlowInEasing
            )
        )
    }

    val currentProgress = progress.value
    if (currentProgress > 0f && currentProgress < 1f) {
        val offsetY = -(currentProgress * item.maxHeightDp).dp
        val horizontalWobble = (item.driftFactor * sin(currentProgress * Math.PI.toFloat() * 2.5f)).dp
        val alpha = when {
            currentProgress < 0.15f -> currentProgress / 0.15f
            currentProgress > 0.75f -> 1f - ((currentProgress - 0.75f) / 0.25f)
            else -> 1f
        }
        val scale = when {
            currentProgress < 0.2f -> 0.3f + (currentProgress / 0.2f) * (item.baseScale - 0.3f)
            else -> item.baseScale
        }
        val rotation = sin(currentProgress * Math.PI.toFloat() * 3f) * 20f

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = item.emoji,
                fontSize = 28.sp,
                modifier = Modifier
                    .offset(x = item.startX.dp + horizontalWobble, y = offsetY)
                    .scale(scale)
                    .rotate(rotation)
                    .alpha(alpha.coerceIn(0f, 1f))
            )
        }
    }
}

/**
 * Center-screen dynamic stage: Glow aura, expanding shockwave rings, floating sparkles explosion,
 * and high-prestige glassmorphic card.
 */
@Composable
private fun CenterGiftHeroStage(
    gift: GiftItem,
    senderName: String?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gift_aura_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val auraRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aura_rotation"
    )

    // Particle explosion state
    val particles = remember(gift.id) {
        val emojis = listOf(gift.emoji, "✨", "💎", "⭐", "🔥", "🎉")
        List(16) { index ->
            val angle = (index * (360f / 16f)) * (Math.PI.toFloat() / 180f)
            GiftParticle(
                id = index.toLong(),
                char = emojis[index % emojis.size],
                initialAngleRad = angle,
                maxRadius = 130f + (index % 4) * 25f,
                sizeSp = 18f + (index % 3) * 6f,
                durationMs = 1200 + (index % 3) * 200
            )
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Radial Glowing Ambient Backlight behind Center
        Box(
            modifier = Modifier
                .size(260.dp)
                .scale(pulseScale)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            when (gift.id) {
                                "gift_crown" -> AyerGold.copy(alpha = 0.55f)
                                "gift_diamond" -> AyerCyan.copy(alpha = 0.55f)
                                "gift_rose" -> Color(0xFFFF1744).copy(alpha = 0.55f)
                                "gift_heart" -> AyerSecondary.copy(alpha = 0.55f)
                                "gift_car" -> Color(0xFFFF9100).copy(alpha = 0.55f)
                                "gift_rocket" -> AyerPrimary.copy(alpha = 0.55f)
                                else -> AyerGold.copy(alpha = 0.5f)
                            },
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 2. Shockwave expanding canvas ring
        GiftShockwaveCanvas(gift = gift)

        // 3. Radial Particle Burst around center
        particles.forEach { particle ->
            ExplodingParticleView(particle = particle)
        }

        // 4. Center VIP Gift Card
        Surface(
            color = Color(0xDD0D1117),
            shape = RoundedCornerShape(26.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                brush = Brush.linearGradient(
                    listOf(
                        AyerGold,
                        when (gift.id) {
                            "gift_diamond" -> AyerCyan
                            "gift_rose" -> Color(0xFFFF4081)
                            else -> AyerPrimary
                        },
                        AyerGold
                    )
                )
            ),
            shadowElevation = 18.dp,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .testTag("active_gift_center_card")
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top VIP Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AyerGold.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AyerGold.copy(alpha = 0.6f)),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🎁 هدية بث مباشر", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AyerGold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "• ${gift.pointsCost} 🪙", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }

                // Giant Animated Emoji
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(90.dp)
                ) {
                    Text(
                        text = gift.emoji,
                        fontSize = 68.sp,
                        modifier = Modifier
                            .scale(pulseScale)
                            .rotate(sin(pulseScale * 8f) * 6f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Gift Name with shimmering gradient effect
                Text(
                    text = gift.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                // Sender Recognition line
                Text(
                    text = "مُهداة من: ${senderName ?: "مشاهد مميز"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AyerCyan,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // Sound Effect / Description subtitle
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "🔊 ${gift.soundEffectDesc}",
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Animated Shockwave expansion ring that spreads outward on trigger
 */
@Composable
private fun GiftShockwaveCanvas(gift: GiftItem) {
    val ringProgress = remember { Animatable(0f) }

    LaunchedEffect(gift.id) {
        ringProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
        )
    }

    val progress = ringProgress.value
    val ringRadius = progress * 220f
    val ringAlpha = (1f - progress).coerceIn(0f, 1f)

    val ringColor = when (gift.id) {
        "gift_crown" -> AyerGold
        "gift_diamond" -> AyerCyan
        "gift_rose" -> Color(0xFFFF1744)
        "gift_heart" -> AyerSecondary
        else -> AyerPrimary
    }

    Canvas(modifier = Modifier.size(440.dp)) {
        if (progress > 0f && progress < 1f) {
            drawCircle(
                color = ringColor.copy(alpha = ringAlpha * 0.7f),
                radius = ringRadius * density,
                center = center,
                style = Stroke(width = (4f * (1f - progress)).coerceAtLeast(1f) * density)
            )
            // Inner secondary ring
            if (progress > 0.15f) {
                val innerP = (progress - 0.15f) / 0.85f
                drawCircle(
                    color = AyerGold.copy(alpha = (1f - innerP) * 0.5f),
                    radius = (innerP * 180f) * density,
                    center = center,
                    style = Stroke(width = 2f * density)
                )
            }
        }
    }
}

/**
 * Individual particle radiating outward from the center card with bounce and fade
 */
@Composable
private fun ExplodingParticleView(particle: GiftParticle) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(particle.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = particle.durationMs,
                easing = FastOutSlowInEasing
            )
        )
    }

    val currentP = progress.value
    if (currentP > 0f && currentP < 1f) {
        val distance = currentP * particle.maxRadius
        val x = (distance * cos(particle.initialAngleRad)).dp
        val y = (distance * sin(particle.initialAngleRad)).dp
        val alpha = if (currentP > 0.6f) 1f - ((currentP - 0.6f) / 0.4f) else 1f
        val scale = 0.5f + sin(currentP * Math.PI.toFloat()) * 0.7f

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = particle.char,
                fontSize = particle.sizeSp.sp,
                modifier = Modifier
                    .offset(x = x, y = y)
                    .scale(scale)
                    .alpha(alpha.coerceIn(0f, 1f))
            )
        }
    }
}
