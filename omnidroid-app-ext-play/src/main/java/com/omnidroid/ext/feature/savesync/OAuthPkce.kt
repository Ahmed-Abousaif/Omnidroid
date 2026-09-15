package com.omnidroid.ext.feature.savesync

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object OAuthPkce {
    fun newVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    fun challengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }

    fun newState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
