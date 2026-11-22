package com.example.data.repository

import com.example.data.local.NotificationDao
import com.example.data.local.SecondaryProfileDao
import com.example.data.local.TransactionDao
import com.example.data.local.UserDao
import com.example.data.model.AppNotification
import com.example.data.model.CoinPackage
import com.example.data.model.CreatorLeaderboardItem
import com.example.data.model.DailyMissionItem
import com.example.data.model.FirestoreRandomChatMessage
import com.example.data.model.GiftItem
import com.example.data.model.LeaderboardScope
import com.example.data.model.LeaderboardTimeframe
import com.example.data.model.LiveStreamReport
import com.example.data.model.LiveStreamRoom
import com.example.data.model.MatchCandidate
import com.example.data.model.NotificationType
import com.example.data.model.PointTransaction
import com.example.data.model.SecondaryProfile
import com.example.data.model.UserEngagementState
import com.example.data.model.UserProfile
import com.example.data.remote.FirestoreChatService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull

class AyerRepository(
    private val userDao: UserDao,
    private val transactionDao: TransactionDao,
    private val notificationDao: NotificationDao,
    private val secondaryProfileDao: SecondaryProfileDao
) {
    private val firestoreChatService = FirestoreChatService()

    val userProfile: Flow<UserProfile?> = userDao.getUserProfile()
    val transactions: Flow<List<PointTransaction>> = transactionDao.getAllTransactions()
    val notifications: Flow<List<AppNotification>> = notificationDao.getAllNotifications()
    val unreadNotificationCount: Flow<Int> = notificationDao.getUnreadCount()

    // Points Engagement Management System State
    private val _engagementState = MutableStateFlow(UserEngagementState())
    val engagementState = _engagementState.asStateFlow()

    // Daily Missions System
    private val _dailyMissions = MutableStateFlow(
        listOf(
            DailyMissionItem(
                id = "mission_stream",
                title = "مشاهدة البث المباشر",
                description = "شاهد البثوث المباشرة لمدة 30 دقيقة اليوم",
                iconName = "live",
                currentProgress = 18,
                targetProgress = 30,
                rewardPoints = 35,
                unit = "دقيقة"
            ),
            DailyMissionItem(
                id = "mission_chat",
                title = "إرسال رسائل في الدردشة",
                description = "تفاعل وأرسل 10 رسائل في غرف المطابقة والمحادثات",
                iconName = "chat",
                currentProgress = 6,
                targetProgress = 10,
                rewardPoints = 20,
                unit = "رسائل"
            ),
            DailyMissionItem(
                id = "mission_matches",
                title = "إجراء مطابقات عشوائية",
                description = "أكمل 5 مطابقات جديدة وتواصل مع أعضاء حول العالم",
                iconName = "match",
                currentProgress = 4,
                targetProgress = 5,
                rewardPoints = 25,
                unit = "مطابقة"
            ),
            DailyMissionItem(
                id = "mission_gifts",
                title = "إرسال هدايا تشجيعية",
                description = "ادعم المضيفين المفضلين بإرسال 3 هدايا في البث",
                iconName = "gift",
                currentProgress = 2,
                targetProgress = 3,
                rewardPoints = 30,
                unit = "هدايا"
            ),
            DailyMissionItem(
                id = "mission_daily_login",
                title = "تسجيل الحضور اليومي",
                description = "حافظ على سلسلة التواجد اليومي النشط في التطبيق",
                iconName = "login",
                currentProgress = 1,
                targetProgress = 1,
                rewardPoints = 15,
                unit = "يوم"
            )
        )
    )
    val dailyMissions = _dailyMissions.asStateFlow()

    // Moderation & Stream Flags
    val flaggedStreamIds = firestoreChatService.flaggedStreamIds
    val submittedReports = firestoreChatService.submittedReports

    // Firestore Real-Time Random Chat Integration
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
        notificationDao.insertNotification(
            AppNotification(
                title = "تم استلام البلاغ الأمني 🛡️",
                message = "تم إرسال بلاغك عن بث ${report.hostName} ووضع علامة مراجعة أمنية فورية على البث في سيرفر Firestore.",
                type = NotificationType.SYSTEM_ALERT
            )
        )
        return result
    }

    // Engagement & Daily Missions Tracking System
    suspend fun recordChatMessageEngagement() {
        // Increment chat mission
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
        val bonus = if (newMatches == current.targetMatchesCount) 20 else 0
        _engagementState.value = current.copy(
            todayMatchesCount = newMatches,
            todayEngagementPoints = current.todayEngagementPoints + bonus + 5
        )
        // Update mission
        _dailyMissions.value = _dailyMissions.value.map { mission ->
            if (mission.id == "mission_matches") {
                mission.copy(currentProgress = newMatches.coerceAtMost(mission.targetProgress))
            } else mission
        }
        if (bonus > 0) {
            userDao.adjustPoints(bonus)
            transactionDao.insertTransaction(
                PointTransaction(
                    type = "ENGAGEMENT_REWARD",
                    amount = bonus,
                    description = "مكافأة إنجاز مهمة المطابقات اليومية (+${bonus} نقطة)"
                )
            )
        }
    }

    suspend fun recordStreamWatchEngagement(minutes: Int = 5) {
        val current = _engagementState.value
        val newMins = current.todayStreamMinutes + minutes
        val bonus = if (newMins >= current.targetStreamMinutes && current.todayStreamMinutes < current.targetStreamMinutes) 30 else 0
        _engagementState.value = current.copy(
            todayStreamMinutes = newMins,
            todayEngagementPoints = current.todayEngagementPoints + bonus + 10
        )
        // Update mission
        _dailyMissions.value = _dailyMissions.value.map { mission ->
            if (mission.id == "mission_stream") {
                mission.copy(currentProgress = newMins.coerceAtMost(mission.targetProgress))
            } else mission
        }
        if (bonus > 0) {
            userDao.adjustPoints(bonus)
            transactionDao.insertTransaction(
                PointTransaction(
                    type = "ENGAGEMENT_REWARD",
                    amount = bonus,
                    description = "مكافأة إنجاز مشاهدة البثوث اليومية (+${bonus} نقطة)"
                )
            )
        }
    }

    suspend fun recordGiftEngagement() {
        val current = _engagementState.value
        val newGifts = current.todayGiftsSent + 1
        val bonus = if (newGifts == current.targetGiftsSent) 25 else 0
        _engagementState.value = current.copy(
            todayGiftsSent = newGifts,
            todayEngagementPoints = current.todayEngagementPoints + bonus + 15
        )
        // Update mission
        _dailyMissions.value = _dailyMissions.value.map { mission ->
            if (mission.id == "mission_gifts") {
                mission.copy(currentProgress = newGifts.coerceAtMost(mission.targetProgress))
            } else mission
        }
        if (bonus > 0) {
            userDao.adjustPoints(bonus)
            transactionDao.insertTransaction(
                PointTransaction(
                    type = "ENGAGEMENT_REWARD",
                    amount = bonus,
                    description = "مكافأة إنجاز تفاعل إرسال الهدايا (+${bonus} نقطة)"
                )
            )
        }
    }

    suspend fun claimDailyMissionReward(missionId: String): Boolean {
        val currentMissions = _dailyMissions.value
        val target = currentMissions.find { it.id == missionId } ?: return false
        if (target.isCompleted && !target.isClaimed) {
            userDao.adjustPoints(target.rewardPoints)
            transactionDao.insertTransaction(
                PointTransaction(
                    type = "MISSION_REWARD",
                    amount = target.rewardPoints,
                    description = "مكافأة إتمام مهمة: ${target.title} (+${target.rewardPoints} نقطة)"
                )
            )
            _dailyMissions.value = currentMissions.map {
                if (it.id == missionId) it.copy(isClaimed = true) else it
            }
            val currentEng = _engagementState.value
            _engagementState.value = currentEng.copy(
                todayEngagementPoints = currentEng.todayEngagementPoints + target.rewardPoints
            )
            return true
        }
        return false
    }

    suspend fun claimDailyMilestoneReward(): Boolean {
        val current = _engagementState.value
        if (!current.dailyMilestoneClaimed && current.todayEngagementPoints >= 100) {
            val reward = 50
            userDao.adjustPoints(reward)
            transactionDao.insertTransaction(
                PointTransaction(
                    type = "ENGAGEMENT_REWARD",
                    amount = reward,
                    description = "مكافأة تفوق التفاعل اليومي الكامل (+50 نقطة)"
                )
            )
            _engagementState.value = current.copy(dailyMilestoneClaimed = true)
            return true
        }
        return false
    }

    fun getCreatorsLeaderboard(
        scope: LeaderboardScope,
        timeframe: LeaderboardTimeframe,
        userAccumulatedPoints: Int
    ): List<CreatorLeaderboardItem> {
        val factor = when (timeframe) {
            LeaderboardTimeframe.TODAY -> 0.08f
            LeaderboardTimeframe.THIS_WEEK -> 0.35f
            LeaderboardTimeframe.ALL_TIME -> 1.0f
        }

        val allCreators = listOf(
            CreatorLeaderboardItem(
                rank = 1,
                id = "cr_1",
                name = "سارة القحطاني 🇸🇦",
                country = "المملكة العربية السعودية",
                countryCode = "SA",
                agencyName = "الوكالة الماسية - Diamond Agency",
                pointsAccumulated = (185400 * factor).toInt(),
                followersCount = 89200,
                isLiveNow = true
            ),
            CreatorLeaderboardItem(
                rank = 2,
                id = "cr_2",
                name = "Elena Rostova 🇷🇺",
                country = "روسيا",
                countryCode = "RU",
                agencyName = "Global Talent Agency",
                pointsAccumulated = (162300 * factor).toInt(),
                followersCount = 74100,
                isLiveNow = true
            ),
            CreatorLeaderboardItem(
                rank = 3,
                id = "cr_3",
                name = "كريم المصري 🇪🇬",
                country = "مصر",
                countryCode = "EG",
                agencyName = "وكالة النيل لايف",
                pointsAccumulated = (138700 * factor).toInt(),
                followersCount = 58900,
                isLiveNow = false
            ),
            CreatorLeaderboardItem(
                rank = 4,
                id = "cr_4",
                name = "نور الهدى 🇱🇧",
                country = "لبنان",
                countryCode = "LB",
                agencyName = "الوكالة الماسية - Diamond Agency",
                pointsAccumulated = (119500 * factor).toInt(),
                followersCount = 42300,
                isLiveNow = true
            ),
            CreatorLeaderboardItem(
                rank = 5,
                id = "cr_5",
                name = "Yuki Tanaka 🇯🇵",
                country = "اليابان",
                countryCode = "JP",
                agencyName = "Tokyo Stars Agency",
                pointsAccumulated = (98200 * factor).toInt(),
                followersCount = 38400,
                isLiveNow = false
            ),
            CreatorLeaderboardItem(
                rank = 6,
                id = "cr_6",
                name = "مريم التونسية 🇹🇳",
                country = "تونس",
                countryCode = "TN",
                agencyName = "الوكالة الماسية - Diamond Agency",
                pointsAccumulated = (84100 * factor).toInt(),
                followersCount = 29500,
                isLiveNow = true
            ),
            CreatorLeaderboardItem(
                rank = 7,
                id = "cr_7",
                name = "Carlos Mendoza 🇲🇽",
                country = "المكسيك",
                countryCode = "MX",
                agencyName = "Fiesta Live Agency",
                pointsAccumulated = (71300 * factor).toInt(),
                followersCount = 24800,
                isLiveNow = false
            ),
            CreatorLeaderboardItem(
                rank = 8,
                id = "cr_8",
                name = "طارق الزهراني 🇸🇦",
                country = "المملكة العربية السعودية",
                countryCode = "SA",
                agencyName = "الوكالة الماسية - Diamond Agency",
                pointsAccumulated = (58900 * factor).toInt(),
                followersCount = 19600,
                isLiveNow = false
            ),
            CreatorLeaderboardItem(
                rank = 9,
                id = "cr_9",
                name = "Amira Dubai 🇦🇪",
                country = "الإمارات",
                countryCode = "AE",
                agencyName = "Gulf Stars Agency",
                pointsAccumulated = (46200 * factor).toInt(),
                followersCount = 15300,
                isLiveNow = true
            ),
            CreatorLeaderboardItem(
                rank = 10,
                id = "cr_user",
                name = "سامر (أنت)",
                country = "المملكة العربية السعودية",
                countryCode = "SA",
                agencyName = "الوكالة الماسية - Diamond Agency",
                pointsAccumulated = (userAccumulatedPoints * factor).toInt(),
                followersCount = 4850,
                isLiveNow = false,
                isCurrentUser = true
            )
        )

        val filtered = if (scope == LeaderboardScope.AGENCY) {
            allCreators.filter { it.agencyName.contains("الوكالة الماسية") || it.isCurrentUser }
        } else {
            allCreators
        }

        // Re-sort and re-rank
        return filtered.sortedByDescending { it.pointsAccumulated }
            .mapIndexed { index, creator ->
                creator.copy(rank = index + 1)
            }
    }

    suspend fun updateAccountSettings(
        name: String,
        bio: String,
        country: String,
        phoneNumber: String,
        avatarResId: Int = com.example.R.drawable.img_user_avatar,
        avatarCustomUri: String? = null
    ) {
        userDao.updateAccountSettings(
            name = name.trim(),
            bio = bio.trim(),
            country = country.trim(),
            phoneNumber = phoneNumber.trim(),
            avatarResId = avatarResId,
            avatarCustomUri = avatarCustomUri
        )
    }

    fun getSecondaryProfiles(parentUserId: Long = 1L): Flow<List<SecondaryProfile>> =
        secondaryProfileDao.getProfilesForParent(parentUserId)

    suspend fun createSecondaryProfile(
        username: String,
        password: String,
        displayName: String,
        bio: String = "بروفايل خاص محمي برمز سري",
        notificationsEnabled: Boolean = false,
        avatarResId: Int? = null,
        parentUserId: Long = 1L
    ): Result<SecondaryProfile> {
        val cleanUsername = username.trim().lowercase()
        if (cleanUsername.length < 3) {
            return Result.failure(Exception("اسم المستخدم يجب أن يتكون من 3 أحرف على الأقل"))
        }
        if (password.length < 4) {
            return Result.failure(Exception("كلمة المرور السرية يجب ألا تقل عن 4 خانات"))
        }
        val existing = secondaryProfileDao.findByUsername(cleanUsername)
        if (existing != null) {
            return Result.failure(Exception("اسم المستخدم مستخدم بالفعل، يرجى اختيار اسم آخر"))
        }

        val parentUser = userDao.getUserProfile().firstOrNull() ?: UserProfile()

        val profile = SecondaryProfile(
            parentUserId = parentUserId,
            username = cleanUsername,
            passwordHash = password,
            displayName = displayName.ifBlank { "مستخدم Ayer الخاص" },
            bio = bio,
            notificationsEnabled = notificationsEnabled,
            avatarResId = avatarResId ?: com.example.R.drawable.img_live_model_3,
            pointsBalance = parentUser.pointsBalance
        )
        val id = secondaryProfileDao.insertProfile(profile)
        return Result.success(profile.copy(id = id))
    }

    suspend fun unlockSecondaryProfileByPassword(passwordAttempt: String, parentUserId: Long = 1L): SecondaryProfile? {
        val cleanPassword = passwordAttempt.trim()
        if (cleanPassword.isBlank()) return null
        val matched = secondaryProfileDao.findMatchingProfileByPassword(parentUserId, cleanPassword)
        if (matched != null) {
            secondaryProfileDao.updateLastAccessed(matched.id, System.currentTimeMillis())
        }
        return matched
    }

    suspend fun updateSecondaryProfileNotification(id: Long, enabled: Boolean) {
        secondaryProfileDao.updateNotificationSetting(id, enabled)
    }

    suspend fun deleteSecondaryProfile(id: Long) {
        secondaryProfileDao.deleteProfile(id)
    }

    suspend fun initializeDefaultUserIfNeeded() {
        val existing = userDao.getUserProfile().firstOrNull()
        if (existing == null) {
            userDao.insertUser(UserProfile())
            transactionDao.insertTransaction(
                PointTransaction(
                    type = "WELCOME_BONUS",
                    amount = 45,
                    description = "مكافأة الانضمام للتطبيق"
                )
            )

            // Initial influencer notifications
            notificationDao.insertNotification(
                AppNotification(
                    title = "مرحباً بك في نظام شركاء Ayer 🌟",
                    message = "تم تفعيل حسابك كإنفلونسر مؤهل. يمكنك ترقية حسابك إلى وكيل VIP وبدء جني العمولات من الإحالات ومضيفي البث.",
                    type = NotificationType.SYSTEM_ALERT,
                    timestamp = System.currentTimeMillis() - 86400000L,
                    isRead = true
                )
            )
            notificationDao.insertNotification(
                AppNotification(
                    title = "إحالة جديدة انضمت لشبكتك! 👥",
                    message = "انضم الوكيل 'عمر الفاروق' من السعودية عبر كود الإحالة الخاص بك. تم قيد +250 نقطة مكافأة انضمام في محفظتك 💎",
                    type = NotificationType.NEW_REFERRAL,
                    amountCoins = 250,
                    timestamp = System.currentTimeMillis() - 3600000L,
                    senderName = "عمر الفاروق",
                    isRead = false
                )
            )
            notificationDao.insertNotification(
                AppNotification(
                    title = "دفعة عمولة جديدة مستلمة! 💰",
                    message = "تم إيداع 560 نقطة (≈ $56.00) في محفظتك كعمولة أرباح من نشاط مضيفي 'كاريزما إيجنسي' 🌟",
                    type = NotificationType.COMMISSION_PAYOUT,
                    amountCoins = 560,
                    timestamp = System.currentTimeMillis() - 1800000L,
                    senderName = "كاريزما إيجنسي",
                    isRead = false
                )
            )
        }
    }

    suspend fun claimDailyReward(): Boolean {
        val user = userDao.getUserProfile().firstOrNull() ?: UserProfile()
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L
        if (now - user.lastDailyClaimTime >= oneDayMillis || user.lastDailyClaimTime == 0L) {
            val rewardCoins = 10
            userDao.claimDailyPoints(coins = rewardCoins, claimTime = now)
            transactionDao.insertTransaction(
                PointTransaction(
                    type = "DAILY_FREE",
                    amount = rewardCoins,
                    description = "النقاط اليومية المجانية (+10)"
                )
            )
            return true
        }
        return false
    }

    suspend fun spendPointsForMatch(cost: Int, filterDesc: String): Boolean {
        val user = userDao.getUserProfile().firstOrNull() ?: return false
        if (user.pointsBalance >= cost) {
            userDao.adjustPoints(-cost)
            transactionDao.insertTransaction(
                PointTransaction(
                    type = "MATCH_SPEND",
                    amount = -cost,
                    description = "مطابقة عشوائية ($filterDesc)"
                )
            )
            return true
        }
        return false
    }

    suspend fun sendGift(gift: GiftItem, streamHost: String): Boolean {
        val user = userDao.getUserProfile().firstOrNull() ?: return false
        if (user.pointsBalance >= gift.pointsCost) {
            userDao.adjustPoints(-gift.pointsCost)
            transactionDao.insertTransaction(
                PointTransaction(
                    type = "GIFT_SENT",
                    amount = -gift.pointsCost,
                    description = "إرسال هدية ${gift.name} ${gift.emoji} إلى $streamHost"
                )
            )
            return true
        }
        return false
    }

    suspend fun purchaseCoinPackage(coinPackage: CoinPackage) {
        val totalCoins = coinPackage.coins + coinPackage.bonusCoins
        userDao.adjustPoints(totalCoins)
        transactionDao.insertTransaction(
            PointTransaction(
                type = "PURCHASE",
                amount = totalCoins,
                description = "شراء باقة $totalCoins نقطة ($${coinPackage.priceUsd})"
            )
        )
    }

    suspend fun adjustPoints(delta: Int) {
        userDao.adjustPoints(delta)
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

    suspend fun applyForHost(agencyName: String) {
        userDao.updateHostStatus("PENDING", isHost = false)
    }

    suspend fun approveHostDemo() {
        userDao.updateHostStatus("APPROVED", isHost = true)
    }

    suspend fun toggleAgentStatus(isAgent: Boolean) {
        userDao.updateAgentStatus(isAgent)
    }

    suspend fun updateAgentCommissionFromAdmin(percent: Int) {
        userDao.updateAgentCommission(percent)
    }

    suspend fun recruitSubAgent(name: String, country: String): AppNotification {
        val commissionBonus = 250 // مكافأة انضمام وكيل فرعي
        userDao.addSubAgentWithBonus(commissionBonus)
        transactionDao.insertTransaction(
            PointTransaction(
                type = "AGENT_COMMISSION",
                amount = commissionBonus,
                description = "عمولة دعوة وكيل جديد: $name ($country)"
            )
        )
        val notif = AppNotification(
            title = "إحالة جديدة انضمت لشبكتك! 👥",
            message = "انضم الوكيل '$name' من $country عبر كود الإحالة الخاص بك. تم إيداع مكافأة +$commissionBonus نقطة عمولة فورية في حسابك 💎",
            type = NotificationType.NEW_REFERRAL,
            amountCoins = commissionBonus,
            senderName = name
        )
        val insertedId = notificationDao.insertNotification(notif)
        return notif.copy(id = insertedId)
    }

    suspend fun payoutCommission(amountCoins: Int, sourceDescription: String, senderName: String = "شبكة مضيفي البث"): AppNotification {
        userDao.addCommissionEarnings(amountCoins)
        transactionDao.insertTransaction(
            PointTransaction(
                type = "COMMISSION_PAYOUT",
                amount = amountCoins,
                description = "دفعة عمولة مستلمة: $sourceDescription"
            )
        )
        val notif = AppNotification(
            title = "دفعة عمولة جديدة مستلمة! 💰",
            message = "تهانينا! تم إيداع $amountCoins نقطة (≈ $${amountCoins / 10}.00) في محفظتك كعمولة أرباح من $sourceDescription 🌟",
            type = NotificationType.COMMISSION_PAYOUT,
            amountCoins = amountCoins,
            senderName = senderName
        )
        val insertedId = notificationDao.insertNotification(notif)
        return notif.copy(id = insertedId)
    }

    suspend fun withdrawCoins(coins: Int): Boolean {
        val user = userDao.getUserProfile().firstOrNull() ?: return false
        if (user.hostEarningsCoins >= coins) {
            userDao.withdrawHostCoins(coins)
            transactionDao.insertTransaction(
                PointTransaction(
                    type = "WITHDRAWAL",
                    amount = -coins,
                    description = "طلب سحب أرباح مضيف ($coins نقطة = $${coins / 10})"
                )
            )
            notificationDao.insertNotification(
                AppNotification(
                    title = "تم اعتماد دفعة سحب الأرباح 🏦",
                    message = "تمت الموافقة على طلب سحب $coins نقطة (≈ $${coins / 10}.00) بنجاح وسيتم تحويلها لحسابك البنكي خلال 24 ساعة.",
                    type = NotificationType.WITHDRAWAL_PAID,
                    amountCoins = coins
                )
            )
            return true
        }
        return false
    }

    suspend fun markNotificationAsRead(id: Long) = notificationDao.markAsRead(id)
    suspend fun markAllNotificationsAsRead() = notificationDao.markAllAsRead()
    suspend fun clearAllNotifications() = notificationDao.clearAll()
    suspend fun insertNotification(notification: AppNotification) = notificationDao.insertNotification(notification)


    fun getSampleSubAgents(): List<com.example.data.model.SubAgentItem> {
        return listOf(
            com.example.data.model.SubAgentItem("sa_1", "عمر الفاروق - كاريزما إيجنسي", "المملكة العربية السعودية", hostsCount = 14, monthlyRevenueCoins = 28000, agentCommissionEarned = 5600),
            com.example.data.model.SubAgentItem("sa_2", "ليلى الهاشمي - جولدن ستارز", "الإمارات العربية المتحدة", hostsCount = 9, monthlyRevenueCoins = 19500, agentCommissionEarned = 3900),
            com.example.data.model.SubAgentItem("sa_3", "يوسف المصري - قاهرة لايف", "مصر", hostsCount = 22, monthlyRevenueCoins = 36000, agentCommissionEarned = 7200),
            com.example.data.model.SubAgentItem("sa_4", "فهد الشمري - فينيكس هَب", "الكويت", hostsCount = 6, monthlyRevenueCoins = 11200, agentCommissionEarned = 2240)
        )
    }

    fun getAvailableCoinPackages(): List<CoinPackage> {
        return listOf(
            CoinPackage("pkg_1", coins = 50, bonusCoins = 0, priceUsd = 0.99),
            CoinPackage("pkg_2", coins = 150, bonusCoins = 15, priceUsd = 2.99),
            CoinPackage("pkg_3", coins = 500, bonusCoins = 80, priceUsd = 9.99, isPopular = true, tag = "الأكثر طلباً"),
            CoinPackage("pkg_4", coins = 1200, bonusCoins = 250, priceUsd = 21.99, tag = "توفير 20%"),
            CoinPackage("pkg_5", coins = 3000, bonusCoins = 800, priceUsd = 49.99, tag = "باقة كبار الشخصيات VIP")
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

    fun getSampleStreams(): List<LiveStreamRoom> {
        val now = System.currentTimeMillis()
        return listOf(
            LiveStreamRoom(
                id = "stream_1",
                title = "أجمل سهرة طرب وعزف عود لايف 🎶",
                hostName = "ليان الدوسري",
                hostCountry = "المملكة العربية السعودية",
                hostCountryCode = "SA",
                viewersCount = 1420,
                category = "Music",
                isVip = false,
                diamondsReceived = 18450,
                imageResId = com.example.R.drawable.img_live_model_1,
                agencyName = "وكالة النجوم الدولية",
                isAgencyStream = true,
                previewClipSeconds = 4,
                previewClipTitle = "عزف عود رومانسي مباشر 🎶",
                previewBadge = "وكالة النجوم 💎",
                startedAt = now - (35 * 60 * 1000L + 18 * 1000L) // 35m 18s ago
            ),
            LiveStreamRoom(
                id = "stream_2",
                title = "دردشة وتعارف وسوالف ممتعة مع الأصدقاء 🍷",
                hostName = "ريناد الحسيني",
                hostCountry = "الإمارات",
                hostCountryCode = "AE",
                viewersCount = 1890,
                category = "Just Chatting",
                isVip = true,
                diamondsReceived = 29200,
                imageResId = com.example.R.drawable.img_live_model_2,
                agencyName = "وكالة روتانا الذهبية",
                isAgencyStream = true,
                previewClipSeconds = 4,
                previewClipTitle = "أجواء تعارف وتحديات ممتعة ✨",
                previewBadge = "وكالة روتانا 👑",
                startedAt = now - (1 * 3600 * 1000L + 14 * 60 * 1000L + 42 * 1000L) // 1h 14m ago
            ),
            LiveStreamRoom(
                id = "stream_3",
                title = "بطولة وتحدي جيمنج لايف PUBG & Warzone 🎮🔥",
                hostName = "سامر إكس جيمر",
                hostCountry = "الكويت",
                hostCountryCode = "KW",
                viewersCount = 3120,
                category = "Gaming",
                isVip = true,
                diamondsReceived = 42800,
                imageResId = com.example.R.drawable.stream_hero_bg,
                agencyName = "وكالة إي سبورتس برو",
                isAgencyStream = true,
                previewClipSeconds = 4,
                previewClipTitle = "مواجهات حاسمة واحترافية لايف 🎮",
                previewBadge = "وكالة الألعاب 🎮",
                startedAt = now - (52 * 60 * 1000L + 5 * 1000L) // 52m ago
            ),
            LiveStreamRoom(
                id = "stream_4",
                title = "جلسة سهر وتحديات وأجواء ساحرة ✨",
                hostName = "نور الشام",
                hostCountry = "لبنان",
                hostCountryCode = "LB",
                viewersCount = 2450,
                category = "Challenges & PK",
                isVip = true,
                diamondsReceived = 38500,
                imageResId = com.example.R.drawable.img_live_model_3,
                agencyName = "وكالة القمة الملكية",
                isAgencyStream = true,
                previewClipSeconds = 4,
                previewClipTitle = "تحدي الهدايا الأكبر مباشرة 🎁",
                previewBadge = "وكالة القمة 🌟",
                startedAt = now - (18 * 60 * 1000L + 30 * 1000L) // 18m ago
            ),
            LiveStreamRoom(
                id = "stream_5",
                title = "ليالي بيروت الموسيقية لايف 🎷",
                hostName = "كارمن سلوم",
                hostCountry = "الإمارات",
                hostCountryCode = "AE",
                viewersCount = 980,
                category = "Music",
                isVip = false,
                diamondsReceived = 12300,
                imageResId = com.example.R.drawable.stream_hero_bg,
                agencyName = "وكالة VIP لايف",
                isAgencyStream = true,
                previewClipSeconds = 4,
                previewClipTitle = "موسيقى وإضاءات ساحرة 🎷",
                previewBadge = "وكالة VIP 💎",
                startedAt = now - (7 * 60 * 1000L + 12 * 1000L) // 7m ago
            ),
            LiveStreamRoom(
                id = "stream_6",
                title = "فلوج ويوميات من استوديو التصميم 🎨",
                hostName = "ماجد الغامدي",
                hostCountry = "المملكة العربية السعودية",
                hostCountryCode = "SA",
                viewersCount = 840,
                category = "Lifestyle",
                isVip = false,
                diamondsReceived = 9200,
                imageResId = com.example.R.drawable.img_live_model_1,
                agencyName = "وكالة المبدعين العرب",
                isAgencyStream = true,
                previewClipSeconds = 4,
                previewClipTitle = "إلهام وتصميم مباشر 🎨",
                previewBadge = "وكالة المبدعين ✨",
                startedAt = now - (3 * 60 * 1000L + 45 * 1000L) // 3m 45s ago
            )
        )
    }

    fun getSampleMatchCandidates(): List<MatchCandidate> {
        return listOf(
            MatchCandidate(
                id = "cand_1",
                name = "سارة العتيبي",
                age = 23,
                country = "المملكة العربية السعودية",
                countryCode = "SA",
                gender = "FEMALE",
                language = "العربية والإنجليزية",
                bio = "مهتمة بالفن التشكيلي، القهوة المختصة، وتبادل الثقافات 🎨☕",
                interests = listOf("فنون", "سفر", "بودكاست"),
                imageResId = com.example.R.drawable.img_live_model_1
            ),
            MatchCandidate(
                id = "cand_2",
                name = "Lucas Martin",
                age = 25,
                country = "فرنسا",
                countryCode = "FR",
                gender = "MALE",
                language = "French & English",
                bio = "Photographer & digital nomad. Exploring world cultures! 📸",
                interests = listOf("تصوير", "مغامرات", "لغات"),
                imageResId = null
            ),
            MatchCandidate(
                id = "cand_3",
                name = "ريم التميمي",
                age = 22,
                country = "الكويت",
                countryCode = "KW",
                gender = "FEMALE",
                language = "العربية",
                bio = "أحب الكتب والموسيقى الهادئة. نبحث عن أصدقاء حقيقيين ✨",
                interests = listOf("موسيقى", "قراءة", "شعر"),
                imageResId = com.example.R.drawable.img_live_model_2
            ),
            MatchCandidate(
                id = "cand_4",
                name = "Sofia Rossi",
                age = 24,
                country = "إيطاليا",
                countryCode = "IT",
                gender = "FEMALE",
                language = "Italian & English",
                bio = "Architect and foodie from Milan. Ciao! 🍕",
                interests = listOf("تصميم", "طبخ", "تاريخ"),
                imageResId = com.example.R.drawable.img_live_model_3
            )
        )
    }

    suspend fun submitUserProfileReport(report: com.example.data.model.UserProfileReport): Result<Boolean> {
        return firestoreChatService.submitUserProfileReport(report)
    }
}
