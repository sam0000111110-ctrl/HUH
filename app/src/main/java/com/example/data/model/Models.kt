package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserProfile(
    @PrimaryKey val id: Long = 1L,
    val name: String = "سامر",
    val age: Int = 24,
    val country: String = "المملكة العربية السعودية",
    val countryCode: String = "SA",
    val gender: String = "MALE",
    val pointsBalance: Int = 1450,
    val totalAccumulatedPoints: Int = 12500, // إجمالي النقاط التراكمية مدى الحياة
    val totalPointsSpent: Int = 8900, // إجمالي النقاط المنفقة
    val lastDailyClaimTime: Long = 0L,
    val ageVerified: Boolean = true,
    val isHost: Boolean = false,
    val hostStatus: String = "APPROVED", // NONE, PENDING, APPROVED
    val isAgent: Boolean = false, // زر الوكلاء لا يظهر الا بعد ترقية الحساب الى وكيل
    val agentCommissionPercent: Int = 20, // نسبة أرباح الوكيل المحددة من لوحة الأدمن (20%)
    val subAgentsEarningsCoins: Int = 3420, // أرباح الوكيل من الوكلاء والمضيفين المدعوين
    val subAgentsCount: Int = 4,
    val referralCode: String = "AYER-AGENT-77",
    val agencyName: String = "الوكالة الماسية - Diamond Agency",
    val agencyId: String = "AG-78901",
    val agencyRole: String = "عضو مميز / وكيل معتمد",
    val agencyJoinedDate: String = "15 يناير 2024",
    val agencyManagerName: String = "أحمد الشريف",
    val agencyActiveHostsCount: Int = 12,
    val agencyMonthlyTargetProgress: Int = 82,
    val hostCommissionPercent: Int = 50,
    val hostEarningsCoins: Int = 1850,
    val totalMatchesCount: Int = 38,
    val agencyLevel: String = "Lv.4 الماسي 💎",
    val bio: String = "مستخدم ومضيف نشط في مجتمع Ayer للتعارف والبث المباشر 🌟",
    val phoneNumber: String = "+966 50 123 4567",
    val email: String = "sam0000111110@gmail.com",
    val avatarResId: Int = com.example.R.drawable.img_user_avatar,
    val avatarCustomUri: String? = null
)

val UserProfile.currentAgencyLevel: String
    get() = if (agencyLevel.isNotBlank()) agencyLevel else if (isAgent) "Lv.4 الماسي 💎" else "Lv.1 🌟"

data class UserEngagementState(
    val dailyStreakDays: Int = 5,
    val todayStreamMinutes: Int = 18,
    val targetStreamMinutes: Int = 30,
    val todayMatchesCount: Int = 4,
    val targetMatchesCount: Int = 5,
    val todayGiftsSent: Int = 2,
    val targetGiftsSent: Int = 3,
    val todayEngagementPoints: Int = 145,
    val dailyMilestoneClaimed: Boolean = false
)

data class FirestoreRandomChatMessage(
    val id: String = "",
    val matchId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val translatedText: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SENT"
)


@Entity(tableName = "transactions")
data class PointTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String, // DAILY_FREE, PURCHASE, MATCH_SPEND, GIFT_SENT, CALL_SPEND, AGENT_COMMISSION
    val amount: Int,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SubAgentItem(
    val id: String,
    val name: String,
    val country: String,
    val hostsCount: Int,
    val monthlyRevenueCoins: Int,
    val agentCommissionEarned: Int,
    val status: String = "نشط"
)

data class LiveStreamRoom(
    val id: String,
    val title: String,
    val hostName: String,
    val hostCountry: String = "العالم 🌐",
    val hostCountryCode: String = "🌍",
    val viewersCount: Int = 1200,
    val category: String = "موسيقى وتواصل",
    val entryCost: Int = 0,
    val isVip: Boolean = false,
    val hostBio: String = "",
    val diamondsReceived: Int = 1240,
    val imageResId: Int? = null,
    val agencyName: String? = null,
    val isAgencyStream: Boolean = true,
    val previewClipSeconds: Int = 4,
    val previewClipTitle: String = "مقطع متحرك (4 ثوانٍ)",
    val previewBadge: String = "بث وكالة معتمد 💎",
    val startedAt: Long = System.currentTimeMillis() - (12 * 60 * 1000L) // Default: started 12 mins ago
)

data class GiftItem(
    val id: String,
    val name: String,
    val pointsCost: Int,
    val emoji: String,
    val soundEffectDesc: String
)

data class StreamMessage(
    val id: String,
    val senderName: String,
    val originalText: String,
    val translatedText: String? = null,
    val isSystem: Boolean = false,
    val isGift: Boolean = false,
    val giftEmoji: String? = null
)

data class MatchCandidate(
    val id: String,
    val name: String,
    val age: Int,
    val country: String,
    val countryCode: String,
    val gender: String,
    val language: String,
    val bio: String,
    val interests: List<String>,
    val imageResId: Int? = null
)

enum class MatchChatType(
    val code: String,
    val title: String,
    val iconEmoji: String,
    val cost: Int
) {
    VIDEO("VIDEO", "فيديو", "🎥", 3),
    VOICE("VOICE", "صوت", "🎙️", 2),
    TEXT("TEXT", "كتابة", "💬", 1);

    companion object {
        fun fromCode(code: String): MatchChatType =
            entries.find { it.code.equals(code, ignoreCase = true) } ?: VIDEO
    }
}

data class CoinPackage(
    val id: String,
    val coins: Int,
    val bonusCoins: Int,
    val priceUsd: Double,
    val isPopular: Boolean = false,
    val tag: String? = null
)

enum class NotificationType {
    NEW_REFERRAL,        // إشعار انضمام إحالة جديدة
    COMMISSION_PAYOUT,   // إشعار وصول دفعة عمولة أرباح
    WITHDRAWAL_PAID,     // إشعار تنفيذ طلب سحب الأرباح
    SYSTEM_ALERT         // إشعار نظام عام
}

@Entity(tableName = "notifications")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val message: String,
    val type: NotificationType,
    val amountCoins: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val senderName: String? = null
)

@Entity(tableName = "secondary_profiles")
data class SecondaryProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val parentUserId: Long = 1L,
    val username: String,
    val passwordHash: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val avatarResId: Int? = null,
    val bio: String = "بروفايل خاص محمي برمز سري",
    val notificationsEnabled: Boolean = false,
    val pointsBalance: Int = 45,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE" // ACTIVE, SUSPENDED
)

enum class CallQualityMode {
    ADAPTIVE_SMART, // LiveKit Simulcast Adaptive Bitrate
    DATA_SAVER,     // توفير بيانات (منخفض 360p)
    HIGH_DEFINITION // أفضل جودة (عالي 1080p)
}

data class ActiveCallSession(
    val callId: String = "call_${System.currentTimeMillis()}",
    val roomName: String = "",
    val participantId: String = "partner_1",
    val participantName: String,
    val participantAge: Int = 23,
    val participantCountry: String = "دبي، الإمارات",
    val participantCountryCode: String = "AE",
    val participantAvatarRes: Int? = null,
    val isVideo: Boolean = true,
    val isOutgoing: Boolean = true,
    val callState: com.example.calls.CallLifecycleState = com.example.calls.CallLifecycleState.DIALING,
    val pointsCostPerMinute: Int = 5,
    val durationSeconds: Int = 0,
    val isMuted: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val isSpeakerOn: Boolean = true,
    val isTranslationEnabled: Boolean = true,
    val qualityMode: CallQualityMode = CallQualityMode.ADAPTIVE_SMART,
    val isAudioPriorityWhenWeak: Boolean = true,
    val isNetworkWeak: Boolean = false,
    val isTorchOn: Boolean = false,
    val isSwitchingCamera: Boolean = false,
    val isCameraSwitchInProgress: Boolean = isSwitchingCamera,
    val reconnectAttempt: Int = 0,
    val downgradedToAudio: Boolean = false,
    val statusHint: String? = null,
    val simulatedSubtitles: String? = null
)

enum class FriendshipStatus {
    ACCEPTED,
    PENDING_INCOMING,
    PENDING_OUTGOING,
    BLOCKED
}

data class FriendItem(
    val id: String,
    val name: String,
    val country: String,
    val age: Int = 24,
    val avatarResId: Int? = null,
    val status: FriendshipStatus = FriendshipStatus.ACCEPTED,
    val lastMessage: String = "مرحبًا! كيف حالك؟",
    val unreadCount: Int = 0,
    val isOnline: Boolean = true
)

data class PrivateChatMessage(
    val id: String = "msg_${System.currentTimeMillis()}_${(0..999).random()}",
    val senderId: String, // "me" or friend's id
    val text: String? = null,
    val textColorHex: String = "#FFFFFF",
    val bgColorHex: String = "#1E1B2E",
    val mediaResId: Int? = null,
    val mediaPriceCoins: Int = 0,
    val isUnlocked: Boolean = false,
    val unlockProgress: Float = 0f, // 0.0 to 1.0 scratch progress
    val isVoiceMessage: Boolean = false,
    val voiceDurationSeconds: Int = 5,
    val callLogRecordId: String? = null,
    val callLogIsVideo: Boolean = false,
    val callLogStatus: String? = null, // ENDED, REJECTED, MISSED, FAILED, BLOCKED_BUDGET
    val callLogDurationSeconds: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class DailyMissionItem(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String, // e.g. "live", "chat", "match", "gift", "login"
    val currentProgress: Int,
    val targetProgress: Int,
    val rewardPoints: Int,
    val isClaimed: Boolean = false,
    val unit: String = ""
) {
    val isCompleted: Boolean get() = currentProgress >= targetProgress
    val progressFraction: Float get() = if (targetProgress > 0) (currentProgress.toFloat() / targetProgress).coerceIn(0f, 1f) else 0f
}

data class LiveStreamReport(
    val id: String = "rep_${System.currentTimeMillis()}_${(100..999).random()}",
    val streamId: String,
    val streamTitle: String,
    val hostName: String,
    val reporterId: String,
    val reporterName: String,
    val reason: String,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING_REVIEW"
)

data class UserProfileReport(
    val id: String = "urep_${System.currentTimeMillis()}_${(100..999).random()}",
    val targetUserId: String,
    val targetUserName: String,
    val reporterId: String,
    val reporterName: String,
    val reason: String,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING_REVIEW"
)

enum class ProfileVisibilityOption(val title: String, val desc: String) {
    PUBLIC("عام للجميع", "يمكن لأي شخص العثور على بروفايلك ورؤيته"),
    FRIENDS_ONLY("الأصدقاء والمتابعين فقط", "يستطيع الأصدقاء المضافون فقط تصفح بروفايلك"),
    PRIVATE_HIDDEN("خاص ومخفي", "بروفايلك مخفي تماماً من قائمة البحث والبطاقات العشوائية")
}

enum class DirectMessagePermission(val title: String, val desc: String) {
    EVERYONE("الجميع", "يمكن لأي مستخدم إرسال رسائل خاصة لك"),
    FRIENDS_ONLY("الأصدقاء فقط", "الرسائل الخاصة مسموحة للأصدقاء المضافين فقط"),
    VERIFIED_VIP_ONLY("الموثقين والـ VIP فقط", "يُسمح فقط للحسابات الموثقة 18+ ومشتري باقات VIP"),
    NOBODY("إيقاف الرسائل الخاصة", "لا يمكن لأي شخص إرسال رسائل خاصة")
}

enum class LeaderboardScope {
    GLOBAL,
    AGENCY
}

enum class LeaderboardTimeframe {
    TODAY,
    THIS_WEEK,
    ALL_TIME
}

data class CreatorLeaderboardItem(
    val rank: Int,
    val id: String,
    val name: String,
    val country: String,
    val countryCode: String,
    val agencyName: String,
    val pointsAccumulated: Int,
    val diamondsReceived: Int = (pointsAccumulated * 0.85).toInt(),
    val followersCount: Int = 1420,
    val isLiveNow: Boolean = false,
    val avatarResId: Int? = null,
    val isCurrentUser: Boolean = false
)

