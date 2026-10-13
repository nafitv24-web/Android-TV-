package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class FirebaseAuthManager(
    private val context: Context,
    private val client: OkHttpClient
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nafitv24_firebase_auth", Context.MODE_PRIVATE)

    companion object {
        const val PRIMARY_ADMIN_EMAIL = "nafitv24@gmail.com"
        private const val API_KEY = MediaRepository.FIREBASE_API_KEY

        private const val KEY_ID_TOKEN = "auth_id_token"
        private const val KEY_REFRESH_TOKEN = "auth_refresh_token"
        private const val KEY_EMAIL = "auth_email"
        private const val KEY_UID = "auth_uid"
        private const val KEY_EXPIRY_TIMESTAMP = "auth_expiry_timestamp"
    }

    fun isUserLoggedIn(): Boolean {
        val token = prefs.getString(KEY_ID_TOKEN, null)
        val email = prefs.getString(KEY_EMAIL, null)
        return !token.isNullOrBlank() && !email.isNullOrBlank()
    }

    fun isAuthorizedAdmin(): Boolean {
        if (!isUserLoggedIn()) return false
        val email = getLoggedInEmail() ?: return false
        return email.trim().equals(PRIMARY_ADMIN_EMAIL, ignoreCase = true) ||
                email.trim().endsWith("@gmail.com", ignoreCase = true)
    }

    fun getLoggedInEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }

    fun getLoggedInUid(): String? {
        return prefs.getString(KEY_UID, null)
    }

    fun getStoredIdToken(): String? {
        return prefs.getString(KEY_ID_TOKEN, null)
    }

    fun getStoredRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    suspend fun getValidIdToken(): String? = withContext(Dispatchers.IO) {
        val currentToken = prefs.getString(KEY_ID_TOKEN, null) ?: return@withContext null
        val expiry = prefs.getLong(KEY_EXPIRY_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()

        // If token expires in less than 5 minutes, refresh it
        if (now >= expiry - 300_000L) {
            val refreshed = refreshAuthToken()
            if (!refreshed.isNullOrBlank()) {
                return@withContext refreshed
            }
        }
        currentToken
    }

    suspend fun signInWithEmail(email: String, pass: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanPass = pass.trim()

        if (cleanEmail.isBlank() || cleanPass.isBlank()) {
            return@withContext Pair(false, "ইমেইল এবং পাসওয়ার্ড উভয়ই প্রদান করুন")
        }

        try {
            val url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$API_KEY"
            val payload = JSONObject().apply {
                put("email", cleanEmail)
                put("password", cleanPass)
                put("returnSecureToken", true)
            }
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val resBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(resBody)
                val idToken = json.optString("idToken")
                val refreshToken = json.optString("refreshToken")
                val userEmail = json.optString("email", cleanEmail)
                val uid = json.optString("localId")
                val expiresInSec = json.optString("expiresIn", "3600").toLongOrNull() ?: 3600L
                val expiryTime = System.currentTimeMillis() + (expiresInSec * 1000L)

                saveSession(idToken, refreshToken, userEmail, uid, expiryTime)
                Pair(true, "ফায়ারবেস অথেন্টিকেশন সফল হয়েছে! স্বাগতম $userEmail")
            } else {
                val errorMsg = parseAuthError(resBody)
                Pair(false, errorMsg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "নেটওয়ার্ক ত্রুটি: ${e.localizedMessage ?: "সংযোগ করা যাচ্ছে না"}")
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanPass = pass.trim()

        if (cleanEmail.isBlank() || cleanPass.length < 6) {
            return@withContext Pair(false, "পাসওয়ার্ড অন্তত ৬ অক্ষরের হতে হবে")
        }

        try {
            val url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$API_KEY"
            val payload = JSONObject().apply {
                put("email", cleanEmail)
                put("password", cleanPass)
                put("returnSecureToken", true)
            }
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val resBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(resBody)
                val idToken = json.optString("idToken")
                val refreshToken = json.optString("refreshToken")
                val userEmail = json.optString("email", cleanEmail)
                val uid = json.optString("localId")
                val expiresInSec = json.optString("expiresIn", "3600").toLongOrNull() ?: 3600L
                val expiryTime = System.currentTimeMillis() + (expiresInSec * 1000L)

                saveSession(idToken, refreshToken, userEmail, uid, expiryTime)
                Pair(true, "অ্যাকাউন্ট সফলভাবে তৈরি হয়েছে! স্বাগতম $userEmail")
            } else {
                val errorMsg = parseAuthError(resBody)
                Pair(false, errorMsg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "রেজিস্ট্রেশন ত্রুটি: ${e.localizedMessage ?: "ব্যর্থ হয়েছে"}")
        }
    }

    suspend fun sendPasswordReset(email: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            return@withContext Pair(false, "ইমেইল প্রদান করুন")
        }

        try {
            val url = "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$API_KEY"
            val payload = JSONObject().apply {
                put("requestType", "PASSWORD_RESET")
                put("email", cleanEmail)
            }
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val resBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Pair(true, "$cleanEmail ঠিকানায় পাসওয়ার্ড রিসেট লিংক পাঠানো হয়েছে। ইমেইল চেক করুন।")
            } else {
                val errorMsg = parseAuthError(resBody)
                Pair(false, errorMsg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "পাসওয়ার্ড রিসেট ত্রুটি: ${e.localizedMessage ?: "ব্যর্থ হয়েছে"}")
        }
    }

    suspend fun refreshAuthToken(): String? = withContext(Dispatchers.IO) {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return@withContext null
        try {
            val url = "https://securetoken.googleapis.com/v1/token?key=$API_KEY"
            val formBody = "grant_type=refresh_token&refresh_token=$refreshToken"
            val body = formBody.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val resBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(resBody)
                val newIdToken = json.optString("id_token")
                val newRefreshToken = json.optString("refresh_token", refreshToken)
                val userUid = json.optString("user_id", prefs.getString(KEY_UID, "") ?: "")
                val expiresInSec = json.optString("expires_in", "3600").toLongOrNull() ?: 3600L
                val expiryTime = System.currentTimeMillis() + (expiresInSec * 1000L)

                saveSession(newIdToken, newRefreshToken, prefs.getString(KEY_EMAIL, "") ?: "", userUid, expiryTime)
                return@withContext newIdToken
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    fun signOut() {
        prefs.edit().clear().apply()
    }

    private fun saveSession(
        idToken: String,
        refreshToken: String,
        email: String,
        uid: String,
        expiryTime: Long
    ) {
        prefs.edit()
            .putString(KEY_ID_TOKEN, idToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_EMAIL, email)
            .putString(KEY_UID, uid)
            .putLong(KEY_EXPIRY_TIMESTAMP, expiryTime)
            .apply()
    }

    private fun parseAuthError(rawResponse: String): String {
        return try {
            val json = JSONObject(rawResponse)
            val errorObj = json.optJSONObject("error")
            val code = errorObj?.optString("message") ?: "UNKNOWN_ERROR"

            when {
                code.contains("EMAIL_NOT_FOUND") -> "এই ইমেইল দিয়ে কোনো অ্যাকাউন্ট পাওয়া যায়নি। অনুগ্রহ করে সাইন আপ করুন।"
                code.contains("INVALID_PASSWORD") || code.contains("INVALID_LOGIN_CREDENTIALS") -> "ভুল পাসওয়ার্ড অথবা অবৈধ তথ্য।"
                code.contains("USER_DISABLED") -> "এই অ্যাকাউন্টটি ফায়ারবেস দ্বারা নিষ্ক্রিয় করা হয়েছে।"
                code.contains("EMAIL_EXISTS") -> "এই ইমেইল দিয়ে ইতোমধ্যে একটি অ্যাকাউন্ট রয়েছে। লগইন করুন।"
                code.contains("TOO_MANY_ATTEMPTS_TRY_LATER") -> "অনেকবার চেষ্টা করা হয়েছে। কিছুক্ষণ পর আবার চেষ্টা করুন।"
                code.contains("WEAK_PASSWORD") -> "পাসওয়ার্ডটি দুর্বল। অন্তত ৬ অক্ষরের শক্তিশালী পাসওয়ার্ড দিন।"
                code.contains("OPERATION_NOT_ALLOWED") -> "ফায়ারবেস কনসোলে Email/Password Auth সাইন-ইন মেথড চালু করা নেই।"
                else -> "অথেন্টিকেশন ব্যর্থ: $code"
            }
        } catch (e: Exception) {
            "ত্রুটি: $rawResponse"
        }
    }
}
