# TCA-style Core Rewrite (Kotlin) — Design

Date: 2026-06-25
Status: Draft (design), pending spec review

## Goal

Re-implement the library's core to match The Composable Architecture (TCA) in
Kotlin: a **single-runtime store**, **`Effect<Action>`** (effects parameterized by
the action they feed back, not a separate data type), and a **single state tree**.
This supersedes the effect-as-data model (`Effect` enum + `EffectHandler`) and the
multi-runtime draft. One spec covers the core, composition, and the app migration.

## Why

The effect-as-data model couldn't compose child effects into a parent without
exposing `ChildEffect` (no structural link between `ChildEffect` and
`ParentEffect`). TCA avoids this because an effect is `Effect<Action>`: mapping the
action lifts the effect (`childEffect.map { embedChildAction(it) }`), so child
effects compose for free and stay defined by the reducer that returns them.

## Core types

### `Effect<Action>` — a Flow wrapper

```kotlin
class Effect<out Action> private constructor(
    internal val actions: Flow<Action>,
    internal val cancelId: Any? = null,
    internal val cancelInFlight: Boolean = false,
) {
    fun <T> map(transform: (Action) -> T): Effect<T> =
        Effect(actions.map(transform), cancelId, cancelInFlight)

    fun cancellable(id: Any, cancelInFlight: Boolean = false): Effect<Action> =
        Effect(actions, id, cancelInFlight)

    companion object {
        fun <Action> none(): Effect<Action> = Effect(emptyFlow())
        fun <Action> run(block: suspend FlowCollector<Action>.() -> Unit): Effect<Action> =
            Effect(flow(block))
        fun <Action> send(action: Action): Effect<Action> = Effect(flowOf(action))
        fun <Action> merge(vararg effects: Effect<Action>): Effect<Action> =
            Effect(merge(*effects.map { it.actions }.toTypedArray()))
        fun <Action> cancel(id: Any): Effect<Action>   // cancels the in-flight effect with [id]
    }
}
```

- An effect *is* a `Flow<Action>`. Nothing interprets it; the store collects it
  and re-sends each emitted action.
- `cancellable(id, cancelInFlight)` tags the effect so the store tracks its job
  under `id` (and cancels a prior in-flight job with that id when requested).
- `cancel(id)` is a distinct effect variant carrying a **cancellation marker**
  (a separate internal field, not the same as `cancelId`): when the store runs an
  effect whose marker is set, it cancels the job registered under `id` and emits
  nothing. This keeps "register a cancellable job" and "cancel that job"
  unambiguous at the store level.

### `Reducer<State, Action>` and `ReduceResult<State, Action>`

```kotlin
fun interface Reducer<State, Action> {
    fun reduce(state: State, action: Action): ReduceResult<State, Action>
}

data class ReduceResult<State, Action>(
    val state: State,
    val effect: Effect<Action> = Effect.none(),
)
```

Reducers are pure: they compute the next state and return an `Effect` describing
any async work. Dependencies (e.g. an API service) are injected into the concrete
reducer and captured inside `Effect.run { ... }`; the work runs later, in the
store, never during `reduce`.

### `Store<State, Action>` — single runtime

```kotlin
abstract class Store<State, Action>(initialState: State) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()
    val currentState: State get() = _state.value

    protected abstract val reducer: Reducer<State, Action>

    private val effectJobs = mutableMapOf<Any, Job>()

    fun send(action: Action) {
        val result = reducer.reduce(_state.value, action)   // serialized on the main dispatcher
        _state.value = result.state
        runEffect(result.effect)
    }

    private fun runEffect(effect: Effect<Action>) {
        effect.cancelId?.let { if (effect.cancelInFlight) effectJobs[it]?.cancel() }
        val job = viewModelScope.launch { effect.actions.collect { send(it) } }
        effect.cancelId?.let { id ->
            effectJobs[id] = job
            job.invokeOnCompletion { effectJobs.remove(id, job) }
        }
    }
}
```

- `send` reduces, commits state, and runs the effect; actions the effect emits
  re-enter `send`, closing the loop. `Effect.none()` collects an empty flow and
  does nothing.
- Action processing is serialized on the main dispatcher (`viewModelScope` =
  `Dispatchers.Main.immediate`), so `reduce` + state update don't race.
- Cancellation reuses the `id → Job` map and the completed-job cleanup already in
  the codebase.

## Composition — replaces `ScopedReducer`

`Scope` keeps the state lens and gains a **total** action embedding:

```kotlin
interface Scope<ParentState, ParentAction, ChildState, ChildAction> {
    val toChildState: (ParentState) -> ChildState
    val fromChildState: (ParentState, ChildState) -> ParentState
    val toChildAction: (ParentAction) -> ChildAction?        // extract (route down), optional
    val embedChildAction: (ChildAction) -> ParentAction      // embed (lift up), total
}
```

A `combine`/`scope` operator lifts a child reducer into the parent and runs both
(parent first, so it can observe the child action), mapping the child effect up:

```kotlin
fun <PS, PA, CS, CA> Reducer<PS, PA>.scope(
    scope: Scope<PS, PA, CS, CA>,
    child: Reducer<CS, CA>,
): Reducer<PS, PA> = Reducer { state, action ->
    val parent = this.reduce(state, action)             // parent observes/handles all actions
    val childAction = scope.toChildAction(action)
        ?: return@Reducer parent
    val childResult = child.reduce(scope.toChildState(parent.state), childAction)
    ReduceResult(
        scope.fromChildState(parent.state, childResult.state),
        Effect.merge(parent.effect, childResult.effect.map(scope.embedChildAction)),
    )
}
```

This is the only place effects from two reducers merge, and it is total/lossless
because `Effect.merge` is well-defined for `Effect<Action>`. Child effects are
never exposed as a separate type — only `.map`'d into parent actions.

## Deletions

- `EffectResult`, `EffectHandler` (and `EffectTest`, `EffectHandlerTest`).
- `StateOwner`, `OwnedStateOwner`, `ScopedStateOwner` — single state tree lives in
  `Store`.
- `ScopedReducer` — replaced by the `scope` operator above.
- The `Effect` type parameter throughout (`Reducer`, `ReduceResult`, `Store` go
  from 3 type params to 2).

## App migration (Universities feature)

- Delete `UniversitiesEffect` (enum) and `UniversitiesEffectHandler`.
- `UniversitiesReducer(service)` returns effects inline:
  ```kotlin
  is LoadUniversities -> ReduceResult(
      s.copy(viewState = Loading),
      Effect.run { emit(UniversitiesLoaded(service.get(action.country))) }
          .cancellable("search", cancelInFlight = true),
  )
  is UniversitiesLoaded -> ReduceResult(s.copy(viewState = Loaded(action.universities)))
  ```
- `UniversitiesStore : Store<UniversitiesState, UniversitiesAction>` — no handler,
  no `StateOwner`; constructs with the injected `service`.
- `UniversitiesViewModel` / `UniversitiesScreen` largely unchanged (`state` is
  still a `StateFlow`). The URL-open side effect becomes an `Effect.run { }`.

## Testing strategy

- A new `TestStore<State, Action>` drives `send`, runs effects against **injected
  fakes** (e.g. a fake `service`) on a `TestDispatcher`, and lets tests assert the
  **state** after each action and the **actions** an effect feeds back.
- Reducer-only tests assert the returned state and (where practical) that an
  effect was produced/none; effect behavior is asserted via the `TestStore`.
- Existing tests rewritten: `ReducerTest`, `StoreTest`, `ReduxTest`,
  `ScopedReducerTest` (→ composition test), `StateOwnerTest` (removed),
  `UniversitiesReducerTest`, `UniversitiesStoreTest`, `UniversitiesViewModelTest`.

## Risks / notes

- **Lost compile-time effect enumeration.** Effects are no longer a closed enum;
  the trade is accepted (TCA recovers testability via injected dependencies + a
  `TestStore`).
- **Re-entrant `send`** must stay on a single dispatcher to keep action
  processing serialized; documented and enforced via `viewModelScope`.
- **`Effect.cancel(id)` / `cancellable`** are the one spot where the Flow model
  needs store-side bookkeeping; reuses the existing `effectJobs` map.

## Success criteria

- Core compiles as `Effect<Action>` / `Reducer<State, Action>` /
  `ReduceResult<State, Action>` / single-runtime `Store<State, Action>`.
- `EffectResult`, `EffectHandler`, `StateOwner`, `OwnedStateOwner`,
  `ScopedStateOwner`, `ScopedReducer` are gone.
- The `scope` operator composes a child reducer and lifts its effects via
  `embedChildAction`, with a passing composition test.
- The Universities feature works end-to-end on the new core; all redux + app unit
  tests pass.
- Public API carries KDoc; version bumped `2.0.0 → 3.0.0`.

## Out of scope

- Navigation helpers (`ifLet`/`forEach` for optional/collection state) beyond the
  single `scope` operator — can follow later.
- Persisting the “effect as serializable data” testability — intentionally dropped.
