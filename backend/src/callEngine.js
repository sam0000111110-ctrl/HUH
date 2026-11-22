import crypto from 'node:crypto';
import { createParticipantToken } from './livekitJwt.js';

export const CALL_TYPES = Object.freeze({
  AUDIO: 'AUDIO',
  VIDEO: 'VIDEO'
});

export const CALL_STATUS = Object.freeze({
  DIALING: 'DIALING',
  ACTIVE: 'ACTIVE',
  RECONNECTING: 'RECONNECTING',
  ENDED: 'ENDED',
  REJECTED: 'REJECTED',
  MISSED: 'MISSED',
  CANCELLED: 'CANCELLED',
  FAILED: 'FAILED',
  BLOCKED_BUDGET: 'BLOCKED_BUDGET'
});

export const TERMINAL_STATUSES = new Set([
  CALL_STATUS.ENDED,
  CALL_STATUS.REJECTED,
  CALL_STATUS.MISSED,
  CALL_STATUS.CANCELLED,
  CALL_STATUS.FAILED,
  CALL_STATUS.BLOCKED_BUDGET
]);

export const DEFAULT_GOVERNANCE_CONFIG = Object.freeze({
  monthlyBudgetAmount: 1000.0,
  currencyCode: 'EGP',
  alertThreshold1Percent: 50,
  alertThreshold2Percent: 80,
  hardStopAt100Percent: true,
  maxCallDurationMinutes: 30,
  perUserDailyCallLimitMinutes: 60,
  perUserDailyVideoLimitMinutes: 30,
  monthlyVideoLimitMinutes: 2000,
  disableVideoKeepAudioOnly: false,
  audioCostPerParticipantMinute: 0.02,
  videoCostPerParticipantMinute: 0.08,
  rateLimitCallsPerMinute: 5,
  alertFailureRatePercent: 30,
  alertMaxConcurrentCalls: 25,
  alertSingleUserDailyMinutes: 120,
  exemptUserIds: [],
  blockedUserIds: []
});

export class CallEngineError extends Error {
  constructor(code, message, statusCode = 400, details = {}) {
    super(message);
    this.name = 'CallEngineError';
    this.code = code;
    this.statusCode = statusCode;
    this.details = details;
  }
}

function getDayKey(tsMs) {
  return new Date(tsMs).toISOString().slice(0, 10);
}

function getMonthKey(tsMs) {
  return new Date(tsMs).toISOString().slice(0, 7);
}

export class AuthoritativeCallEngine {
  constructor({
    livekitUrl = 'wss://livekit.example.invalid',
    apiKey = 'dev_key',
    apiSecret = 'dev_secret_change_me',
    roomCloser = async () => ({ ok: true }),
    nowProvider = () => Date.now(),
    initialState = null
  } = {}) {
    this.livekitUrl = livekitUrl;
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.roomCloser = roomCloser;
    this.now = nowProvider;

    this.config = {
      ...DEFAULT_GOVERNANCE_CONFIG,
      ...(initialState?.config || {}),
      exemptUserIds: [...(initialState?.config?.exemptUserIds || [])],
      blockedUserIds: [...(initialState?.config?.blockedUserIds || [])]
    };
    this.calls = new Map();
    if (Array.isArray(initialState?.calls)) {
      for (const c of initialState.calls) {
        this.calls.set(c.callId, { ...c });
      }
    }
    this.alerts = Array.isArray(initialState?.alerts) ? [...initialState.alerts] : [];
    this.callLogs = Array.isArray(initialState?.callLogs) ? [...initialState.callLogs] : [];
    this.rateLimits = new Map(); // userId -> number[] timestamps
    this.providerErrors = []; // timestamps of provider issues
  }

  exportState() {
    return {
      config: this.getConfig(),
      calls: Array.from(this.calls.values()),
      callLogs: [...this.callLogs],
      alerts: [...this.alerts]
    };
  }

  getConfig() {
    return {
      ...this.config,
      exemptUserIds: [...this.config.exemptUserIds],
      blockedUserIds: [...this.config.blockedUserIds]
    };
  }

  updateConfig(patch) {
    if (!patch || typeof patch !== 'object') {
      throw new CallEngineError('INVALID_CONFIG', 'إعدادات غير صالحة', 400);
    }
    const next = { ...this.config };
    const numFields = [
      'monthlyBudgetAmount',
      'alertThreshold1Percent',
      'alertThreshold2Percent',
      'maxCallDurationMinutes',
      'perUserDailyCallLimitMinutes',
      'perUserDailyVideoLimitMinutes',
      'monthlyVideoLimitMinutes',
      'audioCostPerParticipantMinute',
      'videoCostPerParticipantMinute',
      'rateLimitCallsPerMinute',
      'alertFailureRatePercent',
      'alertMaxConcurrentCalls',
      'alertSingleUserDailyMinutes'
    ];
    for (const key of numFields) {
      if (patch[key] !== undefined) {
        const val = Number(patch[key]);
        if (!Number.isFinite(val) || val < 0) {
          throw new CallEngineError('INVALID_CONFIG', `قيمة غير صالحة للحقل ${key}`, 400);
        }
        next[key] = val;
      }
    }
    if (next.alertThreshold1Percent > next.alertThreshold2Percent) {
      throw new CallEngineError('INVALID_CONFIG', 'العتبة الأولى يجب أن تكون أقل من أو تساوي العتبة الثانية', 400);
    }
    if (patch.currencyCode !== undefined) {
      next.currencyCode = String(patch.currencyCode).trim().slice(0, 8) || 'EGP';
    }
    if (patch.hardStopAt100Percent !== undefined) {
      next.hardStopAt100Percent = Boolean(patch.hardStopAt100Percent);
    }
    if (patch.disableVideoKeepAudioOnly !== undefined) {
      next.disableVideoKeepAudioOnly = Boolean(patch.disableVideoKeepAudioOnly);
    }
    if (Array.isArray(patch.exemptUserIds)) {
      next.exemptUserIds = Array.from(new Set(patch.exemptUserIds.map(String).filter(Boolean)));
    }
    if (Array.isArray(patch.blockedUserIds)) {
      next.blockedUserIds = Array.from(new Set(patch.blockedUserIds.map(String).filter(Boolean)));
    }
    this.config = next;
    return this.getConfig();
  }

  _checkAndRecordRateLimit(userId, nowMs) {
    const windowMs = 60_000;
    const recent = (this.rateLimits.get(userId) || []).filter((t) => nowMs - t < windowMs);
    if (recent.length >= this.config.rateLimitCallsPerMinute) {
      this._emitAlert({
        code: 'ABNORMAL_USAGE_SPIKE',
        severity: 'WARNING',
        title: 'تجاوز حد طلبات إنشاء المكالمات',
        message: `المستخدم ${userId} حاول إنشاء أكثر من ${this.config.rateLimitCallsPerMinute} مكالمات خلال دقيقة واحدة.`,
        dedupKey: `rate_${userId}_${Math.floor(nowMs / 300_000)}`,
        nowMs
      });
      throw new CallEngineError(
        'RATE_LIMITED',
        'تم تجاوز الحد المسموح لمحاولات الاتصال في الدقيقة. يرجى الانتظار قليلاً.',
        429
      );
    }
    recent.push(nowMs);
    this.rateLimits.set(userId, recent);
  }

  _computeUsageStats(nowMs) {
    const todayKey = getDayKey(nowMs);
    const monthKey = getMonthKey(nowMs);

    let monthlyCost = 0;
    let dailyCost = 0;
    let monthlyVideoMinutes = 0;
    let todayTotalMinutes = 0;
    let monthlyTotalMinutes = 0;
    let totalAudioMinutes = 0;
    let totalVideoMinutes = 0;
    let activeCallsCount = 0;

    const userDailyMinutes = new Map();
    const userDailyVideoMinutes = new Map();
    const userMonthlyStats = new Map();

    for (const call of this.calls.values()) {
      const refTs = call.acceptedAt || call.initiatedAt;
      const cDay = getDayKey(refTs);
      const cMonth = getMonthKey(refTs);
      const durMin = (call.connectedDurationSeconds || 0) / 60;

      if (call.status === CALL_STATUS.ACTIVE || call.status === CALL_STATUS.RECONNECTING) {
        activeCallsCount++;
      }

      if (cMonth === monthKey) {
        monthlyCost += call.estimatedCost || 0;
        monthlyTotalMinutes += durMin;
        if (call.callType === CALL_TYPES.VIDEO) {
          monthlyVideoMinutes += durMin;
        }
        for (const [uid, uname] of [[call.callerId, call.callerName], [call.calleeId, call.calleeName]]) {
          const prev = userMonthlyStats.get(uid) || { userId: uid, userName: uname, totalMinutes: 0, estimatedCost: 0, callCount: 0 };
          prev.totalMinutes += durMin;
          prev.estimatedCost += (call.estimatedCost || 0) / 2;
          if (call.status === CALL_STATUS.ENDED || call.status === CALL_STATUS.ACTIVE) {
            prev.callCount += 1;
          }
          userMonthlyStats.set(uid, prev);
        }
      }

      if (cDay === todayKey) {
        dailyCost += call.estimatedCost || 0;
        todayTotalMinutes += durMin;
        for (const uid of [call.callerId, call.calleeId]) {
          userDailyMinutes.set(uid, (userDailyMinutes.get(uid) || 0) + durMin);
          if (call.callType === CALL_TYPES.VIDEO) {
            userDailyVideoMinutes.set(uid, (userDailyVideoMinutes.get(uid) || 0) + durMin);
          }
        }
      }

      if (call.callType === CALL_TYPES.AUDIO) {
        totalAudioMinutes += durMin;
      } else {
        totalVideoMinutes += durMin;
      }
    }

    return {
      todayKey,
      monthKey,
      monthlyCost,
      dailyCost,
      monthlyVideoMinutes,
      todayTotalMinutes,
      monthlyTotalMinutes,
      totalAudioMinutes,
      totalVideoMinutes,
      activeCallsCount,
      userDailyMinutes,
      userDailyVideoMinutes,
      userMonthlyStats
    };
  }

  _emitAlert({ code, severity, title, message, dedupKey, nowMs }) {
    if (dedupKey && this.alerts.some((a) => a.dedupKey === dedupKey)) {
      return;
    }
    this.alerts.unshift({
      id: `alert_${crypto.randomUUID()}`,
      code,
      severity,
      title,
      message,
      dedupKey,
      createdAt: nowMs,
      acknowledged: false
    });
    if (this.alerts.length > 200) {
      this.alerts.length = 200;
    }
  }

  _evaluateAlerts(nowMs) {
    const stats = this._computeUsageStats(nowMs);
    const budget = this.config.monthlyBudgetAmount;
    if (budget > 0) {
      const pct = (stats.monthlyCost / budget) * 100;
      if (pct >= 100) {
        this._emitAlert({
          code: 'BUDGET_100_STOP',
          severity: 'CRITICAL',
          title: 'تم استهلاك 100% من ميزانية المكالمات الشهرية',
          message: `وصلت التكلفة الشهرية إلى ${stats.monthlyCost.toFixed(2)} ${this.config.currencyCode} من أصل ${budget.toFixed(2)}.`,
          dedupKey: `b100_${stats.monthKey}`,
          nowMs
        });
      } else if (pct >= this.config.alertThreshold2Percent) {
        this._emitAlert({
          code: 'BUDGET_THRESHOLD_2',
          severity: 'HIGH',
          title: `تنبيه ميزانية: الوصول إلى ${this.config.alertThreshold2Percent}%`,
          message: `استهلاك الشهر الحالي بلغ ${pct.toFixed(1)}% (${stats.monthlyCost.toFixed(2)} ${this.config.currencyCode}).`,
          dedupKey: `b2_${stats.monthKey}_${this.config.alertThreshold2Percent}`,
          nowMs
        });
      } else if (pct >= this.config.alertThreshold1Percent) {
        this._emitAlert({
          code: 'BUDGET_THRESHOLD_1',
          severity: 'WARNING',
          title: `تنبيه ميزانية: الوصول إلى ${this.config.alertThreshold1Percent}%`,
          message: `استهلاك الشهر الحالي بلغ ${pct.toFixed(1)}% (${stats.monthlyCost.toFixed(2)} ${this.config.currencyCode}).`,
          dedupKey: `b1_${stats.monthKey}_${this.config.alertThreshold1Percent}`,
          nowMs
        });
      }
    }

    if (this.config.monthlyVideoLimitMinutes > 0 &&
      stats.monthlyVideoMinutes >= this.config.monthlyVideoLimitMinutes * 0.85) {
      this._emitAlert({
        code: 'APPROACHING_VIDEO_LIMIT',
        severity: 'WARNING',
        title: 'اقتراب الحد الشهري لمكالمات الفيديو',
        message: `تم استهلاك ${stats.monthlyVideoMinutes.toFixed(1)} دقيقة فيديو من الحد الشهري ${this.config.monthlyVideoLimitMinutes}.`,
        dedupKey: `vlim_${stats.monthKey}`,
        nowMs
      });
    }

    if (stats.activeCallsCount >= this.config.alertMaxConcurrentCalls) {
      this._emitAlert({
        code: 'HIGH_CONCURRENCY',
        severity: 'HIGH',
        title: 'ارتفاع غير معتاد في المكالمات المتزامنة',
        message: `عدد المكالمات النشطة حالياً وصل إلى ${stats.activeCallsCount}.`,
        dedupKey: `conc_${stats.todayKey}_${Math.floor(nowMs / 600_000)}`,
        nowMs
      });
    }

    for (const [uid, mins] of stats.userDailyMinutes.entries()) {
      if (mins >= this.config.alertSingleUserDailyMinutes) {
        this._emitAlert({
          code: 'USER_EXCESSIVE_CONSUMPTION',
          severity: 'WARNING',
          title: 'استهلاك غير طبيعي من مستخدم',
          message: `المستخدم ${uid} استهلك ${mins.toFixed(1)} دقيقة اليوم.`,
          dedupKey: `usr_${uid}_${stats.todayKey}`,
          nowMs
        });
      }
    }

    const recentCalls = Array.from(this.calls.values()).filter((c) => nowMs - c.initiatedAt <= 3_600_000);
    if (recentCalls.length >= 5) {
      const failedCount = recentCalls.filter((c) => c.status === CALL_STATUS.FAILED).length;
      const failPct = (failedCount / recentCalls.length) * 100;
      if (failPct >= this.config.alertFailureRatePercent) {
        this._emitAlert({
          code: 'HIGH_FAILURE_RATE',
          severity: 'CRITICAL',
          title: 'ارتفاع نسبة فشل المكالمات',
          message: `نسبة فشل المكالمات خلال الساعة الأخيرة بلغت ${failPct.toFixed(0)}%.`,
          dedupKey: `fail_${Math.floor(nowMs / 900_000)}`,
          nowMs
        });
      }
    }
  }

  _checkCallAllowance(callerId, calleeId, requestedType, nowMs) {
    if (this.config.blockedUserIds.includes(callerId)) {
      throw new CallEngineError('USER_CALLS_DISABLED', 'تم إيقاف صلاحية إجراء المكالمات لهذا الحساب.', 403);
    }
    if (this.config.blockedUserIds.includes(calleeId)) {
      throw new CallEngineError('CALLEE_CALLS_DISABLED', 'الطرف الآخر غير متاح لاستقبال المكالمات حالياً.', 403);
    }

    const isExempt = this.config.exemptUserIds.includes(callerId);
    const stats = this._computeUsageStats(nowMs);

    if (!isExempt && this.config.hardStopAt100Percent && this.config.monthlyBudgetAmount > 0 &&
      stats.monthlyCost >= this.config.monthlyBudgetAmount) {
      this._evaluateAlerts(nowMs);
      throw new CallEngineError(
        'BUDGET_EXCEEDED',
        'تم إيقاف إنشاء المكالمات مؤقتاً لاستهلاك 100% من الميزانية الشهرية.',
        402,
        { monthlyCost: stats.monthlyCost, monthlyBudgetAmount: this.config.monthlyBudgetAmount }
      );
    }

    let effectiveCallType = requestedType;
    let downgradedToAudio = false;

    if (requestedType === CALL_TYPES.VIDEO && this.config.disableVideoKeepAudioOnly && !isExempt) {
      effectiveCallType = CALL_TYPES.AUDIO;
      downgradedToAudio = true;
    }

    if (!isExempt && this.config.perUserDailyCallLimitMinutes > 0) {
      const usedToday = stats.userDailyMinutes.get(callerId) || 0;
      if (usedToday >= this.config.perUserDailyCallLimitMinutes) {
        throw new CallEngineError(
          'USER_DAILY_LIMIT',
          `وصلت إلى الحد اليومي للمكالمات (${this.config.perUserDailyCallLimitMinutes} دقيقة).`,
          429
        );
      }
    }

    if (effectiveCallType === CALL_TYPES.VIDEO && !isExempt) {
      const userVideoToday = stats.userDailyVideoMinutes.get(callerId) || 0;
      if (this.config.perUserDailyVideoLimitMinutes > 0 && userVideoToday >= this.config.perUserDailyVideoLimitMinutes) {
        if (this.config.disableVideoKeepAudioOnly) {
          effectiveCallType = CALL_TYPES.AUDIO;
          downgradedToAudio = true;
        } else {
          throw new CallEngineError(
            'USER_VIDEO_DAILY_LIMIT',
            `وصلت إلى الحد اليومي لمكالمات الفيديو (${this.config.perUserDailyVideoLimitMinutes} دقيقة).`,
            429
          );
        }
      }
      if (this.config.monthlyVideoLimitMinutes > 0 && stats.monthlyVideoMinutes >= this.config.monthlyVideoLimitMinutes) {
        effectiveCallType = CALL_TYPES.AUDIO;
        downgradedToAudio = true;
      }
    }

    return { effectiveCallType, downgradedToAudio };
  }

  /**
   * Creates a 1-on-1 call session in DIALING state (duration=0, cost=0).
   * Billing NEVER starts during dialing/ringing.
   */
  initiateCall({ callerId, callerName, calleeId, calleeName, callType = CALL_TYPES.AUDIO }) {
    const nowMs = this.now();
    if (!callerId || !calleeId) {
      throw new CallEngineError('INVALID_PARTICIPANTS', 'معرّف المتصل والمستقبل مطلوبان', 400);
    }
    if (callerId === calleeId) {
      throw new CallEngineError('SELF_CALL_FORBIDDEN', 'لا يمكن الاتصال بنفس الحساب', 400);
    }
    if (callType !== CALL_TYPES.AUDIO && callType !== CALL_TYPES.VIDEO) {
      throw new CallEngineError('INVALID_CALL_TYPE', 'نوع مكالمة غير مدعوم', 400);
    }

    this._checkAndRecordRateLimit(callerId, nowMs);
    const { effectiveCallType, downgradedToAudio } = this._checkCallAllowance(callerId, calleeId, callType, nowMs);

    const callId = `call_${crypto.randomUUID()}`;
    const roomName = `ayer_1on1_${callId}`;

    const canPublishSources = effectiveCallType === CALL_TYPES.VIDEO
      ? ['microphone', 'camera']
      : ['microphone'];

    const { token, expiresAtMs } = createParticipantToken({
      apiKey: this.apiKey,
      apiSecret: this.apiSecret,
      identity: callerId,
      name: callerName || callerId,
      roomName,
      ttlSeconds: Math.max(120, this.config.maxCallDurationMinutes * 60 + 60),
      canPublishSources,
      metadata: { callId, role: 'caller', callType: effectiveCallType },
      nowSec: Math.floor(nowMs / 1000)
    });

    const record = {
      callId,
      roomName,
      callerId: String(callerId),
      callerName: String(callerName || callerId),
      calleeId: String(calleeId),
      calleeName: String(calleeName || calleeId),
      callType: effectiveCallType,
      requestedCallType: callType,
      downgradedToAudio,
      status: CALL_STATUS.DIALING,
      initiatedAt: nowMs,
      acceptedAt: null,
      endedAt: null,
      reconnectingStartedAt: null,
      disconnectedGapMs: 0,
      connectedDurationSeconds: 0,
      estimatedCost: 0,
      currencyCode: this.config.currencyCode,
      failureReason: null,
      finalized: false
    };

    this.calls.set(callId, record);
    this._evaluateAlerts(nowMs);

    return {
      call: { ...record },
      livekitUrl: this.livekitUrl,
      token,
      expiresAtMs,
      maxDurationSeconds: this.config.maxCallDurationMinutes * 60
    };
  }

  /**
   * Callee accepts the call -> authoritative billing clock starts strictly NOW (acceptedAt).
   */
  acceptCall({ callId, calleeId }) {
    const nowMs = this.now();
    const call = this.calls.get(callId);
    if (!call) {
      throw new CallEngineError('CALL_NOT_FOUND', 'المكالمة غير موجودة', 404);
    }
    if (call.calleeId !== String(calleeId)) {
      throw new CallEngineError('FORBIDDEN_PARTICIPANT', 'غير مصرح لك بقبول هذه المكالمة', 403);
    }
    if (TERMINAL_STATUSES.has(call.status)) {
      throw new CallEngineError('CALL_ALREADY_ENDED', 'انتهت هذه المكالمة بالفعل', 409);
    }

    if (call.status === CALL_STATUS.DIALING) {
      call.status = CALL_STATUS.ACTIVE;
      call.acceptedAt = nowMs;
    }

    const canPublishSources = call.callType === CALL_TYPES.VIDEO
      ? ['microphone', 'camera']
      : ['microphone'];

    const { token, expiresAtMs } = createParticipantToken({
      apiKey: this.apiKey,
      apiSecret: this.apiSecret,
      identity: call.calleeId,
      name: call.calleeName,
      roomName: call.roomName,
      ttlSeconds: Math.max(120, this.config.maxCallDurationMinutes * 60 + 60),
      canPublishSources,
      metadata: { callId, role: 'callee', callType: call.callType },
      nowSec: Math.floor(nowMs / 1000)
    });

    this._evaluateAlerts(nowMs);
    return {
      call: { ...call },
      livekitUrl: this.livekitUrl,
      token,
      expiresAtMs,
      maxDurationSeconds: this.config.maxCallDurationMinutes * 60
    };
  }

  issueReconnectToken({ callId, requesterId }) {
    const nowMs = this.now();
    const call = this.calls.get(callId);
    if (!call) {
      throw new CallEngineError('CALL_NOT_FOUND', 'المكالمة غير موجودة', 404);
    }
    const uid = String(requesterId);
    if (uid !== call.callerId && uid !== call.calleeId) {
      throw new CallEngineError('FORBIDDEN_ROOM_ACCESS', 'لا تملك صلاحية الانضمام إلى غرفة هذه المكالمة', 403);
    }
    if (TERMINAL_STATUSES.has(call.status)) {
      throw new CallEngineError('CALL_ALREADY_ENDED', 'انتهت المكالمة ولا يمكن إعادة الانضمام', 409);
    }

    const canPublishSources = call.callType === CALL_TYPES.VIDEO
      ? ['microphone', 'camera']
      : ['microphone'];

    const { token, expiresAtMs } = createParticipantToken({
      apiKey: this.apiKey,
      apiSecret: this.apiSecret,
      identity: uid,
      name: uid === call.callerId ? call.callerName : call.calleeName,
      roomName: call.roomName,
      ttlSeconds: 300,
      canPublishSources,
      metadata: { callId, role: uid === call.callerId ? 'caller' : 'callee', reconnect: true },
      nowSec: Math.floor(nowMs / 1000)
    });

    return { livekitUrl: this.livekitUrl, token, expiresAtMs };
  }

  reportConnectionState({ callId, actorId, state }) {
    const nowMs = this.now();
    const call = this.calls.get(callId);
    if (!call) {
      throw new CallEngineError('CALL_NOT_FOUND', 'المكالمة غير موجودة', 404);
    }
    if (String(actorId) !== call.callerId && String(actorId) !== call.calleeId) {
      throw new CallEngineError('FORBIDDEN_PARTICIPANT', 'مشارك غير مصرح له', 403);
    }
    if (TERMINAL_STATUSES.has(call.status) || !call.acceptedAt) {
      return { ...call };
    }

    if (state === 'RECONNECTING') {
      if (call.status !== CALL_STATUS.RECONNECTING) {
        call.status = CALL_STATUS.RECONNECTING;
        call.reconnectingStartedAt = nowMs;
      }
    } else if (state === 'CONNECTED') {
      if (call.status === CALL_STATUS.RECONNECTING && call.reconnectingStartedAt != null) {
        call.disconnectedGapMs += Math.max(0, nowMs - call.reconnectingStartedAt);
        call.reconnectingStartedAt = null;
      }
      call.status = CALL_STATUS.ACTIVE;
    }
    return { ...call };
  }

  async rejectCall({ callId, actorId, reason = 'DECLINED' }) {
    const call = this.calls.get(callId);
    if (!call) {
      throw new CallEngineError('CALL_NOT_FOUND', 'المكالمة غير موجودة', 404);
    }
    if (String(actorId) !== call.calleeId && String(actorId) !== call.callerId) {
      throw new CallEngineError('FORBIDDEN_PARTICIPANT', 'مشارك غير مصرح له', 403);
    }
    const targetStatus = String(actorId) === call.calleeId ? CALL_STATUS.REJECTED : CALL_STATUS.CANCELLED;
    return this.endCall({ callId, actorId, finalStatus: targetStatus, reason });
  }

  /**
   * Idempotent call termination. Computes duration strictly from server timestamps:
   * (endedAt - acceptedAt - disconnectedGapMs). Client-sent duration is ignored.
   */
  async endCall({ callId, actorId, finalStatus = null, reason = null, systemOverride = false }) {
    const nowMs = this.now();
    const call = this.calls.get(callId);
    if (!call) {
      throw new CallEngineError('CALL_NOT_FOUND', 'المكالمة غير موجودة', 404);
    }
    if (!systemOverride && String(actorId) !== call.callerId && String(actorId) !== call.calleeId) {
      throw new CallEngineError('FORBIDDEN_PARTICIPANT', 'غير مصرح بإنهاء هذه المكالمة', 403);
    }

    if (call.finalized && TERMINAL_STATUSES.has(call.status)) {
      return { call: { ...call }, alreadyFinalized: true };
    }

    if (call.reconnectingStartedAt != null) {
      call.disconnectedGapMs += Math.max(0, nowMs - call.reconnectingStartedAt);
      call.reconnectingStartedAt = null;
    }

    call.endedAt = nowMs;

    if (!call.acceptedAt) {
      // Never connected -> 0 duration, 0 cost
      call.connectedDurationSeconds = 0;
      call.estimatedCost = 0;
      call.status = finalStatus || (String(actorId) === call.calleeId ? CALL_STATUS.REJECTED : CALL_STATUS.MISSED);
      call.failureReason = reason;
    } else {
      const rawConnectedMs = Math.max(0, nowMs - call.acceptedAt - call.disconnectedGapMs);
      const maxAllowedMs = this.config.maxCallDurationMinutes * 60 * 1000;
      const boundedMs = this.config.maxCallDurationMinutes > 0
        ? Math.min(rawConnectedMs, maxAllowedMs)
        : rawConnectedMs;
      const durationSec = Math.floor(boundedMs / 1000);
      call.connectedDurationSeconds = durationSec;

      // 2 participants in a 1-on-1 SFU room
      const ratePerParticipantMin = call.callType === CALL_TYPES.VIDEO
        ? this.config.videoCostPerParticipantMinute
        : this.config.audioCostPerParticipantMinute;
      const participantMinutes = (durationSec / 60) * 2;
      call.estimatedCost = Number((participantMinutes * ratePerParticipantMin).toFixed(4));
      call.status = finalStatus || CALL_STATUS.ENDED;
      call.failureReason = reason;
    }

    call.finalized = true;
    this._recordCompletedCallLog(call, nowMs);

    try {
      await this.roomCloser(call.roomName);
    } catch (err) {
      this.recordProviderError(err?.message || 'DeleteRoom failed');
    }

    this._evaluateAlerts(nowMs);
    return { call: { ...call }, alreadyFinalized: false };
  }

  _recordCompletedCallLog(call, nowMs) {
    if (!call?.callId || this.callLogs.some((entry) => entry.callId === call.callId)) {
      return;
    }
    const startTs = call.acceptedAt ?? call.initiatedAt;
    const endTs = call.endedAt ?? nowMs;
    const entry = {
      callId: call.callId,
      roomName: call.roomName,
      callType: call.callType,
      status: call.status,
      callerId: call.callerId,
      callerName: call.callerName,
      calleeId: call.calleeId,
      calleeName: call.calleeName,
      participants: [
        { userId: call.callerId, name: call.callerName, role: 'caller' },
        { userId: call.calleeId, name: call.calleeName, role: 'callee' }
      ],
      initiatedAt: call.initiatedAt,
      acceptedAt: call.acceptedAt,
      startedAt: startTs,
      endedAt: endTs,
      startedAtIso: new Date(startTs).toISOString(),
      endedAtIso: new Date(endTs).toISOString(),
      connectedDurationSeconds: call.connectedDurationSeconds || 0,
      disconnectedGapMs: call.disconnectedGapMs || 0,
      estimatedCost: call.estimatedCost || 0,
      currencyCode: call.currencyCode || this.config.currencyCode,
      failureReason: call.failureReason || null,
      loggedAt: nowMs
    };
    this.callLogs.unshift(entry);
    if (this.callLogs.length > 2000) {
      this.callLogs.length = 2000;
    }
  }

  getCompletedCallLogs({ limit = 200, status = null, callType = null, userId = null } = {}) {
    const safeLimit = Math.max(1, Math.min(500, Number(limit) || 200));
    let filtered = this.callLogs;
    if (status) {
      const targetStatus = String(status).trim().toUpperCase();
      filtered = filtered.filter((entry) => entry.status === targetStatus);
    }
    if (callType) {
      const targetType = String(callType).trim().toUpperCase();
      filtered = filtered.filter((entry) => entry.callType === targetType);
    }
    if (userId) {
      const targetUser = String(userId).trim();
      filtered = filtered.filter((entry) =>
        entry.callerId === targetUser ||
        entry.calleeId === targetUser ||
        (Array.isArray(entry.participants) && entry.participants.some((p) => p.userId === targetUser))
      );
    }
    return {
      total: filtered.length,
      logs: filtered.slice(0, safeLimit).map((entry) => ({
        ...entry,
        participants: Array.isArray(entry.participants) ? entry.participants.map((p) => ({ ...p })) : []
      }))
    };
  }

  /**
   * Prevents orphaned rooms when dialing times out or active calls exceed maxCallDurationMinutes.
   */
  async sweepStaleCalls({ dialingTimeoutMs = 45_000 } = {}) {
    const nowMs = this.now();
    const closed = [];
    for (const call of this.calls.values()) {
      if (TERMINAL_STATUSES.has(call.status)) continue;
      if (call.status === CALL_STATUS.DIALING && nowMs - call.initiatedAt >= dialingTimeoutMs) {
        const res = await this.endCall({
          callId: call.callId,
          actorId: 'system',
          finalStatus: CALL_STATUS.MISSED,
          reason: 'RING_TIMEOUT',
          systemOverride: true
        });
        closed.push(res.call);
      } else if (call.acceptedAt && this.config.maxCallDurationMinutes > 0) {
        const elapsedMs = nowMs - call.acceptedAt - call.disconnectedGapMs;
        if (elapsedMs >= this.config.maxCallDurationMinutes * 60 * 1000) {
          const res = await this.endCall({
            callId: call.callId,
            actorId: 'system',
            finalStatus: CALL_STATUS.ENDED,
            reason: 'MAX_DURATION_LIMIT',
            systemOverride: true
          });
          closed.push(res.call);
        }
      }
    }
    return closed;
  }

  recordProviderError(message) {
    const nowMs = this.now();
    this.providerErrors.push(nowMs);
    this._emitAlert({
      code: 'PROVIDER_CONNECTION_ERROR',
      severity: 'CRITICAL',
      title: 'تنبيه مشكلة في مزود الاتصال (LiveKit)',
      message: String(message || 'تعذر الاتصال بخادم الوسائط'),
      dedupKey: `prov_${Math.floor(nowMs / 300_000)}`,
      nowMs
    });
  }

  acknowledgeAlert(alertId) {
    const found = this.alerts.find((a) => a.id === alertId);
    if (!found) return null;
    found.acknowledged = true;
    return { ...found };
  }

  getMonitoringSnapshot() {
    const nowMs = this.now();
    const todayKey = getDayKey(nowMs);
    const stats = this._computeUsageStats(nowMs);

    const allCalls = Array.from(this.calls.values()).sort((a, b) => b.initiatedAt - a.initiatedAt);
    const todayCalls = allCalls.filter((c) => getDayKey(c.acceptedAt || c.initiatedAt) === todayKey);

    const successfulCalls = allCalls.filter((c) => c.status === CALL_STATUS.ENDED && c.connectedDurationSeconds > 0);
    const totalSuccessfulDurationSec = successfulCalls.reduce((acc, c) => acc + c.connectedDurationSeconds, 0);

    const topConsumingUsers = Array.from(stats.userMonthlyStats.values())
      .sort((a, b) => b.totalMinutes - a.totalMinutes)
      .slice(0, 10)
      .map((u) => ({
        ...u,
        totalMinutes: Number(u.totalMinutes.toFixed(2)),
        estimatedCost: Number(u.estimatedCost.toFixed(2))
      }));

    const budgetAmount = this.config.monthlyBudgetAmount;
    const budgetUsedPercent = budgetAmount > 0
      ? Number(((stats.monthlyCost / budgetAmount) * 100).toFixed(1))
      : 0;

    return {
      todayCallsCount: todayCalls.length,
      todayAudioCallsCount: todayCalls.filter((c) => c.callType === CALL_TYPES.AUDIO).length,
      todayVideoCallsCount: todayCalls.filter((c) => c.callType === CALL_TYPES.VIDEO).length,
      activeCallsCount: stats.activeCallsCount,
      totalAudioMinutes: Number(stats.totalAudioMinutes.toFixed(2)),
      totalVideoMinutes: Number(stats.totalVideoMinutes.toFixed(2)),
      averageCallDurationSeconds: successfulCalls.length > 0
        ? Math.round(totalSuccessfulDurationSec / successfulCalls.length)
        : 0,
      successfulCallsCount: successfulCalls.length,
      failedCallsCount: allCalls.filter((c) => c.status === CALL_STATUS.FAILED).length,
      rejectedCallsCount: allCalls.filter((c) => c.status === CALL_STATUS.REJECTED).length,
      missedCallsCount: allCalls.filter((c) => c.status === CALL_STATUS.MISSED || c.status === CALL_STATUS.CANCELLED).length,
      todayConsumptionMinutes: Number(stats.todayTotalMinutes.toFixed(2)),
      monthlyConsumptionMinutes: Number(stats.monthlyTotalMinutes.toFixed(2)),
      dailyEstimatedCost: Number(stats.dailyCost.toFixed(2)),
      monthlyEstimatedCost: Number(stats.monthlyCost.toFixed(2)),
      budgetUsedPercent,
      currencyCode: this.config.currencyCode,
      topConsumingUsers,
      callLogs: this.callLogs.length > 0 ? this.callLogs.slice(0, 200) : allCalls.slice(0, 200),
      completedCallsCount: this.callLogs.length,
      alerts: [...this.alerts],
      config: this.getConfig()
    };
  }
}
