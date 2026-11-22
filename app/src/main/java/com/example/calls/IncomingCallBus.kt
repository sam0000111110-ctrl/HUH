package com.example.calls

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide reactive bridge between [com.example.fcm.AyerFirebaseMessagingService],
 * Firestore call signaling, and [com.example.ui.AyerViewModel] so incoming 1-on-1 calls
 * appear instantly inside the app or via notification.
 */
object IncomingCallBus {
    private val _pendingInvite = MutableStateFlow<IncomingCallInvite?>(null)
    val pendingInvite: StateFlow<IncomingCallInvite?> = _pendingInvite.asStateFlow()

    fun emitIncomingCall(invite: IncomingCallInvite) {
        _pendingInvite.value = invite
    }

    fun dismissIncomingCall(callId: String? = null) {
        val current = _pendingInvite.value
        if (callId == null || current == null || current.callId == callId) {
            _pendingInvite.value = null
        }
    }
}
