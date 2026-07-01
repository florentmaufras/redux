package com.florentmaufras.reduxdemo.universities.data

data class UniversitiesState(
    val viewState: ViewState = ViewState.Idle,
    val countrySearched: String = "Canada",
)
