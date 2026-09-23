package xyz.sakulik.hertz.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * 采集期间保持屏幕常亮。
 *
 * 需要这个是因为调音时用户只是对着麦克风发声，没有任何触摸事件，
 * 系统会照常调暗并锁屏 —— 这是核心使用场景里的一个真实缺口。
 *
 * [enabled] 变为 false 或离开界面时清除标志，避免屏幕一直亮着耗电。
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val window = (context as? Activity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
