package com.omnidroid.lib.core

import android.content.Context
import com.omnidroid.lib.library.CoreID
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface CoreUpdater {
    suspend fun downloadCores(
        context: Context,
        coreIDs: List<CoreID>,
    )

    interface CoreManagerApi {
        @GET
        @Streaming
        suspend fun downloadFile(
            @Url url: String,
        ): Response<ResponseBody>

        @GET
        @Streaming
        suspend fun downloadZip(
            @Url url: String,
        ): Response<ResponseBody>
    }
}
