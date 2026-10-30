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