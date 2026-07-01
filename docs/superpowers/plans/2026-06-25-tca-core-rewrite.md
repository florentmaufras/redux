# TCA-style Core Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the library's effect-as-data core with a TCA-style single-runtime store, `Effect<Action>`, and a single state tree.

**Architecture:** An effect *is* a `Flow<Action>` wrapped for composition (`Effect<Action>`). `Reducer<State, Action>` returns `ReduceResult<State, Action>` (new state + effect). A single-runtime `Store<State, Action>.send` reduces, commits state, runs the effect, and re-sends emitted actions. Child reducers compose into parents via a `scope` operator that lifts child effects with `.map(embedChildAction)`.

**Tech Stack:** Kotlin, kotlinx.coroutines (Flow + StateFlow), AndroidX `ViewModel`. Tests: JUnit4 + coroutines-test + MockK in `:redux`; JUnit5 (Jupiter) + coroutines-test + MockK in `:app`.

## Global Constraints

- Redux module version: bump `redux/build.gradle.kts` `version` from `2.0.0` to `3.0.0` (breaking).
- `:redux` tests use **JUnit 4** (`org.junit.Test`, `@Before`/`@After`, `junit.framework.TestCase.assertEquals`).
- `:app` tests use **JUnit 5** (`org.junit.jupiter.api.*`, `@BeforeEach`/`@AfterEach`, `Assertions.assertEquals`); `useJUnitPlatform()` is already configured.
- Coroutine tests set the Main dispatcher: `Dispatchers.setMain(UnconfinedTestDispatcher())` in setup, `resetMain()` in teardown; annotate the class `@OptIn(ExperimentalCoroutinesApi::class)`.
- Commit messages: single-line conventional format `type(scope): description` — no body, no trailers (project `commit-message-format` skill).
- Package for all core types: `com.florentmaufras.redux`.

---

## File Structure

`:redux` main (`redux/src/main/java/com/florentmaufras/redux/`):
- Create `Effect.kt` — `Effect<Action>` Flow wrapper.
- Replace `Reducer.kt` — `Reducer<State, Action>` (fun interface).
- Replace `ReduceResult.kt` — `ReduceResult<State, Action>`.
- Replace `Store.kt` — single-runtime `Store<State, Action>`.
- Replace `Scope.kt` — state lens + `toChildAction`/`embedChildAction`.
- Create `Scopes.kt` — the `scope` reducer operator.
- Delete `EffectResult.kt`, `EffectHandler.kt`, `StateOwner.kt`, `ScopedStateOwner.kt`, `ScopedReducer.kt`.

`:redux` test (`redux/src/test/java/com/florentmaufras/redux/`):
- Create `EffectFactoryTest.kt`, `StoreTest.kt` (overwrite), `ScopeTest.kt`.
- Delete `EffectHandlerTest.kt`, `StateOwnerTest.kt`, `ScopedReducerTest.kt`, `ReduxTest.kt`, `ReducerTest.kt`, `TestStore.kt`, `ActionTest.kt`, `StateTest.kt`, `EffectTest.kt` (old effect-enum fixture).

`:app` (`app/src/main/java/com/florentmaufras/reduxdemo/universities/data/`):
- Modify `UniversitiesReducer.kt`, `UniversitiesStore.kt`, `UniversitiesViewModel.kt`.
- Delete `UniversitiesEffect.kt`, `UniversitiesEffectHandler.kt`.
- Modify tests `UniversitiesReducerTest.kt`, `UniversitiesStoreTest.kt`, `UniversitiesViewModelTest.kt`.

---

## Task 1: `Effect<Action>`

**Files:**
- Create: `redux/src/main/java/com/florentmaufras/redux/Effect.kt`
- Test: `redux/src/test/java/com/florentmaufras/redux/EffectFactoryTest.kt` (new name to avoid colliding with the existing `EffectTest.kt` enum fixture, which the still-present old tests reference until Task 2 deletes it)

**Interfaces:**
- Produces: `class Effect<out Action>` with `internal val actions: Flow<Action>`, `internal val cancelId: Any?`, `internal val cancelInFlight: Boolean`, `internal val isCancellation: Boolean`; `fun <T> map((Action)->T): Effect<T>`; `fun cancellable(id: Any, cancelInFlight: Boolean = false): Effect<Action>`; companion `none()`, `run(block)`, `send(action)`, `merge(vararg)`, `cancel(id)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.florentmaufras.redux

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EffectFactoryTest {

    @Test
    fun none_emitsNothing() = runTest {
        assertEquals(emptyList<Int>(), Effect.none<Int>().actions.toList())
    }

    @Test
    fun send_emitsSingleAction() = runTest {
        assertEquals(listOf(7), Effect.send(7).actions.toList())
    }

    @Test
    fun run_emitsWhatBlockEmits() = runTest {
        val effect = Effect.run<Int> { emit(1); emit(2) }
        assertEquals(listOf(1, 2), effect.actions.toList())
    }

    @Test
    fun map_transformsEmittedActions() = runTest {
        assertEquals(listOf(2), Effect.send(1).map { it + 1 }.actions.toList())
    }

    @Test
    fun merge_emitsFromAllEffects() = runTest {
        val merged = Effect.merge(Effect.send(1), Effect.send(2)).actions.toList()
        assertEquals(setOf(1, 2), merged.toSet())
    }

    @Test
    fun cancellable_tagsCancelId() {
        val effect = Effect.send(1).cancellable("id", cancelInFlight = true)
        assertEquals("id", effect.cancelId)
        assertEquals(true, effect.cancelInFlight)
    }

    @Test
    fun cancel_marksCancellationForId() {
        val effect = Effect.cancel<Int>("id")
        assertEquals("id", effect.cancelId)
        assertEquals(true, effect.isCancellation)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux:testDebugUnitTest --tests "*EffectFactoryTest" --console=plain`
Expected: FAIL — `Effect` is unresolved / does not compile.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.florentmaufras.redux

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge as mergeFlows

class Effect<out Action> private constructor(
    internal val actions: Flow<Action>,
    internal val cancelId: Any? = null,
    internal val cancelInFlight: Boolean = false,
    internal val isCancellation: Boolean = false,
) {
    fun <T> map(transform: (Action) -> T): Effect<T> =
        Effect(actions.map(transform), cancelId, cancelInFlight, isCancellation)

    fun cancellable(id: Any, cancelInFlight: Boolean = false): Effect<Action> =
        Effect(actions, cancelId = id, cancelInFlight = cancelInFlight)

    companion object {
        fun <Action> none(): Effect<Action> = Effect(emptyFlow())

        fun <Action> run(block: suspend FlowCollector<Action>.() -> Unit): Effect<Action> =
            Effect(flow(block))

        fun <Action> send(action: Action): Effect<Action> = Effect(flowOf(action))

        fun <Action> merge(vararg effects: Effect<Action>): Effect<Action> =
            Effect(mergeFlows(*effects.map { it.actions }.toTypedArray()))

        fun <Action> cancel(id: Any): Effect<Action> =
            Effect(emptyFlow(), cancelId = id, isCancellation = true)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux:testDebugUnitTest --tests "*EffectFactoryTest" --console=plain`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add redux/src/main/java/com/florentmaufras/redux/Effect.kt redux/src/test/java/com/florentmaufras/redux/EffectFactoryTest.kt
git commit -m "feat(redux): add Effect<Action> flow wrapper"
```

---

## Task 2: Single-runtime core — `Reducer`, `ReduceResult`, `Store`; delete old core

**Files:**
- Replace: `redux/src/main/java/com/florentmaufras/redux/ReduceResult.kt`
- Replace: `redux/src/main/java/com/florentmaufras/redux/Reducer.kt`
- Replace: `redux/src/main/java/com/florentmaufras/redux/Store.kt`
- Delete: `EffectResult.kt`, `EffectHandler.kt`, `StateOwner.kt`, `ScopedStateOwner.kt`, `ScopedReducer.kt`
- Delete tests: `EffectHandlerTest.kt`, `StateOwnerTest.kt`, `ScopedReducerTest.kt`, `ReduxTest.kt`, `ReducerTest.kt`, `TestStore.kt`, `ActionTest.kt`, `StateTest.kt`, `EffectTest.kt` (old effect-enum fixture)
- Create test: `redux/src/test/java/com/florentmaufras/redux/StoreTest.kt` (overwrites the old `StoreTest.kt`)

**Interfaces:**
- Consumes: `Effect<Action>` (Task 1).
- Produces:
  - `data class ReduceResult<State, Action>(val state: State, val effect: Effect<Action> = Effect.none())`
  - `fun interface Reducer<State, Action> { fun reduce(state: State, action: Action): ReduceResult<State, Action> }`
  - `abstract class Store<State, Action>(initialState: State) : ViewModel` with `val state: StateFlow<State>`, `val currentState: State`, `protected abstract val reducer: Reducer<State, Action>`, `open fun send(action: Action)`, `internal val trackedEffectJobCount: Int`.

- [ ] **Step 1: Write the failing Store test**

```kotlin
package com.florentmaufras.redux

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoreTest {

    data class CounterState(val count: Int = 0, val loaded: String? = null)

    sealed interface CounterAction {
        data class Add(val by: Int) : CounterAction
        data object Load : CounterAction
        data class Loaded(val value: String) : CounterAction
        data object StartNever : CounterAction
        data object CancelNever : CounterAction
    }

    private class CounterStore(
        initial: CounterState = CounterState(),
        override val reducer: Reducer<CounterState, CounterAction>,
    ) : Store<CounterState, CounterAction>(initial)

    private val reducer = Reducer<CounterState, CounterAction> { state, action ->
        when (action) {
            is CounterAction.Add -> ReduceResult(state.copy(count = state.count + action.by))
            CounterAction.Load -> ReduceResult(state, Effect.run { emit(CounterAction.Loaded("ok")) })
            is CounterAction.Loaded -> ReduceResult(state.copy(loaded = action.value))
            CounterAction.StartNever -> ReduceResult(
                state,
                Effect.run<CounterAction> { kotlinx.coroutines.awaitCancellation() }
                    .cancellable("never"),
            )
            CounterAction.CancelNever -> ReduceResult(state, Effect.cancel("never"))
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun send_reducesAndUpdatesState() {
        val store = CounterStore(reducer = reducer)
        store.send(CounterAction.Add(3))
        assertEquals(3, store.currentState.count)
        assertEquals(CounterState(count = 3), store.state.value)
    }

    @Test
    fun effect_feedsEmittedActionBackThroughSend() = runTest {
        val store = CounterStore(reducer = reducer)
        store.send(CounterAction.Load)
        advanceUntilIdle()
        assertEquals("ok", store.currentState.loaded)
    }

    @Test
    fun completedEffectJob_isRemovedFromTracking() = runTest {
        val store = CounterStore(reducer = reducer)
        store.send(CounterAction.Load)   // finite effect, no cancelId
        advanceUntilIdle()
        assertEquals(0, store.trackedEffectJobCount)
    }

    @Test
    fun cancel_stopsInFlightEffectUnderId() = runTest {
        val store = CounterStore(reducer = reducer)
        store.send(CounterAction.StartNever)
        assertEquals(1, store.trackedEffectJobCount)
        store.send(CounterAction.CancelNever)
        advanceUntilIdle()
        assertEquals(0, store.trackedEffectJobCount)
    }
}
```

- [ ] **Step 2: Replace `ReduceResult.kt`**

```kotlin
package com.florentmaufras.redux

data class ReduceResult<State, Action>(
    val state: State,
    val effect: Effect<Action> = Effect.none(),
)
```

- [ ] **Step 3: Replace `Reducer.kt`**

```kotlin
package com.florentmaufras.redux

fun interface Reducer<State, Action> {
    fun reduce(state: State, action: Action): ReduceResult<State, Action>
}
```

- [ ] **Step 4: Replace `Store.kt`**

```kotlin
package com.florentmaufras.redux

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class Store<State, Action>(initialState: State) : ViewModel() {

    protected abstract val reducer: Reducer<State, Action>

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()
    val currentState: State get() = _state.value

    private val effectJobs = mutableMapOf<Any, Job>()
    internal val trackedEffectJobCount: Int get() = effectJobs.size

    // Note: send is open so MockK can mock it in tests.
    open fun send(action: Action) {
        val result = reducer.reduce(_state.value, action)
        _state.value = result.state
        runEffect(result.effect)
    }

    private fun runEffect(effect: Effect<Action>) {
        val id = effect.cancelId
        if (effect.isCancellation) {
            if (id != null) effectJobs.remove(id)?.cancel()
            return
        }
        if (id != null && effect.cancelInFlight) effectJobs[id]?.cancel()
        val job = viewModelScope.launch { effect.actions.collect { send(it) } }
        if (id != null) {
            effectJobs[id] = job
            job.invokeOnCompletion { effectJobs.remove(id, job) }
        }
    }
}
```

- [ ] **Step 5: Delete obsolete main + test files**

```bash
git rm redux/src/main/java/com/florentmaufras/redux/EffectResult.kt \
       redux/src/main/java/com/florentmaufras/redux/EffectHandler.kt \
       redux/src/main/java/com/florentmaufras/redux/StateOwner.kt \
       redux/src/main/java/com/florentmaufras/redux/ScopedStateOwner.kt \
       redux/src/main/java/com/florentmaufras/redux/ScopedReducer.kt \
       redux/src/test/java/com/florentmaufras/redux/EffectHandlerTest.kt \
       redux/src/test/java/com/florentmaufras/redux/StateOwnerTest.kt \
       redux/src/test/java/com/florentmaufras/redux/ScopedReducerTest.kt \
       redux/src/test/java/com/florentmaufras/redux/ReduxTest.kt \
       redux/src/test/java/com/florentmaufras/redux/ReducerTest.kt \
       redux/src/test/java/com/florentmaufras/redux/TestStore.kt \
       redux/src/test/java/com/florentmaufras/redux/ActionTest.kt \
       redux/src/test/java/com/florentmaufras/redux/StateTest.kt \
       redux/src/test/java/com/florentmaufras/redux/EffectTest.kt
```

(Note: `Scope.kt` still references the old 4-lens shape and will not compile until Task 3. To keep `:redux` green at the end of Task 2, also apply Step 6.)

- [ ] **Step 6: Temporarily neutralize `Scope.kt`**

Replace `redux/src/main/java/com/florentmaufras/redux/Scope.kt` with the final shape now (it has no dependency on deleted types), so the module compiles:

```kotlin
package com.florentmaufras.redux

interface Scope<ParentState, ParentAction, ChildState, ChildAction> {
    val toChildState: (ParentState) -> ChildState
    val fromChildState: (ParentState, ChildState) -> ParentState
    val toChildAction: (ParentAction) -> ChildAction?
    val embedChildAction: (ChildAction) -> ParentAction
}
```

- [ ] **Step 7: Run test to verify the new Store test passes and the module is green**

Run: `./gradlew :redux:testDebugUnitTest --console=plain`
Expected: PASS — `EffectTest` (7) + `StoreTest` (4); no compilation errors.

- [ ] **Step 8: Commit**

```bash
git add -A redux/src
git commit -m "refactor(redux): replace core with single-runtime Store and Effect<Action>"
```

---

## Task 3: `scope` reducer operator + `Scope`

**Files:**
- Create: `redux/src/main/java/com/florentmaufras/redux/Scopes.kt`
- Test: `redux/src/test/java/com/florentmaufras/redux/ScopeTest.kt`

**Interfaces:**
- Consumes: `Reducer`, `ReduceResult`, `Effect`, `Scope` (Task 2 final shape).
- Produces: `fun <PS, PA, CS, CA> Reducer<PS, PA>.scope(scope: Scope<PS, PA, CS, CA>, child: Reducer<CS, CA>): Reducer<PS, PA>`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.florentmaufras.redux

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ScopeTest {

    data class ChildState(val count: Int = 0)
    data class ParentState(val child: ChildState = ChildState(), val parentSaw: String? = null)

    sealed interface ChildAction {
        data class Increment(val by: Int) : ChildAction
        data object NotifyParent : ChildAction
    }

    sealed interface ParentAction {
        data class Child(val action: ChildAction) : ParentAction
    }

    private val childReducer = Reducer<ChildState, ChildAction> { state, action ->
        when (action) {
            is ChildAction.Increment -> ReduceResult(state.copy(count = state.count + action.by))
            ChildAction.NotifyParent -> ReduceResult(state, Effect.send(ChildAction.Increment(100)))
        }
    }

    private val parentReducer = Reducer<ParentState, ParentAction> { state, action ->
        when (action) {
            is ParentAction.Child ->
                if (action.action is ChildAction.NotifyParent)
                    ReduceResult(state.copy(parentSaw = "notified"))
                else ReduceResult(state)
        }
    }

    private val scope = object : Scope<ParentState, ParentAction, ChildState, ChildAction> {
        override val toChildState: (ParentState) -> ChildState = { it.child }
        override val fromChildState: (ParentState, ChildState) -> ParentState = { p, c -> p.copy(child = c) }
        override val toChildAction: (ParentAction) -> ChildAction? = { (it as? ParentAction.Child)?.action }
        override val embedChildAction: (ChildAction) -> ParentAction = { ParentAction.Child(it) }
    }

    private val composed = parentReducer.scope(scope, childReducer)

    @Test
    fun childAction_updatesChildStateInParent() {
        val result = composed.reduce(ParentState(), ParentAction.Child(ChildAction.Increment(5)))
        assertEquals(ChildState(count = 5), result.state.child)
    }

    @Test
    fun parentReducer_observesChildAction() {
        val result = composed.reduce(ParentState(), ParentAction.Child(ChildAction.NotifyParent))
        assertEquals("notified", result.state.parentSaw)
    }

    @Test
    fun childEffect_isLiftedToParentActions() = runTest {
        val result = composed.reduce(ParentState(), ParentAction.Child(ChildAction.NotifyParent))
        assertEquals(
            listOf(ParentAction.Child(ChildAction.Increment(100))),
            result.effect.actions.toList(),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux:testDebugUnitTest --tests "*ScopeTest" --console=plain`
Expected: FAIL — `scope` is unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.florentmaufras.redux

fun <ParentState, ParentAction, ChildState, ChildAction> Reducer<ParentState, ParentAction>.scope(
    scope: Scope<ParentState, ParentAction, ChildState, ChildAction>,
    child: Reducer<ChildState, ChildAction>,
): Reducer<ParentState, ParentAction> = Reducer { state, action ->
    val parent = this.reduce(state, action)            // parent runs first / observes the action
    val childAction = scope.toChildAction(action)
        ?: return@Reducer parent
    val childResult = child.reduce(scope.toChildState(parent.state), childAction)
    ReduceResult(
        scope.fromChildState(parent.state, childResult.state),
        Effect.merge(parent.effect, childResult.effect.map(scope.embedChildAction)),
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux:testDebugUnitTest --tests "*ScopeTest" --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add redux/src/main/java/com/florentmaufras/redux/Scopes.kt redux/src/test/java/com/florentmaufras/redux/ScopeTest.kt
git commit -m "feat(redux): add scope reducer operator composing child reducers"
```

---

## Task 4: Migrate app main to the new core

**Files:**
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducer.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStore.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesViewModel.kt`
- Delete: `UniversitiesEffect.kt`, `UniversitiesEffectHandler.kt`

**Interfaces:**
- Consumes: `Reducer`, `ReduceResult`, `Effect`, `Store` (new core).
- Produces:
  - `class UniversitiesReducer(service, openUrl, timber) : Reducer<UniversitiesState, UniversitiesAction>`
  - `class UniversitiesStore(...) : Store<UniversitiesState, UniversitiesAction>`
  - `UniversitiesViewModel.dispatchAction` now calls `store.send`.

- [ ] **Step 1: Replace `UniversitiesReducer.kt`**

```kotlin
package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.Effect
import com.florentmaufras.redux.ReduceResult
import com.florentmaufras.redux.Reducer
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import timber.log.Timber

class UniversitiesReducer(
    private val universitiesService: UniversitiesService,
    private val openUrl: (String) -> Unit = {},
    private val timber: Timber.Tree = Timber.asTree(),
) : Reducer<UniversitiesState, UniversitiesAction> {

    override fun reduce(
        state: UniversitiesState,
        action: UniversitiesAction,
    ): ReduceResult<UniversitiesState, UniversitiesAction> = when (action) {
        is UniversitiesAction.LoadUniversities -> ReduceResult(
            state.copy(viewState = ViewState.Loading, countrySearched = action.country),
            Effect.run<UniversitiesAction> {
                try {
                    emit(UniversitiesAction.UniversitiesLoaded(universitiesService.getUniversities(action.country)))
                } catch (e: Exception) {
                    timber.e(e)
                    emit(UniversitiesAction.LoadError(e.message))
                }
            }.cancellable("search", cancelInFlight = true),
        )

        is UniversitiesAction.UniversitiesLoaded ->
            ReduceResult(state.copy(viewState = ViewState.Loaded(action.universities)))

        is UniversitiesAction.LoadError ->
            ReduceResult(state.copy(viewState = ViewState.Error(action.message)))

        is UniversitiesAction.OpenWebsite ->
            ReduceResult(state, Effect.run { openUrl(action.url) })
    }
}
```

- [ ] **Step 2: Replace `UniversitiesStore.kt`**

```kotlin
package com.florentmaufras.reduxdemo.universities.data

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import com.florentmaufras.redux.Store
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private fun defaultRetrofit(): Retrofit = Retrofit.Builder()
    .baseUrl("http://universities.hipolabs.com")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

class UniversitiesStore(
    application: Application? = null,
    initialState: UniversitiesState = UniversitiesState(),
    override val reducer: UniversitiesReducer = UniversitiesReducer(
        universitiesService = UniversitiesService(defaultRetrofit()),
        openUrl = { url ->
            application?.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
    ),
) : Store<UniversitiesState, UniversitiesAction>(initialState) {

    init {
        if (currentState.countrySearched.isNotBlank()) {
            send(UniversitiesAction.LoadUniversities(currentState.countrySearched))
        }
    }
}
```

- [ ] **Step 3: Update `UniversitiesViewModel.kt`**

```kotlin
package com.florentmaufras.reduxdemo.universities.data

import kotlinx.coroutines.flow.StateFlow

class UniversitiesViewModel(val store: UniversitiesStore) {

    val stateFlow: StateFlow<UniversitiesState> = store.state

    fun dispatchAction(action: UniversitiesAction) {
        store.send(action)
    }
}
```

- [ ] **Step 4: Delete the obsolete effect files**

```bash
git rm app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffect.kt \
       app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffectHandler.kt
```

- [ ] **Step 5: Verify app main compiles**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL (app test sources not compiled yet).

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main
git commit -m "refactor(app): migrate universities feature to single-runtime store"
```

---

## Task 5: Migrate app tests

**Files:**
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducerTest.kt`
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStoreTest.kt`
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesViewModelTest.kt`

**Interfaces:**
- Consumes: new `UniversitiesReducer(service)`, `UniversitiesStore(initialState, reducer)`, `UniversitiesViewModel.dispatchAction` → `store.send`.

- [ ] **Step 1: Replace `UniversitiesReducerTest.kt` (assert state only — effects are opaque)**

```kotlin
package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UniversitiesReducerTest {

    private lateinit var reducer: UniversitiesReducer

    @BeforeEach
    fun setup() {
        reducer = UniversitiesReducer(universitiesService = mockk(relaxed = true))
    }

    @Test
    fun loadUniversities_setsLoadingAndCountry() {
        val result = reducer.reduce(UniversitiesState(), UniversitiesAction.LoadUniversities("France"))
        assertEquals(
            UniversitiesState(viewState = ViewState.Loading, countrySearched = "France"),
            result.state,
        )
    }

    @Test
    fun universitiesLoaded_setsLoadedState() {
        val universities = emptyList<University>()
        val result = reducer.reduce(UniversitiesState(), UniversitiesAction.UniversitiesLoaded(universities))
        assertEquals(ViewState.Loaded(universities), result.state.viewState)
    }

    @Test
    fun loadError_setsErrorState() {
        val result = reducer.reduce(UniversitiesState(), UniversitiesAction.LoadError(null))
        assertEquals(ViewState.Error(null), result.state.viewState)
    }

    @Test
    fun openWebsite_leavesStateUnchanged() {
        val state = UniversitiesState(countrySearched = "France")
        val result = reducer.reduce(state, UniversitiesAction.OpenWebsite("https://example.com"))
        assertEquals(state, result.state)
    }
}
```

- [ ] **Step 2: Replace `UniversitiesStoreTest.kt` (drives effects against a fake service)**

```kotlin
package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UniversitiesStoreTest {

    private val service: UniversitiesService = mockk()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadUniversities_emitsLoadedStateFromService() = runTest {
        val universities = listOf(mockk<University>())
        coEvery { service.getUniversities("France") } returns universities

        val store = UniversitiesStore(
            initialState = UniversitiesState(countrySearched = ""),
            reducer = UniversitiesReducer(universitiesService = service),
        )

        store.send(UniversitiesAction.LoadUniversities("France"))
        advanceUntilIdle()

        assertEquals(ViewState.Loaded(universities), store.currentState.viewState)
    }

    @Test
    fun loadUniversities_emitsErrorStateOnFailure() = runTest {
        coEvery { service.getUniversities("France") } throws RuntimeException("boom")

        val store = UniversitiesStore(
            initialState = UniversitiesState(countrySearched = ""),
            reducer = UniversitiesReducer(universitiesService = service),
        )

        store.send(UniversitiesAction.LoadUniversities("France"))
        advanceUntilIdle()

        assertEquals(ViewState.Error("boom"), store.currentState.viewState)
    }
}
```

- [ ] **Step 3: Replace `UniversitiesViewModelTest.kt` (verify `send`)**

```kotlin
package com.florentmaufras.reduxdemo.universities.data

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UniversitiesViewModelTest {

    private val mockedStore: UniversitiesStore = mockk()
    private lateinit var viewModel: UniversitiesViewModel

    private val mockedState = mockk<UniversitiesState>()
    private val stateFlow = MutableStateFlow(mockedState)

    @BeforeEach
    fun setup() {
        every { mockedStore.state }.returns(stateFlow)
        viewModel = UniversitiesViewModel(mockedStore)
    }

    @Test
    fun dispatchAction_forwardsToStoreSend() {
        val action = mockk<UniversitiesAction>()
        justRun { mockedStore.send(action) }

        viewModel.dispatchAction(action)

        verify(exactly = 1) { mockedStore.send(action) }
    }
}
```

- [ ] **Step 4: Run app tests**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: PASS — reducer (4), store (2), viewmodel (1).

- [ ] **Step 5: Commit**

```bash
git add -A app/src/test
git commit -m "test(app): cover universities feature on single-runtime store"
```

---

## Task 6: KDoc, version bump, full verification

**Files:**
- Modify: `Effect.kt`, `Reducer.kt`, `ReduceResult.kt`, `Store.kt`, `Scope.kt`, `Scopes.kt` (KDoc)
- Modify: `redux/build.gradle.kts` (version)

- [ ] **Step 1: Add KDoc to each public core type**

Add a KDoc block above each declaration. `Effect`:

```kotlin
/**
 * A unit of asynchronous work, parameterized by the action it feeds back into the
 * store. An effect *is* a `Flow<Action>`; the store collects it and re-sends each
 * emitted action. Build with [none], [run], [send], [merge]; lift with [map];
 * control lifetime with [cancellable] / [cancel].
 */
```

`Reducer`:

```kotlin
/** Pure function: given [State] and an [Action], returns the next state and an [Effect]. */
```

`ReduceResult`:

```kotlin
/** Output of [Reducer.reduce]: the next [state] and an [effect] (default [Effect.none]). */
```

`Store`:

```kotlin
/**
 * Single-runtime store. [send] reduces an action, commits the new [state], runs the
 * returned [Effect], and re-sends the actions it emits. Cancellation is keyed by an
 * effect's `cancelId`.
 */
```

`Scope`:

```kotlin
/**
 * Lenses mapping a parent feature to a child: state down/up ([toChildState] /
 * [fromChildState]) and the action prism ([toChildAction] extract down,
 * [embedChildAction] embed up). Used by [scope] to compose reducers.
 */
```

`scope` (in `Scopes.kt`):

```kotlin
/**
 * Composes [child] into this parent reducer over [scope]: the parent reduces first
 * (and may observe the child action), then the child reduces its slice; the child's
 * effect is lifted to parent actions via [Scope.embedChildAction] and merged.
 */
```

- [ ] **Step 2: Bump the version**

Modify `redux/build.gradle.kts`: change `version = "2.0.0"` to `version = "3.0.0"`.

- [ ] **Step 3: Run the full suite**

Run: `./gradlew :redux:testDebugUnitTest :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, zero failures across both modules.

- [ ] **Step 4: Commit**

```bash
git add redux/src/main redux/build.gradle.kts
git commit -m "docs(redux): KDoc the TCA core and bump version to 3.0.0"
```

---

## Self-Review

**Spec coverage:**
- `Effect<Action>` (Flow wrapper, none/run/send/merge/map/cancellable/cancel) → Task 1. ✓
- `Reducer<State, Action>` / `ReduceResult<State, Action>` → Task 2. ✓
- Single-runtime `Store` with re-send loop + cancellation → Task 2. ✓
- Deletions (EffectResult, EffectHandler, StateOwner/OwnedStateOwner/ScopedStateOwner, ScopedReducer) → Task 2. ✓
- `scope` operator + `Scope` (total `embedChildAction`) → Task 3. ✓
- App migration (delete UniversitiesEffect/Handler, inline effects, Store, ViewModel) → Task 4. ✓
- TestStore-style effect testing against fakes → Task 5 (store test drives effects via fake service). ✓
- KDoc + version 3.0.0 → Task 6. ✓

**Placeholder scan:** No TBD/TODO; every code step contains complete code. ✓

**Type consistency:** `send(action)` used consistently; `Reducer<State, Action>.reduce(state, action)` order consistent across Tasks 2/3/4; `scope(scope, child)` signature matches Task 3 definition and Task 6 KDoc; `Effect` factory names match Task 1 across all usages. ✓

**Note on a spec deviation:** the spec described a standalone `TestStore<State, Action>` harness; this plan instead exercises effects through concrete `Store` subclasses in the test sources (redux `CounterStore`, app `UniversitiesStore`), which is simpler and covers the same behavior. If a reusable `TestStore` is later wanted, add it as a follow-up.
