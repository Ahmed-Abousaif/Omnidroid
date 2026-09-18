package com.omnidroid.app.shared

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.omnidroid.R
import com.omnidroid.app.OmnidroidApplication
import com.omnidroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.omnidroid.app.shared.covers.CustomCoverManager
import com.omnidroid.app.shared.game.GameLauncher
import com.omnidroid.app.shared.main.BusyActivity
import com.omnidroid.app.shared.savesync.SaveSyncWork
import com.omnidroid.app.tv.shared.TVHelper
import com.omnidroid.common.displayDetailsSettingsScreen
import com.omnidroid.common.displayToast
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.savesync.GameCloudSyncOverride
import com.omnidroid.lib.savesync.GameCloudSyncPreferences
import com.omnidroid.lib.savesync.GameFrameSpeedPreferences
import com.omnidroid.lib.savesync.SaveSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameInteractor(
    private val activity: BusyActivity,
    private val retrogradeDb: RetrogradeDatabase,
    private val useLeanback: Boolean,
    private val shortcutsGenerator: ShortcutsGenerator,
    private val gameLauncher: GameLauncher,
    private val saveSyncManager: SaveSyncManager,
    private val requestNotificationsPermission: ((onResult: (Boolean) -> Unit) -> Unit)? = null,
) {
    private val cloudPrefs = GameCloudSyncPreferences(activity.activity())
    private val frameSpeedPrefs = GameFrameSpeedPreferences(activity.activity())
    private val appScope get() = OmnidroidApplication.scope(activity.activity())

    fun onGamePlay(game: Game) {
        if (!ensureNotBusy()) {
            return
        }
        withNotificationsPermission {
            gameLauncher.launchGameAsync(activity.activity(), game, true, useLeanback)
        }
    }

    fun onGameRestart(game: Game) {
        if (!ensureNotBusy()) {
            return
        }
        withNotificationsPermission {
            gameLauncher.launchGameAsync(activity.activity(), game, false, useLeanback)
        }
    }

    fun onFavoriteToggle(
        game: Game,
        isFavorite: Boolean,
    ) {
        appScope.launch {
            retrogradeDb.gameDao().update(game.copy(isFavorite = isFavorite))
        }
    }

    fun onCreateShortcut(game: Game) {
        appScope.launch {
            shortcutsGenerator.pinShortcutForGame(game)
        }
    }

    fun onSetCustomCover(
        game: Game,
        uri: Uri,
    ) {
        appScope.launch {
            try {
                val manager = CustomCoverManager(activity.activity())
                val path = manager.saveCustomCover(game.id, uri)
                val current = retrogradeDb.gameDao().selectById(game.id) ?: game
                retrogradeDb.gameDao().update(current.copy(customCoverPath = path))
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    activity.activity().displayToast(R.string.game_custom_thumbnail_error)
                }
            }
        }
    }

    fun onRemoveCustomCover(game: Game) {
        appScope.launch {
            CustomCoverManager(activity.activity()).deleteCustomCover(game.id, game.customCoverPath)
            val current = retrogradeDb.gameDao().selectById(game.id) ?: game
            retrogradeDb.gameDao().update(current.copy(customCoverPath = null))
        }
    }

    fun onSetCustomName(
        game: Game,
        name: String,
    ) {
        appScope.launch {
            val current = retrogradeDb.gameDao().selectById(game.id) ?: game
            val trimmed = name.trim()
            val customName = trimmed.takeIf { it.isNotEmpty() && it != current.title }
            retrogradeDb.gameDao().update(current.copy(customName = customName))
        }
    }

    fun onRemoveCustomName(game: Game) {
        appScope.launch {
            val current = retrogradeDb.gameDao().selectById(game.id) ?: game
            retrogradeDb.gameDao().update(current.copy(customName = null))
        }
    }

    fun getFrameSpeed(game: Game): Int = frameSpeedPrefs.get(game.id)

    fun setFrameSpeed(
        game: Game,
        speed: Int,
    ) {
        frameSpeedPrefs.set(game.id, speed)
    }

    fun supportShortcuts(): Boolean {
        return shortcutsGenerator.supportShortcuts()
    }

    fun isSaveSyncSupported(): Boolean = saveSyncManager.isSupported()

    fun getCloudOverride(game: Game): GameCloudSyncOverride = cloudPrefs.getOverride(game.id)

    fun setCloudOverride(
        game: Game,
        override: GameCloudSyncOverride,
    ) {
        cloudPrefs.setOverride(game.id, override)
    }

    fun syncGameNow(game: Game) {
        SaveSyncWork.enqueueGameWork(activity.activity().applicationContext, game.fileName)
    }

    private fun withNotificationsPermission(onGranted: () -> Unit) {
        val host = activity.activity()
        if (useLeanback || TVHelper.isTV(host) || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onGranted()
            return
        }
        if (hasNotificationsPermission(host)) {
            onGranted()
            return
        }

        val requester = requestNotificationsPermission
        if (requester == null) {
            host.displayToast(R.string.game_interactor_notification_permission_required)
            return
        }

        requester { granted ->
            if (granted) {
                onGranted()
                return@requester
            }

            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    host,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            ) {
                openNotificationSettings(host)
            }
        }
    }

    private fun hasNotificationsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openNotificationSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent) }
            .onFailure { context.displayDetailsSettingsScreen() }
    }

    private fun ensureNotBusy(): Boolean {
        if (activity.isBusy()) {
            activity.activity().displayToast(R.string.game_interactory_busy)
            return false
        }
        return true
    }
}
