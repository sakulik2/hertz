package xyz.sakulik.hertz.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * 排版取向：读数用等宽字体，这样 "440.0 Hz" 变成 "98.3 Hz" 时数字不会左右跳动 ——
 * 实时刷新的仪表最忌讳布局抖动。标签文字保持默认无衬线。
 */

/** 主音名读数。字号大、字重轻，靠尺寸而非粗体建立层级。 */
val NoteDisplayStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Light,
    fontSize = 72.sp,
    lineHeight = 76.sp,
    letterSpacing = (-1).sp
)

/** 频率读数等次级数值。 */
val ReadoutStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.5.sp
)

/** 音域卡片里的音名，比主读数小但仍用等宽以便对齐。 */
val RangeValueStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 20.sp,
    lineHeight = 26.sp
)

val HertzTypography = Typography(
    displayLarge = NoteDisplayStyle,
    bodyLarge = ReadoutStyle,
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        // 小号标签加字距，提升全大写/短标签的可读性
        letterSpacing = 1.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    )
)
