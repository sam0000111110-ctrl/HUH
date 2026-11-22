package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.PartyMode
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSecondary

/**
 * Data model for custom emotes and live stickers
 */
data class LiveEmote(
    val id: String,
    val text: String,
    val label: String,
    val category: EmoteCategory,
    val isAnimatedSticker: Boolean = false,
    val badgeText: String? = null
)

enum class EmoteCategory(val displayName: String, val icon: ImageVector) {
    REACTIONS("تفاعلات سريعة", Icons.Default.Mood),
    HOT_STREAM("حماس البث 🔥", Icons.Default.LocalFireDepartment),
    LOVE_APPRECIATION("حب وتقدير ❤️", Icons.Default.Favorite),
    CELEBRATION("احتفال وهتاف 🎉", Icons.Default.PartyMode),
    VIP_STICKERS("ملصقات VIP 👑", Icons.Default.Stars)
}

object LiveEmoteRepository {
    val allEmotes: List<LiveEmote> = listOf(
        // Quick Reactions
        LiveEmote("r1", "😂", "ضحك هستيري", EmoteCategory.REACTIONS),
        LiveEmote("r2", "😍", "منبهر", EmoteCategory.REACTIONS),
        LiveEmote("r3", "😎", "فخم", EmoteCategory.REACTIONS),
        LiveEmote("r4", "👏", "تصفيق حار", EmoteCategory.REACTIONS),
        LiveEmote("r5", "🤩", "نجم البث", EmoteCategory.REACTIONS),
        LiveEmote("r6", "🥳", "احتفال", EmoteCategory.REACTIONS),
        LiveEmote("r7", "🥰", "محبة", EmoteCategory.REACTIONS),
        LiveEmote("r8", "🤯", "صدمة جنونية", EmoteCategory.REACTIONS),
        LiveEmote("r9", "😭", "دموع الفرح", EmoteCategory.REACTIONS),
        LiveEmote("r10", "🙌", "تحية خاصة", EmoteCategory.REACTIONS),

        // Hot Stream / Hype
        LiveEmote("h1", "🔥🔥🔥", "ولّع البث!", EmoteCategory.HOT_STREAM, isAnimatedSticker = true, badgeText = "HYPE"),
        LiveEmote("h2", "⚡⚡⚡", "طاقة خارقة", EmoteCategory.HOT_STREAM),
        LiveEmote("h3", "🚀🚀🚀", "إلى القمة!", EmoteCategory.HOT_STREAM, isAnimatedSticker = true),
        LiveEmote("h4", "💥💥💥", "قنبلة اللايف", EmoteCategory.HOT_STREAM),
        LiveEmote("h5", "💯💯💯", "عظمة 100%", EmoteCategory.HOT_STREAM),
        LiveEmote("h6", "🏆🥇", "المركز الأول", EmoteCategory.HOT_STREAM),
        LiveEmote("h7", "🦁🔥", "أسد التوب", EmoteCategory.HOT_STREAM, isAnimatedSticker = true),
        LiveEmote("h8", "🌟✨", "إشراق مبهر", EmoteCategory.HOT_STREAM),

        // Love & Appreciation
        LiveEmote("l1", "💖💖💖", "قلوب Ayer", EmoteCategory.LOVE_APPRECIATION),
        LiveEmote("l2", "🌹🌹🌹", "ورود للمضيف", EmoteCategory.LOVE_APPRECIATION),
        LiveEmote("l3", "✨💎✨", "ألماسة نادرة", EmoteCategory.LOVE_APPRECIATION, isAnimatedSticker = true),
        LiveEmote("l4", "🫶❤️", "أحبك يا كينج", EmoteCategory.LOVE_APPRECIATION),
        LiveEmote("l5", "👑✨", "تاج الفخامة", EmoteCategory.LOVE_APPRECIATION, isAnimatedSticker = true),
        LiveEmote("l6", "🕊️🤍", "سلام ومحبة", EmoteCategory.LOVE_APPRECIATION),
        LiveEmote("l7", "☕🍩", "روقان البث", EmoteCategory.LOVE_APPRECIATION),

        // Celebration & Cheers
        LiveEmote("c1", "🎉🎊🎉", "مبروك التوب!", EmoteCategory.CELEBRATION, isAnimatedSticker = true),
        LiveEmote("c2", "🍾🥂", "نخب الفوز", EmoteCategory.CELEBRATION),
        LiveEmote("c3", "🎈🎁", "أجواء العيد", EmoteCategory.CELEBRATION),
        LiveEmote("c4", "💃🕺", "رقص وفرحة", EmoteCategory.CELEBRATION),
        LiveEmote("c5", "🎺🥁", "ألحان الفرح", EmoteCategory.CELEBRATION),
        LiveEmote("c6", "🎆🎇", "ألعاب نارية", EmoteCategory.CELEBRATION, isAnimatedSticker = true),

        // VIP Live Stickers
        LiveEmote("v1", "👑 [VIP HOST KING] 👑", "تاج المضيف", EmoteCategory.VIP_STICKERS, isAnimatedSticker = true, badgeText = "VIP"),
        LiveEmote("v2", "💎 [LEGENDARY SUPPORTER] 💎", "داعم أسطوري", EmoteCategory.VIP_STICKERS, isAnimatedSticker = true, badgeText = "VIP"),
        LiveEmote("v3", "🚀 [MEGA ROCKET HYPER] 🚀", "صاروخ أير", EmoteCategory.VIP_STICKERS, isAnimatedSticker = true, badgeText = "SUPER"),
        LiveEmote("v4", "🦁 [LION ROAR 500X] 🦁", "زئير الأسد", EmoteCategory.VIP_STICKERS, isAnimatedSticker = true, badgeText = "TOP 1"),
        LiveEmote("v5", "⚡ [AYER UNSTOPPABLE] ⚡", "بث لا يُقهر", EmoteCategory.VIP_STICKERS, isAnimatedSticker = true, badgeText = "EXCLUSIVE")
    )
}

/**
 * Quick Floating Emoji Bar displayed above the chat input
 */
@Composable
fun QuickEmojiBar(
    onSelectEmoji: (String) -> Unit,
    onTriggerReaction: ((String) -> Unit)? = null,
    onOpenFullPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quickEmojis = remember { listOf("🔥", "❤️", "👏", "😂", "😍", "🎉", "👑", "🚀", "💯") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            quickEmojis.forEach { emoji ->
                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable {
                            onSelectEmoji(emoji)
                            onTriggerReaction?.invoke(emoji)
                        }
                        .testTag("quick_emoji_$emoji"),
                    color = Color.White.copy(alpha = 0.08f),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = emoji, fontSize = 18.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Open full emoji & sticker picker
        Surface(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenFullPicker)
                .testTag("open_full_emoji_picker_btn"),
            color = AyerCyan.copy(alpha = 0.25f),
            shape = CircleShape,
            border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.6f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Mood,
                    contentDescription = "قائمة الإيموجي والملصقات",
                    tint = AyerCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Full Emoji & Emote/Sticker Picker Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStreamEmojiPickerSheet(
    onSendEmote: (LiveEmote) -> Unit,
    onInsertTextEmoji: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember { mutableStateOf(EmoteCategory.REACTIONS) }

    val filteredEmotes = remember(selectedCategory) {
        LiveEmoteRepository.allEmotes.filter { it.category == selectedCategory }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF140F24),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                brush = Brush.radialGradient(listOf(AyerCyan.copy(alpha = 0.3f), Color.Transparent)),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "😎", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "إيموجي وملصقات البث المباشر",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "تفاعل فورياً وعبر عن حماسك في الدردشة",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EmoteCategory.entries.forEach { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (category == EmoteCategory.VIP_STICKERS) AyerGold else AyerCyan,
                            selectedLabelColor = Color.Black,
                            selectedLeadingIconColor = Color.Black,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White,
                            iconColor = Color.LightGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color.White.copy(alpha = 0.15f),
                            selectedBorderColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Grid of Emotes / Stickers
            LazyVerticalGrid(
                columns = if (selectedCategory == EmoteCategory.VIP_STICKERS) GridCells.Fixed(1) else GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredEmotes, key = { it.id }) { emote ->
                    if (selectedCategory == EmoteCategory.VIP_STICKERS) {
                        // VIP Sticker Wide Banner Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    onSendEmote(emote)
                                    onDismiss()
                                }
                                .testTag("vip_sticker_${emote.id}"),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                            border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "👑", fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = emote.text,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AyerGold
                                        )
                                        Text(
                                            text = emote.label,
                                            fontSize = 10.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                }

                                Surface(
                                    color = AyerGold,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = emote.badgeText ?: "VIP",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Standard Grid Emote Card
                        Card(
                            modifier = Modifier
                                .height(82.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (emote.isAnimatedSticker) {
                                        onSendEmote(emote)
                                        onDismiss()
                                    } else {
                                        onInsertTextEmoji(emote.text)
                                    }
                                }
                                .testTag("emote_item_${emote.id}"),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
                            border = BorderStroke(
                                1.dp,
                                if (emote.isAnimatedSticker) AyerSecondary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = emote.text,
                                    fontSize = if (emote.text.length > 2) 20.sp else 26.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = emote.label,
                                    fontSize = 9.sp,
                                    color = if (emote.isAnimatedSticker) AyerCyan else Color.LightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Quick Hint Bottom Line
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💡 انقر لإدراج الإيموجي أو إرسال الملصق فوراً للبث",
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
                Text(
                    text = "${filteredEmotes.size} عنصر",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AyerCyan
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
