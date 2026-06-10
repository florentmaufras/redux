package com.florentmaufras.redux

class StoreTest(
    initialName: String,
    override val reducer: ReducerTest,
    private val effectHandlerTest: EffectHandlerTest
) : Store<ActionTest, StateTest, EffectTest>(OwnedStateOwner(StateTest(initialName))) {

    override fun handleEffect(effect: EffectTest, cancelId: String?) {
        launchEffect(effect, cancelId) { effectHandlerTest.handle(it) }
    }
}
