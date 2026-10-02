package com.example.request

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class FirestoreSyncManager(
    private val context: Context
) {

    private val firestore =
        FirebaseFirestore.getInstance()

    private val dao =
        ERequestDatabase
            .getDatabase(context.applicationContext)
            .eRequestDao()


    // =====================================================
    // SYNC ALL DATA
    // =====================================================

    suspend fun syncAllData() {

        // Sync services first so transaction notifications can
        // resolve the correct service name.
        syncServices()

        // Collect every transaction affected during this sync.
        // A MutableSet guarantees one notification per transaction,
        // even when several sections were changed.
        val affectedTransactionIds =
            mutableSetOf<String>()

        affectedTransactionIds.addAll(
            syncTransactions()
        )

        affectedTransactionIds.addAll(
            syncRequirements()
        )

        affectedTransactionIds.addAll(
            syncProcedureSteps()
        )

        affectedTransactionIds.addAll(
            syncDepartmentGuidelines()
        )

        syncAnnouncements()

        // Create exactly ONE unread notification for each
        // affected school transaction.
        affectedTransactionIds.forEach {
                transactionId ->

            createTransactionUpdateNotification(
                transactionId = transactionId,
                updatedSection = "transaction",
                message =
                    "Recent information for this transaction has been updated."
            )
        }
    }


    // =====================================================
    // SERVICES
    // =====================================================

    private suspend fun syncServices() {

        val snapshot =
            firestore
                .collection("services")
                .get()
                .await()

        val services =
            snapshot.documents.mapNotNull { document ->

                try {

                    ServiceEntity(
                        id = document.id,

                        name =
                            document.getString("name")
                                ?: "",

                        description =
                            document.getString("description")
                                ?: "",

                        isActive =
                            document.getBoolean("isActive")
                                ?: true,

                        updatedAt =
                            document.getLong("updatedAt")
                                ?: document
                                    .getTimestamp("updatedAt")
                                    ?.toDate()
                                    ?.time
                                ?: 0L
                    )

                } catch (e: Exception) {

                    e.printStackTrace()

                    null
                }
            }

        if (services.isNotEmpty()) {

            dao.insertServices(
                services
            )
        }
    }


    // =====================================================
    // TRANSACTIONS
    // =====================================================

    private suspend fun syncTransactions(): Set<String> {

        val snapshot =
            firestore
                .collection("transactions")
                .get()
                .await()

        val changedTransactionIds =
            mutableSetOf<String>()

        val transactions =
            snapshot.documents.mapNotNull { document ->

                try {

                    val newTransaction =
                        TransactionEntity(
                            id = document.id,

                            serviceId =
                                document.getString(
                                    "serviceId"
                                ) ?: "",

                            name =
                                document.getString(
                                    "name"
                                ) ?: "",

                            description =
                                document.getString(
                                    "description"
                                ) ?: "",

                            officeName =
                                document.getString(
                                    "officeName"
                                ) ?: "",

                            officeSchedule =
                                document.getString(
                                    "officeSchedule"
                                ) ?: "",

                            isActive =
                                document.getBoolean(
                                    "isActive"
                                ) ?: true,

                            updatedAt =
                                document.getLong(
                                    "updatedAt"
                                )
                                    ?: document
                                        .getTimestamp(
                                            "updatedAt"
                                        )
                                        ?.toDate()
                                        ?.time
                                    ?: 0L
                        )

                    val oldTransaction =
                        dao.getTransaction(
                            newTransaction.id
                        )

                    if (oldTransaction != null) {

                        val hasChanged =
                            oldTransaction.serviceId !=
                                    newTransaction.serviceId ||

                                    oldTransaction.name !=
                                    newTransaction.name ||

                                    oldTransaction.description !=
                                    newTransaction.description ||

                                    oldTransaction.officeName !=
                                    newTransaction.officeName ||

                                    oldTransaction.officeSchedule !=
                                    newTransaction.officeSchedule ||

                                    oldTransaction.isActive !=
                                    newTransaction.isActive ||

                                    oldTransaction.updatedAt !=
                                    newTransaction.updatedAt

                        if (hasChanged) {

                            changedTransactionIds.add(
                                newTransaction.id
                            )
                        }
                    }

                    newTransaction

                } catch (e: Exception) {

                    e.printStackTrace()

                    null
                }
            }

        if (transactions.isNotEmpty()) {

            dao.insertTransactions(
                transactions
            )
        }

        return changedTransactionIds
    }


    // =====================================================
    // REQUIREMENTS
    // =====================================================

    private suspend fun syncRequirements(): Set<String> {

        val localBeforeSync =
            dao.getAllRequirementsForSync()

        val hadLocalData =
            localBeforeSync.isNotEmpty()

        val localById =
            localBeforeSync.associateBy {
                it.id
            }

        val snapshot =
            firestore
                .collection("requirements")
                .get()
                .await()

        val firestoreIds =
            snapshot.documents
                .map { it.id }
                .toSet()

        val changedTransactionIds =
            mutableSetOf<String>()

        val requirements =
            snapshot.documents.mapNotNull { document ->

                try {

                    val newRequirement =
                        RequirementEntity(
                            id = document.id,
                            transactionId =
                                document.getString("transactionId") ?: "",
                            requirementText =
                                document.getString("requirementText") ?: "",
                            displayOrder =
                                document.getLong("displayOrder")?.toInt() ?: 0,
                            isActive =
                                document.getBoolean("isActive") ?: true,
                            updatedAt =
                                document.getLong("updatedAt")
                                    ?: document.getTimestamp("updatedAt")
                                        ?.toDate()?.time
                                    ?: 0L
                        )

                    val oldRequirement =
                        localById[newRequirement.id]

                    if (hadLocalData) {

                        val isAdded =
                            oldRequirement == null

                        val isChanged =
                            oldRequirement != null &&
                                    (
                                            oldRequirement.transactionId !=
                                                    newRequirement.transactionId ||
                                                    oldRequirement.requirementText !=
                                                    newRequirement.requirementText ||
                                                    oldRequirement.displayOrder !=
                                                    newRequirement.displayOrder ||
                                                    oldRequirement.isActive !=
                                                    newRequirement.isActive ||
                                                    oldRequirement.updatedAt !=
                                                    newRequirement.updatedAt
                                            )

                        if (isAdded || isChanged) {

                            if (
                                oldRequirement != null &&
                                oldRequirement.transactionId.isNotBlank()
                            ) {
                                changedTransactionIds.add(
                                    oldRequirement.transactionId
                                )
                            }

                            if (
                                newRequirement.transactionId.isNotBlank()
                            ) {
                                changedTransactionIds.add(
                                    newRequirement.transactionId
                                )
                            }
                        }
                    }

                    newRequirement

                } catch (e: Exception) {

                    e.printStackTrace()
                    null
                }
            }

        if (hadLocalData) {

            localBeforeSync
                .filter {
                    it.id !in firestoreIds
                }
                .forEach { deletedRequirement ->

                    if (
                        deletedRequirement.transactionId
                            .isNotBlank()
                    ) {
                        changedTransactionIds.add(
                            deletedRequirement.transactionId
                        )
                    }

                    dao.deleteRequirementById(
                        deletedRequirement.id
                    )
                }
        }

        if (requirements.isNotEmpty()) {

            dao.insertRequirements(
                requirements
            )
        }

        return changedTransactionIds
    }


    // =====================================================
    // REQUIREMENTS UPDATED NOTIFICATION
    // =====================================================

    private suspend fun createRequirementsUpdatedNotification(
        transactionId: String
    ) {

        createTransactionUpdateNotification(
            transactionId = transactionId,
            updatedSection = "requirements",
            message =
                "The requirements for this transaction have been updated."
        )
    }


    // =====================================================
    // GENERIC TRANSACTION UPDATE NOTIFICATION
    // =====================================================

    private suspend fun createTransactionUpdateNotification(
        transactionId: String,
        updatedSection: String,
        message: String
    ) {

        try {

            val transaction =
                dao.getTransaction(
                    transactionId
                ) ?: return

            val service =
                dao.getService(
                    transaction.serviceId
                )

            val serviceName =
                service?.name
                    ?: transaction.officeName

            val currentTime =
                System.currentTimeMillis()

            val formattedDate =
                SimpleDateFormat(
                    "MMM dd, yyyy • hh:mm a",
                    Locale.getDefault()
                ).format(
                    Date(currentTime)
                )

            val notificationId =
                "${updatedSection}_${transactionId}_$currentTime"

            val notification =
                UpdateNotificationEntity(
                    id = notificationId,
                    serviceName = serviceName,
                    transactionId = transaction.id,
                    transactionName = transaction.name,
                    updatedSection = updatedSection,
                    message = message,
                    dateTime = formattedDate,
                    isRead = false,
                    createdAt = currentTime
                )

            dao.insertUpdateNotification(
                notification
            )

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }


    // =====================================================
    // PROCEDURE STEPS
    // =====================================================

    private suspend fun syncProcedureSteps(): Set<String> {

        val localBeforeSync =
            dao.getAllProcedureStepsForSync()

        val hadLocalData =
            localBeforeSync.isNotEmpty()

        val localById =
            localBeforeSync.associateBy {
                it.id
            }

        val snapshot =
            firestore
                .collection("procedure_steps")
                .get()
                .await()

        val firestoreIds =
            snapshot.documents
                .map { it.id }
                .toSet()

        val changedTransactionIds =
            mutableSetOf<String>()


        val steps =
            snapshot.documents.mapNotNull { document ->

                try {

                    // -----------------------------------------
                    // DATA FROM FIRESTORE
                    // -----------------------------------------

                    val stepId =
                        document.id


                    val transactionId =
                        document.getString(
                            "transactionId"
                        ) ?: ""


                    val stepNumber =
                        document.getLong(
                            "stepNumber"
                        )?.toInt() ?: 0


                    val instruction =
                        document.getString(
                            "instruction"
                        ) ?: ""


                    val remoteImageUrl =
                        document.getString(
                            "remoteImageUrl"
                        )
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }


                    val imageCaption =
                        document.getString(
                            "imageCaption"
                        )
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }


                    // -----------------------------------------
                    // OPTIONAL WEBSITE LINK
                    // -----------------------------------------

                    val websiteName =
                        document.getString(
                            "websiteName"
                        )
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }


                    val websiteUrl =
                        document.getString(
                            "websiteUrl"
                        )
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }


                    val isActive =
                        document.getBoolean(
                            "isActive"
                        ) ?: true


                    val updatedAt =
                        document.getLong(
                            "updatedAt"
                        )
                            ?: document
                                .getTimestamp(
                                    "updatedAt"
                                )
                                ?.toDate()
                                ?.time
                            ?: 0L


                    // -----------------------------------------
                    // GET OLD LOCAL VERSION FROM ROOM
                    // -----------------------------------------

                    val oldStep =
                        localById[stepId]


                    // -----------------------------------------
                    // DETERMINE LOCAL OFFLINE IMAGE
                    // -----------------------------------------

                    var localImagePath =
                        oldStep?.localImagePath


                    val existingLocalFile =
                        localImagePath
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let {
                                File(it)
                            }


                    val localFileExists =
                        existingLocalFile
                            ?.exists()
                            ?: false


                    // -----------------------------------------
                    // NO REMOTE IMAGE
                    // -----------------------------------------

                    if (remoteImageUrl == null) {

                        if (localFileExists) {

                            try {

                                existingLocalFile?.delete()

                            } catch (e: Exception) {

                                e.printStackTrace()
                            }
                        }


                        localImagePath =
                            null

                    } else {

                        // -------------------------------------
                        // REMOTE IMAGE EXISTS
                        // -------------------------------------

                        val remoteImageChanged =
                            oldStep?.remoteImageUrl !=
                                    remoteImageUrl


                        val needsDownload =
                            oldStep == null ||
                                    remoteImageChanged ||
                                    !localFileExists


                        if (needsDownload) {

                            val downloadedPath =
                                downloadProcedureImage(
                                    stepId =
                                        stepId,

                                    remoteImageUrl =
                                        remoteImageUrl
                                )


                            if (downloadedPath != null) {

                                if (
                                    localFileExists &&
                                    existingLocalFile != null &&
                                    existingLocalFile.absolutePath !=
                                    downloadedPath
                                ) {

                                    try {

                                        existingLocalFile.delete()

                                    } catch (e: Exception) {

                                        e.printStackTrace()
                                    }
                                }


                                localImagePath =
                                    downloadedPath

                            } else {

                                localImagePath =
                                    if (
                                        !remoteImageChanged &&
                                        localFileExists
                                    ) {

                                        existingLocalFile
                                            ?.absolutePath

                                    } else {

                                        null
                                    }
                            }
                        }
                    }


                    // -----------------------------------------
                    // DETECT PROCEDURE CHANGES
                    // -----------------------------------------

                    if (hadLocalData) {

                        val isAdded =
                            oldStep == null

                        val hasChanged =
                            oldStep != null && (
                                    oldStep.transactionId != transactionId ||
                                            oldStep.stepNumber != stepNumber ||
                                            oldStep.instruction != instruction ||
                                            oldStep.remoteImageUrl != remoteImageUrl ||
                                            oldStep.imageCaption != imageCaption ||
                                            oldStep.websiteName != websiteName ||
                                            oldStep.websiteUrl != websiteUrl ||
                                            oldStep.isActive != isActive ||
                                            oldStep.updatedAt != updatedAt
                                    )

                        if (isAdded || hasChanged) {

                            if (
                                oldStep != null &&
                                oldStep.transactionId.isNotBlank()
                            ) {
                                changedTransactionIds.add(
                                    oldStep.transactionId
                                )
                            }

                            if (transactionId.isNotBlank()) {
                                changedTransactionIds.add(
                                    transactionId
                                )
                            }
                        }
                    }


                    // -----------------------------------------
                    // CREATE ROOM ENTITY
                    // -----------------------------------------

                    ProcedureStepEntity(

                        id =
                            stepId,

                        transactionId =
                            transactionId,

                        stepNumber =
                            stepNumber,

                        instruction =
                            instruction,

                        remoteImageUrl =
                            remoteImageUrl,

                        localImagePath =
                            localImagePath,

                        imageCaption =
                            imageCaption,

                        websiteName =
                            websiteName,

                        websiteUrl =
                            websiteUrl,

                        isActive =
                            isActive,

                        updatedAt =
                            updatedAt
                    )

                } catch (e: Exception) {

                    e.printStackTrace()

                    null
                }
            }


        // ---------------------------------------------
        // SAVE PROCEDURE STEPS TO ROOM
        // ---------------------------------------------

        if (hadLocalData) {

            localBeforeSync
                .filter {
                    it.id !in firestoreIds
                }
                .forEach { deletedStep ->

                    if (
                        deletedStep.transactionId
                            .isNotBlank()
                    ) {
                        changedTransactionIds.add(
                            deletedStep.transactionId
                        )
                    }

                    deletedStep.localImagePath
                        ?.takeIf { it.isNotBlank() }
                        ?.let { path ->
                            try {
                                File(path).delete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                    dao.deleteProcedureStepById(
                        deletedStep.id
                    )
                }
        }

        if (steps.isNotEmpty()) {

            dao.insertProcedureSteps(
                steps
            )
        }

        return changedTransactionIds
    }


    // =====================================================
    // DOWNLOAD PROCEDURE IMAGE FOR OFFLINE USE
    // =====================================================

    private suspend fun downloadProcedureImage(
        stepId: String,
        remoteImageUrl: String
    ): String? {

        return withContext(
            Dispatchers.IO
        ) {

            var connection:
                    HttpURLConnection? = null


            try {

                // -----------------------------------------
                // CREATE INTERNAL IMAGE FOLDER
                // -----------------------------------------

                val imageDirectory =
                    File(
                        context.filesDir,
                        "procedure_images"
                    )


                if (!imageDirectory.exists()) {

                    imageDirectory.mkdirs()
                }


                // -----------------------------------------
                // SAFE FILE NAME
                // -----------------------------------------

                val safeStepId =
                    stepId.replace(
                        Regex(
                            "[^A-Za-z0-9_-]"
                        ),
                        "_"
                    )


                val imageFile =
                    File(
                        imageDirectory,
                        "${safeStepId}_${System.currentTimeMillis()}.img"
                    )


                // -----------------------------------------
                // CONNECT TO CLOUDINARY
                // -----------------------------------------

                val url =
                    URL(
                        remoteImageUrl
                    )


                connection =
                    url.openConnection()
                            as HttpURLConnection


                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    15000

                connection.readTimeout =
                    20000

                connection.instanceFollowRedirects =
                    true

                connection.doInput =
                    true


                connection.connect()


                // -----------------------------------------
                // CHECK RESPONSE
                // -----------------------------------------

                if (
                    connection.responseCode !in
                    200..299
                ) {

                    return@withContext null
                }


                // -----------------------------------------
                // SAVE IMAGE TO INTERNAL STORAGE
                // -----------------------------------------

                connection.inputStream.use {
                        inputStream ->

                    imageFile.outputStream().use {
                            outputStream ->

                        inputStream.copyTo(
                            outputStream
                        )
                    }
                }


                // -----------------------------------------
                // VERIFY FILE
                // -----------------------------------------

                if (
                    !imageFile.exists() ||
                    imageFile.length() <= 0L
                ) {

                    imageFile.delete()

                    return@withContext null
                }


                imageFile.absolutePath


            } catch (e: Exception) {

                e.printStackTrace()

                null


            } finally {

                connection?.disconnect()
            }
        }
    }


    // =====================================================
    // DEPARTMENT GUIDELINES
    // =====================================================

    private suspend fun syncDepartmentGuidelines(): Set<String> {

        val localBeforeSync =
            dao.getAllDepartmentGuidelinesForSync()

        val hadLocalData =
            localBeforeSync.isNotEmpty()

        val localById =
            localBeforeSync.associateBy {
                it.id
            }

        val snapshot =
            firestore
                .collection(
                    "department_guidelines"
                )
                .get()
                .await()

        val firestoreIds =
            snapshot.documents
                .map { it.id }
                .toSet()

        val changedTransactionIds =
            mutableSetOf<String>()

        val guidelines =
            snapshot.documents.mapNotNull {
                    document ->

                try {

                    val newGuideline =
                        DepartmentGuidelineEntity(
                            id = document.id,
                            transactionId =
                                document.getString(
                                    "transactionId"
                                ) ?: "",
                            guidelineText =
                                document.getString(
                                    "guidelineText"
                                ) ?: "",
                            displayOrder =
                                document.getLong(
                                    "displayOrder"
                                )?.toInt() ?: 0,
                            isActive =
                                document.getBoolean(
                                    "isActive"
                                ) ?: true,
                            updatedAt =
                                document.getLong(
                                    "updatedAt"
                                )
                                    ?: document.getTimestamp(
                                        "updatedAt"
                                    )?.toDate()?.time
                                    ?: 0L
                        )

                    val oldGuideline =
                        localById[newGuideline.id]

                    if (hadLocalData) {

                        val isAdded =
                            oldGuideline == null

                        val isChanged =
                            oldGuideline != null &&
                                    (
                                            oldGuideline.transactionId !=
                                                    newGuideline.transactionId ||
                                                    oldGuideline.guidelineText !=
                                                    newGuideline.guidelineText ||
                                                    oldGuideline.displayOrder !=
                                                    newGuideline.displayOrder ||
                                                    oldGuideline.isActive !=
                                                    newGuideline.isActive ||
                                                    oldGuideline.updatedAt !=
                                                    newGuideline.updatedAt
                                            )

                        if (isAdded || isChanged) {

                            if (
                                oldGuideline != null &&
                                oldGuideline.transactionId
                                    .isNotBlank()
                            ) {
                                changedTransactionIds.add(
                                    oldGuideline.transactionId
                                )
                            }

                            if (
                                newGuideline.transactionId
                                    .isNotBlank()
                            ) {
                                changedTransactionIds.add(
                                    newGuideline.transactionId
                                )
                            }
                        }
                    }

                    newGuideline

                } catch (e: Exception) {

                    e.printStackTrace()
                    null
                }
            }

        if (hadLocalData) {

            localBeforeSync
                .filter {
                    it.id !in firestoreIds
                }
                .forEach { deletedGuideline ->

                    if (
                        deletedGuideline.transactionId
                            .isNotBlank()
                    ) {
                        changedTransactionIds.add(
                            deletedGuideline.transactionId
                        )
                    }

                    dao.deleteDepartmentGuidelineById(
                        deletedGuideline.id
                    )
                }
        }

        if (guidelines.isNotEmpty()) {

            dao.insertDepartmentGuidelines(
                guidelines
            )
        }

        return changedTransactionIds
    }


    // =====================================================
    // IMPORTANT ANNOUNCEMENTS
    // =====================================================

    private suspend fun syncAnnouncements() {

        val snapshot =
            firestore
                .collection("announcements")
                .get()
                .await()

        // Get IDs that currently exist in Firestore.
        val firestoreAnnouncementIds =
            snapshot.documents
                .map { document ->
                    document.id
                }
                .toSet()

        // Get all local announcements, including inactive records.
        val localAnnouncements =
            dao.getAllAnnouncements()

        // Remove Room records that were permanently deleted in Firestore.
        localAnnouncements.forEach { localAnnouncement ->

            if (
                localAnnouncement.id !in
                firestoreAnnouncementIds
            ) {

                dao.deleteAnnouncementById(
                    localAnnouncement.id
                )
            }
        }

        // Convert current Firestore records to Room entities.
        val announcements =
            snapshot.documents.mapNotNull { document ->

                try {

                    val datePosted =
                        document
                            .getTimestamp("datePosted")
                            ?.toDate()
                            ?.time
                            ?: document.getLong("datePosted")
                            ?: System.currentTimeMillis()

                    val updatedAt =
                        document
                            .getTimestamp("updatedAt")
                            ?.toDate()
                            ?.time
                            ?: document.getLong("updatedAt")
                            ?: datePosted

                    val startDate =
                        document
                            .getTimestamp("startDate")
                            ?.toDate()
                            ?.time
                            ?: document.getLong("startDate")

                    val endDate =
                        document
                            .getTimestamp("endDate")
                            ?.toDate()
                            ?.time
                            ?: document.getLong("endDate")

                    AnnouncementEntity(
                        id = document.id,

                        title =
                            document.getString("title")
                                ?: "",

                        message =
                            document.getString("message")
                                ?: "",

                        datePosted =
                            datePosted,

                        startDate =
                            startDate,

                        endDate =
                            endDate,

                        isActive =
                            document.getBoolean("isActive")
                                ?: true,

                        updatedAt =
                            updatedAt
                    )

                } catch (e: Exception) {

                    e.printStackTrace()

                    null
                }
            }

        // Insert/update records that still exist in Firestore.
        if (announcements.isNotEmpty()) {

            dao.insertAnnouncements(
                announcements
            )
        }
    }
}