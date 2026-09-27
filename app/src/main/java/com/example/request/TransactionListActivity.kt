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

class TransactionListActivity : AppCompatActivity() {

    // =====================================================
    // UI COMPONENTS
    // =====================================================

    private lateinit var btnBack: TextView
    private lateinit var btnRefresh: ImageView

    private lateinit var tvServiceTitle: TextView
    private lateinit var transactionsContainer: LinearLayout
    private lateinit var tvNoTransactions: TextView


    // =====================================================
    // DATABASE
    // =====================================================

    private lateinit var dao: ERequestDao


    // =====================================================
    // FIRESTORE SYNC
    // =====================================================

    private lateinit var firestoreSyncManager: FirestoreSyncManager


    // =====================================================
    // SELECTED SERVICE
    // =====================================================

    private var serviceId: String = ""
    private var serviceName: String = ""


    // =====================================================
    // ON CREATE
    // =====================================================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_transaction_list
        )


        // =================================================
        // CONNECT XML
        // =================================================

        btnBack =
            findViewById(
                R.id.btnBack
            )

        btnRefresh =
            findViewById(
                R.id.btnRefresh
            )

        tvServiceTitle =
            findViewById(
                R.id.tvServiceTitle
            )

        transactionsContainer =
            findViewById(
                R.id.transactionsContainer
            )

        tvNoTransactions =
            findViewById(
                R.id.tvNoTransactions
            )


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
        // RECEIVE SELECTED SERVICE
        // =================================================

        serviceId =
            intent.getStringExtra(
                "SERVICE_ID"
            ) ?: ""

        serviceName =
            intent.getStringExtra(
                "SERVICE_NAME"
            ) ?: "School Service"


        // =================================================
        // DISPLAY SERVICE NAME
        // =================================================

        tvServiceTitle.text =
            serviceName


        // =================================================
        // LOAD SAVED TRANSACTIONS FROM ROOM
        // =================================================

        loadTransactions()


        // =================================================
        // BACK BUTTON
        // =================================================

        btnBack.setOnClickListener {

            finish()
        }


        // =================================================
        // REFRESH BUTTON
        // =================================================

        btnRefresh.setOnClickListener {

            refreshData()
        }
    }


    // =====================================================
    // REFRESH DATA
    //
    // FIRESTORE
    //      ↓
    // ROOM
    //      ↓
    // CURRENT TRANSACTION LIST
    // =====================================================

    private fun refreshData() {

        // Prevent repeated taps while syncing
        btnRefresh.isEnabled = false


        // =================================================
        // ROTATE REFRESH ICON
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
                // STEP 1
                // FIRESTORE → ROOM
                // =========================================

                withContext(
                    Dispatchers.IO
                ) {

                    firestoreSyncManager
                        .syncAllData()
                }


                // =========================================
                // STEP 2
                // GET UPDATED TRANSACTIONS
                // FOR THIS SERVICE ONLY
                // =========================================

                val transactions =
                    withContext(
                        Dispatchers.IO
                    ) {

                        dao.getTransactionsByService(
                            serviceId
                        )
                    }


                // =========================================
                // STEP 3
                // UPDATE CURRENT SCREEN
                // =========================================

                displayTransactions(
                    transactions
                )


                // =========================================
                // SUCCESS
                // =========================================

                Toast.makeText(
                    this@TransactionListActivity,
                    "Transactions updated.",
                    Toast.LENGTH_SHORT
                ).show()


            } catch (e: Exception) {

                e.printStackTrace()


                // =========================================
                // SYNC FAILED
                //
                // KEEP USING SAVED ROOM DATA
                // =========================================

                val savedTransactions =
                    withContext(
                        Dispatchers.IO
                    ) {

                        dao.getTransactionsByService(
                            serviceId
                        )
                    }


                displayTransactions(
                    savedTransactions
                )


                Toast.makeText(
                    this@TransactionListActivity,
                    "Unable to sync. Showing saved data.",
                    Toast.LENGTH_SHORT
                ).show()


            } finally {

                // =========================================
                // ENABLE REFRESH AGAIN
                // =========================================

                btnRefresh.isEnabled = true
            }
        }
    }


    // =====================================================
    // LOAD TRANSACTIONS FROM ROOM DATABASE
    // =====================================================

    private fun loadTransactions() {

        lifecycleScope.launch {

            val transactions =
                withContext(
                    Dispatchers.IO
                ) {

                    dao.getTransactionsByService(
                        serviceId
                    )
                }


            displayTransactions(
                transactions
            )
        }
    }


    // =====================================================
    // DISPLAY TRANSACTIONS
    // =====================================================

    private fun displayTransactions(
        transactions: List<TransactionEntity>
    ) {

        // Remove old transaction cards
        transactionsContainer.removeAllViews()


        // =================================================
        // NO TRANSACTIONS
        // =================================================

        if (transactions.isEmpty()) {

            tvNoTransactions.visibility =
                View.VISIBLE

            return
        }


        tvNoTransactions.visibility =
            View.GONE


        // =================================================
        // DISPLAY EACH TRANSACTION
        // =================================================

        for (transaction in transactions) {

            // =============================================
            // CREATE TRANSACTION CARD
            // =============================================

            val transactionView =
                LayoutInflater
                    .from(this)
                    .inflate(
                        R.layout.item_transaction,
                        transactionsContainer,
                        false
                    )


            // =============================================
            // CONNECT TRANSACTION CARD XML
            // =============================================

            val tvTransactionName =
                transactionView
                    .findViewById<TextView>(
                        R.id.tvTransactionName
                    )


            val tvTransactionDescription =
                transactionView
                    .findViewById<TextView>(
                        R.id.tvTransactionDescription
                    )


            // =============================================
            // TRANSACTION NAME
            // =============================================

            tvTransactionName.text =
                transaction.name


            // =============================================
            // TRANSACTION DESCRIPTION
            // =============================================

            tvTransactionDescription.text =
                transaction.description


            // =============================================
            // OPEN TRANSACTION DETAILS
            // =============================================

            transactionView.setOnClickListener {

                val intent =
                    Intent(
                        this,
                        TransactionDetailsActivity::class.java
                    )


                intent.putExtra(
                    "TRANSACTION_ID",
                    transaction.id
                )


                intent.putExtra(
                    "TRANSACTION_NAME",
                    transaction.name
                )


                intent.putExtra(
                    "SERVICE_NAME",
                    serviceName
                )


                startActivity(
                    intent
                )
            }


            // =============================================
            // ADD TRANSACTION CARD
            // =============================================

            transactionsContainer.addView(
                transactionView
            )
        }
    }
}