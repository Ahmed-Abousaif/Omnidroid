package com.omnidroid.app.mobile.shared.compose.sidemenu

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.app.utils.android.settings.SettingsHorizontalDivider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.yield

/** Sections and rows compose existing settings renderers; they do not own storage or effects. */
class SideMenuPageScope internal constructor(private val focused: MutableState<String?>) {
    internal val targets = linkedMapOf<String, FocusRequester>()
    internal var sectionCount = 0

    @Composable
    internal fun itemModifier(id: String, enabled: Boolean, modifier: Modifier): Modifier {
        val requester = remember(id) { FocusRequester() }
        if (enabled) targets[id] = requester
        return modifier.testTag(id).focusRequester(requester).onFocusChanged { if (it.isFocused) focused.value = id }
    }
}

@Composable
fun SideMenuPage(id: String, content: @Composable SideMenuPageScope.() -> Unit) {
    val focused = rememberSaveable(id) { mutableStateOf<String?>(null) }
    val scope = SideMenuPageScope(focused)
    val controller = LocalControllerNavigation.current
    val ownedTargets = remember { mutableSetOf<FocusRequester>() }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    OmnidroidSettingsPage(verticalArrangement = Arrangement.spacedBy(0.dp)) { scope.content() }
    val target = scope.targets[focused.value] ?: scope.targets.values.firstOrNull()
    SideEffect {
        ownedTargets.addAll(scope.targets.values)
        if (lifecycle.isAtLeast(Lifecycle.State.RESUMED)) controller?.contentFocusRequester = target
    }
    DisposableEffect(controller) {
        onDispose { if (controller?.contentFocusRequester in ownedTargets) controller?.contentFocusRequester = null }
    }
    LaunchedEffect(id, scope.targets.keys.toList(), lifecycle) {
        if (!lifecycle.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        yield()
        runCatching { (scope.targets[focused.value] ?: scope.targets.values.firstOrNull())?.requestFocus() }
    }
}

@Composable
fun SideMenuPageScope.MenuSection(
    id: String,
    visible: Boolean = true,
    @StringRes titleRes: Int? = null,
    content: @Composable SideMenuPageScope.() -> Unit,
) {
    if (!visible) return
    key(id) {
        if (sectionCount++ > 0) SettingsHorizontalDivider()
        OmnidroidSettingsGroup(titleRes = titleRes) { content() }
    }
}

@Composable
fun SideMenuPageScope.MenuAction(
    id: String,
    @StringRes titleRes: Int? = null,
    title: @Composable () -> Unit = { Text(stringResource(requireNotNull(titleRes))) },
    subtitle: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = OmnidroidSettingsMenuLink(
    modifier = itemModifier(id, enabled, modifier), title = title, subtitle = subtitle, icon = icon,
    enabled = enabled, onClick = onClick,
)

data class MenuOption<T>(val value: T, val label: String)

@Composable
fun <T> SideMenuPageScope.MenuChoice(
    id: String,
    @StringRes titleRes: Int? = null,
    title: @Composable () -> Unit = { Text(stringResource(requireNotNull(titleRes))) },
    options: List<MenuOption<T>>,
    value: T,
    enabled: Boolean = true,
    subtitle: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    useSelectedValueAsSubtitle: Boolean = true,
    onSelected: (T) -> Unit,
) = OmnidroidSettingsList(
    modifier = itemModifier(id, enabled && options.isNotEmpty(), Modifier),
    title = title, items = options.map { it.label }, selectedIndex = options.indexOfFirst { it.value == value },
    enabled = enabled, subtitle = subtitle, icon = icon, useSelectedValueAsSubtitle = useSelectedValueAsSubtitle,
    onItemSelected = { index, _ -> onSelected(options[index].value) },
)

@Composable
fun SideMenuPageScope.MenuToggle(
    id: String,
    @StringRes titleRes: Int? = null,
    title: @Composable () -> Unit = { Text(stringResource(requireNotNull(titleRes))) },
    value: Boolean,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    onChanged: (Boolean) -> Unit,
) = OmnidroidSettingsSwitch(
    modifier = itemModifier(id, enabled, Modifier), title = title, value = value,
    enabled = enabled, icon = icon, onCheckedChange = onChanged,
)

@Composable
fun SideMenuPageScope.MenuLink(id: String, destination: SideMenuDestination, onNavigate: (SideMenuDestination) -> Unit) =
    MenuAction(id = id, titleRes = destination.titleRes) { onNavigate(destination) }
