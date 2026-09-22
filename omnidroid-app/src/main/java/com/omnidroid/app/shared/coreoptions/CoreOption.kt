package com.omnidroid.app.shared.coreoptions

import com.omnidroid.lib.core.CoreVariable
import com.swordfish.libretrodroid.Variable
import java.io.Serializable

data class CoreOption(
    val variable: CoreVariable,
    val name: String,
    val optionValues: List<String>,
) : Serializable {
    companion object {
        fun fromLibretroDroidVariable(variable: Variable): CoreOption {
            val description = variable.description ?: ""
            val name: String
            val values: List<String>
            if (description.contains(";")) {
                val parts = description.split(";", limit = 2)
                name = parts[0].trim()
                values = parts.getOrNull(1)?.trim()?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf()
            } else {
                name = description.trim()
                values = listOf()
            }
            val coreVariable = CoreVariable(variable.key ?: "", variable.value ?: "")
            return CoreOption(coreVariable, name, values)
        }
    }
}
