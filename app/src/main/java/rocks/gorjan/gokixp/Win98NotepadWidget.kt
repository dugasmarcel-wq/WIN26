package rocks.gorjan.gokixp

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import rocks.gorjan.gokixp.apps.notepad.NotepadApp

/** The actual Classic Notepad editor in one pinned window, without content transforms. */
object Win98NotepadWidget {
    fun create(activity: MainActivity, pageIndex: Int): View = EmbeddedNotepad(activity, pageIndex)

    /** Only a double-tap-armed editor blocks Classic page navigation. */
    fun isEditorArmed(host: View): Boolean {
        val embedded = (host as? android.view.ViewGroup)?.getChildAt(0) as? EmbeddedNotepad
        return embedded?.isEditorArmed() == true
    }

    private class EmbeddedNotepad(activity: MainActivity, page: Int) : FrameLayout(activity) {
        private val controller: NotepadApp

        init {
            val window = activity.layoutInflater.inflate(R.layout.win98_notepad_widget, this, false)
            addView(window, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            val title = window.findViewById<TextView>(R.id.notepad_window_title)
            controller = activity.createNotepadController({ title.text = it }, page)
            controller.setupApp(window.findViewById(R.id.notepad_classic_shell))
            window.findViewById<View>(R.id.notepad_window_expand).setOnClickListener { controller.openFullNotepad() }
        }

        fun isEditorArmed(): Boolean = controller.isEmbeddedEditorArmed()

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            controller.resume()
        }

        override fun onDetachedFromWindow() {
            controller.cleanup()
            super.onDetachedFromWindow()
        }
    }
}
