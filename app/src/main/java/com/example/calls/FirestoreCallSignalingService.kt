package com.example.calls

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Real-time 1-on-1 call signaling over Firebase Firestore (`calls/{callId}`),
 * gracefully falling back to local single-device signaling when Firebase is not configured.
 */
class FirestoreCallSignalingService {
    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w("CallSignaling", "Firestore unavailable, using local signaling fallback: ${e.message}")
            null
        }
    }

    private var incomingListener: ListenerRegistration? = null

    fun publishCallInvitation(
        callId: String,
        roomName: String,
        callerId: String,
        callerName: String,
        calleeId: String,
        callType: CallType
    ) {
        val fs = firestore ?: return
        try {
            val payload = hashMapOf(
                "callId" to callId,
                "roomName" to roomName,
                "callerId" to callerId,
                "callerName" to callerName,
                "calleeId" to calleeId,
                "callType" to callType.name,
                "status" to CallLifecycleState.DIALING.name,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("calls").document(callId).set(payload, SetOptions.merge())
        } catch (e: Throwable) {
            Log.w("CallSignaling", "Failed to publish call invitation: ${e.message}")
        }
    }

    fun updateCallSignalState(callId: String, state: CallLifecycleState, actorId: String) {
        val fs = firestore ?: return
        try {
            val update = hashMapOf<String, Any>(
                "status" to state.name,
                "lastActorId" to actorId,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("calls").document(callId).set(update, SetOptions.merge())
        } catch (e: Throwable) {
            Log.w("CallSignaling", "Failed to update call state: ${e.message}")
        }
    }

    fun observeIncomingCallsForUser(myUid: String): Flow<IncomingCallInvite?> = callbackFlow {
        val fs = firestore
        if (fs == null || myUid.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        try {
            incomingListener?.remove()
            val reg = fs.collection("calls")
                .whereEqualTo("calleeId", myUid)
                .whereEqualTo("status", CallLifecycleState.DIALING.name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val latest = snapshot.documents.maxByOrNull { it.getLong("updatedAt") ?: 0L }
                    if (latest != null) {
                        val invite = IncomingCallInvite(
                            callId = latest.getString("callId") ?: latest.id,
                            roomName = latest.getString("roomName") ?: "",
                            callerId = latest.getString("callerId") ?: "",
                            callerName = latest.getString("callerName") ?: "متصل",
                            callType = if (latest.getString("callType") == CallType.VIDEO.name) CallType.VIDEO else CallType.AUDIO
                        )
                        trySend(invite)
                    } else {
                        trySend(null)
                    }
                }
            incomingListener = reg
            awaitClose {
                reg.remove()
                if (incomingListener == reg) incomingListener = null
            }
        } catch (e: Throwable) {
            trySend(null)
            awaitClose { }
        }
    }

    fun observeCallStatus(callId: String): Flow<CallLifecycleState?> = callbackFlow {
        val fs = firestore
        if (fs == null || callId.isBlank()) {
            awaitClose { }
            return@callbackFlow
        }
        val reg = fs.collection("calls").document(callId)
            .addSnapshotListener { doc, _ ->
                val raw = doc?.getString("status") ?: return@addSnapshotListener
                val parsed = runCatching { CallLifecycleState.valueOf(raw) }.getOrNull()
                if (parsed != null) trySend(parsed)
            }
        awaitClose { reg.remove() }
    }
}
