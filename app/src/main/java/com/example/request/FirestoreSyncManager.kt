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

        syncServices()

        syncTransactions()

        syncRequirements()

        syncProcedureSteps()

        syncDepartmentGuidelines()

        syncAnnouncements()
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

    private suspend fun syncTransactions() {

        val snapshot =
            firestore
                .collection("transactions")
                .get()
                .await()

        val transactions =
            snapshot.documents.mapNotNull { document ->

                try {

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
    }


    // =====================================================
    // REQUIREMENTS
    // =====================================================

    private suspend fun syncRequirements() {

        val snapshot =
            firestore
                .collection("requirements")
                .get()
                .await()

        val changedTransactionIds =
            mutableSetOf<String>()

        val requirements =
            snapshot.documents.mapNotNull { document ->

                try {

                    val newRequirement =
                        RequirementEntity(
                            id = document.id,

                            transactionId =
                                document.getString(
                                    "transactionId"
                                ) ?: "",

                            requirementText =
                                document.getString(
                                    "requirementText"
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
                                    ?: document
                                        .getTimestamp(
                                            "updatedAt"
                                        )
                                        ?.toDate()
                                        ?.time
                                    ?: 0L
                        )


                    val oldRequirement =
                        dao.getRequirement(
                            newRequirement.id
                        )


                    if (oldRequirement != null) {

                        val hasChanged =
                            oldRequirement.requirementText !=
                                    newRequirement.requirementText ||

                                    oldRequirement.displayOrder !=
                                    newRequirement.displayOrder ||

                                    oldRequirement.isActive !=
                                    newRequirement.isActive ||

                                    oldRequirement.updatedAt !=
                                    newRequirement.updatedAt


                        if (
                            hasChanged &&
                            newRequirement.transactionId
                                .isNotBlank()
                        ) {

                            changedTransactionIds.add(
                                newRequirement.transactionId
                            )
                        }
                    }


                    newRequirement

                } catch (e: Exception) {

                    e.printStackTrace()

                    null
                }
            }


        if (requirements.isNotEmpty()) {

            dao.insertRequirements(
                requirements
            )
        }


        changedTransactionIds.forEach {
                transactionId ->

            createRequirementsUpdatedNotification(
                transactionId
            )
        }
    }


    // =====================================================
    // REQUIREMENTS UPDATED NOTIFICATION
    // =====================================================

    private suspend fun createRequirementsUpdatedNotification(
        transactionId: String
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
                "requirements_${transactionId}_$currentTime"


            val notification =
                UpdateNotificationEntity(

                    id =
                        notificationId,

                    serviceName =
                        serviceName,

                    transactionId =
                        transaction.id,

                    transactionName =
                        transaction.name,

                    updatedSection =
                        "requirements",

                    message =
                        "The requirements for this transaction have been updated.",

                    dateTime =
                        formattedDate,

                    isRead =
                        false,

                    createdAt =
                        currentTime
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

    private suspend fun syncProcedureSteps() {

        val snapshot =
            firestore
                .collection("procedure_steps")
                .get()
                .await()


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
                        dao.getProcedureStep(
                            stepId
                        )


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

        if (steps.isNotEmpty()) {

            dao.insertProcedureSteps(
                steps
            )
        }
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

    private suspend fun syncDepartmentGuidelines() {

        val snapshot =
            firestore
                .collection(
                    "department_guidelines"
                )
                .get()
                .await()


        val guidelines =
            snapshot.documents.mapNotNull {
                    document ->

                try {

                    DepartmentGuidelineEntity(

                        id =
                            document.id,

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
                                ?: document
                                    .getTimestamp(
                                        "updatedAt"
                                    )
                                    ?.toDate()
                                    ?.time
                                ?: 0L
                    )

                } catch (e: Exception) {

                    e.printStackTrace()

                    null
                }
            }


        if (guidelines.isNotEmpty()) {

            dao.insertDepartmentGuidelines(
                guidelines
            )
        }
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