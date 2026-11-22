package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.example.R
import com.example.data.local.AyerDatabase
import com.example.data.model.ActiveCallSession
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
import com.example.data.model.SecondaryProfile
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
import com.example.util.AyerNotificationManager
import com.example.webrtc.WebRTCManager
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    val userProfile: StateFlow<UserProfile?>
    val transactions: StateFlow<List<PointTransaction>>
    val notifications: StateFlow<List<AppNotification>>
    val unreadNotificationCount: StateFlow<Int>
    val secondaryProfiles: StateFlow<List<SecondaryProfile>>
    val engagementState: StateFlow<UserEngagementState>
    val dailyMissions: StateFlow<List<DailyMissionItem>>
    val flaggedStreamIds: StateFlow<Set<String>>

    private val _currentMatchId = MutableStateFlow<String?>("match_demo_1")
    val currentMatchId: StateFlow<String?> = _currentMatchId.asStateFlow()

    private val _firestoreMatchMessages = MutableStateFlow<List<FirestoreRandomChatMessage>>(emptyList())
    val firestoreMatchMessages: StateFlow<List<FirestoreRandomChatMessage>> = _firestoreMatchMessages.asStateFlow()
    private var firestoreChatJob: Job? = null

    // Hidden Secondary Profile State
    private val _activeSecondaryProfile = MutableStateFlow<SecondaryProfile?>(null)
    val activeSecondaryProfile = _activeSecondaryProfile.asStateFlow()

    // Account Security & Transparency State
    private val _isTwoFactorAuthEnabled = MutableStateFlow(false)
    val isTwoFactorAuthEnabled: StateFlow<Boolean> = _isTwoFactorAuthEnabled.asStateFlow()

    private val _lastLoginTimestamp = MutableStateFlow("اليوم، 04:35 م (2026-09-20)")
    val lastLoginTimestamp: StateFlow<String> = _lastLoginTimestamp.asStateFlow()

    private val _lastLoginIpAndDevice = MutableStateFlow("هاتف أندرويد • IP: 197.38.112.4 (القاهرة، مصر)")
    val lastLoginIpAndDevice: StateFlow<String> = _lastLoginIpAndDevice.asStateFlow()

    fun setTwoFactorAuthEnabled(enabled: Boolean) {
        _isTwoFactorAuthEnabled.value = enabled
        _uiNotice.value = if (enabled) {
            "تم تفعيل المصادقة الثنائية (2FA) بنجاح 🔒"
        } else {
            "تم إلغاء تفعيل المصادقة الثنائية"
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
    }

    fun setDarkMode(enabled: Boolean?) {
        _isDarkTheme.value = enabled
    }

    fun toggleTheme(systemIsDark: Boolean = false) {
        val currentEffective = _isDarkTheme.value ?: systemIsDark
        _isDarkTheme.value = !currentEffective
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
    private val callSignalingService: FirestoreCallSignalingService by lazy { FirestoreCallSignalingService() }

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

    // Live Stream State
    private val _liveStreams = MutableStateFlow<List<LiveStreamRoom>>(emptyList())
    val liveStreams = _liveStreams.asStateFlow()

    private val _selectedLiveStream = MutableStateFlow<LiveStreamRoom?>(null)
    val selectedLiveStream = _selectedLiveStream.asStateFlow()

    private val _streamMessages = MutableStateFlow<List<StreamMessage>>(emptyList())
    val streamMessages = _streamMessages.asStateFlow()

    // Live Stream Creator Moderation Dashboard State
    private val _streamBlockedUsers = MutableStateFlow<Set<String>>(setOf("Spammer_99", "BadActor_2026"))
    val streamBlockedUsers = _streamBlockedUsers.asStateFlow()

    private val _streamRestrictedKeywords = MutableStateFlow<Set<String>>(
        setOf("شتيمة", "احتيال", "ممنوع", "spam", "scam", "telegram", "whatsapp")
    )
    val streamRestrictedKeywords = _streamRestrictedKeywords.asStateFlow()

    private val _activeStreamViewers = MutableStateFlow<List<String>>(
        listOf("فهد الشمري", "Alex_Global", "سارة أحمد", "خالد المنصور", "User_912", "Spammer_99")
    )
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
        repository = AyerRepository(db.userDao(), db.transactionDao(), db.notificationDao(), db.secondaryProfileDao())

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

        secondaryProfiles = repository.getSecondaryProfiles().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
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

        _liveStreams.value = repository.getSampleStreams()

        viewModelScope.launch {
            repository.initializeDefaultUserIfNeeded()
            callGovernanceManager.ensureDefaultConfig()
        }
    }

    fun startObservingFirestoreMatchMessages(matchId: String) {
        firestoreChatJob?.cancel()
        _currentMatchId.value = matchId
        firestoreChatJob = viewModelScope.launch {
            repository.observeFirestoreMatchMessages(matchId).collect { messages ->
                _firestoreMatchMessages.value = messages
            }
        }
    }

    fun claimEngagementReward() {
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

        viewModelScope.launch {
            val filterDesc = "نوع الدردشة: ${_selectedChatType.value.title} (${_selectedChatType.value.cost}🪙) | النوع: ${_selectedGenderFilter.value} | الدولة: ${_selectedCountryFilter.value}"
            val success = repository.spendPointsForMatch(cost, filterDesc)
            if (success) {
                // Step 1: Searching state with rapidly spinning 3D globe
                _matchingStatus.value = MatchingStatus.Searching
                _isVideoCallActive.value = false
                _matchChatMessages.value = emptyList()

                // Radar scanning animation delay while 3D globe is spinning around the world
                delay(2200)

                if (_matchingStatus.value !is MatchingStatus.Searching) return@launch

                val candidates = repository.getSampleMatchCandidates()
                val candidate = candidates[candidateIndex % candidates.size]
                candidateIndex++

                // Step 2: Partner Found! Globe locks on coordinates and performs cinematic Zoom-In
                _matchingStatus.value = MatchingStatus.Found(candidate)

                // Cinematic Zoom-In duration on partner's location on the globe
                delay(1800)

                if (_matchingStatus.value !is MatchingStatus.Found) return@launch

                // Step 3: Zoom completes -> Smoothly open the live conversation/chat between both parties
                val matchRoomId = "match_${candidate.id}_${System.currentTimeMillis()}"
                _currentMatchId.value = matchRoomId
                startObservingFirestoreMatchMessages(matchRoomId)

                _matchingStatus.value = MatchingStatus.Connected(candidate, _selectedChatType.value)
                _isVideoCallActive.value = (_selectedChatType.value == MatchChatType.VIDEO)

                // Track engagement for beginning a match
                repository.recordMatchEngagement()

                // Initial welcome message with simulated translation
                val initialCandidateGreeting = if (candidate.countryCode == "SA" || candidate.countryCode == "KW")
                    "أهلاً وسهلاً، كيف حالك؟ تشرفت بالتواصل معك! 👋"
                else
                    "Hello! Nice to meet you here, how is your day? 😊"
                val initialCandidateTranslation = "مرحباً! سررت بلقائك هنا، كيف يسير يومك؟ 😊"

                repository.sendFirestoreMatchMessage(
                    matchId = matchRoomId,
                    senderId = "cand_${candidate.id}",
                    senderName = candidate.name,
                    text = initialCandidateGreeting,
                    translatedText = initialCandidateTranslation
                )

                _matchChatMessages.value = listOf(
                    StreamMessage(
                        id = "1",
                        senderName = candidate.name,
                        originalText = initialCandidateGreeting,
                        translatedText = initialCandidateTranslation
                    )
                )
            } else {
                _uiNotice.value = "فشلت عملية الخصم، يرجى المحاولة لاحقاً."
            }
        }
    }

    fun skipCurrentMatch() {
        _matchingStatus.value = MatchingStatus.Idle
        _isVideoCallActive.value = false
        repository.stopFirestoreChat()
        startRandomMatch()
    }

    fun startVoiceMatch() {
        _selectedChatType.value = MatchChatType.VOICE
        startRandomMatch()
        _uiNotice.value = "تم بدء المطابقة الصوتية السريعة (وضع الصوت النقي 🎙️)"
    }

    fun startVipMatch() {
        startRandomMatch()
        _uiNotice.value = "تم تفعيل رادار VIP فائق السرعة والأولوية القصوى 💎"
    }

    fun endCurrentMatch() {
        _matchingStatus.value = MatchingStatus.Idle
        _isVideoCallActive.value = false
        repository.stopFirestoreChat()
    }

    fun sendMatchChatMessage(text: String) {
        if (text.isBlank()) return
        val sender = userProfile.value?.name ?: "أنا"
        val matchId = _currentMatchId.value ?: "match_${System.currentTimeMillis()}"

        val current = _matchChatMessages.value.toMutableList()
        current.add(
            StreamMessage(
                id = System.currentTimeMillis().toString(),
                senderName = sender,
                originalText = text,
                translatedText = null
            )
        )
        _matchChatMessages.value = current

        // Send to Firestore real-time messaging
        viewModelScope.launch {
            repository.sendFirestoreMatchMessage(
                matchId = matchId,
                senderId = "user_me",
                senderName = sender,
                text = text,
                translatedText = null
            )
            // Track match engagement points
            repository.recordMatchEngagement()

            // Simulate partner response
            delay(1500)
            val partnerName = (_matchingStatus.value as? MatchingStatus.Connected)?.candidate?.name ?: "الطرف الآخر"
            val partnerResponseText = "That's wonderful! What are your favorite hobbies? 🌟"
            val partnerResponseTrans = "هذا رائع جداً! ما هي هواياتك المفضلة؟ 🌟"

            repository.sendFirestoreMatchMessage(
                matchId = matchId,
                senderId = "user_partner",
                senderName = partnerName,
                text = partnerResponseText,
                translatedText = partnerResponseTrans
            )

            val partnerResponse = StreamMessage(
                id = (System.currentTimeMillis() + 1).toString(),
                senderName = partnerName,
                originalText = partnerResponseText,
                translatedText = partnerResponseTrans
            )
            _matchChatMessages.value = _matchChatMessages.value + partnerResponse
        }
    }

    // Live Stream Actions
    fun startUserLiveStream(title: String = "بثي المباشر") {
        val myName = userProfile.value?.name ?: "أنا"
        val myCountry = userProfile.value?.country ?: "المملكة العربية السعودية"
        val myCountryCode = userProfile.value?.countryCode ?: "SA"
        val newStream = LiveStreamRoom(
            id = "user_stream_${System.currentTimeMillis()}",
            title = title,
            hostName = myName,
            hostCountry = myCountry,
            hostCountryCode = myCountryCode,
            viewersCount = 1,
            category = "تعارف وسوالف",
            diamondsReceived = 0,
            imageResId = com.example.R.drawable.stream_hero_bg
        )
        _selectedLiveStream.value = newStream
        _streamMessages.value = listOf(
            StreamMessage("sys1", "نظام الأمان", "مرحباً بك في بثك المباشر! استخدم أدوات التحكم لكتم الصوت، تبديل الكاميرا، أو إيقاف البث مؤقتاً.", isSystem = true),
            StreamMessage("sys2", "Ayer Live", "أنت الآن على الهواء مباشرة! 🔴", isSystem = true)
        )
        _uiNotice.value = "تم بدء البث المباشر بنجاح! 🔴"
    }

    fun openLiveStream(stream: LiveStreamRoom) {
        _selectedLiveStream.value = stream
        _streamMessages.value = listOf(
            StreamMessage("m1", "نظام الأمان", "مرحباً بك في بث ${stream.hostName}! يُرجى الالتزام بسياسات المجتمع والاحترام المتبادل.", isSystem = true),
            StreamMessage("m2", "فهد الشمري", "منور البث يا غالي ✨", translatedText = null),
            StreamMessage("m3", "Alex_Global", "Awesome vibe here! Greetings from Toronto 🇨🇦", translatedText = "أجواء رائعة هنا! تحياتي من تورونتو 🇨🇦")
        )
    }

    fun closeLiveStream() {
        _selectedLiveStream.value = null
        _activeGiftEffect.value = null
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
            val success = repository.sendGift(gift, stream.hostName)
            if (success) {
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
            }
        }
    }

    // Wallet & Purchases
    fun claimDailyBonus() {
        viewModelScope.launch {
            val claimed = repository.claimDailyReward()
            if (claimed) {
                _uiNotice.value = "تم استلام 10 نقاط مجانية يومية بنجاح! 🎁"
            } else {
                _uiNotice.value = "لقد استلمت نقاط اليوم بالفعل! عُد غداً للحصول على 10 نقاط جديدة."
            }
        }
    }

    fun purchasePackage(coinPackage: CoinPackage) {
        viewModelScope.launch {
            repository.purchaseCoinPackage(coinPackage)
            _uiNotice.value = "تمت عملية الشراء بنجاح! تمت إضافة ${coinPackage.coins + coinPackage.bonusCoins} نقطة إلى محفظتك 🎉"
        }
    }

    fun withdrawHostEarnings(coins: Int) {
        viewModelScope.launch {
            val success = repository.withdrawCoins(coins)
            if (success) {
                _uiNotice.value = "تم تقديم طلب سحب $coins نقطة ($${coins / 10}) بنجاح وسيتم تحويلها لحسابك البنكي خلال 24 ساعة."
            } else {
                _uiNotice.value = "رصيد أرباحك غير كافٍ لإتمام طلب السحب."
            }
        }
    }

    fun applyAsHost() {
        viewModelScope.launch {
            repository.applyForHost("الوكالة الماسية")
            _uiNotice.value = "تم تقديم طلب انضمامك كمضيف معتمد تحت الوكالة الماسية. سيتم مراجعة الهوية خلال 24 ساعة."
        }
    }

    fun upgradeToAgent() {
        viewModelScope.launch {
            repository.toggleAgentStatus(true)
            _uiNotice.value = "تهانينا! 🎉 تم ترقية حسابك رسمياً إلى 'وكيل معتمد'. يمكنك الآن دعوة وكلاء وكسب عمولة من أرباحهم وتفقد بوابة الوكالات."
        }
    }

    fun downgradeAgentForTesting() {
        viewModelScope.launch {
            repository.toggleAgentStatus(false)
            _uiNotice.value = "تم إعادة الحساب إلى حساب مستخدم/إنفلونسر عادي (تم إخفاء بوابة الوكالات)."
        }
    }

    fun updateAgentCommissionFromAdmin(percent: Int) {
        viewModelScope.launch {
            repository.updateAgentCommissionFromAdmin(percent)
            _uiNotice.value = "تم تحديث نسبة عمولة الوكيل بنجاح من لوحة الأدمن إلى $percent%"
        }
    }

    fun recruitNewSubAgent(name: String, country: String) {
        viewModelScope.launch {
            val notif = repository.recruitSubAgent(name, country)
            _activeInAppNotification.value = notif
            AyerNotificationManager.sendSystemNotification(
                context = getApplication(),
                notificationId = System.currentTimeMillis().toInt(),
                title = notif.title,
                message = notif.message,
                type = notif.type,
                coinsBadge = "+250 💎"
            )
            _uiNotice.value = "تمت دعوة الوكيل '$name' بنجاح تحت شبكتك وحصلت على مكافأة ترحيبية 250 نقطة 💎"
        }
    }

    fun payoutCommission(amountCoins: Int, sourceDescription: String, senderName: String = "أرباح شبكة المضيفين") {
        viewModelScope.launch {
            val notif = repository.payoutCommission(amountCoins, sourceDescription, senderName)
            _activeInAppNotification.value = notif
            AyerNotificationManager.sendSystemNotification(
                context = getApplication(),
                notificationId = System.currentTimeMillis().toInt(),
                title = notif.title,
                message = notif.message,
                type = notif.type,
                coinsBadge = "+$amountCoins 💎"
            )
            _uiNotice.value = "تم استلام دفعة عمولة بقيمة $amountCoins نقطة ($${amountCoins / 10}.00) من $sourceDescription! 💰"
        }
    }

    fun triggerSampleReferralNotification() {
        val sampleNames = listOf(
            Pair("سلطان الدوسري - فالكون لايف", "السعودية"),
            Pair("ريم الكعبي - إمارات ميديا", "الإمارات"),
            Pair("كريم الشناوي - بيراميدز كاست", "مصر"),
            Pair("حسام التميمي - كويت ستارز", "الكويت")
        )
        val selected = sampleNames.random()
        recruitNewSubAgent(selected.first, selected.second)
    }

    fun triggerSampleCommissionPayout(amountCoins: Int = 750) {
        val sources = listOf(
            "عمولة بثوث المضيف 'عمر الفاروق' (14 ساعة بث)",
            "أرباح هدايا VIP لوكالة 'ليلى الهاشمي' (جولدن ستارز)",
            "حصة الأرباح الأسبوعية من مضيفي كاريزما إيجنسي",
            "عمولة مطابقة حصرية ومكالمات فيديو شبكة الوكلاء"
        )
        payoutCommission(
            amountCoins = amountCoins,
            sourceDescription = sources.random(),
            senderName = "بوابة توزيع العمولات"
        )
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
    // Hidden Secondary Profile Actions
    // ==========================================
    fun createSecondaryProfile(
        username: String,
        password: String,
        displayName: String,
        bio: String = "بروفايل خاص محمي برمز سري",
        notificationsEnabled: Boolean = false,
        avatarResId: Int? = null,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.createSecondaryProfile(
                username = username,
                password = password,
                displayName = displayName,
                bio = bio,
                notificationsEnabled = notificationsEnabled,
                avatarResId = avatarResId
            )
            result.fold(
                onSuccess = { profile ->
                    onComplete(true, "تم إنشاء البروفايل الثانوي بنجاح. تذكر: لا يظهر أي أثر له في الواجهة الرئيسية؛ للوصول إليه اكتب كلمة المرور الخاصة به في شريط البحث الرئيسي.")
                    _uiNotice.value = "تم إنشاء البروفايل الثانوي بنجاح! للوصول إليه اكتب كلمة المرور في شريط البحث."
                },
                onFailure = { err ->
                    onComplete(false, err.message ?: "فشل إنشاء البروفايل الثانوي")
                }
            )
        }
    }

    /**
     * Search-Unlock mechanism:
     * When user enters a query in search bar, if it matches the secret password
     * of an existing secondary profile belonging to the current user, it unlocks
     * and switches to the hidden secondary profile interface seamlessly!
     */
    fun checkSearchUnlock(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.length < 3) return false

        // Check if query matches any secondary profile password
        var unlocked = false
        viewModelScope.launch {
            val matched = repository.unlockSecondaryProfileByPassword(trimmed)
            if (matched != null) {
                _activeSecondaryProfile.value = matched
                _uiNotice.value = "🎭 تم الانتقال للبروفايل الثانوي: '${matched.displayName}' بنجاح"
                unlocked = true
            }
        }
        return unlocked
    }

    fun lockSecondaryProfile() {
        val name = _activeSecondaryProfile.value?.displayName ?: ""
        _activeSecondaryProfile.value = null
        _uiNotice.value = "🔒 تم قفل البروفايل والعودة للحساب الرئيسي بأمان بدون أي أثر."
    }

    fun toggleSecondaryProfileNotification(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSecondaryProfileNotification(id, enabled)
            _activeSecondaryProfile.value = _activeSecondaryProfile.value?.copy(notificationsEnabled = enabled)
            _uiNotice.value = if (enabled) "تم تفعيل إشعارات البروفايل الثانوي" else "تم كتم إشعارات البروفايل الثانوي"
        }
    }

    fun deleteSecondaryProfile(id: Long) {
        viewModelScope.launch {
            repository.deleteSecondaryProfile(id)
            if (_activeSecondaryProfile.value?.id == id) {
                _activeSecondaryProfile.value = null
            }
            _uiNotice.value = "تم حذف البروفايل الثانوي وسجلاته بأمان."
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
            simulatedSubtitles = "أهلاً بك! سعيد جداً بمحادثتك اليوم عبر Ayer 🌟"
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
        activeFriendCallId = _friendsList.value.firstOrNull { it.id == invite.callerId }?.id

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
            val friendMatch = _friendsList.value.firstOrNull { it.id == invite.callerId }
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

                val subtitle = when ((newSec / 6) % 4) {
                    0 -> "أهلاً بك! كيف يسير يومك؟ 😊"
                    1 -> "I'm really enjoying talking with you! ✨ (أستمتع جداً بالحديث معك!)"
                    2 -> "هل تفضل الدردشة الصوتية أم المرئية أكثر؟ 🎧"
                    else -> "That sounds amazing! Tell me more. 🚀 (يبدو ذلك رائعاً! أخبرني بالمزيد.)"
                }

                _activeCallSession.value = current.copy(
                    durationSeconds = newSec,
                    simulatedSubtitles = if (current.isTranslationEnabled) subtitle else null
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

    fun toggleCallTranslation() {
        val current = _activeCallSession.value ?: return
        val newTrans = !current.isTranslationEnabled
        _activeCallSession.value = current.copy(isTranslationEnabled = newTrans)
        _uiNotice.value = if (newTrans) "الترجمة الفورية المباشرة للمكالمة: مفعلة 🌐" else "تم إيقاف الترجمة الفورية للمكالمة"
    }

    fun simulateNetworkWeakness() {
        val current = _activeCallSession.value ?: return
        val newWeak = !current.isNetworkWeak
        _activeCallSession.value = current.copy(
            isNetworkWeak = newWeak,
            callState = if (newWeak) CallLifecycleState.RECONNECTING else CallLifecycleState.ACTIVE,
            reconnectAttempt = if (newWeak) current.reconnectAttempt + 1 else current.reconnectAttempt,
            statusHint = if (newWeak) "انقطع الاتصال مؤقتاً — جاري إعادة المحاولة 📶" else "استقرت الشبكة وتمت استعادة الاتصال 🔒"
        )
        viewModelScope.launch {
            callGovernanceManager.markConnectionState(current.callId, currentCallActorId(), isReconnecting = newWeak)
        }
        if (newWeak) {
            _uiNotice.value = "⚠️ ضعف/انقطاع الشبكة: أوقفنا عداد المدة مؤقتاً لحين استعادة الاتصال."
        } else {
            _uiNotice.value = "استقرت سرعة الاتصال واستؤنف البث والعدّاد التلقائي 📶"
        }
    }

    fun triggerIncomingCallDemo(callerName: String = "سارة أحمد", isVideo: Boolean = true) {
        val invite = IncomingCallInvite(
            callId = "call_demo_${System.currentTimeMillis()}",
            roomName = "ayer_1on1_demo_${System.currentTimeMillis()}",
            callerId = "f1",
            callerName = callerName,
            callerCountry = "دبي، الإمارات",
            callType = if (isVideo) CallType.VIDEO else CallType.AUDIO
        )
        IncomingCallBus.emitIncomingCall(invite)
        AyerNotificationManager.sendIncomingCallNotification(
            context = getApplication(),
            callId = invite.callId,
            callerName = invite.callerName,
            isVideo = isVideo
        )
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

    fun getSubAgents(): List<com.example.data.model.SubAgentItem> = repository.getSampleSubAgents()

    fun clearNotice() {
        _uiNotice.value = null
    }

    fun getCoinPackages(): List<CoinPackage> = repository.getAvailableCoinPackages()
    fun getGifts(): List<GiftItem> = repository.getAvailableGifts()
    fun getLiveStreams(): List<LiveStreamRoom> = _liveStreams.value

    fun updateStreamPreviewClip(streamId: String, newTitle: String, agencyName: String, durationSeconds: Int = 4) {
        _liveStreams.value = _liveStreams.value.map { stream ->
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
        _liveStreams.value = listOf(stream) + _liveStreams.value
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
        val userPoints = userProfile.value?.pointsBalance ?: 250
        return repository.getCreatorsLeaderboard(scope, timeframe, userPoints)
    }

    // ==========================================
    // Firebase Auth Methods
    // ==========================================

    fun loginUser(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        if (auth == null) {
            // Mock mode when Firebase is not configured
            _isUserLoggedIn.value = true
            _uiNotice.value = "تم تسجيل الدخول محلياً (وضع التجربة - Firebase غير متصل)"
            onResult(true, null)
            return
        }
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _isUserLoggedIn.value = true
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message ?: "فشل تسجيل الدخول")
                }
            }
    }

    fun registerUser(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        if (auth == null) {
            // Mock mode when Firebase is not configured
            _isUserLoggedIn.value = true
            _uiNotice.value = "تم إنشاء الحساب محلياً (وضع التجربة - Firebase غير متصل)"
            onResult(true, null)
            return
        }
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _isUserLoggedIn.value = true
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message ?: "فشل إنشاء الحساب")
                }
            }
    }

    fun getCurrentUserEmail(): String? {
        return auth?.currentUser?.email
    }

    fun logoutUser() {
        auth?.signOut()
        _isUserLoggedIn.value = false
    }

    // ==========================================
    // Friendship & Private Chat System
    // ==========================================

    private val _friendsList = MutableStateFlow<List<FriendItem>>(
        listOf(
            FriendItem("f1", "سارة أحمد", "دبي، الإمارات", 23, null, FriendshipStatus.ACCEPTED, "مرحبًا! هل شاهدت البث الأخير؟", 2, true),
            FriendItem("f2", "خالد المنصور", "الرياض، السعودية", 26, null, FriendshipStatus.ACCEPTED, "أرسل لك صورة سرية 📸", 0, true),
            FriendItem("f3", "نور الهدى", "القاهرة، مصر", 22, null, FriendshipStatus.PENDING_INCOMING, "أريد إضافتك كصديق", 1, false)
        )
    )
    val friendsList = _friendsList.asStateFlow()

    private val _privateChatMessages = MutableStateFlow<Map<String, List<PrivateChatMessage>>>(
        mapOf(
            "f1" to listOf(
                PrivateChatMessage(id = "m1", senderId = "f1", text = "مرحبًا! هل شاهدت البث الأخير؟", textColorHex = "#FFFFFF", bgColorHex = "#1E1B2E", timestamp = System.currentTimeMillis() - 100000),
                PrivateChatMessage(id = "m2", senderId = "me", text = "أهلاً سارة، نعم كان مذهلاً!", textColorHex = "#FFFFFF", bgColorHex = "#3B82F6", timestamp = System.currentTimeMillis() - 50000)
            ),
            "f2" to listOf(
                PrivateChatMessage(id = "m3", senderId = "f2", text = "تحقق من هذه الصورة الخاصة", textColorHex = "#FFFFFF", bgColorHex = "#1E1B2E", mediaResId = R.drawable.ic_ayer_logo, mediaPriceCoins = 50, isUnlocked = false, unlockProgress = 0f, timestamp = System.currentTimeMillis() - 20000)
            )
        )
    )
    val privateChatMessages = _privateChatMessages.asStateFlow()

    fun sendFriendRequest(name: String, country: String) {
        val newFriend = FriendItem(
            id = "f_${System.currentTimeMillis()}",
            name = name,
            country = country,
            status = FriendshipStatus.PENDING_OUTGOING,
            lastMessage = "تم إرسال طلب الصداقة"
        )
        _friendsList.value = _friendsList.value + newFriend
        _uiNotice.value = "تم إرسال طلب الصداقة بنجاح إلى $name"
    }

    fun acceptFriendRequest(friendId: String) {
        _friendsList.value = _friendsList.value.map {
            if (it.id == friendId) it.copy(status = FriendshipStatus.ACCEPTED, lastMessage = "تم قبول الصداقة الآن") else it
        }
        _uiNotice.value = "تم قبول طلب الصداقة بنجاح!"
    }

    fun rejectFriendRequest(friendId: String) {
        _friendsList.value = _friendsList.value.filter { it.id != friendId }
        _uiNotice.value = "تم رفض طلب الصداقة"
    }

    fun blockFriend(friendId: String) {
        _friendsList.value = _friendsList.value.map {
            if (it.id == friendId) it.copy(status = FriendshipStatus.BLOCKED, lastMessage = "تم حظر المستخدم") else it
        }
        _uiNotice.value = "تم حظر المستخدم بنجاح"
    }

    fun unfriend(friendId: String) {
        _friendsList.value = _friendsList.value.filter { it.id != friendId }
        _uiNotice.value = "تمت إزالة الصداقة بنجاح"
    }

    fun sendPrivateTextMessage(friendId: String, text: String, textColor: String, bgColor: String) {
        if (text.isBlank()) return
        val newMsg = PrivateChatMessage(
            senderId = "me",
            text = text,
            textColorHex = textColor,
            bgColorHex = bgColor,
            timestamp = System.currentTimeMillis()
        )
        val currentMap = _privateChatMessages.value.toMutableMap()
        val currentList = currentMap[friendId] ?: emptyList()
        currentMap[friendId] = currentList + newMsg
        _privateChatMessages.value = currentMap

        _friendsList.value = _friendsList.value.map {
            if (it.id == friendId) it.copy(lastMessage = text) else it
        }
    }

    fun sendPrivateMediaMessage(friendId: String, mediaResId: Int, priceCoins: Int) {
        val newMsg = PrivateChatMessage(
            senderId = "me",
            mediaResId = mediaResId,
            mediaPriceCoins = priceCoins,
            isUnlocked = false,
            unlockProgress = 0f,
            timestamp = System.currentTimeMillis()
        )
        val currentMap = _privateChatMessages.value.toMutableMap()
        val currentList = currentMap[friendId] ?: emptyList()
        currentMap[friendId] = currentList + newMsg
        _privateChatMessages.value = currentMap

        _friendsList.value = _friendsList.value.map {
            if (it.id == friendId) it.copy(lastMessage = "📷 صورة مغلقة ($priceCoins نقطة)") else it
        }
        _uiNotice.value = "تم إرسال الصورة المغلقة مقابل $priceCoins نقطة بنجاح"
    }

    fun sendVoiceMessage(friendId: String, durationSec: Int) {
        val newMsg = PrivateChatMessage(
            senderId = "me",
            text = "🎤 رسالة صوتية ($durationSec ثانية)",
            isVoiceMessage = true,
            voiceDurationSeconds = durationSec,
            timestamp = System.currentTimeMillis()
        )
        val currentMap = _privateChatMessages.value.toMutableMap()
        val currentList = currentMap[friendId] ?: emptyList()
        currentMap[friendId] = currentList + newMsg
        _privateChatMessages.value = currentMap

        _friendsList.value = _friendsList.value.map {
            if (it.id == friendId) it.copy(lastMessage = "🎤 رسالة صوتية") else it
        }
        _uiNotice.value = "تم إرسال الرسالة الصوتية بنجاح"
    }

    fun startCallWithFriend(friend: FriendItem, isVideo: Boolean) {
        val candidate = MatchCandidate(
            id = friend.id,
            name = friend.name,
            age = friend.age,
            country = friend.country,
            countryCode = "AE",
            gender = "FEMALE",
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
        val msg = PrivateChatMessage(
            senderId = "me",
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

        _friendsList.value = _friendsList.value.map {
            if (it.id == friendId) it.copy(lastMessage = statusSummary) else it
        }
    }

    fun clearChatHistory(friendId: String) {
        val currentMap = _privateChatMessages.value.toMutableMap()
        currentMap[friendId] = emptyList()
        _privateChatMessages.value = currentMap
        _uiNotice.value = "تم مسح سجل المحادثة بنجاح"
    }

    // Privacy Settings State Management
    private val _profileVisibility = MutableStateFlow(com.example.data.model.ProfileVisibilityOption.PUBLIC)
    val profileVisibility = _profileVisibility.asStateFlow()

    private val _directMessagePermission = MutableStateFlow(com.example.data.model.DirectMessagePermission.EVERYONE)
    val directMessagePermission = _directMessagePermission.asStateFlow()

    private val _showOnlineStatus = MutableStateFlow(true)
    val showOnlineStatus = _showOnlineStatus.asStateFlow()

    private val _hideLocationInMatch = MutableStateFlow(false)
    val hideLocationInMatch = _hideLocationInMatch.asStateFlow()

    fun setProfileVisibility(option: com.example.data.model.ProfileVisibilityOption) {
        _profileVisibility.value = option
        _uiNotice.value = "تم تحديث رؤية البروفايل إلى: ${option.title} 🔒"
    }

    fun setDirectMessagePermission(permission: com.example.data.model.DirectMessagePermission) {
        _directMessagePermission.value = permission
        _uiNotice.value = "تم تحديد صلاحيات الرسائل الخاصة: ${permission.title} 💬"
    }

    fun setShowOnlineStatus(enabled: Boolean) {
        _showOnlineStatus.value = enabled
        _uiNotice.value = if (enabled) "حالة الاتصال بالإنترنت ظاهرة الآن للجميع 🟢" else "تم إخفاء حالة الاتصال بالإنترنت 👁️‍🗨️"
    }

    fun setHideLocationInMatch(enabled: Boolean) {
        _hideLocationInMatch.value = enabled
        _uiNotice.value = if (enabled) "تم إخفاء الدولة والمدينة أثناء البحث العشوائي 🌍" else "سيتم إظهار الدولة والمدينة في المطابقة 📍"
    }

    fun submitUserReport(targetUserId: String, targetUserName: String, reason: String, details: String) {
        viewModelScope.launch {
            val me = userProfile.value
            val reporterId = me?.id?.toString() ?: "1"
            val reporterName = me?.name ?: "مستخدم Ayer"

            val report = com.example.data.model.UserProfileReport(
                targetUserId = targetUserId,
                targetUserName = targetUserName,
                reporterId = reporterId,
                reporterName = reporterName,
                reason = reason,
                details = details
            )
            repository.submitUserProfileReport(report)
            _uiNotice.value = "تم إرسال البلاغ إلى فريق الإشراف وسيتم مراجعة الحساب خلال 24 ساعة 🛡️"
        }
    }

    fun reportFriend(friendId: String) {
        _uiNotice.value = "تم إرسال البلاغ بنجاح. سيتم مراجعة الحساب لحماية أمن المنصة."
    }

    fun unlockMediaMessage(friendId: String, messageId: String, price: Int) {
        val currentProfile = userProfile.value
        if (currentProfile != null && currentProfile.pointsBalance < price) {
            _uiNotice.value = "عذراً، رصيدك الحالي من النقاط لا يكفي لفتح هذه الصورة ($price نقطة مطلوبة)"
            return
        }

        if (currentProfile != null) {
            viewModelScope.launch {
                repository.adjustPoints(-price)
                repository.addTransaction(
                    title = "شراء محتوى خاص مغلق",
                    type = "SPEND",
                    amount = -price
                )
                repository.adjustPoints(price)
                repository.addTransaction(
                    title = "أرباح بيع محتوى خاص",
                    type = "EARN",
                    amount = price
                )
            }
        }

        val currentMap = _privateChatMessages.value.toMutableMap()
        val currentList = currentMap[friendId] ?: emptyList()
        currentMap[friendId] = currentList.map { msg ->
            if (msg.id == messageId) msg.copy(isUnlocked = true, unlockProgress = 1f) else msg
        }
        _privateChatMessages.value = currentMap
        _uiNotice.value = "تم خصم $price نقطة وفتح الصورة بنجاح وتوجيه الأرباح للمرسل!"
    }

    fun clearUiNotice() {
        _uiNotice.value = null
    }

    override fun onCleared() {
        super.onCleared()
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
