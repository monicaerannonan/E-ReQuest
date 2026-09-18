package com.example.request

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "requirements")
data class RequirementEntity(
    @PrimaryKey
    val id: String,
    val transactionId: String,
    val requirementText: String,
    val displayOrder: Int,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)