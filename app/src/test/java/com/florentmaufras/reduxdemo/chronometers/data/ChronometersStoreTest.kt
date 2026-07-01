package com.florentmaufras.reduxdemo.chronometers.data

import com.florentmaufras.redux.TestStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChronometersStoreTest {

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun add_autoStartsAndTicksEachSecond() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = TestStore(ChronometersState(), chronometersReducer)

        store.send(ChronometersAction.Add)
        // Auto-start ran eagerly: one running chronometer, no tick yet.
        assertEquals(1, store.currentState.chronometers.size)
        assertEquals(true, store.currentState.chronometers[0].isRunning)
        assertEquals(0, store.currentState.chronometers[0].elapsedSeconds)
        assertEquals(true, store.receivedActions.contains(ChronometersAction.Chronometer(0, ChronometerAction.Play)))

        advanceTimeBy(2_000); runCurrent()
        assertEquals(2, store.currentState.chronometers[0].elapsedSeconds)

        store.send(ChronometersAction.PauseAll) // stop the loop
    }

    @Test
    fun pauseAll_stopsTicking() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = TestStore(ChronometersState(), chronometersReducer)

        store.send(ChronometersAction.Add)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, store.currentState.chronometers[0].elapsedSeconds)

        store.send(ChronometersAction.PauseAll)
        assertEquals(false, store.currentState.chronometers[0].isRunning)

        advanceTimeBy(3_000); runCurrent()
        assertEquals(1, store.currentState.chronometers[0].elapsedSeconds) // no further ticks
    }

    @Test
    fun remove_stopsRemovedElementTicksButOthersKeepRunning() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = TestStore(ChronometersState(), chronometersReducer)

        store.send(ChronometersAction.Add) // id 0
        store.send(ChronometersAction.Add) // id 1
        advanceTimeBy(1_000); runCurrent()

        val ticksForZeroBeforeRemove = store.receivedActions.count {
            it == ChronometersAction.Chronometer(0, ChronometerAction.Tick)
        }
        store.send(ChronometersAction.Remove(0))
        advanceTimeBy(2_000); runCurrent()

        // Removed element is gone; the survivor kept ticking (1 + 2 = 3).
        assertEquals(listOf(1), store.currentState.chronometers.map { it.id })
        assertEquals(3, store.currentState.chronometers[0].elapsedSeconds)
        // Removed element's tick effect was cancelled: no new Tick actions for id 0 arrived.
        val ticksForZeroAfterRemove = store.receivedActions.count {
            it == ChronometersAction.Chronometer(0, ChronometerAction.Tick)
        }
        assertEquals(ticksForZeroBeforeRemove, ticksForZeroAfterRemove)

        store.send(ChronometersAction.PauseAll) // stop the survivor
    }
}
