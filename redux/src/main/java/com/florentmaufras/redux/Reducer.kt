package com.florentmaufras.redux

fun interface Reducer<State, Action> {
    fun reduce(state: State, action: Action): ReduceResult<State, Action>
}
