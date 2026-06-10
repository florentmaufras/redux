package com.florentmaufras.redux

class ScopedReducer<
    ParentAction : Any,
    ParentState : State,
    ChildAction : Any,
    ChildState : State,
    ChildEffect : Any>(
    private val scope: Scope<ParentAction, ParentState, ChildAction, ChildState>,
    private val childReducer: Reducer<ChildAction, ChildState, ChildEffect>
) : Reducer<ParentAction, ParentState, ParentAction> {

    override fun reduce(
        action: ParentAction,
        state: ParentState
    ): ReduceResult<ParentState, ParentAction> {
        val childAction = scope.toChildAction(action)
            ?: return ReduceResult(state, EffectResult.None)

        val childResult = childReducer.reduce(childAction, scope.toChildState(state))
        val newState = scope.fromChildState(state, childResult.state)
        val bubbledAction = scope.fromChildAction(childAction)
            ?.let { EffectResult.Some(it) }
            ?: EffectResult.None

        return ReduceResult(newState, bubbledAction)
    }
}
