package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.HuhTheme

@Composable
fun LiveStreamReportDialog(
    streamId: String,
    streamTitle: String,
    hostName: String,
    onSubmitReport: (reason: String, details: String) -> Unit,
    onDismiss: () -> Unit
) {
    val reportReasons = remember {
        listOf(
            "محتوى غير لائق أو خادش للحياء 🔞",
            "تحرش أو تنمر أو إساءة لفظية 🚫",
            "ترويج للاحتيال أو روابط خارجية مشبوهة ⚠️",
            "محتوى عنيف أو تحريض على الكراهية ⚔️",
            "انتحال شخصية أو انتهاك حقوق الملكية 👤",
            "أخرى (يرجى التوضيح أدناه) 📝"
        )
    }

    var selectedReason by remember { mutableStateOf(reportReasons[0]) }
    var detailsText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isSubmittedSuccess by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        containerColor = HuhTheme.colors.surfaceElevated,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFFF43F5E).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color(0xFFF43F5E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "الإبلاغ عن البث المباشر 🛡️",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = "مضيف البث: $hostName",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textSecondary
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
                        tint = HuhTheme.colors.textTertiary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_stream_report_dialog")
            ) {
                if (isSubmittedSuccess) {
                    // Success state
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "تم إرسال البلاغ بنجاح!",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "تم تسجيل البلاغ في سيرفر Firestore الأمني ووضع علامة مراجعة فورية على هذا البث. فريق الإشراف يتحقق الآن لحماية المجتمع.",
                                fontSize = 11.sp,
                                color = HuhTheme.colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    // Form state
                    Text(
                        text = "اختر سبب البلاغ:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HuhTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        reportReasons.forEach { reason ->
                            val isSelected = selectedReason == reason
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) HuhTheme.colors.primary.copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .clickable { selectedReason = reason }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedReason = reason },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFFF43F5E),
                                        unselectedColor = HuhTheme.colors.textTertiary
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = reason,
                                    fontSize = 12.sp,
                                    color = if (isSelected) HuhTheme.colors.textPrimary else HuhTheme.colors.textSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "تفاصيل إضافية (اختياري):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HuhTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = detailsText,
                        onValueChange = { if (it.length <= 250) detailsText = it },
                        placeholder = {
                            Text(
                                "اذكر أي تفاصيل تساعد المشرفين في مراجعة البلاغ بدقة...",
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp)
                            .testTag("report_details_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = HuhTheme.colors.surface,
                            unfocusedContainerColor = HuhTheme.colors.surface,
                            focusedBorderColor = Color(0xFFF43F5E),
                            unfocusedBorderColor = HuhTheme.colors.outline
                        ),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Moderation Firestore note badge
                    Surface(
                        color = AyerCyan.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AyerCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "يتم إرسال البلاغ مباشرة لسيرفر Firestore مع التوثيق الزمني الفوري",
                                fontSize = 10.sp,
                                color = AyerCyan
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isSubmittedSuccess) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("close_report_success_button")
                ) {
                    Text("تم", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        isSubmitting = true
                        onSubmitReport(selectedReason, detailsText)
                        isSubmitting = false
                        isSubmittedSuccess = true
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF43F5E),
                        disabledContainerColor = Color(0xFFF43F5E).copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("submit_stream_report_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "إرسال البلاغ وتثبيت العلامة 🛡️",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        },
        dismissButton = {
            if (!isSubmittedSuccess) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HuhTheme.colors.outline)
                ) {
                    Text("إلغاء", color = HuhTheme.colors.textSecondary, fontSize = 12.sp)
                }
            }
        }
    )
}
