package com.omnidroid.lib.backend

import android.content.Context

enum class RequirementSeverity {
    REQUIRED,
    RECOMMENDED,
    OPTIONAL,
}

sealed class RequirementAction {
    data class FileImport(
        val destinationDir: String,
        val destinationFileName: String? = null,
        val acceptedExtensions: List<String> = listOf("*"),
        val fileTypeLabel: String = "File",
    ) : RequirementAction()

    data class ArchiveImport(
        val destinationDir: String,
        val acceptedExtensions: List<String> = listOf("zip"),
        val fileTypeLabel: String = "Archive (*.zip)",
        val fileFilterPattern: String? = null,
    ) : RequirementAction()

    data class OpenUrl(val url: String) : RequirementAction()

    object Custom : RequirementAction()
}

data class RequirementItem(
    val id: String,
    val title: String,
    val description: String,
    val severity: RequirementSeverity = RequirementSeverity.REQUIRED,
    val isFulfilled: (Context) -> Boolean,
    val statusSummary: ((Context) -> String)? = null,
    val action: RequirementAction? = null,
)

data class RequirementDescriptor(
    val title: String,
    val items: List<RequirementItem>,
)
