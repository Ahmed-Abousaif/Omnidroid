package com.omnidroid.lib.core

import android.content.Context
import com.omnidroid.lib.core.assetsmanager.SegaCdAssetsManager
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.SystemID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PicoDriveSupportTest {
    @Test
    fun genesisAndCdKeepGxWhile32xUsesPicoDrive() {
        listOf(SystemID.GENESIS, SystemID.SEGACD).forEach { id ->
            val cores = GameSystem.findById(id.dbname).systemCoreConfigs.map { it.coreID }
            assertEquals(listOf(CoreID.GENESIS_PLUS_GX, CoreID.PICODRIVE), cores)
        }
        val system = GameSystem.findById("32x")
        assertEquals(CoreID.PICODRIVE, system.systemCoreConfigs.single().coreID)
        assertEquals(MetaSystemID.GENESIS, MetaSystemID.fromSystemID(system.id))
        assertEquals(SystemID.SEGA32X, GameSystem.findByUniqueFileExtension("32x")?.id)
    }

    @Test
    fun changingGenesisCorePersistsWithoutChangingCdOr32x() =
        runBlocking {
            val context: Context = RuntimeEnvironment.getApplication()
            val preferences = context.getSharedPreferences("picodrive-selection", Context.MODE_PRIVATE)
            preferences.edit().clear().commit()
            val genesis = GameSystem.findById("md")
            val cd = GameSystem.findById("scd")
            val addon = GameSystem.findById("32x")
            CoresSelection { preferences }.updateCoreConfigForSystem(genesis, CoreID.PICODRIVE)
            val reopened = CoresSelection { preferences }
            assertEquals(CoreID.PICODRIVE, reopened.getCoreConfigForSystem(genesis).coreID)
            assertEquals(CoreID.GENESIS_PLUS_GX, reopened.getCoreConfigForSystem(cd).coreID)
            assertEquals(CoreID.PICODRIVE, reopened.getCoreConfigForSystem(addon).coreID)
        }

    @Test
    fun sharedBiosUsesOneAssetLifecycleAndLock() {
        assertTrue(CoreAssets.hasAssets(CoreID.PICODRIVE))
        assertEquals(CoreAssets.assetCore(CoreID.GENESIS_PLUS_GX), CoreAssets.assetCore(CoreID.PICODRIVE))
        assertTrue(CoreID.getAssetManager(CoreID.PICODRIVE) is SegaCdAssetsManager)
        val cd = GameSystem.findById("scd").systemCoreConfigs.single { it.coreID == CoreID.PICODRIVE }
        assertEquals(setOf("bios_CD_E.bin", "bios_CD_J.bin", "bios_CD_U.bin"), cd.regionalBIOSFiles.values.toSet())
        assertTrue(
            GameSystem
                .findById("32x")
                .systemCoreConfigs
                .single()
                .regionalBIOSFiles
                .isEmpty(),
        )
    }

    @Test
    fun picoDriveUsesSixButtonPortsAndTheCorrectCatalogSlices() {
        val config = GameSystem.findById("32x").systemCoreConfigs.single()
        assertEquals(setOf(0, 1), config.controllerConfigs.keys)
        assertTrue(config.controllerConfigs.values.all { it.single().libretroId == 1 })
        assertEquals("6 button pad", config.defaultSettings.single { it.key == "picodrive_input1" }.value)
        assertEquals("6 button pad", config.defaultSettings.single { it.key == "picodrive_input2" }.value)
        assertFalse(config.supportsLibretroVFS)
        assertEquals(listOf("md", "scd", "32x"), SliceCatalog.forCore(CoreID.PICODRIVE).map { it.id })
    }
}
