package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.calls.CallLifecycleState
import com.example.calls.CallLiveKitEngine
import com.example.calls.CallSubtitleSpeechHelper
import com.example.ui.AyerViewModel
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSecondary
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.HuhTheme
import io.livekit.android.renderer.TextureViewRenderer
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private const val TAG = "CallScreen"

/**
 * WebRTCVideoRenderer is an AndroidView Composable that hosts a WebRTC [SurfaceViewRenderer].
 * Manages the hardware EGL context initialization, track sinking/unsinking, mirroring,
 * and lifecycle release without leaking resources.
 */
@Composable
fun WebRTCVideoRenderer(
    videoTrack: VideoTrack?,
    eglContext: EglBase.Context?,
    modifier: Modifier = Modifier,
    isMirror: Boolean = false,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL
) {
    val currentTrack = rememberUpdatedState(videoTrack)
    val currentEglContext = rememberUpdatedState(eglContext)
    var surfaceRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                try {
                    val contextToUse = currentEglContext.value
                    if (contextToUse != null) {
                        init(contextToUse, null)
                        setEnableHardwareScaler(true)
                        setScalingType(scalingType)
                        setMirror(isMirror)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to initialize SurfaceViewRenderer: ${e.message}")
                }
                surfaceRenderer = this
                try {
                    currentTrack.value?.addSink(this)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to add initial sink: ${e.message}")
                }
            }
        },
        update = { renderer ->
            try {
                renderer.setMirror(isMirror)
                renderer.setScalingType(scalingType)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to update SurfaceViewRenderer: ${e.message}")
            }
        },
        onRelease = { renderer ->
            try {
                currentTrack.value?.removeSink(renderer)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to remove sink on release: ${e.message}")
            }
            try {
                renderer.release()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to release SurfaceViewRenderer: ${e.message}")
            }
            surfaceRenderer = null
        },
        modifier = modifier
    )

    // Dynamically manage track sink attachment when track or renderer changes
    DisposableEffect(videoTrack, surfaceRenderer) {
        val renderer = surfaceRenderer
        if (renderer != null && videoTrack != null) {
            try {
                videoTrack.addSink(renderer)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to rebind video track sink: ${e.message}")
            }
        }
        onDispose {
            if (renderer != null && videoTrack != null) {
                try {
                    videoTrack.removeSink(renderer)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to unbind video track sink: ${e.message}")
                }
            }
        }
    }
}

@Composable
fun LiveKitVideoRenderer(
    videoTrack: io.livekit.android.room.track.VideoTrack?,
    engine: CallLiveKitEngine?,
    modifier: Modifier = Modifier,
    isMirror: Boolean = false
) {
    var textureRenderer by remember { mutableStateOf<TextureViewRenderer?>(null) }
    val currentTrack = rememberUpdatedState(videoTrack)

    AndroidView(
        factory = { ctx ->
            TextureViewRenderer(ctx).apply {
                try {
                    engine?.initRenderer(this)
                    setMirror(isMirror)
                } catch (e: Throwable) {
                    Log.w(TAG, "TextureViewRenderer init skipped: ${e.message}")
                }
                textureRenderer = this
            }
        },
        update = { renderer ->
            try {
                renderer.setMirror(isMirror)
            } catch (_: Throwable) {}
        },
        onRelease = { renderer ->
            try {
                currentTrack.value?.removeRenderer(renderer)
                renderer.release()
            } catch (_: Throwable) {}
            textureRenderer = null
        },
        modifier = modifier
    )

    DisposableEffect(videoTrack, textureRenderer) {
        val renderer = textureRenderer
        if (renderer != null && videoTrack != null) {
            try {
                videoTrack.addRenderer(renderer)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to add LiveKit renderer: ${e.message}")
            }
        }
        onDispose {
            if (renderer != null && videoTrack != null) {
                try {
                    videoTrack.removeRenderer(renderer)
                } catch (_: Throwable) {}
            }
        }
    }
}

/**
 * CallScreen Composable:
 * Displays local and remote video streams using LiveKit [TextureViewRenderer] (or WebRTC [SurfaceViewRenderer] fallback)
 * and provides full 1-on-1 call controls.
 */
@Composable
fun CallScreen(
    localVideoTrack: VideoTrack? = null,
    remoteVideoTrack: VideoTrack? = null,
    eglContext: EglBase.Context? = null,
    livekitLocalVideoTrack: io.livekit.android.room.track.VideoTrack? = null,
    livekitRemoteVideoTrack: io.livekit.android.room.track.VideoTrack? = null,
    livekitEngine: CallLiveKitEngine? = null,
    participantName: String = "المتصل",
    participantInfo: String = "مكالمة 1-on-1 مشفرة",
    participantAvatarRes: Int? = null,
    callDurationSeconds: Int = 0,
    callState: CallLifecycleState = CallLifecycleState.ACTIVE,
    isNetworkWeak: Boolean = false,
    downgradedToAudio: Boolean = false,
    statusHint: String? = null,
    permissionWarning: String? = null,
    isAudioEnabled: Boolean = true,
    isCameraEnabled: Boolean = true,
    isSpeakerEnabled: Boolean = true,
    isFrontCamera: Boolean = true,
    isSwitchingCamera: Boolean = false,
    isCameraSwitchInProgress: Boolean = isSwitchingCamera,
    isTorchOn: Boolean = false,
    isTranslationEnabled: Boolean = false,
    liveSubtitles: String? = null,
    onToggleAudio: () -> Unit = {},
    onToggleCamera: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onToggleTorch: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onToggleTranslation: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onDisposeCallResources: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val callContext = LocalContext.current
    var controlsVisible by remember { mutableStateOf(true) }
    val currentDisposeCallResources by rememberUpdatedState(onDisposeCallResources)

    // Properly dispose call & hardware resources and enforce FLAG_SECURE while CallScreen is active
    DisposableEffect(Unit) {
        val activity = com.example.util.SecurityUtils.findActivity(callContext)
        com.example.util.SecurityUtils.setScreenCaptureProtection(activity, true)
        onDispose {
            com.example.util.SecurityUtils.setScreenCaptureProtection(activity, false)
            try {
                currentDisposeCallResources()
            } catch (e: Throwable) {
                Log.e(TAG, "Error disposing call resources on leaving composition: ${e.message}")
            }
        }
    }

    // Pulsing animation for audio/video-off placeholders
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val switching = isSwitchingCamera || isCameraSwitchInProgress

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HuhTheme.colors.background)
            .testTag("call_screen")
    ) {
        // Tappable canvas backdrop layer that toggles controls visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    controlsVisible = !controlsVisible
                }
        )

        // ==========================================
        // 1. REMOTE VIDEO STREAM (Full Screen Canvas)
        // ==========================================
        if (livekitRemoteVideoTrack != null) {
            LiveKitVideoRenderer(
                videoTrack = livekitRemoteVideoTrack,
                engine = livekitEngine,
                isMirror = false,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("remote_video_renderer")
            )
        } else if (remoteVideoTrack != null) {
            WebRTCVideoRenderer(
                videoTrack = remoteVideoTrack,
                eglContext = eglContext,
                isMirror = false,
                scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("remote_video_renderer")
            )
        } else {
            // Elegant placeholder when remote video stream is audio-only, dialing, or reconnecting
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF28114B),
                                Color(0xFF0C0519),
                                Color(0xFF07040E)
                            )
                        )
                    )
                    .testTag("remote_video_renderer"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(AyerPrimary.copy(alpha = 0.16f))
                )
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(pulseScale * 0.95f)
                        .clip(CircleShape)
                        .background(AyerGold.copy(alpha = 0.20f))
                )

                com.example.ui.components.UserInitialAvatar(
                    name = participantName,
                    avatarResId = participantAvatarRes,
                    size = 110.dp,
                    borderWidth = 3.dp,
                    borderColor = AyerGold,
                    fontSize = 32.sp
                )

                val centerStatus = when (callState) {
                    CallLifecycleState.DIALING -> "جاري تجهيز الاتصال الآمن... 🔒"
                    CallLifecycleState.RINGING_OUTGOING -> "يرن الآن... في انتظار الرد 🔔"
                    CallLifecycleState.RECONNECTING -> "انقطع الإنترنت — جاري إعادة الاتصال تلقائياً... 📶"
                    else -> statusHint ?: "مكالمة LiveKit مشفرة 1-on-1 🔒"
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 148.dp, start = 24.dp, end = 24.dp)
                ) {
                    if (downgradedToAudio) {
                        Surface(
                            color = AyerGold.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "تم التحويل التلقائي لصوت فقط حسب إعدادات الإدارة",
                                color = AyerGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    if (!permissionWarning.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFFD50000).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = permissionWarning,
                                color = Color(0xFFFFCDD2),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = centerStatus,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (callState == CallLifecycleState.RECONNECTING) Color(0xFFFFCA28) else AyerCyan,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Ambient gradient overlays for readable text & controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        // ==========================================
        // 2. LOCAL VIDEO STREAM (Picture-in-Picture)
        // ==========================================
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 16.dp)
                .size(width = 112.dp, height = 158.dp)
                .shadow(12.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .border(
                    BorderStroke(
                        2.dp,
                        Brush.linearGradient(listOf(AyerCyan, AyerGold, AyerPrimary))
                    ),
                    RoundedCornerShape(18.dp)
                )
                .background(Color(0xFF1B0E30))
                .clickable(enabled = !isSwitchingCamera, onClickLabel = "تبديل الكاميرا") { onSwitchCamera() }
                .testTag("local_video_container")
        ) {
            if (isCameraEnabled && livekitLocalVideoTrack != null) {
                LiveKitVideoRenderer(
                    videoTrack = livekitLocalVideoTrack,
                    engine = livekitEngine,
                    isMirror = isFrontCamera,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("local_video_renderer")
                )
            } else if (isCameraEnabled && localVideoTrack != null) {
                WebRTCVideoRenderer(
                    videoTrack = localVideoTrack,
                    eglContext = eglContext,
                    isMirror = isFrontCamera,
                    scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("local_video_renderer")
                )
            } else {
                // Camera Off placeholder
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .testTag("local_video_renderer"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = "الكاميرا متوقفة",
                        tint = AyerGold,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "الكاميرا مغلقة",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Quick Camera Switch button on top of PiP window (disabled while switching)
            IconButton(
                onClick = onSwitchCamera,
                enabled = !switching,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(26.dp)
                    .background(
                        if (switching) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.6f),
                        CircleShape
                    )
                    .testTag("pip_switch_camera_button")
            ) {
                if (switching) {
                    CircularProgressIndicator(
                        color = AyerGold,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(13.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = if (isFrontCamera) "التبديل إلى الكاميرا الخلفية" else "التبديل إلى الكاميرا الأمامية",
                        tint = AyerGold,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Quick Torch/Flash button (visible only when rear camera is active)
            if (!isFrontCamera) {
                IconButton(
                    onClick = onToggleTorch,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .background(
                            if (isTorchOn) AyerGold else Color.Black.copy(alpha = 0.6f),
                            CircleShape
                        )
                        .testTag("pip_torch_button")
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = if (isTorchOn) "إيقاف الفلاش" else "تشغيل الفلاش",
                        tint = if (isTorchOn) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Local pip badge indicator
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isFrontCamera) "أنت (أمامية) 🤳" else "أنت (خلفية) 🔄",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ==========================================
        // 3. TOP INFO HEADER BAR
        // ==========================================
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = participantName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = AyerGold.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = AyerGold,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "WebRTC مشفر",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AyerGold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val signalColor = when {
                            callState == CallLifecycleState.RECONNECTING || isNetworkWeak -> Color(0xFFFFB300)
                            callState == CallLifecycleState.ACTIVE -> Color(0xFF00E676)
                            else -> AyerCyan
                        }
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = null,
                            tint = signalColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val stateLabel = when (callState) {
                            CallLifecycleState.DIALING -> "جاري الاتصال..."
                            CallLifecycleState.RINGING_OUTGOING -> "يرن الآن..."
                            CallLifecycleState.RECONNECTING -> "إعادة الاتصال..."
                            CallLifecycleState.ACTIVE -> if (isNetworkWeak) "شبكة ضعيفة • حماية الصوت" else participantInfo
                            else -> participantInfo
                        }
                        Text(
                            text = stateLabel,
                            fontSize = 12.sp,
                            color = Color(0xFFCCCCCC)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onToggleTranslation,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (isTranslationEnabled) AyerCyan.copy(alpha = 0.25f)
                                else Color.White.copy(alpha = 0.14f)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "ترجمة AI الفورية",
                            tint = if (isTranslationEnabled) AyerCyan else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Call Duration Counter Badge
                    Surface(
                        color = Color(0xFF1E1338).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.5f))
                    ) {
                        val minutes = callDurationSeconds / 60
                        val seconds = callDurationSeconds % 60
                        val badgeText = when (callState) {
                            CallLifecycleState.DIALING, CallLifecycleState.RINGING_OUTGOING -> "يرن 🔔"
                            CallLifecycleState.RECONNECTING -> "مؤقت متوقف ⏸"
                            else -> String.format("%02d:%02d", minutes, seconds)
                        }
                        Text(
                            text = badgeText,
                            color = if (callState == CallLifecycleState.RECONNECTING) Color(0xFFFFCA28) else AyerCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("call_duration_badge")
                        )
                    }
                }
            }
        }

        if (isTranslationEnabled && !liveSubtitles.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 124.dp, start = 20.dp, end = 20.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = AyerCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = liveSubtitles,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ==========================================
        // 4. BOTTOM FLOATING CONTROLS DOCK
        // ==========================================
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color(0xF0140A28),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                AyerCyan.copy(alpha = 0.4f),
                                AyerGold.copy(alpha = 0.4f),
                                AyerPrimary.copy(alpha = 0.4f)
                            )
                        )
                    ),
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Audio Toggle (Mute / Unmute)
                        CallControlButton(
                            icon = if (isAudioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            label = if (isAudioEnabled) "كتم" else "تشغيل",
                            contentDescription = "تبديل الميكروفون",
                            isActive = isAudioEnabled,
                            isAlert = !isAudioEnabled,
                            onClick = onToggleAudio,
                            testTag = "toggle_audio_button"
                        )

                        // 2. Camera Toggle (Video On / Off)
                        CallControlButton(
                            icon = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            label = if (isCameraEnabled) "إيقاف" else "تشغيل",
                            contentDescription = "تبديل الكاميرا",
                            isActive = isCameraEnabled,
                            isAlert = !isCameraEnabled,
                            onClick = onToggleCamera,
                            testTag = "toggle_camera_button"
                        )

                        // 3. Prominent Red End Call Button (Hero Action with WebRTC Hardware Cleanup)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(72.dp)
                            ) {
                                // Outer subtle pulse ring
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF1744).copy(alpha = 0.22f))
                                )

                                Surface(
                                    onClick = {
                                        try {
                                            onDisposeCallResources()
                                        } catch (e: Throwable) {
                                            Log.e(TAG, "Cleanup failed on End Call: ${e.message}")
                                        }
                                        onEndCall()
                                    },
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    border = BorderStroke(2.dp, Color(0xFFFF5252).copy(alpha = 0.85f)),
                                    shadowElevation = 10.dp,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("end_call_button")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        Color(0xFFFF334B),
                                                        Color(0xFFD50000),
                                                        Color(0xFF8B0000)
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CallEnd,
                                            contentDescription = "إنهاء المكالمة وإغلاق الاتصال",
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "إنهاء",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252)
                            )
                        }

                        // 4. Switch Camera (Front / Back Flip)
                        CallControlButton(
                            icon = Icons.Default.Cameraswitch,
                            label = if (isFrontCamera) "خلفية" else "أمامية",
                            contentDescription = if (isFrontCamera) "التبديل إلى الكاميرا الخلفية" else "التبديل إلى الكاميرا الأمامية",
                            isActive = true,
                            isAlert = false,
                            enabled = !switching,
                            isLoading = switching,
                            onClick = onSwitchCamera,
                            testTag = "switch_camera_button"
                        )

                        // 5. Speakerphone Toggle
                        CallControlButton(
                            icon = if (isSpeakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            label = if (isSpeakerEnabled) "مكبر" else "سماعة",
                            contentDescription = "تبديل مكبر الصوت",
                            isActive = isSpeakerEnabled,
                            isAlert = false,
                            onClick = onToggleSpeaker,
                            testTag = "toggle_speaker_button"
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable Circular Control Button for the Call Screen Bottom Deck.
 */
@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    isActive: Boolean,
    isAlert: Boolean,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val buttonBg = when {
            !enabled -> Color.White.copy(alpha = 0.05f)
            isAlert -> Color(0xFFE53935)
            isActive -> Color.White.copy(alpha = 0.16f)
            else -> Color.White.copy(alpha = 0.08f)
        }
        val iconTint = when {
            !enabled -> Color.Gray
            isAlert -> Color.White
            isActive -> Color.White
            else -> Color.LightGray
        }

        IconButton(
            onClick = onClick,
            enabled = enabled && !isLoading,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(buttonBg)
                .minimumInteractiveComponentSize()
                .testTag(testTag)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = AyerGold
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                !enabled -> Color.Gray
                isAlert -> Color(0xFFFF8A80)
                else -> Color(0xFFDDDDDD)
            }
        )
    }
}

/**
 * Convenient overload of [CallScreen] that automatically binds to [AyerViewModel]
 * and requests runtime CAMERA / RECORD_AUDIO permissions safely.
 */
@Composable
fun CallScreen(
    viewModel: AyerViewModel,
    onEndCall: () -> Unit = { viewModel.endCallSession() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val callSession by viewModel.activeCallSession.collectAsStateWithLifecycle()
    val localTrack by viewModel.localVideoTrack.collectAsStateWithLifecycle()
    val remoteTrack by viewModel.remoteVideoTrack.collectAsStateWithLifecycle()
    val livekitLocalTrack by viewModel.livekitLocalVideoTrack.collectAsStateWithLifecycle()
    val livekitRemoteTrack by viewModel.livekitRemoteVideoTrack.collectAsStateWithLifecycle()
    val eglContext = viewModel.eglBaseContext
    val isCameraSwitchInProgress by viewModel.isCameraSwitchInProgress.collectAsStateWithLifecycle()

    var permissionWarning by remember { mutableStateOf<String?>(null) }

    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micOk = grants[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val camOk = grants[Manifest.permission.CAMERA]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)

        permissionWarning = when {
            !micOk -> "صلاحية الميكروفون مرفوضة — يرجى تفعيلها من الإعدادات ليسمعك الطرف الآخر."
            callSession?.isVideo == true && !camOk -> "صلاحية الكاميرا مرفوضة — تستمر المكالمة بالصوت فقط."
            else -> null
        }
    }

    LaunchedEffect(callSession?.callId, callSession?.isVideo) {
        val current = callSession ?: return@LaunchedEffect
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (current.isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.CAMERA)
            }
        }
        if (needed.isNotEmpty()) {
            callPermissionsLauncher.launch(needed.toTypedArray())
        } else {
            permissionWarning = null
        }
    }

    val speechHelper = remember(context) {
        CallSubtitleSpeechHelper(context) { spokenText ->
            viewModel.translateCallSubtitle(spokenText)
        }
    }
    DisposableEffect(Unit) {
        onDispose { speechHelper.stop() }
    }

    val activeSession = callSession
    DisposableEffect(
        activeSession?.callId,
        activeSession?.callState,
        activeSession?.isTranslationEnabled,
        activeSession?.isMuted,
        permissionWarning
    ) {
        val shouldCaptureSpeech = activeSession != null &&
            activeSession.callState == CallLifecycleState.ACTIVE &&
            activeSession.isTranslationEnabled &&
            !activeSession.isMuted &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (shouldCaptureSpeech) {
            speechHelper.start()
        } else {
            speechHelper.stop()
        }
        onDispose {
            speechHelper.stop()
        }
    }

    BackHandler(enabled = callSession != null) {
        onEndCall()
    }

    val session = callSession
    if (session != null) {
        val isSwitching = session.isSwitchingCamera || session.isCameraSwitchInProgress || isCameraSwitchInProgress
        CallScreen(
            localVideoTrack = localTrack,
            remoteVideoTrack = remoteTrack,
            eglContext = eglContext,
            livekitLocalVideoTrack = livekitLocalTrack,
            livekitRemoteVideoTrack = livekitRemoteTrack,
            livekitEngine = viewModel.callLiveKitEngine,
            participantName = session.participantName,
            participantInfo = "${session.participantCountry} • ${if (session.isVideo) "فيديو 1-on-1" else "صوت 1-on-1"}",
            participantAvatarRes = session.participantAvatarRes,
            callDurationSeconds = session.durationSeconds,
            callState = session.callState,
            isNetworkWeak = session.isNetworkWeak,
            downgradedToAudio = session.downgradedToAudio,
            statusHint = session.statusHint,
            permissionWarning = permissionWarning,
            isAudioEnabled = !session.isMuted,
            isCameraEnabled = session.isCameraEnabled,
            isSpeakerEnabled = session.isSpeakerOn,
            isFrontCamera = session.isFrontCamera,
            isSwitchingCamera = isSwitching,
            isCameraSwitchInProgress = isSwitching,
            isTorchOn = session.isTorchOn,
            isTranslationEnabled = session.isTranslationEnabled,
            liveSubtitles = session.liveSubtitles,
            onToggleAudio = { viewModel.toggleCallMute() },
            onToggleCamera = { viewModel.toggleCallCamera() },
            onSwitchCamera = { viewModel.toggleCameraFlip() },
            onToggleTorch = { viewModel.toggleCallTorch() },
            onToggleSpeaker = { viewModel.toggleCallSpeaker() },
            onToggleTranslation = { viewModel.toggleCallTranslation() },
            onEndCall = onEndCall,
            onDisposeCallResources = { viewModel.disposeWebRTCResources() },
            modifier = modifier
        )
    }
}
