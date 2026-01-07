package com.florentmaufras.redux

sealed class ActionTest: Action {
    data class Save(val saveName: String): ActionTest()
    data object Rollback: ActionTest()

    data class ActionWithEffectWithAction(val effectName: String): ActionTest()
}