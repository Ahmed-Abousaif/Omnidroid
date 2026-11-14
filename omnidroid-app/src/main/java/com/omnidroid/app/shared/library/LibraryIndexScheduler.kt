package com.omnidroid.app.shared.library

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object LibraryIndexScheduler {
    val CORE_UPDATE_WORK_ID: String = CoreUpdateWork::class.java.simpleName
    val LIBRARY_INDEX_WORK_ID: String = LibraryIndexWork::class.java.simpleName
    val SWITCH_ICON_WORK_ID: String = SwitchIconExtractionWork::class.java.simpleName

    const val KEY_FORCE_DEEP = "force_deep"

    fun scheduleLibrarySync(
        applicationContext: Context,
        forceDeep: Boolean = false,
    ) {
        val input = Data.Builder().putBoolean(KEY_FORCE_DEEP, forceDeep).build()
        WorkManager.getInstance(applicationContext)
            .beginUniqueWork(
                LIBRARY_INDEX_WORK_ID,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<LibraryIndexWork>()
                    .setInputData(input)
                    .build(),
            )
            .then(OneTimeWorkRequestBuilder<CoreUpdateWork>().build())
            .then(OneTimeWorkRequestBuilder<SwitchIconExtractionWork>().build())
            .enqueue()
    }

    fun scheduleCoreUpdate(applicationContext: Context) {
        WorkManager.getInstance(applicationContext)
            .beginUniqueWork(
                CORE_UPDATE_WORK_ID,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<CoreUpdateWork>().build(),
            )
            .then(OneTimeWorkRequestBuilder<SwitchIconExtractionWork>().build())
            .enqueue()
    }

    fun cancelLibrarySync(applicationContext: Context) {
        WorkManager.getInstance(applicationContext).cancelUniqueWork(LIBRARY_INDEX_WORK_ID)
        WorkManager.getInstance(applicationContext).cancelUniqueWork(SWITCH_ICON_WORK_ID)
        LibraryIndexWork.clearProgress()
    }

    fun cancelCoreUpdate(applicationContext: Context) {
        WorkManager.getInstance(applicationContext).cancelUniqueWork(CORE_UPDATE_WORK_ID)
        LibraryIndexWork.clearProgress()
    }
}
