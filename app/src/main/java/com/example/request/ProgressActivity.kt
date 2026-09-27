package com.example.request

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProgressActivity : AppCompatActivity() {

    private lateinit var progressContainer: LinearLayout
    private lateinit var tvNoProgress: TextView
    private lateinit var navHome: TextView
    private lateinit var navServices: TextView
    private lateinit var navProgress: TextView

    private lateinit var dao: ERequestDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_progress)

        progressContainer = findViewById(R.id.progressContainer)
        tvNoProgress = findViewById(R.id.tvNoProgress)
        navHome = findViewById(R.id.navHome)
        navServices = findViewById(R.id.navServices)
        navProgress = findViewById(R.id.navProgress)

        // Room Database
        dao = ERequestDatabase
            .getDatabase(applicationContext)
            .eRequestDao()

        navHome.setOnClickListener {
            startActivity(
                Intent(this, HomeActivity::class.java)
            )
            finish()
        }

        navServices.setOnClickListener {
            startActivity(
                Intent(this, ServicesActivity::class.java)
            )
            finish()
        }

        navProgress.setOnClickListener {
            // Already on Progress screen
        }
    }

    override fun onResume() {
        super.onResume()
        loadProgress()
    }

    // =====================================================
    // LOAD PROGRESS FROM ROOM + SHARED PREFERENCES
    // =====================================================

    private fun loadProgress() {

        progressContainer.removeAllViews()

        lifecycleScope.launch {

            val progressItems = withContext(Dispatchers.IO) {

                val transactions = dao.getAllTransactions()
                    .filter { it.isActive }

                transactions.mapNotNull { transaction ->

                    val requirements =
                        dao.getRequirements(transaction.id)
                            .filter { it.isActive }
                            .sortedBy { it.displayOrder }

                    if (requirements.isEmpty()) {
                        return@mapNotNull null
                    }

                    ProgressItem(
                        transaction = transaction,
                        requirements = requirements
                    )
                }
            }

            displayProgress(progressItems)
        }
    }

    // =====================================================
    // DISPLAY ACTIVE PROGRESS
    // =====================================================

    private fun displayProgress(
        progressItems: List<ProgressItem>
    ) {

        progressContainer.removeAllViews()

        val prefs = getSharedPreferences(
            "RequirementChecklistProgress",
            MODE_PRIVATE
        )

        var activeCount = 0

        progressItems.forEach { item ->

            val transaction = item.transaction
            val requirements = item.requirements

            var completed = 0
            var started = false

            requirements.forEach { requirement ->

                val key =
                    "${transaction.id}_requirement_${requirement.id}"

                if (prefs.contains(key)) {
                    started = true
                }

                if (prefs.getBoolean(key, false)) {
                    completed++
                }
            }

            // Show only transactions where checklist
            // has already been started
            if (started) {

                activeCount++

                addProgressCard(
                    transaction = transaction,
                    completed = completed,
                    total = requirements.size
                )
            }
        }

        tvNoProgress.visibility =
            if (activeCount == 0) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    // =====================================================
    // CREATE PROGRESS CARD
    // =====================================================

    private fun addProgressCard(
        transaction: TransactionEntity,
        completed: Int,
        total: Int
    ) {

        val percentage =
            if (total == 0) {
                0
            } else {
                (completed * 100) / total
            }

        val card = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                18.dp(),
                18.dp(),
                18.dp(),
                18.dp()
            )

            background =
                getDrawable(R.drawable.bg_card)

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 14.dp()
                }
        }

        // TRANSACTION NAME
        val title = TextView(this).apply {

            text = transaction.name

            textSize = 18f

            setTextColor(
                getColor(R.color.text_primary)
            )

            setTypeface(
                typeface,
                Typeface.BOLD
            )
        }

        // OFFICE
        val service = TextView(this).apply {

            text =
                if (transaction.officeName.isNotBlank()) {
                    transaction.officeName
                } else {
                    "School Service"
                }

            textSize = 12f

            setTextColor(
                getColor(R.color.primary)
            )

            setPadding(
                0,
                4.dp(),
                0,
                0
            )
        }

        // PROGRESS TITLE
        val progressTitle = TextView(this).apply {

            text = "Requirements Progress"

            textSize = 13f

            setTextColor(
                getColor(R.color.text_secondary)
            )

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setPadding(
                0,
                16.dp(),
                0,
                7.dp()
            )
        }

        // PROGRESS BAR
        val progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {

            max = 100
            progress = percentage

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    10.dp()
                )
        }

        // PROGRESS TEXT
        val progressText = TextView(this).apply {

            text =
                "$completed of $total requirements completed • $percentage%"

            textSize = 12f

            setTextColor(
                getColor(R.color.text_secondary)
            )

            setPadding(
                0,
                8.dp(),
                0,
                0
            )
        }

        // STATUS
        val status = TextView(this).apply {

            text =
                if (
                    percentage == 100 &&
                    total > 0
                ) {
                    "✓ Requirements Complete"
                } else {
                    "In Progress"
                }

            textSize = 13f

            setTextColor(
                getColor(
                    if (
                        percentage == 100 &&
                        total > 0
                    ) {
                        R.color.success
                    } else {
                        R.color.primary
                    }
                )
            )

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setPadding(
                0,
                8.dp(),
                0,
                0
            )
        }

        // CONTINUE BUTTON
        val continueButton = Button(this).apply {

            text =
                if (
                    percentage == 100 &&
                    total > 0
                ) {
                    "View Checklist"
                } else {
                    "Continue Checklist"
                }

            isAllCaps = false

            setTextColor(
                getColor(R.color.text_white)
            )

            backgroundTintList =
                getColorStateList(R.color.primary)

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    50.dp()
                ).apply {
                    topMargin = 15.dp()
                }

            setOnClickListener {

                openChecklist(transaction)
            }
        }

        card.addView(title)
        card.addView(service)
        card.addView(progressTitle)
        card.addView(progressBar)
        card.addView(progressText)
        card.addView(status)
        card.addView(continueButton)

        progressContainer.addView(card)
    }

    // =====================================================
    // OPEN CHECKLIST
    // =====================================================

    private fun openChecklist(
        transaction: TransactionEntity
    ) {

        val intent =
            Intent(
                this,
                RequirementChecklistActivity::class.java
            ).apply {

                putExtra(
                    "TRANSACTION_ID",
                    transaction.id
                )

                putExtra(
                    "TRANSACTION_NAME",
                    transaction.name
                )
            }

        startActivity(intent)
    }

    // =====================================================
    // INTERNAL MODEL
    // =====================================================

    private data class ProgressItem(
        val transaction: TransactionEntity,
        val requirements: List<RequirementEntity>
    )

    // =====================================================
    // DP CONVERTER
    // =====================================================

    private fun Int.dp(): Int {

        return (
                this *
                        resources.displayMetrics.density
                ).toInt()
    }
}