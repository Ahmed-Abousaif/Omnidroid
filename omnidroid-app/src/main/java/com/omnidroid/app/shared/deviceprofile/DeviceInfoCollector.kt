package com.omnidroid.app.shared.deviceprofile

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ConfigurationInfo
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import timber.log.Timber
import java.io.File
import java.util.Locale

object DeviceInfoCollector {
    fun collect(context: Context): Triple<DeviceInfo, CpuInfo, GpuInfo> {
        val device = collectDeviceInfo(context)
        val cpu = collectCpuInfo()
        val gpu = collectGpuInfo(context)
        return Triple(device, cpu, gpu)
    }

    private fun collectDeviceInfo(context: Context): DeviceInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val socManufacturer =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else ""
        val socModel =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else ""

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            board = Build.BOARD.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            totalRamBytes = memInfo.totalMem,
            socManufacturer = socManufacturer,
            socModel = socModel,
        )
    }

    private fun collectCpuInfo(): CpuInfo {
        val cpuinfo = readProcCpuinfo()
        val hardwareName =
            cpuinfo["Hardware"]
                ?: cpuinfo["model name"]
                ?: Build.HARDWARE.orEmpty()
        val processorName =
            cpuinfo["model name"]
                ?: cpuinfo["Processor"]
                ?: hardwareName
        val features = cpuinfo["Features"] ?: cpuinfo["flags"] ?: ""

        return CpuInfo(
            cores = Runtime.getRuntime().availableProcessors(),
            hardwareName = hardwareName,
            processorName = processorName,
            features = features,
            frequenciesKHz = readCpuFrequencies(),
        )
    }

    private fun collectGpuInfo(context: Context): GpuInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val configInfo: ConfigurationInfo = am.deviceConfigurationInfo
        val glesRaw = configInfo.reqGlEsVersion
        val glesLabel =
            String.format(
                Locale.US,
                "%d.%d",
                configInfo.reqGlEsVersion shr 16,
                configInfo.reqGlEsVersion and 0xffff,
            )

        val gl = queryGlStrings()
        val pm = context.packageManager
        val vulkanLevel = featureVersion(pm, PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val vulkanVersion = featureVersion(pm, PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        val vulkanComputeLevel = featureVersion(pm, FEATURE_VULKAN_COMPUTE_LEVEL)
        val vulkanSupported =
            vulkanLevel >= 0 ||
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) ||
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)

        return GpuInfo(
            glesVersionRaw = glesRaw,
            glesVersionLabel = glesLabel,
            glVendor = gl.vendor,
            glRenderer = gl.renderer,
            glVersion = gl.version,
            glExtensionsCount = gl.extensionsCount,
            vulkanSupported = vulkanSupported,
            vulkanHardwareLevel = vulkanLevel,
            vulkanHardwareVersion = vulkanVersion,
            vulkanComputeLevel = vulkanComputeLevel,
            vulkanComputeVersion = -1,
        )
    }

    private data class GlStrings(
        val vendor: String,
        val renderer: String,
        val version: String,
        val extensionsCount: Int,
    )

    private fun queryGlStrings(): GlStrings {
        return try {
            queryGlStringsEgl()
        } catch (t: Throwable) {
            Timber.w(t, "Failed to query OpenGL ES strings")
            GlStrings("", "", "", 0)
        }
    }

    private fun queryGlStringsEgl(): GlStrings {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            return GlStrings("", "", "", 0)
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            return GlStrings("", "", "", 0)
        }

        val attribList =
            intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE,
            )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfig = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfig, 0) ||
            numConfig[0] == 0
        ) {
            EGL14.eglTerminate(display)
            return GlStrings("", "", "", 0)
        }

        val config = configs[0]
        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val context =
            EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (context == null || context == EGL14.EGL_NO_CONTEXT) {
            EGL14.eglTerminate(display)
            return GlStrings("", "", "", 0)
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        val surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
        if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            return GlStrings("", "", "", 0)
        }

        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            return GlStrings("", "", "", 0)
        }

        val vendor = GLES20.glGetString(GLES20.GL_VENDOR).orEmpty()
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
        val glVersion = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
        val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty()
        val extensionsCount =
            extensions.split(' ')
                .map { it.trim() }
                .count { it.isNotEmpty() }

        EGL14.eglMakeCurrent(
            display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT,
        )
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)

        return GlStrings(vendor, renderer, glVersion, extensionsCount)
    }

    private fun featureVersion(
        pm: PackageManager,
        name: String,
    ): Int {
        return try {
            val feature = pm.systemAvailableFeatures.firstOrNull { it.name == name }
            feature?.version ?: if (pm.hasSystemFeature(name)) 0 else -1
        } catch (_: Throwable) {
            if (pm.hasSystemFeature(name)) 0 else -1
        }
    }

    private fun readProcCpuinfo(): Map<String, String> {
        return try {
            File("/proc/cpuinfo").useLines { lines ->
                lines.mapNotNull { line ->
                    val idx = line.indexOf(':')
                    if (idx <= 0) return@mapNotNull null
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    if (key.isEmpty()) null else key to value
                }.toMap()
            }
        } catch (t: Throwable) {
            Timber.w(t, "Failed to read /proc/cpuinfo")
            emptyMap()
        }
    }

    private fun readCpuFrequencies(): List<Long> {
        val freqs = mutableListOf<Long>()
        var i = 0
        while (i < 32) {
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq"
            val file = File(path)
            if (!file.exists()) break
            val value = file.readText().trim().toLongOrNull()
            if (value != null) freqs += value
            i++
        }
        return freqs
    }

    // Documented PackageManager feature name (API 28+); not always present on compile stubs.
    private const val FEATURE_VULKAN_COMPUTE_LEVEL = "android.software.vulkan.compute"
}
