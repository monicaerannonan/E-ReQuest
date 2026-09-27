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
                isRead = false
            )

        dao.insertUpdateNotification(
            entity
        )
    }

    suspend fun getUnread():
            List<UpdateNotification> {

        return dao
            .getUnreadUpdateNotifications()
            .map { entity ->

                UpdateNotification(
                    id = entity.id,
                    serviceName =
                        entity.serviceName,
                    transactionId =
                        entity.transactionId,
                    transactionName =
                        entity.transactionName,
                    updatedSection =
                        entity.updatedSection,
                    message =
                        entity.message,
                    dateTime =
                        entity.dateTime
                )
            }
    }

    suspend fun markAsRead(
        id: String
    ) {

        dao.markUpdateNotificationAsRead(
            id
        )
    }

    suspend fun deleteAll() {

        dao.deleteAllUpdateNotifications()
    }
}