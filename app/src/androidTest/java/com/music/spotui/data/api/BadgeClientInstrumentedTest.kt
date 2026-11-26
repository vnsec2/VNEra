package com.music.spotui.data.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BadgeClientInstrumentedTest {
    @Test
    fun fetchesAndVerifiesLiveVpsServerAndSwitchesLauncherIcons() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("https://gta.caruno.wtf:8788", BadgeClient.baseUrl(context))

        val state = BadgeClient.fetchUser(context, "emu_probe")
        assertNotNull("Expected verified response from live VPS badge server", state)
        assertTrue(state!!.perks)
        assertEquals(1, state.badges.size)
        assertEquals("VPS Live", state.badges[0].label)
        assertEquals("#1ED760", state.badges[0].colorHex)

        SupportReminder.setServerPerks(context, true)
        SupportReminder.setIcon(context, SupportReminder.Accent.BLUE)
        assertEquals(SupportReminder.Accent.BLUE, SupportReminder.icon)
        SupportReminder.setIcon(context, SupportReminder.Accent.GREEN)
        assertEquals(SupportReminder.Accent.GREEN, SupportReminder.icon)
        SupportReminder.setServerPerks(context, false)
    }
}
