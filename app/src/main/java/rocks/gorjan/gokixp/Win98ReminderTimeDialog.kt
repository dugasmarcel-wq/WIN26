package rocks.gorjan.gokixp

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Fully local Classic calendar grid. Android CalendarView was unreadable with
 * the launcher theme and clipped its final week inside the window.
 */
object Win98ReminderTimeDialog {
    fun show(context: Context, existing: Long = 0L, onSave: (Long) -> Unit) {
        val density = context.resources.displayMetrics.density
        fun dp(n: Int) = (density * n + 0.5f).toInt()
        val initial = Calendar.getInstance().apply {
            timeInMillis = if (existing > System.currentTimeMillis()) existing
                else System.currentTimeMillis() + 3600000L
        }
        val selected = initial.clone() as Calendar
        val displayed = initial.clone() as Calendar
        displayed.set(Calendar.DAY_OF_MONTH, 1)
        val today = Calendar.getInstance()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(5))
        }
        fun label(value: String, size: Float = 13f, bold: Boolean = false) =
            TextView(context).apply {
                text = value
                textSize = size
                setTextColor(Color.BLACK)
                if (bold) typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
        fun button(text: String, click: () -> Unit) = label(text, 17f, true).apply {
            background = GradientDrawable().apply {
                setColor(0xFFD3CEC7.toInt())
                setStroke(dp(1), Color.DKGRAY)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val monthTitle = label("", 14f, true)
        val grid = GridLayout(context).apply {
            columnCount = 7
            rowCount = 7
            setBackgroundColor(Color.WHITE)
        }
        val weekday = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val cells = ArrayList<TextView>(42)
        val params = GridLayout.LayoutParams()
        fun rebuild() {
            val fmt = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
            monthTitle.text = fmt.format(displayed.time)
            val start = displayed.clone() as Calendar
            start.set(Calendar.DAY_OF_MONTH, 1)
            val offset = start.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
            val days = start.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (index in 0 until 42) {
                val day = index - offset + 1
                val view = cells[index]
                if (day in 1..days) {
                    view.text = day.toString()
                    view.isEnabled = true
                    view.setTextColor(Color.BLACK)
                    val isSelected = day == selected.get(Calendar.DAY_OF_MONTH) &&
                        displayed.get(Calendar.YEAR) == selected.get(Calendar.YEAR) &&
                        displayed.get(Calendar.MONTH) == selected.get(Calendar.MONTH)
                    val isToday = day == today.get(Calendar.DAY_OF_MONTH) &&
                        displayed.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                        displayed.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    view.background = GradientDrawable().apply {
                        setColor(if (isSelected) 0xFF1351A0.toInt() else Color.WHITE)
                        if (isToday) setStroke(dp(1), Color.DKGRAY)
                    }
                    view.setTextColor(if (isSelected) Color.WHITE else Color.BLACK)
                    view.setOnClickListener {
                        selected.set(Calendar.YEAR, displayed.get(Calendar.YEAR))
                        selected.set(Calendar.MONTH, displayed.get(Calendar.MONTH))
                        selected.set(Calendar.DAY_OF_MONTH, day)
                        rebuild()
                    }
                } else {
                    view.text = ""
                    view.isEnabled = false
                    view.background = GradientDrawable().apply { setColor(Color.WHITE) }
                    view.setOnClickListener(null)
                }
            }
        }
        header.addView(button("‹") { displayed.add(Calendar.MONTH, -1); rebuild() },
            LinearLayout.LayoutParams(dp(43), dp(36)))
        header.addView(monthTitle, LinearLayout.LayoutParams(0, dp(36), 1f))
        header.addView(button("›") { displayed.add(Calendar.MONTH, 1); rebuild() },
            LinearLayout.LayoutParams(dp(43), dp(36)))
        body.addView(header)
        for ((i, name) in weekday.withIndex()) {
            val cell = label(name, 10f, true).apply { setBackgroundColor(0xFFD3CEC7.toInt()) }
            grid.addView(cell, GridLayout.LayoutParams(
                GridLayout.spec(0), GridLayout.spec(i, 1f)).apply {
                    width = 0; height = dp(27)
                })
        }
        for (index in 0 until 42) {
            val day = label("", 13f)
            cells.add(day)
            grid.addView(day, GridLayout.LayoutParams(
                GridLayout.spec(index / 7 + 1), GridLayout.spec(index % 7, 1f)).apply {
                    width = 0; height = dp(39)
                })
        }
        body.addView(grid, LinearLayout.LayoutParams(-1, dp(261)))
        rebuild()
        body.addView(label("Time (24-hour)", 12f).apply { gravity = Gravity.START },
            LinearLayout.LayoutParams(-1, dp(24)))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val hours = NumberPicker(context).apply {
            minValue = 0; maxValue = 23
            value = initial.get(Calendar.HOUR_OF_DAY)
        }
        val minutes = NumberPicker(context).apply {
            minValue = 0; maxValue = 59
            value = initial.get(Calendar.MINUTE)
            setFormatter { String.format(Locale.US, "%02d", it) }
        }
        row.addView(hours, LinearLayout.LayoutParams(dp(84), dp(96)))
        row.addView(label(":", 19f, true), LinearLayout.LayoutParams(dp(22), dp(96)))
        row.addView(minutes, LinearLayout.LayoutParams(dp(84), dp(96)))
        body.addView(row)
        Win98Dialogs.showCustom(context, "Reminder Date & Time", body,
            "Save Reminder", "Cancel", onPositive = {
                selected.set(Calendar.HOUR_OF_DAY, hours.value)
                selected.set(Calendar.MINUTE, minutes.value)
                selected.set(Calendar.SECOND, 0)
                selected.set(Calendar.MILLISECOND, 0)
                if (selected.timeInMillis > System.currentTimeMillis()) {
                    onSave(selected.timeInMillis)
                } else {
                    android.widget.Toast.makeText(context, "Select a future date and time",
                        android.widget.Toast.LENGTH_LONG).show()
                }
            })
    }
}
