package rocks.gorjan.gokixp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max

/**
 * One independent movable/resizable Classic widget on one launcher page.
 * Normal mode forwards touches to the original live widget. Holding 430 ms enters
 * edit mode; drag the center to move, or any corner to resize both axes independently.
 * The game/WebView remains resident throughout a resize; no replay or re-creation.
 */
class Win98ResizableWidgetHost(
    context: Context,
    private val logicalWidth: Int,
    private val minimumWidth: Int,
    private val minimumHeight: Int,
    private val content: View,
    private val saveGeometry: (Int, Int, Int, Int) -> Unit,
    private val onEditing: (Boolean) -> Unit
) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val cornerTouch = dp(30)
    private val cornerVisual = dp(12)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var naturalHeight = 0
    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downWidth = 0
    private var downHeight = 0
    private var editingArmed = false
    private var gestureMode = 0 // 0 untouched, 1 move, 2 TL, 3 TR, 4 BL, 5 BR
    private var moved = false
    private var wasHolding = false
    private var interceptedChild = false
    private var pendingArm: Runnable? = null
    var isEditing: Boolean = false
        private set

    init {
        clipChildren = true
        clipToPadding = false
        isClickable = true
        addView(content, LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setWillNotDraw(false)
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value + 0.5f).toInt()

    fun isMoveArmed(): Boolean = editingArmed || isEditing

    fun finishEditing() {
        if (!isEditing) return
        isEditing = false
        editingArmed = false
        gestureMode = 0
        invalidate()
        onEditing(false)
    }

    private fun stopPending() {
        pendingArm?.let { handler.removeCallbacks(it) }
        pendingArm = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val baseW = logicalWidth.coerceAtLeast(1)
        content.measure(
            MeasureSpec.makeMeasureSpec(baseW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(1050), MeasureSpec.AT_MOST)
        )
        naturalHeight = content.measuredHeight.coerceAtLeast(dp(96))
        val targetWidth = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED)
            baseW else MeasureSpec.getSize(widthMeasureSpec)
        val targetHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY)
            MeasureSpec.getSize(heightMeasureSpec) else naturalHeight
        setMeasuredDimension(
            resolveSize(targetWidth, widthMeasureSpec),
            resolveSize(targetHeight, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val h = naturalHeight.coerceAtLeast(1)
        content.layout(0, 0, logicalWidth, h)
        content.pivotX = 0f
        content.pivotY = 0f
        content.scaleX = width.toFloat() / logicalWidth.coerceAtLeast(1)
        content.scaleY = height.toFloat() / h
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!isEditing) return
        val w = width.toFloat()
        val h = height.toFloat()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2).toFloat()
        paint.color = Color.rgb(0, 0, 128)
        canvas.drawRect(1f, 1f, w - 1f, h - 1f, paint)
        paint.style = Paint.Style.FILL
        for (cx in floatArrayOf(0f, w)) for (cy in floatArrayOf(0f, h)) {
            val half = cornerVisual / 2f
            val left = (cx - half).coerceIn(0f, (w - cornerVisual).coerceAtLeast(0f))
            val top = (cy - half).coerceIn(0f, (h - cornerVisual).coerceAtLeast(0f))
            paint.color = Color.rgb(48, 48, 48)
            canvas.drawRect(left, top, left + cornerVisual, top + cornerVisual, paint)
            paint.color = Color.rgb(215, 215, 215)
            canvas.drawRect(left + dp(2), top + dp(2),
                left + cornerVisual - dp(2), top + cornerVisual - dp(2), paint)
        }
    }

    private fun hitMode(event: MotionEvent): Int {
        val left = event.x <= cornerTouch
        val right = event.x >= width - cornerTouch
        val top = event.y <= cornerTouch
        val bottom = event.y >= height - cornerTouch
        return when {
            left && top -> 2
            right && top -> 3
            left && bottom -> 4
            right && bottom -> 5
            else -> 1
        }
    }

    // Deliberately use dispatchTouchEvent: embedded WebViews and game controllers
    // can disallow ancestor interception, but a genuine held finger must still
    // activate resizing without breaking the short-tap gameplay path.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopPending()
                downRawX = event.rawX
                downRawY = event.rawY
                downX = x
                downY = y
                downWidth = width
                downHeight = height
                moved = false
                wasHolding = false
                editingArmed = isEditing
                gestureMode = if (isEditing) hitMode(event) else 0
                if (!isEditing) {
                    val arm = Runnable {
                        wasHolding = true
                        isEditing = true
                        editingArmed = true
                        gestureMode = 1
                        onEditing(true)
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        invalidate()
                    }
                    pendingArm = arm
                    handler.postDelayed(arm, 430L)
                }
                if (isEditing) return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (editingArmed && isEditing) {
                    if (!interceptedChild && wasHolding) {
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        interceptedChild = true
                    }
                    applyGesture(event)
                    return true
                }
                if (abs(event.rawX - downRawX) > slop ||
                    abs(event.rawY - downRawY) > slop) stopPending()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopPending()
                if (editingArmed && isEditing) {
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        if (moved) saveGeometry(x.toInt(), y.toInt(), width, height)
                        else if (!wasHolding && gestureMode == 1) finishEditing()
                    } else if (moved) {
                        saveGeometry(x.toInt(), y.toInt(), width, height)
                    }
                    editingArmed = false
                    interceptedChild = false
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> stopPending()
        }
        return super.dispatchTouchEvent(event)
    }

    private fun applyGesture(event: MotionEvent) {
        val dx = event.rawX - downRawX
        val dy = event.rawY - downRawY
        if (abs(dx) < 1f && abs(dy) < 1f) return
        val owner = parent as? ViewGroup ?: return
        val maxW = owner.width.coerceAtLeast(minimumWidth)
        val maxH = owner.height.coerceAtLeast(minimumHeight)
        val x0 = downX
        val y0 = downY
        val x1 = x0 + downWidth
        val y1 = y0 + downHeight
        val leftSide = gestureMode == 2 || gestureMode == 4
        val rightSide = gestureMode == 3 || gestureMode == 5
        val topSide = gestureMode == 2 || gestureMode == 3
        val bottomSide = gestureMode == 4 || gestureMode == 5

        if (gestureMode == 1) {
            x = (x0 + dx).coerceIn(0f, (maxW - width).coerceAtLeast(0).toFloat())
            y = (y0 + dy).coerceIn(0f, (maxH - height).coerceAtLeast(0).toFloat())
        } else {
            val newLeft = if (leftSide)
                (x0 + dx).coerceIn(0f, x1 - minimumWidth) else x0
            val newTop = if (topSide)
                (y0 + dy).coerceIn(0f, y1 - minimumHeight) else y0
            val newRight = if (rightSide)
                (x1 + dx).coerceIn(newLeft + minimumWidth, maxW.toFloat())
                else x1.coerceAtMost(maxW.toFloat())
            val newBottom = if (bottomSide)
                (y1 + dy).coerceIn(newTop + minimumHeight, maxH.toFloat())
                else y1.coerceAtMost(maxH.toFloat())
            val newW = max(minimumWidth, (newRight - newLeft).toInt())
            val newH = max(minimumHeight, (newBottom - newTop).toInt())
            if (newW != width || newH != height) {
                layoutParams = layoutParams.apply { this.width = newW; this.height = newH }
            }
            x = newLeft
            y = newTop
        }
        moved = true
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopPending()
        isEditing = false
        super.onDetachedFromWindow()
    }
}
