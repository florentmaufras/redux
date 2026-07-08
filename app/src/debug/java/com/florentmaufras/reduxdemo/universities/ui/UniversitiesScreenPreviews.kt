package com.florentmaufras.reduxdemo.universities.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.florentmaufras.reduxdemo.universities.data.UniversitiesState
import com.florentmaufras.reduxdemo.universities.data.University
import com.florentmaufras.reduxdemo.universities.data.ViewState

private fun previewUniversity(name: String, withWebsite: Boolean) = University(
    country = "Canada",
    webPages = if (withWebsite) arrayListOf("https://example.com") else null,
    stateProvince = null,
    alphaTwoCode = "CA",
    domains = arrayListOf("example.com"),
    name = name,
)

@Preview(showBackground = true)
@Composable
private fun UniversitiesScreenLoadedPreview() {
    UniversitiesScreen(
        state = UniversitiesState(
            countrySearched = "Canada",
            viewState = ViewState.Loaded(
                listOf(
                    previewUniversity("University of Toronto", withWebsite = true),
                    previewUniversity("McGill University", withWebsite = false),
                )
            ),
        ),
        send = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun UniversitiesScreenLoadingPreview() {
    UniversitiesScreen(
        state = UniversitiesState(countrySearched = "Canada", viewState = ViewState.Loading),
        send = {},
    )
}
