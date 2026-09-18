package com.omnidroid.app.shared.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.omnidroid.R
import com.omnidroid.app.OmnidroidApplication
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

object HdModeBatteryMonitor {
    const val MIN_PERCENT = 15

    @Volatile
    private var monitorJob: Job? = null

    fun isAllowed(percent: Int): Boolean = percent > MIN_PERCENT

    fun isAllowed(context: Context): Boolean = isAllowed(currentPercent(context))

    fun currentPercent(context: Context): Int {
        val sticky =
            ContextCompat.registerReceiver(
                context,
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        return sticky?.let(::percentFrom) ?: 100
    }

    /**
     * Process-lifetime monitor that auto-disables HD mode on low battery.
     * Uses [OmnidroidApplication.scope] so the receiver is unregistered when the job ends.
     */
    fun start(context: Context) {
        val app = context.applicationContext
        apply(app)
        if (monitorJob?.isActive == true) return
        monitorJob =
            OmnidroidApplication.scope(app).launch {
                batteryPercentFlow(app).collect { percent -> apply(app, percent) }
            }
    }

    fun allowedFlow(context: Context): Flow<Boolean> {
        return batteryPercentFlow(context.applicationContext)
            .distinctUntilChanged()
            .map(::isAllowed)
    }

    fun apply(context: Context) {
        apply(context, currentPercent(context))
    }

    private fun apply(
        context: Context,
        percent: Int,
    ) {
        if (isAllowed(percent)) return

        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        val key = context.getString(R.string.pref_key_hd_mode)
        if (prefs.getBoolean(key, false)) {
            Timber.i("Disabling HD mode because battery is %d%%", percent)
            prefs.edit().putBoolean(key, false).apply()
        }
    }

    private fun batteryPercentFlow(appContext: Context): Flow<Int> =
        callbackFlow {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        trySend(percentFrom(intent))
                    }
                }
            val sticky =
                ContextCompat.registerReceiver(
                    appContext,
                    receiver,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            if (sticky != null) {
                trySend(percentFrom(sticky))
            }
            awaitClose { appContext.unregisterReceiver(receiver) }
        }

    private fun percentFrom(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        return ((level.coerceAtLeast(0) * 100f) / scale).toInt().coerceIn(0, 100)
    }
}

@Composable
fun rememberHdModeAllowed(): Boolean {
    val context = LocalContext.current
    return HdModeBatteryMonitor.allowedFlow(context)
        .collectAsState(HdModeBatteryMonitor.isAllowed(context))
        .value
}
