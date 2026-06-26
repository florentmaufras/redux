package com.florentmaufras.redux

interface Scope<ParentState, ParentAction, ChildState, ChildAction> {
    val toChildState: (ParentState) -> ChildState
    val fromChildState: (ParentState, ChildState) -> ParentState
    val toChildAction: (ParentAction) -> ChildAction?
    val embedChildAction: (ChildAction) -> ParentAction
}
