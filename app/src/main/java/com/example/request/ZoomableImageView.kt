package com.example.request

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val transformMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    private var minScale = 1.0f
    private var midScale = 2.5f
    private var maxScale = 5.0f
    private var currentScale = 1.0f

    private var baseScale = 1.0f
    private var origWidth = 0f
    private var origHeight = 0f
    private var viewWidth = 0
    private var viewHeight = 0

    private val lastTouchPoint = PointF()
    private var isDragging = false
    private var zoomAnimator: ValueAnimator? = null

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    init {
        super.setScaleType(ScaleType.MATRIX)
        imageMatrix = transformMatrix
        isClickable = true
    }

    override fun setScaleType(scaleType: ScaleType?) {
        // Always use MATRIX internally so fitCenter + zoom transformations work seamlessly
        super.setScaleType(ScaleType.MATRIX)
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        resetToFitCenter()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        resetToFitCenter()
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        resetToFitCenter()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w - paddingLeft - paddingRight
        viewHeight = h - paddingTop - paddingBottom
        resetToFitCenter()
    }

    /**
     * Fits the image to the center of the view while preserving its exact original aspect ratio.
     */
    fun resetToFitCenter() {
        val d = drawable ?: return
        val dWidth = d.intrinsicWidth
        val dHeight = d.intrinsicHeight

        if (dWidth <= 0 || dHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return
        }

        zoomAnimator?.cancel()

        val scaleX = viewWidth.toFloat() / dWidth.toFloat()
        val scaleY = viewHeight.toFloat() / dHeight.toFloat()
        baseScale = min(scaleX, scaleY)

        origWidth = dWidth * baseScale
        origHeight = dHeight * baseScale
        currentScale = minScale

        val redundantXSpace = (viewWidth.toFloat() - origWidth) / 2f
        val redundantYSpace = (viewHeight.toFloat() - origHeight) / 2f

        transformMatrix.reset()
        transformMatrix.postScale(baseScale, baseScale)
        transformMatrix.postTranslate(redundantXSpace, redundantYSpace)
        imageMatrix = transformMatrix
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawable == null) {
            return super.onTouchEvent(event)
        }

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val currentPoint = PointF(event.x, event.y)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                zoomAnimator?.cancel()
                lastTouchPoint.set(currentPoint)
                isDragging = false
                if (currentScale > minScale + 0.01f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && currentScale > minScale + 0.01f) {
                    val dx = currentPoint.x - lastTouchPoint.x
                    val dy = currentPoint.y - lastTouchPoint.y

                    if (abs(dx) > 2f || abs(dy) > 2f) {
                        isDragging = true
                    }

                    if (isDragging) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        val fixX = getFixDragTrans(dx, viewWidth.toFloat(), origWidth * currentScale)
                        val fixY = getFixDragTrans(dy, viewHeight.toFloat(), origHeight * currentScale)
                        transformMatrix.postTranslate(fixX, fixY)
                        constrainMatrixTranslation()
                        imageMatrix = transformMatrix
                        lastTouchPoint.set(currentPoint.x, currentPoint.y)
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                if (currentScale <= minScale + 0.01f) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }

        return true
    }

    private fun applyScaleFactor(scaleFactor: Float, focusX: Float, focusY: Float) {
        val previousScale = currentScale
        var targetScale = currentScale * scaleFactor
        var clampedFactor = scaleFactor

        if (targetScale > maxScale) {
            targetScale = maxScale
            clampedFactor = maxScale / previousScale
        } else if (targetScale < minScale) {
            targetScale = minScale
            clampedFactor = minScale / previousScale
        }

        currentScale = targetScale

        if (origWidth * currentScale <= viewWidth || origHeight * currentScale <= viewHeight) {
            transformMatrix.postScale(
                clampedFactor,
                clampedFactor,
                viewWidth / 2f,
                viewHeight / 2f
            )
        } else {
            transformMatrix.postScale(
                clampedFactor,
                clampedFactor,
                focusX,
                focusY
            )
        }

        constrainMatrixTranslation()
        imageMatrix = transformMatrix
        invalidate()
    }

    private fun animateZoomTo(targetScale: Float, focusX: Float, focusY: Float) {
        zoomAnimator?.cancel()
        val startScale = currentScale

        zoomAnimator = ValueAnimator.ofFloat(startScale, targetScale).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val animatedScale = animator.animatedValue as Float
                if (currentScale > 0f) {
                    val stepFactor = animatedScale / currentScale
                    applyScaleFactor(stepFactor, focusX, focusY)
                }
            }
            start()
        }
    }

    private fun constrainMatrixTranslation() {
        transformMatrix.getValues(matrixValues)
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]

        val fixTransX = getFixTrans(transX, viewWidth.toFloat(), origWidth * currentScale)
        val fixTransY = getFixTrans(transY, viewHeight.toFloat(), origHeight * currentScale)

        if (fixTransX != 0f || fixTransY != 0f) {
            transformMatrix.postTranslate(fixTransX, fixTransY)
        }
    }

    private fun getFixTrans(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float

        if (contentSize <= viewSize) {
            minTrans = (viewSize - contentSize) / 2f
            maxTrans = (viewSize - contentSize) / 2f
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }

        return when {
            trans < minTrans -> minTrans - trans
            trans > maxTrans -> maxTrans - trans
            else -> 0f
        }
    }

    private fun getFixDragTrans(delta: Float, viewSize: Float, contentSize: Float): Float {
        return if (contentSize <= viewSize) 0f else delta
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            applyScaleFactor(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            toggleTapZoom(e.x, e.y)
            performClick()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            toggleTapZoom(e.x, e.y)
            return true
        }

        private fun toggleTapZoom(focusX: Float, focusY: Float) {
            val destinationScale = if (currentScale > minScale + 0.1f) {
                minScale
            } else {
                max(midScale, minScale)
            }
            animateZoomTo(destinationScale, focusX, focusY)
        }
    }
}
