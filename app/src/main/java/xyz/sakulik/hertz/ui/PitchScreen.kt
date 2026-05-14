package xyz.sakulik.hertz.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PitchScreen(viewModel: PitchViewModel = viewModel(factory = PitchViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsState()

    // 生命周期感知：退到后台自动暂停，回到前台自动恢复
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startListening()
                Lifecycle.Event.ON_PAUSE  -> viewModel.stopListening()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopListening()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val animatedSmoothedCents by animateFloatAsState(
            targetValue = uiState.smoothedCents,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "centsAnimation"
        )

        val pointerColor by animateColorAsState(
            targetValue = if (abs(uiState.smoothedCents) < 10f) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            label = "pointerColorAnimation"
        )

        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            val trackColor = MaterialTheme.colorScheme.surfaceVariant

            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.width * 0.45f
                val arcCenter = Offset(size.width / 2f, size.height)

                drawArc(
                    color = trackColor,
                    startAngle = 210f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(arcCenter.x - radius, arcCenter.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )

                val angleDegrees = 270f + (animatedSmoothedCents / 50f).coerceIn(-1f, 1f) * 60f
                val angleRadians = angleDegrees * PI / 180.0
                val endX = arcCenter.x + radius * cos(angleRadians).toFloat()
                val endY = arcCenter.y + radius * sin(angleRadians).toFloat()

                drawLine(
                    color = pointerColor,
                    start = arcCenter,
                    end = Offset(endX, endY),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val noteText = if (uiState.currentNote != null && uiState.currentOctave != null) {
                "${uiState.currentNote}${uiState.currentOctave}"
            } else {
                "--"
            }

            AnimatedContent(
                targetState = noteText,
                transitionSpec = {
                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "noteAnimation",
                modifier = Modifier.padding(top = 24.dp)
            ) { text ->
                Text(
                    text = text,
                    fontSize = 64.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val freqText = uiState.currentFrequency?.let { String.format(Locale.US, "%.1f Hz", it) } ?: "-- Hz"
        Text(
            text = freqText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val range = uiState.vocalRange
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Lowest", style = MaterialTheme.typography.labelMedium)
                    Text(text = range.lowestNote ?: "--", style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Range", style = MaterialTheme.typography.labelMedium)
                    Text(text = "${range.rangeInSemitones} semitones", style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Highest", style = MaterialTheme.typography.labelMedium)
                    Text(text = range.highestNote ?: "--", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    if (uiState.isListening) {
                        viewModel.stopListening()
                    } else {
                        viewModel.startListening()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (uiState.isListening) "Pause" else "Start")
            }

            OutlinedButton(
                onClick = { viewModel.resetRange() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset Range")
            }
        }
    }
}
