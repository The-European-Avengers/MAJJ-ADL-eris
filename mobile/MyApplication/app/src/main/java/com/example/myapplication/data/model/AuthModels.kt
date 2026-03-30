package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

// Login
data class LoginRequest(
    val username: String, // Backend might support either
    val password: String
)

data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String
)

// Register
data class RegisterRequest(
    val name: String,
    val username: String,
    val email: String,
    val password: String,
    val is_active: Boolean
)

data class RegisterResponse(
    val id: String?,
    val message: String?,
    val email: String?
)


// Respuesta cuando el usuario gana puntos
data class PredictionCompleteResponse(
    val name: String,
    val username: String,
    val current_streak: Int,
    val longest_streak: Int,
    val total_score: Float
)

// Datos para la tabla de clasificación
data class LeaderboardEntry(
    val username: String,
    val current_streak: Int,
    val total_score: Float
)