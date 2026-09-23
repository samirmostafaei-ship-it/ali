package com.assistant.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SocialMessage(
    val id: String,
    val packageName: String,
    val appName: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val contentIntent: PendingIntent?
)

class SocialNotificationListenerService : NotificationListenerService() {

    companion object {
        private val _messages = MutableStateFlow<List<SocialMessage>>(emptyList())
        val messages: StateFlow<List<SocialMessage>> = _messages

        fun clearMessages() {
            _messages.value = emptyList()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val pkg = sbn.packageName ?: return
        val extras = sbn.notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appName = when {
            pkg.contains("telegram", ignoreCase = true) -> "تلگرام (Telegram)"
            pkg.contains("whatsapp", ignoreCase = true) -> "واتساپ (WhatsApp)"
            pkg.contains("instagram", ignoreCase = true) -> "اینستاگرام (Instagram)"
            pkg.contains("bale", ignoreCase = true) -> "بله (Bale)"
            pkg.contains("eitaa", ignoreCase = true) -> "ایتا (Eitaa)"
            pkg.contains("rubika", ignoreCase = true) -> "روبیکا (Rubika)"
            pkg.contains("linkedin", ignoreCase = true) -> "لینکدین (LinkedIn)"
            else -> {
                // نام کلی اپلیکیشن
                try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    pkg
                }
            }
        }

        val item = SocialMessage(
            id = "${pkg}_${sbn.id}_${sbn.postTime}",
            packageName = pkg,
            appName = appName,
            sender = title,
            content = text,
            timestamp = sbn.postTime,
            contentIntent = sbn.notification.contentIntent
        )

        // اضافه کردن پیام جدید به ابتدای فهرست
        val currentList = _messages.value.toMutableList()
        currentList.removeAll { it.id == item.id }
        currentList.add(0, item)
        if (currentList.size > 100) {
            currentList.removeAt(currentList.lastIndex)
        }
        _messages.value = currentList
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
