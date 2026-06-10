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
