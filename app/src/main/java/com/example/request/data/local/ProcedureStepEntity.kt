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

@Entity(tableName = "procedure_steps")
data class ProcedureStepEntity(

    @PrimaryKey
    val id: String,

    val transactionId: String,

    val stepNumber: Int,

    val instruction: String,

    // ==========================================
    // OPTIONAL IMAGE
    // ==========================================

    val remoteImageUrl: String? = null,

    val localImagePath: String? = null,

    val imageCaption: String? = null,

    // ==========================================
    // OPTIONAL WEBSITE LINK
    // ==========================================

    // Name that will be displayed to the student
    // Example: "Online Application Form"
    val websiteName: String? = null,

    // Actual website that will open when clicked
    // Example: "https://example.com/application"
    val websiteUrl: String? = null,

    // ==========================================
    // STATUS
    // ==========================================

    val isActive: Boolean = true,

    val updatedAt: Long = System.currentTimeMillis()
)