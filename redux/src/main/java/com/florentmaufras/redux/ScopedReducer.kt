package com.florentmaufras.redux

/**
 * Embeds an effectless child reducer into a parent reducer over a [Scope].
 *
 * The child reducer is constrained to `Reducer<ChildAction, ChildState, Nothing>`
 * on purpose: this composition runs inside a single store with a single
 * [EffectHandler], so it has no place to run a child's effects. Effects must
 * stay local to the store that owns the action. A child feature that needs its
 * own effects should instead be its own [Store] sharing parent state through a
 * [ScopedStateOwner] (nested-stores composition).
 *
 * Cross-feature signalling is done with delegate actions: [Scope.fromChildAction]
 * maps a child action to a parent action, which this reducer surfaces as its
 * effect so the parent can react.
 */
class ScopedReducer<
    ParentAction : Any,
    ParentState : State,
    ChildAction : Any,
    ChildState : State>(
    private val scope: Scope<ParentAction, ParentState, ChildAction, ChildState>,
    private val childReducer: Reducer<ChildAction, ChildState, Nothing>
) : Reducer<ParentAction, ParentState, ParentAction> {

    override fun reduce(
        action: ParentAction,
        state: ParentState
    ): ReduceResult<ParentState, ParentAction> {
        val childAction = scope.toChildAction(action)
            ?: return ReduceResult(state)

        val childResult = childReducer.reduce(childAction, scope.toChildState(state))
        val newState = scope.fromChildState(state, childResult.state)
        val bubbledAction = scope.fromChildAction(childAction)
            ?.let { EffectResult.Some(it) }
            ?: EffectResult.None

        return ReduceResult(newState, bubbledAction)
    }
}
