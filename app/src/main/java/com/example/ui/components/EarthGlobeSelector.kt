package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Calm, High-End 3D Interactive Earth Globe Selector for Ayer.
 * Features smooth, calm rotation physics, authentic continent & landmass projections,
 * and professional touch damping.
 */
@Composable
fun EarthGlobeSelector(
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    onCountrySelected: (String) -> Unit = {}
) {
    var rotationY by remember { mutableStateOf(35f) }
    var rotationX by remember { mutableStateOf(15f) }
    var zoomScale by remember { mutableStateOf(1.0f) }
    var selectedCountryCode by remember { mutableStateOf<String?>(null) }
    var isUserInteracting by remember { mutableStateOf(false) }

    // Calm auto-rotation after 3 seconds of inactivity (very smooth 30fps ticker)
    LaunchedEffect(isUserInteracting, rotationY) {
        if (!isUserInteracting) {
            delay(3000L)
            while (!isUserInteracting) {
                rotationY += 0.08f // Calm, majestic slow rotation
                delay(32L) // ~30 FPS calm ticker
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                detectTransformGestures(
                    onGesture = { _, pan, zoom, _ ->
                        isUserInteracting = true
                        // Gentle, damped drag sensitivity for calm control
                        rotationY += pan.x * 0.18f
                        rotationX = (rotationX - pan.y * 0.18f).coerceIn(-45f, 45f)
                        zoomScale = (zoomScale * zoom).coerceIn(1.0f, 2.2f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Atmospheric Rim Glow Aura (Soft luxury cyan/gold blend)
        Box(
            modifier = Modifier
                .size(size * 1.18f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AyerCyan.copy(alpha = 0.25f),
                            AyerPrimary.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        // 3D Interactive Globe Canvas Box
        Box(
            modifier = Modifier
                .size(size)
                .scale(zoomScale)
                .clip(CircleShape)
                .background(Color(0xFF020617))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.pressed }) {
                                isUserInteracting = true
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    val localX = change.position.x
                                    val localY = change.position.y
                                    val radius = size.toPx() / 2f
                                    val dx = localX - radius
                                    val dy = localY - radius
                                    val distSq = dx * dx + dy * dy

                                    if (distSq <= radius * radius) {
                                        val nx = dx / radius
                                        val ny = dy / radius
                                        val nz = sqrt((1.0f - nx * nx - ny * ny).coerceAtLeast(0f))

                                        val radX = Math.toRadians(rotationX.toDouble()).toFloat()
                                        val radY = Math.toRadians(rotationY.toDouble()).toFloat()

                                        val lat = Math.toDegrees(asin(ny * cos(radX) + nz * sin(radX)).toDouble()).toFloat()
                                        val lon = Math.toDegrees(atan2(nx.toDouble(), (nz * cos(radX) - ny * sin(radX)).toDouble())).toFloat() - rotationY

                                        val targetCountry = when {
                                            lat in 15f..35f && lon in 30f..50f -> "SA" // Saudi Arabia
                                            lat in 20f..32f && lon in 25f..35f -> "EG" // Egypt
                                            lat in 20f..26f && lon in 50f..56f -> "AE" // UAE
                                            lat in 35f..55f && lon in -10f..25f -> "EU" // Europe
                                            lat in 20f..50f && lon in -120f..-70f -> "US" // USA
                                            else -> "GLOBAL"
                                        }
                                        selectedCountryCode = targetCountry
                                        onCountrySelected(targetCountry)
                                    }
                                }
                            }
                        }
                    }
            ) {
                val radius = size.toPx() / 2f
                val center = Offset(radius, radius)

                // 1. Ocean Base Deep Radial Gradient
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E3A8A), // Deep oceanic blue
                            Color(0xFF0F172A),
                            Color(0xFF020617)
                        ),
                        center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
                        radius = radius * 1.5f
                    ),
                    radius = radius,
                    center = center
                )

                // 2. Continents & Landmass Representation (Procedural 3D projected land polygons)
                val radY = Math.toRadians(rotationY.toDouble()).toFloat()
                val radX = Math.toRadians(rotationX.toDouble()).toFloat()

                // List of representative landmass center points (Lat, Lon, size/radius scale)
                val continents = listOf(
                    Triple(45.0f, 15.0f, 0.45f),   // Europe
                    Triple(25.0f, 45.0f, 0.50f),   // Middle East / Arabia
                    Triple(5.0f, 20.0f, 0.55f),    // Africa
                    Triple(40.0f, 100.0f, 0.70f),  // Asia / China
                    Triple(-25.0f, 135.0f, 0.40f), // Australia
                    Triple(40.0f, -100.0f, 0.65f), // North America
                    Triple(-15.0f, -60.0f, 0.50f)  // South America
                )

                continents.forEach { (lat, lon, landRadiusScale) ->
                    val latRad = Math.toRadians(lat.toDouble()).toFloat()
                    val lonRad = Math.toRadians(lon.toDouble()).toFloat() + radY

                    // 3D sphere coordinate projection
                    val x3d = cos(latRad) * sin(lonRad)
                    val y3d = sin(latRad)
                    val z3d = cos(latRad) * cos(lonRad)

                    // Apply rotationX tilt
                    val yRotated = y3d * cos(radX) - z3d * sin(radX)
                    val zRotated = y3d * sin(radX) + z3d * cos(radX)

                    // Only render if facing the camera (zRotated > -0.15)
                    if (zRotated > -0.15f) {
                        val screenX = center.x + (x3d * radius).toFloat()
                        val screenY = center.y - (yRotated * radius).toFloat()
                        val landRadius = radius * landRadiusScale * (0.5f + 0.5f * zRotated.coerceAtLeast(0f))

                        val alphaFactor = ((zRotated + 0.15f) / 1.15f).coerceIn(0.1f, 0.9f)

                        // Draw continent landmass shape with rich earthy/golden tone
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF059669).copy(alpha = 0.8f * alphaFactor), // Emerald green land
                                    Color(0xFF047857).copy(alpha = 0.5f * alphaFactor),
                                    Color(0xFF064E3B).copy(alpha = 0.2f * alphaFactor)
                                ),
                                center = Offset(screenX, screenY),
                                radius = landRadius
                            ),
                            radius = landRadius,
                            center = Offset(screenX, screenY)
                        )
                    }
                }

                // 3. Subtle Latitude & Longitude grid lines for professional scientific look
                val meridianCount = 10
                for (i in 0 until meridianCount) {
                    val angle = (i.toFloat() / meridianCount) * 2f * PI.toFloat() + radY
                    val cosVal = cos(angle)
                    if (cosVal >= -0.1f) {
                        val rx = radius * abs(cosVal)
                        val yOffsetTilt = radius * sin(radX) * 0.2f * cos(angle)
                        drawOval(
                            color = AyerCyan.copy(alpha = 0.15f * ((cosVal + 0.1f) / 1.1f)),
                            topLeft = Offset(center.x - rx, center.y + yOffsetTilt - radius * cos(radX)),
                            size = Size(rx * 2f, radius * 2f * cos(radX)),
                            style = Stroke(width = 0.8.dp.toPx())
                        )
                    }
                }

                // 4. Selected Country Marker with calm pulsing ring
                if (selectedCountryCode != null) {
                    drawCircle(
                        color = AyerGold,
                        radius = 10.dp.toPx(),
                        center = Offset(center.x + 20f, center.y - 10f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = AyerGold.copy(alpha = 0.35f),
                        radius = 20.dp.toPx(),
                        center = Offset(center.x + 20f, center.y - 10f)
                    )
                }

                // 5. Volumetric Terminator Shadow (Day/Night depth shading)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x88000000),
                            Color(0xDD020617)
                        ),
                        center = Offset(center.x + radius * 0.5f, center.y + radius * 0.5f),
                        radius = radius * 1.2f
                    ),
                    radius = radius,
                    center = center
                )

                // 6. Atmospheric Rim Glow (Fresnel Edge)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            AyerCyan.copy(alpha = 0.10f),
                            AyerCyan.copy(alpha = 0.45f)
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this.graphicsLayer(
    scaleX = scale,
    scaleY = scale
)
