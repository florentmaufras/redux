package com.florentmaufras.redux

sealed class EffectTest {
    data object SaveAPICall: EffectTest()
    data class EffectWithAction(val name: String): EffectTest()
}