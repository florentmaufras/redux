package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.Action

sealed class UniversitiesAction: Action {
    data class LoadUniversities(val country: String): UniversitiesAction()
    data class UniversitiesLoaded(val universities: ArrayList<University>): UniversitiesAction()
    data object LoadError: UniversitiesAction()
    data class LoadWebsite(val website: String): UniversitiesAction()
    data object WebsiteLoaded: UniversitiesAction()
}