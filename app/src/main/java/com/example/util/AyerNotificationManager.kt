package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.NotificationType

object AyerNotificationManager {
    const val CHANNEL_ID_INFLUENCER_ALERTS = "ayer_influencer_alerts"
    const val CHANNEL_ID_CHAT_MESSAGES = "ayer_chat_messages"
    const val CHANNEL_ID_DAILY_MISSIONS = "ayer_daily_missions"
    const val CHANNEL_ID_INCOMING_CALLS = "ayer_incoming_calls"
    const val CHANNEL_ID_CALL_ADMIN_ALERTS = "ayer_call_admin_alerts"

    private const val CHANNEL_NAME_INFLUENCER = "إشعارات العمولات والإحالات - Ayer"
    private const val CHANNEL_NAME_CHAT = "رسائل المحادثات المباشرة - Ayer"
    private const val CHANNEL_NAME_MISSIONS = "المهمات والمكافآت اليومية - Ayer"
    private const val CHANNEL_NAME_INCOMING_CALLS = "المكالمات الواردة - Ayer"
    private const val CHANNEL_NAME_CALL_ADMIN_ALERTS = "تنبيهات إدارة وميزانية المكالمات - Ayer"

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Influencer Channel
            val influencerChannel = NotificationChannel(
                CHANNEL_ID_INFLUENCER_ALERTS,
                CHANNEL_NAME_INFLUENCER,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات فورية للمؤثرين والوكلاء عند انضمام إحالة جديدة أو استلام دفعة أرباح"
                enableLights(true)
                lightColor = Color.MAGENTA
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }
            notificationManager.createNotificationChannel(influencerChannel)

            // 2. Chat Messages Channel
            val chatChannel = NotificationChannel(
                CHANNEL_ID_CHAT_MESSAGES,
                CHANNEL_NAME_CHAT,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات فورية عند وصول رسالة نصية أو طلب اتصال من الشركاء والأصدقاء"
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }
            notificationManager.createNotificationChannel(chatChannel)

            // 3. Daily Missions & Rewards Channel
            val missionsChannel = NotificationChannel(
                CHANNEL_ID_DAILY_MISSIONS,
                CHANNEL_NAME_MISSIONS,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات عند اكتمال المهمات اليومية واستعداد مكافآت النقاط للاستلام"
                enableLights(true)
                lightColor = Color.YELLOW
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(missionsChannel)

            // 4. Incoming 1-on-1 Calls Channel
            val callChannel = NotificationChannel(
                CHANNEL_ID_INCOMING_CALLS,
                CHANNEL_NAME_INCOMING_CALLS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات المكالمات الصوتية والمرئية الفردية الواردة"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 250, 400)
            }
            notificationManager.createNotificationChannel(callChannel)

            // 5. Call Budget & Security Admin Alerts Channel
            val callAdminChannel = NotificationChannel(
                CHANNEL_ID_CALL_ADMIN_ALERTS,
                CHANNEL_NAME_CALL_ADMIN_ALERTS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات الأدمن حول الميزانية الشهرية للمكالمات أو ارتفاع الفشل أو الاستخدام غير الطبيعي"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(callAdminChannel)
        }
    }

    fun sendChatMessageNotification(
        context: Context,
        senderName: String,
        messageText: String,
        chatPartnerId: String? = null
    ) {
        initNotificationChannels(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "chat")
            putExtra("EXTRA_CHAT_PARTNER_ID", chatPartnerId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_CHAT_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("💬 رسالة جديدة من $senderName")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setColor(0xFF00E5FF.toInt()) // Cyan
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(
                ("chat_$senderName".hashCode()),
                builder.build()
            )
        } catch (_: SecurityException) {}
    }

    fun sendDailyMissionReadyNotification(
        context: Context,
        missionTitle: String,
        rewardCoins: Int
    ) {
        initNotificationChannels(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "missions")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_DAILY_MISSIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🎁 مهمة يومية جاهزة للاستلام!")
            .setContentText("أكملت '$missionTitle' بنجاح! اضغط لاستلام +$rewardCoins نقطة مجانية 💎")
            .setStyle(NotificationCompat.BigTextStyle().bigText("أكملت '$missionTitle' بنجاح! اضغط لاستلام +$rewardCoins نقطة مجانية ورصيد فوري في محفظتك."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setColor(0xFFFFB300.toInt()) // Gold
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(9001, builder.build())
        } catch (_: SecurityException) {}
    }

    fun sendSystemNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        type: NotificationType,
        coinsBadge: String? = null
    ) {
        initNotificationChannels(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val subText = when (type) {
            NotificationType.NEW_REFERRAL -> "إحالة جديدة 👥"
            NotificationType.COMMISSION_PAYOUT -> "دفعة عمولة 💰"
            NotificationType.WITHDRAWAL_PAID -> "سحب أرباح 🏦"
            NotificationType.SYSTEM_ALERT -> "نظام Ayer"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_INFLUENCER_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setColor(0xFFE91E63.toInt()) // Neon Magenta
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Handled safely without crashing
        }
    }

    fun sendIncomingCallNotification(
        context: Context,
        callId: String,
        callerName: String,
        isVideo: Boolean
    ) {
        initNotificationChannels(context)
        if (!hasNotificationPermission(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "incoming_call")
            putExtra("EXTRA_CALL_ID", callId)
            putExtra("EXTRA_CALLER_NAME", callerName)
            putExtra("EXTRA_CALL_IS_VIDEO", isVideo)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            callId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val title = if (isVideo) "📹 مكالمة فيديو واردة من $callerName" else "📞 مكالمة صوتية واردة من $callerName"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_INCOMING_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("اضغط للرد أو رفض المكالمة")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(ringtoneUri)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(callId.hashCode(), builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelIncomingCallNotification(context: Context, callId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(callId.hashCode())
        } catch (_: Throwable) {
        }
    }

    fun sendCallAdminAlertNotification(
        context: Context,
        title: String,
        message: String
    ) {
        initNotificationChannels(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "call_admin")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            9501,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_CALL_ADMIN_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🛡️ $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 10000).toInt() + 9500, builder.build())
        } catch (_: SecurityException) {
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
