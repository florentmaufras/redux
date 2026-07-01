package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.Effect
import com.florentmaufras.redux.ReduceResult
import com.florentmaufras.redux.Reducer
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import timber.log.Timber

class UniversitiesReducer(
    private val universitiesService: UniversitiesService,
    private val openUrl: (String) -> Unit = {},
    private val timber: Timber.Tree = Timber.asTree(),
) : Reducer<UniversitiesState, UniversitiesAction> {

    override fun reduce(
        state: UniversitiesState,
        action: UniversitiesAction,
    ): ReduceResult<UniversitiesState, UniversitiesAction> = when (action) {
        is UniversitiesAction.LoadUniversities -> ReduceResult(
            state.copy(viewState = ViewState.Loading, countrySearched = action.country),
            Effect.run<UniversitiesAction> {
                try {
                    emit(UniversitiesAction.UniversitiesLoaded(universitiesService.getUniversities(action.country)))
                } catch (e: Exception) {
                    timber.e(e)
                    emit(UniversitiesAction.LoadError(e.message))
                }
            }.cancellable("search", cancelInFlight = true),
        )

        is UniversitiesAction.UniversitiesLoaded ->
            ReduceResult(state.copy(viewState = ViewState.Loaded(action.universities)))

        is UniversitiesAction.LoadError ->
            ReduceResult(state.copy(viewState = ViewState.Error(action.message)))

        is UniversitiesAction.OpenWebsite ->
            ReduceResult(state, Effect.run { openUrl(action.url) })
    }
}
