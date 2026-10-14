package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserRepository(private val context: Context) {

    private val firestore: FirebaseFirestore? by lazy {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            try {
                FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun isDatabaseReady(): Boolean = firestore != null

    suspend fun saveResidentProfile(fields: Map<String, Any?>): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("Sign in before saving your profile."))
        val allowedFields = setOf("contact", "block", "lot", "phase", "proofDocumentPath", "photoStoragePath")
        require(fields.keys.all { it in allowedFields }) { "The profile update contains an unsupported field." }
        val db = firestore ?: return@withContext Result.failure(
            IllegalStateException("Firestore database is not initialized.")
        )
        try {
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                ?: return@withContext Result.failure(IllegalStateException("Sign in before saving your profile."))
            val profileRef = db.collection("users").document(uid)
            val existing = profileRef.get().await()
            val update = fields + ("updatedAt" to FieldValue.serverTimestamp())
            if (existing.exists()) {
                profileRef.update(update).await()
            } else {
                val email = user.email.orEmpty()
                val usernameStem = email.substringBefore("@").lowercase()
                    .replace(Regex("[^a-z0-9_]"), "_").trim('_').take(11).ifBlank { "resident" }
                val username = "${usernameStem}_${uid.takeLast(8)}".take(30)
                profileRef.set(update + mapOf(
                    "uid" to uid,
                    "email" to email,
                    "username" to username,
                    "displayName" to (user.displayName ?: ""),
                    "accountStatus" to "active",
                    "role" to "Visitor",
                    "isAdmin" to false,
                    "roleRequestStatus" to "none",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp()
                )).await()
            }
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun uploadVerificationDocument(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("Sign in before uploading verification."))
        try {
            val path = "profile-verification/$uid/${System.currentTimeMillis()}.jpg"
            val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
            FirebaseStorage.getInstance().reference.child(path)
                .putFile(uri, com.google.firebase.storage.StorageMetadata.Builder().setContentType(contentType).build())
                .await()
            Result.success(path)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun uploadProfilePhoto(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("Sign in before uploading a profile photo."))
        try {
            val path = "profile-images/$uid/profile_${System.currentTimeMillis()}"
            val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
            FirebaseStorage.getInstance().reference.child(path)
                .putFile(uri, com.google.firebase.storage.StorageMetadata.Builder().setContentType(contentType).build()).await()
            Result.success(path)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /**
     * Observes real-time profile updates for a given user UID.
     */
    fun observeUserProfile(uid: String): Flow<UserProfile?> {
        val db = firestore
        if (db == null || uid.isBlank()) {
            return flow { emit(null) }
        }

        val docRef = db.collection("users").document(uid)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java)
                } else {
                    null
                }
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Fetches user profile once, with local cache fallback if offline.
     */
    suspend fun getUserProfile(uid: String): Result<UserProfile?> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(
            IllegalStateException("Firestore database is not initialized. Please verify google-services.json.")
        )
        try {
            val snapshot = db.collection("users").document(uid).get().await()
            val profile = if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
            Result.success(profile)
        } catch (e: Exception) {
            try {
                val cacheSnapshot = db.collection("users").document(uid).get(com.google.firebase.firestore.Source.CACHE).await()
                val profile = if (cacheSnapshot.exists()) cacheSnapshot.toObject(UserProfile::class.java) else null
                Result.success(profile)
            } catch (cacheException: Exception) {
                handleFirestoreError(e, OperationType.GET, "users/$uid")
                Result.failure(e)
            }
        }
    }

    /**
     * Syncs email verification status in Firestore.
     */
    suspend fun syncEmailVerificationStatus(uid: String, isVerified: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.success(Unit)
        try {
            db.collection("users").document(uid).update(
                mapOf(
                    "emailVerified" to isVerified,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically reserves a username and creates the user profile in a batch write.
     */
    suspend fun createUserProfileWithReservation(
        profile: UserProfile
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(
            IllegalStateException("Firestore is not initialized. Please configure google-services.json.")
        )

        val normalizedUsername = profile.username.trim().lowercase()
        val userDocRef = db.collection("users").document(profile.uid)
        val usernameDocRef = db.collection("usernames").document(normalizedUsername)

        try {
            // Run transaction to ensure username uniqueness and make retries idempotent.
            db.runTransaction { transaction ->
                val existingReservation = transaction.get(usernameDocRef)
                val existingUser = transaction.get(userDocRef)
                if (existingReservation.exists() && existingReservation.getString("uid") != profile.uid) {
                    throw IllegalStateException("Username '$normalizedUsername' is already taken.")
                }

                if (existingUser.exists()) {
                    if (existingUser.getString("uid") != profile.uid ||
                        existingUser.getString("username")?.trim()?.lowercase() != normalizedUsername
                    ) {
                        throw IllegalStateException("A different Firestore profile already exists for this account.")
                    }
                    if (!existingReservation.exists()) {
                        transaction.set(usernameDocRef, mapOf(
                            "username" to normalizedUsername,
                            "uid" to profile.uid,
                            "createdAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    return@runTransaction
                }

                if (!existingReservation.exists()) {
                    val usernameData = mapOf(
                        "username" to normalizedUsername,
                        "uid" to profile.uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    transaction.set(usernameDocRef, usernameData)
                }

                val userPayload = mapOf(
                    "uid" to profile.uid,
                    "email" to profile.email,
                    "username" to normalizedUsername,
                    "displayName" to profile.displayName.ifBlank { profile.username },
                    "photoUrl" to profile.photoUrl,
                    "providerIds" to profile.providerIds,
                    "emailVerified" to profile.emailVerified,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "termsAcceptedAt" to FieldValue.serverTimestamp(),
                    "privacyPolicyAcceptedAt" to FieldValue.serverTimestamp(),
                    "accountStatus" to "active",
                    "role" to "Visitor",
                    "isAdmin" to false,
                    "roleRequestStatus" to "none"
                )
                transaction.set(userDocRef, userPayload)
                Unit
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/${profile.uid}")
            Result.failure(e)
        }
    }

    /**
     * Saves or updates a user profile from Google Sign-In or social provider.
     */
    suspend fun saveOrUpdateGoogleUserProfile(
        uid: String,
        email: String,
        displayName: String,
        photoUrl: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(
            IllegalStateException("Firestore is not initialized.")
        )

        val userDocRef = db.collection("users").document(uid)
        try {
            val usernameStem = email.substringBefore("@").lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .trim('_')
                .take(11)
                .ifBlank { "resident" }
            val uidSuffix = uid.takeLast(8).lowercase().replace(Regex("[^a-z0-9_]"), "0")
            val derivedUsername = "${usernameStem}_$uidSuffix".take(20)
            val usernameDocRef = db.collection("usernames").document(derivedUsername)

            db.runTransaction { transaction ->
                val existingProfile = transaction.get(userDocRef)
                if (existingProfile.exists()) {
                    val existingUsername = existingProfile.getString("username")?.trim()?.lowercase()
                    if (!existingUsername.isNullOrBlank()) {
                        val existingUsernameRef = db.collection("usernames").document(existingUsername)
                        val reservation = transaction.get(existingUsernameRef)
                        if (reservation.exists() && reservation.getString("uid") != uid) {
                            throw IllegalStateException("Your username reservation is associated with another account.")
                        }
                        if (!reservation.exists()) {
                            transaction.set(existingUsernameRef, mapOf(
                                "username" to existingUsername,
                                "uid" to uid,
                                "createdAt" to FieldValue.serverTimestamp()
                            ))
                        }
                    }
                    transaction.update(userDocRef, mapOf(
                        "lastLoginAt" to FieldValue.serverTimestamp(),
                        "emailVerified" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                } else {
                    val reservation = transaction.get(usernameDocRef)
                    if (reservation.exists() && reservation.getString("uid") != uid) {
                        throw IllegalStateException("Could not reserve a unique username for this account.")
                    }
                    if (!reservation.exists()) {
                        transaction.set(usernameDocRef, mapOf(
                            "username" to derivedUsername,
                            "uid" to uid,
                            "createdAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    transaction.set(userDocRef, mapOf(
                        "uid" to uid,
                        "email" to email.trim(),
                        "username" to derivedUsername,
                        "displayName" to displayName,
                        "photoUrl" to photoUrl,
                        "providerIds" to listOf("google.com"),
                        "emailVerified" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "lastLoginAt" to FieldValue.serverTimestamp(),
                        "termsAcceptedAt" to FieldValue.serverTimestamp(),
                        "privacyPolicyAcceptedAt" to FieldValue.serverTimestamp(),
                        "accountStatus" to "active",
                        "role" to "Visitor",
                        "isAdmin" to false,
                        "roleRequestStatus" to "none"
                    ))
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    /**
     * Updates the lastLoginAt timestamp on login using merge so it never fails if document is missing.
     */
    suspend fun updateLastLogin(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.success(Unit)
        try {
            val userDocRef = db.collection("users").document(uid)
            if (!userDocRef.get().await().exists()) {
                return@withContext Result.failure(
                    IllegalStateException("Your Firestore profile is missing. Please complete account setup before updating login details.")
                )
            }
            userDocRef.update(mapOf(
                "lastLoginAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }
}
