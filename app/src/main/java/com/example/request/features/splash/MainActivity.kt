package com.example.request.features.splash

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

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Subtle Logo & Title Entrance Animation
        val ivLogo = findViewById<View>(R.id.ivLogo)
        val tvAppName = findViewById<View>(R.id.tvAppName)
        val tvTagline = findViewById<View>(R.id.tvTagline)

        ivLogo?.let {
            it.alpha = 0f
            it.scaleX = 0.88f
            it.scaleY = 0.88f
            it.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(600)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        tvAppName?.let {
            it.alpha = 0f
            it.translationY = 16f
            it.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(120)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        tvTagline?.let {
            it.alpha = 0f
            it.animate()
                .alpha(0.85f)
                .setDuration(500)
                .setStartDelay(250)
                .start()
        }

        // Sleek Linear Progress Animation (0 to 100% across 1800ms)
        val progressBar = findViewById<ProgressBar>(R.id.progressLoading)
        if (progressBar != null) {
            val progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100)
            progressAnimator.duration = 1800
            progressAnimator.interpolator = DecelerateInterpolator(1.2f)
            progressAnimator.start()
        }

        // Seamless transition to HomeActivity
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)

            // Para hindi makabalik sa Splash Screen
            finish()
        }, 2000)
    }
}
