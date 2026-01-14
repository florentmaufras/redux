package com.florentmaufras.reduxdemo.universities.data

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class UniversitiesViewModel(
    private val store: UniversitiesStore = UniversitiesStore(),
): ViewModel() {

    init {
        if (store.state.value.countrySearched.isNotBlank()) {
            store.dispatch(UniversitiesAction.LoadUniversities(store.state.value.countrySearched))
        }
    }

    val stateFlow: StateFlow<UniversitiesState> = store.state

    fun dispatchAction(action: UniversitiesAction) {
        store.dispatch(action)
    }
}