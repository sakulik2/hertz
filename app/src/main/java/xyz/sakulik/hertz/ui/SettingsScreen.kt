package xyz.sakulik.hertz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.sakulik.hertz.R
import xyz.sakulik.hertz.data.NoteNaming
import xyz.sakulik.hertz.data.TunerConfig
import java.util.Locale

/**
 * 调音器设置。每一项改动立即写入 DataStore 并作用于下一帧音高，
 * 因此没有"保存"按钮 —— 用户能直接听到/看到效果。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.title_settings),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        ReferencePitchSetting(
            referencePitchHz = config.referencePitchHz,
            onChange = viewModel::setReferencePitch
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

        NoteNamingSetting(
            naming = config.noteNaming,
            onChange = viewModel::setNoteNaming
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

        SliderSetting(
            title = stringResource(R.string.setting_sensitivity),
            description = stringResource(R.string.setting_sensitivity_desc),
            valueText = String.format(Locale.US, "%.2f", config.confidenceThreshold),
            value = config.confidenceThreshold,
            valueRange = SettingsViewModel.CONFIDENCE_RANGE,
            onChange = viewModel::setConfidenceThreshold
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

        SliderSetting(
            title = stringResource(R.string.setting_smoothing),
            description = stringResource(R.string.setting_smoothing_desc),
            valueText = String.format(Locale.US, "%.2f", config.smoothingFactor),
            value = config.smoothingFactor,
            valueRange = SettingsViewModel.SMOOTHING_RANGE,
            onChange = viewModel::setSmoothingFactor
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

        FrequencyWindowSetting(
            minHz = config.minFrequencyHz,
            maxHz = config.maxFrequencyHz,
            onChange = viewModel::setFrequencyWindow
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::resetToDefaults,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.btn_reset_defaults))
            }
            Button(onClick = onNavigateBack, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.btn_done))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ReferencePitchSetting(referencePitchHz: Double, onChange: (Double) -> Unit) {
    val range = TunerConfig.REFERENCE_PITCH_RANGE
    SettingHeader(
        title = stringResource(R.string.setting_reference_pitch),
        description = stringResource(R.string.setting_reference_pitch_desc),
        valueText = String.format(Locale.US, "%.0f Hz", referencePitchHz)
    )
    Slider(
        value = referencePitchHz.toFloat(),
        onValueChange = { onChange(it.toDouble()) },
        valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
        // 1 Hz 一档：校准要的是精确值，连续滑动反而难对准
        steps = (range.endInclusive - range.start).toInt() - 1,
        modifier = Modifier.semantics {
            contentDescription = "%.0f Hz".format(Locale.US, referencePitchHz)
        }
    )
}

@Composable
private fun NoteNamingSetting(naming: NoteNaming, onChange: (NoteNaming) -> Unit) {
    SettingHeader(
        title = stringResource(R.string.setting_note_naming),
        description = stringResource(R.string.setting_note_naming_desc)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = naming == NoteNaming.SHARP,
            onClick = { onChange(NoteNaming.SHARP) },
            label = { Text(text = stringResource(R.string.naming_sharp)) }
        )
        FilterChip(
            selected = naming == NoteNaming.FLAT,
            onClick = { onChange(NoteNaming.FLAT) },
            label = { Text(text = stringResource(R.string.naming_flat)) }
        )
    }
}

@Composable
private fun FrequencyWindowSetting(minHz: Float, maxHz: Float, onChange: (Float, Float) -> Unit) {
    SettingHeader(
        title = stringResource(R.string.setting_frequency_window),
        description = stringResource(R.string.setting_frequency_window_desc),
        valueText = String.format(Locale.US, "%.0f - %.0f Hz", minHz, maxHz)
    )
    Text(
        text = stringResource(R.string.label_min_frequency),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Slider(
        value = minHz,
        // 上界留出 1 Hz 余量，避免下界推到与上界相等
        onValueChange = { onChange(it, maxHz) },
        valueRange = SettingsViewModel.MIN_FREQUENCY_HZ..(maxHz - 1f)
    )
    Text(
        text = stringResource(R.string.label_max_frequency),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Slider(
        value = maxHz,
        onValueChange = { onChange(minHz, it) },
        valueRange = (minHz + 1f)..SettingsViewModel.MAX_FREQUENCY_HZ
    )
}

@Composable
private fun SliderSetting(
    title: String,
    description: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    SettingHeader(title = title, description = description, valueText = valueText)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = valueRange,
        modifier = Modifier.semantics { contentDescription = "$title: $valueText" }
    )
}

@Composable
private fun SettingHeader(title: String, description: String, valueText: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (valueText != null) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // 说明文字已由标题和数值涵盖，单独播报只会拖长 TalkBack 的朗读
        modifier = Modifier.clearAndSetSemantics { }
    )
}
