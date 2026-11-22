package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.UserProfile
import com.example.data.model.currentAgencyLevel
import com.example.ui.AyerViewModel
import com.example.ui.components.AgeVerifiedBadge
import com.example.ui.components.DailyMissionsCard
import com.example.ui.components.LeaderboardComponent
import com.example.ui.components.LanguageSelectionBottomSheet
import com.example.ui.components.LanguageSelectionSettingRow
import com.example.ui.locale.AppLanguage
import com.example.ui.locale.AppStrings
import com.example.ui.theme.AyerCyan
import com.example.ui.theme.HuhTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * User Milestone Badge Data Class
 */
data class UserMilestoneBadge(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val icon: ImageVector,
    val rarityColor: Color,
    val rarityLabel: String,
    val isUnlocked: Boolean,
    val progress: Int,
    val maxProgress: Int,
    val unlockedDate: String? = null,
    val rewardPoints: Int = 500
)

/**
 * Recent Activity Item Data Class
 */
data class RecentActivityItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val viewsCount: Int,
    val likesCount: Int,
    val pointsEarned: Int? = null,
    val tag: String,
    val isHighlight: Boolean = false,
    val icon: ImageVector = Icons.Default.LiveTv,
    val accentColor: Color = Color(0xFFFF2A85)
)

/**
 * Cover Preset Data Class
 */
data class CoverPreset(
    val id: Int,
    val title: String,
    val gradient: Brush,
    val previewColor: Color
)

/**
 * Real-time User Presence Status
 */
enum class UserPresenceStatus(
    val title: String,
    val description: String,
    val color: Color,
    val icon: ImageVector
) {
    ONLINE("متصل الآن", "متاح للدردشة والمطابقة الحية", Color(0xFF10B981), Icons.Default.CheckCircle),
    STREAMING("في بث مباشر 🔴", "تبث الآن لمتابعيك", Color(0xFFFF2A85), Icons.Default.Videocam),
    BUSY("مشغول", "وضع عدم الإزعاج", Color(0xFFF59E0B), Icons.Default.Tune),
    OFFLINE("غير متصل", "تظهر كغير نشط", Color(0xFF9CA3AF), Icons.Default.Close)
}

/**
 * Leveling system model and data
 */
data class UserLevelInfo(
    val level: Int,
    val title: String,
    val currentLevelXpStart: Int,
    val nextLevelXpEnd: Int,
    val currentPoints: Int,
    val badgeColor: Color,
    val perks: List<String>
) {
    val progress: Float
        get() {
            val range = (nextLevelXpEnd - currentLevelXpStart).coerceAtLeast(1)
            val currentInTier = (currentPoints - currentLevelXpStart).coerceAtLeast(0)
            return (currentInTier.toFloat() / range.toFloat()).coerceIn(0f, 1f)
        }

    val xpRemaining: Int
        get() = (nextLevelXpEnd - currentPoints).coerceAtLeast(0)
}

fun calculateUserLevel(pointsBalance: Int): UserLevelInfo {
    return when {
        pointsBalance < 500 -> UserLevelInfo(
            level = 1,
            title = "برونزي مبتدئ",
            currentLevelXpStart = 0,
            nextLevelXpEnd = 500,
            currentPoints = pointsBalance,
            badgeColor = Color(0xFFCD7F32),
            perks = listOf("دخول غرف الدردشة العامة", "مطابقات عشوائية أساسية", "10 نقاط يومية مجانية")
        )
        pointsBalance < 1500 -> UserLevelInfo(
            level = 2,
            title = "برونزي متقدم",
            currentLevelXpStart = 500,
            nextLevelXpEnd = 1500,
            currentPoints = pointsBalance,
            badgeColor = Color(0xFFB87333),
            perks = listOf("شارة برونزية بجانب الاسم", "إمكانية إرسال هدايا خاصة", "سرعة مطابقة أعلى 1.2x")
        )
        pointsBalance < 3500 -> UserLevelInfo(
            level = 3,
            title = "فضي متألق",
            currentLevelXpStart = 1500,
            nextLevelXpEnd = 3500,
            currentPoints = pointsBalance,
            badgeColor = Color(0xFFC0C0C0),
            perks = listOf("أولوية في طابور المطابقة", "شارة فضية براقة", "خصم 5% على الهدايا الفاخرة")
        )
        pointsBalance < 7000 -> UserLevelInfo(
            level = 4,
            title = "ذهبي متميز",
            currentLevelXpStart = 3500,
            nextLevelXpEnd = 7000,
            currentPoints = pointsBalance,
            badgeColor = Color(0xFFFFD700),
            perks = listOf("دخول غرف VIP الحصرية", "إبراز التعليقات في البث المباشر", "مضاعفة النقاط اليومية")
        )
        pointsBalance < 15000 -> UserLevelInfo(
            level = 5,
            title = "بلاتيني ملكي",
            currentLevelXpStart = 7000,
            nextLevelXpEnd = 15000,
            currentPoints = pointsBalance,
            badgeColor = Color(0xFFE5E4E2),
            perks = listOf("إطار ملكي متحرك للبروفايل", "دعم فني خاص مباشر", "خصم 15% على كل الميزات")
        )
        pointsBalance < 30000 -> UserLevelInfo(
            level = 6,
            title = "ماسي أسطوري",
            currentLevelXpStart = 15000,
            nextLevelXpEnd = 30000,
            currentPoints = pointsBalance,
            badgeColor = Color(0xFF00E5FF),
            perks = listOf("شارة التاج الماسي", "إمكانية إنشاء غرف بث خاصة VIP", "بث فائق الجودة 1080p")
        )
        else -> UserLevelInfo(
            level = 7,
            title = "نجم Ayer الأسطوري 👑",
            currentLevelXpStart = 30000,
            nextLevelXpEnd = 50000,
            currentPoints = pointsBalance,
            badgeColor = Color(0xFFFF2A85),
            perks = listOf("أعلى مكانة في المنصة", "ظهور دائم في لوحة الشرف", "تأثيرات دخول أسطورية في كل البثات")
        )
    }
}

/**
 * Animated Entrance Wrapper for Cards (Fade-in + Slide-up)
 */
@Composable
fun AnimatedEntranceCard(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val alphaAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(36f) }

    LaunchedEffect(Unit) {
        delay((index * 55L).coerceAtMost(350L))
        coroutineScope {
            launch {
                alphaAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 380,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            launch {
                offsetYAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 420,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alphaAnim.value)
            .offset(y = offsetYAnim.value.dp)
    ) {
        content()
    }
}

/**
 * UserProfileScreen Component
 * Displays user identity & stats, points economy & balance breakdown, and official agency affiliation
 * structured with a modern, high-contrast Card-based layout.
 */
@Composable
fun UserProfileScreen(
    viewModel: AyerViewModel,
    userProfile: UserProfile,
    onNavigateToStore: () -> Unit = {},
    onOpenNotificationCenter: () -> Unit = {},
    onOpenCreateSecondaryProfileDialog: () -> Unit = {},
    onOpenAgenciesScreen: () -> Unit = {},
    onOpenCallAdminDashboard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkThemePref by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isDarkTheme = isDarkThemePref ?: androidx.compose.foundation.isSystemInDarkTheme()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val activeSecondaryProfile by viewModel.activeSecondaryProfile.collectAsStateWithLifecycle()
    val engagementState by viewModel.engagementState.collectAsStateWithLifecycle()
    val dailyMissions by viewModel.dailyMissions.collectAsStateWithLifecycle()
    val isTwoFactorAuthEnabled by viewModel.isTwoFactorAuthEnabled.collectAsStateWithLifecycle()
    val lastLoginTimestamp by viewModel.lastLoginTimestamp.collectAsStateWithLifecycle()
    val lastLoginIpAndDevice by viewModel.lastLoginIpAndDevice.collectAsStateWithLifecycle()

    val profileVisibility by viewModel.profileVisibility.collectAsStateWithLifecycle()
    val directMessagePermission by viewModel.directMessagePermission.collectAsStateWithLifecycle()
    val showOnlineStatus by viewModel.showOnlineStatus.collectAsStateWithLifecycle()
    val hideLocationInMatch by viewModel.hideLocationInMatch.collectAsStateWithLifecycle()
    val uiNotice by viewModel.uiNotice.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val lazyListState = rememberLazyListState()

    var showTransactionHistoryScreen by remember { mutableStateOf(false) }
    var showLeaderboardDialog by remember { mutableStateOf(false) }
    var showLanguageBottomSheet by remember { mutableStateOf(false) }
    var showEditAccountDialog by remember { mutableStateOf(false) }
    var showPresenceDialog by remember { mutableStateOf(false) }
    var showQrCodeDialog by remember { mutableStateOf(false) }
    var showCoverPickerBottomSheet by remember { mutableStateOf(false) }
    var showReportUserBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiNotice) {
        uiNotice?.let { notice ->
            snackbarHostState.showSnackbar(notice)
            viewModel.clearUiNotice()
        }
    }
    var selectedCoverPresetIndex by remember { mutableIntStateOf(0) }
    var customCoverUri by remember { mutableStateOf<String?>(null) }
    var selectedMilestoneDetail by remember { mutableStateOf<UserMilestoneBadge?>(null) }
    var showNewHighlightDialog by remember { mutableStateOf(false) }
    var userPresenceStatus by remember { mutableStateOf(UserPresenceStatus.ONLINE) }

    var editName by remember(userProfile.name) { mutableStateOf(userProfile.name) }
    var editBio by remember(userProfile.bio) { mutableStateOf(userProfile.bio) }
    var editCountry by remember(userProfile.country) { mutableStateOf(userProfile.country) }
    var editPhone by remember(userProfile.phoneNumber) { mutableStateOf(userProfile.phoneNumber) }
    var selectedAvatarResId by remember(userProfile.avatarResId) { mutableStateOf(userProfile.avatarResId) }
    var selectedCustomUri by remember(userProfile.avatarCustomUri) { mutableStateOf(userProfile.avatarCustomUri) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedCustomUri = uri.toString()
        }
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            customCoverUri = uri.toString()
            showCoverPickerBottomSheet = false
            Toast.makeText(context, "تم تحديث صورة الغلاف بنجاح ✨", Toast.LENGTH_SHORT).show()
        }
    }

    val presetAvatars = remember {
        listOf(
            Pair(R.drawable.img_user_avatar, "الافتراضي"),
            Pair(R.drawable.ic_ayer_logo, "شعار Ayer"),
            Pair(R.drawable.img_live_model_1, "مودل 1"),
            Pair(R.drawable.img_live_model_2, "مودل 2"),
            Pair(R.drawable.img_live_model_3, "مودل 3"),
            Pair(R.drawable.stream_hero_bg, "خلفية VIP")
        )
    }

    // Share Profile function opening native Android share sheet
    val onShareProfile: () -> Unit = {
        val levelInfo = calculateUserLevel(userProfile.pointsBalance)
        val shareText = """
🌟 بروفايل تطبيق Ayer | التعارف والبث المباشر
━━━━━━━━━━━━━━━━━━━━
👤 الاسم: ${userProfile.name}
🎖️ المستوى: Lv.${levelInfo.level} (${levelInfo.title})
👑 الرتبة: ${userProfile.currentAgencyLevel}
📍 البلد: ${userProfile.country}
💎 رصيد النقاط: ${userProfile.pointsBalance} نقطة
🔥 التتابع اليومي: ${engagementState.dailyStreakDays} أيام متواصلة
🎙️ ساعات البث: ${(userProfile.totalMatchesCount * 0.8 + 18.5).toInt()} ساعة
🤝 الأصدقاء: ${(userProfile.totalMatchesCount * 3 + 28)} صديق
🌐 كود الدعوة: AYER-${userProfile.id * 8921 + 1000}

انضم إلى تطبيق Ayer واستمتع بالبث المباشر والمحادثات العشوائية:
https://ayer.app/u/${userProfile.id}
""".trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareChooser = Intent.createChooser(sendIntent, "مشاركة الملف التعريفي لـ ${userProfile.name}")
        context.startActivity(shareChooser)
    }

    if (showTransactionHistoryScreen) {
        TransactionHistoryScreen(
            viewModel = viewModel,
            onBack = { showTransactionHistoryScreen = false }
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HuhTheme.colors.background)
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // ==========================================
            // 0. Customizable Cover Header with Dynamic Scroll Blur
            // ==========================================
            item {
                AnimatedEntranceCard(index = 0) {
                    UserProfileCoverHeader(
                        customCoverUri = customCoverUri,
                        selectedCoverPresetIndex = selectedCoverPresetIndex,
                        scrollState = lazyListState,
                        onEditCoverClick = { showCoverPickerBottomSheet = true }
                    )
                }
            }

            // ==========================================
            // 0.5 Profile Completion Progress & Suggestions Card
            // ==========================================
            item {
                AnimatedEntranceCard(index = 1) {
                    ProfileCompletionCard(
                        userProfile = userProfile,
                        onCompleteFieldClick = { showEditAccountDialog = true }
                    )
                }
            }

            // ==========================================
            // 1. User Profile & Identity Header Card
            // ==========================================
            item {
                AnimatedEntranceCard(index = 2) {
                    UserIdentityHeaderCard(
                        userProfile = userProfile,
                        userEmail = viewModel.getCurrentUserEmail() ?: "sam0000111110@gmail.com",
                        activeSecondaryProfileName = activeSecondaryProfile?.displayName,
                        engagementState = engagementState,
                        presenceStatus = userPresenceStatus,
                        onPresenceStatusClick = { showPresenceDialog = true },
                        onShareProfileClick = onShareProfile,
                        onQrCodeClick = { showQrCodeDialog = true },
                        onEditProfileClick = { showEditAccountDialog = true },
                        onNotificationCenterClick = onOpenNotificationCenter,
                        unreadNotificationCount = unreadNotificationCount,
                        onLeaderboardClick = { showLeaderboardDialog = true },
                        onCopyUserId = { userIdStr ->
                            clipboardManager.setText(AnnotatedString(userIdStr))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("تم نسخ معرّف الحساب (ID: $userIdStr) إلى الحافظة بنجاح 📋")
                            }
                        },
                        onReportUserClick = {
                            showReportUserBottomSheet = true
                        }
                    )
                }
            }

            // ==========================================
            // 2. Badges & Achievements Section (Horizontal Scrollable Row)
            // ==========================================
            item {
                AnimatedEntranceCard(index = 2) {
                    BadgesAndAchievementsCard(
                        onMilestoneClick = { selectedMilestoneDetail = it }
                    )
                }
            }

            // ==========================================
            // 3. User Leveling & XP Progress Card
            // ==========================================
            item {
                AnimatedEntranceCard(index = 3) {
                    UserLevelingSystemCard(
                        userProfile = userProfile,
                        onRechargeClick = onNavigateToStore
                    )
                }
            }

            // ==========================================
            // 4. Weekly Engagement & Activity Visual Chart Card
            // ==========================================
            item {
                AnimatedEntranceCard(index = 4) {
                    WeeklyEngagementCanvasChartCard(
                        engagementState = engagementState,
                        userProfile = userProfile
                    )
                }
            }

            // ==========================================
            // 5. Mini 'Recent Activity' Feed (Vertical List)
            // ==========================================
            item {
                AnimatedEntranceCard(index = 5) {
                    RecentActivityFeedCard(
                        onAddHighlightClick = { showNewHighlightDialog = true }
                    )
                }
            }

            // ==========================================
            // 6. Points Balance & Economy Card
            // ==========================================
            item {
                AnimatedEntranceCard(index = 6) {
                    UserPointsBalanceCard(
                        userProfile = userProfile,
                        onRechargeClick = onNavigateToStore,
                        onViewTransactionsClick = { showTransactionHistoryScreen = true },
                        onClaimDailyFreeClick = { viewModel.claimDailyBonus() }
                    )
                }
            }

            // ==========================================
            // 7. User Activity & Engagement Statistics Card
            // ==========================================
            item {
                AnimatedEntranceCard(index = 7) {
                    UserActivityStatisticsCard(
                        userProfile = userProfile,
                        engagementState = engagementState
                    )
                }
            }

            // ==========================================
            // 8. Agency Affiliation & Host/Agent Status Card
            // ==========================================
            item {
                AnimatedEntranceCard(index = 8) {
                    AgencyAffiliationCard(
                        userProfile = userProfile,
                        onOpenAgencyDashboard = onOpenAgenciesScreen,
                        onSimulatePayout = { viewModel.triggerSampleCommissionPayout() }
                    )
                }
            }

            // ==========================================
            // 9. Daily Missions & Tasks Card
            // ==========================================
            item {
                AnimatedEntranceCard(index = 9) {
                    DailyMissionsCard(
                        missions = dailyMissions,
                        onClaimReward = { missionId ->
                            viewModel.claimDailyMissionReward(missionId)
                        }
                    )
                }
            }

            // ==========================================
            // 10. Privacy & Visibility Settings Card
            // ==========================================
            item {
                AnimatedEntranceCard(index = 10) {
                    PrivacySettingsCard(
                        profileVisibility = profileVisibility,
                        onSelectVisibility = { viewModel.setProfileVisibility(it) },
                        directMessagePermission = directMessagePermission,
                        onSelectMessagePermission = { viewModel.setDirectMessagePermission(it) },
                        showOnlineStatus = showOnlineStatus,
                        onToggleOnlineStatus = { viewModel.setShowOnlineStatus(it) },
                        hideLocationInMatch = hideLocationInMatch,
                        onToggleHideLocation = { viewModel.setHideLocationInMatch(it) }
                    )
                }
            }

            // ==========================================
            // 11. Security & Transparency Card (2FA & Last Login)
            // ==========================================
            item {
                AnimatedEntranceCard(index = 11) {
                    AccountSecurityCard(
                        isTwoFactorAuthEnabled = isTwoFactorAuthEnabled,
                        onToggleTwoFactorAuth = { viewModel.setTwoFactorAuthEnabled(it) },
                        lastLoginTimestamp = lastLoginTimestamp,
                        lastLoginIpAndDevice = lastLoginIpAndDevice
                    )
                }
            }

            // ==========================================
            // 12. Account Preferences & Quick Settings Card
            // ==========================================
            item {
                AnimatedEntranceCard(index = 12) {
                    AccountPreferencesCard(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { viewModel.setDarkMode(!isDarkTheme) },
                        appLanguage = appLanguage,
                        onOpenLanguageSelection = { showLanguageBottomSheet = true },
                        onCreateSecondaryProfile = onOpenCreateSecondaryProfileDialog,
                        onLogout = { viewModel.logoutUser() }
                    )
                }
            }

            // ==========================================
            // 13. Call Management & Monitoring (Admin Owner Control)
            // ==========================================
            item {
                AnimatedEntranceCard(index = 13) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_call_admin_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
                        border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(AyerCyan.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = "إدارة المكالمات",
                                            tint = AyerCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Call Management & Monitoring",
                                            color = HuhTheme.colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "مراقبة المكالمات الصوتية والمرئية والميزانية والحدود",
                                            color = HuhTheme.colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onOpenCallAdminDashboard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("profile_open_call_admin_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.primary)
                            ) {
                                Text(
                                    text = "فتح لوحة مراقبة وإدارة المكالمات 📊",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showReportUserBottomSheet) {
            ReportUserBottomSheet(
                targetUserId = "AYER-${userProfile.id * 8921 + 1000}",
                targetUserName = userProfile.name,
                onDismiss = { showReportUserBottomSheet = false },
                onSubmitReport = { reason, details ->
                    showReportUserBottomSheet = false
                    viewModel.submitUserReport(
                        targetUserId = "AYER-${userProfile.id * 8921 + 1000}",
                        targetUserName = userProfile.name,
                        reason = reason,
                        details = details
                    )
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )

        // ==========================================
        // Floating Action Button for Unique QR Code Profile Sharing
        // ==========================================
        FloatingActionButton(
            onClick = { showQrCodeDialog = true },
            containerColor = HuhTheme.colors.primary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_share_qr_code")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = "مشاركة رمز QR للبروفايل",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "رمز QR",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
    }

    // Presence Status Selection Dialog
    if (showPresenceDialog) {
        AlertDialog(
            onDismissRequest = { showPresenceDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = HuhTheme.colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "تحديد حالة الظهور والاتصال",
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "اختر الحالة التي ستظهر للآخرين أثناء تصفح التطبيق:",
                        fontSize = 13.sp,
                        color = HuhTheme.colors.textSecondary
                    )

                    UserPresenceStatus.values().forEach { status ->
                        val isSelected = userPresenceStatus == status
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) status.color.copy(alpha = 0.15f) else HuhTheme.colors.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) status.color else HuhTheme.colors.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    userPresenceStatus = status
                                    showPresenceDialog = false
                                }
                                .testTag("presence_status_option_${status.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(status.color.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = status.icon,
                                            contentDescription = null,
                                            tint = status.color,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = status.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = HuhTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = status.description,
                                            fontSize = 11.sp,
                                            color = HuhTheme.colors.textTertiary
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "محدد",
                                        tint = status.color,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPresenceDialog = false }) {
                    Text("إغلاق", color = HuhTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = HuhTheme.colors.surfaceElevated
        )
    }

    // Edit Profile Dialog (Updates Name & Profile Picture)
    if (showEditAccountDialog) {
        AlertDialog(
            onDismissRequest = { showEditAccountDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = HuhTheme.colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "تعديل الملف الشخصي والصورة",
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Picture Live Preview & Picker
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .border(
                                3.dp,
                                Brush.linearGradient(
                                    listOf(
                                        HuhTheme.colors.primary,
                                        HuhTheme.colors.gold,
                                        HuhTheme.colors.secondary
                                    )
                                ),
                                CircleShape
                            )
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("edit_profile_avatar_preview"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedCustomUri != null) {
                            AsyncImage(
                                model = selectedCustomUri,
                                contentDescription = "صورة البروفايل المختارة",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(id = selectedAvatarResId),
                                contentDescription = "صورة البروفايل المختارة",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Camera Icon Overlay
                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.BottomEnd),
                            shape = CircleShape,
                            color = HuhTheme.colors.primary,
                            border = BorderStroke(1.5.dp, Color.White)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "تغيير الصورة",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Gallery Photo Picker Button
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, HuhTheme.colors.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_pick_photo_from_gallery")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = HuhTheme.colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "اختيار صورة من الاستوديو 🖼️",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = HuhTheme.colors.primary
                        )
                    }

                    // Preset Avatars Selector
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "أو اختر رمزاً تعبيرياً / صورة رمزية جاهزة:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HuhTheme.colors.textSecondary
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(presetAvatars) { (drawableRes, name) ->
                                val isSelected = selectedCustomUri == null && selectedAvatarResId == drawableRes
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedCustomUri = null
                                            selectedAvatarResId = drawableRes
                                        }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) HuhTheme.colors.primary else Color.Gray.copy(alpha = 0.3f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = drawableRes),
                                            contentDescription = name,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )

                                        if (isSelected) {
                                            Surface(
                                                shape = CircleShape,
                                                color = HuhTheme.colors.primary,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .align(Alignment.BottomEnd)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.padding(2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = name,
                                        fontSize = 9.sp,
                                        color = if (isSelected) HuhTheme.colors.primary else HuhTheme.colors.textSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = Color.Gray.copy(alpha = 0.2f),
                        thickness = 0.8.dp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Form Fields: Display Name, Bio, Country, Phone
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("الاسم الكامل / اسم العرض") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = HuhTheme.colors.primary
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_profile_display_name_input")
                            .testTag("edit_name_text_field"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editCountry,
                        onValueChange = { editCountry = it },
                        label = { Text("الدولة / المدينة") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = HuhTheme.colors.primary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("رقم الجوال") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = HuhTheme.colors.primary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("نبذة شخصية (Bio)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = HuhTheme.colors.primary
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_bio_text_field"),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAccountSettings(
                            name = editName.ifBlank { userProfile.name },
                            bio = editBio,
                            country = editCountry.ifBlank { userProfile.country },
                            phoneNumber = editPhone.ifBlank { userProfile.phoneNumber },
                            avatarResId = selectedAvatarResId,
                            avatarCustomUri = selectedCustomUri
                        )
                        showEditAccountDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.primary),
                    modifier = Modifier
                        .testTag("save_profile_button")
                        .testTag("save_account_settings_button")
                ) {
                    Text("حفظ التغييرات", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditAccountDialog = false },
                    modifier = Modifier.testTag("cancel_edit_profile_button")
                ) {
                    Text("إلغاء", color = HuhTheme.colors.textSecondary)
                }
            },
            containerColor = HuhTheme.colors.surfaceElevated
        )
    }

    // Leaderboard Dialog
    if (showLeaderboardDialog) {
        AlertDialog(
            onDismissRequest = { showLeaderboardDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🏆 لوحة الشرف والمتصدرين",
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary,
                        fontSize = 17.sp
                    )
                    IconButton(onClick = { showLeaderboardDialog = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = HuhTheme.colors.textSecondary)
                    }
                }
            },
            text = {
                LeaderboardComponent(viewModel = viewModel)
            },
            confirmButton = {},
            containerColor = HuhTheme.colors.surfaceElevated
        )
    }

    // Language Selection Bottom Sheet
    if (showLanguageBottomSheet) {
        LanguageSelectionBottomSheet(
            currentLanguage = appLanguage,
            onLanguageSelected = { lang ->
                viewModel.setAppLanguage(lang)
                showLanguageBottomSheet = false
            },
            onDismiss = { showLanguageBottomSheet = false }
        )
    }

    // QR Code Sharing Dialog
    if (showQrCodeDialog) {
        UserProfileQrCodeDialog(
            userProfile = userProfile,
            onDismiss = { showQrCodeDialog = false },
            onShareSheet = onShareProfile
        )
    }

    // Cover Image Picker Bottom Sheet / Dialog
    if (showCoverPickerBottomSheet) {
        CoverPresetSelectionDialog(
            selectedIndex = selectedCoverPresetIndex,
            onSelectPreset = { idx ->
                selectedCoverPresetIndex = idx
                customCoverUri = null
                showCoverPickerBottomSheet = false
                Toast.makeText(context, "تم تطبيق غلاف جديد بنجاح 🎨", Toast.LENGTH_SHORT).show()
            },
            onPickCustomImage = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDismiss = { showCoverPickerBottomSheet = false }
        )
    }

    // Milestone Detail Dialog
    selectedMilestoneDetail?.let { badge ->
        MilestoneDetailDialog(
            badge = badge,
            onDismiss = { selectedMilestoneDetail = null }
        )
    }

    // Create New Highlight Dialog
    if (showNewHighlightDialog) {
        CreateNewHighlightDialog(
            onDismiss = { showNewHighlightDialog = false },
            onSave = { title, desc, tag ->
                Toast.makeText(context, "تم نشر الهايلايت بنجاح في ملفك التعريفي ✨", Toast.LENGTH_SHORT).show()
                showNewHighlightDialog = false
            }
        )
    }
}

// =========================================================================
// SUB-COMPONENTS (Card-Based Layouts)
// =========================================================================

/**
 * Card 1: User Identity & Profile Header
 */
@Composable
fun UserIdentityHeaderCard(
    userProfile: UserProfile,
    userEmail: String,
    activeSecondaryProfileName: String?,
    engagementState: com.example.data.model.UserEngagementState,
    presenceStatus: UserPresenceStatus = UserPresenceStatus.ONLINE,
    onPresenceStatusClick: () -> Unit = {},
    onShareProfileClick: () -> Unit = {},
    onQrCodeClick: () -> Unit = {},
    onEditProfileClick: () -> Unit,
    onNotificationCenterClick: () -> Unit,
    unreadNotificationCount: Int,
    onLeaderboardClick: () -> Unit,
    onCopyUserId: (String) -> Unit = {},
    onReportUserClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("user_identity_header_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, HuhTheme.colors.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Top utility actions: Notification Bell, Leaderboard Trophy, QR Code, and Share Profile
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary profile pill if active
                if (!activeSecondaryProfileName.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = HuhTheme.colors.secondary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, HuhTheme.colors.secondary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwitchAccount,
                                contentDescription = null,
                                tint = HuhTheme.colors.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "بروفايل: $activeSecondaryProfileName",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.secondary
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = HuhTheme.colors.gold.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, HuhTheme.colors.gold.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "👑 الحساب الرئيسي",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.gold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // QR Code Share Button
                    Surface(
                        shape = CircleShape,
                        color = HuhTheme.colors.surfaceVariant,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onQrCodeClick() }
                            .testTag("btn_user_profile_qr")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "عرض رمز QR",
                                tint = HuhTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Share Profile Button
                    Surface(
                        shape = CircleShape,
                        color = HuhTheme.colors.surfaceVariant,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onShareProfileClick() }
                            .testTag("btn_share_profile")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "مشاركة الملف الشخصي",
                                tint = HuhTheme.colors.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Leaderboard Button
                    Surface(
                        shape = CircleShape,
                        color = HuhTheme.colors.surfaceVariant,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onLeaderboardClick() }
                            .testTag("btn_user_profile_leaderboard")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "لوحة الشرف",
                                tint = HuhTheme.colors.gold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Notification Button
                    Surface(
                        shape = CircleShape,
                        color = HuhTheme.colors.surfaceVariant,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onNotificationCenterClick() }
                            .testTag("btn_user_profile_notifications")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "الإشعارات",
                                tint = HuhTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            if (unreadNotificationCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFFFF2A85), CircleShape)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Avatar & Main Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(
                                2.5.dp,
                                Brush.linearGradient(
                                    listOf(HuhTheme.colors.primary, HuhTheme.colors.gold, HuhTheme.colors.secondary)
                                ),
                                CircleShape
                            )
                            .clickable { onEditProfileClick() }
                            .testTag("user_profile_avatar_clickable"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (userProfile.avatarCustomUri != null) {
                            AsyncImage(
                                model = userProfile.avatarCustomUri,
                                contentDescription = "صورة المستخدم",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(id = userProfile.avatarResId),
                                contentDescription = "صورة المستخدم",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Camera Icon Overlay
                        Surface(
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.BottomEnd)
                                .testTag("avatar_edit_overlay"),
                            shape = CircleShape,
                            color = HuhTheme.colors.primary,
                            border = BorderStroke(1.5.dp, Color.White)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "تعديل الصورة",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    // Real-time Status Indicator on Avatar (Bottom-Start)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = (-2).dp, y = 2.dp)
                            .clip(CircleShape)
                            .clickable { onPresenceStatusClick() }
                            .testTag("user_avatar_presence_indicator"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (presenceStatus == UserPresenceStatus.ONLINE || presenceStatus == UserPresenceStatus.STREAMING) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .scale(pulseScale)
                                    .alpha(pulseAlpha)
                                    .background(presenceStatus.color, CircleShape)
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = presenceStatus.color,
                            border = BorderStroke(2.dp, HuhTheme.colors.surfaceElevated),
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color.White, CircleShape)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = userProfile.name,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("user_profile_display_name_text")
                        )
                        AgeVerifiedBadge()
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = userEmail,
                        fontSize = 12.sp,
                        color = HuhTheme.colors.gold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val formattedUserId = "AYER-${userProfile.id * 8921 + 1000}"

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = HuhTheme.colors.surfaceVariant,
                            border = BorderStroke(0.8.dp, HuhTheme.colors.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ID: $formattedUserId",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HuhTheme.colors.primary
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onCopyUserId(formattedUserId) }
                                        .testTag("btn_copy_user_id")
                                        .testTag("copy_user_id_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "نسخ معرّف الحساب",
                                        tint = HuhTheme.colors.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFF2A85).copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, Color(0xFFFF2A85).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onReportUserClick() }
                                .testTag("btn_report_user")
                                .testTag("report_user_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = "إبلاغ عن هذا الحساب",
                                    tint = Color(0xFFFF2A85),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "إبلاغ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF2A85)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "📍 ${userProfile.country} • ${userProfile.age} سنة",
                            fontSize = 12.sp,
                            color = HuhTheme.colors.textSecondary
                        )

                        // Clickable Presence Status Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = presenceStatus.color.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, presenceStatus.color.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPresenceStatusClick() }
                                .testTag("user_status_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(presenceStatus.color, CircleShape)
                                )
                                Text(
                                    text = presenceStatus.title,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = presenceStatus.color
                                )
                            }
                        }
                    }
                }
            }

            if (userProfile.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = HuhTheme.colors.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"${userProfile.bio}\"",
                        fontSize = 12.sp,
                        color = HuhTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Visual breakdown of user statistics: Row of Data Chips
            UserStatsChipsRow(
                userProfile = userProfile,
                engagementState = engagementState,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Edit Profile and Share Profile
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onEditProfileClick,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HuhTheme.colors.primary.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_profile_button")
                        .testTag("user_profile_edit_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = HuhTheme.colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تعديل الملف",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HuhTheme.colors.primary
                    )
                }

                Button(
                    onClick = onShareProfileClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.secondary),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "مشاركة البروفايل",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Card 2: User Leveling System & XP Progress
 */
@Composable
fun UserLevelingSystemCard(
    userProfile: UserProfile,
    onRechargeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val levelInfo = remember(userProfile.pointsBalance) {
        calculateUserLevel(userProfile.pointsBalance)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("level_system_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, levelInfo.badgeColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Level Badge & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = levelInfo.badgeColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.5.dp, levelInfo.badgeColor),
                        modifier = Modifier.testTag("user_level_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = levelInfo.badgeColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Lv.${levelInfo.level}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = levelInfo.badgeColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = levelInfo.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = "نظام المستويات والترقيات • Ayer VIP",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textTertiary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = levelInfo.badgeColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, levelInfo.badgeColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "${(levelInfo.progress * 100).toInt()}% مكتمل",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = levelInfo.badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // XP Progress Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HuhTheme.colors.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نقاط الخبرة (XP)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HuhTheme.colors.textSecondary
                    )
                    Text(
                        text = "${levelInfo.currentPoints} / ${levelInfo.nextLevelXpEnd} XP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { levelInfo.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .testTag("level_progress_bar"),
                    color = levelInfo.badgeColor,
                    trackColor = HuhTheme.colors.surfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "المستوى الحالي: Lv.${levelInfo.level}",
                        fontSize = 10.sp,
                        color = HuhTheme.colors.textTertiary
                    )
                    Text(
                        text = if (levelInfo.level < 6) "متبقي ${levelInfo.xpRemaining} XP لـ Lv.${levelInfo.level + 1}" else "أعلى رتبة VIP ⭐",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = levelInfo.badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current Level Perks List
            Text(
                text = "مزايا مستواك الحالي ✨:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = HuhTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                levelInfo.perks.forEach { perk ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = levelInfo.badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = perk,
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card 3: Weekly Engagement & Activity Visual Chart Card (Canvas Implementation)
 */
@Composable
fun WeeklyEngagementCanvasChartCard(
    engagementState: com.example.data.model.UserEngagementState,
    userProfile: UserProfile,
    modifier: Modifier = Modifier
) {
    var selectedMetricTab by remember { mutableIntStateOf(0) } // 0: Stream Time, 1: Coins, 2: Matches
    var selectedDayIndex by remember { mutableStateOf<Int?>(6) } // Default to today (last index)

    val daysOfWeek = remember { listOf("السبت", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "اليوم") }

    val dataPoints = remember(selectedMetricTab, engagementState.todayStreamMinutes, userProfile.pointsBalance) {
        when (selectedMetricTab) {
            0 -> listOf(25f, 40f, 35f, 65f, 50f, 80f, engagementState.todayStreamMinutes.toFloat().coerceAtLeast(45f))
            1 -> listOf(120f, 220f, 180f, 360f, 290f, 480f, (userProfile.pointsBalance * 0.15f + 350f))
            else -> listOf(8f, 14f, 10f, 20f, 16f, 24f, engagementState.todayMatchesCount.toFloat().coerceAtLeast(18f))
        }
    }

    val metricUnit = when (selectedMetricTab) {
        0 -> "دقيقة"
        1 -> "نقطة"
        else -> "مطابقة"
    }

    val metricColor = when (selectedMetricTab) {
        0 -> AyerCyan
        1 -> HuhTheme.colors.gold
        else -> HuhTheme.colors.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("engagement_activity_chart"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, metricColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
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
                            .background(metricColor.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = metricColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "مؤشر النشاط الأسبوعي",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = "Activity Trends (Last 7 Days)",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textTertiary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+28% نمو",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metric Selector Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(0, "ساعات البث", "tab_stream_time"),
                    Triple(1, "النقاط", "tab_coins"),
                    Triple(2, "المطابقات", "tab_matches")
                ).forEach { (tabIndex, label, tag) ->
                    val isSelected = selectedMetricTab == tabIndex
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) metricColor.copy(alpha = 0.2f) else HuhTheme.colors.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) metricColor else HuhTheme.colors.surfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selectedMetricTab = tabIndex
                                selectedDayIndex = 6
                            }
                            .testTag(tag)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) metricColor else HuhTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selected Day Metric Preview Card
            selectedDayIndex?.let { dayIdx ->
                val dayName = daysOfWeek[dayIdx]
                val value = dataPoints[dayIdx].toInt()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = HuhTheme.colors.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, metricColor.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "نشاط يوم $dayName:",
                            fontSize = 12.sp,
                            color = HuhTheme.colors.textSecondary
                        )
                        Text(
                            text = "$value $metricUnit",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = metricColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Chart
            val surfaceElevatedColor = HuhTheme.colors.surfaceElevated
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("weekly_chart_canvas")
                ) {
                    val width = size.width
                    val height = size.height
                    val paddingBottom = 28f
                    val chartHeight = height - paddingBottom
                    val maxVal = (dataPoints.maxOrNull() ?: 100f) * 1.25f
                    val minVal = 0f

                    val stepX = width / (dataPoints.size - 1).coerceAtLeast(1)

                    // Draw subtle grid lines
                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val y = chartHeight * (i.toFloat() / gridSteps.toFloat())
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }

                    // Compute points
                    val points = dataPoints.mapIndexed { index, value ->
                        val x = index * stepX
                        val normalized = (value - minVal) / (maxVal - minVal).coerceAtLeast(1f)
                        val y = chartHeight - (normalized * (chartHeight - 16f))
                        Offset(x, y)
                    }

                    // Construct smooth Bézier path
                    val path = Path().apply {
                        if (points.isNotEmpty()) {
                            moveTo(points.first().x, points.first().y)
                            for (i in 0 until points.size - 1) {
                                val p0 = points[i]
                                val p1 = points[i + 1]
                                val controlPointX1 = p0.x + (p1.x - p0.x) / 2f
                                val controlPointX2 = p0.x + (p1.x - p0.x) / 2f
                                cubicTo(
                                    controlPointX1, p0.y,
                                    controlPointX2, p1.y,
                                    p1.x, p1.y
                                )
                            }
                        }
                    }

                    // Construct filled gradient path
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(width, chartHeight)
                        lineTo(0f, chartHeight)
                        close()
                    }

                    // Draw gradient fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                metricColor.copy(alpha = 0.40f),
                                metricColor.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = chartHeight
                        )
                    )

                    // Draw stroke path
                    drawPath(
                        path = path,
                        color = metricColor,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )

                    // Draw vertex circles
                    points.forEachIndexed { index, point ->
                        val isSelected = selectedDayIndex == index
                        val radius = if (isSelected) 6f else 4f

                        // Outer glow
                        drawCircle(
                            color = if (isSelected) metricColor else metricColor.copy(alpha = 0.6f),
                            radius = radius * 1.6f,
                            center = point
                        )

                        // Inner dot
                        drawCircle(
                            color = if (isSelected) Color.White else surfaceElevatedColor,
                            radius = radius,
                            center = point
                        )
                    }
                }
            }

            // Day labels row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                daysOfWeek.forEachIndexed { index, day ->
                    val isSelected = selectedDayIndex == index
                    Text(
                        text = day,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) metricColor else HuhTheme.colors.textTertiary,
                        modifier = Modifier.clickable { selectedDayIndex = index }
                    )
                }
            }
        }
    }
}

/**
 * Visual breakdown of user statistics using Material 3 Data Chips
 */
@Composable
fun UserStatsChipsRow(
    userProfile: UserProfile,
    engagementState: com.example.data.model.UserEngagementState,
    modifier: Modifier = Modifier
) {
    val totalHoursStreamed = remember(userProfile.totalMatchesCount, engagementState.todayStreamMinutes) {
        String.format("%.1f", (userProfile.totalMatchesCount * 0.8 + 18.5))
    }
    val totalFriendsCount = remember(userProfile.totalMatchesCount) {
        (userProfile.totalMatchesCount * 3 + 28).coerceAtLeast(14)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ملخص الإحصائيات والأرقام 📊",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = HuhTheme.colors.textPrimary
            )
            Text(
                text = "إجمالي التفاعل",
                fontSize = 10.sp,
                color = HuhTheme.colors.textTertiary
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("user_stats_chips_row")
        ) {
            item {
                UserStatDataChip(
                    icon = Icons.Default.Videocam,
                    value = "$totalHoursStreamed س",
                    label = "ساعات البث",
                    tintColor = Color(0xFF8B5CF6),
                    testTag = "chip_total_hours_streamed"
                )
            }

            item {
                UserStatDataChip(
                    icon = Icons.Default.Groups,
                    value = "$totalFriendsCount",
                    label = "الأصدقاء والشركاء",
                    tintColor = Color(0xFF06B6D4),
                    testTag = "chip_total_friends"
                )
            }

            item {
                UserStatDataChip(
                    icon = Icons.Default.Public,
                    value = "19 دولة",
                    label = "الدول المتصلة",
                    tintColor = Color(0xFF10B981),
                    testTag = "chip_connected_countries"
                )
            }

            item {
                UserStatDataChip(
                    icon = Icons.Default.EmojiEvents,
                    value = "#42",
                    label = "الترتيب العالمي",
                    tintColor = Color(0xFFF59E0B),
                    testTag = "chip_global_rank"
                )
            }

            item {
                UserStatDataChip(
                    icon = Icons.Default.CardGiftcard,
                    value = "${engagementState.todayGiftsSent + 36}",
                    label = "الهدايا المتبادلة",
                    tintColor = Color(0xFFFF2A85),
                    testTag = "chip_total_gifts"
                )
            }

            item {
                UserStatDataChip(
                    icon = Icons.Default.Star,
                    value = "${engagementState.dailyStreakDays} أيام",
                    label = "التتابع اليومي",
                    tintColor = Color(0xFFFF7A00),
                    testTag = "chip_daily_streak"
                )
            }
        }
    }
}

/**
 * Individual Material 3 styled Data Chip for user metrics
 */
@Composable
fun UserStatDataChip(
    icon: ImageVector,
    value: String,
    label: String,
    tintColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    testTag: String = ""
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tintColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tintColor.copy(alpha = 0.35f)),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .testTag(testTag.ifBlank { "stat_chip_${label.replace(" ", "_")}" })
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(tintColor.copy(alpha = 0.20f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tintColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Column {
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = HuhTheme.colors.textPrimary,
                    maxLines = 1
                )
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = HuhTheme.colors.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Card 2: Points Balance & Economy
 */
@Composable
fun UserPointsBalanceCard(
    userProfile: UserProfile,
    onRechargeClick: () -> Unit,
    onViewTransactionsClick: () -> Unit,
    onClaimDailyFreeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("user_points_balance_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, HuhTheme.colors.gold.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
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
                            .background(HuhTheme.colors.gold.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = null,
                            tint = HuhTheme.colors.gold,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "محفظة واقتصاد النقاط",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = "Ayer Coins Wallet",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textTertiary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = HuhTheme.colors.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onViewTransactionsClick() }
                        .testTag("btn_points_history")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = HuhTheme.colors.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "سجل الحركات",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Balance Highlight Box
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = HuhTheme.colors.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, HuhTheme.colors.gold.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "الرصيد المتاح الحالي",
                            fontSize = 12.sp,
                            color = HuhTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${userProfile.pointsBalance}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = HuhTheme.colors.gold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "نقطة 🪙",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.gold
                            )
                        }
                    }

                    Button(
                        onClick = onRechargeClick,
                        colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.gold),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_recharge_points")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "شحن الرصيد",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Lifetime Economy Breakdown 3-Column Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMetricPill(
                    title = "إجمالي المكتسب",
                    value = "${userProfile.totalAccumulatedPoints.coerceAtLeast(userProfile.pointsBalance)}",
                    icon = Icons.Default.Star,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )

                StatMetricPill(
                    title = "إجمالي المنفق",
                    value = "${userProfile.totalPointsSpent}",
                    icon = Icons.Default.MonetizationOn,
                    tint = Color(0xFFFF4D6D),
                    modifier = Modifier.weight(1f)
                )

                StatMetricPill(
                    title = "المطابقات",
                    value = "${userProfile.totalMatchesCount}",
                    icon = Icons.Default.Videocam,
                    tint = AyerCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Claim Daily Free Reward Button
            Button(
                onClick = onClaimDailyFreeClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_claim_daily_free_coins")
            ) {
                Icon(
                    imageVector = Icons.Default.CardGiftcard,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "🎁 استلام الهدية اليومية المجانية (+10 نقاط)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Card 3: User Activity & Engagement Statistics
 */
@Composable
fun UserActivityStatisticsCard(
    userProfile: UserProfile,
    engagementState: com.example.data.model.UserEngagementState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("user_activity_statistics_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, HuhTheme.colors.secondary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(HuhTheme.colors.secondary.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = HuhTheme.colors.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "إحصائيات النشاط والتفاعل",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                    Text(
                        text = "User Activity & Engagement Metrics",
                        fontSize = 11.sp,
                        color = HuhTheme.colors.textTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4 Key Activity Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Streak Days
                ActivityMetricBox(
                    title = "التتابع اليومي",
                    value = "${engagementState.dailyStreakDays} أيام 🔥",
                    subtitle = "نشاط متواصل",
                    icon = Icons.Default.Star,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )

                // Streaming Minutes
                ActivityMetricBox(
                    title = "مشاهدة البث",
                    value = "${engagementState.todayStreamMinutes} دقيقة ⏱️",
                    subtitle = "الهدف: ${engagementState.targetStreamMinutes} د",
                    icon = Icons.Default.Videocam,
                    color = AyerCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Today Matches
                ActivityMetricBox(
                    title = "مطابقات اليوم",
                    value = "${engagementState.todayMatchesCount} / ${engagementState.targetMatchesCount}",
                    subtitle = "محادثات عشوائية",
                    icon = Icons.Default.Groups,
                    color = HuhTheme.colors.primary,
                    modifier = Modifier.weight(1f)
                )

                // Gifts Sent
                ActivityMetricBox(
                    title = "الهدايا المرسلة",
                    value = "${engagementState.todayGiftsSent} هدايا 🎁",
                    subtitle = "دعم المضيفين",
                    icon = Icons.Default.CardGiftcard,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Daily Target Progress Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HuhTheme.colors.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مستوى التفاعل اليومي (XP)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                    Text(
                        text = "${engagementState.todayEngagementPoints} / 200 XP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.secondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { (engagementState.todayEngagementPoints / 200f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = HuhTheme.colors.secondary,
                    trackColor = HuhTheme.colors.surfaceVariant
                )
            }
        }
    }
}

/**
 * Card 4: Agency Affiliation & Management
 */
@Composable
fun AgencyAffiliationCard(
    userProfile: UserProfile,
    onOpenAgencyDashboard: () -> Unit,
    onSimulatePayout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("agency_affiliation_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header with Certified Agency Badge
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
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = AyerCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "التبعية للوكالة وإدارة المضيفين",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = "Official Agency Affiliation",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textTertiary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AyerCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AyerCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = userProfile.currentAgencyLevel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AyerCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Agency Profile Box
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = HuhTheme.colors.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = userProfile.agencyName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "معرّف الوكالة: ${userProfile.agencyId} • تاريخ الانضمام: ${userProfile.agencyJoinedDate}",
                                fontSize = 11.sp,
                                color = HuhTheme.colors.textSecondary
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "وكالة معتمدة",
                            tint = AyerCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = HuhTheme.colors.surfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AgencyMiniMetric(
                            label = "المدير المسؤول",
                            value = userProfile.agencyManagerName
                        )
                        AgencyMiniMetric(
                            label = "المضيفون النشطون",
                            value = "${userProfile.agencyActiveHostsCount} مضيف 🎙️"
                        )
                        AgencyMiniMetric(
                            label = "عمولة الوكيل",
                            value = "${userProfile.agentCommissionPercent}% 💰"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Monthly Target Progress
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HuhTheme.colors.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "هدف ساعات البث والإيراد الشهري للوكالة",
                        fontSize = 11.sp,
                        color = HuhTheme.colors.textSecondary
                    )
                    Text(
                        text = "${userProfile.agencyMonthlyTargetProgress}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AyerCyan
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { (userProfile.agencyMonthlyTargetProgress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = AyerCyan,
                    trackColor = HuhTheme.colors.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-agents / Host Earnings Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "أرباح الفريق والوكلاء", fontSize = 10.sp, color = HuhTheme.colors.textSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${userProfile.subAgentsEarningsCoins} نقطة",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = HuhTheme.colors.gold.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, HuhTheme.colors.gold.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "أرباح البث الشخصي", fontSize = 10.sp, color = HuhTheme.colors.textSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${userProfile.hostEarningsCoins} نقطة",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.gold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actions: Open Agency Dashboard & Simulate Payout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenAgencyDashboard,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AyerCyan),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("btn_open_agency_dashboard")
                ) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "لوحة تحكم الوكالة",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                OutlinedButton(
                    onClick = onSimulatePayout,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.7f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_agency_payout_action")
                ) {
                    Text(
                        text = "طلب سحب 💵",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}

/**
 * Card: Account Security & Transparency (2FA & Last Login Timestamp)
 */
@Composable
fun AccountSecurityCard(
    isTwoFactorAuthEnabled: Boolean,
    onToggleTwoFactorAuth: (Boolean) -> Unit,
    lastLoginTimestamp: String,
    lastLoginIpAndDevice: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("security_subsection")
            .testTag("account_security_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
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
                            .background(Color(0xFF10B981).copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "الأمان والشفافية (Security)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = "إدارة المصادقة وشفافية تسجيل الدخول",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textTertiary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "حساب محمي",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Two-Factor Authentication (2FA) Toggle Row
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = HuhTheme.colors.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, HuhTheme.colors.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isTwoFactorAuthEnabled) Color(0xFF10B981).copy(alpha = 0.2f)
                                    else HuhTheme.colors.textTertiary.copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isTwoFactorAuthEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isTwoFactorAuthEnabled) Color(0xFF10B981) else HuhTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "المصادقة الثنائية (Two-Factor Auth)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.textPrimary
                            )
                            Text(
                                text = if (isTwoFactorAuthEnabled) "مُفعلة: تأكيد الدخول برمز أمان إضافي" else "معطلة: تفعيل حماية إضافية للحساب",
                                fontSize = 11.sp,
                                color = if (isTwoFactorAuthEnabled) Color(0xFF10B981) else HuhTheme.colors.textTertiary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = isTwoFactorAuthEnabled,
                        onCheckedChange = { onToggleTwoFactorAuth(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF10B981),
                            checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.testTag("two_factor_auth_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Last Login Timestamp for User Transparency
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = HuhTheme.colors.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, HuhTheme.colors.outline.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("last_login_timestamp_view")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = HuhTheme.colors.gold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "آخر تسجيل دخول (Last Login)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.textPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = HuhTheme.colors.surfaceVariant
                        ) {
                            Text(
                                text = "جلسة نشطة",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = lastLoginTimestamp,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HuhTheme.colors.gold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "📍 $lastLoginIpAndDevice",
                        fontSize = 11.sp,
                        color = HuhTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Security Assurance Notice
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = HuhTheme.colors.textTertiary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "جميع الاتصالات والمكالمات مشفرة بالكامل بنظام SSL/TLS و WebRTC",
                    fontSize = 10.sp,
                    color = HuhTheme.colors.textTertiary
                )
            }
        }
    }
}

/**
 * Card 5: Account Preferences & Quick Settings
 */
@Composable
fun AccountPreferencesCard(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    appLanguage: AppLanguage,
    onOpenLanguageSelection: () -> Unit,
    onCreateSecondaryProfile: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("account_preferences_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, HuhTheme.colors.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(HuhTheme.colors.primary.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = HuhTheme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "الإعدادات والتفضيلات",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                    Text(
                        text = "Preferences & App Settings",
                        fontSize = 11.sp,
                        color = HuhTheme.colors.textTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dark Mode Switch Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleTheme() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = HuhTheme.colors.gold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isDarkTheme) "الوضع الليلي (Dark Mode)" else "الوضع الفاتح (Light Mode)",
                        fontSize = 13.sp,
                        color = HuhTheme.colors.textPrimary
                    )
                }

                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { onToggleTheme() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = HuhTheme.colors.primary,
                        checkedTrackColor = HuhTheme.colors.primary.copy(alpha = 0.35f)
                    )
                )
            }

            HorizontalDivider(color = HuhTheme.colors.surfaceVariant)

            // Language Selection Row
            LanguageSelectionSettingRow(
                currentLanguage = appLanguage,
                onOpenLanguageDialog = onOpenLanguageSelection
            )

            HorizontalDivider(color = HuhTheme.colors.surfaceVariant)

            // Secondary Profiles Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCreateSecondaryProfile() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SwitchAccount,
                        contentDescription = null,
                        tint = HuhTheme.colors.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "إدارة البروفايلات الفرعية (Dual Profile)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = "فصل الهوية الشخصية عن البث والعمل",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textSecondary
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = HuhTheme.colors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }

            HorizontalDivider(color = HuhTheme.colors.surfaceVariant)

            Spacer(modifier = Modifier.height(12.dp))

            // Logout Button
            OutlinedButton(
                onClick = onLogout,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFF4D6D).copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4D6D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_logout_action")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = Color(0xFFFF4D6D),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "تسجيل الخروج من الحساب",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF4D6D)
                )
            }
        }
    }
}

// =========================================================================
// HELPER UI ELEMENTS
// =========================================================================

@Composable
fun StatMetricPill(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                color = HuhTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ActivityMetricBox(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = HuhTheme.colors.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = HuhTheme.colors.textSecondary
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HuhTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = HuhTheme.colors.textTertiary
            )
        }
    }
}

@Composable
fun AgencyMiniMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = HuhTheme.colors.textTertiary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = HuhTheme.colors.textPrimary
        )
    }
}

// =========================================================================
// NEW ENHANCEMENTS: COVER HEADER, BADGES, RECENT ACTIVITY & QR SHARING
// =========================================================================

/**
 * Customizable Cover Image Header with Dynamic Scroll-Based Blur
 */
@Composable
fun UserProfileCoverHeader(
    customCoverUri: String?,
    selectedCoverPresetIndex: Int,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    onEditCoverClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = remember {
        listOf(
            CoverPreset(
                id = 0,
                title = "Cyber Sunset",
                gradient = Brush.linearGradient(
                    listOf(Color(0xFFFF2A85), Color(0xFFA855F7), Color(0xFF38BDF8))
                ),
                previewColor = Color(0xFFFF2A85)
            ),
            CoverPreset(
                id = 1,
                title = "Cosmic Amethyst",
                gradient = Brush.linearGradient(
                    listOf(Color(0xFF2E0854), Color(0xFF6B21A8), Color(0xFFC084FC))
                ),
                previewColor = Color(0xFF6B21A8)
            ),
            CoverPreset(
                id = 2,
                title = "Golden Aurora",
                gradient = Brush.linearGradient(
                    listOf(Color(0xFFD97706), Color(0xFFFBBF24), Color(0xFFFDE68A))
                ),
                previewColor = Color(0xFFFBBF24)
            ),
            CoverPreset(
                id = 3,
                title = "Neon Emerald",
                gradient = Brush.linearGradient(
                    listOf(Color(0xFF064E3B), Color(0xFF059669), Color(0xFF34D399))
                ),
                previewColor = Color(0xFF059669)
            )
        )
    }

    val currentPreset = presets.getOrElse(selectedCoverPresetIndex) { presets[0] }

    // Dynamic scroll blur effect calculation
    val scrollOffset by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex == 0) {
                scrollState.firstVisibleItemScrollOffset.toFloat()
            } else {
                350f
            }
        }
    }
    val blurRadius = (scrollOffset / 25f).coerceIn(0f, 16f)
    val scrimAlpha = (scrollOffset / 200f).coerceIn(0f, 0.75f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .testTag("user_profile_cover_header"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, HuhTheme.colors.outline.copy(alpha = 0.35f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Cover: Custom URI or Preset Gradient with blur
            if (customCoverUri != null) {
                AsyncImage(
                    model = customCoverUri,
                    contentDescription = "صورة غلاف الملف الشخصي",
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(blurRadius.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(currentPreset.gradient)
                        .blur(blurRadius.dp)
                )
            }

            // Dynamic scrim gradient overlay that adapts to scroll depth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.25f + scrimAlpha * 0.4f),
                                HuhTheme.colors.background.copy(alpha = 0.35f + scrimAlpha * 0.45f)
                            )
                        )
                    )
            )

            // Dynamic Cover Badge & Edit Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = HuhTheme.colors.gold,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (customCoverUri != null) "غلاف مخصص" else currentPreset.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Edit Cover Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onEditCoverClick() }
                        .testTag("btn_edit_profile_cover")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "تغيير الغلاف",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "تغيير الغلاف",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Bottom subtle creator tag
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                Text(
                    text = "✨ Ayer Official VIP Creator",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Cover Preset Selection Dialog
 */
@Composable
fun CoverPresetSelectionDialog(
    selectedIndex: Int,
    onSelectPreset: (Int) -> Unit,
    onPickCustomImage: () -> Unit,
    onDismiss: () -> Unit
) {
    val presets = remember {
        listOf(
            CoverPreset(0, "Cyber Sunset", Brush.linearGradient(listOf(Color(0xFFFF2A85), Color(0xFFA855F7), Color(0xFF38BDF8))), Color(0xFFFF2A85)),
            CoverPreset(1, "Cosmic Amethyst", Brush.linearGradient(listOf(Color(0xFF2E0854), Color(0xFF6B21A8), Color(0xFFC084FC))), Color(0xFF6B21A8)),
            CoverPreset(2, "Golden Aurora", Brush.linearGradient(listOf(Color(0xFFD97706), Color(0xFFFBBF24), Color(0xFFFDE68A))), Color(0xFFFBBF24)),
            CoverPreset(3, "Neon Emerald", Brush.linearGradient(listOf(Color(0xFF064E3B), Color(0xFF059669), Color(0xFF34D399))), Color(0xFF059669))
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = HuhTheme.colors.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "تخصيص غلاف الملف الشخصي",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = HuhTheme.colors.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "اختر أحد الثيمات الجاهزة أو ارفع صورة خاصة من جهازك:",
                    fontSize = 13.sp,
                    color = HuhTheme.colors.textSecondary
                )

                // Pick Custom Photo from Device
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = HuhTheme.colors.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, HuhTheme.colors.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPickCustomImage() }
                        .testTag("btn_pick_custom_cover_image")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(HuhTheme.colors.primary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = HuhTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "اختيار صورة من المعرض (Photo Picker)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.primary
                            )
                            Text(
                                text = "يدعم صور JPG, PNG عالية الدقة",
                                fontSize = 11.sp,
                                color = HuhTheme.colors.textTertiary
                            )
                        }
                    }
                }

                Text(
                    text = "الثيمات اللونية الذكية (Smart Gradients):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HuhTheme.colors.textSecondary
                )

                presets.forEach { preset ->
                    val isSelected = preset.id == selectedIndex
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = HuhTheme.colors.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) HuhTheme.colors.primary else HuhTheme.colors.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectPreset(preset.id) }
                            .testTag("preset_cover_option_${preset.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(preset.gradient)
                                )
                                Text(
                                    text = preset.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HuhTheme.colors.textPrimary
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "محدد",
                                    tint = HuhTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = HuhTheme.colors.primary)
            }
        },
        containerColor = HuhTheme.colors.surfaceElevated
    )
}

/**
 * Badges & Achievements Section (Horizontal Scrollable Row of Milestone Icons)
 */
@Composable
fun BadgesAndAchievementsCard(
    onMilestoneClick: (UserMilestoneBadge) -> Unit,
    modifier: Modifier = Modifier
) {
    val milestones = remember {
        listOf(
            UserMilestoneBadge(
                id = "star_host",
                title = "نجم الأسبوع",
                subtitle = "أكثر من 50 ساعة بث نشط",
                category = "البث المباشر",
                icon = Icons.Default.Star,
                rarityColor = Color(0xFFFBBF24),
                rarityLabel = "ذهبي",
                isUnlocked = true,
                progress = 50,
                maxProgress = 50,
                unlockedDate = "15 سبتمبر 2026",
                rewardPoints = 1200
            ),
            UserMilestoneBadge(
                id = "gift_hunter",
                title = "صائد الهدايا",
                subtitle = "استلام 10,000+ نقطة هدايا",
                category = "الهدايا",
                icon = Icons.Default.CardGiftcard,
                rarityColor = Color(0xFFFF2A85),
                rarityLabel = "ألماسي",
                isUnlocked = true,
                progress = 10000,
                maxProgress = 10000,
                unlockedDate = "18 سبتمبر 2026",
                rewardPoints = 2500
            ),
            UserMilestoneBadge(
                id = "first_stream",
                title = "أول بث مباشر",
                subtitle = "بدء أول بث حي على التطبيق",
                category = "البث",
                icon = Icons.Default.Videocam,
                rarityColor = Color(0xFFCD7F32),
                rarityLabel = "برونزي",
                isUnlocked = true,
                progress = 1,
                maxProgress = 1,
                unlockedDate = "01 سبتمبر 2026",
                rewardPoints = 300
            ),
            UserMilestoneBadge(
                id = "global_connector",
                title = "تواصل عالمي",
                subtitle = "مطابقة مع 20 دولة مختلفة",
                category = "المطابقة العشوائية",
                icon = Icons.Default.Public,
                rarityColor = Color(0xFF38BDF8),
                rarityLabel = "فضي",
                isUnlocked = true,
                progress = 20,
                maxProgress = 20,
                unlockedDate = "10 سبتمبر 2026",
                rewardPoints = 800
            ),
            UserMilestoneBadge(
                id = "ayer_ambassador",
                title = "سفير Ayer",
                subtitle = "دعوة 50 صديق نشط للمنصة",
                category = "الدعوات",
                icon = Icons.Default.Groups,
                rarityColor = Color(0xFFA855F7),
                rarityLabel = "ملكي",
                isUnlocked = false,
                progress = 32,
                maxProgress = 50,
                rewardPoints = 3000
            ),
            UserMilestoneBadge(
                id = "challenge_king",
                title = "ملك التحديات",
                subtitle = "الفوز بـ 15 جولة PK حية",
                category = "تحديات PK",
                icon = Icons.Default.MilitaryTech,
                rarityColor = Color(0xFFEF4444),
                rarityLabel = "أسطوري",
                isUnlocked = false,
                progress = 9,
                maxProgress = 15,
                rewardPoints = 2000
            ),
            UserMilestoneBadge(
                id = "vip_elite",
                title = "نخبة VIP",
                subtitle = "بلوغ الفئة الماسية في الرتب",
                category = "الرتبة",
                icon = Icons.Default.WorkspacePremium,
                rarityColor = Color(0xFF10B981),
                rarityLabel = "ماستر",
                isUnlocked = true,
                progress = 1,
                maxProgress = 1,
                unlockedDate = "12 سبتمبر 2026",
                rewardPoints = 5000
            )
        )
    }

    val unlockedCount = milestones.count { it.isUnlocked }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("badges_and_achievements_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, HuhTheme.colors.gold.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
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
                            .background(HuhTheme.colors.gold.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = HuhTheme.colors.gold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "الأوسمة والإنجازات (Badges)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = "تم فتح $unlockedCount من أصل ${milestones.size} وسام",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textTertiary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = HuhTheme.colors.gold.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, HuhTheme.colors.gold.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "🏆 $unlockedCount/${milestones.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.gold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Horizontal Scrollable Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("badges_horizontal_row"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(milestones) { badge ->
                    MilestoneBadgeItem(
                        badge = badge,
                        onClick = { onMilestoneClick(badge) }
                    )
                }
            }
        }
    }
}

@Composable
fun MilestoneBadgeItem(
    badge: UserMilestoneBadge,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(115.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("badge_item_${badge.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (badge.isUnlocked) HuhTheme.colors.surfaceVariant.copy(alpha = 0.7f) else HuhTheme.colors.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            if (badge.isUnlocked) 1.5.dp else 1.dp,
            if (badge.isUnlocked) badge.rarityColor.copy(alpha = 0.6f) else HuhTheme.colors.outline.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Badge with Glowing Border
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        if (badge.isUnlocked) badge.rarityColor.copy(alpha = 0.18f)
                        else HuhTheme.colors.textTertiary.copy(alpha = 0.1f),
                        CircleShape
                    )
                    .border(
                        1.5.dp,
                        if (badge.isUnlocked) badge.rarityColor else HuhTheme.colors.textTertiary.copy(alpha = 0.3f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badge.icon,
                    contentDescription = badge.title,
                    tint = if (badge.isUnlocked) badge.rarityColor else HuhTheme.colors.textTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = badge.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (badge.isUnlocked) HuhTheme.colors.textPrimary else HuhTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Rarity / Progress status pill
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (badge.isUnlocked) badge.rarityColor.copy(alpha = 0.15f) else HuhTheme.colors.surfaceVariant
            ) {
                Text(
                    text = if (badge.isUnlocked) badge.rarityLabel else "${badge.progress}/${badge.maxProgress}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (badge.isUnlocked) badge.rarityColor else HuhTheme.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            if (!badge.isUnlocked) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (badge.progress.toFloat() / badge.maxProgress.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = badge.rarityColor,
                    trackColor = HuhTheme.colors.surfaceVariant
                )
            }
        }
    }
}

/**
 * Milestone Detail Dialog
 */
@Composable
fun MilestoneDetailDialog(
    badge: UserMilestoneBadge,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(badge.rarityColor.copy(alpha = 0.2f), CircleShape)
                        .border(1.5.dp, badge.rarityColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badge.icon,
                        contentDescription = null,
                        tint = badge.rarityColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = badge.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = HuhTheme.colors.textPrimary
                    )
                    Text(
                        text = "الفئة: ${badge.category} • ندرة: ${badge.rarityLabel}",
                        fontSize = 11.sp,
                        color = badge.rarityColor
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = badge.subtitle,
                    fontSize = 13.sp,
                    color = HuhTheme.colors.textSecondary
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = HuhTheme.colors.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "حالة الإنجاز:",
                                fontSize = 12.sp,
                                color = HuhTheme.colors.textTertiary
                            )
                            Text(
                                text = if (badge.isUnlocked) "مكتمل ومفتوح ✅" else "قيد التقدم ⏳",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (badge.isUnlocked) HuhTheme.colors.success else HuhTheme.colors.gold
                            )
                        }

                        if (badge.isUnlocked && badge.unlockedDate != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "تاريخ الحصول عليه:",
                                    fontSize = 12.sp,
                                    color = HuhTheme.colors.textTertiary
                                )
                                Text(
                                    text = badge.unlockedDate,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HuhTheme.colors.textPrimary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "مكافأة النقاط:",
                                fontSize = 12.sp,
                                color = HuhTheme.colors.textTertiary
                            )
                            Text(
                                text = "+${badge.rewardPoints} نقطة",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.gold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.primary)
            ) {
                Text("حسناً", color = Color.White)
            }
        },
        containerColor = HuhTheme.colors.surfaceElevated
    )
}

/**
 * Mini 'Recent Activity' Feed within UserProfileScreen
 */
@Composable
fun RecentActivityFeedCard(
    onAddHighlightClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activities = remember {
        listOf(
            RecentActivityItem(
                id = "act_1",
                title = "🔴 بث مباشر استثنائي: سهرة الأصدقاء والمطابقة",
                subtitle = "استمر البث ساعتين و14 دقيقة بمشاركة 1,420 مشاهد من 8 دول",
                timestamp = "منذ ساعتين",
                viewsCount = 1420,
                likesCount = 248,
                pointsEarned = 350,
                tag = "#بث_مباشر",
                isHighlight = true,
                icon = Icons.Default.LiveTv,
                accentColor = Color(0xFFFF2A85)
            ),
            RecentActivityItem(
                id = "act_2",
                title = "🏆 ترقية رسمية: الانضمام لنخبة مضيفي الوكالة",
                subtitle = "ترقية إلى رتبة Gold Host مع زيادة نسبة العمولة إلى 60%",
                timestamp = "منذ يومين",
                viewsCount = 850,
                likesCount = 192,
                pointsEarned = null,
                tag = "#إنجاز_الوكالة",
                isHighlight = false,
                icon = Icons.Default.WorkspacePremium,
                accentColor = Color(0xFFFBBF24)
            ),
            RecentActivityItem(
                id = "act_3",
                title = "🌐 مطابقة عشوائية دولية مميزة مع روما 🇮🇹",
                subtitle = "محادثة فيديو ممتعة استمرت 24 دقيقة وتبادل هدايا الصداقة",
                timestamp = "منذ 3 أيام",
                viewsCount = 520,
                likesCount = 112,
                pointsEarned = 100,
                tag = "#تواصل_عالمي",
                isHighlight = false,
                icon = Icons.Default.Public,
                accentColor = Color(0xFF38BDF8)
            ),
            RecentActivityItem(
                id = "act_4",
                title = "💎 استلام درع الألماس الملكي VIP",
                subtitle = "هدية حصرية كبرى من أحد كبار الداعمين في غرفة البث الحية",
                timestamp = "منذ 5 أيام",
                viewsCount = 1890,
                likesCount = 310,
                pointsEarned = 5000,
                tag = "#هدايا_VIP",
                isHighlight = true,
                icon = Icons.Default.CardGiftcard,
                accentColor = Color(0xFFA855F7)
            )
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recent_activity_feed_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, HuhTheme.colors.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
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
                            .background(HuhTheme.colors.primary.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = HuhTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "النشاط الأخير والهايلايتس",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = "أحدث المنشورات والبثوث المباشرة وإنجازاتك",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textTertiary
                        )
                    }
                }

                // Add Highlight Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = HuhTheme.colors.primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, HuhTheme.colors.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onAddHighlightClick() }
                        .testTag("btn_add_highlight")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = HuhTheme.colors.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "إضافة",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Vertical list of activity items
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recent_activity_vertical_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                activities.forEach { activity ->
                    RecentActivityRowItem(
                        activity = activity,
                        onLikeClick = {
                            Toast.makeText(context, "تم تسجيل إعجابك بالمنشور ❤️", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Single Row Item in Recent Activity Feed
 */
@Composable
fun RecentActivityRowItem(
    activity: RecentActivityItem,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var liked by remember { mutableStateOf(false) }
    var likesCount by remember { mutableIntStateOf(activity.likesCount) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = HuhTheme.colors.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, HuhTheme.colors.outline.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("activity_item_${activity.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(activity.accentColor.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = activity.icon,
                            contentDescription = null,
                            tint = activity.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = activity.tag,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = activity.accentColor
                    )
                }

                Text(
                    text = activity.timestamp,
                    fontSize = 11.sp,
                    color = HuhTheme.colors.textTertiary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = activity.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = HuhTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = activity.subtitle,
                fontSize = 11.sp,
                color = HuhTheme.colors.textSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer statistics & like button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = HuhTheme.colors.textTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${activity.viewsCount}",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textTertiary
                        )
                    }

                    if (activity.pointsEarned != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = HuhTheme.colors.gold,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "+${activity.pointsEarned}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = HuhTheme.colors.gold
                            )
                        }
                    }
                }

                // Like interactive button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (liked) Color(0xFFFF2A85).copy(alpha = 0.15f) else Color.Transparent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            liked = !liked
                            likesCount += if (liked) 1 else -1
                            if (liked) onLikeClick()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "إعجاب",
                            tint = if (liked) Color(0xFFFF2A85) else HuhTheme.colors.textTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "$likesCount",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (liked) Color(0xFFFF2A85) else HuhTheme.colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Create New Highlight / Post Dialog
 */
@Composable
fun CreateNewHighlightDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, tag: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("#بث_مباشر") }

    val tags = listOf("#بث_مباشر", "#تواصل_عالمي", "#هايلايت", "#إنجاز_الوكالة", "#هدايا_VIP")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = HuhTheme.colors.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "نشر هايلايت أو نشاط جديد",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = HuhTheme.colors.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان النشاط أو الهايلايت") },
                    placeholder = { Text("مثال: سهرة ممتعة مع الأصدقاء") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("التفاصيل والوصف") },
                    placeholder = { Text("اكتب نبذة عن هذا البث أو الإنجاز...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = "الوسم (Tag):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HuhTheme.colors.textSecondary
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(tags) { tag ->
                        val isSelected = tag == selectedTag
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) HuhTheme.colors.primary.copy(alpha = 0.18f) else HuhTheme.colors.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) HuhTheme.colors.primary else HuhTheme.colors.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedTag = tag }
                        ) {
                            Text(
                                text = tag,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) HuhTheme.colors.primary else HuhTheme.colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, description, selectedTag)
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.primary)
            ) {
                Text("نشر", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = HuhTheme.colors.textSecondary)
            }
        },
        containerColor = HuhTheme.colors.surfaceElevated
    )
}

/**
 * QR Code Profile Sharing Dialog
 */
@Composable
fun UserProfileQrCodeDialog(
    userProfile: UserProfile,
    onDismiss: () -> Unit,
    onShareSheet: () -> Unit
) {
    val profileUrl = "https://ayer.app/u/${userProfile.id}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = HuhTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "رمز QR للملف التعريفي",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = HuhTheme.colors.textPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = HuhTheme.colors.textSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "امسح الرمز ضوئياً للوصول المباشر إلى ملف ${userProfile.name} على تطبيق Ayer:",
                    fontSize = 12.sp,
                    color = HuhTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                // White Card for crisp QR contrast in both light and dark modes
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 6.dp,
                    border = BorderStroke(2.dp, HuhTheme.colors.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(220.dp)
                        .testTag("user_qr_code_card")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        QrCodeCanvas(
                            content = profileUrl,
                            modifier = Modifier.fillMaxSize(),
                            darkColor = Color(0xFF1E1E2E),
                            lightColor = Color.White
                        )

                        // Central Logo Badge
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFF2A85),
                            border = BorderStroke(2.dp, Color.White),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "AY",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Profile URL pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = HuhTheme.colors.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = profileUrl,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HuhTheme.colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = HuhTheme.colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onShareSheet()
                },
                colors = ButtonDefaults.buttonColors(containerColor = HuhTheme.colors.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_share_sheet_from_qr")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("مشاركة الرابط", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = HuhTheme.colors.textSecondary)
            }
        },
        containerColor = HuhTheme.colors.surfaceElevated
    )
}

/**
 * QR Code Canvas Drawing Composable
 */
@Composable
fun QrCodeCanvas(
    content: String,
    modifier: Modifier = Modifier,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val sizePx = size.minDimension
        val moduleCount = 25
        val moduleSize = sizePx / moduleCount

        // Draw light background
        drawRect(color = lightColor, size = Size(sizePx, sizePx))

        // Deterministic hash to populate simulated QR grid
        val hash = content.hashCode()

        fun isFinderPattern(r: Int, c: Int): Boolean {
            // Top-left finder (7x7)
            if (r in 0..6 && c in 0..6) return true
            // Top-right finder (7x7)
            if (r in 0..6 && c in (moduleCount - 7) until moduleCount) return true
            // Bottom-left finder (7x7)
            if (r in (moduleCount - 7) until moduleCount && c in 0..6) return true
            // Center logo space (5x5)
            if (r in 10..14 && c in 10..14) return true
            return false
        }

        // Draw standard QR Finder Pattern
        fun drawFinder(topRow: Int, leftCol: Int) {
            val startX = leftCol * moduleSize
            val startY = topRow * moduleSize
            val finderSize = 7 * moduleSize

            // Outer dark square
            drawRect(
                color = darkColor,
                topLeft = Offset(startX, startY),
                size = Size(finderSize, finderSize)
            )
            // Inner light square
            drawRect(
                color = lightColor,
                topLeft = Offset(startX + moduleSize, startY + moduleSize),
                size = Size(finderSize - 2 * moduleSize, finderSize - 2 * moduleSize)
            )
            // Center dark square
            drawRect(
                color = darkColor,
                topLeft = Offset(startX + 2 * moduleSize, startY + 2 * moduleSize),
                size = Size(finderSize - 4 * moduleSize, finderSize - 4 * moduleSize)
            )
        }

        drawFinder(0, 0)
        drawFinder(0, moduleCount - 7)
        drawFinder(moduleCount - 7, 0)

        // Draw data modules
        for (r in 0 until moduleCount) {
            for (c in 0 until moduleCount) {
                if (!isFinderPattern(r, c)) {
                    val bit = ((hash xor (r * 31 + c * 17)) and 1) == 0
                    if (bit) {
                        drawRect(
                            color = darkColor,
                            topLeft = Offset(c * moduleSize, r * moduleSize),
                            size = Size(moduleSize * 0.92f, moduleSize * 0.92f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card 0.5: Profile Completion Progress Indicator & Suggestions
 */
@Composable
fun ProfileCompletionCard(
    userProfile: UserProfile,
    onCompleteFieldClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var score = 0
    if (userProfile.avatarCustomUri != null || userProfile.avatarResId != 0) score += 15
    if (userProfile.name.isNotBlank() && userProfile.name != "مستخدم جديد") score += 15
    if (userProfile.bio.isNotBlank()) score += 20
    if (userProfile.country.isNotBlank()) score += 15
    if (userProfile.phoneNumber.isNotBlank()) score += 15
    if (userProfile.email.isNotBlank()) score += 10
    if (userProfile.ageVerified) score += 10

    val progressFraction = (score.toFloat() / 100f).coerceIn(0f, 1f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_completion_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, HuhTheme.colors.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = HuhTheme.colors.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = HuhTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "نسبة اكتمال الملف الشخصي",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = HuhTheme.colors.textPrimary
                        )
                        Text(
                            text = if (score >= 80) "ملفك مميز وجاهز للتوثيق! 🌟" else "أكمل بياناتك لتحسين فرصة المطابقة 🚀",
                            fontSize = 11.sp,
                            color = HuhTheme.colors.textSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (score >= 80) Color(0xFF10B981).copy(alpha = 0.15f) else HuhTheme.colors.gold.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (score >= 80) Color(0xFF10B981) else HuhTheme.colors.gold)
                ) {
                    Text(
                        text = "$score% مكتمل",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (score >= 80) Color(0xFF10B981) else HuhTheme.colors.gold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("profile_completion_progress"),
                color = if (score >= 80) Color(0xFF10B981) else HuhTheme.colors.primary,
                trackColor = HuhTheme.colors.surfaceVariant
            )

            if (score < 100) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "اقتراحات لتحسين بروفايلك:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HuhTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (userProfile.bio.isBlank()) {
                        item {
                            SuggestionChipPill(
                                label = "+20% إضافة نبذة (Bio)",
                                icon = Icons.Default.Edit,
                                onClick = onCompleteFieldClick
                            )
                        }
                    }
                    if (userProfile.country.isBlank()) {
                        item {
                            SuggestionChipPill(
                                label = "+15% تحديد الدولة والمدينة",
                                icon = Icons.Default.Public,
                                onClick = onCompleteFieldClick
                            )
                        }
                    }
                    if (userProfile.phoneNumber.isBlank()) {
                        item {
                            SuggestionChipPill(
                                label = "+15% إضافة رقم الجوال",
                                icon = Icons.Default.Phone,
                                onClick = onCompleteFieldClick
                            )
                        }
                    }
                    if (userProfile.email.isBlank()) {
                        item {
                            SuggestionChipPill(
                                label = "+10% ربط البريد الإلكتروني",
                                icon = Icons.Default.Badge,
                                onClick = onCompleteFieldClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionChipPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = HuhTheme.colors.primary.copy(alpha = 0.1f),
        border = BorderStroke(0.8.dp, HuhTheme.colors.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HuhTheme.colors.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HuhTheme.colors.primary
            )
        }
    }
}

/**
 * Card 10.5: Privacy Settings & Visibility Control
 */
@Composable
fun PrivacySettingsCard(
    profileVisibility: com.example.data.model.ProfileVisibilityOption,
    onSelectVisibility: (com.example.data.model.ProfileVisibilityOption) -> Unit,
    directMessagePermission: com.example.data.model.DirectMessagePermission,
    onSelectMessagePermission: (com.example.data.model.DirectMessagePermission) -> Unit,
    showOnlineStatus: Boolean,
    onToggleOnlineStatus: (Boolean) -> Unit,
    hideLocationInMatch: Boolean,
    onToggleHideLocation: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("privacy_settings_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HuhTheme.colors.surfaceElevated),
        border = BorderStroke(1.dp, HuhTheme.colors.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = HuhTheme.colors.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = HuhTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "إعدادات الخصوصية والأمان",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                    Text(
                        text = "التحكم الكامل في ظهورك ومن يمكنه التواصل معك",
                        fontSize = 11.sp,
                        color = HuhTheme.colors.textSecondary
                    )
                }
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = HuhTheme.colors.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "رؤية الملف الشخصي (Profile Visibility):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("privacy_visibility_selector"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    com.example.data.model.ProfileVisibilityOption.entries.forEach { option ->
                        val isSelected = (profileVisibility == option)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) HuhTheme.colors.primary else HuhTheme.colors.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) HuhTheme.colors.primary else Color.Gray.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectVisibility(option) }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option.title,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else HuhTheme.colors.textPrimary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = HuhTheme.colors.gold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "من يمكنه إرسال رسائل خاصة لك:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("privacy_messages_selector"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    com.example.data.model.DirectMessagePermission.entries.forEach { perm ->
                        val isSelected = (directMessagePermission == perm)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) HuhTheme.colors.gold.copy(alpha = 0.15f) else HuhTheme.colors.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) HuhTheme.colors.gold else Color.Gray.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectMessagePermission(perm) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = perm.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) HuhTheme.colors.gold else HuhTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = perm.desc,
                                        fontSize = 10.sp,
                                        color = HuhTheme.colors.textSecondary
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = HuhTheme.colors.gold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "إظهار حالة الاتصال بالإنترنت",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                    Text(
                        text = "السماح للآخرين برؤية نقطة النشاط الخضراء متصل/غير متصل",
                        fontSize = 10.sp,
                        color = HuhTheme.colors.textSecondary
                    )
                }
                Switch(
                    checked = showOnlineStatus,
                    onCheckedChange = onToggleOnlineStatus,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = HuhTheme.colors.primary
                    ),
                    modifier = Modifier.testTag("privacy_online_status_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "إخفاء الدولة والمدينة في المطابقة العشوائية",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                    Text(
                        text = "حماية موقعك الحقيقي وعدم ظهوره للطرف الآخر أثناء الدردشة",
                        fontSize = 10.sp,
                        color = HuhTheme.colors.textSecondary
                    )
                }
                Switch(
                    checked = hideLocationInMatch,
                    onCheckedChange = onToggleHideLocation,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = HuhTheme.colors.primary
                    ),
                    modifier = Modifier.testTag("privacy_hide_location_switch")
                )
            }
        }
    }
}

/**
 * Bottom Sheet / Dialog for Reporting a User with Predefined Reasons & Firestore Persistence
 */
@Composable
fun ReportUserBottomSheet(
    targetUserId: String,
    targetUserName: String,
    onDismiss: () -> Unit,
    onSubmitReport: (reason: String, details: String) -> Unit
) {
    var selectedReasonIndex by remember { mutableIntStateOf(0) }
    var detailsText by remember { mutableStateOf("") }

    val predefinedReasons = listOf(
        "سلوك غير لائق أو تحرش",
        "محتوى مخل بالآداب أو إباحي",
        "انتحال شخصية أو حساب مزيف",
        "احتيال أو رسائل مزعجة (Spam)",
        "مستخدم دون السن القانوني (+18)",
        "سبب آخر"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFF2A85).copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color(0xFFFF2A85),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "تقديم بلاغ عن مستخدم",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = HuhTheme.colors.textPrimary
                    )
                    Text(
                        text = "الحساب: $targetUserName ($targetUserId)",
                        fontSize = 11.sp,
                        color = HuhTheme.colors.textSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "يرجى تحديد سبب الإبلاغ الرئيسي:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HuhTheme.colors.textPrimary
                )

                predefinedReasons.forEachIndexed { idx, reason ->
                    val isSelected = (selectedReasonIndex == idx)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFFFF2A85).copy(alpha = 0.15f) else HuhTheme.colors.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFFFF2A85) else Color.Gray.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedReasonIndex = idx }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = reason,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFFF2A85) else HuhTheme.colors.textPrimary
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFFFF2A85),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = detailsText,
                    onValueChange = { detailsText = it },
                    label = { Text("تفاصيل إضافية (اختياري)") },
                    placeholder = { Text("اكتب تفاصيل الإساءة لمساعدة فريق الإشراف...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosenReason = predefinedReasons[selectedReasonIndex]
                    onSubmitReport(chosenReason, detailsText)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A85)),
                modifier = Modifier
                    .testTag("submit_user_report_btn")
            ) {
                Text("إرسال البلاغ للإدارة 🛡️", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = HuhTheme.colors.textSecondary)
            }
        }
    )
}

