package com.omnidroid.app.mobile.shared.controller

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import com.omnidroid.lib.library.db.entity.Game

val LocalControllerNavigation = compositionLocalOf<ControllerNavigationState?> { null }

class ControllerNavigationState {
    val focusedGame: MutableState<Game?> = mutableStateOf(null)
    val hints: MutableState<ControllerHints> = mutableStateOf(ControllerHints())
    val searchArmed: MutableState<Boolean> = mutableStateOf(false)
    val searchFocusRequester = FocusRequester()
    var contentFocusRequester: FocusRequester? = null
    var textInputActive: Boolean = false
    var onBack: () -> Unit = {}
    var onOptions: () -> Unit = {}
    var onSearch: () -> Unit = {}
    var onScan: () -> Unit = {}
    var onZoomIn: () -> Unit = {}
    var onZoomOut: () -> Unit = {}
    var dismissSearch: () -> Unit = {}
    var onPrevConsole: () -> Unit = {}
    var onNextConsole: () -> Unit = {}
    var onCycleSettings: ((Int) -> Unit)? = null
    var onCarouselPrev: () -> Unit = {}
    var onCarouselNext: () -> Unit = {}
    var onCarouselConfirm: () -> Unit = {}

    fun setFocusedGame(game: Game?) {
        focusedGame.value = game
    }

    fun setHints(value: ControllerHints) {
        hints.value = value
    }
}
