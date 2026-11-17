package com.omnidroid.app.mobile.feature.settings.graphicsapi

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage
import com.omnidroid.app.utils.android.settings.SettingsHorizontalDivider

@Composable
fun GraphicsApiSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: GraphicsApiSelectionViewModel,
) {
    val configs by viewModel.systemConfigs.collectAsState()
    val isVulkanSupported by viewModel.isVulkanSupported.collectAsState()
    val vulkanVersion by viewModel.vulkanVersion.collectAsState()

    OmnidroidSettingsPage(modifier = modifier.fillMaxWidth()) {
        ElevatedCard(
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
        ) {
            val statusText = if (isVulkanSupported) {
                stringResource(
                    R.string.settings_graphics_api_vulkan_available,
                    vulkanVersion ?: "1.1+",
                )
            } else {
                stringResource(R.string.settings_graphics_api_vulkan_unavailable)
            }
            Text(
                modifier = Modifier.padding(16.dp),
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SettingsHorizontalDivider()

        OmnidroidSettingsGroup {
            configs.forEach { config ->
                androidx.compose.runtime.key(config.system.id) {
                    // Without Vulkan support the app runs OpenGL anyway, so don't offer Vulkan.
                    val options =
                        config.options.filter { isVulkanSupported || !it.equals("vulkan", ignoreCase = true) }
                    val labels = options.map { graphicsApiLabel(it) }

                    if (options.size <= 1) {
                        // A single choice (e.g. Wii U is Vulkan-only): show it, don't open a one-item picker.
                        OmnidroidSettingsMenuLink(
                            title = { Text(text = stringResource(config.system.titleResId)) },
                            subtitle = {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.settings_graphics_api_only_option,
                                            labels.firstOrNull() ?: graphicsApiLabel(config.currentSelection),
                                        ),
                                )
                            },
                            onClick = {},
                        )
                    } else {
                        val currentIndex =
                            options.indexOfFirst { it.equals(config.currentSelection, ignoreCase = true) }.coerceAtLeast(0)
                        OmnidroidSettingsList(
                            selectedIndex = currentIndex,
                            title = { Text(text = stringResource(config.system.titleResId)) },
                            subtitle = { Text(text = labels[currentIndex]) },
                            items = labels,
                            onItemSelected = { index, _ ->
                                viewModel.setGraphicsApi(config.system.id, config.variableKey, options[index])
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun graphicsApiLabel(value: String): String {
    return when (value) {
        "opengl" -> "OpenGL"
        "software" -> "Software"
        "vulkan" -> "Vulkan"
        else -> value
    }
}
