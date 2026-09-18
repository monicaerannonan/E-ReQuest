package com.example.request

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "procedure_steps")
data class ProcedureStepEntity(
    @PrimaryKey
    val id: String,
    val transactionId: String,
    val stepNumber: Int,
    val instruction: String,
    val remoteImageUrl: String? = null,
    val localImagePath: String? = null,
    val imageCaption: String? = null,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)