package com.florentmaufras.reduxdemo.chronometers.data

sealed interface ChronometerAction {
    data object Play : ChronometerAction
    data object Pause : ChronometerAction
    data object Reset : ChronometerAction
    data object Tick : ChronometerAction
}
