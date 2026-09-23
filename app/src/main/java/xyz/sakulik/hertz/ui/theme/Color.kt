package xyz.sakulik.hertz.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * 配色取向：调音器是一台仪器，不是一个内容型 app。
 * 因此背景压到近黑，只让"读数"发光 —— 参考示波器与实验室仪表的观感。
 * 色相集中在青色（信号）与琥珀/红（偏差），避免 Material 默认紫。
 */

/** 窗口与 Surface 底色。必须与 res/values/colors.xml 的 hertz_window_background 一致。 */
internal val BackgroundDeep = Color(0xFF08090B)

/** 卡片等抬升表面，比底色略亮一档，不用纯灰以免显脏。 */
internal val SurfaceRaised = Color(0xFF14171C)
internal val SurfaceVariantMuted = Color(0xFF262B33)

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
