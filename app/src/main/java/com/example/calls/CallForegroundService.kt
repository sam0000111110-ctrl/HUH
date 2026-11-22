package com.example.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * Play-Policy-compliant Foreground Service keeping 1-on-1 call microphone/camera alive
 * when the app goes to background during an active call, with a Hang-up action.
 */
class CallForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "ayer_ongoing_call_channel"
        const val NOTIFICATION_ID = 4401
        const val ACTION_START = "com.example.calls.ACTION_START_CALL_SERVICE"
        const val ACTION_STOP = "com.example.calls.ACTION_STOP_CALL_SERVICE"
        const val ACTION_HANGUP_FROM_NOTIF = "com.example.calls.ACTION_HANGUP_CALL"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_IS_VIDEO = "extra_is_video"

        @Volatile
        var onHangupFromNotification: (() -> Unit)? = null

        fun start(context: Context, partnerName: String, isVideo: Boolean) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CALLER_NAME, partnerName)
                putExtra(EXTRA_IS_VIDEO, isVideo)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
                // Safe fallback in restricted test/background states
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, CallForegroundService::class.java))
            } catch (_: Throwable) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_HANGUP_FROM_NOTIF -> {
                onHangupFromNotification?.invoke()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val partnerName = intent?.getStringExtra(EXTRA_CALLER_NAME) ?: "مكالمة جارية"
                val isVideo = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) ?: false
                ensureChannel()
                val notification = buildNotification(partnerName, isVideo)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val fgTypes = if (isVideo) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                        } else {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        }
                        startForeground(NOTIFICATION_ID, notification, fgTypes)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (_: Throwable) {
                    // Fallback if permissions revoked while backgrounded
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "المكالمات الجارية - Ayer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "يعرض حالة المكالمة الصوتية أو المرئية النشطة"
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(partnerName: String, isVideo: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "active_call")
        }
        val openPending = PendingIntent.getActivity(
            this,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_HANGUP_FROM_NOTIF
        }
        val hangupPending = PendingIntent.getService(
            this,
            102,
            hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callLabel = if (isVideo) "مكالمة فيديو جارية" else "مكالمة صوتية جارية"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$callLabel • $partnerName")
            .setContentText("اضغط للعودة إلى شاشة المكالمة")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPending)
            .addAction(0, "إنهاء المكالمة", hangupPending)
            .build()
    }
}
