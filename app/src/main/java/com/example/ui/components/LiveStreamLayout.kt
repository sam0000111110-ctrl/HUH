package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.StreamMessage
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerPrimaryGlow
import com.example.ui.theme.AyerSecondary
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.HuhTheme
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Data model for lightweight floating heart particles on double-tap or like button.
 */
data class FloatingHeart(
    val id: Long = System.nanoTime(),
    val startXRatio: Float = Random.nextFloat() * 0.4f + 0.3f,
    val tintColor: Color = listOf(
        AyerPrimary,
        AyerGold,
        Color(0xFFFF4081),
        Color(0xFFFF1744),
        AyerCyan
    ).random()
)

/**
 * Master Live Streaming Layout composable.
 *
 * Consists of three primary sections according to design specifications:
 * 1. [VideoDisplayContainer]: Video feed / background surface with cinematic gradient overlays.
 * 2. [ViewerCountAndPointsDisplay]: Header overlay with viewer count badge, points / diamond counter, and host info.
 * 3. [ChatOverlay]: Semi-transparent overlay displaying chat messages, pinned banners, and interactive input controls.
 */
@Composable
fun LiveStreamBasicLayout(
    modifier: Modifier = Modifier,
    // Video Container Parameters
    videoImageRes: Int? = null,
    streamTitle: String = "بث مباشر تعارف وتواصل 🌟",
    isLive: Boolean = true,
    streamResolutionText: String = "1080p 60fps HD",
    // Viewer Count & Points Display Parameters
    hostName: String = "سارة أحمد",
    hostCountry: String = "السعودية 🇸🇦",
    viewersCount: Int = 1420,
    pointsCount: Int = 28450,
    userCoinsBalance: Int = 350,
    onCloseStream: () -> Unit = {},
    onFollowHost: () -> Unit = {},
    onTopUpPoints: () -> Unit = {},
    // Chat Overlay Parameters
    messages: List<StreamMessage> = emptyList(),
    pinnedAnnouncement: String? = "📌 مرحباً بكم في البث! تفاعلوا وادعموا المضيف بالنقاط والهدايا",
    onSendMessage: (String) -> Unit = {},
    onSendGiftClick: () -> Unit = {},
    onLikeClick: () -> Unit = {},
    // Broadcaster Control Panel Parameters (Start/Stop stream & Camera switch)
    showControlPanel: Boolean = true,
    isStreaming: Boolean = isLive,
    isFrontCamera: Boolean = true,
    isSwitchingCamera: Boolean = false,
    onStartStream: () -> Unit = {},
    onStopStream: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    // Additional Custom Overlays / Slots
    additionalControls: @Composable (() -> Unit)? = null
) {
    val floatingHearts = remember { mutableStateListOf<FloatingHeart>() }
    var currentIsStreaming by remember(isStreaming) { mutableStateOf(isStreaming) }
    var currentIsFrontCamera by remember(isFrontCamera) { mutableStateOf(isFrontCamera) }

    fun triggerFloatingHeart() {
        if (floatingHearts.size > 20) {
            floatingHearts.removeAt(0)
        }
        floatingHearts.add(FloatingHeart())
        onLikeClick()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("live_stream_layout_root")
    ) {
        // 1. Video Display Container (fills entire background viewport)
        VideoDisplayContainer(
            imageRes = videoImageRes,
            hostName = hostName,
            isLive = currentIsStreaming,
            qualityLabel = streamResolutionText,
            onDoubleTap = { triggerFloatingHeart() },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Hearts Layer
        floatingHearts.forEach { heart ->
            FloatingHeartParticle(
                heart = heart,
                onAnimationComplete = { floatingHearts.remove(heart) }
            )
        }

        // 2. Viewer Count & Points Display (Top Bar Header)
        ViewerCountAndPointsDisplay(
            hostName = hostName,
            hostCountry = hostCountry,
            viewersCount = viewersCount,
            pointsCount = pointsCount,
            userCoinsBalance = userCoinsBalance,
            isLive = currentIsStreaming,
            onClose = onCloseStream,
            onFollow = onFollowHost,
            onPointsClick = onTopUpPoints,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        // 3. Broadcaster Control Panel (Start/Stop stream buttons and camera switch button)
        if (showControlPanel) {
            StreamControlPanel(
                isStreaming = currentIsStreaming,
                isFrontCamera = currentIsFrontCamera,
                isSwitchingCamera = isSwitchingCamera,
                onStartStream = {
                    currentIsStreaming = true
                    onStartStream()
                },
                onStopStream = {
                    currentIsStreaming = false
                    onStopStream()
                },
                onSwitchCamera = {
                    currentIsFrontCamera = !currentIsFrontCamera
                    onSwitchCamera()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 90.dp, end = 12.dp)
            )
        }

        // Middle optional controls (e.g. Broadcaster camera switch, mute, etc.)
        if (additionalControls != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 220.dp, end = 12.dp)
            ) {
                additionalControls()
            }
        }

        // 4. Chat Overlay & Bottom Interaction Deck (Bottom Area)
        ChatOverlay(
            messages = messages,
            pinnedNotice = pinnedAnnouncement,
            onSendMessage = onSendMessage,
            onOpenGifts = onSendGiftClick,
            onLike = { triggerFloatingHeart() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * Section: Broadcaster Control Panel
 *
 * Provides real-time stream management controls for the host:
 * - Start Stream Button ([start_stream_button])
 * - Stop Stream Button ([stop_stream_button])
 * - Camera Switch Button ([switch_camera_button] / [camera_switch_button])
 * - Current broadcast readiness & camera state indicator
 */
@Composable
fun StreamControlPanel(
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    isFrontCamera: Boolean = true,
    isSwitchingCamera: Boolean = false,
    onStartStream: () -> Unit = {},
    onStopStream: () -> Unit = {},
    onSwitchCamera: () -> Unit = {}
) {
    val cameraRotation = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        color = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, AyerPrimary.copy(alpha = 0.45f)),
        shadowElevation = 8.dp,
        modifier = modifier.testTag("stream_control_panel")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stream Live Status Badge
            Surface(
                color = if (isStreaming) Color(0xFFFF1744).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isStreaming) Color(0xFFFF1744).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.testTag("stream_status_indicator")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (isStreaming) Color(0xFFFF1744) else Color.Gray,
                                CircleShape
                            )
                    )
                    Text(
                        text = if (isStreaming) "مباشر" else "جاهز",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isStreaming) Color(0xFFFF5252) else Color.LightGray
                    )
                }
            }

            // 1. Start Stream Button
            StreamControlButton(
                icon = Icons.Default.PlayArrow,
                label = "بدء البث",
                contentDescription = "بدء البث المباشر (Start Stream)",
                isActive = !isStreaming,
                highlightColor = Color(0xFF00E676),
                onClick = onStartStream,
                testTag = "start_stream_button"
            )

            // 2. Stop Stream Button
            StreamControlButton(
                icon = Icons.Default.Stop,
                label = "إيقاف",
                contentDescription = "إيقاف البث المباشر (Stop Stream)",
                isActive = isStreaming,
                highlightColor = Color(0xFFFF1744),
                onClick = onStopStream,
                testTag = "stop_stream_button"
            )

            // 3. Camera Switch Button (Front / Back Flip)
            StreamControlButton(
                icon = Icons.Default.Cameraswitch,
                label = if (isFrontCamera) "أمامية 🤳" else "خلفية 📷",
                contentDescription = if (isFrontCamera) "التبديل إلى الكاميرا الخلفية (Switch Camera)" else "التبديل إلى الكاميرا الأمامية (Switch Camera)",
                isActive = true,
                highlightColor = AyerGold,
                isLoading = isSwitchingCamera,
                iconModifier = Modifier.rotate(cameraRotation.value),
                onClick = {
                    coroutineScope.launch {
                        cameraRotation.animateTo(
                            targetValue = cameraRotation.value + 180f,
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                        )
                    }
                    onSwitchCamera()
                },
                testTag = "switch_camera_button",
                secondaryTag = "camera_switch_button"
            )
        }
    }
}

/**
 * Reusable Circular Control Button for the Stream Control Panel.
 */
@Composable
fun StreamControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    isActive: Boolean,
    highlightColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    secondaryTag: String? = null,
    isLoading: Boolean = false,
    iconModifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.then(if (secondaryTag != null) Modifier.testTag(secondaryTag) else Modifier)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) highlightColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                    CircleShape
                )
                .border(
                    width = 1.5.dp,
                    color = if (isActive) highlightColor else Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                )
                .testTag(testTag)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = highlightColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (isActive) highlightColor else Color.White.copy(alpha = 0.45f),
                    modifier = iconModifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (isActive) Color.White else Color.Gray
        )
    }
}

/**
 * Section 1: Video Display Container
 *
 * Renders the live stream feed, handles background image/canvas,
 * displays connection quality badges, and provides dark cinematic gradient
 * scrims so that top and bottom text overlays remain crystal clear.
 */
@Composable
fun VideoDisplayContainer(
    modifier: Modifier = Modifier,
    imageRes: Int? = null,
    hostName: String = "HUH",
    isLive: Boolean = true,
    qualityLabel: String = "1080p 60fps HD",
    onDoubleTap: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("video_display_container")
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onDoubleTap?.invoke()
                    }
                )
            }
    ) {
        // Video Stream Background (Authentic image or clean M3 ambient surface)
        StreamRoomAmbientBackdrop(
            hostName = hostName,
            imageResId = imageRes,
            modifier = Modifier.fillMaxSize()
        )

        // Quality & Latency Badge in top-left
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 64.dp, start = 14.dp)
                .testTag("stream_quality_badge")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (isLive) Color(0xFF00E676) else Color.Gray, CircleShape)
                )
                Text(
                    text = qualityLabel,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Top Gradient Scrim (for Header readability)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.40f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom Gradient Scrim (for Chat and Controls readability)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )
    }
}

/**
 * Section 2: Viewer Count & Points Display
 *
 * Header component featuring:
 * - Host Profile Pill (Avatar, name, follow button)
 * - LIVE Status Badge & Viewer Count Badge with pulsing live dot
 * - Points / Diamonds Received counter pill
 * - User coin balance / recharge shortcut
 * - Close button
 */
@Composable
fun ViewerCountAndPointsDisplay(
    hostName: String,
    hostCountry: String,
    viewersCount: Int,
    pointsCount: Int,
    userCoinsBalance: Int,
    isLive: Boolean,
    onClose: () -> Unit,
    onFollow: () -> Unit,
    onPointsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = modifier.testTag("viewer_count_and_points_header")
    ) {
        // Top Row: Host Info | Viewer Count | Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Host Information Chip
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, AyerPrimary.copy(alpha = 0.6f)),
                modifier = Modifier.testTag("host_info_chip")
            ) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Host Avatar
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.radialGradient(listOf(AyerPrimary, AyerSecondary)),
                                CircleShape
                            )
                            .border(1.5.dp, AyerGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "👑", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = hostName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = hostCountry,
                            fontSize = 10.sp,
                            color = AyerGold,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Follow Button
                    Button(
                        onClick = onFollow,
                        colors = ButtonDefaults.buttonColors(containerColor = AyerPrimary),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("follow_host_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "متابعة",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Right Group: Viewers Counter Badge & Close Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Viewer Count Badge (with live pulsing dot)
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.testTag("viewer_count_display")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Pulsing Live Indicator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(if (isLive) pulseAlpha else 0.4f)
                                .background(if (isLive) Color(0xFFFF1744) else Color.Gray, CircleShape)
                        )
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "عدد المشاهدين",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = formatNumber(viewersCount),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("viewer_count_text")
                        )
                    }
                }

                // Close Stream Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .testTag("close_stream_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق البث",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Second Row: Points / Diamonds Display & User Coin Balance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stream Diamonds / Support Points Pill
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.5f)),
                modifier = Modifier
                    .clickable(onClick = onPointsClick)
                    .testTag("points_display")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "💎", fontSize = 13.sp)
                    Text(
                        text = "${formatNumber(pointsCount)} نقطة دعم",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AyerGold,
                        modifier = Modifier.testTag("points_text")
                    )
                }
            }

            // User Coins Balance Badge
            Surface(
                color = AyerSurfaceElevated.copy(alpha = 0.85f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.5f)),
                modifier = Modifier
                    .clickable(onClick = onPointsClick)
                    .testTag("user_coins_balance_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "🪙", fontSize = 12.sp)
                    Text(
                        text = "$userCoinsBalance رصيدك",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "+شحن",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AyerCyan
                    )
                }
            }
        }
    }
}

/**
 * Section 3: Chat Overlay
 *
 * Provides the semi-transparent floating live chat feed:
 * - Pinned banner marquee for rules or announcements.
 * - Scrollable chat messages with badges, usernames, and content.
 * - Interactive bottom bar with text input, send, gifts button, and like button.
 */
@Composable
fun ChatOverlay(
    messages: List<StreamMessage>,
    pinnedNotice: String?,
    onSendMessage: (String) -> Unit,
    onOpenGifts: () -> Unit,
    onLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = modifier.testTag("chat_overlay")
    ) {
        // Pinned Announcement Banner
        if (!pinnedNotice.isNullOrBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.70f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 6.dp)
                    .testTag("pinned_announcement_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pinnedNotice,
                        fontSize = 11.sp,
                        color = AyerCyan,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Live Chat Messages Stream
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(180.dp)
                .testTag("chat_messages_list"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages.reversed(), key = { it.id }) { message ->
                StreamChatMessagePill(message = message)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom Interaction Deck (Input field, Send, Gift, Like)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Chat Input Field
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = {
                    Text(
                        text = "أرسل تعليقاً للمضيف... 💬",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(alpha = 0.8f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Black.copy(alpha = 0.65f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.65f),
                    focusedBorderColor = AyerPrimary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("chat_input_field")
            )

            // Send Button
            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSendMessage(textInput.trim())
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(AyerPrimary, CircleShape)
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "إرسال الرسالة",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Gift Button
            IconButton(
                onClick = onOpenGifts,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        Brush.radialGradient(listOf(AyerPrimaryGlow, AyerSecondary)),
                        CircleShape
                    )
                    .border(1.dp, AyerGold, CircleShape)
                    .testTag("stream_gift_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Redeem,
                    contentDescription = "إرسال هدية للمضيف",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Heart / Like Button
            IconButton(
                onClick = onLike,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .border(1.dp, Color(0xFFFF4081).copy(alpha = 0.5f), CircleShape)
                    .testTag("stream_like_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "إرسال إعجاب",
                    tint = Color(0xFFFF4081),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Individual Chat Message Pill component.
 * Features sender level badge, distinct username coloring, and message text.
 */
@Composable
fun StreamChatMessagePill(
    message: StreamMessage,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        message.isSystem -> AyerPrimary.copy(alpha = 0.45f)
        message.isGift -> AyerSecondary.copy(alpha = 0.55f)
        else -> Color.Black.copy(alpha = 0.60f)
    }

    val borderColor = when {
        message.isGift -> AyerGold.copy(alpha = 0.6f)
        message.isSystem -> AyerPrimary.copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.testTag("chat_message_pill_${message.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Tier / Level Badge
            Surface(
                color = if (message.isGift) AyerGold.copy(alpha = 0.25f) else AyerCyan.copy(alpha = 0.25f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Text(
                    text = if (message.isGift) "VIP" else "Lv.7",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (message.isGift) AyerGold else AyerCyan,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            // Sender Name
            Text(
                text = "${message.senderName}: ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (message.isGift) AyerGold else AyerCyan
            )

            // Message Body
            Text(
                text = message.originalText,
                fontSize = 11.sp,
                color = Color.White,
                fontWeight = FontWeight.Normal
            )

            // Gift Emoji if applicable
            if (message.isGift && message.giftEmoji != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = message.giftEmoji, fontSize = 14.sp)
            }
        }
    }
}

/**
 * Floating Heart Particle animation rising gracefully from the bottom right on like/double-tap.
 */
@Composable
fun FloatingHeartParticle(
    heart: FloatingHeart,
    onAnimationComplete: () -> Unit
) {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(0.6f) }

    LaunchedEffect(heart.id) {
        launch {
            scale.animateTo(1.2f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        }
        launch {
            offsetY.animateTo(-300f, animationSpec = tween(1500, easing = LinearEasing))
        }
        launch {
            alpha.animateTo(0f, animationSpec = tween(1500, easing = FastOutSlowInEasing))
            onAnimationComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp, end = 20.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = heart.tintColor,
            modifier = Modifier
                .offset { IntOffset(x = (-heart.startXRatio * 150).toInt(), y = offsetY.value.toInt()) }
                .scale(scale.value)
                .alpha(alpha.value)
                .size(28.dp)
        )
    }
}

/**
 * Utility function to format viewer numbers (e.g. 1420 -> 1.4k)
 */
private fun formatNumber(count: Int): String {
    return when {
        count >= 1_000_000 -> java.lang.String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> java.lang.String.format(java.util.Locale.US, "%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}
