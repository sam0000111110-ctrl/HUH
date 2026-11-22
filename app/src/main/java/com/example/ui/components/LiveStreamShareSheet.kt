package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveStreamRoom
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSecondary
import com.example.ui.theme.AyerSurfaceElevated
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SocialShareTarget(
    val id: String,
    val name: String,
    val packageName: String,
    val iconEmoji: String,
    val brandColor: Color
)

val SupportedSocialMediaPlatforms = listOf(
    SocialShareTarget(
        id = "whatsapp",
        name = "واتساب",
        packageName = "com.whatsapp",
        iconEmoji = "💬",
        brandColor = Color(0xFF25D366)
    ),
    SocialShareTarget(
        id = "telegram",
        name = "تيليجرام",
        packageName = "org.telegram.messenger",
        iconEmoji = "✈️",
        brandColor = Color(0xFF2AABEE)
    ),
    SocialShareTarget(
        id = "x",
        name = "X (تويتر)",
        packageName = "com.twitter.android",
        iconEmoji = "𝕏",
        brandColor = Color(0xFF1DA1F2)
    ),
    SocialShareTarget(
        id = "facebook",
        name = "فيسبوك",
        packageName = "com.facebook.katana",
        iconEmoji = "👥",
        brandColor = Color(0xFF1877F2)
    ),
    SocialShareTarget(
        id = "instagram",
        name = "انستغرام",
        packageName = "com.instagram.android",
        iconEmoji = "📸",
        brandColor = Color(0xFFE1306C)
    ),
    SocialShareTarget(
        id = "snapchat",
        name = "سناب شات",
        packageName = "com.snapchat.android",
        iconEmoji = "👻",
        brandColor = Color(0xFFFFFC00)
    )
)

/**
 * Social Sharing Bottom Sheet for Live Streams.
 * Allows instant direct sharing to social media apps, copying the stream link,
 * and standard Android Sharesheet fallback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStreamShareSheet(
    stream: LiveStreamRoom,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    val streamShareUrl = "https://ayer.app/live/${stream.id}"
    val shareMessageText = "🔴 انضم الآن لبث ${stream.hostName} على تطبيق Ayer!\n«${stream.title}»\nرابط البث: $streamShareUrl"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("stream_share_sheet")
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
                            .background(
                                Brush.linearGradient(listOf(AyerCyan, AyerPrimary)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "مشاركة البث المباشر",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "شارك بث ${stream.hostName} مع أصدقائك عبر المنصات",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stream Preview Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AyerSurfaceElevated),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AyerPrimary.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎤", fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stream.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "المضيف: ${stream.hostName}",
                                fontSize = 11.sp,
                                color = AyerGold,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "• ${stream.hostCountryCode}",
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Social Media Platforms Horizontal List
            Text(
                text = "مشاركة مباشرة للتطبيقات:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFCBD5E1)
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SupportedSocialMediaPlatforms) { platform ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                launchSocialShare(
                                    context = context,
                                    platform = platform,
                                    textToShare = shareMessageText,
                                    streamUrl = streamShareUrl
                                )
                                onDismiss()
                            }
                            .testTag("share_platform_${platform.id}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(platform.brandColor.copy(alpha = 0.20f))
                                .padding(2.dp)
                                .background(Color(0xFF1E293B), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = platform.iconEmoji, fontSize = 24.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = platform.name,
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Copy Link Bar with feedback
            Text(
                text = "أو نسخ رابط البث المباشر:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFCBD5E1)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        copyStreamLink(context, streamShareUrl)
                        isCopied = true
                        coroutineScope.launch {
                            delay(2500)
                            isCopied = false
                        }
                    }
                    .testTag("copy_stream_link_button"),
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCopied) Color(0xFF00E676) else Color.White.copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = if (isCopied) Color(0xFF00E676) else AyerCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = streamShareUrl,
                            fontSize = 12.sp,
                            color = if (isCopied) Color(0xFF00E676) else Color(0xFFE2E8F0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = if (isCopied) Color(0xFF00E676).copy(alpha = 0.2f) else AyerCyan.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isCopied) "تم النسخ! ✓" else "نسخ الرابط",
                            color = if (isCopied) Color(0xFF00E676) else AyerCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Standard System Sharesheet Trigger
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        openSystemSharesheet(context, shareMessageText)
                        onDismiss()
                    }
                    .testTag("system_sharesheet_button"),
                color = Color(0x30334155),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "خيارات مشاركة أخرى عبر النظام...",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Copies the stream URL to the Android Clipboard Manager.
 */
private fun copyStreamLink(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("رابط بث Ayer", url)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "تم نسخ رابط البث إلى الحافظة بنجاح 📋", Toast.LENGTH_SHORT).show()
}

/**
 * Attempts to share directly to a specific social media package, or falls back to system sharesheet.
 */
private fun launchSocialShare(
    context: Context,
    platform: SocialShareTarget,
    textToShare: String,
    streamUrl: String
) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(platform.packageName)
            putExtra(Intent.EXTRA_SUBJECT, "بث مباشر على Ayer")
            putExtra(Intent.EXTRA_TEXT, textToShare)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback: If app is not installed, launch the standard system chooser
        val chooserIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "بث مباشر على Ayer")
            putExtra(Intent.EXTRA_TEXT, textToShare)
        }
        val chooser = Intent.createChooser(chooserIntent, "مشاركة عبر ${platform.name}")
        context.startActivity(chooser)
    }
}

/**
 * Standard Android native system share chooser.
 */
private fun openSystemSharesheet(context: Context, textToShare: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "بث مباشر على Ayer")
        putExtra(Intent.EXTRA_TEXT, textToShare)
    }
    val shareIntent = Intent.createChooser(sendIntent, "مشاركة البث المباشر عبر")
    context.startActivity(shareIntent)
}
