package dk.ftb.imageduplicationchecker.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView

/**
 * A full-screen, movable image viewer with pinch-zoom, drag-pan and double-tap-to-zoom.
 * Built on top of [ScaleGestureDetector] + [GestureDetector] + [Matrix] with three
 * degrees of freedom: [scale], [transX] and [transY]. The matrix is rebuilt from scratch
 * on every change to avoid floating-point drift.
 */
class ZoomableImageView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

	private var scale = 1f
	private var transX = 0f
	private var transY = 0f
	private var minScale = 1f
	private val maxScale = 8f

	val isZoomed: Boolean
		get() = scale > minScale * 1.01f

	/** Fired on a confirmed single tap (waits so it doesn't conflict with double-tap). */
	var onSingleTap: (() -> Unit)? = null

	private val matrixBuffer = Matrix()

	private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
		override fun onScale(detector: ScaleGestureDetector): Boolean {
			val newScale = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)
			val ratio = if (scale != 0f) newScale / scale else 1f
			// Keep the focus point visually fixed: trans' = focusX - (focusX - trans) * ratio
			transX = detector.focusX - (detector.focusX - transX) * ratio
			transY = detector.focusY - (detector.focusY - transY) * ratio
			scale = newScale
			clampTranslation()
			applyMatrix()
			return true
		}
	})

	private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
		override fun onScroll(
			e1: MotionEvent?,
			e2: MotionEvent,
			distanceX: Float,
			distanceY: Float
		): Boolean {
			if (!isZoomed) return false
			transX -= distanceX
			transY -= distanceY
			clampTranslation()
			applyMatrix()
			return true
		}

		override fun onDoubleTap(e: MotionEvent): Boolean {
			val targetScale = if (isZoomed) minScale else (minScale * 2.5f).coerceAtMost(maxScale)
			animateZoom(targetScale, e.x, e.y)
			return true
		}

		override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
			onSingleTap?.invoke()
			return true
		}
	})

	init {
		scaleType = ScaleType.MATRIX
		scaleDetector.isQuickScaleEnabled = true
	}

	override fun setImageBitmap(bm: android.graphics.Bitmap?) {
		super.setImageBitmap(bm)
		computeMinScale()
		resetTransform()
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		computeMinScale()
		resetTransform()
	}

	private fun computeMinScale() {
		val vw = width.toFloat()
		val vh = height.toFloat()
		val iw = drawable?.intrinsicWidth?.toFloat() ?: 0f
		val ih = drawable?.intrinsicHeight?.toFloat() ?: 0f
		if (vw > 0f && vh > 0f && iw > 0f && ih > 0f) {
			minScale = minOf(vw / iw, vh / ih)
			if (scale < minScale) scale = minScale
			clampTranslation()
			applyMatrix()
		}
	}

	private fun resetTransform() {
		scale = minScale
		transX = 0f
		transY = 0f
		clampTranslation()
		applyMatrix()
	}

	private fun clampTranslation() {
		val vw = width.toFloat()
		val vh = height.toFloat()
		if (vw <= 0f || vh <= 0f) return
		val iw = (drawable?.intrinsicWidth ?: 0).toFloat()
		val ih = (drawable?.intrinsicHeight ?: 0).toFloat()
		if (iw <= 0f || ih <= 0f) return
		val scaledW = iw * scale
		val scaledH = ih * scale
		transX = if (scaledW <= vw) {
			(vw - scaledW) / 2f
		} else {
			transX.coerceIn(vw - scaledW, 0f)
		}
		transY = if (scaledH <= vh) {
			(vh - scaledH) / 2f
		} else {
			transY.coerceIn(vh - scaledH, 0f)
		}
	}

	private fun applyMatrix() {
		// transX/transY are the view-space coordinates of the scaled image's top-left corner.
		matrixBuffer.reset()
		matrixBuffer.postScale(scale, scale)
		matrixBuffer.postTranslate(transX, transY)
		imageMatrix = matrixBuffer
	}

	private fun animateZoom(targetScale: Float, focusX: Float, focusY: Float) {
		val startScale = scale
		val startTransX = transX
		val startTransY = transY
		// Compute the translation that would keep focus fixed at targetScale.
		val ratio = if (scale != 0f) targetScale / scale else 1f
		val endTransX = focusX - (focusX - transX) * ratio
		val endTransY = focusY - (focusY - transY) * ratio
		val animator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = 250
			interpolator = DecelerateInterpolator()
			addUpdateListener { a ->
				val t = a.animatedValue as Float
				scale = startScale + (targetScale - startScale) * t
				transX = startTransX + (endTransX - startTransX) * t
				transY = startTransY + (endTransY - startTransY) * t
				clampTranslation()
				applyMatrix()
			}
		}
		animator.start()
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		scaleDetector.onTouchEvent(event)
		gestureDetector.onTouchEvent(event)
		if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
			if (scale < minScale) {
				animateZoom(minScale, width / 2f, height / 2f)
			}
		}
		return true
	}
}
