package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppNotification
import com.example.data.model.NotificationType
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerGoldGlow
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSurface
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.AyerSurfaceVariant
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationBellButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .testTag("notification_bell_button")
            .size(44.dp)
            .background(Color(0xFF1B0F2E), CircleShape)
            .border(1.dp, AyerPrimary.copy(alpha = 0.4f), CircleShape)
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(
                        containerColor = AyerPrimary,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("notification_unread_badge")
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        ) {
            Icon(
                imageVector = if (unreadCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                contentDescription = "الإشعارات",
                tint = if (unreadCount > 0) AyerGold else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun InAppNotificationBanner(
    notification: AppNotification?,
    onDismiss: () -> Unit,
    onOpenCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto dismiss after 6 seconds
    LaunchedEffect(notification) {
        if (notification != null) {
            delay(6000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = notification != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("in_app_notification_banner")
    ) {
        if (notification != null) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onOpenCenter()
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF2C0B40),
                                    Color(0xFF4A0A32),
                                    Color(0xFF1C0A33)
                                )
                            )
                        )
                        .border(
                            1.5.dp,
                            Brush.horizontalGradient(listOf(AyerGold, AyerPrimary, AyerCyan)),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon Avatar
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    when (notification.type) {
                                        NotificationType.NEW_REFERRAL -> AyerCyan.copy(alpha = 0.2f)
                                        NotificationType.COMMISSION_PAYOUT -> AyerGold.copy(alpha = 0.2f)
                                        NotificationType.WITHDRAWAL_PAID -> Color(0xFF00E676).copy(alpha = 0.2f)
                                        NotificationType.SYSTEM_ALERT -> AyerPrimary.copy(alpha = 0.2f)
                                    },
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    when (notification.type) {
                                        NotificationType.NEW_REFERRAL -> AyerCyan
                                        NotificationType.COMMISSION_PAYOUT -> AyerGold
                                        NotificationType.WITHDRAWAL_PAID -> Color(0xFF00E676)
                                        NotificationType.SYSTEM_ALERT -> AyerPrimary
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (notification.type) {
                                    NotificationType.NEW_REFERRAL -> Icons.Default.People
                                    NotificationType.COMMISSION_PAYOUT -> Icons.Default.MonetizationOn
                                    NotificationType.WITHDRAWAL_PAID -> Icons.Default.AccountBalance
                                    NotificationType.SYSTEM_ALERT -> Icons.Default.Notifications
                                },
                                contentDescription = null,
                                tint = when (notification.type) {
                                    NotificationType.NEW_REFERRAL -> AyerCyan
                                    NotificationType.COMMISSION_PAYOUT -> AyerGold
                                    NotificationType.WITHDRAWAL_PAID -> Color(0xFF00E676)
                                    NotificationType.SYSTEM_ALERT -> AyerPrimary
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = notification.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (notification.amountCoins != null && notification.amountCoins > 0) {
                                    Surface(
                                        color = AyerGold.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AyerGold)
                                    ) {
                                        Text(
                                            text = "+${notification.amountCoins} 💎",
                                            color = AyerGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = notification.message,
                                fontSize = 12.sp,
                                color = Color(0xFFD4CAEB),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCenterDialog(
    notifications: List<AppNotification>,
    unreadCount: Int,
    onDismiss: () -> Unit,
    onMarkAsRead: (Long) -> Unit,
    onMarkAllAsRead: () -> Unit,
    onClearAll: () -> Unit,
    onSimulateReferral: () -> Unit,
    onSimulateCommissionPayout: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, COMMISSION, REFERRAL, WITHDRAWAL

    val filteredNotifications = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            "COMMISSION" -> notifications.filter { it.type == NotificationType.COMMISSION_PAYOUT }
            "REFERRAL" -> notifications.filter { it.type == NotificationType.NEW_REFERRAL }
            "WITHDRAWAL" -> notifications.filter { it.type == NotificationType.WITHDRAWAL_PAID }
            else -> notifications
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(26.dp))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(AyerPrimary, AyerGold.copy(alpha = 0.5f))),
                    RoundedCornerShape(26.dp)
                )
                .testTag("notification_center_dialog"),
            color = AyerSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
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
                                .size(40.dp)
                                .background(
                                    Brush.radialGradient(listOf(AyerGold, AyerGoldGlow)),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "تنبيهات المؤثرين والوكلاء",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = if (unreadCount > 0) "$unreadCount إشعار غير مقروء" else "جميع الإشعارات مقروءة",
                                fontSize = 12.sp,
                                color = if (unreadCount > 0) AyerGold else AyerCyan
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .background(AyerSurfaceElevated, CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Simulator Strip (Test referral & commission alerts)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF140B24)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AyerPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = AyerGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "محاكاة الإشعارات المباشرة للمؤثرين:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onSimulateReferral,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("test_referral_notification_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AyerPrimary)
                            ) {
                                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إحالة جديدة (+250💎)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onSimulateCommissionPayout,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("test_commission_payout_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AyerGold)
                            ) {
                                Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("دفعة عمولة (+750💎)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NotificationFilterChip(
                        selected = selectedFilter == "ALL",
                        label = "الكل (${notifications.size})",
                        onClick = { selectedFilter = "ALL" }
                    )
                    NotificationFilterChip(
                        selected = selectedFilter == "COMMISSION",
                        label = "العمولات 💰",
                        onClick = { selectedFilter = "COMMISSION" }
                    )
                    NotificationFilterChip(
                        selected = selectedFilter == "REFERRAL",
                        label = "الإحالات 👥",
                        onClick = { selectedFilter = "REFERRAL" }
                    )
                    NotificationFilterChip(
                        selected = selectedFilter == "WITHDRAWAL",
                        label = "السحوبات 🏦",
                        onClick = { selectedFilter = "WITHDRAWAL" }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Actions: Mark all read & Clear all
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (unreadCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable(onClick = onMarkAllAsRead)
                                .padding(vertical = 4.dp)
                                .testTag("mark_all_read_button")
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, tint = AyerCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تعيين الكل كمقروء", fontSize = 11.sp, color = AyerCyan, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (notifications.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable(onClick = onClearAll)
                                .padding(vertical = 4.dp)
                                .testTag("clear_all_notifications_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مسح الكل", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Notifications List
                if (filteredNotifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = Color(0xFF43345A),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "لا توجد إشعارات في هذا القسم حالياً",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ستتلقى تنبيهاً فورياً عند انضمام إحالة جديدة أو نزول عمولة في رصيدك",
                                fontSize = 11.sp,
                                color = Color(0xFF7A6B92),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredNotifications, key = { it.id }) { item ->
                            NotificationItemCard(
                                notification = item,
                                onClick = { onMarkAsRead(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AyerPrimary,
            selectedLabelColor = Color.White,
            containerColor = AyerSurfaceElevated,
            labelColor = Color.LightGray
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (selected) AyerPrimary else Color(0xFF2A1C44)
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
fun NotificationItemCard(
    notification: AppNotification,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("hh:mm a - yyyy/MM/dd", Locale.getDefault()) }
    val formattedTime = remember(notification.timestamp) { dateFormat.format(Date(notification.timestamp)) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.isRead) Color(0xFF1B0C2D) else AyerSurfaceElevated
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (!notification.isRead) 1.5.dp else 1.dp,
            color = if (!notification.isRead) AyerGold.copy(alpha = 0.6f) else Color(0xFF2B1842)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("notification_item_${notification.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Type Icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        when (notification.type) {
                            NotificationType.NEW_REFERRAL -> AyerCyan.copy(alpha = 0.15f)
                            NotificationType.COMMISSION_PAYOUT -> AyerGold.copy(alpha = 0.15f)
                            NotificationType.WITHDRAWAL_PAID -> Color(0xFF00E676).copy(alpha = 0.15f)
                            NotificationType.SYSTEM_ALERT -> AyerPrimary.copy(alpha = 0.15f)
                        },
                        CircleShape
                    )
                    .border(
                        1.dp,
                        when (notification.type) {
                            NotificationType.NEW_REFERRAL -> AyerCyan
                            NotificationType.COMMISSION_PAYOUT -> AyerGold
                            NotificationType.WITHDRAWAL_PAID -> Color(0xFF00E676)
                            NotificationType.SYSTEM_ALERT -> AyerPrimary
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (notification.type) {
                        NotificationType.NEW_REFERRAL -> Icons.Default.People
                        NotificationType.COMMISSION_PAYOUT -> Icons.Default.MonetizationOn
                        NotificationType.WITHDRAWAL_PAID -> Icons.Default.AccountBalance
                        NotificationType.SYSTEM_ALERT -> Icons.Default.Notifications
                    },
                    contentDescription = null,
                    tint = when (notification.type) {
                        NotificationType.NEW_REFERRAL -> AyerCyan
                        NotificationType.COMMISSION_PAYOUT -> AyerGold
                        NotificationType.WITHDRAWAL_PAID -> Color(0xFF00E676)
                        NotificationType.SYSTEM_ALERT -> AyerPrimary
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = notification.title,
                            fontSize = 13.sp,
                            fontWeight = if (!notification.isRead) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (!notification.isRead) Color.White else Color.LightGray
                        )
                        if (!notification.isRead) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(AyerGold, CircleShape)
                            )
                        }
                    }

                    if (notification.amountCoins != null && notification.amountCoins > 0) {
                        Surface(
                            color = when (notification.type) {
                                NotificationType.COMMISSION_PAYOUT -> AyerGold.copy(alpha = 0.2f)
                                NotificationType.NEW_REFERRAL -> AyerCyan.copy(alpha = 0.2f)
                                else -> Color(0xFF00E676).copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (notification.type) {
                                    NotificationType.COMMISSION_PAYOUT -> AyerGold
                                    NotificationType.NEW_REFERRAL -> AyerCyan
                                    else -> Color(0xFF00E676)
                                }
                            )
                        ) {
                            Text(
                                text = "+${notification.amountCoins} 💎",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (notification.type) {
                                    NotificationType.COMMISSION_PAYOUT -> AyerGold
                                    NotificationType.NEW_REFERRAL -> AyerCyan
                                    else -> Color(0xFF00E676)
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.message,
                    fontSize = 12.sp,
                    color = if (!notification.isRead) Color(0xFFE8DEF8) else Color(0xFFB0A4C8),
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    if (notification.senderName != null) {
                        Text(
                            text = "المصدر: ${notification.senderName}",
                            fontSize = 10.sp,
                            color = AyerCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
