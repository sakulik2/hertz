package xyz.sakulik.hertz.data

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.math.log2
import kotlin.math.roundToInt

sealed class PitchResult {
    data class Detected(
        val frequencyHz: Float,
        val noteName: String,
        val octave: Int,
        val centsDeviation: Float
    ) : PitchResult()

    data object Silence : PitchResult()
}

class PitchRepository {

    private val pitchChannel = Channel<PitchResult>(capacity = 8)
    val pitchFlow: Flow<PitchResult> = pitchChannel.receiveAsFlow()

    private var dispatcher: AudioDispatcher? = null
    private var scope: CoroutineScope? = null

    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun startListening() {
        if (dispatcher != null) return

        scope = CoroutineScope(Dispatchers.IO)
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(44100, 4096, 3072)

        val pitchHandler = PitchDetectionHandler { result, _ ->
            val isPitched = result.isPitched
            val freq = result.pitch

            if (isPitched && freq in 70f..1400f) {
                val midiNumber = 12 * log2(freq / 440.0) + 69
                val roundedMidi = midiNumber.roundToInt()

                val noteIndex = roundedMidi % 12
                val noteName = noteNames[if (noteIndex >= 0) noteIndex else (noteIndex + 12) % 12]
                val octave = roundedMidi / 12 - 1
                val centsDeviation = ((midiNumber - roundedMidi) * 100).toFloat()

                pitchChannel.trySend(
                    PitchResult.Detected(
                        frequencyHz = freq,
                        noteName = noteName,
                        octave = octave,
                        centsDeviation = centsDeviation
                    )
                )
            } else {
                pitchChannel.trySend(PitchResult.Silence)
            }
        }

        val pitchProcessor = PitchProcessor(PitchEstimationAlgorithm.YIN, 44100f, 4096, pitchHandler)
        dispatcher?.addAudioProcessor(pitchProcessor)

        scope?.launch(Dispatchers.IO) {
            dispatcher?.run()
        }
    }

    fun stopListening() {
        dispatcher?.stop()
        dispatcher = null
        scope?.cancel()
        scope = null
    }
}
