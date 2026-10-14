## DOMAIN: Architecture & Kotlin Foundations

### 2026 Toolchain — Non-Negotiable Baseline

| Tool | Minimum | Notes |
|------|---------|-------|
| Kotlin | **2.0+** (2.1.x) | K2 compiler only — no K1 fallback |
| AGP | **9.0+** | Built-in Kotlin, new DSL defaults |
| Compose BOM | **2025.05+** | Material 3, strong skipping default |
| Navigation | **3.x** (`2.9+` artifact) | Type-safe `NavKey`, `@Serializable` routes |
| compileSdk / targetSdk | **35+** | Edge-to-edge mandatory; hardcoding `compileSdk 34` in a new app is banned (Play policy) |
| JDK | **17** | Required for AGP 9 |

```toml
[versions]
kotlin = "2.1.20"
agp = "9.0.0"
compose-bom = "2025.05.00"
navigation = "2.9.0"
room = "2.7.1"
hilt = "2.56.2"
lifecycle = "2.9.0"
coroutines = "1.10.2"
```

Kotlin 2.x / K2 compiler constraints:
- Use `ksp` not `kapt` for Hilt/Room (kapt in a new project = banned; slow builds, K2 incompatibility).
- Prefer smart casts: `when (val x = state) { is Loaded -> ... }` over unsafe `!!`.
- Sealed hierarchy: `sealed interface UiState` + `data object Loading : UiState` for exhaustive `when`.
- `@JvmInline value class` for IDs: `@JvmInline value class UserId(val raw: String)`.
- `@Immutable` / `@Stable` on UiState and UI models passed to Compose.
- Explicit API optional for libraries — `kotlin { explicitApi() }` in published modules.
- No `!!` except in tests with a comment — use `requireNotNull` / early return / safe call / Elvis.
- `data class` `copy` for state — never mutate UiState fields in place.

```kotlin
sealed interface AccountsUiState {
    data object Loading : AccountsUiState
    data class Ready(val accounts: List<AccountUi>, val query: String = "") : AccountsUiState
    data class Error(@StringRes val messageRes: Int) : AccountsUiState
}
```

### Layered Architecture (Clean Architecture)

```
┌─────────────────────────────────────────────┐
│  presentation/   (UI + ViewModel)           │  Composables render state;
│  ├── Composables render state               │  ViewModel transforms domain → UiState
│  ├── ViewModel transforms domain → UiState  │
│  └── Depends on: domain                     │
├─────────────────────────────────────────────┤
│  domain/         (Pure Kotlin — no Android) │
│  ├── Models (data classes)                  │
│  ├── Repository interfaces                  │
│  ├── UseCases (optional)                    │
│  └── Depends on: nothing                    │
├─────────────────────────────────────────────┤
│  data/           (Implementation)           │
│  ├── Repository implementations             │
│  ├── Room entities + DAOs                   │
│  ├── Network DTOs + API services            │
│  ├── Mappers (DTO ↔ domain, Entity ↔ domain)│
│  └── Depends on: domain                     │
└─────────────────────────────────────────────┘
        Dependencies point INWARD only
```

- **The dependency rule:** source-code dependencies point toward the center only. Outer layers depend on inner layers. Domain knows nothing about Room, Retrofit/Ktor, Compose, or any framework. Data implements domain interfaces. Presentation consumes domain models.
- Architecture: MVVM with unidirectional data flow, layered by Clean Architecture: `Presentation → Domain → Data`. ViewModels depend on UseCases (or Repositories directly for simple features). The data layer depends on nothing in the app layer.
- Core principles: offline-first (local DB is source of truth, sync with remote); reactive streams — expose all data as `Flow<T>`/`StateFlow<T>`; modular by feature; testable by design (interfaces + fakes; mocking frameworks only for framework classes in the app module); strict layer separation; features depend on core modules, never on other features; app module coordinates navigation; pattern fit — choose patterns matching Android constraints and module boundaries.
- Four-layer variant (feature modules + core/domain + core/data + core/ui): Presentation (Screen + ViewModel with `StateFlow<UiState>`) → Domain (UseCases combining/transforming, Repository interfaces, Domain models) → Data (Repository impls offline-first single source of truth, Local DataSource = Room DAO, Remote DataSource = Retrofit, Data models: Entity/DTO) → shared Core/UI (design system, themes, base ViewModels).
- Domain layer rules: pure business logic, zero platform imports (runs in `commonTest` without emulators); domain models ≠ DTOs or entities; repository interfaces in domain, impls in data (dependency inversion); mappers at the data boundary — domain ignores serialization; use cases only for multi-step orchestration — don't wrap single repo calls.
- Domain modules can be: pure JVM/Kotlin (`app.jvm.library`) — no Android deps; or Android library (`app.android.library`) only if you need `@Immutable`/`@Stable` on domain models (`androidx.compose.runtime` is a Kotlin-only library despite the `androidx` namespace).
- No `Context`/`android.*` in a UseCase; no concrete framework imports (`androidx.room.*`, `io.ktor.*`) in domain.
- Data-flow through layers:
  - Events DOWN: `User Action → Screen → ViewModel → UseCase → Repository → Data Source`.
  - Data UP: `Data Source → Repository → UseCase → ViewModel → UiState → Screen → Recomposition`.
  - Navigation: `Screen → Navigator Interface → App Module → Navigation3 → Destination`.
- Do not force a full Clean Architecture ceremony on small apps: for small apps with no network layer, a single model is acceptable; split into DTO + Entity + Domain when Room annotations would leak or when a network layer appears.

### MVI vs MVVM — Decision Guide

- **Preservation rule:** if the project already has a coherent screen architecture (MVI, MVVM, or variant), preserve it unless the user explicitly asks to migrate or the current pattern cannot satisfy a required constraint. Never force an MVI migration on an existing codebase — introduce MVI for new features only.
- Both use unidirectional data flow with `StateFlow<State>` and `Channel<Effect>`. The difference is how UI actions reach the ViewModel.

| Criterion | MVI | MVVM |
|---|---|---|
| UI-to-VM contract | `sealed interface Event` + `onEvent()` | Named public functions |
| State | Single UiState sealed class or data class | Multiple StateFlows or one state object |
| Events | Sealed UiEvent dispatched | Direct function calls |
| Boilerplate | Higher (sealed class + when) | Lower (direct calls) |
| Testing input | Single `onEvent()` entry point | Multiple function entry points |
| Debugging | Easier — single state timeline | Harder — multiple state sources |
| Complexity | Higher, but predictable | Lower for simple screens |
| Best for | Many events, event logging/analytics, complex screens, multi-step flows | Simpler screens, forms, settings, CRUD, less ceremony |

- **Rule of thumb:** start MVVM. Switch to MVI when you have 3+ interdependent state fields or multi-step flows.
- **Choose MVI when:** project uses MVI; many user actions to enumerate; need exhaustive event contracts; team values explicit event contracts for debugging/analytics/time-travel; complex interrelated state transitions.
- **Choose MVVM when:** project uses MVVM; few actions; team prefers direct function calls; migrating from View-based MVVM to Compose; named functions provide sufficient discoverability.
- Use lighter patterns for: purely presentational leaf composables; small screens with trivial local state and no async/persistence; prototypes unless the user asks to formalize. Do not invent reducers, result types, or global frameworks unless they earn their keep.

| Situation | Default state owner | Why |
|---|---|---|
| Visual state for one composable subtree | Local Compose state | Smallest scope, easiest reuse |
| Complex UI logic, no business/data responsibilities | Plain state holder class | Testable without ViewModel |
| Screen-level business rules, async, persistence, effects | ViewModel | Lifecycle integration, screen state ownership |

- A ViewModel is one implementation of a screen state holder, not a requirement for every composable. Give a screen a dedicated ViewModel when it has: async data, multi-field editing, validation, derived calculations, navigation effects, retry/refresh flow, persistent draft/original comparison.
- Lighter state holder suffices for: purely visual tab selection, local expansion, local scroll affordance, tooltip/menu visibility — that is local UI state, not architecture.

Adapting to existing projects:

| Project has | Action |
|---|---|
| MVI with base class (`MviHost`, `BaseViewModel`) | Use it. Don't introduce a competing base |
| MVVM without strict MVI | Preserve it. Match conventions |
| Plain state holder classes | Valid. Only move to ViewModel when screen needs async/persistence/lifecycle |
| 4-type MVI (Event, Result, State, Effect) | Use `Result` as the project expects. Don't strip it out |
| No architecture | Choose MVI or MVVM per guide. Trivial screens: local state is fine |

Scaling notes: small screens — one file for contract + ViewModel; medium — split contract, ViewModel, screen, route; large — extract calculation/validation/formatting into dedicated collaborators. Do NOT create nested state holders for every card/section by default — only when independent lifecycle, async, tests, and real reuse justify it.

### State Modeling (UiState / UiEvent / UiEffect)

- Per screen, keep three sources of truth separate and never mix them: **Screen behavior** → `StateFlow<ScreenState>` owned by the screen state holder (usually ViewModel); **Persisted data** → repository/database/remote; **Local visual-only concerns** → local Compose state in the route/leaf composable.
- **State** = immutable description of what the screen renders; given the same state, the screen always looks the same. One state per screen, owned via `StateFlow<State>`.
- State must be **equality-friendly**: `data class` with immutable collections. Store canonical values; derive display values at the UI boundary. Computed properties acceptable for trivial derivations.
- **Sealed vs flat data class:** use a flat data class when states can overlap (e.g. showing stale data while loading, error + retry simultaneously); use sealed when states are truly mutually exclusive (multi-step wizard, auth flow, onboarding steps). Sealed is wrong when you need "loading + previous data visible at the same time" — that requires two independent fields, not one sealed branch.
- Sealed-state rules (strict variant): `Loading` never carries data (screen shows skeleton); `Ready` carries everything the screen needs — no nullable fields without a reason; `Error` carries the failure message and whether recovery is user-actionable (`canRetry`). Never add a `data` field to `Loading` or ad-hoc boolean flags to `Ready` (e.g. `isRefreshing`) — add a distinct state or a wrapper instead.
- **Forms/calculators — split into four buckets:** 1) editable input (raw text/choice as user edits), 2) derived/computed (parsed, validated, calculated), 3) persisted snapshot (existing saved entity for dirty tracking), 4) transient UI-only (purely visual, not business-significant).

| Concern | Where | Example |
|---|---|---|
| Raw field text | `state` | `"12"`, `"12."`, `""` |
| Parsed value | computed property or `state` | `val amount get() = amountText.toDoubleOrNull()` |
| Validation | `state.errors` | `mapOf("area" to "Required")` |
| Calculated totals | `state` or computed | subtotal, tax |
| Loading/refresh | `state` flags | `isSaving`, `isLoading` |
| One-off commands | `Effect` via Channel | snackbar, navigate |
| Scroll/focus/animation | local Compose state | `LazyListState`, expansion toggle |

- **Avoid duplicated state:** don't store `total` + `formattedTotal` + `totalText`, or `showErrorDialog` + `pendingError` when one implies the other. Keep one canonical value + derive the rest.
- UiState contract (MVI, single-object variant):

```kotlin
@Immutable
data class FeatureUiState(
    val items: List<ItemUi> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiError? = null
)

sealed interface FeatureUiEvent {
    data class SearchChanged(val query: String) : FeatureUiEvent
    data object Refresh : FeatureUiEvent
    data class ItemClicked(val id: String) : FeatureUiEvent
}

sealed interface FeatureUiEffect {
    data class ShowMessage(@StringRes val messageRes: Int) : FeatureUiEffect
    data class NavigateToDetail(val id: String) : FeatureUiEffect
}
```

- UiState sealed-hierarchy variant:

```kotlin
@Immutable
sealed interface AuthUiState {
    data object Loading : AuthUiState
    data class LoginForm(val email: String, val password: String, val error: String?) : AuthUiState
    data class Success(val user: User) : AuthUiState
    data class Error(val message: String, val canRetry: Boolean = true) : AuthUiState
}
```

- **Where logic belongs:**

| Logic | Where |
|---|---|
| Validation | ViewModel/domain — never in composable body |
| Calculations | Pure calculator/domain service called by ViewModel |
| Async orchestration | ViewModel — launch/cancel, debounce, ignore stale |
| Side effects | ViewModel via `Effect` or `viewModelScope.launch` |
| Local UI state | Composable — `LazyListState`, focus, animation, expansion, tooltip |

- Not acceptable in composables: validation, derived totals, data loading, submit enablement, business decisions.
- **Event naming:** name events from the **user's perspective** — what happened, not what should happen.

| Good | Bad |
|---|---|
| `OnSaveClick` | `SaveCategory` |
| `OnTitleChanged` | `UpdateTitle` |
| `OnRetryClick` | `RetryRequest` |
| `OnBackClick` | `NavigateBack` |

- The event describes a user action; the ViewModel decides how to handle it.
- Form-heavy screens: specific event names for screen-level actions, generic `FieldChanged(field, raw)` only when many fields are structurally similar (`enum class FormField { Area, MaterialRate, ... }`).
- MVVM defines 2 types (`State`, `Effect`); user actions call named ViewModel functions directly. MVI defines 3 types (`Event`, `State`, `Effect`); a 4th type (`Result`/`PartialState`) is rarely justified — consider it only when the same state transition is triggered by many different sources (events, async completions, WebSocket, push) and you want to centralize all transitions in one pure function. For most screens, `onEvent()` handling state updates directly is simpler.
- Do not add a fourth parallel type (`Result`/`PartialState`/mandatory pure `reduce`) when every event maps 1:1 to a small state change — `when (action) { ... }` with `update` is simpler and easier to follow.

**Actions-pattern alternative (sealed actions instead of individual callbacks):** one sealed `ProfileAction` interface and one `onAction(action)` ViewModel method gives a clean screen API and an easy-to-test ViewModel; screen composable receives one lambda `onAction: (ProfileAction) -> Unit`. For MVVM screens with many actions, group related callbacks into a single interface (`interface CreateItemActions { fun onTitleChanged(title: String); fun onSaveClick(); ... }`) which the ViewModel implements — structure without sealed-event ceremony.
- An **event bus for local ViewModel→Screen communication is wrong** — use a direct method call (event sink): `viewModel.deleteWord(word)`. Some project conventions (KMP playbook) reject sealed Event/Intent classes entirely in favor of public ViewModel methods as event sinks; when the host project picks one, follow it — the universal rule is a single decision point (either `onEvent()` or named functions), never scattered `updateState`/`sendEffect` across callbacks.

### ViewModel Rules — Atomic State, Exposure, Structure

Required pattern:

```kotlin
class FeatureViewModel @Inject constructor(
    private val repository: FeatureRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FeatureUiState())
    val state: StateFlow<FeatureUiState> = _state.asStateFlow()

    private val _effects = Channel<FeatureUiEffect>(Channel.BUFFERED)
    val effects: Flow<FeatureUiEffect> = _effects.receiveAsFlow()

    fun onEvent(event: FeatureUiEvent) {
        when (event) {
            is FeatureUiEvent.SearchChanged -> onSearchChanged(event.query)
            FeatureUiEvent.Refresh -> refresh()
            is FeatureUiEvent.ItemClicked -> emitNavigate(event.id)
        }
    }

    private fun onSearchChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }  // ONLY mutation style allowed
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.sync()
                .onSuccess { _state.update { it.copy(isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUiError()) } }
        }
    }

    private fun emitNavigate(id: String) {
        viewModelScope.launch { _effects.send(FeatureUiEffect.NavigateToDetail(id)) }
    }
}
```

- **Allowed mutation:** `_state.update { it.copy(...) }` or `_state.update { prev -> ... }` only.
- **Banned in ViewModel:**

```kotlin
// BANNED
_state.value = AccountsUiState(...)           // naked assign — race + non-atomic
_state.value.accounts.add(item)               // mutating list inside state
mutableStateOf / mutableStateListOf in VM     // Compose scope only
viewModelScope.launch { _state.value = ... }  // use update inside launch
```

- Always expose `StateFlow`, never `MutableStateFlow`. State changes happen through ViewModel functions only:

```kotlin
// BAD — UI can mutate state directly
val state = MutableStateFlow(AccountsUiState())
// RIGHT — private mutable, public read-only
private val _state = MutableStateFlow(AccountsUiState())
val state: StateFlow<AccountsUiState> = _state.asStateFlow()
```

- Screen state holder anatomy (three responsibilities): **state ownership** — holds `MutableStateFlow<State>`, exposes `StateFlow<State>`; **effect delivery** — holds `Channel<Effect>` (or project equivalent), exposes `Flow<Effect>`; **event processing** — `onEvent()` (MVI) or named action functions (MVVM). State updated via thread-safe `update` (`MutableStateFlow.update { it.copy(...) }` or wrapper `updateState { copy(...) }`); effects sent via `channel.trySend(effect)`.
- A screen ViewModel should be `@HiltViewModel` + `@Inject constructor` (or project DI equivalent) and obtained in the route via `hiltViewModel()` / `koinViewModel()`.
- Extract navigation args in the ViewModel via `SavedStateHandle.toRoute<DetailRoute>()` (type-safe, survives process death) — pass IDs through nav args, fetch models in the destination ViewModel.
- No ViewModel references in composable parameters below the route (pass `onEvent`/lambdas/state down). One ViewModel per screen/feature; if a ViewModel exceeds ~200 lines, extract UseCases or split the screen.
- Don't emit no-op state updates: guard unchanged values before `update` (compare before update / avoid redundant `copy`).
- Reactive data collection inside a ViewModel:

```kotlin
private fun collectData() {
    viewModelScope.launch {
        repository.observe()
            .catch { sendEffect(ShowError(it.message ?: "Load failed")) }
            .collect { data -> updateState { copy(items = data, isLoading = false) } }
    }
}
```

- MVVM production example — `combine` two flows into one UiState + `stateIn`:

```kotlin
val uiState: StateFlow<AccountsUiState> = combine(
    repository.getAllAccounts(),
    _searchQuery
) { accounts, query ->
    val filtered = if (query.isBlank()) accounts
        else accounts.filter {
            it.serviceName.contains(query, ignoreCase = true) ||
            it.accountName.contains(query, ignoreCase = true)
        }
    AccountsUiState(accounts = filtered, searchQuery = query)
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = AccountsUiState(isLoading = true)
)
```
Key decisions: Room auto-emits on DB change; `SharingStarted.WhileSubscribed(5_000)` keeps flow active 5s after last subscriber (survives config change); no `Dispatchers.IO` — Room already switches threads internally; search is reactive — no manual "refresh" needed.
- MVI event-processing flow: `UI gesture / lifecycle signal → Event dispatched via onEvent() → ViewModel processes in when() → synchronous events: updateState { copy(...) } → side effects: sendEffect(effect) → async work: viewModelScope.launch { ... } → on async completion: updateState { copy(...) } + sendEffect(...)`. `onEvent()` is the single decision point keeping all event→reaction logic in one place.
- Property-delegated state holder (Compose-native VM state, `@Stable` class with `var x by mutableStateOf(...) private set` + mutation methods) is an acceptable pattern for interactive state; keep setters private and expose explicit update functions.
- Custom observable delegate (`operator fun getValue/setValue` firing `onChange` only when `value != newValue`) is useful for auto-persist settings properties.
- KMP/CMP project-convention variant (`BaseViewModel<S, E>`): `MutableStateFlow(initialState())` + `updateState { }`, effects via `MutableSharedFlow<E>(extraBufferCapacity = 16)` + `emitEffect()`; expose `collectState()` composable wrapper (CMP `commonMain` has no `collectAsStateWithLifecycle`). A thin base providing `updateState()`, `sendEffect()`, `currentState` is fine at 10+ features; a base forcing `handleEvent()` + `reduce()` + `dispatch()` + `asyncAction()` is overengineering unless the whole team agreed. Use an existing project base class; never introduce a competing one.

### Unidirectional Data Flow (UDF) & UI Rendering Boundary

- State flows down, events flow up (event sink pattern). Events flow down, data flows up. One canonical owner per piece of state.
- **Default state collection:** collect whole screen state once at the route boundary, slice downward. `Route` collects `StateFlow<ScreenState>`; `Screen` receives `ScreenState`; leaves receive **only what they need**; leaves never observe the ViewModel directly.
- **Callbacks at boundaries:** MVI — `onEvent(Event)` at route/screen boundary, leaves prefer specific callbacks; MVVM — individual callbacks at screen boundary, same narrowing for leaves. Reusable components must not know your event contract or ViewModel type; do not pass `onEvent` to reusable leaves — adapt to specific callbacks.
- Composable layer split:
  - **Route composable** — obtains state holder (`koinViewModel()`, `hiltViewModel()`, manual), collects state once via lifecycle-aware collector, collects effects via `CollectEffect`/`LaunchedEffect(Unit) { vm.effects.collect { } }`, binds navigation/snackbar/sheet/platform APIs.
  - **Screen/Content composable** — stateless render function receiving state + `onEvent: (Event) -> Unit` or individual callbacks; no ViewModel reference; fully previewable.
  - **Leaf composables** — render sub-state, emit specific callbacks, keep only tiny visual-local state.

```kotlin
@Composable
fun AccountsRoute(vm: AccountsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.effects.collect { effect -> /* one-shot: nav, snackbar */ }
    }
    AccountsScreen(state = state, onEvent = vm::onEvent)
}
```

```kotlin
// Container (has ViewModel) → Content (stateless, previewable)
@Composable
fun AccountsScreen(viewModel: AuthViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountsContent(
        accounts = state.accounts,
        searchQuery = state.searchQuery,
        onSearchChanged = viewModel::onSearchQueryChanged,
        onDelete = viewModel::onDeleteAccount,
        onAdd = viewModel::onAddAccount
    )
}
```

- MVVM route with effects (`CollectEffect` = helper collecting the effect flow with lifecycle awareness):

```kotlin
@Composable
fun CreateItemRoute(
    viewModel: CreateItemViewModel = koinViewModel(),
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            CreateItemEffect.NavigateBack -> onNavigateBack()
            is CreateItemEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.text)
        }
    }
    CreateItemScreen(
        state = state,
        onTitleChange = viewModel::onTitleChanged,
        onAmountChange = viewModel::onAmountChanged,
        onSaveClick = viewModel::save,
    )
}
```

- Navigation boundary (Nav 3 + edge-to-edge): routes are `@Serializable` types (`@Serializable data object Home`, `@Serializable data class Detail(val id: String)`, `NavKey`), not string paths; pass **IDs** in nav args — fetch models in destination ViewModel; `entry.toRoute<Detail>()` to read; `enableEdgeToEdge()` in Activity.
- Screen state example rendering (exhaustive `when`): `when (uiState) { is AuthUiState.Loading -> LoadingScreen(); is AuthUiState.LoginForm -> LoginFormScreen(uiState); is AuthUiState.Success -> SuccessScreen(uiState) }` — UI only renders; transitions live in the ViewModel.

### Effects and One-Shot Events

- **Effect** = one-off UI command that doesn't belong in state: navigate, show snackbar, trigger haptic, copy/share, open browser, analytics.
- **Why effects are not state:** modeling "show snackbar" as a boolean in state requires "consume" logic to flip it back — a classic source of bugs (event replay on config change, race between read and reset, consumer must remember to reset). Effects fire once and are gone.
- Do NOT create an effect for plain synchronous state changes. Model effects separately when the action leaves the ViewModel's state-management scope: network, persistence, delay/debounce, navigation, snackbar, haptics, share, analytics.

```kotlin
// BAD — event replays on config change, race between read and reset
data class UiState(val showSnackbar: Boolean = false)
LaunchedEffect(state.showSnackbar) {
    if (state.showSnackbar) {
        snackbarHostState.showSnackbar("Saved")
        viewModel.onEvent(DismissSnackbar)
    }
}

// GOOD — Channel delivers exactly once, survives config change
sealed interface Effect { data class ShowSnackbar(val msg: String) : Effect }
CollectEffect(viewModel.effects) { effect ->
    when (effect) {
        is Effect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.msg)
    }
}
```

- **Default delivery: `Channel<Effect>(Channel.BUFFERED)` + `receiveAsFlow()`** — buffers for reliable delivery, single consumer, no replay. `SharedFlow(replay = 0)` is acceptable for truly fire-and-forget signals; preserve an existing `SharedFlow` effect mechanism when it keeps the project consistent.
- **Why Channel over SharedFlow for ViewModel effects:** Channel guarantees delivery exactly once. SharedFlow with `replay = 0` drops events if no collector is active (e.g., during config change). Channel is delivered to exactly ONE collector and never dropped (unless full with DROP strategy); use for navigation commands, snackbar, haptics.
- Strict-SharedFlow convention variant (android-lead): `MutableSharedFlow<ProfileEvent>(replay = 0)` — `replay = 0` is non-negotiable (prevents re-fire on recomposition/lifecycle restart); collect with `viewModel.events.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { ... }` inside `LaunchedEffect(Unit)`.
- SharedFlow for broadcast (multiple subscribers, analytics): `MutableSharedFlow<AnalyticsEvent>(replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)`.
- KMP/CMP convention: collect one-shot effects via an `OnEvents(flow) { ... }` helper — never `LaunchedEffect` for navigation/effects.
- From named functions (MVVM): `_effect.trySend(CreateItemEffect.NavigateBack)` directly inside `fun onBackClick()`, `fun save()`.

### UseCase Rules

| Use UseCase | Skip UseCase |
|-------------|--------------|
| Logic shared by 2+ ViewModels | Single-line repo delegate |
| Multi-repo orchestration | One ViewModel, one repo |
| Needs isolated unit tests | CRUD with no extra rules |
| Complex business logic that shouldn't be in ViewModels; multi-step, policy-heavy, reusable, test-worthy | Simple pass-through to repository — ViewModel calls repo directly |

- **Rule:** if a UseCase body is a single line delegating to one repo, delete it. A class that only forwards to a repository with no extra policy, validation, or reuse is noise/ceremony.

```kotlin
// BAD — UseCase that's just a proxy
class GetAllAccountsUseCase(private val repo: AuthRepository) {
    operator fun invoke(): Flow<List<AuthAccount>> = repo.getAllAccounts()
}
// Just call the repo directly in ViewModel
class AuthViewModel(private val repo: AuthRepository) : ViewModel() {
    val accounts = repo.getAllAccounts()
}

// GOOD — valuable: combines multiple repositories
class GetUserProfileWithStatsUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val activityRepository: ActivityRepository,
    private val achievementRepository: AchievementRepository
) {
    operator fun invoke(userId: String): Flow<UserProfileWithStats> = combine(
        userRepository.observeUser(userId),
        activityRepository.observeActivityCount(userId),
        achievementRepository.observeAchievements(userId)
    ) { user, activityCount, achievements ->
        UserProfileWithStats(user = user, totalActivities = activityCount,
            achievements = achievements,
            completionRate = calculateCompletionRate(activityCount, achievements))
    }
}

// GOOD — valuable: real business logic
class GenerateTotpCodeUseCase(
    private val accountRepo: AuthRepository,
    private val totpGenerator: TOTPGenerator
) {
    suspend operator fun invoke(accountId: String): Result<String> {
        val account = accountRepo.getAccountById(accountId)
            ?: return Result.failure(AccountNotFound(accountId))
        return Result.success(totpGenerator.generate(account.secretKey))
    }
}
```

- Use `suspend operator fun invoke(...)` as the call convention. Keep use cases stateless.
- Base abstractions (project convention): `interface UseCase<in P, out T> { suspend operator fun invoke(params: P): Try<T> }` and `interface FlowUseCase<in P, out T> { operator fun invoke(params: P): Flow<T> }`; parameter data classes on the UseCase (`data class Params(...)`).
- Inject `Clock` (e.g. `Clock.System` default) so time-dependent use cases/domain services are deterministic in tests; pass "today" as a parameter to keep functions pure.
- For complex logic involving multiple models/repositories, a domain service is an alternative to a UseCase (`class SpacedRepetitionService(private val clock: Clock = Clock.System)`).
- Repository must not call another repository — cross-repository orchestration belongs in a UseCase.
- A UseCase that only delegates with no logic → inline it into the ViewModel. Add a UseCase only when it encodes real business logic (filtering, mapping, combining sources).

### Repository, DataSource, and Data-Layer Boundaries

- Repository pattern = single public API (facade) for data access; hides local/remote/cache complexity. Interface in `core/domain`, implementation in `core/data` (`internal class AuthRepositoryImpl @Inject constructor(...)`), bound via DI.
- Data sources: Local = Room DAO (persistent source of truth); Remote = Retrofit/Ktor API; Preferences = Proto/typed DataStore. Data source interfaces live in **data**, not domain (they use data-layer types `WordEntity`, `WordDto` — never place them in domain).
- Model mapping: separate Entity (database), DTO (network), Domain models. Annotate repository interfaces `@Stable`; domain models `@Immutable` (deeply immutable, all `val`); `@Stable` for mutable types with observable changes.

```kotlin
// domain/
data class AuthAccount(val id: String, val serviceName: String, val secretKey: String)
// data/
@Entity(tableName = "accounts")
data class AuthAccountEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "service_name") val serviceName: String,
    @ColumnInfo(name = "secret_key") val secretKey: String
)
fun AuthAccountEntity.toDomain() = AuthAccount(id, serviceName, secretKey)
fun AuthAccount.toEntity() = AuthAccountEntity(id, serviceName, secretKey)
```

- Never let Room annotations leak into domain (`@Entity` on a domain model is banned); map at the repository boundary.
- **Return-type contracts:** suspend repository op → `Result<T>` (or project `Try<T>`) — never throw outward; stream op → `Flow<T>` — never `Flow<Try<T>>`; no framework types in domain signatures (`Response<T>`, `Entity`, `Cursor`). Data sources throw (framework exceptions bubble up naturally); repository wraps (`tryOf { }` / `try/catch`) and never throws outward; use case passes through/transforms; ViewModel `.fold`/`.onSuccess`/`.onFailure` or `.reduce(onSuccess, onFailure)` → `updateState` / `emitEffect`; screen renders error state or shows snackbar. No exceptions escape the repository layer unhandled.
- Repository error mapping example: catch `HttpException` → map `e.code()` (401 → `DomainError.Unauthorized` / `AuthError.InvalidCredentials`, 404 → `NotFound`, else `Network`/`ServerError`); catch `IOException` → `NetworkError(-1, "No connection")`; optionally report to a `CrashReporter` abstraction with context map.
- Domain-specific error types: `sealed class AuthError(message: String, cause: Throwable? = null) : Exception(message, cause) { class NetworkError; class InvalidCredentials; class UserAlreadyExists; class ServerError; class UnknownError }` — or sealed interface `DomainError` for non-exception errors. Validation errors as sealed types (`sealed class ValidationError : Exception() { data object InvalidEmail; data object PasswordTooShort; ... }`) returning `Result.failure(...)`.
- Use mappers only when transformations add business logic (normalization, formatting, defaults) — not for simple 1:1 field mappings. Mappers are always extension functions on the **source** type (`Entity.toDomain()`, `Domain.toEntity()`, `Dto.toEntity()`), contain no business logic (pure structural transformation), and live in `data`.
- Network layer: all Retrofit endpoint functions `suspend`; use `Response<T>` only when you need status codes/error bodies, else the body type directly. Network DTOs use **nullable** properties for fields the server may omit/null; map to non-null domain types after deciding defaults; keep `Json { ignoreUnknownKeys = true }` (plus `coerceInputValues = true`, `isLenient = true`) so new server fields don't crash; avoid fake non-nulls (`String = ""`) for missing keys unless a strict documented contract; use `@SerialName("json_name")` (Gson: `@SerializedName`) when wire names differ.
- Inject auth tokens via an OkHttp `Interceptor` (`AuthInterceptor` adding `.header("Authorization", "Bearer $token")`) instead of `@Header` params on every endpoint.
- DataStore critical rules: **never** create more than one `DataStore` instance for a given file in the same process (throws `IllegalStateException`) — always provide as `@Singleton` via DI; the generic type `T` in `DataStore<T>` must be immutable; **never mix access modes** for the same file (if any path uses `MultiProcessDataStoreFactory`, ALL access must); read with `dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }`; write with `dataStore.edit { }` (migrating off `SharedPreferences.edit().apply()` — races/ANR); `PreferenceDataStoreFactory.create(migrations = listOf(SharedPreferencesMigration(context, "legacy_prefs")), ...)`; corruption handling `ReplaceFileCorruptionHandler { UserSettings() }`, serializer rethrows `CorruptionException`. Prefer Room when you need relational data, queries, partial updates, large collections (~100+ entries threshold); DataStore only for small preference blobs / typed settings / feature flags; files for blobs.
- Sync workers (`@HiltWorker CoroutineWorker`) map failures to `Result.retry()` (NetworkError, or ServerError with `runAttemptCount < 3`) vs `Result.failure()`.
- Room (targets **Room 3**, `androidx.room3`, DB built with `.setDriver(BundledSQLiteDriver)`, Flow-based invalidation via `InvalidationTracker.createFlow` — removed `InvalidationTracker.Observer` APIs banned):
  - `@Upsert` returns `-1` on updates — use `@Insert(onConflict = OnConflictStrategy.REPLACE)` if you need the inserted row ID; conversely prefer `@Upsert` when you need update-in-place because `REPLACE` = delete+insert and can trigger foreign-key `ON DELETE CASCADE`.
  - Never `Flow<List<T>>` for large tables (loads whole table per change) — use Paging 3. Always select specific columns (avoid `SELECT *`). Use `@Transaction` for multiple operations; annotate `@Relation` multi-query DAO methods `@Transaction` (single snapshot). Index what you filter/sort/join (`@Entity(indices = [...])`). Avoid N+1 (one JOIN/`IN (:ids)`/projection query, not per-row queries in a loop). Never `allowMainThreadQueries()` in production. One `RoomDatabase` instance per database name (`@Singleton` DI). Store file paths/URIs for large binary payloads, not BLOBs. FTS4 (`@Fts4(contentEntity = ...)`, `MATCH :query`) instead of `LIKE '%query%'`.

### Modules & Boundaries (Multi-Module Structure)

- Benefits: build speed (incremental recompile), team scale/parallel development, compiler-enforced boundaries, reusability, encapsulation, testability, feature independence, centralized navigation coordination.
- Module layout (large apps):

```
app/                          # wires everything: MainActivity, App (@HiltAndroidApp), NavHost, DI graph entry
core/
  core-ui/                    # Design system, shared composables, theme
  core-domain/  (domain/)     # Shared domain models, repository interfaces, base UseCase — pure Kotlin
  core-data/    (data/)       # Repository impls, NetworkClient, BaseRepository
  core-network/               # Retrofit/Ktor, API models
  core-database/              # Room DAOs, entities, migrations
  core-datastore/             # preferences
  core-common/                # shared utilities, extensions, Try<T>, dispatchers
  core-testing/               # TestDispatcherRule, FakeRepository, rules — testImplementation only
feature/
  feature-<name>/
    presentation/             # Screen, Route, viewmodel/, components/
    domain/                   # models, repository interfaces, UseCases
    data/                     # Room, Ktor/Retrofit, mappers
    navigation/               # Destination (NavKey), Navigator interface, Graph
    di/                       # AuthModule.kt (Hilt) / StudyModule.kt (Koin)
build-logic/convention/       # convention plugins — no duplicated Gradle boilerplate
```

- **Dependency rules (STRICT):**
  - `feature-*` depends on `core-*`/`:domain` only — **never on another feature module.**
  - `core-domain`/`:domain` depends on nothing (pure Kotlin, no Android).
  - `core-data` depends on `core-domain` (implements interfaces); `core:network`/`core:database` → domain types.
  - `app` depends on all features + all cores (wiring only: navigation coordination, DI).
  - No circular dependencies between any modules. `core:*` → `feature:*`/`app` forbidden (core can't depend on consumers).
  - api/impl split (when used): `feature:X:impl → feature:X:api` (any feature's api OK), `feature:*:api` must be leaf deps (api → only `core:designsystem` route types); `app → feature:*:impl, feature:*:api, core:*`; `core:data → core:network, core:database, core:datastore`.
  - `feature:impl → another feature:impl` forbidden (circular risk). Domain → data direction forbidden (domain declares, data implements).
  - KMP: `:core:design-system` → Compose only (no `:domain`, no `:core:network`/`:database`); `:core:testing` → commonTest only, never on production classpath; `:platforms` contains only `expect`/`actual`; each `:feature:X` exposes a `NavGraphBuilder` extension + a DI module, both wired in `:app`.
- Forbidden imports table:

| Module | Must NOT import |
|---|---|
| `:domain` | Room, Ktor, Koin, Hilt, Compose, Android SDK, any framework |
| `:feature:*` | Any other `:feature:*` module |
| `:core:design-system` | `:domain`, `:data`, `:feature:*` |
| `:core:common` | `:feature:*`, `:app` |
| `:core:testing` | Any production module on production classpath |

- **Feature-first organization is the default:** organize by feature first, then by internal layers only when needed. Giant top-level `presentation/`, `domain/`, `data/` package islands (all features nested inside each layer) become a horizontal maze — bad.
- Enforce boundaries: Dropbox Dependency Guard on both `releaseRuntimeClasspath` and `debugRuntimeClasspath` (`dependencyGuardBaseline` after intentional changes); Detekt `ForbiddenImport` rules (`androidx.room.*`, `io.ktor.*` with reasons); validate: `./gradlew :feature:study:dependencies | grep ':feature:'` (only itself), `./gradlew :domain:dependencies | grep 'androidx'` (nothing).
- Build speed: `org.gradle.parallel=true`, `org.gradle.caching=true`, `org.gradle.configureondemand=true`, `kotlin.incremental.multiplatform=true`; a change in `:feature:study` must not invalidate `:feature:words`.
- KMP Gradle gotchas: `pluginManagement { includeBuild("build-logic") }` **must be the first block** in root `settings.gradle.kts`; inside a `Plugin<Project>` use `extensions.configure<KotlinMultiplatformExtension>` / `extensions.configure<LibraryExtension>` (bare `kotlin {}`/`android {}` not in scope); `iosMain.dependencies { }` needs `applyDefaultHierarchyTemplate()` in the convention plugin; set `namespace` per module (AGP 7.3+); replace `compileOptions sourceCompatibility` with a single `jvmToolchain(17)`; version catalog needs convention-plugin aliases with `version = "unspecified"`; `core:common` exposes `Try<T>`/coroutines via `api(...)` so consumers see the types.
- Migrating a monolith: Phase 1 extract core (`:core:common`, `:core:network`, `:core:database`) with no feature changes; Phase 2 extract domain (move models + interfaces to `:domain`, keep impls in `:app` temporarily); Phase 3 extract features one at a time starting with the most isolated; Phase 4 `:app` = only `MainActivity`, DI app, `AppNavGraph`, manifest.
- Module best practices: start simple (app + core, add features as needed); features work in isolation; navigation contracts as interfaces not direct NavController access; keep `core:domain` Android-free; consistent `feature-{name}` naming; each module has its own test suite; convention plugins for consistent config; always `feature → core:domain → core:data`.

### Inter-Feature Communication

| Need | Pattern | Why |
|---|---|---|
| React to event from another feature | Event bus (`SharedFlow`) | Fire-and-forget, many listeners |
| Navigate to another feature | Feature API contract (`:api` module) / Navigator interface | Type-safe, no impl dependency |
| Pass data back | Feature API + callback | Structured return, testable |
| Shared data stream (current user) | Shared repository in `core` | Persistent state, not one-shot |

- Anti-patterns: importing another feature's ViewModel; a global "god event bus" with 50 events; cross-feature data via `CompositionLocal`.
- Bridge pattern: features declare narrow `AuthNavigator` interfaces; the app module implements them (`AppAuthNavigator`/`AppNavigationMediator implements AuthNavigator, ProfileNavigator, SettingsNavigator`); features never know about `NavController` or each other.

### DI Wiring at the Architecture Level (Hilt; Koin equivalents noted)

- Prefer **constructor injection** `@Inject constructor(...)` on types Hilt builds. Avoid `@Inject lateinit var` on app/domain types (hidden dependencies, harder tests). Field injection only where a platform API requires it.
- **`@Binds` vs `@Provides`:** `@Binds` (abstract method in `abstract class @Module @InstallIn(...)`) maps an interface to an `@Inject`-constructable impl — use for all interface→impl bindings (`@Binds @Singleton abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository`; Koin: `single<IWordReader> { WordRepositoryImpl(get()) }`). `@Provides` only when you construct the instance (`OkHttpClient.Builder()`, `Retrofit.Builder()`, `DataStoreFactory.create`, third-party SDKs). `@Provides` for an interface bind (when `@Binds` works) is banned — extra allocation.
- Scopes must match real lifetime:

| Annotation | Lifetime | Typical use |
|---|---|---|
| `@Singleton` | Application | Retrofit, OkHttp, Room, DataStore, dispatchers, repositories |
| `@ActivityRetainedScoped` | Survives config change until activity finished | Session-like state (use sparingly) |
| `@ViewModelScoped` | Same as hosting `ViewModel` | Feature helpers (validators, calculators), use cases |
| `@ActivityScoped` / `@FragmentScoped` | Activity/Fragment instance | Rare in Compose-first apps |

- Over-scoping wastes memory; under-scoping duplicates heavy types or breaks singleton expectations. Feature-only deps in `SingletonComponent` "just in case" is an anti-pattern — use `ViewModelComponent`/`@ViewModelScoped` when only screens need the type.
- DI layering: `SingletonComponent` — app-wide singletons (networking, database, repositories); `ViewModelComponent` — use cases scoped to ViewModel lifetime; `ActivityComponent` — rare, avoid.
- Colocate Hilt modules with the feature/layer they wire (`AuthModule` in a feature, `DatabaseModule` in `core/data`); app module owns the application-graph entry points (`@HiltAndroidApp`, `@AndroidEntryPoint`).
- ViewModel DI: `@HiltViewModel` + `@Inject constructor` + `hiltViewModel()` in Compose. A ViewModel with `@Inject` but no `@HiltViewModel` means Hilt doesn't own the instance — banned. Manual `ViewModel(...)` factories everywhere bypasses the graph — use `hiltViewModel()` or Hilt-assisted factories (`@AssistedInject` + `SavedStateHandle`, see nav docs as source of truth).
- Never inject `Context`/`Activity`/`Fragment` into a ViewModel — pass IDs via `SavedStateHandle`, navigation args, repositories; `@ApplicationContext` belongs in data/repository wiring only.
- DI-managed singletons beat `object` for anything with Android dependencies: `object BadAnalytics { private lateinit var context: Context }` leaks — use `@Singleton class Logger @Inject constructor(@ApplicationContext private val context: Context)`.
- For app-wide lifetimes rely on DI scopes instead of manual singletons; delegate objects via DI, avoid manual construction.

### Coroutines & Structured Concurrency

Scopes:

| Scope | Tied to | Survives config change | Use for |
|-------|---------|----------------------|---------|
| `viewModelScope` | ViewModel lifecycle | Yes | Data loading, state updates, business logic |
| `lifecycleScope` | Activity/Fragment | No | UI-bound work (permissions, navigation side effects) |
| `rememberCoroutineScope()` | Composable | No | One-shot composable actions (scroll, animation) |

- **Never use `GlobalScope`** — it ignores structured concurrency; coroutines leak past lifecycle. Use `viewModelScope`, `lifecycleScope`, or injected custom scopes.
- Long-lived non-UI components may own a scope: `CoroutineScope(dispatcher + SupervisorJob())` with an explicit `cleanup()`/`cancel()`.
- One-shot composable action stays in the composable (`scope.launch { listState.animateScrollToItem(0) }` — UI-only, doesn't need a ViewModel).

Dispatchers:

| Dispatcher | Thread | Use for |
|-----------|--------|---------|
| `Dispatchers.Main` | Main/UI | UI updates, state emission, small logic |
| `Dispatchers.IO` | Shared IO pool (64+ threads) | Network calls, file I/O, DB (when not Room) |
| `Dispatchers.Default` | CPU cores | Heavy computation, JSON parsing, sorting large lists |
| `Dispatchers.Main.immediate` | Main, no re-dispatch | Already on Main, avoid queue hop |

- **When NOT to switch:** Room, Retrofit (with suspend), and Ktor handle dispatcher switching internally — don't wrap them in `withContext(Dispatchers.IO)`/`Dispatchers.IO` launch (double dispatch). Right: `viewModelScope.launch { val accounts = dao.getAllAccounts() }`.
- **When TO switch:** heavy CPU work — `val parsed = withContext(Dispatchers.Default) { rawData.map { parseComplexItem(it) } }` then `_state.update { it.copy(items = parsed) }`; unwrapped file I/O — `withContext(Dispatchers.IO) { File("large.json").readText() }`.
- Parallelize independent calls with `async`/`await` instead of sequential awaits:

```kotlin
viewModelScope.launch {
    val accountsDeferred = async { repo.getAccounts() }
    val settingsDeferred = async { repo.getSettings() }
    _state.update { it.copy(accounts = accountsDeferred.await(), settings = settingsDeferred.await()) }
}
```

- Cancel-and-relaunch for debounce: keep `private var searchJob: Job? = null`; `searchJob?.cancel(); searchJob = viewModelScope.launch { delay(300); ... }`. Prefer `Flow.debounce()` / `Flow.distinctUntilChanged()` over manual delay/relaunch when a flow exists.
- Isolate sibling failures with `supervisorScope { launch { syncAccounts() }; launch { syncSettings() } }` — a plain `launch` parent propagates one child's failure to siblings.
- `runBlocking { }` on main is banned (ANR) — `suspend` + proper scope. In tests use `runTest`, never `runBlocking`.
- Nested callback hell → replace with sequential `suspend` functions returning `Result`.
- Generic suspend utilities are fine (`retryWithBackoff(maxAttempts, initialDelay, maxDelay, factor, block): Result<T>` with exponential `currentDelay = (currentDelay * factor).coerceAtMost(maxDelay)`).
- Coroutines over callbacks. `Flow` over `LiveData` unless interfacing with legacy code — LiveData in new Compose code is banned; always use `Flow`/`StateFlow` for consistency.

Flow selection:

| Type | Properties | Use for |
|------|-----------|---------|
| `StateFlow` | always has `.value`; conflates (only latest); replays latest to new collectors | Any UI state |
| `SharedFlow` | no current value; multiple subscribers; configurable replay/buffer | Analytics, logging, broadcast events |
| `Channel` | delivered to exactly ONE collector; never dropped unless buffer-full DROP | Navigation commands, snackbar, haptics — one-shot effects |

- `collectAsState()` on Android screens is banned — use `collectAsStateWithLifecycle()` (stops collecting in onStop, resumes in onStart; a background `collectAsState` collector drains battery/CPU for invisible UI). Custom min state: `collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)`. Dependency: `androidx.lifecycle:lifecycle-runtime-compose`. **CMP exception:** in `commonMain` it's unavailable — use `collectAsState()` (or the project `collectState()` wrapper) there; on Android targets always the lifecycle-aware version. `collect {}` without lifecycle awareness → use `collectAsStateWithLifecycle` or `repeatOnLifecycle`.
- Convert cold flows (Room queries) to hot with `stateIn` — raw `val accounts: Flow<List<T>>` re-runs the query per collector:

```kotlin
val accounts: StateFlow<List<AuthAccount>> = repo.getAllAccounts()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )
```

| SharingStarted | Behavior | Use when |
|----------|----------|----------|
| `WhileSubscribed(5_000)` | Active while subscribed + 5s buffer | Default for most UI flows |
| `Eagerly` | Active immediately, never stops | App-wide state (auth, theme) |
| `Lazily` | Active on first subscriber, never stops | Data that should load once |

- Why 5000ms: survives config-change rotation (~2-3s) without restarting upstream. Battle-tested default.
- Collecting a Flow in `init {}` with manual `launch` → prefer `stateIn` to convert to StateFlow. SharedFlow with `replay = 1` for UI state → wrong, that's just a StateFlow. `flowOn(Dispatchers.IO)` on a Room Flow → double-hop, Room already dispatches.
- Coroutines anti-patterns list: GlobalScope; naked `_state.value =` for state races; blocking dispatchers on main; unbound scopes; `stateIn` misuse.

### Error Handling (Result / Try / sealed errors / cancellation)

- Model domain errors as sealed types, NOT stringly-typed exceptions: `sealed interface DomainError { data class Network(val code: Int, val message: String); data object Unauthorized; data object NotFound; data class Unknown(val cause: Throwable) }`.
- Repositories return `Result<T>` (stdlib) or project `Try<T>` / `Either<AppError, T>`; ViewModels consume with `.onSuccess { } / .onFailure { }` or `.fold(onSuccess = ..., onFailure = ...)` or `.reduce(onSuccess, onFailure)`.
- Prefer `Result<T>` over throwing exceptions for expected failures; map failures to user-facing text via exhaustive `when` in the ViewModel.
- Keep error UI-string mapping at the boundary: store canonical errors/`@StringRes` in state, format with resources at the Compose boundary. Don't bake display strings in the ViewModel (locale rigidity).
- **Critical cancellation rule:** `runCatching` and `try/catch(Throwable)` catch `CancellationException` — silently breaking structured concurrency. Always rethrow it:

```kotlin
suspend fun <T> safeRunCatching(block: suspend () -> T): Result<T> =
    try { Result.success(block()) }
    catch (e: CancellationException) { throw e }   // never swallow this
    catch (e: Throwable) { Result.failure(e) }
```

- KMP-project stricter convention: avoid `runCatching {}` entirely — use typed `Try<T>`/`Either`; `tryOf { block() }` wraps exceptions as `Try.Failure(e)`; `Try<T>` exposes `getOrThrow()`, `map { }`, `reduce(onSuccess, onFailure)`; `.fold {}`, `.getOrElse {}` — never unwrap with `!!`. No try-catch for control flow — use `Flow.catch {}` or `Try<T>`; empty `catch {}` banned — always handle or rethrow.

### Kotlin Idioms (Language Foundations)

- Kotlin idioms over Java patterns. Basics (data classes, null safety, scope functions) assumed; enforce the following.
- **Immutability:** prefer `val` over `var`; prefer immutable collections; mutate by replacing, not in place (`val updated = words + newWord`); `data class User(val name: String)` + `copy()` — never `var` in data classes. `ImmutableList<T>`/`PersistentList<T>` over `List<T>` where structural sharing helps (Compose params, VM state); mutable collections in `@Immutable` data classes are a lie that breaks Compose change detection.
- **Read-only collection APIs:** expose `List`/`Set`/`Map`, keep mutables private; return copies (`sessions.toMap()`, not the reference). Model collection changes as pure transformations (`fun reduceSessions(current: List<Session>, event: SessionEvent): List<Session>`).
- **Sealed interfaces preferred over sealed classes** for closed type hierarchies; exhaustive `when`, no `else`, compiler enforces all cases. Use `sealed` when variants are closed and owned by you; use open `interface` when third parties extend (channels, formatters, strategies). Prefer polymorphism/sealed types over `instanceof`/`is Type` checks on open types.
- **`when` over long if-else chains** (subject and subjectless forms).
- **Data class + `copy()`** for immutable value objects and state updates (prototype pattern).
- **`@JvmInline value class`** to eliminate primitive obsession: `value class UserId(val value: Int)`, `Email`, `AuthToken`, `Temperature` — compile-time ID mixing prevention, zero runtime overhead. Limits: single property; must be public; some reflection limits. IDs are value classes, not raw `Int`/`String`.
- **typealias** for readability of complex generics/callback/domain terms (`typealias AuthCallback = (Result<AuthToken>) -> Unit`, `typealias UserListFlow = Flow<List<User>>`); never for single-use/obscuring types (`typealias S = String`, `IntList` add nothing). Prefer value classes over aliases for domain primitives.
- **Extension functions** for domain behavior without inheritance (`fun User.isActive(): Boolean`, `fun List<User>.filterActive()`, `fun Instant.formatRelativeTime()`, `fun <T> Flow<T>.throttle(period: Duration)`); keep in same module as type or `core:common`; prefer extensions over utility classes; names read naturally at call site (`user.displayName()` not `UserUtils.getDisplayName(user)`).
- **Companion objects** for type-related constants (`const val MAX_LOGIN_ATTEMPTS`, `val SESSION_TIMEOUT = 30.minutes`) and validating factory methods (`User.create(...): Result<User>` with private constructor). Top-level functions for pure utilities.
- **Scope functions:**

| Function | Object ref | Returns | Use for |
|---|---|---|---|
| `let` | `it` | lambda result | Null-safe transform, scoped variable |
| `run` | `this` | lambda result | Configuration + compute result |
| `with` | `this` | lambda result | Group operations on a non-null receiver |
| `apply` | `this` | receiver | Builder-style initialisation |
| `also` | `it` | receiver | Side effects (logging, validation) |

- **Destructuring:** data classes, `for ((id, name, _) in users)`, map entries, Pair returns (`val (min, max) = getMinMax(list)`). Limits: only first 5 components by default, position-based, don't overuse.
- **Inline functions:** `inline` higher-order functions eliminate lambda allocation (timing wrappers, DSL builders like `buildUser { }`). `reified` type params (only with `inline`) for runtime type info: `inline fun <reified T> Retrofit.create()`, `inline fun <reified T> Json.decodeFromString(...)`, `inline fun <reified T : Activity> Context.startActivity()`, `getOrThrow<T>` on `SavedStateHandle`. Don't overuse reified — it grows code size at call sites.

| Modifier | When to Use | Effect |
|----------|-------------|--------|
| (default) | Lambda used directly at call site | Inlined, non-local `return` allowed |
| `noinline` | Lambda stored, passed on, or returned | Not inlined, creates object |
| `crossinline` | Lambda runs in different execution context (`launch`, `withContext`) | Inlined, but non-local `return` forbidden |

- **Named arguments** for multiple same-type params, boolean params, defaults, builder-like calls.
- **Generic bounds:** `fun <T : User> processUsers(users: List<T>)`; generic base repository contracts where real reuse exists.
- **Sequences** for lazy evaluation: `users.asSequence().filter { }.map { }.sortedBy { }.take(10).toList()` avoids intermediate lists. Use for large collections (1000+), multi-step chains, infinite streams (`sequence { yield(...) }`), file/cursor iteration (`file.useLines { }`). Don't use for small collections (<100), single ops, or when random access/size needed.
- **Avoid memory churn** (GC pauses → jank): reuse `StringBuilder` in loops instead of string concatenation per iteration; cache reused objects.
- **Null safety:** design APIs to return non-null; represent absence with sealed types or `Try<T>` (`sealed interface FindResult { data class Found; data object NotFound }`) instead of nullable returns; `?.` and `?:` over `!!`; `!!` only with a comment proving the invariant (or in tests); `requireNotNull(value) { "reason" }` at system boundaries.
- **Constants:** no magic numbers/strings in business logic — `object ReviewQuality { const val AGAIN = 0; ... }`, `object Timeouts { val networkRequest = 30.seconds }`. Use `kotlin.time.Duration` (`3.minutes`, `1.seconds`) and `kotlinx.datetime`/`Clock.System` for time.
- **Comments** explain **why**, not **what**; delete commented-out code (version control remembers); KDoc on public API in shared modules.
- **Functions:** one responsibility — if you need "and" to describe it, split (`processPayment` composes `validate`/`charge`/`logPayment`/`notifyUser`); functions under ~20 lines; expression bodies for single-expression functions; prefer <3 params, more → data class params (`CreateWordParams(...)`); named args for multi-param calls; default parameters over overloads.
- **Custom View interop:** in `View` subclasses register/remove lifecycle observers **in pairs** (`onAttachedToWindow`/`onDetachedFromWindow`, `findViewTreeLifecycleOwner()?.lifecycle?.addObserver(this)` / `removeObserver`); prefer `LifecycleResumeEffect`/`DisposableEffect` in pure composables.

### Kotlin Delegation (Composition over Inheritance)

- Prefer composition and delegation (`by`) over inheritance; keep components testable, avoid framework-heavy base classes; add complexity only when needed.
- **When to use delegation:** shared behavior across multiple ViewModels/classes; cross-cutting concerns (logging, validation, analytics, feature flags) split into focused interfaces; decorator layering; DI-injected swappable behavior.
- **When NOT:** single-use logic; delegation adding indirection without value; framework-required inheritance (`Activity`, `Application`, `ViewModel` itself); extremely perf-critical paths (rare; measure first — delegation overhead is negligible, don't over-optimize).
- Class delegation — ViewModel composing injected interfaces:

```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    logger: Logger,
    validator: FormValidator,
    analytics: Analytics
) : ViewModel(),
    Logger by logger,
    FormValidator by validator,
    Analytics by analytics {
    fun onLoginClicked(email: String) { /* validateEmail(email), log(...), trackEvent(...) — delegated */ }
}
```

vs the ❌ alternative: `abstract class BaseViewModel : ViewModel() { abstract fun log; abstract fun validateEmail; abstract fun trackEvent }` with a tightly-coupled subclass.
- **No `private` on constructor parameters used only for delegation** (`crashReporter: CrashReporter // No private - delegated only`) — makes delegation explicit and prevents bypassing.
- When overriding a delegated method, call `super.method()` to forward to the delegate (decorator pattern: `class PrivacyAwareCrashReporter(crashReporter: CrashReporter) : CrashReporter by crashReporter { override fun recordException(t, ctx) { super.recordException(t, scrub(ctx)) } }`).
- Keep interfaces focused: 2–5 methods; delegate interfaces, not concrete classes; return sealed result types (`ValidationResult.Valid` / `ValidationResult.Invalid(error)`) — not nullable strings; prefer DI for delegates; document delegation intent.
- Fakes over mocks for delegated interfaces in tests (`FakeLogger`, `FakeAnalytics`, `FakeFormValidator`) — construct VM directly, assert recorded events (`assertThat(fakeAnalytics.events).contains("login_attempt" to mapOf("method" to "email"))`); test overridden delegation separately.
- **Auditing base classes for dead code** (when migrating inheritance → delegation), common dead code: 1) methods never overridden/called by subclasses; 2) `@Inject` fields never accessed; 3) Channels/Flows never collected by UI (search for `.collect` / `collectAsStateWithLifecycle()`; if none, delete); 4) methods only used by removed code (delete transitively). Process: find all subclasses → check each method's usage, field access, flow collection → delete aggressively. Why: base classes centralize dead code invisible to static analysis; unused `@Inject` fields add deps to every subclass; dead code survives because "it might be used somewhere."

### Design Patterns (Android Mapping)

- Principles: composition/delegation over inheritance; patterns local to their layer (UI vs Domain vs Data); no framework-heavy base classes; DI scopes for app-wide lifetimes; start simple; Kotlin-first (sealed classes, data classes, delegation, coroutines). Use patterns to solve real problems, not to add complexity.
- **MVVM** = base architecture: ViewModel holds `StateFlow<UiState>`, composables observe and render.
- **Repository** = single source of truth for data access (also a Facade).
- **Singleton** → `@Singleton` via DI, not `object` holding Context.
- **Factory / Abstract Factory** → `ViewModelProvider.Factory`, `WorkManager` factories, Retrofit creation; swap provider families (Crashlytics vs Sentry) by build variant via `@Provides`.
- **Builder** → `OkHttpClient.Builder`, `Retrofit.Builder`, custom builders with `apply {}` chains; immutable outputs; keep config in DI modules.
- **Prototype** → `data class.copy()` for immutable UI-model updates.
- **Adapter** → DTO↔Domain↔Entity mappers in `core/data`.
- **Bridge** → `Navigator` interfaces in features with app-level implementations.
- **Composite** → sealed `NavigationItem` tree (`NavScreen` leaf / `NavGroup` node) rendered recursively.
- **Decorator** → OkHttp interceptors, Compose `Modifier` chains, stacked `CrashReporter by delegate` wrappers provided through `@Provides`.
- **Proxy** → `by lazy` init for analytics/crash reporters; caching data source wrappers (`cache.getOrPut`).
- **Observer** → `Flow`/`StateFlow` everywhere; repository exposes `Flow`, VM `stateIn` → `StateFlow`, UI collects.
- **Strategy** → interchangeable algorithms (auth providers, caching strategies) injected via DI qualifiers (`@EmailPassword`, `@Google`); don't branch on build flavors in business logic.
- **Chain of Responsibility** → OkHttp interceptor pipeline (auth → retry → logging), chain configured in DI.
- **Command** → sealed `AuthAction` types + `onAction()` processor (see MVI/MVVM).
- **Iterator** → keep iteration in data/paging layers (prefer `Pager(...).flow` over manual pagination iterators).
- **Mediator** → app-level navigator implementing per-feature Navigator interfaces.
- **Memento** → `SavedStateHandle` snapshots of form drafts (`savedStateHandle[KEY] = @Serializable Memento`; restore in `init`); keep snapshots minimal and serializable. (The example uses direct `_x.value =` writes — combine with the atomic `_x.update { }` rule.)
- **State** → `@Immutable sealed interface AuthUiState` + ViewModel state machine + `when`-driven UI.
- **Template Method** → avoid inheritance-based base use cases; prefer composition (inject `CredentialsValidator`, `Authenticator`, `TokenStorage` collaborators).
- **Visitor** → only if it improves clarity (e.g. analytics over sealed UiState) — otherwise overengineering.
- **Result type** → repositories/use cases return `Result<T>`/`Try<T>`; custom sealed `Result<out T> { Success<T>; Error }` when domain-typed errors are needed (stdlib `Result<T>` otherwise fine).

### Clean Code & Anti-Overengineering

- **Disciplined MVI:** one feature ViewModel, one clear state model, one `onEvent()`, small number of effects, explicit UI contracts, shared business logic, direct feature names.
- **Bloated MVI:** too many tiny sealed types; every action wrapped twice; separate mapper/presenter/handler for trivial screens; verbose generic layers with little value.
- **Overengineered MVI:** generic frameworks and base abstractions replace feature code; trivial repository calls get use-case wrappers; mandatory 4-type MVI with pure reducers before screens need them.

| Area | Good architecture | Overengineering |
|---|---|---|
| ViewModel | `ProductViewModel` with `onEvent()` | `BaseMviViewModel<State, Intent, Effect, Result>` with `handleEvent()` + `reduce()` |
| Events | one feature sealed interface | multi-layer intent taxonomy |
| State updates | inline `updateState { copy(...) }` in `onEvent()` | separate `Result` type + pure `reduce()` for simple screens |
| Effects | only for impure one-shot actions | effects for trivial synchronous transitions |
| UI | route + dumb screen + meaningful leaves | every row has its own ViewModel/presenter |
| Use cases | used for real domain logic | one wrapper per repository call |
| Modules | feature-first | giant "domain/data/presentation" package islands |
| Platform abstractions | introduced when needed | abstracted preemptively everywhere |
| Navigation | semantic effect + route binding | global command bus + abstract navigator hierarchy |
| Naming | `ProductState`, `ProductEvent` | `FeatureContract.State`, `FeatureContract.Action` |

- Event sealed class per feature is almost always enough. Event hierarchies become excessive when you see `UserEvent`, `UiEvent`, `SystemEvent`, `InternalEvent`, `ViewEvent`, `ActionEvent` — three wrappers before feature logic — or child components that need to know root feature events.
- Extract reusable UI only when there's real reuse, a stable API, and a meaningful visual/behavioral boundary (`MoneyField`, `ResultCard`, `ValidationStatus`/`ValidationMessage`, `SettingsToggleRow`). Do not extract: one-line wrappers around `Text`/`Spacer`, modifier-forwarding wrappers, components "reusable" in theory but used once, components whose props are harder to understand than inline code.
- Don't abstract platform capabilities preemptively — share business logic first, abstract real platform capabilities only when pain appears.
- BAD (forced 4-type MVI on a trivial picker): `MviViewModel<CurrencyEvent, CurrencyResult, CurrencyState, CurrencyEffect>` with 1:1 `dispatch(CurrencyResult...))` + `reduce(r, s)`. GOOD (3-type, same screen):

```kotlin
sealed interface CurrencyEvent { data class OnSelected(val currency: Currency) : CurrencyEvent }
data class CurrencyState(val selected: Currency? = null)
sealed interface CurrencyEffect { data class NavigateBack(val currency: Currency) : CurrencyEffect }
class CurrencyViewModel : ViewModel() {
    private val _state = MutableStateFlow(CurrencyState())
    val state = _state.asStateFlow()
    private val _effect = Channel<CurrencyEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()
    fun onEvent(event: CurrencyEvent) = when (event) {
        is CurrencyEvent.OnSelected -> {
            _state.update { it.copy(selected = event.currency) }
            _effect.trySend(CurrencyEffect.NavigateBack(event.currency))
        }
    }
}
```

- BAD MVI example to avoid: business logic (calculation/validation) living in the composable with `rememberSaveable` field vars — logic belongs in the state holder.

### Naming & Import Hygiene

| Concept | Recommended | Avoid |
|---|---|---|
| Event | `ProductEvent` | `ProductActionEventIntent` |
| State | `ProductState` | `ProductViewState`, `Contract.State` |
| Effect | `ProductEffect` | `ProductCommandEffectSideEffect`, `SingleLiveEvent` |
| Contract file | `ProductContract.kt` | separate files per type for small screens |
| ViewModel | `ProductViewModel` | `BaseProductViewModel` |
| Route | `ProductRoute` | `ProductContainerFragmentLikeThing` |
| Screen | `ProductScreen` | `ProductView` |
| Leaf component | `ResultCard`, `ProductForm` | `ProductFormWidgetComponentView` |

- Class/Interface/Object/Enum `PascalCase`; function/property/variable `camelCase`; `const val`/top-level immutable `SCREAMING_SNAKE_CASE`; packages lowercase without underscores; `@Composable` functions `PascalCase` (they are types); enum entries `SCREAMING_SNAKE_CASE`.
- Names reveal intent (`dueWords` not `list2`, `isExpired` not `flag`); no abbreviations (`userRepository` not `usrRepo`); booleans `is*`/`has*`/`can*`; functions are verbs; avoid redundant context inside a type (`findById()` in `UserRepository`, not `findUserById()`).
- Test names communicate intent (JUnit5 backticks): `given expired card, when charging, then returns Failure` / `isDue returns false when reviewed within the last hour` / `should_X_when_Y`.
- **Import hygiene (strict):** never write fully qualified package paths inline — always import at top; use `import ... as ...` for name clashes across layers, aliasing with the layer (`Db`, `Domain`, `Ui`, `Api`, `Dto`):

```kotlin
// BAD
val unit = com.example.app.data.db.entity.enums.WeightUnit.entries.find { it.name == rawValue }
// GOOD
import com.example.app.data.db.entity.enums.WeightUnit
val unit = WeightUnit.entries.find { it.name == rawValue }
// GOOD — clash resolution
import com.example.app.data.db.entity.enums.WeightUnit as DbWeightUnit
import com.example.app.domain.model.WeightUnit
```

### SOLID, YAGNI, DRY, KISS

- **SRP** — one reason to change. ❌ ViewModel touching `FirebaseAnalytics.getInstance(context)`, `navController.navigate(...)`, and `prefs.edit()` in one function (plus `context` in a VM = DIP violation). ✅ ViewModel manages state and delegates (use case + `IAnalyticsService` + `emitEffect`). Layer SRP: one class must not talk to both Room and Ktor — `WordRemoteDataSource` + `WordLocalDataSource` each do one job; `WordRepositoryImpl` orchestrates.
- **OCP** — ❌ `when (type) { "push" -> ...; "email" -> ... }` requiring edits per new type; ✅ interface + implementations + `List<INotificationChannel>` injected. Sealed for closed sets (compile-time exhaustiveness), open interfaces for extensible sets.
- **LSP** — implementations must honor the contract: same pre/postconditions, no extra throws. ❌ `syncWithRemote()` throwing `IllegalStateException` when the interface returns `Try<Unit>`; ❌ silent no-op overrides pretending success (`override suspend fun save(word) = Try.Success(word)` that saves nothing — data silently lost); redesign the contract (use the narrower `IWordReader`). ❌ Composable ignoring a parameter (`Button(onClick = {})` discarding the passed `onClick`) — model disabled state with `enabled`, never a discarded callback.
- **ISP** — split fat interfaces by role: `IWordReader`/`IWordWriter`/`IWordSync`/`IWordImportExport`; use cases depend only on what they call; same for data sources; split monolithic ViewModel state interfaces by UI concern (`INotificationSettings`, `IThemeSettings` consumed section-by-section).
- **DIP** — ViewModel/UseCase import only interfaces; `import androidx.room.*`/`io.ktor.*` absent from `:domain`; bind interface→impl in DI; concretes hidden behind abstractions.
- SOLID → testability: DIP makes fakes possible; ISP keeps fakes small (2 methods, not 9 stubs throwing `UnsupportedOperationException`); SRP gives tests one reason to fail.
- SOLID checklist: SRP — single-noun class name? OCP — add behavior without touching existing classes? LSP — every impl honors the full contract? ISP — each client imports only what it uses? DIP — domain/VM/use-case import only interfaces?
- **SOLID anti-patterns:** God ViewModel (20+ methods, whole feature); God Activity/Fragment (logic+nav+analytics in `onCreate`); `Context`/`android.*` in a UseCase; concrete deps in domain; repository calling another repository; `instanceof`/`is Type` instead of polymorphism/sealed; throwing from overrides when the base contract doesn't; silent no-op overrides; `IEverything` interfaces.
- **YAGNI** — don't build for hypothetical futures: ❌ constructor params with defaults nobody configures (`strategy`, `retryPolicy`, `cacheExpiry`, `logger`); ❌ base class for a single implementation; ❌ interface existing purely for DI while tests use the concrete anyway (extract interface only when a fake is needed); ❌ pass-through UseCase; ❌ `expect/actual` where both actuals behave identically (use one common API like `Clock.System.now()`); ❌ abstract `BaseViewModelTest` for one test class (extract when 3+ duplicate the setup). Checklist: concrete current requirement? 2+ real callers? Would removing break anything today? If no to all — delete.
- **DRY** — about knowledge, not code: extract only when the same **decision** repeats (same formatting decision in 3 files → one `LocalDate.toDisplayString()`; same SRS calc in VM+UseCase+Service → one service owns the algorithm). Coincidental duplication (UserDto/AuthorDto mappers that merely look alike) must NOT be extracted — false coupling. Compose: extract when same UI + behavior in 2+ unrelated screens; keep inline when semantics differ though shapes match. Theme tokens are the single source of truth for color decisions (`MaterialTheme.colorScheme.primary`, never repeated `Color(0xFF6200EE)` literals).
- **KISS** — complexity is the enemy; the best code is code that doesn't exist. ❌ abstract factory for one product; ❌ generic transformer called once ("for future use"); ❌ wrappers of stdlib calls used in one place (`toImmutableListSafe()`); ❌ state machines where a boolean suffices (`isSyncing`, `lastSyncError` flags beat a 5-state enum for simple flows); ✅ direct method calls instead of event buses; ✅ just write what you need where you need it.
- Right-abstraction signals: same logic copied ≥3 times → extract; same concept in 2 representations → canonicalize; "might need X later" → don't build; unused parameter → remove; class with one caller → inline; interface with one impl and no fake needed → remove; UseCase that only delegates → inline into VM; `expect/actual` with identical actuals → common API. Abstraction IS right when: 2+ implementations exist now; needed for testing (enables fakes); encodes a genuine shared business rule; reduces cognitive load; names a real domain concept.

### Banned Anti-Patterns — Master Lookup (WRONG → RIGHT)

Load before reviewing any agent-generated Kotlin. Every row is a production bug waiting to ship. When auditing, grep for each WRONG pattern; if found, cite row number and apply the RIGHT fix before approving.

| # | Banned (WRONG) | Correct (RIGHT) | Why it breaks |
|---|----------------|-----------------|---------------|
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

Anti-rationalizations (excuse → reality):

| Excuse | Reality |
|--------|---------|
| "UiState overkill" | `data object Loading` costs one line |
| "Keys later" | Broken scroll/focus NOW |
| "Prototype skip repo" | Prototypes ship |
| "collectAsState fine" | Background collector drains battery |

Repo-level "Do Not" boundaries: never invent dependency versions — use the version table; no `GlobalScope`, naked `_state.value =`, or hardcoded UI strings; never skip `LazyColumn` keys or use `collectAsState()` on Android screens.

### Cross-Cutting Architecture / State Anti-Patterns (Quick Reference)

| Anti-pattern | Why it is harmful | Better replacement |
|---|---|---|
| Business logic inside composables | forks source of truth, hurts testability, reruns during composition | move logic into ViewModel/domain services; composables map state → UI |
| Giant god-ViewModel | blast radius too large, slow reasoning, hard ownership | one ViewModel per screen or independent flow |
| Scattered `updateState`/`sendEffect` with no structure | state transitions hard to trace, mutations across callbacks | disciplined `onEvent()` as single entry point |
| Unstable state models (mutable collections, lambdas in state) | defeats Compose skipping, more recomposition | immutable data classes, immutable collections |
| Duplicated derived data (`total`, `formattedTotal`, `hasTotal` all stored) | bugs from drift, harder transitions | keep canonical value + derive via computed property |
| Broad state reads in parent composables | recomposition cascades to all children | slice state, pass only required props to each child |
| Mutable state passed deep into tree | hidden writes, unpredictable data flow | explicit props + callbacks |
| One-off events stored as consumable state (`showSnackbarOnce = true`) | event replay on config change, stale effects | separate `Effect` via `Channel` |
| No-op state emissions (copy state when nothing changed) | wasted recomposition cycles | guard unchanged values before updating |
| Full-screen loading wipes existing content | bad UX, layout jumps, lost user trust | keep old content + inline refresh indicator |
| ViewModel doing platform work directly (share, analytics, navigation) | breaks testability, platform coupling | emit effects, handle in Route composable |
| Animation state in ViewModel for no reason (`shakeCount`, `alpha`) | pollutes business state | local composable animation state |
| Display strings stored too early (ViewModel emits pre-baked formatted text) | locale inflexibility, state duplication, harder reuse | keep canonical values until presentation boundary |
| Poor lazy list keys (no key or index-based) | state jumps between rows, broken animations | stable key by domain ID |
| Too many trivial composables (wrappers around single `Text`/`Spacer`) | fragmentation, harder reading | extract only meaningful boundaries |
| Platform abstraction too early (interfaces for everything before pain) | unnecessary indirection, poor fit | share business logic first, abstract real platform capabilities only |
| Forcing MVI migration on existing codebase | churn without value, team friction | respect existing patterns, introduce MVI for new features only |
| Inline fully qualified package paths | hurts readability, clutters business logic, hides intent behind package noise | import at file top; use `import ... as ...` for name clashes |
| `Activity`/`Fragment`/raw `Context` in ViewModel | leaks, lifecycle mismatch | IDs via `SavedStateHandle`, nav args, repositories |
| `@Inject` ViewModel without `@HiltViewModel` | Hilt does not own the instance | `@HiltViewModel` + `@Inject constructor` + `hiltViewModel()` |
| Manual `ViewModel(...)` factories everywhere | bypasses DI graph | `hiltViewModel()` or Hilt-assisted factories |
| Feature-only deps in `SingletonComponent` "just in case" | wrong lifetime, wasted memory | `ViewModelComponent` / `@ViewModelScoped` |
| LiveData in new code | lacks Flow power/operator story | `StateFlow`/`SharedFlow` + `collectAsStateWithLifecycle()` |
| Static objects holding `Context` | memory leaks | `@Singleton @Inject constructor(@ApplicationContext ...)` |
| Mutable collections in `@Immutable` data classes | contract lie, Compose can't detect changes | immutable or persistent collections |
| Premature abstraction (interface+factory+impl for one caller) | indirection without value | simple, direct classes until complexity emerges |
| Callback hell | unreadable, error paths multiply | suspend functions + structured concurrency |
| Feature-to-feature VM deps | coupling breaks modularity | Navigator interfaces + app-module mediator |

Kotlin clean-code anti-pattern table (`!!`; try-catch control flow; `var` in data classes; long parameter lists; deeply nested lambdas; `Any`/unchecked casts; empty `catch {}`; commented-out code; `runCatching {}`; primitive IDs; LiveData; mocks) — prefer respectively: safe calls/Elvis/`requireNotNull`; `Try<T>`/`.fold {}`; `val` + `copy()`; data class params; coroutines/named functions; generics/sealed types; handle or rethrow; delete it; `Try<T>`/`Either`; `@JvmInline value class`; `StateFlow`/`SharedFlow`; fakes.
Never-use list (KMP playbook): `!!`; try-catch for control flow; sealed Event/Intent classes where event sinks suffice (project convention); `collectAsStateWithLifecycle` in `commonMain` (use `collectState()`); `LaunchedEffect` for navigation/effects (use `OnEvents`); `runBlocking` in tests (use `runTest`); mocks in tests (write fakes).

### Code Quality Gates (Detekt) & Suppressions

- Detekt is the primary static-analysis tool, applied per module via a convention plugin (`alias(libs.plugins.app.detekt)`); central config (`plugins/detekt.yml` / `config/detekt.yml`) with optional per-module overrides; type resolution enabled for Android modules; Compose-specific ruleset; XML/HTML/SARIF reports; exclude `**/build/**`, `**/generated/**`, `**/*.kts`, `**/resources/**`.
- Baselines: use when adopting on legacy code / enabling new rules gradually; do NOT use for new projects or in active development (they hide problems). `./gradlew :app:detektBaseline`, commit `detekt-baseline.xml`.
- Acceptable `@Suppress` on `@Composable` functions (place directly above `@Composable`): `LongMethod` (layout trees are naturally long), `LongParameterList` (route/screen params), `CyclomaticComplexMethod` (`when` over UI states).
- Targeted suppressions over broad: `catch (@Suppress("TooGenericExceptionCaught") e: Exception)`; `@file:Suppress("MatchingDeclarationName")` for a file with a primary composable + supporting types; `"TooManyFunctions"` for composable helper files; `"MagicNumber"` for layout-dimension files.
- Do NOT suppress without fixing: `ComplexMethod` in ViewModels/business logic → refactor; `LongParameterList` in data classes → builder/DSL; `TooGenericExceptionCaught` when specific exceptions are handled → specific catches; `UnusedPrivateProperty` → remove.
- Rules: fix, don't suppress; justify each suppression with a comment; be specific; review "temporary" suppressions regularly; accept that Compose differs from imperative-code rules.

### Checklists

Mandatory defaults summary (Android screen scaffold):
- Strings: `stringResource` / `@StringRes` — zero hardcoded UI text.
- State: ViewModel `StateFlow` + `_state.update { }`.
- UI: stateless composables; `modifier` as first optional `Modifier = Modifier` param.
- DI: `@HiltViewModel` + constructor injection (or `koinViewModel()`).
- Collect: `collectAsStateWithLifecycle()` at the route only; effects via `Channel`/`CollectEffect`.
- Lists: `LazyColumn` + keys + `contentType` (see Compose/perf domain).

Architecture review checklist:
- [ ] Dependencies point inward; `:domain` has zero framework/Android imports.
- [ ] Repository interfaces in domain, impls (`internal`) in data; mappers as source-type extensions at the data boundary.
- [ ] One ViewModel per screen; no `_state.value =` naked assigns; private `MutableStateFlow` + public `asStateFlow()`.
- [ ] Effects for one-shot actions only; no consumable-boolean state.
- [ ] `stateIn(WhileSubscribed(5_000))` instead of init-collect; `combine` multi-flows into single UiState.
- [ ] No `Dispatchers.IO` wrapping Room/suspend-network; `CancellationException` always rethrown.
- [ ] UseCases exist only where logic is shared/multi-repo/policy-heavy; pass-through UseCases deleted.
- [ ] No feature→feature deps; navigation via interfaces + app-module wiring; nav args = IDs.
- [ ] No ViewModel in composable params; route→screen→leaf slicing; leaves get specific callbacks.
- [ ] UiState equality-friendly: immutable data class or sealed per state exclusivity; no duplicated derived fields; canonical values, display formatting at UI edge.

Kotlin review checklist:
- [ ] No `!!` without a justification comment.
- [ ] No `var` in data classes — `val` + `copy()`.
- [ ] No magic literals — named constants or `value class`.
- [ ] Functions under ~20 lines, single responsibility.
- [ ] All failure paths return `Result`/`Try` or a sealed type — no `null` leaking out.
- [ ] No `try-catch` for control flow.
- [ ] Booleans named `is*`, `has*`, `can*`.
- [ ] IDs are value classes, not raw `Int`/`String`.
- [ ] No commented-out code.
- [ ] Test names describe the scenario (`given_when_then`).
- [ ] KDoc on all public API in shared modules.
- [ ] Imports at top; `import … as …` with layer suffix for clashes.
- [ ] Delegation params have no `private`; base classes audited for dead code.

Module checklist:
- [ ] `pluginManagement { includeBuild("build-logic") }` first block in root `settings.gradle.kts`.
- [ ] Convention plugins used — no duplicated Gradle boilerplate; `extensions.configure<...>` inside plugins; `applyDefaultHierarchyTemplate()` for `iosMain`; `jvmToolchain(17)` not `compileOptions`; `namespace` per Android module; catalog plugin aliases (`kmp-library`, `kmp-feature`, `kmp-compose` — `version = "unspecified"`).
- [ ] `:domain` zero Android/framework imports; `:feature:X` never depends on another `:feature:*`; `:core:design-system` Compose-only; `:core:testing` test-classpath-only; `:platforms` expect/actual only.
- [ ] Each feature: own DI module + navigation extension registered/wired in `:app`.
- [ ] Dependency Guard on release + debug runtime classpaths; gradle parallel + caching.

Layer testing map (architecture-driven):

| Layer | Test approach | Tools |
|---|---|---|
| Domain / UseCase | Unit — pure logic, inject `FakeClock` | `kotlin-test`, fakes |
| Repository | Unit — fake data sources | `FakeXxxLocalDataSource` |
| Data source | Unit — fake HTTP / in-memory DB | `MockEngine`, in-memory Room |
| ViewModel | Unit — state & effects | Turbine, fake use cases |
| Integration | Instrumented | Hilt testing, real DB |
| UI | Compose UI testing | `composeTestRule` |

## DOMAIN: Data, Networking & Dependency Injection

### Room

Setup (version catalog):
```toml
[versions]
room = "<latest>"   # always search for latest stable androidx.room / androidx.sqlite / com.google.devtools.ksp
sqlite = "<latest>"
ksp = "<latest>"    # must match your Kotlin version
[libraries]
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
# room-ktx (2.x), room-paging (only when a DAO returns PagingSource)
```
KSP, **never kapt** (`ksp(libs.room.compiler)`). Android-only: `Room.databaseBuilder(context, AppDatabase::class.java, "app.db")`. Gradle: plugins `com.google.devtools.ksp` + `androidx.room`; `room { schemaDirectory("$projectDir/schemas") }` — export schema JSONs to VCS.

Room 3 (`androidx.room3`): keep **`suspend`** and **`Flow`** DAOs; `Room.databaseBuilder(...).setDriver(BundledSQLiteDriver())`; KSP-only (`androidx.room3:room3-runtime`, `sqlite-bundled`, ksp `room3-compiler`); optional `room3-paging` with `@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)` only when a DAO returns `PagingSource`; `room3-testing` for instrumented DB tests. Room 3 **disallows blocking DAO methods** except reactive types such as `Flow`. `SupportSQLiteDatabase.query` is not exposed — ad-hoc SQL uses `SQLiteConnection`. Prefer a stable `androidx.room3` release; if pinned to preview, plan upgrade. (androidx.room 2.7+ is already KMP-ready.)

Database definition (KMP):
```kotlin
@Database(entities = [ProjectEntity::class, TaskEntity::class], version = 1)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
}
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
```
Android-only: skip `@ConstructedBy`, use `Room.databaseBuilder`. Provide `RoomDatabase` + DAOs as DI singletons — each instance manages its own connection pool; multiple instances break invalidation.

Critical performance rules:
| Rule | Why |
|------|-----|
| Index every column in `WHERE`, `ORDER BY`, `JOIN ON` | Avoids full table scan: O(n) → O(log n) |
| Batch writes inside `@Transaction` | Individual inserts each trigger separate disk sync |
| Select only needed columns (projection data classes) | Reduces memory and I/O vs `SELECT *` |
| `Flow` for reactive reads, `suspend` for writes | Auto-notify on changes; keep main thread free |
| Never `allowMainThreadQueries()` in production | Blocks UI, causes ANRs |
| Use `BundledSQLiteDriver` for KMP | Consistent SQLite version across platforms |
| Provide `RoomDatabase` as DI singleton | Each instance manages its own connection pool |

Entity design:
```kotlin
@Entity(
    tableName = "tasks",
    indices = [Index("projectId"), Index("projectId", "dueDate")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,      // String UUID or autoGenerate Int/Long
    val title: String,
    val projectId: Long,
    @ColumnInfo(name = "due_date") val dueDate: Long? = null,
    @ColumnInfo(defaultValue = "0") val isCompleted: Boolean = false,  // defaults survive migrations
    @Ignore val displayOrder: Int = 0                     // not persisted
)
// Composite key: @Entity(primaryKeys = ["taskId", "labelId"])
// Unique: Index(value = ["title"], unique = true) / Index(value=["email"], unique=true)
// Full-text search: @Fts4(contentEntity = ItemEntity::class) with MATCH queries
```
Foreign keys (junction example doubles as pattern):
```kotlin
@Entity(
    tableName = "task_labels", primaryKeys = ["taskId", "labelId"],
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LabelEntity::class, parentColumns = ["id"], childColumns = ["labelId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("labelId")]
)
data class TaskLabelCrossRef(val taskId: Long, val labelId: Long)
```

Index decision:
| Scenario | Index? | Reason |
|----------|--------|--------|
| Column in `WHERE`/`ORDER BY`/`JOIN ON` | Yes | Avoids full scan / sort pass |
| Foreign key column | Yes | Room warns if missing |
| Rarely queried column / tiny table | No | Wastes storage, slows writes |

Composite index `(a, b)` accelerates queries on `a` alone or both. Column order matters — most selective first. Don't index everything — each index costs write performance.

DAO patterns:
```kotlin
@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(task: TaskEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(tasks: List<TaskEntity>): List<Long>
    @Update suspend fun update(task: TaskEntity)
    @Upsert suspend fun upsert(task: TaskEntity)          // Room 2.5+; also upsertAll(List<..>)
    @Delete suspend fun delete(task: TaskEntity)
    @Query("DELETE FROM tasks WHERE projectId = :projectId") suspend fun deleteByProject(projectId: Long)
    @Query("SELECT * FROM tasks WHERE projectId = :projectId ORDER BY due_date ASC")
    fun observeByProject(projectId: Long): Flow<List<TaskEntity>>   // auto-invalidates on table change
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun getById(id: Long): TaskEntity?
    @Query("SELECT COUNT(*) FROM tasks WHERE user_id = :userId") suspend fun countByUserId(userId: String): Int
    @Query("UPDATE items SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)     // partial-column update
}
```
Return-type rule:
| Return Type | Use for | Thread |
|------------|---------|--------|
| `Flow<List<T>>` | Reactive queries (lists, search) | Background (auto) |
| `Flow<T?>` | Single item observation | Background (auto) |
| `suspend fun` | One-shot reads, all writes | Background (auto) |
| `fun` (no suspend, no Flow) | **WRONG** — blocks main thread | |

- **KMP:** all DAO functions for non-Android must be `suspend` or return `Flow` (blocking = crash).
- Always use `:paramName` bind parameters — never concatenate SQL.
- `@Upsert` preferred over `@Insert(onConflict = REPLACE)` — REPLACE deletes then re-inserts, triggering FK cascading deletes. (compose-kotlin variant: use `IGNORE` + manual UPDATE instead of REPLACE with FKs.)
- Room handles threading internally — do **not** wrap Room calls in `Dispatchers.IO`/`withContext(Dispatchers.IO)` (double dispatch; banned row 10). Same for Retrofit-suspend and Ktor.

Performance-oriented queries:
```kotlin
data class TaskSummary(val id: Long, val title: String, @ColumnInfo(name = "due_date") val dueDate: Long?)
@Query("SELECT id, title, due_date FROM tasks WHERE projectId = :projectId")
fun observeSummaries(projectId: Long): Flow<List<TaskSummary>>   // projection, not SELECT *
@Query("SELECT projectId, COUNT(*) AS taskCount, SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END) AS completedCount FROM tasks GROUP BY projectId")
suspend fun getProjectStats(): List<ProjectStats>
@Query("SELECT * FROM posts ORDER BY created_at DESC LIMIT :limit OFFSET :offset")  // bounded results
suspend fun getPostsPage(limit: Int, offset: Int): List<Post>
fun getPostsPaged(): PagingSource<Int, Post>  // unbounded scrolling → Paging 3
```
SQLite tips: avoid `TEXT` for numbers (`id INTEGER PRIMARY KEY, age INTEGER, created_at INTEGER`); check `EXPLAIN QUERY PLAN` in Database Inspector. Never load an entire table into memory.

Relationships:
```kotlin
data class ProjectWithTasks(
    @Embedded val project: ProjectEntity,
    @Relation(parentColumn = "id", entityColumn = "projectId") val tasks: List<TaskEntity>
)
@Transaction @Query("SELECT * FROM projects WHERE id = :id")
suspend fun getWithTasks(id: Long): ProjectWithTasks?
// many-to-many:
data class TaskWithLabels(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "id",
        associateBy = Junction(TaskLabelCrossRef::class, parentColumn = "taskId", entityColumn = "labelId"))
    val labels: List<LabelEntity>
)
```
Always `@Transaction` on relational queries — Room issues multiple queries internally (consistent snapshot).

TypeConverters:
```kotlin
class Converters {
    @TypeConverter fun fromInstant(value: Long?): Instant? = value?.let { Instant.fromEpochMilliseconds(it) }
    @TypeConverter fun toInstant(instant: Instant?): Long? = instant?.toEpochMilliseconds()
    @TypeConverter fun fromStringList(value: List<String>?): String? = value?.let { Json.encodeToString(it) }
    @TypeConverter fun toStringList(value: String?): List<String>? = value?.let { Json.decodeFromString<List<String>>(it) }
    @TypeConverter fun fromStatus(status: Status?): String? = status?.name
    @TypeConverter fun toStatus(value: String?): Status? = value?.let { Status.valueOf(it) }
    // Compose Color ↔ Int via toArgb()/Color(argb)
}
@Database(entities = [...], version = 2, exportSchema = true)
@TypeConverters(Converters::class)   // ← registering on the @Database class is mandatory
abstract class AppDatabase : RoomDatabase() { ... }
```
KMP: use `kotlinx-datetime`. Reserve TypeConverters for simple mappings (timestamps, enums) — prefer normalized tables over JSON blobs; store time as epoch-millis `Long`.

Transactions:
- **KMP:** `database.useWriterConnection { it.immediateTransaction { } }` for writes, `database.useReaderConnection { it.deferredTransaction { } }` for consistent reads.
- **Android-only:** `database.withTransaction { }` (not available in KMP `commonMain`).
- **DAO-level:** `@Transaction` to group multiple queries atomically (e.g. `deleteAll()` + `insertAll()` = `replaceAll`). NOT needed for a single `@Insert`/`@Update`/`@Delete`/`@Query`.

Migrations:
```kotlin
// Android (SupportSQLiteDatabase) — KMP variant takes androidx.sqlite.SQLiteConnection with same execSQL
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
    }
}
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `comments` (`id` TEXT NOT NULL, ..., PRIMARY KEY(`id`), FOREIGN KEY(`item_id`) REFERENCES `items`(`id`) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_item_id` ON `comments` (`item_id`)")  // create indices too
    }
}
Room.databaseBuilder(...).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
// AutoMigration for simple changes (Room 2.4+):
@Database(entities = [...], version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3, spec = Migration2To3Spec::class)])
abstract class AppDatabase : RoomDatabase()
@RenameColumn(tableName = "auth_accounts", fromColumnName = "name", toColumnName = "service_name")
class Migration2To3Spec : AutoMigrationSpec
```
Export schema to VCS (`exportSchema = true` — otherwise can't verify migrations). `fallbackToDestructiveMigration()` **only in early dev, NEVER in production** (wipes user data).

Full-text search:
```kotlin
@Entity(tableName = "items_fts")
@Fts4(contentEntity = ItemEntity::class)
data class ItemFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String
)
@Query("SELECT items.* FROM items JOIN items_fts ON items.rowid = items_fts.rowid WHERE items_fts MATCH :query")
fun search(query: String): Flow<List<ItemEntity>>
itemDao.search("$query*")   // append * for prefix matching
```

Sync-aware entity (single source of truth bookkeeping):
```kotlin
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String? = null,
    val title: String, val status: TaskStatus,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus,       // SYNCED, PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE, FAILED
    @ColumnInfo(name = "last_modified") val lastModified: Instant,
    @ColumnInfo(name = "server_version") val serverVersion: Int = 0,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,    // soft delete
)
// DAO: @Query("SELECT * FROM tasks WHERE sync_status != 'SYNCED' AND is_deleted = 0") suspend fun getPendingSync(): List<TaskEntity>
//      @Query("UPDATE tasks SET is_deleted = 1, last_modified = :timestamp WHERE id = :id") suspend fun markAsDeleted(id: String, timestamp: Instant)
```

KMP builder via `expect/actual` (paths differ per platform — never hardcode):
```kotlin
expect class DatabaseFactory { fun create(): RoomDatabase.Builder<AppDatabase> }
// androidMain:  Room.databaseBuilder(context, AppDatabase::class.java, context.getDatabasePath("auth.db").absolutePath)
// iosMain:      Room.databaseBuilder(AppDatabase::class.java, NSHomeDirectory() + "/Documents/auth.db")
// desktopMain:  Room.databaseBuilder(AppDatabase::class.java, File(System.getProperty("user.home"), ".auth/auth.db").absolutePath)
```

Room vs SQLDelight (KMP):
| Aspect | Room (2.7+) | SQLDelight |
|--------|-------------|------------|
| KMP support | Android + JVM + iOS (limited) | Full KMP (all targets) |
| API style | Annotations + DAO interface | Raw SQL + generated Kotlin |
| Type safety | Compile-time via KSP | Compile-time via SQL verification |
| Flow support | Built-in `Flow<List<T>>` | `.asFlow().mapToList()` |
| Migration | Auto + manual | Numbered `.sqm` files |
| Learning curve | Easier for Android devs | Easier for SQL-first devs |
| Maturity on KMP | Newer (2024+) | Battle-tested |

Choose Room when: team knows Room, Android + iOS targets, already on Koin/Hilt. Choose SQLDelight when: all KMP targets, SQL-first, complex queries.

Entity vs domain: map `TaskEntity.toDomain()` / `Task.toEntity()` at the repository boundary. Never pass `@Entity` classes to the UI; domain models carry no Room annotations. When single model is OK: small app, no network layer, entity IS the domain model — split when Room annotations would leak into shared KMP modules or DTO shapes differ. UI-transient flags (`isExpanded`, `isSelected`) belong in UiState, not Room.

Testing:
- DAO tests: `Room.inMemoryDatabaseBuilder<AppDatabase>()` with `BundledSQLiteDriver` + test dispatcher; assert `Flow` with Turbine.
- Migration tests: `MigrationTestHelper` — `helper.createDatabase("test_db", 1)`, seed rows, `runMigrationsAndValidate("test_db", 2, true, MIGRATION_1_2)`, verify data survived.
- ViewModel tests: fake DAO backed by `MutableStateFlow<List<Entity>>`.

Common Mistakes (verbatim, piyush):
❌ `@Insert` without `onConflict` — crashes on duplicate primary key
❌ Calling DAO from Main thread — always `withContext(Dispatchers.IO)`
❌ `fallbackToDestructiveMigration()` in production — destroys user data
❌ Missing `exportSchema = true` — can't verify migrations
❌ Collecting Flow inside Repository — let ViewModel/UseCase collect
❌ Foreign key without index — slow JOIN queries
❌ Forgetting `@TypeConverters` annotation on `@Database` class
❌ Using kapt for Room — use ksp

Room anti-patterns (meetmiyani table):
| Anti-pattern | Why it is harmful | Better replacement |
|---|---|---|
| `allowMainThreadQueries()` | Blocks UI, ANRs | `suspend` + `Flow` |
| `SELECT *` everywhere | Loads unused columns | Projection data classes |
| Missing indexes on queried columns | Full table scan | `@Entity(indices = [...])` |
| Destructive fallback only | Users lose data | `Migration` or `AutoMigration` |
| `@Insert(onConflict = REPLACE)` with FKs | Cascading deletes | `@Upsert` |
| Blocking DAO functions on KMP | Crashes non-Android | `suspend` or `Flow` |
| No `@Transaction` on relational queries | Inconsistent snapshot | Always `@Transaction` with `@Relation` |
| Multiple `RoomDatabase` instances | Breaks invalidation | DI singleton |
| Large blobs / nested JSON via TypeConverter | Bloats DB, opaque to SQL | File paths + normalized tables |

More (compose-kotlin): `@Transaction` on single query → overhead for nothing; `OnConflictStrategy.REPLACE` with foreign keys → REPLACE = DELETE + INSERT, cascades — use IGNORE + manual UPDATE; returning `LiveData` from DAO → use `Flow` (map/combine/filter/stateIn); storing JSON blobs in single column → normalize; no migration strategy → users uninstall; fat entities with UI state → keep transient in UiState. Repository returning `itemDao.getAll().first()` kills reactivity — return the `Flow`, let ViewModel collect.

### DataStore

Key-value + typed preferences via coroutines and Flow. Coroutine-based, type-safe, safe for Main thread. Modern replacement for SharedPreferences.

When to use:
| Need | Solution | Why |
|------|----------|-----|
| Key-value settings (theme, locale, flags) | Preferences DataStore | No schema, simple key-value, reactive Flow |
| Typed settings object with multiple fields | Typed DataStore (JSON serializer) | Type-safe, schema evolution via `@Serializable` data class |
| Structured config with schema (nested/repeated) | Proto DataStore | Protobuf message, full schema |
| Structured data with queries, indexes, relations | Room | SQL-backed, compile-time verified, supports Paging |
| Large binary blobs or files | Filesystem | DataStore is not designed for large payloads |
| Legacy SharedPreferences | `produceMigrations` + delete SP after | One-shot migration |
| Auth tokens | Keystore + EncryptedSharedPreferences or encrypted Proto | Not plain Preferences |

**Scope rule:** If you need `WHERE`, `JOIN`, or more than ~100 entries, use Room.

Critical rules:
1. **One instance per file** — never create multiple `DataStore` instances for the same file (→ `IllegalStateException`, corruption). Enforce via DI singleton.
2. **Immutable types only** — `T` in `DataStore<T>` must be immutable. Mutating breaks transactional consistency.
3. **No mixing SingleProcess / MultiProcess** — if any access point uses `MultiProcessDataStoreFactory`, all must.

Preference key types:
| Type | Factory |
|------|---------|
| `Int` | `intPreferencesKey("name")` |
| `Long` | `longPreferencesKey("name")` |
| `Double` | `doublePreferencesKey("name")` |
| `Float` | `floatPreferencesKey("name")` |
| `Boolean` | `booleanPreferencesKey("name")` |
| `String` | `stringPreferencesKey("name")` |
| `Set<String>` | `stringSetPreferencesKey("name")` |

WRONG / RIGHT — main-thread prefs & sync reads in composables:
```kotlin
// WRONG — apply() is async but still blocks binder; commit() blocks main thread
context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("dark_mode", dark).apply()
// WRONG — blocks composition
val dark = context.getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark_mode", false)  // in @Composable

// RIGHT — suspend edit on DataStore
private val Context.dataStore by preferencesDataStore(name = "settings")
suspend fun saveTheme(context: Context, dark: Boolean) {
    context.dataStore.edit { prefs -> prefs[booleanPreferencesKey("dark_mode")] = dark }
}
// RIGHT — Flow from DataStore, collect with lifecycle
@Composable fun ThemeToggle(vm: SettingsViewModel = hiltViewModel()) {
    val dark by vm.isDarkMode.collectAsStateWithLifecycle(initialValue = false)
    Switch(checked = dark, onCheckedChange = { vm.onEvent(SettingsEvent.DarkModeChanged(it)) })
}
class SettingsViewModel @Inject constructor(private val settingsRepo: SettingsRepository) : ViewModel() {
    val isDarkMode: StateFlow<Boolean> = settingsRepo.isDarkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
```

WRONG / RIGHT — typed keys, one object:
```kotlin
// WRONG — typo-prone magic strings: prefs.edit().putString("user_id", id).apply()
// RIGHT
object SettingsKeys {
    val DARK_MODE = booleanPreferencesKey("dark_mode")
    val USER_ID = stringPreferencesKey("user_id")
}
suspend fun DataStore<Preferences>.setDarkMode(enabled: Boolean) { edit { it[SettingsKeys.DARK_MODE] = enabled } }
val DataStore<Preferences>.darkModeFlow: Flow<Boolean> get() = data.map { it[SettingsKeys.DARK_MODE] ?: false }
```

Repository pattern (read + write) — always `.catch` IOException, `edit` is an atomic read-modify-write transaction:
```kotlin
object PrefsKeys {
    val DARK_MODE = booleanPreferencesKey("dark_mode")
    val LOCALE = stringPreferencesKey("locale")
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
}
class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<UserSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }   // file may be unreadable on first launch/corruption
        .map { prefs -> UserSettings(darkMode = prefs[PrefsKeys.DARK_MODE] ?: false) }
    suspend fun setDarkMode(enabled: Boolean) { dataStore.edit { it[PrefsKeys.DARK_MODE] = enabled } }
    suspend fun clearAll() { dataStore.edit { it.clear() } }
}
// Per-field Flows work too: val isDarkTheme: Flow<Boolean> = dataStore.data.catch{...}.map { it[KEY] ?: false }
```

Hilt module — full control:
```kotlin
@Provides @Singleton
fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        migrations = listOf(SharedPreferencesMigration(context, "legacy_prefs")),
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("app_preferences") }
    )
// KMP: PreferenceDataStoreFactory.createWithPath(produceFile = { producePath().toPath() }) with platform paths:
// android filesDir / ios NSDocumentDirectory / jvm app folder — NEVER java.io.tmpdir (data lost on reboot)
// Android-only shortcut: val Context.settingsDataStore by preferencesDataStore(name = "settings")
```

Typed DataStore (JSON serializer):
```kotlin
@Serializable data class AppSettings(val darkMode: Boolean = false, val locale: String = "en", val itemsPerPage: Int = 20)
object AppSettingsSerializer : Serializer<AppSettings> {
    override val defaultValue = AppSettings()
    override suspend fun readFrom(input: InputStream): AppSettings =
        try { Json.decodeFromString(input.readBytes().decodeToString()) }
        catch (e: SerializationException) { throw CorruptionException("Cannot read settings", e) }
    override suspend fun writeTo(t: AppSettings, output: OutputStream) =
        output.write(Json.encodeToString(t).encodeToByteArray())
}
val settingsDataStore: DataStore<AppSettings> = DataStoreFactory.create(
    serializer = AppSettingsSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
    produceFile = { File(context.filesDir, "app_settings.json") }
)
// Read: settingsDataStore.data ; Write: settingsDataStore.updateData { it.copy(locale = "fr") }
```

Proto DataStore:
```kotlin
// proto: syntax = "proto3"; option java_multiple_files = true; message UserSettings { bool dark_mode = 1; ... }
object UserSettingsSerializer : Serializer<UserSettings> {
    override val defaultValue: UserSettings = UserSettings.getDefaultInstance()
    override suspend fun readFrom(input: InputStream): UserSettings = try { UserSettings.parseFrom(input) }
        catch (e: InvalidProtocolBufferException) { throw CorruptionException("Cannot read proto", e) }
    override suspend fun writeTo(t: UserSettings, output: OutputStream) { t.writeTo(output) }
}
// DataStoreFactory.create(serializer = ..., produceFile = { context.dataStoreFile("user_settings.pb") })
// deps: androidx.datastore:datastore + protobuf-kotlin-lite ; typed variant needs kotlinx-serialization plugin
```

SharedPreferences migration — runs once on first access, old file deleted after success; never do manual dual-write migration (race on first launch):
```kotlin
// WRONG — manual copy: dual writes, race on first launch
// RIGHT option A:
preferencesDataStore(name = "settings", produceMigrations = { context ->
    listOf(SharedPreferencesMigration(context, "legacy_shared_prefs")) })
// RIGHT option B — selective keys:
SharedPreferencesMigration(context = context, sharedPreferencesName = "legacy_prefs",
    keysToMigrate = setOf("is_dark_theme", "user_id"))
// RIGHT option C — custom DataMigration<Preferences>: shouldMigrate / migrate / cleanUp { context.deleteSharedPreferences("legacy") }
```

Security (WRONG/RIGHT 5): never `dataStore.edit { it[stringPreferencesKey("auth_token")] = token }` in unencrypted prefs — use `EncryptedSharedPreferences` (`MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM)`, key scheme `AES256_SIV`, value scheme `AES256_GCM`) or Android Keystore + encrypted Proto.

DI: always provide `DataStore` as singleton — `@Provides @Singleton` (Hilt) / `single<DataStore<Preferences>> { createDataStore(get()) }` (Koin); multiple instances for the same file cause `IllegalStateException`.

Testing:
```kotlin
private fun createTestDataStore(testDir: File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        scope = TestScope(UnconfinedTestDispatcher()),
        produceFile = { File(testDir, "test.preferences_pb") }
    )
```
Temp directory per test, `deleteRecursively()` in teardown. For ViewModel tests, bypass DataStore with a fake repository backed by `MutableStateFlow`.

Common Mistakes (verbatim, piyush):
❌ Using `SharedPreferences` — always use DataStore in new code
❌ Reading DataStore with `.first()` on Main thread — observe as Flow in ViewModel
❌ Creating multiple DataStore instances for same file — always `@Singleton`
❌ No corruption handler — add `ReplaceFileCorruptionHandler { emptyPreferences() }`
❌ Storing large data in DataStore — use Room for lists/complex objects
❌ Calling `dataStore.edit {}` from Main thread — always from a coroutine

Anti-patterns (meetmiyani table):
| Anti-pattern | Why it is harmful | Better replacement |
|---|---|---|
| Multiple `DataStore` instances for same file | `IllegalStateException`, data corruption | DI singleton (`@Singleton` / `single`) |
| `runBlocking` on main thread | Blocks UI, ANRs | Collect `data` Flow in `viewModelScope` |
| Large objects/lists in DataStore | Entire file read/written every operation | Use Room for structured/large data |
| Missing `.catch` on `dataStore.data` | `IOException` crashes app | `.catch { if (it is IOException) emit(default) }` |
| No corruption handler | Corrupted file breaks reads permanently | `ReplaceFileCorruptionHandler` with defaults |
| `java.io.tmpdir` for Desktop | Data lost on reboot | Use app data dir (`~/Library/Application Support/` etc.) |
| Reading preferences inside composables | Recomposition storms | Read in repository/ViewModel, expose as `StateFlow` |
| Passing raw `Preferences` to UI | Leaks storage implementation | Map to domain model at repository boundary |

Extra (compose-kotlin 14): never `commit()` or `apply()` in new code — migrate to DataStore; never read DataStore synchronously in `@Composable` — always Flow; never duplicate writes to both SP and DataStore after migration completes. Map `Preferences` → domain models at the repository boundary — never pass `Preferences` or raw key lookups into ViewModel/UI.

### Offline-First

Apps that require internet lose 30%+ of users. Core principle: **local database is the single source of truth**. UI always reads from local DB, never directly from network.

```
User Action → ViewModel → Repository
                               ↓
                         Local DB (Room)  ← single source of truth
                               ↓
                          UI observes Flow from Room
                               ↓ (background)
                          Network sync → update Local DB → UI auto-updates
```
Dependency rule (android-lead): ViewModel/UseCase → Repository interface (domain) → RepositoryImpl (data) → RemoteDataSource + LocalDataSource. Repositories decide cached vs fresh; ViewModels never touch data sources directly.

Rule 1 — single-direction data flow:
```kotlin
class ItemRepositoryImpl @Inject constructor(
    private val itemDao: ItemDao, private val api: ItemApiService,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : ItemRepository {
    override fun getItemsStream(): Flow<List<Item>> =
        itemDao.getAll().map { it.map { entity -> entity.toDomain() } }   // UI always observes local DB
    override suspend fun sync(): Result<Unit> = withContext(dispatcher) {
        runCatching { itemDao.upsertAll(api.getItems().map { it.toEntity() }) }  // network updates DB silently
    }
}
@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: ItemRepository) : ViewModel() {
    val items: StateFlow<List<Item>> = repository.getItemsStream()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    init { sync() }
    fun sync() { viewModelScope.launch { repository.sync() } }   // DB update → Flow auto-updates UI
}
```

Read strategy — stale-while-revalidate (show cache immediately; refresh in background; never block UI on a call for content you already have):
```kotlin
override fun observeArticles(): Flow<DomainResult<List<Article>>> = flow {
    emitAll(dao.observeAll().map { entities -> DomainResult.Success(entities.map(ArticleEntity::toDomain)) })
    withContext(dispatchers.io) {
        val result = safeApiCall { api.getArticles(page = 1) }
        if (result is DomainResult.Success) dao.upsertAll(result.data.map(ArticleDto::toEntity))
        // If network fails, cached data is already emitted — no error interruption
    }
}
// Ktor variant: val items: Flow<List<Item>> = dao.observeAll().map { it.map { e -> e.toDomain() } }
//               suspend fun refresh() { dao.replaceAll(api.getItems().items.map { it.toEntity() }) }
```
Cache invalidation: time-based (`isCacheValid(key, maxAge = 5.minutes)` on cached lastUpdate timestamp, `markCacheUpdated`, `invalidateCache`), event-based (invalidate `"tasks"` + `"task_$id"` on create/delete), size-based LRU for in-memory caches (`LruCache<String, Bitmap>` with `sizeOf = byteCount`, ~`maxMemory()/8`).

Rule 2 — optimistic UI updates (apply locally first, rollback on server failure):
```kotlin
override suspend fun toggleFavorite(itemId: String): Result<Unit> = withContext(dispatcher) {
    val item = itemDao.getByIdOnce(itemId) ?: return@withContext Result.failure(NotFoundException())
    val newValue = !item.isFavorite
    itemDao.updateFavorite(itemId, newValue)                         // 1. UI updates instantly
    runCatching { api.patchItem(itemId, mapOf("is_favorite" to newValue)) }  // 2. sync background
        .onFailure { itemDao.updateFavorite(itemId, !newValue) }     // 3. rollback
}
```
Write path with sync queue: always save locally first (`SyncStatus.PENDING_CREATE` + `lastModified`), try immediate sync if online, queue on failure; `markAsDeleted` (soft delete) for offline deletes — never lose user data even if sync fails.

Rule 3 — connectivity monitoring (`<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`):
```kotlin
class NetworkMonitor @Inject constructor(@ApplicationContext private val context: Context) {
    val isOnline: Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService<ConnectivityManager>()!!
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
        }
        cm.registerNetworkCallback(NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
        trySend(cm.isCurrentlyConnected())     // emit current state immediately
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().shareIn(CoroutineScope(Dispatchers.IO), SharingStarted.WhileSubscribed(), 1)
}
// Stronger check: also require NET_CAPABILITY_VALIDATED (hasInternet && isValidated)
// ViewModel: networkMonitor.isOnline.filter { it }.collect { repository.sync() }   // sync when restored
```

Rule 4 — WorkManager background sync:
```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context, @Assisted workerParams: WorkerParameters,
    private val repository: ItemRepository
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = repository.sync().fold(
        onSuccess = { Result.success() },
        onFailure = { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    )
    companion object {
        const val WORK_NAME = "SyncWorker"
        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)  // 15 min = system minimum
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
```
SyncCoordinator pattern: `scheduleSyncNow()` → `OneTimeWorkRequestBuilder` + `ExistingWorkPolicy.REPLACE` + CONNECTED + exponential backoff; `schedulePeriodicSync()` → `PeriodicWorkRequestBuilder(1 HOUR, flex 15 MIN)` + `setRequiresBatteryNotLow(true)` + `ExistingPeriodicWorkPolicy.KEEP`; `observeSyncStatus()` via `getWorkInfosForUniqueWorkFlow`. Schedule periodic sync from `@HiltAndroidApp` Application `onCreate`. Chain: `beginWith(...).then(...)` sequential; parallel + join via `beginWith(listOf(a,b,c)).then(final)`; fan-out/fan-in. Pass `Data.Builder()` (putInt/putString/putStringArray → inputData) between workers — max 10KB per Data object. Progress: `setProgress(Data...)` + observe via `workManager.getWorkInfoByIdFlow(workId).map { it?.progress }` → `stateIn`; foreground via `setForeground(ForegroundInfo(id, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC))` (API 31+); expedited via `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`.

Work constraint comparison:
| Constraint | Use Case | Example |
|------------|----------|---------|
| `NetworkType.CONNECTED` | Any data sync | General API calls |
| `NetworkType.UNMETERED` | Large downloads | Media sync, backups |
| `NetworkType.NOT_ROAMING` | Cost-sensitive ops | International users |
| `requiresBatteryNotLow` | Background sync | Periodic updates |
| `requiresCharging` | Heavy operations | Database cleanup, indexing |
| `requiresStorageNotLow` | Downloads | Media, cache management |
| `requiresDeviceIdle` | Very heavy ops | Full database rebuild |

WorkManager Do: unique work names; constraints for battery/data; backoff for transient failures; expedited (API 31+) only when time-sensitive; report progress; chain multi-step ops; pass data via `Data.Builder()`; observe work status in ViewModel, not composables; test with TestDriver; cancel when unneeded. Don't: use for immediate execution (use coroutines); store large data in Worker input/output (max 10KB per `Data` object); rely on Worker lifecycle (interrupted/restarted); periodic flex < 15 min; forget to cancel periodic work when feature disabled; chain too many workers (batch in single worker); expedited unnecessarily (quota); block main thread in Worker (use CoroutineWorker); assume work runs immediately (constraints); use WorkManager for UI updates (use StateFlow/LiveData).

Rule 5 — pending operations queue (offline writes):
```kotlin
@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "operation_type") val operationType: String,   // "CREATE", "UPDATE", "DELETE"
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "payload") val payload: String,                // JSON-serialized request
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0
)
// create: always itemDao.upsert locally; if online runCatching api.createItem(...) .onFailure { queue }; else queue immediately → Result.success(Unit)
```

Rule 6 — stale data indicator: expose `SyncMetadata(lastSyncTime: Instant?, isSyncing: Boolean, syncError: String?)` as `StateFlow`; on failure keep showing cached data with `"Last sync failed. Showing cached data."`.

Sync strategies: (1) one-way server→local (`getTasks()` → `deleteAll()+insertAll()` or upsert incremental); (2) two-way push+pull with conflict checks (`getTasksSince(lastSyncTime)`; remote applied only when local `syncStatus == SYNCED`, else resolve conflict; store last sync time); (3) periodic WorkManager; (4) event-driven realtime (WebSocket/SSE events → apply only if `task.version > existing.serverVersion`). Result models: `enum class SyncStatus { SYNCED, PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE, FAILED }`; `sealed interface SyncResult { Success(itemsSynced), PartialSuccess(successCount, failures), NoNetwork, Error(message) }`; per-item `SyncItemResult { Success, Failed, Conflict, ConflictResolved, Retry }`. Sync error handling: 409 → Conflict; 400..499 → mark `SyncStatus.FAILED`, don't retry; else → Retry.

Conflict resolution strategies:
| Scenario | Strategy | Reason |
|-----------------------|-------------------|--------------------------------|
| User preferences | Client wins | User's device is authoritative |
| Shared documents | Last write wins | Simple, works for most cases |
| Collaborative editing | Manual resolution | Preserve both versions |
| Server-managed data | Server wins | Server is authoritative |

Server-wins: overwrite local fields from remote, `syncStatus = SYNCED`. Client-wins: force-push local with `expectedVersion`. Last-write-wins: compare `local.lastModified > remote.updatedAt`. Manual: persist `ConflictEntity(localVersion, remoteVersion)` + mark `SyncStatus.CONFLICT` + UI screen with "Keep Mine"/"Use Server" buttons.

Retry with exponential backoff:
```kotlin
class ExponentialBackoffRetry @Inject constructor() : RetryStrategy {
    override suspend fun <T> retry(block: suspend () -> T): T {
        var currentDelay = 1.seconds; val maxDelay = 32.seconds; var attempts = 0; val maxAttempts = 5
        while (true) { attempts++
            try { return block() } catch (e: Exception) {
                if (attempts >= maxAttempts) throw e
                if (e is HttpException && e.code() in 400..499) throw e   // don't retry client errors
                delay(currentDelay); currentDelay = (currentDelay * 2).coerceAtMost(maxDelay) } }
    }
}
// Configurable policy: RetryPolicy(maxAttempts=5, initialDelay=1s, maxDelay=32s, backoffMultiplier=2.0,
//   retryableErrors=setOf(408, 429, 500, 502, 503, 504))
// Jitter variant: randomize delay in 0..baseDelay to prevent thundering herd
```

Sync frequency guidelines: Critical data — immediate + periodic (15 min); user-generated content — immediately on create/update/delete; feed/timeline — periodic 30–60 min + manual refresh; profile — app start + manual; settings — immediately on change. Performance: batch operations, delta sync (`getTasksSince`), pagination, compression, all sync on IO dispatcher.

Best Practices Always (verbatim gist): use local database as source of truth; write locally first (optimistic updates); schedule background sync with WorkManager; monitor network state before attempting sync; implement conflict resolution for diverging changes; use exponential backoff for retries; add sync metadata to entities (syncStatus, lastModified, serverVersion); invalidate caches when data changes; handle partial sync failures gracefully; test offline scenarios. Never Do: never block UI waiting for network; never show stale data without refreshing (always emit cached first); never lose user data — persist locally even if sync fails; never retry indefinitely — set max retry limits; never ignore conflicts; never sync on metered connections without user consent; never assume sync will succeed; never expose repository sync details to UI; never use SharedPreferences for large datasets (use Room); never forget to cancel coroutines in repository when scope is cancelled.

Common Mistakes (verbatim, piyush):
❌ Showing loading while Room DB loads — DB is fast, show data immediately
❌ UI observing network response directly — UI must only observe local DB
❌ No retry logic for failed sync — use exponential backoff
❌ Running WorkManager sync on unconstrained network — always require CONNECTED
❌ Not handling sync conflicts — decide on server-wins or last-write-wins policy
❌ Clearing local cache on every fresh load — breaks offline experience

Testing offline: `FakeNetworkMonitor` (`MutableStateFlow`, `setConnected`), `FakeTaskRepository` (`setShouldFailSync`, `setSyncDelay`), `FakeSyncCoordinator` (flags `syncScheduled/periodicSyncScheduled/cancelled`), WorkManager `WorkManagerTestInitHelper.initializeTestWorkManager` + `TestDriver.setAllConstraintsMet(request.id)`; assert `WorkInfo.State.SUCCEEDED` / retry (`ENQUEUED` + `runAttemptCount > 0`) / chain failure cancelling downstream; `assertThat(result).isEqualTo(SyncResult.NoNetwork)`; verify optimistic write persists `PENDING_CREATE` + `verify(fakeSyncCoordinator).scheduleSyncNow()`.

### Networking

Ktor (default for CMP; also valid Android-only) and Retrofit+OkHttp (recommended for Android REST). Library choice: Retrofit for traditional REST / existing projects (mature, interceptors, `retrofit2-kotlinx-serialization-converter`); Ktor for pure Kotlin multiplatform (claude-android-ninja: "avoid for Android-only — Retrofit is more established"; meetmiyani treats Ktor as CMP default).

Ktor engine selection:
| Platform | Engine | Dependency |
|---|---|---|
| Android | OkHttp | `ktor-client-okhttp` |
| iOS | Darwin (NSURLSession) | `ktor-client-darwin` |
| JVM/Desktop | CIO | `ktor-client-cio` |
| All (testing) | MockEngine | `ktor-client-mock` |

Ktor client — create ONE reusable `HttpClient`, never per request; engine never omitted (`HttpClient()` with no engine crashes — `HttpClient(OkHttp)`/`Darwin`; in KMP pick engine via expect/actual per platform):
```kotlin
fun createHttpClient(engine: HttpClientEngine, baseUrl: String): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true      // ignore unknown JSON fields
            coerceInputValues = true      // null → defaults for non-null props
            encodeDefaults = true         // include defaults when serializing
        })
    }
    defaultRequest { url(baseUrl); headers.append("Accept", "application/json") }
    install(HttpTimeout) { connectTimeoutMillis = 15_000; requestTimeoutMillis = 30_000; socketTimeoutMillis = 15_000 }
    install(Logging) { logger = Logger.DEFAULT; level = LogLevel.HEADERS; sanitizeHeader { it == "Authorization" } }
}
```
`isLenient = true` only for non-standard APIs — accepts malformed JSON, hides data issues in production. `encodeDefaults = false` if you want null/defaults skipped in requests.

`expectSuccess` — choose by error strategy (both valid, apply consistently):
| Setting | Behavior | Use when |
|---|---|---|
| `true` (Ktor default) | Throws `ClientRequestException` / `ServerResponseException` on non-2xx | Using `try/catch` or `runCatching` |
| `false` | Returns the response regardless of status | Inspecting `response.status` in a custom wrapper |

Ktor API service + repository:
```kotlin
class ItemApi(private val client: HttpClient) {
    suspend fun getItems(page: Int = 1, limit: Int = 20): ItemListDto =
        client.get("items") { parameter("page", page); parameter("limit", limit) }.body()
    suspend fun getItem(id: String): ItemDto = client.get("items/$id").body()
    suspend fun createItem(request: CreateItemRequest): ItemDto =
        client.post("items") { contentType(ContentType.Application.Json); setBody(request) }.body()
    suspend fun deleteItem(id: String) { client.delete("items/$id") }
}
// Repository maps DTO→domain; simplest: exceptions bubble, ViewModel catches. Or Result/ApiResult wrapper (below).
```
Type-safe Resources (optional): add `ktor-client-resources`, `install(Resources)`; `@Serializable @Resource("/articles") class Articles { @Serializable @Resource("{id}") class ById(val parent: Articles = Articles(), val id: Int) ... }` → `client.get(Articles.ById(id = 42))`, nested paths/query params resolve from the resource tree.

Bearer token auth (`ktor-client-auth`) — default pattern uses `markAsRefreshTokenRequest()` so the refresh call isn't intercepted (avoids circular auth loops without a separate client):
```kotlin
install(Auth) {
    bearer {
        loadTokens { val t = tokenStorage.getTokens(); BearerTokens(t.accessToken, t.refreshToken) }
        refreshTokens {
            val refreshToken = oldTokens?.refreshToken ?: return@refreshTokens null
            try {
                markAsRefreshTokenRequest()
                val r = client.post("auth/refresh") { contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(refreshToken)) }.body<TokenResponse>()
                tokenStorage.saveTokens(r.accessToken, r.refreshToken)
                BearerTokens(r.accessToken, r.refreshToken)
            } catch (e: Exception) { onSessionExpired(); null }
        }
        sendWithoutRequest { request -> request.url.pathSegments.none { it in listOf("login", "register") } }
    }
}
```
Key points: `oldTokens` from `RefreshTokensParams`; `sendWithoutRequest` skips auth for login/register/public; return `null` from `refreshTokens` = refresh failed, no retry. `TokenStorage` interface uses app-owned types (`AuthTokens(accessToken, refreshToken)`) — convert to `BearerTokens` only at plugin boundary; implement with DataStore, encrypted SharedPreferences, or Keychain. Alternative: isolated refresh `HttpClient` without `Auth`, closed via `.use {}` — valid but `markAsRefreshTokenRequest()` achieves the same with less ceremony.

WebSockets (`ktor-client-websockets`): `install(WebSockets) { pingIntervalMillis = 30_000 }`; `client.webSocket("wss://...") { send(Frame.Text(...)); for (frame in incoming) { when (frame) { is Frame.Text -> ...; is Frame.Close -> break; else -> Unit } } }`; external control via `client.webSocketSession(...)` + `session.send/incoming.receive()/close()`; typed messaging: `contentConverter = KotlinxWebsocketSerializationConverter(Json)` → `sendSerialized(msg)` / `receiveDeserialized<T>()`.

SSE (built into `ktor-client-core`, no extra dep): `install(SSE)`; `client.sse(url) { incoming.collect { event -> event.event/data/id } }`.
| Criterion | SSE | WebSocket |
|---|---|---|
| Direction | Server -> Client only | Bidirectional |
| Protocol | HTTP (standard) | WebSocket (protocol upgrade) |
| Auto-reconnect | Built-in | Manual |
| Binary data | No (text only) | Yes |
| Use case | Live feeds, notifications, progress, streaming AI | Chat, gaming, real-time collaboration |
Prefer SSE for server-push; WebSockets when client also sends frequent messages.

Error handling decision:
| Criterion | `Result<T>` (stdlib) | Custom `ApiResult<T>` |
|---|---|---|
| Operators | Built-in `map`, `fold`, `getOrNull`, `onSuccess`, `onFailure` | Define your own |
| Error info | `Throwable` only | Sealed subclasses with structured data per kind |
| UI branching | `when (e) { is IOException -> ... }` | `when (error) { is ApiResult.Unauthorized -> ... }` |
| Maintenance | Zero | Team maintains |
| Best for | Most apps, prototypes, few error branches | Per-error-type UI flows (login redirect, retry prompt, offline) |
```kotlin
// Option A
suspend inline fun <reified T> HttpClient.safeRequest(block: HttpRequestBuilder.() -> Unit): Result<T> =
    runCatching { request { block() }.body<T>() }
// ViewModel: repository.getItems().onSuccess { ... }.onFailure { error -> when (error) {
//   is ClientRequestException -> handleHttpError(error.response.status.value)
//   is IOException -> "No connection"; else -> generic } }
// Option B
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    sealed class Failure : ApiResult<Nothing>() {
        data class HttpError(val code: Int, val message: String?, val serverMessage: String? = null) : Failure()
        data class NetworkError(val message: String? = null) : NetworkError-like : ...
        data class Timeout(...) / Unauthorized(...) / SerializationError(...) / Unknown(val throwable: Throwable)
    }
}   // + map/fold/getOrNull helpers
// Wrapper paired with expectSuccess = false:
suspend inline fun <reified T> HttpClient.safeRequest(block: HttpRequestBuilder.() -> Unit): ApiResult<T> = try {
    val response = request { block() }
    when (response.status.value) { in 200..299 -> ApiResult.Success(response.body<T>())
        else -> classifyStatus(response.status.value, tryParseError(response)) }
} catch (e: CancellationException) { throw e } catch (e: Exception) { classifyException(e) }
// 204 No Content → safeRequest<Unit> { ... }
```
Exception classification: timeouts (`HttpRequestTimeoutException`, `ConnectTimeoutException`, `SocketTimeoutException`) → Timeout; `IOException`, `UnresolvedAddressException` → NetworkError; `SerializationException`, `JsonConvertException`, `MissingFieldException` → SerializationError; `ClientRequestException` 401 → Unauthorized else HttpError; `ServerResponseException` → HttpError(5xx). Status: 401 Unauthorized, 403 Access denied, 404 Not found, 429 Too many requests, 400..599 generic. Parse error envelopes safely, never fail on malformed error body:
```kotlin
@Serializable data class ErrorDto(val message: String? = null, val error: String? = null, val detail: String? = null) {
    val displayMessage: String? get() = message ?: error ?: detail }
suspend fun tryParseError(response: HttpResponse): String? =
    runCatching { response.body<ErrorDto>().displayMessage }.getOrNull()
```
**`CancellationException` must always be re-thrown — never swallow it (breaks structured concurrency).**

Plugin composition:
| Concern | Where | Why |
|---|---|---|
| Base URL, content type, static headers | `defaultRequest {}` | Runs per-request, reads live state |
| JSON parsing | `ContentNegotiation` | Core plugin |
| Timeouts | `HttpTimeout` | Default for every project |
| Logging | `Logging` | Debug aid — sanitize `Authorization` in production |
| Token load and refresh | `Auth` plugin | Built-in retry cycle |
| Retry on server errors | `HttpRequestRetry` | Add when API has transient failures |
| Compression | `ContentEncoding` | Bandwidth-sensitive APIs |
Install order matters — plugins execute in installation order for requests, reverse for responses: `ContentNegotiation → Auth → HttpRequestRetry → HttpTimeout → ContentEncoding`. Install `HttpRequestRetry` before `HttpTimeout` so retries work on timeout errors; `Auth` handles 401s independently from `HttpRequestRetry` — keep separate. Custom plugins via `createClientPlugin("ApiKeyPlugin", ::ApiKeyConfig) { onRequest { request, _ -> request.headers.append("X-Api-Key", pluginConfig.apiKey) } }` (+ `onResponse` for analytics/session-expiry observation). Logging: Debug `LogLevel.BODY`; Production `LogLevel.HEADERS` or not installed + required `sanitizeHeader` for `Authorization`.

MockEngine testing (`commonTest` `ktor-client-mock`): respond with `content`/`status`/`headersOf(HttpHeaders.ContentType, "application/json")`; assert `request.url.encodedPath`, `request.method`, `(request.body as TextContent).text`; route by path (`when (request.url.encodedPath) { ... else -> respondError(HttpStatusCode.NotFound) }`); test 404 with `expectSuccess = true` → `assertFailsWith<ClientRequestException>`, or with wrapper → check `result.isFailure`. Accept `HttpClientEngine` as constructor parameter to inject `MockEngine`; share the same `createHttpClient` factory between prod and tests so plugin config matches.

Retrofit setup (catalog: `com.squareup.retrofit2:retrofit`, `converter-kotlinx-serialization`, `okhttp`, `logging-interceptor`, `kotlinx-serialization-json`, plugin `org.jetbrains.kotlin.plugin.serialization`):
```kotlin
interface ItemApiService {
    @GET("items/{id}") suspend fun getItem(@Path("id") id: String): ItemDto
    @GET("items") suspend fun getItems(@Query("page") page: Int = 1, @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "created_at", @Query("order") order: String = "desc"): PagedResponse<ItemDto>
    @POST("items") suspend fun createItem(@Body request: CreateItemRequest): ItemDto
    @PUT("items/{id}") suspend fun updateItem(@Path("id") id: String, @Body request: UpdateItemRequest): ItemDto
    @PATCH("items/{id}") suspend fun patchItem(@Path("id") id: String,
        @Body fields: Map<String, @JvmSuppressWildcards Any>): ItemDto
    @DELETE("items/{id}") suspend fun deleteItem(@Path("id") id: String): Unit
    @Multipart @POST("items/{id}/image") suspend fun uploadImage(@Path("id") id: String, @Part image: MultipartBody.Part): ImageDto
    @GET("user/profile") suspend fun getProfile(@Header("Authorization") token: String): UserDto
}
```
NetworkModule:
```kotlin
@Provides @Singleton fun provideJson(): Json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
@Provides @Singleton fun provideAuthInterceptor(tokenProvider: TokenProvider): Interceptor = Interceptor { chain ->
    val token = tokenProvider.getToken()
    chain.proceed(if (token != null) chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build() else chain.request())
}
@Provides @Singleton fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient =
    OkHttpClient.Builder().addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE })
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
@Provides @Singleton fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
    .baseUrl(BuildConfig.BASE_URL).client(client)
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
@Provides @Singleton fun provideItemApiService(retrofit: Retrofit): ItemApiService = retrofit.create(ItemApiService::class.java)
```
Result wrapper:
```kotlin
sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Error(val code: Int, val message: String) : NetworkResult<Nothing>
    data object NetworkError : NetworkResult<Nothing>      // no connection
    data object Timeout : NetworkResult<Nothing>
}
suspend fun <T> safeApiCall(apiCall: suspend () -> T): NetworkResult<T> = try { NetworkResult.Success(apiCall()) }
    catch (e: HttpException) { NetworkResult.Error(e.code(), e.response()?.errorBody()?.string() ?: e.message()) }
    catch (e: IOException) { NetworkResult.NetworkError }
    catch (e: SocketTimeoutException) { NetworkResult.Timeout }
// Repository maps NetworkResult → Result/domain exceptions (ApiException, NoNetworkException, TimeoutException)
```
Automatic 401 re-auth (OkHttp `Authenticator` is blocking — refresh synchronously):
```kotlin
class TokenAuthenticator @Inject constructor(private val tokenRepository: TokenRepository) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.pathSegments.last() == "refresh") return null  // don't retry refresh itself
        val newToken = runBlocking { tokenRepository.refreshToken() } ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer $newToken").build()
    }
} // OkHttpClient.Builder().authenticator(tokenAuthenticator).build()
```
Multipart: `File.toMultipartPart(partName)` → `asRequestBody(getMimeType().toMediaTypeOrNull())` → `MultipartBody.Part.createFormData(partName, name, requestBody)`.

Clean-architecture error mapping (android-lead): map network errors at the data source boundary — never expose `HttpException`/`IOException` to the domain layer. `sealed interface DomainResult<out T> { Success<T>(data), Failure(error: DomainError) }`; `sealed interface DomainError { NetworkUnavailable, Unauthorized, NotFound, ServerError(code,message), Unknown(throwable) }` — `safeApiCall` maps 401/404/5xx/IOException. Repositories expose `Flow<T>` for observed data and `suspend fun: DomainResult<T>` for operations; never return `List<T>` directly from a repository.

Backend SDKs — Supabase (supabase-kt; BOM + `postgrest-kt`/`auth-kt`/`realtime-kt`/`storage-kt`/`functions-kt` + a Ktor engine):
- Client via `@Module @InstallIn(SingletonComponent::class)` `createSupabaseClient(supabaseUrl = BuildConfig.SUPABASE_URL, supabaseKey = BuildConfig.SUPABASE_ANON_KEY) { install(Auth) { scheme = "myapp"; host = "callback" }; install(Postgrest); ... }`.
- #1 bug `UnauthorizedRestException` on Edge Functions: default `install(Auth)` persists session and `getUser()` returns null after hot restart. FIX for JWT passthrough: `install(Auth) { persistSession = false }` + `userClient.auth.getUser(jwt)` (pass jwt directly, always), then `functions.invoke("my-function")`. Server: `verify_jwt = false` in `config.toml` when handling JWT manually (true → 401).
- Queries: DTOs `@Serializable`; `supabase.from("items").select { filter { eq("user_id", userId) }; order("created_at", Order.DESCENDING); limit(50) }.decodeList()` (arrays — `decodeSingle()` only for one); `insert(item) { select() }.decodeSingle()`; `update(updates) { filter { eq("id", id) }; select() }`; `upsert(item)`; `delete { filter { eq("id", id) } }`.
- Realtime: `supabase.realtime.createChannel("items-$userId")`, `channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "items"; filter = "user_id=eq.$userId" }` → re-fetch on change; `connect(); channel.subscribe(); awaitCancellation()`, cleanup in `onCompletion { removeAllChannels() }`.
- Storage: `supabase.storage.from(bucket).upload(path, data) { upsert = true; contentType = ContentType.Image.JPEG }` → `publicUrl(path)`.
- Session observation: `supabase.auth.sessionStatus`; `signInWith(Email) { this.email = ...; this.password = ... }`, `signUpWith`, `signOut()`, `currentUserOrNull()`.
- Common Mistakes (verbatim): ❌ Missing `persistSession = false` when calling Edge Functions with user JWT / ❌ Using `decodeSingle()` on a list query — use `decodeList()` / ❌ Not making DTOs `@Serializable` — runtime crash on decode / ❌ Calling Supabase on Main thread — all operations are suspend, call from coroutine / ❌ `verify_jwt = true` on Edge Function that handles JWT manually — 401 error / ❌ Not setting up Row Level Security — all users can see all data

Backend SDKs — Firebase (always use BoM `platform(libs.firebase.bom)` + `google-services` plugin):
- Hilt module `@Provides @Singleton`: `Firebase.auth`, `Firebase.firestore.also { it.firestoreSettings = firestoreSettings { isPersistenceEnabled = true } }`, `Firebase.storage`, `Firebase.analytics`.
- Auth repo: `currentUser: Flow<User?> = callbackFlow { val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toDomain()) }; auth.addAuthStateListener(l); awaitClose { auth.removeAuthStateListener(l) } }`; `signInWithEmailAndPassword(...).await()` inside `runCatching`; Google: `GoogleAuthProvider.getCredential(idToken, null)` → `signInWithCredential`.
- Firestore: real-time `callbackFlow` + `addSnapshotListener` (`close(error)` on error, `doc.toObject(ItemDto::class.java)?.copy(id = doc.id)`); one-shot `.get().await()`; `add()` for new / `set()` for known id; partial update `.update("is_favorite", x, "updated_at", FieldValue.serverTimestamp())`; atomic multi-doc `firestore.runBatch { batch -> ... }`; `runTransaction { transaction.get/set/delete }`.
- FCM: `@AndroidEntryPoint class MyFirebaseMessagingService : FirebaseMessagingService()` with `@Inject lateinit var` handler; `onMessageReceived`, `onNewToken`.
- Common Mistakes (verbatim): ❌ Missing `google-services.json` in `app/` directory / ❌ Not using BoM — version conflicts between Firebase libraries / ❌ Calling Firebase on Main thread without `.await()` — use suspend + `.await()` / ❌ No offline persistence for Firestore — enable `isPersistenceEnabled = true` / ❌ Missing Firestore security rules — always configure before production / ❌ Storing sensitive data in Firestore without encryption

Networking Common Mistakes (verbatim, piyush retrofit):
❌ Non-suspend service functions — all Retrofit functions must be `suspend`
❌ Catching generic `Exception` without type — always catch `HttpException` + `IOException`
❌ Exposing Retrofit exceptions to ViewModel — wrap in domain exceptions
❌ Hardcoded base URL — use BuildConfig.BASE_URL
❌ Logging enabled in release build — check `BuildConfig.DEBUG` before setting log level
❌ No `ignoreUnknownKeys = true` — crashes when API adds new fields
❌ Raw Response types — always use typed response bodies
❌ Missing timeout configuration — default OkHttp timeouts are too long

Networking anti-patterns (tables):
| Anti-pattern | Why it hurts | Better approach |
|---|---|---|
| `HttpClient` per request | Connection pool waste, resource leaks | Shared singleton via DI |
| Swallowing `CancellationException` | Breaks structured concurrency, coroutine never cancels | Re-throw explicitly |
| Logging request bodies in production | Leaks sensitive data (tokens, PII) | `LogLevel.HEADERS` or off; `sanitizeHeader` for auth |
| Mixing `expectSuccess = true` with manual status inspection | Exception thrown before you inspect status | Pick one: `true` + catch, or `false` + check `response.status` |
| Random plugin install order | Retries fire before timeout, auth conflicts with retry | Follow documented composition order |
| Forced specific result wrapper | Doesn't adapt to team conventions/scale | Present `Result`/`ApiResult` as a project decision |
| DTOs used directly in UI state | UI coupled to API contract | Map to domain models at repository boundary |
| Network calls in composables | Violates UDF, untestable, reruns on recomposition | Call from ViewModel, expose via StateFlow |
| No timeout configuration | Requests hang indefinitely on bad networks | Set `connectTimeoutMillis`, `requestTimeoutMillis`, `socketTimeoutMillis` |
| Hardcoded base URLs | Can't switch environments | Inject base URL via config or DI |
| Parsing/mapping in the API service | Mixes concerns | API service returns DTOs; repository maps to domain |
| Creating a new `HttpClient` per test | Tests miss plugin-config mismatches | Same `createHttpClient` factory with `MockEngine` |
| No compression | Wastes bandwidth on text-heavy APIs | `install(ContentEncoding) { gzip() }` |
| Catching `Exception` instead of specific types | Catches `CancellationException`, breaks coroutines | Specific catch + re-throw cancellation |
| Parsing JSON in composable | CPU-heavy | Parse in repository/ViewModel on background dispatcher |
| Token in URL query param | Visible in logs/history | Always use Authorization header |
| Default Ktor has no timeout | Hangs | Set explicit `HttpTimeout` |

### Serialization & Mapping

kotlinx.serialization (recommended over Gson: compile-time safety, faster; Retrofit via `converter-kotlinx-serialization`/`asConverterFactory(MediaType)`, Ktor via `ktor-serialization-kotlinx-json`). Recommended `Json` config: `ignoreUnknownKeys = true` (API can add fields without breaking app), `coerceInputValues = true` (null → default for non-null props), `encodeDefaults` per need, `isLenient` only for non-standard APIs. Serialization Gradle plugin required (`org.jetbrains.kotlin.plugin.serialization`).

DTO rules: always `@Serializable`; `@SerialName("created_at")` when JSON keys differ; default values for optional fields; enums with `@SerialName("active") ACTIVE`; DTOs mirror the API contract — no business logic; never expose DTOs to the domain/UI layer.
```kotlin
@Serializable data class ItemListDto(val items: List<ItemDto>, val total: Int, @SerialName("next_page") val nextPage: String? = null)
@Serializable data class ItemDto(val id: String, val name: String, val status: StatusDto = StatusDto.ACTIVE, @SerialName("created_at") val createdAt: Long)
@Serializable enum class StatusDto { @SerialName("active") ACTIVE, @SerialName("archived") ARCHIVED }
@Serializable data class PagedResponse<T>(val data: List<T>, val page: Int, val limit: Int, val total: Int, @SerialName("has_next") val hasNext: Boolean)
```
Mapping at the repository boundary; domain models have no serialization annotations:
```kotlin
fun ItemDto.toDomain() = Item(id, name, ItemStatus.valueOf(status.name), createdAt)
fun List<ItemDto>.toDomain() = map { it.toDomain() }
fun Item.toEntity() = ItemEntity(...)   // same pattern for Room entities
```
Why separate DTO from domain: (1) API shape changes don't cascade through the app; (2) `@SerialName` doesn't pollute domain; (3) date parsing happens once in mapper (`Instant.parse(createdAt)` / `LocalDateTime.parse`) not throughout; (4) null handling and defaults isolated in mapper. Same for Entity↔Domain: split when Room annotations would leak into shared KMP modules or DTO shapes differ; small app with no network layer may use a single model.

### Paging

Critical performance rules:
1. **PagingData must be a separate Flow, NEVER inside UiState** — wrapping in `data class UiState(val pagingData: PagingData<T>)` causes scroll-to-top on any state change. Use two separate properties: `state: StateFlow<UiState>` + `pagingDataFlow: Flow<PagingData>`.
2. **Never create a new Pager per recomposition** — store the Flow as a `val` in ViewModel.
3. **Always `cachedIn(viewModelScope)`** — prevents data loss on config change.
4. **Always provide stable keys** — `itemKey { it.id }` prevents scroll jumps.
5. **Use `flatMapLatest` for parameter changes** — not `combine` on PagingData flows.

Deps: `androidx.paging:paging-compose` + `paging-common` (3.3.6), `paging-testing` (test). KMP (since 3.3.0-alpha02): `paging-common`/`paging-compose` in `commonMain` (Android/JVM/iOS); `paging-runtime` is Android-only (RecyclerView adapters, not needed in Compose); verify Web/WASM per version.

Core data flow:
```text
PagingSource -> Pager(config, factory) -> Flow<PagingData<T>>
  -> .cachedIn(viewModelScope) -> collectAsLazyPagingItems() -> LazyColumn/Grid/Pager
```
| Component | Role |
|---|---|
| `PagingSource<Key, Value>` | Loads pages from a single source |
| `RemoteMediator` | Coordinates network + local DB |
| `Pager` | Creates `Flow<PagingData>` from config + source |
| `PagingConfig` | Page size, prefetch, placeholders |
| `LazyPagingItems<T>` | Compose wrapper for consuming PagingData |

PagingSource:
```kotlin
class ItemPagingSource(private val api: ItemApi, private val query: String) : PagingSource<Int, ItemDto>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ItemDto> {
        val page = params.key ?: 1
        return try {
            val response = api.getItems(page = page, limit = params.loadSize, query = query)   // suspend — never blocking fetch inside load()
            LoadResult.Page(data = response.items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (response.items.isEmpty()) null else page + 1)
        } catch (e: IOException) { LoadResult.Error(e) }
        catch (e: HttpException) { LoadResult.Error(e) }
    }
    override fun getRefreshKey(state: PagingState<Int, ItemDto>): Int? =
        state.anchorPosition?.let { pos -> state.closestPageToPosition(pos)?.let { it.prevKey?.plus(1) ?: it.nextKey?.minus(1) } }
}
```
Rules: factory must return a **new instance** every call (reuse → crash "PagingSource was re-used"); catch specific exceptions (generic `Exception` hides bugs); `null` prev/next key signals end; cursor APIs → `String` key type with `nextCursor`. Room-backed source: `@Query(...) fun pagingSource(): PagingSource<Int, ItemEntity>` (`room-paging`/`room3-paging` converter).

Pager + ViewModel:
```kotlin
val items: Flow<PagingData<ItemUi>> = Pager(
    config = PagingConfig(pageSize = 20, prefetchDistance = 5, enablePlaceholders = false, initialLoadSize = 40),
    pagingSourceFactory = { repository.itemPagingSource() },
).flow.map { it.map { item -> item.toUi() } }.cachedIn(viewModelScope)
```
| PagingConfig param | Purpose |
|---|---|
| `pageSize` | Items per page (required) |
| `prefetchDistance` | Distance from edge to trigger next load |
| `enablePlaceholders` | Show null placeholders for unloaded items |
| `initialLoadSize` | Items on first request |

Invalidation after mutations — factory returns new instance; Paging reloads from `getRefreshKey`:
```kotlin
class ItemRepository(private val api: ItemApi) {
    private var currentPagingSource: ItemPagingSource? = null
    fun itemPagingSource(query: String = ""): PagingSource<Int, ItemDto> =
        ItemPagingSource(api, query).also { currentPagingSource = it }
    fun invalidate() { currentPagingSource?.invalidate() }
}
```
Filters/search: `combine(_query.debounce(300).distinctUntilChanged(), _statusFilter.distinctUntilChanged()) { q, s -> q to s }.flatMapLatest { (q, s) -> Pager(...).flow.map { it.map(ItemDto::toUi) } }.cachedIn(viewModelScope)`. `distinctUntilChanged()` before `flatMapLatest` avoids redundant Pager creation; `debounce` on text prevents excessive calls; `cachedIn` must come **after** `flatMapLatest`, not inside it; single filter → omit `combine`.

Compose UI:
```kotlin
LazyColumn {
    items(count = pagingItems.itemCount,
        key = pagingItems.itemKey { it.id },
        contentType = pagingItems.itemContentType { "item" }) { index ->
        pagingItems[index]?.let { item -> ItemRow(item = item, ...) }
    }
}
```
| Operation | What it does |
|---|---|
| `pagingItems[index]` | Access item **and** trigger load |
| `pagingItems.peek(index)` | Access **without** triggering load |
| `pagingItems.retry()` | Retry last failed load |
| `pagingItems.refresh()` | Reload all data (never call from composable body) |
| `pagingItems.itemKey { }` | Stable keys |
| `pagingItems.itemContentType { }` | Content type for layout reuse |
Works with all lazy layouts (`LazyColumn`, `LazyVerticalGrid`, `HorizontalPager`). Prefer `items` with `itemKey`/`itemContentType` over `itemsIndexed` — indices shift during prepend.

LoadState:
| State | refresh | append/prepend |
|---|---|---|
| `Loading` | Initial load or pull-to-refresh | Loading next/previous page |
| `Error(throwable)` | Initial load failed | Page load failed |
| `NotLoading(endReached)` | Idle | No more pages / idle |
Pattern: branch on `pagingItems.loadState.refresh` — full-screen loading/error/empty only when `itemCount == 0`; with items, top `LinearProgressIndicator` for refresh and append-row loading/error + `retry()`. With RemoteMediator check `loadState.source.refresh` — convenience `loadState.refresh` may report complete before Room finishes writing (indicator disappears too early). WRONG: `Text(lazyItems[index]!!.title)` — crash on placeholders/errors; RIGHT: render placeholder row for null items, `key = { items[index]?.id ?: "placeholder-$index" }`.

Transformations — apply on the outer Flow **before** `cachedIn` (after = lost on cache hit):
```kotlin
.map { pagingData -> pagingData
    .map { ListItem.ContentItem(it.toUi()) }
    .filter { it.item.status != ItemStatus.DELETED }
    .insertSeparators { before, after -> when {
        before == null -> ListItem.DateHeader("Today")
        after == null -> null
        before.dateGroup != after.dateGroup -> ListItem.DateHeader(after.dateGroup)
        else -> null } } }
.cachedIn(viewModelScope)
```
With `insertSeparators` provide unique keys per type (`"item_${id}"`, `"header_${label}"`) and distinct `contentType` values.

Offline-first with RemoteMediator (`@OptIn(ExperimentalPagingApi::class)`): `initialize()` decides first-load behavior:
| Return value | Behavior |
|---|---|
| `LAUNCH_INITIAL_REFRESH` | Triggers `REFRESH` immediately — fetches network before showing cache. **Default** if not overridden. |
| `SKIP_INITIAL_REFRESH` | Shows cached Room data immediately; network only on user refresh/append. Use when cache is still fresh. |
```kotlin
override suspend fun initialize(): InitializeAction {
    val cacheTimeout = TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS)
    val lastUpdated = db.remoteKeyDao().getLastUpdated("items") ?: 0L
    return if (System.currentTimeMillis() - lastUpdated < cacheTimeout) InitializeAction.SKIP_INITIAL_REFRESH
    else InitializeAction.LAUNCH_INITIAL_REFRESH
}
override suspend fun load(loadType: LoadType, state: PagingState<Int, ItemEntity>): MediatorResult {
    val page = when (loadType) {
        LoadType.REFRESH -> 1
        LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
        LoadType.APPEND -> db.remoteKeyDao().getRemoteKey("items")?.nextPage
            ?: return MediatorResult.Success(endOfPaginationReached = true)
    }
    return try {
        val response = api.getItems(page = page, limit = state.config.pageSize)
        db.withTransaction {
            if (loadType == LoadType.REFRESH) { db.itemDao().clearAll(); db.remoteKeyDao().delete("items") }
            db.itemDao().insertAll(response.items.map { it.toEntity() })
            db.remoteKeyDao().insert(RemoteKey(id = "items",
                nextPage = if (response.items.isEmpty()) null else page + 1,
                lastUpdated = System.currentTimeMillis()))
        }
        MediatorResult.Success(endOfPaginationReached = response.items.isEmpty())
    } catch (e: IOException) { MediatorResult.Error(e) } catch (e: HttpException) { MediatorResult.Error(e) }
}
// Pager wiring:
Pager(config = PagingConfig(pageSize = 20), remoteMediator = ItemRemoteMediator(api, db),
      pagingSourceFactory = { db.itemDao().pagingSource() }).flow.cachedIn(viewModelScope)
// Remote keys table:
@Entity(tableName = "remote_keys")
data class RemoteKey(@PrimaryKey val id: String, val nextPage: Int?, val lastUpdated: Long = System.currentTimeMillis())
@Dao interface RemoteKeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(key: RemoteKey)
    @Query("SELECT * FROM remote_keys WHERE id = :id") suspend fun getRemoteKey(id: String): RemoteKey?
    @Query("SELECT lastUpdated FROM remote_keys WHERE id = :id") suspend fun getLastUpdated(id: String): Long?
    @Query("DELETE FROM remote_keys WHERE id = :id") suspend fun delete(id: String)
}
```

MVI dual-flow: ViewModel exposes `state: StateFlow<ItemListState>` (filters, selection, errors) + `items: Flow<PagingData<ItemUi>>` separately; route collects both:
```kotlin
@Composable fun ItemListRoute(viewModel: ItemListViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagingItems = viewModel.items.collectAsLazyPagingItems()
    ItemListScreen(state = state, pagingItems = pagingItems, onEvent = viewModel::onEvent)
}   // screen composable is dumb — props in, callbacks out
```

2026 presenter/API variants (compose-kotlin): WRONG `collectAsLazyPagingItems()` in ViewModel (Compose API in wrong layer, stale paging — banned row 22). RIGHT: expose `val pagingFlow: Flow<PagingData<ItemUi>> = _query.flatMapLatest { repository.pagerFor(it).flow }.map { it.map(ItemDto::toUi) }.cachedIn(viewModelScope)`, collect in presenter with `vm.pagingFlow.asState(initialList = emptyList()).collectAsStateWithLifecycle()` for a stable first frame (`Flow<PagingData<T>>.asState(initialList)` caches the latest LazyPagingItems-equivalent snapshot — check your `paging-compose` BOM pin); explicit loads via `pager?.append()` / `pager?.prepend()` (CoroutineWorker-scoped launch), never by recreating the Pager per scroll edge.

Paging decision matrix:
| Scenario | Pattern |
|---|---|
| Network-only list | `PagingSource` |
| Room + network sync | `RemoteMediator` + Room DAO `PagingSource` |
| Search query changes | `flatMapLatest` on query → new `Pager` |
| UI collection | `asState(initialList)` + `collectAsStateWithLifecycle` (or `collectAsLazyPagingItems()`) |
| User taps "Load more" | `pager.append()` |
| Pull-to-refresh | `LazyPagingItems.refresh()` or new refresh trigger |

Testing: call `pagingSource.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))` directly, assert `LoadResult.Page` data/`prevKey == null`/`nextKey` or `LoadResult.Error`; `viewModel.items.asSnapshot { scrollTo(index = 30) }` (paging-testing) asserts loaded content; `dtos.asPagingSourceFactory().invoke()` + `TestPager(PagingConfig(pageSize = 10), source)` then `pager.refresh() as LoadResult.Page` for transformations.

Paging anti-patterns (verbatim table):
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

### Dependency Injection

Framework decision:
| Criterion | Hilt | Koin |
|---|---|---|
| Platform | Android-only | Multiplatform (Android, iOS, Desktop, Web) |
| Dependency resolution | Compile-time | Runtime (DSL) or compile-time (Koin Annotations + KSP) |
| Error detection | Build-time | Runtime — use `verify()` in tests; KSP annotations add compile-time checks |
| Setup complexity | Higher (Gradle plugins, annotations) | Lower (DSL modules); annotations optional |
| Compose Multiplatform | Not supported | Full support |
| Build time impact | KSP adds ~10-20% | Zero (no code gen) |
| Performance | Zero runtime reflection | Reflection at startup (small) |
| ViewModel injection | `hiltViewModel()` | `koinViewModel()` |
| Testing | `@TestInstallIn`, `@UninstallModules` | `loadKoinModules()` override / module overrides |
| Community | Google-backed, massive | JetBrains-friendly, growing |

Default: Android-only → Hilt (Koin valid if team prefers or future multiplatform). CMP → Koin (Hilt has no non-Android targets). Choose Hilt when: Android-only + compile-time safety + Dagger familiarity. Choose Koin when: KMP/CMP, simpler setup, small-to-medium app, rapid prototyping.

Shared concepts (any framework): constructor injection is the default — field injection (`@Inject lateinit var`) couples class to DI and hurts testing; bind interfaces to implementations (swap fakes without mocking DI); align scope with actual lifetime (over-scoping wastes memory, under-scoping duplicates: Singleton = app lifetime/API client/database/analytics; Activity-retained = survives config change/session, auth state; ViewModel-scoped = feature calculators/validators; Factory = stateless formatters/mappers); organize modules **by feature, not by type** (`ProductModule` → repository+calculator+validator+ViewModel; `CoreModule` → API client, database, platform bindings; combine in app module; platform bindings in `androidMain`/`iosMain`); test by swapping real implementations via DI config, don't mock the DI framework itself.

Hilt (Android-only): plugins `com.google.dagger.hilt.android` + KSP; deps `hilt-android`, `ksp(hilt-compiler)`, `androidx.hilt:hilt-navigation-compose`. `@HiltAndroidApp class MyApplication : Application()` — REQUIRED and declared in `AndroidManifest` (`android:name=".MyApplication"`). KSP never kapt.
```kotlin
// @Binds — interface→impl (abstract class module; zero runtime overhead, generates less code)
@Module @InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}
// @Provides — you construct it: third-party, builders (Room, Retrofit, Ktor), config logic (object module)
@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun provideApiClient(): ApiClient = ApiClient()
    @Provides @Singleton fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "app.db").addMigrations(...).build()
    @Provides fun provideItemDao(db: AppDatabase): ItemDao = db.itemDao()
}
// Feature-scoped:
@Module @InstallIn(ViewModelComponent::class)
object ProductModule { @Provides @ViewModelScoped fun provideProductCalculator(): ProductCalculator = ProductCalculator() }
```
| Scenario | Use |
|----------|-----|
| Interface → Implementation | `@Binds` |
| Third-party library object | `@Provides` |
| Complex construction with params | `@Provides` |
| Builder pattern (Room, Retrofit, Ktor) | `@Provides` |
Scopes:
| Scope | Lifecycle | Use case |
|---|---|---|
| `@Singleton` | Application | API clients, databases, shared preferences |
| `@ActivityRetainedScoped` | Activity (survives config change) | User session, auth state (default for `@HiltViewModel` — don't re-declare) |
| `@ViewModelScoped` | ViewModel | Feature-specific services, calculators |
| `@ActivityScoped` | Activity instance | Activity-bound resources (rare in Compose) |
| `@FragmentScoped` | Fragment instance | Fragment-bound resources (rare in Compose) |
| No scope | New instance per injection | Stateless helpers, mappers, UseCases |
Rule: scope as narrow as possible; `@Singleton` only for truly shared, expensive objects; injecting a `@Singleton` into a narrower scope = Dagger error.
ViewModels:
```kotlin
@HiltViewModel
class ProductViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val savedStateHandle: SavedStateHandle,   // free with Hilt; populated with nav args
) : ViewModel() { private val itemId: String = checkNotNull(savedStateHandle["itemId"]) }
// Caller-supplied runtime params — only when SavedStateHandle can't carry the data:
@HiltViewModel(assistedFactory = DetailViewModel.Factory::class)
class DetailViewModel @AssistedInject constructor(
    private val repository: ItemRepository, @Assisted private val itemId: String,
) : ViewModel() { @AssistedFactory interface Factory { fun create(itemId: String): DetailViewModel } }
val viewModel = hiltViewModel<DetailViewModel, DetailViewModel.Factory> { factory -> factory.create(itemId) }
// Compose: ProductRoute(viewModel: ProductViewModel = hiltViewModel()) — never viewModel()/manual instantiation;
// @AndroidEntryPoint on MainActivity. Prefer SavedStateHandle for nav args (simpler, survives process death).
// Nav 2 graph-scoped VM: hiltViewModel<CheckoutViewModel>(navController.getBackStackEntry("checkout_graph"))
```
`@AndroidEntryPoint` required on every Android class that injects: Activity, Fragment, View, Service, BroadcastReceiver — forgetting it → `lateinit var not initialized` crash. Non-Hilt classes (ContentProvider, AccessibilityService):
```kotlin
@EntryPoint @InstallIn(SingletonComponent::class)
interface AnalyticsEntryPoint { fun analyticsTracker(): AnalyticsTracker }
EntryPointAccessors.fromApplication(context.applicationContext, AnalyticsEntryPoint::class.java).analyticsTracker()
```
Qualifiers for multiple bindings of same type:
```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Module @InstallIn(SingletonComponent::class)
object DispatchersModule { @Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO }
```
WorkManager: `@HiltWorker class SyncWorker @AssistedInject constructor(@Assisted appContext: Context, @Assisted params: WorkerParameters, private val repository: ...) : CoroutineWorker(...)`.
Hilt testing: `androidTestImplementation(hilt-android-testing)`, `kspAndroidTest(hilt-compiler)`; `@HiltAndroidTest` + `@get:Rule(order = 0) HiltAndroidRule(this)` (with `hiltRule.inject()` in `@Before`) + compose rule (`order = 1`); `@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])` fake module. Plain ViewModel unit tests need no Hilt — pass fakes to the constructor (fakes over mocks: fakes test the contract, mocks test interaction).
Common Dagger compile errors:
| Error | Cause | Fix |
|---|---|---|
| `[Dagger/MissingBinding]` | Missing @Provides / @Binds | Add module with binding |
| `[Dagger/IncompatiblyScopedBindings]` | Singleton injected into narrower scope | Match scopes or remove scope annotation |
| `abstract @Provides` | @Provides in abstract class | Use `object` for @Provides, `abstract class` for @Binds |
| `@Binds methods must have only one parameter` | Wrong @Binds signature | `abstract fun bind(impl: Impl): Interface` |
| `lateinit var not initialized` | Missing @AndroidEntryPoint | Add @AndroidEntryPoint to class |
| `Cannot be provided without @Inject or @Provides` | Interface binding missing | Add @Binds module |
Hilt anti-patterns:
| Anti-pattern | Why it is harmful | Better approach |
|---|---|---|
| Injecting Context into ViewModel | Lifecycle mismatch, leaks | Use `@ApplicationContext` or move platform code to Repository |
| Injecting Activity/Fragment into ViewModel | Memory leaks | Pass data via SavedStateHandle or route arguments |
| `@Inject` on ViewModel without `@HiltViewModel` | ViewModel not managed by Hilt | Always use `@HiltViewModel` with `@Inject constructor` |
| Manual ViewModel instantiation | Bypasses Hilt injection | Use `hiltViewModel()` in Compose |
| Installing ViewModel dependencies in `SingletonComponent` | Unnecessary lifecycle extension | Use `ViewModelComponent` or `ViewModelScoped` |
Plus (compose-kotlin): `@Singleton` on ViewModel → wrong, use `@HiltViewModel`; injecting `Context` directly → `@ApplicationContext`/`@ActivityContext`; module per class → one module per layer (don't create DataStoreModule + PreferencesModule + SettingsModule for 3 related bindings); `@Provides` when `@Binds` works → extra allocation/less efficient; scoping everything `@Singleton`; injecting ViewModel into ViewModel → never — extract shared state to a `@Singleton` repository/manager.

Koin (multiplatform): BOM + `koin-core`, `koin-compose` (`koinInject`), `koin-compose-viewmodel` (`koinViewModel`), `koin-compose-viewmodel-navigation` (Nav 3), Android-only convenience `koin-androidx-compose`. Platform support: Android, iOS, Desktop full; Web experimental.
```kotlin
// commonMain
fun initKoin(config: KoinAppDeclaration? = null) { startKoin { config?.invoke(this); modules(appModule, featureModules) } }
// Android Application.onCreate: initKoin { androidContext(this@MyApplication); androidLogger() }
// iOS (from Swift): InitKoinKt.doInitKoin(config: nil)  — "do" prefix because `init` is reserved
// Compose-managed alternative: KoinApplication(configuration = koinConfiguration { modules(appModule) }) { MainScreen() }
val appModule = module {
    single<UserRepository> { UserRepositoryImpl(get()) }   // app lifetime: stateless services, repositories, API clients, databases
    factory { ProductValidator() }                          // new instance per call: stateful/short-lived
    scoped { CheckoutState() }                              // bound to a Koin scope
    viewModelOf(::ProductViewModel)                         // ViewModel lifecycle: survives recomposition + config change
    // Compiler-plugin DSL (auto-wiring): single<ProductCalculator>(); single<UserRepositoryImpl>() bind UserRepository::class; viewModel<ProductViewModel>()
}
val appModule = module { includes(productModule, settingsModule, coreModule) }   // feature-first
```
Annotations (KSP + `koin-annotations`, `koin-ksp-compiler` per target; `ksp { arg("KOIN_USE_COMPOSE_VIEWMODEL", "true"); arg("KOIN_CONFIG_CHECK", "true") }`): `@Single`, `@Factory`, `@KoinViewModel`, `@InjectedParam` (↔ `parametersOf`), `@Module` + `@ComponentScan`; use generated `modules(AppModule().module)`.
Platform-specific impls via `expect val platformModule: Module` (androidMain binds Android impl, iosMain iOS); for platform deps in expect/actual classes use `KoinComponent` with `inject()` — justified only because constructors must match across platforms; avoid `KoinComponent` elsewhere. Bridge choice: interface + DI for services with lifecycle/state/async (testable, fakeable); `expect/actual` function for stateless platform facts (UUID, platform name); `expect class` + `actual typealias` rare — when platform type already matches contract. `commonMain` DI heavy/async/hardware services (GPS, biometrics, keystore) → interface + Koin platform impls; tiny sync primitives → expect/actual. **Before claiming a dep works in `commonMain`, verify multiplatform artifacts exist** (Maven `-jvm`, `-iosarm64`, `-iosX64` classifiers); if unverifiable, say so and use platform placement or wrapper interfaces.
Compose injection:
```kotlin
val service: MyService = koinInject()                                  // non-VM deps in @Composable (default param = testable)
val viewModel = koinViewModel<HomeViewModel>()                         // lifecycle-aware
val viewModel = koinViewModel<DetailViewModel> { parametersOf(itemId) }
val viewModel = koinViewModel<DetailViewModel>(key = "detail_$itemId", parameters = { parametersOf(itemId) })  // keyed per entity
```
| Function | Platform | When to use |
|---|---|---|
| `koinInject<T>()` | All | Non-ViewModel dependencies inside `@Composable` |
| `koinViewModel<T>()` | All | ViewModel — lifecycle-aware, survives recomposition |
| `koinActivityViewModel<T>()` | Android | Share ViewModel across all composables in an Activity |
| `koinEntryProvider<T>()` | All | Wire Nav 3 `NavDisplay` to Koin `navigation<T>` entries |
| `parametersOf(...)` | All | Pass runtime values to `koinViewModel` or `koinInject` |
| `get<T>()` | All | Resolve inside `module { }` only — never in composables |
Nav 3: `navigation<HomeRoute> { HomeScreen(viewModel = koinViewModel()) }`, `NavDisplay(..., entryProvider = koinEntryProvider())`. Scopes: `scope<CheckoutFlow> { scoped { CheckoutState() }; viewModel<CheckoutViewModel>() }` (all platforms); Android `activityRetainedScope { }` survives config change.
Testing: `appModule.verify(extraTypes = listOf(SavedStateHandle::class))` — dry-run graph check catches missing declarations before runtime (`koin-test`).
Koin anti-patterns:
| Anti-pattern | Why it is harmful | Better approach |
|---|---|---|
| `factory { MyViewModel() }` for ViewModels | Not lifecycle-aware, new instance on recomposition | `viewModelOf(::MyViewModel)` |
| Not using `parametersOf` for runtime params | Constructor params unresolved | `koinViewModel { parametersOf(id) }` |
| `koin-compose` without `koin-compose-viewmodel` | `koinViewModel()` unavailable | Add `koin-compose-viewmodel` |
| Calling `startKoin` multiple times | `KoinAppAlreadyStartedException` | Call once, use `loadKoinModules` for dynamic additions |
| Android `Context` in `commonMain` modules | Breaks multiplatform | `expect/actual` platform modules |

Networking DI wiring: provide `HttpClient` + `HttpClientEngine` as singletons; engine chosen via `expect/actual` platform modules (OkHttp Android, Darwin iOS, CIO JVM). `single { createHttpClient(engine = get(), baseUrl = "...") }` (Koin) / `@Provides @Singleton` (Hilt). `ImageLoader`, `RoomDatabase`, `DataStore`, `WorkManager`, `NetworkMonitor` (`@Binds` interface→impl), Firebase clients — all app-level singletons.

### Image Loading

Coil 3 — Coil 3 does NOT include network loading by default: `io.coil-kt.coil3:coil-compose` + exactly ONE network integration (`coil-network-okhttp` Android/JVM, `coil-network-ktor2`/`coil-network-ktor3` multiplatform). `coil-network-okhttp` is **not transitive — network images fail silently without it** (banned row 23). Extras: `coil-svg`, `coil-gif`, `coil-video`. Ktor networking needs platform engines per target.

API choice:
| Use case | Best API | Why |
|---|---|---|
| Most image rendering in UI | `AsyncImage` | Best default; resolves image size from constraints |
| Need a `Painter` / manual request restart / state observation | `rememberAsyncImagePainter` | More control, lower-level; MUST provide size resolver (`rememberConstraintsSizeResolver`) to avoid always loading original size |
| Composable slots per loading state + first-frame state correctness | `SubcomposeAsyncImage` | Slot API with immediate state, but slower |
Performance: `SubcomposeAsyncImage` uses subcomposition — less suitable for dense `LazyColumn`/`LazyGrid` cells; prefer `AsyncImage` for list-heavy screens. (compose-kotlin framing: `AsyncImage` for icons/known-size; `SubcomposeAsyncImage` for hero images/skeleton UI.)

Default pattern:
```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalPlatformContext.current).data(imageUrl).crossfade(true).build(),
    placeholder = painterResource(Res.drawable.placeholder),
    error = painterResource(Res.drawable.image_error),
    fallback = painterResource(Res.drawable.image_fallback),
    contentDescription = title,        // null only for decorative images
    contentScale = ContentScale.Crop,
    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
)
// WRONG: bare AsyncImage(model = url, contentDescription = null) — layout jump + no error state
```

ImageLoader: one shared `ImageLoader` per app process — multiple loaders fragment memory/disk caches and reduce hit rates; never build one per screen/composable (WRONG: `ImageLoader.Builder(LocalContext.current).build()` inside composable):
```kotlin
setSingletonImageLoaderFactory { context ->
    ImageLoader.Builder(context)
        .crossfade(true)
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }        // 25% of RAM
        .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("image_cache"))
            .maxSizePercent(0.02) /* or .maxSizeBytes(50L * 1024 * 1024) */ .build() }
        .build()
}
// Or Application : ImageLoaderFactory { override fun newImageLoader() = injectedFactory.newImageLoader() }
// Or Hilt @Provides @Singleton fun imageLoader(...): ImageLoader
```
Full component registration: `.components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })); add(VideoFrameDecoder.Factory()); add(SvgDecoder.Factory()); add(ImageDecoderDecoder.Factory() /* animated GIF/AVIF/WebP */) }` + `.respectCacheHeaders(false)` when servers misconfigure `Cache-Control`. Libraries: prefer `coil-core` and pass your own `ImageLoader` — don't override the app singleton.

Pipeline (executes in order): `Interceptor` → `Mapper` → `Keyer` → `Fetcher` → `Decoder`; register custom components once at `ImageLoader` build: `.components { add(CustomCacheInterceptor()); add(ItemMapper()); add(ItemKeyer()); add(PartialUrlFetcher.Factory()); add(SvgDecoder.Factory()) }`.
| Need | Customize | Why |
|---|---|---|
| Add request retry/short-circuit/global policy | `Interceptor` | Wraps entire pipeline; can modify/proceed/return early. Cross-cutting: timeouts, retries, custom cache layer, metrics. |
| Accept custom model type in `.data(...)` | `Mapper` | Normalizes domain data to a supported type (for example `ProductImage` → URL string). |
| Keep custom data memory-cacheable | `Keyer` | Stable memory cache key segment for custom models. If a custom `Fetcher` introduces a new data type, add a matching `Keyer` so memory caching works. |
| Support custom source/protocol | `Fetcher.Factory<T>` | Data transport: custom scheme, signed URLs, alternate client. |
| Decode custom encoded data/format | `Decoder.Factory` | Converts fetched source to a renderable image. |
| Add auth headers for all image requests | Network fetcher + client interceptor | Centralized networking behavior. |
| Per-request dynamic headers | `ImageRequest.httpHeaders(...)` | Scoped request-level networking metadata. |
For HTTP cache semantics with OkHttp register `CacheControlCacheStrategy` with the network fetcher. Pipeline anti-patterns: registering components per screen / duplicating what request options already cover (`httpHeaders`, cache policy, size resolver) → fragments caches; custom `Fetcher` without a stable `Keyer`, volatile data (timestamps, random values) in cache keys → poor memory cache hit rate; heavy blocking work in `Interceptor` without bounds/timeouts; platform-only types in `commonMain` pipeline contracts. CMP: model wrappers/mapping in `commonMain`; OkHttp/Android-only client setup in platform source sets; Ktor network for broad CMP.

Caching: default request cache policies are enabled; override `memoryCachePolicy` / `diskCachePolicy` / `networkCachePolicy` only for non-default behavior (e.g. preloading: `CachePolicy.ENABLED`). Stable keys for list→detail / shared elements:
```kotlin
ImageRequest.Builder(LocalPlatformContext.current).data(url)
    .memoryCacheKey("image-$id").placeholderMemoryCacheKey("image-$id").build()
// placeholderMemoryCacheKey reuses an in-memory result as placeholder → avoids visual flashes
// WRONG: .memoryCacheKey("$System.currentTimeMillis()-${article.id}") → defeats caching, list flicker
// GOOD: memoryCacheKey(article.imageUrl) + diskCacheKey(article.imageUrl)
```
Preload upcoming pages: `LaunchedEffect(imageUrls) { imageUrls.forEach { imageLoader.execute(ImageRequest.Builder(context).data(url).memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).build()) } }` (`LocalImageLoader.current`). Cache management: `SingletonImageLoader.get(context).diskCache?.clear()` / `memoryCache?.clear()` (logout); evict one URL: `diskCache?.remove(imageUrl)` + `memoryCache?.remove(MemoryCache.Key(imageUrl))`.

Downsampling/perf: tell Coil the target size — `.size(400, 400)` / `.size(80.dp.toPxInt(), 80.dp.toPxInt())` so it downsamples before decoding (loading 2000px into an 80dp cell wastes memory); enforce fixed height on the composable (`.fillMaxWidth().height(200.dp)`) so placeholders don't cause layout shift; keep item size predictable; stable LazyColumn keys + stable cache keys together.

Transformations: `.transformations(...)` (`CircleCropTransformation()`, `RoundedCornersTransformation(radius)`, `BlurTransformation(radius = 20, scale = 0.5f)` downsample-then-blur for perf; multiple applied in order) ONLY for pixel-level changes to decoded output — they materialize bitmaps and can collapse animated images to one frame. Prefer `Modifier.clip` / shapes for UI-only effects.

State observation (2026 breaking changes): `painter.state` is a StateFlow — `val state = painter.state.value` is **BANNED in Coil 3.4+** (not observable; UI won't recompose): `val state by painter.state.collectAsState()` then `when (state) { is AsyncImagePainter.State.Loading/Success/Error, AsyncImagePainter.State.Empty }`. The `equalityDelegate` parameter was removed from `AsyncImage` — provide via `CompositionLocalProvider(LocalAsyncImageModelEqualityDelegate provides DefaultModelEqualityDelegate())`. In `SubcomposeAsyncImage` content scope use `painter.state` + `SubcomposeAsyncImageContent()`, retry via `painter.restart()`.

Formats: SVG — add `coil-svg`, auto-detected once on classpath (explicit `SvgDecoder.Factory()` only for non-default wiring). Video thumbnails — `VideoFrameDecoder.Factory()` + `.videoFrameMillis(1000)`. Animated GIF/WebP/AVIF — `ImageDecoderDecoder.Factory()`.

Compose Multiplatform resources: `AsyncImage(model = Res.getUri("drawable/sample.jpg"), ...)` — string URIs via `Res.getUri`; direct handles like `Res.drawable.someImage` are not currently passed directly as Coil models.

Preview/test/debug: Compose preview has no network — `LocalAsyncImagePreviewHandler` injects deterministic preview images; enable `DebugLogger` only in debug builds; inject a custom/fake `ImageLoader` in large apps instead of global singleton state.

### Anti-Patterns

Cross-cutting banned lookup (AI-hallucination rows relevant to data/network/DI; grep WRONG column when auditing):
(Also relevant from adjacent rows: 2 `runBlocking` on main; 4 `collectAsState()` on Android → `collectAsStateWithLifecycle()`; 5 `_state.value =` → `_state.update { }`.)

Universal data-layer invariants (deduplicated across all sources):
- Repository never collects; it returns `Flow` / `suspend` results. ViewModel/UseCase collects.
- Single source of truth: UI observes local DB only; network writes into DB; DTOs/`@Entity`/raw `Preferences` never reach UI; map at repository boundary.
- One instance per resource: `HttpClient`, `RoomDatabase`, `DataStore`, `ImageLoader`, `startKoin` — all app singletons via DI.
- Never swallow `CancellationException`; catch specific exception types (`IOException`, `HttpException`, timeouts), never bare `Exception` at API/PagingSource boundaries.
- All IO off main: suspend/Flow DAOs, `.await()` coroutines for Firebase, DataStore `edit` from coroutines; Room/Retrofit-suspend/Ktor switch dispatchers internally — don't double-wrap.
- No hardcoded config: base URLs / keys via `BuildConfig` or injected config; never commit or leak secrets (tokens → encrypted storage; `sanitizeHeader`/`Level.NONE` in release).
- KMP: verify artifacts publish multiplatform targets before claiming `commonMain`; blocking DAOs, `Context`, `collectAsStateWithLifecycle`, Hilt all break shared code.

### Checklists

Room:
- [ ] KSP for `room-compiler` (never kapt); Room 3 = KSP-only
- [ ] Every `WHERE`/`ORDER BY`/`JOIN ON`/FK column indexed (not more); composite order = most selective first
- [ ] Reads = `Flow`, writes = `suspend`; no blocking DAO functions (hard rule on KMP/Room 3)
- [ ] Projections instead of `SELECT *`; `LIMIT` or Paging for unbounded results
- [ ] `@Upsert` not `REPLACE` with foreign keys; batch writes in `@Transaction`; `@Transaction` on all `@Relation` queries
- [ ] `exportSchema = true` + schema dir committed; explicit `Migration`/`AutoMigration`; `fallbackToDestructiveMigration()` only in early dev
- [ ] Simple TypeConverters only (timestamp/enum); `@TypeConverters` registered on `@Database`; UI-transient flags not in entities
- [ ] `RoomDatabase` DI singleton; entity↔domain mapping at repository boundary
- [ ] DAO tests (in-memory + Turbine) and `MigrationTestHelper` tests

DataStore:
- [ ] One singleton instance per file; immutable `T`; SingleProcess/MultiProcess not mixed
- [ ] Typed keys in one object; `.catch { IOException → emptyPreferences() }` on every `data` Flow
- [ ] `ReplaceFileCorruptionHandler` (or `CorruptionException` in serializer); reads via `stateIn(WhileSubscribed(5_000))` in VM, never in composables
- [ ] `SharedPreferencesMigration`/`produceMigrations`, not manual dual writes; delete legacy file after
- [ ] Secrets in EncryptedSharedPreferences/Keystore, never plain Preferences; >100 entries or need queries → Room

Networking:
- [ ] One shared client (singleton) with platform engine; base URL from `BuildConfig`/DI
- [ ] `ignoreUnknownKeys = true`; timeouts set (Ktor `HttpTimeout` / OkHttp connect+read+write); no default "no timeout"
- [ ] Logging: `BuildConfig.DEBUG`/`LogLevel.HEADERS` in release, `sanitizeHeader("Authorization")`, `HttpLoggingInterceptor.Level.NONE` in prod
- [ ] Error strategy chosen: `expectSuccess = true` + specific catches, or `false` + status inspection via `safeRequest` wrapper
- [ ] Plugin order `ContentNegotiation → Auth → HttpRequestRetry → HttpTimeout → ContentEncoding`; `markAsRefreshTokenRequest()` or isolated refresh client
- [ ] All API functions `suspend`; typed response bodies; DTO→domain mapped in repository; exceptions mapped to domain errors
- [ ] Retry: exponential backoff + max attempts + jitter; 4xx not retried; `CancellationException` re-thrown
- [ ] MockEngine tests reuse production `createHttpClient` factory

Offline-first:
- [ ] Room = single source of truth; UI never observes network; cached data emitted immediately, refresh in background
- [ ] Optimistic writes with rollback; sync metadata on entities (`syncStatus`, `lastModified`, `serverVersion`, soft delete); pending-operations queue for offline writes
- [ ] WorkManager: unique names, `NetworkType.CONNECTED` constraint, exponential backoff, periodic ≥ 15 min, `ExistingPeriodicWorkPolicy.KEEP`, Data ≤ 10KB, progress via `setProgress` + `getWorkInfoByIdFlow`
- [ ] Conflict policy decided (server-wins / client-wins / LWW / manual); "last synced" indicator shown; test offline scenarios with fakes/TestDriver

Paging:
- [ ] `PagingData` Flow separate from UiState; Pager stored as `val`; `cachedIn(viewModelScope)` after `flatMapLatest`
- [ ] `pagingSourceFactory` returns new instance; specific exceptions → `LoadResult.Error`; null keys = end of list
- [ ] `itemKey` + `itemContentType`; `insertSeparators` keys unique per type; transformations before `cachedIn`
- [ ] LoadState handled (refresh/append/prepend; `loadState.source.refresh` with RemoteMediator); `refresh()`/`retry()` only from handlers, not composable body
- [ ] Tests: `LoadParams.Refresh` direct call, `asSnapshot`, `asPagingSourceFactory`

DI:
- [ ] Constructor injection only; interfaces bound to impls (`@Binds`/`single<T>`); qualifiers (`@IoDispatcher`) for same-type duplicates
- [ ] Scopes match lifetimes; nothing over-scoped; feature-first module organization; expect/actual (Koin) for platform impls
- [ ] Hilt: `@HiltAndroidApp` + manifest entry, `@AndroidEntryPoint` on injecting Android classes, `hiltViewModel()`, `@TestInstallIn` fakes
- [ ] Koin: `viewModelOf` for VMs, `parametersOf` for runtime args, `verify()` graph test, `startKoin` once
- [ ] No Context/Activity in ViewModels; no ViewModel→ViewModel injection; nav params via `SavedStateHandle` (assisted inject only if it can't carry data)

Coil (source checklist, verbatim):
- [ ] `coil-network-okhttp:3.4.0` on classpath
- [ ] `contentDescription` set (or explicitly decorative)
- [ ] `painter.state.collectAsState()` not `.value`
- [ ] `LocalAsyncImageModelEqualityDelegate` for custom equality
- [ ] Singleton `ImageLoader` with memory + disk cache limits
Plus: request `.size(...)` matching display bounds; fixed-height modifier (no layout shift); stable cache keys shared between list/detail with `placeholderMemoryCacheKey`; `Modifier.clip` preferred over bitmap transformations; library code passes its own loader instead of touching the singleton.

Dependencies (claude-android-ninja): prefer existing version-catalog entries, versions centralized in `[versions]` + `version.ref`, bundles for grouped libs, BOMs for Compose/Firebase (no explicit versions on BOM-managed deps); evaluate new libs for stability (avoid alpha/beta in production — Hilt & Coroutines especially), maintenance, license (Apache 2.0/MIT preferred), APK size; `implementation` over `api` unless types are public API; `-ktx` AndroidX extensions; never `com.android.support.*`; prefer Retrofit for Android-only REST, Ktor for KMP, Coil over Glide for Compose, kotlinx-serialization over Gson.
