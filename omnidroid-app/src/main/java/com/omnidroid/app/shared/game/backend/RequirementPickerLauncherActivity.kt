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
                val action = if (isArchive) {
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

                val result = RequirementImporter.import(this, uri, action)
                if (result.success) {
                    val msg = if (destDirRel.contains("registered")) {
                        getString(R.string.settings_switch_firmware_import_success)
                    } else {
                        getString(R.string.settings_switch_keys_import_success)
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                } else {
                    val msg = if (destDirRel.contains("registered")) {
                        getString(R.string.settings_switch_firmware_import_failed)
                    } else {
                        getString(R.string.settings_switch_keys_import_failed)
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            finish()
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
