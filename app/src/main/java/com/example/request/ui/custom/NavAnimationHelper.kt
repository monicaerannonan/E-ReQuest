package com.example.request.ui.custom

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.request.R
import com.example.request.features.home.HomeActivity
import com.example.request.features.progress.ProgressActivity
import com.example.request.features.services.ServicesActivity

object NavAnimationHelper {

    private var lastNavTime = 0L

    private data class TabData(
        val index: Int,
        val container: ViewGroup,
        val pillFrame: ViewGroup,
        val iconView: ImageView?,
        val textView: TextView?,
        val activeIconRes: Int,
        val inactiveIconRes: Int,
        val targetActivityClass: Class<out Activity>
    )

    /**
     * Initializes the bottom navigation with a shared sliding pill indicator.
     * Snappy, instantaneous navigation without sluggish delays or shockwave rings.
     */
    fun setupSlidingBottomNav(
        activity: Activity,
        activeTabIndex: Int
    ) {
        val bottomNavContainer = activity.findViewById<ViewGroup>(R.id.bottomNavigation) ?: return
        val slidingIndicator = activity.findViewById<View>(R.id.navSlidingIndicator) ?: return
        val navHome = activity.findViewById<ViewGroup>(R.id.navHome) ?: return
        val navServices = activity.findViewById<ViewGroup>(R.id.navServices) ?: return
        val navProgress = activity.findViewById<ViewGroup>(R.id.navProgress) ?: return

        bottomNavContainer.clipChildren = false
        bottomNavContainer.clipToPadding = false

        val rawTabs = listOf(
            Triple(navHome, Pair(R.drawable.ic_nav_home_active, R.drawable.ic_nav_home_inactive), HomeActivity::class.java),
            Triple(navServices, Pair(R.drawable.ic_nav_services_active, R.drawable.ic_nav_services_inactive), ServicesActivity::class.java),
            Triple(navProgress, Pair(R.drawable.ic_nav_progress_active, R.drawable.ic_nav_progress_inactive), ProgressActivity::class.java)
        )

        val tabDataList = rawTabs.mapIndexed { index, (tabView, icons, targetClass) ->
            tabView.clipChildren = false
            tabView.clipToPadding = false

            val pillGroup = tabView.getChildAt(0) as ViewGroup
            pillGroup.clipChildren = false
            pillGroup.clipToPadding = false

            // Ensure individual tab pill background is transparent; slidingIndicator is the shared pill
            pillGroup.setBackgroundResource(android.R.color.transparent)

            val iconView = (0 until pillGroup.childCount)
                .map { pillGroup.getChildAt(it) }
                .filterIsInstance<ImageView>()
                .firstOrNull()

            val textView = tabView.getChildAt(1) as? TextView

            TabData(
                index = index,
                container = tabView,
                pillFrame = pillGroup,
                iconView = iconView,
                textView = textView,
                activeIconRes = icons.first,
                inactiveIconRes = icons.second,
                targetActivityClass = targetClass
            )
        }

        // Align sliding indicator initially
        fun positionIndicator() {
            if (activity.isFinishing || activity.isDestroyed) return
            val activeTab = tabDataList.getOrNull(activeTabIndex) ?: return
            if (activeTab.container.width == 0) return

            val targetX = activeTab.container.left + (activeTab.container.width - slidingIndicator.width) / 2f
            val targetY = activeTab.container.top + activeTab.pillFrame.top

            slidingIndicator.x = targetX
            slidingIndicator.y = targetY.toFloat()
            slidingIndicator.visibility = View.VISIBLE
        }

        bottomNavContainer.post { positionIndicator() }
        bottomNavContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            positionIndicator()
        }

        // Wire snappy click handlers
        tabDataList.forEach { tab ->
            attachFastSlidingTabClick(
                activity = activity,
                currentTabIndex = activeTabIndex,
                targetTab = tab,
                allTabs = tabDataList,
                slidingIndicator = slidingIndicator
            )
        }
    }

    private fun attachFastSlidingTabClick(
        activity: Activity,
        currentTabIndex: Int,
        targetTab: TabData,
        allTabs: List<TabData>,
        slidingIndicator: View
    ) {
        val tabView = targetTab.container

        tabView.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            if (targetTab.index == currentTabIndex) {
                // Tapping current tab: subtle quick bounce in place, no page reload
                slidingIndicator.animate().cancel()
                slidingIndicator.animate()
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(70)
                    .withEndAction {
                        slidingIndicator.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(110)
                            .setInterpolator(OvershootInterpolator(2.2f))
                            .start()
                    }
                    .start()
                return@setOnClickListener
            }

            val now = SystemClock.elapsedRealtime()
            if (now - lastNavTime < 250) return@setOnClickListener
            lastNavTime = now

            // 1. Fast sliding pill animation to the new tab position
            val targetX = targetTab.container.left +
                (targetTab.container.width - slidingIndicator.width) / 2f

            slidingIndicator.animate().cancel()
            slidingIndicator.animate()
                .x(targetX)
                .scaleX(1.08f)
                .setDuration(80)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .withEndAction {
                    slidingIndicator.animate()
                        .scaleX(1.0f)
                        .setDuration(50)
                        .start()
                }
                .start()

            // 2. Instantly update tab states (colors & icons)
            val currentTab = allTabs.getOrNull(currentTabIndex)
            val colorActive = ContextCompat.getColor(activity, R.color.nav_active_primary)
            val colorInactive = ContextCompat.getColor(activity, R.color.nav_inactive)

            // Deactivate leaving tab
            currentTab?.let { cur ->
                cur.iconView?.setImageResource(cur.inactiveIconRes)
                cur.textView?.setTextColor(colorInactive)
                cur.textView?.typeface = Typeface.create(cur.textView?.typeface, Typeface.NORMAL)
            }

            // Activate entering tab
            targetTab.iconView?.setImageResource(targetTab.activeIconRes)
            targetTab.textView?.setTextColor(colorActive)
            targetTab.textView?.typeface = Typeface.create(targetTab.textView?.typeface, Typeface.BOLD)

            // 3. Fast navigation (35ms headstart allows slide animation to begin while activity transitions)
            tabView.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    val intent = Intent(activity, targetTab.targetActivityClass).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    seamlessNavigate(activity, intent)
                }
            }, 35)
        }
    }

    /**
     * Retained for backward compatibility.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun applyNavBouncePress(
        tabView: View,
        onClick: () -> Unit
    ) {
        tabView.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val now = SystemClock.elapsedRealtime()
            if (now - lastNavTime > 250) {
                lastNavTime = now
                v.postDelayed({ onClick() }, 40)
            }
        }
    }

    /**
     * Seamlessly transitions to the target Activity without jerky window transitions,
     * keeping the bottom navigation bar looking fixed and rock-solid.
     */
    fun seamlessNavigate(activity: Activity, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
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


