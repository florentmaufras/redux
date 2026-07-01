package com.florentmaufras.reduxdemo.universities.data

sealed class ViewState {
    data object Idle : ViewState()
    data object Loading : ViewState()
    data class Error(val message: String? = null) : ViewState()
    data class Loaded(val universities: List<University>) : ViewState()
}
