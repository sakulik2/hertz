package xyz.sakulik.hertz.data

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

class NoteTest {

    @Test fun convertsA440ToMidi69() {
        assertEquals(69, Note.fromFrequency(440f).midi)
        assertEquals(0f, Note.centsFrom(440f), 0.01f)
    }

    @Test fun namesNotesAndOctavesInScientificPitchNotation() {
        assertEquals("A", Note(69).name())
        assertEquals(4, Note(69).octave)
        assertEquals("A4", Note(69).displayName())
        assertEquals("C4", Note(60).displayName())
        assertEquals("B3", Note(59).displayName())
    }

    @Test fun namesNotesBelowMidiZeroWithoutWrapping() {
        assertEquals("B", Note(-1).name())
        assertEquals(-2, Note(-1).octave)
        assertEquals("C", Note(0).name())
        assertEquals(-1, Note(0).octave)
    }

    @Test fun spellsEnharmonicsAccordingToNamingPreference() {
        val cSharp = Note(61)
        assertEquals("C#4", cSharp.displayName(NoteNaming.SHARP))
        assertEquals("Db4", cSharp.displayName(NoteNaming.FLAT))
        // 白键音名不受写法影响
        assertEquals("A4", Note(69).displayName(NoteNaming.FLAT))
    }

    @Test fun reportsSignedCentsDeviationFromNearestNote() {
        val sharpOfA440 = (440.0 * 2.0.pow(20.0 / 1200.0)).toFloat()
        assertEquals(69, Note.fromFrequency(sharpOfA440).midi)
        assertEquals(20f, Note.centsFrom(sharpOfA440), 0.5f)

        val flatOfA440 = (440.0 * 2.0.pow(-20.0 / 1200.0)).toFloat()
        assertEquals(-20f, Note.centsFrom(flatOfA440), 0.5f)
    }

    @Test fun shiftsReadingsWhenReferencePitchIsNot440() {
        // 基准音设为 442 时，440 Hz 仍是 A4，但偏低约 7.85 音分
        val note = Note.fromFrequency(440f, referenceHz = 442.0)
        assertEquals(69, note.midi)
        val cents = Note.centsFrom(440f, referenceHz = 442.0)
        assertEquals(-7.85f, cents, 0.1f)

        // 而在 442 基准下，442 Hz 才是零偏差
        assertEquals(0f, Note.centsFrom(442f, referenceHz = 442.0), 0.01f)
    }

    @Test fun computesIdealFrequencyForAReferencePitch() {
        assertEquals(440.0, Note(69).idealFrequencyHz(), 0.001)
        assertEquals(220.0, Note(57).idealFrequencyHz(), 0.001)
        assertEquals(880.0, Note(81).idealFrequencyHz(), 0.001)
        assertEquals(442.0, Note(69).idealFrequencyHz(referenceHz = 442.0), 0.001)
    }

    @Test fun roundTripsBetweenNoteAndItsIdealFrequency() {
        // 每个音的理论频率应当解析回同一个音，覆盖人声到乐器的常用范围
        for (midi in 24..108) {
            val ideal = Note(midi).idealFrequencyHz().toFloat()
            assertEquals(midi, Note.fromFrequency(ideal).midi)
            assertEquals(0f, Note.centsFrom(ideal), 0.05f)
        }
    }

    @Test fun mapsFrequenciesToTheNearerNeighbourAtSemitoneBoundaries() {
        // 半音中点（+50 音分）之下仍归前一个音，之上归后一个音
        val justBelowMidpoint = (440.0 * 2.0.pow(49.0 / 1200.0)).toFloat()
        assertEquals(69, Note.fromFrequency(justBelowMidpoint).midi)

        val justAboveMidpoint = (440.0 * 2.0.pow(51.0 / 1200.0)).toFloat()
        assertEquals(70, Note.fromFrequency(justAboveMidpoint).midi)
    }
}
