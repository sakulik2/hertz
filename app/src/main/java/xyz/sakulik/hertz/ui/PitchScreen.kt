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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.sakulik.hertz.R
import xyz.sakulik.hertz.ui.theme.RangeValueStyle
import xyz.sakulik.hertz.ui.theme.tunerColors
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 指针配色档位：偏差小于此值视为准音 */
private const val IN_TUNE_CENTS = 10f

/** 偏差小于此值视为轻微偏离，超出则视为明显偏离 */
private const val SLIGHTLY_OFF_CENTS = 25f

/*
 * 表盘几何：从 210° 起顺时针扫过 120°，正中为 270°（正上方）。
 * ±DIAL_CENTS_SPAN 音分映射到 ±DIAL_HALF_SWEEP_DEGREES。
 */
private const val DIAL_START_ANGLE = 210f
private const val DIAL_SWEEP_ANGLE = 120f
private const val DIAL_CENTER_ANGLE = 270f
private const val DIAL_HALF_SWEEP_DEGREES = 60f
private const val DIAL_CENTS_SPAN = 50f

@Composable
fun PitchScreen(viewModel: PitchViewModel = viewModel(factory = PitchViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearRecordDialog by rememberSaveable { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (uiState.hasError) {
            Text(text = stringResource(R.string.mic_error_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.mic_error_message), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { viewModel.startListening(isUserAction = true) }) {
                Text(text = stringResource(R.string.btn_retry))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        val animatedSmoothedCents by animateFloatAsState(
            targetValue = uiState.smoothedCents,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "centsAnimation"
        )

        // 指针颜色分三档，比原先的"准/不准"二值更能反映接近程度
        val tunerColors = MaterialTheme.tunerColors
        val pointerColor by animateColorAsState(
            targetValue = when {
                abs(uiState.smoothedCents) < IN_TUNE_CENTS -> tunerColors.inTune
                abs(uiState.smoothedCents) < SLIGHTLY_OFF_CENTS -> tunerColors.slightlyOff
                else -> tunerColors.off
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
            val trackColor = tunerColors.dialTrack

            Canvas(modifier = Modifier.fillMaxSize()) {
                val arcCenter = Offset(size.width / 2f, size.height - 12.dp.toPx())
                val radius = size.height * 0.85f

                // 绘制背景半圆弧轨迹（从 210° 扫过 120° 到 330°）
                drawArc(
                    color = trackColor,
                    startAngle = DIAL_START_ANGLE,
                    sweepAngle = DIAL_SWEEP_ANGLE,
                    useCenter = false,
                    topLeft = Offset(arcCenter.x - radius, arcCenter.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )

                // 绘制音高偏差指示针
                val angleDegrees = DIAL_CENTER_ANGLE +
                    (animatedSmoothedCents / DIAL_CENTS_SPAN).coerceIn(-1f, 1f) * DIAL_HALF_SWEEP_DEGREES
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
        val noteText = uiState.currentNoteLabel ?: "--"

        AnimatedContent(
            targetState = noteText,
            transitionSpec = {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "noteAnimation"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.displayLarge,
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

        // 音域统计卡片：本次会话 + 历史最佳
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.label_session_range),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                val range = uiState.vocalRange
                RangeRow(
                    lowest = range.lowest?.displayName(uiState.noteNaming),
                    semitones = range.rangeInSemitones,
                    highest = range.highest?.displayName(uiState.noteNaming)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    text = stringResource(R.string.label_best_range),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                val best = uiState.bestRange
                RangeRow(
                    lowest = best?.lowest?.displayName(uiState.noteNaming),
                    semitones = best?.rangeInSemitones ?: 0,
                    highest = best?.highest?.displayName(uiState.noteNaming)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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

        // 清除历史记录是破坏性操作，因此单独放置并要求二次确认
        if (uiState.bestRange != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { showClearRecordDialog = true }) {
                Text(
                    text = stringResource(R.string.btn_clear_record),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showClearRecordDialog) {
        AlertDialog(
            onDismissRequest = { showClearRecordDialog = false },
            title = { Text(text = stringResource(R.string.dialog_clear_record_title)) },
            text = { Text(text = stringResource(R.string.dialog_clear_record_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearBestRange()
                    showClearRecordDialog = false
                }) {
                    Text(
                        text = stringResource(R.string.btn_clear_record_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearRecordDialog = false }) {
                    Text(text = stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

/** 最低音 / 音域 / 最高音 三栏。null 显示占位符。 */
@Composable
private fun RangeRow(lowest: String?, semitones: Int, highest: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RangeCell(label = stringResource(R.string.label_lowest), value = lowest ?: "--")
        RangeCell(
            label = stringResource(R.string.label_range),
            value = stringResource(R.string.unit_semitones, semitones)
        )
        RangeCell(label = stringResource(R.string.label_highest), value = highest ?: "--")
    }
}

@Composable
private fun RangeCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = RangeValueStyle)
    }
}
