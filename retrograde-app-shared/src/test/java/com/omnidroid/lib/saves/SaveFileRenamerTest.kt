package com.omnidroid.lib.saves

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.omnidroid.lib.R
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.storage.DirectoriesManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SaveFileRenamerTest {
    @Test
    fun renamesSaveRamAndStates() {
        val root = createTempDirectory("save-rename").toFile()
        try {
            val saves = File(root, "saves").also { it.mkdirs() }
            val states = File(root, "states").also { it.mkdirs() }
            val statesCore = File(states, "core").also { it.mkdirs() }
            val previews = File(root, "previews").also { it.mkdirs() }
            val previewsCore = File(previews, "core").also { it.mkdirs() }
            File(saves, "Old Game.srm").writeText("sram")
            File(statesCore, "Old Game.nes.state").writeText("state")
            File(statesCore, "Old Game.nes.state.metadata").writeText("{}")
            File(statesCore, "Old Game.nes.slot1").writeText("slot")
            File(previewsCore, "Old Game.nes.slot1.jpg").writeText("jpg")

            assertTrue(
                SaveFileRenamer.renameArtifacts(
                    saves,
                    states,
                    previews,
                    "Old Game.nes",
                    "New Game.nes",
                ),
            )
            assertTrue(File(saves, "New Game.srm").exists())
            assertFalse(File(saves, "Old Game.srm").exists())
            assertTrue(File(statesCore, "New Game.nes.state").exists())
            assertTrue(File(statesCore, "New Game.nes.slot1").exists())
            assertTrue(File(previewsCore, "New Game.nes.slot1.jpg").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesCollision() {
        val root = createTempDirectory("save-collide").toFile()
        try {
            val saves = File(root, "saves").also { it.mkdirs() }
            File(saves, "Old.srm").writeText("a")
            File(saves, "New.srm").writeText("b")
            assertFalse(
                SaveFileRenamer.renameArtifacts(
                    saves,
                    File(root, "states").also { it.mkdirs() },
                    File(root, "previews").also { it.mkdirs() },
                    "Old.nes",
                    "New.nes",
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun renamesConsoleScopedCustomFolderArtifactsWithoutTouchingOtherConsoles() {
        val root = createTempDirectory("save-rename-custom").toFile()
        try {
            val externalDir = File(root, "external").also { it.mkdirs() }
            val customRoot = File(root, "custom").also { it.mkdirs() }
            val appCtx = ApplicationProvider.getApplicationContext<Context>()
            val context =
                object : ContextWrapper(appCtx) {
                    override fun getExternalFilesDir(type: String?): File = externalDir
                }
            val key =
                runCatching { context.getString(R.string.pref_key_custom_saves_folder) }
                    .getOrDefault(CustomSaveStorage.PREF_KEY_CUSTOM_SAVES_FOLDER)
            SharedPreferencesHelper
                .getLegacySharedPreferences(context)
                .edit()
                .clear()
                .putString(key, customRoot.absolutePath)
                .commit()

            val directories = DirectoriesManager(context)
            val customStorage = CustomSaveStorage(context, directories)

            val gbaSave =
                File(customRoot, "saves/GBA/Tetris.srm").apply {
                    parentFile!!.mkdirs()
                    writeText("gba")
                }
            val nesSave =
                File(customRoot, "saves/NES/Tetris.srm").apply {
                    parentFile!!.mkdirs()
                    writeText("nes")
                }
            val gbaState =
                File(customRoot, "states/GBA/mgba/Tetris.gba.slot1").apply {
                    parentFile!!.mkdirs()
                    writeText("slot")
                }
            val gbaPreview =
                File(customRoot, "state-previews/GBA/mgba/Tetris.gba.slot1.jpg").apply {
                    parentFile!!.mkdirs()
                    writeText("jpg")
                }

            assertTrue(
                SaveFileRenamer.renameArtifacts(
                    directories.getSavesDirectory(),
                    directories.getStatesDirectory(),
                    directories.getStatesPreviewDirectory(),
                    "Tetris.gba",
                    "Tetris Advance.gba",
                    customStorage,
                    systemId = "gba",
                ),
            )

            assertFalse(gbaSave.exists())
            assertTrue(File(customRoot, "saves/GBA/Tetris Advance.srm").exists())
            assertTrue(nesSave.exists())
            assertFalse(gbaState.exists())
            assertTrue(File(customRoot, "states/GBA/mgba/Tetris Advance.gba.slot1").exists())
            assertFalse(gbaPreview.exists())
            assertTrue(File(customRoot, "state-previews/GBA/mgba/Tetris Advance.gba.slot1.jpg").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
