package xyz.sakulik.hertz.data

/**
 * 历史最佳音域，跨会话保留。
 *
 * 与 [VocalRangeState] 的区别是刻意的：后者是本次会话的观测值，可以随时重置；
 * 这里是一条长期记录，因此只在明显更宽时才扩展，且提交门限比会话内严格得多
 * （见 [TunerConfig.recordStreakThreshold]）。
 *
 * [referencePitchHz] 随记录一起存：同一个频率在不同基准音下是不同的音，
 * 不记下当时的基准音，日后就无法解释这条记录。
 */
data class RangeRecord(
    val lowestFreq: Float,
    val highestFreq: Float,
    val referencePitchHz: Double,
    /** 记录更新时间，Unix 毫秒。 */
    val updatedAtEpochMillis: Long
) {
    val lowest: Note get() = Note.fromFrequency(lowestFreq, referencePitchHz)
    val highest: Note get() = Note.fromFrequency(highestFreq, referencePitchHz)
    val rangeInSemitones: Int get() = maxOf(0, highest.midi - lowest.midi)

    /**
     * 用一次新观测扩展这条记录，只取更极端的一侧；没有任何一侧更极端时返回 null，
     * 让调用方省掉一次写盘。
     */
    fun extendedBy(
        lowestFreq: Float?,
        highestFreq: Float?,
        referencePitchHz: Double,
        nowEpochMillis: Long
    ): RangeRecord? {
        val newLow = lowestFreq?.takeIf { it < this.lowestFreq }
        val newHigh = highestFreq?.takeIf { it > this.highestFreq }
        if (newLow == null && newHigh == null) return null
        return copy(
            lowestFreq = newLow ?: this.lowestFreq,
            highestFreq = newHigh ?: this.highestFreq,
            referencePitchHz = referencePitchHz,
            updatedAtEpochMillis = nowEpochMillis
        )
    }

    companion object {
        /** 由首次观测建立一条记录。两端缺任意一侧就返回 null。 */
        fun of(
            lowestFreq: Float?,
            highestFreq: Float?,
            referencePitchHz: Double,
            nowEpochMillis: Long
        ): RangeRecord? {
            if (lowestFreq == null || highestFreq == null) return null
            return RangeRecord(
                lowestFreq = minOf(lowestFreq, highestFreq),
                highestFreq = maxOf(lowestFreq, highestFreq),
                referencePitchHz = referencePitchHz,
                updatedAtEpochMillis = nowEpochMillis
            )
        }
    }
}
