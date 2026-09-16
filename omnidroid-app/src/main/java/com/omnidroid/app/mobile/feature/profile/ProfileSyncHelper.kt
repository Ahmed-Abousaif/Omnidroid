package com.omnidroid.app.mobile.feature.profile

import android.content.Context
import com.omnidroid.lib.storage.DirectoriesManager
import java.io.File

/**
 * Bridges [UserProfileStore] with the file-based cloud sync infrastructure.
 *
 * Before a sync: writes the current profile state to `profile/profile.json` on-device so
 * [SaveSyncManagerImpl] picks it up and uploads it.
 *
 * After a sync: reads `profile/profile.json` (which may now contain the remote version)
 * and merges it into [UserProfileStore] via the conflict-aware [UserProfileStore.importFromJson].
 */
class ProfileSyncHelper(
    context: Context,
    private val directoriesManager: DirectoriesManager,
) {
    private val profileStore = UserProfileStore(context)

    private val profileFile: File
        get() = File(directoriesManager.getProfileDirectory(), PROFILE_FILENAME)

    /**
     * Serialize current profile to disk so the sync worker can upload it.
     * Call this *before* invoking [SaveSyncManager.sync].
     */
    fun exportBeforeSync() {
        runCatching {
            val json = profileStore.exportToJson()
            profileFile.writeText(json)
        }
    }

    /**
     * After the sync completes the remote profile.json has been downloaded (if newer).
     * Merge it into the local profile store.
     * Call this *after* [SaveSyncManager.sync] returns.
     */
    fun importAfterSync() {
        runCatching {
            if (profileFile.exists() && profileFile.length() > 0) {
                profileStore.importFromJson(profileFile.readText())
            }
        }
    }

    companion object {
        private const val PROFILE_FILENAME = "profile.json"
    }
}
