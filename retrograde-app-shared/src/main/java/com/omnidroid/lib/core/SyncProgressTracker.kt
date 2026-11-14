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
    private var owner: Session? = null

    /** A quiet/finished operation must never clear another operation's active progress. */
    class Session internal constructor() {
        fun update(
            stepText: String,
            current: Int = 0,
            total: Int = 0,
            isIndeterminate: Boolean = total <= 0,
        ) = publish(this, SyncProgress(stepText, current, total, isIndeterminate))

        fun clear() = clearOwned(this)
    }

    fun session() = Session()

    @Synchronized
    private fun publish(
        session: Session?,
        progress: SyncProgress,
    ) {
        owner = session
        _progress.value = progress
    }

    @Synchronized
    private fun clearOwned(session: Session) {
        if (owner === session) clear()
    }

    fun update(
        stepText: String,
        current: Int = 0,
        total: Int = 0,
        isIndeterminate: Boolean = total <= 0,
    ) {
        publish(
            null,
            SyncProgress(
                stepText = stepText,
                current = current,
                total = total,
                isIndeterminate = isIndeterminate,
            ),
        )
    }

    @Synchronized
    fun clear() {
        owner = null
        _progress.value = null
    }
}
