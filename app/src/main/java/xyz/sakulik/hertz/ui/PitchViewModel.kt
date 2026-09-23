package xyz.sakulik.hertz.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sakulik.hertz.data.Note
import xyz.sakulik.hertz.data.NoteNaming
import xyz.sakulik.hertz.data.PitchRepository
import xyz.sakulik.hertz.data.PitchResult
import xyz.sakulik.hertz.data.PitchSource
import xyz.sakulik.hertz.data.PitchTracker
import xyz.sakulik.hertz.data.TunerConfig
import xyz.sakulik.hertz.data.VocalRangeState

data class UiState(
    val currentNote: Note? = null,
    val currentFrequency: Float? = null,
    val centsDeviation: Float = 0f,
    val isListening: Boolean = false,
    val vocalRange: VocalRangeState = VocalRangeState(),
    val smoothedCents: Float = 0f,
    val hasError: Boolean = false,
    val noteNaming: NoteNaming = NoteNaming.SHARP
) {
    /** 例如 "A4"；无读数时为 null，由 UI 决定占位符。 */
    val currentNoteLabel: String? get() = currentNote?.displayName(noteNaming)
}

class PitchViewModel(
    private val repository: PitchSource = PitchRepository(),
    private val config: TunerConfig = TunerConfig.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState(noteNaming = config.noteNaming))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val tracker = PitchTracker(config.streakThreshold)
    private var collectJob: Job? = null

    /** 区分用户主动暂停与生命周期暂停：前者不应被切回前台自动恢复。 */
    private var userManuallyPaused = false

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PitchViewModel() }
        }
    }

    fun startListening(isUserAction: Boolean = false) {
        if (isUserAction) userManuallyPaused = false
        if (_uiState.value.isListening) return

        repository.startListening()
        _uiState.update { it.copy(isListening = true, hasError = false) }

        if (collectJob?.isActive == true) return
        collectJob = viewModelScope.launch {
            repository.pitchFlow.collect { result ->
                if (!_uiState.value.isListening) return@collect
                when (result) {
                    is PitchResult.Detected -> onDetected(result)
                    PitchResult.Silence -> onSilence()
                    is PitchResult.Error -> onError()
                }
            }
        }
    }

    private fun onDetected(result: PitchResult.Detected) {
        val range = tracker.observe(result.frequencyHz, result.note)
        _uiState.update { state ->
            state.copy(
                currentNote = result.note,
                currentFrequency = result.frequencyHz,
                centsDeviation = result.centsDeviation,
                smoothedCents = smooth(state.smoothedCents, result.centsDeviation),
                vocalRange = range
            )
        }
    }

    /** 指数平滑，抑制表盘指针抖动。音域统计用的是原始读数，不受此影响。 */
    private fun smooth(previous: Float, next: Float): Float =
        previous * (1f - config.smoothingFactor) + next * config.smoothingFactor

    /** 清当前读数，但保留已测得的音域。 */
    private fun onSilence() = _uiState.update {
        it.copy(
            currentNote = null,
            currentFrequency = null,
            centsDeviation = 0f,
            smoothedCents = 0f
        )
    }

    private fun onError() {
        stopListening()
        _uiState.update { it.copy(hasError = true) }
    }

    fun resumeListeningFromLifecycle() {
        if (!userManuallyPaused) startListening()
    }

    fun stopListening(isUserAction: Boolean = false) {
        if (isUserAction) userManuallyPaused = true
        repository.stopListening()
        collectJob?.cancel()
        collectJob = null
        _uiState.update { it.copy(isListening = false) }
    }

    fun resetRange() {
        tracker.reset()
        _uiState.update {
            it.copy(vocalRange = VocalRangeState(), centsDeviation = 0f, smoothedCents = 0f)
        }
    }

    override fun onCleared() {
        stopListening()
    }
}
