package com.example.request

import com.example.request.data.local.DepartmentGuidelineEntity
import com.example.request.data.local.ProcedureStepEntity
import com.example.request.data.local.RequirementEntity
import com.example.request.data.local.TransactionEntity
import com.example.request.data.local.UpdateNotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationTimestampSyncTest {

    /**
     * Replicates the effective timestamp resolution logic from FirestoreSyncManager.createTransactionUpdateNotification
     */
    private fun resolveEffectiveTimestamp(
        adminUpdatedAt: Long?,
        transaction: TransactionEntity,
        requirements: List<RequirementEntity>,
        steps: List<ProcedureStepEntity>,
        guidelines: List<DepartmentGuidelineEntity>,
        serviceUpdatedAt: Long? = null
    ): Long {
        val candidateTimestamps = mutableListOf<Long>()
        if (adminUpdatedAt != null && adminUpdatedAt > 0L) {
            candidateTimestamps.add(adminUpdatedAt)
        }
        if (transaction.updatedAt > 0L) {
            candidateTimestamps.add(transaction.updatedAt)
        }
        requirements.forEach {
            if (it.updatedAt > 0L) candidateTimestamps.add(it.updatedAt)
        }
        steps.forEach {
            if (it.updatedAt > 0L) candidateTimestamps.add(it.updatedAt)
        }
        guidelines.forEach {
            if (it.updatedAt > 0L) candidateTimestamps.add(it.updatedAt)
        }
        serviceUpdatedAt?.takeIf { it > 0L }?.let { candidateTimestamps.add(it) }

        return candidateTimestamps.maxOrNull() ?: System.currentTimeMillis()
    }

    @Test
    fun testOfflineReconnectionRetainsAdminSaveTime() {
        val adminSaveTime = 1712000000000L // 2:00 PM
        val clientReconnectTime = adminSaveTime + (3 * 3600 * 1000L) // 5:00 PM (3 hours offline)

        val transaction = TransactionEntity(
            id = "tx_1",
            serviceId = "svc_1",
            name = "Clearance Request",
            description = "Graduation clearance",
            officeName = "Registrar",
            officeSchedule = "8am - 5pm",
            isActive = true,
            updatedAt = adminSaveTime
        )

        // When syncing after coming back online, effective timestamp must match admin's save time, not reconnect time
        val effectiveTimestamp = resolveEffectiveTimestamp(
            adminUpdatedAt = adminSaveTime,
            transaction = transaction,
            requirements = emptyList(),
            steps = emptyList(),
            guidelines = emptyList()
        )

        assertEquals("Timestamp must match the admin update time exactly", adminSaveTime, effectiveTimestamp)

        val formattedDate = SimpleDateFormat(
            "MMM dd, yyyy \u2022 hh:mm a",
            Locale.US
        ).format(Date(effectiveTimestamp))

        val expectedFormatted = SimpleDateFormat(
            "MMM dd, yyyy \u2022 hh:mm a",
            Locale.US
        ).format(Date(adminSaveTime))

        assertEquals(expectedFormatted, formattedDate)
    }

    @Test
    fun testRequirementUpdateReflectsLatestAdminTime() {
        val txCreatedTime = 1712000000000L // 2:00 PM
        val reqUpdatedTime = 1712003600000L // 3:00 PM (Admin updated requirements later)

        val transaction = TransactionEntity(
            id = "tx_1",
            serviceId = "svc_1",
            name = "Clearance Request",
            description = "Graduation clearance",
            officeName = "Registrar",
            officeSchedule = "8am - 5pm",
            isActive = true,
            updatedAt = txCreatedTime
        )

        val requirements = listOf(
            RequirementEntity(
                id = "req_1",
                transactionId = "tx_1",
                requirementText = "2x2 ID picture",
                displayOrder = 1,
                isActive = true,
                updatedAt = reqUpdatedTime
            )
        )

        val effectiveTimestamp = resolveEffectiveTimestamp(
            adminUpdatedAt = reqUpdatedTime,
            transaction = transaction,
            requirements = requirements,
            steps = emptyList(),
            guidelines = emptyList()
        )

        assertEquals("Effective timestamp must pick the latest child update", reqUpdatedTime, effectiveTimestamp)
    }

    @Test
    fun testDeduplicationAndNewAlertDetection() {
        val adminUpdateTime1 = 1712000000000L
        val existingNotification = UpdateNotificationEntity(
            id = "tx_update_tx_1",
            serviceName = "Registrar",
            transactionId = "tx_1",
            transactionName = "Clearance Request",
            updatedSection = "ABOUT",
            message = "Updated",
            dateTime = "Apr 01, 2024 \u2022 02:00 PM",
            isRead = false,
            isNotified = true,
            createdAt = adminUpdateTime1
        )

        // 1. Same update pass does not trigger alert again (isAlreadyNotified = true)
        val isAlreadyNotifiedSame = existingNotification.isNotified &&
                existingNotification.createdAt >= adminUpdateTime1
        assertTrue("Already notified for same update must be true", isAlreadyNotifiedSame)

        // 2. Newer update by admin triggers alert again (isAlreadyNotified = false)
        val adminUpdateTime2 = adminUpdateTime1 + 3600_000L // 1 hour later
        val isAlreadyNotifiedNewer = existingNotification.isNotified &&
                existingNotification.createdAt >= adminUpdateTime2
        assertFalse("Newer update from admin must require a new notification", isAlreadyNotifiedNewer)
    }
}
