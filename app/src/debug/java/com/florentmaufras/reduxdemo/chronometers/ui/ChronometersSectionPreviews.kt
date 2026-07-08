package com.florentmaufras.reduxdemo.chronometers.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.florentmaufras.reduxdemo.chronometers.data.ChronometerState
import com.florentmaufras.reduxdemo.chronometers.data.ChronometersState

@Preview(showBackground = true)
@Composable
private fun ChronometersSectionPreview() {
    ChronometersSection(
        state = ChronometersState(
            chronometers = listOf(
                ChronometerState(id = 0, elapsedSeconds = 7, isRunning = true),
                ChronometerState(id = 1, elapsedSeconds = 125, isRunning = false),
            ),
            nextId = 2,
        ),
        dispatch = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun ChronometerRowRunningPreview() {
    ChronometerRow(
        state = ChronometerState(id = 0, elapsedSeconds = 75, isRunning = true),
        dispatch = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun ChronometerRowPausedPreview() {
    ChronometerRow(
        state = ChronometerState(id = 0, elapsedSeconds = 0, isRunning = false),
        dispatch = {},
    )
}
