package xyz.sakulik.hertz.data

import be.tarsos.dsp.io.android.MicrophoneUnavailableException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PitchErrorClassifierTest {

    @Test fun reportsRevokedPermissionRegardlessOfTheUnderlyingCause() {
        // 没有权限时，底层异常长什么样都不重要：处置方式只有一种
        val causes = listOf(
            IllegalStateException("anything"),
            IOException("read failed"),
            notInitialized()
        )
        for (cause in causes) {
            assertEquals(
                PitchError.PermissionRevoked,
                classifyPitchError(cause, hasPermission = false)
            )
        }
    }

    @Test fun treatsAFailedStartAsTheDeviceBeingBusy() {
        val error = classifyPitchError(startFailed(), hasPermission = true)
        assertEquals(PitchError.DeviceBusy, error)
    }

    @Test fun treatsAnUninitializedRecorderWithPermissionAsAnInitFailure() {
        // 有权限却仍无法初始化，说明不是权限问题，而是硬件或系统限制
        val error = classifyPitchError(notInitialized(), hasPermission = true)
        assertEquals(PitchError.InitFailed, error)
    }

    @Test fun treatsAMidCaptureIllegalStateAsTheDeviceBeingBusy() {
        // 采集途中麦克风被抢走时 AudioRecord 抛的就是 IllegalStateException
        val error = classifyPitchError(IllegalStateException("mic gone"), hasPermission = true)
        assertEquals(PitchError.DeviceBusy, error)
    }

    @Test fun treatsASecurityExceptionAsARevokedPermission() {
        val error = classifyPitchError(SecurityException("denied"), hasPermission = true)
        assertEquals(PitchError.PermissionRevoked, error)
    }

    @Test fun keepsTheOriginalCauseForUnclassifiedFailures() {
        val cause = IOException("disk on fire")
        val error = classifyPitchError(cause, hasPermission = true)
        assertEquals(PitchError.Unknown(cause), error)
        assertEquals(cause, (error as PitchError.Unknown).cause)
    }

    @Test fun marksOnlyTransientFailuresAsRetryable() {
        assertTrue(PitchError.DeviceBusy.isRetryable)
        assertTrue(PitchError.Unknown(IOException()).isRetryable)
        // 这两种重试不会有任何改变，UI 因此不该提供重试按钮
        assertFalse(PitchError.PermissionRevoked.isRetryable)
        assertFalse(PitchError.InitFailed.isRetryable)
    }

    private fun notInitialized() = MicrophoneUnavailableException(
        MicrophoneUnavailableException.Reason.NOT_INITIALIZED,
        "not initialized",
        null
    )

    private fun startFailed() = MicrophoneUnavailableException(
        MicrophoneUnavailableException.Reason.START_FAILED,
        "start failed",
        IllegalStateException("busy")
    )
}
