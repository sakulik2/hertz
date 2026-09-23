package xyz.sakulik.hertz.data

/**
 * 调音器的全部可调参数。这些值原先散落在 PitchRepository、PitchViewModel 和
 * PitchTracker 三个文件里，其中采样参数和基准音还各自重复了两遍。
 *
 * 音频参数（[sampleRate] / [bufferSize] / [bufferOverlap]）与用户偏好放在一起，
 * 是因为它们共同决定帧率，而帧率决定 [streakThreshold] 和 [recordStreakThreshold]
 * 对应多少毫秒 —— 分开放会让这层关系隐形。
 */
data class TunerConfig(
    /** 基准音 A4 频率。常见校准范围 415–466 Hz。 */
    val referencePitchHz: Double = Note.STANDARD_REFERENCE_HZ,

    /** 音名写法偏好。 */
    val noteNaming: NoteNaming = NoteNaming.SHARP,

    /** YIN 置信度阈值：低于此值的帧视为噪声，不触发音高事件。 */
    val confidenceThreshold: Float = 0.85f,

    /** 可接受的频率下限（Hz）。低于此值多为环境低频噪声。低音炮约 80Hz。 */
    val minFrequencyHz: Float = 80f,

    /** 可接受的频率上限（Hz）。女高音约 1100Hz。 */
    val maxFrequencyHz: Float = 1100f,

    /** 音分平滑系数：新读数所占权重，越小越稳但越迟钝。 */
    val smoothingFactor: Float = 0.3f,

    /** 会话内音域极值需要连续稳定的帧数。 */
    val streakThreshold: Int = 3,

    /**
     * 写入永久记录前需要连续稳定的帧数，明显高于 [streakThreshold]。
     * 会话内读数错了刷新一下就好，永久记录被一个 YIN 离群值污染则会一直留着。
     */
    val recordStreakThreshold: Int = 20,

    val sampleRate: Int = 44100,
    val bufferSize: Int = 4096,
    val bufferOverlap: Int = 3072
) {
    /**
     * 每秒帧数。步长为 [bufferSize] - [bufferOverlap]，
     * 默认配置下为 44100 / 1024 ≈ 43 帧/秒。
     */
    val framesPerSecond: Float get() = sampleRate.toFloat() / (bufferSize - bufferOverlap)

    init {
        require(minFrequencyHz < maxFrequencyHz) {
            "minFrequencyHz ($minFrequencyHz) must be below maxFrequencyHz ($maxFrequencyHz)"
        }
        require(bufferOverlap < bufferSize) {
            "bufferOverlap ($bufferOverlap) must be below bufferSize ($bufferSize)"
        }
        require(smoothingFactor > 0f && smoothingFactor <= 1f) {
            "smoothingFactor ($smoothingFactor) must be in (0, 1]"
        }
        require(referencePitchHz > 0) { "referencePitchHz ($referencePitchHz) must be positive" }
        require(streakThreshold >= 1) { "streakThreshold ($streakThreshold) must be at least 1" }
        require(recordStreakThreshold >= 1) {
            "recordStreakThreshold ($recordStreakThreshold) must be at least 1"
        }
    }

    companion object {
        val Default = TunerConfig()

        /** 用户可校准的基准音范围，涵盖常见的古乐与乐团调校习惯。 */
        val REFERENCE_PITCH_RANGE = 415.0..466.0
    }
}
