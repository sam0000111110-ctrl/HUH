package com.example.calls

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CallType {
    AUDIO,
    VIDEO
}

enum class CallLifecycleState {
    IDLE,
    DIALING,
    RINGING_OUTGOING,
    RINGING_INCOMING,
    CONNECTING,
    ACTIVE,
    RECONNECTING,
    ENDED,
    REJECTED,
    MISSED,
    CANCELLED,
    FAILED,
    BLOCKED_BUDGET
}

@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey val callId: String,
    val roomName: String,
    val callerId: String,
    val callerName: String,
    val calleeId: String,
    val calleeName: String,
    val callType: String, // AUDIO or VIDEO
    val status: String,   // CallLifecycleState.name
    val initiatedAtMs: Long,
    val acceptedAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val disconnectedGapMs: Long = 0L,
    val connectedDurationSeconds: Int = 0,
    val estimatedCost: Double = 0.0,
    val currencyCode: String = "EGP",
    val failureReason: String? = null,
    val pointsDeducted: Int = 0,
    val finalized: Boolean = false
)

@Entity(tableName = "call_governance_config")
data class CallGovernanceConfigEntity(
    @PrimaryKey val id: Int = 1,
    val monthlyBudgetAmount: Double = 1000.0,
    val currencyCode: String = "EGP",
    val alertThreshold1Percent: Int = 50,
    val alertThreshold2Percent: Int = 80,
    val hardStopAt100Percent: Boolean = true,
    val maxCallDurationMinutes: Int = 30,
    val perUserDailyCallLimitMinutes: Int = 60,
    val perUserDailyVideoLimitMinutes: Int = 30,
    val monthlyVideoLimitMinutes: Int = 2000,
    val disableVideoKeepAudioOnly: Boolean = false,
    val audioCostPerParticipantMinute: Double = 0.02,
    val videoCostPerParticipantMinute: Double = 0.08,
    val rateLimitCallsPerMinute: Int = 5,
    val alertFailureRatePercent: Int = 30,
    val alertMaxConcurrentCalls: Int = 25,
    val alertSingleUserDailyMinutes: Int = 120,
    val exemptUserIdsCsv: String = "",
    val blockedUserIdsCsv: String = "",
    val providerHealthHealthy: Boolean = true
) {
    val exemptUserIds: Set<String>
        get() = exemptUserIdsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val blockedUserIds: Set<String>
        get() = blockedUserIdsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

@Entity(tableName = "call_admin_alerts")
data class CallAdminAlertEntity(
    @PrimaryKey val id: String,
    val code: String,
    val severity: String, // INFO, WARNING, HIGH, CRITICAL
    val title: String,
    val message: String,
    val dedupKey: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val acknowledged: Boolean = false
)

data class IncomingCallInvite(
    val callId: String,
    val roomName: String,
    val callerId: String,
    val callerName: String,
    val callerCountry: String = "دبي، الإمارات",
    val callerAvatarRes: Int? = null,
    val callType: CallType = CallType.VIDEO,
    val livekitUrl: String = "",
    val calleeToken: String = "",
    val receivedAtMs: Long = System.currentTimeMillis()
)

data class UserCallConsumptionSummary(
    val userId: String,
    val userName: String,
    val totalMinutes: Double,
    val estimatedCost: Double,
    val callCount: Int,
    val isBlocked: Boolean,
    val isExempt: Boolean
)

data class CallMonitoringSnapshot(
    val todayCallsCount: Int = 0,
    val todayAudioCallsCount: Int = 0,
    val todayVideoCallsCount: Int = 0,
    val activeCallsCount: Int = 0,
    val totalAudioMinutes: Double = 0.0,
    val totalVideoMinutes: Double = 0.0,
    val averageCallDurationSeconds: Int = 0,
    val successfulCallsCount: Int = 0,
    val failedCallsCount: Int = 0,
    val rejectedCallsCount: Int = 0,
    val missedCallsCount: Int = 0,
    val todayConsumptionMinutes: Double = 0.0,
    val monthlyConsumptionMinutes: Double = 0.0,
    val dailyEstimatedCost: Double = 0.0,
    val monthlyEstimatedCost: Double = 0.0,
    val budgetUsedPercent: Double = 0.0,
    val currencyCode: String = "EGP",
    val topConsumingUsers: List<UserCallConsumptionSummary> = emptyList(),
    val callLogs: List<CallRecordEntity> = emptyList(),
    val alerts: List<CallAdminAlertEntity> = emptyList(),
    val config: CallGovernanceConfigEntity = CallGovernanceConfigEntity()
)

sealed class CallInitiationOutcome {
    data class Ready(
        val callId: String,
        val roomName: String,
        val livekitUrl: String,
        val token: String,
        val effectiveCallType: CallType,
        val downgradedToAudio: Boolean,
        val maxDurationSeconds: Int
    ) : CallInitiationOutcome()

    data class Blocked(
        val code: String,
        val reasonArabic: String
    ) : CallInitiationOutcome()
}
