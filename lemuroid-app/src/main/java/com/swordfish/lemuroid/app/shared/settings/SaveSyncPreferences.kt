package com.swordfish.lemuroid.app.shared.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.savesync.SaveBackupManager
import com.swordfish.lemuroid.app.shared.savesync.SaveBackupWork
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.savesync.ConflictResolution
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager

class SaveSyncPreferences(private val saveSyncManager: SaveSyncManager) {
    fun addSaveSyncPreferences(preferenceScreen: PreferenceScreen) {
        val context = preferenceScreen.context
        saveSyncManager.getProviders().forEach { provider ->
            Preference(context).apply {
                key = keyProvider(provider.id)
                preferenceScreen.addPreference(this)
            }
            Preference(context).apply {
                key = keySignOut(provider.id)
                preferenceScreen.addPreference(this)
            }
        }
        SwitchPreference(context).apply {
            key = keySyncEnabled(context)
            preferenceScreen.addPreference(this)
        }
        MultiSelectListPreference(context).apply {
            key = keySyncCores(context)
            preferenceScreen.addPreference(this)
        }
        SwitchPreference(context).apply {
            key = keyAutoSync(context)
            preferenceScreen.addPreference(this)
        }
        ListPreference(context).apply {
            key = keyInterval(context)
            preferenceScreen.addPreference(this)
        }
        SwitchPreference(context).apply {
            key = keyBeforeGame(context)
            preferenceScreen.addPreference(this)
        }
        Preference(context).apply {
            key = keyForceSync(context)
            preferenceScreen.addPreference(this)
        }
        Preference(context).apply {
            key = keyConflicts(context)
            preferenceScreen.addPreference(this)
        }
        Preference(context).apply {
            key = keyExport(context)
            preferenceScreen.addPreference(this)
        }
        Preference(context).apply {
            key = keyImport(context)
            preferenceScreen.addPreference(this)
        }
        updatePreferences(preferenceScreen, false)
    }

    fun updatePreferences(
        preferenceScreen: PreferenceScreen,
        syncInProgress: Boolean,
    ) {
        val context = preferenceScreen.context
        val configured = saveSyncManager.isConfigured()
        saveSyncManager.getProviders().forEach { provider ->
            preferenceScreen.findPreference<Preference>(keyProvider(provider.id))?.apply {
                title = provider.displayName
                summary =
                    buildString {
                        append(provider.accountLabel)
                        if (provider.remoteUsage.isNotBlank()) {
                            append("\n")
                            append(provider.remoteUsage)
                        }
                        append("\n")
                        append(provider.lastSync)
                        if (!provider.lastError.isNullOrBlank()) {
                            append("\n")
                            append(context.getString(R.string.settings_save_sync_last_error, provider.lastError))
                        }
                    }
                isEnabled = !syncInProgress
                isIconSpaceReserved = false
            }
            preferenceScreen.findPreference<Preference>(keySignOut(provider.id))?.apply {
                title =
                    if (provider.configured) {
                        context.getString(R.string.settings_save_sync_sign_out)
                    } else {
                        context.getString(R.string.settings_save_sync_connect)
                    }
                isEnabled = !syncInProgress
                isIconSpaceReserved = false
                isVisible = true
            }
        }

        preferenceScreen.findPreference<Preference>(keySyncEnabled(context))?.apply {
            title = context.getString(R.string.settings_save_sync_include_saves)
            summary =
                context.getString(
                    R.string.settings_save_sync_include_saves_description,
                    saveSyncManager.computeSavesSpace(),
                )
            isEnabled = configured && !syncInProgress
            isIconSpaceReserved = false
        }

        preferenceScreen.findPreference<Preference>(keyAutoSync(context))?.apply {
            title = context.getString(R.string.settings_save_sync_enable_auto)
            isEnabled = configured && !syncInProgress
            summary = context.getString(R.string.settings_save_sync_enable_auto_description)
            isIconSpaceReserved = false
        }

        preferenceScreen.findPreference<ListPreference>(keyInterval(context))?.apply {
            title = context.getString(R.string.settings_save_sync_interval)
            entries = context.resources.getStringArray(R.array.pref_key_save_sync_interval_display_names)
            entryValues = context.resources.getStringArray(R.array.pref_key_save_sync_interval_values)
            setDefaultValue("3h")
            isEnabled = configured && !syncInProgress
            isIconSpaceReserved = false
            setOnPreferenceChangeListener { _, _ ->
                SaveSyncWork.enqueueAutoWork(context.applicationContext)
                true
            }
        }

        preferenceScreen.findPreference<Preference>(keyBeforeGame(context))?.apply {
            title = context.getString(R.string.settings_save_sync_before_game)
            summary = context.getString(R.string.settings_save_sync_before_game_description)
            isEnabled = configured && !syncInProgress
            isIconSpaceReserved = false
        }

        preferenceScreen.findPreference<Preference>(keyForceSync(context))?.apply {
            title = context.getString(R.string.settings_save_sync_refresh)
            isEnabled = configured && !syncInProgress
            summary =
                context.getString(
                    R.string.settings_save_sync_refresh_description,
                    saveSyncManager.getLastSyncInfo(),
                )
            isIconSpaceReserved = false
        }

        preferenceScreen.findPreference<MultiSelectListPreference>(keySyncCores(context))?.apply {
            title = context.getString(R.string.settings_save_sync_include_states)
            summary = context.getString(R.string.settings_save_sync_include_states_description)
            isEnabled = configured && !syncInProgress
            entries = CoreID.values().map { saveSyncManager.getDisplayNameForCore(context, it) }.toTypedArray()
            entryValues = CoreID.values().map { it.coreName }.toTypedArray()
            isIconSpaceReserved = false
        }

        val conflicts = saveSyncManager.getConflicts()
        preferenceScreen.findPreference<Preference>(keyConflicts(context))?.apply {
            title = context.getString(R.string.settings_save_sync_conflicts)
            summary =
                if (conflicts.isEmpty()) {
                    context.getString(R.string.settings_save_sync_conflicts_none)
                } else {
                    context.getString(R.string.settings_save_sync_conflicts_count, conflicts.size)
                }
            isEnabled = !syncInProgress
            isIconSpaceReserved = false
        }

        preferenceScreen.findPreference<Preference>(keyExport(context))?.apply {
            title = context.getString(R.string.settings_save_sync_export)
            summary = context.getString(R.string.settings_save_sync_export_description)
            isEnabled = !syncInProgress
            isIconSpaceReserved = false
        }
        preferenceScreen.findPreference<Preference>(keyImport(context))?.apply {
            title = context.getString(R.string.settings_save_sync_import)
            summary = context.getString(R.string.settings_save_sync_import_description)
            isEnabled = !syncInProgress
            isIconSpaceReserved = false
        }
    }

    fun onPreferenceTreeClick(
        activity: Activity?,
        preference: Preference,
    ): Boolean {
        val context = preference.context
        saveSyncManager.getProviders().forEach { provider ->
            if (preference.key == keyProvider(provider.id) || preference.key == keySignOut(provider.id)) {
                if (provider.configured) {
                    confirmSignOut(activity, provider.id, provider.displayName)
                } else {
                    provider.signInActivity?.let { activity?.startActivity(Intent(activity, it)) }
                }
                return true
            }
        }
        return when (preference.key) {
            keyForceSync(context) -> {
                SaveSyncWork.enqueueManualWork(context.applicationContext)
                true
            }
            keyAutoSync(context) -> {
                val enabled = (preference as? SwitchPreference)?.isChecked == true
                if (enabled) {
                    SaveSyncWork.enqueueAutoWork(context.applicationContext)
                } else {
                    SaveSyncWork.cancelAutoWork(context.applicationContext)
                }
                true
            }
            keyConflicts(context) -> {
                showConflicts(activity)
                true
            }
            keyExport(context) -> {
                val intent =
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/zip"
                        putExtra(Intent.EXTRA_TITLE, SaveBackupManager.suggestedFileName())
                    }
                activity?.startActivityForResult(intent, REQUEST_EXPORT)
                true
            }
            keyImport(context) -> {
                val intent =
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/zip"
                    }
                activity?.startActivityForResult(intent, REQUEST_IMPORT)
                true
            }
            else -> false
        }
    }

    fun handleActivityResult(
        context: Context,
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?,
    ) {
        if (resultCode != Activity.RESULT_OK) return
        val uri: Uri = data?.data ?: return
        when (requestCode) {
            REQUEST_EXPORT -> SaveBackupWork.enqueueExport(context, uri)
            REQUEST_IMPORT -> SaveBackupWork.enqueueImport(context, uri)
        }
    }

    private fun confirmSignOut(
        activity: Activity?,
        providerId: String,
        displayName: String,
    ) {
        val host = activity ?: return
        AlertDialog.Builder(host)
            .setTitle(R.string.settings_save_sync_sign_out)
            .setMessage(host.getString(R.string.settings_save_sync_sign_out_confirm, displayName))
            .setPositiveButton(R.string.ok) { _, _ -> saveSyncManager.signOut(providerId) }
            .setNegativeButton(R.string.pre_game_sync_cancel, null)
            .show()
    }

    private fun showConflicts(activity: Activity?) {
        val host = activity ?: return
        val conflicts = saveSyncManager.getConflicts()
        if (conflicts.isEmpty()) {
            AlertDialog.Builder(host)
                .setTitle(R.string.settings_save_sync_conflicts)
                .setMessage(R.string.settings_save_sync_conflicts_none)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        val labels = conflicts.map { it.relativePath }.toTypedArray()
        AlertDialog.Builder(host)
            .setTitle(R.string.settings_save_sync_conflicts)
            .setItems(labels) { _, which ->
                val conflict = conflicts[which]
                AlertDialog.Builder(host)
                    .setTitle(conflict.relativePath)
                    .setItems(
                        arrayOf(
                            host.getString(R.string.settings_save_sync_conflict_use_local),
                            host.getString(R.string.settings_save_sync_conflict_use_cloud),
                            host.getString(R.string.settings_save_sync_conflict_keep_both),
                        ),
                    ) { _, resolution ->
                        val action =
                            when (resolution) {
                                0 -> ConflictResolution.USE_LOCAL
                                1 -> ConflictResolution.USE_CLOUD
                                else -> ConflictResolution.KEEP_BOTH
                            }
                        saveSyncManager.resolveConflict(conflict.id, action)
                    }
                    .show()
            }
            .show()
    }

    private fun keySyncEnabled(context: Context) = context.getString(R.string.pref_key_save_sync_enable)

    private fun keyForceSync(context: Context) = context.getString(R.string.pref_key_save_sync_force_refresh)

    private fun keyAutoSync(context: Context) = context.getString(R.string.pref_key_save_sync_auto)

    private fun keySyncCores(context: Context) = context.getString(R.string.pref_key_save_sync_cores)

    private fun keyInterval(context: Context) = context.getString(R.string.pref_key_save_sync_interval)

    private fun keyBeforeGame(context: Context) = context.getString(R.string.pref_key_save_sync_before_game)

    private fun keyConflicts(context: Context) = context.getString(R.string.pref_key_save_sync_conflicts)

    private fun keyExport(context: Context) = context.getString(R.string.pref_key_save_sync_export)

    private fun keyImport(context: Context) = context.getString(R.string.pref_key_save_sync_import)

    private fun keyProvider(id: String) = "save_sync_provider_$id"

    private fun keySignOut(id: String) = "save_sync_sign_out_$id"

    companion object {
        const val REQUEST_EXPORT = 7101
        const val REQUEST_IMPORT = 7102
    }
}
