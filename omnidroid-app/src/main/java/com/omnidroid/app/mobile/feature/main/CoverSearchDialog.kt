package com.omnidroid.app.mobile.feature.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.DialogScrollInfo
import com.omnidroid.app.mobile.shared.compose.dialog.DialogWidth
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogTextField
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.metadata.libretrodb.CoverSearchResult
import com.omnidroid.metadata.libretrodb.CoverSearcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun CoverSearchDialog(
    game: Game,
    coverSearcher: CoverSearcher,
    onDismiss: () -> Unit,
    onUpdateCoverOnly: (String) -> Unit,
    onFixFullMatch: (String, String) -> Unit,
) {
    var query by remember { mutableStateOf(game.displayName) }
    var results by remember { mutableStateOf<List<CoverSearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCandidate by remember { mutableStateOf<CoverSearchResult?>(null) }
    val gridState = rememberLazyGridState()
    val gridLayout = gridState.layoutInfo
    val gridSpacing = with(LocalDensity.current) { 8.dp.toPx() }
    val gridScrollInfo = if (!isLoading && (gridState.canScrollForward || gridState.canScrollBackward)) {
        val rowHeight = gridLayout.visibleItemsInfo.maxOfOrNull { it.size.height }?.toFloat() ?: 1f
        val rowCount = (gridLayout.totalItemsCount + 1) / 2
        val viewportHeight = (gridLayout.viewportEndOffset - gridLayout.viewportStartOffset).toFloat()
        val estimatedHeight = rowCount * (rowHeight + gridSpacing)
        val offset = (gridState.firstVisibleItemIndex / 2) * (rowHeight + gridSpacing) + gridState.firstVisibleItemScrollOffset
        DialogScrollInfo(
            progress = if (!gridState.canScrollForward) 1f else offset / (estimatedHeight - viewportHeight).coerceAtLeast(1f),
            visibleFraction = (viewportHeight / estimatedHeight.coerceAtLeast(1f)).coerceIn(0f, 0.98f),
            canScrollForward = gridState.canScrollForward,
        )
    } else null

    LaunchedEffect(game.id, query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        delay(350)
        try {
            results = coverSearcher.search(game.systemId, trimmed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            results = emptyList()
        } finally {
            isLoading = false
        }
    }

    OmnidroidDialog(
        width = DialogWidth.Expanded,
        bodyScrollInfo = gridScrollInfo,
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.game_search_cover_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OmnidroidDialogTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.game_search_cover_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        }
                        results.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.game_search_cover_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        else -> {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(results, key = { it.coverUrl }) { candidate ->
                                    CoverCandidateCard(
                                        candidate = candidate,
                                        onClick = {
                                            selectedCandidate = candidate
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OmnidroidDialogButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )

    selectedCandidate?.let { candidate ->
        OmnidroidDialog(
            onDismissRequest = { selectedCandidate = null },
            title = { Text(text = stringResource(R.string.game_match_dialog_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = candidate.coverUrl,
                            contentDescription = candidate.title,
                            modifier =
                                Modifier
                                    .size(64.dp, 84.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Fit,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = candidate.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.game_match_dialog_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                OmnidroidDialogButton(
                    onClick = {
                        val chosen = candidate
                        selectedCandidate = null
                        onFixFullMatch(chosen.title, chosen.coverUrl)
                        onDismiss()
                    },
                ) {
                    Text(text = stringResource(R.string.game_match_apply_both))
                }
            },
            dismissButton = {
                OmnidroidDialogButton(onClick = { selectedCandidate = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
                OmnidroidDialogButton(
                    onClick = {
                        val chosen = candidate
                        selectedCandidate = null
                        onUpdateCoverOnly(chosen.coverUrl)
                        onDismiss()
                    },
                ) {
                    Text(text = stringResource(R.string.game_match_apply_cover_only))
                }
            },
        )
    }
}

@Composable
private fun CoverCandidateCard(
    candidate: CoverSearchResult,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = candidate.coverUrl,
                contentDescription = candidate.title,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = candidate.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
