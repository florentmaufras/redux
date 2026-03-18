package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.Action

sealed class UniversitiesAction : Action {
    data class LoadUniversities(val country: String) : UniversitiesAction()
    data class UniversitiesLoaded(val universities: List<University>) : UniversitiesAction()
    data class LoadError(val message: String? = null) : UniversitiesAction()
    data class OpenWebsite(val url: String) : UniversitiesAction()
}
