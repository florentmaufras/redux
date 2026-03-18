package com.florentmaufras.reduxdemo.universities.ui

import android.app.Application
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florentmaufras.reduxdemo.universities.data.UniversitiesAction
import com.florentmaufras.reduxdemo.universities.data.UniversitiesStore
import com.florentmaufras.reduxdemo.universities.data.UniversitiesViewModel
import com.florentmaufras.reduxdemo.universities.data.University
import com.florentmaufras.reduxdemo.universities.data.ViewState

@Composable
fun UniversitiesScreen() {
    val context = LocalContext.current  // read here, in composable scope
    val store: UniversitiesStore = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext as Application
                @Suppress("UNCHECKED_CAST")
                return UniversitiesStore(application = app) as T
            }
        }
    )
    val viewModel = remember { UniversitiesViewModel(store) }
    val state = viewModel.stateFlow.collectAsState()
    val textFieldState = rememberTextFieldState(state.value.countrySearched)

    Column(
        modifier = Modifier.padding(8.dp)
    ) {
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
                viewModel.dispatchAction(UniversitiesAction.LoadUniversities(textFieldState.text.toString()))
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.padding(8.dp),
        )

        when (val vs = state.value.viewState) {
            ViewState.Idle -> UniversitiesNoResult(state.value.countrySearched)
            ViewState.Loading -> UniversitiesLoading()
            is ViewState.Error -> UniversitiesError(vs.message)
            is ViewState.Loaded -> {
                if (vs.universities.isEmpty()) UniversitiesNoResult(state.value.countrySearched)
                else UniversitiesContent(vs.universities, viewModel::dispatchAction)
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
