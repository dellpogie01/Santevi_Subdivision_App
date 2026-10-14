## DOMAIN: Compose UI & Performance

### Stability

**Core model**
- Compose skips recomposition by comparing parameters to the previous call; an unstable parameter historically disabled skipping outright, and under Strong Skipping degrades the comparison to `===` (reference equality) — a fresh allocation every recomposition still forces the body to run.
- The compiler stores stability as one of five values: **Certain** (primitives, `String`, `Unit`, function types, enums, `@Stable`/`@Immutable` classes — final at compile time), **Runtime** (separately compiled class; emits `$stable: Int` + `@StabilityInferred`, resolved against type arguments at runtime), **Unknown** (interface / abstract class — runtime falls back to `===` identity probe), **Parameter** (generic; stability is a function of type arguments via a bitmask), **Combined** (aggregate of fields/type args; **Unstable dominates**).
- MUST distinguish `Stability.Unknown` ("cannot tell"; falls back to identity) from `Stability.Unstable` ("proven unstable"; disables skipping) when explaining a report.

**12-phase inference algorithm (first match wins)**

| Phase | Trigger | Result |
|---|---|---|
| 1 | primitive / `String` / `Unit` / function / suspend function | `Certain(stable=true)` |
| 2 | bare type parameter `T` | `Parameter` (deferred to call site) |
| 3 | nullable (`Int?`) | unwrap, recurse — nullability never changes stability |
| 4 | inline/value class | recurse on underlying type (exactly as stable as it) |
| 5 | cycle (type already on visited stack) | `Certain(stable=false)` (conservative bail; no fixed-point analysis) |
| 6 | `@Stable` / `@Immutable` / `@StableMarker` | `Certain(stable=true)` — fires *before* recursion, the escape hatch for recursive types |
| 7 | Known Stable Constructs registry | `Parameter` with registry bitmask |
| 8 | `stability_config.conf` match | `Parameter` with config bitmask |
| 9 | external module with `@StabilityInferred` | `Runtime` with annotation bitmask |
| 10 | Java class (no config match) | `Certain(stable=false)` — fix via config file, not by editing Java source |
| 11 | interface / abstract class | `Unknown` |
| 12 | field-by-field over linearized hierarchy | any `var` → Unstable; any unstable field type → Unstable; else Stable |

- Combined normalization: any Unstable → Unstable; all Certain-stable → stable; any Unknown → Unknown; Runtime/Parameter mix stays deferred to runtime.
- `runtime stable` is NOT a bug or an unstable verdict: it means "stability is conditional on type arguments; the compiler emitted `@StabilityInferred(parameters = …)` on the class plus a synthetic static final `$stable: Int` field (JVM; a mangled `…$stableprop` top-level property on Kotlin/Native & JS); the call site ANDs the bits against substituted argument stabilities". Cost is one field load + one bitwise AND — far cheaper than the unskipped recomposition it prevents. MUST NOT annotate a `runtime` class with `@Stable` to "promote" it.
- Cross-module classes without `@StabilityInferred` (old compiler / Java) resolve to a runtime check that always reports "unstable": recompile the dependency, or add the class to `stabilityConfigurationFiles` so phase 8 fires first.
- If a class shows `runtime stable` but every field is `val`, leave it — multi-module projects routinely show many `runtime …` lines and that is fine.

**Generic bitmask encoding (bit i set ⇒ type param i affects stability)**

| Class | Bitmask |
|---|---|
| `kotlin.Pair<A,B>` | `0b11` |
| `kotlin.Triple<A,B,C>` | `0b111` |
| `kotlin.Result<T>` / `ClosedRange<T>` / `ClosedFloatingPointRange<T>` / `PersistentList<E>` / `ImmutableList<E>` / `ImmutableSet<E>` / `dagger.Lazy<T>` / `java.util.Comparator<T>` | `0b1` |
| `ImmutableMap<K,V>` | `0b11` |
| `EmptyCoroutineContext` / `java.util.Locale` / `java.math.BigInteger` / `java.math.BigDecimal` | `0b0` (unconditionally stable; erased type args irrelevant) |
- Worked logic to cite: "bit 0 of `Pair` is set, `A=String` Certain-stable ✓; bit 1 set, `B=List` → Unknown ✗ → Combined collapses."
- Config-file bitmask syntax: `com.example.Generic<*,_>` — `*` = parameter participates, `_` = ignored; bits positional left-to-right.
- Adding `@Stable`/`@Immutable` skips bitmask emission entirely (phase 6 beats phases 9/12): the class becomes `Certain(stable=true)` with no `$stable` field, and the contract is yours to honor.

**@Stable vs @Immutable**
- `@Stable` = contract "I will notify Compose of changes" (snapshot-observable mutation allowed). `@Immutable` = stronger contract "I will never change": all `val`, all nested values immutable, structural `equals`. Both classify as `Certain`, but MUST prefer `@Immutable` whenever the type qualifies.
- The compiler treats `@Immutable` more aggressively: when every constructor argument at a call site is a compile-time constant it performs **static expression promotion** — instance hoisted to singleton, lambda captures de-duplicated, parameter reported `stable @static`. `@Stable` still emits equality checks; using `@Stable` for a never-mutating value class forfeits this.
- `@Stable` belongs on mutable-but-snapshot-observable holders: `@Stable class CartState { var total: Money by mutableStateOf(...); val lines: SnapshotStateList<Line> = mutableStateListOf() }`.
- `@Stable` on a class whose `var`s are NOT `mutableStateOf` is a lie — mutations are invisible to Compose; silent missed recompositions are worse than a non-skippable composable. `@Stable` is rare in app code.
- NEVER annotate a type `@Stable`/`@Immutable` unless the mutation/equality contract is actually honored; a false promise shows stale UI. If visible evidence can't distinguish "immutable data" from "observable mutable state", state both valid directions instead of guessing.
- For a false stability promise, first replace mutable non-snapshot properties with immutable data or snapshot-observable state, verify the contract, and only then decide whether an annotation remains needed.
- Recursive types: `data class Node(val id: String, val children: List<Node>)` is Unstable/Unknown via cycle bail; `@Stable` over a plain `List` is a lie (backed ArrayList mutates silently). RIGHT: `@Immutable data class Node(val id: String, val children: ImmutableList<Node>)` — `ImmutableList`'s registry hit permits the recursion, and phase 6 short-circuits the cycle check.
- `Set<String>`/`List<String>`/`Map` parameters are Unknown interfaces blocking skipping (or `===`-compared under strong skipping); `ImmutableSet<String>` etc. resolve to stable via registry when the element type is stable.
- Annotate sealed parents `@Immutable` (with immutable subclasses) — otherwise the abstract parent is `Unknown` and the hierarchy reports unstable.
- For classes Compose can't infer stable but mutation is tracked: `@Stable class ItemState(val id: String, val title: String, var isExpanded: Boolean = false)`; for guaranteed-immutable data use `@Immutable` (faster than `@Stable`) even when it holds a `List` field.

**Three-tier stabilization waterfall (never invert)**
1. **Tier 1 — make the type truly stable**: every property `val` of an already-stable type; no annotation needed, compiler infers it. WRONG: `data class Snack(var name: String, val tags: Set<String>)` (unstable on two axes: `var` + Unknown interface).
2. **Tier 2 — annotate** (`@Immutable`/`@Stable`) when you own the source and the contract holds.
3. **Tier 3 — `stabilityConfigurationFiles`** for third-party/Java types.
- Replace `kotlin.collections.List/Set/Map` parameters with `kotlinx.collections.immutable` `ImmutableList/ImmutableSet/ImmutableMap`; build with `persistentListOf()`/`persistentSetOf()`/`persistentMapOf()` or `.toImmutableList()` — `org.jetbrains.kotlinx:kotlinx-collections-immutable`. The type system enforces immutability — preferred over whitelisting `kotlin.collections.*` in config.
- PURE: convert once at the boundary: `val items: ImmutableList<Item> = repository.items().toImmutableList()`.
- Pure-Kotlin/data modules without `compose-runtime`: add `androidx.compose.runtime:runtime-annotation` (official) or `com.github.skydoves:compose-stable-marker` (legacy) so `@Stable`/`@Immutable`/`@StableMarker` are available without the full runtime.
- Nothing in tiers 1-3 fits: wrap in the `StableHolder` escape hatch — `@Stable class StableHolder<T>(val item: T)` with `equals`/`hashCode` delegating to `item`.
- `Flow<T>` parameters: MUST NOT annotate as stable — Flow is a cold producer with no observable identity, blocks skipping, and any inner `collectAsState` detaches from lifecycle. Collect upstream (ViewModel / `collectAsStateWithLifecycle`) and pass the resolved value.
- Do NOT "fix" instability by wrapping inline composables (`Row`/`Column`/`Box`) in an extracted composable — they are inline/non-restartable; wrapping changes restart scoping unpredictably without addressing the unstable parameter.

**stability_config.conf rules**
```kotlin
composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("stability_config.conf"))
}
```
- Legacy singular `stabilityConfigurationFile = ...` is deprecated ("Use the stabilityConfigurationFiles option instead"); plural is a `ListProperty`, `.add(...)` multiple files, compiler unions them. Requires Compose Compiler 1.5.5+ DSL.
- Grammar: `#` comments; exact FQCN; `com.example.data.*` direct children only (single segment); `com.example.data.**` recursive; `Foo<*,_>` generic bitmask positional; wildcards match by package only — `com.example.data.U*` is NOT valid; exact names beat wildcards; match type-parameter count exactly (`Pair<*,*>` not `Pair<*>`).
- The file is a contract, not a magic spell: the compiler does not validate listed types; a wrongly-listed mutable type causes silent stale UI with no diagnostic.
- Whitelist immutable `java.time` types (`LocalDateTime`, `LocalDate`, `Instant`, `ZonedDateTime`, `Duration`, …) — separately compiled, fall through to Unknown. MUST NOT add `java.util.Date` (mutable) — use `Instant`.
- Project-wide `kotlin.collections.List/Set/Map` whitelisting is a strong opt-in: every producer MUST NOT hand out an aliased mutable `List`; swap to `kotlinx.collections.immutable` instead when possible.
- Copy the FQCN from `classes.txt` (never hand-type); prefer the narrowest pattern; add a dated comment with rationale/audit note; re-run reports after every change to verify entries took effect (still `unstable` ⇒ FQCN wrong or wildcard too narrow).
- Declare the config in a convention plugin or `subprojects { composeCompiler { … } }` so modules don't diverge.
- Strong-skipping interaction: config still helps because equals-comparable parameters can re-skip on equal values from fresh allocations; without it a fresh `LocalDateTime` with the same instant fails `===`.

**Compiler reports — diagnosis workflow**
- Kotlin 2.0.0+: apply `id("org.jetbrains.kotlin.plugin.compose")`; the `composeCompiler { … }` extension is per-module on that plugin (pre-2.0 `kotlinCompilerExtensionVersion` is obsolete).
- Enable: `composeCompiler { reportsDestination = layout.buildDirectory.dir("compose_compiler"); metricsDestination = ... }` — gate behind a gradle property (e.g. `-PcomposeCompilerReports=true`) to keep builds fast.
- MUST generate reports from the **release** variant: `./gradlew :app:assembleRelease -PcomposeCompilerReports=true`. Debug builds enable Live Literals (constants become getters) making literals look dynamic and skewing every report.
- Outputs in `<module>/build/compose_compiler/`: `<m>_<variant>-classes.txt` (per-class), `-composables.txt` (per-composable; highest signal), `-composables.csv` (CI mirror; line counts are a regression sentinel, not a diagnosis), `-module.json` (aggregate counts — compare before/after; DO NOT treat as a target).
- `composables.txt` grammar: `[restartable] [skippable] [readonly] [scheme("[androidx.compose.ui.UiComposable]")] fun Name(stable|unstable|runtime|@static|@dynamic name: Type, …)`.
  - `restartable` — own restart scope (default for non-inline `@Composable fun` with body; inline composables like `Row`/`Column`/`Box` never appear).
  - `skippable` — `skipToGroupEnd()` guard emitted; **the absence of `skippable` on a `restartable` function is the diagnosis**. `@NonRestartableComposable` / `@NonSkippableComposable` appear as the absence of the respective flag.
  - `readonly` — proven not to write composition state (informational, not a fix target); `scheme(…)` informational.
  - Param flags: `stable` (equals-compared), `unstable` (`===` under strong skipping), `runtime` (`$stable` query; not a problem), `@static` (call-site compile-time constant — cannot trigger recomposition; the `@Immutable` bonus), `@dynamic` default. Same param can carry two prefixes (`stable @static index: Int`).
  - Filter to `restartable` lines NOT followed by `skippable`; scan params for the first `unstable`; map back to `classes.txt` for the root-cause field; deprioritize anything not on a hot path (LazyColumn item / animation tick).
  - Lambdas report `stable` (function types always stable); `Function2<Composer, Int, Unit>` reported as `runtime` is just the desugared `@Composable () -> Unit` — fine. Under strong skipping, the question shifts from "is it skippable?" to "do its params compare equal?".
- `classes.txt` grammar: `stable class …` / `unstable class …` / `runtime stable class …` / `runtime unstable class …` with per-field prefixes; annotations are consumed by prefixes, not echoed.
  - For an `unstable` class scan for the first `unstable` field — that is the root cause (`var`, unstable nested type, interface field, generic).
  - `runtime unstable` (e.g. `var` + generic) — treat as unstable.
  - Interface-typed field ⇒ whole class unstable/Unknown (annotate the interface `@Stable` only if every implementation honors it, pass a concrete type, or whitelist).
  - Inline classes report by underlying type. Hand-written non-structural `equals` is not flagged per se but silently misses recompositions — cached parameter comparisons use `equals`.
  - `@StableMarker` meta-annotation not working ⇒ annotation not on the compiler's classpath.
  - An unstable class that never crosses a composable boundary needs no fix.
- Prioritize by hot path: cross-reference `@TraceRecomposition` / Macrobenchmark `FrameTimingMetric` before fixing; a non-skippable composable that runs once at startup is irrelevant.
- MUST NOT chase 100% skippability — skippability is a diagnostic, not a KPI. MUST NOT annotate based on a report alone (contract evaluation belongs to the fix). MUST NOT mark functions `@NonSkippableComposable` to "fix" the report — hides the diagnostic.
- When the output dir is empty: confirm the compose plugin applied to *this* module, that Kotlin actually recompiled (touch/clean), and that `reportsDestination` is in a `composeCompiler { }` block (not legacy freeCompilerArgs).
- Record a baseline `module.json` per release so regressions are visible by diff.
- Evidence → repair mapping:

| Evidence | Repair |
|---|---|
| UI state exposes `List`/`Set` that must be immutable | `ImmutableList`/`ImmutableSet`, converting once at the boundary |
| `@Immutable`/`@Stable` describes mutable non-snapshot state | Make it immutable or snapshot-observable first; keep/add annotation only if truthful |
| Third-party type genuinely immutable | Add only that type to `stabilityConfigurationFiles` |
| Lazy item gets a new lambda/derived object each parent recomposition | Hoist/remember it for the item's stable inputs |
- Call-site lambda fix: `items(list, key = { it.id }) { item -> val onClick = remember(item.id) { { onItemClick(item.id) } }; RowCard(onClick) }`. Call-site stabilization does NOT solve deferred reads or cross-row measurement — route those to phase work.

**CI enforcement (skydoves/compose-stability-analyzer; ComposeGuard for KMP)**
- Stability silently regresses — a new `var` in a shared data class disables skipping across dozens of screens with no build complaint. Treat like binary compatibility: commit a baseline, diff on every PR, fail the build on regression.
- Apply plugin `com.github.skydoves.compose.stability.analyzer` **after** the Compose compiler plugin, on **every** UI module (a `:app`-only check misses feature-module regressions).
- DSL: `composeStabilityAnalyzer { enabled.set(true); stabilityValidation { enabled.set(true); outputDir.set(layout.projectDirectory.dir("stability")); failOnStabilityChange.set(true); ignoredPackages.set(...); ignoredClasses.set(...); stabilityConfigurationFiles.add(...) } }` — point at the same `stability_config.conf` so plugin and compiler agree.
- MUST commit the `.stability` baseline files (one per module per variant) into the repo — a baseline in `build/` is gitignored and the gate is theater. `outputDir` must be inside the module.
- Generate with `./gradlew :app:stabilityDump` (or `:debugStabilityDump`); check in CI with `./gradlew assemble :app:stabilityCheck` in the SAME job as compile (earlier signal, one Gradle daemon) and upload the diff report on failure.
- MUST set `failOnStabilityChange.set(true)` as the committed default; local opt-out via a gradle property is fine. MUST NOT baseline a known-bad state ("baseline only what the team will defend"); diagnose + stabilize first.
- On failure, either fix the regression (`var`→`val`, `List`→`ImmutableList`, `Flow` param → collect upstream) and confirm the baseline is unchanged, or deliberately re-dump and commit the baseline with a PR note explaining why — same ceremony as binary-compat baselines.
- Every `ignoredPackages`/`ignoredClasses` entry needs a one-line comment justifying it (previews, benchmark fixtures, test doubles); NEVER silence a production package.
- The plugin proves compile-time *classification*; pair it with runtime tracing — a green gate with rising recomposition counts means a runtime-only invalidation source (misplaced `mutableStateOf` read).
- Compose Multiplatform/desktop/iOS: `j-roskopf/ComposeGuard` with `<variant>ComposeCompilerGenerate` / `<variant>ComposeCompilerCheck` tasks; same workflow shape.

**IDE plugin (same repo, in-editor feedback)**
- Gutter colors per `@Composable`: green `#5FB865` skippable-all-stable; red `#E8684A` ≥1 unstable param (not skippable pre-strong-skipping); yellow `#F0C674` runtime-stability param; gray `#808080` no params (fine).
- Hover shows the per-parameter stability table + suggested fix; inline hints render per-parameter badges — tune noise with `showOnlyUnstableHints` / `showGutterIconsOnlyForUnskippable` instead of turning the inspection off.
- `UnstableComposable` inspection is intentionally a weak warning (group Compose) — a backlog signal, never build-blocking. Suppress only on the specific function via `@Suppress("NonSkippableComposable")` or `@Suppress("ParamsComparedByRef")` with a documented reason; NEVER `@file:Suppress("UnstableComposable")`; NEVER disable the inspection globally to silence noise.
- Keep `isStrongSkippingEnabled = true` and point `stabilityConfigurationPath` at the project's `stability_config.conf` so editor verdicts match the compiler.
- A red/yellow gutter on a hot-path composable is actionable even when the build is green — confirm via compiler reports, fix via stabilization. Do not chase 100% green gutters: interop POJOs and third-party classes may legitimately be red on cold paths.
- Alt+Enter "Add @TraceRecomposition" wires runtime instrumentation for the heatmap.
- Cascade visualizer: right-click inside a `@Composable` → Analyze Recomposition Cascade; static call-graph walk (resolveMainReference, depth cap 10, cycle detection by FQCN, stability badge per node). It is an estimate of blast radius, NOT a runtime guarantee — several downstream nodes may already be skipping. Confirm with the live heatmap before refactoring.
- Live heatmap: requires `@TraceRecomposition`-annotated composables and `ComposeStabilityAnalyzer.setEnabled(true)` (debug-gated); subscribes via `adb logcat -s Recomposition:D -T 1`; block inlays show counts since toggle-on; "Clear Recomposition Data" resets between back-to-back scenarios; focus one screen at a time.
- MUST toggle the heatmap OFF before Macrobenchmark / baseline-profile generation — logcat I/O + PSI work on every recomposition skews `FrameTimingMetric`.
- Prune cascade branches by stabilizing the parameter at its declaration site — wrapping leaves in `key { … }` does not make an unstable parameter stable.

### Recomposition

**Three phases & invalidation**
- Every frame: **Composition** (run `@Composable`s, build/diff tree — most expensive) → **Layout** (`measure()`/`placeRelative()`) → **Draw** (record draw commands — cheapest).
- A state read at phase N invalidates phase N and every phase below it:

| Read happens in | Invalidates Composition | Invalidates Layout | Invalidates Draw |
|---|---|---|---|
| Composition (body, value-form modifier) | YES | YES | YES |
| Layout (lambda-form layout modifier) | NO | YES | YES (re-record) |
| Draw (lambda-form draw modifier) | NO | NO | YES |
- Every read you defer one phase down saves the cost of every phase above it. A frame may skip any phase whose read-set is unchanged.
- A `restartable` composable owns a restart scope subscribed to every snapshot read in its body; inline composables (`Row`, `Column`, `Box`, `Layout`) are NOT restartable — their reads attach to the nearest enclosing scope. Lambda modifiers register a separate observer against Layout/Draw instead.
- Per-LayoutNode flags: `needsRemeasure` / `needsRelayout` / `needsRedraw`. `Modifier.offset { }` invalidation marks `needsRelayout` only; `graphicsLayer { }` marks `needsRedraw` only; neither triggers Composition — that is why 60fps lambda-modifier animations consume orders of magnitude less CPU.
- What triggers recomposition: `mutableStateOf` value change; collected `StateFlow` emitting a new value; parent recomposing with new params; a read `CompositionLocal` value change. What does NOT: reading a non-state variable; a `remember`ed value with unchanged keys; a stable param equal to its previous value (skip).

**Deferred state reads**
- Core: keep the `State<T>` (or provider lambda) intact across composition and read `.value` inside a block-form layout/draw modifier; keep a composition-time read ONLY when it decides which composables exist (UI branch).
- Value form → lambda form:

| Composition-time (reads in Composition) | Deferred form |
|---|---|
| `Modifier.offset(x = animatedX)` | `Modifier.offset { IntOffset(animatedX.value.roundToPx(), 0) }` (Layout; takes PIXELS — watch dp/px) |
| `Modifier.alpha(float)` / `rotate` / `scale` | `Modifier.graphicsLayer { alpha = …; rotationZ = …; scaleX/scaleY = … }` (Draw) |
| `Modifier.graphicsLayer(translationY = y)` | `Modifier.graphicsLayer { translationY = … }` |
| `Modifier.background(color)` | `Modifier.drawBehind { drawRect(color) }` |
| `Modifier.padding(dp)` / `size(dp)` | NO lambda overload — `Modifier.layout { measurable, constraints -> … }` |
| `Child(scrollOffset = listState.firstVisibleItemScrollOffset)` | `Child(scrollOffsetProvider = { listState.firstVisibleItemScrollOffset })` |
| `Modifier.absoluteOffset(x,y)` | `Modifier.absoluteOffset { IntOffset(x, y) }` |
- Fold multiple per-frame transforms into ONE `Modifier.graphicsLayer { }` block (translation/scale/rotation/alpha/cameraDistance/clip/shape) instead of chaining `alpha` + `offset` + `rotate`.
- Pass `() -> T` lambda providers (suffix the parameter `Provider` when it clarifies the deferred-read contract) instead of `Float`/`Dp` values across composable boundaries so the caller never reads hot state — the receiver reads it inside its own lambda modifier during Layout/Draw.
- `Modifier.drawBehind { }` re-runs every Draw pass; `Modifier.drawWithCache { }` caches rebuildable resources (paths, brushes, gradients — rebuilt when its captured state changes) and re-runs only `onDrawBehind`/`onDrawWithContent` blocks — prefer for heavier per-frame draw work. `rememberGraphicsLayer()` (Compose UI 1.7+) builds a layer once and replays it across draws.
- MUST NOT read frequently-changing state (scroll, animation, drag, measured size) in a composable body when a lambda modifier could read it later; MUST NOT feed hot state into value-form modifiers.
- Exceptions: do not obscure a one-shot cheap value or a test assertion solely to defer it; if evidence shows recomposition is not the bottleneck, leave the simpler form.
- Verify: the parent's recomposition count stops incrementing per animation frame (only Layout/Draw counters tick); grep the migrated file for `Modifier.alpha(`/`Modifier.rotate(`/`Modifier.scale(`/`Modifier.offset(<dp>)` fed by hot state; value→lambda is behavioral no-op only if the arithmetic matches (px vs dp!).

**Back-writing across phases**
- A composable MUST NOT write to a `MutableState` it has already read in the same composition pass (backwards write) — the runtime detects it, aborts the recomposition, and marks the scope dirty; worst case an infinite loop of aborted recompositions. Perform such writes in `LaunchedEffect`, `SideEffect`, an event handler, or a derived computation — never the synchronous body.
- Do not rebuild snapshot lists/maps from the composable body (composition mutation re-invalidates the same composition): derive immutable values with `remember(keys) { … }`; mutate snapshot state from events or effects only.
- Layout→composition cascade: an `onSizeChanged` (layout phase) write read by a sibling in composition invalidates composition per measure. When one item measures and another consumes it, capture the measurement in layout and apply it during measure (e.g. a `decorateMeasureConstraints` measure-phase helper), keep a fixed fallback while size is unknown, and compare before writing the captured size.

```kotlin
fun Modifier.decorateMeasureConstraints(
    decorate: (Constraints) -> Constraints,
): Modifier = layout { measurable, incoming ->
    val constraints = decorate(incoming).constrain(incoming)
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}
```

**Diagnosis workflow & axes**
- Measure one user-visible transition, identify the runtime axis causing the work, apply the smallest correction at the phase/boundary where that axis begins; change one axis at a time and re-measure the same transition; never stop at diagnosis.
- Three axes: (1) parameter stability / skipping, (2) where `State` is read (composition vs layout/draw), (3) back-writing across phases. Axes 2 and 3 often overlap; axis 1 is independent.
- Review order: reproduce one transition and note which composables recompose → counts spiking on unchanged lazy items? check back-writing (composition mutations, cross-row measurement) BEFORE blaming stability → counts climbing every frame during scroll/animation? check deferred reads → skipping failing despite stable data? check parameter stability + compiler reports → re-measure after each fix.
- Finish when evidence improves at the observed boundary without hiding state changes, caching stale values, or moving work to a less correct owner. A cross-phase write proves invalidation or an extra pass — do NOT claim oscillation or an infinite loop unless evidence shows values repeatedly changing.
- False leads — these do NOT reduce recomposition count:

| Attempt | Why it fails |
|---|---|
| `remember(index) { isFirstRow(index) }` instead of inline `when (index)` | Same inputs; no skipping benefit |
| Identity cache for read-only derived maps | Can serve stale overlays; `remember(keys)` is enough |
| `mutableIntStateOf` + layout modifier on **both** measured and sibling rows | Sibling still reads size in composition unless measure-only |
| Forcing `Exactly(1)` on both rows in focus-move tests | One row often correctly recomposes 0 times |
| Hoisting without stabilizing lambda captures | New lambda instance each frame still defeats skipping |
- When NOT to apply: recomposition tracks real data changes; the issue is correctness not cost; no profiler/compiler signal.

**Minimizing recomposition scope**
- Extract sub-composables so only the leaf with the changed input recomposes (`Profile` example: `NameText(name)` in its own scope so `Avatar`/`SettingsButton` skip).
- Read state as close to its use as possible — never `LazyListState`/animation/keyboard state high in the tree; collect once at the route and slice aggressively for leaves; pass each child only the fields it renders.
- Keep visual-only ephemeral state (shimmer alpha, pulse phase) local — never in global screen state; guard identical transitions upstream (reducer returns early when the value didn't change) so the same state isn't re-emitted per keystroke.
- Avoid expensive computation during composition (parse, sort, filter, format) — move it upstream to ViewModel/domain or pre-compute in the reducer.

**derivedStateOf**
- Use ONLY when input update frequency exceeds output update frequency ("filter hot inputs into cold outputs"): `val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }` (index ticks per scrolled item; boolean flips once).
- MUST wrap in `remember { … }` (or `remember(keys)`) — a bare `derivedStateOf { }` is re-created every composition and filters nothing.
- MUST pass every captured NON-state variable (function params, local vals, props) as `remember` keys: `remember(threshold) { derivedStateOf { … > threshold } }` — otherwise the derivation freezes the first-composition value forever (the silent stale-capture bug).
- Input ≈ output frequency (e.g. `"$first $last"`) → `derivedStateOf` is pure overhead; use a direct read or `remember(first, last) { … }` if memoization of a costly computation is the concern.
- MUST NOT wrap `collectAsState()`/`collectAsStateWithLifecycle()` results "just to be safe" — filter upstream with `.distinctUntilChanged()` / `.map { }` instead.
- For a one-shot side effect on a derived condition, prefer `LaunchedEffect(key) { snapshotFlow { … }.distinctUntilChanged().filter { … }.collect { … } }` — the read happens in the coroutine, the composable never subscribes or recomposes for the signal; MUST NOT fire side effects by reading derived state in a composable body `if (pastFold) onEvent()` (runs on every (re)composition, ties the effect to composition lifecycle).
- Deriving to a bucket (prices → Small/Medium/Large) is the canonical case; recomputing filters on input change also fits when the read set is high-frequency (`remember(items) { derivedStateOf { items.filter { … } } }`).
- Measure recomposition counts BEFORE and AFTER: if they didn't drop, something else is wrong (missing `remember`, wrong-phase read, sibling read in the same scope).

**Subcomposition pitfalls (SubcomposeLayout / BoxWithConstraints / Scaffold)**
- `SubcomposeLayout` runs its content's composition DURING the measure pass — that is its power (children compose against measured constraints) and its cost. `BoxWithConstraints`, `material3.Scaffold`, and the lazy layouts are built on it.
- MUST NOT use `BoxWithConstraints` merely to read `maxWidth`/`maxHeight` for a modifier-level effect — each new constraint value (rotation, IME show, animated parent size) re-runs the subcomposition. Use `Modifier.onSizeChanged { … }` (layout phase, fires only when measured size differs) or `Modifier.layout { … }` — no composition at all.
- MUST NOT place a `BoxWithConstraints` inside a `LazyColumn`/`LazyRow`/`LazyVerticalGrid` item — every newly visible item pays a fresh subcomposition. Hoist one `BoxWithConstraints` to the lazy layout's parent and pass the resolved value (a stable `Boolean`) down.
- MUST NOT nest `Scaffold` inside `Scaffold` — each is a `SubcomposeLayout`; use the single outer's `topBar`/`bottomBar`/`snackbarHost`/`floatingActionButton` slots.
- KEEP `SubcomposeLayout` when child composition genuinely depends on a measured value (tab strips sized by content, popovers aligned to measured anchors, lookahead shared elements) — tune it instead of banning it.
- When slots come and go: `SubcomposeLayoutState(SubcomposeSlotReusePolicy(maxSlotsToRetainForReuse = n))` retains up to n slot tables for reuse (the default no-op policy disposes eagerly and pays full composition cost on reappearance); `state.precompose(slotId, content)` on a previous frame returns a `PrecomposedSlotHandle` so the next measure-pass `subcompose` skips composition (this is how lazy layouts spread composition across frames); dispose the handle if unused.
- Do not allocate a fresh composable lambda inside `subcompose(slotId) { … }` on every measurement (AndroidX internal lint `ComposableLambdaInMeasurePolicy` flags this; `BoxWithConstraints`/`Scaffold`/`TabRow` suppress it because it is fundamental to their public contract — app code has no such excuse). Hoist the content lambda to a `remember`-stable reference or comment why it is safe.
- Verify with a Perfetto system trace (`androidx.compose.runtime:runtime-tracing`): `Compose:recompose` / `Compose:applyChanges` must fire only on real structural change, not per constraint/scroll tick.

**Debugging recompositions (Layout Inspector + runtime tracing)**
- Three instrumentation layers: Layout Inspector recomposition counts + skip counts (debug); Layout Inspector per-parameter Argument Change Reasons (Android Studio Hedgehog+); `@TraceRecomposition` (release/R8/real device).
- Layout Inspector: toggle BOTH "Show recomposition counts" and "Show recomposition skip counts"; reset counts before reproducing (cumulative counts pollute); tree shows `<recompositions>/<skips>`; healthy ratio is skips ≫ recompositions — `100/0` is the smoking gun, `100/95` is fine, `0/0` is dead code. Track the skip-to-recomposition ratio, not absolute counts.
- Argument Change Reasons (name the status before acting — "it recomposes a lot" is not a finding):

| Status | Meaning | Action |
|---|---|---|
| Static | Compile-time constant; never invalidates | None — not the cause |
| Unchanged | Compared equal to previous composition | None — look elsewhere (another param, unconditional parent re-invoke) |
| Changed | Compared not-equal | Was the change meaningful or accidental (a `copy()` with identical content failing `equals` because a field is `MutableList`; a flow without `distinctUntilChanged`)? |
| Uncertain | Unstable to the compiler → runtime fell back to `===`, inconclusive | Stabilize the type (`@Stable`/`@Immutable`, `List<T>`→`ImmutableList<T>`/`PersistentList<T>`) or accept it. MOST COMMON smoking gun: `@Immutable` contract violated by a mutable sub-field; `copy()`/builder producing structurally-equal but identity-different instances |
| Unknown | Compiler could not classify (separately-compiled module without `@StabilityInferred`, interface, Java POJO) | Run stability diagnosis |
- Debug counts are directional only — interpreted runtime + Live Literals inflate them (the same composable may be `100/0` in debug and `5/95` in release). Confirm in release + R8 + `@TraceRecomposition`; quote the release number when claiming a fix worked.

### State & Effects

**State ownership**
- Give every piece of UI state ONE lowest responsible owner; run imperative work through the effect whose lifecycle follows that owner. Composition renders; state and effects make rendering change safely.

| Situation | Owner |
|---|---|
| One composable owns simple UI state | Local `remember` / `rememberSaveable` |
| Siblings or a parent share it | Lowest common composable owner |
| Related UI mechanics need named operations or coordinated state | Plain state holder remembered in composition |
| Repository calls, persistence, business rules, screen state production | Screen-level holder (`ViewModel` or component) |
| App wiring and layout are mixed | Small wiring composable + plain UI composable |

- Screen boundary = three seams: small wiring composable owns app dependencies, durable data and effects; composition or a plain UI state holder owns runtime UI objects; a plain, previewable content composable receives immutable state + event callbacks separately from app wiring. Readable as `ProfileScreen(component) → collectAsStateWithLifecycle → ProfileContent(state, callbacks)`. Naming only state and intents is not that boundary.
- Extract a plain state holder only when coordinated UI behavior is a concept; keep a lone boolean or trivial text field local. Do not split a tiny one-off composable already receiving plain values + callbacks; do not create holder/UI overloads for structural symmetry or design-system primitives (use slots and modifiers there).
- A plain state holder may retain `LazyListState`, `FocusRequester`, `PagerState`, drawer state and other composition-created UI mechanics; pass business-relevant derived values across the screen boundary but do NOT expose runtime objects to the screen holder; accept the holder in a child only when it must coordinate it, otherwise pass plain values and callbacks.
- Keep frame-clock operations (scrolling, drawer animation) in a composition-scoped coroutine (`rememberCoroutineScope`), never `viewModelScope`.
- If UI input drives repository-backed data, keep that input with the screen holder that produces the data.
- NEVER keep screen/business state in `remember { mutableStateOf(...) }` (dies on config change) — ViewModel + `StateFlow`. Do NOT keep `mutableStateListOf` in a ViewModel — expose `StateFlow<List<T>>` with immutable lists.

**Authoring local state**
- Mutable composable-scope value: `var count by remember { mutableStateOf(0) }` — a bare local `var` resets on recomposition and never invalidates UI.
- In-place observable collections: `mutableStateListOf` / `mutableStateMapOf`. With `mutableStateOf(listOf(...))` you must REPLACE the list; structural changes are what fire. `SnapshotStateList` fires on add/set/remove but NOT on in-place mutation of an element (`items[0].name = "x"` does not recompose).
- Primitive specializations to avoid boxing on every read/write: `mutableIntStateOf(0)`, `mutableFloatStateOf(0f)` (Boolean has no specialization; use general `mutableStateOf`).
- `rememberSaveable` (or a `Saver`) only when recreation must preserve it; save ONLY serializable values — never runtime objects or callbacks. Custom types need an explicit `Saver(save = { … }, restore = { … })` via `rememberSaveable(stateSaver = …)`. Use it for tiny UI-local values surviving process death — not entire screen state, large graphs, or domain objects (screen business state belongs in the ViewModel).
- Do not mutate snapshot state from the composable body to rebuild derived data — `remember(keys) { … }` for read-only results; events and effects own mutations.
- Remember variants: `remember` survives recomposition only; `rememberSaveable` survives config change (rotation); `remember(key)` recalculates on key change; `remember { }` WITHOUT keys for derived state is a stale-value bug.
- `remember` usage: local objects/state across recompositions, expensive local objects, hot callback adaptation — not business state, repo results, or derived domain data.
- A local `var` inside `remember`'s producer, a non-composable callback, or a plain helper function is ordinary Kotlin state (fine). `setContent`-based tests are composable scopes and follow the same rules.

**Choosing an effect API (cheapest correct one)**

| Need | API |
|---|---|
| Suspending, deferred, or keyed work | `LaunchedEffect(keys…)` |
| Register + unregister listener/resource (no coroutine) | `DisposableEffect(keys…) { onDispose { … } }` |
| Publish state to a non-Compose system after every successful recomposition | `SideEffect` |
| Suspend work caused by a user event | `rememberCoroutineScope()` |
| Turn snapshot reads into a Flow | `snapshotFlow { … }` in `LaunchedEffect` (needs terminal `collect`) |
| Bridge external async/callback source to Compose state | `produceState` |
| Latest callback inside a long-lived effect without restarting it | `rememberUpdatedState` |
| React synchronously to a key change, no coroutine | `RememberedEffect(key) { … }` (skydoves/compose-effects) |
| Per-row ViewModel in a lazy list | `ViewModelStoreScope(key = stableId) { hiltViewModel() }` (compose-effects-viewmodel) |

- Key an effect by the semantic input that should restart or dispose it. Do NOT use `Unit` to hide a changing identity, and do NOT key on a broad state object when one property owns the lifecycle. MUST key on every value the effect closes over that should restart it — lying about keys is the top cause of "my effect doesn't see the new value".
- `LaunchedEffect` keys: `Unit` = once per entry; value = re-runs when it changes; multiple keys = re-runs if ANY changes. Never a constantly-changing key (`LaunchedEffect(System.currentTimeMillis())` = infinite re-execution).
- Long-lived effect consuming a changing callback: `val latest by rememberUpdatedState(onTimeout)` then call `latest()` lazily inside the effect — the coroutine keeps running, the reference stays fresh. Do NOT read `latest` eagerly in `remember { … }` (snapshots the initial value); if a changed value SHOULD recreate the work, use it as a key instead (and don't `rememberUpdatedState(userId)` to dodge restarting a collection that should follow `userId`).
- `LaunchedEffect(Unit)` is WRONG for one-shot non-suspending work that must never re-run (it re-runs after recreation) — use `remember { … }`. `LaunchedEffect(key) { syncCall() }` wastes a coroutine scope — use `RememberedEffect(key) { syncCall() }` (no `onDispose` — pair with `DisposableEffect` if teardown needed).
- `DisposableEffect`: pair EVERY registration with `onDispose`; it fires on key change AND composition exit. Do not wrap non-coroutine subscribers in `LaunchedEffect { try {} finally {} }` with `awaitCancellation` — the scope is wasted and the teardown contract is obscured.
- `SideEffect` runs after EVERY successful recomposition — use sparingly for thin stateless synchronization; MUST NOT for per-frame work or anything that allocates. Use a keyed `LaunchedEffect` for one-shot work.
- Event/side-effect flows (`Channel(BUFFERED).receiveAsFlow()` / `SharedFlow`) belong in `LaunchedEffect` collection (or a route-level `CollectEffect` = `repeatOnLifecycle(STARTED) { flow.collect { … } }`); render state is collected near the holder and rendered as plain state. One-off effects MUST NOT ride a `StateFlow` (replays to new collectors → fires twice on config change); `SharedFlow(replay = 0)` loses mandatory effects when UI detaches — `Channel(BUFFERED)` for one-offs, `SharedFlow` for broadcasts (analytics/logging), `StateFlow` for screen state; `Channel()` default RENDEZVOUS suspends senders without a receiver — use `BUFFERED`.
- Hot upstream: convert cold flows to shared with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)` declared as a `val` (never created per function call — leaks hot flows); strategies: `Lazily` (start first collector, keep alive), `Eagerly` (start immediately).
- Effect ordering: effects run after composition commits, in declaration order; `SideEffect` after every composition; `DisposableEffect` setup after composition; `LaunchedEffect` coroutines scheduled asynchronously.
- `produceState` has its own coroutine producer — do not nest a `LaunchedEffect` just to produce it.
- Per-row `hiltViewModel()` inside a lazy item resolves against the parent NavBackStackEntry store — every row shares one VM and state collapses; wrap the row in `ViewModelStoreScope(key = row.id) { … }` (key MUST be stable) so each row gets its own VM whose `onCleared` fires when the row leaves.
- Never run IO/DB/network/`viewModelScope` work directly in a composable body; the body renders only. Compute in ViewModel/reducer; UI-local async (scroll animation, snackbar) via `rememberCoroutineScope`; otherwise dispatch events to the ViewModel.

**Flow collection (lifecycle-safe)**
- MUST use `collectAsStateWithLifecycle()` (`androidx.lifecycle:lifecycle-runtime-compose` 2.6+) for any flow originating outside the composition (ViewModel, repository, sensor, websocket, location): pauses upstream work below `Lifecycle.State.STARTED` when backgrounded; `collectAsState()` keeps collecting in the background (CPU/battery drain). Use plain `collectAsState()` only where lifecycle-awareness is unavailable, or for flows created inside the same composable scope.
- Pick `minActiveState`: default `STARTED` matches `repeatOnLifecycle`; `RESUMED` only for focus-required widgets (document the rationale); NEVER `CREATED` (defeats the migration — keeps collecting while invisible).
- Provide `initialValue` for cold `Flow`/`SharedFlow`; the `StateFlow` overload reads `.value` — none needed.
- Chatty producers (>1 emission per ~100 ms): add `.conflate()` (keep latest) and `.distinctUntilChanged()` (needs correct `equals()`) upstream, wrapped in `remember(key) { flow.conflate().distinctUntilChanged() }` so the operator chain is not rebuilt per recomposition. If emissions drive animation values, prefer phase deferral over `.conflate()`.
- `Flow<T>` as a composable parameter is banned (unstable; blocks skipping; unclear collection lifecycle) — collect at the caller and pass the value, or a `() -> T` provider for very hot producers.
- State→Flow direction: `snapshotFlow { stateRead }.distinctUntilChanged().collect { … }` inside a `LaunchedEffect` — supported bridge, reads happen in the coroutine not in composition.
- Existing `State<T>` (`mutableStateOf`, `Animatable.asState()`): do NOT rewrap into a flow just to collect it; keep the State direct.
- Truly always-on background listeners that must survive `STOPPED` belong in a Service / WorkManager / Activity `repeatOnLifecycle` — not Compose.
- Backpressure menu: default (suspends producer), `buffer(n)` (every item, spikes smoothed), `conflate()` (latest only — UI updates), `collectLatest { }` (cancel previous processing — search); `debounce(300) + distinctUntilChanged + collectLatest` is the search pipeline; `flowOn(dispatcher)` shifts upstream + auto-buffers at the switch.

**CompositionLocal**
- Use for: theming, platform integration (`LocalDensity`, `LocalLifecycleOwner`, `LocalContext`), infrequently-changing cross-cutting concerns.
- Do NOT use for: frequently-changing values (widespread recomposition), values needed 1-2 levels deep (pass explicitly), dependencies that should use DI, feature state in MVI-style apps (state flows ViewModel → route → screen → leaves via parameters).
- Inside `Modifier.Node`s, read locals via `CompositionLocalConsumerModifierNode.currentValueOf(local)` — tracked against the phase's invalidation list, not a composition restart scope.

**Focus (stateful UI behavior)**
- Components already focusable (button/text field/clickable) need nothing extra; add hooks only for requested behavior: programmatic initial/restored focus → `FocusRequester` + `Modifier.focusRequester(…)`; visual reaction to focus → `Modifier.onFocusChanged { state -> … }`; custom interactive surface → `Modifier.focusable()` + semantics.
- Request initial/restored focus from `LaunchedEffect` keyed to the condition that makes the target present (`LaunchedEffect(items.isNotEmpty()) { if (…) requester.requestFocus() }`) — NEVER from the composable body. Keep requesters for lazy content in a map keyed by stable item id and request only after the item is composed. Inside `AnimatedContent`, use the content lambda's target consistently for identity, tags, requester ownership, and effect key — captured outer state gives outgoing and incoming content the same identity.
- Keep default spatial search; encode ONLY wrong edges/jumps/traps with `Modifier.focusProperties { up = …; down = …; left = FocusRequester.Cancel }` — too many hard-coded links create stale focus graphs.
- Handle keys only for non-normal-click/traversal behavior; consume exactly the handled event (`onPreviewKeyEvent { if (match) { act(); true } else false }`); throttle rapid D-pad work at its expensive owner, not across the screen.
- Restore focus by semantic identity after refresh: retain the focused id when it exists, else a deterministic fallback.
- Test through user input (`performKeyInput { pressKey(Key.DirectionDown) }` + `assertIsFocused()`), not direct state mutation; screenshots only for focus appearance.

### Modifiers

**API contract for reusable composables**
- Any composable emitting layout declares `modifier: Modifier = Modifier` after required parameters and applies it to its ROOT layout. A modifier on a child does not make the root caller-placeable. (A private/framework primitive may require `modifier` as its first required parameter instead.)
- Caller modifier goes FIRST in the root chain, then intrinsic identity modifiers: `modifier.clip(CircleShape).size(48.dp)`. `clip` + a default avatar size can be intrinsic; `fillMaxWidth`, screen padding, alignment, placement are the caller's. The parent owns placement; the component owns invariant structure.
- Do not add a `modifier` parameter to non-layout `@ReadOnlyComposable` accessors, previews, or test-only single-use composables, and do not add a root modifier merely because a focused task is about slots.
- Build a modifier as ONE fluent expression; 1-2 calls inline, 3+ one per line; conditional segment via `.then(if (condition) Modifier.x() else Modifier)`; keep imperative construction when animation/procedural state makes fluency unclear.
- Every public composable must accept `modifier: Modifier = Modifier` (first optional param).

**Ordering semantics (wrap-the-next-modifier model)**
- `Modifier.a().b().c()` = a wraps (b wraps (c)). Read top-to-bottom; the top is outermost, the bottom is closest to content. Order is observable, not stylistic.
- `padding` subtracts from the space passed to everything BELOW it; `background` paints across the bounds AT its position; `clickable`'s hit area is the bounds at its position (everything above it is clickable, everything below is not); `clip` clips everything BELOW it; `graphicsLayer` wraps everything BELOW it in a render layer.
- Canonical decisions:
  - `Modifier.padding(8.dp).background(Red)` → red only inside the padded bounds. `Modifier.background(Red).padding(8.dp)` → red across the full outer bounds. Default for "card with internal padding": background → padding.
  - `clip(shape).background(color)` to round the fill — `background(color).clip(shape)` leaves square corners — or skip the question with `Modifier.background(color, shape)` (single node).
  - Clickable OUTSIDE the visual padding (above it) = Material 48dp touch target including padding, ripple fires in padding too; clickable BELOW padding = tap only on visible content. Accessibility sandwich: `.padding(8.dp)` outer spacing → `.clickable{}` → `.padding(16.dp)` visual padding.
  - `graphicsLayer` at/near the TOP of the chain so alpha/transforms apply to the whole visual unit including the background; placed below `background` it fades only the content (the classic "why is my fade weird" bug).
  - `graphicsLayer` does NOT change hit-test geometry: `clickable` hit-tests its own position; with layer transforms (translation/scale/rotation) hit testing follows pre-transform laid-out bounds — use `Modifier.pointerInput { }` with explicit hit testing for transformed targets.
  - `.size(100.dp).padding(8.dp)` = 100dp box with 84dp content; `.padding(8.dp).size(100.dp)` = 100dp content with 116dp total. Same modifiers, different layout.
- Reference orderings by goal: card with shadow `fillMaxWidth → padding → shadow(shape) → clip(shape) → background → clickable → padding`; clickable row `fillMaxWidth → clip → clickable → padding`; icon button `size → clip(CircleShape) → clickable → padding`; badge `clip → background → padding`.

**Phase behavior of modifiers**
- Value-form modifiers read their arguments in Composition (`offset(Dp)`, `padding`, `size`, `alpha`, `rotate`, `scale`, `background`, `graphicsLayer(…)` value form, `clip(Shape)`, `shadow`, `weight`, `fillMaxWidth`, `aspectRatio`, `absoluteOffset(x,y)`).
- Lambda/block forms read during Layout (`offset { }`, `absoluteOffset { }`, `layout { }`, `onSizeChanged`, `onGloballyPositioned`) or Draw (`graphicsLayer { }`, `drawBehind`, `drawWithContent`, `drawWithCache`). Prefer lambda forms for hot state — see the deferral table in Recomposition; `padding` has NO lambda overload (use `Modifier.layout { }`).
- `Modifier.composed { }` is discouraged for new modifiers and MUST NOT be used to fix ordering: it re-creates a fresh composable scope per call site, can never be skipped, and forces parent recomposition. Author `Modifier.Node` instead.

**Modifier.Node authoring & migration**
- `Modifier.Node` is a persistent node diffed by `ModifierNodeElement.equals()`: created once, updated in place — no per-recomposition allocation, no fresh composable scope, no parent invalidation.
- Every custom modifier has three pieces: (a) public extension `fun Modifier.foo(...) = this then FooElement(...)`; (b) `private data class FooElement(...) : ModifierNodeElement<FooNode>()` with `create()` (first apply) and `update(node)` (subsequent applies); (c) `class FooNode(...) : Modifier.Node(), <specialized interfaces>` holding mutable `var` fields + lifecycle hooks.
- MUST make the Element a `data class`: a plain `class` gets referential equality, the diff treats every apply as new (recreate churn) or `update()` is never called (node keeps stale parameters silently).
- `update(node)` mutates node fields in place; MUST NOT recreate the node from `update()`. It is called only when the new Element `!equals` the previous.
- Lifecycle: `create()` → `onAttach()` (node joins tree; `coroutineScope` becomes valid; register listeners) → `update()` per changed apply → `onReset()` on lazy-layout reuse (clear transient state) → `onDetach()` (leave tree; `coroutineScope` cancelled; release manual resources). `onAttach` runs once per node instance; nodes may cycle attach/detach in lazy layouts.
- Use the node's built-in lazy `coroutineScope` (tied to attach/detach) for animation loops/observers/debouncers launched from `onAttach`; MUST NOT allocate your own `CoroutineScope` in `onAttach` (leaks past `onDetach`); MUST NOT hold `Composer`, the calling composable, or any composition-scoped object in a node (outlives any single pass — leak/UB). Accept primitives/stable params; get composition context via `currentValueOf`.
- Manual invalidation: `update()` auto-invalidates; override `shouldAutoInvalidate = false` and call `invalidateDraw()` / `invalidateMeasurement()` / `invalidatePlacement()` for fine control (draw-only changes, re-measure signals, placement-only). Leave default unless profiling shows the auto-invalidation costs extra.
- Interface selection:

| Interface | Implement when / replaces |
|---|---|
| `DrawModifierNode` (`ContentDrawScope.draw()`; `drawContent()` to paint under/over) | painting; replaces `drawBehind`/`drawWithCache` |
| `LayoutModifierNode` (`MeasureScope.measure(measurable, constraints): MeasureResult`; optional intrinsic overrides) | measure/place; replaces `layout { }` and one-off custom `Layout` |
| `SemanticsModifierNode` (`applySemantics()`; `shouldMergeDescendantSemantics`/`shouldClearDescendantSemantics`) | accessibility; replaces `semantics { }` |
| `PointerInputModifierNode` (`onPointerEvent`, `onCancelPointerInput`) | raw pointer input; replaces `pointerInput { }` (delegate gestures via `DelegatingNode`) |
| `CompositionLocalConsumerModifierNode` (`currentValueOf(local)`) | reading CompositionLocals in node callbacks (pair with behavior interface; e.g. theme-aware drawing) |
| `LayoutAwareModifierNode` (`onPlaced`, `onRemeasured`) | own size/placement changes; replaces `onSizeChanged`/`onPlaced` |
| `GlobalPositionAwareModifierNode` (`onGloballyPositioned`) | window-relative position (fires on any ancestor placement change — pricier; prefer `LayoutAwareModifierNode` if local suffices) |
| `ObserverModifierNode` (`observeReads { }`, `onObservedReadsChanged()`) | observe arbitrary state reads outside standard invalidation; carries costs |
| `DelegatingNode` (`delegate()`/`undelegate()`, bound `DelegatableNode`) | composing multi-behavior modifiers; children are real nodes sharing host lifecycle; PREFERRED past ~3 interfaces on one node |
| `TraversableNode` (`traverseKey`) | chain walks via `traverseAncestors`/`traverseChildren`/`traverseDescendants` on `DelegatableNode` (blocks return Boolean; descendants return `TraverseDescendantsAction`: Continue/SkipSubtree/Cancel) — focus scopes, nested scroll sources |
- Migration cheat sheet (`composed` body → node): `drawBehind`/`drawWithCache`→`DrawModifierNode`; `layout`/`Layout`→`LayoutModifierNode`; `pointerInput`→`PointerInputModifierNode`; `LocalFoo.current`→+`CompositionLocalConsumerModifierNode`; `LaunchedEffect`→`onAttach { coroutineScope.launch }`; `DisposableEffect/onDispose`→`onDetach`; `onSizeChanged`/`onPlaced`→`LayoutAwareModifierNode`; `onGloballyPositioned`→`GlobalPositionAwareModifierNode`; `semantics`→`SemanticsModifierNode`; multiple→`DelegatingNode` children.
- One-line composable wrappers that could be `@Composable` functions can stay functions; built-in chains suffice without custom nodes; ordering bugs are fixed by reordering, not by new nodes.
- Don't write state from inside a `Modifier.layout { }` block — wrong tool; use `LayoutModifierNode`'s `coordinator` or surface the effect via callbacks.
- Verify migration: zero `Modifier.composed` left in module; every Element is `data class`; parents consuming the migrated modifier now report `restartable skippable`; parent recomposition plateaus while only node invalidation lists tick; `onAttach` resources released in `onDetach` (leak-canary pass).
- Strong-skipping memoization gaps are modifiers too: `Modifier.pointerInput { }`, `draggable`, `scrollable`, `drawBehind`, `drawWithCache` lambdas are NOT `@Composable` scopes and are never auto-`remember`ed.

**Lambda providers**
- Cross-boundary hot values: `fun Parent(scrollOffset: () -> Float) { Child(scrollOffset) }` with `Box(Modifier.offset { IntOffset(0, scrollOffset().toInt()) })` — the lambda is a stable reference; Parent never reads the hot state and never recomposes per frame.
- Reading a provider inside the composable body (rather than inside a Layout/Draw lambda) defeats the purpose; the deferred shape is the whole point.

### Lists & Scrolling

**Keys**
- MUST supply `key = { it.id }` (server-side stable id) on every `items(...)` where identity outlives a single composition. Without keys, identity is the index: insert/remove/reorder discards composition state (focus, expansion, scroll restoration) past the change point and breaks `animateItem()`.
- Banned keys: list index; `UUID.randomUUID()` evaluated per emission (fresh key every recomposition = strictly worse than no key — cached composition discarded every time); `hashCode()` of a mutable object (changes when fields mutate); new objects created in the key lambda (use primitive stable identifiers).

**contentType**
- Lazy layouts maintain a per-`contentType` composition-reuse cache (RecyclerView view-type analog); with matching type, the recycled slot's composition is reused, else it is discarded and rebuilt.
- MUST supply a stable discriminator (`it::class` or a sealed-type string/enum) for heterogeneous feeds (cards + headers + ads + dividers). Homogeneous lists: optional (single inferred type). Without it on a mixed feed, every row crossing a type boundary is a fresh build and all types compete for one reuse pool.
- Applies identically to `LazyVerticalGrid` (with `span = { … GridItemSpan(maxLineSpan) … }` for full-width headers) and staggered grids; `item(contentType = "header") { … }` works for single items.

**animateItem**
- `Modifier.animateItem()` (GA in Compose Foundation/UI 1.7+, replaces experimental `animateItemPlacement`) animates insert/remove/reorder — but ONLY with a stable `key`; without it identity is index-based, an insert at position 0 looks like every-row-changed and the animation silently no-ops. Default fade-in/fade-out/placement springs are usually right; tune `fadeInSpec`/`fadeOutSpec`/`placementSpec` only on design request.

**Item-level hygiene**
- Validate item composable stability FIRST — an unstable item parameter cancels every gain from key/contentType (row recomposes on every scroll-driven snapshot tick).
- Hoist allocation-heavy values out of the items lambda (`painterResource`, `BorderStroke`, color resolutions, shapes) to the screen-level composable or `remember` at the `LazyColumn` parent — the lambda runs once per item per scroll-driven recomposition.
- Modifier chains are deduplicated/interned by Compose structurally — hoisting `remember { Modifier.fillMaxWidth().padding(16.dp) }` is a micro-optimization; hoist only when profiling proves it.
- Lambdas in `items { }`: strong skipping does NOT memoize inside `LazyListScope.items { }` (a DSL builder, not `@Composable`) — hoist manually: `val onClick = remember(snack.id) { { vm.select(snack.id) } }`, or better, pass the id-aware callback once (`onClick = vm::select` — method reference is stable).
- No extra inline composable wrappers around items lambdas (`Row { items { } }`) "to force" skippability — `Row`/`Column`/`Box` are not restartable/skippable in the first place.
- No sort/filter/expensive computation inline in `items {}` — compute upstream (reducer/ViewModel) or in `remember`, pass pre-computed data.

**When to be lazy**
- Large or dynamic lists: `LazyColumn`/`LazyRow` compose only visible items. Small fixed lists (<10): plain `Column`/`Row` — no lazy machinery.
- DSL: `item { }` for single header/footer/divider, `items(list, key) { }`, `itemsIndexed(list) { index, item -> }` when the index is needed.
- Grids: `LazyVerticalGrid(columns = GridCells.Fixed(3))` or responsive `GridCells.Adaptive(minSize = 120.dp)`; Pinterest layouts: `LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(2))`.
- Pagers: `rememberPagerState(pageCount = { pages.size })`; programmatic scroll via `LaunchedEffect(targetPage) { pagerState.animateScrollToPage(targetPage) }`.
- Pass `contentPadding = innerPadding` (Scaffold) and `verticalArrangement = Arrangement.spacedBy(…)` instead of per-item spacer padding.

**Nested scrolling**
- MUST NOT nest same-axis scrollable containers: `Column(verticalScroll)` around a `LazyColumn` (or `verticalScroll` content inside a lazy item) makes two scroll containers fight / crash on infinite constraints — use a single `LazyColumn` with mixed `item { }` blocks. Different axes are OK (inner `LazyRow` inside a `LazyColumn` row). Complex coordination: `Modifier.nestedScroll(connection)` with a custom `NestedScrollConnection`.

**Scroll-derived UI**
- Keep `LazyListState` local (never in ViewModel/MVI state). Scroll-driven booleans via `derivedStateOf` (see Recomposition): `val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }`; FAB action via `rememberCoroutineScope { listState.animateScrollToItem(0) }`.

**Prefetch tuning (LazyLayoutCacheWindow)**
- Lazy layouts pre-compose items just outside the viewport. Foundation 1.9+: configurable `LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)` (Dp extents, NOT item counts) — an `@ExperimentalFoundationApi` factory plumbed through `rememberLazyListState(cacheWindow = window)` (or the grid/staggered equivalent state), then `LazyColumn(state = state)`; it is NOT a parameter on `LazyColumn`/`LazyRow`/`LazyVerticalGrid`/`LazyHorizontalGrid` themselves; every call site needs `@OptIn(ExperimentalFoundationApi::class)`.
- Foundation 1.10+: prefetch composition is PAUSABLE by default — the prefetch composer suspends mid-item at the frame deadline and resumes next idle frame, so one heavy item no longer blows the budget. Upgrade before manually tuning windows.
- MUST stay on defaults unless a Macrobenchmark `FrameTimingMetric` baseline proves prefetch is the bottleneck; item-level fixes (keys, contentType, stability) MUST be complete first — a wider window only spreads wasted work for non-skippable items.
- MUST NOT widen "to be safe": e.g. 2000.dp ahead/behind pre-composes a screenful extra per scroll tick, raises memory pressure (image-heavy items), and wastes most prefetched work on a direction reversal. Start narrow (200/100 dp), widen only on measured need; revert if `frameDurationCpuMs` p95/p99 didn't improve.
- Items hosting inner lazy layouts (row with `HorizontalPager`/inner `LazyRow`): implement `NestedPrefetchScope` so the outer prefetch composes the inner layout's first item — otherwise the first horizontal swipe still pays inner composition cost.
- MUST NOT bundle cache-window tuning with key/stability fixes in one PR — one variable at a time, re-measure. If frames still drop on 1.10+, profile per-frame: if measure or draw dominates, prefetch can't help — simplify item content (smaller images, fewer subcompositions, `graphicsLayer` for alpha/scale).
- Verify: jank decreases on the same fixed scroll journey, release+R8+real device; Layout Inspector shows prefetch composing on idle frames not inside scroll frames; memory profiler shows no heap regression.

**Verification (lazy surfaces)**
- During a controlled scroll each item recomposes at most once per real state change; Layout Inspector counts plateau rather than climb monotonically; insert/remove/reorder preserve per-item state; `animateItem` runs; `composables.txt` shows the item composable `restartable skippable` with all params `stable`/`runtime`.

### Measurement & Baseline Profiles

**Measure in release, always**
- Compose ships unbundled; debug runs the runtime interpreted with JIT warmup, Live Literals wraps every constant (`0.dp`, `"Hello"`, `Color.Red`) in a getter the recomposer treats as dynamic (false-unstable reports, inflated counts), no R8 optimizations, and Layout Inspector counts are sampled/approximate. Cited debug→release deltas: ~75% startup gain, ~60% frame-render gain.
- MUST measure in the release variant, R8 on, on a real physical device (prefer low-end: what users feel; then Cuttlefish `aosp_cf_x86_64_phone-userdebug`; emulator only as last resort, never for frame budgets). Emulator/debug numbers are diagnostic, never evidence.
- MUST NOT report debug perf numbers without an explicit "debug build, treat as approximate" caveat in the same sentence; MUST NOT equate Layout Inspector counts (sampled, debug-only) with `@TraceRecomposition` counts (deterministic, instrumented).
- MUST quote variant + device + compilation mode + iteration count with every startup/frame number (bug-report template material). A number without provenance is unreviewable.
- Live Literals: no longer togglable in the Kotlin 2.0+ Compose Compiler DSL — `featureFlags` exposes only `IntrinsicRemember`, `OptimizeNonSkippingGroups`, `PausableComposition`, `StrongSkipping`; legacy 1.5.x `liveLiterals` property was deprecated. Build release — it is off there by default.
- Runtime phase invalidation checks happen the same, but confirm any fix in release; a separate `benchmark` build type (release-equivalent, debug signing) is the recommended measurement vehicle.
- For runtime profiling without instrumentation: release + `ndk { debugSymbolLevel = "FULL" }` (NATIVE frames only — Kotlin frame readability comes from `mapping.txt`, always produced when `isMinifyEnabled = true`) + R8 retrace.

**Baseline Profiles**
- A Baseline Profile ships an AOT compilation hint list inside the APK so ART pre-compiles hot paths at install (`~30% faster cold start, ~40% smoother first scroll on profiled journeys`). Every Compose app benefits — Compose is never AOT'd by the system image.
- Setup: AGP 8.2+ Studio template **New Module → Baseline Profile Generator** → `:baselineprofile` module with plugins `com.android.test` + `androidx.baselineprofile`, `targetProjectPath = ":app"`, `benchmark` build type (`isDebuggable`, debug signing, `matchingFallbacks += listOf("release")`), deps `androidx.benchmark:benchmark-macro-junit4`, junit-ext, uiautomator, espresso; `instrumentationRunnerArguments["targetAppId"]` from built artifacts. App module: apply `androidx.baselineprofile` + `baselineProfile(project(":baselineprofile"))` (missing it = profile generated but never packaged) + mirror `benchmark` build type `initWith(getByName("release"))`; `baselineProfile { automaticGenerationDuringBuild = false }`.
- Manifest: `<profileable android:shell="true" tools:targetApi="29"/>` under `<application>` — without it simpleperf/perfetto can't attach and FrameTimingMetric comes back empty.
- Generate: `./gradlew :app:generateBaselineProfile` (variant-specific: `generateReleaseBaselineProfile`) → `app/src/<variant>/generated/baselineProfiles/baseline-prof.txt` (merge to `main` if variants share).
- Generator test: `BaselineProfileRule().collect(packageName = …) { startActivityAndWait(); … }` MUST cover cold startup AND at least one scroll fling AND key navigation (detail entry, back) — startup-only leaves first-scroll cold. Use `Modifier.testTag("feed")` + `By.res(...)` selectors (text matchers break with localization; semantics-merged text nodes need `testTagsAsResourceId = true` on the root if `findObject` returns null); `feed.setGestureMargin(device.displayWidth / 5)` before `fling(Direction.DOWN)` or gesture-nav eats the fling and you measure an idle screen.
- Verify shipping: APK Analyzer must show `assets/dexopt/baseline.prof` + `assets/dexopt/baseline.profm`; or `unzip -l app-release.apk | grep baseline`.
- Measure: separate `MacrobenchmarkRule` tests; `StartupTimingMetric` (`timeToInitialDisplayMs`, `timeToFullDisplayMs`) + `StartupMode.COLD` + `iterations = 10`; `FrameTimingMetric` (`frameDurationCpuMs` P50/P90/P95/P99, `frameOverrunMs`, headline = P95 overrun; negative = on time) + `iterations = 5`. MUST use `CompilationMode.Partial(BaselineProfileMode.Require)` so a missing/stale profile fails loudly (`UseIfAvailable` silently measures an unprofiled build); keep an A/B sibling `CompilationMode.None` test to PROVE the delta; report MEDIANS not means (one thermal-throttled run drags means).
- TTFD requires the app to report the meaningful first frame: `ReportDrawn` (first frame is the one), `ReportDrawnWhen { state.items.isNotEmpty() }` (typical: state hydrated), `ReportDrawnAfter { awaitFirstFrame() }` — from `androidx.activity.compose`. Without them `timeToFullDisplay` falls back to TTID and undercounts (an empty `LazyColumn` draws a frame too).
- `BenchmarkRule` (micro) vs `MacrobenchmarkRule` (out-of-process APK): in-process for pure hot loops (a `derivedStateOf` calc); Macro for startup/scroll/navigation/AOT effects. Other metrics: `TraceSectionMetric("Feed:firstComposition", mode = First/Sum/Min/Max/Count/Average)` paired with `androidx.tracing.trace("…") { }` in composables; `MemoryUsageMetric(Mode.Last/Max)` (noisy); `PowerMetric(Type.Battery())` (Pixel 6+ only).
- CompilationMode matrix: `None` (interpreted — A/B control), `Partial(Require)` (shipped behavior, fails without profile), `Partial(UseIfAvailable)` (avoid), `Partial(warmupIterations = N)` (JIT-warm approximation), `Full` (upper-bound reference, never user-real), `Ignore` (almost never).
- CI: per-test JSON at `:baselineprofile/build/outputs/connected_android_test_additional_output/...-benchmarkData.json`; parse `median` with `jq` against a threshold; track deltas across PRs (noise band ±5%), not absolutes.
- Harness mistakes to grep for: missing `<profileable>`; plugin absent from either module; running the debug variant (`Require` then fails); missing `baselineProfile(...)` dependency; wrong `packageName` (must match release applicationId incl. suffix); null `findObject` (test tag debug-only or merged semantics); flings without gesture margin. Toggle off IDE heatmap/logcat instrumentation before runs.

**Runtime recomposition tracing (`@TraceRecomposition`)**
- Apply Gradle plugin `com.github.skydoves.compose.stability.analyzer` to the composable-owning module; `composeStabilityAnalyzer { enabled.set(true) }` (compile-time weaving switch); annotate suspects: `@TraceRecomposition(traceStates = true)` (`traceStates` diffs internal `mutableStateOf` reads too — start `true`, drop to `false` once diagnosed to keep the annotation as a quiet tripwire).
- Runtime gate in `Application.onCreate`: `ComposeStabilityAnalyzer.setEnabled(BuildConfig.DEBUG)` — or a dedicated `BuildConfig.ENABLE_RECOMPOSITION_TRACE` for release-with-symbols profiling builds. MUST NOT hard-code `true`; MUST NOT ship enabled (logcat I/O per recomposition on hot rows + PII captured into log strings).
- Read: `adb logcat -s Recomposition:D` → `[Recomposition #N] Composable (tag) (ms)` lines with per-`[param]`/`[state]` changed (old → new) / unchanged detail; `#N` is per-restart-scope cumulative — `#50` within two on-screen seconds is the smoking gun; post-fix expectation: one `#1` on initial composition and nothing during the scenario.
- Counts have no meaning without context (scenario, device, variant) — track deltas across a fixed scenario, don't declare raw-count SLOs.
- Name both the composable AND the changing parameter when reporting. Annotate a small deliberate set (screen's hot composables, the row composable) — annotating everything kills signal.
- Chain to fixes: unstable param → stabilization; lambda capture/Flow → strong-skipping/flow skills; wrong phase → deferral; and gate the next regression with CI `stabilityCheck` (tracing alone is half the workflow).
- For system-trace visibility of measure/draw work add `androidx.compose.runtime:runtime-tracing` (Perfetto shows `Compose:recompose`, `Compose:applyChanges`).

**Audit orchestration (broad symptoms)**
- Enter only for "the app feels sluggish everywhere" / perf-sprint kickoff / pre-release gate; with a named symptom go straight to the focused skill.
- Four phases, in order, never skipped: **Measure** (release+R8 device baselines BEFORE any code change: startup median ≥10 iterations; scroll P50/P90/P99 ≥5) → **Diagnose** (release compiler reports; Layout Inspector counts; `@TraceRecomposition`; rank hotspots by FREQUENCY × COST, not compiler severity) → **Fix** (one named cause per PR, re-measure between fixes so each delta is attributable and bisectable) → **Verify** (regenerate + commit baseline profile, commit `.stability` baselines, CI `stabilityCheck` active, written report circulated — no report, no complete audit).
- Deliverable table per fix: skill | change | files | Macrobenchmark delta. Success metric is FrameTiming/StartupTiming improvement; skip-rate improvement is a side effect, never the target.
- Min-version appendix: Strong Skipping default — Kotlin 2.0.20+; `LazyLayoutCacheWindow` — Foundation 1.9+; pausable prefetch default — Foundation 1.10+; `stabilityConfigurationFile(s)` DSL — Compose Compiler 1.5.5+; Baseline Profile Generator template — AGP 8.2+; R8 full mode default — AGP 8.0+; `Modifier.animateItem()` + `rememberGraphicsLayer()` — Compose UI 1.7+.

### R8/Strong Skipping

**R8 for Compose**
- R8 is the only supported shrinker (ProGuard deprecated). AGP 8.0+ defaults to R8 full mode (older AGP: `android.enableR8.fullMode=true` in gradle.properties).
- Minimum correct release config: `isMinifyEnabled = true`, `isShrinkResources = true`, `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`.
- MUST use `proguard-android-optimize.txt`, NOT `proguard-android.txt` — the non-optimize default skips R8's optimization passes (the release then behaves like a poorly minified app). The most common single mistake.
- What R8 buys Compose specifically: lambda grouping (merges generated lambda classes), `sourceInformation()` stripping (removes composable metadata strings), composable-arg constant-folding (Live-Literal-wrapped constants become short-circuitable checks), `ComposerImpl` devirtualization, plus resource shrinking. Break one of these with a bad keep and the 75/60% gains evaporate.
- Compose ships correct consumer ProGuard rules per artifact (runtime, ui, foundation, material3…) — a well-configured app has NO Compose-specific keep lines; `proguard-rules.pro` must contain zero lines matching `^-keep .* androidx\.compose\.`.
- Banned keeps and why: `-keep class androidx.compose.** { *; }` (blocks all four optimizations above, bloats dex); `-keep class kotlin.Metadata { *; }` (identify the real reflective consumer — serialization/Moshi/Gson/Hilt — and keep narrowly); `-dontobfuscate` / `-dontoptimize` "for easier debugging" (measured build ≠ shipped build — ship R8 on and read stacks via retrace); blanket `-keep class **.Saver { *; }` (most Savers are kept transitively; only reflectively-loaded ones need a narrow `public static *** INSTANCE` keep); `-keep class com.company.app.data.remote.dto.** { *; }` + `@SerializedName <fields>` + `-keep class kotlin.** { *; }` + `**$WhenMappings` appear in some sources as DTO/when-mapping insurance — prefer the plugin-emitted rules (kotlinx-serialization plugin, Hilt, Moshi codegen) and verify, do not duplicate. Sources that keep annotated stability classes explicitly: `-keep @androidx.compose.runtime.Stable class **`, `-keep @androidx.compose.runtime.Immutable class **` — treat as last resort after the missing-rules reporter.
- Legitimate keep territory lives OUTSIDE Compose: `@Serializable` classes via kotlinx-serialization plugin, Hilt entry points / generated DI, reflectively-accessed `rememberSaveable` `Saver`s, any `Class.forName`/JNI/service-loader consumer, library public API surfaces.
- Do NOT add speculative keeps: after a release runtime failure, AGP 8.x writes `app/build/outputs/mapping/release/missing_rules.txt` with the exact narrow directives — add only those, narrowed to the specific class/method.
- Retrace before diagnosing any release crash: `$ANDROID_HOME/cmdline-tools/latest/bin/retrace app/build/outputs/mapping/release/mapping.txt < crash.txt` or `./gradlew :app:retraceR8DebuggingArtifact` (legacy `tools/proguard/bin/retrace.sh` is gone with sunset SDK Tools; don't look for it). Upload `mapping.txt` to the crash reporter (Crashlytics/Sentry) for automatic deobfuscation.
- R8-caused failures manifest as `ClassNotFoundException` / `NoSuchMethodError` / empty reflection results — not behavioral bugs; verify with APK Analyzer (correctly configured minification typically HALVES method count vs un-minified) and track APK size in CI so a future over-broad keep trips a wire.
- Cross-link: release measurement assumes this config; Macrobenchmark vs `CompilationMode.None` confirms the gain.

**Strong Skipping Mode**
- Default since Kotlin 2.0.20 (Compose compiler plugin `org.jetbrains.kotlin.plugin.compose`). Two behavior changes at once: (1) every restartable composable becomes SKIPPABLE regardless of parameter stability — stable params compare with `equals`, unstable with `===`; (2) every CAPTURING lambda literal inside a `@Composable` is auto-wrapped in `remember(captures)` keyed on the captured values. Capture-less lambdas are already compiler singletons and are NOT wrapped.
- Opt out: `composeCompiler { featureFlags.add(ComposeFeatureFlag.StrongSkipping.disabled()) }` — the legacy `enableStrongSkippingMode = true/false` property is deprecated (still the only switch on Kotlin 1.9.x/2.0.0–2.0.10).
- MUST verify the toolchain before attributing behavior to strong skipping (`./gradlew :app:dependencies --configuration kotlinCompilerPluginClasspathRelease | grep -i compose`); MUST verify a claim of skippability against `*-composables.txt` (reports should show `restartable skippable` on previously-unstable signatures; a missing `skippable` means an intentional annotation or a disabled mode).
- Strong skipping does NOT eliminate the need for `@Immutable`/`@Stable`: reference equality is a poor substitute for structural `equals` when the producer doesn't preserve identity — `copy()`, `map { }`, builder DSLs, and freshly allocated literals (`listOf(...)` per recomposition) all fail `===` and re-run the body every tick. Fix: stabilize the type, hoist into `remember { listOf(...) }`, or a top-level `private val ToolbarActions = persistentListOf(...)`.
- Non-memoization gaps (not `@Composable` scopes — allocate fresh unless you `remember(key)` yourself): `LazyListScope.items { }` / grid / staggered scopes, `Modifier.pointerInput { }` / `draggable` / `scrollable`, `Modifier.drawBehind { }` / `drawWithCache { }` lambdas, object expressions and SAM conversions (`object : DefaultLifecycleObserver { }`), coroutine builders in non-composable scopes.
- The report question "is it skippable?" is dead; the live question is "do its params compare equal across the recomposition the user cares about?".

**Escape-hatch annotations (`androidx.compose.runtime`)**

| Annotation | Applies to | Disables | Use when | Avoid when |
|---|---|---|---|---|
| `@NonRestartableComposable` | function / property getter | restart-scope generation | Trivial pass-throughs where bookkeeping costs more than the body (runtime-internal style) | App code — parent rebuilds it on every parent tick; trust the compiler |
| `@NonSkippableComposable` | function / property getter | param-equality skip guard | Side-effect-only composables that must observe every tick (`SideEffect` logger, telemetry tick, debug overlay) | "Must always update" hiding an unstable param or missed state read — fix upstream first; the annotation papers over the diagnostic |
| `@DontMemoize` | LAMBDA EXPRESSION site only (`@Target(EXPRESSION)`) | auto-`remember(captures)` wrap | Lambda's contract is to capture the latest per-call values (telemetry snapshot); apply at the call site `Button(onClick = @DontMemoize { … })`; type positions like `(@DontMemoize () -> Unit)` do not compile | Default UI callbacks — fresh allocation fails `===`, child recomposes per tick; measure first; sharpest of the four |
| `@ReadOnlyComposable` | function / property getter (BINARY retention) | per-call group allocation | Pure getters reading CompositionLocals/ambient state, emitting no nodes, no positional state, no content invocation, no effects (`MaterialTheme.colorScheme` getters, `fun appSpacing(): Dp = LocalDimensions.current.spacing`) | Body calls any emitting/layout/effect/`remember`/normal composable — forbidden (compiler error modern, runtime crash ancient); if an override/abstract base is not read-only, don't add it locally |
- MUST NOT use `@DontMemoize`/`@NonSkippableComposable`/etc. without a co-located one-line rationale (the next reader will assume a mistake).
- Verify effect in regenerated release reports: `@NonRestartableComposable` removes `restartable`; `@NonSkippableComposable` removes `skippable`; `@ReadOnlyComposable` adds `readonly` (or omits the call group); `@DontMemoize` is invisible in `composables.txt` — confirm with `@TraceRecomposition` (lambda param shows Changed every tick).
- Pair strong-skipping adoption with a CI stability baseline — near-universal `skippable` in reports makes eyeball regression-spotting impossible, so the baseline diff must catch flips.

### Rules & Anti-Patterns

**Composable design & naming**
- Make reusable components caller-placeable AND caller-composable: the component owns invariant structure; callers retain placement, content, and policy choices that vary by use.
- Identify every varying region; represent unconstrained caller-controlled visual regions as named `@Composable` SLOTS, never by proliferating primitive content parameters or Boolean shape flags (flag matrices).
- Keep as primitives: semantic/design-system constraints and genuinely constrained values (`checked`), and primitives in measured hot paths where a slot allocation is proven to matter.
- Optional slot = nullable with `null` default so its container AND spacing disappear when absent.
- Add a `RowScope`/`ColumnScope`/`BoxScope` receiver to a slot ONLY when caller control of that region's child layout is a public contract (e.g. `actions: @Composable RowScope.() -> Unit` in a top bar); an ordinary trailing slot inside a component-owned `Row` stays `@Composable () -> Unit` (callers choose content; the component owns order and spacing); an internal layout alone is not enough reason.
- Name free-form slots `xxxContent` (`headlineContent`, `supportingContent`, `leadingContent`, `trailingContent`); name deliberately constrained regions with a semantic noun (`title`, `icon`). Keep ONE convention within a component.
- Put repeated composable defaults and tokens in an `XxxDefaults` object.
- Component contract checklist: (1) `modifier: Modifier = Modifier` param; (2) slot params `@Composable () -> Unit` with defaults; (3) action params plain lambdas (`onClick: () -> Unit`); (4) data params are domain/UI types, not UI-tree types; (5) NO ViewModel references in leaf composables — pass state + lambdas (previews and tests depend on it).
- Extract a composable when: reused, single clear visual/behavioral responsibility, easier isolated testing, or independent recomposition-skipping helps. Don't extract when: single use, trivial one-`Text`/`Icon` wrapper, more parameters than inline clarity, or tightly coupled logic that reads clearer inline. Keep simple conditional structure inline.
- Slots accept `@Composable` lambdas, not pre-composed values (composition stays deferred and scope-aware).
- Prefer `Surface(onClick = …, shape, tonalElevation)` over `Box(Modifier.clickable)` for clickable containers (elevation, ripple, shape, color role semantics).
- State screens with ONE sealed interface (`sealed interface HomeUiState { Loading / Success(items) / Error(message) / Empty }`) handled exhaustively in `when` — never parallel `isLoading`/`isError`/`isEmpty` booleans (impossible states).
- Stateless-first: leaf composables take `value + onValueChange/callbacks` and hoist state; internal `remember { mutableStateOf }` in "reusable" components is untestable and un-reusable.
- Localization & design tokens: `Text("Save")` hardcoded is banned → `stringResource(R.string.…)`; raw `fontSize = 16.sp`/`fontWeight` banned → `MaterialTheme.typography.*`; hardcoded `Color(0xFF333333)` banned → `MaterialTheme.colorScheme.*` roles (`primary/onPrimary/surface/onSurface/surfaceVariant/error/outline`, dynamic color via `dynamicDarkColorScheme`/`dynamicLightColorScheme` on S+); domain colors beyond M3 roles go through a custom token object delivered by `CompositionLocal`; `Box(Modifier.fillMaxSize())` as screen root → `Scaffold`.
- Edge-to-edge (AGP 9 / API 35+): `enableEdgeToEdge()` in every `ComponentActivity` before `super.onCreate`; never hardcode status-bar padding; consume `WindowInsets.safeDrawing` for interactive content; apply `Modifier.imePadding()` on the screen `Scaffold` for keyboard shift; test gesture navbar + display cutout on a physical device.
- `Scaffold { _ -> … }` ignoring `innerPadding` is broken UI: pass it (`contentPadding = innerPadding` for scrollables, `Modifier.padding(innerPadding)` otherwise). `ModalBottomSheet` content MUST add `.navigationBarsPadding()` before its own padding. Complete `Scaffold` = `topBar` + `floatingActionButton` + `snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }`.
- `AnimatedVisibility` always with explicit `enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()` — defaults look janky.
- Accessibility in Compose: meaningful `contentDescription` on interactive icons, `null` only for decorative; custom `Modifier.semantics { contentDescription = "Like ${item.title}"; role = Role.Button }`; `semantics(mergeDescendants = true)` for cards; password fields use `PasswordVisualTransformation()` + `KeyboardType.Password` + `KeyboardActions(onDone = …)`.
- Previews: multi-preview annotations (`Devices.PHONE`, `Devices.TABLET`, `uiMode = UI_MODE_NIGHT_YES`) wrapping in `AppTheme`.
- Adaptive: `calculateWindowSizeClass` → `WindowWidthSizeClass.Compact/Medium/Expanded` layouts; two-pane via `Row` + `weight(0.4f)/weight(0.6f)`; foldables: `WindowInfoTracker`, keep content off the fold.
- Images in lists: Coil `AsyncImage` caches by URL but items still need stable `key`s; size images to display size (`Size.ORIGINAL` only when justified, or explicit `.size(w, h)`) — full-resolution in lists is OOM; `contentScale = ContentScale.Crop`, placeholder/error painters; `coil-compose` alone is not enough for network images — add `coil-network-okhttp`; preload the next few with `imageLoader.enqueue(ImageRequest…)`.
- Coroutines hygiene feeding UI: never `GlobalScope`; never `runBlocking` on Main (ANR; suspend with `withContext(Dispatchers.IO)`); never swallow `CancellationException` (rethrow first, then `catch (e: Exception)` — never `catch (e: Throwable)`); the CALLEE switches dispatchers (`withContext(ioDispatcher)` injected via constructor for testability); `coroutineScope { }` all-must-succeed, `supervisorScope { }` independent children; blocking IO never on `Dispatchers.Default`; loops check `ensureActive()`; `Mutex.withLock` not `synchronized` in coroutine code; `combine` waits for every upstream's first value — seed with `onStart { emit(default) }`; `flatMapLatest`/`collectLatest` for search; `callbackFlow` requires `awaitClose { unregister() }` and non-suspending `trySend`; `channelFlow` for concurrent producers; `Semaphore(permits)` for rate limiting.
- Main-thread protection: wrap expensive work in `trace("processData") { }` from `androidx.tracing` for profiler visibility; target <16.67 ms per frame at 60fps; use `FrameTimingMetric` for scroll/interaction journeys.

**Verbatim common-mistakes tables**

meetmiyani performance mistakes:

| # | Issue | Fix |
|---|---|---|
| 1 | Unstable parameters (`MutableList`, lambdas in state models, anonymous objects) | Immutable data classes + immutable collections |
| 2 | Broad state observation — parent reads whole state, ripples through tree | Collect once at route, slice aggressively for leaves |
| 3 | Large state passed everywhere — many nodes observe unused fields | Pass only what each child renders |
| 4 | Callback recreation in hot paths (large lazy lists, nested rows) | `remember(key, callback)` for repeated rows |
| 5 | Expensive calculations during composition (parse, sort, filter, format) | Move upstream to ViewModel/domain |
| 6 | `remember` misuse — caching business state, hiding architecture issues | Use only for local UI state, expensive local objects, hot callback adaptation |
| 7 | `derivedStateOf` misuse — wrapping cheap expressions | Use only when derived from rapidly changing Compose state with coarse output |
| 8 | `rememberSaveable` misuse — entire screen state, large graphs | Use only for tiny UI-local values surviving recreation |
| 9 | State reads too high in tree (`LazyListState`, animation, keyboard state) | Read close to use |
| 10 | List recomposition — missing keys, unstable items, inline filters/sorts | Stable keys, immutable models, pre-computed data |
| 11 | Reducer emits excessive updates — same state, rebuilds on every keystroke | Guard identical transitions, emit only on semantic change |
| 12 | Ephemeral visual state in global screen state (shimmer alpha, pulse phase) | Keep visual-only state local |
| 13 | Equality pitfalls — lambdas in data classes, random IDs, mutable collections | No lambdas/mutables in data classes, stable IDs |
| 14 | Abusing `@Immutable`/`@Stable` to silence compiler | Use only to describe truth — `@Immutable` for truly immutable, `@Stable` rare in app code |
| 15 | Raw text input in MVI causing stutter (25+ fields) | `TextFieldState`/`BasicTextField2`, group fields into nested data classes, isolate read scopes |
| 16 | State reads in Composition phase for layout/draw values | Lambda modifiers: `Modifier.offset { IntOffset(scrollOffset, 0) }` |

meetmiyani API decision table:

| API | Use it for | Do not use it for |
|---|---|---|
| `remember` | local objects/state across recompositions | business state, repo results, derived domain data |
| `rememberSaveable` | small UI-local state needing restoration | whole screen state, large graphs, domain objects |
| `derivedStateOf` | reducing downstream updates from fast-changing Compose state | cheap string concatenation, reducer-owned derivations |
| `key` | preserving identity in dynamic children/lists | hiding bad state models |
| `LaunchedEffect` | collecting UI effects, startup event, one-shot route work | screen business logic in leaves |
| `DisposableEffect` | register/unregister listeners with cleanup | long-running business jobs |
| `produceState` | bridging external async/callback source to local Compose state | replacing a real ViewModel |
| `snapshotFlow` | turning Compose state reads into `Flow` operators | normal state rendering |
| `collectAsState` | collect `StateFlow` into Compose | collecting everywhere in the tree |
| lifecycle-aware collection | Lifecycle host integration (multiplatform since lifecycle 2.8+) | common leaf components |
| stable callbacks | hot repeated UI paths | every single callback everywhere |

meetmiyani list anti-patterns:

| Anti-pattern | Fix |
|---|---|
| No keys on mutable lists | Always provide stable domain ID keys |
| Index-based keys | Use `it.id`, not position index |
| Expensive computation inside item lambda | Compute upstream in reducer, pass pre-computed data |
| Inline `filter`/`sort` inside `items {}` | Sort/filter in reducer or ViewModel before emitting state |
| `LazyColumn` for 5 fixed items | Use `Column` for small fixed lists |
| Creating new objects in `key` lambda | Use primitive stable identifiers |
| Missing `contentType` on multi-type lists | Provide `contentType` for efficient reuse |

meetmiyani coroutines/Flow anti-patterns:

| Anti-pattern | Why it hurts | Fix |
|---|---|---|
| `GlobalScope.launch { }` | No lifecycle, memory leak | `viewModelScope` or structured scope |
| `runBlocking` on Main | Blocks UI, ANR | `launch` / `async` from coroutine scope |
| Swallowing `CancellationException` | Zombie coroutines | Always rethrow |
| Blocking I/O on `Dispatchers.Default` | Starves CPU pool | `Dispatchers.IO` |
| Non-suspending loop without `ensureActive()` | Ignores cancellation | Check `isActive` / `ensureActive()` |
| `stateIn` per function call | Leaks hot flows | Declare as `val`, create once |
| `catch (e: Throwable)` | Catches everything including OOM | `catch (e: Exception)` + rethrow `CancellationException` |
| Hardcoded `Dispatchers.IO` | Untestable | Inject dispatcher as constructor param |
| `combine` without initial values | No output until all emit | `onStart { emit(default) }` |

compose-kotlin banned antipatterns (AI-hallucination lookup — every row is a production bug waiting to ship):

| # | Banned (WRONG) | Correct (RIGHT) | Why it breaks |
|---|---|---|---|
| 1 | `GlobalScope.launch { }` | `viewModelScope.launch { }` | Leaks past lifecycle |
| 2 | `runBlocking { }` on main | `suspend` + proper scope | ANR |
| 3 | `Text("Hello")` in UI | `stringResource(R.string.hello)` | No localization |
| 4 | `collectAsState()` Android | `collectAsStateWithLifecycle()` | Background battery drain |
| 5 | `_state.value = x` | `_state.update { x }` | Race + non-atomic |
| 6 | `mutableStateListOf` in VM | `StateFlow<List<T>>` + immutable list | Not snapshot-safe |
| 7 | `items(list)` no key | `items(list, key = { it.id })` | Lost item state |
| 8 | Pass `ViewModel` to child | Pass lambdas / state | Broken previews + tight coupling |
| 9 | `remember { mutableStateOf }` for screen state | ViewModel `StateFlow` | Dies on rotation |
| 10 | `Dispatchers.IO` around Room | `viewModelScope.launch` only | Double dispatch |
| 11 | `@Provides` interface bind | `@Binds` interface | Extra allocation |
| 12 | Nav arg = whole object | Nav arg = `id: String` | Process death crash |
| 13 | `fallbackToDestructiveMigration()` prod | Explicit `Migration` | Data wipe |
| 14 | `Modifier.clickable` before `clip` wrong order | padding→clip→bg→clickable | Wrong hit target |
| 15 | Sort inside `LazyColumn` items | `remember` or VM sort | Recompose every frame |
| 16 | `derivedStateOf` without `remember` | `remember { derivedStateOf { } }` | Recreated each frame |
| 17 | Backwards write in `@Composable` | Write in event handlers only | Infinite recompose |
| 18 | `!!` on nullable state | Smart cast / `when` | NPE |
| 19 | Ktor `HttpClient()` no engine | `HttpClient(OkHttp)` / `Darwin` | Runtime crash |
| 20 | Hardcode `compileSdk 34` new app | `compileSdk 35` + edge-to-edge | Play policy |
| 21 | `SharedPreferences.edit().apply()` | `dataStore.edit { }` suspend | Race + main-thread ANR risk |
| 22 | `collectAsLazyPagingItems()` in VM | `pager.flow.asState()` + presenter collect | Wrong layer + stale paging |
| 23 | Coil `AsyncImage` without network dep | `coil-compose` + `coil-network-okhttp` | Images never load |
| 24 | `contentDescription = null` on icons | Meaningful description or `null` only if decorative | TalkBack failure |
| 25 | `kapt` for Hilt/Room new project | KSP (`ksp(...)`) | Slow builds, K2 incompatibility |

compose-kotlin Compose anti-patterns:

| Banned | Fix |
|---|---|
| Hardcoded `Text("Save")` | `stringResource(R.string.action_save)` |
| `collectAsState()` on Android | `collectAsStateWithLifecycle()` |
| `Modifier.clickable` before `padding` when touch target should exclude margin | Order: padding → clip → background → clickable |
| `items(list)` without key | `items(list, key = { it.id })` |
| Raw `fontSize = 16.sp` | `MaterialTheme.typography.bodyLarge` |

compose-kotlin performance anti-patterns:
- No keys in LazyColumn → positional identity breaks on reorder/delete.
- `@Stable` on class with plain `var` → lie to compiler; use `mutableStateOf` for observable state.
- `derivedStateOf` for everything → overhead for simple pass-throughs; only when input changes faster than output.
- `remember {}` without keys → stale value when params change; add dependencies as keys.
- Full-resolution images in lists → OOM; Coil size constraints or thumbnail URLs.
- Skipping baseline profiles → free 20–40% startup improvement left on the table.
- Nested scrollables: `Column(verticalScroll) { LazyColumn }` crashes or measures infinitely — single `LazyColumn` with mixed `item {}` blocks.

piyush compose-ui common mistakes:

| ❌ Wrong | ✅ Right |
|---|---|
| `collectAsState()` | `collectAsStateWithLifecycle()` |
| Ignoring `innerPadding` | `contentPadding = innerPadding` |
| Multiple state booleans | Single `sealed interface UiState` |
| String routes | `@Serializable` route objects |
| Hardcoded colors | `MaterialTheme.colorScheme.*` |
| Logic in `@Composable` | Logic in ViewModel |
| StateFlow for events | SharedFlow for events |
| No `key=` in LazyColumn | `items(list, key = { it.id })` |

piyush performance common mistakes:
- Passing `List<T>` to Composable — use `ImmutableList<T>` or wrap in `@Immutable` class.
- Computing in composition — move to `remember {}` or `derivedStateOf {}`.
- `isMinifyEnabled = false` in release — dead code ships in the APK.
- Loading full-resolution images — always size to display size.
- DB queries on the Main thread — always `withContext(Dispatchers.IO)`.
- No baseline profile — first launch is 40–60% slower without it.

**Meta rules (apply to every perf claim)**
- Skippability is a diagnostic, not a KPI; stability config is a contract, not a magic spell; debug builds lie; measure before AND after every change; change one axis/variable at a time; every opt-out annotation carries a co-located rationale; never optimize a count that tracks real data changes or a correctness defect.

### Checklists

**Composable authoring (pre-write)**
- [ ] `modifier: Modifier = Modifier` declared after required params and applied to the root layout (caller first, intrinsic after).
- [ ] State hoisted to the lowest owner that needs it; leaf takes immutable state + callbacks; no ViewModel in leaves; no screen state in `remember`; previewable without app dependencies.
- [ ] Variable visual regions are slots (`xxxContent`, nullable default when optional); semantic constraints stay primitives; no boolean shape-flag matrices.
- [ ] Sealed `UiState` (no impossible-state booleans); all branches handled in `when`.
- [ ] `collectAsStateWithLifecycle()` only; no `Flow<T>` parameters; chatty flows conflated/distinctUntilChanged inside `remember(key)`.
- [ ] Effects: chosen from the API table, keyed by the semantic restart input, `rememberUpdatedState` for long-lived callbacks, `onDispose` for every `DisposableEffect` registration, no work in the body.
- [ ] `@Immutable`/`ImmutableList` on UI state models crossing composable boundaries; no untruthful annotations.
- [ ] Strings via `stringResource`, colors via `colorScheme`, type via `typography`; `Scaffold` insets consumed; `imePadding`/`navigationBarsPadding` where needed; semantics + 48dp targets.

**Stability fix loop**
- [ ] Reports generated from release variant (`assembleRelease -PcomposeCompilerReports=true`); all four files present.
- [ ] Every `restartable`-without-`skippable` composable on a hot path listed with its first `unstable` param; each traced to a `classes.txt` root-cause field.
- [ ] `runtime`/`@static`/`readonly` markers recognized as non-problems; unstable class crossing no composable boundary → no fix.
- [ ] Fixes follow the waterfall: val + immutable structure → truthful `@Immutable`/`@Stable` → `stabilityConfigurationFiles` (narrowest pattern, dated comment, contract verified) → `StableHolder` only as last resort.
- [ ] Re-run reports: previously unstable params now `stable`/`runtime`; no annotation added over a `var` or unstable nested type; `@TraceRecomposition` recomposition counts dropped in release; `.stability` baseline re-dumped and reviewed.

**Recomposition reduction**
- [ ] One transition reproduced with counts; axis named (stability / read phase / back-write); false leads checked (data really changed? lazy item legitimately recomposing?).
- [ ] Hot reads inside lambda modifiers (`offset {}`, `graphicsLayer {}`, `drawWithCache`) or `() -> T` providers; no value-form modifier fed hot state; `graphicsLayer` near chain top for whole-unit transforms.
- [ ] No writes to state read in the same pass; derived values via `remember(keys)`; mutations from events/effects.
- [ ] `derivedStateOf` only when input freq > output freq, always `remember`-wrapped, captured non-state values as keys; side effects via `LaunchedEffect { snapshotFlow{}.collect{} }`.
- [ ] No `BoxWithConstraints`/`Scaffold` nesting or inside lazy items; constraint reads via `onSizeChanged`/`layout` where composition isn't needed; retained `SubcomposeLayout` has `SubcomposeSlotReusePolicy` and a hoisted/precomposed content lambda.
- [ ] Sub-composables extracted so narrow scopes invalidate; state read close to use; visual-only state local.

**Effects & flow**
- [ ] No `LaunchedEffect(Unit)` for one-shot non-suspending work (use `remember`); no `RememberedEffect` missed where a sync key-reaction wastes a coroutine; no `SideEffect` doing per-frame or allocating work.
- [ ] Every effect key justified; `rememberUpdatedState` lazily read; `ViewModelStoreScope(key)` for per-row VMs.
- [ ] `grep "collectAsState("` → zero (except same-scope-created flows); `grep ": Flow<" ` on composables → zero; backgrounding stops upstream emissions; no `CREATED` minActiveState.

**Lazy lists**
- [ ] `key = { it.id }` everywhere; never index/UUID-per-emission/hashCode/mutable-derived; `contentType` on every mixed feed; `animateItem()` only alongside stable keys.
- [ ] Item params all stable/runtime; painters/shapes/borders/callbacks hoisted (`remember(id) { { … } }` or method refs) out of items scope; no filters/sorts/allocation inside `items {}`.
- [ ] No same-axis nested scroll; `LazyListState` local; scroll-derived booleans via `derivedStateOf`; small fixed lists use `Column`/`Row`.
- [ ] Prefetch: on defaults unless FrameTimingMetric proves otherwise; `LazyLayoutCacheWindow` via `rememberLazyListState(cacheWindow = )` with `@OptIn(ExperimentalFoundationApi::class)`, Dp extents, memory check, one-variable PR; `NestedPrefetchScope` before widening for nested pagers.

**Modifier chains**
- [ ] Chain read top-to-bottom as nested wrappers; background/padding side matches intent; clip before background or `background(color, shape)`; clickable position matches the intended hit area (48dp rule deliberate); `graphicsLayer` scope covers exactly the visual unit.
- [ ] No `Modifier.composed { }` in the module; custom modifiers are `Modifier.Node` + `data class ModifierNodeElement` with in-place `update`, built-in `coroutineScope`, `onDetach` cleanup, no composition-scoped references, `DelegatingNode` past ~3 interfaces.
- [ ] No `remember { Modifier.… }` hoist without a FrameTimingMetric regression to point at.

**Measurement / release readiness**
- [ ] Release build: `isMinifyEnabled`, `isShrinkResources`, `proguard-android-optimize.txt`, zero `androidx.compose.**` keeps, `missing_rules.txt`-driven narrow keeps only, mapping.txt uploaded.
- [ ] Baseline profile generated for startup AND scroll journey, APK contains `assets/dexopt/baseline.prof(.profm)`, measured with `CompilationMode.Partial(BaselineProfileMode.Require)` vs `None` A/B, medians over ≥10/≥5 iterations, `<profileable android:shell="true"/>` present, TTFD wired via `ReportDrawn*`.
- [ ] Every published number carries variant + device + compilation mode + iterations; heatmap/logcat tracing toggled off during benchmark runs; `ComposeStabilityAnalyzer.setEnabled` debug-gated; strong-skipping opt-out annotations all annotated with rationale; CI runs `assemble + :stabilityCheck` in one job with baselines committed.
- [ ] Audit phases completed in order with the written Before/After report (startup median, scroll P50/P90/P99 deltas) and committed regeneration of profile + stability baselines.

## DOMAIN: Security, Auth, Biometrics & Permissions

### Secrets Management

**Golden rule: anything packaged into the APK is recoverable by an attacker.** A decompiled APK exposes `BuildConfig`, `strings.xml`, native libs, and asset files. Local dev conveniences (`local.properties`, Secrets Gradle Plugin, env injection) keep secrets out of *source control* but NOT out of the *shipped app*.

Wrong / right:

```kotlin
// WRONG — committed to git AND visible in decompiled APK
const val API_KEY = "sk-1234567890abcdef"
// <string name="api_key">sk_live_abc123...</string>   // strings.xml
```
```kotlin
// BETTER — not in source, still in APK (decompile-recoverable)
// build.gradle.kts
buildConfigField("String", "API_KEY", "\"sk-1234567890abcdef\"") // NEVER a real key literal
// Access: val apiKey = BuildConfig.API_KEY

// ✅ Inject from gitignored local.properties (dev only; still ships in APK)
val apiKey = gradleLocalProperties(rootDir, providers).getProperty("API_KEY") ?: ""
buildConfigField("String", "API_KEY", "\"$apiKey\"")

// ✅ BEST — server-side proxy. Client → YOUR backend → third-party API.
// The app NEVER holds the real key.
```

- Use the Secrets Gradle Plugin (`com.google.android.libraries.mapsplatform.secrets-gradle-plugin`) to read `local.properties` automatically — keeps secrets out of VCS, **not** out of the app.
- Separate keys per build type:

```gradle
buildTypes {
    debug   { buildConfigField "String", "API_KEY", "\"${project.findProperty("DEV_API_KEY")}\"" }
    release { buildConfigField "String", "API_KEY", "\"${project.findProperty("PROD_API_KEY")}\"" }
}
```

- `.gitignore` for local secrets:

```gitignore
local.properties
*.jks
*.keystore
keystore.properties
signing.properties
google-services.json   # if it contains sensitive project config
sentry.properties
.env
```

- API-key access control: restrict by package name + signing cert (Google Maps by package+SHA-1; Firebase auto package-restricted; custom APIs verify server-side). Monitor usage: unusual patterns, unexpected geos, high volume per device.
- CI guard — fail build on hardcoded secrets:

```yaml
- name: Check for hardcoded secrets
  run: |
    if grep -rn "AIza\|sk_live\|-----BEGIN" --include="*.kt" --include="*.xml" app/; then
      echo "Potential secrets found in source code!"; exit 1
    fi
```

Never commit: `*.jks`/`*.keystore`, `google-services.json` w/ prod keys, `sentry.properties` auth tokens, `.env`, API keys in source. Use CI secrets (`${{ secrets.KEYSTORE_PASSWORD }}`) for signing, never files in repo. Upload ProGuard/R8 mapping to crash reporter (never the map itself to the app).

### Secure Storage & Keystore

**Storage trust ranking:** Android Keystore (hardware) > internal storage (app sandbox) > encrypted file/prefs > external storage (never for secrets).

Internal storage: sandboxed per app, auto-deleted on uninstall, no permission needed — use for PII, tokens (encrypted, key anchored in Keystore), auth data. **Never** `MODE_WORLD_READABLE`/`MODE_WORLD_WRITEABLE` (deprecated/insecure) — use a ContentProvider to share instead.

External storage: no security enforcement; any app with `WRITE_EXTERNAL_STORAGE` can read (≤ Android 10); Android 11+ Scoped Storage isolates better. Store only non-sensitive data (or encrypt with Keystore-anchored key); validate/hash-verify all data read back; never store executables/class files (if forced, cryptographically verify before dynamic load). Check `Environment.getExternalStorageState() == MEDIA_MOUNTED`. Cache: internal `cacheDir` for ≤1 MB, `externalCacheDir` for >1 MB; cache is cleared by system anytime, never critical data.

Plain `SharedPreferences` (MODE_PRIVATE) for prefs/settings/UI state ONLY. **Never store:** passwords/raw credentials, auth tokens, encryption keys, sensitive user data. Use it never for cross-app sharing (use ContentProvider). Modern alt: `dataStore.edit { }` over `SharedPreferences.edit().apply()` (avoids race/main-thread ANR).

**Android Keystore System** — long-term key storage; keys never leave secure hardware (when available); hardware-backed. Provider MUST be specified for Keystore only:

```kotlin
// Keystore: ALWAYS pass "AndroidKeyStore" provider
KeyGenerator.getInstance("AES", "AndroidKeyStore")
// Everything else: do NOT specify a provider — let system pick best
KeyGenerator.getInstance("AES")
```

Generate + retrieve a Keystore key:

```kotlin
val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
keyGenerator.init(
    KeyGenParameterSpec.Builder("my_key_alias",
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
)
val secretKey = keyGenerator.generateKey()

val keyStore = KeyStore.getInstance("AndroidKeyStore"); keyStore.load(null)
val key = keyStore.getKey("my_key_alias", null) as SecretKey
```

Require user auth for keys protecting payments/financial, sensitive personal data, critical account ops, proof-of-presence. Do NOT require auth for background ops, non-sensitive app↔server keys, keys needed on startup.

```kotlin
.setUserAuthenticationRequired(true)
.setUserAuthenticationValidityDurationSeconds(30) // 5–30s high-sensitivity; 300+ repeated session; -1 per-operation
```
Benefits: keys bound to lock screen, auto-invalidated on lock-screen change, hardware-backed.

**Keystore vs KeyChain API:** Keystore = app-private credentials, no user-selection UI, single-app encryption. KeyChain = system-wide credentials shared across apps with user consent (VPN, Wi-Fi enterprise certs, reusable certs).

**EncryptedSharedPreferences** (small secrets: tokens, session flags). Master key `MasterKey.AES256_GCM`; key `AES256_SIV`, value `AES256_GCM`:

```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .setRequestStrongBoxBacked(true)   // StrongBox if available
    .build()
val prefs = EncryptedSharedPreferences.create(
    context, "secure_prefs", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
fun saveToken(t: String) = prefs.edit().putString("auth_token", t).apply()
fun getToken(): String? = prefs.getString("auth_token", null)
fun clearAll() = prefs.edit().clear().apply()
```

**EncryptedFile** (larger encrypted blobs): scheme `EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB`, write with `encryptedFile.openFileOutput().use { it.write(data) }`, delete-before-rewrite.

> Note: `security-crypto` Jetpack lib (EncryptedSharedPreferences/EncryptedFile) APIs deprecated in 1.1.0+; Google now recommends direct Android Keystore usage. Keep using these where already shipped, prefer Keystore for new work.

Room: never store sensitive data unencrypted. Options: full DB via SQLCipher (needs an `androidx.sqlite` `SQLiteDriver` for Room 3 — the old `SupportOpenHelperFactory`/`openHelperFactory` pattern does NOT apply to Room 3; `room3-sqlite-wrapper` is only for bridging legacy SupportSQLite call sites, not for replacing the main driver), or per-field encryption storing `encryptedSsn: ByteArray` + `ssnIv: ByteArray`, with the repository doing AES-GCM encrypt/decrypt using a Keystore key. `fallbackToDestructiveMigration()` must never ship to prod (data wipe).

### Cryptography

**Core rules:** use JCA providers + highest-level framework APIs; **NEVER implement custom crypto**; prefer `HttpsURLConnection`/`SSLSocket` for comms. Standard impls are tested and receive security updates; custom ones are error-prone.

Recommended algorithms:

| Class | Use |
|---|---|
| Cipher | AES CBC or **GCM**, 256-bit (`AES/GCM/NoPadding`) |
| MessageDigest | SHA-2 family (`SHA-256`) |
| Mac | SHA-2 HMAC (`HmacSHA256`) |
| Signature | SHA-2 + ECDSA (`SHA256withECDSA`) |

AES-GCM (authenticated encryption — preferred over CBC/CTR which lack integrity; if CBC/CTR forced for compat they MUST add auth integrity protection):

```kotlin
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
cipher.init(Cipher.ENCRYPT_MODE, key)
val ciphertext = cipher.doFinal(plaintext)
val iv = cipher.iv               // store IV with ciphertext; NEVER reuse IV with same key
```
```kotlin
// Digest / sign / verify
MessageDigest.getInstance("SHA-256").digest(message)
Signature.getInstance("SHA256withECDSA").apply { initSign(key); update(message) }.sign()
Signature.getInstance("SHA256withECDSA").apply { initVerify(pub); update(message) }.verify(sig)
```

Bank-grade Keystore AES-256-GCM (IV prepended, 12-byte IV, 128-bit tag):

```kotlin
// GCM_TAG_LENGTH=128; GCM_IV_LENGTH=12; TRANSFORMATION="AES/GCM/NoPadding"
fun encrypt(data: ByteArray, key: SecretKey): ByteArray {          // [IV][ciphertext+tag]
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return cipher.iv + cipher.doFinal(data)
}
fun decrypt(encrypted: ByteArray, key: SecretKey): ByteArray {
    val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
    val ct = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
    return cipher.doFinal(ct)
}
```

Software fallback (no TEE/StrongBox, rare): `KeyGenerator.getInstance("AES").init(256, SecureRandom())`, same AES/GCM/NoPadding. **Store the software key derived (e.g., PBKDF2 from user password), never hardcoded, never plaintext in SharedPreferences.** Always pass an explicit IV for password-based encryption — never rely on default IV generation.

**TEE vs StrongBox:** Android Keystore = system key storage, hardware-backed when present. TEE = isolated CPU mode (ARM TrustZone), most devices API 24+, limited side-channel/tamper resistance. StrongBox = dedicated secure-element chip (API 28+, select devices), physical tamper resistance, key extraction near-impossible, high side-channel resistance.

| Feature | TEE | StrongBox |
|---|---|---|
| Hardware isolation | CPU trust zone | Dedicated chip |
| Side-channel resistance | Limited | High |
| Tamper resistance | Software-level | Physical |
| Key extraction | Difficult | Near impossible |
| Availability | Most devices API 24+ | API 28+ (select devices) |

Detect StrongBox: `context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)` (API 28+); enable with `KeyGenParameterSpec.Builder.setIsStrongBoxBacked(true)`.

Avoid deprecated: explicitly-requested Bouncy Castle providers; `Crypto` provider (removed Android 9/API 28); PBE without explicit IV; `security-crypto` APIs in 1.1.0+.

### Network Security & Pinning

**Always TLS/HTTPS** with trusted CAs; `HttpsURLConnection` for web, `SSLSocket` for sockets; don't use localhost ports for sensitive IPC (use Service/Intent); don't trust data over insecure protocols; validate+sanitize all input. Prefer FCM/IP over SMS — SMS is unencrypted, weakly authenticated, spoofable/interceptable; never rely on unauthenticated SMS for sensitive commands.

Network Security Config — block cleartext, system trust anchors, user CAs only in debug:

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors><certificates src="system" /></trust-anchors>
    </base-config>
    <debug-overrides>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />  <!-- Charles/mitmproxy in debug ONLY -->
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    android:usesCleartextTraffic="false">
```
`cleartextTrafficPermitted="false"` is default-on-9+, set explicitly anyway.

OkHttp hardening: `.connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))` (TLS 1.2+), timeouts, `followRedirects/followSslRedirects`.

**Certificate pinning** — pin the server public-key SHA-256 to stop MITM even with a compromised CA. Option 1 declarative (NSC, recommended for static pins):

```xml
<domain-config>
    <domain includeSubdomains="true">api.example.com</domain>
    <pin-set expiration="2027-01-01">
        <pin digest="SHA-256">base64EncodedSHA256PinHere=</pin>       <!-- primary -->
        <pin digest="SHA-256">base64EncodedBackupPinHere=</pin>       <!-- backup -->
    </pin-set>
</domain-config>
```
Option 2 `CertificatePinner` (programmatic, dynamic/per-request):

```kotlin
val pinner = CertificatePinner.Builder()
    .add("api.example.com", "sha256/AAAA...=")     // leaf / primary
    .add("api.example.com", "sha256/BBBB...=")     // backup
    .build()
OkHttpClient.Builder().certificatePinner(pinner).build()
```

Rotation rules: **always ship a backup pin** (intermediate/root) to avoid lockout during rotation; **set `expiration`** on pin-sets so stale pins don't brick the app; monitor pin failures (log to crash reporter); test in staging before release. Extract pin hash:

```bash
openssl s_client -servername api.example.com -connect api.example.com:443 2>/dev/null \
 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der \
 | openssl dgst -sha256 -binary | openssl enc -base64
```

### Component & Intent Security

**`android:exported`:** always declare explicitly (never rely on default — default exported=true only when an intent-filter is present). Set `false` unless other apps must reach the component. Protect every exported service/receiver/activity/provider with a permission (prefer `protectionLevel="signature"` for same-developer), and validate all incoming intent data.

```xml
<activity android:name=".MainActivity" android:exported="true"> ... </activity>
<service  android:name=".MyService" android:exported="false" />
<receiver android:name=".SecureReceiver" android:exported="true"
          android:permission="com.example.myapp.RECEIVE_PERMISSION"> ... </receiver>
<permission android:name="com.example.myapp.RECEIVE_PERMISSION" android:protectionLevel="signature" />
```

Protection levels: `normal` (auto-granted, low-risk only), `dangerous` (user prompt/runtime — avoid for your own custom perms), `signature` (same signing cert, no prompt — best for your apps), `signatureOrSystem` (platform/system apps only — third parties can't use, prefer `signature`).

**Intents:** prefer explicit intents or `setPackage()`; use chooser when user picks app; avoid sensitive data in implicit intents unless receiver constrained; **NEVER use implicit intents for Services**; input-validate in every exported receiver.

```kotlin
val i = Intent(this, TargetActivity::class.java)                              // explicit ✅
val c = Intent("com.example.partnerapp.SECURE_ACTION").apply { setPackage("com.example.partnerapp") } // constrained
```

**PendingIntent** delegates YOUR app's permissions/capabilities to another process → highest-care. API 31+ requires an explicit mutability flag. Default to `FLAG_IMMUTABLE`; use `FLAG_MUTABLE` only for documented platform needs (inline reply / `RemoteInput`, certain `Notification.CarExtender`, location updates needing change, dynamic media controls). Mutable PendingIntents MUST have an explicit component or `setPackage()` — never create an implicit, mutable PendingIntent (hijack/fill-in risk).

```kotlin
// GOOD — immutable + explicit
PendingIntent.getActivity(context, REQ, Intent(context, SecureActivity::class.java),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
// Even better — set component explicitly
Intent().apply { component = ComponentName(context, SecureActivity::class.java); putExtra("data", validatedData) }
// BAD — implicit, interceptable
Intent("com.example.myapp.ACTION")            // hijackable
```
When sending a mutable PendingIntent, restrict fill-in: `pendingIntent.send(context, 0, null, null, null)`, catch `PendingIntent.CanceledException`.

**Intent redirection (nested Intent extra):** never launch a nested Intent directly. With AndroidX Core ≥ 1.9.0 use `IntentSanitizer` (`sanitizeByThrowing()` / `sanitizeByFiltering()`) with explicit allowlists (`allowComponent`, `allowAction`, `allowDataWithAuthority`, `allowType`, `allowExtra`); URI-grant flags are not allowlisted so they're stripped/throw. Else manually: reject `FLAG_GRANT_{READ,WRITE,PERSISTABLE,PREFIX}_URI_PERMISSION`, require `target.packageName == packageName`, require target `activityInfo.exported`, set `nestedIntent.component = target` before `startActivity`. Read nested via `IntentCompat.getParcelableExtra(intent, "EXTRA_NESTED_INTENT", Intent::class.java)`.

**onNewIntent / singleTop warm-boot:** apply the SAME validation in `onNewIntent` as `onCreate`, and call `setIntent(newIntent)` before processing.

**BroadcastReceivers:** exported=false unless receiving external broadcasts; protect receivers that trigger sensitive behavior; send protected broadcasts via `sendBroadcast(intent, "PERMISSION")`; rely on system **Protected Broadcasts** for system events; dynamic receivers use `RECEIVER_NOT_EXPORTED` to restrict sender. **NEVER** use `Binder.getCallingUid()` in `onReceive` to identify the sender (returns the receiver's own UID). **NEVER** use sticky broadcasts (`sendStickyBroadcast`).

**Binder/Messenger:** design without interface-specific permission checks; inherit manifest perms; `checkCallingPermission("…")` and throw `SecurityException` on deny; `Binder.clearCallingIdentity()`/`restoreCallingIdentity()` mainly for system/privileged calls. Service caller verification (partner apps): get `Binder.getCallingUid()` → `PackageManager.getPackagesForUid()` → `pm.hasSigningCertificate(pkg, sha256, PackageManager.CERT_INPUT_SHA256)`; verify **per transaction method**, NOT in `onBind()` (binder is cached, so onBind-only checks are bypassable on later binds).

**ContentProvider:** `exported="false"` if internal; else guard with `android:readPermission`/`android:writePermission`; `android:grantUriPermissions="false"` unless temporary URL access required, then scope with `<grant-uri-permission android:pathPattern="/shared/.*"/>`. Treat exported providers as public APIs even when permission-guarded. SQL injection: ALWAYS parameterize `selection` with `?` + `selectionArgs`; never concatenate user input into selection (concatenating selectionArgs is still vulnerable); use `SQLiteQueryBuilder` with a strict `projectionMap` + `setStrict(true)/setStrictColumns(true)/setStrictGrammar(true)`.

```kotlin
val selection = "user_id = ?"; val selectionArgs = arrayOf(userId.toString())  // ✅
val selection = "user_id = $userId"                                            // ❌ SQLi
```
Write-permission warning: a write grant lets attackers craft WHERE clauses that probe/exfiltrate data (update-count > 0 confirms a value exists), so **write ≈ read+write** — don't assume write is less sensitive.

**FileProvider / URI grants:** share files via `content://` URIs (FileProvider), never `file://` (insecure, throws `FileUriExposedException`). Temporary scoped access:

```kotlin
val uri = FileProvider.getUriForFile(context, "com.example.myapp.fileprovider", file)
intent.setDataAndType(uri, "application/pdf")
intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)   // or OR ..._WRITE
```
```xml
<provider android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false" android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/>
</provider>
```

**Task-hijacking / UI redressal:** avoid exported activities with custom `taskAffinity`; set `android:taskAffinity=""` on sensitive activities (malicious same-affinity apps inject activities/phishing overlays); `android:excludeFromRecents="true"` hides from recents; `android:documentLaunchMode="never"` (+ `maxRecents="1"`) prevents document-task creation. Keep sensitive activities in separate tasks; validate all incoming data (esp. deep links/exported entry points).

**Deep links:** use `android:autoVerify="true"` intent-filter (https scheme, host, pathPrefix); validate scheme, host, allowlisted path prefixes, and query params; block path traversal (`..`) and injection (`<`,`>`); `finish()` on invalid.

**Isolated processes:** `android:isolatedProcess="true"` for untrusted content (web rendering, parsing complex formats) — no app perms, no cross-process bind, no shared data. Component-alias (`<activity-alias>`) enables runtime feature-gates via `setComponentEnabledSetting`, but each alias needs its own export/permission config and can leak functionality if misconfigured.

**Signature-level for SSO/payments:** never expose sensitive functionality (SSO, payment processors) to components without `signature`-level protection. Error handling: log security violations for audit, return generic user feedback, never leak internal structures/SQL to UI.

### Data Leakage (logs/backup/webview)

**Logs — never log PII/secrets.** Forbidden: PII, passwords/credentials, auth tokens, card numbers, SSNs, addresses, phone numbers. All production logging goes through a "logs sanitizer" (tokenize/mask/redact/filter); do NOT mask when partial exposure still compromises security (e.g. passwords). R8 strips `Log` in release:

```kotlin
object SecureLog {
    fun d(tag: String, msg: String) { if (BuildConfig.DEBUG) Log.d(tag, msg) }
    fun e(tag: String, msg: String) { Log.e(tag, sanitize(msg)) }  // errors even in prod, sanitized
}
```
```proguard
-assumenosideeffects class android.util.Log { public static *** d(...); public static *** v(...); public static *** i(...); }
-assumenosideeffects class timber.log.Timber { public static *** d(...); public static *** v(...); }
```
`Log.d("token", userToken)` is visible in logcat — never do it.

**Backup / data-extraction:** `android:allowBackup="false"` + exclude sensitive files, or data moves to Google/device-transfer unencrypted.

```xml
<application android:allowBackup="false" android:dataExtractionRules="@xml/backup_rules" ...>
```
```xml
<!-- res/xml/data_extraction_rules.xml (API 31+): cloud + device-transfer -->
<data-extraction-rules>
  <cloud-backup>     <exclude domain="sharedpref" path="secure_prefs.xml"/> <exclude domain="database" path="app_database"/> <exclude domain="file" path="."/> </cloud-backup>
  <device-transfer>  <exclude domain="sharedpref" path="secure_prefs.xml"/> <exclude domain="database" path="app_database"/> </device-transfer>
</data-extraction-rules>
<!-- pre-31: <full-backup-content> with same <exclude> entries -->
```

**WebView hardening:** JS off by default (prevents XSS) — keep disabled unless required. `allowFileAccess=false`, `allowContentAccess=false`, `domStorageEnabled=false` (unless needed), `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`, `cacheMode = LOAD_NO_CACHE` for sensitive content, `setSupportMultipleWindows(false)`, `javaScriptCanOpenWindowsAutomatically=false`, geolocation off. Restrict navigation via allowlist in `shouldOverrideUrlLoading` (return true = block). `clearCache(true)` + server `Cache-Control: no-store,no-cache` for sensitive data.

`addJavascriptInterface()` exposes native methods to untrusted web content (XSS/exploit) — avoid. On API 23+ prefer **WebMessageChannel** (`createWebMessageChannel`, `setWebMessageCallback`, `postWebMessage` to a trusted origin); channels are NOT trusted by default — validate sender origin, treat messages as untrusted. If JS is unavoidable for controlled app↔web calls, use `evaluateJavascript()`. Android <4.4: confirm WebView shows only trusted content.

**FLAG_SECURE** (screenshots, screen recording, casting to non-secure displays, recents thumbnail):

```kotlin
window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE) // Activity.onCreate
```
```kotlin
// Per-screen in Compose
@Composable fun SecureScreen(content: @Composable () -> Unit) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    content()
}
```

**Clipboard:** sensitive `OutlinedTextField` → `visualTransformation = PasswordVisualTransformation()`, `keyboardType = Password`, `autoCorrectEnabled = false`. Android 13+ auto-clears sensitive clipboard after timeout; flag content on API 33+ via `ClipDescription.EXTRA_IS_SENSITIVE = true` in `clipData.description.extras`.

**Do not leak permission-protected data:** if your app holds a permission (e.g. READ_CONTACTS), a provider/service exposing that data must `checkCallingPermission` before returning rows, else it leaks to callers lacking it.

**Minimize collection / device IDs:** use hashes/non-reversible forms; use app UUID (`UUID.randomUUID()`) instead of `ANDROID_ID`/`telephonyManager.deviceId` (READ_PHONE_STATE). Table:

| Identifier | Guidance |
|---|---|
| IMEI, IMSI, serial, MAC | Do NOT use for ads/analytics; restricted |
| Advertising ID | Ads/measurement only where allowed; resettable; declare in Data Safety |
| Android ID | App-scoped, changes on factory reset; not a cross-app user ID |
| App-specific ID | Random UUID in app storage, or bind to account after sign-in |

Complete Play Console **Data safety** form to match actual SDK/app behavior + privacy policy; allow account/data deletion; declare Advertising ID and sensitive permissions accurately.

### Device Integrity & Play Integrity

Replaces deprecated SafetyNet Attestation. Verify **device + app integrity + licensing** as **server-verifiable signals, not a client boolean**. Client-only root/`su`/package heuristics are trivially evaded/tampered → use only as telemetry/risk inputs, never the sole gate for API authorization or high-value actions.

Backend must be authoritative; bind each token to the specific action (hash a canonical representation; never put secrets in plaintext in the hash field); roll out enforcement gradually (log verdicts + error rates first, then tighten); combine with Keystore-backed keys where appropriate.

**Standard vs Classic:**

| | Standard API | Classic API |
|---|---|---|
| Warm-up | Yes (`prepareIntegrityToken`, generous timeout ~1 min) | No |
| Latency after warm-up | Low (hundreds of ms) | High (seconds) |
| Use for | Frequent checks tied to actions/API calls | Rare, high-value/sensitive actions |
| Binding field | `requestHash` (digest of protected request) | `nonce` (server-chosen, Base64 URL-safe) |
| Replay mitigation | Play mitigates; still bind `requestHash` | You own nonce + server checks |
| Rate limits | Prepare: 5/min per app instance | 5 token requests/min per app instance |

Setup: engineer enables Play Integrity API in Google Cloud, links Cloud project in Play Console (Test and release → App integrity), provides **numeric Cloud project number**; backend service account has `playintegrity` scope for decode. Dependency `com.google.android.play:integrity`. Default quota ~10k tokens + 10k decryptions/day.

```kotlin
private val integrityManager = IntegrityManagerFactory.createStandard(context)
@Volatile private var tokenProvider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null
suspend fun warmUp() { tokenProvider = integrityManager.prepareIntegrityToken(
    StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
        .setCloudProjectNumber(CLOUD_PROJECT_NUMBER).build()).await() }
suspend fun requestIntegrityToken(requestHash: String): String {
    warmUp()
    return try {
        tokenProvider!!.request(StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
            .setRequestHash(requestHash).build()).await().token()
    } catch (e: Exception) { tokenProvider = null; throw e }  // INTEGRITY_TOKEN_PROVIDER_INVALID → re-warmUp + retry
}
// Optional: verdictOptOut to skip latency-adding optional verdicts.
```
Classic: `IntegrityManagerFactory.create(context)` + `IntegrityTokenRequest.builder().setNonce(nonce)` (Play-installed apps usually don't need `setCloudProjectNumber`; non-Play installs may).

**Server decode order** (`decodeIntegrityToken` with service account): 1) `requestDetails` — `requestPackageName == appId`, `requestHash` equals recomputed (Standard) / `nonce` equals issued (Classic), `timestampMillis` within window; 2) `appIntegrity.appRecognitionVerdict` (`PLAY_RECOGNIZED` vs `UNRECOGNIZED_VERSION`); 3) `deviceIntegrity.deviceRecognitionVerdict`; 4) `accountDetails.appLicensingVerdict` (`LICENSED`/`UNLICENSED`); 5) `environmentDetails` (only if optional verdicts enabled in Console). Issue ONE token per protected request — repeated decryption of the same token clears/weakens verdicts.

| Verdict | Meaning |
|---|---|
| `MEETS_DEVICE_INTEGRITY` | Real device with Play |
| `MEETS_BASIC_INTEGRITY` | Maybe rooted, passes basic |
| `MEETS_STRONG_INTEGRITY` | Genuine, recent patch, verified boot |
| `MEETS_VIRTUAL_INTEGRITY` | Google-recognized emulator |

Errors: retry-with-backoff (`NETWORK_ERROR`, `TOO_MANY_REQUESTS`, `GOOGLE_SERVER_UNAVAILABLE`, `CLIENT_TRANSIENT_ERROR`, `INTERNAL_ERROR`); fix env/config (`API_NOT_AVAILABLE`, `PLAY_STORE_NOT_FOUND`, `*_VERSION_OUTDATED`, `CLOUD_PROJECT_NUMBER_IS_INVALID`, `CANNOT_BIND_TO_SERVICE`); `INTEGRITY_TOKEN_PROVIDER_INVALID` (Standard: clear provider, re-warmUp, retry); `REQUEST_HASH_TOO_LONG` (shorten/fixed-length hash). Persistent failure after retries = treat as failed integrity. Remediation dialogs need lib 1.3.0+ (`showDialog`) / 1.5.0+ (`GET_INTEGRITY`/`GET_STRONG_INTEGRITY`); server decides, app builds `StandardIntegrityDialogRequest`, then prepare a fresh provider/token afterward.

**Root & emulator detection (supplementary only):** root = su binaries + Magisk paths, `which su`, props `ro.debuggable=1`/`ro.secure=0`, cloak packages (rootcloak, xposed, magisk…), `Build.TAGS.contains("test-keys")`. Emulator = `Build.FINGERPRINT`/`MODEL`/`MANUFACTURER`/`HARDWARE`(goldfish/ranchu/vbox86)/`PRODUCT`/`BOARD` heuristics + `/proc/cpuinfo` hypervisor/QEMU. Never crash on detection — tier by risk:

| Risk | Rooted | Emulator |
|---|---|---|
| Low (news) | log warning | allow |
| Medium (e-commerce) | warn + log | block in prod |
| High (banking) | block + explain | block |

### Authentication & Passkeys

**Never store passwords/raw credentials/unencrypted tokens on device.** Store: short-lived service-specific auth tokens; encrypted refresh tokens (key in Keystore); expiring session IDs. Don't pass passwords/tokens via Intents/Bundles (Binder transaction-log leak). Only request auth when needed; cache securely; re-auth after timeout.

**Credential Manager** (`androidx.credentials`) = unified API for passkeys + saved passwords + federated (Sign in with Google). Client collects; **server-side validation stays authoritative**. Deps: `androidx.credentials:credentials` + `androidx.credentials:credentials-play-services-auth`. `val credentialManager = CredentialManager.create(context)`.

Passkey creation: get `PublicKeyCredentialCreationOptionsJSON` (`challenge`, `rp{id,name}`, `user{id,name,displayName}` — `user.id` random 16-byte, NO PII; `authenticatorSelection.requireResidentKey=true`/`residentKey=required`/`userVerification`; `excludeCredentials` to avoid dupes) → `CreatePublicKeyCredentialRequest(requestJson, preferImmediatelyAvailableCredentials, isConditional)` → `createCredential()` → returns `PublicKeyCredential`; serialize + send to server. `isConditional=true` auto-creates after password login (no UI). Server verifies origin (`android:apk-key-hash:<base64url(SHA-256 cert fingerprint)>` against allowlist), saves public key + AAGUID.

Sign-in: server sends `GetCredentialRequest` options + fresh `challenge`; build `GetPublicKeyCredentialOption(requestJson)` (+ `GetPasswordOption()`), wrap in `GetCredentialRequest(listOf(...), preferImmediatelyAvailableCredentials)`, call `getCredential(activity, request)` (use `MutableContextWrapper(activityContext)` to avoid leaks; handle `GetCredentialException`/`NoCredentialException`). Android 14+ `prepareGetCredential()` caches the selector to cut latency. Handle returned `PublicKeyCredential` (signed assertion → server verifies), `PasswordCredential` (`id`/`password`), `CustomCredential` (federated). Handle subclasses: `CreateCredentialCancellationException`, `CreateCredentialInterruptedException` (retryable), `CreateCredentialProviderConfigurationException` (missing play-services-auth module), `CreateCredentialNoCreateOptionException`.

**Restore Credentials** (`androidx.credentials` 1.5.0+, API 28+, GMS 24220000+) = silent re-sign-in on a new device; independent of auth method. Two-tier: Tier 1 background `BackupAgent.onRestoreFinished()` (run retrieval synchronously, e.g. `runBlocking`; use `onRestoreFinished` not `onRestore`); Tier 2 foreground `Launcher Activity.onCreate()`. Implement both only if `allowBackup=true` (else tier 2 only); if you ADD a `BackupAgent` set `android:fullBackupOnly="true"`. Do NOT change `allowBackup`. Create: `CreateRestoreCredentialRequest(requestJson, isCloudBackupEnabled=true)` → `createCredential()`; fallback: if `E2eeUnavailableException`, retry with `isCloudBackupEnabled=false`. Retrieve: `GetRestoreCredentialOption`→`GetCredentialRequest`→`getCredential()`; credential is `RestoreCredential` (not `PublicKeyCredential`); don't chain `GetRestoreCredentialOption` with other options. On sign-out MUST call `clearCredentialState(ClearCredentialStateRequest(TYPE_CLEAR_RESTORE_CREDENTIAL))` — a typeless request clears only NON-restore creds; Credential Manager never auto-deletes restore keys. Restore key tied to package name (one per app). Backend: distinguish system-managed restore creds from user passkeys (don't show in passkey UI, bypass user-verify for bg sign-in); prevent orphaned keys; TTL survives source-device logout; allow N keys per user (multi-device). Notifications aren't auto-restored — resend FCM token to backend.

**Verified Email** (Digital Credentials via Credential Manager, minSdk 28, GMS 25.49.x+) = OTP-less email verify (sign-up, recovery, re-auth). Build OpenID4VP request JSON (`digital.requests[].data.dcql_query` with `UserInfoCredential`, claims incl. `email_verified`) with a fresh cryptographically-random `nonce` (never reuse — anti-replay); wrap in `GetDigitalCredentialOption(requestJson)` → `GetCredentialRequest` → `getCredential()`; result is `DigitalCredential`, `credential.credentialJson` holds the SD-JWT `vp_token`. Parse client-side for UI ONLY; full crypto verification MUST be server-side: verify `iss == https://verifiablecredentials-pa.googleapis.com`, SD-JWT signature via JWKS at `.../.well-known/vc-public-jwks`, `cnf`/key-binding for presenter identity, and the `nonce`. `email_verified=true` + empty `hd` ⇒ personal Google Account (Workspace accounts get no verifiable creds); non-@gmail has no freshness claim ⇒ add an OTP. From Aug 2026 response JSON nests `vp_token` under `data` (W3C format) — handle both formats during transition. After account creation, offer passkey creation. WebView flow needs a JS bridge to hand off to native Credential Manager.

**Other auth:** Account Manager for shared multi-app account (same authenticator) — short-lived tokens, no device passwords. Federated sign-in (Google/Facebook/Apple/OAuth2): validate provider ID token — verify signature, check expiry, validate audience (client ID). Autofill framework integrates password managers; add `android:isCredential="true"` (API 34+) to username/password fields to suppress autofill dialogs over Credential Manager UI. Passkeys = phishing-resistant primary; provide fallback options.

### Biometrics

Use for sensitive operations: financial info, health data, payments, security-setting changes, data deletion. Biometrics alone are an added layer — don't rely solely for critical decisions; combine factors; re-auth after timeout.

**Availability check first** (never show broken UI on unsupported devices). `BiometricManager.from(context).canAuthenticate(authenticators)`:

| Result | Meaning |
|---|---|
| `BIOMETRIC_SUCCESS` | Available |
| `BIOMETRIC_ERROR_NO_HARDWARE` / `BIOMETRIC_ERROR_HW_UNAVAILABLE` | No/temp-unavailable hardware |
| `BIOMETRIC_ERROR_NONE_ENROLLED` | Nothing enrolled — route to enrollment |
| `BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED` | Needs security update |
| else | Unsupported |

```kotlin
fun checkAvailability(context: Context): Boolean =
    BiometricManager.from(context)
        .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
```

**Prompt setup — create `BiometricPrompt` in the Activity/`FragmentActivity`, NOT in a Composable**; pass a lambda down to Compose. `BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)`. `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`. Do NOT call `setNegativeButtonText` when `DEVICE_CREDENTIAL` is allowed (crash). Handle: `onAuthenticationSucceeded(result)` (use `result.cryptoObject` if CryptoObject); `onAuthenticationError` (ignore `ERROR_USER_CANCELED`/`ERROR_NEGATIVE_BUTTON` for error UX); `onAuthenticationFailed()` = non-match, **do NOT lock out** (BiometricPrompt auto-handles lockout). Use `BIOMETRIC_STRONG` (Class 3) for sensitive ops — never `BIOMETRIC_WEAK`. Provide `DEVICE_CREDENTIAL` (PIN/pattern/password) fallback.

```kotlin
promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("Authenticate").setSubtitle("Use biometric to unlock")
    .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
    .build()   // NO setNegativeButtonText here
biometricPrompt.authenticate(promptInfo)
```

**CryptoObject (strongest):** bind a Keystore key to biometric auth so the key only unlocks on a match. Generate biometric-protected key + init Cipher + authenticate with it:

```kotlin
keyGen.init(KeyGenParameterSpec.Builder(KEY_NAME, PURPOSE_ENCRYPT or PURPOSE_DECRYPT)
    .setBlockModes(BLOCK_MODE_CBC).setEncryptionPaddings(ENCRYPTION_PADDING_PKCS7)
    .setUserAuthenticationRequired(true)
    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG) // 0 = every use
    .setInvalidatedByBiometricEnrollment(true)
    .build())
// then: biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
```
(AES/GCM equivalent with `BLOCK_MODE_GCM`/`ENCRYPTION_PADDING_NONE` + `GCMParameterSpec` on decrypt.) StrongBox + biometric + invalidate-on-re-enrollment = bank-level.

### Runtime Permissions

Only request what's needed; **just-in-time** (on the action, not app launch); explain why; handle denial gracefully (offer alternative or disable feature). Request in **Screen composables, not ViewModels**.

**Prefer intents/pickers over permissions** (removes the request entirely): `ACTION_INSERT` Contacts/Calendar, `ACTION_DIAL` (no CALL_PHONE), `MediaStore.ACTION_IMAGE_CAPTURE` (no CAMERA for simple capture), `ACTION_OPEN_DOCUMENT` (Storage Access Framework), **Photo Picker** (`ActivityResultContracts.PickVisualMedia()` / `PickMultipleVisualMedia(max)`, API 33+; fallback `GetContent`/`OpenMultipleDocuments` on <33), `MediaStore` insert (no WRITE_EXTERNAL_STORAGE).

Activity Result API — single: `registerForActivityResult(RequestPermission()){ if (it) use() else rationale() }`; Compose: `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()/RequestMultiplePermissions())`. Check `ContextCompat.checkSelfPermission(...) == PERMISSION_GRANTED` before launching. (Accompanist Permissions is deprecated — use native APIs; if still used: `rememberPermissionState`, `rememberMultiplePermissionsState`, `.status.isGranted`, `.shouldShowRationale`.)

Always show rationale before requesting (why + benefit); if denied repeatedly guide to settings. `shouldShowRequestPermissionRationale` is unreliable for "don't ask again" — **track denial count in ViewModel/SavedStateHandle** (≥2 → show "Open Settings"):

```kotlin
fun Context.openAppSettings() = startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    .apply { data = Uri.fromParts("package", packageName, null) })
```

Special permissions (can't dialog — redirect to settings): overlay `Settings.canDrawOverlays`/`ACTION_MANAGE_OVERLAY_PERMISSION`; write-settings `Settings.System.canWrite`/`ACTION_MANAGE_WRITE_SETTINGS`; exact alarms `canScheduleExactAlarms()`/`ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (API 31+); all-files `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` (API 30+).

Version-specific declarations/behavior:
- **Notifications** `POST_NOTIFICATIONS` (runtime, API 33+); request contextually after an action that benefits.
- **Media** API 33+: `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`; API 34+ partial `READ_MEDIA_VISUAL_USER_SELECTED`; legacy `READ_EXTERNAL_STORAGE` with `android:maxSdkVersion="32"`. Access levels: Full / Partial (user-selected) / None.
- **Background location**: request FOREGROUND first, then `ACCESS_BACKGROUND_LOCATION` separately — Android rejects background-before-foreground.
- **Storage**: don't request `WRITE_EXTERNAL_STORAGE` on Android 10+ (use Scoped Storage / MediaStore).
- **Bluetooth** (API 31+): `BLUETOOTH_SCAN` (with `neverForLocation` flag if not deriving location), `BLUETOOTH_CONNECT`.
- **Health (API 36)**: migrate `BODY_SENSORS`/`BODY_SENSORS_BACKGROUND` → `android.permission.health.READ_HEART_RATE`/`READ_OXYGEN_SATURATION`/`READ_SKIN_TEMPERATURE`/`READ_HEALTH_DATA_IN_BACKGROUND`; must declare a privacy-policy activity (else revoked).
- **Local network (API 36 opt-in)**: LAN/mDNS/SSDP/raw-socket access needs `NEARBY_WIFI_DEVICES`; DNS-to-local :53 and normal internet exempt; enforcement in future release.
- **App-owned photo pre-selection (API 36)**: picker pre-selects app-owned photos; users may deselect (revoke).

**Permission groups (Android 8.0+): DO NOT rely on group behavior** — permissions granted/revoked individually and grouping changes across versions. Always check the EXACT permission needed (`checkSelfPermission(WRITE_CONTACTS)`), never infer from a related grant. Don't declare `dangerous` custom perms; use `signature` for same-developer, `normal` for low-risk, intents otherwise. Testing: `GrantPermissionRule.grant(...)`, test denial shows rationale.

### Anti-Patterns

Common mistakes (verbatim from sources):

- ❌ Storing tokens in plain SharedPreferences — use EncryptedSharedPreferences
- ❌ API keys in BuildConfig — visible by decompiling APK
- ❌ `android:allowBackup="true"` without backup rules — sensitive DB backed up to Google
- ❌ `android:usesCleartextTraffic="true"` in production — all traffic unencrypted
- ❌ Logging tokens or PII in debug — `Log.d("token", userToken)` visible in logcat
- ❌ No root detection for banking/payment apps — use Play Integrity API
- ❌ Creating BiometricPrompt in Composable — create in Activity, pass lambda to Compose
- ❌ Setting `setNegativeButtonText` when `DEVICE_CREDENTIAL` is allowed — crash
- ❌ Not checking availability before showing prompt — shows broken UI on unsupported devices
- ❌ Locking user out on `onAuthenticationFailed` — BiometricPrompt handles lockout
- ❌ Using `BIOMETRIC_WEAK` for sensitive operations — use `BIOMETRIC_STRONG` with CryptoObject
- ❌ Requesting permission without rationale — users deny without explanation
- ❌ Requesting multiple unrelated permissions at once — request only when needed
- ❌ Not handling permanently denied state — always offer "Open Settings" option
- ❌ Requesting background location before foreground — Android rejects this
- ❌ Missing permission in AndroidManifest — request will always be denied
- ❌ Requesting `WRITE_EXTERNAL_STORAGE` on Android 10+ — not needed, use scoped storage

Intent/component NEVERs:
- **NEVER** launch a nested Intent from an untrusted source without verifying target package + exported status.
- **NEVER** use sticky broadcasts (`sendStickyBroadcast`).
- **NEVER** assume an exported component is safe because it runs on a background thread / does internal checks.
- **NEVER** expose sensitive functionality (SSO, payments) without signature-level permission.
- **NEVER** process incoming intents in `onNewIntent` without the same controls as `onCreate`.
- **NEVER** create a mutable PendingIntent without an explicit target component in the base Intent.
- **NEVER** use dynamic string concatenation for ContentProvider selection.
- **NEVER** use `Binder.getCallingUid()` in `BroadcastReceiver.onReceive` to identify the sender (returns receiver's own UID).
- **NEVER** use implicit intents for Services.

Other: implicit PendingIntent can be intercepted by malicious apps; mutable PendingIntents modified by receiver; `file://` URIs insecure (`FileUriExposedException`); signature checks in `onBind()` get bypassed (binder cached) — verify per method; do NOT treat client "root detected" as equivalent to "Play Integrity failed"; do NOT cache an integrity verdict to authorize unrelated later actions; do NOT verify integrity on-device — backend only; do NOT trust client-parsed Digital Credential/passkey claims for account creation.

AI/LLM-specific (OWASP LLM01 Prompt Injection, LLM02 Sensitive Disclosure, LLM06 Excessive Agency): treat LLM output as untrusted; wrap external/user input in unique escaped delimiters, tell model to treat tag content as data not instructions; scrub PII (email/phone/card/SSN regex) before sending; redact/block sensitive patterns in output before render; prefer native plain-text render, else HTML-escape + JS off in WebView; use structured/typed/allowlisted actions + per-user authz + user approval for critical actions; give LLM minimal single-purpose tools (no `Runtime.exec`, no raw SQL, no whole-contact-list exposure — constrain read scope); keep a human in charge; use differential privacy / federated / on-device ML for sensitive data.

### Checklist

Secrets
- [ ] No API keys/tokens/passwords in source, `strings.xml`, or committed files
- [ ] BuildConfig/local.properties treated as dev-only; real secret behind a server proxy
- [ ] `.gitignore` covers local.properties, *.jks, *.keystore, google-services.json, .env
- [ ] CI greps for hardcoded secrets + runs dependency-vuln + Detekt

Storage & crypto
- [ ] Auth tokens in `EncryptedSharedPreferences` (`MasterKey.AES256_GCM`, `AES256_SIV`/`AES256_GCM`)
- [ ] Encrypted files via `EncryptedFile` / `AES256_GCM_HKDF_4KB`
- [ ] Sensitive Room fields encrypted or full SQLCipher (correct Room 3 `SQLiteDriver`)
- [ ] Keystore keys with proper `KeyGenParameterSpec`; `setIsStrongBoxBacked`/`setUserAuthenticationRequired` where warranted
- [ ] Only standard algorithms (AES-GCM, SHA-2, HMAC-SHA2, ECDSA); no custom crypto; provider set only for `AndroidKeyStore`

Network
- [ ] HTTPS enforced all endpoints; `cleartextTrafficPermitted="false"`, `usesCleartextTraffic="false"`
- [ ] Network Security Config referenced; `<certificates src="user"/>` only in `debug-overrides`
- [ ] TLS 1.2+ (`ConnectionSpec.MODERN_TLS`)
- [ ] Certificate pinning for critical endpoints, with backup pin + `expiration`; monitor pin failures; test staging

Components & Intents
- [ ] `android:exported` explicit everywhere; `false` unless external access needed
- [ ] Exported components guarded by `signature` permission; incoming data validated
- [ ] No implicit Service intents; nested intents sanitized (`IntentSanitizer`) / manually verified
- [ ] `PendingIntent.FLAG_IMMUTABLE` default; any `FLAG_MUTABLE` has explicit component; no implicit mutable
- [ ] Same validation in `onNewIntent` (+ `setIntent`); no sticky broadcasts; receivers `RECEIVER_NOT_EXPORTED`/signature
- [ ] ContentProviders `exported=false` or read/write perms; parameterized queries + projection map; FileProvider not `file://`
- [ ] Deep links `autoVerify` + validated; sensitive activities `taskAffinity=""`, `excludeFromRecents`; `FLAG_SECURE` on sensitive screens

Data leakage
- [ ] No PII/tokens in logs; R8 strips `Log`/Timber in release; logs via sanitizer
- [ ] `allowBackup="false"` or `dataExtractionRules` excludes prefs/db/files
- [ ] WebView JS off unless required; file/content access off; no-mixed-content; allowlist nav; `addJavascriptInterface` avoided

Device integrity
- [ ] Play Integrity: Cloud project linked in Console, project number in config, `warmUp()` before first sensitive use
- [ ] Standard `requestHash` / Classic `nonce` bound per request; one token per request
- [ ] Server calls `decodeIntegrityToken`, validates `requestDetails`→`appIntegrity`→`deviceIntegrity`→`accountDetails`→`environmentDetails`
- [ ] Tiered enforcement, gradual rollout (telemetry first), remediation path; local root/emulator only supplementary

Auth & Biometrics
- [ ] No passwords/raw creds on device; short-lived tokens; no creds via Intents/Bundles
- [ ] Credential Manager (passkeys primary, fallbacks); server authoritative; Digital Credential/email claims verified server-side (iss, SD-JWT sig, cnf, nonce)
- [ ] Restore: both tiers only if `allowBackup=true`; `clearCredentialState(TYPE_CLEAR_RESTORE_CREDENTIAL)` on sign-out
- [ ] BiometricPrompt created in Activity; `BIOMETRIC_STRONG` (+ `DEVICE_CREDENTIAL` fallback) for sensitive; `CryptoObject` bound to Keystore; availability checked; no lockout on `onAuthenticationFailed`

Permissions
- [ ] Just-in-time, minimal, contextual; rationale shown; denial handled (Open Settings via SavedStateHandle counter)
- [ ] Prefer intents/Photo Picker/MediaStore over runtime perms; check exact permission (never group behavior)
- [ ] Version-gated storage/media/notifications/location/health per API level
- [ ] `android:debuggable` not true in release; R8/ProGuard enabled; mapping uploaded

## DOMAIN: Testing & Code Quality

### Testing Strategy

**No tests = not production-ready.**

Test pyramid (two playbook variants — keep both ratios in mind):

```
        ┌─────────────────┐
        │    UI Tests      │  Slowest — Compose UI, Screenshot
        ├─────────────────┤
        │ Integration Tests│  Real DB, real network (MockWebServer)
        ├─────────────────┤
        │   Unit Tests     │  Fastest — VM, UseCase, Repository, DataSource
        └─────────────────┘
```

- Rezaiyan KMP playbook focus: **70% unit, 20% integration, 10% UI.**
- Android-lead variant: **Unit 70%** (JVM-only: ViewModels w/ fake repos, use cases w/ fake data sources, mappers, utils) · **Integration 20%** (Room DAOs vs in-memory DB, Retrofit vs MockWebServer; JVM-only where possible) · **Screenshot 8%** (Roborazzi on JVM, all theme variants) · **E2E 2%** (UIAutomator/Espresso, critical happy path only, real emulator in CI).
- End-to-end (Release Candidate) tests: about **5% of all tests**, covering big user journeys; UI Automator only when accessing platform features (notifications, system UI).

**Google `testing-setup` strategy workflow (14 steps, condensed):**
1. Analyze existing setup before installing anything: DI framework (Hilt/Koin/Anvil/Dagger), local test framework (JUnit4/5), mocking framework, Robolectric usage (fakes for platform entities / running UI tests without device / Roborazzi screenshots), Compose vs Views vs hybrid, behavior UI tests (`androidx.compose.ui:ui-test-*`, Espresso + `androidx.test:runner`/`androidx.test:rules`, wrappers like Kaspresso), screenshot flavor (instrumented e.g. Dropshots / Robolectric-based e.g. Roborazzi / LayoutLib-based e.g. Paparazzi or Compose Preview Screenshot Testing), E2E (UIAutomator, Appium, Robotium). Produce a Markdown analysis report.
2. DI: if none, install one — multiplatform → ask Koin vs kotlin-inject; otherwise Hilt. Add `com.google.dagger:hilt-compiler` via `kspAndroidTest`. Configure `testInstrumentationRunner`; annotate tests `@HiltAndroidTest` + `HiltAndroidRule` (other frameworks differ — consult their docs).
3. Respect the project's existing stack. If none, defaults: JUnit4 (local + instrumented), JaCoCo coverage, Espresso if Views / Compose Testing APIs if fully Compose, Robolectric to run UI tests, Compose Preview Screenshot Testing tool for screenshots, Dropshots for device screenshots, Mockk (`io.mockk:mockk`) **only if clearly necessary**, UIAutomator for E2E.
4. Refactor for testability: dependency on Android/framework class → **first use a fake** (create interface + "Default" impl if needed; Fake goes in test/androidTest source set). Only mock when a fake is impossible (no access to the class/interface).
5. Unit tests for every file with business logic (ViewModels, Repositories, DAOs, mappers, utils). **Don't create unit tests for Activities, Compose layouts, or DI configuration files.**
6. Compose/Espresso UI tests live in `test` sourceset when run via Robolectric; instrumented (emulator/device) tests go in `androidTest`.
7. SQLite-backed DBs (Room, SQLDelight): instrumented tests with in-memory database to verify the real SQLite engine behavior.
8. Screenshot matrix: screen-level tests at 9 sizes (widths 400/610/900 dp × heights 400/500/1000 dp); mobile 400x500 shots of all alternative themes and fontScale 1.5; component-level tests across themes/font scales. Behavior is NOT tested with screenshots; capture common state variants (loading injected via fake) if UI changes a lot per state.
9. UI behavior tests: semantic matchers first — if a matcher needs **more than 3 matchers to find one element**, use `testTag`. **Always verify state restoration.** Use ComposeTestRule with a `ComponentActivity` to access string resources. Views → Espresso.
10. Navigation test suite: back handling, deeplinks, special patterns like "exit through home" with multiple backstacks.
11. Simulate window sizes/settings with `DeviceConfigurationOverride`.
12. E2E: Compose Test APIs or Espresso; UI Automator for platform features; Dropshots when device screenshots are needed (edge-to-edge, notifications, picture-in-picture).
13. Instrumented screenshot tests: `com.dropbox.dropshots` plugin + `Dropshots()` JUnit Rule.
14. JaCoCo plugin in each module containing tests.
Final touch: document the strategy — update AGENTS.md if present, else create `docs/testing.md` (commands to run each test type, where screenshot references live) and link it from a new root AGENTS.md.

Test types by module (claude-android-ninja):

| Module | Test Type | Location | Purpose |
|---|---|---|---|
| Feature modules | Unit tests | `src/test/` | ViewModel, UI logic |
| Core/Domain | Unit tests | `src/test/` | Use Cases, business logic |
| Core/Data | Integration tests | `src/test/` | Repository, DataSource |
| Core/UI | UI tests | `src/androidTest/` | Shared components |
| App module | Navigation tests | `src/test/` | Navigator implementations |

What to test vs skip (compose-kotlin + 11-testing detail):

| Always test | Why | Tool | Usually skip | Why |
|---|---|---|---|---|
| ViewModel state transitions | Core business logic | Turbine + runTest | Room generated SQL (simple DAOs) | Annotation processor generates correct SQL |
| Flow transformations (combine/map/filter) | Correctness | Turbine | Hilt graph wiring | Compile-time verified |
| Error paths (`Result.failure`, fake throws) | Failure states must work | Fake that throws | Pixel-perfect screenshots | Brittle — use design review instead |
| Repository mappers (DTO/Entity → Domain) | Mapping correctness | Unit test | Third-party SDK internals / external API calls | Mock the repository, not the HTTP client |
| Critical user flows (instrumented) | Release confidence | composeRule / TestNavHostController | | |

Test if time allows: Compose UI rendering (composeRule), navigation flows (TestNavHostController), accessibility (semantics assertions).

Lean default test matrix (meetmiyani): 1) ViewModel event→state→effect tests for every feature (Turbine) · 2) validation/calculation pure-function tests for rule-heavy features · 3) UI tests for high-risk screens · 4) platform integration tests only for real platform behavior. **Do not sink weeks into screenshot infrastructure before you have ViewModel test coverage.**

Test file organization:

```
src/test/             # Unit tests (JVM, fast)
├── viewmodel/  ├── repository/  └── fakes/   (FakeXxxRepository.kt)
src/androidTest/      # Instrumented tests (device/emulator)
├── ui/  └── navigation/
```

Dependency setup (version catalogs — versions as listed per source):

```toml
# JUnit4 stack (piyush): junit 4.13.2 · junitExt 1.2.1 · coroutinesTest 1.9.0 · mockk 1.13.12 · turbine 1.1.0 · roborazzi 1.27.0 · hiltTesting 2.52
junit = { group = "junit", name = "junit" }                       # org.jetbrains.kotlinx:kotlinx-coroutines-test
mockk = { group = "io.mockk", name = "mockk" }                    # + mockk-android for androidTest
turbine = { group = "app.cash.turbine", name = "turbine" }        # app.cash.turbine:turbine (1.2.0 in rezaiyan/compose-kotlin playbooks)
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test" }
androidx-junit = { group = "androidx.test.ext", name = "junit" }
hilt-android-testing = { group = "com.google.dagger", name = "hilt-android-testing" }
room-testing = { group = "androidx.room", name = "room-testing" } # Room3: room3-testing; roborazzi + roborazzi-compose
ktor-client-mock = { module = "io.ktor:ktor-client-mock" }        # MockEngine; kotlin-test = org.jetbrains.kotlin:kotlin-test
```

```kotlin
// JUnit5 variant (rezaiyan): org.junit.jupiter:junit-jupiter-api / -params / -engine (5.11.3)
tasks.withType<Test> { useJUnitPlatform() }
// avoid mockk — write fakes instead
```

```kotlin
// gradle wiring (piyush)
testImplementation(libs.junit); testImplementation(libs.mockk)
testImplementation(libs.kotlinx.coroutines.test); testImplementation(libs.turbine)
testImplementation(libs.room.testing)
androidTestImplementation(libs.androidx.junit); androidTestImplementation(libs.hilt.android.testing)
androidTestImplementation(libs.mockk.android); androidTestImplementation(libs.roborazzi); androidTestImplementation(libs.roborazzi.compose)
kspAndroidTest(libs.hilt.compiler)
```

### ViewModel & Flow Testing

Turbine (`app.cash.turbine:turbine`) = library for testing Kotlin Flows; essential for ViewModel tests. Highest ROI: test the full **event → state → effect** cycle through the ViewModel.

Canonical pattern (deduplicated across all sources):

```kotlin
class WordListViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()   // JUnit5: @RegisterExtension, see Coroutines Testing
    private val fakeRepo = FakeWordRepository()
    private lateinit var vm: WordListViewModel

    @Before fun setUp() { vm = WordListViewModel(GetDueWordsUseCase(fakeRepo), DeleteWordUseCase(fakeRepo)) }

    @Test fun `initial state is loading`() = runTest {
        vm.state.test {
            assertTrue(awaitItem().isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `words loaded successfully updates state`() = runTest {
        vm.state.test {
            skipItems(1)                       // skip initial loading state
            fakeRepo.emitWords(listOf(testWord(id = 1), testWord(id = 2)))
            val loaded = awaitItem()
            assertFalse(loaded.isLoading); assertEquals(2, loaded.words.size); assertNull(loaded.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `delete word emits undo effect`() = runTest {
        val word = testWord()
        fakeRepo.emitWords(listOf(word))
        vm.effects.test {
            vm.deleteWord(word)
            assertIs<WordListEffect.ShowUndo>(awaitItem())
        }
    }

    @Test fun `delete word failure updates error state`() = runTest {
        fakeRepo.setDeleteError(RuntimeException("DB error"))
        vm.state.test { skipItems(1); vm.deleteWord(testWord()); assertNotNull(awaitItem().error); cancelAndIgnoreRemainingEvents() }
    }
}
```

Turbine API semantics:
- `awaitItem()` — next emission; `skipItems(n)` — skip n emissions (e.g. initial loading); `expectNoEvents()` — assert nothing arrived yet (debounce window check); `awaitComplete()`/`awaitError()` for terminal events.
- `cancelAndIgnoreRemainingEvents()` — cancels the flow and silently drops any unread items. Use when you only care about specific items and the rest are irrelevant.
- `cancelAndConsumeRemainingEvents()` — cancels and returns the remaining events as a list. Use when you want to assert that no unexpected events arrived, or inspect what was left.
- kotlinx-coroutines-test's own assertion API (`assertThat(stateFlow)`, `assertThatAwait` / `assertThatNotEmitted`) is a Turbine alternative for final-value checks; Turbine stays the choice for emission sequences.

Turbine vs `advanceUntilIdle()`:

| Use Turbine | Use `advanceUntilIdle()` |
|---|---|
| Multiple emissions from a Flow | Final StateFlow value after operation |
| Verifying emission order and values | Simple async operations with one result |
| Flow transformations | No need to inspect intermediate states |

Testing state and effects separately — when one event produces both, assert them independently:

```kotlin
@Test
fun `back click emits NavigateBack effect without changing state`() = runTest {
    val viewModel = CreateItemViewModel(FakeItemRepository())
    viewModel.effect.test {
        viewModel.onEvent(CreateItemEvent.OnBackClick)
        assertEquals(CreateItemEffect.NavigateBack, awaitItem())
    }
    viewModel.state.test { assertEquals(CreateItemState(), awaitItem()) }
}
```

What to cover (meetmiyani): Event→state transitions (field edits, validation triggers, loading states) · Event→effect emissions (navigation, snackbar, error messages) · Async flows: loading→success, loading→failure, retry · Edge cases: empty input, duplicate detection, concurrent saves · State preservation: old content kept during refresh, error doesn't wipe data.

Validation & domain logic tests: test validation as pure functions when extracted (`CreateItemValidator.validate(title = "", amount = "10")` → assert errors map); if validation is inline in the ViewModel (acceptable for simple cases), test through ViewModel events. Test pure calculation/domain services directly, no ViewModel: `LoanCalculator.monthlyPayment(100000.0, 5.0, 30)` with delta assertion. Cover edge cases, rounding policy, domain invariants, regression fixtures.

Each test constructs its own state — avoid `@BeforeEach` seeds that some tests must undo. Reset fakes between tests (`fake.reset()`) when sharing instances.

Assertions with Google Truth (claude-android-ninja — fluent, readable, rich failure messages):

```kotlin
assertThat(actual).isEqualTo(expected)   // not assertEquals(expected, actual)
assertThat(condition).isTrue()           // not assertTrue(condition)
assertThat(value).isNotNull()            // not assertNotNull(value)
assertThat(userList).hasSize(3); assertThat(userList).contains(user1); assertThat(list).doesNotContain(x)
assertThat(nullable).isNull()
assertThat(state).isInstanceOf(AuthUiState.Error::class.java)
// Custom subjects: reusable assertUserEquals(expected, actual) helpers in core:testing TestData
```

Naming + structure discipline:
- Test name describes behavior: `` `given X when Y then Z` `` (Given/When/Then; backtick names like `` `deleting word removes it from list`() ``).
- One assertion concept per test; multiple `assert*` calls fine when they describe one concept.
- `@Nested` classes to group scenarios (`given empty repository` / `given words loaded` / `given repository error`) — keeps output readable.
- Parameterized tests for data-driven scenarios (JUnit5):

```kotlin
@ParameterizedTest
@CsvSource("0, 5, 1", "1, 5, 3", "2, 5, 5", "5, 1, 1")
fun `calculateNextReview returns correct interval`(bucket: Int, quality: Int, expectedDays: Long) { ... }
```

SavedStateHandle testing:

```kotlin
savedStateHandle = SavedStateHandle(mapOf("userId" to "user-123"))   // nav argument
// missing argument → assertThrows<IllegalStateException> { ProfileViewModel(repo, SavedStateHandle()) }
// process death: snapshot savedStateHandle.keys() into a map → new SavedStateHandle(map) → rebuilt VM keeps values
```

### Coroutines Testing

- **Always `runTest`** (virtual time + automatic completion waiting). **No `runBlocking` in tests** (exception: Room3 `MigrationTestHelper` suspend APIs from instrumentation tests).
- Why a dispatcher rule is needed: ViewModels use `viewModelScope` which dispatches on `Dispatchers.Main`; tests run on JVM — no Main dispatcher. No rule → tests hang or fail on `Dispatchers.Main`.

`MainDispatcherRule` — three sanctioned forms:

```kotlin
// 1) JUnit4 (JVM/androidTest, Robolectric): TestWatcher + @get:Rule
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}

// 2) JUnit5 — TestWatcher and @get:Rule are JUnit4 APIs; do NOT use them with JUnit5.
//    Use BeforeEachCallback/AfterEachCallback + @RegisterExtension instead:
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher())
    : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) { Dispatchers.setMain(dispatcher) }
    override fun afterEach(context: ExtensionContext) { Dispatchers.resetMain() }
}

// 3) KMP commonTest (no JUnit4 dependency):
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseTest {
    @BeforeTest fun setUpDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest  fun tearDownDispatcher() = Dispatchers.resetMain()
}
```

Dispatcher choice (also available via `TestScope`/`testScheduler`):

| | `UnconfinedTestDispatcher` | `StandardTestDispatcher` |
|---|---|---|
| Coroutine execution | Eager (runs immediately) | Lazy (requires `advanceUntilIdle()`) |
| Best for | StateFlow / Turbine tests | Precise timing control, execution ordering |
| Default choice | Yes | Only when testing delays or ordering |

Share the same scheduler across test dispatchers for predictable timing: `UnconfinedTestDispatcher(testScheduler)`. (ninja's `TestDispatcherRule` defaults to `StandardTestDispatcher()`; android-lead's older `TestCoroutineDispatcher` variant calls `dispatcher.cleanupTestCoroutines()` in `finished`.)

Virtual time:

```kotlin
// Debounce / time-based logic
@Test fun `search is debounced by 300ms`() = runTest {
    vm.state.test {
        skipItems(1)
        vm.onSearchQueryChanged("h"); vm.onSearchQueryChanged("he"); vm.onSearchQueryChanged("hel")
        expectNoEvents()                 // debounce window not elapsed
        advanceTimeBy(300)
        assertEquals("hel", awaitItem().query)
        cancelAndIgnoreRemainingEvents()
    }
}

advanceUntilIdle()            // run all pending coroutines before asserting final state
advanceTimeBy(30.minutes)     // fast-forward delays (periodic refresh, backoff); assert counts on fake
currentTime                   // verify time progression: assertThat(currentTime - startTime).isEqualTo(1000L)
```

Timeouts: build a fake with `responseDelay = 40.seconds`, call the `withTimeout(30s)` code path, `advanceTimeBy(35.seconds)`, assert `null`/`Timeout` result. `async { repo.print(...) }` + advance past the 60s timeout + `await()` → assert `PrintResult.Timeout`.

Cancellation & cleanup:

```kotlin
val job = launch { uploader.upload(files) }
advanceTimeBy(100L); job.cancel(); advanceUntilIdle()
assertThat(fakeUploader.uploadedFiles.size).isLessThan(5)          // stops on cancellation
// cleanup: fakeCamera closed despite cancellation (NonCancellable cleanup) → assertThat(fakeCamera.isClosed).isTrue()
```

Key coroutine testing principles:
1. Always use `runTest`. 2. Share test scheduler. 3. **Inject dispatchers — never hardcode in production code**: `class WordRepositoryImpl(..., private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)` and pass `UnconfinedTestDispatcher()` in tests. 4. `advanceUntilIdle()` before assertions. 5. `advanceTimeBy()` for delay/timeout testing without waiting. 6. Test cancellation. 7. Test cleanup (resources released even on cancellation).

`Try<T>` contract (rezaiyan): `sealed class Try<out T> { Success(value) / Failure(error) }` + `inline fun <T> tryOf(block: () -> T): Try<T>`; suspend repository methods return `Try<T>` and never throw — failure-path tests set `fakeRepo.setDeleteError(...)` instead of expecting exceptions.

### Fakes vs Mocks

**Fakes over mocks.** Fakes implement the interface with in-memory data. Mocks (Mockito/MockK) verify method calls but don't test behavior. Fakes catch bugs mocks miss. Fakes produce real behavior; they exercise the same contract as production code and surface interface design issues. Rezaiyan rule: `fakes` over `mocks` in tests — write `FakeXxxRepository`, not `mockk<IXxxRepository>()`; Mockito/MockK are banned there. Google rule: try fake first; mock only when a fake is impossible.

Mock policy (claude-android-ninja exception model): feature modules — no mocking libraries, fakes implementing interfaces; core modules — no mocking libraries, fakes + in-memory DB; **app module — MockK allowed for Navigation3 testing only** (framework classes: `NavigationState`, `Navigator`). Repository integration tests may use `mockk()` for the API service when DB is the subject under test (piyush: `coEvery { mockApi.getItems() } returns/throws ...`).

Fake conventions:
- **`Fake` prefix** = working implementation with test hooks (`FakeAuthRepository`) — contains business logic and state management, not just stubs; lives in shared `core:testing` module / `test/fakes/`.
- Expose control helpers: `emitX(...)`, `setXError(Throwable)`, `setShouldFail(Boolean)`, `var syncDelay`, `reset()` clearing all state.
- Fake DataSources mirror Fake Repositories (emit + error hooks + exposed recorded state e.g. `savedEntities`).
- Recording fakes for effects/navigation: `FakeAuthNavigator` appends strings to `navigationEvents` (`navigateToProfile:$userId`), helpers `clearEvents()`, `getLastEvent()`.
- Use **real use cases wired to fake repositories** so production logic is exercised.

```kotlin
class FakeWordRepository : IWordRepository {
    private val _words = MutableStateFlow<List<Word>>(emptyList())
    private var deleteError: Throwable? = null
    fun emitWords(words: List<Word>) { _words.value = words }
    fun setDeleteError(e: Throwable) { deleteError = e }
    fun reset() { _words.value = emptyList(); deleteError = null }
    override fun observeWords(): Flow<List<Word>> = _words.asStateFlow()
    override suspend fun delete(id: Int): Try<Unit> = tryOf { deleteError?.let { throw it }; _words.value = _words.value.filter { it.id != id } }
}
```

Test builders / factories — never duplicate test data construction; one shared set per module:

```kotlin
// Use a FIXED date — never LocalDate.now() or Clock.System.now() in builders.
// Tests that depend on the current date are fragile and can fail at midnight.
val FIXED_DATE: LocalDate = LocalDate(2025, 1, 1)
val FIXED_INSTANT: Instant = Instant.parse("2025-01-01T00:00:00Z")
fun testWord(id: Int = 1, original: String = "hello", translated: String = "hola", bucket: Int = 0, nextReviewDate: LocalDate = FIXED_DATE) = Word(...)
fun wordEntity(...) = WordEntity(...); fun wordDto(...) = WordDto(...)

object ArticleFactory {                       // unique-id factory variant
    private var idCounter = 0L
    fun create(id: Long = ++idCounter, title: String = "Test Article $id", ...) = Article(..., publishedAt = Instant.now())
    fun createList(count: Int) = List(count) { create() }
}
```

Runtime fakes via DI for UI tests: **simulate** user scenarios (wrong credentials...), server states (no connection, server down, bad JSON), platform conditions (insufficient permissions, no disk space); **improve** speed/reliability (in-memory DB, in-memory fake repository instead of network).

### DI in Tests

**Unit tests don't need Hilt**: constructor-injected classes are built directly with fakes/mocks — same for ViewModels obtained via `hiltViewModel()` in composables (construct the ViewModel manually).

Replace a binding for all tests in a source set — `@TestInstallIn` (recommended whenever possible):

```kotlin
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])
abstract class TestRepositoryModule {
    @Binds @Singleton
    abstract fun bindRepo(impl: FakeAuthRepository): AuthRepository
}
```

Replace a binding for one test class — `@UninstallModules` + nested test module:

```kotlin
@UninstallModules(AnalyticsModule::class)
@HiltAndroidTest
class SettingsScreenTest {
    @Module @InstallIn(SingletonComponent::class)
    abstract class TestModule {
        @Singleton @Binds abstract fun bindAnalyticsService(fake: FakeAnalyticsService): AnalyticsService
    }
}
```

- You cannot uninstall modules that are not annotated with `@InstallIn`. Attempting to do so causes a compilation error.
- `@UninstallModules` can only uninstall `@InstallIn` modules, not `@TestInstallIn` modules. Attempting to do so causes a compilation error.
- Hilt creates new components for tests that use `@UninstallModules` → can significantly impact unit test build times; prefer `@TestInstallIn` when replacing everywhere.

`@BindValue` — bind a field directly into the graph (replaces binding and references it in one step):

```kotlin
@BindValue @JvmField val analyticsService: AnalyticsService = FakeAnalyticsService()
@BindValue @ExampleQualifier @Mock lateinit var qualifiedVariable: ExampleCustomType   // composes with qualifiers & Mockito
// multibindings: @BindValueIntoSet / @BindValueIntoMap (map key annotation required)
```

Instrumented/Robolectric Hilt UI test setup:

```kotlin
dependencies {
    testImplementation("com.google.dagger:hilt-android-testing:2.57.1"); kspTest("com.google.dagger:hilt-android-compiler:2.57.1")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.57.1"); kspAndroidTest("com.google.dagger:hilt-android-compiler:2.57.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4"); debugImplementation("androidx.compose.ui:ui-test-manifest")
}
// Jetpack integrations (hilt-navigation-compose / hiltViewModel()) need their annotation processors in test deps too.

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)   // or AndroidJUnit4::class
@Config(application = HiltTestApplication::class)
class SettingsScreenTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()
    @Inject lateinit var analyticsAdapter: AnalyticsAdapter
    @Before fun setup() { hiltRule.inject() }
}
```

- `HiltAndroidRule` must run **first** — order with `@get:Rule(order = …)` or `RuleChain.outerRule(HiltAndroidRule(this)).around(...)`.
- Create an empty `HiltTestActivity` annotated `@AndroidEntryPoint` in `androidTest` as compose host; `ui-test-manifest` provides `ComponentActivity` for resource access.
- Instrumented: custom runner `class CustomTestRunner : AndroidJUnitRunner() { override fun newApplication(cl, name, context) = super.newApplication(cl, HiltTestApplication::class.java.name, context) }` + `testInstrumentationRunner = "…CustomTestRunner"`.
- Robolectric alternative: `robolectric.properties` → `application = dagger.hilt.android.testing.HiltTestApplication`, or per-class `@Config(application = HiltTestApplication::class)`.
- Need a base Application? `@CustomTestApplication(BaseApplication::class) interface HiltTestApplication` → generated `HiltTestApplication_<name>` (red in IDE until tests run; set as test application as above).
- `@EarlyEntryPoint`: escape hatch when an entry point is needed before the singleton component exists in a Hilt test.
- Replacing the binding is enough for Compose: the composable's `hiltViewModel()` picks up the fake automatically.
- Koin tests: override with `loadKoinModules()` (vs Hilt's `TestInstallIn`/`@UninstallModules`).
- Integration style (real DI graph + fake repo): inject `AuthRepository` with `@Inject` after `hiltRule.inject()` and drive UI with `createAndroidComposeRule<MainActivity>()`.

### Compose UI Tests

**Core principle: test the smallest UI contract that proves the behavior.** Prefer plain state-driven UI tests with callbacks. Add integration only when lifecycle, navigation, DI, or platform behavior is the thing under test.

Test target choice:

| What you need to prove | Test shape |
|---|---|
| Text, button, loading/error branch, conditional content | Plain UI Compose test |
| Callback wiring from click/input | Plain UI Compose test |
| Focus navigation or keyboard behavior | Compose test with key input |
| Visual layout, clipping, elevation, typography, image composition | Screenshot test |
| State holder updates UI correctly | State holder/unit test plus one wiring smoke test |
| Hover, pressed, focused, dragged interaction state | Plain UI test with MutableInteractionSource |
| Navigation, lifecycle, DI integration | Integration test |

**Test the stateless content composable, not the ViewModel-integrated screen** — DON'T test the screen with ViewModel (that's integration testing); DO test e.g. `AccountsContent(accounts, searchQuery, onSearchChanged, onDelete, onAdd)` — pure function of its params, easy to isolate:

```kotlin
class FeatureScreenTest {
    @get:Rule val composeRule = createComposeRule()   // composeTestRule

    @Test fun showsEmptyState() {
        composeRule.setContent { AppTheme { FeatureContent(state = FeatureUiState(items = emptyList()), onEvent = {}) } }
        composeRule.onNodeWithTag("feature_empty").assertIsDisplayed()
    }
}
```

Semantics first — assert semantics when behavior is semantic:
- Text exists: `onNodeWithText`. Button enabled/disabled: `assertIsEnabled` / `assertIsNotEnabled`. Content selected/focused/toggled: semantics assertions. Content absent: `assertDoesNotExist`. Exists: `assertExists()`.
- Other APIs: `onNodeWithTag`, `onNodeWithContentDescription`, `assertTextEquals`, `performClick()`, `performTextInput("…")`.
- Use `Modifier.testTag("search_bar")` only for nodes without stable user-visible text or with duplicated text; **not** the first choice for all assertions — user-visible semantics are usually stronger. Google rule: if the semantic matcher needs >3 matchers, switch to testTag.

Callback testing — simple captured values/counters; direct assertion after the action is usually enough:

```kotlin
var selectedId: String? = null
composeTestRule.setContent { ItemList(items = listOf(ItemUi("movie-1", "Movie")), onItemClick = { selectedId = it }) }
composeTestRule.onNodeWithText("Movie").performClick()
assertThat(selectedId).isEqualTo("movie-1")
```

Determinism:
- Render controlled state with `setContent` instead of constructing the production app graph — DI, repositories, lifecycle observers, background effects add irrelevant async work → flakiness.
- Do not use `Thread.sleep` to wait for Compose. Drive to a known state, then use semantic assertions + Compose synchronization (`waitForIdle()`, `runOnIdle`, bounded `waitUntil` for a real asynchronous condition). Use `runOnIdle` when the assertion needs Compose to finish applying snapshot state/recomposition/queued UI work before reading the result.
- Reserve full-app integration for behavior that actually depends on navigation, lifecycle, DI, platform wiring.
- Compose Multiplatform common UI testing uses `runComposeUiTest`, not Android's JUnit `TestRule` model. UI tests: critical field entry flows, submit enable/disable, error visibility, loading placeholder/content swap, preserved content during refresh, accessibility labels on critical controls.
- Platform (Android/iOS) tests: shell wiring, deep-link entry, navigation host integration, share sheet/clipboard/haptic bindings, lifecycle edge cases, keyboard/safe-area regressions.

Accessing resources (string-asserting tests): can't call `setContent` on a rule from `createAndroidComposeRule()` if the activity already calls it → host on an empty `ComponentActivity`:

```kotlin
@get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()
composeTestRule.setContent { MyAppTheme { MainScreen(uiState = exampleUiState) } }
val continueLabel = composeTestRule.activity.getString(R.string.next)
composeTestRule.onNodeWithText(continueLabel).performClick()
// requires debugImplementation("androidx.compose.ui:ui-test-manifest:$compose_version") in the manifest
```

Custom semantics properties (last resort):

```kotlin
val PickedDateKey = SemanticsPropertyKey<Long>("PickedDate")
var SemanticsPropertyReceiver.pickedDate by PickedDateKey
Modifier.semantics { pickedDate = datePickerValue }
composeTestRule.onNode(SemanticsMatcher.expectValue(PickedDateKey, 1445378400)).assertExists()
```
Warning: use custom Semantics properties only when it's hard to match with the given finders/matchers. Exposing visual properties (colors, font size, corner radius) is not recommended — pollutes production code and wrong implementations cause hard-to-find bugs.

State restoration (always verify):

```kotlin
val restorationTester = StateRestorationTester(composeTestRule)
restorationTester.setContent { MainScreen() }
// actions that modify state…
restorationTester.emulateSavedInstanceStateRestore()
// verify rememberSaveable state restored
```

Device configurations without a device — `DeviceConfigurationOverride` wraps content under test: `DarkMode()`, `FontScale()`, `FontWeightAdjustment()`, `ForcedSize(DpSize(1280.dp, 800.dp))`, `LayoutDirection()`, `Locales()`, `RoundScreen()`; compose with `then`:

```kotlin
composeTestRule.setContent {
    DeviceConfigurationOverride(
        DeviceConfigurationOverride.FontScale(1.5f) then DeviceConfigurationOverride.FontWeightAdjustment(200)
    ) { Text("text with increased scale and weight") }
}
```

Interaction state (hover/pressed/focused/dragged) — inject `MutableInteractionSource` and emit states directly; never simulate pointer/mouse events (fragile, environment-dependent, flaky):

```kotlin
val interactionSource = MutableInteractionSource()
composeTestRule.setContent { OutlinedButton(onClick = {}, interactionSource = interactionSource) }
TestScope().launch { interactionSource.emit(HoverInteraction.Enter()) }   // emit is suspend
composeTestRule.waitForIdle()
// assert the RESULT (border color/elevation/enabled state), or capture for screenshot
```
- Always inject `MutableInteractionSource` rather than relying on the default internal source — full control over transitions.
- Emit interactions from a coroutine scope (e.g. `TestScope().launch { }`) since `emit` is suspend. Do not use `LaunchedEffect` — that is a production Compose effect, not a test tool.
- Assert the result of the interaction, not the interaction itself; the source is a driver, not the assertion target.
- Works for `PressInteraction.Press/Release/Cancel`, `FocusInteraction.Focus/Unfocus`, `DragInteraction.Start/Stop/Cancel`; also for screenshot tests (emit state, then capture a deterministic hover/press/focus visual).

Keyboard/focus/TV/desktop: drive navigation with the same input model users use (keys/D-pad), not clicks alone. Assert focused semantics, not colors or scale; reserve screenshots for visual focus treatment.

Animations: Compose test APIs pause animations by default; to test animated states set `composeTestRule.mainClock.autoAdvance = false`, `mainClock.advanceTimeBy(500L)`, assert intermediate state, then `autoAdvance = true`. For screenshot tests of animated states (shimmer), capture after a fixed time offset for a deterministic frame.

Fake images / platform services: when image content is irrelevant, fake the loader and assert the requested model (`requestedModels += request.data; errorPainter()` in a project helper — hook depends on the image library). When appearance matters, provide a deterministic local painter/bitmap instead of network data.

Navigation tests: TestNavHostController for destinations; fake navigators (event-recording) for interfaces; Navigation3: unit-test `NavigationState`/`Navigator` (app module may MockK framework classes); deep-link parsing unit tests + `adb shell am start -W -a android.intent.action.VIEW -d "https://example.com/products/abc123" com.example.app` (custom scheme + `--activity-new-task` variants); assert synthetic back stacks: `buildSyntheticBackStack(key)` contains Home → List → Detail `.inOrder()`.

### Screenshot Tests

Use screenshots for visual contracts semantics cannot prove: layout spacing/alignment · themed colors, typography, elevation, shadows · image composition, gradients, overlays · focus highlight appearance · loading skeletons/dense visual states.

Keep screenshot state deterministic: fixed state data · freeze clocks or animation progress when possible · replace network/image loading with fake or preview handlers · avoid asserting dynamic text (current time) unless controlled.

**Roborazzi** (JVM via Robolectric — no emulator; runs in CI without hardware):

```toml
roborazzi = "1.27.0"  # piyush   /   "1.38.0" + plugin { id = "io.github.takahirom.roborazzi" } — android-lead
# io.github.takahirom.roborazzi:roborazzi · :roborazzi-compose · :roborazzi-junit-rule
```

```kotlin
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel6)   // android-lead uses sdk = [33]
class HomeScreenScreenshotTest {
    @get:Rule val composeTestRule = createComposeRule()
    @get:Rule val roborazziRule = RoborazziRule(options = RoborazziRule.Options(
        outputDirectoryPath = "src/test/snapshots", captureType = RoborazziRule.CaptureType.Screenshot()))

    @Test fun homeScreen_loading()  { composeTestRule.setContent { MyAppTheme { HomeContent(HomeUiState.Loading, {}, {}) } }; composeTestRule.onRoot().captureRoboImage() }
    @Test fun homeScreen_dark()     { composeTestRule.setContent { MyAppTheme(darkTheme = true) { … } }; composeTestRule.onRoot().captureRoboImage() }
    @Test fun articleCard_largeFont() {
        composeTestRule.setContent {
            AppTheme { CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 1.5f)) { Surface { ArticleCard(...) } } }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
```

```bash
./gradlew recordRoborazziDebug    # record baseline snapshots (first run / intentional changes)
./gradlew verifyRoborazziDebug    # verify no visual regression against snapshots
./gradlew testDebugUnitTest       # CI: unit tests incl. screenshot verification
```

**Compose Preview Screenshot Testing** (official; experimental — APIs may change substantially in alpha): host-side JVM, no emulator, reuses `@Preview` composables, HTML diff report on failure. Write one screenshot test per meaningful UI state (loading, error, success, empty) for key screens.

Setup:
```properties
# gradle.properties
android.experimental.enableScreenshotTest=true
```
```kotlin
android { experimentalProperties["android.experimental.enableScreenshotTest"] = true
    testOptions { screenshotTests { imageDifferenceThreshold = 0.0001f } } }   // 0.01% tolerance
plugins { alias(libs.plugins.screenshot) }   // com.android.compose.screenshot 0.0.1-alpha15+
dependencies { screenshotTestImplementation(libs.screenshot.validation.api); screenshotTestImplementation(libs.androidx.compose.ui.tooling) }
```

Tests go in the `screenshotTest` source set, annotated with BOTH `@PreviewTest` and `@Preview`:

```kotlin
@PreviewTest @Preview(showBackground = true) @Composable
fun LoginScreen_Loading() { AppTheme { LoginScreen(uiState = LoginUiState.Loading, onAction = {}) } }

@PreviewTest
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark") @Composable
fun LoginScreen_Themes() { … }   // likewise fontScale = 1.0f / 1.5f / 2.0f variants
```

```bash
./gradlew updateDebugScreenshotTest    # generate/update reference images (commit them to VCS)
./gradlew :feature:auth:updateDebugScreenshotTest
./gradlew validateDebugScreenshotTest  # CI validation
```
- References: `{module}/src/screenshotTest{Variant}/reference/` (named `fully.qualified.fun_<preview-param-hashes>.png`). Reports: `{module}/build/reports/screenshotTest/preview/{variant}/index.html`.
- Requirements: AGP 8.5.0+ for Gradle tasks; AGP 9.0+/Studio Panda 1 Canary 4+ for IDE integration (gutter run/update icons, per-preview reference-generation dialog, Run-panel results, Reference/Actual/Diff visual diff viewer with match % and attributes); plugin 0.0.1-alpha15+; Kotlin 1.9.20+ (recommended 2.2.10+ w/ Compose Compiler Gradle plugin); JDK 17+.
- Renaming a `@PreviewTest` function breaks the association with existing reference images → regenerate.
- Memory-heavy host tests: `android.compose.screenshot.maxHeapSize=4g` in gradle.properties.
- Known issue: KMP — IDE + plugin are Android-only, no non-Android targets.
- Best practices: one test per meaningful state · always wrap in `AppTheme { }` · test light/dark via `uiMode` · test font scaling (catches overflow at large fonts) · commit reference images (CI baseline) · keep tests in `screenshotTest` source set.

**Dropshots** for device/instrumented screenshots (`com.dropbox.dropshots` plugin + `Dropshots()` rule) — needed when verifying system-UI interaction: edge-to-edge rendering, notifications, picture-in-picture.

Coverage matrix (google): screen-level across 9 window sizes (400/610/900 dp widths × 400/500/1000 dp heights); mobile 400x500 for each alternative theme and fontScale 1.5; component-level across themes/font scales; don't screenshot behavior — inject loading/error states via fakes when the UI diverges a lot.

Caveats & ordering: per-platform rendering/typography/layout differ — shared Android/iOS goldens are brittle; prefer semantic + interaction tests, per-platform visual goldens only for a few high-value screens. Establish ViewModel + validator coverage first, then add screenshots selectively. Skip "pixel-perfect" assertions (brittle — design review instead).

### Networking & Paging Tests

**Ktor MockEngine** (`io.ktor:ktor-client-mock` in commonTest):

```kotlin
@Test fun `fetchAll returns mapped words on 200`() = runTest {
    val mockEngine = MockEngine { request ->
        assertEquals("/api/words", request.url.encodedPath)                     // request assertion
        respond(content = ByteReadChannel("""[{"id":1,"original":"hello"}]"""), // or plain String
            status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val client = createHttpClient(mockEngine)          // SAME factory as production → consistent plugins
    assertIs<Try.Success<List<WordDto>>>(WordRemoteDataSourceImpl(client).fetchAll())
}
@Test fun `fetchAll returns failure on 401`() = runTest { respond(status = HttpStatusCode.Unauthorized) → assertIs<Try.Failure<…>> }
@Test fun `fetchAll returns failure on network error`() = runTest { MockEngine { throw IOException("No route to host") } }
// android-lead also covers 500: respond("Internal Server Error", HttpStatusCode.InternalServerError) → failure
```

Request assertions: `assertEquals(HttpMethod.Post, request.method)`, `request.body.contentType?.toString()`, `val body = (request.body as TextContent).text`. Multiple routes: `when (request.url.encodedPath) { "/items" -> …; "/items/1" -> …; else -> respondError(HttpStatusCode.NotFound) }`.

Error-handling styles:
- `expectSuccess = true` client → assert throw: `assertFailsWith<ClientRequestException> { api.getItem("999") }` on 404.
- `safeRequest` wrapper / `expectSuccess = false` → assert the wrapper's `Result`/sealed variant: `assertTrue(result.isFailure)`.

Testability: accept `HttpClientEngine` as constructor parameter — production `OkHttp.create()`, test `MockEngine { … }`. Provide `HttpClient`/`HttpClientEngine` as singletons in DI (Koin `single { createHttpClient(engine = get(), …) }` / Hilt `@Provides @Singleton`); `expect/actual` platform engine modules (OkHttp Android, Darwin iOS). DataSource coverage requirement: 200 + 4xx + 5xx responses. Integration tier: real HTTP stack against MockWebServer.

**Paging:**

PagingSource unit test (fake/`MockItemApi` injected):

```kotlin
val result = pagingSource.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))
assertTrue(result is PagingSource.LoadResult.Page)
assertEquals(2, page.data.size); assertEquals(null, page.prevKey); assertEquals(2, page.nextKey)
// api with error = IOException("Network error") → assertTrue(result is PagingSource.LoadResult.Error)
```

`TestPager` + `asPagingSourceFactory` for transformation tests:

```kotlin
val pagingSource = dtos.asPagingSourceFactory().invoke()
val result = TestPager(PagingConfig(pageSize = 10), pagingSource).refresh() as PagingSource.LoadResult.Page
assertEquals(1, result.data.size)
```

`asSnapshot` for loading behavior: `viewModel.items.asSnapshot { scrollTo(index = 30) }` → assert size / first id.

ViewModel-level paging: expose `PagingData` as a **separate Flow** from MVI state; route collects both (`collectAsLazyPagingItems()`); screen receives `LazyPagingItems` + state as props. In tests, `PagingData.from(list)` via a fake repo backed by `MutableSharedFlow<PagingData<Product>>(replay = 1)` + `emitProducts(...)`.

`cachedIn()` warning: `cachedIn(viewModelScope)` caches PagingData and can swallow exceptions → error-state testing unreliable. Solutions: (1) test error handling via a separate non-paging `Result`-based flow (`repository.getProductsAsList().catch { emit(Result.failure(it)) }.stateIn(...)`); (2) separate error/loading StateFlows fed from `.catch { error -> _errorState.value = error.message; emit(PagingData.empty()) }` before `cachedIn`. For asserting actual loaded items use `AsyncPagingDataDiffer(diffCallback, updateCallback(no-ops), workerDispatcher = testDispatcher)` + `differ.submitData(pagingData)` + `advanceUntilIdle()` + `differ.snapshot().items`.

Paging testing best practices: keep it simple with `PagingData.from()` · test error handling separately, don't rely on PagingData flows for errors · combine PagingData with `StateFlow<Error>`/`StateFlow<Loading>` · avoid testing pagination library internals — focus on ViewModel business logic · `advanceUntilIdle()` before assertions.

Anti-patterns (networking, meetmiyani):

| Anti-pattern | Why it hurts | Better replacement |
|---|---|---|
| DTOs used directly in UI state | UI coupled to API contract, breaks on API changes | Map to domain models at repository boundary |
| Network calls in composables | Violates UDF, untestable, reruns on recomposition | Call from ViewModel, expose via StateFlow |
| No timeout configuration | Requests hang indefinitely on bad networks | Set `connectTimeoutMillis`, `requestTimeoutMillis`, `socketTimeoutMillis` |
| Hardcoded base URLs | Can't switch environments (dev/staging/prod) | Inject base URL via config or DI |
| Parsing/mapping in the API service | Mixes concerns, harder to test | API service returns DTOs; repository maps to domain |
| Creating a new `HttpClient` per test | Tests miss plugin-config mismatches | Use the same `createHttpClient` factory with `MockEngine` |
| No compression | Wastes bandwidth on text-heavy APIs | `install(ContentEncoding) { gzip() }` |

Anti-patterns (paging, meetmiyani):

| Anti-pattern | Why it hurts | Fix |
|---|---|---|
| `PagingData` inside `UiState` StateFlow | Any non-paging state change re-emits the wrapping StateFlow, creating a new flow for `collectAsLazyPagingItems()` and resetting scroll position | Expose PagingData as **separate** `Flow` |
| New `Pager` per recomposition | Duplicate network requests, lost pagination state | Store `Flow` as `val` in ViewModel |
| Reusing `PagingSource` instance | Crash: "PagingSource was re-used" | Always create new instance in `pagingSourceFactory` |
| Missing `cachedIn(viewModelScope)` | Data lost on config change, duplicate loads | Always call `cachedIn` |
| Missing list keys | Scroll jumps, state corruption on updates | `itemKey { it.id }` with stable domain IDs |
| `combine` on `PagingData` flows | "Collecting from multiple PagingData concurrently" error | Use `flatMapLatest` for parameter changes |
| Calling `refresh()` in composable body | Infinite refresh loop on every recomposition | Call from event handler or `LaunchedEffect` |
| No `LoadState` handling | Broken UX: no loading indicator, no error recovery | Handle `refresh`, `append`, `prepend` states |
| Transformations after `cachedIn` | Transformations lost on cache hit | Apply `.map { }` / `.filter { }` **before** `cachedIn` |
| Catching generic `Exception` in PagingSource | Hides bugs, swallows unexpected errors | Catch `IOException`, `HttpException` specifically |

### TDD Discipline

1. **Red** — write a failing test for the behavior you want. 2. **Green** — write the minimum code to make it pass. 3. **Refactor** — clean up without breaking tests. **Never write production code before a failing test exists.** Never skip Step 1; if asked to implement something without tests, write the test file first, see it fail.

TDD rules (enforcer):
1. No implementation before test — write the test file first, see it fail.
2. One behavior per test — each test verifies one observable outcome; multiple `assert*` calls are fine when they describe one concept.
3. Test names describe behavior — `` fun `deleting word removes it from list`() ``.
4. Fakes over mocks — Mockito/MockK are banned; write FakeRepository instead.
5. No `runBlocking` in tests — always `runTest` (handles virtual time).
6. Reset state between tests — `@BeforeTest`/`@BeforeEach` re-creates fakes and viewmodels; call `fake.reset()` when sharing instances.
7. Cover the sad path — every happy-path test needs a corresponding failure test.

Coverage requirements: ViewModel — 100% of event-sink methods · UseCase — 100% of business logic branches · Repository — happy path + at least one failure mode · DataSource — 200 + 4xx + 5xx responses.

TDD checklist (rezaiyan):
- [ ] Write the test BEFORE writing production code
- [ ] Test name describes behavior: `` `given X when Y then Z` ``
- [ ] One assertion concept per test
- [ ] Use fakes, not mocks — fakes produce real behavior
- [ ] Tests run fast (<100ms each) — no real network, no real disk I/O
- [ ] Test the contract, not the implementation
- [ ] All new ViewModels and UseCases have tests
- [ ] Cover happy path + failure + edge cases
- [ ] Parameterized tests for data-driven scenarios
- [ ] `MainDispatcherRule` uses JUnit5 `@RegisterExtension`, not JUnit4 `@get:Rule`
- [ ] No `LocalDate.now()` / `Clock.System.now()` in test builders — use fixed constants
- [ ] Dispatchers injected, not hardcoded — replaceable in tests
- [ ] `@Nested` used to group happy / failure / edge scenarios

### Coverage

JaCoCo combines unit + instrumented coverage. Use for: CI/CD threshold enforcement, code review (untested paths), trend metrics, team standards. Kover is the alternative used by the rezaiyan playbook's CI/CD pipeline (Kover coverage task in GitHub Actions).

Convention plugins (`app.android.application.jacoco` / `app.android.library.jacoco`) automatically: apply JaCoCo · pin version from catalog · enable coverage for debug builds only · exclude generated code (Hilt, R files, BuildConfig) · create combined report tasks.

```bash
./gradlew testDebugUnitTest          # unit tests
./gradlew connectedDebugAndroidTest  # instrumented (device/emulator)
./gradlew createDebugCombinedCoverageReport            # report
./gradlew :core:data:createDebugCombinedCoverageReport # per module
./gradlew :feature:auth:testDebugUnitTest --tests "*AuthViewModelTest"   # targeted run
```
Reports: `build/reports/jacoco/createDebugCombinedCoverageReport/createDebugCombinedCoverageReport.xml` and `.../html/index.html`.

Auto-excluded: `R.class`, `BuildConfig.class`, `Manifest` · `*_Hilt*.class`, `Hilt_*.class`, `*_Factory.class` · `*Component.class`, `*Module.class`.

```kotlin
tasks.withType<JacocoCoverageVerification>().configureEach {
    violationRules { rule { limit { minimum = "0.80".toBigDecimal() } } }   // 80% gate
}
```
CI: unit job + `reactivecircus/android-emulator-runner` instrumented job + `createDebugCombinedCoverageReport` + `codecov/codecov-action@v4` uploading the XML.

Best practices: run coverage in CI regularly · focus on business logic, don't obsess over 100% · exclude UI code (UI tests cover Compose better) · review trends · don't game metrics — meaningful tests, not coverage-padding.

Troubleshooting: no data → tests actually running/passing? correct dirs (`src/test/`, `src/androidTest/`)? debug variant? · missing classes → check `config/Jacoco.kt` exclusions, plugin applied · Robolectric compat: `isIncludeNoLocationClasses = true; excludes = listOf("jdk.internal.*")` (fixes Robolectric + JDK 11+).

### Lint & Static Analysis

**Detekt** is the primary static analysis tool (claude-android-ninja multi-module playbook): single source of truth `plugins/detekt.yml` with optional per-module overrides · type-resolution-enabled tasks for accurate Android analysis · Compose-specific rules via the Compose detekt ruleset (mrmans0n compose-rules; use the provided template as-is) · Kotlin 2.2.x config without legacy `buildscript`.

`DetektConventionPlugin` (build-logic, id `app.detekt`): applies Detekt from catalog · adds Compose rules automatically · central `config/detekt.yml` + module overrides · type resolution for Android modules · XML, HTML, and SARIF reports. Apply in every module: `alias(libs.plugins.app.detekt)`.

```bash
./gradlew detekt        # all modules
./gradlew :app:detekt   # one module
./gradlew detektMain    # type resolution — slower, more accurate
./gradlew :app:detektBaseline   # app/detekt-baseline.xml — suppress existing issues in that module only; commit it
```

Exclude generated code in `plugins/detekt.yml`: `'**/build/**'`, `'**/generated/**'`, `'**/*.kts'`, `'**/resources/**'`.

Baselines — use when: adopting detekt in an existing project with many violations · preventing new issues without fixing old ones immediately · enabling new rules gradually. Don't use when: starting a new project (fix issues instead) · in active development (baselines hide problems).

CI (GitHub Actions): run `./gradlew detekt` · upload SARIF via `github/codeql-action/upload-sarif@v3` (`build/reports/detekt/detekt.sarif`) with `if: always()` · upload HTML reports as artifact · fail build on issues (default) · Gradle caching; with Gradle toolchains Detekt resolves the proper JDK automatically.

**Suppressions.** Acceptable for `@Composable` functions (declarative UI differs from imperative code):
- `@Suppress("LongMethod")` — composables declare layout trees, naturally long.
- `@Suppress("LongParameterList")` — Route/Screen composables take ViewModel, callbacks, modifier, nav args (6+ is normal at feature entry points).
- `@Suppress("CyclomaticComplexMethod")` — multiple `when` branches for UI states is normal.
Place `@Suppress` directly above `@Composable`. Targeted: `catch (@Suppress("TooGenericExceptionCaught") e: Exception)` — more precise than suppressing the whole function. File-level `@file:Suppress` when the issue affects the whole file: `MatchingDeclarationName` (primary composable + supporting types), `TooManyFunctions` (many small helper composables), `MagicNumber` (layout dimensions).

Do NOT suppress without fixing: `ComplexMethod` in ViewModels/business logic → refactor · `LongParameterList` in data classes → builder/DSL · `TooGenericExceptionCaught` when specific catches work · `UnusedPrivateProperty` → remove.

Best practices: fix, don't suppress (rare) · justify with a comment · be specific (catch param, single function) over file-level · review suppressions regularly (temporary becomes permanent) · accept that Compose violates some imperative-code rules.

**ktlint via Spotless** (root `build.gradle.kts`):

```kotlin
configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin { target("**/*.kt"); targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf(
            "indent_size" to "4", "continuation_indent_size" to "4",
            "max_line_length" to "120", "disabled_rules" to "no-wildcard-imports"))
        licenseHeaderFile(rootProject.file("spotless/copyright.kt")) }
    kotlinGradle { target("**/*.gradle.kts"); ktlint(libs.versions.ktlint.get()) }
}
```

**Compose-Rules lint checks**: Compose detekt ruleset (`compose-rules-detekt` in version catalog) catches Compose-specific issues; verify Compose correctness in reviews via the compose-reviewer checklist below.

### StrictMode

StrictMode = three-tier guardrail in modern Compose apps: 1) classic thread/VM checks, 2) Compose compiler stability diagnostics, 3) CI guardrails.

Classic (init in `Application`, app-level only):

```kotlin
if (BuildConfig.DEBUG) {
    StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
    StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
        .detectLeakedSqlLiteObjects()   // unclosed SQLite cursors
        .detectLeakedClosableObjects()  // unclosed Closeables
        // .detectActivityLeaks() .detectFileUriExposure() .detectCleartextNetwork() .detectUnsafeIntentLaunch() — or .detectAll()
        .penaltyLog().build())
} else {  // Production: silent collection
    StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
        .detectLeakedClosableObjects().detectActivityLeaks()
        .penaltyListener(mainExecutor) { violation -> FirebaseCrashlytics.getInstance().recordException(violation) }
        // or .penaltyDropBox() — system DropBoxManager, read via `adb shell dumpbox`/dumpsys dropbox
        .build())
}
```

- ThreadPolicy: `detectAll()`, `detectDiskReads()`, `detectDiskWrites()`, `detectNetwork()`, `detectCustomSlowCalls()`, `permitAll()`.
- VmPolicy: `detectAll()`, `detectActivityLeaks()`, `detectLeakedClosableObjects()`, `detectLeakedSqlLiteObjects()`, `detectFileUriExposure()`, `detectCleartextNetwork()`, `detectUnsafeIntentLaunch()`.
- Penalties: `penaltyLog()` (default choice) · `penaltyDeath()` (crash on violation during development) · `penaltyFlashScreen()` · `penaltyDropBox()`.
- Production: use `penaltyListener(executor, listener)` to ship violations to crash reporters, or `penaltyDropBox()`. **Avoid `penaltyDeath()` or `penaltyLog()` in production** (crashes users or spams logs).
- When to run production StrictMode: collecting violations from beta/internal builds · monitoring leaks in production (sample 1–5% of users) · verifying fixes in the wild.

Compose stability guardrails (tier 2): gate metrics/reports behind Gradle properties (they're large/slow — don't generate during normal builds):

```kotlin
composeCompiler {
    if (gradleProperty("enableComposeCompilerMetrics")) metricsDestination = layout.buildDirectory.dir("compose-metrics")
    if (gradleProperty("enableComposeCompilerReports")) reportsDestination = layout.buildDirectory.dir("compose-reports")
    enableStrongSkippingMode = true
    stabilityConfigurationFile = rootProject.file("stability_config.conf")
}
// ./gradlew assembleDebug -PenableComposeCompilerMetrics=true [-PenableComposeCompilerReports=true]
```
`stability_config.conf` (root project; Google's recommended filename) marks external types stable: `kotlin.collections.*`, `org.jetbrains.kotlinx.collections.immutable.*`, third-party immutable models, generated code (Room entities, proto messages), sealed hierarchies from dependencies, value classes from SDKs. **When NOT to add:** your own code (annotate `@Stable`/`@Immutable` directly) · mutable types (causes bugs!) · types you're unsure about (verify immutability first). Analyze metrics: look for `unstable` parameters, `skippable: false` composables, high `groups` counts.

Uploading StrictMode signals to crash reporters: debug → `penaltyLog()` to Logcat; production/beta → `penaltyListener()` → Crashlytics `recordException` or `Sentry.captureException`; or auto-collect logcat via reporter config — Sentry `options.logs.isEnabled = true`, Crashlytics Analytics-based log breadcrumbs.

### Crash Reporting

Goals: keep SDK-specific code out of feature modules · interface in `core` + injected implementations · swappable providers / dual reporting · Play Vitals (Reporting API) is store-level observability, not a Crashlytics substitute.

Placement: `core:domain` (or `core:common`) — interfaces/event models · `core:data` (or `core:analytics`) — SDK implementations · `app` — initialization & wiring.

```kotlin
interface CrashReporter {
    fun setUserId(id: String?)
    fun setUserProperty(key: String, value: String)
    fun log(message: String)
    fun recordException(throwable: Throwable, context: Map<String, String> = emptyMap())
}
// FirebaseCrashReporter: setUserId("") / setCustomKey / log / context→setCustomKey + recordException
// SentryCrashReporter: Sentry.setUser(User().apply{id=…}) / setTag / addBreadcrumb /
//   Sentry.withScope { scope -> context.forEach { (k,v) -> scope.setTag(k,v) }; Sentry.captureException(t) }  // Isolated (Local) Scope — tags don't leak to later events
```

Sentry setup: convention plugin `app.sentry` applies `io.sentry.android.gradle` (auto-adds core SDK, uploads mappings) + `io.sentry.kotlin.compiler.gradle` (automatic `@Composable` tagging) + `sentry-android` + `sentry-compose-android`. Manual: plugins `sentry.android` + `sentry.kotlin.compiler`, deps `sentry.android`, `sentry.compose.android`. ContentProvider auto-init via manifest meta-data: `io.sentry.dsn`, `io.sentry.traces.sample-rate`, `io.sentry.traces.user-interaction.enable`, `io.sentry.attach-view-hierarchy`, `io.sentry.attach-screenshot`.

```kotlin
SentryAndroid.init(this) { options ->
    options.dsn = "YOUR_DSN_HERE"          // "" to disable in debug
    options.logs.isEnabled = true          // ship StrictMode penaltyLog output
    options.environment = if (BuildConfig.DEBUG) "debug" else "production"
    options.release = BuildConfig.VERSION_NAME
    options.tracesSampleRate = 1.0         // or tracesSampler = { 0.2 } for dynamic control
    // tracePropagationTargets, propagateTraceparent, traceOptionsRequests
    options.profilesSampleRate = 1.0       // OR profileSessionSampleRate — never both; requires tracesSampleRate > 0
    // profileLifecycle = SentryOptions.ProfileLifecycle.TRACE; startProfilerOnAppStart
    options.enableAutoSessionTracking = true
    options.sendDefaultPii = false
    // sampleRate (error events), maxBreadcrumbs, attachStacktrace, attachThreads, collectAdditionalContext, inAppIncludes/inAppExcludes
}
```
Compose specifics: automatic navigation breadcrumbs/transactions with `androidx.navigation` · `sentry-kotlin-compiler` tags composables by function name (no manual `Modifier.sentryTag()`) · manual critical-screen tracing with `SentryTraced(name = "auth_profile_screen") { … }`.

Firebase setup: convention plugin `app.firebase` applies `com.google.gms.google-services` + `com.google.firebase.crashlytics`, Firebase BoM, `firebase-analytics` + `firebase-crashlytics`, native symbol uploads. `-ktx` artifacts no longer needed with BoM. Init: `FirebaseApp.initializeApp(this)`.

Crashlytics breadcrumbs don't include Compose destination names → log screen transitions in the app-level `AppNavigation()` coordinator: `LaunchedEffect(navigationState.topLevelRoute)` → current stack `.last()` → `analytics.logScreenView(route::class.simpleName ?: "Unknown", "MainActivity")`.

Capture UI state via delegation (params NOT private — delegated only):

```kotlin
@HiltViewModel
class AuthViewModel @Inject constructor(private val savedStateHandle: SavedStateHandle,
    crashReporter: CrashReporter, logger: CrashlyticsStateLogger,
) : ViewModel(), CrashReporter by crashReporter, CrashlyticsStateLogger by logger {
    fun onRoleSelected(role: String) { logUiState("auth_role", role); logAction("Auth role selected: $role") }
    fun onLoginFailed(error: Throwable) { recordException(error, mapOf("action" to "login", "screen" to "auth")) }
}
```
Non-fatal exceptions in coroutines: `CoroutineExceptionHandler { _, e -> Firebase.crashlytics.recordException(e) }` → `viewModelScope.launch(crashHandler) { … }`.

Wiring/switching: `@Binds CrashReporter → FirebaseCrashReporter` or `→ SentryCrashReporter` in `SingletonComponent`; switch = change binding + convention plugin; feature modules untouched.

Best practices: initialize once in app module · Sentry scopes — `Sentry.withScope` (local) for one-off tagged captures; `configureScope` on main thread = Global Scope, on background thread = Thread Scope · avoid PII, keep identifiers minimal, data scrub sensitive info · sample perf tracing/profiling · send non-fatals intentionally (only what helps, e.g. API failures) · correlate with Analytics events (user flow before crash) · quality breadcrumbs (user actions/state changes, not internals) · upload ProGuard/R8 mappings for symbolicated stacks, keep line numbers.

R8 mappings: Firebase plugin uploads automatically during build; keep `SourceFile` + `LineNumberTable` attributes for exact line numbers. Sentry: `sentry { includeSourceContext.set(true); includeProguardMapping.set(true); autoUploadProguardMapping.set(true); org.set(…); projectName.set(…); authToken.set(System.getenv("SENTRY_AUTH_TOKEN")) }`.

Breadcrumbs — good: navigation ("User navigated to profile screen", category `navigation`), clicks (category `ui.click`, `data = mapOf("button_id" …)`), state changes (category `state`) via `Breadcrumb().apply { … level = SentryLevel.INFO }`. Bad (noise/PII): "Coroutine launched on IO dispatcher", "getUserId() called", `$entireUserObject` dumps.

Network tracking: catch `IOException` vs `HttpException` separately; `log("Network error during login: …")` + `recordException(e, mapOf("endpoint" to "auth/login", "error_type" to "network" | "status_code" to e.code().toString() + "error_type" to "http"))`. Sentry + OkHttp: `sentry-okhttp` integration for automatic network breadcrumbs.

Testing & hygiene: debug-only `DebugViewModel.testCrash()` (guarded by `BuildConfig.DEBUG`) recording a tagged non-fatal + optional commented fatal throw, exposed via a Debug screen · disable collection in debug: manifest `firebase_crashlytics_collection_enabled=false` or `setCrashlyticsCollectionEnabled(false)`; Sentry empty DSN per build type.

Scrubbing: Sentry built-in PII scrubbing + `sendDefaultPii = false` + `setBeforeSend { event, hint -> … }` (regex-redact emails, `event.removeTag("user_email")`, `removeExtra("raw_user_data")`). Provider-agnostic: `PrivacyAwareCrashReporter` delegating wrapper filtering keys containing password/token/secret/key/auth and regex-replacing emails in messages/context.

### Debugging

Logcat: `adb logcat --pid=$(adb shell pidof -s com.example.app)` · `adb logcat -s "YourTag:E"` · `adb logcat -d > crash_log.txt`. Levels V/D/I/W/E/F. Discipline: `Log.i` normal checkpoints (non-PII parameters) · `Log.w` recoverable problems (fallback, retry, handled unexpected state) · `Log.e` failures (request failed, uncaught path logged before UI mapping). Avoid `Log.v`/`Log.d` spam in hot paths of release builds; never log secrets/tokens/personal data. **The root cause is usually at the bottom of the `Caused by:` chain** — read the full stack trace before forming a hypothesis.

ANR: system watchdog fired on main thread / bounded callback path:

| Scenario | Typical timeout |
|---|---|
| Input dispatch (touch, key) on main | ~5 seconds |
| `BroadcastReceiver.onReceive` on main | ~10 seconds |
| Service start / bound work on main | ~20 seconds for some paths |

Evidence: `adb pull /data/anr/traces.txt ./anr_traces.txt`; `adb logcat -s "ActivityManager:E" | grep -A 30 "ANR in"`. In the trace find the `main` thread: `MONITOR` = waiting on a lock (deadlock candidate) · `TIMED_WAITING` on sleep = `Thread.sleep()` on main · blocking I/O = DB/network/file on main. Common causes: DB/network on main, `runBlocking` on main, deadlock between coroutine scopes, expensive computation (JSON parse, bitmap decode) on main.

Memory leaks — common causes: static references to Activity Context (use Application context) · non-static inner classes holding Activity (use static inner + `WeakReference`, or coroutines tied to `lifecycleScope`) · Handler with delayed messages (always `handler.removeCallbacksAndMessages(null)` in `onDestroy()`). LeakCanary: Android Studio Panda 3+ has built-in "Analyze Leaks" Profiler task (no dependency); older — `debugImplementation(libs.leakcanary)`, notification trace: first bold entry = leaking object, path shows the holder; fix by clearing the reference in the right lifecycle callback. Manual: `adb shell am dumpheap com.example.app /data/local/tmp/heap.hprof` + `adb pull` → Memory Profiler.

R8 de-obfuscation — outputs in `app/build/outputs/mapping/<variant>/`: `mapping.txt` (names back to original) · `usage.txt` (removed/tree-shaken) · `seeds.txt` (kept by `-keep`) · `configuration.txt` (merged config). **Always archive `mapping.txt` alongside every release build.** Retrace: `./gradlew :app:retrace --stacktrace-file crash.txt` or `retrace mapping.txt crash.txt` (bundled at `$ANDROID_HOME/cmdline-tools/latest/bin/retrace`). Manual decode: search `-> a.b.c:` for the class → `-> b` for the method → line = `originalStart + (obfuscatedLine - startLine)` (e.g. `42 + (2 - 1)` = 43) → repeat per frame. Unexpected removals: grep `usage.txt` (removed → add `-keep` rule); absent from both files → not in any dependency.

Gradle failures: read errors **bottom up** (Gradle wraps in layers). Patterns: `Manifest merger failed` → `app/build/intermediates/merged_manifests/` · `Duplicate class` → `./gradlew :app:dependencies` transitive dupe · `Could not resolve` → repos/VPN/version exists · `Unresolved reference` → import/module classpath/typo · `Type mismatch` → generic/nullable/API change after bump · `@Composable invocations` → composable from non-composable context · `AAPT`/resource → invalid XML, bad `@drawable`, res merge conflict · `D8/R8: Type not present` → keep rule/desugaring/minSdk vs API · `KSP error` → processor message above the wrapper. Investigate: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`, `./gradlew assembleDebug --stacktrace --info`.

Compose recomposition debugging: Layout Inspector → "Show recomposition counts"; temporary `SideEffect { Log.d("Recompose", "MyScreen recomposed") }`. Common causes: `State` created inside composition without `remember` · missing `equals()` on state classes (new instance, same values, still recomposes without structural equality) · unstable lambda references (only relevant with Strong Skipping disabled — default-on since Compose Compiler 2.0+/Kotlin 2.0+ auto-memoizes lambdas).

Multi-layer boundary debugging: instrument each layer boundary temporarily (`Log.d("DEBUG_LAYER", "Repository: result=$it")` in repo/VM/UI) → identify the layer producing the bad value → investigate it in isolation → **remove debug logging before committing**.

ADB quick reference: `adb devices` · `adb install -r app-debug.apk` · `adb shell am start -n com.example.app/.MainActivity` · `adb shell pm clear com.example.app` · `adb exec-out screencap -p > screen.png` · `adb shell ps | grep com.example` · `adb shell run-as com.example.app ls /data/data/com.example.app/` · `adb forward tcp:8080 tcp:8080` · `adb shell dumpsys meminfo|batterystats|gfxinfo [framestats] com.example.app`.

Red Flags:
- Fixing a crash without reading the full `Caused by:` chain
- Guessing at an R8 issue without checking the mapping file
- Adding `Thread.sleep()` to "fix" an ANR or race condition
- Resolving a dependency conflict with `exclude` without understanding why the duplicate exists
- Wrapping a Compose bug in `key()` without understanding what triggers recomposition

### Quality Checklist

Compose code review checklist (run after writing/modifying any Compose UI):

1. Recomposition & stability: unstable lambdas captured as composable parameters (use `remember {}` / `::` refs) · `remember {}` inside a lambda body (stale outer state) · unstable params — `List<T>` → `ImmutableList<T>`, mutable data classes → `@Stable`/`@Immutable` · `@Immutable` on a class whose fields can change → `@Stable` instead (`@Immutable` means ALL fields immutable AND stable) · missing `key` in LazyColumn/LazyRow · derived state not in `remember { derivedStateOf {} }` (bare `derivedStateOf` recalculates every recomposition) · heavy computation directly in composition scope · `snapshotFlow {}` outside composition.

```kotlin
// BAD — new lambda every recomposition; missing key causes item identity churn
items(words) { word -> WordCard(word = word, onClick = { onWordClick(word) }) }
// GOOD — key fixes item identity; lambda stability is a separate concern
items(words, key = { it.id }) { word -> val onClick = remember(word.id) { { onWordClick(word) } }; WordCard(word, onClick) }
```

2. Modifier ordering: `fillMaxSize`/`size` before `padding` · `wrapContentSize` must NOT come after `fillMaxSize` (silently no-ops) · `clickable` before `padding` when click area should include padding · `background` before `padding` · `border` after `background`, before `padding`. BAD `Modifier.padding(16.dp).fillMaxWidth().background(color)` → GOOD `Modifier.fillMaxWidth().background(color).padding(16.dp)`.

3. State hoisting: non-UI state `remember { mutableStateOf }` that belongs in ViewModel · `collectAsState`/`collectAsStateWithLifecycle` → project's `viewModel.state()` Compose-native pattern · composable returning a value · side-effectful state writes in composition · business logic in `@Composable` → domain/use cases.

4. Side effects: `LaunchedEffect(Unit)` when a real dependency exists — CRITICAL, missed re-triggers (use `LaunchedEffect(userId)`) · `produceState(Unit)` same problem · `SideEffect` for one-time work (runs every recomposition — use LaunchedEffect) · `LaunchedEffect` for navigation/one-shot events → `OnEvents(viewModel.effects) { … }` with SharedFlow/Channel · launching in composition without `rememberCoroutineScope` · `DisposableEffect` missing `onDispose {}`.

5. Slot API & design: god composables (>~100 UI lines) · >5 params → data class/slot · hardcoded colors/typography instead of `MaterialTheme.*` · hardcoded strings instead of `stringResource(R.string.*)` · missing `modifier: Modifier = Modifier` on public composables · missing `@Preview` · `CompositionLocal` for non-cross-cutting data (theme/locale only) · `AndroidView`/`AndroidViewBinding` without `update` lambda.

6. Accessibility: `Image`/`Icon` without `contentDescription` (`null` if decorative, never omitted) · touch targets < 48×48dp · custom interactive elements missing `Modifier.semantics { role = Role.Button }` · color-only meaning.

7. Performance: state read in `drawBehind`/`Canvas` without lambda wrapper · `Painter`/`ImageBitmap` without `remember` · `buildAnnotatedString` without `remember`.

Grep for anti-patterns before line-by-line reading: `collectAsState` (PATTERN) · `List<` in `@Composable fun` signatures (PERF → ImmutableList) · `LaunchedEffect(Unit)` (CRITICAL if real dependency) · `buildAnnotatedString` outside `remember` (PERF) · `derivedStateOf` outside `remember` (PERF) · `Color(0x`/`Color(0xFF` hardcoded (PATTERN) · `items(` without `key =` (PERF) · `@Immutable` misuse (CRITICAL) · `try {` inside composable (PATTERN — banned control flow).

Severity levels & output:

| Severity | Meaning |
|---|---|
| CRITICAL | Correctness bug — wrong behavior at runtime (`LaunchedEffect(Unit)` w/ real dependency, `@Immutable` on mutable class) |
| PERF | Recomposition waste or jank (unstable lambda, missing `key`, missing `remember`) |
| PATTERN | Architecture/convention violation (logic in composable, `collectAsState`, missing `modifier` param) |
| A11Y | Accessibility (missing `contentDescription`, small touch target) |

Format per finding: `[SEVERITY] File:Line — description / Why: … / Fix: concrete code change`; group CRITICAL first; end with severity counts; if clean, confirm and explain why.

Complementary reviews: stability annotations via reflection tests (`User::class.findAnnotation<Immutable>()`, all-`val` check, sealed subclasses) + Compose stability analyzer at build time.

Pre-ship quality gate (release checklist essentials): `versionCode` incremented · release build **tested on physical device with R8 enabled** (Debug works ≠ Release works — always test the release APK/AAB) · `isMinifyEnabled = true` + `isShrinkResources = true` (false → 2-3× APK, visible code) · baseline profile in APK (`unzip -l app-release.apk | grep baseline` → `assets/dexopt/baseline.prof`/`.profm`) · crash-free in Crashlytics · keystore/passwords NEVER in git (env vars/Secrets) · `bundleRelease` for Play (AAB ~15% smaller, required for new apps) · different debug `applicationIdSuffix` (install both for testing) · no `fallbackToDestructiveMigration()` in release (deletes user data) · coverage/tests green in CI.

### Anti-Patterns

Testing (verbatim "common mistakes", piyush):
- ❌ Testing without `MainDispatcherRule` — coroutine tests hang or produce wrong results
- ❌ Mocking Repository in ViewModel tests — use Fake instead for reliability
- ❌ Missing `@After` to close in-memory DB — causes test pollution
- ❌ `runBlocking` in tests — use `runTest` from coroutines-test
- ❌ Testing ViewModel with real network calls — always fake/mock external dependencies
- ❌ Missing `allowMainThreadQueries()` for Room in tests — crash on test thread

Anti-patterns (compose-kotlin 11-testing, verbatim):
- **Mocking everything with MockK** → tests pass but don't verify behavior. Fakes > mocks
- **Testing ViewModel with real repository** → slow, flaky, tests two things. Use fake
- **`Thread.sleep()` in tests** → flaky. Use `advanceUntilIdle()` or Turbine's `awaitItem()`
- **Testing private ViewModel functions** → test through public state. If `_calculateScore()` is correct, `state.score` reflects it
- **One giant test class** → split by feature. `AuthViewModelTest`, `WorkoutViewModelTest`
- **No test dispatcher rule** → tests hang or fail on `Dispatchers.Main`. Always include `MainDispatcherRule`

Anti-patterns (meetmiyani testing):

| Anti-pattern | Why it hurts | Better replacement |
|---|---|---|
| No ViewModel tests, only UI tests | slow feedback, flaky, hard to isolate failures | ViewModel event→state→effect tests with Turbine first |
| Testing implementation details (private functions, internal state) | brittle tests that break on refactoring | test through public API: send event, assert state/effect |
| Mocking the DI framework | couples tests to DI internals | swap real implementations with fakes via constructor injection |
| Screenshot tests before ViewModel coverage | high maintenance, low defect yield | establish ViewModel + validator coverage first, then add screenshots selectively |
| Testing derived/computed properties in isolation from ViewModel | duplicates logic, drifts from real behavior | test derived values through ViewModel state assertions |
| Sharing mutable test fixtures across tests | hidden coupling, order-dependent failures | fresh state per test, explicit setup in each test function |

Never-use (rezaiyan playbook): `runBlocking` in tests — use `runTest` · mocks in tests — write fakes instead · `try-catch` for control flow — `Try<T>` or `Flow.catch {}` · `LocalDate.now()` in builders · hardcoded `Dispatchers.IO`.
