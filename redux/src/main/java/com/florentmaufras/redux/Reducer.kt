package com.florentmaufras.redux

interface Reducer<Action: com.florentmaufras.redux.Action, State: com.florentmaufras.redux.State, Effect: com.florentmaufras.redux.Effect> {
    fun reduce(action: Action, state: State) : ReduceResult<State, Effect?>
}