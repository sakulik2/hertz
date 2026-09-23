package xyz.sakulik.hertz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * 与 res/values/themes.xml 及 res/values-night/themes.xml 的成对契约
 * （改一边就必须改另一边）：
 *
 * themes.xml 的 windowBackground 承诺启动时的底色，Compose 必须用同一个颜色，
 * 否则第一帧会闪色。两侧的对应关系：
 *   values/        → hertz_window_background = BackgroundLight，windowLightStatusBar = true
 *   values-night/  → hertz_window_background = BackgroundDeep， windowLightStatusBar = false
 *
 * 状态栏图标方向也必须跟着走：浅背景要深色图标（windowLightStatusBar = true），
 * 反之则相反，否则图标会与背景同色而看不见。
 */

private val HertzDarkColorScheme = darkColorScheme(
    primary = SignalCyan,
    onPrimary = BackgroundDeep,
    primaryContainer = SignalCyanDim,
    onPrimaryContainer = SignalCyan,
    secondary = InstrumentBlue,
    onSecondary = BackgroundDeep,
    secondaryContainer = SignalContainerDark,
    onSecondaryContainer = SignalCyan,
    tertiary = InstrumentBlue,
    onTertiary = BackgroundDeep,
    tertiaryContainer = SignalContainerDark,
    onTertiaryContainer = SignalCyan,
    background = BackgroundDeep,
    onBackground = TextPrimary,
    surface = BackgroundDeep,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantMuted,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceRaised,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    error = DeviationRed,
    onError = BackgroundDeep,
    errorContainer = DeviationRedDim,
    onErrorContainer = DeviationRed,
    outline = DialTrack
)

private val HertzLightColorScheme = lightColorScheme(
    primary = SignalTealLight,
    onPrimary = Color.White,
    primaryContainer = SignalTealContainerLight,
    onPrimaryContainer = SignalTealLight,
    secondary = InstrumentBlueLight,
    onSecondary = Color.White,
    secondaryContainer = SignalTealContainerLight,
    onSecondaryContainer = SignalTealLight,
    tertiary = InstrumentBlueLight,
    onTertiary = Color.White,
    tertiaryContainer = SignalTealContainerLight,
    onTertiaryContainer = SignalTealLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = BackgroundLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = SurfaceRaisedLight,
    surfaceContainerLow = SurfaceRaisedLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    error = DeviationRedLight,
    onError = Color.White,
    errorContainer = DeviationRedContainerLight,
    onErrorContainer = DeviationRedLight,
    outline = DialTrackLight
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

private val DarkTunerColors = TunerColors(
    inTune = SignalCyan,
    slightlyOff = DeviationAmber,
    off = DeviationRed,
    dialTrack = DialTrack
)

private val LightTunerColors = TunerColors(
    inTune = SignalTealLight,
    slightlyOff = DeviationAmberLight,
    off = DeviationRedLight,
    dialTrack = DialTrackLight
)

/** 默认给暗色，仅在未包裹 HertzTheme 时生效（预览与仪器测试）。 */
private val LocalTunerColors = staticCompositionLocalOf { DarkTunerColors }

/** 通过 `MaterialTheme.tunerColors` 取用，与 MaterialTheme.colorScheme 对称。 */
val MaterialTheme.tunerColors: TunerColors
    @Composable get() = LocalTunerColors.current

@Composable
fun HertzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalTunerColors provides if (darkTheme) DarkTunerColors else LightTunerColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) HertzDarkColorScheme else HertzLightColorScheme,
            typography = HertzTypography,
            content = content
        )
    }
}
