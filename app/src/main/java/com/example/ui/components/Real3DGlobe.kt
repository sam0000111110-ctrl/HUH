package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.LiveStreamRoom
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Geographic city node on Earth's surface
 */
private data class EarthCity(
    val name: String,
    val latDeg: Float,
    val lonDeg: Float,
    val isPrimary: Boolean = false
)

private val WORLD_CITIES = listOf(
    EarthCity("Riyadh", 24.7f, 46.7f, isPrimary = true),
    EarthCity("Cairo", 30.0f, 31.2f, isPrimary = true),
    EarthCity("Dubai", 25.2f, 55.3f, isPrimary = true),
    EarthCity("London", 51.5f, -0.1f),
    EarthCity("Paris", 48.8f, 2.3f),
    EarthCity("Tokyo", 35.7f, 139.7f),
    EarthCity("New York", 40.7f, -74.0f),
    EarthCity("Rio", -22.9f, -43.2f),
    EarthCity("Sydney", -33.8f, 151.2f),
    EarthCity("Mumbai", 19.0f, 72.8f)
)

/**
 * High-fidelity Geographic Vector Polygon definition
 */
private data class GeoPolygon(
    val name: String,
    val isWaterBody: Boolean = false,
    val rawPoints: List<Pair<Float, Float>> // (latDeg, lonDeg)
) {
    val interpolatedPoints: List<Pair<Float, Float>> by lazy {
        interpolateGeoPath(rawPoints, stepDeg = 3.5f)
    }
}

/**
 * Interpolates long geodesic segments across degrees to create smooth 3D curves along the sphere curvature
 */
private fun interpolateGeoPath(points: List<Pair<Float, Float>>, stepDeg: Float = 3.5f): List<Pair<Float, Float>> {
    if (points.size < 2) return points
    val result = mutableListOf<Pair<Float, Float>>()
    for (i in 0 until points.size) {
        val p1 = points[i]
        val p2 = points[(i + 1) % points.size]
        result.add(p1)

        var dLon = p2.second - p1.second
        if (dLon > 180f) dLon -= 360f
        if (dLon < -180f) dLon += 360f
        val dLat = p2.first - p1.first
        val dist = hypot(dLat, dLon)

        val steps = (dist / stepDeg).toInt()
        for (s in 1 until steps) {
            val t = s.toFloat() / steps
            val lat = p1.first + dLat * t
            var lon = p1.second + dLon * t
            if (lon > 180f) lon -= 360f
            if (lon < -180f) lon += 360f
            result.add(Pair(lat, lon))
        }
    }
    return result
}

/**
 * High-Fidelity Continental & Island Vector Outlines
 */
private val CONTINENTAL_VECTOR_POLYGONS = listOf(
    // 1. Africa
    GeoPolygon(
        name = "Africa",
        rawPoints = listOf(
            Pair(37.3f, 9.8f), Pair(36.8f, 3.0f), Pair(35.8f, -5.3f), Pair(31.5f, -9.8f),
            Pair(20.8f, -17.0f), Pair(14.7f, -17.4f), Pair(11.5f, -15.5f), Pair(4.3f, -7.5f),
            Pair(4.8f, 1.5f), Pair(4.0f, 9.5f), Pair(0.5f, 9.3f), Pair(-4.5f, 11.8f),
            Pair(-15.2f, 12.1f), Pair(-22.5f, 14.5f), Pair(-28.6f, 16.5f), Pair(-34.8f, 20.0f),
            Pair(-34.0f, 25.7f), Pair(-28.5f, 32.5f), Pair(-18.0f, 36.8f), Pair(-10.5f, 40.5f),
            Pair(-4.0f, 39.5f), Pair(2.0f, 45.3f), Pair(11.8f, 51.3f), Pair(11.8f, 43.5f),
            Pair(18.0f, 38.5f), Pair(24.0f, 35.5f), Pair(27.8f, 34.5f), Pair(31.3f, 32.3f),
            Pair(31.5f, 25.0f), Pair(32.8f, 20.0f), Pair(37.3f, 10.5f)
        )
    ),

    // 2. Madagascar
    GeoPolygon(
        name = "Madagascar",
        rawPoints = listOf(
            Pair(-12.0f, 49.3f), Pair(-15.5f, 50.5f), Pair(-20.5f, 48.5f), Pair(-25.5f, 47.0f),
            Pair(-25.5f, 44.5f), Pair(-21.0f, 43.5f), Pair(-16.0f, 44.5f), Pair(-12.0f, 49.3f)
        )
    ),

    // 3. Arabian Peninsula
    GeoPolygon(
        name = "Arabian Peninsula",
        rawPoints = listOf(
            Pair(31.5f, 34.5f), Pair(28.0f, 35.0f), Pair(22.5f, 39.0f), Pair(16.5f, 42.5f),
            Pair(12.7f, 45.0f), Pair(14.5f, 50.0f), Pair(17.0f, 55.0f), Pair(22.5f, 59.8f),
            Pair(26.2f, 56.4f), Pair(24.5f, 54.0f), Pair(24.0f, 51.5f), Pair(26.0f, 50.5f),
            Pair(30.0f, 48.0f), Pair(30.0f, 44.0f), Pair(32.0f, 36.5f), Pair(31.5f, 34.5f)
        )
    ),

    // 4. Europe & Mediterranean Coastline
    GeoPolygon(
        name = "Europe",
        rawPoints = listOf(
            Pair(36.0f, -5.5f), Pair(37.0f, -9.0f), Pair(43.5f, -9.3f), Pair(43.5f, -2.0f),
            Pair(47.5f, -3.0f), Pair(49.5f, 0.0f), Pair(51.0f, 2.5f), Pair(53.5f, 7.0f),
            Pair(57.0f, 8.5f), Pair(55.5f, 12.5f), Pair(54.5f, 19.0f), Pair(58.5f, 22.0f),
            Pair(59.5f, 30.0f), Pair(65.0f, 40.0f), Pair(68.0f, 50.0f), Pair(60.0f, 60.0f),
            Pair(52.0f, 50.0f), Pair(46.0f, 48.0f), Pair(45.5f, 36.0f), Pair(46.5f, 31.0f),
            Pair(43.5f, 28.5f), Pair(41.0f, 29.0f), Pair(38.0f, 24.0f), Pair(36.5f, 22.0f),
            Pair(40.0f, 18.5f), Pair(45.5f, 13.5f), Pair(44.0f, 8.5f), Pair(43.0f, 4.0f),
            Pair(41.5f, 2.5f), Pair(36.8f, -2.0f), Pair(36.0f, -5.5f)
        )
    ),

    // 5. Scandinavia
    GeoPolygon(
        name = "Scandinavia",
        rawPoints = listOf(
            Pair(58.0f, 7.0f), Pair(62.0f, 5.0f), Pair(68.0f, 14.0f), Pair(71.0f, 26.0f),
            Pair(70.0f, 31.0f), Pair(66.0f, 24.0f), Pair(60.0f, 19.0f), Pair(56.0f, 13.0f),
            Pair(58.0f, 7.0f)
        )
    ),

    // 6. British Isles
    GeoPolygon(
        name = "British Isles",
        rawPoints = listOf(
            Pair(50.0f, -5.2f), Pair(51.5f, -4.5f), Pair(53.5f, -4.5f), Pair(55.0f, -5.5f),
            Pair(58.5f, -5.0f), Pair(58.5f, -2.0f), Pair(56.0f, -2.5f), Pair(52.5f, 1.8f),
            Pair(51.0f, 1.4f), Pair(50.5f, -2.0f), Pair(50.0f, -5.2f)
        )
    ),

    // 7. Asia (India, East Asia, Siberia, Indochina)
    GeoPolygon(
        name = "Asia",
        rawPoints = listOf(
            Pair(25.0f, 62.0f), Pair(24.0f, 67.5f), Pair(21.0f, 70.0f), Pair(20.0f, 73.0f),
            Pair(15.0f, 74.0f), Pair(8.0f, 77.5f), Pair(10.0f, 80.0f), Pair(16.0f, 82.0f),
            Pair(21.5f, 87.0f), Pair(22.0f, 92.0f), Pair(16.0f, 94.5f), Pair(12.0f, 99.0f),
            Pair(6.0f, 100.0f), Pair(1.3f, 104.0f), Pair(8.0f, 103.0f), Pair(10.5f, 107.5f),
            Pair(16.0f, 108.5f), Pair(21.5f, 108.0f), Pair(22.5f, 114.0f), Pair(30.0f, 122.0f),
            Pair(35.0f, 119.5f), Pair(39.0f, 121.5f), Pair(38.0f, 126.0f), Pair(35.0f, 129.0f),
            Pair(38.5f, 128.5f), Pair(43.0f, 132.0f), Pair(50.0f, 140.5f), Pair(56.0f, 156.0f),
            Pair(60.0f, 163.0f), Pair(66.0f, 170.0f), Pair(66.0f, -170.0f), Pair(70.0f, 180.0f),
            Pair(73.0f, 140.0f), Pair(77.0f, 105.0f), Pair(74.0f, 75.0f), Pair(70.0f, 60.0f),
            Pair(60.0f, 55.0f), Pair(45.0f, 55.0f), Pair(38.0f, 54.0f), Pair(30.0f, 58.0f),
            Pair(25.0f, 62.0f)
        )
    ),

    // 8. Japan
    GeoPolygon(
        name = "Japan",
        rawPoints = listOf(
            Pair(31.0f, 130.5f), Pair(33.5f, 133.5f), Pair(35.5f, 137.0f), Pair(40.5f, 140.0f),
            Pair(45.0f, 142.0f), Pair(43.0f, 145.5f), Pair(38.0f, 141.5f), Pair(34.5f, 136.0f),
            Pair(31.0f, 130.5f)
        )
    ),

    // 9. Indonesia & Australia & Oceania
    GeoPolygon(
        name = "Australia",
        rawPoints = listOf(
            Pair(-12.0f, 131.0f), Pair(-15.0f, 124.5f), Pair(-22.0f, 114.0f), Pair(-28.0f, 114.0f),
            Pair(-35.0f, 117.0f), Pair(-35.0f, 126.0f), Pair(-32.0f, 133.0f), Pair(-38.5f, 144.0f),
            Pair(-37.5f, 150.0f), Pair(-32.0f, 152.5f), Pair(-24.0f, 153.0f), Pair(-18.0f, 146.0f),
            Pair(-11.0f, 142.5f), Pair(-14.5f, 136.0f), Pair(-12.0f, 131.0f)
        )
    ),
    GeoPolygon(
        name = "Sumatra Java",
        rawPoints = listOf(
            Pair(5.5f, 95.5f), Pair(0.0f, 102.0f), Pair(-5.5f, 106.0f), Pair(-7.5f, 112.0f),
            Pair(-8.5f, 115.0f), Pair(-6.5f, 106.0f), Pair(-3.0f, 100.0f), Pair(5.5f, 95.5f)
        )
    ),
    GeoPolygon(
        name = "New Zealand",
        rawPoints = listOf(
            Pair(-35.0f, 174.0f), Pair(-38.0f, 178.0f), Pair(-41.5f, 175.0f), Pair(-46.5f, 168.0f),
            Pair(-44.0f, 169.0f), Pair(-41.0f, 172.0f), Pair(-35.0f, 174.0f)
        )
    ),

    // 10. North America
    GeoPolygon(
        name = "North America",
        rawPoints = listOf(
            Pair(71.0f, -156.0f), Pair(65.0f, -168.0f), Pair(58.0f, -160.0f), Pair(54.0f, -164.0f),
            Pair(58.0f, -137.0f), Pair(49.0f, -125.0f), Pair(38.0f, -123.0f), Pair(32.0f, -117.0f),
            Pair(23.0f, -110.0f), Pair(18.0f, -104.0f), Pair(15.0f, -93.0f), Pair(8.5f, -80.0f),
            Pair(15.5f, -88.0f), Pair(21.0f, -87.0f), Pair(22.0f, -97.5f), Pair(29.0f, -94.0f),
            Pair(25.0f, -81.0f), Pair(30.5f, -81.5f), Pair(35.5f, -75.5f), Pair(41.0f, -72.0f),
            Pair(44.5f, -64.0f), Pair(52.0f, -56.0f), Pair(58.0f, -62.0f), Pair(62.0f, -70.0f),
            Pair(68.0f, -85.0f), Pair(72.0f, -95.0f), Pair(70.0f, -130.0f), Pair(71.0f, -156.0f)
        )
    ),

    // 11. Greenland
    GeoPolygon(
        name = "Greenland",
        rawPoints = listOf(
            Pair(60.0f, -44.0f), Pair(65.0f, -38.0f), Pair(76.0f, -20.0f), Pair(82.5f, -30.0f),
            Pair(78.0f, -68.0f), Pair(68.0f, -54.0f), Pair(60.0f, -44.0f)
        )
    ),

    // 12. South America
    GeoPolygon(
        name = "South America",
        rawPoints = listOf(
            Pair(11.5f, -72.0f), Pair(10.0f, -62.0f), Pair(5.0f, -52.0f), Pair(-2.0f, -44.0f),
            Pair(-6.0f, -35.0f), Pair(-13.0f, -38.5f), Pair(-23.0f, -43.0f), Pair(-33.0f, -52.0f),
            Pair(-42.0f, -64.0f), Pair(-54.5f, -68.0f), Pair(-51.0f, -74.0f), Pair(-42.0f, -74.0f),
            Pair(-30.0f, -71.5f), Pair(-18.0f, -71.0f), Pair(-5.0f, -81.0f), Pair(2.0f, -78.0f),
            Pair(8.5f, -77.5f), Pair(11.5f, -72.0f)
        )
    ),

    // 13. Antarctica
    GeoPolygon(
        name = "Antarctica",
        rawPoints = listOf(
            Pair(-64.0f, -60.0f), Pair(-67.0f, -10.0f), Pair(-68.0f, 40.0f), Pair(-66.0f, 90.0f),
            Pair(-66.0f, 140.0f), Pair(-72.0f, 170.0f), Pair(-78.0f, -170.0f), Pair(-73.0f, -110.0f),
            Pair(-64.0f, -60.0f)
        )
    )
)

/**
 * Major High-Fidelity Water Bodies & Inland Seas
 */
private val WATER_BODIES_VECTOR_POLYGONS = listOf(
    // 1. Mediterranean Sea
    GeoPolygon(
        name = "Mediterranean Sea",
        isWaterBody = true,
        rawPoints = listOf(
            Pair(36.0f, -5.0f), Pair(40.0f, 2.0f), Pair(43.5f, 9.0f), Pair(44.0f, 14.0f),
            Pair(40.0f, 19.0f), Pair(36.0f, 24.0f), Pair(35.5f, 35.0f), Pair(31.5f, 34.0f),
            Pair(31.5f, 26.0f), Pair(32.5f, 14.0f), Pair(37.0f, 10.5f), Pair(36.0f, -5.0f)
        )
    ),

    // 2. Red Sea
    GeoPolygon(
        name = "Red Sea",
        isWaterBody = true,
        rawPoints = listOf(
            Pair(29.0f, 32.5f), Pair(26.0f, 35.0f), Pair(20.0f, 39.0f), Pair(13.5f, 43.0f),
            Pair(12.8f, 43.5f), Pair(16.0f, 41.5f), Pair(22.0f, 38.0f), Pair(27.5f, 34.0f),
            Pair(29.0f, 32.5f)
        )
    ),

    // 3. Arabian / Persian Gulf
    GeoPolygon(
        name = "Arabian Gulf",
        isWaterBody = true,
        rawPoints = listOf(
            Pair(30.0f, 48.2f), Pair(28.0f, 51.5f), Pair(24.5f, 54.5f), Pair(26.0f, 56.2f),
            Pair(27.5f, 52.0f), Pair(29.5f, 50.0f), Pair(30.0f, 48.2f)
        )
    ),

    // 4. Black Sea
    GeoPolygon(
        name = "Black Sea",
        isWaterBody = true,
        rawPoints = listOf(
            Pair(42.0f, 28.5f), Pair(46.0f, 31.0f), Pair(45.0f, 36.5f), Pair(42.5f, 41.5f),
            Pair(41.0f, 38.0f), Pair(41.5f, 30.0f), Pair(42.0f, 28.5f)
        )
    ),

    // 5. Caspian Sea
    GeoPolygon(
        name = "Caspian Sea",
        isWaterBody = true,
        rawPoints = listOf(
            Pair(47.0f, 48.0f), Pair(46.5f, 52.5f), Pair(42.0f, 53.0f), Pair(37.0f, 50.5f),
            Pair(38.5f, 49.0f), Pair(43.0f, 48.0f), Pair(47.0f, 48.0f)
        )
    ),

    // 6. North American Great Lakes
    GeoPolygon(
        name = "Great Lakes",
        isWaterBody = true,
        rawPoints = listOf(
            Pair(45.5f, -87.5f), Pair(48.5f, -88.0f), Pair(46.5f, -82.0f), Pair(43.5f, -78.5f),
            Pair(42.0f, -82.5f), Pair(45.5f, -87.5f)
        )
    )
)

/**
 * 3D Projected Screen Point structure
 */
private data class ProjectedPoint(
    val offset: Offset,
    val zNormal: Float,
    val isFrontFacing: Boolean
)

/**
 * Mathematical 3D Spherical Orthographic Projection
 */
private fun projectSphere(
    latDeg: Float,
    lonDeg: Float,
    rotationAngle: Float,
    axialTiltRad: Float,
    radius: Float,
    center: Offset
): ProjectedPoint {
    val latR = Math.toRadians(latDeg.toDouble()).toFloat()
    val lonR = Math.toRadians((lonDeg + rotationAngle).toDouble()).toFloat()

    val cosLat = cos(latR)
    val sinLat = sin(latR)
    val cosLon = cos(lonR)
    val sinLon = sin(lonR)

    // 3D Unit sphere coordinates
    val x3d = radius * cosLat * sinLon
    val y3d = radius * sinLat
    val z3d = radius * cosLat * cosLon

    // Tilted by Earth Axial Tilt (23.4 degrees)
    val yTilted = y3d * cos(axialTiltRad) + z3d * sin(axialTiltRad)
    val zTilted = z3d * cos(axialTiltRad) - y3d * sin(axialTiltRad)
    val xTilted = x3d

    val isFront = zTilted > -radius * 0.08f
    val screenX = center.x + xTilted
    val screenY = center.y - yTilted

    return ProjectedPoint(
        offset = Offset(screenX, screenY),
        zNormal = (zTilted / radius).coerceIn(-1f, 1f),
        isFrontFacing = isFront
    )
}

/**
 * Helper to render 3D projected vector polygon on Canvas
 */
private fun DrawScope.draw3DGeoPolygon(
    geo: GeoPolygon,
    rotationAngle: Float,
    axialTiltRad: Float,
    radius: Float,
    center: Offset,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Float,
    glowColor: Color? = null
) {
    val projected = geo.interpolatedPoints.map {
        projectSphere(it.first, it.second, rotationAngle, axialTiltRad, radius, center)
    }

    // Build continuous front-hemisphere path segments
    val visibleSegments = mutableListOf<MutableList<ProjectedPoint>>()
    var currentSegment = mutableListOf<ProjectedPoint>()

    projected.forEach { pt ->
        if (pt.isFrontFacing) {
            currentSegment.add(pt)
        } else {
            if (currentSegment.isNotEmpty()) {
                visibleSegments.add(currentSegment)
                currentSegment = mutableListOf()
            }
        }
    }
    if (currentSegment.isNotEmpty()) {
        if (visibleSegments.size > 1 && projected.first().isFrontFacing && projected.last().isFrontFacing) {
            val firstSeg = visibleSegments.removeAt(0)
            currentSegment.addAll(firstSeg)
        }
        visibleSegments.add(currentSegment)
    }

    // 1. Draw Landmass / Sea Solid Biome Fill
    visibleSegments.forEach { seg ->
        if (seg.size >= 3) {
            val fillPath = Path().apply {
                moveTo(seg[0].offset.x, seg[0].offset.y)
                for (i in 1 until seg.size) {
                    lineTo(seg[i].offset.x, seg[i].offset.y)
                }
                close()
            }
            drawPath(
                path = fillPath,
                color = fillColor
            )
        }
    }

    // 2. Draw Vector Coastline Outlines & Bathymetric Shelf Glow
    visibleSegments.forEach { seg ->
        if (seg.size >= 2) {
            val strokePath = Path().apply {
                moveTo(seg[0].offset.x, seg[0].offset.y)
                for (i in 1 until seg.size) {
                    lineTo(seg[i].offset.x, seg[i].offset.y)
                }
            }

            // Outer Continental Shelf Glow
            if (glowColor != null) {
                drawPath(
                    path = strokePath,
                    color = glowColor,
                    style = Stroke(width = strokeWidth * 2.2f)
                )
            }

            // Sharp Primary Vector Coastline
            drawPath(
                path = strokePath,
                color = strokeColor,
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

/**
 * Realistic 3D Planet Earth Globe Component for Ayer.
 *
 * Key features:
 * 1. Single-source, perfectly synchronized 3D Orthographic Spherical Projection.
 * 2. Ultra-smooth, frame-delta continuous rotation engine with zero stutter or jumps.
 * 3. Physics-based touch drag, fling momentum, and seamless idle auto-rotation fallback.
 * 4. Harmonized geographic biomes (emerald & sand terrain, deep oceanic gradients, luminous cyan shelves).
 * 5. Dynamic day/night terminator with twilight atmospheric scattering and night city clusters.
 * 6. Rayleigh scattering atmosphere & luminous Fresnel limb glow.
 */
@Composable
fun Real3DGlobe(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    isSearching: Boolean = false,
    autoRotate: Boolean = true,
    enableGestures: Boolean = true,
    idleRotationDurationMillis: Int = 26000,
    activeRotationDurationMillis: Int = 3500,
    idleResumeDelayMillis: Long = 1800L,
    zoomScale: Float = 1f,
    onClick: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val rotationAnim = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var lastDragTimestamp by remember { mutableLongStateOf(0L) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    // Butter-smooth frame-time continuous auto-rotation engine
    LaunchedEffect(isSearching, autoRotate, isDragging, lastDragTimestamp) {
        if (!isDragging) {
            if (lastDragTimestamp > 0L) {
                delay(idleResumeDelayMillis)
            }
            if (autoRotate) {
                val speedDegPerSec = if (isSearching) (360f / 3.5f) else (360f / (idleRotationDurationMillis / 1000f))
                var lastTimeNanos = 0L
                while (true) {
                    var deltaDeg = 0f
                    withFrameNanos { frameTimeNanos ->
                        if (lastTimeNanos != 0L) {
                            val dtSeconds = (frameTimeNanos - lastTimeNanos) / 1_000_000_000f
                            deltaDeg = speedDegPerSec * dtSeconds
                        }
                        lastTimeNanos = frameTimeNanos
                    }
                    if (deltaDeg > 0f) {
                        val nextVal = rotationAnim.value + deltaDeg
                        rotationAnim.snapTo(if (nextVal >= 360000f) nextVal % 360f else nextVal)
                    }
                }
            }
        }
    }

    val currentAngle = (rotationAnim.value % 360f + 360f) % 360f
    val angleRad = Math.toRadians(currentAngle.toDouble()).toFloat()

    // Gentle atmospheric breathing pulse
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_halo")
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )

    // Beacon radar ping pulse
    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beacon_pulse"
    )

    // High-frequency water body shimmering reflection phase
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ocean_shimmer_phase"
    )

    // Animated scale for interactive click & zoom
    val animatedScale by animateFloatAsState(
        targetValue = zoomScale,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "globe_zoom"
    )

    // Gesture modifier for manual drag and spin
    val gestureModifier = if (enableGestures) {
        Modifier.pointerInput(size) {
            detectDragGestures(
                onDragStart = {
                    isDragging = true
                    totalDragDistance = 0f
                    dragVelocity = 0f
                    coroutineScope.launch {
                        rotationAnim.stop()
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val moveDist = hypot(dragAmount.x, dragAmount.y)
                    totalDragDistance += moveDist
                    val sensitivity = 360f / (size.toPx() * 3.14159f)
                    val deltaDeg = -dragAmount.x * sensitivity * 1.5f
                    dragVelocity = deltaDeg
                    coroutineScope.launch {
                        rotationAnim.snapTo(rotationAnim.value + deltaDeg)
                    }
                },
                onDragEnd = {
                    isDragging = false
                    lastDragTimestamp = System.currentTimeMillis()
                    if (totalDragDistance < 8f) {
                        onClick()
                    } else {
                        coroutineScope.launch {
                            val flingAmount = dragVelocity * 12f
                            if (abs(flingAmount) > 1.2f) {
                                rotationAnim.animateTo(
                                    targetValue = rotationAnim.value + flingAmount,
                                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                    }
                },
                onDragCancel = {
                    isDragging = false
                    lastDragTimestamp = System.currentTimeMillis()
                }
            )
        }
    } else {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(animatedScale)
            .testTag("real_3d_globe"),
        contentAlignment = Alignment.Center
    ) {
        // 0. CONNECTING RIPPLE WAVES (Active during searching / matchmaking)
        if (isSearching) {
            ConnectingRippleAnimation(
                modifier = Modifier.size(size * 1.55f),
                ringCount = 3,
                cycleDurationMillis = 2200,
                baseRadiusFraction = 0.52f,
                maxRadiusFraction = 0.98f,
                primaryColor = AyerCyan,
                accentColor = AyerGold,
                maxAlpha = 0.65f,
                showDashedAccents = true,
                showParticles = true
            )
        }

        // 1. ATMOSPHERIC OUTER OZONE HALO (Rayleigh Scattering Aura)
        Box(
            modifier = Modifier
                .size(size * 1.22f)
                .scale(haloPulse)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00B4D8).copy(alpha = if (isSearching) 0.38f else 0.20f),
                            Color(0xFF0077B6).copy(alpha = if (isSearching) 0.22f else 0.10f),
                            AyerPrimary.copy(alpha = if (isSearching) 0.12f else 0.04f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        // 2. RADAR SCANNING SWEEP BEAM (ONLY when isSearching)
        if (isSearching) {
            Box(
                modifier = Modifier
                    .size(size * 1.15f)
                    .graphicsLayer { rotationZ = -currentAngle * 1.5f }
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                AyerCyan.copy(alpha = 0.40f),
                                AyerGold.copy(alpha = 0.20f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
        }

        // 3. THE UNIFIED 3D PLANET EARTH SPHERE CONTAINER
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF020B1A))
                .then(gestureModifier),
            contentAlignment = Alignment.Center
        ) {
            // 3.1 NATIVE HIGH-PERFORMANCE 3D ORTHOGRAPHIC SPHERE CANVAS
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.toPx() / 2f
                val center = Offset(radius, radius)
                val axialTiltRad = Math.toRadians(23.4).toFloat()

                // --- A. Realistic 3D Deep Ocean Bathymetric Base ---
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0E4375), // Shallow tropical waters
                            Color(0xFF07294D), // Mid-depth ocean
                            Color(0xFF03162F), // Deep oceanic abyss
                            Color(0xFF010A18)  // Shadowed horizon rim
                        ),
                        center = Offset(radius * 0.72f, radius * 0.68f),
                        radius = radius * 1.18f
                    ),
                    radius = radius,
                    center = center
                )

                // --- A2. HIGH-FREQUENCY ANIMATED OCEAN SHIMMER WAVE PATHS ---
                val oceanLatitudes = listOf(-60f, -42f, -24f, -8f, 12f, 28f, 46f, 62f)
                oceanLatitudes.forEachIndexed { idx, latDeg ->
                    val latRad = Math.toRadians(latDeg.toDouble()).toFloat()
                    val yOffset = radius * sin(latRad) * cos(axialTiltRad)
                    val latRadiusX = radius * cos(latRad)
                    val latRadiusY = latRadiusX * sin(axialTiltRad) + 2.5.dp.toPx()

                    if (latRadiusX > 4.dp.toPx()) {
                        val waveHarmonic = sin(shimmerPhase * 2.2f + idx * 0.9f) * 1.2.dp.toPx()
                        val shimmerAlpha = (0.12f + 0.12f * sin(shimmerPhase * 1.8f + idx * 1.4f)).coerceIn(0.04f, 0.28f)

                        drawOval(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0x3580E9FF).copy(alpha = shimmerAlpha * 0.6f),
                                    Color(0x7500E5FF).copy(alpha = shimmerAlpha),
                                    Color(0x3580E9FF).copy(alpha = shimmerAlpha * 0.6f),
                                    Color.Transparent
                                )
                            ),
                            topLeft = Offset(center.x - latRadiusX, center.y - yOffset - latRadiusY + waveHarmonic),
                            size = Size(latRadiusX * 2, latRadiusY * 2),
                            style = Stroke(
                                width = 1.0.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(12f, 16f, 5f, 12f),
                                    (currentAngle * 1.8f + shimmerPhase * 28f + idx * 10f)
                                )
                            )
                        )
                    }
                }

                // --- A3. HIGH-FREQUENCY SOLAR WATER CAUSTICS & SPECULAR SHIMMER FLARES ---
                val waterGlintPoints = listOf(
                    Pair(14f, -38f), Pair(-16f, -28f), Pair(4f, -142f), Pair(-24f, -112f),
                    Pair(34f, -158f), Pair(-2f, 58f), Pair(-28f, 82f), Pair(26f, 152f),
                    Pair(-14f, 168f), Pair(46f, -32f), Pair(-40f, -128f), Pair(16f, 118f)
                )
                waterGlintPoints.forEachIndexed { i, (gLat, gLon) ->
                    val pt = projectSphere(gLat, gLon, currentAngle, axialTiltRad, radius, center)
                    if (pt.isFrontFacing && pt.zNormal > 0.12f) {
                        val glintIntensity = ((sin(shimmerPhase * 3.4f + i * 1.8f) + 1f) / 2f) * pt.zNormal
                        if (glintIntensity > 0.30f) {
                            val flareSize = (2.0.dp.toPx() + 3.2.dp.toPx() * glintIntensity)
                            val flareAlpha = (glintIntensity * 0.70f).coerceIn(0f, 0.85f)
                            drawLine(
                                color = Color.White.copy(alpha = flareAlpha),
                                start = Offset(pt.offset.x - flareSize, pt.offset.y),
                                end = Offset(pt.offset.x + flareSize, pt.offset.y),
                                strokeWidth = 0.8.dp.toPx()
                            )
                            drawLine(
                                color = Color(0xFF80E9FF).copy(alpha = flareAlpha),
                                start = Offset(pt.offset.x, pt.offset.y - flareSize),
                                end = Offset(pt.offset.x, pt.offset.y + flareSize),
                                strokeWidth = 0.8.dp.toPx()
                            )
                            drawCircle(
                                color = Color(0xFF00E5FF).copy(alpha = flareAlpha * 0.5f),
                                radius = 1.5.dp.toPx() * glintIntensity,
                                center = pt.offset
                            )
                        }
                    }
                }

                // --- B. Draw 3D Rotating Longitude Meridians (12 meridians, 30° apart) ---
                val meridianCount = 12
                for (i in 0 until meridianCount) {
                    val baseAngle = (i.toFloat() / meridianCount) * 2f * PI.toFloat()
                    val totalAngle = baseAngle + angleRad
                    val cosVal = cos(totalAngle)

                    if (cosVal >= -0.10f) {
                        val meridianWidth = radius * abs(cosVal)
                        val alpha = ((cosVal + 0.10f) / 1.10f).coerceIn(0.08f, 0.9f)

                        drawOval(
                            color = AyerCyan.copy(alpha = 0.16f * alpha),
                            topLeft = Offset(center.x - meridianWidth, 0f),
                            size = Size(meridianWidth * 2, radius * 2),
                            style = Stroke(width = 0.8.dp.toPx())
                        )
                    }
                }

                // --- C. Draw Latitude Parallels (Equator, Tropics, Arctic Circles) ---
                val latitudes = listOf(
                    Triple(0f, AyerGold.copy(alpha = 0.50f), 1.3.dp.toPx()),          // Equator
                    Triple(23.4f, AyerCyan.copy(alpha = 0.26f), 0.7.dp.toPx()),       // Tropic of Cancer
                    Triple(-23.4f, AyerCyan.copy(alpha = 0.26f), 0.7.dp.toPx()),      // Tropic of Capricorn
                    Triple(66.5f, Color(0xFFBAE6FD).copy(alpha = 0.20f), 0.6.dp.toPx()), // Arctic Circle
                    Triple(-66.5f, Color(0xFFBAE6FD).copy(alpha = 0.20f), 0.6.dp.toPx()) // Antarctic Circle
                )

                latitudes.forEach { (latDeg, color, strokeW) ->
                    val latRad = Math.toRadians(latDeg.toDouble()).toFloat()
                    val yOffset = radius * sin(latRad) * cos(axialTiltRad)
                    val latRadiusX = radius * cos(latRad)
                    val latRadiusY = latRadiusX * sin(axialTiltRad) + 2.5.dp.toPx()

                    if (latRadiusX > 2.dp.toPx()) {
                        drawOval(
                            color = color,
                            topLeft = Offset(center.x - latRadiusX, center.y - yOffset - latRadiusY),
                            size = Size(latRadiusX * 2, latRadiusY * 2),
                            style = Stroke(
                                width = strokeW,
                                pathEffect = if (latDeg != 0f) PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) else null
                            )
                        )
                    }
                }

                // --- D. Draw High-Fidelity Continental Landmasses with Natural Biomes ---
                CONTINENTAL_VECTOR_POLYGONS.forEach { continent ->
                    draw3DGeoPolygon(
                        geo = continent,
                        rotationAngle = currentAngle,
                        axialTiltRad = axialTiltRad,
                        radius = radius,
                        center = center,
                        fillColor = Color(0xFF1B6B48).copy(alpha = 0.88f), // Lush terrain biome
                        strokeColor = Color(0xFF00E5FF).copy(alpha = 0.95f), // High-contrast cyan coastline
                        strokeWidth = 1.3.dp.toPx(),
                        glowColor = Color(0xFF00B4D8).copy(alpha = 0.30f)  // Coastal shelf halo
                    )
                }

                // --- E. Draw Major Water Bodies & Inland Enclosed Seas with Shimmering Waves ---
                WATER_BODIES_VECTOR_POLYGONS.forEachIndexed { idx, sea ->
                    draw3DGeoPolygon(
                        geo = sea,
                        rotationAngle = currentAngle,
                        axialTiltRad = axialTiltRad,
                        radius = radius,
                        center = center,
                        fillColor = Color(0xFF07294D).copy(alpha = 0.92f), // Deep enclosed water tint
                        strokeColor = AyerCyan.copy(alpha = 0.80f),
                        strokeWidth = 1.0.dp.toPx(),
                        glowColor = Color(0xFF0077B6).copy(alpha = 0.20f)
                    )

                    // Subtle high-frequency surface refraction shimmer across inland seas
                    val seaShimmerAlpha = (0.12f + 0.12f * sin(shimmerPhase * 2.4f + idx * 1.5f)).coerceIn(0.02f, 0.25f)
                    draw3DGeoPolygon(
                        geo = sea,
                        rotationAngle = currentAngle,
                        axialTiltRad = axialTiltRad,
                        radius = radius,
                        center = center,
                        fillColor = Color(0xFF00E5FF).copy(alpha = seaShimmerAlpha * 0.4f),
                        strokeColor = Color.White.copy(alpha = seaShimmerAlpha * 1.5f),
                        strokeWidth = 0.8.dp.toPx(),
                        glowColor = null
                    )
                }

                // --- F. Projected 3D City Beacons & Connection Flight Arcs ---
                val visibleCityPoints = mutableListOf<Pair<EarthCity, Offset>>()

                WORLD_CITIES.forEach { city ->
                    val pt = projectSphere(city.latDeg, city.lonDeg, currentAngle, axialTiltRad, radius, center)
                    if (pt.isFrontFacing && pt.zNormal > 0.05f) {
                        visibleCityPoints.add(Pair(city, pt.offset))

                        val visibilityFactor = pt.zNormal.coerceIn(0.2f, 1f)
                        val beaconColor = if (city.isPrimary) AyerGold else AyerCyan

                        // Draw City glowing pin point
                        drawCircle(
                            color = beaconColor.copy(alpha = 0.95f * visibilityFactor),
                            radius = if (city.isPrimary) 3.5.dp.toPx() else 2.2.dp.toPx(),
                            center = pt.offset
                        )

                        // Draw City radar ripple
                        val pingRadius = (4.dp.toPx() + (12.dp.toPx() * beaconPulse)) * visibilityFactor
                        val pingAlpha = ((1f - beaconPulse) * 0.75f * visibilityFactor).coerceIn(0f, 1f)
                        drawCircle(
                            color = beaconColor.copy(alpha = pingAlpha),
                            radius = pingRadius,
                            center = pt.offset,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }
                }

                // --- G. Intercontinental Matchmaking Flight / Radar Arcs ---
                if (visibleCityPoints.size >= 2) {
                    val hub = visibleCityPoints.firstOrNull { it.first.isPrimary } ?: visibleCityPoints[0]
                    for (i in 1 until visibleCityPoints.size) {
                        val target = visibleCityPoints[i]
                        val path = Path().apply {
                            moveTo(hub.second.x, hub.second.y)
                            val midX = (hub.second.x + target.second.x) / 2f
                            val midY = (hub.second.y + target.second.y) / 2f - (18.dp.toPx() * if (isSearching) 1.2f else 0.8f)
                            quadraticTo(midX, midY, target.second.x, target.second.y)
                        }

                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(
                                listOf(
                                    AyerGold.copy(alpha = if (isSearching) 0.85f else 0.40f),
                                    AyerCyan.copy(alpha = if (isSearching) 0.90f else 0.50f)
                                ),
                                start = hub.second,
                                end = target.second
                            ),
                            style = Stroke(
                                width = if (isSearching) 1.5.dp.toPx() else 1.0.dp.toPx(),
                                pathEffect = if (isSearching) PathEffect.dashPathEffect(floatArrayOf(12f, 8f), beaconPulse * 20f) else null
                            )
                        )
                    }
                }

                // --- H. Tilted Earth Polar Axis Line ---
                val poleDx = radius * sin(axialTiltRad)
                val poleDy = radius * cos(axialTiltRad)
                drawLine(
                    color = AyerGold.copy(alpha = 0.35f),
                    start = Offset(center.x - poleDx, center.y - poleDy),
                    end = Offset(center.x + poleDx, center.y + poleDy),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )

                // North Pole Star Marker
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = 2.dp.toPx(),
                    center = Offset(center.x - poleDx, center.y - poleDy)
                )

                // --- I. Night-Side Glowing City Lights (Visible in the Unlit Shadow Zone) ---
                WORLD_CITIES.forEach { city ->
                    val pt = projectSphere(city.latDeg, city.lonDeg, currentAngle, axialTiltRad, radius, center)
                    if (pt.isFrontFacing && pt.zNormal > 0.05f) {
                        val cityLonRad = Math.toRadians((city.lonDeg + currentAngle).toDouble()).toFloat()
                        val isNightSide = sin(cityLonRad) < 0.15f
                        if (isNightSide) {
                            val nightFactor = (1f - sin(cityLonRad).coerceIn(-1f, 1f)) / 2f
                            drawCircle(
                                color = AyerGold.copy(alpha = 0.70f * nightFactor * pt.zNormal),
                                radius = 2.0.dp.toPx(),
                                center = pt.offset
                            )
                            drawCircle(
                                color = Color(0xFFFFD54F).copy(alpha = 0.40f * nightFactor * pt.zNormal),
                                radius = 4.0.dp.toPx(),
                                center = pt.offset
                            )
                        }
                    }
                }
            }

            // 3.2 DYNAMIC DAY/NIGHT CYCLE: Dynamic Sunlight Specular Glint & Forward Ocean Scattering (Daylit Hemisphere)
            val sunDirectionDeg = 135f + (currentAngle * 0.25f)
            val sunRad = Math.toRadians(sunDirectionDeg.toDouble()).toFloat()
            val sunCenterRatioX = 0.50f - 0.28f * cos(sunRad)
            val sunCenterRatioY = 0.50f - 0.28f * sin(sunRad)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.38f),       // Intense solar specular core
                                Color(0x6500E5FF),                    // Tropical cyan surface glare
                                Color(0x250077B6),                    // Atmospheric diffuse spread
                                Color.Transparent
                            ),
                            center = Offset(size.value * sunCenterRatioX, size.value * sunCenterRatioY),
                            radius = size.value * 0.58f
                        )
                    )
            )

            // 3.3 DYNAMIC DAY/NIGHT CYCLE: High-Contrast Deep Blue-Black Layer with Differential Light Scattering
            val nightAngleDeg = sunDirectionDeg + 180f
            val nightRad = Math.toRadians(nightAngleDeg.toDouble()).toFloat()
            val nightCenterRatioX = 0.50f + 0.38f * cos(nightRad)
            val nightCenterRatioY = 0.50f + 0.38f * sin(nightRad)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.toPx() / 2f
                val globeCenter = Offset(r, r)
                val nightCenter = Offset(r * 2f * nightCenterRatioX, r * 2f * nightCenterRatioY)

                // 1. Deep Oceanic Absorption & Umbra Abyss Layer (Darkens oceans & water bodies to deep blue-black)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFA000308), // 98% Deepest obsidian blue-black (Umbra Core)
                            Color(0xF2010614), // 95% Deep oceanic midnight absorption
                            Color(0xC8020D26), // 78% Dark indigo penumbra
                            Color(0x75031438), // 46% Twilight diffusion zone
                            Color(0x38FF5E00), // Rayleigh/Mie sunset atmospheric scattering band
                            Color(0x15FFA000), // Golden hour rim
                            Color.Transparent
                        ),
                        center = nightCenter,
                        radius = r * 1.38f
                    ),
                    radius = r,
                    center = globeCenter
                )

                // 2. Differential Terrain & Atmosphere Linear Terminator Shift (Scatters sunset light on terrain)
                val startTerminator = Offset(
                    globeCenter.x - r * cos(nightRad),
                    globeCenter.y - r * sin(nightRad)
                )
                val endTerminator = Offset(
                    globeCenter.x + r * cos(nightRad),
                    globeCenter.y + r * sin(nightRad)
                )

                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x28FF6D00), // Warm sunset rim on terrain
                            Color(0x40FFA726), // Golden grazing sunlight
                            Color(0x85020A1E), // Rapid water absorption boundary
                            Color(0xDC010512), // Deep night shadow
                            Color(0xF6000207)  // Opaque night-side terrain/ocean mask
                        ),
                        start = startTerminator,
                        end = endTerminator
                    ),
                    radius = r,
                    center = globeCenter
                )

                // 3. Terrain Specular-to-Diffuse Contrast Enhancement (Accentuates day/night contrast)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x2500E5FF),
                            Color(0x1000B4D8),
                            Color.Transparent
                        ),
                        center = Offset(globeCenter.x - (globeCenter.x - nightCenter.x) * 0.8f, globeCenter.y - (globeCenter.y - nightCenter.y) * 0.8f),
                        radius = r * 0.85f
                    ),
                    radius = r,
                    center = globeCenter
                )
            }

            // 3.4 ATMOSPHERIC FRESNEL LIMB BRIGHTENING (Outer Luminous Atmospheric Rim)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color(0x3000B4D8),
                                Color(0x9500E5FF)
                            )
                        )
                    )
            )

            // 3.5 PLANETARY HORIZON BORDER RING
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        BorderStroke(
                            2.dp,
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFF00E5FF),
                                    AyerGold,
                                    AyerPrimary,
                                    Color(0xFF00B4D8),
                                    Color(0xFF00E5FF)
                                )
                            )
                        ),
                        CircleShape
                    )
            )
        }

        // 4. OUTER EQUATORIAL ORBITAL RING (Futuristic Tech Horizon)
        Canvas(modifier = Modifier.size(size * 1.34f)) {
            val center = Offset(size.toPx() * 0.67f, size.toPx() * 0.67f)
            val ringRadiusX = size.toPx() * 0.64f
            val ringRadiusY = size.toPx() * 0.20f

            drawOval(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        AyerGold.copy(alpha = 0.65f),
                        AyerCyan.copy(alpha = 0.75f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(center.x - ringRadiusX, center.y - ringRadiusY),
                size = Size(ringRadiusX * 2, ringRadiusY * 2),
                style = Stroke(
                    width = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), currentAngle * 0.5f)
                )
            )
        }
    }
}

/**
 * Dynamic and Immersive Auto-Rotating Earth / Globe Background for the Live Streaming Lobby.
 *
 * Features:
 * - Ambient starry cosmic canvas with twinkling stars and soft deep space nebula gradients.
 * - Center-top auto-rotating 3D Earth with SVG/Vector continental outlines and illuminated oceans.
 * - Multi-layered orbital broadcast rings with glowing satellite telemetry indicators.
 * - Pulsating global live transmission waves radiating across world capitals.
 * - Contrast-optimized scrim for readability of live stream cards and UI components.
 */
@Composable
fun LiveStreamGlobeBackground(
    modifier: Modifier = Modifier,
    globeSize: Dp = 280.dp,
    autoRotate: Boolean = true,
    enableGestures: Boolean = false,
    rotationSpeedDurationMillis: Int = 28000,
    backgroundStreams: List<LiveStreamRoom> = emptyList(),
    onSelectStream: ((LiveStreamRoom) -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "lobby_globe_bg")

    // Slow ambient nebula breathing
    val nebulaAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nebula_pulse"
    )

    // Orbiting live satellite angle
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_satellite"
    )

    // Broadcast wave pulse
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "broadcast_wave"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030712)) // Deep cosmic canvas
    ) {
        // 1. COSMIC STARFIELD & NEBULA CANVAS
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Deep Space Nebula Glows
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F172A).copy(alpha = nebulaAlpha),
                        Color(0xFF0284C7).copy(alpha = 0.12f * nebulaAlpha),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.28f),
                    radius = w * 0.75f
                )
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AyerPrimary.copy(alpha = 0.08f * nebulaAlpha),
                        Color(0xFF1E1B4B).copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.85f, h * 0.65f),
                    radius = w * 0.60f
                )
            )

            // Twinkling Celestial Star Dots
            val starSeeds = listOf(
                Pair(0.12f, 0.08f), Pair(0.88f, 0.12f), Pair(0.24f, 0.22f), Pair(0.76f, 0.25f),
                Pair(0.06f, 0.40f), Pair(0.94f, 0.45f), Pair(0.18f, 0.60f), Pair(0.82f, 0.68f),
                Pair(0.10f, 0.78f), Pair(0.90f, 0.82f), Pair(0.35f, 0.90f), Pair(0.65f, 0.92f),
                Pair(0.48f, 0.05f), Pair(0.55f, 0.95f), Pair(0.28f, 0.48f), Pair(0.72f, 0.52f)
            )

            starSeeds.forEachIndexed { idx, (sx, sy) ->
                val starBrightness = 0.35f + 0.55f * ((sin(orbitAngle * 0.05f + idx) + 1f) / 2f)
                val starRadius = if (idx % 3 == 0) 1.8.dp.toPx() else 1.1.dp.toPx()
                val starColor = if (idx % 4 == 0) AyerGold else if (idx % 3 == 0) AyerCyan else Color.White

                drawCircle(
                    color = starColor.copy(alpha = starBrightness),
                    radius = starRadius,
                    center = Offset(w * sx, h * sy)
                )
            }
        }

        // 2. AMBIENT AUTO-ROTATING 3D EARTH GLOBE
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-20).dp)
                .graphicsLayer { alpha = 0.82f },
            contentAlignment = Alignment.Center
        ) {
            Real3DGlobe(
                size = globeSize,
                isSearching = false,
                autoRotate = autoRotate,
                enableGestures = enableGestures,
                idleRotationDurationMillis = rotationSpeedDurationMillis
            )

            // Broadcast Waves radiating from Globe Center
            Canvas(modifier = Modifier.size(globeSize * 1.8f)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = globeSize.toPx() * 0.55f
                val maxRadius = globeSize.toPx() * 0.88f
                val currentWaveRadius = baseRadius + (maxRadius - baseRadius) * waveProgress
                val waveAlpha = (1f - waveProgress).coerceIn(0f, 1f) * 0.45f

                drawCircle(
                    color = AyerCyan.copy(alpha = waveAlpha),
                    radius = currentWaveRadius,
                    center = center,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )

                // Secondary Golden Wave
                val waveProgress2 = (waveProgress + 0.5f) % 1f
                val currentWaveRadius2 = baseRadius + (maxRadius - baseRadius) * waveProgress2
                val waveAlpha2 = (1f - waveProgress2).coerceIn(0f, 1f) * 0.35f

                drawCircle(
                    color = AyerGold.copy(alpha = waveAlpha2),
                    radius = currentWaveRadius2,
                    center = center,
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                )

                // Orbiting Live Satellite Beacon Node
                val orbitRad = Math.toRadians(orbitAngle.toDouble()).toFloat()
                val rx = globeSize.toPx() * 0.72f
                val ry = globeSize.toPx() * 0.28f
                val satX = center.x + rx * cos(orbitRad)
                val satY = center.y + ry * sin(orbitRad)

                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = Offset(satX, satY)
                )
                drawCircle(
                    color = AyerCyan.copy(alpha = 0.8f),
                    radius = 7.dp.toPx(),
                    center = Offset(satX, satY),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // 3. READABILITY VIGNETTE & GRADIENT SCRIMS
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x75030712),
                            Color(0xAA030712),
                            Color(0xF5030712),
                            Color(0xFF030712)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
    }
}

