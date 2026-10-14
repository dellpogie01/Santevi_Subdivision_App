package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AuthRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nexus_auth_prefs", Context.MODE_PRIVATE)

    init {
        // Remove passwords stored by older app versions; they are not safe to retain here.
        prefs.edit().remove("pref_saved_password").apply()
    }

    private val firebaseAuth: FirebaseAuth? by lazy {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            try {
                FirebaseAuth.getInstance()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun isFirebaseConfigured(): Boolean = firebaseAuth != null

    val currentUser: FirebaseUser?
        get() = firebaseAuth?.currentUser

    /**
     * Checks if a valid session exists on app launch, respecting Remember Me preference.
     */
    fun hasValidSession(): Boolean {
        val user = currentUser ?: return false
        val rememberMe = getRememberMePreference()
        return rememberMe && user.uid.isNotEmpty()
    }

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val auth = firebaseAuth
        if (auth == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = FirebaseAuth.AuthStateListener { currentAuth ->
            trySend(currentAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> =
        withContext(Dispatchers.IO) {
            val auth = firebaseAuth ?: return@withContext Result.failure(
                IllegalStateException("Firebase Auth is not configured. Please add google-services.json to the app folder as described in README.md.")
            )
            try {
                val authResult = withTimeout(8000L) {
                    auth.signInWithEmailAndPassword(email.trim(), password).await()
                }
                val user = authResult.user
                    ?: return@withContext Result.failure(IllegalStateException("Authentication succeeded but user is null."))
                Result.success(user)
            } catch (e: TimeoutCancellationException) {
                Log.e("AuthRepository", "Sign-in timed out after 8 seconds")
                Result.failure(Exception("Sign-in request timed out. Please check your network connection."))
            } catch (e: Exception) {
                Log.e("AuthRepository", "Sign in failed: ${e.message}")
                Result.failure(mapAuthException(e))
            }
        }

    suspend fun signUpWithEmail(email: String, password: String): Result<FirebaseUser> =
        withContext(Dispatchers.IO) {
            val auth = firebaseAuth ?: return@withContext Result.failure(
                IllegalStateException("Firebase Auth is not configured. Please add google-services.json to the app folder as described in README.md.")
            )
            try {
                val authResult = withTimeout(15000L) {
                    auth.createUserWithEmailAndPassword(email.trim(), password).await()
                }
                val user = authResult.user
                    ?: return@withContext Result.failure(IllegalStateException("Account created but user object is null."))
                // Automatically dispatch email verification
                try {
                    withTimeout(5000L) {
                        user.sendEmailVerification().await()
                    }
                } catch (verifyError: Exception) {
                    Log.w("AuthRepository", "Failed to dispatch email verification: ${verifyError.message}")
                }
                Result.success(user)
            } catch (e: TimeoutCancellationException) {
                Log.e("AuthRepository", "Sign up timed out after 15 seconds")
                Result.failure(Exception("Registration request timed out. Please check your network connection."))
            } catch (e: Exception) {
                Log.e("AuthRepository", "Registration failed: ${e.message}")
                Result.failure(mapAuthException(e))
            }
        }

    suspend fun signInWithGoogleCredential(idToken: String): Result<FirebaseUser> =
        withContext(Dispatchers.IO) {
            val auth = firebaseAuth ?: return@withContext Result.failure(
                IllegalStateException("Firebase Auth is not configured. Please add google-services.json to the app folder as described in README.md.")
            )
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user
                    ?: return@withContext Result.failure(IllegalStateException("Google sign-in succeeded but user is null."))
                Result.success(user)
            } catch (e: Exception) {
                Log.e("AuthRepository", "Google sign-in failed: ${e.message}")
                Result.failure(mapAuthException(e))
            }
        }

    suspend fun sendPasswordReset(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val auth = firebaseAuth ?: return@withContext Result.failure(
                IllegalStateException("Firebase Auth is not configured. Please add google-services.json to the app folder as described in README.md.")
            )
            try {
                auth.sendPasswordResetEmail(email.trim()).await()
                Result.success(Unit)
            } catch (e: Exception) {
                if (e is FirebaseAuthException && e.errorCode == "ERROR_USER_NOT_FOUND") {
                    // Keep the response indistinguishable from a registered address.
                    return@withContext Result.success(Unit)
                }
                Log.e("AuthRepository", "Password reset failed: ${e.message}")
                Result.failure(mapAuthException(e))
            }
        }

    suspend fun sendEmailVerification(): Result<Unit> =
        withContext(Dispatchers.IO) {
            val user = currentUser ?: return@withContext Result.failure(
                IllegalStateException("No authenticated user to verify.")
            )
            try {
                user.sendEmailVerification().await()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("AuthRepository", "Resend verification failed: ${e.message}")
                Result.failure(mapAuthException(e))
            }
        }

    suspend fun reloadUser(): Result<FirebaseUser?> =
        withContext(Dispatchers.IO) {
            val user = currentUser ?: return@withContext Result.success(null)
            try {
                user.reload().await()
                Result.success(currentUser)
            } catch (e: Exception) {
                Result.failure(mapAuthException(e))
            }
        }

    suspend fun signOut(credentialManager: CredentialManager): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                firebaseAuth?.signOut()
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("AuthRepository", "Failed to clear credential manager state: ${e.message}")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun getRememberMePreference(): Boolean =
        prefs.getBoolean("pref_remember_me", true)

    fun setRememberMePreference(remember: Boolean) {
        prefs.edit().putBoolean("pref_remember_me", remember).apply()
    }

    fun getSavedEmail(): String =
        prefs.getString("pref_saved_email", "") ?: ""

    fun setSavedEmail(email: String) {
        prefs.edit().putString("pref_saved_email", email).apply()
    }

    private fun mapAuthException(e: Exception): Exception {
        if (e is FirebaseAuthException) {
            val friendly = when (e.errorCode) {
                "ERROR_INVALID_EMAIL" -> "The email address is improperly formatted."
                "ERROR_WRONG_PASSWORD" -> "The password entered is incorrect. Please try again."
                "ERROR_USER_NOT_FOUND" -> "No account found matching this email address."
                "ERROR_USER_DISABLED" -> "This user account has been disabled."
                "ERROR_TOO_MANY_REQUESTS" -> "Too many requests. Firebase has temporarily blocked requests from this device due to unusual activity. Please try again in a few minutes."
                "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email address already exists."
                "ERROR_WEAK_PASSWORD" -> "The password provided is too weak. Please use at least 8 characters."
                "ERROR_OPERATION_NOT_ALLOWED" -> "This sign-in method is not enabled in Firebase Console."
                "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Please check your internet connection."
                "ERROR_INVALID_CREDENTIAL" -> "Invalid email or password. Please double check your details."
                else -> e.localizedMessage ?: "Authentication failed."
            }
            return Exception(friendly, e)
        }
        val msg = e.message ?: ""
        if (msg.contains("invalid-credential", ignoreCase = true) || msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true)) {
            return Exception("Invalid email or password. Please check your details and try again.", e)
        }
        if (msg.contains("blocked all requests", ignoreCase = true) || msg.contains("too many requests", ignoreCase = true)) {
            return Exception("Firebase has temporarily blocked requests from this device due to multiple attempts. Please check your email inbox/spam folder, or try again in a few minutes.", e)
        }
        return e
    }
}
