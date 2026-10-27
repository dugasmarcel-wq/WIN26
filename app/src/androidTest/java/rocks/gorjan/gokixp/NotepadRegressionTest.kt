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
