package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LiveStreamRoom
import com.example.data.model.UserProfile
import com.example.ui.AyerViewModel
import com.example.ui.components.NotificationBellButton
import com.example.ui.components.NotificationItemCard
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerGoldGlow
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerPrimaryContainer
import com.example.ui.theme.AyerSecondary
import com.example.ui.theme.AyerSurface
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.AyerSurfaceVariant
import com.example.ui.theme.HuhTheme
import com.example.util.SecurityUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgenciesScreen(
    viewModel: AyerViewModel,
    userProfile: UserProfile,
    onOpenNotificationCenter: () -> Unit = {},
    onOpenCallAdminDashboard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = SecurityUtils.findActivity(context)
        SecurityUtils.setScreenCaptureProtection(activity, true)
        onDispose {
            SecurityUtils.setScreenCaptureProtection(activity, false)
        }
    }

    val clipboardManager = LocalClipboardManager.current
    var showInviteDialog by remember { mutableStateOf(false) }
    var showAdminCommissionModal by remember { mutableStateOf(false) }
    var showAdminAddStreamModal by remember { mutableStateOf(false) }
    var showBankPayoutModal by remember { mutableStateOf(false) }
    var referralCopied by remember { mutableStateOf(false) }
    var newAgentName by remember { mutableStateOf("") }
    var newAgentCountry by remember { mutableStateOf("المملكة العربية السعودية") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var adminCommissionSlider by remember { mutableFloatStateOf(userProfile.agentCommissionPercent.toFloat()) }

    LaunchedEffect(Unit) {
        viewModel.refreshAgencyDashboard()
    }

    val subAgentsList by viewModel.subAgentsList.collectAsStateWithLifecycle()
    val myPayouts by viewModel.myPayouts.collectAsStateWithLifecycle()
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val withdrawableCoins = userProfile.subAgentsEarningsCoins + userProfile.hostEarningsCoins

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(HuhTheme.colors.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Hero Agent VIP Header
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agency_hero_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF2A0D45),
                                    Color(0xFF5A082E),
                                    Color(0xFF130A24)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.horizontalGradient(listOf(AyerGold, AyerPrimary, AyerCyan)),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(
                                            Brush.radialGradient(listOf(AyerGold, AyerGoldGlow)),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WorkspacePremium,
                                        contentDescription = "شارة الوكيل",
                                        tint = Color.Black,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "بوابة الوكيل المعتمد",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = AyerGold.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AyerGold)
                                        ) {
                                            Text(
                                                text = "VIP Agent",
                                                color = AyerGold,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "الوكالة: ${userProfile.agencyName}",
                                        fontSize = 12.sp,
                                        color = Color.LightGray
                                    )
                                }
                            }

                            NotificationBellButton(
                                unreadCount = unreadNotificationCount,
                                onClick = onOpenNotificationCenter
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Commission rate & Admin control callout
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AyerPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "نسبة أرباحك من الوكلاء والمضيفين",
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = "${userProfile.agentCommissionPercent}%",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = AyerGold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(محددة من لوحة الأدمن المركزية)",
                                            fontSize = 11.sp,
                                            color = AyerCyan
                                        )
                                    }
                                }

                                if (userProfile.isAdmin) {
                                    Button(
                                        onClick = { showAdminCommissionModal = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = AyerPrimaryContainer),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("admin_commission_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "لوحة الأدمن", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }

                        if (userProfile.isAdmin) {
                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = onOpenCallAdminDashboard,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E293B)),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AyerCyan.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("agency_open_call_admin_button")
                            ) {
                                Icon(Icons.Default.QueryStats, contentDescription = null, tint = AyerCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Call Management & Monitoring (مراقبة وميزانية المكالمات)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AyerCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stats Summary Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatBox(
                                title = "الوكلاء المدعوون",
                                value = "${userProfile.subAgentsCount} وكلاء",
                                color = AyerCyan,
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                title = "إجمالي عمولاتك",
                                value = "${userProfile.subAgentsEarningsCoins} 💎",
                                color = AyerGold,
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                title = "طلبات السحب",
                                value = "${myPayouts.size} طلب",
                                color = Color(0xFF00E676),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Action Buttons (Invite Sub-Agent & Referral Code)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AyerSurfaceElevated),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "كود دعوة الوكلاء الخاص بك",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F071D), RoundedCornerShape(12.dp))
                            .border(1.dp, AyerPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = userProfile.referralCode,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AyerGold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (userProfile.referralCode.isNotBlank()) {
                                        clipboardManager.setText(AnnotatedString(userProfile.referralCode))
                                        referralCopied = true
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("copy_referral_code_button")
                        ) {
                            Icon(
                                imageVector = if (referralCopied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                                contentDescription = "نسخ كود الدعوة",
                                tint = if (referralCopied) Color(0xFF00E676) else AyerCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (referralCopied) "تم النسخ" else "نسخ الكود",
                                fontSize = 12.sp,
                                color = if (referralCopied) Color(0xFF00E676) else AyerCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showInviteDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("recruit_sub_agent_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AyerPrimary)
                    ) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "إضافة ودعوة وكيل جديد لشبكتك",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }

        // Tab Row: Sub-agents List vs Agency Notifications vs Agency Rules
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = HuhTheme.colors.surface,
                contentColor = HuhTheme.colors.textPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = HuhTheme.colors.primary
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "شبكة الوكلاء (${subAgentsList.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) HuhTheme.colors.primary else HuhTheme.colors.textSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = if (unreadNotificationCount > 0) "العمولات ($unreadNotificationCount 🔴)" else "العمولات (${notifications.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) HuhTheme.colors.primary else HuhTheme.colors.textSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "سحب الأرباح واللوائح",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 2) HuhTheme.colors.primary else HuhTheme.colors.textSecondary
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        if (selectedTab == 0) {
            // Sub Agents list
            items(subAgentsList) { subAgent ->
                SubAgentCard(
                    item = subAgent,
                    commissionPercent = userProfile.agentCommissionPercent
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        } else if (selectedTab == 1) {
            // Agency & Influencer Notifications Center Tab
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF160927)),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AyerPrimary.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "تنبيهات العمولات والإحالات المباشرة 🔔",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "إشعارات فورية لكل إحالة تسجل برابطك وأرباح عمولاتك",
                                    fontSize = 11.sp,
                                    color = Color(0xFFD3C5EE)
                                )
                            }
                            if (unreadNotificationCount > 0) {
                                Surface(
                                    color = AyerGold.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AyerGold)
                                ) {
                                    Text(
                                        text = "$unreadNotificationCount جديد",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AyerGold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (unreadNotificationCount > 0) {
                                TextButton(
                                    onClick = { viewModel.markAllNotificationsAsRead() },
                                    modifier = Modifier.testTag("agency_mark_all_read")
                                ) {
                                    Text("تعيين الكل كمقروء ✓", fontSize = 11.sp, color = AyerCyan)
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            TextButton(
                                onClick = onOpenNotificationCenter,
                                modifier = Modifier.testTag("agency_open_center_btn")
                            ) {
                                Text("فتح مركز الإشعارات المنبثق ↗", fontSize = 11.sp, color = AyerGold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            if (notifications.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AyerSurfaceElevated),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "لا توجد إشعارات حالياً في صندوق الوارد",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ستظهر هنا تنبيهات الإحالات والعمولات وسحوباتك المعتمدة فور وصولها",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                items(notifications, key = { it.id }) { item ->
                    NotificationItemCard(
                        notification = item,
                        onClick = { viewModel.markNotificationAsRead(item.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        } else {
            // Cash Out & Payout Requests History
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AyerSurfaceElevated),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AyerGold.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(text = "رصيد أرباح الوكالة القابل للسحب", fontSize = 12.sp, color = Color.LightGray)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$withdrawableCoins نقطة",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AyerGold
                        )
                        Text(
                            text = "≈ $${String.format("%.2f", withdrawableCoins / 10.0)} دولار أمريكي (الحد الأدنى للسحب 500 نقطة)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF00E676)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showBankPayoutModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AyerGold),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_bank_payout_modal_button")
                        ) {
                            Icon(imageVector = Icons.Default.AccountBalance, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "تقديم طلب سحب بنكي للمراجعة", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "سجل طلبات السحب (${myPayouts.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HuhTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (myPayouts.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AyerSurfaceElevated),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "لا توجد طلبات سحب مسجلة حتى الآن. عند تقديم طلب سحب، سيتم حجز الرصيد ومراجعته من الإدارة.",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            if (myPayouts.isNotEmpty()) {
                items(myPayouts, key = { it.id }) { payout ->
                    AgencyPayoutCard(payout = payout)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))

                // Security and Compliance Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AyerSurfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Color(0xFF00E676))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "حماية الخصوصية ومكافحة التسجيل",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "التطبيق مفعل عليه ميزة FLAG_SECURE لحظر تسجيل الشاشة أو التقاط لقطات لضمان أقصى درجات الخصوصية والأمان لمضيفاتك ومستخدميك.",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }

    // Modal: Invite Sub-Agent Dialog
    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = {
                Text(
                    text = "دعوة وكيل فرعي جديد لشبكتك",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = "ستحصل على عمولة بنسبة ${userProfile.agentCommissionPercent}% من أرباح مضيفي الوكيل الفرعي وفق لوائح الوكالة المعتمدة.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = newAgentName,
                        onValueChange = { newAgentName = it },
                        label = { Text("اسم الوكالة أو الوكيل") },
                        placeholder = { Text("مثال: وكالة الأضواء العالمية") },
                        modifier = Modifier.fillMaxWidth().testTag("input_agent_name"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newAgentCountry,
                        onValueChange = { newAgentCountry = it },
                        label = { Text("الدولة") },
                        placeholder = { Text("مثال: المملكة العربية السعودية") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newAgentName.isNotBlank()) {
                            viewModel.recruitNewSubAgent(newAgentName, newAgentCountry)
                            newAgentName = ""
                            showInviteDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AyerPrimary),
                    modifier = Modifier.testTag("confirm_recruit_button")
                ) {
                    Text("إرسال الدعوة وإضافة الوكيل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = AyerSurfaceElevated
        )
    }

    // Modal: Admin Panel Commission Configuration Simulation
    if (showAdminCommissionModal) {
        AlertDialog(
            onDismissRequest = { showAdminCommissionModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AdminPanelSettings, contentDescription = null, tint = AyerGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "لوحة تحكم الأدمن المركزية",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AyerGold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "تتيح لك هذه اللوحة (كإدارة التطبيق) التحكم في نسبة عمولة الوكلاء من أرباح المضيفين والوكلاء الفرعيين بدون تحديث التطبيق.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "نسبة عمولة الوكيل المقررة:", fontSize = 13.sp, color = Color.White)
                        Text(
                            text = "${adminCommissionSlider.toInt()}%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = AyerGold
                        )
                    }
                    Slider(
                        value = adminCommissionSlider,
                        onValueChange = { adminCommissionSlider = it },
                        valueRange = 5f..50f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = AyerGold,
                            activeTrackColor = AyerPrimary
                        ),
                        modifier = Modifier.testTag("admin_commission_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "5% (حد أدنى)", fontSize = 10.sp, color = Color.Gray)
                        Text(text = "50% (حد أقصى)", fontSize = 10.sp, color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Admin feature: Add/Manage 4-second stream previews
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AyerPrimary.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = AyerGold, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "إدارة مقاطع البث 4ث (خلف الكرة)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AyerGold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "يمكن للأدمن إضافة أو اعتماد بث وكيل مع مقطع متحرك 4 ثوانٍ يظهر في شريط الرادار بالصفحة الرئيسية.",
                                fontSize = 10.sp,
                                color = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showAdminAddStreamModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AyerPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ إضافة بث وكالة 4ث للشريط المتحرك", fontSize = 11.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    showAdminCommissionModal = false
                                    onOpenCallAdminDashboard()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E293B)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Call Management & Monitoring 📊", fontSize = 11.sp, color = AyerCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAgentCommissionFromAdmin(adminCommissionSlider.toInt())
                        showAdminCommissionModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AyerGold),
                    modifier = Modifier.testTag("save_admin_commission_button")
                ) {
                    Text("حفظ النسبة الجديدة من الأدمن", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminCommissionModal = false }) {
                    Text("إغلاق", color = Color.Gray)
                }
            },
            containerColor = AyerSurfaceElevated
        )
    }

    // Modal: Admin Add/Manage 4-second Agency Stream
    if (showAdminAddStreamModal) {
        var hostNameInput by remember { mutableStateOf("") }
        var agencyNameInput by remember { mutableStateOf("وكالة النجوم الدولية") }
        var clipTitleInput by remember { mutableStateOf("مقطع بث حصري معتمد ⚡") }

        AlertDialog(
            onDismissRequest = { showAdminAddStreamModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = AyerGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "إضافة بث وكالة معتمد (4ث)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AyerGold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "أدخل بيانات البث لإضافته فوراً للشريط المتحرك (4 ثوانٍ) خلف الكرة الأرضية في شاشة المطابقة:",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = hostNameInput,
                        onValueChange = { hostNameInput = it },
                        label = { Text("اسم المضيف/ة") },
                        placeholder = { Text("مثال: ريم - دبي 🇦🇪") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = agencyNameInput,
                        onValueChange = { agencyNameInput = it },
                        label = { Text("اسم الوكالة المشرفة") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = clipTitleInput,
                        onValueChange = { clipTitleInput = it },
                        label = { Text("عنوان المقطع 4 ثوانٍ") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val host = hostNameInput.ifBlank { "مضيف وكالة جديد" }
                        val newStream = LiveStreamRoom(
                            id = "agency_admin_${System.currentTimeMillis()}",
                            hostName = host,
                            title = clipTitleInput,
                            viewersCount = 0,
                            isVip = true,
                            agencyName = agencyNameInput,
                            previewClipSeconds = 4,
                            previewClipTitle = clipTitleInput
                        )
                        viewModel.addAgencyStreamFromAdmin(newStream)
                        showAdminAddStreamModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AyerGold)
                ) {
                    Text("اعتماد وإدراج في الرادار", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminAddStreamModal = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = AyerSurfaceElevated
        )
    }

    // Modal: Real Bank Payout Request
    if (showBankPayoutModal) {
        var amountInput by remember { mutableStateOf(if (withdrawableCoins >= 500) "500" else withdrawableCoins.toString()) }
        var payoutMethod by remember { mutableStateOf("BANK_TRANSFER") }
        var accountHolder by remember { mutableStateOf(userProfile.name) }
        var bankName by remember { mutableStateOf("") }
        var ibanOrAccount by remember { mutableStateOf("") }
        var localError by remember { mutableStateOf<String?>(null) }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showBankPayoutModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AccountBalance, contentDescription = null, tint = AyerGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "طلب سحب أرباح بنكي",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AyerGold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "الرصيد القابل للسحب: $withdrawableCoins نقطة (≈ $${String.format("%.2f", withdrawableCoins / 10.0)})",
                        fontSize = 12.sp,
                        color = AyerCyan
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("المبلغ بالنقاط (الحد الأدنى 500)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("payout_amount_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "BANK_TRANSFER" to "تحويل بنكي",
                            "INSTAPAY" to "InstaPay",
                            "VODAFONE_CASH" to "محفظة كاش"
                        ).forEach { (code, label) ->
                            FilterChip(
                                selected = payoutMethod == code,
                                onClick = { payoutMethod = code },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AyerPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = accountHolder,
                        onValueChange = { accountHolder = it },
                        label = { Text("اسم صاحب الحساب الثلاثي") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("payout_holder_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("اسم البنك أو المحفظة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("payout_bank_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = ibanOrAccount,
                        onValueChange = { ibanOrAccount = it },
                        label = { Text("رقم الآيبان (IBAN) أو رقم الحساب") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("payout_iban_input")
                    )

                    if (localError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = localError!!, fontSize = 11.sp, color = Color(0xFFFF5252))
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSubmitting,
                    onClick = {
                        val coins = amountInput.toIntOrNull() ?: 0
                        when {
                            coins < 500 -> localError = "الحد الأدنى للسحب 500 نقطة."
                            coins > withdrawableCoins -> localError = "رصيدك القابل للسحب غير كافٍ."
                            accountHolder.isBlank() || bankName.isBlank() || ibanOrAccount.isBlank() -> {
                                localError = "يرجى إكمال جميع بيانات الحساب البنكي."
                            }
                            else -> {
                                localError = null
                                isSubmitting = true
                                viewModel.requestBankPayout(
                                    amountCoins = coins,
                                    payoutMethod = payoutMethod,
                                    bankAccountHolder = accountHolder.trim(),
                                    bankName = bankName.trim(),
                                    ibanOrAccount = ibanOrAccount.trim()
                                ) { success ->
                                    isSubmitting = false
                                    if (success) showBankPayoutModal = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AyerGold),
                    modifier = Modifier.testTag("submit_bank_payout_button")
                ) {
                    Text(if (isSubmitting) "جاري الإرسال..." else "إرسال الطلب للمراجعة", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(enabled = !isSubmitting, onClick = { showBankPayoutModal = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = AyerSurfaceElevated
        )
    }
}

@Composable
private fun AgencyPayoutCard(payout: com.example.data.remote.PayoutRequestDto) {
    val (statusLabel, statusColor) = when (payout.status) {
        "APPROVED" -> "معتمد للصرف" to Color(0xFF00E5FF)
        "PAID" -> "تم التحويل" to Color(0xFF00E676)
        "REJECTED" -> "مرفوض (مُسترد)" to Color(0xFFFF5252)
        else -> "قيد المراجعة" to AyerGold
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${payout.amountCoins} نقطة (≈ $${String.format("%.2f", payout.usdEquivalentCents / 100.0)})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AyerGold
                )
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${payout.payoutMethod} • ${payout.bankName} (${payout.ibanOrAccount.takeLast(6).padStart(payout.ibanOrAccount.length.coerceAtMost(10), '*')})",
                fontSize = 11.sp,
                color = HuhTheme.colors.textSecondary
            )
            if (!payout.paymentReference.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "مرجع الحوالة: ${payout.paymentReference}",
                    fontSize = 11.sp,
                    color = Color(0xFF00E676),
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (!payout.reviewerNote.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ملاحظة الإدارة: ${payout.reviewerNote}",
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
fun SubAgentCard(
    item: com.example.data.model.SubAgentItem,
    commissionPercent: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, AyerPrimary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = item.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                    Text(
                        text = "الدولة: ${item.country}",
                        fontSize = 11.sp,
                        color = HuhTheme.colors.textSecondary
                    )
                }
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = item.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HuhTheme.colors.surfaceVariant, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "مضيفين نشطين", fontSize = 10.sp, color = HuhTheme.colors.textTertiary)
                    Text(text = "${item.hostsCount}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AyerCyan)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "دخل الوكالة الشهري", fontSize = 10.sp, color = HuhTheme.colors.textTertiary)
                    Text(text = "${item.monthlyRevenueCoins} 💎", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HuhTheme.colors.textPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "عمولتك ($commissionPercent%)", fontSize = 10.sp, color = HuhTheme.colors.textTertiary)
                    Text(text = "+${(item.monthlyRevenueCoins * commissionPercent) / 100} 💎", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AyerGold)
                }
            }
        }
    }
}

@Composable
fun StatBox(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = HuhTheme.colors.surfaceElevated.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 10.sp, color = HuhTheme.colors.textSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}
