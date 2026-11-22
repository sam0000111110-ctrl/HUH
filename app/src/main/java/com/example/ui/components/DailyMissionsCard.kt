package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DailyMissionItem
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.HuhTheme

@Composable
fun DailyMissionsCard(
    missions: List<DailyMissionItem>,
    onClaimReward: (missionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalMissionsCount = missions.size
    val completedCount = missions.count { it.isCompleted }
    val claimedCount = missions.count { it.isClaimed }
    val overallProgressFraction = if (totalMissionsCount > 0) completedCount.toFloat() / totalMissionsCount else 0f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_missions_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AyerSurfaceElevated),
        border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(AyerCyan.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TaskAlt,
                            contentDescription = null,
                            tint = AyerCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "المهام اليومية والمكافآت 🎯",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = "أكمل الأنشطة واكسب نقاط إضافية يومياً",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textSecondary
                        )
                    }
                }

                Surface(
                    color = AyerGold.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "$completedCount / $totalMissionsCount مكتمل",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AyerGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Overall Progress Track
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "نسبة إنجاز مهام اليوم",
                        fontSize = 11.sp,
                        color = HuhTheme.colors.textSecondary
                    )
                    Text(
                        text = "${(overallProgressFraction * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AyerCyan
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { overallProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = AyerCyan,
                    trackColor = AyerCyan.copy(alpha = 0.15f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Missions Items List
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                missions.forEach { mission ->
                    MissionItemRow(
                        mission = mission,
                        onClaim = { onClaimReward(mission.id) }
                    )
                }
            }

            // Milestone Banner if all completed
            if (completedCount == totalMissionsCount && totalMissionsCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = AyerGold.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = AyerGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "مبروك! تم إتمام جميع مهام اليوم وحصدت كافة المكافآت بنجاح 🏆",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AyerGold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MissionItemRow(
    mission: DailyMissionItem,
    onClaim: () -> Unit
) {
    val iconPair = getMissionIcon(mission.iconName)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mission_item_${mission.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surface),
        border = BorderStroke(
            1.dp,
            if (mission.isClaimed) Color.Transparent
            else if (mission.isCompleted) Color(0xFF10B981).copy(alpha = 0.4f)
            else HuhTheme.colors.outline.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(iconPair.second.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconPair.first,
                            contentDescription = null,
                            tint = iconPair.second,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mission.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = mission.description,
                            fontSize = 10.sp,
                            color = HuhTheme.colors.textSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Reward Badge or Action Button
                if (mission.isClaimed) {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تم الاستلام",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                } else if (mission.isCompleted) {
                    Button(
                        onClick = onClaim,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("claim_mission_${mission.id}")
                    ) {
                        Text(
                            text = "استلام +${mission.rewardPoints} 🎁",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                } else {
                    Surface(
                        color = AyerGold.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "+${mission.rewardPoints} نقطة",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AyerGold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar & Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { mission.progressFraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (mission.isCompleted) Color(0xFF10B981) else AyerCyan,
                    trackColor = HuhTheme.colors.outline.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${mission.currentProgress} / ${mission.targetProgress} ${mission.unit}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HuhTheme.colors.textSecondary
                )
            }
        }
    }
}

fun getMissionIcon(iconName: String): Pair<ImageVector, Color> {
    return when (iconName.lowercase()) {
        "live" -> Pair(Icons.Default.LiveTv, Color(0xFFF43F5E))
        "chat" -> Pair(Icons.Default.Chat, AyerCyan)
        "match" -> Pair(Icons.Default.Radar, AyerPrimary)
        "gift" -> Pair(Icons.Default.CardGiftcard, AyerGold)
        "login" -> Pair(Icons.Default.Login, Color(0xFF10B981))
        else -> Pair(Icons.Default.Stars, AyerGold)
    }
}
