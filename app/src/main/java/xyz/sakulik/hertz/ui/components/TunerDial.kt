package xyz.sakulik.hertz.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.sakulik.hertz.ui.theme.tunerColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 指针配色档位：偏差绝对值小于此值视为准音 */
const val IN_TUNE_CENTS = 10f

/** 偏差小于此值视为轻微偏离，超出则视为明显偏离 */
const val SLIGHTLY_OFF_CENTS = 25f

/*
 * 表盘几何：从 210° 起顺时针扫过 120°，正中为 270°（正上方）。
 * ±DIAL_CENTS_SPAN 音分映射到 ±DIAL_HALF_SWEEP_DEGREES。
 */
private const val DIAL_START_ANGLE = 210f
private const val DIAL_SWEEP_ANGLE = 120f
private const val DIAL_CENTER_ANGLE = 270f
private const val DIAL_HALF_SWEEP_DEGREES = 60f
private const val DIAL_CENTS_SPAN = 50f

private const val DIAL_HEIGHT_DP = 150
private const val DIAL_RADIUS_FRACTION = 0.85f

/**
 * 半圆表盘与音分偏差指针。
 *
 * [semanticLabel] 是这个控件对屏幕阅读器的全部信息来源：表盘是本 app 的主要信息载体，
 * 而 Canvas 默认对 TalkBack 完全不可见，所以调用方必须提供一句可读的偏差描述。
 */
@Composable
fun TunerDial(
    centsDeviation: Float,
    semanticLabel: String,
    modifier: Modifier = Modifier
) {
    val animatedCents by animateFloatAsState(
        targetValue = centsDeviation,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "centsAnimation"
    )

    // 指针颜色分三档，比"准/不准"二值更能反映接近程度
    val tunerColors = MaterialTheme.tunerColors
    val pointerColor by animateColorAsState(
        targetValue = when {
            abs(centsDeviation) < IN_TUNE_CENTS -> tunerColors.inTune
            abs(centsDeviation) < SLIGHTLY_OFF_CENTS -> tunerColors.slightlyOff
            else -> tunerColors.off
        },
        label = "pointerColorAnimation"
    )
    val trackColor = tunerColors.dialTrack

    // 独立控制高度与圆心，避免表盘与音符文字互相穿透
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DIAL_HEIGHT_DP.dp)
            .semantics { contentDescription = semanticLabel },
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val arcCenter = Offset(size.width / 2f, size.height - 12.dp.toPx())
            val radius = size.height * DIAL_RADIUS_FRACTION

            drawArc(
                color = trackColor,
                startAngle = DIAL_START_ANGLE,
                sweepAngle = DIAL_SWEEP_ANGLE,
                useCenter = false,
                topLeft = Offset(arcCenter.x - radius, arcCenter.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )

            val angleDegrees = DIAL_CENTER_ANGLE +
                (animatedCents / DIAL_CENTS_SPAN).coerceIn(-1f, 1f) * DIAL_HALF_SWEEP_DEGREES
            val angleRadians = angleDegrees * PI / 180.0

            drawLine(
                color = pointerColor,
                start = arcCenter,
                end = Offset(
                    arcCenter.x + radius * cos(angleRadians).toFloat(),
                    arcCenter.y + radius * sin(angleRadians).toFloat()
                ),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
