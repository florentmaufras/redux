package com.florentmaufras.reduxdemo.chronometers.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChronometersReducerTest {

    @Test
    fun add_appendsRunningChronometerWithNextId() {
        val result = chronometersReducer.reduce(ChronometersState(), ChronometersAction.Add)
        assertEquals(1, result.state.chronometers.size)
        assertEquals(ChronometerState(id = 0, isRunning = true), result.state.chronometers[0])
        assertEquals(1, result.state.nextId)
    }

    @Test
    fun add_twice_usesDistinctIncrementingIds() {
        val first = chronometersReducer.reduce(ChronometersState(), ChronometersAction.Add)
        val second = chronometersReducer.reduce(first.state, ChronometersAction.Add)
        assertEquals(listOf(0, 1), second.state.chronometers.map { it.id })
        assertEquals(2, second.state.nextId)
    }

    @Test
    fun remove_dropsMatchingElement() {
        val state = ChronometersState(
            chronometers = listOf(ChronometerState(0), ChronometerState(1)),
            nextId = 2,
        )
        val result = chronometersReducer.reduce(state, ChronometersAction.Remove(0))
        assertEquals(listOf(1), result.state.chronometers.map { it.id })
    }

    @Test
    fun elementAction_updatesOnlyMatchingChild() {
        val state = ChronometersState(
            chronometers = listOf(ChronometerState(0), ChronometerState(1, elapsedSeconds = 5)),
            nextId = 2,
        )
        val result = chronometersReducer.reduce(
            state,
            ChronometersAction.Chronometer(1, ChronometerAction.Tick),
        )
        assertEquals(listOf(0, 6), result.state.chronometers.map { it.elapsedSeconds })
    }

    @Test
    fun broadcastOnEmptyState_isHarmlessNoOp() {
        val result = chronometersReducer.reduce(ChronometersState(), ChronometersAction.PauseAll)
        assertEquals(ChronometersState(), result.state)
    }
}
