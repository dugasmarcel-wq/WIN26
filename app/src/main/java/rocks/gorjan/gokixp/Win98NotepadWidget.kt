package rocks.gorjan.gokixp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import rocks.gorjan.gokixp.apps.notepad.Note
import java.util.Calendar
import java.util.UUID

/** Persistent, Classic page-resident two-tap Notepad sharing the full app database. */
object Win98NotepadWidget {
    fun create(activity: MainActivity, pageIndex: Int): View = PinnedNotepad(activity, pageIndex)

    private class PinnedNotepad(private val activity: MainActivity, page: Int) : LinearLayout(activity) {
        private val prefs = activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        private val gson = Gson()
        private val type = object : TypeToken<MutableList<Note>>() {}.type
        private val pinPrefix = "win98_notepad_widget_note_${page}_"
        private val categoryKey = "win98_notepad_widget_category_${page}"
        private val sideKey = "win98_notepad_widget_sidebar_${page}"
        private var category = prefs.getString(categoryKey, "day_to_day") ?: "day_to_day"
        private var currentId: String? = null
        private var updating = false
        private var editArmed = false
        private val sidebar = LinearLayout(activity).apply { orientation = VERTICAL }
        private val title = TextView(activity).apply { textSize = 12f; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD }
        private val editor = EditText(activity).apply {
            setTextColor(Color.BLACK)
            setTextSize(13f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(5), dp(6), dp(5), dp(6))
            setBackgroundColor(Color.WHITE)
            isFocusableInTouchMode = true
            showSoftInputOnFocus = false
            hint = "Tap twice to edit"
        }
        private val day = button("Day-to-Day") { changeCategory("day_to_day") }
        private val long = button("Long-Term") { changeCategory("long_term") }
        private val sidebarToggle = button("<") { toggleSidebar() }
        private val list = LinearLayout(activity).apply { orientation = VERTICAL }
        private val status = TextView(activity).apply { textSize = 10f; setTextColor(Color.DKGRAY); gravity = Gravity.CENTER_VERTICAL }
        private fun dp(x: Int) = (resources.displayMetrics.density * x + .5f).toInt()
        private fun button(label: String, click: () -> Unit) = TextView(activity).apply {
            text = label
            textSize = 11f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFFD3CEC7.toInt())
                setStroke(dp(1), 0xFF737373.toInt())
            }
            setOnClickListener { click() }
            isClickable = true
        }
        private fun row() = LinearLayout(activity).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        private fun LinearLayout.slot(child: View, weight: Float = 1f, height: Int = 30) {
            addView(child, LayoutParams(0, dp(height), weight).apply { marginEnd = dp(2) })
        }
        init {
            orientation = VERTICAL
            setBackgroundColor(0xFFD3CEC7.toInt())
            setPadding(dp(3), dp(3), dp(3), dp(3))
            val tabs = row().apply { slot(day); slot(long) }
            addView(tabs)
            val commands = row().apply {
                slot(sidebarToggle, .6f)
                slot(button("New") { createNote() })
                slot(button("Remind") { remind() })
                slot(button("Open") { openFull() })
            }
            addView(commands, LayoutParams(-1, dp(34)))
            val body = row().apply { gravity = Gravity.TOP }
            sidebar.setBackgroundColor(0xFFD3CEC7.toInt())
            val scroll = ScrollView(activity)
            scroll.addView(list)
            sidebar.addView(scroll, LayoutParams(-1, dp(430)))
            body.addView(sidebar, LayoutParams(dp(104), dp(430)))
            val right = LinearLayout(activity).apply { orientation = VERTICAL }
            right.addView(title, LayoutParams(-1, dp(24)))
            right.addView(editor, LayoutParams(-1, dp(400)))
            body.addView(right, LayoutParams(0, dp(430), 1f))
            addView(body)
            addView(status, LayoutParams(-1, dp(21)))
            editor.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && !editArmed) {
                    editArmed = true
                    status.text = "Tap again to type. Swipe outside Notepad to change page."
                    true
                } else if (event.actionMasked == MotionEvent.ACTION_UP && editArmed && !editor.showSoftInputOnFocus) {
                    editor.showSoftInputOnFocus = true
                    editor.requestFocus()
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
                    false
                } else false
            }
            editor.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun afterTextChanged(s: Editable?) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!updating) saveText(s?.toString().orEmpty())
                }
            })
            sidebar.visibility = if (prefs.getBoolean(sideKey, true)) VISIBLE else GONE
            sidebarToggle.text = if (sidebar.visibility == VISIBLE) "<" else ">"
            reload()
        }
        private fun notes(): MutableList<Note>? = try {
            val json = prefs.getString("notepad_notes", null)
            if (json.isNullOrEmpty()) mutableListOf()
            else gson.fromJson<MutableList<Note>>(json, type) ?: mutableListOf()
        } catch (_: Exception) { null }
        private fun all(): MutableList<Note>? {
            val data = notes() ?: return null
            if (data.isEmpty()) {
                data.add(Note(UUID.randomUUID().toString(), "Note 1",
                    prefs.getString("notepad_content", "").orEmpty()))
                prefs.edit().putString("notepad_notes", gson.toJson(data))
                    .remove("notepad_content").apply()
            }
            return data
        }
        private fun changeCategory(c: String) {
            category = c
            prefs.edit().putString(categoryKey, c).apply()
            currentId = null
            editArmed = false
            editor.showSoftInputOnFocus = false
            reload()
        }
        private fun reload() {
            val data = all() ?: return
            val filtered = data.filter { !it.isArchived && it.category == category }
            val savedId = prefs.getString(pinPrefix + category, null)
            val selected = filtered.firstOrNull { it.id == currentId }
                ?: filtered.firstOrNull { it.id == savedId } ?: filtered.firstOrNull()
            currentId = selected?.id
            if (selected != null) prefs.edit().putString(pinPrefix + category, selected.id).apply()
            day.setBackgroundColor(if (category == "day_to_day") Color.WHITE else 0xFFD3CEC7.toInt())
            long.setBackgroundColor(if (category == "long_term") Color.WHITE else 0xFFD3CEC7.toInt())
            list.removeAllViews()
            filtered.forEach { note ->
                val entry = button(note.title.ifBlank { "Untitled" }) {
                    currentId = note.id
                    editArmed = false
                    editor.showSoftInputOnFocus = false
                    reload()
                }
                entry.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                entry.setPadding(dp(5), 0, dp(2), 0)
                entry.setBackgroundColor(if (selected?.id == note.id) Color.WHITE else 0xFFD3CEC7.toInt())
                list.addView(entry, LayoutParams(-1, dp(39)))
            }
            updating = true
            title.text = selected?.title ?: "No notes in this tab"
            editor.setText(selected?.content.orEmpty())
            editor.isEnabled = selected != null
            editor.showSoftInputOnFocus = false
            updating = false
            status.text = if (selected?.reminderAt ?: 0L > System.currentTimeMillis()) "Reminder set" else "Tap twice to type"
        }
        private fun saveText(text: String) {
            val id = currentId ?: return
            val data = notes() ?: return
            val item = data.firstOrNull { it.id == id && !it.isArchived } ?: return
            if (item.content != text) {
                item.content = text
                prefs.edit().putString("notepad_notes", gson.toJson(data)).apply()
            }
        }
        private fun createNote() {
            val data = all() ?: return
            val note = Note(UUID.randomUUID().toString(), "Note ${data.size + 1}", "", category = category)
            data.add(note)
            prefs.edit().putString("notepad_notes", gson.toJson(data)).apply()
            currentId = note.id
            reload()
        }
        private fun toggleSidebar() {
            val expanded = sidebar.visibility != VISIBLE
            sidebar.visibility = if (expanded) VISIBLE else GONE
            sidebarToggle.text = if (expanded) "<" else ">"
            prefs.edit().putBoolean(sideKey, expanded).apply()
        }
        private fun remind() {
            val id = currentId ?: return
            val cal = Calendar.getInstance()
            DatePickerDialog(activity, { _, year, month, day ->
                val time = Calendar.getInstance().apply { set(year, month, day) }
                TimePickerDialog(activity, { _, hour, minute ->
                    time.set(Calendar.HOUR_OF_DAY, hour)
                    time.set(Calendar.MINUTE, minute)
                    time.set(Calendar.SECOND, 0)
                    val at = time.timeInMillis
                    if (at <= System.currentTimeMillis()) {
                        status.text = "Choose a future date and time"
                        return@TimePickerDialog
                    }
                    val data = notes() ?: return@TimePickerDialog
                    val note = data.firstOrNull { it.id == id } ?: return@TimePickerDialog
                    note.reminderAt = at
                    prefs.edit().putString("notepad_notes", gson.toJson(data)).apply()
                    Win98NoteReminderReceiver.schedule(activity, id, note.title, at)
                    status.text = "Reminder scheduled"
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        private fun openFull() {
            currentId?.let { prefs.edit().putString("notepad_last_note_id", it).apply() }
            prefs.edit().putString("notepad_active_category", category).apply()
            activity.launchSystemApp("system.notepad")
        }
    }
}
