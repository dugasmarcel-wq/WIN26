package rocks.gorjan.gokixp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class Win98WidgetSpec(
    val id: String,
    val title: String,
    val description: String
)

/**
 * Small, local WINSUNG/Win98-style widgets. These deliberately do not use WebView or network
 * access: the "old Internet" pieces are self-contained toys, and the animated pieces only run
 * while their page is actually visible.
 */
object Win98WidgetViews {

    val specs = listOf(
        Win98WidgetSpec(
            "desktop_pet",
            "Desktop Pet",
            "Animated pixel desk creature. Drag it around or tap to make it scurry."
        ),
        Win98WidgetSpec(
            "pipes",
            "3D Pipes Preview",
            "A moving Win98-style pipes preview. Tap the display to regenerate it."
        ),
        Win98WidgetSpec(
            "internet_zone",
            "WINSUNG Internet Zone",
            "A tiny 1998 homepage with a marquee, hit counter and links to Classic games."
        ),
        Win98WidgetSpec(
            "mines",
            "Mini Minesweeper",
            "Playable 6x6 Minesweeper directly on the home page. Long-press to flag."
        ),
        Win98WidgetSpec(
            "cyber_pet",
            "Cyber Pet",
            "Feed and play with a tiny animated virtual pet living inside a Win98 panel."
        ),
        Win98WidgetSpec(
            "solitaire_desk",
            "Solitaire Desk",
            "Deal quick card hands in the widget or jump into the full Classic Solitaire."
        ),
        Win98WidgetSpec(
            "doodle",
            "Doodle Pad",
            "A touch drawing pad with an old Paint-like white canvas and local Clear button."
        )
    )

    fun create(activity: MainActivity, id: String): View {
        return when (id) {
            "desktop_pet" -> DesktopPetView(activity)
            "pipes" -> PipesView(activity)
            "internet_zone" -> createInternetZone(activity)
            "mines" -> createMiniMines(activity)
            "cyber_pet" -> createCyberPet(activity)
            "solitaire_desk" -> createSolitaireDesk(activity)
            "doodle" -> createDoodlePad(activity)
            else -> TextView(activity).apply {
                text = "Widget unavailable"
                setTextColor(Color.BLACK)
                setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
            }
        }
    }

    private fun classicInset(context: Context, fill: Int = Color.rgb(211, 206, 199)): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(context, 1), Color.rgb(105, 105, 105))
        }

    private fun button(context: Context, label: String, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            textSize = 11f
            minHeight = dp(context, 28)
            setPadding(dp(context, 7), dp(context, 3), dp(context, 7), dp(context, 3))
            background = AppCompatResources.getDrawable(context, R.drawable.window_button_background)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun createInternetZone(activity: MainActivity): View {
        val context = activity
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 8))
            background = classicInset(context, Color.rgb(238, 235, 227))
        }

        root.addView(TextView(context).apply {
            text = "Address:  http://www.winsung.local/~home"
            setTextColor(Color.BLACK)
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            setPadding(dp(context, 5), dp(context, 4), dp(context, 5), dp(context, 4))
            background = classicInset(context, Color.WHITE)
        })

        root.addView(OldWebMarquee(context).apply {
            setPadding(0, dp(context, 7), 0, dp(context, 5))
        })

        root.addView(TextView(context).apply {
            text = "WINSUNG.NET PERSONAL HOME PAGE"
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(0, 0, 170))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })

        root.addView(TextView(context).apply {
            val hit = ((System.currentTimeMillis() / 60000L) % 900000L + 100000L).toInt()
            text = "WELCOME, WEB SURFER!    VISITOR #$hit"
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(130, 0, 110))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(context, 4), 0, dp(context, 7))
        })

        val games = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        games.addView(button(context, "Mines") {
            activity.launchSystemApp("system.minesweeper")
        }, LinearLayout.LayoutParams(0, dp(context, 30), 1f).apply {
            marginEnd = dp(context, 3)
        })
        games.addView(button(context, "Solitaire") {
            activity.launchSystemApp("system.solitare")
        }, LinearLayout.LayoutParams(0, dp(context, 30), 1f).apply {
            marginStart = dp(context, 2)
            marginEnd = dp(context, 2)
        })
        games.addView(button(context, "Pinball") {
            activity.launchSystemApp("system.pinball")
        }, LinearLayout.LayoutParams(0, dp(context, 30), 1f).apply {
            marginStart = dp(context, 3)
        })
        root.addView(games)

        root.addView(button(context, "Sign the Guestbook") {
            Win98Dialogs.showMessage(
                context = activity,
                title = "Guestbook",
                message = "Thanks for visiting WINSUNG.NET. Your extremely important guestbook signature has been saved nowhere, exactly like a suspicious 1998 homepage.",
                positiveText = "Radical"
            )
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 30)
        ).apply {
            topMargin = dp(context, 6)
        })

        root.addView(TextView(context).apply {
            text = "Best viewed at 800x600  |  No cookies  |  Under Construction"
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(70, 70, 70))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(context, 6), 0, 0)
        })
        return root
    }

    private class OldWebMarquee(context: Context) : TextView(context) {
        private val handler = Handler(Looper.getMainLooper())
        private val message =
            "*** WELCOME TO WINSUNG.NET *** FREE GAMES *** COOL LINKS *** PAGE UPDATED 9/23/98 ***    "
        private var offset = 0
        private val ticker = object : Runnable {
            override fun run() {
                if (isShown) {
                    offset = (offset + 1) % message.length
                    text = message.substring(offset) + message.substring(0, offset)
                }
                handler.postDelayed(this, if (isShown) 170L else 500L)
            }
        }

        init {
            setTextColor(Color.rgb(200, 0, 0))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            maxLines = 1
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }

        override fun onDetachedFromWindow() {
            handler.removeCallbacks(ticker)
            super.onDetachedFromWindow()
        }
    }

    private class DesktopPetView(context: Context) : View(context) {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var petX = 0f
        private var velocity = 1.8f * resources.displayMetrics.density
        private var dragging = false
        private var blink = false
        private val ticker = object : Runnable {
            override fun run() {
                if (isShown && width > 0) {
                    if (!dragging) {
                        petX += velocity
                        val minX = dp(context, 12).toFloat()
                        val maxX = max(minX, width - dp(context, 38).toFloat())
                        if (petX <= minX || petX >= maxX) {
                            velocity = -velocity
                            petX = petX.coerceIn(minX, maxX)
                        }
                    }
                    blink = !blink && Random.nextInt(9) == 0
                    invalidate()
                }
                handler.postDelayed(this, if (isShown) 55L else 400L)
            }
        }

        init {
            minimumHeight = dp(context, 132)
            petX = dp(context, 20).toFloat()
            setBackgroundColor(Color.rgb(0, 128, 128))
            isClickable = true
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    petX = event.x.coerceIn(0f, width.toFloat())
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        petX = event.x.coerceIn(0f, width.toFloat())
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    velocity = if (event.x < width / 2f) abs(velocity) else -abs(velocity)
                    performClick()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }

        override fun onDetachedFromWindow() {
            handler.removeCallbacks(ticker)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Tiny CRT workstation.
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(192, 192, 192)
            val crt = RectF(dp(context, 12).toFloat(), dp(context, 12).toFloat(),
                dp(context, 105).toFloat(), dp(context, 80).toFloat())
            canvas.drawRect(crt, paint)
            paint.color = Color.rgb(40, 60, 45)
            canvas.drawRect(
                dp(context, 21).toFloat(), dp(context, 20).toFloat(),
                dp(context, 96).toFloat(), dp(context, 65).toFloat(), paint
            )
            paint.color = Color.rgb(80, 220, 110)
            paint.textSize = dp(context, 9).toFloat()
            paint.typeface = Typeface.MONOSPACE
            canvas.drawText("C:\\PET> RUN", dp(context, 25).toFloat(), dp(context, 43).toFloat(), paint)
            paint.color = Color.rgb(128, 128, 128)
            canvas.drawRect(
                dp(context, 44).toFloat(), dp(context, 80).toFloat(),
                dp(context, 73).toFloat(), dp(context, 88).toFloat(), paint
            )

            // Original blocky creature.
            val x = petX
            val y = height - dp(context, 38).toFloat()
            val u = dp(context, 4).toFloat()
            paint.color = Color.rgb(238, 238, 220)
            canvas.drawRect(x, y, x + u * 7, y + u * 5, paint)
            canvas.drawRect(x + u, y - u * 2, x + u * 3, y, paint)
            canvas.drawRect(x + u * 5, y - u * 2, x + u * 7, y, paint)
            paint.color = Color.BLACK
            if (!blink) {
                canvas.drawRect(x + u * 2, y + u, x + u * 3, y + u * 2, paint)
                canvas.drawRect(x + u * 5, y + u, x + u * 6, y + u * 2, paint)
            }
            canvas.drawRect(x + u * 3, y + u * 3, x + u * 5, y + u * 4, paint)
            paint.color = Color.rgb(255, 255, 255)
            paint.textSize = dp(context, 9).toFloat()
            canvas.drawText("drag me", x, y - dp(context, 7), paint)
        }
    }

    private class PipesView(context: Context) : View(context) {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val joints = mutableListOf<Pair<Int, Int>>()
        private var dir = 0
        private var cols = 1
        private var rows = 1
        private val palette = intArrayOf(
            Color.rgb(0, 210, 210),
            Color.rgb(220, 80, 220),
            Color.rgb(230, 210, 40),
            Color.rgb(70, 190, 80)
        )
        private val ticker = object : Runnable {
            override fun run() {
                if (isShown && width > 0 && height > 0) {
                    step()
                    invalidate()
                }
                handler.postDelayed(this, if (isShown) 115L else 450L)
            }
        }

        init {
            minimumHeight = dp(context, 150)
            setBackgroundColor(Color.BLACK)
            isClickable = true
            setOnClickListener { resetPipe() }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val cell = max(8, dp(context, 12))
            cols = max(3, w / cell)
            rows = max(3, h / cell)
            resetPipe()
        }

        private fun resetPipe() {
            joints.clear()
            joints.add(cols / 2 to rows / 2)
            dir = Random.nextInt(4)
            invalidate()
        }

        private fun step() {
            if (joints.isEmpty()) resetPipe()
            if (Random.nextInt(5) == 0) {
                dir = (dir + if (Random.nextBoolean()) 1 else 3) % 4
            }
            val head = joints.last()
            var nx = head.first
            var ny = head.second
            when (dir) {
                0 -> nx++
                1 -> ny++
                2 -> nx--
                3 -> ny--
            }
            if (nx !in 1 until cols - 1 || ny !in 1 until rows - 1) {
                dir = (dir + 2) % 4
                return
            }
            joints.add(nx to ny)
            while (joints.size > 70) joints.removeAt(0)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }

        override fun onDetachedFromWindow() {
            handler.removeCallbacks(ticker)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (joints.size < 2) return
            val sx = width.toFloat() / cols.toFloat()
            val sy = height.toFloat() / rows.toFloat()
            paint.strokeWidth = min(sx, sy) * 0.45f

            for (i in 1 until joints.size) {
                val a = joints[i - 1]
                val b = joints[i]
                paint.color = palette[(i / 8) % palette.size]
                canvas.drawLine(
                    (a.first + 0.5f) * sx,
                    (a.second + 0.5f) * sy,
                    (b.first + 0.5f) * sx,
                    (b.second + 0.5f) * sy,
                    paint
                )
            }
        }
    }

    private fun createMiniMines(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 8))
            background = classicInset(context)
        }
        val status = TextView(context).apply {
            text = "MINES: 06    READY"
            setTextColor(Color.rgb(128, 0, 0))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 24)
        ))

        val board = GridLayout(context).apply {
            columnCount = 6
            rowCount = 6
        }
        root.addView(board)

        val mines = BooleanArray(36)
        val state = IntArray(36) // 0 covered, 1 open, 2 flag
        val cells = arrayOfNulls<TextView>(36)
        var gameOver = false

        fun neighbours(index: Int): List<Int> {
            val r = index / 6
            val c = index % 6
            val out = mutableListOf<Int>()
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val rr = r + dr
                val cc = c + dc
                if (rr in 0..5 && cc in 0..5) out.add(rr * 6 + cc)
            }
            return out
        }

        fun adjacent(index: Int): Int = neighbours(index).count { mines[it] }

        fun paintCell(index: Int) {
            val cell = cells[index] ?: return
            when (state[index]) {
                0 -> {
                    cell.text = ""
                    cell.setTextColor(Color.BLACK)
                }
                2 -> {
                    cell.text = "F"
                    cell.setTextColor(Color.rgb(180, 0, 0))
                }
                else -> {
                    if (mines[index]) {
                        cell.text = "*"
                        cell.setTextColor(Color.rgb(180, 0, 0))
                    } else {
                        val n = adjacent(index)
                        cell.text = if (n == 0) "" else n.toString()
                        cell.setTextColor(if (n == 1) Color.BLUE else Color.rgb(0, 110, 0))
                    }
                }
            }
        }

        fun reveal(start: Int) {
            if (gameOver || state[start] != 0) return
            val queue = java.util.ArrayDeque<Int>()
            queue.add(start)
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst()
                if (state[i] != 0) continue
                state[i] = 1
                paintCell(i)
                if (mines[i]) {
                    gameOver = true
                    status.text = "BOOM!  TAP RESET"
                    for (j in 0 until 36) {
                        if (mines[j]) {
                            state[j] = 1
                            paintCell(j)
                        }
                    }
                    break
                }
                if (adjacent(i) == 0) {
                    neighbours(i).filter { state[it] == 0 }.forEach { queue.add(it) }
                }
            }
            if (!gameOver && (0 until 36).count { state[it] == 1 && !mines[it] } == 30) {
                gameOver = true
                status.text = "CLEARED!  NICE."
            }
        }

        fun reset() {
            mines.fill(false)
            state.fill(0)
            (0 until 36).shuffled().take(6).forEach { mines[it] = true }
            gameOver = false
            status.text = "MINES: 06    READY"
            for (i in 0 until 36) paintCell(i)
        }

        for (i in 0 until 36) {
            val cell = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.BLACK)
                background = AppCompatResources.getDrawable(context, R.drawable.window_button_background)
                isClickable = true
                isFocusable = true
                setOnClickListener { reveal(i) }
                setOnLongClickListener {
                    if (!gameOver && state[i] != 1) {
                        state[i] = if (state[i] == 2) 0 else 2
                        paintCell(i)
                    }
                    true
                }
            }
            cells[i] = cell
            board.addView(cell, GridLayout.LayoutParams().apply {
                width = dp(context, 31)
                height = dp(context, 31)
                setMargins(dp(context, 1), dp(context, 1), dp(context, 1), dp(context, 1))
            })
        }
        root.addView(button(context, "Reset") { reset() }, LinearLayout.LayoutParams(
            dp(context, 90),
            dp(context, 30)
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(context, 6)
        })
        reset()
        return root
    }

    private fun createCyberPet(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 8))
            background = classicInset(context, Color.rgb(228, 224, 214))
        }
        val face = CyberPetFace(context)
        root.addView(face, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 92)
        ))
        val status = TextView(context).apply {
            text = "HUNGER 3/5    FUN 3/5"
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }
        root.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 24)
        ))

        var hunger = 3
        var funLevel = 3
        fun update() {
            status.text = "HUNGER $hunger/5    FUN $funLevel/5"
            face.mood = (funLevel + hunger) / 2
        }

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        buttons.addView(button(context, "Feed") {
            hunger = min(5, hunger + 1)
            face.bounce()
            update()
        }, LinearLayout.LayoutParams(0, dp(context, 30), 1f).apply { marginEnd = dp(context, 3) })
        buttons.addView(button(context, "Play") {
            funLevel = min(5, funLevel + 1)
            face.bounce()
            update()
        }, LinearLayout.LayoutParams(0, dp(context, 30), 1f).apply { marginStart = dp(context, 3) })
        root.addView(buttons)
        update()
        return root
    }

    private class CyberPetFace(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val handler = Handler(Looper.getMainLooper())
        var mood: Int = 3
        private var bob = 0f
        private var bobDirection = 1f
        private var blink = false
        private val ticker = object : Runnable {
            override fun run() {
                if (isShown) {
                    bob += bobDirection * resources.displayMetrics.density
                    if (abs(bob) > dp(context, 3)) bobDirection = -bobDirection
                    blink = Random.nextInt(8) == 0
                    invalidate()
                }
                handler.postDelayed(this, if (isShown) 230L else 600L)
            }
        }

        init {
            setBackgroundColor(Color.rgb(30, 45, 35))
        }

        fun bounce() {
            bob = -dp(context, 4).toFloat()
            bobDirection = 1f
            invalidate()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }

        override fun onDetachedFromWindow() {
            handler.removeCallbacks(ticker)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f + bob
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(155, 235, 165)
            canvas.drawOval(RectF(cx - dp(context, 38), cy - dp(context, 28),
                cx + dp(context, 38), cy + dp(context, 28)), paint)
            paint.color = Color.rgb(20, 45, 25)
            if (!blink) {
                canvas.drawRect(cx - dp(context, 18), cy - dp(context, 8),
                    cx - dp(context, 12), cy - dp(context, 2), paint)
                canvas.drawRect(cx + dp(context, 12), cy - dp(context, 8),
                    cx + dp(context, 18), cy - dp(context, 2), paint)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(context, 2).toFloat()
            if (mood >= 3) {
                canvas.drawArc(RectF(cx - dp(context, 16), cy,
                    cx + dp(context, 16), cy + dp(context, 18)), 10f, 160f, false, paint)
            } else {
                canvas.drawArc(RectF(cx - dp(context, 16), cy + dp(context, 9),
                    cx + dp(context, 16), cy + dp(context, 23)), 190f, 160f, false, paint)
            }
        }
    }

    private fun createSolitaireDesk(activity: MainActivity): View {
        val context = activity
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 8))
            setBackgroundColor(Color.rgb(0, 116, 0))
        }
        val cards = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val cardViews = mutableListOf<TextView>()
        repeat(5) {
            val card = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    setStroke(dp(context, 1), Color.BLACK)
                }
            }
            cardViews.add(card)
            cards.addView(card, LinearLayout.LayoutParams(dp(context, 42), dp(context, 58)).apply {
                marginStart = dp(context, 2)
                marginEnd = dp(context, 2)
            })
        }
        root.addView(cards)

        val ranks = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
        val suits = listOf("S", "H", "D", "C")
        fun deal() {
            val deck = mutableListOf<String>()
            suits.forEach { suit -> ranks.forEach { rank -> deck.add("$rank$suit") } }
            deck.shuffle()
            cardViews.forEachIndexed { i, v ->
                val value = deck[i]
                v.text = value
                v.setTextColor(
                    if (value.endsWith("H") || value.endsWith("D")) Color.rgb(180, 0, 0)
                    else Color.BLACK
                )
            }
        }
        deal()

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(context, 7), 0, 0)
        }
        controls.addView(button(context, "Deal") { deal() },
            LinearLayout.LayoutParams(0, dp(context, 30), 1f).apply { marginEnd = dp(context, 3) })
        controls.addView(button(context, "Full Solitaire") {
            activity.launchSystemApp("system.solitare")
        }, LinearLayout.LayoutParams(0, dp(context, 30), 1f).apply { marginStart = dp(context, 3) })
        root.addView(controls)
        return root
    }

    private fun createDoodlePad(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 7))
            background = classicInset(context)
        }
        val canvas = DoodleView(context)
        root.addView(canvas, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 165)
        ))
        root.addView(button(context, "Clear") { canvas.clear() }, LinearLayout.LayoutParams(
            dp(context, 88),
            dp(context, 30)
        ).apply {
            gravity = Gravity.END
            topMargin = dp(context, 5)
        })
        return root
    }

    private class DoodleView(context: Context) : View(context) {
        private val path = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = dp(context, 2).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        init {
            setBackgroundColor(Color.WHITE)
            isClickable = true
        }

        fun clear() {
            path.reset()
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    path.moveTo(event.x, event.y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    path.lineTo(event.x, event.y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    performClick()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawPath(path, paint)
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
