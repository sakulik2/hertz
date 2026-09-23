package xyz.sakulik.hertz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchTrackerTest {

    private val a3 = Note(57)   // 220 Hz
    private val a4 = Note(69)   // 440 Hz
    private val c4 = Note(60)

    @Test fun requiresStableFramesBeforeRecordingExtremes() {
        val tracker = PitchTracker(streakThreshold = 3)
        tracker.observe(220f, a3)
        assertNull(tracker.range.lowest)
        tracker.observe(220f, a3)
        assertNull(tracker.range.lowest)

        val range = tracker.observe(220f, a3)
        assertEquals(a3, range.lowest)
        assertEquals(a3, range.highest)
        assertEquals(0, range.rangeInSemitones)
    }

    @Test fun discardsCandidateWhenTheStreakIsBroken() {
        val tracker = PitchTracker(streakThreshold = 3)
        // 先立一个基准极值
        repeat(3) { tracker.observe(440f, a4) }
        assertEquals(a4, tracker.range.lowest)

        // 两帧偏低的候选，尚未达到阈值
        tracker.observe(220f, a3)
        tracker.observe(220f, a3)
        assertEquals(a4, tracker.range.lowest)

        // 插入一帧不更低的读数，应当打断这串计数
        tracker.observe(440f, a4)
        assertEquals(a4, tracker.range.lowest)

        // 再来两帧还不够，因为计数已归零
        tracker.observe(220f, a3)
        tracker.observe(220f, a3)
        assertEquals(a4, tracker.range.lowest)

        // 第三帧才提交
        tracker.observe(220f, a3)
        assertEquals(a3, tracker.range.lowest)
    }

    @Test fun resetsStreakWhenTheCandidateNoteChanges() {
        val tracker = PitchTracker(streakThreshold = 3)
        repeat(3) { tracker.observe(440f, a4) }

        // 两个不同的偏低候选交替出现，谁都不该达到阈值
        tracker.observe(220f, a3)
        tracker.observe(261.6f, c4)
        tracker.observe(220f, a3)
        assertEquals(a4, tracker.range.lowest)
    }

    @Test fun tracksLowAndHighIndependently() {
        val tracker = PitchTracker(streakThreshold = 2)
        repeat(2) { tracker.observe(261.6f, c4) }
        assertEquals(c4, tracker.range.lowest)
        assertEquals(c4, tracker.range.highest)

        // 升高：只动最高音
        repeat(2) { tracker.observe(440f, a4) }
        assertEquals(c4, tracker.range.lowest)
        assertEquals(a4, tracker.range.highest)
        assertEquals(9, tracker.range.rangeInSemitones)

        // 降低：只动最低音
        repeat(2) { tracker.observe(220f, a3) }
        assertEquals(a3, tracker.range.lowest)
        assertEquals(a4, tracker.range.highest)
        assertEquals(12, tracker.range.rangeInSemitones)
    }

    @Test fun keepsTheMostExtremeFrequencyWithinOneNote() {
        val tracker = PitchTracker(streakThreshold = 3)
        // 同一个音的三帧，频率略有出入；最低音应取其中最低的那次
        tracker.observe(220f, a3)
        tracker.observe(219.2f, a3)
        val range = tracker.observe(219.8f, a3)
        assertEquals(219.2f, range.lowestFreq!!, 0.01f)
        assertEquals(220f, range.highestFreq!!, 0.01f)
    }

    @Test fun reportsWhetherARangeHasBeenObserved() {
        val tracker = PitchTracker(streakThreshold = 2)
        assertFalse(tracker.range.hasRange)
        repeat(2) { tracker.observe(440f, a4) }
        assertTrue(tracker.range.hasRange)
    }

    @Test fun resetClearsRangeAndPendingCandidates() {
        val tracker = PitchTracker(streakThreshold = 3)
        repeat(3) { tracker.observe(440f, a4) }
        // 留一个进行中的候选，reset 之后它也不该残留
        tracker.observe(220f, a3)

        tracker.reset()
        assertNull(tracker.range.lowest)
        assertNull(tracker.range.highest)
        assertEquals(0, tracker.range.rangeInSemitones)
        assertFalse(tracker.range.hasRange)

        // reset 后需要重新累积完整的帧数
        tracker.observe(220f, a3)
        tracker.observe(220f, a3)
        assertNull(tracker.range.lowest)
        tracker.observe(220f, a3)
        assertEquals(a3, tracker.range.lowest)
    }

    @Test fun honoursAHigherStreakThresholdForPersistedRecords() {
        // 永久记录路径用更严格的阈值，一个离群值不该污染它
        val strict = PitchTracker(streakThreshold = 20)
        repeat(19) { strict.observe(220f, a3) }
        assertNull(strict.range.lowest)
        strict.observe(220f, a3)
        assertEquals(a3, strict.range.lowest)
    }
}
