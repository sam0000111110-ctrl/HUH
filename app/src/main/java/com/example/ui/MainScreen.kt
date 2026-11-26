package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.components.AyerSearchBar
import com.example.ui.components.InAppNotificationBanner
import com.example.ui.components.MinimalCallOverlay
import com.example.ui.components.NotificationCenterDialog
import com.example.ui.components.UserProfileTopCornerWidget
import com.example.ui.components.IncomingCallOverlay
import com.example.ui.locale.AppLanguage
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LocalAppLanguage
import com.example.ui.screens.AgenciesScreen
import com.example.ui.screens.CallAdminDashboardScreen
import com.example.ui.screens.CallScreen
import com.example.ui.screens.LiveStreamScreen
import com.example.ui.screens.MatchScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.WalletStoreScreen
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSecondary
import com.example.ui.theme.AyerSurface
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.HuhTheme

sealed class AyerScreen(val route: String, val titleKey: String, val defaultTitle: String, val icon: ImageVector) {
    object Match : AyerScreen("match", "tab_match", "المطابقة", Icons.Default.Radar)
    object Live : AyerScreen("live", "tab_live", "بث مباشر", Icons.Default.LiveTv)
    object Wallet : AyerScreen("wallet", "tab_store", "المتجر", Icons.Default.MonetizationOn)
    object Agency : AyerScreen("agency", "tab_agency", "الوكالات", Icons.Default.Business)
    object Profile : AyerScreen("profile", "tab_profile", "حسابي", Icons.Default.Person)

    fun getTitle(language: AppLanguage): String {
        return AppStrings.get(titleKey, language)
    }
}

@Composable
fun MainScreen(viewModel: AyerViewModel) {
    var currentScreen by remember { mutableStateOf<AyerScreen>(AyerScreen.Match) }
    var showNotificationCenterDialog by remember { mutableStateOf(false) }
    var showFriendsDialog by remember { mutableStateOf(false) }
    var showCallAdminDashboard by remember { mutableStateOf(false) }
    var selectedChatFriend by remember { mutableStateOf<com.example.data.model.FriendItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val matchingStatus by viewModel.matchingStatus.collectAsStateWithLifecycle()
    val selectedGender by viewModel.selectedGenderFilter.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountryFilter.collectAsStateWithLifecycle()
    val selectedChatType by viewModel.selectedChatType.collectAsStateWithLifecycle()
    val chatMessages by viewModel.matchChatMessages.collectAsStateWithLifecycle()
    val isTranslationEnabled by viewModel.isTranslationEnabled.collectAsStateWithLifecycle()
    val selectedStream by viewModel.selectedLiveStream.collectAsStateWithLifecycle()
    val streamMessages by viewModel.streamMessages.collectAsStateWithLifecycle()
    val activeGift by viewModel.activeGiftEffect.collectAsStateWithLifecycle()
    val uiNotice by viewModel.uiNotice.collectAsStateWithLifecycle()

    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val activeInAppNotification by viewModel.activeInAppNotification.collectAsStateWithLifecycle()

    val friends by viewModel.friendsList.collectAsStateWithLifecycle()
    val privateChatMessagesMap by viewModel.privateChatMessages.collectAsStateWithLifecycle()

    val activeCallSession by viewModel.activeCallSession.collectAsStateWithLifecycle()
    val incomingCallInvite by viewModel.incomingCallInvite.collectAsStateWithLifecycle()
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val showRateUsDialog by viewModel.showRateUsDialog.collectAsStateWithLifecycle()
    val completedMissionsClaimCount by viewModel.completedMissionsClaimCount.collectAsStateWithLifecycle()

    // Global Call & Incoming Call Layer (ensures calls launched from PrivateChatScreen or anywhere always render cleanly)
    if (showCallAdminDashboard) {
        BackHandler { showCallAdminDashboard = false }
        CallAdminDashboardScreen(
            viewModel = viewModel,
            onDismiss = { showCallAdminDashboard = false }
        )
        return
    }

    if (selectedChatFriend != null) {
        val chatFriend = selectedChatFriend!!
        BackHandler { selectedChatFriend = null }
        DisposableEffect(chatFriend.id) {
            viewModel.openPrivateChat(chatFriend.id)
            onDispose {
                viewModel.closePrivateChat()
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            com.example.ui.screens.PrivateChatScreen(
                friend = chatFriend,
                viewModel = viewModel,
                messages = privateChatMessagesMap[chatFriend.id] ?: emptyList(),
                onBack = { selectedChatFriend = null }
            )
            activeCallSession?.let {
                CallScreen(
                    viewModel = viewModel,
                    onEndCall = { viewModel.endCallSession() }
                )
            }
            IncomingCallOverlay(
                invite = incomingCallInvite,
                onAccept = { viewModel.acceptIncomingCall(it) },
                onReject = { viewModel.rejectIncomingCall(it) }
            )
        }
        return
    }

    if (showFriendsDialog) {
        BackHandler { showFriendsDialog = false }
        Box(modifier = Modifier.fillMaxSize()) {
            com.example.ui.screens.FriendsScreen(
                viewModel = viewModel,
                friends = friends,
                onNavigateToChat = { friend ->
                    showFriendsDialog = false
                    selectedChatFriend = friend
                },
                onDismiss = { showFriendsDialog = false }
            )
            activeCallSession?.let {
                CallScreen(
                    viewModel = viewModel,
                    onEndCall = { viewModel.endCallSession() }
                )
            }
            IncomingCallOverlay(
                invite = incomingCallInvite,
                onAccept = { viewModel.acceptIncomingCall(it) },
                onReject = { viewModel.rejectIncomingCall(it) }
            )
        }
        return
    }

    // Unconditional state and launchers
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(isUserLoggedIn) {
        if (isUserLoggedIn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uiNotice) {
        uiNotice?.let { notice ->
            snackbarHostState.showSnackbar(notice)
            viewModel.clearNotice()
        }
    }

    val isAgentUser = userProfile?.isAgent == true

    // Conditional Agency tab: Only visible when influencer account is promoted to Agent
    val navItems = remember(isAgentUser) {
        if (isAgentUser) {
            listOf(
                AyerScreen.Match,
                AyerScreen.Live,
                AyerScreen.Wallet,
                AyerScreen.Agency,
                AyerScreen.Profile
            )
        } else {
            listOf(
                AyerScreen.Match,
                AyerScreen.Live,
                AyerScreen.Wallet,
                AyerScreen.Profile
            )
        }
    }

    // Safety fallback: If downgraded from Agent while on Agency screen, redirect to Profile
    LaunchedEffect(isAgentUser) {
        if (!isAgentUser && currentScreen == AyerScreen.Agency) {
            currentScreen = AyerScreen.Profile
        }
    }

    // Hide bottom navigation bar when actively in a live broadcast, globe zooming into partner, or video call
    val isFullBleedActive = (currentScreen == AyerScreen.Live && selectedStream != null) ||
            (currentScreen == AyerScreen.Match && (matchingStatus is MatchingStatus.Connected || matchingStatus is MatchingStatus.Found)) ||
            activeCallSession != null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HuhTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AnimatedVisibility(
                visible = !isFullBleedActive,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(180, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(160))
            ) {
                MainScreenTopBar(
                    userProfile = userProfile ?: UserProfile(),
                    onNavigateToProfile = { currentScreen = AyerScreen.Profile },
                    onOpenFriends = { showFriendsDialog = true },
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearchSubmitted = { queryText ->
                        if (queryText.isNotBlank()) {
                            currentScreen = AyerScreen.Live
                        }
                        searchQuery = ""
                    }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !isFullBleedActive,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(180, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(160))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(26.dp),
                    color = if (HuhTheme.colors.isDark) Color(0xF2120B24) else Color(0xF5FFFFFF),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                HuhTheme.colors.primary.copy(alpha = 0.5f),
                                HuhTheme.colors.secondary.copy(alpha = 0.4f),
                                HuhTheme.colors.tertiary.copy(alpha = 0.35f)
                            )
                        )
                    ),
                    shadowElevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navItems.forEach { screen ->
                            val selected = currentScreen == screen
                            val interactionScale by animateFloatAsState(
                                targetValue = if (selected) 1.05f else 1.0f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                label = "nav_scale"
                            )
                            val containerColor by animateColorAsState(
                                targetValue = if (selected) {
                                    HuhTheme.colors.primary.copy(alpha = if (HuhTheme.colors.isDark) 0.28f else 0.12f)
                                } else {
                                    Color.Transparent
                                },
                                animationSpec = tween(200),
                                label = "nav_bg_color"
                            )
                            val iconColor by animateColorAsState(
                                targetValue = if (selected) HuhTheme.colors.primary else HuhTheme.colors.textSecondary,
                                animationSpec = tween(200),
                                label = "nav_icon_color"
                            )

                            Surface(
                                modifier = Modifier
                                    .scale(interactionScale)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { currentScreen = screen }
                                    .testTag("nav_item_${screen.route}"),
                                shape = RoundedCornerShape(18.dp),
                                color = containerColor,
                                border = if (selected) {
                                    BorderStroke(1.dp, HuhTheme.colors.primary.copy(alpha = 0.6f))
                                } else null
                            ) {
                                 Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val localizedTitle = screen.getTitle(appLanguage)
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = localizedTitle,
                                        tint = iconColor,
                                        modifier = Modifier.size(21.dp)
                                    )
                                    AnimatedVisibility(
                                        visible = selected,
                                        enter = fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing)) +
                                                expandHorizontally(
                                                    expandFrom = Alignment.Start,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                ),
                                        exit = fadeOut(animationSpec = tween(150)) +
                                                shrinkHorizontally(
                                                    shrinkTowards = Alignment.Start,
                                                    animationSpec = tween(160, easing = FastOutLinearInEasing)
                                                )
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = localizedTitle,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (HuhTheme.colors.isDark) HuhTheme.colors.textPrimary else HuhTheme.colors.primary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val profile = userProfile ?: UserProfile()
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val initialIndex = navItems.indexOf(initialState)
                    val targetIndex = navItems.indexOf(targetState)
                    val forward = targetIndex >= initialIndex
                    val slideDir = if (forward) 1 else -1

                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> (fullWidth * 0.18f * slideDir).toInt() },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                      scaleIn(initialScale = 0.97f, animationSpec = tween(220))).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> (-fullWidth * 0.18f * slideDir).toInt() },
                            animationSpec = tween(180, easing = FastOutLinearInEasing)
                        ) + fadeOut(animationSpec = tween(180)) +
                        scaleOut(targetScale = 0.97f, animationSpec = tween(180))
                    )
                },
                label = "ScreenNavigationTransition",
                modifier = Modifier.fillMaxSize()
            ) { activeScreen ->
                when (activeScreen) {
                    is AyerScreen.Match -> {
                        MatchScreen(
                            viewModel = viewModel,
                            userBalance = profile.pointsBalance,
                            matchingStatus = matchingStatus,
                            selectedGender = selectedGender,
                            selectedCountry = selectedCountry,
                            selectedChatType = selectedChatType,
                            chatMessages = chatMessages,
                            isTranslationEnabled = isTranslationEnabled,
                            onNavigateToStore = { currentScreen = AyerScreen.Wallet },
                            onNavigateToLiveStream = { stream ->
                                viewModel.openLiveStream(stream)
                                currentScreen = AyerScreen.Live
                            }
                        )
                    }
                    is AyerScreen.Live -> {
                        LiveStreamScreen(
                            viewModel = viewModel,
                            userBalance = profile.pointsBalance,
                            selectedStream = selectedStream,
                            streamMessages = streamMessages,
                            activeGift = activeGift,
                            onNavigateToStore = { currentScreen = AyerScreen.Wallet }
                        )
                    }
                    is AyerScreen.Wallet -> {
                        WalletStoreScreen(
                            viewModel = viewModel,
                            balance = profile.pointsBalance,
                            transactions = transactions
                        )
                    }
                    is AyerScreen.Agency -> {
                        AgenciesScreen(
                            viewModel = viewModel,
                            userProfile = profile,
                            onOpenNotificationCenter = { showNotificationCenterDialog = true },
                            onOpenCallAdminDashboard = { showCallAdminDashboard = true }
                        )
                    }
                    is AyerScreen.Profile -> {
                        com.example.ui.screens.UserProfileScreen(
                            viewModel = viewModel,
                            userProfile = profile,
                            onNavigateToStore = { currentScreen = AyerScreen.Wallet },
                            onOpenNotificationCenter = { showNotificationCenterDialog = true },
                            onOpenAgenciesScreen = { currentScreen = AyerScreen.Agency }
                        )
                    }
                }
            }

            // In-app HUD notification banner (auto dismisses or clicks to open center)
            InAppNotificationBanner(
                notification = activeInAppNotification,
                onDismiss = { viewModel.dismissInAppNotification() },
                onOpenCenter = { showNotificationCenterDialog = true }
            )

            // 1-on-1 LiveKit / WebRTC Call Screen overlay
            activeCallSession?.let { _ ->
                CallScreen(
                    viewModel = viewModel,
                    onEndCall = { viewModel.endCallSession() }
                )
            }

            // Incoming 1-on-1 Call Banner Overlay
            IncomingCallOverlay(
                invite = incomingCallInvite,
                onAccept = { viewModel.acceptIncomingCall(it) },
                onReject = { viewModel.rejectIncomingCall(it) }
            )
        }

        // Full Influencer & Agent Notification Center Modal
        if (showNotificationCenterDialog) {
            NotificationCenterDialog(
                notifications = notifications,
                unreadCount = unreadNotificationCount,
                onDismiss = { showNotificationCenterDialog = false },
                onMarkAsRead = { viewModel.markNotificationAsRead(it) },
                onMarkAllAsRead = { viewModel.markAllNotificationsAsRead() },
                onClearAll = { viewModel.clearAllNotifications() }
            )
        }

        // Google Play In-App Review / Rate Us Dialog
        if (showRateUsDialog) {
            com.example.ui.components.RateUsDialog(
                completedMissionsCount = completedMissionsClaimCount.coerceAtLeast(1),
                onDismiss = { viewModel.dismissRateUsDialog() },
                onRated = { rating, tags ->
                    viewModel.recordUserRating(rating, tags)
                }
            )
        }
    }
}

/**
 * Top Bar for MainScreen with User Profile UI in the TOP RIGHT CORNER.
 * Left: App branding logo & expandable secret code search.
 * Top Right Corner: User Profile widget showing Avatar, Username, Total Points, and Current Agency Level.
 */
@Composable
fun MainScreenTopBar(
    userProfile: UserProfile,
    onNavigateToProfile: () -> Unit,
    onOpenFriends: () -> Unit,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearchExpanded by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT SIDE: App Branding or Expandable Search with smooth enter/exit
            AnimatedContent(
                targetState = isSearchExpanded,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(
                            initialOffsetX = { it / 3 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(animationSpec = tween(200))).togetherWith(
                            slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut(animationSpec = tween(150))
                        )
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(animationSpec = tween(200))).togetherWith(
                            slideOutHorizontally(targetOffsetX = { it / 3 }) + fadeOut(animationSpec = tween(150))
                        )
                    }
                },
                label = "TopBarSearchTransition",
                modifier = Modifier.weight(1f, fill = isSearchExpanded)
            ) { expanded ->
                if (!expanded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenFriends() }
                            .padding(4.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.huh_app_logo_1789389783248),
                            contentDescription = "HUH Logo - الأصدقاء",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HUH",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = HuhTheme.colors.textPrimary,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { isSearchExpanded = true },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(HuhTheme.colors.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "بحث في البثوث",
                                tint = HuhTheme.colors.gold,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                    ) {
                        AyerSearchBar(
                            query = searchQuery,
                            onQueryChange = onQueryChange,
                            onSearchSubmitted = {
                                onSearchSubmitted(it)
                                isSearchExpanded = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { isSearchExpanded = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق البحث",
                                tint = HuhTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // TOP RIGHT CORNER: The User Profile UI Widget!
            // Displays: Avatar, Username, Total Points, and Current Agency Level
            UserProfileTopCornerWidget(
                userProfile = userProfile,
                onClick = onNavigateToProfile
            )
        }
    }
}
