package com.omnidroid.app.mobile.feature.settings.general

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.chromeContentColor
import com.omnidroid.app.mobile.shared.compose.ui.chromeMutedContentColor
import com.omnidroid.app.mobile.shared.compose.ui.homeChromeBackground
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow

private val SettingsSidebarWidth = 220.dp

@Composable
internal fun SettingsBackHeader(
    sectionTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onBack,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .controllerFocusGlow(RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = LibraryNeonGreen,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.settings_back_to, sectionTitle),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = chromeContentColor,
            )
        }
    }
}

@Composable
internal fun SettingsTopMenu(
    modifier: Modifier = Modifier,
    selected: SettingsSection,
    focusRequesters: Map<SettingsSection, FocusRequester>,
    onFocused: (SettingsSection) -> Unit,
    onActivate: (SettingsSection) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selected) {
        val index = SettingsSection.values().indexOf(selected)
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .homeChromeBackground(),
    ) {
        LazyRow(
            state = listState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(SettingsSection.values()) { item ->
                val isSelected = item == selected
                Surface(
                    onClick = { onActivate(item) },
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .focusRequester(focusRequesters.getValue(item))
                            .onFocusChanged { if (it.isFocused) onFocused(item) },
                    shape = RectangleShape,
                    color =
                        if (isSelected) {
                            chromeContentColor.copy(alpha = 0.08f)
                        } else {
                            Color.Transparent
                        },
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(item.titleId),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) chromeContentColor else chromeMutedContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(if (isSelected) LibraryNeonGreen else Color.Transparent),
                        )
                    }
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(chromeContentColor.copy(alpha = 0.08f)),
        )
    }
}

@Composable
internal fun SettingsSidebar(
    modifier: Modifier = Modifier,
    selected: SettingsSection,
    focusRequesters: Map<SettingsSection, FocusRequester>,
    onFocused: (SettingsSection) -> Unit,
    onActivate: (SettingsSection) -> Unit,
) {
    Column(
        modifier =
            modifier
                .width(SettingsSidebarWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 16.dp),
    ) {
        SettingsSection.values().forEach { item ->
            val isSelected = item == selected
            Surface(
                onClick = { onActivate(item) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequesters.getValue(item))
                        .onFocusChanged { if (it.isFocused) onFocused(item) },
                shape = RectangleShape,
                color =
                    if (isSelected) {
                        chromeContentColor.copy(alpha = 0.08f)
                    } else {
                        Color.Transparent
                    },
            ) {
                Row(
                    modifier = Modifier.height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(if (isSelected) LibraryNeonGreen else Color.Transparent),
                    )
                    Text(
                        text = stringResource(item.titleId),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) chromeContentColor else chromeMutedContentColor,
                        modifier = Modifier.padding(start = 16.dp, end = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
