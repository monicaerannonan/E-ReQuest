package com.example.request.data.local

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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ERequestDao {

    // =====================================================
    // SERVICES
    // =====================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServices(
        services: List<ServiceEntity>
    )

    @Query("""
        SELECT * FROM services
        WHERE isActive = 1
        ORDER BY name ASC
    """)
    fun getAllServicesFlow(): Flow<List<ServiceEntity>>

    @Query("""
        SELECT * FROM services
        WHERE isActive = 1
        ORDER BY name ASC
    """)
    suspend fun getAllServices(): List<ServiceEntity>

    @Query("""
        SELECT * FROM services
        WHERE id = :serviceId
        LIMIT 1
    """)
    suspend fun getService(
        serviceId: String
    ): ServiceEntity?

    @Query("""
        SELECT * FROM services
    """)
    suspend fun getAllServicesForSync(): List<ServiceEntity>

    @Query("""
        DELETE FROM services
        WHERE id = :serviceId
    """)
    suspend fun deleteServiceById(
        serviceId: String
    )

    @Query("DELETE FROM services")
    suspend fun deleteAllServices()


    // =====================================================
    // TRANSACTIONS
    // =====================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(
        transactions: List<TransactionEntity>
    )

    @Query("""
        SELECT * FROM transactions
        WHERE serviceId = :serviceId
        AND isActive = 1
        ORDER BY name ASC
    """)
    fun getTransactionsByServiceFlow(
        serviceId: String
    ): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE serviceId = :serviceId
        AND isActive = 1
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
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE isActive = 1
        ORDER BY name ASC
    """)
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions
    """)
    suspend fun getAllTransactionsForSync(): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions
        WHERE id = :transactionId
        LIMIT 1
    """)
    fun getTransactionFlow(
        transactionId: String
    ): Flow<TransactionEntity?>

    @Query("""
        SELECT * FROM transactions
        WHERE id = :transactionId
        LIMIT 1
    """)
    suspend fun getTransaction(
        transactionId: String
    ): TransactionEntity?

    @Query("""
        DELETE FROM transactions
        WHERE id = :transactionId
    """)
    suspend fun deleteTransactionById(
        transactionId: String
    )

    @Query("""
        DELETE FROM transactions
        WHERE serviceId = :serviceId
    """)
    suspend fun deleteTransactionsByService(
        serviceId: String
    )

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()


    // =====================================================
    // REQUIREMENTS
    // =====================================================

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
    fun getRequirementsFlow(
        transactionId: String
    ): Flow<List<RequirementEntity>>

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
        SELECT * FROM requirements
        WHERE isActive = 1
        ORDER BY displayOrder ASC
    """)
    suspend fun getAllActiveRequirements(): List<RequirementEntity>

    @Query("""
        SELECT * FROM requirements
    """)
    suspend fun getAllRequirementsForSync(): List<RequirementEntity>

    @Query("""
        SELECT * FROM requirements
        WHERE id = :requirementId
        LIMIT 1
    """)
    suspend fun getRequirement(
        requirementId: String
    ): RequirementEntity?

    @Query("""
        DELETE FROM requirements
        WHERE transactionId = :transactionId
    """)
    suspend fun deleteRequirementsByTransaction(
        transactionId: String
    )

    @Query("""
        DELETE FROM requirements
        WHERE id = :requirementId
    """)
    suspend fun deleteRequirementById(
        requirementId: String
    )

    @Query("DELETE FROM requirements")
    suspend fun deleteAllRequirements()


    // =====================================================
    // PROCEDURE STEPS
    // =====================================================

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
    fun getProcedureStepsFlow(
        transactionId: String
    ): Flow<List<ProcedureStepEntity>>

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
        SELECT * FROM procedure_steps
    """)
    suspend fun getAllProcedureStepsForSync(): List<ProcedureStepEntity>

    @Query("""
        SELECT * FROM procedure_steps
        WHERE id = :stepId
        LIMIT 1
    """)
    suspend fun getProcedureStep(
        stepId: String
    ): ProcedureStepEntity?

    @Query("""
        DELETE FROM procedure_steps
        WHERE transactionId = :transactionId
    """)
    suspend fun deleteProcedureStepsByTransaction(
        transactionId: String
    )

    @Query("""
        DELETE FROM procedure_steps
        WHERE id = :stepId
    """)
    suspend fun deleteProcedureStepById(
        stepId: String
    )

    @Query("DELETE FROM procedure_steps")
    suspend fun deleteAllProcedureSteps()


    // =====================================================
    // DEPARTMENT GUIDELINES
    // =====================================================

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
        SELECT * FROM department_guidelines
    """)
    suspend fun getAllDepartmentGuidelinesForSync():
            List<DepartmentGuidelineEntity>

    @Query("""
        DELETE FROM department_guidelines
        WHERE transactionId = :transactionId
    """)
    suspend fun deleteDepartmentGuidelinesByTransaction(
        transactionId: String
    )

    @Query("""
        DELETE FROM department_guidelines
        WHERE id = :guidelineId
    """)
    suspend fun deleteDepartmentGuidelineById(
        guidelineId: String
    )

    @Query("DELETE FROM department_guidelines")
    suspend fun deleteAllDepartmentGuidelines()


    // =====================================================
    // IMPORTANT ANNOUNCEMENTS
    // =====================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncements(
        announcements: List<AnnouncementEntity>
    )

    @Query("""
        SELECT * FROM announcements
        WHERE isActive = 1
        ORDER BY datePosted DESC
    """)
    fun getAllActiveAnnouncementsFlow(): Flow<List<AnnouncementEntity>>

    @Query("""
        SELECT * FROM announcements
        WHERE isActive = 1
        ORDER BY datePosted DESC
    """)
    suspend fun getAllActiveAnnouncements():
            List<AnnouncementEntity>

    @Query("""
        SELECT * FROM announcements
        WHERE id = :announcementId
        LIMIT 1
    """)
    suspend fun getAnnouncement(
        announcementId: String
    ): AnnouncementEntity?

    @Query("""
        SELECT * FROM announcements
    """)
    suspend fun getAllAnnouncements():
            List<AnnouncementEntity>

    @Query("""
        DELETE FROM announcements
        WHERE id = :announcementId
    """)
    suspend fun deleteAnnouncementById(
        announcementId: String
    )

    @Query("DELETE FROM announcements")
    suspend fun deleteAllAnnouncements()


    // =====================================================
    // UPDATE NOTIFICATIONS
    // =====================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpdateNotification(
        notification: UpdateNotificationEntity
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpdateNotifications(
        notifications: List<UpdateNotificationEntity>
    )

    @Query("""
        SELECT * FROM update_notifications
        WHERE isRead = 0
        ORDER BY createdAt DESC
    """)
    fun getUnreadUpdateNotificationsFlow(): Flow<List<UpdateNotificationEntity>>

    @Query("""
        SELECT * FROM update_notifications
        WHERE isRead = 0
        ORDER BY createdAt DESC
    """)
    suspend fun getUnreadUpdateNotifications():
            List<UpdateNotificationEntity>

    @Query("""
        SELECT * FROM update_notifications
        WHERE isRead = 0
        AND isNotified = 0
        ORDER BY createdAt DESC
    """)
    suspend fun getUnnotifiedUpdateNotifications():
            List<UpdateNotificationEntity>

    @Query("""
        SELECT * FROM update_notifications
        WHERE transactionId = :transactionId
        AND isRead = 0
        ORDER BY createdAt DESC
        LIMIT 1
    """)
    suspend fun getUnreadNotificationByTransaction(
        transactionId: String
    ): UpdateNotificationEntity?

    @Query("""
        SELECT * FROM update_notifications
        ORDER BY createdAt DESC
    """)
    suspend fun getAllUpdateNotifications():
            List<UpdateNotificationEntity>

    @Query("""
        UPDATE update_notifications
        SET isRead = 1
        WHERE id = :notificationId
    """)
    suspend fun markUpdateNotificationAsRead(
        notificationId: String
    )

    @Query("""
        UPDATE update_notifications
        SET isNotified = 1
        WHERE id = :notificationId
    """)
    suspend fun markUpdateNotificationAsNotified(
        notificationId: String
    )

    @Query("""
        SELECT * FROM update_notifications
        WHERE id = :notificationId
        LIMIT 1
    """)
    suspend fun getUpdateNotification(
        notificationId: String
    ): UpdateNotificationEntity?

    @Query("""
        DELETE FROM update_notifications
        WHERE transactionId = :transactionId
    """)
    suspend fun deleteUpdateNotificationsByTransaction(
        transactionId: String
    )

    @Query("DELETE FROM update_notifications")
    suspend fun deleteAllUpdateNotifications()
}
