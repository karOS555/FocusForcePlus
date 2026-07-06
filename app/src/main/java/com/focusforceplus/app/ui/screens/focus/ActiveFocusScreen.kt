package com.focusforceplus.app.ui.screens.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveFocusScreen(
    onBack: () -> Unit,
    viewModel: ActiveFocusViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsState()
    // 0 = hidden, 1 = first confirm, 2 = second confirm (double confirmation).
    // Leaving this screen never ends the session — the foreground service owns the
    // timer — so system Back stays usable even for invincible sessions.
    var endConfirmStep by remember { mutableIntStateOf(0) }

    if (!s.isRunning) {
        FocusFinishedContent(
            sessionName = s.sessionName,
            completed = s.showCompleted,
            onDone = onBack,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.sessionName, maxLines = 1) },
                navigationIcon = {
                    if (s.invincible) {
                        IconButton(onClick = {}) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Invincible mode",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FocusCountdownCircle(
                remainingSeconds = s.remainingSeconds,
                totalSeconds = s.totalSeconds,
                paused = s.paused,
            )

            Spacer(Modifier.height(40.dp))

            if (s.invincible) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Invincible - runs to the end. You can leave the app; the timer keeps going.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = { if (s.paused) viewModel.resume() else viewModel.pause() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Icon(
                            if (s.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.paused) "Resume" else "Pause")
                    }
                    Button(
                        onClick = { endConfirmStep = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("End") }
                }
            }
        }
    }

    // ── Double confirmation for ending early ──────────────────────────────────
    when (endConfirmStep) {
        1 -> AlertDialog(
            onDismissRequest = { endConfirmStep = 0 },
            title = { Text("End session early?") },
            text = { Text("You still have time on the clock. End the focus session anyway?") },
            confirmButton = {
                Button(onClick = { endConfirmStep = 2 }) { Text("End session") }
            },
            dismissButton = {
                TextButton(onClick = { endConfirmStep = 0 }) { Text("Keep focusing") }
            },
        )
        2 -> AlertDialog(
            onDismissRequest = { endConfirmStep = 0 },
            title = { Text("Really sure?") },
            text = { Text("Last chance - your focus streak ends here.") },
            confirmButton = {
                Button(
                    onClick = {
                        endConfirmStep = 0
                        viewModel.endSession()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Yes, end it") }
            },
            dismissButton = {
                TextButton(onClick = { endConfirmStep = 0 }) { Text("Back to focus") }
            },
        )
    }
}

@Composable
private fun FocusCountdownCircle(
    remainingSeconds: Int,
    totalSeconds: Int,
    paused: Boolean,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val arcColor = if (paused) MaterialTheme.colorScheme.onSurfaceVariant
                   else MaterialTheme.colorScheme.primary
    val fraction = remainingSeconds.toFloat() / totalSeconds.toFloat()

    Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (fraction > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = fraction * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatCountdown(remainingSeconds),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                if (paused) "paused" else "remaining",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FocusFinishedContent(
    sessionName: String,
    completed: Boolean,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (completed) "Session complete!" else "Session ended",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (sessionName.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = sessionName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { Text("Done") }
    }
}

private fun formatCountdown(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
