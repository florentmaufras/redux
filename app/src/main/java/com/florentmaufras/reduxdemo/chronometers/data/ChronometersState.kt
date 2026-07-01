package com.florentmaufras.reduxdemo.chronometers.data

data class ChronometersState(
    val chronometers: List<ChronometerState> = emptyList(),
    val nextId: Int = 0,
)
