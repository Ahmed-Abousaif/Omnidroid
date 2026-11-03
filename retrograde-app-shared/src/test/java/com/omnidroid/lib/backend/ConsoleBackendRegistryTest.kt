package com.omnidroid.lib.backend

import android.content.ContextWrapper
import com.omnidroid.lib.backend.libretro.LibretroBackendDescriptor
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsoleBackendRegistryTest {

    @Before
    fun setUp() {
        ConsoleBackendRegistry.clear()
    }

    @After
    fun tearDown() {
        ConsoleBackendRegistry.clear()
    }

    @Test
    fun registersLibretroDescriptorAndResolvesCores() {
        val descriptor = LibretroBackendDescriptor()
        ConsoleBackendRegistry.registerDescriptor(descriptor)

        // Standard cores map to Libretro
        assertEquals(descriptor, ConsoleBackendRegistry.findDescriptorForCore(CoreID.SNES9X))
        assertEquals(descriptor, ConsoleBackendRegistry.findDescriptorForCore(CoreID.MGBA))
        assertEquals(descriptor, ConsoleBackendRegistry.findDescriptorForCore(CoreID.MUPEN64_PLUS_NEXT))

        // Switch is NOT owned by Libretro
        assertNull(ConsoleBackendRegistry.findDescriptorForCore(CoreID.SWITCH))
        assertFalse(descriptor.ownedCoreIDs.contains(CoreID.SWITCH))
    }

    @Test
    fun resolvesDescriptorBySystemId() {
        val descriptor = LibretroBackendDescriptor()
        ConsoleBackendRegistry.registerDescriptor(descriptor)

        assertEquals(descriptor, ConsoleBackendRegistry.findDescriptorForSystem("snes"))
        assertEquals(descriptor, ConsoleBackendRegistry.findDescriptorForSystem("gba"))
        assertEquals(descriptor, ConsoleBackendRegistry.findDescriptorForSystem("n64"))

        // Switch system does not resolve to Libretro
        assertNull(ConsoleBackendRegistry.findDescriptorForSystem("switch"))
        assertFalse(descriptor.ownedSystemIDs.contains(SystemID.SWITCH))
    }

    @Test
    fun registersSwitchDescriptorAndResolvesSwitchCoreAndSystem() {
        val libretro = LibretroBackendDescriptor()
        val switch = com.omnidroid.lib.backend.switch_backend.SwitchBackendDescriptor()
        ConsoleBackendRegistry.registerDescriptor(libretro)
        ConsoleBackendRegistry.registerDescriptor(switch)

        assertEquals(libretro, ConsoleBackendRegistry.findDescriptorForCore(CoreID.SNES9X))
        assertEquals(switch, ConsoleBackendRegistry.findDescriptorForCore(CoreID.SWITCH))
        assertEquals(switch, ConsoleBackendRegistry.findDescriptorForSystem("switch"))
        assertFalse(switch.capabilities(com.omnidroid.lib.library.SystemCoreConfig(CoreID.SWITCH, hashMapOf())).supportsSaveStates)
        assertTrue(switch.capabilities(com.omnidroid.lib.library.SystemCoreConfig(CoreID.SWITCH, hashMapOf())).supportsCustomTouchLayout)

        val reqs = switch.requirementsFor(com.omnidroid.lib.library.SystemCoreConfig(CoreID.SWITCH, hashMapOf()))
        assertEquals(2, reqs.items.size)
        assertEquals("prod_keys", reqs.items[0].id)
        assertEquals(RequirementSeverity.REQUIRED, reqs.items[0].severity)
        assertEquals("switch_firmware", reqs.items[1].id)
        assertEquals(RequirementSeverity.RECOMMENDED, reqs.items[1].severity)
        assertTrue(reqs.items[1].action is RequirementAction.ArchiveImport)
    }

    @Test
    fun runtimeFactoryRegistrationAndRetrieval() {
        var factoryCalled = false
        val dummyRuntime = object : ConsoleBackendRuntime {
            override suspend fun prepare(
                activity: android.app.Activity,
                game: com.omnidroid.lib.library.db.entity.Game,
                config: com.omnidroid.lib.library.SystemCoreConfig,
            ): Result<Unit> = Result.success(Unit)

            override fun createSession(
                activity: android.app.Activity,
                game: com.omnidroid.lib.library.db.entity.Game,
                config: com.omnidroid.lib.library.SystemCoreConfig,
                container: android.view.ViewGroup,
            ): BackendSession {
                throw UnsupportedOperationException()
            }
        }

        ConsoleBackendRegistry.registerRuntimeFactory(BackendID.LIBRETRO) {
            factoryCalled = true
            dummyRuntime
        }

        val dummyContext = object : ContextWrapper(null) {}
        val runtime = ConsoleBackendRegistry.getRuntime(dummyContext, BackendID.LIBRETRO)
        assertNotNull(runtime)
        assertTrue(factoryCalled)
    }
}
