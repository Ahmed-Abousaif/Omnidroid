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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.omnidroid.app.shared.covers.CoverCacheStore
import com.omnidroid.app.shared.covers.ManagedCover
import com.omnidroid.app.shared.metadata.MetadataRepository
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.metadata.libretrodb.CoverSearchResult
import com.omnidroid.metadata.libretrodb.CoverSearcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CoverSearchDialog(
    game: Game,
    coverSearcher: CoverSearcher,
    onDismiss: () -> Unit,
    onUpdateCoverOnly: (CoverSearchResult) -> Unit,
    onFixFullMatch: (CoverSearchResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var onlineSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(game.displayName) }
    var results by remember { mutableStateOf<List<CoverSearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchFailed by remember { mutableStateOf(false) }
    var selectedCandidate by remember { mutableStateOf<CoverSearchResult?>(null) }
    val gridState = rememberLazyGridState()
    val gridLayout = gridState.layoutInfo
    val gridSpacing = with(LocalDensity.current) { 8.dp.toPx() }
    val gridScrollInfo =
        if (!isLoading && (gridState.canScrollForward || gridState.canScrollBackward)) {
            val rowHeight = gridLayout.visibleItemsInfo.maxOfOrNull { it.size.height }?.toFloat() ?: 1f
            val rowCount = (gridLayout.totalItemsCount + 1) / 2
            val viewportHeight = (gridLayout.viewportEndOffset - gridLayout.viewportStartOffset).toFloat()
            val estimatedHeight = rowCount * (rowHeight + gridSpacing)
            val offset =
                (gridState.firstVisibleItemIndex / 2) * (rowHeight + gridSpacing) +
                    gridState.firstVisibleItemScrollOffset
            DialogScrollInfo(
                progress =
                    if (!gridState.canScrollForward) {
                        1f
                    } else {
                        offset /
                            (estimatedHeight - viewportHeight).coerceAtLeast(1f)
                    },
                visibleFraction = (viewportHeight / estimatedHeight.coerceAtLeast(1f)).coerceIn(0f, 0.98f),
                canScrollForward = gridState.canScrollForward,
            )
        } else {
            null
        }

    LaunchedEffect(game.id, query) {
        onlineSearch = false
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        searchFailed = false
        delay(350)
        try {
            results = coverSearcher.search(game.systemId, trimmed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Usually no connection; say so instead of "no matching covers".
            results = emptyList()
            searchFailed = true
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

                // Online IGDB results are placed at the top of the list when requested.
                OmnidroidDialogButton(
                    onClick = {
                        if (!isLoading && query.isNotBlank()) {
                            onlineSearch = true
                            searchFailed = false
                            isLoading = true
                            val searched = query
                            scope.launch {
                                try {
                                    val online = MetadataRepository.get(context).search(game, searched)
                                    if (query == searched) {
                                        results = mergeOnlineCoverResults(results, online)
                                        if (online.isNotEmpty()) gridState.scrollToItem(0)
                                    }
                                } catch (
                                    cancelled: CancellationException,
                                ) {
                                    throw cancelled
                                } catch (
                                    _: Exception,
                                ) {
                                    if (query == searched) searchFailed = true
                                } finally {
                                    if (query == searched) isLoading = false
                                }
                            }
                        }
                    },
                ) { Text(stringResource(R.string.game_search_online_covers)) }
                if (onlineSearch) {
                    Text(
                        stringResource(R.string.metadata_attribution),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

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
                                text =
                                    stringResource(
                                        if (searchFailed) {
                                            R.string.game_search_cover_failed
                                        } else {
                                            R.string.game_search_cover_no_results
                                        },
                                    ),
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
                            model = ManagedCover(0, candidate.coverUrl, CoverCacheStore.Tier.GRID),
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
                        onFixFullMatch(chosen)
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
                        onUpdateCoverOnly(chosen)
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
                model = ManagedCover(0, candidate.coverUrl, CoverCacheStore.Tier.GRID),
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

internal fun mergeOnlineCoverResults(
    existing: List<CoverSearchResult>,
    online: List<CoverSearchResult>,
): List<CoverSearchResult> = (online + existing).distinctBy { it.coverUrl }
