package com.omnidroid.app.shared.ra

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omnidroid.app.shared.covers.CoverUtils

/**
 * Displays a RetroAchievements badge URL with an immediate vector fallback while loading or on
 * network/policy failure so badge slots never collapse to blank space.
 */
@Composable
fun RaBadgeImage(
    badgeUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    cornerRadius: Dp = 8.dp,
    fallbackIcon: ImageVector = Icons.Outlined.EmojiEvents,
    fallbackTint: Color = LocalContentColor.current.copy(alpha = 0.7f),
    fallbackSize: Dp = (size * 0.65f).coerceAtLeast(18.dp),
) {
    val allowedUrl = badgeUrl.takeIf(CoverUtils::isAllowedRetroAchievementsImageUrl)
    var loadFailed by remember(allowedUrl) { mutableStateOf(false) }
    var imageLoaded by remember(allowedUrl) { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(RoundedCornerShape(cornerRadius)),
    ) {
        if (!imageLoaded || allowedUrl == null || loadFailed) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = fallbackTint,
                modifier = Modifier.size(fallbackSize),
            )
        }
        if (allowedUrl != null && !loadFailed) {
            AsyncImage(
                model = allowedUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onSuccess = { imageLoaded = true },
                onError = {
                    imageLoaded = false
                    loadFailed = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
