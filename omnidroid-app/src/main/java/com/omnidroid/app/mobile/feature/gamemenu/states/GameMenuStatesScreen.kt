package com.omnidroid.app.mobile.feature.gamemenu.states

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.utils.android.settings.OmnidroidCardSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage
import kotlinx.coroutines.yield

@Composable
fun GameMenuStatesScreen(
    viewModel: GameMenuStatesViewModel,
    onStateClicked: (Int) -> Unit,
) {
    val state = viewModel.uiStates.collectAsState(initial = GameMenuStatesViewModel.State())
    val firstItemRequester = remember { FocusRequester() }
    val controllerNav = LocalControllerNavigation.current

    DisposableEffect(firstItemRequester) {
        controllerNav?.contentFocusRequester = firstItemRequester
        onDispose {
            if (controllerNav?.contentFocusRequester == firstItemRequester) {
                controllerNav?.contentFocusRequester = null
            }
        }
    }

    LaunchedEffect(Unit) {
        yield()
        runCatching { firstItemRequester.requestFocus() }
    }

    OmnidroidSettingsPage {
        OmnidroidCardSettingsGroup {
            state.value.entries.forEachIndexed { index, entry ->
                OmnidroidSettingsMenuLink(
                    modifier = if (index == 0) Modifier.focusRequester(firstItemRequester) else Modifier,
                    title = { Text(text = entry.title) },
                    subtitle = { Text(text = entry.description) },
                    enabled = entry.enabled,
                    icon = {
                        if (entry.preview != null) {
                            Image(
                                modifier = Modifier.size(48.dp),
                                bitmap = entry.preview.asImageBitmap(),
                                contentScale = ContentScale.Crop,
                                contentDescription = null,
                            )
                        }
                    },
                    onClick = { onStateClicked(index) },
                )
            }
        }
    }
}
