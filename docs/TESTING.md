# Testability of the demo (redux / TCA-style core)

This demo doubles as a testability showcase for the `:redux` architecture. All
tests here are **unit tests** (pure logic + the `TestStore` harness). UI is
deliberately **not** unit-tested — the composables are state-hoisted
`(state, dispatch)` functions with no logic to test.

## Easy — reducers are pure functions

A `Reducer<State, Action>` is a pure `(State, Action) -> ReduceResult`. Testing a
state transition needs no mocks, no coroutines, no dispatcher — just call
`reduce` and assert the returned `state`.

- `ChronometerReducerTest` — play/pause/reset/tick state transitions.
- `ChronometersReducerTest` — add / remove / element routing / empty-broadcast no-op.
- `UniversitiesReducerTest` — loading / loaded / error / no-op state.

Caveat: the returned `Effect` is opaque (its internals are `internal` to
`:redux`), so a reducer test asserts **state only**. Effect behavior is verified
one level up, with `TestStore`.

## Medium — effects via `TestStore` + injected fakes

`TestStore(initialState, reducer)` runs the real store loop: it reduces, commits
state, runs effects, and re-feeds emitted actions, exposing `currentState` and
`receivedActions`. Dependencies are injected, so effects run against fakes with
no Android on the classpath.

- `UniversitiesStoreTest.loadUniversities_*` — drive `LoadUniversities` against a
  fake `UniversitiesService` (MockK) and assert the `Loaded`/`Error` state and
  the fed-back action.
- `UniversitiesStoreTest.openWebsite_invokesInjectedOpenUrl` — the `OpenWebsite`
  side effect is asserted by injecting a plain `openUrl: (String) -> Unit` lambda
  and checking it fired. No `Intent`, no Robolectric.
- `AppStoreTest` — the whole `App → Chronometers → forEach → Chronometer` and
  `App → Universities` composition is exercised through one `AppStore`: universities
  loads through the root, and Add / PauseAll / Remove route through `scope` +
  `forEach` end-to-end.

## Harder — time and concurrency

The chronometer tick is an infinite `delay(1_000)` loop, so its tests must control
virtual time:

- Set `Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))` inside `runTest`.
- Advance with `advanceTimeBy(ms)` **then** `runCurrent()` (a task scheduled at the
  exact boundary runs on `runCurrent`).
- **Never** `advanceUntilIdle()` while a tick loop is live — it never idles. End
  such tests by sending a pause.

See `ChronometersStoreTest` (auto-start ticking, pause-all, remove-cancels,
reset, pause/resume) and `AppStoreTest` (ticks through the root).

## Out of scope

- **UI tests.** Composables are `(state, dispatch)`; there is no view logic worth a
  test, and Compose UI testing needs instrumentation/Robolectric. Not included.
- Persistence / process-death restoration.
