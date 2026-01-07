package com.florentmaufras.redux

sealed class EffectTest: Effect {
    data object SaveAPICall: EffectTest()
    data class EffectWithAction(val name: String): EffectTest()
}