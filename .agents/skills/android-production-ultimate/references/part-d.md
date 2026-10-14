## DOMAIN: Legal, Play Policy, Privacy & Billing

> Scope: Google Play Developer Program Policies (snapshot Jul 2026), Data Safety, privacy/disclosure/consent, account deletion, restricted permissions, target-API hygiene, ads & Families/AAID, minimum functionality, Play Billing & subscriptions, GDPR/CCPA, privacy-by-design data handling, crash/analytics PII scrubbing, pre-submission checklist, rejection causes.
> Play enforcement covers the app, its **metadata**, its **ads**, every **third-party SDK** inside it, AND the **developer account + whole catalog**. Repeated/egregious violations escalate: Rejection → Removal → **Suspension (strike)** → **Account termination** (related accounts permanently suspended; new accounts to evade = violation). One appeal per enforcement action.

### Play Data Safety

- **Every app must complete the Data safety form** (Play Console → App content) covering data handled by the app **and every SDK in it**. Divergence between app behavior and the form → corrections, update blocks, or **removal**. (Top removal cause.)
- "Collected" = transmitted **off-device** (incl. to your own servers). Carve-outs (exempt → severity downgrade): fully **anonymized** data not linked to a user; **E2EE** data the developer cannot read; data typed into a WebView navigating the **open web**; **local-only** processing (caching theme/UI prefs, on-device DB).
- Per data type declare: **Collected?** · **Shared?** (exceptions: service providers, legal, user-initiated transfers, anonymized) · **Purposes** (app functionality, analytics, developer communications, advertising/marketing, fraud prevention/security, personalization, account management) · Optional vs Required · Encrypted in transit · Deletion available.
- Data type buckets: Location, Personal info, Financial info, Health & fitness, Messages, Photos & videos, Audio, Files, Calendar, Contacts, App activity, Web browsing, App info & performance, Device or other IDs.
- **You are responsible for SDK data handling.** Inventory every data-collecting SDK and declare each.

| SDK | Data collected | Declare (category) |
|-----|----------------|--------------------|
| Firebase Analytics | app interactions, device info | Analytics |
| Firebase Crashlytics / Sentry | crash logs, device state, **device IDs** | App diagnostics |
| Google Ads / GMA SDK | Advertising ID (`AD_ID`) | Advertising |
| Facebook SDK / AppsFlyer / Adjust / Amplitude / Mixpanel | events, device IDs | Analytics/Advertising |
| Firebase Auth | email, phone | Personal info |
| Firebase Cloud Messaging | FCM token | App functionality |

Data-safety audit decision matrix (behavioral classification):

| Case | Observation | `user_initiated` | `is_third_party` | Disclosure | Severity |
|------|-------------|------------------|------------------|------------|----------|
| 1 | Not transferred (local only) | N/A | N/A | EXEMPT | Suggestion (compliant) |
| 2A | Transferred + Background + No disclosure | false | any | MISSING | **CRITICAL** (silent background transfer) |
| 2B | Transferred + Background + Disclosure found | false | any | DISCLOSED | Important (verify disclosure) |
| 3 | Transferred + User-initiated + First party | true | false | EXEMPT | Important (no disclosure needed, **must declare in form**) |
| 4 | Transferred + User-initiated + Third party (share sheet) | true | true | EXEMPT | Suggestion (policy exempt) |

- Implicit leaks count as **transferred**: emails/account IDs/precise location passed to a crash/telemetry SDK that uploads off-device.
- Ambiguous destination → default to first-party but **treat as collected** (err toward declaring).
- Silent on-device sharing to another app via Intents/ContentProviders counts as a transfer.

### Privacy Policy & Disclosure/Consent

- **Privacy policy required for ALL apps** (even ones collecting nothing). Linked in **both** the Play Console store-listing field **AND inside the app** (Settings, registration/consent screen, first-launch onboarding).
- URL must be **HTTPS, active, publicly accessible, non-geofenced**, not a PDF, not editable-only. Must name the entity, be labeled "Privacy Policy", describe collection/use/sharing, cover retention & deletion.

**Prominent disclosure + affirmative consent** — required when collecting/transmitting personal/sensitive data for a purpose NOT expected from prominently described functionality (classic trigger: **background collection**):
- Must be **in-app**, shown during normal usage without menu navigation.
- Must describe the data collected and how it is used/shared; not bundled with unrelated disclosures.
- Format: `"[This app] collects/transmits/syncs/stores [data type] to enable [feature], [scenario]."`
- Consent dialog must require **affirmative action** (tap/checkbox). Navigating away, auto-dismiss, or expiring messages do **NOT** count.
- Consent must **precede collection AND the runtime permission prompt**; the disclosure must **gate** the data-collection logic (tracking that starts before "Accept", or dismissible dialog while tracking continues = **CRITICAL** violation).
- **Never pre-check consent boxes.** Show consent before any non-essential collection.

```kotlin
// ✅ gate collection behind consent; drop event if no consent
privacyGuard.withConsent(ConsentPurpose.ANALYTICS, Unit) { analyticsProvider.logEvent(name, safeParams) }
// ❌ collecting before consent / pre-checked boxes
```

### Account Deletion

- **If the app enables account creation in-app, it MUST offer account deletion** (mirrors Apple 5.1.1(v)). Same applies to Firebase Auth / Supabase / Auth0 / Google Sign-In / custom signup.
- Two required paths:
  1. **In-app** — readily discoverable path to initiate deletion (Settings → Account).
  2. **Web resource** — a public URL where users (incl. those who uninstalled) can request deletion; **declared in the Data safety form** and shown on the store listing. Link must load without error, prominently feature the deletion path, and reference the app/developer name as listed.
- Deleting the account must **delete associated remote user data** — a backend purge, not `clearPreferences()` / cookie clear / `logout()`. **Deactivation/freezing does NOT qualify.** (The "Partial Deletion Trap".)
- Legitimate retention (security/fraud/compliance) is allowed but must be disclosed.
- Indirect accounts: third-party sign-in with remotely cached preferences/device IDs still requires a delete link/button.
- **Reviewer demo credentials**: if features are gated behind login, submit active, non-expiring test credentials in Play Console so reviewers can reach gated features (also for hidden gatekeepers like sync/checkout/member dashboards requiring auth).

### Restricted Permissions

General rules: request only permissions needed for **current, implemented, promoted** features; request **in context at runtime**; never sell data from sensitive permissions; if a **narrower alternative** (picker, intent, `<queries>`) exists, it **must** be used. Restricted permissions require the **Permissions Declaration Form**. List any permission with no corresponding feature = **unused permission violation**. Beware **merged manifest**: SDKs inject `AD_ID`, `QUERY_ALL_PACKAGES`, etc. — verify merged manifest.

| Permission | Allowed for | Banned / prefer | Declaration |
|-----------|-------------|-----------------|-------------|
| `READ_SMS`,`SEND_SMS`,`RECEIVE_SMS`,`RECEIVE_WAP_PUSH`,`RECEIVE_MMS`,`READ_CALL_LOG`,`WRITE_CALL_LOG`,`PROCESS_OUTGOING_CALLS` | default SMS/Phone/Assistant handler, narrow exceptions (device automation, companion, cross-device sync, SMS financial txns, backup/restore, enterprise, carrier, anti-fraud) | **OTP/verification → use SMS Retriever API / SMS User Consent API (0 perms)**; content sharing; contact prioritization; deriving equivalent data by other means | Yes (+ possible demo video) |
| `QUERY_ALL_PACKAGES` (package visibility) | antivirus, file managers, device search, banking security, browsers, digital wallets, device mgmt, accessibility | ad/analytics/marketing use; app inventory may **never** be sold or shared for ads/analytics. Prefer `<queries>`; flag `getInstalledPackages()`/`getInstalledApplications()` loops | Yes |
| `MANAGE_EXTERNAL_STORAGE` (All files access) | file managers, antivirus, backup/restore, device migration (core need, prominent in listing) | anything else → **Scoped Storage / SAF / MediaStore / app-specific dir** (`getExternalFilesDir()`, 0 perms); legacy broad `READ/WRITE_EXTERNAL_STORAGE` at targetSdk ≥ 30 | Yes |
| `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO` | apps with broad/persistent media access as **core** (gallery, editor) | one-time/infrequent (avatar upload, attachments, check deposit) → **Android Photo Picker** `PickVisualMedia` / `ACTION_GET_CONTENT`/SAF | Yes (enforced since Jan 22 2025) |
| `READ_CONTACTS` | broad continuous sync (friend-matching, full contact manager) | one-time pick, referrals, forms → **Contact Picker** (`ACTION_PICK`/`ACTION_PICK_CONTACTS`); at targetSdk 37+ broad access restricted | — |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | promoted features only | fine when city-level suffices → downgrade to coarse; at targetSdk 37+ use **Location Button**; location shared with ad SDKs without consent | — |
| `ACCESS_BACKGROUND_LOCATION` | core feature, clear benefit, foreground/FGS insufficient | ads/marketing/general analytics only = **CRITICAL**; ad/analytics/social/sharing NEVER justify it. Must be in disclosure text + say "when app is closed or not in use" | **Location declaration + prominent disclosure + case-by-case approval (+ video)** |
| `BIND_ACCESSIBILITY_SERVICE` / `AccessibilityService` | helping users with disabilities → set `isAccessibilityTool="true"` | automation, screen scraping, ad blocking, keystroke/notification capture → telemetry siphoning = **CRITICAL**; non-a11y use needs declaration + disclosure + affirmative consent; banned: change settings w/o permission, prevent disable/uninstall, work around privacy controls, **remote call audio recording** | Yes (stricter since Jan 28 2026) |
| `USE_EXACT_ALARM` (install-time, non-revocable) | **alarm clock / timer / calendar** only | else use `SCHEDULE_EXACT_ALARM` (user-revocable; denied by default on 14+ new installs, graceful degradation) or `WorkManager`/inexact | — |
| `FOREGROUND_SERVICE_*` + `android:foregroundServiceType` (targetSdk 34+) | every `<service>` needs a type + matching permission; must be user-initiated, perceptible; valid notification/`startForeground()` | missing `foregroundServiceType` = **CRITICAL**; `specialUse` needs `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" ...>` (weak/placeholder justification rejected) | **Per-FGS-type declaration: functionality desc, user impact, demo video link** |
| `USE_FULL_SCREEN_INTENT` (targetSdk 34+) | calling & alarm apps (auto-granted) | ads/engagement = violation; others must request special access at runtime | Yes (revocation enforced since Jan 22 2025) |
| `RECORD_AUDIO` (targetSdk 34+) | user-visible, time-bounded actions; continuous capture w/o indicator = **CRITICAL** | occasional vocal input → **Microphone Button** API; analytics/user-agent capture | — |
| `REQUEST_INSTALL_PACKAGES` | user-initiated install/send/receive (browsers, messaging w/ attachments, file managers, enterprise, backup) | **self-updates**, modifying other apps, bundled APKs (except device mgmt); Play apps update only via Play | Yes |
| `BIND_VPN_SERVICE`/`VpnService` | VPN as core (parental control, usage tracking, device security, network tools, browsers, carrier) | undisclosed proxying = abuse/MUwS; redirecting/manipulating other apps' traffic for monetization; ads manipulation. Must document in listing + **encrypt device-to-endpoint** | Yes |
| `SCHEDULE_EXACT_ALARM`/`SYSTEM_ALERT_WINDOW`(overlays)/`RECORD_AUDIO`+`CAMERA` in background | — | flag overlays combined w/ accessibility or ads; background mic/camera; `TYPE_APPLICATION_OVERLAY` covering other apps | — |
| `com.google.android.gms.permission.AD_ID` | Android 13+ if app/any SDK uses advertising ID | children target → must be absent (see Ads & Families) | — |

```xml
<!-- ✅ drop legacy perms on newer APIs -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>
<!-- ❌ MANAGE_EXTERNAL_STORAGE in a non-file-manager app = CRITICAL -->
```

### Target API & Hygiene

- **Target API is a hard technical gate.** New apps & updates must target within **one year** of the latest Android release.
  - Current (mid-2026): new apps/updates target **API 35 (Android 15)**+; Wear OS / Android TV / Automotive **API 34+**.
  - **From Aug 31, 2026:** new apps/updates target **API 36 (Android 16)**; existing apps must target ≥ API 35 to stay available to new users on Android 16+ devices.
  - Existing non-updated apps must stay within **two years** to remain discoverable to new users. Extensions requestable in Play Console.
- `minSdk` very low (< 21) → compat/maintenance warning only.
- `android:usesCleartextTraffic="true"` or missing network security config w/ HTTP URLs → flag. Ship `network_security_config.xml` blocking cleartext (see Privacy-by-Design).
- `android:debuggable="true"` in release = **CRITICAL**.
- Secrets: no hardcoded `apiKey`/`secret`/`password`/`token`/`Bearer` in source, `gradle.properties`, `local.properties`, `.env`; no committed keystores (`.jks`,`.keystore`) = **CRITICAL**; service-account JSON must NEVER ship in the app.
- Strip logs via R8 `-assumenosideeffects`.
- Metadata policy: Title **≤ 30 chars**; no emoji/emoticons/repeated special chars/ALL CAPS (unless brand) in title/icon/developer name; no promo words ("top","#1","best","free","no ads","sale"); no keyword stuffing; no competitor names; screenshots must show real functionality (no "Download now" CTAs). Short desc ≤ 80 chars; full desc ≤ 4000 chars. App icon 512×512 PNG ≤1MB no alpha; feature graphic 1024×500; screenshots 2–8/form factor.
- Content rating (**IARC**) questionnaire required for all apps; misrepresentation → rejection/removal/termination. Ads must fit the rating.
- Review-manipulation: only the **In-App Review API** (`com.google.android.play:review`); no custom dialogs filtering happy users, no incentivized ratings/reviews/installs.
- Required Play Console declarations (see Pre-Submission Checklist).
- Dynamic code loading (stricter than Apple): app **may not update itself except via Play**, and **may not download executable code (dex/JAR/.so) from outside Play**. Exception: code in a VM/interpreter with only indirect Android API access (JS in WebView). Runtime-loaded interpreted code (JS/Python/Lua) must not get direct Android API access or evade review. RN OTA (CodePush/expo-updates) tolerated only for JS bundles consistent with policy; **flag native-code hot patching**. Scan for `DexClassLoader`, `PathClassLoader` on downloaded files, `System.load()` on downloaded paths, WebView `JavascriptInterface` loading `http://`/unverified URLs.

### Ads & Families/AAID

Ads:
- **Ads are app content**: ads + landing offers must comply with all policies and fit the content rating.
- **Disruptive ads banned**: unexpected full-screen ads (during a call, on unlock, during GPS nav), ads without clear dismissal, forced-click deception, ads obscuring controls.
- **Lockscreen:** no ads/monetization on the locked screen unless the app's exclusive purpose is a lockscreen.
- **In-context only:** ads run only within the app serving them — no home-screen/out-of-app ads, no notification ads.
- **Interstitials:** no unexpected ones (during gameplay, at level start, before splash/loading screen); expected pre-reward/pre-score and opt-in interstitials OK; **all full-screen ads must be closable within 15 seconds** (except opted-in rewarded ads).
- **Rewarded ads:** require explicit opt-in via a clear prompt describing the reward.
- **Consent/UMP before personalized ads** in GDPR regions: check `UserMessagingPlatform` / CMP usage; **flag ad SDK initialization before consent**.
- **Ad fraud (severe enforcement / instant termination risk):** hidden/stacked/pixel ads, auto-clicks, `MotionEvent` synthesis/click injection, fake attribution, ads while app not in use, background ad loading, off-screen/0-size ad views.

Families / children (if app targets children or mixed audience):
- **NO advertising ID for child-only apps** — `AD_ID` must be absent/removed; mixed apps must not transmit AAID from children/unknown-age users; child-only apps targeting API 33+ must use the Play services mechanism that **zeroes the AAID**.
- Only **Families Self-Certified Ads SDKs** (child-only: exclusively; mixed: for children/unknown-age). AdMob with `setTagForChildDirectedTreatment(true)`/`tagForChildDirectedTreatment`, configured mediation passing child-directed signals only to other certified SDKs.
- **No interest-based / personalized ads or remarketing to children.** No location permissions for child-directed apps. No social features requiring personal info without **verifiable parental consent**. App + all APIs/SDKs/ads must comply with COPPA + GDPR.
- Forbid transmitting from children/unknown-age users: SIM serial, build serial, BSSID, **MAC**, SSID, **IMEI**, IMSI, phone number. No precise location of children.
- **Mixed audience → neutral age screen**: neutral date-of-birth entry, no hints ("must be 13+"), no adult defaults; children + unknown-age get child-directed experience.
- IAP: no deceptive "buy now" pressure on kids; distinguish virtual currency from real money; **DFF apps: Play re-authenticates before every IAP**; no non-dismissible/imitative ads causing inadvertent clicks; no real or simulated gambling / gambling ads in kids apps.
- Target audience & content must be completed by every app (buckets: children only / mixed / older users only); misrepresentation → Google may reassign audience.
- **Age-Restricted Content (inverse of Families):** real-money gambling apps and apps whose **core** functionality is dating/matchmaking MUST enable **"Restrict Minor Access"** — declare **"18 and over" sole target audience** (in force since Jan 28 2026 for RMG & core-dating). Incidental (non-core) dating (since Apr 15 2026) → effective alternative in-app age-gating (neutral age/DOB screen) blocking minors from those features; a covered app declaring mixed/teen audience is a violation.
- **Play Age Signals API** `com.google.android.play:age-signals` (beta): returns `userStatus` + age bands only in jurisdictions legally requiring it (Brazil Digital ECA, Texas SB 2420, Utah, Louisiana). Use **only** for age-appropriate experiences — **prohibited for advertising, marketing, profiling, analytics** (suspension risk). Significant changes require notifying Play so parents can re-approve.

### Minimum Functionality & Rejection Causes

Spam & Minimum Functionality (source answer/9898783):
- **Broken functionality banned:** crashing, force-closing, freezing, failing to install/load; no placeholder/"coming soon"/"Lorem ipsum"/TODO/dead-button shells in user-facing flows.
- **Minimum functionality:** stable, responsive, engaging experience with real utility. Banned: single-wallpaper apps, text/PDF-only apps, near-empty apps, **thin WebView/CustomTabsIntent wrappers around a website with no native features**, template-generated app markers (AppsGeyser), made-for-ads apps, clone farms / many near-identical template apps from one account.
- Site owners may ship a webview app only with engaging added functionality + owner permission; affiliate-traffic-only apps removed.
- **Deceptive behavior:** app must function as described; no fake system dialogs, fake virus alerts, fake cleaner/booster claims; no hidden/dormant features; no obfuscation to evade review; no Remote Config gating undisclosed post-review features (`killSwitch`, `isHidden`, date-based unlocks).

Other rejection-worthy categories:
- **Impersonation/IP:** no misleading impersonation of another developer/company/app (icons, titles, names, "Official"/"Google Inc.", national emblems); no copyright/trademark infringement or inducing it (piracy streams, YouTube-download tools); modifying copyrighted content is not a defense.
- **Restricted content:** child endangerment (CSAM → NCMEC, termination); sexual/porn, hate, gratuitous violence, terrorist content, sensitive-event exploitation, bullying, dangerous products (firearms/bump stocks/ammunition/explosives), marijuana/THC sales (banned regardless of legality), tobacco/vape sales; illegal activities.
- **Financial services:** binary options = flat ban; crypto no on-device mining, custodial exchanges/wallets need regional licenses (US FinCEN/state MTL; EU CASP/MiCA; non-custodial exempt) → **Financial Features Declaration**; personal loans: no repayment ≤ 60 days, US APR < 36% (TILA), **no contacts/photos access**, metadata must show min/max repayment + max APR + representative cost → **Personal Loan App Declaration** with country licenses.
- **Real-money gambling:** Google gambling application accepted, license per region, geo-gating, age-gating, **free to download and must NOT use Play billing** (transactions outside Play), AO rating, responsible-gambling info; only allowance-list countries.
- **Health:** no health misinformation; medical devices must declare + proof (FDA/CE); disclaimer "not a medical device..."; **Health apps declaration** (eff. Aug 28 2025); **Health Connect data:** no ads / no personalized ads, no selling/transferring to ad platforms/brokers/resellers, no credit/insurance/employment/lending decisions → restricted-use declaration.
- **UGC:** ToS acceptance, reasonable ongoing moderation, in-app **report** (objectionable content + users) and **block** (public UGC + 1:1 DMs), timely action; monetized UGC stricter; social/dating apps → **Child Safety Standards** (published CSAE standards URL, in-app report, NCMEC reporting process, child-safety point of contact, self-certify).
- **AI-generated content:** must block restricted/CSAE/deceptive output; **in-app report/flag of offensive AI content**; guard against prompt injection/jailbreaks (Google red-teams); accurate marketing.
- **MUwS/malware:** no covert tracking (stalkerware). Monitoring apps (parental/enterprise only) → **`IsMonitoringTool` manifest metadata flag** + persistent notification + unique icon; never covert partner/adult tracking. No hidden device admin, root exploits, keylogging, launcher-icon hiding.
- **Device/network abuse:** no killing other apps, modifying system settings without consent (must be reversible), blocking other apps' ads/monetization, gaming cheats, undisclosed proxying.

### Play Billing & Subscriptions

**Google Play Billing is REQUIRED for in-app purchases of digital goods/services consumed in the app** (mirror of Apple 3.1.1): virtual game items/currency, subscriptions (fitness/dating/education/music/video), app functionality/content (ad-free, unlocked features), cloud software/services. **Cannot be bypassed.**
- **Play Billing must NOT be used** for physical goods, physical services (rideshare, cleaning, airfare, gym, food delivery, event tickets), bill pay/remittance, P2P payments, online gambling — use Stripe/PayPal/Braintree there (correct).
- **Digital-goods bypass = violation:** Stripe/PayPal/Braintree/iyzico/Paddle/crypto unlocking app features; external purchase links/webviews routing around billing. Loot-box randomized rewards must disclose **odds before and near the purchase**.
- Virtual currency usable only inside the app/game where purchased; prices shown in-app must match the Play billing interface; listing-mentioned paid features flagged as paid.
- **Alternative / user-choice billing:** only in 35+ eligible markets (EEA, UK, US, India, Japan, South Korea…) for enrolled developers; outside these programs you must not steer users to non-Play payment.

Subscriptions:
- Paywall must show **price, billing period, auto-renewal terms, cancellation info, whether a subscription is required** — **in-app** (not only listing/behind links) **before** the purchase button.
- Free trial / intro offer: state trial duration, post-trial price, when billing starts, how to cancel before conversion; no misleading "free" for auto-converting trials; intro pricing must not hide post-intro charge.
- **Easy cancellation:** disclose how to manage/cancel + provide an easy **online** cancellation method (e.g., in-app link to Play subscription center). No dark patterns. Grace-period / account-hold follow Play billing rules — **no entitlements during account hold.**

Play Billing Library version policy — keep on a supported PBL (new apps/updates must use a recent library; migrate off deprecated majors):
- Dependency: `com.android.billingclient:billing` (Flutter `in_app_purchase`/`purchases_flutter`; RN/Expo `react-nativiap`/`react-native-purchases`).
- **PBL 7.0 / 8.0 require `compileSdk 34+`; PBL 9.0 requires `compileSdk 35+`;** PBL 8 min SDK 23; PBL 9 `targetSdkVersion` 35. Kotlin stdlib ≥ 1.9.x.
- Migration path: within 2 majors = direct; more = **stepped (2 majors at a time)**, `./gradlew assembleDebug` between steps. "Effective version" = where present deprecated APIs were last available (e.g. `SkuDetails` → v7 or earlier baseline).

Key API changes to apply (WRONG → RIGHT):

| Area | Legacy (wrong) | Modern (right) | Version |
|------|----------------|----------------|---------|
| Connection | manual `startConnection()`/retry in `onServiceDisconnected()` | `.enableAutoServiceReconnection()` on `BillingClient.Builder` | v8+ |
| Query | `SkuDetails`, `querySkuDetailsAsync()`, `SkuDetailsResponseListener` | `ProductDetails`, `queryProductDetailsAsync()`; listener receives `QueryProductDetailsResult` (`result.productDetailsList`) | v5→v8/v9 |
| Active purchases | `queryPurchases()` (sync), `queryPurchasesAsync(String skuType,…)` | `queryPurchasesAsync(QueryPurchasesParams)` w/ `ProductType` (`INAPP`/`SUBS`) | v4/v6+ |
| Purchase history | `queryPurchaseHistory()` (removed v8) | `queryPurchasesAsync()` + server tracking + **voided-purchases** API | v8 |
| Sub update | `setOldSkuPurchaseToken()`, `ProrationMode`, `setReplaceSkusProrationMode` | `SubscriptionUpdateParams`, `setOldPurchaseToken()`, `ReplacementMode` (e.g. `CHARGE_FULL_PRICE` vs `WITH_TIME_PRORATION`) | v6/v7 |
| Pending | parameterless `enablePendingPurchases()` (removed v8) | `enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())` **before `.build()`**; add `.enablePrepaidPlans()` if prepaid | v7/v8 |
| Alt billing | `AlternativeBillingListener`, `enableAlternativeBilling()` | `UserChoiceBillingListener`, `enableUserChoiceBilling()` | v6 |
| Terminology | "in-app items" | "one-time products" | v8 |
| SUBS buy | missing offer token | `ProductDetailsParams` **must** include `offerToken` (throws if empty) | v8 |
| Error codes | blocked-Play-Store → `ERROR`; `SERVICE_TIMEOUT` | `BILLING_UNAVAILABLE` (needs androidx.core 1.9+); network → `NETWORK_ERROR` | v6/v9 |
| Ack | — | `acknowledgePurchase()`/`consumeAsync()` **within 3 days** (mandatory since v2) | v2+ |
| PII | `setAccountId()` (PII) | `setObfuscatedAccountId()` (≤64 chars, **no PII**), `setObfuscatedProfileId()` | v2.2 |
| External | `enableExternalOffer()`, `isExternalOfferAvailableAsync()` | `enableBillingProgram(EnableBillingProgramParams)`, `isBillingProgramAvailableAsync()`, `launchExternalLink()`, `BillingProgram.EXTERNAL_PAYMENTS`; handle `DeveloperProvidedBillingDetails.getLinkUri()` null AND `""` (v9 @Nullable) | v8.2/8.3/v9 |

Newer monetization features (opt-in, post-upgrade): v7 **Installments**; v8 **Prepaid Plans**, **Personalized Pricing** (legal disclosure if price varies by user — `setIsOfferPersonalized()`, EU requirement, since v5); v9 **In-App Price Reviews** (user accepts opt-in price increase in-app; shown max once / 7 days). Locale-aware currency/price display for these paywalls (localized `NumberFormat` currency) is a correctness/compliance concern — never hardcode "$X".

```kotlin
// PBL 8+ query pattern
billingClient.queryProductDetailsAsync(params) { result: QueryProductDetailsResult ->
    val code = result.billingResult.responseCode
    val products = result.productDetailsList   // list now comes from the result object
}
```

### GDPR/CCPA

- **Data minimization** at every layer: collect only what is directly necessary for features promoted in the listing; prefer less-specific / derived / on-device alternatives; document *why* each field is needed.
- **Data classification** drives storage/logging/retention/control:

| Tier | Examples | Storage | Logging | Retention | User control |
|------|----------|---------|---------|-----------|--------------|
| Public | app version, device model | plain SharedPreferences | allowed | indefinite | — |
| Internal | feature flags, UI prefs | DataStore | key only | until uninstall | Settings |
| Confidential | email, name, photo URL | **EncryptedSharedPreferences** | **never** | per policy | **export + delete** |
| Restricted | auth tokens, SSN, payment | EncryptedSharedPreferences + Keystore | **never** | minimum necessary | export + delete + re-auth |

- **Subject rights:** Right to access (**data export**), Right to erasure (**hard delete**, anonymize → purge ~30 days), consent withdrawal (**revoke → stop + clean up that purpose**), portability, opt-out. Provide a **Privacy Dashboard** in Settings: consent toggles, data categories + encryption status, "Export My Data", "Delete My Data" (destructive, confirmation), Privacy Policy link.
- **Retention:** enforce minimum periods via periodic cleanup (`WorkManager` retention policies, e.g. analytics 90d, search history 30d, cached profiles 7d).
- **Security:** encrypt at rest per classification; HTTPS + certificate pinning in transit; request signing for Restricted tier; **no PII in URL query params** (use request body); exclude sensitive data from cloud backups/device transfer (`data_extraction_rules.xml`, avoid `allowBackup="true"` with sensitive data).
- Consent must be obtained **before** any non-essential data collection; users can export/delete; crash reports scrubbed; SDKs audited (review their privacy policy, data sent, GDPR/CCPA compliance, opt-out, and document in Data Safety).

### Privacy-by-Design Data Handling

Flow rules:
```
Collect:    User Input → Consent Check → Classify → Encrypt (if needed) → Store
Outbound:   Read → Decrypt → Minimize fields → HTTPS + Pinning → Server
Analytics:  Event → Consent Check → PII Scrub → Anonymize IDs → Send (or Drop)
```
- Storage map: small secrets → EncryptedSharedPreferences; relational → Room 3 + field-level encryption; files → EncryptedFile; Internal prefs → DataStore; Confidential+ prefs → EncryptedSharedPreferences.
- Use account-based or random UUID identity; **never hardware IDs (IMEI/MAC) for tracking**; anonymous session IDs regenerated periodically.
- **Permissions:** just-in-time (on feature trigger, not launch), explain *why* before the system dialog, degrade gracefully on denial, never dark-pattern/nag/block core function. Use `maxSdkVersion` to auto-remove. Compose: `rememberPermissionState` / rationale dialog; Photo Picker via `rememberLauncherForActivityResult(PickVisualMedia())` (0 perms).
- Retention/lifecycle: Create → Consent → Encrypt & Store → Active → periodic auto-purge → Export/Delete on request.

`assets/network_security_config.xml` (block cleartext; pin critical endpoints; user certs debug-only):
```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors><certificates src="system" /></trust-anchors>
    </base-config>
    <!-- <domain-config><domain includeSubdomains="true">api.yourdomain.com</domain>
         <pin-set expiration="2027-01-01"><pin digest="SHA-256">…=</pin></pin-set></domain-config> -->
    <debug-overrides><trust-anchors><certificates src="user" /></trust-anchors></debug-overrides>
</network-security-config>
```
`assets/data_extraction_rules.xml` (exclude PII from backup/transfer) — reference in manifest:
```xml
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="secure_prefs.xml" />
        <exclude domain="database" path="app_database" />
        <exclude domain="file" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="secure_prefs.xml" />
        <exclude domain="database" path="app_database" />
    </device-transfer>
</data-extraction-rules>
```

### Crash/Analytics PII

- **Never** log PII, tokens, or user-identifiable data at any level. Use a `PrivacyGuard`/`PrivacyLogger` wrapper and gate all telemetry behind consent.

```kotlin
// ❌ Log.d(TAG, "User logged in: email=$email, token=$token")
// ✅ Log.d(TAG, "User logged in successfully")
```
- Scrub crash/telemetry payloads before send (regexes):

| Target | Pattern | Replace |
|--------|---------|---------|
| Email | `[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}` | `[EMAIL]` |
| Phone | `\b\d{3}[-.]?\d{3}[-.]?\d{4}\b` / `\b\d{10,}\b` | `[PHONE]` / `[REDACTED_NUM]` |
| SSN | `\b\d{3}-\d{2}-\d{4}\b` | `[SSN]` |
| Card | `\b\d{4}[- ]?\d{4}[- ]?\d{4}[- ]?\d{4}\b` | `[CARD]` |
| Bearer token | `Bearer\s+\S+` | `Bearer [REDACTED]` |
- Also remove file paths containing usernames; don't send breadcrumbs with sensitive nav args; use anonymized user IDs (never email/phone) for `setUserId`.
- Release log stripping (R8, proguard-rules):
```proguard
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
```
- SDK collection disabled on consent revoke: `setAnalyticsCollectionEnabled(consent)`, `setCrashlyticsCollectionEnabled(consent)`.
- Declared SDKs (crash logs, device IDs) must match Data Safety form.

### Pre-Submission Checklist

Play Console **declarations** (enforced via forms, not just code):

| Declaration | Required when |
|-------------|---------------|
| Data safety form | every app |
| Target audience & content | every app |
| Content rating (IARC) | every app |
| Account deletion URL | apps with in-app account creation |
| Permissions Declaration Form | SMS/Call Log, QUERY_ALL_PACKAGES, MANAGE_EXTERNAL_STORAGE, non-a11y Accessibility, etc. |
| Location permissions declaration + video | ACCESS_BACKGROUND_LOCATION |
| Photo & video permissions declaration | READ_MEDIA_IMAGES / READ_MEDIA_VIDEO |
| Foreground service declarations (+ demo video) | each FGS type, targetSdk 34+ |
| Full-screen intent declaration | USE_FULL_SCREEN_INTENT |
| Financial Features Declaration | loans, crypto exchanges/wallets, tokenized assets, other financial features |
| Personal Loan App Declaration | loan apps (+ country licenses) |
| Health apps declaration | health/medical apps |
| Gambling application | real-money gambling apps |
| Child safety standards declaration | Social & Dating apps |
| Restrict Minor Access (18+ audience) | RMG + core-dating/matchmaking apps |
| News declaration | news apps |

Code/config gate (all must pass):
- [ ] Framework + merged manifest inspected (SDK-injected perms accounted for)
- [ ] targetSdk meets current requirement (API 35 now / 36 after Aug 31 2026); AAB not APK
- [ ] No restricted permission without qualifying use case + declaration; SMS/Call Log & OTP → SMS Retriever; photo picker instead of broad media perms; `<queries>` not QUERY_ALL_PACKAGES; no MANAGE_EXTERNAL_STORAGE misuse; Accessibility not misused; background location justified or absent; FGS types match usage; no unused permissions
- [ ] Privacy policy linked in-app AND in listing (HTTPS, real URL)
- [ ] Data safety declarations match actual SDK collection; crash/device IDs declared
- [ ] Prominent disclosure + affirmative consent before sensitive/off-purpose collection; consent precedes permission prompt
- [ ] Account deletion (in-app + web URL) present & wiping remote data (if account creation exists)
- [ ] Play Billing for digital goods; no external-payment bypass; subscription terms shown before purchase; trial/cancellation terms clear; odds disclosed
- [ ] Ads: no disruptive/unexpected/lockscreen/out-of-context ads; interstitials closeable ≤15s; UMP/CMP consent before personalization; no ad fraud
- [ ] Kids/Families: no AAID, self-certified ad SDKs only, no location, neutral age screen (if mixed); Age Signals not used for ads; Restrict Minor Access where required
- [ ] No dynamic native-code loading from outside Play; no hidden features/kill switches
- [ ] UGC moderation (report + block + filter); AI-content reporting; monitoring tools disclose `IsMonitoringTool`
- [ ] No debuggable/cleartext in release; network_security_config + data_extraction_rules present; no hardcoded secrets/committed keystores; R8 log stripping; mapping uploaded
- [ ] Metadata compliant (title ≤30, no promo/emoji/keyword stuffing); not a bare webview wrapper; no placeholder/TODO/broken flows

### Top Rejection Reasons

Top 10 Play rejection/removal reasons (verbatim):
1. **Data safety form mismatch** — SDKs collect data not declared
2. **Restricted permissions without declaration** — SMS, Call Log, QUERY_ALL_PACKAGES, MANAGE_EXTERNAL_STORAGE, Accessibility
3. **Background location without justification**
4. **Play Billing bypass** — external payment for digital goods
5. **Missing account deletion** — when account creation exists
6. **Target API level too old**
7. **Missing privacy policy** — in listing AND in app
8. **Families policy violations** — ad SDKs / AAID in kids apps
9. **Disruptive ads** — unexpected interstitials, lockscreen ads
10. **Minimum functionality** — webview wrappers, broken/placeholder features

Release-flow mistakes (from piyush): ❌ APK instead of AAB · ❌ `isDebuggable=true` in release · ❌ forgot to increment `versionCode` · ❌ `targetSdk` too old (rejected after grace period) · ❌ no mapping upload (obfuscated crashes) · ❌ incomplete Data Safety form (**app removed from store**).

## DOMAIN: Platform, Build, Release & Observability

### Accessibility

Required by law in many markets, checked by Play Store, benefits all users. Build in from the start; an inaccessible screen is an unfinished screen. WCAG 2.1 Level AA.

**Rule 1 — Content descriptions on all interactive elements**

```kotlin
// ✅ Describe the ACTION, not the icon name
Icon(
    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
    contentDescription = if (isLiked) "Remove from favorites" else "Add to favorites"
)

// ✅ Image with context
AsyncImage(model = imageUrl, contentDescription = "$productName product image")

// ✅ Decorative — explicitly null (TalkBack skips)
Icon(Icons.Default.Star, contentDescription = null)

// ✅ Icon + Label — description on icon is redundant, use null
Row {
    Icon(Icons.Default.Star, contentDescription = null) // label provides context
    Text("5 stars")
}

// ❌ Generic/useless description
Icon(Icons.Default.Favorite, contentDescription = "icon")
// ❌ Redundant (Button already says "Save")
Button(onClick = { }) { Icon(icon, contentDescription = "Save"); Text("Save") }
```

Content description rules: always provide for icons/images/custom graphics; set `null` only if decorative or parent already describes it; be specific ("Delete Shopping List" not "Delete"); include state ("Favorite, added").

**Label copy (TalkBack announces the ROLE already — describe purpose, not control type)**

| Prefer | Avoid |
|---|---|
| "Save" | "Save button" |
| "Submit" | "Click here to submit" |
| "Profile photo of Alex" | "Image" or "Image 1" |
| "Delete message" | "Button" (generic) |

Do not put "tap"/"click" in descriptions (input method varies). Keep labels short and unique in context ("Delete draft" vs "Delete message"). For editable fields use Material `label`/`placeholder` semantics; do not duplicate text into `contentDescription`.

**Rule 2 — Minimum touch target 48×48dp**

```kotlin
// ✅ Auto 48dp touch target
Icon(icon, contentDescription = description, modifier = Modifier.minimumInteractiveComponentSize())

// ✅ Explicit Box wrapper (visual size separate from touch target)
Box(modifier = Modifier.size(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
    Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
}

// ✅ defaultMinSize / sizeIn for custom clickables
Box(modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clickable(onClick = onClick))

// ❌ Bare Icon + clickable is only 24dp
Icon(icon, contentDescription = "Settings", modifier = Modifier.clickable { })
```

Material components (`Button`, `IconButton`, `Switch`) already handle 48dp internally — do not add redundant padding. Maintain minimum 8dp spacing between adjacent targets (`horizontalArrangement = Arrangement.spacedBy(8.dp)`).

**Rule 3 — Semantic properties for complex components**

| Property | Purpose | Example values |
|---|---|---|
| `contentDescription` | Override/declare announcement | `"Profile picture of $name"` |
| `role` | Declare interactive role | `Role.Button`, `Role.Image`, `Role.Switch`, `Role.Tab`, `Role.RadioButton`, `Role.Checkbox`, `Role.DropdownList` |
| `stateDescription` | Current state | `"Expanded"`, `"Selected"`, `"3 of 5"` |
| `heading()` | Section heading for nav | `Modifier.semantics { heading() }` |

```kotlin
// ✅ Custom state description
Switch(checked = isEnabled, onCheckedChange = onToggle, modifier = Modifier.semantics {
    stateDescription = if (isEnabled) "Notifications on" else "Notifications off"
})

// ✅ Heading for navigation
Text("Today's Orders", style = MaterialTheme.typography.titleLarge,
     modifier = Modifier.semantics { heading() })

// ✅ Custom toggle role
Row(modifier = Modifier.toggleable(value = isChecked, onValueChange = onCheckedChange, role = Role.Checkbox))

// ✅ Prefer built-in Material components over manual role assignment — they include correct semantics.
```

**mergeDescendants vs clearAndSetSemantics**

```kotlin
// ✅ Merge children into ONE announcement ("Blue T-Shirt, $29.99, In stock")
Card(modifier = Modifier.semantics(mergeDescendants = true) { }) { Column { Text(name); Text(price); Button { ... } } }

// ✅ GOOD — announces "4.5 stars, 128 reviews" as one item
Row(modifier = Modifier.semantics(mergeDescendants = true) { }) {
    Icon(Icons.Default.Star, contentDescription = null); Text("4.5 stars"); Text("(128 reviews)")
}
// ❌ BAD — screen reader stops on each child, fragmenting meaning
Row { Icon(Icons.Default.Star, contentDescription = "Star icon"); Text("4.5 stars"); Text("(128 reviews)") }

// ✅ clearAndSetSemantics — REPLACE all child semantics with one custom string
Row(modifier = Modifier.clearAndSetSemantics {
    contentDescription = "Rating: 4.5 stars from 128 reviews"
}) { StarRating(4.5f); Text("(128 reviews)") }

// Prevent merging — each child is its own node
Row(modifier = Modifier.semantics(mergeDescendants = false) {}) { Text(title); IconButton(onClick = onShare) { Icon(share, "Share") } }
```

| Need | Use |
|---|---|
| Group children into one announcement, keep their text | `semantics(mergeDescendants = true)` |
| Replace all child semantics with a custom string | `clearAndSetSemantics { }` |

Note: clickable containers merge child semantics by default (good for cards); control explicitly when needed.

**Rule 4 — Custom accessibility actions (TalkBack menu / discover gestures without navigating to each button)**

```kotlin
Modifier.semantics {
    customActions = listOf(
        CustomAccessibilityAction("Add to favorites") { onFavorite(); true },
        CustomAccessibilityAction("Share") { onShare(); true },
        CustomAccessibilityAction("Delete ${item.title}") { onDelete(item.id); true }
    )
}
// lambda returns true if the action was handled successfully
```

**Rule 5 — Screen reader traversal order**

```kotlin
Row(modifier = Modifier.semantics { isTraversalGroup = true }) {
    Text(name, modifier = Modifier.semantics { traversalIndex = 0f })
    Text(price, modifier = Modifier.semantics { traversalIndex = 1f })
    RatingBar(rating, modifier = Modifier.semantics { traversalIndex = 2f })
}
```

**Rule 6 — Live regions for dynamic content**

```kotlin
// Polite — announced when idle, doesn't interrupt
Text("Found ${results.size} results", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })

// Assertive — interrupts current speech; critical errors only
Text(errorMessage, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
```

Prefer polite live regions on the composable that actually changed, or rely on `stateDescription`/`error` semantics so TalkBack picks up updates. Avoid firing raw `AccessibilityEvent.TYPE_ANNOUNCEMENT` for every minor UI tick; use one-off announcements only when there is no stable node to attach semantics to.

**Custom interactive elements** — when using `Modifier.clickable` on a non-Button composable, add `onClickLabel` and `role`:

```kotlin
Card(modifier = Modifier
    .clickable(onClickLabel = "Open book details") { onBookClick(book.id) }
    .semantics { role = Role.Button }) { Text(book.title) }
```

**Hide decorative element:** `Modifier.semantics { invisibleToUser() }`. **Custom semantic key:** `val IsFavoriteKey = SemanticsPropertyKey<Boolean>("IsFavorite"); Modifier.semantics { set(IsFavoriteKey, true) }`.

**Color contrast (WCAG AA minimum)**

| Text type | Minimum ratio |
|---|---|
| Normal text (< 18sp / < 14sp bold) | 4.5 : 1 |
| Large text (≥ 18sp or ≥ 14sp bold) | 3 : 1 |
| UI components / graphical objects | 3 : 1 |

```kotlin
// ✅ Material 3 color roles guarantee contrast
Text("Important message", color = MaterialTheme.colorScheme.onSurface)
// ❌ Custom color without contrast check
Text("Label", color = Color(0xFF888888)) // gray on white fails WCAG
```

Use `MaterialTheme.colorScheme` tokens rather than hardcoded colors (contrast-safe across light/dark). `onSurfaceVariant` on `SurfaceVariant` is often marginal — check. Dynamic contrast: `if (bg.luminance() > 0.5) Color.Black else Color.White`. Verify pairs with Material Theme Builder / webaim contrast checker.

**Never rely on color alone** — pair with icon + text + pattern:

```kotlin
// ❌ BAD — only color differentiates status
Box(modifier = Modifier.background(if (isOnline) Color.Green else Color.Red))
// ✅ GOOD — icon + text + color, merged
Row(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$text status" }) {
    Icon(if (isOnline) Icons.Default.CheckCircle else Icons.Default.Cancel, contentDescription = null)
    Text(if (isOnline) "Online" else "Offline")
}
```

**Form errors** announce via `.semantics { error(errorMsg) }` plus icon + supportingText; support dark mode and high contrast (Android 14+ `configuration`); visible focus indicators for keyboard nav (`Modifier.focusable()`, `onFocusChanged { isFocused = it.isFocused }`).

**Focus management**

```kotlin
// Programmatic focus
val focusRequester = remember { FocusRequester() }
OutlinedTextField(..., modifier = Modifier.focusRequester(focusRequester))
LaunchedEffect(Unit) { delay(100.milliseconds); focusRequester.requestFocus() }

// Custom focus order / skip overlay
Modifier.focusProperties { canFocus = false }
Modifier.focusProperties { next = second; previous = first }
val (first, second, third) = remember { FocusRequester.createRefs() }

// Keyboard IME chaining
keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
```

**Skip to content** — visually hidden but accessible button: `.semantics { contentDescription = "Skip to main content" }.size(1.dp).alpha(0f)`.

**Announce list size:** `LazyColumn(modifier = Modifier.semantics { contentDescription = "${users.size} users" })`.

**MVI integration** — accessibility does not change architecture:

| Concern | Where | Why |
|---|---|---|
| Semantic descriptions (`contentDescription`, `stateDescription`) | Screen / Leaf composables | UI-layer concern, resolve from state near rendering |
| Semantic keys/enums for dynamic descriptions | `State` data class | e.g. `statusLabel: StringKey` — UI resolves to localized string |
| `Modifier.semantics` | Composable `modifier` chains | Applied in UI, never in ViewModel |
| Accessibility-triggered actions | `onEvent` callbacks → ViewModel | Same as any interaction — event pipeline |

Keep descriptions in UI layer, not state. State holds semantic keys; Screen/Leaf resolves via `stringResource()`.

**Testing accessibility**

```kotlin
// Compose tests — touch target
composeTestRule.onNodeWithContentDescription("Delete").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
// content description exists
composeTestRule.onNodeWithContentDescription("Delete ${testItem.name}").assertExists()
// error announced
composeTestRule.onNodeWithText("Email").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Invalid email address"))
// state announced
.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Enabled"))
// find by semantics / verify none missing descriptions
composeTestRule.onRoot().printToLog("Semantics")
composeTestRule.onNode(hasContentDescription("Add to favorites") and hasClickAction()).assertIsDisplayed().performClick()

// Espresso (View/hybrid screens): add espresso-accessibility artifact
@Before fun enableA11yChecks() { AccessibilityChecks.enable() }
```

Lint (fail in CI): `android { lint { enable += setOf("ContentDescription", "TouchTargetSizeCheck", "TextContrastCheck", "ClickableViewAccessibility"); abortOnError = System.getenv("CI") == "true" } }`.

Manual: TalkBack (navigate all screens, verify announcements/descriptions/errors/custom actions in long-press menu); Switch Access (focus order); Font scaling (Settings → Display → Font size → Largest — no cut-off text, no overlapping targets); Accessibility Scanner (contrast ratios).

**Accessibility checklist (run before every release)**
- [ ] All interactive elements have meaningful `contentDescription`
- [ ] All touch targets ≥ 48×48dp
- [ ] Cards/list items use `mergeDescendants = true`
- [ ] Color-only info has text/shape alternative
- [ ] All text passes 4.5:1 (WCAG AA)
- [ ] Headings marked with `heading()`
- [ ] Swipeable actions have `customActions`
- [ ] Dynamic content uses live regions
- [ ] Tested with TalkBack enabled and touch exploration

**Never:** rely on color alone; touch targets < 48dp; ignore form validation announcements; use `contentDescription` on decorative images (use `null`); forget to test with a11y services enabled; hardcode text (use string resources).

### Notifications & FCM

**Rule 1 — Create channels before posting (Android 8+; no-op on 24-25).** Do this once in `Application.onCreate()`, never dynamically per notification.

```kotlin
class MyApplication : Application() {
    @Inject lateinit var channelSetup: NotificationChannelSetup
    override fun onCreate() { super.onCreate(); channelSetup.createAll() }
}

// androidx.core NotificationChannelCompat (preferred) — group by USER INTENT, not technical category
NotificationChannelCompat.Builder(NotificationChannels.MESSAGES, NotificationManagerCompat.IMPORTANCE_HIGH)
    .setName("Messages").setDescription("Direct messages").setVibrationEnabled(true).setLightsEnabled(true).build()
NotificationManagerCompat.from(this).createNotificationChannelsCompat(channels)

// platform API variant (API 26+):
NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
    description = "New message notifications"; enableVibration(true); enableLights(true) }
// IMPORTANCE_LOW for downloads/sync: setShowBadge(false)
```

| Channel | Constant | IMPORTANCE |
|---|---|---|
| Messages | `messages` | HIGH (heads-up) |
| Breaking news | `breaking_news` | HIGH |
| Updates/Reminders | `updates`/`reminders` | DEFAULT |
| Downloads/Weekly digest/FGS | `downloads`/`weekly_digest`/`background_sync` | LOW/MIN |

Importance levels: `IMPORTANCE_HIGH` time-sensitive (heads-up); `IMPORTANCE_DEFAULT` standard; `IMPORTANCE_LOW` background ops, no sound; `IMPORTANCE_MIN` ongoing FGS, no sound/badge.

**Rule 2 — NotificationCompat builder + POST_NOTIFICATIONS (Android 13+)**

```kotlin
// Always gate on permission on API 33+
if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    == PackageManager.PERMISSION_GRANTED) {
    notificationManager.notify(id, notification)
}
// Helper form:
fun canShowNotifications(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    else NotificationManagerCompat.from(context).areNotificationsEnabled()

// Request permission (Compose)
val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(it) }
LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    else onResult(true)
}

val notification = NotificationCompat.Builder(context, NotificationChannels.MESSAGES)
    .setSmallIcon(R.drawable.ic_notification)   // monochrome, tinted
    .setLargeIcon(loadBitmap(article.imageUrl))
    .setContentTitle(senderName)
    .setContentText(messagePreview)
    .setStyle(NotificationCompat.BigTextStyle().bigText(longText).setBigContentTitle(title).setSummaryText(cat))
    .setContentIntent(deepLinkPendingIntent)
    .addAction(replyAction)
    .setColor(ContextCompat.getColor(context, R.color.notification_accent)).setColorized(true)
    .setAutoCancel(true)                          // dismiss on tap
    .setPriority(NotificationCompat.PRIORITY_HIGH)
    .setCategory(NotificationCompat.CATEGORY_MESSAGE) // CATEGORY_NEWS for news
    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE) // hide on lock screen
    .build()
```

**PendingIntent mutability (critical):**
- Deep link / content tap → `PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE`
- Reply action (with `RemoteInput`) → `FLAG_MUTABLE` **required** for RemoteInput; use `getBroadcast` to a receiver.
- ❌ `FLAG_MUTABLE` without RemoteInput = security vulnerability; ❌ `FLAG_IMMUTABLE` with RemoteInput = reply won't work.
- API 31+ mutable form: `if (SDK >= S) FLAG_MUTABLE or FLAG_UPDATE_CURRENT else FLAG_UPDATE_CURRENT`.

```kotlin
val replyInput = RemoteInput.Builder("reply_text").setLabel("Reply...").build()
val replyPendingIntent = PendingIntent.getBroadcast(context, id, replyIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
val replyAction = NotificationCompat.Action.Builder(R.drawable.ic_reply, "Reply", replyPendingIntent)
    .addRemoteInput(replyInput).build()
```

**Deep link with proper back stack:** use `TaskStackBuilder.create(context).addNextIntentWithParentStack(deepLink).getPendingIntent(id, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)`. Intent: `Intent(Intent.ACTION_VIEW, "app://example.com/$route".toUri(), context, MainActivity::class.java)` with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP`. Treat notification tap like cold entry — resolve destination, then push/replace so Back returns sensibly (Navigation3: match back stack to user expectations).

**Styles:** `BigTextStyle` (long text; `setContentText` = collapsed), `BigPictureStyle` (`.bigPicture(bitmap)`, `setLargeIcon` = collapsed), `InboxStyle` (`.addLine`, `setSummaryText`), `MessagingStyle(Person)` (`.setConversationTitle`, `.addMessage(text, timestamp, sender)`).

**Progress notifications:** `.setProgress(max, progress, indeterminate)` (`setProgress(0,0,true)` = indeterminate); in-progress `.setOngoing(true)`, `.setPriority(PRIORITY_LOW)`, `.setOnlyAlertOnce(true)` (silent updates after first); on completion `.setProgress(0,0,false).setOngoing(false).setAutoCancel(true)`.

**ProgressStyle (API 36+ / Android 16):** rich multi-step user journeys (rideshare, delivery, navigation). `Notification.ProgressStyle().addSegment(Segment(500)); addPoint(Point(750)); progress = 250; progressTrackerIcon = Icon.createWithResource(...)`. Provide fallback (`if (SDK < BAKLAVA) showProgressNotification(...)`) — API 36+ only. Use standard `setProgress()` for simple determinate/indeterminate tasks.

**Groups:** individual notifications `.setGroup(GROUP_KEY_NEWS)`; summary `.setGroup(...).setGroupSummary(true).setStyle(InboxStyle().setSummaryText("${n} new articles"))`, distinct `SUMMARY_ID`.

**Action buttons + receiver:** actions via `PendingIntent.getBroadcast` to `@AndroidEntryPoint class NotificationActionReceiver : BroadcastReceiver()` with `goAsync()` + coroutine; register in manifest `android:exported="false"` with `<intent-filter>` actions; call `NotificationManagerCompat.from(context).cancel(id)` to dismiss after handling.

**FCM (Firebase Cloud Messaging)**

```kotlin
// project build.gradle.kts: id("com.google.gms.google-services") version "4.4.2" apply false
// app: id("com.google.gms.google-services"); implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
//      implementation("com.google.firebase:firebase-messaging-ktx")

@AndroidEntryPoint
class AppFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        when (data["type"]) { /* route to notificationHelper.showXxx(...) */ }
    }
    override fun onNewToken(token: String) { // send new token to backend
        CoroutineScope(Dispatchers.IO).launch { tokenRepository.updateFcmToken(token) }
    }
}
```

```xml
<service android:name=".service.AppFirebaseMessagingService" android:exported="false">
    <intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT" /></intent-filter>
</service>
```

Token: `Firebase.messaging.token.await()` → save to secure storage + register with backend; update on login/logout.

**Foreground service notification** — required on all API levels; start foreground BEFORE doing work, within 5 s:

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(NOTIFICATION_ID, buildProgressNotification(0),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC /* API 29+ */)
    ...
    return START_NOT_STICKY
}
// .setOngoing(true).setSilent(true).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
```

**Testable interface pattern:** define `interface NotificationManager { showNotification; showNotificationWithAction; showProgressNotification; dismissNotification; dismissAllNotifications }`, impl `AndroidNotificationManager` in `core:data`, bind via Hilt `@Binds`, use `FakeNotificationManager` in tests.

**Notification ID strategy:** fixed IDs for single-instance (`SYNC_SERVICE=1001`, `DOWNLOAD_SERVICE=1002`); dynamic per-entity via `hashCode()` (`fun forMessage(id: String) = id.hashCode()`). ❌ random/hardcoded ad-hoc IDs — use consistent ID per type.

**Test notifications on multiple API levels: 24, 26, 29, 31, 33, 36.**

**Common mistakes (verbatim):**
- ❌ Not creating channel before posting — notification silently dropped on Android 8+
- ❌ `FLAG_MUTABLE` on PendingIntent without RemoteInput — security vulnerability
- ❌ `FLAG_IMMUTABLE` on PendingIntent with RemoteInput — reply won't work
- ❌ Not checking POST_NOTIFICATIONS permission on Android 13+
- ❌ Hardcoded notification IDs — use consistent ID per notification type, not random
- ❌ No `setAutoCancel(true)` — notification stays after user taps it
- ❌ Never forget `startForeground()` within 5 s of starting a foreground service
- ❌ Never `setOngoing(true)` for dismissible notifications
- ❌ Never rely on notifications for critical user-facing info (can be disabled)
- ❌ Never create channels dynamically per notification
- ❌ Never show notifications from background on API 26+ without a proper foreground service

**Also (media/share/background guidance):** request audio focus via `AudioManager`/`AudioFocusRequest` before playback (permanent loss=stop, transient=pause, duck=lower volume). Long-running background playback = foreground service + `MediaStyle` notification + `MediaSession`. Support PiP (`android:supportsPictureInPicture="true"`, `enterPictureInPictureMode()`). Use system chooser `Intent.createChooser(send, null)` for shares, not custom UI. Deferrable work → WorkManager; user-visible ongoing → foreground service; push → FCM. Avoid wake locks / silent background services WorkManager can schedule.

### Background Work

Android aggressively kills background processes. **WorkManager** = the vast majority of background work (survives process death, reboot, Doze). **Foreground Services** = user-aware ongoing work. **AlarmManager** = exact-time triggers. Wrong choice = killed jobs or battery drain.

**WorkManager — when to use:** upload on connectivity; scheduled sync; image processing/transcoding; batched analytics; any work that must complete even if the app is closed.

```toml
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version = "2.10.0" }
hilt-work = { group = "androidx.hilt", name = "hilt-work", version = "1.2.0" }
```

**Worker with Hilt:**

```kotlin
@HiltWorker
class ArticleSyncWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted workerParams: WorkerParameters,
    private val articleRepository: ArticleRepository
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)
            setProgress(workDataOf(KEY_PROGRESS to 0))
            val result = articleRepository.refreshArticles(forceRefresh)
            if (result is DomainResult.Success) Result.success(workDataOf(KEY_ARTICLES_SYNCED to result.data.size))
            else if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure(...)
        } catch (e: CancellationException) { throw e }  // always rethrow
        catch (e: Exception) { if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure() }
    }
}
```

**One-time with constraints + backoff + unique name:**

```kotlin
val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
val request = OneTimeWorkRequestBuilder<ArticleSyncWorker>()
    .setConstraints(constraints)
    .setInputData(workDataOf(KEY_FORCE_REFRESH to forceRefresh))
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS, TimeUnit.MILLISECONDS)
    .addTag("sync").build()
workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)  // KEEP = don't replace pending
```

**Periodic — minimum interval 15 min (OS-enforced), flex window:**

```kotlin
val request = PeriodicWorkRequestBuilder<ArticleSyncWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
    .setConstraints(constraints).build()
workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)  // UPDATE replaces params
```

**Chaining:** `beginWith(...).then(...).then(...).enqueue()`; parallel→merge `beginWith(listOf(d1,d2)).then(merge).enqueue()`.

**Observing:** `workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { ... }` → `WorkInfo.State.ENQUEUED/BLOCKED/RUNNING/SUCCEEDED/FAILED/CANCELLED`; read `workInfo.progress` / `workInfo.outputData`.

**Expedited (Android 12+)** — high-priority user-initiated work that should start immediately:

```kotlin
OneTimeWorkRequestBuilder<SendMessageWorker>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
// Worker MUST override getForegroundInfo() for pre-12 compatibility (returns ForegroundInfo(id, notification))
```

**Foreground services** — user-aware work (playback, navigation, downloads, location). **Android 14: declare the specific type.**

```xml
<service android:name=".service.MusicPlaybackService" android:foregroundServiceType="mediaPlayback" android:exported="false" />
<service android:name=".service.LocationTrackingService" android:foregroundServiceType="location" android:exported="false" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

Types seen: `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (constant) / `dataSync` (manifest), `mediaPlayback`, `location`. Start foreground BEFORE work; call `stopSelf()` when done.

**AlarmManager — exact-time triggers only** (calendar reminder, timer alarm). Inexact preferred otherwise. `SCHEDULE_EXACT_ALARM` permission on Android 12+; check `alarmManager.canScheduleExactAlarms()` else open `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`; use `setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)`. Cancel with `FLAG_NO_CREATE`.

**Reschedule after reboot:** `RECEIVE_BOOT_COMPLETED` permission + `<receiver android:exported="true">` for `BOOT_COMPLETED`; in `onReceive` use `goAsync()` and reload saved alarms from Room.

**Doze/App Standby** limit background execution — design for batched work and FCM. Release WakeLocks promptly (`wakeLock.acquire(timeout)`). Avoid ContentProvider auto-init at startup (see Profiling/Startup and Build sections).

**Decision table**

| Need | API |
|---|---|
| Deferrable, must-complete, constrained (sync/upload/cleanup) | WorkManager (periodic ≥ 15 min; one-time; expedited for immediate user-initiated) |
| User-visible ongoing work (playback/navigation/download in progress) | Foreground service + typed notification |
| Precise wall-clock trigger (reminder/alarm) | AlarmManager exact + BOOT_COMPLETED reschedule |

### Gradle Build System

**Non-negotiables:** Kotlin DSL only (`build.gradle.kts`/`settings.gradle.kts`, never Groovy `.gradle`); Version Catalog only (`libs.versions.toml`, never hardcode versions); KSP only, never kapt; `minSdk = 24`, `targetSdk = 35`, `compileSdk = 35`; Kotlin 2.0.0+, AGP 8.5.0+; `namespace` in every module.

**Version Catalog (`gradle/libs.versions.toml`) is the single source of truth.** Four sections: `[versions]`, `[libraries]`, `[plugins]`, `[bundles]`. Naming: kebab-case keys → dot accessors (`koin-core` → `libs.koin.core`). BOM-managed libs omit `version.ref`. Group with `# ---- Section ----` comment headers. Bundles group libs always added together (`implementation(libs.bundles.androidx.base)`) — convenience only, no resolution change; CMP projects rarely need bundles.

```toml
[versions]
agp = "8.5.2"; kotlin = "2.0.21"; ksp = "2.0.21-1.0.25"
composeBom = "2024.10.00"; hilt = "2.52"; room = "2.6.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }  # BOM-managed, no version

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

```kotlin
// ✅ catalog aliases everywhere
implementation(libs.androidx.core.ktx); plugins { alias(libs.plugins.android.application) }
// ❌ hardcoded
implementation("androidx.core:core-ktx:1.13.1")
```

**settings.gradle.kts:**

```kotlin
pluginManagement {
    repositories {
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }
        mavenCentral(); gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "MyApp"; include(":app")
```

**Root build.gradle.kts = plugins only, `apply false`, NO dependencies:**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

**App module buildTypes:** `release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }`; `debug { applicationIdSuffix = ".debug"; isDebuggable = true }`; `compileOptions` + `kotlinOptions { jvmTarget = "17" }`; `buildFeatures { compose = true; buildConfig = true }`.

**Build performance — gradle.properties (always enabled):**

```properties
org.gradle.jvmargs=-Xmx4096m -XX:+UseParallelGC -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
android.enableR8.fullMode=true
kotlin.incremental=true
ksp.incremental=true
```

**KSP over kapt (2-3× faster, Kotlin-native):**

```kotlin
// ❌ plugins { id("kotlin-kapt") }; kapt(libs.hilt.compiler); kapt(libs.room.compiler)
// ✅ plugins { alias(libs.plugins.ksp) }; ksp(libs.hilt.compiler); ksp(libs.room.compiler)
```

CMP KSP wiring: `listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.room.compiler) }`.

**Build variants / product flavors:**

```kotlin
android {
    flavorDimensions += "environment"
    productFlavors {
        create("dev") { dimension = "environment"; applicationIdSuffix = ".dev"; versionNameSuffix = "-dev"
            buildConfigField("String", "BASE_URL", "\"https://dev-api.myapp.com/\"") }
        create("staging") { dimension = "environment"; applicationIdSuffix = ".staging"; versionNameSuffix = "-staging" }
        create("prod") { dimension = "environment"; buildConfigField("String", "BASE_URL", "\"https://api.myapp.com/\"") }
    }
}
// variants: devDebug, devRelease, stagingDebug, stagingRelease, prodDebug, prodRelease
```

Variant names: `{productFlavor}{buildType}` with **capitalized** build type (`developmentDebug`, `productionRelease`). `BuildConfig` not generated AGP 8+ unless `buildFeatures.buildConfig = true` (needed for `buildConfigField` and `BuildConfig.DEBUG`). Flavor-specific source sets: `app/src/development/` etc.; `app/src/debug/`, `app/src/release/` per build type. Multiple dimensions (e.g. `tier`=free/paid) → combinatorial variants (`developmentFreeDebug`) — keep dimensions few to avoid build/CI explosion.

Commands: `./gradlew :app:assembleProductionRelease`, `installDevelopmentDebug`, `dependencies`, `--refresh-dependencies`, `tasks --group="build"`.

**Convention plugins** for multi-module consistency (introduce when 3+ modules duplicate config; not needed for ≤3 modules; use `build-logic/` included-build, not `buildSrc` for versions):

```kotlin
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library"); pluginManager.apply("org.jetbrains.kotlin.android")
        extensions.configure<LibraryExtension> { compileSdk = 35; defaultConfig.minSdk = 24
            compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } }
    }
}
// build-logic/convention/build.gradle.kts: plugins { `kotlin-dsl` }; compileOnly(libs.android.gradlePlugin)...
// gradlePlugin { register("androidLibrary") { id = "yourapp.android.library"; implementationClass = "..." } }
// module: plugins { id("yourapp.android.library") } // one line instead of 20
```

Typical catalog of convention plugins: `app.android.application`, `.compose`, `.baseline`, `app.android.library`/`.compose`, `app.android.feature`, `app.android.test`, `app.android.room`, `app.android.lint`, `app.hilt`, `app.detekt`, `app.spotless`, `app.jvm.library`, `app.kotlin.serialization`, `app.firebase`, `app.sentry`, `app.play.vitals`. `build-logic/settings.gradle.kts` versionCatalogs: `create("libs") { from(files("../gradle/libs.versions.toml")) }`. Root settings: `pluginManagement { includeBuild("build-logic") }`. Enable `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` (Gradle 8; on by default Gradle 9).

**Non-transitive R classes** (`android.nonTransitiveRClass=true`): each module generates its own R with only its resources. Unqualified `R` may not resolve from a sub-package → `import com.example.feature.products.R`; cross-module → `import com.example.core.ui.R as CoreUiR` (use aliases; group at top). Improves build speed but requires explicit imports.

**api vs implementation:** default `implementation`; promote to `api` only when a dependency type surfaces in the module's public API (e.g. a `Flow<T>` in a repository interface where `T` lives elsewhere).

**Composite builds** — conditional so CI works without local checkout:

```kotlin
val localLibPath = file("../my-library")
if (localLibPath.exists()) { includeBuild(localLibPath) { dependencySubstitution { substitute(module("com.example:my-library")).using(project(":my-library")) } } }
```

**Gradle wrapper** (`gradle/wrapper/gradle-wrapper.properties`) = most important file for reproducibility; always set `distributionUrl` explicitly, never rely on IDE-bundled Gradle; regenerate: `./gradlew wrapper --gradle-version=8.7 --distribution-type=bin`. AGP↔Gradle matrix:

| AGP | Required Gradle | Notes |
|---|---|---|
| 8.5.x | 8.7 | Stable; recommended for 2024 |
| 8.6.x | 8.7 | Minor bump |
| 8.7.x | 8.9 | Do NOT use with Gradle 8.11+ (breaks `debugRuntimeClasspathCopy`) |
| 8.8.x | 8.10.2 | Gradle 8.11 strict-mode compatible |

**Build performance workflow:** measure baseline (clean + incremental) → `--scan` (Performance → Build timeline: Initialization/Configuration/Execution) → apply ONE optimization → measure again. Local report without upload: `--profile` (→ `build/reports/profile/`).
- **Lazy task config:** `tasks.register` (lazy) not `tasks.create` (eager instantiates even when not in graph).
- **Avoid I/O during configuration** (breaks config cache): `providers.fileContents(...).asText`, `providers.exec { commandLine("git",...) }.standardOutput.asText.map { it.trim() }`.
- **Pin versions** — no dynamic `1.+`/`latest.release`/`-SNAPSHOT` (non-reproducible, network every build).
- Slow config → `tasks.register()`, defer I/O, move plugins to convention plugins, drop `subprojects{}`/`allprojects{}`. Slow execution → kapt→KSP, `org.gradle.caching=true` (fix non-deterministic inputs: timestamps/absolute paths), `org.gradle.parallel=true`, raise `-Xmx`. Slow resolution → pin versions, reorder repos (google first, mavenCentral second), remove unused.
- Shared/remote build cache via `gradle/init.gradle.kts` (`buildCache { local {...}; remote<HttpBuildCache> { isEnabled=false /* true on CI */ } }`).

**AGP 9 changes (Gradle 9 / AGP 9.0):**
- **Built-in Kotlin:** AGP 9 includes Kotlin — do NOT apply `org.jetbrains.kotlin.android` in Android app modules (remove from all `build.gradle.kts`/convention plugins). `org.jetbrains.kotlin.plugin.compose` still required for Compose.
- `compileSdk { version = release(36) }` (or `release(35)`) instead of `compileSdk = 36`. (Integer still works inside KMP `androidLibrary { compileSdk = 35 }`.)
- New KMP library plugin `com.android.kotlin.multiplatform.library` for KMP modules targeting Android; `androidApp` thin shell required by AGP 9 (com.android.application cannot coexist with KMP plugin).
- `kotlin {}` must NOT be nested inside `android {}` (put `kotlin { jvmToolchain(21) }` at top level).
- **Gradle Managed Devices:** `testOptions.managedDevices.localDevices { create("pixel6Api31") { device="Pixel 6"; apiLevel=31; systemImageSource="aosp" } }` (was `devices { maybeCreate(...) }`); groups use `create("ci")`; reference `localDevices[name]`.
- **Removed gradle.properties:** `org.gradle.configureondemand`, `android.enableBuildCache`, `android.enableJetifier`, `android.defaults.buildfeatures.aidl/renderscript/resvalues/shaders`, `org.gradle.configuration-cache.problems=warn`; after migration also remove `android.builtInKotlin`, `android.newDsl`, `android.uniquePackageNames`, `android.enableAppCompileTimeRClass`.
- `CommonExtension` type params removed; `KotlinAndroidProjectExtension` not registered with built-in Kotlin (configure via `tasks.withType<KotlinCompile>().configureEach { compilerOptions { ... } }`).
- **Hilt ≥ 2.59.2 required** for AGP 9; **KSP ≥ 2.3.6** (use `2.x` suffix, e.g. `2.2.21-2.0.5`, not `1.x`).
- Type-safe project accessors on by default in Gradle 9; JVM 17+ to run Gradle 9.
- Legacy API removal: `BaseExtension`, `applicationVariants.all`, `Convention`, `com.android.build.gradle.api.*` → use `androidComponents` API.
- AGP upgrade steps (Google skill): check current AGP (if <9 prefer Android Studio AGP Upgrade Assistant); update deps (KSP ≥2.3.6, Hilt ≥2.59.2); migrate to built-in Kotlin; migrate to new DSL; migrate kapt→KSP/legacy-kapt; handle custom BuildConfig; clean gradle.properties. Verify: Gradle sync, `./gradlew help`, `./gradlew build --dry-run`. Do NOT run `clean` to verify; never add `android.disallowKotlinSourceSets=false`; KMP projects unsupported by this skill. Troubleshooting: Paparazzi ≤ v2.0.0-alpha04 broken with AGP 9.

**App bootstrap resources (minSdk <26 needs PNG mipmaps; no XML layouts needed for Compose-only but still required):** `values/strings.xml`, `values/themes.xml` (NoActionBar), `drawable/ic_launcher_background.xml`+`_foreground.xml`, `mipmap-anydpi-v26/ic_launcher.xml`+`ic_launcher_round.xml` (adaptive-icon). **VectorDrawable only supports `<path>`, `<group>`, `<clip-path>` — NOT SVG `<circle>`/`<rect>`/`<ellipse>`** (draw as arc/`M`+`L` path data). minSdk 26 ≈ 99% device coverage (2025).

### Code Obfuscation & App Size

R8 = default shrinker/obfuscator. Enable in release: `isMinifyEnabled = true`, `isShrinkResources = true`, `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`. Full mode: `android.enableR8.fullMode=true` (and for R8-analyzer: verify `android.enableR8.fullMode=false` is removed — full mode is default in AGP 9; migrating to AGP ≥9 improves build time).

**Key rules:** most AndroidX/Jetpack libs ship consumer rules inside the AAR — only add manual rules when docs say so or R8 full-mode requires it. Retrofit needs explicit rules (interfaces via `Proxy` invisible to R8). `EncryptedSharedPreferences` needs `-dontwarn` for Tink annotations. SQLCipher native methods kept. Keep `SourceFile`/`LineNumberTable` for line numbers in stack traces.

```proguard
# Kotlin + Retrofit + Room essentials
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}

# Compose stability annotations (keep for recomposition skipping)
-keep @androidx.compose.runtime.Stable class **
-keep @androidx.compose.runtime.Immutable class **
-keepclassmembers class * { @androidx.compose.runtime.Stable <methods>; }
```

**R8 analyzer (Google skill):** select path by version — AGP ≥ 9.3.0 → standalone `./gradlew :app:analyzeReleaseR8Config` (→ convert pb→json, analyze); AGP <9.3.0 + R8 ≥ 9.3.7-dev → quantitative config analyzer; else heuristic — inspect `proguard-rules.pro`, remove bundled/redundant rules, **refine** broad package-wide rules, validate changes with Macrobenchmark + UI Automator. Prioritize keep-rule impact; keep rules that subsume library consumer rules in mind. Suggest-only (no code changes).

**App size reduction:** R8 shrink (30-60%); upload **AAB** not APK (Play generates optimized per-device splits); `resConfigs("en","es")` keeps only supported languages; WebP over PNG (up to 70% smaller); vector drawables over multi-density PNGs; NDK `abiFilters.addAll(listOf("armeabi-v7a","arm64-v8a"))`; resize images to display size, Coil for loading, batch network requests, enable HTTP/2.

### Signing & Release

**Never commit keystore or passwords** — read from environment:

```kotlin
android {
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: ""
            if (keystorePath.isNotEmpty()) {
                storeFile = file(keystorePath); storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS"); keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes { release { signingConfig = signingConfigs.getByName("release") } }
}
```

Release build config (all required): `isDebuggable = false` (never ship debuggable), `isMinifyEnabled = true`, `isShrinkResources = true`; increment `versionCode` every upload; production (not dev/staging) endpoints; no test/mock data. Build release bundle — **AAB required** (not APK) since Aug 2021: `./gradlew bundleProdRelease` → `app/build/outputs/bundle/prodRelease/app-prod-release.aab`. **Play App Signing** (Google-managed release key; you use a recoverable upload key; self-managed release key unrecoverable if lost) — enroll before first release. Upload ProGuard `mapping.txt` (R8/ProGuard) so crash reports are deobfuscated/symbolicated (Play Console → Android vitals → Deobfuscation, or `firebaseCrashlytics { mappingFileUploadEnabled = true }`).

### CI/CD

**GitHub Actions full workflow** (`.github/workflows/ci.yml`) — `push`/`pull_request` on main/develop; `concurrency: group: ${{ github.workflow }}-${{ github.ref }}` + `cancel-in-progress: true`; jobs ordered lint → unit-test → build → deploy-internal.

```yaml
on: { push: { branches: [main, develop] }, pull_request: { branches: [main, develop] } }
concurrency: { group: ${{ github.workflow }}-${{ github.ref }}, cancel-in-progress: true }
jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }   # Java 21 in newer templates
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew lint --continue
      - if: failure()
        uses: actions/upload-artifact@v4
        with: { name: lint-results, path: '**/build/reports/lint-results-*.html' }
  unit-test: { needs: [], run: ./gradlew testDebugUnitTest --continue, upload '**/build/test-results/**/*.xml' }
  build:
    needs: [lint, unit-test]
    run: ./gradlew assembleProdDebug   # upload app/build/outputs/apk/prod/debug/*.apk
  deploy-internal:
    needs: build
    if: github.ref == 'refs/heads/main'
    environment: production
    steps:
      - name: Decode keystore
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > keystore.jks
      - name: Build release AAB
        env: { KEYSTORE_PATH: ${{ github.workspace }}/keystore.jks, KEYSTORE_PASSWORD: ..., KEY_ALIAS: ..., KEY_PASSWORD: ... }
        run: ./gradlew bundleProdRelease
      - name: Upload to Play Store (Internal)
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}
          packageName: com.company.app
          releaseFiles: app/build/outputs/bundle/prodRelease/*.aab
          track: internal
          status: completed
```

**Required secrets:** `KEYSTORE_BASE64` (`base64 -i keystore.jks | pbcopy`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON` (Play service account with Editor permission).

**Gradle caching** (saves 3-5 min/run): `uses: gradle/actions/setup-gradle@v4` with `cache-read-only: ${{ github.ref != 'refs/heads/main' }}`.

**Instrumented tests on Firebase Test Lab:**

```yaml
- uses: google-github-actions/auth@v2
  with: { credentials_json: ${{ secrets.GCP_SERVICE_ACCOUNT_JSON }} }
- run: |
    gcloud firebase test android run --type instrumentation \
      --app app/build/outputs/apk/debug/*.apk --test app/build/outputs/apk/androidTest/debug/*.apk \
      --device model=Pixel6,version=33 --device model=Pixel_Tablet,version=33 --timeout 15m
```

Compose Stability validation in CI: `needs: build` job running `./gradlew stabilityCheck` (fails on stability regression; `stabilityDump` to baseline). Play Vitals: `./gradlew playVitalsReport` on schedule.

**Common mistakes (verbatim):**
- ❌ Committing keystore or passwords — always use GitHub Secrets
- ❌ No `concurrency:` group — multiple PRs queue up unnecessarily
- ❌ Running tests before lint — lint is faster, fail fast
- ❌ No Gradle caching — every CI run downloads all dependencies
- ❌ Building unsigned APK for Play Store — AAB must be signed
- ❌ Uploading APK instead of AAB — Play Store requires AAB since 2021

**Distribution by platform (CMP context):** Android APK/AAB (`assembleRelease`/`bundleRelease`); Desktop DMG/MSI/DEB (`packageDmg`/`packageMsi`/`packageDeb`); iOS `.app/.ipa` via Xcode Archive (Gradle builds framework only, `embedAndSignAppleFrameworkForXcode`, `isStatic = true` for App Store). Multi-platform desktop via `workflow_dispatch` matrix (macos-latest/windows-latest/ubuntu-latest).

### Play Console Release

**Version management:** `versionCode` integer must increment every upload; `versionName` human-readable semver. Automate in CI: `val versionCode = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1`.

**Release checklist before every submission:**
1. Version management (both fields bumped).
2. Release build config (`isMinifyEnabled`/`isShrinkResources` true, `isDebuggable` false).
3. Build release AAB (`./gradlew bundleProdRelease`) — AAB required since Aug 2021.
4. `targetSdk` within 1 year of latest Android (as of 2025 min 34, recommended 35; `compileSdk = 35`, `minSdk = 24`).
5. **Data Safety form** (App content → Data safety) — data collected, encrypted in transit, deletion requestable, third-party sharing. Failure = rejected.
6. **Content rating** (App content → Content ratings) — IARC questionnaire, global (ESRB/PEGI); get before review.
7. **Store listing:** app icon 512×512 PNG max 1MB no alpha; feature graphic 1024×500; screenshots 2-8 per form factor (phone 1080×1920/1920×1080 min; tablet 1920×1200/2560×1600 min); short desc ≤ 80 chars; full desc ≤ 4000 chars.
8. **Release tracks:**

| Track | Testers | Review |
|---|---|---|
| Internal testing | up to 100, instant publish | no |
| Closed testing | specific groups | yes |
| Open testing | anyone | yes |
| Production | full rollout | yes (3-7 days first time) |

Strategy: Internal → Closed alpha → Open/beta → **staged production rollout 10% → 50% → 100%**.
9. **Play App Signing** (Setup → App integrity): Google manages release key, you upload with recoverable upload key; enroll before first release (self-managed release key = unrecoverable if lost).
10. **ProGuard mapping upload** — `firebaseCrashlytics { mappingFileUploadEnabled = true }` auto-uploads on release; or upload `build/outputs/mapping/release/mapping.txt` to Play Console → Android vitals → Deobfuscation files.

**Pre-launch checklist:** `isDebuggable=false`; `isMinifyEnabled`+`isShrinkResources`; `targetSdk=35`; `versionCode` incremented; no test/mock code in release; API keys point to production; all permissions declared + justified in Data Safety; ProGuard preserves DTO/serialization classes; all 3rd-party SDK privacy policies in Data Safety; tested on physical device with release build (`adb install`); crash monitoring + analytics enabled.

**Common mistakes (verbatim):**
- ❌ Uploading APK instead of AAB — rejected since 2021
- ❌ `isDebuggable = true` in release — security violation, rejected by Play
- ❌ Forgetting to increment versionCode — rejected, same version already uploaded
- ❌ targetSdk too old — rejection after grace period
- ❌ No mapping file upload — crash reports show obfuscated stack traces
- ❌ Incomplete Data Safety form — app removed from store

### Crash Reporting & Vitals

**Provider-agnostic interface** — keep SDK code out of feature modules; `CrashReporter` in `core:domain`, impls in `core:data`, wire via Hilt `@Binds`; swap Firebase/Sentry by changing binding + convention plugin.

```kotlin
interface CrashReporter {
    fun setUserId(id: String?); fun setUserProperty(key: String, value: String)
    fun log(message: String); fun recordException(throwable: Throwable, context: Map<String,String> = emptyMap())
}
```

**Firebase Crashlytics** — `app.firebase` convention plugin applies `com.google.gms.google-services` + `com.google.firebase.crashlytics`, Firebase BoM, `firebase-analytics` + `firebase-crashlytics` (separate `-ktx` no longer needed), native symbol upload; `FirebaseApp.initializeApp(this)`. Map `recordException`→`crashlytics.recordException` (+ `setCustomKey` for context). Non-fatal in coroutines: `CoroutineExceptionHandler { _, e -> Firebase.crashlytics.recordException(e) }` passed to `viewModelScope.launch(crashHandler)`. Disable in debug: `firebase_crashlytics_collection_enabled=false` meta-data / `setCrashlyticsCollectionEnabled(false)`.

**Sentry** — `app.sentry` plugin applies `io.sentry.android.gradle` (+`io.sentry.kotlin.compiler` for auto `@Composable` tagging), `sentry-android` + `sentry-compose-android`. Auto-init via ContentProvider; manifest `io.sentry.dsn`, `io.sentry.traces.sample-rate`, `io.sentry.traces.user-interaction.enable`, `io.sentry.attach-view-hierarchy`, `io.sentry.attach-screenshot`. Init in Application `SentryAndroid.init { options -> dsn; logs.isEnabled = true; environment = if (DEBUG) "debug" else "production"; release = VERSION_NAME; tracesSampleRate = 1.0; profilesSampleRate = 1.0; sendDefaultPii = false }` — use ONE of `profilesSampleRate` OR `profileSessionSampleRate`. Use `Sentry.withScope { }` (isolated scope) for one-off tags (avoid leaking to global/thread scope); `SentryTraced(name=...)` for critical screens. `sentry { org; projectName; authToken = System.getenv("SENTRY_AUTH_TOKEN"); autoUploadProguardMapping = true; includeSourceContext = true }`. `sentry-okhttp` for network breadcrumbs.

**Composition/delegation** — wrap `PrivacyAwareCrashReporter : CrashReporter by crashReporter` to scrub email regex + sensitive keys (`password/token/secret/key/auth`) before sending; delegate in ViewModels (`crashReporter: CrashReporter` no `private`; `... : ViewModel(), CrashReporter by crashReporter`).

**Breadcrumb quality:** good = user navigation (`category="navigation"`), UI clicks (`"ui.click"`), state changes (`"state"`). Bad = internal details ("Coroutine launched on IO dispatcher"), every method call, verbose data dumps (also PII risk).

**Compose screen tracking:** Crashlytics breadcrumbs don't include Compose destination names — log `logScreenView` in the app-level `AppNavigation()` coordinator via `LaunchedEffect(navigationState.topLevelRoute)`.

**Play Vitals (store-aggregated, complements in-app crash reporting):**
- Bad-behavior thresholds (verify current in Play docs): overall phone **crash rate ~1.09%**, **ANR rate ~0.47%**; per-device-model buckets differ. Exceeding reduces distribution/discovery.
- Optional Play Developer Reporting API automation in **`build-logic`/CI** (NOT in `:app`, never shipped in APK). Implement as convention plugin `app.play.vitals` (id) registering `playVitalsReport` on `rootProject` ONLY; apply `alias(libs.plugins.app.play.vitals)` in root `build.gradle.kts` only; never via `subprojects`/`allprojects`.
- Auth: Google Cloud service account, JSON from env/CI secret (never commit). OAuth scope `https://www.googleapis.com/auth/playdeveloperreporting`.
- Client `com.google.apis:google-api-services-playdeveloperreporting` v1beta1 (+ `google-auth-library-oauth2-http`), pinned in `libs.versions.toml` (`googlePlayDeveloperReporting`).
- Metric sets expose `get` (describe) + `query` with `TimelineSpec` (e.g. `DAILY`, timezone `America/Los_Angeles`); query `anrRateMetricSet`/`crashRateMetricSet`/`slowStartRateMetricSet`; map `MetricsRow` (`anrRate`, `anrRate7dUserWeighted`, `anrRate28dUserWeighted`) to data classes; compare to thresholds → green/yellow/red Slack summary.
- Task runs on build JVM; put HTTP in repository `withContext(Dispatchers.IO)`, `@TaskAction` only `runBlocking { }` (pick one outer IO scope). Prefer nullable/`Result`/`runCatching` + `logger.lifecycle`/`warn` + post "metrics unavailable" + return without rethrowing so optional health reporting doesn't fail CI. Schedule nightly job injecting secrets + `apps/com.example.app` resource name.

### Profiling & ANRs/Leaks

**Frame budgets:** 60 Hz ~16.7 ms, 90 Hz ~11.1 ms, 120 Hz ~8.3 ms. Slow frames exceed budget; frozen frames (hundreds of ms) harm perceived quality. Investigate with `FrameTimingMetric()`, Perfetto, system GPU tools. **JankStats** / system GPU profiler for per-frame capture.

**Startup targets:** Cold < ~1s (investigate if > ~2s w/o progress UI); Warm < ~500ms; Hot < ~100ms. **TTID** (first frame, system-measured, Logcat) vs **TTFD** (fully interactive — call `reportFullyDrawn()` / ReportDrawn APIs).

**Macrobenchmark** (real perf, not just profiling; detect regressions): dedicated `:benchmark` module, `plugins { alias(libs.plugins.android.test) }`, `testBuildType = "benchmark"`, `targetProjectPath = ":app"`, `testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"`. `benchmark` build type in app = `initWith(getByName("release")); signingConfig = signingConfigs.getByName("debug"); isDebuggable = false`.

```kotlin
benchmarkRule.measureRepeated(
    packageName = "com.example.app",
    metrics = listOf(StartupTimingMetric()),          // FrameTimingMetric(), MemoryUsageMetric()
    compilationMode = CompilationMode.Partial(),       // approximates Baseline Profile behavior
    iterations = 5, startupMode = StartupMode.COLD) {  // WARM/HOT
    pressHome(); startActivityAndWait() }
```

Use a **physical device** (emulators add noise); disable animations: `adb shell settings put global animator_duration_scale 0` (+ `transition_animation_scale 0`, `window_animation_scale 0`). Run `./gradlew :benchmark:connectedCheck` or single class via `-Pandroid.testInstrumentationRunnerArguments.class=...`. Artifacts: JSON in `connected_android_test_additional_output/`, HTML in `reports/androidTests/connected/`. Scroll/jank: `device.findObject(By.res("home_feed")).fling(Direction.DOWN)`.

**Custom system tracing (Tracing 2.0 / `androidx.tracing.wire`):** `trace("processImage") { ... }`; coroutine-aware `tracer.traceCoroutine(category="main","taskOne"){...}` — appears in Perfetto trace for method-level visibility.

**Baseline Profiles** — AOT-compile critical paths; 10-30% faster cold start, less startup/scroll jank; release builds only. `:baselineprofile` module (`android.test` + `androidx.baselineprofile`, `targetProjectPath = ":app"`, `baselineProfile { managedDevices += "pixel6Api31"; useConnectedDevices = false }`, GMD `systemImageSource = "aosp"`); app adds convention plugin + `baselineProfile(project(":baselineprofile"))`.

```kotlin
BaselineProfileRule().collect(packageName = "com.example.app", includeInStartupProfile = true) {
    startActivityAndWait(); device.wait(Until.hasObject(By.res("auth_form")), 5000); device.findObject(By.text("Login")).click(); device.waitForIdle() }
```

Generate `./gradlew :app:generateReleaseBaselineProfile` (→ `app/src/release/generated/baselineProfiles/baseline-prof.txt`). Compare `CompilationMode.None()` vs `Partial(BaselineProfileMode.Require)`. Include startup + runtime journeys; update when adding features.

**ProfileInstaller** (`androidx.profileinstaller`) ensures ART compiles the profile right after install/update — instant first-launch benefit, essential outside Play (direct APK/MDM) and for consistency.

**ReportDrawn APIs (`androidx.activity.compose`):** `ReportDrawn()` (immediately), `ReportDrawnWhen { uiState is Success }` (predicate), `ReportDrawnAfter { awaitCriticalData() }`. Call once per screen (extra calls no-op); report even on error (`Success || Error`) to avoid blocking metrics; report when primary content visible, not all images/ads. Logcat: `ActivityTaskManager: Fully drawn com.example.app/.MainActivity: +850ms`.

**Memory / leaks:**

```toml
leakcanary = { group = "com.squareup.leakcanary", name = "leakcanary-android", version = "2.14" }
```
```kotlin
dependencies { debugImplementation(libs.leakcanary) }  // no code — auto-installs via ContentProvider in debug
```

Compose leak patterns: ❌ Activity context captured in long-lived `viewModelScope` lambda → use `@ApplicationContext`; ❌ store `@Composable` lambda outside composition. Bitmap: downsample to display size `ImageRequest.Builder(context).data(url).size(200,200).crossfade(true).build()`.

**android-profiler (Google skill):** orchestrator that routes recording/analysis intents (system traces, heap dumps, method recordings, callstack samples, memory allocations, ad-hoc SQL queries) for user+system apps; investigate bottlenecks/jank/leaks/startup. Intent disambiguation first; set `$SKILL_ROOT` from `references/env_setup.md`; route recording via `recording_orchestrator.md`, analysis via `analysis_orchestrator.md`.

**Compose recomposition perf (design constraint, not a late phase):** unstable types cause waste — `List/Map/Set` (use `kotlinx.collections.immutable` `ImmutableList`/`ImmutableMap`), data classes with `var`, unverifiable external classes, lambdas capturing unstable values. `@Immutable` (all-val) / `@Stable` (reads consistent, writes notify) — **never lie to the compiler**. Lambda stability: `remember(id){ { ... } }` or method refs (`viewModel::onArticleClicked`). `derivedStateOf` for threshold crossings (`scrollState.value > 200`) / filtering (non-trivial only). `items(key = { it.id }, contentType = { it.type })`; `Modifier.animateItem()`. `graphicsLayer {}`/`offset {}`/`drawBehind {}` defer state reads to layout/draw phase (no recomposition). Three phases: Composition → Layout → Drawing; push state reads latest. `staticCompositionLocalOf` (rarely changes) vs `compositionLocalOf` (frequent). Never nest LazyColumns — single LazyColumn w/ mixed `contentType`. `BasicTextField2`/`rememberTextFieldState()` for high-frequency input (prevents dropped keystrokes). Recomposition counts via Layout Inspector (**Show Composition Counts**). R8 Compose rules (keep stability annotations) per Code Obfuscation section.

**CPU / battery / network / image hot paths:** hoist `items.size` out of loops / use `forEach`; `StringBuilder` not `+=` in loops; compile `Regex` once (companion object). Wakelocks with timeout (`wakeLock.acquire(10*60*1000L)`); balanced-accuracy location at reasonable intervals; OkHttp cache + offline header swap; compress images before upload; batch requests; HTTP/2. Coil over raw `BitmapFactory`; WebP; vector drawables.

**App startup optimization:** avoid `ContentProvider.onCreate()` auto-init (runs on main thread before `Application.onCreate()`; each adds cold-start cost). Disable lib auto-init (e.g. WorkManager) by removing `androidx.work.WorkManagerInitializer` meta-data from `InitializationProvider`; use `androidx.startup` `Initializer<T>` (`create` + `dependencies()`), register only leaf initializers in the shared `InitializationProvider` (deps resolved automatically), or lazy `AppInitializer.getInstance(context).initializeComponent(...)`. Never block main thread in `Application.onCreate` — `applicationScope.launch(Dispatchers.IO)`. Classify: Eager (App Startup: crash reporter, logging, StrictMode); after first frame (analytics, feature flags, remote config); on demand (image loader, ML models, DB migrations, WorkManager). Splash: `androidx.core:core-splashscreen`, `installSplashScreen()` **before** `super.onCreate()`, `setKeepOnScreenCondition { viewModel.isLoading.value }` (read a boolean, runs before each draw), `postSplashScreenTheme` switches after dismiss; animated icons API 31+ ≤ 1000 ms. Use `ProcessLifecycleOwner` to defer non-critical init.

**Startup optimization checklist:** audit ContentProviders → App Startup; classify eager/after-first-frame/on-demand; `installSplashScreen()`+`setKeepOnScreenCondition`; generate Baseline Profiles for startup; measure cold start Macrobenchmark before/after; no main-thread I/O/network/compute at startup.

### Internationalization

- **Always use string resources** — `stringResource(R.string.x)`, never hardcoded `Text("...")`.
- **Plurals** — `pluralStringResource(R.plurals.item_count, count, count)` (1st arg = quantity for selection, 2nd = `%d` format arg). Arabic/Russian have extra forms (`zero/one/two/few/many/other`); English `zero/one/other`.
- **Never concatenate** ("Hello "+name breaks word order) → parameterized `%1$s`; never assume English word order.
- **RTL:** `android:supportsRtl="true"`; use `start`/`end` not `left`/`right` (padding, `Arrangement.Start/End`, `TextAlign.Start/End`); mirror directional icons (`Modifier.scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f)`); `LocalLayoutDirection.current`; force `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)`.
- **Date/time/currency** via `kotlinx-datetime` + `DateTimeFormatter.ofLocalizedDate/Time/DateTime(FormatStyle...).withLocale(Locale)`; `NumberFormat.getCurrencyInstance(locale)` + `Currency.getInstance(code)`; locale-aware relative time with plurals.
- **Locales qualifiers:** `values-es`, `values-ar`, `values-fa`, `values-zh-rCN`, `values-pt-rBR`, combine `values-es-night` / `drawable-ldrtl` / `drawable-night-ldrtl`; `string-array` via `resources.getStringArray(R.array.days_of_week)`.
- **Handle text expansion** (German/Finnish 30-40% longer): `widthIn(min=120.dp)`, `maxLines`/`overflow`; relative `lineHeight = 1.5.em`. Provide translator context comments; ICU MessageFormat for complex plurals.

**String ownership (with `nonTransitiveRClass`):** `core:common`/`core:domain` = generic errors/result states; `core:ui` = shared UI labels, a11y descriptions, common actions; `feature:xxx` = feature-specific. Cross-module access via `import com.example.core.ui.R as CoreUiR`. Never duplicate strings across modules; move shared ones to `core:*`.

CI: grep for hardcoded `Text("` (fail), `validate_translations.sh` for key coverage across locales.

Testing: set `Configuration.setLocale(locale)` + `Locale.setDefault`; parameterized tests across locales; `assertLayoutDirectionEquals(LayoutDirection.Rtl)`; `@Preview(locale = "ar")`; screenshot golden per locale.

**Font scaling (a11y/i18n):** support large fonts without truncation/overlap (see Accessibility manual test: Settings → Display → Font size → Largest; `adb shell settings put system font_scale 1.5`).

### Device/Emulator Automation for AI

**Mental model:** "MCP is your eyes on the device. Code is your hands in the codebase." Write → build → install → screenshot → inspect → refine. Without MCP you're building blind (guessing 16dp padding, animation timing, transition clipping). With MCP you verify.

**adb capabilities:** `adb install`/`push`; `shell am start` (launch activity/deep-link); `shell input tap/swipe`; `shell screencap`; `adb pull`; `shell uiautomator dump` (accessibility/UI hierarchy XML); `adb logcat`; `shell dumpsys activity` (running activities/back stack/tasks); `shell dumpsys gfxinfo` (frame timing/jank); `shell wm size`/`wm density` (responsive testing).

**Screenshot-driven review loop:**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.app/.MainActivity --es "destination" "profile/123"
adb shell screencap /sdcard/screen.png && adb pull /sdcard/screen.png /tmp/review/screen.png
# review: matches design? 4dp spacing? typography? design-system color roles?
```

Capture across: light (`cmd uimode night no`), dark (`cmd uimode night yes`), large font (`settings put system font_scale 1.5`), RTL (`setprop persist.demo.forcertl 1 && adb reboot`).

**Log-driven debugging:** `adb logcat -v time AppTag:D *:S`; `adb logcat *:E`; `adb logcat -d > /tmp/crash.log` (dump buffer around crash); `adb logcat -c` before reproducing; consistent `private const val TAG` + `Log.d/e`. Patterns: `grep -E "AndroidRuntime|FATAL|compose"` (render errors), `"OnLowMemory|GC|OutOfMemory"` (memory), `"Recomposing|recomposition"`, `"NavController|navigation"`, `"SharedElement|SharedTransition"`.

**UI hierarchy inspection** (`uiautomator dump /sdcard/ui.xml` → pull → grep content-description) verifies shared-element keys, content descriptions, semantic roles, focus traversal.

**Animation/jank:** `dumpsys gfxinfo com.example.app reset` → interact (`input tap 540 960`) → `dumpsys gfxinfo` → read "Janky frames" (>16 ms) + 50/90/95/99th percentile (90th < 16 ms = smooth). Frame-interval screenshots (`sleep 0.1 && screencap && pull` ×4) to verify transition start position/clipping/z-order/fade.

**Responsive:** `wm size 1280x800 && wm density 240` (tablet), `wm size 1080x2400 && wm density 420` (phone), `wm size reset`/`wm density reset`, `cmd device_state state 1|2` (folded/unfolded).

**`android` CLI (Google skill):** install via platform curl script (linux/darwin_arm64/darwin_x86_64/windows). Subcommands: `create` (project templates, `--name`, `--output`, `--minSdk`, `--list`); `sdk install/update/remove/list`; `run` (build/deploy/launch, `--activity`, `--device`, `--debug`, `--use-delta-install`); `install` (faster delta install than adb); `emulator create/start/stop/list/remove`; `screenshot` / `screen capture` (PNG) / `screen resolve`; `layout` (UI tree as JSON — faster than screenshot for debugging; `-d` diff since last dump, `-p` pretty, `-o` file); `docs search "kw"` / `docs fetch kb://...` (authoritative Android knowledge base — use for migration guides, API examples, best practices); `describe` (project structure → artifact paths); `info` (SDK location, connected devices); `skills find/add/remove/list`; `studio` (`render-compose-preview`, `find-declaration`, `version-lookup`); `update`.

### Anti-Patterns

- Hardcoding dependency versions in build files / Groovy `.gradle` / kapt / missing `configuration-cache=true` → all covered in Gradle section.
- ❌ Using Groovy `.gradle` instead of `.kts`; ❌ hardcoding versions instead of catalog; ❌ `kapt` instead of KSP; ❌ missing `enableEdgeToEdge()` in MainActivity; ❌ `compileSdk` lower than `targetSdk`; ❌ forgetting `namespace` per module; ❌ `implementation` for annotation processors instead of `ksp`; ❌ root `build.gradle.kts` with dependencies (root = plugins only).
- **Accessibility:** `contentDescription = "icon"`/generic; meaningful images left `null` without a comment; applying `role` manually when a Material component already provides it; extra padding on Material components that already meet touch targets; color alone for state; localized a11y strings in ViewModel state; hardcoded a11y text; unmerged fragmenting announcements.
- **Notifications:** see Common Mistakes list (channel-after-post, wrong mutability flags, missing POST_NOTIFICATIONS check, ad-hoc IDs, no autoCancel, no startForeground within 5 s, dynamic channels, background notifications without FGS).
- **Build:** `tasks.create` eager; file/network/`exec{}` I/O during configuration (breaks config cache); dynamic versions; unconditional `includeBuild` (breaks CI); `allprojects{}`/`subprojects{}`; over-engineering convention plugins for ≤3 modules; relying on IDE-bundled Gradle (no wrapper URL).
- **AGP 9:** applying `org.jetbrains.kotlin.android` on AGP 9+; nesting `kotlin {}` inside `android {}`; using `compileSdk = N` (use `compileSdk { version = release(N) }`); Hilt < 2.59.2 / KSP < 2.3.6; `devices { maybeCreate }` (use `localDevices { create }`).
- **Performance:** state reads in composition during animation (push to layout/draw phase); `List<T>` params (use `ImmutableList`); lying with `@Immutable`; un-keyed `LazyColumn` items; filtering without `derivedStateOf`; nested scrollables; blocking `Application.onCreate`; `ContentProvider` auto-init; loading full-res bitmaps; `refresh()` on `PagingData` inside composable body.
- **R8:** broad package-wide keep rules; keeping bundled library consumer rules; forgetting to keep SourceFile/LineNumberTable (loses line numbers); shipping `isDebuggable=true`.
- **Play Vitals / crash reporting:** putting service-account credentials or Reporting API calls inside `:app` or the APK; registering the `playVitalsReport` task via `subprojects`/`allprojects`; letting optional health reporting throw and fail CI.
- **Edge-to-edge:** `Modifier.padding()` on a LazyColumn parent (clips — pass insets to `contentPadding`); `safeDrawingPadding`/similar on a `NavigationSuiteScaffold` parent (clips, breaks edge-to-edge — apply to individual screens/FAB); double IME padding (`imePadding()` when `contentWindowInsets` already contains IME insets, or `padding(...asPaddingValues())` + `imePadding()`); `SOFT_INPUT_ADJUST_RESIZE` (deprecated — use manifest `android:windowSoftInputMode="adjustResize"`); forgetting `isAppearanceLightStatusBars`/`isAppearanceLightNavigationBars` with `WindowCompat.enableEdgeToEdge` (but DO NOT set these with `ComponentActivity.enableEdgeToEdge` — it handles icons automatically); not `window.isNavigationBarContrastEnforced = false` under a bottom bar (SDK 29+, else translucent scrim appears over bar colors).

### Release-Readiness Checklist

**Gradle/build:** Kotlin DSL; version catalog everywhere; KSP (no kapt); `minSdk=24`/`targetSdk=35`/`compileSdk=35`; `namespace` per module; `org.gradle.configuration-cache=true` + `parallel` + `caching`; `android.nonTransitiveRClass=true`; `enableR8.fullMode=true`; `enableEdgeToEdge()`.

**Obfuscation/size:** `isMinifyEnabled=true`, `isShrinkResources=true`, `isDebuggable=false`; `proguard-rules.pro` (DTO/serialization kept, SourceFile/LineNumberTable kept, Compose stability annotations kept); mapping uploaded to Crashlytics/Sentry + Play deobfuscation; AAB build; `resConfigs` trimmed.

**CI:** concurrency group + cancel-in-progress; lint before unit tests before build; Gradle cache; keystore from secrets (never committed); Firebase Test Lab / physical-device instrumented runs; `stabilityCheck`/accessibility lint fail on CI.

**Play Console:** versionCode incremented (CI `BUILD_NUMBER`); AAB (not APK); targetSdk current; Data Safety form complete (incl. 3rd-party SDK policies + permission justification); content rating (IARC) obtained; store listing (icon 512², feature graphic 1024×500, 2-8 screenshots/form factor, short ≤80 / full ≤4000); Play App Signing enrolled; staged rollout 10%→50%→100%; production API keys; no test/mock data; crash monitoring + analytics enabled.

**Quality:** Accessibility checklist complete (48dp targets, contentDescription, mergeDescendants, contrast ≥4.5:1, headings, customActions, live regions, TalkBack/Switch Access/font-scaling/contrast tested); notifications tested on API 24/26/29/31/33/36 (channel created before post, POST_NOTIFICATIONS checked); background work uses correct API (WorkManager/FGS-with-typed-notification/exact-alarm); startup measured cold<~1s (Baseline Profile + ProfileInstaller + App Startup + splash); no main-thread I/O at startup; memory leak-free (LeakCanary clean on debug); RTL + i18n (locales, plurals, string resources, text expansion) validated; verified on a physical device with the release build via `adb install` + screenshot/logcat/gfxinfo review.
