package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.GiftItem
import com.example.data.model.LiveStreamRoom
import com.example.data.model.StreamMessage
import com.example.ui.AyerViewModel
import com.example.ui.components.AnimatedViewerCountBadge
import com.example.ui.components.CachedLiveStreamVideoCard
import com.example.ui.components.CoinBalanceBadge
import com.example.ui.components.FloatingReactionItem
import com.example.ui.components.FloatingReactionsOverlay
import com.example.ui.components.LiveGiftAnimationOverlay
import com.example.ui.components.LiveStreamGlobeBackground
import com.example.ui.components.LiveStreamEmojiPickerSheet
import com.example.ui.components.LiveStreamModerationSheet
import com.example.ui.components.LiveStreamReportDialog
import com.example.ui.components.LiveStreamShareSheet
import com.example.ui.components.QuickEmojiBar
import com.example.ui.components.StandardStreamCategories
import com.example.ui.components.StreamCardSkeleton
import com.example.ui.components.StreamCategory
import com.example.ui.components.StreamCategoryFilterBar
import com.example.ui.components.StreamLiveDurationBadge
import com.example.ui.components.StreamSkeletonGrid
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSecondary
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.AyerSurfaceVariant
import com.example.ui.theme.HuhTheme

@Composable
fun LiveStreamScreen(
    viewModel: AyerViewModel,
    userBalance: Int,
    selectedStream: LiveStreamRoom?,
    streamMessages: List<StreamMessage>,
    activeGift: GiftItem?,
    onNavigateToStore: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedStream != null) {
        val blockedUsers by viewModel.streamBlockedUsers.collectAsStateWithLifecycle()
        val restrictedKeywords by viewModel.streamRestrictedKeywords.collectAsStateWithLifecycle()
        val activeViewers by viewModel.activeStreamViewers.collectAsStateWithLifecycle()
        val flaggedStreamIds by viewModel.flaggedStreamIds.collectAsStateWithLifecycle()
        val isFlagged = flaggedStreamIds.contains(selectedStream.id)

        LiveRoomViewer(
            stream = selectedStream,
            messages = streamMessages,
            activeGift = activeGift,
            userBalance = userBalance,
            gifts = viewModel.getGifts(),
            blockedUsers = blockedUsers,
            restrictedKeywords = restrictedKeywords,
            activeViewers = activeViewers,
            isFlagged = isFlagged,
            onSubmitReport = { reason, details ->
                viewModel.submitStreamReport(
                    streamId = selectedStream.id,
                    streamTitle = selectedStream.title,
                    hostName = selectedStream.hostName,
                    reason = reason,
                    details = details
                )
            },
            onBlockUser = { viewModel.blockStreamUser(it) },
            onUnblockUser = { viewModel.unblockStreamUser(it) },
            onAddKeyword = { viewModel.addRestrictedKeyword(it) },
            onRemoveKeyword = { viewModel.removeRestrictedKeyword(it) },
            onClearChat = { viewModel.clearLiveStreamChat() },
            onClose = { viewModel.closeLiveStream() },
            onSendMessage = { text -> viewModel.sendStreamMessage(text) },
            onSendGift = { gift -> viewModel.sendGiftToStream(gift) },
            onNavigateToStore = onNavigateToStore,
            onStartPrivateCall = { host -> viewModel.startCallSession(host, isVideo = true) }
        )
    } else {
        LiveStreamBrowseList(
            viewModel = viewModel,
            userBalance = userBalance,
            onSelectStream = { stream -> viewModel.openLiveStream(stream) },
            onNavigateToStore = onNavigateToStore,
            modifier = modifier
        )
    }
}

@Composable
fun LiveStreamBrowseList(
    viewModel: AyerViewModel,
    userBalance: Int,
    onSelectStream: (LiveStreamRoom) -> Unit,
    onNavigateToStore: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryId by remember { mutableStateOf("ALL") }
    var isCategoryLoading by remember { mutableStateOf(false) }
    var sharingStream by remember { mutableStateOf<LiveStreamRoom?>(null) }

    val context = LocalContext.current
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var permissionDeniedExplanation by remember { mutableStateOf<String?>(null) }

    val broadcastPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (cameraGranted && audioGranted) {
            viewModel.startUserLiveStream()
        } else {
            permissionDeniedExplanation = when {
                !cameraGranted && !audioGranted -> "يتطلب بدء البث المباشر منح إذن الكاميرا والميكروفون لبث الفيديو والصوت للمشاهدين."
                !cameraGranted -> "يتطلب بدء البث المباشر إذن الكاميرا لبث الفيديو الحي للمشاهدين."
                else -> "يتطلب بدء البث المباشر إذن الميكروفون لبث صوتك بوضوح للمشاهدين."
            }
            showPermissionRationaleDialog = true
        }
    }

    val onStartBroadcastClick = {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted && audioGranted) {
            viewModel.startUserLiveStream()
        } else {
            broadcastPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    val allStreams = viewModel.getLiveStreams()
    val filteredStreams = remember(allStreams, selectedCategoryId) {
        if (selectedCategoryId == "ALL" || selectedCategoryId == "الكل") {
            allStreams
        } else {
            allStreams.filter { room ->
                room.category.equals(selectedCategoryId, ignoreCase = true) ||
                (selectedCategoryId == "Gaming" && (room.category.contains("ألعاب", ignoreCase = true) || room.category.contains("Gaming", ignoreCase = true))) ||
                (selectedCategoryId == "Music" && (room.category.contains("موسيقى", ignoreCase = true) || room.category.contains("Music", ignoreCase = true))) ||
                (selectedCategoryId == "Just Chatting" && (room.category.contains("تعارف", ignoreCase = true) || room.category.contains("سوالف", ignoreCase = true) || room.category.contains("Chat", ignoreCase = true))) ||
                (selectedCategoryId == "Challenges & PK" && (room.category.contains("تحديات", ignoreCase = true) || room.category.contains("PK", ignoreCase = true))) ||
                (selectedCategoryId == "Lifestyle" && (room.category.contains("حياة", ignoreCase = true) || room.category.contains("Lifestyle", ignoreCase = true)))
            }
        }
    }

    LaunchedEffect(selectedCategoryId) {
        isCategoryLoading = true
        delay(220)
        isCategoryLoading = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
    ) {
        // 1. Dynamic Auto-Rotating 3D Earth Globe & Celestial Starfield Background
        LiveStreamGlobeBackground(
            modifier = Modifier.fillMaxSize(),
            globeSize = 300.dp,
            autoRotate = true,
            rotationSpeedDurationMillis = 28000,
            backgroundStreams = filteredStreams.ifEmpty { allStreams },
            onSelectStream = onSelectStream
        )

        // 2. Foreground Live Stream Lobby Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "البث المباشر 🔴",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "مضيفون معتمدون حول العالم عبر الوكالات",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Go Live / Start Stream Button with Camera & Mic Runtime Permission Request
                    Button(
                        onClick = onStartBroadcastClick,
                        colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.primary),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("start_live_broadcast_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ابدأ بثك",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    CoinBalanceBadge(balance = userBalance, onClick = onNavigateToStore)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Modern Category Filtering Bar (Gaming, Music, Just Chatting, etc.)
            StreamCategoryFilterBar(
                selectedCategoryId = selectedCategoryId,
                onCategorySelected = { category ->
                    selectedCategoryId = category.id
                },
                allStreams = allStreams,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Grid of Live Streams or Skeleton Screen Placeholders / Empty State
            if (isCategoryLoading) {
                StreamSkeletonGrid(
                    itemCount = 6,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (filteredStreams.isEmpty()) {
                // Empty Category Fallback
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "📡",
                        fontSize = 42.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "لا توجد بثوث نشطة في هذا القسم حالياً",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "جرب استكشاف أقسام أخرى أو إظهار كافة البثوث المتاحة",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { selectedCategoryId = "ALL" },
                        colors = ButtonDefaults.buttonColors(containerColor = AyerPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("reset_category_filter_button")
                    ) {
                        Text("عرض جميع البثوث", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredStreams, key = { it.id }) { stream ->
                        StreamCard(
                            stream = stream,
                            onClick = { onSelectStream(stream) },
                            onShareClick = { sharingStream = stream }
                        )
                    }
                }
            }
        }

        // Live Stream Social Share Sheet
        if (sharingStream != null) {
            LiveStreamShareSheet(
                stream = sharingStream!!,
                onDismiss = { sharingStream = null }
            )
        }

        // Camera & Microphone Permission Explanation Dialog
        if (showPermissionRationaleDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionRationaleDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = AyerPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "أذونات البث المباشر 🎥🎙️",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        text = permissionDeniedExplanation
                            ?: "يتطلب بثك المباشر منح أذونات الكاميرا والميكروفون لتمكين نقل الفيديو والصوت بجودة عالية لمشاهديك.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionRationaleDialog = false
                            broadcastPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.RECORD_AUDIO
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AyerPrimary),
                        modifier = Modifier.testTag("grant_permissions_dialog_button")
                    ) {
                        Text("منح الأذونات الآن", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showPermissionRationaleDialog = false },
                        modifier = Modifier.testTag("dismiss_permissions_dialog_button")
                    ) {
                        Text("إلغاء", color = Color(0xFF94A3B8))
                    }
                },
                containerColor = AyerSurfaceElevated
            )
        }
    }
}

@Composable
fun StreamCard(
    stream: LiveStreamRoom,
    onClick: () -> Unit,
    onShareClick: (() -> Unit)? = null
) {
    CachedLiveStreamVideoCard(
        stream = stream,
        onClick = onClick,
        modifier = Modifier.testTag("stream_card_${stream.id}"),
        height = 230.dp,
        showMuteBadge = true,
        showDetails = true,
        onShareClick = onShareClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRoomViewer(
    stream: LiveStreamRoom,
    messages: List<StreamMessage>,
    activeGift: GiftItem?,
    userBalance: Int,
    gifts: List<GiftItem>,
    blockedUsers: Set<String> = emptySet(),
    restrictedKeywords: Set<String> = emptySet(),
    activeViewers: List<String> = emptyList(),
    isFlagged: Boolean = false,
    onSubmitReport: (reason: String, details: String) -> Unit = { _, _ -> },
    onBlockUser: (String) -> Unit = {},
    onUnblockUser: (String) -> Unit = {},
    onAddKeyword: (String) -> Unit = {},
    onRemoveKeyword: (String) -> Unit = {},
    onClearChat: () -> Unit = {},
    onClose: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendGift: (GiftItem) -> Unit,
    onNavigateToStore: () -> Unit,
    onStartPrivateCall: (com.example.data.model.MatchCandidate) -> Unit = {}
) {
    var messageInput by remember { mutableStateOf("") }
    var showGiftSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showEmojiSheet by remember { mutableStateOf(false) }
    var showModerationSheet by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var likeCounter by remember { mutableStateOf(1280) }

    // Floating reaction animations state
    var floatingReactions by remember { mutableStateOf<List<FloatingReactionItem>>(emptyList()) }

    fun triggerFloatingReaction(emoji: String) {
        val newItem = FloatingReactionItem(
            id = System.currentTimeMillis() + kotlin.random.Random.nextLong(1000),
            emoji = emoji,
            startOffsetX = kotlin.random.Random.nextFloat() * 180f - 90f,
            driftX = kotlin.random.Random.nextFloat() * 50f - 25f,
            rotation = kotlin.random.Random.nextFloat() * 30f - 15f,
            durationMs = 1900 + kotlin.random.Random.nextInt(400)
        )
        floatingReactions = floatingReactions + newItem
    }

    // Stream Broadcaster & Control States
    var isStreaming by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Video Broadcast Background with Front Camera Mirroring
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = stream.imageResId ?: R.drawable.stream_hero_bg),
                contentDescription = "بث المضيف",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = if (isFrontCamera) -1f else 1f // Mirror camera if front-facing
                    ),
                contentScale = ContentScale.Crop
            )

            // Paused / Stopped Stream Overlay
            if (!isStreaming) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.86f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFD32F2F).copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE53935)),
                            modifier = Modifier.size(84.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.VideocamOff,
                                    contentDescription = null,
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "البث المباشر متوقف حالياً ⏸️",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "تم إيقاف تدفق الفيديو للمشاهدين مؤقتاً.\nانقر على زر 'بدء البث' للاستئناف والظهور للمشاهدين.",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Button(
                            onClick = { isStreaming = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            modifier = Modifier.testTag("resume_stream_center_button")
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("بدء البث المباشر 🔴", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Gradient for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Top Stream Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Host Pill
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AyerPrimary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(AyerPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "⭐", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = stream.hostName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "💎 ${stream.diamondsReceived} نقطة دعم",
                            fontSize = 10.sp,
                            color = AyerGold
                        )
                    }
                }
            }

            // Viewers count, Live Badge, and Close button
            Row(verticalAlignment = Alignment.CenterVertically) {
                // LIVE Badge / STOPPED Badge
                Surface(
                    color = if (isStreaming) Color(0xFFD50000).copy(alpha = 0.85f) else Color(0xFFE65100).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isStreaming) "LIVE" else "متوقف",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Live Duration Badge
                StreamLiveDurationBadge(
                    startedAt = stream.startedAt,
                    fontSize = 11.sp,
                    iconSize = 13.dp
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Viewers Count with smooth incremental animation & real-time feedback
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        AnimatedViewerCountBadge(
                            baseCount = if (isStreaming) stream.viewersCount else 0,
                            enableLiveFluctuation = isStreaming,
                            fontSize = 12.sp,
                            iconSize = 14.dp,
                            showDeltaPopup = true
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Moderation Dashboard Button in Header
                IconButton(
                    onClick = { showModerationSheet = true },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("stream_viewer_moderation_button")
                ) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = "لوحة الإشراف", tint = AyerGold, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Share Stream Button in Header
                IconButton(
                    onClick = { showShareSheet = true },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("stream_viewer_share_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "مشاركة البث", tint = AyerCyan, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                // User-facing Inappropriate Content Report Button
                IconButton(
                    onClick = { showReportDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isFlagged) Color(0xFFF43F5E) else Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .testTag("stream_viewer_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = "إبلاغ عن محتوى غير لائق",
                        tint = if (isFlagged) Color.White else Color(0xFFF43F5E),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق البث", tint = Color.White)
                }
            }
        }

        // Moderation Warning Banner if stream is flagged in Firestore
        if (isFlagged) {
            Surface(
                color = Color(0xFFF43F5E).copy(alpha = 0.92f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 70.dp, start = 16.dp, end = 16.dp)
                    .testTag("stream_flagged_moderation_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⚠️ تم الإبلاغ عن هذا البث وتثبيت علامة مراجعة فورية في Firestore للإشراف الأمني",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Broadcaster / Stream Controls Toolbar (Mute, Flip Camera, Start/Stop Stream Toggle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 74.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Badges (Mic & Camera indicators)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Mic Status Badge
                Surface(
                    color = if (isMuted) Color(0xFFD32F2F).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isMuted) Color.White else AyerCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isMuted) "صامت 🔇" else "مفعل 🎙️",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Camera Flip Status Badge
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = null,
                            tint = AyerGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isFrontCamera) "أمامي 🤳" else "خلفي 📷",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Quick Interactive Controls Group (Flip, Mute, Start/Stop Stream Toggle)
            Surface(
                color = Color.Black.copy(alpha = 0.70f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AyerPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.testTag("stream_control_panel")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Flip Camera Button (switch_camera_button / camera_switch_button)
                    IconButton(
                        onClick = { isFrontCamera = !isFrontCamera },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .testTag("switch_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "تبديل الكاميرا (Flip Camera)",
                            tint = AyerGold,
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("camera_switch_button")
                        )
                    }

                    // 2. Mute / Unmute Button
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isMuted) Color(0xFFD32F2F) else Color.White.copy(alpha = 0.15f),
                                CircleShape
                            )
                            .testTag("stream_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) "تشغيل الصوت (Unmute)" else "كتم الصوت (Mute)",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3. Start Stream Button
                    Button(
                        onClick = { isStreaming = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isStreaming) Color(0xFF00E676) else Color.White.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("start_stream_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "بدء البث",
                                tint = if (!isStreaming) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "بدء",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isStreaming) Color.Black else Color.White
                            )
                        }
                    }

                    // 4. Stop Stream Button
                    Button(
                        onClick = { isStreaming = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStreaming) Color(0xFFFF1744) else Color.White.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("stop_stream_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "إيقاف",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "إيقاف",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Live Chat & Controls at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Live Chat List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                reverseLayout = true
            ) {
                items(messages.reversed()) { msg ->
                    Surface(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        color = if (msg.isSystem) AyerPrimary.copy(alpha = 0.4f)
                        else if (msg.isGift) AyerSecondary.copy(alpha = 0.5f)
                        else Color.Black.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${msg.senderName}: ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (msg.isGift) AyerGold else AyerCyan
                            )
                            Text(
                                text = msg.originalText,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Emoji Floating Bar above input
            QuickEmojiBar(
                onSelectEmoji = { emoji ->
                    messageInput += emoji
                },
                onTriggerReaction = { emoji ->
                    triggerFloatingReaction(emoji)
                },
                onOpenFullPicker = { showEmojiSheet = true },
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Bottom controls: Text input, Gift button, Like button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = { Text("أرسل تعليقاً للمضيف...", fontSize = 12.sp, color = Color.LightGray) },
                    trailingIcon = {
                        IconButton(
                            onClick = { showEmojiSheet = true },
                            modifier = Modifier.size(32.dp).testTag("chat_input_emoji_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mood,
                                contentDescription = "الإيموجي والملصقات",
                                tint = AyerCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.6f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.6f),
                        focusedBorderColor = AyerPrimary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (messageInput.isNotBlank()) {
                            val text = messageInput
                            if (text.contains("🔥")) triggerFloatingReaction("🔥")
                            if (text.contains("❤️") || text.contains("💖")) triggerFloatingReaction("❤️")
                            if (text.contains("😂")) triggerFloatingReaction("😂")
                            if (text.contains("👏")) triggerFloatingReaction("👏")
                            if (text.contains("🎉")) triggerFloatingReaction("🎉")
                            if (text.contains("👑")) triggerFloatingReaction("👑")
                            if (text.contains("🚀")) triggerFloatingReaction("🚀")
                            if (text.contains("😍")) triggerFloatingReaction("😍")
                            if (text.contains("💯")) triggerFloatingReaction("💯")

                            onSendMessage(messageInput)
                            messageInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(AyerPrimary, CircleShape)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال", tint = Color.White, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Video Call Button (Paid Call)
                IconButton(
                    onClick = {
                        val host = com.example.data.model.MatchCandidate(
                            id = "host_${stream.id}",
                            name = stream.hostName,
                            age = 25,
                            country = stream.hostCountry,
                            countryCode = stream.hostCountryCode,
                            gender = "Female",
                            language = "ar",
                            bio = stream.hostBio,
                            interests = listOf(stream.category),
                            imageResId = stream.imageResId
                        )
                        onStartPrivateCall(host)
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                        .testTag("start_paid_call_button")
                ) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = "مكالمة خاصة", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Gift Button (🎁)
                IconButton(
                    onClick = { showGiftSheet = true },
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            brush = Brush.radialGradient(listOf(AyerSecondary, Color(0xFFC2185B))),
                            shape = CircleShape
                        )
                        .testTag("open_gifts_button")
                ) {
                    Icon(imageVector = Icons.Default.Redeem, contentDescription = "إرسال هدية", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Social Share Broadcast Button
                IconButton(
                    onClick = { showShareSheet = true },
                    modifier = Modifier
                        .size(44.dp)
                        .background(AyerCyan.copy(alpha = 0.25f), CircleShape)
                        .testTag("stream_room_share_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "مشاركة البث", tint = AyerCyan, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Like Heart Button
                IconButton(
                    onClick = {
                        likeCounter++
                        triggerFloatingReaction("❤️")
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .testTag("stream_room_like_button")
                ) {
                    Icon(imageVector = Icons.Default.Favorite, contentDescription = "إعجاب", tint = Color(0xFFFF4081), modifier = Modifier.size(22.dp))
                }
            }
        }

        // Floating dynamic animated reaction overlay
        FloatingReactionsOverlay(
            reactions = floatingReactions,
            onReactionFinished = { finishedId ->
                floatingReactions = floatingReactions.filter { it.id != finishedId }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Virtual Gift Animation Overlay on Video Player
        LiveGiftAnimationOverlay(
            activeGift = activeGift,
            senderName = "أنت",
            modifier = Modifier.fillMaxSize()
        )
    }

    // Social Sharing Bottom Sheet for active broadcast
    if (showShareSheet) {
        LiveStreamShareSheet(
            stream = stream,
            onDismiss = { showShareSheet = false }
        )
    }

    // Live Stream Creator Moderation Dashboard Bottom Sheet
    if (showModerationSheet) {
        LiveStreamModerationSheet(
            blockedUsers = blockedUsers,
            restrictedKeywords = restrictedKeywords,
            activeViewers = activeViewers,
            onBlockUser = onBlockUser,
            onUnblockUser = onUnblockUser,
            onAddKeyword = onAddKeyword,
            onRemoveKeyword = onRemoveKeyword,
            onClearChat = onClearChat,
            onDismiss = { showModerationSheet = false }
        )
    }

    // Emoji and Custom Emotes / Stickers Bottom Sheet Picker
    if (showEmojiSheet) {
        LiveStreamEmojiPickerSheet(
            onSendEmote = { emote ->
                val emojiToSend = emote.text.take(4).trim()
                triggerFloatingReaction(emojiToSend.ifBlank { "✨" })
                onSendMessage(emote.text)
            },
            onInsertTextEmoji = { emoji ->
                messageInput += emoji
                triggerFloatingReaction(emoji)
            },
            onDismiss = { showEmojiSheet = false }
        )
    }

    // Gift Selection Bottom Sheet
    if (showGiftSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGiftSheet = false },
            containerColor = Color(0xFF160F29),
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎁 متجر هدايا البث",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    CoinBalanceBadge(balance = userBalance, onClick = {
                        showGiftSheet = false
                        onNavigateToStore()
                    })
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Gifts Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(220.dp)
                ) {
                    items(gifts) { gift ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    triggerFloatingReaction(gift.emoji)
                                    onSendGift(gift)
                                    showGiftSheet = false
                                }
                                .testTag("gift_${gift.id}"),
                            colors = CardDefaults.cardColors(containerColor = AyerSurfaceElevated),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AyerGold.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = gift.emoji, fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = gift.name,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${gift.pointsCost} 🪙",
                                    fontSize = 12.sp,
                                    color = AyerGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // User-facing Moderation Reporting Dialog
    if (showReportDialog) {
        LiveStreamReportDialog(
            streamId = stream.id,
            streamTitle = stream.title,
            hostName = stream.hostName,
            onSubmitReport = { reason, details ->
                onSubmitReport(reason, details)
            },
            onDismiss = { showReportDialog = false }
        )
    }
}
