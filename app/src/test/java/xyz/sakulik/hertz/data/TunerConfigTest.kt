package xyz.sakulik.hertz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TunerConfigTest {

    @Test fun derivesFrameRateFromTheBufferStep() {
        // 默认步长 4096 - 3072 = 1024，即 44100 / 1024 ≈ 43 帧/秒
        assertEquals(43.07f, TunerConfig.Default.framesPerSecond, 0.01f)
        assertEquals(
            86.13f,
            TunerConfig(bufferSize = 4096, bufferOverlap = 3584).framesPerSecond,
            0.01f
        )
    }

    @Test fun demandsALongerStreakForRecordsThanForTheSessionReadout() {
        // 这条不变式是持久化正确性的前提，不只是一个默认值
        val config = TunerConfig.Default
        assert(config.recordStreakThreshold > config.streakThreshold)
    }

    @Test fun rejectsAnInvertedFrequencyWindow() {
        assertThrows(IllegalArgumentException::class.java) {
            TunerConfig(minFrequencyHz = 1100f, maxFrequencyHz = 80f)
        }
    }

    @Test fun rejectsOverlapAtOrAboveBufferSize() {
        // 步长为零会导致帧率无穷大
        assertThrows(IllegalArgumentException::class.java) {
            TunerConfig(bufferSize = 4096, bufferOverlap = 4096)
        }
    }

    @Test fun rejectsOutOfRangeSmoothingAndThresholds() {
        assertThrows(IllegalArgumentException::class.java) { TunerConfig(smoothingFactor = 0f) }
        assertThrows(IllegalArgumentException::class.java) { TunerConfig(smoothingFactor = 1.5f) }
        assertThrows(IllegalArgumentException::class.java) { TunerConfig(streakThreshold = 0) }
        assertThrows(IllegalArgumentException::class.java) { TunerConfig(recordStreakThreshold = 0) }
        assertThrows(IllegalArgumentException::class.java) { TunerConfig(referencePitchHz = 0.0) }
    }

    @Test fun coversCommonOrchestralTuningsInTheCalibrationRange() {
        // 415 为巴洛克音高，442/443 为常见乐团调校
        assert(415.0 in TunerConfig.REFERENCE_PITCH_RANGE)
        assert(440.0 in TunerConfig.REFERENCE_PITCH_RANGE)
        assert(443.0 in TunerConfig.REFERENCE_PITCH_RANGE)
        assert(400.0 !in TunerConfig.REFERENCE_PITCH_RANGE)
    }
}
