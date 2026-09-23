package xyz.sakulik.hertz.data

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** 音名写法。等音（如 C# / Db）选哪一种是显示偏好，不影响音高本身。 */
enum class NoteNaming { SHARP, FLAT }

/**
 * 一个十二平均律音级，以 MIDI 音号标识（MIDI 69 = A4，MIDI 60 = C4）。
 *
 * 刻意只持有 midi：基准音和音名写法都是"上下文"，不是音的身份 —— 同一个 A4
 * 在 A440 和 A442 下仍是同一个音，只是理论频率不同。所以它们是函数参数而非字段。
 *
 * value class 意味着零装箱开销，这对每秒约 43 帧的音频回调路径有意义。
 */
@JvmInline
value class Note(val midi: Int) {

    /** 音级序号 0..11（C 为 0）。对负 MIDI 音号同样正确。 */
    val pitchClass: Int get() = ((midi % 12) + 12) % 12

    /** 科学音高记号法的八度，MIDI 60 为 C4。 */
    val octave: Int get() = Math.floorDiv(midi, 12) - 1

    fun name(naming: NoteNaming = NoteNaming.SHARP): String = when (naming) {
        NoteNaming.SHARP -> SHARP_NAMES[pitchClass]
        NoteNaming.FLAT -> FLAT_NAMES[pitchClass]
    }

    /** 例如 "A4"、"C#3"。 */
    fun displayName(naming: NoteNaming = NoteNaming.SHARP): String = "${name(naming)}$octave"

    /** 该音在给定基准音下的理论频率。 */
    fun idealFrequencyHz(referenceHz: Double = STANDARD_REFERENCE_HZ): Double =
        referenceHz * 2.0.pow((midi - A4_MIDI) / 12.0)

    companion object {
        /** 国际标准基准音 A4 = 440 Hz。 */
        const val STANDARD_REFERENCE_HZ = 440.0

        private const val A4_MIDI = 69

        private val SHARP_NAMES =
            arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        private val FLAT_NAMES =
            arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

        /** 连续的 MIDI 坐标（含小数），是取整与音分计算的共同基础。 */
        private fun midiPosition(frequencyHz: Float, referenceHz: Double): Double =
            12 * log2(frequencyHz.toDouble() / referenceHz) + A4_MIDI

        /** 距离给定频率最近的音。 */
        fun fromFrequency(
            frequencyHz: Float,
            referenceHz: Double = STANDARD_REFERENCE_HZ
        ): Note = Note(midiPosition(frequencyHz, referenceHz).roundToInt())

        /**
         * 相对某个音的带符号音分偏差；正值偏高。
         * 不传 [note] 时相对最近的音，取值范围为 ±50 音分。
         */
        fun centsFrom(
            frequencyHz: Float,
            referenceHz: Double = STANDARD_REFERENCE_HZ,
            note: Note = fromFrequency(frequencyHz, referenceHz)
        ): Float = ((midiPosition(frequencyHz, referenceHz) - note.midi) * 100).toFloat()
    }
}
