package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.Effect

sealed class UniversitiesEffect: Effect {
    data class LoadUniversities(val country: String): UniversitiesEffect()
}