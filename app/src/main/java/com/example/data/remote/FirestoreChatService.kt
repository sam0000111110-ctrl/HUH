package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.model.AgeVerificationSubmissionDoc
import com.example.data.model.FirestoreRandomChatMessage
import com.example.data.model.FirestoreUserDoc
import com.example.data.model.LiveStreamReport
import com.example.data.model.UserProfileReport
import com.example.util.FirestoreClients
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Real-time Firestore service for user sync, age verification queue, random match chat,
 * and UGC moderation reports. All reads/writes require an authenticated FirebaseUser and
 * align 1:1 with firestore.rules.
 */
class FirestoreChatService(private val context: Context) {

    private val firestore: FirebaseFirestore? by lazy {
        runCatching { FirestoreClients.getDatabase(context) }
            .onFailure {
                Log.w("FirestoreChatService", "Firestore unavailable: ${it.message}")
            }
            .getOrNull()
    }

    private fun currentUid(): String? =
        runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()?.takeIf { it.isNotBlank() }

    private val _flaggedStreamIds = MutableStateFlow<Set<String>>(emptySet())
    val flaggedStreamIds = _flaggedStreamIds.asStateFlow()

    private val _submittedReports = MutableStateFlow<List<LiveStreamReport>>(emptyList())
    val submittedReports = _submittedReports.asStateFlow()

    private var activeRegistration: ListenerRegistration? = null

    suspend fun syncUserOnSignIn(firebaseUser: FirebaseUser): Result<FirestoreUserDoc> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val uid = firebaseUser.uid
        val path = "users/$uid"
        return try {
            val docRef = fs.collection("users").document(uid)
            val snapshot = docRef.get().await()
            val safeName = (firebaseUser.displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: firebaseUser.email?.substringBefore('@')?.trim()?.takeIf { it.isNotBlank() }
                ?: "مستخدم HUH").take(80)
            val email = firebaseUser.email?.trim().orEmpty()
            val photoUrl = firebaseUser.photoUrl?.toString()?.takeIf { it.isNotBlank() }

            if (!snapshot.exists()) {
                val createPayload = hashMapOf<String, Any>(
                    "uid" to uid,
                    "displayName" to safeName,
                    "email" to email,
                    "ageVerified" to false,
                    "ageVerificationStatus" to "UNVERIFIED",
                    "isAdmin" to false,
                    "isAgent" to false,
                    "isHost" to false,
                    "hostStatus" to "NONE",
                    "pointsBalance" to 0,
                    "totalAccumulatedPoints" to 0,
                    "totalPointsSpent" to 0,
                    "hostEarningsCoins" to 0,
                    "subAgentsEarningsCoins" to 0,
                    "subAgentsCount" to 0,
                    "profileVisibility" to "PUBLIC",
                    "directMessagePermission" to "EVERYONE",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (photoUrl != null) {
                    createPayload["photoUrl"] = photoUrl
                }
                docRef.set(createPayload).await()
                val created = docRef.get().await().toObject(FirestoreUserDoc::class.java)
                    ?: FirestoreUserDoc(id = uid, uid = uid, displayName = safeName, email = email, photoUrl = photoUrl)
                Result.success(created)
            } else {
                val updatePayload = hashMapOf<String, Any>(
                    "email" to email,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val existingName = snapshot.getString("displayName")
                if (existingName.isNullOrBlank()) {
                    updatePayload["displayName"] = safeName
                }
                if (photoUrl != null && snapshot.getString("photoUrl").isNullOrBlank()) {
                    updatePayload["photoUrl"] = photoUrl
                }
                docRef.set(updatePayload, SetOptions.merge()).await()
                val updated = docRef.get().await().toObject(FirestoreUserDoc::class.java)
                    ?: FirestoreUserDoc(id = uid, uid = uid, displayName = safeName, email = email, photoUrl = photoUrl)
                Result.success(updated)
            }
        } catch (e: Exception) {
            val op = OperationType.WRITE
            handleFirestoreError(e, op, path)
            Result.failure(e)
        }
    }

    fun observeUserDoc(uid: String): Flow<FirestoreUserDoc?> = callbackFlow {
        val fs = firestore
        val authUid = currentUid()
        if (fs == null || uid.isBlank() || authUid != uid) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val path = "users/$uid"
        val reg = fs.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, path)
                    return@addSnapshotListener
                }
                val doc = snapshot?.toObject(FirestoreUserDoc::class.java)
                trySend(doc)
            }
        awaitClose { reg.remove() }
    }

    suspend fun submitAgeVerification(
        uid: String,
        displayName: String,
        email: String,
        dob: String,
        ageYears: Int
    ): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val authUid = currentUid()
        if (authUid == null || authUid != uid) {
            return Result.failure(SecurityException("المصادقة مطلوبة قبل توثيق العمر."))
        }
        val queuePath = "age_verifications/$uid"
        return try {
            val safeName = displayName.trim().ifBlank { "مستخدم HUH" }.take(80)
            val verificationPayload = hashMapOf<String, Any>(
                "userId" to uid,
                "displayName" to safeName,
                "email" to email.trim(),
                "dob" to dob,
                "ageYears" to ageYears,
                "status" to "PENDING_REVIEW",
                "acceptedTermsAt" to FieldValue.serverTimestamp(),
                "acceptedPrivacyAt" to FieldValue.serverTimestamp(),
                "submittedAt" to FieldValue.serverTimestamp()
            )
            fs.collection("age_verifications").document(uid)
                .set(verificationPayload)
                .await()

            val userSafeUpdate = hashMapOf<String, Any>(
                "dob" to dob,
                "age" to ageYears,
                "acceptedTermsAt" to FieldValue.serverTimestamp(),
                "acceptedPrivacyAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            fs.collection("users").document(uid)
                .set(userSafeUpdate, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, queuePath)
            Result.failure(e)
        }
    }

    fun observeAgeVerification(uid: String): Flow<AgeVerificationSubmissionDoc?> = callbackFlow {
        val fs = firestore
        val authUid = currentUid()
        if (fs == null || uid.isBlank() || authUid != uid) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val path = "age_verifications/$uid"
        val reg = fs.collection("age_verifications").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, path)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(AgeVerificationSubmissionDoc::class.java))
            }
        awaitClose { reg.remove() }
    }

    fun observeMatchMessages(matchId: String): Flow<List<FirestoreRandomChatMessage>> = callbackFlow {
        val fs = firestore
        val authUid = currentUid()
        if (fs == null || authUid == null || matchId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val path = "matches/$matchId/messages"
        try {
            val query = fs.collection("matches")
                .document(matchId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)

            val registration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        runCatching {
                            FirestoreRandomChatMessage(
                                id = doc.id,
                                matchId = matchId,
                                senderId = doc.getString("senderId") ?: "",
                                senderName = doc.getString("senderName") ?: "",
                                text = doc.getString("text") ?: "",
                                translatedText = doc.getString("translatedText"),
                                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                                status = doc.getString("status") ?: "SENT"
                            )
                        }.getOrNull()
                    }
                    trySend(messages)
                }
            }
            activeRegistration = registration
            awaitClose {
                registration.remove()
                if (activeRegistration == registration) activeRegistration = null
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            trySend(emptyList())
            awaitClose { }
        }
    }

    suspend fun sendMessage(
        matchId: String,
        senderId: String,
        senderName: String,
        text: String,
        translatedText: String? = null
    ): Result<FirestoreRandomChatMessage> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val authUid = currentUid()
            ?: return Result.failure(SecurityException("المصادقة مطلوبة لإرسال الرسائل."))
        val trimmedText = text.trim()
        if (trimmedText.isEmpty() || trimmedText.length > 2000) {
            return Result.failure(IllegalArgumentException("نص الرسالة غير صالح."))
        }

        val messageId = "msg_${System.currentTimeMillis()}_${(100..999).random()}"
        val now = System.currentTimeMillis()
        val chatMessage = FirestoreRandomChatMessage(
            id = messageId,
            matchId = matchId,
            senderId = authUid,
            senderName = senderName,
            text = trimmedText,
            translatedText = translatedText,
            timestamp = now,
            status = "SENT"
        )

        val path = "matches/$matchId/messages/$messageId"
        return try {
            val data = hashMapOf<String, Any>(
                "senderId" to authUid,
                "senderName" to senderName,
                "text" to trimmedText,
                "timestamp" to now,
                "status" to "SENT"
            )
            if (!translatedText.isNullOrBlank()) {
                data["translatedText"] = translatedText
            }
            fs.collection("matches")
                .document(matchId)
                .collection("messages")
                .document(messageId)
                .set(data)
                .await()
            Result.success(chatMessage)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun stopObserving() {
        activeRegistration?.remove()
        activeRegistration = null
    }

    suspend fun submitStreamModerationReport(report: LiveStreamReport): Result<Boolean> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val authUid = currentUid()
            ?: return Result.failure(SecurityException("المصادقة مطلوبة لإرسال البلاغات."))
        val cleanReason = report.reason.trim().ifBlank { "مخالفة شروط الاستخدام" }.take(500)
        val path = "reports/${report.id}"

        return try {
            val reportData = hashMapOf<String, Any>(
                "reportType" to "LIVE_STREAM",
                "streamId" to report.streamId,
                "streamTitle" to report.streamTitle,
                "hostName" to report.hostName,
                "reporterId" to authUid,
                "reporterName" to report.reporterName,
                "reason" to cleanReason,
                "details" to report.details.trim().take(1000),
                "status" to "PENDING_REVIEW",
                "createdAt" to FieldValue.serverTimestamp()
            )
            fs.collection("reports")
                .document(report.id)
                .set(reportData)
                .await()

            _flaggedStreamIds.value = _flaggedStreamIds.value + report.streamId
            _submittedReports.value = _submittedReports.value + report.copy(
                reporterId = authUid,
                reason = cleanReason,
                status = "PENDING_REVIEW"
            )
            Result.success(true)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitUserProfileReport(report: UserProfileReport): Result<Boolean> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val authUid = currentUid()
            ?: return Result.failure(SecurityException("المصادقة مطلوبة لإرسال البلاغات."))
        val cleanReason = report.reason.trim().ifBlank { "سلوك غير لائق" }.take(500)
        val path = "reports/${report.id}"

        return try {
            val reportData = hashMapOf<String, Any>(
                "reportType" to "USER_PROFILE",
                "targetUserId" to report.targetUserId,
                "targetUserName" to report.targetUserName,
                "reporterId" to authUid,
                "reporterName" to report.reporterName,
                "reason" to cleanReason,
                "details" to report.details.trim().take(1000),
                "status" to "PENDING_REVIEW",
                "createdAt" to FieldValue.serverTimestamp()
            )
            fs.collection("reports")
                .document(report.id)
                .set(reportData)
                .await()
            Result.success(true)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // ==========================================================
    // Phase E: Friends, Privacy Settings, Blocks & Private Chat
    // ==========================================================

    data class PrivacySettingsSnapshot(
        val profileVisibility: String = "PUBLIC",
        val directMessagePermission: String = "EVERYONE",
        val showOnlineStatus: Boolean = true,
        val hideLocationInMatch: Boolean = false
    )

    suspend fun updateUserProfileFields(
        uid: String,
        displayName: String,
        bio: String,
        country: String
    ): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val authUid = currentUid()
        if (authUid == null || authUid != uid) {
            return Result.failure(SecurityException("المصادقة مطلوبة لتحديث الحساب."))
        }
        return try {
            val payload = hashMapOf<String, Any>(
                "displayName" to displayName.trim().ifBlank { "مستخدم HUH" }.take(80),
                "bio" to bio.trim().take(500),
                "country" to country.trim().take(80),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            fs.collection("users").document(uid).set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(
        uid: String,
        profileVisibility: String,
        directMessagePermission: String,
        showOnlineStatus: Boolean,
        hideLocationInMatch: Boolean
    ): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val authUid = currentUid()
        if (authUid == null || authUid != uid) {
            return Result.failure(SecurityException("المصادقة مطلوبة لتحديث الخصوصية."))
        }
        return try {
            fs.collection("users").document(uid).set(
                hashMapOf<String, Any>(
                    "profileVisibility" to profileVisibility,
                    "directMessagePermission" to directMessagePermission,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

            fs.collection("users").document(uid)
                .collection("private_settings").document("preferences")
                .set(
                    hashMapOf<String, Any>(
                        "profileVisibility" to profileVisibility,
                        "directMessagePermission" to directMessagePermission,
                        "showOnlineStatus" to showOnlineStatus,
                        "hideLocationInMatch" to hideLocationInMatch,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/private_settings/preferences")
            Result.failure(e)
        }
    }

    fun observePrivacySettings(uid: String): Flow<PrivacySettingsSnapshot?> = callbackFlow {
        val fs = firestore
        val authUid = currentUid()
        if (fs == null || uid.isBlank() || authUid != uid) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val path = "users/$uid/private_settings/preferences"
        val reg = fs.collection("users").document(uid)
            .collection("private_settings").document("preferences")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, path)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(
                        PrivacySettingsSnapshot(
                            profileVisibility = snapshot.getString("profileVisibility") ?: "PUBLIC",
                            directMessagePermission = snapshot.getString("directMessagePermission") ?: "EVERYONE",
                            showOnlineStatus = snapshot.getBoolean("showOnlineStatus") ?: true,
                            hideLocationInMatch = snapshot.getBoolean("hideLocationInMatch") ?: false
                        )
                    )
                }
            }
        awaitClose { reg.remove() }
    }

    fun observeBlockedUsers(uid: String): Flow<Set<String>> = callbackFlow {
        val fs = firestore
        val authUid = currentUid()
        if (fs == null || uid.isBlank() || authUid != uid) {
            trySend(emptySet())
            awaitClose { }
            return@callbackFlow
        }
        val reg = fs.collection("users").document(uid).collection("blocks")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "users/$uid/blocks")
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
                trySend(ids)
            }
        awaitClose { reg.remove() }
    }

    suspend fun setUserBlocked(targetUid: String, blocked: Boolean): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val authUid = currentUid() ?: return Result.failure(SecurityException("المصادقة مطلوبة"))
        if (targetUid.isBlank() || targetUid == authUid) return Result.success(Unit)
        return try {
            val blockRef = fs.collection("users").document(authUid).collection("blocks").document(targetUid)
            val friendRef = fs.collection("users").document(authUid).collection("friends").document(targetUid)
            if (blocked) {
                blockRef.set(hashMapOf("blockedAt" to FieldValue.serverTimestamp())).await()
                val existingFriend = runCatching { friendRef.get().await() }.getOrNull()
                if (existingFriend?.exists() == true) {
                    friendRef.set(
                        hashMapOf<String, Any>(
                            "status" to "BLOCKED",
                            "lastMessage" to "تم حظر المستخدم",
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
                }
            } else {
                blockRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$authUid/blocks/$targetUid")
            Result.failure(e)
        }
    }

    fun observeFriends(uid: String): Flow<List<com.example.data.model.FriendItem>> = callbackFlow {
        val fs = firestore
        val authUid = currentUid()
        if (fs == null || uid.isBlank() || authUid != uid) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val path = "users/$uid/friends"
        val reg = fs.collection("users").document(uid).collection("friends")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    runCatching {
                        val statusStr = doc.getString("status") ?: "ACCEPTED"
                        val status = runCatching {
                            com.example.data.model.FriendshipStatus.valueOf(statusStr)
                        }.getOrDefault(com.example.data.model.FriendshipStatus.ACCEPTED)
                        com.example.data.model.FriendItem(
                            id = doc.id,
                            name = doc.getString("name") ?: "مستخدم HUH",
                            country = doc.getString("country") ?: "العالم",
                            age = (doc.getLong("age") ?: 24L).toInt(),
                            status = status,
                            lastMessage = doc.getString("lastMessage") ?: "",
                            unreadCount = (doc.getLong("unreadCount") ?: 0L).toInt(),
                            isOnline = doc.getBoolean("isOnline") ?: false
                        )
                    }.getOrNull()
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendFriendRequest(
        targetUid: String,
        targetName: String,
        targetCountry: String,
        myName: String,
        myCountry: String
    ): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val authUid = currentUid() ?: return Result.failure(SecurityException("المصادقة مطلوبة"))
        if (targetUid.isBlank() || targetUid == authUid) {
            return Result.failure(IllegalArgumentException("معرّف الصديق غير صالح"))
        }
        return try {
            val outgoing = hashMapOf<String, Any>(
                "name" to targetName.ifBlank { "مستخدم HUH" }.take(80),
                "country" to targetCountry.ifBlank { "العالم" }.take(80),
                "status" to "PENDING_OUTGOING",
                "lastMessage" to "تم إرسال طلب الصداقة",
                "isOnline" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val incoming = hashMapOf<String, Any>(
                "name" to myName.ifBlank { "مستخدم HUH" }.take(80),
                "country" to myCountry.ifBlank { "العالم" }.take(80),
                "status" to "PENDING_INCOMING",
                "lastMessage" to "يرغب في إضافتك كصديق",
                "isOnline" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            fs.collection("users").document(authUid).collection("friends").document(targetUid)
                .set(outgoing, SetOptions.merge()).await()
            fs.collection("users").document(targetUid).collection("friends").document(authUid)
                .set(incoming, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$authUid/friends/$targetUid")
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(
        targetUid: String,
        myName: String,
        myCountry: String
    ): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val authUid = currentUid() ?: return Result.failure(SecurityException("المصادقة مطلوبة"))
        return try {
            val mine = hashMapOf<String, Any>(
                "status" to "ACCEPTED",
                "lastMessage" to "تم قبول الصداقة الآن",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val theirs = hashMapOf<String, Any>(
                "name" to myName.ifBlank { "مستخدم HUH" }.take(80),
                "country" to myCountry.ifBlank { "العالم" }.take(80),
                "status" to "ACCEPTED",
                "lastMessage" to "تم قبول الصداقة الآن",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            fs.collection("users").document(authUid).collection("friends").document(targetUid)
                .set(mine, SetOptions.merge()).await()
            fs.collection("users").document(targetUid).collection("friends").document(authUid)
                .set(theirs, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$authUid/friends/$targetUid")
            Result.failure(e)
        }
    }

    suspend fun rejectOrUnfriend(targetUid: String): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val authUid = currentUid() ?: return Result.failure(SecurityException("المصادقة مطلوبة"))
        return try {
            fs.collection("users").document(authUid).collection("friends").document(targetUid).delete().await()
            runCatching {
                fs.collection("users").document(targetUid).collection("friends").document(authUid).delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$authUid/friends/$targetUid")
            Result.failure(e)
        }
    }

    fun privateChatId(uid1: String, uid2: String): String =
        listOf(uid1.trim(), uid2.trim()).sorted().joinToString("_")

    fun observePrivateChatMessages(
        myUid: String,
        friendUid: String
    ): Flow<List<com.example.data.model.PrivateChatMessage>> = callbackFlow {
        val fs = firestore
        val authUid = currentUid()
        if (fs == null || authUid == null || myUid.isBlank() || friendUid.isBlank() || authUid != myUid) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val chatId = privateChatId(myUid, friendUid)
        val path = "private_chats/$chatId/messages"
        val reg = fs.collection("private_chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    return@addSnapshotListener
                }
                val msgs = snapshot?.documents?.mapNotNull { doc ->
                    runCatching {
                        com.example.data.model.PrivateChatMessage(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: "",
                            text = doc.getString("text"),
                            textColorHex = doc.getString("textColorHex") ?: "#FFFFFF",
                            bgColorHex = doc.getString("bgColorHex") ?: "#1E1B2E",
                            mediaResId = doc.getLong("mediaResId")?.toInt(),
                            mediaUri = doc.getString("mediaUri"),
                            mediaPriceCoins = (doc.getLong("mediaPriceCoins") ?: 0L).toInt(),
                            isUnlocked = doc.getBoolean("isUnlocked") ?: false,
                            unlockProgress = (doc.getDouble("unlockProgress") ?: 0.0).toFloat(),
                            isVoiceMessage = doc.getBoolean("isVoiceMessage") ?: false,
                            voiceDurationSeconds = (doc.getLong("voiceDurationSeconds") ?: 0L).toInt(),
                            voiceFilePath = doc.getString("voiceFilePath"),
                            voiceAudioBase64 = doc.getString("voiceAudioBase64"),
                            translatedText = doc.getString("translatedText"),
                            callLogRecordId = doc.getString("callLogRecordId"),
                            callLogIsVideo = doc.getBoolean("callLogIsVideo") ?: false,
                            callLogStatus = doc.getString("callLogStatus"),
                            callLogDurationSeconds = (doc.getLong("callLogDurationSeconds") ?: 0L).toInt(),
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }.getOrNull()
                } ?: emptyList()
                trySend(msgs)
            }
        awaitClose { reg.remove() }
    }

    suspend fun canSendDirectMessage(
        targetUid: String,
        isAcceptedFriend: Boolean,
        isSenderVerified: Boolean
    ): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        val authUid = currentUid() ?: return Result.failure(SecurityException("المصادقة مطلوبة"))
        if (targetUid.isBlank()) return Result.failure(IllegalArgumentException("معرّف المستلم غير صالح"))

        val isBlockedLocally = runCatching {
            fs.collection("users").document(authUid).collection("blocks").document(targetUid).get().await().exists()
        }.getOrDefault(false)
        if (isBlockedLocally) {
            return Result.failure(SecurityException("قمت بحظر هذا المستخدم — لا يمكن إرسال رسائل."))
        }

        return try {
            val targetDoc = fs.collection("users").document(targetUid).get().await()
            if (!targetDoc.exists()) return Result.success(Unit)
            val visibility = targetDoc.getString("profileVisibility") ?: "PUBLIC"
            if (visibility == "PRIVATE_HIDDEN" && !isAcceptedFriend) {
                return Result.failure(SecurityException("حساب هذا المستخدم خاص ومخفي حالياً."))
            }
            when (targetDoc.getString("directMessagePermission") ?: "EVERYONE") {
                "NOBODY" -> Result.failure(SecurityException("أوقف هذا المستخدم استقبال الرسائل الخاصة."))
                "FRIENDS_ONLY" -> if (isAcceptedFriend) Result.success(Unit) else Result.failure(SecurityException("هذا المستخدم يقبل الرسائل من الأصدقاء فقط."))
                "VERIFIED_VIP_ONLY" -> if (isSenderVerified) Result.success(Unit) else Result.failure(SecurityException("الرسائل متاحة للحسابات الموثقة +18 فقط."))
                else -> Result.success(Unit)
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) && !isAcceptedFriend) {
                Result.failure(SecurityException("خصوصية هذا المستخدم تمنع استقبال رسائل مباشرة."))
            } else {
                Result.success(Unit)
            }
        }
    }

    suspend fun sendPrivateMessage(
        friendUid: String,
        message: com.example.data.model.PrivateChatMessage,
        summaryPreview: String
    ): Result<com.example.data.model.PrivateChatMessage> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val authUid = currentUid() ?: return Result.failure(SecurityException("المصادقة مطلوبة"))
        val chatId = privateChatId(authUid, friendUid)
        val path = "private_chats/$chatId/messages/${message.id}"
        return try {
            val payload = hashMapOf<String, Any>(
                "senderId" to authUid,
                "recipientId" to friendUid,
                "participantIds" to listOf(authUid, friendUid),
                "textColorHex" to message.textColorHex,
                "bgColorHex" to message.bgColorHex,
                "mediaPriceCoins" to message.mediaPriceCoins,
                "isUnlocked" to message.isUnlocked,
                "unlockProgress" to message.unlockProgress.toDouble(),
                "isVoiceMessage" to message.isVoiceMessage,
                "voiceDurationSeconds" to message.voiceDurationSeconds,
                "callLogIsVideo" to message.callLogIsVideo,
                "callLogDurationSeconds" to message.callLogDurationSeconds,
                "timestamp" to message.timestamp
            )
            message.text?.let { payload["text"] = it }
            message.mediaUri?.let { payload["mediaUri"] = it }
            message.mediaResId?.let { payload["mediaResId"] = it }
            message.voiceFilePath?.let { payload["voiceFilePath"] = it }
            message.voiceAudioBase64?.let { payload["voiceAudioBase64"] = it }
            message.translatedText?.let { payload["translatedText"] = it }
            message.callLogRecordId?.let { payload["callLogRecordId"] = it }
            message.callLogStatus?.let { payload["callLogStatus"] = it }

            fs.collection("private_chats").document(chatId)
                .collection("messages").document(message.id)
                .set(payload)
                .await()

            val summaryUpdate = hashMapOf<String, Any>(
                "lastMessage" to summaryPreview.take(120),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            runCatching {
                fs.collection("users").document(authUid).collection("friends").document(friendUid)
                    .set(summaryUpdate, SetOptions.merge()).await()
                fs.collection("users").document(friendUid).collection("friends").document(authUid)
                    .set(summaryUpdate, SetOptions.merge()).await()
            }
            Result.success(message.copy(senderId = authUid))
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markPrivateMediaUnlocked(friendUid: String, messageId: String): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val authUid = currentUid() ?: return Result.failure(SecurityException("المصادقة مطلوبة"))
        val chatId = privateChatId(authUid, friendUid)
        return try {
            fs.collection("private_chats").document(chatId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "isUnlocked" to true,
                        "unlockProgress" to 1.0
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "private_chats/$chatId/messages/$messageId")
            Result.failure(e)
        }
    }

    suspend fun updatePrivateMessageTranslation(friendUid: String, messageId: String, translatedText: String) {
        val fs = firestore ?: return
        val authUid = currentUid() ?: return
        val chatId = privateChatId(authUid, friendUid)
        runCatching {
            fs.collection("private_chats").document(chatId)
                .collection("messages").document(messageId)
                .update("translatedText", translatedText)
                .await()
        }
    }

    suspend fun clearOwnPrivateChatMessages(friendUid: String) {
        val fs = firestore ?: return
        val authUid = currentUid() ?: return
        val chatId = privateChatId(authUid, friendUid)
        runCatching {
            val snap = fs.collection("private_chats").document(chatId)
                .collection("messages")
                .whereEqualTo("senderId", authUid)
                .get()
                .await()
            for (doc in snap.documents) {
                doc.reference.delete().await()
            }
        }
    }
}
