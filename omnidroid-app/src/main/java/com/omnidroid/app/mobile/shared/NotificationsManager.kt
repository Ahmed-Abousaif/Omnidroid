package com.omnidroid.app.mobile.shared

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.game.GameActivity
import com.omnidroid.app.mobile.feature.game.GameService
import com.omnidroid.app.shared.game.BaseGameActivity
import com.omnidroid.app.shared.library.CoreUpdateBroadcastReceiver
import com.omnidroid.app.shared.library.LibraryIndexBroadcastReceiver
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.db.entity.Game

class NotificationsManager(private val applicationContext: Context) {

    fun gameRunningNotification(
        gameIntent: Intent,
        sessionStartTime: Long = System.currentTimeMillis(),
        coverBitmap: Bitmap? = null,
    ): Notification {
        createGameSessionNotificationChannel()

        val game = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gameIntent.getSerializableExtra(BaseGameActivity.EXTRA_GAME, Game::class.java)
            } else {
                @Suppress("DEPRECATION")
                gameIntent.getSerializableExtra(BaseGameActivity.EXTRA_GAME) as? Game
            }
        }.getOrNull()

        val gameTitle = game?.displayName?.takeIf { it.isNotBlank() }
            ?: applicationContext.getString(R.string.game_running_notification_title_alternative)

        val systemName = game?.systemId?.let { systemId ->
            runCatching { GameSystem.findById(systemId) }.getOrNull()?.let {
                applicationContext.getString(it.titleResId)
            }
        }

        val subtitle = if (systemName != null) {
            applicationContext.getString(R.string.game_running_notification_subtitle_with_system, systemName)
        } else {
            applicationContext.getString(R.string.game_running_notification_message)
        }

        val expandedText = applicationContext.getString(R.string.game_running_notification_expanded_text)

        // Intent to return directly to the running GameActivity
        val resumeIntent = Intent(gameIntent).apply {
            setClass(applicationContext, GameActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            REQUEST_CODE_RESUME,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Action: Resume
        val resumeAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification_play,
            applicationContext.getString(R.string.game_running_notification_action_resume),
            contentIntent,
        ).build()

        // Action: Save & Quit (signals GameService to cleanly save and terminate)
        val quitIntent = Intent(applicationContext, GameService::class.java).apply {
            action = GameService.ACTION_QUIT_GAME
        }
        val quitPendingIntent = PendingIntent.getService(
            applicationContext,
            REQUEST_CODE_QUIT,
            quitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val quitAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification_quit,
            applicationContext.getString(R.string.game_running_notification_action_quit),
            quitPendingIntent,
        ).build()

        val builder = NotificationCompat.Builder(applicationContext, GAME_SESSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_omnidroid_tiny)
            .setContentTitle(gameTitle)
            .setContentText(subtitle)
            .setSubText(applicationContext.getString(R.string.omnidroid_name))
            .setShowWhen(true)
            .setWhen(sessionStartTime)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(ContextCompat.getColor(applicationContext, R.color.main_color))
            .setContentIntent(contentIntent)
            .addAction(resumeAction)
            .addAction(quitAction)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(gameTitle)
                    .bigText("$subtitle\n$expandedText")
                    .setSummaryText(systemName ?: applicationContext.getString(R.string.omnidroid_name))
            )

        if (coverBitmap != null) {
            builder.setLargeIcon(coverBitmap)
        }

        return builder.build()
    }

    fun libraryIndexingNotification(): Notification {
        createDefaultNotificationChannel()

        val broadcastIntent = Intent(applicationContext, LibraryIndexBroadcastReceiver::class.java)
        val broadcastPendingIntent: PendingIntent =
            PendingIntent.getBroadcast(
                applicationContext,
                0,
                broadcastIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat.Builder(applicationContext, DEFAULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_omnidroid_tiny)
                .setContentTitle(applicationContext.getString(R.string.library_index_notification_title))
                .setContentText(applicationContext.getString(R.string.library_index_notification_message))
                .setProgress(100, 0, true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(
                    NotificationCompat.Action(
                        null,
                        applicationContext.getString(R.string.cancel),
                        broadcastPendingIntent,
                    ),
                )

        return builder.build()
    }

    fun installingCoresNotification(): Notification {
        createDefaultNotificationChannel()

        val broadcastIntent = Intent(applicationContext, CoreUpdateBroadcastReceiver::class.java)
        val broadcastPendingIntent: PendingIntent =
            PendingIntent.getBroadcast(
                applicationContext,
                0,
                broadcastIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat.Builder(applicationContext, DEFAULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_omnidroid_tiny)
                .setContentTitle(
                    applicationContext.getString(
                        com.omnidroid.ext.R.string.installing_core_notification_title,
                    ),
                )
                .setContentText(
                    applicationContext.getString(
                        com.omnidroid.ext.R.string.installing_core_notification_message,
                    ),
                )
                .setProgress(100, 0, true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(
                    NotificationCompat.Action(
                        null,
                        applicationContext.getString(R.string.cancel),
                        broadcastPendingIntent,
                    ),
                )

        return builder.build()
    }

    fun switchIconExtractionNotification(): Notification {
        createDefaultNotificationChannel()

        val builder =
            NotificationCompat.Builder(applicationContext, DEFAULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_omnidroid_tiny)
                .setContentTitle(applicationContext.getString(R.string.switch_icon_notification_title))
                .setContentText(applicationContext.getString(R.string.switch_icon_notification_message))
                .setProgress(100, 0, true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

        return builder.build()
    }

    fun saveSyncNotification(): Notification {
        createDefaultNotificationChannel()

        val builder =
            NotificationCompat.Builder(applicationContext, DEFAULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_omnidroid_tiny)
                .setContentTitle(applicationContext.getString(R.string.save_sync_notification_title))
                .setContentText(applicationContext.getString(R.string.save_sync_notification_message))
                .setProgress(100, 0, true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

        return builder.build()
    }

    private fun createDefaultNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = applicationContext.getString(R.string.notification_channel_name)
            val importance = NotificationManager.IMPORTANCE_MIN
            val mChannel = NotificationChannel(DEFAULT_CHANNEL_ID, name, importance)
            val notificationManager =
                ContextCompat.getSystemService(applicationContext, NotificationManager::class.java)

            notificationManager?.createNotificationChannel(mChannel)
        }
    }

    fun createGameSessionNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = applicationContext.getString(R.string.notification_channel_game_session)
            val desc = applicationContext.getString(R.string.notification_channel_game_session_desc)
            val channel = NotificationChannel(GAME_SESSION_CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                description = desc
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager =
                ContextCompat.getSystemService(applicationContext, NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val DEFAULT_CHANNEL_ID = "DEFAULT_CHANNEL_ID"
        const val GAME_SESSION_CHANNEL_ID = "game_session_channel"

        const val LIBRARY_INDEXING_NOTIFICATION_ID = 1
        const val SAVE_SYNC_NOTIFICATION_ID = 2
        const val GAME_RUNNING_NOTIFICATION_ID = 3
        const val CORE_INSTALL_NOTIFICATION_ID = 4
        const val SWITCH_ICON_EXTRACTION_NOTIFICATION_ID = 5

        const val REQUEST_CODE_RESUME = 1001
        const val REQUEST_CODE_QUIT = 1002
    }
}
