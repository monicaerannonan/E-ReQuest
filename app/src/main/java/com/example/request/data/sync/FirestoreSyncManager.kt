package com.example.request.data.sync

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

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean


class FirestoreSyncManager(
    private val context: Context
) {

    companion object {
        private val syncMutex = Mutex()
        private val isSyncInProgress = AtomicBoolean(false)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val syncCompletionListeners = CopyOnWriteArraySet<() -> Unit>()

        @Volatile
        private var lastSyncCompletedAtMillis: Long = 0L

        private const val SYNC_PREFS_NAME = "ERequestSyncPrefs"
        private const val KEY_PENDING_SYNC = "pending_sync_required"

        fun isCurrentlySyncing(): Boolean = isSyncInProgress.get()

        fun wasSyncedRecently(cooldownMs: Long = 3000L): Boolean {
            val elapsed = System.currentTimeMillis() - lastSyncCompletedAtMillis
            return lastSyncCompletedAtMillis > 0L && elapsed in 0..cooldownMs
        }

        fun registerSyncCompletionListener(listener: () -> Unit) {
            syncCompletionListeners.add(listener)
        }

        fun unregisterSyncCompletionListener(listener: () -> Unit) {
            syncCompletionListeners.remove(listener)
        }

        private fun notifySyncCompleted() {
            mainHandler.post {
                syncCompletionListeners.forEach { listener ->
                    try {
                        listener.invoke()
                    } catch (e: Exception) {
                        Log.e("FirestoreSyncManager", "Error in sync completion listener", e)
                    }
                }
            }
        }

        fun setPendingSync(context: Context, isPending: Boolean) {
            context.applicationContext
                .getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PENDING_SYNC, isPending)
                .apply()
        }

        fun hasPendingSync(context: Context): Boolean {
            return context.applicationContext
                .getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PENDING_SYNC, false)
        }
    }

    private val firestore =
        FirebaseFirestore.getInstance()

    private val database =
        ERequestDatabase
            .getDatabase(context.applicationContext)

    private val dao =
        database.eRequestDao()

    private val parsingErrors =
        mutableListOf<String>()

    private fun recordParsingError(collection: String, docId: String, e: Exception) {
        val msg = "Failed to parse document '$docId' in '$collection': ${e.message}"
        Log.e("FirestoreSyncManager", msg, e)
        parsingErrors.add(msg)
    }

    private fun readTimestampMillis(
        document: DocumentSnapshot,
        field: String
    ): Long? {
        return when (val value = document.get(field)) {
            is Number -> value.toLong()
            is com.google.firebase.Timestamp ->
                value.toDate().time
            is java.util.Date -> value.time
            else -> {
                if (document.contains(field) && value != null) {
                    Log.w(
                        "FirestoreSyncManager",
                        "Unrecognized timestamp format for field '$field' in document '${document.id}': ${value::class.java.name}"
                    )
                }
                null
            }
        }
    }


    // =====================================================
    // SYNC ALL DATA
    // =====================================================

    suspend fun syncAllData() {
        // If another sync is currently running, wait for it to finish writing to Room
        // instead of returning prematurely before Room has the updated notifications.
        if (!isSyncInProgress.compareAndSet(false, true)) {
            Log.d("FirestoreSyncManager", "Sync already in progress; awaiting completion of active sync.")
            syncMutex.withLock {
                // Active sync has now completed and Room is up to date.
            }
            return
        }

        try {
            syncMutex.withLock {
                parsingErrors.clear()

                val changedTransactionInfoIds = mutableSetOf<String>()
                val changedRequirementTxIds = mutableSetOf<String>()
                val changedProcedureTxIds = mutableSetOf<String>()
                val changedGuidelineTxIds = mutableSetOf<String>()

                val syncErrors = mutableListOf<Throwable>()

                runCatching {
                    syncServices()
                }.onFailure { e ->
                    Log.e("FirestoreSyncManager", "syncServices failed: ${e.message}", e)
                    syncErrors.add(e)
                }

                runCatching {
                    changedTransactionInfoIds.addAll(syncTransactions())
                }.onFailure { e ->
                    Log.e("FirestoreSyncManager", "syncTransactions failed: ${e.message}", e)
                    syncErrors.add(e)
                }

                runCatching {
                    changedRequirementTxIds.addAll(syncRequirements())
                }.onFailure { e ->
                    Log.e("FirestoreSyncManager", "syncRequirements failed: ${e.message}", e)
                    syncErrors.add(e)
                }

                runCatching {
                    changedProcedureTxIds.addAll(syncProcedureSteps())
                }.onFailure { e ->
                    Log.e("FirestoreSyncManager", "syncProcedureSteps failed: ${e.message}", e)
                    syncErrors.add(e)
                }

                runCatching {
                    changedGuidelineTxIds.addAll(syncDepartmentGuidelines())
                }.onFailure { e ->
                    Log.e("FirestoreSyncManager", "syncDepartmentGuidelines failed: ${e.message}", e)
                    syncErrors.add(e)
                }

                runCatching {
                    syncAnnouncements()
                }.onFailure { e ->
                    Log.e("FirestoreSyncManager", "syncAnnouncements failed: ${e.message}", e)
                    syncErrors.add(e)
                }

                val affectedTransactionIds = mutableSetOf<String>().apply {
                    addAll(changedTransactionInfoIds)
                    addAll(changedRequirementTxIds)
                    addAll(changedProcedureTxIds)
                    addAll(changedGuidelineTxIds)
                }

                // Create or update a single deduplicated unread notification
                // for each affected school transaction.
                affectedTransactionIds.forEach { transactionId ->
                    val inTx = transactionId in changedTransactionInfoIds
                    val inReq = transactionId in changedRequirementTxIds
                    val inProc = transactionId in changedProcedureTxIds
                    val inGuide = transactionId in changedGuidelineTxIds

                    val sectionCount = listOf(inTx, inReq, inProc, inGuide).count { it }

                    val (targetSection, updateMessage) = when {
                        sectionCount > 1 -> "ABOUT" to "Recent information for this transaction has been updated."
                        inReq -> "REQUIREMENTS" to "The requirements for this transaction have been updated."
                        inProc -> "PROCEDURE" to "The step-by-step procedure for this transaction has been updated."
                        inGuide -> "DEPARTMENT_GUIDELINES" to "The department guidelines for this transaction have been updated."
                        else -> "ABOUT" to "Recent information for this transaction has been updated."
                    }

                    createTransactionUpdateNotification(
                        transactionId = transactionId,
                        updatedSection = targetSection,
                        message = updateMessage
                    )
                }

                // Dispatch local NotificationCompat alerts once for any unnotified updates
                // and persist isNotified = 1 in Room without clearing isRead = 0.
                NotificationHelper(context).dispatchUnnotifiedUpdates(dao)

                if (parsingErrors.isNotEmpty()) {
                    setPendingSync(context, true)
                    throw IllegalStateException(
                        "Synchronization completed with ${parsingErrors.size} document parsing error(s):\n" +
                                parsingErrors.joinToString("\n")
                    )
                }

                if (syncErrors.isNotEmpty()) {
                    setPendingSync(context, true)
                    throw IllegalStateException(
                        "Synchronization encountered ${syncErrors.size} step error(s):\n" +
                                syncErrors.joinToString("\n") { it.message ?: it.javaClass.simpleName }
                    )
                }

                setPendingSync(context, false)
                lastSyncCompletedAtMillis = System.currentTimeMillis()
            }
        } finally {
            isSyncInProgress.set(false)
            notifySyncCompleted()
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

        val isFromCache = snapshot.metadata.isFromCache
        val hasPendingWrites = snapshot.metadata.hasPendingWrites()
        val isServerConfirmed = !isFromCache && !hasPendingWrites

        // 1. Read ALL local services from Room before syncing (including inactive)
        val localBeforeSync = dao.getAllServicesForSync()

        // 2. Parse incoming documents, tracking if any parsing errors occurred
        var hasParsingErrors = false

        val services =
            snapshot.documents.mapNotNull { document ->

                try {

                    val updatedAt = readTimestampMillis(document, "updatedAt")
                    if (updatedAt == null) {
                        Log.w("FirestoreSyncManager", "Missing or unparseable updatedAt timestamp in service '${document.id}'")
                    }

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
                            updatedAt ?: 0L
                    )

                } catch (e: Exception) {

                    hasParsingErrors = true
                    Log.e("FirestoreSyncManager", "Parsing failed for service '${document.id}': ${e.message}", e)

                    recordParsingError("services", document.id, e)

                    null
                }
            }

        // 3. Determine if safe to reconcile deletions
        // Must be server-confirmed (not cached or unverified) and free of parsing errors
        val canReconcileDeletions = isServerConfirmed && !hasParsingErrors

        val firestoreIds = snapshot.documents.map { it.id }.toSet()
        val servicesToDelete = localBeforeSync.filter { it.id !in firestoreIds }

        if (!isServerConfirmed) {
            Log.w("FirestoreSyncManager", "Skipping service deletion reconciliation: snapshot is cached or has pending writes (isFromCache=$isFromCache, hasPendingWrites=$hasPendingWrites). Local data preserved.")
        } else if (hasParsingErrors) {
            Log.w("FirestoreSyncManager", "Skipping service deletion reconciliation: document parsing errors occurred. Local data preserved.")
        }

        // 4. Atomically reconcile deletions and insert updated records in a Room transaction
        database.withTransaction {
            if (canReconcileDeletions && servicesToDelete.isNotEmpty()) {
                servicesToDelete.forEach { deletedService ->
                    dao.deleteServiceById(deletedService.id)
                }
            }

            if (services.isNotEmpty()) {
                dao.insertServices(services)
            }
        }
    }


    // =====================================================
    // TRANSACTIONS
    // =====================================================

    private suspend fun syncTransactions(): Set<String> {

        val localBeforeSync =
            dao.getAllTransactionsForSync()

        val hadLocalData =
            localBeforeSync.isNotEmpty()

        val localById =
            localBeforeSync.associateBy {
                it.id
            }

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
                                readTimestampMillis(document, "updatedAt")
                                    ?: 0L
                        )

                    val oldTransaction =
                        localById[newTransaction.id]

                    if (hadLocalData) {

                        val isAdded =
                            oldTransaction == null && newTransaction.isActive

                        val hasChanged =
                            oldTransaction != null && (
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
                                    )

                        if (isAdded || hasChanged) {

                            changedTransactionIds.add(
                                newTransaction.id
                            )
                        }
                    }

                    newTransaction

                } catch (e: Exception) {

                    recordParsingError("transactions", document.id, e)

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
                                readTimestampMillis(document, "updatedAt")
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
                    "MMM dd, yyyy â€¢ hh:mm a",
                    Locale.getDefault()
                ).format(
                    Date(currentTime)
                )

            // Check if an unread notification already exists for this transaction
            // (e.g., created by an incoming FCM push payload or a prior unacknowledged update).
            val existingUnread =
                dao.getUnreadNotificationByTransaction(transaction.id)

            val notificationId =
                existingUnread?.id ?: "tx_update_${transaction.id}"

            // Preserve isNotified = true only if an incoming FCM push just created this
            // unread notification within the last 15 seconds for the same update event.
            // Any newly detected Firestore diff keeps isRead = false (for the in-app banner)
            // and sets isNotified = false so the system tray fires once for this new update.
            val wasJustNotifiedByFcm =
                existingUnread != null &&
                    existingUnread.isNotified &&
                    (currentTime - existingUnread.createdAt) in 0..15_000L

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
                    isNotified = wasJustNotifiedByFcm,
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
                        readTimestampMillis(document, "updatedAt")
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
                // SAFE FILE NAME (PRESERVE ORIGINAL FORMAT)
                // -----------------------------------------

                val safeStepId =
                    stepId.replace(
                        Regex(
                            "[^A-Za-z0-9_-]"
                        ),
                        "_"
                    )

                // -----------------------------------------
                // CONNECT TO REMOTE IMAGE URL
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

                val urlExtension =
                    url.path
                        .substringAfterLast('.', "")
                        .lowercase(Locale.ROOT)
                        .takeIf { it in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp") }

                val contentTypeExtension =
                    when (connection.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)) {
                        "image/png" -> "png"
                        "image/jpeg", "image/jpg" -> "jpg"
                        "image/webp" -> "webp"
                        "image/gif" -> "gif"
                        "image/bmp" -> "bmp"
                        else -> null
                    }

                val extension = urlExtension ?: contentTypeExtension ?: "img"

                val imageFile =
                    File(
                        imageDirectory,
                        "${safeStepId}_${System.currentTimeMillis()}.$extension"
                    )


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
                                readTimestampMillis(document, "updatedAt")
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

    suspend fun syncAnnouncements() {

        val snapshot = try {
            firestore
                .collection("announcements")
                .get(Source.SERVER)
                .await()
        } catch (e: Exception) {
            Log.w(
                "FirestoreSyncManager",
                "Failed to fetch announcements from SERVER, falling back to default source: ${e.message}"
            )
            firestore
                .collection("announcements")
                .get()
                .await()
        }

        val isFromCache = snapshot.metadata.isFromCache
        val hasPendingWrites = snapshot.metadata.hasPendingWrites()
        val isServerConfirmed = !isFromCache && !hasPendingWrites

        // Read all local announcements (including inactive) before synchronizing
        val localBeforeSync = dao.getAllAnnouncements()

        var hasParsingErrors = false

        // Convert current Firestore records to Room entities.
        val announcements =
            snapshot.documents.mapNotNull { document ->

                try {

                    val datePosted =
                        readTimestampMillis(document, "datePosted")
                            ?: System.currentTimeMillis()

                    val updatedAt =
                        readTimestampMillis(document, "updatedAt")
                            ?: datePosted

                    val startDate =
                        readTimestampMillis(document, "startDate")

                    val endDate =
                        readTimestampMillis(document, "endDate")

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

                    hasParsingErrors = true
                    Log.e("FirestoreSyncManager", "Parsing failed for announcement '${document.id}': ${e.message}", e)
                    recordParsingError("announcements", document.id, e)

                    null
                }
            }

        // Deletions are only safely reconciled when:
        // 1. Snapshot is server-confirmed (not cached or pending local writes)
        // 2. No documents failed parsing (prevents wiping local records if incoming format was corrupt)
        val canReconcileDeletions = isServerConfirmed && !hasParsingErrors

        val firestoreIds = snapshot.documents.map { it.id }.toSet()
        val announcementsToDelete = localBeforeSync.filter { it.id !in firestoreIds }

        if (!isServerConfirmed) {
            Log.w(
                "FirestoreSyncManager",
                "Skipping announcement deletion reconciliation: snapshot is cached or has pending writes (isFromCache=$isFromCache, hasPendingWrites=$hasPendingWrites). Local data preserved."
            )
        } else if (hasParsingErrors) {
            Log.w(
                "FirestoreSyncManager",
                "Skipping announcement deletion reconciliation: document parsing errors occurred. Local data preserved."
            )
        }

        // Atomically reconcile deletions and insert updated records in a Room transaction
        database.withTransaction {
            if (canReconcileDeletions && announcementsToDelete.isNotEmpty()) {
                announcementsToDelete.forEach { deletedAnnouncement ->
                    dao.deleteAnnouncementById(deletedAnnouncement.id)
                }
            }

            if (announcements.isNotEmpty()) {
                dao.insertAnnouncements(announcements)
            }
        }
    }
}