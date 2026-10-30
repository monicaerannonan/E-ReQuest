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

class UpdateNotificationManager(
    private val dao: ERequestDao
) {

    suspend fun save(
        notification: UpdateNotification
    ) {
        val entity =
            UpdateNotificationEntity(
                id = notification.id,
                serviceName = notification.serviceName,
                transactionId = notification.transactionId,
                transactionName = notification.transactionName,
                updatedSection = notification.updatedSection,
                message = notification.message,
                dateTime = notification.dateTime,
                isRead = false,
                isNotified = notification.isNotified
            )

        dao.insertUpdateNotification(
            entity
        )
    }

    suspend fun getUnread(): List<UpdateNotification> {
        return dao
            .getUnreadUpdateNotifications()
            .map { entity ->
                entity.toDomainModel()
            }
    }

    suspend fun getUnnotified(): List<UpdateNotification> {
        return dao
            .getUnnotifiedUpdateNotifications()
            .map { entity ->
                entity.toDomainModel()
            }
    }

    suspend fun markAsNotified(id: String) {
        dao.markUpdateNotificationAsNotified(id)
    }

    suspend fun markAsRead(
        id: String
    ) {
        dao.markUpdateNotificationAsRead(id)
        dao.markUpdateNotificationAsNotified(id)
    }

    suspend fun deleteAll() {
        dao.deleteAllUpdateNotifications()
    }

    private fun UpdateNotificationEntity.toDomainModel(): UpdateNotification {
        return UpdateNotification(
            id = id,
            serviceName = serviceName,
            transactionId = transactionId,
            transactionName = transactionName,
            updatedSection = updatedSection,
            message = message,
            dateTime = dateTime,
            isNotified = isNotified
        )
    }
}
