package xyz.sakulik.hertz.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 历史最佳音域的存取。抽成接口以便 ViewModel 在 JVM 单元测试中使用内存实现，
 * 与 [PitchSource] 同一套路。
 */
interface RangeRecordStore {
    /** 尚无记录时发射 null。 */
    val record: Flow<RangeRecord?>

    suspend fun save(record: RangeRecord)

    suspend fun clear()
}

private val Context.rangeRecordDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "range_record"
)

class DataStoreRangeRecordStore(private val context: Context) : RangeRecordStore {

    override val record: Flow<RangeRecord?> =
        context.rangeRecordDataStore.data.map { prefs -> prefs.toRangeRecord() }

    override suspend fun save(record: RangeRecord) {
        context.rangeRecordDataStore.edit { prefs ->
            prefs[KEY_LOWEST_FREQ] = record.lowestFreq
            prefs[KEY_HIGHEST_FREQ] = record.highestFreq
            prefs[KEY_REFERENCE_HZ] = record.referencePitchHz
            prefs[KEY_UPDATED_AT] = record.updatedAtEpochMillis
        }
    }

    override suspend fun clear() {
        context.rangeRecordDataStore.edit { it.clear() }
    }

    private fun Preferences.toRangeRecord(): RangeRecord? {
        // 四个字段缺一不可；缺任意一个都视为无记录，而不是用默认值拼一条假记录出来
        val lowest = this[KEY_LOWEST_FREQ] ?: return null
        val highest = this[KEY_HIGHEST_FREQ] ?: return null
        val reference = this[KEY_REFERENCE_HZ] ?: return null
        val updatedAt = this[KEY_UPDATED_AT] ?: return null
        return RangeRecord(lowest, highest, reference, updatedAt)
    }

    private companion object {
        val KEY_LOWEST_FREQ = floatPreferencesKey("lowest_freq")
        val KEY_HIGHEST_FREQ = floatPreferencesKey("highest_freq")
        val KEY_REFERENCE_HZ = doublePreferencesKey("reference_hz")
        val KEY_UPDATED_AT = longPreferencesKey("updated_at")
    }
}
