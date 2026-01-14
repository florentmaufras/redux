package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.ReduceResult
import com.florentmaufras.redux.Reducer

class UniversitiesReducer : Reducer<UniversitiesAction, UniversitiesState, UniversitiesEffect> {
    override fun reduce(
        action: UniversitiesAction,
        state: UniversitiesState
    ): ReduceResult<UniversitiesState, UniversitiesEffect?> {
        var effect: UniversitiesEffect? = null
        var newState: UniversitiesState = state
        when (action) {
            is UniversitiesAction.LoadUniversities -> {
                newState = state.copy(
                    isLoading = true,
                    countrySearched = action.country,
                    hasError = false
                )
                effect = UniversitiesEffect.LoadUniversities(action.country)
            }

            is UniversitiesAction.UniversitiesLoaded -> {
                newState = state.copy(isLoading = false, universities = action.universities)
            }

            is UniversitiesAction.LoadError -> {
                newState = state.copy(isLoading = false, hasError = true)
            }

            is UniversitiesAction.LoadWebsite -> {
                newState = state.copy(website = action.website)
            }

            is UniversitiesAction.WebsiteLoaded -> {
                newState = state.copy(website = null)
            }
        }
        return ReduceResult(newState, effect)
    }
}