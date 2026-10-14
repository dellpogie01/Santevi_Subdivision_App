# Android Production Ultimate — the AI skill that ships Android apps

**A complete, battle-tested agent skill for building production Android apps with AI.**
One always-loaded rules card + a routed 770 KB knowledge corpus covering the entire app
lifecycle — architecture, Compose UI, premium motion, data, security, Play compliance,
testing, performance, release — audited against **both** Google's Material 3 / M3 Expressive
guidance **and** Apple's Human Interface Guidelines (iOS 26 era), and topped with a
design-taste / anti-AI-slop layer from Anthropic's `frontend-design` and the 2026 slop canon.

Works with **Claude Code, Cursor, Codex, opencode, Gemini CLI, Copilot** — anything that
reads the open [Agent Skills](https://agentskills.io) format.

> **Why does this exist?** Every AI coding agent knows *some* Android. None of them know
> *your production bar*. This skill closes that gap: it enforces modern 2026 toolchain
> (Kotlin 2.x / K2, Compose BOM, Nav3, Hilt, Room, R8, baseline profiles), bans the ~50
> anti-patterns LLMs keep regenerating, teaches Apple-grade motion and layout discipline
> in Material 3, refuses the ~70 named AI-design tells (purple gradients, Inter-everywhere,
> 3-card heroes, emoji icons, candy status boxes…), and refuses to let you ship without
> Data Safety, account deletion, accessibility, and a signed AAB.

---

## What's inside

```
SKILL.md                  ← 12 KB always-loaded "Golden Rules" card (auto-triggers on Android work)
references/
  INDEX.md                ← routing table: task → exact file + line-anchored sections to read
  part-a.md   (195 KB)    ← Domains 1–2: Architecture & Kotlin · Data/Network/DI
  part-b.md   (190 KB)    ← Domains 3–4: Design/Motion/Adaptive · Navigation 3
  part-c.md   (230 KB)    ← Domains 5–7: Compose Perf · Security/Auth · Testing
  part-d.md   (110 KB)    ← Domains 8–9: Play Legal/Billing · Release/Observability
  part-e.md   (16 KB)     ← Domain 10: Apple HIG ↔ Material 3 compliance (the dual-platform audit)
  part-f.md   (18 KB)     ← Domain 11: Design taste & anti-AI-slop (tell catalog + taste checklist)
scripts/
  build-single-file.ps1/.sh ← regenerate the portable ULTIMATE-SKILL.md (770 KB, 11 domains)
  install.ps1/.sh           ← one-shot install for your agent of choice
AUDIT.md                  ← how it was built + the honest guideline scorecard
```

**Coverage: 11 domains · 149 sections · ~205K tokens of curated rules, code patterns,
WRONG→RIGHT tables, and checklists** — loaded one routed slice at a time, so a task costs
5–20K tokens of context, not 205K.

## The 11 domains

| # | Domain | Representative content |
|---|--------|------------------------|
| 1 | Architecture & Kotlin | Clean Arch, MVI/MVVM, UDF, atomic `StateFlow`, effects via `Channel`, banned-patterns master lookup |
| 2 | Data, Network, DI | Room+migrations, DataStore, offline-first, Ktor/Retrofit, Paging 3, Hilt/Koin, Coil3 |
| 3 | Design, Motion, Adaptive | M3 Expressive tokens, 29 color roles, spring-first motion, shared-element transitions, edge-to-edge, predictive back |
| 4 | Navigation | Nav3 type-safe routes, back stack persistence, scenes, deep links, auth guards, nav transitions |
| 5 | Compose Performance | stability/`@Immutable`, recomposition elimination, deferred reads, lazy keys/contentType, baseline profiles, R8 |
| 6 | Security & Auth | Keystore, `EncryptedSharedPreferences`, cert pinning, intent hardening, Play Integrity (server decode), passkeys, biometrics |
| 7 | Testing & Quality | Turbine, Compose UI tests, Roborazzi, MockEngine, TDD, Detekt/ktlint/compose-rules, StrictMode |
| 8 | Play, Legal & Billing | Data Safety accuracy, account deletion, restricted permissions, AAID/families, Play Billing, GDPR/CCPA |
| 9 | Platform & Release | accessibility, FCM, WorkManager/FGS, Gradle/AGP9, signing, CI/CD, Play Console, profiling, i18n |
| 10 | **HIG ↔ Material 3** | two-layer chrome/content discipline, materials policy, iOS-smooth motion recipes, SF↔Material icon map, M3-vs-HIG conflict rulings, compliance checklist |
| 11 | **Design Taste & Anti-AI-Slop** | P0/P1/P2 AI-tell catalog (~70 tells: color/type/layout/components/motion/copy) with fixes, direction chooser, plan→review→build→critique loop, pre-delivery taste check, over-flag calibration |

## Install

**Claude Code / anything reading `~/.claude/skills`:**
```bash
git clone https://github.com/AquaticAzelf/android-production-skill ~/.claude/skills/android-production-ultimate
```

**opencode (global):**
```bash
git clone https://github.com/AquaticAzelf/android-production-skill ~/.config/opencode/skills/android-production-ultimate
```

**Cursor (project rules):**
```bash
git clone https://github.com/AquaticAzelf/android-production-skill .cursor/skills/android-production-ultimate
```

Restart your agent (skills load at startup), then just work:

> *"use the android-production-ultimate skill — build a habit-tracker: Kotlin, Compose, M3,
> Home/Streak/Stats/Settings, dark-first, spring motion, Room offline-first + WorkManager reminders"*

The card auto-triggers on Android/Kotlin/Compose tasks, the INDEX routes each request to the
exact sections needed, and the review-mode grep kit audits every generation against the
banned-patterns master table.

## Design philosophy

- **Nothing is summarized away.** Distillation removed repo-admin noise and cross-source
  duplicates only — every rule, snippet, table, and checklist survived. Code blocks were
  preserved where they encode an API contract; prose was compressed into imperatives.
- **Route, don't dump.** The always-loaded entry point is ~3K tokens. The corpus is pulled
  per-domain via line-anchored `references/INDEX.md`. You can feed a 200K-token window model
  a task without spending 200K tokens of context on instructions.
- **Dual-platform audited.** Domain 10 exists because this project was scored against both
  Material guidance and Apple's HIG (iOS 26 / WWDC26), and the deltas were added as
  Compose-translatable rules — see [AUDIT.md](AUDIT.md).

## Sources & gratitude

Distilled from (each MIT/BSD-style licensed; see [LICENSE](LICENSE)):

- [haidrrrry/compose-kotlin-agent-skills](https://github.com/haidrrrry/compose-kotlin-agent-skills) · [Meet-Miyani/compose-skill](https://github.com/Meet-Miyani/compose-skill) · [piyushverma0/android-agent-skills](https://github.com/piyushverma0/android-agent-skills) · [skydoves/compose-performance-skills](https://github.com/skydoves/compose-performance-skills) · [chrisbanes/skills](https://github.com/chrisbanes/skills) · [krshmbb/android-security-skill](https://github.com/krshmbb/android-security-skill) · [Drjacky/claude-android-ninja](https://github.com/Drjacky/claude-android-ninja) · [13krub/android-lead-agent-skills](https://github.com/13krub/android-lead-agent-skills) · [rezaiyan/android-kmp-claude-playbook](https://github.com/rezaiyan/android-kmp-claude-playbook) · [android/skills (official Google)](https://github.com/android/skills) · [devsemih/appstore-review-skill](https://github.com/devsemih/appstore-review-skill) · [AbhijeetBabar-2025/privacy-first-android-skill](https://github.com/AbhijeetBabar-2025/privacy-first-android-skill) · [krutikJain/android-agent-skills](https://github.com/krutikJain/android-agent-skills)

Design-taste layer (Domain 11):

- [anthropics/skills → frontend-design](https://github.com/anthropics/skills/tree/main/skills/frontend-design) — Anthropic's official design skill
- [funboy322/avoid-ai-design](https://github.com/funboy322/avoid-ai-design) — AI-tells catalog with P0/P1/P2 severities + aesthetic directions
- [yetone/kill-ai-slop](https://github.com/yetone/kill-ai-slop) — 35-tell slop taxonomy (the "why it reads machine-made" reasoning)
- [nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) — 67 styles / 161 palettes / 57 font pairings / native pre-delivery checklist

Apple HIG content synthesized from public Apple Developer documentation (HIG 2026 updates,
Liquid Glass adoption guide, WWDC25/26 sessions). Google guidance from developer.android.com.

## Known limits

- Version pins reflect **mid-2026 stable**; verify against release notes before upgrades.
- It cannot replace on-device QA, your brand's design system, or legal counsel for GDPR/CCPA.
- Compose-first. Flutter/RN developers: useful for motion/design/play-policy domains only.

## License

MIT — see [LICENSE](LICENSE). Upstream attribution preserved.
