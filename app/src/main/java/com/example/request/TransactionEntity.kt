package com.example.request

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val serviceId: String,
    val name: String,
    val description: String,
    val officeName: String,
    val officeSchedule: String,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)