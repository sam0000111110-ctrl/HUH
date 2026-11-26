package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.data.local.NotificationDao
import com.example.data.local.TransactionDao
import com.example.data.local.UserDao
import com.example.data.model.AgeVerificationSubmissionDoc
import com.example.data.model.AppNotification
import com.example.data.model.CoinPackage
import com.example.data.model.CreatorLeaderboardItem
import com.example.data.model.DailyMissionItem
import com.example.data.model.FirestoreRandomChatMessage
import com.example.data.model.FirestoreUserDoc
import com.example.data.model.GiftItem
import com.example.data.model.LeaderboardScope
import com.example.data.model.LeaderboardTimeframe
import com.example.data.model.LiveStreamReport
import com.example.data.model.LiveStreamRoom
import com.example.data.model.NotificationType
import com.example.data.model.PointTransaction
import com.example.data.model.SubAgentItem
import com.example.data.model.UserEngagementState
import com.example.data.model.UserProfile
import com.example.data.model.UserProfileReport
import com.example.data.remote.AdminAgeVerificationItemDto
import com.example.data.remote.AdminUpdateRolesRequestDto
import com.example.data.remote.ApplyHostRequestDto
import com.example.data.remote.AuthSyncRequestDto
import com.example.data.remote.BackendLedgerEntryDto
import com.example.data.remote.BackendPlatformApi
import com.example.data.remote.BackendUserDto
import com.example.data.remote.BackendWalletDto
import com.example.data.remote.BlockUserRequestDto
import com.example.data.remote.CreatePayoutRequestDto
import com.example.data.remote.CreateReportRequestDto
import com.example.data.remote.FeatureFlagsDto
import com.example.data.remote.FirestoreChatService
import com.example.data.remote.JoinMatchRequestDto
import com.example.data.remote.LeaveMatchRequestDto
import com.example.data.remote.LiveStreamDto
import com.example.data.remote.MatchJoinPollResponseDto
import com.example.data.remote.MissionRewardRequestDto
import com.example.data.remote.ModerationReportDto
import com.example.data.remote.PayoutRequestDto
import com.example.data.remote.RecruitSubAgentRequestDto
import com.example.data.remote.ResolveReportRequestDto
import com.example.data.remote.ReviewAgeRequestDto
import com.example.data.remote.ReviewPayoutRequestDto
import com.example.data.remote.SendGiftRequestDto
import com.example.data.remote.SpendCoinsRequestDto
import com.example.data.remote.StartStreamRequestDto
import com.example.data.remote.StreamSessionResponseDto
import com.example.data.remote.SubmitAgeVerificationRequestDto
import com.example.data.remote.VerifyPlayPurchaseRequestDto
import com.example.util.AgePolicyUtils
import com.example.util.SecurityUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await

class AyerRepository(
    private val context: Context,
    private val userDao: UserDao,
    private val transactionDao: TransactionDao,
    private val notificationDao: NotificationDao,
    private val platformApi: BackendPlatformApi? = buildOptionalPlatformApi()
) {
    companion object {
        private fun buildOptionalPlatformApi(): BackendPlatformApi? {
            val url = BuildConfig.CALL_BACKEND_URL.trim()
            val isAllowedScheme = url.startsWith("https://") ||
                (BuildConfig.DEBUG && url.startsWith("http://"))
            return if (isAllowedScheme) BackendPlatformApi.create(url) else null
        }
    }

    private val firestoreChatService = FirestoreChatService(context.applicationContext)

    val userProfile: Flow<UserProfile?> = userDao.getUserProfile()
    val transactions: Flow<List<PointTransaction>> = transactionDao.getAllTransactions()
    val notifications: Flow<List<AppNotification>> = notificationDao.getAllNotifications()
    val unreadNotificationCount: Flow<Int> = notificationDao.getUnreadCount()

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds = _blockedUserIds.asStateFlow()

    private val _subAgents = MutableStateFlow<List<SubAgentItem>>(emptyList())
    val subAgents = _subAgents.asStateFlow()

    private val _myPayouts = MutableStateFlow<List<PayoutRequestDto>>(emptyList())
    val myPayouts = _myPayouts.asStateFlow()

    private val _engagementState = MutableStateFlow(UserEngagementState())
    val engagementState = _engagementState.asStateFlow()

    private val _featureFlags = MutableStateFlow(FeatureFlagsDto())
    val featureFlags = _featureFlags.asStateFlow()

    suspend fun refreshFeatureFlags(): FeatureFlagsDto {
        val api = platformApi ?: return _featureFlags.value
        val res = runCatching { api.getFeatureFlags() }.getOrNull()
        if (res != null && res.isSuccessful && res.body() != null) {
            _featureFlags.value = res.body()!!
        }
        return _featureFlags.value
    }

    internal fun setFeatureFlagsForTesting(flags: FeatureFlagsDto) {
        _featureFlags.value = flags
    }

    private val _dailyMissions = MutableStateFlow(
        listOf(
            DailyMissionItem(
                id = "mission_stream",
                title = "مشاهدة البث المباشر",
                description = "شاهد البثوث المباشرة لمدة 30 دقيقة اليوم",
                iconName = "live",
                currentProgress = 0,
                targetProgress = 30,
                rewardPoints = 35,
                unit = "دقيقة"
            ),
            DailyMissionItem(
                id = "mission_chat",
                title = "إرسال رسائل في الدردشة",
                description = "تفاعل وأرسل 10 رسائل في غرف المطابقة والمحادثات",
                iconName = "chat",
                currentProgress = 0,
                targetProgress = 10,
                rewardPoints = 20,
                unit = "رسائل"
            ),
            DailyMissionItem(
                id = "mission_matches",
                title = "إجراء مطابقات عشوائية",
                description = "أكمل 5 مطابقات جديدة وتواصل مع أعضاء حول العالم",
                iconName = "match",
                currentProgress = 0,
                targetProgress = 5,
                rewardPoints = 25,
                unit = "مطابقة"
            ),
            DailyMissionItem(
                id = "mission_gifts",
                title = "إرسال هدايا تشجيعية",
                description = "ادعم المضيفين المفضلين بإرسال 3 هدايا في البث",
                iconName = "gift",
                currentProgress = 0,
                targetProgress = 3,
                rewardPoints = 30,
                unit = "هدايا"
            ),
            DailyMissionItem(
                id = "mission_daily_login",
                title = "تسجيل الحضور اليومي",
                description = "حافظ على سلسلة التواجد اليومي النشط في التطبيق",
                iconName = "login",
                currentProgress = 0,
                targetProgress = 1,
                rewardPoints = 15,
                unit = "يوم"
            )
        )
    )
    val dailyMissions = _dailyMissions.asStateFlow()

    val flaggedStreamIds = firestoreChatService.flaggedStreamIds
    val submittedReports = firestoreChatService.submittedReports

    private suspend fun getBearerHeader(): String? {
        val token = try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        } catch (_: Throwable) {
            null
        }
        return token?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
    }

    private suspend fun applyAuthoritativeSummary(
        backendUser: BackendUserDto? = null,
        wallet: BackendWalletDto,
        ledgerEntries: List<BackendLedgerEntryDto>? = null
    ) {
        val current = userDao.getUserProfileSync() ?: UserProfile()
        val updated = current.copy(
            uid = backendUser?.uid?.ifBlank { current.uid } ?: current.uid,
            name = backendUser?.displayName?.ifBlank { current.name } ?: current.name,
            email = backendUser?.email?.ifBlank { current.email } ?: current.email,
            dob = backendUser?.dob ?: current.dob,
            age = backendUser?.ageYears?.takeIf { it > 0 } ?: current.age,
            ageVerified = backendUser?.ageVerified ?: current.ageVerified,
            ageVerificationStatus = backendUser?.ageVerificationStatus?.ifBlank { current.ageVerificationStatus } ?: current.ageVerificationStatus,
            isAdmin = backendUser?.isAdmin ?: current.isAdmin,
            isAgent = backendUser?.isAgent ?: current.isAgent,
            isHost = backendUser?.isHost ?: current.isHost,
            hostStatus = backendUser?.hostStatus?.ifBlank { current.hostStatus } ?: current.hostStatus,
            agentCommissionPercent = backendUser?.agentCommissionPercent ?: current.agentCommissionPercent,
            hostCommissionPercent = backendUser?.hostCommissionPercent ?: current.hostCommissionPercent,
            agencyId = backendUser?.agencyId?.ifBlank { current.agencyId } ?: current.agencyId,
            agencyName = backendUser?.agencyName?.ifBlank { current.agencyName } ?: current.agencyName,
            referralCode = backendUser?.referralCode?.ifBlank { current.referralCode } ?: current.referralCode,
            profileVisibility = backendUser?.profileVisibility?.ifBlank { current.profileVisibility } ?: current.profileVisibility,
            directMessagePermission = backendUser?.directMessagePermission?.ifBlank { current.directMessagePermission } ?: current.directMessagePermission,
            pointsBalance = wallet.pointsBalance,
            hostEarningsCoins = wallet.hostEarningsCoins,
            subAgentsEarningsCoins = wallet.subAgentsEarningsCoins,
            totalAccumulatedPoints = wallet.totalAccumulatedPoints,
            totalPointsSpent = wallet.totalPointsSpent,
            lastDailyClaimTime = wallet.lastDailyClaimMs
        )
        userDao.insertUser(updated)
        if (ledgerEntries != null) {
            transactionDao.clearAll()
            ledgerEntries.forEach { entry ->
                transactionDao.insertTransaction(
                    PointTransaction(
                        type = entry.type,
                        amount = entry.amount,
                        description = entry.description,
                        timestamp = if (entry.timestamp > 0L) entry.timestamp else System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun syncAuthenticatedUser(firebaseUser: FirebaseUser): Result<UserProfile> {
        val remoteResult = firestoreChatService.syncUserOnSignIn(firebaseUser)
        val localExisting = userDao.getUserProfileSync()

        val remoteDoc = remoteResult.getOrNull()
        val resolvedProfile = if (remoteDoc != null) {
            UserProfile(
                id = 1L,
                uid = firebaseUser.uid,
                name = remoteDoc.displayName.ifBlank {
                    firebaseUser.displayName ?: firebaseUser.email?.substringBefore('@') ?: "مستخدم HUH"
                },
                age = remoteDoc.age,
                country = remoteDoc.country,
                countryCode = remoteDoc.countryCode,
                gender = remoteDoc.gender,
                pointsBalance = remoteDoc.pointsBalance,
                totalAccumulatedPoints = remoteDoc.totalAccumulatedPoints,
                totalPointsSpent = remoteDoc.totalPointsSpent,
                lastDailyClaimTime = localExisting?.lastDailyClaimTime ?: 0L,
                ageVerified = remoteDoc.ageVerified,
                ageVerificationStatus = remoteDoc.ageVerificationStatus,
                dob = remoteDoc.dob,
                acceptedTermsAt = remoteDoc.acceptedTermsAt?.toDate()?.time,
                acceptedPrivacyAt = remoteDoc.acceptedPrivacyAt?.toDate()?.time,
                isHost = remoteDoc.isHost,
                hostStatus = remoteDoc.hostStatus,
                isAgent = remoteDoc.isAgent,
                isAdmin = remoteDoc.isAdmin,
                agentCommissionPercent = remoteDoc.agentCommissionPercent,
                subAgentsEarningsCoins = remoteDoc.subAgentsEarningsCoins,
                subAgentsCount = remoteDoc.subAgentsCount,
                referralCode = localExisting?.referralCode ?: "HUH-${firebaseUser.uid.take(6).uppercase()}",
                agencyName = localExisting?.agencyName ?: "",
                agencyId = localExisting?.agencyId ?: "",
                agencyRole = localExisting?.agencyRole ?: "",
                agencyJoinedDate = localExisting?.agencyJoinedDate ?: "",
                agencyManagerName = localExisting?.agencyManagerName ?: "",
                agencyActiveHostsCount = localExisting?.agencyActiveHostsCount ?: 0,
                agencyMonthlyTargetProgress = localExisting?.agencyMonthlyTargetProgress ?: 0,
                hostCommissionPercent = remoteDoc.hostCommissionPercent,
                hostEarningsCoins = remoteDoc.hostEarningsCoins,
                totalMatchesCount = localExisting?.totalMatchesCount ?: 0,
                agencyLevel = localExisting?.agencyLevel ?: "Lv.1 🌟",
                bio = remoteDoc.bio,
                phoneNumber = localExisting?.phoneNumber ?: "",
                email = remoteDoc.email.ifBlank { firebaseUser.email.orEmpty() },
                avatarResId = localExisting?.avatarResId ?: com.example.R.drawable.img_user_avatar,
                avatarCustomUri = remoteDoc.photoUrl ?: localExisting?.avatarCustomUri,
                profileVisibility = remoteDoc.profileVisibility,
                directMessagePermission = remoteDoc.directMessagePermission
            )
        } else {
            (localExisting ?: UserProfile()).copy(
                id = 1L,
                uid = firebaseUser.uid,
                name = localExisting?.name?.takeIf { it.isNotBlank() }
                    ?: firebaseUser.displayName
                    ?: firebaseUser.email?.substringBefore('@')
                    ?: "مستخدم HUH",
                email = firebaseUser.email ?: localExisting?.email.orEmpty(),
                avatarCustomUri = firebaseUser.photoUrl?.toString() ?: localExisting?.avatarCustomUri,
                referralCode = localExisting?.referralCode?.takeIf { it.isNotBlank() }
                    ?: "HUH-${firebaseUser.uid.take(6).uppercase()}"
            )
        }

        userDao.insertUser(resolvedProfile)
        // Authoritative backend PostgreSQL sync (wallet, transactions, role flags, blocks)
        val api = platformApi
        val bearer = getBearerHeader()
        if (api != null && bearer != null) {
            runCatching {
                val res = api.syncAuth(
                    bearer,
                    AuthSyncRequestDto(
                        displayName = resolvedProfile.name,
                        email = resolvedProfile.email
                    )
                )
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    _featureFlags.value = body.featureFlags
                    applyAuthoritativeSummary(body.user, body.wallet, body.transactions)
                }
                val blocksRes = api.getBlocks(bearer)
                if (blocksRes.isSuccessful && blocksRes.body() != null) {
                    _blockedUserIds.value = blocksRes.body()!!.blockedUsers.toSet()
                }
            }
        }
        runCatching {
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            if (!fcmToken.isNullOrBlank()) {
                firestoreChatService.saveFcmToken(firebaseUser.uid, fcmToken)
            }
        }
        _dailyMissions.value = _dailyMissions.value.map { mission ->
            if (mission.id == "mission_daily_login") {
                mission.copy(currentProgress = 1)
            } else {
                mission
            }
        }
        if (remoteResult.isFailure && localExisting == null) {
            return Result.failure(remoteResult.exceptionOrNull() ?: IllegalStateException("Sync failed"))
        }
        return Result.success(userDao.getUserProfileSync() ?: resolvedProfile)
    }

    suspend fun applyRemoteUserSnapshot(doc: FirestoreUserDoc) {
        val current = userDao.getUserProfileSync() ?: UserProfile()
        if (current.uid.isNotBlank() && doc.uid.isNotBlank() && current.uid != doc.uid) return
        userDao.insertUser(
            current.copy(
                uid = doc.uid.ifBlank { current.uid },
                name = doc.displayName.ifBlank { current.name },
                email = doc.email.ifBlank { current.email },
                bio = doc.bio.ifBlank { current.bio },
                country = doc.country.ifBlank { current.country },
                countryCode = doc.countryCode.ifBlank { current.countryCode },
                gender = doc.gender.ifBlank { current.gender },
                age = if (doc.age > 0) doc.age else current.age,
                dob = doc.dob ?: current.dob,
                acceptedTermsAt = doc.acceptedTermsAt?.toDate()?.time ?: current.acceptedTermsAt,
                acceptedPrivacyAt = doc.acceptedPrivacyAt?.toDate()?.time ?: current.acceptedPrivacyAt,
                ageVerified = doc.ageVerified,
                ageVerificationStatus = doc.ageVerificationStatus,
                isAdmin = doc.isAdmin,
                isAgent = doc.isAgent,
                isHost = doc.isHost,
                hostStatus = doc.hostStatus,
                agentCommissionPercent = doc.agentCommissionPercent,
                hostCommissionPercent = doc.hostCommissionPercent,
                pointsBalance = doc.pointsBalance,
                totalAccumulatedPoints = doc.totalAccumulatedPoints,
                totalPointsSpent = doc.totalPointsSpent,
                hostEarningsCoins = doc.hostEarningsCoins,
                subAgentsEarningsCoins = doc.subAgentsEarningsCoins,
                subAgentsCount = doc.subAgentsCount,
                profileVisibility = doc.profileVisibility.ifBlank { current.profileVisibility },
                directMessagePermission = doc.directMessagePermission.ifBlank { current.directMessagePermission }
            )
        )
    }

    fun observeRemoteUserDoc(uid: String): Flow<FirestoreUserDoc?> =
        firestoreChatService.observeUserDoc(uid)

    fun observeAgeVerification(uid: String): Flow<AgeVerificationSubmissionDoc?> =
        firestoreChatService.observeAgeVerification(uid)

    suspend fun submitAgeVerification(
        uid: String,
        displayName: String,
        email: String,
        dobInput: String,
        acceptedTerms: Boolean,
        acceptedPrivacy: Boolean
    ): Result<AgePolicyUtils.AgeCheckResult> {
        if (!acceptedTerms || !acceptedPrivacy) {
            return Result.failure(IllegalArgumentException("يجب الموافقة على شروط الاستخدام وسياسة الخصوصية أولاً."))
        }
        val check = AgePolicyUtils.evaluateDob(dobInput)
        if (!check.isValidFormat || !check.isAdult) {
            return Result.failure(IllegalArgumentException(check.errorMessage ?: "غير مؤهل عمرياً (+18 مطلوب)."))
        }

        val remote = firestoreChatService.submitAgeVerification(
            uid = uid,
            displayName = displayName,
            email = email,
            dob = check.normalizedDob,
            ageYears = check.ageYears
        )
        if (remote.isFailure) {
            return Result.failure(remote.exceptionOrNull() ?: IllegalStateException("تعذر إرسال طلب التحقق."))
        }

        val now = System.currentTimeMillis()
        val api = platformApi
        val bearer = getBearerHeader()
        if (api != null && bearer != null) {
            runCatching {
                api.submitAgeVerification(
                    bearer,
                    SubmitAgeVerificationRequestDto(
                        displayName = displayName,
                        email = email,
                        dob = check.normalizedDob,
                        ageYears = check.ageYears,
                        acceptedTermsAt = now,
                        acceptedPrivacyAt = now
                    )
                )
            }
        }

        userDao.updateAgeVerificationState(
            ageVerified = false,
            status = "PENDING_REVIEW",
            dob = check.normalizedDob,
            age = check.ageYears,
            acceptedTermsAt = now,
            acceptedPrivacyAt = now
        )
        return Result.success(check)
    }

    suspend fun clearLocalSessionData() {
        firestoreChatService.stopObserving()
        userDao.clearAll()
        transactionDao.clearAll()
        notificationDao.clearAll()
        _engagementState.value = UserEngagementState()
    }

    fun observeFirestoreMatchMessages(matchId: String): Flow<List<FirestoreRandomChatMessage>> {
        return firestoreChatService.observeMatchMessages(matchId)
    }

    suspend fun sendFirestoreMatchMessage(
        matchId: String,
        senderId: String,
        senderName: String,
        text: String,
        translatedText: String? = null
    ): Result<FirestoreRandomChatMessage> {
        return firestoreChatService.sendMessage(
            matchId = matchId,
            senderId = senderId,
            senderName = senderName,
            text = text,
            translatedText = translatedText
        )
    }

    fun stopFirestoreChat() {
        firestoreChatService.stopObserving()
    }

    suspend fun submitLiveStreamReport(report: LiveStreamReport): Result<Boolean> {
        val result = firestoreChatService.submitStreamModerationReport(report)
        if (result.isSuccess) {
            notificationDao.insertNotification(
                AppNotification(
                    title = "تم استلام البلاغ الأمني 🛡️",
                    message = "تم إرسال بلاغك عن بث ${report.hostName} لقائمة المراجعة الإدارية.",
                    type = NotificationType.SYSTEM_ALERT
                )
            )
        }
        return result
    }

    suspend fun recordChatMessageEngagement() {
        val currentMissions = _dailyMissions.value
        _dailyMissions.value = currentMissions.map { mission ->
            if (mission.id == "mission_chat") {
                mission.copy(currentProgress = (mission.currentProgress + 1).coerceAtMost(mission.targetProgress))
            } else mission
        }
        val current = _engagementState.value
        _engagementState.value = current.copy(
            todayEngagementPoints = current.todayEngagementPoints + 2
        )
    }

    suspend fun recordMatchEngagement() {
        val current = _engagementState.value
        val newMatches = current.todayMatchesCount + 1
        _engagementState.value = current.copy(
            todayMatchesCount = newMatches,
            todayEngagementPoints = current.todayEngagementPoints + 5
        )
        _dailyMissions.value = _dailyMissions.value.map { mission ->
            if (mission.id == "mission_matches") {
                mission.copy(currentProgress = newMatches.coerceAtMost(mission.targetProgress))
            } else mission
        }
    }

    suspend fun recordStreamWatchEngagement(minutes: Int = 5) {
        val current = _engagementState.value
        val newMins = current.todayStreamMinutes + minutes
        _engagementState.value = current.copy(
            todayStreamMinutes = newMins,
            todayEngagementPoints = current.todayEngagementPoints + 10
        )
        _dailyMissions.value = _dailyMissions.value.map { mission ->
            if (mission.id == "mission_stream") {
                mission.copy(currentProgress = newMins.coerceAtMost(mission.targetProgress))
            } else mission
        }
    }

    suspend fun recordGiftEngagement() {
        val current = _engagementState.value
        val newGifts = current.todayGiftsSent + 1
        _engagementState.value = current.copy(
            todayGiftsSent = newGifts,
            todayEngagementPoints = current.todayEngagementPoints + 15
        )
        _dailyMissions.value = _dailyMissions.value.map { mission ->
            if (mission.id == "mission_gifts") {
                mission.copy(currentProgress = newGifts.coerceAtMost(mission.targetProgress))
            } else mission
        }
    }

    suspend fun claimDailyMissionReward(missionId: String): Boolean {
        if (!_featureFlags.value.missionRewardsEnabled) return false
        val currentMissions = _dailyMissions.value
        val target = currentMissions.find { it.id == missionId } ?: return false
        if (!target.isCompleted || target.isClaimed) return false

        val api = platformApi ?: return false
        val bearer = getBearerHeader() ?: return false
        val res = runCatching {
            api.claimMissionReward(bearer, MissionRewardRequestDto(missionId = missionId))
        }.getOrNull() ?: return false

        if (res.isSuccessful && res.body() != null) {
            applyAuthoritativeSummary(wallet = res.body()!!.wallet)
            refreshWalletFromBackend()
            _dailyMissions.value = currentMissions.map {
                if (it.id == missionId) it.copy(isClaimed = true) else it
            }
            val currentEng = _engagementState.value
            _engagementState.value = currentEng.copy(
                todayEngagementPoints = currentEng.todayEngagementPoints + res.body()!!.creditedCoins
            )
            return true
        }
        return false
    }

    suspend fun claimDailyMilestoneReward(): Boolean {
        if (!_featureFlags.value.missionRewardsEnabled) return false
        val current = _engagementState.value
        if (current.dailyMilestoneClaimed || current.todayEngagementPoints < 100) return false

        val api = platformApi ?: return false
        val bearer = getBearerHeader() ?: return false
        val res = runCatching {
            api.claimMissionReward(bearer, MissionRewardRequestDto(missionId = "daily_milestone"))
        }.getOrNull() ?: return false

        if (res.isSuccessful && res.body() != null) {
            applyAuthoritativeSummary(wallet = res.body()!!.wallet)
            refreshWalletFromBackend()
            _engagementState.value = current.copy(dailyMilestoneClaimed = true)
            return true
        }
        return false
    }

    suspend fun getCreatorsLeaderboard(
        scope: LeaderboardScope,
        timeframe: LeaderboardTimeframe,
        userAccumulatedPoints: Int
    ): List<CreatorLeaderboardItem> {
        val user = userDao.getUserProfileSync() ?: return emptyList()
        if (scope == LeaderboardScope.AGENCY && user.agencyName.isBlank()) {
            return emptyList()
        }
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
                pointsAccumulated = (userAccumulatedPoints * factor).toInt(),
                followersCount = 0,
                isLiveNow = false,
                isCurrentUser = true
            )
        )
    }

    suspend fun updateAccountSettings(
        name: String,
        bio: String,
        country: String,
        phoneNumber: String,
        avatarResId: Int = com.example.R.drawable.img_user_avatar,
        avatarCustomUri: String? = null
    ) {
        val cleanName = name.trim()
        val cleanBio = bio.trim()
        val cleanCountry = country.trim()
        userDao.updateAccountSettings(
            name = cleanName,
            bio = cleanBio,
            country = cleanCountry,
            phoneNumber = phoneNumber.trim(),
            avatarResId = avatarResId,
            avatarCustomUri = avatarCustomUri
        )
        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        if (!uid.isNullOrBlank()) {
            firestoreChatService.updateUserProfileFields(uid, cleanName, cleanBio, cleanCountry)
        }
    }

    suspend fun refreshWalletFromBackend(): Result<Unit> {
        val api = platformApi ?: return Result.failure(IllegalStateException("الخادم المالي غير مهيأ"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل دخول صالح"))
        return try {
            val res = api.getWalletSummary(bearer)
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                _featureFlags.value = body.featureFlags
                applyAuthoritativeSummary(body.user, body.wallet, body.transactions)
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر تحديث رصيد المحفظة")))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun verifyAndCreditPlayPurchase(
        productId: String,
        purchaseToken: String,
        orderId: String?
    ): Result<Int> {
        val api = platformApi ?: return Result.failure(IllegalStateException("لا يمكن شحن النقاط بدون الاتصال بخادم التحقق الرسمي."))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("يرجى تسجيل الدخول بحساب Google قبل الشراء."))
        return try {
            val res = api.verifyPlayPurchase(
                bearer,
                VerifyPlayPurchaseRequestDto(
                    packageName = context.packageName,
                    productId = productId,
                    purchaseToken = purchaseToken,
                    orderId = orderId
                )
            )
            if (res.isSuccessful && res.body() != null) {
                val credited = res.body()!!.creditedCoins
                refreshWalletFromBackend()
                Result.success(credited)
            } else {
                Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "فشل التحقق من عملية الشراء لدى الخادم.")))
            }
        } catch (t: Throwable) {
            Result.failure(IllegalStateException(t.localizedMessage ?: "تعذر الاتصال بخادم التحقق المالي."))
        }
    }

    suspend fun claimDailyRewardResult(): Result<Int> {
        val api = platformApi ?: return Result.failure(IllegalStateException("يتطلب استلام المكافأة اليومية اتصالاً بخادم الحسابات."))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("يرجى تسجيل الدخول أولاً."))
        return try {
            val res = api.claimDailyBonus(bearer)
            if (res.isSuccessful && res.body() != null) {
                val credited = res.body()!!.creditedCoins
                refreshWalletFromBackend()
                Result.success(credited)
            } else {
                Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تم استلام المكافأة اليومية بالفعل.")))
            }
        } catch (t: Throwable) {
            Result.failure(IllegalStateException(t.localizedMessage ?: "تعذر الاتصال بالخادم"))
        }
    }

    suspend fun spendPointsForMatch(cost: Int, filterDesc: String): Boolean {
        val api = platformApi
        val bearer = getBearerHeader()
        if (api != null && bearer != null) {
            val res = runCatching {
                api.spendCoins(
                    bearer,
                    SpendCoinsRequestDto(
                        amount = cost,
                        entryType = "MATCH_SPEND",
                        description = "مطابقة عشوائية ($filterDesc)",
                        idempotencyKey = "match_${UUID.randomUUID()}"
                    )
                )
            }.getOrNull() ?: return false
            if (res.isSuccessful && res.body() != null) {
                refreshWalletFromBackend()
                return true
            }
            return false
        }
        return false
    }

    suspend fun sendGift(gift: GiftItem, streamHost: String, recipientUid: String = "", streamId: String? = null): Boolean {
        val api = platformApi
        val bearer = getBearerHeader()
        if (api != null && bearer != null && recipientUid.isNotBlank()) {
            val res = runCatching {
                api.sendGift(
                    bearer,
                    SendGiftRequestDto(
                        recipientUid = recipientUid,
                        giftId = gift.id,
                        giftName = "${gift.name} ${gift.emoji}",
                        coinsCost = gift.pointsCost,
                        idempotencyKey = "gift_${UUID.randomUUID()}",
                        streamId = streamId
                    )
                )
            }.getOrNull() ?: return false
            if (res.isSuccessful && res.body() != null) {
                refreshWalletFromBackend()
                return true
            }
            return false
        }
        if (api != null && bearer != null) {
            val res = runCatching {
                api.spendCoins(
                    bearer,
                    SpendCoinsRequestDto(
                        amount = gift.pointsCost,
                        entryType = "GIFT_SENT",
                        description = "إرسال هدية ${gift.name} ${gift.emoji} إلى $streamHost",
                        idempotencyKey = "gift_${UUID.randomUUID()}",
                        referenceId = streamId
                    )
                )
            }.getOrNull() ?: return false
            if (res.isSuccessful) {
                refreshWalletFromBackend()
                return true
            }
            return false
        }
        return false
    }

    suspend fun adjustPoints(delta: Int) {
        if (delta >= 0) return // Zero unverified local point minting allowed
        val user = userDao.getUserProfileSync() ?: return
        if (user.pointsBalance + delta >= 0) {
            userDao.adjustPoints(delta)
        }
    }

    suspend fun addTransaction(title: String, type: String, amount: Int) {
        transactionDao.insertTransaction(
            PointTransaction(
                type = type,
                amount = amount,
                description = title
            )
        )
    }

    suspend fun applyForHost(agencyName: String): Result<Unit> {
        if (!_featureFlags.value.agenciesEnabled) {
            return Result.failure(IllegalStateException("ميزة الوكالات وطلبات المضيفين متوقفة مؤقتاً أثناء المراجعة."))
        }
        val api = platformApi ?: return Result.failure(IllegalStateException("يتطلب التقديم كمضيف اتصالاً بالخادم الرسمي."))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول أولاً."))
        return try {
            val res = api.applyForHost(bearer, ApplyHostRequestDto(agencyName = agencyName.trim()))
            if (res.isSuccessful && res.body() != null) {
                userDao.updateHostStatus("PENDING", isHost = false)
                refreshWalletFromBackend()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر إرسال طلب الانضمام كمضيف")))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun unlockPaidMedia(friendId: String, messageId: String, priceCoins: Int): Result<Unit> {
        if (!_featureFlags.value.paidMediaEnabled) {
            return Result.failure(IllegalStateException("ميزة فتح المحتوى المدفوع متوقفة مؤقتاً أثناء المراجعة."))
        }
        val api = platformApi ?: return Result.failure(IllegalStateException("يتطلب فتح المحتوى المدفوع الاتصال بالخادم المالي."))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول أولاً."))
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: userDao.getUserProfileSync()?.uid.orEmpty()
        return try {
            val res = api.sendGift(
                bearer,
                SendGiftRequestDto(
                    recipientUid = friendId,
                    giftId = "media_$messageId",
                    giftName = "فتح محتوى خاص 🔓",
                    coinsCost = priceCoins,
                    idempotencyKey = "unlock_${messageId}_${myUid}",
                    streamId = messageId
                )
            )
            if (res.isSuccessful && res.body() != null) {
                refreshWalletFromBackend()
                val unlockRes = firestoreChatService.markPrivateMediaUnlocked(friendId, messageId)
                if (unlockRes.isFailure) {
                    Result.failure(unlockRes.exceptionOrNull() ?: IllegalStateException("تم الدفع لكن تعذر تحديث حالة الرسالة في Firestore"))
                } else {
                    Result.success(Unit)
                }
            } else {
                Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر إتمام عملية الدفع لفتح المحتوى.")))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun refreshAgencyDashboard(): Result<Unit> {
        if (!_featureFlags.value.agenciesEnabled) {
            return Result.failure(IllegalStateException("لوحة الوكالات متوقفة مؤقتاً أثناء المراجعة."))
        }
        val api = platformApi ?: return Result.failure(IllegalStateException("خادم الوكالات غير متصل"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.getAgencyDashboard(bearer)
            if (res.isSuccessful && res.body() != null) {
                val dash = res.body()!!
                applyAuthoritativeSummary(dash.user, dash.wallet)
                _subAgents.value = dash.subAgents.map {
                    SubAgentItem(
                        id = it.id,
                        name = it.name,
                        country = it.country,
                        hostsCount = it.hostsCount,
                        monthlyRevenueCoins = it.monthlyRevenueCoins,
                        agentCommissionEarned = it.agentCommissionEarned,
                        status = it.status
                    )
                }
                _myPayouts.value = dash.payouts
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر تحميل بيانات الوكالة")))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun recruitSubAgentResult(name: String, country: String): Result<AppNotification> {
        if (!_featureFlags.value.agenciesEnabled) {
            return Result.failure(IllegalStateException("ميزة تسجيل الوكلاء الفرعيين متوقفة مؤقتاً أثناء المراجعة."))
        }
        val api = platformApi ?: return Result.failure(IllegalStateException("خادم الوكالات غير متصل"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.recruitSubAgent(
                bearer,
                RecruitSubAgentRequestDto(subAgentName = name.trim(), country = country.trim())
            )
            if (res.isSuccessful && res.body() != null) {
                refreshAgencyDashboard()
                val notif = AppNotification(
                    title = "إضافة وكيل فرعي للشبكة 👥",
                    message = "تم تسجيل الوكيل '${name.trim()}' تحت وكالتك في السجل الرسمي.",
                    type = NotificationType.NEW_REFERRAL,
                    senderName = name.trim()
                )
                val id = notificationDao.insertNotification(notif)
                Result.success(notif.copy(id = id))
            } else {
                Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر إضافة الوكيل الفرعي")))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun requestBankPayout(
        amountCoins: Int,
        payoutMethod: String,
        bankAccountHolder: String,
        bankName: String,
        ibanOrAccount: String
    ): Result<PayoutRequestDto> {
        if (!_featureFlags.value.payoutsEnabled) {
            return Result.failure(IllegalStateException("طلبات السحب المالي متوقفة مؤقتاً أثناء المراجعة."))
        }
        val api = platformApi ?: return Result.failure(IllegalStateException("خدمة سحب الأرباح تتطلب الاتصال بالخادم المالي"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.createPayoutRequest(
                bearer,
                CreatePayoutRequestDto(
                    amountCoins = amountCoins,
                    payoutMethod = payoutMethod,
                    bankAccountHolder = bankAccountHolder.trim(),
                    bankName = bankName.trim(),
                    ibanOrAccount = ibanOrAccount.trim()
                )
            )
            if (res.isSuccessful && res.body() != null) {
                val created = res.body()!!
                applyAuthoritativeSummary(wallet = created.wallet)
                refreshAgencyDashboard()
                Result.success(created.request)
            } else {
                Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر تقديم طلب السحب")))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // Admin Endpoints
    suspend fun fetchAdminAgeVerifications(status: String = "PENDING_REVIEW"): Result<List<AdminAgeVerificationItemDto>> {
        val api = platformApi ?: return Result.failure(IllegalStateException("خادم الإدارة غير مهيأ"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.listAdminAgeVerifications(bearer, status)
            if (res.isSuccessful && res.body() != null) Result.success(res.body()!!.submissions)
            else Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر جلب طلبات +18")))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun reviewAdminAgeVerification(targetUid: String, decision: String, reason: String? = null): Result<Unit> {
        val api = platformApi ?: return Result.failure(IllegalStateException("خادم الإدارة غير مهيأ"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.reviewAdminAgeVerification(bearer, targetUid, ReviewAgeRequestDto(decision, reason))
            if (res.isSuccessful) Result.success(Unit)
            else Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر مراجعة طلب +18")))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun fetchAdminPayouts(status: String? = null): Result<List<PayoutRequestDto>> {
        val api = platformApi ?: return Result.failure(IllegalStateException("خادم الإدارة غير مهيأ"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.listAdminPayouts(bearer, status)
            if (res.isSuccessful && res.body() != null) Result.success(res.body()!!.payouts)
            else Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر جلب طلبات السحب")))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun reviewAdminPayout(requestId: String, decision: String, note: String? = null, paymentReference: String? = null): Result<Unit> {
        val api = platformApi ?: return Result.failure(IllegalStateException("خادم الإدارة غير مهيأ"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.reviewAdminPayout(bearer, requestId, ReviewPayoutRequestDto(decision, note, paymentReference))
            if (res.isSuccessful) {
                refreshWalletFromBackend()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر تحديث حالة طلب السحب")))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun updateUserRolesAdmin(
        targetUid: String,
        isAgent: Boolean? = null,
        isHost: Boolean? = null,
        agentCommissionPercent: Int? = null,
        hostCommissionPercent: Int? = null,
        agencyName: String? = null
    ): Result<BackendUserDto> {
        val api = platformApi ?: return Result.failure(IllegalStateException("خادم الإدارة غير مهيأ"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.updateUserRolesAdmin(
                bearer,
                targetUid,
                AdminUpdateRolesRequestDto(isAgent, isHost, null, agentCommissionPercent, hostCommissionPercent, agencyName)
            )
            if (res.isSuccessful && res.body() != null) {
                refreshWalletFromBackend()
                Result.success(res.body()!!.user)
            } else {
                Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر تحديث صلاحيات المستخدم")))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun fetchAdminReports(status: String = "PENDING_REVIEW"): Result<List<ModerationReportDto>> {
        val api = platformApi ?: return Result.failure(IllegalStateException("خادم الإدارة غير مهيأ"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.listAdminReports(bearer, status)
            if (res.isSuccessful && res.body() != null) Result.success(res.body()!!.reports)
            else Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر جلب البلاغات")))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun resolveAdminReport(reportId: String, status: String, actionTaken: String): Result<Unit> {
        val api = platformApi ?: return Result.failure(IllegalStateException("خادم الإدارة غير مهيأ"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.resolveAdminReport(bearer, reportId, ResolveReportRequestDto(status, actionTaken))
            if (res.isSuccessful) Result.success(Unit)
            else Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر البت في البلاغ")))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun blockUser(blockedUid: String): Result<Set<String>> {
        firestoreChatService.setUserBlocked(blockedUid, blocked = true)
        val api = platformApi
        val bearer = getBearerHeader()
        if (api != null && bearer != null) {
            val res = runCatching { api.blockUser(bearer, BlockUserRequestDto(blockedUid)) }.getOrNull()
            if (res?.isSuccessful == true && res.body() != null) {
                val updated = res.body()!!.blockedUsers.toSet()
                _blockedUserIds.value = updated
                return Result.success(updated)
            }
        }
        val next = _blockedUserIds.value + blockedUid
        _blockedUserIds.value = next
        return Result.success(next)
    }

    suspend fun unblockUser(blockedUid: String): Result<Set<String>> {
        firestoreChatService.setUserBlocked(blockedUid, blocked = false)
        val api = platformApi
        val bearer = getBearerHeader()
        if (api != null && bearer != null) {
            val res = runCatching { api.unblockUser(bearer, blockedUid) }.getOrNull()
            if (res?.isSuccessful == true && res.body() != null) {
                val updated = res.body()!!.blockedUsers.toSet()
                _blockedUserIds.value = updated
                return Result.success(updated)
            }
        }
        val next = _blockedUserIds.value - blockedUid
        _blockedUserIds.value = next
        return Result.success(next)
    }

    suspend fun deleteAccountPermanently(): Result<Unit> {
        val api = platformApi
        val bearer = getBearerHeader()
        if (api != null && bearer != null) {
            val res = runCatching { api.deleteAccount(bearer) }.getOrNull()
            if (res?.isSuccessful != true) {
                return Result.failure(IllegalStateException(res?.let { BackendPlatformApi.extractErrorMessage(it, "تعذر حذف الحساب من الخادم") } ?: "تعذر الاتصال بالخادم"))
            }
        }
        val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        if (currentUser != null) {
            firestoreChatService.deleteCurrentUserAccountData(currentUser.uid)
            try {
                currentUser.delete().await()
            } catch (_: FirebaseAuthRecentLoginRequiredException) {
                return Result.failure(
                    IllegalStateException("تتطلب سياسة الأمان إعادة تسجيل الدخول قبل حذف حساب المصادقة نهائياً.")
                )
            } catch (e: Exception) {
                return Result.failure(
                    IllegalStateException(e.localizedMessage ?: "تعذر حذف حساب المصادقة في Firebase.")
                )
            }
        }
        clearLocalSessionData()
        return Result.success(Unit)
    }

    suspend fun markNotificationAsRead(id: Long) = notificationDao.markAsRead(id)
    suspend fun markAllNotificationsAsRead() = notificationDao.markAllAsRead()
    suspend fun clearAllNotifications() = notificationDao.clearAll()
    suspend fun insertNotification(notification: AppNotification) = notificationDao.insertNotification(notification)

    fun getSubAgents(): List<SubAgentItem> = _subAgents.value

    fun getAvailableCoinPackages(): List<CoinPackage> {
        return listOf(
            CoinPackage("pkg_100", coins = 100, bonusCoins = 10, priceUsd = 0.99, productId = "ayer_coins_100"),
            CoinPackage("pkg_550", coins = 550, bonusCoins = 65, priceUsd = 4.99, isPopular = true, tag = "الأكثر طلباً", productId = "ayer_coins_550"),
            CoinPackage("pkg_1200", coins = 1200, bonusCoins = 200, priceUsd = 9.99, tag = "توفير 20%", productId = "ayer_coins_1200"),
            CoinPackage("pkg_3000", coins = 3000, bonusCoins = 600, priceUsd = 24.99, tag = "باقة التميز", productId = "ayer_coins_3000"),
            CoinPackage("pkg_7500", coins = 7500, bonusCoins = 1800, priceUsd = 59.99, tag = "باقة كبار الشخصيات VIP", productId = "ayer_coins_7500")
        )
    }

    fun getAvailableGifts(): List<GiftItem> {
        return listOf(
            GiftItem("gift_rose", "وردة حمراء", 5, "🌹", "تأثير الورود المتطايرة"),
            GiftItem("gift_heart", "قلب نيون", 15, "💖", "نبضات قلب مضيئة"),
            GiftItem("gift_crown", "تاج الملوك", 50, "👑", "تتويج المضيف بالتاج الذهبي"),
            GiftItem("gift_diamond", "ماسة زرقاء", 100, "💎", "بريق الألماس الملكي"),
            GiftItem("gift_car", "سيارة رياضية", 300, "🏎️", "مرور سيارة نفاثة بالشاشة"),
            GiftItem("gift_rocket", "صاروخ الفضاء", 600, "🚀", "انطلاق صاروخ مع ألعاب نارية")
        )
    }

    suspend fun submitUserProfileReport(report: UserProfileReport): Result<Boolean> {
        val api = platformApi
        val bearer = getBearerHeader()
        if (api != null && bearer != null) {
            runCatching {
                api.createReport(
                    bearer,
                    CreateReportRequestDto(
                        reportType = "USER_PROFILE",
                        targetUserId = report.targetUserId,
                        reason = report.reason,
                        details = report.details
                    )
                )
            }
        }
        return firestoreChatService.submitUserProfileReport(report)
    }

    // ==========================================================
    // Phase D: LiveKit Broadcast Rooms & Matchmaking Queue
    // ==========================================================

    fun LiveStreamDto.toRoomModel(): LiveStreamRoom {
        return LiveStreamRoom(
            id = id,
            title = title,
            hostName = hostName,
            hostCountry = hostCountry.ifBlank { "العالم 🌐" },
            hostCountryCode = hostCountryCode.ifBlank { "SA" },
            viewersCount = viewersCount,
            category = category.ifBlank { "تعارف وسوالف" },
            isVip = isVip,
            diamondsReceived = diamondsReceived,
            agencyName = agencyName,
            isAgencyStream = isAgencyStream,
            startedAt = if (startedAtMs > 0L) startedAtMs else System.currentTimeMillis(),
            hostUid = hostUid,
            roomName = roomName
        )
    }

    suspend fun fetchLiveStreams(category: String? = null): Result<List<LiveStreamRoom>> {
        val api = platformApi ?: return Result.success(emptyList())
        val bearer = getBearerHeader() ?: return Result.success(emptyList())
        return try {
            val res = api.listLiveStreams(bearer, category)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!.streams.map { it.toRoomModel() })
            } else {
                Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر جلب البثوث المباشرة")))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun startLiveBroadcast(
        title: String,
        category: String,
        hostName: String,
        hostCountry: String,
        hostCountryCode: String
    ): Result<StreamSessionResponseDto> {
        val api = platformApi ?: return Result.failure(IllegalStateException("خادم البث غير مهيأ"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.startLiveStream(
                bearer,
                StartStreamRequestDto(
                    title = title,
                    category = category,
                    hostName = hostName,
                    hostCountry = hostCountry,
                    hostCountryCode = hostCountryCode
                )
            )
            if (res.isSuccessful && res.body() != null) Result.success(res.body()!!)
            else Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر بدء البث المباشر")))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun joinLiveBroadcast(streamId: String): Result<StreamSessionResponseDto> {
        val api = platformApi ?: return Result.failure(IllegalStateException("خادم البث غير مهيأ"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.joinLiveStream(bearer, streamId)
            if (res.isSuccessful && res.body() != null) Result.success(res.body()!!)
            else Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر الانضمام للبث")))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun leaveLiveBroadcast(streamId: String) {
        val api = platformApi ?: return
        val bearer = getBearerHeader() ?: return
        runCatching { api.leaveLiveStream(bearer, streamId) }
    }

    suspend fun endLiveBroadcast(streamId: String) {
        val api = platformApi ?: return
        val bearer = getBearerHeader() ?: return
        runCatching { api.endLiveStream(bearer, streamId) }
    }

    suspend fun joinMatchQueue(
        displayName: String,
        country: String,
        countryCode: String,
        userGender: String,
        bio: String,
        chatType: String,
        genderFilter: String,
        countryFilter: String
    ): Result<MatchJoinPollResponseDto> {
        val api = platformApi ?: return Result.failure(IllegalStateException("خادم المطابقة غير مهيأ"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.joinMatchQueue(
                bearer,
                JoinMatchRequestDto(
                    displayName = displayName,
                    country = country,
                    countryCode = countryCode,
                    userGender = userGender,
                    bio = bio,
                    chatType = chatType,
                    genderFilter = genderFilter,
                    countryFilter = countryFilter
                )
            )
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                if (body.wallet != null) {
                    applyAuthoritativeSummary(wallet = body.wallet)
                    refreshWalletFromBackend()
                }
                Result.success(body)
            } else {
                Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر الانضمام لطابور المطابقة")))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun pollMatchQueue(): Result<MatchJoinPollResponseDto> {
        val api = platformApi ?: return Result.failure(IllegalStateException("خادم المطابقة غير مهيأ"))
        val bearer = getBearerHeader() ?: return Result.failure(IllegalStateException("مطلوب تسجيل الدخول"))
        return try {
            val res = api.pollMatchQueue(bearer)
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                if (body.wallet != null) {
                    applyAuthoritativeSummary(wallet = body.wallet)
                    refreshWalletFromBackend()
                }
                Result.success(body)
            } else {
                Result.failure(IllegalStateException(BackendPlatformApi.extractErrorMessage(res, "تعذر متابعة طابور المطابقة")))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun leaveMatchQueue(matchId: String? = null) {
        val api = platformApi ?: return
        val bearer = getBearerHeader() ?: return
        runCatching { api.leaveMatchQueue(bearer, LeaveMatchRequestDto(matchId)) }
    }

    // ==========================================================
    // Phase E: Firestore Friends, Privacy & Private Chat
    // ==========================================================

    fun observeFirestoreBlockedUsers(uid: String): Flow<Set<String>> =
        firestoreChatService.observeBlockedUsers(uid)

    fun observeFriends(uid: String): Flow<List<com.example.data.model.FriendItem>> =
        firestoreChatService.observeFriends(uid)

    fun observePrivacySettings(uid: String): Flow<FirestoreChatService.PrivacySettingsSnapshot?> =
        firestoreChatService.observePrivacySettings(uid)

    suspend fun updatePrivacySettings(
        uid: String,
        profileVisibility: String,
        directMessagePermission: String,
        showOnlineStatus: Boolean,
        hideLocationInMatch: Boolean
    ): Result<Unit> = firestoreChatService.updatePrivacySettings(
        uid = uid,
        profileVisibility = profileVisibility,
        directMessagePermission = directMessagePermission,
        showOnlineStatus = showOnlineStatus,
        hideLocationInMatch = hideLocationInMatch
    )

    suspend fun sendFriendRequest(
        targetUid: String,
        targetName: String,
        targetCountry: String,
        myName: String,
        myCountry: String
    ): Result<Unit> = firestoreChatService.sendFriendRequest(
        targetUid = targetUid,
        targetName = targetName,
        targetCountry = targetCountry,
        myName = myName,
        myCountry = myCountry
    )

    suspend fun acceptFriendRequest(
        targetUid: String,
        myName: String,
        myCountry: String
    ): Result<Unit> = firestoreChatService.acceptFriendRequest(targetUid, myName, myCountry)

    suspend fun rejectOrUnfriend(targetUid: String): Result<Unit> =
        firestoreChatService.rejectOrUnfriend(targetUid)

    fun observePrivateChatMessages(
        myUid: String,
        friendUid: String
    ): Flow<List<com.example.data.model.PrivateChatMessage>> =
        firestoreChatService.observePrivateChatMessages(myUid, friendUid)

    suspend fun canSendDirectMessage(
        targetUid: String,
        isAcceptedFriend: Boolean,
        isSenderVerified: Boolean
    ): Result<Unit> = firestoreChatService.canSendDirectMessage(
        targetUid = targetUid,
        isAcceptedFriend = isAcceptedFriend,
        isSenderVerified = isSenderVerified
    )

    suspend fun sendPrivateMessage(
        friendUid: String,
        message: com.example.data.model.PrivateChatMessage,
        summaryPreview: String
    ): Result<com.example.data.model.PrivateChatMessage> =
        firestoreChatService.sendPrivateMessage(friendUid, message, summaryPreview)

    suspend fun markPrivateMediaUnlocked(friendUid: String, messageId: String): Result<Unit> =
        firestoreChatService.markPrivateMediaUnlocked(friendUid, messageId)

    suspend fun updatePrivateMessageTranslation(friendUid: String, messageId: String, translatedText: String) =
        firestoreChatService.updatePrivateMessageTranslation(friendUid, messageId, translatedText)

    suspend fun clearOwnPrivateChatMessages(friendUid: String) =
        firestoreChatService.clearOwnPrivateChatMessages(friendUid)
}
