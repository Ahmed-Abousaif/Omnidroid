package com.omnidroid.app.shared.game.backend

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.omnidroid.R
import com.omnidroid.lib.android.RetrogradeComponentActivity
import com.omnidroid.lib.backend.RequirementAction
import timber.log.Timber
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Generic Activity for fulfilling RequirementAction via SAF document picker.
 * Replaces console-specific file picker trampolines.
 */
class RequirementPickerLauncherActivity : RetrogradeComponentActivity() {

    private val pickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                val destDirRel = intent.getStringExtra(EXTRA_DESTINATION_DIR) ?: "requirements"
                val destFileName = intent.getStringExtra(EXTRA_DESTINATION_FILE_NAME)
                val isArchive = intent.getBooleanExtra(EXTRA_IS_ARCHIVE, false)
                val backend = intent.getStringExtra(EXTRA_BACKEND)
                val action = if (backend != null) {
                    RequirementAction.BackendImport(
                        com.omnidroid.lib.backend.BackendID(backend),
                        intent.getStringExtra(EXTRA_OPERATION) ?: "firmware",
                        emptyList(),
                    )
                } else if (isArchive) {
                    RequirementAction.ArchiveImport(
                        destinationDir = destDirRel,
                        fileFilterPattern = intent.getStringExtra(EXTRA_FILTER_PATTERN) ?: ".*\\.nca$",
                    )
                } else {
                    RequirementAction.FileImport(
                        destinationDir = destDirRel,
                        destinationFileName = destFileName,
                    )
                }

                val status = android.widget.TextView(this).apply { text = "Installing…"; setPadding(40, 40, 40, 40) }
                setContentView(android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    addView(android.widget.ProgressBar(this@RequirementPickerLauncherActivity))
                    addView(status)
                })
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        RequirementImporter.import(applicationContext, uri, action) { message ->
                            runOnUiThread { status.text = message }
                        }
                    }
                    Toast.makeText(this@RequirementPickerLauncherActivity, result.message ?: if (result.success) "Import complete" else "Import failed", Toast.LENGTH_LONG).show()
                    finish()
                }
            } else {
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            try {
                pickerLauncher.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                Timber.e(e, "Error launching file picker")
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_DESTINATION_DIR = "extra_destination_dir"
        private const val EXTRA_DESTINATION_FILE_NAME = "extra_destination_file_name"
        private const val EXTRA_IS_ARCHIVE = "extra_is_archive"
        private const val EXTRA_FILTER_PATTERN = "extra_filter_pattern"
        private const val EXTRA_BACKEND = "extra_backend"
        private const val EXTRA_OPERATION = "extra_operation"

        fun launch(context: Context, destinationDir: String, destinationFileName: String?) {
            val intent = Intent(context, RequirementPickerLauncherActivity::class.java).apply {
                putExtra(EXTRA_DESTINATION_DIR, destinationDir)
                putExtra(EXTRA_DESTINATION_FILE_NAME, destinationFileName)
                putExtra(EXTRA_IS_ARCHIVE, false)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }

        fun launch(context: Context, action: RequirementAction) {
            val intent = Intent(context, RequirementPickerLauncherActivity::class.java).apply {
                when (action) {
                    is RequirementAction.BackendImport -> {
                        putExtra(EXTRA_BACKEND, action.backendID.value)
                        putExtra(EXTRA_OPERATION, action.operation)
                    }
                    is RequirementAction.FileImport -> {
                        putExtra(EXTRA_DESTINATION_DIR, action.destinationDir)
                        putExtra(EXTRA_DESTINATION_FILE_NAME, action.destinationFileName)
                        putExtra(EXTRA_IS_ARCHIVE, false)
                    }
                    is RequirementAction.ArchiveImport -> {
                        putExtra(EXTRA_DESTINATION_DIR, action.destinationDir)
                        putExtra(EXTRA_IS_ARCHIVE, true)
                        putExtra(EXTRA_FILTER_PATTERN, action.fileFilterPattern)
                    }
                    else -> {}
                }
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}
