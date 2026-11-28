package com.omnidroid.lib.ra

import android.content.Context
import android.content.SharedPreferences
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RetroAchievementsCoreTest {
    @Test
    fun coreCatalogExcludesSwitchAndPs3AndMapsLibretroConsoles() {
        val switchRes = RaCoreCatalog.resolve(CoreID.SWITCH, SystemID.SWITCH)
        assertFalse(switchRes.supported)

        val ps3Res = RaCoreCatalog.resolve(CoreID.ARMSX3, SystemID.PS3)
        assertFalse(ps3Res.supported)

        val nesRes = RaCoreCatalog.resolve(CoreID.FCEUMM, SystemID.NES)
        assertTrue(nesRes.supported)
        assertEquals(7, nesRes.consoleId)
        assertEquals("FCEUmm", nesRes.libraryName)

        val snesRes = RaCoreCatalog.resolve(CoreID.SNES9X, SystemID.SNES)
        assertTrue(snesRes.supported)
        assertEquals(3, snesRes.consoleId)

        val genRes = RaCoreCatalog.resolve(CoreID.GENESIS_PLUS_GX, SystemID.GENESIS)
        assertTrue(genRes.supported)
        assertEquals(1, genRes.consoleId)

        val gbaRes = RaCoreCatalog.resolve(CoreID.MGBA, SystemID.GBA)
        assertTrue(gbaRes.supported)
        assertEquals(5, gbaRes.consoleId)

        val psxRes = RaCoreCatalog.resolve(CoreID.PCSX_REARMED, SystemID.PSX)
        assertTrue(psxRes.supported)
        assertEquals(12, psxRes.consoleId)

        val pceRes = RaCoreCatalog.resolve(CoreID.MEDNAFEN_PCE_FAST, SystemID.PC_ENGINE)
        assertTrue(pceRes.supported)
        assertEquals(8, pceRes.consoleId)
    }

    @Test
    fun hardcorePolicyBlocksForbiddenFeaturesOnlyWhenActiveAndLoaded() {
        val loadedEngine =
            object : FakeRaEngine(loaded = true) {
                var pauseCooldown = 0

                override fun isSettingAllowed(
                    libraryName: String?,
                    consoleId: Int,
                    settingKey: String,
                    settingValue: String,
                ): Boolean = !(settingKey == "fceumm_overclocking" && settingValue == "2x-Postrender")

                override fun canPause(): RaPauseDecision =
                    if (pauseCooldown > 0) RaPauseDecision.Cooldown(pauseCooldown) else RaPauseDecision.Allowed
            }

        val policy = HardcorePolicy(loadedEngine)
        policy.updateSession(
            moduleLoaded = true,
            loggedIn = true,
            raEnabled = true,
            hardcoreRequested = true,
            gameLoaded = true,
            coreSupported = true,
        )

        assertTrue(policy.isHardcoreActive)
        assertEquals(HardcoreDecision.Allowed, policy.canSaveState())
        assertTrue(policy.canLoadSaveState(isAutoSaveOnLaunch = false) is HardcoreDecision.Blocked)
        assertEquals(
            HardcoreBlockReason.LOAD_AUTO_SAVE_ON_LAUNCH,
            (policy.canLoadSaveState(isAutoSaveOnLaunch = true) as HardcoreDecision.Blocked).reason,
        )
        assertTrue(policy.canEnableRewind() is HardcoreDecision.Blocked)
        assertTrue(policy.canEnableSlowMotion() is HardcoreDecision.Blocked)
        assertTrue(policy.canSetFrameSpeedMultiplier(0.5f) is HardcoreDecision.Blocked)
        assertEquals(HardcoreDecision.Allowed, policy.canSetFrameSpeedMultiplier(2.0f))
        assertEquals(HardcoreDecision.Allowed, policy.canEnableFastForward())
        assertTrue(policy.canUseCheats() is HardcoreDecision.Blocked)
        assertTrue(policy.canEditMemory() is HardcoreDecision.Blocked)
        assertTrue(
            policy.canApplyCoreOption("FCEUmm", 7, "fceumm_overclocking", "2x-Postrender") is HardcoreDecision.Blocked,
        )
        assertEquals(
            HardcoreDecision.Allowed,
            policy.canApplyCoreOption("FCEUmm", 7, "fceumm_overclocking", "disabled"),
        )

        loadedEngine.pauseCooldown = 180
        assertEquals(RaPauseDecision.Cooldown(180), policy.canPauseGame())

        // Demoting to Softcore unlocks everything
        policy.updateSession(hardcoreRequested = false)
        assertFalse(policy.isHardcoreActive)
        assertEquals(HardcoreDecision.Allowed, policy.canLoadSaveState(false))
        assertEquals(HardcoreDecision.Allowed, policy.canEnableRewind())
        assertEquals(HardcoreDecision.Allowed, policy.canSetFrameSpeedMultiplier(0.5f))
        assertEquals(RaPauseDecision.Allowed, policy.canPauseGame())

        // Unloaded / Noop module never enforces Hardcore even if requested=true
        val noopPolicy = HardcorePolicy(NoopRetroAchievementsEngine())
        noopPolicy.updateSession(
            moduleLoaded = true,
            loggedIn = true,
            raEnabled = true,
            hardcoreRequested = true,
            gameLoaded = true,
            coreSupported = true,
        )
        assertFalse(noopPolicy.isHardcoreActive)
        assertEquals(HardcoreDecision.Allowed, noopPolicy.canLoadSaveState(true))
    }

    @Test
    fun saveStateProgressCodecPreservesExactCoreStateAndHandlesLegacyAndCorruptBlobs() {
        val coreState = byteArrayOf(0x01, 0x02, 0x03, 0x7F, 0x55)
        val raProgress = "RCPR_MOCK_PROGRESS_DATA".encodeToByteArray()

        val wrapped = RaSaveStateProgressCodec.wrap(coreState, raProgress)
        val unwrapped = RaSaveStateProgressCodec.unwrap(wrapped)
        assertArrayEquals(coreState, unwrapped.coreState)
        assertArrayEquals(raProgress, unwrapped.raProgress)

        val unwrappedLegacy = RaSaveStateProgressCodec.unwrap(coreState)
        assertArrayEquals(coreState, unwrappedLegacy.coreState)
        assertNull(unwrappedLegacy.raProgress)

        assertArrayEquals(coreState, RaSaveStateProgressCodec.wrap(coreState, null))
        assertArrayEquals(coreState, RaSaveStateProgressCodec.wrap(coreState, ByteArray(0)))

        val corrupted = wrapped.copyOf().apply { this[size - 1] = 0x00 }
        val safeCorrupt = RaSaveStateProgressCodec.unwrap(corrupted)
        assertNull(safeCorrupt.raProgress)
    }

    @Test
    fun credentialStoreEncryptsTokenAndNeverStoresRawPassword() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs: SharedPreferences = context.getSharedPreferences("ra_cred_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val xorCipher =
            object : RaTokenCipher {
                override fun encrypt(plaintext: ByteArray): ByteArray =
                    plaintext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

                override fun decrypt(ciphertext: ByteArray): ByteArray =
                    ciphertext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
            }

        val store = RaCredentialStore(prefs, xorCipher)
        store.saveToken("RetroPlayer", "ra_web_token_xyz")

        assertTrue(store.hasCredentials())
        assertEquals("RetroPlayer", store.getSavedUsername())
        assertEquals(RaStoredCredentials("RetroPlayer", "ra_web_token_xyz"), store.loadToken())

        val allValues = prefs.all.values.joinToString("|") { it.toString() }
        assertFalse(allValues.contains("ra_web_token_xyz"))
        assertFalse(prefs.all.keys.any { it.contains("password", ignoreCase = true) })

        store.clear()
        assertFalse(store.hasCredentials())
        assertNull(store.loadToken())
    }

    private open class FakeRaEngine(
        private val loaded: Boolean,
    ) : RetroAchievementsEngine by NoopRetroAchievementsEngine() {
        override fun isModuleLoaded(): Boolean = loaded
    }
}
