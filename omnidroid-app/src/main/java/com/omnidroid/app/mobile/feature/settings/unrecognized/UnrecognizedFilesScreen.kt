package com.omnidroid.app.mobile.feature.settings.unrecognized

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.UnrecognizedFile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@Composable
fun UnrecognizedFilesScreen(
    modifier: Modifier = Modifier,
    viewModel: UnrecognizedFilesViewModel,
) {
    val files by viewModel.files.collectAsState()
    if (files.isEmpty()) {
        Text(
            text = stringResource(R.string.unrecognized_files_empty),
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier.padding(24.dp),
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(files, key = { it.fileUri }) { file ->
            UnrecognizedFileRow(file)
        }
    }
}

@Composable
private fun UnrecognizedFileRow(file: UnrecognizedFile) {
    androidx.compose.foundation.layout.Column {
        Text(text = file.fileName, style = MaterialTheme.typography.titleMedium)
        Text(
            text = file.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

class UnrecognizedFilesViewModel(
    database: RetrogradeDatabase,
) : ViewModel() {
    val files: StateFlow<List<UnrecognizedFile>> =
        database.unrecognizedFileDao().observeAll().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    class Factory(
        private val database: RetrogradeDatabase,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return UnrecognizedFilesViewModel(database) as T
        }
    }
}
