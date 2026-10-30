package com.example.request.features.progress

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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProgressActivity : AppCompatActivity() {

    private lateinit var rvProgress: RecyclerView
    private lateinit var tvNoProgress: TextView
    private lateinit var navHome: View
    private lateinit var navServices: View
    private lateinit var navProgress: View

    private lateinit var dao: ERequestDao
    private lateinit var progressAdapter: ProgressAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        rvProgress = findViewById(R.id.rvProgress)
        tvNoProgress = findViewById(R.id.tvNoProgress)
        navHome = findViewById(R.id.navHome)
        navServices = findViewById(R.id.navServices)
        navProgress = findViewById(R.id.navProgress)

        dao = ERequestDatabase
            .getDatabase(applicationContext)
            .eRequestDao()

        setupRecyclerView()

        NavAnimationHelper.applyNavBouncePress(navHome) {
            NavAnimationHelper.seamlessNavigate(
                this,
                Intent(this, HomeActivity::class.java)
            )
        }

        NavAnimationHelper.applyNavBouncePress(navServices) {
            NavAnimationHelper.seamlessNavigate(
                this,
                Intent(this, ServicesActivity::class.java)
            )
        }

        NavAnimationHelper.applyNavBouncePress(navProgress) {
            // Already on Progress screen - bounce animation still plays delightfully
        }
    }

    override fun onResume() {
        super.onResume()
        loadProgress()
    }

    private fun setupRecyclerView() {
        progressAdapter = ProgressAdapter { item ->
            openChecklist(item.transactionId, item.transactionName)
        }

        rvProgress.apply {
            layoutManager = LinearLayoutManager(this@ProgressActivity)
            adapter = progressAdapter
            setHasFixedSize(true)
        }
    }

    private fun loadProgress() {
        lifecycleScope.launch {
            val activeProgressItems = withContext(Dispatchers.IO) {
                val prefs = getSharedPreferences(
                    "RequirementChecklistProgress",
                    MODE_PRIVATE
                )

                // Batch-load all active transactions and requirements in 2 queries (eliminating N+1 queries)
                val transactions = dao.getAllTransactions()
                val requirementsByTransaction = dao.getAllActiveRequirements()
                    .groupBy { it.transactionId }

                transactions.mapNotNull { transaction ->
                    val requirements = requirementsByTransaction[transaction.id]
                        .orEmpty()

                    if (requirements.isEmpty()) {
                        return@mapNotNull null
                    }

                    var completed = 0
                    var started = false

                    for (requirement in requirements) {
                        val key = "${transaction.id}_requirement_${requirement.id}"
                        if (prefs.contains(key)) {
                            started = true
                        }
                        if (prefs.getBoolean(key, false)) {
                            completed++
                        }
                    }

                    if (!started) {
                        return@mapNotNull null
                    }

                    val total = requirements.size
                    val percentage = (completed * 100) / total

                    TransactionProgressUiModel(
                        transactionId = transaction.id,
                        transactionName = transaction.name,
                        officeName = transaction.officeName,
                        completedCount = completed,
                        totalCount = total,
                        percentage = percentage,
                        isComplete = percentage == 100 && total > 0
                    )
                }
            }

            displayProgress(activeProgressItems)
        }
    }

    private fun displayProgress(items: List<TransactionProgressUiModel>) {
        progressAdapter.submitList(items) {
            if (!isFinishing && !isDestroyed) {
                if (items.isEmpty()) {
                    rvProgress.visibility = View.GONE
                    tvNoProgress.visibility = View.VISIBLE
                } else {
                    rvProgress.visibility = View.VISIBLE
                    tvNoProgress.visibility = View.GONE
                }
            }
        }
    }

    private fun openChecklist(
        transactionId: String,
        transactionName: String
    ) {
        val intent = Intent(
            this,
            RequirementChecklistActivity::class.java
        ).apply {
            putExtra("TRANSACTION_ID", transactionId)
            putExtra("TRANSACTION_NAME", transactionName)
        }
        startActivity(intent)
    }
}
