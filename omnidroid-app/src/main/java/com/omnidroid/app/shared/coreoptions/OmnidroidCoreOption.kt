package com.omnidroid.app.shared.coreoptions

import android.content.Context
import com.omnidroid.lib.library.ExposedSetting
import java.io.Serializable

data class OmnidroidCoreOption(
    private val exposedSetting: ExposedSetting,
    private val coreOption: CoreOption,
) : Serializable {
    fun getKey(): String {
        return exposedSetting.key
    }

    fun getDisplayName(context: Context): String {
        return context.getString(exposedSetting.titleId)
    }

    fun getEntries(context: Context): List<String> {
        val settings = getCorrectExposedSettings()
        if (settings.isNotEmpty()) {
            return settings.map { context.getString(it.titleId) }
        }
        if (exposedSetting.values.isNotEmpty()) {
            return exposedSetting.values.map { context.getString(it.titleId) }
        }

        if (coreOption.optionValues.isNotEmpty()) {
            return coreOption.optionValues.map {
                it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
            }
        }

        return listOf("Disabled", "Enabled")
    }

    fun getEntriesValues(): List<String> {
        val settings = getCorrectExposedSettings()
        if (settings.isNotEmpty()) {
            return settings.map { it.key }
        }
        if (exposedSetting.values.isNotEmpty()) {
            return exposedSetting.values.map { it.key }
        }

        if (coreOption.optionValues.isNotEmpty()) {
            return coreOption.optionValues
        }

        return listOf("disabled", "enabled")
    }

    fun getCurrentValue(): String {
        return coreOption.variable.value
    }

    fun getCurrentIndex(): Int {
        return maxOf(getEntriesValues().indexOf(getCurrentValue()), 0)
    }

    private fun getCorrectExposedSettings(): List<ExposedSetting.Value> {
        if (exposedSetting.values.isEmpty()) return emptyList()
        if (coreOption.optionValues.isEmpty()) return exposedSetting.values
        val filtered =
            exposedSetting.values
                .filter { exposedVal ->
                    coreOption.optionValues.any { it.equals(exposedVal.key, ignoreCase = true) }
                }
        return if (filtered.isNotEmpty()) filtered else exposedSetting.values
    }
}
