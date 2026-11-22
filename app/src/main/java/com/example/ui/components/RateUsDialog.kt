package com.example.ui.components

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSecondary
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.HuhTheme
import com.example.util.AyerInAppReviewManager

@Composable
fun RateUsDialog(
    completedMissionsCount: Int = 3,
    onDismiss: () -> Unit,
    onRated: (rating: Int, feedbackTags: List<String>) -> Unit
) {
    val context = LocalContext.current
    var rating by remember { mutableIntStateOf(5) }
    val selectedTags = remember { mutableStateListOf<String>() }
    var isSubmitted by remember { mutableStateOf(false) }

    val feedbackTags = listOf(
        "⚡ مكالمات WebRTC سريعة",
        "🌐 ترجمة فورية دقيقة",
        "🎁 هدايا وتفاعل مباشر",
        "🛡️ أمان ومستخدمون حقيقيون",
        "💎 باقات نقاط ومكافآت"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .border(
                    BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(listOf(AyerGold, AyerCyan, AyerPrimary))
                    ),
                    RoundedCornerShape(28.dp)
                )
                .testTag("rate_us_dialog"),
            color = HuhTheme.colors.surfaceElevated,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = HuhTheme.colors.textSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AyerGold.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Celebration,
                                    contentDescription = null,
                                    tint = AyerGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$completedMissionsCount مهمات مكتملة! 🎉",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AyerGold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Hero Trophy Icon with glowing ring
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(AyerGold.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                        .border(
                            BorderStroke(
                                2.dp,
                                Brush.sweepGradient(listOf(AyerGold, AyerCyan, AyerGold))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSubmitted) "💖" else "⭐",
                        fontSize = 36.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isSubmitted) "شكراً جزيلاً لتقييمك!" else "هل يعجبك تطبيق Ayer؟",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = HuhTheme.colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isSubmitted)
                        "رأيك يساعدنا على الاستمرار في تطوير تجربة تعارف وبث مباشر بأعلى معايير الجودة."
                    else
                        "لقد أتممت مهماتك اليومية بنجاح! يسعدنا جداً معرفة تقييمك لمساعدتنا على تقديم أفضل جودة لمكالماتك وتجربتك.",
                    fontSize = 13.sp,
                    color = HuhTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (!isSubmitted) {
                    // 5-Star Rating Selector
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..5) {
                            val isSelected = i <= rating
                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1.15f else 1.0f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "star_scale_$i"
                            )

                            Icon(
                                imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = "نجمة $i",
                                tint = if (isSelected) AyerGold else HuhTheme.colors.outline,
                                modifier = Modifier
                                    .size(38.dp)
                                    .scale(scale)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        rating = i
                                    }
                                    .testTag("rate_star_$i")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dynamic mood label
                    val ratingLabel = when (rating) {
                        5 -> "ممتاز جداً! أسطوري ❤️🔥"
                        4 -> "رائع جداً وممتع! 😊"
                        3 -> "جيد، وهناك مجال للتحسين 👍"
                        2 -> "مقبول 😐"
                        else -> "يحتاج لتطوير 😕"
                    }

                    Text(
                        text = ratingLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AyerCyan
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Optional Feedback Feature Chips
                    Text(
                        text = "ما أكثر ما يعجبك في Ayer؟ (اختياري)",
                        fontSize = 12.sp,
                        color = HuhTheme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        feedbackTags.chunked(2).forEach { rowTags ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowTags.forEach { tag ->
                                    val isTagSelected = selectedTags.contains(tag)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isTagSelected) AyerPrimary.copy(alpha = 0.2f) else HuhTheme.colors.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isTagSelected) AyerPrimary else HuhTheme.colors.outline.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                if (isTagSelected) selectedTags.remove(tag) else selectedTags.add(tag)
                                            }
                                    ) {
                                        Text(
                                            text = tag,
                                            fontSize = 11.sp,
                                            fontWeight = if (isTagSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isTagSelected) AyerPrimary else HuhTheme.colors.textPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Primary Action Button (Triggers Google Play In-App Review API)
                    Button(
                        onClick = {
                            isSubmitted = true
                            onRated(rating, selectedTags.toList())

                            // Trigger official Google Play In-App Review flow
                            if (context is Activity) {
                                AyerInAppReviewManager.launchReviewFlow(context) {
                                    onDismiss()
                                }
                            } else {
                                AyerInAppReviewManager.openPlayStoreFallback(context)
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("submit_rating_btn"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AyerGold)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RateReview,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تقييم على Google Play ⭐",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("rate_later_btn")
                    ) {
                        Text(
                            text = "سأقوم بالتقييم لاحقاً",
                            fontSize = 13.sp,
                            color = HuhTheme.colors.textSecondary
                        )
                    }
                } else {
                    // Success View
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0x2200E676),
                        border = BorderStroke(1.dp, Color(0xFF00E676)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "تم تسجيل تقييمك بنجاح. نتمنى لك أوقاتاً ممتعة ومحادثات مميزة!",
                                fontSize = 13.sp,
                                color = Color.White,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AyerPrimary)
                    ) {
                        Text("متابعة الاستخدام", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
