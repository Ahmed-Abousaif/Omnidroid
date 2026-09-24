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
import com.alorma.compose.settings.storage.memory.rememberMemoryIntSettingState
import com.omnidroid.R
import com.omnidroid.app.utils.android.settings.OmnidroidCardSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage

@Composable
fun GraphicsApiSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: GraphicsApiSelectionViewModel,
) {
    val configs by viewModel.systemConfigs.collectAsState()
    val isVulkanSupported = viewModel.isVulkanSupported
    val vulkanVersion = viewModel.vulkanVersion

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

        OmnidroidCardSettingsGroup {
            configs.forEach { config ->
                androidx.compose.runtime.key(config.system.id) {
                    val currentIndex = config.options.indexOf(config.currentSelection).coerceAtLeast(0)
                    val state = rememberMemoryIntSettingState(currentIndex)
                    val labels = config.options.map { graphicsApiLabel(it) }

                    OmnidroidSettingsList(
                        state = state,
                        title = { Text(text = stringResource(config.system.titleResId)) },
                        subtitle = { Text(text = graphicsApiLabel(config.currentSelection)) },
                        items = labels,
                        onItemSelected = { index, _ ->
                            val selectedOption = config.options[index]
                            viewModel.setGraphicsApi(config.system.id, config.variableKey, selectedOption)
                        },
                    )
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
