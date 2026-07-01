package com.florentmaufras.reduxdemo.app.ui

import android.app.Application
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

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        ChronometersSection(state.chronometers) { store.send(AppAction.Chronometers(it)) }
        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
            UniversitiesScreen(state.universities) { store.send(AppAction.Universities(it)) }
        }
    }
}
