package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.LiveStreamRoom
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSurfaceElevated
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * Animated high-frequency shimmer brush for skeleton loading placeholders.
 */
@Composable
fun rememberShimmerBrush(
    targetValue: Float = 1200f,
    durationMillis: Int = 1100
): Brush {
    val shimmerColors = listOf(
        Color(0xFF0F172A).copy(alpha = 0.85f),
        Color(0xFF1E293B).copy(alpha = 0.95f),
        Color(0xFF334155).copy(alpha = 0.60f),
        Color(0xFF1E293B).copy(alpha = 0.95f),
        Color(0xFF0F172A).copy(alpha = 0.85f)
    )

    val transition = rememberInfiniteTransition(label = "skeleton_shimmer_transition")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton_shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnimation.value - 400f, y = translateAnimation.value - 400f),
        end = Offset(x = translateAnimation.value, y = translateAnimation.value)
    )
}

/**
 * Skeleton Placeholder Card for live streams while cached video data is being retrieved/initialized.
 */
@Composable
fun StreamCardSkeleton(
    modifier: Modifier = Modifier,
    height: Dp = 230.dp
) {
    val shimmer = rememberShimmerBrush()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = Color(0x1FFFFFFF),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("stream_card_skeleton"),
        colors = CardDefaults.cardColors(containerColor = AyerSurfaceElevated),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shimmer)
        ) {
            // 1. Top Badges Skeleton Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left skeleton badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Skeleton Live Pill
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x33FFFFFF))
                    )

                    // Skeleton Duration Pill
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x22FFFFFF))
                    )
                }

                // Skeleton Viewers Pill
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x33FFFFFF))
                )
            }

            // 2. Central Video Buffer Wave Shimmer
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
            )

            // 3. Bottom Information Skeleton
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Agency Badge Placeholder
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x3300E5FF))
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Title Placeholder Line 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x4DFFFFFF))
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Title Placeholder Line 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x26FFFFFF))
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Host Name & Country Icon Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.50f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33FFD700))
                    )

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0x26FFFFFF))
                    )
                }
            }
        }
    }
}

/**
 * Grid of Skeleton Stream Cards for full list loading states.
 */
@Composable
fun StreamSkeletonGrid(
    modifier: Modifier = Modifier,
    itemCount: Int = 6
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(itemCount) {
            StreamCardSkeleton()
        }
    }
}

/**
 * High-performance Cached Silent Live Stream Video Player Card.
 *
 * Features:
 * 1. Automatic skeleton screen while the video cache is being initialized or fetched.
 * 2. Smooth Crossfade transition to the active silent live video broadcast.
 * 3. High-fidelity cinematic camera motion, stage lighting sweep, and live vocal EQ activity.
 */
@Composable
fun CachedLiveStreamVideoCard(
    stream: LiveStreamRoom,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 230.dp,
    showMuteBadge: Boolean = true,
    showDetails: Boolean = true,
    initCacheDelayMillis: Long = 350L,
    onShareClick: (() -> Unit)? = null
) {
    var isVideoCached by remember(stream.id) { mutableStateOf(false) }

    LaunchedEffect(stream.id) {
        if (initCacheDelayMillis > 0) {
            delay(initCacheDelayMillis)
        }
        isVideoCached = true
    }

    Crossfade(
        targetState = isVideoCached,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "cached_video_transition_${stream.id}"
    ) { cached ->
        if (!cached) {
            StreamCardSkeleton(
                modifier = modifier,
                height = height
            )
        } else {
            LiveStreamVideoCardContent(
                stream = stream,
                onClick = onClick,
                modifier = modifier,
                height = height,
                showMuteBadge = showMuteBadge,
                showDetails = showDetails,
                onShareClick = onShareClick
            )
        }
    }
}

@Composable
private fun LiveStreamVideoCardContent(
    stream: LiveStreamRoom,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 230.dp,
    showMuteBadge: Boolean = true,
    showDetails: Boolean = true,
    onShareClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stream_card_dot_${stream.id}")

    // Pulsating Live Red Dot
    val liveDotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 1.00f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_dot_pulse"
    )

    // Interactive Touch/Hold Animation States (Subtle Scaling & Elevation)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val touchScale by animateFloatAsState(
        targetValue = if (isPressed) 1.035f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "card_touch_scale_${stream.id}"
    )

    val touchElevation by animateDpAsState(
        targetValue = if (isPressed) 14.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "card_touch_elevation_${stream.id}"
    )

    val borderGlowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 0.35f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "card_border_glow_${stream.id}"
    )

    val focusAuraAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.22f else 0.0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "card_focus_aura_${stream.id}"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = touchScale
                scaleY = touchScale
            }
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isPressed) 1.5.dp else 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        AyerCyan.copy(alpha = borderGlowAlpha),
                        Color.White.copy(alpha = if (isPressed) 0.40f else 0.12f),
                        AyerGold.copy(alpha = if (isPressed) 0.50f else 0.0f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("stream_card_${stream.id}"),
        colors = CardDefaults.cardColors(containerColor = AyerSurfaceElevated),
        elevation = CardDefaults.cardElevation(
            defaultElevation = touchElevation,
            pressedElevation = 14.dp,
            focusedElevation = 10.dp,
            hoveredElevation = 8.dp
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ========================================================
            // A. CACHED SILENT LIVE STREAM VIDEO SURFACE
            // ========================================================
            Box(modifier = Modifier.fillMaxSize()) {
                StreamRoomAmbientBackdrop(
                    hostName = stream.hostName,
                    imageResId = stream.imageResId,
                    modifier = Modifier.fillMaxSize()
                )

                // Focus Aura Sheen when touched/held
                if (focusAuraAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        AyerCyan.copy(alpha = focusAuraAlpha),
                                        AyerGold.copy(alpha = focusAuraAlpha * 0.5f),
                                        Color.Transparent
                                    ),
                                    radius = 500f
                                )
                            )
                    )
                }
            }

            // ========================================================
            // B. GRADIENT SCRIM (Readability for Badges & Stream Info)
            // ========================================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.92f)
                            ),
                            startY = 0f,
                            endY = 700f
                        )
                    )
            )

            // ========================================================
            // C. TOP STATUS BAR: LIVE BADGE + DURATION + SILENT ICON + VIEWERS
            // ========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Badges: LIVE (Pulsing Red) + Live Duration Timer + Silent Mute Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = Color(0xFFD50000),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = liveDotAlpha))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // Live Stream Duration / Timestamp Indicator
                    StreamLiveDurationBadge(
                        startedAt = stream.startedAt,
                        fontSize = 8.sp,
                        iconSize = 9.dp
                    )

                    if (showMuteBadge) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.60f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeOff,
                                    contentDescription = "صامت",
                                    tint = AyerGold,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "صامت",
                                    color = AyerGold,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Right Badge: Authoritative Viewers Counter
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedViewerCountBadge(
                            baseCount = stream.viewersCount,
                            fontSize = 9.sp,
                            iconSize = 11.dp,
                            enableLiveFluctuation = false,
                            showDeltaPopup = false
                        )
                    }
                }
            }

            // ========================================================
            // D. BOTTOM DETAILS: TITLE, HOST NAME & AGENCY BADGE
            // ========================================================
            if (showDetails) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    // Agency or Preview Badge
                    if (stream.isAgencyStream) {
                        Surface(
                            color = AyerPrimary.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 3.dp)
                        ) {
                            Text(
                                text = stream.previewBadge.ifBlank { "💎 بث معتمد" },
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Text(
                        text = stream.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = "🎤 ${stream.hostName}",
                                fontSize = 10.sp,
                                color = AyerGold,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = stream.hostCountryCode,
                                fontSize = 11.sp
                            )
                        }

                        if (onShareClick != null) {
                            Surface(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { onShareClick() }
                                    .testTag("stream_card_share_button_${stream.id}"),
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = CircleShape
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "مشاركة البث",
                                        tint = AyerCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating Miniature Background Live Stream Video Card.
 * Perfect for deep space orbital rings or background carousel behind the 3D globe.
 */
@Composable
fun BackgroundMiniLiveStreamCard(
    stream: LiveStreamRoom,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CachedLiveStreamVideoCard(
        stream = stream,
        onClick = onClick,
        modifier = modifier
            .width(135.dp)
            .height(180.dp),
        height = 180.dp,
        showMuteBadge = true,
        showDetails = true
    )
}
