package rocks.gorjan.gokixp

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/**
 * A keyboard-aware, full-height Classic app finder. Replaces the cramped Start-menu
 * search on swipe-up only; XP/Vista search and the regular Start menu are unchanged.
 * Filtering is entirely local. Internet Explorer only opens on explicit user action.
 */
class Win98AppSearchDialog(
    context: Context,
    private val onLaunchApp: (AppInfo) -> Unit,
    private val onWebSearch: (String) -> Unit
) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    private fun dp(value: Int) =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private val ink = Color.rgb(34, 40, 49)
    private val muted = Color.rgb(108, 116, 129)
    private val outline = Color.rgb(180, 185, 192)
    private val entries = ArrayList<AppInfo>()
    private val adapter = SearchAdapter()
    private val resultsLabel = TextView(context)
    private val emptyLabel = TextView(context)
    private val input = EditText(context)
    private val clear = TextView(context)
    private val webRow = TextView(context)
    private var allApps: List<AppInfo> = emptyList()
    private var pinned: Set<String> = emptySet()
    private var loaded = false

    private fun panel(fill: Int, stroke: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(context).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    init {
        setCancelable(true)
        setCanceledOnTouchOutside(false)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(31, 45, 70), Color.rgb(32, 36, 44))
            )
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("WINSUNG  /  Search", 16f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(text("X", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Close search"
            background = panel(Color.rgb(66, 73, 85), Color.rgb(115, 126, 147), 10)
            isClickable = true
            isFocusable = true
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(dp(44), dp(38)))
        root.addView(header)

        val searchBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = panel(Color.WHITE, Color.rgb(171, 186, 208), 15)
            setPadding(dp(10), dp(2), dp(6), dp(2))
        }
        searchBar.addView(text("⌕", 25f, Color.rgb(24, 83, 146)).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(32), dp(48)))
        input.apply {
            hint = "Search apps"
            setHintTextColor(Color.rgb(125, 132, 142))
            setTextColor(ink)
            textSize = 17f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = null
            setPadding(dp(4), 0, 0, 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filterApps()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                    if (entries.isNotEmpty()) launch(entries.first())
                    else if (text.toString().isNotBlank()) webSearch()
                    true
                } else false
            }
        }
        searchBar.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
        clear.apply {
            text = "X"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(76, 84, 96))
            contentDescription = "Clear search"
            isClickable = true
            isFocusable = true
            setOnClickListener { input.setText("") }
        }
        searchBar.addView(clear, LinearLayout.LayoutParams(dp(36), dp(42)))
        root.addView(searchBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        ).apply { bottomMargin = dp(13) })

        val resultsPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = panel(Color.rgb(247, 247, 247), outline, 14)
            setPadding(dp(8), dp(11), dp(8), dp(6))
        }
        resultsLabel.apply {
            textSize = 14f
            setTextColor(Color.rgb(36, 54, 79))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(9), 0, dp(9), dp(7))
        }
        resultsPanel.addView(resultsLabel)
        emptyLabel.apply {
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(28), dp(16), dp(28))
        }
        resultsPanel.addView(emptyLabel)

        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@Win98AppSearchDialog.adapter
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            setPadding(0, 0, 0, dp(5))
            itemAnimator = null // Fast re-filtering without rows jumping or flickering.
        }
        resultsPanel.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        webRow.apply {
            textSize = 13f
            setTextColor(Color.rgb(0, 60, 130))
            setPadding(dp(12), dp(5), dp(10), dp(5))
            gravity = Gravity.CENTER_VERTICAL
            background = panel(Color.rgb(228, 235, 246), Color.rgb(170, 187, 214), 9)
            isClickable = true
            isFocusable = true
            setOnClickListener { webSearch() }
        }
        resultsPanel.addView(webRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
        ).apply { topMargin = dp(5) })
        root.addView(resultsPanel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        root.addView(text("Installed apps only  •  No search history", 10f,
            Color.rgb(179, 191, 208)).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(27)
        ))

        setContentView(root)
        filterApps()
    }

    override fun onStart() {
        super.onStart()
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.40f)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.TOP)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        input.requestFocus()
        input.post {
            if (isShowing) {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    fun setApps(apps: List<AppInfo>, pinnedPackages: Set<String>) {
        allApps = apps
        pinned = pinnedPackages
        loaded = true
        filterApps()
    }

    private fun filterApps() {
        val q = input.text.toString().trim().lowercase(Locale.getDefault())
        val matched = if (q.isEmpty()) allApps else allApps.filter {
            it.name.lowercase(Locale.getDefault()).contains(q)
        }
        val ranked = matched.sortedWith(
            compareBy<AppInfo> {
                if (q.isNotEmpty() && it.name.lowercase(Locale.getDefault()).startsWith(q)) 0 else 1
            }.thenBy { if (it.packageName in pinned) 0 else 1 }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
        )
        entries.clear()
        entries.addAll(ranked)
        adapter.notifyDataSetChanged()
        resultsLabel.text = when {
            !loaded -> "Loading apps..."
            q.isEmpty() -> "All apps  ·  \${entries.size}"
            else -> "App results  ·  \${entries.size}"
        }
        emptyLabel.text = when {
            !loaded -> "Looking up installed apps"
            entries.isEmpty() -> "No matching installed apps"
            else -> ""
        }
        emptyLabel.visibility = if (!loaded || entries.isEmpty()) View.VISIBLE else View.GONE
        clear.visibility = if (input.text.isNotEmpty()) View.VISIBLE else View.INVISIBLE
        webRow.text = if (q.isEmpty()) "Search the web in Internet Explorer" else
            "Search web for  “\${input.text.toString().trim().take(40)}”"
        webRow.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun launch(app: AppInfo) {
        dismiss()
        onLaunchApp(app)
    }

    private fun webSearch() {
        val query = input.text.toString().trim()
        if (query.isEmpty()) return
        dismiss()
        onWebSearch(query)
    }

    private inner class SearchAdapter : RecyclerView.Adapter<SearchAdapter.AppHolder>() {
        inner class AppHolder(val row: LinearLayout, val image: ImageView, val name: TextView) :
            RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppHolder {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(4), dp(10), dp(4))
                minimumHeight = dp(61)
                background = panel(Color.WHITE, Color.rgb(225, 226, 230), 9)
                isClickable = true
                isFocusable = true
            }
            val icon = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginEnd = dp(13)
            })
            val name = text("", 15f, ink, true)
            name.maxLines = 2
            row.addView(name, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))
            row.setOnClickListener {
                val holder = row.tag as? AppHolder ?: return@setOnClickListener
                val position = holder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position in entries.indices) {
                    launch(entries[position])
                }
            }
            return AppHolder(row, icon, name).also { row.tag = it }
        }

        override fun onBindViewHolder(holder: AppHolder, position: Int) {
            val app = entries[position]
            holder.image.setImageDrawable(app.icon)
            holder.name.text = app.name
            holder.row.contentDescription = "Open \${app.name}"
            val lp = (holder.row.layoutParams as? RecyclerView.LayoutParams)
                ?: RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(65)
                )
            lp.bottomMargin = dp(4)
            holder.row.layoutParams = lp
        }

        override fun getItemCount() = entries.size
    }
}
