package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.AyerViewModel
import com.example.ui.components.AgeVerifiedBadge
import com.example.ui.components.DailyMissionsCard
import com.example.ui.components.LeaderboardComponent
import com.example.ui.components.LanguageSelectionBottomSheet
import com.example.ui.components.LanguageSelectionSettingRow
import com.example.ui.locale.AppStrings
import com.example.ui.screens.TransactionHistoryScreen
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.HuhTheme

@Composable
fun ProfileScreen(
    viewModel: AyerViewModel,
    userProfile: UserProfile,
    onOpenNotificationCenter: () -> Unit = {},
    onOpenCreateSecondaryProfileDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var autoTranslate by remember { mutableStateOf(true) }
    var highQualityVideo by remember { mutableStateOf(true) }
    val isDarkThemePref by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isDarkTheme = isDarkThemePref ?: androidx.compose.foundation.isSystemInDarkTheme()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    var showLanguageBottomSheet by remember { mutableStateOf(false) }
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val activeSecondaryProfile by viewModel.activeSecondaryProfile.collectAsStateWithLifecycle()
    val engagementState by viewModel.engagementState.collectAsStateWithLifecycle()
    val dailyMissions by viewModel.dailyMissions.collectAsStateWithLifecycle()

    var showTransactionHistoryScreen by remember { mutableStateOf(false) }
    var showLeaderboardDialog by remember { mutableStateOf(false) }

    var showEditAccountDialog by remember { mutableStateOf(false) }
    var editName by remember(userProfile.name) { mutableStateOf(userProfile.name) }
    var editBio by remember(userProfile.bio) { mutableStateOf(userProfile.bio) }
    var editCountry by remember(userProfile.country) { mutableStateOf(userProfile.country) }
    var editPhone by remember(userProfile.phoneNumber) { mutableStateOf(userProfile.phoneNumber) }

    if (showTransactionHistoryScreen) {
        TransactionHistoryScreen(
            viewModel = viewModel,
            onBack = { showTransactionHistoryScreen = false }
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(HuhTheme.colors.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // User Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, HuhTheme.colors.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, HuhTheme.colors.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_ayer_logo),
                            contentDescription = "الصورة الشخصية",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = userProfile.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AgeVerifiedBadge()
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = viewModel.getCurrentUserEmail() ?: "dev@ayer.com",
                        fontSize = 14.sp,
                        color = HuhTheme.colors.gold
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📍 ${userProfile.country} • ${userProfile.age} سنة",
                        fontSize = 12.sp,
                        color = HuhTheme.colors.textSecondary
                    )

                    if (userProfile.bio.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = userProfile.bio,
                            fontSize = 12.sp,
                            color = HuhTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Points & Activity Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${userProfile.pointsBalance}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.gold
                            )
                            Text(text = "الرصيد المتاح", fontSize = 10.sp, color = HuhTheme.colors.textTertiary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${userProfile.totalAccumulatedPoints.coerceAtLeast(userProfile.pointsBalance)}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Text(text = "إجمالي النقاط المكتسبة", fontSize = 10.sp, color = HuhTheme.colors.textTertiary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${userProfile.totalPointsSpent}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF43F5E)
                            )
                            Text(text = "النقاط المستهلكة", fontSize = 10.sp, color = HuhTheme.colors.textTertiary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${userProfile.totalMatchesCount}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.textPrimary
                            )
                            Text(text = "مطابقات تمت", fontSize = 10.sp, color = HuhTheme.colors.textTertiary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Edit Account Profile Action Button
                    OutlinedButton(
                        onClick = { showEditAccountDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HuhTheme.colors.primary.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_edit_account_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = HuhTheme.colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تعديل إعدادات الحساب والبيانات الشخصية",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HuhTheme.colors.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section: Points Management & Daily User Engagement Tracking System
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("points_engagement_management_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF10B981).copy(alpha = 0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "إدارة النقاط والتفاعل اليومي 🎯",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HuhTheme.colors.textPrimary
                                )
                                Text(
                                    text = "اجمع نقاط إضافية يومياً مع كل مطابقة وبث",
                                    fontSize = 11.sp,
                                    color = HuhTheme.colors.textSecondary
                                )
                            }
                        }

                        // Streak badge
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔥 ${engagementState.dailyStreakDays} أيام",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Engagement Progress Bar
                    val progressFraction = (engagementState.todayEngagementPoints / 100f).coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تقدم إنجاز اليوم: ${engagementState.todayEngagementPoints} / 100 نقطة",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = "${(progressFraction * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF10B981),
                        trackColor = HuhTheme.colors.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Engagement Sub-missions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "💬 مطابقات اليوم: ${engagementState.todayMatchesCount}/${engagementState.targetMatchesCount}",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textSecondary
                        )
                        Text(
                            text = "📺 مشاهدة البث: ${engagementState.todayStreamMinutes}/${engagementState.targetStreamMinutes} د",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textSecondary
                        )
                        Text(
                            text = "🎁 هدايا: ${engagementState.todayGiftsSent}/${engagementState.targetGiftsSent}",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Claim Daily Engagement Bonus Button
                    Button(
                        onClick = { viewModel.claimEngagementReward() },
                        enabled = engagementState.todayEngagementPoints >= 100 && !engagementState.dailyMilestoneClaimed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            disabledContainerColor = HuhTheme.colors.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("claim_engagement_reward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = if (engagementState.todayEngagementPoints >= 100 && !engagementState.dailyMilestoneClaimed) Color.White else HuhTheme.colors.textTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (engagementState.dailyMilestoneClaimed) "تم استلام مكافأة التفاعل اليومية ✅"
                            else if (engagementState.todayEngagementPoints >= 100) "استلم مكافأة التفاعل اليومي (+50 نقطة) 🎁"
                            else "أكمل 100 نقطة تفاعل لفتح المكافأة (+50)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (engagementState.todayEngagementPoints >= 100 && !engagementState.dailyMilestoneClaimed) Color.White else HuhTheme.colors.textTertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Actions: Transaction History & Creators Leaderboard
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showTransactionHistoryScreen = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HuhTheme.colors.gold.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_transaction_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = HuhTheme.colors.gold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "سجل المعاملات 📜",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.gold
                            )
                        }

                        OutlinedButton(
                            onClick = { showLeaderboardDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_leaderboard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Leaderboard,
                                contentDescription = null,
                                tint = AyerCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "المتصدرون 🏆",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AyerCyan
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section: Daily Missions Module
        item {
            DailyMissionsCard(
                missions = dailyMissions,
                onClaimReward = { missionId ->
                    viewModel.claimDailyMissionReward(missionId)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section: Agency Membership View (عضوية الوكالة الرسمية)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agency_membership_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                border = BorderStroke(1.dp, HuhTheme.colors.gold.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                        Brush.radialGradient(listOf(HuhTheme.colors.gold, Color(0xFFD97706))),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "عضوية الوكالة الرسمية 🏢",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HuhTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "موثق",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = userProfile.agencyName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HuhTheme.colors.gold
                                )
                            }
                        }

                        Surface(
                            color = HuhTheme.colors.gold.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, HuhTheme.colors.gold.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "معرّف: ${userProfile.agencyId}",
                                fontSize = 10.sp,
                                color = HuhTheme.colors.gold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = HuhTheme.colors.surfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Agency Details Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "رتبة العضوية", fontSize = 10.sp, color = HuhTheme.colors.textTertiary)
                            Text(
                                text = userProfile.agencyRole,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.textPrimary
                            )
                        }
                        Column {
                            Text(text = "نسبة العمولة", fontSize = 10.sp, color = HuhTheme.colors.textTertiary)
                            Text(
                                text = "${userProfile.agentCommissionPercent}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                        Column {
                            Text(text = "تاريخ الانضمام", fontSize = 10.sp, color = HuhTheme.colors.textTertiary)
                            Text(
                                text = userProfile.agencyJoinedDate,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.textPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "المدير المسؤول", fontSize = 10.sp, color = HuhTheme.colors.textTertiary)
                            Text(
                                text = userProfile.agencyManagerName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.textPrimary
                            )
                        }
                        Column {
                            Text(text = "الوكلاء التابعون", fontSize = 10.sp, color = HuhTheme.colors.textTertiary)
                            Text(
                                text = "${userProfile.subAgentsCount} وكلاء",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.textPrimary
                            )
                        }
                        Column {
                            Text(text = "أرباح العمولات المتراكمة", fontSize = 10.sp, color = HuhTheme.colors.textTertiary)
                            Text(
                                text = "${userProfile.subAgentsEarningsCoins} 🪙",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.gold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section: Account Settings Overview (إعدادات الحساب والبيانات)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_settings_overview_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                border = BorderStroke(1.dp, HuhTheme.colors.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(HuhTheme.colors.primary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = HuhTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "إعدادات الحساب والملف ⚙️",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HuhTheme.colors.textPrimary
                                )
                                Text(
                                    text = "إدارة البيانات الشخصية ومعلومات التواصل",
                                    fontSize = 11.sp,
                                    color = HuhTheme.colors.textSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = { showEditAccountDialog = true },
                            modifier = Modifier.testTag("edit_account_settings_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "تعديل",
                                tint = HuhTheme.colors.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = HuhTheme.colors.surfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Account fields list
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "الاسم المسجل:", fontSize = 12.sp, color = HuhTheme.colors.textSecondary)
                        Text(text = userProfile.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HuhTheme.colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "الدولة والمنطقة:", fontSize = 12.sp, color = HuhTheme.colors.textSecondary)
                        Text(text = userProfile.country, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HuhTheme.colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "رقم الهاتف المحمول:", fontSize = 12.sp, color = HuhTheme.colors.textSecondary)
                        Text(
                            text = userProfile.phoneNumber.ifEmpty { "غير محدد" },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "حالة التحقق من العمر:", fontSize = 12.sp, color = HuhTheme.colors.textSecondary)
                        Text(
                            text = if (userProfile.ageVerified) "معتمد (+18) ✅" else "قيد التحقق",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (userProfile.ageVerified) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (userProfile.isAgent) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                    border = BorderStroke(1.dp, HuhTheme.colors.gold)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        Brush.radialGradient(listOf(HuhTheme.colors.gold, HuhTheme.colors.primary)),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "حسابك مرقّى كـ 'وكيل معتمد' 🌟",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HuhTheme.colors.textPrimary
                                )
                                Text(
                                    text = "نسبة عمولتك المعتمدة: ${userProfile.agentCommissionPercent}%",
                                    fontSize = 11.sp,
                                    color = HuhTheme.colors.gold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Section: Security & Screen Capture Protection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, HuhTheme.colors.success.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(HuhTheme.colors.success.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NoPhotography,
                                contentDescription = null,
                                tint = HuhTheme.colors.success,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "حماية الخصوصية ومنع التقاط وتسجيل الشاشة",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.textPrimary
                            )
                            Text(
                                text = "مفعّل عبر FLAG_SECURE على الشاشات الحساسة",
                                fontSize = 11.sp,
                                color = HuhTheme.colors.success
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "تُطبّق حماية منع التقاط وتسجيل الشاشة (FLAG_SECURE) تلقائياً أثناء المكالمات الفردية، الدردشة الخاصة، المحفظة، وبوابة الوكالات لحماية خصوصية الأعضاء.",
                        fontSize = 11.sp,
                        color = HuhTheme.colors.textSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section: Preferences & Appearance
        item {
            Text(
                text = AppStrings.get("settings_title", appLanguage),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = HuhTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, HuhTheme.colors.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Language Selection Row
                    LanguageSelectionSettingRow(
                        currentLanguage = appLanguage,
                        onOpenLanguageDialog = { showLanguageBottomSheet = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = HuhTheme.colors.surfaceVariant)

                    // Theme Switcher Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = HuhTheme.colors.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isDarkTheme) AppStrings.get("dark_mode_title", appLanguage) else AppStrings.get("light_mode_title", appLanguage),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HuhTheme.colors.textPrimary
                                )
                                Text(
                                    text = if (isDarkTheme) AppStrings.get("dark_mode_desc", appLanguage) else AppStrings.get("light_mode_desc", appLanguage),
                                    fontSize = 11.sp,
                                    color = HuhTheme.colors.textSecondary
                                )
                            }
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { viewModel.setDarkMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = HuhTheme.colors.primary,
                                checkedTrackColor = HuhTheme.colors.primary.copy(alpha = 0.3f),
                                uncheckedThumbColor = HuhTheme.colors.textSecondary,
                                uncheckedTrackColor = HuhTheme.colors.surfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = HuhTheme.colors.surfaceVariant)

                    // Auto translate toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = HuhTheme.colors.tertiary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = AppStrings.get("auto_translate_title", appLanguage),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HuhTheme.colors.textPrimary
                                )
                                Text(
                                    text = AppStrings.get("auto_translate_desc", appLanguage),
                                    fontSize = 11.sp,
                                    color = HuhTheme.colors.textSecondary
                                )
                            }
                        }
                        Switch(
                            checked = autoTranslate,
                            onCheckedChange = { autoTranslate = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = HuhTheme.colors.primary,
                                checkedTrackColor = HuhTheme.colors.primary.copy(alpha = 0.3f)
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = HuhTheme.colors.surfaceVariant)

                    // Video quality
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = HuhTheme.colors.gold)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = AppStrings.get("simulcast_title", appLanguage),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HuhTheme.colors.textPrimary
                                )
                                Text(
                                    text = AppStrings.get("simulcast_desc", appLanguage),
                                    fontSize = 11.sp,
                                    color = HuhTheme.colors.textSecondary
                                )
                            }
                        }
                        Switch(
                            checked = highQualityVideo,
                            onCheckedChange = { highQualityVideo = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = HuhTheme.colors.primary,
                                checkedTrackColor = HuhTheme.colors.primary.copy(alpha = 0.3f)
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = HuhTheme.colors.surfaceVariant)

                    // Notification Center row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenNotificationCenter)
                            .testTag("profile_notification_center_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = HuhTheme.colors.gold)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = "مركز إشعارات العمولات والإحالات", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HuhTheme.colors.textPrimary)
                                Text(text = "عرض وتتبع تنبيهات الإحالات وأرباح العمولات الفورية", fontSize = 11.sp, color = HuhTheme.colors.textSecondary)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (unreadNotificationCount > 0) {
                                Surface(
                                    color = HuhTheme.colors.primary,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "$unreadNotificationCount جديد",
                                        color = HuhTheme.colors.onPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = HuhTheme.colors.textTertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section: Hidden Secondary Profile (البروفايل الثانوي المخفي برمز سري)
        item {
            Text(
                text = "الخصوصية المتقدمة (البروفايل الثانوي المخفي)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = HuhTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, HuhTheme.colors.gold.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val activeSec = activeSecondaryProfile
                    if (activeSec != null) {
                        // Currently in secondary profile
                        Surface(
                            color = HuhTheme.colors.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HuhTheme.colors.gold)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val avRes = activeSec.avatarResId ?: R.drawable.img_live_model_3
                                    Image(
                                        painter = painterResource(id = avRes),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, HuhTheme.colors.gold, CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "🎭 أنت الآن داخل: ${activeSec.displayName}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = HuhTheme.colors.gold
                                        )
                                        Text(
                                            text = "@${activeSec.username} • محمي برمز سري",
                                            fontSize = 11.sp,
                                            color = HuhTheme.colors.textSecondary
                                        )
                                    }
                                }

                                Button(
                                    onClick = { viewModel.lockSecondaryProfile() },
                                    colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.error),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("قفل فوري", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    } else {
                        // Not in secondary profile -> Option to create or unlock
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenCreateSecondaryProfileDialog)
                                .testTag("open_create_secondary_profile_button"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = HuhTheme.colors.gold,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "إنشاء بروفايل ثانوي مخفي (Hidden Vault)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = HuhTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = "هوية مستقلة محميّة برمز سري؛ لا تظهر في أي قائمة وتُفتح بالبحث",
                                        fontSize = 11.sp,
                                        color = HuhTheme.colors.textSecondary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = HuhTheme.colors.gold,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = HuhTheme.colors.surfaceVariant)

                        // Explanation
                        Text(
                            text = "💡 طريقة الدخول للبروفايل المخفي: اكتب الرمز السري الخاص بالبروفايل في أي وقت في شريط البحث الرئيسي للتطبيق وسيتم نقلك للبروفايل السري مباشرة وبشكل صامت دون ترك أي أثر.",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section: Safety & Policies
        item {
            Text(
                text = AppStrings.get("safety_title", appLanguage),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = HuhTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, HuhTheme.colors.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingRow(
                        icon = Icons.Default.VerifiedUser,
                        title = AppStrings.get("safety_age_verify_title", appLanguage),
                        subtitle = AppStrings.get("safety_age_verify_desc", appLanguage)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = HuhTheme.colors.surfaceVariant)
                    SettingRow(
                        icon = Icons.Default.Policy,
                        title = AppStrings.get("safety_tos_title", appLanguage),
                        subtitle = AppStrings.get("safety_tos_desc", appLanguage)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = HuhTheme.colors.surfaceVariant)
                    SettingRow(
                        icon = Icons.Default.Lock,
                        title = AppStrings.get("safety_privacy_title", appLanguage),
                        subtitle = AppStrings.get("safety_privacy_desc", appLanguage)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = HuhTheme.colors.surfaceVariant)
                    SettingRow(
                        icon = Icons.Default.Info,
                        title = AppStrings.get("safety_about_title", appLanguage),
                        subtitle = AppStrings.get("safety_about_desc", appLanguage)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            // Logout Button
            Button(
                onClick = { viewModel.logoutUser() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.error),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppStrings.get("logout_button", appLanguage),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Language Selection Bottom Sheet Dialog
    if (showLanguageBottomSheet) {
        LanguageSelectionBottomSheet(
            currentLanguage = appLanguage,
            onLanguageSelected = { newLanguage ->
                viewModel.setAppLanguage(newLanguage)
            },
            onDismiss = { showLanguageBottomSheet = false }
        )
    }

    // Edit Account Settings & Profile Dialog
    if (showEditAccountDialog) {
        AlertDialog(
            onDismissRequest = { showEditAccountDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = HuhTheme.colors.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "تعديل إعدادات الحساب ⚙️",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = HuhTheme.colors.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "قم بتحديث بيانات حسابك ومعلومات ملفك الشخصي:",
                        fontSize = 12.sp,
                        color = HuhTheme.colors.textSecondary
                    )

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("الاسم المستعار / الظاهر") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = HuhTheme.colors.primary)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_name_text_field")
                    )

                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("النبذة الشخصية (Bio)") },
                        leadingIcon = {
                            Icon(Icons.Default.Info, contentDescription = null, tint = HuhTheme.colors.primary)
                        },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_bio_text_field")
                    )

                    OutlinedTextField(
                        value = editCountry,
                        onValueChange = { editCountry = it },
                        label = { Text("الدولة / المنطقة") },
                        leadingIcon = {
                            Icon(Icons.Default.Public, contentDescription = null, tint = HuhTheme.colors.primary)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_country_text_field")
                    )

                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("رقم الهاتف المحمول") },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = HuhTheme.colors.primary)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_phone_text_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAccountSettings(
                            name = editName.trim().ifEmpty { userProfile.name },
                            bio = editBio.trim(),
                            country = editCountry.trim().ifEmpty { userProfile.country },
                            phoneNumber = editPhone.trim()
                        )
                        showEditAccountDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.primary),
                    modifier = Modifier.testTag("save_account_settings_button")
                ) {
                    Text("حفظ التغييرات", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditAccountDialog = false },
                    modifier = Modifier.testTag("cancel_edit_account_settings_button")
                ) {
                    Text("إلغاء", color = HuhTheme.colors.textSecondary)
                }
            },
            containerColor = HuhTheme.colors.surfaceElevated
        )
    }

    if (showLeaderboardDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showLeaderboardDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { showLeaderboardDialog = false },
                            modifier = Modifier
                                .size(36.dp)
                                .background(HuhTheme.colors.surfaceElevated, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = HuhTheme.colors.textPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LeaderboardComponent(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = HuhTheme.colors.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HuhTheme.colors.textPrimary)
                Text(text = subtitle, fontSize = 11.sp, color = HuhTheme.colors.textSecondary)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = HuhTheme.colors.textTertiary,
            modifier = Modifier.size(14.dp)
        )
    }
}

