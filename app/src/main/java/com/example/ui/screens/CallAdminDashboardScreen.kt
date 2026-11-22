package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.calls.CallAdminAlertEntity
import com.example.calls.CallGovernanceConfigEntity
import com.example.calls.CallLifecycleState
import com.example.calls.CallMonitoringSnapshot
import com.example.calls.CallRecordEntity
import com.example.calls.CallType
import com.example.calls.UserCallConsumptionSummary
import com.example.ui.AyerViewModel
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.HuhTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full "Call Management & Monitoring" Admin Dashboard Screen.
 * Gives the project owner live 1-on-1 call KPIs, cost & budget tracking,
 * user consumption rankings, full call logs, alert management, and live rules editing.
 */
@Composable
fun CallAdminDashboardScreen(
    viewModel: AyerViewModel,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val snapshot by viewModel.callMonitoringSnapshot.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("call_admin_dashboard"),
        containerColor = HuhTheme.colors.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HuhTheme.colors.surface)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(AyerPrimary, AyerCyan))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "إدارة المكالمات",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Call Management & Monitoring",
                                color = AyerGold,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "إدارة ومراقبة المكالمات والميزانية (LiveKit SFU)",
                                color = HuhTheme.colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_call_admin_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = HuhTheme.colors.textPrimary
                        )
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = HuhTheme.colors.surface,
                    contentColor = HuhTheme.colors.textPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = AyerCyan
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "المراقبة الحية",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) AyerCyan else HuhTheme.colors.textSecondary
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "السجل (${snapshot.callLogs.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 1) AyerCyan else HuhTheme.colors.textSecondary
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                text = "الميزانية والحدود",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 2) AyerCyan else HuhTheme.colors.textSecondary
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            when (selectedTab) {
                0 -> CallMonitoringTab(
                    snapshot = snapshot,
                    onAcknowledgeAlert = { viewModel.acknowledgeCallAlert(it) },
                    onToggleUserBlock = { uid, blocked -> viewModel.toggleUserCallBlock(uid, blocked) },
                    onToggleUserVip = { uid, exempt -> viewModel.toggleUserVipExemption(uid, exempt) }
                )
                1 -> CallLogsTab(logs = snapshot.callLogs)
                else -> CallBudgetControlsTab(
                    config = snapshot.config,
                    onSaveConfig = { viewModel.updateCallGovernanceConfig(it) },
                    onSimulateIncomingVideo = {
                        onDismiss()
                        viewModel.triggerIncomingCallDemo("سارة أحمد", isVideo = true)
                    },
                    onSimulateIncomingAudio = {
                        onDismiss()
                        viewModel.triggerIncomingCallDemo("خالد المنصور", isVideo = false)
                    }
                )
            }
        }
    }
}

@Composable
private fun CallMonitoringTab(
    snapshot: CallMonitoringSnapshot,
    onAcknowledgeAlert: (String) -> Unit,
    onToggleUserBlock: (String, Boolean) -> Unit,
    onToggleUserVip: (String, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Budget Consumption & Provider Health Hero Card
        item {
            val pct = snapshot.budgetUsedPercent.coerceIn(0.0, 100.0)
            val progressColor = when {
                pct >= 100.0 -> Color(0xFFFF1744)
                pct >= snapshot.config.alertThreshold2Percent -> Color(0xFFFF9100)
                pct >= snapshot.config.alertThreshold1Percent -> AyerGold
                else -> Color(0xFF00E676)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                border = BorderStroke(1.dp, progressColor.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "استهلاك الميزانية الشهرية",
                                color = HuhTheme.colors.textSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.2f", snapshot.monthlyEstimatedCost)} / ${String.format(Locale.US, "%.2f", snapshot.config.monthlyBudgetAmount)} ${snapshot.currencyCode}",
                                color = HuhTheme.colors.textPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 19.sp
                            )
                        }
                        Surface(
                            color = progressColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, progressColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", snapshot.budgetUsedPercent)}%",
                                color = progressColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { (pct / 100.0).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = progressColor,
                        trackColor = HuhTheme.colors.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "التكلفة اليومية: ${String.format(Locale.US, "%.2f", snapshot.dailyEstimatedCost)} ${snapshot.currencyCode}",
                            color = AyerCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (snapshot.config.providerHealthHealthy) "LiveKit: متصل سليم 🟢" else "تنبيه اتصال بالمزود 🔴",
                            color = if (snapshot.config.providerHealthHealthy) Color(0xFF00E676) else Color(0xFFFF5252),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (snapshot.config.disableVideoKeepAudioOnly) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = AyerGold.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "⚠️ وضع الصوت فقط مفعل حالياً (الفيديو متوقف للحفاظ على الميزانية)",
                                color = AyerGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. KPI Metrics Grid (All 15+ requested monitoring stats)
        item {
            Text(
                text = "مؤشرات الأداء والاستهلاك الفعلي",
                color = HuhTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiMiniCard("مكالمات اليوم", "${snapshot.todayCallsCount}", AyerCyan, Modifier.weight(1f))
                    KpiMiniCard("النشطة حالياً", "${snapshot.activeCallsCount}", Color(0xFF00E676), Modifier.weight(1f))
                    KpiMiniCard("متوسط المدة", "${snapshot.averageCallDurationSeconds} ث", AyerGold, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiMiniCard("صوتية اليوم", "${snapshot.todayAudioCallsCount}", Color(0xFF38BDF8), Modifier.weight(1f))
                    KpiMiniCard("فيديو اليوم", "${snapshot.todayVideoCallsCount}", AyerPrimary, Modifier.weight(1f))
                    KpiMiniCard("استهلاك اليوم", "${String.format(Locale.US, "%.1f", snapshot.todayConsumptionMinutes)} د", Color(0xFFA78BFA), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiMiniCard("دقائق الصوت", "${String.format(Locale.US, "%.1f", snapshot.totalAudioMinutes)} د", Color(0xFF38BDF8), Modifier.weight(1f))
                    KpiMiniCard("دقائق الفيديو", "${String.format(Locale.US, "%.1f", snapshot.totalVideoMinutes)} د", AyerPrimary, Modifier.weight(1f))
                    KpiMiniCard("استهلاك الشهر", "${String.format(Locale.US, "%.1f", snapshot.monthlyConsumptionMinutes)} د", AyerGold, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiMiniCard("ناجحة", "${snapshot.successfulCallsCount}", Color(0xFF00E676), Modifier.weight(1f))
                    KpiMiniCard("فائتة", "${snapshot.missedCallsCount}", Color(0xFFFFB300), Modifier.weight(1f))
                    KpiMiniCard("مرفوضة", "${snapshot.rejectedCallsCount}", Color(0xFFFF7043), Modifier.weight(1f))
                    KpiMiniCard("فاشلة", "${snapshot.failedCallsCount}", Color(0xFFFF5252), Modifier.weight(1f))
                }
            }
        }

        // 3. Active Admin Alerts
        if (snapshot.alerts.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تنبيهات الأمان والميزانية (${snapshot.alerts.count { !it.acknowledged }} غير مقروء)",
                        color = HuhTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            items(snapshot.alerts.take(8), key = { it.id }) { alert ->
                AdminCallAlertCard(alert = alert, onAcknowledge = { onAcknowledgeAlert(alert.id) })
            }
        }

        // 4. Top Consuming Users
        item {
            Text(
                text = "المستخدمون الأكثر استهلاكاً",
                color = HuhTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        if (snapshot.topConsumingUsers.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = HuhTheme.colors.surfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "لا يوجد استهلاك مسجل بعد في هذا الشهر.",
                        color = HuhTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(snapshot.topConsumingUsers, key = { it.userId }) { user ->
                TopConsumingUserCard(
                    user = user,
                    currencyCode = snapshot.currencyCode,
                    onToggleBlock = { onToggleUserBlock(user.userId, !user.isBlocked) },
                    onToggleVip = { onToggleUserVip(user.userId, !user.isExempt) }
                )
            }
        }
    }
}

@Composable
private fun KpiMiniCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = HuhTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, color = HuhTheme.colors.textSecondary, fontSize = 10.sp, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, color = accent, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
        }
    }
}

@Composable
private fun AdminCallAlertCard(
    alert: CallAdminAlertEntity,
    onAcknowledge: () -> Unit
) {
    val sevColor = when (alert.severity) {
        "CRITICAL" -> Color(0xFFFF1744)
        "HIGH" -> Color(0xFFFF9100)
        else -> AyerGold
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, sevColor.copy(alpha = if (alert.acknowledged) 0.2f else 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = alert.severity,
                    tint = sevColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = alert.title, color = HuhTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = alert.message, color = HuhTheme.colors.textSecondary, fontSize = 11.sp)
                }
            }
            if (!alert.acknowledged) {
                TextButton(onClick = onAcknowledge) {
                    Text("مراجعة ✓", color = AyerCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Icon(Icons.Default.CheckCircle, contentDescription = "تمت المراجعة", tint = Color(0xFF00E676), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TopConsumingUserCard(
    user: UserCallConsumptionSummary,
    currencyCode: String,
    onToggleBlock: () -> Unit,
    onToggleVip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.userName,
                        color = HuhTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${user.callCount} مكالمة • ${String.format(Locale.US, "%.1f", user.totalMinutes)} د • ~${String.format(Locale.US, "%.2f", user.estimatedCost)} $currencyCode",
                        color = HuhTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onToggleVip,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (user.isExempt) AyerGold else HuhTheme.colors.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (user.isExempt) AyerGold.copy(alpha = 0.16f) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Default.Star, null, tint = AyerGold, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (user.isExempt) "VIP معفى" else "استثناء", fontSize = 10.sp, color = AyerGold)
                    }

                    OutlinedButton(
                        onClick = onToggleBlock,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (user.isBlocked) Color(0xFFFF5252) else HuhTheme.colors.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (user.isBlocked) Color(0xFFFF5252).copy(alpha = 0.18f) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Default.Block, null, tint = if (user.isBlocked) Color(0xFFFF5252) else HuhTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (user.isBlocked) "موقوف" else "إيقاف", fontSize = 10.sp, color = if (user.isBlocked) Color(0xFFFF5252) else HuhTheme.colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallLogsTab(logs: List<CallRecordEntity>) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    val filtered = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "AUDIO" -> logs.filter { it.callType == CallType.AUDIO.name }
            "VIDEO" -> logs.filter { it.callType == CallType.VIDEO.name }
            "ENDED" -> logs.filter { it.status == CallLifecycleState.ENDED.name }
            "MISSED" -> logs.filter { it.status == CallLifecycleState.MISSED.name || it.status == CallLifecycleState.CANCELLED.name }
            "REJECTED" -> logs.filter { it.status == CallLifecycleState.REJECTED.name }
            "FAILED" -> logs.filter { it.status == CallLifecycleState.FAILED.name || it.status == CallLifecycleState.BLOCKED_BUDGET.name }
            else -> logs
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "ALL" to "الكل",
                "AUDIO" to "صوتية",
                "VIDEO" to "فيديو",
                "ENDED" to "مكتملة",
                "MISSED" to "فائتة",
                "REJECTED" to "مرفوضة",
                "FAILED" to "فاشلة/محظورة"
            ).forEach { (key, label) ->
                FilterChip(
                    selected = selectedFilter == key,
                    onClick = { selectedFilter = key },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AyerPrimary.copy(alpha = 0.28f),
                        selectedLabelColor = AyerGold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "لا توجد مكالمات مطابقة لهذا الفلتر بعد.",
                    color = HuhTheme.colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.callId }) { record ->
                    CallLogItemCard(record)
                }
            }
        }
    }
}

@Composable
private fun CallLogItemCard(record: CallRecordEntity) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    val dateStr = formatter.format(Date(record.initiatedAtMs))
    val isVideo = record.callType == CallType.VIDEO.name
    val statusColor = when (record.status) {
        CallLifecycleState.ENDED.name -> Color(0xFF00E676)
        CallLifecycleState.ACTIVE.name, CallLifecycleState.RECONNECTING.name -> AyerCyan
        CallLifecycleState.REJECTED.name -> Color(0xFFFF7043)
        CallLifecycleState.MISSED.name, CallLifecycleState.CANCELLED.name -> Color(0xFFFFB300)
        else -> Color(0xFFFF5252)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background((if (isVideo) AyerGold else AyerCyan).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = record.callType,
                        tint = if (isVideo) AyerGold else AyerCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${record.callerName} ➔ ${record.calleeName}",
                        color = HuhTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "$dateStr • ${if (isVideo) "فيديو" else "صوت"}",
                        color = HuhTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = record.status,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val dur = record.connectedDurationSeconds
                Text(
                    text = "${String.format("%02d:%02d", dur / 60, dur % 60)} • ${String.format(Locale.US, "%.3f", record.estimatedCost)} ${record.currencyCode}",
                    color = HuhTheme.colors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CallBudgetControlsTab(
    config: CallGovernanceConfigEntity,
    onSaveConfig: (CallGovernanceConfigEntity) -> Unit,
    onSimulateIncomingVideo: () -> Unit,
    onSimulateIncomingAudio: () -> Unit
) {
    var budgetText by remember(config.monthlyBudgetAmount) { mutableStateOf(config.monthlyBudgetAmount.toString()) }
    var currencyText by remember(config.currencyCode) { mutableStateOf(config.currencyCode) }
    var alert1Text by remember(config.alertThreshold1Percent) { mutableStateOf(config.alertThreshold1Percent.toString()) }
    var alert2Text by remember(config.alertThreshold2Percent) { mutableStateOf(config.alertThreshold2Percent.toString()) }
    var hardStop by remember(config.hardStopAt100Percent) { mutableStateOf(config.hardStopAt100Percent) }
    var maxDurationText by remember(config.maxCallDurationMinutes) { mutableStateOf(config.maxCallDurationMinutes.toString()) }
    var dailyUserMinText by remember(config.perUserDailyCallLimitMinutes) { mutableStateOf(config.perUserDailyCallLimitMinutes.toString()) }
    var dailyUserVideoText by remember(config.perUserDailyVideoLimitMinutes) { mutableStateOf(config.perUserDailyVideoLimitMinutes.toString()) }
    var monthlyVideoText by remember(config.monthlyVideoLimitMinutes) { mutableStateOf(config.monthlyVideoLimitMinutes.toString()) }
    var disableVideoKeepAudio by remember(config.disableVideoKeepAudioOnly) { mutableStateOf(config.disableVideoKeepAudioOnly) }
    var audioCostText by remember(config.audioCostPerParticipantMinute) { mutableStateOf(config.audioCostPerParticipantMinute.toString()) }
    var videoCostText by remember(config.videoCostPerParticipantMinute) { mutableStateOf(config.videoCostPerParticipantMinute.toString()) }
    var rateLimitText by remember(config.rateLimitCallsPerMinute) { mutableStateOf(config.rateLimitCallsPerMinute.toString()) }
    var exemptCsv by remember(config.exemptUserIdsCsv) { mutableStateOf(config.exemptUserIdsCsv) }
    var blockedCsv by remember(config.blockedUserIdsCsv) { mutableStateOf(config.blockedUserIdsCsv) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("call_budget_lazy_column"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = AyerGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إعدادات الميزانية والإنفاق الشهري", color = AyerGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericSettingField("الميزانية الشهرية", budgetText, { budgetText = it }, Modifier.weight(2f))
                        SettingTextField("العملة", currencyText, { currencyText = it }, Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericSettingField("تنبيه أول (%)", alert1Text, { alert1Text = it }, Modifier.weight(1f))
                        NumericSettingField("تنبيه ثاني (%)", alert2Text, { alert2Text = it }, Modifier.weight(1f))
                    }

                    SettingSwitchRow(
                        title = "إيقاف المكالمات الجديدة عند 100%",
                        subtitle = "لا ينقطع الاتصال النشط حالياً، ويمنع بدء مكالمات جديدة لغير المعفيين",
                        checked = hardStop,
                        onCheckedChange = { hardStop = it }
                    )

                    SettingSwitchRow(
                        title = "إيقاف الفيديو والإبقاء على الصوت فقط",
                        subtitle = "يحوّل جميع المكالمات إلى صوتية فائقة التوفير عند تفعيله",
                        checked = disableVideoKeepAudio,
                        onCheckedChange = { disableVideoKeepAudio = it }
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                border = BorderStroke(1.dp, AyerPrimary.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("حدود الاستخدام والأمان", color = AyerCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericSettingField("أقصى مدة للمكالمة (د)", maxDurationText, { maxDurationText = it }, Modifier.weight(1f))
                        NumericSettingField("الحد اليومي للمستخدم (د)", dailyUserMinText, { dailyUserMinText = it }, Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericSettingField("فيديو يومي/مستخدم (د)", dailyUserVideoText, { dailyUserVideoText = it }, Modifier.weight(1f))
                        NumericSettingField("فيديو شهري عام (د)", monthlyVideoText, { monthlyVideoText = it }, Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericSettingField("تكلفة دقيقة صوت/مشارك", audioCostText, { audioCostText = it }, Modifier.weight(1f))
                        NumericSettingField("تكلفة دقيقة فيديو/مشارك", videoCostText, { videoCostText = it }, Modifier.weight(1f))
                    }

                    NumericSettingField("حد الطلبات بالدقيقة (Rate Limit)", rateLimitText, { rateLimitText = it }, Modifier.fillMaxWidth())
                    SettingTextField("معرفات المستخدمين المعفيين VIP (مفصولة بفاصلة)", exemptCsv, { exemptCsv = it }, Modifier.fillMaxWidth())
                    SettingTextField("معرفات المستخدمين المحظورين من المكالمات", blockedCsv, { blockedCsv = it }, Modifier.fillMaxWidth())
                }
            }
        }

        item {
            Button(
                onClick = {
                    onSaveConfig(
                        config.copy(
                            monthlyBudgetAmount = budgetText.toDoubleOrNull() ?: config.monthlyBudgetAmount,
                            currencyCode = currencyText.trim().ifBlank { "EGP" }.uppercase(Locale.US),
                            alertThreshold1Percent = alert1Text.toIntOrNull() ?: config.alertThreshold1Percent,
                            alertThreshold2Percent = alert2Text.toIntOrNull() ?: config.alertThreshold2Percent,
                            hardStopAt100Percent = hardStop,
                            maxCallDurationMinutes = maxDurationText.toIntOrNull() ?: config.maxCallDurationMinutes,
                            perUserDailyCallLimitMinutes = dailyUserMinText.toIntOrNull() ?: config.perUserDailyCallLimitMinutes,
                            perUserDailyVideoLimitMinutes = dailyUserVideoText.toIntOrNull() ?: config.perUserDailyVideoLimitMinutes,
                            monthlyVideoLimitMinutes = monthlyVideoText.toIntOrNull() ?: config.monthlyVideoLimitMinutes,
                            disableVideoKeepAudioOnly = disableVideoKeepAudio,
                            audioCostPerParticipantMinute = audioCostText.toDoubleOrNull() ?: config.audioCostPerParticipantMinute,
                            videoCostPerParticipantMinute = videoCostText.toDoubleOrNull() ?: config.videoCostPerParticipantMinute,
                            rateLimitCallsPerMinute = rateLimitText.toIntOrNull() ?: config.rateLimitCallsPerMinute,
                            exemptUserIdsCsv = exemptCsv,
                            blockedUserIdsCsv = blockedCsv
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_call_budget_config_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AyerGold)
            ) {
                Text("حفظ وتطبيق القواعد فوراً", color = Color.Black, fontWeight = FontWeight.Black)
            }
        }

        // Quick Owner Verification Triggers for Incoming Calls
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختبار سريع للمكالمات الواردة في المحاكي", color = HuhTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onSimulateIncomingVideo,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Videocam, null, tint = AyerGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مكالمة فيديو واردة", fontSize = 11.sp, color = HuhTheme.colors.textPrimary)
                        }
                        OutlinedButton(
                            onClick = onSimulateIncomingAudio,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhoneCallback, null, tint = AyerCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مكالمة صوتية واردة", fontSize = 11.sp, color = HuhTheme.colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericSettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AyerCyan,
            unfocusedBorderColor = HuhTheme.colors.outline,
            focusedTextColor = HuhTheme.colors.textPrimary,
            unfocusedTextColor = HuhTheme.colors.textPrimary
        )
    )
}

@Composable
private fun SettingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AyerCyan,
            unfocusedBorderColor = HuhTheme.colors.outline,
            focusedTextColor = HuhTheme.colors.textPrimary,
            unfocusedTextColor = HuhTheme.colors.textPrimary
        )
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = HuhTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(text = subtitle, color = HuhTheme.colors.textSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = AyerGold)
        )
    }
}
