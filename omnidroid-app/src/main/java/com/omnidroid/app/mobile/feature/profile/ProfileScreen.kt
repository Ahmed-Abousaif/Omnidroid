package com.omnidroid.app.mobile.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

        if (isLandscape) {
            // ─────────────────────────────────────────────────────────────────
            // Landscape Layout:
            // Left 1/3 (Weight 1f) — Fixed (No Scroll): Profile Header + Level Card
            // Right 2/3 (Weight 2f) — Scrollable: Streaks + Achievements + Recent Sessions
            // ─────────────────────────────────────────────────────────────────
            val rightScrollState = rememberScrollState()

            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Left Column (Fixed, No Scroll, fills total height)
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProfileHeaderSection(
                        modifier = Modifier.weight(1f),
                        tag = state.tag,
                        profilePicUri = state.profilePicUri,
                        milestoneBadge = state.milestoneBadge,
                        compact = true,
                        onAvatarClick = { showPhotoOptionsDialog = true },
                        onEditTagClick = { showEditTagDialog = true },
                    )

                    ProfileLevelCard(
                        modifier = Modifier.weight(1f),
                        level = state.level,
                        xpProgress = state.xpProgress,
                        xpCurrent = state.xpCurrent,
                        xpForNext = state.xpForNext,
                        xpToNext = state.xpToNext,
                        totalPlayTimeMs = state.totalPlayTimeMs,
                        compact = true,
                    )
                }

                // Right Column (Scrolls alone)
                Column(
                    modifier =
                        Modifier
                            .weight(2f)
                            .fillMaxHeight()
                            .verticalScroll(rightScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileStreakCard(
                        currentStreak = state.currentStreak,
                        bestStreak = state.bestStreak,
                        streakMultiplier = state.streakMultiplier,
                    )

                    ProfileAchievementsCard(
                        achievements = state.achievements,
                        unlockedConsoles = state.unlockedConsoles,
                        totalConsoles = state.totalConsoles,
                    )

                    ProfileRecentSessionsCard(
                        recentSessions = state.recentSessions,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            // ─────────────────────────────────────────────────────────────────
            // Portrait Layout: Single scrollable vertical list
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
                )

                ProfileStreakCard(
                    currentStreak = state.currentStreak,
                    bestStreak = state.bestStreak,
                    streakMultiplier = state.streakMultiplier,
                )

                ProfileAchievementsCard(
                    achievements = state.achievements,
                    unlockedConsoles = state.unlockedConsoles,
                    totalConsoles = state.totalConsoles,
                )

                ProfileRecentSessionsCard(
                    recentSessions = state.recentSessions,
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
                    .then(if (compact) Modifier.fillMaxHeight() else Modifier)
                    .padding(if (compact) 12.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (compact) Arrangement.SpaceEvenly else Arrangement.Center,
        ) {
            val avatarSize = if (compact) 68.dp else 88.dp

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
                                width = 2.dp,
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
                            modifier = Modifier.size(if (compact) 38.dp else 48.dp),
                        )
                    }
                }

                // Camera icon overlay badge
                Box(
                    modifier =
                        Modifier
                            .size(if (compact) 22.dp else 26.dp)
                            .clip(CircleShape)
                            .background(LibraryNeonGreen)
                            .clickable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = stringResource(R.string.profile_change_picture),
                        tint = Color.Black,
                        modifier = Modifier.size(if (compact) 13.dp else 15.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))

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
                    fontSize = if (compact) 17.sp else 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.profile_change_tag),
                    tint = LibraryNeonGreen,
                    modifier = Modifier.size(if (compact) 15.dp else 17.dp),
                )
            }

            // Milestone Badge (if earned)
            if (milestoneBadge != null) {
                Spacer(modifier = Modifier.height(if (compact) 4.dp else 6.dp))
                val badgeText =
                    when (milestoneBadge) {
                        ConsoleAchievementsStore.MilestoneBadge.COLLECTOR -> stringResource(R.string.profile_badge_collector)
                        ConsoleAchievementsStore.MilestoneBadge.ENTHUSIAST -> stringResource(R.string.profile_badge_enthusiast)
                        ConsoleAchievementsStore.MilestoneBadge.HISTORIAN -> stringResource(R.string.profile_badge_historian)
                        ConsoleAchievementsStore.MilestoneBadge.OMNIDROID_MASTER -> stringResource(R.string.profile_badge_master)
                    }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LibraryNeonGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, LibraryNeonGreen.copy(alpha = 0.35f)),
                ) {
                    Text(
                        text = badgeText,
                        color = LibraryNeonGreen,
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                    .then(if (compact) Modifier.fillMaxHeight() else Modifier)
                    .padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = if (compact) Arrangement.SpaceEvenly else Arrangement.Top,
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
                        modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.profile_level, level),
                        color = Color.White,
                        fontSize = if (compact) 15.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = stringResource(R.string.profile_xp_to_next, xpToNext),
                    color = SubtitleColor,
                    fontSize = if (compact) 11.sp else 12.sp,
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))

            // Animated Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(if (compact) 6.dp else 8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                color = LibraryNeonGreen,
                trackColor = Color.White.copy(alpha = 0.1f),
            )

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 6.dp))

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
                    fontSize = if (compact) 10.sp else 12.sp,
                )
                Text(
                    text = "${(xpProgress * 100).toInt()}%",
                    color = LibraryNeonGreen,
                    fontSize = if (compact) 10.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 6.dp else 14.dp))
            Divider(color = PanelBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(if (compact) 6.dp else 12.dp))

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
                )
                ProfileStatColumn(
                    icon = Icons.Outlined.EmojiEvents,
                    title = "Total XP",
                    value = String.format(Locale.US, "%,d", xpCurrent),
                    compact = compact,
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
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = LibraryNeonGreen,
                modifier = Modifier.size(if (compact) 13.dp else 16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                color = SubtitleColor,
                fontSize = if (compact) 10.sp else 12.sp,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = if (compact) 13.sp else 16.sp,
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
                    .padding(16.dp),
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
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.profile_streak_current),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // XP Multiplier Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = FireColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, FireColor.copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = stringResource(R.string.profile_streak_multiplier, streakMultiplier),
                        color = FireColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.profile_streak_days, currentStreak),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "${stringResource(R.string.profile_streak_best)}: ${stringResource(R.string.profile_streak_days, bestStreak)}",
                        color = SubtitleColor,
                        fontSize = 11.sp,
                    )
                }

                // 7-day streak progress visual dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (day in 1..7) {
                        val active = day <= currentStreak
                        val isMaxBonus = day == 7
                        Box(
                            modifier =
                                Modifier
                                    .size(if (isMaxBonus) 14.dp else 10.dp)
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
                    .padding(16.dp),
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
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.profile_achievements),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = stringResource(R.string.profile_consoles_unlocked, unlockedConsoles, totalConsoles),
                    color = SubtitleColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Console badges (FlowRow)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                achievements.forEach { achievement ->
                    ConsoleAchievementItem(achievement = achievement)
                }
            }
        }
    }
}

@Composable
private fun ConsoleAchievementItem(achievement: ConsoleAchievement) {
    val unlocked = achievement.unlocked
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (unlocked) Color(0xFF242424) else Color(0xFF121212),
        border = BorderStroke(
            1.dp,
            if (unlocked) LibraryNeonGreen.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.07f),
        ),
        modifier = Modifier.size(76.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 3.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Console Icon & Lock Overlay
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                // Console Icon (Bigger: 44dp)
                Icon(
                    painter = painterResource(achievement.imageResId),
                    contentDescription = stringResource(achievement.nameResId),
                    tint = if (unlocked) Color.White else Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.size(44.dp),
                )

                // High-Contrast Lock in front of the console icon
                if (!unlocked) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xEE000000),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.size(24.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFFF5F5F5),
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(achievement.nameResId),
                color = if (unlocked) Color.White else Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp,
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
                    .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = LibraryNeonGreen,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.profile_recent_sessions),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                recentSessions.forEachIndexed { index, item ->
                    if (index > 0) {
                        Divider(color = PanelBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    RecentSessionRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun RecentSessionRow(item: ProfileViewModel.RecentSessionItem) {
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
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$durationMin min • $dateText",
                color = SubtitleColor,
                fontSize = 11.sp,
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
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
