package com.omnidroid.lib.storage

data class StorageListOptions(
    /** When true, never skip directories via mtime cache (pull-to-refresh / deep revalidation). */
    val forceDeep: Boolean = false,
)
