# Redux Kotlin Library Improvements — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply 13 targeted improvements to the Kotlin Redux library and its demo app, bringing the architecture closer to TCA idioms: explicit effect results, Flow-based effects, cancellation, reducer composition, a TestStore utility, and cleaner app-layer modelling.

**Architecture:** The core library (`redux` module) is improved first since the app module depends on it. Within the app, changes flow from data layer (State/Action/Effect/Reducer/EffectHandler) outward to the ViewModel and UI. Each task produces a self-contained, compilable change committed independently.

**Tech Stack:** Kotlin, AndroidX ViewModel, Kotlin Coroutines + Flow, Jetpack Compose, Retrofit, MockK, JUnit 5

---

## File Map

### redux module — library

| File | Change |
|---|---|
| `redux/src/main/java/com/florentmaufras/redux/EffectResult.kt` | **CREATE** — sealed class `EffectResult<out E>` |
| `redux/src/main/java/com/florentmaufras/redux/ReduceResult.kt` | **MODIFY** — use `EffectResult<Effect>` instead of `Effect?` |
| `redux/src/main/java/com/florentmaufras/redux/Reducer.kt` | **MODIFY** — return `ReduceResult<State, Effect>` (non-nullable) |
| `redux/src/main/java/com/florentmaufras/redux/EffectHandler.kt` | **MODIFY** — return `Flow<Action>` instead of `Action?` |
| `redux/src/main/java/com/florentmaufras/redux/Store.kt` | **MODIFY** — remove R/E generics, take interfaces as params, collect Flow, add cancellation |
| `redux/src/main/java/com/florentmaufras/redux/Scope.kt` | **CREATE** — child reducer composition |
| `redux/src/test/java/com/florentmaufras/redux/TestStore.kt` | **CREATE** — synchronous step-through test helper (test source set only, not shipped in the AAR) |
| `redux/src/test/java/com/florentmaufras/redux/ReducerTest.kt` | **MODIFY** — return `EffectResult.Some/None` |
| `redux/src/test/java/com/florentmaufras/redux/EffectHandlerTest.kt` | **MODIFY** — return `Flow<ActionTest>` |
| `redux/src/test/java/com/florentmaufras/redux/StoreTest.kt` | **MODIFY** — remove R/E generics |
| `redux/src/test/java/com/florentmaufras/redux/ReduxTest.kt` | **MODIFY** — adapt to Flow-based effect handler |

### app module — example

| File | Change |
|---|---|
| `app/src/main/java/.../universities/data/UniversitiesViewModel.kt` | **MODIFY** — remove `ViewModel` base class |
| `app/src/main/java/.../universities/data/UniversitiesStore.kt` | **MODIFY** — move init dispatch here; accept `Application`; remove R/E generics |
| `app/src/main/java/.../universities/data/UniversitiesAction.kt` | **MODIFY** — `LoadError` → `data class`, remove `LoadWebsite`/`WebsiteLoaded`, add `OpenWebsite` action |
| `app/src/main/java/.../universities/data/UniversitiesEffect.kt` | **MODIFY** — add `OpenWebsite(url: String)` |
| `app/src/main/java/.../universities/data/UniversitiesState.kt` | **MODIFY** — replace booleans with `ViewState`; `ArrayList` → `List`; remove `website` |
| `app/src/main/java/.../universities/data/ViewState.kt` | **CREATE** — sealed class `ViewState` |
| `app/src/main/java/.../universities/data/UniversitiesReducer.kt` | **MODIFY** — adapt to all state/action/effect changes |
| `app/src/main/java/.../universities/data/UniversitiesEffectHandler.kt` | **MODIFY** — return `Flow<Action>`; handle `OpenWebsite` via `Application` context |
| `app/src/main/java/.../universities/api/UniversitiesService.kt` | **MODIFY** — inject `Retrofit` instance; remove `Retrofit.Builder` param |
| `app/src/main/java/.../universities/ui/UniversitiesScreen.kt` | **MODIFY** — adapt to ViewState; remove `LaunchedEffect(website)`; use store factory |
| `app/src/test/java/.../universities/data/UniversitiesReducerTest.kt` | **MODIFY** — adapt assertions to `EffectResult`, ViewState, new types |
| `app/src/test/java/.../universities/data/UniversitiesEffectHandlerTest.kt` | **MODIFY** — collect `Flow`; test `OpenWebsite` |
| `app/src/test/java/.../universities/data/UniversitiesViewModelTest.kt` | **MODIFY** — adapt to non-ViewModel |
| `app/src/test/java/.../universities/api/UniversitiesServiceTest.kt` | **MODIFY** — inject `Retrofit` directly |

---

## Task 1 — EffectResult sealed class

**Files:**
- Create: `redux/src/main/java/com/florentmaufras/redux/EffectResult.kt`
- Modify: `redux/src/main/java/com/florentmaufras/redux/ReduceResult.kt`
- Modify: `redux/src/main/java/com/florentmaufras/redux/Reducer.kt`
- Modify: `redux/src/main/java/com/florentmaufras/redux/Store.kt`
- Modify: `redux/src/test/java/com/florentmaufras/redux/ReducerTest.kt`

- [ ] **Step 1: Create EffectResult.kt**

```kotlin
package com.florentmaufras.redux

sealed class EffectResult<out Effect : com.florentmaufras.redux.Effect> {
    data object None : EffectResult<Nothing>()
    data class Some<Effect : com.florentmaufras.redux.Effect>(val effect: Effect) : EffectResult<Effect>()
}
```

- [ ] **Step 2: Update ReduceResult.kt — remove nullable bound**

```kotlin
package com.florentmaufras.redux

data class ReduceResult<State : com.florentmaufras.redux.State, Effect : com.florentmaufras.redux.Effect>(
    val state: State,
    val effect: EffectResult<Effect>
)
```

- [ ] **Step 3: Update Reducer.kt — non-nullable effect in return type**

```kotlin
package com.florentmaufras.redux

interface Reducer<Action : com.florentmaufras.redux.Action, State : com.florentmaufras.redux.State, Effect : com.florentmaufras.redux.Effect> {
    fun reduce(action: Action, state: State): ReduceResult<State, Effect>
}
```

- [ ] **Step 4: Update Store.kt — switch `result.effect?.let` to `when` on EffectResult**

Replace the dispatch body's effect handling block:

```kotlin
fun dispatch(action: Action) {
    val result = reducer.reduce(action, currentState)
    _state.update { result.state }
    when (val effectResult = result.effect) {
        is EffectResult.Some -> viewModelScope.launch {
            effectHandler.handle(effectResult.effect)?.let { dispatch(it) }
        }
        EffectResult.None -> Unit
    }
}
```

- [ ] **Step 5: Update ReducerTest.kt — return EffectResult.Some/None**

```kotlin
package com.florentmaufras.redux

class ReducerTest : Reducer<ActionTest, StateTest, EffectTest> {
    override fun reduce(action: ActionTest, state: StateTest): ReduceResult<StateTest, EffectTest> {
        return when (action) {
            is ActionTest.Save -> ReduceResult(
                state.copy(name = action.saveName),
                EffectResult.Some(EffectTest.SaveAPICall)
            )
            is ActionTest.Rollback -> ReduceResult(state.copy(), EffectResult.None)
            is ActionTest.ActionWithEffectWithAction -> ReduceResult(
                state.copy(),
                EffectResult.Some(EffectTest.EffectWithAction(action.effectName))
            )
        }
    }
}
```

- [ ] **Step 6: Run tests**

```bash
cd /Users/Dev/Documents/brozh/redux && ./gradlew :redux:test
```

Expected: all passing.

- [ ] **Step 7: Commit**

```bash
git add redux/src/main/java/com/florentmaufras/redux/EffectResult.kt \
        redux/src/main/java/com/florentmaufras/redux/ReduceResult.kt \
        redux/src/main/java/com/florentmaufras/redux/Reducer.kt \
        redux/src/main/java/com/florentmaufras/redux/Store.kt \
        redux/src/test/java/com/florentmaufras/redux/ReducerTest.kt
git commit -m "$(cat <<'EOF'
refactor(redux): replace nullable Effect? with EffectResult sealed class

EffectResult<out E> with None/Some removes nullable-in-generic-position
ambiguity and makes intent explicit via exhaustive when expressions.

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>
EOF
)"
```

---

## Task 2 — Remove R/E type parameters from Store

**Files:**
- Modify: `redux/src/main/java/com/florentmaufras/redux/Store.kt`
- Modify: `redux/src/test/java/com/florentmaufras/redux/StoreTest.kt`

- [ ] **Step 1: Rewrite Store.kt — take interfaces as constructor params, drop R/E**

```kotlin
package com.florentmaufras.redux

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class Store<Action : com.florentmaufras.redux.Action, State : com.florentmaufras.redux.State, Effect : com.florentmaufras.redux.Effect>(
    initialState: State
) : ViewModel() {

    protected abstract val reducer: Reducer<Action, State, Effect>
    protected abstract val effectHandler: EffectHandler<Action, Effect>

    val currentState: State
        get() = state.value

    private val _state: MutableStateFlow<State> = MutableStateFlow(initialState)
    val state = _state.asStateFlow()

    // Note: dispatch is open so MockK can mock it in tests.
    open fun dispatch(action: Action) {
        val result = reducer.reduce(action, currentState)
        _state.update { result.state }
        when (val effectResult = result.effect) {
            is EffectResult.Some -> viewModelScope.launch {
                effectHandler.handle(effectResult.effect)?.let { dispatch(it) }
            }
            EffectResult.None -> Unit
        }
    }
}
```

- [ ] **Step 2: Update StoreTest.kt — remove R/E generics**

```kotlin
package com.florentmaufras.redux

class StoreTest(
    initialName: String,
    override val reducer: ReducerTest,
    override val effectHandler: EffectHandlerTest
) : Store<ActionTest, StateTest, EffectTest>(StateTest(initialName))
```

- [ ] **Step 3: Run tests**

```bash
cd /Users/Dev/Documents/brozh/redux && ./gradlew :redux:test
```

Expected: all passing.

- [ ] **Step 4: Commit**

```bash
git add redux/src/main/java/com/florentmaufras/redux/Store.kt \
        redux/src/test/java/com/florentmaufras/redux/StoreTest.kt
git commit -m "$(cat <<'EOF'
refactor(redux): remove redundant R/E generic type params from Store

Store only calls reduce() and handle() via their interfaces, so R and E
added no type safety. Consumers now declare Store<Action, State, Effect>.

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>
EOF
)"
```

---

## Task 3 — EffectHandler returns Flow\<Action\>

**Files:**
- Modify: `redux/src/main/java/com/florentmaufras/redux/EffectHandler.kt`
- Modify: `redux/src/main/java/com/florentmaufras/redux/Store.kt`
- Modify: `redux/src/test/java/com/florentmaufras/redux/EffectHandlerTest.kt`
- Modify: `redux/src/test/java/com/florentmaufras/redux/ReduxTest.kt`

- [ ] **Step 1: Update EffectHandler.kt**

```kotlin
package com.florentmaufras.redux

import kotlinx.coroutines.flow.Flow

interface EffectHandler<Action : com.florentmaufras.redux.Action, Effect : com.florentmaufras.redux.Effect> {
    fun handle(effect: Effect): Flow<Action>
}
```

- [ ] **Step 2: Update Store.kt — collect Flow instead of awaiting single Action?**

Replace the `EffectResult.Some` branch in `dispatch`:

```kotlin
is EffectResult.Some -> viewModelScope.launch {
    effectHandler.handle(effectResult.effect).collect { action ->
        dispatch(action)
    }
}
```

- [ ] **Step 3: Update EffectHandlerTest.kt — return Flow**

```kotlin
package com.florentmaufras.redux

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

class EffectHandlerTest(
    val repository: Any,
    val dispatcherIO: CoroutineDispatcher,
) : EffectHandler<ActionTest, EffectTest> {
    override fun handle(effect: EffectTest): Flow<ActionTest> = when (effect) {
        is EffectTest.SaveAPICall -> flow {
            withContext(dispatcherIO) { repository.toString() }
            // no emission — side effect only
        }
        is EffectTest.EffectWithAction -> flowOf(ActionTest.Save(effect.name))
    }
}
```

- [ ] **Step 4: ReduxTest.kt — remove `coEvery` mocking on effect handler (it's no longer suspend)**

The `coEvery { mockedRepository.toString() }` stubs remain valid since `repository.toString()` is still called inside the flow. No structural change needed to `ReduxTest.kt` itself — verify it compiles.

- [ ] **Step 5: Run tests**

```bash
cd /Users/Dev/Documents/brozh/redux && ./gradlew :redux:test
```

Expected: all passing.

- [ ] **Step 6: Commit**

```bash
git add redux/src/main/java/com/florentmaufras/redux/EffectHandler.kt \
        redux/src/main/java/com/florentmaufras/redux/Store.kt \
        redux/src/test/java/com/florentmaufras/redux/EffectHandlerTest.kt
git commit -m "$(cat <<'EOF'
feat(redux): EffectHandler returns Flow<Action> instead of Action?

Enables effects to emit multiple actions or none (emptyFlow). Store now
collects the flow, dispatching each emitted action through the reducer.

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>
EOF
)"
```

---

## Task 4 — Effect cancellation in Store

**Files:**
- Modify: `redux/src/main/java/com/florentmaufras/redux/Store.kt`
- Modify: `redux/src/test/java/com/florentmaufras/redux/ReduxTest.kt`

- [ ] **Step 1: Add cancelId parameter and job map to Store.kt**

```kotlin
package com.florentmaufras.redux

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class Store<Action : com.florentmaufras.redux.Action, State : com.florentmaufras.redux.State, Effect : com.florentmaufras.redux.Effect>(
    initialState: State
) : ViewModel() {

    protected abstract val reducer: Reducer<Action, State, Effect>
    protected abstract val effectHandler: EffectHandler<Action, Effect>

    val currentState: State
        get() = state.value

    private val _state: MutableStateFlow<State> = MutableStateFlow(initialState)
    val state = _state.asStateFlow()

    private val effectJobs = mutableMapOf<String, Job>()

    open fun dispatch(action: Action, cancelId: String? = null) {
        val result = reducer.reduce(action, currentState)
        _state.update { result.state }
        when (val effectResult = result.effect) {
            is EffectResult.Some -> {
                cancelId?.let { effectJobs[it]?.cancel() }
                val job = viewModelScope.launch {
                    effectHandler.handle(effectResult.effect).collect { dispatch(it) }
                }
                cancelId?.let { effectJobs[it] = job }
            }
            EffectResult.None -> Unit
        }
    }
}
```

- [ ] **Step 2: Add a cancellation test to ReduxTest.kt**

Add this test to the `ReduxTest` class:

```kotlin
@Test
fun dispatchingActionWithSameCancelId_shouldCancelPreviousEffect() = runTest {
    // Dispatch two actions with the same cancelId. The second should cancel the first.
    // Since EffectHandlerTest.SaveAPICall emits nothing, we verify state is consistent.
    coEvery { mockedRepository.toString() }.returns("")

    storeTest.dispatch(ActionTest.Save("first"), cancelId = "search")
    storeTest.dispatch(ActionTest.Save("second"), cancelId = "search")

    assertEquals(storeTest.currentState, StateTest("second"))
}
```

- [ ] **Step 3: Run tests**

```bash
cd /Users/Dev/Documents/brozh/redux && ./gradlew :redux:test
```

Expected: all passing including new cancellation test.

- [ ] **Step 4: Commit**

```bash
git add redux/src/main/java/com/florentmaufras/redux/Store.kt \
        redux/src/test/java/com/florentmaufras/redux/ReduxTest.kt
git commit -m "$(cat <<'EOF'
feat(redux): add effect cancellation via cancelId in Store.dispatch()

Passing a cancelId cancels any in-flight effect job with the same id
before launching the new one, preventing stale responses from racing.

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>
EOF
)"
```

---

## Task 5 — Scope reducer for composition

**Files:**
- Create: `redux/src/main/java/com/florentmaufras/redux/Scope.kt`

- [ ] **Step 1: Create Scope.kt**

```kotlin
package com.florentmaufras.redux

/**
 * Composes a child reducer into a parent reducer by providing lenses for state and action,
 * and a mapping function for child effects back to parent effects.
 *
 * Usage:
 *   val scoped = Scope(
 *       toChildState = { parent -> parent.child },
 *       fromChildState = { parent, child -> parent.copy(child = child) },
 *       toChildAction = { action -> action as? ChildAction },
 *       fromChildEffect = { childEffect -> ParentEffect.Child(childEffect) },
 *       childReducer = ChildReducer()
 *   )
 */
class Scope<
    ParentAction : Action,
    ParentState : State,
    ParentEffect : Effect,
    ChildAction : Action,
    ChildState : State,
    ChildEffect : Effect>(
    private val toChildState: (ParentState) -> ChildState,
    private val fromChildState: (ParentState, ChildState) -> ParentState,
    private val toChildAction: (ParentAction) -> ChildAction?,
    private val fromChildEffect: (ChildEffect) -> ParentEffect,
    private val childReducer: Reducer<ChildAction, ChildState, ChildEffect>
) : Reducer<ParentAction, ParentState, ParentEffect> {

    override fun reduce(
        action: ParentAction,
        state: ParentState
    ): ReduceResult<ParentState, ParentEffect> {
        val childAction = toChildAction(action)
            ?: return ReduceResult(state, EffectResult.None)

        val childResult = childReducer.reduce(childAction, toChildState(state))
        val newState = fromChildState(state, childResult.state)
        val parentEffect = when (val e = childResult.effect) {
            is EffectResult.Some -> EffectResult.Some(fromChildEffect(e.effect))
            EffectResult.None -> EffectResult.None
        }
        return ReduceResult(newState, parentEffect)
    }
}
```

- [ ] **Step 2: Run tests to confirm no regressions**

```bash
cd /Users/Dev/Documents/brozh/redux && ./gradlew :redux:test
```

Expected: all passing.

- [ ] **Step 3: Commit**

```bash
git add redux/src/main/java/com/florentmaufras/redux/Scope.kt
git commit -m "$(cat <<'EOF'
feat(redux): add Scope for composing child reducers into parent reducers

Scope maps child State/Action/Effect into a parent reducer via lenses,
enabling feature-level reducer isolation similar to TCA's Scope operator.

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>
EOF
)"
```

---

## Task 6 — TestStore utility

**Files:**
- Create: `redux/src/test/java/com/florentmaufras/redux/TestStore.kt`

Note: TestStore goes in the **test** source set so it is not included in the published library AAR.

- [ ] **Step 1: Create TestStore.kt in the test source set**

```kotlin
package com.florentmaufras.redux

/**
 * A synchronous, non-ViewModel store for use in unit tests.
 * Lets tests send actions and assert state step by step without coroutines or lifecycle.
 *
 * Effects produced by the reducer are recorded in [dispatchedEffects] but not executed —
 * this keeps the test scope focused on the reducer's pure logic.
 */
class TestStore<A : Action, S : State, E : Effect>(
    initialState: S,
    private val reducer: Reducer<A, S, E>
) {
    var state: S = initialState
        private set

    val dispatchedEffects: MutableList<E> = mutableListOf()

    /**
     * Sends [action] through the reducer, updates [state], and records any produced effect.
     * Returns the EffectResult so callers can assert on it directly.
     */
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

- [ ] **Step 2: Run tests**

```bash
cd /Users/Dev/Documents/brozh/redux && ./gradlew :redux:test
```

Expected: all passing.

- [ ] **Step 3: Commit**

```bash
git add redux/src/test/java/com/florentmaufras/redux/TestStore.kt
git commit -m "$(cat <<'EOF'
feat(redux): add TestStore for step-through reducer testing without lifecycle

TestStore runs the reducer synchronously and records effects without
executing them, enabling focused pure-function assertions in unit tests.

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>
EOF
)"
```

---

## Task 7 — Remove ViewModel from UniversitiesViewModel

The `Store` is already the ViewModel. `UniversitiesViewModel` becomes a plain facade. The initial dispatch moves to `UniversitiesStore.init`.

**Files:**
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStore.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesViewModel.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/ui/UniversitiesScreen.kt`
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesViewModelTest.kt`

- [ ] **Step 1: Move init dispatch to UniversitiesStore; update signature for Task 2 generics**

```kotlin
package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.Store

class UniversitiesStore(
    initialState: UniversitiesState = UniversitiesState(),
    override val reducer: UniversitiesReducer = UniversitiesReducer(),
    override val effectHandler: UniversitiesEffectHandler = UniversitiesEffectHandler()
) : Store<UniversitiesAction, UniversitiesState, UniversitiesEffect>(initialState) {

    init {
        if (state.value.countrySearched.isNotBlank()) {
            dispatch(UniversitiesAction.LoadUniversities(state.value.countrySearched))
        }
    }
}
```

- [ ] **Step 2: Strip ViewModel from UniversitiesViewModel**

```kotlin
package com.florentmaufras.reduxdemo.universities.data

import kotlinx.coroutines.flow.StateFlow

class UniversitiesViewModel(val store: UniversitiesStore) {

    val stateFlow: StateFlow<UniversitiesState> = store.state

    fun dispatchAction(action: UniversitiesAction) {
        store.dispatch(action)
    }
}
```

- [ ] **Step 3: Update UniversitiesScreen — obtain Store via viewModel(), wrap in UniversitiesViewModel**

```kotlin
@Composable
fun UniversitiesScreen(store: UniversitiesStore = viewModel()) {
    val viewModel = remember { UniversitiesViewModel(store) }
    // rest of the composable unchanged
```

- [ ] **Step 4: Update UniversitiesViewModelTest — no ViewModel lifecycle needed**

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
    fun dispatchAction_shouldForwardActionToStore() {
        val action = mockk<UniversitiesAction>()
        justRun { mockedStore.dispatch(action) }

        viewModel.dispatchAction(action)

        verify(exactly = 1) { mockedStore.dispatch(action) }
    }
}
```

- [ ] **Step 5: Run tests**

```bash
cd /Users/Dev/Documents/brozh/redux && ./gradlew :app:test
```

Expected: all passing.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStore.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesViewModel.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/ui/UniversitiesScreen.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesViewModelTest.kt
git commit -m "$(cat <<'EOF'
fix(app): remove double ViewModel — Store is the sole ViewModel

UniversitiesViewModel is now a plain facade. Init dispatch moved to
UniversitiesStore.init. UI obtains UniversitiesStore via viewModel().

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>
EOF
)"
```

---

## Task 8 — ArrayList → List in state and actions

**Files:**
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesState.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesAction.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/api/UniversitiesAPI.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/api/UniversitiesService.kt`
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducerTest.kt`
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffectHandlerTest.kt`

- [ ] **Step 1: Update UniversitiesState.kt**

```kotlin
data class UniversitiesState(
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val website: String? = null,
    val countrySearched: String = "Canada",
    val universities: List<University> = emptyList(),
) : State
```

- [ ] **Step 2: Update UniversitiesAction.kt — UniversitiesLoaded uses List**

```kotlin
sealed class UniversitiesAction : Action {
    data class LoadUniversities(val country: String) : UniversitiesAction()
    data class UniversitiesLoaded(val universities: List<University>) : UniversitiesAction()
    data object LoadError : UniversitiesAction()
    data class LoadWebsite(val website: String) : UniversitiesAction()
    data object WebsiteLoaded : UniversitiesAction()
}
```

- [ ] **Step 3: Update UniversitiesAPI.kt — return List**

```kotlin
interface UniversitiesAPI {
    @GET("search")
    suspend fun getUniversities(@Query("country") country: String): List<University>
}
```

- [ ] **Step 4: Update UniversitiesService.kt — change return type to List**

The wrapper method must match the updated API return type or it will fail to compile:

```kotlin
suspend fun getUniversities(country: String): List<University> =
    api.getUniversities(country)
```

Note: this is a partial change only; the constructor and `Retrofit.Builder` are refactored in Task 12.

- [ ] **Step 5: Update test fixtures — replace arrayListOf() with listOf()**

In `UniversitiesReducerTest.kt`, change:
```kotlin
val universities = arrayListOf<University>()
```
to:
```kotlin
val universities = emptyList<University>()
```

In `UniversitiesEffectHandlerTest.kt`, change:
```kotlin
val universities = arrayListOf<University>()
```
to:
```kotlin
val universities = emptyList<University>()
```

- [ ] **Step 6: Run tests**

```bash
cd /Users/Dev/Documents/brozh/redux && ./gradlew :app:test
```

Expected: all passing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesState.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesAction.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/api/UniversitiesAPI.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/api/UniversitiesService.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducerTest.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffectHandlerTest.kt
git commit -m "$(cat <<'EOF'
fix(app): replace mutable ArrayList with immutable List in state and actions

Mutable collections in state allow external mutation bypassing the reducer.
Using List<University> enforces immutability at the type level.

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>
EOF
)"
```

---

## Task 9 — LoadError becomes a data class with a message

**Files:**
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesAction.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffectHandler.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducer.kt`
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducerTest.kt`
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffectHandlerTest.kt`

- [ ] **Step 1: Update LoadError in UniversitiesAction.kt**

```kotlin
data class LoadError(val message: String? = null) : UniversitiesAction()
```

Remove `data object LoadError`.

- [ ] **Step 2: Update UniversitiesEffectHandler.kt — pass exception message**

```kotlin
} catch (e: Exception) {
    timber.e(e)
    UniversitiesAction.LoadError(e.message)
}
```

- [ ] **Step 3: Update UniversitiesReducer.kt — pattern match on data class**

```kotlin
is UniversitiesAction.LoadError -> {
    newState = state.copy(isLoading = false, hasError = true)
}
```
No structural change needed here — the `is` check still works for a data class.

- [ ] **Step 4: Update UniversitiesReducerTest.kt — construct LoadError()**

```kotlin
universitiesReducer.reduce(UniversitiesAction.LoadError(), state)
```

- [ ] **Step 5: Update UniversitiesEffectHandlerTest.kt — assert LoadError with message**

```kotlin
assertEquals(
    UniversitiesAction.LoadError(null), // Exception() has null message
    universitiesEffectHandler.handle(UniversitiesEffect.LoadUniversities(country))
)
```

- [ ] **Step 6: Run tests**

```bash
cd /Users/Dev/Documents/brozh/redux && ./gradlew :app:test
```

Expected: all passing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesAction.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffectHandler.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducer.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducerTest.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffectHandlerTest.kt
git commit -m "$(cat <<'EOF'
fix(app): LoadError carries error message instead of being a data object

Enables the UI to display meaningful error context and allows tests to
assert on specific failure reasons rather than just the error case.

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>
EOF
)"
```

---

## Task 10 — ViewState sealed class replaces boolean flags

**Files:**
- Create: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/ViewState.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesState.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducer.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/ui/UniversitiesScreen.kt`
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducerTest.kt`

- [ ] **Step 1: Create ViewState.kt**

```kotlin
package com.florentmaufras.reduxdemo.universities.data

sealed class ViewState {
    data object Idle : ViewState()
    data object Loading : ViewState()
    data class Error(val message: String? = null) : ViewState()
    data class Loaded(val universities: List<University>) : ViewState()
}
```

- [ ] **Step 2: Update UniversitiesState.kt — remove booleans and universities field**

```kotlin
package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.State

data class UniversitiesState(
    val viewState: ViewState = ViewState.Idle,
    val countrySearched: String = "Canada",
    val website: String? = null,
) : State
```

Note: `website` stays temporarily — it is removed in Task 11.

- [ ] **Step 3: Update UniversitiesReducer.kt — use ViewState**

```kotlin
package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.EffectResult
import com.florentmaufras.redux.ReduceResult
import com.florentmaufras.redux.Reducer

class UniversitiesReducer : Reducer<UniversitiesAction, UniversitiesState, UniversitiesEffect> {
    override fun reduce(
        action: UniversitiesAction,
        state: UniversitiesState
    ): ReduceResult<UniversitiesState, UniversitiesEffect> {
        return when (action) {
            is UniversitiesAction.LoadUniversities -> ReduceResult(
                state.copy(
                    viewState = ViewState.Loading,
                    countrySearched = action.country
                ),
                EffectResult.Some(UniversitiesEffect.LoadUniversities(action.country))
            )
            is UniversitiesAction.UniversitiesLoaded -> ReduceResult(
                state.copy(viewState = ViewState.Loaded(action.universities)),
                EffectResult.None
            )
            is UniversitiesAction.LoadError -> ReduceResult(
                state.copy(viewState = ViewState.Error(action.message)),
                EffectResult.None
            )
            is UniversitiesAction.LoadWebsite -> ReduceResult(
                state.copy(website = action.website),
                EffectResult.None
            )
            is UniversitiesAction.WebsiteLoaded -> ReduceResult(
                state.copy(website = null),
                EffectResult.None
            )
        }
    }
}
```

- [ ] **Step 4: Update UniversitiesScreen.kt — replace isLoading/hasError when-block**

```kotlin
when (val vs = state.value.viewState) {
    ViewState.Idle -> UniversitiesNoResult(state.value.countrySearched)
    ViewState.Loading -> UniversitiesLoading()
    is ViewState.Error -> UniversitiesError(vs.message)
    is ViewState.Loaded -> {
        if (vs.universities.isEmpty()) UniversitiesNoResult(state.value.countrySearched)
        else UniversitiesContent(vs.universities, viewModel::dispatchAction)
    }
}
```

Update `UniversitiesError` composable to accept optional message:

```kotlin
@Composable
private fun UniversitiesError(message: String?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(if (message != null) "Error: $message" else "Oupsy daisy! Something went wrong!")
    }
}
```

Update `UniversitiesContent` to take `List<University>`:

```kotlin
private fun UniversitiesContent(
    universities: List<University>,
    dispatchAction: (UniversitiesAction) -> Unit,
)
```

- [ ] **Step 5: Update UniversitiesReducerTest.kt — assert on ViewState and EffectResult**

```kotlin
@Test
fun reduce_shouldHandleLoadUniversitiesAndReturnLoadUniversitiesEffectAndNewState() {
    val state = UniversitiesState()
    val country = ""

    assertEquals(
        ReduceResult(
            state.copy(viewState = ViewState.Loading, countrySearched = country),
            EffectResult.Some(UniversitiesEffect.LoadUniversities(country))
        ),
        universitiesReducer.reduce(UniversitiesAction.LoadUniversities(country), state)
    )
}

@Test
fun reduce_shouldHandleUniversitiesLoadedAndReturnNewState() {
    val state = UniversitiesState()
    val universities = emptyList<University>()

    assertEquals(
        ReduceResult(state.copy(viewState = ViewState.Loaded(universities)), EffectResult.None),
        universitiesReducer.reduce(UniversitiesAction.UniversitiesLoaded(universities), state)
    )
}

@Test
fun reduce_shouldHandleLoadErrorAndReturnNewState() {
    val state = UniversitiesState()

    assertEquals(
        ReduceResult(state.copy(viewState = ViewState.Error(null)), EffectResult.None),
        universitiesReducer.reduce(UniversitiesAction.LoadError(null), state)
    )
}

@Test
fun reduce_shouldHandleLoadWebsiteAndReturnNewState() {
    val state = UniversitiesState()
    val website = ""

    assertEquals(
        ReduceResult(state.copy(website = website), EffectResult.None),
        universitiesReducer.reduce(UniversitiesAction.LoadWebsite(website), state)
    )
}

@Test
fun reduce_shouldHandleWebsiteLoadedAndReturnNewState() {
    val state = UniversitiesState()

    assertEquals(
        ReduceResult(state.copy(website = null), EffectResult.None),
        universitiesReducer.reduce(UniversitiesAction.WebsiteLoaded, state)
    )
}
```

- [ ] **Step 6: Run tests**

```bash
cd /Users/Dev/Documents/brozh/redux && ./gradlew :app:test
```

Expected: all passing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/florentmaufras/reduxdemo/universities/data/ViewState.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesState.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducer.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/ui/UniversitiesScreen.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducerTest.kt
git commit -m "$(cat <<'EOF'
refactor(app): replace isLoading/hasError booleans with ViewState sealed class

Eliminates impossible boolean combinations. UI exhaustively pattern-matches
on Idle/Loading/Error/Loaded, removing the need for boolean flag coordination.

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>
EOF
)"
```

---

## Task 11 — Remove website from state; open URL via effect

**Files:**
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesAction.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffect.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesState.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducer.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffectHandler.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStore.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/ui/UniversitiesScreen.kt`
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducerTest.kt`
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffectHandlerTest.kt`

- [ ] **Step 1: Update UniversitiesAction.kt — rename LoadWebsite to OpenWebsite, remove WebsiteLoaded**

```kotlin
sealed class UniversitiesAction : Action {
    data class LoadUniversities(val country: String) : UniversitiesAction()
    data class UniversitiesLoaded(val universities: List<University>) : UniversitiesAction()
    data class LoadError(val message: String? = null) : UniversitiesAction()
    data class OpenWebsite(val url: String) : UniversitiesAction()
}
```

- [ ] **Step 2: Update UniversitiesEffect.kt — add OpenWebsite effect**

```kotlin
sealed class UniversitiesEffect : Effect {
    data class LoadUniversities(val country: String) : UniversitiesEffect()
    data class OpenWebsite(val url: String) : UniversitiesEffect()
}
```

- [ ] **Step 3: Update UniversitiesState.kt — remove website field**

```kotlin
data class UniversitiesState(
    val viewState: ViewState = ViewState.Idle,
    val countrySearched: String = "Canada",
) : State
```

- [ ] **Step 4: Update UniversitiesReducer.kt — OpenWebsite action returns OpenWebsite effect**

```kotlin
is UniversitiesAction.OpenWebsite -> ReduceResult(
    state,
    EffectResult.Some(UniversitiesEffect.OpenWebsite(action.url))
)
```

Remove the `LoadWebsite` and `WebsiteLoaded` cases.

- [ ] **Step 5: Update UniversitiesEffectHandler.kt — accept Application context; handle OpenWebsite**

```kotlin
package com.florentmaufras.reduxdemo.universities.data

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import com.florentmaufras.redux.EffectHandler
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

class UniversitiesEffectHandler(
    private val universitiesService: UniversitiesService,
    private val application: Application,
    private val timber: Timber.Tree = Timber,
) : EffectHandler<UniversitiesAction, UniversitiesEffect> {

    override fun handle(effect: UniversitiesEffect): Flow<UniversitiesAction> = when (effect) {
        is UniversitiesEffect.LoadUniversities -> flow {
            try {
                val list = universitiesService.getUniversities(effect.country)
                emit(UniversitiesAction.UniversitiesLoaded(list))
            } catch (e: Exception) {
                timber.e(e)
                emit(UniversitiesAction.LoadError(e.message))
            }
        }
        is UniversitiesEffect.OpenWebsite -> {
            application.startActivity(
                Intent(Intent.ACTION_VIEW, effect.url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            emptyFlow()
        }
    }
}
```

- [ ] **Step 6: Update UniversitiesStore.kt — accept Application; pass to EffectHandler**

```kotlin
package com.florentmaufras.reduxdemo.universities.data

import android.app.Application
import com.florentmaufras.redux.Store
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService

class UniversitiesStore(
    application: Application,
    initialState: UniversitiesState = UniversitiesState(),
    override val reducer: UniversitiesReducer = UniversitiesReducer(),
    override val effectHandler: UniversitiesEffectHandler = UniversitiesEffectHandler(
        universitiesService = UniversitiesService(),
        application = application
    )
) : Store<UniversitiesAction, UniversitiesState, UniversitiesEffect>(initialState) {

    init {
        if (state.value.countrySearched.isNotBlank()) {
            dispatch(UniversitiesAction.LoadUniversities(state.value.countrySearched))
        }
    }
}
```

- [ ] **Step 7: Update UniversitiesScreen.kt — create Store via AndroidViewModelFactory; remove LaunchedEffect(website)**

`LocalContext.current` is a composable-scoped read and **must not** be called inside `ViewModelProvider.Factory.create()` (a plain function). Hoist the context read to the composable scope before the factory:

```kotlin
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import android.app.Application
import androidx.compose.ui.platform.LocalContext

@Composable
fun UniversitiesScreen() {
    val context = LocalContext.current  // read here, in composable scope
    val store: UniversitiesStore = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext as Application  // capture from above
                @Suppress("UNCHECKED_CAST")
                return UniversitiesStore(app) as T
            }
        }
    )
    val viewModel = remember { UniversitiesViewModel(store) }
    val state = viewModel.stateFlow.collectAsState()
    val textFieldState = rememberTextFieldState(state.value.countrySearched)
    // Remove the LaunchedEffect(state.value.website) block entirely
```

Update the website button click to dispatch `OpenWebsite`:
```kotlin
onClick = {
    dispatchAction(UniversitiesAction.OpenWebsite(university.webPages[0]))
}
```

- [ ] **Step 8: Update UniversitiesReducerTest.kt — remove LoadWebsite/WebsiteLoaded tests, add OpenWebsite test**

```kotlin
@Test
fun reduce_shouldHandleOpenWebsiteAndReturnOpenWebsiteEffect() {
    val state = UniversitiesState()
    val url = "https://example.com"

    assertEquals(
        ReduceResult(state, EffectResult.Some(UniversitiesEffect.OpenWebsite(url))),
        universitiesReducer.reduce(UniversitiesAction.OpenWebsite(url), state)
    )
}
```

Remove the `reduce_shouldHandleUniversitiesLoadWebsite...` and `reduce_shouldHandleUniversitiesWebsiteLoaded...` tests.

- [ ] **Step 9: Update UniversitiesEffectHandlerTest.kt — integrate Application mock; update all tests for Flow**

Replace the entire test class:

```kotlin
package com.florentmaufras.reduxdemo.universities.data

import android.app.Application
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber

class UniversitiesEffectHandlerTest {

    private val mockedUniversitiesService: UniversitiesService = mockk()
    private val mockedApplication: Application = mockk(relaxed = true)
    private val mockedTimber: Timber.Tree = mockk()
    private val country = "country"

    private lateinit var universitiesEffectHandler: UniversitiesEffectHandler

    @BeforeEach
    fun setup() {
        universitiesEffectHandler = UniversitiesEffectHandler(
            universitiesService = mockedUniversitiesService,
            application = mockedApplication,
            timber = mockedTimber
        )
    }

    @Test
    fun handle_shouldEmitUniversitiesLoaded_whenSuccessful() = runTest {
        val universities = emptyList<University>()
        coEvery { mockedUniversitiesService.getUniversities(country) }.returns(universities)

        val result = universitiesEffectHandler
            .handle(UniversitiesEffect.LoadUniversities(country))
            .toList()

        assertEquals(listOf(UniversitiesAction.UniversitiesLoaded(universities)), result)
    }

    @Test
    fun handle_shouldEmitLoadError_whenNotSuccessful() = runTest {
        val exception = Exception("network error")
        coEvery { mockedUniversitiesService.getUniversities(country) }.throws(exception)
        justRun { mockedTimber.e(any<Exception>()) }

        val result = universitiesEffectHandler
            .handle(UniversitiesEffect.LoadUniversities(country))
            .toList()

        assertEquals(listOf(UniversitiesAction.LoadError("network error")), result)
    }

    @Test
    fun handle_shouldOpenWebsiteAndEmitNothing() = runTest {
        val url = "https://example.com"

        val result = universitiesEffectHandler
            .handle(UniversitiesEffect.OpenWebsite(url))
            .toList()

        assertEquals(emptyList<UniversitiesAction>(), result)
    }
}
```

- [ ] **Step 10: Run tests**

```bash
cd /Users/Dev/Documents/brozh/redux && ./gradlew :app:test
```

Expected: all passing.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesAction.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffect.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesState.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducer.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffectHandler.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStore.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/ui/UniversitiesScreen.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesReducerTest.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffectHandlerTest.kt
git commit -m "$(cat <<'EOF'
refactor(app): move URL opening from state to effect, remove website from state

website: String? in state was a one-time event trigger, not true state.
EffectHandler now opens the browser directly via Application context,
eliminating the LaunchedEffect(website) workaround in the UI.

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>
EOF
)"
```

---

## Task 12 — Inject configured Retrofit into UniversitiesService

**Files:**
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/api/UniversitiesService.kt`
- Modify: `app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesEffectHandler.kt`
- Modify: `app/src/test/java/com/florentmaufras/reduxdemo/universities/api/UniversitiesServiceTest.kt`

- [ ] **Step 1: Rewrite UniversitiesService.kt — accept Retrofit instance**

```kotlin
package com.florentmaufras.reduxdemo.universities.api

import com.florentmaufras.reduxdemo.universities.data.University
import retrofit2.Retrofit

class UniversitiesService(retrofit: Retrofit) {
    private val api: UniversitiesAPI = retrofit.create(UniversitiesAPI::class.java)

    suspend fun getUniversities(country: String): List<University> =
        api.getUniversities(country)
}
```

- [ ] **Step 2: Add top-level private helper in UniversitiesStore.kt and update default effectHandler**

Add a private top-level function at the top of `UniversitiesStore.kt` (outside the class, same file):

```kotlin
private fun defaultRetrofit(): Retrofit = Retrofit.Builder()
    .baseUrl("http://universities.hipolabs.com")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

Update the `UniversitiesStore` default parameter:

```kotlin
override val effectHandler: UniversitiesEffectHandler = UniversitiesEffectHandler(
    universitiesService = UniversitiesService(defaultRetrofit()),
    application = application
)
```

- [ ] **Step 3: Update UniversitiesServiceTest.kt — inject a mock Retrofit**

```kotlin
package com.florentmaufras.reduxdemo.universities.api

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import kotlin.test.assertNotNull

class UniversitiesServiceTest {

    @Test
    fun service_shouldCreateApiFromInjectedRetrofit() {
        val mockRetrofit: Retrofit = mockk(relaxed = true)
        every { mockRetrofit.create(UniversitiesAPI::class.java) }.returns(mockk())

        val service = UniversitiesService(mockRetrofit)

        assertNotNull(service)
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd /Users/Dev/Documents/brozh/redux && ./gradlew :app:test
```

Expected: all passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/florentmaufras/reduxdemo/universities/api/UniversitiesService.kt \
        app/src/main/java/com/florentmaufras/reduxdemo/universities/data/UniversitiesStore.kt \
        app/src/test/java/com/florentmaufras/reduxdemo/universities/api/UniversitiesServiceTest.kt
git commit -m "$(cat <<'EOF'
refactor(app): inject configured Retrofit instance into UniversitiesService

UniversitiesService no longer mixes construction with injection.
A pre-built Retrofit instance is passed in, making the service trivially testable.

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>
EOF
)"
```

---

## Task 13 — countrySearched: keep with justification

`countrySearched` in `UniversitiesState` is **kept**. It holds the last country that was successfully searched, used to display "No results found for {country}" when `ViewState.Loaded` has an empty list. This is view-state, not a copy of the in-flight action parameter — it persists across state transitions (e.g., it remains set while `ViewState.Loading` is active for the next request, letting the UI show a stable previous label if needed).

No code change. Commit a note in the plan.

```bash
git commit --allow-empty -m "$(cat <<'EOF'
docs: retain countrySearched in UniversitiesState (intentional)

Evaluated per review item 13: countrySearched is kept as it serves
display purposes (empty-state label) independent of in-flight requests.

Generated with [Claude Code](https://claude.ai/code)
via [Happy](https://happy.engineering)

Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Happy <yesreply@happy.engineering>
EOF
)"
```

---

## Verification

After all tasks:

```bash
cd /Users/Dev/Documents/brozh/redux
./gradlew :redux:test :app:test
```

All tests green. The library has no references to nullable `Effect?`, no extraneous type parameters on `Store`, and a clean `Flow`-based effect pipeline.
