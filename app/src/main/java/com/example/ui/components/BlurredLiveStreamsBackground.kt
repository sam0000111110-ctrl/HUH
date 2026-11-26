package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.LiveStreamRoom
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Authentic Moving Live Streams Background for Login and Registration Screens.
 * Features dual continuous horizontal carousels smoothly scrolling from Right to Left,
 * displaying real, larger [CachedLiveStreamVideoCard] components with authentic camera micro-motion,
 * audio equalizer feedback, and live broadcast badges.
 */
@Composable
fun BlurredLiveStreamsBackground(
    popularStreams: List<LiveStreamRoom>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "login_streams_ambient_lights")

    // Ambient floating glow accents
    val floatAnim1 by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_drift_1"
    )

    val floatAnim2 by infiniteTransition.animateFloat(
        initialValue = 16f,
        targetValue = -16f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_drift_2"
    )

    val baseStreams = popularStreams

    val topRowStreams = remember(baseStreams) {
        if (baseStreams.isEmpty()) emptyList()
        else List(20) { index -> baseStreams[index % baseStreams.size] }
    }

    val bottomRowStreams = remember(baseStreams) {
        if (baseStreams.isEmpty()) emptyList()
        else {
            val rev = baseStreams.reversed()
            List(20) { index -> rev[index % rev.size] }
        }
    }

    val topListState = rememberLazyListState()
    val bottomListState = rememberLazyListState()

    LaunchedEffect(topRowStreams.isNotEmpty()) {
        if (topRowStreams.isEmpty()) return@LaunchedEffect
        while (isActive) {
            try {
                topListState.scrollBy(1.5f)
            } catch (_: Exception) { }
            delay(16)
        }
    }

    LaunchedEffect(bottomRowStreams.isNotEmpty()) {
        if (bottomRowStreams.isEmpty()) return@LaunchedEffect
        while (isActive) {
            try {
                bottomListState.scrollBy(1.9f)
            } catch (_: Exception) { }
            delay(16)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060913))
    ) {
        if (baseStreams.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                LazyRow(
                    state = topListState,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    userScrollEnabled = false,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(topRowStreams) { stream ->
                        CachedLiveStreamVideoCard(
                            stream = stream,
                            onClick = { },
                            modifier = Modifier
                                .width(235.dp)
                                .height(325.dp),
                            height = 325.dp,
                            showMuteBadge = true,
                            showDetails = true,
                            initCacheDelayMillis = 100L
                        )
                    }
                }

                LazyRow(
                    state = bottomListState,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    userScrollEnabled = false,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(bottomRowStreams) { stream ->
                        CachedLiveStreamVideoCard(
                            stream = stream,
                            onClick = { },
                            modifier = Modifier
                                .width(235.dp)
                                .height(325.dp),
                            height = 325.dp,
                            showMuteBadge = true,
                            showDetails = true,
                            initCacheDelayMillis = 150L
                        )
                    }
                }
            }
        }

        // Ambient Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x38040711), // 22% opacity top
                            Color(0x40070C1B), // 25% opacity middle
                            Color(0x4D030612)  // 30% opacity bottom
                        )
                    )
                )
        )

        // 3. Subtle Ambient Neon Glows
        Box(
            modifier = Modifier
                .size(360.dp)
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    translationX = 80f + floatAnim1
                    translationY = -50f + floatAnim2
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AyerPrimary.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .size(380.dp)
                .align(Alignment.BottomStart)
                .graphicsLayer {
                    translationX = -80f + floatAnim2
                    translationY = 70f + floatAnim1
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AyerGold.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
