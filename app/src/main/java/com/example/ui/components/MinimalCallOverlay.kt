package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ActiveCallSession
import com.example.data.model.CallQualityMode
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSurfaceElevated
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalCallOverlay(
    callSession: ActiveCallSession,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleCameraFlip: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSetQualityMode: (CallQualityMode) -> Unit,
    onToggleAudioPriority: () -> Unit,
    onToggleTranslation: () -> Unit,
    onEndCall: () -> Unit
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var showAdvancedSettingsSheet by remember { mutableStateOf(false) }

    // Auto-hide controls after 5 seconds of inactivity
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(5000)
            controlsVisible = false
        }
    }

    // Audio pulse animation for when camera is turned off or voice-only call
    val infiniteTransition = rememberInfiniteTransition(label = "audio_ripple")
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ripple"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tapping anywhere toggles controls
                controlsVisible = !controlsVisible
            }
            .testTag("minimal_call_screen")
    ) {
        // Video View or Audio/Camera-Off Placeholder
        if (callSession.isCameraEnabled && !callSession.isNetworkWeak) {
            // Main participant placeholder until live remote video track attaches
            StreamRoomAmbientBackdrop(
                hostName = callSession.participantName,
                imageResId = callSession.participantAvatarRes,
                modifier = Modifier.fillMaxSize()
            )

            // Self view preview (Picture in Picture in top-left)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 90.dp, start = 16.dp)
                    .size(width = 100.dp, height = 140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .background(Color(0xFF201A30)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "الكاميرا الخاصة بي",
                    tint = AyerGold,
                    modifier = Modifier.size(30.dp)
                )
                Text(
                    text = if (callSession.isFrontCamera) "أمامي" else "خلفي",
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Camera is OFF or network is weak -> audio soundwave ripple around avatar!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF28114B),
                                Color(0xFF0D0418)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Outer pulsating ripple ring
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(rippleScale)
                        .clip(CircleShape)
                        .background(AyerPrimary.copy(alpha = 0.18f))
                )

                // Middle ripple ring
                Box(
                    modifier = Modifier
                        .size(135.dp)
                        .clip(CircleShape)
                        .background(AyerGold.copy(alpha = 0.22f))
                )

                // Central Avatar
                UserInitialAvatar(
                    name = callSession.participantName,
                    avatarResId = callSession.participantAvatarRes,
                    size = 110.dp,
                    borderWidth = 3.dp,
                    borderColor = AyerGold,
                    fontSize = 32.sp
                )
            }
        }

        // Live Call Translated Subtitles (Centered bottom above controls)
        val subtitlesText = callSession.liveSubtitles
        if (callSession.isTranslationEnabled && !subtitlesText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 20.dp, end = 20.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
                    .border(1.dp, AyerGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = AyerGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = subtitlesText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // TOP OVERLAY: Call status, timer, participant info, and LiveKit SFU Bitrate indicator
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Participant info & Duration
                    Column {
                        Text(
                            text = "${callSession.participantName} (${callSession.participantAge})",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${callSession.participantCountry} • ",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                            val minutes = callSession.durationSeconds / 60
                            val seconds = callSession.durationSeconds % 60
                            Text(
                                text = String.format("%02d:%02d", minutes, seconds),
                                color = AyerGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = AyerGold.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${callSession.pointsCostPerMinute} 🪙/د",
                                    color = AyerGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // More Options (⋯) Button for progressive disclosure
                    IconButton(
                        onClick = { showAdvancedSettingsSheet = true },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .testTag("call_more_options_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "خيارات المكالمة",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Network & Adaptive Bitrate Status Banner
                val networkText = when {
                    callSession.isNetworkWeak -> "⚠️ ضعف اتصال: تم خفض الفيديو وحفظ صفاء الصوت (Audio Priority) ⚡"
                    callSession.qualityMode == CallQualityMode.DATA_SAVER -> "📶 وضع توفير البيانات: جودة 360p منخفضة الباندويدث"
                    callSession.qualityMode == CallQualityMode.HIGH_DEFINITION -> "💎 جودة فائقة: 1080p Full HD"
                    else -> "⚡ LiveKit SFU: تكيف تلقائي ذكي (Adaptive Simulcast)"
                }

                Surface(
                    color = if (callSession.isNetworkWeak) Color(0xFFC62828) else Color(0xFF1E1338).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (callSession.isNetworkWeak) Color.Yellow else AyerGold.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = null,
                            tint = if (callSession.isNetworkWeak) Color.Yellow else AyerGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = networkText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // BOTTOM OVERLAY: The 5 Essential Controls (Mute, Camera, Flip, Speaker, End Call)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(bottom = 36.dp, start = 20.dp, end = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Mute / Unmute
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (callSession.isMuted) Color(0xFFE53935) else Color.White.copy(alpha = 0.22f),
                                CircleShape
                            )
                            .testTag("call_mute_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (callSession.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "كتم الصوت",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // 2. Camera On / Off
                    IconButton(
                        onClick = onToggleCamera,
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (!callSession.isCameraEnabled) Color(0xFFE53935) else Color.White.copy(alpha = 0.22f),
                                CircleShape
                            )
                            .testTag("call_camera_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (callSession.isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "الكاميرا",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // 4. END CALL (Prominent Red Button in Center)
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color(0xFFD32F2F), CircleShape)
                            .testTag("end_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "إنهاء المكالمة",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // 3. Flip Camera (Front / Back)
                    IconButton(
                        onClick = onToggleCameraFlip,
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.White.copy(alpha = 0.22f), CircleShape)
                            .testTag("call_camera_flip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "قلب الكاميرا",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // 5. Speaker / Earpiece
                    IconButton(
                        onClick = onToggleSpeaker,
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (callSession.isSpeakerOn) AyerGold else Color.White.copy(alpha = 0.22f),
                                CircleShape
                            )
                            .testTag("call_speaker_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (callSession.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            contentDescription = "مكبر الصوت",
                            tint = if (callSession.isSpeakerOn) Color.Black else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Advanced Settings (Progressive Disclosure)
    if (showAdvancedSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAdvancedSettingsSheet = false },
            containerColor = AyerSurfaceElevated,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "إعدادات المكالمة المتقدمة (LiveKit SFU)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "تحكم في استهلاك البيانات وجودة البث الصوتي والمرئي",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quality Modes Selector
                Text(text = "وضع جودة البث الفيديو:", color = AyerGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        Triple(CallQualityMode.ADAPTIVE_SMART, "تلقائي ذكي ⚡", "Simulcast"),
                        Triple(CallQualityMode.DATA_SAVER, "توفير بيانات 📶", "360p"),
                        Triple(CallQualityMode.HIGH_DEFINITION, "فائق الدقة 💎", "1080p")
                    )
                    modes.forEach { (mode, title, label) ->
                        val isSelected = callSession.qualityMode == mode
                        Card(
                            onClick = { onSetQualityMode(mode) },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) AyerPrimary else Color.Black.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) AyerGold else Color.Gray.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    color = if (isSelected) AyerGold else Color.Gray
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Audio Priority Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "أولوية الصوت عند ضعف الشبكة", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(text = "يضمن استمرار الصوت بدون تقطيع حتى لو توقف الفيديو", color = Color.LightGray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = callSession.isAudioPriorityWhenWeak,
                        onCheckedChange = { onToggleAudioPriority() },
                        colors = SwitchDefaults.colors(checkedThumbColor = AyerGold)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Translation Subtitles Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "الترجمة الفورية للمكالمة (Live Captions)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(text = "ترجمة حية فورية تظهر أسفل الشاشة للطرفين", color = Color.LightGray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = callSession.isTranslationEnabled,
                        onCheckedChange = { onToggleTranslation() },
                        colors = SwitchDefaults.colors(checkedThumbColor = AyerPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
