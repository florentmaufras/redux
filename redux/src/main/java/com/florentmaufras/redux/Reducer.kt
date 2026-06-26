package com.florentmaufras.redux

/** Pure function: given [State] and an [Action], returns the next state and an [Effect]. */
fun interface Reducer<State, Action> {
    fun reduce(state: State, action: Action): ReduceResult<State, Action>
}
