package com.omnidroid.ext.feature.savesync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.omnidroid.ext.R
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import timber.log.Timber

abstract class OAuthPkceActivity : Activity() {
    protected abstract val providerId: String
    protected abstract val missingConfigMessage: Int

    protected abstract fun clientId(): String

    protected abstract fun redirectUri(): String

    protected abstract fun authorizationUrl(
        challenge: String,
        state: String,
    ): String

    protected abstract fun exchangeCode(
        code: String,
        verifier: String,
    ): OAuthTokens

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri != null && uri.toString().startsWith(redirectUri())) {
            handleRedirect(uri)
            return
        }
        startAuthorization()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val uri = intent?.data ?: return
        handleRedirect(uri)
    }

    private fun startAuthorization() {
        val clientId = clientId()
        if (clientId.isBlank()) {
            Toast.makeText(this, missingConfigMessage, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val verifier = OAuthPkce.newVerifier()
        val state = OAuthPkce.newState()
        prefs().edit()
            .putString(pendingVerifierKey(), verifier)
            .putString(pendingStateKey(), state)
            .apply()
        val challenge = OAuthPkce.challengeS256(verifier)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl(challenge, state))))
        finish()
    }

    private fun handleRedirect(uri: Uri) {
        val expectedState = prefs().getString(pendingStateKey(), null)
        val verifier = prefs().getString(pendingVerifierKey(), null)
        prefs().edit().remove(pendingStateKey()).remove(pendingVerifierKey()).apply()

        val error = uri.getQueryParameter("error")
        if (error != null) {
            Toast.makeText(this, getString(R.string.oauth_sign_in_failed, error), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val state = uri.getQueryParameter("state")
        val code = uri.getQueryParameter("code")
        if (verifier == null || code == null || (expectedState != null && expectedState != state)) {
            Toast.makeText(this, getString(R.string.oauth_sign_in_failed, "invalid_state"), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        try {
            val tokens = exchangeCode(code, verifier)
            OAuthTokenStore(this, providerId).write(tokens)
            val label = tokens.accountLabel ?: providerId
            Toast.makeText(this, getString(R.string.oauth_sign_in_success, label), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.e(e, "OAuth token exchange failed")
            Toast.makeText(this, getString(R.string.oauth_sign_in_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun prefs() = SharedPreferencesHelper.getSharedPreferences(this)

    private fun pendingVerifierKey() = "oauth_pending_verifier_$providerId"

    private fun pendingStateKey() = "oauth_pending_state_$providerId"
}
