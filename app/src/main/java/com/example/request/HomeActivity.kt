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
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.EditText
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
        ) { }

    // IDs already shown as Android system notifications during this activity session.
    private val shownSystemNotificationIds =
        mutableSetOf<String>()



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

                    if (!isSyncing) {
                        syncData()
                    }
                }
            }

        networkMonitor.start()

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

                        syncData()
                    }
                }
            }

        firestoreUpdateListener.start()

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

                syncData()
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
        }

        navProgress.setOnClickListener {

            val intent =
                Intent(
                    this,
                    ProgressActivity::class.java
                )

            startActivity(intent)
        }
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

                showNewSystemNotifications(
                    updates
                )

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
                // EMPTY MESSAGE
                // =================================================

                tvNoUpdateNotifications.visibility =
                    if (updates.isEmpty()) {

                        View.VISIBLE

                    } else {

                        View.GONE
                    }

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
            }
        }
    }

    // =====================================================
    // ACTUAL FIRESTORE → ROOM REFRESH
    // =====================================================

    private fun syncData() {

        if (isSyncing) {

            return
        }

        isSyncing =
            true

        startRefreshAnimation()

        updateConnectionStatus()

        // =================================================
        // CHECK INTERNET FIRST
        // =================================================

        if (!isInternetAvailable()) {

            stopRefreshAnimation()

            Toast.makeText(
                this,
                "No internet connection. Showing saved information.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        Toast.makeText(
            this,
            "Checking for updates...",
            Toast.LENGTH_SHORT
        ).show()

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

                Toast.makeText(
                    this@HomeActivity,
                    "Latest information downloaded.",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {

                e.printStackTrace()

                Toast.makeText(
                    this@HomeActivity,
                    "Unable to sync. Saved information is still available.",
                    Toast.LENGTH_LONG
                ).show()

            } finally {

                stopRefreshAnimation()
            }
        }
    }

    // =====================================================
    // START REFRESH ANIMATION
    // =====================================================

    private fun startRefreshAnimation() {

        syncAnimator?.cancel()

        syncAnimator =
            ObjectAnimator.ofFloat(
                btnSync,
                "rotation",
                0f,
                360f
            ).apply {

                duration =
                    700

                repeatCount =
                    ValueAnimator.INFINITE

                repeatMode =
                    ValueAnimator.RESTART

                interpolator =
                    LinearInterpolator()

                start()
            }
    }

    // =====================================================
    // STOP REFRESH ANIMATION
    // =====================================================

    private fun stopRefreshAnimation() {

        syncAnimator?.cancel()

        syncAnimator =
            null

        btnSync.rotation =
            0f

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


    private fun showNewSystemNotifications(
        updates: List<UpdateNotification>
    ) {

        updates.forEach { update ->

            if (
                shownSystemNotificationIds.add(
                    update.id
                )
            ) {
                showSystemNotification(
                    update
                )
            }
        }
    }


    private fun showSystemNotification(
        update: UpdateNotification
    ) {

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

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

                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                update.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat.Builder(
                this,
                ERequestApplication.UPDATES_CHANNEL_ID
            )
                .setSmallIcon(
                    R.mipmap.ic_launcher
                )
                .setContentTitle(
                    update.transactionName
                        .ifBlank {
                            "E-Request Update"
                        }
                )
                .setContentText(
                    update.message
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(
                            update.message
                        )
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setCategory(
                    NotificationCompat.CATEGORY_STATUS
                )
                .setAutoCancel(true)
                .setContentIntent(
                    pendingIntent
                )
                .build()

        NotificationManagerCompat
            .from(this)
            .notify(
                update.id.hashCode(),
                notification
            )
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

            var targetHeight = 0

            for (index in 0 until visibleItemCount) {

                val child =
                    container.getChildAt(index)

                if (child.measuredHeight == 0) {

                    child.measure(
                        View.MeasureSpec.makeMeasureSpec(
                            container.width,
                            View.MeasureSpec.EXACTLY
                        ),
                        View.MeasureSpec.makeMeasureSpec(
                            0,
                            View.MeasureSpec.UNSPECIFIED
                        )
                    )
                }

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

            scrollView.layoutParams =
                scrollView.layoutParams.apply {

                    height =
                        targetHeight
                }

            scrollView.requestLayout()
        }
    }


    private fun resetScrollableSectionHeight(
        scrollView: ScrollView
    ) {

        scrollView.layoutParams =
            scrollView.layoutParams.apply {

                height =
                    ViewGroup.LayoutParams.WRAP_CONTENT
            }

        scrollView.requestLayout()
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

        syncAnimator?.cancel()

        if (::networkMonitor.isInitialized) {
            networkMonitor.stop()
        }

        if (::firestoreUpdateListener.isInitialized) {
            firestoreUpdateListener.stop()
        }

        super.onDestroy()
    }
}