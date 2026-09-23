package xyz.sakulik.hertz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PitchTrackerTest {
    @Test fun convertsA440ToMidi69() {
        assertEquals(69, PitchTracker.frequencyToMidi(440f))
        assertEquals(0f, PitchTracker.centsFromMidi(440f, 69), 0.01f)
    }

    @Test fun namesNotesAndOctavesInScientificPitchNotation() {
        assertEquals("A", PitchTracker.noteNameFromMidi(69))
        assertEquals(4, PitchTracker.octaveFromMidi(69))
        assertEquals("C", PitchTracker.noteNameFromMidi(60))
        assertEquals(4, PitchTracker.octaveFromMidi(60))
        assertEquals("B", PitchTracker.noteNameFromMidi(59))
        assertEquals(3, PitchTracker.octaveFromMidi(59))
    }

    @Test fun namesNotesBelowMidiZeroWithoutWrapping() {
        assertEquals("B", PitchTracker.noteNameFromMidi(-1))
        assertEquals(-2, PitchTracker.octaveFromMidi(-1))
        assertEquals("C", PitchTracker.noteNameFromMidi(0))
        assertEquals(-1, PitchTracker.octaveFromMidi(0))
    }

    @Test fun reportsSignedCentsDeviationFromNearestNote() {
        assertEquals(0f, PitchTracker.centsFromMidi(440f), 0.01f)
        // 440 Hz raised by 20 cents is still A4, deviating +20 cents.
        val sharpOfA440 = (440.0 * Math.pow(2.0, 20.0 / 1200.0)).toFloat()
        assertEquals(69, PitchTracker.frequencyToMidi(sharpOfA440))
        assertEquals(20f, PitchTracker.centsFromMidi(sharpOfA440), 0.5f)
        val flatOfA440 = (440.0 * Math.pow(2.0, -20.0 / 1200.0)).toFloat()
        assertEquals(-20f, PitchTracker.centsFromMidi(flatOfA440), 0.5f)
    }

    @Test fun requiresStableFramesBeforeRecordingExtremes() {
        val tracker = PitchTracker(streakThreshold = 3)
        tracker.observe(220f, "A", 3)
        assertNull(tracker.range.lowestNote)
        tracker.observe(220f, "A", 3)
        assertNull(tracker.range.lowestNote)
        val range = tracker.observe(220f, "A", 3)
        assertEquals("A3", range.lowestNote)
        assertEquals("A3", range.highestNote)
    }
}
