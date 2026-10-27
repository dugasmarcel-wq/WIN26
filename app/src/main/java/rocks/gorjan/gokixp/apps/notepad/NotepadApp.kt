package rocks.gorjan.gokixp.apps.notepad

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Calendar
import java.util.Date
import java.text.DateFormat
import rocks.gorjan.gokixp.Win98NoteReminderReceiver
import rocks.gorjan.gokixp.Win98ReminderTimeDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.text.Editable
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import rocks.gorjan.gokixp.ContextMenuItem
import rocks.gorjan.gokixp.Helpers
import rocks.gorjan.gokixp.MainActivity
import rocks.gorjan.gokixp.R
import rocks.gorjan.gokixp.theme.AppTheme
import rocks.gorjan.gokixp.theme.ThemeManager

/**
 * Notepad app logic and UI controller
 * This structure can be used as a template for other system apps
 */
class NotepadApp(
    private val context: Context,
    private val onSoundPlay: (String) -> Unit,
    private val onShowContextMenu: (List<ContextMenuItem>, Float, Float) -> Unit,
    private val onShowRenameDialog: (String, String, String, (String) -> Unit) -> Unit,
    private val onUpdateWindowTitle: (String) -> Unit,
    private val galleryPickerLauncher: ActivityResultLauncher<String>,
    private val onCameraCapture: (Uri) -> Unit,
    private val onShowFullscreenImage: (Uri) -> Unit,
    private val getCursorPosition: () -> Pair<Float, Float>,
    private val embeddedPage: Int? = null,
    private val onActivate: (NotepadApp) -> Unit = {}
) {
    companion object {
        private const val KEY_NOTES = "notepad_notes"
        private const val KEY_OLD_NOTE = "notepad_content"
        private const val KEY_LAST_NOTE_ID = "notepad_last_note_id"
    }

    private val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
    private val store = NotepadStore(prefs)
    private val notes = mutableListOf<Note>()
    private var savedSnapshot = emptyList<Note>()
    private var lastStoredJson: String? = null
    private var saving = false
    private var listening = false
    private var databaseReady = false
    private var currentNote: Note? = null
    private var isListExpanded = true
    private var isProgrammaticChange = false
    private var noteInMoveMode: Note? = null
    private var showingArchivedNotes = false
    private var activeCategory = "day_to_day"
    private var classicUi = false
    private var categoryPopup: android.widget.PopupWindow? = null
    private var editorArmed = false
    private val categoryKey = embeddedPage?.let { "win98_notepad_widget_category_$it" }
        ?: "notepad_active_category"
    private val sidebarKey = embeddedPage?.let { "win98_notepad_widget_sidebar_$it" }
        ?: "notepad_sidebar_expanded"
    private val storageListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_NOTES && !saving && prefs.getString(KEY_NOTES, null) != lastStoredJson) {
            reloadExternalNotes()
        }
    }
    private var dayTab: TextView? = null
    private var longTab: TextView? = null
    private var reminderTab: TextView? = null
    private var reminderPanel: View? = null
    private var reminderSubject: EditText? = null
    private var reminderWhen: TextView? = null
    private var notesEditText: EditText? = null
    private var notesListContainer: LinearLayout? = null
    private var notesList: RecyclerView? = null
    private var expandButton: TextView? = null
    private var addNoteButton: TextView? = null
    private var archiveButton: TextView? = null
    private var notesAdapter: NotesAdapter? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private var imageGallery: RecyclerView? = null
    private var imageGalleryAdapter: ImageGalleryAdapter? = null

    fun setupApp(contentView: View): View {
        classicUi = contentView.tag == "classic_notepad"
        notesEditText = contentView.findViewById(R.id.notes)
        notesListContainer = contentView.findViewById(R.id.notes_list_container)
        notesList = contentView.findViewById(R.id.notes_list)
        expandButton = contentView.findViewById(R.id.expand_button)
        addNoteButton = contentView.findViewById(R.id.add_note_button)
        archiveButton = contentView.findViewById(R.id.notepad_archive_button)
        imageGallery = contentView.findViewById(R.id.image_gallery)
        dayTab = contentView.findViewById(R.id.notepad_day_tab)
        longTab = contentView.findViewById(R.id.notepad_long_tab)
        reminderTab = contentView.findViewById(R.id.notepad_reminder_tab)
        reminderPanel = contentView.findViewById(R.id.notepad_reminder_panel)
        reminderSubject = contentView.findViewById(R.id.notepad_reminder_subject)
        reminderWhen = contentView.findViewById(R.id.notepad_reminder_when)
        activeCategory = prefs.getString(categoryKey, "day_to_day") ?: "day_to_day"
        isListExpanded = prefs.getBoolean(sidebarKey, true)
        applySidebarVisibility()

        notesAdapter = NotesAdapter()
        notesList?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = notesAdapter
        }
        imageGallery?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        val drag = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(view: RecyclerView, holder: RecyclerView.ViewHolder): Int =
                makeMovementFlags(if (noteInMoveMode != null) ItemTouchHelper.UP or ItemTouchHelper.DOWN else 0, 0)
            override fun onMove(view: RecyclerView, from: RecyclerView.ViewHolder,
                to: RecyclerView.ViewHolder): Boolean {
                if (from.adapterPosition < 0 || to.adapterPosition < 0) return false
                notesAdapter?.onItemMove(from.adapterPosition, to.adapterPosition)
                return true
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled() = false
            override fun clearView(view: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(view, holder)
                notesAdapter?.onItemDropped()
                noteInMoveMode = null
                refreshNotesList()
            }
        }
        itemTouchHelper = ItemTouchHelper(drag).also { it.attachToRecyclerView(notesList) }

        listOf(dayTab to "day_to_day", longTab to "long_term", reminderTab to "reminder_notes")
            .forEach { (tab, category) ->
                tab?.setOnClickListener {
                    switchCategory(category)
                    if (classicUi) showCategoryMenu(tab)
                }
                tab?.contentDescription = when (category) {
                    "long_term" -> "Long-Term notes menu"
                    "reminder_notes" -> "Reminder notes menu"
                    else -> "Day-to-Day notes menu"
                }
            }
        addNoteButton?.setOnClickListener { addNote() }
        expandButton?.setOnClickListener { toggleSidebar() }
        archiveButton?.setOnClickListener { toggleArchiveView() }
        contentView.findViewById<TextView>(R.id.notepad_rename_action)?.setOnClickListener { renameCurrentNote() }
        contentView.findViewById<TextView>(R.id.notepad_add_image)?.setOnClickListener { showImageSourcePicker(it) }
        contentView.findViewById<TextView>(R.id.notepad_reminder_set)?.setOnClickListener { scheduleCurrentReminder() }
        reminderSubject?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isProgrammaticChange && activeCategory == "reminder_notes") {
                    currentNote?.reminderSubject = s?.toString().orEmpty()
                    saveNotes()
                }
            }
        })
        notesEditText?.addTextChangedListener(object : android.text.TextWatcher {
            private var autoInserting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isProgrammaticChange || autoInserting || s == null || count != 1 || s[start] != '\n') return
                val previousStart = s.toString().lastIndexOf('\n', start - 1) + 1
                if (s.subSequence(previousStart, start).startsWith("- ")) {
                    autoInserting = true
                    notesEditText?.text?.insert(start + 1, "- ")
                    autoInserting = false
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (isProgrammaticChange || autoInserting) return
                // Editing always belongs to the selected ID. Only Add creates notes.
                currentNote?.content = s?.toString().orEmpty()
                saveNotes()
            }
        })
        if (embeddedPage != null) setupEmbeddedEditing()
        resume()
        return contentView
    }

    fun resume() {
        if (!listening) {
            prefs.registerOnSharedPreferenceChangeListener(storageListener)
            listening = true
        }
        reloadExternalNotes()
    }

    private fun reloadExternalNotes() {
        try {
            saving = true
            applyLoadedNotes(store.load())
            savedSnapshot = NotepadJson.snapshot(notes)
            lastStoredJson = prefs.getString(KEY_NOTES, null)
            databaseReady = true
            val selectedId = currentNote?.id ?: prefs.getString(selectionKey(), null)
            if (embeddedPage == null && currentNote == null) {
                showingArchivedNotes = notes.firstOrNull { it.id == selectedId }?.isArchived == true
            }
            val available = getFilteredNotes()
            selectNote(available.firstOrNull { it.id == selectedId } ?: available.firstOrNull())
            addNoteButton?.isEnabled = true
        } catch (e: Exception) {
            databaseReady = false
            notesEditText?.isEnabled = false
            addNoteButton?.isEnabled = false
            android.widget.Toast.makeText(context, "Notes could not be read. Stored data has been kept.",
                android.widget.Toast.LENGTH_LONG).show()
            Log.e("NotepadApp", "Cannot read notes", e)
        } finally { saving = false }
    }

    private fun applyLoadedNotes(loaded: List<Note>) {
        val live = notes.associateBy { it.id }
        val selected = currentNote?.id
        val merged = loaded.map { stored ->
            live[stored.id]?.apply {
                title = stored.title; content = stored.content
                imageUris = stored.imageUris.toMutableList(); isArchived = stored.isArchived
                category = stored.category; reminderAt = stored.reminderAt
                reminderSubject = stored.reminderSubject
            } ?: stored
        }
        notes.clear(); notes.addAll(merged)
        currentNote = notes.firstOrNull { it.id == selected }
    }

    private fun selectionKey(): String = embeddedPage?.let {
        "win98_notepad_widget_note_${it}_$activeCategory"
    } ?: KEY_LAST_NOTE_ID

    private fun selectNote(note: Note?) {
        currentNote = note
        saveLastNoteId()
        isProgrammaticChange = true
        val value = note?.content.orEmpty()
        if (notesEditText?.text?.toString() != value) notesEditText?.setText(value)
        notesEditText?.isEnabled = note != null && databaseReady
        notesEditText?.hint = if (note == null) "Use Add to create a note" else ""
        isProgrammaticChange = false
        note?.let { loadNoteImages(it) } ?: run { imageGallery?.visibility = View.GONE }
        updateWindowTitle()
        refreshNotesList()
        updateCategoryTabs()
        updateReminderFields()
    }

    private fun switchCategory(category: String) {
        if (activeCategory == category) return
        disarmEmbeddedEditor()
        activeCategory = category
        showingArchivedNotes = false
        prefs.edit().putString(categoryKey, category).apply()
        val available = getFilteredNotes()
        val id = prefs.getString(selectionKey(), null)
        selectNote(available.firstOrNull { it.id == id } ?: available.firstOrNull())
    }

    private fun addNote() {
        if (!databaseReady) return
        val note = NotepadJson.newNote(activeCategory, showingArchivedNotes)
        notes.add(note)
        saveNotes()
        disarmEmbeddedEditor()
        selectNote(notes.firstOrNull { it.id == note.id })
    }

    private fun renameCurrentNote() {
        val id = currentNote?.id ?: return
        onShowRenameDialog("Rename Note", currentNote?.title.orEmpty(), "Note name") { title ->
            if (title.isNotBlank()) {
                notes.firstOrNull { it.id == id }?.title = title.trim()
                saveNotes(); updateWindowTitle(); refreshNotesList()
            }
        }
    }

    private fun applySidebarVisibility() {
        notesListContainer?.visibility = if (isListExpanded) View.VISIBLE else View.GONE
        expandButton?.visibility = View.VISIBLE
        expandButton?.text = if (isListExpanded) "<" else ">"
    }

    private fun toggleSidebar() {
        isListExpanded = !isListExpanded
        prefs.edit().putBoolean(sidebarKey, isListExpanded).apply()
        applySidebarVisibility()
    }

    private fun toggleArchiveView() {
        showingArchivedNotes = !showingArchivedNotes
        archiveButton?.text = if (showingArchivedNotes) "Active Notes" else "Archived Notes"
        disarmEmbeddedEditor()
        selectNote(getFilteredNotes().firstOrNull())
    }

    private fun showCategoryMenu(anchor: View) {
        categoryPopup?.dismiss()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(3.dpToPx(), 3.dpToPx(), 3.dpToPx(), 3.dpToPx())
            setBackgroundResource(R.drawable.win98_start_menu_border)
        }
        val popup = android.widget.PopupWindow(column, 194.dpToPx(), ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        popup.isOutsideTouchable = true
        fun item(label: String, enabled: Boolean = true, action: () -> Unit) {
            column.addView(TextView(context).apply {
                text = label; textSize = 12f; setTextColor(if (enabled) Color.BLACK else Color.DKGRAY)
                typeface = MainActivity.getInstance()?.getThemePrimaryFont()
                minHeight = 36.dpToPx()
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(10.dpToPx(), 5.dpToPx(), 10.dpToPx(), 5.dpToPx())
                isEnabled = enabled
                setOnClickListener { popup.dismiss(); action() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        item("Add note", databaseReady) { addNote() }
        item("Rename note", currentNote != null) { renameCurrentNote() }
        item(if (isListExpanded) "Hide notes sidebar" else "Show notes sidebar") { toggleSidebar() }
        item(if (showingArchivedNotes) "Show active notes" else "Archived notes") { toggleArchiveView() }
        item("Add image", currentNote != null) { showImageSourcePicker(anchor) }
        if (activeCategory == "reminder_notes" && (currentNote?.reminderAt ?: 0L) > 0L) {
            item("Cancel reminder") {
                currentNote?.let { it.reminderAt = 0L; Win98NoteReminderReceiver.cancel(context, it.id) }
                saveNotes(); updateReminderFields()
            }
        }
        categoryPopup = popup
        popup.showAsDropDown(anchor)
    }

    private fun setupEmbeddedEditing() {
        val editor = notesEditText ?: return
        editor.showSoftInputOnFocus = false
        var firstTap = false
        var downX = 0f
        var downY = 0f
        editor.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    firstTap = !editorArmed
                    downX = event.rawX; downY = event.rawY
                    firstTap
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (firstTap) {
                        if (kotlin.math.abs(event.rawX - downX) + kotlin.math.abs(event.rawY - downY) < 12.dpToPx()) {
                            editorArmed = true
                        }
                        firstTap = false
                        true
                    } else {
                        if (editorArmed) {
                            editor.showSoftInputOnFocus = true
                            editor.requestFocus()
                            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                                .showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                        }
                        false
                    }
                }
                android.view.MotionEvent.ACTION_CANCEL -> { firstTap = false; false }
                else -> firstTap
            }
        }
    }

    private fun disarmEmbeddedEditor() {
        if (embeddedPage == null) return
        editorArmed = false
        notesEditText?.showSoftInputOnFocus = false
        notesEditText?.clearFocus()
    }

    fun openNote(id: String?, category: String?) {
        if (category != null) activeCategory = category
        showingArchivedNotes = false
        prefs.edit().putString(categoryKey, activeCategory).apply()
        reloadExternalNotes()
        val target = notes.firstOrNull { it.id == id }
        showingArchivedNotes = target?.isArchived == true
        selectNote(target ?: getFilteredNotes().firstOrNull())
    }

    fun openFullNotepad() {
        onMinimize()
        (context as? MainActivity)?.openNotepad(currentNote?.id, activeCategory)
    }

    fun onMinimize() {
        saveNotes()
        saveLastNoteId()
    }

    /**
     * Update window title with current note name
     */
    private fun updateWindowTitle() {
        currentNote?.let {
            onUpdateWindowTitle("${it.title} - Notepad")
        } ?: onUpdateWindowTitle("Notepad")
    }

    /**
     * Get filtered notes based on current view (active or archived)
     */
    private fun getFilteredNotes(): List<Note> {
        return notes.filter { it.isArchived == showingArchivedNotes && (if (it.category == "long_term") "long_term" else if (it.category == "reminder_notes") "reminder_notes" else "day_to_day") == activeCategory }
    }

    private fun updateReminderFields() {
        reminderPanel?.visibility = if (activeCategory == "reminder_notes") View.VISIBLE else View.GONE
        notesEditText?.isEnabled = databaseReady && currentNote != null
        isProgrammaticChange = true
        reminderSubject?.setText(currentNote?.reminderSubject.orEmpty())
        reminderSubject?.isEnabled = currentNote != null
        isProgrammaticChange = false
        reminderWhen?.text = currentNote?.reminderAt?.takeIf { it > 0L }?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
        } ?: "No date or time set"
    }

    private fun scheduleCurrentReminder() {
        if (activeCategory != "reminder_notes") return
        val note = currentNote ?: return
        val subject = reminderSubject?.text?.toString()?.trim().orEmpty()
        if (subject.isEmpty()) { reminderSubject?.error = "Enter a subject"; return }
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(notesEditText?.windowToken, 0)
        notesEditText?.clearFocus()
        Win98ReminderTimeDialog.show(context, note.reminderAt) { at ->
            note.reminderAt = at
            note.reminderSubject = subject
            saveNotes()
            if (databaseReady) {
                Win98NoteReminderReceiver.schedule(context, note.id, subject, at)
                if (android.os.Build.VERSION.SDK_INT >= 33 && context is Activity &&
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    context.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2601)
                }
            }
            updateReminderFields()
        }
    }

    private fun updateCategoryTabs() {
        dayTab?.setBackgroundColor(if (activeCategory == "day_to_day") Color.WHITE else 0xFFD3CEC7.toInt())
        longTab?.setBackgroundColor(if (activeCategory == "long_term") Color.WHITE else 0xFFD3CEC7.toInt())
        reminderTab?.setBackgroundColor(if (activeCategory == "reminder_notes") Color.WHITE else 0xFFD3CEC7.toInt())
    }

    /**
     * Refresh the notes list UI
     */
    private fun refreshNotesList() {
        notesAdapter?.notifyDataSetChanged()
    }

    /**
     * Load images for a note and update the gallery
     */
    private fun loadNoteImages(note: Note) {
        // Ensure imageUris is initialized (for backward compatibility with old notes)

        if (note.imageUris.isEmpty()) {
            // No images - hide gallery
            imageGallery?.visibility = View.GONE
        } else {
            // Has images - show gallery and load them
            imageGallery?.visibility = View.VISIBLE
            imageGalleryAdapter = ImageGalleryAdapter(note.imageUris)
            imageGallery?.adapter = imageGalleryAdapter
        }
    }

    /**
     * Show image source picker dialog (Gallery or Camera)
     */
    private fun showImageSourcePicker(anchorView: View) {
        onActivate(this)
        val menuItems = listOf(
            ContextMenuItem(
                title = "From Gallery",
                isEnabled = true,
                action = {
                    onSoundPlay("click")
                    // Launch gallery picker
                    galleryPickerLauncher.launch("image/*")
                }
            ),
            ContextMenuItem(
                title = "From Camera",
                isEnabled = true,
                action = {
                    onSoundPlay("click")
                    // Create temporary URI for camera
                    val tempUri = createTempImageUri()
                    if (tempUri != null) {
                        onCameraCapture(tempUri)
                    }
                }
            )
        )

        // Show context menu right below the Add Image button
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val x = location[0].toFloat()
        val y = location[1].toFloat() + anchorView.height
        onShowContextMenu(menuItems, x, y)
    }

    /**
     * Create a temporary URI for camera capture
     */
    private fun createTempImageUri(): Uri? {
        return try {
            val fileName = "notepad_camera_${System.currentTimeMillis()}.jpg"
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } catch (e: Exception) {
            Log.e("NotepadApp", "Error creating temp URI", e)
            null
        }
    }

    /**
     * Handle image selected from gallery or camera
     */
    fun onImageSelected(uri: Uri?) {
        uri?.let { selectedUri ->
            try {
                // Take persistent permission for this URI
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(selectedUri, takeFlags)

                // Add to current note
                currentNote?.let { note ->
                    // Ensure imageUris is initialized (for backward compatibility)
                    note.imageUris.add(selectedUri.toString())
                    saveNotes()
                    loadNoteImages(note)
                }
            } catch (e: SecurityException) {
                Log.e("NotepadApp", "Could not take persistent permission", e)
                // Still add the image even if we can't take persistent permission
                currentNote?.let { note ->
                    // Ensure imageUris is initialized (for backward compatibility)
                    note.imageUris.add(selectedUri.toString())
                    saveNotes()
                    loadNoteImages(note)
                }
            } catch (e: Exception) {
                Log.e("NotepadApp", "Error adding image", e)
            }
        }
    }

    /**
     * Show context menu for an image
     */
    private fun showImageContextMenu(uriString: String) {
        val menuItems = listOf(
            ContextMenuItem(
                title = "Delete",
                isEnabled = true,
                action = {
                    onSoundPlay("click")
                    // Remove image from current note
                    currentNote?.let { note ->
                        note.imageUris?.remove(uriString)
                        saveNotes()
                        loadNoteImages(note)
                    }
                }
            )
        )

        val (x, y) = getCursorPosition()
        onShowContextMenu(menuItems, x, y)
    }

    /**
     * Show context menu for a note
     */
    private fun showNoteContextMenu(note: Note, x: Float, y: Float) {
        // Create context menu items
        val menuItems = mutableListOf<ContextMenuItem>()

        // Rename option
        menuItems.add(ContextMenuItem(
            title = "Rename",
            isEnabled = true,
            action = {
                onShowRenameDialog("Rename Note", note.title, "Note name") { newTitle ->
                    if (newTitle.isNotBlank()) {
                        note.title = newTitle
                        saveNotes()
                        updateWindowTitle()
                        refreshNotesList()
                    }
                }
            }
        ))

        // Move option (only if more than one note exists in current view)
        val filteredNotes = getFilteredNotes()
        if (filteredNotes.size > 1) {
            menuItems.add(ContextMenuItem(
                title = "Move",
                isEnabled = true,
                action = {
                    // Enter move mode for this note
                    noteInMoveMode = note
                    refreshNotesList()

                    // Start dragging for this note
                    val noteIndex = filteredNotes.indexOfFirst { it.id == note.id }
                    notesList?.post {
                        val viewHolder = notesList?.findViewHolderForAdapterPosition(noteIndex)
                        viewHolder?.let {
                            itemTouchHelper?.startDrag(it)
                        }
                    }

                    onSoundPlay("click")
                }
            ))
        }

        // Archive/Unarchive option
        menuItems.add(ContextMenuItem(
            title = if (note.isArchived) "Unarchive" else "Archive",
            isEnabled = true,
            action = {
                onSoundPlay("click")

                val wasSelected = note.id == currentNote?.id
                // Toggle archive status
                note.isArchived = !note.isArchived
                saveNotes()

                // If we archived/unarchived the current note, switch to first available note in current view
                if (wasSelected) {
                    val remainingNotes = getFilteredNotes()
                    currentNote = remainingNotes.firstOrNull()
                    saveLastNoteId()

                    currentNote?.let {
                        isProgrammaticChange = true
                        notesEditText?.setText(it.content)
                        isProgrammaticChange = false
                        loadNoteImages(it)
                    } ?: run {
                        // No notes left in this view
                        isProgrammaticChange = true
                        notesEditText?.setText("")
                        isProgrammaticChange = false
                        imageGallery?.visibility = View.GONE
                    }

                    updateWindowTitle()
                }

                Win98NoteReminderReceiver.cancel(context, note.id)
                if (!note.isArchived && note.reminderAt > 0L) {
                    Win98NoteReminderReceiver.schedule(context, note.id, note.reminderSubject, note.reminderAt)
                }
                selectNote(currentNote)
            }
        ))

        // Delete option (only if more than one note exists)
        if (notes.size > 1) {
            menuItems.add(ContextMenuItem(
                title = "Delete",
                isEnabled = true,
                action = {
                    // Find index of note to delete
                    val index = notes.indexOfFirst { it.id == note.id }
                    if (index != -1) {
                        notes.removeAt(index)
                        Win98NoteReminderReceiver.cancel(context, note.id)
                        saveNotes()

                        // Switch to first remaining note
                        val newCurrentNote = getFilteredNotes().firstOrNull()
                        currentNote = newCurrentNote
                        saveLastNoteId()
                        newCurrentNote?.let {
                            isProgrammaticChange = true
                            notesEditText?.setText(it.content)
                            isProgrammaticChange = false
                        }

                        selectNote(newCurrentNote)
                    }
                }
            ))
        }

        // Show context menu
        onShowContextMenu(menuItems, x, y)
    }

    /**
     * Load notes from SharedPreferences
     */
    private fun saveNotes() {
        if (!databaseReady || saving) return
        try {
            saving = true
            applyLoadedNotes(store.save(savedSnapshot, notes))
            savedSnapshot = NotepadJson.snapshot(notes)
            lastStoredJson = prefs.getString(KEY_NOTES, null)
        } catch (e: Exception) {
            databaseReady = false
            notesEditText?.isEnabled = false
            android.widget.Toast.makeText(context, "Notes could not be saved. Stored data has been kept.",
                android.widget.Toast.LENGTH_LONG).show()
            Log.e("NotepadApp", "Cannot save notes", e)
        } finally { saving = false }
    }

    /**
     * Save the current note ID to SharedPreferences
     */
    private fun saveLastNoteId() {
        currentNote?.let { note ->
            prefs.edit {
                putString(selectionKey(), note.id)
            }
        }
    }

    /**
     * Cleanup when app is closed
     */
    fun cleanup() {
        categoryPopup?.dismiss()
        saveNotes()
        saveLastNoteId()
        prefs.unregisterOnSharedPreferenceChangeListener(storageListener)
        listening = false
    }

    /**
     * Extension function to convert dp to pixels
     */
    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    /**
     * RecyclerView Adapter for notes list
     */
    inner class NotesAdapter : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

        inner class NoteViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
            val textView = TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setTextColor(Color.BLACK)
                setPadding(8.dpToPx(), 6.dpToPx(), 8.dpToPx(), 6.dpToPx())

                // Use theme's primary font
                typeface = MainActivity.getInstance()?.getThemePrimaryFont()
                val currentTheme = ThemeManager(context).getSelectedTheme()
                textSize = if (currentTheme == AppTheme.WindowsClassic) 10.5f else 11f
                minHeight = 32.dpToPx()
            }
            return NoteViewHolder(textView)
        }

        override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
            val filteredNotes = getFilteredNotes()
            val note = filteredNotes[position]
            val textView = holder.textView

            // Set text and appearance based on state
            if (note.id == noteInMoveMode?.id) {
                // Note in move mode - highlight with yellow/gold
                textView.setBackgroundColor(0xFFFFFF99.toInt())
                textView.text = "↕ ${note.title}"
            } else if (note.id == currentNote?.id) {
                // Current note - white background
                textView.setBackgroundColor(0xFFFFFFFF.toInt())
                textView.text = note.title
            } else {
                // Normal note
                textView.setBackgroundColor(Color.TRANSPARENT)
                textView.text = note.title
            }

            // Click listener (only if not in move mode)
            if (note.id != noteInMoveMode?.id) {
                textView.setOnClickListener {
                    disarmEmbeddedEditor()
                    selectNote(notes.firstOrNull { it.id == note.id })
                }

                // Long press for context menu
                textView.setOnLongClickListener { view ->
                    val location = IntArray(2)
                    view.getLocationOnScreen(location)
                    showNoteContextMenu(note, location[0].toFloat(), location[1].toFloat())
                    true
                }
            } else {
                // In move mode - clear listeners
                textView.setOnClickListener(null)
                textView.setOnLongClickListener(null)
            }
        }

        override fun getItemCount(): Int = getFilteredNotes().size

        fun onItemMove(fromPosition: Int, toPosition: Int) {
            val filteredNotes = getFilteredNotes().toMutableList()

            // Move within filtered list
            val movedNote = filteredNotes[fromPosition]
            filteredNotes.removeAt(fromPosition)
            filteredNotes.add(toPosition, movedNote)

            // Update positions in main notes list
            // Remove all filtered notes from main list, then re-add in new order
            val visibleIds = filteredNotes.map { it.id }.toSet()
            val otherNotes = notes.filter { it.id !in visibleIds }
            notes.clear()
            notes.addAll(otherNotes)
            notes.addAll(filteredNotes)

            notifyItemMoved(fromPosition, toPosition)
        }

        fun onItemDropped() {
            // Save the new order when dragging is complete
            saveNotes()
        }
    }

    /**
     * Image Gallery Adapter for displaying images in horizontal scroll
     */
    inner class ImageGalleryAdapter(
        private val imageUris: MutableList<String>
    ) : RecyclerView.Adapter<ImageGalleryAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(val imageView: android.widget.ImageView) : RecyclerView.ViewHolder(imageView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val imageView = android.widget.ImageView(context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(72.dpToPx(), 72.dpToPx()).apply {
                    marginEnd = 4.dpToPx()
                }
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.LTGRAY)
            }
            return ImageViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val uriString = imageUris[position]
            val imageView = holder.imageView
            var isValidImage = false

            try {
                val uri = android.net.Uri.parse(uriString)

                // Try to load the image with proper orientation
                val bitmap = loadBitmapWithOrientation(uri)

                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    imageView.setBackgroundColor(Color.TRANSPARENT)
                    isValidImage = true
                } else {
                    // Image couldn't be decoded - show placeholder
                    showPlaceholder(imageView)
                }

                // Set up click listener to view full-screen (only for valid images)
                if (isValidImage) {
                    imageView.setOnClickListener {
                        onSoundPlay("click")
                        onShowFullscreenImage(uri)
                    }
                } else {
                    imageView.setOnClickListener(null)
                }

            } catch (e: Exception) {
                // Error loading image - show placeholder
                Log.e("NotepadApp", "Error loading image: $uriString", e)
                showPlaceholder(imageView)
                imageView.setOnClickListener(null)
            }

            // Set up long press listener to show delete menu (always available)
            imageView.setOnLongClickListener {
                showImageContextMenu(uriString)
                true
            }
        }

        private fun loadBitmapWithOrientation(uri: Uri): android.graphics.Bitmap? {
            try {
                // First, decode the bitmap
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap == null) return null

                // Read EXIF orientation
                val exifInputStream = context.contentResolver.openInputStream(uri)
                val exif = exifInputStream?.use {
                    androidx.exifinterface.media.ExifInterface(it)
                }

                val orientation = exif?.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                ) ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL

                // Calculate rotation angle
                val rotationAngle = when (orientation) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }

                // If no rotation needed, return original bitmap
                if (rotationAngle == 0f) return bitmap

                // Rotate the bitmap
                val matrix = android.graphics.Matrix()
                matrix.postRotate(rotationAngle)
                val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )

                // Recycle original if different from rotated
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                }

                return rotatedBitmap
            } catch (e: Exception) {
                Log.e("NotepadApp", "Error loading bitmap with orientation", e)
                return null
            }
        }

        private fun showPlaceholder(imageView: android.widget.ImageView) {
            // Create a simple placeholder: gray background with "?" icon
            imageView.setBackgroundColor(0xFFE0E0E0.toInt())
            imageView.setImageDrawable(null)

            // Create a simple text drawable as placeholder
            val textDrawable = android.graphics.drawable.ShapeDrawable().apply {
                paint.color = Color.GRAY
                paint.textSize = 32f
                paint.textAlign = android.graphics.Paint.Align.CENTER
            }

            // Set a broken image indicator (we'll use a simple colored square for now)
            imageView.setColorFilter(0xFF999999.toInt())
        }

        override fun getItemCount(): Int = imageUris.size

        fun updateImages(newImageUris: List<String>) {
            imageUris.clear()
            imageUris.addAll(newImageUris)
            notifyDataSetChanged()
        }
    }
}

