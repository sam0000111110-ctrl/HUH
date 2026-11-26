package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.R
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.util.MediaImageHelper
import kotlin.math.abs

private val AvatarGradients = listOf(
    listOf(Color(0xFF6B21A8), Color(0xFFEC4899)),
    listOf(Color(0xFF0F766E), Color(0xFF06B6D4)),
    listOf(Color(0xFF1D4ED8), Color(0xFF7C3AED)),
    listOf(Color(0xFF9333EA), Color(0xFFF59E0B)),
    listOf(Color(0xFF0369A1), Color(0xFF2DD4BF)),
    listOf(Color(0xFF831843), Color(0xFFF43F5E))
)

fun extractDisplayInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "؟"
    val first = parts.first().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    val second = if (parts.size > 1) parts.last().firstOrNull()?.uppercaseChar()?.toString().orEmpty() else ""
    return (first + second).ifBlank { "؟" }
}

private fun isStockPlaceholderDrawable(@DrawableRes resId: Int?): Boolean {
    return resId == null ||
        resId == 0 ||
        resId == R.drawable.img_live_model_1 ||
        resId == R.drawable.img_live_model_2 ||
        resId == R.drawable.img_live_model_3 ||
        resId == R.drawable.stream_hero_bg
}

/**
 * Honest user/host avatar: shows real custom photo URL/URI or explicit non-stock drawable if present,
 * otherwise renders a clean deterministic monogram avatar from the user's real name.
 */
@Composable
fun UserInitialAvatar(
    name: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    @DrawableRes avatarResId: Int? = null,
    size: Dp = 48.dp,
    borderWidth: Dp = 1.5.dp,
    borderColor: Color = AyerCyan,
    fontSize: TextUnit = 18.sp
) {
    val coilModel = remember(imageUrl) { MediaImageHelper.toCoilModel(imageUrl, null) }
    val validResId = remember(avatarResId) {
        if (isStockPlaceholderDrawable(avatarResId)) null else avatarResId
    }
    val palette = remember(name) {
        val idx = abs(name.hashCode()) % AvatarGradients.size
        AvatarGradients[idx]
    }
    val initials = remember(name) { extractDisplayInitials(name) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(BorderStroke(borderWidth, borderColor), CircleShape)
                } else {
                    Modifier
                }
            )
            .background(Brush.linearGradient(palette)),
        contentAlignment = Alignment.Center
    ) {
        when {
            coilModel != null -> {
                Image(
                    painter = rememberAsyncImagePainter(model = coilModel),
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            validResId != null -> {
                Image(
                    painter = painterResource(id = validResId),
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Honest backdrop for a live stream card/stage when no custom poster frame exists,
 * using deterministic theme gradients and host monogram instead of stock model photography.
 */
@Composable
fun StreamRoomAmbientBackdrop(
    hostName: String,
    @DrawableRes imageResId: Int? = null,
    modifier: Modifier = Modifier
) {
    val validResId = remember(imageResId) {
        if (isStockPlaceholderDrawable(imageResId)) null else imageResId
    }
    if (validResId != null) {
        Image(
            painter = painterResource(id = validResId),
            contentDescription = hostName,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        val palette = remember(hostName) {
            val idx = abs(hostName.hashCode()) % AvatarGradients.size
            AvatarGradients[idx]
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF100821),
                            palette[0].copy(alpha = 0.72f),
                            palette[1].copy(alpha = 0.42f),
                            Color(0xFF0A0518)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            UserInitialAvatar(
                name = hostName,
                size = 64.dp,
                borderWidth = 2.dp,
                borderColor = AyerGold.copy(alpha = 0.75f),
                fontSize = 24.sp
            )
        }
    }
}
