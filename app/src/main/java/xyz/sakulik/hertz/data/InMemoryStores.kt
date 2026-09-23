package xyz.sakulik.hertz.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/*
 * 内存实现。放在 main 而非 test 源集，是为了让 JVM 单元测试、仪器测试和
 * Compose 预览共用同一份，不必各写一遍。
 */

class InMemorySettingsStore(initial: TunerConfig = TunerConfig.Default) : SettingsStore {
    private val state = MutableStateFlow(initial)
    override val config: Flow<TunerConfig> = state.asStateFlow()

    override suspend fun update(transform: (TunerConfig) -> TunerConfig) {
        state.update(transform)
    }
}

class InMemoryRangeRecordStore(initial: RangeRecord? = null) : RangeRecordStore {
    private val state = MutableStateFlow(initial)
    override val record: Flow<RangeRecord?> = state.asStateFlow()

    /** 便于测试断言写盘次数。 */
    var saveCount: Int = 0
        private set

    override suspend fun save(record: RangeRecord) {
        saveCount++
        state.value = record
    }

    override suspend fun clear() {
        state.value = null
    }
}
