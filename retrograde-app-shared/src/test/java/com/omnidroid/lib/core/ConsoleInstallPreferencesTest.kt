package com.omnidroid.lib.core

import android.content.Context
import com.omnidroid.lib.library.MetaSystemID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ConsoleInstallPreferencesTest {
    @Test fun uninstallChoicePersistsAndOnlyReinstallingThatConsoleClearsIt() {
        val context: Context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("console-install-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val first = ConsoleInstallPreferences(preferences)
        first.setUninstalled(MetaSystemID.GAMECUBE, true)
        first.setUninstalled(MetaSystemID.PSP, true)
        val reopened = ConsoleInstallPreferences(preferences)
        assertEquals(setOf(MetaSystemID.GAMECUBE, MetaSystemID.PSP), reopened.uninstalled())
        reopened.setUninstalled(MetaSystemID.GAMECUBE, false)
        assertEquals(setOf(MetaSystemID.PSP), first.uninstalled())
    }
}
