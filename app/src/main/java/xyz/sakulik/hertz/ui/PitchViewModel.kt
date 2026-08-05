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

/** 极值候选人：用于连续帧计数防抖 */
private data class ExtremeCandidate(
    val midiNote: Int,
    val freq: Float,
    val noteName: String,
    val streak: Int
)

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
    val smoothedCents: Float = 0f,
    val hasError: Boolean = false
)

class PitchViewModel(
    private val repository: PitchRepository = PitchRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    // 极值防抖候选状态
    private var lowestCandidate: ExtremeCandidate? = null
    private var highestCandidate: ExtremeCandidate? = null

    // 追踪用户显式手动的暂停操作
    private var userManuallyPaused: Boolean = false

    companion object {
        /** 连续出现 N 帧后才修改音域边界。帧间隔 ≈ 23ms，3 帧 ≈ 70ms */
        private const val EXTREMUM_STREAK_THRESHOLD = 3

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PitchViewModel() }
        }
    }

    fun startListening(isUserAction: Boolean = false) {
        if (isUserAction) {
            userManuallyPaused = false
        }
        if (_uiState.value.isListening) return

        repository.startListening()
        _uiState.update { it.copy(isListening = true, hasError = false) }

        if (collectJob == null || collectJob?.isCompleted == true) {
            collectJob = viewModelScope.launch {
                repository.pitchFlow.collect { result ->
                    if (!_uiState.value.isListening) return@collect

                    when (result) {
                        is PitchResult.Detected -> {
                            val freq = result.frequencyHz
                            val fullNoteName = "${result.noteName}${result.octave}"
                            val currentMidi = (12 * log2(freq.toDouble() / 440.0) + 69).roundToInt()

                            _uiState.update { state ->
                                val newCents = result.centsDeviation
                                val smoothed = state.smoothedCents * 0.7f + newCents * 0.3f
                                val currentRange = state.vocalRange

                                // --- 最低音防抖 ---
                                var newLowestFreq = currentRange.lowestFreq
                                var newLowestNote = currentRange.lowestNote
                                if (currentRange.lowestFreq == null || freq < currentRange.lowestFreq) {
                                    val c = lowestCandidate
                                    when {
                                        c != null && c.midiNote == currentMidi -> {
                                            val updatedFreq = minOf(c.freq, freq)
                                            val newStreak = c.streak + 1
                                            lowestCandidate = c.copy(freq = updatedFreq, streak = newStreak)
                                            if (newStreak >= EXTREMUM_STREAK_THRESHOLD) {
                                                newLowestFreq = updatedFreq
                                                newLowestNote = c.noteName
                                            }
                                        }
                                        c == null || freq < c.freq -> {
                                            lowestCandidate = ExtremeCandidate(currentMidi, freq, fullNoteName, 1)
                                        }
                                    }
                                } else {
                                    lowestCandidate = null
                                }

                                // --- 最高音防抖 ---
                                var newHighestFreq = currentRange.highestFreq
                                var newHighestNote = currentRange.highestNote
                                if (currentRange.highestFreq == null || freq > currentRange.highestFreq) {
                                    val c = highestCandidate
                                    when {
                                        c != null && c.midiNote == currentMidi -> {
                                            val updatedFreq = maxOf(c.freq, freq)
                                            val newStreak = c.streak + 1
                                            highestCandidate = c.copy(freq = updatedFreq, streak = newStreak)
                                            if (newStreak >= EXTREMUM_STREAK_THRESHOLD) {
                                                newHighestFreq = updatedFreq
                                                newHighestNote = c.noteName
                                            }
                                        }
                                        c == null || freq > c.freq -> {
                                            highestCandidate = ExtremeCandidate(currentMidi, freq, fullNoteName, 1)
                                        }
                                    }
                                } else {
                                    highestCandidate = null
                                }

                                val rangeInSemitones = if (newLowestFreq != null && newHighestFreq != null) {
                                    val lowMidi = (12 * log2(newLowestFreq.toDouble() / 440.0) + 69).roundToInt()
                                    val highMidi = (12 * log2(newHighestFreq.toDouble() / 440.0) + 69).roundToInt()
                                    maxOf(0, highMidi - lowMidi)
                                } else 0

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
                        is PitchResult.Error -> {
                            stopListening(isUserAction = false)
                            _uiState.update { state ->
                                state.copy(hasError = true)
                            }
                        }
                    }
                }
            }
        }
    }

    fun resumeListeningFromLifecycle() {
        if (!userManuallyPaused) {
            startListening(isUserAction = false)
        }
    }

    fun stopListening(isUserAction: Boolean = false) {
        if (isUserAction) {
            userManuallyPaused = true
        }
        repository.stopListening()
        collectJob?.cancel()
        collectJob = null
        _uiState.update { it.copy(isListening = false) }
    }

    fun resetRange() {
        lowestCandidate = null
        highestCandidate = null
        _uiState.update { it.copy(vocalRange = VocalRangeState(), centsDeviation = 0f, smoothedCents = 0f) }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening(isUserAction = false)
    }
}
