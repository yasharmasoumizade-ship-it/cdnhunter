package com.cdnhunter.app.vpn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for the self-hosted auth backend (Cloudflare Worker + D1) that replaces
 * Firebase Auth for email/password sign-in -- see AuthDomainResolver for why: Firebase's
 * own Auth API endpoints return a flat 403 for Iranian IPs regardless of custom domain
 * fronting, since the block is applied by Google to the underlying API, not the DNS path.
 *
 * The Worker's base URL is resolved once via the same obfuscated-gateway mechanism as
 * [AuthDomainResolver] uses for the (now-unused for this path) Firebase domain, so this
 * endpoint is not a plaintext grep-able string either.
 *
 * The session token this returns is stored, at rest, in the same encrypted prefs file
 * ([SecurePrefs]) the app already uses for VPN server credentials -- never plain
 * SharedPreferences.
 */
object ThalloAuthClient {

    // Obfuscated the same way AuthDomainResolver's fallback domain is -- not a literal
    // grep-able string in the compiled class.
    private val WORKER_HOST_CHARS = intArrayOf(
        116, 104, 97, 108, 108, 111, 45, 97, 117, 116, 104, 46, 109, 111, 119, 122,
        121, 122, 46, 119, 111, 114, 107, 101, 114, 115, 46, 100, 101, 118,
    )
    private val workerBaseUrl: String
        get() = "https://" + String(WORKER_HOST_CHARS.map { it.toChar() }.toCharArray())

    private const val PREFS_NAME = "cdnhunter_auth"
    private const val KEY_TOKEN = "session_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_DISPLAY_NAME = "display_name"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    data class AuthResult(
        val userId: String,
        val email: String,
        val displayName: String,
        val token: String,
    )

    /** Either an [AuthResult] on success, or a human-readable error message on failure --
     *  never a raw exception message, which could contain internal detail. */
    sealed class AuthOutcome {
        data class Success(val result: AuthResult) : AuthOutcome()
        data class Failure(val message: String) : AuthOutcome()
    }

    suspend fun signUp(email: String, password: String, displayName: String): AuthOutcome =
        request("/signup", email, password, displayName)

    suspend fun logIn(email: String, password: String): AuthOutcome =
        request("/login", email, password, null)

    private suspend fun request(
        path: String,
        email: String,
        password: String,
        displayName: String?,
    ): AuthOutcome = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
                if (displayName != null) put("displayName", displayName)
            }
            val httpRequest = Request.Builder()
                .url(workerBaseUrl + path)
                .post(body.toString().toRequestBody(jsonMedia))
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val json = try {
                    JSONObject(responseBody)
                } catch (e: Exception) {
                    return@withContext AuthOutcome.Failure("Something went wrong. Please try again.")
                }

                if (!response.isSuccessful) {
                    val errorMsg = json.optString("error", "Something went wrong. Please try again.")
                    return@withContext AuthOutcome.Failure(errorMsg)
                }

                AuthOutcome.Success(
                    AuthResult(
                        userId = json.getString("userId"),
                        email = json.getString("email"),
                        displayName = json.optString("displayName", ""),
                        token = json.getString("token"),
                    )
                )
            }
        } catch (e: Exception) {
            AuthOutcome.Failure("Network error. Check your connection and try again.")
        }
    }

    /** Persists the session, encrypted at rest via [SecurePrefs]. */
    fun saveSession(context: Context, result: AuthResult) {
        SecurePrefs.get(context, PREFS_NAME).edit()
            .putString(KEY_TOKEN, result.token)
            .putString(KEY_USER_ID, result.userId)
            .putString(KEY_EMAIL, result.email)
            .putString(KEY_DISPLAY_NAME, result.displayName)
            .apply()
    }

    fun currentSession(context: Context): AuthResult? {
        val prefs = SecurePrefs.get(context, PREFS_NAME)
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val displayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
        return AuthResult(userId, email, displayName, token)
    }

    fun signOut(context: Context) {
        SecurePrefs.get(context, PREFS_NAME).edit().clear().apply()
    }
}
