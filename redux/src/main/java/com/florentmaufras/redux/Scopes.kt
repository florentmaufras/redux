package com.florentmaufras.redux

fun <ParentState, ParentAction, ChildState, ChildAction> Reducer<ParentState, ParentAction>.scope(
    scope: Scope<ParentState, ParentAction, ChildState, ChildAction>,
    child: Reducer<ChildState, ChildAction>,
): Reducer<ParentState, ParentAction> = Reducer { state, action ->
    val parent = this.reduce(state, action)            // parent runs first / observes the action
    val childAction = scope.toChildAction(action)
        ?: return@Reducer parent
    val childResult = child.reduce(scope.toChildState(parent.state), childAction)
    ReduceResult(
        scope.fromChildState(parent.state, childResult.state),
        Effect.merge(parent.effect, childResult.effect.map(scope.embedChildAction)),
    )
}
