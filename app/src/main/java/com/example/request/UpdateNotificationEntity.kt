package com.example.request

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "update_notifications")
data class UpdateNotificationEntity(

    @PrimaryKey
    val id: String,

    val serviceName: String,

    val transactionId: String,

    val transactionName: String,

    val updatedSection: String,

    val message: String,

    val dateTime: String,

    val isRead: Boolean = false,

    val isNotified: Boolean = false,

    val createdAt: Long = System.currentTimeMillis()
)
