package com.omnidroid.app.shared.library

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.omnidroid.R
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import java.util.concurrent.TimeUnit

object LibraryPeriodicScanScheduler {
    private const val PERIODIC_WORK_NAME = "library_periodic_scan"

    fun syncWithPreference(context: Context) {
        val enabled =
            SharedPreferencesHelper.getSharedPreferences(context)
                .getBoolean(context.getString(R.string.pref_key_periodic_library_scan), false)
        if (enabled) {
            enable(context)
        } else {
            disable(context)
        }
    }

    fun enable(context: Context) {
        val constraints =
            Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
        val request =
            PeriodicWorkRequestBuilder<LibraryIndexWork>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME)
    }
}
