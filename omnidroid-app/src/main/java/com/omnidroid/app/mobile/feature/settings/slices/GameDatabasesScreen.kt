package com.omnidroid.app.mobile.feature.settings.slices

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.R
import com.omnidroid.lib.core.MetadataSliceInstaller
import com.omnidroid.lib.core.SliceCatalog
import com.omnidroid.lib.library.findByName
import com.omnidroid.metadata.libretrodb.db.LibretroDBManager
import com.omnidroid.metadata.libretrodb.db.entity.InstalledSlice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SliceStatus(
    val id: String,
    val systems: String,
    val installed: InstalledSlice?,
)

@Composable
fun GameDatabasesScreen(
    modifier: Modifier = Modifier,
    viewModel: GameDatabasesViewModel,
) {
    val slices by viewModel.slices.collectAsState()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(slices, key = { it.id }) { slice ->
            Column {
                Text(text = slice.systems, style = MaterialTheme.typography.titleMedium)
                val installed = slice.installed
                if (installed == null) {
                    Text(
                        text = stringResource(R.string.game_databases_missing),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { viewModel.retry(slice.id) }) {
                        Text(text = stringResource(R.string.game_databases_retry))
                    }
                } else {
                    Text(
                        text =
                            stringResource(
                                R.string.game_databases_installed,
                                installed.rows,
                                installed.sha256.take(8),
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val slices: StateFlow<List<SliceStatus>> =
        manager.dbInstance.sliceDao().observeAll().map { installed ->
            val byId = installed.associateBy { it.sliceId }
            SliceCatalog.slices.map { slice ->
                SliceStatus(slice.id, slice.systems.joinToString(", "), byId[slice.id])
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(sliceId: String) {
        val slice = SliceCatalog.byId(sliceId) ?: return
        val core = slice.cores.firstNotNullOfOrNull { findByName(it) } ?: return
        viewModelScope.launch {
            installer.ensureSlices(context, listOf(core))
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
