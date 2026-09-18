package com.example.request

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "department_guidelines")
data class DepartmentGuidelineEntity(
    @PrimaryKey
    val id: String,
    val transactionId: String,
    val guidelineText: String,
    val displayOrder: Int,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)