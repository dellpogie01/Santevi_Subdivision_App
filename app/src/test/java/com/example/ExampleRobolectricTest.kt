package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Santevi", appName)
  }

  @Test
  fun `username placeholder string is juandelacruz_26`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val placeholder = context.getString(R.string.username_placeholder)
    assertEquals("juandelacruz_26", placeholder)
  }

  @Test
  fun `signUp fails when passwords do not match`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val viewModel = com.example.ui.viewmodel.AuthViewModel(context as android.app.Application)

    viewModel.onSignUpEmailChanged("user@example.com")
    viewModel.onSignUpUsernameChanged("valid_user")
    viewModel.onSignUpPasswordChanged("Password123")
    viewModel.onSignUpConfirmPasswordChanged("MismatchPassword123")
    viewModel.onAgreeTermsChanged(true)
    viewModel.onAgreeCommunityChanged(true)

    viewModel.signUp(onSuccess = {})

    val state = viewModel.signUpState.value
    assertEquals("Passwords do not match.", state.confirmPasswordError)
  }

  @Test
  fun `empty signup requires both agreements and all fields`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val viewModel = com.example.ui.viewmodel.AuthViewModel(context as android.app.Application)

    viewModel.signUp(onSuccess = {})

    assertEquals("Complete all fields and accept both agreements.", viewModel.signUpState.value.generalError)
    assertEquals(false, viewModel.signUpState.value.isLoading)
  }

  @Test
  fun `remember me toggle off clears saved email and password field`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("nexus_auth_prefs", Context.MODE_PRIVATE)
      .edit().putString("pref_saved_password", "legacy-password-value").commit()
    val repo = com.example.data.repository.AuthRepository(context)
    assertEquals(null, context.getSharedPreferences("nexus_auth_prefs", Context.MODE_PRIVATE).getString("pref_saved_password", null))
    repo.setSavedEmail("test@example.com")
    repo.setRememberMePreference(true)

    val viewModel = com.example.ui.viewmodel.AuthViewModel(context as android.app.Application)
    assertEquals("test@example.com", viewModel.loginState.value.email)
    assertEquals("", viewModel.loginState.value.password)

    viewModel.onRememberMeChanged(false)

    val updatedState = viewModel.loginState.value
    assertEquals(false, updatedState.rememberMe)
    assertEquals("", updatedState.password)
    assertEquals("", repo.getSavedEmail())
  }

  @Test
  fun `remember me toggle on auto fills saved email without restoring a password`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.repository.AuthRepository(context)
    repo.setSavedEmail("saved@example.com")
    repo.setRememberMePreference(false)

    val viewModel = com.example.ui.viewmodel.AuthViewModel(context as android.app.Application)
    assertEquals("", viewModel.loginState.value.email)
    assertEquals("", viewModel.loginState.value.password)

    viewModel.onRememberMeChanged(true)

    val state = viewModel.loginState.value
    assertEquals(true, state.rememberMe)
    assertEquals("saved@example.com", state.email)
    assertEquals("", state.password)
  }
}
