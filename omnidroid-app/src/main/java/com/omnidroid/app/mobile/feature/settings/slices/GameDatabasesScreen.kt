package com.omnidroid.app.mobile.feature.settings.slices

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.R
import com.omnidroid.lib.R as LibR
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.lib.core.MetadataSliceInstaller
import com.omnidroid.lib.core.SliceCatalog
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.findByName
import com.omnidroid.metadata.libretrodb.db.LibretroDBManager
import com.omnidroid.metadata.libretrodb.db.entity.InstalledSlice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class SliceUiModel(
    val id: String,
    val titleRes: Int,
    val badge: String,
    val systems: List<String>,
    val installed: InstalledSlice?,
)

data class ConsoleInfo(
    val titleRes: Int,
    val badge: String,
)

fun getConsoleInfo(sliceId: String): ConsoleInfo {
    return when (sliceId) {
        "atari2600" -> ConsoleInfo(LibR.string.game_system_title_atari2600, "2600")
        "nes" -> ConsoleInfo(LibR.string.game_system_title_nes, "NES")
        "snes" -> ConsoleInfo(LibR.string.game_system_title_snes, "SNES")
        "sms" -> ConsoleInfo(LibR.string.game_system_title_sms, "SMS")
        "md" -> ConsoleInfo(LibR.string.game_system_title_genesis, "GEN")
        "scd" -> ConsoleInfo(LibR.string.game_system_title_scd, "SCD")
        "gg" -> ConsoleInfo(LibR.string.game_system_title_gg, "GG")
        "gb" -> ConsoleInfo(LibR.string.game_system_title_gb, "GB")
        "gbc" -> ConsoleInfo(LibR.string.game_system_title_gbc, "GBC")
        "gba" -> ConsoleInfo(LibR.string.game_system_title_gba, "GBA")
        "n64" -> ConsoleInfo(LibR.string.game_system_title_n64, "N64")
        "psx" -> ConsoleInfo(LibR.string.game_system_title_psx, "PS1")
        "psp" -> ConsoleInfo(LibR.string.game_system_title_psp, "PSP")
        "arcade" -> ConsoleInfo(LibR.string.game_system_title_arcade, "ARC")
        "nds" -> ConsoleInfo(LibR.string.game_system_title_nds, "NDS")
        "3ds" -> ConsoleInfo(LibR.string.game_system_title_3ds, "3DS")
        "atari7800" -> ConsoleInfo(LibR.string.game_system_title_atari7800, "7800")
        "lynx" -> ConsoleInfo(LibR.string.game_system_title_lynx, "LYNX")
        "pce" -> ConsoleInfo(LibR.string.game_system_title_pce, "PCE")
        "ngp" -> ConsoleInfo(LibR.string.game_system_title_ngp, "NGP")
        "ngc" -> ConsoleInfo(LibR.string.game_system_title_ngc, "NGPC")
        "ws" -> ConsoleInfo(LibR.string.game_system_title_ws, "WS")
        "wsc" -> ConsoleInfo(LibR.string.game_system_title_wsc, "WSC")
        "dos" -> ConsoleInfo(LibR.string.game_system_title_dos, "DOS")
        "ps2" -> ConsoleInfo(LibR.string.game_system_title_ps2, "PS2")
        "gamecube" -> ConsoleInfo(LibR.string.game_system_title_gamecube, "GCN")
        "wii" -> ConsoleInfo(LibR.string.game_system_title_wii, "WII")
        "dreamcast" -> ConsoleInfo(LibR.string.game_system_title_dreamcast, "DC")
        else -> {
            val system = runCatching { GameSystem.findById(sliceId) }.getOrNull()
            if (system != null) {
                ConsoleInfo(system.titleResId, sliceId.uppercase(Locale.US).take(4))
            } else {
                ConsoleInfo(LibR.string.game_system_title_nes, sliceId.uppercase(Locale.US).take(4))
            }
        }
    }
}

enum class DatabaseFilter {
    ALL,
    INSTALLED,
    MISSING,
}

@Composable
fun GameDatabasesScreen(
    modifier: Modifier = Modifier,
    viewModel: GameDatabasesViewModel,
) {
    val context = LocalContext.current
    val slices by viewModel.slices.collectAsState()
    val loadingSliceIds by viewModel.loadingSliceIds.collectAsState()
    val isRefreshingAll by viewModel.isRefreshingAll.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(DatabaseFilter.ALL) }

    val installedCount = slices.count { it.installed != null }
    val totalCount = slices.size
    val totalGames = slices.sumOf { it.installed?.rows ?: 0 }

    val filteredSlices = remember(slices, searchQuery, selectedFilter) {
        slices.filter { slice ->
            val matchesFilter = when (selectedFilter) {
                DatabaseFilter.ALL -> true
                DatabaseFilter.INSTALLED -> slice.installed != null
                DatabaseFilter.MISSING -> slice.installed == null
            }
            if (!matchesFilter) return@filter false

            if (searchQuery.isBlank()) return@filter true

            val query = searchQuery.trim().lowercase(Locale.US)
            val consoleTitle = runCatching { context.getString(slice.titleRes) }.getOrDefault("").lowercase(Locale.US)
            val badge = slice.badge.lowercase(Locale.US)
            val id = slice.id.lowercase(Locale.US)
            val systems = slice.systems.joinToString(" ").lowercase(Locale.US)

            consoleTitle.contains(query) || badge.contains(query) || id.contains(query) || systems.contains(query)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header_card") {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(10.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_title_game_databases),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.game_databases_installed_count, installedCount, totalCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.refreshAll() },
                            enabled = !isRefreshingAll,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.controllerFocusGlow(RoundedCornerShape(10.dp)),
                        ) {
                            if (isRefreshingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.game_databases_refreshing_all),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.game_databases_refresh_all),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.game_databases_header_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = if (installedCount == totalCount && totalCount > 0) {
                                        Color(0xFF4CAF50)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$installedCount / $totalCount Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.game_databases_total_games, totalGames),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "search_and_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .controllerFocusGlow(RoundedCornerShape(12.dp)),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.game_databases_search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedFilter == DatabaseFilter.ALL,
                        onClick = { selectedFilter = DatabaseFilter.ALL },
                        label = { Text(stringResource(R.string.game_databases_filter_all)) },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                    FilterChip(
                        selected = selectedFilter == DatabaseFilter.INSTALLED,
                        onClick = { selectedFilter = DatabaseFilter.INSTALLED },
                        label = {
                            Text("${stringResource(R.string.game_databases_filter_installed)} ($installedCount)")
                        },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                    FilterChip(
                        selected = selectedFilter == DatabaseFilter.MISSING,
                        onClick = { selectedFilter = DatabaseFilter.MISSING },
                        label = {
                            Text("${stringResource(R.string.game_databases_filter_missing)} (${totalCount - installedCount})")
                        },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }

        if (filteredSlices.isEmpty()) {
            item(key = "empty_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.game_databases_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(filteredSlices, key = { it.id }) { slice ->
                val isSliceLoading = slice.id in loadingSliceIds || isRefreshingAll
                ConsoleDatabaseCard(
                    slice = slice,
                    isLoading = isSliceLoading,
                    onRefetch = { viewModel.refetch(slice.id) },
                )
            }
        }
    }
}

@Composable
private fun ConsoleDatabaseCard(
    slice: SliceUiModel,
    isLoading: Boolean,
    onRefetch: () -> Unit,
) {
    val installed = slice.installed
    val isInstalled = installed != null

    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .controllerFocusGlow(RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left Side: Console Badge + Full Console Name & Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (isInstalled) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = slice.badge,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isInstalled) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(slice.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (installed != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(androidx.compose.ui.graphics.Color(0xFF4CAF50), CircleShape),
                            )
                            Text(
                                text = stringResource(R.string.game_databases_games_count, installed.rows) +
                                    " • " +
                                    stringResource(R.string.game_databases_build_hash, installed.sha256.take(8)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                            )
                            Text(
                                text = stringResource(R.string.game_databases_missing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Right Side: Refetch / Download Button or Spinner (Opposite side, not below the name!)
            Spacer(modifier = Modifier.width(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                    )
                }
            } else if (isInstalled) {
                OutlinedButton(
                    onClick = onRefetch,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.controllerFocusGlow(RoundedCornerShape(8.dp)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.game_databases_refetch),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                Button(
                    onClick = onRefetch,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.controllerFocusGlow(RoundedCornerShape(8.dp)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.game_databases_download),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

class GameDatabasesViewModel(
    private val context: Context,
    manager: LibretroDBManager,
    private val installer: MetadataSliceInstaller,
) : ViewModel() {
    private val _loadingSliceIds = MutableStateFlow<Set<String>>(emptySet())
    val loadingSliceIds: StateFlow<Set<String>> = _loadingSliceIds.asStateFlow()

    private val _isRefreshingAll = MutableStateFlow(false)
    val isRefreshingAll: StateFlow<Boolean> = _isRefreshingAll.asStateFlow()

    val slices: StateFlow<List<SliceUiModel>> =
        manager.dbInstance.sliceDao().observeAll().map { installedList ->
            val byId = installedList.associateBy { it.sliceId }
            SliceCatalog.slices.map { slice ->
                val consoleInfo = getConsoleInfo(slice.id)
                SliceUiModel(
                    id = slice.id,
                    titleRes = consoleInfo.titleRes,
                    badge = consoleInfo.badge,
                    systems = slice.systems,
                    installed = byId[slice.id],
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SliceCatalog.slices.map { slice ->
                val consoleInfo = getConsoleInfo(slice.id)
                SliceUiModel(
                    id = slice.id,
                    titleRes = consoleInfo.titleRes,
                    badge = consoleInfo.badge,
                    systems = slice.systems,
                    installed = null,
                )
            },
        )

    fun refetch(sliceId: String) {
        val slice = SliceCatalog.byId(sliceId) ?: return
        val cores = slice.cores.mapNotNull { findByName(it) }
        if (cores.isEmpty()) return

        viewModelScope.launch {
            _loadingSliceIds.update { it + sliceId }
            try {
                installer.ensureSlices(context, cores, force = true)
            } finally {
                _loadingSliceIds.update { it - sliceId }
            }
        }
    }

    fun refreshAll() {
        if (_isRefreshingAll.value) return
        val allCores = SliceCatalog.slices.flatMap { it.cores }.distinct().mapNotNull { findByName(it) }
        if (allCores.isEmpty()) return

        viewModelScope.launch {
            _isRefreshingAll.value = true
            try {
                installer.ensureSlices(context, allCores, force = true)
            } finally {
                _isRefreshingAll.value = false
            }
        }
    }

    class Factory(
        private val context: Context,
        private val manager: LibretroDBManager,
        private val installer: MetadataSliceInstaller,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return GameDatabasesViewModel(context, manager, installer) as T
        }
    }
}
