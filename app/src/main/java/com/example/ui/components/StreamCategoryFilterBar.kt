package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveStreamRoom
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSurfaceElevated

/**
 * Model representing a Live Stream Category for the discovery filter bar.
 */
data class StreamCategory(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val icon: ImageVector,
    val emoji: String,
    val themeColor: Color,
    val badge: String? = null
)

/**
 * Preset of standard discovery categories (Gaming, Music, Just Chatting, etc.)
 */
val StandardStreamCategories = listOf(
    StreamCategory(
        id = "ALL",
        titleAr = "الكل",
        titleEn = "All",
        icon = Icons.Default.Stream,
        emoji = "🔴",
        themeColor = AyerPrimary,
        badge = "LIVE"
    ),
    StreamCategory(
        id = "Gaming",
        titleAr = "ألعاب وجيمنج",
        titleEn = "Gaming",
        icon = Icons.Default.Gamepad,
        emoji = "🎮",
        themeColor = Color(0xFF7C4DFF),
        badge = "HOT 🔥"
    ),
    StreamCategory(
        id = "Music",
        titleAr = "موسيقى وغناء",
        titleEn = "Music",
        icon = Icons.Default.Audiotrack,
        emoji = "🎵",
        themeColor = AyerCyan,
        badge = null
    ),
    StreamCategory(
        id = "Just Chatting",
        titleAr = "تعارف وسوالف",
        titleEn = "Just Chatting",
        icon = Icons.Default.Chat,
        emoji = "💬",
        themeColor = Color(0xFFFF4081),
        badge = null
    ),
    StreamCategory(
        id = "Challenges & PK",
        titleAr = "تحديات وباتل PK",
        titleEn = "Challenges & PK",
        icon = Icons.Default.EmojiEvents,
        emoji = "⚔️",
        themeColor = AyerGold,
        badge = "PK 🏆"
    ),
    StreamCategory(
        id = "Lifestyle",
        titleAr = "حياة يومية ومواهب",
        titleEn = "Lifestyle",
        icon = Icons.Default.Whatshot,
        emoji = "🌟",
        themeColor = Color(0xFF00E676),
        badge = null
    )
)

/**
 * Modern M3 Horizontal Category Filter Bar for live stream discovery.
 */
@Composable
fun StreamCategoryFilterBar(
    selectedCategoryId: String,
    onCategorySelected: (StreamCategory) -> Unit,
    allStreams: List<LiveStreamRoom>,
    modifier: Modifier = Modifier,
    categories: List<StreamCategory> = StandardStreamCategories
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stream_category_filter_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
    ) {
        items(categories, key = { it.id }) { category ->
            val isSelected = selectedCategoryId == category.id || (category.id == "ALL" && (selectedCategoryId == "ALL" || selectedCategoryId == "الكل"))

            // Compute active stream count for this specific category
            val streamCount = remember(allStreams, category.id) {
                if (category.id == "ALL" || category.id == "الكل") {
                    allStreams.size
                } else {
                    allStreams.count {
                        it.category.equals(category.id, ignoreCase = true) ||
                        it.category.equals(category.titleAr, ignoreCase = true) ||
                        it.category.equals(category.titleEn, ignoreCase = true)
                    }
                }
            }

            CategoryFilterChip(
                category = category,
                isSelected = isSelected,
                streamCount = streamCount,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

/**
 * Individual Category Filter Chip with glowing active states and count pill.
 */
@Composable
private fun CategoryFilterChip(
    category: StreamCategory,
    isSelected: Boolean,
    streamCount: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "category_chip_scale_${category.id}"
    )

    val elevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        animationSpec = tween(200),
        label = "category_chip_elevation_${category.id}"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            category.themeColor.copy(alpha = 0.22f)
        } else {
            Color(0xFF1E293B).copy(alpha = 0.70f)
        },
        animationSpec = tween(200),
        label = "category_chip_container_${category.id}"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            category.themeColor
        } else {
            Color(0xFF334155).copy(alpha = 0.60f)
        },
        animationSpec = tween(200),
        label = "category_chip_border_${category.id}"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF94A3B8),
        animationSpec = tween(200),
        label = "category_chip_text_${category.id}"
    )

    Surface(
        modifier = Modifier
            .scale(scale)
            .shadow(elevation, shape = RoundedCornerShape(22.dp), spotColor = category.themeColor)
            .clip(RoundedCornerShape(22.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("category_chip_${category.id}"),
        color = containerColor,
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Category Emoji / Icon
            Text(
                text = category.emoji,
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 4.dp)
            )

            // Category Title (Ar / En bilingual support)
            Text(
                text = category.titleAr,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Live Stream Count Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) category.themeColor.copy(alpha = 0.50f)
                        else Color(0x33000000)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$streamCount",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Color(0xFF64748B)
                )
            }

            // Optional Category Highlight Badge (e.g. HOT 🔥, PK 🏆)
            category.badge?.let { badgeText ->
                if (isSelected) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = AyerGold.copy(alpha = 0.90f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
