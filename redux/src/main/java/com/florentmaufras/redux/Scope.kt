package com.florentmaufras.redux

/**
 * Composes a child reducer into a parent reducer by providing lenses for state and action,
 * and a mapping function for child effects back to parent effects.
 *
 * Usage:
 *   val scoped = Scope(
 *       toChildState = { parent -> parent.child },
 *       fromChildState = { parent, child -> parent.copy(child = child) },
 *       toChildAction = { action -> action as? ChildAction },
 *       fromChildEffect = { childEffect -> ParentEffect.Child(childEffect) },
 *       childReducer = ChildReducer()
 *   )
 */
class Scope<
    ParentAction : Any,
    ParentState : State,
    ParentEffect : Any,
    ChildAction : Any,
    ChildState : State,
    ChildEffect : Any>(
    private val toChildState: (ParentState) -> ChildState,
    private val fromChildState: (ParentState, ChildState) -> ParentState,
    private val toChildAction: (ParentAction) -> ChildAction?,
    private val fromChildEffect: (ChildEffect) -> ParentEffect,
    private val childReducer: Reducer<ChildAction, ChildState, ChildEffect>
) : Reducer<ParentAction, ParentState, ParentEffect> {

    override fun reduce(
        action: ParentAction,
        state: ParentState
    ): ReduceResult<ParentState, ParentEffect> {
        val childAction = toChildAction(action)
            ?: return ReduceResult(state, EffectResult.None)

        val childResult = childReducer.reduce(childAction, toChildState(state))
        val newState = fromChildState(state, childResult.state)
        val parentEffect = when (val e = childResult.effect) {
            is EffectResult.Some -> EffectResult.Some(fromChildEffect(e.effect))
            EffectResult.None -> EffectResult.None
        }
        return ReduceResult(newState, parentEffect)
    }
}
