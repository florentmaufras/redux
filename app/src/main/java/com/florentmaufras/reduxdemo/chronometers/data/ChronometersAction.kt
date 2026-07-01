package com.florentmaufras.reduxdemo.chronometers.data

sealed interface ChronometersAction {
    data class Chronometer(val id: Int, val action: ChronometerAction) : ChronometersAction
    data object Add : ChronometersAction
    data class Remove(val id: Int) : ChronometersAction
    data object PlayAll : ChronometersAction
    data object PauseAll : ChronometersAction
    data object ResetAll : ChronometersAction
}
