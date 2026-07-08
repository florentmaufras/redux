# Chronometers Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a chronometers feature to the demo app under one root `AppStore` that owns Chronometers (a `forEach` collection of `Chronometer` children) and the existing Universities search (via `scope`).

**Architecture:** Three-level TCA composition. `AppReducer` composes two children with `scope`; `ChronometersReducer` composes a dynamic `List<ChronometerState>` with `forEach`; each `ChronometerReducer` runs a 1-second cancellable tick effect. One `AppStore : Store<AppState, AppAction>` is the only ViewModel. Universities' reducer/state/actions are untouched; only its store/screen wiring changes.

**Tech Stack:** Kotlin, kotlinx.coroutines (Flow/StateFlow), Jetpack Compose, AndroidX ViewModel. `:app` tests: JUnit 5 (Jupiter) + coroutines-test + MockK.

## Global Constraints

- `:app` tests use **JUnit 5** (`org.junit.jupiter.api.Test`, `@BeforeEach`/`@AfterEach`, `org.junit.jupiter.api.Assertions.assertEquals`).
- `:app` tests **cannot** read `:redux` `internal` members (the `Effect` tree nodes, `Store.trackedEffectJobCount`). Assert only via public API: `state` / `currentState` and `TestStore.receivedActions`.
- Effects that use `delay` require a test-scheduler-backed Main dispatcher: inside `runTest`, `Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))`, advance with `advanceTimeBy(ms)` **then** `runCurrent()` (tasks scheduled at exactly the boundary run on `runCurrent`), and end any test with running chronometers by sending a pause/reset so the loop stops. Never call `advanceUntilIdle()` while a tick loop is running (it never idles).
- Package roots: chronometers `com.florentmaufras.reduxdemo.chronometers`, app root `com.florentmaufras.reduxdemo.app`.
- Redux API used (all public): `Reducer`, `ReduceResult`, `Effect` (`none/run/send/merge/cancel`, `.map`, `.cancellable`), `Store`, `TestStore`, `Scope` + `Reducer.scope`, `Identifiable`, `ForEachScope` + `Reducer.forEach`.
- Commit messages: single-line conventional `type(scope): description` — no body, no trailers.
- Design spec: `docs/superpowers/specs/2026-06-30-chronometers-demo-design.md`.

---

## File Structure

`:app` main (`app/src/main/java/com/florentmaufras/reduxdemo/`):
- `chronometers/data/ChronometerState.kt`, `ChronometerAction.kt`, `ChronometerReducer.kt` — leaf feature.
- `chronometers/data/ChronometersState.kt`, `ChronometersAction.kt`, `ChronometersReducer.kt` — collection parent + `forEach`.
- `chronometers/ui/ChronometersSection.kt` — section + `ChronometerRow`.
- `app/AppState.kt`, `app/AppAction.kt`, `app/AppReducer.kt`, `app/AppStore.kt` — root feature + store.
- `app/ui/AppScreen.kt` — root screen.
- Modify `universities/ui/UniversitiesScreen.kt` → child composable `(state, send)`.
- Modify `app/MainActivity.kt` → `AppScreen()`.
- Delete `universities/data/UniversitiesStore.kt`.

`:app` test (`app/src/test/java/com/florentmaufras/reduxdemo/`):
- `chronometers/data/ChronometerReducerTest.kt`, `ChronometersReducerTest.kt`, `ChronometersStoreTest.kt`.
- `app/AppStoreTest.kt`.

---

## Task 1: Chronometer leaf feature

**Files:**
- Create: `app/src/main/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometerState.kt`
- Create: `app/src/main/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometerAction.kt`
- Create: `app/src/main/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometerReducer.kt`
- Test: `app/src/test/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometerReducerTest.kt`

**Interfaces:**
- Consumes: `com.florentmaufras.redux.{Identifiable, Reducer, ReduceResult, Effect}`.
- Produces:
  - `data class ChronometerState(override val id: Int, val elapsedSeconds: Int = 0, val isRunning: Boolean = false) : Identifiable<Int>`
  - `sealed interface ChronometerAction { Play; Pause; Reset; Tick }`
  - `class ChronometerReducer : Reducer<ChronometerState, ChronometerAction>`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometerReducerTest.kt`:

```kotlin
package com.florentmaufras.reduxdemo.chronometers.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChronometerReducerTest {

    private val reducer = ChronometerReducer()

    @Test
    fun play_marksRunning() {
        val result = reducer.reduce(ChronometerState(id = 1), ChronometerAction.Play)
        assertEquals(true, result.state.isRunning)
    }

    @Test
    fun pause_marksNotRunning() {
        val result = reducer.reduce(ChronometerState(id = 1, isRunning = true), ChronometerAction.Pause)
        assertEquals(false, result.state.isRunning)
    }

    @Test
    fun reset_zeroesElapsedAndStops() {
        val result = reducer.reduce(
            ChronometerState(id = 1, elapsedSeconds = 42, isRunning = true),
            ChronometerAction.Reset,
        )
        assertEquals(ChronometerState(id = 1, elapsedSeconds = 0, isRunning = false), result.state)
    }

    @Test
    fun tick_incrementsElapsedByOne() {
        val result = reducer.reduce(ChronometerState(id = 1, elapsedSeconds = 5), ChronometerAction.Tick)
        assertEquals(6, result.state.elapsedSeconds)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ChronometerReducerTest" --console=plain`
Expected: FAIL — `ChronometerState` / `ChronometerAction` / `ChronometerReducer` unresolved.

- [ ] **Step 3: Create `ChronometerState.kt`**

```kotlin
package com.florentmaufras.reduxdemo.chronometers.data

import com.florentmaufras.redux.Identifiable

data class ChronometerState(
    override val id: Int,
    val elapsedSeconds: Int = 0,
    val isRunning: Boolean = false,
) : Identifiable<Int>
```

- [ ] **Step 4: Create `ChronometerAction.kt`**

```kotlin
package com.florentmaufras.reduxdemo.chronometers.data

sealed interface ChronometerAction {
    data object Play : ChronometerAction
    data object Pause : ChronometerAction
    data object Reset : ChronometerAction
    data object Tick : ChronometerAction
}
```

- [ ] **Step 5: Create `ChronometerReducer.kt`**

```kotlin
package com.florentmaufras.reduxdemo.chronometers.data

import com.florentmaufras.redux.Effect
import com.florentmaufras.redux.ReduceResult
import com.florentmaufras.redux.Reducer
import kotlinx.coroutines.delay

class ChronometerReducer : Reducer<ChronometerState, ChronometerAction> {
    override fun reduce(
        state: ChronometerState,
        action: ChronometerAction,
    ): ReduceResult<ChronometerState, ChronometerAction> = when (action) {
        ChronometerAction.Play -> ReduceResult(
            state.copy(isRunning = true),
            Effect.run<ChronometerAction> {
                while (true) {
                    delay(1_000)
                    emit(ChronometerAction.Tick)
                }
            }.cancellable(state.id, cancelInFlight = true),
        )
        ChronometerAction.Pause -> ReduceResult(
            state.copy(isRunning = false),
            Effect.cancel(state.id),
        )
        ChronometerAction.Reset -> ReduceResult(
            state.copy(elapsedSeconds = 0, isRunning = false),
            Effect.cancel(state.id),
        )
        ChronometerAction.Tick -> ReduceResult(
            state.copy(elapsedSeconds = state.elapsedSeconds + 1),
        )
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ChronometerReducerTest" --console=plain`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometerState.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometerAction.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometerReducer.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometerReducerTest.kt
git commit -m "feat(app): add chronometer leaf feature (play/pause/reset/tick)"
```

---

## Task 2: Chronometers collection parent

**Files:**
- Create: `app/src/main/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersState.kt`
- Create: `app/src/main/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersAction.kt`
- Create: `app/src/main/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersReducer.kt`
- Test: `app/src/test/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersReducerTest.kt`
- Test: `app/src/test/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersStoreTest.kt`

**Interfaces:**
- Consumes: Task 1 types; `com.florentmaufras.redux.{Reducer, ReduceResult, Effect, ForEachScope, forEach, TestStore}`.
- Produces:
  - `data class ChronometersState(val chronometers: List<ChronometerState> = emptyList(), val nextId: Int = 0)`
  - `sealed interface ChronometersAction { data class Chronometer(val id: Int, val action: ChronometerAction); Add; data class Remove(val id: Int); PlayAll; PauseAll; ResetAll }`
  - `val chronometersReducer: Reducer<ChronometersState, ChronometersAction>` (own reducer `.forEach(...)` the `ChronometerReducer`).

- [ ] **Step 1: Write the failing reducer test**

Create `app/src/test/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersReducerTest.kt`:

```kotlin
package com.florentmaufras.reduxdemo.chronometers.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChronometersReducerTest {

    @Test
    fun add_appendsRunningChronometerWithNextId() {
        val result = chronometersReducer.reduce(ChronometersState(), ChronometersAction.Add)
        assertEquals(1, result.state.chronometers.size)
        assertEquals(ChronometerState(id = 0, isRunning = true), result.state.chronometers[0])
        assertEquals(1, result.state.nextId)
    }

    @Test
    fun add_twice_usesDistinctIncrementingIds() {
        val first = chronometersReducer.reduce(ChronometersState(), ChronometersAction.Add)
        val second = chronometersReducer.reduce(first.state, ChronometersAction.Add)
        assertEquals(listOf(0, 1), second.state.chronometers.map { it.id })
        assertEquals(2, second.state.nextId)
    }

    @Test
    fun remove_dropsMatchingElement() {
        val state = ChronometersState(
            chronometers = listOf(ChronometerState(0), ChronometerState(1)),
            nextId = 2,
        )
        val result = chronometersReducer.reduce(state, ChronometersAction.Remove(0))
        assertEquals(listOf(1), result.state.chronometers.map { it.id })
    }

    @Test
    fun elementAction_updatesOnlyMatchingChild() {
        val state = ChronometersState(
            chronometers = listOf(ChronometerState(0), ChronometerState(1, elapsedSeconds = 5)),
            nextId = 2,
        )
        val result = chronometersReducer.reduce(
            state,
            ChronometersAction.Chronometer(1, ChronometerAction.Tick),
        )
        assertEquals(listOf(0, 6), result.state.chronometers.map { it.elapsedSeconds })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ChronometersReducerTest" --console=plain`
Expected: FAIL — `ChronometersState` / `ChronometersAction` / `chronometersReducer` unresolved.

- [ ] **Step 3: Create `ChronometersState.kt`**

```kotlin
package com.florentmaufras.reduxdemo.chronometers.data

data class ChronometersState(
    val chronometers: List<ChronometerState> = emptyList(),
    val nextId: Int = 0,
)
```

- [ ] **Step 4: Create `ChronometersAction.kt`**

```kotlin
package com.florentmaufras.reduxdemo.chronometers.data

sealed interface ChronometersAction {
    data class Chronometer(val id: Int, val action: ChronometerAction) : ChronometersAction
    data object Add : ChronometersAction
    data class Remove(val id: Int) : ChronometersAction
    data object PlayAll : ChronometersAction
    data object PauseAll : ChronometersAction
    data object ResetAll : ChronometersAction
}
```

- [ ] **Step 5: Create `ChronometersReducer.kt`**

```kotlin
package com.florentmaufras.reduxdemo.chronometers.data

import com.florentmaufras.redux.Effect
import com.florentmaufras.redux.ForEachScope
import com.florentmaufras.redux.ReduceResult
import com.florentmaufras.redux.Reducer
import com.florentmaufras.redux.forEach

private val chronometersScope =
    object : ForEachScope<ChronometersState, ChronometersAction, ChronometerState, ChronometerAction, Int> {
        override val toChildren: (ChronometersState) -> List<ChronometerState> = { it.chronometers }
        override val fromChildren: (ChronometersState, List<ChronometerState>) -> ChronometersState =
            { s, c -> s.copy(chronometers = c) }
        override val toChildAction: (ChronometersAction) -> Pair<Int, ChronometerAction>? =
            { a -> (a as? ChronometersAction.Chronometer)?.let { it.id to it.action } }
        override val embedChildAction: (Int, ChronometerAction) -> ChronometersAction =
            { id, a -> ChronometersAction.Chronometer(id, a) }
    }

private fun broadcast(
    chronometers: List<ChronometerState>,
    action: ChronometerAction,
): Effect<ChronometersAction> =
    Effect.merge(
        *chronometers
            .map { Effect.send(ChronometersAction.Chronometer(it.id, action)) }
            .toTypedArray()
    )

private val chronometersOwnReducer = Reducer<ChronometersState, ChronometersAction> { state, action ->
    when (action) {
        ChronometersAction.Add -> {
            val id = state.nextId
            ReduceResult(
                state.copy(
                    chronometers = state.chronometers + ChronometerState(id = id, isRunning = true),
                    nextId = id + 1,
                ),
                Effect.send(ChronometersAction.Chronometer(id, ChronometerAction.Play)),
            )
        }
        is ChronometersAction.Remove -> ReduceResult(
            state.copy(chronometers = state.chronometers.filterNot { it.id == action.id }),
            Effect.cancel(action.id),
        )
        ChronometersAction.PlayAll -> ReduceResult(state, broadcast(state.chronometers, ChronometerAction.Play))
        ChronometersAction.PauseAll -> ReduceResult(state, broadcast(state.chronometers, ChronometerAction.Pause))
        ChronometersAction.ResetAll -> ReduceResult(state, broadcast(state.chronometers, ChronometerAction.Reset))
        is ChronometersAction.Chronometer -> ReduceResult(state)
    }
}

val chronometersReducer: Reducer<ChronometersState, ChronometersAction> =
    chronometersOwnReducer.forEach(chronometersScope, ChronometerReducer())
```

- [ ] **Step 6: Run the reducer test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ChronometersReducerTest" --console=plain`
Expected: PASS (4 tests).

- [ ] **Step 7: Write the store-integration test**

Create `app/src/test/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersStoreTest.kt`. This drives the reducer through a `TestStore`, exercising the tick effect (`delay`), auto-start, broadcast, and remove. It uses `UnconfinedTestDispatcher(testScheduler)` and advances virtual time.

```kotlin
package com.florentmaufras.reduxdemo.chronometers.data

import com.florentmaufras.redux.TestStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChronometersStoreTest {

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun add_autoStartsAndTicksEachSecond() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = TestStore(ChronometersState(), chronometersReducer)

        store.send(ChronometersAction.Add)
        // Auto-start ran eagerly: one running chronometer, no tick yet.
        assertEquals(1, store.currentState.chronometers.size)
        assertEquals(true, store.currentState.chronometers[0].isRunning)
        assertEquals(0, store.currentState.chronometers[0].elapsedSeconds)
        assertEquals(true, store.receivedActions.contains(ChronometersAction.Chronometer(0, ChronometerAction.Play)))

        advanceTimeBy(2_000); runCurrent()
        assertEquals(2, store.currentState.chronometers[0].elapsedSeconds)

        store.send(ChronometersAction.PauseAll) // stop the loop
    }

    @Test
    fun pauseAll_stopsTicking() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = TestStore(ChronometersState(), chronometersReducer)

        store.send(ChronometersAction.Add)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, store.currentState.chronometers[0].elapsedSeconds)

        store.send(ChronometersAction.PauseAll)
        assertEquals(false, store.currentState.chronometers[0].isRunning)

        advanceTimeBy(3_000); runCurrent()
        assertEquals(1, store.currentState.chronometers[0].elapsedSeconds) // no further ticks
    }

    @Test
    fun remove_stopsRemovedElementTicksButOthersKeepRunning() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = TestStore(ChronometersState(), chronometersReducer)

        store.send(ChronometersAction.Add) // id 0
        store.send(ChronometersAction.Add) // id 1
        advanceTimeBy(1_000); runCurrent()

        val ticksForZeroBeforeRemove = store.receivedActions.count {
            it == ChronometersAction.Chronometer(0, ChronometerAction.Tick)
        }
        store.send(ChronometersAction.Remove(0))
        advanceTimeBy(2_000); runCurrent()

        // Removed element is gone; the survivor kept ticking (1 + 2 = 3).
        assertEquals(listOf(1), store.currentState.chronometers.map { it.id })
        assertEquals(3, store.currentState.chronometers[0].elapsedSeconds)
        // Removed element's tick effect was cancelled: no new Tick actions for id 0 arrived.
        val ticksForZeroAfterRemove = store.receivedActions.count {
            it == ChronometersAction.Chronometer(0, ChronometerAction.Tick)
        }
        assertEquals(ticksForZeroBeforeRemove, ticksForZeroAfterRemove)

        store.send(ChronometersAction.PauseAll) // stop the survivor
    }
}
```

- [ ] **Step 8: Run the store test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ChronometersStoreTest" --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersState.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersAction.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersReducer.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersReducerTest.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/chronometers/data/ChronometersStoreTest.kt
git commit -m "feat(app): add chronometers collection parent composing children via forEach"
```

---

## Task 3: App root feature + store

**Files:**
- Create: `app/src/main/java/com/florentmaufras/reduxdemo/app/AppState.kt`
- Create: `app/src/main/java/com/florentmaufras/reduxdemo/app/AppAction.kt`
- Create: `app/src/main/java/com/florentmaufras/reduxdemo/app/AppReducer.kt`
- Create: `app/src/main/java/com/florentmaufras/reduxdemo/app/AppStore.kt`
- Test: `app/src/test/java/com/florentmaufras/reduxdemo/app/AppStoreTest.kt`

**Interfaces:**
- Consumes: Task 2 `chronometersReducer`, `ChronometersState`, `ChronometersAction`; existing `UniversitiesReducer(universitiesService, openUrl, timber)`, `UniversitiesState`, `UniversitiesAction`, `UniversitiesService`, `ViewState`; `com.florentmaufras.redux.{Scope, scope, Reducer, ReduceResult, Effect, Store, TestStore}`.
- Produces:
  - `data class AppState(val chronometers: ChronometersState = ChronometersState(), val universities: UniversitiesState = UniversitiesState())`
  - `sealed interface AppAction { data class Chronometers(val action: ChronometersAction); data class Universities(val action: UniversitiesAction); OnAppear }`
  - `fun appReducer(universitiesReducer: UniversitiesReducer): Reducer<AppState, AppAction>`
  - `class AppStore(application: Application? = null, initialState: AppState = AppState(), override val reducer: Reducer<AppState, AppAction> = …) : Store<AppState, AppAction>(initialState)`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/florentmaufras/reduxdemo/app/AppStoreTest.kt`:

```kotlin
package com.florentmaufras.reduxdemo.app

import com.florentmaufras.redux.TestStore
import com.florentmaufras.reduxdemo.chronometers.data.ChronometerAction
import com.florentmaufras.reduxdemo.chronometers.data.ChronometersAction
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import com.florentmaufras.reduxdemo.universities.data.University
import com.florentmaufras.reduxdemo.universities.data.UniversitiesReducer
import com.florentmaufras.reduxdemo.universities.data.ViewState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppStoreTest {

    private val service: UniversitiesService = mockk()

    private fun store(initial: AppState = AppState()) =
        TestStore(initial, appReducer(UniversitiesReducer(universitiesService = service)))

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onAppear_loadsUniversitiesThroughRoot() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val universities = listOf(mockk<University>())
        coEvery { service.getUniversities("Canada") } returns universities

        val store = store() // default countrySearched = "Canada"
        store.send(AppAction.OnAppear)
        advanceUntilIdle()

        assertEquals(ViewState.Loaded(universities), store.currentState.universities.viewState)
    }

    @Test
    fun chronometerTicks_throughRoot_independentOfUniversities() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = store()

        store.send(AppAction.Chronometers(ChronometersAction.Add))
        advanceTimeBy(2_000); runCurrent()

        assertEquals(2, store.currentState.chronometers.chronometers[0].elapsedSeconds)
        assertEquals(ViewState.Idle, store.currentState.universities.viewState) // universities untouched

        store.send(AppAction.Chronometers(ChronometersAction.PauseAll))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*AppStoreTest" --console=plain`
Expected: FAIL — `AppState` / `AppAction` / `appReducer` unresolved.

- [ ] **Step 3: Create `AppState.kt`**

```kotlin
package com.florentmaufras.reduxdemo.app

import com.florentmaufras.reduxdemo.chronometers.data.ChronometersState
import com.florentmaufras.reduxdemo.universities.data.UniversitiesState

data class AppState(
    val chronometers: ChronometersState = ChronometersState(),
    val universities: UniversitiesState = UniversitiesState(),
)
```

- [ ] **Step 4: Create `AppAction.kt`**

```kotlin
package com.florentmaufras.reduxdemo.app

import com.florentmaufras.reduxdemo.chronometers.data.ChronometersAction
import com.florentmaufras.reduxdemo.universities.data.UniversitiesAction

sealed interface AppAction {
    data class Chronometers(val action: ChronometersAction) : AppAction
    data class Universities(val action: UniversitiesAction) : AppAction
    data object OnAppear : AppAction
}
```

- [ ] **Step 5: Create `AppReducer.kt`**

```kotlin
package com.florentmaufras.reduxdemo.app

import com.florentmaufras.redux.Effect
import com.florentmaufras.redux.ReduceResult
import com.florentmaufras.redux.Reducer
import com.florentmaufras.redux.Scope
import com.florentmaufras.redux.scope
import com.florentmaufras.reduxdemo.chronometers.data.ChronometersAction
import com.florentmaufras.reduxdemo.chronometers.data.ChronometersState
import com.florentmaufras.reduxdemo.chronometers.data.chronometersReducer
import com.florentmaufras.reduxdemo.universities.data.UniversitiesAction
import com.florentmaufras.reduxdemo.universities.data.UniversitiesReducer
import com.florentmaufras.reduxdemo.universities.data.UniversitiesState

private val universitiesScope =
    object : Scope<AppState, AppAction, UniversitiesState, UniversitiesAction> {
        override val toChildState: (AppState) -> UniversitiesState = { it.universities }
        override val fromChildState: (AppState, UniversitiesState) -> AppState = { s, c -> s.copy(universities = c) }
        override val toChildAction: (AppAction) -> UniversitiesAction? = { (it as? AppAction.Universities)?.action }
        override val embedChildAction: (UniversitiesAction) -> AppAction = { AppAction.Universities(it) }
    }

private val chronometersScope =
    object : Scope<AppState, AppAction, ChronometersState, ChronometersAction> {
        override val toChildState: (AppState) -> ChronometersState = { it.chronometers }
        override val fromChildState: (AppState, ChronometersState) -> AppState = { s, c -> s.copy(chronometers = c) }
        override val toChildAction: (AppAction) -> ChronometersAction? = { (it as? AppAction.Chronometers)?.action }
        override val embedChildAction: (ChronometersAction) -> AppAction = { AppAction.Chronometers(it) }
    }

fun appReducer(universitiesReducer: UniversitiesReducer): Reducer<AppState, AppAction> {
    val base = Reducer<AppState, AppAction> { state, action ->
        when (action) {
            AppAction.OnAppear ->
                if (state.universities.countrySearched.isNotBlank()) {
                    ReduceResult(
                        state,
                        Effect.send(
                            AppAction.Universities(
                                UniversitiesAction.LoadUniversities(state.universities.countrySearched)
                            )
                        ),
                    )
                } else {
                    ReduceResult(state)
                }
            is AppAction.Universities -> ReduceResult(state)
            is AppAction.Chronometers -> ReduceResult(state)
        }
    }
    return base
        .scope(universitiesScope, universitiesReducer)
        .scope(chronometersScope, chronometersReducer)
}
```

- [ ] **Step 6: Create `AppStore.kt`**

```kotlin
package com.florentmaufras.reduxdemo.app

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import com.florentmaufras.redux.Reducer
import com.florentmaufras.redux.Store
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import com.florentmaufras.reduxdemo.universities.data.UniversitiesReducer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private fun defaultRetrofit(): Retrofit = Retrofit.Builder()
    .baseUrl("http://universities.hipolabs.com")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

class AppStore(
    application: Application? = null,
    initialState: AppState = AppState(),
    override val reducer: Reducer<AppState, AppAction> = appReducer(
        UniversitiesReducer(
            universitiesService = UniversitiesService(defaultRetrofit()),
            openUrl = { url ->
                application?.startActivity(
                    Intent(Intent.ACTION_VIEW, url.toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
        )
    ),
) : Store<AppState, AppAction>(initialState)
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*AppStoreTest" --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/florentmaufras/reduxdemo/app/AppState.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/app/AppAction.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/app/AppReducer.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/app/AppStore.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/app/AppStoreTest.kt
git commit -m "feat(app): add AppStore composing chronometers and universities via scope"
```

---

## Task 4: UI wiring — AppScreen, chronometers UI, universities as child

**Files:**
- Create: `app/src/main/java/com/florentmaufras/reduxdemo/chronometers/ui/ChronometersSection.kt`
- Create: `app/src/main/java/com/florentmaufras/reduxdemo/app/ui/AppScreen.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/ui/UniversitiesScreen.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/app/MainActivity.kt`
- Delete: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStore.kt`

**Interfaces:**
- Consumes: `AppStore`, `AppAction`, `AppState` (Task 3); `ChronometersState`, `ChronometersAction`, `ChronometerState`, `ChronometerAction` (Tasks 1-2); `UniversitiesState`, `UniversitiesAction`, `University`, `ViewState` (existing).
- Produces: `@Composable fun ChronometersSection(state: ChronometersState, dispatch: (ChronometersAction) -> Unit)`; `@Composable fun AppScreen()`; `@Composable fun UniversitiesScreen(state: UniversitiesState, send: (UniversitiesAction) -> Unit)`.

This task is UI wiring; it is verified by a clean `:app` compile and the full unit-test suite staying green (no `UniversitiesStore` references remain; `UniversitiesStoreTest` already drives a `TestStore`, not `UniversitiesStore`).

- [ ] **Step 1: Create `ChronometersSection.kt`**

```kotlin
package com.florentmaufras.reduxdemo.chronometers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.florentmaufras.reduxdemo.chronometers.data.ChronometerAction
import com.florentmaufras.reduxdemo.chronometers.data.ChronometerState
import com.florentmaufras.reduxdemo.chronometers.data.ChronometersAction
import com.florentmaufras.reduxdemo.chronometers.data.ChronometersState

@Composable
fun ChronometersSection(
    state: ChronometersState,
    dispatch: (ChronometersAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text("Chronometers", modifier = Modifier.padding(bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { dispatch(ChronometersAction.Add) }) { Text("Add") }
            OutlinedButton(onClick = { dispatch(ChronometersAction.PlayAll) }) { Text("Play all") }
            OutlinedButton(onClick = { dispatch(ChronometersAction.PauseAll) }) { Text("Pause all") }
            OutlinedButton(onClick = { dispatch(ChronometersAction.ResetAll) }) { Text("Reset all") }
        }
        state.chronometers.forEach { chrono ->
            ChronometerRow(chrono, dispatch)
        }
    }
}

@Composable
private fun ChronometerRow(
    state: ChronometerState,
    dispatch: (ChronometersAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(formatElapsed(state.elapsedSeconds))
        if (state.isRunning) {
            OutlinedButton(onClick = { dispatch(ChronometersAction.Chronometer(state.id, ChronometerAction.Pause)) }) {
                Text("Pause")
            }
        } else {
            OutlinedButton(onClick = { dispatch(ChronometersAction.Chronometer(state.id, ChronometerAction.Play)) }) {
                Text("Play")
            }
        }
        OutlinedButton(onClick = { dispatch(ChronometersAction.Chronometer(state.id, ChronometerAction.Reset)) }) {
            Text("Reset")
        }
        OutlinedButton(onClick = { dispatch(ChronometersAction.Remove(state.id)) }) {
            Text("Remove")
        }
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
```

- [ ] **Step 2: Rewrite `UniversitiesScreen.kt` as a `(state, send)` child**

Replace the entire file with (only the top `UniversitiesScreen` composable and its imports change; the private `UniversitiesLoading`/`UniversitiesError`/`UniversitiesNoResult`/`UniversitiesContent` composables below are unchanged and must be kept exactly as they are today):

```kotlin
package com.florentmaufras.reduxdemo.universities.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.florentmaufras.reduxdemo.universities.data.UniversitiesAction
import com.florentmaufras.reduxdemo.universities.data.UniversitiesState
import com.florentmaufras.reduxdemo.universities.data.University
import com.florentmaufras.reduxdemo.universities.data.ViewState

@Composable
fun UniversitiesScreen(
    state: UniversitiesState,
    send: (UniversitiesAction) -> Unit,
) {
    val textFieldState = rememberTextFieldState(state.countrySearched)

    Column(modifier = Modifier.padding(8.dp)) {
        Text(
            text = "Welcome to that small simple demo app!",
            modifier = Modifier.padding(8.dp)
        )
        Text(
            text = "Please search for a country below and see the universities found in it.",
            modifier = Modifier.padding(8.dp)
        )
        TextField(
            state = textFieldState,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
            onKeyboardAction = KeyboardActionHandler {
                send(UniversitiesAction.LoadUniversities(textFieldState.text.toString()))
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.padding(8.dp),
        )

        when (val vs = state.viewState) {
            ViewState.Idle -> UniversitiesNoResult(state.countrySearched)
            ViewState.Loading -> UniversitiesLoading()
            is ViewState.Error -> UniversitiesError(vs.message)
            is ViewState.Loaded -> {
                if (vs.universities.isEmpty()) UniversitiesNoResult(state.countrySearched)
                else UniversitiesContent(vs.universities, send)
            }
        }
    }
}

@Composable
private fun UniversitiesLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun UniversitiesError(message: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(if (message != null) "Error: $message" else "Oupsy daisy! Something went wrong!")
    }
}

@Composable
private fun UniversitiesNoResult(country: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("No result found for this search: $country")
    }
}

@Composable
private fun UniversitiesContent(
    universities: List<University>,
    dispatchAction: (UniversitiesAction) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp),
        verticalArrangement = Arrangement.SpaceAround,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        item {
            Text(
                text = "University/ies found:",
                modifier = Modifier.padding(8.dp)
            )
        }
        items(items = universities) { university ->
            Card(
                modifier = Modifier.padding(8.dp)
            ) {
                Column {
                    Text(
                        text = university.name,
                        modifier = Modifier.padding(8.dp)
                    )
                    Text(
                        text = university.country,
                        modifier = Modifier.padding(8.dp)
                    )
                    if (university.webPages?.isNotEmpty() == true) {
                        Button(
                            onClick = {
                                dispatchAction(UniversitiesAction.OpenWebsite(university.webPages[0]))
                            },
                            modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 16.dp)
                        ) {
                            Text("Open Website")
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Create `AppScreen.kt`**

`ChronometersSection` is a non-scrolling `Column`; the universities `LazyVerticalGrid` is the only scrollable and is given bounded height via `Modifier.weight(1f)`, avoiding nested-scroll conflicts.

```kotlin
package com.florentmaufras.reduxdemo.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florentmaufras.reduxdemo.app.AppAction
import com.florentmaufras.reduxdemo.app.AppStore
import com.florentmaufras.reduxdemo.chronometers.ui.ChronometersSection
import com.florentmaufras.reduxdemo.universities.ui.UniversitiesScreen

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val store: AppStore = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext as Application
                @Suppress("UNCHECKED_CAST")
                return AppStore(application = app) as T
            }
        }
    )
    val state by store.state.collectAsState()

    LaunchedEffect(Unit) { store.send(AppAction.OnAppear) }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        ChronometersSection(state.chronometers) { store.send(AppAction.Chronometers(it)) }
        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
            UniversitiesScreen(state.universities) { store.send(AppAction.Universities(it)) }
        }
    }
}
```

- [ ] **Step 4: Update `MainActivity.kt`**

Replace the file body so it hosts `AppScreen`:

```kotlin
package com.florentmaufras.reduxdemo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.florentmaufras.reduxdemo.app.ui.AppScreen

class MainActivity: ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppScreen()
        }
    }
}
```

- [ ] **Step 5: Delete `UniversitiesStore.kt`**

```bash
git rm app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStore.kt
```

- [ ] **Step 6: Verify the app compiles**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL, no unresolved references (no remaining `UniversitiesStore` usages).

- [ ] **Step 7: Run the full unit-test suite**

Run: `./gradlew :redux:testDebugUnitTest :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL — all `:redux` and `:app` tests pass (including the existing `UniversitiesReducerTest`, `UniversitiesStoreTest`, `UniversitiesServiceTest`, `UniversityTest`).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/florentmaufras/reduxdemo/chronometers/ui/ChronometersSection.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/app/ui/AppScreen.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/ui/UniversitiesScreen.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/app/MainActivity.kt
git rm app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStore.kt
git commit -m "feat(app): host chronometers above universities under one AppStore screen"
```

---

## Self-Review

**Spec coverage:**
- One `AppStore` owns Chronometers + Universities; root composed via two `scope` calls → Task 3 (`appReducer`). ✓
- Chronometers composed via `forEach` → Task 2 (`chronometersReducer`). ✓
- Chronometer auto-starts on Add (parent sends `Chronometer(newId, Play)`) → Task 2 reducer + `ChronometersStoreTest.add_autoStartsAndTicksEachSecond`. ✓
- 1s ticks while running; Play/Pause/Reset (Reset = zero + pause) → Task 1 reducer + tests; tick timing → Task 2 store test. ✓
- Remove stops and untracks the removed element's tick (parent emits `Effect.cancel(id)`) → Task 2 `remove_stopsRemovedElementTicksButOthersKeepRunning`. ✓
- PlayAll/PauseAll/ResetAll fan out one child action per element → Task 2 `broadcast(...)` + `pauseAll_stopsTicking`. ✓
- Universities search works unchanged through root; reducer/state/actions untouched; `UniversitiesStore` removed → Task 3 `onAppear_loadsUniversitiesThroughRoot`, Task 4 (screen child + delete + full suite). ✓
- UI: chronometers above the search under one screen → Task 4 `AppScreen`. ✓
- Full `:redux` + `:app` suites green → Task 4 Step 7. ✓
- Test-dispatcher constraint (delay-backed ticks) honored → Tasks 2 & 3 use `UnconfinedTestDispatcher(testScheduler)` + `advanceTimeBy`/`runCurrent`, never `advanceUntilIdle` with a live loop. ✓

**Placeholder scan:** No TBD/TODO; every code step has complete code. ✓

**Type consistency:** `ChronometerAction`/`ChronometersAction`/`AppAction` case names identical across reducers, tests, and UI; `chronometersReducer` (val) consumed by `appReducer`; `appReducer(universitiesReducer)` signature matches `AppStore` and `AppStoreTest`; `ChronometersAction.Chronometer(id, action)`, `Remove(id)`, and the `ForEachScope`/`Scope` member names match the redux operator signatures; `UniversitiesScreen(state, send)` new signature used consistently in `AppScreen`. ✓
```
