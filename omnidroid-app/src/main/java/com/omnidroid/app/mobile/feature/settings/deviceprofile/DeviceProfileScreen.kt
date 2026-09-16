package com.omnidroid.app.mobile.feature.settings.deviceprofile

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.HomeChromeBackground
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.app.shared.deviceprofile.BenchmarkResult
import com.omnidroid.app.shared.deviceprofile.CpuInfo
import com.omnidroid.app.shared.deviceprofile.DeviceInfo
import com.omnidroid.app.shared.deviceprofile.DeviceProfile
import com.omnidroid.app.shared.deviceprofile.GpuInfo
import java.util.Locale

private val PanelColor = Color(0xFF1A1A1A)
private val PanelBorder = Color.White.copy(alpha = 0.08f)
private val MutedText = Color.White.copy(alpha = 0.55f)
private val ValueText = Color.White.copy(alpha = 0.92f)
private val PanelShape = RoundedCornerShape(12.dp)
private val ChipShape = RoundedCornerShape(6.dp)

@Composable
fun DeviceProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: DeviceProfileViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    val profile = state.profile
    val context = LocalContext.current

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(HomeChromeBackground),
    ) {
        if (profile == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = LibraryNeonGreen)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_device_profile_loading),
                    color = MutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            DeviceProfileContent(
                profile = profile,
                context = context,
                running = state.runningExtended,
                onRunExtended = { viewModel.runExtendedBenchmark() },
            )
        }
    }

    if (state.runningExtended) {
        ExtendedBenchmarkProgressDialog(progress = state.extendedProgress)
    }
}

@Composable
private fun DeviceProfileContent(
    profile: DeviceProfile,
    context: android.content.Context,
    running: Boolean,
    onRunExtended: () -> Unit,
) {
    val device = profile.device
    val ramLabel =
        remember(device.totalRamBytes) {
            Formatter.formatShortFileSize(context, device.totalRamBytes)
        }
    val socLabel =
        remember(device.socManufacturer, device.socModel) {
            listOf(device.socManufacturer, device.socModel)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DeviceHeader(
            device = device,
            ramLabel = ramLabel,
            socLabel = socLabel,
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CpuPanel(
                cpu = profile.cpu,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            GpuPanel(
                gpu = profile.gpu,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        BenchmarkPanel(
            quick = profile.quickBenchmark,
            extended = profile.extendedBenchmark,
            running = running,
            onRunExtended = onRunExtended,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceHeader(
    device: DeviceInfo,
    ramLabel: String,
    socLabel: String,
) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(LibraryNeonGreen.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhoneAndroid,
                    contentDescription = null,
                    tint = LibraryNeonGreen,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.model.ifBlank { stringResource(R.string.settings_title_device_profile) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        listOf(device.manufacturer, device.brand)
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinctBy { it.lowercase(Locale.US) }
                            .joinToString(" · ")
                            .ifBlank { device.device },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetaChip("Android ${device.androidRelease}")
                    MetaChip("API ${device.sdkInt}")
                    MetaChip(ramLabel)
                    if (device.supportedAbis.isNotEmpty()) {
                        MetaChip(device.supportedAbis.first())
                    }
                    if (socLabel.isNotBlank()) {
                        MetaChip(socLabel)
                    }
                    if (device.hardware.isNotBlank()) {
                        MetaChip(device.hardware)
                    }
                }
            }
        }
    }
}

@Composable
private fun CpuPanel(
    cpu: CpuInfo,
    modifier: Modifier = Modifier,
) {
    val freqSummary =
        if (cpu.frequenciesKHz.isEmpty()) {
            "—"
        } else {
            cpu.frequenciesKHz
                .distinct()
                .sortedDescending()
                .take(3)
                .joinToString(" / ") { String.format(Locale.US, "%.0f", it / 1000.0) } + " MHz"
        }

    SpecPanel(
        modifier = modifier,
        title = stringResource(R.string.settings_device_profile_category_cpu),
        icon = Icons.Outlined.Memory,
    ) {
        SpecGrid(
            items =
                listOf(
                    stringResource(R.string.settings_device_profile_cpu_name) to
                        cpu.processorName.ifBlank { "—" },
                    stringResource(R.string.settings_device_profile_cpu_hardware) to
                        cpu.hardwareName.ifBlank { "—" },
                    stringResource(R.string.settings_device_profile_cpu_cores) to
                        cpu.cores.toString(),
                    stringResource(R.string.settings_device_profile_cpu_freq) to freqSummary,
                ),
        )
        if (cpu.features.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_device_profile_cpu_features),
                style = MaterialTheme.typography.labelMedium,
                color = MutedText,
            )
            Text(
                text = summarizeCpuFeatures(cpu.features),
                style = MaterialTheme.typography.bodySmall,
                color = ValueText.copy(alpha = 0.75f),
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun GpuPanel(
    gpu: GpuInfo,
    modifier: Modifier = Modifier,
) {
    val vulkanLabel =
        if (gpu.vulkanSupported) {
            buildString {
                append(stringResource(R.string.settings_device_profile_supported))
                if (gpu.vulkanHardwareLevel >= 0) {
                    append(" · L").append(gpu.vulkanHardwareLevel)
                }
                if (gpu.vulkanHardwareVersion >= 0) {
                    append(" · ").append(formatVulkanVersionShort(gpu.vulkanHardwareVersion))
                }
            }
        } else {
            stringResource(R.string.settings_device_profile_not_supported)
        }

    SpecPanel(
        modifier = modifier,
        title = stringResource(R.string.settings_device_profile_category_gpu),
        icon = Icons.Outlined.DeveloperBoard,
    ) {
        SpecGrid(
            items =
                listOf(
                    stringResource(R.string.settings_device_profile_gl_renderer) to
                        gpu.glRenderer.ifBlank { "—" },
                    stringResource(R.string.settings_device_profile_gl_vendor) to
                        gpu.glVendor.ifBlank { "—" },
                    stringResource(R.string.settings_device_profile_gles_version) to
                        gpu.glesVersionLabel.ifBlank { "—" },
                    stringResource(R.string.settings_device_profile_gl_version) to
                        shortenGlVersion(gpu.glVersion),
                    stringResource(R.string.settings_device_profile_vulkan) to vulkanLabel,
                    stringResource(R.string.settings_device_profile_gl_extensions) to
                        gpu.glExtensionsCount.toString(),
                ),
        )
        if (gpu.vulkanComputeLevel >= 0) {
            Spacer(modifier = Modifier.height(8.dp))
            SpecLine(
                label = stringResource(R.string.settings_device_profile_vulkan_compute_level),
                value = gpu.vulkanComputeLevel.toString(),
            )
        }
    }
}

@Composable
private fun BenchmarkPanel(
    quick: BenchmarkResult?,
    extended: BenchmarkResult?,
    running: Boolean,
    onRunExtended: () -> Unit,
) {
    val scoreUnit = stringResource(R.string.settings_device_profile_score_unit)
    val notRun = stringResource(R.string.settings_device_profile_benchmark_not_run)

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = null,
                tint = LibraryNeonGreen,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_device_profile_category_benchmark),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScoreTile(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.settings_device_profile_quick_benchmark),
                score = formatScore(quick),
                detail = formatDetail(quick, scoreUnit, notRun),
            )
            ScoreTile(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.settings_device_profile_extended_benchmark),
                score = formatScore(extended),
                detail = formatDetail(extended, scoreUnit, notRun),
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.settings_device_profile_run_extended_description),
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
            )
            Button(
                onClick = onRunExtended,
                enabled = !running,
                modifier = Modifier.controllerFocusGlow(RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = LibraryNeonGreen,
                        contentColor = Color.Black,
                        disabledContainerColor = LibraryNeonGreen.copy(alpha = 0.35f),
                        disabledContentColor = Color.Black.copy(alpha = 0.5f),
                    ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_device_profile_run_extended),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ScoreTile(
    label: String,
    score: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, PanelBorder),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MutedText,
            )
            Text(
                text = score,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (score == "—") MutedText else Color.White,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SpecPanel(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Panel(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LibraryNeonGreen,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SpecGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { (label, value) ->
                    SpecCell(
                        label = label,
                        value = value,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SpecCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = ValueText,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SpecLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MutedText)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = ValueText,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.85f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .background(Color.White.copy(alpha = 0.07f), ChipShape)
                .border(1.dp, PanelBorder, ChipShape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PanelShape,
        color = PanelColor,
        border = BorderStroke(1.dp, PanelBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(16.dp),
        ) {
            content()
        }
    }
}

@Composable
fun ExtendedBenchmarkProgressDialog(progress: Float) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(text = stringResource(R.string.settings_device_profile_benchmark_running_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(R.string.settings_device_profile_benchmark_running_message))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = LibraryNeonGreen,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
                Text(
                    text =
                        stringResource(
                            R.string.settings_device_profile_benchmark_progress,
                            (progress * 100).toInt(),
                        ),
                )
            }
        },
        confirmButton = { },
    )
}

@Composable
fun WelcomeBenchmarkDialog(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(text = stringResource(R.string.device_profile_welcome_title)) },
        text = { Text(text = stringResource(R.string.device_profile_welcome_message)) },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text(text = stringResource(R.string.device_profile_welcome_agree))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(text = stringResource(R.string.device_profile_welcome_decline))
            }
        },
    )
}

private fun formatScore(result: BenchmarkResult?): String {
    if (result == null) return "—"
    return String.format(Locale.US, "%.1fM", result.scoreOpsPerSec / 1_000_000.0)
}

private fun formatDetail(
    result: BenchmarkResult?,
    scoreUnit: String,
    notRun: String,
): String {
    if (result == null) return notRun
    return "$scoreUnit · ${result.durationMs} ms"
}

private fun shortenGlVersion(version: String): String {
    if (version.isBlank()) return "—"
    val trimmed = version.trim()
    return if (trimmed.length <= 36) trimmed else trimmed.take(36).trimEnd() + "…"
}

private fun summarizeCpuFeatures(features: String): String {
    val tokens =
        features
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return "—"
    val preferred =
        listOf(
            "neon", "asimd", "aes", "sha1", "sha2", "crc32", "atomics", "fp", "asimdhp",
            "sve", "sve2", "i8mm", "bf16", "sse", "sse2", "avx", "avx2",
        )
    val highlighted =
        preferred.filter { flag ->
            tokens.any { it.equals(flag, ignoreCase = true) }
        }
    val display = if (highlighted.isNotEmpty()) highlighted else tokens.take(12)
    val extra = (tokens.size - display.size).coerceAtLeast(0)
    return buildString {
        append(display.joinToString("  "))
        if (extra > 0) append("  +").append(extra)
    }
}

private fun formatVulkanVersionShort(version: Int): String {
    if (version <= 0) return version.toString()
    val major = version shr 22
    val minor = (version shr 12) and 0x3ff
    val patch = version and 0xfff
    return if (major > 0 || minor > 0) {
        String.format(Locale.US, "%d.%d.%d", major, minor, patch)
    } else {
        "0x${Integer.toHexString(version)}"
    }
}
