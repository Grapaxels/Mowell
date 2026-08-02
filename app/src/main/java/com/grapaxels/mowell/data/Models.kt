package com.grapaxels.mowell.data

data class MessageEntity(
    val id: String,
    val conversationId: String,
    val sender: String,
    val body: String,
    val sentAt: Long,
    val outgoing: Boolean,
    val route: String,
    val delivery: String,
    val kind: String = "text",
    val attachmentId: String? = null,
    val attachmentMime: String? = null,
    val attachmentName: String? = null
)

data class ConversationEntity(
    val id: String,
    val title: String,
    val subtitle: String,
    val isGroup: Boolean,
    val updatedAt: Long,
    val username: String? = null,
    val avatarUrl: String? = null,
    val lastSeenAt: Long = 0L,
    val members: String = "",
    val unreadCount: Int = 0,
    val blocked: Boolean = false,
    val blockedByMe: Boolean = false,
    /** Local-only: hidden conversation tiles stay hidden until a newer incoming message arrives. */
    val hiddenAt: Long = 0L,
    /** A personal contact label. It is stored only in this phone's SQLite database. */
    val localTitle: String? = null
)

data class CachedUser(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?
)

data class ChatListEntity(
    val id: String,
    val name: String,
    val conversationIds: Set<String>
)
