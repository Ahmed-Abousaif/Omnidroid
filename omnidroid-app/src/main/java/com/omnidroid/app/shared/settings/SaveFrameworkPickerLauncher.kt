package com.omnidroid.app.shared.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.showOmnidroidDialog
import com.omnidroid.app.tv.folderpicker.TVFolderPickerActivity
import com.omnidroid.app.tv.shared.TVHelper
import com.omnidroid.app.utils.android.displayErrorDialog
import com.omnidroid.lib.android.RetrogradeActivity
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.saves.CustomSaveStorage
import com.omnidroid.lib.storage.DirectoriesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SaveFrameworkPickerLauncher : RetrogradeActivity() {
    @Inject
    lateinit var directoriesManager: DirectoriesManager

    @Inject
    lateinit var customSaveStorage: CustomSaveStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            checkPermissionsAndLaunch()
        }
    }

    private fun checkPermissionsAndLaunch() {
        if (TVHelper.isSAFSupported(this)) {
            launchSafPicker()
        } else {
            // Extra permissions are only asked if the user opts to use this feature in settings
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && !hasStorageWritePermission()) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                    ),
                    REQUEST_CODE_PERMISSIONS,
                )
            } else {
                launchTvPicker()
            }
        }
    }

    private fun hasStorageWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

    private fun launchSafPicker() {
        // Any folder can be chosen (including network folders and cloud folders).
        // Intent.EXTRA_LOCAL_ONLY is deliberately omitted / set to false.
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                putExtra(Intent.EXTRA_LOCAL_ONLY, false)
            }
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER)
        } catch (e: Exception) {
            Timber.e(e, "Storage Access Framework picker not available")
            showStorageAccessFrameworkNotSupportedDialog()
        }
    }

    private fun launchTvPicker() {
        try {
            startActivityForResult(
                Intent(this, TVFolderPickerActivity::class.java),
                REQUEST_CODE_PICK_FOLDER_TV,
            )
        } catch (e: Exception) {
            Timber.e(e, "TV folder picker failed to launch")
            showStorageAccessFrameworkNotSupportedDialog()
        }
    }

    private fun showStorageAccessFrameworkNotSupportedDialog() {
        val message = getString(R.string.dialog_saf_save_dir_not_found, directoriesManager.getSavesDirectory())
        val actionLabel = getString(R.string.ok)
        displayErrorDialog(message, actionLabel) { finish() }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                launchTvPicker()
            } else {
                finish()
            }
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        resultData: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, resultData)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_PICK_FOLDER -> {
                    val newValue = resultData?.data
                    if (newValue != null) {
                        migrateAndSetCustomFolder(newValue.toString(), newValue)
                        return
                    }
                }
                REQUEST_CODE_PICK_FOLDER_TV -> {
                    val newValue = resultData?.extras?.getString(TVFolderPickerActivity.RESULT_DIRECTORY_PATH)
                    if (!newValue.isNullOrBlank()) {
                        migrateAndSetCustomFolder(newValue, null)
                        return
                    }
                }
            }
        }
        finish()
    }

    private fun migrateAndSetCustomFolder(
        newFolderValue: String,
        safUri: Uri?,
    ) {
        val currentUri = customSaveStorage.getCustomSavesFolderUriString()

        if (newFolderValue == currentUri) {
            finish()
            return
        }

        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (safUri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(safUri, takeFlags)
            }
        }

        val handle =
            showOmnidroidDialog(
                activity = this,
                title = getString(R.string.settings_migrating_saves_title),
                message = getString(R.string.settings_migrating_saves_message),
                actions = emptyList(),
                cancelable = false,
            )

        lifecycleScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    customSaveStorage.migrateStorage(currentUri, newFolderValue)
                }
            handle?.dismiss()

            when (result) {
                is CustomSaveStorage.MigrationResult.Success -> {
                    if (safUri != null) {
                        releaseOldPersistedUris(safUri)
                    }
                    finish()
                }
                is CustomSaveStorage.MigrationResult.Failure -> {
                    if (safUri != null) {
                        runCatching {
                            contentResolver.releasePersistableUriPermission(safUri, takeFlags)
                        }
                    }
                    displayErrorDialog(
                        message = getString(R.string.settings_migrating_saves_failed),
                        actionLabel = getString(R.string.ok),
                    ) {
                        finish()
                    }
                }
            }
        }
    }

    private fun releaseOldPersistedUris(newUri: Uri) {
        val legacyPrefs = SharedPreferencesHelper.getLegacySharedPreferences(this)
        val romsKey = getString(com.omnidroid.lib.R.string.pref_key_extenral_folder)
        val romsUriString = legacyPrefs.getString(romsKey, null)

        contentResolver.persistedUriPermissions
            .filter { it.isReadPermission || it.isWritePermission }
            .filter { it.uri != newUri && it.uri.toString() != romsUriString }
            .forEach {
                runCatching {
                    contentResolver.releasePersistableUriPermission(
                        it.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
    }

    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 101
        private const val REQUEST_CODE_PICK_FOLDER_TV = 102
        private const val REQUEST_CODE_PERMISSIONS = 201

        fun pickFolder(context: Context) {
            context.startActivity(Intent(context, SaveFrameworkPickerLauncher::class.java))
        }
    }
}
