package com.example.request

import com.example.request.data.local.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying safe Transaction deletion, update, and cascade synchronization logic:
 * - Create / Add Transaction
 * - Update Transaction
 * - Delete Transaction (Server Confirmed)
 * - Offline Refresh (Cache Protection: no deletion when isFromCache = true)
 * - Pending Writes Protection (no deletion when hasPendingWrites = true)
 * - Parsing Failure Guard (no deletion when incoming document parsing fails)
 * - Cascade cleanup simulation (removing child requirements, steps, guidelines, and checklist progress)
 */
class TransactionSyncReconciliationTest {

    data class MockSnapshotMetadata(
        val isFromCache: Boolean,
        val hasPendingWrites: Boolean
    )

    data class ReconciliationDecision(
        val canReconcileDeletions: Boolean,
        val toDeleteIds: Set<String>,
        val toInsertOrUpdate: List<TransactionEntity>
    )

    /**
     * Pure logic replica of the decision pipeline in FirestoreSyncManager.syncTransactions()
     */
    private fun computeReconciliation(
        metadata: MockSnapshotMetadata,
        hasParsingErrors: Boolean,
        localBeforeSync: List<TransactionEntity>,
        incomingRemoteTransactions: List<TransactionEntity>,
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
            toInsertOrUpdate = incomingRemoteTransactions
        )
    }

    @Test
    fun testAddTransaction() {
        val local = listOf(
            TransactionEntity("tx_1", "svc_1", "Birth Certificate", "Desc", "Registrar", "8-5", true, 1000L)
        )
        val incoming = listOf(
            TransactionEntity("tx_1", "svc_1", "Birth Certificate", "Desc", "Registrar", "8-5", true, 1000L),
            TransactionEntity("tx_2", "svc_1", "Transcript of Records", "Desc", "Registrar", "8-5", true, 2000L)
        )
        val firestoreIds = setOf("tx_1", "tx_2")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteTransactions = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertTrue("Clean server snapshot must allow deletion reconciliation", decision.canReconcileDeletions)
        assertTrue("No transactions should be deleted", decision.toDeleteIds.isEmpty())
        assertEquals("Both transactions must be retained/inserted", 2, decision.toInsertOrUpdate.size)
        assertTrue(decision.toInsertOrUpdate.any { it.id == "tx_2" })
    }

    @Test
    fun testUpdateTransaction() {
        val local = listOf(
            TransactionEntity("tx_1", "svc_1", "Old Name", "Old Desc", "Registrar", "8-5", true, 1000L)
        )
        val incoming = listOf(
            TransactionEntity("tx_1", "svc_1", "Updated Name", "Updated Desc", "Registrar", "9-4", true, 2000L)
        )
        val firestoreIds = setOf("tx_1")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteTransactions = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertTrue(decision.canReconcileDeletions)
        assertTrue(decision.toDeleteIds.isEmpty())
        assertEquals(1, decision.toInsertOrUpdate.size)
        assertEquals("Updated Name", decision.toInsertOrUpdate.first().name)
        assertEquals("9-4", decision.toInsertOrUpdate.first().officeSchedule)
        assertEquals(2000L, decision.toInsertOrUpdate.first().updatedAt)
    }

    @Test
    fun testDeleteTransactionServerConfirmed() {
        val local = listOf(
            TransactionEntity("tx_1", "svc_1", "Transaction to keep", "Desc", "Registrar", "8-5", true, 1000L),
            TransactionEntity("tx_deleted", "svc_1", "Transaction removed in Firestore", "Desc", "Registrar", "8-5", true, 1000L)
        )
        // tx_deleted was deleted on Firestore
        val incoming = listOf(
            TransactionEntity("tx_1", "svc_1", "Transaction to keep", "Desc", "Registrar", "8-5", true, 1000L)
        )
        val firestoreIds = setOf("tx_1")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteTransactions = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertTrue("Server confirmed snapshot should enable deletion", decision.canReconcileDeletions)
        assertEquals("Deleted transaction ID must be marked for cascade deletion", setOf("tx_deleted"), decision.toDeleteIds)
        assertEquals(1, decision.toInsertOrUpdate.size)
        assertEquals("tx_1", decision.toInsertOrUpdate.first().id)
    }

    @Test
    fun testOfflineCacheProtectionPreventsTransactionDeletion() {
        val local = listOf(
            TransactionEntity("tx_1", "svc_1", "Tx 1", "Desc", "Registrar", "8-5", true, 1000L),
            TransactionEntity("tx_2", "svc_1", "Tx 2", "Desc", "Registrar", "8-5", true, 1000L)
        )
        // Offline cache returns partial list
        val cachedIncoming = listOf(
            TransactionEntity("tx_1", "svc_1", "Tx 1", "Desc", "Registrar", "8-5", true, 1000L)
        )
        val firestoreIds = setOf("tx_1")
        val metadata = MockSnapshotMetadata(isFromCache = true, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteTransactions = cachedIncoming,
            firestoreDocumentIds = firestoreIds
        )

        assertFalse("Reconciliation must NOT run for cached/offline snapshots", decision.canReconcileDeletions)
        assertTrue("Local records must NOT be deleted during offline sync", decision.toDeleteIds.isEmpty())
    }

    @Test
    fun testPendingWritesPreventsTransactionDeletion() {
        val local = listOf(
            TransactionEntity("tx_1", "svc_1", "Tx 1", "Desc", "Registrar", "8-5", true, 1000L),
            TransactionEntity("tx_2", "svc_1", "Tx 2", "Desc", "Registrar", "8-5", true, 1000L)
        )
        val incoming = listOf(
            TransactionEntity("tx_1", "svc_1", "Tx 1", "Desc", "Registrar", "8-5", true, 1000L)
        )
        val firestoreIds = setOf("tx_1")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = true)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = false,
            localBeforeSync = local,
            incomingRemoteTransactions = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertFalse("Pending writes snapshot must NOT permit deletion reconciliation", decision.canReconcileDeletions)
        assertTrue("Local records must be preserved when writes are pending", decision.toDeleteIds.isEmpty())
    }

    @Test
    fun testParsingErrorGuardProtectsLocalTransactions() {
        val local = listOf(
            TransactionEntity("tx_1", "svc_1", "Tx 1", "Desc", "Registrar", "8-5", true, 1000L),
            TransactionEntity("tx_2", "svc_1", "Tx 2", "Desc", "Registrar", "8-5", true, 1000L)
        )
        val incoming = listOf(
            TransactionEntity("tx_1", "svc_1", "Tx 1", "Desc", "Registrar", "8-5", true, 1000L)
        )
        val firestoreIds = setOf("tx_1", "tx_2_corrupted")
        val metadata = MockSnapshotMetadata(isFromCache = false, hasPendingWrites = false)

        val decision = computeReconciliation(
            metadata = metadata,
            hasParsingErrors = true,
            localBeforeSync = local,
            incomingRemoteTransactions = incoming,
            firestoreDocumentIds = firestoreIds
        )

        assertFalse("Parsing errors must abort deletion reconciliation", decision.canReconcileDeletions)
        assertTrue("Local transactions must NOT be deleted when parsing failed", decision.toDeleteIds.isEmpty())
    }

    @Test
    fun testCascadeCleanupSimulation() {
        val deletedTransactionId = "tx_deleted"

        // Mock database child records
        val mockRequirements = mutableListOf(
            RequirementEntity("req_1", deletedTransactionId, "Form 137", 1, true, 1000L),
            RequirementEntity("req_2", "tx_keep", "Valid ID", 1, true, 1000L)
        )
        val mockSteps = mutableListOf(
            ProcedureStepEntity("step_1", deletedTransactionId, 1, "Submit form", null, null, null, null, null, true, 1000L),
            ProcedureStepEntity("step_2", "tx_keep", 1, "Present ID", null, null, null, null, null, true, 1000L)
        )
        val mockGuidelines = mutableListOf(
            DepartmentGuidelineEntity("dg_1", deletedTransactionId, "Strict attire", 1, true, 1000L),
            DepartmentGuidelineEntity("dg_2", "tx_keep", "Office hours only", 1, true, 1000L)
        )
        val mockChecklistPreferences = mutableMapOf(
            "${deletedTransactionId}_requirement_req_1" to true,
            "tx_keep_requirement_req_2" to true
        )

        // Execute cascade delete simulation for deletedTransactionId
        mockRequirements.removeAll { it.transactionId == deletedTransactionId }
        mockSteps.removeAll { it.transactionId == deletedTransactionId }
        mockGuidelines.removeAll { it.transactionId == deletedTransactionId }

        val prefix = "${deletedTransactionId}_requirement_"
        val keysToRemove = mockChecklistPreferences.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { mockChecklistPreferences.remove(it) }

        // Assert child records and checklist progress for the deleted transaction were cleaned up
        assertEquals("Only remaining transaction's requirement should remain", 1, mockRequirements.size)
        assertEquals("req_2", mockRequirements.first().id)

        assertEquals("Only remaining transaction's step should remain", 1, mockSteps.size)
        assertEquals("step_2", mockSteps.first().id)

        assertEquals("Only remaining transaction's guideline should remain", 1, mockGuidelines.size)
        assertEquals("dg_2", mockGuidelines.first().id)

        assertEquals("Only remaining transaction's checklist progress should remain", 1, mockChecklistPreferences.size)
        assertTrue("Remaining transaction's progress must be preserved", mockChecklistPreferences.containsKey("tx_keep_requirement_req_2"))
        assertFalse("Deleted transaction's progress must be removed", mockChecklistPreferences.containsKey("${deletedTransactionId}_requirement_req_1"))
    }
}
