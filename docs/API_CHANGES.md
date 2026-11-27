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

