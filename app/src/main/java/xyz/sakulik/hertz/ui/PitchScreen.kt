package xyz.sakulik.hertz.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.sakulik.hertz.R
import xyz.sakulik.hertz.data.PitchError
import xyz.sakulik.hertz.ui.components.IN_TUNE_CENTS
import xyz.sakulik.hertz.ui.components.KeepScreenOn
import xyz.sakulik.hertz.ui.components.NoteDisplay
import xyz.sakulik.hertz.ui.components.RangeCard
import xyz.sakulik.hertz.ui.components.TunerDial
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PitchScreen(
    viewModel: PitchViewModel = viewModel(factory = PitchViewModel.Factory),
    onOpenSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showClearRecordDialog by rememberSaveable { mutableStateOf(false) }

    // 采集时保持屏幕常亮：用户举着手机唱歌，没有触摸事件，屏幕会自己变暗锁屏
    KeepScreenOn(enabled = uiState.isListening)

    // 生命周期感知：遵守用户手动选择，切前台时自动恢复
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resumeListeningFromLifecycle()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopListening(isUserAction = false)
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
            // 放大字号、小屏或横屏时内容会超出一屏，必须可滚动否则底部按钮被裁掉
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        uiState.error?.let { error ->
            MicErrorBanner(
                error = error,
                onRetry = { viewModel.startListening(isUserAction = true) },
                onOpenAppSettings = { context.openAppSettings() }
            )
        }

        TunerDial(
            centsDeviation = uiState.smoothedCents,
            semanticLabel = dialSemanticLabel(uiState)
        )

        Spacer(modifier = Modifier.height(16.dp))

        NoteDisplay(
            noteLabel = uiState.currentNoteLabel,
            frequencyText = uiState.currentFrequency
                ?.let { String.format(Locale.US, "%.1f Hz", it) }
                ?: stringResource(R.string.value_placeholder_hz),
            semanticLabel = noteSemanticLabel(uiState)
        )

        Spacer(modifier = Modifier.height(32.dp))

        val range = uiState.vocalRange
        val best = uiState.bestRange
        RangeCard(
            sessionLowest = range.lowest?.displayName(uiState.noteNaming),
            sessionSemitones = range.rangeInSemitones,
            sessionHighest = range.highest?.displayName(uiState.noteNaming),
            bestLowest = best?.lowest?.displayName(uiState.noteNaming),
            bestSemitones = best?.rangeInSemitones ?: 0,
            bestHighest = best?.highest?.displayName(uiState.noteNaming)
        )

        Spacer(modifier = Modifier.height(24.dp))

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

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = onOpenSettings) {
                Text(text = stringResource(R.string.btn_settings))
            }

            // 清除历史记录是破坏性操作，因此要求二次确认
            if (uiState.bestRange != null) {
                TextButton(onClick = { showClearRecordDialog = true }) {
                    Text(
                        text = stringResource(R.string.btn_clear_record),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showClearRecordDialog) {
        ClearRecordDialog(
            onConfirm = {
                viewModel.clearBestRange()
                showClearRecordDialog = false
            },
            onDismiss = { showClearRecordDialog = false }
        )
    }
}

/**
 * 按故障类型给出对得上的说明与操作：权限被撤销时重试毫无意义，得跳系统设置；
 * 被占用时重试才有希望。
 */
@Composable
private fun MicErrorBanner(
    error: PitchError,
    onRetry: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val messageRes = when (error) {
        PitchError.PermissionRevoked -> R.string.mic_error_permission
        PitchError.DeviceBusy -> R.string.mic_error_busy
        PitchError.InitFailed -> R.string.mic_error_init
        is PitchError.Unknown -> R.string.mic_error_unknown
    }

    Text(
        text = stringResource(R.string.mic_error_title),
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (error == PitchError.PermissionRevoked) {
        OutlinedButton(onClick = onOpenAppSettings) {
            Text(text = stringResource(R.string.btn_open_settings))
        }
    } else if (error.isRetryable) {
        OutlinedButton(onClick = onRetry) {
            Text(text = stringResource(R.string.btn_retry))
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

/** 打开本应用的系统设置页，供用户手动恢复被撤销的麦克风权限。 */
private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
    )
}

@Composable
private fun ClearRecordDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dialog_clear_record_title)) },
        text = { Text(text = stringResource(R.string.dialog_clear_record_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.btn_clear_record_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.btn_cancel))
            }
        }
    )
}

/**
 * 表盘的无障碍播报。表盘是本页的主要信息载体，而 Canvas 对屏幕阅读器完全不可见，
 * 所以这里把指针位置翻译成"偏高/偏低多少音分"。
 */
@Composable
private fun dialSemanticLabel(uiState: UiState): String {
    if (uiState.currentNote == null) return stringResource(R.string.a11y_no_reading)
    val cents = uiState.smoothedCents.roundToInt()
    return when {
        abs(uiState.smoothedCents) < IN_TUNE_CENTS -> stringResource(R.string.a11y_in_tune)
        cents > 0 -> stringResource(R.string.a11y_cents_sharp, cents)
        else -> stringResource(R.string.a11y_cents_flat, -cents)
    }
}

@Composable
private fun noteSemanticLabel(uiState: UiState): String {
    val label = uiState.currentNoteLabel ?: return stringResource(R.string.a11y_no_reading)
    val frequency = uiState.currentFrequency ?: return label
    return stringResource(R.string.a11y_note_and_frequency, label, frequency)
}
