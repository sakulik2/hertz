package be.tarsos.dsp.io.android;

/**
 * Thrown when the microphone cannot be opened, carrying enough detail for the caller to
 * tell the failure modes apart.
 *
 * A bare IllegalStateException forced the repository to guess, so every microphone
 * problem reached the user as the same generic message.
 */
public class MicrophoneUnavailableException extends IllegalStateException {

    public enum Reason {
        /** AudioRecord was constructed but never reached STATE_INITIALIZED. */
        NOT_INITIALIZED,
        /** Construction succeeded but startRecording failed, which usually means another app holds the mic. */
        START_FAILED
    }

    private final Reason reason;

    public MicrophoneUnavailableException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
