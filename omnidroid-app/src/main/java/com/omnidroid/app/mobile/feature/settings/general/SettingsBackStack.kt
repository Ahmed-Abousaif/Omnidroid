package com.omnidroid.app.mobile.feature.settings.general

/** Immutable navigation state: Back removes one level; selecting a category clears the path. */
internal data class SettingsBackStack<T>(private val pages: List<T> = emptyList()) {
    val current: T? get() = pages.lastOrNull()
    val parent: T? get() = pages.dropLast(1).lastOrNull()
    fun push(page: T): SettingsBackStack<T> = SettingsBackStack(pages + page)
    fun pop(): SettingsBackStack<T> = SettingsBackStack(pages.dropLast(1))
}
