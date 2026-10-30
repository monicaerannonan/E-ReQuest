package com.example.request.features.notifications

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

data class UpdateNotification(
    val id: String,
    val serviceName: String,
    val transactionId: String,
    val transactionName: String,
    val updatedSection: String,
    val message: String,
    val dateTime: String,
    val isNotified: Boolean = false
)
