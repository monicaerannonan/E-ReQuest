package com.example.request.ui.custom

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

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ScrollView

/**
 * Custom ScrollView designed for embedding inside a parent ScrollView (such as homeScrollView).
 *
 * Provides bidirectional nested touch arbitration:
 * 1. When the section contains 4+ items (overflowing visible bounds), vertical drags
 *    scroll internally within this section.
 * 2. When the user reaches the edge (top edge when dragging down, or bottom edge when dragging up),
 *    touch events are released back to the parent Homepage ScrollView for seamless scrolling.
 * 3. When the section has 1-3 items (no overflow), touch events pass freely to the parent
 *    Homepage without disallowing intercept.
 * 4. Tap/click events on child cards are preserved through standard dispatchTouchEvent delegation.
 */
class NestedInnerScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private var lastY = 0f

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (canScrollVertically(1) || canScrollVertically(-1)) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = ev.y
                    // Proactively disallow parent ScrollView from intercepting on initial ACTION_MOVE
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = ev.y - lastY
                    lastY = ev.y

                    if (deltaY > 0 && !canScrollVertically(-1)) {
                        // User is dragging DOWN, but this section is already at the TOP:
                        // Allow parent Homepage ScrollView to scroll up.
                        parent?.requestDisallowInterceptTouchEvent(false)
                    } else if (deltaY < 0 && !canScrollVertically(1)) {
                        // User is dragging UP, but this section is already at the BOTTOM:
                        // Allow parent Homepage ScrollView to scroll down.
                        parent?.requestDisallowInterceptTouchEvent(false)
                    } else {
                        // Section has room to scroll in the drag direction: keep touch internal
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        } else {
            // When content fits naturally (1-3 items), do not disallow parent intercept
            if (ev.actionMasked == MotionEvent.ACTION_DOWN ||
                ev.actionMasked == MotionEvent.ACTION_UP ||
                ev.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return super.dispatchTouchEvent(ev)
    }
}
