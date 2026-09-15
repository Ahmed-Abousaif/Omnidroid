package com.omnidroid.ext.feature.savesync

import android.content.Context
import com.omnidroid.lib.preferences.SharedPreferencesHelper

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val accountLabel: String?,
)

class OAuthTokenStore(
    context: Context,
    private val providerId: String,
) {
    private val prefs = SharedPreferencesHelper.getSharedPreferences(context)

    fun read(): OAuthTokens? {
        val access = prefs.getString(key("access"), null) ?: return null
        return OAuthTokens(
            accessToken = access,
            refreshToken = prefs.getString(key("refresh"), null),
            accountLabel = prefs.getString(key("account"), null),
        )
    }

    fun write(tokens: OAuthTokens) {
        prefs.edit()
            .putString(key("access"), tokens.accessToken)
            .putString(key("refresh"), tokens.refreshToken)
            .putString(key("account"), tokens.accountLabel)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(key("access"))
            .remove(key("refresh"))
            .remove(key("account"))
            .apply()
    }

    private fun key(suffix: String) = "oauth_${providerId}_$suffix"
}
