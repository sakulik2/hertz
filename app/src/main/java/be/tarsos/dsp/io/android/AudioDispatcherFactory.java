package be.tarsos.dsp.io.android;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.TarsosDSPAudioInputStream;

public class AudioDispatcherFactory {
    public static AudioDispatcher fromDefaultMicrophone(int sampleRate, int audioBufferSize, int bufferOverlap) {
        int minAudioBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minAudioBufferSize <= audioBufferSize) {
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, audioBufferSize * 2);
            audioRecord.startRecording();
            TarsosDSPAudioFormat format = new TarsosDSPAudioFormat((float)sampleRate, 16, 1, true, false);
            TarsosDSPAudioInputStream audioStream = new AndroidAudioInputStream(audioRecord, format);
            return new AudioDispatcher(audioStream, audioBufferSize, bufferOverlap);
        } else {
            throw new IllegalArgumentException("Buffer size too small should be at least " + (minAudioBufferSize));
        }
    }
}
