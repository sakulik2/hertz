package xyz.sakulik.hertz

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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

    fun createPitchRepository() = PitchRepository(
        // 每帧读取快照，使基准音与灵敏度的改动无需重启采集即可生效
        configProvider = { configSnapshot.value },
        // 每次出错时重新查询：权限可能在采集途中被撤销
        hasPermission = {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    )
}

class HertzApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
