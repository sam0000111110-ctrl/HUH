package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CreatorLeaderboardItem
import com.example.data.model.LeaderboardScope
import com.example.data.model.LeaderboardTimeframe
import com.example.ui.AyerViewModel
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerGoldGlow
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSurface
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.HuhTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LeaderboardComponent(
    viewModel: AyerViewModel,
    modifier: Modifier = Modifier
) {
    var selectedScopeIndex by remember { mutableIntStateOf(0) } // 0: Global, 1: Agency
    var selectedTimeframe by remember { mutableStateOf(LeaderboardTimeframe.THIS_WEEK) }
    var isLeaderboardLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val scope = if (selectedScopeIndex == 0) LeaderboardScope.GLOBAL else LeaderboardScope.AGENCY
    val leaderboardList = remember(scope, selectedTimeframe) {
        viewModel.getCreatorsLeaderboard(scope, selectedTimeframe)
    }

    val topThree = leaderboardList.take(3)
    val remaining = leaderboardList.drop(3)
    val currentUserItem = leaderboardList.find { it.isCurrentUser }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("leaderboard_component"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = AyerSurfaceElevated),
        border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                Brush.radialGradient(listOf(AyerGold, AyerGoldGlow)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "لوحة المتصدرين وصُنّاع المحتوى 🏆",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = "ترتيب المضيفين بحسب إجمالي النقاط المكتسبة",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textSecondary
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (!isLeaderboardLoading) {
                            coroutineScope.launch {
                                isLeaderboardLoading = true
                                delay(650)
                                isLeaderboardLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(HuhTheme.colors.surface, CircleShape)
                        .testTag("refresh_leaderboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث الترتيب",
                        tint = AyerGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isLeaderboardLoading) {
                LeaderboardSkeletonLoader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("leaderboard_shimmer_skeleton")
                )
            } else {
                // Scope Selector Tabs: Global vs Agency
            TabRow(
                selectedTabIndex = selectedScopeIndex,
                containerColor = HuhTheme.colors.surface,
                contentColor = HuhTheme.colors.textPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedScopeIndex]),
                        color = AyerGold
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedScopeIndex == 0,
                    onClick = { selectedScopeIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedScopeIndex == 0) AyerGold else HuhTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "الترتيب العالمي 🌍",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedScopeIndex == 0) AyerGold else HuhTheme.colors.textSecondary
                            )
                        }
                    },
                    modifier = Modifier.testTag("tab_leaderboard_global")
                )
                Tab(
                    selected = selectedScopeIndex == 1,
                    onClick = { selectedScopeIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedScopeIndex == 1) AyerGold else HuhTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ترتيب الوكالة الماسية 🏢",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedScopeIndex == 1) AyerGold else HuhTheme.colors.textSecondary
                            )
                        }
                    },
                    modifier = Modifier.testTag("tab_leaderboard_agency")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Timeframe Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTimeframe == LeaderboardTimeframe.TODAY,
                    onClick = { selectedTimeframe = LeaderboardTimeframe.TODAY },
                    label = { Text("اليوم", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AyerGold,
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.testTag("timeframe_today")
                )
                FilterChip(
                    selected = selectedTimeframe == LeaderboardTimeframe.THIS_WEEK,
                    onClick = { selectedTimeframe = LeaderboardTimeframe.THIS_WEEK },
                    label = { Text("هذا الأسبوع", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AyerGold,
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.testTag("timeframe_week")
                )
                FilterChip(
                    selected = selectedTimeframe == LeaderboardTimeframe.ALL_TIME,
                    onClick = { selectedTimeframe = LeaderboardTimeframe.ALL_TIME },
                    label = { Text("كل الأوقات", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AyerGold,
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.testTag("timeframe_all_time")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Top 3 Podium
            if (topThree.isNotEmpty()) {
                TopThreePodium(topThree = topThree)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Remaining Ranked Creators List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                remaining.forEach { creator ->
                    LeaderboardRowItem(creator = creator)
                }
            }

            // Sticky Current User Rank Bar
            if (currentUserItem != null) {
                Spacer(modifier = Modifier.height(14.dp))
                CurrentUserRankCard(currentUser = currentUserItem)
            }
            }
        }
    }
}

@Composable
fun TopThreePodium(topThree: List<CreatorLeaderboardItem>) {
    val first = topThree.getOrNull(0)
    val second = topThree.getOrNull(1)
    val third = topThree.getOrNull(2)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1E1035), Color(0xFF0F071D))
                ),
                RoundedCornerShape(18.dp)
            )
            .border(1.dp, AyerGold.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        // #2 Rank (Silver - Left)
        if (second != null) {
            PodiumItem(
                creator = second,
                rank = 2,
                podiumHeight = 90.dp,
                ringColor = Color(0xFFC0C0C0),
                crownOrMedal = "🥈",
                modifier = Modifier.weight(1f)
            )
        }

        // #1 Rank (Gold - Center)
        if (first != null) {
            PodiumItem(
                creator = first,
                rank = 1,
                podiumHeight = 120.dp,
                ringColor = AyerGold,
                crownOrMedal = "👑",
                modifier = Modifier.weight(1.15f)
            )
        }

        // #3 Rank (Bronze - Right)
        if (third != null) {
            PodiumItem(
                creator = third,
                rank = 3,
                podiumHeight = 75.dp,
                ringColor = Color(0xFFCD7F32),
                crownOrMedal = "🥉",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PodiumItem(
    creator: CreatorLeaderboardItem,
    rank: Int,
    podiumHeight: androidx.compose.ui.unit.Dp,
    ringColor: Color,
    crownOrMedal: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = crownOrMedal,
            fontSize = if (rank == 1) 22.sp else 18.sp
        )
        Spacer(modifier = Modifier.height(2.dp))

        // Avatar with ring
        Box(
            modifier = Modifier
                .size(if (rank == 1) 56.dp else 46.dp)
                .border(2.dp, ringColor, CircleShape)
                .padding(3.dp)
                .background(HuhTheme.colors.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = ringColor,
                modifier = Modifier.size(if (rank == 1) 32.dp else 26.dp)
            )
            if (creator.isLiveNow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .background(Color(0xFFF43F5E), CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = creator.name,
            fontSize = if (rank == 1) 12.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Text(
            text = "${creator.pointsAccumulated} 💎",
            fontSize = if (rank == 1) 12.sp else 10.sp,
            fontWeight = FontWeight.Black,
            color = ringColor
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Podium Pillar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(podiumHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(ringColor.copy(alpha = 0.35f), ringColor.copy(alpha = 0.1f))
                    ),
                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                )
                .border(
                    1.dp,
                    ringColor.copy(alpha = 0.5f),
                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#$rank",
                fontSize = if (rank == 1) 20.sp else 16.sp,
                fontWeight = FontWeight.Black,
                color = ringColor
            )
        }
    }
}

@Composable
fun LeaderboardRowItem(creator: CreatorLeaderboardItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_item_${creator.id}"),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (creator.isCurrentUser) AyerGold.copy(alpha = 0.6f) else HuhTheme.colors.outline.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Rank number
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(HuhTheme.colors.surfaceElevated, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${creator.rank}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Avatar
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(AyerPrimary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = AyerPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    if (creator.isLiveNow) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(10.dp)
                                .background(Color(0xFFF43F5E), CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = creator.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        if (creator.isLiveNow) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = Color(0xFFF43F5E).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "مباشر 🔴",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF43F5E),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = creator.agencyName,
                        fontSize = 10.sp,
                        color = HuhTheme.colors.textSecondary,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Points Value
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${creator.pointsAccumulated} 💎",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = AyerGold
                )
                Text(
                    text = "${creator.followersCount} متابع",
                    fontSize = 9.sp,
                    color = HuhTheme.colors.textTertiary
                )
            }
        }
    }
}

@Composable
fun CurrentUserRankCard(currentUser: CreatorLeaderboardItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("current_user_rank_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1038)),
        border = BorderStroke(1.5.dp, AyerGold)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(AyerGold.copy(alpha = 0.2f), CircleShape)
                        .border(1.dp, AyerGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#${currentUser.rank}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = AyerGold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "موقعك الحالي في الترتيب ⭐",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = AyerCyan.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "أنت",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AyerCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "شارك بالبثوث والتفاعل اليومي لتصعد للمراكز الأولى",
                        fontSize = 10.sp,
                        color = Color.LightGray
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${currentUser.pointsAccumulated} 💎",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = AyerGold
                )
                Text(
                    text = "نقاطك المحسوبة",
                    fontSize = 9.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}
