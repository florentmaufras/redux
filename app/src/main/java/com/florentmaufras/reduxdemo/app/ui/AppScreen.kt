package com.florentmaufras.reduxdemo.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florentmaufras.reduxdemo.app.AppAction
import com.florentmaufras.reduxdemo.app.AppState
import com.florentmaufras.reduxdemo.app.AppStore
import com.florentmaufras.reduxdemo.chronometers.ui.ChronometersSection
import com.florentmaufras.reduxdemo.universities.ui.UniversitiesScreen

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val store: AppStore = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext as Application
                @Suppress("UNCHECKED_CAST")
                return AppStore(application = app) as T
            }
        }
    )
    val state by store.state.collectAsState()

    LaunchedEffect(Unit) { store.send(AppAction.OnAppear) }

    AppContent(state) { store.send(it) }
}

@Composable
internal fun AppContent(
    state: AppState,
    dispatch: (AppAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        ChronometersSection(state.chronometers) { dispatch(AppAction.Chronometers(it)) }
        Box(modifier = Modifier.weight(1f)) {
            UniversitiesScreen(state.universities) { dispatch(AppAction.Universities(it)) }
        }
    }
}
