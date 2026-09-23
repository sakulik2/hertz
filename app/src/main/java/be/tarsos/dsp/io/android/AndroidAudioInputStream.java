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

    /** Upper bound on the scratch buffer so a large skip cannot allocate an oversized array. */
    private static final int MAX_SKIP_BUFFER_SIZE = 8192;

    @Override
    public long skip(long bytesToSkip) throws IOException {
        if (bytesToSkip <= 0) {
            return 0;
        }
        long skipped = 0;
        byte[] buffer = new byte[(int) Math.min(bytesToSkip, MAX_SKIP_BUFFER_SIZE)];
        while (skipped < bytesToSkip) {
            int chunk = (int) Math.min(bytesToSkip - skipped, buffer.length);
            int read = audioRecord.read(buffer, 0, chunk);
            if (read <= 0) break;
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
        try {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
        } catch (IllegalStateException ignored) {
            // A failed or already stopped recording still needs to release the device.
        } finally {
            audioRecord.release();
        }
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
