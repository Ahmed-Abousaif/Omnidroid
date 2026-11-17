package com.omnidroid.app.shared.input

/**
 * Pure edits on a device's button map (physical key -> RetroPad button). A physical key can only
 * drive one button, so assigning a key that another button uses has to resolve that conflict
 * explicitly instead of silently unbinding the other button.
 */
object BindingEdits {
    enum class ConflictResolution {
        /** The other button takes over the target's previous key (if it had one). */
        SWAP,

        /** The other button is left without a key. */
        REPLACE,
    }

    /** The button other than [target] that [newKey] currently drives, if any. */
    fun conflictingButton(
        bindings: Map<InputKey, RetroKey>,
        target: RetroKey,
        newKey: InputKey,
    ): RetroKey? = bindings[newKey]?.takeIf { it != target }

    fun assign(
        bindings: Map<InputKey, RetroKey>,
        target: RetroKey,
        newKey: InputKey,
        resolution: ConflictResolution = ConflictResolution.REPLACE,
    ): Map<InputKey, RetroKey> {
        val other = conflictingButton(bindings, target, newKey)
        val previousKey = bindings.entries.firstOrNull { it.value == target }?.key
        val result = bindings.filterValues { it != target }.toMutableMap()
        result[newKey] = target
        if (other != null && resolution == ConflictResolution.SWAP && previousKey != null && previousKey != newKey) {
            result[previousKey] = other
        }
        return result
    }

    fun clear(
        bindings: Map<InputKey, RetroKey>,
        target: RetroKey,
    ): Map<InputKey, RetroKey> = bindings.filterValues { it != target }
}
