package com.example.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** Shared access point for the app's default Cloud Firestore database. */
class FirestoreRepository(
    private val firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() }
) {
    private val firestore: FirebaseFirestore
        get() = firestoreProvider()

    private val auth: FirebaseAuth
        get() = authProvider()

    enum class Collection(val id: String) {
        ANNOUNCEMENTS("announcements"),
        AMENITY_RESERVATIONS("amenity_reservations"),
        COMMUNITY_EVENTS("community_events"),
        MARKETPLACE_ITEMS("marketplace_items"),
        COMMUNITY_CHAT("community_chat"),
        HOMEOWNER_CONCERNS("homeowner_concerns")
    }

    fun observe(
        collection: Collection,
        onEvent: (QuerySnapshot?, FirebaseFirestoreException?) -> Unit
    ): ListenerRegistration = observeQuery(collection, onEvent) { it }

    fun observeOwned(
        collection: Collection,
        ownerField: String,
        ownerUid: String,
        onEvent: (QuerySnapshot?, FirebaseFirestoreException?) -> Unit
    ): ListenerRegistration = observeQuery(collection, onEvent) { query ->
        query.whereEqualTo(ownerField, ownerUid)
    }

    private fun observeQuery(
        collection: Collection,
        onEvent: (QuerySnapshot?, FirebaseFirestoreException?) -> Unit,
        buildQuery: (com.google.firebase.firestore.Query) -> com.google.firebase.firestore.Query
    ): ListenerRegistration {
        if (auth.currentUser?.uid.isNullOrBlank()) {
            onEvent(null, FirebaseFirestoreException("Sign in to access community data.", FirebaseFirestoreException.Code.UNAUTHENTICATED))
            return object : ListenerRegistration {
                override fun remove() = Unit
            }
        }
        return buildQuery(firestore.collection(collection.id)).addSnapshotListener { snapshot, error ->
            onEvent(snapshot, error)
        }
    }

    fun observeChatReplies(
        postId: String,
        onEvent: (QuerySnapshot?, FirebaseFirestoreException?) -> Unit
    ): ListenerRegistration = observeSubcollection("replies", postId, onEvent)

    fun observeChatReactions(
        postId: String,
        onEvent: (QuerySnapshot?, FirebaseFirestoreException?) -> Unit
    ): ListenerRegistration = observeSubcollection("reactions", postId, onEvent)

    private fun observeSubcollection(
        childCollection: String,
        postId: String,
        onEvent: (QuerySnapshot?, FirebaseFirestoreException?) -> Unit
    ): ListenerRegistration {
        if (auth.currentUser?.uid.isNullOrBlank()) {
            onEvent(null, FirebaseFirestoreException("Sign in to access community data.", FirebaseFirestoreException.Code.UNAUTHENTICATED))
            return object : ListenerRegistration {
                override fun remove() = Unit
            }
        }
        return firestore.collection(Collection.COMMUNITY_CHAT.id)
            .document(postId)
            .collection(childCollection)
            .addSnapshotListener { snapshot, error -> onEvent(snapshot, error) }
    }

    /** Creates a user-authored record and waits for the server to confirm the write. */
    suspend fun create(
        collection: Collection,
        fields: Map<String, Any?>,
        authorField: String = "creatorUid"
    ): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Sign in before creating a community record.")
        require(fields.keys.none { it == authorField || it == "createdAt" }) {
            "Author and creation timestamp are managed by the repository."
        }
        val imageUrls = fields["imageUrls"] as? List<*>
        require("imageUrls" !in fields || imageUrls != null) {
            "Image references must be a list of uploaded HTTPS URLs."
        }
        require(imageUrls == null || imageUrls.all { it is String && it.startsWith("https://") }) {
            "Only uploaded HTTPS image URLs can be saved. Local device images remain previews."
        }
        val document = firestore.collection(collection.id).document()
        val data = fields.toMutableMap().apply {
            put(authorField, uid)
            put("createdAt", FieldValue.serverTimestamp())
        }
        document.set(data).await()
        return document.id
    }

    suspend fun updateCurrentUserProfile(fields: Map<String, Any?>) {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Sign in before saving your profile.")
        require(fields.keys.none { it == "uid" || it == "updatedAt" }) {
            "UID and update timestamp are managed by the repository."
        }
        firestore.collection("users").document(uid).set(
            fields.toMutableMap().apply {
                put("uid", uid)
                put("updatedAt", FieldValue.serverTimestamp())
            },
            SetOptions.merge()
        ).await()
    }

    suspend fun createChatReply(postId: String, senderName: String, text: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Sign in before adding a community reply.")
        val reply = firestore.collection(Collection.COMMUNITY_CHAT.id)
            .document(postId).collection("replies").document()
        reply.set(mapOf(
            "senderUid" to uid,
            "senderName" to senderName,
            "text" to text.trim(),
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
        return reply.id
    }

    suspend fun setChatReaction(postId: String, reaction: String) {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Sign in before reacting to a community post.")
        firestore.collection(Collection.COMMUNITY_CHAT.id)
            .document(postId).collection("reactions").document(uid)
            .set(mapOf(
                "uid" to uid,
                "reaction" to reaction,
                "updatedAt" to FieldValue.serverTimestamp()
            )).await()
    }
}
