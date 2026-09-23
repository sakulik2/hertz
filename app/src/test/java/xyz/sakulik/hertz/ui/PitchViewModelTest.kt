package xyz.sakulik.hertz.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.sakulik.hertz.data.InMemoryRangeRecordStore
import xyz.sakulik.hertz.data.InMemorySettingsStore
import xyz.sakulik.hertz.data.Note
import xyz.sakulik.hertz.data.NoteNaming
import xyz.sakulik.hertz.data.PitchError
import xyz.sakulik.hertz.data.PitchResult
import xyz.sakulik.hertz.data.PitchSource
import xyz.sakulik.hertz.data.RangeRecord
import xyz.sakulik.hertz.data.TunerConfig

@OptIn(ExperimentalCoroutinesApi::class)
class PitchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Replays nothing; tests push frames explicitly to control ordering. */
    private class FakePitchSource : PitchSource {
        val emissions = MutableSharedFlow<PitchResult>(extraBufferCapacity = 16)
        override val pitchFlow: Flow<PitchResult> = emissions
        var startCount = 0
        var stopCount = 0
        override fun startListening() { startCount++ }
        override fun stopListening() { stopCount++ }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun smoothsCentsTowardTheLatestReading() = runTest(dispatcher) {
        val source = FakePitchSource()
        val viewModel = PitchViewModel.forTesting(source)
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        source.emissions.emit(detected(centsDeviation = 50f))
        testScheduler.advanceUntilIdle()
        // 首帧：0 * 0.7 + 50 * 0.3
        assertEquals(15f, viewModel.uiState.value.smoothedCents, 0.01f)
        assertEquals(50f, viewModel.uiState.value.centsDeviation, 0.01f)

        source.emissions.emit(detected(centsDeviation = 50f))
        testScheduler.advanceUntilIdle()
        // 第二帧：15 * 0.7 + 50 * 0.3，平滑值向真实读数收敛但不会越过它
        assertEquals(25.5f, viewModel.uiState.value.smoothedCents, 0.01f)
        assertTrue(viewModel.uiState.value.smoothedCents < 50f)
    }

    @Test fun silenceClearsCurrentNoteButKeepsRange() = runTest(dispatcher) {
        val source = FakePitchSource()
        val viewModel = PitchViewModel.forTesting(source)
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        repeat(3) { source.emissions.emit(detected()) }
        testScheduler.advanceUntilIdle()
        val recordedRange = viewModel.uiState.value.vocalRange
        assertEquals(Note(69), recordedRange.lowest)

        source.emissions.emit(PitchResult.Silence)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.currentNote)
        assertEquals(0f, viewModel.uiState.value.smoothedCents, 0.01f)
        assertEquals(recordedRange, viewModel.uiState.value.vocalRange)
    }

    @Test fun lifecycleResumeDoesNotRestartAfterUserPause() = runTest(dispatcher) {
        val source = FakePitchSource()
        val viewModel = PitchViewModel.forTesting(source)
        viewModel.startListening(isUserAction = true)
        testScheduler.advanceUntilIdle()
        assertEquals(1, source.startCount)

        viewModel.stopListening(isUserAction = true)
        viewModel.resumeListeningFromLifecycle()
        testScheduler.advanceUntilIdle()

        assertEquals(1, source.startCount)
        assertFalse(viewModel.uiState.value.isListening)
    }

    @Test fun lifecycleResumeRestartsAfterLifecyclePause() = runTest(dispatcher) {
        val source = FakePitchSource()
        val viewModel = PitchViewModel.forTesting(source)
        viewModel.startListening(isUserAction = true)
        viewModel.stopListening(isUserAction = false)
        viewModel.resumeListeningFromLifecycle()
        testScheduler.advanceUntilIdle()

        assertEquals(2, source.startCount)
        assertTrue(viewModel.uiState.value.isListening)
    }

    @Test fun errorStopsCaptureAndFlagsUi() = runTest(dispatcher) {
        val source = FakePitchSource()
        val viewModel = PitchViewModel.forTesting(source)
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        source.emissions.emit(PitchResult.Error(PitchError.DeviceBusy))
        testScheduler.advanceUntilIdle()

        assertEquals(PitchError.DeviceBusy, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isListening)
        assertEquals(1, source.stopCount)
    }

    @Test fun surfacesTheSpecificErrorKindToTheUi() = runTest(dispatcher) {
        // 四种故障的处置方式不同，UI 必须能分辨，而不是只知道"出错了"
        val cases = listOf(
            PitchError.PermissionRevoked,
            PitchError.DeviceBusy,
            PitchError.InitFailed,
            PitchError.Unknown(IllegalArgumentException("boom"))
        )
        for (expected in cases) {
            val source = FakePitchSource()
            val viewModel = PitchViewModel.forTesting(source)
            viewModel.startListening()
            testScheduler.advanceUntilIdle()

            source.emissions.emit(PitchResult.Error(expected))
            testScheduler.advanceUntilIdle()
            assertEquals(expected, viewModel.uiState.value.error)
        }
    }

    @Test fun clearsThePreviousErrorWhenRetrying() = runTest(dispatcher) {
        val source = FakePitchSource()
        val viewModel = PitchViewModel.forTesting(source)
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        source.emissions.emit(PitchResult.Error(PitchError.DeviceBusy))
        testScheduler.advanceUntilIdle()
        assertEquals(PitchError.DeviceBusy, viewModel.uiState.value.error)

        viewModel.startListening(isUserAction = true)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.isListening)
    }

    @Test fun usesTheConfiguredSmoothingFactor() = runTest(dispatcher) {
        val source = FakePitchSource()
        // 权重 1.0 意味着不平滑，读数直接透传
        val viewModel = PitchViewModel.forTesting(source, InMemorySettingsStore(TunerConfig(smoothingFactor = 1f)))
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        source.emissions.emit(detected(centsDeviation = 40f))
        testScheduler.advanceUntilIdle()
        assertEquals(40f, viewModel.uiState.value.smoothedCents, 0.01f)
    }

    @Test fun labelsNotesUsingTheConfiguredNaming() = runTest(dispatcher) {
        val source = FakePitchSource()
        val viewModel = PitchViewModel.forTesting(source, InMemorySettingsStore(TunerConfig(noteNaming = NoteNaming.FLAT)))
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        source.emissions.emit(detected(frequencyHz = 277.2f, note = Note(61)))
        testScheduler.advanceUntilIdle()
        assertEquals("Db4", viewModel.uiState.value.currentNoteLabel)
    }

    @Test fun requiresTheConfiguredStreakBeforeRecordingRange() = runTest(dispatcher) {
        val source = FakePitchSource()
        val viewModel = PitchViewModel.forTesting(source, InMemorySettingsStore(TunerConfig(streakThreshold = 5)))
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        repeat(4) { source.emissions.emit(detected()) }
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.vocalRange.lowest)

        source.emissions.emit(detected())
        testScheduler.advanceUntilIdle()
        assertEquals(Note(69), viewModel.uiState.value.vocalRange.lowest)
    }

    @Test fun resetRangeClearsRangeAndCents() = runTest(dispatcher) {
        val source = FakePitchSource()
        val viewModel = PitchViewModel.forTesting(source)
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        repeat(3) { source.emissions.emit(detected()) }
        testScheduler.advanceUntilIdle()
        assertEquals(Note(69), viewModel.uiState.value.vocalRange.lowest)

        viewModel.resetRange()
        assertNull(viewModel.uiState.value.vocalRange.lowest)
        assertEquals(0, viewModel.uiState.value.vocalRange.rangeInSemitones)
        assertEquals(0f, viewModel.uiState.value.smoothedCents, 0.01f)
    }

    @Test fun requiresALongerStreakBeforePersistingARecord() = runTest(dispatcher) {
        val source = FakePitchSource()
        val records = InMemoryRangeRecordStore()
        // 会话内 2 帧即可，永久记录要 6 帧
        val viewModel = PitchViewModel.forTesting(
            source,
            InMemorySettingsStore(TunerConfig(streakThreshold = 2, recordStreakThreshold = 6)),
            records
        )
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        // 会话读数早已提交，但永久记录还不该落盘
        repeat(5) { source.emissions.emit(detected()) }
        testScheduler.advanceUntilIdle()
        assertEquals(Note(69), viewModel.uiState.value.vocalRange.lowest)
        assertNull(viewModel.uiState.value.bestRange)
        assertEquals(0, records.saveCount)

        source.emissions.emit(detected())
        testScheduler.advanceUntilIdle()
        assertEquals(Note(69), viewModel.uiState.value.bestRange?.lowest)
        assertEquals(1, records.saveCount)
    }

    @Test fun doesNotPersistATransientOutlier() = runTest(dispatcher) {
        val source = FakePitchSource()
        val records = InMemoryRangeRecordStore()
        val viewModel = PitchViewModel.forTesting(
            source,
            InMemorySettingsStore(TunerConfig(streakThreshold = 2, recordStreakThreshold = 5)),
            records
        )
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        // 稳定的 A4 建立记录
        repeat(5) { source.emissions.emit(detected()) }
        testScheduler.advanceUntilIdle()
        val baseline = viewModel.uiState.value.bestRange
        assertEquals(Note(69), baseline?.highest)

        // 一帧离群的极高音，不应污染永久记录
        source.emissions.emit(detected(frequencyHz = 1046.5f, note = Note(84)))
        testScheduler.advanceUntilIdle()
        assertEquals(Note(69), viewModel.uiState.value.bestRange?.highest)
    }

    @Test fun extendsAnExistingRecordOnlyWhenTheRangeWidens() = runTest(dispatcher) {
        val source = FakePitchSource()
        val existing = RangeRecord(
            lowestFreq = 220f,
            highestFreq = 440f,
            referencePitchHz = 440.0,
            updatedAtEpochMillis = 1_000L
        )
        val records = InMemoryRangeRecordStore(existing)
        val viewModel = PitchViewModel.forTesting(
            source,
            InMemorySettingsStore(TunerConfig(streakThreshold = 2, recordStreakThreshold = 2)),
            records
        )
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        // 区间内的音高，不构成更宽的音域，不该写盘
        repeat(4) { source.emissions.emit(detected(frequencyHz = 330f, note = Note(64))) }
        testScheduler.advanceUntilIdle()
        assertEquals(0, records.saveCount)
        assertEquals(existing, viewModel.uiState.value.bestRange)

        // 更低的音，应扩展下界并保留原上界
        repeat(4) { source.emissions.emit(detected(frequencyHz = 110f, note = Note(45))) }
        testScheduler.advanceUntilIdle()
        val updated = viewModel.uiState.value.bestRange!!
        assertEquals(110f, updated.lowestFreq, 0.01f)
        assertEquals(440f, updated.highestFreq, 0.01f)
    }

    @Test fun exposesAPersistedRecordBeforeAnyFrameArrives() = runTest(dispatcher) {
        val stored = RangeRecord(
            lowestFreq = 82.4f,
            highestFreq = 440f,
            referencePitchHz = 440.0,
            updatedAtEpochMillis = 42L
        )
        val viewModel = PitchViewModel.forTesting(
            FakePitchSource(),
            recordStore = InMemoryRangeRecordStore(stored)
        )
        testScheduler.advanceUntilIdle()

        // 历史记录应在任何音频到达前就可见，这正是持久化的意义
        assertEquals(stored, viewModel.uiState.value.bestRange)
        assertEquals("E2", viewModel.uiState.value.bestRange?.lowest?.displayName())
    }

    @Test fun resetRangeKeepsThePersistedRecord() = runTest(dispatcher) {
        val source = FakePitchSource()
        val records = InMemoryRangeRecordStore()
        val viewModel = PitchViewModel.forTesting(
            source,
            InMemorySettingsStore(TunerConfig(streakThreshold = 2, recordStreakThreshold = 2)),
            records
        )
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        repeat(3) { source.emissions.emit(detected()) }
        testScheduler.advanceUntilIdle()
        assertEquals(Note(69), viewModel.uiState.value.bestRange?.lowest)

        viewModel.resetRange()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.vocalRange.lowest)
        assertEquals(Note(69), viewModel.uiState.value.bestRange?.lowest)
    }

    @Test fun clearBestRangeRemovesThePersistedRecord() = runTest(dispatcher) {
        val stored = RangeRecord(220f, 440f, 440.0, 1L)
        val viewModel = PitchViewModel.forTesting(
            FakePitchSource(),
            recordStore = InMemoryRangeRecordStore(stored)
        )
        testScheduler.advanceUntilIdle()
        assertEquals(stored, viewModel.uiState.value.bestRange)

        viewModel.clearBestRange()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.bestRange)
    }

    @Test fun stampsRecordsWithTheConfiguredReferencePitch() = runTest(dispatcher) {
        val source = FakePitchSource()
        val viewModel = PitchViewModel.forTesting(
            source,
            InMemorySettingsStore(
                TunerConfig(referencePitchHz = 442.0, streakThreshold = 2, recordStreakThreshold = 2)
            ),
            now = { 12_345L }
        )
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        repeat(3) { source.emissions.emit(detected()) }
        testScheduler.advanceUntilIdle()

        val record = viewModel.uiState.value.bestRange!!
        assertEquals(442.0, record.referencePitchHz, 0.001)
        assertEquals(12_345L, record.updatedAtEpochMillis)
    }

    private fun detected(
        centsDeviation: Float = 0f,
        frequencyHz: Float = 440f,
        note: Note = Note(69)
    ) = PitchResult.Detected(
        frequencyHz = frequencyHz,
        note = note,
        centsDeviation = centsDeviation,
        probability = 0.95f
    )
}
