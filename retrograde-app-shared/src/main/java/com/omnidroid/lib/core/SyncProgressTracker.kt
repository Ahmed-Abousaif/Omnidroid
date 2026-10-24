package com.omnidroid.lib.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncProgress(
    val stepText: String,
    val current: Int = 0,
    val total: Int = 0,
    val isIndeterminate: Boolean = total <= 0,
) {
    val displayMessage: String
        get() = if (total > 0) "$stepText ($current/$total)" else stepText
}

object SyncProgressTracker {
    private val _progress = MutableStateFlow<SyncProgress?>(null)
    val progress: StateFlow<SyncProgress?> = _progress.asStateFlow()

    fun update(
        stepText: String,
        current: Int = 0,
        total: Int = 0,
        isIndeterminate: Boolean = total <= 0,
    ) {
        _progress.value =
            SyncProgress(
                stepText = stepText,
                current = current,
                total = total,
                isIndeterminate = isIndeterminate,
            )
    }

    fun clear() {
        _progress.value = null
    }
}
