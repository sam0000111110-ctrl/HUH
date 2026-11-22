package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FriendItem
import com.example.data.model.FriendshipStatus
import com.example.ui.AyerViewModel
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSecondary
import com.example.ui.theme.HuhTheme
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: AyerViewModel,
    friends: List<FriendItem>,
    onNavigateToChat: (FriendItem) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Friends, 1: Requests

    val acceptedFriends = friends.filter { it.status == FriendshipStatus.ACCEPTED }
    val incomingRequests = friends.filter { it.status == FriendshipStatus.PENDING_INCOMING }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "قائمة الأصدقاء والطلبات",
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = HuhTheme.colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HuhTheme.colors.surface
                )
            )
        },
        containerColor = HuhTheme.colors.background
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = HuhTheme.colors.surfaceVariant,
                contentColor = HuhTheme.colors.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = HuhTheme.colors.primary,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "الأصدقاء (${acceptedFriends.size})",
                            color = if (selectedTab == 0) HuhTheme.colors.primary else HuhTheme.colors.textSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "الطلبات (${incomingRequests.size})",
                            color = if (selectedTab == 1) HuhTheme.colors.primary else HuhTheme.colors.textSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                if (acceptedFriends.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ليس لديك أصدقاء مضافون حالياً.\nابحث وتعرف على أصدقاء جدد من خلال المطابقة!",
                            color = HuhTheme.colors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(acceptedFriends) { friend ->
                            FriendCard(
                                friend = friend,
                                onClickChat = { onNavigateToChat(friend) },
                                onUnfriend = { viewModel.unfriend(friend.id) }
                            )
                        }
                    }
                }
            } else {
                if (incomingRequests.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد طلبات صداقة معلقة في الوقت الحالي.",
                            color = HuhTheme.colors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(incomingRequests) { request ->
                            FriendRequestCard(
                                request = request,
                                onAccept = { viewModel.acceptFriendRequest(request.id) },
                                onReject = { viewModel.rejectFriendRequest(request.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FriendCard(
    friend: FriendItem,
    onClickChat: () -> Unit,
    onUnfriend: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surface),
        border = BorderStroke(1.dp, HuhTheme.colors.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(HuhTheme.colors.primary, HuhTheme.colors.secondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = friend.name.take(1),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = friend.name,
                        color = HuhTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${friend.country}",
                        color = HuhTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = friend.lastMessage,
                    color = HuhTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onClickChat,
                modifier = Modifier
                    .size(40.dp)
                    .background(HuhTheme.colors.tertiary.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Message,
                    contentDescription = "فتح المحادثة",
                    tint = HuhTheme.colors.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun FriendRequestCard(
    request: FriendItem,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surface),
        border = BorderStroke(1.dp, HuhTheme.colors.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(HuhTheme.colors.gold.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = request.name.take(1),
                    color = HuhTheme.colors.gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = request.name,
                    color = HuhTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "يرغب في صداقتك من ${request.country}",
                    color = HuhTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onAccept,
                    modifier = Modifier
                        .size(36.dp)
                        .background(HuhTheme.colors.success.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "قبول",
                        tint = HuhTheme.colors.success,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onReject,
                    modifier = Modifier
                        .size(36.dp)
                        .background(HuhTheme.colors.error.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "رفض",
                        tint = HuhTheme.colors.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
