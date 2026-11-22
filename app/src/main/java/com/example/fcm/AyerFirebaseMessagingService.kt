package com.example.fcm

import android.util.Log
import com.example.data.model.NotificationType
import com.example.util.AyerNotificationManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging (FCM) Service for Ayer.
 * Listens for incoming server-sent push notifications:
 * - New 1-on-1 private chat messages
 * - Daily mission completion and ready-to-claim rewards
 * - Commission and referral payouts for influencers/hosts
 */
class AyerFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AyerFCMService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM Device Registration Token: $token")
        // Here the FCM token is forwarded to the Ayer backend/Firestore user profile document
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val type = data["type"] ?: "system"

        when (type) {
            "incoming_call" -> {
                val callId = data["call_id"] ?: "call_${System.currentTimeMillis()}"
                val roomName = data["room_name"] ?: "ayer_1on1_$callId"
                val callerId = data["caller_id"] ?: "remote_caller"
                val callerName = data["caller_name"] ?: notification?.title ?: "مستخدم Ayer"
                val isVideo = data["call_type"]?.equals("VIDEO", ignoreCase = true) ?: true
                com.example.calls.IncomingCallBus.emitIncomingCall(
                    com.example.calls.IncomingCallInvite(
                        callId = callId,
                        roomName = roomName,
                        callerId = callerId,
                        callerName = callerName,
                        callType = if (isVideo) com.example.calls.CallType.VIDEO else com.example.calls.CallType.AUDIO
                    )
                )
                AyerNotificationManager.sendIncomingCallNotification(
                    context = applicationContext,
                    callId = callId,
                    callerName = callerName,
                    isVideo = isVideo
                )
            }
            "call_cancelled", "call_ended" -> {
                val callId = data["call_id"] ?: ""
                if (callId.isNotBlank()) {
                    AyerNotificationManager.cancelIncomingCallNotification(applicationContext, callId)
                    com.example.calls.IncomingCallBus.dismissIncomingCall(callId)
                }
            }
            "chat_message" -> {
                val senderName = data["sender_name"] ?: notification?.title ?: "شريك في Ayer"
                val messageText = data["message_text"] ?: notification?.body ?: "أرسل لك رسالة جديدة"
                val partnerId = data["partner_id"]
                AyerNotificationManager.sendChatMessageNotification(
                    context = applicationContext,
                    senderName = senderName,
                    messageText = messageText,
                    chatPartnerId = partnerId
                )
            }
            "daily_mission_claim" -> {
                val missionTitle = data["mission_title"] ?: "المهمة اليومية"
                val rewardCoins = data["reward_coins"]?.toIntOrNull() ?: 50
                AyerNotificationManager.sendDailyMissionReadyNotification(
                    context = applicationContext,
                    missionTitle = missionTitle,
                    rewardCoins = rewardCoins
                )
            }
            "commission_payout" -> {
                val title = notification?.title ?: "دفعة أرباح وعمولة جديدة 💰"
                val body = notification?.body ?: (data["amount"] ?: "تم إضافة أرباحك إلى محفظتك")
                AyerNotificationManager.sendSystemNotification(
                    context = applicationContext,
                    notificationId = (System.currentTimeMillis() % 10000).toInt(),
                    title = title,
                    message = body,
                    type = NotificationType.COMMISSION_PAYOUT
                )
            }
            else -> {
                val title = notification?.title ?: data["title"] ?: "Ayer Live"
                val body = notification?.body ?: data["body"] ?: "إشعار جديد من تطبيق Ayer"
                AyerNotificationManager.sendSystemNotification(
                    context = applicationContext,
                    notificationId = (System.currentTimeMillis() % 10000).toInt(),
                    title = title,
                    message = body,
                    type = NotificationType.SYSTEM_ALERT
                )
            }
        }
    }
}
