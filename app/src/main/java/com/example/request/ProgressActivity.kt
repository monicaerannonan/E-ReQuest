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

class ProgressActivity : AppCompatActivity() {

    private lateinit var progressContainer: LinearLayout
    private lateinit var tvNoProgress: TextView
    private lateinit var navHome: TextView
    private lateinit var navServices: TextView
    private lateinit var navProgress: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        progressContainer = findViewById(R.id.progressContainer)
        tvNoProgress = findViewById(R.id.tvNoProgress)
        navHome = findViewById(R.id.navHome)
        navServices = findViewById(R.id.navServices)
        navProgress = findViewById(R.id.navProgress)

        navHome.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        navServices.setOnClickListener {
            startActivity(Intent(this, ServicesActivity::class.java))
            finish()
        }

        navProgress.setOnClickListener { }
    }

    override fun onResume() {
        super.onResume()
        loadProgress()
    }

    private fun loadProgress() {
        progressContainer.removeAllViews()

        val prefs = getSharedPreferences(
            "RequirementChecklistProgress",
            MODE_PRIVATE
        )

        val transactions = TransactionRepository.getAllTransactions()
        var activeCount = 0

        transactions.forEach { transaction ->
            val requirements = transaction.requirements
            var completed = 0
            var started = false

            requirements.indices.forEach { index ->
                val key = "${transaction.id}_requirement_$index"

                if (prefs.contains(key)) {
                    started = true
                }

                if (prefs.getBoolean(key, false)) {
                    completed++
                }
            }

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
            if (activeCount == 0) View.VISIBLE else View.GONE
    }

    private fun addProgressCard(
        transaction: TransactionRepository.Transaction,
        completed: Int,
        total: Int
    ) {
        val percentage =
            if (total == 0) 0
            else (completed * 100) / total

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
            background = getDrawable(R.drawable.bg_card)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14.dp()
            }
        }

        val title = TextView(this).apply {
            text = transaction.name
            textSize = 18f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, Typeface.BOLD)
        }

        val service = TextView(this).apply {
            text = transaction.serviceName
            textSize = 12f
            setTextColor(getColor(R.color.primary))
            setPadding(0, 4.dp(), 0, 0)
        }

        val progressTitle = TextView(this).apply {
            text = "Requirements Progress"
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 16.dp(), 0, 7.dp())
        }

        val progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = percentage

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                10.dp()
            )
        }

        val progressText = TextView(this).apply {
            text = "$completed of $total requirements completed • $percentage%"
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, 8.dp(), 0, 0)
        }

        val status = TextView(this).apply {
            text =
                if (percentage == 100 && total > 0) {
                    "✓ Requirements Complete"
                } else {
                    "In Progress"
                }

            textSize = 13f

            setTextColor(
                getColor(
                    if (percentage == 100 && total > 0) {
                        R.color.success
                    } else {
                        R.color.primary
                    }
                )
            )

            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 8.dp(), 0, 0)
        }

        val continueButton = Button(this).apply {
            text =
                if (percentage == 100 && total > 0) {
                    "View Checklist"
                } else {
                    "Continue Checklist"
                }

            isAllCaps = false
            setTextColor(getColor(R.color.text_white))
            backgroundTintList = getColorStateList(R.color.primary)

            layoutParams = LinearLayout.LayoutParams(
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

    private fun openChecklist(
        transaction: TransactionRepository.Transaction
    ) {
        val intent = Intent(
            this,
            RequirementChecklistActivity::class.java
        ).apply {
            putExtra("TRANSACTION_ID", transaction.id)
            putExtra("TRANSACTION_NAME", transaction.name)
        }

        startActivity(intent)
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}