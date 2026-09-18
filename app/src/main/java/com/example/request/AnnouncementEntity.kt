package com.example.request

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "announcements")
data class AnnouncementEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val message: String,
    val datePosted: Long,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)