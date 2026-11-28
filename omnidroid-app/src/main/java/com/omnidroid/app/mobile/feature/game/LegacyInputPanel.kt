package com.omnidroid.app.mobile.feature.game

import android.view.KeyEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.omnidroid.R
import com.omnidroid.app.shared.game.view.ComputerKeyboardInput
import com.omnidroid.app.shared.game.view.IRetroGameView
import com.omnidroid.lib.library.SystemID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LegacyInputPanel(
    system: SystemID,
    view: IRetroGameView?,
    modifier: Modifier = Modifier,
) {
    val keypad = system in setOf(SystemID.ATARI5200, SystemID.COLECOVISION, SystemID.JAGUAR)
    if (!keypad && system !in ComputerKeyboardInput.systems) return
    var expanded by remember(system) { mutableStateOf(false) }
    var shift by remember(system) { mutableStateOf(false) }
    var control by remember(system) { mutableStateOf(false) }
    val held = remember(view) { mutableSetOf<Int>() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(view, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    held.toList().forEach { view?.sendKeyboardEvent(false, it) }
                    held.clear()
                    shift = false
                    control = false
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            held.toList().forEach { view?.sendKeyboardEvent(false, it) }
            held.clear()
        }
    }
    DisposableEffect(expanded, view, system) {
        if (expanded && system in setOf(SystemID.MSX, SystemID.MSX2)) view?.setControllerType(0, 3)
        onDispose {
            held.toList().forEach { view?.sendKeyboardEvent(false, it) }
            held.clear()
            if (system in setOf(SystemID.MSX, SystemID.MSX2)) view?.setControllerType(0, 257)
        }
    }
    Column(modifier = modifier) {
        TextButton(onClick = {
            held.toList().forEach { view?.sendKeyboardEvent(false, it) }
            held.clear()
            shift = false
            control = false
            expanded = !expanded
        }) {
            Text(stringResource(if (keypad) R.string.game_input_keypad else R.string.game_input_keyboard))
        }
        if (expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (keypad) {
                        listOf("123", "456", "789", "*0#").forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                row.forEach { label ->
                                    InputKey(label.toString()) { down -> sendKeypad(view, system, label, down) }
                                }
                            }
                        }
                    } else {
                        val rows =
                            listOf(
                                listOf("Esc" to 27, "Tab" to 9) + (1..12).map { "F$it" to 281 + it },
                                "1234567890-=".map { it.toString() to it.code },
                                "qwertyuiop[]".map { it.toString() to it.code },
                                "asdfghjkl;'".map { it.toString() to it.code },
                                "zxcvbnm,./".map { it.toString() to it.code },
                                listOf(
                                    "Space" to 32,
                                    "Enter" to 13,
                                    "Back" to 8,
                                    "Left" to 276,
                                    "Up" to 273,
                                    "Down" to 274,
                                    "Right" to 275,
                                ),
                            )
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                row.forEach { (label, key) ->
                                    InputKey(if (shift && key in 97..122) label.uppercase() else label) { down ->
                                        if (down) held.add(key) else held.remove(key)
                                        val character =
                                            if (down) {
                                                ComputerKeyboardInput.character(
                                                    key,
                                                    shift,
                                                    control,
                                                )
                                            } else {
                                                0
                                            }
                                        view?.sendKeyboardEvent(
                                            down,
                                            key,
                                            character,
                                            (if (shift) 1 else 0) or (if (control) 64 else 0),
                                        )
                                    }
                                }
                            }
                        }
                        Row {
                            TextButton(onClick = {
                                shift = !shift
                                if (shift) held.add(304) else held.remove(304)
                                view?.sendKeyboardEvent(shift, 304)
                            }) { Text(if (shift) "Shift ✓" else "Shift") }
                            TextButton(onClick = {
                                control = !control
                                if (control) held.add(306) else held.remove(306)
                                view?.sendKeyboardEvent(control, 306)
                            }) { Text(if (control) "Ctrl ✓" else "Ctrl") }
                            TextButton(onClick = {
                                held.toList().forEach { view?.sendKeyboardEvent(false, it) }
                                held.clear()
                                shift =
                                    false
                                control = false
                                expanded = false
                            }) { Text(stringResource(R.string.game_input_hide)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputKey(
    label: String,
    onState: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Surface(
        modifier =
            Modifier
                .sizeIn(minWidth = 34.dp, minHeight = 34.dp)
                .semantics {
                    role = Role.Button
                    onClick {
                        scope.launch {
                            onState(true)
                            try {
                                delay(50)
                            } finally {
                                onState(false)
                            }
                        }
                        true
                    }
                }.pointerInput(onState) {
                    detectTapGestures(onPress = {
                        onState(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            onState(false)
                        }
                    })
                },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) { Text(label, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

private fun sendKeypad(
    view: IRetroGameView?,
    system: SystemID,
    label: Char,
    down: Boolean,
) {
    val code =
        when (system) {
            SystemID.ATARI5200 ->
                when (label) {
                    '0' -> KeyEvent.KEYCODE_BUTTON_R1
                    '1' -> KeyEvent.KEYCODE_BUTTON_R2
                    '3' -> KeyEvent.KEYCODE_BUTTON_L2
                    '5' -> KeyEvent.KEYCODE_BUTTON_THUMBR
                    '7' -> KeyEvent.KEYCODE_BUTTON_THUMBL
                    '9' -> KeyEvent.KEYCODE_BUTTON_L1
                    '*' -> KeyEvent.KEYCODE_BUTTON_Y
                    '#' -> KeyEvent.KEYCODE_BUTTON_X
                    else -> null
                }
            SystemID.COLECOVISION ->
                when (label) {
                    '1' -> KeyEvent.KEYCODE_BUTTON_Y
                    '2' -> KeyEvent.KEYCODE_BUTTON_X
                    '3' -> KeyEvent.KEYCODE_BUTTON_L1
                    '4' -> KeyEvent.KEYCODE_BUTTON_R1
                    '5' -> KeyEvent.KEYCODE_BUTTON_L2
                    '6' -> KeyEvent.KEYCODE_BUTTON_R2
                    '7' -> KeyEvent.KEYCODE_BUTTON_THUMBL
                    '8' -> KeyEvent.KEYCODE_BUTTON_THUMBR
                    '*' -> KeyEvent.KEYCODE_BUTTON_START
                    '#' -> KeyEvent.KEYCODE_BUTTON_SELECT
                    else -> null
                }
            else -> null
        }
    if (code != null) {
        view?.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code)
        return
    }
    if (system == SystemID.JAGUAR) {
        val key =
            when (label) {
                '*' -> '-'.code
                '#' -> '='.code
                else -> label.code
            }
        view?.sendKeyboardEvent(down, key, if (down) key else 0)
        return
    }
    val x =
        when {
            !down -> 0f
            system == SystemID.COLECOVISION && label == '0' -> 1f
            label == '4' -> -1f
            label == '6' -> 1f
            else -> 0f
        }
    val y =
        when {
            !down -> 0f
            system == SystemID.COLECOVISION && label == '9' -> 1f
            label == '2' -> -1f
            label == '8' -> 1f
            else -> 0f
        }
    view?.sendMotionEvent(if (system == SystemID.COLECOVISION) 1 else 2, x, y)
}
