package com.omnidroid.lib.backend

import android.content.Context
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.SystemID

/**
 * Global registry for console backend descriptors and dynamic runtime factories.
 * All backend resolution routes through here without hardcoded type branches.
 */
object ConsoleBackendRegistry {
    private val descriptors = mutableListOf<ConsoleBackendDescriptor>()
    private val descriptorsByCore = mutableMapOf<CoreID, ConsoleBackendDescriptor>()
    private val descriptorsBySystem = mutableMapOf<SystemID, ConsoleBackendDescriptor>()
    private val runtimeFactories = mutableMapOf<BackendID, (Context) -> ConsoleBackendRuntime>()

    @Synchronized
    fun registerDescriptor(descriptor: ConsoleBackendDescriptor) {
        descriptors.removeAll { it.id == descriptor.id }
        descriptors.add(descriptor)
        for (core in descriptor.ownedCoreIDs) {
            descriptorsByCore[core] = descriptor
        }
        for (system in descriptor.ownedSystemIDs) {
            descriptorsBySystem[system] = descriptor
        }
    }

    @Synchronized
    fun registerRuntimeFactory(id: BackendID, factory: (Context) -> ConsoleBackendRuntime) {
        runtimeFactories[id] = factory
    }

    fun findDescriptorForCore(coreID: CoreID): ConsoleBackendDescriptor? {
        return descriptorsByCore[coreID]
    }

    fun findDescriptorForSystem(systemId: String): ConsoleBackendDescriptor? {
        val system = try {
            GameSystem.findById(systemId)
        } catch (e: Exception) {
            null
        } ?: return null
        return descriptorsBySystem[system.id]
    }

    fun getRuntime(context: Context, id: BackendID): ConsoleBackendRuntime? {
        return runtimeFactories[id]?.invoke(context)
    }

    fun allDescriptors(): List<ConsoleBackendDescriptor> = descriptors.toList()

    @Synchronized
    fun clear() {
        descriptors.clear()
        descriptorsByCore.clear()
        descriptorsBySystem.clear()
        runtimeFactories.clear()
    }
}
