package rocks.gorjan.gokixp

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small Classic dialog; never launches the full-screen Android calendar/time picker. */
object Win98ReminderTimeDialog {
    fun show(context: Context, existing: Long = 0L, onSave: (Long) -> Unit) {
        val density = context.resources.displayMetrics.density
        fun dp(n: Int) = (n * density + 0.5f).toInt()
        val initial = if (existing > System.currentTimeMillis()) existing
            else System.currentTimeMillis() + 3600000L
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
        val timeFormat = SimpleDateFormat("HH:mm", Locale.US).apply { isLenient = false }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        fun field(label: String, value: String, hint: String): EditText {
            body.addView(TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(Color.BLACK)
                setPadding(0, dp(5), 0, dp(2))
            })
            return EditText(context).apply {
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_NORMAL
                setText(value)
                setSelectAllOnFocus(true)
                this.hint = hint
                setTextColor(Color.BLACK)
                textSize = 16f
                setBackgroundColor(Color.WHITE)
                setPadding(dp(8), 0, dp(8), 0)
                body.addView(this, LinearLayout.LayoutParams(-1, dp(43)))
            }
        }
        val date = field("Date (YYYY-MM-DD)", dateFormat.format(Date(initial)), "2026-10-26")
        val time = field("Time (24-hour HH:mm)", timeFormat.format(Date(initial)), "14:30")
        val error = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.rgb(150, 0, 0))
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(24)
        }
        body.addView(error)
        val handle = Win98Dialogs.showCustom(context, "Set Reminder Date & Time",
            body, "Save Reminder", "Cancel", onPositive = {
                val at = try {
                    dateTimeFormat.parse(date.text.toString().trim() + " " + time.text.toString().trim())?.time
                } catch (_: Exception) { null }
                if (at != null && at > System.currentTimeMillis()) onSave(at)
                else {
                    // A valid date and a future clock time are required.
                    android.widget.Toast.makeText(context,
                        "Enter a valid future date and time (YYYY-MM-DD, HH:mm)",
                        android.widget.Toast.LENGTH_LONG).show()
                }
            })
    }
}
