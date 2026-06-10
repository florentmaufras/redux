package com.florentmaufras.reduxdemo.universities.data

sealed class UniversitiesEffect {
    data class LoadUniversities(val country: String) : UniversitiesEffect()
    data class OpenWebsite(val url: String) : UniversitiesEffect()
}
