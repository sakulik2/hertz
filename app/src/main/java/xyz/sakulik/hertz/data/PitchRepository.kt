package xyz.sakulik.hertz.data

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed class PitchResult {
    data class Detected(
        val frequencyHz: Float,
        val note: Note,
        val centsDeviation: Float,
        val probability: Float
    ) : PitchResult()

    data object Silence : PitchResult()

    data class Error(val exception: Throwable) : PitchResult()
}

/**
 * 音高数据来源。抽成接口以便 [xyz.sakulik.hertz.ui.PitchViewModel] 在 JVM 单元测试中
 * 使用假实现，而不必触碰真实的 AudioRecord。
 */
interface PitchSource {
    val pitchFlow: Flow<PitchResult>
    fun startListening()
    fun stopListening()
}

/**
 * [configProvider] 每帧读取一次，而不是在构造时固化，这样基准音、灵敏度和频率门限
 * 的改动能立刻生效。采样率与缓冲大小例外：它们在 [startListening] 时读取一次，
 * 因为改变它们必须重建 AudioRecord。
 */
class PitchRepository(
    private val configProvider: () -> TunerConfig = { TunerConfig.Default }
) : PitchSource {

    private val pitchChannel = Channel<PitchResult>(
        capacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val pitchFlow: Flow<PitchResult> = pitchChannel.receiveAsFlow()

    private var dispatcher: AudioDispatcher? = null
    private var scope: CoroutineScope? = null

    @Synchronized
    override fun startListening() {
        if (dispatcher != null) return

        // 丢弃上次暂停时残留在缓冲区里的帧，否则它们会被当成当前音高处理并污染音域统计
        do {
            val drained = pitchChannel.tryReceive()
        } while (drained.isSuccess)

        val newScope = CoroutineScope(Dispatchers.IO)
        scope = newScope

        // 音频参数只在开始采集时读一次：改变它们要重建 AudioRecord，
        // 而 PitchProcessor 也必须拿到与之一致的采样率和缓冲大小
        val audioConfig = configProvider()

        try {
            val newDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(
                audioConfig.sampleRate,
                audioConfig.bufferSize,
                audioConfig.bufferOverlap
            )
            dispatcher = newDispatcher

            val pitchHandler = PitchDetectionHandler { result, _ ->
                // 每帧重新读取，让基准音与灵敏度的改动立即生效
                val config = configProvider()
                val probability = result.probability
                val freq = result.pitch

                if (result.isPitched
                    && probability >= config.confidenceThreshold
                    && freq in config.minFrequencyHz..config.maxFrequencyHz
                ) {
                    val note = Note.fromFrequency(freq, config.referencePitchHz)

                    pitchChannel.trySend(
                        PitchResult.Detected(
                            frequencyHz = freq,
                            note = note,
                            centsDeviation = Note.centsFrom(freq, config.referencePitchHz, note),
                            probability = probability
                        )
                    )
                } else {
                    pitchChannel.trySend(PitchResult.Silence)
                }
            }

            val pitchProcessor = PitchProcessor(
                PitchEstimationAlgorithm.YIN,
                audioConfig.sampleRate.toFloat(),
                audioConfig.bufferSize,
                pitchHandler
            )
            newDispatcher.addAudioProcessor(pitchProcessor)

            newScope.launch(Dispatchers.IO) {
                try {
                    newDispatcher.run()
                } catch (e: Exception) {
                    pitchChannel.trySend(PitchResult.Error(e))
                }
            }
        } catch (e: Exception) {
            stopListening()
            pitchChannel.trySend(PitchResult.Error(e))
        }
    }

    @Synchronized
    override fun stopListening() {
        val activeDispatcher = dispatcher
        dispatcher = null
        scope?.cancel()
        scope = null
        if (activeDispatcher != null) {
            try {
                activeDispatcher.stop()
            } catch (_: Exception) {
                // Dispatcher shutdown is best effort; its audio stream is closed by the dispatcher.
            }
        }
    }
}
