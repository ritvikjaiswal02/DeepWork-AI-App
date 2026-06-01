package com.example.deepworkai.network

import android.content.Context
import android.content.SharedPreferences
import com.example.deepworkai.BuildConfig

object NetworkPreferences {
    private const val PREFS_NAME = "deepwork_network_prefs"
    private const val KEY_BACKEND_URL = "backend_url"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Backend URL with runtime override:
     *   - If the user has typed a custom URL in Settings, use that (survives reboots via prefs).
     *   - Otherwise fall back to the value baked into BuildConfig at compile time.
     * This lets you switch networks (e.g. new Wi-Fi at the exam hall) without rebuilding.
     */
    var backendUrl: String
        get() {
            val override = if (::prefs.isInitialized) prefs.getString(KEY_BACKEND_URL, null) else null
            return if (!override.isNullOrBlank()) override else BuildConfig.BACKEND_URL
        }
        set(value) {
            if (::prefs.isInitialized) {
                val cleaned = value.trim().trimEnd('/')
                if (cleaned.isBlank()) {
                    prefs.edit().remove(KEY_BACKEND_URL).apply()
                } else {
                    prefs.edit().putString(KEY_BACKEND_URL, cleaned).apply()
                }
            }
        }

    var userId: String?
        get() = prefs.getString("user_id", null)
        set(value) = prefs.edit().putString("user_id", value).apply()

    var userName: String?
        get() = prefs.getString("user_name", null)
        set(value) = prefs.edit().putString("user_name", value).apply()

    var authToken: String?
        get() = prefs.getString("auth_token", null)
        set(value) = prefs.edit().putString("auth_token", value).apply()
}
