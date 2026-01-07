package com.florentmaufras.redux

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class EffectHandlerTest(
    val repository: Any,
    val dispatcherIO: CoroutineDispatcher,
): EffectHandler<ActionTest, EffectTest> {
    override suspend fun handle(effect: EffectTest): ActionTest? {
        return when(effect) {
            is EffectTest.SaveAPICall -> saveAPICall()
            is EffectTest.EffectWithAction -> effectWithAction(effect.name)
        }
    }

    private suspend fun saveAPICall(): ActionTest? {
        withContext(dispatcherIO) {
            repository.toString() // Could/should call your repo
        }
        return null
    }

    private fun effectWithAction(name: String): ActionTest {
        // Do something... then save
        return ActionTest.Save(name)
    }
}