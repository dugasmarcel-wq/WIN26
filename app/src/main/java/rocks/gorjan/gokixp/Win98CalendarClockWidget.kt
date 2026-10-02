package rocks.gorjan.gokixp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Compact rounded Windows-98-style calendar + clock based on the user's reference.
 * Entirely local; no network access.
 */
object Win98CalendarClockWidget {

    fun create(activity: MainActivity): View = CalendarClockView(activity)

    private class CalendarClockView(
        private val activity: MainActivity
    ) : LinearLayout(activity) {

        private val displayMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        private var selectedYear = Calendar.getInstance().get(Calendar.YEAR)
        private var selectedMonth = Calendar.getInstance().get(Calendar.MONTH)
        private var selectedDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        private lateinit var monthField: TextView
        private lateinit var yearField: TextView
        private lateinit var calendarGrid: GridLayout

        private fun dp(value: Int): Int =
            Win98CalendarClockWidget.dp(context, value)

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(148)
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = roundedPanel(
                fill = Color.rgb(250, 250, 250),
                stroke = Color.rgb(212, 212, 212),
                radiusDp = 28
            )

            addView(buildCalendarPanel(), LayoutParams(0, dp(134), 1.08f))
            addView(View(context).apply {
                setBackgroundColor(Color.rgb(220, 220, 220))
            }, LayoutParams(dp(1), dp(134)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
            addView(buildClockPanel(), LayoutParams(0, dp(134), 0.92f))

            refreshCalendar()
        }

        private fun buildCalendarPanel(): View {
            val panel = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(2), dp(1), dp(2), dp(1))
            }

            val controls = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            monthField = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(3), 0, dp(3), 0)
                background = sunkenField()
                isClickable = true
                isFocusable = true
                setOnClickListener { showMonthPicker() }
            }
            controls.addView(monthField, LayoutParams(0, dp(23), 1f))

            controls.addView(TextView(context).apply {
                text = "▼"
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
                textSize = 6.5f
                background = AppCompatResources.getDrawable(
                    context,
                    R.drawable.window_button_background
                )
                isClickable = true
                setOnClickListener { showMonthPicker() }
            }, LayoutParams(dp(17), dp(23)).apply {
                marginEnd = dp(4)
            })

            yearField = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                background = sunkenField()
            }
            controls.addView(yearField, LayoutParams(dp(44), dp(23)))

            val yearSpin = LinearLayout(context).apply {
                orientation = VERTICAL
            }
            yearSpin.addView(spinButton("▲") { changeYear(1) },
                LayoutParams(dp(17), 0, 1f))
            yearSpin.addView(spinButton("▼") { changeYear(-1) },
                LayoutParams(dp(17), 0, 1f))
            controls.addView(yearSpin, LayoutParams(dp(17), dp(23)).apply {
                marginStart = dp(2)
            })

            panel.addView(controls, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(23)
            ).apply {
                bottomMargin = dp(4)
            })

            val weekdayRow = GridLayout(context).apply {
                columnCount = 7
                rowCount = 1
                setBackgroundColor(Color.rgb(112, 112, 104))
            }
            arrayOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                weekdayRow.addView(TextView(context).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    textSize = 8.5f
                    typeface = Typeface.DEFAULT_BOLD
                }, GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(18)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                })
            }
            panel.addView(weekdayRow, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(18)
            ))

            calendarGrid = GridLayout(context).apply {
                columnCount = 7
                rowCount = 6
                setPadding(0, dp(1), 0, 0)
            }
            panel.addView(calendarGrid, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
            return panel
        }

        private fun buildClockPanel(): View {
            val panel = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), 0, dp(2), 0)
            }

            panel.addView(AnalogClockView(context), LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))

            val digitalRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
            }
            val digital = LiveDigitalClock(context)
            digitalRow.addView(digital, LayoutParams(0, dp(24), 1f))

            val formatSpin = LinearLayout(context).apply {
                orientation = VERTICAL
            }
            formatSpin.addView(spinButton("▲") { digital.toggleFormat() },
                LayoutParams(dp(17), 0, 1f))
            formatSpin.addView(spinButton("▼") { digital.toggleFormat() },
                LayoutParams(dp(17), 0, 1f))
            digitalRow.addView(formatSpin, LayoutParams(dp(17), dp(24)).apply {
                marginStart = dp(2)
            })

            panel.addView(digitalRow, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(24)
            ))
            return panel
        }

        private fun spinButton(label: String, action: () -> Unit): TextView =
            TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 6f
                setTextColor(Color.BLACK)
                background = AppCompatResources.getDrawable(
                    context,
                    R.drawable.window_button_background
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }

        private fun showMonthPicker() {
            val names = DateFormatSymbols.getInstance(Locale.getDefault())
                .months
                .take(12)
                .toTypedArray()
            Win98Dialogs.showList(
                context = activity,
                title = "Choose Month",
                items = names,
                negativeText = "Cancel"
            ) { which ->
                selectedMonth = which.coerceIn(0, 11)
                refreshCalendar()
            }
        }

        private fun changeYear(delta: Int) {
            selectedYear = (selectedYear + delta).coerceIn(1900, 2199)
            refreshCalendar()
        }

        private fun refreshCalendar() {
            displayMonth.set(Calendar.YEAR, selectedYear)
            displayMonth.set(Calendar.MONTH, selectedMonth)
            displayMonth.set(Calendar.DAY_OF_MONTH, 1)

            monthField.text = SimpleDateFormat("MMMM", Locale.getDefault()).format(displayMonth.time)
            yearField.text = selectedYear.toString()
            calendarGrid.removeAllViews()

            val firstWeekday = displayMonth.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
            val daysInMonth = displayMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
            val today = Calendar.getInstance()
            val todayYear = today.get(Calendar.YEAR)
            val todayMonth = today.get(Calendar.MONTH)
            val todayDay = today.get(Calendar.DAY_OF_MONTH)

            repeat(42) { cellIndex ->
                val day = cellIndex - firstWeekday + 1
                val valid = day in 1..daysInMonth
                val isToday = valid &&
                    selectedYear == todayYear &&
                    selectedMonth == todayMonth &&
                    day == todayDay
                val isSelected = valid &&
                    selectedYear == displayMonth.get(Calendar.YEAR) &&
                    selectedMonth == displayMonth.get(Calendar.MONTH) &&
                    day == selectedDay

                val cell = TextView(context).apply {
                    text = if (valid) day.toString() else ""
                    gravity = Gravity.CENTER
                    textSize = 8.2f
                    setTextColor(Color.BLACK)
                    typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    background = when {
                        isToday -> GradientDrawable().apply {
                            setColor(Color.rgb(55, 245, 170))
                            setStroke(dp(1), Color.rgb(0, 145, 105))
                        }
                        isSelected -> GradientDrawable().apply {
                            setColor(Color.rgb(225, 238, 246))
                            setStroke(dp(1), Color.rgb(90, 125, 150))
                        }
                        else -> null
                    }
                    if (valid) {
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            selectedDay = day
                            refreshCalendar()
                        }
                    }
                }

                calendarGrid.addView(cell, GridLayout.LayoutParams().apply {
                    width = 0
                    height = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                })
            }
        }

        private fun sunkenField(): GradientDrawable =
            GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(125, 125, 125))
            }

        private fun roundedPanel(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable =
            GradientDrawable().apply {
                setColor(fill)
                setStroke(dp(1), stroke)
                cornerRadius = dp(radiusDp).toFloat()
            }
    }

    private class LiveDigitalClock(context: Context) : TextView(context) {
        private val handler = Handler(Looper.getMainLooper())
        private var force24Hour: Boolean? = null

        private val ticker = object : Runnable {
            override fun run() {
                updateTime()
                val delay = 1000L - (System.currentTimeMillis() % 1000L)
                handler.postDelayed(this, delay.coerceAtLeast(120L))
            }
        }

        init {
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            textSize = 9.2f
            typeface = Typeface.MONOSPACE
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(context, 1), Color.rgb(125, 125, 125))
            }
            updateTime()
        }

        fun toggleFormat() {
            val system24 = DateFormat.is24HourFormat(context)
            force24Hour = when (force24Hour) {
                null -> !system24
                true -> false
                false -> true
            }
            updateTime()
        }

        private fun updateTime() {
            val is24 = force24Hour ?: DateFormat.is24HourFormat(context)
            val pattern = if (is24) "HH:mm:ss" else "h:mm:ss a"
            text = SimpleDateFormat(pattern, Locale.getDefault()).format(Calendar.getInstance().time)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }

        override fun onDetachedFromWindow() {
            handler.removeCallbacks(ticker)
            super.onDetachedFromWindow()
        }
    }

    private class AnalogClockView(context: Context) : View(context) {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val handPath = Path()

        private val ticker = object : Runnable {
            override fun run() {
                invalidate()
                val delay = 1000L - (System.currentTimeMillis() % 1000L)
                handler.postDelayed(this, delay.coerceAtLeast(120L))
            }
        }

        init {
            setBackgroundColor(Color.TRANSPARENT)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }

        override fun onDetachedFromWindow() {
            handler.removeCallbacks(ticker)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(width, height) * 0.40f
            val now = Calendar.getInstance()

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, radius + dp(context, 5f), paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(context, 0.7f)
            paint.color = Color.rgb(235, 235, 235)
            canvas.drawCircle(cx, cy, radius + dp(context, 5f), paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(0, 139, 136)
            for (i in 0 until 12) {
                val angle = Math.toRadians((i * 30.0) - 90.0)
                val x = cx + cos(angle).toFloat() * radius
                val y = cy + sin(angle).toFloat() * radius
                canvas.drawCircle(x, y, dp(context, 1.8f), paint)
            }

            val second = now.get(Calendar.SECOND) + now.get(Calendar.MILLISECOND) / 1000f
            val minute = now.get(Calendar.MINUTE) + second / 60f
            val hour = (now.get(Calendar.HOUR) % 12) + minute / 60f

            drawTealHand(canvas, cx, cy, radius * 0.52f, hour * 30f - 90f, dp(context, 5.2f))
            drawTealHand(canvas, cx, cy, radius * 0.78f, minute * 6f - 90f, dp(context, 3.1f))

            paint.color = Color.BLACK
            paint.strokeWidth = dp(context, 0.8f)
            paint.style = Paint.Style.STROKE
            val secondAngle = Math.toRadians((second * 6f - 90f).toDouble())
            canvas.drawLine(
                cx,
                cy,
                cx + cos(secondAngle).toFloat() * radius * 0.88f,
                cy + sin(secondAngle).toFloat() * radius * 0.88f,
                paint
            )

            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            canvas.drawCircle(cx, cy, dp(context, 1.8f), paint)
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, dp(context, 0.7f), paint)
        }

        private fun drawTealHand(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            length: Float,
            degrees: Float,
            halfWidth: Float
        ) {
            val angle = Math.toRadians(degrees.toDouble())
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            val px = -dy
            val py = dx

            handPath.reset()
            handPath.moveTo(cx + px * halfWidth, cy + py * halfWidth)
            handPath.lineTo(cx + dx * length, cy + dy * length)
            handPath.lineTo(cx - px * halfWidth, cy - py * halfWidth)
            handPath.close()

            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(0, 145, 140)
            canvas.drawPath(handPath, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(context, 0.7f)
            paint.color = Color.rgb(0, 90, 88)
            canvas.drawPath(handPath, paint)
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density
}
