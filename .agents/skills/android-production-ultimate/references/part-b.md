## DOMAIN: Design System, Motion & Adaptive Layout

Goal: smooth, premium, Apple-grade-feeling UI on Android with Kotlin + Jetpack Compose + Material 3 / Material 3 Expressive. Every color, spacing value, radius, duration and easing must come from the token system. Every motion must communicate something. Every screen must work at 600dp and 840dp width.

---

### Tokens

**Three-layer token architecture (never collapse layers)**

```
Layer 1: Primitive Tokens   — raw values (hex, dp, ms). internal; nothing outside the theme module touches them.
Layer 2: Semantic Tokens    — role-named mappings (what the value means). Used by component implementations.
Layer 3: Component Tokens   — per-component overrides, kept PRIVATE in the component file, not global.
```

"Collapsing these layers — writing hex codes directly in composables, or mapping primitive tokens straight to components — is the root cause of weak, inflexible theming."

Layer 1 primitives are named by **hue + tone**, not purpose: `Indigo40` (primary in light), `Indigo80` (primary in dark), `Grey99`, `NeutralVariant90`, `Red40`. Tone scale: `10 = darkest … 99 = lightest`.

```kotlin
// Layer 1
internal object ColorPrimitives { val Indigo40 = Color(0xFF3D00C7); val Grey99 = Color(0xFFFFFBFE) }
internal object PrimitiveTokens {
    val Space4 = 4.dp; val Space8 = 8.dp; val Space12 = 12.dp; val Space16 = 16.dp
    val Space20 = 20.dp; val Space24 = 24.dp; val Space32 = 32.dp; val Space40 = 40.dp
    val Space48 = 48.dp; val Space64 = 64.dp
    val Radius4 = 4.dp; val Radius8 = 8.dp; val Radius12 = 12.dp
    val Radius16 = 16.dp; val Radius24 = 24.dp; val Radius28 = 28.dp
}
// Layer 2
object AppSpacing {
    val XS  = PrimitiveTokens.Space4   // 4dp  — icon padding, dense chip insets
    val S   = PrimitiveTokens.Space8   // 8dp  — related items, list item gaps
    val M   = PrimitiveTokens.Space16  // 16dp — screen horizontal padding
    val L   = PrimitiveTokens.Space24  // 24dp — section gaps
    val XL  = PrimitiveTokens.Space32  // 32dp — major section separators
    val XXL = PrimitiveTokens.Space48  // 48dp — hero padding
}
object AppRadius {
    val ExtraSmall = PrimitiveTokens.Radius4   // chip, badge
    val Small      = PrimitiveTokens.Radius8   // button, text field
    val Medium     = PrimitiveTokens.Radius12  // card, dialog
    val Large      = PrimitiveTokens.Radius16  // bottom sheet, expanded card
    val ExtraLarge = PrimitiveTokens.Radius28  // large surface, navigation drawer
    val Full       = 50.dp                     // FAB, avatar, pill
}
// Layer 3 — inside the component file, not exported
private object CardTokens {
    val HorizontalPadding = AppSpacing.M
    val VerticalPadding = AppSpacing.S
    val Elevation = 1.dp
    val ImageAspectRatio = 16f / 9f
}
```

**Spacing / grid: 8dp grid (4dp only for fine tuning). All spacing must be a multiple of 4dp — "deviation is a bug, not a style choice."**

| Token | Value | Typical use |
|---|---|---|
| xs | 4 dp | Icon padding, badge padding, icon-to-label gap, tight gaps |
| sm | 8 dp | Inline spacing, icon↔label, list item gaps, dense lists |
| md | 16 dp | Default screen horizontal padding + card padding (most common) |
| lg | 24 dp | Section separation, dialog padding, FAB bottom padding |
| xl | 32 dp | Large gaps between groups |
| xxl | 48 dp | Screen edge margins on compact width, hero padding |
| xxxl | 64 dp | Onboarding, splash (extended scale) |

Variant scale used by the design-tokens skill (4dp grid, `md = 12dp` for list item vertical padding, `lg = 16dp`, `xl = 24dp`, `xxl = 32dp`, `xxxl = 48dp`). Pick one, apply it globally; the 8dp-grid form above is the canonical one.

Standard rules: screen horizontal padding `16dp` always; card internal padding `16dp`; between sections `24dp`; icon↔text `8dp`; dialog padding `24dp`; list item vertical padding `8dp` top + bottom; related items gap `8dp`; FAB bottom padding `24dp` + nav bar inset.

```kotlin
// WRONG — arbitrary, off-grid, inconsistent between screens
Modifier.padding(13.dp)   // also 17.dp, 22.dp, 7.dp
// Screen A uses 16.dp edge padding, screen B uses 20.dp
```

Category density: Fintech/Enterprise → dense (8–12dp vertical, maximize information) · Healthtech/Proptech → generous (16–24dp, calm) · Social feeds → compact (8–12dp between cards) · Edtech → readable (16–24dp horizontal for text).

**Elevation tokens (also expressed as tonal elevation, see Color)**

```kotlin
object Elevation {
    val none = 0.dp   // flat — standard cards
    val level1 = 1.dp; val level2 = 3.dp   // level2: FAB, raised cards
    val level3 = 6.dp // dialogs (some sources: drawers)
    val level4 = 8.dp // navigation drawers
    val level5 = 12.dp // tooltips / modal sheets
}
val LocalElevation = staticCompositionLocalOf { AppElevation() }
data class AppElevation(val none: Dp = 0.dp, val subtle: Dp = 1.dp, val card: Dp = 2.dp, val modal: Dp = 8.dp)
```

**Motion duration tokens (M3 Expressive — replaces ALL hardcoded ms)**

```kotlin
object Duration {
    const val short1 = 50    // micro: icon swap
    const val short2 = 100   // fast: color shift
    const val short3 = 150   // quick: appear short distance
    const val short4 = 200   // standard fade / exits
    const val medium1 = 250  // expand/collapse
    const val medium2 = 300  // default transition
    const val medium3 = 350  // complex motion
    const val medium4 = 400  // enter from edge
    const val long1 = 450    // large surface
    const val long2 = 500    // full-screen takeover
    const val extraLong1 = 700 // emphasis / delight
}
```

Duration bands: Micro 50–100ms (ripples, toggles, hover) · Short 100–200ms (fades, simple transitions) · Medium 200–300ms (expand/collapse, bottom sheet) · Long 300–500ms (larger choreography, complex screen changes). Keep most UI transitions under ~400ms unless showing loading/long-form motion. Budgets: navigation 300–500ms; UI feedback (button press) 100–200ms; loading completion 400–600ms; never exceed 600ms for a single non-illustrative motion.

**Easing tokens**

```kotlin
object AppEasing {
    val Standard        = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)  // new content entering
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)  // content leaving
    val Linear          = LinearEasing
}
```

Easing roles: `Standard` default enter/exit · `Emphasized` prominent transitions · `Decelerate` (FastOutSlowInEasing) entering · `Accelerate` (FastOutLinearInEasing) leaving permanently · `Sharp` temporary exit and return.

**Motion token facade — always use it, never `tween(300)`**

```kotlin
object MotionTokens {
    // Spatial — elements that move or scale
    fun spatialEnter() = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    fun spatialExit()  = tween<Float>(Duration.short4, easing = AppEasing.EmphasizedAccel)
    // Effects — color, opacity, elevation
    fun effectsStandard(duration: Int = Duration.medium2) = tween<Float>(duration, easing = AppEasing.Standard)
    fun effectsDecel(duration: Int = Duration.medium4)    = tween<Float>(duration, easing = AppEasing.EmphasizedDecel)
    // Container — screen-level transitions
    fun containerEnter() = tween<IntOffset>(Duration.medium4, easing = AppEasing.EmphasizedDecel)
    fun containerExit()  = tween<IntOffset>(Duration.short4,  easing = AppEasing.EmphasizedAccel)
}
val alpha by animateFloatAsState(targetValue = if (isVisible) 1f else 0f,
    animationSpec = MotionTokens.effectsStandard(), label = "alpha")
```

**Dimension / size tokens beyond the grid**

```kotlin
@Immutable data class AppDimensions(
    val cardPadding: Dp = 16.dp, val screenHorizontal: Dp = 16.dp, val sectionGap: Dp = 24.dp,
    val iconSizeMedium: Dp = 24.dp, val iconSizeLarge: Dp = 32.dp,
    val avatarSizeSmall: Dp = 32.dp, val avatarSizeMedium: Dp = 48.dp,
    val minTouchTarget: Dp = 48.dp, val bottomBarHeight: Dp = 80.dp,
)
val LocalAppDimensions = staticCompositionLocalOf { AppDimensions() }
```

Component dimensions (M3): standard button 40dp height / min width 64dp (touch target still ≥48dp) · FAB 56×56dp (mini 40dp) · text field 56dp tall, min width ~280dp · top app bar 64dp · bottom navigation 80dp · navigation rail 80dp width.

**Extension properties for single values that belong to an existing Material system (no extra CompositionLocal)**

```kotlin
val ColorScheme.snackbarAction: Color @Composable get() = if (isSystemInDarkTheme()) Coral80 else Coral40
val Typography.overline: TextStyle get() = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp)
val Shapes.pill: Shape get() = RoundedCornerShape(percent = 50)
```

**Naming**: semantic names, not visual descriptions (`primary` not `lightBlue`). Avoid Android-reserved / generic resource names: `background`, `foreground`, `transparent`, `white`, `black`, `icon`, `logo`, `image`, `drawable`, `view`, `text`, `button`, `layout`, `container`, bare `id`/`name`/`type`/`style`/`theme`/`color`, `app`/`android`/`content`/`data`/`action`.

---

### Color & Dynamic Color

M3 uses **HCT (Hue/Chroma/Tone)** color science — perceptually uniform, WCAG contrast by construction. Generate schemes at [Material Theme Builder](https://m3.material.io/theme-builder) (`#/custom`): set brand primary → optionally secondary/tertiary keys → **Export → Jetpack Compose** → complete `Color.kt` with all roles for light and dark. The tool guarantees AA contrast between every role and its `on-` counterpart; hand-picked palettes almost never do.

**29 color roles (some tooling/export lists 30 role values — same set plus the surface container variants).** Define every role in BOTH light and dark schemes; leaving any at the Material baseline is the #1 theming failure ("correct, but anonymous").

| Group | Roles | Use |
|---|---|---|
| Primary | `primary` | Filled buttons, active state indicators, FABs, active nav icons |
| | `onPrimary` | Text/icons directly on `primary` |
| | `primaryContainer` | Tinted backgrounds: selected chips, highlighted cards, active tab bg |
| | `onPrimaryContainer` | Text/icons on `primaryContainer` |
| | `inversePrimary` | Primary-equivalent on `inverseSurface` (snackbar action text/links) |
| Secondary | `secondary` / `onSecondary` | Secondary buttons, filter chips, less prominent interactives |
| | `secondaryContainer` / `onSecondaryContainer` | Tag backgrounds, secondary selection states, grouped content areas |
| Tertiary | `tertiary` / `onTertiary` | Accent contrasting with primary AND secondary — use sparingly |
| | `tertiaryContainer` / `onTertiaryContainer` | Calendar event backgrounds, mood indicators, illustrative tinted areas |
| Error | `error` / `onError` | Error text, destructive buttons, validation underlines |
| | `errorContainer` / `onErrorContainer` | Error banners, error chip fills |
| Surface | `surface` / `onSurface` | Default card & sheet backgrounds; primary text/icons |
| | `surfaceVariant` / `onSurfaceVariant` | Alt card bg, input fills; secondary text, captions, metadata, inactive icons |
| | `surfaceTint` | The primary tint applied to elevated surfaces (auto via `Surface(tonalElevation)`) |
| | `background` / `onBackground` | App-level screen background; in most apps == `surface` |
| | `surfaceContainerLowest` / `surfaceContainerLow` / `surfaceContainer` / `surfaceContainerHigh` / `surfaceContainerHighest` | Layered depth without shadows |
| Inverse | `inverseSurface` / `inverseOnSurface` | Tooltip & snackbar containers / their text |
| Outline | `outline` | Text field borders, card outlines, section dividers |
| | `outlineVariant` | Lighter dividers, decorative borders, subtle separators |
| | `scrim` | Overlay behind modal dialogs and bottom sheets |

**Pairing law**: each `on*` role is designed for exactly one background — its base. "Content on `primaryContainer` uses `onPrimaryContainer`, never `onSurface`." Never cross-mix (`tertiaryContainer` bg with `primaryContainer` text). `error` is for text/icon fills, `errorContainer` for backgrounds — never `onError` text on `errorContainer`, contrast fails. M3 tonal palettes guarantee 3:1+ contrast when paired correctly; AA = 4.5:1 body text, 3:1 large text/UI.

| Container | Content on it |
|---|---|
| `primary` | `onPrimary` |
| `primaryContainer` | `onPrimaryContainer` |
| `secondary` | `onSecondary` |
| `secondaryContainer` | `onSecondaryContainer` |
| `tertiary` | `onTertiary` |
| `tertiaryContainer` | `onTertiaryContainer` |
| `surface` | `onSurface` (+ `onSurfaceVariant` for secondary) |
| `surfaceVariant` | `onSurfaceVariant` |
| `error` | `onError` |
| `errorContainer` | `onErrorContainer` |

**Component → role mapping**

| Component | Container | Content |
|---|---|---|
| Primary Button | `primary` | `onPrimary` |
| Tonal Button | `secondaryContainer` | `onSecondaryContainer` |
| Outlined Button | transparent | `primary` |
| Text Button | transparent | `primary` |
| Card | `surfaceContainerLow` | `onSurface` |
| Dialog | `surfaceContainerHighest` | `onSurface` |
| Bottom Sheet | `surfaceContainerLow` | `onSurface` |
| Navigation Bar | `surfaceContainer` | `onSurfaceVariant` inactive / `onSecondaryContainer` active |
| Top App Bar | `surface` → `surfaceContainerHighest` when scrolled | `onSurface` |
| FAB | `primaryContainer` | `onPrimaryContainer` |
| Input Field | `surfaceVariant` | `onSurfaceVariant` label, `onSurface` text |
| Snackbar | `inverseSurface` | `inverseOnSurface` |
| Chip unselected | transparent | `onSurfaceVariant` |
| Chip selected | `secondaryContainer` | `onSecondaryContainer` |
| Badge | `error` | `onError` |

**Surface container hierarchy (tonal elevation without shadows)**

```
Background (0dp) — page itself
surfaceContainerLowest — barely distinguishable from background (cards on white bg)
surfaceContainerLow    — cards on background, list items, bottom sheet
surfaceContainer       — standard card, default modal surface
surfaceContainerHigh   — card on card, elevated panels, selected/active state
surfaceContainerHighest — dialogs, menus, top-most modal header
```

**Tonal elevation — depth without shadows.** M3 does not use shadow elevation on surfaces: the higher the elevation, the more `surfaceTint` (== `primary`) bleeds into the surface. Cards feeling slightly purple/tinted in dark mode is correct behavior. "Do not replicate this manually with `color.copy(alpha = X)` — the `tonalElevation` parameter on `Surface`/`Card` handles it automatically."

| tonalElevation | Primary tint % | Use |
|---|---|---|
| 0dp | 0% | App background, non-elevated surfaces |
| 1dp | 5% | Cards, list items |
| 3dp | 8% | FAB, elevated chips, nav bar, sheets |
| 6dp | 11% | Navigation drawers |
| 8dp | 12% | Menus, dialogs |
| 12dp | 14% | Modal bottom sheets |

Dark mode: higher elevation = lighter surface (automatic with `tonalElevation`).

```kotlin
Surface(tonalElevation = 3.dp) { /* M3-correct elevation */ }
Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp))
Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
```

**Semantic color hierarchy** (fixes "everything fights for attention"): Button `primary` (highest) → selected Chip `primaryContainer`/`secondaryContainer` (medium) → Card `surfaceVariant`/`surfaceContainer` (lowest). Never use `primary` for backgrounds unless it is a FAB or filled button.

**Theme assembly — dynamic color + version check + fallbacks + system bar icons**

```kotlin
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Priority 1: Material You wallpaper palette (API 31+ = Build.VERSION_CODES.S)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AppDarkColorScheme   // brand dark
        else      -> AppLightColorScheme  // brand light
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(
        LocalAppGradientColors provides (if (darkTheme) DarkGradientColors else LightGradientColors),
        LocalAppDimensions provides AppDimensions(),
        LocalAppExtendedColors provides (if (darkTheme) DarkExtendedColors else LightExtendedColors),
        LocalAppElevation provides AppElevation(),
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
// Theme files: Color.kt · Type.kt · Shape.kt · Theme.kt
```

Dynamic color rules: **always** guard with `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`; **always** provide a real, good-looking static fallback (not a placeholder, not bare `lightColorScheme()` = default purple with no brand). Dynamic color is Android-only; CMP projects fall back to brand schemes on non-Android targets.

Category guidance — DISABLE dynamic color (brand = trust): fintech (HDFC, Paytm), enterprise, banking, brand-critical utilities (pass `dynamicColor = false` and document the reason). ENABLE (personalization = comfort): edtech, healthtech, social, content-focused apps (news, productivity). OPTIONAL: ecommerce, depending on brand strength. Test with various wallpapers — light, dark, colorful, monochrome — on real devices, not just the emulator toggle.

**Dark mode = shift, don't invert.** Every semantic role needs an independently chosen dark variant; dark primary is typically the `80` tone, not the inverted `40`.
- Background `#FAFAFA` → `#1C1B1F` (dark grey, NOT black)
- Primary dark green `#006C51` → light green `#68DBA8`
- Text `#212121` → `#E6E1E5` (off-white, not pure white)
- Status/nav bar icons: light icons when `darkTheme = true`

**User preference + persistence**

```kotlin
enum class ThemePreference { LIGHT, DARK, SYSTEM }
data class ThemeConfig(val themePreference: ThemePreference = ThemePreference.SYSTEM, val useDynamicColor: Boolean = true)
val isDarkTheme = when (themeConfig.themePreference) {
    ThemePreference.LIGHT -> false; ThemePreference.DARK -> true
    ThemePreference.SYSTEM -> isSystemInDarkTheme()
}
val useDynamicColor = themeConfig.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
```
Persist theme preferences with **DataStore** (`stringPreferencesKey("theme_preference")`, `booleanPreferencesKey("use_dynamic_color")`) behind a `ThemeRepository` (`Flow<ThemeConfig>`), exposed via `StateFlow` with `stateIn(..., SharingStarted.WhileSubscribed(5_000), ThemeConfig())`; only render the dynamic-color switch when `SDK_INT >= S`. Never call `isSystemInDarkTheme()` in ViewModels — composables only.

**Extension tokens beyond M3 (CompositionLocals).** Material 3 only defines `error`; add warning/success/info as extensions. `staticCompositionLocalOf` for rarely-changing values, `compositionLocalOf` for frequently-changing (runtime theme switching).

```kotlin
@Immutable data class AppExtendedColors(
    val warning: Color = Color.Unspecified, val onWarning: Color = Color.Unspecified,
    val warningContainer: Color = Color.Unspecified, val onWarningContainer: Color = Color.Unspecified,
    val success: Color = Color.Unspecified, val onSuccess: Color = Color.Unspecified,
    val successContainer: Color = Color.Unspecified, val onSuccessContainer: Color = Color.Unspecified,
)
val LocalAppExtendedColors = staticCompositionLocalOf { AppExtendedColors() }
object AppTheme {
    val extendedColors: AppExtendedColors @Composable get() = LocalAppExtendedColors.current
    val gradientColors: AppGradientColors @Composable get() = LocalAppGradientColors.current
    val dimensions: AppDimensions @Composable get() = LocalAppDimensions.current
}
```

Semantic colors beyond M3's `error` (status + finance + health + edtech):

```kotlin
object SemanticColors {
    val SuccessLight = Color(0xFF2E7D32); val SuccessContainer = Color(0xFFE8F5E9)
    val WarningLight = Color(0xFFE65100); val WarningContainer = Color(0xFFFFF3E0)
    val InfoLight = Color(0xFF0277BD)
}
```
- Fintech direction colors: green = received/credit/gain, red = sent/debit/loss. WRONG: green for "positive balance" AND green for "available" (ambiguous, wrong semantic layer).
- Healthtech metric colors: heart rate always red · sleep always indigo · steps always blue. WRONG: all metrics same blue — unreadable at a glance.
- Edtech answer states: correct green · incorrect red · skipped gray · streak orange.
- Brand color contrast: too-light brand (`#58CC02`) must be darkened (`#3D9B00`) for text use, or used as container with dark text on it. Never use brand color for body text (`primary` is interactive only); never let brand override `error` — errors stay red; brand always needs a dark-mode variant (same shade in both themes fails contrast).

**Component state alphas (replicate `Indication` behavior in custom components)**

```kotlin
val disabledContentAlpha = 0.38f   val disabledContainerAlpha = 0.12f
val pressedOverlayAlpha  = 0.10f   val focusedOverlayAlpha    = 0.12f   val hoveredOverlayAlpha = 0.08f
// ButtonColors(containerColor, contentColor, disabledContainerColor, disabledContentColor)
```

**Brush / gradient system as first-class tokens** (not inline in composables)

```kotlin
object AppBrushes {
    @Composable fun verticalHero(): Brush = Brush.verticalGradient(listOf(LocalAppGradientColors.current.topColor, LocalAppGradientColors.current.bottomColor))
    @Composable fun primaryRadial(): Brush = Brush.radialGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary.copy(alpha = 0f)), radius = 600f)
    @Composable fun scrimGradient(): Brush = Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = .6f), MaterialTheme.colorScheme.surface))
    @Composable fun brandTextGradient(): Brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
}
// Gradient text
Text("Brand Headline", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.drawWithCache {
    val brush = AppBrushes.brandTextGradient()
    onDrawWithContent { drawWithLayer { drawContent(); drawRect(brush = brush, blendMode = BlendMode.SrcAtop) } }
})
```

**Text color on each surface**

```kotlin
Text(title,      color = MaterialTheme.colorScheme.onSurface)                      // primary text on surface
Text(subtitle,   color = MaterialTheme.colorScheme.onSurfaceVariant)               // supporting text
Text(label,      color = MaterialTheme.colorScheme.onPrimaryContainer)             // on primaryContainer
Text(placeholder,color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))   // hint ONLY (see mistake 6)
```

**Runtime theme switching** (branded variants): `enum class AppBrandTheme { Default, HighContrast, Warm, Cool }` + `BrandColorTokens(light, dark, lightGradients, darkGradients)` in a `BrandThemeCatalog: Map`; select from DataStore/StateFlow and resolve inside `AppTheme`.

**Theming checklist — before shipping any screen**
- [ ] All 29 color roles defined in both light and dark `ColorScheme` — none left at Material default
- [ ] No hardcoded hex colours outside of `ColorPrimitives` file
- [ ] No `Color.White`, `Color.Black`, `Color.Gray` in any composable
- [ ] `tertiary` and `tertiaryContainer` set to brand-intentional values, not Material default purple
- [ ] `onX` colour used on its matching `X` background only — no cross-mixing
- [ ] `onSurfaceVariant` used for secondary/supporting text, not `onSurface.copy(alpha=0.6f)`
- [ ] Dynamic color tested: does the app look good with wallpaper-derived colours?
- [ ] Static palette tested: does the app look good when dynamic color is unavailable (API < 31)?
- [ ] Dark mode tested on a real device — not just toggling system setting in emulator
- [ ] Custom extension tokens (`AppExtendedColors`, `AppGradientColors`) provided via `CompositionLocalProvider`
- [ ] `Material Theme Builder` export used to generate the palette, not hand-picked hex values
- [ ] Tonal elevation used for depth — no manual `color.copy(alpha)` overlays to simulate shadow

---

### Typography

M3 Expressive = **15 styles across 5 categories**. Always use `MaterialTheme.typography.*`; zero hardcoded `fontSize`, `fontWeight`, `FontFamily` in composables.

| Category | Slots (size / line height) |
|---|---|
| Display | `displayLarge` 57/64 · `displayMedium` 45/52 · `displaySmall` 36/44 — splash, hero numbers, article titles |
| Headline | `headlineLarge` 32/40 · `headlineMedium` 28/36 · `headlineSmall` 24/32 — page & section titles |
| Title | `titleLarge` 22/28 · `titleMedium` 16/24 (ls .15) · `titleSmall` 14/20 (ls .1) — card titles, list item titles |
| Body | `bodyLarge` 16/24 (ls .5) · `bodyMedium` 14/20 (ls .25) · `bodySmall` 12/16 (ls .4) — primary/secondary body, captions |
| Label | `labelLarge` 14/20 (button text) · `labelMedium` 12/16 (chip text) · `labelSmall` 11/16 (badge text) |

```kotlin
private val BrandFont = FontFamily(
    Font(R.font.brand_regular, FontWeight.Normal),
    Font(R.font.brand_medium, FontWeight.Medium),
    Font(R.font.brand_semibold, FontWeight.SemiBold),
    Font(R.font.brand_bold, FontWeight.Bold),
)
val AppTypography = Typography(
    displayLarge  = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    titleLarge    = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    bodyMedium    = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    labelLarge    = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    /* … all 15 slots … */
)

Text("Screen title", style = MaterialTheme.typography.titleLarge)   // ✅
Text("Wrong", fontSize = 18.sp, fontWeight = FontWeight.Bold)       // ❌ never hardcode
```

**Font pairing (premium signal)**: a **display face** (Fraunces, Playfair, Cormorant, Canela) for headlines gives personality and signals quality; a **humanist sans** (Inter, DM Sans, Plus Jakarta Sans) for body maintains readability. The contrast creates hierarchy you *feel* while scrolling. Apply the display face to `displayLarge/Medium` + `headlineLarge` only; override individual slots rather than rebuilding `Typography`.

Default posture: use the M3 defaults and override only specific slots for branding (`titleLarge = TextStyle(fontFamily = BrandFont, fontWeight = SemiBold, fontSize = 22.sp)`); same for shapes.

Type hierarchy must read at a glance — a senior designer should identify the most important information within 2 seconds.

**Android 16 / API 36 fonts**: `elegantTextHeight` is deprecated and **ignored** on API 36 (readable fonts always used; it defaulted to `true` on API 35). Remove it from XML layouts/styles, never set it programmatically, and re-test rendering for Arabic, Lao, Myanmar, Tamil, Gujarati, Kannada, Malayalam, Odia, Telugu, Thai. Compose has no `elegantTextHeight` concept — use `Text` + `MaterialTheme.typography`.

**Smooth text motion**: set `textMotion = TextMotion.Animated` for scale transitions on text; `Modifier.skipToLookaheadSize()` prevents text reflow during shared-element size transitions.

**Compose Multiplatform**: `Font()` is a **composable** in CMP (unlike Android), so `Typography` construction must be composable:

```kotlin
@Composable fun AppTypography(): Typography {
    val fontFamily = FontFamily(Font(Res.font.Inter_Regular, FontWeight.Normal), Font(Res.font.Inter_Bold, FontWeight.Bold))
    return MaterialTheme.typography.copy(
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = fontFamily),
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = fontFamily, fontWeight = FontWeight.Bold))
}
```
Fonts live in `composeResources/font/` (`.ttf`/`.otf`); icons in `composeResources/drawable/` tinted with `ColorFilter.tint(MaterialTheme.colorScheme.onSurface)`; dark variants in `drawable-dark/`. Keep resource resolution in composables (`stringResource`/`painterResource` at render time); ViewModels hold semantic enums only.

---

### Shapes

M3 shape scale: `extraSmall`, `small`, `medium`, `large`, `extraLarge` (+ M3 Expressive adds morphing and a `full`/pill slot).

```kotlin
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),    // chips, badges, small tags
    small      = RoundedCornerShape(8.dp),    // text fields, buttons, tooltips
    medium     = RoundedCornerShape(12.dp),   // cards, menus (most common)
    large      = RoundedCornerShape(16.dp),   // bottom sheets, expanded cards, dialogs
    extraLarge = RoundedCornerShape(28.dp),   // nav drawer, large dialogs / feature cards
)
// M3 Expressive asymmetric shapes — personality and directional emphasis
object AsymmetricShapes {
    val topRounded    = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    val bottomRounded = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
    val startRounded  = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    val full          = RoundedCornerShape(50)  // pills, FABs, avatars
}
Button(shape = MaterialTheme.shapes.full)  // pill buttons (M3 Expressive)
ModalBottomSheet(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))  // top corners only
```

Alternative full scales for a distinct personality: Rounded `8/12/16/20/32` or Angular `2/4/6/8/12`.

Category shape personalities: Fintech → conservative 4–12dp (pill shapes signal unreliability in banking) · Edtech → friendly 16–20dp course cards, pill tags · Healthtech → soft 20–28dp metric cards, circles for rings · Enterprise → minimal 4–8dp · Social → chat bubbles need asymmetric radius (large on 3 corners, small on the sender corner).

Custom non-rectangular clipping via `GenericShape`:

```kotlin
val TicketShape = GenericShape { size, _ -> /* arcTo() punch-out at corner, then lineTo edges, close() */ }
val WaveBottomShape = GenericShape { size, _ -> /* lineTo + quadraticBezierTo twice, close() */ }
```

Shape morphing (M3 Expressive) — animate the radius instead of swapping shapes:

```kotlin
val cornerRadius by transition.animateDp(transitionSpec = { spring(stiffness = Spring.StiffnessMediumLow) }, label = "radius") { s ->
    if (s == ButtonState.Loading) AppRadius.Full else AppRadius.Small
}
Button(onClick, shape = RoundedCornerShape(cornerRadius)) { … }
```

Rules: components pick up their shape from the theme automatically (`Card` → medium). Images must be `clip`ped to the parent radius or they bleed past it. Wrong: arbitrary radii (`7dp`, `15dp`, `22dp`), one radius for everything (no shape hierarchy), inline `cornerRadius = 12.dp` instead of `MaterialTheme.shapes.medium`.

---

### Motion Principles

"Animation is not decoration. Every motion should communicate something: that a new element has arrived, that a process is in progress, that content is loading, that an action succeeded. Motion that communicates nothing costs the user attention for no return."

**M3 Expressive replaced fixed-duration easing curves with physics-based springs** (`MotionScheme` / `MaterialTheme.motionScheme` is the carrier of these specs; the expressive variant is `MotionScheme.expressive`, standard is `MotionScheme.standard()`, e.g. Wear OS `MaterialTheme(colorScheme, typography, shapes, motionScheme = MotionScheme.standard(), …)` and `snapAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()`).

**The core rule: if it moves → `spring()`. If it fades/changes color → `tween()` with M3 easing.**

```kotlin
// Spatial motion (position, size, scale) — never tween() for elements that move
val spatialEnter = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
// Effects (opacity, color, blur) — no mass, so a curve is correct
val effectsIn  = tween<Float>(Duration.medium2, easing = AppEasing.EmphasizedDecel)
val effectsOut = tween<Float>(Duration.short4,  easing = AppEasing.EmphasizedAccel)
```

Spring is the default because it is **interruption-safe**: it maintains velocity when the target changes. `tween` snaps to a new curve on interruption, which feels jarring. A new `animateTo` on an `Animatable` cancels the ongoing animation and continues from the current value/velocity — no jumpiness.

**Choreography rules (motion that feels intentional, not random)**
1. **Enter with ease-out, exit with ease-in.** Arriving content decelerates (has reached its destination); leaving content accelerates away. `FastOutSlowInEasing` / `AppEasing.EmphasizedDecel` for enter; `FastOutLinearInEasing` / `AppEasing.EmphasizedAccel` for exit.
2. **Stagger arrivals by ~30–40ms per item**, capped at `min(index * 35, 300)` ms total — beyond 300ms the last item feels forgotten.
3. **Enter before exit.** When replacing content, the new content begins at or slightly before the old content begins exiting → "something new is here." Sequential (out then in) → "something is loading."
4. **Coordinate opacity with geometry.** `fadeIn()` synchronized with `expandVertically()` "doubles perceived quality" for one line of code.
5. **Reserve bounce for celebration.** `DampingRatioMediumBouncy` reads playful → success states, first-time experiences, achievements. Never on everyday navigational actions — it reads immature.
6. **Duration budget**: navigation 300–500ms; UI feedback 100–200ms; loading completion 400–600ms; never >600ms except illustrative animation.

**API choice — "pick the smallest API that expresses the motion and its lifecycle"**

| Need | Prefer |
|---|---|
| Show/hide a subtree with enter/exit semantics (content removed after exit) | `AnimatedVisibility` |
| One value follows state | `animate*AsState` |
| Several values follow one boolean/enum/sealed state, in sync | `rememberTransition` / `updateTransition` + child animations |
| Child size changes (resize a container) | `Modifier.animateContentSize()` |
| Different composable trees fill one region | `AnimatedContent` (or `Crossfade` for the simple case) |
| Drag, fling, interruption, imperative control | `Animatable` |
| Repeating cycle (shimmer, pulse, spinner) | `rememberInfiniteTransition` |
| Seekable / test-controlled progress | `SeekableTransitionState` |
| Custom type | `animateValueAsState` + `TwoWayConverter` |
| SVG/icon animation | `AnimatedVectorDrawable` (Android), Lottie/Compottie (CMP) |
| List item insert/remove/reorder | `Modifier.animateItem()` |
| Different timing per property | `Animatable` with sequential `animateTo` |
| Screen destination swaps | Navigation Compose `enterTransition`/`exitTransition` (it owns them) |

Lifecycle check: an alpha animation keeps content composed; `AnimatedVisibility` removes it after exit — do not use a fade when unmounting is required.

**AnimationSpec reference**

| Spec | When | Key detail |
|---|---|---|
| `spring` (default) | General purpose, interruption-safe | Maintains velocity on target change; `dampingRatio` (bounciness), `stiffness` (speed) |
| `tween` | Exact duration control | `durationMillis`, `delayMillis`, `easing` |
| `keyframes` | Specific values at timestamps | `value at millis using easing` |
| `keyframesWithSplines` | Smooth 2D curved paths | `Offset at fraction` |
| `repeatable` / `infiniteRepeatable` | Looping | `iterations`, `repeatMode` (Reverse/Restart) |
| `snap` | Instant jump | Optional `delayMillis` — also the reduce-motion/low-power fallback |

**Spring tuning — "the feel of your app lives in these numbers. Choose deliberately."**

| Stiffness | Feel | Use for |
|---|---|---|
| `Spring.StiffnessVeryHigh` | Snappy, instant-feeling | Toggle switches, checkboxes |
| `Spring.StiffnessHigh` | Fast, crisp | Press feedback, button scale, icon changes/quick snaps |
| `Spring.StiffnessMedium` | Balanced, responsive | Cards, panels, shared elements |
| `Spring.StiffnessMediumLow` | Relaxed, flowing | Container transforms, FAB morphs, page transitions, large elements |
| `Spring.StiffnessLow` | Slow, gentle | Background effects, ambient motion |

| DampingRatio | Feel | Use for |
|---|---|---|
| `DampingRatioHighBouncy` | Very springy overshoot | Playful, gamified UIs |
| `DampingRatioMediumBouncy` | Noticeable bounce | List item arrival, success state |
| `DampingRatioLowBouncy` (0.75) | Subtle settle, settles fast | Headers, navigation, subtle feedback |
| `DampingRatioNoBouncy` | Clean, no overshoot | Professional, content-heavy apps |

```kotlin
// The "feels right" default for most UI — balanced, no bounce, medium speed
val defaultSpring = spring<Float>(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
// Container transforms (card expansions, FAB morphs)
val containerTransformSpring = spring<Float>(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
```

**Animation state is local UI state** — keep it in composables, never in reducers/ViewModels. "Never put `buttonBounceProgress`, `errorShakeCounter`, `skeletonAlpha`, `rowRemovalAnimationPhase` in ViewModel state." Reducer state = business/UI meaning, not visual tween progress.

**Performance phase rules**
- `graphicsLayer { scaleX = …; rotationZ = …; alpha = …; translationX = …; shadowElevation = … }` → Drawing phase only, cheapest, no recomposition. Replaces `Modifier.scale()` / `Modifier.offset()` (which recompose every frame).
- `Modifier.offset { IntOffset(...) }` (lambda form) defers to the Layout phase.
- Animated colors: `Modifier.drawBehind { drawRect(animatedColor.value) }` beats `Modifier.background()` when the color updates every frame.
- Keep animated `State` reads inside layout/draw-block modifiers when it changes at frame rate.
- `animateContentSize` goes BEFORE size modifiers in the chain.
- `Canvas` draws in the Drawing phase — visual updates need no recomposition.
- Never animate padding/size every frame (expensive Layout phase) — prefer draw-phase transforms.
- `keyframes { durationMillis = Duration.medium3; 0f at 0 with FastOutLinearInEasing; 1.15f at Duration.medium2; 1f at Duration.medium3 with LinearOutSlowInEasing }` for branded overshoot.

---

### Animation APIs

**animate\*AsState** — types: `Float`, `Color`, `Dp`, `Size`, `Offset`, `Rect`, `Int`, `IntOffset`, `IntSize`. `label` is mandatory (profiler/Layout Inspector).

```kotlin
// Spatial → spring
val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp,
    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "cardElevation")
// Effects → tween with M3 easing
val alpha by animateFloatAsState(if (isEnabled) 1f else 0.38f,
    animationSpec = tween(Duration.short4, easing = AppEasing.Standard), label = "alpha")
val backgroundColor by animateColorAsState(
    if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    animationSpec = tween(Duration.medium1, easing = AppEasing.Standard), label = "bgColor")
val width by animateDpAsState(if (expanded) 200.dp else 56.dp, animationSpec = spring(dampingRatio = 0.7f), label = "fabWidth")
val offset by animateIntOffsetAsState(if (moved) IntOffset(100, 100) else IntOffset.Zero, label = "offset")

Card(modifier = Modifier.graphicsLayer { this.alpha = alpha }.offset(x = offsetX),
     colors = CardDefaults.cardColors(containerColor = backgroundColor),
     elevation = CardDefaults.cardElevation(defaultElevation = elevation)) { /* … */ }
// Theme transitions animate too:
val surfaceColor by animateColorAsState(MaterialTheme.colorScheme.surface, label = "background")
```

**Animatable** — coroutine-based, gesture-coupled, imperative.

| Operation | Purpose |
|---|---|
| `animateTo(target, spec, initialVelocity)` | Animate to target (suspends) |
| `snapTo(value)` | Instant set — sync with finger during drag |
| `animateDecay(velocity, decay)` | Fling deceleration (`splineBasedDecay<Float>(this)`) |
| `stop()` | Cancel animation |
| `updateBounds(lower, upper)` | Constrain range |

```kotlin
val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }   // ALWAYS remember {}
LaunchedEffect(targetPosition) { offset.animateTo(targetPosition) }
Box(Modifier.offset { offset.value.toIntOffset() })
// Sequential:                     // Concurrent:
// LaunchedEffect(Unit) {          // LaunchedEffect(Unit) {
//   alphaAnim.animateTo(1f)       //   launch { alphaAnim.animateTo(1f) }
//   yAnim.animateTo(100f)         //   launch { yAnim.animateTo(100f) }
// }                               // }
```

**rememberTransition / updateTransition** — one state drives many synchronized properties.

```kotlin
val transition = rememberTransition(targetState = phase, label = "phase")   // or updateTransition(state, label = "button_state")
val alpha  by transition.animateFloat(label = "alpha")  { if (it == Phase.Visible) 1f else 0f }
val offset by transition.animateDp(label = "offset")    { if (it == Phase.Visible) 0.dp else 24.dp }

val containerColor by transition.animateColor(
    transitionSpec = { tween(Duration.medium2, easing = AppEasing.Standard) }, label = "containerColor"
) { s -> when (s) {
    ButtonState.Idle -> MaterialTheme.colorScheme.primary
    ButtonState.Loading -> MaterialTheme.colorScheme.primaryContainer
    ButtonState.Success -> MaterialTheme.colorScheme.tertiaryContainer
    ButtonState.Error -> MaterialTheme.colorScheme.errorContainer } }
val scale by transition.animateFloat(
    transitionSpec = { spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh) }, label = "scale"
) { s -> if (s == ButtonState.Success) 1.06f else 1f }

Button(onClick, colors = ButtonDefaults.buttonColors(containerColor = containerColor),
       modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(20.dp).alpha(1f - contentAlpha), strokeWidth = 2.dp, color = LocalContentColor.current)
        Text(label, modifier = Modifier.alpha(contentAlpha))
    }
}
```
Per-direction timing: `transitionSpec = { when { Expanded isTransitioningTo Collapsed -> spring(stiffness = 50f); else -> tween(Duration.medium2) } }`. Start immediately: `MutableTransitionState(Collapsed).apply { targetState = Expanded }`. Coordinated children: `transition.AnimatedVisibility(visible = { it == Expanded }) { … }`, `transition.AnimatedContent { … }`.

**AnimatedVisibility** — enter/exit; `+` combines; content is removed after exit.

| Enter | Exit |
|---|---|
| `fadeIn` | `fadeOut` |
| `slideIn` / `slideInHorizontally` / `slideInVertically` | `slideOut` / `slideOutHorizontally` / `slideOutVertically` |
| `scaleIn` | `scaleOut` |
| `expandIn` / `expandHorizontally` / `expandVertically` | `shrinkOut` / `shrinkHorizontally` / `shrinkVertically` |

```kotlin
AnimatedVisibility(
    visible = isVisible,
    enter = slideInVertically(initialOffsetY = { -it / 4 },
              animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
          + fadeIn(tween(Duration.medium2, easing = AppEasing.EmphasizedDecel)),
    exit  = slideOutVertically(targetOffsetY = { -it / 4 },
              animationSpec = tween(Duration.short4, easing = AppEasing.EmphasizedAccel))
          + fadeOut(tween(Duration.short4))
) { Content() }
```

Canonical presets:
```kotlin
// Bottom panels / FAB / snackbar
slideInVertically(initialOffsetY = { it }, animationSpec = spring(DampingRatioMediumBouncy, StiffnessMedium)) + fadeIn(tween(Duration.medium2))
// exit: slideOutVertically(targetOffsetY = { it }) tween(short4, EmphasizedAccel) + fadeOut(tween(short4))
// Pop (menus, badges, chips): scaleIn(initialScale = .7f, spring(LowBouncy, StiffnessHigh)) + fadeIn(tween(short3))
// Collapse (accordion): expandVertically(expandFrom = Alignment.Top, spring(NoBouncy, StiffnessMediumLow)) + fadeIn(tween(medium1))
//                     shrinkVertically(shrinkTowards = Alignment.Top, tween(short4, EmphasizedAccel)) + fadeOut(tween(short4))
// Side sheet: slideInHorizontally(initialOffsetX = { it }) / slideOutHorizontally(targetOffsetX = { it })
```

Element-level choreography: children with their own timing via `Modifier.animateEnterExit(enter = slideInVertically(tween(Duration.medium2, delayMillis = 100), initialOffsetY = { it / 2 }), exit = slideOutVertically(tween(Duration.short3)))`. Put `EnterTransition.None` / `ExitTransition.None` on the parent so children define their own. Stagger list arrival: `LaunchedEffect(Unit) { isVisible = true }` then per index `val delay = (index * 40).coerceAtMost(300)` into `fadeIn(tween(300, delayMillis = delay)) + slideInVertically(tween(300, delayMillis = delay), initialOffsetY = { it / 3 })`.

**AnimatedContent** — swaps composable trees; outgoing + incoming stay composed together. `Crossfade` is the simple case (opacity only, content stays composed).

```kotlin
AnimatedContent(
    targetState = uiState,
    contentKey  = { it::class },            // key by STATE TYPE / visual shape, not payload value
    transitionSpec = {
        (slideInVertically(initialOffsetY = { it / 4 }, animationSpec = spring(DampingRatioLowBouncy, StiffnessMediumLow))
            + fadeIn(tween(Duration.medium2, easing = AppEasing.EmphasizedDecel)))
        .togetherWith(slideOutVertically(targetOffsetY = { -it / 4 }, animationSpec = tween(Duration.short4, easing = EmphasizedAccel))
            + fadeOut(tween(Duration.short4)))
    },
    label = "uiStateTransition"
) { state -> when (state) { Loading -> AppLoadingScreen(); is Success -> SuccessContent(state.data); … } }
```

Render from the **content-lambda target**, never captured outer state (both branches otherwise read the newest value, and outer state is stale during exit):
```kotlin
AnimatedContent(targetState = selectedId) { Destination(selectedId) }        // WRONG
AnimatedContent(targetState = selectedId) { targetId -> Destination(targetId) } // RIGHT
```

`contentKey` choice:

| Change | Typical key |
|---|---|
| Loading/content/error have different shapes | A branch key (`"loading"`/`"content"`/`"error"`) |
| Different items should crossfade | Stable item id |
| A data refresh stays in the same shape | One key for that branch (updates in place) |

Without `contentKey`, unequal payloads animate as *new* content — keep the default only when the payload change itself is the desired transition.

Direction-aware transitions + counter (needs `SizeTransform` or the layout jumps):
```kotlin
transitionSpec = {
    val dir = if (targetState > initialState) 1 else -1
    (slideInVertically { it * dir } + fadeIn()).togetherWith(slideOutVertically { -it * dir } + fadeOut())
        .using(SizeTransform(clip = false))     // SizeTransform controls size animation between states
}
// Tabs: track previous index with SideEffect { previousTab = tab } and slide { it * direction }
```

**rememberInfiniteTransition** — shimmer, pulse, breathing, spinners. Auto-pauses when the app is backgrounded but **always runs while composed** even when off-screen inside a `LazyColumn`; cancel on exit and never exceed 2–3 concurrent animated properties (each costs composition time every frame; don't put one on every screen).

```kotlin
val t = rememberInfiniteTransition(label = "shimmer")
val alpha by t.animateFloat(0.25f, 0.55f,
    infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "shimmerAlpha")
val translate by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "shimmer_translate")
```

**LazyList item animations** — `Modifier.animateItem()` replaces deprecated `animateItemPlacement()`; requires stable `key`.

```kotlin
LazyColumn { items(items, key = { it.id }) { item ->
    ItemRow(item, modifier = Modifier.animateItem(
        fadeInSpec = tween(Duration.medium2, easing = AppEasing.EmphasizedDecel),
        fadeOutSpec = tween(Duration.short4, easing = AppEasing.EmphasizedAccel),
        placementSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)))
} }
```

**animateContentSize** — container resizes smoothly when content changes; place before size modifiers.
```kotlin
Column(Modifier.animateContentSize(animationSpec = spring()).background(surfaceVariant, RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(16.dp)) { … }
```

**Gesture-driven motion**
- Tap-to-follow: `Animatable(Offset.Zero, Offset.VectorConverter)` + `pointerInput { coroutineScope { while(true) { awaitPointerEventScope { val p = awaitFirstDown().position; launch { offset.animateTo(p) } } } } }` — a new tap cancels and continues with velocity preserved.
- Swipe-to-dismiss: `snapTo` during drag → `VelocityTracker.calculateVelocity()` → `decay.calculateTargetValue()` → `updateBounds(-width, width)` → `animateTo(0f, initialVelocity = v)` for snap-back, `animateDecay(v, decay)` + `onDismissed()` for fling-off.
- Swipe-to-delete with threshold + colored container revealing behind:
```kotlin
var offsetX by remember { mutableStateOf(0f) }
val animatedOffset by animateFloatAsState(offsetX, spring(DampingRatioMediumBouncy, StiffnessMedium), label = "swipeOffset")
Box(Modifier.pointerInput(item.id) { detectHorizontalDragGestures(
    onDragEnd = { if (offsetX < deleteThreshold) onDelete(item.id) else offsetX = 0f },
    onHorizontalDrag = { _, d -> offsetX = (offsetX + d).coerceIn(deleteThreshold * 1.2f, 0f) }) }) {
    Box(Modifier.fillMaxSize().alpha((-animatedOffset / -deleteThreshold).coerceIn(0f, 1f))
        .background(MaterialTheme.colorScheme.errorContainer), Alignment.CenterEnd) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer) }
    AppCard(Modifier.offset { IntOffset(animatedOffset.roundToInt(), 0) }) { Text(item.title) }
}
```
- Press feedback without ripple (image/card surfaces): custom `Indication` scaling to `0.94f` with `spring(stiffness = Spring.StiffnessHigh)` in `IndicationInstance.drawIndication()`, passed as `clickable(interactionSource = remember { MutableInteractionSource() }, indication = ScaleIndicationToken)`.
- Parallax hero: `derivedStateOf { scrollState.value * 0.4f }` → `graphicsLayer { translationY = offset }` inside `clipToBounds()`.
- Swipeable/draggable content must have a **visible affordance** (drag handle, peek of content behind, directional indicator) — users must not discover gestures by accident.

**Canvas / custom drawing**: `Canvas { drawCircle(…), drawRect(…), drawLine(…), drawArc(…), drawPath(…) }`; `Modifier.drawBehind { }` behind children, `Modifier.drawWithContent { drawContent(); … }` over/around them. Animate by reading an `animateFloatAsState` progress inside the draw lambda (`sweepAngle = 360f * progress`), incl. `Brush.sweepGradient` progress rings and `Path` morphs (`lerp(0.4f, 1f, progress)` inner radius). Art-based motion (Lottie): `rememberLottieComposition(LottieCompositionSpec.RawRes(id))` + `animateLottieCompositionAsState(...)` + `LottieAnimation(composition, progress = { progress })`.

**NavHost screen transitions (M3 Expressive, applied to all destinations)**

```kotlin
NavHost(navController, startDestination,
    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(Duration.medium4, easing = AppEasing.EmphasizedDecel)) + fadeIn(tween(Duration.medium4)) },
    exitTransition  = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(Duration.short4, easing = AppEasing.EmphasizedAccel)) + fadeOut(tween(Duration.short4)) },
    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(Duration.medium4, easing = EmphasizedDecel)) + fadeIn(tween(Duration.medium4)) },
    popExitTransition  = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(Duration.short4, easing = EmphasizedAccel)) + fadeOut(tween(Duration.short4)) })
```

**Styles API (experimental) — built-in state animation**: inside `Style { }` blocks, `pressed { animate { borderColor(Color.Magenta); background(...) } }`, `animate(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { scale(1.2f) }`, with `hovered { }`, `focused { }`, `selected { }`, `disabled { }`, `toggled { }`, nested states, `Style then { … }` composition, `Modifier.styleable(styleState, base then style)`, `rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }`, `MutableStyleState`, custom `StyleStateKey(PlayerState.Stopped)` + `state(key, block) { k, s -> s[k] == … }` extension states. Requires `compileSdk` 37+, foundation `1.12.0-alpha01`+ (or BOM `2026.04.01`+), `-opt-in=androidx.compose.foundation.style.ExperimentalFoundationStyleApi`, `import androidx.compose.foundation.style.Style`, default `style: Style = Style` (never a concrete default), and static `Theme.styles` reference (not CompositionLocal). Custom components should check `rememberRipple()` usage in M3 source when wrapping existing components.

---

### Shared Element Transitions

"Shared element transitions are the single most powerful technique for communicating spatial relationships during navigation… No amount of fade or slide achieves that. Use them by default for any content navigation." Instant cuts communicate nothing; slide says "you moved sideways"; shared elements say **this is that thing, grown**.

**Three layers**
1. `SharedTransitionLayout` (coordinator) — a layout composable creating a `SharedTransitionScope`; must **wrap the entire region** where elements transition, i.e. it wraps the `NavHost` / `NavDisplay`, not individual screens. Keeps the registry of active shared content states and interpolates geometry.
2. `SharedTransitionScope` (receiver) — composables must be inside it (via `with(sharedTransitionScope) { }` or CompositionLocal) to call `sharedElement()` / `sharedBounds()`.
3. `AnimatedVisibilityScope` (per-composable handle) — each destination is an `AnimatedContentScope` (implements `AnimatedVisibilityScope`), telling the system whether it's entering or exiting. Navigation 2: `this@composable`. Navigation 3: `LocalNavAnimatedContentScope.current`.

**Key contract**: source and destination nodes with **identical keys** get their geometry interpolated. Keys compared by structural equality → use a stable identity value (database ID), never a list index.

```kotlin
SharedTransitionLayout {
    AnimatedContent(showDetails, label = "shared") { targetState ->
        if (!targetState) ListItem(sharedTransitionScope = this@SharedTransitionLayout, animatedVisibilityScope = this@AnimatedContent)
        else DetailScreen(sharedTransitionScope = this@SharedTransitionLayout, animatedVisibilityScope = this@AnimatedContent)
    }
}
// or wrap NavHost:
SharedTransitionLayout {
    NavHost(navController, startDestination = "list") {
        composable("list")   { ListScreen(this@SharedTransitionLayout, this@composable) }
        composable("detail/{id}") { DetailScreen(this@SharedTransitionLayout, this@composable) }
    }
}
```

**sharedElement() vs sharedBounds() — the most important per-transition choice**

| Question | `sharedElement()` | `sharedBounds()` |
|---|---|---|
| Same content in both states? | Yes | No (visually different content) |
| Rendering during transition | Only target content rendered | Both entering and exiting content visible, clipped to shared region |
| Same image / icon hero | ✓ | — |
| Card → full screen / FAB → dialog | — | ✓ |
| Text | Avoid (prefer `sharedBounds`) | Preferred (handles font changes) |
| `enter`/`exit` params | Not available (only geometry changes) | Available |

```kotlin
Modifier.sharedElement(
    state = rememberSharedContentState(key = "media-image-${item.id}"),
    animatedVisibilityScope = animatedVisibilityScope,
    boundsTransform = { initialBounds, targetBounds ->
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
        // or keyframes { durationMillis = 300; initial at 0 using ArcMode.ArcBelow using FastOutSlowInEasing; target at 300 }
    })

Modifier.sharedBounds(
    sharedContentState = rememberSharedContentState(key = "content-card-${content.id}"),
    animatedVisibilityScope = animatedVisibilityScope,
    enter = fadeIn(tween(300)), exit = fadeOut(tween(150)),
    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),   // or RemeasureToBounds
    clipInOverlayDuringTransition = OverlayClip(cardShape),
    zIndexInOverlay = 1f)
```

Default behavior out of the box: spring for geometry + crossfade for content visibility. "This is already good. Customise only when the default feels wrong." Spring = physical, slight overshoot → imagery, cards, surfaces (tune `stiffness` for speed, `dampingRatio` for bounce). Tween = designed, precise timing → when you must coordinate with Lottie/audio.

- `ResizeMode.ScaleToBounds()` — scales the child layout graphically; recommended for `Text`.
- `ResizeMode.RemeasureToBounds` — re-measures the child each frame; recommended for differing aspect ratios.
- `renderInSharedTransitionScopeOverlay()` / `renderInSharedTransitionScope(scope)` — keeps elements that exist on only one screen (FAB, bottom bar, back button, toolbar) in the overlay layer so they don't blink into existence at the end; pair with `animateEnterExit(enter = fadeIn(tween(300, delayMillis = 150)), exit = fadeOut(tween(150)))`. Forgetting it = element hidden behind transitioning content or popping in — "neither is acceptable in a polished UI."
- `clipInOverlayDuringTransition = OverlayClip(shape)` — set it, and do NOT apply `Modifier.clip()` before `sharedBounds()`; let `sharedBounds()` manage clipping.
- `zIndexInOverlay` — controls which overlapping shared element renders on top.
- `skipToLookaheadSize()` — prevents text reflow during size transitions.
- Modifier order: **size modifiers AFTER `sharedElement()`**; inconsistent order between matched elements causes visual jumps.
- Unique keys: `data class SharedElementKey(val id: Long, val origin: String, val type: SharedElementType)` / `data class ContentSharedKey(val contentId: Long, val surface: String)` to disambiguate the same content in multiple contexts.
- Text: `sharedElement()` interpolates bounds, not typography — the text renders at the destination style throughout (animate `fontSize` separately with `animateFloatAsState` only if you truly need the appearance of interpolation).

**Threading scopes**: explicit parameters for 1–2-level trees (explicit, zero-magic, IDE-traceable); `compositionLocalOf<SharedTransitionScope?> { null }` + `compositionLocalOf<AnimatedVisibilityScope?> { null }` for 4+ levels. Rule of thumb: switch to CompositionLocal once you add both params to 3+ intermediate composables that don't use them.

**Async images (Coil 3)** — warm the cache so the hero doesn't pop:
```kotlin
AsyncImage(
  model = ImageRequest.Builder(LocalPlatformContext.current).data(url)
      .placeholderMemoryCacheKey("image-$id").memoryCacheKey("image-$id").build(),
  modifier = Modifier.sharedElement(rememberSharedContentState(key = "image-$id"), animatedVisibilityScope = scope))
```

**Four production patterns**: 1) grid thumbnail → full-width detail hero (`sharedElement`, same asset, identical key on both screens, `boundsTransform` spring `StiffnessMedium`/`DampingRatioNoBouncy`); 2) card expansion / container transform (`sharedBounds` + `OverlayClip(cardShape)` + `StiffnessMediumLow`); 3) FAB → full-screen compose surface (`AnimatedContent` with `fadeIn(tween(1)) togetherWith fadeOut(tween(1))` because `sharedBounds` owns the visibility, `OverlayClip(CircleShape)`, shared key as a `private const val` to avoid typos); 4) text continuation (title `sharedElement` list→heading, supporting body text revealed with `animateEnterExit(fadeIn(tween(300, delayMillis = 200)))`).

**Pitfalls & debugging**
1. **Key mismatch → no animation fires** (navigation works, elements snap). Fix: same string template / data-class key from the same stable ID; log `key = 'media-image-${item.id}'` on both sides. Data classes work; mutable lists don't.
2. **Scope not threaded → `IllegalStateException: No SharedTransitionScope found` / NPE or silent fallback.** Fix: `SharedTransitionLayout` must wrap the whole `NavHost`; use `with(sharedTransitionScope) { }`; verify `animatedVisibilityScope` is the correct `this@composable`, not an unrelated/outer scope.
3. **Z-ordering → wrong element renders on top, visual jump.** Fix: `zIndexInOverlay`.
4. **Missing `renderInSharedTransitionScope` → destination-only element disappears/blinks.** Fix: apply it + `animateEnterExit`.
5. **Non-stable keys → animation fires on unrelated recompositions (jitter).** Fix: entity IDs, or composite data-class keys.
6. **Conflicting clip shapes → ugly corner/edge artifacts.** Fix: `OverlayClip(sourceShape)`, remove `clip()` before `sharedBounds()`.

**Predictive back integration**: `<application android:enableOnBackInvokedCallback="true">` + Navigation Compose 2.8+; the back gesture then scrubs the shared element transition in reverse with **no additional code**.

---

### Loading & Empty States

**Decision rule**

| Situation | Best default |
|---|---|
| First load, known result card layout | **Skeleton** mirroring the final geometry |
| Small inline refresh of one section | Keep content + small inline indicator |
| Whole-screen blocking startup with no known structure | Spinner — but rare |
| Recalculating while an old result exists | Keep old result + "updating" affordance |
| Empty but idle | Empty-state hint, not spinner |
| Primary action in flight (button) | `AnimatedContent`/`updateTransition` swapping label ↔ 20dp `CircularProgressIndicator(strokeWidth = 2.dp)` |

Skeleton is the default; subtle shimmer over skeleton is optional polish, not the strategy; spinner only for unknown-layout or blocking operations. "A shimmer that doesn't match the content layout is worse than a spinner — it creates layout shift when the content arrives."

**Never wipe content during refresh. Never cause height jumps, flicker, or lost context.** Reserve a stable slot with a min height.

```kotlin
// BAD — content disappears, layout jumps
if (isLoading) CircularProgressIndicator() else if (quote != null) QuoteContent(quote)
// GOOD — old content preserved, refreshing state layered on
@Composable fun QuoteSection(quote: QuoteUi?, isLoading: Boolean) {
    ResultCardSlot { when {
        quote != null -> QuoteContent(quote = quote, refreshing = isLoading)
        isLoading -> QuoteCardSkeleton()
        else -> QuoteEmptyState() } } }
@Composable fun ResultCardSlot(content: @Composable BoxScope.() -> Unit) =
    Box(Modifier.fillMaxWidth().heightIn(min = 180.dp), content = content)
```

**Shimmer + skeleton implementations**

```kotlin
@Composable fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium) {
    val translate by rememberInfiniteTransition(label = "shimmer").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "shimmer_translate")
    val brush = Brush.linearGradient(
        colors = listOf(surfaceVariant, surfaceVariant.copy(alpha = 0.4f), surfaceVariant),
        start = Offset(translate * 1000f, translate * 500f), end = Offset(translate * 1000f + 600f, translate * 500f + 300f))
    Box(modifier.clip(shape).background(brush))
}
// Skeleton mirrors the real card exactly: 80dp avatar, 75% / 50% / 90% text lines
@Composable fun ArticleCardSkeleton(modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().height(180.dp)) { Row(Modifier.padding(AppSpacing.M)) {
        ShimmerBox(Modifier.size(80.dp), MaterialTheme.shapes.small); Spacer(Modifier.width(AppSpacing.M))
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S)) {
            ShimmerBox(Modifier.fillMaxWidth(0.75f).height(16.dp)); ShimmerBox(Modifier.fillMaxWidth(0.5f).height(16.dp)); ShimmerBox(Modifier.fillMaxWidth(0.9f).height(12.dp)) } } } }
// Alpha-pulse variant (0.25f→0.55f / 0.35f→0.60f, tween(800), RepeatMode.Reverse) on onSurface / surfaceVariant
// Show a grid of placeholders during load:
is Loading -> LazyVerticalGrid(columns = GridCells.Adaptive(180.dp)) { items(6) { ItemCardSkeleton() } }
```

Also: `PulseIndicator` (scale 1→1.2/1.4 + alpha 0.8→0.3, `tween(700, FastOutSlowInEasing)`, Reverse) for online dots; `BreathingGradientBackground` (4000ms `LinearEasing` offset sweep across `primaryContainer`/`secondaryContainer`) as a subtle premium ambient effect.

M3 Expressive loading components: `LoadingIndicator()` / `ContainedLoadingIndicator()` (morph between shapes while loading) replace `CircularProgressIndicator` for in-content loading; `ButtonGroup { Button … ; FilledTonalButton … }` replaces side-by-side buttons.

**Screen-level states** (use everywhere): `AppLoadingScreen` (centered `CircularProgressIndicator`), `AppEmptyScreen(title, body, illustration?, action?)` (headlineSmall title, bodyMedium `onSurfaceVariant` body, CTA), `AppErrorScreen(message, onRetry?)` (64dp `Icons.Rounded.ErrorOutline` tinted `error` + "Something went wrong" + Tonal "Try again"). Wrap all four in `AnimatedContent(targetState = uiState, contentKey = { it::class })` so Loading→Success→Error→Empty transitions rather than snapping.

**Pull to refresh**: `PullToRefreshBox(isRefreshing, onRefresh, state = rememberPullToRefreshState(), indicator = { PullToRefreshDefaults.Indicator(state, isRefreshing, color = primary, containerColor = primaryContainer, modifier = Modifier.align(Alignment.TopCenter)) })`. `LinearProgressIndicator` may exist only as a secondary top-of-screen background-refresh affordance — never as the primary loading state.

**Snackbar** (async completion feedback): `SnackbarHostState` remembered in the Route, passed to `Scaffold(snackbarHost = { SnackbarHost(it) })`, shown from a ViewModel `events`/`Effect` flow collected in `LaunchedEffect(Unit)`; `showSnackbar(message, actionLabel, duration = SnackbarDuration.Short)` returns `SnackbarResult.ActionPerformed` → dispatch the action event.

**Partial results / perceived performance**: compute an instant local estimate from the draft and show it; fetch remote refinement in background; keep the old refined value until the new one arrives; label refreshed state clearly. Apply local field state instantly, recalculate cheap deterministic outputs immediately, debounce only expensive async work, keep layout stable, animate only meaningful content changes.

**Inline validation / disabled / input preservation** (form UX that motion supports): validate as the user edits where feedback is obvious; don't scream on untouched fields; errors inline next to the field, without collapsing the layout; no modal per keystroke, no full-form red wall; disabled is acceptable only when the reason is obvious, the screen stays readable, and input is preserved; never clear edited fields on refresh, never clear the last good result while fetching, never wipe the screen because one request failed; error messages must be text, not color only; keep controls large enough for data-entry reliability.

---

### Adaptive Layout

"An app that only runs well on a phone in portrait mode is half-built." 1B+ active large-screen Android devices; Google Play requires large-screen quality tiers for featuring; and **from Android 16 / API 36, on displays with smallest width ≥ 600dp the system ignores `screenOrientation`, `resizableActivity="false"`, `minAspectRatio`/`maxAspectRatio`, and `setRequestedOrientation()`** (exceptions: `android:appCategory="game"` and windows below sw600dp). Build adaptive by default.

**Golden rule: before placing any layout element, ask how it looks at 600dp AND 840dp. If the answer is "one stretched column", it's wrong.**

**WindowSizeClass breakpoints**

| Class | Width | Typical device | Navigation | Layout |
|---|---|---|---|---|
| `COMPACT` | < 600dp | Phone portrait | `NavigationBar` (bottom bar) | Single column |
| `MEDIUM` | 600–840dp | Phone landscape, small tablet, unfolded foldable inner | `NavigationRail` | Two columns / wider single |
| `EXPANDED` | ≥ 840dp | Tablet landscape, desktop, Chromebook | `NavigationDrawer` (permanent) | Two- or three-pane |

```kotlin
// Activity-level
val windowSizeClass = calculateWindowSizeClass(this)   // androidx.window:window:1.3.0 + window-core
// Composable-level (material3-window-size-class)
val wsc = currentWindowAdaptiveInfo().windowSizeClass
val isCompact  = wsc.windowWidthSizeClass == WindowWidthSizeClass.COMPACT    // or wsc.widthSizeClass
val isMedium   = wsc.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM
val isExpanded = wsc.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
// helpers:
val WindowSizeClass.isCompact get() = windowWidthSizeClass == WindowWidthSizeClass.COMPACT
val WindowSizeClass.isShortLandscape get() = windowHeightSizeClass == WindowHeightSizeClass.COMPACT && !isCompact
```
Compute `WindowSizeClass` once at app/activity level and pass derived layout decisions down as state.

**NavigationSuiteScaffold — zero-code adaptive navigation** (`androidx.compose.material3.adaptive:adaptive-navigation-suite`)

```kotlin
NavigationSuiteScaffold(navigationSuiteItems = {
    TopLevelRoute.entries.forEach { route -> item(
        selected = currentEntry?.destination?.hasRoute(route.routeClass) == true,
        onClick  = { navController.navigateTopLevel(route) },
        icon = { Icon(if (selected) route.selectedIcon else route.icon, stringResource(route.labelRes)) },
        label = { Text(stringResource(route.labelRes)) },
        badge = if (route.badgeCount > 0) { { Badge { Text("$count") } } } else null) }
}) { AppNavHost(navController) }   // Compact bar → Medium rail → Expanded drawer. No extra code, ever.
```
Default for any app with 3–5 top-level destinations.

```kotlin
// Top-level navigation helper — prevents duplicate backstack entries
fun NavController.navigateTopLevel(route: TopLevelRoute) = navigate(route.routeClass.objectInstance ?: return) {
    popUpTo(graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
```
Visibility control (hide on scroll-down / in immersive media): `var isNavBarVisible by remember { mutableStateOf(true) }` (name it `isNavBarVisible` or `shouldShowNavBar`) + `rememberNavigationSuiteScaffoldState()` → `LaunchedEffect(isNavBarVisible) { if (it) state.show() else state.hide() }`. When a detail screen is full-screen on mobile, full-screen mode must be deactivated on larger screens; detail screens must not show a back arrow when part of a list-detail layout. Hide the navigation area when it distracts (camera preview, full-screen photo).

**Multi-pane scaffolds**

```kotlin
// Two-pane master/detail — every list+detail screen
val navigator = rememberListDetailPaneScaffoldNavigator<String>()     // <Article>() → contentKey
BackHandler(navigator.canNavigateBack()) { navigator.navigateBack() } // REQUIRED for phone back
ListDetailPaneScaffold(directive = navigator.scaffoldDirective, value = navigator.scaffoldValue,
    listPane = { AnimatedPane { ItemListPane(onItemSelected = { id -> navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) }) } },
    detailPane = { AnimatedPane { val id = navigator.currentDestination?.content /* or contentKey */
        if (id != null) ItemDetailPane(id) else Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Select an item", …) } } })
// SupportingPaneScaffold — list + detail + supporting info (email/docs), extra pane shows > 1200dp:
val n = rememberSupportingPaneScaffoldNavigator<String>()
SupportingPaneScaffold(directive = n.scaffoldDirective, value = n.scaffoldValue,
    mainPane = { AnimatedPane { MainContent(onShowDetails = { n.navigateTo(SupportingPaneScaffoldRole.Supporting, it) }) } },
    supportingPane = { AnimatedPane { SupportingContent(n.currentDestination?.content) } },
    extraPane = { AnimatedPane { ExtraContent() } })
// ThreePaneScaffold / NavigableListDetailPaneScaffold / NavigableSupportingPaneScaffold exist;
// prefer the Navigable* variants — they handle pane visibility and back navigation for you.
```
Google's current adaptive skill mandates the **Navigation 3 `SceneStrategy`** approach for new multi-pane work instead of `ListDetailPaneScaffold`/`SupportingPaneScaffold`: add `androidx.compose.material3.adaptive:adaptive-navigation3`, `rememberListDetailSceneStrategy()` / `rememberSupportingPaneSceneStrategy()`, pass to `NavDisplay(sceneStrategies = …)`, tag entries with metadata `ListDetailSceneStrategy.listPane(detailPlaceholder = { … })` / `.detailPane()`, `SupportingPaneSceneStrategy.mainPane()` / `.supportingPane()`.

**Adaptive grids & lists**
```kotlin
LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 180.dp), contentPadding = PaddingValues(Spacing.md),
    horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { … }
// 360dp→1 col, 412dp→2, 768dp→4, 1200dp→6. Staggered: StaggeredGridCells.Adaptive(<width>.dp).
val columns = when { wsc.isExpanded -> GridCells.Fixed(4); wsc.isMedium -> GridCells.Fixed(3); else -> GridCells.Fixed(2) }
```
For non-lazy lists of repeated same-type items, migrate `Column` → experimental `Grid` (Compose 1.11.0-beta01+, `@OptIn(ExperimentalGridApi::class)`), configuring rows/columns from `constraints.maxWidth` inside the `config: GridConfigurationScope.() -> Unit` (`column(cellSize)`, `row(cellSize)`, `gap(8.dp)`). Do NOT put `Grid` inside the existing `Column`; replace it. Also available (experimental): `FlexBox` (container/item behavior) and `MediaQuery` (screen size, pointer precision, keyboard type, cameras/microphones).

**Content width & padding caps** — never stretch text on large screens (typography research: ~75 chars/line max).
```kotlin
BoxWithConstraints(Modifier.fillMaxSize()) { val w = minOf(maxWidth, 720.dp)
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) { Column(Modifier.width(w).padding(horizontal = Spacing.md).verticalScroll(…)) { … } } }
val WindowSizeClass.contentPadding() = PaddingValues(horizontal = when { isExpanded -> Spacing.xl; isMedium -> Spacing.lg; else -> Spacing.md })
```
Within-composable width response also uses `BoxWithConstraints { if (maxWidth > 600.dp) Row { … } else Column { … } }`.

**Bottom sheet → side sheet; adaptive dialogs**
```kotlin
if (wsc.isCompact) ModalBottomSheet(onDismissRequest = onDismiss) { Box(Modifier.navigationBarsPadding()) { content() } }
else AnimatedVisibility(visible, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
    Box(Modifier.fillMaxHeight().width(360.dp).background(MaterialTheme.colorScheme.surfaceContainerLow).align(Alignment.CenterEnd)) { … } }

AlertDialog(..., modifier = Modifier.widthIn(max = 400.dp))                 // never full-width on tablet
if (wsc.isCompact) Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) { Box(Modifier.fillMaxSize().background(background)) { content() } }  // full-screen takeover
else Dialog(onDismiss) { Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = 560.dp)) { content() } }
```

**Foldables** (`FoldingFeature` from `WindowInfoTracker.windowLayoutInfo().displayFeatures`)
```kotlin
val folding = rememberFoldingFeature()   // filterIsInstance<FoldingFeature>().firstOrNull()
val isTableTop  = folding?.orientation == FoldingFeature.Orientation.HORIZONTAL
val isSeparating = folding?.isSeparating == true
when {
  folding?.state == FoldingFeature.State.HALF_OPENED && folding.orientation == FoldingFeature.Orientation.HORIZONTAL -> TabletopLayout() // content above, controls below hinge
  folding?.state == FoldingFeature.State.FLAT -> ExpandedLayout()   // fully unfolded = large tablet
  else -> CompactLayout()
}
// via adaptive info: currentWindowAdaptiveInfo().windowPosture.hingeList
```
Tabletop: `Box(Modifier.weight(1f)) { VideoPlayer() }` + `Box(Modifier.weight(0.4f).background(surfaceContainerLow)) { PlaybackControls() }` (thumb-reachable controls, optional hinge guide separator).

**Large screen quality requirements (Play featuring)**
- [ ] App does not crash or show blank screen on large screen
- [ ] Usable in all orientations (no orientation lock unless camera/game)
- [ ] No fixed-size windows requiring scroll to use the app
- [ ] Multi-window (split-screen) works correctly
- [ ] Layout adapts meaningfully at 600dp+ — not a stretched phone UI
- [ ] Keyboard and mouse input handled (no touch-only gesture as the only option)
- [ ] `android:resizeableActivity="true"` (default true from API 24)
- [ ] Save/restore UI state — rotation recreates the Activity
- [ ] Verify with `@Preview(name = "Phone", device = Devices.PHONE)` + `FOLDABLE` + `TABLET` + `DESKTOP` (Compose Preview Screenshot Testing / `@PreviewTest`)

Adaptive app bars: `exitUntilCollapsedScrollBehavior` (hides on scroll down, stays hidden until top) vs `enterAlwaysScrollBehavior` (shows immediately on scroll up); each top-level screen manages its own app bar state independently.

**M3 component correctness that keeps the system coherent** (same skill bucket, condensed): `TopAppBar`/`CenterAlignedTopAppBar`/`MediumTopAppBar`/`LargeTopAppBar` with `colors = TopAppBarDefaults.topAppBarColors(containerColor = surface, scrolledContainerColor = surfaceContainer)`; `SegmentedButton` instead of `RadioButton` for 2–5 options (`SingleChoiceSegmentedButtonRow`, `MultiChoiceSegmentedButtonRow`, `SegmentedButtonDefaults.itemShape(index, size)`); `FilterChip`/`AssistChip`/`InputChip`/`SuggestionChip`; `SearchBar` with `SearchBarDefaults.InputField`; `ExposedDropdownMenuBox` + `menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)`; `BadgedBox` + `Badge` (99+ cap); `PullToRefreshBox`; `DatePickerDialog`/`rememberDatePickerState`; FAB variants (`FloatingActionButton`, `ExtendedFloatingActionButton(expanded = !isScrollingDown)`, `LargeFloatingActionButton`, `SmallFloatingActionButton`); `ListItem`; `ModalBottomSheet` vs `BottomSheetScaffold`; `SnackbarHost`. Chips use `FilterChipDefaults.IconSize`/`AssistChipDefaults.IconSize`/`InputChipDefaults.IconSize`. M2→M3: `Colors`→`ColorScheme`, `BottomNavigation`→`NavigationBar`, `ModalBottomSheetLayout`→`ModalBottomSheet`, `ModalDrawer`→`ModalNavigationDrawer`, `BackdropScaffold`→`BottomSheetScaffold`; M3 `Scaffold` has no `drawerState`.

---

### Edge-to-Edge & Insets

Edge-to-edge is **mandatory on API 35/36** (`enableEdgeToEdge()`; `windowOptOutEdgeToEdgeEnforcement` is disabled on API 36; do not set `fitsSystemWindows` in XML; do not assume the content area excludes system bars). Project **must target SDK 35+**. "Status bar and nav bar are part of the design."

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()            // before setContent; for EVERY Activity
        setContent { AppTheme { Scaffold(Modifier.fillMaxSize()) { innerPadding -> MainNavigation(Modifier.padding(innerPadding)) } } }
    }
}
```
```xml
android:windowSoftInputMode="adjustResize"   <!-- every Activity using a soft keyboard -->
<!-- NEVER SOFT_INPUT_ADJUST_RESIZE (deprecated) -->
```

**Apply insets exactly ONE way (avoid double padding)**
1. **PREFERRED** — `Scaffold` `innerPadding` passed to content, and consumed by scrollables:
```kotlin
Scaffold { innerPadding -> LazyColumn(Modifier.fillMaxSize().consumeWindowInsets(innerPadding), contentPadding = innerPadding) { } }
```
2. **PREFERRED** — rely on Material 3's own inset handling: `TopAppBar`, `SmallTopAppBar`, `CenterAlignedTopAppBar`, `MediumTopAppBar`, `LargeTopAppBar`, `BottomAppBar`, `ModalDrawerSheet`, `DismissibleDrawerSheet`, `PermanentDrawerSheet`, `ModalBottomSheet`, `NavigationBar`, `NavigationRail` manage their safe areas. (M2: pass `windowInsets = AppBarDefaults.topAppBarWindowInsets` to the app bar — **do NOT** pad the parent, which stops the bar's background drawing into the system bar area.)
3. Padding modifiers outside a Scaffold: `Modifier.safeDrawingPadding()`, `windowInsetsPadding(WindowInsets.safeDrawing)`.
4. Deeply nested/over-padded trees: `Modifier.fitInside(WindowInsetsRulers.SafeDrawing.current)`.
5. Element sized like a system bar (scrim, header): `Modifier.windowInsetsTopHeight(WindowInsets.systemBars)`, `WindowInsets.navigationBars.asPaddingValues()`.

Individual modifiers: `safeDrawingPadding` (status + nav + cutout) · `statusBarsPadding` (app bars/top content) · `navigationBarsPadding` (bottom sheets, FABs) · `imePadding` (scroll containers with text fields) · `systemGesturesPadding` (edge/back-gesture zones) · `windowInsetsPadding(WindowInsets.x)`.

**Adaptive scaffolds caveat**: `NavigationSuiteScaffold`, `ListDetailPaneScaffold` etc. manage their own rail/bar safe areas but **do not propagate `PaddingValues`** — apply insets to individual screens/components (list `contentPadding`, FAB padding). **DO NOT** put `safeDrawingPadding()` on the `NavigationSuiteScaffold` parent — it clips and defeats edge-to-edge.

**Correct Scaffold + content pattern**
```kotlin
Scaffold(topBar = { AppTopBar() }, bottomBar = { AppBottomBar() },
    floatingActionButton = { FloatingActionButton(onClick = { }, modifier = Modifier.navigationBarsPadding()) { … } },
    contentWindowInsets = WindowInsets(0)) { paddingValues ->
    LazyColumn(contentPadding = paddingValues + PaddingValues(horizontal = AppSpacing.M), modifier = Modifier.fillMaxSize()) { … } }
```

**Lists**: apply insets to `contentPadding`, **never** as `Modifier.padding()` on the list's parent — padding clips content and stops it scrolling behind the bars.

**IME / keyboard** (must be verified for every `TextField`, `OutlinedTextField`, `BasicTextField`): add `android:windowSoftInputMode="adjustResize"`, keep focus on the field, and choose ONE:
```kotlin
// RIGHT — contentWindowInsets already contains IME insets → innerPadding handles it, consume to avoid doubling
Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { inner -> Column(Modifier.padding(inner).consumeWindowInsets(inner).verticalScroll(…)) { } }
// RIGHT — fitInside works regardless of contentWindowInsets (PREFERRED: less jank than imePadding)
Scaffold { inner -> Column(Modifier.padding(inner).consumeWindowInsets(inner).fitInside(WindowInsetsRulers.Ime.current).verticalScroll(…)) { } }
// RIGHT — default contentWindowInsets lacks IME → imePadding() adds it (BEFORE verticalScroll)
Scaffold { inner -> Column(Modifier.padding(inner).consumeWindowInsets(inner).imePadding().verticalScroll(…)) { } }
// WRONG — IME applied twice (innerPadding + imePadding) → excess padding
Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { inner -> Column(Modifier.padding(inner).imePadding().verticalScroll(…)) { } }
// WRONG — IME covers the field (default contentWindowInsets has no IME insets, and nothing adds them)
Scaffold { inner -> Column(Modifier.padding(inner).verticalScroll(…)) { } }
// WRONG outside Scaffold — asPaddingValues() doesn't consume, so inner imePadding() doubles
Box(Modifier.padding(WindowInsets.safeDrawing.asPaddingValues())) { Column(Modifier.imePadding()) { } }
// RIGHT outside Scaffold — safeDrawingPadding() consumes
Box(Modifier.safeDrawingPadding()) { Column(Modifier.imePadding()) { } }
```

**System bar legibility**: if you use `enableEdgeToEdge` from **`WindowCompat`**, you MUST set `isAppearanceLightStatusBars = !darkTheme` and `isAppearanceLightNavigationBars = !darkTheme` (recommended in the theme file, guarded by `!view.isInEditMode`). Do NOT do it when using `ComponentActivity.enableEdgeToEdge()`, which handles icon colors automatically. With a `Scaffold`/`NavigationSuiteScaffold` bottom bar, set `window.isNavigationBarContrastEnforced = false` (SDK 29+) so the system doesn't add a translucent scrim under your bar colors.

**Status bar protection scrim** (keep icons legible over content that scrolls up):
```kotlin
Spacer(Modifier.fillMaxWidth().height(with(LocalDensity.current) { (WindowInsets.statusBars.getTop(this) * 1.2f).toDp() })
    .background(Brush.verticalGradient(listOf(color.copy(alpha = 1f), color.copy(alpha = 0.8f), Color.Transparent))))
```

**Display cutouts**: `WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LocalLayoutDirection.current)` (or `displayCutoutPadding()`).

**Full-screen dialogs**: set `decorFitsSystemWindows = false` in `DialogProperties` alongside `usePlatformDefaultWidth = false` + `Modifier.fillMaxSize()`.

Edge-to-edge checklist: every Activity calls `enableEdgeToEdge()` · `adjustResize` in manifest · every text field has an IME-aware parent · first/last list item clears system bars via `contentPadding` · FABs draw above the nav bar (in a Scaffold or via `safeDrawingPadding()`) · project builds (`./gradlew build`). UI must never leave "an unclaimed grey band" above the status bar or "a white rectangle" at the bottom.

---

### Predictive Back

From **Android 16 (API 36)** predictive back system animations are enabled by default: `onBackPressed` is no longer called and `KeyEvent.KEYCODE_BACK` is no longer dispatched.

- [ ] `<application android:enableOnBackInvokedCallback="true">` (API 33–35 must set it explicitly; API 36+ defaults to true)
- [ ] Never set `enableOnBackInvokedCallback="false"` as a permanent fix — temporary escape hatch only
- [ ] Compose: `BackHandler` from `androidx.activity.compose` for ALL back handling; non-Compose: `onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { }` (API 33+), registered ahead of time so the system can play the prediction
- [ ] Pane scaffolds: `BackHandler(navigator.canNavigateBack()) { navigator.navigateBack() }`, or `ThreePaneScaffoldPredictiveBackHandler(...)`, or use the `Navigable*Scaffold` variants which handle it
- [ ] NavHost 2.8+ handles it automatically for `composable()` destinations; Navigation 3 `NavDisplay(predictivePopTransitionSpec = { … })`; `ListDetailSceneStrategy` handles pane arrangement + predictive back
- [ ] Shared element transitions scrub in reverse with no extra code beyond the manifest flag
- [ ] Custom preview animation: `SeekableTransitionState` / `animateFloatAsState(1f, spring(stiffness = Spring.StiffnessMedium), label = "screenScale")` + `graphicsLayer { scaleX = scale; scaleY = scale }`

**Do NOT**: override `onBackPressed()` · dispatch/consume `KEYCODE_BACK` · rely on `onBackPressedDispatcher` legacy paths.

---

### Reduced Motion

```kotlin
// Compose (androidx.compose.ui.accessibility):
val reducedMotion = LocalReducedMotion.current
AnimatedVisibility(visible = visible,
    enter = if (reducedMotion) EnterTransition.None else fadeIn() + slideInVertically(),
    exit  = if (reducedMotion) ExitTransition.None  else fadeOut() + slideOutVertically()) { Content() }
// Alternative spec-level fallback: animationSpec = snap()  (instant, no animation — also for low-power mode)
// Or branch the whole subtree:
val reduced = LocalContext.current.getSystemService<AccessibilityManager>()?.isEnabled == true
if (reduced) { if (visible) content() } else AnimatedVisibility(visible, expandVertically() + fadeIn(), shrinkVertically() + fadeOut()) { content() }
@Composable fun rememberReducedMotion(): Boolean {
    val am = LocalContext.current.getSystemService<AccessibilityManager>()
    return remember { am?.isEnabled == true && am.isTouchExplorationEnabled }
}
```
Rules: check the system setting for every enter/exit and every infinite animation; `snap()` / `EnterTransition.None` / `ExitTransition.None` are the accessible substitutes; loading indicators must not hide context unnecessarily; **avoid rapid flashing or sweeping shimmer**; error messages must be text, not color only; support logical keyboard/focus order; all touch targets ≥ 48×48dp (wrap 24dp icons in a 48dp container or use `Modifier.minimumInteractiveComponentSize()`; icon-only buttons need `contentDescription`); custom interactive components declare `Modifier.semantics { role = Role.Button }`; merge card content with `Modifier.semantics(mergeDescendants = true) {}`; use `rememberRipple(bounded = false)` for unbounded icon ripples; text-on-background must meet WCAG AA (4.5:1 body, 3:1 large/UI) at large font scale (`@Preview(fontScale = 1.5f)`) and RTL (`@Preview(locale = "ar")`).

---

### iOS-Smooth Feel

The bar for "Apple-grade" on Android: **every screen passes three tests** —
1. **Scroll-stop test** — would a designer pause on this? Something considered: a hierarchy that breathes, a transition revealing spatial relationship, a surface treatment that says "we care."
2. **Feel test** — does it respond to touch like a physical object? Spring physics on press, haptic coordination with state changes, skeleton loaders that mirror real content.
3. **Motion test** — do transitions communicate where content came from? Shared elements = spatial memory.

Concrete recipe:
- `spring()` physics tuned per context, never unexamined defaults; interruption keeps velocity (the single biggest contributor to "buttery").
- Everything that moves stays in the **draw phase**: `graphicsLayer`, `drawBehind`, `Canvas`, `Modifier.offset { }` — no per-frame recomposition, no animating padding/size.
- Enter decelerates, exit accelerates; new content enters *before* old content finishes leaving.
- Bounce is a spice, not a base note: `DampingRatioLowBouncy`/`NoBouncy` for navigation, `MediumBouncy` only for success/celebration.
- Continuous, non-blocking loading: skeleton geometry that matches the final layout, min-height slots, old content kept while refreshing — no spinner-then-jump.
- Shared element hero transitions for every list→detail navigation.
- Tactile press feedback: ripple for contained surfaces, scale indication (`0.94f`, `StiffnessHigh`) for cards/images — "no visible feedback" must be a deliberate choice, never an oversight.
- Haptics coordinated with meaningful state transitions: the primary action (FAB or equivalent) triggers `HapticFeedbackType.LongPress` or `TextHandleMove` on press (`LocalHapticFeedback.current.performHapticFeedback(...)`).
- Surfaces that catch light: tonal elevation hierarchy, at least one non-flat visual element (gradient, brush fill, layered radial mesh, custom canvas drawing) where flat color would be weak; a considered background instead of default system white/grey.
- Depth and life where standard components stop: parallax hero (`derivedStateOf` + `graphicsLayer`), `MeshBackground` (stacked `Brush.radialGradient` in `drawBehind`), `BreathingGradientBackground`, `GlassCard` (translucent surface + `.paint()` texture + gradient border), gradient text via `drawWithCache` + `BlendMode.SrcAtop`, `ArcProgressRing` with `Brush.sweepGradient`, path-morphing shapes.
- Type: display face for headlines + humanist sans for body; scale hierarchy legible in 2 seconds.
- Edge-to-edge always; system bars treated as part of the composition.
- Duration discipline: micro-interactions 50–200ms; nothing everyday over 400ms; nothing at all over 600ms.
- Verify on device with `adb shell dumpsys gfxinfo` (reset → interact → read frame timings) for jank; MCP/screenshot loops catch real inset behavior, animation timing, system bar styling, density quirks.

---

### UI Excellence Checklist

Run against every screen before it is marked done. Every item is binary — "good enough" is not a pass.

**Visual Design**
- [ ] All colors come from `MaterialTheme.colorScheme.*` or a named `CompositionLocal` — zero hardcoded hex values outside the primitive token file
- [ ] All typography uses `MaterialTheme.typography.*` — zero hardcoded `fontSize`, `fontWeight`, or `FontFamily` in composables
- [ ] All spacing values are multiples of 4dp, referenced via `AppSpacing.*` tokens — zero hardcoded `Modifier.padding(Xdp)` calls with non-4dp-multiples
- [ ] Surfaces use appropriate elevation tinting — cards are not all flat at the same `tonalElevation`; depth hierarchy is visible
- [ ] The background is not the default system white or grey — a considered surface treatment exists (gradient, tonal background, texture, or intentional `surfaceVariant`)
- [ ] At least one non-flat visual element exists where flat color would be visually weak: gradient, brush fill, layered radial background, or custom canvas drawing
- [ ] The type scale creates a hierarchy that reads at a glance — a senior designer could identify the most important information within 2 seconds

**Motion & Transitions**
- [ ] Navigation between related content (list → detail, card → expanded) uses shared element transitions (`sharedElement()` or `sharedBounds()`), not instant or slide-only navigation
- [ ] Enter animations use ease-out or spring physics — content arrives with deceleration, not abruptly
- [ ] Exit animations use ease-in — content leaves by accelerating, not by fading uniformly
- [ ] List items that arrive after a navigation or load stagger their entry animations (per-item delay, capped at 300ms total)
- [ ] The primary loading state is a skeleton screen that mirrors the geometry of the `Ready` state — not a centred `CircularProgressIndicator` on a blank background
- [ ] `LinearProgressIndicator` is absent as the sole loading affordance — it may exist as a secondary indicator (e.g., top-of-screen during background refresh), but not as the primary one

**Interaction Design**
- [ ] Every interactive element has visible, contextually appropriate press feedback — ripple for contained surfaces, scale indication for cards/images, no visible feedback is a deliberate choice that has been made consciously (not an oversight)
- [ ] All touch targets are minimum 48dp × 48dp — icons with `size(24.dp)` that are clickable are wrapped in a 48dp container
- [ ] The primary action (FAB or equivalent) triggers `HapticFeedbackType.LongPress` or `TextHandleMove` on press — haptics coordinate with the visual confirmation
- [ ] Any swipeable or draggable content has a visible affordance (drag handle, peek of content behind, or clear directional indicator) — users don't need to discover the gesture by accident

**Edge-to-Edge & Insets**
- [ ] The status bar area is styled or content extends behind it — the status bar is never an unclaimed grey band above the UI
- [ ] The navigation bar area is styled or content extends behind it — the nav bar is not a white rectangle at the screen bottom disconnected from the app's palette
- [ ] All scrollable content has `WindowInsets.navigationBars` padding (or `safeDrawingPadding()`) applied correctly — the last list item is not obscured by the system nav bar
- [ ] The keyboard does not cover text input fields — `imePadding()` or `WindowInsets.ime` is applied to the scroll container containing the input

**Accessibility**
- [ ] Every `AsyncImage` / `Image` has a meaningful `contentDescription`, or is marked `contentDescription = null` with a documented reason (decorative) — no image has a blank or missing description
- [ ] Every custom interactive component (non-standard button, card, toggle) declares its semantic role via `Modifier.semantics { role = Role.Button }` or equivalent
- [ ] Focus traversal order for keyboard and TalkBack navigation is logical — tested by navigating the screen with TalkBack enabled or verified via `uiautomator dump` hierarchy inspection
- [ ] All text-on-background color combinations meet WCAG AA minimum contrast ratio (4.5:1 for body text, 3:1 for large text / UI components) — verified against the design system color palette

**Preview Coverage** (every public `@Composable`, no exceptions)
- [ ] A `@Preview` exists showing the screen/component in **light theme** with realistic content (not placeholder text)
- [ ] A `@Preview` exists showing the screen/component in **dark theme** (`uiMode = Configuration.UI_MODE_NIGHT_YES`)
- [ ] A `@Preview` exists with **large font scale** (`fontScale = 1.5f`) — text doesn't overflow bounds or clip
- [ ] A `@Preview` exists with **RTL layout** (`locale = "ar"`) — layout mirrors correctly and no element is hardcoded to a specific side

```kotlin
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Large font", fontScale = 1.5f, showBackground = true)
@Preview(name = "RTL", locale = "ar", showBackground = true)
@Composable private fun ProductCardPreview() { AppTheme { ProductCard(product = PreviewData.sampleProduct, onClick = {}) } }
```
Preview data objects use realistic values — "a preview with blank text teaches you nothing."

**Component/screen structure supporting the checklist**: one public screen composable + private `…ScreenContent` (state-hoisted, testable without the ViewModel); slot-based APIs (`content: @Composable ColumnScope.() -> Unit`) for containers, explicit params when the component knows its content; state hoisting in 3 levels (fully stateless → `rememberXxxState()` internal → ViewModel-owned via `collectAsStateWithLifecycle()`); `innerPadding` from `Scaffold` always applied to the content root; touch targets ≥48dp; category-appropriate visual direction (calm/conservative for finance, soft/generous for health, bright + 18sp+ type + 56dp targets for kids, high-contrast dense modes for productivity, brand-forward media-rich for social) — playful palette on finance, dense dashboards on meditation apps, tiny targets on kids flows, clownish UI on enterprise are mismatches.

---

### Anti-Patterns

**Motion / animation**

❌ `tween(300)` for spatial motion — use `spring()` for anything that moves
❌ No `label` parameter on animate* — hard to debug in profiler
❌ `AnimatedContent` without `contentKey` — wrong composable reused on state change
❌ Stacking animations without `SizeTransform` on count changes — layout jumps
❌ No reduce motion check — bad experience for accessibility users
❌ Hardcoded `300` ms — always use `Duration.*` tokens
❌ `FastOutSlowInEasing` everywhere — use M3 easing: `AppEasing.EmphasizedDecel` for enter, `AppEasing.EmphasizedAccel` for exit
❌ InfiniteTransition without performance check — pauses when app is backgrounded automatically, but don't add them to every screen

| Anti-pattern | Why | Fix |
|---|---|---|
| Animation state in ViewModel | Pollutes business state | Local `animate*AsState` or `Animatable` |
| `Modifier.scale()`/`.offset()` | Recomposition every frame | `graphicsLayer { scaleX = ...; translationX = ... }` |
| Animating every change | Jittery UI | Animate meaningful transitions only |
| `animateContentSize` after size modifiers | No effect | Place BEFORE `size`/`fillMaxWidth` |
| Outer variable in AnimatedContent | Stale during exit | Use lambda parameter |
| `tween`/`snap` everywhere | Jarring interruption | Prefer `spring` |
| Animating padding/size every frame | Expensive Layout phase | Prefer `graphicsLayer` transforms |

Additional: `val animatable = Animatable(0f)` not wrapped in `remember` (recreated every recomposition) · animating state in the composition phase (`position += 10f` in body → infinite recomposition loop; do it in `LaunchedEffect`) · using a fade when the content must be unmounted · bounce (`MediumBouncy`) on everyday navigation · more than 300ms of total stagger · >600ms single motion · sequential out-then-in when the intent was "something new is here".

**Design system / tokens**

❌ Spacing not on 4dp grid — `padding(13.dp)` use Spacing.sm or Spacing.md
❌ `fontSize = 20.sp` hardcoded — `MaterialTheme.typography.titleLarge`
❌ `Color(0xFF333333)` hardcoded — `MaterialTheme.colorScheme.onSurface`
❌ `cornerRadius = 12.dp` inline — `MaterialTheme.shapes.medium`
❌ `tween(300)` hardcoded — `MotionTokens.effectsStandard()`
❌ Different button heights per screen — always 48.dp
❌ No dark mode testing — check every component with `darkTheme = true`
❌ Dynamic color disabled — enable for Android 12+, fallback palette for older
❌ `Color.White` in dark mode — `MaterialTheme.colorScheme.surface`
❌ `Card(elevation = 4.dp)` — use tonal containers, not shadows: `surfaceContainerHigh`
❌ Arbitrary radii (7dp/15dp/22dp) · one radius for everything · image corner bleeding past the card radius (missing `clip`)
❌ `shadowElevation = 8.dp` everywhere (Material 2 style) · `Modifier.shadow()` (bypasses M3) · same elevation for everything (no depth hierarchy)
❌ Theme defined inside a specific screen · dark scheme missing entirely · `lightColorScheme()` with no arguments (all-M3 purple, no brand)

**Color / theming — "Common Theming Mistakes — and Their Fixes"**

Mistake 1 — Using only `primary` and ignoring the container roles (every element fights for attention; differentiate: button `primary`, selected chip `primaryContainer`, card `surfaceVariant`).
Mistake 2 — Hardcoding `Color.White`/`Color.Black`/`Color.Gray` for text (breaks in dark mode; use `onSurface`/`onSurfaceVariant`).
Mistake 3 — Defining the dark scheme as a colour-inverted copy of light ("Dark mode is not light mode inverted… The primary color in dark mode is typically the `80`-tone… Always derive dark mode values from the Material Theme Builder output").
Mistake 4 — Placing `onPrimary` text on a `primaryContainer` background ("white on light pastel — invisible"; use `onPrimaryContainer`).
Mistake 5 — Leaving `tertiary` at the Material baseline default ("stray purple accents"; always set all three accent families).
Mistake 6 — Using `alpha` on `onSurface` when `onSurfaceVariant` is the correct choice ("manual alpha is fragile; contrast may fail at some device display settings").

Never: hardcode colors or text sizes · assume light theme · use deprecated theming APIs (`androidx.compose.material.MaterialTheme`) · ignore the system theme without an explicit user override · skip dark-mode contrast testing · call `isSystemInDarkTheme()` in ViewModels · add custom color attributes without light/dark variants · use `Color.Unspecified` without a fallback · test the theme only in the emulator (real devices, different wallpapers).

**M3 components**

❌ M2 components (`androidx.compose.material`) mixed with M3 — causes visual inconsistency
❌ `nestedScroll` missing with `LargeTopAppBar` — scroll behavior won't work
❌ Using `RadioButton` for 2-4 options — use `SegmentedButton`
❌ Drop shadows on cards (`elevation = 4.dp`) — use `surfaceContainer` colors instead
❌ No `contentDescription` on icon-only buttons — accessibility violation
❌ `FilterChip` leading icon not sized with `FilterChipDefaults.IconSize` — renders too large
❌ `SegmentedButton` without `SegmentedButtonDefaults.itemShape()` — corners wrong
❌ `SearchBar` in older API style — use new `inputField` parameter (M3 1.3+)

**Adaptive layout**

❌ Hardcoded `BottomNavigation` — use `NavigationSuiteScaffold`
❌ Single-column layout on tablet — use `ListDetailPaneScaffold` for list+detail
❌ `LazyColumn` with no grid alternative on large screen — use `GridCells.Adaptive`
❌ Full-width text — cap at `720.dp` with `widthIn(max = 720.dp)`
❌ Same `ModalBottomSheet` on tablet — switch to side sheet on medium+
❌ Fixed-size dialog — use `widthIn(max = 400.dp)` always
❌ Ignoring `FoldingFeature` — check `windowPosture.hingeList` for foldables
❌ No `BackHandler` in list-detail — handle back for phone navigation
Plus: `safeDrawingPadding()` on the `NavigationSuiteScaffold` parent (clips, breaks edge-to-edge) · a detail screen showing a back arrow while part of a list-detail layout · keeping full-screen detail mode on large screens · `ListDetailPaneScaffold` in new Nav3 apps (use `SceneStrategy`) · `Grid` nested inside the `Column` it was meant to replace · relying on orientation locks / `resizableActivity="false"` at ≥600dp on API 36 (system ignores them) · touch-only gestures as the only input path.

**Motion-choreography refusals (from the UI mandate)**

Default grey scaffold backgrounds · hardcoded hex colors in composables · instant navigation between related content · `LinearProgressIndicator` as the sole loading state · cards with identical elevation everywhere · spacing not on the 4dp grid · a centred spinner where a skeleton belongs · shimmer whose geometry does not match the content it replaces · `Modifier.padding()` on a scrollable's parent instead of `contentPadding` · IME insets applied twice.

## DOMAIN: Navigation (Compose, Nav3-first)

Nav 3 = `androidx.navigation3` (user-owned back stack, Compose/CMP-first). Nav 2 = `androidx.navigation:navigation-compose` (`NavHost`/`NavController`, **not deprecated**, fully supported fallback). NavigationEvent = `androidx.navigationevent` (predictive back foundation layer).

### Type-Safe Routes

**Rule: all routes are `@Serializable` — zero string routes.** Nav 3 additionally requires routes to implement `NavKey`. Why: compile-time type safety; string routes like `"detail/$id"` crash at runtime if you typo the arg name, serializable routes fail at compile time.

```kotlin
// Nav 3 — keys: @Serializable data classes/objects implementing NavKey
@Serializable sealed interface AppRoute : NavKey          // group with sealed interfaces for type safety
@Serializable data object Home : AppRoute
@Serializable data class Details(val id: String) : AppRoute
@Serializable data object Settings : AppRoute

// Nav 2 (2.8+) — same types, WITHOUT NavKey
@Serializable data object Home
@Serializable data class Detail(val itemId: String)
```

```kotlin
// WRONG (Nav 2) — string routes: typo-prone, no type safety, args nullable/untyped
navController.navigate("detail/$itemId")
composable("detail/{itemId}") { backStackEntry ->
    backStackEntry.arguments?.getString("itemId")
}
// RIGHT (Nav 2 2.8+)
navController.navigate(ItemDetailRoute(itemId))
composable<ItemDetailRoute> { entry ->
    val route: ItemDetailRoute = entry.toRoute()
    ItemDetailScreen(itemId = route.itemId)
}
// RIGHT (Nav 3) — the typed key is passed straight to the entry lambda; no toRoute()
entry<Details> { key -> DetailScreen(id = key.id) }
```

Centralize route definitions in one place (`navigation/Routes.kt`, or a `:core:navigation` module so feature modules navigate to each other without importing each other). All routes in `:core:navigation`; `@Serializable` is required for back stack state saving and process death recovery.

**Rules for route data classes:**
- Only primitives and `String` as parameters — no complex objects. Pass IDs and simple primitives; fetch the full object from ViewModel/Repository on the destination screen.
- Use `Long` for IDs; never pass entity objects across destinations.
- `String?` for optional params; avoid nullable primitives (use default values instead). The API supports nullable types (e.g. `data class Search(val query: String?)`) and provides default values automatically.
- For routes without parameters always use `object` (or `data object`) instead of `class` — avoids unnecessary allocations.
- For large apps group routes using a sealed interface/class; mark the sealed type `@Immutable` + `@Serializable`.

```kotlin
// WRONG — serializing entire object through nav args
navController.navigate(AccountDetail(account = entireAccountObject))
// RIGHT — pass ID, fetch in destination ViewModel
@Serializable data class AccountDetail(val accountId: String)

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    private val repo: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // Type-safe extraction — survives process death and configuration changes (Nav 2)
    private val accountId: String = savedStateHandle.toRoute<AccountDetail>().accountId
    val account: StateFlow<AuthAccount?> = repo.getAccountById(accountId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
```

**Custom types in route args:** provide a custom `KSerializer`. In CMP, prefer `String` paths or `expect/actual` wrappers. Nav 2 complex data classes need a custom `NavType`:

```kotlin
// Nav 2 — custom NavType for complex types
val SearchFilterType = object : NavType<SearchFilter>(isNullableAllowed = false) {
    override fun get(bundle: Bundle, key: String): SearchFilter? =
        Json.decodeFromString(bundle.getString(key) ?: return null)
    override fun parseValue(value: String): SearchFilter = Json.decodeFromString(Uri.decode(value))
    override fun put(bundle: Bundle, key: String, value: SearchFilter) {
        bundle.putString(key, Json.encodeToString(value))
    }
}
composable<Search>(typeMap = mapOf(typeOf<SearchFilter>() to SearchFilterType)) { /* ... */ }
```

**Testing routes type-safely:** `navController.currentBackStackEntry?.hasRoute<T>()` to check the current destination in UI tests.

### Nav3 Architecture

**Nav 3 gives you:** conventions for modeling a back stack (each entry = content the user navigated to); a UI that automatically updates with back stack changes (including animations); a scope for items in the back stack so state is retained while an item is on the stack; an adaptive layout system that displays multiple destinations at once and switches layouts seamlessly; a mechanism for content to communicate with its parent layout (metadata). Improvements over Nav 2: simpler Compose integration, full control of the back stack, layouts that read more than one destination at once.

**Four building blocks:**
1. **Keys** — `@Serializable` types identifying destinations (`NavKey`)
2. **Back stack** — a `SnapshotStateList` you own and mutate directly
3. **NavEntry** — wraps a key with composable content and optional metadata
4. **NavDisplay** — observes back stack, resolves keys via entry provider, picks a Scene, renders

```text
User interaction
  -> backStack.add(key) / backStack.removeLastOrNull()
  -> NavDisplay observes change
  -> entryProvider resolves key -> NavEntry
  -> SceneStrategy picks layout
  -> Scene renders content
```

| Type | Role |
|---|---|
| `NavKey` | Marker interface for serializable destination keys |
| `NavEntry` | Key + composable content + metadata map |
| `NavDisplay` | Observes back stack, manages scenes and animations |
| `Scene` / `SceneStrategy` | Decides layout (single pane, list-detail, dialog) |
| `NavEntryDecorator` | Cross-cutting concern (ViewModel scoping, saveable state) |

**Dependencies:**

```toml
[versions]
nav3Core = "1.1.7"
lifecycleViewmodelNav3 = "2.11.0"   # only if screens depend on ViewModels
[libraries]
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "nav3Core" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "nav3Core" }
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycleViewmodelNav3" }
# Nav 3 requires minSdk 23, compileSdk 36, and the KotlinX Serialization plugin:
#   org.jetbrains.kotlin.plugin.serialization + kotlinx-serialization-json
```

Newer library — verify artifact stability before production use. All new Compose projects should use Nav 3 as the modern navigation API.

**Minimal NavDisplay (raw entryProvider lambda — `when` over keys):**

```kotlin
val backStack = remember { mutableStateListOf<Any>(RouteA) }   // no persistence — prototyping only
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },                  // NEVER omit onBack
    entryProvider = { key ->
        when (key) {
            is RouteA -> NavEntry(key) { /* content */ }
            is RouteB -> NavEntry(key) { ContentBlue("Route id: ${key.id}") }
            else -> error("Unknown route: $key")
        }
    },
)
```

**Full-featured NavDisplay (`entryProvider` DSL — recommended):**

```kotlin
val backStack = rememberNavBackStack(Home)   // persists across config changes and process death
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),   // preserves rememberSaveable while on stack
        rememberViewModelStoreNavEntryDecorator(),        // per-entry ViewModelStoreOwner
    ),
    sceneStrategy = listDetailStrategy,
    transitionSpec = { slideInHorizontally(initialOffsetX = { it }) togetherWith slideOutHorizontally(targetOffsetX = { -it }) },
    popTransitionSpec = { slideInHorizontally(initialOffsetX = { -it }) togetherWith slideOutHorizontally(targetOffsetX = { it }) },
    entryProvider = entryProvider {
        entry<Home> {
            HomeScreen(onNavigateToDetails = { id -> backStack.add(Details(id)) })
        }
        entry<Details>(metadata = mapOf("pane" to "detail")) { key ->   // typed key arrives in lambda
            DetailScreen(id = key.id, onNavigateBack = { backStack.removeLastOrNull() })
        }
    },
)
```

Pass `metadata` to control scene placement and per-entry animations. `entryProvider` does **not** define parent-child relationships between entries — parent/child is modelled in your navigation state (top-level routes + one stack per top-level route).

**Unidirectional Data Flow pattern (multi-stack apps):** `NavigationState` holds routes/stacks and never modifies itself; `Navigator` handles navigation events by updating `NavigationState`; the UI (`NavDisplay`) observes state and reacts. Create both with the same scope as your former `NavController`. Full code in Back Stack & Persistence. Feature-facing variant — expose a `Navigator` interface per feature (no navigation logic in features), implemented in the app module:

```kotlin
interface ProductsNavigator {
    fun navigateToDetail(productId: String)
    fun navigateBack()
}
// app module implements it against the shared back stack / NavigationState
```

**MVI boundary — the architectural rule (identical for Nav 2 and Nav 3): ViewModels emit semantic effects; the route layer handles navigation.**
- Never call navigation during composition — always in `LaunchedEffect` or event handler callbacks
- Never pass the back stack (Nav 3) or `NavController` (Nav 2) to the ViewModel or leaf composables
- ViewModel emits semantic effects (`NavigateBack`, `OpenDetails(id)`)
- Route/navigation layer translates effects to navigation calls
- Keep navigation logic at the route boundary, not in screens or leaves

```kotlin
sealed interface ItemEffect {
    data object NavigateBack : ItemEffect
    data class OpenDetails(val id: String) : ItemEffect
}
// Nav 3 route layer — manipulates back stack
CollectEffect(viewModel.effect) { effect ->
    when (effect) {
        is ItemEffect.NavigateBack -> backStack.removeLastOrNull()
        is ItemEffect.OpenDetails -> backStack.add(Details(effect.id))
    }
}
```

**Destination lifecycle:** by default each `NavEntry` is provided its own `LifecycleOwner` via `LocalLifecycleOwner.current` — lifecycle-aware components inside an entry are automatically scoped to the `NavEntry`.

```kotlin
LifecycleResumeEffect(Unit) {                 // runs only while entry is RESUMED
    val job = coroutineScope.launch { /* work */ }
    onPauseOrDispose { job.cancel() }
}
LifecycleEventEffect(Lifecycle.Event.ON_START) { /* ON_CREATE/ON_START/ON_RESUME/ON_PAUSE/ON_STOP */ }
// ON_DESTROY is not observable from composables.
```

A screen behind a `DialogSceneStrategy` dialog leaves `RESUMED` (stays `STARTED`) — `LifecycleResumeEffect` pauses work and resumes on dismiss. Wrap navigation click handlers in `dropUnlessResumed` to prevent double-clicks/duplicate pushes during transitions: `Button(onClick = dropUnlessResumed { backStack.add(RouteB("123")) })`.

**Nav 2 vs Nav 3 decision guide:**

| Criterion | Nav 3 (NavDisplay) | Nav 2 (NavHost / NavController) |
|---|---|---|
| Back stack ownership | You own it (`SnapshotStateList`) | Library owns it (`NavController`) |
| Navigation model | List manipulation — `add()`, `removeLastOrNull()` | Imperative — `navigate()`, `popBackStack()` |
| MVI alignment | Natural — back stack is state you mutate | Requires bridging — controller calls in effect handlers |
| Deep link parsing | You parse URIs, construct back stack manually | Built-in `NavDeepLink` parsing |
| Scenes / adaptive layouts | First-class: dialog, bottom sheet, list-detail | Manual: separate composable overlays |
| CMP support | Full (Android, iOS, Desktop, Web) | Android-only (JetBrains forks exist but differ) |
| Maturity | Newer — verify artifact stability for production | Stable, battle-tested |
| Fragment interop | None | Full Fragment/Activity integration |

Use Nav 3 for: new Compose MVI projects; Compose Multiplatform; direct back stack control as state; adaptive layout scenes. Use Nav 2 for: existing `NavHost`/`NavController` codebases; built-in `NavDeepLink` parsing; hybrid Compose + Fragment apps; teams preferring the declarative `NavGraph` DSL. Prefer Nav 3 `NavDisplay` for new MVI-first projects; Nav 2 remains valid for existing codebases.

### Back Stack & Persistence

```kotlin
// Recommended — persists across config changes and process death (keys must be @Serializable + NavKey)
val backStack = rememberNavBackStack(Home)

// Simple — no persistence, prototyping only
val backStack = remember { mutableStateListOf<Any>(Home) }
```

`rememberNavBackStack` returns `NavBackStack<NavKey>`. For a typed subtype, provide your own overload (`androidx.navigation3.runtime.serialization.NavBackStackSerializer` / `NavKeySerializer`; see issuetracker.google.com/issues/463382671):

```kotlin
@Composable
fun <T : NavKey> rememberNavBackStack(vararg elements: T): NavBackStack<T> =
    rememberSerializable(
        serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer())
    ) { NavBackStack(*elements) }
```

**Polymorphic serialization (CMP, non-JVM targets):** needs `SavedStateConfiguration` plus a `SerializersModule` with polymorphic `NavKey` subclasses (e.g. `subclassesOfSealed<AppRoute>()`).

**Core class (persists config changes and process death). AI-agent rule, verbatim: `rememberSerializable` is correct. Do not change it to `rememberSaveable`.**

```kotlin
@Composable
fun rememberNavigationState(startRoute: NavKey, topLevelRoutes: Set<NavKey>): NavigationState {
    val topLevelRoute = rememberSerializable(
        startRoute, topLevelRoutes,
        serializer = MutableStateSerializer(NavKeySerializer())
    ) { mutableStateOf(startRoute) }
    val backStacks = topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }
    return remember(startRoute, topLevelRoutes) {
        NavigationState(startRoute = startRoute, topLevelRoute = topLevelRoute, backStacks = backStacks)
    }
}

class NavigationState(
    val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    var topLevelRoute: NavKey by topLevelRoute
    // "exit through home": start stack is ALWAYS in use; at most one other stack.
    val stacksInUse: List<NavKey>
        get() = if (topLevelRoute == startRoute) listOf(startRoute) else listOf(startRoute, topLevelRoute)
}

@Composable
fun NavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>
): SnapshotStateList<NavEntry<NavKey>> {
    val decoratedEntries = backStacks.mapValues { (_, stack) ->
        val decorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>())
        rememberDecoratedNavEntries(backStack = stack, entryDecorators = decorators, entryProvider = entryProvider)
    }
    return stacksInUse.flatMap { decoratedEntries[it] ?: emptyList() }.toMutableStateList()
}

class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route                       // top level route: just switch to it
        } else {
            state.backStacks[state.topLevelRoute]?.add(route) // child route: push on current stack
        }
    }
    fun goBack() {
        val currentStack = state.backStacks[state.topLevelRoute]
            ?: error("Stack for ${state.topLevelRoute} not found")
        if (currentStack.last() == state.topLevelRoute) {
            state.topLevelRoute = state.startRoute            // at base -> back to start stack
        } else {
            currentStack.removeLastOrNull()
        }
    }
}
// usage: same scope as your former NavController
val navigationState = rememberNavigationState(startRoute = Home, topLevelRoutes = setOf(Home, Search, Profile))
val navigator = remember { Navigator(navigationState) }
```

**Single-stack manipulation patterns:**

```kotlin
backStack.add(Details("123"))                                   // forward
backStack.removeLastOrNull()                                    // back
backStack.removeAll { it is Details }; backStack.add(Details(newId)) // replace duplicates
backStack.clear(); backStack.addAll(listOf(Home, Details(deepLinkId))) // synthetic stack (deep link / logout)
while (backStack.size > 1) backStack.removeLast(); backStack[0] = targetKey // tabs: pop to root, swap root key
```

Detail-pane dedupe (list-detail): `removeIf { it is ConversationDetail }; add(detailRoute)` before adding a detail — note keeping multiple detail entries can be desirable when multiple detail panes show at once. Duplicate avoidance for paged content: `if (route !in backStack) add(route)`.

**Multiple back stacks — key behaviors:**
- "Exit through home": entries of the start stack are *always* in the rendered list; navigating top-level A→B→C results in entries for A+C (B's entries are removed from the *rendered* list).
- Even if a top-level route is not in use, its state is still retained.
- Each top-level route gets its own `SaveableStateHolderNavEntryDecorator` (`rememberDecoratedNavEntries` per stack) — the object managing state for that stack's entries.
- Allow more than two active top-level routes if your app needs it — max-two is a design decision of the recipe.
- Tab reselect reset: expose `reselectEvents = MutableSharedFlow<NavKey>(extraBufferCapacity = 1)` on `Navigator` (`onReselect(route)` → `tryEmit`); `NavigationBar` calls `navigator.navigate(key)` then `if (isSelected) navigator.onReselect(key)`; the entry observes `LaunchedEffect(reselectEvents) { reselectEvents.collect { if (it == RouteA) scrollState.scrollToItem(0) } }`.

**Flattened-stack alternative (`TopLevelBackStack`, no persistence — history cleared on pop):** maintains one stack per top-level route in a `LinkedHashMap`, exposes one flattened `SnapshotStateList` to `NavDisplay`:

```kotlin
class TopLevelBackStack<T : Any>(startKey: T) {
    private var topLevelStacks: LinkedHashMap<T, SnapshotStateList<T>> =
        linkedMapOf(startKey to mutableStateListOf(startKey))
    var topLevelKey by mutableStateOf(startKey); private set
    val backStack = mutableStateListOf(startKey)
    private fun updateBackStack() =
        backStack.apply { clear(); addAll(topLevelStacks.flatMap { it.value }) }
    fun addTopLevel(key: T) {
        if (topLevelStacks[key] == null) topLevelStacks.put(key, mutableStateListOf(key))
        else topLevelStacks.remove(key)?.let { topLevelStacks.put(key, it) } // move to end
        topLevelKey = key; updateBackStack()
    }
    fun add(key: T) { topLevelStacks[topLevelKey]?.add(key); updateBackStack() }
    fun removeLast() {
        val removedKey = topLevelStacks[topLevelKey]?.removeLastOrNull()
        topLevelStacks.remove(removedKey)               // popped top level -> its whole stack is dropped
        topLevelKey = topLevelStacks.keys.last(); updateBackStack()
    }
}
```

Behavioral notes on this variant: navigating back out of a top-level destination clears that destination's whole navigation history (state is NOT saved; returning to the tab starts at its initial screen). If the start route can move above other routes, back from it doesn't necessarily exit — the app exits when going back from a single remaining top-level route.

### Tabs & Top-Level Nav

**Chrome:** use `NavigationSuiteScaffold` (or custom scaffold) with `NavDisplay` inside; it auto-switches between bottom bar, rail, and drawer by window size class.

```kotlin
NavigationSuiteScaffold(
    navigationSuiteItems = {
        TOP_LEVEL_ROUTES.forEach { (key, item) ->
            val isSelected = key == navigationState.topLevelRoute   // selection = state, no controller introspection
            item(
                icon = { Icon(item.icon, contentDescription = item.description) },
                label = { Text(item.description) },
                selected = isSelected,
                onClick = { navigator.navigate(key); if (isSelected) navigator.onReselect(key) },
            )
        }
    },
) {
    NavDisplay(
        entries = navigationState.toEntries(entryProvider),   // multi-stack version
        onBack = { navigator.goBack() },
    )
}
```

Selection rule (Nav 3): `val isSelected = key == navigationState.topLevelRoute` — replaces Nav 2's `currentBackStackEntryAsState()` + `destination.hierarchy`/`hasRoute()` traversal. Current key accessors on a single-stack state holder:

```kotlin
@Stable
class NavigationState(val backStack: SnapshotStateList<NavKey>, val topLevelKeys: Set<NavKey>) {
    val currentKey: NavKey get() = backStack.last()
    val currentTopLevelKey: NavKey? get() = backStack.lastOrNull { it in topLevelKeys }
}
```

Tab switching must NOT recreate per-tab navigation history — use persistent per-tab stacks (above) or the root-swap single-stack pattern:

```kotlin
class Navigator(private val state: NavigationState) {
    fun navigate(key: NavKey) {
        if (key in state.topLevelKeys) {
            while (state.backStack.size > 1) state.backStack.removeLast()
            if (state.backStack.lastOrNull() != key) state.backStack[0] = key
        } else { state.backStack.add(key) }
    }
    fun goBack() { state.backStack.removeLastOrNull() }
}
```

**Adaptive chrome expectations (large screens):**

| Window width | Typical layout (Material adaptive) |
|---|---|
| Compact (under 600 dp) | Bottom bar, single pane |
| Medium (600–840 dp) | Navigation rail, optional list-detail |
| Expanded (over 840 dp) | Rail or persistent drawer, list-detail or multi-pane |

Use `WindowSizeClass` / `currentWindowAdaptiveInfoV2()` for custom splits. Google's adaptive quality tiers — aim ≥ tier 3 ("Adaptive ready": no letterboxing, rotation/resize/split-screen, basic keyboard/mouse); tier 2 ("optimized": responsive at all widths, keyboard shortcuts/hover, state survives resize) for tablet-heavy audiences; tier 1 ("differentiated": multitasking/drag-drop, fold postures, stylus, windowing) for foldables/Chromebooks. Handle config changes without losing context: rotation, fold/unfold, resize, split-screen enter/exit, keyboard attach/detach — keep UI state in ViewModel, process death in `SavedStateHandle`; test with "Don't keep activities". Foldables: no primary actions/tap targets on the hinge; folded-closed behaves as compact. Pointer/keyboard: tab order matches visual order, Enter/Space activate, arrows in lists, hover states on clickable rows; never rely on swipe-only shortcuts without a visible alternative. Multi-window: assume you don't own the full display; support ~220 dp min resize width; avoid modal flows that break at half width. Manual test matrix: phone portrait+landscape (required), tablet portrait+landscape (required if shipping large screens), foldable fold/unfold (high), desktop/Chromebook windowed (medium), split-screen/free-form resize (required tier 2+). Use Jetpack WindowManager (`androidx.window`) only for explicit fold/posture, not everyday bar-vs-rail decisions.

Nav 2 tab state saving/restoring (`saveState`/`restoreState` + `launchSingleTop`) lives in Nav2 Notes.

### Scenes

A `Scene` is the fundamental rendering unit — it renders one or more `NavEntry` instances (single-pane, multi-pane, dialog, bottom sheet). A `SceneStrategy` decides how back stack entries are arranged into a `Scene`.

```kotlin
interface Scene<T : Any> {
    val key: Any                        // unique id driving top-level animation when the Scene changes
    val entries: List<NavEntry<T>>      // entries this Scene displays
    val previousEntries: List<NavEntry<T>> // used for calculating predictive back state
    val content: @Composable () -> Unit
}
interface SceneStrategy<T : Any> {
    fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>): Scene<T>?  // null = let next strategy try
}
```

Built-in: `SinglePaneSceneStrategy` (last entry full-screen — always the implicit fallback), `DialogSceneStrategy`. Chain strategies with `then` (first match wins):

```kotlin
val strategy = dialogStrategy then bottomSheetStrategy then listDetailStrategy
```

**Dialog (built-in):** pass `DialogSceneStrategy<NavKey>()()` to `NavDisplay` (`sceneStrategies = listOf(dialogStrategy)`; older samples use the singular `sceneStrategy =`), mark the entry:

```kotlin
val dialogStrategy = remember { DialogSceneStrategy<NavKey>() }
entry<ConfirmDialog>(
    metadata = DialogSceneStrategy.dialog(DialogProperties(dismissOnClickOutside = true, windowTitle = "..."))
) { key ->
    AlertDialog(onDismissRequest = { backStack.removeLastOrNull() }, /* ... */)
}
```
The dialog renders as an overlay on top of the previous entry; content is plain composables (clip to rounded corners etc.).

**Bottom sheet (custom — `BottomSheetSceneStrategy` is NOT part of core Nav 3; copy it into your project):** an `OverlayScene` rendering `ModalBottomSheet(onDismissRequest = onBack)`; add its metadata to the entry.

```kotlin
internal data class BottomSheetScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val modalBottomSheetProperties: ModalBottomSheetProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val content: @Composable (() -> Unit) = {
        val lifecycleOwner = rememberLifecycleOwner()
        ModalBottomSheet(onDismissRequest = onBack, properties = modalBottomSheetProperties) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) { entry.Content() }
        }
    }
}
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val props = lastEntry.metadata[BottomSheetKey] ?: return null   // entry opts in via metadata
        return BottomSheetScene(key = lastEntry.contentKey as T, previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1), entry = lastEntry, modalBottomSheetProperties = props, onBack = onBack)
    }
    companion object {
        fun bottomSheet(modalBottomSheetProperties: ModalBottomSheetProperties = ModalBottomSheetProperties()) =
            metadata { put(BottomSheetKey, modalBottomSheetProperties) }
        object BottomSheetKey : NavMetadataKey<ModalBottomSheetProperties>
    }
}
// NavDisplay: sceneStrategies = listOf(remember { BottomSheetSceneStrategy<NavKey>() })
// entry<RouteB>(metadata = BottomSheetSceneStrategy.bottomSheet()) { ... }
```
**This overlay strategy must always be added before any non-overlay scene strategies.**

**Custom list-detail scene (adaptive):** activates when window width ≥ 600dp (`WIDTH_DP_MEDIUM_LOWER_BOUND`), last entry has detail-pane metadata, and a list entry is anywhere in the stack.

```kotlin
data class ListDetailScene<T : Any>(
    override val key: Any, override val previousEntries: List<NavEntry<T>>,
    val listEntry: NavEntry<T>, val detailEntry: NavEntry<T>,
) : Scene<T> {
    override val entries = listOf(listEntry, detailEntry)
    override val content: @Composable (() -> Unit) = {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(0.4f)) { listEntry.Content() }
            CompositionLocalProvider(LocalBackButtonVisibility provides false) {
                Column(Modifier.weight(0.6f)) {
                    AnimatedContent(targetState = detailEntry, contentKey = { it.contentKey },
                        transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it }}
                    ) { it.Content() }
                }
            }
        }
    }
    companion object {
        fun listPane() = metadata { put(ListKey, true) }
        fun detailPane() = metadata { put(DetailKey, true) }
        object ListKey : NavMetadataKey<Boolean>; object DetailKey : NavMetadataKey<Boolean>
    }
}
class ListDetailSceneStrategy<T : Any>(val windowSizeClass: WindowSizeClass) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)) return null
        val detailEntry = entries.lastOrNull()?.takeIf { it.metadata.contains(ListDetailScene.DetailKey) } ?: return null
        val listEntry = entries.findLast { it.metadata.contains(ListDetailScene.ListKey) } ?: return null
        // Scene key = LIST's contentKey: when the detail changes the scene key does NOT change,
        // so the scene (not NavDisplay) animates detail swaps.
        return ListDetailScene(key = listEntry.contentKey, previousEntries = entries.dropLast(1),
            listEntry = listEntry, detailEntry = detailEntry)
    }
}
@Composable fun <T : Any> rememberListDetailSceneStrategy(): ListDetailSceneStrategy<T> {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    return remember(windowSizeClass) { ListDetailSceneStrategy(windowSizeClass) }
}
val LocalBackButtonVisibility = compositionLocalOf { true }  // detail entry hides its back button in two-pane mode
```
Narrow screens: strategy returns `null` → default single-pane takes over. List-detail selection handler replaces prior detail: `removeIf { it is ConversationDetail }; add(detailRoute)`.

**Two-pane scene:** activates when window ≥ 600dp AND the last two entries both declare `twoPane()` metadata.

```kotlin
fun twoPane() = metadata { put(TwoPaneKey, true) }
object TwoPaneKey : NavMetadataKey<Boolean>
// sceneKey = Pair(firstEntry.contentKey, secondEntry.contentKey) — must uniquely represent scene state
// previousEntries = entries.dropLast(1): adds one entry on forward nav but removes only one on back —
// UX decision: displaying two panes while consuming one push avoids confusing double-pop.
```
A non-two-pane entry (e.g. `Profile` without metadata) falls back to single pane automatically.

**Material 3 Adaptive scenes (production layouts — use these; `androidx.compose.material3.adaptive:adaptive-navigation3`):**
- `rememberListDetailSceneStrategy<NavKey>()` — adaptive 1/2/3-pane list-detail. Metadata roles: `ListDetailSceneStrategy.listPane(detailPlaceholder = { Text("Select a conversation") })` (list pane always visible; placeholder shown in detail area when nothing selected), `ListDetailSceneStrategy.detailPane()`, `ListDetailSceneStrategy.extraPane()` (tertiary). Automatically handles pane arrangement, predictive back, and window-size adaptation; navigation is still plain back stack add/remove.
- `rememberSupportingPaneSceneStrategy<NavKey>()` — main + supporting pane. Roles: `SupportingPaneSceneStrategy.mainPane()` (always visible), `.supportingPane()`, `.extraPane()`. To make Back dismiss the supporting pane, pass `backNavigationBehavior = BackNavigationBehavior.PopUntilCurrentDestinationChange`.
- Pane spacing quirk: override directive to remove default partition spacers (b/418201867, b/444438086):

```kotlin
val directive = remember(currentWindowAdaptiveInfoV2()) {
    calculatePaneScaffoldDirective(windowAdaptiveInfo).copy(
        horizontalPartitionSpacerSize = 0.dp, verticalPartitionSpacerSize = 0.dp
    )
}
rememberListDetailSceneStrategy<NavKey>(directive = directive)
```

### Deep Links

Nav 2 parses deep links for you (built-in `NavDeepLink`); **in Nav 3 you own handling** — you parse the request, pick the `NavKey`, and build the back stack. `androidx.navigation3.runtime.deeplink` provides matcher primitives; still no automatic task/back-stack wiring.

**Nav 3 static/parameterized URI recipe:**

```kotlin
// 1. declare one matcher per supported URL pattern ({placeholders} map to NavKey property names;
//    path args /users/with/{filter} and query args ?ageMin={ageMin}&... both work).
//    One NavKey may carry MULTIPLE matchers if it supports several links.
internal val deepLinkMatchers: List<UriDeepLinkMatcher<NavKey>> = listOf(
    UriDeepLinkMatcher("https://www.nav3recipes.com/home".toUri(), serializer<HomeKey>()),
    UriDeepLinkMatcher("https://www.nav3recipes.com/users/with/{filter}".toUri(), serializer<UsersKey>()),
    UriDeepLinkMatcher("https://www.nav3recipes.com/users/search?{firstName}...".toUri(), serializer<SearchKey>()),
)
// 2. in Activity.onCreate:
val request = DeepLinkRequest(intent)
// 3. match all candidates, take best
val matches = deepLinkMatchers.mapNotNull { it.match(request) }   // null = no match
val bestMatch = matches.maxOrNull()
// 4. fallback when unsupported
val key = bestMatch?.key ?: HomeKey   // or a dedicated FallbackKey screen
// 5. start the back stack on that key BEFORE first composition
setContent { val backStack = rememberNavBackStack(key); NavDisplay(backStack = backStack, onBack = { backStack.removeLastOrNull() }, entryProvider = ...) }
```

**Custom `DeepLinkMatcher`** (e.g. JSON in intent extras): implement `RequestExtrasKey<T>` for type-safe extras, extend `DeepLinkMatcher<T, DeepLinkMatcher.MatchResult<T>>`, override `matchRequest(request)`; return `MatchResult(decoded)` or `null` on `SerializationException`.

```kotlin
internal data object JsonDeepLinkMatcherKey : RequestExtrasKey<String>
internal class JsonDeepLinkMatcher<T : NavKey>(val serializer: KSerializer<T>) :
    DeepLinkMatcher<T, DeepLinkMatcher.MatchResult<T>>() {
    override fun matchRequest(request: DeepLinkRequest): MatchResult<T>? {
        val json = request.extras[JsonDeepLinkMatcherKey] ?: return null
        return try { MatchResult(Json.decodeFromString(serializer, json)) }
        catch (e: SerializationException) { null }
    }
}
// sender: intent.putExtra(JsonDeepLinkMatcherKey.toString(), Json.encodeToString(HomeKey.serializer(), HomeKey(name)))
```

Older hand-rolled variant: `DeepLinkPattern(serializer, pattern)` + `DeepLinkRequest(uri)` + first match + `KeyDecoder(match.args).decodeSerializableValue(match.serializer)` — same shape: URI pattern → serializer → decoded `NavKey`, fallback default.

**Runtime deep links / `onNewIntent` (`launchMode="singleTask"`):** validate + parse the new intent and rebuild the stack —

```kotlin
LaunchedEffect(deepLinkId) {
    if (deepLinkId != null) {
        backStack.clear()
        backStack.addAll(listOf(Home, Details(deepLinkId)))
    }
}
```

**Synthetic back stack + task management:** deep link straight into a destination must simulate manual navigation so Up/Back walk through parents.

```kotlin
interface DeepLinkKey : NavKey { val parent: NavKey }
@Serializable data class ProductDetail(val productId: String) : DeepLinkKey {
    override val parent: NavKey = ProductListRoute
}
fun buildSyntheticBackStack(deepLinkKey: NavKey): List<NavKey> = buildList {
    var current: NavKey? = deepLinkKey
    while (current != null) { add(0, current); current = (current as? DeepLinkKey)?.parent }
}
// ProductDetail("abc") -> [HomeRoute, ProductListRoute, ProductDetail("abc")]
```

Detect the task in `onCreate`: `intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0` — new task ⇒ build synthetic back stack; existing task ⇒ add the deep link destination to the existing stack (or navigate there).

| Scenario | Back | Up | Synthetic back stack? |
|---|---|---|---|
| New task | Parent screen | Parent screen | Yes, on Activity creation |
| Existing task | Previous app/screen | Parent screen (restarts in new task) | Optional |

Up-button on original task — restart the Activity in a new task so Up navigates within the app: build `Intent(activity, activity::class.java)` with parent's deep link URI, `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`, `TaskStackBuilder.create(activity).addNextIntentWithParentStack(intent).startActivities()`, then `activity.finish()`.
Guidelines: **Up button never exits the app — disable it on the start destination; the start destination should never show an Up button.**

**Manifest:**

```xml
<activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTask">
    <!-- App Links (verified HTTPS - preferred) -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="example.com" android:pathPrefix="/products" />
        <data android:pathPrefix="/users" />
    </intent-filter>
    <!-- Custom scheme (fallback, not verifiable) -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="myapp" android:host="open" />
    </intent-filter>
</activity>
```
- `android:autoVerify="true"` enables App Links verification (HTTPS only)
- `android:exported="true"` required for Activities with intent filters (Android 12+)
- `launchMode="singleTask"` reuses the existing Activity instance via `onNewIntent`
- Keep `pathPrefix` entries narrow — avoid matching overly broad paths
- Prefer HTTPS App Links over custom schemes — custom schemes can be claimed by any app
- CMP registration lives in platform entry points: `AndroidManifest.xml` intent filters, App Delegate/SceneDelegate on iOS, URL handlers on Desktop; back stack construction logic can live in shared `commonMain`.

**App Links verification (`/.well-known/assetlinks.json`):**

```json
[{ "relation": ["delegate_permission/common.handle_all_urls"],
   "target": { "namespace": "android_app", "package_name": "com.example.app",
     "sha256_cert_fingerprints": ["AA:BB:..."] } }]
```
Requirements: exact path; HTTP 200 (redirects are NOT followed); `Content-Type: application/json`; include fingerprints for ALL signing keys (debug, release, Play App Signing). Fingerprints: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android` (or Play Console > Setup > App signing). Device checks (Android 12+): `adb shell pm set-app-links --package com.example.app 0 all` (reset), `adb shell pm verify-app-links --re-verify com.example.app`, `adb shell pm get-app-links com.example.app` → states `verified | approved | denied | none`.

**Security — deep links are public entry points; treat all incoming data as untrusted:**
- Always validate scheme and host against allowlists before processing
- Sanitize all URI parameters (path segments, query values) — they are attacker-controlled (length cap + character allowlist)
- Verify authentication/authorization state before navigating to protected screens
- Never load deep link URLs directly in a WebView without strict allowlisting
- Log deep link attempts for anomaly detection

**Nav 2 deep links:** `composable<Detail>(deepLinks = listOf(navDeepLink<Detail>(basePath = "https://example.com/detail")))` — matches `https://example.com/detail/{itemId}`; or `navDeepLink { uriPattern = "https://example.com/article/{articleId}" }` / custom scheme `myapp://article/{articleId}`. Explicit (internal) deep link from a notification/accessibility service: `Intent(context, MainActivity::class.java).apply { data = "https://myapp.com/account?accountId=abc123".toUri(); flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }`. Notification PendingIntent: `TaskStackBuilder.create(context).run { addNextIntentWithParentStack(deepLinkIntent); getPendingIntent(articleId.toInt(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }`.

### Results & Conditional Nav

**Nav 2 — results via `SavedStateHandle`** (avoids bloating route arguments):

```kotlin
// Sender: set on previous entry, then pop
Button(onClick = {
    navController.previousBackStackEntry?.savedStateHandle?.set("filter_result", selectedFilter)
    navController.navigateUp()
}) { Text("Apply") }
// Receiver: observe on current entry
val filterResult = navController.currentBackStackEntry?.savedStateHandle
    ?.getStateFlow<String?>("filter_result", null)?.collectAsStateWithLifecycle()
// Receiver in ViewModel: val newItemId = savedStateHandle.getStateFlow<String?>("new_item_id", null)
```

**Nav 3 — result event bus** (`androidx.navigation3.runtime.result`). Add `rememberResultEventBusNavEntryDecorator()` to `entryDecorators`; it provides a `ResultEventBus` via `LocalResultEventBus`.

```kotlin
entryDecorators = listOf(
    rememberSaveableStateHolderNavEntryDecorator(),
    rememberResultEventBusNavEntryDecorator(),
),
// SENDER (child screen):
val resultBus = LocalResultEventBus.current
resultBus.sendResult<Person>(result = person)
backStack.removeLastOrNull()
// RECEIVER A — event-based (one-time events, transient results):
ResultEffect<Person> { person -> viewModel.person = person }
// RECEIVER B — state-based (only latest result matters):
val person = LocalResultEventBus.current.conflateAsState<Person?>(null).value
```
Event-based = useful for results that are transient and should be handled as one-time events. State-based = suitable when only the latest result is required; **the result state does not survive configuration change or process death**.

**Nav 3 — callback-based results (alternative, recommended in modular apps):** define result callbacks on the feature's `Navigator` interface; the app module implements them by writing to hoisted state and popping.

```kotlin
interface ColorPickerNavigator { fun navigateBackWithColor(color: String); fun navigateBack() }
override fun navigateBackWithColor(color: String) { selectedColor = color; backStack.removeLastOrNull() }
```

| Pattern | Pros | Cons |
|---|---|---|
| Callback-based | Type-safe, fits Navigator interface pattern, simple | Requires state hoisting in app module |
| Event-based | Decoupled, works without Navigator | Not observable by Compose (plain `MutableMap`), manual key management |

Decoupled event variant without the result bus: `resultMap[ScreenA] = name; backStack.removeLastOrNull()`, caller reads `resultMap.remove(ScreenA)` in a `LaunchedEffect`.

**Conditional / auth-guard navigation (Nav 3):** centralize the rule in the `Navigator` (or a custom back stack). Mark gated keys with `requiresLogin`; redirect by pushing a `Login(redirectToKey)`; after success remove the Login entry and go to the original target; on logout remove any login-requiring destinations.

```kotlin
@Serializable sealed class ConditionalNavKey(val requiresLogin: Boolean = false) : NavKey
@Serializable private data object Home : ConditionalNavKey()
@Serializable private data object Profile : ConditionalNavKey(requiresLogin = true)
@Serializable private data class Login(val redirectToKey: ConditionalNavKey? = null) : ConditionalNavKey()
// sealed class/interface so KotlinX Serialization handles polymorphic serialization automatically

class Navigator(
    private val backStack: NavBackStack<ConditionalNavKey>,
    private val onNavigateToRestrictedKey: (targetKey: ConditionalNavKey?) -> ConditionalNavKey,
    private val isLoggedIn: () -> Boolean,
) {
    fun navigate(key: ConditionalNavKey) {
        if (key.requiresLogin && !isLoggedIn()) backStack.add(onNavigateToRestrictedKey(key))
        else backStack.add(key)
    }
    fun goBack() = backStack.removeLastOrNull()
}
// wiring: Navigator(backStack, onNavigateToRestrictedKey = { Login(it) }, isLoggedIn = { isLoggedIn })
// Login success: isLoggedIn = true; key.redirectToKey?.let { target -> backStack.remove(key); navigator.navigate(target) }
// Logout: isLoggedIn = false; navigator.navigate(Home)  (+ strip restricted entries from the stack)
```
Use `rememberSaveable` for `isLoggedIn` so auth state survives config changes; in production back it with a ViewModel or repository. Older recipe shape: a marker interface `RequiresLogin` + custom `AppBackStack` that stores the intended destination, pushes `Login` instead, and on `login()` adds the stored destination while removing `Login`.

**Conditional navigation (Nav 2):** redirect via `startDestination` and clear login from the stack after success:

```kotlin
val startDestination = if (isAuthenticated) Home else Login
composable<Login> {
    LoginScreen(onLoginSuccess = {
        navController.navigate(Home) { popUpTo<Login> { inclusive = true } }  // back can't reach login again
    })
}
// multi-screen auth flow: wrap it — navigation<AuthGraph>(startDestination = LoginRoute) { ... }
// on success: navController.navigate(HomeRoute) { popUpTo(AuthGraph) { inclusive = true } }
```

### Transitions & Predictive Back

**Nav 3 global transitions on `NavDisplay`:** `transitionSpec` (forward — content added), `popTransitionSpec` (back — content removed), `predictivePopTransitionSpec` (predictive back gesture, Android 14+). All are `ContentTransform` lambdas with `initialState`/`targetState`.

```kotlin
NavDisplay(
    transitionSpec = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(1000)) togetherWith
                       slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(1000)) },
    popTransitionSpec = { slideInHorizontally(initialOffsetX = { -it }) togetherWith
                          slideOutHorizontally(targetOffsetX = { it }) },
    predictivePopTransitionSpec = { /* same as pop */ },
)
```

**Per-entry overrides via metadata** (override the global specs; combine with `+`; raw keys `NavDisplay.TransitionKey`, `NavDisplay.PopTransitionKey`, `NavDisplay.PredictivePopTransitionKey`):

```kotlin
entry<ScreenC>(
    metadata = NavDisplay.transitionSpec {
        slideInVertically(initialOffsetY = { it }, animationSpec = tween(1000)) togetherWith
            ExitTransition.KeepUntilTransitionsFinished   // old content stays in place underneath
    } + NavDisplay.popTransitionSpec {
        EnterTransition.None togetherWith slideOutVertically(targetOffsetY = { it }, animationSpec = tween(1000))
    } + NavDisplay.predictivePopTransitionSpec { /* same as pop */ }
) { ScreenCContent() }
```

Common patterns:
```kotlin
fadeIn(tween(300)) togetherWith fadeOut(tween(300))                    // fade
slideInHorizontally(initialOffsetX = { it }) togetherWith slideOutHorizontally(targetOffsetX = { -it }) // h-slide
slideInVertically(initialOffsetY = { it }) togetherWith ExitTransition.KeepUntilTransitionsFinished     // modal-ish
EnterTransition.None togetherWith ExitTransition.None                                                  // none
```

**Conditional (route-dependent) transitions:** branch on the initial/target content keys:

```kotlin
transitionSpec = {
    val initialKey = initialState.entries.lastOrNull()?.contentKey
    val targetKey = targetState.entries.lastOrNull()?.contentKey
    when (initialKey to targetKey) {
        Step1.toContentKey() to Step2.toContentKey() -> swipeLeft()
        Step2.toContentKey() to Step3.toContentKey() -> swipeUp()
        else -> swipeRight()
    }
}
// popTransitionSpec mirrors the reverse directions; a backStack.clear()+add(Step1) restart
// can still animate seamlessly (Step4 -> Step1 pair matched in BOTH specs).
```

**Nav 2 transitions:** `NavHost(enterTransition=, exitTransition=, popEnterTransition=, popExitTransition=)` (e.g. `slideInHorizontally(initialOffsetX = { it }) + fadeIn()`, mirror for pop) applies to all destinations; per-destination `composable<Route>(enterTransition = {...}, ...)` overrides. Emphasized-easing example: `slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(Duration.medium4, easing = AppEasing.EmphasizedDecel)) + fadeIn(...)`; exit uses partial offsets (`targetOffsetX = { -it / 4 }`).

**Shared element transitions** (Nav 2 2.8+ with material3 1.4+; Nav 3 via `sharedTransitionScope`): wrap in `SharedTransitionLayout`, pass `animatedVisibilityScope` (the `composable`/`entry`/`AnimatedContent` receiver) and `sharedTransitionScope` down, mark elements:

```kotlin
SharedTransitionLayout {
    NavDisplay(backStack = backStack, onBack = { backStack.removeLastOrNull() },
        sharedTransitionScope = this, entryProvider = entryProvider { /* entries receive scope */ })
}
with(sharedTransitionScope) {
    AsyncImage(model = item.imageUrl, contentDescription = null,
        modifier = Modifier.sharedElement(
            state = rememberSharedContentState(key = "image-${item.id}"),
            animatedVisibilityScope = animatedVisibilityScope))
}
```
Keys must match between list and detail — use consistent key generation (`"card-${id}"`, `"title-${id}"`, `"image-${id}"`).

**Predictive back enablement:**
```xml
<!-- AndroidManifest.xml — required for predictive back gesture (Android 14+); mandatory on API 36 -->
<application android:enableOnBackInvokedCallback="true">
```
On API 36+ it defaults to `"true"`; on API 33–35 ensure it is set to `"true"`. NavHost 2.8+ handles predictive back automatically for type-safe routes. Nav 3 supports it via `predictivePopTransitionSpec` + scene `previousEntries`.

**Back handling:**
- Nav 3: system back gesture routes into `NavDisplay(onBack = ...)` — always provide `onBack = { backStack.removeLastOrNull() }`; with multi-stack state, `onBack = { navigator.goBack() }`.
- Nav 2 / custom: `BackHandler { navController.navigateUp() }` or animated: `BackHandler { coroutineScope.launch { isVisible = false; delay(...); navController.navigateUp() } }`; conditional: `BackHandler { if (hasUnsavedChanges) showDiscardDialog = true else onBack() }`.
- If Navigation 3 is in use, use Nav 3's built-in back navigation — do NOT manually implement low-level NavigationEvent dispatchers.

**NavigationEvent library (`androidx.navigationevent`, KMP foundation layer for system navigation events):**

```toml
[versions]
navigationevent = "1.0.0"   # compileSdk must be >= 36
[libraries]
androidx-navigationevent = { module = "androidx.navigationevent:navigationevent", version.ref = "navigationevent" }
androidx-navigationevent-compose = { module = "androidx.navigationevent:navigationevent-compose", version.ref = "navigationevent" }
```

Core classes: `NavigationEventDispatcher` (event hub managing registered handlers; all dispatchers in a hierarchy share one `NavigationEventProcessor` with global LIFO ordering); `NavigationEventHandler` (abstract; `onBackStarted` / `onBackProgressed` / `onBackCompleted` (required) / `onBackCancelled`, ctor params `initialInfo = NavigationEventInfo.None, isBackEnabled = true`); `NavigationEvent` (carries gesture details: `progress` 0f..1f, `swipeEdge` `EDGE_LEFT`/`EDGE_RIGHT`); `NavigationEventInfo` (contextual state); `NavigationEventInput` (translates platform input — gestures, hardware/software buttons — into `NavigationEvent`s; `DirectNavigationEventInput` for simple inputs). Handlers invoke by priority then recency: all `PRIORITY_OVERLAY` handlers run before `PRIORITY_DEFAULT`; within each group, most recently added first (LIFO). Child dispatcher = constructed with parent reference; `isEnabled = false` on a parent ignores that parent's and all children's handlers regardless of their individual state. `dispose()` cascades to all descendants and unregisters handlers.

```kotlin
// Compose handler: creates a NavigationEventHandler linked to LocalNavigationEventDispatcherOwner,
// auto-disposes via DisposableEffect when the composable leaves composition.
@Composable fun NavigationBackHandler(
    state: NavigationEventState<out NavigationEventInfo>,
    isBackEnabled: Boolean = true,
    onBackCancelled: () -> Unit = {},
    onBackCompleted: () -> Unit,
)
val navigationState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
val transitionState = navigationState.transitionState
when (transitionState) {
    is NavigationEventTransitionState.InProgress -> { val progress = transitionState.latestEvent.progress } // animate UI
    is NavigationEventTransitionState.Idle -> { /* reset temporary UI state on cancel */ }
}
NavigationBackHandler(state = navigationState,
    onBackCancelled = { }, onBackCompleted = { onNavigateUp() })
// gesture-reactive offset/scale:
val backProgress = (transitionState as? NavigationEventTransitionState.InProgress)?.latestEvent?.progress ?: 0f
val swipeEdge = latestEvent?.swipeEdge ?: NavigationEvent.EDGE_LEFT
val animatedScale by animateFloatAsState(targetValue = 1f - (backProgress * 0.1f), label = "ScaleAnimation")
val offsetX = when (swipeEdge) {
    NavigationEvent.EDGE_LEFT -> (backProgress * maxShift).dp
    NavigationEvent.EDGE_RIGHT -> (-backProgress * maxShift).dp
    else -> 0.dp
}
Modifier.offset(x = offsetX).scale(animatedScale)
// while a predictive gesture is InProgress on Screen B, also render Screen A underneath so the reveal is visible
```

**MANDATORY rules (verbatim guidance):**
- `ComponentActivity` automatically implements `NavigationEventDispatcherOwner` out-of-the-box. You must use the built-in `navigationEventDispatcher` without creating anonymous delegate owners or overriding member properties.
- Floating windows (Compose `Dialog`, `ModalBottomSheet`, `ComponentDialog`) automatically provide a `NavigationEventDispatcherOwner`. No manual `CompositionLocalProvider` propagation for dialogs.
- When scoping navigation handling to ViewPagers, tabbed interfaces, or nested navigation containers, use `rememberNavigationEventDispatcherOwner()` to create a child owner linked to the parent (`LocalNavigationEventDispatcherOwner.current`). Disabling the owner (`enabled = false`) automatically cascades to disable all child handlers.
- A one-to-one relationship between `NavigationEventState` and handlers is strictly enforced. Never bind the same `NavigationEventState` to multiple active `NavigationBackHandler` instances (`IllegalArgumentException`).

```kotlin
// WRONG — overrides the built-in dispatcher: shadows the library's extension property,
// recursive infinite loop crash on launch (StackOverflowError)
class MainActivity : ComponentActivity(), NavigationEventDispatcherOwner {
    override val navigationEventDispatcher = NavigationEventDispatcher()
    ...
}
// RIGHT — rely on the built-in ComponentActivity dispatcher owner
class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ... } } }

// WRONG — redundant: ComponentDialog provides NavigationEventDispatcherOwner automatically
CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) { Dialog { ... } }
// RIGHT — just put rememberNavigationEventState + NavigationBackHandler inside the Dialog

// WRONG — unlinked, unremembered raw dispatcher; .addChild() does not exist
val childDispatcher = NavigationEventDispatcher()
parentDispatcher?.addChild(childDispatcher)
// RIGHT — scoped child owner per tab/page; non-visible tabs stop intercepting back without leaking handlers
val childOwner = rememberNavigationEventDispatcherOwner(enabled = isSelected)
CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides childOwner) { NavigationBackHandler(state = ..., onBackCompleted = ...) }

// WRONG — two NavigationBackHandlers on one state -> IllegalArgumentException at runtime
NavigationBackHandler(state = navigationState, isBackEnabled = hasUnsavedChanges, ...)
NavigationBackHandler(state = navigationState, isBackEnabled = !hasUnsavedChanges, ...)
// RIGHT — single unified handler, branch inside onBackCompleted
NavigationBackHandler(state = navigationState, isBackEnabled = true, onBackCompleted = {
    if (hasUnsavedChanges) showDiscardDialog() else onNavigateUp()
})
```

Cleanup: Compose APIs (`NavigationBackHandler`, `rememberNavigationEventDispatcherOwner()`) remove handlers and dispose dispatchers automatically on leaving composition. Manual cleanup ONLY for custom dispatchers/non-Compose handlers: `myHandler.remove()` during teardown; `isEnabled = false` to temporarily disable a subtree; `dispose()` when the hosting component is destroyed (cascades to children). Platform support: Back on all (phone/tablet/web/iOS); Up on Android+Web; Forward on Web+iOS; Home on Android+iOS. Triggers: gesture-from-left = Back (all), gesture-from-right = Back on Android / Forward on iOS, gesture-from-bottom = Home. Web is inconsistent (browser owns the back stack; app must synchronize with browser navigation state).

### DI Wiring

**Nav 3 entry-scoped ViewModels** — via `rememberViewModelStoreNavEntryDecorator()` (dep: `androidx.lifecycle:lifecycle-viewmodel-navigation3`). Each entry gets its own `ViewModelStoreOwner`; VMs are created when the entry is added to the back stack and cleared when popped.

```kotlin
// BAD — globally-scoped ViewModel for per-screen data (leaks across screens, not cleared on pop)
val viewModel: DetailViewModel = viewModel()          // Activity-scoped
// GOOD — entry-scoped (requires rememberViewModelStoreNavEntryDecorator() in entryDecorators)
val viewModel: DetailViewModel = viewModel()          // scoped to entry via decorator
```

Always include BOTH decorators: `rememberSaveableStateHolderNavEntryDecorator()` + `rememberViewModelStoreNavEntryDecorator()`. When you add a custom decorator you must also re-add the defaults (they provide saveable state + display info). New VM per key instance: `ViewModelStoreNavEntryDecorator` uses `NavEntry.contentKey` to uniquely identify the ViewModel — "tl;dr: Make sure you use rememberViewModelStoreNavEntryDecorator() if you want a new ViewModel for each new navigation key instance." For shared state across entries, lift state to a parent composable or use a shared ViewModel at the Activity/App scope. (A shared-VM-between-parent-and-child entry needs a custom `NavEntryDecorator` handing out the parent's `ViewModelStoreOwner` — or just hoist.)

**Hilt (Nav 3):**

```kotlin
entry<Home> { val viewModel = hiltViewModel<HomeViewModel>(); HomeScreen(viewModel = viewModel) }   // Android only

// NavKey args via assisted injection (type-safe, no SavedStateHandle string keys):
@HiltViewModel(assistedFactory = RouteBViewModel.Factory::class)
class RouteBViewModel @AssistedInject constructor(@Assisted val navKey: RouteB) : ViewModel() {
    @AssistedFactory interface Factory { fun create(navKey: RouteB): RouteBViewModel }
}
entry<RouteB> { key ->
    val viewModel = hiltViewModel<RouteBViewModel, RouteBViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
}
// partial args: hiltViewModel<CreationViewModel, CreationViewModel.Factory> { factory ->
//     factory.create(originalImageUrl = createKey.fileName) }
```

Modularized entry providers with Hilt multibindings — each feature contributes, app aggregates:

```kotlin
// common
typealias EntryProviderInstaller = EntryProviderScope<Any>.() -> Unit

// feature module
@Module @InstallIn(ActivityRetainedComponent::class)
object ProfileModule {
    @IntoSet @Provides
    fun provideEntryProviderInstaller(navigator: Navigator): EntryProviderInstaller = {
        entry<Profile> { ProfileScreen() }
    }
}

// app: activity-scoped Navigator owning the stack + inject the set
@ActivityRetainedScoped
class Navigator(startDestination: Any) {
    val backStack: SnapshotStateList<Any> = mutableStateListOf(startDestination)
    fun goTo(destination: Any) { backStack.add(destination) }
    fun goBack() { backStack.removeLastOrNull() }
}
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var navigator: Navigator
    @Inject lateinit var entryProviderScopes: Set<@JvmSuppressWildcards EntryProviderInstaller>
    // NavDisplay(backStack = navigator.backStack, onBack = { navigator.goBack() },
    //     entryProvider = entryProvider { entryProviderScopes.forEach { builder -> this.builder() } })
}
// meetmiyani variant: EntryProviderScope<NavKey>.() -> Unit, provided @IntoSet, injected as
//   Set<@JvmSuppressWildcards EntryProviderScope<NavKey>.() -> Unit>, applied inside entryProvider { entryBuilders.forEach { this.it() } }
```

**Koin (Nav 3):**

```kotlin
entry<Details> { key ->
    val viewModel = koinViewModel<DetailViewModel> { parametersOf(key.id) }   // Android + CMP
    DetailScreen(viewModel = viewModel)
}
// navigation DSL (koin-compose-navigation3 artifact) — Koin aggregates automatically:
val appModule = module {
    activityRetainedScope {                       // modular recipe scope
        navigation<HomeRoute> { HomeScreen(viewModel = koinViewModel()) }
        navigation<DetailRoute> { route -> DetailScreen(viewModel = koinViewModel { parametersOf(route.id) }) }
    }
}
NavDisplay(
    backStack = rememberNavBackStack(HomeRoute),
    onBack = { backStack.removeLastOrNull() },
    entryProvider = koinEntryProvider(),
)
```
| Function | Platform | Description |
|---|---|---|
| `koinEntryProvider<T>()` | All (CMP) | Composable entry provider — use in `commonMain` |
| `getEntryProvider<T>()` | Android | Eager entry provider via `AndroidScopeComponent` |

Koin modular recipe: `Activity` implements `AndroidScopeComponent, KoinComponent`, `override val scope: Scope by activityRetainedScope()`, `val navigator: Navigator by inject()`, `entryProvider = getEntryProvider()`; app module `includes(featureModules)` + `activityRetainedScope { scoped { Navigator(startDestination = ...) } }`.

**Modularization (api/impl split):**

```text
feature-home/
  api/    HomeNavKey.kt        -- @Serializable data object HomeNavKey : NavKey   (other features depend on this ONLY)
  impl/   HomeScreen.kt        -- composable UI
          HomeEntryBuilder.kt  -- extension function on EntryProviderScope
```

```kotlin
// feature-home/impl
fun EntryProviderScope<NavKey>.homeEntry(navigator: Navigator) {
    entry<HomeNavKey> { HomeScreen(onItemClick = { navigator.navigate(DetailsNavKey(it)) }) }
}
// app module: NavDisplay(entryProvider = entryProvider { homeEntry(navigator); searchEntry(navigator); profileEntry(navigator) })
```
Benefits: features don't depend on each other; only the app module coordinates navigation via `Navigator` interfaces; `Navigator` interfaces make navigation mockable without NavController dependencies.

**Hilt (Nav 2):** each `composable()` destination gets a VM scoped to its `NavBackStackEntry` via `hiltViewModel<DetailViewModel>()`. `SavedStateHandle` is auto-injected and populated with navigation arguments (`checkNotNull(savedStateHandle["itemId"])`). Graph-scoped shared VM (multi-step flow):

```kotlin
composable("checkout/cart") { entry ->
    val parentEntry = remember(entry) { navController.getBackStackEntry("checkout") }
    val sharedViewModel: CheckoutViewModel = hiltViewModel(parentEntry)   // same instance for all destinations
}   // ... in the checkout graph; cleared when the graph is popped from the back stack
```
`@AssistedInject` for values that aren't navigation args / can't go through `SavedStateHandle`:
```kotlin
@HiltViewModel(assistedFactory = EditorViewModel.Factory::class)
class EditorViewModel @AssistedInject constructor(
    private val repository: DocRepository, @Assisted private val mode: EditMode,
) : ViewModel() { @AssistedFactory interface Factory { fun create(mode: EditMode): EditorViewModel } }
composable<Editor> { val vm = hiltViewModel<EditorViewModel, EditorViewModel.Factory> { factory -> factory.create(EditMode.CREATE) } }
```
Prefer `SavedStateHandle` for navigation arguments (simpler, survives process death). Use `@AssistedInject` only when `SavedStateHandle` can't carry the data.

**Koin (Nav 2):**
| Function | Purpose |
|---|---|
| `koinViewModel<T>()` | Standard injection — new instance per destination |
| `koinNavViewModel<T>()` | Like `koinViewModel` but auto-populates `SavedStateHandle` with nav arguments |
| `sharedKoinViewModel<T>(navController)` | Share ViewModel within a navigation graph (experimental) |
| `koinViewModel(parameters = { parametersOf(...) })` | Pass runtime values to the ViewModel constructor |

```kotlin
composable("detail/{itemId}") { val viewModel = koinNavViewModel<DetailViewModel>() }   // args flow into SavedStateHandle
navigation(startDestination = "checkout/cart", route = "checkout") {
    composable("checkout/cart") { entry -> CartScreen(viewModel = entry.sharedKoinViewModel<CheckoutViewModel>(navController)) }
    composable("checkout/shipping") { entry -> ShippingScreen(viewModel = entry.sharedKoinViewModel<CheckoutViewModel>(navController)) }
}   // Koin equivalent of hiltViewModel(navController.getBackStackEntry("checkout"))
```

### Nav2 Notes

Nav 2 is **not deprecated** and remains fully supported. Building blocks: **NavController** (imperative controller owning the back stack), **NavHost** (composable container mapping routes to destinations), **NavGraph** (the graph defined via the `NavHost` DSL). Android-only; full Fragment/Activity integration.

```kotlin
// Basic (string routes — legacy)
val navController = rememberNavController()
NavHost(navController = navController, startDestination = "home") {
    composable("home") { HomeScreen(onNavigateToDetail = { id -> navController.navigate("detail/$id") }) }
    composable("detail/{itemId}") { backStackEntry ->
        val itemId = backStackEntry.arguments?.getString("itemId") ?: return@composable
        DetailScreen(itemId = itemId, onBack = { navController.navigateUp() })
    }
}

// Legacy argument DSL — for pre-2.8 / string-route codebases (type-safe routes are the recommended default)
composable(
    route = "detail/{itemId}?sort={sort}",
    arguments = listOf(
        navArgument("itemId") { type = NavType.StringType },
        navArgument("sort") { type = NavType.StringType; defaultValue = "name" },
    )
) { backStackEntry -> /* backStackEntry.arguments?.getString(...) */ }
```

**Common actions:**

```kotlin
navController.navigate("detail/$id")
navController.navigate("detail/$id") { popUpTo("home") { inclusive = false }; launchSingleTop = true }
navController.navigateUp()          // up-style pop
navController.popBackStack()        // simple pop
// Type-safe (2.8+)
navController.navigate(Detail(id)) { popUpTo<Home> { inclusive = false }; launchSingleTop = true }
// Pop back to a specific destination, removing everything above it
navController.popBackStack(route = Home, inclusive = false)   // inclusive = false means Home stays
// Navigate and clear back stack (login -> home)
navController.navigate(Home) { popUpTo(navController.graph.startDestinationId) { inclusive = true }; launchSingleTop = true }
// Log out — pop entire back stack (0 = root of graph)
navController.navigate(AuthGraph) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
navController.navigate(Settings) { launchSingleTop = true }   // reuse existing instance instead of creating new
```

**Top-level tabs (NavigationBar + `currentBackStackEntryAsState`):** track selection with `destination.hierarchy` and `hasRoute(route::class)`.

```kotlin
@Serializable sealed interface TopLevelRoute {
    @Serializable data object Home : TopLevelRoute
    @Serializable data object Search : TopLevelRoute
    @Serializable data object Profile : TopLevelRoute
}
val navBackStackEntry by navController.currentBackStackEntryAsState()
val currentDestination = navBackStackEntry?.destination
// per tab: val selected = currentDestination?.hierarchy?.any { it.hasRoute(route::class) } == true
onClick = {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
// helper:
fun NavController.navigateTopLevel(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true; restoreState = true
}
// NavigationSuiteScaffold item(selected = currentDestination?.hasRoute<HomeRoute>() == true,
//                             onClick = { navController.navigateTopLevel(HomeRoute) })
```

**Nested graphs:** group related destinations; **nested graphs give each tab its own back stack — navigating between tabs preserves scroll position and state.**

```kotlin
NavHost(navController = navController, startDestination = "home") {
    composable("home") { HomeScreen(navController) }
    navigation(startDestination = "checkout/cart", route = "checkout") {
        composable("checkout/cart") { CartScreen(navController) }
        composable("checkout/shipping") { ShippingScreen(navController) }
        composable("checkout/payment") { PaymentScreen(navController) }
    }
}
// Type-safe: navigation<CheckoutGraph>(startDestination = CartRoute) { composable<CartRoute> { ... } ... }
```

**MVI bridging (Nav 2 route layer):** same effect contract as Nav 3 but calling the controller —
```kotlin
CollectEffect(viewModel.effect) { effect ->
    when (effect) {
        is ItemEffect.NavigateBack -> navController.navigateUp()
        is ItemEffect.OpenDetails -> navController.navigate(Detail(effect.id))
    }
}
```
How you wire ViewModels and state inside each `composable` block depends on architecture — see DI Wiring. ViewModel navigation events via `SharedFlow` + `flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)` collected in `LaunchedEffect` calling `navController.navigate(...)`.

**Multi-module Nav 2:** `:core:navigation` holds all `@Serializable` routes; each feature exposes a `NavGraphBuilder` extension; app assembles:

```kotlin
fun NavGraphBuilder.homeGraph(navController: NavController, sharedTransitionScope: SharedTransitionScope) {
    composable<HomeRoute> { HomeScreen(sharedTransitionScope = this@SharedTransitionLayout /* outer */,
        animatedVisibilityScope = this@composable,
        onNavigateToArticle = { id -> navController.navigate(ArticleDetailRoute(id)) }) }
}
SharedTransitionLayout {
    NavHost(navController, startDestination = HomeRoute) {
        homeGraph(navController, this@SharedTransitionLayout)
        articleGraph(navController, this@SharedTransitionLayout)
        searchGraph(navController); profileGraph(navController)
    }
}
```

**Testing Nav 2:**

```kotlin
val navController = TestNavHostController(ApplicationProvider.getApplicationContext())
navController.navigatorProvider.addNavigator(ComposeNavigator())
composeTestRule.setContent { navController.setGraph(R.navigation.app_graph); AppNavigation(navController = navController) }
composeTestRule.onNodeWithTag("article_card_1").performClick()
val route = navController.currentBackStackEntry?.toRoute<ArticleDetailRoute>()
assertThat(route?.articleId).isEqualTo(1L)
```

### Migration

**Key conceptual shifts (Nav 2 → Nav 3):**

| Nav 2 | Nav 3 |
|---|---|
| `NavController` owns the back stack | You own the back stack (`SnapshotStateList`) |
| `NavHost` renders composable destinations | `NavDisplay` observes the back stack and renders entries |
| Routes are strings or `@Serializable` types | Keys are `@Serializable` types implementing `NavKey` |
| Imperative navigation (`navController.navigate()`) | List manipulation (`backStack.add()`, `backStack.removeLastOrNull()`) |
| `NavGraph` groups destinations | No separate graph — entries are resolved by the `entryProvider` |
| Deep links parsed by Navigation library | Deep links parsed by your code — you construct the back stack |
| Graph-scoped ViewModels via `getBackStackEntry()` | Entry-scoped ViewModels via `rememberViewModelStoreNavEntryDecorator()` |
| `currentBackStackEntryAsState()` for selected tab | Direct back stack inspection (`backStack.last()` / `topLevelRoute`) |
| `saveState`/`restoreState` for tab persistence | Persistent per-tab stacks or root swap pattern |
| `navigate()` | `Navigator.navigate()` / `backStack.add()` |
| `popBackStack()` | `Navigator.goBack()` / `backStack.removeLastOrNull()` |
| `currentBackStack` | `backStacks[topLevelRoute]` |
| `currentBackStackEntry` / `currentBackStackEntryAsState()` / `currentBackStackEntryFlow` / `currentDestination` | `backStacks[topLevelRoute].last()` |
| Get top level route: traverse hierarchy from current entry | `topLevelRoute` |

**Official migration prerequisites:** `compileSdk` 36+; destinations are composable functions (Nav 3 is Compose-only — use Compose interop wrappers for Fragments/Views); routes already strongly typed (migrate string routes to type-safe FIRST); optional-but-recommended test coverage pinning current behavior.

**Guide assumptions** (AI agent: verify these before changing code; if false, STOP and ask the user how to proceed): one/several top-level routes each with its own back stack; state retained when switching stacks; the app is always exited through the Home/start screen; migration is a single atomic change (not incremental Nav2-alongside-Nav3).

**Feature support matrix (official guide):** supported directly = composable destinations, dialogs. Covered by recipes (check the recipe README/source, build a migration plan, confirm the plan with the user before proceeding) = bottom sheets, modularized/injected destinations, ViewModel arguments, returning results. NOT covered = more than one level of nested navigation, shared destinations that move between stacks, custom destination types, deep links (AI agent: if present, do not proceed — inform the user and ask for instructions).

**Steps (atomic):**
1. Add Nav 3 deps (`navigation3-runtime`, `navigation3-ui` @ `nav3Core = "1.1.7"`; `lifecycle-viewmodel-navigation3` @ `2.11.0` if using ViewModels); set `minSdk` 23, `compileSdk` 36.
2. Every route implements `NavKey`: `@Serializable data object RouteA` → `@Serializable data object RouteA : NavKey`.
3. Create `NavigationState` + `Navigator` (code in Back Stack & Persistence; same scope as the old `NavController`).
4. Replace `NavController` methods/fields (tables above). Tab selection before/after:
```kotlin
// before: currentDestination?.hierarchy?.any { it.hasRoute(key::class) }   (NavDestination?.isRouteInHierarchy)
// after:  val isSelected = key == navigationState.topLevelRoute
```
   Lifecycle before/after — Nav 2's `NavBackStackEntry` was the `LifecycleOwner`; Nav 3's `NavDisplay` provides an entry-scoped one via `LocalLifecycleOwner.current`:
```kotlin
// before: val lifecycleOwner = navController.currentBackStackEntry!!
//         val state by flow.collectAsStateWithLifecycle(lifecycleOwner = lifecycleOwner)
// after (inside destination composable): val state by flow.collectAsStateWithLifecycle()
```
   Verify ALL `NavController` references (including imports) are removed.
5. Move destinations into `entryProvider`: `composable<T>` → `entry<T>` (keep the type parameter); `dialog<T>` → `entry<T>(metadata = DialogSceneStrategy.dialog())`; `navigation(...)` → DELETE it with its route (no "base routes" needed — top-level routes identify each nested stack); `bottomSheet` → follow the bottom-sheet recipe (`BottomSheetSceneStrategy` is copied into the project, not in core). When deleting a nested-graph route, replace every reference to it (nav bar/rail/drawer lists) with the route type of its first child. Refactor `NavGraphBuilder` extension functions into `EntryProviderScope<T>` extension functions. Args come from the key passed to `entry`'s lambda.

```kotlin
// BEFORE (Nav 2)
@Composable
fun NavHostSnippet(navController: NavHostController) {
    NavHost(navController = navController, startDestination = BaseRouteA) {
        composable<RouteA> { entry -> val id = entry.toRoute<RouteA>().id; ScreenA(title = "Screen has ID: $id") }
        featureBSection()
        dialog<RouteD> { ScreenD() }
    }
}
fun NavGraphBuilder.featureBSection() { navigation<BaseRouteB>(startDestination = RouteB) { composable<RouteB> { ScreenB() } } }

// AFTER (Nav 3)
val entryProvider = entryProvider {
    entry<RouteA> { key -> ScreenA(title = "Screen has ID: ${key.id}") }
    featureBSection()
    entry<RouteD>(metadata = DialogSceneStrategy.dialog()) { ScreenD() }
}
fun EntryProviderScope<NavKey>.featureBSection() { entry<RouteB> { ScreenB() } }
// BaseRouteA/BaseRouteB deleted; references replaced by their first child routes
```
6. Replace `NavHost` with `NavDisplay`:
```kotlin
NavDisplay(
    entries = navigationState.toEntries(entryProvider),
    onBack = { navigator.goBack() },
    sceneStrategies = remember { listOf(DialogSceneStrategy()) },   // only if you have dialog destinations
)
```
7. Remove all Navigation 2 imports and dependencies.

**Incremental migration (alternative to atomic):** you do not have to migrate everything at once — (1) start with leaf screens with simple navigation (fewest navigation dependencies); (2) move shared/graph-scoped ViewModels LAST (entry decorators replace graph scoping — most restructuring); (3) keep Nav 2 running alongside Nav 3 during transition if needed; (4) convert navigation effects one screen at a time (`navController.navigate()` handlers → `backStack.add()`); (5) test each migrated screen independently before moving on. Coexistence: use Nav 3 for new feature modules, Nav 2 for existing screens; bridge at the Activity level — a Nav 2 destination can launch an Activity/Fragment hosting Nav 3, or vice versa. Graph-scoped VM replacement options: shared ViewModel at a higher scope (e.g. Activity-scoped `viewModel()`), or state hoisting in a parent composable passing state to child entries.

**String → type-safe Nav 2 (prereq for Nav 3; Navigation 2.8.0+, serialization plugin + `kotlinx-serialization-json`):**
1. Constants → `@Serializable object Home` / `@Serializable data class Profile(val userId: String)` (data object for no args, data class for args).
2. `composable("profile/{userId}")` → `composable<Profile> { backStackEntry -> val profile: Profile = backStackEntry.toRoute() }` (library auto-extracts args).
3. `navController.navigate("profile/user123")` → `navController.navigate(Profile(userId = "user123"))`.
4. ViewModel: `savedStateHandle.toRoute<Profile>()` instead of `savedStateHandle["userId"]`.
5. Custom types → custom `NavType` + `typeMap` (see Type-Safe Routes).
Best practices: sealed hierarchies for organization; `object` over parameterless `class`; nullable args supported with defaults; test with `hasRoute<T>()`.

### Anti-Patterns

**Shared rules (Nav 2 + Nav 3):** navigating during composition (triggers on every recomposition, infinite loops) → navigate in `LaunchedEffect` or event handler callbacks; passing `NavController`/back stack to ViewModel or leaf composables (violates MVI boundary) → ViewModel emits semantic effects, route handles navigation; string-based routes without type safety → `@Serializable` data classes/objects; missing `onBack` handler (Nav 3, system back gesture does nothing) → always `onBack = { backStack.removeLastOrNull() }`; globally-scoped ViewModel for per-screen data (leaks across screens, not cleared on pop) → entry-scoped VMs (Nav 3 decorators) or destination-scoped VMs (Nav 2); recreating back stacks on tab switch (loses per-tab history) → persistent per-tab stacks (Nav 3) or `saveState`/`restoreState` (Nav 2); missing entry decorators (Nav 3: VMs leak, saveable state lost) → always include BOTH `rememberSaveableStateHolderNavEntryDecorator` and `rememberViewModelStoreNavEntryDecorator`; using Nav 2 in new MVI codebases → prefer Nav 3 `NavDisplay` (Nav 2 remains valid for existing codebases).

**Common mistakes (verbatim, Nav 2-focused lists):**

❌ String routes anywhere — use `@Serializable` object/data class
❌ `navController.navigate(route)` for tabs — use `navigateTopLevel()`
❌ `navController.navigate(SomeRoute) { popUpTo(0) }` — pops past graph root; use `graph.findStartDestination().id`
❌ `hiltViewModel()` in every screen that shares state — scope to NavGraph entry
❌ Missing `popUpTo(AuthGraph) { inclusive = true }` after login — user can press Back to login
❌ Passing complex objects as route args — pass only IDs, fetch in ViewModel
❌ SharedElement keys not matching between list and detail — transition won't animate
❌ No screen-level transitions in NavHost — add enter/exit/pop specs at NavHost level
❌ Arguments as nullable strings from `arguments?.getString()` — use `backStackEntry.toRoute()`
❌ ViewModel per Composable — scope to NavGraph when sharing state across screens
❌ Missing `popUpTo` when navigating from auth to main flow — user can press back to login
❌ `LocalContext.current` to get NavController — pass NavController or actions as lambdas

- **String route paths** → use `@Serializable` routes. String typos crash at runtime
- **Passing Parcelable/Serializable objects as nav args** → pass IDs, fetch on destination
- **`navigate()` without `launchSingleTop`** → rapid taps create duplicate destinations
- **Nested `NavHost` for bottom tabs** → use nested `navigation()` graphs in single NavHost
- **ViewModel in nav args** → ViewModels are scoped to lifecycle, not navigation. Use `hiltViewModel()` scoped to back stack entry
- **Forgetting `popUpTo` on login→home** → user presses back from home and sees login again

**`hiltViewModel()` scope mistake:**

```kotlin
// Bad: hiltViewModel() inside a nested composable — scoped to the entire NavEntry, not the card;
// multiple ProductCards share the exact same ViewModel instance.
@Composable fun ProductCard() { val viewModel: ProductViewModel = hiltViewModel() }
// Good: pass state and callbacks down from the route/screen level; leaf = pure UI
@Composable fun ProductCard(product: Product, onClick: () -> Unit) { /* ... */ }
```

**ViewModel-driven navigation breaks unidirectional data flow and testability:**

```kotlin
// Bad: Navigator injected into ViewModel — ViewModel shouldn't drive navigation directly
class AuthViewModel(private val navigator: AuthNavigator) : ViewModel() {
    fun login() { /* ... */ navigator.navigateToMainApp() }
}
// Good: emit a one-shot event; the route composable handles navigation
class AuthViewModel : ViewModel() {
    private val _events = Channel<AuthEvent>(); val events = _events.receiveAsFlow()
    fun login() { /* ... */ _events.trySend(AuthEvent.LoginSuccess) }
}
```

**Complex objects inside NavKeys:**

```kotlin
// Bad: Product might be too large for SavedStateHandle or contain non-serializable data
@Serializable data class ProductDetail(val product: Product) : ProductsDestination
// Good: pass only IDs, fetch data in the destination
@Serializable data class ProductDetail(val productId: String) : ProductsDestination
```

**NavigationEvent mistakes** — see Transitions & Predictive Back for the WRONG/RIGHT pairs: overriding `ComponentActivity`'s dispatcher (StackOverflowError recursion), manually re-providing the dispatcher owner inside dialogs (redundant — `ComponentDialog` resolves it), unlinked/unremembered child dispatchers / non-existent `.addChild()` (breaks hierarchy routing; inactive tabs keep intercepting back), multiple active `NavigationBackHandler`s bound to one `NavigationEventState` (IllegalArgumentException).

### Checklist

**NavigationEvent / back handling (from the official skill, verbatim items):**
- [ ] Is compile SDK set to `36` or higher? (If lower, set it to `36` or higher in `build.gradle.kts`)
- [ ] Is `android:enableOnBackInvokedCallback` NOT explicitly set to `"false"` in `AndroidManifest.xml`? (On API 36+, it defaults to `"true"`; on API 33–35, ensure it is set to `"true"`)
- [ ] Does the Activity rely on the built-in `ComponentActivity` dispatcher owner without redundant anonymous delegate wrapping?
- [ ] Do dialogs or sheets rely on automatic `ComponentDialog` dispatcher resolution without redundant `CompositionLocalProvider` wrapping?
- [ ] Are parent-child dispatcher relationships in Compose scoped using `rememberNavigationEventDispatcherOwner()` when managing nested hierarchies?
- [ ] Is conditional back logic handled within a single unified `NavigationBackHandler` to avoid duplicate registration (`IllegalArgumentException`)?
- [ ] Are legacy `BackHandler` usages migrated to `NavigationBackHandler` with predictive progress support?
- [ ] Does the project build and pass tests successfully?

**Nav 3 core:**
- [ ] Every destination key is `@Serializable : NavKey` (sealed interface/class groups them and gives free polymorphic serialization)
- [ ] `rememberNavBackStack(...)` (not plain `mutableStateListOf`) so the stack survives config change + process death
- [ ] `NavDisplay` has `onBack = { backStack.removeLastOrNull() }` (or `navigator.goBack()`) wired
- [ ] Both `rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()` present (plus any custom decorator re-including the defaults) when entry-scoping ViewModels/state
- [ ] `rememberSerializable` used for saved navigation state — never converted to `rememberSaveable`
- [ ] Multi-stack tab pattern: persistent stack per top-level route, "exit through home", state retained for stacks not in use
- [ ] Tab reselect handled explicitly (`navigate` + `onReselect`/scroll reset), not by recreating stacks
- [ ] Navigation only from `LaunchedEffect`/event handlers; ViewModels emit semantic effects, route layer mutates the stack; no back stack/`NavController`/`Navigator` passed into ViewModels or leaf composables
- [ ] Navigation click handlers wrapped in `dropUnlessResumed`
- [ ] Scenes: overlay strategies (dialog/bottom sheet) listed before non-overlay strategies; `BottomSheetSceneStrategy` copied into the project (not core); Material 3 adaptive `listPane/detailPane/extraPane` (or `mainPane/supportingPane/extraPane`) metadata set; supporting-pane dismissal via `BackNavigationBehavior.PopUntilCurrentDestinationChange` if desired
- [ ] Deep links: matchers declared per URL; best-match selection (`maxOrNull`); fallback key; synthetic parent-chain stack for new-task links; Up never exits (hidden on start destination); manifest `exported="true"` + `autoVerify` (+ `launchMode="singleTask"` with `onNewIntent` if applicable); assetlinks.json exact path/200/Content-Type/all fingerprints; scheme+host allowlisted; args validated/sanitized
- [ ] Auth gating: gated keys marked (`requiresLogin`), redirect with `redirectToKey`, Login removed from stack on success, restricted screens removed on logout
- [ ] Transitions: global `transitionSpec`/`popTransitionSpec`/`predictivePopTransitionSpec` set; per-entry overrides via `NavDisplay.*Spec` metadata; conditional specs branch on `initialState`/`targetState` content keys
- [ ] Shared elements: `SharedTransitionLayout` + `animatedVisibilityScope` plumbed; keys identical on both sides
- [ ] DI: assisted factories for NavKey args; feature modules contribute `EntryProviderScope` builders (Hilt `@IntoSet`/`@JvmSuppressWildcards` set, Koin `navigation<>` + `koinEntryProvider()`/`getEntryProvider()`); api/impl module split exposes only NavKeys
- [ ] Adaptive: `NavigationSuiteScaffold` picks bar/rail/drawer; width classes (600/840 dp) respected; state survives rotation/fold/resize; keyboard/mouse/hover alternatives exist for swipe-only affordances

**Nav 2 / migration:**
- [ ] 2.8+ type-safe routes (`composable<T>`, `toRoute()`, `navDeepLink<T>(basePath=...)`) — no string routes
- [ ] Tabs use `navigateTopLevel()` helper (`popUpTo(graph.findStartDestination().id) { saveState = true }` + `launchSingleTop` + `restoreState`)
- [ ] Auth: `popUpTo<Login/AuthGraph> { inclusive = true }` after login; start destination chosen from auth state
- [ ] Shared checkout-style VMs scoped to the graph entry (`hiltViewModel(parentEntry)` / `sharedKoinViewModel`)
- [ ] Nav args = IDs/primitives only
- [ ] Migration pre-checks: assumptions verified (top-level stacks, exit-through-home, atomic vs incremental); unsupported features (nested >1 level, shared destinations, custom destination types, deep links) escalated before coding; nested-graph base routes deleted and references replaced with first-child routes; ALL `NavController` imports/refs and Nav 2 dependencies removed (atomic path) or coexistence bridged at Activity level (incremental path); each migrated screen tested independently; `compileSdk` 36 / `minSdk` 23; UI-test assertions via `hasRoute<T>()` / `currentBackStackEntry?.toRoute<T>()`
