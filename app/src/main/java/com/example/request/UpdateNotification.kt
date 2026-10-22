package com.example.request

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
