package com.florentmaufras.redux

/**
 * A set of lenses mapping between a parent feature and a child feature.
 *
 * - [toChildState] / [fromChildState] read a child slice out of parent state and
 *   write an updated child slice back.
 * - [toChildAction] routes a parent action to the child (or `null` to ignore it).
 * - [fromChildAction] is the delegate channel: it maps a child action up to a
 *   parent action (or `null` for none), letting a child signal the parent.
 *
 * Note there is deliberately no child-to-parent *effect* mapping. Effects stay
 * local to the store that owns the action; effectful child features compose as
 * nested [Store]s sharing state via [ScopedStateOwner], not by propagating
 * effect data across this boundary. See [ScopedReducer].
 */
interface Scope<
    ParentAction : Any,
    ParentState : Any,
    ChildAction : Any,
    ChildState : Any> {

    val toChildState: (ParentState) -> ChildState
    val fromChildState: (ParentState, ChildState) -> ParentState
    val toChildAction: (ParentAction) -> ChildAction?
    val fromChildAction: (ChildAction) -> ParentAction?
}
