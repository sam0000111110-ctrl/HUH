package com.example.calls

import android.content.Context
import android.util.Log
import com.example.util.FirestoreClients
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Real-time 1-on-1 call signaling over the named Firebase Firestore database (`calls/{callId}`).
 */
class FirestoreCallSignalingService(private val context: Context) {
    private val firestore: FirebaseFirestore? by lazy {
        runCatching { FirestoreClients.getDatabase(context) }
            .onFailure {
                Log.w("CallSignaling", "Firestore unavailable: ${it.message}")
            }
            .getOrNull()
    }

    private fun currentUid(): String? =
        runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()?.takeIf { it.isNotBlank() }

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
        val authUid = currentUid() ?: return
        if (calleeId.isBlank() || calleeId == authUid) return

        val path = "calls/$callId"
        try {
            val payload = hashMapOf(
                "callId" to callId,
                "roomName" to roomName,
                "callerId" to authUid,
                "callerName" to callerName,
                "calleeId" to calleeId,
                "callType" to callType.name,
                "status" to CallLifecycleState.DIALING.name,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("calls").document(callId).set(payload, SetOptions.merge())
                .addOnFailureListener { handleFirestoreError(it, OperationType.CREATE, path) }
        } catch (e: Throwable) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    fun updateCallSignalState(callId: String, state: CallLifecycleState, actorId: String) {
        val fs = firestore ?: return
        val authUid = currentUid() ?: return
        val path = "calls/$callId"
        try {
            val update = hashMapOf<String, Any>(
                "status" to state.name,
                "lastActorId" to authUid,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("calls").document(callId).set(update, SetOptions.merge())
                .addOnFailureListener { handleFirestoreError(it, OperationType.UPDATE, path) }
        } catch (e: Throwable) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    fun observeIncomingCallsForUser(myUid: String): Flow<IncomingCallInvite?> = callbackFlow {
        val fs = firestore
        val authUid = currentUid()
        if (fs == null || myUid.isBlank() || authUid == null || authUid != myUid) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val path = "calls"
        try {
            incomingListener?.remove()
            val reg = fs.collection("calls")
                .whereEqualTo("calleeId", myUid)
                .whereEqualTo("status", CallLifecycleState.DIALING.name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.LIST, path)
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
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
            handleFirestoreError(e, OperationType.LIST, path)
            trySend(null)
            awaitClose { }
        }
    }

    fun observeCallStatus(callId: String): Flow<CallLifecycleState?> = callbackFlow {
        val fs = firestore
        val authUid = currentUid()
        if (fs == null || callId.isBlank() || authUid == null) {
            awaitClose { }
            return@callbackFlow
        }
        val path = "calls/$callId"
        val reg = fs.collection("calls").document(callId)
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, path)
                    return@addSnapshotListener
                }
                val raw = doc?.getString("status") ?: return@addSnapshotListener
                val parsed = runCatching { CallLifecycleState.valueOf(raw) }.getOrNull()
                if (parsed != null) trySend(parsed)
            }
        awaitClose { reg.remove() }
    }
}
