package com.omnidroid.lib.graphics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object VulkanDetector {

    /**
     * Checks if the device has Vulkan hardware support.
     * Vulkan was introduced in Android 7.0 (API 24) and standardized in API 28+.
     */
    fun isVulkanSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        val packageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
    }

    /**
     * Returns the Vulkan hardware level (0 = basic, 1 = full) if supported, or -1 if unsupported.
     */
    fun getVulkanHardwareLevel(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return -1
        }
        val packageManager = context.packageManager
        val features = packageManager.systemAvailableFeatures
        for (feature in features) {
            if (feature.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) {
                return feature.version
            }
        }
        return if (packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) 0 else -1
    }

    /**
     * Returns the Vulkan API version formatted string (e.g. "1.1.0", "1.3.0") or null.
     */
    fun getVulkanVersionString(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return null
        }
        val packageManager = context.packageManager
        val features = packageManager.systemAvailableFeatures
        for (feature in features) {
            if (feature.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION) {
                val version = feature.version
                val major = version shr 22
                val minor = (version shr 12) and 0x3ff
                val patch = version and 0xfff
                return "$major.$minor.$patch"
            }
        }
        return null
    }
}
