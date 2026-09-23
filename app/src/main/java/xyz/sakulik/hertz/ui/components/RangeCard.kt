package xyz.sakulik.hertz.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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
import xyz.sakulik.hertz.ui.theme.RangeValueStyle

/** 本次会话与历史最佳音域并列显示。传 null 表示尚无数据。 */
@Composable
fun RangeCard(
    sessionLowest: String?,
    sessionSemitones: Int,
    sessionHighest: String?,
    bestLowest: String?,
    bestSemitones: Int,
    bestHighest: String?,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionLabel(stringResource(R.string.label_session_range))
            Spacer(modifier = Modifier.height(8.dp))
            RangeRow(sessionLowest, sessionSemitones, sessionHighest)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionLabel(stringResource(R.string.label_best_range))
            Spacer(modifier = Modifier.height(8.dp))
            RangeRow(bestLowest, bestSemitones, bestHighest)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 最低音 / 音域 / 最高音 三栏。 */
@Composable
private fun RangeRow(lowest: String?, semitones: Int, highest: String?) {
    val placeholder = stringResource(R.string.value_placeholder)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RangeCell(
            label = stringResource(R.string.label_lowest),
            value = lowest ?: placeholder,
            spokenValue = lowest ?: stringResource(R.string.a11y_no_reading)
        )
        RangeCell(
            label = stringResource(R.string.label_range),
            value = stringResource(R.string.unit_semitones, semitones),
            spokenValue = stringResource(R.string.unit_semitones, semitones)
        )
        RangeCell(
            label = stringResource(R.string.label_highest),
            value = highest ?: placeholder,
            spokenValue = highest ?: stringResource(R.string.a11y_no_reading)
        )
    }
}

@Composable
private fun RangeCell(label: String, value: String, spokenValue: String) {
    // 合并为一个语义节点，否则 TalkBack 会把标签和数值读成两条不相干的信息，
    // 而 "--" 这样的占位符读出来也毫无意义
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "$label: $spokenValue"
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = RangeValueStyle)
    }
}
