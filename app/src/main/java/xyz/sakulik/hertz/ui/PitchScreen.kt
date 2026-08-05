package xyz.sakulik.hertz.ui

import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.sakulik.hertz.R
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PitchScreen(viewModel: PitchViewModel = viewModel(factory = PitchViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 生命周期感知：遵守用户手动选择，切前台时自动恢复
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resumeListeningFromLifecycle()
                Lifecycle.Event.ON_PAUSE  -> viewModel.stopListening(isUserAction = false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopListening(isUserAction = false)
        }
    }

    LaunchedEffect(uiState.hasError) {
        if (uiState.hasError) {
            Toast.makeText(
                context,
                context.getString(R.string.mic_error_toast),
                Toast.LENGTH_LONG
            ).show()
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

        // 仪表盘（半圆表盘与指针），独立控制高度与圆心，解决与音符文字的穿透冲突
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val trackColor = MaterialTheme.colorScheme.surfaceVariant

            Canvas(modifier = Modifier.fillMaxSize()) {
                val arcCenter = Offset(size.width / 2f, size.height - 12.dp.toPx())
                val radius = size.height * 0.85f

                // 绘制背景半圆弧轨迹（从 210° 扫过 120° 到 330°）
                drawArc(
                    color = trackColor,
                    startAngle = 210f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(arcCenter.x - radius, arcCenter.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )

                // 绘制音高偏差指示针
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 当前音符显示区域（独立于 Canvas，防止交叉遮挡）
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
            label = "noteAnimation"
        ) { text ->
            Text(
                text = text,
                fontSize = 56.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val freqText = uiState.currentFrequency?.let { String.format(Locale.US, "%.1f Hz", it) } ?: "-- Hz"
        Text(
            text = freqText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 音域统计卡片
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
                    Text(
                        text = stringResource(R.string.label_lowest),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = range.lowestNote ?: "--",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.label_range),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = stringResource(R.string.unit_semitones, range.rangeInSemitones),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.label_highest),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = range.highestNote ?: "--",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 控制按钮组件
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    if (uiState.isListening) {
                        viewModel.stopListening(isUserAction = true)
                    } else {
                        viewModel.startListening(isUserAction = true)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (uiState.isListening) {
                        stringResource(R.string.btn_pause)
                    } else {
                        stringResource(R.string.btn_start)
                    }
                )
            }

            OutlinedButton(
                onClick = { viewModel.resetRange() },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.btn_reset_range))
            }
        }
    }
}
