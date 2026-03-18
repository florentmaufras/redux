package com.florentmaufras.reduxdemo.universities.data

import kotlinx.coroutines.flow.StateFlow

class UniversitiesViewModel(val store: UniversitiesStore) {

    val stateFlow: StateFlow<UniversitiesState> = store.state

    fun dispatchAction(action: UniversitiesAction) {
        store.dispatch(action)
    }
}
