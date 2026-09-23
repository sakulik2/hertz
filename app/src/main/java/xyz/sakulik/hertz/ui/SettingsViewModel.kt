package xyz.sakulik.hertz.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.sakulik.hertz.HertzApp
import xyz.sakulik.hertz.data.NoteNaming
import xyz.sakulik.hertz.data.SettingsStore
import xyz.sakulik.hertz.data.TunerConfig

/** 完全不平滑（1.0）可用，但 0 会让读数永远停在初值。 */
private const val MIN_SMOOTHING = 0.05f

/**
 * 设置页状态。直接暴露 [TunerConfig]，因为设置页编辑的就是它本身，
 * 再包一层 UiState 只会制造一份需要同步的副本。
 */
class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    val config: StateFlow<TunerConfig> = settingsStore.config.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TunerConfig.Default
    )

    fun setReferencePitch(hz: Double) = update {
        it.copy(referencePitchHz = hz.coerceIn(TunerConfig.REFERENCE_PITCH_RANGE))
    }

    fun setNoteNaming(naming: NoteNaming) = update { it.copy(noteNaming = naming) }

    fun setConfidenceThreshold(value: Float) = update {
        it.copy(confidenceThreshold = value.coerceIn(0f, 1f))
    }

    fun setSmoothingFactor(value: Float) = update {
        it.copy(smoothingFactor = value.coerceIn(MIN_SMOOTHING, 1f))
    }

    /** 收束到合法区间，避免下界越过上界让 TunerConfig 的 init 抛异常。 */
    fun setFrequencyWindow(minHz: Float, maxHz: Float) = update { current ->
        val low = minHz.coerceIn(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ)
        val high = maxHz.coerceIn(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ)
        if (low >= high) current else current.copy(minFrequencyHz = low, maxFrequencyHz = high)
    }

    fun resetToDefaults() = update { TunerConfig.Default }

    private fun update(transform: (TunerConfig) -> TunerConfig) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // 与 PitchViewModel 共用 AppContainer 里的同一个 store
                val container = requireNotNull(this[APPLICATION_KEY] as? HertzApp) {
                    "SettingsViewModel.Factory needs HertzApp in CreationExtras"
                }.container
                SettingsViewModel(container.settingsStore)
            }
        }

        /** 灵敏度与频率窗口的可调范围，同时用于滑杆取值域。 */
        val CONFIDENCE_RANGE = 0.5f..0.99f
        val SMOOTHING_RANGE = MIN_SMOOTHING..1f
        const val MIN_FREQUENCY_HZ = 40f
        const val MAX_FREQUENCY_HZ = 2000f
    }
}
