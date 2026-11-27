package rocks.gorjan.gokixp

import android.accessibilityservice.AccessibilityService
import android.appwidget.AppWidgetHostView
import android.content.Context
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
        val root=instrumentation.uiAutomation.rootInActiveWindow
        val state=instrumentation.targetContext.getSharedPreferences("daybook-host",0).all
        fail("$label; window=${root?.packageName}; binding=$state")
    }
    private fun positive() = instrumentation.uiAutomation.rootInActiveWindow
        ?.findAccessibilityNodeInfosByViewId("android:id/button1")?.firstOrNull()
    @Test fun bindRetryAndTapLiveWidget() {
        val automation = instrumentation.uiAutomation
        val service = automation.serviceInfo
        service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = service
        val ctx=instrumentation.targetContext
        val desktop=ctx.getSharedPreferences(MainActivity.PREFS_NAME,Context.MODE_PRIVATE)
        val oldTheme=desktop.getString("selected_theme",null)
        val oldWidgets=desktop.getStringSet("win98_widget_page_1",null)?.toSet()
        desktop.edit().putString("selected_theme","Windows Classic").putStringSet("win98_widget_page_1",setOf("daybook")).commit()
        val bindings=ctx.getSharedPreferences("daybook-host",0)
        try {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun findHost(view: View): DaybookWidgetHost? {
                if(view is DaybookWidgetHost)return view
                if(view is ViewGroup) for(i in 0 until view.childCount) findHost(view.getChildAt(i))?.let { return it }
                return null
            }
            fun host(a:MainActivity)=findHost(a.findViewById(R.id.desktop_icons_container))!!
            onView(withText("Connect Daybook widget")).perform(click())
            waitFor("Consent or existing Android host permission") { positive()!=null || bindings.getInt("page-1",-1)>0 }
            if(positive()!=null){
                automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                waitFor("Cancellation returns to WIN26") { automation.rootInActiveWindow?.packageName == ctx.packageName && !bindings.contains("pending-page-1") }
                onView(withText("Connect Daybook widget")).perform(click())
                waitFor("Retry opens consent") { positive()!=null }
                assertTrue(positive()!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            }
            fun checkRow(fragment: String, tap: Boolean = false): Boolean {
                var found = false
                scenario.onActivity { a ->
                    val live = host(a).getChildAt(0) as? AppWidgetHostView ?: return@onActivity
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
            waitFor("Bound goal survives host recreation") { checkRow(", checked") }
            scenario.onActivity { a ->
                val live = host(a).getChildAt(0) as AppWidgetHostView
                val id = a.packageManager.getResourcesForApplication("com.marcel.daybook")
                    .getIdentifier("openNote", "id", "com.marcel.daybook")
                assertTrue(live.findViewById<View>(id).performClick())
            }
            waitFor("Open note launches Daybook") { automation.rootInActiveWindow?.packageName == "com.marcel.daybook" }
        }
        } finally {
            desktop.edit().putString("selected_theme",oldTheme).putStringSet("win98_widget_page_1",oldWidgets).commit()
        }
    }
}
