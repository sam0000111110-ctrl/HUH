import crypto from 'node:crypto';
import { createParticipantToken } from './livekitJwt.js';
import { CallEngineError } from './callEngine.js';

export const MATCH_COSTS = Object.freeze({
  VIDEO: 3,
  VOICE: 2,
  TEXT: 1
});

export function calculateMatchCost({ chatType = 'VIDEO', genderFilter = 'ANY', countryFilter = 'ANY' }) {
  const normalizedType = String(chatType || 'VIDEO').toUpperCase();
  const base = MATCH_COSTS[normalizedType] ?? MATCH_COSTS.VIDEO;
  let cost = base;
  if (genderFilter && genderFilter !== 'ANY') cost += 2;
  if (countryFilter && countryFilter !== 'ANY') cost += 2;
  return cost;
}

export class RealtimeBroadcastAndMatchEngine {
  constructor({
    livekitUrl = '',
    apiKey = '',
    apiSecret = '',
    roomCloser = async () => ({ ok: true }),
    nowProvider = () => Date.now()
  } = {}) {
    this.livekitUrl = livekitUrl;
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.roomCloser = roomCloser;
    this.now = nowProvider;

    this.matchQueue = new Map(); // uid -> queue entry
    this.activeMatches = new Map(); // matchId -> match object
    this.userActiveMatch = new Map(); // uid -> { matchId, partner, token, expiresAtMs, chatType, roomName }
    this.streams = new Map(); // streamId -> stream object
  }

  _mintToken({ identity, name, roomName, canPublish, canSubscribe = true, metadata = {} }) {
    if (!this.apiKey || !this.apiSecret) {
      return {
        token: `pending_lk_${crypto.randomUUID().slice(0, 12)}`,
        expiresAtMs: this.now() + 300_000,
        livekitConfigured: false
      };
    }
    const issued = createParticipantToken({
      apiKey: this.apiKey,
      apiSecret: this.apiSecret,
      identity,
      name,
      roomName,
      ttlSeconds: 3600,
      canPublish,
      canSubscribe,
      canPublishData: true,
      metadata,
      nowSec: Math.floor(this.now() / 1000)
    });
    return {
      ...issued,
      livekitConfigured: true
    };
  }

  _pruneStaleQueue(ttlMs = 90_000) {
    const cutoff = this.now() - ttlMs;
    for (const [uid, entry] of this.matchQueue.entries()) {
      if (entry.lastHeartbeatMs < cutoff) {
        this.matchQueue.delete(uid);
      }
    }
  }

  _areCompatible(a, b) {
    if (!a || !b || a.uid === b.uid) return false;
    if (a.blockedUids.has(b.uid) || b.blockedUids.has(a.uid)) return false;
    if (a.chatType !== b.chatType) return false;

    if (a.genderFilter !== 'ANY' && b.userGender && b.userGender !== 'ANY' && b.userGender !== a.genderFilter) {
      return false;
    }
    if (b.genderFilter !== 'ANY' && a.userGender && a.userGender !== 'ANY' && a.userGender !== b.genderFilter) {
      return false;
    }
    if (a.countryFilter !== 'ANY' && b.countryCode && b.countryCode !== a.countryFilter) {
      return false;
    }
    if (b.countryFilter !== 'ANY' && a.countryCode && a.countryCode !== b.countryFilter) {
      return false;
    }
    return true;
  }

  joinMatchQueue({
    uid,
    displayName,
    country = '',
    countryCode = 'SA',
    userGender = 'ANY',
    age = 21,
    bio = '',
    chatType = 'VIDEO',
    genderFilter = 'ANY',
    countryFilter = 'ANY',
    blockedUids = []
  }) {
    if (!uid) {
      throw new CallEngineError('MISSING_UID', 'معرّف المستخدم مطلوب', 400);
    }
    this._pruneStaleQueue();

    // If user already matched while waiting, return that match directly
    const existingMatch = this.userActiveMatch.get(uid);
    if (existingMatch) {
      const m = this.activeMatches.get(existingMatch.matchId);
      if (m && m.status === 'ACTIVE') {
        return {
          status: 'MATCHED',
          ...existingMatch
        };
      }
      this.userActiveMatch.delete(uid);
    }

    const normalizedType = String(chatType || 'VIDEO').toUpperCase();
    const cost = calculateMatchCost({ chatType: normalizedType, genderFilter, countryFilter });
    const callerEntry = {
      uid,
      displayName: String(displayName || 'مستخدم HUH').trim() || 'مستخدم HUH',
      country: String(country || '').trim(),
      countryCode: String(countryCode || 'SA').toUpperCase(),
      userGender: String(userGender || 'ANY').toUpperCase(),
      age: Number(age || 21),
      bio: String(bio || '').trim(),
      chatType: normalizedType,
      genderFilter: String(genderFilter || 'ANY').toUpperCase(),
      countryFilter: String(countryFilter || 'ANY').toUpperCase(),
      cost,
      blockedUids: new Set(Array.isArray(blockedUids) ? blockedUids : []),
      joinedAtMs: this.now(),
      lastHeartbeatMs: this.now()
    };

    for (const [partnerUid, candidate] of this.matchQueue.entries()) {
      if (this._areCompatible(callerEntry, candidate)) {
        this.matchQueue.delete(partnerUid);
        this.matchQueue.delete(uid);

        const matchId = `match_${crypto.randomUUID().slice(0, 12)}`;
        const roomName = `huh_match_${matchId}`;
        const canMedia = normalizedType !== 'TEXT';

        const callerToken = canMedia
          ? this._mintToken({
              identity: uid,
              name: callerEntry.displayName,
              roomName,
              canPublish: true,
              canSubscribe: true,
              metadata: { matchId, chatType: normalizedType }
            })
          : { token: '', expiresAtMs: this.now() + 300_000, livekitConfigured: true };

        const partnerToken = canMedia
          ? this._mintToken({
              identity: partnerUid,
              name: candidate.displayName,
              roomName,
              canPublish: true,
              canSubscribe: true,
              metadata: { matchId, chatType: normalizedType }
            })
          : { token: '', expiresAtMs: this.now() + 300_000, livekitConfigured: true };

        const matchRecord = {
          matchId,
          roomName,
          chatType: normalizedType,
          participantIds: [uid, partnerUid],
          status: 'ACTIVE',
          createdAtMs: this.now(),
          costs: {
            [uid]: callerEntry.cost,
            [partnerUid]: candidate.cost
          }
        };
        this.activeMatches.set(matchId, matchRecord);

        const callerPayload = {
          matchId,
          roomName,
          livekitUrl: this.livekitUrl,
          token: callerToken.token,
          expiresAtMs: callerToken.expiresAtMs,
          chatType: normalizedType,
          costCoins: callerEntry.cost,
          partner: {
            uid: candidate.uid,
            name: candidate.displayName,
            age: candidate.age,
            country: candidate.country,
            countryCode: candidate.countryCode,
            gender: candidate.userGender,
            bio: candidate.bio
          }
        };

        const partnerPayload = {
          matchId,
          roomName,
          livekitUrl: this.livekitUrl,
          token: partnerToken.token,
          expiresAtMs: partnerToken.expiresAtMs,
          chatType: normalizedType,
          costCoins: candidate.cost,
          partner: {
            uid: callerEntry.uid,
            name: callerEntry.displayName,
            age: callerEntry.age,
            country: callerEntry.country,
            countryCode: callerEntry.countryCode,
            gender: callerEntry.userGender,
            bio: callerEntry.bio
          }
        };

        this.userActiveMatch.set(uid, callerPayload);
        this.userActiveMatch.set(partnerUid, partnerPayload);

        return {
          status: 'MATCHED',
          newlyMatched: true,
          partnerUid,
          partnerCostCoins: candidate.cost,
          ...callerPayload
        };
      }
    }

    this.matchQueue.set(uid, callerEntry);
    return {
      status: 'WAITING',
      queueSize: this.matchQueue.size,
      waitSeconds: 0
    };
  }

  pollMatchQueue(uid) {
    this._pruneStaleQueue();
    const active = this.userActiveMatch.get(uid);
    if (active) {
      const m = this.activeMatches.get(active.matchId);
      if (m && m.status === 'ACTIVE') {
        return {
          status: 'MATCHED',
          ...active
        };
      }
      this.userActiveMatch.delete(uid);
    }
    const waiting = this.matchQueue.get(uid);
    if (!waiting) {
      return {
        status: 'IDLE',
        queueSize: this.matchQueue.size,
        waitSeconds: 0
      };
    }
    waiting.lastHeartbeatMs = this.now();
    return {
      status: 'WAITING',
      queueSize: this.matchQueue.size,
      waitSeconds: Math.max(0, Math.floor((this.now() - waiting.joinedAtMs) / 1000))
    };
  }

  async leaveMatchQueueOrSession({ uid, matchId = null }) {
    this.matchQueue.delete(uid);
    const active = this.userActiveMatch.get(uid);
    const targetMatchId = matchId || active?.matchId;
    if (targetMatchId) {
      const m = this.activeMatches.get(targetMatchId);
      if (m && m.participantIds.includes(uid)) {
        m.status = 'ENDED';
        m.endedAtMs = this.now();
        for (const pUid of m.participantIds) {
          this.userActiveMatch.delete(pUid);
        }
        await Promise.resolve(this.roomCloser(m.roomName)).catch(() => {});
      }
    } else {
      this.userActiveMatch.delete(uid);
    }
    return { status: 'IDLE' };
  }

  // ==========================================================
  // LiveKit Broadcast Rooms Registry
  // ==========================================================

  startBroadcast({
    hostUid,
    hostName,
    hostCountry = '',
    hostCountryCode = 'SA',
    title,
    category = 'تعارف وسوالف',
    agencyName = null,
    isVip = false
  }) {
    if (!hostUid) {
      throw new CallEngineError('MISSING_HOST', 'معرّف المضيف مطلوب لبدء البث', 400);
    }
    const cleanTitle = String(title || '').trim();
    if (!cleanTitle || cleanTitle.length > 140) {
      throw new CallEngineError('INVALID_TITLE', 'عنوان البث مطلوب ولا يتجاوز 140 حرفاً', 400);
    }

    // End any previous open broadcast by the same host so no ghost stream remains
    for (const s of this.streams.values()) {
      if (s.hostUid === hostUid && s.status === 'LIVE') {
        s.status = 'ENDED';
        s.endedAtMs = this.now();
        Promise.resolve(this.roomCloser(s.roomName)).catch(() => {});
      }
    }

    const id = `stream_${crypto.randomUUID().slice(0, 12)}`;
    const roomName = `huh_live_${id}`;
    const stream = {
      id,
      roomName,
      hostUid,
      hostName: String(hostName || 'مضيف HUH').trim() || 'مضيف HUH',
      hostCountry: String(hostCountry || '').trim(),
      hostCountryCode: String(hostCountryCode || 'SA').toUpperCase(),
      title: cleanTitle,
      category: String(category || 'تعارف وسوالف').trim(),
      agencyName: agencyName || null,
      isAgencyStream: Boolean(agencyName),
      isVip: Boolean(isVip),
      diamondsReceived: 0,
      status: 'LIVE',
      startedAtMs: this.now(),
      viewerUids: new Set([hostUid]),
      blockedViewerUids: new Set()
    };
    this.streams.set(id, stream);

    const publisherToken = this._mintToken({
      identity: hostUid,
      name: stream.hostName,
      roomName,
      canPublish: true,
      canSubscribe: true,
      canPublishData: true,
      metadata: { streamId: id, role: 'HOST' }
    });

    return {
      stream: this._serializeStream(stream),
      livekitUrl: this.livekitUrl,
      token: publisherToken.token,
      expiresAtMs: publisherToken.expiresAtMs,
      role: 'PUBLISHER'
    };
  }

  _serializeStream(stream) {
    return {
      id: stream.id,
      roomName: stream.roomName,
      hostUid: stream.hostUid,
      hostName: stream.hostName,
      hostCountry: stream.hostCountry,
      hostCountryCode: stream.hostCountryCode,
      title: stream.title,
      category: stream.category,
      agencyName: stream.agencyName,
      isAgencyStream: stream.isAgencyStream,
      isVip: stream.isVip,
      viewersCount: stream.viewerUids.size,
      diamondsReceived: stream.diamondsReceived,
      status: stream.status,
      startedAtMs: stream.startedAtMs
    };
  }

  listLiveStreams({ category = null, blockedUids = [] } = {}) {
    const blocked = new Set(Array.isArray(blockedUids) ? blockedUids : []);
    const active = [];
    for (const stream of this.streams.values()) {
      if (stream.status !== 'LIVE') continue;
      if (blocked.has(stream.hostUid)) continue;
      if (category && category !== 'ALL' && category !== 'الكل' && !stream.category.toLowerCase().includes(String(category).toLowerCase())) {
        continue;
      }
      active.push(this._serializeStream(stream));
    }
    return active.sort((a, b) => b.viewersCount - a.viewersCount || b.startedAtMs - a.startedAtMs);
  }

  getStream(streamId) {
    const stream = this.streams.get(streamId);
    return stream && stream.status === 'LIVE' ? stream : null;
  }

  joinBroadcast({ streamId, viewerUid, viewerName, blockedUids = [] }) {
    const stream = this.streams.get(streamId);
    if (!stream || stream.status !== 'LIVE') {
      throw new CallEngineError('STREAM_NOT_FOUND', 'هذا البث المباشر غير نشط حالياً', 404);
    }
    const blockedSet = new Set(Array.isArray(blockedUids) ? blockedUids : []);
    if (blockedSet.has(stream.hostUid) || stream.blockedViewerUids.has(viewerUid)) {
      throw new CallEngineError('USER_BLOCKED', 'لا يمكن الانضمام لهذا البث بسبب قيود الحظر', 403);
    }

    const isHost = stream.hostUid === viewerUid;
    stream.viewerUids.add(viewerUid);

    const tokenObj = this._mintToken({
      identity: viewerUid,
      name: String(viewerName || 'مشاهد HUH'),
      roomName: stream.roomName,
      canPublish: isHost,
      canSubscribe: true,
      canPublishData: true,
      metadata: { streamId, role: isHost ? 'HOST' : 'VIEWER' }
    });

    return {
      stream: this._serializeStream(stream),
      livekitUrl: this.livekitUrl,
      token: tokenObj.token,
      expiresAtMs: tokenObj.expiresAtMs,
      role: isHost ? 'PUBLISHER' : 'SUBSCRIBER'
    };
  }

  leaveBroadcast({ streamId, viewerUid }) {
    const stream = this.streams.get(streamId);
    if (!stream) return { ok: true };
    if (stream.hostUid === viewerUid) {
      return this.endBroadcast({ streamId, actorUid: viewerUid, reason: 'HOST_LEFT' });
    }
    stream.viewerUids.delete(viewerUid);
    return { stream: this._serializeStream(stream) };
  }

  async endBroadcast({ streamId, actorUid, isAdmin = false, systemOverride = false, reason = 'HOST_ENDED' }) {
    const stream = this.streams.get(streamId);
    if (!stream) {
      throw new CallEngineError('STREAM_NOT_FOUND', 'البث غير موجود', 404);
    }
    if (!systemOverride && !isAdmin && stream.hostUid !== actorUid) {
      throw new CallEngineError('FORBIDDEN', 'فقط مضيف البث أو الإدارة يمكنه إغلاق الغرفة', 403);
    }
    if (stream.status === 'LIVE') {
      stream.status = 'ENDED';
      stream.endedAtMs = this.now();
      stream.endReason = reason;
      await Promise.resolve(this.roomCloser(stream.roomName)).catch(() => {});
    }
    return { stream: this._serializeStream(stream) };
  }

  recordStreamGift({ streamId, coinsCost }) {
    const stream = this.streams.get(streamId);
    if (stream && stream.status === 'LIVE') {
      stream.diamondsReceived += Number(coinsCost || 0);
      return this._serializeStream(stream);
    }
    return null;
  }

  async handleLiveKitWebhook({ eventName, roomName, participantIdentity = null }) {
    if (!roomName) return false;
    // Check live broadcast rooms
    for (const stream of this.streams.values()) {
      if (stream.roomName === roomName && stream.status === 'LIVE') {
        if (eventName === 'room_finished') {
          await this.endBroadcast({ streamId: stream.id, actorUid: 'livekit_webhook', systemOverride: true, reason: 'WEBHOOK_ROOM_FINISHED' });
          return true;
        }
        if (eventName === 'participant_left' && participantIdentity) {
          if (participantIdentity === stream.hostUid) {
            await this.endBroadcast({ streamId: stream.id, actorUid: 'livekit_webhook', systemOverride: true, reason: 'WEBHOOK_HOST_LEFT' });
          } else {
            stream.viewerUids.delete(participantIdentity);
          }
          return true;
        }
      }
    }
    // Check match rooms
    for (const match of this.activeMatches.values()) {
      if (match.roomName === roomName && match.status === 'ACTIVE') {
        if (eventName === 'room_finished' || eventName === 'participant_left') {
          match.status = 'ENDED';
          match.endedAtMs = this.now();
          for (const pUid of match.participantIds) {
            this.userActiveMatch.delete(pUid);
          }
          await Promise.resolve(this.roomCloser(match.roomName)).catch(() => {});
          return true;
        }
      }
    }
    return false;
  }
}
