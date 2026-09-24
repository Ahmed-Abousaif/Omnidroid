package com.omnidroid.app.mobile.feature.settings.graphicsapi

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.lib.core.CoreVariablesManager
import com.omnidroid.lib.graphics.VulkanDetector
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.SystemID
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GraphicsApiSelectionViewModel(
    private val context: Context,
    private val coreVariablesManager: CoreVariablesManager,
) : ViewModel() {

    class Factory(
        private val context: Context,
        private val coreVariablesManager: CoreVariablesManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GraphicsApiSelectionViewModel(context, coreVariablesManager) as T
        }
    }

    private data class GraphicsApiSystem(
        val systemId: SystemID,
        val variableKey: String,
        val options: List<String>,
        val defaultValue: String,
    )

    data class SystemGraphicsApiConfig(
        val system: GameSystem,
        val variableKey: String,
        val options: List<String>,
        val currentSelection: String,
    )

    private val _systemConfigs = MutableStateFlow<List<SystemGraphicsApiConfig>>(emptyList())
    val systemConfigs: StateFlow<List<SystemGraphicsApiConfig>> = _systemConfigs.asStateFlow()

    val isVulkanSupported: Boolean = VulkanDetector.isVulkanSupported(context)
    val vulkanVersion: String? = VulkanDetector.getVulkanVersionString(context)

    init {
        loadConfigurations()
    }

    fun loadConfigurations() {
        viewModelScope.launch {
            val supportedSystems = listOf(
                GraphicsApiSystem(SystemID.NINTENDO_3DS, "citra_graphics_api", listOf("OpenGL", "Vulkan"), "OpenGL"),
                GraphicsApiSystem(SystemID.GAMECUBE, "dolphin_graphics_api", listOf("OpenGL", "Vulkan"), "OpenGL"),
                GraphicsApiSystem(SystemID.WII, "dolphin_graphics_api", listOf("OpenGL", "Vulkan"), "OpenGL"),
                GraphicsApiSystem(SystemID.PSP, "ppsspp_rendering_backend", listOf("OpenGL", "Vulkan"), "OpenGL"),
                GraphicsApiSystem(SystemID.PS2, "pcsx2_renderer", listOf("opengl", "vulkan", "software"), "opengl"),
            )

            val prefs = SharedPreferencesHelper.getSharedPreferences(context)
            val list = mutableListOf<SystemGraphicsApiConfig>()

            for (entry in supportedSystems) {
                val gameSystem = GameSystem.findById(entry.systemId.dbname) ?: continue
                val prefKey = CoreVariablesManager.computeSharedPreferenceKey(entry.variableKey, entry.systemId.dbname)
                val saved = prefs.getString(prefKey, entry.defaultValue) ?: entry.defaultValue
                val currentValue = saved.takeIf { it in entry.options } ?: entry.defaultValue

                list.add(
                    SystemGraphicsApiConfig(
                        system = gameSystem,
                        variableKey = entry.variableKey,
                        options = entry.options,
                        currentSelection = currentValue,
                    )
                )
            }
            _systemConfigs.value = list
        }
    }

    fun setGraphicsApi(systemId: SystemID, variableKey: String, value: String) {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        val prefKey = CoreVariablesManager.computeSharedPreferenceKey(variableKey, systemId.dbname)
        prefs.edit().putString(prefKey, value).apply()
        loadConfigurations()
    }
}
