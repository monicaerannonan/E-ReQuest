package com.example.request.features.auth

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.request.R
import com.example.request.data.sync.CatalogCryptoUtils
import com.example.request.features.home.HomeActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.util.Calendar

class StudentAuthActivity : AppCompatActivity() {

    private lateinit var authRepository: StudentAuthRepository
    private lateinit var sessionManager: StudentSessionManager

    // Mode 1: GCash-style Returning View
    private lateinit var layoutReturningGcashStyle: View
    private lateinit var btnStudentNumberPill: View
    private lateinit var tvActiveStudentNumber: TextView
    private lateinit var tvAuthOfflineBadge: TextView
    private lateinit var cardPinLogin: MaterialCardView
    private lateinit var tvForgotPin: TextView
    private lateinit var tvAuthVersion: TextView

    // Mode 2: Enrollment / Add Account / OTP / PIN Setup View
    private lateinit var scrollEnrollmentContainer: ScrollView
    private lateinit var btnBackToQuickLogin: TextView
    private lateinit var stepStudentInput: View
    private lateinit var stepOtpVerification: View
    private lateinit var stepPinSetup: View
    private lateinit var progressAuthAction: ProgressBar

    // Sub-step 1 Views
    private lateinit var tabRegister: TextView
    private lateinit var tabExistingAccount: TextView
    private lateinit var tvEnrollmentSubtitle: TextView
    private lateinit var btnSelectCampus: View
    private lateinit var tvSelectedCampus: TextView
    private lateinit var btnSelectYear: View
    private lateinit var tvSelectedYear: TextView
    private lateinit var etStudentDigits: EditText
    private lateinit var tvStudentNumberPreview: TextView
    private lateinit var layoutEmailInput: View
    private lateinit var etStudentEmail: EditText
    private lateinit var layoutDataPrivacyConsentCard: View
    private lateinit var cbDataPrivacyConsent: CheckBox
    private lateinit var tvDataPrivacyConsentLabel: TextView
    private lateinit var btnViewDataPrivacyNotice: TextView
    private lateinit var tvDailySlotStatus: TextView
    private lateinit var btnRequestOtp: MaterialButton

    // Sub-step 2 Views
    private lateinit var tvOtpSentInfo: TextView
    private lateinit var cardDevOtpBanner: View
    private lateinit var tvDevOtpCode: TextView
    private lateinit var etOtpCode: EditText
    private lateinit var btnVerifyOtp: MaterialButton
    private lateinit var btnChangeStudentDetails: TextView
    private lateinit var btnResendOtp: TextView

    // Sub-step 3 Views
    private lateinit var tvPinSetupTitle: TextView
    private lateinit var tvPinSetupSubtitle: TextView
    private lateinit var etNewPin: EditText
    private lateinit var etConfirmPin: EditText
    private lateinit var btnSavePin: MaterialButton

    // Selected Dropdown Values
    private var selectedCampusCode: String = StudentNumberValidator.CAMPUS_CODES.first()
    private var selectedYear: String = Calendar.getInstance().get(Calendar.YEAR)
        .coerceIn(StudentNumberValidator.MIN_YEAR, StudentNumberValidator.DEFAULT_MAX_YEAR)
        .toString()

    // Transient UI State
    private var isRegisterTabSelected = true
    private var pendingStudentNumber = ""
    private var pendingEmail = ""
    private var currentOtpPurpose = OtpPurpose.REGISTER

    private var otpResendTimer: CountDownTimer? = null
    private var keypadLockoutTimer: CountDownTimer? = null
    private var currentPrivacyDialog: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_auth)

        authRepository = StudentAuthRepository(this)
        sessionManager = authRepository.sessionManager

        bindViews()
        setupCampusAndYearSelectors()
        setupListeners()
        setupBackNavigation()

        refreshInitialScreenState()
    }

    override fun onResume() {
        super.onResume()
        updateOfflineBadgeVisibility()
        updateDailySlotStatusLabel()
        refreshDailyUsageFromServer()
    }

    /**
     * Pulls today's real device usage from Firestore (survives reinstall / clear data)
     * so "Today's account usage: X / 3" always matches the server.
     */
    private fun refreshDailyUsageFromServer() {
        lifecycleScope.launch {
            val synced = authRepository.syncDailyUsageFromServer()
            if (synced != null && !isFinishing && !isDestroyed) {
                updateDailySlotStatusLabel()
                if (sessionManager.getSavedAccounts().isEmpty() &&
                    synced >= DailyDeviceSwitchPolicy.MAX_DISTINCT_ACCOUNTS_PER_DAY &&
                    isRegisterTabSelected
                ) {
                    selectEnrollmentTab(isRegister = false)
                }
            }
        }
    }

    override fun onDestroy() {
        otpResendTimer?.cancel()
        keypadLockoutTimer?.cancel()
        currentPrivacyDialog?.dismiss()
        currentPrivacyDialog = null
        super.onDestroy()
    }

    private fun bindViews() {
        layoutReturningGcashStyle = findViewById(R.id.layoutReturningGcashStyle)
        btnStudentNumberPill = findViewById(R.id.btnStudentNumberPill)
        tvActiveStudentNumber = findViewById(R.id.tvActiveStudentNumber)
        tvAuthOfflineBadge = findViewById(R.id.tvAuthOfflineBadge)
        cardPinLogin = findViewById(R.id.cardPinLogin)
        tvForgotPin = findViewById(R.id.tvForgotPin)
        tvAuthVersion = findViewById(R.id.tvAuthVersion)

        scrollEnrollmentContainer = findViewById(R.id.scrollEnrollmentContainer)
        btnBackToQuickLogin = findViewById(R.id.btnBackToQuickLogin)
        stepStudentInput = findViewById(R.id.stepStudentInput)
        stepOtpVerification = findViewById(R.id.stepOtpVerification)
        stepPinSetup = findViewById(R.id.stepPinSetup)
        progressAuthAction = findViewById(R.id.progressAuthAction)

        tabRegister = findViewById(R.id.tabRegister)
        tabExistingAccount = findViewById(R.id.tabExistingAccount)
        tvEnrollmentSubtitle = findViewById(R.id.tvEnrollmentSubtitle)
        btnSelectCampus = findViewById(R.id.btnSelectCampus)
        tvSelectedCampus = findViewById(R.id.tvSelectedCampus)
        btnSelectYear = findViewById(R.id.btnSelectYear)
        tvSelectedYear = findViewById(R.id.tvSelectedYear)
        etStudentDigits = findViewById(R.id.etStudentDigits)
        tvStudentNumberPreview = findViewById(R.id.tvStudentNumberPreview)
        layoutEmailInput = findViewById(R.id.layoutEmailInput)
        etStudentEmail = findViewById(R.id.etStudentEmail)
        layoutDataPrivacyConsentCard = findViewById(R.id.layoutDataPrivacyConsentCard)
        cbDataPrivacyConsent = findViewById(R.id.cbDataPrivacyConsent)
        tvDataPrivacyConsentLabel = findViewById(R.id.tvDataPrivacyConsentLabel)
        btnViewDataPrivacyNotice = findViewById(R.id.btnViewDataPrivacyNotice)
        tvDailySlotStatus = findViewById(R.id.tvDailySlotStatus)
        btnRequestOtp = findViewById(R.id.btnRequestOtp)

        tvOtpSentInfo = findViewById(R.id.tvOtpSentInfo)
        cardDevOtpBanner = findViewById(R.id.cardDevOtpBanner)
        tvDevOtpCode = findViewById(R.id.tvDevOtpCode)
        etOtpCode = findViewById(R.id.etOtpCode)
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp)
        btnChangeStudentDetails = findViewById(R.id.btnChangeStudentDetails)
        btnResendOtp = findViewById(R.id.btnResendOtp)

        tvPinSetupTitle = findViewById(R.id.tvPinSetupTitle)
        tvPinSetupSubtitle = findViewById(R.id.tvPinSetupSubtitle)
        etNewPin = findViewById(R.id.etNewPin)
        etConfirmPin = findViewById(R.id.etConfirmPin)
        btnSavePin = findViewById(R.id.btnSavePin)

        val versionName = DeviceInfoProvider.getAppVersionName(this)
        tvAuthVersion.text = "v$versionName"

        val rootView = findViewById<View>(R.id.studentAuthRoot)
        val topBrandView = findViewById<View>(R.id.layoutTopBrand)
        val bottomSheetView = findViewById<View>(R.id.panelBottomLoginSheet)
        val density = resources.displayMetrics.density
        val baseTopPad = (20 * density).toInt()
        val baseBottomPad = (18 * density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBrandView.setPadding(
                topBrandView.paddingLeft,
                baseTopPad + systemBars.top,
                topBrandView.paddingRight,
                topBrandView.paddingBottom
            )
            bottomSheetView.setPadding(
                bottomSheetView.paddingLeft,
                bottomSheetView.paddingTop,
                bottomSheetView.paddingRight,
                baseBottomPad + systemBars.bottom
            )
            insets
        }
    }

    private fun setupCampusAndYearSelectors() {
        tvSelectedCampus.text = selectedCampusCode
        tvSelectedYear.text = selectedYear

        btnSelectCampus.setOnClickListener {
            showOptionPickerBottomSheet(
                title = "Select Campus Code",
                options = StudentNumberValidator.CAMPUS_CODES,
                currentSelection = selectedCampusCode
            ) { chosen ->
                selectedCampusCode = chosen
                tvSelectedCampus.text = chosen
                updateStudentNumberPreview()
            }
        }

        btnSelectYear.setOnClickListener {
            val years = StudentNumberValidator.getAvailableYears()
            showOptionPickerBottomSheet(
                title = "Select Year (2000 - ${years.last()})",
                options = years,
                currentSelection = selectedYear
            ) { chosen ->
                selectedYear = chosen
                tvSelectedYear.text = chosen
                updateStudentNumberPreview()
            }
        }

        etStudentDigits.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateStudentNumberPreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        updateStudentNumberPreview()
    }

    /**
     * High-contrast white BottomSheet picker so Campus (AU..XX) and Year (2000..2040)
     * are 100% readable in both Light Mode and Dark Mode, with clear active highlight.
     */
    private fun showOptionPickerBottomSheet(
        title: String,
        options: List<String>,
        currentSelection: String,
        onSelected: (String) -> Unit
    ) {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.dialog_option_picker, null)
        dialog.setContentView(sheetView)

        val tvTitle = sheetView.findViewById<TextView>(R.id.tvPickerTitle)
        val scrollView = sheetView.findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollPickerOptions)
        val container = sheetView.findViewById<LinearLayout>(R.id.containerPickerOptions)

        tvTitle.text = title
        val density = resources.displayMetrics.density
        var selectedRowView: View? = null

        for (item in options) {
            val isSelected = item.equals(currentSelection, ignoreCase = true)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = (16 * density).toInt()
                val padV = (14 * density).toInt()
                setPadding(padH, padV, padH, padV)

                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12f * density
                    setColor(if (isSelected) Color.parseColor("#EBF3FE") else Color.parseColor("#F8FAFC"))
                    setStroke(
                        (1.2f * density).toInt(),
                        if (isSelected) Color.parseColor("#155EC4") else Color.parseColor("#E2E8F0")
                    )
                }

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (8 * density).toInt()
                }
            }

            val tvOption = TextView(this).apply {
                text = item
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(
                    if (isSelected) Color.parseColor("#155EC4")
                    else Color.parseColor("#102A3E")
                )
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val tvCheck = TextView(this).apply {
                text = if (isSelected) "✓ Selected" else ""
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#155EC4"))
            }

            row.addView(tvOption)
            row.addView(tvCheck)

            row.setOnClickListener {
                onSelected(item)
                dialog.dismiss()
            }

            if (isSelected) {
                selectedRowView = row
            }

            container.addView(row)
        }

        selectedRowView?.let { target ->
            scrollView.post {
                val scrollY = (target.top - (80 * density).toInt()).coerceAtLeast(0)
                scrollView.scrollTo(0, scrollY)
            }
        }

        dialog.show()
    }

    private fun updateStudentNumberPreview() {
        val digitsRaw = etStudentDigits.text?.toString()?.trim().orEmpty()
        val displayDigits = if (digitsRaw.isEmpty()) "00000" else digitsRaw.padEnd(5, '•')
        tvStudentNumberPreview.text = "Student Number: $selectedCampusCode-$selectedYear-$displayDigits"
    }

    private fun setupListeners() {
        cardPinLogin.setOnClickListener {
            val activeAccount = sessionManager.getActiveAccount()
            if (activeAccount != null) {
                showPinKeypadBottomSheet(activeAccount.studentNumber)
            } else {
                showEnrollmentScreen(registerMode = true)
            }
        }

        btnStudentNumberPill.setOnClickListener {
            showSwitchAccountBottomSheet()
        }

        tvForgotPin.setOnClickListener {
            val activeAccount = sessionManager.getActiveAccount()
            if (activeAccount != null) {
                initiateForgotPinFlow(activeAccount.studentNumber)
            }
        }

        btnBackToQuickLogin.setOnClickListener {
            if (sessionManager.getActiveAccount() != null) {
                showReturningQuickLoginScreen()
            }
        }

        tabRegister.setOnClickListener {
            val usedCount = sessionManager.getDailyUsedStudentsToday().size
            if (usedCount >= DailyDeviceSwitchPolicy.MAX_DISTINCT_ACCOUNTS_PER_DAY) {
                Toast.makeText(
                    this,
                    "Device limit reached (3/3). This device cannot register a new account. Please use the Existing Account tab.",
                    Toast.LENGTH_LONG
                ).show()
                selectEnrollmentTab(isRegister = false)
            } else {
                selectEnrollmentTab(isRegister = true)
            }
        }

        tabExistingAccount.setOnClickListener {
            selectEnrollmentTab(isRegister = false)
        }

        btnRequestOtp.setOnClickListener {
            handleRequestOtpClick()
        }

        cbDataPrivacyConsent.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setDataPrivacyAccepted(isChecked)
            updateRegisterButtonState()
        }

        tvDataPrivacyConsentLabel.setOnClickListener {
            cbDataPrivacyConsent.toggle()
        }

        btnViewDataPrivacyNotice.setOnClickListener {
            showDataPrivacyBottomSheet()
        }

        cardDevOtpBanner.setOnClickListener {
            val code = tvDevOtpCode.text?.toString()?.trim().orEmpty()
            if (code.length == 6) {
                etOtpCode.setText(code)
                etOtpCode.setSelection(code.length)
            }
        }

        btnVerifyOtp.setOnClickListener {
            handleVerifyOtpClick()
        }

        btnChangeStudentDetails.setOnClickListener {
            otpResendTimer?.cancel()
            if (currentOtpPurpose == OtpPurpose.RESET_PIN && sessionManager.getActiveAccount() != null) {
                showReturningQuickLoginScreen()
            } else {
                showEnrollmentSubStep(step = 1)
            }
        }

        btnResendOtp.setOnClickListener {
            handleResendOtpClick()
        }

        btnSavePin.setOnClickListener {
            handleSavePinClick()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (scrollEnrollmentContainer.visibility == View.VISIBLE &&
                    sessionManager.getActiveAccount() != null
                ) {
                    otpResendTimer?.cancel()
                    showReturningQuickLoginScreen()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun refreshInitialScreenState() {
        val savedAccounts = sessionManager.getSavedAccounts()
        if (savedAccounts.isNotEmpty()) {
            val mostRecent = savedAccounts.first()
            sessionManager.setActiveStudentNumber(mostRecent.studentNumber)
            tvActiveStudentNumber.text = mostRecent.studentNumber
            showReturningQuickLoginScreen()
        } else {
            val isDeviceAtLimit = sessionManager.getDailyUsedStudentsToday().size >=
                DailyDeviceSwitchPolicy.MAX_DISTINCT_ACCOUNTS_PER_DAY
            showEnrollmentScreen(registerMode = !isDeviceAtLimit)
        }
    }

    private fun showReturningQuickLoginScreen() {
        val mostRecent = sessionManager.getSavedAccounts().firstOrNull()
        if (mostRecent == null) {
            val isDeviceAtLimit = sessionManager.getDailyUsedStudentsToday().size >=
                DailyDeviceSwitchPolicy.MAX_DISTINCT_ACCOUNTS_PER_DAY
            showEnrollmentScreen(registerMode = !isDeviceAtLimit)
            return
        }

        sessionManager.setActiveStudentNumber(mostRecent.studentNumber)
        tvActiveStudentNumber.text = mostRecent.studentNumber
        updateOfflineBadgeVisibility()

        scrollEnrollmentContainer.visibility = View.GONE
        layoutReturningGcashStyle.visibility = View.VISIBLE
    }

    private fun showEnrollmentScreen(registerMode: Boolean) {
        layoutReturningGcashStyle.visibility = View.GONE
        scrollEnrollmentContainer.visibility = View.VISIBLE

        val hasSavedAccount = sessionManager.getSavedAccounts().isNotEmpty()
        btnBackToQuickLogin.visibility = if (hasSavedAccount) View.VISIBLE else View.GONE

        if (registerMode) {
            sessionManager.setDataPrivacyAccepted(false)
            cbDataPrivacyConsent.isChecked = false
        }
        selectEnrollmentTab(isRegister = registerMode)
        showEnrollmentSubStep(step = 1)
        updateDailySlotStatusLabel()
        refreshDailyUsageFromServer()
        updateRegisterButtonState()

        if (registerMode && !sessionManager.isDataPrivacyAccepted()) {
            scrollEnrollmentContainer.postDelayed({
                if (!isFinishing && !isDestroyed && isRegisterTabSelected && !sessionManager.isDataPrivacyAccepted()) {
                    showDataPrivacyBottomSheet()
                }
            }, 300)
        }
    }

    private fun selectEnrollmentTab(isRegister: Boolean) {
        isRegisterTabSelected = isRegister

        if (isRegister) {
            tabRegister.setBackgroundResource(R.drawable.bg_nav_pill_active)
            tabRegister.setTextColor(ContextCompat.getColor(this, R.color.primary))
            tabExistingAccount.background = null
            tabExistingAccount.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            layoutEmailInput.visibility = View.VISIBLE
            layoutDataPrivacyConsentCard.visibility = View.VISIBLE
            cbDataPrivacyConsent.isChecked = sessionManager.isDataPrivacyAccepted()
            tvEnrollmentSubtitle.text =
                "Select Campus and Year, enter your 5-digit number and Email Address to receive an OTP."
            btnRequestOtp.text = "Send OTP to Email"
        } else {
            tabExistingAccount.setBackgroundResource(R.drawable.bg_nav_pill_active)
            tabExistingAccount.setTextColor(ContextCompat.getColor(this, R.color.primary))
            tabRegister.background = null
            tabRegister.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            layoutEmailInput.visibility = View.GONE
            layoutDataPrivacyConsentCard.visibility = View.GONE
            tvEnrollmentSubtitle.text =
                "Enter your registered Student Number. A 6-digit OTP will be sent to your registered email."
            btnRequestOtp.text = "Send Login OTP"
        }
        updateRegisterButtonState()
    }

    private fun updateRegisterButtonState() {
        if (isRegisterTabSelected) {
            val isAccepted = cbDataPrivacyConsent.isChecked
            btnRequestOtp.alpha = if (isAccepted) 1.0f else 0.55f
        } else {
            btnRequestOtp.alpha = 1.0f
        }
    }

    private fun showDataPrivacyBottomSheet() {
        if (currentPrivacyDialog?.isShowing == true) return

        val dialog = BottomSheetDialog(this)
        currentPrivacyDialog = dialog
        val sheetView = layoutInflater.inflate(R.layout.dialog_data_privacy_notice, null)
        dialog.setContentView(sheetView)

        val btnAccept = sheetView.findViewById<MaterialButton>(R.id.btnAcceptDataPrivacy)
        val btnDecline = sheetView.findViewById<TextView>(R.id.btnDeclineDataPrivacy)

        btnAccept?.setOnClickListener {
            sessionManager.setDataPrivacyAccepted(true)
            cbDataPrivacyConsent.isChecked = true
            updateRegisterButtonState()
            dialog.dismiss()
            Toast.makeText(
                this,
                "Data Privacy Act accepted. You may now tap Send OTP to proceed.",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnDecline?.setOnClickListener {
            cbDataPrivacyConsent.isChecked = false
            sessionManager.setDataPrivacyAccepted(false)
            updateRegisterButtonState()
            dialog.dismiss()
            Toast.makeText(
                this,
                "You must accept the Data Privacy Act notice to proceed with registration.",
                Toast.LENGTH_SHORT
            ).show()
        }

        dialog.setOnDismissListener {
            currentPrivacyDialog = null
        }

        dialog.show()
    }

    private fun showEnrollmentSubStep(step: Int) {
        stepStudentInput.visibility = if (step == 1) View.VISIBLE else View.GONE
        stepOtpVerification.visibility = if (step == 2) View.VISIBLE else View.GONE
        stepPinSetup.visibility = if (step == 3) View.VISIBLE else View.GONE

        if (step == 2) {
            if (currentOtpPurpose != OtpPurpose.RESET_PIN) {
                btnChangeStudentDetails.text = "Change Student Details"
            }
            val lockoutRem = sessionManager.getOtpLockoutRemainingSeconds(pendingStudentNumber)
            if (lockoutRem > 0L) {
                startOtpLockoutCooldown(lockoutRem.toInt())
            }
        }
    }

    private fun updateOfflineBadgeVisibility() {
        val offline = !authRepository.isOnline()
        tvAuthOfflineBadge.visibility = if (offline) View.VISIBLE else View.GONE
    }

    private fun updateDailySlotStatusLabel() {
        val usedCount = sessionManager.getDailyUsedStudentsToday().size
        val maxLimit = DailyDeviceSwitchPolicy.MAX_DISTINCT_ACCOUNTS_PER_DAY
        tvDailySlotStatus.text = when (usedCount) {
            0 -> "Accounts on this device: 0 / $maxLimit"
            1 -> "Accounts on this device: 1 / $maxLimit"
            2 -> "Accounts on this device: 2 / $maxLimit (1 slot left)"
            else -> "Accounts on this device: $maxLimit / $maxLimit (Device limit reached)"
        }
    }

    // =====================================================
    // STEP 1: REQUEST EMAIL OTP
    // =====================================================

    private fun handleRequestOtpClick() {
        val digits = etStudentDigits.text?.toString().orEmpty()

        val validation = StudentNumberValidator.validateStudentNumberParts(
            campusCode = selectedCampusCode,
            year = selectedYear,
            digits = digits
        )

        val formattedStudentNumber = when (validation) {
            is StudentNumberValidator.ValidationResult.Invalid -> {
                Toast.makeText(this, validation.message, Toast.LENGTH_LONG).show()
                return
            }
            is StudentNumberValidator.ValidationResult.Valid -> validation.formattedStudentNumber
        }

        if (isRegisterTabSelected) {
            if (!cbDataPrivacyConsent.isChecked) {
                Toast.makeText(
                    this,
                    "Please check 'I have read and agree to the Data Privacy Act' to proceed.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val email = etStudentEmail.text?.toString()?.trim().orEmpty()
            if (!StudentNumberValidator.isValidEmail(email)) {
                etStudentEmail.error = "Please enter a valid email address"
                etStudentEmail.requestFocus()
                return
            }

            currentOtpPurpose = OtpPurpose.REGISTER
            setLoadingState(true)
            lifecycleScope.launch {
                val result = authRepository.requestRegistrationOtp(
                    studentNumber = formattedStudentNumber,
                    email = email
                )
                setLoadingState(false)
                handleOtpRequestResult(result)
            }
        } else {
            currentOtpPurpose = OtpPurpose.EXISTING_ACCOUNT_LOGIN
            setLoadingState(true)
            lifecycleScope.launch {
                val result = authRepository.requestExistingAccountOtp(
                    studentNumber = formattedStudentNumber
                )
                setLoadingState(false)
                handleOtpRequestResult(result)
            }
        }
    }

    private fun initiateForgotPinFlow(studentNumber: String) {
        if (!authRepository.isOnline()) {
            Toast.makeText(
                this,
                "An internet connection is required to reset your PIN via Email OTP.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        currentOtpPurpose = OtpPurpose.RESET_PIN
        pendingStudentNumber = studentNumber

        val savedAccount = sessionManager.getSavedAccounts()
            .firstOrNull { it.studentNumber.equals(studentNumber, ignoreCase = true) }
        val existingSession = sessionManager.getOtpSession(studentNumber)
        val knownEmail = CatalogCryptoUtils.decrypt(existingSession?.email ?: savedAccount?.email.orEmpty()).trim()
        pendingEmail = knownEmail

        // Direct IMMEDIATELY to Step 2 (Verify Email OTP)
        layoutReturningGcashStyle.visibility = View.GONE
        scrollEnrollmentContainer.visibility = View.VISIBLE
        btnBackToQuickLogin.visibility = View.VISIBLE
        showEnrollmentSubStep(step = 2)

        etOtpCode.setText("")
        val displayEmail = if (knownEmail.isNotBlank()) knownEmail else "your registered email"
        tvOtpSentInfo.text = "Sending a 6-digit OTP for $studentNumber to $displayEmail..."
        btnChangeStudentDetails.text = "Cancel & Back to PIN Login"

        val nowMillis = System.currentTimeMillis()
        if (existingSession != null && existingSession.resendAvailableAtMillis > nowMillis) {
            val remainingSec = ((existingSession.resendAvailableAtMillis - nowMillis) + 999L) / 1000L
            startOtpResendCooldown(remainingSec.toInt())
        }

        setLoadingState(true)
        lifecycleScope.launch {
            val result = authRepository.requestPinResetOtp(studentNumber)
            setLoadingState(false)
            if (result is OtpRequestResult.Error) {
                Toast.makeText(this@StudentAuthActivity, result.message, Toast.LENGTH_LONG).show()
            } else {
                handleOtpRequestResult(result)
            }
        }
    }

    private fun handleResendOtpClick() {
        if (pendingStudentNumber.isBlank()) return

        setLoadingState(true)
        lifecycleScope.launch {
            val result = when (currentOtpPurpose) {
                OtpPurpose.REGISTER -> authRepository.requestRegistrationOtp(
                    studentNumber = pendingStudentNumber,
                    email = pendingEmail
                )
                OtpPurpose.EXISTING_ACCOUNT_LOGIN -> authRepository.requestExistingAccountOtp(
                    studentNumber = pendingStudentNumber
                )
                OtpPurpose.RESET_PIN -> authRepository.requestPinResetOtp(
                    studentNumber = pendingStudentNumber
                )
            }
            setLoadingState(false)
            handleOtpRequestResult(result)
        }
    }

    private fun handleOtpRequestResult(result: OtpRequestResult) {
        when (result) {
            is OtpRequestResult.Sent -> {
                val cleanEmail = CatalogCryptoUtils.decrypt(result.email).trim()
                pendingStudentNumber = result.studentNumber
                pendingEmail = cleanEmail
                etOtpCode.setText("")

                tvOtpSentInfo.text =
                    "We sent a 6-digit OTP for ${result.studentNumber} to $cleanEmail. Please check your Inbox or Spam folder."

                if (!result.devPreviewOtp.isNullOrBlank()) {
                    cardDevOtpBanner.visibility = View.VISIBLE
                    tvDevOtpCode.text = result.devPreviewOtp
                } else {
                    cardDevOtpBanner.visibility = View.GONE
                }

                Toast.makeText(
                    this,
                    "OTP sent to $cleanEmail!",
                    Toast.LENGTH_SHORT
                ).show()

                showEnrollmentSubStep(step = 2)
                startOtpResendCooldown(result.cooldownSeconds)
            }
            is OtpRequestResult.CooldownActive -> {
                showEnrollmentSubStep(step = 2)
                startOtpResendCooldown(result.remainingSeconds.toInt())
                val cleanPendingEmail = CatalogCryptoUtils.decrypt(pendingEmail).trim()
                val displayEmail = if (cleanPendingEmail.isNotBlank()) cleanPendingEmail else "your registered email"
                tvOtpSentInfo.text =
                    "The previous 6-digit OTP for $pendingStudentNumber is still active at $displayEmail. Please check your Inbox or Spam folder."
                Toast.makeText(
                    this,
                    "Please wait ${result.remainingSeconds} seconds before requesting another OTP.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            is OtpRequestResult.Error -> {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startOtpResendCooldown(seconds: Int) {
        otpResendTimer?.cancel()
        btnResendOtp.isEnabled = false
        btnResendOtp.alpha = 0.5f

        otpResendTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000L) + 1L
                btnResendOtp.text = "Resend in ${sec}s"
            }

            override fun onFinish() {
                btnResendOtp.isEnabled = true
                btnResendOtp.alpha = 1f
                btnResendOtp.text = "Resend OTP"
            }
        }.start()
    }

    // =====================================================
    // STEP 2: VERIFY EMAIL OTP
    // =====================================================

    private fun handleVerifyOtpClick() {
        val code = etOtpCode.text?.toString()?.trim().orEmpty()
        if (!PinSecurityUtils.isValidSixDigitOtp(code)) {
            Toast.makeText(this, "Please enter the exact 6-digit OTP code.", Toast.LENGTH_SHORT).show()
            return
        }

        setLoadingState(true)
        lifecycleScope.launch {
            val result = authRepository.verifyOtp(
                studentNumber = pendingStudentNumber,
                enteredOtp = code,
                expectedPurpose = currentOtpPurpose
            )
            setLoadingState(false)

            when (result) {
                is OtpVerifyResult.VerifiedForRegistration -> {
                    otpResendTimer?.cancel()
                    pendingStudentNumber = result.studentNumber
                    pendingEmail = result.email
                    tvPinSetupTitle.text = "Create 4-Digit PIN"
                    tvPinSetupSubtitle.text =
                        "You will use this 4-digit PIN to log in to ${result.studentNumber} even without an internet connection (Offline Mode)."
                    etNewPin.setText("")
                    etConfirmPin.setText("")
                    showEnrollmentSubStep(step = 3)
                }
                is OtpVerifyResult.VerifiedForPinReset -> {
                    otpResendTimer?.cancel()
                    pendingStudentNumber = result.studentNumber
                    pendingEmail = result.email
                    tvPinSetupTitle.text = "Set New 4-Digit PIN"
                    tvPinSetupSubtitle.text =
                        "Enter a new 4-digit PIN for ${result.studentNumber}."
                    etNewPin.setText("")
                    etConfirmPin.setText("")
                    showEnrollmentSubStep(step = 3)
                }
                is OtpVerifyResult.VerifiedExistingStudent -> {
                    otpResendTimer?.cancel()
                    Toast.makeText(
                        this@StudentAuthActivity,
                        "Account ${result.studentNumber} verified! Welcome back.",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToHome()
                }
                is OtpVerifyResult.Error -> {
                    Toast.makeText(this@StudentAuthActivity, result.message, Toast.LENGTH_LONG).show()
                    val lockoutRem = sessionManager.getOtpLockoutRemainingSeconds(pendingStudentNumber)
                    if (lockoutRem > 0L) {
                        startOtpLockoutCooldown(lockoutRem.toInt())
                    }
                }
            }
        }
    }

    private fun startOtpLockoutCooldown(seconds: Int) {
        startOtpResendCooldown(seconds)
        btnVerifyOtp.isEnabled = false
        btnVerifyOtp.alpha = 0.5f

        object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000L) + 1L
                val mins = sec / 60L
                val remainderSec = sec % 60L
                btnVerifyOtp.text = String.format(java.util.Locale.US, "Locked (%02d:%02d)", mins, remainderSec)
            }

            override fun onFinish() {
                btnVerifyOtp.isEnabled = true
                btnVerifyOtp.alpha = 1.0f
                btnVerifyOtp.text = "Verify Code"
            }
        }.start()
    }

    // =====================================================
    // STEP 3: SAVE 4-DIGIT PIN
    // =====================================================

    private fun handleSavePinClick() {
        val newPin = etNewPin.text?.toString()?.trim().orEmpty()
        val confirmPin = etConfirmPin.text?.toString()?.trim().orEmpty()

        if (!PinSecurityUtils.isValidFourDigitPin(newPin)) {
            Toast.makeText(this, "PIN must be an exact 4-digit number.", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPin != confirmPin) {
            Toast.makeText(this, "The entered 4-digit PINs do not match.", Toast.LENGTH_SHORT).show()
            return
        }

        val isReset = currentOtpPurpose == OtpPurpose.RESET_PIN
        setLoadingState(true)
        lifecycleScope.launch {
            val result = authRepository.completePinSetupAndLogin(
                studentNumber = pendingStudentNumber,
                email = pendingEmail,
                fourDigitPin = newPin,
                isPinReset = isReset
            )
            setLoadingState(false)

            when (result) {
                is AuthActionResult.Success -> {
                    Toast.makeText(
                        this@StudentAuthActivity,
                        if (isReset) "PIN updated successfully!" else "Registration complete!",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToHome()
                }
                is AuthActionResult.Error -> {
                    Toast.makeText(this@StudentAuthActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // =====================================================
    // GCASH-STYLE 4-DIGIT PIN KEYPAD BOTTOM SHEET
    // =====================================================

    private fun showPinKeypadBottomSheet(studentNumber: String) {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.dialog_pin_keypad, null)
        dialog.setContentView(sheetView)

        val tvStudent = sheetView.findViewById<TextView>(R.id.tvKeypadStudentNumber)
        val tvBadge = sheetView.findViewById<TextView>(R.id.tvKeypadNetworkBadge)
        val tvError = sheetView.findViewById<TextView>(R.id.tvKeypadError)
        val dots = listOf<View>(
            sheetView.findViewById(R.id.pinDot1),
            sheetView.findViewById(R.id.pinDot2),
            sheetView.findViewById(R.id.pinDot3),
            sheetView.findViewById(R.id.pinDot4)
        )

        tvStudent.text = studentNumber
        val isOnline = authRepository.isOnline()
        tvBadge.text = if (isOnline) {
            "Online • Secure PIN Login"
        } else {
            "Offline Mode • Local 4-Digit PIN Login"
        }

        val enteredDigits = StringBuilder()
        var isVerifying = false

        fun updatePinDots() {
            for (i in dots.indices) {
                dots[i].setBackgroundResource(
                    if (i < enteredDigits.length) R.drawable.bg_pin_dot_filled
                    else R.drawable.bg_pin_dot_empty
                )
            }
        }

        val keypadViews = listOf(
            sheetView.findViewById<View>(R.id.key0),
            sheetView.findViewById<View>(R.id.key1),
            sheetView.findViewById<View>(R.id.key2),
            sheetView.findViewById<View>(R.id.key3),
            sheetView.findViewById<View>(R.id.key4),
            sheetView.findViewById<View>(R.id.key5),
            sheetView.findViewById<View>(R.id.key6),
            sheetView.findViewById<View>(R.id.key7),
            sheetView.findViewById<View>(R.id.key8),
            sheetView.findViewById<View>(R.id.key9),
            sheetView.findViewById<View>(R.id.keyBackspace)
        )

        fun setKeypadEnabled(enabled: Boolean) {
            for (view in keypadViews) {
                view?.isEnabled = enabled
                view?.alpha = if (enabled) 1.0f else 0.35f
            }
        }

        fun startLockoutCountdown(seconds: Long) {
            setKeypadEnabled(false)
            keypadLockoutTimer?.cancel()
            keypadLockoutTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    val totalSec = (millisUntilFinished / 1000L) + 1L
                    val mins = totalSec / 60L
                    val secs = totalSec % 60L
                    tvError.text = String.format(
                        java.util.Locale.US,
                        "PIN login locked. Please wait %02d:%02d.",
                        mins,
                        secs
                    )
                }

                override fun onFinish() {
                    tvError.text = ""
                    setKeypadEnabled(true)
                }
            }.start()
        }

        val initialLockout = sessionManager.getLockoutRemainingSeconds(studentNumber)
        if (initialLockout > 0L) {
            setKeypadEnabled(false)
            startLockoutCountdown(initialLockout)
        }

        fun onDigitPressed(digit: Char) {
            if (isVerifying) return
            val lockoutRem = sessionManager.getLockoutRemainingSeconds(studentNumber)
            if (lockoutRem > 0L) {
                setKeypadEnabled(false)
                startLockoutCountdown(lockoutRem)
                return
            }

            if (enteredDigits.length < 4) {
                if (enteredDigits.isEmpty()) {
                    tvError.text = ""
                }
                enteredDigits.append(digit)
                updatePinDots()

                if (enteredDigits.length == 4) {
                    isVerifying = true
                    setKeypadEnabled(false)
                    val pinCandidate = enteredDigits.toString()

                    lifecycleScope.launch {
                        val verifyResult = authRepository.loginWithFourDigitPin(
                            studentNumber = studentNumber,
                            enteredPin = pinCandidate
                        )
                        isVerifying = false

                        when (verifyResult) {
                            is LocalPinVerifyResult.Success -> {
                                keypadLockoutTimer?.cancel()
                                dialog.dismiss()
                                navigateToHome()
                            }
                            is LocalPinVerifyResult.WrongPin -> {
                                setKeypadEnabled(true)
                                enteredDigits.clear()
                                updatePinDots()
                                tvError.text =
                                    "Incorrect PIN. You have ${verifyResult.remainingAttempts} attempt(s) remaining."
                            }
                            is LocalPinVerifyResult.LockedOut -> {
                                enteredDigits.clear()
                                updatePinDots()
                                startLockoutCountdown(verifyResult.remainingSeconds)
                            }
                            is LocalPinVerifyResult.DailyLimitReached -> {
                                setKeypadEnabled(true)
                                enteredDigits.clear()
                                updatePinDots()
                                tvError.text = verifyResult.message
                            }
                            is LocalPinVerifyResult.AccountNotFound -> {
                                setKeypadEnabled(true)
                                dialog.dismiss()
                                showEnrollmentScreen(registerMode = true)
                            }
                        }
                    }
                }
            }
        }

        val digitButtonIds = listOf(
            R.id.key0 to '0',
            R.id.key1 to '1',
            R.id.key2 to '2',
            R.id.key3 to '3',
            R.id.key4 to '4',
            R.id.key5 to '5',
            R.id.key6 to '6',
            R.id.key7 to '7',
            R.id.key8 to '8',
            R.id.key9 to '9'
        )
        for ((viewId, digitChar) in digitButtonIds) {
            sheetView.findViewById<View>(viewId).setOnClickListener {
                onDigitPressed(digitChar)
            }
        }

        sheetView.findViewById<View>(R.id.keyBackspace).setOnClickListener {
            if (!isVerifying && enteredDigits.isNotEmpty()) {
                enteredDigits.deleteCharAt(enteredDigits.lastIndex)
                updatePinDots()
            }
        }

        sheetView.findViewById<View>(R.id.keyForgotPin).setOnClickListener {
            keypadLockoutTimer?.cancel()
            dialog.dismiss()
            initiateForgotPinFlow(studentNumber)
        }

        dialog.setOnDismissListener {
            keypadLockoutTimer?.cancel()
        }

        dialog.show()
    }

    // =====================================================
    // OPTION B: SWITCH ACCOUNT BOTTOM SHEET (MAX 3 / DAY)
    // =====================================================

    private fun showSwitchAccountBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.dialog_switch_account, null)
        dialog.setContentView(sheetView)

        val tvDailyCounter = sheetView.findViewById<TextView>(R.id.tvSwitchDailyCounter)
        val containerSaved = sheetView.findViewById<LinearLayout>(R.id.containerSavedAccounts)
        val btnAddAccount = sheetView.findViewById<MaterialButton>(R.id.btnAddStudentAccount)
        val tvLimitHint = sheetView.findViewById<TextView>(R.id.tvSwitchLimitReachedHint)

        val usedToday = sessionManager.getDailyUsedStudentsToday()
        val maxLimit = DailyDeviceSwitchPolicy.MAX_DISTINCT_ACCOUNTS_PER_DAY
        val savedAccounts = sessionManager.getSavedAccounts() // Sorted by lastLoginAtMillis DESC (most recent first)
        val mostRecentNumber = savedAccounts.firstOrNull()?.studentNumber ?: sessionManager.getActiveStudentNumber()

        tvDailyCounter.text = when (usedToday.size) {
            0 -> "Accounts on this device: 0 / $maxLimit"
            1 -> "Accounts on this device: 1 / $maxLimit"
            2 -> "Accounts on this device: 2 / $maxLimit (1 slot left)"
            else -> "Accounts on this device: $maxLimit / $maxLimit (Device limit reached)"
        }

        val density = resources.displayMetrics.density

        for ((index, account) in savedAccounts.withIndex()) {
            val isMostRecent = index == 0 || account.studentNumber.equals(mostRecentNumber, ignoreCase = true)
            val isAlreadyUsedToday = usedToday.any {
                it.equals(account.studentNumber, ignoreCase = true)
            }

            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = (16 * density).toInt()
                val padV = (14 * density).toInt()
                setPadding(padH, padV, padH, padV)

                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 14f * density
                    setColor(if (isMostRecent) Color.parseColor("#EBF3FE") else Color.parseColor("#F8FAFC"))
                    setStroke(
                        (1.2f * density).toInt(),
                        if (isMostRecent) Color.parseColor("#1967D2") else Color.parseColor("#E2E8F0")
                    )
                }

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (10 * density).toInt()
                }
            }

            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val tvId = TextView(this).apply {
                text = account.studentNumber
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#102A3E"))
            }

            val statusSubtitle = buildString {
                append(CatalogCryptoUtils.decrypt(account.email).trim())
                if (isMostRecent) {
                    append(" • Most recent open")
                }
            }
            val tvEmail = TextView(this).apply {
                text = statusSubtitle
                textSize = 12f
                setTextColor(Color.parseColor("#667085"))
            }

            infoCol.addView(tvId)
            infoCol.addView(tvEmail)

            val tvBadge = TextView(this).apply {
                text = if (isMostRecent) "Recent • PIN →" else "Switch →"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#155EC4"))
            }

            rowLayout.addView(infoCol)
            rowLayout.addView(tvBadge)

            rowLayout.setOnClickListener {
                when (val decision = sessionManager.evaluateDailySwitchPolicy(account.studentNumber)) {
                    is DailyDeviceSwitchPolicy.SwitchDecision.DeniedDailyLimit -> {
                        Toast.makeText(this, decision.message, Toast.LENGTH_LONG).show()
                    }
                    is DailyDeviceSwitchPolicy.SwitchDecision.DeniedClockTamper -> {
                        Toast.makeText(this, decision.message, Toast.LENGTH_LONG).show()
                    }
                    is DailyDeviceSwitchPolicy.SwitchDecision.Allowed -> {
                        dialog.dismiss()
                        showPinKeypadBottomSheet(account.studentNumber)
                    }
                }
            }

            containerSaved.addView(rowLayout)
        }

        // 2. Accounts already registered to this device in Firestore logs but not yet saved in local PIN storage
        // (e.g. after reinstalling app or clearing app storage)
        val savedNumbers = savedAccounts.map { it.studentNumber.uppercase() }.toSet()
        val unsavedRegistered = usedToday.filter { it.isNotBlank() && it.uppercase() !in savedNumbers }

        for (unsavedNumber in unsavedRegistered) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = (16 * density).toInt()
                val padV = (14 * density).toInt()
                setPadding(padH, padV, padH, padV)

                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 14f * density
                    setColor(Color.parseColor("#F8FAFC"))
                    setStroke(
                        (1.2f * density).toInt(),
                        Color.parseColor("#CBD5E1")
                    )
                }

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (10 * density).toInt()
                }
            }

            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val tvId = TextView(this).apply {
                text = unsavedNumber
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#102A3E"))
            }

            val tvSubtitle = TextView(this).apply {
                text = "Registered on this device • Tap to log in"
                textSize = 12f
                setTextColor(Color.parseColor("#475569"))
            }

            infoCol.addView(tvId)
            infoCol.addView(tvSubtitle)

            val tvBadge = TextView(this).apply {
                text = "Log In →"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#155EC4"))
            }

            rowLayout.addView(infoCol)
            rowLayout.addView(tvBadge)

            rowLayout.setOnClickListener {
                dialog.dismiss()
                showEnrollmentScreen(registerMode = false)
                prefillStudentNumber(unsavedNumber)
            }

            containerSaved.addView(rowLayout)
        }

        // 3. Add / Log In button state
        val allSlotsOccupiedLocally = savedAccounts.size >= maxLimit
        val isDeviceLimitReached = usedToday.size >= maxLimit

        if (allSlotsOccupiedLocally) {
            btnAddAccount.isEnabled = false
            btnAddAccount.alpha = 0.45f
            btnAddAccount.text = "+ Add Student Account"
            tvLimitHint.visibility = View.VISIBLE
            tvLimitHint.text = "All $maxLimit student accounts for this device are active above."
        } else {
            btnAddAccount.isEnabled = true
            btnAddAccount.alpha = 1.0f
            if (isDeviceLimitReached) {
                btnAddAccount.text = "+ Log In Existing Account"
                tvLimitHint.visibility = View.VISIBLE
                tvLimitHint.text = "Device limit reached ($maxLimit/$maxLimit). You can log in accounts already registered to this device."
                btnAddAccount.setOnClickListener {
                    dialog.dismiss()
                    showEnrollmentScreen(registerMode = false)
                }
            } else {
                btnAddAccount.text = "+ Add / Register Student Account"
                tvLimitHint.visibility = View.GONE
                btnAddAccount.setOnClickListener {
                    dialog.dismiss()
                    showEnrollmentScreen(registerMode = true)
                }
            }
        }

        dialog.show()
    }

    private fun prefillStudentNumber(studentNumber: String) {
        val parts = studentNumber.trim().split("-")
        if (parts.size == 3) {
            val campus = parts[0].uppercase()
            val year = parts[1]
            val digits = parts[2]
            if (campus in StudentNumberValidator.CAMPUS_CODES) {
                selectedCampusCode = campus
                tvSelectedCampus.text = campus
            }
            val availableYears = StudentNumberValidator.getAvailableYears()
            if (year in availableYears) {
                selectedYear = year
                tvSelectedYear.text = year
            }
            etStudentDigits.setText(digits)
            etStudentDigits.setSelection(etStudentDigits.text?.length ?: 0)
            updateStudentNumberPreview()
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        progressAuthAction.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnRequestOtp.isEnabled = !isLoading
        btnVerifyOtp.isEnabled = !isLoading
        btnSavePin.isEnabled = !isLoading
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}
