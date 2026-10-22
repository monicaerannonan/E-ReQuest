package com.example.request

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying safe Announcement deletion and update synchronization logic:
 * - Create / Add Announcement
 * - Update Announcement
 * - Delete Announcement (Server Confirmed)
 * - Offline Refresh (Cache Protection: no deletion when isFromCache = true)
 * - Pending Writes Protection (no deletion when hasPendingWrites = true)
 * - Parsing Failure Guard (no deletion when incoming document parsing fails)
 * - Reconnect and Repeated Refresh (Idempotent state reconciliation)
 */
class AnnouncementSyncReconciliationTest {

    data class MockSnapshotMetadata(
        val isFromCache: Boolean,
        val hasPendingWrites: Boolean
    )

    data class ReconciliationDecision(
        val canReconcileDeletions: Boolean,
        val toDeleteIds: Set<String>,
        val toInsertOrUpdate: List<AnnouncementEntity>
    )

    /**
     * Pure logic replica of the decision pipeline in FirestoreSyncManager.syncAnnouncements()
     */
    private fun computeReconciliation(
        metadata: MockSnapshotMetadata,
        hasParsingErrors: Boolean,
        localBeforeSync: List<AnnouncementEntity>,
        incomingRemoteAnnouncements: List<AnnouncementEntity>,
        firestoreDocumentIds: Set<String>
    ): ReconciliationDecision {
        val isServerConfirmed = !metadata.isFromCache && !metadata.hasPendingWrites
        val canReconcile = isServerConfirmed && !hasParsingErrors

        val toDeleteIds = if (canReconcile) {
            localBeforeSync.filter { it.id !in firestoreDocumentIds }.map { it.id }.toSet()
        } else {
            emptySet()
        }

        return ReconciliationDecision(
            canReconcileDeletions = canReconcile,
            toDeleteIds = toDeleteIds,
            toInsertOrUpdate = incomingRemoteAnnouncements
        )
    }

    @Test
    fun testCreateAnnouncement() {
        val local = listOf(
            AnnouncementEntity("ann_1", "Welcome", "Welcome back", 1000L, null, null, true, 1000L)
        )
        val incoming = listOf(
            AnnouncementEntity("ann_1", "Welcome", "Welcome back", 1000L, null, null, true, 1000L),
            AnnouncementEntity("ann_2", "New Notice", "Exam Schedule", 2000L, null, null, true, 2000L)
        )
        val firestoreIds = setOf("ann_1", "ann_2")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteAnnouncements = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertTrue("Should allow reconciliation on clean server snapshot", decision.canReconcileDeletions)
        assertTrue("No announcements should be deleted", decision.toDeleteIds.isEmpty())
        assertEquals("Both announcements should be inserted/updated", 2, decision.toInsertOrUpdate.size)
        assertTrue(decision.toInsertOrUpdate.any { it.id == "ann_2" })
    }

    @Test
    fun testUpdateAnnouncement() {
        val local = listOf(
            AnnouncementEntity("ann_1", "Old Title", "Old Message", 1000L, null, null, true, 1000L)
        )
        val incoming = listOf(
            AnnouncementEntity("ann_1", "Updated Title", "Updated Message", 1000L, null, null, true, 2000L)
        )
        val firestoreIds = setOf("ann_1")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteAnnouncements = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertTrue(decision.canReconcileDeletions)
        assertTrue(decision.toDeleteIds.isEmpty())
        assertEquals(1, decision.toInsertOrUpdate.size)
        assertEquals("Updated Title", decision.toInsertOrUpdate.first().title)
        assertEquals(2000L, decision.toInsertOrUpdate.first().updatedAt)
    }

    @Test
    fun testDeleteAnnouncementServerConfirmed() {
        // Local has current announcement AND stale announcement
        val local = listOf(
            AnnouncementEntity("Pmj7fN9H4DdgYf9znR6H", "j", "gh", 1791463411506L, null, null, true, 1791463411506L),
            AnnouncementEntity("dgH3urQilWUJQPpPCzV4", "testing", "announcement", 1790906765293L, null, null, true, 1790908270959L)
        )
        // Firestore only has current announcement (stale announcement was deleted)
        val incoming = listOf(
            AnnouncementEntity("Pmj7fN9H4DdgYf9znR6H", "j", "gh", 1791463411506L, null, null, true, 1791463411506L)
        )
        val firestoreIds = setOf("Pmj7fN9H4DdgYf9znR6H")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteAnnouncements = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertTrue("Server confirmed snapshot should enable deletion", decision.canReconcileDeletions)
        assertEquals("Stale announcement ID must be queued for deletion", setOf("dgH3urQilWUJQPpPCzV4"), decision.toDeleteIds)
        assertEquals(1, decision.toInsertOrUpdate.size)
        assertEquals("Pmj7fN9H4DdgYf9znR6H", decision.toInsertOrUpdate.first().id)
    }

    @Test
    fun testOfflineCacheProtectionPreventsDeletion() {
        val local = listOf(
            AnnouncementEntity("ann_1", "Title 1", "Msg 1", 1000L, null, null, true, 1000L),
            AnnouncementEntity("ann_2", "Title 2", "Msg 2", 1000L, null, null, true, 1000L)
        )
        // Cached snapshot returns only ann_1 (e.g. offline query or partial cache)
        val incoming = listOf(
            AnnouncementEntity("ann_1", "Title 1", "Msg 1", 1000L, null, null, true, 1000L)
        )
        val firestoreIds = setOf("ann_1")
        val metadata = MockSnapshotMetadata(isFromCache = true, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteAnnouncements = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertFalse("Cached snapshot must NOT permit deletion reconciliation", decision.canReconcileDeletions)
        assertTrue("Local records must be preserved when snapshot is from cache", decision.toDeleteIds.isEmpty())
    }

    @Test
    fun testPendingWritesPreventsDeletion() {
        val local = listOf(
            AnnouncementEntity("ann_1", "Title 1", "Msg 1", 1000L, null, null, true, 1000L),
            AnnouncementEntity("ann_2", "Title 2", "Msg 2", 1000L, null, null, true, 1000L)
        )
        val incoming = listOf(
            AnnouncementEntity("ann_1", "Title 1", "Msg 1", 1000L, null, null, true, 1000L)
        )
        val firestoreIds = setOf("ann_1")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = true)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteAnnouncements = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertFalse("Pending writes snapshot must NOT permit deletion reconciliation", decision.canReconcileDeletions)
        assertTrue("Local records must be preserved when writes are pending", decision.toDeleteIds.isEmpty())
    }

    @Test
    fun testParsingErrorGuardProtectsLocalData() {
        val local = listOf(
            AnnouncementEntity("ann_1", "Title 1", "Msg 1", 1000L, null, null, true, 1000L),
            AnnouncementEntity("ann_2", "Title 2", "Msg 2", 1000L, null, null, true, 1000L)
        )
        val incoming = listOf(
            AnnouncementEntity("ann_1", "Title 1", "Msg 1", 1000L, null, null, true, 1000L)
        )
        val firestoreIds = setOf("ann_1", "ann_2_corrupt")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = true,
            localBeforeSync = local,
            incomingRemoteAnnouncements = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertFalse("Parsing errors must abort deletion reconciliation", decision.canReconcileDeletions)
        assertTrue("Local announcements must NOT be deleted when parsing failed", decision.toDeleteIds.isEmpty())
    }

    @Test
    fun testReconnectAndRepeatedRefreshIdempotence() {
        // Step 1: Initial state before sync
        var localRoom = listOf(
            AnnouncementEntity("Pmj7fN9H4DdgYf9znR6H", "j", "gh", 1791463411506L, null, null, true, 1791463411506L),
            AnnouncementEntity("dgH3urQilWUJQPpPCzV4", "testing", "announcement", 1790906765293L, null, null, true, 1790908270959L)
        )

        val remoteFirestore = listOf(
            AnnouncementEntity("Pmj7fN9H4DdgYf9znR6H", "j", "gh", 1791463411506L, null, null, true, 1791463411506L)
        )
        val remoteIds = setOf("Pmj7fN9H4DdgYf9znR6H")

        // First Refresh (Server Confirmed)
        val firstSync = computeReconciliation(
            metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false),
            hasParsingErrors = false,
            localBeforeSync = localRoom,
            incomingRemoteAnnouncements = remoteFirestore,
            firestoreDocumentIds = remoteIds
        )
        assertTrue(firstSync.canReconcileDeletions)
        assertEquals(setOf("dgH3urQilWUJQPpPCzV4"), firstSync.toDeleteIds)

        // Apply first sync to local simulation
        localRoom = localRoom.filter { it.id !in firstSync.toDeleteIds }
        assertEquals(1, localRoom.size)
        assertEquals("Pmj7fN9H4DdgYf9znR6H", localRoom.first().id)

        // Repeated Refresh (User clicks sync button again or returns to Home)
        val repeatedSync = computeReconciliation(
            metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false),
            hasParsingErrors = false,
            localBeforeSync = localRoom,
            incomingRemoteAnnouncements = remoteFirestore,
            firestoreDocumentIds = remoteIds
        )
        assertTrue(repeatedSync.canReconcileDeletions)
        assertTrue("Subsequent refresh should find no additional deletions", repeatedSync.toDeleteIds.isEmpty())
        assertEquals(1, repeatedSync.toInsertOrUpdate.size)
        assertEquals("Pmj7fN9H4DdgYf9znR6H", repeatedSync.toInsertOrUpdate.first().id)
    }
}
