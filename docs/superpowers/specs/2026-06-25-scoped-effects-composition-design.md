# Scoped Effects & Composition Model — Design

Date: 2026-06-25
Status: Approved (design), pending spec review

## Problem

`ScopedReducer` declares a `ChildEffect` type parameter that is never used. In
`reduce`, the child reducer's result effect (`childResult.effect`) is read for
its `state` but its effect is discarded:

```kotlin
val childResult = childReducer.reduce(childAction, scope.toChildState(state))
val newState = scope.fromChildState(state, childResult.state)   // state used…
// childResult.effect is never read → any child effect is silently dropped
```

The existing tests hide this because the test child reducer is typed
`Reducer<ChildAction, ChildState, Nothing>`, so no child effect is ever
constructed.

The naive fix — propagate the child effect into the parent's effect channel via
a new `Scope.fromChildEffect: (ChildEffect) -> ParentEffect` — was rejected.

## Why effect propagation is the wrong fix

This library models `Effect` as a **separate, developer-defined data type**,
interpreted by `EffectHandler.handle(effect): Flow<Action>` ("effects as data").
This is deliberate and valuable — `TestStore.dispatchedEffects` lets a test
assert that a reducer returned a specific effect without running it.

The Composable Architecture (TCA, Swift) — the reference for this style — does
**not** treat effects as a communicable type. An effect is `Effect<Action>`:
opaque async work parameterized by the action it can feed back. Child effects
"reach" the parent only incidentally, because `Scope`/`ifLet` map the child's
`Effect<ChildAction>` to `Effect<ParentAction>` automatically — the developer
writes no effect mapping. Cross-feature communication is done with **delegate
actions**: the child emits a delegate action and the **parent reducer
pattern-matches** it (e.g. `case .destination(.presented(.editItem(.save)))`).

TCA can auto-map child effects for free because `Effect<Action>` is structurally
tied to `Action`. In this library `ChildEffect` and `ParentEffect` have **no**
structural relationship, so a `fromChildEffect` mapping forces the parent to know
about the child's private async concerns. That is the smell. Effects should stay
local to whoever owns the action.

## Decision: nested-stores composition model

A feature is a `Store` (reducer + `EffectHandler` + state). Features compose by
**nesting stores that share state through `ScopedStateOwner`**. Effects are
always handled by the store that owns the action — never propagated across a
boundary. Cross-feature signaling uses **delegate actions**, not effects.

This makes the effect-propagation problem disappear, and aligns the library's
own pieces (`ScopedStateOwner` already exists and already works this way).

## Changes

### 1. `ScopedReducer` becomes effectless-by-construction

- Remove the `ChildEffect` type parameter.
- Child reducer type becomes `Reducer<ChildAction, ChildState, Nothing>`.
- `childResult.effect` is then statically `EffectResult<Nothing>` ≡ always
  `None`. Dropping a child effect becomes a **compile-time impossibility**
  rather than a silent runtime bug.
- Keep the state lens (`toChildState` / `fromChildState`), action routing
  (`toChildAction`), and the `fromChildAction` delegate bubble — those were
  correct.
- `ScopedReducer` keeps its current output type
  `Reducer<ParentAction, ParentState, ParentAction>` (the bubbled `ParentAction`
  surfaced as an effect). Behavior is unchanged from today.

Rationale for narrowing rather than extending: the previous signature advertised
an effect channel it threw away. Constraining the child to `Nothing` states the
real contract — *this composition is for pure children; use a nested `Store`
for effects.*

### 2. `ScopedStateOwner` affirmed as the path for effectful children

No code change. It is documented as *the* way an effectful child feature
participates: the child is its own `Store` with its own `EffectHandler`, sharing
parent state via the lens. Its effects run locally.

### 3. Documentation + tests

- KDoc on the public composition types — `Scope`, `ScopedReducer`,
  `ScopedStateOwner`, `EffectHandler` — stating the composition model and where
  effects live. (This is a published library; these are public API.)
- Tests:
  - Existing `ScopedReducerTest` (child reducer already `…, Nothing`) keeps
    passing unchanged — verifying state write-back, delegate bubble via
    `fromChildAction`, and the no-mapping → no-effect path.
  - No new runtime test is needed for "child effects can't leak" because the
    constraint is enforced by the type system; it is captured as a documented
    invariant in the `ScopedReducer` KDoc.

### 4. Version bump

`ScopedReducer`'s public signature changes (one fewer type parameter, narrower
child reducer bound), so this is a breaking change. Bump
`redux/build.gradle.kts` `version` from `1.0.0` to `2.0.0`.

## Out of scope (deferred to a separate list item)

A **reducer combinator** (`Reduce { own } + ScopedReducer`, both run per action)
that closes the delegate loop synchronously inside one store. Without it,
`ScopedReducer`'s bubbled `ParentAction` is produced but not auto-re-dispatched
by the `Store`. The combinator is the correct home for closed-loop synchronous
bubble-up and for a parent reducer that mixes its own logic with scoped
children. It is not required to fix the dropped-effect bug or to land the
composition model.

## Success criteria

- `ScopedReducer` no longer has a `ChildEffect` type parameter.
- It is impossible to construct a `ScopedReducer` from an effect-producing child
  reducer (compile-time).
- All existing redux + app unit tests pass.
- Public composition types carry KDoc describing the nested-stores model.
- Version bumped to reflect the breaking API change (`ScopedReducer` signature).
