# Demo Testability Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Strengthen the demo app's unit tests so they demonstrate how testable the TCA-style architecture is across its layers, and document that story.

**Architecture:** Test-only + docs. Add `:app` unit tests that exercise the full `App → Chronometers → forEach → Chronometer` composition through the single `AppStore`, demonstrate side-effect testing via dependency injection, and cover Reset/resume; add a `TESTING.md` narrating the easy/medium/hard testability story. No production code changes. No UI tests (out of scope by decision).

**Tech Stack:** Kotlin, kotlinx.coroutines-test, MockK, JUnit 5, the `:redux` `TestStore`.

## Global Constraints

- `:app` tests use **JUnit 5** (`org.junit.jupiter.api.Test`, `@AfterEach`, `org.junit.jupiter.api.Assertions.assertEquals`).
- `:app` cannot read `:redux` `internal` members — assert only via public API: `currentState` and `TestStore.receivedActions`.
- Effects using `delay` (the chronometer tick) require `Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))` inside `runTest`, advanced with `advanceTimeBy(ms)` then `runCurrent()`; end tests that leave a chronometer running by sending a pause. Never `advanceUntilIdle()` on a live tick loop. Finite effects (universities load, `OpenWebsite`) may use `advanceUntilIdle()`.
- Tests added to a same-package test class need no new imports for types in that package (`ChronometersAction`/`ChronometerAction`/`ChronometerState`/`ChronometersState`/`chronometersReducer` in `chronometers.data`; `UniversitiesAction`/`UniversitiesState`/`ViewState`/`UniversitiesReducer` in `universities.data`).
- No production code changes; no new UI tests.
- Commit messages: single-line conventional `type(scope): description` — no body, no trailers.

---

## File Structure

`:app` test:
- Modify `app/src/test/java/com/florentmaufras/reduxdemo/app/AppStoreTest.kt` — root-level collection-op tests.
- Modify `app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStoreTest.kt` — side-effect-via-DI test.
- Modify `app/src/test/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersStoreTest.kt` — Reset + pause/resume tests.

Docs:
- Create `docs/TESTING.md` — testability narrative.

---

## Task 1: Root-level collection ops through the single `AppStore`

**Files:**
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/app/AppStoreTest.kt`

**Interfaces:**
- Consumes: existing `AppStoreTest.store()` helper (`TestStore(AppState(), appReducer(UniversitiesReducer(service)))`), `AppAction.Chronometers`, `ChronometersAction` (already imported).
- Produces: two tests proving `PauseAll` and `Remove` route through `scope` + `forEach` end-to-end.

- [ ] **Step 1: Write the failing tests**

Insert these two methods into `AppStoreTest` (after `onAppear_whenAlreadyLoaded_doesNotReload`, before the closing brace). No new imports are needed.

```kotlin
    @Test
    fun pauseAll_throughRoot_stopsAllChronometers() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = store()

        store.send(AppAction.Chronometers(ChronometersAction.Add)) // id 0
        store.send(AppAction.Chronometers(ChronometersAction.Add)) // id 1
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(1, 1), store.currentState.chronometers.chronometers.map { it.elapsedSeconds })

        store.send(AppAction.Chronometers(ChronometersAction.PauseAll))
        assertEquals(listOf(false, false), store.currentState.chronometers.chronometers.map { it.isRunning })

        advanceTimeBy(3_000); runCurrent()
        assertEquals(listOf(1, 1), store.currentState.chronometers.chronometers.map { it.elapsedSeconds }) // frozen
    }

    @Test
    fun remove_throughRoot_dropsElementAndCancelsItsTicks() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = store()

        store.send(AppAction.Chronometers(ChronometersAction.Add)) // id 0
        store.send(AppAction.Chronometers(ChronometersAction.Add)) // id 1
        advanceTimeBy(1_000); runCurrent()

        store.send(AppAction.Chronometers(ChronometersAction.Remove(0)))
        advanceTimeBy(2_000); runCurrent()

        // Removed element gone; survivor kept ticking (1 + 2 = 3) — cancellation composed through scope + forEach.
        assertEquals(listOf(1), store.currentState.chronometers.chronometers.map { it.id })
        assertEquals(3, store.currentState.chronometers.chronometers[0].elapsedSeconds)

        store.send(AppAction.Chronometers(ChronometersAction.PauseAll)) // stop the survivor
    }
```

- [ ] **Step 2: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*AppStoreTest" --console=plain`
Expected: PASS — 5 tests total (3 existing + 2 new). (These are integration guards over existing production code; they pass immediately once written. Confirm they run — check the report shows 5 tests, 0 skipped.)

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/florentmaufras/reduxdemo/app/AppStoreTest.kt
git commit -m "test(app): cover pause-all and remove through the root AppStore"
```

---

## Task 2: Side-effect-via-DI, and Reset / pause-resume

**Files:**
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStoreTest.kt`
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersStoreTest.kt`

**Interfaces:**
- Consumes: `UniversitiesReducer(universitiesService, openUrl, timber)` (injectable `openUrl: (String) -> Unit`), `UniversitiesAction.OpenWebsite`; `chronometersReducer`, `ChronometersAction`, `ChronometerAction` (all same-package, already imported in each test class); `TestStore`.
- Produces: a test proving `OpenWebsite` drives an injected `openUrl`; tests proving Reset zeroes+stops and Pause→Play resumes.

- [ ] **Step 1: Write the side-effect-via-DI test**

Insert into `UniversitiesStoreTest` (after `loadUniversities_emitsErrorStateOnFailure`, before the closing brace). `UniversitiesStoreTest` already sets the Main dispatcher in `@BeforeEach`; no new imports needed (all types are in this test's package).

```kotlin
    @Test
    fun openWebsite_invokesInjectedOpenUrl() = runTest {
        val opened = mutableListOf<String>()
        val store = TestStore(
            UniversitiesState(),
            UniversitiesReducer(universitiesService = service, openUrl = { opened.add(it) }),
        )

        store.send(UniversitiesAction.OpenWebsite("https://example.com"))
        advanceUntilIdle()

        // The side effect is testable purely via constructor injection — no Android, no Intent mocking.
        assertEquals(listOf("https://example.com"), opened)
    }
```

- [ ] **Step 2: Run the universities test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*UniversitiesStoreTest" --console=plain`
Expected: PASS — 3 tests total (2 existing + 1 new).

- [ ] **Step 3: Write the Reset and pause/resume tests**

Insert into `ChronometersStoreTest` (after `remove_stopsRemovedElementTicksButOthersKeepRunning`, before the closing brace). No new imports needed.

```kotlin
    @Test
    fun reset_zeroesElapsedAndStopsTicking() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = TestStore(ChronometersState(), chronometersReducer)

        store.send(ChronometersAction.Add) // id 0
        advanceTimeBy(3_000); runCurrent()
        assertEquals(3, store.currentState.chronometers[0].elapsedSeconds)

        store.send(ChronometersAction.Chronometer(0, ChronometerAction.Reset))
        assertEquals(0, store.currentState.chronometers[0].elapsedSeconds)
        assertEquals(false, store.currentState.chronometers[0].isRunning)

        advanceTimeBy(3_000); runCurrent()
        assertEquals(0, store.currentState.chronometers[0].elapsedSeconds) // stays at 0, stopped
    }

    @Test
    fun pauseThenPlay_resumesTickingFromSameElapsed() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = TestStore(ChronometersState(), chronometersReducer)

        store.send(ChronometersAction.Add) // id 0
        advanceTimeBy(2_000); runCurrent()

        store.send(ChronometersAction.Chronometer(0, ChronometerAction.Pause))
        advanceTimeBy(2_000); runCurrent()
        assertEquals(2, store.currentState.chronometers[0].elapsedSeconds) // frozen while paused

        store.send(ChronometersAction.Chronometer(0, ChronometerAction.Play))
        advanceTimeBy(2_000); runCurrent()
        assertEquals(4, store.currentState.chronometers[0].elapsedSeconds) // resumed: 2 + 2

        store.send(ChronometersAction.PauseAll) // stop the loop
    }
```

- [ ] **Step 4: Run the chronometers store test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ChronometersStoreTest" --console=plain`
Expected: PASS — 5 tests total (3 existing + 2 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStoreTest.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersStoreTest.kt
git commit -m "test(app): cover OpenWebsite side effect via DI and chronometer reset/resume"
```

---

## Task 3: `TESTING.md` testability writeup

**Files:**
- Create: `docs/TESTING.md`

**Interfaces:**
- Consumes: the test suite (as reference exemplars). No code.
- Produces: a narrative document of the demo's testability story.

- [ ] **Step 1: Create `docs/TESTING.md`**

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add docs/TESTING.md
git commit -m "docs: add testability writeup for the demo"
```

---

## Self-Review

**Spec coverage:**
- Full 3-level composition through the root (Add already covered by existing `chronometerTicks_throughRoot_...`; new PauseAll + Remove) → Task 1. ✓
- Side-effect via DI (`OpenWebsite` → injected `openUrl`) → Task 2 Step 1. ✓
- Store-level Reset (zero + stop) and Pause→Play resume → Task 2 Step 3. ✓
- Testability narrative (easy/medium/hard, UI out of scope) → Task 3. ✓
- Logic-only, no UI tests, no production changes → all tasks are `:app` test + one doc. ✓

**Placeholder scan:** No TBD/TODO; every code step has complete code. ✓

**Type consistency:** `store()` helper reused from `AppStoreTest`; `store.currentState.chronometers.chronometers` (AppState.chronometers: ChronometersState, whose `.chronometers` is the list) used consistently; `ChronometersAction.Chronometer(id, action)` / `Remove(id)` / `PauseAll` and `ChronometerAction.Reset`/`Pause`/`Play` match the feature types; `UniversitiesReducer(universitiesService, openUrl)` matches the existing constructor; `TestStore(initialState, reducer)` / `currentState` / `receivedActions` match the redux API. ✓

**Note:** Task 1 and Task 2's store tests exercise existing production behavior, so they pass on first run rather than red-first — they are integration/behavior guards and testability exemplars, not new-code TDD. Each step verifies the test actually runs and the count increases.
