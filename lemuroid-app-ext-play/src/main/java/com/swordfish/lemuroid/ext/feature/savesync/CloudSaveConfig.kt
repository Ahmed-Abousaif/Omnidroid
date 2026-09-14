package com.swordfish.lemuroid.ext.feature.savesync

object CloudSaveConfig {
    val dropboxAppKey: String
        get() = stringField("DROPBOX_APP_KEY")

    val oneDriveClientId: String
        get() = stringField("ONEDRIVE_CLIENT_ID")

    private fun stringField(name: String): String {
        return try {
            val clazz = Class.forName("com.swordfish.lemuroid.ext.BuildConfig")
            clazz.getField(name).get(null) as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
