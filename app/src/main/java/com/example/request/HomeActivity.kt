package com.example.request

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var btnSync: ImageView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvLastUpdated: TextView
    private lateinit var etSearch: EditText
    private lateinit var searchResultsContainer: LinearLayout
    private lateinit var updateNotificationsContainer: LinearLayout
    private lateinit var tvNoUpdateNotifications: TextView
    private lateinit var tvNotificationCount: TextView
    private lateinit var btnTestNotification: Button
    private lateinit var importantAnnouncementsContainer: LinearLayout
    private lateinit var tvNoImportantAnnouncements: TextView
    private lateinit var navHome: TextView
    private lateinit var navServices: TextView
    private lateinit var navProgress: TextView
    private lateinit var updateManager: UpdateNotificationManager

    private var syncAnimator: ObjectAnimator? = null
    private var isSyncing = false

    private val services = listOf(
        Service("service_001", "Registrar Services"),
        Service("service_002", "Academic Services")
    )

    private val transactions = listOf(
        Transaction("transaction_001", "service_001", "Getting Prospectus", "Registrar Services"),
        Transaction("transaction_002", "service_001", "Updating Prospectus", "Registrar Services"),
        Transaction("transaction_003", "service_002", "Academic Concern", "Academic Services")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        btnSync = findViewById(R.id.btnSync)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvLastUpdated = findViewById(R.id.tvLastUpdated)
        etSearch = findViewById(R.id.etSearch)
        searchResultsContainer = findViewById(R.id.searchResultsContainer)
        updateNotificationsContainer = findViewById(R.id.updateNotificationsContainer)
        tvNoUpdateNotifications = findViewById(R.id.tvNoUpdateNotifications)
        tvNotificationCount = findViewById(R.id.tvNotificationCount)
        btnTestNotification = findViewById(R.id.btnTestNotification)
        importantAnnouncementsContainer = findViewById(R.id.importantAnnouncementsContainer)
        tvNoImportantAnnouncements = findViewById(R.id.tvNoImportantAnnouncements)
        navHome = findViewById(R.id.navHome)
        navServices = findViewById(R.id.navServices)
        navProgress = findViewById(R.id.navProgress)

        updateManager = UpdateNotificationManager(this)

        requestNotificationPermission()
        setupSearch()
        displayUpdateNotifications()
        displayImportantAnnouncements()
        updateConnectionStatus()
        loadLastUpdated()

        btnSync.setOnClickListener {
            if (!isSyncing) syncData()
        }

        btnTestNotification.setOnClickListener {
            createTestUpdate()
        }

        navHome.setOnClickListener {
            // Already on Home
        }

        navServices.setOnClickListener {
            startActivity(Intent(this, ServicesActivity::class.java))
        }

        navProgress.setOnClickListener {
            startActivity(Intent(this, ProgressActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::updateManager.isInitialized) displayUpdateNotifications()
        if (::tvConnectionStatus.isInitialized) updateConnectionStatus()
    }

    // TEST UPDATE
    private fun createTestUpdate() {
        val dateTime = SimpleDateFormat(
            "MMM dd, yyyy • hh:mm a",
            Locale.getDefault()
        ).format(Date())

        val update = UpdateNotification(
            id = "update_${System.currentTimeMillis()}",
            serviceName = "Registrar Services",
            transactionId = "transaction_002",
            transactionName = "Updating Prospectus",
            updatedSection = "REQUIREMENTS",
            message = "Requirements Updated",
            dateTime = dateTime
        )

        updateManager.save(update)
        displayUpdateNotifications()

        if (hasNotificationPermission()) {
            NotificationHelper(this).showTransactionUpdateNotification(
                notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                transactionId = update.transactionId,
                transactionName = update.transactionName,
                serviceName = update.serviceName,
                updatedSection = update.updatedSection,
                notificationMessage = update.message
            )
        } else {
            requestNotificationPermission()
        }

        Toast.makeText(this, "Test update created.", Toast.LENGTH_SHORT).show()
    }

    // HOME UPDATE NOTIFICATIONS
    private fun displayUpdateNotifications() {
        val updates = updateManager.getUnread()
        updateNotificationsContainer.removeAllViews()

        tvNotificationCount.text = updates.size.toString()
        tvNotificationCount.visibility = if (updates.isEmpty()) View.GONE else View.VISIBLE
        tvNoUpdateNotifications.visibility = if (updates.isEmpty()) View.VISIBLE else View.GONE

        updates.forEach { update ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
                background = getDrawable(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 12.dp() }
                isClickable = true
                isFocusable = true
            }

            val service = TextView(this).apply {
                text = update.serviceName
                textSize = 12f
                setTextColor(getColor(R.color.primary))
                setTypeface(typeface, Typeface.BOLD)
            }

            val transaction = TextView(this).apply {
                text = update.transactionName
                textSize = 17f
                setTextColor(getColor(R.color.text_primary))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 5.dp(), 0, 0)
            }

            val message = TextView(this).apply {
                text = update.message
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 5.dp(), 0, 0)
            }

            val dateTime = TextView(this).apply {
                text = update.dateTime
                textSize = 11f
                setTextColor(getColor(R.color.text_hint))
                setPadding(0, 8.dp(), 0, 0)
            }

            card.addView(service)
            card.addView(transaction)
            card.addView(message)
            card.addView(dateTime)

            card.setOnClickListener {
                updateManager.markAsRead(update.id)
                displayUpdateNotifications()
                openUpdatedTransaction(update)
            }

            updateNotificationsContainer.addView(card)
        }
    }

    private fun openUpdatedTransaction(update: UpdateNotification) {
        val intent = Intent(this, TransactionDetailsActivity::class.java).apply {
            putExtra("TRANSACTION_ID", update.transactionId)
            putExtra("TRANSACTION_NAME", update.transactionName)
            putExtra("SERVICE_NAME", update.serviceName)
            putExtra("TARGET_SECTION", update.updatedSection)
        }
        startActivity(intent)
    }

    // SEARCH
    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                showSearchResults(s.toString().trim())
            }
        })
    }

    private fun showSearchResults(query: String) {
        searchResultsContainer.removeAllViews()

        if (query.isBlank()) {
            searchResultsContainer.visibility = View.GONE
            return
        }

        val foundServices = services.filter {
            it.name.contains(query, ignoreCase = true)
        }

        val foundTransactions = transactions.filter {
            it.name.contains(query, ignoreCase = true)
        }

        if (foundServices.isEmpty() && foundTransactions.isEmpty()) {
            searchResultsContainer.visibility = View.GONE
            return
        }

        searchResultsContainer.visibility = View.VISIBLE

        foundServices.forEach { service ->
            addSearchResult(service.name, "School Service") {
                val intent = Intent(this, TransactionListActivity::class.java).apply {
                    putExtra("SERVICE_ID", service.id)
                    putExtra("SERVICE_NAME", service.name)
                }
                etSearch.setText("")
                startActivity(intent)
            }
        }

        foundTransactions.forEach { transaction ->
            addSearchResult(transaction.name, transaction.serviceName) {
                val intent = Intent(this, TransactionDetailsActivity::class.java).apply {
                    putExtra("TRANSACTION_ID", transaction.id)
                    putExtra("TRANSACTION_NAME", transaction.name)
                    putExtra("SERVICE_NAME", transaction.serviceName)
                }
                etSearch.setText("")
                startActivity(intent)
            }
        }
    }

    private fun addSearchResult(title: String, subtitle: String, action: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 13.dp(), 18.dp(), 13.dp())
            isClickable = true
            isFocusable = true
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, 3.dp(), 0, 0)
        }

        item.addView(titleView)
        item.addView(subtitleView)
        item.setOnClickListener { action() }
        searchResultsContainer.addView(item)
    }

    // IMPORTANT ANNOUNCEMENTS
    private fun displayImportantAnnouncements() {
        importantAnnouncementsContainer.removeAllViews()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
            background = getDrawable(R.drawable.bg_reminder)
        }

        val title = TextView(this).apply {
            text = "Office Closure"
            textSize = 16f
            setTextColor(getColor(R.color.primary))
            setTypeface(typeface, Typeface.BOLD)
        }

        val message = TextView(this).apply {
            text = "School offices will be temporarily closed due to the upcoming holiday."
            textSize = 13f
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, 6.dp(), 0, 0)
        }

        val date = TextView(this).apply {
            text = "Posted: September 18, 2026"
            textSize = 11f
            setTextColor(getColor(R.color.text_hint))
            setPadding(0, 8.dp(), 0, 0)
        }

        card.addView(title)
        card.addView(message)
        card.addView(date)
        importantAnnouncementsContainer.addView(card)
        tvNoImportantAnnouncements.visibility = View.GONE
    }

    // REFRESH / SYNC
    private fun syncData() {
        isSyncing = true
        startRefreshAnimation()
        updateConnectionStatus()

        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isInternetAvailable()) {
                stopRefreshAnimation()
                Toast.makeText(
                    this,
                    "No internet connection. Showing saved information.",
                    Toast.LENGTH_SHORT
                ).show()
                return@postDelayed
            }

            val dateTime = SimpleDateFormat(
                "MMM dd, yyyy • hh:mm a",
                Locale.getDefault()
            ).format(Date())

            getSharedPreferences("EReQuestPreferences", MODE_PRIVATE)
                .edit()
                .putString("last_updated", dateTime)
                .apply()

            tvLastUpdated.text = "Last updated: $dateTime"

            displayUpdateNotifications()
            displayImportantAnnouncements()
            stopRefreshAnimation()

            Toast.makeText(
                this,
                "Information is up to date.",
                Toast.LENGTH_SHORT
            ).show()
        }, 1500)
    }

    private fun startRefreshAnimation() {
        syncAnimator?.cancel()

        syncAnimator = ObjectAnimator.ofFloat(btnSync, "rotation", 0f, 360f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopRefreshAnimation() {
        syncAnimator?.cancel()
        syncAnimator = null
        btnSync.rotation = 0f
        isSyncing = false
    }

    // INTERNET
    private fun updateConnectionStatus() {
        tvConnectionStatus.text = if (isInternetAvailable()) {
            "● Online"
        } else {
            "● Offline"
        }
    }

    private fun isInternetAvailable(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
    }

    // LAST UPDATED
    private fun loadLastUpdated() {
        val lastUpdated = getSharedPreferences(
            "EReQuestPreferences",
            MODE_PRIVATE
        ).getString("last_updated", null)

        tvLastUpdated.text = if (lastUpdated == null) {
            "Last updated: Never"
        } else {
            "Last updated: $lastUpdated"
        }
    }

    // ANDROID NOTIFICATION PERMISSION
    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        syncAnimator?.cancel()
        super.onDestroy()
    }

    data class Service(
        val id: String,
        val name: String
    )

    data class Transaction(
        val id: String,
        val serviceId: String,
        val name: String,
        val serviceName: String
    )
}