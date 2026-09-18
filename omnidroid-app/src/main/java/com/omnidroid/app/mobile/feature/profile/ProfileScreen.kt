package com.omnidroid.app.mobile.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.HomeChromeBackground
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val PanelColor = Color(0xFF1A1A1A)
private val DialogColor = Color(0xFF1E1E1E)
private val PanelBorder = Color.White.copy(alpha = 0.08f)
private val DialogBorder = Color.White.copy(alpha = 0.14f)
private val SubtitleColor = Color.White.copy(alpha = 0.55f)
private val PanelShape = RoundedCornerShape(14.dp)
private val FireColor = Color(0xFFFF6D00)
private val DangerColor = Color(0xFFFF5252)

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showEditTagDialog by remember { mutableStateOf(false) }
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            uri?.let {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                viewModel.updateProfilePicUri(it.toString())
            }
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(HomeChromeBackground),
    ) {
        val isLandscape = maxWidth > maxHeight
        val isLargeLandscape = isLandscape && (maxWidth >= 840.dp || maxHeight >= 500.dp)
        val isTabletPortrait = !isLandscape && maxWidth >= 600.dp
        val isLargeScreen = isLargeLandscape || isTabletPortrait

        if (isLandscape) {
            // ─────────────────────────────────────────────────────────────────
            // Landscape Layout:
            // Left Column — Fixed (No Scroll): Profile Header + Level Card
            // Right Column — Scrollable: Streaks + Achievements + Recent Sessions
            // ─────────────────────────────────────────────────────────────────
            val rightScrollState = rememberScrollState()

            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = if (isLargeScreen) 24.dp else 16.dp,
                            vertical = if (isLargeScreen) 14.dp else 10.dp,
                        ),
                horizontalArrangement = Arrangement.spacedBy(if (isLargeScreen) 20.dp else 16.dp),
            ) {
                // Left Column (Scrolls if needed on short screens, natural height, never stretches)
                val leftScrollState = rememberScrollState()
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(leftScrollState),
                    verticalArrangement = Arrangement.spacedBy(if (isLargeScreen) 16.dp else 10.dp),
                ) {
                    ProfileHeaderSection(
                        tag = state.tag,
                        profilePicUri = state.profilePicUri,
                        milestoneBadge = state.milestoneBadge,
                        compact = !isLargeScreen,
                        isLargeScreen = isLargeScreen,
                        onAvatarClick = { showPhotoOptionsDialog = true },
                        onEditTagClick = { showEditTagDialog = true },
                    )

                    ProfileLevelCard(
                        level = state.level,
                        xpProgress = state.xpProgress,
                        xpCurrent = state.xpCurrent,
                        xpForNext = state.xpForNext,
                        xpToNext = state.xpToNext,
                        totalPlayTimeMs = state.totalPlayTimeMs,
                        compact = !isLargeScreen,
                        isLargeScreen = isLargeScreen,
                    )
                }

                // Right Column (Scrolls alone)
                Column(
                    modifier =
                        Modifier
                            .weight(if (isLargeScreen) 1.85f else 2f)
                            .fillMaxHeight()
                            .verticalScroll(rightScrollState),
                    verticalArrangement = Arrangement.spacedBy(if (isLargeScreen) 16.dp else 12.dp),
                ) {
                    ProfileStreakCard(
                        currentStreak = state.currentStreak,
                        bestStreak = state.bestStreak,
                        streakMultiplier = state.streakMultiplier,
                        isLargeScreen = isLargeScreen,
                    )

                    ProfileAchievementsCard(
                        achievements = state.achievements,
                        unlockedConsoles = state.unlockedConsoles,
                        totalConsoles = state.totalConsoles,
                        isLargeScreen = isLargeScreen,
                    )

                    ProfileRecentSessionsCard(
                        recentSessions = state.recentSessions,
                        isLargeScreen = isLargeScreen,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else if (isTabletPortrait) {
            // ─────────────────────────────────────────────────────────────────
            // Tablet Portrait Layout: 2-column scrollable dashboard
            // ─────────────────────────────────────────────────────────────────
            val tabletPortraitScrollState = rememberScrollState()

            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(tabletPortraitScrollState)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ProfileHeaderSection(
                        tag = state.tag,
                        profilePicUri = state.profilePicUri,
                        milestoneBadge = state.milestoneBadge,
                        compact = false,
                        isLargeScreen = true,
                        onAvatarClick = { showPhotoOptionsDialog = true },
                        onEditTagClick = { showEditTagDialog = true },
                    )

                    ProfileLevelCard(
                        level = state.level,
                        xpProgress = state.xpProgress,
                        xpCurrent = state.xpCurrent,
                        xpForNext = state.xpForNext,
                        xpToNext = state.xpToNext,
                        totalPlayTimeMs = state.totalPlayTimeMs,
                        compact = false,
                        isLargeScreen = true,
                    )

                    ProfileStreakCard(
                        currentStreak = state.currentStreak,
                        bestStreak = state.bestStreak,
                        streakMultiplier = state.streakMultiplier,
                        isLargeScreen = true,
                    )
                }

                Column(
                    modifier = Modifier.weight(1.2f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ProfileAchievementsCard(
                        achievements = state.achievements,
                        unlockedConsoles = state.unlockedConsoles,
                        totalConsoles = state.totalConsoles,
                        isLargeScreen = true,
                    )

                    ProfileRecentSessionsCard(
                        recentSessions = state.recentSessions,
                        isLargeScreen = true,
                    )
                }
            }
        } else {
            // ─────────────────────────────────────────────────────────────────
            // Phone Portrait Layout: Single scrollable vertical list
            // ─────────────────────────────────────────────────────────────────
            val portraitScrollState = rememberScrollState()

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(portraitScrollState)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProfileHeaderSection(
                    tag = state.tag,
                    profilePicUri = state.profilePicUri,
                    milestoneBadge = state.milestoneBadge,
                    compact = false,
                    isLargeScreen = false,
                    onAvatarClick = { showPhotoOptionsDialog = true },
                    onEditTagClick = { showEditTagDialog = true },
                )

                ProfileLevelCard(
                    level = state.level,
                    xpProgress = state.xpProgress,
                    xpCurrent = state.xpCurrent,
                    xpForNext = state.xpForNext,
                    xpToNext = state.xpToNext,
                    totalPlayTimeMs = state.totalPlayTimeMs,
                    compact = false,
                    isLargeScreen = false,
                )

                ProfileStreakCard(
                    currentStreak = state.currentStreak,
                    bestStreak = state.bestStreak,
                    streakMultiplier = state.streakMultiplier,
                    isLargeScreen = false,
                )

                ProfileAchievementsCard(
                    achievements = state.achievements,
                    unlockedConsoles = state.unlockedConsoles,
                    totalConsoles = state.totalConsoles,
                    isLargeScreen = false,
                )

                ProfileRecentSessionsCard(
                    recentSessions = state.recentSessions,
                    isLargeScreen = false,
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // Redesigned Premium Dialogs
        // ─────────────────────────────────────────────────────────────────────
        if (showEditTagDialog) {
            EditTagDialog(
                currentTag = state.tag,
                onDismiss = { showEditTagDialog = false },
                onConfirm = { newTag ->
                    viewModel.updateTag(newTag)
                    showEditTagDialog = false
                },
            )
        }

        if (showPhotoOptionsDialog) {
            PhotoOptionsDialog(
                hasCustomPhoto = state.profilePicUri != null,
                currentPhotoUri = state.profilePicUri,
                onDismiss = { showPhotoOptionsDialog = false },
                onChangePhoto = {
                    showPhotoOptionsDialog = false
                    photoPickerLauncher.launch("image/*")
                },
                onRemovePhoto = {
                    showPhotoOptionsDialog = false
                    viewModel.removeProfilePic()
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeaderSection(
    modifier: Modifier = Modifier,
    tag: String,
    profilePicUri: String?,
    milestoneBadge: ConsoleAchievementsStore.MilestoneBadge?,
    compact: Boolean,
    isLargeScreen: Boolean = false,
    onAvatarClick: () -> Unit,
    onEditTagClick: () -> Unit,
) {
    Card(
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = PanelColor),
        border = BorderStroke(1.dp, PanelBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (isLargeScreen) 20.dp else if (compact) 12.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val avatarSize = if (isLargeScreen) 136.dp else if (compact) 96.dp else 120.dp

            // Avatar with edit badge
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.size(avatarSize),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .border(
                                width = if (isLargeScreen) 2.5.dp else 2.dp,
                                brush =
                                    Brush.linearGradient(
                                        listOf(LibraryNeonGreen, LibraryNeonGreen.copy(alpha = 0.4f)),
                                    ),
                                shape = CircleShape,
                            )
                            .background(Color(0xFF222222))
                            .clickable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center,
                ) {
                    if (profilePicUri != null) {
                        AsyncImage(
                            model = profilePicUri,
                            contentDescription = stringResource(R.string.title_profile),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_profile_robot),
                            contentDescription = stringResource(R.string.title_profile),
                            tint = LibraryNeonGreen,
                            modifier = Modifier.size(if (isLargeScreen) 74.dp else if (compact) 52.dp else 68.dp),
                        )
                    }
                }

                // Camera icon overlay badge
                Box(
                    modifier =
                        Modifier
                            .size(if (isLargeScreen) 34.dp else if (compact) 26.dp else 30.dp)
                            .clip(CircleShape)
                            .background(LibraryNeonGreen)
                            .clickable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = stringResource(R.string.profile_change_picture),
                        tint = Color.Black,
                        modifier = Modifier.size(if (isLargeScreen) 18.dp else if (compact) 14.dp else 16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isLargeScreen) 8.dp else if (compact) 4.dp else 6.dp))

            // Gamer Tag with inline edit pencil
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEditTagClick)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = tag,
                    color = Color.White,
                    fontSize = if (isLargeScreen) 23.sp else if (compact) 17.sp else 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.profile_change_tag),
                    tint = LibraryNeonGreen,
                    modifier = Modifier.size(if (isLargeScreen) 18.dp else if (compact) 15.dp else 17.dp),
                )
            }

            // Milestone rank (if earned)
            if (milestoneBadge != null) {
                Spacer(modifier = Modifier.height(if (isLargeScreen) 6.dp else if (compact) 2.dp else 4.dp))
                val badgeText =
                    when (milestoneBadge) {
                        ConsoleAchievementsStore.MilestoneBadge.COLLECTOR -> stringResource(R.string.profile_badge_collector)
                        ConsoleAchievementsStore.MilestoneBadge.ENTHUSIAST -> stringResource(R.string.profile_badge_enthusiast)
                        ConsoleAchievementsStore.MilestoneBadge.HISTORIAN -> stringResource(R.string.profile_badge_historian)
                        ConsoleAchievementsStore.MilestoneBadge.OMNIDROID_MASTER -> stringResource(R.string.profile_badge_master)
                    }
                val isMaster = milestoneBadge == ConsoleAchievementsStore.MilestoneBadge.OMNIDROID_MASTER
                val rankColor = if (isMaster) LibraryNeonGreen else SubtitleColor
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.EmojiEvents,
                        contentDescription = null,
                        tint = rankColor,
                        modifier = Modifier.size(if (isLargeScreen) 14.dp else if (compact) 12.dp else 13.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = badgeText,
                        color = rankColor,
                        fontSize = if (isLargeScreen) 13.sp else if (compact) 11.sp else 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Level & XP Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileLevelCard(
    modifier: Modifier = Modifier,
    level: Int,
    xpProgress: Float,
    xpCurrent: Long,
    xpForNext: Long,
    xpToNext: Long,
    totalPlayTimeMs: Long,
    compact: Boolean,
    isLargeScreen: Boolean = false,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = xpProgress,
        animationSpec = tween(durationMillis = 800),
        label = "xp_progress",
    )

    Card(
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = PanelColor),
        border = BorderStroke(1.dp, PanelBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (isLargeScreen) 18.dp else if (compact) 12.dp else 16.dp),
        ) {
            // Level header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Stars,
                        contentDescription = null,
                        tint = LibraryNeonGreen,
                        modifier = Modifier.size(if (isLargeScreen) 24.dp else if (compact) 18.dp else 22.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.profile_level, level),
                        color = Color.White,
                        fontSize = if (isLargeScreen) 20.sp else if (compact) 15.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = stringResource(R.string.profile_xp_to_next, xpToNext),
                    color = SubtitleColor,
                    fontSize = if (isLargeScreen) 13.sp else if (compact) 11.sp else 12.sp,
                )
            }

            Spacer(modifier = Modifier.height(if (isLargeScreen) 10.dp else if (compact) 6.dp else 10.dp))

            // Animated Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(if (isLargeScreen) 9.dp else if (compact) 6.dp else 8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                color = LibraryNeonGreen,
                trackColor = Color.White.copy(alpha = 0.1f),
            )

            Spacer(modifier = Modifier.height(if (isLargeScreen) 6.dp else if (compact) 4.dp else 6.dp))

            // XP numbers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        R.string.profile_xp_progress,
                        String.format(Locale.US, "%,d", xpCurrent),
                        String.format(Locale.US, "%,d", xpForNext),
                    ),
                    color = SubtitleColor,
                    fontSize = if (isLargeScreen) 13.sp else if (compact) 10.sp else 12.sp,
                )
                Text(
                    text = "${(xpProgress * 100).toInt()}%",
                    color = LibraryNeonGreen,
                    fontSize = if (isLargeScreen) 13.sp else if (compact) 10.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(if (isLargeScreen) 12.dp else if (compact) 6.dp else 14.dp))
            Divider(color = PanelBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(if (isLargeScreen) 10.dp else if (compact) 6.dp else 12.dp))

            // Stats row: Playtime + Total XP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ProfileStatColumn(
                    icon = Icons.Outlined.HourglassEmpty,
                    title = stringResource(R.string.profile_total_playtime),
                    value = formatPlayTime(totalPlayTimeMs),
                    compact = compact,
                    isLargeScreen = isLargeScreen,
                )
                ProfileStatColumn(
                    icon = Icons.Outlined.EmojiEvents,
                    title = "Total XP",
                    value = String.format(Locale.US, "%,d", xpCurrent),
                    compact = compact,
                    isLargeScreen = isLargeScreen,
                )
            }
        }
    }
}

@Composable
private fun ProfileStatColumn(
    icon: ImageVector,
    title: String,
    value: String,
    compact: Boolean = false,
    isLargeScreen: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = LibraryNeonGreen,
                modifier = Modifier.size(if (isLargeScreen) 18.dp else if (compact) 13.dp else 16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                color = SubtitleColor,
                fontSize = if (isLargeScreen) 13.sp else if (compact) 10.sp else 12.sp,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = if (isLargeScreen) 18.sp else if (compact) 13.sp else 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Play Streak Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileStreakCard(
    currentStreak: Int,
    bestStreak: Int,
    streakMultiplier: Double,
    isLargeScreen: Boolean = false,
) {
    Card(
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = PanelColor),
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (isLargeScreen) 20.dp else 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocalFireDepartment,
                        contentDescription = null,
                        tint = FireColor,
                        modifier = Modifier.size(if (isLargeScreen) 26.dp else 22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.profile_streak_current),
                        color = Color.White,
                        fontSize = if (isLargeScreen) 17.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = stringResource(R.string.profile_streak_multiplier, streakMultiplier),
                    color = if (streakMultiplier > 1.0) FireColor else SubtitleColor,
                    fontSize = if (isLargeScreen) 15.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(if (isLargeScreen) 14.dp else 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.profile_streak_days, currentStreak),
                        color = Color.White,
                        fontSize = if (isLargeScreen) 26.sp else 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "${stringResource(R.string.profile_streak_best)}: ${stringResource(R.string.profile_streak_days, bestStreak)}",
                        color = SubtitleColor,
                        fontSize = if (isLargeScreen) 13.sp else 11.sp,
                    )
                }

                // 7-day streak progress visual dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isLargeScreen) 8.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (day in 1..7) {
                        val active = day <= currentStreak
                        val isMaxBonus = day == 7
                        Box(
                            modifier =
                                Modifier
                                    .size(
                                        if (isLargeScreen) {
                                            if (isMaxBonus) 16.dp else 12.dp
                                        } else {
                                            if (isMaxBonus) 14.dp else 10.dp
                                        },
                                    )
                                    .clip(CircleShape)
                                    .background(
                                        if (active) {
                                            if (isMaxBonus) LibraryNeonGreen else FireColor
                                        } else {
                                            Color.White.copy(alpha = 0.12f)
                                        },
                                    ),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Console Achievements Card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileAchievementsCard(
    achievements: List<ConsoleAchievement>,
    unlockedConsoles: Int,
    totalConsoles: Int,
    isLargeScreen: Boolean = false,
) {
    Card(
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = PanelColor),
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (isLargeScreen) 20.dp else 16.dp),
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Gamepad,
                        contentDescription = null,
                        tint = LibraryNeonGreen,
                        modifier = Modifier.size(if (isLargeScreen) 24.dp else 20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.profile_achievements),
                        color = Color.White,
                        fontSize = if (isLargeScreen) 17.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = stringResource(R.string.profile_consoles_unlocked, unlockedConsoles, totalConsoles),
                    color = SubtitleColor,
                    fontSize = if (isLargeScreen) 14.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(if (isLargeScreen) 14.dp else 12.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val spacing = if (isLargeScreen) 10.dp else 8.dp
                val minCell = if (isLargeScreen) 78.dp else 64.dp
                val columns =
                    maxOf(
                        1,
                        ((maxWidth + spacing) / (minCell + spacing)).toInt(),
                    )
                val cellWidth = (maxWidth - spacing * (columns - 1)) / columns

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    maxItemsInEachRow = columns,
                ) {
                    achievements.forEach { achievement ->
                        ConsoleAchievementItem(
                            achievement = achievement,
                            modifier = Modifier.width(cellWidth),
                            isLargeScreen = isLargeScreen,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleAchievementItem(
    achievement: ConsoleAchievement,
    modifier: Modifier = Modifier,
    isLargeScreen: Boolean = false,
) {
    val unlocked = achievement.unlocked
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (unlocked) Color(0xFF242424) else Color(0xFF121212),
        border =
            BorderStroke(
                1.dp,
                if (unlocked) LibraryNeonGreen.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.07f),
            ),
        modifier = modifier.aspectRatio(1f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = if (isLargeScreen) 8.dp else 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(achievement.imageResId),
                    contentDescription = stringResource(achievement.nameResId),
                    tint = if (unlocked) Color.White else Color.White.copy(alpha = 0.18f),
                    modifier =
                        Modifier
                            .fillMaxWidth(0.62f)
                            .aspectRatio(1f),
                )

                if (!unlocked) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xEE000000),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(0.34f).aspectRatio(1f),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFFF5F5F5),
                                modifier = Modifier.fillMaxSize(0.55f),
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(achievement.nameResId),
                color = if (unlocked) Color.White else Color.White.copy(alpha = 0.4f),
                fontSize = if (isLargeScreen) 10.sp else 9.sp,
                fontWeight = if (unlocked) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recent Sessions Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileRecentSessionsCard(
    recentSessions: List<ProfileViewModel.RecentSessionItem>,
    isLargeScreen: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val initialLimit = 5
    val hasMore = recentSessions.size > initialLimit
    val displayedSessions = if (expanded || !hasMore) recentSessions else recentSessions.take(initialLimit)

    Card(
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = PanelColor),
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (isLargeScreen) 20.dp else 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        tint = LibraryNeonGreen,
                        modifier = Modifier.size(if (isLargeScreen) 22.dp else 18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.profile_recent_sessions),
                        color = Color.White,
                        fontSize = if (isLargeScreen) 17.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (recentSessions.isNotEmpty()) {
                    Text(
                        text = "${recentSessions.size} total",
                        color = SubtitleColor,
                        fontSize = if (isLargeScreen) 13.sp else 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isLargeScreen) 14.dp else 10.dp))

            if (recentSessions.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.profile_no_sessions),
                        color = SubtitleColor,
                        fontSize = if (isLargeScreen) 14.sp else 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                displayedSessions.forEachIndexed { index, item ->
                    if (index > 0) {
                        Divider(
                            color = PanelBorder,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = if (isLargeScreen) 8.dp else 6.dp),
                        )
                    }
                    RecentSessionRow(item = item, isLargeScreen = isLargeScreen)
                }

                if (hasMore) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = PanelBorder, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { expanded = !expanded }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                if (expanded) {
                                    stringResource(R.string.profile_show_less)
                                } else {
                                    stringResource(R.string.profile_view_all, recentSessions.size)
                                },
                            color = LibraryNeonGreen,
                            fontSize = if (isLargeScreen) 14.sp else 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector =
                                if (expanded) {
                                    Icons.Outlined.ExpandLess
                                } else {
                                    Icons.Outlined.ExpandMore
                                },
                            contentDescription = null,
                            tint = LibraryNeonGreen,
                            modifier = Modifier.size(if (isLargeScreen) 18.dp else 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSessionRow(
    item: ProfileViewModel.RecentSessionItem,
    isLargeScreen: Boolean = false,
) {
    val session = item.session
    val durationMin = maxOf(1L, session.durationMs / 60_000L)
    val dateText = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(session.playedAt))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.gameTitle,
                color = Color.White,
                fontSize = if (isLargeScreen) 15.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$durationMin min • $dateText",
                color = SubtitleColor,
                fontSize = if (isLargeScreen) 12.sp else 11.sp,
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = LibraryNeonGreen.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, LibraryNeonGreen.copy(alpha = 0.3f)),
        ) {
            Text(
                text = stringResource(R.string.profile_session_xp, session.xpEarned),
                color = LibraryNeonGreen,
                fontSize = if (isLargeScreen) 12.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier.padding(
                        horizontal = if (isLargeScreen) 9.dp else 7.dp,
                        vertical = if (isLargeScreen) 4.dp else 3.dp,
                    ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Redesigned Custom Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditTagDialog(
    currentTag: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var tagText by remember { mutableStateOf(currentTag) }
    val focusManager = LocalFocusManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DialogColor,
            border = BorderStroke(1.dp, DialogBorder),
            shadowElevation = 16.dp,
            modifier =
                Modifier
                    .fillMaxWidth(0.92f)
                    .padding(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(LibraryNeonGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Badge,
                            contentDescription = null,
                            tint = LibraryNeonGreen,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.profile_change_tag),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Choose your display tag across Omnidroid",
                            color = SubtitleColor,
                            fontSize = 12.sp,
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(android.R.string.cancel),
                            tint = SubtitleColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Input Field
                OutlinedTextField(
                    value = tagText,
                    onValueChange = {
                        if (it.length <= UserProfileStore.MAX_TAG_LENGTH) {
                            tagText = it
                        }
                    },
                    placeholder = {
                        Text(
                            stringResource(R.string.profile_tag_hint),
                            color = Color.White.copy(alpha = 0.3f),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (tagText.isNotEmpty()) {
                            IconButton(onClick = { tagText = "" }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Clear",
                                    tint = SubtitleColor,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (tagText.isNotBlank()) onConfirm(tagText.trim())
                            },
                        ),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF141414),
                            unfocusedContainerColor = Color(0xFF141414),
                            focusedBorderColor = LibraryNeonGreen,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            cursorColor = LibraryNeonGreen,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Character Counter
                Text(
                    text = "${tagText.length} / ${UserProfileStore.MAX_TAG_LENGTH}",
                    color = if (tagText.length >= UserProfileStore.MAX_TAG_LENGTH) FireColor else SubtitleColor,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.End),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(
                            stringResource(android.R.string.cancel),
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Button(
                        onClick = { if (tagText.isNotBlank()) onConfirm(tagText.trim()) },
                        enabled = tagText.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = LibraryNeonGreen,
                                contentColor = Color.Black,
                                disabledContainerColor = Color.White.copy(alpha = 0.1f),
                                disabledContentColor = Color.White.copy(alpha = 0.3f),
                            ),
                    ) {
                        Text("Save Tag", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoOptionsDialog(
    hasCustomPhoto: Boolean,
    currentPhotoUri: String?,
    onDismiss: () -> Unit,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DialogColor,
            border = BorderStroke(1.dp, DialogBorder),
            shadowElevation = 16.dp,
            modifier =
                Modifier
                    .fillMaxWidth(0.92f)
                    .padding(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header with close button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(LibraryNeonGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = LibraryNeonGreen,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.profile_change_picture),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Customize your profile avatar",
                            color = SubtitleColor,
                            fontSize = 12.sp,
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(android.R.string.cancel),
                            tint = SubtitleColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Avatar Preview
                Box(
                    modifier =
                        Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(
                                width = 2.dp,
                                brush =
                                    Brush.linearGradient(
                                        listOf(LibraryNeonGreen, LibraryNeonGreen.copy(alpha = 0.4f)),
                                    ),
                                shape = CircleShape,
                            )
                            .background(Color(0xFF222222)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (currentPhotoUri != null) {
                        AsyncImage(
                            model = currentPhotoUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_profile_robot),
                            contentDescription = null,
                            tint = LibraryNeonGreen,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Main Action: Choose from gallery
                Button(
                    onClick = onChangePhoto,
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = LibraryNeonGreen,
                            contentColor = Color.Black,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                ) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Choose from Gallery",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }

                // Secondary Action: Revert to default robot avatar
                if (hasCustomPhoto) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRemovePhoto,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DangerColor.copy(alpha = 0.4f)),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = DangerColor.copy(alpha = 0.08f),
                                contentColor = DangerColor,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.profile_remove_picture),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Format helper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun formatPlayTime(playTimeMs: Long): String {
    if (playTimeMs <= 0L) return "0m"
    val hours = TimeUnit.MILLISECONDS.toHours(playTimeMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(playTimeMs) % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}
