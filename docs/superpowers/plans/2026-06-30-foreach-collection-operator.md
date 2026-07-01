# `forEach` Collection Operator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `forEach` reducer operator to `:redux` so a parent feature can compose a dynamic collection (`0..X`) of identified child features.

**Architecture:** `forEach` mirrors the existing single-child `scope` operator for a `List<ChildState>` of `Identifiable` elements. It runs the parent reducer first (handling add/remove/broadcast), then routes an element-addressed action to the matching child, writes the element back, and lifts the child's effect up with the element id embedded. Per-element effect cancellation is the developer's responsibility (child keys effects on `state.id`); the operator does no auto-namespacing.

**Tech Stack:** Kotlin, kotlinx.coroutines (Flow/StateFlow), AndroidX ViewModel. Tests: JUnit 4 + coroutines-test in `:redux`.

## Global Constraints

- `:redux` tests use **JUnit 4** (`org.junit.Test`, `@Before`/`@After`, `junit.framework.TestCase.assertEquals`).
- Coroutine tests set the Main dispatcher: `Dispatchers.setMain(UnconfinedTestDispatcher())` in setup, `Dispatchers.resetMain()` in teardown; annotate the class `@OptIn(ExperimentalCoroutinesApi::class)`.
- Package for all core types: `com.florentmaufras.redux`.
- Commit messages: single-line conventional `type(scope): description` — no body, no trailers (project `commit-message-format` skill).
- Version bump: `redux/build.gradle.kts` `version` from `3.0.0` to `3.1.0` (additive public API).
- Design spec: `docs/superpowers/specs/2026-06-30-foreach-collection-operator-design.md`.

---

## File Structure

`:redux` main (`redux/src/main/java/com/florentmaufras/redux/`):
- Create `Identifiable.kt` — the `Identifiable<Id>` marker interface.
- Create `ForEachScope.kt` — the collection descriptor interface.
- Modify `Scopes.kt` — add the `forEach` extension operator beside `scope`.
- Modify `redux/build.gradle.kts` — version `3.0.0` → `3.1.0`.

`:redux` test (`redux/src/test/java/com/florentmaufras/redux/`):
- Create `ForEachTest.kt` — reducer-level TDD tests + a store-level cancellation test.

---

## Task 1: `forEach` operator + `Identifiable` / `ForEachScope`

**Files:**
- Create: `redux/src/main/java/com/florentmaufras/redux/Identifiable.kt`
- Create: `redux/src/main/java/com/florentmaufras/redux/ForEachScope.kt`
- Modify: `redux/src/main/java/com/florentmaufras/redux/Scopes.kt`
- Test: `redux/src/test/java/com/florentmaufras/redux/ForEachTest.kt`

**Interfaces:**
- Consumes: `Reducer`, `ReduceResult`, `Effect` (existing core).
- Produces:
  - `interface Identifiable<out Id> { val id: Id }`
  - `interface ForEachScope<ParentState, ParentAction, ChildState, ChildAction, Id> where ChildState : Identifiable<Id>` with `toChildren: (ParentState) -> List<ChildState>`, `fromChildren: (ParentState, List<ChildState>) -> ParentState`, `toChildAction: (ParentAction) -> Pair<Id, ChildAction>?`, `embedChildAction: (Id, ChildAction) -> ParentAction`.
  - `fun <ParentState, ParentAction, ChildState, ChildAction, Id> Reducer<ParentState, ParentAction>.forEach(scope: ForEachScope<ParentState, ParentAction, ChildState, ChildAction, Id>, child: Reducer<ChildState, ChildAction>): Reducer<ParentState, ParentAction> where ChildState : Identifiable<Id>`.

- [ ] **Step 1: Write the failing tests**

Create `redux/src/test/java/com/florentmaufras/redux/ForEachTest.kt`:

```kotlin
package com.florentmaufras.redux

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ForEachTest {

    // Test-only: flatten an effect tree to the actions it would emit (ignoring cancellation).
    private fun <A> Effect<A>.allActions(): Flow<A> = when (this) {
        Effect.None, is Effect.Cancel -> emptyFlow()
        is Effect.Actions -> flow
        is Effect.Cancellable -> effect.allActions()
        is Effect.Merge -> merge(*effects.map { it.allActions() }.toTypedArray())
    }

    data class Counter(override val id: Int, val count: Int = 0) : Identifiable<Int>

    sealed interface CounterAction {
        data class Add(val by: Int) : CounterAction
        data object Ping : CounterAction    // effect emits Add(1) back
        data object Work : CounterAction    // starts a cancellable never-ending effect
        data object Stop : CounterAction    // cancels the Work effect
    }

    data class ListState(
        val counters: List<Counter> = emptyList(),
        val pausedAll: Boolean = false,
    )

    sealed interface ListAction {
        data class Element(val id: Int, val action: CounterAction) : ListAction   // element-addressed
        data object PauseAll : ListAction                                          // broadcast
        data class Insert(val counter: Counter) : ListAction
    }

    private val childReducer = Reducer<Counter, CounterAction> { state, action ->
        when (action) {
            is CounterAction.Add -> ReduceResult(state.copy(count = state.count + action.by))
            CounterAction.Ping -> ReduceResult(state, Effect.send(CounterAction.Add(1)))
            CounterAction.Work ->
                ReduceResult(state, Effect.run<CounterAction> { kotlinx.coroutines.awaitCancellation() }.cancellable(state.id))
            CounterAction.Stop -> ReduceResult(state, Effect.cancel(state.id))
        }
    }

    private val parentReducer = Reducer<ListState, ListAction> { state, action ->
        when (action) {
            is ListAction.Insert -> ReduceResult(state.copy(counters = state.counters + action.counter))
            ListAction.PauseAll -> ReduceResult(state.copy(pausedAll = true))
            is ListAction.Element -> ReduceResult(state)   // observed; element routing handled by forEach
        }
    }

    private val scope = object : ForEachScope<ListState, ListAction, Counter, CounterAction, Int> {
        override val toChildren: (ListState) -> List<Counter> = { it.counters }
        override val fromChildren: (ListState, List<Counter>) -> ListState = { s, c -> s.copy(counters = c) }
        override val toChildAction: (ListAction) -> Pair<Int, CounterAction>? =
            { a -> (a as? ListAction.Element)?.let { it.id to it.action } }
        override val embedChildAction: (Int, CounterAction) -> ListAction =
            { id, a -> ListAction.Element(id, a) }
    }

    private val composed = parentReducer.forEach(scope, childReducer)

    @Test
    fun elementAction_updatesOnlyMatchingChild() {
        val state = ListState(counters = listOf(Counter(1), Counter(2)))
        val result = composed.reduce(state, ListAction.Element(2, CounterAction.Add(5)))
        assertEquals(listOf(Counter(1, 0), Counter(2, 5)), result.state.counters)
    }

    @Test
    fun broadcastAction_handledByParent() {
        val state = ListState(counters = listOf(Counter(1)))
        val result = composed.reduce(state, ListAction.PauseAll)
        assertEquals(true, result.state.pausedAll)
    }

    @Test
    fun childEffect_isLiftedWithIdEmbedded() = runTest {
        val state = ListState(counters = listOf(Counter(7)))
        val result = composed.reduce(state, ListAction.Element(7, CounterAction.Ping))
        assertEquals(
            listOf(ListAction.Element(7, CounterAction.Add(1))),
            result.effect.allActions().toList(),
        )
    }

    @Test
    fun unknownElementId_isNoOp() = runTest {
        val state = ListState(counters = listOf(Counter(1)))
        val result = composed.reduce(state, ListAction.Element(99, CounterAction.Add(5)))
        assertEquals(state, result.state)
        assertEquals(emptyList<ListAction>(), result.effect.allActions().toList())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :redux:testDebugUnitTest --tests "*ForEachTest" --console=plain`
Expected: FAIL — compilation errors, `Identifiable` / `ForEachScope` / `forEach` unresolved.

- [ ] **Step 3: Create `Identifiable.kt`**

Create `redux/src/main/java/com/florentmaufras/redux/Identifiable.kt`:

```kotlin
package com.florentmaufras.redux

/**
 * Stable identity for an element addressed within a collection composed by [forEach].
 * Two elements are the same element across reductions iff their [id] is equal; ids in a
 * collection must be unique.
 */
interface Identifiable<out Id> {
    val id: Id
}
```

- [ ] **Step 4: Create `ForEachScope.kt`**

Create `redux/src/main/java/com/florentmaufras/redux/ForEachScope.kt`:

```kotlin
package com.florentmaufras.redux

/**
 * Lenses/prisms mapping a parent feature to a collection of identified children, used by
 * [forEach]. Symmetric with [Scope], but the action prism carries the element [Id].
 *
 * - [toChildren] / [fromChildren]: read and write the child collection in parent state.
 * - [toChildAction]: extract an element-addressed action as `(id, childAction)`, or `null`
 *   when the action does not target an element (e.g. a broadcast or add/remove action).
 * - [embedChildAction]: embed a child action for element [Id] back into a parent action.
 */
interface ForEachScope<ParentState, ParentAction, ChildState, ChildAction, Id>
    where ChildState : Identifiable<Id> {
    val toChildren: (ParentState) -> List<ChildState>
    val fromChildren: (ParentState, List<ChildState>) -> ParentState
    val toChildAction: (ParentAction) -> Pair<Id, ChildAction>?
    val embedChildAction: (Id, ChildAction) -> ParentAction
}
```

- [ ] **Step 5: Add `forEach` to `Scopes.kt`**

Append to `redux/src/main/java/com/florentmaufras/redux/Scopes.kt` (same package; no new imports needed — `Reducer`, `ReduceResult`, `Effect` are all in `com.florentmaufras.redux`):

```kotlin
/**
 * Composes [child] over a dynamic collection of identified elements. The parent reduces
 * first (so it handles add / remove / broadcast actions and can observe element actions);
 * then, if the action is element-addressed (via [ForEachScope.toChildAction]), the matching
 * element is reduced and written back, and its effect is lifted to parent actions via
 * [ForEachScope.embedChildAction] and merged.
 *
 * An action for an id not present in the collection is a no-op. Elements keep their order.
 * Cancellation is not auto-namespaced: a child keys its cancellable effects on its own
 * `state.id`, and the parent emits `Effect.cancel(id)` when it removes an element.
 */
fun <ParentState, ParentAction, ChildState, ChildAction, Id> Reducer<ParentState, ParentAction>.forEach(
    scope: ForEachScope<ParentState, ParentAction, ChildState, ChildAction, Id>,
    child: Reducer<ChildState, ChildAction>,
): Reducer<ParentState, ParentAction> where ChildState : Identifiable<Id> = Reducer { state, action ->
    val parent = this.reduce(state, action)                    // parent first
    val (id, childAction) = scope.toChildAction(action) ?: return@Reducer parent
    val children = scope.toChildren(parent.state)
    val index = children.indexOfFirst { it.id == id }
    if (index < 0) return@Reducer parent                       // unknown/removed element → no-op
    val childResult = child.reduce(children[index], childAction)
    val updated = children.toMutableList().also { it[index] = childResult.state }
    ReduceResult(
        scope.fromChildren(parent.state, updated),
        Effect.merge(parent.effect, childResult.effect.map { scope.embedChildAction(id, it) }),
    )
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :redux:testDebugUnitTest --tests "*ForEachTest" --console=plain`
Expected: PASS — `ForEachTest` (4 tests).

- [ ] **Step 7: Commit**

```bash
git add redux/src/main/java/com/florentmaufras/redux/Identifiable.kt \
        redux/src/main/java/com/florentmaufras/redux/ForEachScope.kt \
        redux/src/main/java/com/florentmaufras/redux/Scopes.kt \
        redux/src/test/java/com/florentmaufras/redux/ForEachTest.kt
git commit -m "feat(redux): add forEach operator composing a collection of child reducers"
```

---

## Task 2: Store-level independent cancellation + version bump

**Files:**
- Modify: `redux/src/test/java/com/florentmaufras/redux/ForEachTest.kt`
- Modify: `redux/build.gradle.kts`

**Interfaces:**
- Consumes: `Store`, `forEach`, `ForEachScope`, `Identifiable` (Task 1), `Effect.cancellable`/`Effect.cancel`.
- Produces: no new production API — proves the operator integrates with the store's cancellation and bumps the library version.

- [ ] **Step 1: Add the store-level test**

This test proves two sibling elements' effects cancel independently through a real store. It exercises the operator end-to-end (it passes once Task 1 is in place; it is an integration guard, not a red-first unit).

Add the coroutine-test imports and the test to `redux/src/test/java/com/florentmaufras/redux/ForEachTest.kt`. Add these imports to the existing import block:

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
```

Change the class header from `class ForEachTest {` to:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class ForEachTest {
```

Add a store subclass just after the `composed` property:

```kotlin
    private class ListStore(
        override val reducer: Reducer<ListState, ListAction>,
    ) : Store<ListState, ListAction>(ListState(counters = listOf(Counter(1), Counter(2))))
```

Add setup/teardown and the test (place after the existing tests):

```kotlin
    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun siblingElementEffects_cancelIndependently() = runTest {
        val store = ListStore(reducer = composed)

        store.send(ListAction.Element(1, CounterAction.Work))
        store.send(ListAction.Element(2, CounterAction.Work))
        assertEquals(2, store.trackedEffectJobCount)

        store.send(ListAction.Element(1, CounterAction.Stop))   // cancels only element 1's effect
        advanceUntilIdle()
        assertEquals(1, store.trackedEffectJobCount)            // element 2 still running
    }
```

- [ ] **Step 2: Run the tests to verify they pass**

Run: `./gradlew :redux:testDebugUnitTest --tests "*ForEachTest" --console=plain`
Expected: PASS — `ForEachTest` (5 tests).

- [ ] **Step 3: Bump the library version**

Modify `redux/build.gradle.kts`: change `version = "3.0.0"` to `version = "3.1.0"`.

- [ ] **Step 4: Run the full suite**

Run: `./gradlew :redux:testDebugUnitTest :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, zero failures across both modules.

- [ ] **Step 5: Commit**

```bash
git add redux/src/test/java/com/florentmaufras/redux/ForEachTest.kt redux/build.gradle.kts
git commit -m "test(redux): prove forEach effects cancel per element; bump to 3.1.0"
```

---

## Self-Review

**Spec coverage:**
- `Identifiable<Id>` marker → Task 1 Step 3. ✓
- `ForEachScope` descriptor (toChildren/fromChildren/toChildAction/embedChildAction, `where ChildState : Identifiable<Id>`) → Task 1 Step 4. ✓
- `forEach` operator (parent-first, element routing on post-parent state, unknown-id no-op, order preserved, effect lifting with id embedded) → Task 1 Step 5; covered by tests in Step 1. ✓
- Minimal cancellation contract (child keys on `state.id`; independent per-element cancel) → Task 2 store-level test. ✓
- KDoc on all public types → Task 1 Steps 3–5. ✓
- Version `3.0.0 → 3.1.0` → Task 2 Step 3. ✓
- Full `:redux` + `:app` suites green → Task 2 Step 4. ✓

**Placeholder scan:** No TBD/TODO; every code step contains complete code. ✓

**Type consistency:** `forEach(scope, child)` signature matches the spec and the `Produces` block; `ForEachScope` member names (`toChildren`/`fromChildren`/`toChildAction`/`embedChildAction`) are identical across the interface, the test's anonymous implementation, and the operator body; `Identifiable.id` used consistently; `Effect.allActions()` flattening helper matches the sealed-tree shape used in `ScopeTest`. ✓
