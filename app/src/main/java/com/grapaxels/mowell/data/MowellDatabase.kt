package com.grapaxels.mowell.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MowellDatabase private constructor(context: Context) {
    private val helper = Helper(context.applicationContext)
    private val mowellDao by lazy { MowellDao { helper.writableDatabase } }
    fun dao(): MowellDao = mowellDao

    private class Helper(context: Context) : SQLiteOpenHelper(context, "mowell.db", null, 3) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE conversations (
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    subtitle TEXT NOT NULL,
                    isGroup INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
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
                    attachmentName TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_messages_conversation ON messages(conversationId, sentAt)")
            createUsers(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createUsers(db)
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE messages ADD COLUMN kind TEXT NOT NULL DEFAULT 'text'")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentId TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentMime TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentName TEXT")
            }
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
    }

    companion object {
        fun create(context: Context) = MowellDatabase(context)
    }
}
