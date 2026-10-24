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
        private var visibleNotes = listOf<Note>()

        private val adapter = object : RecyclerView.Adapter<NoteHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteHolder {
                val label = TextView(activity).apply {
                    setTextColor(android.graphics.Color.BLACK)
                    textSize = 12f
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
            shell.findViewById<TextView>(R.id.notepad_archive_button).apply {
                text = "Remind"
                setOnClickListener { reminderDialog() }
            }
            shell.findViewById<TextView>(R.id.notepad_add_image).apply {
                text = "Open Full"
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
                    (if (it.category == "long_term") "long_term" else "day_to_day") == category
            }
            val selected = visibleNotes.firstOrNull { it.id == noteId }
                ?: visibleNotes.firstOrNull {
                    it.id == prefs.getString(pageKey + category, null)
                } ?: visibleNotes.firstOrNull()
            noteId = selected?.id
            selected?.let { prefs.edit().putString(pageKey + category, it.id).apply() }
            day.setBackgroundColor(if (category == "day_to_day")
                android.graphics.Color.WHITE else 0xFFD3CEC7.toInt())
            long.setBackgroundColor(if (category == "long_term")
                android.graphics.Color.WHITE else 0xFFD3CEC7.toInt())
            adapter.notifyDataSetChanged()
            rebinding = true
            editor.setText(selected?.content.orEmpty())
            editor.isEnabled = selected != null
            editor.hint = if (selected == null) "Add a note" else ""
            rebinding = false
        }

        private fun saveText(value: String) {
            val id = noteId ?: return
            val data = getNotes() ?: return
            val note = data.firstOrNull { it.id == id && !it.isArchived } ?: return
            if (note.content == value) return
            note.content = value
            prefs.edit().putString("notepad_notes", gson.toJson(data)).apply()
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
        }

        private fun reminderDialog() {
            val id = noteId ?: return
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
                    prefs.edit().putString("notepad_notes", gson.toJson(data)).apply()
                    Win98NoteReminderReceiver.schedule(activity, id, note.title, whenAt)
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
        }
    }
}
