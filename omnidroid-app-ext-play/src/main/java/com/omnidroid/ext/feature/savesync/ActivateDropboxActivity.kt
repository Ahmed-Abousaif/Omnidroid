package com.omnidroid.ext.feature.savesync

import android.net.Uri
import com.omnidroid.ext.R
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ActivateDropboxActivity : OAuthPkceActivity() {
    override val providerId: String = DropboxCloudSaveProvider.PROVIDER_ID
    override val missingConfigMessage: Int = R.string.dropbox_missing_app_key

    override fun clientId(): String = CloudSaveConfig.dropboxAppKey

    override fun redirectUri(): String = REDIRECT_URI

    override fun authorizationUrl(
        challenge: String,
        state: String,
    ): String {
        return Uri.parse("https://www.dropbox.com/oauth2/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId())
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("token_access_type", "offline")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("redirect_uri", redirectUri())
            .appendQueryParameter("state", state)
            .build()
            .toString()
    }

    override fun exchangeCode(
        code: String,
        verifier: String,
    ): OAuthTokens {
        val body =
            FormBody.Builder()
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("client_id", clientId())
                .add("code_verifier", verifier)
                .add("redirect_uri", redirectUri())
                .build()
        val request =
            Request.Builder()
                .url("https://api.dropboxapi.com/oauth2/token")
                .post(body)
                .build()
        OkHttpClient().newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(payload.ifBlank { response.message })
            }
            val json = JSONObject(payload)
            return OAuthTokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token").ifBlank { null },
                accountLabel = json.optString("account_id").ifBlank { "Dropbox" },
            )
        }
    }

    companion object {
        const val REDIRECT_URI = "com.omnidroid://oauth-dropbox"
    }
}
