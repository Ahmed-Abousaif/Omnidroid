package com.omnidroid.app.shared.game.viewmodel

import com.omnidroid.lib.core.CoreVariable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererSettingsGuardTest {
    private val gl = CoreVariable("citra_graphics_api", "OpenGL")
    private val vk = CoreVariable("citra_graphics_api", "Vulkan")

    @Test
    fun rendererChangeRetainsRunningBackendAndAppliesOtherSettings() {
        val guard = RendererSettingsGuard(listOf(gl))
        val layout = CoreVariable("citra_layout_option", "Single Screen Only")
        val update = guard.filter(listOf(vk, layout))
        assertEquals(listOf(gl, layout), update.applicable)
        assertTrue(update.notifyDeferredChange)
    }

    @Test
    fun vulkanToGlAlsoWaitsForNextLaunch() {
        val update = RendererSettingsGuard(listOf(vk)).filter(listOf(gl))
        assertEquals(listOf(vk), update.applicable)
        assertTrue(update.notifyDeferredChange)
    }

    @Test
    fun defaultRendererCannotBeChangedByIntroducingANewOption() {
        val update = RendererSettingsGuard(emptyList()).filter(listOf(vk))
        assertTrue(update.applicable.isEmpty())
        assertTrue(update.notifyDeferredChange)
    }

    @Test
    fun repeatedRefreshAndPartialLayoutUpdatesDoNotRepeatTheNotice() {
        val guard = RendererSettingsGuard(listOf(gl))
        assertTrue(guard.filter(listOf(vk)).notifyDeferredChange)
        assertFalse(guard.filter(listOf(CoreVariable("layout", "single"))).notifyDeferredChange)
        assertFalse(guard.filter(listOf(vk)).notifyDeferredChange)
    }

    @Test
    fun restoringRunningSettingCancelsDeferralAndAllowsANewNoticeLater() {
        val guard = RendererSettingsGuard(listOf(gl))
        guard.filter(listOf(vk))
        val restored = guard.filter(listOf(gl))
        assertEquals(listOf(gl), restored.applicable)
        assertFalse(restored.notifyDeferredChange)
        assertTrue(guard.filter(listOf(vk)).notifyDeferredChange)
    }

    @Test
    fun aliasOptionsCannotSneakADifferentBackendIntoTheCore() {
        val launch = CoreVariable("ppsspp_rendering_backend", "OpenGL")
        val update = RendererSettingsGuard(listOf(launch)).filter(
            listOf(CoreVariable(launch.key, "Vulkan"), CoreVariable("ppsspp_gpu_backend", "vulkan")),
        )
        assertEquals(listOf(launch), update.applicable)
        assertTrue(update.notifyDeferredChange)
    }

    @Test
    fun softwareBackendChangeIsDeferredEvenThoughItUsesTheSameViewType() {
        val launch = CoreVariable("armsx2_renderer", "opengl")
        val update = RendererSettingsGuard(listOf(launch)).filter(listOf(CoreVariable(launch.key, "software")))
        assertEquals(listOf(launch), update.applicable)
        assertTrue(update.notifyDeferredChange)
    }

    @Test
    fun newSessionStartsWithPersistedBackendWithoutDeferringIt() {
        val update = RendererSettingsGuard(listOf(vk)).filter(listOf(vk))
        assertEquals(listOf(vk), update.applicable)
        assertFalse(update.notifyDeferredChange)
        assertTrue(RendererSettingsGuard.requestsVulkan(listOf(vk)))
        assertFalse(RendererSettingsGuard.requestsVulkan(listOf(gl)))
    }
}
