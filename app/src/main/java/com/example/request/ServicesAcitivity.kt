package com.example.request

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServicesActivity : AppCompatActivity() {

    // =====================================================
    // UI COMPONENTS
    // =====================================================

    private lateinit var servicesContainer: LinearLayout
    private lateinit var tvNoServices: TextView

    private lateinit var navHome: TextView
    private lateinit var navServices: TextView
    private lateinit var navProgress: TextView

    private lateinit var btnRefresh: ImageView


    // =====================================================
    // DATABASE
    // =====================================================

    private lateinit var dao: ERequestDao


    // =====================================================
    // FIRESTORE SYNC
    // =====================================================

    private lateinit var firestoreSyncManager: FirestoreSyncManager


    // =====================================================
    // ON CREATE
    // =====================================================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_services)


        // =================================================
        // CONNECT XML
        // =================================================

        servicesContainer =
            findViewById(R.id.servicesContainer)

        tvNoServices =
            findViewById(R.id.tvNoServices)

        navHome =
            findViewById(R.id.navHome)

        navServices =
            findViewById(R.id.navServices)

        navProgress =
            findViewById(R.id.navProgress)

        btnRefresh =
            findViewById(R.id.btnRefresh)


        // =================================================
        // CONNECT ROOM DATABASE
        // =================================================

        val database =
            ERequestDatabase.getDatabase(
                applicationContext
            )

        dao =
            database.eRequestDao()


        // =================================================
        // CONNECT FIRESTORE SYNC MANAGER
        // =================================================

        firestoreSyncManager =
            FirestoreSyncManager(
                applicationContext
            )


        // =================================================
        // LOAD SAVED SERVICES FROM ROOM
        // =================================================

        loadServices()


        // =================================================
        // REFRESH BUTTON
        // =================================================

        btnRefresh.setOnClickListener {

            refreshData()
        }


        // =================================================
        // HOME NAVIGATION
        // =================================================

        navHome.setOnClickListener {

            val intent =
                Intent(
                    this,
                    HomeActivity::class.java
                )

            startActivity(intent)

            finish()
        }


        // =================================================
        // SERVICES NAVIGATION
        // =================================================

        navServices.setOnClickListener {

            // Already on Services page
        }


        // =================================================
        // PROGRESS NAVIGATION
        // =================================================

        navProgress.setOnClickListener {

            val intent =
                Intent(
                    this,
                    ProgressActivity::class.java
                )

            startActivity(intent)

            finish()
        }
    }


    // =====================================================
    // REFRESH DATA
    //
    // FIRESTORE
    //      ↓
    // ROOM DATABASE
    //      ↓
    // SERVICES PAGE
    // =====================================================

    private fun refreshData() {

        // Disable button while syncing
        // para hindi mapindot nang paulit-ulit

        btnRefresh.isEnabled = false


        // =================================================
        // REFRESH ICON ANIMATION
        // =================================================

        btnRefresh.animate()
            .rotationBy(360f)
            .setDuration(700)
            .start()


        Toast.makeText(
            this,
            "Refreshing...",
            Toast.LENGTH_SHORT
        ).show()


        lifecycleScope.launch {

            try {

                // =========================================
                // STEP 1:
                // FIRESTORE → ROOM
                // =========================================

                withContext(Dispatchers.IO) {

                    firestoreSyncManager
                        .syncAllData()
                }


                // =========================================
                // STEP 2:
                // GET UPDATED SERVICES FROM ROOM
                // =========================================

                val services =
                    withContext(Dispatchers.IO) {

                        dao.getAllServices()
                    }


                // =========================================
                // STEP 3:
                // DISPLAY UPDATED SERVICES
                // =========================================

                displayServices(
                    services
                )


                // =========================================
                // SUCCESS MESSAGE
                // =========================================

                Toast.makeText(
                    this@ServicesActivity,
                    "Data updated.",
                    Toast.LENGTH_SHORT
                ).show()


            } catch (e: Exception) {

                // =========================================
                // SYNC FAILED
                // =========================================

                e.printStackTrace()


                // =========================================
                // KEEP USING SAVED ROOM DATA
                // =========================================

                val savedServices =
                    withContext(Dispatchers.IO) {

                        dao.getAllServices()
                    }


                displayServices(
                    savedServices
                )


                // =========================================
                // OFFLINE / ERROR MESSAGE
                // =========================================

                Toast.makeText(
                    this@ServicesActivity,
                    "Unable to sync. Showing saved data.",
                    Toast.LENGTH_SHORT
                ).show()


            } finally {

                // =========================================
                // ENABLE REFRESH BUTTON AGAIN
                // =========================================

                btnRefresh.isEnabled = true
            }
        }
    }


    // =====================================================
    // LOAD SERVICES FROM ROOM DATABASE
    // =====================================================

    private fun loadServices() {

        lifecycleScope.launch {

            val services =
                withContext(Dispatchers.IO) {

                    dao.getAllServices()
                }


            displayServices(
                services
            )
        }
    }


    // =====================================================
    // DISPLAY SERVICES
    // =====================================================

    private fun displayServices(
        services: List<ServiceEntity>
    ) {

        // Remove old service cards first

        servicesContainer.removeAllViews()


        // =================================================
        // NO SERVICES AVAILABLE
        // =================================================

        if (services.isEmpty()) {

            tvNoServices.visibility =
                View.VISIBLE

            return
        }


        // Hide empty message

        tvNoServices.visibility =
            View.GONE


        // =================================================
        // DISPLAY EACH SERVICE
        // =================================================

        services.forEach { service ->


            // =============================================
            // CREATE SERVICE CARD
            // =============================================

            val serviceView =
                LayoutInflater
                    .from(this)
                    .inflate(
                        R.layout.item_school_service,
                        servicesContainer,
                        false
                    )


            // =============================================
            // CONNECT SERVICE CARD XML
            // =============================================

            val tvServiceIcon =
                serviceView
                    .findViewById<TextView>(
                        R.id.tvServiceIcon
                    )


            val tvServiceName =
                serviceView
                    .findViewById<TextView>(
                        R.id.tvServiceName
                    )


            val tvServiceDescription =
                serviceView
                    .findViewById<TextView>(
                        R.id.tvServiceDescription
                    )


            // =============================================
            // SERVICE ICON
            // First letter of service name
            // =============================================

            tvServiceIcon.text =
                service.name
                    .firstOrNull()
                    ?.uppercase()
                    ?: "S"


            // =============================================
            // SERVICE NAME
            // =============================================

            tvServiceName.text =
                service.name


            // =============================================
            // SERVICE DESCRIPTION
            // =============================================

            tvServiceDescription.text =
                service.description


            // =============================================
            // SERVICE CLICK
            //
            // SERVICES
            //      ↓
            // TRANSACTION LIST
            // =============================================

            serviceView.setOnClickListener {

                val intent =
                    Intent(
                        this,
                        TransactionListActivity::class.java
                    ).apply {

                        putExtra(
                            "SERVICE_ID",
                            service.id
                        )

                        putExtra(
                            "SERVICE_NAME",
                            service.name
                        )
                    }


                startActivity(intent)
            }


            // =============================================
            // ADD CARD TO SERVICES CONTAINER
            // =============================================

            servicesContainer.addView(
                serviceView
            )
        }
    }
}