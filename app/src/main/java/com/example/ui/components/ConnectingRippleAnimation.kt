package com.example.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Concentric smooth ripple wave animation for the 'Connecting' state during matchmaking.
 *
 * Renders multiple continuous expanding ripple wavefronts that radiate outwards
 * with easing curves, progressive opacity falloff, and dual-tone gradient rings.
 */
@Composable
fun ConnectingRippleAnimation(
    modifier: Modifier = Modifier,
    ringCount: Int = 4,
    cycleDurationMillis: Int = 2400,
    baseRadiusFraction: Float = 0.35f,
    maxRadiusFraction: Float = 0.95f,
    primaryColor: Color = AyerCyan,
    accentColor: Color = AyerGold,
    maxAlpha: Float = 0.75f,
    showDashedAccents: Boolean = true,
    showParticles: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "connecting_ripple_transition")

    // Continuous progress cycle from 0.0f to 1.0f
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cycleDurationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_progress"
    )

    // Gentle global breathing glow
    val ambientGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_glow"
    )

    Canvas(
        modifier = modifier
            .testTag("connecting_ripple_canvas")
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxAvailableRadius = minOf(size.width, size.height) / 2f
        val startRadius = maxAvailableRadius * baseRadiusFraction
        val endRadius = maxAvailableRadius * maxRadiusFraction

        // 1. Soft radial ambient backing glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = ambientGlowAlpha),
                    accentColor.copy(alpha = ambientGlowAlpha * 0.4f),
                    Color.Transparent
                ),
                center = center,
                radius = startRadius * 1.5f
            ),
            radius = startRadius * 1.5f,
            center = center
        )

        // 2. Draw staggered concentric expanding ripples
        for (i in 0 until ringCount) {
            val ringOffset = i.toFloat() / ringCount
            val ringProgress = (progress + ringOffset) % 1f

            // Ease-out quadratic progression for natural fluid expansion
            val easedProgress = 1f - (1f - ringProgress) * (1f - ringProgress)
            val currentRadius = startRadius + (endRadius - startRadius) * easedProgress

            // Opacity curve: Quick fade-in then smooth gradual fade-out
            val alpha = when {
                ringProgress < 0.12f -> (ringProgress / 0.12f) * maxAlpha
                else -> (1f - ringProgress) / 0.88f * maxAlpha
            }.coerceIn(0f, 1f)

            if (alpha > 0.01f) {
                val strokeWidth = (2.5f * (1f - ringProgress * 0.65f)).dp.toPx()
                val isAccentRing = (i % 2 == 1)

                val ringColor = if (isAccentRing) {
                    accentColor.copy(alpha = alpha * 0.85f)
                } else {
                    primaryColor.copy(alpha = alpha)
                }

                // Main expanding ripple ring
                drawCircle(
                    color = ringColor,
                    radius = currentRadius,
                    center = center,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = if (showDashedAccents && isAccentRing) {
                            PathEffect.dashPathEffect(floatArrayOf(14f, 10f), ringProgress * 40f)
                        } else null
                    )
                )

                // Optional subtle outer halo on the primary wave
                if (!isAccentRing && ringProgress in 0.1f..0.7f) {
                    drawCircle(
                        color = primaryColor.copy(alpha = alpha * 0.25f),
                        radius = currentRadius + 3.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Orbital particle dots traveling along wave crest
                if (showParticles && ringProgress in 0.15f..0.85f) {
                    val particleAngles = listOf(30.0, 120.0, 210.0, 300.0)
                    particleAngles.forEachIndexed { index, baseAngleDeg ->
                        val spinOffset = ringProgress * 60.0 * (if (index % 2 == 0) 1 else -1)
                        val angleRad = Math.toRadians(baseAngleDeg + spinOffset)
                        val px = center.x + currentRadius * cos(angleRad).toFloat()
                        val py = center.y + currentRadius * sin(angleRad).toFloat()

                        drawCircle(
                            color = if (isAccentRing) accentColor.copy(alpha = alpha) else primaryColor.copy(alpha = alpha),
                            radius = (2.2f * (1f - ringProgress * 0.4f)).dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Micro-ripple beacon for live "Connecting" status indicators.
 * Features an inner pulsing core with 2 radiating concentric mini-waves.
 */
@Composable
fun ConnectingStatusBeacon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    coreColor: Color = Color(0xFF00E676),
    rippleColor: Color = AyerCyan
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_beacon")

    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beacon_p1"
    )

    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beacon_p2"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = this.size.width / 2f
            val coreR = maxR * 0.35f

            // Ripple wave 1
            val r1 = coreR + (maxR - coreR) * (1f - (1f - pulse1) * (1f - pulse1))
            val alpha1 = ((1f - pulse1) * 0.8f).coerceIn(0f, 1f)
            drawCircle(
                color = rippleColor.copy(alpha = alpha1),
                radius = r1,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Ripple wave 2
            val r2 = coreR + (maxR - coreR) * (1f - (1f - pulse2) * (1f - pulse2))
            val alpha2 = ((1f - pulse2) * 0.8f).coerceIn(0f, 1f)
            drawCircle(
                color = coreColor.copy(alpha = alpha2),
                radius = r2,
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Core solid pulsing dot
            drawCircle(
                color = coreColor,
                radius = coreR,
                center = center
            )
        }
    }
}

/**
 * Animated audio/signal telemetry waveform bars representing the 'Connecting' handshake.
 */
@Composable
fun ConnectingWaveformFeedback(
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    barColor: Color = AyerCyan
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val duration = 450 + (i * 110)
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_scale_$i"
            )

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp * scale)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(AyerGold, barColor)
                        )
                    )
            )
        }
    }
}

/**
 * High-profile 'Connecting' status banner with live telemetry,
 * animated ripple beacon, signal bars, and dynamic stage description.
 */
@Composable
fun ConnectingStatusBanner(
    modifier: Modifier = Modifier,
    destinationLabel: String = "العالم",
    chatTypeEmoji: String = "📹",
    chatTypeTitle: String = "فيديو",
    latencyMs: Int = 24
) {
    val infiniteTransition = rememberInfiniteTransition(label = "connecting_banner_stage")

    // Dynamic phase indicator cycle (0 to 3)
    val phaseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_progress"
    )

    val currentPhaseText = when (phaseProgress.toInt().coerceIn(0, 2)) {
        0 -> "جاري الاتصال بالسيرفر وتشفير القنوات P2P..."
        1 -> "فحص وتحديد المرشحين المتوافقين في $destinationLabel..."
        else -> "تبادل مفاتيح WebRTC والربط بالمستخدم..."
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xF0180F33),
        border = androidx.compose.foundation.BorderStroke(1.dp, AyerCyan.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                ConnectingStatusBeacon(
                    size = 20.dp,
                    coreColor = Color(0xFF00E676),
                    rippleColor = AyerCyan
                )

                Spacer(modifier = Modifier.width(10.dp))

                androidx.compose.foundation.layout.Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "جاري الاتصال... Connecting",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AyerGold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$chatTypeEmoji $chatTypeTitle",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentPhaseText,
                        fontSize = 11.sp,
                        color = Color(0xFFB0B0D0),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Latency & Signal bars
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectingWaveformFeedback(
                    barCount = 4,
                    barColor = AyerCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x3300E676),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF00E676).copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "${latencyMs}ms ⚡",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
