package com.omnidroid.lib.launch

/**
 * Resolved launch input parsed from intent data and frontend extras.
 */
data class ResolvedLaunchInput(
    /** The raw input string as provided (data URI or extra value). */
    val uriString: String,
    /** Standardized URI representation (e.g. raw path converted to file:// URI). */
    val normalizedUriString: String,
    /** Absolute filesystem path if derivable (e.g. /storage/emulated/0/...), or null. */
    val canonicalPath: String?,
    /** Disambiguating system extra if provided, e.g. "psx", "psp", etc. */
    val systemExtra: String?,
)
