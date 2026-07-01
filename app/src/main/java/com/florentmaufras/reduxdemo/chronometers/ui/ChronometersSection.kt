package com.florentmaufras.reduxdemo.chronometers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.florentmaufras.reduxdemo.chronometers.data.ChronometerAction
import com.florentmaufras.reduxdemo.chronometers.data.ChronometerState
import com.florentmaufras.reduxdemo.chronometers.data.ChronometersAction
import com.florentmaufras.reduxdemo.chronometers.data.ChronometersState

@Composable
fun ChronometersSection(
    state: ChronometersState,
    dispatch: (ChronometersAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text("Chronometers", modifier = Modifier.padding(bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { dispatch(ChronometersAction.Add) }) { Text("Add") }
            OutlinedButton(onClick = { dispatch(ChronometersAction.PlayAll) }) { Text("Play all") }
            OutlinedButton(onClick = { dispatch(ChronometersAction.PauseAll) }) { Text("Pause all") }
            OutlinedButton(onClick = { dispatch(ChronometersAction.ResetAll) }) { Text("Reset all") }
        }
        state.chronometers.forEach { chrono ->
            ChronometerRow(chrono, dispatch)
        }
    }
}

@Composable
private fun ChronometerRow(
    state: ChronometerState,
    dispatch: (ChronometersAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(formatElapsed(state.elapsedSeconds))
        if (state.isRunning) {
            OutlinedButton(onClick = { dispatch(ChronometersAction.Chronometer(state.id, ChronometerAction.Pause)) }) {
                Text("Pause")
            }
        } else {
            OutlinedButton(onClick = { dispatch(ChronometersAction.Chronometer(state.id, ChronometerAction.Play)) }) {
                Text("Play")
            }
        }
        OutlinedButton(onClick = { dispatch(ChronometersAction.Chronometer(state.id, ChronometerAction.Reset)) }) {
            Text("Reset")
        }
        OutlinedButton(onClick = { dispatch(ChronometersAction.Remove(state.id)) }) {
            Text("Remove")
        }
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
