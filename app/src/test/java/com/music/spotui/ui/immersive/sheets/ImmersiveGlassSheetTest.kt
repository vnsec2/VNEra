package com.music.spotui.ui.immersive.sheets

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-port-xhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalMaterial3Api::class)
class ImmersiveGlassSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun backGestureSlidesDownWithoutShrinkingAndCanCancelOrComplete() {
        var dismissals = 0
        rule.setContent {
            MaterialTheme {
                var open by remember { mutableStateOf(true) }
                if (open) {
                    ImmersiveGlassSheet(
                        onDismiss = { dismissals++; open = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    ) {
                        Box(Modifier.fillMaxWidth().height(240.dp).testTag("sheet-content"))
                    }
                }
            }
        }
        rule.waitForIdle()
        val dispatcher = (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher
        fun bounds() = rule.onNodeWithTag("sheet-content").fetchSemanticsNode().boundsInRoot
        val initial = bounds()

        fun preview() {
            rule.runOnIdle {
                dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
            }
            rule.runOnIdle {
                dispatcher.dispatchOnBackProgressed(BackEventCompat(80f, 300f, 0.25f, BackEventCompat.EDGE_LEFT))
            }
            rule.waitForIdle()
        }

        preview()
        val during = bounds()
        assertTrue("The preview should move the sheet down", during.top > initial.top + 10f)
        assertEquals("The preview should keep the sheet's width", initial.width, during.width, 1f)
        assertEquals(0, dismissals)

        rule.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        rule.waitForIdle()
        assertEquals(initial.top, bounds().top, 1f)
        assertEquals(0, dismissals)

        preview()
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("sheet-content").assertDoesNotExist()
        assertEquals(1, dismissals)
    }
}
