package com.florentmaufras.redux

/**
 * A [Store] for tests: construct it directly with an [initialState] and [reducer] (no
 * subclassing), drive it with [send], and assert against [currentState]/[state]. Effects
 * run on the real runtime, so set the main dispatcher to a test dispatcher
 * (`Dispatchers.setMain(...)`) and advance the scheduler to let them complete.
 *
 * [receivedActions] records every action the store processed in order — both the actions
 * you [send] and the ones effects feed back — so a test can assert what an effect emitted.
 */
class TestStore<State, Action>(
    initialState: State,
    override val reducer: Reducer<State, Action>,
) : Store<State, Action>(initialState) {

    private val _receivedActions = mutableListOf<Action>()

    /** Every action processed so far, in order, including effect-fed actions. */
    val receivedActions: List<Action> get() = _receivedActions.toList()

    override fun send(action: Action) {
        _receivedActions.add(action)
        super.send(action)
    }
}
