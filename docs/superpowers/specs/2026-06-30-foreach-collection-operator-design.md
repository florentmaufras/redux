# `forEach` Collection Reducer Operator — Design

Date: 2026-06-30
Status: Approved (design), pending spec review

## Goal

Add a `forEach` reducer operator to `:redux` so a parent feature can compose a
**dynamic collection (`0..X`) of identified child features** — the collection
analog of the existing single-child `scope` operator. This is the deferred
"navigation helper" from the TCA-core rewrite design, and it unblocks features
where the parent owns a list of children that each have their own state, actions,
and cancellable effects, while the parent can also add/remove elements and
broadcast actions to all of them.

Driving consumer (separate spec/plan): a chronometers demo where a parent holds
`0..X` chronometers, can add one and broadcast play/pause/reset to all, and each
child handles its own play/pause/reset and runs its own ticking effect.

## Background

TCA models this with a first-class `forEach` operator over an
`IdentifiedArrayOf<Child.State>` plus an id-wrapped `IdentifiedAction`. The
operator routes an element-addressed action to the matching element, writes it
back, and lifts that element's effects up (with the id embedded). Per-element
effect identity is scoped by the element id, so removing an element cancels its
in-flight effects. Broadcast ("all elements") actions are **not** a `forEach`
concern — they are ordinary parent-reducer logic.

Our library already has the single-child equivalent: `Scope` (state lens + action
prism) and `Reducer.scope(scope, child)`. `forEach` mirrors that shape for a
collection.

## New types

```kotlin
/** Stable identity for an element addressed within a collection. */
interface Identifiable<out Id> {
    val id: Id
}

/**
 * Lenses/prisms mapping a parent feature to a collection of identified children.
 * Symmetric with [Scope]; the action prism carries the element [Id].
 */
interface ForEachScope<ParentState, ParentAction, ChildState, ChildAction, Id>
    where ChildState : Identifiable<Id> {
    val toChildren: (ParentState) -> List<ChildState>
    val fromChildren: (ParentState, List<ChildState>) -> ParentState
    val toChildAction: (ParentAction) -> Pair<Id, ChildAction>?   // extract an element-addressed action
    val embedChildAction: (Id, ChildAction) -> ParentAction       // embed one back up
}
```

Collection representation is a plain `List<ChildState>` where `ChildState :
Identifiable<Id>` — ordered, no new collection type, id read from the element.

## The operator (added to `Scopes.kt`, beside `scope`)

```kotlin
fun <ParentState, ParentAction, ChildState, ChildAction, Id> Reducer<ParentState, ParentAction>.forEach(
    scope: ForEachScope<ParentState, ParentAction, ChildState, ChildAction, Id>,
    child: Reducer<ChildState, ChildAction>,
): Reducer<ParentState, ParentAction>
    where ChildState : Identifiable<Id> = Reducer { state, action ->
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

## Behavior & semantics

- **Parent-first**, exactly like `scope`. The parent reducer runs first so it
  handles **add / remove / broadcast** (pause-all, reset-all) actions and can
  observe element actions; then an element-addressed action is routed to its
  child.
- Element lookup runs against the **post-parent** state, so an element the parent
  just added in this same reduction is immediately reachable.
- **Unknown id → no-op** (returns the parent result unchanged). This makes a stale
  action for a just-removed element harmless.
- Child effects are lifted with the id embedded (`embedChildAction(id, it)`), so
  actions a child effect emits round-trip back through this operator to the same
  child.
- **Order preserved** (list index replace). **Ids assumed unique**
  (`indexOfFirst` takes the first match) — documented as a precondition.

## Cancellation contract (minimal — no auto-namespacing)

`forEach` does state routing + action routing + effect lifting only, symmetric
with `scope`. It does **not** rewrite child effect cancel-ids or diff the
collection.

Because `ChildState : Identifiable<Id>`, a child reducer keys its cancellable
effects on its own identity:

```kotlin
Effect.run { … }.cancellable(state.id)   // distinct element → distinct cancel id
```

so `Effect.cancel(state.id)` cancels exactly that element's effect and no
sibling's. Cancelling an element's effects when it is **removed** is done
explicitly by the parent reducer emitting `Effect.cancel(removedId)` in the same
reduction that drops the element. (Automatic per-element namespacing +
cancel-on-remove, as TCA does, is a deliberate future enhancement, not part of
this change.)

If a single element needs multiple distinct cancellable effects, it keys them on
a composite of its id (e.g. `state.id to "tick"`) — the store's cancel-id
namespace already accepts any `Any` key.

## Files

- New: `redux/src/main/java/com/florentmaufras/redux/Identifiable.kt`
- New: `redux/src/main/java/com/florentmaufras/redux/ForEachScope.kt`
- Modify: `redux/src/main/java/com/florentmaufras/redux/Scopes.kt` — add `forEach`
- Modify: `redux/build.gradle.kts` — version `3.0.0` → `3.1.0` (additive public API)
- New test: `redux/src/test/java/com/florentmaufras/redux/ForEachTest.kt`

## Testing (TDD)

Reducer-level (call `composed.reduce(...)` directly):
- Element action updates **only** the matching child's state; siblings untouched.
- Parent observes/handles the same action (parent-first): a broadcast action
  mutates parent/all-children state before element routing.
- Child effect is lifted to a parent element-action with the id embedded
  (assert via the effect-flattening helper already used in `ScopeTest`).
- An element-addressed action for an **unknown id** is a no-op (state unchanged;
  parent effect preserved).

Store-level (drive a `Store`/`TestStore` on `UnconfinedTestDispatcher`):
- One element's `cancellable(id)` effect can be cancelled (via `cancel(id)`)
  **independently** of a sibling element's still-running effect
  (`trackedEffectJobCount` reflects only the cancelled one going away).

## Success criteria

- `Identifiable`, `ForEachScope`, and `Reducer.forEach` compile and are public
  with KDoc.
- `forEach` routes element-addressed actions to the correct child, preserves
  order, lifts child effects with the id embedded, and no-ops on unknown ids.
- Parent-first ordering holds (broadcast/add/remove handled by the parent before
  element routing).
- Per-element cancellation is independent, proven by a store-level test.
- Version bumped to `3.1.0`; full `:redux` + `:app` suites green.

## Out of scope

- Automatic per-element effect namespacing and cancel-on-remove (future).
- The chronometers demo feature itself (separate spec + plan).
- An `IdentifiedArray`-style collection type (plain `List` + `Identifiable` is
  sufficient).
