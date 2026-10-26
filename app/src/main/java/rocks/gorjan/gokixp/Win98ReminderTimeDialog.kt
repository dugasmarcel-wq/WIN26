package rocks.gorjan.gokixp

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.CalendarView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

/** Compact clickable calendar and exact hour/minute controls in Classic chrome. */
object Win98ReminderTimeDialog {
    fun show(context: Context, existing: Long = 0L, onSave: (Long) -> Unit) {
        val density = context.resources.displayMetrics.density
        fun dp(n: Int) = (n * density + 0.5f).toInt()
        val initial = Calendar.getInstance().apply {
            timeInMillis = if (existing > System.currentTimeMillis()) existing
                else System.currentTimeMillis() + 3600000L
        }
        val chosen = (initial.clone() as Calendar)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(5), dp(8), dp(6))
        }
        val calendar = CalendarView(context).apply {
            firstDayOfWeek = Calendar.SUNDAY
            date = initial.timeInMillis
            minDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            setOnDateChangeListener { _, year, month, day ->
                chosen.set(Calendar.YEAR, year)
                chosen.set(Calendar.MONTH, month)
                chosen.set(Calendar.DAY_OF_MONTH, day)
            }
        }
        body.addView(calendar, LinearLayout.LayoutParams(-1, dp(270)))
        body.addView(TextView(context).apply {
            text = "Reminder time (24-hour)"
            textSize = 12f
            setTextColor(Color.BLACK)
            setPadding(dp(4), dp(5), 0, 0)
        })
        val timeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val hours = NumberPicker(context).apply {
            minValue = 0; maxValue = 23
            value = initial.get(Calendar.HOUR_OF_DAY)
            wrapSelectorWheel = true
        }
        val minutes = NumberPicker(context).apply {
            minValue = 0; maxValue = 59
            value = initial.get(Calendar.MINUTE)
            wrapSelectorWheel = true
            setFormatter { "%02d".format(Locale.US, it) }
        }
        timeRow.addView(hours, LinearLayout.LayoutParams(dp(84), dp(106)))
        timeRow.addView(TextView(context).apply {
            text = ":"
            textSize = 20f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(24), dp(106)))
        timeRow.addView(minutes, LinearLayout.LayoutParams(dp(84), dp(106)))
        body.addView(timeRow)
        Win98Dialogs.showCustom(context, "Reminder Date & Time", body,
            "Save Reminder", "Cancel", onPositive = {
                chosen.set(Calendar.HOUR_OF_DAY, hours.value)
                chosen.set(Calendar.MINUTE, minutes.value)
                chosen.set(Calendar.SECOND, 0)
                chosen.set(Calendar.MILLISECOND, 0)
                if (chosen.timeInMillis > System.currentTimeMillis()) {
                    onSave(chosen.timeInMillis)
                } else {
                    android.widget.Toast.makeText(context,
                        "Select a future date and time", android.widget.Toast.LENGTH_LONG).show()
                }
            })
    }
}
