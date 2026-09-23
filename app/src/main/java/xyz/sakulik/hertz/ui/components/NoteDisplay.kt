package xyz.sakulik.hertz.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import xyz.sakulik.hertz.R

/**
 * 当前音名与频率读数。刻意独立于表盘 Canvas，避免两者互相遮挡。
 *
 * [noteLabel] 为 null 表示当前没有读数。
 */
@Composable
fun NoteDisplay(
    noteLabel: String?,
    frequencyText: String,
    semanticLabel: String,
    modifier: Modifier = Modifier
) {
    val placeholder = stringResource(R.string.value_placeholder)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // 音名与频率合成一条播报，否则屏幕阅读器会把它们读成两条割裂的信息
        modifier = modifier.clearAndSetSemantics { contentDescription = semanticLabel }
    ) {
        AnimatedContent(
            targetState = noteLabel ?: placeholder,
            transitionSpec = {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "noteAnimation"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = frequencyText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
