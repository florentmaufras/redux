package com.florentmaufras.redux

class ReducerTest: Reducer<ActionTest, StateTest, EffectTest> {
    override fun reduce(
        action: ActionTest,
        state: StateTest
    ): ReduceResult<StateTest, EffectTest?> {
        return when(action) {
            is ActionTest.Save -> {
                ReduceResult(state.copy(name= action.saveName), EffectTest.SaveAPICall)
            }
            is ActionTest.Rollback -> {
                ReduceResult(state.copy(), null)
            }
            is ActionTest.ActionWithEffectWithAction ->  {
                ReduceResult(state.copy(), EffectTest.EffectWithAction(action.effectName))
            }
        }
    }
}