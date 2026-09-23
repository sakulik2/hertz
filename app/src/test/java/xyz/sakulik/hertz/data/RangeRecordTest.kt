package xyz.sakulik.hertz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RangeRecordTest {

    @Test fun derivesNotesAndSpanFromStoredFrequencies() {
        val record = RangeRecord(
            lowestFreq = 82.4f,      // E2
            highestFreq = 440f,      // A4
            referencePitchHz = 440.0,
            updatedAtEpochMillis = 1L
        )
        assertEquals("E2", record.lowest.displayName())
        assertEquals("A4", record.highest.displayName())
        assertEquals(29, record.rangeInSemitones)
    }

    @Test fun interpretsStoredFrequenciesWithTheStoredReferencePitch() {
        // 同一频率在不同基准音下音分偏差不同，所以基准音必须随记录一起存
        val at440 = RangeRecord(440f, 440f, 440.0, 1L)
        val at442 = RangeRecord(440f, 440f, 442.0, 1L)
        assertEquals(0f, Note.centsFrom(440f, at440.referencePitchHz), 0.01f)
        assertEquals(-7.85f, Note.centsFrom(440f, at442.referencePitchHz), 0.1f)
    }

    @Test fun requiresBothEndsToCreateARecord() {
        assertNull(RangeRecord.of(null, 440f, 440.0, 1L))
        assertNull(RangeRecord.of(220f, null, 440.0, 1L))
        assertNull(RangeRecord.of(null, null, 440.0, 1L))
    }

    @Test fun ordersEndsWhenCreatingARecord() {
        // 传入顺序颠倒时仍应得到合法区间，而不是一个负跨度
        val record = RangeRecord.of(lowestFreq = 440f, highestFreq = 220f, 440.0, 1L)!!
        assertEquals(220f, record.lowestFreq, 0.01f)
        assertEquals(440f, record.highestFreq, 0.01f)
        assertEquals(12, record.rangeInSemitones)
    }

    @Test fun returnsNullWhenNeitherEndWidens() {
        val record = RangeRecord(220f, 440f, 440.0, 1L)
        assertNull(record.extendedBy(330f, 330f, 440.0, 2L))
        // 相等也不算更极端，避免无谓写盘
        assertNull(record.extendedBy(220f, 440f, 440.0, 2L))
    }

    @Test fun extendsOnlyTheEndThatWidened() {
        val record = RangeRecord(220f, 440f, 440.0, 1L)

        val lower = record.extendedBy(110f, 330f, 440.0, 2L)!!
        assertEquals(110f, lower.lowestFreq, 0.01f)
        assertEquals(440f, lower.highestFreq, 0.01f)
        assertEquals(2L, lower.updatedAtEpochMillis)

        val higher = record.extendedBy(330f, 880f, 440.0, 3L)!!
        assertEquals(220f, higher.lowestFreq, 0.01f)
        assertEquals(880f, higher.highestFreq, 0.01f)
    }

    @Test fun toleratesAMissingEndWhenExtending() {
        val record = RangeRecord(220f, 440f, 440.0, 1L)
        val extended = record.extendedBy(lowestFreq = 110f, highestFreq = null, 440.0, 2L)!!
        assertEquals(110f, extended.lowestFreq, 0.01f)
        assertEquals(440f, extended.highestFreq, 0.01f)
        assertNull(record.extendedBy(lowestFreq = null, highestFreq = null, 440.0, 2L))
    }

    @Test fun recordsTheReferencePitchInEffectAtUpdateTime() {
        val record = RangeRecord(220f, 440f, 440.0, 1L)
        val extended = record.extendedBy(110f, null, referencePitchHz = 442.0, nowEpochMillis = 2L)!!
        assertEquals(442.0, extended.referencePitchHz, 0.001)
    }
}
