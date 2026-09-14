package com.swordfish.lemuroid.ext.feature.savesync

import android.net.Uri
import com.swordfish.lemuroid.ext.R
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ActivateOneDriveActivity : OAuthPkceActivity() {
    override val providerId: String = OneDriveCloudSaveProvider.PROVIDER_ID
    override val missingConfigMessage: Int = R.string.onedrive_missing_client_id

    override fun clientId(): String = CloudSaveConfig.oneDriveClientId

    override fun redirectUri(): String = REDIRECT_URI

    override fun authorizationUrl(
        challenge: String,
        state: String,
    ): String {
        return Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", clientId())
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri())
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
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
                .add("client_id", clientId())
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri())
                .add("code_verifier", verifier)
                .build()
        val request =
            Request.Builder()
                .url("https://login.microsoftonline.com/common/oauth2/v2.0/token")
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
                accountLabel = "OneDrive",
            )
        }
    }

    companion object {
        const val REDIRECT_URI = "com.swordfish.lemuroid://oauth-onedrive"
        const val SCOPE = "offline_access Files.ReadWrite.AppFolder User.Read"
    }
}
