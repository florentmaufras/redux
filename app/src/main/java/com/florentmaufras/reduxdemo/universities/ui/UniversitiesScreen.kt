package com.florentmaufras.reduxdemo.universities.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.florentmaufras.reduxdemo.universities.data.UniversitiesAction
import com.florentmaufras.reduxdemo.universities.data.UniversitiesState
import com.florentmaufras.reduxdemo.universities.data.University
import com.florentmaufras.reduxdemo.universities.data.ViewState

@Composable
fun UniversitiesScreen(
    state: UniversitiesState,
    send: (UniversitiesAction) -> Unit,
) {
    val textFieldState = rememberTextFieldState(state.countrySearched)

    Column(modifier = Modifier.padding(8.dp)) {
        Text(
            text = "Welcome to that small simple demo app!",
            modifier = Modifier.padding(8.dp)
        )
        Text(
            text = "Please search for a country below and see the universities found in it.",
            modifier = Modifier.padding(8.dp)
        )
        TextField(
            state = textFieldState,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
            onKeyboardAction = KeyboardActionHandler {
                send(UniversitiesAction.LoadUniversities(textFieldState.text.toString()))
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.padding(8.dp),
        )

        when (val vs = state.viewState) {
            ViewState.Idle -> UniversitiesNoResult(state.countrySearched)
            ViewState.Loading -> UniversitiesLoading()
            is ViewState.Error -> UniversitiesError(vs.message)
            is ViewState.Loaded -> {
                if (vs.universities.isEmpty()) UniversitiesNoResult(state.countrySearched)
                else UniversitiesContent(vs.universities, send)
            }
        }
    }
}

@Composable
private fun UniversitiesLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun UniversitiesError(message: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(if (message != null) "Error: $message" else "Oupsy daisy! Something went wrong!")
    }
}

@Composable
private fun UniversitiesNoResult(country: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("No result found for this search: $country")
    }
}

@Composable
private fun UniversitiesContent(
    universities: List<University>,
    dispatchAction: (UniversitiesAction) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp),
        verticalArrangement = Arrangement.SpaceAround,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        item {
            Text(
                text = "University/ies found:",
                modifier = Modifier.padding(8.dp)
            )
        }
        items(items = universities) { university ->
            Card(
                modifier = Modifier.padding(8.dp)
            ) {
                Column {
                    Text(
                        text = university.name,
                        modifier = Modifier.padding(8.dp)
                    )
                    Text(
                        text = university.country,
                        modifier = Modifier.padding(8.dp)
                    )
                    if (university.webPages?.isNotEmpty() == true) {
                        Button(
                            onClick = {
                                dispatchAction(UniversitiesAction.OpenWebsite(university.webPages[0]))
                            },
                            modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 16.dp)
                        ) {
                            Text("Open Website")
                        }
                    }
                }
            }
        }
    }
}

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
