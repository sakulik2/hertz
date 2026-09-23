package xyz.sakulik.hertz.data

import kotlin.math.log2
import kotlin.math.roundToInt

data class VocalRangeState(
    val lowestFreq: Float? = null,
    val lowestNote: String? = null,
    val highestFreq: Float? = null,
    val highestNote: String? = null,
    val rangeInSemitones: Int = 0
)

/** Holds an extremum only after it has been observed in several consecutive frames. */
class PitchTracker(private val streakThreshold: Int = 3) {
    private data class Candidate(val midi: Int, val frequency: Float, val name: String, val streak: Int)

    private var lowest: Candidate? = null
    private var highest: Candidate? = null
    var range: VocalRangeState = VocalRangeState()
        private set

    fun observe(frequency: Float, noteName: String, octave: Int): VocalRangeState {
        val midi = frequencyToMidi(frequency)
        val fullName = "$noteName$octave"
        var lowFreq = range.lowestFreq
        var lowName = range.lowestNote
        var highFreq = range.highestFreq
        var highName = range.highestNote

        if (lowFreq == null || frequency < lowFreq) {
            val candidate = lowest
            lowest = if (candidate?.midi == midi) candidate.copy(frequency = minOf(candidate.frequency, frequency), streak = candidate.streak + 1)
                else Candidate(midi, frequency, fullName, 1)
            if (lowest!!.streak >= streakThreshold) { lowFreq = lowest!!.frequency; lowName = lowest!!.name }
        } else lowest = null

        if (highFreq == null || frequency > highFreq) {
            val candidate = highest
            highest = if (candidate?.midi == midi) candidate.copy(frequency = maxOf(candidate.frequency, frequency), streak = candidate.streak + 1)
                else Candidate(midi, frequency, fullName, 1)
            if (highest!!.streak >= streakThreshold) { highFreq = highest!!.frequency; highName = highest!!.name }
        } else highest = null

        val lowMidi = lowFreq?.let(::frequencyToMidi)
        val highMidi = highFreq?.let(::frequencyToMidi)
        range = VocalRangeState(lowFreq, lowName, highFreq, highName, if (lowMidi != null && highMidi != null) maxOf(0, highMidi - lowMidi) else 0)
        return range
    }

    fun reset() { lowest = null; highest = null; range = VocalRangeState() }

    companion object {
        private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        fun frequencyToMidi(frequency: Float): Int = (12 * log2(frequency.toDouble() / 440.0) + 69).roundToInt()
        fun centsFromMidi(frequency: Float, midi: Int = frequencyToMidi(frequency)): Float =
            ((12 * log2(frequency.toDouble() / 440.0) + 69 - midi) * 100).toFloat()

        /** Pitch class name for a MIDI number, correct for negative inputs. */
        fun noteNameFromMidi(midi: Int): String = NOTE_NAMES[((midi % 12) + 12) % 12]

        /** Scientific pitch notation octave, where MIDI 60 is C4. */
        fun octaveFromMidi(midi: Int): Int = Math.floorDiv(midi, 12) - 1
    }
}
