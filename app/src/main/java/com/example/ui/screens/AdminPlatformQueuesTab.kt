package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.AdminAgeVerificationItemDto
import com.example.data.remote.ModerationReportDto
import com.example.data.remote.PayoutRequestDto
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.HuhTheme
import java.util.Locale

@Composable
fun AdminPlatformQueuesTab(
    ageSubmissions: List<AdminAgeVerificationItemDto>,
    payouts: List<PayoutRequestDto>,
    reports: List<ModerationReportDto>,
    onRefresh: () -> Unit,
    onReviewAge: (uid: String, decision: String, reason: String?) -> Unit,
    onReviewPayout: (requestId: String, decision: String, note: String?, ref: String?) -> Unit,
    onAssignRoles: (uid: String, isAgent: Boolean, isHost: Boolean, commissionPct: Int, agencyName: String?) -> Unit,
    onResolveReport: (reportId: String, status: String, actionTaken: String) -> Unit
) {
    var sectionIndex by remember { mutableIntStateOf(0) }
    var targetUid by remember { mutableStateOf("") }
    var grantAgent by remember { mutableStateOf(true) }
    var grantHost by remember { mutableStateOf(false) }
    var commissionInput by remember { mutableStateOf("20") }
    var agencyNameInput by remember { mutableStateOf("") }
    var paymentRefInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "بوابة المراجعات والحوكمة الإدارية",
                    color = AyerGold,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
                IconButton(onClick = onRefresh, modifier = Modifier.testTag("admin_refresh_queues_btn")) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = AyerCyan)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = sectionIndex == 0,
                    onClick = { sectionIndex = 0 },
                    label = { Text("+18 (${ageSubmissions.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = sectionIndex == 1,
                    onClick = { sectionIndex = 1 },
                    label = { Text("السحوبات (${payouts.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = sectionIndex == 2,
                    onClick = { sectionIndex = 2 },
                    label = { Text("البلاغات (${reports.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = sectionIndex == 3,
                    onClick = { sectionIndex = 3 },
                    label = { Text("الوكالات", fontSize = 11.sp) }
                )
            }
        }

        when (sectionIndex) {
            0 -> {
                if (ageSubmissions.isEmpty()) {
                    item {
                        EmptyQueueCard(
                            title = "لا توجد طلبات تحقق +18 معلقة حالياً",
                            subtitle = "تظهر هنا طلبات التحقق من تاريخ الميلاد والموافقة القانونية للمراجعة."
                        )
                    }
                } else {
                    items(ageSubmissions, key = { it.uid }) { sub ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                            border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = AyerGold, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(sub.displayName.ifBlank { sub.uid }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Text("UID: ${sub.uid}", color = HuhTheme.colors.textSecondary, fontSize = 11.sp)
                                Text("تاريخ الميلاد: ${sub.dob} • العمر المحسوب: ${sub.ageYears} سنة", color = AyerCyan, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onReviewAge(sub.uid, "APPROVED", null) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("اعتماد +18", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { onReviewAge(sub.uid, "REJECTED", "تاريخ الميلاد غير متطابق") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("رفض", color = Color(0xFFFF5252), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                item {
                    OutlinedTextField(
                        value = paymentRefInput,
                        onValueChange = { paymentRefInput = it },
                        label = { Text("مرجع التحويل البنكي (عند الاعتماد كمدفوع)") },
                        placeholder = { Text("مثال: WIRE-2026-991") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (payouts.isEmpty()) {
                    item {
                        EmptyQueueCard(
                            title = "لا توجد طلبات سحب أرباح حالياً",
                            subtitle = "تصل هنا طلبات السحب البنكية من الوكلاء والمضيفين لمراجعتها وتأكيد الدفع."
                        )
                    }
                } else {
                    items(payouts, key = { it.id }) { req ->
                        val usd = String.format(Locale.US, "%.2f", req.usdEquivalentCents / 100.0)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                            border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = AyerGold, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("${req.amountCoins} نقطة ($$usd)", color = AyerGold, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                    }
                                    Surface(
                                        color = AyerPrimary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(req.status, color = AyerCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("المستفيد: ${req.bankAccountHolder} • ${req.bankName}", color = Color.White, fontSize = 12.sp)
                                Text("الحساب / IBAN: ${req.ibanOrAccount} (${req.payoutMethod})", color = HuhTheme.colors.textSecondary, fontSize = 11.sp)
                                if (req.status == "PENDING_REVIEW" || req.status == "APPROVED") {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                onReviewPayout(
                                                    req.id,
                                                    "PAID",
                                                    "تم التحويل البنكي",
                                                    paymentRefInput.ifBlank { "PAY-${System.currentTimeMillis()}" }
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("تأكيد الصرف (PAID)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { onReviewPayout(req.id, "REJECTED", "بيانات الحساب غير مكتملة", null) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("رفض واسترداد", color = Color(0xFFFF5252), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                if (reports.isEmpty()) {
                    item {
                        EmptyQueueCard(
                            title = "لا توجد بلاغات معلقة",
                            subtitle = "جميع بلاغات المحتوى والحسابات تمت مراجعتها."
                        )
                    }
                } else {
                    items(reports, key = { it.id }) { rep ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${rep.reportType}: ${rep.reason}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text("الهدف: ${rep.targetUserId ?: rep.streamId ?: "-"} • المُبلغ: ${rep.reporterUid}", color = HuhTheme.colors.textSecondary, fontSize = 11.sp)
                                if (rep.details.isNotBlank()) {
                                    Text(rep.details, color = Color.LightGray, fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onResolveReport(rep.id, "RESOLVED", "SUSPEND_USER") },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("إيقاف / تحذير", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { onResolveReport(rep.id, "DISMISSED", "NO_VIOLATION") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("حفظ البلاغ", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                        border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = AyerGold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ترقية وتعيين صلاحيات الوكيل/المضيف", color = AyerGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            OutlinedTextField(
                                value = targetUid,
                                onValueChange = { targetUid = it },
                                label = { Text("Firebase UID للمستخدم") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = agencyNameInput,
                                onValueChange = { agencyNameInput = it },
                                label = { Text("اسم الوكالة (اختياري)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = commissionInput,
                                onValueChange = { commissionInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("نسبة عمولة الوكيل % (5 إلى 50)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("تفعيل صفة الوكيل المعتمد (isAgent)", color = Color.White, fontSize = 13.sp)
                                Switch(checked = grantAgent, onCheckedChange = { grantAgent = it })
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("اعتماد مضيف بث (isHost)", color = Color.White, fontSize = 13.sp)
                                Switch(checked = grantHost, onCheckedChange = { grantHost = it })
                            }
                            Button(
                                onClick = {
                                    if (targetUid.isNotBlank()) {
                                        onAssignRoles(
                                            targetUid,
                                            grantAgent,
                                            grantHost,
                                            commissionInput.toIntOrNull()?.coerceIn(5, 50) ?: 20,
                                            agencyNameInput.takeIf { it.isNotBlank() }
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AyerPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("حفظ وتحديث الصلاحيات في الخادم", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyQueueCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = HuhTheme.colors.textSecondary, fontSize = 12.sp)
        }
    }
}
