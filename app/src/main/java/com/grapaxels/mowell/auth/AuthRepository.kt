package com.grapaxels.mowell.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class UserProfile(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null
)

data class AuthSession(val token: String, val user: UserProfile)
data class AuthResult(val session: AuthSession? = null, val error: String? = null)
data class RemoteMessage(val id: String, val conversationId: String, val sender: String, val body: String, val sentAt: Long, val outgoing: Boolean)

class AuthRepository(context: Context) {
    private val prefs = context.getSharedPreferences("mowell_session", Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    var serverUrl: String
        get() = prefs.getString("server_url", "https://api.example.invalid")!!.trimEnd('/')
        set(value) { prefs.edit().putString("server_url", value.trim().trimEnd('/')).apply() }

    var googleClientId: String
        get() = prefs.getString("google_client_id", "")!!
        set(value) { prefs.edit().putString("google_client_id", value.trim()).apply() }

    val savedSession: AuthSession?
        get() {
            val token = prefs.getString("token", null) ?: return null
            val id = prefs.getString("user_id", null) ?: return null
            return AuthSession(token, UserProfile(
                id, prefs.getString("username", "user")!!,
                prefs.getString("email", "")!!, prefs.getString("display_name", "Mowell user")!!,
                prefs.getString("avatar_url", null)
            ))
        }

    suspend fun login(identity: String, password: String): AuthResult = postAuth(
        "/v1/auth/login", JSONObject().put("identity", identity).put("password", password)
    )

    suspend fun register(email: String, username: String, displayName: String, password: String): AuthResult = postAuth(
        "/v1/auth/register",
        JSONObject().put("email", email).put("username", username).put("displayName", displayName).put("password", password)
    )

    suspend fun google(idToken: String): AuthResult = postAuth("/v1/auth/google", JSONObject().put("idToken", idToken))

    suspend fun searchUsers(query: String): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/users/search?q=$encoded").header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Search failed"))
            val array = json.optJSONArray("users") ?: return@runCatching emptyList()
            buildList { for (index in 0 until array.length()) add(parseUser(array.getJSONObject(index))) }
        }
    }

    suspend fun createConversation(userId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("memberIds", org.json.JSONArray().put(userId))
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/conversations")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").post(body.toString().toRequestBody(jsonType)).build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not start conversation"))
            json.getJSONObject("conversation").getString("_id")
        }
    }

    suspend fun fetchMessages(conversationId: String, afterMillis: Long): Result<List<RemoteMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            val session = savedSession ?: error("Not signed in")
            val suffix = if (afterMillis > 0) "?after=" + java.net.URLEncoder.encode(isoDate(afterMillis), "UTF-8") else ""
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId/messages$suffix")
                .header("Authorization", "Bearer ${session.token}").build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Message sync failed"))
            val array = json.optJSONArray("messages") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val senderObject = item.optJSONObject("sender")
                    val senderId = senderObject?.optString("_id").orEmpty()
                    add(RemoteMessage(
                        item.optString("clientId", item.optString("_id")), conversationId,
                        senderObject?.let { it.optString("displayName", it.optString("username", "Mowell user")) } ?: "Mowell user",
                        item.optString("body"), parseDate(item.optString("sentAt")), senderId == session.user.id
                    ))
                }
            }
        }
    }

    private fun isoDate(value: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(java.util.Date(value))

    private fun parseDate(value: String): Long = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(value)?.time ?: System.currentTimeMillis()
    }.getOrDefault(System.currentTimeMillis())

    private suspend fun postAuth(path: String, body: JSONObject): AuthResult = withContext(Dispatchers.IO) {
        try {
            if (serverUrl.contains("example.invalid")) return@withContext AuthResult(error = "Set your Mowell server URL first")
            val response = client.newCall(Request.Builder().url(serverUrl + path).post(body.toString().toRequestBody(jsonType)).build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) return@withContext AuthResult(error = json.optString("error", "Request failed"))
            val session = AuthSession(json.getString("token"), parseUser(json.getJSONObject("user")))
            save(session)
            AuthResult(session = session)
        } catch (error: Exception) {
            AuthResult(error = error.message ?: "Could not reach the Mowell server")
        }
    }

    private fun parseUser(json: JSONObject) = UserProfile(
        json.getString("id"), json.getString("username"), json.optString("email"),
        json.optString("displayName", json.getString("username")), json.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" }
    )

    private fun save(session: AuthSession) {
        prefs.edit().putString("token", session.token).putString("user_id", session.user.id)
            .putString("username", session.user.username).putString("email", session.user.email)
            .putString("display_name", session.user.displayName).putString("avatar_url", session.user.avatarUrl).apply()
    }

    fun logout() {
        prefs.edit().remove("token").remove("user_id").remove("username").remove("email").remove("display_name").remove("avatar_url").apply()
    }
}
