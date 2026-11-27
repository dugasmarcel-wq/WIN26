package rocks.gorjan.gokixp

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class DaybookWidgetHost(private val activity: MainActivity, page: Int) : FrameLayout(activity) {
    private val manager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, 27600 + page)
    private val provider = ComponentName("com.marcel.daybook", "com.marcel.daybook.Widget")
    private val prefs = activity.getSharedPreferences("daybook-host", 0)
    private val key = "page-" + page
    private val pendingKey = "pending-" + key
    private var displayed = -1
    private var approval: ActivityResultLauncher<Intent>? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        host.startListening()
        approval = activity.activityResultRegistry.register("daybook-bind-" + key,
            ActivityResultContracts.StartActivityForResult()) { result ->
            val pending = prefs.getInt(pendingKey, -1)
            if (pending > 0) {
                val returned = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending) ?: pending
                if (result.resultCode == Activity.RESULT_OK && returned == pending && bound(pending)) {
                    accept(pending)
                } else {
                    discard(pending)
                    message("Daybook is disconnected. Tap Connect to try again.")
                }
            }
            refresh()
        }
        refresh()
    }
    override fun onDetachedFromWindow() {
        approval?.unregister()
        approval = null
        host.stopListening()
        displayed = -1
        super.onDetachedFromWindow()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && approval != null) refresh()
    }
    private fun bound(id: Int) = id > 0 && manager.getAppWidgetInfo(id)?.provider == provider
    private fun accept(id: Int) { prefs.edit().putInt(key, id).remove(pendingKey).apply() }
    private fun discard(id: Int) {
        if (id > 0) host.deleteAppWidgetId(id)
        prefs.edit().remove(pendingKey).apply()
    }
    private fun message(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()

    private fun refresh() {
        val pending = prefs.getInt(pendingKey, -1)
        if (bound(pending)) accept(pending)
        val id = prefs.getInt(key, -1)
        val info = if (id > 0) manager.getAppWidgetInfo(id) else null
        if (info?.provider == provider) {
            if (displayed == id) return
            removeAllViews()
            // The AppCompat activity inflater replaces framework widget classes.
            // RemoteViews requires the original platform methods on those classes.
            val widgetContext = android.view.ContextThemeWrapper(activity.applicationContext, android.R.style.Theme_Material_Light)
            val content = host.createView(widgetContext, id, info)
            addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            content.updateAppWidgetSize(Bundle(), 250, 240, 330, 280)
            displayed = id
            return
        }
        displayed = -1
        val button = getChildAt(0) as? Button ?: Button(activity).also {
            removeAllViews()
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            it.isAllCaps = false
            it.setTextColor(Color.BLACK)
            it.setOnClickListener { connect() }
        }
        button.text = if (manager.installedProviders.any { it.provider == provider }) "Connect Daybook widget" else "Install Daybook"
    }
    private fun connect() {
        android.util.Log.d("DaybookWidgetHost","Connect pressed on $key")
        val launcher = approval ?: return
        val info = manager.installedProviders.firstOrNull { it.provider == provider }
        if (info == null) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://github.com/dugasmarcel-wq/WIN26/releases/tag/win26-update-100141")))
            return
        }
        val old = prefs.getInt(key, -1)
        if (bound(old)) { refresh(); return }
        if (old > 0) {
            host.deleteAppWidgetId(old)
            prefs.edit().remove(key).apply()
        }
        val previous = prefs.getInt(pendingKey, -1)
        if (bound(previous)) { accept(previous); refresh(); return }
        if (previous > 0) discard(previous)
        val id = host.allocateAppWidgetId()
        prefs.edit().putInt(pendingKey, id).apply()
        try {
            if (manager.bindAppWidgetIdIfAllowed(id, info.profile, provider, null)) {
                accept(id)
                refresh()
            } else {
                android.util.Log.d("DaybookWidgetHost","Requesting Android consent for $id")
                launcher.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile))
            }
        } catch (e: RuntimeException) {
            discard(id)
            refresh()
            android.util.Log.w("DaybookWidgetHost", "Widget connection failed", e)
            message("Android could not connect the widget. Open Daybook, then try Connect again.")
        }
    }
}
