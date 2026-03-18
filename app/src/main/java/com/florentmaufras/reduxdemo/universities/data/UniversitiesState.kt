package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.State

data class UniversitiesState(
    val viewState: ViewState = ViewState.Idle,
    val countrySearched: String = "Canada",
) : State
