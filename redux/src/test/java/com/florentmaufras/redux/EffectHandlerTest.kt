package com.florentmaufras.redux

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

class EffectHandlerTest(
    val repository: Any,
    val dispatcherIO: CoroutineDispatcher,
) : EffectHandler<ActionTest, EffectTest> {
    override fun handle(effect: EffectTest): Flow<ActionTest> = when (effect) {
        is EffectTest.SaveAPICall -> flow {
            withContext(dispatcherIO) { repository.toString() }
            // no emission — side effect only
        }
        is EffectTest.EffectWithAction -> flowOf(ActionTest.Save(effect.name))
    }
}
