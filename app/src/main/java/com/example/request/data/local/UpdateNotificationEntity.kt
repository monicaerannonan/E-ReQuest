package com.example.request.data.local

import com.example.request.R
import com.example.request.data.local.*
import com.example.request.data.sync.*
import com.example.request.features.checklist.*
import com.example.request.features.home.*
import com.example.request.features.notifications.*
import com.example.request.features.progress.*
import com.example.request.features.services.*
import com.example.request.features.splash.*
import com.example.request.features.transactions.*
import com.example.request.ui.custom.*

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
