# Nested Store Composition via Action Routing — Design

Date: 2026-06-25
Status: SUPERSEDED by `2026-06-25-tca-core-rewrite-design.md`. This design kept
the effect-as-data model and used a multi-runtime store hierarchy. We pivoted to
a full TCA model in Kotlin (single runtime, `Effect<Action>`), which makes this
approach obsolete. Retained only as a record of the rejected direction.

## Goal

Compose features as a hierarchy of `Store`s in one dispatch graph, where:

- Each store owns its reducer, its effects, and a slice of state.
- **Effects are private** to the store that produces them — an effect is a
  reducer's internal "run this async work and produce an action" logic, and is
  never carried, mapped, or exposed across a scope boundary.
- The **only thing that crosses a boundary is an action**: up via
  `fromChildAction`, down via `toChildAction`.
- The **parent consumes an action first**, then routes it down to the child.

## Why not a reducer combinator (the previously-deferred idea)

A combinator composes child reducers into one root reducer. In a library where
`Effect` is a separate data type (not TCA's `Effect<Action>`), that forces a
child's effect to either be merged into a multi-effect `ReduceResult` or mapped
up via a `fromChildEffect` — both **expose child effects to the parent**, which
violates the principle above. Composing at the **store** level via action
routing keeps effects local and needs neither multi-effect `ReduceResult` nor
`fromChildEffect`. It supersedes the combinator item.

## Mechanism

`Store` gains:

- `dispatchToParent: ((Action) -> Unit)?` — optional. When present, this store
  is a child and forwards actions up to its parent.
- a registry of child **down-routes**: `(Action) -> Unit` lambdas, each of which
  projects the action via `toChildAction` and, if it maps, invokes that child's
  `runOwnReducer`.

```kotlin
fun dispatch(action: Action, cancelId: String? = null) {
    val up = dispatchToParent
    if (up != null) up(action)               // originated below → bubble to root
    else runOwnReducer(action, cancelId)     // root reduces
}

private fun runOwnReducer(action: Action, cancelId: String?) {
    val r = reducer.reduce(action, currentState)
    updateState { r.state }
    r.effect.ifSome { handleEffect(it, cancelId) }   // OWN effects only, local
    downRoutes.forEach { it(action) }                // toChildAction(action)?.let { child.runOwnReducer(it) }
}
```

### Wiring a child to a parent

When a child store is constructed with a `parent` and a `Scope`:

- **State**: the child's `StateOwner` is a `ScopedStateOwner` over the parent's
  state (uses `toChildState` / `fromChildState`).
- **Up-link**: `dispatchToParent = { childAction -> parent.dispatch(scope.fromChildAction(childAction)) }`.
- **Down-link**: the child registers a down-route on the parent:
  `parent.registerDownRoute { parentAction -> scope.toChildAction(parentAction)?.let { runOwnReducer(it, null) } }`.
  The lambda closes over the child's own `runOwnReducer`, so it can stay private
  (no cross-module visibility needed).

### Round trip (child action)

1. `child.dispatch(childAction)` → up-link wraps it → `parent.dispatch(parentAction)`.
2. Parent is root → `runOwnReducer`: parent reduces (consumes/reacts first),
   runs its own effects, then routes down.
3. Down-route: `toChildAction(parentAction)` → `childAction` →
   `child.runOwnReducer(childAction)`: child reduces, updates its state slice,
   **handles its own effects**.
4. A child effect emits a child action → `child.dispatch` → back to step 1.

Terminates when no action maps onward. The down-route calls `runOwnReducer`
directly (not `dispatch`), so there is no up/down infinite loop.

## Scope changes

- **`fromChildAction` becomes total**: `(ChildAction) -> ParentAction` (every
  child action wraps into a parent action, because every child action bubbles
  up). This replaces the current optional delegate shape
  `(ChildAction) -> ParentAction?`. *(Decision derived from "all actions go up" —
  flagged for review below.)*
- `toChildAction` stays optional: `(ParentAction) -> ChildAction?` (not every
  parent action concerns the child).
- `toChildState` / `fromChildState` unchanged (used by `ScopedStateOwner`).

The delegate use case (parent reacting to a specific child action) is now served
by the parent reducer pattern-matching the wrapped child action — no separate
optional mapping needed.

## Effects stay local (the core invariant)

`ChildEffect` is never a type parameter of, carried by, or visible to the parent.
Each `runOwnReducer` handles only its own reducer's effect through its own
`handleEffect` / `EffectHandler`. The effect's *result* (an action) is the only
thing that travels, via the normal dispatch path.

## Relationship to existing types — open questions for review

1. **`ScopedReducer`**: its reducer-embedding + delegate-as-effect role is
   superseded by store-wiring. Decision needed — **remove it**, or **keep it**
   for pure in-store sub-reduction (which would need its `fromChildAction` use
   reconciled with the new total signature). Recommendation: keep it out of this
   change's scope and decide separately; do not silently break it.
2. **`fromChildAction` total vs optional**: confirm the total signature is what
   you want (the round-trip model requires it).
3. **`cancelId` on down-routes**: a parent routing down currently passes no
   `cancelId` to the child's `runOwnReducer`. Confirm `cancelId` is scoped to the
   originating store (likely fine) rather than propagated across boundaries.

## Testing

- Up-routing: a child dispatch reaches the root and is reduced there.
- Parent-first: parent reduces/reacts to a child action before the child does.
- Down-routing: parent routes a mapped action into the child's reducer.
- Effect locality: a parent never receives or is typed with a child effect; a
  child effect's resulting action round-trips correctly.
- No infinite loop on a normal child-action round trip.

## Success criteria

- A child store wired to a parent forwards all its actions up; the parent
  consumes first, then routes down; the child reduces and runs its own effects.
- No `ChildEffect` appears in any parent-facing type or `ReduceResult`.
- No multi-effect `ReduceResult` and no `fromChildEffect` are introduced.
- Existing redux + app unit tests pass (after any wiring updates).

## Out of scope

- Reducer combinator (superseded by this model).
- Multi-effect `ReduceResult` (not needed).
