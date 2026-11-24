package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AgeVerificationSubmissionDoc
import com.example.data.model.UserProfile
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerGreenOnline
import com.example.ui.theme.HuhTheme
import com.example.util.AgePolicyUtils

@Composable
fun AgeVerificationGateScreen(
    userProfile: UserProfile?,
    ageSubmission: AgeVerificationSubmissionDoc?,
    isSubmitting: Boolean,
    errorMessage: String?,
    onSubmitAgeVerification: (dob: String, acceptedTerms: Boolean, acceptedPrivacy: Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val effectiveStatus = when {
        userProfile?.ageVerified == true || ageSubmission?.status == "APPROVED" -> "APPROVED"
        ageSubmission?.status == "PENDING_REVIEW" || userProfile?.ageVerificationStatus == "PENDING_REVIEW" -> "PENDING_REVIEW"
        ageSubmission?.status == "REJECTED" || userProfile?.ageVerificationStatus == "REJECTED" -> "REJECTED"
        else -> "UNVERIFIED"
    }

    var dobInput by remember(userProfile?.dob) { mutableStateOf(userProfile?.dob.orEmpty()) }
    var acceptTerms by remember { mutableStateOf(userProfile?.acceptedTermsAt != null) }
    var acceptPrivacy by remember { mutableStateOf(userProfile?.acceptedPrivacyAt != null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("age_verification_gate_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
            border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (effectiveStatus == "PENDING_REVIEW") Icons.Default.HourglassTop else Icons.Default.VerifiedUser,
                    contentDescription = "التحقق من العمر +18",
                    tint = if (effectiveStatus == "PENDING_REVIEW") AyerGold else AyerCyan,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "بوابة التحقق من العمر (+18) والامتثال القانوني",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = HuhTheme.colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "مرحباً ${userProfile?.name?.ifBlank { "بك" } ?: "بك"}. حفاظاً على سلامة المجتمع والامتثال لسياسات المتجر، يُشترط التحقق من بلوغ 18 عاماً والموافقة على الشروط قبل استخدام التطبيق.",
                    fontSize = 13.sp,
                    color = HuhTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (effectiveStatus == "PENDING_REVIEW") {
                    Surface(
                        color = AyerGold.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("age_verification_pending_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "طلبك قيد المراجعة الإدارية",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AyerGold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "تم استلام تاريخ ميلادك (${ageSubmission?.dob ?: userProfile?.dob ?: ""}) وموافقتك القانونية بنجاح. سيتم فتح الحساب فور اعتماد فريق المراجعة.",
                                fontSize = 13.sp,
                                color = HuhTheme.colors.textPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    if (effectiveStatus == "REJECTED") {
                        Surface(
                            color = HuhTheme.colors.error.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, HuhTheme.colors.error.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = HuhTheme.colors.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = ageSubmission?.rejectionReason?.takeIf { it.isNotBlank() }
                                        ?: "تم رفض طلب التحقق السابق. يرجى التأكد من صحة تاريخ الميلاد وإعادة الإرسال.",
                                    color = HuhTheme.colors.error,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = dobInput,
                        onValueChange = {
                            dobInput = it
                            localError = null
                        },
                        label = { Text("تاريخ الميلاد (YYYY-MM-DD)") },
                        placeholder = { Text("مثال: 1998-05-14") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Cake, contentDescription = null, tint = AyerCyan)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dob_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = acceptTerms,
                            onCheckedChange = {
                                acceptTerms = it
                                localError = null
                            },
                            modifier = Modifier.testTag("accept_terms_checkbox")
                        )
                        Text(
                            text = "أوافق على شروط الاستخدام وأؤكد أن عمري 18 سنة فأكثر",
                            fontSize = 12.sp,
                            color = HuhTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showTermsDialog = true }) {
                            Text("عرض", color = AyerCyan, fontSize = 12.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = acceptPrivacy,
                            onCheckedChange = {
                                acceptPrivacy = it
                                localError = null
                            },
                            modifier = Modifier.testTag("accept_privacy_checkbox")
                        )
                        Text(
                            text = "أوافق على سياسة الخصوصية ومعالجة بيانات التحقق",
                            fontSize = 12.sp,
                            color = HuhTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showPrivacyDialog = true }) {
                            Text("عرض", color = AyerCyan, fontSize = 12.sp)
                        }
                    }

                    val activeError = localError ?: errorMessage
                    if (!activeError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = activeError,
                            color = HuhTheme.colors.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("age_verification_error")
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val check = AgePolicyUtils.evaluateDob(dobInput)
                            if (!check.isValidFormat || !check.isAdult) {
                                localError = check.errorMessage ?: "غير مؤهل عمرياً (+18 مطلوب)."
                                return@Button
                            }
                            if (!acceptTerms || !acceptPrivacy) {
                                localError = "يجب الموافقة على شروط الاستخدام وسياسة الخصوصية."
                                return@Button
                            }
                            onSubmitAgeVerification(check.normalizedDob, acceptTerms, acceptPrivacy)
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("submit_age_verification_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AyerGreenOnline,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("إرسال للمراجعة الإدارية (+18)", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sign_out_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "تسجيل الخروج"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تسجيل الخروج")
                }
            }
        }
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("شروط الاستخدام (+18)") },
            text = { Text("هذا التطبيق مخصص للبالغين 18 عاماً فأكثر، ويُمنع أي محتوى مسيء أو انتهاك لخصوصية المستخدمين.") },
            confirmButton = { TextButton(onClick = { showTermsDialog = false }) { Text("إغلاق") } }
        )
    }
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("سياسة الخصوصية") },
            text = { Text("يتم حفظ تاريخ الميلاد وطوابع الموافقة القانونية بأمان في سجل التحقق الخاص بك ولا تتم مشاركته مع أطراف ثالثة.") },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("إغلاق") } }
        )
    }
}
