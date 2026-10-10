package rocks.gorjan.gokixp

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/**
 * Classic Find window used only for the Windows 98 swipe-up gesture. It deliberately
 * follows the existing WIN26 Windows shell: square bevels, dark blue title bar,
 * gray surfaces and dense Explorer-like results, not modern Android search cards.
 *
 * No background requests, search history or telemetry. Web search is explicit.
 */
class Win98AppSearchDialog(
    context: Context,
    private val onLaunchApp: (AppInfo) -> Unit,
    private val onWebSearch: (String) -> Unit
) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private val shellGray = Color.rgb(192, 192, 192)
    private val ink = Color.BLACK
    private val darkEdge = Color.rgb(82, 82, 82)
    private val blue = Color.rgb(0, 0, 128)
    private val allApps = ArrayList<AppInfo>()
    private val matches = ArrayList<AppInfo>()
    private var pinned = emptySet<String>()
    private var loaded = false

    private val resultsTitle = TextView(context)
    private val emptyText = TextView(context)
    private val searchInput = EditText(context)
    private val clearButton = TextView(context)
    private val webButton = TextView(context)
    private val statusText = TextView(context)
    private val resultAdapter = ApplicationAdapter()
    private val resultsList = RecyclerView(context)
    private val root = LinearLayout(context)

    private fun flat(fill: Int, edge: Int = darkEdge): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), edge)
            cornerRadius = 0f
        }

    private fun sunken(): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(dp(2), Color.rgb(110, 110, 110))
            cornerRadius = 0f
        }

    private fun winButton(): android.graphics.drawable.Drawable =
        AppCompatResources.getDrawable(context, R.drawable.window_button_background)
            ?.constantState?.newDrawable()?.mutate()
            ?: flat(shellGray)

    private fun label(text: String, size: Float = 12f, bold: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(ink)
            gravity = Gravity.CENTER_VERTICAL
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            includeFontPadding = false
        }

    private fun button(text: String, description: String, click: () -> Unit): TextView =
        label(text, 12f).apply {
            gravity = Gravity.CENTER
            background = winButton()
            contentDescription = description
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }

    init {
        setCancelable(true)
        setCanceledOnTouchOutside(false)

        root.apply {
            orientation = LinearLayout.VERTICAL
            background = flat(shellGray, Color.rgb(47, 47, 47))
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }

        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(4), 0)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(0, 0, 128), Color.rgb(0, 95, 190))
            ).apply { cornerRadius = 0f }
        }
        titleBar.addView(TextView(context).apply {
            text = "Find: Applications"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }, LinearLayout.LayoutParams(0, dp(30), 1f))
        titleBar.addView(button("X", "Close Find") { dismiss() },
            LinearLayout.LayoutParams(dp(25), dp(23)))
        root.addView(titleBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(31)
        ))

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(5), dp(7), dp(4))
        }
        root.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        body.addView(label("Find an application by name:", 12f),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)
            ))

        val searchLine = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchInput.apply {
            hint = "Type to find an app"
            setHintTextColor(Color.rgb(118, 118, 118))
            setTextColor(Color.BLACK)
            textSize = 14f
            setSingleLine(true)
            background = sunken()
            setPadding(dp(9), 0, dp(6), 0)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filterApps()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            setOnEditorActionListener { _, actionId, event ->
                val submitted = actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_DOWN)
                if (submitted) {
                    if (matches.isNotEmpty()) openApp(matches.first())
                    else if (text.toString().isNotBlank()) searchWeb()
                }
                submitted
            }
        }
        searchLine.addView(searchInput,
            LinearLayout.LayoutParams(0, dp(39), 1f))
        clearButton.apply {
            text = "Clear"
            textSize = 11f
            gravity = Gravity.CENTER
            background = winButton()
            isClickable = true
            isFocusable = true
            setOnClickListener { searchInput.setText("") }
        }
        searchLine.addView(clearButton,
            LinearLayout.LayoutParams(dp(54), dp(30)).apply {
                marginStart = dp(6)
            })
        body.addView(searchLine, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
        ))

        val listFrame = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = flat(Color.WHITE, Color.rgb(90, 90, 90))
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        val columnHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = winButton()
            setPadding(dp(9), 0, dp(6), 0)
        }
        columnHeader.addView(label("Name", 12f, true),
            LinearLayout.LayoutParams(0, dp(25), 1f))
        resultsTitle.apply {
            textSize = 11f
            setTextColor(Color.rgb(52, 52, 52))
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            includeFontPadding = false
        }
        columnHeader.addView(resultsTitle,
            LinearLayout.LayoutParams(dp(88), dp(25)))
        listFrame.addView(columnHeader, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(26)
        ))

        val listArea = android.widget.FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
        }
        resultsList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = resultAdapter
            itemAnimator = null
            setHasFixedSize(true)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = true
        }
        listArea.addView(resultsList,
            android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))

        emptyText.apply {
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.rgb(90, 90, 90))
            setPadding(dp(8), dp(16), dp(8), dp(16))
        }
        listArea.addView(emptyText,
            android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
        listFrame.addView(listArea, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        body.addView(listFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = dp(7) })

        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, 0, 0)
        }
        statusText.apply {
            textSize = 11f
            setTextColor(Color.rgb(45, 45, 45))
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }
        bottom.addView(statusText, LinearLayout.LayoutParams(0, dp(33), 1f))
        webButton.apply {
            text = "Search Web..."
            textSize = 11f
            gravity = Gravity.CENTER
            background = winButton()
            isClickable = true
            isFocusable = true
            setOnClickListener { searchWeb() }
        }
        bottom.addView(webButton,
            LinearLayout.LayoutParams(dp(114), dp(27)))
        body.addView(bottom, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(37)
        ))

        // The dialog is sized above the Android keyboard. On edge-to-edge Android
        // versions inset the title bar only if the decor actually overlaps the
        // status bar; this avoids a clipped title and avoids double-padding.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val topOfStatusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val extraTop = (topOfStatusBar - location[1]).coerceAtLeast(0)
            val desiredTop = dp(3) + extraTop
            if (view.paddingTop != desiredTop) {
                view.setPadding(dp(3), desiredTop, dp(3), dp(3))
            }
            insets
        }

        val outer = android.widget.FrameLayout(context).apply {
            setPadding(dp(8), dp(8), dp(8), dp(7))
            background = ColorDrawable(Color.TRANSPARENT)
        }
        outer.addView(root, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(outer)
        filterApps()
    }

    override fun onStart() {
        super.onStart()
        window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, true)
            w.setBackgroundDrawableResource(android.R.color.transparent)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(0.34f)
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
            w.setGravity(Gravity.TOP)
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        ViewCompat.requestApplyInsets(root)
        searchInput.requestFocus()
        searchInput.post {
            if (isShowing) {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    fun setApps(apps: List<AppInfo>, pinnedPackages: Set<String>) {
        allApps.clear()
        allApps.addAll(apps)
        pinned = pinnedPackages
        loaded = true
        filterApps()
    }

    private fun filterApps() {
        val query = searchInput.text.toString().trim().lowercase(Locale.getDefault())
        val filtered = if (query.isEmpty()) allApps else allApps.filter {
            it.name.lowercase(Locale.getDefault()).contains(query)
        }
        matches.clear()
        matches.addAll(filtered.sortedWith(
            compareBy<AppInfo> {
                if (query.isNotEmpty() && it.name.lowercase(Locale.getDefault()).startsWith(query)) 0 else 1
            }.thenBy { if (it.packageName in pinned) 0 else 1 }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
        ))
        resultAdapter.notifyDataSetChanged()
        if (matches.isNotEmpty()) resultsList.scrollToPosition(0)
        resultsTitle.text = if (loaded) matches.size.toString() + " found" else "Loading..."
        statusText.text = when {
            !loaded -> "Loading installed applications"
            matches.size == 1 -> "1 application"
            else -> matches.size.toString() + " applications"
        }
        emptyText.text = if (!loaded) "Loading installed apps..." else
            "No matching applications"
        emptyText.visibility = if (!loaded || matches.isEmpty()) View.VISIBLE else View.GONE
        clearButton.visibility = if (searchInput.text.isNotEmpty()) View.VISIBLE else View.INVISIBLE
        webButton.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openApp(app: AppInfo) {
        dismiss()
        onLaunchApp(app)
    }

    private fun searchWeb() {
        val query = searchInput.text.toString().trim()
        if (query.isBlank()) return
        dismiss()
        onWebSearch(query)
    }

    private inner class ApplicationAdapter : RecyclerView.Adapter<ApplicationAdapter.AppHolder>() {
        inner class AppHolder(
            val row: LinearLayout,
            val icon: ImageView,
            val name: TextView
        ) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppHolder {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, dp(6), 0)
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(blue))
                    addState(intArrayOf(), ColorDrawable(Color.WHITE))
                }
                minimumHeight = dp(43)
                isClickable = true
                isFocusable = true
            }
            val icon = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(29), dp(29)).apply {
                marginEnd = dp(10)
            })
            val name = label("", 13f)
            name.maxLines = 1
            name.ellipsize = android.text.TextUtils.TruncateAt.END
            name.setTextColor(ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                intArrayOf(Color.WHITE, Color.BLACK)
            ))
            row.addView(name, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            ))
            row.setOnClickListener {
                val holder = row.tag as? AppHolder ?: return@setOnClickListener
                val i = holder.bindingAdapterPosition
                if (i != RecyclerView.NO_POSITION && i in matches.indices) {
                    openApp(matches[i])
                }
            }
            return AppHolder(row, icon, name).also { row.tag = it }
        }

        override fun onBindViewHolder(holder: AppHolder, position: Int) {
            val app = matches[position]
            holder.icon.setImageDrawable(app.icon)
            holder.name.text = app.name
            holder.row.contentDescription = "Open " + app.name
            holder.row.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(43)
            )
        }

        override fun getItemCount(): Int = matches.size
    }
}
