# سجل تغييرات واجهة برمجة التطبيقات (API Changes Log)

يُسجَّل في هذا الملف كل تعديل على عقود Backend API بين الخادم وتطبيق Android مع تحديث التطبيق واختباراته بالتوازي.

---

## WP-0 — أقفال الأمان الفورية وصدق الواجهة (Feature Flags & Fail-Closed)

### نقاط النهاية والعقود الجديدة/المحدثة
1. **`GET /api/config/flags`** *(جديد — قراءة عامة)*
   - **الاستجابة (`200 OK`):**
     ```json
     {
       "payoutsEnabled": false,
       "missionRewardsEnabled": false,
       "paidMediaEnabled": false,
       "agenciesEnabled": false
     }
     ```
2. **`GET /health`، `POST /api/auth/sync`، `GET /api/wallet/summary`** *(محدثة مع توافق عكسي)*
   - أُضيف حقل `featureFlags` بالكائن نفسه (`payoutsEnabled`, `missionRewardsEnabled`, `paidMediaEnabled`, `agenciesEnabled`) إلى استجابة `200 OK`.
3. **إغلاق المسارات المالية غير المكتملة افتراضياً (`503 Service Unavailable — FEATURE_DISABLED`):**
   - `PAYOUTS_ENABLED=false` يغلق: `POST /api/payouts/request`، `GET /api/payouts`، `GET /api/admin/payouts`، `POST /api/admin/payouts/:id/review`.
   - `MISSION_REWARDS_ENABLED=false` يغلق: `POST /api/wallet/mission-reward`.
   - `AGENCIES_ENABLED=false` يغلق: `GET /api/agency/dashboard`، `POST /api/host/apply`، `POST /api/agency/sub-agents`.
   - `PAID_MEDIA_ENABLED=false` يغلق: `POST /api/wallet/gift` عند إرسال `giftId` يبدأ بـ `media_` أو `isPaidMedia=true`.
   - يظل تفعيلها محصوراً بمتغيرات البيئة الصريحة (`=true`) بعد اجتياز بوابات WP-1..WP-4.

### توافق تطبيق Android
- تمت إضافة `FeatureFlagsDto` وقراءة `GET /api/config/flags` و`featureFlags` داخل `WalletSummaryResponseDto` في `BackendPlatformApi.kt` و`AyerRepository.kt` و`AyerViewModel.kt`، مع إغلاق الواجهات المقابلة وإظهار رسالة واضحة للمستخدم عند التعطيل.

---

## WP-2 — تحصين فوترة Google Play واستردادات المشتريات (Play Billing & Voided Purchases)

### نقاط النهاية والعقود الجديدة/المحدثة
1. **`POST /api/billing/verify-play-purchase`** *(محصن ومتكرر-آمن)*
   - **الترتيب الذري:** التحقق عبر Google Play API `GET` ومطابقة `obfuscatedExternalAccountId` ← القيد الذري في دفتر الحسابات `iap_transactions` & `wallet_accounts` ← استدعاء `:acknowledge` و`:consume` لاحقاً.
   - **إعادة المحاولة لنفس المستخدم (`200 OK`):** ترجع `{ wallet, creditedCoins, alreadyCredited: true, duplicate: true }` دون إعادة إضافة النقاط أو رمي خطأ.
   - **إعادة استخدام رمز شراء لمستخدم آخر (`409 Conflict`):** `DUPLICATE_PURCHASE_TOKEN_OTHER_USER`.
   - **التحقق من الهوية المشفرة (`403 Forbidden`):** يحسب الخادم `SHA-256(uid)` بصيغة hex (64 حرفاً) ويطابقه مع `obfuscatedExternalAccountId` (يرفض `FORBIDDEN_PURCHASE_ACCOUNT_MISMATCH` عند الاختلاف و`FORBIDDEN_PURCHASE_ACCOUNT_MISSING` عند الفقدان ما لم يُسمح بـ `ALLOW_MISSING_OBFUSCATED_ACCOUNT_ID=true`).
   - **سداد المديونية أولاً:** في حال وجود `debtCoins > 0` يخصم من النقاط المضافة أولاً بقيد `PURCHASE_DEBT_SETTLEMENT` ثم يُضاف الباقي إلى `pointsBalance` مع إرجاع `debtSettled`.
2. **`POST /api/billing/webhook/play-rtdn`** *(جديد — إشعارات Google Play RTDN)*
   - يدعم رسائل Pub/Sub المشفرة بـ Base64 ويدعم `voidedPurchaseNotification` و`oneTimeProductNotification` (`notificationType` 2 أو 12).
   - يتطلب `PLAY_RTDN_SECRET` في الإنتاج (`NODE_ENV=production`) وإلا يفشل مغلقاً (`503 RTDN_NOT_CONFIGURED`).
3. **`POST /api/admin/billing/process-voided-purchase`** *(جديد — للأدمن فقط)*
   - معالجة الاستردادات يدوياً برمز `purchaseToken` و`reason`: تسجيل `REFUND_CLAWBACK`، خصم الرصيد المتاح حتى `0` (احترام `points_balance >= 0`)، تحويل العجز إلى `debt_coins` مع إطلاق تنبيه `REFUND_CLAWBACK_DEBT` ووسم أرباح المضيف الموهوب له بـ `HOST_EARNINGS_REVIEW_FLAGGED` دون خصم صامت منه.
4. **`GET /api/wallet/summary` وحماية الإنفاق (`spend`, `gift`, `payouts`)**
   - أُضيف `debtCoins` إلى `wallet`؛ وتُرفض عمليات الإنفاق والهدايا والسحب بـ `403 ACCOUNT_HAS_DEBT` ما دام `debtCoins > 0`.

### توافق تطبيق Android
- تحديث `PlayBillingManager.kt` و`AyerViewModel.kt` لتمرير `sha256Hex(uid)` داخل `BillingFlowParams.Builder.setObfuscatedAccountId(...)`.
- تحديث `BackendWalletDto` و`VerifyPlayPurchaseResponseDto` في `BackendPlatformApi.kt` بإضافة `debtCoins` و`debtSettled` و`alreadyCredited` و`duplicate`.

---

## WP-1 — سلامة المال ومنع التلاعب بالاقتصاد (Money Safety, Ledger Holds, Escrow & Rate Alignment)

### نقاط النهاية والعقود الجديدة/المحدثة
1. **كتالوج الهدايا المرجعي في الخادم (`GET /api/wallet/gifts` و`POST /api/wallet/gift`) — (A5)**
   - **جديد `GET /api/wallet/gifts`:** يرجع كتالوج الهدايا الرسمي ومبالغه المعتمدة من الخادم (`SERVER_GIFT_CATALOG`).
   - **محدث `POST /api/wallet/gift`:** يتجاهل الخادم `coinsCost` القادم من العميل ما لم يطابق سعر الكتالوج؛ ويرفض `400 UNKNOWN_GIFT_ID` أو `400 GIFT_PRICE_MISMATCH`. كما يُقيَّد `POST /api/wallet/spend` بقائمة `ALLOWED_CLIENT_SPEND_ENTRY_TYPES` (`MATCH_SPEND`, `CALL_MINUTE_CHARGE`, `BOOST_PROFILE`) ويرفض `GIFT_SENT`.
2. **تقسيم العمولات وإيرادات المنصة + إعدادات الاقتصاد العالمية (A2 & R-4)**
   - **جديد `GET /api/admin/economy/config` و`PUT /api/admin/economy/config` (أدمن فقط):** إدارة نسب العمولة العالمية للمضيف والوكيل بحيث `hostCommissionPercent ∈ [10, 75]` و`agentCommissionPercent ∈ [5, 50]` و`host + agent <= 85` (بقاء `platform >= 15%`).
   - تقسيم الهدية يوزع `100%` من التكلفة بدقة عددية صحيحة (`host + agent + platform === costCoins`) وتُسجَّل حصة المنصة وإنفاق النقاط في حساب الخزينة `__platform__` (`platformRevenueCoins`) لتحقيق حفظ الكتلة في الدفتر.
3. **نظام المهام والمكافآت المجانية المحصّن (A1)**
   - **محدث `POST /api/wallet/daily-bonus` و`POST /api/wallet/mission-reward`:** يدعمان ترويسة `X-Device-Id` (مُشفَّرة بـ SHA-256) لمنع التعدد اليومي من نفس الجهاز، ويفرضان سقفاً يومياً قدره `25` عملة مجانية (`DAILY_FREE_REWARD_CAP_COINS`).
   - تقتصر المهام على المهام الموثقة من الخادم فقط (`mission_stream` بعد الانضمام لبث عبر `POST /api/streams/:id/join`، `mission_matches` بعد إتمام مطابقة، `mission_gifts` بعد إرسال هدية) وتُرفض المهام غير الموثقة (`mission_chat`, `mission_daily_login`, `daily_milestone`) بـ `400 INVALID_MISSION` أو `403 MISSION_NOT_VERIFIED`.
4. **نظام السحب المالي وتشفير الحسابات البنكية (A3 & A6)**
   - **تعديل سعر الصرف المرجعي:** `250 coins = $1.00` (`usdEquivalentCents = floor(amountCoins * 100 / 250)`؛ مثلاً `500 coins = $2.00`).
   - **تشفير IBAN والخصوصية:** يُخزَّن رقم الحساب/IBAN مشفراً بـ AES-256-GCM (`BANK_ENCRYPTION_KEY`) ولا يُرجَع في الاستجابات إلا مقنَّعاً (`ibanMasked` مثل `****1234` في `ibanOrAccount` و`ibanMasked`).
   - **ضوابط الصرف:** حد أقصى طلب نشط واحد لكل مستخدم (`409 ACTIVE_PAYOUT_EXISTS`)، فترة انتظار إلزامية `24` ساعة قبل الاعتماد (`409 PAYOUT_HOLD_ACTIVE`)، منع الموافقة الذاتية (`403 SELF_APPROVAL_FORBIDDEN`)، واسترداد دقيق لمحافظ `subAgentsEarningsCoins` و`hostEarningsCoins` عند الرفض.
5. **نظام الحجز المسبق والالتقاط والإفراج للمطابقة (A8 — Hold/Capture/Release)**
   - **محدث `POST /api/match/join` و`GET /api/match/poll` و`POST /api/match/leave`:** تحجز `join` الرصيد عبر `holdCoins` (`heldCoins` / `402 INSUFFICIENT_COINS`) وتُرجع `wallet` المحدث؛ عند التطابق يُنفذ `captureHold` لكلا الطرفين وإذا فشل الالتقاط يُلغى التطابق ويُفرج عن الحجز؛ وعند مغادرة الطابور أو انتهاء صلاحيته (`90s`) يُفرج عن الحجز عبر `releaseHold`.
6. **توافق تطبيق Android:**
   - إضافة `heldCoins`, `platformRevenueCoins` لـ `BackendWalletDto` و`ibanMasked` لـ `PayoutRequestDto` و`EconomyConfigDto`/`ServerGiftDto` في `BackendPlatformApi.kt`.
   - تمرير `X-Device-Id` (UUID محلي مجزأ SHA-256) في `AyerRepository.kt`، وتعديل R-4 في `AyerViewModel.kt` لاستدعاء `PUT /api/admin/economy/config`، وتحديث عرض سعر الصرف `/ 250.0` في `WalletStoreScreen.kt` و`AgenciesScreen.kt` و`AyerViewModel.kt`.

---

## WP-3 — تكافؤ PostgreSQL والبنية والترحيلات (C1, C2, C6)

### نقاط النهاية والعقود الجديدة/المحدثة
1. **`GET /health`** *(توافق عكسي — حقول تشخيصية إضافية)*
   - أُضيفت الحقول التشخيصية:
     * `callPersistenceMode`: `"postgres"` عند تفعيل `DATABASE_URL`/`pool`، أو `"json-file"` / `"memory"` محلياً.
     * `realtimeArchitecture`: `"single-instance"` (تأكيد معمارية النسخة الواحدة الحالية لمحرك البث والمطابقة في الذاكرة) أو `"multi-instance-redis-configured"` عند ضبط `REDIS_URL`.
2. **توحيد سلوك `PostgresLedgerStore` و`MemoryLedgerStore` (C1):**
   - إضافة عمود `users.agency_name` وإرجاع `agencyName` في `_mapUser` وتحديثه في `adminSetUserRolesAndCommission`.
   - توحيد سجلات التدقيق `audit_logs` في `reviewAgeVerification` (`REVIEW_AGE_VERIFICATION`) و`resolveModerationReport` (`RESOLVE_MODERATION_REPORT`) و`deleteUserAccount` (`ACCOUNT_DELETED` مع حذف `age_verifications` و`user_blocks` فقط دون المساس بالسجلات المالية)، مع دالة `getAuditLogs` موحدة في المخزنين.
   - توحيد حساب سقف المكافآت اليومية المجانية في `claimDailyBonus` و`claimMissionReward` بالاعتماد على `dayKey` المحسوب من ساعة الخادم المرجعية `this.now()`.
3. **الترحيلات المرقّمة وحماية الدفتر بالقاعدة (C6):**
   - إضافة `backend/migrations/001..003` ومنفّذ `backend/src/migrator.js` (`schema_migrations` مع فحص SHA-256) وسكربت `npm run migrate` (`backend/scripts/migrate.mjs`).
   - استبدال `ON DELETE CASCADE` بـ `ON DELETE RESTRICT` على الجداول المالية الخمسة (`wallet_accounts`, `ledger_holds`, `ledger_entries`, `iap_transactions`, `payout_requests`).
   - إضافة مشغّل `trg_ledger_entries_immutable` (`BEFORE UPDATE OR DELETE ON ledger_entries`) لمنع أي تعديل أو حذف في الدفتر على مستوى PostgreSQL.
4. **نقل حالة المكالمات من ملف JSON إلى PostgreSQL (C2):**
   - إضافة `backend/src/callStore.js` (`PostgresCallStore` و`MemoryCallStore`) وجدول `call_completions` لحفظ `call_governance_config` و`call_sessions` و`call_alerts` و`call_completions` في PostgreSQL عند توفر `DATABASE_URL`، مع استمرار دعم `DATA_FILE` فقط كبديل اختباري محلي عند غياب قاعدة البيانات.

### توافق تطبيق Android
- لم تتغير أي مسارات أو DTOs خاصة بتطبيق Android؛ الإضافات في `GET /health` متوافقة عكسياً بالكامل.



