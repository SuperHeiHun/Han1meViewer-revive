package com.yenaly.han1meviewer.logic.network.service

import androidx.annotation.IntRange
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/08 008 22:10
 */
interface HanimeBaseService {

    @GET("/")
    suspend fun getHomePage(): Response<ResponseBody>

    @GET("search")
    suspend fun getHanimeSearchResult(
        @Query("page") @IntRange(from = 1) page: Int = 1,
        @Query("query") query: String? = null,
        @Query("genre") genre: String? = null,
        @Query("sort") sort: String? = null,
        @Query("broad") broad: String? = null,
        // 新版網站把年份和月份合併成一個 date 參數，例如 "2026 年 3 月"
        @Query("date") date: String? = null,
        @Query("duration") duration: String? = null,
        @Query("tags[]") tags: Set<String> = emptySet(),
    ): Response<ResponseBody>

    @GET("watch")
    suspend fun getHanimeVideo(
        @Query("v") videoCode: String,
    ): Response<ResponseBody>

    @GET("previews/{date}")
    suspend fun getHanimePreview(
        @Path("date") date: String, // 类似 202206. 202012
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("_token") csrfToken: String?,
        @Field("email") email: String,
        @Field("password") password: String,
        @Header("X-CSRF-TOKEN") csrfToken_1: String? = csrfToken,
    ): Response<ResponseBody>

    @GET("login")
    suspend fun getLoginPage(): Response<ResponseBody>
}