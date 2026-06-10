package com.florentmaufras.redux

interface Reducer<Action : Any, State : Any, Effect : Any> {
    fun reduce(action: Action, state: State): ReduceResult<State, Effect>
}
