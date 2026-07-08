package com.florentmaufras.reduxdemo.app

import com.florentmaufras.reduxdemo.chronometers.data.ChronometersState
import com.florentmaufras.reduxdemo.universities.data.UniversitiesState

data class AppState(
    val chronometers: ChronometersState = ChronometersState(),
    val universities: UniversitiesState = UniversitiesState(),
)
