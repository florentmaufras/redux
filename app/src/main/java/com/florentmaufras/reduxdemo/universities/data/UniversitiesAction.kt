package com.florentmaufras.reduxdemo.universities.data

sealed class UniversitiesAction {
    data class LoadUniversities(val country: String) : UniversitiesAction()
    data class UniversitiesLoaded(val universities: List<University>) : UniversitiesAction()
    data class LoadError(val message: String? = null) : UniversitiesAction()
    data class OpenWebsite(val url: String) : UniversitiesAction()
}
