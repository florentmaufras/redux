package com.florentmaufras.redux

/**
 * A synchronous, non-ViewModel store for use in unit tests.
 * Lets tests send actions and assert state step by step without coroutines or lifecycle.
 *
 * Effects produced by the reducer are recorded in [dispatchedEffects] but not executed —
 * this keeps the test scope focused on the reducer's pure logic.
 */
class TestStore<A : Action, S : State, E : Effect>(
    initialState: S,
    private val reducer: Reducer<A, S, E>
) {
    var state: S = initialState
        private set

    val dispatchedEffects: MutableList<E> = mutableListOf()

    /**
     * Sends [action] through the reducer, updates [state], and records any produced effect.
     * Returns the EffectResult so callers can assert on it directly.
     */
    fun send(action: A): EffectResult<E> {
        val result = reducer.reduce(action, state)
        state = result.state
        if (result.effect is EffectResult.Some) {
            dispatchedEffects.add((result.effect as EffectResult.Some<E>).effect)
        }
        return result.effect
    }

    fun assertState(expected: S) {
        check(state == expected) {
            "State mismatch.\nExpected: $expected\nActual:   $state"
        }
    }
}
