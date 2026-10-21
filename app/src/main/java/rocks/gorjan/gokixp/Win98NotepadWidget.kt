package rocks.gorjan.gokixp

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import rocks.gorjan.gokixp.apps.notepad.Note
import java.util.UUID

/**
 * A real editor for existing NotepadApp notes, pinned independently on each
 * Classic page. Uses the same JSON records as NotepadApp, preserving titles,
 * images, archive flags and unrelated notes when editing one note's text.
 * The surrounding Win98ResizableWidgetHost owns drag, resize and page geometry.
 */
object Win98NotepadWidget {
    fun create(activity: MainActivity, pageIndex: Int): View =
        PinnedNotepadView(activity, pageIndex)

    private class PinnedNotepadView(
        private val activity: MainActivity,
        private val pageIndex: Int
    ) : LinearLayout(activity) {
        private val prefs = activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        private val gson = Gson()
        private val notesType = object : TypeToken<MutableList<Note>>() {}.type
        private val pinKey = "win98_notepad_widget_note_${pageIndex}"
        private var activeNoteId: String? = null
        private var binding = false

        private val noteTitle = TextView(activity).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }

        private val editor = EditText(activity).apply {
            setTextColor(Color.BLACK)
            setTextSize(14f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            isSingleLine = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(dp(8), dp(6), dp(8), dp(6))
            hint = "Type a note..."
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(2), Color.rgb(110, 110, 110))
            }
        }

        init {
            orientation = VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.rgb(211, 206, 199))
                setStroke(dp(1), Color.rgb(105, 105, 105))
            }
            addView(noteTitle, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)))

            val actions = LinearLayout(activity).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            fun action(label: String, onClick: () -> Unit) {
                val button = TextView(activity).apply {
                    text = label
                    textSize = 11f
                    setTextColor(Color.BLACK)
                    gravity = Gravity.CENTER
                    background = AppCompatResources.getDrawable(
                        activity, R.drawable.window_button_background
                    )
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onClick() }
                }
                actions.addView(button, LayoutParams(0, dp(30), 1f).apply {
                    marginEnd = dp(3)
                })
            }
            action("Choose") { chooseNote() }
            action("New") { createNote() }
            action("Reload") { reload() }
            action("Open") { openNotepad() }
            addView(actions, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
            addView(editor, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(194)))

            editor.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!binding) saveText(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            reload()
        }

        private fun dp(value: Int): Int =
            (resources.displayMetrics.density * value + 0.5f).toInt()

        // Null on malformed JSON: never overwrite an unreadable Notepad database.
        private fun readNotes(): MutableList<Note>? = try {
            val stored = prefs.getString("notepad_notes", null)
            if (stored.isNullOrBlank()) mutableListOf()
            else gson.fromJson<MutableList<Note>>(stored, notesType) ?: mutableListOf()
        } catch (_: Exception) {
            null
        }

        private fun ensureNotes(): MutableList<Note>? {
            val notes = readNotes() ?: return null
            if (notes.isEmpty()) {
                // Match the legacy one-note migration used by the full Notepad app.
                val legacyContent = prefs.getString("notepad_content", null).orEmpty()
                notes.add(Note(UUID.randomUUID().toString(), "Note 1", legacyContent))
                prefs.edit()
                    .putString("notepad_notes", gson.toJson(notes))
                    .remove("notepad_content")
                    .apply()
            }
            return notes
        }

        private fun selectNote(id: String) {
            activeNoteId = id
            prefs.edit().putString(pinKey, id).apply()
            reload()
        }

        private fun reload() {
            val notes = ensureNotes()
            val pinned = prefs.getString(pinKey, null)
            val selected = notes?.firstOrNull { it.id == pinned && !it.isArchived }
                ?: notes?.firstOrNull { !it.isArchived }
            activeNoteId = selected?.id
            if (selected != null && pinned != selected.id) {
                prefs.edit().putString(pinKey, selected.id).apply()
            }
            binding = true
            noteTitle.text = selected?.title ?: if (notes == null) "Notes unavailable" else "No active notes"
            editor.setText(selected?.content.orEmpty())
            editor.isEnabled = selected != null
            binding = false
        }

        private fun saveText(text: String) {
            val id = activeNoteId ?: return
            // Read immediately before each write so edits never restore a stale
            // copy of unrelated notes, images, archive flags or ordering.
            val notes = readNotes() ?: return
            val note = notes.firstOrNull { it.id == id && !it.isArchived } ?: return
            if (note.content == text) return
            note.content = text
            prefs.edit().putString("notepad_notes", gson.toJson(notes)).apply()
        }

        private fun chooseNote() {
            val notes = ensureNotes()?.filter { !it.isArchived } ?: return
            if (notes.isEmpty()) {
                createNote()
                return
            }
            Win98Dialogs.showList(
                context = activity,
                title = "Pin Notepad Note",
                items = notes.map { it.title.ifBlank { "Untitled" } }.toTypedArray(),
                negativeText = "Cancel"
            ) { which ->
                notes.getOrNull(which)?.let { selectNote(it.id) }
            }
        }

        private fun createNote() {
            val notes = ensureNotes() ?: return
            val index = notes.size + 1
            val note = Note(UUID.randomUUID().toString(), "Note ${index}", "")
            notes.add(note)
            prefs.edit().putString("notepad_notes", gson.toJson(notes)).apply()
            selectNote(note.id)
            editor.requestFocus()
        }

        private fun openNotepad() {
            // Open the same record in the full Notepad app, not a separate pad.
            activeNoteId?.let {
                prefs.edit().putString("notepad_last_note_id", it).apply()
            }
            activity.launchSystemApp("system.notepad")
        }
    }
}
