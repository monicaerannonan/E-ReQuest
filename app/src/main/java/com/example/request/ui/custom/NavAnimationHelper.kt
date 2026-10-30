package com.example.request.ui.custom

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.example.request.R

object NavAnimationHelper {

    private var lastNavTime = 0L

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * Attaches a scale & bounce press animation along with an expanding shockwave pulse ring
     * to a navigation tab.
     * - ACTION_DOWN:
     *     * Icon pill and label scale down smoothly with subtle haptic feedback.
     *     * Expanding pulse ring radiates from the icon center (20dp -> 70dp) and fades out.
     * - ACTION_UP:
     *     * Springs back with an overshoot bounce.
     *     * Triggers onClick once the bounce lands (~120ms) and release is inside bounds.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun applyNavBouncePress(
        tabView: View,
        onClick: () -> Unit
    ) {
        val tabGroup = tabView as? ViewGroup
        tabGroup?.clipChildren = false
        tabGroup?.clipToPadding = false

        val pillGroup = tabGroup?.getChildAt(0) as? ViewGroup
        pillGroup?.clipChildren = false
        pillGroup?.clipToPadding = false

        val pillView = pillGroup ?: tabView
        val labelView = tabGroup?.getChildAt(1)

        // Create the expanding pulse glow ring view
        val pulseRing = View(tabView.context).apply {
            val ringSize = dpToPx(context, 32)
            layoutParams = FrameLayout.LayoutParams(ringSize, ringSize).apply {
                gravity = Gravity.CENTER
            }
            setBackgroundResource(R.drawable.bg_nav_pulse_ring)
            alpha = 0f
            scaleX = 0.5f
            scaleY = 0.5f
            isClickable = false
            isFocusable = false
        }

        // Add the pulse ring behind the icon (index 0) if pillGroup is a ViewGroup
        pillGroup?.addView(pulseRing, 0)

        var isActionDown = false

        tabView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isActionDown = true
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

                    // 1. Smooth scale down on pill (icon container)
                    pillView.animate()
                        .scaleX(0.88f)
                        .scaleY(0.88f)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .start()

                    // 2. Subtle scale down on label
                    labelView?.animate()
                        ?.scaleX(0.94f)
                        ?.scaleY(0.94f)
                        ?.setDuration(100)
                        ?.setInterpolator(DecelerateInterpolator())
                        ?.start()

                    // 3. Shockwave / Expanding Pulse Ring Glow
                    pulseRing.animate().cancel()
                    pulseRing.alpha = 0.85f
                    pulseRing.scaleX = 0.6f
                    pulseRing.scaleY = 0.6f
                    pulseRing.animate()
                        .scaleX(2.2f) // expands from ~20dp up to ~70dp
                        .scaleY(2.2f)
                        .alpha(0f)
                        .setDuration(380)
                        .setInterpolator(DecelerateInterpolator(1.4f))
                        .start()

                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isActionDown) {
                        isActionDown = false
                        v.isPressed = false
                        v.performClick()

                        val isInside = event.x >= 0 && event.x <= v.width &&
                                       event.y >= 0 && event.y <= v.height

                        // Spring back with overshoot bounce
                        pillView.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(220)
                            .setInterpolator(OvershootInterpolator(3.5f))
                            .start()

                        labelView?.animate()
                            ?.scaleX(1.0f)
                            ?.scaleY(1.0f)
                            ?.setDuration(220)
                            ?.setInterpolator(OvershootInterpolator(2.5f))
                            ?.start()

                        if (isInside) {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastNavTime > 400) {
                                lastNavTime = now
                                v.postDelayed({
                                    onClick()
                                }, 120)
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (isActionDown) {
                        isActionDown = false
                        v.isPressed = false

                        pillView.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .setInterpolator(DecelerateInterpolator())
                            .start()

                        labelView?.animate()
                            ?.scaleX(1.0f)
                            ?.scaleY(1.0f)
                            ?.setDuration(150)
                            ?.setInterpolator(DecelerateInterpolator())
                            ?.start()

                        pulseRing.animate()
                            .alpha(0f)
                            .setDuration(120)
                            .start()
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val isInside = event.x >= 0 && event.x <= v.width &&
                                   event.y >= 0 && event.y <= v.height
                    if (!isInside && isActionDown) {
                        isActionDown = false
                        v.isPressed = false

                        pillView.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .setInterpolator(DecelerateInterpolator())
                            .start()

                        labelView?.animate()
                            ?.scaleX(1.0f)
                            ?.scaleY(1.0f)
                            ?.setDuration(150)
                            ?.setInterpolator(DecelerateInterpolator())
                            ?.start()

                        pulseRing.animate()
                            .alpha(0f)
                            .setDuration(120)
                            .start()
                    }
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Seamlessly transitions to the target Activity without jerky window transitions,
     * keeping the bottom navigation bar looking fixed and rock-solid.
     */
    fun seamlessNavigate(activity: Activity, intent: Intent) {
        activity.startActivity(intent)
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
        activity.finish()
    }
}
