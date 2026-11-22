package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Format timestamp duration elapsed into human readable format (e.g., "14:20" or "01:25:30").
 */
fun formatStreamDuration(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * Real-time Stream Broadcast Duration Indicator Badge.
 * Automatically ticks every second to show how long the live broadcast has been running.
 */
@Composable
fun StreamLiveDurationBadge(
    startedAt: Long,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 8.sp,
    iconSize: Dp = 10.dp,
    containerColor: Color = Color.Black.copy(alpha = 0.65f),
    contentColor: Color = Color(0xFFE2E8F0)
) {
    var elapsedMillis by remember(startedAt) {
        mutableLongStateOf(System.currentTimeMillis() - startedAt)
    }

    LaunchedEffect(startedAt) {
        while (true) {
            elapsedMillis = System.currentTimeMillis() - startedAt
            delay(1000L)
        }
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "مدة البث",
                tint = AyerCyan,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = formatStreamDuration(elapsedMillis),
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                letterSpacing = 0.3.sp
            )
        }
    }
}

/**
 * Format count with comma or k-format if over 10,000.
 */
fun formatViewerCount(count: Int): String {
    return when {
        count >= 100_000 -> String.format("%.1fk", count / 1000.0)
        count >= 10_000 -> String.format("%.1fk", count / 1000.0)
        else -> String.format("%,d", count)
    }
}

/**
 * CountUpText Component:
 * Uses a LaunchedEffect and Animatable to smoothly interpolate and count up/down to the target value
 * whenever the targetCount changes, giving an organic live increment/decrement feeling.
 */
@Composable
fun CountUpText(
    targetCount: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 9.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    color: Color = Color.White,
    durationMillis: Int = 600,
    formatAsViewerString: Boolean = true
) {
    val countAnim = remember { Animatable(targetCount.toFloat()) }

    LaunchedEffect(targetCount) {
        countAnim.animateTo(
            targetValue = targetCount.toFloat(),
            animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        )
    }

    val displayedValue = countAnim.value.roundToInt()
    val formattedText = if (formatAsViewerString) {
        formatViewerCount(displayedValue)
    } else {
        String.format("%,d", displayedValue)
    }

    Text(
        text = formattedText,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

/**
 * Smooth Rolling Number Display: Animates individual digits vertically like an odometer
 * when the count changes incrementally.
 */
@Composable
fun RollingNumberText(
    count: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 9.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    color: Color = Color.White
) {
    val formattedString = formatViewerCount(count)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        formattedString.forEachIndexed { index, char ->
            if (char.isDigit()) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { height -> height } + fadeIn())
                                .togetherWith(slideOutVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { height -> -height } + fadeOut())
                        } else {
                            (slideInVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { height -> -height } + fadeIn())
                                .togetherWith(slideOutVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { height -> height } + fadeOut())
                        }
                    },
                    label = "digit_roll_${index}_$char"
                ) { targetDigit ->
                    Text(
                        text = targetDigit.toString(),
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = color
                    )
                }
            } else {
                Text(
                    text = char.toString(),
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    color = color
                )
            }
        }
    }
}

/**
 * Complete Real-Time Animated Viewer Badge with simulated organic engagement fluctuations,
 * smooth rolling numbers, and engagement delta notifications (+N badge on join).
 */
@Composable
fun AnimatedViewerCountBadge(
    baseCount: Int,
    modifier: Modifier = Modifier,
    enableLiveFluctuation: Boolean = true,
    fontSize: TextUnit = 9.sp,
    iconSize: Dp = 11.dp,
    showDeltaPopup: Boolean = true,
    useCountUpText: Boolean = true
) {
    var currentCount by remember(baseCount) { mutableIntStateOf(baseCount) }
    var lastDelta by remember { mutableIntStateOf(0) }
    var showDeltaAnimation by remember { mutableStateOf(false) }

    // Organic real-time incremental viewer simulation
    LaunchedEffect(baseCount, enableLiveFluctuation) {
        if (!enableLiveFluctuation || baseCount <= 0) return@LaunchedEffect

        while (true) {
            // Wait random interval between 2.2s and 5.5s
            val nextDelay = Random.nextLong(2200L, 5500L)
            delay(nextDelay)

            // Organic change: 75% chance of gain (+1 to +5), 25% chance of slight dip (-1 or -2)
            val roll = Random.nextFloat()
            val delta = when {
                roll < 0.40f -> Random.nextInt(1, 3)    // +1 or +2
                roll < 0.70f -> Random.nextInt(2, 5)    // +2 to +4
                roll < 0.85f -> 1                       // +1
                else -> -Random.nextInt(1, 2)           // -1
            }

            val newCount = (currentCount + delta).coerceAtLeast(1)
            if (newCount != currentCount) {
                lastDelta = newCount - currentCount
                currentCount = newCount
                if (showDeltaPopup && lastDelta > 0) {
                    showDeltaAnimation = true
                    delay(1200L)
                    showDeltaAnimation = false
                }
            }
        }
    }

    val badgeScale by animateFloatAsState(
        targetValue = if (showDeltaAnimation) 1.08f else 1.0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "viewer_badge_scale"
    )

    Box(
        modifier = modifier.scale(badgeScale),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "عدد المشاهدين",
                tint = if (showDeltaAnimation) AyerCyan else Color.White,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(3.dp))
            if (useCountUpText) {
                CountUpText(
                    targetCount = currentCount,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = if (showDeltaAnimation) AyerCyan else Color.White,
                    durationMillis = 500
                )
            } else {
                RollingNumberText(
                    count = currentCount,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = if (showDeltaAnimation) AyerCyan else Color.White
                )
            }
        }

        // Floating Real-Time Engagement Indicator (+N)
        if (showDeltaPopup) {
            AnimatedVisibility(
                visible = showDeltaAnimation && lastDelta > 0,
                enter = fadeIn(tween(150)) + slideInVertically(tween(250)) { -it },
                exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it * 2 },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-12).dp)
            ) {
                Surface(
                    color = Color(0xFF00E676),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "+$lastDelta",
                        color = Color.Black,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
