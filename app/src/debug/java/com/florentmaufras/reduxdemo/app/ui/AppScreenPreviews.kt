package com.florentmaufras.reduxdemo.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.florentmaufras.reduxdemo.app.AppState
import com.florentmaufras.reduxdemo.chronometers.data.ChronometerState
import com.florentmaufras.reduxdemo.chronometers.data.ChronometersState

@Preview(showBackground = true)
@Composable
private fun AppContentPreview() {
    AppContent(
        state = AppState(
            chronometers = ChronometersState(
                chronometers = listOf(
                    ChronometerState(id = 0, elapsedSeconds = 7, isRunning = true),
                    ChronometerState(id = 1, elapsedSeconds = 125, isRunning = false),
                ),
                nextId = 2,
            ),
        ),
        dispatch = {},
    )
}
