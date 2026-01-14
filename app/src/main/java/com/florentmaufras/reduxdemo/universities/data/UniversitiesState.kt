package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.State

data class UniversitiesState(
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val website: String? = null,
    val countrySearched: String = "Canada",
    val universities: ArrayList<University> = arrayListOf(),
): State
