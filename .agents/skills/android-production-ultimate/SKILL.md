---
name: android-production-ultimate
description: Complete production knowledge for shipping Android apps — Kotlin 2.x, Jetpack Compose, Material 3 / M3 Expressive, MVI/MVVM Clean Architecture, UDF, spring motion & animation, adaptive layouts, Navigation 3, Room/DataStore/Ktor/Retrofit/Paging, Hilt/Koin, security & Play Integrity & biometrics, Google Play policy & Data Safety & GDPR & billing, testing & TDD, Compose performance & baseline profiles & R8, CI/CD & release, accessibility, notifications, background work, AND design taste / anti-AI-slop visual judgment (Anthropic frontend-design + AI-tell catalogs). Use when building, reviewing, optimizing, or shipping ANY Android / Kotlin / Jetpack Compose app.
---

# THE ULTIMATE ANDROID PRODUCTION SKILL

Distilled from the best-of-breed agent-skill ecosystem: haidrrrry (compose-kotlin-agent-skills),
Meet-Miyani (compose-skill, 37 refs), piyushverma0 (27 skills: animation, design-system, security,
biometrics, performance), skydoves (compose-performance-skills), chrisbanes (Google DevRel),
krshmbb (android-security-skill), Drjacky/GubenkoAleksey (claude-android-ninja), 13krub
(android-lead-agent-skills), rezaiyan (kmp-claude-playbook), Google's official `android/skills`
(play-policy-insights, navigation-3, edge-to-edge, r8-analyzer, play-billing), devsemih
(playstore-review), AbhijeetBabar (privacy-first-android). Design-taste layer from Anthropic's
official `frontend-design` skill (anthropics/skills), `avoid-ai-design` (funboy322), `kill-ai-slop`
(yetone), and `ui-ux-pro-max` (nextlevelbuilder) — the 2026 anti-AI-slop + design-intelligence canon.

## HOW TO USE THIS SKILL

1. The GOLDEN RULES below are always active — they are the non-negotiable core.
2. ROUTE FIRST: open `references/INDEX.md` and find the task in its "Task → what to read"
   table. Read ONLY the named `###` sections (they cite file + line anchors). Never load the
   whole corpus (~200K tokens).
3. Before working in a domain, read that domain's full section (below, or the matching file in
   `references/`) and follow it. Every rule, snippet, table and checklist there is load-bearing.
3. When REVIEWING agent output, start from the Banned Anti-Patterns master lookup and grep for each WRONG pattern.
4. Before any Play Store submission, run the Pre-Submission Checklist + Top Rejection Reasons (Legal domain).

## GOLDEN RULES — ALWAYS ACTIVE

### Toolchain baseline (2026)
- Kotlin 2.x K2, AGP 9+, Compose BOM 2025.05+, compileSdk/targetSdk 35+, JDK 17, Gradle version catalog, KSP (never kapt in new code).

### Architecture
- Clean Architecture: `domain` is pure Kotlin (zero Android imports); deps point inward; repository interfaces in domain, impls in data.
- UDF: state flows down, events flow up. One ViewModel per screen. Composables never know the ViewModel — route composable injects, screen composable is stateless.
- Expose `StateFlow<UiState>` (`private MutableStateFlow` + `_state.update { it.copy(...) }` — never naked `_state.value =`); one-shot effects via `Channel`/`SharedFlow`, never consumable-boolean state.
- Collect ONLY at route with `collectAsStateWithLifecycle()`; derive with `combine`/`stateIn(WhileSubscribed(5_000))`.
- Sealed `UiState` (Loading/Ready/Error/Empty) + exhaustive `when` as expression — no `else -> Unit`.
- `viewModelScope` only; no `GlobalScope`, no `runBlocking` off-main-never; `CancellationException` always rethrown; Room/Retrofit are main-safe — don't wrap in `withContext(Dispatchers.IO)`.
- Nav args are IDs (`@Serializable` routes), never objects; UseCases only where logic is shared/multi-repo/policy-heavy; no feature→feature deps.

### Compose correctness & performance
- Composable rules: PascalCase, `modifier: Modifier = Modifier` last optional param, state hoisted, `@Preview` for every reusable composable (light/dark/font-scaled).
- Stability: `@Immutable`/`@Stable` on models crossing composable boundaries; `ImmutableList` over `List`; unstable params defeat skipping.
- Lists: `items(list, key = { it.id })` + `contentType`; sort/filter in VM or `remember`, never inside item blocks.
- Read state as late as possible (deferred reads in `graphicsLayer`/draw lambdas); `derivedStateOf` wrapped in `remember`.
- Modifier order: size → padding → background → clip → clickable (wrong order = dead-zone clicks).
- Measure in release + R8 + real device; debug builds lie. Baseline Profile per release (~30% first-launch speedup); `ProfileInstaller` dependency.

### UI quality — the Apple-smooth bar, Material 3 Expressive way
- Motion philosophy: animation communicates, never decorates. If it MOVES → `spring(DampingRatioLowBouncy, StiffnessMediumLow)`. If it fades/changes color → `tween` with M3 easing (`EmphasizedDecel` enter, `EmphasizedAccel` exit). Never `FastOutSlowIn` everywhere; never hardcoded `300` ms — use Duration tokens (short1..extraLong4).
- Choreography: enter-with-ease-out, exit-with-ease-in; stagger 30–40 ms/item capped at 300 ms; enter before exit; coordinate opacity with geometry; bounce only for celebration; nav 300–500 ms, feedback 100–200 ms, nothing >600 ms.
- Pick the smallest animation API: AnimatedVisibility → animate*AsState → rememberTransition → AnimatedContent (`contentKey` by visual shape) → Animatable (gestures) → InfiniteTransition (shimmer/pulse).
- Every screen covers 4 states: loading (skeleton shimmer matching real layout, not spinners), content, error (retry affordance), empty (what to do next). Keep old content during refresh — never wipe screen on reload.
- Theming: 29 M3 color roles with semantic naming; 3-layer tokens (primitive→semantic→component); `MaterialTheme.colorScheme.*` — zero hardcoded colors; dynamic color on 12+; tonal elevation, not `Modifier.shadow()`; 8dp spacing grid; Roboto Flex/Google Sans, never Inter-on-Android.
- Layout: Scaffold content padding consumed; edge-to-edge (`enableEdgeToEdge()` + insets) mandatory on 16; 48dp touch targets; thumb-zone primary actions; bottom nav 3–5 items (no hamburger on phones); adaptive via `WindowSizeClass` → `NavigationSuiteScaffold` auto Bar/Rail/Drawer; predictive back wired.
- Reduced motion: read `ANIMATOR_DURATION_SCALE`/AccessibilityManager and provide instant fallbacks.
- Accessibility = completeness: semantics, contentDescription, TalkBack flow, WCAG AA contrast. An inaccessible screen is unfinished.
- Anti-AI-slop: **slop is the absence of a decision, not a banned color** — every visual choice must be
  explainable for THIS product or it's a default to replace. Zero P0 tells on delivery: no indigo/purple
  gradient, no gradient-clip headlines, no Inter/untouched-Material-theme everywhere, no centered-hero +
  3-identical-cards combo, no emoji as icons, no one-hue candy status boxes. Card test: if removing
  border+shadow+bg+radius costs nothing, it's not a card. Hierarchy from scale+space (one dominant
  element, ≥1.25× steps), one accent not a rainbow, corners nest (inner = outer − gap), one elevation
  scale held, blur only where layering is real. Commit to one named direction (type/palette/layout/motion/
  signature); plan→review-vs-brief→build→critique before shipping; don't replace old slop (purple
  gradients) with new slop (cream + serif + sage). (Full tell catalog + pre-delivery taste check:
  Platform-Guidelines + Design-Taste domains.)
- HIG bar on Android (iOS-26-era guidance, translated to Compose): two-layer discipline — a
  translucent FUNCTIONAL layer (bars/tab bar/floating actions) floats above full-bleed CONTENT;
  content scrolls under bars with a tokenized scrim/blur; brand lives in content + action roles,
  never as custom bar backgrounds. All springs interruptible; any drag follows the finger on
  release (zoom/sheet morph). Hierarchy by size+spacing before weight; ≥14sp interactive text;
  sentence-case headers; label + tooltip on every icon-only control; layout reacts to container
  size, never orientation/idiom (foldables == iOS 27 resizing); destructive = confirm + undo;
  long ops = in-place progress + real cancel; test dark, high-contrast, forced-dark/invert, and
  200% font scaling. (Full rules + SF↔Material icon map + M3/HIG conflict rulings: Platform-Guidelines domain.)

### Data
- Room as single source of truth (offline-first): DAO returns `Flow` for observe / `suspend` for one-shot; export schema + tested migrations; never `fallbackToDestructiveMigration()` in prod.
- DTO ↔ domain ↔ entity mappers at boundaries; domain models annotation-free.
- Paging 3 for big lists; DataStore (never SharedPreferences) for preferences; Coil3 `AsyncImage` sized to display, with `crossfade(true)`; images need `coil-network-okhttp`.

### Security (non-negotiable)
- No secrets in code/BuildConfig/resources — a decompiled APK reveals everything; server-side proxy for privileged keys; `local.properties` + gitignored for build-time.
- Tokens/sensitive: `EncryptedSharedPreferences` (AES256_GCM) / Keystore; HTTPS-only via `network_security_config` (`cleartextTrafficPermitted=false`); cert pinning with backup key + rotation plan.
- Components: `android:exported="false"` by default; explicit intent targets; `PendingIntent.FLAG_IMMUTABLE`; FileProvider never raw paths; WebView JS bridges treated as RCE surface.
- Data hygiene: `allowBackup=false` + `dataExtractionRules`; strip logs (`Log.d` of tokens/PII = leak); `FLAG_SECURE` on payment/password screens.
- High-value apps: Play Integrity verdicts decoded SERVER-side with nonce/`requestHash` binding; client root checks are advisory only.
- Auth: Credential Manager + passkeys over password forms; BiometricPrompt in Activity (not composable), `BIOMETRIC_STRONG`+`CryptoObject` for key ops, never `onAuthenticationFailed` = lockout.

### Play Store legal & policy (rejection/removal causes)
- Data Safety form must match ACTUAL behavior of app + every SDK (top removal reason); privacy policy linked in Console AND in-app; prominent disclosure + affirmative consent BEFORE sensitive permission prompts.
- Account creation ⇒ account deletion required in-app + web URL, and real data deletion (no freeze-only).
- Restricted (SMS, CALL_LOG, QUERY_ALL_PACKAGES, MANAGE_EXTERNAL_STORAGE, Accessibility, background location) need declared justification or removal. Target API up to date. Digital goods = Play Billing, never bypass. Kids apps: no AAID, certified SDKs only. No interstitials that can't be closed ≤15 s, no lockscreen ads.
- GDPR/CCPA: data minimization with classification tiers, export+delete implemented, PII scrubbed from analytics/crash logs.

### Quality gates
- Fakes over mocks; Turbine for Flows; `runTest`; test names as sentences, Given/When/Then; every VM has tests; Compose UI tests via semantics matchers; screenshot tests (Roborazzi) for regression; Detekt + ktlint + Compose-Rules clean; StrictMode in debug; Crashlytics/Sentry with mapping.txt upload; CI builds on every PR (build → lint → unit → instrumentation).
- AAB only, minify + shrinkResources on release, versionCode increments, staged rollout with crash-rate halt.

### Domain index (full detail in corresponding section below / references file)

| # | Domain | Sections |
|---|--------|----------|
| 1 | Architecture & Kotlin Foundations | toolchain baseline, layers, MVI vs MVVM, state/effect modeling, UDF, usecases, modules, DI wiring, coroutines, error handling, Kotlin idioms, delegation, design patterns, banned anti-patterns master lookup, quality gates, checklists |
| 2 | Data, Networking & DI | Room, DataStore, offline-first, Ktor/Retrofit, serialization/mapping, Paging 3, Hilt/Koin, Coil3 |
| 3 | Design System, Motion & Adaptive | tokens, 29 color roles, typography, shapes, motion principles, animation APIs, shared element transitions, states, adaptive layout, edge-to-edge, predictive back, reduced motion, iOS-smooth feel, UI excellence checklist |
| 4 | Navigation (Nav3-first) | type-safe routes, NavDisplay/scenes, back stack persistence, tabs, deep links, results/guards, transitions, DI wiring, Nav2, migration |
| 5 | Compose UI & Performance | stability, recomposition, effects, modifiers, lists, measurement/baseline profiles, R8/strong-skipping |
| 6 | Security, Auth, Biometrics, Permissions | secrets, keystore, crypto, network security, intent security, leakage, Play Integrity, passkeys, biometrics, runtime permissions |
| 7 | Testing & Code Quality | strategy, Turbine/VM, coroutines, fakes, DI in tests, Compose UI, screenshots, MockEngine/Paging, TDD, coverage, Detekt, StrictMode, crash reporting, debugging |
| 8 | Legal, Play Policy, Privacy & Billing | Data Safety, disclosure/consent, account deletion, restricted permissions, ads/families, minimum functionality, Play Billing, GDPR/CCPA, pre-submission checklist, top rejections |
| 9 | Platform, Build, Release & Observability | accessibility, notifications/FCM, WorkManager/FGS, Gradle/convention plugins, R8/app size, signing, CI/CD, Play Console flow, vitals, profiling, i18n, device automation |
| 10 | Platform-Guidelines Compliance (HIG ↔ Material 3) | honest audit scorecard, HIG 5 values in Compose, two-layer discipline, materials/blur policy, iOS-smooth motion, typography/brand/invert rules, SF↔Material icon map, M3-vs-HIG conflict rulings, compliance checklist |
| 11 | Design Taste & Anti-AI-Slop | governing principle (no-decision=the tell), full AI-tell catalog with P0/P1/P2 severities + fixes (color/type/layout/components/craft/spacing/motion/copy/imagery), aesthetic direction chooser, plan→review→build→critique loop, pre-delivery taste check, what-not-to-over-flag calibration |

---

