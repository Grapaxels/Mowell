package com.grapaxels.mowell.auth

import android.content.Context
import android.util.Base64
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
data class ConnectionRequest(val id: String, val direction: String, val user: UserProfile)
data class GroupInvitation(val id: String, val groupId: String, val groupTitle: String, val inviter: UserProfile)
data class GroupMember(val user: UserProfile, val isAdmin: Boolean, val isCreator: Boolean)
data class GroupMemberState(
    val members: List<GroupMember>,
    val creatorId: String,
    val viewerIsAdmin: Boolean,
    val bannedMembers: List<UserProfile> = emptyList()
)
data class AuthResult(val session: AuthSession? = null, val error: String? = null, val verificationEmail: String? = null)
data class RemoteConversation(
    val id: String, val title: String, val isGroup: Boolean, val updatedAt: Long,
    val username: String? = null, val avatarUrl: String? = null,
    val lastSeenAt: Long = 0L, val members: String = "",
    val blocked: Boolean = false, val blockedByMe: Boolean = false
)
data class RemoteMessage(
    val id: String, val conversationId: String, val sender: String, val body: String,
    val sentAt: Long, val outgoing: Boolean, val kind: String = "text",
    val attachmentId: String? = null, val attachmentMime: String? = null,
    val attachmentName: String? = null,
    val editedAt: Long = 0L,
    val replyToId: String? = null,
    val threadRootId: String? = null,
    val reactions: String = "{}",
    val metadata: String = "{}",
    val syncAt: Long = sentAt
)

class AuthRepository(context: Context) {
    private val prefs = context.getSharedPreferences("mowell_session", Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    var serverUrl: String
        get() = "https://mowell-api.grapaxels.in"
        set(@Suppress("UNUSED_PARAMETER") value) { }

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

    suspend fun verifyEmail(email: String, code: String): AuthResult = postAuth(
        "/v1/auth/verify-email", JSONObject().put("email", email).put("code", code)
    )

    suspend fun resendVerification(email: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("email", email)
            client.newCall(Request.Builder().url("$serverUrl/v1/auth/resend-verification").post(body.toString().toRequestBody(jsonType)).build()).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if (!response.isSuccessful) AuthResult(error = json.optString("error", "Could not resend code"), verificationEmail = email)
                else AuthResult(error = "A new code was sent", verificationEmail = email)
            }
        } catch (error: Exception) { AuthResult(error = error.message ?: "Could not resend code", verificationEmail = email) }
    }

    suspend fun requestPasswordReset(email: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("email", email.trim())
            client.newCall(Request.Builder().url("$serverUrl/v1/auth/request-password-reset")
                .post(body.toString().toRequestBody(jsonType)).build()).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if (!response.isSuccessful) error(json.optString("error", "Could not send reset code"))
                json.optString("message", "Reset code sent")
            }
        }
    }

    suspend fun resetPassword(email: String, code: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("email", email.trim()).put("code", code.trim()).put("password", password)
            client.newCall(Request.Builder().url("$serverUrl/v1/auth/reset-password")
                .post(body.toString().toRequestBody(jsonType)).build()).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if (!response.isSuccessful) error(json.optString("error", "Could not update password"))
                json.optString("message", "Password updated")
            }
        }
    }

    suspend fun validateSession(): AuthResult = withContext(Dispatchers.IO) {
        try {
            val session = savedSession ?: return@withContext AuthResult(error = "Not signed in")
            client.newCall(Request.Builder().url("$serverUrl/v1/me").header("Authorization", "Bearer ${session.token}").build()).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if (json.optBoolean("verificationRequired")) return@withContext AuthResult(error = json.optString("error", "Verify your email"), verificationEmail = json.optString("email"))
                if (!response.isSuccessful) return@withContext AuthResult(error = json.optString("error", "Session validation failed"))
                val refreshed = AuthSession(session.token, parseUser(json.getJSONObject("user")))
                save(refreshed)
                AuthResult(session = refreshed)
            }
        } catch (error: Exception) { AuthResult(error = error.message ?: "Could not validate session") }
    }

    suspend fun updateDisplayName(displayName: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val session = savedSession ?: return@withContext AuthResult(error = "Not signed in")
            val body = JSONObject().put("displayName", displayName.trim())
            val request = Request.Builder().url("$serverUrl/v1/me").header("Authorization", "Bearer ${session.token}")
                .patch(body.toString().toRequestBody(jsonType)).build()
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if (!response.isSuccessful) return@withContext AuthResult(error = json.optString("error", "Could not update name"))
                val updated = AuthSession(session.token, parseUser(json.getJSONObject("user")))
                save(updated)
                AuthResult(session = updated)
            }
        } catch (error: Exception) { AuthResult(error = error.message ?: "Could not update name") }
    }

    suspend fun updateAvatar(data: ByteArray, mimeType: String = "image/jpeg"): AuthResult = withContext(Dispatchers.IO) {
        try {
            require(data.size <= 1_572_864) { "Profile photo must be 1.5 MB or smaller" }
            val session = savedSession ?: return@withContext AuthResult(error = "Not signed in")
            val body = JSONObject().put("mimeType", mimeType).put("data", Base64.encodeToString(data, Base64.NO_WRAP))
            val request = Request.Builder().url("$serverUrl/v1/me/avatar").header("Authorization", "Bearer ${session.token}")
                .post(body.toString().toRequestBody(jsonType)).build()
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if (!response.isSuccessful) return@withContext AuthResult(error = json.optString("error", "Could not update profile photo"))
                val updated = AuthSession(session.token, parseUser(json.getJSONObject("user")))
                save(updated)
                AuthResult(session = updated)
            }
        } catch (error: Exception) { AuthResult(error = error.message ?: "Could not update profile photo") }
    }

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

    suspend fun fetchConnections(): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/contacts")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not load connections"))
            val array = json.optJSONArray("users") ?: return@runCatching emptyList()
            buildList { for (index in 0 until array.length()) add(parseUser(array.getJSONObject(index))) }
        }
    }

    suspend fun fetchConnectionRequests(): Result<List<ConnectionRequest>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/contacts/requests")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not load connection requests"))
            val array = json.optJSONArray("requests") ?: return@runCatching emptyList()
            buildList { for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(ConnectionRequest(item.getString("_id"), item.getString("direction"), parseUser(item.getJSONObject("user"))))
            } }
        }
    }

    suspend fun sendConnectionRequest(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("userId", userId)
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/contacts/requests")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").post(body.toString().toRequestBody(jsonType)).build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not send connection request"))
        }
    }

    suspend fun respondConnectionRequest(requestId: String, accept: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = if (accept) "$serverUrl/v1/contacts/requests/$requestId/accept" else "$serverUrl/v1/contacts/requests/$requestId"
            val builder = Request.Builder().url(endpoint)
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}")
            val response = client.newCall(if (accept) builder.post("{}".toRequestBody(jsonType)).build() else builder.delete().build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not update connection request"))
        }
    }

    suspend fun fetchGroupInvitations(): Result<List<GroupInvitation>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/groups/invitations")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not load group invitations"))
            val array = json.optJSONArray("invitations") ?: return@runCatching emptyList()
            buildList { for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(GroupInvitation(item.getString("_id"), item.getString("groupId"), item.getString("groupTitle"), parseUser(item.getJSONObject("inviter"))))
            } }
        }
    }

    suspend fun respondGroupInvitation(invitationId: String, accept: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url("$serverUrl/v1/groups/invitations/$invitationId")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}")
            val response = client.newCall(if (accept) builder.post("{}".toRequestBody(jsonType)).build() else builder.delete().build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not update group invitation"))
        }
    }

    suspend fun createGroup(title: String, memberIds: Set<String>, inviteIds: Set<String>, groupType: String = "private", groupPassword: String = ""): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("title", title.trim()).put("isGroup", true)
                .put("memberIds", org.json.JSONArray(memberIds.toList())).put("inviteIds", org.json.JSONArray(inviteIds.toList()))
                .put("groupType", groupType).put("groupPassword", groupPassword)
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/conversations")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").post(body.toString().toRequestBody(jsonType)).build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not create group"))
            json.getJSONObject("conversation").getString("_id")
        }
    }

    suspend fun addGroupMembers(conversationId: String, memberIds: Set<String>, inviteIds: Set<String>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("memberIds", org.json.JSONArray(memberIds.toList())).put("inviteIds", org.json.JSONArray(inviteIds.toList()))
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId/members")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").post(body.toString().toRequestBody(jsonType)).build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not add group members"))
        }
    }

    suspend fun updateGroupTitle(conversationId: String, title: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("title", title.trim())
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").patch(body.toString().toRequestBody(jsonType)).build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not update group name"))
        }
    }

    suspend fun updateGroupAvatar(conversationId: String, data: ByteArray, mimeType: String = "image/jpeg"): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(data.size <= 1_572_864) { "Group icon must be 1.5 MB or smaller" }
            val body = JSONObject().put("mimeType", mimeType).put("data", Base64.encodeToString(data, Base64.NO_WRAP))
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId/avatar")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").post(body.toString().toRequestBody(jsonType)).build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not update group icon"))
            json.optString("avatarUrl")
        }
    }

    suspend fun fetchGroupMembers(conversationId: String): Result<GroupMemberState> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId/members")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not load group members"))
            val array = json.optJSONArray("members")
            val members = buildList { if (array != null) for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(GroupMember(parseUser(item), item.optBoolean("isAdmin"), item.optBoolean("isCreator")))
            } }
            val bannedArray = json.optJSONArray("bannedMembers")
            val banned = buildList { if (bannedArray != null) for (index in 0 until bannedArray.length()) add(parseUser(bannedArray.getJSONObject(index))) }
            GroupMemberState(members, json.optString("creatorId"), json.optBoolean("viewerIsAdmin"), banned)
        }
    }

    suspend fun setGroupAdmin(conversationId: String, userId: String, admin: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url("$serverUrl/v1/conversations/$conversationId/admins/$userId")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}")
            val response = client.newCall(if (admin) builder.post("{}".toRequestBody(jsonType)).build() else builder.delete().build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not change admin role"))
        }
    }

    suspend fun removeGroupMember(conversationId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId/members/$userId")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").delete().build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not remove group member"))
        }
    }

    suspend fun setGroupMemberBanned(conversationId: String, userId: String, banned: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url("$serverUrl/v1/conversations/$conversationId/banned/$userId")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}")
            val request = if (banned) builder.post("{}".toRequestBody(jsonType)).build() else builder.delete().build()
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if (!response.isSuccessful) error(json.optString("error", "Could not update banned member"))
            }
        }
    }

    suspend fun leaveGroup(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId/leave")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}")
                .post("{}".toRequestBody(jsonType)).build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not exit group"))
        }
    }

    suspend fun deleteGroup(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").delete().build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not delete group"))
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

    suspend fun ringCall(room: String, conversationId: String, video: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("conversationId", conversationId).put("video", video)
            val response = client.newCall(Request.Builder().url("$serverUrl/v1/calls/$room/ring")
                .header("Authorization", "Bearer ${savedSession?.token.orEmpty()}").post(body.toString().toRequestBody(jsonType)).build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (!response.isSuccessful) error(json.optString("error", "Could not start call"))
        }
    }

    suspend fun fetchConversations(): Result<List<RemoteConversation>> = withContext(Dispatchers.IO) {
        runCatching {
            val session = savedSession ?: error("Not signed in")
            client.newCall(Request.Builder().url("$serverUrl/v1/conversations")
                .header("Authorization", "Bearer ${session.token}").build()).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if (!response.isSuccessful) error(json.optString("error", "Conversation sync failed"))
                val array = json.optJSONArray("conversations") ?: return@use emptyList()
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        val isGroup = item.optBoolean("isGroup")
                        val members = item.optJSONArray("members")
                        var directName = "Mowell user"
                        var directUsername: String? = null
                        var directAvatar: String? = null
                        var directLastSeen = 0L
                        val memberNames = mutableListOf<String>()
                        if (members != null) for (memberIndex in 0 until members.length()) {
                            val member = members.optJSONObject(memberIndex) ?: continue
                            if (member.optString("_id") != session.user.id) {
                                val memberName = member.optString("displayName", member.optString("username", directName))
                                memberNames += memberName
                                if (directUsername == null) {
                                    directName = memberName
                                    directUsername = member.optString("username").takeIf { it.isNotBlank() }
                                    directAvatar = member.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" }
                                    directLastSeen = parseDate(member.optString("lastSeenAt"))
                                }
                            }
                        }
                        val title = if (isGroup) item.optString("title").ifBlank { "Mowell group" } else directName
                        val avatar = if (isGroup) item.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" } else directAvatar
                        add(RemoteConversation(item.getString("_id"), title, isGroup, parseDate(item.optString("lastMessageAt")), directUsername, avatar, directLastSeen, memberNames.joinToString(", "), item.optBoolean("blocked"), item.optBoolean("blockedByMe")))
                    }
                }
            }
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
                    add(parseMessage(item, conversationId, session))
                }
            }
        }
    }

    suspend fun uploadAttachment(conversationId: String, clientId: String, fileName: String, mimeType: String, data: ByteArray): Result<RemoteMessage> = withContext(Dispatchers.IO) {
        runCatching {
            require(data.size <= 2_621_440) { "Attachment must be 2.5 MB or smaller" }
            val session = savedSession ?: error("Not signed in")
            val body = JSONObject().put("clientId", clientId).put("fileName", fileName)
                .put("mimeType", mimeType).put("data", Base64.encodeToString(data, Base64.NO_WRAP))
            client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId/attachments")
                .header("Authorization", "Bearer ${session.token}")
                .post(body.toString().toRequestBody(jsonType)).build()).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if (!response.isSuccessful) error(json.optString("error", "Attachment upload failed"))
                parseMessage(json.getJSONObject("message"), conversationId, session)
            }
        }
    }

    suspend fun downloadAttachment(id: String): Result<Pair<String, ByteArray>> = withContext(Dispatchers.IO) {
        runCatching {
            val session = savedSession ?: error("Not signed in")
            client.newCall(Request.Builder().url("$serverUrl/v1/attachments/$id")
                .header("Authorization", "Bearer ${session.token}").build()).execute().use { response ->
                if (!response.isSuccessful) error("Attachment download failed")
                response.header("Content-Type", "application/octet-stream")!! to (response.body?.bytes() ?: error("Empty attachment"))
            }
        }
    }

    suspend fun setTyping(conversationId: String, active: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = savedSession ?: error("Not signed in")
            val body = JSONObject().put("active", active)
            client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId/typing")
                .header("Authorization", "Bearer ${session.token}")
                .post(body.toString().toRequestBody(jsonType)).build()).execute().use { response ->
                if (!response.isSuccessful) error("Typing update failed")
            }
        }
    }

    suspend fun fetchTyping(conversationId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val session = savedSession ?: error("Not signed in")
            client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId/typing")
                .header("Authorization", "Bearer ${session.token}").build()).execute().use { response ->
                if (!response.isSuccessful) error("Typing status unavailable")
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                val users = json.optJSONArray("users") ?: return@use emptyList()
                buildList { for (index in 0 until users.length()) users.optString(index).takeIf { it.isNotBlank() }?.let(::add) }
            }
        }
    }

    suspend fun deleteMessage(conversationId: String, clientId: String, everyone: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = savedSession ?: error("Not signed in")
            client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId/messages/$clientId?everyone=$everyone")
                .header("Authorization", "Bearer ${session.token}").delete().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                    error(json.optString("error", "Message could not be deleted"))
                }
            }
        }
    }

    suspend fun editMessage(conversationId: String, clientId: String, body: String): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val session = savedSession ?: error("Not signed in")
            val payload = JSONObject().put("body", body)
            client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId/messages/$clientId")
                .header("Authorization", "Bearer ${session.token}")
                .patch(payload.toString().toRequestBody(jsonType)).build()).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if (!response.isSuccessful) error(json.optString("error", "Message could not be edited"))
                parseDate(json.optJSONObject("message")?.optString("editedAt").orEmpty()).takeIf { it > 0L } ?: System.currentTimeMillis()
            }
        }
    }

    suspend fun toggleReaction(conversationId: String, clientId: String, emoji: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val session = savedSession ?: error("Not signed in")
            val payload = JSONObject().put("emoji", emoji)
            client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId/messages/$clientId/reactions")
                .header("Authorization", "Bearer ${session.token}")
                .post(payload.toString().toRequestBody(jsonType)).build()).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if (!response.isSuccessful) error(json.optString("error", "Reaction could not be updated"))
                (json.optJSONObject("reactions") ?: JSONObject()).toString()
            }
        }
    }

    suspend fun votePoll(conversationId: String, clientId: String, option: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val session = savedSession ?: error("Not signed in")
            val payload = JSONObject().put("option", option)
            client.newCall(Request.Builder().url("$serverUrl/v1/conversations/$conversationId/messages/$clientId/poll-vote")
                .header("Authorization", "Bearer ${session.token}")
                .post(payload.toString().toRequestBody(jsonType)).build()).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if (!response.isSuccessful) error(json.optString("error", "Vote could not be saved"))
                (json.optJSONObject("metadata") ?: JSONObject()).toString()
            }
        }
    }

    suspend fun setBlocked(conversationId: String, blocked: Boolean): Result<Pair<Boolean, Boolean>> = withContext(Dispatchers.IO) {
        runCatching {
            val session = savedSession ?: error("Not signed in")
            val builder = Request.Builder().url("$serverUrl/v1/conversations/$conversationId/block")
                .header("Authorization", "Bearer ${session.token}")
            val request = if (blocked) builder.post("{}".toRequestBody(jsonType)).build() else builder.delete().build()
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                if (!response.isSuccessful) error(json.optString("error", "Could not update blocked user"))
                json.optBoolean("blocked") to json.optBoolean("blockedByMe")
            }
        }
    }

    private fun parseMessage(item: JSONObject, conversationId: String, session: AuthSession): RemoteMessage {
        val senderObject = item.optJSONObject("sender")
        val senderId = senderObject?.optString("_id").orEmpty()
        val attachment = item.optJSONObject("attachment")
        val reactionCounts = JSONObject()
        val reactionArray = item.optJSONArray("reactions")
        if (reactionArray != null) for (index in 0 until reactionArray.length()) {
            val emoji = reactionArray.optJSONObject(index)?.optString("emoji").orEmpty()
            if (emoji.isNotBlank()) reactionCounts.put(emoji, reactionCounts.optInt(emoji) + 1)
        }
        return RemoteMessage(
            item.optString("clientId", item.optString("_id")), conversationId,
            senderObject?.let { it.optString("displayName", it.optString("username", "Mowell user")) } ?: "Mowell user",
            item.optString("body"), parseDate(item.optString("sentAt")), senderId == session.user.id,
            item.optString("kind", "text"), attachment?.optString("_id"),
            attachment?.optString("mimeType"), attachment?.optString("fileName"),
            parseDate(item.optString("editedAt")),
            item.optString("replyToClientId").takeIf { it.isNotBlank() && it != "null" },
            item.optString("threadRootClientId").takeIf { it.isNotBlank() && it != "null" },
            reactionCounts.toString(),
            (item.optJSONObject("metadata") ?: JSONObject()).toString(),
            maxOf(parseDate(item.optString("updatedAt")), parseDate(item.optString("sentAt")))
        )
    }

    private fun isoDate(value: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(java.util.Date(value))

    private fun parseDate(value: String): Long {
        if (value.isBlank() || value == "null") return 0L
        return runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(value)?.time ?: System.currentTimeMillis()
        }.getOrDefault(0L)
    }

    private suspend fun postAuth(path: String, body: JSONObject): AuthResult = withContext(Dispatchers.IO) {
        try {
            if (serverUrl.contains("example.invalid")) return@withContext AuthResult(error = "Set your Mowell server URL first")
            val response = client.newCall(Request.Builder().url(serverUrl + path).post(body.toString().toRequestBody(jsonType)).build()).execute()
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            if (json.optBoolean("verificationRequired")) return@withContext AuthResult(error = json.optString("message", json.optString("error", "Verify your email")), verificationEmail = json.optString("email"))
            if (!response.isSuccessful) return@withContext AuthResult(error = json.optString("error", "Request failed"))
            val session = AuthSession(json.getString("token"), parseUser(json.getJSONObject("user")))
            save(session)
            AuthResult(session = session)
        } catch (error: Exception) {
            AuthResult(error = error.message ?: "Could not reach the Mowell server")
        }
    }

    private fun parseUser(json: JSONObject) = UserProfile(
        json.getString("id"), json.getString("username"), json.optString("email", ""),
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
