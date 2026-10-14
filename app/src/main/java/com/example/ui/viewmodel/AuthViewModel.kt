package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import android.util.Patterns
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserProfile
import com.example.data.util.PasswordPolicy
import com.example.data.repository.AuthRepository
import com.example.data.repository.UserRepository
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.NotificationTokenRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(application)
    private val userRepository = UserRepository(application)
    private val notificationFirestoreRepository = FirestoreRepository()
    private val notificationTokenRepository by lazy { NotificationTokenRepository(application) }

    private val _loginState = MutableStateFlow(
        run {
            val savedEmail = authRepository.getSavedEmail()
            LoginFormState(
                email = if (authRepository.getRememberMePreference()) savedEmail else "",
                rememberMe = false
            )
        }
    )
    val loginState: StateFlow<LoginFormState> = _loginState.asStateFlow()

    private val _signUpState = MutableStateFlow(SignUpFormState())
    val signUpState: StateFlow<SignUpFormState> = _signUpState.asStateFlow()

    private val _forgotPasswordState = MutableStateFlow(ForgotPasswordFormState())
    val forgotPasswordState: StateFlow<ForgotPasswordFormState> = _forgotPasswordState.asStateFlow()

    private val _homeState = MutableStateFlow(
        HomeUiState(
            firebaseUser = authRepository.currentUser,
            isDatabaseConfigured = userRepository.isDatabaseReady()
        )
    )
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    init {
        // Observe auth state changes to keep home screen updated
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                _homeState.update { it.copy(firebaseUser = user) }
                if (user != null) {
                    val isAdmin = runCatching { user.getIdToken(false).await().claims["admin"] == true }.getOrDefault(false)
                    _homeState.update { it.copy(isTrustedAdmin = isAdmin) }
                    loadUserProfile(user.uid)
                } else {
                    _homeState.update { it.copy(userProfile = null, isTrustedAdmin = false) }
                }
            }
        }
    }

    fun hasValidSession(): Boolean = authRepository.hasValidSession()

    // -------------------------------------------------------------------------
    // Login Screen Handlers
    // -------------------------------------------------------------------------

    fun onLoginEmailChanged(email: String) {
        _loginState.update { it.copy(email = email, emailError = null, generalError = null) }
    }

    fun onLoginPasswordChanged(password: String) {
        _loginState.update { it.copy(password = password, passwordError = null, generalError = null) }
    }

    fun toggleLoginPasswordVisibility() {
        _loginState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onRememberMeChanged(remember: Boolean) {
        authRepository.setRememberMePreference(remember)
        if (!remember) {
            authRepository.setSavedEmail("")
            _loginState.update {
                it.copy(
                    rememberMe = false,
                    email = "",
                    password = ""
                )
            }
        } else {
            val savedEmail = authRepository.getSavedEmail().ifBlank { authRepository.currentUser?.email.orEmpty() }
            _loginState.update {
                it.copy(
                    rememberMe = true,
                    email = if (it.email.isBlank()) savedEmail else it.email,
                    password = ""
                )
            }
        }
    }

    fun login(onSuccess: () -> Unit) {
        val state = _loginState.value
        val email = state.email.trim()
        val password = state.password

        var hasError = false
        var emailErr: String? = null
        var passErr: String? = null

        if (email.isEmpty()) {
            emailErr = "Email address is required."
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailErr = "Please enter a valid email address."
            hasError = true
        }

        if (password.isEmpty()) {
            passErr = "Password is required."
            hasError = true
        }

        if (hasError) {
            _loginState.update { it.copy(emailError = emailErr, passwordError = passErr) }
            return
        }

        _loginState.update { it.copy(isLoading = true, generalError = null) }

        viewModelScope.launch {
            val result = authRepository.signInWithEmail(email, password)
            result.fold(
                onSuccess = { user ->
                    if (state.rememberMe) {
                        authRepository.setSavedEmail(email)
                    } else {
                        authRepository.setSavedEmail("")
                    }
                    _loginState.update {
                        it.copy(
                            isLoading = false,
                            email = if (state.rememberMe) email else "",
                            password = ""
                        )
                    }
                    viewModelScope.launch {
                        userRepository.updateLastLogin(user.uid).onFailure { error ->
                            _homeState.update {
                                it.copy(generalError = error.message ?: "Could not update your Firestore login record.")
                            }
                        }
                    }
                    onSuccess()
                },
                onFailure = { error ->
                    _loginState.update {
                        it.copy(
                            isLoading = false,
                            generalError = error.message ?: "Sign-in failed."
                        )
                    }
                }
            )
        }
    }

    // -------------------------------------------------------------------------
    // Sign-Up Screen Handlers
    // -------------------------------------------------------------------------

    fun onSignUpEmailChanged(email: String) {
        _signUpState.update { it.copy(email = email, emailError = null, generalError = null) }
    }

    fun onSignUpConfirmEmailChanged(confirmEmail: String) {
        _signUpState.update { it.copy(confirmEmail = confirmEmail, confirmEmailError = null, generalError = null) }
    }

    fun onSignUpUsernameChanged(username: String) {
        _signUpState.update { it.copy(username = username, usernameError = null, generalError = null) }
    }

    fun onSignUpPasswordChanged(password: String) {
        _signUpState.update { it.copy(password = password, passwordError = null, generalError = null) }
    }

    fun onSignUpConfirmPasswordChanged(confirm: String) {
        _signUpState.update { it.copy(confirmPassword = confirm, confirmPasswordError = null, generalError = null) }
    }

    fun toggleSignUpPasswordVisibility() {
        _signUpState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleSignUpConfirmPasswordVisibility() {
        _signUpState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    fun onAgreeTermsChanged(agree: Boolean) {
        _signUpState.update { it.copy(agreeTerms = agree, termsError = null) }
    }

    fun onAgreeCommunityChanged(agree: Boolean) {
        _signUpState.update { it.copy(agreeCommunity = agree, communityError = null, generalError = null) }
    }

    fun signUp(onSuccess: () -> Unit) {
        val state = _signUpState.value
        val email = state.email.trim()
        val username = state.username.trim()
        val password = state.password
        val confirm = state.confirmPassword

        var hasError = false
        var emailErr: String? = null
        var userErr: String? = null
        var passErr: String? = null
        var confErr: String? = null
        var termsErr: String? = null

        if (email.isEmpty()) {
            emailErr = "Email address is required."
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailErr = "Invalid email format."
            hasError = true
        }

        val usernamePattern = "^[a-zA-Z0-9_]{3,20}$".toRegex()
        if (username.isEmpty()) {
            userErr = "Username is required."
            hasError = true
        } else if (!usernamePattern.matches(username)) {
            userErr = "Username must be 3-20 characters (letters, numbers, underscores)."
            hasError = true
        }

        if (password.isEmpty()) {
            passErr = "Password is required."
            hasError = true
        } else if (!PasswordPolicy.meetsMinimumLength(password)) {
            passErr = PasswordPolicy.MIN_LENGTH_MESSAGE
            hasError = true
        } else if (!password.any { it.isDigit() } || !password.any { it.isLetter() }) {
            passErr = "Password must contain both letters and numbers."
            hasError = true
        }

        if (confirm.isEmpty()) {
            confErr = "Please confirm your password."
            hasError = true
        } else if (password != confirm) {
            confErr = "Passwords do not match."
            hasError = true
        }

        if (!state.agreeTerms) {
            termsErr = "You must agree to the Terms of Service & Privacy Policy."
            hasError = true
        }
        val communityErr = if (!state.agreeCommunity) "You must agree to the Community Code of Conduct." else null
        if (communityErr != null) hasError = true

        if (hasError) {
            _signUpState.update {
                it.copy(
                    emailError = emailErr,
                    usernameError = userErr,
                    passwordError = passErr,
                    confirmPasswordError = confErr,
                    termsError = termsErr,
                    communityError = communityErr,
                    generalError = if (email.isBlank() && username.isBlank() && password.isBlank() && confirm.isBlank() && !state.agreeTerms && !state.agreeCommunity) {
                        "Complete all fields and accept both agreements."
                    } else null
                )
            }
            return
        }

        _signUpState.update { it.copy(isLoading = true, generalError = null) }

        viewModelScope.launch {
            // If Auth succeeded earlier but the profile transaction failed, let
            // the resident retry with a corrected username without recreating Auth.
            val existingUser = authRepository.currentUser
            val authResult = if (existingUser?.email.equals(email, ignoreCase = true)) {
                Result.success(existingUser!!)
            } else {
                authRepository.signUpWithEmail(email, password)
            }
            authResult.fold(
                onSuccess = { user ->
                    // Create UserProfile in Cloud Firestore
                    val profile = UserProfile(
                        uid = user.uid,
                        email = email.trim(),
                        username = username.trim().lowercase(),
                        displayName = username.trim(),
                        photoUrl = "",
                        providerIds = listOf("password"),
                        emailVerified = false,
                        accountStatus = "active"
                    )
                    val profileResult = userRepository.createUserProfileWithReservation(profile)
                    if (profileResult.isFailure) {
                        val cause = profileResult.exceptionOrNull()
                        Log.w("AuthViewModel", "Firestore profile creation failed: ${cause?.message}")
                        _signUpState.update {
                            it.copy(
                                isLoading = false,
                                generalError = cause?.message
                                    ?: "Account created, but profile setup failed. Sign in again or contact support."
                            )
                        }
                        return@fold
                    }

                    _signUpState.update {
                        it.copy(
                            isLoading = false,
                            verificationSentSuccess = true,
                            confirmEmail = "",
                            password = "",
                            confirmPassword = ""
                        )
                    }
                    onSuccess()
                },
                onFailure = { error ->
                    _signUpState.update {
                        it.copy(
                            isLoading = false,
                            generalError = error.message ?: "Registration failed."
                        )
                    }
                }
            )
        }
    }

    // -------------------------------------------------------------------------
    // Google Sign-In Flow
    // -------------------------------------------------------------------------

    fun handleGoogleSignInToken(idToken: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _loginState.update { it.copy(isLoading = true, generalError = null) }

        viewModelScope.launch {
            val result = authRepository.signInWithGoogleCredential(idToken)
            result.fold(
                onSuccess = { user ->
                    val profileResult = userRepository.saveOrUpdateGoogleUserProfile(
                        uid = user.uid,
                        email = user.email ?: "",
                        displayName = user.displayName ?: "",
                        photoUrl = user.photoUrl?.toString() ?: ""
                    )
                    if (profileResult.isFailure) {
                        val message = profileResult.exceptionOrNull()?.message
                            ?: "Could not save your Firestore profile. Please retry sign-in."
                        _loginState.update { it.copy(isLoading = false, generalError = message) }
                        onError(message)
                        return@fold
                    }
                    _loginState.update { it.copy(isLoading = false) }
                    onSuccess()
                },
                onFailure = { error ->
                    val msg = error.message ?: "Google sign-in failed."
                    _loginState.update { it.copy(isLoading = false, generalError = msg) }
                    onError(msg)
                }
            )
        }
    }

    // -------------------------------------------------------------------------
    // Forgot Password Screen Handlers
    // -------------------------------------------------------------------------

    fun onForgotPasswordEmailChanged(email: String) {
        _forgotPasswordState.update { it.copy(email = email, emailError = null, generalError = null) }
    }

    fun sendPasswordReset() {
        val state = _forgotPasswordState.value
        val email = state.email.trim()

        if (email.isEmpty()) {
            _forgotPasswordState.update { it.copy(emailError = "Email address is required.") }
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _forgotPasswordState.update { it.copy(emailError = "Please enter a valid email address.") }
            return
        }

        _forgotPasswordState.update { it.copy(isLoading = true, generalError = null, resetEmailSent = false) }

        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(email)
            result.fold(
                onSuccess = {
                    _forgotPasswordState.update {
                        it.copy(isLoading = false, resetEmailSent = true)
                    }
                },
                onFailure = { error ->
                    _forgotPasswordState.update {
                        it.copy(
                            isLoading = false,
                            generalError = error.message ?: "Failed to send reset link."
                        )
                    }
                }
            )
        }
    }

    fun resetForgotPasswordState() {
        _forgotPasswordState.update { ForgotPasswordFormState() }
    }

    // -------------------------------------------------------------------------
    // Home Screen Handlers
    // -------------------------------------------------------------------------

    private var resendCooldownJob: Job? = null

    fun loadUserProfile(uid: String) {
        val currentUser = authRepository.currentUser
        viewModelScope.launch {
            _homeState.update { it.copy(isLoading = true, generalError = null) }
            val profileResult = userRepository.getUserProfile(uid)
            profileResult.fold(
                onSuccess = { profile ->
                    val finalProfile = profile ?: currentUser?.let { createFallbackProfile(it) }
                    _homeState.update { it.copy(userProfile = finalProfile, isLoading = false) }
                },
                onFailure = { error ->
                    Log.w("AuthViewModel", "Firestore profile load issue: ${error.message}; using fallback profile")
                    val fallback = currentUser?.let { createFallbackProfile(it) }
                    _homeState.update {
                        it.copy(
                            userProfile = fallback,
                            isLoading = false
                        )
                    }
                }
            )
        }
    }

    fun refreshUser() {
        viewModelScope.launch {
            Log.d("AuthViewModel", "Refresh status button clicked")

            _homeState.update {
                it.copy(
                    isRefreshing = true,
                    actionMessage = null,
                    generalError = null
                )
            }

            val reloadResult = authRepository.reloadUser()

            reloadResult.onFailure { error ->
                Log.e("AuthViewModel", "Failed to reload Firebase user", error)
            }

            val user = authRepository.currentUser

            Log.d(
                "AuthViewModel",
                "User refreshed. Email=${user?.email}, verified=${user?.isEmailVerified}"
            )

            _homeState.update { it.copy(firebaseUser = user) }

            if (user != null) {
                if (user.isEmailVerified) {
                    userRepository.syncEmailVerificationStatus(user.uid, true).onFailure { error ->
                        _homeState.update {
                            it.copy(generalError = error.message ?: "Could not update email verification in Firestore.")
                        }
                    }
                }

                userRepository.getUserProfile(user.uid).fold(
                    onSuccess = { profile ->
                        val finalProfile = (profile ?: createFallbackProfile(user)).copy(
                            emailVerified = user.isEmailVerified
                        )
                        _homeState.update {
                            it.copy(
                                userProfile = finalProfile,
                                isRefreshing = false,
                                actionMessage = if (reloadResult.isSuccess) {
                                    if (user.isEmailVerified) "Email verification confirmed!" else "Verification status refreshed."
                                } else {
                                    "Could not refresh Firebase status. Please try again."
                                }
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.w("AuthViewModel", "Failed to load profile from Firestore: ${error.message}")
                        val fallbackProfile = createFallbackProfile(user)
                        _homeState.update {
                            it.copy(
                                userProfile = fallbackProfile,
                                isRefreshing = false,
                                actionMessage = if (reloadResult.isSuccess) {
                                    if (user.isEmailVerified) "Email verification confirmed!" else "Verification status refreshed."
                                } else {
                                    "Status refreshed."
                                }
                            )
                        }
                    }
                )
            } else {
                _homeState.update {
                    it.copy(
                        isRefreshing = false,
                        generalError = "No signed-in Firebase user found."
                    )
                }
            }
        }
    }

    fun resendVerificationEmail() {
        if (_homeState.value.resendCooldownSeconds > 0) {
            _homeState.update {
                it.copy(
                    actionMessage = "Please wait ${_homeState.value.resendCooldownSeconds}s before requesting another verification email."
                )
            }
            return
        }

        viewModelScope.launch {
            Log.d("AuthViewModel", "Resend verification button clicked")

            _homeState.update {
                it.copy(
                    isLoading = true,
                    actionMessage = null,
                    generalError = null
                )
            }

            val result = authRepository.sendEmailVerification()

            result.fold(
                onSuccess = {
                    Log.d("AuthViewModel", "Verification email request succeeded")

                    _homeState.update {
                        it.copy(
                            isLoading = false,
                            actionMessage = "Verification email sent! Check your inbox and spam folder."
                        )
                    }
                    startResendCooldown(60)
                },
                onFailure = { error ->
                    Log.e("AuthViewModel", "Verification email request failed", error)

                    val userMessage = if (error.message?.contains("blocked", ignoreCase = true) == true ||
                        error.message?.contains("too many requests", ignoreCase = true) == true
                    ) {
                        "Firebase temporarily blocked verification requests due to multiple taps. Please check your inbox/spam folder or try again in a few minutes."
                    } else {
                        error.message ?: "Failed to send verification email."
                    }

                    _homeState.update {
                        it.copy(
                            isLoading = false,
                            generalError = userMessage
                        )
                    }
                    startResendCooldown(60)
                }
            )
        }
    }

    private fun startResendCooldown(seconds: Int) {
        resendCooldownJob?.cancel()
        resendCooldownJob = viewModelScope.launch {
            for (i in seconds downTo 1) {
                _homeState.update { it.copy(resendCooldownSeconds = i) }
                delay(1000L)
            }
            _homeState.update { it.copy(resendCooldownSeconds = 0) }
        }
    }

    private fun createFallbackProfile(user: com.google.firebase.auth.FirebaseUser): UserProfile {
        val derivedUsername = user.displayName?.ifBlank { null }
            ?: user.email?.substringBefore("@")
            ?: "user"
        return UserProfile(
            uid = user.uid,
            email = user.email ?: "",
            username = derivedUsername,
            displayName = user.displayName?.ifBlank { null } ?: derivedUsername,
            photoUrl = user.photoUrl?.toString() ?: "",
            providerIds = user.providerData.map { it.providerId }.filter { it != "firebase" },
            emailVerified = user.isEmailVerified,
            accountStatus = "active"
        )
    }

    fun signOut(credentialManager: CredentialManager, onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.currentUser?.uid?.let { uid ->
                runCatching { notificationTokenRepository.removeCurrentDeviceToken(uid) }
                    .onFailure { error ->
                        Log.w("NotificationSetup", "Could not remove signed-out device token (${error.javaClass.simpleName}).")
                    }
            }
            authRepository.signOut(credentialManager)
            _homeState.update { HomeUiState() }
            val isRemember = authRepository.getRememberMePreference()
            _loginState.update {
                it.copy(
                    email = if (isRemember) authRepository.getSavedEmail() else "",
                    password = "",
                    rememberMe = isRemember,
                    generalError = null
                )
            }
            onSignedOut()
        }
    }

    fun clearActionMessage() {
        _homeState.update { it.copy(actionMessage = null, generalError = null) }
    }

    suspend fun updateNotificationPreference(field: String, enabled: Boolean) {
        val allowedFields = setOf(
            "notifyMarketplace", "notifyCommunityChat", "notifyConcernsRequests",
            "notifyAnnouncements", "notifyAmenityReservations", "notifyCommunityEvents",
            "notifyEmergencyContactUpdates"
        )
        require(field in allowedFields) { "Unknown notification preference." }
        notificationFirestoreRepository.updateCurrentUserProfile(mapOf(field to enabled))
        _homeState.update { state ->
            state.copy(userProfile = state.userProfile?.let { profile ->
                when (field) {
                    "notifyMarketplace" -> profile.copy(notifyMarketplace = enabled)
                    "notifyCommunityChat" -> profile.copy(notifyCommunityChat = enabled)
                    "notifyConcernsRequests" -> profile.copy(notifyConcernsRequests = enabled)
                    "notifyAnnouncements" -> profile.copy(notifyAnnouncements = enabled)
                    "notifyAmenityReservations" -> profile.copy(notifyAmenityReservations = enabled)
                    "notifyCommunityEvents" -> profile.copy(notifyCommunityEvents = enabled)
                    else -> profile.copy(notifyEmergencyContactUpdates = enabled)
                }
            })
        }
        runCatching { notificationTokenRepository.syncCurrentDeviceToken(_homeState.value.userProfile) }
            .onFailure { error ->
                Log.e("NotificationSetup", "Could not sync device token (${error.javaClass.simpleName}).")
            }
    }

    fun setNotificationPreference(field: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { updateNotificationPreference(field, enabled) }
                .onFailure { error ->
                    _homeState.update {
                        it.copy(generalError = error.message ?: "Could not save notification preference.")
                    }
                }
        }
    }

    fun saveResidentProfile(fields: Map<String, Any?>, photoUri: Uri?, proofUri: Uri?) {
        viewModelScope.launch {
            val values = fields.toMutableMap()
            if (photoUri != null) {
                userRepository.uploadProfilePhoto(photoUri).fold(
                    onSuccess = { values["photoStoragePath"] = it },
                    onFailure = { error ->
                        _homeState.update { it.copy(generalError = error.message ?: "Could not upload the profile photo.") }
                        return@launch
                    }
                )
            }
            if (proofUri != null) {
                userRepository.uploadVerificationDocument(proofUri).fold(
                    onSuccess = { values["proofDocumentPath"] = it },
                    onFailure = { error ->
                        _homeState.update { it.copy(generalError = error.message ?: "Could not upload the verification document.") }
                        return@launch
                    }
                )
            }
            userRepository.saveResidentProfile(values).fold(
                onSuccess = {
                    val uid = authRepository.currentUser?.uid.orEmpty()
                    userRepository.getUserProfile(uid).onSuccess { profile ->
                        _homeState.update { it.copy(userProfile = profile, actionMessage = "Profile changes saved.") }
                    }
                },
                onFailure = { error ->
                    _homeState.update { it.copy(generalError = error.message ?: "Could not save profile changes.") }
                }
            )
        }
    }
}
