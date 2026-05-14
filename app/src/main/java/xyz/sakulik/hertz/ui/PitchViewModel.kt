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
import kotlin.math.log2
import kotlin.math.roundToInt

data class VocalRangeState(
    val lowestFreq: Float? = null,
    val lowestNote: String? = null,
    val highestFreq: Float? = null,
    val highestNote: String? = null,
    val rangeInSemitones: Int = 0
)

data class UiState(
    val currentNote: String? = null,
    val currentOctave: Int? = null,
    val currentFrequency: Float? = null,
    val centsDeviation: Float = 0f,
    val isListening: Boolean = false,
    val vocalRange: VocalRangeState = VocalRangeState(),
    val smoothedCents: Float = 0f
)

class PitchViewModel(
    private val repository: PitchRepository = PitchRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PitchViewModel() }
        }
    }

    fun startListening() {
        if (_uiState.value.isListening) return

        repository.startListening()
        _uiState.update { it.copy(isListening = true) }

        if (collectJob == null || collectJob?.isCompleted == true) {
            collectJob = viewModelScope.launch {
                repository.pitchFlow.collect { result ->
                    if (!_uiState.value.isListening) return@collect

                    when (result) {
                        is PitchResult.Detected -> {
                            _uiState.update { state ->
                                val newCents = result.centsDeviation
                                val smoothed = state.smoothedCents * 0.7f + newCents * 0.3f

                                val currentRange = state.vocalRange
                                val freq = result.frequencyHz
                                val fullNoteName = "${result.noteName}${result.octave}"

                                var newLowestFreq = currentRange.lowestFreq
                                var newLowestNote = currentRange.lowestNote
                                if (newLowestFreq == null || freq < newLowestFreq) {
                                    newLowestFreq = freq
                                    newLowestNote = fullNoteName
                                }

                                var newHighestFreq = currentRange.highestFreq
                                var newHighestNote = currentRange.highestNote
                                if (newHighestFreq == null || freq > newHighestFreq) {
                                    newHighestFreq = freq
                                    newHighestNote = fullNoteName
                                }

                                val lowMidi = (12 * log2(newLowestFreq / 440.0) + 69).roundToInt()
                                val highMidi = (12 * log2(newHighestFreq / 440.0) + 69).roundToInt()
                                val rangeInSemitones = highMidi - lowMidi

                                state.copy(
                                    currentNote = result.noteName,
                                    currentOctave = result.octave,
                                    currentFrequency = result.frequencyHz,
                                    centsDeviation = newCents,
                                    smoothedCents = smoothed,
                                    vocalRange = VocalRangeState(
                                        lowestFreq = newLowestFreq,
                                        lowestNote = newLowestNote,
                                        highestFreq = newHighestFreq,
                                        highestNote = newHighestNote,
                                        rangeInSemitones = rangeInSemitones
                                    )
                                )
                            }
                        }
                        is PitchResult.Silence -> {
                            _uiState.update { state ->
                                state.copy(
                                    currentNote = null,
                                    currentOctave = null,
                                    currentFrequency = null,
                                    centsDeviation = 0f,
                                    smoothedCents = 0f
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun stopListening() {
        repository.stopListening()
        _uiState.update { it.copy(isListening = false) }
    }

    fun resetRange() {
        _uiState.update { it.copy(vocalRange = VocalRangeState(), centsDeviation = 0f, smoothedCents = 0f) }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
