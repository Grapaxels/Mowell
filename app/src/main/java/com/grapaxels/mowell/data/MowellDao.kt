package com.grapaxels.mowell.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class MowellDao(private val db: () -> SQLiteDatabase) {
    private val conversations = MutableStateFlow(loadConversations())
    private val messageFlows = ConcurrentHashMap<String, MutableStateFlow<List<MessageEntity>>>()

    fun observeConversations(): Flow<List<ConversationEntity>> = conversations

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageFlows.getOrPut(conversationId) { MutableStateFlow(loadMessages(conversationId)) }

    suspend fun latestMessageTime(conversationId: String): Long = withContext(Dispatchers.IO) {
        db().rawQuery("SELECT COALESCE(MAX(sentAt), 0) FROM messages WHERE conversationId = ?", arrayOf(conversationId)).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    suspend fun upsertConversation(conversation: ConversationEntity) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", conversation.id)
            put("title", conversation.title)
            put("subtitle", conversation.subtitle)
            put("isGroup", if (conversation.isGroup) 1 else 0)
            put("updatedAt", conversation.updatedAt)
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

    suspend fun updateDelivery(id: String, delivery: String, route: String) = withContext(Dispatchers.IO) {
        val database = db()
        val conversationId = database.query("messages", arrayOf("conversationId"), "id = ?", arrayOf(id), null, null, null).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        val values = ContentValues().apply { put("delivery", delivery); put("route", route) }
        database.update("messages", values, "id = ?", arrayOf(id))
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

    private fun refreshMessages(conversationId: String) {
        messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value = loadMessages(conversationId)
    }

    private fun loadConversations(): List<ConversationEntity> = db().query(
        "conversations", null, null, null, null, null, "updatedAt DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(ConversationEntity(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getString(cursor.getColumnIndexOrThrow("subtitle")),
                cursor.getInt(cursor.getColumnIndexOrThrow("isGroup")) == 1,
                cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt"))
            ))
        }
    }

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
}
