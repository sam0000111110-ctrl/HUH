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
