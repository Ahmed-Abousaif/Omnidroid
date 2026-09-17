package com.omnidroid.metadata.rawg

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RawgApi {
    @GET("api/games")
    suspend fun searchGames(
        @Query("key") key: String,
        @Query("search") search: String,
        @Query("page_size") pageSize: Int = 5,
        @Query("platforms") platforms: String? = null,
        @Query("search_precise") searchPrecise: Boolean = false,
    ): ResponseBody

    @GET("api/games/{id}")
    suspend fun getGameDetails(
        @Path("id") id: Int,
        @Query("key") key: String,
    ): ResponseBody

    @GET("api/games/{id}/screenshots")
    suspend fun getGameScreenshots(
        @Path("id") id: Int,
        @Query("key") key: String,
        @Query("page_size") pageSize: Int = 8,
    ): ResponseBody
}
