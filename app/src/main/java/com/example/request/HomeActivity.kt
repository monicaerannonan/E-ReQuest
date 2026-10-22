package com.example.request

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted && ::dao.isInitialized) {
                lifecycleScope.launch(Dispatchers.IO) {
                    NotificationHelper(this@HomeActivity).dispatchUnnotifiedUpdates(dao)
                }
            }
        }



    // =====================================================
    // XML VIEWS
    // =====================================================

    private lateinit var btnSync: ImageView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvLastUpdated: TextView

    private lateinit var etSearch: EditText
    private lateinit var searchResultsContainer: LinearLayout

    private lateinit var updateNotificationsScrollView: ScrollView
    private lateinit var updateNotificationsContainer: LinearLayout
    private lateinit var tvNoUpdateNotifications: TextView
    private lateinit var tvNotificationCount: TextView

    private lateinit var importantAnnouncementsScrollView: ScrollView
    private lateinit var importantAnnouncementsContainer: LinearLayout
    private lateinit var tvNoImportantAnnouncements: TextView

    private lateinit var navHome: TextView
    private lateinit var navServices: TextView
    private lateinit var navProgress: TextView

    // =====================================================
    // ROOM DATABASE
    // =====================================================

    private lateinit var database: ERequestDatabase
    private lateinit var dao: ERequestDao

    private lateinit var updateManager: UpdateNotificationManager

    // =====================================================
    // FIRESTORE SYNC
    // =====================================================

    private lateinit var firestoreSyncManager: FirestoreSyncManager

    // =====================================================
    // NETWORK MONITOR
    // =====================================================

    private lateinit var networkMonitor: NetworkMonitor

    // =====================================================
    // FIRESTORE REAL-TIME LISTENER
    // =====================================================

    private lateinit var firestoreUpdateListener: FirestoreUpdateListener

    // =====================================================
    // SEARCH DATA
    // =====================================================

    private var services: List<ServiceEntity> = emptyList()

    private var transactions: List<TransactionEntity> = emptyList()

    // =====================================================
    // REFRESH
    // =====================================================

    private var syncAnimator: ObjectAnimator? = null

    private var isSyncing = false

    private var activeBannerView: TextView? = null
    private var dismissBannerRunnable: Runnable? = null

    private val backgroundSyncCompletionListener: () -> Unit = {
        if (!isFinishing && !isDestroyed && ::dao.isInitialized) {
            loadSearchData()
            displayUpdateNotifications()
            displayImportantAnnouncements()
            updateConnectionStatus()
        }
    }

    private fun showSingleToast(
        message: String,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        val rootView = findViewById<ViewGroup>(android.R.id.content) ?: return

        dismissBannerRunnable?.let { rootView.removeCallbacks(it) }
        activeBannerView?.let { existing ->
            existing.animate().cancel()
            rootView.removeView(existing)
        }

        val banner = TextView(this).apply {
            text = message
            textSize = 13.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(20.dp(), 11.dp(), 20.dp(), 11.dp())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24.dp().toFloat()
                setColor(0xE6323232.toInt())
            }
            elevation = 6.dp().toFloat()
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 88.dp()
                leftMargin = 24.dp()
                rightMargin = 24.dp()
            }
        }

        rootView.addView(banner)
        activeBannerView = banner

        banner.animate()
            .alpha(1f)
            .setDuration(180L)
            .start()

        val displayMillis = if (duration == Toast.LENGTH_LONG) 3200L else 2000L
        val runnable = Runnable {
            banner.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction {
                    rootView.removeView(banner)
                    if (activeBannerView === banner) {
                        activeBannerView = null
                    }
                }
                .start()
        }
        dismissBannerRunnable = runnable
        rootView.postDelayed(runnable, displayMillis)
    }

    // =====================================================
    // ON CREATE
    // =====================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_home)

        requestNotificationPermissionIfNeeded()

        // =================================================
        // CONNECT XML
        // =================================================

        btnSync =
            findViewById(R.id.btnSync)

        tvConnectionStatus =
            findViewById(R.id.tvConnectionStatus)

        tvLastUpdated =
            findViewById(R.id.tvLastUpdated)

        etSearch =
            findViewById(R.id.etSearch)

        searchResultsContainer =
            findViewById(R.id.searchResultsContainer)

        updateNotificationsScrollView =
            findViewById(R.id.updateNotificationsScrollView)

        updateNotificationsContainer =
            findViewById(R.id.updateNotificationsContainer)

        tvNoUpdateNotifications =
            findViewById(R.id.tvNoUpdateNotifications)

        tvNotificationCount =
            findViewById(R.id.tvNotificationCount)

        importantAnnouncementsScrollView =
            findViewById(R.id.importantAnnouncementsScrollView)

        importantAnnouncementsContainer =
            findViewById(R.id.importantAnnouncementsContainer)

        tvNoImportantAnnouncements =
            findViewById(R.id.tvNoImportantAnnouncements)

        navHome =
            findViewById(R.id.navHome)

        navServices =
            findViewById(R.id.navServices)

        navProgress =
            findViewById(R.id.navProgress)

        // =================================================
        // INITIALIZE ROOM DATABASE
        // =================================================

        database =
            ERequestDatabase.getDatabase(
                applicationContext
            )

        dao =
            database.eRequestDao()

        // =================================================
        // UPDATE NOTIFICATION MANAGER
        // =================================================

        updateManager =
            UpdateNotificationManager(
                dao
            )

        // =================================================
        // FIRESTORE SYNC MANAGER
        // =================================================

        firestoreSyncManager =
            FirestoreSyncManager(
                applicationContext
            )

        // =================================================
        // AUTOMATIC ONLINE MONITOR
        // =================================================

        networkMonitor =
            NetworkMonitor(this) {

                runOnUiThread {

                    updateConnectionStatus()

                    if (
                        !isSyncing &&
                        !FirestoreSyncManager.isCurrentlySyncing() &&
                        !FirestoreSyncManager.wasSyncedRecently()
                    ) {
                        syncData(isSilent = true)
                    }
                }
            }

        // =================================================
        // FIRESTORE REAL-TIME UPDATE LISTENER
        // =================================================

        firestoreUpdateListener =
            FirestoreUpdateListener {

                runOnUiThread {

                    if (
                        isInternetAvailable() &&
                        !isSyncing
                    ) {

                        syncData(isSilent = true)
                    }
                }
            }

        // =================================================
        // LOAD SAVED ROOM DATA
        // =================================================

        loadSearchData()

        setupSearch()

        displayUpdateNotifications()

        displayImportantAnnouncements()

        updateConnectionStatus()

        loadLastUpdated()

        // =================================================
        // REFRESH BUTTON
        // =================================================

        btnSync.setOnClickListener {

            if (!isSyncing) {

                syncData(isSilent = false)
            }
        }

        // =================================================
        // BOTTOM NAVIGATION
        // =================================================

        navHome.setOnClickListener {

            // Already on Home
        }

        navServices.setOnClickListener {

            val intent =
                Intent(
                    this,
                    ServicesActivity::class.java
                )

            startActivity(intent)
            finish()
        }

        navProgress.setOnClickListener {

            val intent =
                Intent(
                    this,
                    ProgressActivity::class.java
                )

            startActivity(intent)
            finish()
        }
    }

    // =====================================================
    // LIFECYCLE MANAGEMENT FOR ACTIVE LISTENERS
    // =====================================================

    override fun onStart() {
        super.onStart()

        FirestoreSyncManager.registerSyncCompletionListener(
            backgroundSyncCompletionListener
        )

        if (::networkMonitor.isInitialized) {
            networkMonitor.start()
        }

        if (::firestoreUpdateListener.isInitialized) {
            firestoreUpdateListener.start()
        }
    }

    override fun onStop() {
        FirestoreSyncManager.unregisterSyncCompletionListener(
            backgroundSyncCompletionListener
        )

        if (::networkMonitor.isInitialized) {
            networkMonitor.stop()
        }

        if (::firestoreUpdateListener.isInitialized) {
            firestoreUpdateListener.stop()
        }

        super.onStop()
    }

    // =====================================================
    // ON RESUME
    // =====================================================

    override fun onResume() {
        super.onResume()

        if (::dao.isInitialized) {

            loadSearchData()

            displayUpdateNotifications()

            displayImportantAnnouncements()
        }

        if (::tvConnectionStatus.isInitialized) {

            updateConnectionStatus()
        }

        // Whenever user returns to Home, silently reconcile online updates/deletions in the background
        // only if no sync is currently running and no sync completed within the recent cooldown window.
        if (
            isInternetAvailable() &&
            !isSyncing &&
            !FirestoreSyncManager.isCurrentlySyncing() &&
            !FirestoreSyncManager.wasSyncedRecently()
        ) {
            syncData(isSilent = true)
        }
    }

    // =====================================================
    // LOAD SEARCH DATA FROM ROOM
    // =====================================================

    private fun loadSearchData() {

        lifecycleScope.launch {

            try {

                val loadedServices =
                    withContext(Dispatchers.IO) {

                        dao.getAllServices()
                    }

                val loadedTransactions =
                    withContext(Dispatchers.IO) {

                        dao.getAllTransactions()
                    }

                services =
                    loadedServices

                transactions =
                    loadedTransactions

                val currentQuery =
                    etSearch.text
                        .toString()
                        .trim()

                if (currentQuery.isNotBlank()) {

                    showSearchResults(
                        currentQuery
                    )
                }

            } catch (e: Exception) {

                e.printStackTrace()

                Toast.makeText(
                    this@HomeActivity,
                    "Unable to load search data.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // =====================================================
    // SETUP SEARCH
    // =====================================================

    private fun setupSearch() {

        etSearch.addTextChangedListener(

            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                    // No action needed
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                    // No action needed
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {

                    val query =
                        s.toString().trim()

                    showSearchResults(
                        query
                    )
                }
            }
        )
    }

    // =====================================================
    // SHOW SEARCH RESULTS
    // =====================================================

    private fun showSearchResults(
        query: String
    ) {

        searchResultsContainer.removeAllViews()

        if (query.isBlank()) {

            searchResultsContainer.visibility =
                View.GONE

            return
        }

        // =================================================
        // SEARCH SERVICES
        // =================================================

        val foundServices =
            services.filter { service ->

                service.name.contains(
                    query,
                    ignoreCase = true
                )
            }

        // =================================================
        // SEARCH TRANSACTIONS
        // =================================================

        val foundTransactions =
            transactions.filter { transaction ->

                transaction.name.contains(
                    query,
                    ignoreCase = true
                )
            }

        // =================================================
        // NO RESULTS
        // =================================================

        if (
            foundServices.isEmpty() &&
            foundTransactions.isEmpty()
        ) {

            searchResultsContainer.visibility =
                View.GONE

            return
        }

        searchResultsContainer.visibility =
            View.VISIBLE

        // =================================================
        // DISPLAY SERVICE RESULTS
        // =================================================

        foundServices.forEach { service ->

            addSearchResult(
                title = service.name,
                subtitle = "School Service"
            ) {

                val intent =
                    Intent(
                        this,
                        TransactionListActivity::class.java
                    ).apply {

                        putExtra(
                            "SERVICE_ID",
                            service.id
                        )

                        putExtra(
                            "SERVICE_NAME",
                            service.name
                        )
                    }

                etSearch.setText("")

                startActivity(intent)
            }
        }

        // =================================================
        // DISPLAY TRANSACTION RESULTS
        // =================================================

        foundTransactions.forEach { transaction ->

            val parentService =
                services.find { service ->

                    service.id ==
                            transaction.serviceId
                }

            val serviceName =
                parentService?.name
                    ?: transaction.officeName

            addSearchResult(
                title = transaction.name,
                subtitle = serviceName
            ) {

                val intent =
                    Intent(
                        this,
                        TransactionDetailsActivity::class.java
                    ).apply {

                        putExtra(
                            "TRANSACTION_ID",
                            transaction.id
                        )

                        putExtra(
                            "TRANSACTION_NAME",
                            transaction.name
                        )

                        putExtra(
                            "SERVICE_NAME",
                            serviceName
                        )
                    }

                etSearch.setText("")

                startActivity(intent)
            }
        }
    }

    // =====================================================
    // ADD SEARCH RESULT
    // =====================================================

    private fun addSearchResult(
        title: String,
        subtitle: String,
        action: () -> Unit
    ) {

        val item =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    18.dp(),
                    13.dp(),
                    18.dp(),
                    13.dp()
                )

                isClickable = true
                isFocusable = true
            }

        val titleView =
            TextView(this).apply {

                text =
                    title

                textSize =
                    15f

                setTextColor(
                    getColor(
                        R.color.text_primary
                    )
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        val subtitleView =
            TextView(this).apply {

                text =
                    subtitle

                textSize =
                    12f

                setTextColor(
                    getColor(
                        R.color.text_secondary
                    )
                )

                setPadding(
                    0,
                    3.dp(),
                    0,
                    0
                )
            }

        item.addView(
            titleView
        )

        item.addView(
            subtitleView
        )

        item.setOnClickListener {

            action()
        }

        searchResultsContainer.addView(
            item
        )
    }

    // =====================================================
    // DISPLAY UPDATE NOTIFICATIONS FROM ROOM
    // =====================================================

    private fun displayUpdateNotifications() {

        lifecycleScope.launch {

            try {

                val updates =
                    withContext(Dispatchers.IO) {
                        updateManager.getUnread()
                    }

                updateNotificationsContainer
                    .removeAllViews()

                // =================================================
                // NOTIFICATION COUNT
                // =================================================

                tvNotificationCount.text =
                    updates.size.toString()

                tvNotificationCount.visibility =
                    if (updates.isEmpty()) {

                        View.GONE

                    } else {

                        View.VISIBLE
                    }

                // =================================================
                // EMPTY MESSAGE & BANNER VISIBILITY
                // =================================================

                if (updates.isEmpty()) {

                    tvNoUpdateNotifications.visibility =
                        View.VISIBLE

                    updateNotificationsScrollView.visibility =
                        View.GONE

                    resetScrollableSectionHeight(
                        updateNotificationsScrollView
                    )

                    return@launch
                }

                tvNoUpdateNotifications.visibility =
                    View.GONE

                updateNotificationsScrollView.visibility =
                    View.VISIBLE

                // =================================================
                // DISPLAY EACH NOTIFICATION
                // =================================================

                updates.forEach { update ->

                    val card =
                        LinearLayout(
                            this@HomeActivity
                        ).apply {

                            orientation =
                                LinearLayout.VERTICAL

                            setPadding(
                                18.dp(),
                                16.dp(),
                                18.dp(),
                                16.dp()
                            )

                            background =
                                getDrawable(
                                    R.drawable.bg_card
                                )

                            layoutParams =
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {

                                    bottomMargin =
                                        12.dp()
                                }

                            isClickable = true

                            isFocusable = true
                        }

                    // =============================================
                    // SERVICE NAME
                    // =============================================

                    val service =
                        TextView(
                            this@HomeActivity
                        ).apply {

                            text =
                                update.serviceName

                            textSize =
                                12f

                            setTextColor(
                                getColor(
                                    R.color.primary
                                )
                            )

                            setTypeface(
                                typeface,
                                Typeface.BOLD
                            )
                        }

                    // =============================================
                    // TRANSACTION NAME
                    // =============================================

                    val transaction =
                        TextView(
                            this@HomeActivity
                        ).apply {

                            text =
                                update.transactionName

                            textSize =
                                17f

                            setTextColor(
                                getColor(
                                    R.color.text_primary
                                )
                            )

                            setTypeface(
                                typeface,
                                Typeface.BOLD
                            )

                            setPadding(
                                0,
                                5.dp(),
                                0,
                                0
                            )
                        }

                    // =============================================
                    // UPDATE MESSAGE
                    // =============================================

                    val message =
                        TextView(
                            this@HomeActivity
                        ).apply {

                            text =
                                update.message

                            textSize =
                                14f

                            setTextColor(
                                getColor(
                                    R.color.text_secondary
                                )
                            )

                            setPadding(
                                0,
                                5.dp(),
                                0,
                                0
                            )
                        }

                    // =============================================
                    // DATE AND TIME
                    // =============================================

                    val dateTime =
                        TextView(
                            this@HomeActivity
                        ).apply {

                            text =
                                update.dateTime

                            textSize =
                                11f

                            setTextColor(
                                getColor(
                                    R.color.text_hint
                                )
                            )

                            setPadding(
                                0,
                                8.dp(),
                                0,
                                0
                            )
                        }

                    card.addView(
                        service
                    )

                    card.addView(
                        transaction
                    )

                    card.addView(
                        message
                    )

                    card.addView(
                        dateTime
                    )

                    // =============================================
                    // CLICK NOTIFICATION
                    // =============================================

                    card.setOnClickListener {

                        lifecycleScope.launch {

                            try {

                                withContext(
                                    Dispatchers.IO
                                ) {

                                    updateManager
                                        .markAsRead(
                                            update.id
                                        )
                                }

                                displayUpdateNotifications()

                                openUpdatedTransaction(
                                    update
                                )

                            } catch (e: Exception) {

                                e.printStackTrace()
                            }
                        }
                    }

                    updateNotificationsContainer
                        .addView(
                            card
                        )
                }

                updateScrollableSectionHeight(
                    scrollView = updateNotificationsScrollView,
                    container = updateNotificationsContainer,
                    visibleItemCount = 3
                )

            } catch (e: Exception) {

                e.printStackTrace()

                updateNotificationsContainer
                    .removeAllViews()

                tvNotificationCount.visibility =
                    View.GONE

                tvNoUpdateNotifications.visibility =
                    View.VISIBLE

                updateNotificationsScrollView.visibility =
                    View.GONE

                resetScrollableSectionHeight(
                    updateNotificationsScrollView
                )
            }
        }
    }

    // =====================================================
    // OPEN TRANSACTION FROM NOTIFICATION
    // =====================================================

    private fun openUpdatedTransaction(
        update: UpdateNotification
    ) {

        val intent =
            Intent(
                this,
                TransactionDetailsActivity::class.java
            ).apply {

                putExtra(
                    "TRANSACTION_ID",
                    update.transactionId
                )

                putExtra(
                    "TRANSACTION_NAME",
                    update.transactionName
                )

                putExtra(
                    "SERVICE_NAME",
                    update.serviceName
                )

                putExtra(
                    "TARGET_SECTION",
                    update.updatedSection
                )
            }

        startActivity(intent)
    }

    // =====================================================
    // IMPORTANT ANNOUNCEMENTS FROM ROOM
    // =====================================================

    private fun displayImportantAnnouncements() {

        lifecycleScope.launch {

            try {

                val announcements =
                    withContext(
                        Dispatchers.IO
                    ) {

                        dao.getAllActiveAnnouncements()
                    }

                importantAnnouncementsContainer
                    .removeAllViews()

                // =================================================
                // NO ANNOUNCEMENTS
                // =================================================

                if (announcements.isEmpty()) {

                    tvNoImportantAnnouncements.visibility =
                        View.VISIBLE

                    resetScrollableSectionHeight(
                        importantAnnouncementsScrollView
                    )

                    return@launch
                }

                tvNoImportantAnnouncements.visibility =
                    View.GONE

                // =================================================
                // DISPLAY ANNOUNCEMENTS
                // =================================================

                announcements.forEach { announcement ->

                    val card =
                        LinearLayout(
                            this@HomeActivity
                        ).apply {

                            orientation =
                                LinearLayout.VERTICAL

                            setPadding(
                                18.dp(),
                                16.dp(),
                                18.dp(),
                                16.dp()
                            )

                            background =
                                getDrawable(
                                    R.drawable.bg_reminder
                                )

                            layoutParams =
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {

                                    bottomMargin =
                                        12.dp()
                                }
                        }

                    // =============================================
                    // ANNOUNCEMENT TITLE
                    // =============================================

                    val title =
                        TextView(
                            this@HomeActivity
                        ).apply {

                            text =
                                announcement.title

                            textSize =
                                16f

                            setTextColor(
                                getColor(
                                    R.color.primary
                                )
                            )

                            setTypeface(
                                typeface,
                                Typeface.BOLD
                            )
                        }

                    // =============================================
                    // ANNOUNCEMENT MESSAGE
                    // =============================================

                    val message =
                        TextView(
                            this@HomeActivity
                        ).apply {

                            text =
                                announcement.message

                            textSize =
                                13f

                            setTextColor(
                                getColor(
                                    R.color.text_primary
                                )
                            )

                            setPadding(
                                0,
                                6.dp(),
                                0,
                                0
                            )
                        }

                    // =============================================
                    // FORMAT ANNOUNCEMENT DATE
                    // =============================================

                    val formattedDate =
                        if (announcement.datePosted > 0L) {

                            SimpleDateFormat(
                                "MMM dd, yyyy",
                                Locale.getDefault()
                            ).format(
                                Date(
                                    announcement.datePosted
                                )
                            )

                        } else {

                            ""
                        }

                    val date =
                        TextView(
                            this@HomeActivity
                        ).apply {

                            text =
                                if (formattedDate.isNotBlank()) {

                                    "Posted: $formattedDate"

                                } else {

                                    ""
                                }

                            textSize =
                                11f

                            setTextColor(
                                getColor(
                                    R.color.text_hint
                                )
                            )

                            setPadding(
                                0,
                                8.dp(),
                                0,
                                0
                            )
                        }

                    card.addView(
                        title
                    )

                    card.addView(
                        message
                    )

                    if (formattedDate.isNotBlank()) {

                        card.addView(
                            date
                        )
                    }

                    importantAnnouncementsContainer
                        .addView(
                            card
                        )
                }

                updateScrollableSectionHeight(
                    scrollView = importantAnnouncementsScrollView,
                    container = importantAnnouncementsContainer,
                    visibleItemCount = 3
                )

            } catch (e: Exception) {

                e.printStackTrace()

                importantAnnouncementsContainer
                    .removeAllViews()

                tvNoImportantAnnouncements.visibility =
                    View.VISIBLE

                resetScrollableSectionHeight(
                    importantAnnouncementsScrollView
                )
            }
        }
    }

    // =====================================================
    // ACTUAL FIRESTORE → ROOM REFRESH
    // =====================================================

    private fun syncData(isSilent: Boolean = false) {

        if (isSyncing) {
            return
        }

        isSyncing = true
        btnSync.isEnabled = false

        startRefreshAnimation()

        updateConnectionStatus()

        // =================================================
        // CHECK INTERNET FIRST
        // =================================================

        if (!isInternetAvailable()) {

            stopRefreshAnimation()

            if (!isSilent) {
                showSingleToast(
                    "No internet connection. Showing saved information.",
                    Toast.LENGTH_SHORT
                )
            }

            return
        }

        // =================================================
        // FIRESTORE → ROOM
        // =================================================

        lifecycleScope.launch {

            try {

                // Download latest data from Firestore
                // FirestoreSyncManager will save it into Room.
                withContext(Dispatchers.IO) {

                    firestoreSyncManager
                        .syncAllData()
                }

                // =================================================
                // UPDATE LAST UPDATED
                // =================================================

                val dateTime =
                    SimpleDateFormat(
                        "MMM dd, yyyy • hh:mm a",
                        Locale.getDefault()
                    ).format(
                        Date()
                    )

                getSharedPreferences(
                    "EReQuestPreferences",
                    MODE_PRIVATE
                )
                    .edit()
                    .putString(
                        "last_updated",
                        dateTime
                    )
                    .apply()

                tvLastUpdated.text =
                    "Last updated: $dateTime"

                // =================================================
                // RELOAD UPDATED ROOM DATA
                // =================================================

                loadSearchData()

                displayUpdateNotifications()

                displayImportantAnnouncements()

                updateConnectionStatus()

            } catch (e: Exception) {

                e.printStackTrace()

            } finally {

                stopRefreshAnimation()
            }
        }
    }

    // =====================================================
    // START REFRESH ANIMATION
    // =====================================================

    private fun startRefreshAnimation() {

        btnSync.animate()
            .rotationBy(360f)
            .setDuration(700L)
            .start()
    }

    // =====================================================
    // STOP REFRESH ANIMATION
    // =====================================================

    private fun stopRefreshAnimation() {

        btnSync.isEnabled =
            true

        isSyncing =
            false
    }

    // =====================================================
    // CONNECTION STATUS
    // =====================================================

    private fun updateConnectionStatus() {

        tvConnectionStatus.text =
            if (
                isInternetAvailable()
            ) {

                "● Online"

            } else {

                "● Offline"
            }
    }

    // =====================================================
    // CHECK INTERNET
    // =====================================================

    private fun isInternetAvailable(): Boolean {

        val manager =
            getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

        val network =
            manager.activeNetwork
                ?: return false

        val capabilities =
            manager.getNetworkCapabilities(
                network
            )
                ?: return false

        return capabilities
            .hasCapability(
                NetworkCapabilities
                    .NET_CAPABILITY_INTERNET
            )
    }

    // =====================================================
    // LOAD LAST UPDATED
    // =====================================================

    private fun loadLastUpdated() {

        val lastUpdated =
            getSharedPreferences(
                "EReQuestPreferences",
                MODE_PRIVATE
            )
                .getString(
                    "last_updated",
                    null
                )

        tvLastUpdated.text =
            if (
                lastUpdated == null
            ) {

                "Last updated: Never"

            } else {

                "Last updated: $lastUpdated"
            }
    }

    // =====================================================
    // ANDROID SYSTEM NOTIFICATIONS
    // =====================================================

    private fun requestNotificationPermissionIfNeeded() {

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }


    // =====================================================
    // LIMIT HOME SECTIONS TO 3 VISIBLE CARDS
    // =====================================================

    private fun updateScrollableSectionHeight(
        scrollView: ScrollView,
        container: LinearLayout,
        visibleItemCount: Int
    ) {

        container.post {

            if (container.childCount <= visibleItemCount) {

                resetScrollableSectionHeight(
                    scrollView
                )

                return@post
            }

            // Ensure container has a valid non-zero width before measuring children
            if (container.width == 0) {
                container.viewTreeObserver.addOnGlobalLayoutListener(
                    object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            updateScrollableSectionHeight(
                                scrollView,
                                container,
                                visibleItemCount
                            )
                        }
                    }
                )
                return@post
            }

            var targetHeight = 0
            val countToMeasure = minOf(visibleItemCount, container.childCount)

            for (index in 0 until countToMeasure) {

                val child =
                    container.getChildAt(index) ?: continue

                val widthSpec =
                    View.MeasureSpec.makeMeasureSpec(
                        container.width,
                        View.MeasureSpec.EXACTLY
                    )

                val heightSpec =
                    View.MeasureSpec.makeMeasureSpec(
                        0,
                        View.MeasureSpec.UNSPECIFIED
                    )

                child.measure(
                    widthSpec,
                    heightSpec
                )

                targetHeight +=
                    child.measuredHeight

                val params =
                    child.layoutParams

                if (
                    params is ViewGroup.MarginLayoutParams
                ) {

                    targetHeight +=
                        params.topMargin +
                                params.bottomMargin
                }
            }

            if (targetHeight > 0) {
                if (scrollView.layoutParams.height != targetHeight) {
                    scrollView.layoutParams =
                        scrollView.layoutParams.apply {
                            height = targetHeight
                        }
                    scrollView.requestLayout()
                }
            }
        }
    }


    private fun resetScrollableSectionHeight(
        scrollView: ScrollView
    ) {

        if (scrollView.layoutParams.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            scrollView.layoutParams =
                scrollView.layoutParams.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            scrollView.requestLayout()
        }
    }


    // =====================================================
    // DP HELPER
    // =====================================================

    private fun Int.dp(): Int {

        return (
                this *
                        resources
                            .displayMetrics
                            .density
                ).toInt()
    }

    // =====================================================
    // DESTROY
    // =====================================================

    override fun onDestroy() {

        val rootView = findViewById<ViewGroup>(android.R.id.content)
        dismissBannerRunnable?.let { rootView?.removeCallbacks(it) }
        dismissBannerRunnable = null
        activeBannerView?.let { rootView?.removeView(it) }
        activeBannerView = null

        syncAnimator?.cancel()

        FirestoreSyncManager.unregisterSyncCompletionListener(
            backgroundSyncCompletionListener
        )

        if (::networkMonitor.isInitialized) {
            networkMonitor.stop()
        }

        if (::firestoreUpdateListener.isInitialized) {
            firestoreUpdateListener.stop()
        }

        super.onDestroy()
    }
}