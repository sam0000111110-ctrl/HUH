# Ayer / HUH — LiveKit Call Broker, Signaling & Budget Governance Backend

خادم Node.js خفيف (Zero external runtime dependencies) مسؤول عن:
1. التحقق من هوية المستخدم عبر **Firebase Auth ID Token** (عدم السماح بانتحال الهوية).
2. إصدار **LiveKit JWT Tokens** قصيرة العمر ومقيدة بغرفة المكالمة فقط (`1-on-1`).
3. فرض حدود الميزانية الشهرية، حدود الاستخدام اليومي للمستخدم، إيقاف الفيديو عند الحاجة، والـ **Rate Limiting**.
4. احتساب المدة الفعلية للمكالمة من الخادم فقط (`acceptedAt` إلى `endedAt` مع استثناء فترات `RECONNECTING` وعدم احتساب وقت الرنين).
5. إغلاق غرف LiveKit تلقائياً (`DeleteRoom`) فور انتهاء المكالمة أو انقطاعها لمنع الغرف اليتيمة.
6. تغذية لوحة **Call Management & Monitoring** للأدمن لحظياً وإطلاق التنبيهات التلقائية.

## التشغيل والاختبار محلياً
```bash
cd backend
npm test
PORT=8080 ALLOW_DEV_AUTH=true LIVEKIT_URL=wss://your-project.livekit.cloud LIVEKIT_API_KEY=xxx LIVEKIT_API_SECRET=yyy npm start
```

## النشر السريع (Google Cloud Run / Railway / VPS)
1. أنشئ حساباً مجانياً على [LiveKit Cloud](https://cloud.livekit.io) واحصل على `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`.
2. انشر مجلد `backend/` عبر Docker (`Dockerfile` مرفق) مع ضبط متغيرات البيئة التالية في السيرفر **فقط**:
   - `LIVEKIT_URL`
   - `LIVEKIT_API_KEY`
   - `LIVEKIT_API_SECRET`
   - `FIREBASE_PROJECT_ID`
   - `ADMIN_UIDS`
   - `ADMIN_API_KEY`
   - `ALLOW_DEV_AUTH=false`
3. ضع رابط الخادم المنشور في `.env` داخل تطبيق الأندرويد كـ `CALL_BACKEND_URL=https://your-backend-url` (بدون أي مفاتيح سرية داخل التطبيق نهائياً).
4. في لوحة LiveKit Cloud فعّل Webhook موجّهاً إلى `https://your-backend-url/api/webhooks/livekit`.
5. عند التوسع لاحقاً وتقليل التكاليف: يمكنك تشغيل `livekit/livekit-server` على VPS خاصة بك وتغيير `LIVEKIT_URL` و`LIVEKIT_API_KEY` و`LIVEKIT_API_SECRET` في الخادم فقط دون أي تعديل في تطبيق الأندرويد.
