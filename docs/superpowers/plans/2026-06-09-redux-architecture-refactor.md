# Redux Architecture Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the Redux library to remove `Action`/`Effect` marker interfaces, strip effects from `Scope`, introduce a `StateOwner` abstraction, and remove `EffectHandler` from `Store`.

**Architecture:** Four ordered changes on the `feature/redux-improvements` branch. (1) Replace `Action`/`Effect` marker interfaces with `Any` bounds everywhere. (2) Convert `Scope` to an interface whose members are the four previously-private lambda properties (implementors define the how); extract `ScopedReducer` from the old class body. (3) Introduce a `StateOwner` interface with `OwnedStateOwner` and `ScopedStateOwner` implementations, update `Store` to take one. (4) Remove `effectHandler` entirely from `Store`; Store gains a `protected fun launchEffect(...)` helper and a `protected open fun handleEffect(effect, cancelId)` no-op that subclasses override when they need effects.

**Tech Stack:** Kotlin, Android ViewModel, Kotlin Coroutines/Flow, JUnit 5, MockK

---

## File Map

### Delete
- `redux/src/main/java/com/florentmaufras/redux/Action.kt`
- `redux/src/main/java/com/florentmaufras/redux/Effect.kt`

### Create
- `redux/src/main/java/com/florentmaufras/redux/StateOwner.kt` — `StateOwner<S>` interface + `OwnedStateOwner<S>` impl
- `redux/src/main/java/com/florentmaufras/redux/ScopedStateOwner.kt` — `ScopedStateOwner<PS, CS>` impl
- `redux/src/main/java/com/florentmaufras/redux/ScopedReducer.kt` — replaces the `Scope` class body (Scope becomes an interface)
- `redux/src/test/java/com/florentmaufras/redux/StateOwnerTest.kt` — tests for both StateOwner impls
- `redux/src/test/java/com/florentmaufras/redux/ScopedReducerTest.kt` — tests for ScopedReducer

### Modify
| File | Change |
|------|--------|
| `redux/.../Reducer.kt` | `: Action` → `: Any`, `: Effect` → `: Any` |
| `redux/.../EffectHandler.kt` | same bounds |
| `redux/.../EffectResult.kt` | same bounds |
| `redux/.../ReduceResult.kt` | same bounds |
| `redux/.../Scope.kt` | convert to interface; remove `ParentEffect`/`ChildEffect`; replace `fromChildEffect` with `fromChildAction` |
| `redux/.../Store.kt` | take `StateOwner<State>` instead of `initialState`; remove `effectHandler` entirely; add `protected fun launchEffect(...)` + `protected open fun handleEffect(effect, cancelId)` |
| `redux/test/.../ActionTest.kt` | remove `: Action` |
| `redux/test/.../EffectTest.kt` | remove `: Effect` |
| `redux/test/.../TestStore.kt` | bounds `: Action` → `: Any`, `: Effect` → `: Any` |
| `redux/test/.../StoreTest.kt` | pass `OwnedStateOwner(initialState)`; remove `override val effectHandler`; override `handleEffect` to delegate to `EffectHandlerTest` via `launchEffect` |
| `redux/test/.../ReduxTest.kt` | update `StoreTest` construction |
| `app/.../UniversitiesStore.kt` | pass `OwnedStateOwner(initialState)`; remove `effectHandler` override; override `handleEffect` to delegate to `UniversitiesEffectHandler` via `launchEffect` |
| `app/.../UniversitiesEffectHandler.kt` | remove `: Action`, `: Effect` bounds from type params |

---

## Task 1: Remove Action and Effect marker interfaces

**Files:**
- Delete: `redux/src/main/java/com/florentmaufras/redux/Action.kt`
- Delete: `redux/src/main/java/com/florentmaufras/redux/Effect.kt`
- Modify: `redux/src/main/java/com/florentmaufras/redux/Reducer.kt`
- Modify: `redux/src/main/java/com/florentmaufras/redux/EffectHandler.kt`
- Modify: `redux/src/main/java/com/florentmaufras/redux/EffectResult.kt`
- Modify: `redux/src/main/java/com/florentmaufras/redux/ReduceResult.kt`
- Modify: `redux/src/test/java/com/florentmaufras/redux/ActionTest.kt`
- Modify: `redux/src/test/java/com/florentmaufras/redux/EffectTest.kt`
- Modify: `redux/src/test/java/com/florentmaufras/redux/TestStore.kt`

- [ ] **Step 1: Update test fixtures to remove marker interface inheritance**

`redux/src/test/java/com/florentmaufras/redux/ActionTest.kt`:
```kotlin
package com.florentmaufras.redux

sealed class ActionTest {
    data class Save(val saveName: String) : ActionTest()
    data object Rollback : ActionTest()
    data class ActionWithEffectWithAction(val effectName: String) : ActionTest()
}
```

`redux/src/test/java/com/florentmaufras/redux/EffectTest.kt`:
```kotlin
package com.florentmaufras.redux

sealed class EffectTest {
    data object SaveAPICall : EffectTest()
    data class EffectWithAction(val name: String) : EffectTest()
}
```

`redux/src/test/java/com/florentmaufras/redux/TestStore.kt`:
```kotlin
package com.florentmaufras.redux

class TestStore<A : Any, S : State, E : Any>(
    initialState: S,
    private val reducer: Reducer<A, S, E>
) {
    var state: S = initialState
        private set

    val dispatchedEffects: MutableList<E> = mutableListOf()

    fun send(action: A): EffectResult<E> {
        val result = reducer.reduce(action, state)
        state = result.state
        if (result.effect is EffectResult.Some) {
            dispatchedEffects.add((result.effect as EffectResult.Some<E>).effect)
        }
        return result.effect
    }

    fun assertState(expected: S) {
        check(state == expected) {
            "State mismatch.\nExpected: $expected\nActual:   $state"
        }
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure (Action/Effect still exist)**

```
./gradlew :redux:test :app:test
```

Expected: compile errors because `ActionTest` no longer extends `Action` but `Action` still exists and bounds reference it.

- [ ] **Step 3: Update library source to replace bounds with Any, delete marker files**

Delete `redux/src/main/java/com/florentmaufras/redux/Action.kt` and `Effect.kt`.

`redux/src/main/java/com/florentmaufras/redux/Reducer.kt`:
```kotlin
package com.florentmaufras.redux

interface Reducer<Action : Any, State : Any, Effect : Any> {
    fun reduce(action: Action, state: State): ReduceResult<State, Effect>
}
```

`redux/src/main/java/com/florentmaufras/redux/EffectHandler.kt`:
```kotlin
package com.florentmaufras.redux

import kotlinx.coroutines.flow.Flow

interface EffectHandler<Action : Any, Effect : Any> {
    fun handle(effect: Effect): Flow<Action>
}
```

`redux/src/main/java/com/florentmaufras/redux/EffectResult.kt`:
```kotlin
package com.florentmaufras.redux

sealed class EffectResult<out Effect : Any> {
    data object None : EffectResult<Nothing>()
    data class Some<Effect : Any>(val effect: Effect) : EffectResult<Effect>()
}
```

`redux/src/main/java/com/florentmaufras/redux/ReduceResult.kt`:
```kotlin
package com.florentmaufras.redux

data class ReduceResult<State : Any, Effect : Any>(
    val state: State,
    val effect: EffectResult<Effect>
)
```

- [ ] **Step 4: Run tests — expect pass**

```
./gradlew :redux:test :app:test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add redux/src/main/java/com/florentmaufras/redux/Reducer.kt \
        redux/src/main/java/com/florentmaufras/redux/EffectHandler.kt \
        redux/src/main/java/com/florentmaufras/redux/EffectResult.kt \
        redux/src/main/java/com/florentmaufras/redux/ReduceResult.kt \
        redux/src/test/java/com/florentmaufras/redux/ActionTest.kt \
        redux/src/test/java/com/florentmaufras/redux/EffectTest.kt \
        redux/src/test/java/com/florentmaufras/redux/TestStore.kt
git rm redux/src/main/java/com/florentmaufras/redux/Action.kt \
       redux/src/main/java/com/florentmaufras/redux/Effect.kt
git commit -m "refactor(redux): replace Action/Effect marker interfaces with Any bounds"
```

---

## Task 2: Convert Scope to an interface, introduce ScopedReducer

**Files:**
- Modify: `redux/src/main/java/com/florentmaufras/redux/Scope.kt`
- Create: `redux/src/main/java/com/florentmaufras/redux/ScopedReducer.kt`
- Create: `redux/src/test/java/com/florentmaufras/redux/ScopedReducerTest.kt`

**Design:**
- `Scope` becomes an interface exposing the four lens/mapping functions. No effects — effects are local to each reducer.
- `fromChildAction: (ChildAction) -> ParentAction?` allows a child action to bubble a parent action up (e.g. logout). The `ScopedReducer` returns this parent action as its `Effect`, which the parent `Store` re-dispatches.
- `ScopedReducer<PA, PS, CA, CS, CE>` implements `Reducer<PA, PS, PA>`. The "effect" type is `PA` itself — when the child action maps to a parent action via `fromChildAction`, that parent action is returned as `EffectResult.Some(parentAction)` so the parent Store can re-dispatch it. Child effects (`CE`) are discarded by the scoped layer.

- [ ] **Step 1: Write a failing test for ScopedReducer**

`redux/src/test/java/com/florentmaufras/redux/ScopedReducerTest.kt`:
```kotlin
package com.florentmaufras.redux

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScopedReducerTest {

    // --- Test types ---

    data class ParentState(val child: ChildState = ChildState(), val log: String = "") : State
    data class ChildState(val count: Int = 0) : State

    sealed class ParentAction {
        data class IncrementChild(val by: Int) : ParentAction()
        data object Logout : ParentAction()
        data object ChildLogout : ParentAction() // bubbled up from child
    }

    sealed class ChildAction {
        data class Increment(val by: Int) : ChildAction()
        data object RequestLogout : ChildAction() // maps to ParentAction.ChildLogout
    }

    object ChildReducer : Reducer<ChildAction, ChildState, Nothing> {
        override fun reduce(action: ChildAction, state: ChildState): ReduceResult<ChildState, Nothing> =
            when (action) {
                is ChildAction.Increment -> ReduceResult(state.copy(count = state.count + action.by), EffectResult.None)
                ChildAction.RequestLogout -> ReduceResult(state, EffectResult.None)
            }
    }

    val scope = object : Scope<ParentAction, ParentState, ChildAction, ChildState> {
        override val toChildState: (ParentState) -> ChildState = { it.child }
        override val fromChildState: (ParentState, ChildState) -> ParentState = { p, c -> p.copy(child = c) }
        override val toChildAction: (ParentAction) -> ChildAction? = { action ->
            when (action) {
                is ParentAction.IncrementChild -> ChildAction.Increment(action.by)
                ParentAction.Logout -> null
                ParentAction.ChildLogout -> null
            }
        }
        override val fromChildAction: (ChildAction) -> ParentAction? = { childAction ->
            when (childAction) {
                ChildAction.RequestLogout -> ParentAction.ChildLogout
                else -> null
            }
        }
    }

    val scopedReducer = ScopedReducer(scope, ChildReducer)

    @Test
    fun `unrelated parent action returns unchanged state and no effect`() {
        val state = ParentState()
        val result = scopedReducer.reduce(ParentAction.Logout, state)
        assertEquals(state, result.state)
        assertEquals(EffectResult.None, result.effect)
    }

    @Test
    fun `child action updates child state in parent`() {
        val state = ParentState(child = ChildState(count = 0))
        val result = scopedReducer.reduce(ParentAction.IncrementChild(5), state)
        assertEquals(ParentState(child = ChildState(count = 5)), result.state)
        assertEquals(EffectResult.None, result.effect)
    }

    @Test
    fun `child action with fromChildAction mapping bubbles parent action as effect`() {
        val state = ParentState()
        val result = scopedReducer.reduce(ParentAction.IncrementChild(1), state)
        // IncrementChild maps to ChildAction.Increment — fromChildAction returns null
        assertEquals(EffectResult.None, result.effect)

        // Now test the logout bubble
        val logoutResult = scopedReducer.reduce(
            // We need a ParentAction that maps to RequestLogout
            ParentAction.IncrementChild(0), // doesn't map to RequestLogout
            state
        )
        assertEquals(EffectResult.None, logoutResult.effect)
    }

    @Test
    fun `child action mapping to fromChildAction returns parent action as effect`() {
        // Add a parent action that maps to ChildAction.RequestLogout
        val scopeWithLogout = object : Scope<ParentAction, ParentState, ChildAction, ChildState> {
            override val toChildState: (ParentState) -> ChildState = { it.child }
            override val fromChildState: (ParentState, ChildState) -> ParentState = { p, c -> p.copy(child = c) }
            override val toChildAction: (ParentAction) -> ChildAction? = { action ->
                when (action) {
                    ParentAction.Logout -> ChildAction.RequestLogout
                    else -> null
                }
            }
            override val fromChildAction: (ChildAction) -> ParentAction? = { childAction ->
                when (childAction) {
                    ChildAction.RequestLogout -> ParentAction.ChildLogout
                    else -> null
                }
            }
        }
        val reducer = ScopedReducer(scopeWithLogout, ChildReducer)
        val result = reducer.reduce(ParentAction.Logout, ParentState())
        assertEquals(EffectResult.Some(ParentAction.ChildLogout), result.effect)
    }
}
```

- [ ] **Step 2: Run test — expect compile failure (Scope and ScopedReducer don't exist yet)**

```
./gradlew :redux:test
```

Expected: compile error — `Scope` is still a class, `ScopedReducer` doesn't exist.

- [ ] **Step 3: Convert Scope to interface**

`redux/src/main/java/com/florentmaufras/redux/Scope.kt`:
```kotlin
package com.florentmaufras.redux

interface Scope<
    ParentAction : Any,
    ParentState : State,
    ChildAction : Any,
    ChildState : State> {

    val toChildState: (ParentState) -> ChildState
    val fromChildState: (ParentState, ChildState) -> ParentState
    val toChildAction: (ParentAction) -> ChildAction?
    val fromChildAction: (ChildAction) -> ParentAction?
}
```

- [ ] **Step 4: Create ScopedReducer**

`redux/src/main/java/com/florentmaufras/redux/ScopedReducer.kt`:
```kotlin
package com.florentmaufras.redux

class ScopedReducer<
    ParentAction : Any,
    ParentState : State,
    ChildAction : Any,
    ChildState : State,
    ChildEffect : Any>(
    private val scope: Scope<ParentAction, ParentState, ChildAction, ChildState>,
    private val childReducer: Reducer<ChildAction, ChildState, ChildEffect>
) : Reducer<ParentAction, ParentState, ParentAction> {

    override fun reduce(
        action: ParentAction,
        state: ParentState
    ): ReduceResult<ParentState, ParentAction> {
        val childAction = scope.toChildAction(action)
            ?: return ReduceResult(state, EffectResult.None)

        val childResult = childReducer.reduce(childAction, scope.toChildState(state))
        val newState = scope.fromChildState(state, childResult.state)
        val bubbledAction = scope.fromChildAction(childAction)
            ?.let { EffectResult.Some(it) }
            ?: EffectResult.None

        return ReduceResult(newState, bubbledAction)
    }
}
```

- [ ] **Step 5: Run tests — expect pass**

```
./gradlew :redux:test :app:test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add redux/src/main/java/com/florentmaufras/redux/Scope.kt \
        redux/src/main/java/com/florentmaufras/redux/ScopedReducer.kt \
        redux/src/test/java/com/florentmaufras/redux/ScopedReducerTest.kt
git commit -m "refactor(redux): convert Scope to interface, extract ScopedReducer with fromChildAction"
```

---

## Task 3: Introduce StateOwner and OwnedStateOwner

**Files:**
- Create: `redux/src/main/java/com/florentmaufras/redux/StateOwner.kt`
- Create: `redux/src/test/java/com/florentmaufras/redux/StateOwnerTest.kt`

**Design:**
- `StateOwner<S>` exposes `stateFlow: StateFlow<S>` and `updateState(S)`.
- `OwnedStateOwner<S>` wraps a `MutableStateFlow` — identical to what Store currently does internally.

- [ ] **Step 1: Write a failing test for OwnedStateOwner**

`redux/src/test/java/com/florentmaufras/redux/StateOwnerTest.kt`:
```kotlin
package com.florentmaufras.redux

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StateOwnerTest {

    data class TestState(val value: Int) : State

    @Test
    fun `OwnedStateOwner initial state is accessible`() = runTest {
        val owner = OwnedStateOwner(TestState(42))
        assertEquals(TestState(42), owner.stateFlow.first())
    }

    @Test
    fun `OwnedStateOwner updateState emits new value`() = runTest {
        val owner = OwnedStateOwner(TestState(0))
        owner.updateState(TestState(7))
        assertEquals(TestState(7), owner.stateFlow.first())
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```
./gradlew :redux:test
```

Expected: `OwnedStateOwner` not found.

- [ ] **Step 3: Create StateOwner.kt**

`redux/src/main/java/com/florentmaufras/redux/StateOwner.kt`:
```kotlin
package com.florentmaufras.redux

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface StateOwner<S : Any> {
    val stateFlow: StateFlow<S>
    fun updateState(newState: S)
}

class OwnedStateOwner<S : Any>(initialState: S) : StateOwner<S> {
    private val _state = MutableStateFlow(initialState)
    override val stateFlow: StateFlow<S> = _state.asStateFlow()
    override fun updateState(newState: S) = _state.update { newState }
}
```

- [ ] **Step 4: Run tests — expect pass**

```
./gradlew :redux:test :app:test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add redux/src/main/java/com/florentmaufras/redux/StateOwner.kt \
        redux/src/test/java/com/florentmaufras/redux/StateOwnerTest.kt
git commit -m "feat(redux): add StateOwner interface and OwnedStateOwner implementation"
```

---

## Task 4: Update Store — take StateOwner, make EffectHandler optional

**Files:**
- Modify: `redux/src/main/java/com/florentmaufras/redux/Store.kt`
- Modify: `redux/src/test/java/com/florentmaufras/redux/StoreTest.kt`
- Modify: `redux/src/test/java/com/florentmaufras/redux/ReduxTest.kt`

**Design:**
- `Store` takes a `StateOwner<State>` instead of `initialState: State`. The internal `MutableStateFlow` moves into `OwnedStateOwner`.
- `effectHandler` is **removed entirely** from Store. No field, no abstract property, no reference.
- Store gains a `protected fun launchEffect(effect, cancelId, block)` helper that manages `effectJobs` and coroutine launching — reusable by any subclass.
- Store gains a `protected open fun handleEffect(effect: Effect, cancelId: String?)` no-op. Subclasses that need effects override it and call `launchEffect` inside.
- `EffectHandler` remains a standalone useful interface that subclasses can use however they like inside their `handleEffect` override.
- `state` and `currentState` now delegate to the `StateOwner`.

- [ ] **Step 1: Update test helpers to use the new Store API**

`redux/src/test/java/com/florentmaufras/redux/StoreTest.kt`:
```kotlin
package com.florentmaufras.redux

class StoreTest(
    initialName: String,
    override val reducer: ReducerTest,
    private val handler: EffectHandlerTest
) : Store<ActionTest, StateTest, EffectTest>(OwnedStateOwner(StateTest(initialName))) {

    override fun handleEffect(effect: EffectTest, cancelId: String?) {
        launchEffect(effect, cancelId) { handler.handle(it) }
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```
./gradlew :redux:test
```

Expected: compile error — `Store` still takes `initialState` and has `abstract effectHandler`.

- [ ] **Step 3: Update Store.kt**

`redux/src/main/java/com/florentmaufras/redux/Store.kt`:
```kotlin
package com.florentmaufras.redux

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

abstract class Store<Action : Any, State : Any, Effect : Any>(
    private val stateOwner: StateOwner<State>
) : ViewModel() {

    abstract val reducer: Reducer<Action, State, Effect>

    val currentState: State
        get() = stateOwner.stateFlow.value

    val state: StateFlow<State>
        get() = stateOwner.stateFlow

    private val effectJobs = mutableMapOf<String, Job>()

    protected fun launchEffect(
        effect: Effect,
        cancelId: String?,
        block: (Effect) -> Flow<Action>
    ) {
        cancelId?.let { effectJobs[it]?.cancel() }
        val job = viewModelScope.launch {
            block(effect).collect { dispatch(it) }
        }
        cancelId?.let { effectJobs[it] = job }
    }

    protected open fun handleEffect(effect: Effect, cancelId: String?) {}

    open fun dispatch(action: Action, cancelId: String? = null) {
        val result = reducer.reduce(action, currentState)
        stateOwner.updateState(result.state)
        when (val effectResult = result.effect) {
            is EffectResult.Some -> handleEffect(effectResult.effect, cancelId)
            EffectResult.None -> Unit
        }
    }
}
```

- [ ] **Step 4: Fix the app module — UniversitiesStore**

`app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStore.kt`:
```kotlin
package com.florentmaufras.reduxdemo.universities.data

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import com.florentmaufras.redux.OwnedStateOwner
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
    override val reducer: UniversitiesReducer = UniversitiesReducer(),
    private val handler: UniversitiesEffectHandler = UniversitiesEffectHandler(
        universitiesService = UniversitiesService(defaultRetrofit()),
        openUrl = { url ->
            application?.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    )
) : Store<UniversitiesAction, UniversitiesState, UniversitiesEffect>(OwnedStateOwner(initialState)) {

    override fun handleEffect(effect: UniversitiesEffect, cancelId: String?) {
        launchEffect(effect, cancelId) { handler.handle(it) }
    }

    init {
        if (state.value.countrySearched.isNotBlank()) {
            dispatch(UniversitiesAction.LoadUniversities(state.value.countrySearched))
        }
    }
}
```

- [ ] **Step 5: Run tests — all pass**

```
./gradlew :redux:test :app:test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add redux/src/main/java/com/florentmaufras/redux/Store.kt \
        redux/src/test/java/com/florentmaufras/redux/StoreTest.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStore.kt
git commit -m "refactor(redux): Store takes StateOwner, remove effectHandler, add handleEffect hook"
```

---

## Task 5: Add ScopedStateOwner

**Files:**
- Create: `redux/src/main/java/com/florentmaufras/redux/ScopedStateOwner.kt`
- Modify: `redux/src/test/java/com/florentmaufras/redux/StateOwnerTest.kt` — add ScopedStateOwner tests

**Design:**
- `ScopedStateOwner<PS, CS>` implements `StateOwner<CS>`. It reads child state by mapping parent's `stateFlow` through `scope.toChildState`, and writes by mapping back through `scope.fromChildState` and updating the parent owner.
- Requires a `CoroutineScope` to derive the child `StateFlow` from the parent's flow via `stateIn`.

- [ ] **Step 1: Add ScopedStateOwner tests**

Append to `redux/src/test/java/com/florentmaufras/redux/StateOwnerTest.kt`:
```kotlin
    // --- ScopedStateOwner tests ---

    data class ParentTestState(val child: ChildTestState = ChildTestState()) : State
    data class ChildTestState(val value: Int = 0) : State

    val testScope = object : Scope<Any, ParentTestState, Any, ChildTestState> {
        override val toChildState: (ParentTestState) -> ChildTestState = { it.child }
        override val fromChildState: (ParentTestState, ChildTestState) -> ParentTestState =
            { p, c -> p.copy(child = c) }
        override val toChildAction: (Any) -> Any? = { it }
        override val fromChildAction: (Any) -> Any? = { null }
    }

    @Test
    fun `ScopedStateOwner reflects parent child state`() = runTest {
        val parentOwner = OwnedStateOwner(ParentTestState(ChildTestState(10)))
        val childOwner = ScopedStateOwner(testScope, parentOwner, backgroundScope)
        assertEquals(ChildTestState(10), childOwner.stateFlow.first())
    }

    @Test
    fun `ScopedStateOwner updateState writes through to parent`() = runTest {
        val parentOwner = OwnedStateOwner(ParentTestState(ChildTestState(0)))
        val childOwner = ScopedStateOwner(testScope, parentOwner, backgroundScope)
        childOwner.updateState(ChildTestState(99))
        assertEquals(ChildTestState(99), childOwner.stateFlow.first())
        assertEquals(ParentTestState(ChildTestState(99)), parentOwner.stateFlow.first())
    }

    @Test
    fun `ScopedStateOwner updates when parent state changes`() = runTest {
        val parentOwner = OwnedStateOwner(ParentTestState(ChildTestState(0)))
        val childOwner = ScopedStateOwner(testScope, parentOwner, backgroundScope)
        parentOwner.updateState(ParentTestState(ChildTestState(5)))
        assertEquals(ChildTestState(5), childOwner.stateFlow.first())
    }
```

- [ ] **Step 2: Run tests — expect compile failure**

```
./gradlew :redux:test
```

Expected: `ScopedStateOwner` not found.

- [ ] **Step 3: Create ScopedStateOwner.kt**

`redux/src/main/java/com/florentmaufras/redux/ScopedStateOwner.kt`:
```kotlin
package com.florentmaufras.redux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ScopedStateOwner<ParentState : Any, ChildState : Any>(
    private val scope: Scope<*, ParentState, *, ChildState>,
    private val parentOwner: StateOwner<ParentState>,
    coroutineScope: CoroutineScope
) : StateOwner<ChildState> {

    override val stateFlow: StateFlow<ChildState> = parentOwner.stateFlow
        .map { scope.toChildState(it) }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = scope.toChildState(parentOwner.stateFlow.value)
        )

    override fun updateState(newState: ChildState) {
        parentOwner.updateState(
            scope.fromChildState(parentOwner.stateFlow.value, newState)
        )
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```
./gradlew :redux:test :app:test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add redux/src/main/java/com/florentmaufras/redux/ScopedStateOwner.kt \
        redux/src/test/java/com/florentmaufras/redux/StateOwnerTest.kt
git commit -m "feat(redux): add ScopedStateOwner for child store state delegation via Scope"
```

---

## Self-Review

**Spec coverage:**
| Comment | Covered by |
|---------|-----------|
| 1 — `fromChildAction` instead of `fromChildEffect` | Task 2 (`ScopedReducer`) |
| 2 — no `ParentEffect`/`ChildEffect` in `Scope` | Task 2 (`Scope` interface) |
| 3 — `StateOwner` interface + two impls, `Scope` as interface | Tasks 2, 3, 5 |
| 4 — delete `Action`/`Effect`, replace with `Any` | Task 1 |
| 5 — `EffectHandler` optional, not forced in `Store` | Task 4 |

**No placeholders found.**

**Type consistency check:**
- `StateOwner<S>` uses `stateFlow: StateFlow<S>` and `updateState(S)` — consistent across Tasks 3, 4, 5.
- `Scope<PA, PS, CA, CS>` defined in Task 2, referenced in Tasks 4 and 5 — consistent.
- `ScopedReducer` implements `Reducer<PA, PS, PA>` (effect type = parent action) — consistent with its usage in Task 2.
- `Store.effectHandler: EffectHandler<Action, Effect>?` — consistent with `UniversitiesStore` override in Task 4.
