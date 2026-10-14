# SKILL ROUTING INDEX — read only what the task needs

Line anchors refer to the files in THIS folder (`part-a..d.md`). They are stable
(those files are frozen; regenerate this index if they ever change).
Prefer reading a named `###` range over a whole file. Total corpus: 9 domains,
135 sections, ~200K tokens — never load it all.

## 1. Task → what to read (start here)

| If the task is… | Read these sections |
|---|---|
| **Start any new app/project** | `part-a.md:3` (toolchain baseline), `part-a.md:44` (layers), `part-a.md:498` (modules), `part-d.md:849` (Gradle), then the screens you'll build from part-b/part-c |
| **Any new screen/feature** | `part-a.md:124` (state model) + `part-a.md:206` (VM rules) + `part-b.md:7` (tokens) + `part-c.md:3` (stability) + finish with `part-a.md:943` checklist |
| **It should look/behave premium (motion, theme, polish)** | `part-b.md:515` (motion principles), `part-b.md:610` (animation APIs), `part-b.md:819` (shared element), `part-b.md:911` (loading/empty states), `part-b.md:1221` (iOS-smooth feel), `part-b.md:1246` (UI excellence checklist) |
| **Colors/typography/theme/dynamic color** | `part-b.md:7`, `part-b.md:172` (29 color roles), `part-b.md:413`, `part-b.md:468` (shapes) |
| **Lists scroll bad / big lists** | `part-c.md:386` (lists & scrolling) + `part-a.md:1899` (Paging) + `part-c.md:433` (measure) |
| **UI feels slow / janky / recomposing** | `part-c.md:140` (recomposition), `part-c.md:3` (stability), `part-c.md:433` (baseline profiles), `part-d.md:1190` (profiling/ANRs) |
| **Navigation / deep links / back issues** | `part-b.md:1383` → whole Navigation domain; deep links `part-b.md:1938`; predictive back `part-b.md:2149` + `part-b.md:1184` |
| **Tablet/foldable/large screen** | `part-b.md:979` (adaptive layout), `part-b.md:1108` (edge-to-edge) |
| **Login/auth/passkeys/biometrics** | `part-c.md:1168` (passkeys/Credential Manager), `part-c.md:1184` (biometrics), `part-c.md:1227` (permissions) |
| **Data: DB, prefs, offline, sync** | `part-a.md:998` → whole Data domain (Room 1000, DataStore 1277, offline-first 1457) |
| **API layer / networking / images** | `part-a.md:1636` (networking), `part-a.md:1880` (mapping), `part-a.md:2242` (Coil images) |
| **DI (Hilt/Koin) wiring** | `part-a.md:562` (arch level) + `part-a.md:2091` (modules/scopes) |
| **Coroutines/Flow misuse, leaks** | `part-a.md:583` (structured concurrency) + `part-c.md:247` (effects) |
| **Security review / "is this safe?"** | `part-c.md:722` → whole Security domain; review-greps in §3 below; intent security `part-c.md:974` |
| **Write tests / CI test job** | `part-c.md:1346` → whole Testing domain |
| **Add notifications/FCM** | `part-d.md:606` + permissions `part-c.md:1227` |
| **Background sync / periodic work** | `part-d.md:760` (WorkManager/FGS) + `part-a.md:583` (coroutines) |
| **App store submission prep** | `part-d.md:1` → whole Legal domain, esp. `part-d.md:287` (pre-submission checklist) + `part-d.md:326` (top rejections) + `part-d.md:1304` (release-readiness) |
| **Monetization / subscriptions** | `part-d.md:155` (Play Billing) — digital goods MUST use it, no bypass |
| **Privacy / GDPR / Data Safety** | `part-d.md:6`, `part-d.md:37` (disclosure/consent), `part-d.md:56` (account deletion), `part-d.md:201` (GDPR/CCPA) |
| **Release: signing, R8, size, AAB** | `part-d.md:1009` (size), `part-d.md:1035` (signing), `part-d.md:1126` (Play Console) |
| **CI/CD pipeline** | `part-d.md:1056` (GitHub Actions) + `part-a.md:934` (quality gates) |
| **Accessibility audit** | `part-d.md:344` (a11y) + `part-b.md:1200` (reduced motion) |
| **Localization** | `part-d.md:1245` |
| **AI-driven device QA / adb** | `part-d.md:1263` |
| **"Make it look like an Apple-quality app"** | `part-e.md:34` (HIG 5 values), `part-e.md:53` (8 concrete rules), `part-e.md:181` (Apple patterns in Compose) |
| **Design review vs platform guidelines** | `part-e.md:11` (scorecard), `part-e.md:198` (compliance checklist) |
| **Blur / glass / translucent chrome** | `part-e.md:53` §3.2 (two-layer) + §3.3 (materials policy) |
| **Icon choice (share/settings/search/trash…)** | `part-e.md:142` (SF↔Material map) |
| **M3 vs Apple conflict (elevation/back/bars)** | `part-e.md:169` (conflict rulings table) |
| **Dark mode / high contrast / invert safety** | `part-e.md:133` (§4) + `part-d.md:344` (a11y) |
| **"Don't let it look AI-generated" / taste pass** | `part-f.md:36` (tells catalog), `part-f.md:234` (pre-delivery taste check) |
| **Choose a visual direction for a new product** | `part-f.md:15` (principle), `part-f.md:191` (direction chooser), `part-f.md:217` (plan→critique loop) |
| **Review UI for slop before shipping** | `part-f.md:261` (calibration) then the taste check at `part-f.md:234` |

## 2. Domain map (where everything lives)

| Domain | File | Section anchors |
|---|---|---|
| 1. Architecture & Kotlin | part-a.md | 3 toolchain · 44 layers · 82 MVI/MVVM · 124 state · 206 VM · 311 UDF · 375 effects · 407 usecases · 465 repos · 498 modules · 550 inter-feature · 562 DI · 583 coroutines · 655 errors · 672 idioms · 713 delegation · 741 patterns · 767 clean-code · 811 naming · 840 SOLID · **855 BANNED MASTER** · 898 anti-patterns · 934 detekt · 943 checklists |
| 2. Data/Network/DI | part-a.md | 1000 Room · 1277 DataStore · 1457 offline · 1636 network · 1880 mapping · 1899 paging · 2091 DI · 2242 images · 2317 anti · 2331 checks |
| 3. Design/Motion/Adaptive | part-b.md | 7 tokens · 172 color · 413 type · 468 shapes · 515 motion · 610 anim-APIs · 819 shared-el · 911 states · 979 adaptive · 1108 e2e · 1184 p-back · 1200 reduced · 1221 ios-feel · 1246 excellence · 1304 anti |
| 4. Navigation (Nav3) | part-b.md | 1387 routes · 1463 nav3 · 1609 backstack · 1736 tabs · 1797 scenes · 1938 deeplinks · 2062 results · 2149 transitions · 2310 DI · 2454 nav2 · 2577 migration · 2663 anti · 2724 checks |
| 5. Compose UI & Perf | part-c.md | 3 stability · 140 recomposition · 247 effects · 328 modifiers · 386 lists · 433 measurement · 473 r8 · 508 anti · 673 checks |
| 6. Security/Auth/Bio | part-c.md | 724 secrets · 785 keystore · 854 crypto · 916 network-sec · 974 intent · 1047 leakage · 1113 integrity · 1168 passkeys · 1184 biometrics · 1227 perms · 1256 anti · 1293 checks |
| 7. Testing & Quality | part-c.md | 1348 strategy · 1448 VM/Flow · 1561 coroutines · 1639 fakes · 1683 DI · 1754 compose-ui · 1866 screenshot · 1949 network/paging · 2030 TDD · 2060 coverage · 2088 lint · 2132 strictmode · 2176 crash · 2244 debugging · 2277 checklist · 2319 anti |
| 8. Legal/Play/Privacy/Billing | part-d.md | 6 data-safety · 37 policy/consent · 56 acct-deletion · 67 restricted-perms · 96 target-api · 113 ads/families · 136 min-functionality · 155 billing · 201 GDPR · 218 privacy-design · 257 crash-PII · 287 PRE-SUBMIT · 326 TOP-REJECTIONS |
| 9. Platform/Release/Observ. | part-d.md | 344 a11y · 606 notifications · 760 background · 849 gradle · 1009 obfuscation/size · 1035 signing · 1056 ci-cd · 1126 console · 1161 vitals · 1190 profiling · 1245 i18n · 1263 device-auto · 1291 anti · 1304 RELEASE-READY |
| 10. HIG ↔ Material 3 | part-e.md | 11 SCORECARD · 34 HIG-values · 53 eight-rules (53 §3.2 two-layer · §3.3 materials) · 133 invert · 142 ICON-MAP · 169 CONFLICTS · 181 apple-recipes · 198 COMPLIANCE-CHECKLIST |
| 11. Design Taste & Anti-AI-Slop | part-f.md | 15 PRINCIPLE (no-decision=the-tell) · 36 TELLS-CATALOG (C/T/L/K/S/M/CP/IM, P0→P2) · 191 DIRECTION-CHOOSER · 217 plan→review→build→critique LOOP · 234 TASTE-CHECKLIST · 261 do-NOT-over-flag CALIBRATION |

## 3. Review mode — grep these WRONG patterns in generated code

Audit flow: run greps → fix per `part-a.md:855` (master lookup) → re-run → apply domain checklist.

```
grep -rn "collectAsState()"            -- must be collectAsStateWithLifecycle()
grep -rn "GlobalScope\|runBlocking"    -- banned outside main()/tests/Gradle
grep -rn "_state.value *="             -- must be _state.update { }
grep -rn 'Text("[^"]*")'               -- hardcoded strings → stringResource
grep -rn "Color(0x"                    -- hardcoded colors → colorScheme
grep -rn "androidx.compose.material\."  -- Material 2 import in M3 project
grep -rn "items(.*[^)]{ *$" , "key ="  -- LazyColumn without stable key/contentType
grep -rn "!!"                          -- ban; requireNotNull/smart-cast
grep -rn "kapt"                        -- new code: KSP only
grep -rn "fallbackToDestructive"       -- prod: explicit tested migrations
grep -rn "SharedPreferences("          -- migrate to DataStore (enc: EncryptedSharedPreferences)
grep -rn "allowBackup=\"true\"\|usesCleartextTraffic=\"true\""
grep -rn "PendingIntent"               -- must carry FLAG_IMMUTABLE
grep -rn "tween([0-9]"                 -- spatial motion → spring(); durations → tokens
```

## 4. Phase → always-load trio
When unsure, this subset covers any task: `part-a.md:855` (BANNED MASTER) + the domain's checklist section + `part-d.md:287` (if release-adjacent).

## 5. Single-file portable version
All four parts concatenated: `ULTIMATE-SKILL.md` (735 KB) — same content, but see §intro: too big for one context; use this index to slice.
