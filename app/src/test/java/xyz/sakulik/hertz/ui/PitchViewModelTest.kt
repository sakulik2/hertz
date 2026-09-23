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
import xyz.sakulik.hertz.data.Note
import xyz.sakulik.hertz.data.NoteNaming
import xyz.sakulik.hertz.data.PitchResult
import xyz.sakulik.hertz.data.PitchSource
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
        val viewModel = PitchViewModel(source)
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
        val viewModel = PitchViewModel(source)
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
        val viewModel = PitchViewModel(source)
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
        val viewModel = PitchViewModel(source)
        viewModel.startListening(isUserAction = true)
        viewModel.stopListening(isUserAction = false)
        viewModel.resumeListeningFromLifecycle()
        testScheduler.advanceUntilIdle()

        assertEquals(2, source.startCount)
        assertTrue(viewModel.uiState.value.isListening)
    }

    @Test fun errorStopsCaptureAndFlagsUi() = runTest(dispatcher) {
        val source = FakePitchSource()
        val viewModel = PitchViewModel(source)
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        source.emissions.emit(PitchResult.Error(IllegalStateException("mic gone")))
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasError)
        assertFalse(viewModel.uiState.value.isListening)
        assertEquals(1, source.stopCount)
    }

    @Test fun usesTheConfiguredSmoothingFactor() = runTest(dispatcher) {
        val source = FakePitchSource()
        // 权重 1.0 意味着不平滑，读数直接透传
        val viewModel = PitchViewModel(source, TunerConfig(smoothingFactor = 1f))
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        source.emissions.emit(detected(centsDeviation = 40f))
        testScheduler.advanceUntilIdle()
        assertEquals(40f, viewModel.uiState.value.smoothedCents, 0.01f)
    }

    @Test fun labelsNotesUsingTheConfiguredNaming() = runTest(dispatcher) {
        val source = FakePitchSource()
        val viewModel = PitchViewModel(source, TunerConfig(noteNaming = NoteNaming.FLAT))
        viewModel.startListening()
        testScheduler.advanceUntilIdle()

        source.emissions.emit(detected(frequencyHz = 277.2f, note = Note(61)))
        testScheduler.advanceUntilIdle()
        assertEquals("Db4", viewModel.uiState.value.currentNoteLabel)
    }

    @Test fun requiresTheConfiguredStreakBeforeRecordingRange() = runTest(dispatcher) {
        val source = FakePitchSource()
        val viewModel = PitchViewModel(source, TunerConfig(streakThreshold = 5))
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
        val viewModel = PitchViewModel(source)
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
