package xyz.sakulik.hertz.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * 配色取向：调音器是一台仪器，不是一个内容型 app。色相集中在青色（信号）
 * 与琥珀/红（偏差），避免 Material 默认紫。
 *
 * 两套配色不是简单取反：
 * - 暗色下背景压到近黑，只让"读数"发光，参考示波器与实验室仪表的观感。
 * - 亮色下同样的青色在白底上对比度不足，因此把信号色与偏差色整体压暗提纯，
 *   让指针在浅背景上依然是视觉焦点。
 */

// ---------- 暗色 ----------

/** 窗口与 Surface 底色。必须与 res/values-night/colors.xml 的 hertz_window_background 一致。 */
internal val BackgroundDeep = Color(0xFF08090B)

/*
 * 抬升表面梯度。必须逐档都给值：Material 3 的组件各取不同档位
 * （ElevatedCard 取 surfaceContainerLow，AlertDialog 取 surfaceContainerHigh），
 * 漏掉任意一档，该组件就会回落到 Material 基线紫。
 */
internal val SurfaceContainerLowestDark = Color(0xFF050608)
internal val SurfaceContainerLowDark = Color(0xFF0F1216)
internal val SurfaceRaised = Color(0xFF14171C)
internal val SurfaceContainerHighDark = Color(0xFF1B1F25)
internal val SurfaceContainerHighestDark = Color(0xFF232830)
internal val SurfaceVariantMuted = Color(0xFF262B33)

/** 次要容器：FilledTonalButton 取的就是这一档。 */
internal val SignalContainerDark = Color(0xFF123A35)

/** 主色：信号青。用于准音状态、指针、强调。 */
internal val SignalCyan = Color(0xFF3DDCC4)
internal val SignalCyanDim = Color(0xFF0C2E2A)

/** 次要色：冷蓝，用于非关键强调，避免与主色抢注意力。 */
internal val InstrumentBlue = Color(0xFF7AA2D4)

/** 偏差色阶：轻微偏离用琥珀，明显偏离用红。 */
internal val DeviationAmber = Color(0xFFE8B84B)
internal val DeviationRed = Color(0xFFE5674F)
internal val DeviationRedDim = Color(0xFF3A1512)

internal val TextPrimary = Color(0xFFECEFF3)
internal val TextSecondary = Color(0xFF9BA4B0)

/** 表盘未激活轨道。刻意压暗，让指针成为唯一亮点。 */
internal val DialTrack = Color(0xFF2A3038)

// ---------- 亮色 ----------

/** 窗口与 Surface 底色。必须与 res/values/colors.xml 的 hertz_window_background 一致。 */
internal val BackgroundLight = Color(0xFFF7F8FA)

/**
 * 抬升表面梯度（亮色）。卡片用纯白，比背景亮一档，
 * 与暗色下"抬升即变亮"的方向保持一致。同样必须逐档给值。
 */
internal val SurfaceRaisedLight = Color(0xFFFFFFFF)
internal val SurfaceContainerLight = Color(0xFFF1F3F6)
internal val SurfaceContainerHighLight = Color(0xFFEAEEF2)
internal val SurfaceContainerHighestLight = Color(0xFFE3E7EC)
internal val SurfaceVariantLight = Color(0xFFE3E7EC)

/**
 * 亮色下的信号色。暗色那支 #3DDCC4 在白底上对比度只有约 1.8:1，
 * 达不到可读要求，因此压暗到深青，对 BackgroundLight 约 4.8:1。
 */
internal val SignalTealLight = Color(0xFF00786C)
internal val SignalTealContainerLight = Color(0xFFCFF2EC)

internal val InstrumentBlueLight = Color(0xFF3C6491)

/** 偏差色同样压暗：琥珀在白底上尤其容易糊掉，需要更深的橙。 */
internal val DeviationAmberLight = Color(0xFF9A6B00)
internal val DeviationRedLight = Color(0xFFC0392B)
internal val DeviationRedContainerLight = Color(0xFFFBE0DC)

internal val TextPrimaryLight = Color(0xFF14171C)
internal val TextSecondaryLight = Color(0xFF55606E)

/** 表盘未激活轨道。亮色下比背景略深，作用与暗色下的"略亮"对称。 */
internal val DialTrackLight = Color(0xFFCBD2DA)
