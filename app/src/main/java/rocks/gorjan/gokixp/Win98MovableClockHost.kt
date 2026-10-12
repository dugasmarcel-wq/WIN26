package rocks.gorjan.gokixp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Movable Classic calendar/clock frame. Hold for 430 ms to move; ordinary taps
 * still reach the month, year, date, and time-format controls inside the clock.
 * Parent page navigation is excluded only for touches that begin on this host.
 */
class Win98MovableClockHost(
    context: Context,
    private val savePosition: (Int, Int) -> Unit
) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f
    private var armed = false
    private var moved = false
    private var pendingArm: Runnable? = null

    init {
        isClickable = true
        isFocusable = false
    }

    fun isMoveArmed(): Boolean = armed

    private fun cancelArm() {
        pendingArm?.let { handler.removeCallbacks(it) }
        pendingArm = null
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelArm()
                armed = false
                moved = false
                downX = ev.rawX
                downY = ev.rawY
                startX = x
                startY = y
                val action = Runnable {
                    armed = true
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                pendingArm = action
                handler.postDelayed(action, 430L)
            }
            MotionEvent.ACTION_MOVE -> {
                if (armed) return true
                if (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop) {
                    cancelArm()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (armed) return true
                cancelArm()
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!armed) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val owner = parent as? ViewGroup ?: return true
                val maxX = (owner.width - width).coerceAtLeast(0).toFloat()
                val maxY = (owner.height - height).coerceAtLeast(0).toFloat()
                x = (startX + event.rawX - downX).coerceIn(0f, maxX)
                y = (startY + event.rawY - downY).coerceIn(0f, maxY)
                moved = true
                return true
            }
            MotionEvent.ACTION_UP -> {
                cancelArm()
                savePosition(x.toInt(), y.toInt())
                armed = false
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelArm()
                if (moved) savePosition(x.toInt(), y.toInt())
                armed = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        cancelArm()
        armed = false
        super.onDetachedFromWindow()
    }
}
