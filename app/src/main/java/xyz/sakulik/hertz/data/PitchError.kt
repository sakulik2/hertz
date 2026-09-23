package xyz.sakulik.hertz.data

/**
 * 麦克风故障的分类。
 *
 * 原先所有故障都塌缩成一个布尔值，用户看到的是同一句"请检查麦克风权限和设备"，
 * 但这几种情况的处置方式完全不同：权限被撤销要跳系统设置，被别的应用占用只需重试，
 * 硬件初始化失败重试也没用。分类的意义在于让 UI 能给出对得上的下一步操作。
 */
sealed class PitchError {

    /** 采集过程中权限被撤销，或启动时根本没有权限。需要用户前往系统设置。 */
    data object PermissionRevoked : PitchError()

    /** 麦克风被其他应用占用。通常稍后重试即可。 */
    data object DeviceBusy : PitchError()

    /** AudioRecord 初始化失败，多为硬件或系统限制。重试大概率无效。 */
    data object InitFailed : PitchError()

    /** 未能归类的异常，保留原始异常以便日志排查。 */
    data class Unknown(val cause: Throwable) : PitchError()

    /** 重试是否有望成功。决定 UI 显示"重试"还是"去设置"。 */
    val isRetryable: Boolean
        get() = when (this) {
            is DeviceBusy, is Unknown -> true
            is PermissionRevoked, is InitFailed -> false
        }
}
