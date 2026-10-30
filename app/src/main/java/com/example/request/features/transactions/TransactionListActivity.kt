package com.example.request.features.transactions

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

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionListActivity : AppCompatActivity() {

    private lateinit var btnBack: TextView
    private lateinit var btnRefresh: ImageView
    private lateinit var tvServiceTitle: TextView
    private lateinit var rvTransactions: RecyclerView
    private lateinit var tvNoTransactions: TextView

    private lateinit var dao: ERequestDao
    private lateinit var firestoreSyncManager: FirestoreSyncManager
    private lateinit var transactionAdapter: TransactionAdapter

    private var serviceId: String = ""
    private var serviceName: String = ""
    private var isRefreshing = false
    private var activeToast: Toast? = null

    private fun showSingleToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        activeToast?.cancel()
        activeToast = Toast.makeText(this, message, duration).also { it.show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_list)

        btnBack = findViewById(R.id.btnBack)
        btnRefresh = findViewById(R.id.btnRefresh)
        tvServiceTitle = findViewById(R.id.tvServiceTitle)
        rvTransactions = findViewById(R.id.rvTransactions)
        tvNoTransactions = findViewById(R.id.tvNoTransactions)

        val database = ERequestDatabase.getDatabase(applicationContext)
        dao = database.eRequestDao()
        firestoreSyncManager = FirestoreSyncManager(applicationContext)

        serviceId = intent.getStringExtra("SERVICE_ID") ?: ""
        serviceName = intent.getStringExtra("SERVICE_NAME") ?: "School Service"
        tvServiceTitle.text = serviceName

        setupRecyclerView()

        btnBack.setOnClickListener {
            finish()
        }

        btnRefresh.setOnClickListener {
            refreshData()
        }

        loadTransactions()
    }

    override fun onResume() {
        super.onResume()
        loadTransactions()
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter { transaction ->
            val intent = Intent(this, TransactionDetailsActivity::class.java).apply {
                putExtra("TRANSACTION_ID", transaction.id)
                putExtra("TRANSACTION_NAME", transaction.name)
                putExtra("SERVICE_NAME", serviceName)
            }
            startActivity(intent)
        }

        rvTransactions.apply {
            layoutManager = LinearLayoutManager(this@TransactionListActivity)
            adapter = transactionAdapter
            setHasFixedSize(true)
        }
    }

    private fun loadTransactions() {
        lifecycleScope.launch {
            val transactions = withContext(Dispatchers.IO) {
                dao.getTransactionsByService(serviceId)
            }
            displayTransactions(transactions)
        }
    }

    private fun displayTransactions(transactions: List<TransactionEntity>) {
        transactionAdapter.submitList(transactions) {
            if (!isFinishing && !isDestroyed) {
                if (transactions.isEmpty()) {
                    rvTransactions.visibility = View.GONE
                    tvNoTransactions.visibility = View.VISIBLE
                } else {
                    rvTransactions.visibility = View.VISIBLE
                    tvNoTransactions.visibility = View.GONE
                }
            }
        }
    }

    private fun refreshData() {
        if (isRefreshing || FirestoreSyncManager.isCurrentlySyncing()) return
        isRefreshing = true
        btnRefresh.isEnabled = false

        btnRefresh.animate()
            .rotationBy(360f)
            .setDuration(700L)
            .start()

        showSingleToast("Refreshing...")

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    firestoreSyncManager.syncAllData()
                }

                val transactions = withContext(Dispatchers.IO) {
                    dao.getTransactionsByService(serviceId)
                }

                displayTransactions(transactions)

                showSingleToast("Transactions updated.")
            } catch (e: Exception) {
                e.printStackTrace()

                val savedTransactions = withContext(Dispatchers.IO) {
                    dao.getTransactionsByService(serviceId)
                }

                displayTransactions(savedTransactions)

                showSingleToast("Unable to sync. Showing saved data.")
            } finally {
                isRefreshing = false
                btnRefresh.isEnabled = true
            }
        }
    }
}
