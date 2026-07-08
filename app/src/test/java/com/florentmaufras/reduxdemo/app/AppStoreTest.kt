package com.florentmaufras.reduxdemo.app

import com.florentmaufras.redux.TestStore
import com.florentmaufras.reduxdemo.chronometers.data.ChronometersAction
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import com.florentmaufras.reduxdemo.universities.data.University
import com.florentmaufras.reduxdemo.universities.data.UniversitiesReducer
import com.florentmaufras.reduxdemo.universities.data.UniversitiesState
import com.florentmaufras.reduxdemo.universities.data.ViewState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppStoreTest {

    private val service: UniversitiesService = mockk()

    private fun store(initial: AppState = AppState()) =
        TestStore(initial, appReducer(UniversitiesReducer(universitiesService = service)))

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onAppear_loadsUniversitiesThroughRoot() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val universities = listOf(mockk<University>())
        coEvery { service.getUniversities("Canada") } returns universities

        val store = store() // default countrySearched = "Canada"
        store.send(AppAction.OnAppear)
        advanceUntilIdle()

        assertEquals(ViewState.Loaded(universities), store.currentState.universities.viewState)
    }

    @Test
    fun chronometerTicks_throughRoot_independentOfUniversities() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = store()

        store.send(AppAction.Chronometers(ChronometersAction.Add))
        advanceTimeBy(2_000); runCurrent()

        assertEquals(2, store.currentState.chronometers.chronometers[0].elapsedSeconds)
        assertEquals(ViewState.Idle, store.currentState.universities.viewState) // universities untouched

        store.send(AppAction.Chronometers(ChronometersAction.PauseAll))
    }

    @Test
    fun onAppear_whenAlreadyLoaded_doesNotReload() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val universities = listOf(mockk<University>())
        val store = TestStore(
            AppState(universities = UniversitiesState(viewState = ViewState.Loaded(universities))),
            appReducer(UniversitiesReducer(universitiesService = service)),
        )

        store.send(AppAction.OnAppear)
        advanceUntilIdle()

        // No reload: state stays Loaded (a reload would flip viewState to Loading).
        assertEquals(ViewState.Loaded(universities), store.currentState.universities.viewState)
    }

    @Test
    fun pauseAll_throughRoot_stopsAllChronometers() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = store()

        store.send(AppAction.Chronometers(ChronometersAction.Add)) // id 0
        store.send(AppAction.Chronometers(ChronometersAction.Add)) // id 1
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(1, 1), store.currentState.chronometers.chronometers.map { it.elapsedSeconds })

        store.send(AppAction.Chronometers(ChronometersAction.PauseAll))
        assertEquals(listOf(false, false), store.currentState.chronometers.chronometers.map { it.isRunning })

        advanceTimeBy(3_000); runCurrent()
        assertEquals(listOf(1, 1), store.currentState.chronometers.chronometers.map { it.elapsedSeconds }) // frozen
    }

    @Test
    fun remove_throughRoot_dropsElementAndSurvivorKeepsTicking() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = store()

        store.send(AppAction.Chronometers(ChronometersAction.Add)) // id 0
        store.send(AppAction.Chronometers(ChronometersAction.Add)) // id 1
        advanceTimeBy(1_000); runCurrent()

        store.send(AppAction.Chronometers(ChronometersAction.Remove(0)))
        advanceTimeBy(2_000); runCurrent()

        // Remove routes through scope + forEach: the element leaves state and the survivor
        // keeps ticking (1 + 2 = 3). (That the removed element's tick is actually cancelled is
        // proven directly in ChronometersStoreTest.remove_stopsRemovedElementTicksButOthersKeepRunning.)
        assertEquals(listOf(1), store.currentState.chronometers.chronometers.map { it.id })
        assertEquals(3, store.currentState.chronometers.chronometers[0].elapsedSeconds)

        store.send(AppAction.Chronometers(ChronometersAction.PauseAll)) // stop the survivor
    }
}
