# Santevi Android App — Phase 1 Foundation

A community-focused Android app for Santevi Subdivision, San Pablo City, Laguna, featuring announcements, marketplace, notifications, community concerns, and home location mapping. Built with Kotlin and Jetpack Compose.

Welcome to **Santevi**, a production-ready Android application foundation built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Firebase Authentication**, and **Cloud Firestore**.

This repository contains the complete Phase 1 implementation:
- **3-Second Splash Screen**: Replaceable background image, two replaceable icon placeholders, uppercase white tagline, and session-aware navigation.
- **Login Screen**: Replaceable app logo, white welcome text, email & password fields with visibility toggle, Remember Me switch, Forgot Password link, primary LOGIN button, "OR LOGIN WITH" divider, official Google Sign-In branding via Jetpack Credential Manager, and Sign-Up redirect.
- **Sign-Up Screen**: Replaceable logo, welcome message, heading, instructions, email, username (alphanumeric validation), password policy verification, matching password check, Terms of Service & Privacy Policy agreement modal, account creation with email verification dispatch, and Firestore profile initialization.
- **Forgot Password Screen**: Input validation, loading indicator, and real Firebase password-reset email delivery.
- **Cloud Firestore User Profiles**: Automatic user document creation under `users/{uid}`, concurrency-safe username reservations under `usernames/{username}`, and hardened zero-trust security rules in `firestore.rules`.
- **Authenticated Home Screen**: User welcome message, email display, Firestore profile summary card, email verification status badge with resend button, and functional SIGN OUT.

---

## Architecture Overview

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/
│   ├── MainActivity.kt
│   ├── data/
│   │   ├── model/
│   │   │   └── UserProfile.kt
│   │   ├── repository/
│   │   │   ├── AuthRepository.kt
│   │   │   └── UserRepository.kt
│   │   └── util/
│   │       └── FirestoreErrorHelper.kt
│   └── ui/
│       ├── navigation/
│       │   └── AppNavigation.kt
│       ├── screens/
│       │   ├── SplashScreen.kt
│       │   ├── LoginScreen.kt
│       │   ├── SignUpScreen.kt
│       │   ├── ForgotPasswordScreen.kt
│       │   ├── HomeScreen.kt
│       │   └── TermsAndPrivacyDialog.kt
│       ├── theme/
│       │   ├── Color.kt
│       │   ├── Theme.kt
│       │   └── Type.kt
│       └── viewmodel/
│           ├── AuthUiState.kt
│           └── AuthViewModel.kt
└── res/
    └── drawable/
        ├── splash_bg.xml            # Replaceable splash background
        ├── splash_icon_top.xml      # Replaceable splash top icon
        ├── splash_icon_bottom.xml   # Replaceable splash bottom icon
        ├── login_logo.xml           # Replaceable login logo
        ├── signup_logo.xml          # Replaceable sign-up logo
        └── ic_google_logo.xml       # Official Google 'G' icon
```

---

## Step-by-Step Firebase & Google Cloud Setup Guide

Follow these numbered steps to connect your project to Firebase and Google Cloud Console.

### 1. Opening the Project in Android Studio
1. Launch **Android Studio** (Ladybug or newer recommended).
2. Click **Open** and select this project's root folder.
3. Allow Gradle to perform its initial sync.

### 2. Creating or Selecting a Firebase Project
1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Click **Add project** (or select an existing Google Cloud project).
3. Name your project (e.g., `Nexus-Auth-App`).
4. (Optional) Choose whether to enable Google Analytics, then click **Create Project**.

### 3. Registering the Android App with its Package Name
1. On your Firebase project overview page, click the **Android** icon to add an Android app.
2. In the **Android package name** field, enter your application ID:
   ```
   com.juanmediahubph.santevi
   ```
   *(Note: This matches `applicationId` defined in `app/build.gradle.kts`)*
3. Enter an App nickname (e.g., `Nexus Android`).
4. Do not click Register yet—first obtain your signing certificate fingerprints (Step 7).

### 4. Configuring Google Cloud OAuth Clients and App Signing Fingerprints
To enable Google Sign-In and Firebase App verification, register your SHA-1 and SHA-256 fingerprints in Firebase:
1. In your local terminal, run `keytool` to view your debug keystore fingerprints:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey
   ```
   *(Or if using the project debug keystore:)*
   ```bash
   keytool -list -v -keystore debug.keystore -alias androiddebugkey
   ```
2. Copy the **SHA-1** and **SHA-256** fingerprints.
3. In the Firebase App Registration modal, paste the **Debug signing certificate SHA-1**.
4. Click **Register app**.

### 5. Downloading and Installing `google-services.json`
1. Download the generated `google-services.json` configuration file from Firebase Console.
2. Move the file into your project's `app/` folder:
   ```
   app/google-services.json
   ```
3. Re-sync Gradle in Android Studio (`File > Sync Project with Gradle Files`).

### 6. Configuring Firebase Authentication
1. In the Firebase Console left sidebar, click **Build > Authentication**.
2. Click **Get Started**.
3. Under the **Sign-in method** tab:
   - Click **Email/Password**:
     - Toggle **Enable** to ON.
     - (Optional) Toggle **Email link (passwordless sign-in)** if desired.
     - Click **Save**.
   - Click **Google**:
     - Toggle **Enable** to ON.
     - Select your project support email.
     - Click **Save**.

### 7. Google Cloud Web Client ID Verification
1. Once Google Sign-In is enabled in Firebase Authentication, Firebase automatically creates a Web Client ID in the Google Cloud Console.
2. When `google-services.json` is placed in `app/`, the Google Services Gradle plugin extracts `default_web_client_id` automatically at build time.
3. This is retrieved by Jetpack Credential Manager for Google Sign-In.

### 8. Creating a Cloud Firestore Database
1. In Firebase Console, navigate to **Build > Firestore Database**.
2. Click **Create database**.
3. Choose a database location close to your users (e.g., `nam5 (us-central)` or `europe-west1`).
4. Select **Start in production mode** (or test mode; we will deploy secure rules next).
5. Click **Create**.

### 9. Deploying Firestore Security Rules
1. The project includes hardened zero-trust rules in `firestore.rules`.
2. In Firebase Console, go to **Firestore Database > Rules** tab.
3. Paste the contents of `firestore.rules` from this repository:
   - Private read/write isolation on `/users/{userId}` restricted to `request.auth.uid == userId`.
   - Schema validation asserting required data types and permitted status values.
   - Concurrency-safe username reservations on `/usernames/{username}`.
4. Click **Publish**.

*(Alternatively, if using the Firebase CLI:)*
```bash
firebase deploy --only firestore:rules
```

### 10. Running the App on an Emulator or Device
1. In Android Studio, select a device or launch an Android Virtual Device (AVD with Google Play Services, API 24+).
2. Click **Run > Run 'app'** (`Shift + F10`).
3. The app will launch with the 3-second animated Splash Screen.

### 11. Testing Email/Password Registration and Login
1. On launch, the Splash Screen transitions to the **Login Screen**.
2. Tap **"Don't have an account? Sign up here"** to navigate to the **Sign-Up Screen**.
3. Enter:
   - Email: `yourname@example.com`
   - Username: `nexus_user1`
   - Password: `your-secure-password`
   - Confirm Password: `your-secure-password`
4. Check the **"I agree to the Terms of Service and Privacy Policy"** box (tap the links to preview the modal).
5. Tap **CREATE ACCOUNT**:
   - The user account is registered in Firebase Auth.
   - An email verification link is dispatched automatically.
   - A profile document is created in Firestore at `users/{uid}`.
   - The username is reserved at `usernames/nexus_user1`.
6. Tap **Continue to App** to navigate to the **Home Screen**.

### 12. Testing Google Sign-In with Real Credentials
1. Sign out from the Home Screen.
2. On the Login Screen, tap **Sign in with Google**.
3. Google Credential Manager opens a bottom sheet with your registered Google accounts.
4. Select an account:
   - The ID token is securely exchanged with Firebase Auth.
   - The Firestore profile is created or updated with `lastLoginAt` and verified status.
   - The app navigates to the Home Screen.

### 13. Testing Password-Reset Emails and Email Verification
1. On the Login Screen, tap **Forgot Password?**.
2. Enter your registered email address and tap **SEND RESET LINK**.
3. Check your email inbox for the Firebase password-reset email.
4. On the Home Screen:
   - Check the **Email Verification Pending** status banner.
   - Tap **Resend Verification Email** to trigger a new verification link.
   - Verify the link in your email, then tap **Refresh Status** to see the banner switch to **Email Verified**.

### 14. Replacing Placeholder Images
All images are decoupled in `app/src/main/res/drawable/` and can be customized without touching Kotlin code:
- **`splash_bg.xml`**: Replace with your custom vector or raster background (`splash_bg.png`).
- **`splash_icon_top.xml`**: Replace with your primary brand symbol or icon.
- **`splash_icon_bottom.xml`**: Replace with your secondary logo or tech badge.
- **`login_logo.xml`**: Replace with your official login brand mark.
- **`signup_logo.xml`**: Replace with your official sign-up badge.

### 15. Troubleshooting Common Issues
- **`default_web_client_id not found`**: Ensure `google-services.json` has been downloaded and placed in `app/` and that Google provider is enabled in Firebase Auth.
- **`ApiException: 10` or Credential Cancellation**: This indicates a mismatch between your app's SHA-1 fingerprint and the SHA-1 registered in Firebase Console. Re-check Step 4.
- **`Default FirebaseApp is not initialized`**: Verify `google-services.json` is located directly inside the `app/` directory and re-build the app.

---

## Phase 1 Test Checklist

| Component / Requirement | Status | Verification Note |
|---|---|---|
| **Splash Screen (3s timer)** | Implemented | Animated fade/scale, replaceable background & 2 icons, uppercase tagline, single-run guard |
| **Session Restoration** | Implemented | Checks `hasValidSession()`, auto-routes to Home if signed in |
| **Login Screen Form** | Implemented | Email, password with toggle, Remember Me switch, Forgot Password link |
| **Material 3 UI & Theme** | Implemented | Modern dark/light M3 palette with accessible touch targets |
| **Real Email/Password Auth** | Implemented | Firebase `signInWithEmailAndPassword`, friendly error messages |
| **Real Google Sign-In** | Implemented | Jetpack Credential Manager + `GoogleAuthProvider` credential exchange |
| **Sign-Up Screen** | Implemented | Email, username, password policy, confirm password, terms checkbox & modal |
| **Email Verification** | Implemented | Dispatched automatically upon sign-up, resend button on Home |
| **Password Reset** | Implemented | Real `sendPasswordResetEmail` in ForgotPasswordScreen |
| **Cloud Firestore Storage** | Implemented | `users/{uid}` profile documents with server timestamps |
| **Username Uniqueness** | Implemented | Concurrency-safe transactions on `usernames/{username}` |
| **Firestore Security Rules** | Implemented | Hardened zero-trust owner isolation in `firestore.rules` |
| **Authenticated Home** | Implemented | Profile summary card, email verification status, sign-out |
| **Replaceable Drawables** | Implemented | `splash_bg`, `splash_icon_top`, `splash_icon_bottom`, `login_logo`, `signup_logo` |
| **External Configuration** | User Action | Requires adding `google-services.json` from Firebase Console |

## Secure community roles and administration

New Firebase Auth profiles are initialized as `Visitor` with `isAdmin: false` and `roleRequestStatus: none`. The nine selectable roles are Visitor, Homeowner, Security, HOA Officer, Property Manager, Maintenance, Community Staff, Service Provider, and Emergency Response. The seven elevated roles are assigned only by the trusted callable Functions after an administrator review. Firestore documents do not grant permission; rules use the verified Auth `admin` custom claim.

### Deploy trusted backend and rules

From the repository root, install Functions dependencies in `functions/`, then run `firebase deploy --only functions,firestore:rules,firestore:indexes --project santevi` with an authorized Firebase CLI account. Review and deploy separately in a controlled release. Functions require a Firebase plan that supports Cloud Functions, and callable functions should be protected with Firebase App Check before broad public release.

### Provision the first administrator

1. Create the Firebase Authentication account for `juanmediahubph@gmail.com` in Firebase Console if it does not exist, and complete email verification.
2. From a trusted operator machine with Firebase Admin IAM permissions and Application Default Credentials for the `santevi` project, run `node functions/provisionFirstAdmin.js` with `GOOGLE_CLOUD_PROJECT=santevi` set in that shell.
3. Verify that the script reports the actual UID, then sign out/in on the target account (or refresh its ID token). The script is guarded against another existing admin and repeat execution, preserves unrelated profile fields, and writes the separate one-time marker.

The provisioning script has not been run in this workspace; no Authentication account or live database was modified here. If the target account is missing or its email is unverified, fix that in Firebase Console before running the script. A failed run after the Auth claim update may require operator inspection of `systemMetadata/initialAdminProvisioning` before retrying.

### Existing data and rules migration

Legacy community posts without `publicationStatus: "published"` are intentionally hidden from ordinary users under these rules. Review legacy documents and backfill a published status only for content approved for community visibility. Existing user role fields are not migrated automatically; review them and provision eligible administrators through a trusted process. The app currently uses `amenity_reservations` for published amenity information; confirm this does not overlap a separate booking ledger before deploying the stricter admin-only write policy.

Run `npm.cmd run test:rules` from the root for Firestore and Storage emulator rules, and `node test/notificationPolicy.test.js` plus `node test/rolePolicy.test.js` from `functions/` for local policy tests. Do not infer that Rules Emulator tests execute or authenticate Cloud Functions; callable integration tests and real-device Firebase verification are still required.
