# Chronometers Demo Feature — Design

Date: 2026-06-30
Status: Approved (design), pending spec review

## Goal

Add a chronometers feature to the demo app that stress-tests the full `:redux`
composition surface in one screen: a single root `Store` (`AppFeature`) that owns
**two** child features via `scope` — a `Chronometers` feature and the existing
`Universities` search — where `Chronometers` is itself the parent of a dynamic
`0..X` collection of `Chronometer` children via `forEach`. This exercises `scope`
(twice), `forEach`, nested effect lifting across two levels, per-element
cancellable effects, and parent broadcast — end to end under one store.

## Composition

```
AppFeature                      ← the single Store / ViewModel
├── Chronometers   (scope)      ← Add / Remove / PlayAll / PauseAll / ResetAll
│      └── Chronometer × N  (forEach)  ← Play / Pause / Reset / Tick
└── Universities   (scope)      ← existing reducer, logic untouched
```

Root reducer:
`appBase.scope(uniScope, universitiesReducer).scope(chronoScope, chronometersReducer)`
where `chronometersReducer = chronometersOwnReducer.forEach(chronoForEachScope, chronometerReducer)`.

There is exactly **one** store — `AppStore : Store<AppState, AppAction>` — and it
is the only `ViewModel`.

## Chronometer (leaf feature)

```kotlin
data class ChronometerState(
    override val id: Int,
    val elapsedSeconds: Int = 0,
    val isRunning: Boolean = false,
) : Identifiable<Int>

sealed interface ChronometerAction {
    data object Play : ChronometerAction
    data object Pause : ChronometerAction
    data object Reset : ChronometerAction
    data object Tick : ChronometerAction
}
```

Reducer (`ChronometerReducer : Reducer<ChronometerState, ChronometerAction>`):
- `Play` → `state.copy(isRunning = true)` +
  `Effect.run { while (true) { delay(1_000); emit(Tick) } }.cancellable(state.id, cancelInFlight = true)`.
  `cancelInFlight = true` so re-issuing `Play` never leaves two tick loops running
  for the same id.
- `Pause` → `state.copy(isRunning = false)` + `Effect.cancel(state.id)`.
- `Reset` → `state.copy(elapsedSeconds = 0, isRunning = false)` + `Effect.cancel(state.id)` (zero + pause).
- `Tick` → `state.copy(elapsedSeconds = state.elapsedSeconds + 1)`.

The tick effect keys on `state.id` (the chronometer's `Int` id), which is unique
across the collection and does not collide with the universities `"search"` id in
the store-global cancel namespace.

## Chronometers (collection-parent feature)

```kotlin
data class ChronometersState(
    val chronometers: List<ChronometerState> = emptyList(),
    val nextId: Int = 0,
)

sealed interface ChronometersAction {
    data class Chronometer(val id: Int, val action: ChronometerAction) : ChronometersAction // element-addressed
    data object Add : ChronometersAction
    data class Remove(val id: Int) : ChronometersAction
    data object PlayAll : ChronometersAction
    data object PauseAll : ChronometersAction
    data object ResetAll : ChronometersAction
}
```

Own reducer (runs first, then `.forEach`):
- `Add` → append `ChronometerState(id = nextId, isRunning = true)`, `nextId + 1`,
  and **auto-start** the new one via `Effect.send(Chronometer(newId, ChronometerAction.Play))`.
- `Remove(id)` → `chronometers.filterNot { it.id == id }` +
  `Effect.cancel(id)` (stops the removed element's tick — the operator does not
  auto-cancel on removal, so the parent does it explicitly).
- `PauseAll` → `Effect.merge(chronometers.map { Effect.send(Chronometer(it.id, ChronometerAction.Pause)) })`.
- `PlayAll` → `Effect.merge(chronometers.map { Effect.send(Chronometer(it.id, ChronometerAction.Play)) })`.
- `ResetAll` → `Effect.merge(chronometers.map { Effect.send(Chronometer(it.id, ChronometerAction.Reset)) })`.
- `Chronometer(id, action)` → `ReduceResult(state)` (observed only; element routing
  is done by `forEach`).

Broadcast is implemented by **re-dispatching child actions** (fan-out via
`Effect.send`), not by mutating each child's state directly: it reuses each child's
own Play/Pause/Reset logic (DRY) and exercises the effect round-trip through
`forEach`.

`forEach` descriptor:
```kotlin
object : ForEachScope<ChronometersState, ChronometersAction, ChronometerState, ChronometerAction, Int> {
    override val toChildren = { s: ChronometersState -> s.chronometers }
    override val fromChildren = { s: ChronometersState, c: List<ChronometerState> -> s.copy(chronometers = c) }
    override val toChildAction = { a: ChronometersAction -> (a as? ChronometersAction.Chronometer)?.let { it.id to it.action } }
    override val embedChildAction = { id: Int, a: ChronometerAction -> ChronometersAction.Chronometer(id, a) }
}
```

## App (root feature)

```kotlin
data class AppState(
    val chronometers: ChronometersState = ChronometersState(),
    val universities: UniversitiesState = UniversitiesState(),
)

sealed interface AppAction {
    data class Chronometers(val action: ChronometersAction) : AppAction
    data class Universities(val action: UniversitiesAction) : AppAction
    data object OnAppear : AppAction
}
```

Root base reducer handles `OnAppear`: if `universities.countrySearched` is not
blank, `Effect.send(Universities(UniversitiesAction.LoadUniversities(country)))`
— this replaces the auto-load that lived in `UniversitiesStore.init`. Element
actions (`Chronometers(...)`, `Universities(...)`) are observed as `ReduceResult(state)`
and routed by the two `scope` operators.

Two `scope` descriptors (state lens + action prism), one per child, mirroring the
existing `Scope` shape: `AppAction.Chronometers`/`AppAction.Universities` extract
and embed the child action; `toChildState`/`fromChildState` project the slice.

`AppStore(application)` builds `UniversitiesReducer` (with the retrofit-backed
`UniversitiesService` and the `openUrl` side effect that used to live in
`UniversitiesStore`), builds `chronometersReducer`, composes the root reducer, and
extends `Store<AppState, AppAction>`.

## Universities adaptation (logic untouched)

- `UniversitiesReducer`, `UniversitiesState`, `UniversitiesAction`,
  `UniversitiesService`, `University`, `ViewState` — **unchanged**.
- **Delete** `UniversitiesStore.kt` — the App is now the store; its
  `defaultRetrofit()` / service / `openUrl` construction and the initial-load
  trigger move into `AppStore` and `AppAction.OnAppear`.
- `UniversitiesScreen` becomes a child composable taking
  `(state: UniversitiesState, send: (UniversitiesAction) -> Unit)` instead of
  owning a store.

## UI

- `AppScreen()` obtains `AppStore` via `viewModel(factory = …)` (same factory
  pattern the old `UniversitiesScreen` used, now building `AppStore`), collects
  `store.state`, fires `LaunchedEffect(Unit) { store.send(AppAction.OnAppear) }`,
  and renders a single `Column`:
  - `ChronometersSection(state.chronometers) { store.send(AppAction.Chronometers(it)) }` **above**
  - the universities search UI: `UniversitiesScreen(state.universities) { store.send(AppAction.Universities(it)) }`.
- `ChronometersSection`: a header row with **Add** and **Play all / Pause all /
  Reset all** buttons, then the list of `ChronometerRow`s.
- `ChronometerRow(state, dispatch)`: shows `mm:ss` (from `elapsedSeconds`), a
  Play/Pause toggle (by `isRunning`), a Reset button, and a Remove button — each
  dispatching the corresponding `ChronometersAction.Chronometer(id, …)` / `Remove(id)`.
- `MainActivity` → `setContent { AppScreen() }`.

## Testing

Reducer unit tests (`:app`, JUnit 5, as the existing app tests):
- `ChronometerReducer`: `Play` sets running + returns a cancellable effect;
  `Pause` stops + cancels; `Reset` zeroes + stops + cancels; `Tick` increments.
- `ChronometersReducer`: `Add` appends a running element with the next id and
  auto-starts it (assert the fed-back `Chronometer(newId, Play)` via `TestStore`
  or effect inspection); `Remove` drops the element; `PauseAll`/`PlayAll`/`ResetAll`
  fan out one child action per element; element action updates the right child.

`TestStore` integration (`:app`):
- Add two chronometers → two tick jobs tracked; advance virtual time → each
  element's `elapsedSeconds` increments independently.

Note: the tick effect uses `delay(1_000)`, so tests that advance time must run
the store's effects on a dispatcher backed by `runTest`'s scheduler — set
`Dispatchers.setMain(StandardTestDispatcher(testScheduler))` inside `runTest` and
use `advanceTimeBy(1_000)` (plus `runCurrent()`) to release ticks deterministically
rather than a standalone `UnconfinedTestDispatcher`. Tests that only assert
immediate reducer/effect behavior can keep the `UnconfinedTestDispatcher` pattern
used elsewhere.
- `PauseAll` → all tick jobs cancelled; `Remove` of a running element → exactly
  its job leaves the store.
- Universities still loads through the root store: `OnAppear` (or a
  `Universities(LoadUniversities)` action) drives the existing reducer/effect via
  `scope` and reaches `ViewState.Loaded`.

## File structure

New (`app/src/main/java/com/florentmaufras/reduxdemo/`):
- `chronometers/data/ChronometerState.kt`, `ChronometerAction.kt`, `ChronometerReducer.kt`
- `chronometers/data/ChronometersState.kt`, `ChronometersAction.kt`, `ChronometersReducer.kt`
- `chronometers/ui/ChronometersSection.kt` (section + `ChronometerRow`)
- `app/AppState.kt`, `app/AppAction.kt`, `app/AppReducer.kt`, `app/AppStore.kt`
- `app/ui/AppScreen.kt`

Modify:
- `universities/ui/UniversitiesScreen.kt` → child composable `(state, send)`
- `app/MainActivity.kt` → `AppScreen()`

Delete:
- `universities/data/UniversitiesStore.kt`

Test (`app/src/test/java/com/florentmaufras/reduxdemo/`):
- `chronometers/data/ChronometerReducerTest.kt`, `ChronometersReducerTest.kt`
- `chronometers/data/ChronometersStoreTest.kt` (TestStore integration)
- `app/AppStoreTest.kt` (universities-through-root integration)

## Success criteria

- One `AppStore` owns Chronometers + Universities; root composed via two `scope`
  calls; Chronometers composed via `forEach`.
- A chronometer auto-starts on Add, ticks once per second while running, and
  Play/Pause/Reset behave per this spec; Reset zeroes and pauses.
- Remove stops and untracks the removed element's tick effect.
- Parent PlayAll/PauseAll/ResetAll act on every child via fan-out.
- Universities search works unchanged through the root store; its reducer/state/
  actions are untouched and `UniversitiesStore` is removed.
- Full `:redux` + `:app` suites green.

## Out of scope

- Persistence / process-death restoration of chronometers.
- Sub-second precision (1-second ticks only).
- Reordering chronometers.
- Any change to universities' reducer/state/actions/service logic.
