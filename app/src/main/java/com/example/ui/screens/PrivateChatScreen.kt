package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.ui.components.SmoothDropdownMenu
import com.example.ui.components.SmoothDropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.R
import com.example.data.model.FriendItem
import com.example.data.model.PrivateChatMessage
import com.example.ui.AyerViewModel
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSecondary
import com.example.ui.theme.HuhTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatScreen(
    friend: FriendItem,
    viewModel: AyerViewModel,
    messages: List<PrivateChatMessage>,
    onBack: () -> Unit
) {
    val isDark = HuhTheme.colors.isDark
    var textInput by remember { mutableStateOf("") }
    var selectedTextColor by remember { mutableStateOf(if (isDark) "#FFFFFF" else "#0F172A") }
    var selectedBgColor by remember { mutableStateOf(if (isDark) "#1E1B2E" else "#F1F5F9") }
    var chatWallpaperColor by remember(isDark) { 
        mutableStateOf(if (isDark) Color(0xFF07040E) else Color(0xFFF8FAFC)) 
    }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showMediaSendDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Derived state: tracks whether the user has scrolled up away from the bottom
    val isScrolledUp by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 1) {
                false
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleIndex < totalItems - 1
            }
        }
    }

    // Auto-scroll feature: keeps the latest messages in view
    // Automatically scrolls to the bottom on first load and when new messages arrive (unless user is reading history scrolled up)
    var isFirstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (isFirstLoad) {
                listState.scrollToItem(messages.size - 1)
                isFirstLoad = false
            } else if (!isScrolledUp) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(HuhTheme.colors.primary, HuhTheme.colors.secondary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = friend.name.take(1),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = friend.name,
                                color = HuhTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (friend.isOnline) "🟢 متصل الآن • P2P عالي الجودة" else "⚪ غائب",
                                color = HuhTheme.colors.tertiary,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "رجوع",
                            tint = HuhTheme.colors.textPrimary
                        )
                    }
                },
                actions = {
                    // Voice Call Button
                    IconButton(onClick = { viewModel.startCallWithFriend(friend, isVideo = false) }) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "اتصال صوتي",
                            tint = HuhTheme.colors.primary
                        )
                    }

                    // Video Call Button
                    IconButton(onClick = { viewModel.startCallWithFriend(friend, isVideo = true) }) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "اتصال مرئي",
                            tint = HuhTheme.colors.gold
                        )
                    }

                    // Options Menu
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "إعدادات الصداقة المتقدمة",
                            tint = HuhTheme.colors.textPrimary
                        )
                    }

                    SmoothDropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false },
                        containerColor = HuhTheme.colors.surfaceElevated,
                        borderColor = HuhTheme.colors.outline.copy(alpha = 0.5f),
                        minWidth = 200.dp
                    ) {
                        SmoothDropdownMenuItem(
                            text = { Text("تخصيص خلفية المحادثة", color = HuhTheme.colors.textPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Palette, null, tint = HuhTheme.colors.primary, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOptionsMenu = false
                                showWallpaperDialog = true
                            }
                        )
                        SmoothDropdownMenuItem(
                            text = { Text("مسح سجل المحادثة", color = HuhTheme.colors.textPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = HuhTheme.colors.gold, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOptionsMenu = false
                                viewModel.clearChatHistory(friend.id)
                            }
                        )
                        SmoothDropdownMenuItem(
                            text = { Text("الإبلاغ عن المستخدم", color = Color(0xFFFACC15), fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Report, null, tint = Color(0xFFFACC15), modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOptionsMenu = false
                                viewModel.reportFriend(friend.id)
                            }
                        )
                        SmoothDropdownMenuItem(
                            text = { Text("إلغاء الصداقة", color = HuhTheme.colors.textPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = HuhTheme.colors.textSecondary, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOptionsMenu = false
                                viewModel.unfriend(friend.id)
                                onBack()
                            }
                        )
                        SmoothDropdownMenuItem(
                            text = { Text("حظر المستخدم نهائيًا", color = HuhTheme.colors.error, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Default.Block, null, tint = HuhTheme.colors.error, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOptionsMenu = false
                                viewModel.blockFriend(friend.id)
                                onBack()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HuhTheme.colors.surface
                )
            )
        },
        containerColor = chatWallpaperColor
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
        ) {
            // Chat Messages List with Auto-Scroll & Jump to Bottom feature
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp)
                        .testTag("chat_messages_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg ->
                        if (msg.callLogRecordId != null) {
                            PrivateChatCallLogCard(
                                message = msg,
                                onCallBack = {
                                    viewModel.startCallWithFriend(friend, isVideo = msg.callLogIsVideo)
                                }
                            )
                        } else {
                            ChatMessageBubble(
                                message = msg,
                                onUnlock = { price ->
                                    viewModel.unlockMediaMessage(friend.id, msg.id, price)
                                }
                            )
                        }
                    }
                }

                // 'Jump to bottom' button that appears when the user scrolls up
                PrivateChatJumpToBottomButton(
                    visible = isScrolledUp,
                    onClick = {
                        coroutineScope.launch {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }

            // Recording voice indicator if active
            if (isRecordingVoice) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = HuhTheme.colors.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, null, tint = HuhTheme.colors.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جارٍ تسجيل الصوت (0:04)...", color = HuhTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                isRecordingVoice = false
                                viewModel.sendVoiceMessage(friend.id, 4)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.success)
                        ) {
                            Text("إرسال", color = Color.White)
                        }
                    }
                }
            }

            // Message Customization & Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = HuhTheme.colors.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    // Bubble Color Customizer Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الخط:", color = HuhTheme.colors.textSecondary, fontSize = 11.sp)
                        listOf("#FFFFFF", "#FACC15", "#38BDF8", "#F43F5E", "#10B981").forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .clickable { selectedTextColor = hex }
                                    .border(
                                        width = if (selectedTextColor == hex) 2.dp else 0.dp,
                                        color = HuhTheme.colors.primary,
                                        shape = CircleShape
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text("الفقاعة:", color = HuhTheme.colors.textSecondary, fontSize = 11.sp)
                        listOf("#1E1B2E", "#3B82F6", "#4C1D95", "#065F46", "#831843").forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .clickable { selectedBgColor = hex }
                                    .border(
                                        width = if (selectedBgColor == hex) 2.dp else 0.dp,
                                        color = HuhTheme.colors.primary,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Media Locked Button
                        IconButton(
                            onClick = { showMediaSendDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(HuhTheme.colors.gold.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "إرسال صورة مغلقة",
                                tint = HuhTheme.colors.gold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Voice Message Button
                        IconButton(
                            onClick = { isRecordingVoice = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(HuhTheme.colors.tertiary.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "تسجيل صوتي",
                                tint = HuhTheme.colors.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("اكتب رسالة...", color = HuhTheme.colors.textSecondary) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = HuhTheme.colors.primary,
                                unfocusedBorderColor = HuhTheme.colors.outline,
                                focusedTextColor = HuhTheme.colors.textPrimary,
                                unfocusedTextColor = HuhTheme.colors.textPrimary
                            ),
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (textInput.isNotBlank()) {
                                    val currentText = textInput
                                    textInput = ""
                                    viewModel.sendPrivateTextMessage(
                                        friendId = friend.id,
                                        text = currentText,
                                        textColor = selectedTextColor,
                                        bgColor = selectedBgColor
                                    )
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(60)
                                        if (messages.isNotEmpty()) {
                                            listState.animateScrollToItem(messages.size - 1)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    Brush.linearGradient(listOf(HuhTheme.colors.primary, HuhTheme.colors.secondary)),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "إرسال",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMediaSendDialog) {
        SendPaidMediaDialog(
            onDismiss = { showMediaSendDialog = false },
            onSend = { price ->
                viewModel.sendPrivateMediaMessage(friend.id, R.drawable.ic_ayer_logo, price)
                showMediaSendDialog = false
            }
        )
    }

    if (showWallpaperDialog) {
        AlertDialog(
            onDismissRequest = { showWallpaperDialog = false },
            title = { Text("تخصيص خلفية المحادثة", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر ثيم الخلفية المفضل لمحادثتك:", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))

                    listOf(
                        "أسود فاحم (Obsidian)" to Color(0xFF07040E),
                        "أزرق ملكي (Royal Navy)" to Color(0xFF0D1B2A),
                        "بنفسجي غامق (Velvet Night)" to Color(0xFF180D2B),
                        "أخضر زمردي (Emerald Dark)" to Color(0xFF051A14)
                    ).forEach { (name, color) ->
                        Button(
                            onClick = {
                                chatWallpaperColor = color
                                showWallpaperDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.5f))
                        ) {
                            Text(name, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWallpaperDialog = false }) {
                    Text("إغلاق", color = AyerCyan)
                }
            },
            containerColor = Color(0xFF160F2C)
        )
    }
}

@Composable
fun ChatMessageBubble(
    message: PrivateChatMessage,
    onUnlock: (Int) -> Unit
) {
    @Suppress("UNUSED_VARIABLE")
    val isMe = message.senderId == "me"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.width(270.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = try {
                    Color(android.graphics.Color.parseColor(message.bgColorHex))
                } catch (e: Exception) {
                    Color(0xFF1E1B2E)
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                if (message.isVoiceMessage) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = {},
                            modifier = Modifier
                                .size(36.dp)
                                .background(AyerCyan, CircleShape)
                        ) {
                            Icon(Icons.Default.PlayArrow, "تشغيل", tint = Color.Black)
                        }
                        Column {
                            Text(
                                text = "🎤 رسالة صوتية مشفرة P2P",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "المدة: ${message.voiceDurationSeconds} ثانية",
                                color = AyerCyan,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else if (message.text != null) {
                    Text(
                        text = message.text,
                        color = try {
                            Color(android.graphics.Color.parseColor(message.textColorHex))
                        } catch (e: Exception) {
                            Color.White
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (message.mediaResId != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        if (message.isUnlocked) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = message.mediaResId),
                                contentDescription = "صورة مكشوفة",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(id = message.mediaResId),
                                    contentDescription = "صورة مغلقة",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .blur(20.dp)
                                )

                                var scratchProgress by remember { mutableStateOf(0f) }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, _ ->
                                                change.consume()
                                                scratchProgress = (scratchProgress + 0.05f).coerceAtMost(1f)
                                                if (scratchProgress >= 0.8f && !message.isUnlocked) {
                                                    onUnlock(message.mediaPriceCoins)
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "🔒 صورة خاصة مغلقة",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "المطلوب: ${message.mediaPriceCoins} نقطة للمشاهدة",
                                            color = AyerGold,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { onUnlock(message.mediaPriceCoins) },
                                            colors = ButtonDefaults.buttonColors(containerColor = AyerGold),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("اسحب أو اضغط لفتح الصورة", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SendPaidMediaDialog(
    onDismiss: () -> Unit,
    onSend: (Int) -> Unit
) {
    var priceStr by remember { mutableStateOf("50") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إرسال محتوى خاص مغلق", color = HuhTheme.colors.textPrimary) },
        text = {
            Column {
                Text("حدد عدد النقاط المطلوبة لفتح الصورة من قِبل الطرف الآخر:", color = HuhTheme.colors.textSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("سعر النقاط", color = HuhTheme.colors.textSecondary) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HuhTheme.colors.gold,
                        unfocusedBorderColor = HuhTheme.colors.outline,
                        focusedTextColor = HuhTheme.colors.textPrimary,
                        unfocusedTextColor = HuhTheme.colors.textPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = priceStr.toIntOrNull() ?: 50
                    onSend(p)
                },
                colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.gold)
            ) {
                Text("إرسال الآن", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = HuhTheme.colors.textSecondary)
            }
        },
        containerColor = HuhTheme.colors.surface
    )
}

@Composable
private fun PrivateChatJumpToBottomButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.testTag("jump_to_bottom_button"),
            shape = RoundedCornerShape(24.dp),
            color = HuhTheme.colors.surfaceElevated.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, HuhTheme.colors.primary.copy(alpha = 0.7f)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(HuhTheme.colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "الانتقال إلى أحدث الرسائل",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "أحدث الرسائل ↓",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = HuhTheme.colors.textPrimary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PrivateChatCallLogCard(
    message: PrivateChatMessage,
    onCallBack: () -> Unit
) {
    val status = message.callLogStatus ?: "ENDED"
    val isMissedOrBlocked = status == "MISSED" || status == "CANCELLED" || status == "REJECTED" || status == "FAILED" || status == "BLOCKED_BUDGET"
    val accentColor = when {
        status == "BLOCKED_BUDGET" -> Color(0xFFFFB300)
        isMissedOrBlocked -> Color(0xFFFF5252)
        else -> AyerCyan
    }
    val statusText = when (status) {
        "ENDED" -> if (message.callLogIsVideo) "مكالمة فيديو مكتملة" else "مكالمة صوتية مكتملة"
        "REJECTED" -> "مكالمة مرفوضة"
        "MISSED", "CANCELLED" -> "مكالمة فائتة"
        "BLOCKED_BUDGET" -> "موقوفة بحد الميزانية"
        else -> "مكالمة غير مكتملة"
    }
    val dur = message.callLogDurationSeconds
    val durFormatted = String.format("%02d:%02d", dur / 60, dur % 60)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = HuhTheme.colors.surfaceElevated,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .testTag("chat_call_log_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isMissedOrBlocked -> Icons.Default.CallMissed
                                message.callLogIsVideo -> Icons.Default.Videocam
                                else -> Icons.Default.Call
                            },
                            contentDescription = statusText,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = statusText,
                            color = HuhTheme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (dur > 0) "المدة الفعلية: $durFormatted" else "المدة: 00:00",
                            color = HuhTheme.colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onCallBack,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AyerPrimary.copy(alpha = 0.18f))
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneCallback,
                        contentDescription = "إعادة الاتصال",
                        tint = AyerGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
