package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AyerGold
import com.example.ui.theme.HuhTheme

/**
 * High-performance Shimmer Animation Brush Modifier.
 * Translates an angled linear gradient across the component to create a sleek skeleton loading shimmer effect.
 */
fun Modifier.shimmerEffect(
    shape: Shape = RoundedCornerShape(8.dp),
    durationMillis: Int = 1100
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    val isDark = HuhTheme.colors.isDark
    val baseColor = if (isDark) Color(0xFF1E1633) else Color(0xFFE2E8F0)
    val highlightColor = if (isDark) Color(0xFF382A5C) else Color(0xFFF8FAFC)

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor
        ),
        start = Offset(translateAnim - 250f, translateAnim - 250f),
        end = Offset(translateAnim, translateAnim)
    )

    this
        .clip(shape)
        .background(shimmerBrush)
}

/**
 * Generic Shimmer Placeholder Box
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    width: Dp? = null,
    height: Dp? = null
) {
    var appliedModifier = modifier
    if (width != null) appliedModifier = appliedModifier.width(width)
    if (height != null) appliedModifier = appliedModifier.height(height)

    Box(
        modifier = appliedModifier.shimmerEffect(shape = shape)
    )
}

/**
 * Shimmer-effect skeleton loader for the Creators & Streamers Leaderboard.
 * Replaces progress spinners with fluid skeleton cards and podium placeholders.
 */
@Composable
fun LeaderboardSkeletonLoader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Filter Chips Shimmer Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            ShimmerBox(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            ShimmerBox(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Timeframe selector Shimmer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) {
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Top 3 Podium Skeleton
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = HuhTheme.colors.surfaceElevated
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Podium Title Bar
                ShimmerBox(width = 160.dp, height = 20.dp, shape = RoundedCornerShape(10.dp))

                Spacer(modifier = Modifier.height(20.dp))

                // 3 Podium Columns (Rank 2, Rank 1, Rank 3)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Rank 2 (Silver)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        ShimmerBox(modifier = Modifier.size(54.dp), shape = CircleShape)
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerBox(width = 60.dp, height = 14.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(65.dp),
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        )
                    }

                    // Rank 1 (Gold - Elevated)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1.1f)
                    ) {
                        ShimmerBox(modifier = Modifier.size(68.dp), shape = CircleShape)
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerBox(width = 75.dp, height = 16.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(95.dp),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                    }

                    // Rank 3 (Bronze)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        ShimmerBox(modifier = Modifier.size(50.dp), shape = CircleShape)
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerBox(width = 55.dp, height = 14.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(48.dp),
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Ranking Rows Skeletons (Ranks 4 - 8)
        repeat(5) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = HuhTheme.colors.surfaceElevated
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rank badge skeleton
                    ShimmerBox(modifier = Modifier.size(28.dp), shape = CircleShape)

                    Spacer(modifier = Modifier.width(12.dp))

                    // Avatar skeleton
                    ShimmerBox(modifier = Modifier.size(44.dp), shape = CircleShape)

                    Spacer(modifier = Modifier.width(12.dp))

                    // Name and info lines
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerBox(
                            modifier = Modifier.fillMaxWidth(0.6f),
                            height = 14.dp,
                            shape = RoundedCornerShape(6.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerBox(
                            modifier = Modifier.fillMaxWidth(0.35f),
                            height = 10.dp,
                            shape = RoundedCornerShape(4.dp)
                        )
                    }

                    // Points/Gifts Pill
                    ShimmerBox(width = 65.dp, height = 24.dp, shape = RoundedCornerShape(12.dp))
                }
            }
        }
    }
}

/**
 * Shimmer-effect skeleton loader for the Financial Transaction History Screen.
 */
@Composable
fun TransactionHistorySkeletonLoader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Summary Card Skeleton
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = HuhTheme.colors.surfaceElevated
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        ShimmerBox(width = 110.dp, height = 14.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerBox(width = 150.dp, height = 28.dp, shape = RoundedCornerShape(8.dp))
                    }
                    ShimmerBox(modifier = Modifier.size(54.dp), shape = CircleShape)
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Stats breakdown cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
                    ShimmerBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Search bar skeleton
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Filter chips skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(4) {
                ShimmerBox(width = 75.dp, height = 32.dp, shape = RoundedCornerShape(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transaction list items skeletons
        repeat(6) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                shape = RoundedCornerShape(16.dp),
                color = HuhTheme.colors.surfaceElevated
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type Icon Box skeleton
                    ShimmerBox(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(12.dp))

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & subtitle
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerBox(
                            modifier = Modifier.fillMaxWidth(0.65f),
                            height = 14.dp,
                            shape = RoundedCornerShape(6.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerBox(
                            modifier = Modifier.fillMaxWidth(0.4f),
                            height = 11.dp,
                            shape = RoundedCornerShape(4.dp)
                        )
                    }

                    // Coin value indicator
                    ShimmerBox(width = 60.dp, height = 22.dp, shape = RoundedCornerShape(8.dp))
                }
            }
        }
    }
}
