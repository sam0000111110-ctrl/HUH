import crypto from 'node:crypto';
import { CallEngineError } from './callEngine.js';

export const MODERATION_STATUS = Object.freeze({
  CLEAN: 'CLEAN',
  FLAGGED: 'FLAGGED',
  BLOCKED: 'BLOCKED',
  PENDING_MANUAL_REVIEW: 'PENDING_MANUAL_REVIEW',
  REVIEWED_APPROVED: 'REVIEWED_APPROVED',
  REVIEWED_CONFIRMED: 'REVIEWED_CONFIRMED'
});

const LIKELIHOOD_SCORE = Object.freeze({
  UNKNOWN: 0,
  VERY_UNLIKELY: 0.05,
  UNLIKELY: 0.2,
  POSSIBLE: 0.5,
  LIKELY: 0.78,
  VERY_LIKELY: 0.96
});

const PROHIBITED_TEXT_SIGNALS = [
  'nude', 'nudity', 'porn', 'xxx', 'explicit_nsfw', 'sexual_act',
  'csam', 'minor_sexual', 'self_harm', 'suicide', 'gore', 'violence_extreme',
  'عاري', 'اباحي', 'إباحي', 'عنف_دموي', 'انتحار'
];

export class VisionSafeSearchProvider {
  constructor({ apiKey = '', fetchImpl = globalThis.fetch } = {}) {
    this.name = 'vision_safesearch';
    this.estimatedCostPerSampleUsd = 0.0015;
    this.apiKey = apiKey;
    this.fetchImpl = fetchImpl;
  }

  async inspect({ imageBase64 = null, imageUrl = null }) {
    if (!this.apiKey) {
      throw new Error('GOOGLE_VISION_API_KEY is not configured');
    }
    if (!imageBase64 && !imageUrl) {
      return { verdict: MODERATION_STATUS.CLEAN, riskScore: 0, labels: [], details: 'NO_IMAGE_PAYLOAD' };
    }
    const imageSpec = imageBase64
      ? { content: String(imageBase64).replace(/^data:image\/\w+;base64,/, '') }
      : { source: { imageUri: imageUrl } };

    const res = await this.fetchImpl(
      `https://vision.googleapis.com/v1/images:annotate?key=${encodeURIComponent(this.apiKey)}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          requests: [
            {
              image: imageSpec,
              features: [{ type: 'SAFE_SEARCH_DETECTION' }]
            }
          ]
        })
      }
    );
    if (!res.ok) {
      throw new Error(`Google Vision API error: ${res.status}`);
    }
    const data = await res.json();
    const annotation = data?.responses?.[0]?.safeSearchAnnotation;
    if (!annotation) {
      throw new Error('Missing safeSearchAnnotation in Google Vision response');
    }

    const categories = ['adult', 'violence', 'racy', 'medical', 'spoof'];
    const labels = [];
    let maxScore = 0;
    for (const cat of categories) {
      const level = String(annotation[cat] || 'UNKNOWN').toUpperCase();
      const score = LIKELIHOOD_SCORE[level] ?? 0;
      if (score >= 0.5 && (cat === 'adult' || cat === 'violence' || cat === 'racy')) {
        labels.push(`${cat}:${level}`);
      }
      if (cat === 'adult' || cat === 'violence') {
        maxScore = Math.max(maxScore, score);
      } else if (cat === 'racy') {
        maxScore = Math.max(maxScore, score * 0.85);
      }
    }

    const verdict = maxScore >= 0.85
      ? MODERATION_STATUS.BLOCKED
      : maxScore >= 0.5
        ? MODERATION_STATUS.FLAGGED
        : MODERATION_STATUS.CLEAN;

    return { verdict, riskScore: Number(maxScore.toFixed(3)), labels, details: JSON.stringify(annotation) };
  }
}

export class SightengineProvider {
  constructor({ apiUser = '', apiSecret = '', fetchImpl = globalThis.fetch } = {}) {
    this.name = 'sightengine';
    this.estimatedCostPerSampleUsd = 0.002;
    this.apiUser = apiUser;
    this.apiSecret = apiSecret;
    this.fetchImpl = fetchImpl;
  }

  async inspect({ imageUrl = null, imageBase64 = null }) {
    if (!this.apiUser || !this.apiSecret) {
      throw new Error('SIGHTENGINE credentials not configured');
    }
    if (!imageUrl && !imageBase64) {
      return { verdict: MODERATION_STATUS.CLEAN, riskScore: 0, labels: [], details: 'NO_IMAGE_PAYLOAD' };
    }
    const form = new URLSearchParams({
      models: 'nudity-2.1,wad,offensive',
      api_user: this.apiUser,
      api_secret: this.apiSecret
    });
    if (imageUrl) form.set('url', imageUrl);
    if (imageBase64 && !imageUrl) form.set('media', imageBase64);

    const res = await this.fetchImpl('https://api.sightengine.com/1.0/check.json', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: form.toString()
    });
    if (!res.ok) {
      throw new Error(`Sightengine error: ${res.status}`);
    }
    const data = await res.json();
    const sexualActivity = Number(data?.nudity?.sexual_activity || 0);
    const sexualDisplay = Number(data?.nudity?.sexual_display || 0);
    const violence = Number(data?.violence?.prob || 0);
    const weapon = Number(data?.weapon || 0);
    const riskScore = Math.max(sexualActivity, sexualDisplay, violence, weapon);
    const labels = [];
    if (sexualActivity >= 0.5 || sexualDisplay >= 0.5) labels.push('nudity');
    if (violence >= 0.5) labels.push('violence');
    if (weapon >= 0.5) labels.push('weapon');

    const verdict = riskScore >= 0.85
      ? MODERATION_STATUS.BLOCKED
      : riskScore >= 0.5
        ? MODERATION_STATUS.FLAGGED
        : MODERATION_STATUS.CLEAN;
    return { verdict, riskScore: Number(riskScore.toFixed(3)), labels, details: 'sightengine_v1' };
  }
}

export class OpenAIModerationProvider {
  constructor({ apiKey = '', fetchImpl = globalThis.fetch } = {}) {
    this.name = 'openai_moderation';
    this.estimatedCostPerSampleUsd = 0.0005;
    this.apiKey = apiKey;
    this.fetchImpl = fetchImpl;
  }

  async inspect({ textContent = '', imageUrl = null }) {
    if (!this.apiKey) {
      throw new Error('OPENAI_API_KEY is not configured');
    }
    const inputs = [];
    if (textContent) inputs.push({ type: 'text', text: String(textContent).slice(0, 2000) });
    if (imageUrl) inputs.push({ type: 'image_url', image_url: { url: imageUrl } });
    if (inputs.length === 0) {
      return { verdict: MODERATION_STATUS.CLEAN, riskScore: 0, labels: [], details: 'EMPTY_SAMPLE' };
    }
    const res = await this.fetchImpl('https://api.openai.com/v1/moderations', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${this.apiKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ model: 'omni-moderation-latest', input: inputs })
    });
    if (!res.ok) {
      throw new Error(`OpenAI moderation error: ${res.status}`);
    }
    const data = await res.json();
    const first = data?.results?.[0] || {};
    const scores = Object.values(first.category_scores || {}).map(Number);
    const maxScore = scores.length > 0 ? Math.max(...scores) : 0;
    const labels = Object.entries(first.categories || {})
      .filter(([, flagged]) => Boolean(flagged))
      .map(([cat]) => cat);
    const verdict = first.flagged
      ? (maxScore >= 0.85 ? MODERATION_STATUS.BLOCKED : MODERATION_STATUS.FLAGGED)
      : MODERATION_STATUS.CLEAN;
    return { verdict, riskScore: Number(maxScore.toFixed(3)), labels, details: 'openai_omni_moderation' };
  }
}

export class LocalHeuristicModerationProvider {
  constructor() {
    this.name = 'heuristic_local';
    this.estimatedCostPerSampleUsd = 0;
  }

  async inspect({ textContent = '', imageBase64 = null, clientSignalHint = null }) {
    const combined = `${String(textContent || '')} ${String(clientSignalHint || '')}`.toLowerCase();
    const matched = PROHIBITED_TEXT_SIGNALS.filter((kw) => combined.includes(kw));
    if (matched.length > 0) {
      const critical = matched.some((m) => m.includes('csam') || m.includes('minor') || m.includes('self_harm') || m.includes('porn') || m.includes('إباحي'));
      return {
        verdict: critical ? MODERATION_STATUS.BLOCKED : MODERATION_STATUS.FLAGGED,
        riskScore: critical ? 0.95 : 0.72,
        labels: matched,
        details: 'LOCAL_HEURISTIC_KEYWORD_MATCH'
      };
    }
    // Check image base64 size/sanity only (never fabricates computer vision results)
    if (imageBase64 && String(imageBase64).length > 0) {
      return {
        verdict: MODERATION_STATUS.CLEAN,
        riskScore: 0.05,
        labels: [],
        details: 'LOCAL_HEURISTIC_UNVERIFIED_EXTERNAL_KEY_UNSET'
      };
    }
    return {
      verdict: MODERATION_STATUS.CLEAN,
      riskScore: 0,
      labels: [],
      details: 'LOCAL_HEURISTIC_CLEAN'
    };
  }
}

export function resolveModerationProvider({ env = process.env, fetchImpl = globalThis.fetch, customProvider = null } = {}) {
  if (customProvider) return customProvider;
  const preferred = String(env.MODERATION_PROVIDER || '').trim().toLowerCase();
  if (preferred === 'vision_safesearch' || (!preferred && env.GOOGLE_VISION_API_KEY)) {
    return new VisionSafeSearchProvider({ apiKey: env.GOOGLE_VISION_API_KEY || '', fetchImpl });
  }
  if (preferred === 'sightengine' || (!preferred && env.SIGHTENGINE_API_USER && env.SIGHTENGINE_API_SECRET)) {
    return new SightengineProvider({
      apiUser: env.SIGHTENGINE_API_USER || '',
      apiSecret: env.SIGHTENGINE_API_SECRET || '',
      fetchImpl
    });
  }
  if (preferred === 'openai_moderation' || (!preferred && env.OPENAI_API_KEY)) {
    return new OpenAIModerationProvider({ apiKey: env.OPENAI_API_KEY || '', fetchImpl });
  }
  return new LocalHeuristicModerationProvider();
}

export class ModerationService {
  constructor({
    env = process.env,
    fetchImpl = globalThis.fetch,
    provider = null,
    nowProvider = () => Date.now()
  } = {}) {
    this.env = env;
    this.now = nowProvider;
    this.provider = resolveModerationProvider({ env, fetchImpl, customProvider: provider });
    this.dailyBudgetUsd = Math.max(0, Number(env.MODERATION_DAILY_BUDGET_USD ?? 5));
    this.dailyMaxSamples = Math.max(1, Number(env.MODERATION_DAILY_MAX_SAMPLES ?? 3300));
    this.sampleIntervalSeconds = Math.max(10, Number(env.MODERATION_SAMPLE_INTERVAL_SEC ?? 45));
  }

  async evaluateAndRecordSample({
    ledger,
    userUid,
    streamId = null,
    sampleType = 'STREAM_FRAME',
    imageBase64 = null,
    imageUrl = null,
    textContent = '',
    clientSignalHint = null
  }) {
    if (!userUid) {
      throw new CallEngineError('MISSING_UID', 'معرف المستخدم مطلوب للفحص الآلي', 400);
    }

    const nowMs = this.now();
    const dayKey = new Date(nowMs).toISOString().slice(0, 10);
    const usage = await ledger.getModerationDailyUsage(dayKey);
    const estimatedCost = Number(this.provider.estimatedCostPerSampleUsd || 0);

    const budgetExceeded =
      usage.sampleCount >= this.dailyMaxSamples ||
      (this.dailyBudgetUsd > 0 && estimatedCost > 0 && usage.estimatedCostUsd + estimatedCost > this.dailyBudgetUsd);

    let result;
    let providerName = this.provider.name || 'unknown';
    let billedCost = estimatedCost;
    let fallbackReason = null;

    if (budgetExceeded) {
      providerName = 'manual_fallback';
      billedCost = 0;
      fallbackReason = 'DAILY_MODERATION_BUDGET_OR_SAMPLE_CAP_REACHED';
      result = {
        verdict: MODERATION_STATUS.PENDING_MANUAL_REVIEW,
        riskScore: 0.5,
        labels: ['BUDGET_CAP_FALLBACK'],
        details: fallbackReason
      };
    } else {
      try {
        result = await this.provider.inspect({
          imageBase64,
          imageUrl,
          textContent,
          clientSignalHint,
          streamId,
          userUid
        });
      } catch (err) {
        providerName = 'manual_fallback';
        billedCost = 0;
        fallbackReason = `PROVIDER_ERROR:${err?.message || 'UNKNOWN'}`;
        result = {
          verdict: MODERATION_STATUS.PENDING_MANUAL_REVIEW,
          riskScore: 0.5,
          labels: ['PROVIDER_FALLBACK'],
          details: fallbackReason
        };
      }
    }

    const status = result.verdict || MODERATION_STATUS.PENDING_MANUAL_REVIEW;
    const sampleId = `mod_${crypto.randomUUID()}`;
    const shouldTerminateStream = Boolean(streamId && status === MODERATION_STATUS.BLOCKED);
    const actionTaken = shouldTerminateStream
      ? 'AUTO_STOP_STREAM_AND_FLAG'
      : (status === MODERATION_STATUS.FLAGGED || status === MODERATION_STATUS.PENDING_MANUAL_REVIEW)
        ? 'QUEUED_FOR_MANUAL_REVIEW'
        : 'NONE';

    const recorded = await ledger.createModerationSample({
      id: sampleId,
      streamId,
      userUid,
      sampleType,
      provider: providerName,
      status,
      riskScore: Number(result.riskScore || 0),
      labels: Array.isArray(result.labels) ? result.labels : [],
      estimatedCostUsd: billedCost,
      actionTaken,
      details: String(result.details || ''),
      dayKey,
      incrementUsage: !budgetExceeded && !fallbackReason
    });

    return {
      sample: recorded,
      shouldTerminateStream,
      shouldQueueManualReview: status !== MODERATION_STATUS.CLEAN,
      fallbackReason,
      dailyUsage: await ledger.getModerationDailyUsage(dayKey),
      sampleIntervalSeconds: this.sampleIntervalSeconds
    };
  }
}
