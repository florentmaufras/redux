package com.florentmaufras.reduxdemo.app

import com.florentmaufras.redux.Effect
import com.florentmaufras.redux.ReduceResult
import com.florentmaufras.redux.Reducer
import com.florentmaufras.redux.Scope
import com.florentmaufras.redux.scope
import com.florentmaufras.reduxdemo.chronometers.data.ChronometersAction
import com.florentmaufras.reduxdemo.chronometers.data.ChronometersState
import com.florentmaufras.reduxdemo.chronometers.data.chronometersReducer
import com.florentmaufras.reduxdemo.universities.data.UniversitiesAction
import com.florentmaufras.reduxdemo.universities.data.UniversitiesReducer
import com.florentmaufras.reduxdemo.universities.data.UniversitiesState
import com.florentmaufras.reduxdemo.universities.data.ViewState

private val universitiesScope =
    object : Scope<AppState, AppAction, UniversitiesState, UniversitiesAction> {
        override val toChildState: (AppState) -> UniversitiesState = { it.universities }
        override val fromChildState: (AppState, UniversitiesState) -> AppState = { s, c -> s.copy(universities = c) }
        override val toChildAction: (AppAction) -> UniversitiesAction? = { (it as? AppAction.Universities)?.action }
        override val embedChildAction: (UniversitiesAction) -> AppAction = { AppAction.Universities(it) }
    }

private val chronometersScope =
    object : Scope<AppState, AppAction, ChronometersState, ChronometersAction> {
        override val toChildState: (AppState) -> ChronometersState = { it.chronometers }
        override val fromChildState: (AppState, ChronometersState) -> AppState = { s, c -> s.copy(chronometers = c) }
        override val toChildAction: (AppAction) -> ChronometersAction? = { (it as? AppAction.Chronometers)?.action }
        override val embedChildAction: (ChronometersAction) -> AppAction = { AppAction.Chronometers(it) }
    }

fun appReducer(universitiesReducer: UniversitiesReducer): Reducer<AppState, AppAction> {
    val base = Reducer<AppState, AppAction> { state, action ->
        when (action) {
            AppAction.OnAppear ->
                if (state.universities.countrySearched.isNotBlank() &&
                    state.universities.viewState == ViewState.Idle
                ) {
                    ReduceResult(
                        state,
                        Effect.send(
                            AppAction.Universities(
                                UniversitiesAction.LoadUniversities(state.universities.countrySearched)
                            )
                        ),
                    )
                } else {
                    ReduceResult(state)
                }
            is AppAction.Universities -> ReduceResult(state)
            is AppAction.Chronometers -> ReduceResult(state)
        }
    }
    return base
        .scope(universitiesScope, universitiesReducer)
        .scope(chronometersScope, chronometersReducer)
}
