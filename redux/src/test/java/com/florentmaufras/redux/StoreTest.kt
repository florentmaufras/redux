package com.florentmaufras.redux

class StoreTest(
    initialName: String,
    override val reducer: ReducerTest,
    override val effectHandler: EffectHandlerTest
) : Store<ActionTest, StateTest, EffectTest, ReducerTest, EffectHandlerTest>(StateTest(initialName))