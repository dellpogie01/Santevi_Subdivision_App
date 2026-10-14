package com.example.ui.viewmodel

import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseUser

data class LoginFormState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val rememberMe: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val generalError: String? = null
)

data class SignUpFormState(
    val email: String = "",
    val confirmEmail: String = "",
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val agreeTerms: Boolean = false,
    val agreeCommunity: Boolean = false,
    val emailError: String? = null,
    val confirmEmailError: String? = null,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val termsError: String? = null,
    val communityError: String? = null,
    val isLoading: Boolean = false,
    val generalError: String? = null,
    val verificationSentSuccess: Boolean = false
)

data class ForgotPasswordFormState(
    val email: String = "",
    val emailError: String? = null,
    val isLoading: Boolean = false,
    val generalError: String? = null,
    val resetEmailSent: Boolean = false
)

data class HomeUiState(
    val firebaseUser: FirebaseUser? = null,
    val userProfile: UserProfile? = null,
    val isTrustedAdmin: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val generalError: String? = null,
    val actionMessage: String? = null,
    val isDatabaseConfigured: Boolean = true,
    val resendCooldownSeconds: Int = 0
)
