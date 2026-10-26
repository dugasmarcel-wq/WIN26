package rocks.gorjan.gokixp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import rocks.gorjan.gokixp.apps.notepad.Note
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Reuses the actual program_notepad.xml shell (Win98 fonts, menu strip,
 * scrollbars, list, editor, bottom resize chrome) instead of drawing a
 * second unrelated imitation. The existing movable host pins this view.
 */
object Win98NotepadWidget {
    fun create(activity: MainActivity, pageIndex: Int): View =
        EmbeddedNotepad(activity, pageIndex)

    private class EmbeddedNotepad(private val activity: MainActivity, page: Int) :
        LinearLayout(activity) {
        private val prefs = activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        private val gson = Gson()
        private val type = object : TypeToken<MutableList<Note>>() {}.type
        private val pageKey = "win98_notepad_widget_note_${page}_"
        private val categoryKey = "win98_notepad_widget_category_${page}"
        private val sidebarKey = "win98_notepad_widget_sidebar_${page}"
        private var category = prefs.getString(categoryKey, "day_to_day") ?: "day_to_day"
        private var noteId: String? = null
        private var rebinding = false
        private var armed = false
        private var firstTap = false
        private var downX = 0f
        private var downY = 0f
        private val shell = activity.layoutInflater.inflate(R.layout.program_notepad, this, false)
        private val editor = shell.findViewById<EditText>(R.id.notes)
        private val noteList = shell.findViewById<RecyclerView>(R.id.notes_list)
        private val sidebar = shell.findViewById<LinearLayout>(R.id.notes_list_container)
        private val toggle = shell.findViewById<TextView>(R.id.expand_button)
        private val day = shell.findViewById<TextView>(R.id.notepad_day_tab)
        private val long = shell.findViewById<TextView>(R.id.notepad_long_tab)
        private val reminders = shell.findViewById<TextView>(R.id.notepad_reminder_tab)
        private val reminderPanel = shell.findViewById<View>(R.id.notepad_reminder_panel)
        private val subjectField = shell.findViewById<EditText>(R.id.notepad_reminder_subject)
        private val whenLabel = shell.findViewById<TextView>(R.id.notepad_reminder_when)
        private var visibleNotes = listOf<Note>()

        private val adapter = object : RecyclerView.Adapter<NoteHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteHolder {
                val label = TextView(activity).apply {
                    setTextColor(android.graphics.Color.BLACK)
                    textSize = 10.5f
                    typeface = activity.getThemePrimaryFont()
                    setPadding(dp(7), dp(6), dp(2), dp(6))
                }
                return NoteHolder(label)
            }
            override fun getItemCount() = visibleNotes.size
            override fun onBindViewHolder(holder: NoteHolder, position: Int) {
                val note = visibleNotes[position]
                holder.label.text = note.title
                holder.label.setBackgroundColor(
                    if (note.id == noteId) android.graphics.Color.WHITE
                    else android.graphics.Color.TRANSPARENT
                )
                holder.label.setOnLongClickListener {
                    renameNote(note)
                    true
                }
                holder.label.setOnClickListener {
                    noteId = note.id
                    disarm()
                    reload()
                }
            }
        }
        private class NoteHolder(val label: TextView) : RecyclerView.ViewHolder(label)
        private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()

        init {
            orientation = VERTICAL
            // Use the original layout's layout-weight editor, not a separately
            // fixed white rectangle. The host can still resize the entire widget.
            addView(shell, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(470)))
            noteList.layoutManager = LinearLayoutManager(activity)
            noteList.adapter = adapter
            day.setOnClickListener { switchTab("day_to_day") }
            long.setOnClickListener { switchTab("long_term") }
            reminders.setOnClickListener { switchTab("reminder_notes") }
            shell.findViewById<TextView>(R.id.add_note_button).setOnClickListener { addNote() }
            toggle.visibility = VISIBLE
            toggle.setOnClickListener {
                val expanded = sidebar.visibility != VISIBLE
                sidebar.visibility = if (expanded) VISIBLE else GONE
                toggle.text = if (expanded) "<" else ">"
                prefs.edit().putBoolean(sidebarKey, expanded).apply()
            }
            val expanded = prefs.getBoolean(sidebarKey, true)
            sidebar.visibility = if (expanded) VISIBLE else GONE
            toggle.text = if (expanded) "<" else ">"
            shell.findViewById<TextView>(R.id.notepad_rename_action).apply {
                text = "Rename"
                setOnClickListener {
                    val selected = getNotes()?.firstOrNull { it.id == noteId }
                    if (selected != null) renameNote(selected)
                }
            }
            // Reminders live inside their notes, never as a second toolbar action.
            shell.findViewById<TextView>(R.id.notepad_archive_button).visibility = GONE
            shell.findViewById<TextView>(R.id.notepad_reminder_set).setOnClickListener { reminderDialog() }
            shell.findViewById<TextView>(R.id.notepad_add_image).apply {
                text = "Open"
                setOnClickListener {
                    noteId?.let { prefs.edit().putString("notepad_last_note_id", it).apply() }
                    prefs.edit().putString("notepad_active_category", category).apply()
                    activity.launchSystemApp("system.notepad")
                }
            }
            // First stationary tap only arms the editor; second enables keyboard.
            // Dragging on the editor before it is armed does not open the IME.
            editor.showSoftInputOnFocus = false
            editor.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        firstTap = !armed
                        downX = event.rawX
                        downY = event.rawY
                        if (firstTap) true else false
                    }
                    MotionEvent.ACTION_UP -> {
                        if (firstTap) {
                            val movement = kotlin.math.abs(event.rawX - downX) +
                                kotlin.math.abs(event.rawY - downY)
                            if (movement < dp(12)) armed = true
                            firstTap = false
                            true
                        } else {
                            if (armed && !editor.showSoftInputOnFocus) {
                                editor.showSoftInputOnFocus = true
                                editor.requestFocus()
                                (activity.getSystemService(Context.INPUT_METHOD_SERVICE)
                                    as InputMethodManager).showSoftInput(
                                    editor, InputMethodManager.SHOW_IMPLICIT
                                )
                            }
                            false
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> { firstTap = false; false }
                    else -> firstTap
                }
            }
            subjectField.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun afterTextChanged(s: Editable?) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!rebinding && category == "reminder_notes") {
                        val data = getNotes() ?: return
                        data.firstOrNull { it.id == noteId }?.let {
                            it.reminderSubject = s?.toString().orEmpty()
                            prefs.edit().putString("notepad_notes", gson.toJson(data)).apply()
                        }
                    }
                }
            })
            editor.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun afterTextChanged(s: Editable?) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!rebinding) saveText(s?.toString().orEmpty())
                }
            })
            reload()
        }

        private fun disarm() {
            armed = false
            firstTap = false
            editor.showSoftInputOnFocus = false
            editor.clearFocus()
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(editor.windowToken, 0)
        }

        private fun getNotes(): MutableList<Note>? = try {
            val data = prefs.getString("notepad_notes", null)
            if (data.isNullOrBlank()) mutableListOf()
            else gson.fromJson<MutableList<Note>>(data, type) ?: mutableListOf()
        } catch (_: Exception) { null }

        private fun allNotes(): MutableList<Note>? {
            val notes = getNotes() ?: return null
            if (notes.isEmpty()) {
                notes.add(Note(UUID.randomUUID().toString(), "Note 1",
                    prefs.getString("notepad_content", "").orEmpty()))
                prefs.edit().putString("notepad_notes", gson.toJson(notes))
                    .remove("notepad_content").apply()
            }
            return notes
        }

        private fun reload() {
            val notes = allNotes() ?: return
            visibleNotes = notes.filter {
                !it.isArchived &&
                    (if (it.category == "long_term") "long_term" else if (it.category == "reminder_notes") "reminder_notes" else "day_to_day") == category
            }
            val todayId = if (category == "day_to_day") prefs.getString("win98_notepad_daily_created_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()), null) else null
            val selected = visibleNotes.firstOrNull { it.id == todayId }
                ?: visibleNotes.firstOrNull { it.id == noteId }
                ?: visibleNotes.firstOrNull {
                    it.id == prefs.getString(pageKey + category, null)
                } ?: visibleNotes.firstOrNull()
            noteId = selected?.id
            selected?.let { prefs.edit().putString(pageKey + category, it.id).apply() }
            day.setBackgroundColor(if (category == "day_to_day")
                android.graphics.Color.WHITE else 0xFFD3CEC7.toInt())
            long.setBackgroundColor(if (category == "long_term")
                android.graphics.Color.WHITE else 0xFFD3CEC7.toInt())
            reminders.setBackgroundColor(if (category == "reminder_notes")
                android.graphics.Color.WHITE else 0xFFD3CEC7.toInt())
            adapter.notifyDataSetChanged()
            rebinding = true
            reminderPanel.visibility = if (category == "reminder_notes") VISIBLE else GONE
            subjectField.setText(selected?.reminderSubject.orEmpty())
            subjectField.isEnabled = selected != null
            whenLabel.text = if ((selected?.reminderAt ?: 0L) > 0L)
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(selected!!.reminderAt))
                else "No date or time set"
            editor.setText(selected?.content.orEmpty())
            editor.isEnabled = selected != null
            editor.hint = if (selected == null) "Tap Add to create a note" else ""
            rebinding = false
        }

        private fun saveText(value: String) {
            if (category == "day_to_day" && value.isNotBlank()) {
                val today = SimpleDateFormat("yyyy MMM d", Locale.ENGLISH).format(Date())
                val key = "win98_notepad_daily_created_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                val notesToday = getNotes() ?: return
                val existing = prefs.getString(key, null)
                if (existing == null) {
                    val current = notesToday.firstOrNull { it.id == noteId }
                    if (current != null && current.content.isBlank()) {
                        current.title = today
                        prefs.edit().putString(key, current.id).apply()
                        prefs.edit().putString("notepad_notes", gson.toJson(notesToday)).apply()
                        adapter.notifyDataSetChanged()
                    } else {
                        val old = current?.content.orEmpty()
                        val prefix = old.commonPrefixWith(value).length
                        val restOld = old.substring(prefix)
                        val restNew = value.substring(prefix)
                        val suffix = restOld.reversed().commonPrefixWith(restNew.reversed()).length
                        val insertion = restNew.dropLast(suffix)
                        val fresh = Note(UUID.randomUUID().toString(), today,
                            insertion.ifBlank { value }, category = "day_to_day")
                        notesToday.add(fresh)
                        prefs.edit().putString(key, fresh.id).putString("notepad_notes", gson.toJson(notesToday)).apply()
                        noteId = fresh.id
                        reload()
                        return
                    }
                }
            }
            val id = noteId ?: return
            val data = getNotes() ?: return
            val note = data.firstOrNull { it.id == id && !it.isArchived } ?: return
            if (note.content == value) return
            note.content = value
            prefs.edit().putString("notepad_notes", gson.toJson(data)).apply()
        }

        private fun renameNote(note: Note) {
            val input = EditText(activity).apply { setSingleLine(true); setText(note.title); selectAll() }
            Win98Dialogs.showCustom(activity, "Rename Note", input, "Save", onPositive = {
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val notes = getNotes() ?: return@showCustom
                    notes.firstOrNull { it.id == note.id }?.title = name
                    prefs.edit().putString("notepad_notes", gson.toJson(notes)).apply()
                    reload()
                }
            })
        }

        private fun switchTab(value: String) {
            if (value == category) return
            disarm()
            category = value
            noteId = null
            prefs.edit().putString(categoryKey, value).apply()
            reload()
        }

        private fun addNote() {
            val data = allNotes() ?: return
            val note = Note(UUID.randomUUID().toString(), "Note ${data.size + 1}",
                "", category = category)
            data.add(note)
            prefs.edit().putString("notepad_notes", gson.toJson(data)).apply()
            noteId = note.id
            disarm()
            reload()
            if (category == "reminder_notes") subjectField.requestFocus()
        }

        private fun reminderDialog() {
            if (category != "reminder_notes") return
            val id = noteId ?: return
            val subject = subjectField.text.toString().trim()
            if (subject.isEmpty()) {
                subjectField.error = "Enter a notification subject"
                subjectField.requestFocus()
                return
            }
            chooseReminderDate(id, subject)
        }

        private fun chooseReminderDate(id: String, subject: String) {
            val now = Calendar.getInstance()
            DatePickerDialog(activity, { _, year, month, day ->
                TimePickerDialog(activity, { _, hour, minute ->
                    val whenAt = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (whenAt <= System.currentTimeMillis()) return@TimePickerDialog
                    val data = getNotes() ?: return@TimePickerDialog
                    val note = data.firstOrNull { it.id == id } ?: return@TimePickerDialog
                    note.reminderAt = whenAt
                    note.reminderSubject = subject
                    prefs.edit().putString("notepad_notes", gson.toJson(data)).apply()
                    Win98NoteReminderReceiver.schedule(activity, id, subject, whenAt)
                    reload()
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
        }
    }
}
