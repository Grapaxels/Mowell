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
    val unreadCount: Int = 0
)

data class CachedUser(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?
)
