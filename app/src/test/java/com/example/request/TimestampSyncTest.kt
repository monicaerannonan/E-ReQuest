package com.example.request

import com.example.request.data.local.*
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class TimestampSyncTest {

    // Mirror of the readTimestampMillis logic in FirestoreSyncManager
    private fun parseTimestampValue(value: Any?): Long? {
        return when (value) {
            is Number -> value.toLong()
            is Timestamp -> value.toDate().time
            is Date -> value.time
            else -> null
        }
    }

    @Test
    fun testLegacyNumericTimestamp() {
        val legacyMillis: Long = 1712000000000L
        val result = parseTimestampValue(legacyMillis)
        assertEquals(1712000000000L, result)

        val intMillis: Int = 12345678
        val intResult = parseTimestampValue(intMillis)
        assertEquals(12345678L, intResult)

        val doubleMillis: Double = 1712000000000.0
        val doubleResult = parseTimestampValue(doubleMillis)
        assertEquals(1712000000000L, doubleResult)
    }

    @Test
    fun testFirestoreTimestamp() {
        val expectedDate = Date(1712000000500L)
        val firestoreTimestamp = Timestamp(expectedDate)
        val result = parseTimestampValue(firestoreTimestamp)
        assertEquals(1712000000500L, result)
    }

    @Test
    fun testJavaDateTimestamp() {
        val date = Date(1712000000123L)
        val result = parseTimestampValue(date)
        assertEquals(1712000000123L, result)
    }

    @Test
    fun testMissingAndNullTimestamp() {
        val nullResult = parseTimestampValue(null)
        assertNull(nullResult)
    }

    @Test
    fun testInvalidTimestampType() {
        val invalidResult = parseTimestampValue("2026-10-08T00:00:00Z")
        assertNull(invalidResult)

        val objectResult = parseTimestampValue(Any())
        assertNull(objectResult)
    }

    @Test
    fun testServiceEntityCreationWithBothFormats() {
        // Legacy format
        val legacyUpdatedAt = parseTimestampValue(1712000000000L) ?: 0L
        val legacyService = ServiceEntity(
            id = "service_001",
            name = "Civil Registry",
            description = "Birth certificate",
            isActive = true,
            updatedAt = legacyUpdatedAt
        )
        assertEquals(1712000000000L, legacyService.updatedAt)

        // New serverTimestamp() format
        val newUpdatedAt = parseTimestampValue(Timestamp(Date(1712000000500L))) ?: 0L
        val newService = ServiceEntity(
            id = "service_002",
            name = "Business Permit",
            description = "New business",
            isActive = true,
            updatedAt = newUpdatedAt
        )
        assertEquals(1712000000500L, newService.updatedAt)
    }

    @Test
    fun testTransactionEntityCreationWithBothFormats() {
        val legacyUpdatedAt = parseTimestampValue(1712000000000L) ?: 0L
        val legacyTransaction = TransactionEntity(
            id = "tx_001",
            serviceId = "service_001",
            name = "Birth Certificate Request",
            description = "Request birth cert",
            officeName = "Registrar",
            officeSchedule = "8am-5pm",
            isActive = true,
            updatedAt = legacyUpdatedAt
        )
        assertEquals(1712000000000L, legacyTransaction.updatedAt)

        val newUpdatedAt = parseTimestampValue(Timestamp(Date(1712000000500L))) ?: 0L
        val newTransaction = TransactionEntity(
            id = "tx_002",
            serviceId = "service_001",
            name = "Marriage Certificate Request",
            description = "Request marriage cert",
            officeName = "Registrar",
            officeSchedule = "8am-5pm",
            isActive = true,
            updatedAt = newUpdatedAt
        )
        assertEquals(1712000000500L, newTransaction.updatedAt)
    }

    @Test
    fun testAnnouncementEntityCreationWithBothFormats() {
        val legacyPosted = parseTimestampValue(1712000000000L) ?: System.currentTimeMillis()
        val legacyAnnouncement = AnnouncementEntity(
            id = "ann_001",
            title = "Holiday Notice",
            message = "Office closed tomorrow",
            datePosted = legacyPosted,
            updatedAt = legacyPosted
        )
        assertEquals(1712000000000L, legacyAnnouncement.datePosted)

        val newPosted = parseTimestampValue(Timestamp(Date(1712000000500L))) ?: System.currentTimeMillis()
        val newAnnouncement = AnnouncementEntity(
            id = "ann_002",
            title = "Maintenance Notice",
            message = "System upgrade",
            datePosted = newPosted,
            updatedAt = newPosted
        )
        assertEquals(1712000000500L, newAnnouncement.datePosted)
    }
}
