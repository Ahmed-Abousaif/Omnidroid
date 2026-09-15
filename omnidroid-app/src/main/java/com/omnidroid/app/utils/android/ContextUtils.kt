package com.omnidroid.app.utils.android

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process

fun Context.isMainProcess(): Boolean {
    return retrieveProcessName(this) == this.packageName
}

private fun retrieveProcessName(context: Context): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return Application.getProcessName()
    }

    val currentPID = Process.myPid()
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.runningAppProcesses
        .firstOrNull { it.pid == currentPID }
        ?.processName
}

fun Context.getGLSLVersion(): Int {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return if (activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000) {
        3
    } else {
        2
    }
}
