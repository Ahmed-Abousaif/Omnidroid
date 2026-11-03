package org.yuzu.yuzu_emu.utils

import androidx.annotation.Keep

@Keep
object NativeConfig {
    @Synchronized
    external fun initializeGlobalConfig()

    @Synchronized
    external fun unloadGlobalConfig()

    @Synchronized
    external fun reloadGlobalConfig()

    @Synchronized
    external fun saveGlobalConfig()

    @Synchronized
    external fun getBoolean(key: String, needsGlobal: Boolean = false): Boolean

    @Synchronized
    external fun setBoolean(key: String, value: Boolean)

    @Synchronized
    external fun getInt(key: String, needsGlobal: Boolean = false): Int

    @Synchronized
    external fun setInt(key: String, value: Int)

    @Synchronized
    external fun getString(key: String, needsGlobal: Boolean = false): String

    @Synchronized
    external fun setString(key: String, value: String)
}
