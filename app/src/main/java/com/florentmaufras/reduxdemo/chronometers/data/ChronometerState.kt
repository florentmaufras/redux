package com.florentmaufras.reduxdemo.chronometers.data

import com.florentmaufras.redux.Identifiable

data class ChronometerState(
    override val id: Int,
    val elapsedSeconds: Int = 0,
    val isRunning: Boolean = false,
) : Identifiable<Int>
