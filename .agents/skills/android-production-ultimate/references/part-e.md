## DOMAIN: Platform-Guidelines Compliance — Apple HIG ↔ Material 3, Applied in Compose

This domain makes the skill dual-audited: every design rule in the corpus is checked against
Google's platform guidance (Material 3 / M3 Expressive / HIG-of-Android: edge-to-edge, predictive
back, adaptive) AND Apple's Human Interface Guidelines (iOS 26 "Liquid Glass" era, updated at
WWDC26 for resizable iPhone environments). HIG rules are expressed as **what to do in a Compose
app to reach that bar**, plus **what to do when you actually target iOS** (Compose Multiplatform
or SwiftUI handoff). Where the two guidance systems conflict, the conflict is called out and a
resolution given.

### 1. The audit result (honest scorecard)

| Guideline area | Android/M3 side | Apple HIG side | Verdict |
|---|---|---|---|
| Motion: physics springs, interruption-safe, enter≠exit easing | M3E `MotionScheme`, spring-first | iOS 26: interactive, interruptible transitions; glass morphs | ✅ already covered (Design/Motion domain) |
| Touch targets | 48dp minimum (M3) | 44pt minimum (HIG), 28pt absolute floor with spacing | ✅ covered — note: 48dp > 44pt, M3 value is the safe superset |
| Typography scale | M3 15-role type scale | HIG 13 text styles; SF Pro; weight≠>size for hierarchy | ✅ covered; add: never signal hierarchy with weight alone (§3.4) |
| Color roles / semantic tokens | 29 M3 color roles, tonal surfaces | HIG semantic system colors; ~10 accent roles | ✅ covered |
| Contrast / a11y | WCAG AA, TalkBack semantics | WCAG, VoiceOver, Increase Contrast, Smart Invert | ✅ contrast covered; Smart Invert + Dynamic Type gaps fixed in §4 |
| Safe areas / insets | WindowInsets + edge-to-edge (mandatory A16) | Safe Area guides; never hard-code bar heights | ✅ covered — same discipline, different API |
| Navigation patterns | Bottom nav 3–5, Rail, Drawer by WindowSizeClass | Tab bar (3–5) floats in glass layer, minimizes on scroll, adapts→sidebar on iPad/opts-in iOS 27 | ✅ pattern parity covered; layering gap fixed in §3.2 |
| Loading/empty/error states | 4-state rule, skeletons | Same principles; HIG adds "never block: progress + cancel path" | ✅ covered |
| Reduced motion / transparency | `ANIMATOR_DURATION_SCALE` check | Reduce Motion + Reduce Transparency settings; Liquid Glass degrades gracefully | ✅ reduced-motion covered; glass degradation = §3.3 |
| Two-layer separation (chrome vs content) | Implicit via M3 surfaces | iOS 26 EXPLICIT: functional UI layer above content layer | ⚠️ was implicit — now made explicit §3.2 |
| Materials strategy (blur/translucency) | Tonal surfaces, shadows restricted | Liquid Glass = interaction layer ONLY, sparingly | ⚠️ was absent — §3.3 |
| Brand placement | M3 dynamic color encourages personalization | WWDC26: brand belongs in CONTENT layer, UI layer stays stock | ⚠️ was absent — §3.5 |
| Iconography | Material Symbols variable axes | SF Symbols 7,000+; label required on every icon-only control | ✅ Material side covered; cross-platform mapping §5 |
| Resizing/foldables parity | WindowSizeClass, foldables | iOS 27: iPhone apps resizable (Mirroring/iPad) — idiom/orientation checks BANNED, use size classes | ✅ Android side covered; HIG rule adopted §3.6 |
| Dark mode | Dynamic dark schemes | Appearance-aware colors; test both + elevated variants | ✅ covered |

Score before this module: Android guidance ~90%, HIG ~55%. Everything below closes the HIG
gaps with Compose-translatable rules.

### 2. HIG's 5 design values (2026 reintroduction), translated
1. **Clarity** — text legible at all sizes, icons precise, purpose over ornament. → Compose: body
   text ≥14sp, never place text over unprepared imagery (use a scrim layer), icon-only buttons get
   `contentDescription` + `semantics { role = Role.Button }`.
2. **Deference** — UI serves content; fluidity and feedback come from content, chrome recedes.
   → Compose: transparent `TopAppBar` over scrolling content + scroll-behavior `enterAlways`;
   tab bar hides on scroll-down (`enterLate()`), reappears on scroll-up. This IS the iOS-26
   "tab bar minimizes" pattern, done with Material primitives.
3. **Strength/Hierarchy** — type, spacing, and layers (not color) carry structure.
   → Compose: fixed `typography` roles per level; 8dp-grid spacing tokens; `surfaceContainer`
   tonal ladder for grouping depth; reserve `primary` for actions only.
4. **Consistency** — patterns and timing repeat; the app feels like one system.
   → Compose: the Duration/Easing token rule (short1–extraLong4) applied everywhere; one
   `AppButton`/`AppCard` component set; motion spec chosen from the table in §3.1 every time.
5. **Feedback & delight** — respond to every input; celebration where earned.
   → Compose: `Modifier.hoverable`/`pressIndication` on custom controls; haptics via
   `LocalHapticFeedback` on success (`LongPress`/confirm), spring overshoot ONLY for celebration
   (the "bounce = immature on nav" rule already in Motion domain).

### 3. The eight concrete rules HIG adds to this skill

**3.1 Spring physics — the iOS feel, in Compose (already core, now named)**
iOS's signature is *interruptible* springs that inherit velocity. Rules:
- Navigation push/pop: `slideInHorizontally(tween(400, LinearOutSlowInEasing))` is the classic
  iOS curve; M3E equivalent uses `spring(StiffnessMediumLow, DampingRatioNoBouncy)` — prefer the
  spring (interruptible, matches iOS 26's "fluid slide" direction).
- Sheet/modal present: spring with overshoot 1.15 (`keyframes` allowed only for branded
  presentations); dismiss must follow the finger (gesture-driven `Animatable`, never
  fixed-duration on drag release).
- Zoom/scale presentations (Apple's "morph" language): shared-element `SharedTransitionLayout`
  from tap-target bounds to detail bounds — already in Shared Element Transitions section; HIG
  makes this the PREFERRED present style for list→detail.

**3.2 Two-layer discipline (iOS 26's biggest structural idea)**
Every screen has a CONTENT layer (your product, full-bleed, scrollable) and a FUNCTIONAL layer
(bars, tab bar, floating actions — translucent, above content). Compose implementation:
- Content draws edge-to-edge UNDER bars: `Scaffold { innerPadding -> LazyColumn(contentPadding =
  innerPadding + extra for transparency readability) }` with `containerColor = Color.Transparent`
  top bars on media-style screens.
- Functional layer gets legibility backing: as content scrolls beneath bars, apply scrim/blur
  (`Modifier.blur` on a `BackdropModifier` (haze) or tonal `surface.copy(alpha=.85f)`), and use
  `Modifier.windowInsetsPadding` — this is Compose's answer to the "scroll edge effect".
- Never mix layers: controls never float in the content region without a glass/scrim layer
  behind them; content never draws over the functional layer.
- Bar item grouping: cluster bar actions in `Row`s sharing one container (HIG groups bar buttons
  into visual glass islands; Material does the same with `AssistChip`-like containers or a
  rounded `surfaceContainerHigh` pill around action groups).

**3.3 Materials policy — when blur/translucency is allowed**
- Blur/translucency is for the FUNCTIONAL layer only (bars, sheets, floating pills) — not for
  content surfaces. Content cards use tonal surfaces (M3) not glass.
- One blur strength system-wide (`blur(20.dp–30.dp)` + scrim), defined once as a token
  `AppMaterials.thick/thin`, chosen by content legibility, not decoration.
- Respect "Reduce Transparency": on Android there's no system toggle — implement
  `highContrastOn = AccessibilityManager.isAccessibilityEnabled || force opaque setting` and swap
  glass for solid `surface` colors (mirror iOS behavior; it also satisfies Increase Contrast).
- Performance: `Modifier.blur` on large layers is expensive — apply to a cached `graphicsLayer`
  or use `RenderEffect` only where hardware supports it; fall back to scrim-only (this keeps
  60fps, which HIG's "fluidity" actually depends on).

**3.4 Typography beyond M3 (HIG corrections)**
- Hierarchy by SIZE + SPACING before weight; weight alone fails at small sizes and under Smart
  Invert. M3 already gives 15 roles — use them strictly: section title = `titleLarge`, body =
  `bodyLarge`/`bodyMedium`, never invent ad-hoc `fontSize`.
- Line spacing: HIG tightened list padding in iOS 26 (more breathing room) → LazyColumn row
  vertical padding ≥ `Spacing.md (16)` for list rows, `xl (24)` for grouped sections; increased
  corner radius on cards `shapes.large`.
- Title-style capitalization for section headers (HIG 26 convention; ALL-CAPS headers are going
  away) — same on Android: prefer sentence-case; `TextAllCaps` only for buttons per M3.
- Never less than 14sp for interactive text (HIG's minimum body ~13pt + tap-label legibility).

**3.5 Brand placement (WWDC26 "Communicate your brand")**
- UI layer = stock platform components (familiarity = trust). Brand lives in: content surfaces,
  illustrations, type personality in headers, motion signature (your curve library = brand),
  photography. In Compose: brand accent appears in `primary` action roles + content hero areas
  only; don't tint tab bars/top bars with custom backgrounds (breaks the glass/two-layer rule and
  M3 tonal elevation).
- Test brand colors in dark appearance AND with `onSurface` contrast ≥4.5:1; every brand color
  gets a light/dark variant (M3 roles give you this; don't bypass them).

**3.6 Resize-adaptive, not orientation-adaptive (iOS 27 / foldable convergence)**
- HIG/iOS 27 bans layout decisions from device idiom & orientation; use SIZE (size classes /
  `WindowSizeClass`) + surrounding container size. This is the SAME rule Android already teaches
  (foldables/tablets/desktop mode). Compose: derive everything from
  `currentWindowAdaptiveInfo()` or `BoxWithConstraints` container width, never
  `Configuration.orientation`.
- Android 16+ desktop windowing makes this a shipping requirement, not future-proofing.

**3.7 Icon-only controls: label required**
- Every icon-only control needs `contentDescription` + tooltip (`TooltipBox`) on long-press
  (HIG: "provide an accessibility label for every icon"). VoiceOver and TalkBack both enforced
  from the same Compose semantics call.

**3.8 Never block: progress + cancel**
- Any operation >300ms shows in-place progress (skeleton/inline indicator, not modal spinner);
  any operation >10s shows progress + a working cancel path that actually cancels the coroutine
  (`viewModelScope` job cancellation wired to UI). Destructive ops: confirm sheet + undo snackbar
  (HIG destructive-verb rule + Android undo pattern).

### 4. Smart Invert & color-inversion readiness (HIG a11y, missing on Android too)
- Use semantic color roles everywhere (already rule) — hard-coded hexes break Smart Invert
  (`isInvertColorSchemeEnabled` on iOS; Android equivalent: high-contrast settings).
- Mark images that shouldn't invert (`Modifier.semantics { }` content groups; on Compose:
  provide `HighContrastColorScheme` variants when needed).
- Brand-heavy full-bleed art: provide `forcedDark` testing pass (Android `Forced Dark` dev
  setting) — check logos/photos don't auto-invert into garbage; exclude via
  `android:forceDarkAllowed=false` + own dark asset.

### 5. SF Symbols ↔ Material Symbols mapping table (icon equivalence)
| Concept | SF Symbol | Material Symbol (filled/outlined) |
|---|---|---|
| back / up | `chevron.left` | `ArrowBack` / `arrow_back` |
| close/dismiss | `xmark` | `Close` / `close` |
| search | `magnifyingglass` | `Search` / `search` |
| settings | `gearshape` | `Settings` / `settings` |
| share (system) | `square.and.arrow.up` | `Share` / `share` |
| add | `plus` | `Add` / `add` |
| more (h) | `ellipsis` | `MoreHoriz` / `more_horiz` |
| more (v) | `ellipsis.vertical` | `MoreVert` / `more_vert` |
| favorites | `heart` / `star` | `Favorite` / `Star` |
| delete | `trash` | `Delete` / `delete` |
| check/success | `checkmark` | `Check` / `check` |
| info | `info.circle` | `Info` / `info` |
| home | `house` | `Home` / `home` |
| person | `person` / `person.crop.circle` | `Person` / `account_circle` |
| filter | `line.3.horizontal.decrease` | `FilterList` / `filter_list` |
| refresh | `arrow.clockwise` | `Refresh` / `refresh` |
| camera | `camera` | `PhotoCamera` / `photo_camera` |
| bell/notifications | `bell` | `Notifications` / `notifications` |
| edit | `pencil` | `Edit` / `edit` |
| calendar | `calendar` | `CalendarMonth` / `calendar_month` |
Rules (both platforms): one style per surface (all outlined OR all filled, never mixed);
stroke weight matches adjacent text weight; standard meaning — never repurpose the
share/gear/trash glyphs for custom actions; size at 24dp content size (M3) ≈ 22pt (HIG).

### 6. Conflict resolutions (when M3 and HIG disagree)
| Topic | HIG says | M3/Android says | This skill's ruling |
|---|---|---|---|
| Elevation | Floating glass layer + real blur | Tonal elevation (color shift), shadows discouraged in M3E | Android ships tonal; use blur ONLY for the functional layer on media screens (§3.3) |
| Back | Swipe-from-edge + chevron in title area | System predictive-back + `NavigationBar` icon | Always implement BOTH on Android: predictive back API + `AutoMirrored` arrow icon; swipe handled by system — never a custom back stack |
| Bottom bar height | ~49pt + safe-area inset | 80dp M3 NavigationBar (larger touch) | Respect M3 80dp on Android; never shrink to iOS metrics |
| Section headers | Title-case now | M3 caps via `titleSmall` label style | Sentence-case content headers; caps only inside component-internal labels |
| Tab count | 3–5 (max 5, no "more" overload) | 3–5 identical | No conflict — enforced |
| Search | Dedicated search pattern, trailing placement on larger surfaces | `Search`/`ExpandingSearchBar` | Use M3 SearchBar; on adaptive layouts place in top bar trailing (§part-b Adaptive domain) |
| Modal sheets | Half-sheet with peek + inset, drag handle | `ModalBottomSheet` | Both already aligned: drag handle mandatory, peek 16–25% content visible |
| Toast/snackbar | Transient ≤6s, no undo overload | Snackbar w/ action, 4–10s | Keep 4s default, 10s only with action (§M3 motion durations) |

### 7. Apple-adjacent patterns implemented in Compose (quick recipes)
- **Large-title collapse** (HIG nav pattern): `CollapsingToolbar` (M3 1.4+) or `TopAppBar` +
  `enterAlwaysCollapsed()`; title interpolates 28sp→17sp with `lerp` on `nestedScrollConnection`
  offset — never cross-fade two Texts with different layout IDs (jank).
- **Search-as-you-type sheet**: M3 `SearchBar` expanded to full-screen via `AnimatedContent`;
  recent-queries list under header — mirrors UISearchController pattern.
- **Swipe-to-dismiss detail→list**: predictive back + `Modifier.swipeable`; content scales 0.95
  behind (HIG "stack behind" presentation) via `graphicsLayer { scaleX = lerp(.95f, 1f, fraction) }`.
- **Pull-to-refresh sparkle**: M3 `PullToRefresh` with `EmphasizedDecel` + overshoot 1.1 spring
  (already in Loading States section) — matches UIRefreshControl's bounce.
- **Segmented control**: `SingleChoiceSegmentedButtonRow` (M3) == `UISegmentedControl`; ≤5
  segments, equal widths, verb-free labels.
- **Context menus (long-press)**: `DropdownMenu` on `combinedClickable(onLongClick)` — HIG's
  UIMenu equivalent; actions first, destructive red, max 6 items.
- **Settings screens**: grouped `ListItem` rows, 16/24 padding, section title-case headers,
  switches trailing — the iOS Settings pattern transfers 1:1 and Android users expect it too.

### 8. Guidelines-compliance checklist (run before "it looks good")
- [ ] Functional layer separated from content (§3.2) on every screen
- [ ] All springs interruptible; any drag follows the finger on release
- [ ] Bars: no custom backgrounds fighting the tonal/glass system
- [ ] Blur (if any) only on functional layer, tokenized, degraded for high-contrast
- [ ] Type hierarchy from scale tokens; ≥14sp interactive text; sentence-case headers
- [ ] Every icon-only control: label + tooltip
- [ ] Touch targets ≥48dp; grouped actions have 8dp+ separation
- [ ] Contrast ≥4.5:1 (text) / ≥3:1 (large) in BOTH dark & light; high-contrast pass
- [ ] Smart-invert/forced-dark pass (logos/art not mangled)
- [ ] Layout reacts to container size, not orientation/idiom (§3.6)
- [ ] Brand only in content + action roles, not chrome backgrounds (§3.5)
- [ ] Long ops: in-place progress + real cancel; destructive: confirm + undo
- [ ] Reduced-motion & font-scaling (up to 200%) tested on every custom component
- [ ] SF/Material icon mapping used for system-meaning glyphs (share, gear, trash, close)
- [ ] Tab bar: 3–5 items; hides on scroll-down on media surfaces
