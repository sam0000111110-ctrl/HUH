package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.AyerSurfaceVariant

@Composable
fun CreateSecondaryProfileDialog(
    onDismiss: () -> Unit,
    onCreateProfile: (username: String, password: String, displayName: String, bio: String, notificationsEnabled: Boolean, avatarResId: Int) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("بروفايل خاص محمي برمز سري") }
    var notificationsEnabled by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val sampleAvatars = listOf(
        R.drawable.img_live_model_1,
        R.drawable.img_live_model_2,
        R.drawable.img_live_model_3,
        R.drawable.ic_ayer_logo
    )
    var selectedAvatarRes by remember { mutableIntStateOf(sampleAvatars[2]) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("create_secondary_profile_dialog"),
            colors = CardDefaults.cardColors(containerColor = AyerSurfaceElevated),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
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
                                .size(36.dp)
                                .background(AyerPrimary.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = AyerGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "إنشاء بروفايل ثانوي مخفي",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "محمي برمز سري ومخفي عن المتطفلين",
                                fontSize = 11.sp,
                                color = AyerCyan
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Important Compliance & Feature Explanation Banner
                Surface(
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AyerSurfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AyerGold,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "كيف يعمل البروفايل المخفي؟",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AyerGold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "• لن يظهر هذا البروفايل في أي قائمة أو زر تبديل في الواجهة لحمايتك من المتطفلين.\n• للوصول إليه في أي وقت: اكتب كلمة المرور الخاصة به في مربع البحث الرئيسي بالتطبيق!\n• نظامياً: البروفايل مرتبط بهويتك الموثقة والـ KYC ومحفظة النقاط المشتركة منعاً للاحتيال.",
                                fontSize = 11.sp,
                                color = Color.LightGray,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Avatar Selection
                Text(text = "اختر الصورة الرمزية للبروفايل:", fontSize = 12.sp, color = Color.LightGray)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(sampleAvatars) { avatarRes ->
                        val isSelected = selectedAvatarRes == avatarRes
                        Image(
                            painter = painterResource(id = avatarRes),
                            contentDescription = "صورة رمزية",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) AyerGold else Color.Gray.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { selectedAvatarRes = avatarRes },
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Username field
                Text(text = "اسم المستخدم الفريد (Username):", fontSize = 12.sp, color = Color.LightGray)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it.replace(" ", "").lowercase()
                        errorMessage = null
                    },
                    placeholder = { Text("مثال: shadow_rider99", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("secondary_username_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.25f),
                        focusedBorderColor = AyerPrimary,
                        unfocusedBorderColor = AyerSurfaceVariant,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Display Name field
                Text(text = "الاسم الظاهر (Display Name):", fontSize = 12.sp, color = Color.LightGray)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        errorMessage = null
                    },
                    placeholder = { Text("الاسم المستعار في المحادثات", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.25f),
                        focusedBorderColor = AyerPrimary,
                        unfocusedBorderColor = AyerSurfaceVariant,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Secret Password Field
                Text(text = "كلمة المرور السرية (تُكتب في البحث للدخول):", fontSize = 12.sp, color = Color.LightGray)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    placeholder = { Text("كلمة مرور أو رقم سري خاص (4 خانات فأكثر)", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("secondary_password_input"),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.25f),
                        focusedBorderColor = AyerGold,
                        unfocusedBorderColor = AyerSurfaceVariant,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Confirm Password Field
                Text(text = "تأكيد كلمة المرور:", fontSize = 12.sp, color = Color.LightGray)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    placeholder = { Text("أعد إدخال كلمة المرور للتأكيد", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.25f),
                        focusedBorderColor = AyerGold,
                        unfocusedBorderColor = AyerSurfaceVariant,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Notifications Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AyerSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = if (notificationsEnabled) AyerGold else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "إشعارات خاصة بالبروفايل", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(text = if (notificationsEnabled) "مفعلة لهذا البروفايل" else "معطلة لحماية الخصوصية", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = AyerPrimary)
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Submit Button
                Button(
                    onClick = {
                        when {
                            username.length < 3 -> {
                                errorMessage = "اسم المستخدم يجب ألا يقل عن 3 أحرف"
                            }
                            password.length < 4 -> {
                                errorMessage = "كلمة المرور يجب ألا تقل عن 4 خانات"
                            }
                            password != confirmPassword -> {
                                errorMessage = "كلمة المرور وتأكيدها غير متطابقين"
                            }
                            else -> {
                                onCreateProfile(
                                    username,
                                    password,
                                    displayName.ifBlank { username },
                                    bio,
                                    notificationsEnabled,
                                    selectedAvatarRes
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_create_secondary_profile_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AyerPrimary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "إنشاء وتفعيل البروفايل المخفي",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
