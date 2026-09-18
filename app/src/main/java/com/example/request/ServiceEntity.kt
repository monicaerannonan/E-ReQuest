package com.example.request

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "services")
data class ServiceEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)