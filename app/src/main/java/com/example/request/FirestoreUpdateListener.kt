package com.example.request

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class FirestoreUpdateListener(
    private val onUpdateDetected: () -> Unit
) {

    private val firestore =
        FirebaseFirestore.getInstance()

    private val listeners =
        mutableListOf<ListenerRegistration>()

    private var isStarted = false

    // Prevent the first Firestore snapshot from being
    // treated as a new admin update.
    private val initializedCollections =
        mutableSetOf<String>()


    // =====================================================
    // START LISTENING
    // =====================================================

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


    // =====================================================
    // LISTEN TO FIRESTORE COLLECTION
    // =====================================================

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

                    // First snapshot only establishes the
                    // current Firestore state.
                    //
                    // We don't want opening HomeActivity
                    // while already online to immediately
                    // trigger a full sync.
                    if (
                        !initializedCollections.contains(
                            collectionName
                        )
                    ) {

                        initializedCollections.add(
                            collectionName
                        )

                        return@addSnapshotListener
                    }

                    // Ignore snapshots where Firestore reports
                    // no document changes.
                    if (snapshot.documentChanges.isEmpty()) {
                        return@addSnapshotListener
                    }

                    onUpdateDetected()
                }

        listeners.add(
            registration
        )
    }


    // =====================================================
    // STOP LISTENING
    // =====================================================

    fun stop() {

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