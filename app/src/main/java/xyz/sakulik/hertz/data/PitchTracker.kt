package xyz.sakulik.hertz.data

/**
 * 观测到的音域。[lowest] / [highest] 为 null 表示尚未有任何极值稳定到可提交。
 */
data class VocalRangeState(
    val lowestFreq: Float? = null,
    val lowest: Note? = null,
    val highestFreq: Float? = null,
    val highest: Note? = null,
    val rangeInSemitones: Int = 0
) {
    val hasRange: Boolean get() = lowest != null && highest != null
}

/**
 * 追踪音域极值，只有当同一个音连续出现 [streakThreshold] 帧后才提交，
 * 以此过滤 YIN 的瞬时离群值。
 *
 * 纯 Kotlin，不依赖 Android，因此音高数学可以在 JVM 单元测试中验证。
 *
 * 阈值可配是为了让"会话内读数"和"永久记录"用不同的严格程度：
 * 会话内读数错了刷新即可，永久记录被污染会一直留着。
 */
class PitchTracker(private val streakThreshold: Int = TunerConfig.Default.streakThreshold) {

    private val low = ExtremeStreak(isLower = true)
    private val high = ExtremeStreak(isLower = false)

    var range: VocalRangeState = VocalRangeState()
        private set

    fun observe(frequencyHz: Float, note: Note): VocalRangeState {
        low.observe(frequencyHz, note, range.lowestFreq)
        high.observe(frequencyHz, note, range.highestFreq)

        val lowestFreq = low.committedFrequency ?: range.lowestFreq
        val lowest = low.committedNote ?: range.lowest
        val highestFreq = high.committedFrequency ?: range.highestFreq
        val highest = high.committedNote ?: range.highest

        range = VocalRangeState(
            lowestFreq = lowestFreq,
            lowest = lowest,
            highestFreq = highestFreq,
            highest = highest,
            rangeInSemitones = if (lowest != null && highest != null) {
                maxOf(0, highest.midi - lowest.midi)
            } else {
                0
            }
        )
        return range
    }

    fun reset() {
        low.reset()
        high.reset()
        range = VocalRangeState()
    }

    /**
     * 单个方向（最低或最高）的连续帧计数。低音与高音的逻辑互为镜像，
     * 只有比较方向不同，所以合成一个类而不是把两份几乎相同的代码并排放着。
     */
    private inner class ExtremeStreak(private val isLower: Boolean) {
        private var candidateNote: Note? = null
        private var candidateFrequency: Float = 0f
        private var streak: Int = 0

        /** 本次 observe 中达到阈值而提交的值；未提交则为 null。 */
        var committedNote: Note? = null
            private set
        var committedFrequency: Float? = null
            private set

        fun observe(frequencyHz: Float, note: Note, currentExtreme: Float?) {
            committedNote = null
            committedFrequency = null

            // 不比已提交的极值更极端，说明这一串已经断了
            if (currentExtreme != null && !isMoreExtreme(frequencyHz, currentExtreme)) {
                reset()
                return
            }

            if (candidateNote == note) {
                streak++
                // 同一个音内取最极端的那次频率，音分偏差才不会被平均掉
                candidateFrequency = if (isLower) {
                    minOf(candidateFrequency, frequencyHz)
                } else {
                    maxOf(candidateFrequency, frequencyHz)
                }
            } else {
                candidateNote = note
                candidateFrequency = frequencyHz
                streak = 1
            }

            if (streak >= streakThreshold) {
                committedNote = candidateNote
                committedFrequency = candidateFrequency
            }
        }

        private fun isMoreExtreme(frequencyHz: Float, current: Float): Boolean =
            if (isLower) frequencyHz < current else frequencyHz > current

        fun reset() {
            candidateNote = null
            candidateFrequency = 0f
            streak = 0
            committedNote = null
            committedFrequency = null
        }
    }
}
