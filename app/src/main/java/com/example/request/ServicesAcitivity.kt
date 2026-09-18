package com.example.request

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.request.model.SchoolService

class ServicesActivity : AppCompatActivity() {

    private lateinit var servicesContainer: LinearLayout
    private lateinit var tvNoServices: TextView
    private lateinit var navHome: TextView
    private lateinit var navServices: TextView
    private lateinit var navProgress: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_services)

        servicesContainer = findViewById(R.id.servicesContainer)
        tvNoServices = findViewById(R.id.tvNoServices)
        navHome = findViewById(R.id.navHome)
        navServices = findViewById(R.id.navServices)
        navProgress = findViewById(R.id.navProgress)

        val schoolServices = getTemporaryServices()
        displayServices(schoolServices)

        navHome.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        navServices.setOnClickListener {
            // Already on Services
        }

        navProgress.setOnClickListener {
            startActivity(Intent(this, ProgressActivity::class.java))
            finish()
        }
    }

    private fun getTemporaryServices(): List<SchoolService> {
        return listOf(
            SchoolService(
                id = "service_001",
                name = "Registrar Services",
                description = "Documents, records and student requests",
                iconLetter = "R",
                isActive = true,
                displayOrder = 1
            ),
            SchoolService(
                id = "service_002",
                name = "Academic Services",
                description = "Academic procedures and student concerns",
                iconLetter = "A",
                isActive = true,
                displayOrder = 2
            )
        )
    }

    private fun displayServices(services: List<SchoolService>) {
        servicesContainer.removeAllViews()

        val activeServices = services
            .filter { it.isActive }
            .sortedBy { it.displayOrder }

        if (activeServices.isEmpty()) {
            tvNoServices.visibility = View.VISIBLE
            return
        }

        tvNoServices.visibility = View.GONE

        activeServices.forEach { service ->
            val serviceView = LayoutInflater.from(this).inflate(
                R.layout.item_school_service,
                servicesContainer,
                false
            )

            val tvServiceIcon = serviceView.findViewById<TextView>(R.id.tvServiceIcon)
            val tvServiceName = serviceView.findViewById<TextView>(R.id.tvServiceName)
            val tvServiceDescription = serviceView.findViewById<TextView>(R.id.tvServiceDescription)

            tvServiceIcon.text = service.iconLetter
            tvServiceName.text = service.name
            tvServiceDescription.text = service.description

            serviceView.setOnClickListener {
                val intent = Intent(this, TransactionListActivity::class.java).apply {
                    putExtra("SERVICE_ID", service.id)
                    putExtra("SERVICE_NAME", service.name)
                }
                startActivity(intent)
            }

            servicesContainer.addView(serviceView)
        }
    }
}