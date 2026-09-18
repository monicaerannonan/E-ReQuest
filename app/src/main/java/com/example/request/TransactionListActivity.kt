package com.example.request

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.request.model.Transaction

class TransactionListActivity : AppCompatActivity() {

    private lateinit var btnBack: TextView
    private lateinit var tvServiceTitle: TextView
    private lateinit var transactionsContainer: LinearLayout
    private lateinit var tvNoTransactions: TextView

    private var serviceId: String = ""
    private var serviceName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_transaction_list)

        // CONNECT XML
        btnBack = findViewById(R.id.btnBack)
        tvServiceTitle = findViewById(R.id.tvServiceTitle)
        transactionsContainer = findViewById(R.id.transactionsContainer)
        tvNoTransactions = findViewById(R.id.tvNoTransactions)

        // RECEIVE SELECTED SERVICE
        serviceId =
            intent.getStringExtra("SERVICE_ID") ?: ""

        serviceName =
            intent.getStringExtra("SERVICE_NAME")
                ?: "School Service"

        // DISPLAY SERVICE NAME
        tvServiceTitle.text = serviceName

        // LOAD TEMPORARY TRANSACTIONS
        val transactions =
            getTemporaryTransactions()

        displayTransactions(transactions)

        // BACK BUTTON
        btnBack.setOnClickListener {
            finish()
        }
    }


    // =====================================================
    // TEMPORARY TRANSACTION DATA
    // Later this will come from the database
    // =====================================================

    private fun getTemporaryTransactions(): List<Transaction> {

        return listOf(

            // REGISTRAR
            Transaction(
                id = "transaction_001",
                serviceId = "service_001",
                name = "Getting Prospectus",
                description =
                    "View the procedure and requirements for getting a prospectus.",
                isActive = true,
                displayOrder = 1
            ),

            Transaction(
                id = "transaction_002",
                serviceId = "service_001",
                name = "Updating Prospectus",
                description =
                    "View the procedure and requirements for updating a prospectus.",
                isActive = true,
                displayOrder = 2
            ),

            // ACADEMIC
            Transaction(
                id = "transaction_003",
                serviceId = "service_002",
                name = "Academic Concern",
                description =
                    "View the procedure for submitting an academic concern.",
                isActive = true,
                displayOrder = 1
            )
        )
    }


    // =====================================================
    // DISPLAY TRANSACTIONS
    // =====================================================

    private fun displayTransactions(
        transactions: List<Transaction>
    ) {

        transactionsContainer.removeAllViews()

        val filteredTransactions =
            transactions
                .filter {
                    it.serviceId == serviceId &&
                            it.isActive
                }
                .sortedBy {
                    it.displayOrder
                }

        if (filteredTransactions.isEmpty()) {

            tvNoTransactions.visibility =
                View.VISIBLE

            return
        }

        tvNoTransactions.visibility =
            View.GONE

        for (transaction in filteredTransactions) {

            val transactionView =
                LayoutInflater.from(this).inflate(
                    R.layout.item_transaction,
                    transactionsContainer,
                    false
                )

            val tvTransactionName =
                transactionView.findViewById<TextView>(
                    R.id.tvTransactionName
                )

            val tvTransactionDescription =
                transactionView.findViewById<TextView>(
                    R.id.tvTransactionDescription
                )

            tvTransactionName.text =
                transaction.name

            tvTransactionDescription.text =
                transaction.description

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

                startActivity(intent)
            }

            transactionsContainer.addView(
                transactionView
            )
        }
    }
}