package xyz.sakulik.hertz.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 用户可调设置的存取。只持久化用户真正会改的字段；采样率等音频参数留在
 * [TunerConfig] 的默认值里，改它们需要重建 AudioRecord，不属于设置项。
 */
interface SettingsStore {
    /** 始终先发射一个值（无存档时为 [TunerConfig.Default]）。 */
    val config: Flow<TunerConfig>

    suspend fun update(transform: (TunerConfig) -> TunerConfig)
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tuner_settings"
)

class DataStoreSettingsStore(private val context: Context) : SettingsStore {

    override val config: Flow<TunerConfig> =
        context.settingsDataStore.data.map { prefs -> prefs.toConfig() }

    override suspend fun update(transform: (TunerConfig) -> TunerConfig) {
        context.settingsDataStore.edit { prefs ->
            val updated = transform(prefs.toConfig())
            prefs[KEY_REFERENCE_HZ] = updated.referencePitchHz
            prefs[KEY_NOTE_NAMING] = updated.noteNaming.name
            prefs[KEY_CONFIDENCE] = updated.confidenceThreshold
            prefs[KEY_MIN_FREQ] = updated.minFrequencyHz
            prefs[KEY_MAX_FREQ] = updated.maxFrequencyHz
            prefs[KEY_SMOOTHING] = updated.smoothingFactor
            prefs[KEY_STREAK] = updated.streakThreshold
        }
    }

    /**
     * 缺失的键回落到默认值。存档值还要额外做一次合法性收束：
     * TunerConfig 的 init 会对越界值抛异常，而一条坏存档不应该让 app 起不来
     * （降级或手工改过文件都可能造成这种情况）。
     */
    private fun Preferences.toConfig(): TunerConfig {
        val defaults = TunerConfig.Default
        val minFreq = this[KEY_MIN_FREQ] ?: defaults.minFrequencyHz
        val maxFreq = this[KEY_MAX_FREQ] ?: defaults.maxFrequencyHz
        val sane = minFreq < maxFreq
        return TunerConfig(
            referencePitchHz = (this[KEY_REFERENCE_HZ] ?: defaults.referencePitchHz)
                .coerceIn(TunerConfig.REFERENCE_PITCH_RANGE),
            noteNaming = this[KEY_NOTE_NAMING]?.toNoteNamingOrNull() ?: defaults.noteNaming,
            confidenceThreshold = (this[KEY_CONFIDENCE] ?: defaults.confidenceThreshold)
                .coerceIn(0f, 1f),
            minFrequencyHz = if (sane) minFreq else defaults.minFrequencyHz,
            maxFrequencyHz = if (sane) maxFreq else defaults.maxFrequencyHz,
            smoothingFactor = (this[KEY_SMOOTHING] ?: defaults.smoothingFactor)
                .coerceIn(0.01f, 1f),
            streakThreshold = (this[KEY_STREAK] ?: defaults.streakThreshold)
                .coerceAtLeast(1)
        )
    }

    private fun String.toNoteNamingOrNull(): NoteNaming? =
        NoteNaming.entries.firstOrNull { it.name == this }

    private companion object {
        val KEY_REFERENCE_HZ = doublePreferencesKey("reference_pitch_hz")
        val KEY_NOTE_NAMING = stringPreferencesKey("note_naming")
        val KEY_CONFIDENCE = floatPreferencesKey("confidence_threshold")
        val KEY_MIN_FREQ = floatPreferencesKey("min_frequency_hz")
        val KEY_MAX_FREQ = floatPreferencesKey("max_frequency_hz")
        val KEY_SMOOTHING = floatPreferencesKey("smoothing_factor")
        val KEY_STREAK = intPreferencesKey("streak_threshold")
    }
}
