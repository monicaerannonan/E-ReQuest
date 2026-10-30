package com.example.request.features.services

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

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServicesActivity : AppCompatActivity() {

    private lateinit var rvServices: RecyclerView
    private lateinit var tvNoServices: TextView
    private lateinit var navHome: View
    private lateinit var navServices: View
    private lateinit var navProgress: View
    private lateinit var btnRefresh: ImageView

    private lateinit var dao: ERequestDao
    private lateinit var firestoreSyncManager: FirestoreSyncManager
    private lateinit var serviceAdapter: SchoolServiceAdapter

    private var isRefreshing = false
    private var activeToast: Toast? = null

    private fun showSingleToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        activeToast?.cancel()
        activeToast = Toast.makeText(this, message, duration).also { it.show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_services)

        rvServices = findViewById(R.id.rvServices)
        tvNoServices = findViewById(R.id.tvNoServices)
        navHome = findViewById(R.id.navHome)
        navServices = findViewById(R.id.navServices)
        navProgress = findViewById(R.id.navProgress)
        btnRefresh = findViewById(R.id.btnRefresh)

        val database = ERequestDatabase.getDatabase(applicationContext)
        dao = database.eRequestDao()
        firestoreSyncManager = FirestoreSyncManager(applicationContext)

        setupRecyclerView()

        btnRefresh.setOnClickListener {
            refreshData()
        }

        NavAnimationHelper.applyNavBouncePress(navHome) {
            NavAnimationHelper.seamlessNavigate(
                this,
                Intent(this, HomeActivity::class.java)
            )
        }

        NavAnimationHelper.applyNavBouncePress(navServices) {
            // Already on Services page - bounce animation still plays delightfully
        }

        NavAnimationHelper.applyNavBouncePress(navProgress) {
            NavAnimationHelper.seamlessNavigate(
                this,
                Intent(this, ProgressActivity::class.java)
            )
        }

        loadServices()
    }

    override fun onResume() {
        super.onResume()
        loadServices()
    }

    private fun setupRecyclerView() {
        serviceAdapter = SchoolServiceAdapter { service ->
            val intent = Intent(this, TransactionListActivity::class.java).apply {
                putExtra("SERVICE_ID", service.id)
                putExtra("SERVICE_NAME", service.name)
            }
            startActivity(intent)
        }

        rvServices.apply {
            layoutManager = LinearLayoutManager(this@ServicesActivity)
            adapter = serviceAdapter
            setHasFixedSize(true)
        }
    }

    private fun loadServices() {
        lifecycleScope.launch {
            val services = withContext(Dispatchers.IO) {
                dao.getAllServices()
            }
            displayServices(services)
        }
    }

    private fun displayServices(services: List<ServiceEntity>) {
        serviceAdapter.submitList(services) {
            if (!isFinishing && !isDestroyed) {
                if (services.isEmpty()) {
                    rvServices.visibility = View.GONE
                    tvNoServices.visibility = View.VISIBLE
                } else {
                    rvServices.visibility = View.VISIBLE
                    tvNoServices.visibility = View.GONE
                }
            }
        }
    }

    private fun refreshData() {
        if (isRefreshing || FirestoreSyncManager.isCurrentlySyncing()) return
        isRefreshing = true
        btnRefresh.isEnabled = false

        btnRefresh.animate()
            .rotationBy(360f)
            .setDuration(700L)
            .start()

        showSingleToast("Refreshing...")

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    firestoreSyncManager.syncAllData()
                }

                val services = withContext(Dispatchers.IO) {
                    dao.getAllServices()
                }

                displayServices(services)

                showSingleToast("Data updated.")
            } catch (e: Exception) {
                e.printStackTrace()

                val savedServices = withContext(Dispatchers.IO) {
                    dao.getAllServices()
                }

                displayServices(savedServices)

                showSingleToast("Unable to sync. Showing saved data.")
            } finally {
                isRefreshing = false
                btnRefresh.isEnabled = true
            }
        }
    }
}
