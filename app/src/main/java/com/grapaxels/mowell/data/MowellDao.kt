package com.grapaxels.mowell.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

class MowellDao(private val db: () -> SQLiteDatabase) {
    private val conversations = MutableStateFlow(loadConversations())
    private val chatLists = MutableStateFlow(loadChatLists())
    private val messageFlows = ConcurrentHashMap<String, MutableStateFlow<List<MessageEntity>>>()

    fun observeConversations(): Flow<List<ConversationEntity>> = conversations
    fun observeChatLists(): Flow<List<ChatListEntity>> = chatLists

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageFlows.getOrPut(conversationId) { MutableStateFlow(loadMessages(conversationId)) }

    suspend fun getConversation(id: String): ConversationEntity? = withContext(Dispatchers.IO) {
        loadConversation(id)
    }

    suspend fun latestMessageTime(conversationId: String): Long = withContext(Dispatchers.IO) {
        db().rawQuery("SELECT COALESCE(MAX(sentAt), 0) FROM messages WHERE conversationId = ?", arrayOf(conversationId)).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    suspend fun upsertConversation(conversation: ConversationEntity) = withContext(Dispatchers.IO) {
        // Network syncs do not know about a user's local hide choice. Preserve it
        // until revealConversationOnIncoming receives a genuinely new incoming item.
        val existingHiddenAt = loadConversation(conversation.id)?.hiddenAt ?: 0L
        val hiddenAt = if (conversation.hiddenAt > 0L) conversation.hiddenAt else existingHiddenAt
        val values = ContentValues().apply {
            put("id", conversation.id)
            put("title", conversation.title)
            put("subtitle", conversation.subtitle)
            put("isGroup", if (conversation.isGroup) 1 else 0)
            put("updatedAt", conversation.updatedAt)
            put("username", conversation.username)
            put("avatarUrl", conversation.avatarUrl)
            put("lastSeenAt", conversation.lastSeenAt)
            put("members", conversation.members)
            put("unreadCount", conversation.unreadCount)
            put("blocked", if (conversation.blocked) 1 else 0)
            put("blockedByMe", if (conversation.blockedByMe) 1 else 0)
            put("hiddenAt", hiddenAt)
        }
        db().insertWithOnConflict("conversations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        conversations.value = loadConversations()
    }

    suspend fun insertMessage(message: MessageEntity) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", message.id)
            put("conversationId", message.conversationId)
            put("sender", message.sender)
            put("body", message.body)
            put("sentAt", message.sentAt)
            put("outgoing", if (message.outgoing) 1 else 0)
            put("route", message.route)
            put("delivery", message.delivery)
            put("kind", message.kind)
            put("attachmentId", message.attachmentId)
            put("attachmentMime", message.attachmentMime)
            put("attachmentName", message.attachmentName)
        }
        db().insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        refreshMessages(message.conversationId)
    }

    suspend fun hasMessage(id: String): Boolean = withContext(Dispatchers.IO) {
        db().rawQuery("SELECT 1 FROM messages WHERE id = ? LIMIT 1", arrayOf(id)).use { it.moveToFirst() }
    }

    suspend fun incrementUnread(conversationId: String): Int = withContext(Dispatchers.IO) {
        db().execSQL("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE id = ?", arrayOf(conversationId))
        conversations.value = loadConversations()
        conversations.value.sumOf { it.unreadCount }
    }

    suspend fun markRead(conversationId: String): Int = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("unreadCount", 0) }
        db().update("conversations", values, "id = ?", arrayOf(conversationId))
        conversations.value = loadConversations()
        conversations.value.sumOf { it.unreadCount }
    }

    /** Removes only the tile from this phone. Messages are retained locally. */
    suspend fun hideConversation(conversationId: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("hiddenAt", System.currentTimeMillis())
            put("unreadCount", 0)
        }
        db().update("conversations", values, "id = ?", arrayOf(conversationId))
        conversations.value = loadConversations()
    }

    /** Makes a locally hidden tile visible only when a newer message arrives from someone else. */
    suspend fun revealConversationOnIncoming(conversationId: String, sentAt: Long): Boolean = withContext(Dispatchers.IO) {
        val hiddenAt = loadConversation(conversationId)?.hiddenAt ?: return@withContext false
        if (hiddenAt <= 0L || sentAt <= hiddenAt) return@withContext false
        db().update("conversations", ContentValues().apply { put("hiddenAt", 0) }, "id = ?", arrayOf(conversationId))
        conversations.value = loadConversations()
        true
    }

    /** Used after leaving a group or permanently deleting one. */
    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        val database = db()
        database.beginTransaction()
        try {
            database.delete("messages", "conversationId = ?", arrayOf(conversationId))
            database.delete("chat_list_members", "conversationId = ?", arrayOf(conversationId))
            database.delete("conversations", "id = ?", arrayOf(conversationId))
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
        messageFlows.remove(conversationId)?.value = emptyList()
        conversations.value = loadConversations()
        chatLists.value = loadChatLists()
    }

    suspend fun updateDelivery(id: String, delivery: String, route: String) = withContext(Dispatchers.IO) {
        val database = db()
        val conversationId = database.query("messages", arrayOf("conversationId"), "id = ?", arrayOf(id), null, null, null).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        val values = ContentValues().apply { put("delivery", delivery); put("route", route) }
        database.update("messages", values, "id = ?", arrayOf(id))
        conversationId?.let(::refreshMessages)
    }

    suspend fun deleteMessage(id: String) = withContext(Dispatchers.IO) {
        val database = db()
        val conversationId = database.query("messages", arrayOf("conversationId"), "id = ?", arrayOf(id), null, null, null).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        database.delete("messages", "id = ?", arrayOf(id))
        conversationId?.let(::refreshMessages)
    }

    suspend fun cacheUsers(users: List<CachedUser>) = withContext(Dispatchers.IO) {
        val database = db()
        database.beginTransaction()
        try {
            users.forEach { user ->
                val values = ContentValues().apply {
                    put("id", user.id); put("username", user.username); put("displayName", user.displayName)
                    put("avatarUrl", user.avatarUrl); put("cachedAt", System.currentTimeMillis())
                }
                database.insertWithOnConflict("cached_users", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
    }

    suspend fun searchCachedUsers(query: String): List<CachedUser> = withContext(Dispatchers.IO) {
        db().query("cached_users", null, "username LIKE ?", arrayOf("${query.lowercase()}%"), null, null, "username ASC", "20").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(CachedUser(
                    cursor.getString(cursor.getColumnIndexOrThrow("id")), cursor.getString(cursor.getColumnIndexOrThrow("username")),
                    cursor.getString(cursor.getColumnIndexOrThrow("displayName")), cursor.getString(cursor.getColumnIndexOrThrow("avatarUrl"))
                ))
            }
        }
    }

    suspend fun retainSyncedConversations(ids: Set<String>) = withContext(Dispatchers.IO) {
        val database = db()
        if (ids.isEmpty()) {
            database.delete("conversations", "id != ?", arrayOf("general"))
        } else {
            val placeholders = ids.joinToString(",") { "?" }
            database.delete("conversations", "id != ? AND id NOT IN ($placeholders)", arrayOf("general", *ids.toTypedArray()))
        }
        conversations.value = loadConversations()
    }

    suspend fun createChatList(name: String, conversationIds: Set<String>) = withContext(Dispatchers.IO) {
        val cleanName = name.trim().take(30)
        if (cleanName.isBlank() || conversationIds.isEmpty()) return@withContext
        val database = db()
        val listId = UUID.randomUUID().toString()
        database.beginTransaction()
        try {
            database.insertOrThrow("chat_lists", null, ContentValues().apply {
                put("id", listId); put("name", cleanName); put("createdAt", System.currentTimeMillis())
            })
            conversationIds.forEach { conversationId ->
                database.insertWithOnConflict("chat_list_members", null, ContentValues().apply {
                    put("listId", listId); put("conversationId", conversationId)
                }, SQLiteDatabase.CONFLICT_IGNORE)
            }
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
        chatLists.value = loadChatLists()
    }

    suspend fun clearAccountData() = withContext(Dispatchers.IO) {
        val database = db()
        database.beginTransaction()
        try {
            database.delete("messages", null, null)
            database.delete("conversations", null, null)
            database.delete("cached_users", null, null)
            database.delete("chat_list_members", null, null)
            database.delete("chat_lists", null, null)
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
        messageFlows.values.forEach { it.value = emptyList() }
        conversations.value = emptyList()
        chatLists.value = emptyList()
    }

    private fun refreshMessages(conversationId: String) {
        messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value = loadMessages(conversationId)
    }

    private fun loadConversations(): List<ConversationEntity> = db().query(
        "conversations", null, "hiddenAt = 0", null, null, null, "updatedAt DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(conversationFromCursor(cursor))
        }
    }

    private fun loadConversation(id: String): ConversationEntity? = db().query(
        "conversations", null, "id = ?", arrayOf(id), null, null, null, "1"
    ).use { cursor -> if (cursor.moveToFirst()) conversationFromCursor(cursor) else null }

    private fun conversationFromCursor(cursor: android.database.Cursor) = ConversationEntity(
        cursor.getString(cursor.getColumnIndexOrThrow("id")),
        cursor.getString(cursor.getColumnIndexOrThrow("title")),
        cursor.getString(cursor.getColumnIndexOrThrow("subtitle")),
        cursor.getInt(cursor.getColumnIndexOrThrow("isGroup")) == 1,
        cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")),
        cursor.getString(cursor.getColumnIndexOrThrow("username")),
        cursor.getString(cursor.getColumnIndexOrThrow("avatarUrl")),
        cursor.getLong(cursor.getColumnIndexOrThrow("lastSeenAt")),
        cursor.getString(cursor.getColumnIndexOrThrow("members")),
        cursor.getInt(cursor.getColumnIndexOrThrow("unreadCount")),
        cursor.getInt(cursor.getColumnIndexOrThrow("blocked")) == 1,
        cursor.getInt(cursor.getColumnIndexOrThrow("blockedByMe")) == 1,
        cursor.getLong(cursor.getColumnIndexOrThrow("hiddenAt"))
    )

    private fun loadMessages(conversationId: String): List<MessageEntity> = db().query(
        "messages", null, "conversationId = ?", arrayOf(conversationId), null, null, "sentAt ASC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(MessageEntity(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("conversationId")),
                cursor.getString(cursor.getColumnIndexOrThrow("sender")),
                cursor.getString(cursor.getColumnIndexOrThrow("body")),
                cursor.getLong(cursor.getColumnIndexOrThrow("sentAt")),
                cursor.getInt(cursor.getColumnIndexOrThrow("outgoing")) == 1,
                cursor.getString(cursor.getColumnIndexOrThrow("route")),
                cursor.getString(cursor.getColumnIndexOrThrow("delivery")),
                cursor.getString(cursor.getColumnIndexOrThrow("kind")),
                cursor.getString(cursor.getColumnIndexOrThrow("attachmentId")),
                cursor.getString(cursor.getColumnIndexOrThrow("attachmentMime")),
                cursor.getString(cursor.getColumnIndexOrThrow("attachmentName"))
            ))
        }
    }

    private fun loadChatLists(): List<ChatListEntity> = db().query(
        "chat_lists", arrayOf("id", "name"), null, null, null, null, "createdAt ASC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val members = db().query(
                    "chat_list_members", arrayOf("conversationId"), "listId = ?", arrayOf(id), null, null, null
                ).use { memberCursor ->
                    buildSet { while (memberCursor.moveToNext()) add(memberCursor.getString(0)) }
                }
                add(ChatListEntity(id, cursor.getString(1), members))
            }
        }
    }
}
