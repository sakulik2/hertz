package xyz.sakulik.hertz.data

/**
 * 设置的最新快照，可从任意线程同步读取。
 *
 * 存在的理由：设置以 `Flow<TunerConfig>` 的形式到达，但 TarsosDSP 的音高回调运行在
 * 音频线程上，不能挂起等一个 Flow。ViewModel 在设置变化时写入这里，
 * [PitchRepository] 每帧同步读取。
 */
class ConfigSnapshot(initial: TunerConfig = TunerConfig.Default) {
    @Volatile
    var value: TunerConfig = initial
}
