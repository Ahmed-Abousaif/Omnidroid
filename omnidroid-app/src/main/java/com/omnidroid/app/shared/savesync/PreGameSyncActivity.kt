package com.omnidroid.app.shared.savesync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.omnidroid.R
import com.omnidroid.app.shared.cast.CastDisplayManager
import com.omnidroid.app.shared.game.BaseGameActivity
import com.omnidroid.app.shared.main.GameLaunchTaskHandler
import com.omnidroid.common.kotlin.serializable
import com.omnidroid.lib.android.RetrogradeComponentActivity
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.savesync.SaveSyncManager
import com.omnidroid.lib.savesync.SaveSyncRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PreGameSyncActivity : RetrogradeComponentActivity() {
    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var castDisplayManager: CastDisplayManager

    private var syncJob: Job? = null
    private var finished = false
    private var isExternalLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pre_game_sync)

        val game = intent.serializable<Game>(EXTRA_GAME) ?: run { abort(); return }
        val coreConfig =
            intent.serializable<SystemCoreConfig>(EXTRA_CORE_CONFIG) ?: run { abort(); return }
        val loadSave = intent.getBooleanExtra(EXTRA_LOAD_SAVE, true)
        val leanback = intent.getBooleanExtra(EXTRA_LEANBACK, false)
        isExternalLaunch = intent.getBooleanExtra(EXTRA_EXTERNAL_LAUNCH, false)

        findViewById<TextView>(R.id.pre_game_sync_message).text =
            getString(R.string.pre_game_sync_message, game.displayName)

        findViewById<Button>(R.id.pre_game_sync_play_anyway).setOnClickListener {
            proceed(coreConfig, game, loadSave, leanback)
        }
        findViewById<Button>(R.id.pre_game_sync_cancel).setOnClickListener { abort() }

        syncJob =
            lifecycleScope.launch {
                val work =
                    launch {
                        saveSyncManager.sync(
                            SaveSyncRequest(
                                cores = setOf(coreConfig.coreID),
                                gameFileName = game.fileName,
                                includeSaves = true,
                                includeStates = true,
                                includePreviews = true,
                            ),
                        )
                    }
                val completed = withTimeoutOrNull(TIMEOUT_MS) {
                    work.join()
                    true
                }
                if (completed == true) {
                    proceed(coreConfig, game, loadSave, leanback)
                }
            }
    }

    private fun proceed(
        coreConfig: SystemCoreConfig,
        game: Game,
        loadSave: Boolean,
        leanback: Boolean,
    ) {
        if (finished) return
        finished = true
        syncJob?.cancel()
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
        gameLaunchTaskHandler.handleGameStart(applicationContext)
        castDisplayManager.hideIdleForGame()
        BaseGameActivity.launchGame(
            this,
            coreConfig,
            game,
            loadSave,
            leanback,
            castDisplayManager.launchOptions(),
            isExternalLaunch,
        )
    }

    private fun abort() {
        if (finished) return
        finished = true
        syncJob?.cancel()
        setResult(Activity.RESULT_CANCELED)
        if (isExternalLaunch) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BaseGameActivity.REQUEST_PLAY_GAME) {
            setResult(resultCode, data)
            if (isExternalLaunch) {
                finishAndRemoveTask()
            } else {
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_GAME = "EXTRA_GAME"
        private const val EXTRA_CORE_CONFIG = "EXTRA_CORE_CONFIG"
        private const val EXTRA_LOAD_SAVE = "EXTRA_LOAD_SAVE"
        private const val EXTRA_LEANBACK = "EXTRA_LEANBACK"
        private const val EXTRA_EXTERNAL_LAUNCH = "EXTRA_EXTERNAL_LAUNCH"
        private const val TIMEOUT_MS = 20_000L

        fun intent(
            activity: Activity,
            game: Game,
            coreConfig: SystemCoreConfig,
            loadSave: Boolean,
            leanback: Boolean,
            isExternalLaunch: Boolean = false,
        ): Intent {
            return Intent(activity, PreGameSyncActivity::class.java).apply {
                val uri = Uri.parse(game.fileUri)
                if (uri.scheme.equals("content", ignoreCase = true)) {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                putExtra(EXTRA_GAME, game)
                putExtra(EXTRA_CORE_CONFIG, coreConfig)
                putExtra(EXTRA_LOAD_SAVE, loadSave)
                putExtra(EXTRA_LEANBACK, leanback)
                putExtra(EXTRA_EXTERNAL_LAUNCH, isExternalLaunch)
            }
        }
    }
}
