package com.example.request

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RequirementChecklistActivity : AppCompatActivity() {

    private lateinit var btnBack: TextView
    private lateinit var tvChecklistTitle: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvPercentage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var requirementsChecklistContainer: LinearLayout
    private lateinit var tvCompleteMessage: TextView
    private lateinit var btnResetChecklist: Button

    private var transactionId = ""
    private var transactionName = ""
    private var requirements: List<String> = emptyList()
    private val checkBoxes = mutableListOf<CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_requirement_checklist)

        btnBack = findViewById(R.id.btnBack)
        tvChecklistTitle = findViewById(R.id.tvChecklistTitle)
        tvProgress = findViewById(R.id.tvProgress)
        tvPercentage = findViewById(R.id.tvPercentage)
        progressBar = findViewById(R.id.progressBar)
        requirementsChecklistContainer = findViewById(R.id.requirementsChecklistContainer)
        tvCompleteMessage = findViewById(R.id.tvCompleteMessage)
        btnResetChecklist = findViewById(R.id.btnResetChecklist)

        transactionId = intent.getStringExtra("TRANSACTION_ID") ?: ""
        transactionName = intent.getStringExtra("TRANSACTION_NAME") ?: "Transaction"

        tvChecklistTitle.text = transactionName

        // Requirements now come from ONE shared source.
        requirements = TransactionRepository.getRequirements(transactionId)

        createRequirementChecklist()

        btnBack.setOnClickListener {
            finish()
        }

        btnResetChecklist.setOnClickListener {
            resetChecklist()
        }
    }

    private fun createRequirementChecklist() {
        requirementsChecklistContainer.removeAllViews()
        checkBoxes.clear()

        if (requirements.isEmpty()) {
            val noRequirements = TextView(this).apply {
                text = "No requirements available."
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(12, 16, 12, 16)
            }

            requirementsChecklistContainer.addView(noRequirements)
            updateProgress()
            return
        }

        requirements.forEachIndexed { index, requirement ->

            val checkBox = CheckBox(this).apply {
                text = requirement
                textSize = 15f
                setTextColor(getColor(R.color.text_primary))
                setPadding(8, 14, 8, 14)

                // Restore saved checkbox state.
                isChecked = loadRequirementStatus(index)

                setOnCheckedChangeListener { _, isChecked ->
                    saveRequirementStatus(index, isChecked)
                    updateProgress()
                }
            }

            checkBoxes.add(checkBox)
            requirementsChecklistContainer.addView(checkBox)
        }

        updateProgress()
    }

    private fun updateProgress() {
        val total = checkBoxes.size
        val completed = checkBoxes.count { it.isChecked }
        val percentage = if (total == 0) 0 else (completed * 100) / total

        tvProgress.text = "$completed of $total requirements completed"
        tvPercentage.text = "$percentage%"
        progressBar.progress = percentage

        tvCompleteMessage.visibility =
            if (total > 0 && completed == total) View.VISIBLE
            else View.GONE
    }

    private fun saveRequirementStatus(index: Int, checked: Boolean) {
        getSharedPreferences("RequirementChecklistProgress", MODE_PRIVATE)
            .edit()
            .putBoolean("${transactionId}_requirement_$index", checked)
            .apply()
    }

    private fun loadRequirementStatus(index: Int): Boolean {
        return getSharedPreferences("RequirementChecklistProgress", MODE_PRIVATE)
            .getBoolean("${transactionId}_requirement_$index", false)
    }

    private fun resetChecklist() {
        val preferences = getSharedPreferences(
            "RequirementChecklistProgress",
            MODE_PRIVATE
        )

        val editor = preferences.edit()

        checkBoxes.forEachIndexed { index, checkBox ->
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = false
            editor.remove("${transactionId}_requirement_$index")
        }

        editor.apply()

        createRequirementChecklist()
    }
}
