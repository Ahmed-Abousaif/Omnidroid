package com.omnidroid.app.mobile.feature.shortcuts

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.app.shared.deeplink.DeepLink
import com.omnidroid.common.bitmap.cropToSquare
import com.omnidroid.common.bitmap.toBitmap
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

class ShortcutsGenerator(
    private val appContext: Context,
    retrofit: Retrofit,
) {
    private val thumbnailsApi = retrofit.create(ThumbnailsApi::class.java)

    suspend fun pinShortcutForGame(game: Game) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val shortcutManager = appContext.getSystemService(ShortcutManager::class.java)!!
        val bitmap = retrieveBitmap(game)

        val shortcutInfo =
            ShortcutInfo.Builder(appContext, "game_${game.id}")
                .setShortLabel(game.displayName)
                .setLongLabel(game.displayName)
                .setIntent(DeepLink.launchIntentForGame(appContext, game))
                .setIcon(Icon.createWithBitmap(bitmap))
                .build()

        shortcutManager.requestPinShortcut(shortcutInfo, null)
    }

    private suspend fun retrieveBitmap(game: Game): Bitmap =
        withContext(Dispatchers.IO) {
            CoverUtils.resolveCustomCoverFile(game)?.let { file ->
                BitmapFactory.decodeFile(file.absolutePath)?.cropToSquare()
            } ?: runCatching {
                val response = thumbnailsApi.downloadThumbnail(game.coverFrontUrl!!)
                response.body()?.use { body ->
                    BitmapFactory.decodeStream(body.byteStream()).cropToSquare()
                } ?: retrieveFallbackBitmap(game)
            }.getOrElse { retrieveFallbackBitmap(game) }
        }

    private fun retrieveFallbackBitmap(game: Game): Bitmap {
        val desiredIconSize = getDesiredIconSize()
        return CoverUtils.getFallbackDrawable(game).toBitmap(desiredIconSize, desiredIconSize)
    }

    private fun getDesiredIconSize(): Int {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        return am?.launcherLargeIconSize ?: 256
    }

    fun supportShortcuts(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }

        val shortcutManager = appContext.getSystemService(ShortcutManager::class.java)!!
        return shortcutManager.isRequestPinShortcutSupported
    }

    interface ThumbnailsApi {
        @GET
        @Streaming
        suspend fun downloadThumbnail(
            @Url url: String,
        ): Response<ResponseBody>
    }
}
