package com.florentmaufras.reduxdemo.chronometers.data

import com.florentmaufras.redux.Effect
import com.florentmaufras.redux.ReduceResult
import com.florentmaufras.redux.Reducer
import kotlinx.coroutines.delay

class ChronometerReducer : Reducer<ChronometerState, ChronometerAction> {
    override fun reduce(
        state: ChronometerState,
        action: ChronometerAction,
    ): ReduceResult<ChronometerState, ChronometerAction> = when (action) {
        ChronometerAction.Play -> ReduceResult(
            state.copy(isRunning = true),
            Effect.run<ChronometerAction> {
                while (true) {
                    delay(1_000)
                    emit(ChronometerAction.Tick)
                }
            }.cancellable(state.id, cancelInFlight = true),
        )
        ChronometerAction.Pause -> ReduceResult(
            state.copy(isRunning = false),
            Effect.cancel(state.id),
        )
        ChronometerAction.Reset -> ReduceResult(
            state.copy(elapsedSeconds = 0, isRunning = false),
            Effect.cancel(state.id),
        )
        ChronometerAction.Tick -> ReduceResult(
            state.copy(elapsedSeconds = state.elapsedSeconds + 1),
        )
    }
}
