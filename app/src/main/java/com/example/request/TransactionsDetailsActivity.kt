package com.example.request

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TransactionDetailsActivity : AppCompatActivity() {

    private lateinit var btnBack: TextView
    private lateinit var tvTransactionTitle: TextView
    private lateinit var tvServiceName: TextView
    private lateinit var tvDescription: TextView
    private lateinit var requirementsContainer: LinearLayout
    private lateinit var stepsContainer: LinearLayout
    private lateinit var departmentGuidelinesContainer: LinearLayout
    private lateinit var tvOffice: TextView
    private lateinit var tvSchedule: TextView
    private lateinit var btnStartChecklist: Button

    private lateinit var detailsScrollView: ScrollView
    private lateinit var sectionAbout: View
    private lateinit var sectionRequirements: View
    private lateinit var sectionProcedure: View
    private lateinit var sectionOfficeInformation: View
    private lateinit var sectionOfficeSchedule: View
    private lateinit var sectionDepartmentGuidelines: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_details)

        btnBack = findViewById(R.id.btnBack)
        tvTransactionTitle = findViewById(R.id.tvTransactionTitle)
        tvServiceName = findViewById(R.id.tvServiceName)
        tvDescription = findViewById(R.id.tvDescription)
        requirementsContainer = findViewById(R.id.requirementsContainer)
        stepsContainer = findViewById(R.id.stepsContainer)
        departmentGuidelinesContainer = findViewById(R.id.departmentGuidelinesContainer)
        tvOffice = findViewById(R.id.tvOffice)
        tvSchedule = findViewById(R.id.tvSchedule)
        btnStartChecklist = findViewById(R.id.btnStartChecklist)

        detailsScrollView = findViewById(R.id.detailsScrollView)
        sectionAbout = findViewById(R.id.sectionAbout)
        sectionRequirements = findViewById(R.id.sectionRequirements)
        sectionProcedure = findViewById(R.id.sectionProcedure)
        sectionOfficeInformation = findViewById(R.id.sectionOfficeInformation)
        sectionOfficeSchedule = findViewById(R.id.sectionOfficeSchedule)
        sectionDepartmentGuidelines = findViewById(R.id.sectionDepartmentGuidelines)

        val transactionId = intent.getStringExtra("TRANSACTION_ID") ?: ""
        val transactionName = intent.getStringExtra("TRANSACTION_NAME") ?: "Transaction"
        val serviceName = intent.getStringExtra("SERVICE_NAME") ?: "School Service"
        val targetSection = intent.getStringExtra("TARGET_SECTION")

        tvTransactionTitle.text = transactionName
        tvServiceName.text = serviceName

        loadTransactionDetails(transactionId)

        if (!targetSection.isNullOrBlank()) {
            scrollToTargetSection(targetSection)
        }

        btnBack.setOnClickListener {
            finish()
        }

        btnStartChecklist.setOnClickListener {
            val checklistIntent = Intent(
                this,
                RequirementChecklistActivity::class.java
            ).apply {
                putExtra("TRANSACTION_ID", transactionId)
                putExtra("TRANSACTION_NAME", transactionName)
            }
            startActivity(checklistIntent)
        }
    }

    private fun scrollToTargetSection(targetSection: String) {
        val targetView = when (targetSection.uppercase()) {
            "ABOUT" -> sectionAbout
            "REQUIREMENTS" -> sectionRequirements
            "PROCEDURE",
            "STEP_BY_STEP_PROCEDURE",
            "STEPS" -> sectionProcedure
            "OFFICE_INFORMATION" -> sectionOfficeInformation
            "OFFICE_SCHEDULE" -> sectionOfficeSchedule
            "DEPARTMENT_GUIDELINES" -> sectionDepartmentGuidelines
            else -> null
        }

        targetView ?: return

        detailsScrollView.post {
            detailsScrollView.smoothScrollTo(0, targetView.top)
        }
    }

    private fun loadTransactionDetails(transactionId: String) {
        val requirements = TransactionRepository.getRequirements(transactionId)
        val procedures = TransactionRepository.getProcedures(transactionId)

        displayRequirements(requirements)
        displaySteps(procedures)

        when (transactionId) {
            "transaction_001" -> {
                tvDescription.text =
                    "View the requirements and step-by-step procedure for getting a prospectus."
                tvOffice.text = "Registrar's Office"
                tvSchedule.text = "Schedule will be provided by the administrator."

                displayDepartmentGuidelines(
                    listOf(
                        "Make sure all required documents are complete before proceeding.",
                        "Follow the guidelines provided by the Registrar's Office.",
                        "Check the Registrar's Office schedule before processing the transaction."
                    )
                )
            }

            "transaction_002" -> {
                tvDescription.text =
                    "View the requirements and step-by-step procedure for updating a prospectus."
                tvOffice.text = "Registrar's Office"
                tvSchedule.text = "Schedule will be provided by the administrator."

                displayDepartmentGuidelines(
                    listOf(
                        "Review your prospectus information before requesting an update.",
                        "Prepare all documents required by the Registrar's Office.",
                        "Follow the guidelines provided by the Registrar's Office."
                    )
                )
            }

            "transaction_003" -> {
                tvDescription.text =
                    "View the information and procedure for submitting an academic concern."
                tvOffice.text = "Academic Office"
                tvSchedule.text = "Schedule will be provided by the administrator."

                displayDepartmentGuidelines(
                    listOf(
                        "Prepare the necessary information related to your academic concern.",
                        "Follow the guidelines provided by the Academic Office."
                    )
                )
            }

            else -> {
                tvDescription.text = "Transaction information is currently unavailable."
                tvOffice.text = "Not available"
                tvSchedule.text = "Not available"
                displayDepartmentGuidelines(emptyList())
            }
        }
    }

    private fun displayRequirements(requirements: List<String>) {
        requirementsContainer.removeAllViews()

        if (requirements.isEmpty()) {
            val textView = TextView(this).apply {
                text = "No requirements available."
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 10.dp(), 0, 10.dp())
            }
            requirementsContainer.addView(textView)
            return
        }

        requirements.forEach { requirement ->
            val textView = TextView(this).apply {
                text = "• $requirement"
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                setPadding(0, 10.dp(), 0, 10.dp())
            }
            requirementsContainer.addView(textView)
        }
    }

    private fun displaySteps(steps: List<ProcedureStep>) {
        stepsContainer.removeAllViews()

        if (steps.isEmpty()) {
            val textView = TextView(this).apply {
                text = "No procedure available."
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 10.dp(), 0, 10.dp())
            }
            stepsContainer.addView(textView)
            return
        }

        steps.sortedBy { it.stepNumber }.forEach { step ->
            val stepContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12.dp(), 0, 12.dp())
            }

            val stepText = TextView(this).apply {
                text = "${step.stepNumber}.  ${step.instruction}"
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
            }

            stepContainer.addView(stepText)

            if (step.imageResource != null) {
                val imageView = ImageView(this).apply {
                    setImageResource(step.imageResource)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription =
                        step.imageCaption ?: "Procedure reference image"
                    isClickable = true
                    isFocusable = true

                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        220.dp()
                    ).apply {
                        topMargin = 12.dp()
                    }

                    setOnClickListener {
                        showImagePreview(
                            step.imageResource,
                            step.imageCaption
                        )
                    }
                }

                stepContainer.addView(imageView)

                if (!step.imageCaption.isNullOrBlank()) {
                    val caption = TextView(this).apply {
                        text = step.imageCaption
                        textSize = 12f
                        setTextColor(getColor(R.color.text_secondary))
                        setPadding(0, 6.dp(), 0, 0)
                    }
                    stepContainer.addView(caption)
                }

                val tapHint = TextView(this).apply {
                    text = "Tap image to enlarge"
                    textSize = 11f
                    setTextColor(getColor(R.color.primary))
                    setPadding(0, 4.dp(), 0, 0)
                }

                stepContainer.addView(tapHint)
            }

            stepsContainer.addView(stepContainer)
        }
        }

        private fun showImagePreview(
            imageResource: Int,
            caption: String?
        ) {
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

            val mainContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
                setBackgroundColor(getColor(R.color.surface))
            }

            val topContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val title = TextView(this).apply {
                text = caption ?: "Image Preview"
                textSize = 18f
                setTextColor(getColor(R.color.text_primary))
                setTypeface(typeface, android.graphics.Typeface.BOLD)

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val closeButton = TextView(this).apply {
                text = "✕"
                textSize = 24f
                setTextColor(getColor(R.color.text_primary))
                gravity = android.view.Gravity.CENTER
                setPadding(16.dp(), 8.dp(), 8.dp(), 8.dp())

                setOnClickListener {
                    dialog.dismiss()
                }
            }

            topContainer.addView(title)
            topContainer.addView(closeButton)

            val imageView = ImageView(this).apply {
                setImageResource(imageResource)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = caption ?: "Image Preview"

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply {
                    topMargin = 12.dp()
                }
            }

            mainContainer.addView(topContainer)
            mainContainer.addView(imageView)

            dialog.setContentView(mainContainer)

            dialog.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(
                    (resources.displayMetrics.widthPixels * 0.95).toInt(),
                    (resources.displayMetrics.heightPixels * 0.85).toInt()
                )
            }

            dialog.setOnShowListener {
                dialog.window?.setLayout(
                    (resources.displayMetrics.widthPixels * 0.95).toInt(),
                    (resources.displayMetrics.heightPixels * 0.85).toInt()
                )
            }

            dialog.show()
        }

        private fun displayDepartmentGuidelines(
            departmentGuidelines: List<String>
        ) {
            departmentGuidelinesContainer.removeAllViews()

            if (departmentGuidelines.isEmpty()) {
                val textView = TextView(this).apply {
                    text = "No department guidelines available."
                    textSize = 14f
                    setTextColor(getColor(R.color.text_secondary))
                    setPadding(0, 10.dp(), 0, 10.dp())
                }

                departmentGuidelinesContainer.addView(textView)
                return
            }

            departmentGuidelines.forEach { guideline ->
                val textView = TextView(this).apply {
                    text = "• $guideline"
                    textSize = 14f
                    setTextColor(getColor(R.color.text_secondary))
                    setPadding(0, 10.dp(), 0, 10.dp())
                }

                departmentGuidelinesContainer.addView(textView)
            }
        }

        private fun Int.dp(): Int {
            return (this * resources.displayMetrics.density).toInt()
        }
    }