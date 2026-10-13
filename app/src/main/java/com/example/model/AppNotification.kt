package com.example.model

enum class NotificationType {
    ALL,
    BROADCAST,
    LIVE_EVENT,
    LIVE_TV,
    MOVIE,
    PLAYLIST,
    APP_UPDATE
}

data class AppNotification(
    val id: String = "notif_${System.currentTimeMillis()}_${(1000..9999).random()}",
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: NotificationType = NotificationType.BROADCAST,
    val targetId: String? = null,
    val targetType: String? = null, // "EVENTS", "LIVE_TV", "MOVIES", "PLAYLIST", "UPDATE"
    val imageUrl: String? = null,
    val isRead: Boolean = false,
    val actionUrl: String? = null,
    val sender: String = "Admin"
)
