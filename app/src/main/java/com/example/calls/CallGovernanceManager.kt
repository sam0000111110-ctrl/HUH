package com.example.calls

import android.os.SystemClock
import com.example.BuildConfig
import com.example.data.local.CallDao
import com.example.data.local.NotificationDao
import com.example.data.model.AppNotification
import com.example.data.model.NotificationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Authoritative 1-on-1 Call Governance, Accounting & Monitoring Manager.
 *
 * Security & Accounting Guarantees:
 * - Zero RTC API Secrets stored in the APK. When [BuildConfig.CALL_BACKEND_URL] is set,
 *   calls are brokered remotely via [CallBackendApi] using Firebase ID Tokens.
 * - When operating in local/offline preview mode (no backend URL configured), applies the exact
 *   same strict rules locally backed by Room ([CallDao]), monotonic clock ([elapsedRealtimeProvider])
 *   to prevent device clock tampering, idempotent finalization, sliding-window rate limits,
 *   budget hard-stops, and admin alert notifications.
 * - Ringing/dialing time is NEVER billed or counted in duration.
 * - Disconnected / RECONNECTING gaps are tracked and subtracted from connected talk duration.
 */
class CallGovernanceManager(
    private val callDao: CallDao,
    private val notificationDao: NotificationDao,
    private val remoteApi: CallBackendApi? = buildOptionalRemoteApi(),
    private val authTokenProvider: suspend () -> String? = { null },
    private val wallClockProvider: () -> Long = { System.currentTimeMillis() },
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val onCriticalAdminAlert: (CallAdminAlertEntity) -> Unit = {}
) {
    private val mutex = Mutex()
    private val rateLimitWindow = ConcurrentHashMap<String, MutableList<Long>>()
    private val activeMonotonicAcceptMs = ConcurrentHashMap<String, Long>()
    private val activeReconnectStartMonotonicMs = ConcurrentHashMap<String, Long>()

    companion object {
        private fun buildOptionalRemoteApi(): CallBackendApi? {
            val url = BuildConfig.CALL_BACKEND_URL.trim()
            return if (url.startsWith("https://") || url.startsWith("http://")) {
                CallBackendApi.create(url)
            } else null
        }

        fun dayKey(tsMs: Long): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return fmt.format(Date(tsMs))
        }

        fun monthKey(tsMs: Long): String {
            val fmt = SimpleDateFormat("yyyy-MM", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return fmt.format(Date(tsMs))
        }
    }

    suspend fun ensureDefaultConfig() {
        mutex.withLock {
            if (callDao.getGovernanceConfigSync() == null) {
                callDao.upsertGovernanceConfig(CallGovernanceConfigEntity())
            }
        }
    }

    fun observeGovernanceConfig(): Flow<CallGovernanceConfigEntity> =
        callDao.observeGovernanceConfig().map { it ?: CallGovernanceConfigEntity() }

    fun observeMonitoringSnapshot(): Flow<CallMonitoringSnapshot> =
        combine(
            callDao.observeAllCallRecords(),
            callDao.observeGovernanceConfig(),
            callDao.observeAlerts()
        ) { records, config, alerts ->
            buildMonitoringSnapshot(
                records = records,
                config = config ?: CallGovernanceConfigEntity(),
                alerts = alerts,
                nowMs = wallClockProvider()
            )
        }

    suspend fun getMonitoringSnapshotSync(): CallMonitoringSnapshot {
        val records = callDao.getAllCallRecordsSync()
        val config = callDao.getGovernanceConfigSync() ?: CallGovernanceConfigEntity()
        val alerts = callDao.getAlertsSync()
        return buildMonitoringSnapshot(records, config, alerts, wallClockProvider())
    }

    suspend fun updateGovernanceConfig(config: CallGovernanceConfigEntity) {
        mutex.withLock {
            val sanitized = config.copy(
                id = 1,
                monthlyBudgetAmount = config.monthlyBudgetAmount.coerceAtLeast(0.0),
                alertThreshold1Percent = config.alertThreshold1Percent.coerceIn(1, 99),
                alertThreshold2Percent = config.alertThreshold2Percent.coerceIn(config.alertThreshold1Percent.coerceIn(1, 99), 100),
                maxCallDurationMinutes = config.maxCallDurationMinutes.coerceAtLeast(1),
                perUserDailyCallLimitMinutes = config.perUserDailyCallLimitMinutes.coerceAtLeast(0),
                perUserDailyVideoLimitMinutes = config.perUserDailyVideoLimitMinutes.coerceAtLeast(0),
                monthlyVideoLimitMinutes = config.monthlyVideoLimitMinutes.coerceAtLeast(0),
                rateLimitCallsPerMinute = config.rateLimitCallsPerMinute.coerceAtLeast(1)
            )
            callDao.upsertGovernanceConfig(sanitized)

            val bearer = authTokenProvider()
            if (remoteApi != null && !bearer.isNullOrBlank()) {
                try {
                    remoteApi.updateGovernanceConfig(
                        "Bearer $bearer",
                        CallConfigDto(
                            monthlyBudgetAmount = sanitized.monthlyBudgetAmount,
                            currencyCode = sanitized.currencyCode,
                            alertThreshold1Percent = sanitized.alertThreshold1Percent,
                            alertThreshold2Percent = sanitized.alertThreshold2Percent,
                            hardStopAt100Percent = sanitized.hardStopAt100Percent,
                            maxCallDurationMinutes = sanitized.maxCallDurationMinutes,
                            perUserDailyCallLimitMinutes = sanitized.perUserDailyCallLimitMinutes,
                            perUserDailyVideoLimitMinutes = sanitized.perUserDailyVideoLimitMinutes,
                            monthlyVideoLimitMinutes = sanitized.monthlyVideoLimitMinutes,
                            disableVideoKeepAudioOnly = sanitized.disableVideoKeepAudioOnly,
                            audioCostPerParticipantMinute = sanitized.audioCostPerParticipantMinute,
                            videoCostPerParticipantMinute = sanitized.videoCostPerParticipantMinute,
                            rateLimitCallsPerMinute = sanitized.rateLimitCallsPerMinute,
                            alertFailureRatePercent = sanitized.alertFailureRatePercent,
                            alertMaxConcurrentCalls = sanitized.alertMaxConcurrentCalls,
                            alertSingleUserDailyMinutes = sanitized.alertSingleUserDailyMinutes,
                            exemptUserIds = sanitized.exemptUserIds.toList(),
                            blockedUserIds = sanitized.blockedUserIds.toList()
                        )
                    )
                } catch (_: Throwable) {
                }
            }
        }
    }

    suspend fun acknowledgeAlert(alertId: String) {
        callDao.acknowledgeAlert(alertId)
    }

    suspend fun toggleUserCallBlock(userId: String, blocked: Boolean) {
        val updatedConfig = mutex.withLock {
            val cfg = callDao.getGovernanceConfigSync() ?: CallGovernanceConfigEntity()
            val updated = cfg.blockedUserIds.toMutableSet().apply {
                if (blocked) add(userId) else remove(userId)
            }
            cfg.copy(blockedUserIdsCsv = updated.joinToString(","))
        }
        updateGovernanceConfig(updatedConfig)
    }

    suspend fun toggleUserVipExemption(userId: String, exempt: Boolean) {
        val updatedConfig = mutex.withLock {
            val cfg = callDao.getGovernanceConfigSync() ?: CallGovernanceConfigEntity()
            val updated = cfg.exemptUserIds.toMutableSet().apply {
                if (exempt) add(userId) else remove(userId)
            }
            cfg.copy(exemptUserIdsCsv = updated.joinToString(","))
        }
        updateGovernanceConfig(updatedConfig)
    }

    suspend fun initiateCall(
        callerId: String,
        callerName: String,
        calleeId: String,
        calleeName: String,
        requestedType: CallType
    ): CallInitiationOutcome = mutex.withLock {
        val nowMs = wallClockProvider()
        val nowMono = elapsedRealtimeProvider()

        if (callerId.isBlank() || calleeId.isBlank() || callerId == calleeId) {
            return@withLock CallInitiationOutcome.Blocked(
                code = "INVALID_PARTICIPANTS",
                reasonArabic = "بيانات طرفي المكالمة غير صالحة."
            )
        }

        val config = callDao.getGovernanceConfigSync() ?: CallGovernanceConfigEntity().also {
            callDao.upsertGovernanceConfig(it)
        }

        // 1. Sliding-window rate limit check
        val recentCalls = rateLimitWindow.getOrPut(callerId) { mutableListOf() }
        recentCalls.removeAll { nowMono - it >= 60_000L }
        if (recentCalls.size >= config.rateLimitCallsPerMinute) {
            emitAlertLocked(
                code = "ABNORMAL_USAGE_SPIKE",
                severity = "WARNING",
                title = "تجاوز حد محاولات الاتصال السريعة",
                message = "المستخدم $callerName ($callerId) تجاوز حد ${config.rateLimitCallsPerMinute} طلبات اتصال في الدقيقة.",
                dedupKey = "rate_${callerId}_${nowMs / 300_000L}",
                nowMs = nowMs
            )
            return@withLock CallInitiationOutcome.Blocked(
                code = "RATE_LIMITED",
                reasonArabic = "تم تجاوز الحد المسموح لطلبات الاتصال المتكررة. يرجى الانتظار دقيقة."
            )
        }
        recentCalls.add(nowMono)

        // 2. User block / permissions check
        if (config.blockedUserIds.contains(callerId)) {
            return@withLock CallInitiationOutcome.Blocked(
                code = "USER_CALLS_DISABLED",
                reasonArabic = "تم إيقاف صلاحية إجراء المكالمات لهذا الحساب من الإدارة."
            )
        }
        if (config.blockedUserIds.contains(calleeId)) {
            return@withLock CallInitiationOutcome.Blocked(
                code = "CALLEE_CALLS_DISABLED",
                reasonArabic = "الطرف الآخر مقيد من استقبال المكالمات حالياً."
            )
        }

        val isExempt = config.exemptUserIds.contains(callerId)
        val records = callDao.getAllCallRecordsSync()
        val snap = buildMonitoringSnapshot(records, config, emptyList(), nowMs)

        // 3. Monthly budget hard stop (NEVER cuts off already-active calls; blocks NEW ones)
        if (!isExempt && config.hardStopAt100Percent && config.monthlyBudgetAmount > 0.0 &&
            snap.monthlyEstimatedCost >= config.monthlyBudgetAmount
        ) {
            evaluateBudgetAndHealthAlertsLocked(config, snap, nowMs)
            val blockedRecord = CallRecordEntity(
                callId = "call_${UUID.randomUUID()}",
                roomName = "",
                callerId = callerId,
                callerName = callerName,
                calleeId = calleeId,
                calleeName = calleeName,
                callType = requestedType.name,
                status = CallLifecycleState.BLOCKED_BUDGET.name,
                initiatedAtMs = nowMs,
                endedAtMs = nowMs,
                connectedDurationSeconds = 0,
                estimatedCost = 0.0,
                currencyCode = config.currencyCode,
                failureReason = "BUDGET_EXCEEDED",
                finalized = true
            )
            callDao.upsertCallRecord(blockedRecord)
            return@withLock CallInitiationOutcome.Blocked(
                code = "BUDGET_EXCEEDED",
                reasonArabic = "تم إيقاف إنشاء المكالمات مؤقتاً لاستهلاك 100% من ميزانية التشغيل الشهرية."
            )
        }

        // 4. Per-user daily call minutes check
        val todayKey = dayKey(nowMs)
        val userTodayMinutes = records.filter {
            dayKey(it.acceptedAtMs ?: it.initiatedAtMs) == todayKey &&
                (it.callerId == callerId || it.calleeId == callerId)
        }.sumOf { it.connectedDurationSeconds / 60.0 }

        if (!isExempt && config.perUserDailyCallLimitMinutes > 0 &&
            userTodayMinutes >= config.perUserDailyCallLimitMinutes
        ) {
            return@withLock CallInitiationOutcome.Blocked(
                code = "USER_DAILY_LIMIT",
                reasonArabic = "وصلت إلى الحد اليومي للمكالمات (${config.perUserDailyCallLimitMinutes} دقيقة)."
            )
        }

        // 5. Video call allowance / global video-off switch
        var effectiveType = requestedType
        var downgradedToAudio = false
        if (requestedType == CallType.VIDEO && config.disableVideoKeepAudioOnly && !isExempt) {
            effectiveType = CallType.AUDIO
            downgradedToAudio = true
        }

        if (effectiveType == CallType.VIDEO && !isExempt) {
            val userTodayVideoMin = records.filter {
                dayKey(it.acceptedAtMs ?: it.initiatedAtMs) == todayKey &&
                    it.callType == CallType.VIDEO.name &&
                    (it.callerId == callerId || it.calleeId == callerId)
            }.sumOf { it.connectedDurationSeconds / 60.0 }

            if (config.perUserDailyVideoLimitMinutes > 0 && userTodayVideoMin >= config.perUserDailyVideoLimitMinutes) {
                if (config.disableVideoKeepAudioOnly) {
                    effectiveType = CallType.AUDIO
                    downgradedToAudio = true
                } else {
                    return@withLock CallInitiationOutcome.Blocked(
                        code = "USER_VIDEO_DAILY_LIMIT",
                        reasonArabic = "استهلكت حدك اليومي لمكالمات الفيديو (${config.perUserDailyVideoLimitMinutes} دقيقة)."
                    )
                }
            }

            if (config.monthlyVideoLimitMinutes > 0 && snap.totalVideoMinutes >= config.monthlyVideoLimitMinutes) {
                effectiveType = CallType.AUDIO
                downgradedToAudio = true
            }
        }

        // 6. Attempt remote broker if CALL_BACKEND_URL is configured
        val bearer = authTokenProvider()
        if (remoteApi != null && !bearer.isNullOrBlank()) {
            try {
                val response = remoteApi.initiateCall(
                    bearerToken = "Bearer $bearer",
                    body = InitiateCallRequestDto(
                        callerName = callerName,
                        calleeId = calleeId,
                        calleeName = calleeName,
                        callType = effectiveType.name
                    )
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val rec = CallRecordEntity(
                        callId = body.call.callId,
                        roomName = body.call.roomName,
                        callerId = body.call.callerId,
                        callerName = body.call.callerName,
                        calleeId = body.call.calleeId,
                        calleeName = body.call.calleeName,
                        callType = body.call.callType,
                        status = CallLifecycleState.DIALING.name,
                        initiatedAtMs = body.call.initiatedAt,
                        currencyCode = body.call.currencyCode
                    )
                    callDao.upsertCallRecord(rec)
                    return@withLock CallInitiationOutcome.Ready(
                        callId = body.call.callId,
                        roomName = body.call.roomName,
                        livekitUrl = body.livekitUrl,
                        token = body.token,
                        effectiveCallType = if (body.call.callType == CallType.VIDEO.name) CallType.VIDEO else CallType.AUDIO,
                        downgradedToAudio = body.call.downgradedToAudio || downgradedToAudio,
                        maxDurationSeconds = body.maxDurationSeconds
                    )
                } else if (response.code() in listOf(402, 403, 429)) {
                    return@withLock CallInitiationOutcome.Blocked(
                        code = "BACKEND_POLICY_${response.code()}",
                        reasonArabic = "رفض الخادم إنشاء المكالمة وفق سياسة الميزانية أو الاستخدام."
                    )
                }
            } catch (t: Throwable) {
                recordProviderIssueLocked("تعذر الاتصال بخادم إدارة المكالمات: ${t.message}", nowMs)
                return@withLock CallInitiationOutcome.Blocked(
                    code = "BACKEND_UNREACHABLE",
                    reasonArabic = "تعذر التحقق من صلاحية المكالمة عبر الخادم. تحقق من اتصال الإنترنت."
                )
            }
        }

        // Local authoritative preview mode (when CALL_BACKEND_URL is blank in local/emulator mode)
        val callId = "call_${UUID.randomUUID()}"
        val roomName = "ayer_1on1_$callId"
        val record = CallRecordEntity(
            callId = callId,
            roomName = roomName,
            callerId = callerId,
            callerName = callerName,
            calleeId = calleeId,
            calleeName = calleeName,
            callType = effectiveType.name,
            status = CallLifecycleState.DIALING.name,
            initiatedAtMs = nowMs,
            acceptedAtMs = null,
            endedAtMs = null,
            connectedDurationSeconds = 0,
            estimatedCost = 0.0,
            currencyCode = config.currencyCode,
            finalized = false
        )
        callDao.upsertCallRecord(record)
        evaluateBudgetAndHealthAlertsLocked(config, snap, nowMs)

        CallInitiationOutcome.Ready(
            callId = callId,
            roomName = roomName,
            livekitUrl = "",
            token = "",
            effectiveCallType = effectiveType,
            downgradedToAudio = downgradedToAudio,
            maxDurationSeconds = config.maxCallDurationMinutes * 60
        )
    }

    /**
     * Marks call as accepted and starts monotonic connected-time accounting strictly from this instant.
     * Enforces participant identity: only the designated callee or authorized participant may accept.
     */
    suspend fun markCallAccepted(callId: String, actorId: String): CallRecordEntity? = mutex.withLock {
        val record = callDao.getCallById(callId) ?: return@withLock null
        if (actorId != record.calleeId && actorId != record.callerId) {
            return@withLock null
        }
        if (record.finalized) return@withLock record

        val nowMs = wallClockProvider()
        val nowMono = elapsedRealtimeProvider()

        if (record.acceptedAtMs == null) {
            activeMonotonicAcceptMs[callId] = nowMono
            val updated = record.copy(
                status = CallLifecycleState.ACTIVE.name,
                acceptedAtMs = nowMs
            )
            callDao.upsertCallRecord(updated)

            val bearer = authTokenProvider()
            if (remoteApi != null && !bearer.isNullOrBlank()) {
                try {
                    remoteApi.acceptCall("Bearer $bearer", callId)
                } catch (_: Throwable) {
                }
            }
            return@withLock updated
        }
        return@withLock record
    }

    /**
     * Authorizes & accepts an incoming call invitation for [calleeId].
     * Ensures budget/block policies and fetches a fresh LiveKit participant token if backend is active.
     */
    suspend fun acceptIncomingInvite(
        invite: IncomingCallInvite,
        calleeId: String,
        calleeName: String
    ): CallInitiationOutcome = mutex.withLock {
        val nowMs = wallClockProvider()
        val nowMono = elapsedRealtimeProvider()
        val config = callDao.getGovernanceConfigSync() ?: CallGovernanceConfigEntity().also {
            callDao.upsertGovernanceConfig(it)
        }

        if (config.blockedUserIds.contains(calleeId) || config.blockedUserIds.contains(invite.callerId)) {
            return@withLock CallInitiationOutcome.Blocked(
                code = "USER_CALLS_DISABLED",
                reasonArabic = "المكالمات موقوفة لأحد الطرفين من الإدارة."
            )
        }

        var resolvedUrl = invite.livekitUrl
        var resolvedToken = invite.calleeToken
        val bearer = authTokenProvider()
        if (remoteApi != null && !bearer.isNullOrBlank()) {
            try {
                val resp = remoteApi.acceptCall("Bearer $bearer", invite.callId)
                if (resp.isSuccessful && resp.body() != null) {
                    resolvedUrl = resp.body()!!.livekitUrl
                    resolvedToken = resp.body()!!.token
                } else if (resp.code() in listOf(402, 403, 409)) {
                    return@withLock CallInitiationOutcome.Blocked(
                        code = "BACKEND_ACCEPT_${resp.code()}",
                        reasonArabic = "لم يعد بالإمكان قبول هذه المكالمة (انتهت أو تم إغلاقها)."
                    )
                }
            } catch (t: Throwable) {
                recordProviderIssueLocked("تعذر قبول المكالمة عبر الخادم: ${t.message}", nowMs)
            }
        }

        val existing = callDao.getCallById(invite.callId)
        val record = (existing ?: CallRecordEntity(
            callId = invite.callId,
            roomName = invite.roomName.ifBlank { "ayer_1on1_${invite.callId}" },
            callerId = invite.callerId,
            callerName = invite.callerName,
            calleeId = calleeId,
            calleeName = calleeName,
            callType = invite.callType.name,
            status = CallLifecycleState.RINGING_INCOMING.name,
            initiatedAtMs = invite.receivedAtMs,
            currencyCode = config.currencyCode
        )).copy(
            status = CallLifecycleState.ACTIVE.name,
            acceptedAtMs = nowMs
        )

        activeMonotonicAcceptMs[invite.callId] = nowMono
        callDao.upsertCallRecord(record)

        val effectiveType = if (invite.callType == CallType.VIDEO && config.disableVideoKeepAudioOnly && !config.exemptUserIds.contains(calleeId)) {
            CallType.AUDIO
        } else {
            invite.callType
        }

        CallInitiationOutcome.Ready(
            callId = record.callId,
            roomName = record.roomName,
            livekitUrl = resolvedUrl,
            token = resolvedToken,
            effectiveCallType = effectiveType,
            downgradedToAudio = (invite.callType == CallType.VIDEO && effectiveType == CallType.AUDIO),
            maxDurationSeconds = config.maxCallDurationMinutes * 60
        )
    }

    /**
     * Rejects an incoming call invitation with zero duration & zero cost.
     */
    suspend fun rejectIncomingInvite(
        invite: IncomingCallInvite,
        calleeId: String,
        calleeName: String,
        reason: String = "REJECTED_BY_CALLEE"
    ): CallRecordEntity? {
        mutex.withLock {
            val existing = callDao.getCallById(invite.callId)
            if (existing == null) {
                val cfg = callDao.getGovernanceConfigSync() ?: CallGovernanceConfigEntity()
                callDao.upsertCallRecord(
                    CallRecordEntity(
                        callId = invite.callId,
                        roomName = invite.roomName.ifBlank { "ayer_1on1_${invite.callId}" },
                        callerId = invite.callerId,
                        callerName = invite.callerName,
                        calleeId = calleeId,
                        calleeName = calleeName,
                        callType = invite.callType.name,
                        status = CallLifecycleState.RINGING_INCOMING.name,
                        initiatedAtMs = invite.receivedAtMs,
                        currencyCode = cfg.currencyCode
                    )
                )
            }
        }
        val finalized = finalizeCall(
            callId = invite.callId,
            actorId = calleeId,
            targetStatus = CallLifecycleState.REJECTED,
            reason = reason
        )
        val bearer = authTokenProvider()
        if (remoteApi != null && !bearer.isNullOrBlank()) {
            try {
                remoteApi.rejectCall(
                    bearerToken = "Bearer $bearer",
                    callId = invite.callId,
                    body = EndCallRequestDto(finalStatus = CallLifecycleState.REJECTED.name, reason = reason)
                )
            } catch (_: Throwable) {
            }
        }
        return finalized
    }

    suspend fun markConnectionState(callId: String, actorId: String, isReconnecting: Boolean) = mutex.withLock {
        val record = callDao.getCallById(callId) ?: return@withLock
        if (actorId != record.callerId && actorId != record.calleeId) return@withLock
        if (record.finalized || record.acceptedAtMs == null) return@withLock

        val nowMono = elapsedRealtimeProvider()
        if (isReconnecting) {
            if (activeReconnectStartMonotonicMs[callId] == null) {
                activeReconnectStartMonotonicMs[callId] = nowMono
                callDao.upsertCallRecord(record.copy(status = CallLifecycleState.RECONNECTING.name))
            }
        } else {
            val gapStart = activeReconnectStartMonotonicMs.remove(callId)
            val addedGap = if (gapStart != null) (nowMono - gapStart).coerceAtLeast(0L) else 0L
            callDao.upsertCallRecord(
                record.copy(
                    status = CallLifecycleState.ACTIVE.name,
                    disconnectedGapMs = record.disconnectedGapMs + addedGap
                )
            )
        }
    }

    /**
     * Idempotent call finalization.
     * Rejects unauthorized callers, ignores any client-tampered clocks via monotonic elapsed measurement,
     * guarantees 0 duration and 0 cost for unaccepted (ringing/rejected/missed) calls, and evaluates admin alerts.
     */
    suspend fun finalizeCall(
        callId: String,
        actorId: String,
        targetStatus: CallLifecycleState? = null,
        reason: String? = null,
        pointsDeducted: Int = 0
    ): CallRecordEntity? = mutex.withLock {
        val record = callDao.getCallById(callId) ?: return@withLock null
        if (actorId != record.callerId && actorId != record.calleeId && actorId != "system") {
            return@withLock null
        }
        if (record.finalized) {
            return@withLock record
        }

        val nowMs = wallClockProvider()
        val nowMono = elapsedRealtimeProvider()
        val config = callDao.getGovernanceConfigSync() ?: CallGovernanceConfigEntity()

        val reconnStart = activeReconnectStartMonotonicMs.remove(callId)
        val trailingGapMs = if (reconnStart != null) (nowMono - reconnStart).coerceAtLeast(0L) else 0L
        val totalGapMs = record.disconnectedGapMs + trailingGapMs

        val monoAccept = activeMonotonicAcceptMs.remove(callId)

        val finalRecord = if (record.acceptedAtMs == null) {
            val resolvedStatus = targetStatus ?: if (actorId == record.calleeId) {
                CallLifecycleState.REJECTED
            } else {
                CallLifecycleState.MISSED
            }
            record.copy(
                status = resolvedStatus.name,
                endedAtMs = nowMs,
                disconnectedGapMs = 0L,
                connectedDurationSeconds = 0,
                estimatedCost = 0.0,
                failureReason = reason,
                pointsDeducted = 0,
                finalized = true
            )
        } else {
            val rawElapsedMs = if (monoAccept != null) {
                (nowMono - monoAccept).coerceAtLeast(0L)
            } else {
                (nowMs - record.acceptedAtMs).coerceAtLeast(0L)
            }
            val netConnectedMs = (rawElapsedMs - totalGapMs).coerceAtLeast(0L)
            val maxAllowedMs = config.maxCallDurationMinutes * 60_000L
            val cappedMs = if (maxAllowedMs > 0L) netConnectedMs.coerceAtMost(maxAllowedMs) else netConnectedMs
            val durationSeconds = (cappedMs / 1000L).toInt()

            val ratePerParticipantMin = if (record.callType == CallType.VIDEO.name) {
                config.videoCostPerParticipantMinute
            } else {
                config.audioCostPerParticipantMinute
            }
            val participantMinutes = (durationSeconds / 60.0) * 2.0
            val estimatedCost = kotlin.math.round(participantMinutes * ratePerParticipantMin * 10000.0) / 10000.0

            record.copy(
                status = (targetStatus ?: CallLifecycleState.ENDED).name,
                endedAtMs = nowMs,
                disconnectedGapMs = totalGapMs,
                connectedDurationSeconds = durationSeconds,
                estimatedCost = estimatedCost,
                failureReason = reason,
                pointsDeducted = pointsDeducted,
                finalized = true
            )
        }

        callDao.upsertCallRecord(finalRecord)

        val bearer = authTokenProvider()
        if (remoteApi != null && !bearer.isNullOrBlank()) {
            try {
                remoteApi.endCall(
                    bearerToken = "Bearer $bearer",
                    callId = callId,
                    body = EndCallRequestDto(finalStatus = finalRecord.status, reason = reason)
                )
            } catch (_: Throwable) {
            }
        }

        val allRecords = callDao.getAllCallRecordsSync()
        val snap = buildMonitoringSnapshot(allRecords, config, emptyList(), nowMs)
        evaluateBudgetAndHealthAlertsLocked(config, snap, nowMs)

        finalRecord
    }

    suspend fun reportProviderError(message: String) = mutex.withLock {
        recordProviderIssueLocked(message, wallClockProvider())
    }

    private suspend fun recordProviderIssueLocked(message: String, nowMs: Long) {
        val cfg = callDao.getGovernanceConfigSync() ?: CallGovernanceConfigEntity()
        callDao.upsertGovernanceConfig(cfg.copy(providerHealthHealthy = false))
        emitAlertLocked(
            code = "PROVIDER_CONNECTION_ERROR",
            severity = "CRITICAL",
            title = "تنبيه مشكلة اتصال بمزود المكالمات (LiveKit)",
            message = message,
            dedupKey = "prov_${nowMs / 300_000L}",
            nowMs = nowMs
        )
    }

    private suspend fun evaluateBudgetAndHealthAlertsLocked(
        config: CallGovernanceConfigEntity,
        snap: CallMonitoringSnapshot,
        nowMs: Long
    ) {
        val mKey = monthKey(nowMs)
        val tKey = dayKey(nowMs)

        if (config.monthlyBudgetAmount > 0.0) {
            val pct = (snap.monthlyEstimatedCost / config.monthlyBudgetAmount) * 100.0
            when {
                pct >= 100.0 -> emitAlertLocked(
                    code = "BUDGET_100_STOP",
                    severity = "CRITICAL",
                    title = "استنفاد 100% من ميزانية المكالمات الشهرية",
                    message = "وصل الاستهلاك الشهري إلى ${String.format(Locale.US, "%.2f", snap.monthlyEstimatedCost)} ${config.currencyCode} من أصل ${String.format(Locale.US, "%.2f", config.monthlyBudgetAmount)}.",
                    dedupKey = "b100_$mKey",
                    nowMs = nowMs
                )
                pct >= config.alertThreshold2Percent -> emitAlertLocked(
                    code = "BUDGET_THRESHOLD_2",
                    severity = "HIGH",
                    title = "تنبيه ميزانية: الوصول إلى ${config.alertThreshold2Percent}%",
                    message = "تم استهلاك ${String.format(Locale.US, "%.1f", pct)}% من الميزانية الشهرية للمكالمات.",
                    dedupKey = "b2_${mKey}_${config.alertThreshold2Percent}",
                    nowMs = nowMs
                )
                pct >= config.alertThreshold1Percent -> emitAlertLocked(
                    code = "BUDGET_THRESHOLD_1",
                    severity = "WARNING",
                    title = "تنبيه ميزانية: الوصول إلى ${config.alertThreshold1Percent}%",
                    message = "تم استهلاك ${String.format(Locale.US, "%.1f", pct)}% من الميزانية الشهرية للمكالمات.",
                    dedupKey = "b1_${mKey}_${config.alertThreshold1Percent}",
                    nowMs = nowMs
                )
            }
        }

        if (config.monthlyVideoLimitMinutes > 0 && snap.totalVideoMinutes >= config.monthlyVideoLimitMinutes * 0.85) {
            emitAlertLocked(
                code = "APPROACHING_VIDEO_LIMIT",
                severity = "WARNING",
                title = "اقتراب الحد الشهري لمكالمات الفيديو",
                message = "استهلاك الفيديو الشهري بلغ ${String.format(Locale.US, "%.1f", snap.totalVideoMinutes)} / ${config.monthlyVideoLimitMinutes} دقيقة.",
                dedupKey = "vlim_$mKey",
                nowMs = nowMs
            )
        }

        if (snap.activeCallsCount >= config.alertMaxConcurrentCalls) {
            emitAlertLocked(
                code = "HIGH_CONCURRENCY",
                severity = "HIGH",
                title = "ارتفاع غير معتاد في المكالمات النشطة متزامناً",
                message = "عدد المكالمات النشطة حالياً بلغ ${snap.activeCallsCount}.",
                dedupKey = "conc_${tKey}_${nowMs / 600_000L}",
                nowMs = nowMs
            )
        }

        snap.topConsumingUsers.forEach { u ->
            if (u.totalMinutes >= config.alertSingleUserDailyMinutes) {
                emitAlertLocked(
                    code = "USER_EXCESSIVE_CONSUMPTION",
                    severity = "WARNING",
                    title = "استهلاك غير طبيعي من مستخدم",
                    message = "المستخدم ${u.userName} استهلك ${String.format(Locale.US, "%.1f", u.totalMinutes)} دقيقة.",
                    dedupKey = "usr_${u.userId}_$tKey",
                    nowMs = nowMs
                )
            }
        }

        val totalFinished = snap.successfulCallsCount + snap.failedCallsCount
        if (totalFinished >= 5) {
            val failRate = (snap.failedCallsCount.toDouble() / totalFinished.toDouble()) * 100.0
            if (failRate >= config.alertFailureRatePercent) {
                emitAlertLocked(
                    code = "HIGH_FAILURE_RATE",
                    severity = "CRITICAL",
                    title = "ارتفاع نسبة فشل المكالمات",
                    message = "بلغت نسبة المكالمات الفاشلة ${failRate.toInt()}%.",
                    dedupKey = "fail_${nowMs / 900_000L}",
                    nowMs = nowMs
                )
            }
        }
    }

    private suspend fun emitAlertLocked(
        code: String,
        severity: String,
        title: String,
        message: String,
        dedupKey: String,
        nowMs: Long
    ) {
        if (callDao.countAlertsByDedupKey(dedupKey) > 0) return
        val alert = CallAdminAlertEntity(
            id = "alert_${UUID.randomUUID()}",
            code = code,
            severity = severity,
            title = title,
            message = message,
            dedupKey = dedupKey,
            createdAtMs = nowMs,
            acknowledged = false
        )
        callDao.insertAlert(alert)
        notificationDao.insertNotification(
            AppNotification(
                title = "🛡️ مراقبة المكالمات: $title",
                message = message,
                type = NotificationType.SYSTEM_ALERT,
                timestamp = nowMs,
                isRead = false,
                senderName = "مركز إدارة المكالمات"
            )
        )
        onCriticalAdminAlert(alert)
    }

    fun buildMonitoringSnapshot(
        records: List<CallRecordEntity>,
        config: CallGovernanceConfigEntity,
        alerts: List<CallAdminAlertEntity>,
        nowMs: Long
    ): CallMonitoringSnapshot {
        val tKey = dayKey(nowMs)
        val mKey = monthKey(nowMs)

        val todayRecords = records.filter { dayKey(it.acceptedAtMs ?: it.initiatedAtMs) == tKey }
        val monthRecords = records.filter { monthKey(it.acceptedAtMs ?: it.initiatedAtMs) == mKey }

        val activeCount = records.count {
            it.status == CallLifecycleState.ACTIVE.name || it.status == CallLifecycleState.RECONNECTING.name
        }
        val successfulRecords = records.filter {
            it.status == CallLifecycleState.ENDED.name && it.connectedDurationSeconds > 0
        }
        val avgSec = if (successfulRecords.isNotEmpty()) {
            (successfulRecords.sumOf { it.connectedDurationSeconds } / successfulRecords.size)
        } else 0

        val totalAudioMin = records.filter { it.callType == CallType.AUDIO.name }
            .sumOf { it.connectedDurationSeconds / 60.0 }
        val totalVideoMin = records.filter { it.callType == CallType.VIDEO.name }
            .sumOf { it.connectedDurationSeconds / 60.0 }

        val todayMin = todayRecords.sumOf { it.connectedDurationSeconds / 60.0 }
        val monthMin = monthRecords.sumOf { it.connectedDurationSeconds / 60.0 }
        val dailyCost = todayRecords.sumOf { it.estimatedCost }
        val monthlyCost = monthRecords.sumOf { it.estimatedCost }

        val userAgg = mutableMapOf<String, UserCallConsumptionSummary>()
        for (rec in monthRecords) {
            val mins = rec.connectedDurationSeconds / 60.0
            val halfCost = rec.estimatedCost / 2.0
            listOf(rec.callerId to rec.callerName, rec.calleeId to rec.calleeName).forEach { (uid, uname) ->
                val prev = userAgg[uid]
                userAgg[uid] = UserCallConsumptionSummary(
                    userId = uid,
                    userName = uname,
                    totalMinutes = (prev?.totalMinutes ?: 0.0) + mins,
                    estimatedCost = (prev?.estimatedCost ?: 0.0) + halfCost,
                    callCount = (prev?.callCount ?: 0) + 1,
                    isBlocked = config.blockedUserIds.contains(uid),
                    isExempt = config.exemptUserIds.contains(uid)
                )
            }
        }

        val topUsers = userAgg.values.sortedByDescending { it.totalMinutes }.take(10)
        val budgetPct = if (config.monthlyBudgetAmount > 0.0) {
            (monthlyCost / config.monthlyBudgetAmount) * 100.0
        } else 0.0

        return CallMonitoringSnapshot(
            todayCallsCount = todayRecords.size,
            todayAudioCallsCount = todayRecords.count { it.callType == CallType.AUDIO.name },
            todayVideoCallsCount = todayRecords.count { it.callType == CallType.VIDEO.name },
            activeCallsCount = activeCount,
            totalAudioMinutes = totalAudioMin,
            totalVideoMinutes = totalVideoMin,
            averageCallDurationSeconds = avgSec,
            successfulCallsCount = successfulRecords.size,
            failedCallsCount = records.count { it.status == CallLifecycleState.FAILED.name },
            rejectedCallsCount = records.count { it.status == CallLifecycleState.REJECTED.name },
            missedCallsCount = records.count {
                it.status == CallLifecycleState.MISSED.name || it.status == CallLifecycleState.CANCELLED.name
            },
            todayConsumptionMinutes = todayMin,
            monthlyConsumptionMinutes = monthMin,
            dailyEstimatedCost = dailyCost,
            monthlyEstimatedCost = monthlyCost,
            budgetUsedPercent = budgetPct,
            currencyCode = config.currencyCode,
            topConsumingUsers = topUsers,
            callLogs = records,
            alerts = alerts,
            config = config
        )
    }
}
