package com.example.request

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class TransactionDetailsActivity : AppCompatActivity() {

    private lateinit var btnBack: TextView
    private lateinit var btnRefresh: ImageView
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

    private lateinit var dao: ERequestDao
    private lateinit var firestoreSyncManager: FirestoreSyncManager

    private var transactionId: String = ""

    // =========================================================
    // ON CREATE
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_transaction_details
        )

        // =====================================================
        // CONNECT XML
        // =====================================================

        btnBack =
            findViewById(R.id.btnBack)

        btnRefresh =
            findViewById(R.id.btnRefresh)

        tvTransactionTitle =
            findViewById(R.id.tvTransactionTitle)

        tvServiceName =
            findViewById(R.id.tvServiceName)

        tvDescription =
            findViewById(R.id.tvDescription)

        requirementsContainer =
            findViewById(R.id.requirementsContainer)

        stepsContainer =
            findViewById(R.id.stepsContainer)

        departmentGuidelinesContainer =
            findViewById(
                R.id.departmentGuidelinesContainer
            )

        tvOffice =
            findViewById(R.id.tvOffice)

        tvSchedule =
            findViewById(R.id.tvSchedule)

        btnStartChecklist =
            findViewById(R.id.btnStartChecklist)

        detailsScrollView =
            findViewById(R.id.detailsScrollView)

        sectionAbout =
            findViewById(R.id.sectionAbout)

        sectionRequirements =
            findViewById(R.id.sectionRequirements)

        sectionProcedure =
            findViewById(R.id.sectionProcedure)

        sectionOfficeInformation =
            findViewById(
                R.id.sectionOfficeInformation
            )

        sectionOfficeSchedule =
            findViewById(
                R.id.sectionOfficeSchedule
            )

        sectionDepartmentGuidelines =
            findViewById(
                R.id.sectionDepartmentGuidelines
            )

        // =====================================================
        // ROOM DATABASE
        // =====================================================

        dao =
            ERequestDatabase
                .getDatabase(applicationContext)
                .eRequestDao()

        firestoreSyncManager =
            FirestoreSyncManager(applicationContext)

        // =====================================================
        // RECEIVE DATA
        // =====================================================

        transactionId =
            intent.getStringExtra(
                "TRANSACTION_ID"
            ) ?: ""

        val transactionName =
            intent.getStringExtra(
                "TRANSACTION_NAME"
            ) ?: "Transaction"

        val serviceName =
            intent.getStringExtra(
                "SERVICE_NAME"
            ) ?: "School Service"

        val targetSection =
            intent.getStringExtra(
                "TARGET_SECTION"
            )

        tvTransactionTitle.text =
            transactionName

        tvServiceName.text =
            serviceName

        // =====================================================
        // LOAD DETAILS
        // =====================================================

        lifecycleScope.launch {

            loadTransactionDetails(
                transactionId = transactionId,
                fallbackTransactionName = transactionName,
                fallbackServiceName = serviceName,
                targetSection = targetSection
            )
        }

        // =====================================================
        // BACK
        // =====================================================

        btnBack.setOnClickListener {

            finish()
        }

        // =====================================================
        // REFRESH
        // =====================================================

        btnRefresh.setOnClickListener {

            refreshData()
        }

        // =====================================================
        // CHECKLIST
        // =====================================================

        btnStartChecklist.setOnClickListener {

            val checklistIntent =
                Intent(
                    this,
                    RequirementChecklistActivity::class.java
                ).apply {

                    putExtra(
                        "TRANSACTION_ID",
                        transactionId
                    )

                    putExtra(
                        "TRANSACTION_NAME",
                        tvTransactionTitle.text.toString()
                    )
                }

            startActivity(
                checklistIntent
            )
        }
    }

    // =========================================================
    // REFRESH DATA
    // FIRESTORE -> ROOM -> CURRENT DETAILS PAGE
    // =========================================================

    private var isRefreshing = false
    private var activeToast: Toast? = null

    private fun showSingleToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        activeToast?.cancel()
        activeToast = Toast.makeText(this, message, duration).also { it.show() }
    }

    private fun refreshData() {
        if (isRefreshing || FirestoreSyncManager.isCurrentlySyncing()) return
        isRefreshing = true
        btnRefresh.isEnabled = false

        btnRefresh.animate()
            .rotationBy(360f)
            .setDuration(700)
            .start()

        showSingleToast("Refreshing...")

        lifecycleScope.launch {

            try {

                withContext(Dispatchers.IO) {

                    firestoreSyncManager.syncAllData()
                }

                loadTransactionDetails(
                    transactionId = transactionId,
                    fallbackTransactionName =
                        tvTransactionTitle.text.toString(),
                    fallbackServiceName =
                        tvServiceName.text.toString(),
                    targetSection = null
                )

                showSingleToast("Transaction details updated.")

            } catch (e: Exception) {

                e.printStackTrace()

                loadTransactionDetails(
                    transactionId = transactionId,
                    fallbackTransactionName =
                        tvTransactionTitle.text.toString(),
                    fallbackServiceName =
                        tvServiceName.text.toString(),
                    targetSection = null
                )

                showSingleToast("Unable to sync. Showing saved data.")

            } finally {
                isRefreshing = false
                btnRefresh.isEnabled = true
            }
        }
    }

    // =========================================================
    // LOAD TRANSACTION DETAILS
    // =========================================================

    private suspend fun loadTransactionDetails(
        transactionId: String,
        fallbackTransactionName: String,
        fallbackServiceName: String,
        targetSection: String?
    ) {

        val transaction =
            dao.getTransaction(
                transactionId
            )

        // =================================================
        // TRANSACTION NOT FOUND
        // =================================================

        if (transaction == null) {

            tvTransactionTitle.text =
                fallbackTransactionName

            tvServiceName.text =
                fallbackServiceName

            tvDescription.text =
                "Transaction information is currently unavailable."

            tvOffice.text =
                "Not available"

            tvSchedule.text =
                "Not available"

            displayRequirements(
                emptyList()
            )

            displaySteps(
                emptyList()
            )

            displayDepartmentGuidelines(
                emptyList()
            )

            scrollToTargetSection(
                targetSection
            )

            return
        }

        // =================================================
        // RELATED ROOM DATA
        // =================================================

        val service =
            dao.getService(
                transaction.serviceId
            )

        val requirements =
            dao.getRequirements(
                transactionId
            )
                .filter {
                    it.isActive
                }
                .sortedBy {
                    it.displayOrder
                }

        val procedureEntities =
            dao.getProcedureSteps(
                transactionId
            )
                .filter {
                    it.isActive
                }
                .sortedBy {
                    it.stepNumber
                }

        val guidelines =
            dao.getDepartmentGuidelines(
                transactionId
            )
                .filter {
                    it.isActive
                }
                .sortedBy {
                    it.displayOrder
                }

        // =================================================
        // BASIC INFORMATION
        // =================================================

        tvTransactionTitle.text =
            transaction.name

        tvServiceName.text =
            service?.name
                ?: fallbackServiceName

        tvDescription.text =
            if (
                transaction.description.isNotBlank()
            ) {

                transaction.description

            } else {

                "No description available."
            }

        tvOffice.text =
            if (
                transaction.officeName.isNotBlank()
            ) {

                transaction.officeName

            } else {

                "Not available"
            }

        tvSchedule.text =
            if (
                transaction.officeSchedule.isNotBlank()
            ) {

                transaction.officeSchedule

            } else {

                "Not available"
            }

        // =================================================
        // REQUIREMENTS
        // =================================================

        displayRequirements(
            requirements.map {
                it.requirementText
            }
        )

        // =================================================
        // PROCEDURE STEPS
        // =================================================

        val procedureSteps =
            procedureEntities.map { entity ->

                ProcedureStep(
                    id =
                        entity.id,

                    stepNumber =
                        entity.stepNumber,

                    instruction =
                        entity.instruction,

                    imageResource =
                        getLocalDrawableResource(
                            entity.localImagePath
                        ),

                    remoteImageUrl =
                        entity.remoteImageUrl,

                    localImagePath =
                        entity.localImagePath,

                    imageCaption =
                        entity.imageCaption,

                    websiteName =
                        entity.websiteName,

                    websiteUrl =
                        entity.websiteUrl
                )
            }

        displaySteps(
            procedureSteps
        )

        // =================================================
        // GUIDELINES
        // =================================================

        displayDepartmentGuidelines(
            guidelines.map {
                it.guidelineText
            }
        )

        // =================================================
        // OPTIONAL AUTO SCROLL
        // =================================================

        scrollToTargetSection(
            targetSection
        )
    }

    // =========================================================
    // GET OLD LOCAL DRAWABLE RESOURCE
    // =========================================================

    private fun getLocalDrawableResource(
        localImagePath: String?
    ): Int? {

        if (
            localImagePath.isNullOrBlank()
        ) {

            return null
        }

        val actualFile =
            File(localImagePath)

        if (
            actualFile.exists()
        ) {

            return null
        }

        val resourceName =
            localImagePath
                .substringAfterLast("/")
                .substringBeforeLast(".")

        val resourceId =
            resources.getIdentifier(
                resourceName,
                "drawable",
                packageName
            )

        return if (
            resourceId != 0
        ) {

            resourceId

        } else {

            null
        }
    }

    // =========================================================
    // LOAD BITMAP FROM DOWNLOADED OFFLINE FILE
    // =========================================================

    private suspend fun loadLocalBitmap(
        localImagePath: String
    ): Bitmap? {

        return withContext(
            Dispatchers.IO
        ) {

            try {

                val file =
                    File(
                        localImagePath
                    )

                if (
                    !file.exists() ||
                    file.length() <= 0L
                ) {

                    return@withContext null
                }

                BitmapFactory.decodeFile(
                    file.absolutePath
                )

            } catch (e: Exception) {

                e.printStackTrace()

                null
            }
        }
    }

    // =========================================================
    // DOWNLOAD REMOTE BITMAP AS FALLBACK
    // =========================================================

    private suspend fun downloadRemoteBitmap(
        remoteImageUrl: String
    ): Bitmap? {

        return withContext(
            Dispatchers.IO
        ) {

            var connection:
                    HttpURLConnection? = null

            try {

                val url =
                    URL(
                        remoteImageUrl
                    )

                connection =
                    url.openConnection()
                            as HttpURLConnection

                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    15000

                connection.readTimeout =
                    20000

                connection.instanceFollowRedirects =
                    true

                connection.doInput =
                    true

                connection.connect()

                if (
                    connection.responseCode !in
                    200..299
                ) {

                    return@withContext null
                }

                connection.inputStream.use {
                        inputStream ->

                    BitmapFactory.decodeStream(
                        inputStream
                    )
                }

            } catch (e: Exception) {

                e.printStackTrace()

                null

            } finally {

                connection?.disconnect()
            }
        }
    }

    // =========================================================
    // LOAD PROCEDURE IMAGE
    //
    // PRIORITY:
    // 1. DOWNLOADED OFFLINE FILE
    // 2. OLD DRAWABLE RESOURCE
    // 3. REMOTE CLOUDINARY FALLBACK
    // =========================================================

    private fun loadProcedureImage(
        step: ProcedureStep,
        imageView: ImageView,
        progressBar: ProgressBar?,
        onLoaded: ((Bitmap?) -> Unit)? = null
    ) {

        progressBar?.visibility =
            View.VISIBLE

        imageView.visibility =
            View.GONE

        lifecycleScope.launch {

            var loadedBitmap: Bitmap? =
                null

            // =================================================
            // 1. DOWNLOADED OFFLINE FILE
            // =================================================

            if (
                !step.localImagePath.isNullOrBlank()
            ) {

                loadedBitmap =
                    loadLocalBitmap(
                        step.localImagePath
                    )
            }

            if (
                loadedBitmap != null
            ) {

                progressBar?.visibility =
                    View.GONE

                imageView.setImageBitmap(
                    loadedBitmap
                )

                imageView.visibility =
                    View.VISIBLE

                onLoaded?.invoke(
                    loadedBitmap
                )

                return@launch
            }

            // =================================================
            // 2. OLD DRAWABLE RESOURCE
            // =================================================

            if (
                step.imageResource != null
            ) {

                progressBar?.visibility =
                    View.GONE

                imageView.setImageResource(
                    step.imageResource
                )

                imageView.visibility =
                    View.VISIBLE

                onLoaded?.invoke(
                    null
                )

                return@launch
            }

            // =================================================
            // 3. REMOTE CLOUDINARY FALLBACK
            // =================================================

            if (
                !step.remoteImageUrl.isNullOrBlank()
            ) {

                loadedBitmap =
                    downloadRemoteBitmap(
                        step.remoteImageUrl
                    )
            }

            progressBar?.visibility =
                View.GONE

            if (
                loadedBitmap != null
            ) {

                imageView.setImageBitmap(
                    loadedBitmap
                )

                imageView.visibility =
                    View.VISIBLE

            } else {

                imageView.visibility =
                    View.GONE
            }

            onLoaded?.invoke(
                loadedBitmap
            )
        }
    }

    // =========================================================
    // SCROLL TO TARGET SECTION
    // =========================================================

    private fun scrollToTargetSection(
        targetSection: String?
    ) {

        if (
            targetSection.isNullOrBlank()
        ) {

            return
        }

        val targetView =
            when (
                targetSection.uppercase()
            ) {

                "ABOUT" ->
                    sectionAbout

                "REQUIREMENTS" ->
                    sectionRequirements

                "PROCEDURE",
                "STEP_BY_STEP_PROCEDURE",
                "STEPS" ->
                    sectionProcedure

                "OFFICE_INFORMATION" ->
                    sectionOfficeInformation

                "OFFICE_SCHEDULE" ->
                    sectionOfficeSchedule

                "DEPARTMENT_GUIDELINES" ->
                    sectionDepartmentGuidelines

                else ->
                    null
            }

        targetView ?: return

        detailsScrollView.post {

            detailsScrollView
                .smoothScrollTo(
                    0,
                    targetView.top
                )
        }
    }

    // =========================================================
    // DISPLAY REQUIREMENTS
    // =========================================================

    private fun displayRequirements(
        requirements: List<String>
    ) {

        requirementsContainer
            .removeAllViews()

        if (
            requirements.isEmpty()
        ) {

            val textView =
                TextView(this).apply {

                    text =
                        "No requirements available."

                    textSize =
                        14f

                    setTextColor(
                        getColor(
                            R.color.text_secondary
                        )
                    )

                    setPadding(
                        0,
                        10.dp(),
                        0,
                        10.dp()
                    )
                }

            requirementsContainer
                .addView(
                    textView
                )

            return
        }

        requirements.forEach {
                requirement ->

            val textView =
                TextView(this).apply {

                    text =
                        "• $requirement"

                    textSize =
                        14f

                    setTextColor(
                        getColor(
                            R.color.text_primary
                        )
                    )

                    setPadding(
                        0,
                        10.dp(),
                        0,
                        10.dp()
                    )
                }

            requirementsContainer
                .addView(
                    textView
                )
        }
    }

    // =========================================================
    // DISPLAY PROCEDURE STEPS
    // =========================================================

    private fun displaySteps(
        steps: List<ProcedureStep>
    ) {

        stepsContainer
            .removeAllViews()

        if (
            steps.isEmpty()
        ) {

            val textView =
                TextView(this).apply {

                    text =
                        "No procedure available."

                    textSize =
                        14f

                    setTextColor(
                        getColor(
                            R.color.text_secondary
                        )
                    )

                    setPadding(
                        0,
                        10.dp(),
                        0,
                        10.dp()
                    )
                }

            stepsContainer.addView(
                textView
            )

            return
        }

        steps
            .sortedBy {
                it.stepNumber
            }
            .forEach { step ->

                val stepContainer =
                    LinearLayout(this).apply {

                        orientation =
                            LinearLayout.VERTICAL

                        setPadding(
                            0,
                            12.dp(),
                            0,
                            12.dp()
                        )
                    }

                // =============================================
                // STEP TEXT
                // =============================================

                val stepText =
                    TextView(this).apply {

                        text =
                            "${step.stepNumber}.  ${step.instruction}"

                        textSize =
                            14f

                        setTextColor(
                            getColor(
                                R.color.text_primary
                            )
                        )
                    }

                stepContainer.addView(
                    stepText
                )

                // =============================================
                // OPTIONAL WEBSITE LINK
                // =============================================

                if (
                    !step.websiteUrl
                        .isNullOrBlank()
                ) {

                    val websiteLink =
                        TextView(this).apply {

                            text =
                                "${step.websiteName?.trim()?.takeIf { it.isNotBlank() } ?: "Open Website"} ↗"

                            textSize =
                                14f

                            setTextColor(
                                getColor(
                                    R.color.primary
                                )
                            )

                            setPadding(
                                0,
                                8.dp(),
                                0,
                                2.dp()
                            )

                            isClickable =
                                true

                            isFocusable =
                                true

                            setOnClickListener {

                                openWebsite(
                                    step.websiteUrl
                                )
                            }
                        }

                    stepContainer.addView(
                        websiteLink
                    )
                }

                // =============================================
                // DETERMINE IF THIS STEP HAS AN IMAGE
                // =============================================

                val downloadedFileExists =
                    if (
                        !step.localImagePath.isNullOrBlank()
                    ) {

                        try {

                            val file =
                                File(
                                    step.localImagePath
                                )

                            file.exists() &&
                                    file.length() > 0L

                        } catch (e: Exception) {

                            false
                        }

                    } else {

                        false
                    }

                val hasDrawable =
                    step.imageResource != null

                val hasRemote =
                    !step.remoteImageUrl
                        .isNullOrBlank()

                val hasImage =
                    downloadedFileExists ||
                            hasDrawable ||
                            hasRemote

                // =============================================
                // OPTIONAL IMAGE
                // =============================================

                if (hasImage) {

                    val progressBar =
                        ProgressBar(this).apply {

                            isIndeterminate =
                                true

                            layoutParams =
                                LinearLayout.LayoutParams(
                                    36.dp(),
                                    36.dp()
                                ).apply {

                                    gravity =
                                        Gravity.CENTER_HORIZONTAL

                                    topMargin =
                                        12.dp()
                                }
                        }

                    val imageView =
                        ImageView(this).apply {

                            adjustViewBounds =
                                true

                            scaleType =
                                ImageView.ScaleType.FIT_CENTER

                            contentDescription =
                                step.imageCaption
                                    ?: "Procedure reference image"

                            isClickable =
                                true

                            isFocusable =
                                true

                            layoutParams =
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {

                                    topMargin =
                                        12.dp()
                                }
                        }

                    stepContainer.addView(
                        progressBar
                    )

                    stepContainer.addView(
                        imageView
                    )

                    loadProcedureImage(
                        step =
                            step,

                        imageView =
                            imageView,

                        progressBar =
                            progressBar
                    )

                    imageView.setOnClickListener {

                        showImagePreview(
                            step
                        )
                    }

                    // =========================================
                    // OPTIONAL CAPTION
                    // =========================================

                    if (
                        !step.imageCaption
                            .isNullOrBlank()
                    ) {

                        val caption =
                            TextView(this).apply {

                                text =
                                    step.imageCaption

                                textSize =
                                    12f

                                setTextColor(
                                    getColor(
                                        R.color.text_secondary
                                    )
                                )

                                setPadding(
                                    0,
                                    6.dp(),
                                    0,
                                    0
                                )
                            }

                        stepContainer.addView(
                            caption
                        )
                    }

                    val tapHint =
                        TextView(this).apply {

                            text =
                                "Tap image to enlarge and zoom"

                            textSize =
                                11f

                            setTextColor(
                                getColor(
                                    R.color.primary
                                )
                            )

                            setPadding(
                                0,
                                4.dp(),
                                0,
                                0
                            )
                        }

                    stepContainer.addView(
                        tapHint
                    )
                }

                stepsContainer.addView(
                    stepContainer
                )
            }
    }

    // =========================================================
    // IMAGE PREVIEW (PINCH-TO-ZOOM & TAP-TO-ZOOM)
    // =========================================================

    private fun showImagePreview(
        step: ProcedureStep
    ) {

        val dialog =
            Dialog(this)

        dialog.requestWindowFeature(
            Window.FEATURE_NO_TITLE
        )

        dialog.setContentView(
            R.layout.dialog_image_preview
        )

        val tvPreviewTitle =
            dialog.findViewById<TextView>(
                R.id.tvPreviewTitle
            )

        val btnClosePreview =
            dialog.findViewById<TextView>(
                R.id.btnClosePreview
            )

        val progressBar =
            dialog.findViewById<ProgressBar>(
                R.id.pbPreviewLoading
            )

        val imageView =
            dialog.findViewById<ZoomableImageView>(
                R.id.ivZoomablePreview
            )

        tvPreviewTitle.text =
            if (!step.imageCaption.isNullOrBlank()) {
                step.imageCaption
            } else {
                "Image Preview"
            }

        imageView.contentDescription =
            step.imageCaption ?: "Image Preview"

        btnClosePreview.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.apply {

            setBackgroundDrawableResource(
                android.R.color.transparent
            )
        }

        dialog.setOnShowListener {

            dialog.window?.setLayout(
                (
                        resources
                            .displayMetrics
                            .widthPixels *
                                0.95
                        ).toInt(),

                (
                        resources
                            .displayMetrics
                            .heightPixels *
                                0.85
                        ).toInt()
            )
        }

        dialog.show()

        loadProcedureImage(
            step =
                step,

            imageView =
                imageView,

            progressBar =
                progressBar
        ) {
            imageView.post {
                imageView.resetToFitCenter()
            }
        }
    }

    // =========================================================
    // OPEN OPTIONAL WEBSITE
    // =========================================================

    private fun openWebsite(
        websiteUrl: String?
    ) {

        if (
            websiteUrl.isNullOrBlank()
        ) {

            return
        }

        try {

            val uri =
                Uri.parse(
                    websiteUrl.trim()
                )

            val scheme =
                uri.scheme
                    ?.lowercase()

            if (
                scheme != "http" &&
                scheme != "https"
            ) {

                Toast.makeText(
                    this,
                    "Invalid website URL.",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            val browserIntent =
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )

            startActivity(
                browserIntent
            )

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                this,
                "Unable to open website.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // =========================================================
    // DISPLAY DEPARTMENT GUIDELINES
    // =========================================================

    private fun displayDepartmentGuidelines(
        departmentGuidelines: List<String>
    ) {

        departmentGuidelinesContainer
            .removeAllViews()

        if (
            departmentGuidelines.isEmpty()
        ) {

            val textView =
                TextView(this).apply {

                    text =
                        "No department guidelines available."

                    textSize =
                        14f

                    setTextColor(
                        getColor(
                            R.color.text_secondary
                        )
                    )

                    setPadding(
                        0,
                        10.dp(),
                        0,
                        10.dp()
                    )
                }

            departmentGuidelinesContainer
                .addView(
                    textView
                )

            return
        }

        departmentGuidelines.forEach {
                guideline ->

            val textView =
                TextView(this).apply {

                    text =
                        "• $guideline"

                    textSize =
                        14f

                    setTextColor(
                        getColor(
                            R.color.text_secondary
                        )
                    )

                    setPadding(
                        0,
                        10.dp(),
                        0,
                        10.dp()
                    )
                }

            departmentGuidelinesContainer
                .addView(
                    textView
                )
        }
    }

    // =========================================================
    // DP CONVERTER
    // =========================================================

    private fun Int.dp(): Int {

        return (
                this *
                        resources
                            .displayMetrics
                            .density
                ).toInt()
    }
}