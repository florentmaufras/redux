package com.florentmaufras.reduxdemo.app

import com.florentmaufras.reduxdemo.chronometers.data.ChronometersAction
import com.florentmaufras.reduxdemo.universities.data.UniversitiesAction

sealed interface AppAction {
    data class Chronometers(val action: ChronometersAction) : AppAction
    data class Universities(val action: UniversitiesAction) : AppAction
    data object OnAppear : AppAction
}
