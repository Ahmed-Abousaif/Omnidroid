package com.omnidroid.lib.backend

import android.content.Context
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.SystemID

/**
 * Lightweight, static metadata descriptor that resides in the base APK.
 * Never references native code, JNI classes, or dynamic split code.
 */
interface ConsoleBackendDescriptor {
    val id: BackendID
    val displayName: String
    val ownedCoreIDs: Set<CoreID>
    val ownedSystemIDs: Set<SystemID>
    val ownedMetaSystems: Set<MetaSystemID>
        get() = ownedSystemIDs.map { MetaSystemID.fromSystemID(it) }.toSet()

    fun capabilities(config: SystemCoreConfig): BackendCapabilities
    fun capabilitiesFor(config: SystemCoreConfig): BackendCapabilities = capabilities(config)
    fun requirementsFor(config: SystemCoreConfig): RequirementDescriptor?
    fun isAvailable(context: Context): Boolean
}
