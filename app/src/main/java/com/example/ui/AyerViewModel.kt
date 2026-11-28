package com.example.ui

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.example.R
import com.example.billing.PlayBillingManager
import com.example.data.local.AyerDatabase
import com.example.data.model.ActiveCallSession
import com.example.data.model.AgeVerificationSubmissionDoc
import com.example.data.model.AppNotification
import com.example.data.model.CallQualityMode
import com.example.data.model.CoinPackage
import com.example.data.model.CreatorLeaderboardItem
import com.example.data.model.DailyMissionItem
import com.example.data.model.FirestoreRandomChatMessage
import com.example.data.model.FriendItem
import com.example.data.model.FriendshipStatus
import com.example.data.model.GiftItem
import com.example.data.model.LeaderboardScope
import com.example.data.model.LeaderboardTimeframe
import com.example.data.model.LiveStreamReport
import com.example.data.model.LiveStreamRoom
import com.example.data.model.MatchCandidate
import com.example.data.model.MatchChatType
import com.example.data.model.NotificationType
import com.example.data.model.PointTransaction
import com.example.data.model.PrivateChatMessage
import com.example.data.model.StreamMessage
import com.example.data.model.UserEngagementState
import com.example.data.model.UserProfile
import com.example.calls.CallAudioRouter
import com.example.calls.CallAdminAlertEntity
import com.example.calls.CallForegroundService
import com.example.calls.CallGovernanceConfigEntity
import com.example.calls.CallGovernanceManager
import com.example.calls.CallInitiationOutcome
import com.example.calls.CallLifecycleState
import com.example.calls.CallLiveKitEngine
import com.example.calls.CallMonitoringSnapshot
import com.example.calls.CallType
import com.example.calls.FirestoreCallSignalingService
import com.example.calls.IncomingCallBus
import com.example.calls.IncomingCallInvite
import com.example.data.repository.AyerRepository
import com.example.ui.locale.AppLanguage
import com.example.util.AppPreferencesStore
import com.example.util.AyerNotificationManager
import com.example.util.authStateFlow
import com.example.webrtc.WebRTCManager
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Typealias providing direct compatibility for VideoCallViewModel
 */
typealias VideoCallViewModel = AyerViewModel

sealed class MatchingStatus {
    object Idle : MatchingStatus()
    object Searching : MatchingStatus()
    data class Found(val candidate: MatchCandidate) : MatchingStatus()
    data class Connected(
        val candidate: MatchCandidate,
        val chatType: MatchChatType = MatchChatType.VIDEO
    ) : MatchingStatus()
}

class AyerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AyerRepository
    private val preferencesStore = AppPreferencesStore(application)

    val userProfile: StateFlow<UserProfile?>
    val transactions: StateFlow<List<PointTransaction>>
    val notifications: StateFlow<List<AppNotification>>
    val unreadNotificationCount: StateFlow<Int>
    val featureFlags: StateFlow<com.example.data.remote.FeatureFlagsDto>
    val engagementState: StateFlow<UserEngagementState>
    val dailyMissions: StateFlow<List<DailyMissionItem>>
    val flaggedStreamIds: StateFlow<Set<String>>
    private val _firestoreBlockedUsers = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds: StateFlow<Set<String>>
    private val _rawFriendsList = MutableStateFlow<List<FriendItem>>(emptyList())
    val friendsList: StateFlow<List<FriendItem>>
    private val _privateChatMessages = MutableStateFlow<Map<String, List<PrivateChatMessage>>>(emptyMap())
    val privateChatMessages = _privateChatMessages.asStateFlow()
    val subAgentsList: StateFlow<List<com.example.data.model.SubAgentItem>>
    val myPayouts: StateFlow<List<com.example.data.remote.PayoutRequestDto>>

    private val aiTranslationService by lazy { com.example.ai.AiTranslationService() }

    private val _adminAgeVerifications = MutableStateFlow<List<com.example.data.remote.AdminAgeVerificationItemDto>>(emptyList())
    val adminAgeVerifications: StateFlow<List<com.example.data.remote.AdminAgeVerificationItemDto>> = _adminAgeVerifications.asStateFlow()

    private val _adminPayouts = MutableStateFlow<List<com.example.data.remote.PayoutRequestDto>>(emptyList())
    val adminPayouts: StateFlow<List<com.example.data.remote.PayoutRequestDto>> = _adminPayouts.asStateFlow()

    private val _adminReports = MutableStateFlow<List<com.example.data.remote.ModerationReportDto>>(emptyList())
    val adminReports: StateFlow<List<com.example.data.remote.ModerationReportDto>> = _adminReports.asStateFlow()

    private val _adminEconomyConfig = MutableStateFlow(com.example.data.remote.EconomyConfigDto())
    val adminEconomyConfig: StateFlow<com.example.data.remote.EconomyConfigDto> = _adminEconomyConfig.asStateFlow()

    val playBillingManager: PlayBillingManager
    val playProductPrices: StateFlow<Map<String, String>>
    val isPlayBillingReady: StateFlow<Boolean>

    private val _currentMatchId = MutableStateFlow<String?>(null)
    val currentMatchId: StateFlow<String?> = _currentMatchId.asStateFlow()

    private val _firestoreMatchMessages = MutableStateFlow<List<FirestoreRandomChatMessage>>(emptyList())
    val firestoreMatchMessages: StateFlow<List<FirestoreRandomChatMessage>> = _firestoreMatchMessages.asStateFlow()
    private var firestoreChatJob: Job? = null
    private var privateChatJob: Job? = null
    private var activePrivateChatFriendId: String? = null
    private var authSessionSyncJob: Job? = null

    private val _ageVerificationSubmission = MutableStateFlow<AgeVerificationSubmissionDoc?>(null)
    val ageVerificationSubmission: StateFlow<AgeVerificationSubmissionDoc?> = _ageVerificationSubmission.asStateFlow()

    private val _isSubmittingAgeVerification = MutableStateFlow(false)
    val isSubmittingAgeVerification: StateFlow<Boolean> = _isSubmittingAgeVerification.asStateFlow()

    private val _ageVerificationError = MutableStateFlow<String?>(null)
    val ageVerificationError: StateFlow<String?> = _ageVerificationError.asStateFlow()

    // Account Security & Transparency State
    private val _isTwoFactorAuthEnabled = MutableStateFlow(true)
    val isTwoFactorAuthEnabled: StateFlow<Boolean> = _isTwoFactorAuthEnabled.asStateFlow()

    private val _lastLoginTimestamp = MutableStateFlow("غير مسجل")
    val lastLoginTimestamp: StateFlow<String> = _lastLoginTimestamp.asStateFlow()

    private val _lastLoginIpAndDevice = MutableStateFlow(
        "${Build.MANUFACTURER.orEmpty()} ${Build.MODEL.orEmpty()} • Android ${Build.VERSION.RELEASE.orEmpty()}".trim()
    )
    val lastLoginIpAndDevice: StateFlow<String> = _lastLoginIpAndDevice.asStateFlow()

    fun setTwoFactorAuthEnabled(enabled: Boolean) {
        _isTwoFactorAuthEnabled.value = enabled
        _uiNotice.value = if (enabled) {
            "حماية تسجيل الدخول عبر Google مفعلة 🔒"
        } else {
            "تم تحديث تفضيل تنبيهات الأمان"
        }
    }

    // App Theme State (null = Follow System, true = Dark Mode, false = Light Mode)
    private val _isDarkTheme = MutableStateFlow<Boolean?>(null)
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    // App Interface Language State
    private val _appLanguage = MutableStateFlow(AppLanguage.ARABIC)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    fun setAppLanguage(language: AppLanguage) {
        _appLanguage.value = language
        viewModelScope.launch { preferencesStore.setAppLanguage(language) }
    }

    fun setDarkMode(enabled: Boolean?) {
        _isDarkTheme.value = enabled
        viewModelScope.launch { preferencesStore.setDarkTheme(enabled) }
    }

    fun toggleTheme(systemIsDark: Boolean = false) {
        val currentEffective = _isDarkTheme.value ?: systemIsDark
        setDarkMode(!currentEffective)
    }

    // 1-on-1 Call Session State, Governance, LiveKit SFU & Audio Routing
    private val _activeCallSession = MutableStateFlow<ActiveCallSession?>(null)
    val activeCallSession = _activeCallSession.asStateFlow()
    private var callTimerJob: Job? = null
    private var callSignalingObserverJob: Job? = null
    private var outgoingRingJob: Job? = null
    private var activeFriendCallId: String? = null
    private var activeCallMaxDurationSeconds: Int = 30 * 60

    val callGovernanceManager: CallGovernanceManager
    val callMonitoringSnapshot: StateFlow<CallMonitoringSnapshot>
    val callGovernanceConfig: StateFlow<CallGovernanceConfigEntity>
    val incomingCallInvite: StateFlow<IncomingCallInvite?>

    val callLiveKitEngine: CallLiveKitEngine by lazy { CallLiveKitEngine(getApplication()) }
    private val callAudioRouter: CallAudioRouter by lazy { CallAudioRouter(getApplication()) }
    private val callSignalingService: FirestoreCallSignalingService by lazy { FirestoreCallSignalingService(getApplication()) }

    val livekitLocalVideoTrack: StateFlow<io.livekit.android.room.track.VideoTrack?>
        get() = callLiveKitEngine.localVideoTrack

    val livekitRemoteVideoTrack: StateFlow<io.livekit.android.room.track.VideoTrack?>
        get() = callLiveKitEngine.remoteVideoTrack

    // Layer 1 Concurrency Guard (ViewModel level): Prevents duplicate/concurrent UI button clicks
    private val isSwitchingCamera = AtomicBoolean(false)

    // UI State for observing camera switch in progress across Compose UI
    private val _isCameraSwitchInProgress = MutableStateFlow(false)
    val isCameraSwitchInProgress: StateFlow<Boolean> = _isCameraSwitchInProgress.asStateFlow()

    fun isCameraSwitching(): Boolean = isSwitchingCamera.get()

    // WebRTC Streaming Engine State
    private var _webRTCManager: WebRTCManager? = null
    val webRTCManager: WebRTCManager?
        get() {
            if (_webRTCManager == null) {
                try {
                    _webRTCManager = WebRTCManager(getApplication())
                } catch (e: Throwable) {
                    android.util.Log.e("AyerViewModel", "WebRTCManager init skipped in mock/test environment", e)
                }
            }
            return _webRTCManager
        }

    val eglBaseContext: EglBase.Context?
        get() = try { webRTCManager?.getEglBaseContext() } catch (e: Throwable) { null }

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    // Active in-app heads-up notification alert for influencers
    private val _activeInAppNotification = MutableStateFlow<AppNotification?>(null)
    val activeInAppNotification = _activeInAppNotification.asStateFlow()

    // Matching Engine State
    private val _selectedChatType = MutableStateFlow(MatchChatType.VIDEO)
    val selectedChatType = _selectedChatType.asStateFlow()

    private val _selectedGenderFilter = MutableStateFlow("ANY") // ANY, FEMALE, MALE
    val selectedGenderFilter = _selectedGenderFilter.asStateFlow()

    private val _selectedCountryFilter = MutableStateFlow("ANY") // ANY, SA, EG, AE, GLOBAL
    val selectedCountryFilter = _selectedCountryFilter.asStateFlow()

    private val _matchingStatus = MutableStateFlow<MatchingStatus>(MatchingStatus.Idle)
    val matchingStatus = _matchingStatus.asStateFlow()

    private val _matchChatMessages = MutableStateFlow<List<StreamMessage>>(emptyList())
    val matchChatMessages = _matchChatMessages.asStateFlow()

    private val _isTranslationEnabled = MutableStateFlow(true)
    val isTranslationEnabled = _isTranslationEnabled.asStateFlow()

    private val _isVideoCallActive = MutableStateFlow(false)
    val isVideoCallActive = _isVideoCallActive.asStateFlow()

    private val _matchQueueSize = MutableStateFlow(0)
    val matchQueueSize = _matchQueueSize.asStateFlow()

    private val _matchWaitSeconds = MutableStateFlow(0)
    val matchWaitSeconds = _matchWaitSeconds.asStateFlow()

    private var matchPollingJob: Job? = null

    // Live Stream State
    private val _rawLiveStreams = MutableStateFlow<List<LiveStreamRoom>>(emptyList())
    val liveStreams: StateFlow<List<LiveStreamRoom>>

    private val _activeBroadcastIsHost = MutableStateFlow(false)
    val activeBroadcastIsHost = _activeBroadcastIsHost.asStateFlow()

    private val _isBroadcastLoading = MutableStateFlow(false)
    val isBroadcastLoading = _isBroadcastLoading.asStateFlow()

    private val _selectedLiveStream = MutableStateFlow<LiveStreamRoom?>(null)
    val selectedLiveStream = _selectedLiveStream.asStateFlow()

    private val _streamMessages = MutableStateFlow<List<StreamMessage>>(emptyList())
    val streamMessages = _streamMessages.asStateFlow()

    // Live Stream Creator Moderation Dashboard State
    private val _streamBlockedUsers = MutableStateFlow<Set<String>>(emptySet())
    val streamBlockedUsers = _streamBlockedUsers.asStateFlow()

    private val _streamRestrictedKeywords = MutableStateFlow<Set<String>>(emptySet())
    val streamRestrictedKeywords = _streamRestrictedKeywords.asStateFlow()

    private val _activeStreamViewers = MutableStateFlow<List<String>>(emptyList())
    val activeStreamViewers = _activeStreamViewers.asStateFlow()

    private val _activeGiftEffect = MutableStateFlow<GiftItem?>(null)
    val activeGiftEffect = _activeGiftEffect.asStateFlow()

    // Alert & Snackbar state
    private val _uiNotice = MutableStateFlow<String?>(null)
    val uiNotice = _uiNotice.asStateFlow()

    // Google Play In-App Review / Rate Us State
    private val _showRateUsDialog = MutableStateFlow(false)
    val showRateUsDialog = _showRateUsDialog.asStateFlow()

    private val _completedMissionsClaimCount = MutableStateFlow(0)
    val completedMissionsClaimCount = _completedMissionsClaimCount.asStateFlow()

    // Firebase Auth State
    private val auth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        null
    }
    private val _isUserLoggedIn = MutableStateFlow(auth?.currentUser != null)
    val isUserLoggedIn = _isUserLoggedIn.asStateFlow()

    private var candidateIndex = 0

    init {
        val db = AyerDatabase.getDatabase(application)
        repository = AyerRepository(
            application,
            db.userDao(),
            db.transactionDao(),
            db.notificationDao()
        )
        featureFlags = repository.featureFlags

        lateinit var billingRef: PlayBillingManager
        billingRef = PlayBillingManager(
            context = application,
            onVerifiedPurchaseReady = { productId, purchaseToken, orderId ->
                viewModelScope.launch {
                    val res = repository.verifyAndCreditPlayPurchase(productId, purchaseToken, orderId)
                    if (res.isSuccess) {
                        billingRef.consumeVerifiedPurchase(purchaseToken)
                        _uiNotice.value = "تم التحقق من Google Play وإضافة +${res.getOrNull() ?: 0} نقطة إلى محفظتك 🎉"
                    } else {
                        _uiNotice.value = res.exceptionOrNull()?.message ?: "تعذر تأكيد عملية الشراء من الخادم."
                    }
                }
            },
            onBillingError = { message ->
                _uiNotice.value = message
            }
        )
        playBillingManager = billingRef
        playProductPrices = playBillingManager.formattedPrices
        isPlayBillingReady = playBillingManager.isBillingReady
        blockedUserIds = combine(repository.blockedUserIds, _firestoreBlockedUsers) { a, b -> a + b }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
        subAgentsList = repository.subAgents
        myPayouts = repository.myPayouts

        callGovernanceManager = CallGovernanceManager(
            callDao = db.callDao(),
            notificationDao = db.notificationDao(),
            authTokenProvider = {
                try {
                    auth?.currentUser?.getIdToken(false)?.await()?.token
                } catch (_: Throwable) {
                    null
                }
            },
            onCriticalAdminAlert = { alert ->
                AyerNotificationManager.sendCallAdminAlertNotification(
                    context = application,
                    title = alert.title,
                    message = alert.message
                )
            }
        )

        callMonitoringSnapshot = callGovernanceManager.observeMonitoringSnapshot().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            CallMonitoringSnapshot()
        )

        callGovernanceConfig = callGovernanceManager.observeGovernanceConfig().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            CallGovernanceConfigEntity()
        )

        val firestoreIncomingFlow = callSignalingService.observeIncomingCallsForUser(
            auth?.currentUser?.uid ?: ""
        )
        incomingCallInvite = combine(
            IncomingCallBus.pendingInvite,
            firestoreIncomingFlow
        ) { pushInvite, fsInvite ->
            pushInvite ?: fsInvite
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

        // Initialize OS notification channel
        AyerNotificationManager.initNotificationChannels(application)

        userProfile = repository.userProfile.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UserProfile()
        )

        transactions = repository.transactions.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        notifications = repository.notifications.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        unreadNotificationCount = repository.unreadNotificationCount.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0
        )

        engagementState = repository.engagementState.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UserEngagementState()
        )

        dailyMissions = repository.dailyMissions.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        flaggedStreamIds = repository.flaggedStreamIds.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptySet()
        )

        liveStreams = combine(_rawLiveStreams, blockedUserIds, flaggedStreamIds) { list, blocked, flagged ->
            list.filter { it.hostUid !in blocked && it.id !in flagged }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        friendsList = combine(_rawFriendsList, blockedUserIds) { list, blocked ->
            list.filter { it.id !in blocked && it.status != FriendshipStatus.BLOCKED }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        viewModelScope.launch {
            preferencesStore.darkThemeFlow.collect { stored ->
                _isDarkTheme.value = stored
            }
        }
        viewModelScope.launch {
            preferencesStore.appLanguageFlow.collect { lang ->
                _appLanguage.value = lang
            }
        }

        viewModelScope.launch {
            callGovernanceManager.ensureDefaultConfig()
            repository.refreshFeatureFlags()
        }

        val firebaseAuth = auth
        if (firebaseAuth != null) {
            viewModelScope.launch {
                firebaseAuth.authStateFlow().collect { firebaseUser ->
                    _isUserLoggedIn.value = firebaseUser != null
                    authSessionSyncJob?.cancel()
                    if (firebaseUser != null) {
                        val lastSignInMs = firebaseUser.metadata?.lastSignInTimestamp
                        _lastLoginTimestamp.value = if (lastSignInMs != null && lastSignInMs > 0L) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSignInMs))
                        } else {
                            "جلسة مصادقة نشطة"
                        }
                        _isTwoFactorAuthEnabled.value = true
                        authSessionSyncJob = launch {
                            repository.syncAuthenticatedUser(firebaseUser)
                            launch {
                                repository.observeRemoteUserDoc(firebaseUser.uid).collect { remoteDoc ->
                                    if (remoteDoc != null) {
                                        repository.applyRemoteUserSnapshot(remoteDoc)
                                    }
                                }
                            }
                            launch {
                                repository.observeAgeVerification(firebaseUser.uid).collect { sub ->
                                    _ageVerificationSubmission.value = sub
                                }
                            }
                            launch {
                                repository.observeFirestoreBlockedUsers(firebaseUser.uid).collect { blocked ->
                                    _firestoreBlockedUsers.value = blocked
                                }
                            }
                            launch {
                                repository.observeFriends(firebaseUser.uid).collect { friends ->
                                    _rawFriendsList.value = friends
                                }
                            }
                            launch {
                                repository.observePrivacySettings(firebaseUser.uid).collect { snap ->
                                    if (snap != null) {
                                        runCatching {
                                            _profileVisibility.value = com.example.data.model.ProfileVisibilityOption.valueOf(snap.profileVisibility)
                                        }
                                        runCatching {
                                            _directMessagePermission.value = com.example.data.model.DirectMessagePermission.valueOf(snap.directMessagePermission)
                                        }
                                        _showOnlineStatus.value = snap.showOnlineStatus
                                        _hideLocationInMatch.value = snap.hideLocationInMatch
                                    }
                                }
                            }
                        }
                    } else {
                        _ageVerificationSubmission.value = null
                        _rawFriendsList.value = emptyList()
                        _firestoreBlockedUsers.value = emptySet()
                        _lastLoginTimestamp.value = "غير مسجل"
                    }
                }
            }
        }
    }

    fun startObservingFirestoreMatchMessages(matchId: String) {
        firestoreChatJob?.cancel()
        _currentMatchId.value = matchId
        firestoreChatJob = viewModelScope.launch {
            val myUid = currentCallActorId()
            repository.observeFirestoreMatchMessages(matchId).collect { messages ->
                val enriched = if (_isTranslationEnabled.value) {
                    val targetLang = if (_appLanguage.value == AppLanguage.ARABIC) "Arabic" else "English"
                    messages.map { msg ->
                        if (msg.senderId != myUid && msg.translatedText.isNullOrBlank()) {
                            val translated = aiTranslationService.translateText(msg.text, targetLang).getOrNull()
                            if (!translated.isNullOrBlank()) msg.copy(translatedText = translated) else msg
                        } else {
                            msg
                        }
                    }
                } else {
                    messages
                }
                _firestoreMatchMessages.value = enriched
            }
        }
    }

    fun claimEngagementReward() {
        if (!featureFlags.value.missionRewardsEnabled) {
            _uiNotice.value = "مكافآت المهام متوقفة مؤقتاً أثناء المراجعة."
            return
        }
        viewModelScope.launch {
            val success = repository.claimDailyMilestoneReward()
            if (success) {
                _uiNotice.value = "تهانينا! استلمت +50 نقطة مكافأة إكمال مهام التفاعل اليومي 🏆"
            } else {
                _uiNotice.value = "يجب جمع 100 نقطة تفاعل على الأقل لاستلام المكافأة اليومية 🎯"
            }
        }
    }

    fun recordStreamWatchEngagement(minutes: Int = 5) {
        viewModelScope.launch {
            repository.recordStreamWatchEngagement(minutes)
        }
    }

    fun updateAccountSettings(
        name: String,
        bio: String,
        country: String,
        phoneNumber: String,
        avatarResId: Int = com.example.R.drawable.img_user_avatar,
        avatarCustomUri: String? = null
    ) {
        viewModelScope.launch {
            repository.updateAccountSettings(name, bio, country, phoneNumber, avatarResId, avatarCustomUri)
            _uiNotice.value = "تم تحديث الملف الشخصي والصورة بنجاح ✅"
        }
    }

    fun calculateMatchCost(): Int {
        var cost = _selectedChatType.value.cost
        if (_selectedGenderFilter.value != "ANY") cost += 2
        if (_selectedCountryFilter.value != "ANY") cost += 2
        return cost
    }

    fun setChatTypeFilter(chatType: MatchChatType) {
        _selectedChatType.value = chatType
    }

    fun setGenderFilter(gender: String) {
        _selectedGenderFilter.value = gender
    }

    fun setCountryFilter(country: String) {
        _selectedCountryFilter.value = country
    }

    fun toggleTranslation() {
        _isTranslationEnabled.value = !_isTranslationEnabled.value
    }

    fun startRandomMatch() {
        val cost = calculateMatchCost()
        val currentBalance = userProfile.value?.pointsBalance ?: 0
        if (currentBalance < cost) {
            _uiNotice.value = "رصيدك غير كافٍ! تحتاج $cost نقطة للمطابقة بهذا الفلتر."
            return
        }
        matchPollingJob?.cancel()
        _matchingStatus.value = MatchingStatus.Searching
        _isVideoCallActive.value = false
        _matchChatMessages.value = emptyList()
        _matchWaitSeconds.value = 0

        val profile = userProfile.value
        val displayName = profile?.name?.ifBlank { "مستخدم HUH" } ?: "مستخدم HUH"
        val country = if (_hideLocationInMatch.value) "" else profile?.country.orEmpty()
        val countryCode = if (_hideLocationInMatch.value) "SA" else profile?.countryCode?.ifBlank { "SA" } ?: "SA"
        val userGender = profile?.gender?.ifBlank { "ANY" } ?: "ANY"
        val chatType = _selectedChatType.value
        val genderFilter = _selectedGenderFilter.value
        val countryFilter = _selectedCountryFilter.value

        matchPollingJob = viewModelScope.launch {
            val joinRes = repository.joinMatchQueue(
                displayName = displayName,
                country = country,
                countryCode = countryCode,
                userGender = userGender,
                bio = profile?.bio.orEmpty(),
                chatType = chatType.code,
                genderFilter = genderFilter,
                countryFilter = countryFilter
            )
            joinRes.fold(
                onSuccess = { initial ->
                    if (initial.status == "MATCHED") {
                        onMatchFoundFromServer(initial)
                    } else {
                        _matchQueueSize.value = initial.queueSize
                        _matchWaitSeconds.value = initial.waitSeconds
                        while (_matchingStatus.value is MatchingStatus.Searching) {
                            delay(2000)
                            val poll = repository.pollMatchQueue().getOrNull() ?: continue
                            _matchQueueSize.value = poll.queueSize
                            _matchWaitSeconds.value = poll.waitSeconds
                            if (poll.status == "MATCHED") {
                                onMatchFoundFromServer(poll)
                                break
                            }
                        }
                    }
                },
                onFailure = { err ->
                    _matchingStatus.value = MatchingStatus.Idle
                    _uiNotice.value = err.message ?: "تعذر الاتصال بخادم المطابقة المباشر."
                }
            )
        }
    }

    private suspend fun onMatchFoundFromServer(res: com.example.data.remote.MatchJoinPollResponseDto) {
        val partnerDto = res.partner ?: return
        val matchId = res.matchId ?: return
        val resolvedType = MatchChatType.fromCode(res.chatType ?: _selectedChatType.value.code)
        val candidate = MatchCandidate(
            id = partnerDto.uid,
            name = partnerDto.name.ifBlank { "مستخدم HUH" },
            age = partnerDto.age.coerceAtLeast(18),
            country = partnerDto.country.ifBlank { "العالم 🌐" },
            countryCode = partnerDto.countryCode.ifBlank { "SA" },
            gender = partnerDto.gender,
            language = "العربية",
            bio = partnerDto.bio,
            interests = listOf("تعارف", "دردشة")
        )
        _matchingStatus.value = MatchingStatus.Found(candidate)
        if (resolvedType != MatchChatType.TEXT && !res.livekitUrl.isNullOrBlank() && !res.token.isNullOrBlank() && !res.token.startsWith("pending_lk_")) {
            callLiveKitEngine.connect(
                livekitUrl = res.livekitUrl,
                token = res.token,
                enableVideo = (resolvedType == MatchChatType.VIDEO),
                publishMedia = true,
                endOnRemoteLeave = true,
                onReconnecting = { _uiNotice.value = "جارٍ إعادة الاتصال..." },
                onReconnected = {},
                onRemoteParticipantLeft = { endCurrentMatch() },
                onConnectionQualityWeak = {},
                onDisconnected = {}
            )
            _isVideoCallActive.value = (resolvedType == MatchChatType.VIDEO)
        }
        delay(900)
        _matchingStatus.value = MatchingStatus.Connected(candidate, resolvedType)
        startObservingFirestoreMatchMessages(matchId)
    }

    fun skipCurrentMatch() {
        val prevMatchId = _currentMatchId.value
        matchPollingJob?.cancel()
        matchPollingJob = null
        _matchingStatus.value = MatchingStatus.Idle
        _isVideoCallActive.value = false
        repository.stopFirestoreChat()
        runCatching { callLiveKitEngine.disconnect() }
        viewModelScope.launch {
            repository.leaveMatchQueue(prevMatchId)
            startRandomMatch()
        }
    }

    fun startVoiceMatch() {
        _selectedChatType.value = MatchChatType.VOICE
        startRandomMatch()
    }

    fun startVipMatch() {
        startRandomMatch()
    }

    fun endCurrentMatch() {
        val prevMatchId = _currentMatchId.value
        matchPollingJob?.cancel()
        matchPollingJob = null
        _matchingStatus.value = MatchingStatus.Idle
        _isVideoCallActive.value = false
        _currentMatchId.value = null
        repository.stopFirestoreChat()
        runCatching { callLiveKitEngine.disconnect() }
        viewModelScope.launch {
            repository.leaveMatchQueue(prevMatchId)
        }
    }

    fun sendMatchChatMessage(text: String) {
        if (text.isBlank()) return
        val sender = userProfile.value?.name?.ifBlank { "أنا" } ?: "أنا"
        val matchId = _currentMatchId.value ?: return
        val senderUid = currentCallActorId()
        val msgId = System.currentTimeMillis().toString()

        val current = _matchChatMessages.value.toMutableList()
        current.add(
            StreamMessage(
                id = msgId,
                senderName = sender,
                originalText = text,
                translatedText = null
            )
        )
        _matchChatMessages.value = current

        viewModelScope.launch {
            val translated = if (_isTranslationEnabled.value) {
                val targetLang = if (_appLanguage.value == AppLanguage.ARABIC) "English" else "Arabic"
                aiTranslationService.translateText(text, targetLang).getOrNull()
            } else {
                null
            }
            if (!translated.isNullOrBlank()) {
                _matchChatMessages.value = _matchChatMessages.value.map {
                    if (it.id == msgId) it.copy(translatedText = translated) else it
                }
            }
            repository.sendFirestoreMatchMessage(
                matchId = matchId,
                senderId = senderUid,
                senderName = sender,
                text = text,
                translatedText = translated
            )
            repository.recordMatchEngagement()
        }
    }

    // Live Stream Actions
    fun refreshLiveStreams() {
        viewModelScope.launch {
            _isBroadcastLoading.value = true
            repository.fetchLiveStreams().onSuccess { list ->
                _rawLiveStreams.value = list
            }
            _isBroadcastLoading.value = false
        }
    }

    fun startUserLiveStream(title: String = "بثي المباشر", category: String = "تعارف وسوالف") {
        val profile = userProfile.value
        val myName = profile?.name?.ifBlank { "مضيف HUH" } ?: "مضيف HUH"
        val myCountry = profile?.country?.ifBlank { "المملكة العربية السعودية" } ?: "المملكة العربية السعودية"
        val myCountryCode = profile?.countryCode?.ifBlank { "SA" } ?: "SA"
        viewModelScope.launch {
            _isBroadcastLoading.value = true
            val res = repository.startLiveBroadcast(
                title = title,
                category = category,
                hostName = myName,
                hostCountry = myCountry,
                hostCountryCode = myCountryCode
            )
            _isBroadcastLoading.value = false
            res.fold(
                onSuccess = { session ->
                    val room = with(repository) { session.stream.toRoomModel() }
                    _activeBroadcastIsHost.value = true
                    _selectedLiveStream.value = room
                    _streamMessages.value = listOf(
                        StreamMessage("sys1", "نظام الأمان", "مرحباً بك في بثك المباشر! استخدم أدوات التحكم لكتم الصوت، تبديل الكاميرا، أو إيقاف البث مؤقتاً.", isSystem = true),
                        StreamMessage("sys2", "HUH Live", "أنت الآن على الهواء مباشرة عبر LiveKit SFU 🔴", isSystem = true)
                    )
                    if (session.livekitUrl.isNotBlank() && session.token.isNotBlank() && !session.token.startsWith("pending_lk_")) {
                        callLiveKitEngine.connect(
                            livekitUrl = session.livekitUrl,
                            token = session.token,
                            enableVideo = true,
                            publishMedia = true,
                            endOnRemoteLeave = false,
                            onReconnecting = { _uiNotice.value = "جارٍ استعادة اتصال البث..." },
                            onReconnected = { _uiNotice.value = "تمت استعادة اتصال البث" },
                            onRemoteParticipantLeft = {},
                            onConnectionQualityWeak = {},
                            onDisconnected = {}
                        )
                    }
                    refreshLiveStreams()
                    _uiNotice.value = "تم بدء البث المباشر بنجاح! 🔴"
                },
                onFailure = { err ->
                    _uiNotice.value = err.message ?: "تعذر بدء البث المباشر."
                }
            )
        }
    }

    fun openLiveStream(stream: LiveStreamRoom) {
        _activeBroadcastIsHost.value = (stream.hostUid.isNotBlank() && stream.hostUid == userProfile.value?.uid)
        _selectedLiveStream.value = stream
        _streamMessages.value = listOf(
            StreamMessage("m1", "نظام الأمان", "مرحباً بك في بث ${stream.hostName}! يُرجى الالتزام بسياسات المجتمع والاحترام المتبادل.", isSystem = true)
        )
        viewModelScope.launch {
            val res = repository.joinLiveBroadcast(stream.id)
            res.onSuccess { session ->
                val updatedRoom = with(repository) { session.stream.toRoomModel() }
                val isPub = session.role == "PUBLISHER"
                _activeBroadcastIsHost.value = isPub
                _selectedLiveStream.value = updatedRoom
                if (session.livekitUrl.isNotBlank() && session.token.isNotBlank() && !session.token.startsWith("pending_lk_")) {
                    callLiveKitEngine.connect(
                        livekitUrl = session.livekitUrl,
                        token = session.token,
                        enableVideo = isPub,
                        publishMedia = isPub,
                        endOnRemoteLeave = false,
                        onReconnecting = {},
                        onReconnected = {},
                        onRemoteParticipantLeft = {},
                        onConnectionQualityWeak = {},
                        onDisconnected = {}
                    )
                }
            }.onFailure { err ->
                _uiNotice.value = err.message ?: "تعذر الاتصال بغرفة البث."
            }
        }
    }

    fun closeLiveStream() {
        val stream = _selectedLiveStream.value
        val wasHost = _activeBroadcastIsHost.value
        _selectedLiveStream.value = null
        _activeGiftEffect.value = null
        _activeBroadcastIsHost.value = false
        runCatching { callLiveKitEngine.disconnect() }
        if (stream != null) {
            viewModelScope.launch {
                if (wasHost) repository.endLiveBroadcast(stream.id)
                else repository.leaveLiveBroadcast(stream.id)
                refreshLiveStreams()
            }
        }
    }

    fun setBroadcastMuted(muted: Boolean) {
        viewModelScope.launch { callLiveKitEngine.setMicrophoneMuted(muted) }
    }

    fun setBroadcastVideoEnabled(enabled: Boolean) {
        viewModelScope.launch { callLiveKitEngine.setCameraEnabled(enabled) }
    }

    fun switchBroadcastCamera(toFront: Boolean) {
        callLiveKitEngine.switchCamera(toFront)
    }

    fun sendStreamMessage(text: String) {
        if (text.isBlank()) return
        val sender = userProfile.value?.name ?: "أنا"

        // Check if sender is blocked by creator
        if (_streamBlockedUsers.value.contains(sender)) {
            _uiNotice.value = "تم حظرك من إرسال التعليقات في هذا البث من قِبل المضيف 🚫"
            return
        }

        // Check if message contains restricted keywords
        val lowerText = text.lowercase()
        val containsRestricted = _streamRestrictedKeywords.value.any { kw ->
            lowerText.contains(kw.lowercase().trim())
        }
        if (containsRestricted) {
            _uiNotice.value = "تم حجب التعليق لاحتوائه على كلمات محظورة في هذا البث ⚠️"
            return
        }

        val msg = StreamMessage(
            id = System.currentTimeMillis().toString(),
            senderName = sender,
            originalText = text
        )
        _streamMessages.value = _streamMessages.value + msg
        recordChatMessageSent()
    }

    // ==========================================
    // Live Stream Creator Moderation Operations
    // ==========================================

    fun blockStreamUser(username: String) {
        if (username.isBlank()) return
        val trimmed = username.trim()
        _streamBlockedUsers.value = _streamBlockedUsers.value + trimmed
        // Automatically hide past messages from this user in current session
        _streamMessages.value = _streamMessages.value.filter { it.senderName != trimmed }
        _uiNotice.value = "تم حظر المستخدم '$trimmed' وحذف رسائله من البث 🛡️"
    }

    fun unblockStreamUser(username: String) {
        val trimmed = username.trim()
        _streamBlockedUsers.value = _streamBlockedUsers.value - trimmed
        _uiNotice.value = "تم إلغاء حظر المستخدم '$trimmed' والسماح له بالتعليق مجدداً ✅"
    }

    fun addRestrictedKeyword(keyword: String) {
        if (keyword.isBlank()) return
        val trimmed = keyword.trim()
        _streamRestrictedKeywords.value = _streamRestrictedKeywords.value + trimmed
        // Automatically filter any existing message containing the newly restricted keyword
        _streamMessages.value = _streamMessages.value.filter { msg ->
            !msg.originalText.contains(trimmed, ignoreCase = true)
        }
        _uiNotice.value = "تمت إضافة الكلمة المقيدة '$trimmed' وتصفية المحادثة 🚫"
    }

    fun removeRestrictedKeyword(keyword: String) {
        val trimmed = keyword.trim()
        _streamRestrictedKeywords.value = _streamRestrictedKeywords.value - trimmed
        _uiNotice.value = "تمت إزالة الكلمة '$trimmed' من قائمة القيود ✅"
    }

    fun clearLiveStreamChat() {
        val hostName = _selectedLiveStream.value?.hostName ?: "المضيف"
        _streamMessages.value = listOf(
            StreamMessage(
                id = "clear_${System.currentTimeMillis()}",
                senderName = "نظام الأمان",
                originalText = "قام $hostName بمسح سجل المحادثة بالكامل في الوقت الفعلي 🧹",
                isSystem = true
            )
        )
        _uiNotice.value = "تم مسح سجل محادثة البث بالكامل فورياً بنجاح 🧹"
    }

    fun sendGiftToStream(gift: GiftItem) {
        val stream = _selectedLiveStream.value ?: return
        val balance = userProfile.value?.pointsBalance ?: 0
        if (balance < gift.pointsCost) {
            _uiNotice.value = "رصيدك (${balance} نقطة) لا يكفي لإرسال ${gift.name} (${gift.pointsCost} نقطة)"
            return
        }

        viewModelScope.launch {
            val success = repository.sendGift(
                gift = gift,
                streamHost = stream.hostName,
                recipientUid = stream.hostUid,
                streamId = stream.id
            )
            if (success) {
                _selectedLiveStream.value = stream.copy(diamondsReceived = stream.diamondsReceived + gift.pointsCost)
                _activeGiftEffect.value = gift
                val giftMsg = StreamMessage(
                    id = System.currentTimeMillis().toString(),
                    senderName = userProfile.value?.name ?: "أنا",
                    originalText = "أرسل ${gift.name} ${gift.emoji} إلى المضيف!",
                    isGift = true,
                    giftEmoji = gift.emoji
                )
                _streamMessages.value = _streamMessages.value + giftMsg
                delay(3000)
                _activeGiftEffect.value = null
            } else {
                _uiNotice.value = "تعذر إرسال الهدية. يرجى المحاولة مجدداً."
            }
        }
    }

    // Wallet & Google Play Billing Purchases
    fun initPlayBilling() {
        playBillingManager.startConnection()
    }

    fun claimDailyBonus() {
        viewModelScope.launch {
            val res = repository.claimDailyRewardResult()
            _uiNotice.value = if (res.isSuccess) {
                "تم استلام +${res.getOrNull() ?: 10} نقاط مجانية يومية بنجاح! 🎁"
            } else {
                res.exceptionOrNull()?.message ?: "لقد استلمت نقاط اليوم بالفعل! عُد غداً."
            }
        }
    }

    fun purchasePackage(coinPackage: CoinPackage, activity: Activity? = null) {
        if (activity == null) {
            _uiNotice.value = "تعذر فتح Google Play Billing: الشاشة غير نشطة."
            return
        }
        val currentUid: String? = auth?.currentUser?.uid ?: userProfile.value?.uid?.takeIf { it.isNotBlank() }
        playBillingManager.startConnection()
        playBillingManager.launchPurchaseFlow(activity, coinPackage.productId, currentUid)
    }

    fun refreshAgencyDashboard() {
        if (!featureFlags.value.agenciesEnabled) return
        viewModelScope.launch {
            repository.refreshAgencyDashboard()
        }
    }

    fun requestBankPayout(
        amountCoins: Int,
        payoutMethod: String,
        bankAccountHolder: String,
        bankName: String,
        ibanOrAccount: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!featureFlags.value.payoutsEnabled) {
            _uiNotice.value = "طلبات السحب المالي متوقفة مؤقتاً أثناء المراجعة."
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val res = repository.requestBankPayout(
                amountCoins = amountCoins,
                payoutMethod = payoutMethod,
                bankAccountHolder = bankAccountHolder,
                bankName = bankName,
                ibanOrAccount = ibanOrAccount
            )
            if (res.isSuccess) {
                _uiNotice.value = "تم تقديم طلب سحب $amountCoins نقطة (≈ $${String.format(Locale.US, "%.2f", amountCoins / 250.0)}) للمراجعة الإدارية 🏦"
                onComplete(true)
            } else {
                _uiNotice.value = res.exceptionOrNull()?.message ?: "تعذر تقديم طلب السحب."
                onComplete(false)
            }
        }
    }

    fun withdrawHostEarnings(coins: Int) {
        if (!featureFlags.value.payoutsEnabled) {
            _uiNotice.value = "طلبات السحب المالي متوقفة مؤقتاً أثناء المراجعة."
            return
        }
        _uiNotice.value = "يرجى تعبئة بيانات الحساب البنكي في نموذج سحب الأرباح لتقديم الطلب للمراجعة."
    }

    fun applyAsHost() {
        if (!featureFlags.value.agenciesEnabled) {
            _uiNotice.value = "طلبات المضيفين والوكالات متوقفة مؤقتاً أثناء المراجعة."
            return
        }
        viewModelScope.launch {
            val res = repository.applyForHost("الوكالة الماسية")
            _uiNotice.value = if (res.isSuccess) {
                "تم تقديم طلب انضمامك كمضيف معتمد. سيتم مراجعة الطلب من الإدارة."
            } else {
                res.exceptionOrNull()?.message ?: "تعذر تقديم طلب المضيف."
            }
        }
    }

    fun updateAgentCommissionFromAdmin(percent: Int) {
        if (userProfile.value?.isAdmin != true) {
            _uiNotice.value = "صلاحية تعديل العمولات محصورة بحساب الإدارة."
            return
        }
        viewModelScope.launch {
            val res = repository.updateEconomyConfigAdmin(agentCommissionPercent = percent)
            _uiNotice.value = if (res.isSuccess) {
                res.getOrNull()?.let { _adminEconomyConfig.value = it }
                "تم تحديث نسبة عمولة الوكلاء العامة إلى $percent% (حصة المنصة ${res.getOrNull()?.platformCommissionPercent ?: 30}%)"
            } else {
                res.exceptionOrNull()?.message ?: "تعذر تحديث العمولة"
            }
        }
    }

    fun recruitNewSubAgent(name: String, country: String) {
        if (!featureFlags.value.agenciesEnabled) {
            _uiNotice.value = "ميزة تسجيل الوكلاء الفرعيين متوقفة مؤقتاً أثناء المراجعة."
            return
        }
        viewModelScope.launch {
            val res = repository.recruitSubAgentResult(name, country)
            if (res.isSuccess) {
                val notif = res.getOrNull()!!
                _activeInAppNotification.value = notif
                _uiNotice.value = "تم تسجيل الوكيل '$name' في شبكتك الرسمية"
            } else {
                _uiNotice.value = res.exceptionOrNull()?.message ?: "تعذر إضافة الوكيل الفرعي"
            }
        }
    }

    fun loadAdminQueues() {
        if (userProfile.value?.isAdmin != true) return
        viewModelScope.launch {
            repository.fetchAdminAgeVerifications("PENDING_REVIEW").onSuccess {
                _adminAgeVerifications.value = it
            }
            repository.fetchAdminPayouts("ALL").onSuccess {
                _adminPayouts.value = it
            }
            repository.fetchAdminReports("PENDING_REVIEW").onSuccess {
                _adminReports.value = it
            }
            repository.fetchEconomyConfigAdmin().onSuccess {
                _adminEconomyConfig.value = it
            }
        }
    }

    fun reviewAgeVerificationAdmin(targetUid: String, decision: String, reason: String? = null) {
        if (userProfile.value?.isAdmin != true) return
        viewModelScope.launch {
            val res = repository.reviewAdminAgeVerification(targetUid, decision, reason)
            _uiNotice.value = if (res.isSuccess) {
                loadAdminQueues()
                "تم تحديث حالة التحقق (+18) إلى $decision"
            } else {
                res.exceptionOrNull()?.message ?: "تعذر تحديث حالة التحقق"
            }
        }
    }

    fun reviewPayoutAdmin(requestId: String, decision: String, note: String? = null, paymentReference: String? = null) {
        if (userProfile.value?.isAdmin != true) return
        viewModelScope.launch {
            val res = repository.reviewAdminPayout(requestId, decision, note, paymentReference)
            _uiNotice.value = if (res.isSuccess) {
                loadAdminQueues()
                "تم تحديث حالة طلب السحب إلى $decision"
            } else {
                res.exceptionOrNull()?.message ?: "تعذر تحديث طلب السحب"
            }
        }
    }

    fun updateUserRolesAdmin(
        targetUid: String,
        isAgent: Boolean?,
        isHost: Boolean?,
        agentCommissionPercent: Int?,
        agencyName: String?
    ) {
        if (userProfile.value?.isAdmin != true) return
        viewModelScope.launch {
            val res = repository.updateUserRolesAdmin(
                targetUid = targetUid.trim(),
                isAgent = isAgent,
                isHost = isHost,
                agentCommissionPercent = agentCommissionPercent,
                agencyName = agencyName
            )
            _uiNotice.value = if (res.isSuccess) {
                "تم تحديث صلاحيات الحساب $targetUid بنجاح"
            } else {
                res.exceptionOrNull()?.message ?: "تعذر تحديث الصلاحيات"
            }
        }
    }

    fun resolveReportAdmin(reportId: String, status: String, actionTaken: String) {
        if (userProfile.value?.isAdmin != true) return
        viewModelScope.launch {
            val res = repository.resolveAdminReport(reportId, status, actionTaken)
            _uiNotice.value = if (res.isSuccess) {
                loadAdminQueues()
                "تم حسم البلاغ ($actionTaken)"
            } else {
                res.exceptionOrNull()?.message ?: "تعذر البت في البلاغ"
            }
        }
    }

    fun blockUser(targetUid: String) {
        viewModelScope.launch {
            repository.blockUser(targetUid)
            _uiNotice.value = "تم حظر المستخدم وإخفاء محتواه عنك 🛡️"
        }
    }

    fun unblockUser(targetUid: String) {
        viewModelScope.launch {
            repository.unblockUser(targetUid)
            _uiNotice.value = "تم إلغاء الحظر"
        }
    }

    fun deleteAccountPermanently(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.deleteAccountPermanently()
            if (res.isSuccess) {
                logoutUser()
                _uiNotice.value = "تم حذف حسابك وجميع بياناتك نهائياً ✅"
                onResult(true)
            } else {
                _uiNotice.value = res.exceptionOrNull()?.message ?: "تعذر حذف الحساب"
                onResult(false)
            }
        }
    }

    fun dismissInAppNotification() {
        _activeInAppNotification.value = null
    }

    fun markNotificationAsRead(id: Long) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
            _uiNotice.value = "تم تعيين جميع الإشعارات كمقروءة."
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
            _uiNotice.value = "تم مسح سجل الإشعارات."
        }
    }

    // ==========================================
    // 1-on-1 Call Session, Governance & LiveKit SFU Engine
    // ==========================================
    private fun currentCallActorId(): String =
        auth?.currentUser?.uid ?: "user_${userProfile.value?.id ?: 1L}"

    private fun currentCallActorName(): String =
        userProfile.value?.name ?: "سامر"

    fun startCallSession(
        candidate: MatchCandidate,
        isVideo: Boolean = true,
        friendIdForChatLog: String? = null
    ) {
        if (blockedUserIds.value.contains(candidate.id)) {
            _uiNotice.value = "هذا المستخدم محظور — لا يمكن بدء المكالمة 🚫"
            return
        }
        if (_activeCallSession.value != null) {
            _uiNotice.value = "يوجد اتصال نشط بالفعل"
            return
        }

        callTimerJob?.cancel()
        callSignalingObserverJob?.cancel()
        outgoingRingJob?.cancel()
        activeFriendCallId = friendIdForChatLog

        val provisionalCallId = "pending_${System.currentTimeMillis()}"
        _activeCallSession.value = ActiveCallSession(
            callId = provisionalCallId,
            roomName = "",
            participantId = candidate.id,
            participantName = candidate.name,
            participantAge = candidate.age,
            participantCountry = candidate.country,
            participantCountryCode = candidate.countryCode,
            participantAvatarRes = candidate.imageResId,
            isVideo = isVideo,
            isOutgoing = true,
            callState = CallLifecycleState.DIALING,
            pointsCostPerMinute = 5,
            durationSeconds = 0,
            isMuted = false,
            isCameraEnabled = isVideo,
            isFrontCamera = true,
            isSpeakerOn = isVideo,
            isTranslationEnabled = _isTranslationEnabled.value,
            qualityMode = CallQualityMode.ADAPTIVE_SMART,
            isAudioPriorityWhenWeak = true,
            isNetworkWeak = false,
            statusHint = "جاري التحقق من السياسة وتجهيز الاتصال...",
            liveSubtitles = null
        )

        // Initialize fallback local WebRTC preview synchronously for instant responsiveness & test compatibility
        try {
            val rtc = webRTCManager
            if (rtc != null) {
                rtc.initLocalMedia(enableVideo = isVideo, enableAudio = true, isFrontFacing = true)
                _localVideoTrack.value = rtc.localVideoTrack
            }
        } catch (e: Throwable) {
            android.util.Log.e("AyerViewModel", "WebRTC initLocalMedia skipped: ${e.message}")
        }

        val callerId = currentCallActorId()
        val callerName = currentCallActorName()
        val requestedType = if (isVideo) CallType.VIDEO else CallType.AUDIO

        viewModelScope.launch {
            val outcome = callGovernanceManager.initiateCall(
                callerId = callerId,
                callerName = callerName,
                calleeId = candidate.id,
                calleeName = candidate.name,
                requestedType = requestedType
            )

            when (outcome) {
                is CallInitiationOutcome.Blocked -> {
                    disposeWebRTCResources()
                    _activeCallSession.value = null
                    _uiNotice.value = outcome.reasonArabic
                    if (friendIdForChatLog != null) {
                        appendCallLogToPrivateChat(
                            friendId = friendIdForChatLog,
                            callId = "blocked_${System.currentTimeMillis()}",
                            isVideo = isVideo,
                            status = CallLifecycleState.BLOCKED_BUDGET.name,
                            durationSeconds = 0
                        )
                    }
                    activeFriendCallId = null
                    return@launch
                }

                is CallInitiationOutcome.Ready -> {
                    activeCallMaxDurationSeconds = outcome.maxDurationSeconds
                    val effectiveIsVideo = outcome.effectiveCallType == CallType.VIDEO

                    // If downgraded from video to audio due to admin quota/toggle, stop camera capture cleanly
                    if (outcome.downgradedToAudio) {
                        try { webRTCManager?.setVideoEnabled(false) } catch (_: Throwable) {}
                        _uiNotice.value = "تم التحويل إلى مكالمة صوتية فقط وفق حدود استهلاك الفيديو ⚙️"
                    }

                    _activeCallSession.value = _activeCallSession.value?.copy(
                        callId = outcome.callId,
                        roomName = outcome.roomName,
                        isVideo = effectiveIsVideo,
                        isCameraEnabled = effectiveIsVideo,
                        isSpeakerOn = effectiveIsVideo,
                        downgradedToAudio = outcome.downgradedToAudio,
                        callState = CallLifecycleState.RINGING_OUTGOING,
                        statusHint = "يرن الآن لدى ${candidate.name}..."
                    )

                    callAudioRouter.startCallAudio(speakerOn = effectiveIsVideo)
                    CallForegroundService.onHangupFromNotification = { endCallSession() }
                    CallForegroundService.start(getApplication(), candidate.name, effectiveIsVideo)

                    callSignalingService.publishCallInvitation(
                        callId = outcome.callId,
                        roomName = outcome.roomName,
                        callerId = callerId,
                        callerName = callerName,
                        calleeId = candidate.id,
                        callType = outcome.effectiveCallType
                    )

                    observeRemoteCallSignaling(outcome.callId)

                    if (outcome.livekitUrl.isNotBlank() && outcome.token.isNotBlank()) {
                        connectLiveKitRoom(
                            callId = outcome.callId,
                            livekitUrl = outcome.livekitUrl,
                            token = outcome.token,
                            enableVideo = effectiveIsVideo
                        )
                    } else {
                        // Local preview mode: ring for 1.8s (NEVER counted as call duration), then accept & start connected timer
                        outgoingRingJob = viewModelScope.launch {
                            delay(1800)
                            val current = _activeCallSession.value ?: return@launch
                            if (current.callId == outcome.callId &&
                                (current.callState == CallLifecycleState.DIALING || current.callState == CallLifecycleState.RINGING_OUTGOING)
                            ) {
                                markOutgoingCallAccepted(outcome.callId, candidate.id)
                            }
                        }
                    }
                }
            }
        }
    }

    fun acceptIncomingCall(invite: IncomingCallInvite) {
        if (_activeCallSession.value != null) {
            _uiNotice.value = "يوجد اتصال نشط حالياً — أنهِ المكالمة الحالية أولاً."
            return
        }
        IncomingCallBus.dismissIncomingCall(invite.callId)
        AyerNotificationManager.cancelIncomingCallNotification(getApplication(), invite.callId)

        val myId = currentCallActorId()
        val myName = currentCallActorName()
        val isVideoInitial = invite.callType == CallType.VIDEO
        activeFriendCallId = friendsList.value.firstOrNull { it.id == invite.callerId }?.id

        viewModelScope.launch {
            val outcome = callGovernanceManager.acceptIncomingInvite(
                invite = invite,
                calleeId = myId,
                calleeName = myName
            )
            when (outcome) {
                is CallInitiationOutcome.Blocked -> {
                    _uiNotice.value = outcome.reasonArabic
                }
                is CallInitiationOutcome.Ready -> {
                    activeCallMaxDurationSeconds = outcome.maxDurationSeconds
                    val effectiveIsVideo = outcome.effectiveCallType == CallType.VIDEO
                    _activeCallSession.value = ActiveCallSession(
                        callId = outcome.callId,
                        roomName = outcome.roomName,
                        participantId = invite.callerId,
                        participantName = invite.callerName,
                        participantCountry = invite.callerCountry,
                        participantAvatarRes = invite.callerAvatarRes,
                        isVideo = effectiveIsVideo,
                        isOutgoing = false,
                        callState = CallLifecycleState.ACTIVE,
                        isCameraEnabled = effectiveIsVideo,
                        isSpeakerOn = effectiveIsVideo,
                        downgradedToAudio = outcome.downgradedToAudio,
                        isTranslationEnabled = _isTranslationEnabled.value,
                        statusHint = "مكالمة نشطة ومشفرة 🔒"
                    )
                    try {
                        val rtc = webRTCManager
                        if (rtc != null) {
                            rtc.initLocalMedia(enableVideo = effectiveIsVideo, enableAudio = true, isFrontFacing = true)
                            _localVideoTrack.value = rtc.localVideoTrack
                        }
                    } catch (_: Throwable) {}

                    callAudioRouter.startCallAudio(speakerOn = effectiveIsVideo)
                    CallForegroundService.onHangupFromNotification = { endCallSession() }
                    CallForegroundService.start(getApplication(), invite.callerName, effectiveIsVideo)

                    callSignalingService.updateCallSignalState(outcome.callId, CallLifecycleState.ACTIVE, myId)
                    observeRemoteCallSignaling(outcome.callId)

                    if (outcome.livekitUrl.isNotBlank() && outcome.token.isNotBlank()) {
                        connectLiveKitRoom(
                            callId = outcome.callId,
                            livekitUrl = outcome.livekitUrl,
                            token = outcome.token,
                            enableVideo = effectiveIsVideo
                        )
                    } else {
                        startConnectedCallTimer()
                    }
                }
            }
        }
    }

    fun rejectIncomingCall(invite: IncomingCallInvite) {
        IncomingCallBus.dismissIncomingCall(invite.callId)
        AyerNotificationManager.cancelIncomingCallNotification(getApplication(), invite.callId)

        val myId = currentCallActorId()
        val myName = currentCallActorName()
        viewModelScope.launch {
            callGovernanceManager.rejectIncomingInvite(invite, myId, myName)
            callSignalingService.updateCallSignalState(invite.callId, CallLifecycleState.REJECTED, myId)
            val friendMatch = friendsList.value.firstOrNull { it.id == invite.callerId }
            if (friendMatch != null) {
                appendCallLogToPrivateChat(
                    friendId = friendMatch.id,
                    callId = invite.callId,
                    isVideo = invite.callType == CallType.VIDEO,
                    status = CallLifecycleState.REJECTED.name,
                    durationSeconds = 0
                )
            }
            _uiNotice.value = "تم رفض المكالمة الواردة من ${invite.callerName}"
        }
    }

    fun markOutgoingCallAccepted(callId: String? = null, actorId: String? = null) {
        val current = _activeCallSession.value ?: return
        val targetCallId = callId ?: current.callId
        if (current.callId != targetCallId) return

        val resolvedActor = actorId ?: current.participantId
        viewModelScope.launch {
            callGovernanceManager.markCallAccepted(targetCallId, resolvedActor)
            _activeCallSession.value = _activeCallSession.value?.copy(
                callState = CallLifecycleState.ACTIVE,
                statusHint = "مكالمة نشطة ومشفرة 🔒"
            )
            startConnectedCallTimer()
        }
    }

    private fun observeRemoteCallSignaling(callId: String) {
        callSignalingObserverJob?.cancel()
        callSignalingObserverJob = viewModelScope.launch {
            callSignalingService.observeCallStatus(callId).collect { remoteState ->
                val current = _activeCallSession.value ?: return@collect
                if (current.callId != callId || remoteState == null) return@collect
                when (remoteState) {
                    CallLifecycleState.ACTIVE -> {
                        if (current.callState == CallLifecycleState.DIALING || current.callState == CallLifecycleState.RINGING_OUTGOING) {
                            markOutgoingCallAccepted(callId, current.participantId)
                        }
                    }
                    CallLifecycleState.REJECTED -> {
                        _uiNotice.value = "تم رفض المكالمة من ${current.participantName}"
                        endCallSession(targetStatus = CallLifecycleState.REJECTED, reason = "REJECTED_BY_CALLEE")
                    }
                    CallLifecycleState.ENDED, CallLifecycleState.CANCELLED -> {
                        endCallSession(targetStatus = remoteState, reason = "PEER_ENDED")
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun connectLiveKitRoom(
        callId: String,
        livekitUrl: String,
        token: String,
        enableVideo: Boolean
    ) {
        val actorId = currentCallActorId()
        val connected = callLiveKitEngine.connect(
            livekitUrl = livekitUrl,
            token = token,
            enableVideo = enableVideo,
            onReconnecting = {
                val cur = _activeCallSession.value ?: return@connect
                _activeCallSession.value = cur.copy(
                    callState = CallLifecycleState.RECONNECTING,
                    reconnectAttempt = cur.reconnectAttempt + 1,
                    isNetworkWeak = true,
                    statusHint = "انقطع الاتصال مؤقتاً — جاري إعادة المحاولة..."
                )
                viewModelScope.launch {
                    callGovernanceManager.markConnectionState(callId, actorId, isReconnecting = true)
                }
            },
            onReconnected = {
                val cur = _activeCallSession.value ?: return@connect
                _activeCallSession.value = cur.copy(
                    callState = CallLifecycleState.ACTIVE,
                    isNetworkWeak = false,
                    statusHint = "تمت استعادة الاتصال بنجاح 📶"
                )
                viewModelScope.launch {
                    callGovernanceManager.markConnectionState(callId, actorId, isReconnecting = false)
                }
            },
            onRemoteParticipantLeft = {
                viewModelScope.launch {
                    _uiNotice.value = "غادر الطرف الآخر المكالمة — تم إغلاق الاتصال."
                    endCallSession(targetStatus = CallLifecycleState.ENDED, reason = "PEER_LEFT")
                }
            },
            onConnectionQualityWeak = { isWeak ->
                _activeCallSession.value = _activeCallSession.value?.copy(isNetworkWeak = isWeak)
            },
            onDisconnected = { reason ->
                if (_activeCallSession.value?.callId == callId) {
                    viewModelScope.launch {
                        if (!reason.isNullOrBlank()) {
                            callGovernanceManager.reportProviderError(reason)
                        }
                        endCallSession(targetStatus = CallLifecycleState.ENDED, reason = reason ?: "DISCONNECTED")
                    }
                }
            }
        )

        if (connected) {
            markOutgoingCallAccepted(callId, actorId)
        } else {
            callGovernanceManager.reportProviderError("فشل الاتصال بغرفة LiveKit")
            endCallSession(targetStatus = CallLifecycleState.FAILED, reason = "LIVEKIT_CONNECT_FAILED")
        }
    }

    private fun startConnectedCallTimer() {
        if (callTimerJob?.isActive == true) return
        callTimerJob = viewModelScope.launch {
            while (_activeCallSession.value != null) {
                delay(1000)
                val current = _activeCallSession.value ?: break
                // Strictly pause counter while dialing, ringing, or reconnecting!
                if (current.callState != CallLifecycleState.ACTIVE) {
                    continue
                }
                val newSec = current.durationSeconds + 1

                if (activeCallMaxDurationSeconds > 0 && newSec >= activeCallMaxDurationSeconds) {
                    _activeCallSession.value = current.copy(durationSeconds = newSec)
                    _uiNotice.value = "تم إنهاء المكالمة تلقائياً للوصول إلى الحد الأقصى لمدة المكالمة الواحدة."
                    endCallSession(targetStatus = CallLifecycleState.ENDED, reason = "MAX_DURATION_REACHED")
                    break
                }

                // Every 60 connected seconds, deduct points per minute
                if (newSec % 60 == 0) {
                    val balance = userProfile.value?.pointsBalance ?: 0
                    if (balance >= current.pointsCostPerMinute) {
                        repository.spendPointsForMatch(
                            current.pointsCostPerMinute,
                            "مكالمة جارية مع ${current.participantName} (دقيقة ${newSec / 60})"
                        )
                    } else {
                        _uiNotice.value = "انتهى رصيد النقاط المخصص للمكالمة. تم إنهاء المكالمة تلقائياً."
                        endCallSession(targetStatus = CallLifecycleState.ENDED, reason = "OUT_OF_COINS")
                        break
                    }
                }

                _activeCallSession.value = current.copy(
                    durationSeconds = newSec
                )
            }
        }
    }

    fun toggleCallMute() {
        val current = _activeCallSession.value ?: return
        val newMute = !current.isMuted
        _activeCallSession.value = current.copy(isMuted = newMute)
        try {
            webRTCManager?.setAudioMuted(newMute)
        } catch (_: Throwable) {}
        viewModelScope.launch {
            callLiveKitEngine.setMicrophoneMuted(newMute)
        }
        _uiNotice.value = if (newMute) "تم كتم الميكروفون 🔇" else "الميكروفون يعمل الآن 🎙️"
    }

    fun toggleCallCamera() {
        val current = _activeCallSession.value ?: return
        val newCamera = !current.isCameraEnabled
        _activeCallSession.value = current.copy(isCameraEnabled = newCamera)
        try {
            webRTCManager?.setVideoEnabled(newCamera)
        } catch (_: Throwable) {}
        viewModelScope.launch {
            callLiveKitEngine.setCameraEnabled(newCamera)
        }
        _uiNotice.value = if (newCamera) "تم تشغيل الكاميرا 📹" else "تم إيقاف الكاميرا، جاري عرض الصورة الرمزية 👤"
    }

    /**
     * Toggles between front and rear cameras during an active call session.
     * Preserves Layer 1 & Layer 2 concurrency guards.
     */
    fun toggleCameraFlip(onResult: ((Boolean) -> Unit)? = null): Boolean {
        if (!isSwitchingCamera.compareAndSet(false, true)) {
            android.util.Log.w("AyerViewModel", "Camera switch already in progress (ViewModel Layer 1 guard); rejecting concurrent UI click")
            return false
        }

        val current = _activeCallSession.value
        if (current == null) {
            isSwitchingCamera.set(false)
            _isCameraSwitchInProgress.value = false
            return false
        }

        val targetFront = !current.isFrontCamera
        _isCameraSwitchInProgress.value = true
        _activeCallSession.value = current.copy(
            isSwitchingCamera = true,
            isCameraSwitchInProgress = true,
            isTorchOn = if (targetFront) false else current.isTorchOn
        )

        try {
            callLiveKitEngine.switchCamera(targetFront)
            val started = webRTCManager?.switchCamera { success ->
                try {
                    val actualFront = webRTCManager?.isFrontCamera ?: targetFront
                    val rtcTorch = webRTCManager?.isTorchOn ?: false
                    _activeCallSession.value = _activeCallSession.value?.copy(
                        isFrontCamera = actualFront,
                        isSwitchingCamera = false,
                        isCameraSwitchInProgress = false,
                        isTorchOn = if (actualFront) false else rtcTorch
                    )
                    if (success) {
                        _uiNotice.value = if (actualFront) "تم التبديل للكاميرا الأمامية 🤳" else "تم التبديل للكاميرا الخلفية 🔄"
                    } else {
                        _uiNotice.value = "تعذر تبديل الكاميرا، يرجى المحاولة مرة أخرى ⚠️"
                    }
                    onResult?.invoke(success)
                } finally {
                    isSwitchingCamera.set(false)
                    _isCameraSwitchInProgress.value = false
                }
            } ?: true

            if (!started) {
                _activeCallSession.value = _activeCallSession.value?.copy(
                    isSwitchingCamera = false,
                    isCameraSwitchInProgress = false
                )
                _uiNotice.value = "تعذر تبديل الكاميرا، العملية جارية بالفعل ⚠️"
                isSwitchingCamera.set(false)
                _isCameraSwitchInProgress.value = false
                onResult?.invoke(false)
                return false
            }

            if (webRTCManager == null) {
                try {
                    _activeCallSession.value = current.copy(
                        isFrontCamera = targetFront,
                        isSwitchingCamera = false,
                        isCameraSwitchInProgress = false,
                        isTorchOn = if (targetFront) false else current.isTorchOn
                    )
                    _uiNotice.value = if (targetFront) "تم التبديل للكاميرا الأمامية 🤳" else "تم التبديل للكاميرا الخلفية 🔄"
                    onResult?.invoke(true)
                } finally {
                    isSwitchingCamera.set(false)
                    _isCameraSwitchInProgress.value = false
                }
            }
            return true
        } catch (e: Throwable) {
            android.util.Log.e("AyerViewModel", "Exception during switchCamera invocation: ${e.message}", e)
            _activeCallSession.value = _activeCallSession.value?.copy(
                isSwitchingCamera = false,
                isCameraSwitchInProgress = false
            )
            _uiNotice.value = "حدث خطأ أثناء تبديل الكاميرا ⚠️"
            isSwitchingCamera.set(false)
            _isCameraSwitchInProgress.value = false
            onResult?.invoke(false)
            return false
        }
    }

    fun switchCamera(onResult: ((Boolean) -> Unit)? = null): Boolean = toggleCameraFlip(onResult)

    fun toggleCallTorch() {
        val current = _activeCallSession.value ?: return
        if (current.isFrontCamera) {
            _uiNotice.value = "فلاش الكشاف متاح فقط مع الكاميرا الخلفية ⚠️"
            return
        }
        val targetTorch = !current.isTorchOn
        webRTCManager?.setTorch(targetTorch)
        _activeCallSession.value = current.copy(isTorchOn = targetTorch)
        _uiNotice.value = if (targetTorch) "تم تشغيل كشاف الفلاش 🔦" else "تم إيقاف كشاف الفلاش 💡"
    }

    fun toggleCallSpeaker() {
        val current = _activeCallSession.value ?: return
        val newSpeaker = !current.isSpeakerOn
        _activeCallSession.value = current.copy(isSpeakerOn = newSpeaker)
        callAudioRouter.setSpeakerphone(newSpeaker)
        _uiNotice.value = if (newSpeaker) "تم تفعيل مكبر الصوت (Speaker) 🔊" else "تم التحويل إلى سماعة الأذن (Earpiece) 📱"
    }

    fun setCallQualityMode(mode: CallQualityMode) {
        val current = _activeCallSession.value ?: return
        _activeCallSession.value = current.copy(qualityMode = mode)
        val msg = when (mode) {
            CallQualityMode.ADAPTIVE_SMART -> "تم تفعيل الجودة التكيفية التلقائية (LiveKit Simulcast) ⚡"
            CallQualityMode.DATA_SAVER -> "تم تفعيل وضع توفير البيانات (360p) لتوفير الباندويدث 📶"
            CallQualityMode.HIGH_DEFINITION -> "تم تفعيل أفضل جودة فيديو فائقة الوضوح (1080p HD) 💎"
        }
        _uiNotice.value = msg
    }

    fun toggleCallAudioPriority() {
        val current = _activeCallSession.value ?: return
        val newPriority = !current.isAudioPriorityWhenWeak
        _activeCallSession.value = current.copy(isAudioPriorityWhenWeak = newPriority)
        _uiNotice.value = if (newPriority) "أولوية الصوت مفعلة: الصوت مستمر بدون تقطيع حتى لو تجمد الفيديو" else "تم تعطيل أولوية الصوت"
    }

    @androidx.annotation.VisibleForTesting
    internal fun setActiveCallSessionForTesting(session: ActiveCallSession?) {
        _activeCallSession.value = session
    }

    fun toggleCallTranslation() {
        val current = _activeCallSession.value ?: return
        val newTrans = !current.isTranslationEnabled
        _activeCallSession.value = current.copy(
            isTranslationEnabled = newTrans,
            liveSubtitles = if (!newTrans) null else current.liveSubtitles
        )
        _uiNotice.value = if (newTrans) "الترجمة الفورية بالذكاء الاصطناعي للمكالمة: مفعلة 🌐" else "تم إيقاف الترجمة الفورية للمكالمة"
    }

    fun translateCallSubtitle(spokenText: String) {
        val current = _activeCallSession.value ?: return
        if (!current.isTranslationEnabled || spokenText.isBlank()) return
        val targetLang = if (_appLanguage.value == AppLanguage.ARABIC) "Arabic" else "English"
        viewModelScope.launch {
            val res = aiTranslationService.translateText(spokenText, targetLang)
            res.onSuccess { translated ->
                _activeCallSession.value = _activeCallSession.value?.copy(liveSubtitles = translated)
            }.onFailure { err ->
                _uiNotice.value = err.message ?: "تعذر الترجمة الفورية."
            }
        }
    }



    fun disposeWebRTCResources() {
        try {
            webRTCManager?.disposeCallResources()
        } catch (e: Throwable) {
            android.util.Log.e("AyerViewModel", "WebRTC cleanup failed: ${e.message}")
        }
        try {
            callLiveKitEngine.disconnect()
        } catch (_: Throwable) {}
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
    }

    fun endCallSession(targetStatus: CallLifecycleState? = null, reason: String? = null) {
        callTimerJob?.cancel()
        callTimerJob = null
        callSignalingObserverJob?.cancel()
        callSignalingObserverJob = null
        outgoingRingJob?.cancel()
        outgoingRingJob = null
        isSwitchingCamera.set(false)
        _isCameraSwitchInProgress.value = false

        val endedSession = _activeCallSession.value
        val friendIdForLog = activeFriendCallId
        activeFriendCallId = null
        _activeCallSession.value = null

        disposeWebRTCResources()
        callAudioRouter.stopCallAudio()
        CallForegroundService.onHangupFromNotification = null
        CallForegroundService.stop(getApplication())

        if (endedSession != null) {
            val resolvedActor = currentCallActorId()
            val resolvedStatus = targetStatus ?: when (endedSession.callState) {
                CallLifecycleState.DIALING, CallLifecycleState.RINGING_OUTGOING -> CallLifecycleState.MISSED
                else -> CallLifecycleState.ENDED
            }
            val duration = endedSession.durationSeconds
            val pointsSpent = (duration / 60) * endedSession.pointsCostPerMinute

            viewModelScope.launch {
                val finalizedRecord = callGovernanceManager.finalizeCall(
                    callId = endedSession.callId,
                    actorId = resolvedActor,
                    targetStatus = resolvedStatus,
                    reason = reason,
                    pointsDeducted = pointsSpent
                )
                callSignalingService.updateCallSignalState(endedSession.callId, resolvedStatus, resolvedActor)

                val finalSec = finalizedRecord?.connectedDurationSeconds ?: duration
                val finalStatus = finalizedRecord?.status ?: resolvedStatus.name
                if (friendIdForLog != null) {
                    appendCallLogToPrivateChat(
                        friendId = friendIdForLog,
                        callId = endedSession.callId,
                        isVideo = endedSession.isVideo,
                        status = finalStatus,
                        durationSeconds = finalSec
                    )
                }
                _uiNotice.value = when (finalStatus) {
                    CallLifecycleState.MISSED.name, CallLifecycleState.CANCELLED.name ->
                        "مكالمة فائتة/ملغاة مع ${endedSession.participantName} (0 ثانية)"
                    CallLifecycleState.REJECTED.name ->
                        "تم رفض المكالمة مع ${endedSession.participantName}"
                    CallLifecycleState.FAILED.name ->
                        "تعذر إتمام المكالمة مع ${endedSession.participantName}"
                    else ->
                        "انتهت المكالمة (المدة الفعلية: ${finalSec / 60} دقيقة و ${finalSec % 60} ثانية)"
                }
            }
        }
    }

    // ==========================================
    // Admin Call Governance & Monitoring Controls
    // ==========================================
    fun updateCallGovernanceConfig(config: CallGovernanceConfigEntity) {
        viewModelScope.launch {
            callGovernanceManager.updateGovernanceConfig(config)
            _uiNotice.value = "تم حفظ وتفعيل إعدادات الميزانية وحدود المكالمات ✅"
        }
    }

    fun acknowledgeCallAlert(alertId: String) {
        viewModelScope.launch {
            callGovernanceManager.acknowledgeAlert(alertId)
        }
    }

    fun toggleUserCallBlock(userId: String, blocked: Boolean) {
        viewModelScope.launch {
            callGovernanceManager.toggleUserCallBlock(userId, blocked)
            _uiNotice.value = if (blocked) "تم إيقاف المكالمات للمستخدم $userId 🚫" else "تم تفعيل المكالمات للمستخدم $userId ✅"
        }
    }

    fun toggleUserVipExemption(userId: String, exempt: Boolean) {
        viewModelScope.launch {
            callGovernanceManager.toggleUserVipExemption(userId, exempt)
            _uiNotice.value = if (exempt) "تم منح المستخدم $userId استثناء VIP 💎" else "تم إلغاء استثناء المستخدم $userId"
        }
    }

    fun getSubAgents(): List<com.example.data.model.SubAgentItem> = repository.getSubAgents()

    fun clearNotice() {
        _uiNotice.value = null
    }

    fun getCoinPackages(): List<CoinPackage> = repository.getAvailableCoinPackages()
    fun getGifts(): List<GiftItem> = repository.getAvailableGifts()
    fun getLiveStreams(): List<LiveStreamRoom> = liveStreams.value

    fun updateStreamPreviewClip(streamId: String, newTitle: String, agencyName: String, durationSeconds: Int = 4) {
        _rawLiveStreams.value = _rawLiveStreams.value.map { stream ->
            if (stream.id == streamId) {
                stream.copy(
                    previewClipTitle = newTitle,
                    agencyName = agencyName,
                    previewClipSeconds = durationSeconds,
                    isAgencyStream = true,
                    previewBadge = "$agencyName 💎"
                )
            } else {
                stream
            }
        }
        _uiNotice.value = "تم اعتماد مقطع البث المتحرك ($durationSeconds ثوانٍ) بنجاح!"
    }

    fun addAgencyStreamFromAdmin(stream: LiveStreamRoom) {
        _rawLiveStreams.value = listOf(stream) + _rawLiveStreams.value
        _uiNotice.value = "تمت إضافة بث الوكالة الجديد للشريط المتحرك خلف الكرة الأرضية!"
    }

    // ==========================================
    // Daily Missions, Reporting & Leaderboard
    // ==========================================

    fun recordChatMessageSent() {
        viewModelScope.launch {
            repository.recordChatMessageEngagement()
        }
    }

    fun claimDailyMissionReward(missionId: String, onResult: (Boolean) -> Unit = {}) {
        if (!featureFlags.value.missionRewardsEnabled) {
            _uiNotice.value = "مكافآت المهام متوقفة مؤقتاً أثناء المراجعة."
            onResult(false)
            return
        }
        viewModelScope.launch {
            val success = repository.claimDailyMissionReward(missionId)
            if (success) {
                _completedMissionsClaimCount.value += 1
                _uiNotice.value = "تم استلام مكافأة المهمة بنجاح! 🎁"

                // Trigger 'Rate Us' dialog after completing 2 or more daily missions
                if (_completedMissionsClaimCount.value >= 2) {
                    _showRateUsDialog.value = true
                }

                // Send notification for claimed mission
                AyerNotificationManager.sendDailyMissionReadyNotification(
                    context = getApplication(),
                    missionTitle = "تم إكمال ومطالبة المهمة اليومية بنجاح!",
                    rewardCoins = 20
                )
            }
            onResult(success)
        }
    }

    fun dismissRateUsDialog() {
        _showRateUsDialog.value = false
    }

    fun triggerRateUsDialog() {
        _showRateUsDialog.value = true
    }

    fun recordUserRating(rating: Int, tags: List<String>) {
        _showRateUsDialog.value = false
        _uiNotice.value = "شكراً لتقييمك الرائع ($rating/5 نجوم)! تم حفظ رأيك لمساعدتنا في تحسين تجربة Ayer ⭐"
    }

    fun submitStreamReport(
        streamId: String,
        streamTitle: String,
        hostName: String,
        reason: String,
        details: String = "",
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val user = userProfile.value
            val report = LiveStreamReport(
                streamId = streamId,
                streamTitle = streamTitle,
                hostName = hostName,
                reporterId = user?.id?.toString() ?: "user_me",
                reporterName = user?.name ?: "المستخدم",
                reason = reason,
                details = details
            )
            val result = repository.submitLiveStreamReport(report)
            _uiNotice.value = "تم إرسال البلاغ بنجاح ووضع علامة مراجعة أمنية في Firestore 🛡️"
            onResult(result.isSuccess)
        }
    }

    fun getCreatorsLeaderboard(
        scope: LeaderboardScope = LeaderboardScope.GLOBAL,
        timeframe: LeaderboardTimeframe = LeaderboardTimeframe.THIS_WEEK
    ): List<CreatorLeaderboardItem> {
        val user = userProfile.value ?: return emptyList()
        if (user.uid.isBlank() && user.name.isBlank()) return emptyList()
        if (scope == LeaderboardScope.AGENCY && user.agencyName.isBlank()) return emptyList()
        val factor = when (timeframe) {
            LeaderboardTimeframe.TODAY -> 0.25f
            LeaderboardTimeframe.THIS_WEEK -> 0.6f
            LeaderboardTimeframe.ALL_TIME -> 1.0f
        }
        return listOf(
            CreatorLeaderboardItem(
                rank = 1,
                id = user.uid.ifBlank { "user_self" },
                name = user.name.ifBlank { "حسابك" },
                country = user.country.ifBlank { "غير محدد" },
                countryCode = user.countryCode.ifBlank { "SA" },
                agencyName = user.agencyName.ifBlank { "بدون وكالة" },
                pointsAccumulated = (user.pointsBalance * factor).toInt(),
                followersCount = 0,
                isLiveNow = false,
                isCurrentUser = true
            )
        )
    }

    // ==========================================
    // Firebase Auth & +18 Age Verification Methods
    // ==========================================

    fun submitAgeVerification(dob: String, acceptedTerms: Boolean, acceptedPrivacy: Boolean) {
        val firebaseUser = auth?.currentUser
        if (firebaseUser == null) {
            _ageVerificationError.value = "يلزم تسجيل الدخول أولاً."
            return
        }
        viewModelScope.launch {
            _isSubmittingAgeVerification.value = true
            _ageVerificationError.value = null
            val displayName = userProfile.value?.name?.takeIf { it.isNotBlank() }
                ?: firebaseUser.displayName
                ?: firebaseUser.email?.substringBefore('@')
                ?: "مستخدم HUH"
            val result = repository.submitAgeVerification(
                uid = firebaseUser.uid,
                displayName = displayName,
                email = firebaseUser.email.orEmpty(),
                dobInput = dob,
                acceptedTerms = acceptedTerms,
                acceptedPrivacy = acceptedPrivacy
            )
            _isSubmittingAgeVerification.value = false
            result.fold(
                onSuccess = {
                    _uiNotice.value = "تم إرسال طلب التحقق (+18) بنجاح وهو بانتظار مراجعة الإدارة."
                },
                onFailure = { err ->
                    _ageVerificationError.value = err.message ?: "تعذر إرسال طلب التحقق."
                }
            )
        }
    }

    fun getCurrentUserEmail(): String? {
        return auth?.currentUser?.email
    }

    fun logoutUser(credentialManager: CredentialManager? = null) {
        firestoreChatJob?.cancel()
        firestoreChatJob = null
        callTimerJob?.cancel()
        callTimerJob = null
        callSignalingObserverJob?.cancel()
        callSignalingObserverJob = null
        outgoingRingJob?.cancel()
        outgoingRingJob = null
        authSessionSyncJob?.cancel()
        authSessionSyncJob = null
        privateChatJob?.cancel()
        privateChatJob = null

        viewModelScope.launch {
            withContext(NonCancellable) {
                val cm = credentialManager ?: runCatching { CredentialManager.create(getApplication()) }.getOrNull()
                if (cm != null) {
                    runCatching { cm.clearCredentialState(ClearCredentialStateRequest()) }
                }
                auth?.signOut()
                repository.clearLocalSessionData()
                _rawFriendsList.value = emptyList()
                _firestoreBlockedUsers.value = emptySet()
                _privateChatMessages.value = emptyMap()
                _ageVerificationSubmission.value = null
                _isUserLoggedIn.value = false
            }
        }
    }

    // ==========================================
    // Friendship & Private Chat System (Firestore-backed + Privacy Enforced)
    // ==========================================

    fun currentUserUid(): String = auth?.currentUser?.uid ?: userProfile.value?.uid.orEmpty()

    fun openPrivateChat(friendId: String) {
        val myUid = currentUserUid()
        if (myUid.isBlank() || friendId.isBlank()) return
        if (activePrivateChatFriendId == friendId && privateChatJob?.isActive == true) return
        activePrivateChatFriendId = friendId
        privateChatJob?.cancel()
        privateChatJob = viewModelScope.launch {
            repository.observePrivateChatMessages(myUid, friendId).collect { msgs ->
                val map = _privateChatMessages.value.toMutableMap()
                map[friendId] = msgs
                _privateChatMessages.value = map
            }
        }
    }

    fun closePrivateChat() {
        privateChatJob?.cancel()
        privateChatJob = null
        activePrivateChatFriendId = null
    }

    fun sendFriendRequest(name: String, country: String) {
        sendFriendRequest(targetUid = "", name = name, country = country)
    }

    fun sendFriendRequest(targetUid: String, name: String, country: String) {
        val myUid = currentUserUid()
        if (targetUid.isBlank() || myUid.isBlank()) {
            _uiNotice.value = "يلزم معرّف مستخدم صالح لإرسال طلب الصداقة."
            return
        }
        val myName = userProfile.value?.name?.ifBlank { "مستخدم HUH" } ?: "مستخدم HUH"
        val myCountry = userProfile.value?.country?.ifBlank { "العالم" } ?: "العالم"
        viewModelScope.launch {
            val res = repository.sendFriendRequest(
                targetUid = targetUid,
                targetName = name,
                targetCountry = country,
                myName = myName,
                myCountry = myCountry
            )
            _uiNotice.value = if (res.isSuccess) {
                "تم إرسال طلب الصداقة إلى $name ✅"
            } else {
                res.exceptionOrNull()?.message ?: "تعذر إرسال طلب الصداقة"
            }
        }
    }

    fun acceptFriendRequest(friendId: String) {
        val myName = userProfile.value?.name?.ifBlank { "مستخدم HUH" } ?: "مستخدم HUH"
        val myCountry = userProfile.value?.country?.ifBlank { "العالم" } ?: "العالم"
        viewModelScope.launch {
            val res = repository.acceptFriendRequest(friendId, myName, myCountry)
            if (res.isSuccess) {
                _rawFriendsList.value = _rawFriendsList.value.map {
                    if (it.id == friendId) it.copy(status = FriendshipStatus.ACCEPTED, lastMessage = "تم قبول الصداقة الآن") else it
                }
                _uiNotice.value = "تم قبول طلب الصداقة بنجاح!"
            } else {
                _uiNotice.value = res.exceptionOrNull()?.message ?: "تعذر قبول الطلب"
            }
        }
    }

    fun rejectFriendRequest(friendId: String) {
        viewModelScope.launch {
            repository.rejectOrUnfriend(friendId)
            _rawFriendsList.value = _rawFriendsList.value.filter { it.id != friendId }
            _uiNotice.value = "تم رفض طلب الصداقة"
        }
    }

    fun blockFriend(friendId: String) {
        viewModelScope.launch {
            repository.blockUser(friendId)
            _rawFriendsList.value = _rawFriendsList.value.map {
                if (it.id == friendId) it.copy(status = FriendshipStatus.BLOCKED, lastMessage = "تم حظر المستخدم") else it
            }
            _uiNotice.value = "تم حظر المستخدم وإخفاء جميع تفاعلاته 🛡️"
        }
    }

    fun unfriend(friendId: String) {
        viewModelScope.launch {
            repository.rejectOrUnfriend(friendId)
            _rawFriendsList.value = _rawFriendsList.value.filter { it.id != friendId }
            _uiNotice.value = "تمت إزالة الصداقة بنجاح"
        }
    }

    private suspend fun verifyPrivateMessageAllowed(friendId: String): Boolean {
        if (blockedUserIds.value.contains(friendId)) {
            _uiNotice.value = "هذا المستخدم محظور ولا يمكن مراسلته 🚫"
            return false
        }
        val isFriend = _rawFriendsList.value.any { it.id == friendId && it.status == FriendshipStatus.ACCEPTED }
        val isVerified = userProfile.value?.ageVerified == true
        val check = repository.canSendDirectMessage(friendId, isFriend, isVerified)
        if (check.isFailure) {
            _uiNotice.value = check.exceptionOrNull()?.message ?: "خصوصية المستخدم تمنع الرسائل الخاصة."
            return false
        }
        return true
    }

    fun sendPrivateTextMessage(friendId: String, text: String, textColor: String, bgColor: String) {
        if (text.isBlank()) return
        val myUid = currentUserUid()
        viewModelScope.launch {
            if (!verifyPrivateMessageAllowed(friendId)) return@launch
            val newMsg = PrivateChatMessage(
                senderId = myUid.ifBlank { "me" },
                text = text,
                textColorHex = textColor,
                bgColorHex = bgColor,
                timestamp = System.currentTimeMillis()
            )
            val res = repository.sendPrivateMessage(friendId, newMsg, text)
            if (res.isFailure) {
                _uiNotice.value = res.exceptionOrNull()?.message ?: "تعذر إرسال الرسالة."
            }
        }
    }

    fun sendPrivateMediaMessage(
        friendId: String,
        mediaUri: String,
        priceCoins: Int,
        mediaResId: Int? = null
    ) {
        val myUid = currentUserUid()
        viewModelScope.launch {
            if (!verifyPrivateMessageAllowed(friendId)) return@launch
            val newMsg = PrivateChatMessage(
                senderId = myUid.ifBlank { "me" },
                mediaResId = mediaResId,
                mediaUri = mediaUri,
                mediaPriceCoins = priceCoins.coerceAtLeast(1),
                isUnlocked = false,
                unlockProgress = 0f,
                timestamp = System.currentTimeMillis()
            )
            val res = repository.sendPrivateMessage(
                friendId,
                newMsg,
                "📷 صورة خاصة مغلقة (${newMsg.mediaPriceCoins} نقطة)"
            )
            _uiNotice.value = if (res.isSuccess) {
                "تم إرسال الصورة المغلقة مقابل ${newMsg.mediaPriceCoins} نقطة بنجاح"
            } else {
                res.exceptionOrNull()?.message ?: "تعذر إرسال الصورة."
            }
        }
    }

    fun sendVoiceMessage(
        friendId: String,
        durationSec: Int,
        voiceFilePath: String? = null,
        voiceAudioBase64: String? = null
    ) {
        if (voiceFilePath != null && voiceAudioBase64.isNullOrBlank()) {
            _uiNotice.value = "المقطع الصوتي طويل جداً ولا يمكن إرساله عبر السحابة."
            return
        }
        val myUid = currentUserUid()
        viewModelScope.launch {
            if (!verifyPrivateMessageAllowed(friendId)) return@launch
            val newMsg = PrivateChatMessage(
                senderId = myUid.ifBlank { "me" },
                text = "🎤 رسالة صوتية (${durationSec} ثانية)",
                isVoiceMessage = true,
                voiceDurationSeconds = durationSec,
                voiceFilePath = voiceFilePath,
                voiceAudioBase64 = voiceAudioBase64,
                timestamp = System.currentTimeMillis()
            )
            val res = repository.sendPrivateMessage(friendId, newMsg, "🎤 رسالة صوتية (${durationSec}ث)")
            _uiNotice.value = if (res.isSuccess) {
                "تم إرسال الرسالة الصوتية بنجاح"
            } else {
                res.exceptionOrNull()?.message ?: "تعذر إرسال الرسالة الصوتية."
            }
        }
    }

    fun translatePrivateMessage(friendId: String, messageId: String, text: String) {
        val targetLang = if (_appLanguage.value == AppLanguage.ARABIC) "Arabic" else "English"
        viewModelScope.launch {
            val res = aiTranslationService.translateText(text, targetLang)
            res.onSuccess { translated ->
                repository.updatePrivateMessageTranslation(friendId, messageId, translated)
                val map = _privateChatMessages.value.toMutableMap()
                map[friendId] = (map[friendId] ?: emptyList()).map {
                    if (it.id == messageId) it.copy(translatedText = translated) else it
                }
                _privateChatMessages.value = map
            }.onFailure { err ->
                _uiNotice.value = err.message ?: "تعذر ترجمة الرسالة."
            }
        }
    }

    fun startCallWithFriend(friend: FriendItem, isVideo: Boolean) {
        val candidate = MatchCandidate(
            id = friend.id,
            name = friend.name,
            age = friend.age,
            country = friend.country,
            countryCode = "AE",
            gender = "ANY",
            language = "العربية",
            bio = "صديق مقرب",
            interests = listOf("دردشة", "تعارف"),
            imageResId = friend.avatarResId
        )
        startCallSession(candidate, isVideo, friendIdForChatLog = friend.id)
    }

    private fun appendCallLogToPrivateChat(
        friendId: String,
        callId: String,
        isVideo: Boolean,
        status: String,
        durationSeconds: Int
    ) {
        val callLabel = if (isVideo) "مكالمة فيديو" else "مكالمة صوتية"
        val statusSummary = when (status) {
            CallLifecycleState.ENDED.name -> "$callLabel (${String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60)})"
            CallLifecycleState.REJECTED.name -> "$callLabel مرفوضة"
            CallLifecycleState.MISSED.name, CallLifecycleState.CANCELLED.name -> "$callLabel فائتة"
            CallLifecycleState.BLOCKED_BUDGET.name -> "تعذر إجراء $callLabel (سياسة الميزانية)"
            else -> "$callLabel لم تكتمل"
        }
        val myUid = currentUserUid()
        val msg = PrivateChatMessage(
            senderId = myUid.ifBlank { "me" },
            text = statusSummary,
            callLogRecordId = callId,
            callLogIsVideo = isVideo,
            callLogStatus = status,
            callLogDurationSeconds = durationSeconds,
            timestamp = System.currentTimeMillis()
        )
        val currentMap = _privateChatMessages.value.toMutableMap()
        val currentList = currentMap[friendId] ?: emptyList()
        currentMap[friendId] = currentList + msg
        _privateChatMessages.value = currentMap
        viewModelScope.launch {
            repository.sendPrivateMessage(friendId, msg, statusSummary)
        }
    }

    fun clearChatHistory(friendId: String) {
        viewModelScope.launch {
            repository.clearOwnPrivateChatMessages(friendId)
            val currentMap = _privateChatMessages.value.toMutableMap()
            currentMap[friendId] = emptyList()
            _privateChatMessages.value = currentMap
            _uiNotice.value = "تم مسح سجل رسائلك في المحادثة بنجاح"
        }
    }

    // Privacy Settings State Management (Firestore-backed)
    private val _profileVisibility = MutableStateFlow(com.example.data.model.ProfileVisibilityOption.PUBLIC)
    val profileVisibility = _profileVisibility.asStateFlow()

    private val _directMessagePermission = MutableStateFlow(com.example.data.model.DirectMessagePermission.EVERYONE)
    val directMessagePermission = _directMessagePermission.asStateFlow()

    private val _showOnlineStatus = MutableStateFlow(true)
    val showOnlineStatus = _showOnlineStatus.asStateFlow()

    private val _hideLocationInMatch = MutableStateFlow(false)
    val hideLocationInMatch = _hideLocationInMatch.asStateFlow()

    private fun persistPrivacyToFirestore() {
        val uid = currentUserUid()
        if (uid.isBlank()) return
        viewModelScope.launch {
            repository.updatePrivacySettings(
                uid = uid,
                profileVisibility = _profileVisibility.value.name,
                directMessagePermission = _directMessagePermission.value.name,
                showOnlineStatus = _showOnlineStatus.value,
                hideLocationInMatch = _hideLocationInMatch.value
            )
        }
    }

    fun setProfileVisibility(option: com.example.data.model.ProfileVisibilityOption) {
        _profileVisibility.value = option
        persistPrivacyToFirestore()
        _uiNotice.value = "تم حفظ رؤية البروفايل: ${option.title} 🔒"
    }

    fun setDirectMessagePermission(permission: com.example.data.model.DirectMessagePermission) {
        _directMessagePermission.value = permission
        persistPrivacyToFirestore()
        _uiNotice.value = "تم حفظ صلاحيات الرسائل الخاصة: ${permission.title} 💬"
    }

    fun setShowOnlineStatus(enabled: Boolean) {
        _showOnlineStatus.value = enabled
        persistPrivacyToFirestore()
        _uiNotice.value = if (enabled) "حالة الاتصال بالإنترنت ظاهرة الآن 🟢" else "تم إخفاء حالة الاتصال 👁️‍🗨️"
    }

    fun setHideLocationInMatch(enabled: Boolean) {
        _hideLocationInMatch.value = enabled
        persistPrivacyToFirestore()
        _uiNotice.value = if (enabled) "تم إخفاء الدولة والمدينة في المطابقة 🌍" else "سيتم إظهار الدولة والمدينة 📍"
    }

    fun submitUserReport(targetUserId: String, targetUserName: String, reason: String, details: String) {
        viewModelScope.launch {
            val me = userProfile.value
            val reporterId = auth?.currentUser?.uid ?: me?.uid.orEmpty()
            val reporterName = me?.name?.ifBlank { "مستخدم HUH" } ?: "مستخدم HUH"

            val report = com.example.data.model.UserProfileReport(
                targetUserId = targetUserId,
                targetUserName = targetUserName,
                reporterId = reporterId,
                reporterName = reporterName,
                reason = reason,
                details = details
            )
            val res = repository.submitUserProfileReport(report)
            _uiNotice.value = if (res.isSuccess) {
                "تم إرسال البلاغ إلى فريق الإشراف وسيتم مراجعة الحساب 🛡️"
            } else {
                "تعذر إرسال البلاغ، يرجى المحاولة لاحقاً."
            }
        }
    }

    fun reportFriend(friendId: String, friendName: String = "مستخدم", reason: String = "سلوك غير لائق", details: String = "") {
        submitUserReport(friendId, friendName, reason, details)
    }

    fun unlockMediaMessage(friendId: String, messageId: String, price: Int) {
        if (!featureFlags.value.paidMediaEnabled) {
            _uiNotice.value = "ميزة فتح المحتوى المدفوع متوقفة مؤقتاً أثناء المراجعة."
            return
        }
        val currentProfile = userProfile.value
        if (currentProfile == null || currentProfile.pointsBalance < price) {
            _uiNotice.value = "عذراً، رصيدك الحالي من النقاط لا يكفي لفتح هذا المحتوى ($price نقطة مطلوبة)"
            return
        }

        viewModelScope.launch {
            val unlockRes = repository.unlockPaidMedia(friendId, messageId, price)
            if (unlockRes.isSuccess) {
                val currentMap = _privateChatMessages.value.toMutableMap()
                val currentList = currentMap[friendId] ?: emptyList()
                currentMap[friendId] = currentList.map { msg ->
                    if (msg.id == messageId) msg.copy(isUnlocked = true, unlockProgress = 1f) else msg
                }
                _privateChatMessages.value = currentMap
                _uiNotice.value = "تم خصم $price نقطة وفتح المحتوى بنجاح!"
            } else {
                _uiNotice.value = unlockRes.exceptionOrNull()?.message ?: "تعذر إتمام عملية الدفع لفتح المحتوى."
            }
        }
    }

    fun clearUiNotice() {
        _uiNotice.value = null
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { playBillingManager.endConnection() }
        try {
            callLiveKitEngine.disconnect()
        } catch (_: Throwable) {}
        try {
            callAudioRouter.stopCallAudio()
        } catch (_: Throwable) {}
        try {
            _webRTCManager?.release()
        } catch (e: Throwable) {
            // ignore
        }
    }
}
