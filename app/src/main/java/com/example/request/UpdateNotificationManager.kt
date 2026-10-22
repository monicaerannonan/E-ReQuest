package com.example.request

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
