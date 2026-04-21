package com.example.myapplication.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

class TokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString("jwt_token", token).apply()
        // Extract and cache username from JWT payload automatically
        decodeUsernameFromJwt(token)?.let { saveUsername(it) }
    }

    fun getToken(): String? = prefs.getString("jwt_token", null)

    fun clearToken() {
        prefs.edit()
            .remove("jwt_token")
            .remove("username")
            .apply()
    }

    fun saveUsername(username: String) {
        prefs.edit().putString("username", username).apply()
    }

    fun getUsername(): String? = prefs.getString("username", null)

    /**
     * Decodes the `username` claim from a JWT token without any external library.
     * Falls back to `sub` if `username` is not present.
     */
    private fun decodeUsernameFromJwt(token: String): String? {
        return try {
            val payload = token.split(".").getOrNull(1) ?: return null
            val padded = payload
                .replace('-', '+')
                .replace('_', '/')
                .let { it + "=".repeat((4 - it.length % 4) % 4) }
            val json = String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
            // Try "username" claim first (new backend), fall back to "sub"
            Regex(""""username"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.getOrNull(1)
                ?: Regex(""""sub"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.getOrNull(1)
        } catch (e: Exception) {
            null
        }
    }
}