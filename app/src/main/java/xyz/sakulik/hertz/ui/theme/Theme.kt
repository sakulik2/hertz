package xyz.sakulik.hertz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * 与 res/values/themes.xml 的成对契约（改一边就必须改另一边）：
 * themes.xml 承诺黑色 windowBackground 与浅色状态栏图标，所以这里必须是深色配色。
 * 只改一边会重新引入启动闪白，或让状态栏图标在深色背景上不可见。
 * 本 app 刻意只有深色主题，没有 values-night。
 */

private val HertzColorScheme = darkColorScheme(
    primary = SignalCyan,
    onPrimary = BackgroundDeep,
    primaryContainer = SignalCyanDim,
    onPrimaryContainer = SignalCyan,
    secondary = InstrumentBlue,
    onSecondary = BackgroundDeep,
    background = BackgroundDeep,
    onBackground = TextPrimary,
    surface = BackgroundDeep,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantMuted,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SurfaceRaised,
    surfaceContainerHigh = SurfaceRaised,
    error = DeviationRed,
    onError = BackgroundDeep,
    errorContainer = DeviationRedDim,
    onErrorContainer = DeviationRed,
    outline = DialTrack
)

/**
 * 调音器专有的语义色。Material 的 ColorScheme 没有"准音 / 偏差"这类角色，
 * 硬套 primary / error 会让语义和主题角色混在一起，所以单独开一层。
 */
@Immutable
data class TunerColors(
    /** 偏差在容许范围内 */
    val inTune: Color,
    /** 轻微偏离 */
    val slightlyOff: Color,
    /** 明显偏离 */
    val off: Color,
    /** 表盘未激活轨道 */
    val dialTrack: Color
)

private val DefaultTunerColors = TunerColors(
    inTune = SignalCyan,
    slightlyOff = DeviationAmber,
    off = DeviationRed,
    dialTrack = DialTrack
)

private val LocalTunerColors = staticCompositionLocalOf { DefaultTunerColors }

/** 通过 `MaterialTheme.tunerColors` 取用，与 MaterialTheme.colorScheme 对称。 */
val MaterialTheme.tunerColors: TunerColors
    @Composable get() = LocalTunerColors.current

@Composable
fun HertzTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTunerColors provides DefaultTunerColors) {
        MaterialTheme(
            colorScheme = HertzColorScheme,
            typography = HertzTypography,
            content = content
        )
    }
}
