package xyz.sakulik.hertz

import android.app.Application
import xyz.sakulik.hertz.data.ConfigSnapshot
import xyz.sakulik.hertz.data.DataStoreRangeRecordStore
import xyz.sakulik.hertz.data.DataStoreSettingsStore
import xyz.sakulik.hertz.data.PitchRepository
import xyz.sakulik.hertz.data.RangeRecordStore
import xyz.sakulik.hertz.data.SettingsStore

/**
 * 极简的依赖容器。项目没有引入 DI 框架 —— 依赖只有这几个，
 * 引框架的代价大于收益。
 *
 * 之所以要有它：设置页与调音页必须共享同一个 [SettingsStore] 与 [ConfigSnapshot]，
 * 否则改了设置不会作用到正在跑的音高检测上。
 */
class AppContainer(application: Application) {
    private val context = application.applicationContext

    val settingsStore: SettingsStore = DataStoreSettingsStore(context)
    val recordStore: RangeRecordStore = DataStoreRangeRecordStore(context)

    /** 音频线程每帧同步读取的设置快照，由 PitchViewModel 写入。 */
    val configSnapshot = ConfigSnapshot()

    /** 每帧读取快照，使基准音与灵敏度的改动无需重启采集即可生效。 */
    fun createPitchRepository() = PitchRepository { configSnapshot.value }
}

class HertzApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
