package com.example.myapplication.data.api

import com.example.myapplication.data.model.LoginRequest
import com.example.myapplication.data.model.LoginResponse
import com.example.myapplication.data.model.LeaderboardEntry
import com.example.myapplication.data.model.PredictionCompleteResponse
import com.example.myapplication.data.model.RegisterRequest
import com.example.myapplication.data.model.RegisterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @FormUrlEncoded
    @POST("api/v1/auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    @POST("api/v1/users/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    // Add more endpoints here as needed

    @POST("api/v1/users/prediction-complete")
    suspend fun registerPredictionActivity(
        @Header("Authorization") token: String,
        @Query("points") points: Float
    ): Response<PredictionCompleteResponse>

    @GET("api/v1/users/leaderboard")
    suspend fun getLeaderboard(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 5 // Traemos el Top 5 para que quepa bien en pantalla
    ): Response<List<LeaderboardEntry>>
}
