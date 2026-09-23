package xyz.sakulik.hertz.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import xyz.sakulik.hertz.R
import xyz.sakulik.hertz.data.Note
import xyz.sakulik.hertz.data.PitchError
import xyz.sakulik.hertz.data.PitchResult
import xyz.sakulik.hertz.data.PitchSource

/**
 * Drives [PitchScreen] against a fake source so the lifecycle and control wiring can be
 * exercised on-device without depending on real microphone input.
 */
class PitchScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private class FakePitchSource : PitchSource {
        val emissions = MutableSharedFlow<PitchResult>(extraBufferCapacity = 16)
        override val pitchFlow: Flow<PitchResult> = emissions
        var startCount = 0
        var stopCount = 0
        override fun startListening() { startCount++ }
        override fun stopListening() { stopCount++ }
    }

    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    @Test fun startsCaptureOnceTheScreenResumes() {
        val source = FakePitchSource()
        composeTestRule.setContent { PitchScreen(PitchViewModel.forTesting(source)) }
        composeTestRule.waitForIdle()

        // 屏幕进入 RESUMED 后应自动开始采集，因此按钮显示"暂停"
        composeTestRule.onNodeWithText(string(R.string.btn_pause)).assertIsDisplayed()
        assertEquals(1, source.startCount)
    }

    @Test fun userPauseStopsCaptureAndTogglesButton() {
        val source = FakePitchSource()
        composeTestRule.setContent { PitchScreen(PitchViewModel.forTesting(source)) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.btn_pause)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.btn_start)).assertIsDisplayed()
        assertEquals(1, source.stopCount)
    }

    @Test fun showsPlaceholdersBeforeAnyPitchArrives() {
        composeTestRule.setContent { PitchScreen(PitchViewModel.forTesting(FakePitchSource())) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("--").assertIsDisplayed()
        composeTestRule.onNodeWithText("-- Hz").assertIsDisplayed()
    }

    @Test fun rendersDetectedNoteAndFrequency() {
        val source = FakePitchSource()
        composeTestRule.setContent { PitchScreen(PitchViewModel.forTesting(source)) }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            source.emissions.tryEmit(
                PitchResult.Detected(
                    frequencyHz = 440f,
                    note = Note(69),
                    centsDeviation = 0f,
                    probability = 0.95f
                )
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("A4").assertIsDisplayed()
        composeTestRule.onNodeWithText("440.0 Hz").assertIsDisplayed()
    }

    @Test fun surfacesErrorStateWithRetryAction() {
        val source = FakePitchSource()
        composeTestRule.setContent { PitchScreen(PitchViewModel.forTesting(source)) }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            source.emissions.tryEmit(PitchResult.Error(PitchError.DeviceBusy))
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.mic_error_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.mic_error_busy)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.btn_retry)).assertIsDisplayed()
    }

    @Test fun offersSystemSettingsRatherThanRetryWhenPermissionIsRevoked() {
        val source = FakePitchSource()
        composeTestRule.setContent { PitchScreen(PitchViewModel.forTesting(source)) }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            source.emissions.tryEmit(PitchResult.Error(PitchError.PermissionRevoked))
        }
        composeTestRule.waitForIdle()

        // 权限被撤销时重试毫无意义，应引导用户去系统设置
        composeTestRule.onNodeWithText(string(R.string.mic_error_permission)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.btn_open_settings)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.btn_retry)).assertDoesNotExist()
    }

    @Test fun offersNoActionWhenTheRecorderCannotInitialize() {
        val source = FakePitchSource()
        composeTestRule.setContent { PitchScreen(PitchViewModel.forTesting(source)) }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            source.emissions.tryEmit(PitchResult.Error(PitchError.InitFailed))
        }
        composeTestRule.waitForIdle()

        // 硬件初始化失败，重试也不会成功，因此不给按钮而只说明原因
        composeTestRule.onNodeWithText(string(R.string.mic_error_init)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.btn_retry)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.btn_open_settings)).assertDoesNotExist()
    }
}
