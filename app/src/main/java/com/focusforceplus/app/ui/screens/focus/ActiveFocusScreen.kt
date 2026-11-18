package com.focusforceplus.app.ui.screens.focus

import com.focusforceplus.app.ui.theme.ffBackdrop
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.ui.components.OutlineButton
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.TonalButton
import com.focusforceplus.app.ui.guards.GuardCopy
import com.focusforceplus.app.ui.guards.GuardDialog
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FfType

@Composable
fun ActiveFocusScreen(
    onBack: () -> Unit,
    viewModel: ActiveFocusViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors
    var ending by remember { mutableStateOf(false) }

    if (!s.isRunning) {
        FocusFinished(sessionName = s.sessionName, completed = s.showCompleted, onDone = onBack)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .ffBackdrop()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Leaving the screen never ends the session; the timer lives in a service.
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textSecondary)
            }
            Text(
                s.sessionName,
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (s.invincible) Icon(Icons.Filled.Lock, contentDescription = "Invincible mode", tint = colors.accent)
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Countdown(remainingSeconds = s.remainingSeconds, totalSeconds = s.totalSeconds, paused = s.paused)
        }

        if (s.invincible) {
            Text(
                "Invincible: it runs to the end. You can leave the app; the timer keeps going.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TonalButton(
                    text = if (s.paused) "Resume" else "Pause",
                    icon = if (s.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    onClick = { if (s.paused) viewModel.resume() else viewModel.pause() },
                    modifier = Modifier.weight(1f).height(56.dp),
                )
                OutlineButton(
                    text = "End early",
                    onClick = { ending = true },
                    modifier = Modifier.weight(1f).height(56.dp),
                )
            }
        }
    }

    if (ending) {
        GuardDialog(
            guard = s.endGuard,
            copy = GuardCopy.FOCUS_END,
            subject = s.sessionName,
            onPassed = {
                ending = false
                viewModel.endSession()
                onBack()
            },
            onDismiss = { ending = false },
        )
    }
}

@Composable
private fun Countdown(remainingSeconds: Int, totalSeconds: Int, paused: Boolean) {
    val colors = FfTheme.colors
    val track = colors.surfaceHigh
    val arc = if (paused) colors.textMuted else colors.accent
    val fraction = remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1).toFloat()
    Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (fraction > 0f) {
                drawArc(arc, -90f, fraction * 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatCountdown(remainingSeconds),
                style = FfType.number.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize),
                color = colors.text,
            )
            Text(if (paused) "paused" else "left", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        }
    }
}

@Composable
private fun FocusFinished(sessionName: String, completed: Boolean, onDone: () -> Unit) {
    val colors = FfTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .ffBackdrop()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (completed) "Session done" else "Session ended",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMuted,
            )
            if (sessionName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(sessionName, style = MaterialTheme.typography.headlineMedium, color = colors.text)
            }
        }
        PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp))
    }
}

private fun formatCountdown(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
