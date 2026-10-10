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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omnidroid.R
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.metadata.libretrodb.CoverSearchResult
import com.omnidroid.metadata.libretrodb.CoverSearcher
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

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        delay(350)
        results = coverSearcher.search(game.systemId, trimmed)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.game_search_cover_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
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
                            .heightIn(min = 200.dp, max = 360.dp),
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
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )

    selectedCandidate?.let { candidate ->
        AlertDialog(
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
                Button(
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { selectedCandidate = null }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            val chosen = candidate
                            selectedCandidate = null
                            onUpdateCoverOnly(chosen.coverUrl)
                            onDismiss()
                        },
                    ) {
                        Text(text = stringResource(R.string.game_match_apply_cover_only))
                    }
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
