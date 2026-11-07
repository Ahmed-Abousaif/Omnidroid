package com.omnidroid.app.update

sealed class UpdateState {
    data object Idle : UpdateState()

    data object Checking : UpdateState()

    data class Available(
        val version: String,
        val notes: String,
    ) : UpdateState()

    data class Downloading(
        val progress: Float,
    ) : UpdateState()

    data object ReadyToInstall : UpdateState()

    data object UpToDate : UpdateState()

    data class Error(
        val message: String? = null,
    ) : UpdateState()
}
