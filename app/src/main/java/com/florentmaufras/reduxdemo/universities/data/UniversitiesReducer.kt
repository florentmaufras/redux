package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.EffectResult
import com.florentmaufras.redux.ReduceResult
import com.florentmaufras.redux.Reducer

class UniversitiesReducer : Reducer<UniversitiesAction, UniversitiesState, UniversitiesEffect> {
    override fun reduce(
        action: UniversitiesAction,
        state: UniversitiesState
    ): ReduceResult<UniversitiesState, UniversitiesEffect> {
        return when (action) {
            is UniversitiesAction.LoadUniversities -> ReduceResult(
                state.copy(viewState = ViewState.Loading, countrySearched = action.country),
                EffectResult.Some(UniversitiesEffect.LoadUniversities(action.country))
            )
            is UniversitiesAction.UniversitiesLoaded -> ReduceResult(
                state.copy(viewState = ViewState.Loaded(action.universities)),
                EffectResult.None
            )
            is UniversitiesAction.LoadError -> ReduceResult(
                state.copy(viewState = ViewState.Error(action.message)),
                EffectResult.None
            )
            is UniversitiesAction.LoadWebsite -> ReduceResult(
                state.copy(website = action.website),
                EffectResult.None
            )
            is UniversitiesAction.WebsiteLoaded -> ReduceResult(
                state.copy(website = null),
                EffectResult.None
            )
        }
    }
}
