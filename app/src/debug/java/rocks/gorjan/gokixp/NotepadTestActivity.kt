package rocks.gorjan.gokixp

import android.app.Activity
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import rocks.gorjan.gokixp.apps.notepad.NotepadApp

/** Debug-only fixture: tests never open or modify the launcher's real notes. */
class NotepadTestActivity : Activity() {
    lateinit var controller: NotepadApp
    lateinit var surface: android.view.View
    lateinit var host: Win98ResizableWidgetHost
    var expanded = false
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences(if (name == MainActivity.PREFS_NAME) "notepad_test_fixture" else name, mode)

    override fun onCreate(state: Bundle?) {
        setTheme(R.style.Theme_GokiXP_Classic)
        super.onCreate(state)
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        if (!prefs.contains("notepad_notes")) prefs.edit().putString("notepad_notes", """[
            {"id":"old","title":"Trip","content":"Existing text","category":"day_to_day","future":{"keep":true}},
            {"id":"long","title":"Project","content":"Long-term text","category":"long_term"},
            {"id":"reminder","title":"Work","content":"Reminder body","category":"reminder_notes"}
        ]""").commit()
        surface = layoutInflater.inflate(R.layout.win98_notepad_widget, null)
        val picker = object : ActivityResultLauncher<String>() {
            override fun launch(input: String, options: ActivityOptionsCompat?) {}
            override fun unregister() {}
            override fun getContract(): ActivityResultContract<String, *> = ActivityResultContracts.GetContent()
        }
        controller = NotepadApp(this, {}, { _, _, _ -> }, { _, _, _, save -> save("Renamed") },
            { surface.findViewById<TextView>(R.id.notepad_window_title).text = it },
            picker, {}, {}, { 0f to 0f }, embeddedPage = 1)
        controller.setupApp(surface.findViewById(R.id.notepad_classic_shell))
        surface.findViewById<android.view.View>(R.id.notepad_window_expand).setOnClickListener { expanded = true }
        fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
        host = Win98ResizableWidgetHost(this, dp(330), dp(120), dp(100), surface,
            { _, _, _, _ -> error("Pinned widget must not save arbitrary geometry") }, {}, resources.displayMetrics.widthPixels to dp(440))
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF008080.toInt()) }
        root.addView(host, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, -2, Gravity.TOP).apply {
            setMargins(dp(12), dp(24), dp(12), dp(12))
        })
        setContentView(root)
    }

    override fun onDestroy() {
        controller.cleanup()
        super.onDestroy()
    }
}
