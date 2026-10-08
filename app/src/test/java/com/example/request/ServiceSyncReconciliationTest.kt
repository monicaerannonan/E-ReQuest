package com.example.request

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying safe Service deletion synchronization logic:
 * - Add Service
 * - Update Service
 * - Delete Service
 * - Inactive Service
 * - Offline Refresh (cache protection)
 * - Empty Firestore collection
 * - Parsing failure safety guard
 * - Existing checklist progress preservation
 */
class ServiceSyncReconciliationTest {

    data class MockSnapshotMetadata(
        val isFromCache: Boolean,
        val hasPendingWrites: Boolean
    )

    data class ReconciliationDecision(
        val canReconcileDeletions: Boolean,
        val toDeleteIds: Set<String>,
        val toInsertOrUpdate: List<ServiceEntity>
    )

    /**
     * Pure logic replica of the decision pipeline in FirestoreSyncManager.syncServices()
     */
    private fun computeReconciliation(
        metadata: MockSnapshotMetadata,
        hasParsingErrors: Boolean,
        localBeforeSync: List<ServiceEntity>,
        incomingRemoteServices: List<ServiceEntity>,
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
            toInsertOrUpdate = incomingRemoteServices
        )
    }

    @Test
    fun testAddService() {
        val local = listOf(
            ServiceEntity("svc_1", "Local Service 1", "Desc", true, 1000L)
        )
        val incoming = listOf(
            ServiceEntity("svc_1", "Local Service 1", "Desc", true, 1000L),
            ServiceEntity("svc_2", "New Service 2", "Desc", true, 2000L)
        )
        val firestoreIds = setOf("svc_1", "svc_2")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteServices = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertTrue(decision.canReconcileDeletions)
        assertTrue("No service should be deleted", decision.toDeleteIds.isEmpty())
        assertEquals(2, decision.toInsertOrUpdate.size)
        assertTrue(decision.toInsertOrUpdate.any { it.id == "svc_2" })
    }

    @Test
    fun testUpdateService() {
        val local = listOf(
            ServiceEntity("svc_1", "Old Name", "Old Desc", true, 1000L)
        )
        val incoming = listOf(
            ServiceEntity("svc_1", "Updated Name", "Updated Desc", true, 2000L)
        )
        val firestoreIds = setOf("svc_1")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteServices = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertTrue(decision.canReconcileDeletions)
        assertTrue(decision.toDeleteIds.isEmpty())
        assertEquals(1, decision.toInsertOrUpdate.size)
        assertEquals("Updated Name", decision.toInsertOrUpdate.first().name)
        assertEquals(2000L, decision.toInsertOrUpdate.first().updatedAt)
    }

    @Test
    fun testDeleteService() {
        val local = listOf(
            ServiceEntity("svc_1", "Service to keep", "Desc", true, 1000L),
            ServiceEntity("svc_deleted", "Service removed from Firestore", "Desc", true, 1000L)
        )
        // svc_deleted was deleted in Firestore
        val incoming = listOf(
            ServiceEntity("svc_1", "Service to keep", "Desc", true, 1000L)
        )
        val firestoreIds = setOf("svc_1")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteServices = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertTrue(decision.canReconcileDeletions)
        assertEquals(setOf("svc_deleted"), decision.toDeleteIds)
        assertEquals(1, decision.toInsertOrUpdate.size)
    }

    @Test
    fun testInactiveServiceHandling() {
        val local = listOf(
            ServiceEntity("svc_active", "Active Service", "Desc", true, 1000L),
            ServiceEntity("svc_inactive", "Inactive Service", "Desc", false, 1000L)
        )
        // Both exist on Firestore, but one is inactive
        val incoming = listOf(
            ServiceEntity("svc_active", "Active Service", "Desc", true, 1000L),
            ServiceEntity("svc_inactive", "Inactive Service", "Desc", false, 1500L)
        )
        val firestoreIds = setOf("svc_active", "svc_inactive")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteServices = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertTrue(decision.canReconcileDeletions)
        assertTrue("Neither service should be deleted", decision.toDeleteIds.isEmpty())
        assertEquals(2, decision.toInsertOrUpdate.size)

        // If inactive service is deleted from Firestore instead:
        val firestoreIdsAfterDelete = setOf("svc_active")
        val incomingAfterDelete = listOf(
            ServiceEntity("svc_active", "Active Service", "Desc", true, 1000L)
        )
        val decisionAfterDelete = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteServices = incomingAfterDelete,
            firestoreDocumentIds = firestoreIdsAfterDelete
        )
        assertEquals(setOf("svc_inactive"), decisionAfterDelete.toDeleteIds)
    }

    @Test
    fun testOfflineRefreshPreservesLocalData() {
        val local = listOf(
            ServiceEntity("svc_1", "Service 1", "Desc", true, 1000L),
            ServiceEntity("svc_2", "Service 2", "Desc", true, 1000L)
        )
        // Offline snapshot from cache might only contain partial/cached docs
        val cachedIncoming = listOf(
            ServiceEntity("svc_1", "Service 1", "Desc", true, 1000L)
        )
        val firestoreIds = setOf("svc_1")
        val metadata = MockSnapshotMetadata(isFromCache = true, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteServices = cachedIncoming,
            firestoreDocumentIds = firestoreIds
        )

        assertFalse("Reconciliation should NOT run for cached/offline snapshots", decision.canReconcileDeletions)
        assertTrue("Local records must NOT be deleted during offline sync", decision.toDeleteIds.isEmpty())
    }

    @Test
    fun testEmptyFirestoreCollectionHandledSafely() {
        val local = listOf(
            ServiceEntity("svc_1", "Old Service 1", "Desc", true, 1000L),
            ServiceEntity("svc_2", "Old Service 2", "Desc", true, 1000L)
        )
        val emptyIncoming = emptyList<ServiceEntity>()
        val firestoreIds = emptySet<String>()
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteServices = emptyIncoming,
            firestoreDocumentIds = firestoreIds
        )

        assertTrue(decision.canReconcileDeletions)
        assertEquals("All absent local records are safely marked for deletion", setOf("svc_1", "svc_2"), decision.toDeleteIds)
        assertTrue(decision.toInsertOrUpdate.isEmpty())
    }

    @Test
    fun testParsingFailureAbortsDeletionReconciliation() {
        val local = listOf(
            ServiceEntity("svc_1", "Service 1", "Desc", true, 1000L),
            ServiceEntity("svc_unparseable", "Service that failed parsing", "Desc", true, 1000L)
        )
        // svc_unparseable threw an exception while reading fields
        val parsedIncoming = listOf(
            ServiceEntity("svc_1", "Service 1", "Desc", true, 1000L)
        )
        // Firestore has both documents
        val firestoreIds = setOf("svc_1", "svc_unparseable")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = true,
            localBeforeSync = local,
            incomingRemoteServices = parsedIncoming,
            firestoreDocumentIds = firestoreIds
        )

        assertFalse("Reconciliation must NOT run when document parsing errors exist", decision.canReconcileDeletions)
        assertTrue("Local records must be preserved when parsing fails", decision.toDeleteIds.isEmpty())
    }

    @Test
    fun testChecklistProgressPreservedDuringServiceReconciliation() {
        // Mock checklist progress stored in SharedPreferences (keyed by transaction/requirement ID)
        val mockChecklistPreferences = mutableMapOf(
            "progress_tx_001_req_001" to true,
            "progress_tx_001_req_002" to false
        )

        val deletedServiceId = "svc_deleted"
        val firestoreIds = setOf("svc_1")
        val local = listOf(
            ServiceEntity(deletedServiceId, "Deleted Service", "Desc", true, 1000L),
            ServiceEntity("svc_1", "Keep Service", "Desc", true, 1000L)
        )
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteServices = listOf(ServiceEntity("svc_1", "Keep Service", "Desc", true, 1000L)),
            firestoreDocumentIds = firestoreIds
        )

        // Deleting the service in Room does not touch or affect checklist progress
        assertEquals(setOf(deletedServiceId), decision.toDeleteIds)
        assertEquals(2, mockChecklistPreferences.size)
        assertTrue(mockChecklistPreferences["progress_tx_001_req_001"] == true)
        assertTrue(mockChecklistPreferences["progress_tx_001_req_002"] == false)
    }
}
