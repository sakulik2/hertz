package xyz.sakulik.hertz.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sakulik.hertz.data.ConfigSnapshot
import xyz.sakulik.hertz.data.DataStoreRangeRecordStore
import xyz.sakulik.hertz.data.DataStoreSettingsStore
import xyz.sakulik.hertz.data.InMemoryRangeRecordStore
import xyz.sakulik.hertz.data.InMemorySettingsStore
import xyz.sakulik.hertz.data.Note
import xyz.sakulik.hertz.data.NoteNaming
import xyz.sakulik.hertz.data.PitchRepository
import xyz.sakulik.hertz.data.PitchResult
import xyz.sakulik.hertz.data.PitchSource
import xyz.sakulik.hertz.data.PitchTracker
import xyz.sakulik.hertz.data.RangeRecord
import xyz.sakulik.hertz.data.RangeRecordStore
import xyz.sakulik.hertz.data.SettingsStore
import xyz.sakulik.hertz.data.TunerConfig
import xyz.sakulik.hertz.data.VocalRangeState

data class UiState(
    val currentNote: Note? = null,
    val currentFrequency: Float? = null,
    val centsDeviation: Float = 0f,
    val isListening: Boolean = false,
    /** 本次会话观测到的音域，重置后清空。 */
    val vocalRange: VocalRangeState = VocalRangeState(),
    /** 跨会话保留的历史最佳音域；尚无记录时为 null。 */
    val bestRange: RangeRecord? = null,
    val smoothedCents: Float = 0f,
    val hasError: Boolean = false,
    val noteNaming: NoteNaming = NoteNaming.SHARP
) {
    /** 例如 "A4"；无读数时为 null，由 UI 决定占位符。 */
    val currentNoteLabel: String? get() = currentNote?.displayName(noteNaming)
}

class PitchViewModel(
    private val repository: PitchSource,
    private val settingsStore: SettingsStore,
    private val recordStore: RangeRecordStore,
    /** 与 [repository] 共享的快照，设置变化时由本类写入。 */
    private val configSnapshot: ConfigSnapshot = ConfigSnapshot(),
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val config: TunerConfig get() = configSnapshot.value

    /** 本次会话的音域，阈值较松，读数错了重置一下即可。 */
    private var tracker = PitchTracker(config.streakThreshold)

    /**
     * 永久记录用的独立追踪器，阈值明显更高。会话内读数被一个 YIN 离群值带偏无所谓，
     * 但污染了永久记录会一直留着，所以两者刻意解耦。
     */
    private var recordTracker = PitchTracker(config.recordStreakThreshold)

    private var collectJob: Job? = null

    /** 区分用户主动暂停与生命周期暂停：前者不应被切回前台自动恢复。 */
    private var userManuallyPaused = false

    init {
        viewModelScope.launch {
            settingsStore.config.collect { loaded ->
                val streakChanged = loaded.streakThreshold != config.streakThreshold ||
                    loaded.recordStreakThreshold != config.recordStreakThreshold
                configSnapshot.value = loaded

                // 阈值变了就得换追踪器，否则新阈值要等到下次重置才生效。
                // 追踪器内部累积的候选帧是按旧阈值计数的，所以会话读数一并清空。
                if (streakChanged) {
                    tracker = PitchTracker(loaded.streakThreshold)
                    recordTracker = PitchTracker(loaded.recordStreakThreshold)
                }
                _uiState.update { state ->
                    state.copy(
                        noteNaming = loaded.noteNaming,
                        vocalRange = if (streakChanged) VocalRangeState() else state.vocalRange
                    )
                }
            }
        }
        viewModelScope.launch {
            recordStore.record.collect { record ->
                _uiState.update { it.copy(bestRange = record) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val context = requireNotNull(this[APPLICATION_KEY]) {
                    "PitchViewModel.Factory needs the Application in CreationExtras"
                }.applicationContext
                // Repository 与 ViewModel 共享同一个快照，所以设置改动无需重启采集
                val snapshot = ConfigSnapshot()
                PitchViewModel(
                    repository = PitchRepository { snapshot.value },
                    settingsStore = DataStoreSettingsStore(context),
                    recordStore = DataStoreRangeRecordStore(context),
                    configSnapshot = snapshot
                )
            }
        }

        /**
         * 供测试与预览使用的轻量构造：全部依赖走内存实现，不触碰磁盘或 AudioRecord。
         */
        fun forTesting(
            repository: PitchSource,
            settingsStore: SettingsStore = InMemorySettingsStore(),
            recordStore: RangeRecordStore = InMemoryRangeRecordStore(),
            now: () -> Long = System::currentTimeMillis
        ) = PitchViewModel(
            repository = repository,
            settingsStore = settingsStore,
            recordStore = recordStore,
            now = now
        )
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

    private suspend fun onDetected(result: PitchResult.Detected) {
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
        persistIfRecordExtended(result)
    }

    /** 只有通过更严格的稳定门限后，才考虑扩展永久记录。 */
    private suspend fun persistIfRecordExtended(result: PitchResult.Detected) {
        val confirmed = recordTracker.observe(result.frequencyHz, result.note)
        if (!confirmed.hasRange) return

        val existing = _uiState.value.bestRange
        val updated = if (existing == null) {
            RangeRecord.of(
                lowestFreq = confirmed.lowestFreq,
                highestFreq = confirmed.highestFreq,
                referencePitchHz = config.referencePitchHz,
                nowEpochMillis = now()
            )
        } else {
            existing.extendedBy(
                lowestFreq = confirmed.lowestFreq,
                highestFreq = confirmed.highestFreq,
                referencePitchHz = config.referencePitchHz,
                nowEpochMillis = now()
            )
        } ?: return

        recordStore.save(updated)
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

    /** 只清本次会话的观测值，历史记录不受影响。 */
    fun resetRange() {
        tracker.reset()
        recordTracker.reset()
        _uiState.update {
            it.copy(vocalRange = VocalRangeState(), centsDeviation = 0f, smoothedCents = 0f)
        }
    }

    /** 清除永久记录。破坏性操作，UI 需要二次确认。 */
    fun clearBestRange() {
        viewModelScope.launch { recordStore.clear() }
    }

    override fun onCleared() {
        stopListening()
    }
}
