package com.omnidroid.app.shared.covers

import android.app.Application
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class SecureCoverInterceptorTest {
    private fun clientWithFakeNetwork(): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(SecureCoverInterceptor)
            .addInterceptor { chain ->
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "image/png")
                    .body(byteArrayOf(1, 2, 3, 4).toResponseBody("image/png".toMediaType()))
                    .build()
            }.build()

    @Test
    fun coverWhitelistExcludesRetroAchievementsHost() {
        val raBadgeUrl = "https://media.retroachievements.org/Badge/609830.png"
        assertFalse(CoverUtils.isAllowedRemoteCoverUrl(raBadgeUrl))
        assertTrue(CoverUtils.isAllowedRetroAchievementsImageUrl(raBadgeUrl))
        assertTrue(CoverUtils.isAllowedRemoteImageUrl(raBadgeUrl))
    }

    @Test
    fun retroAchievementsImagePolicyRequiresHttpsAndExactHost() {
        val base = "https://media.retroachievements.org"
        assertTrue(CoverUtils.isAllowedRetroAchievementsImageUrl("$base/Images/126553.png"))
        assertTrue(CoverUtils.isAllowedRetroAchievementsImageUrl("$base/Badge/609830_lock.png"))
        assertTrue(CoverUtils.isAllowedRetroAchievementsImageUrl("$base/UserPic/hobsterizer.png"))

        assertFalse(
            CoverUtils.isAllowedRetroAchievementsImageUrl(
                "http://media.retroachievements.org/Badge/609830.png",
            ),
        )
        assertFalse(
            CoverUtils.isAllowedRetroAchievementsImageUrl(
                "https://evil.example/Badge/609830.png",
            ),
        )
        assertFalse(
            CoverUtils.isAllowedRetroAchievementsImageUrl(
                "https://media.retroachievements.org.evil.com/Badge/609830.png",
            ),
        )
        assertFalse(
            CoverUtils.isAllowedRetroAchievementsImageUrl(
                "$base/../secret.png",
            ),
        )
        assertFalse(CoverUtils.isAllowedRetroAchievementsImageUrl(""))
        assertFalse(CoverUtils.isAllowedRetroAchievementsImageUrl(null))
    }

    @Test
    fun interceptorAllowsRetroAchievementsBadgesAndBlocksUntrustedHosts() {
        val client = clientWithFakeNetwork()

        val badgeResponse =
            client
                .newCall(
                    Request
                        .Builder()
                        .url("https://media.retroachievements.org/Badge/609830.png")
                        .build(),
                ).execute()
        assertEquals(200, badgeResponse.code)
        badgeResponse.close()

        val gameImageResponse =
            client
                .newCall(
                    Request
                        .Builder()
                        .url("https://media.retroachievements.org/Images/126553.png")
                        .build(),
                ).execute()
        assertEquals(200, gameImageResponse.code)
        gameImageResponse.close()

        val failedCleartext =
            runCatching {
                client
                    .newCall(
                        Request
                            .Builder()
                            .url("http://media.retroachievements.org/Badge/609830.png")
                            .build(),
                    ).execute()
            }.exceptionOrNull()
        assertTrue(failedCleartext is IOException)

        val failedEvilHost =
            runCatching {
                client
                    .newCall(
                        Request
                            .Builder()
                            .url("https://evil.example/Badge/609830.png")
                            .build(),
                    ).execute()
            }.exceptionOrNull()
        assertTrue(failedEvilHost is IOException)
    }
}
