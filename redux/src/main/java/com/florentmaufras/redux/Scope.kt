package com.florentmaufras.redux

/**
 * Lenses mapping a parent feature to a child: state down/up ([toChildState] /
 * [fromChildState]) and the action prism ([toChildAction] extract down,
 * [embedChildAction] embed up). Used by [scope] to compose reducers.
 */
interface Scope<ParentState, ParentAction, ChildState, ChildAction> {
    val toChildState: (ParentState) -> ChildState
    val fromChildState: (ParentState, ChildState) -> ParentState
    val toChildAction: (ParentAction) -> ChildAction?
    val embedChildAction: (ChildAction) -> ParentAction
}
