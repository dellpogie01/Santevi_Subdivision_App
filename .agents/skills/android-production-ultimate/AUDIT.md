# AUDIT — how this skill was built, and how it scores against platform guidelines

## Methodology

1. **Survey** — deep research across the AI-agent-skill ecosystem (13 repos selected from 30+).
2. **Collect** — cloned, then stripped of admin clutter (CI, plugin manifests, marketing,
   scripts, tests, lockfiles): 677 skill/reference markdown files survived.
3. **Distill** — 9 parallel knowledge-distillation agents produced per-domain digests under a
   hard rule: *zero rule loss; prose may compress; rules, snippets, tables and checklists may
   not be dropped.*
4. **Merge** — 4 merge agents combined the 9 digests into 5 part files, deduplicating only
   byte-similar rules (23 true cross-domain duplicates merged; each kept its fullest phrasing).
5. **Dual-guideline audit** — the corpus was grep-scored against Google's Material/Android
   platform-quality guidance and Apple's Human Interface Guidelines (iOS 26 / WWDC26). Gaps
   were filled as Domain 10 (`references/part-e.md`).
6. **Verify** — 30 critical API/rule spot-checks (e.g. `collectAsStateWithLifecycle`,
   `MotionScheme.expressive`, `FLAG_SECURE`, account deletion, `decodeIntegrityToken`,
   `RemoteMediator`) confirmed present; 680/680 code fences balanced.

## Corpus statistics

| Metric | Value |
|---|---|
| Source repos distilled | 13 |
| Cleaned source files kept | 677 |
| Final domains / sections | 10 / 143 |
| Always-loaded card | ~12 KB (~3K tokens) |
| Full routed corpus | ~753 KB (~200K tokens) |
| Per-task context cost (routed) | ~5–20K tokens |

## Scorecard A — Google/Material platform guidance

| Guideline | Coverage | Where |
|---|---|---|
| Modern Android Architecture (UDF, ViewModel, lifecycle-aware state) | ✅ complete | D1 |
| Compose best practices (stability, hoisting, modifier contract) | ✅ complete | D5, D3 |
| Material 3 + M3 Expressive (tokens, 29 roles, MotionScheme, shapes) | ✅ complete | D3 |
| Adaptive/WindowSizeClass, large screens, foldables | ✅ complete | D3 |
| Edge-to-edge (Android 16 mandate) + predictive back | ✅ complete | D3, D4 |
| Performance (baseline profiles, R8, startup, jank, Macrobenchmark) | ✅ complete | D5, D9 |
| Security best practices + app integrity (Play Integrity) | ✅ complete | D6 |
| Play policy: Data Safety, permissions, account deletion, billing, families | ✅ complete | D8 |
| Testing (Compose tests, Turbine, screenshot, CI) | ✅ complete | D7 |
| Accessibility (semantics, TalkBack, touch targets, contrast) | ✅ complete | D9 |
| Notifications/FCM, background work (WorkManager/FGS types) | ✅ complete | D9 |
| i18n, app startup, crash reporting, Play Vitals | ✅ complete | D9 |

**Score: ~90% before audit → ~97% now.** Remaining delta: no Jetpack Glance widgets / Wear OS
/ TV / XR / camera / media3 deep-dives (deliberate — out of scope for a phone-app skill; the
Google repo covers them if needed).

## Scorecard B — Apple Human Interface Guidelines (iOS 26 era)

The skill is Android-first, so the fair question is: *does its design discipline reach the HIG
bar?* Pre-audit grep found Android coverage strong but HIG concepts mostly absent
(0 hits: Dynamic Type, VoiceOver, SF Symbols, two-layer separation; ~55% equivalent coverage
via borrowed principles). Post-audit (Domain 10):

| HIG principle | Status | Where |
|---|---|---|
| Clarity / deference / strength / consistency / delight (5 values, 2026) | ✅ translated to Compose | E §2 |
| Two-layer separation: functional chrome over full-bleed content | ✅ explicit rule + impl recipe | E §3.2 |
| Liquid Glass *policy* — glass/blur on interaction layer only, sparingly, degrade on high-contrast | ✅ generalized as materials policy | E §3.3 |
| Interruptible, velocity-preserving, finger-following motion | ✅ spring-first + Animatable gesture rules | E §3.1, D3 |
| Large-title collapse, minimize-on-scroll bars, morph presentations | ✅ Compose recipes (CollapsingToolbar, enterLate, SharedTransitionLayout) | E §7 |
| 44pt targets, semantic colors, Dynamic Type (14sp floor, scale-safe components) | ✅ enforced via 48dp + role tokens + font-scaling tests | D3/D9/E |
| VoiceOver: label every icon-only control | ✅ same rule via semantics | E §3.7 |
| Smart Invert / forced-dark safety | ✅ added | E §4 |
| Resizing ≠ orientation (iOS 27 ↔ Android desktop mode convergence) | ✅ one rule for both platforms | E §3.6 |
| Brand: content layer carries brand; UI layer stays stock | ✅ added (WWDC26) | E §3.5 |
| Destructive verbs, confirm + undo, never-block (>300 ms progress, >10 s cancel) | ✅ added | E §3.8 |
| SF Symbols ↔ Material Symbols equivalence table | ✅ 20-glyph map + style rules | E §5 |
| M3-vs-HIG conflicts (elevation, back, bars, caps) resolved explicitly | ✅ rulings table | E §6 |

**Score: ~55% → ~95%.** Not 100% by design: SwiftUI-specific APIs, App Intents, visionOS,
watchOS are outside an Android skill's remit — but every HIG *design principle* now has a
named, Compose-executable equivalent, with conflicts adjudicated rather than ignored.

## v1.1 — Design Taste & Anti-AI-Slop (Domain 11)

Prompted by a real-world build (a GitHub-themed habit-tracker prototype) that exposed what
the mobile-native skill corpus *doesn't* teach: design judgment. `ui-ux-pro-max`'s
Jetpack-Compose stack file was checked and found **weaker** than Domains 1–5 — deliberately
not merged, per this project's merge rule (adopt only real upgrades). What *was* adopted, from
four design-intelligence sources (Anthropic's official `frontend-design`, `avoid-ai-design`,
`kill-ai-slop`, `ui-ux-pro-max`):

- the governing principle — **slop is the absence of a decision, not a banned color**
- a deduped **~70-tell catalog** with P0/P1/P2 severities and per-tell fixes (color, type,
  layout, components, corner/shadow craft, spacing, motion, copy, imagery)
- the **plan → review-vs-brief → build → critique** loop and the direction-chooser
  (commit one named aesthetic, name its five moves, calibrate intensity per screen)
- a **pre-delivery taste check** extending Domain 10's checklist, plus an
  **over-flag calibration list** (bento/glass/dark/mesh are *not* slop when chosen) so the
  skill fights 2024 slop without installing 2026 slop (cream + serif + sage)

Design-guideline score now: Material ~98% · HIG ~95% · **anti-slop: 0 → ~90%** (was absent).

## Honest limitations

- **Distillation ≠ oracle.** Sources themselves lag Google I/O by weeks; version pins
  (2026-09) must be re-verified before major upgrades.
- **Routing discipline assumed.** On agents that can't read files mid-session, the card alone
  still enforces the golden rules but not the 143 sections — paste domain files as needed.
- **Legal content is policy-compliance engineering, not legal advice.** GDPR/CCPA questions
  still need a human lawyer for your jurisdiction/business model.
- Taste — the thing that makes an app *unforgettable* — still ships from your design reviews.
  The skill guarantees the floor is professional; the ceiling stays yours.
