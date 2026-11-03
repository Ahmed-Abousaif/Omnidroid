package com.omnidroid.app.mobile.feature.game

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.omnidroid.app.mobile.shared.NotificationsManager
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.app.shared.game.BaseGameActivity
import com.omnidroid.lib.library.db.entity.Game
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.system.exitProcess

@AndroidEntryPoint
class GameService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentGameIntent: Intent? = null
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var isForegroundStarted = false

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            awaitTermination()
            withContext(Dispatchers.Main) {
                ServiceCompat.stopForeground(this@GameService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                exitProcess(0)
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_QUIT_GAME -> {
                    handleQuitGame()
                }
                else -> {
                    val gameIntent = intent.getParcelableExtra<Intent>(EXTRA_GAME_ACTIVITY_INTENT)
                    if (gameIntent != null) {
                        currentGameIntent = gameIntent
                        sessionStartTime = intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis())
                        displayNotification(gameIntent, sessionStartTime)
                        loadCoverAndRefresh(gameIntent, sessionStartTime)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun displayNotification(gameIntent: Intent, startTime: Long, coverBitmap: Bitmap? = null) {
        val notification = NotificationsManager(applicationContext).gameRunningNotification(gameIntent, startTime, coverBitmap)
        if (!isForegroundStarted) {
            ServiceCompat.startForeground(
                this,
                NotificationsManager.GAME_RUNNING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            isForegroundStarted = true
        } else {
            val notificationManager = ContextCompat.getSystemService(applicationContext, NotificationManager::class.java)
            notificationManager?.notify(NotificationsManager.GAME_RUNNING_NOTIFICATION_ID, notification)
        }
    }

    private fun loadCoverAndRefresh(gameIntent: Intent, startTime: Long) {
        val game = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gameIntent.getSerializableExtra(BaseGameActivity.EXTRA_GAME, Game::class.java)
            } else {
                @Suppress("DEPRECATION")
                gameIntent.getSerializableExtra(BaseGameActivity.EXTRA_GAME) as? Game
            }
        }.getOrNull() ?: return

        serviceScope.launch(Dispatchers.IO) {
            val file = CoverUtils.resolveCustomCoverFile(game)
            var bitmap: Bitmap? = null
            if (file != null && file.exists()) {
                bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            }
            if (bitmap == null) {
                val data = CoverUtils.coverData(game)
                if (data != null) {
                    val request = ImageRequest.Builder(applicationContext)
                        .data(data)
                        .size(256, 256)
                        .build()
                    val result = (applicationContext.imageLoader.execute(request) as? SuccessResult)?.drawable
                    bitmap = (result as? BitmapDrawable)?.bitmap
                }
            }
            if (bitmap != null) {
                withContext(Dispatchers.Main) {
                    if (isForegroundStarted) {
                        displayNotification(gameIntent, startTime, bitmap)
                    }
                }
            }
        }
    }

    private fun handleQuitGame() {
        Timber.i("GameService: handleQuitGame triggered from notification")
        val activity = activeGameActivity
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            activity.runOnUiThread {
                activity.saveAndQuitFromNotification()
            }
        } else {
            requestTermination()
        }
    }

    private fun hideNotification() {
        NotificationManagerCompat.from(this).cancel(NotificationsManager.GAME_RUNNING_NOTIFICATION_ID)
    }

    override fun onDestroy() {
        isForegroundStarted = false
        serviceScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        hideNotification()
        super.onDestroy()
    }

    companion object {
        const val ACTION_QUIT_GAME = "com.omnidroid.app.ACTION_QUIT_GAME"
        const val EXTRA_GAME_ACTIVITY_INTENT = "EXTRA_GAME_ACTIVITY_INTENT"
        const val EXTRA_START_TIME = "EXTRA_START_TIME"

        @Volatile
        var activeGameActivity: BaseGameActivity? = null

        private data class GameProcessTask(
            val task: suspend () -> Unit = {},
            val terminate: Boolean = false,
        )

        private val tasks = Channel<GameProcessTask>(capacity = Channel.BUFFERED)

        fun startService(
            context: Context,
            gameActivityIntent: Intent,
            startTime: Long = System.currentTimeMillis(),
        ) {
            context.startService(
                Intent(context, GameService::class.java).apply {
                    putExtra(EXTRA_GAME_ACTIVITY_INTENT, gameActivityIntent)
                    putExtra(EXTRA_START_TIME, startTime)
                },
            )
        }

        fun schedule(task: suspend () -> Unit) {
            val result = tasks.trySend(GameProcessTask(task = task))
            Timber.i("GameService.schedule sent=%s", result.isSuccess)
        }

        fun requestTermination() {
            val result = tasks.trySend(GameProcessTask(terminate = true))
            Timber.i("GameService.requestTermination sent=%s", result.isSuccess)
        }

        private suspend fun awaitTermination() {
            for (task in tasks) {
                runCatching { task.task() }
                    .onFailure { Timber.e(it, "GameService task failed") }

                if (task.terminate) {
                    return
                }
            }
        }
    }
}
