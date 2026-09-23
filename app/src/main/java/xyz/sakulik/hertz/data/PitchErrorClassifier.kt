package xyz.sakulik.hertz.data

import be.tarsos.dsp.io.android.MicrophoneUnavailableException

/**
 * 把底层异常翻译成 [PitchError]。
 *
 * 单独成文件而不是塞进 PitchRepository，是为了让分类规则本身可以在 JVM 上测试 ——
 * MicrophoneUnavailableException 只是个普通异常类，不需要真实的 AudioRecord。
 *
 * [hasPermission] 由调用方注入而非在此查询 Context：NOT_INITIALIZED 既可能是缺权限
 * 也可能是硬件不可用，只有结合权限状态才能分开，而 PitchRepository 刻意不持有 Context。
 */
fun classifyPitchError(cause: Throwable, hasPermission: Boolean): PitchError {
    if (!hasPermission) return PitchError.PermissionRevoked

    return when {
        cause is MicrophoneUnavailableException -> when (cause.reason) {
            // 有权限却起不来，基本是硬件或系统策略限制
            MicrophoneUnavailableException.Reason.NOT_INITIALIZED -> PitchError.InitFailed
            // 构造成功但 startRecording 失败，最常见的原因是被其他应用占用
            MicrophoneUnavailableException.Reason.START_FAILED -> PitchError.DeviceBusy
        }
        // AudioRecord 在使用中被抢走时，读取会抛 IllegalStateException
        cause is IllegalStateException -> PitchError.DeviceBusy
        cause is SecurityException -> PitchError.PermissionRevoked
        else -> PitchError.Unknown(cause)
    }
}
