package rocks.gorjan.gokixp

import android.accessibilityservice.AccessibilityService
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaybookWidgetConnectionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun waitFor(label: String, test: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < end) {
            if (test()) return
            SystemClock.sleep(100)
        }
        fail(label)
    }
    private fun positive() = instrumentation.uiAutomation.rootInActiveWindow
        ?.findAccessibilityNodeInfosByViewId("android:id/button1")?.firstOrNull()
    @Test fun bindRetryAndTapLiveWidget() {
        val automation = instrumentation.uiAutomation
        val service = automation.serviceInfo
        service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = service
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var host: DaybookWidgetHost
            fun mount() {
                scenario.onActivity { a ->
                    host = DaybookWidgetHost(a, 2)
                    a.addContentView(host, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            }
            mount()
            onView(withText("Connect Daybook widget")).perform(click())
            waitFor("Android shows widget consent") { positive()!=null }
            automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            onView(withText("Connect Daybook widget")).perform(click())
            waitFor("Retry opens consent") { positive()!=null }
            assertTrue(positive()!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            fun checkRow(fragment: String, tap: Boolean = false): Boolean {
                var found = false
                scenario.onActivity { a ->
                    val live = host.getChildAt(0) as? AppWidgetHostView ?: return@onActivity
                    val rowId = a.packageManager.getResourcesForApplication("com.marcel.daybook")
                        .getIdentifier("row", "id", "com.marcel.daybook")
                    val row = live.findViewById<View>(rowId) ?: return@onActivity
                    found = row.contentDescription?.toString()?.endsWith(fragment) == true
                    if (found && tap) row.performClick()
                }
                return found
            }
            waitFor("Live unchecked Daybook goal") { checkRow(", unchecked",true) }
            waitFor("Daybook check-in changes in WIN26") { checkRow(", checked") }
            scenario.recreate()
            mount()
            waitFor("Bound goal survives host recreation") { checkRow(", checked") }
            scenario.onActivity { a ->
                val live = host.getChildAt(0) as AppWidgetHostView
                val id = a.packageManager.getResourcesForApplication("com.marcel.daybook")
                    .getIdentifier("openNote", "id", "com.marcel.daybook")
                assertTrue(live.findViewById<View>(id).performClick())
            }
            waitFor("Open note launches Daybook") { automation.rootInActiveWindow?.packageName == "com.marcel.daybook" }
        }
    }
}
