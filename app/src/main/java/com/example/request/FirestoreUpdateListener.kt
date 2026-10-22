package com.example.request

import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class FirestoreUpdateListener(
    private val debounceDelayMs: Long = 1500L,
    private val onUpdateDetected: () -> Unit
) {

    private val firestore =
        FirebaseFirestore.getInstance()

    private val listeners =
        mutableListOf<ListenerRegistration>()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private var pendingDispatchRunnable: Runnable? = null

    private var isStarted = false

    // Prevent the first Firestore snapshot from being
    // treated as a new admin update.
    private val initializedCollections =
        mutableSetOf<String>()

    fun start() {
        if (isStarted) {
            return
        }

        isStarted = true

        listenToCollection("services")
        listenToCollection("transactions")
        listenToCollection("requirements")
        listenToCollection("procedure_steps")
        listenToCollection("department_guidelines")
        listenToCollection("announcements")
    }

    private fun listenToCollection(
        collectionName: String
    ) {
        val registration =
            firestore
                .collection(collectionName)
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        error.printStackTrace()
                        return@addSnapshotListener
                    }

                    if (snapshot == null) {
                        return@addSnapshotListener
                    }

                    // Establish baseline on the first snapshot for each collection.
                    if (!initializedCollections.contains(collectionName)) {
                        initializedCollections.add(collectionName)
                        return@addSnapshotListener
                    }

                    // Ignore cached snapshots or snapshots without actual document changes.
                    if (snapshot.metadata.isFromCache || snapshot.documentChanges.isEmpty()) {
                        return@addSnapshotListener
                    }

                    scheduleDebouncedUpdate()
                }

        listeners.add(registration)
    }

    private fun scheduleDebouncedUpdate() {
        pendingDispatchRunnable?.let { mainHandler.removeCallbacks(it) }

        val runnable = object : Runnable {
            override fun run() {
                if (!isStarted) {
                    pendingDispatchRunnable = null
                    return
                }

                // If a sync pass is currently running, defer the callback rather than dropping it
                if (FirestoreSyncManager.isCurrentlySyncing()) {
                    mainHandler.postDelayed(this, debounceDelayMs)
                    return
                }

                pendingDispatchRunnable = null
                onUpdateDetected()
            }
        }

        pendingDispatchRunnable = runnable
        mainHandler.postDelayed(runnable, debounceDelayMs)
    }

    fun stop() {
        pendingDispatchRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingDispatchRunnable = null

        listeners.forEach { listener ->
            try {
                listener.remove()
            } catch (_: Exception) {
                // Ignore listener cleanup errors.
            }
        }

        listeners.clear()
        initializedCollections.clear()
        isStarted = false
    }
}
