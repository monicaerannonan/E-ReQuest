package com.example.request.features.checklist

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

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RequirementChecklistActivity : AppCompatActivity() {

    private lateinit var btnBack: TextView
    private lateinit var tvChecklistTitle: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvPercentage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var requirementsChecklistContainer: LinearLayout
    private lateinit var tvCompleteMessage: TextView
    private lateinit var btnResetChecklist: Button

    private lateinit var dao: ERequestDao

    private var transactionId = ""
    private var transactionName = ""

    private var requirements: List<RequirementEntity> = emptyList()

    private val checkBoxes = mutableListOf<CheckBox>()

    private var transactionObservationJob: kotlinx.coroutines.Job? = null
    private var hasLoadedTransaction = false

    private val syncCompletionListener: () -> Unit = {
        if (!isFinishing && !isDestroyed) {
            loadRequirements()
        }
    }

    private fun observeTransactionFlow() {
        transactionObservationJob?.cancel()
        transactionObservationJob = lifecycleScope.launch {
            dao.getTransactionFlow(transactionId).collect { transaction ->
                if (transaction == null) {
                    if (hasLoadedTransaction) {
                        android.widget.Toast.makeText(
                            this@RequirementChecklistActivity,
                            "This transaction is no longer available.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                } else {
                    hasLoadedTransaction = true
                    tvChecklistTitle.text = transaction.name
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        FirestoreSyncManager.registerSyncCompletionListener(syncCompletionListener)
        observeTransactionFlow()
    }

    override fun onStop() {
        FirestoreSyncManager.unregisterSyncCompletionListener(syncCompletionListener)
        transactionObservationJob?.cancel()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_requirement_checklist)

        // =====================================================
        // CONNECT XML
        // =====================================================

        btnBack = findViewById(R.id.btnBack)
        tvChecklistTitle = findViewById(R.id.tvChecklistTitle)
        tvProgress = findViewById(R.id.tvProgress)
        tvPercentage = findViewById(R.id.tvPercentage)
        progressBar = findViewById(R.id.progressBar)

        requirementsChecklistContainer =
            findViewById(R.id.requirementsChecklistContainer)

        tvCompleteMessage =
            findViewById(R.id.tvCompleteMessage)

        btnResetChecklist =
            findViewById(R.id.btnResetChecklist)

        // =====================================================
        // ROOM DATABASE
        // =====================================================

        dao = ERequestDatabase
            .getDatabase(applicationContext)
            .eRequestDao()

        // =====================================================
        // RECEIVE TRANSACTION
        // =====================================================

        transactionId =
            intent.getStringExtra("TRANSACTION_ID") ?: ""

        transactionName =
            intent.getStringExtra("TRANSACTION_NAME")
                ?: "Transaction"

        tvChecklistTitle.text = transactionName

        // =====================================================
        // LOAD REQUIREMENTS FROM ROOM
        // =====================================================

        loadRequirements()

        // =====================================================
        // BACK BUTTON
        // =====================================================

        btnBack.setOnClickListener {
            finish()
        }

        // =====================================================
        // RESET CHECKLIST
        // =====================================================

        btnResetChecklist.setOnClickListener {
            resetChecklist()
        }
    }

    // =========================================================
    // LOAD REQUIREMENTS FROM ROOM DATABASE
    // =========================================================

    private fun loadRequirements() {

        lifecycleScope.launch {

            val roomRequirements = withContext(Dispatchers.IO) {

                dao.getRequirements(transactionId)
            }

            requirements = roomRequirements
                .filter { it.isActive }
                .sortedBy { it.displayOrder }

            createRequirementChecklist()
        }
    }

    // =========================================================
    // CREATE CHECKLIST
    // =========================================================

    private fun createRequirementChecklist() {

        requirementsChecklistContainer.removeAllViews()

        checkBoxes.clear()

        // =====================================================
        // NO REQUIREMENTS
        // =====================================================

        if (requirements.isEmpty()) {

            val noRequirements =
                TextView(this).apply {

                    text = "No requirements available."

                    textSize = 14f

                    setTextColor(
                        getColor(R.color.text_secondary)
                    )

                    setPadding(
                        12,
                        16,
                        12,
                        16
                    )
                }

            requirementsChecklistContainer.addView(
                noRequirements
            )

            updateProgress()

            return
        }

        // =====================================================
        // DISPLAY REQUIREMENTS
        // =====================================================

        requirements.forEach { requirement ->

            val checkBox =
                CheckBox(this).apply {

                    text =
                        requirement.requirementText

                    textSize = 15f

                    setTextColor(
                        getColor(R.color.text_primary)
                    )

                    setPadding(
                        8,
                        14,
                        8,
                        14
                    )

                    // Restore saved checkbox state
                    isChecked =
                        loadRequirementStatus(
                            requirement.id
                        )

                    setOnCheckedChangeListener {
                            _,
                            isChecked ->

                        saveRequirementStatus(
                            requirement.id,
                            isChecked
                        )

                        updateProgress()
                    }
                }

            checkBoxes.add(checkBox)

            requirementsChecklistContainer.addView(
                checkBox
            )
        }

        updateProgress()
    }

    // =========================================================
    // UPDATE PROGRESS
    // =========================================================

    private fun updateProgress() {

        val total =
            checkBoxes.size

        val completed =
            checkBoxes.count {
                it.isChecked
            }

        val percentage =
            if (total == 0) {
                0
            } else {
                (completed * 100) / total
            }

        tvProgress.text =
            "$completed of $total requirements completed"

        tvPercentage.text =
            "$percentage%"

        progressBar.progress =
            percentage

        tvCompleteMessage.visibility =
            if (
                total > 0 &&
                completed == total
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    // =========================================================
    // SAVE CHECKBOX STATE
    // =========================================================

    private fun saveRequirementStatus(
        requirementId: String,
        checked: Boolean
    ) {

        getSharedPreferences(
            "RequirementChecklistProgress",
            MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                "${transactionId}_requirement_$requirementId",
                checked
            )
            .apply()
    }

    // =========================================================
    // LOAD CHECKBOX STATE
    // =========================================================

    private fun loadRequirementStatus(
        requirementId: String
    ): Boolean {

        return getSharedPreferences(
            "RequirementChecklistProgress",
            MODE_PRIVATE
        )
            .getBoolean(
                "${transactionId}_requirement_$requirementId",
                false
            )
    }

    // =========================================================
    // RESET CHECKLIST
    // =========================================================

    private fun resetChecklist() {

        val preferences =
            getSharedPreferences(
                "RequirementChecklistProgress",
                MODE_PRIVATE
            )

        val editor =
            preferences.edit()

        requirements.forEachIndexed {
                index,
                requirement ->

            if (index < checkBoxes.size) {

                val checkBox =
                    checkBoxes[index]

                checkBox.setOnCheckedChangeListener(
                    null
                )

                checkBox.isChecked =
                    false
            }

            editor.remove(
                "${transactionId}_requirement_${requirement.id}"
            )
        }

        editor.apply()

        createRequirementChecklist()
    }
}