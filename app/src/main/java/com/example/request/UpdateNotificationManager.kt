package com.example.request

import android.content.Context

class UpdateNotificationManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("UpdateNotifications", Context.MODE_PRIVATE)

    fun save(notification: UpdateNotification) {
        val ids = prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(notification.id)

        prefs.edit()
            .putStringSet("ids", ids)
            .putString("${notification.id}_service", notification.serviceName)
            .putString("${notification.id}_transactionId", notification.transactionId)
            .putString("${notification.id}_transaction", notification.transactionName)
            .putString("${notification.id}_section", notification.updatedSection)
            .putString("${notification.id}_message", notification.message)
            .putString("${notification.id}_dateTime", notification.dateTime)
            .apply()
    }

    fun getUnread(): List<UpdateNotification> {
        val ids = prefs.getStringSet("ids", emptySet()) ?: emptySet()

        return ids.filter { !isRead(it) }.map { id ->
            UpdateNotification(
                id = id,
                serviceName = prefs.getString("${id}_service", "") ?: "",
                transactionId = prefs.getString("${id}_transactionId", "") ?: "",
                transactionName = prefs.getString("${id}_transaction", "") ?: "",
                updatedSection = prefs.getString("${id}_section", "") ?: "",
                message = prefs.getString("${id}_message", "") ?: "",
                dateTime = prefs.getString("${id}_dateTime", "") ?: ""
            )
        }
    }

    fun markAsRead(id: String) {
        prefs.edit().putBoolean("${id}_read", true).apply()
    }

    fun isRead(id: String): Boolean {
        return prefs.getBoolean("${id}_read", false)
    }
}