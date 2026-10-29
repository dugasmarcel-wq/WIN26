package rocks.gorjan.gokixp

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.NumberPicker
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import com.google.gson.JsonParser
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class NotepadRegressionTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()

    @Before fun resetFixture() {
        instrument.targetContext.getSharedPreferences("notepad_test_fixture", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun rows(activity: NotepadTestActivity) = JsonParser.parseString(
        activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .getString("notepad_notes", "[]")).asJsonArray

    private fun capture(activity: NotepadTestActivity, name: String) {
        val view = activity.surface
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val folder = activity.getExternalFilesDir("notepad-layout-checks")!!
        folder.mkdirs()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun menusPreserveSelectionAndDataAcrossRecreation() {
        ActivityScenario.launch(NotepadTestActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            scenario.onActivity { activity ->
                capture(activity, "day-to-day")
                activity.surface.findViewById<EditText>(R.id.notes).setText("Existing text\nEdited in place")
                assertEquals(3, rows(activity).size())
                assertEquals("Trip", rows(activity)[0].asJsonObject["title"].asString)
            }
            repeat(2) {
                onView(withText("Day-to-Day")).perform(click())
                onView(withText("Add note")).perform(click())
            }
            scenario.onActivity { activity ->
                val data = rows(activity)
                assertEquals(5, data.size())
                assertNotEquals(data[3].asJsonObject["id"].asString, data[4].asJsonObject["id"].asString)
                val today = java.text.SimpleDateFormat("yyyyMMMd", java.util.Locale.ENGLISH).format(java.util.Date())
                assertEquals(today, data[4].asJsonObject["title"].asString)
            }
            onView(withText("Day-to-Day")).perform(click())
            onView(withText("Rename note")).perform(click())
            onView(withText("Day-to-Day")).perform(click())
            onView(withText("Hide notes sidebar")).perform(click())
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.surface.findViewById<View>(R.id.notes_list_container).visibility)
                capture(activity, "sidebar-collapsed")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.surface.findViewById<View>(R.id.notes_list_container).visibility)
                assertEquals("Renamed", rows(activity)[4].asJsonObject["title"].asString)
            }
            onView(withText("Day-to-Day")).perform(click())
            onView(withText("Show notes sidebar")).perform(click())
            onView(withText("Long-Term")).perform(click())
            // Dismiss the category menu by opening its Add action, then switch to the old note.
            onView(withText("Add note")).perform(click())
            scenario.onActivity { activity ->
                activity.controller.openNote("long", "long_term")
                assertEquals("Long-term text", activity.surface.findViewById<EditText>(R.id.notes).text.toString())
                activity.controller.openNote("old", "day_to_day")
                assertEquals("Existing text\nEdited in place", activity.surface.findViewById<EditText>(R.id.notes).text.toString())
                assertTrue(rows(activity)[0].asJsonObject["future"].asJsonObject["keep"].asBoolean)
            }
        }
    }

    private fun setNumber(value: Int) = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(NumberPicker::class.java)
        override fun getDescription() = "Set picker to $value"
        override fun perform(ui: UiController, view: View) { (view as NumberPicker).value = value; ui.loopMainThreadUntilIdle() }
    }

    @Test fun calendarMonthRolloverTimeAndSubjectPersist() {
        ActivityScenario.launch(NotepadTestActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            scenario.onActivity { activity ->
                val data = rows(activity)
                val initial = Calendar.getInstance().apply { set(2099, Calendar.JANUARY, 31, 8, 0, 0); set(Calendar.MILLISECOND, 0) }
                data[2].asJsonObject.addProperty("reminderAt", initial.timeInMillis)
                activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString("notepad_notes", data.toString()).commit()
                activity.controller.openNote("reminder", "reminder_notes")
                activity.surface.findViewById<EditText>(R.id.notepad_reminder_subject).setText("Work shift")
                capture(activity, "reminder")
            }
            onView(withText("Set Date / Time")).perform(click())
            onView(withText("›")).perform(click())
            onView(withContentDescription("Select 2099-02-15")).perform(click())
            onView(withContentDescription("Hour (24-hour)")).perform(setNumber(9))
            onView(withContentDescription("Minute")).perform(setNumber(17))
            onView(withText("Save Reminder")).perform(click())
            scenario.recreate()
            scenario.onActivity { activity ->
                activity.controller.openNote("reminder", "reminder_notes")
                assertEquals("Work shift", activity.surface.findViewById<EditText>(R.id.notepad_reminder_subject).text.toString())
                val selected = Calendar.getInstance().apply { timeInMillis = rows(activity)[2].asJsonObject["reminderAt"].asLong }
                assertEquals(2099, selected.get(Calendar.YEAR))
                assertEquals(Calendar.FEBRUARY, selected.get(Calendar.MONTH))
                assertEquals(15, selected.get(Calendar.DAY_OF_MONTH))
                assertEquals(9, selected.get(Calendar.HOUR_OF_DAY))
                assertEquals(17, selected.get(Calendar.MINUTE))
                Win98NoteReminderReceiver.cancel(activity, "reminder")
            }
        }
    }

    @Test fun notificationCreationCancellationAndBootRestoration() {
        instrument.uiAutomation.executeShellCommand("pm grant ${instrument.targetContext.packageName} android.permission.POST_NOTIFICATIONS").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
        ActivityScenario.launch(NotepadTestActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            scenario.onActivity { activity ->
                val data = rows(activity)
                data[2].asJsonObject.addProperty("reminderAt", System.currentTimeMillis() - 5000L)
                data[2].asJsonObject.addProperty("reminderSubject", "Work shift")
                activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString("notepad_notes", data.toString()).commit()
                Win98NoteReminderReceiver().onReceive(activity, Intent().putExtra("note_id", "reminder"))
                val manager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                var notification = manager.activeNotifications.firstOrNull { it.tag == "reminder" && it.id == 26 }
                val deadline = android.os.SystemClock.uptimeMillis() + 5000L
                while (notification == null && android.os.SystemClock.uptimeMillis() < deadline) {
                    android.os.SystemClock.sleep(50L)
                    notification = manager.activeNotifications.firstOrNull { it.tag == "reminder" && it.id == 26 }
                }
                assertNotNull(notification)
                assertEquals("Work shift", notification!!.notification.extras.getString("android.title"))
                assertNotNull(notification!!.notification.contentIntent)
                Win98NoteReminderReceiver.cancel(activity, "reminder")
                val cancelDeadline = android.os.SystemClock.uptimeMillis() + 5000L
                while (manager.activeNotifications.any { it.tag == "reminder" } && android.os.SystemClock.uptimeMillis() < cancelDeadline) {
                    android.os.SystemClock.sleep(50L)
                }
                assertFalse(manager.activeNotifications.any { it.tag == "reminder" })
                Win98NoteReminderReceiver().onReceive(activity, Intent(Intent.ACTION_BOOT_COMPLETED))
                val pending = PendingIntent.getBroadcast(activity, "reminder".hashCode(),
                    Intent(activity, Win98NoteReminderReceiver::class.java)
                        .setData(Uri.parse("win26://note-reminder/reminder")),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                assertNotNull(pending)
                Win98NoteReminderReceiver.cancel(activity, "reminder")
            }
        }
    }

    @Test fun actualClassicLauncherNotepadMatchesDesktopWidth() {
        // Unlike the isolated NotepadTestActivity, this exercises MainActivity's
        // real widget creation, page container and first-layout correction.
        val prefs = instrument.targetContext.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val oldTheme = prefs.getString("selected_theme", null)
        val oldWidgets = prefs.getStringSet("win98_widget_page_1", null)?.toSet()
        prefs.edit().putString("selected_theme", "Windows Classic")
            .putStringSet("win98_widget_page_1", setOf("notepad_98")).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                instrument.waitForIdleSync()
                scenario.onActivity { activity ->
                    val desktop = activity.findViewById<ViewGroup>(R.id.desktop_icons_container)
                    val host = (0 until desktop.childCount)
                        .map { desktop.getChildAt(it) }
                        .filterIsInstance<Win98ResizableWidgetHost>()
                        .firstOrNull { it.contentDescription?.toString()?.startsWith("Pinned Notepad") == true }
                    assertNotNull("Notepad must exist in the real launcher", host)
                    val target = host!!
                    // MainActivity.dp truncates to pixels; match that exact conversion.
                    val inset = (12 * activity.resources.displayMetrics.density).toInt()
                    assertTrue("Desktop should have a real measured width", desktop.width > inset * 4)
                    assertEquals("Pinned width must come from desktop, not a 320dp content width",
                        desktop.width - inset * 2, target.width)
                    assertEquals("Widget must start at 12dp", inset.toFloat(), target.x, 1f)
                    assertEquals("Widget must end at 12dp",
                        inset.toFloat(), desktop.width - target.x - target.width, 1f)
                    // Hardware-backed wallpaper bitmaps cannot be drawn into a software Canvas.
                    // Use geometry assertions here; other tests capture Notepad fixture bitmaps.
                }
            }
        } finally {
            val editor = prefs.edit()
            if (oldTheme == null) editor.remove("selected_theme") else editor.putString("selected_theme", oldTheme)
            if (oldWidgets == null) editor.remove("win98_widget_page_1")
            else editor.putStringSet("win98_widget_page_1", oldWidgets)
            editor.commit()
        }
    }

    @Test fun doubleTapEnablesKeyboardAndNativeCursorPlacement() {
        ActivityScenario.launch(NotepadTestActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            scenario.onActivity { activity ->
                val editor = activity.surface.findViewById<EditText>(R.id.notes)
                editor.setText("First line of text\nSecond line of text\nThird line of text")
                val firstX = editor.totalPaddingLeft + 10f
                val firstY = editor.totalPaddingTop + editor.layout.getLineTop(0) + 6f
                val thirdY = editor.totalPaddingTop + editor.layout.getLineTop(2) + 6f
                fun tap(x: Float, y: Float, time: Long) {
                    val down = android.view.MotionEvent.obtain(time, time,
                        android.view.MotionEvent.ACTION_DOWN, x, y, 0)
                    val up = android.view.MotionEvent.obtain(time, time + 35L,
                        android.view.MotionEvent.ACTION_UP, x, y, 0)
                    editor.dispatchTouchEvent(down)
                    editor.dispatchTouchEvent(up)
                    down.recycle()
                    up.recycle()
                }
                val start = android.os.SystemClock.uptimeMillis()
                assertFalse(activity.controller.isEmbeddedEditorArmed())
                tap(firstX, firstY, start)
                assertFalse("Single tap must not arm the keyboard", activity.controller.isEmbeddedEditorArmed())
                tap(firstX, firstY, start + 160L)
                assertTrue("Double tap should arm keyboard", activity.controller.isEmbeddedEditorArmed())
                tap(firstX, thirdY, start + 700L)
                assertTrue("A later tap must move the cursor to the third line",
                    editor.selectionStart >= editor.layout.getLineStart(2))
            }
        }
    }

    @Test fun swipeAcrossInactiveNotepadPagesRealClassicDesktop() {
        val prefs = instrument.targetContext.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val oldTheme = prefs.getString("selected_theme", null)
        val oldWidgets = prefs.getStringSet("win98_widget_page_1", null)?.toSet()
        prefs.edit().putString("selected_theme", "Windows Classic")
            .putStringSet("win98_widget_page_1", setOf("notepad_98")).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                instrument.waitForIdleSync()
                var x = 0f
                var y = 0f
                var width = 0f
                scenario.onActivity { activity ->
                    val desktop = activity.findViewById<ViewGroup>(R.id.desktop_icons_container)
                    val host = (0 until desktop.childCount).map { desktop.getChildAt(it) }
                        .filterIsInstance<Win98ResizableWidgetHost>()
                        .first { it.contentDescription?.toString()?.startsWith("Pinned Notepad") == true }
                    val editor = host.findViewById<EditText>(R.id.notes)
                    assertTrue(editor.isShown)
                    assertFalse(Win98NotepadWidget.isEditorArmed(host))
                    val location = IntArray(2)
                    editor.getLocationOnScreen(location)
                    x = location[0] + editor.width * 0.7f
                    y = location[1] + editor.height * 0.45f
                    width = desktop.width.toFloat()
                }
                // Dispatch via the activity's real touch pipeline. Instrumentation's
                // sendPointerSync rejects events if an Android system overlay has focus.
                scenario.onActivity { activity ->
                    val start = android.os.SystemClock.uptimeMillis()
                    fun send(action: Int, at: Long, atX: Float) {
                        val event = android.view.MotionEvent.obtain(start, at, action, atX, y, 0)
                        activity.dispatchTouchEvent(event)
                        event.recycle()
                    }
                    send(android.view.MotionEvent.ACTION_DOWN, start, x)
                    fun field(name: String): Any? = MainActivity::class.java
                        .getDeclaredField(name).apply { isAccessible = true }.get(activity)
                    val debugDown = android.view.MotionEvent.obtain(start, start,
                        android.view.MotionEvent.ACTION_DOWN, x, y, 0)
                    val idleMethod = MainActivity::class.java.getDeclaredMethod(
                        "isInactivePinnedNotepadTouch", Int::class.javaPrimitiveType,
                        android.view.MotionEvent::class.java).apply { isAccessible = true }
                    val inactive = idleMethod.invoke(activity, 1, debugDown)
                    debugDown.recycle()
                    assertFalse("Pager blocks idle Notepad DOWN: inactive=$inactive, " +
                        "page=${field("win98CurrentPage")}, " +
                        "menu=${field("isStartMenuVisible")}, " +
                        "pagerBlocked=${field("win98DesktopPagerBlocked")}",
                        field("win98DesktopPagerBlocked") as Boolean)
                    send(android.view.MotionEvent.ACTION_MOVE, start + 40L, x - width * 0.15f)
                    send(android.view.MotionEvent.ACTION_MOVE, start + 90L, x - width * 0.38f)
                    assertTrue("Horizontal drag must be acquired by the Classic pager",
                        activity.findViewById<View>(R.id.desktop_icons_container).translationX < -width * 0.08f)
                    send(android.view.MotionEvent.ACTION_UP, start + 130L, x - width * 0.38f)
                }
                instrument.waitForIdleSync()
                android.os.SystemClock.sleep(300L)
                scenario.onActivity { activity ->
                    assertEquals("Swipe over idle Notepad should leave Desktop",
                        View.INVISIBLE,
                        activity.findViewById<View>(R.id.desktop_icons_container).visibility)
                }
            }
        } finally {
            val editor = prefs.edit()
            if (oldTheme == null) editor.remove("selected_theme") else editor.putString("selected_theme", oldTheme)
            if (oldWidgets == null) editor.remove("win98_widget_page_1")
            else editor.putStringSet("win98_widget_page_1", oldWidgets)
            editor.commit()
        }
    }

    @Test fun pinnedWidgetUsesParentWidthWithTwelveDpMargins() {
        ActivityScenario.launch(NotepadTestActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.host.parent as android.widget.FrameLayout
                val margin = (12 * activity.resources.displayMetrics.density + 0.5f).toInt()
                assertEquals("Left inset must be 12dp", margin, activity.host.left)
                assertEquals("Right inset must be 12dp", margin, root.width - activity.host.right)
                assertEquals(root.width - 2 * margin, activity.host.width)
                assertEquals(1f, activity.surface.scaleX, 0f)
                assertEquals(1f, activity.surface.scaleY, 0f)
                capture(activity, "pinned-width")
            }
        }
    }

    @Test fun expandedClassicWindowKeepsTitleInBoundsAndClosesCleanly() {
        lateinit var dialog: WindowsDialog
        ActivityScenario.launch(NotepadTestActivity::class.java).use { scenario ->
            instrument.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.host.parent as android.widget.FrameLayout
                dialog = WindowsDialog(activity,
                    initialTheme = rocks.gorjan.gokixp.theme.AppTheme.WindowsClassic)
                dialog.windowIdentifier = "test.notepad.expanded"
                dialog.setSaveState(false)
                dialog.setTitle("Notepad")
                dialog.setContentView(activity.layoutInflater.inflate(R.layout.program_notepad_classic, null))
                dialog.setMaximizable(true)
                dialog.setWindowSizePercentage(90f, 50f)
                FloatingWindowManager(activity, root).showWindow(dialog)
            }
            instrument.waitForIdleSync()
            scenario.onActivity { dialog.maximizeWindow() }
            instrument.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.host.parent as android.widget.FrameLayout
                val frame = dialog.findViewById<View>(R.id.window_frame)
                val title = dialog.findViewById<View>(R.id.dialog_title_bar)
                val border = dialog.findViewById<View>(R.id.window_border)
                val editor = dialog.findViewById<EditText>(R.id.notes)
                assertEquals(root.height, frame.height)
                assertEquals(root.width, frame.width)
                assertTrue("Title bar must be visible", title.height > 0 && title.top >= 0)
                assertTrue("Content must begin below title", border.top >= title.bottom)
                assertTrue("Editor must not extend below frame", border.bottom <= frame.height)
                assertTrue("Editor must stay inside content", editor.width > 0 && editor.height > 0)
                dialog.findViewById<View>(R.id.dialog_close_button).performClick()
                assertNull("Closing Notepad must remove the overlay", dialog.parent)
                assertSame("Pinned widget must remain", root, activity.host.parent)
                assertTrue(activity.host.isShown)
            }
        }
    }

    @Test fun nativeLayoutFitsNarrowScreensFontSizesAndKeyboardViewport() {
        instrument.runOnMainSync {
            for (density in listOf(160, 320, 480)) for (font in listOf(1f, 1.3f))
                for (widthDp in listOf(280, 320, 360)) for (heightDp in listOf(280, 440)) {
                    val base = instrument.targetContext
                    val config = Configuration(base.resources.configuration).apply { densityDpi = density; fontScale = font }
                    val context = ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_GokiXP_Classic)
                    fun dp(n: Int) = (n * context.resources.displayMetrics.density + 0.5f).toInt()
                    val content = LayoutInflater.from(context).inflate(R.layout.win98_notepad_widget, null)
                    content.findViewById<View>(R.id.notepad_reminder_panel).visibility = View.VISIBLE
                    val editor = content.findViewById<EditText>(R.id.notes)
                    editor.setText("Long text wraps within the writing area on every measured line. ".repeat(30))
                    val host = Win98ResizableWidgetHost(context, dp(330), dp(120), dp(100), content,
                        { _, _, _, _ -> fail("Pinned layout changed geometry") }, {}, dp(320) to dp(440))
                    host.measure(View.MeasureSpec.makeMeasureSpec(dp(widthDp), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(dp(heightDp), View.MeasureSpec.AT_MOST))
                    host.layout(0, 0, host.measuredWidth, host.measuredHeight)
                    assertEquals(minOf(dp(320), dp(widthDp)), host.width)
                    assertEquals(minOf(dp(440), dp(heightDp)), host.height)
                    assertEquals(1f, content.scaleX, 0f)
                    assertEquals(1f, content.scaleY, 0f)
                    assertTrue(editor.height > dp(40))
                    fun check(view: View) {
                        if (view is ViewGroup && view !is androidx.recyclerview.widget.RecyclerView) {
                            for (index in 0 until view.childCount) {
                                val child = view.getChildAt(index)
                                if (child.visibility == View.GONE) continue
                                assertTrue("Left clipping: ${child.javaClass.simpleName}", child.left >= 0)
                                assertTrue("Right clipping: ${child.javaClass.simpleName}", child.right <= view.width)
                                assertTrue("Top clipping: ${child.javaClass.simpleName}", child.top >= 0)
                                assertTrue("Bottom clipping: ${child.javaClass.simpleName}", child.bottom <= view.height)
                                check(child)
                            }
                        }
                    }
                    check(content)
                    val textWidth = editor.width - editor.compoundPaddingLeft - editor.compoundPaddingRight
                    for (line in 0 until editor.layout.lineCount) {
                        assertTrue("Editor line clips at $density/$font/$widthDp/$heightDp",
                            editor.layout.getLineWidth(line) <= textWidth + editor.paint.measureText(" ") + 1f)
                    }
                }
        }
    }
}
