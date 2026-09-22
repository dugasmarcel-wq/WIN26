package rocks.gorjan.gokixp

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources

/**
 * One visual shell for launcher-owned modal windows.
 *
 * Android system-owned surfaces (package installer, role chooser, notification-access
 * settings, etc.) remain Android UI because another process owns those windows.
 */
object Win98Dialogs {

    class Handle internal constructor(
        private val dialog: Dialog,
        private val messageView: TextView? = null
    ) {
        val isShowing: Boolean
            get() = dialog.isShowing

        fun dismiss() {
            dialog.dismiss()
        }

        fun setMessage(message: String) {
            messageView?.text = message
        }
    }

    private data class Parts(
        val dialog: Dialog,
        val body: LinearLayout,
        val buttons: LinearLayout
    )

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun windowBackground(context: Context) = GradientDrawable().apply {
        setColor(Color.rgb(192, 192, 192))
        setStroke(dp(context, 2), Color.BLACK)
    }

    private fun titleBackground() = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.rgb(0, 0, 128), Color.rgb(16, 132, 208))
    )

    private fun makeButton(context: Context, label: String): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            minWidth = dp(context, 72)
            minHeight = dp(context, 30)
            setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4))
            background = AppCompatResources.getDrawable(
                context,
                R.drawable.window_button_background
            ) ?: GradientDrawable().apply {
                setColor(Color.rgb(192, 192, 192))
                setStroke(dp(context, 1), Color.rgb(64, 64, 64))
            }
            isClickable = true
            isFocusable = true
        }

    private fun create(
        context: Context,
        title: String,
        cancelable: Boolean = true
    ): Parts {
        val dialog = Dialog(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3))
            background = windowBackground(context)
        }

        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 6), dp(context, 3), dp(context, 3), dp(context, 3))
            background = titleBackground()
        }

        titleBar.addView(
            TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, dp(context, 28), 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        )

        val close = makeButton(context, "X").apply {
            textSize = 13f
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            contentDescription = "Close"
            setOnClickListener { dialog.dismiss() }
        }
        titleBar.addView(
            close,
            LinearLayout.LayoutParams(dp(context, 26), dp(context, 24))
        )
        root.addView(
            titleBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 30)
            )
        )

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(context, 12),
                dp(context, 12),
                dp(context, 12),
                dp(context, 10)
            )
        }
        root.addView(
            body,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(
                dp(context, 10),
                dp(context, 4),
                dp(context, 10),
                dp(context, 10)
            )
        }
        root.addView(
            buttons,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        dialog.setContentView(root)
        dialog.setCancelable(cancelable)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.48f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        dialog.setOnShowListener {
            val width = minOf(
                (context.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                dp(context, 430)
            )
            dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        return Parts(dialog, body, buttons)
    }

    private fun addAction(
        context: Context,
        buttons: LinearLayout,
        label: String,
        marginEnd: Boolean = true,
        action: () -> Unit
    ) {
        val button = makeButton(context, label)
        button.setOnClickListener { action() }
        buttons.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(context, 32)
            ).apply {
                if (marginEnd) this.marginEnd = dp(context, 7)
            }
        )
    }

    fun showMessage(
        context: Context,
        title: String,
        message: String,
        positiveText: String = "OK",
        negativeText: String? = null,
        neutralText: String? = null,
        cancelable: Boolean = true,
        onPositive: (() -> Unit)? = null,
        onNegative: (() -> Unit)? = null,
        onNeutral: (() -> Unit)? = null
    ): Handle {
        val parts = create(context, title, cancelable)
        val messageView = TextView(context).apply {
            text = message
            setTextColor(Color.BLACK)
            textSize = 14f
            setLineSpacing(0f, 1.08f)
        }
        parts.body.addView(
            messageView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        neutralText?.let { label ->
            addAction(context, parts.buttons, label) {
                parts.dialog.dismiss()
                onNeutral?.invoke()
            }
        }
        negativeText?.let { label ->
            addAction(context, parts.buttons, label) {
                parts.dialog.dismiss()
                onNegative?.invoke()
            }
        }
        addAction(context, parts.buttons, positiveText, marginEnd = false) {
            parts.dialog.dismiss()
            onPositive?.invoke()
        }

        parts.dialog.show()
        return Handle(parts.dialog, messageView)
    }

    fun showProgress(
        context: Context,
        title: String,
        message: String
    ): Handle {
        val parts = create(context, title, cancelable = true)
        val messageView = TextView(context).apply {
            text = message
            setTextColor(Color.BLACK)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(context, 4), 0, dp(context, 6))
        }
        parts.body.addView(
            messageView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        parts.buttons.visibility = View.GONE
        parts.dialog.show()
        return Handle(parts.dialog, messageView)
    }

    fun showList(
        context: Context,
        title: String,
        items: Array<String>,
        negativeText: String? = "Cancel",
        neutralText: String? = null,
        onNeutral: (() -> Unit)? = null,
        onItem: (Int) -> Unit
    ): Handle {
        val parts = create(context, title)
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.rgb(211, 206, 199))
                setStroke(dp(context, 1), Color.rgb(128, 128, 128))
            }
        }

        items.forEachIndexed { index, label ->
            val row = TextView(context).apply {
                text = label
                setTextColor(Color.BLACK)
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dp(context, 10),
                    dp(context, 8),
                    dp(context, 10),
                    dp(context, 8)
                )
                minHeight = dp(context, 38)
                background = if (index % 2 == 0) {
                    GradientDrawable().apply { setColor(Color.rgb(224, 220, 214)) }
                } else {
                    GradientDrawable().apply { setColor(Color.rgb(211, 206, 199)) }
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    parts.dialog.dismiss()
                    onItem(index)
                }
            }
            list.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = false
            addView(
                list,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        parts.body.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (items.size > 7) dp(context, 330) else ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        neutralText?.let { label ->
            addAction(context, parts.buttons, label) {
                parts.dialog.dismiss()
                onNeutral?.invoke()
            }
        }
        negativeText?.let { label ->
            addAction(context, parts.buttons, label, marginEnd = false) {
                parts.dialog.dismiss()
            }
        }

        parts.dialog.show()
        return Handle(parts.dialog)
    }

    fun showCustom(
        context: Context,
        title: String,
        content: View,
        positiveText: String,
        negativeText: String? = "Cancel",
        neutralText: String? = null,
        onPositive: (() -> Unit)? = null,
        onNegative: (() -> Unit)? = null,
        onNeutral: (() -> Unit)? = null
    ): Handle {
        val parts = create(context, title)
        (content.parent as? ViewGroup)?.removeView(content)
        parts.body.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        neutralText?.let { label ->
            addAction(context, parts.buttons, label) {
                parts.dialog.dismiss()
                onNeutral?.invoke()
            }
        }
        negativeText?.let { label ->
            addAction(context, parts.buttons, label) {
                parts.dialog.dismiss()
                onNegative?.invoke()
            }
        }
        addAction(context, parts.buttons, positiveText, marginEnd = false) {
            parts.dialog.dismiss()
            onPositive?.invoke()
        }

        parts.dialog.show()
        return Handle(parts.dialog)
    }
}
