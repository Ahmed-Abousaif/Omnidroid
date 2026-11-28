package com.omnidroid.app.mobile.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.libraryAccentColor
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.compose.ui.chromeContentColor
import com.omnidroid.app.mobile.shared.compose.ui.chromeMutedContentColor
import com.omnidroid.app.mobile.shared.compose.ui.homeChromeBackground
import com.omnidroid.app.mobile.shared.compose.ui.homeChromeColor
import com.omnidroid.app.shared.ra.RaAchievementCard
import com.omnidroid.lib.library.GameSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

private enum class ProfileTab(
    val labelRes: Int,
) {
    Sessions(R.string.profile_sessions_tab),
    Consoles(R.string.profile_consoles_tab),
    RetroAchievements(R.string.profile_retro_achievements_tab),
}

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel,
    onGameClick: (Int) -> Unit,
    onOpenRetroAchievementsSettings: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showEditTag by rememberSaveable { mutableStateOf(false) }
    var showPhotoOptions by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refreshRetroAchievements()
        }
    }

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                viewModel.updateProfilePicUri(it.toString())
            }
        }

    ProfileContent(
        state = state,
        modifier = modifier,
        onEditTag = { showEditTag = true },
        onEditPhoto = { showPhotoOptions = true },
        onGameClick = onGameClick,
        onOpenRetroAchievementsSettings = onOpenRetroAchievementsSettings,
    )

    if (showEditTag) {
        EditProfileTagDialog(
            currentTag = state.tag,
            onDismiss = { showEditTag = false },
            onConfirm = {
                viewModel.updateTag(it)
                showEditTag = false
            },
        )
    }
    if (showPhotoOptions) {
        ProfilePhotoDialog(
            hasCustomPhoto = state.profilePicUri != null,
            onDismiss = { showPhotoOptions = false },
            onChoosePhoto = {
                showPhotoOptions = false
                photoPicker.launch(arrayOf("image/*"))
            },
            onRemovePhoto = {
                viewModel.removeProfilePic()
                showPhotoOptions = false
            },
        )
    }
}

/** A shared content grid uses the full height beside a compact profile rail on wide screens. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProfileContent(
    state: ProfileViewModel.UiState,
    onEditTag: () -> Unit,
    onEditPhoto: () -> Unit,
    onGameClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenRetroAchievementsSettings: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(ProfileTab.Sessions) }
    val listState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val now by produceState(System.currentTimeMillis(), lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(60_000L)
            }
        }
    }
    val timeZone = TimeZone.getDefault()
    val sessionDays =
        remember(state.recentSessions, configuration, now, timeZone.id) {
            groupProfileSessions(state.recentSessions, now, timeZone)
        }
    val dateFormat = remember(configuration, timeZone.id) { DateFormat.getDateInstance(DateFormat.FULL) }

    BoxWithConstraints(modifier.fillMaxSize().homeChromeBackground(), contentAlignment = Alignment.TopCenter) {
        val showProfileRail = maxWidth > maxHeight && maxWidth >= 640.dp
        val horizontalPadding =
            if (showProfileRail) {
                20.dp
            } else if (maxWidth >= 600.dp) {
                32.dp
            } else {
                20.dp
            }
        Row(Modifier.widthIn(max = 1200.dp).fillMaxSize()) {
            if (showProfileRail) {
                Column(
                    Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ProfileIdentity(state, onEditTag, onEditPhoto, compact = true)
                    ProfileProgress(state)
                    ProfilePlaytime(state.totalPlayTimeMs)
                    ProfileDivider()
                    ProfileStreak(state)
                }
                VerticalDivider(color = chromeColors.divider)
            }
            LazyVerticalGrid(
                columns =
                    if (selectedTab == ProfileTab.Consoles) {
                        GridCells.Adaptive(104.dp * configuration.fontScale)
                    } else {
                        GridCells.Fixed(1)
                    },
                state = listState,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
            ) {
                if (!showProfileRail) {
                    item(key = "overview", span = { GridItemSpan(maxLineSpan) }, contentType = "overview") {
                        ProfileOverview(state, onEditTag, onEditPhoto)
                    }
                    item(key = "streak", span = { GridItemSpan(maxLineSpan) }, contentType = "streak") {
                        ProfileStreak(state, Modifier.padding(vertical = 16.dp))
                        ProfileDivider()
                    }
                }
                stickyHeader(key = "tabs", contentType = "tabs") {
                    SecondaryScrollableTabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = homeChromeColor,
                        contentColor = libraryAccentColor,
                        edgePadding = 0.dp,
                        divider = { ProfileDivider() },
                    ) {
                        ProfileTab.entries.forEach { tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = {
                                    selectedTab = tab
                                    val tabIndex = if (showProfileRail) 0 else 2
                                    if (listState.firstVisibleItemIndex > tabIndex) {
                                        scope.launch { listState.scrollToItem(tabIndex) }
                                    }
                                },
                                selectedContentColor = chromeContentColor,
                                unselectedContentColor = chromeMutedContentColor,
                                text = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
                when (selectedTab) {
                    ProfileTab.Sessions -> {
                    item(
                        key = "sessions_heading",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "section_heading",
                    ) {
                        if (showProfileRail) {
                            if (state.recentSessions.isNotEmpty()) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.profile_latest_sessions,
                                        state.recentSessions.size,
                                        state.recentSessions.size,
                                    ),
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = chromeMutedContentColor,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else {
                            ProfileSectionHeading(
                                title = stringResource(R.string.profile_recent_sessions),
                                subtitle =
                                    if (state.recentSessions.isEmpty()) {
                                        null
                                    } else {
                                        pluralStringResource(
                                            R.plurals.profile_latest_sessions,
                                            state.recentSessions.size,
                                            state.recentSessions.size,
                                        )
                                    },
                            )
                        }
                    }
                    if (state.sessionsLoading) {
                        item(key = "sessions_loading", span = { GridItemSpan(maxLineSpan) }, contentType = "loading") {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                color = libraryAccentColor,
                                trackColor = chromeColors.divider,
                            )
                        }
                    } else if (sessionDays.isEmpty()) {
                        item(key = "sessions_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                                Text(
                                    stringResource(R.string.profile_no_activity),
                                    color = chromeContentColor,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(R.string.profile_no_sessions),
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = chromeMutedContentColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        sessionDays.forEach { day ->
                            item(
                                key = "day_${day.startedAt}",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "day_heading",
                            ) {
                                val dayLabel =
                                    when (day.relativeDay) {
                                        0 -> stringResource(R.string.profile_today)
                                        -1 -> stringResource(R.string.profile_yesterday)
                                        else -> dateFormat.format(Date(day.startedAt))
                                    }
                                Text(
                                    dayLabel,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
                                    color = chromeMutedContentColor,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            items(
                                day.sessions,
                                key = { "session_${it.session.id}" },
                                contentType = { "session" },
                            ) { item ->
                                ProfileSessionRow(item, onClick = { onGameClick(item.session.gameId) })
                                ProfileDivider()
                            }
                        }
                    }
                    }
                    ProfileTab.Consoles -> {
                        item(
                            key = "consoles_heading",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "section_heading",
                        ) {
                            ProfileSectionHeading(
                                title = stringResource(R.string.profile_achievements),
                                subtitle =
                                    stringResource(
                                        R.string.profile_consoles_unlocked,
                                        state.unlockedConsoles,
                                        state.totalConsoles,
                                    ),
                            )
                            if (!showProfileRail) {
                                Text(
                                    stringResource(R.string.profile_console_help),
                                    modifier = Modifier.padding(bottom = 12.dp),
                                    color = chromeMutedContentColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        items(
                            state.achievements,
                            key = { it.metaSystemID.name },
                            contentType = { "console" },
                        ) { achievement ->
                            ProfileConsoleTile(achievement)
                        }
                    }
                    ProfileTab.RetroAchievements -> {
                        item(
                            key = "ra_account",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "ra_account",
                        ) {
                            ProfileRaAccountCard(
                                signedIn = state.raSignedIn,
                                username = state.raUsername,
                                userInfo = state.raUserInfo,
                                loadingUser = state.raUserLoading,
                                aggregate = state.raAggregate,
                                onOpenSettings = onOpenRetroAchievementsSettings,
                            )
                        }
                        val aggregate = state.raAggregate
                        if (aggregate.games.isEmpty()) {
                            if (state.raSignedIn) {
                                item(
                                    key = "ra_empty_games",
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = "empty",
                                ) {
                                    Text(
                                        text = stringResource(R.string.profile_ra_no_games_yet),
                                        modifier = Modifier.padding(vertical = 16.dp),
                                        color = chromeMutedContentColor,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        } else {
                            item(
                                key = "ra_games_heading",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "section_heading",
                            ) {
                                ProfileRaSectionHeader(
                                    stringResource(R.string.profile_ra_games_heading, aggregate.games.size),
                                )
                            }
                            items(
                                aggregate.games,
                                key = { "ra_game_${it.gameId}" },
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = { "ra_game" },
                            ) { game ->
                                ProfileRaGameRow(game = game, onClick = { onGameClick(game.gameId) })
                            }
                            item(
                                key = "ra_unlocked_heading",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "section_heading",
                            ) {
                                ProfileRaSectionHeader(
                                    stringResource(
                                        R.string.profile_ra_unlocked_heading,
                                        aggregate.unlockedAchievements.size,
                                    ),
                                )
                            }
                            if (aggregate.unlockedAchievements.isEmpty()) {
                                item(
                                    key = "ra_no_unlocked",
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = "empty",
                                ) {
                                    Text(
                                        text = stringResource(R.string.profile_ra_no_unlocked_yet),
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = chromeMutedContentColor,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            } else {
                                items(
                                    aggregate.unlockedAchievements,
                                    key = { "ra_ach_${it.gameId}_${it.achievement.id}" },
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = { "ra_achievement" },
                                ) { unlocked ->
                                    RaAchievementCard(
                                        achievement = unlocked.achievement,
                                        useHardcoreRarity = true,
                                        contextLabel =
                                            formatRaUnlockContext(
                                                gameTitle = unlocked.gameTitle,
                                                unlockTimeSeconds = unlocked.achievement.unlockTime,
                                            ),
                                        modifier =
                                            Modifier
                                                .padding(vertical = 3.dp)
                                                .clickable(
                                                    role = Role.Button,
                                                    onClickLabel = stringResource(R.string.profile_open_game),
                                                    onClick = { onGameClick(unlocked.gameId) },
                                                ),
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "bottom_space", span = { GridItemSpan(maxLineSpan) }, contentType = "spacer") {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileSectionHeading(
    title: String,
    subtitle: String?,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
        Text(
            title,
            color = chromeContentColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                subtitle,
                modifier = Modifier.padding(top = 4.dp),
                color = chromeMutedContentColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProfileConsoleTile(achievement: ConsoleAchievement) {
    val status = stringResource(if (achievement.unlocked) R.string.profile_unlocked else R.string.profile_locked)
    Column(
        modifier =
            Modifier.fillMaxWidth().heightIn(min = 92.dp).padding(horizontal = 4.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) { stateDescription = status },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painterResource(achievement.imageResId),
            contentDescription = null,
            tint = if (achievement.unlocked) chromeContentColor else chromeMutedContentColor,
            modifier = Modifier.size(28.dp),
        )
        Text(
            stringResource(achievement.nameResId),
            color = chromeContentColor,
            style = MaterialTheme.typography.bodySmall,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (achievement.unlocked) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = libraryAccentColor,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(status, color = chromeMutedContentColor, style = MaterialTheme.typography.labelSmall)
        }
        ProfileDivider()
    }
}

@Composable
private fun ProfileSessionRow(
    item: ProfileViewModel.RecentSessionItem,
    onClick: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val timeZoneId = TimeZone.getDefault().id
    val timeFormat = remember(configuration, timeZoneId) { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val system = remember(item.systemId) { runCatching { GameSystem.findById(item.systemId) }.getOrNull() }
    val subtitle =
        listOfNotNull(
            system?.let { stringResource(it.shortTitleResId) },
            timeFormat.format(Date(item.session.playedAt)),
        ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable(
            role = Role.Button,
            onClickLabel = stringResource(R.string.profile_open_game),
            onClick = onClick,
        ).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.coverArt,
            contentDescription = null,
            placeholder = painterResource(R.drawable.ic_profile_robot),
            error = painterResource(R.drawable.ic_profile_robot),
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(32.dp).height(42.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.gameTitle,
                color = chromeContentColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, color = chromeMutedContentColor, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                profileDuration(item.session.durationMs),
                color = chromeContentColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(R.string.profile_session_xp, item.session.xpEarned),
                color = chromeMutedContentColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun profileDuration(durationMs: Long): String {
    val minutes = durationMs.coerceAtLeast(0L) / 60_000L
    return when {
        durationMs <= 0L -> stringResource(R.string.profile_minutes, 0)
        minutes == 0L -> stringResource(R.string.profile_under_minute)
        minutes < 60L -> stringResource(R.string.profile_minutes, minutes)
        else -> stringResource(R.string.profile_hours_minutes, minutes / 60L, minutes % 60L)
    }
}

@Composable
internal fun ProfileDivider() {
    HorizontalDivider(color = chromeColors.divider)
}
