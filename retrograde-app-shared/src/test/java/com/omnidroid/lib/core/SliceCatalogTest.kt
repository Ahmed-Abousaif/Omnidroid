package com.omnidroid.lib.core

import com.omnidroid.lib.library.GameSystem
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SliceCatalogTest {
    @Test
    fun installedSliceWithTheSameChecksumIsNotFetchedAgain() {
        assertTrue(
            !SliceCatalog.needsInstall("abc", SliceCatalog.SCHEMA_VERSION, "abc", SliceCatalog.SCHEMA_VERSION),
        )
        assertTrue(SliceCatalog.needsInstall("abc", SliceCatalog.SCHEMA_VERSION, "def", SliceCatalog.SCHEMA_VERSION))
        assertTrue(!SliceCatalog.needsInstall(null, null, "abc", 99))
    }

    @Test
    fun everyConsoleCoreHasASlice() {
        val coresRoot = File("../omnidroid-cores")
        if (!coresRoot.exists()) return
        GameSystem.all().forEach { system ->
            val slice = SliceCatalog.slices.firstOrNull { system.id.dbname in it.systems } ?: return@forEach
            system.systemCoreConfigs.forEach { config ->
                assertTrue(
                    "${system.id.dbname} missing from ${config.coreID.coreName}",
                    config.coreID.coreName in slice.cores,
                )
                val manifest =
                    File(
                        coresRoot,
                        "omnidroid_core_${config.coreID.coreName}/src/main/assets/libretro-db/${config.coreID.coreName}/manifest.json",
                    )
                if (manifest.exists()) {
                    assertTrue(manifest.readText().contains("\"sliceId\":\"${slice.id}\""))
                }
            }
        }
    }
}
