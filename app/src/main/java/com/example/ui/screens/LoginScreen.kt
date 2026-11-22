package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.AyerViewModel
import com.example.ui.components.BlurredLiveStreamsBackground
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSurface
import com.example.ui.theme.HuhTheme

@Composable
fun LoginScreen(
    viewModel: AyerViewModel,
    onLoginSuccess: () -> Unit
) {
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("dev@huh.com") }
    var password by remember { mutableStateOf("123456") }
    var showPassword by remember { mutableStateOf(false) }

    // Track user interaction (touched / typed) for real-time validation feedback
    var emailTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }

    // Real-time email validation
    val emailValidationError = remember(email, emailTouched) {
        if (!emailTouched && email.isBlank()) {
            null
        } else if (email.trim().isEmpty()) {
            "البريد الإلكتروني مطلوب"
        } else {
            val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
            if (!email.trim().matches(emailPattern)) {
                "صيغة البريد الإلكتروني غير صحيحة (مثال: name@example.com)"
            } else {
                null
            }
        }
    }

    // Real-time password validation
    val passwordValidationError = remember(password, passwordTouched, isLoginMode) {
        if (!passwordTouched && password.isEmpty()) {
            null
        } else if (password.isEmpty()) {
            "كلمة المرور مطلوبة"
        } else if (password.length < 6) {
            "يجب أن تتكون كلمة المرور من 6 أحرف أو أرقام على الأقل"
        } else {
            null
        }
    }

    val isFormValid = emailValidationError == null &&
        passwordValidationError == null &&
        email.trim().isNotBlank() &&
        password.isNotBlank()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val liveStreams by viewModel.liveStreams.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 1. Intriguing Blurred Live Streams Background
        BlurredLiveStreamsBackground(
            popularStreams = liveStreams
        )

        // 2. Foreground Authentication Card (Centered Glass Aesthetic with balanced presence)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        BorderStroke(
                            1.5.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.45f),
                                    AyerCyan.copy(alpha = 0.50f),
                                    AyerPrimary.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.15f)
                                )
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    ),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x6B0D1527) // Balanced translucent glass (~42% opacity) so card is softly visible and clear
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Logo & Header
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, AyerGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.huh_app_logo_1789389783248),
                            contentDescription = "HUH Logo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "HUH",
                        color = HuhTheme.colors.textPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Teaser Pill Tag
                    Surface(
                        color = Color(0x3300E5FF),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(0.5.dp, AyerCyan.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Whatshot,
                                contentDescription = null,
                                tint = AyerGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "بثوث حصرية ومباشرة جارية الآن",
                                color = AyerCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isLoginMode) "تسجيل الدخول" else "إنشاء حساب جديد",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "سجل دخولك لاكتشاف البثوث الحية والتواصل مع المضيفين",
                        fontSize = 13.sp,
                        color = HuhTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { 
                            email = it 
                            emailTouched = true
                            errorMessage = null
                        },
                        placeholder = { Text("البريد الإلكتروني", color = HuhTheme.colors.textTertiary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_email_input"),
                        shape = RoundedCornerShape(12.dp),
                        isError = emailValidationError != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email, 
                                contentDescription = null, 
                                tint = if (emailValidationError != null) HuhTheme.colors.error else HuhTheme.colors.textSecondary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0x401E293B),
                            unfocusedContainerColor = Color(0x301E293B),
                            focusedBorderColor = if (emailValidationError != null) HuhTheme.colors.error else HuhTheme.colors.primary,
                            unfocusedBorderColor = if (emailValidationError != null) HuhTheme.colors.error.copy(alpha = 0.7f) else Color(0x33FFFFFF),
                            focusedTextColor = HuhTheme.colors.textPrimary,
                            unfocusedTextColor = HuhTheme.colors.textPrimary,
                            errorBorderColor = HuhTheme.colors.error,
                            errorLeadingIconColor = HuhTheme.colors.error
                        )
                    )

                    // Real-time Email Validation Error Message
                    if (emailValidationError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = emailValidationError,
                            color = HuhTheme.colors.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp)
                                .testTag("email_validation_error")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it 
                            passwordTouched = true
                            errorMessage = null
                        },
                        placeholder = { Text("كلمة المرور", color = HuhTheme.colors.textTertiary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input"),
                        shape = RoundedCornerShape(12.dp),
                        isError = passwordValidationError != null,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock, 
                                contentDescription = null, 
                                tint = if (passwordValidationError != null) HuhTheme.colors.error else HuhTheme.colors.textSecondary
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { showPassword = !showPassword },
                                modifier = Modifier.testTag("toggle_password_visibility_button")
                            ) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showPassword) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                                    tint = if (showPassword) AyerCyan else HuhTheme.colors.textSecondary
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0x401E293B),
                            unfocusedContainerColor = Color(0x301E293B),
                            focusedBorderColor = if (passwordValidationError != null) HuhTheme.colors.error else HuhTheme.colors.primary,
                            unfocusedBorderColor = if (passwordValidationError != null) HuhTheme.colors.error.copy(alpha = 0.7f) else Color(0x33FFFFFF),
                            focusedTextColor = HuhTheme.colors.textPrimary,
                            unfocusedTextColor = HuhTheme.colors.textPrimary,
                            errorBorderColor = HuhTheme.colors.error,
                            errorLeadingIconColor = HuhTheme.colors.error
                        )
                    )

                    // Real-time Password Validation Error Message
                    if (passwordValidationError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = passwordValidationError,
                            color = HuhTheme.colors.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp)
                                .testTag("password_validation_error")
                        )
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = HuhTheme.colors.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            emailTouched = true
                            passwordTouched = true

                            if (!isFormValid) {
                                errorMessage = emailValidationError ?: passwordValidationError ?: "يرجى تصحيح الأخطاء قبل المتابعة"
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null

                            if (isLoginMode) {
                                viewModel.loginUser(email.trim(), password) { success, errorMsg ->
                                    isLoading = false
                                    if (success) {
                                        onLoginSuccess()
                                    } else {
                                        errorMessage = errorMsg ?: "فشل تسجيل الدخول"
                                    }
                                }
                            } else {
                                viewModel.registerUser(email.trim(), password) { success, errorMsg ->
                                    isLoading = false
                                    if (success) {
                                        onLoginSuccess()
                                    } else {
                                        errorMessage = errorMsg ?: "فشل إنشاء الحساب"
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("login_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFormValid) HuhTheme.colors.primary else HuhTheme.colors.primary.copy(alpha = 0.65f),
                            contentColor = HuhTheme.colors.onPrimary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = HuhTheme.colors.onPrimary, modifier = Modifier.size(22.dp))
                        } else {
                            Text(
                                text = if (isLoginMode) "دخول" else "إنشاء الحساب",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.onPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = {
                            isLoginMode = !isLoginMode
                            errorMessage = null
                        }
                    ) {
                        Text(
                            text = if (isLoginMode) "ليس لديك حساب؟ إنشاء حساب جديد" else "لديك حساب بالفعل؟ تسجيل الدخول",
                            color = AyerCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
