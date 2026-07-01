package com.florentmaufras.reduxdemo.chronometers.data

import com.florentmaufras.redux.Effect
import com.florentmaufras.redux.ForEachScope
import com.florentmaufras.redux.ReduceResult
import com.florentmaufras.redux.Reducer
import com.florentmaufras.redux.forEach

private val chronometersScope =
    object : ForEachScope<ChronometersState, ChronometersAction, ChronometerState, ChronometerAction, Int> {
        override val toChildren: (ChronometersState) -> List<ChronometerState> = { it.chronometers }
        override val fromChildren: (ChronometersState, List<ChronometerState>) -> ChronometersState =
            { s, c -> s.copy(chronometers = c) }
        override val toChildAction: (ChronometersAction) -> Pair<Int, ChronometerAction>? =
            { a -> (a as? ChronometersAction.Chronometer)?.let { it.id to it.action } }
        override val embedChildAction: (Int, ChronometerAction) -> ChronometersAction =
            { id, a -> ChronometersAction.Chronometer(id, a) }
    }

private fun broadcast(
    chronometers: List<ChronometerState>,
    action: ChronometerAction,
): Effect<ChronometersAction> =
    Effect.merge(
        *chronometers
            .map { Effect.send(ChronometersAction.Chronometer(it.id, action)) }
            .toTypedArray()
    )

private val chronometersOwnReducer = Reducer<ChronometersState, ChronometersAction> { state, action ->
    when (action) {
        ChronometersAction.Add -> {
            val id = state.nextId
            ReduceResult(
                state.copy(
                    chronometers = state.chronometers + ChronometerState(id = id, isRunning = true),
                    nextId = id + 1,
                ),
                Effect.send(ChronometersAction.Chronometer(id, ChronometerAction.Play)),
            )
        }
        is ChronometersAction.Remove -> ReduceResult(
            state.copy(chronometers = state.chronometers.filterNot { it.id == action.id }),
            Effect.cancel(action.id),
        )
        ChronometersAction.PlayAll -> ReduceResult(state, broadcast(state.chronometers, ChronometerAction.Play))
        ChronometersAction.PauseAll -> ReduceResult(state, broadcast(state.chronometers, ChronometerAction.Pause))
        ChronometersAction.ResetAll -> ReduceResult(state, broadcast(state.chronometers, ChronometerAction.Reset))
        is ChronometersAction.Chronometer -> ReduceResult(state)
    }
}

val chronometersReducer: Reducer<ChronometersState, ChronometersAction> =
    chronometersOwnReducer.forEach(chronometersScope, ChronometerReducer())
