package com.grapaxels.mowell.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MowellDatabase private constructor(context: Context) {
    private val helper = Helper(context.applicationContext)
    private val mowellDao by lazy { MowellDao { helper.writableDatabase } }
    fun dao(): MowellDao = mowellDao

    private class Helper(context: Context) : SQLiteOpenHelper(context, "mowell.db", null, 11) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE conversations (
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    subtitle TEXT NOT NULL,
                    isGroup INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    username TEXT,
                    avatarUrl TEXT,
                    lastSeenAt INTEGER NOT NULL DEFAULT 0,
                    members TEXT NOT NULL DEFAULT '',
                    unreadCount INTEGER NOT NULL DEFAULT 0,
                    blocked INTEGER NOT NULL DEFAULT 0,
                    blockedByMe INTEGER NOT NULL DEFAULT 0,
                    hiddenAt INTEGER NOT NULL DEFAULT 0,
                    localTitle TEXT
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE messages (
                    id TEXT PRIMARY KEY NOT NULL,
                    conversationId TEXT NOT NULL,
                    sender TEXT NOT NULL,
                    body TEXT NOT NULL,
                    sentAt INTEGER NOT NULL,
                    outgoing INTEGER NOT NULL,
                    route TEXT NOT NULL,
                    delivery TEXT NOT NULL,
                    kind TEXT NOT NULL DEFAULT 'text',
                    attachmentId TEXT,
                    attachmentMime TEXT,
                    attachmentName TEXT,
                    editedAt INTEGER NOT NULL DEFAULT 0,
                    replyToId TEXT,
                    threadRootId TEXT,
                    reactions TEXT NOT NULL DEFAULT '{}',
                    metadata TEXT NOT NULL DEFAULT '{}'
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_messages_conversation ON messages(conversationId, sentAt)")
            createUsers(db)
            createChatLists(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createUsers(db)
            if (oldVersion < 3) {
                addColumnIfMissing(db, "messages", "kind", "TEXT NOT NULL DEFAULT 'text'")
                addColumnIfMissing(db, "messages", "attachmentId", "TEXT")
                addColumnIfMissing(db, "messages", "attachmentMime", "TEXT")
                addColumnIfMissing(db, "messages", "attachmentName", "TEXT")
            }
            if (oldVersion < 4) {
                addColumnIfMissing(db, "conversations", "username", "TEXT")
                addColumnIfMissing(db, "conversations", "avatarUrl", "TEXT")
                addColumnIfMissing(db, "conversations", "lastSeenAt", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "conversations", "members", "TEXT NOT NULL DEFAULT ''")
            }
            if (oldVersion < 5) addColumnIfMissing(db, "conversations", "unreadCount", "INTEGER NOT NULL DEFAULT 0")
            if (oldVersion < 6) {
                addColumnIfMissing(db, "conversations", "blocked", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "conversations", "blockedByMe", "INTEGER NOT NULL DEFAULT 0")
            }
            if (oldVersion < 7) createChatLists(db)
            if (oldVersion < 8) addColumnIfMissing(db, "conversations", "hiddenAt", "INTEGER NOT NULL DEFAULT 0")
            if (oldVersion < 9) addColumnIfMissing(db, "conversations", "localTitle", "TEXT")

            // Version 10 repairs databases produced by interrupted or partially
            // installed older updates without deleting the user's local chats.
            createUsers(db)
            createChatLists(db)
            addColumnIfMissing(db, "messages", "kind", "TEXT NOT NULL DEFAULT 'text'")
            addColumnIfMissing(db, "messages", "attachmentId", "TEXT")
            addColumnIfMissing(db, "messages", "attachmentMime", "TEXT")
            addColumnIfMissing(db, "messages", "attachmentName", "TEXT")
            addColumnIfMissing(db, "messages", "editedAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "messages", "replyToId", "TEXT")
            addColumnIfMissing(db, "messages", "threadRootId", "TEXT")
            addColumnIfMissing(db, "messages", "reactions", "TEXT NOT NULL DEFAULT '{}'")
            addColumnIfMissing(db, "messages", "metadata", "TEXT NOT NULL DEFAULT '{}'")
            addColumnIfMissing(db, "conversations", "username", "TEXT")
            addColumnIfMissing(db, "conversations", "avatarUrl", "TEXT")
            addColumnIfMissing(db, "conversations", "lastSeenAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "conversations", "members", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "conversations", "unreadCount", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "conversations", "blocked", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "conversations", "blockedByMe", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "conversations", "hiddenAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "conversations", "localTitle", "TEXT")
        }

        private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, definition: String) {
            val exists = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
                        found = true
                        break
                    }
                }
                found
            }
            if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
        }

        private fun createUsers(db: SQLiteDatabase) = db.execSQL("""
            CREATE TABLE IF NOT EXISTS cached_users (
                id TEXT PRIMARY KEY NOT NULL,
                username TEXT NOT NULL,
                displayName TEXT NOT NULL,
                avatarUrl TEXT,
                cachedAt INTEGER NOT NULL
            )
        """.trimIndent())

        private fun createChatLists(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_lists (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_list_members (
                    listId TEXT NOT NULL,
                    conversationId TEXT NOT NULL,
                    PRIMARY KEY (listId, conversationId)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_list_members_list ON chat_list_members(listId)")
        }
    }

    companion object {
        fun create(context: Context) = MowellDatabase(context)
    }
}
