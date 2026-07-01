package com.florentmaufras.reduxdemo.chronometers.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChronometerReducerTest {

    private val reducer = ChronometerReducer()

    @Test
    fun play_marksRunning() {
        val result = reducer.reduce(ChronometerState(id = 1), ChronometerAction.Play)
        assertEquals(true, result.state.isRunning)
    }

    @Test
    fun pause_marksNotRunning() {
        val result = reducer.reduce(ChronometerState(id = 1, isRunning = true), ChronometerAction.Pause)
        assertEquals(false, result.state.isRunning)
    }

    @Test
    fun reset_zeroesElapsedAndStops() {
        val result = reducer.reduce(
            ChronometerState(id = 1, elapsedSeconds = 42, isRunning = true),
            ChronometerAction.Reset,
        )
        assertEquals(ChronometerState(id = 1, elapsedSeconds = 0, isRunning = false), result.state)
    }

    @Test
    fun tick_incrementsElapsedByOne() {
        val result = reducer.reduce(ChronometerState(id = 1, elapsedSeconds = 5), ChronometerAction.Tick)
        assertEquals(6, result.state.elapsedSeconds)
    }
}
