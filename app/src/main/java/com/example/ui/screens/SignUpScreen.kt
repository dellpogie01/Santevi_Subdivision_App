package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.util.PasswordPolicy
import com.example.ui.components.BrandHeaderWithFlankingLogos
import com.example.ui.components.OrnamentalFlourishDivider
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.SignUpFormState
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val state by viewModel.signUpState.collectAsState()

    SignUpScreenContent(
        state = state,
        onUsernameChanged = { viewModel.onSignUpUsernameChanged(it) },
        onEmailChanged = { viewModel.onSignUpEmailChanged(it) },
        onPasswordChanged = { viewModel.onSignUpPasswordChanged(it) },
        onConfirmPasswordChanged = { viewModel.onSignUpConfirmPasswordChanged(it) },
        onTogglePasswordVisibility = { viewModel.toggleSignUpPasswordVisibility() },
        onToggleConfirmPasswordVisibility = { viewModel.toggleSignUpConfirmPasswordVisibility() },
        onAgreeTermsChanged = { viewModel.onAgreeTermsChanged(it) },
        onAgreeCommunityChanged = { viewModel.onAgreeCommunityChanged(it) },
        onSignUpClick = { viewModel.signUp(onSuccess = onNavigateToHome) },
        onNavigateToLogin = onNavigateToLogin
    )
}

@Composable
fun SignUpScreenContent(
    state: SignUpFormState,
    onUsernameChanged: (String) -> Unit = {},
    onEmailChanged: (String) -> Unit = {},
    onPasswordChanged: (String) -> Unit = {},
    onConfirmPasswordChanged: (String) -> Unit = {},
    onTogglePasswordVisibility: () -> Unit = {},
    onToggleConfirmPasswordVisibility: () -> Unit = {},
    onAgreeTermsChanged: (Boolean) -> Unit = {},
    onAgreeCommunityChanged: (Boolean) -> Unit = {},
    onSignUpClick: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showTermsDialog by remember { mutableStateOf(false) }
    var showCodeOfConductDialog by remember { mutableStateOf(false) }

    // Focus lost validation error prompts
    var emailFocusError by remember { mutableStateOf<String?>(null) }
    var usernameFocusError by remember { mutableStateOf<String?>(null) }
    var passwordFocusError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordFocusError by remember { mutableStateOf<String?>(null) }

    if (showTermsDialog) {
        TermsAndPrivacyDialog(onDismiss = { showTermsDialog = false })
    }

    if (showCodeOfConductDialog) {
        CommunityCodeOfConductDialog(onDismiss = { showCodeOfConductDialog = false })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("signup_screen_root")
    ) {
        // Background image
        Image(
            painter = painterResource(id = R.drawable.santevi_bg),
            contentDescription = "Santeví Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Dark scrim overlay to ensure all texts & form elements are crystal clear
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Scrollable column layout to ensure soft keyboard / IME never compresses form elements
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Application brand header
            BrandHeaderWithFlankingLogos(testTag = "signup_app_logo")

            Spacer(modifier = Modifier.height(6.dp))

            // Location Bar below header images
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location Pin",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Brgy. San Vicente, San Pablo City, Laguna 4000",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Ornamental flourish decorative curves
            OrnamentalFlourishDivider()

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Main Heading: CREATE AN ACCOUNT
            Text(
                text = stringResource(id = R.string.create_account_heading),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("signup_heading")
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = stringResource(id = R.string.create_account_instruction),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("signup_instruction")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // General Error Card if present
            if (state.generalError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("signup_general_error_card")
                ) {
                    Text(
                        text = state.generalError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Email Address field
            val effectiveEmailError = state.emailError ?: emailFocusError
            OutlinedTextField(
                value = state.email,
                onValueChange = {
                    onEmailChanged(it)
                    emailFocusError = null
                },
                textStyle = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Color.White),
                label = { Text(stringResource(id = R.string.email_label), fontSize = 11.5.sp) },
                placeholder = { Text(stringResource(id = R.string.email_placeholder), fontSize = 11.5.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Email, contentDescription = "Email Icon", modifier = Modifier.size(18.dp))
                },
                isError = effectiveEmailError != null,
                supportingText = {
                    if (effectiveEmailError != null) {
                        Text(text = effectiveEmailError, color = MaterialTheme.colorScheme.error, fontSize = 10.5.sp)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && state.email.isNotBlank()) {
                            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email.trim()).matches()) {
                                emailFocusError = "Please enter a valid email address."
                            } else if (FirebaseApp.getApps(context).isNotEmpty()) {
                                try {
                                    val db = FirebaseFirestore.getInstance()
                                    db.collection("users")
                                        .whereEqualTo("email", state.email.trim())
                                        .get()
                                        .addOnSuccessListener { query ->
                                            if (query != null && !query.isEmpty) {
                                                emailFocusError = "Email address already registered. Please log in."
                                            }
                                        }
                                } catch (e: Exception) {
                                    // Safe catch
                                }
                            }
                        }
                    }
                    .testTag("signup_email_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Black.copy(alpha = 0.50f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.50f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.85f),
                    focusedLeadingIconColor = Color.White,
                    unfocusedLeadingIconColor = Color.White.copy(alpha = 0.85f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.50f)
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Username field
            val effectiveUsernameError = state.usernameError ?: usernameFocusError
            OutlinedTextField(
                value = state.username,
                onValueChange = {
                    onUsernameChanged(it)
                    usernameFocusError = null
                },
                textStyle = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Color.White),
                label = { Text(stringResource(id = R.string.username_label), fontSize = 11.5.sp) },
                placeholder = { Text(stringResource(id = R.string.username_placeholder), fontSize = 11.5.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Username Icon", modifier = Modifier.size(18.dp))
                },
                isError = effectiveUsernameError != null,
                supportingText = {
                    if (effectiveUsernameError != null) {
                        Text(text = effectiveUsernameError, color = MaterialTheme.colorScheme.error, fontSize = 10.5.sp)
                    } else {
                        Text(text = "3-20 characters: letters, numbers, underscores", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && state.username.isNotBlank()) {
                            if (state.username.trim().length < 3) {
                                usernameFocusError = "Username must be at least 3 characters."
                            } else if (FirebaseApp.getApps(context).isNotEmpty()) {
                                try {
                                    val db = FirebaseFirestore.getInstance()
                                    db.collection("users")
                                        .document(state.username.trim())
                                        .get()
                                        .addOnSuccessListener { doc ->
                                            if (doc != null && doc.exists()) {
                                                usernameFocusError = "Username is already taken."
                                            }
                                        }
                                } catch (e: Exception) {
                                    // Safe catch
                                }
                            }
                        }
                    }
                    .testTag("signup_username_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Black.copy(alpha = 0.50f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.50f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.85f),
                    focusedLeadingIconColor = Color.White,
                    unfocusedLeadingIconColor = Color.White.copy(alpha = 0.85f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.50f)
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Password field
            val effectivePasswordError = state.passwordError ?: passwordFocusError
            OutlinedTextField(
                value = state.password,
                onValueChange = {
                    onPasswordChanged(it)
                    passwordFocusError = null
                },
                textStyle = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Color.White),
                label = { Text(stringResource(id = R.string.password_label), fontSize = 11.5.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = "Password Icon", modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    IconButton(
                        onClick = onTogglePasswordVisibility,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("signup_password_toggle")
                    ) {
                        Icon(
                            imageVector = if (state.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (state.isPasswordVisible) "Hide Password" else "Show Password",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = effectivePasswordError != null,
                supportingText = {
                    if (effectivePasswordError != null) {
                        Text(text = effectivePasswordError, color = MaterialTheme.colorScheme.error, fontSize = 10.5.sp)
                    } else {
                        Text(text = "At least 8 characters with letters & numbers", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && state.password.isNotBlank()) {
                            if (!PasswordPolicy.meetsMinimumLength(state.password)) {
                                passwordFocusError = PasswordPolicy.MIN_LENGTH_MESSAGE
                            }
                        }
                    }
                    .testTag("signup_password_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Black.copy(alpha = 0.50f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.50f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.85f),
                    focusedLeadingIconColor = Color.White,
                    unfocusedLeadingIconColor = Color.White.copy(alpha = 0.85f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.50f)
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Confirm Password field
            val effectiveConfirmPasswordError = state.confirmPasswordError ?: confirmPasswordFocusError
            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = {
                    onConfirmPasswordChanged(it)
                    confirmPasswordFocusError = null
                },
                textStyle = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Color.White),
                label = { Text(stringResource(id = R.string.confirm_password_label), fontSize = 11.5.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = "Confirm Password Icon", modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    IconButton(
                        onClick = onToggleConfirmPasswordVisibility,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("signup_confirm_password_toggle")
                    ) {
                        Icon(
                            imageVector = if (state.isConfirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (state.isConfirmPasswordVisible) "Hide Confirm Password" else "Show Confirm Password",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                visualTransformation = if (state.isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = effectiveConfirmPasswordError != null,
                supportingText = {
                    if (effectiveConfirmPasswordError != null) {
                        Text(text = effectiveConfirmPasswordError, color = MaterialTheme.colorScheme.error, fontSize = 10.5.sp)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (!state.isLoading) {
                            onSignUpClick()
                        }
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && state.confirmPassword.isNotBlank()) {
                            if (state.confirmPassword != state.password) {
                                confirmPasswordFocusError = "Passwords do not match."
                            }
                        }
                    }
                    .testTag("signup_confirm_password_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Black.copy(alpha = 0.50f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.50f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.85f),
                    focusedLeadingIconColor = Color.White,
                    unfocusedLeadingIconColor = Color.White.copy(alpha = 0.85f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.50f)
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Checkbox 1: Terms of Service and Privacy Policy
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.agreeTerms,
                    onCheckedChange = onAgreeTermsChanged,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("signup_terms_checkbox"),
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("I agree to the ", fontSize = 11.5.sp, color = Color.White)
                    Text(
                        text = "Terms of Service and Privacy Policy",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clickable { showTermsDialog = true }
                            .testTag("signup_terms_link")
                    )
                }
            }
            if (state.termsError != null) {
                Text(state.termsError, color = MaterialTheme.colorScheme.error, fontSize = 10.5.sp)
            }

            // Checkbox 2: Community Code of Conduct
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.agreeCommunity,
                    onCheckedChange = onAgreeCommunityChanged,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("signup_code_of_conduct_checkbox"),
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("I agree to the ", fontSize = 11.5.sp, color = Color.White)
                    Text(
                        text = "Community Code of Conduct",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clickable { showCodeOfConductDialog = true }
                            .testTag("signup_code_of_conduct_link")
                    )
                }
            }
            if (state.communityError != null) {
                Text(state.communityError, color = MaterialTheme.colorScheme.error, fontSize = 10.5.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary CREATE ACCOUNT button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSignUpClick()
                },
                enabled = !state.isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("signup_primary_button")
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Creating Account...", fontSize = 13.sp)
                } else {
                    Text(
                        text = stringResource(id = R.string.create_account_button),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Text: "Already have an account? Log in"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = Color.White
                )
                Text(
                    text = "Log in",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onNavigateToLogin() }
                        .testTag("signup_navigate_login_text")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun CommunityCodeOfConductDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Community Code of Conduct",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Text(
                    text = "Our Pledge",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "In the interest of fostering an open and welcoming community for all residents and neighbors in Santevi Subdivision, San Pablo City, Laguna, we pledge to make participation in our community hub a harassment-free experience for everyone.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Text(
                    text = "Community Standards",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• Respectful and courteous communication between neighbors, staff, and HOA officers.\n" +
                            "• Zero tolerance for harassment, threats, discriminatory language, or abusive behavior.\n" +
                            "• Accurate and truthful marketplace listings with fair representation in Philippine Peso (₱).\n" +
                            "• Respect for resident privacy: never publish or distribute neighbor contact details without consent.\n" +
                            "• Honest reporting of community concerns and proper use of the anonymous reporting channel.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = "I Understand",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SignUpScreenPreview() {
    MyApplicationTheme {
        SignUpScreenContent(
            state = SignUpFormState(email = "newresident@santevi.ph", username = "newresident"),
            onUsernameChanged = {},
            onEmailChanged = {},
            onPasswordChanged = {},
            onConfirmPasswordChanged = {},
            onTogglePasswordVisibility = {},
            onToggleConfirmPasswordVisibility = {},
            onAgreeTermsChanged = {},
            onAgreeCommunityChanged = {},
            onSignUpClick = {},
            onNavigateToLogin = {}
        )
    }
}
