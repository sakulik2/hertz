package xyz.sakulik.hertz.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import xyz.sakulik.hertz.data.InMemorySettingsStore
import xyz.sakulik.hertz.data.NoteNaming
import xyz.sakulik.hertz.data.TunerConfig

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /**
     * config 用 stateIn(WhileSubscribed) 共享，没有订阅者时不会启动上游，
     * value 会一直停在初始值。真机上由 Compose 的 collectAsState 订阅，
     * 测试里必须自己挂一个收集器，否则测的是一个从未启动的流。
     */
    private fun TestScope.viewModelWithActiveState(
        store: InMemorySettingsStore
    ): SettingsViewModel {
        val viewModel = SettingsViewModel(store)
        backgroundScope.launch { viewModel.config.collect { } }
        testScheduler.advanceUntilIdle()
        return viewModel
    }

    @Test fun persistsReferencePitchChanges() = runTest(dispatcher) {
        val store = InMemorySettingsStore()
        val viewModel = viewModelWithActiveState(store)

        viewModel.setReferencePitch(442.0)
        testScheduler.advanceUntilIdle()
        assertEquals(442.0, viewModel.config.value.referencePitchHz, 0.001)
    }

    @Test fun clampsReferencePitchToTheCalibrationRange() = runTest(dispatcher) {
        val viewModel = viewModelWithActiveState(InMemorySettingsStore())

        viewModel.setReferencePitch(1000.0)
        testScheduler.advanceUntilIdle()
        assertEquals(
            TunerConfig.REFERENCE_PITCH_RANGE.endInclusive,
            viewModel.config.value.referencePitchHz,
            0.001
        )

        viewModel.setReferencePitch(100.0)
        testScheduler.advanceUntilIdle()
        assertEquals(
            TunerConfig.REFERENCE_PITCH_RANGE.start,
            viewModel.config.value.referencePitchHz,
            0.001
        )
    }

    @Test fun persistsNoteNamingChanges() = runTest(dispatcher) {
        val viewModel = viewModelWithActiveState(InMemorySettingsStore())

        viewModel.setNoteNaming(NoteNaming.FLAT)
        testScheduler.advanceUntilIdle()
        assertEquals(NoteNaming.FLAT, viewModel.config.value.noteNaming)
    }

    @Test fun rejectsAFrequencyWindowThatWouldInvert() = runTest(dispatcher) {
        val viewModel = viewModelWithActiveState(InMemorySettingsStore())
        val original = viewModel.config.value

        // 下界高于上界会让 TunerConfig 的 init 抛异常，因此应整体拒绝
        viewModel.setFrequencyWindow(minHz = 900f, maxHz = 200f)
        testScheduler.advanceUntilIdle()
        assertEquals(original.minFrequencyHz, viewModel.config.value.minFrequencyHz, 0.01f)
        assertEquals(original.maxFrequencyHz, viewModel.config.value.maxFrequencyHz, 0.01f)
    }

    @Test fun clampsFrequencyWindowToSupportedBounds() = runTest(dispatcher) {
        val viewModel = viewModelWithActiveState(InMemorySettingsStore())

        viewModel.setFrequencyWindow(minHz = 1f, maxHz = 99_000f)
        testScheduler.advanceUntilIdle()
        assertEquals(
            SettingsViewModel.MIN_FREQUENCY_HZ,
            viewModel.config.value.minFrequencyHz,
            0.01f
        )
        assertEquals(
            SettingsViewModel.MAX_FREQUENCY_HZ,
            viewModel.config.value.maxFrequencyHz,
            0.01f
        )
    }

    @Test fun keepsSmoothingAboveZeroSoReadingsStillMove() = runTest(dispatcher) {
        val viewModel = viewModelWithActiveState(InMemorySettingsStore())

        // 平滑系数为 0 会让读数永远停在初值，且会触发 TunerConfig 的 require
        viewModel.setSmoothingFactor(0f)
        testScheduler.advanceUntilIdle()
        assert(viewModel.config.value.smoothingFactor > 0f)
    }

    @Test fun clampsConfidenceToAValidProbability() = runTest(dispatcher) {
        val viewModel = viewModelWithActiveState(InMemorySettingsStore())

        viewModel.setConfidenceThreshold(5f)
        testScheduler.advanceUntilIdle()
        assertEquals(1f, viewModel.config.value.confidenceThreshold, 0.001f)
    }

    @Test fun resetsEveryFieldToDefaults() = runTest(dispatcher) {
        val viewModel = viewModelWithActiveState(
            InMemorySettingsStore(
                TunerConfig(referencePitchHz = 443.0, noteNaming = NoteNaming.FLAT)
            )
        )

        viewModel.resetToDefaults()
        testScheduler.advanceUntilIdle()
        assertEquals(TunerConfig.Default, viewModel.config.value)
    }

    @Test fun sharesStoredSettingsWithOtherReaders() = runTest(dispatcher) {
        // 设置页与调音页共用同一个 store，这是改设置能立即作用到音高检测的前提
        val store = InMemorySettingsStore()
        val viewModel = viewModelWithActiveState(store)

        viewModel.setReferencePitch(441.0)
        testScheduler.advanceUntilIdle()

        // 直接读 store 而不是 ViewModel，确认改动确实落到了共享的那一份上
        assertEquals(441.0, store.config.first().referencePitchHz, 0.001)
    }
}
