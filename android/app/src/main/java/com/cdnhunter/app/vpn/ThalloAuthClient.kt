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
 * Client for the self-hosted auth backend (Cloudflare Worker + D1 + KV) that replaces
 * Firebase Auth for email/password sign-in -- see AuthDomainResolver for why: Firebase's
 * Auth API endpoints return a flat 403 for Iranian IPs regardless of custom domain
 * fronting, since the block is applied by Google to the underlying API, not the DNS path.
 *
 * Session model: a short-lived (15 min) access token plus a long-lived (30 day) refresh
 * token, with rotation-on-use and reuse detection on the backend -- if a stale refresh
 * token is ever presented (meaning it was copied while the legitimate device already
 * rotated past it), the backend tears down every session for that user.
 *
 * The Worker's base URL is obfuscated in the compiled class the same way
 * [AuthDomainResolver]'s fallback domain is, rather than appearing as a plaintext string.
 */
object ThalloAuthClient {

    private val WORKER_HOST_CHARS = intArrayOf(
        116, 104, 97, 108, 108, 111, 45, 97, 117, 116, 104, 46, 109, 111, 119, 122,
        121, 122, 46, 119, 111, 114, 107, 101, 114, 115, 46, 100, 101, 118,
    )
    private val workerBaseUrl: String
        get() = "https://" + String(WORKER_HOST_CHARS.map { it.toChar() }.toCharArray())

    private const val PREFS_NAME = "cdnhunter_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
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
        val accessToken: String,
        val refreshToken: String,
    )

    sealed class AuthOutcome {
        data class Success(val result: AuthResult) : AuthOutcome()
        data class Failure(val message: String) : AuthOutcome()
    }

    suspend fun signUp(email: String, password: String, displayName: String): AuthOutcome =
        request("/signup", email, password, displayName)

    suspend fun logIn(email: String, password: String): AuthOutcome =
        request("/login", email, password, null)

    /** Exchanges a Google ID token (from Google Play Services' own Sign-In flow -- never
     *  touches Firebase) for a Thallo session. The backend verifies the ID token directly
     *  against Google's tokeninfo endpoint from Cloudflare's network, sidestepping the
     *  API-region block that hits Firebase Auth for Iranian IPs. */
    suspend fun signInWithGoogle(idToken: String): AuthOutcome = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("idToken", idToken)
            val httpRequest = Request.Builder()
                .url(workerBaseUrl + "/google-signin")
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
                        accessToken = json.getString("accessToken"),
                        refreshToken = json.getString("refreshToken"),
                    )
                )
            }
        } catch (e: Exception) {
            AuthOutcome.Failure("Network error. Check your connection and try again.")
        }
    }

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
                        accessToken = json.getString("accessToken"),
                        refreshToken = json.getString("refreshToken"),
                    )
                )
            }
        } catch (e: Exception) {
            AuthOutcome.Failure("Network error. Check your connection and try again.")
        }
    }

    /** Exchanges the stored refresh token for a new access+refresh pair, persisting the
     *  result. Returns false (and clears the session) if the refresh token is no longer
     *  valid -- either expired or revoked by reuse detection -- meaning the caller should
     *  fall back to the sign-in screen. */
    suspend fun refreshSession(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = SecurePrefs.get(context, PREFS_NAME)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return@withContext false

        try {
            val body = JSONObject().put("refreshToken", refreshToken)
            val httpRequest = Request.Builder()
                .url(workerBaseUrl + "/refresh")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    signOut(context)
                    return@withContext false
                }
                val json = JSONObject(response.body?.string() ?: "")
                prefs.edit()
                    .putString(KEY_ACCESS_TOKEN, json.getString("accessToken"))
                    .putString(KEY_REFRESH_TOKEN, json.getString("refreshToken"))
                    .apply()
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Persists a fresh session, encrypted at rest via [SecurePrefs]. */
    fun saveSession(context: Context, result: AuthResult) {
        SecurePrefs.get(context, PREFS_NAME).edit()
            .putString(KEY_ACCESS_TOKEN, result.accessToken)
            .putString(KEY_REFRESH_TOKEN, result.refreshToken)
            .putString(KEY_USER_ID, result.userId)
            .putString(KEY_EMAIL, result.email)
            .putString(KEY_DISPLAY_NAME, result.displayName)
            .apply()
    }

    /** The stored session, if any -- present regardless of whether the access token
     *  inside it has since expired (callers needing a guaranteed-fresh token should
     *  call [refreshSession] first). */
    fun currentSession(context: Context): AuthResult? {
        val prefs = SecurePrefs.get(context, PREFS_NAME)
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val displayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
        return AuthResult(userId, email, displayName, accessToken, refreshToken)
    }

    /** Signs out locally and tells the backend to revoke the refresh token, so a stolen
     *  copy of it (if one existed) can't be used to mint new sessions after this. */
    fun signOut(context: Context) {
        val prefs = SecurePrefs.get(context, PREFS_NAME)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        prefs.edit().clear().apply()

        if (refreshToken != null) {
            Thread {
                try {
                    val body = JSONObject().put("refreshToken", refreshToken)
                    val httpRequest = Request.Builder()
                        .url(workerBaseUrl + "/logout")
                        .post(body.toString().toRequestBody(jsonMedia))
                        .build()
                    client.newCall(httpRequest).execute().close()
                } catch (e: Exception) {
                    // Best-effort: local session is already cleared either way.
                }
            }.start()
        }
    }
}
