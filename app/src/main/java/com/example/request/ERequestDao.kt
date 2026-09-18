package com.example.request

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ERequestDao {

    // SERVICES
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServices(services: List<ServiceEntity>)

    @Query("SELECT * FROM services WHERE isActive = 1 ORDER BY name ASC")
    suspend fun getAllServices(): List<ServiceEntity>

    @Query("SELECT * FROM services WHERE id = :serviceId LIMIT 1")
    suspend fun getService(serviceId: String): ServiceEntity?

    @Query("DELETE FROM services")
    suspend fun deleteAllServices()

    // TRANSACTIONS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Query("""
        SELECT * FROM transactions
        WHERE serviceId = :serviceId AND isActive = 1
        ORDER BY name ASC
    """)
    suspend fun getTransactionsByService(
        serviceId: String
    ): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions
        WHERE isActive = 1
        ORDER BY name ASC
    """)
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions
        WHERE id = :transactionId
        LIMIT 1
    """)
    suspend fun getTransaction(
        transactionId: String
    ): TransactionEntity?

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    // REQUIREMENTS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequirements(
        requirements: List<RequirementEntity>
    )

    @Query("""
        SELECT * FROM requirements
        WHERE transactionId = :transactionId
        AND isActive = 1
        ORDER BY displayOrder ASC
    """)
    suspend fun getRequirements(
        transactionId: String
    ): List<RequirementEntity>

    @Query("""
        DELETE FROM requirements
        WHERE transactionId = :transactionId
    """)
    suspend fun deleteRequirementsByTransaction(
        transactionId: String
    )

    @Query("DELETE FROM requirements")
    suspend fun deleteAllRequirements()

    // PROCEDURE STEPS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcedureSteps(
        steps: List<ProcedureStepEntity>
    )

    @Query("""
        SELECT * FROM procedure_steps
        WHERE transactionId = :transactionId
        AND isActive = 1
        ORDER BY stepNumber ASC
    """)
    suspend fun getProcedureSteps(
        transactionId: String
    ): List<ProcedureStepEntity>

    @Query("""
        DELETE FROM procedure_steps
        WHERE transactionId = :transactionId
    """)
    suspend fun deleteProcedureStepsByTransaction(
        transactionId: String
    )

    @Query("DELETE FROM procedure_steps")
    suspend fun deleteAllProcedureSteps()

    // DEPARTMENT GUIDELINES
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDepartmentGuidelines(
        guidelines: List<DepartmentGuidelineEntity>
    )

    @Query("""
        SELECT * FROM department_guidelines
        WHERE transactionId = :transactionId
        AND isActive = 1
        ORDER BY displayOrder ASC
    """)
    suspend fun getDepartmentGuidelines(
        transactionId: String
    ): List<DepartmentGuidelineEntity>

    @Query("""
        DELETE FROM department_guidelines
        WHERE transactionId = :transactionId
    """)
    suspend fun deleteDepartmentGuidelinesByTransaction(
        transactionId: String
    )

    @Query("DELETE FROM department_guidelines")
    suspend fun deleteAllDepartmentGuidelines()

    // IMPORTANT ANNOUNCEMENTS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncements(
        announcements: List<AnnouncementEntity>
    )

    @Query("""
        SELECT * FROM announcements
        WHERE isActive = 1
        ORDER BY datePosted DESC
    """)
    suspend fun getAllActiveAnnouncements(): List<AnnouncementEntity>

    @Query("""
        SELECT * FROM announcements
        WHERE id = :announcementId
        LIMIT 1
    """)
    suspend fun getAnnouncement(
        announcementId: String
    ): AnnouncementEntity?

    @Query("DELETE FROM announcements")
    suspend fun deleteAllAnnouncements()
}