package be.tarsos.dsp.io.android;

import android.media.AudioRecord;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.TarsosDSPAudioInputStream;
import java.io.IOException;

public class AndroidAudioInputStream implements TarsosDSPAudioInputStream {
    private final AudioRecord audioRecord;
    private final TarsosDSPAudioFormat format;

    public AndroidAudioInputStream(AudioRecord audioRecord, TarsosDSPAudioFormat format) {
        this.audioRecord = audioRecord;
        this.format = format;
    }

    @Override
    public long skip(long bytesToSkip) throws IOException {
        long skipped = 0;
        byte[] buffer = new byte[(int) bytesToSkip];
        while (skipped < bytesToSkip) {
            int read = audioRecord.read(buffer, 0, (int) (bytesToSkip - skipped));
            if (read < 0) break;
            skipped += read;
        }
        return skipped;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return audioRecord.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        audioRecord.stop();
        audioRecord.release();
    }

    @Override
    public TarsosDSPAudioFormat getFormat() {
        return format;
    }

    @Override
    public long getFrameLength() {
        return -1;
    }
}
