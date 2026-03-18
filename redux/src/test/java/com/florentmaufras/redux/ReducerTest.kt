package com.florentmaufras.redux

class ReducerTest : Reducer<ActionTest, StateTest, EffectTest> {
    override fun reduce(action: ActionTest, state: StateTest): ReduceResult<StateTest, EffectTest> {
        return when (action) {
            is ActionTest.Save -> ReduceResult(
                state.copy(name = action.saveName),
                EffectResult.Some(EffectTest.SaveAPICall)
            )
            is ActionTest.Rollback -> ReduceResult(state.copy(), EffectResult.None)
            is ActionTest.ActionWithEffectWithAction -> ReduceResult(
                state.copy(),
                EffectResult.Some(EffectTest.EffectWithAction(action.effectName))
            )
        }
    }
}
