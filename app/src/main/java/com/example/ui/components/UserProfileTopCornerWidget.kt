package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserProfile
import com.example.data.model.currentAgencyLevel
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.HuhTheme

/**
 * High-end User Profile UI widget displayed in the top right corner of the main screen.
 * Displays:
 * 1. User's Avatar with glowing holographic rim and online status indicator
 * 2. Username
 * 3. Total Points balance
 * 4. Current Agency Level
 */
@Composable
fun UserProfileTopCornerWidget(
    userProfile: UserProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("top_right_user_profile"),
        shape = RoundedCornerShape(22.dp),
        color = if (HuhTheme.colors.isDark) Color(0xEE120B24) else Color(0xF5FFFFFF),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    HuhTheme.colors.gold.copy(alpha = 0.55f),
                    HuhTheme.colors.primary.copy(alpha = 0.45f),
                    HuhTheme.colors.tertiary.copy(alpha = 0.4f)
                )
            )
        ),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // Text info column: Username, Agency Level, Total Points
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                // Top row: Username + Current Agency Level badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Current Agency Level mini-badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = HuhTheme.colors.surfaceVariant,
                        border = BorderStroke(0.5.dp, HuhTheme.colors.gold.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = userProfile.currentAgencyLevel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.gold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = userProfile.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 90.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Bottom row: Total Points
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "${userProfile.pointsBalance} 🪙",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = HuhTheme.colors.gold
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "نقطة",
                        fontSize = 10.sp,
                        color = HuhTheme.colors.textSecondary
                    )
                }
            }

            // User's Avatar with glowing rim and online status indicator
            Box(
                modifier = Modifier
                    .size(36.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_user_avatar),
                    contentDescription = "الصورة الشخصية للمستخدم",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(
                            BorderStroke(
                                1.5.dp,
                                Brush.sweepGradient(
                                    listOf(AyerGold, AyerCyan, AyerPrimary, AyerGold)
                                )
                            ),
                            CircleShape
                        ),
                    contentScale = ContentScale.Crop
                )

                // Active online status green dot indicator
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .align(Alignment.BottomEnd)
                        .background(Color(0xFF00E676), CircleShape)
                        .border(1.5.dp, Color(0xFF120B24), CircleShape)
                )
            }
        }
    }
}
