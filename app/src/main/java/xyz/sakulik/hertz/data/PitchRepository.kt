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

/** YIN 置信度阈值：低于此值的帧视为噪声，不触发音高事件 */
private const val CONFIDENCE_THRESHOLD = 0.85f

/** 人声可用频率范围（Hz）。低音炮约 80Hz，女高音约 1100Hz */
private const val VOCAL_FREQ_MIN = 80f
private const val VOCAL_FREQ_MAX = 1100f

sealed class PitchResult {
    data class Detected(
        val frequencyHz: Float,
        val noteName: String,
        val octave: Int,
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

class PitchRepository : PitchSource {

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

        try {
            val newDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(44100, 4096, 3072)
            dispatcher = newDispatcher

            val pitchHandler = PitchDetectionHandler { result, _ ->
                val probability = result.probability
                val freq = result.pitch

                if (result.isPitched
                    && probability >= CONFIDENCE_THRESHOLD
                    && freq in VOCAL_FREQ_MIN..VOCAL_FREQ_MAX
                ) {
                    val midi = PitchTracker.frequencyToMidi(freq)

                    pitchChannel.trySend(
                        PitchResult.Detected(
                            frequencyHz = freq,
                            noteName = PitchTracker.noteNameFromMidi(midi),
                            octave = PitchTracker.octaveFromMidi(midi),
                            centsDeviation = PitchTracker.centsFromMidi(freq, midi),
                            probability = probability
                        )
                    )
                } else {
                    pitchChannel.trySend(PitchResult.Silence)
                }
            }

            val pitchProcessor = PitchProcessor(PitchEstimationAlgorithm.YIN, 44100f, 4096, pitchHandler)
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
