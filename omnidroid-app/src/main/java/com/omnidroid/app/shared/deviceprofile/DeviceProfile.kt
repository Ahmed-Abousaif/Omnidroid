package com.omnidroid.app.shared.deviceprofile

import kotlinx.serialization.Serializable

@Serializable
data class DeviceProfile(
    val capturedAtEpochMs: Long = 0L,
    val device: DeviceInfo = DeviceInfo(),
    val cpu: CpuInfo = CpuInfo(),
    val gpu: GpuInfo = GpuInfo(),
    val quickBenchmark: BenchmarkResult? = null,
    val extendedBenchmark: BenchmarkResult? = null,
)

@Serializable
data class DeviceInfo(
    val manufacturer: String = "",
    val model: String = "",
    val brand: String = "",
    val device: String = "",
    val product: String = "",
    val board: String = "",
    val hardware: String = "",
    val androidRelease: String = "",
    val sdkInt: Int = 0,
    val supportedAbis: List<String> = emptyList(),
    val totalRamBytes: Long = 0L,
    val socManufacturer: String = "",
    val socModel: String = "",
)

@Serializable
data class CpuInfo(
    val cores: Int = 0,
    val hardwareName: String = "",
    val processorName: String = "",
    val features: String = "",
    val frequenciesKHz: List<Long> = emptyList(),
)

@Serializable
data class GpuInfo(
    val glesVersionRaw: Int = 0,
    val glesVersionLabel: String = "",
    val glVendor: String = "",
    val glRenderer: String = "",
    val glVersion: String = "",
    val glExtensionsCount: Int = 0,
    val vulkanSupported: Boolean = false,
    val vulkanHardwareLevel: Int = -1,
    val vulkanHardwareVersion: Int = -1,
    val vulkanComputeLevel: Int = -1,
    val vulkanComputeVersion: Int = -1,
)

@Serializable
data class BenchmarkResult(
    val kind: String,
    val completedAtEpochMs: Long,
    val durationMs: Long,
    val iterations: Long,
    /** Higher is better: iterations completed per second. */
    val scoreOpsPerSec: Double,
)
