package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.example.ui.components.Real3DGlobe
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.LiveStreamRoom
import com.example.data.model.MatchCandidate
import com.example.data.model.MatchChatType
import com.example.data.model.StreamMessage
import com.example.ui.AyerViewModel
import com.example.ui.MatchingStatus
import com.example.ui.components.AgeVerifiedBadge
import com.example.ui.components.CoinBalanceBadge
import com.example.ui.components.ConnectingRippleAnimation
import com.example.ui.components.ConnectingStatusBanner
import com.example.ui.components.ConnectingStatusBeacon
import com.example.ui.components.ConnectingWaveformFeedback
import com.example.ui.components.SmoothDropdownMenu
import com.example.ui.components.SmoothDropdownMenuItem
import com.example.ui.components.StreamRoomAmbientBackdrop
import com.example.ui.components.UserInitialAvatar
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerOutline
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSecondary
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.AyerSurfaceVariant
import com.example.ui.theme.HuhTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MatchScreen(
    viewModel: AyerViewModel,
    userBalance: Int,
    matchingStatus: MatchingStatus,
    selectedGender: String,
    selectedCountry: String,
    selectedChatType: MatchChatType = MatchChatType.VIDEO,
    chatMessages: List<StreamMessage>,
    isTranslationEnabled: Boolean,
    onNavigateToStore: () -> Unit,
    onNavigateToLiveStream: ((LiveStreamRoom) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Collect real-time messages from Firebase Firestore
    val firestoreMessages by viewModel.firestoreMatchMessages.collectAsStateWithLifecycle()
    val queueSize by viewModel.matchQueueSize.collectAsStateWithLifecycle()
    val waitSeconds by viewModel.matchWaitSeconds.collectAsStateWithLifecycle()
    val livekitLocalTrack by viewModel.livekitLocalVideoTrack.collectAsStateWithLifecycle()
    val livekitRemoteTrack by viewModel.livekitRemoteVideoTrack.collectAsStateWithLifecycle()
    val effectiveChatMessages = remember(firestoreMessages, chatMessages) {
        if (firestoreMessages.isNotEmpty()) {
            firestoreMessages.map { fMsg ->
                StreamMessage(
                    id = fMsg.id,
                    senderName = fMsg.senderName,
                    originalText = fMsg.text,
                    translatedText = fMsg.translatedText
                )
            }
        } else {
            chatMessages
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HuhTheme.colors.background)
    ) {
        when (matchingStatus) {
            is MatchingStatus.Idle -> {
                IdleMatchingView(
                    viewModel = viewModel,
                    userBalance = userBalance,
                    selectedChatType = selectedChatType,
                    selectedGender = selectedGender,
                    selectedCountry = selectedCountry,
                    onNavigateToStore = onNavigateToStore,
                    onNavigateToLiveStream = onNavigateToLiveStream
                )
            }
            is MatchingStatus.Searching -> {
                SearchingReal3DGlobeView(
                    selectedChatType = selectedChatType,
                    selectedGender = selectedGender,
                    selectedCountry = selectedCountry,
                    queueSize = queueSize,
                    waitSeconds = waitSeconds,
                    onCancel = { viewModel.endCurrentMatch() }
                )
            }
            is MatchingStatus.Found -> {
                PartnerFoundGlobeZoomView(
                    candidate = matchingStatus.candidate,
                    chatType = selectedChatType
                )
            }
            is MatchingStatus.Connected -> {
                ActiveMatchSessionView(
                    candidate = matchingStatus.candidate,
                    chatType = matchingStatus.chatType,
                    chatMessages = effectiveChatMessages,
                    isTranslationEnabled = isTranslationEnabled,
                    localVideoTrack = livekitLocalTrack,
                    remoteVideoTrack = livekitRemoteTrack,
                    liveKitEngine = viewModel.callLiveKitEngine,
                    onToggleMute = { muted -> viewModel.setBroadcastMuted(muted) },
                    onSwitchCamera = { front -> viewModel.switchBroadcastCamera(front) },
                    onReportPartner = { reason ->
                        viewModel.submitUserReport(
                            targetUserId = matchingStatus.candidate.id,
                            targetUserName = matchingStatus.candidate.name,
                            reason = reason,
                            details = "Reported from active random match"
                        )
                        viewModel.blockUser(matchingStatus.candidate.id)
                        viewModel.skipCurrentMatch()
                    },
                    onToggleTranslation = { viewModel.toggleTranslation() },
                    onSkip = { viewModel.skipCurrentMatch() },
                    onEnd = { viewModel.endCurrentMatch() },
                    onSendMessage = { text -> viewModel.sendMatchChatMessage(text) },
                    onStartPrivateCall = {
                        viewModel.startCallSession(
                            candidate = matchingStatus.candidate,
                            isVideo = (matchingStatus.chatType == MatchChatType.VIDEO)
                        )
                    }
                )
            }
        }
    }
}

data class CountryFilterItem(
    val code: String,
    val name: String,
    val flag: String,
    val capital: String,
    val costExtra: Int
)

val AYER_AVAILABLE_COUNTRIES = listOf(
    CountryFilterItem("ANY", "كل العالم", "🌐", "العالم", 0),
    CountryFilterItem("SA", "السعودية", "🇸🇦", "الرياض", 2),
    CountryFilterItem("EG", "مصر", "🇪🇬", "القاهرة", 2),
    CountryFilterItem("AE", "الإمارات", "🇦🇪", "دبي", 2),
    CountryFilterItem("KW", "الكويت", "🇰🇼", "الكويت", 2),
    CountryFilterItem("QA", "قطر", "🇶🇦", "الدوحة", 2),
    CountryFilterItem("BH", "البحرين", "🇧🇭", "المنامة", 2),
    CountryFilterItem("OM", "عُمان", "🇴🇲", "مسقط", 2),
    CountryFilterItem("JO", "الأردن", "🇯🇴", "عمّان", 2),
    CountryFilterItem("MA", "المغرب", "🇲🇦", "الرباط", 2)
)

@Composable
fun CompactTransparentFilterBar(
    selectedChatType: MatchChatType,
    onSelectChatType: (MatchChatType) -> Unit,
    selectedGender: String,
    onSelectGender: (String) -> Unit,
    selectedCountry: String,
    onSelectCountry: (String) -> Unit,
    matchCost: Int,
    modifier: Modifier = Modifier
) {
    var isChatTypeDropdownExpanded by remember { mutableStateOf(false) }
    var isGenderDropdownExpanded by remember { mutableStateOf(false) }
    var isCountryDropdownExpanded by remember { mutableStateOf(false) }

    // Smooth rotation transitions for dropdown arrows
    val chatTypeArrowRotation by animateFloatAsState(
        targetValue = if (isChatTypeDropdownExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "chat_type_arrow_anim"
    )
    val genderArrowRotation by animateFloatAsState(
        targetValue = if (isGenderDropdownExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "gender_arrow_anim"
    )
    val countryArrowRotation by animateFloatAsState(
        targetValue = if (isCountryDropdownExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "country_arrow_anim"
    )

    // Tactile press scale animations
    val chatTypeScale by animateFloatAsState(
        targetValue = if (isChatTypeDropdownExpanded) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "chat_type_scale"
    )
    val genderScale by animateFloatAsState(
        targetValue = if (isGenderDropdownExpanded) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "gender_scale"
    )
    val countryScale by animateFloatAsState(
        targetValue = if (isCountryDropdownExpanded) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "country_scale"
    )

    val currentCountryItem = AYER_AVAILABLE_COUNTRIES.find { it.code == selectedCountry }
        ?: AYER_AVAILABLE_COUNTRIES.first()

    val currentGenderTitle = when (selectedGender) {
        "FEMALE" -> "بنات 🌸"
        "MALE" -> "شباب ⚡"
        else -> "الكل 🌐"
    }

    // Transparent Background, Single Horizontal Row with 3 dropdown menus (Chat Type, Gender, Country) + Cost Badge
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Chat Type Dropdown (فيديو / صوت / كتابة مع السعر)
        Box(modifier = Modifier.weight(1.05f)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .scale(chatTypeScale)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { isChatTypeDropdownExpanded = !isChatTypeDropdownExpanded }
                    .testTag("chat_type_filter_dropdown"),
                color = AyerPrimary.copy(alpha = 0.85f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedChatType.iconEmoji} ${selectedChatType.title}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "قائمة نوع الدردشة",
                        tint = AyerCyan,
                        modifier = Modifier
                            .size(17.dp)
                            .rotate(chatTypeArrowRotation)
                    )
                }
            }

            SmoothDropdownMenu(
                expanded = isChatTypeDropdownExpanded,
                onDismissRequest = { isChatTypeDropdownExpanded = false },
                containerColor = HuhTheme.colors.surfaceElevated,
                borderColor = AyerCyan.copy(alpha = 0.5f),
                minWidth = 160.dp
            ) {
                MatchChatType.entries.forEach { type ->
                    val isSelected = selectedChatType == type
                    SmoothDropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = type.iconEmoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = type.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AyerGold else HuhTheme.colors.textPrimary
                                )
                            }
                        },
                        trailingContent = {
                            Text(
                                text = "${type.cost}🪙",
                                fontSize = 11.sp,
                                color = AyerGold,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        isSelected = isSelected,
                        onClick = {
                            onSelectChatType(type)
                            isChatTypeDropdownExpanded = false
                        },
                        modifier = Modifier.testTag("chat_type_option_${type.code.lowercase()}")
                    )
                }
            }
        }

        // 2. Gender Dropdown (الكل / بنات / شباب مع السعر)
        Box(modifier = Modifier.weight(0.95f)) {
            val isGenderFiltered = selectedGender != "ANY"
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .scale(genderScale)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { isGenderDropdownExpanded = !isGenderDropdownExpanded }
                    .testTag("gender_filter_dropdown"),
                color = if (isGenderFiltered) AyerSecondary.copy(alpha = 0.85f) else HuhTheme.colors.surfaceElevated.copy(alpha = 0.7f),
                shape = RoundedCornerShape(18.dp),
                border = if (isGenderFiltered) {
                    BorderStroke(1.dp, AyerGold.copy(alpha = 0.8f))
                } else {
                    BorderStroke(0.5.dp, HuhTheme.colors.outline.copy(alpha = 0.35f))
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentGenderTitle,
                        fontSize = 11.sp,
                        fontWeight = if (isGenderFiltered) FontWeight.Bold else FontWeight.Medium,
                        color = HuhTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "قائمة النوع",
                        tint = if (isGenderFiltered) AyerGold else AyerCyan,
                        modifier = Modifier
                            .size(17.dp)
                            .rotate(genderArrowRotation)
                    )
                }
            }

            SmoothDropdownMenu(
                expanded = isGenderDropdownExpanded,
                onDismissRequest = { isGenderDropdownExpanded = false },
                containerColor = HuhTheme.colors.surfaceElevated,
                borderColor = AyerCyan.copy(alpha = 0.5f),
                minWidth = 145.dp
            ) {
                val genderOptions = listOf(
                    Triple("ANY", "الكل 🌐", "0🪙"),
                    Triple("FEMALE", "بنات 🌸", "+2🪙"),
                    Triple("MALE", "شباب ⚡", "+2🪙")
                )
                genderOptions.forEach { (code, title, costStr) ->
                    val isSelected = selectedGender == code
                    SmoothDropdownMenuItem(
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AyerGold else HuhTheme.colors.textPrimary
                            )
                        },
                        trailingContent = {
                            Text(
                                text = costStr,
                                fontSize = 11.sp,
                                color = AyerGold,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        isSelected = isSelected,
                        onClick = {
                            onSelectGender(code)
                            isGenderDropdownExpanded = false
                        },
                        modifier = Modifier.testTag("gender_option_${code.lowercase()}")
                    )
                }
            }
        }

        // 3. Country Dropdown (الدول مع السعر)
        Box(modifier = Modifier.weight(1.15f)) {
            val isCountryFiltered = selectedCountry != "ANY"
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .scale(countryScale)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { isCountryDropdownExpanded = !isCountryDropdownExpanded }
                    .testTag("country_filter_dropdown"),
                color = if (isCountryFiltered) AyerSecondary.copy(alpha = 0.85f) else HuhTheme.colors.surfaceElevated.copy(alpha = 0.7f),
                shape = RoundedCornerShape(18.dp),
                border = if (isCountryFiltered) {
                    BorderStroke(1.dp, AyerGold.copy(alpha = 0.8f))
                } else {
                    BorderStroke(0.5.dp, HuhTheme.colors.outline.copy(alpha = 0.35f))
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentCountryItem.flag} ${currentCountryItem.name}",
                        fontSize = 11.sp,
                        fontWeight = if (isCountryFiltered) FontWeight.Bold else FontWeight.Medium,
                        color = HuhTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "قائمة الدول",
                        tint = if (isCountryFiltered) AyerGold else AyerCyan,
                        modifier = Modifier
                            .size(17.dp)
                            .rotate(countryArrowRotation)
                    )
                }
            }

            SmoothDropdownMenu(
                expanded = isCountryDropdownExpanded,
                onDismissRequest = { isCountryDropdownExpanded = false },
                containerColor = HuhTheme.colors.surfaceElevated,
                borderColor = AyerCyan.copy(alpha = 0.5f),
                minWidth = 175.dp
            ) {
                AYER_AVAILABLE_COUNTRIES.forEach { country ->
                    val isPicked = selectedCountry == country.code
                    SmoothDropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = country.flag, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = country.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isPicked) AyerGold else HuhTheme.colors.textPrimary
                                )
                            }
                        },
                        trailingContent = {
                            Text(
                                text = if (country.costExtra > 0) "+${country.costExtra}🪙" else "0🪙",
                                fontSize = 11.sp,
                                color = AyerGold,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        isSelected = isPicked,
                        onClick = {
                            onSelectCountry(country.code)
                            isCountryDropdownExpanded = false
                        },
                        modifier = Modifier.testTag("country_option_${country.code.lowercase()}")
                    )
                }
            }
        }

        // 4. Total Match Cost Tag in same row
        Surface(
            modifier = Modifier
                .height(36.dp)
                .testTag("total_match_cost_badge"),
            color = HuhTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.6f))
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 9.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$matchCost🪙",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AyerGold
                )
            }
        }
    }
}

@Composable
fun IdleMatchingView(
    viewModel: AyerViewModel,
    userBalance: Int,
    selectedChatType: MatchChatType,
    selectedGender: String,
    selectedCountry: String,
    onNavigateToStore: () -> Unit,
    onNavigateToLiveStream: ((LiveStreamRoom) -> Unit)? = null
) {
    val matchCost = viewModel.calculateMatchCost()
    val liveStreams by viewModel.liveStreams.collectAsStateWithLifecycle()
    var showAgencyClipManager by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshLiveStreams()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Compact Transparent Single-Row Filter Bar with 3 Dropdown Menus
        CompactTransparentFilterBar(
            selectedChatType = selectedChatType,
            onSelectChatType = { viewModel.setChatTypeFilter(it) },
            selectedGender = selectedGender,
            onSelectGender = { viewModel.setGenderFilter(it) },
            selectedCountry = selectedCountry,
            onSelectCountry = { viewModel.setCountryFilter(it) },
            matchCost = matchCost
        )

        Spacer(modifier = Modifier.weight(1f))

        // CENTER STAGE: Agency Streams Moving Behind the Realistic 3D Globe
        // Clean, immaculate 3D globe with ZERO text overlays on its surface!
        // Stays stationary when idle; spins ONLY when clicked or searching!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp),
            contentAlignment = Alignment.Center
        ) {
            // LAYER 1 (BEHIND GLOBE): Moving Agency Streams Carousel elevated slightly higher
            AgencyStreamsMovingBehindGlobe(
                agencyStreams = liveStreams.ifEmpty { viewModel.getLiveStreams() },
                onStreamClick = { stream ->
                    onNavigateToLiveStream?.invoke(stream) ?: viewModel.openLiveStream(stream)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-14).dp)
            )

            // LAYER 2 (FOREGROUND): Realistic 3D Globe lowered down slightly
            // Clean holographic sphere: No text/clutter on top. Spins only on user interaction!
            Real3DGlobe(
                size = 195.dp,
                isSearching = false,
                onClick = { viewModel.startRandomMatch() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 26.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Interactive Pulse Cue: Clicking the globe starts matching!
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = HuhTheme.colors.surfaceElevated.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.45f)),
            modifier = Modifier
                .clickable { viewModel.startRandomMatch() }
                .padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "👆 انقر على الكرة الأرضية لبدء المطابقة الفورية",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AyerCyan
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(AyerGold, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Agency Streams Interactive Indicator & 4s Clip Ticker
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(HuhTheme.colors.surfaceElevated)
                .border(1.dp, AyerGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .clickable { showAgencyClipManager = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🔴 بثات الوكالات المباشرة خلف الكرة • مقطع 4ث",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AyerGold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = "تخصيص مقطع 4 ثوانٍ",
                tint = AyerCyan,
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "تواصل عشوائي مباشر مع أشخاص حقيقيين 100%",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = HuhTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "فيديو عالي الجودة • ترجمة فورية ذكية • بدون أي حسابات وهمية",
            fontSize = 11.sp,
            color = HuhTheme.colors.textTertiary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // MODERN HORIZONTAL ACTION BUTTONS SUITE (BEAUTIFUL UI)
        MatchScreenBottomActionDeck(
            matchCost = matchCost,
            onStartMatch = { viewModel.startRandomMatch() },
            onVoiceMatch = { viewModel.startVoiceMatch() },
            onVipMatch = { viewModel.startVipMatch() },
            onOpenClipManager = { showAgencyClipManager = true }
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showAgencyClipManager) {
        AgencyClipManagerDialog(
            liveStreams = liveStreams.ifEmpty { viewModel.getLiveStreams() },
            onSaveClip = { streamId, title, agency, seconds ->
                viewModel.updateStreamPreviewClip(streamId, title, agency, seconds)
            },
            onDismiss = { showAgencyClipManager = false }
        )
    }
}

@Composable
fun SearchingReal3DGlobeView(
    selectedChatType: MatchChatType,
    selectedGender: String,
    selectedCountry: String,
    queueSize: Int = 1,
    waitSeconds: Int = 0,
    onCancel: () -> Unit
) {
    val destinationCountry = remember(selectedCountry) {
        if (selectedCountry != "ANY") {
            AYER_AVAILABLE_COUNTRIES.find { it.code == selectedCountry }
                ?: AYER_AVAILABLE_COUNTRIES.first()
        } else {
            val realCountries = AYER_AVAILABLE_COUNTRIES.filter { it.code != "ANY" }
            realCountries.random()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // TOP CONNECTING STATUS BANNER WITH LIVE RIPPLE BEACON
        ConnectingStatusBanner(
            modifier = Modifier
                .fillMaxWidth(0.96f),
            destinationLabel = "${destinationCountry.flag} ${destinationCountry.name}",
            chatTypeEmoji = selectedChatType.iconEmoji,
            chatTypeTitle = selectedChatType.title,
            latencyMs = 22
        )

        Spacer(modifier = Modifier.height(20.dp))

        // CENTER: 3D Globe with Multi-Stage Concentric Expanding Ripples
        // Smooth continuous ripple waves radiate outward from the connecting globe
        Box(
            modifier = Modifier.size(310.dp),
            contentAlignment = Alignment.Center
        ) {
            // LAYER 1: Wide-stage concentric smooth ripples expanding across the screen
            ConnectingRippleAnimation(
                modifier = Modifier.fillMaxSize(),
                ringCount = 4,
                cycleDurationMillis = 2600,
                baseRadiusFraction = 0.36f,
                maxRadiusFraction = 0.98f,
                primaryColor = AyerCyan,
                accentColor = AyerGold,
                maxAlpha = 0.80f,
                showDashedAccents = true,
                showParticles = true
            )

            // LAYER 2: Pure 3D Planet rotating across longitude with internal glow
            Real3DGlobe(
                size = 215.dp,
                isSearching = true
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SEARCH & CONNECTING METRICS CARD
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = HuhTheme.colors.surfaceElevated,
            border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.94f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    ConnectingStatusBeacon(
                        size = 18.dp,
                        coreColor = AyerGold,
                        rippleColor = AyerCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "جاري الاتصال والبحث عن شريك متطابق... 🌍",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AyerGold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Chat Type & Filter Details Row
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AyerPrimary.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${selectedChatType.iconEmoji} ${selectedChatType.title} (${selectedChatType.cost}🪙)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الوجهة: ${destinationCountry.flag} ${destinationCountry.name}",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الفلتر: $selectedGender",
                        fontSize = 12.sp,
                        color = AyerCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    ConnectingWaveformFeedback(
                        barCount = 5,
                        barColor = AyerCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "طابور المطابقة الحقيقي • انتظار ${waitSeconds}ث • المتاحون الآن: ${queueSize.coerceAtLeast(1)}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // CANCEL BUTTON
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = AyerSurfaceElevated),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            Text("إلغاء البحث والعودة", color = Color.LightGray, fontSize = 13.sp)
        }
    }
}

/**
 * Partner Found Globe Zoom View.
 * Matches user's exact specification:
 * "وعندما يجد الشريك يحدث زووم علي مكانه في الكورة الارضيه ثم تفتح المحادثة بين الطرفيين"
 */
@Composable
fun PartnerFoundGlobeZoomView(
    candidate: MatchCandidate,
    chatType: MatchChatType = MatchChatType.VIDEO
) {
    // Zoom Scale Animates rapidly and smoothly from 1.0f to 2.8f
    val zoomAnim = remember { Animatable(1.0f) }
    val reticleAlpha = remember { Animatable(0f) }
    val cardSlideAnim = remember { Animatable(0.85f) }

    LaunchedEffect(Unit) {
        zoomAnim.animateTo(
            targetValue = 2.8f,
            animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(Unit) {
        reticleAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, delayMillis = 300, easing = LinearEasing)
        )
    }

    LaunchedEffect(Unit) {
        cardSlideAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, delayMillis = 400, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06030E)),
        contentAlignment = Alignment.Center
    ) {
        // 1. Zooming 3D Globe focused on coordinates
        Real3DGlobe(
            size = 230.dp,
            isSearching = false,
            zoomScale = zoomAnim.value
        )

        // 2. High-Tech Target Lock-On Reticle & Pulsing Beacon at Focal Point
        Canvas(modifier = Modifier.size(240.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val alpha = reticleAlpha.value

            if (alpha > 0f) {
                // Expanding target rings
                drawCircle(
                    color = AyerGold.copy(alpha = 0.8f * alpha),
                    radius = 35.dp.toPx(),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )
                drawCircle(
                    color = AyerCyan.copy(alpha = 0.5f * alpha),
                    radius = 55.dp.toPx(),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f), 0f)
                    )
                )
                // Crosshairs
                drawLine(
                    color = AyerGold.copy(alpha = 0.9f * alpha),
                    start = Offset(center.x - 45.dp.toPx(), center.y),
                    end = Offset(center.x - 12.dp.toPx(), center.y),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = AyerGold.copy(alpha = 0.9f * alpha),
                    start = Offset(center.x + 12.dp.toPx(), center.y),
                    end = Offset(center.x + 45.dp.toPx(), center.y),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = AyerGold.copy(alpha = 0.9f * alpha),
                    start = Offset(center.x, center.y - 45.dp.toPx()),
                    end = Offset(center.x, center.y - 12.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = AyerGold.copy(alpha = 0.9f * alpha),
                    start = Offset(center.x, center.y + 12.dp.toPx()),
                    end = Offset(center.x, center.y + 45.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
                // Center pinpoint
                drawCircle(
                    color = AyerCyan.copy(alpha = alpha),
                    radius = 5.dp.toPx()
                )
            }
        }

        // 3. Floating Holographic Partner Card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 36.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xF2140B2C),
                border = BorderStroke(
                    1.5.dp,
                    Brush.horizontalGradient(
                        listOf(AyerGold, AyerCyan, AyerPrimary)
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .scale(cardSlideAnim.value)
                    .graphicsLayer { this.alpha = reticleAlpha.value }
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Lock-On Status Tag
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33FFB300))
                            .border(1.dp, AyerGold.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🎯 تم العثور على الشريك وتحديد موقعه بدقة!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AyerGold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Candidate Avatar with glowing cyan & gold border
                        UserInitialAvatar(
                            name = candidate.name,
                            avatarResId = candidate.imageResId,
                            size = 64.dp,
                            borderWidth = 2.dp,
                            borderColor = AyerCyan,
                            fontSize = 22.sp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = candidate.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${candidate.age} سنة",
                                    fontSize = 13.sp,
                                    color = Color.LightGray
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0x3300E676),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "موثق 🛡️",
                                        fontSize = 10.sp,
                                        color = Color(0xFF00E676),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = candidate.country,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AyerGold
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "🟢 متصل الآن • جاري فتح محادثة ${chatType.title} ${chatType.iconEmoji} المباشرة...",
                                fontSize = 11.sp,
                                color = AyerCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveMatchSessionView(
    candidate: MatchCandidate,
    chatType: MatchChatType,
    chatMessages: List<StreamMessage>,
    isTranslationEnabled: Boolean,
    localVideoTrack: io.livekit.android.room.track.VideoTrack? = null,
    remoteVideoTrack: io.livekit.android.room.track.VideoTrack? = null,
    liveKitEngine: com.example.calls.CallLiveKitEngine? = null,
    onToggleMute: (Boolean) -> Unit = {},
    onSwitchCamera: (Boolean) -> Unit = {},
    onReportPartner: (String) -> Unit = {},
    onToggleTranslation: () -> Unit,
    onSkip: () -> Unit,
    onEnd: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStartPrivateCall: (() -> Unit)? = null
) {
    when (chatType) {
        MatchChatType.VIDEO -> {
            ActiveMatchVideoContent(
                candidate = candidate,
                chatMessages = chatMessages,
                isTranslationEnabled = isTranslationEnabled,
                localVideoTrack = localVideoTrack,
                remoteVideoTrack = remoteVideoTrack,
                liveKitEngine = liveKitEngine,
                onToggleMute = onToggleMute,
                onSwitchCamera = onSwitchCamera,
                onReportPartner = onReportPartner,
                onToggleTranslation = onToggleTranslation,
                onSkip = onSkip,
                onEnd = onEnd,
                onSendMessage = onSendMessage,
                onStartPrivateCall = onStartPrivateCall
            )
        }
        MatchChatType.VOICE -> {
            ActiveMatchVoiceContent(
                candidate = candidate,
                chatMessages = chatMessages,
                isTranslationEnabled = isTranslationEnabled,
                onToggleTranslation = onToggleTranslation,
                onSkip = onSkip,
                onEnd = onEnd,
                onSendMessage = onSendMessage
            )
        }
        MatchChatType.TEXT -> {
            ActiveMatchTextContent(
                candidate = candidate,
                chatMessages = chatMessages,
                isTranslationEnabled = isTranslationEnabled,
                onToggleTranslation = onToggleTranslation,
                onSkip = onSkip,
                onEnd = onEnd,
                onSendMessage = onSendMessage
            )
        }
    }
}

@Composable
fun ActiveMatchVideoContent(
    candidate: MatchCandidate,
    chatMessages: List<StreamMessage>,
    isTranslationEnabled: Boolean,
    localVideoTrack: io.livekit.android.room.track.VideoTrack? = null,
    remoteVideoTrack: io.livekit.android.room.track.VideoTrack? = null,
    liveKitEngine: com.example.calls.CallLiveKitEngine? = null,
    onToggleMute: (Boolean) -> Unit = {},
    onSwitchCamera: (Boolean) -> Unit = {},
    onReportPartner: (String) -> Unit = {},
    onToggleTranslation: () -> Unit,
    onSkip: () -> Unit,
    onEnd: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStartPrivateCall: (() -> Unit)? = null
) {
    var messageInput by remember { mutableStateOf("") }
    var isMuted by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }

    val videoChatListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isVideoChatScrolledUp by remember {
        derivedStateOf {
            val total = videoChatListState.layoutInfo.totalItemsCount
            if (total <= 1) {
                false
            } else {
                val lastIdx = videoChatListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastIdx < total - 1
            }
        }
    }

    var isFirstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            if (isFirstLoad) {
                videoChatListState.scrollToItem(chatMessages.size - 1)
                isFirstLoad = false
            } else if (!isVideoChatScrolledUp) {
                videoChatListState.animateScrollToItem(chatMessages.size - 1)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0614))) {
        // Real LiveKit Remote Partner Video Stream
        if (remoteVideoTrack != null && liveKitEngine != null) {
            LiveKitVideoRenderer(
                videoTrack = remoteVideoTrack,
                engine = liveKitEngine,
                modifier = Modifier.fillMaxSize(),
                isMirror = false
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🎥", fontSize = 42.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "بانتظار بث فيديو ${candidate.name} عبر LiveKit...",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            }
        }

        // Gradient scrim for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        ),
                        startY = 0f,
                        endY = 1800f
                    )
                )
        )

        // Top Info Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${candidate.name}, ${candidate.age}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AgeVerifiedBadge()
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "📍 ${candidate.country} • ${candidate.language} • 🎥 فيديو",
                    fontSize = 12.sp,
                    color = Color(0xFFDDDDDD)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // WebRTC 1-on-1 Private Call Button
                if (onStartPrivateCall != null) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onStartPrivateCall() }
                            .testTag("match_private_webrtc_call_btn"),
                        color = AyerGold
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "مكالمة خاصة WebRTC",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "مكالمة خاصة 🔒",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }

                // Translation Toggle Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleTranslation),
                    color = if (isTranslationEnabled) AyerPrimary else AyerSurfaceElevated
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "الترجمة الفورية",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isTranslationEnabled) "الترجمة: تعمل" else "الترجمة: إيقاف",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }

                // Report & Block Partner button
                IconButton(
                    onClick = { onReportPartner("سلوك غير لائق أثناء المطابقة") },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Red.copy(alpha = 0.3f), CircleShape)
                        .testTag("match_report_partner_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = "إبلاغ وحظر",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Self-view PiP (Picture in Picture) in top right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 16.dp)
                .size(width = 100.dp, height = 140.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, AyerPrimary, RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1035))
        ) {
            if (localVideoTrack != null && liveKitEngine != null) {
                LiveKitVideoRenderer(
                    videoTrack = localVideoTrack,
                    engine = liveKitEngine,
                    modifier = Modifier.fillMaxSize(),
                    isMirror = isFrontCamera
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "كاميرتك 🤳",
                    fontSize = 10.sp,
                    color = Color.LightGray
                )
                if (localVideoTrack == null) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = AyerCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "تبديل الكاميرا",
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                isFrontCamera = !isFrontCamera
                                onSwitchCamera(isFrontCamera)
                            }
                    )
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "كتم الصوت",
                        tint = if (isMuted) Color.Red else Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                isMuted = !isMuted
                                onToggleMute(isMuted)
                            }
                    )
                }
            }
        }

        // Chat & Subtitles Overlay (bottom half)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            // Live Chat Messages with instant translation, auto-scroll and jump-to-bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                LazyColumn(
                    state = videoChatListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("match_video_chat_list")
                ) {
                    items(chatMessages) { msg ->
                        Surface(
                            modifier = Modifier
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            color = Color.Black.copy(alpha = 0.55f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(
                                    text = "${msg.senderName}: ${msg.originalText}",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                if (isTranslationEnabled && msg.translatedText != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Translate,
                                            contentDescription = null,
                                            tint = AyerCyan,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = msg.translatedText,
                                            fontSize = 12.sp,
                                            color = AyerCyan,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 'Jump to bottom' button that appears when user scrolls up
                MatchChatJumpToBottomButton(
                    visible = isVideoChatScrolledUp,
                    onClick = {
                        coroutineScope.launch {
                            if (chatMessages.isNotEmpty()) {
                                videoChatListState.animateScrollToItem(chatMessages.size - 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick message input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = { Text("اكتب رسالة مترجمة...", fontSize = 12.sp, color = Color.Gray) },
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
                            onSendMessage(messageInput)
                            messageInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(AyerPrimary, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Bar: End Call vs. Skip (تخطي ⏩)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // End Call Button
                Button(
                    onClick = onEnd,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.CallEnd, contentDescription = "إنهاء المكالمة")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إنهاء")
                }

                // Skip to Next Match (تخطي)
                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(containerColor = AyerSecondary),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("skip_match_button")
                ) {
                    Text(
                        text = "تخطي ومطابقة جديدة ⏩",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Voice Match UI with pulsing sound waves around partner avatar,
 * microphone mute/unmute, loudspeaker toggle, and live voice translation overlay.
 */
@Composable
fun ActiveMatchVoiceContent(
    candidate: MatchCandidate,
    chatMessages: List<StreamMessage>,
    isTranslationEnabled: Boolean,
    onToggleTranslation: () -> Unit,
    onSkip: () -> Unit,
    onEnd: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var messageInput by remember { mutableStateOf("") }

    val voiceChatListState = rememberLazyListState()
    val voiceCoroutineScope = rememberCoroutineScope()

    val isVoiceChatScrolledUp by remember {
        derivedStateOf {
            val total = voiceChatListState.layoutInfo.totalItemsCount
            if (total <= 1) {
                false
            } else {
                val lastIdx = voiceChatListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastIdx < total - 1
            }
        }
    }

    var isVoiceFirstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            if (isVoiceFirstLoad) {
                voiceChatListState.scrollToItem(chatMessages.size - 1)
                isVoiceFirstLoad = false
            } else if (!isVoiceChatScrolledUp) {
                voiceChatListState.animateScrollToItem(chatMessages.size - 1)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_waves")
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1"
    )
    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = 1.15f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF130A2B),
                        Color(0xFF0C071C),
                        Color(0xFF06030F)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${candidate.name}, ${candidate.age}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AgeVerifiedBadge()
                    }
                    Text(
                        text = "🎙️ مكالمة صوتية HD • ${candidate.country}",
                        fontSize = 12.sp,
                        color = AyerCyan
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onToggleTranslation),
                        color = if (isTranslationEnabled) AyerPrimary else AyerSurfaceElevated
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "ترجمة صوتية",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isTranslationEnabled) "ترجمة: تعمل" else "ترجمة: إيقاف",
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }

                    IconButton(
                        onClick = { /* Report */ },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Red.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "إبلاغ",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.6f))

            // Center Stage: Pulsing Sound Waves Around Candidate Avatar
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer Pulse Ring 2
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .graphicsLayer {
                            scaleX = waveScale2
                            scaleY = waveScale2
                            alpha = 0.25f
                        }
                        .background(AyerCyan, CircleShape)
                )
                // Outer Pulse Ring 1
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .graphicsLayer {
                            scaleX = waveScale1
                            scaleY = waveScale1
                            alpha = 0.45f
                        }
                        .background(AyerPrimary, CircleShape)
                )

                UserInitialAvatar(
                    name = candidate.name,
                    avatarResId = candidate.imageResId,
                    size = 120.dp,
                    borderWidth = 3.dp,
                    borderColor = AyerCyan,
                    fontSize = 38.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x991E1338),
                border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF00E676), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isMuted) "صوتك مكتوم 🔇" else "الميكروفون نشط 🎙️ • الطرف الآخر يستمع",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMuted) Color(0xFFFF5252) else Color.White
                    )
                }
            }

            // Audio Controls (Mute, Speaker)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic Mute Button
                Surface(
                    shape = CircleShape,
                    color = if (isMuted) Color(0xFFD32F2F) else AyerSurfaceElevated,
                    border = BorderStroke(1.dp, if (isMuted) Color.Red else AyerCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .clickable { isMuted = !isMuted }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "كتم الميكروفون",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Speaker Button
                Surface(
                    shape = CircleShape,
                    color = if (isSpeakerOn) AyerPrimary else AyerSurfaceElevated,
                    border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .clickable { isSpeakerOn = !isSpeakerOn }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "مكبر الصوت",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Subtitle / text messages during call with Auto-Scroll & Jump to Bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
            ) {
                LazyColumn(
                    state = voiceChatListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("match_voice_chat_list")
                ) {
                    items(chatMessages) { msg ->
                        Surface(
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            color = Color.Black.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = "${msg.senderName}: ${msg.originalText}",
                                fontSize = 12.sp,
                                color = Color.LightGray,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // 'Jump to bottom' button for voice call
                MatchChatJumpToBottomButton(
                    visible = isVoiceChatScrolledUp,
                    onClick = {
                        voiceCoroutineScope.launch {
                            if (chatMessages.isNotEmpty()) {
                                voiceChatListState.animateScrollToItem(chatMessages.size - 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick message input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = { Text("أرسل رسالة نصية أثناء المكالمة...", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0x801A0E32),
                        unfocusedContainerColor = Color(0x801A0E32),
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
                            onSendMessage(messageInput)
                            messageInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(AyerPrimary, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Bar: End Call vs. Skip (تخطي ⏩)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onEnd,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.CallEnd, contentDescription = "إنهاء المكالمة")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إنهاء المكالمة")
                }

                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(containerColor = AyerSecondary),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("skip_match_button")
                ) {
                    Text(
                        text = "تخطي ومطابقة جديدة ⏩",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Text Match UI with full conversation chat stream, instant translations,
 * quick emoji reaction bar, and smooth navigation.
 */
@Composable
fun ActiveMatchTextContent(
    candidate: MatchCandidate,
    chatMessages: List<StreamMessage>,
    isTranslationEnabled: Boolean,
    onToggleTranslation: () -> Unit,
    onSkip: () -> Unit,
    onEnd: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var messageInput by remember { mutableStateOf("") }
    val quickEmojis = listOf("👋", "❤️", "😊", "🌹", "🔥", "✨")

    val chatListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isChatScrolledUp by remember {
        derivedStateOf {
            val total = chatListState.layoutInfo.totalItemsCount
            if (total <= 1) {
                false
            } else {
                val lastIdx = chatListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastIdx < total - 1
            }
        }
    }

    var isFirstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            if (isFirstLoad) {
                chatListState.scrollToItem(chatMessages.size - 1)
                isFirstLoad = false
            } else if (!isChatScrolledUp) {
                chatListState.animateScrollToItem(chatMessages.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0518))
            .padding(16.dp)
    ) {
        // Top Bar
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF160D2D),
            border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserInitialAvatar(
                        name = candidate.name,
                        avatarResId = candidate.imageResId,
                        size = 44.dp,
                        borderWidth = 1.5.dp,
                        borderColor = AyerCyan,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${candidate.name}, ${candidate.age}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            AgeVerifiedBadge()
                        }
                        Text(
                            text = "💬 دردشة كتابية فورية • ${candidate.country}",
                            fontSize = 11.sp,
                            color = AyerCyan
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onToggleTranslation),
                        color = if (isTranslationEnabled) AyerPrimary else AyerSurfaceElevated
                    ) {
                        Text(
                            text = if (isTranslationEnabled) "ترجمة: تعمل" else "ترجمة: إيقاف",
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                        )
                    }

                    IconButton(
                        onClick = onEnd,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFFD32F2F).copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "إنهاء",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chat Stream with Auto-Scroll and Jump to Bottom Button
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = chatListState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("match_chat_messages_list")
            ) {
                items(chatMessages) { msg ->
                    val isMe = msg.senderName.contains("أنت") || msg.senderName.contains("You")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 14.dp,
                                bottomStart = if (isMe) 14.dp else 2.dp,
                                bottomEnd = if (isMe) 2.dp else 14.dp
                            ),
                            color = if (isMe) AyerPrimary.copy(alpha = 0.9f) else Color(0xFF1E1338),
                            border = BorderStroke(
                                0.5.dp,
                                if (isMe) AyerCyan.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    text = msg.originalText,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Normal
                                )
                                if (isTranslationEnabled && msg.translatedText != null) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Translate,
                                            contentDescription = null,
                                            tint = AyerGold,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = msg.translatedText,
                                            fontSize = 11.sp,
                                            color = AyerGold,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 'Jump to bottom' button that appears when user scrolls up
            MatchChatJumpToBottomButton(
                visible = isChatScrolledUp,
                onClick = {
                    coroutineScope.launch {
                        if (chatMessages.isNotEmpty()) {
                            chatListState.animateScrollToItem(chatMessages.size - 1)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )
        }

        // Quick Emoji Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            quickEmojis.forEach { emoji ->
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1A1133),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onSendMessage(emoji) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = emoji, fontSize = 16.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Message Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = { messageInput = it },
                placeholder = { Text("اكتب رسالة فورية...", fontSize = 12.sp, color = Color.Gray) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF180E30),
                    unfocusedContainerColor = Color(0xFF180E30),
                    focusedBorderColor = AyerCyan,
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
                        val input = messageInput
                        messageInput = ""
                        onSendMessage(input)
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(50)
                            if (chatMessages.isNotEmpty()) {
                                chatListState.animateScrollToItem(chatMessages.size - 1)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(AyerPrimary, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "إرسال",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Skip to Next Match Button
        Button(
            onClick = onSkip,
            colors = ButtonDefaults.buttonColors(containerColor = AyerSecondary),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("skip_match_button")
        ) {
            Text(
                text = "تخطي ومطابقة شريك جديد ⏩",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Backward compatibility wrapper for ActiveMatchVideoView.
 */
@Composable
fun ActiveMatchVideoView(
    candidate: MatchCandidate,
    chatMessages: List<StreamMessage>,
    isTranslationEnabled: Boolean,
    onToggleTranslation: () -> Unit,
    onSkip: () -> Unit,
    onEnd: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    ActiveMatchSessionView(
        candidate = candidate,
        chatType = MatchChatType.VIDEO,
        chatMessages = chatMessages,
        isTranslationEnabled = isTranslationEnabled,
        onToggleTranslation = onToggleTranslation,
        onSkip = onSkip,
        onEnd = onEnd,
        onSendMessage = onSendMessage
    )
}

// =========================================================================
// Agency Streams Behind Globe Carousel with 4-Second Animated Motion Loops
// =========================================================================

@Composable
fun AgencyStreamsMovingBehindGlobe(
    agencyStreams: List<LiveStreamRoom>,
    onStreamClick: (LiveStreamRoom) -> Unit,
    modifier: Modifier = Modifier
) {
    if (agencyStreams.isEmpty()) return

    // Create continuous looping list
    val repeatedStreams = remember(agencyStreams) {
        List(120) { index -> agencyStreams[index % agencyStreams.size] }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 25)

    // Smooth continuous auto-scroll moving Right to Left
    LaunchedEffect(listState) {
        while (isActive) {
            try {
                listState.scrollBy(2f)
            } catch (e: Exception) {
                // Ignore cancellation gracefully
            }
            delay(16)
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(235.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(repeatedStreams) { stream ->
            AgencyStreamMotionCard(
                stream = stream,
                onClick = { onStreamClick(stream) }
            )
        }
    }
}

@Composable
fun AgencyStreamMotionCard(
    stream: LiveStreamRoom,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(135.dp)
            .height(185.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF140D26),
        border = BorderStroke(
            1.5.dp,
            Brush.verticalGradient(
                listOf(
                    AyerGold.copy(alpha = 0.8f),
                    AyerPrimary.copy(alpha = 0.6f),
                    AyerCyan.copy(alpha = 0.6f)
                )
            )
        ),
        shadowElevation = 8.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            StreamRoomAmbientBackdrop(
                hostName = stream.hostName,
                imageResId = stream.imageResId,
                modifier = Modifier.fillMaxSize()
            )

            // Top Badges Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Red.copy(alpha = 0.88f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "بث", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Agency Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(0.5.dp, AyerGold.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = "💎 ${stream.agencyName?.take(9) ?: "وكالة"}",
                        fontSize = 8.sp,
                        color = AyerGold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Bottom Info Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stream.hostName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👁️ ${stream.viewersCount}",
                        fontSize = 9.sp,
                        color = AyerCyan
                    )
                    Text(
                        text = stream.hostCountry,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AyerGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// =========================================================================
// Modern Bottom Action Buttons Deck (High-End Beautiful UI)
// =========================================================================

@Composable
fun MatchScreenBottomActionDeck(
    matchCost: Int,
    onStartMatch: () -> Unit,
    onVoiceMatch: () -> Unit,
    onVipMatch: () -> Unit,
    onOpenClipManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Primary Luminous Match Action Pill with Animated Glow
        val buttonPulse = rememberInfiniteTransition(label = "pulse_btn")
        val btnScale by buttonPulse.animateFloat(
            initialValue = 0.99f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "btn_scale"
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .scale(btnScale)
                .clip(RoundedCornerShape(29.dp))
                .clickable { onStartMatch() }
                .testTag("start_match_button"),
            shape = RoundedCornerShape(29.dp),
            color = Color.Transparent,
            border = BorderStroke(
                1.5.dp,
                Brush.horizontalGradient(
                    listOf(AyerCyan, Color(0xFFFF2A85), AyerGold)
                )
            ),
            shadowElevation = 12.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFF1493),
                                AyerPrimary,
                                Color(0xFF6A0DAD)
                            )
                        )
                    )
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.22f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "بدء المطابقة العشوائية ⚡",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "اتصال مباشر بأشخاص حقيقيين 100%",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    // Gold Coins Badge
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, AyerGold.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$matchCost 🪙",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AyerGold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Secondary Sleek Horizontal Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Button 1: Audio Match (صوت فقط)
            ModernHorizontalActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Mic,
                title = "صوت فقط",
                subtitle = "1🪙 سريع",
                accentColor = AyerCyan,
                onClick = onVoiceMatch
            )

            // Button 2: VIP Priority Match (رادار VIP)
            ModernHorizontalActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Diamond,
                title = "رادار VIP",
                subtitle = "أولوية 👑",
                accentColor = AyerGold,
                onClick = onVipMatch
            )

            // Button 3: Agency 4s Clip Manager (مقطع 4ث)
            ModernHorizontalActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Movie,
                title = "مقطع 4ث",
                subtitle = "تحكم الوكالة",
                accentColor = AyerPrimary,
                onClick = onOpenClipManager
            )
        }
    }
}

@Composable
fun ModernHorizontalActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = HuhTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(accentColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = HuhTheme.colors.textPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 9.sp,
                    color = accentColor
                )
            }
        }
    }
}

// =========================================================================
// Agency & Admin 4-Second Animated Clip Manager Dialog
// =========================================================================

@Composable
fun AgencyClipManagerDialog(
    liveStreams: List<LiveStreamRoom>,
    onSaveClip: (streamId: String, title: String, agency: String, seconds: Int) -> Unit,
    onAddNewAgencyStream: ((LiveStreamRoom) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var selectedStream by remember { mutableStateOf(liveStreams.firstOrNull()) }
    var clipTitle by remember(selectedStream) {
        mutableStateOf(selectedStream?.previewClipTitle ?: "مقطع بث مميز (4 ثوانٍ)")
    }
    var agencyName by remember(selectedStream) {
        mutableStateOf(selectedStream?.agencyName ?: "وكالة النجوم الدولية")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = AyerGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "إدارة مقطع 4 ثوانٍ (الوكيل والأدمن)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AyerGold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "حدد البث وخصّص مقطع المعاينة المتحرك (4 ثوانٍ) الذي يظهر للمستخدمين خلف الكرة الأرضية في شاشة المطابقة:",
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "اختر البث المعني:", fontSize = 11.sp, color = AyerCyan)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    items(liveStreams) { stream ->
                        val isSel = selectedStream?.id == stream.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) AyerPrimary else Color.White.copy(alpha = 0.08f),
                            border = if (isSel) BorderStroke(1.dp, AyerGold) else null,
                            modifier = Modifier.clickable { selectedStream = stream }
                        ) {
                            Text(
                                text = stream.hostName,
                                fontSize = 11.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = clipTitle,
                    onValueChange = { clipTitle = it },
                    label = { Text("عنوان المقطع 4 ثوانٍ") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AyerPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = agencyName,
                    onValueChange = { agencyName = it },
                    label = { Text("اسم الوكالة الراعية") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AyerGold,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Live 4-second preview inside dialog
                Text(text = "معاينة حية للمقطع المتحرك (4 ثوانٍ):", fontSize = 11.sp, color = AyerGold)
                Spacer(modifier = Modifier.height(4.dp))
                selectedStream?.let { stream ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AgencyStreamMotionCard(
                            stream = stream.copy(previewClipTitle = clipTitle, agencyName = agencyName),
                            onClick = { /* Preview only */ }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedStream?.let {
                        onSaveClip(it.id, clipTitle, agencyName, 4)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AyerGold)
            ) {
                Text("حفظ وتثبيت في الرادار", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        },
        containerColor = AyerSurfaceElevated
    )
}

@Composable
private fun MatchChatJumpToBottomButton(
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
            modifier = Modifier.testTag("match_jump_to_bottom_button"),
            shape = RoundedCornerShape(20.dp),
            color = AyerSurfaceElevated.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.7f)),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "الانتقال لأحدث الرسائل",
                    tint = AyerCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "أحدث الرسائل ↓",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
