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
import xyz.sakulik.hertz.data.PitchRepository
import xyz.sakulik.hertz.data.PitchResult
import xyz.sakulik.hertz.data.PitchSource
import xyz.sakulik.hertz.data.PitchTracker
import xyz.sakulik.hertz.data.VocalRangeState

data class UiState(val currentNote: String? = null, val currentOctave: Int? = null, val currentFrequency: Float? = null, val centsDeviation: Float = 0f, val isListening: Boolean = false, val vocalRange: VocalRangeState = VocalRangeState(), val smoothedCents: Float = 0f, val hasError: Boolean = false)

class PitchViewModel(private val repository: PitchSource = PitchRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val tracker = PitchTracker()
    private var collectJob: Job? = null
    private var userManuallyPaused = false

    companion object { val Factory: ViewModelProvider.Factory = viewModelFactory { initializer { PitchViewModel() } } }

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
                    is PitchResult.Detected -> {
                        val range = tracker.observe(result.frequencyHz, result.noteName, result.octave)
                        _uiState.update { state ->
                            val smoothed = state.smoothedCents * 0.7f + result.centsDeviation * 0.3f
                            state.copy(currentNote = result.noteName, currentOctave = result.octave, currentFrequency = result.frequencyHz, centsDeviation = result.centsDeviation, smoothedCents = smoothed, vocalRange = range)
                        }
                    }
                    PitchResult.Silence -> _uiState.update { it.copy(currentNote = null, currentOctave = null, currentFrequency = null, centsDeviation = 0f, smoothedCents = 0f) }
                    is PitchResult.Error -> { stopListening(); _uiState.update { it.copy(hasError = true) } }
                }
            }
        }
    }

    fun resumeListeningFromLifecycle() { if (!userManuallyPaused) startListening() }
    fun stopListening(isUserAction: Boolean = false) { if (isUserAction) userManuallyPaused = true; repository.stopListening(); collectJob?.cancel(); collectJob = null; _uiState.update { it.copy(isListening = false) } }
    fun resetRange() { tracker.reset(); _uiState.update { it.copy(vocalRange = VocalRangeState(), centsDeviation = 0f, smoothedCents = 0f) } }
    override fun onCleared() { stopListening() }
}
