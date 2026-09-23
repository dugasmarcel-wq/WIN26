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
            "P1 Classic Pet",
            "Persistent 1996-style virtual pet: hunger, happiness, discipline, illness, sleep, care mistakes, game and branching growth."
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

    private fun createCyberPet(context: Context): View = P1ClassicPetView(context)

    /**
     * A native first-generation virtual-pet simulation. The timing/care model follows the
     * documented 1996-97 P1 rules closely, while all pixels below are original WIN26 artwork.
     * State is timestamp based, so the pet keeps aging while this page or the launcher is closed.
     */
    private class P1ClassicPetView(context: Context) : View(context) {

        private enum class Character(
            val label: String,
            val minWeight: Int,
            val hungerMinutes: Int,
            val happyMinutes: Int,
            val illnessMinutes: Int,
            val evolutionMinutes: Int,
            val wakeHour: Int,
            val sleepHour: Int,
            val medicineDoses: Int,
            val disciplineEvery: Int
        ) {
            EGG("EGG", 0, 0, 0, 0, 5, 0, 24, 0, 0),
            BABY("BABY", 5, 3, 4, 45, 65, 0, 24, 2, 0),
            CHILD("MARUTCHI", 10, 50, 60, 990, 1380, 9, 20, 2, 6),
            TEEN_GOOD("TAMATCHI", 20, 75, 85, 1656, 2220, 9, 21, 2, 6),
            TEEN_BAD("TAMATCHI", 20, 75, 85, 1656, 2220, 9, 21, 2, 6),
            KUCHI_GOOD("KUCHITAMATCHI", 20, 75, 85, 660, 1380, 9, 21, 2, 6),
            KUCHI_BAD("KUCHITAMATCHI", 20, 75, 85, 660, 1380, 9, 21, 2, 6),
            MAMETCHI("MAMETCHI", 30, 81, 91, 3900, 0, 9, 22, 1, 0),
            GINJI("GINJIROTCHI", 30, 81, 91, 2808, 0, 9, 22, 1, 7),
            MASK("MASKUTCHI", 30, 55, 65, 2592, 5760, 11, 23, 1, 7),
            KUCHIPATCHI("KUCHIPATCHI", 20, 60, 70, 1170, 0, 9, 22, 2, 0),
            NYOROTCHI("NYOROTCHI", 10, 60, 70, 360, 0, 9, 22, 3, 7),
            TARAKOTCHI("TARAKOTCHI", 20, 45, 50, 660, 0, 10, 22, 2, 7),
            BILL("BILL", 30, 81, 91, 3900, 0, 9, 22, 1, 0),
            DEAD("DEAD", 0, 0, 0, 0, 0, 0, 24, 0, 0)
        }

        private enum class ScreenMode {
            MAIN, FEED, LIGHT, STATUS, GAME, MESSAGE, DEAD
        }

        private val prefs = context.getSharedPreferences(
            MainActivity.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val lcdPaint = Paint().apply { isAntiAlias = false }

        private var character = Character.EGG
        private var stageStartedAt = 0L
        private var lastProcessedAt = 0L
        private var lastHungryAt = 0L
        private var lastHappyAt = 0L
        private var nextPoopAt = 0L
        private var nextIllnessAt = 0L

        private var hungry = 0
        private var happy = 0
        private var discipline = 0
        private var weight = 0
        private var poopCount = 0
        private var sick = false
        private var medicineGiven = 0
        private var lightsOff = false

        private var careMistakes = 0
        private var lifetimeCareMistakes = 0
        private var disciplineCountdown = 0
        private var misbehaving = false

        private var hungerZeroSince = 0L
        private var happyZeroSince = 0L
        private var sleepCallSince = 0L
        private var hungerMistakeCounted = false
        private var happyMistakeCounted = false
        private var sleepMistakeCounted = false
        private var sickSince = 0L

        private var selectedIcon = 0
        private var screenMode = ScreenMode.MAIN
        private var submenuChoice = 0
        private var statusPage = 0
        private var gameRound = 0
        private var gameWins = 0
        private var gameLastResult = ""
        private var message = ""
        private var messageUntil = 0L
        private var lastAAt = 0L
        private var animationFrame = false

        private var lastUiTick = 0L

        private val ticker = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                if (now - lastUiTick >= 15_000L) {
                    processElapsedTime(now)
                    lastUiTick = now
                }
                animationFrame = !animationFrame
                invalidate()
                handler.postDelayed(this, if (isShown) 520L else 5_000L)
            }
        }

        init {
            minimumHeight = dp(context, 248)
            isClickable = true
            isFocusable = true
            loadState()
            processElapsedTime(System.currentTimeMillis())
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }

        override fun onDetachedFromWindow() {
            saveState()
            handler.removeCallbacks(ticker)
            super.onDetachedFromWindow()
        }

        private fun loadState() {
            val now = System.currentTimeMillis()
            val savedChar = prefs.getString("p1_pet_char", null)
            if (savedChar == null) {
                resetPet(now)
                return
            }

            character = try {
                Character.valueOf(savedChar)
            } catch (_: Exception) {
                Character.EGG
            }
            stageStartedAt = prefs.getLong("p1_pet_stage_started", now)
            lastProcessedAt = prefs.getLong("p1_pet_last_processed", now)
            lastHungryAt = prefs.getLong("p1_pet_last_hungry", stageStartedAt)
            lastHappyAt = prefs.getLong("p1_pet_last_happy", stageStartedAt)
            nextPoopAt = prefs.getLong("p1_pet_next_poop", stageStartedAt + poopIntervalMs())
            nextIllnessAt = prefs.getLong(
                "p1_pet_next_illness",
                if (character.illnessMinutes > 0) {
                    stageStartedAt + minutes(character.illnessMinutes)
                } else Long.MAX_VALUE
            )

            hungry = prefs.getInt("p1_pet_hungry", if (character == Character.BABY) 0 else 4)
            happy = prefs.getInt("p1_pet_happy", if (character == Character.BABY) 0 else 4)
            discipline = prefs.getInt("p1_pet_discipline", 0).coerceIn(0, 4)
            weight = prefs.getInt("p1_pet_weight", character.minWeight)
            poopCount = prefs.getInt("p1_pet_poop", 0).coerceIn(0, 4)
            sick = prefs.getBoolean("p1_pet_sick", false)
            medicineGiven = prefs.getInt("p1_pet_medicine", 0)
            lightsOff = prefs.getBoolean("p1_pet_lights_off", false)
            careMistakes = prefs.getInt("p1_pet_care_mistakes", 0)
            lifetimeCareMistakes = prefs.getInt("p1_pet_lifetime_mistakes", 0)
            disciplineCountdown = prefs.getInt("p1_pet_discipline_countdown", 0)
            misbehaving = prefs.getBoolean("p1_pet_misbehaving", false)

            hungerZeroSince = prefs.getLong("p1_pet_hunger_zero_since", 0L)
            happyZeroSince = prefs.getLong("p1_pet_happy_zero_since", 0L)
            sleepCallSince = prefs.getLong("p1_pet_sleep_call_since", 0L)
            hungerMistakeCounted = prefs.getBoolean("p1_pet_hunger_mistake_counted", false)
            happyMistakeCounted = prefs.getBoolean("p1_pet_happy_mistake_counted", false)
            sleepMistakeCounted = prefs.getBoolean("p1_pet_sleep_mistake_counted", false)
            sickSince = prefs.getLong("p1_pet_sick_since", 0L)

            if (lastProcessedAt > now) lastProcessedAt = now
        }

        private fun saveState() {
            prefs.edit()
                .putString("p1_pet_char", character.name)
                .putLong("p1_pet_stage_started", stageStartedAt)
                .putLong("p1_pet_last_processed", lastProcessedAt)
                .putLong("p1_pet_last_hungry", lastHungryAt)
                .putLong("p1_pet_last_happy", lastHappyAt)
                .putLong("p1_pet_next_poop", nextPoopAt)
                .putLong("p1_pet_next_illness", nextIllnessAt)
                .putInt("p1_pet_hungry", hungry)
                .putInt("p1_pet_happy", happy)
                .putInt("p1_pet_discipline", discipline)
                .putInt("p1_pet_weight", weight)
                .putInt("p1_pet_poop", poopCount)
                .putBoolean("p1_pet_sick", sick)
                .putInt("p1_pet_medicine", medicineGiven)
                .putBoolean("p1_pet_lights_off", lightsOff)
                .putInt("p1_pet_care_mistakes", careMistakes)
                .putInt("p1_pet_lifetime_mistakes", lifetimeCareMistakes)
                .putInt("p1_pet_discipline_countdown", disciplineCountdown)
                .putBoolean("p1_pet_misbehaving", misbehaving)
                .putLong("p1_pet_hunger_zero_since", hungerZeroSince)
                .putLong("p1_pet_happy_zero_since", happyZeroSince)
                .putLong("p1_pet_sleep_call_since", sleepCallSince)
                .putBoolean("p1_pet_hunger_mistake_counted", hungerMistakeCounted)
                .putBoolean("p1_pet_happy_mistake_counted", happyMistakeCounted)
                .putBoolean("p1_pet_sleep_mistake_counted", sleepMistakeCounted)
                .putLong("p1_pet_sick_since", sickSince)
                .apply()
        }

        private fun resetPet(now: Long) {
            character = Character.EGG
            stageStartedAt = now
            lastProcessedAt = now
            lastHungryAt = now
            lastHappyAt = now
            nextPoopAt = Long.MAX_VALUE
            nextIllnessAt = Long.MAX_VALUE
            hungry = 0
            happy = 0
            discipline = 0
            weight = 0
            poopCount = 0
            sick = false
            medicineGiven = 0
            lightsOff = false
            careMistakes = 0
            lifetimeCareMistakes = 0
            disciplineCountdown = 0
            misbehaving = false
            clearCareCalls()
            sickSince = 0L
            selectedIcon = 0
            screenMode = ScreenMode.MAIN
            saveState()
        }

        private fun clearCareCalls() {
            hungerZeroSince = 0L
            happyZeroSince = 0L
            sleepCallSince = 0L
            hungerMistakeCounted = false
            happyMistakeCounted = false
            sleepMistakeCounted = false
        }

        private fun minutes(value: Int): Long = value.toLong() * 60_000L

        private fun poopIntervalMs(): Long = when (character) {
            Character.EGG -> Long.MAX_VALUE
            Character.BABY -> minutes(30)
            Character.CHILD -> minutes(90)
            Character.TEEN_GOOD,
            Character.TEEN_BAD,
            Character.KUCHI_GOOD,
            Character.KUCHI_BAD -> minutes(120)
            else -> minutes(180)
        }

        private fun localHour(now: Long): Int {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = now
            return cal.get(java.util.Calendar.HOUR_OF_DAY)
        }

        private fun isAsleep(now: Long): Boolean {
            if (character == Character.EGG || character == Character.BABY || character == Character.DEAD) {
                return false
            }
            val hour = localHour(now)
            val wake = character.wakeHour
            val sleep = character.sleepHour
            return if (sleep > wake) {
                hour >= sleep || hour < wake
            } else {
                hour >= sleep && hour < wake
            }
        }

        private fun processElapsedTime(now: Long) {
            if (character == Character.DEAD) {
                lastProcessedAt = now
                saveState()
                return
            }

            var safety = 0
            while (character.evolutionMinutes > 0 &&
                now - stageStartedAt >= minutes(character.evolutionMinutes) &&
                safety++ < 8
            ) {
                evolve(stageStartedAt + minutes(character.evolutionMinutes))
            }

            if (character == Character.EGG) {
                lastProcessedAt = now
                saveState()
                return
            }

            if (character.hungerMinutes > 0) {
                val interval = minutes(character.hungerMinutes)
                var decrements = ((now - lastHungryAt) / interval).coerceAtLeast(0L).coerceAtMost(1000L)
                while (decrements-- > 0) {
                    lastHungryAt += interval
                    if (hungry > 0) hungry--
                    disciplineCountdown++
                    if (hungry == 0 && hungerZeroSince == 0L) {
                        hungerZeroSince = lastHungryAt
                        hungerMistakeCounted = false
                    }
                    maybeStartMisbehavior()
                }
            }

            if (character.happyMinutes > 0) {
                val interval = minutes(character.happyMinutes)
                var decrements = ((now - lastHappyAt) / interval).coerceAtLeast(0L).coerceAtMost(1000L)
                while (decrements-- > 0) {
                    lastHappyAt += interval
                    if (happy > 0) happy--
                    disciplineCountdown++
                    if (happy == 0 && happyZeroSince == 0L) {
                        happyZeroSince = lastHappyAt
                        happyMistakeCounted = false
                    }
                    maybeStartMisbehavior()
                }
            }

            if (nextPoopAt != Long.MAX_VALUE) {
                var poopSafety = 0
                while (now >= nextPoopAt && poopSafety++ < 100) {
                    poopCount = min(4, poopCount + 1)
                    nextPoopAt += poopIntervalMs()
                }
            }

            if (!sick && nextIllnessAt != Long.MAX_VALUE && now >= nextIllnessAt) {
                makeSick(nextIllnessAt)
                nextIllnessAt = Long.MAX_VALUE
            }
            if (!sick && poopCount >= 4) {
                makeSick(now)
            }

            updateCareCalls(now)

            if (hungry == 0 && hungerZeroSince > 0L && now - hungerZeroSince >= minutes(600)) {
                die(now)
            } else if (sick && sickSince > 0L && now - sickSince >= minutes(360)) {
                die(now)
            }

            lastProcessedAt = now
            saveState()
        }

        private fun maybeStartMisbehavior() {
            if (
                character.disciplineEvery > 0 &&
                discipline < 4 &&
                !misbehaving &&
                disciplineCountdown >= character.disciplineEvery &&
                hungry > 0 &&
                happy > 0
            ) {
                misbehaving = true
                disciplineCountdown = 0
            }
        }

        private fun updateCareCalls(now: Long) {
            if (hungry > 0) {
                hungerZeroSince = 0L
                hungerMistakeCounted = false
            } else if (hungerZeroSince == 0L) {
                hungerZeroSince = now
            }

            if (happy > 0) {
                happyZeroSince = 0L
                happyMistakeCounted = false
            } else if (happyZeroSince == 0L) {
                happyZeroSince = now
            }

            val asleep = isAsleep(now)
            if (asleep && !lightsOff) {
                if (sleepCallSince == 0L) sleepCallSince = now
            } else {
                sleepCallSince = 0L
                sleepMistakeCounted = false
                if (!asleep) lightsOff = false
            }

            if (
                hungerZeroSince > 0L &&
                !hungerMistakeCounted &&
                now - hungerZeroSince >= minutes(15)
            ) {
                registerCareMistake()
                hungerMistakeCounted = true
            }
            if (
                happyZeroSince > 0L &&
                !happyMistakeCounted &&
                now - happyZeroSince >= minutes(15)
            ) {
                registerCareMistake()
                happyMistakeCounted = true
            }
            if (
                sleepCallSince > 0L &&
                !sleepMistakeCounted &&
                now - sleepCallSince >= minutes(15)
            ) {
                registerCareMistake()
                sleepMistakeCounted = true
            }
        }

        private fun registerCareMistake() {
            careMistakes++
            lifetimeCareMistakes++
        }

        private fun makeSick(at: Long) {
            sick = true
            medicineGiven = 0
            sickSince = at
        }

        private fun die(at: Long) {
            character = Character.DEAD
            stageStartedAt = at
            screenMode = ScreenMode.DEAD
            sick = false
            misbehaving = false
            saveState()
        }

        private fun evolve(at: Long) {
            val previous = character
            character = when (previous) {
                Character.EGG -> Character.BABY
                Character.BABY -> Character.CHILD
                Character.CHILD -> {
                    val goodDiscipline = discipline >= 3
                    if (careMistakes <= 1) {
                        if (goodDiscipline) Character.TEEN_GOOD else Character.TEEN_BAD
                    } else {
                        if (goodDiscipline) Character.KUCHI_GOOD else Character.KUCHI_BAD
                    }
                }
                Character.TEEN_GOOD -> when {
                    careMistakes <= 2 && discipline >= 4 -> Character.MAMETCHI
                    careMistakes <= 2 && discipline == 3 -> Character.GINJI
                    careMistakes <= 2 -> Character.MASK
                    discipline >= 4 -> Character.KUCHIPATCHI
                    discipline == 3 -> Character.NYOROTCHI
                    else -> Character.TARAKOTCHI
                }
                Character.TEEN_BAD -> when {
                    careMistakes <= 2 && discipline >= 4 -> Character.GINJI
                    careMistakes <= 2 -> Character.MASK
                    discipline >= 4 -> Character.NYOROTCHI
                    else -> Character.TARAKOTCHI
                }
                Character.KUCHI_GOOD -> when {
                    discipline >= 4 -> Character.KUCHIPATCHI
                    discipline == 3 -> Character.NYOROTCHI
                    else -> Character.TARAKOTCHI
                }
                Character.KUCHI_BAD -> when {
                    discipline >= 4 -> Character.NYOROTCHI
                    else -> Character.TARAKOTCHI
                }
                Character.MASK -> {
                    if (discipline == 0) Character.BILL else Character.MASK
                }
                else -> previous
            }

            if (character == previous && previous == Character.MASK) {
                // Maskutchi only has the hidden evolution when raised with no discipline.
                stageStartedAt = at
                return
            }

            stageStartedAt = at
            careMistakes = 0
            disciplineCountdown = 0
            misbehaving = false
            poopCount = 0
            sick = false
            sickSince = 0L
            medicineGiven = 0
            clearCareCalls()

            when (character) {
                Character.BABY -> {
                    hungry = 0
                    happy = 0
                    discipline = 0
                    weight = 5
                    nextPoopAt = at + minutes(15)
                }
                Character.CHILD -> {
                    hungry = 4
                    happy = 4
                    discipline = 0
                    weight = 10
                    nextPoopAt = at + poopIntervalMs()
                }
                else -> {
                    hungry = 4
                    happy = 4
                    weight = max(weight, character.minWeight)
                    nextPoopAt = at + poopIntervalMs()
                }
            }

            lastHungryAt = at
            lastHappyAt = at
            nextIllnessAt = if (character.illnessMinutes > 0) {
                at + minutes(character.illnessMinutes)
            } else {
                Long.MAX_VALUE
            }
            showMessage("EVOLVED: ${character.label}", 2600L)
        }

        private fun ageYears(now: Long): Int {
            if (character == Character.EGG) return 0
            val elapsedDays = ((now - prefs.getLong("p1_pet_birth_anchor", stageStartedAt)) /
                86_400_000L).toInt().coerceAtLeast(0)
            return if (character == Character.BABY) 0 else max(1, elapsedDays + 1)
        }

        private fun ensureBirthAnchor() {
            if (!prefs.contains("p1_pet_birth_anchor")) {
                prefs.edit().putLong("p1_pet_birth_anchor", stageStartedAt).apply()
            }
        }

        private fun feedMeal() {
            if (character == Character.EGG || character == Character.DEAD) return
            if (misbehaving) {
                showMessage("REFUSES FOOD", 1800L)
                return
            }
            if (hungry >= 4) {
                showMessage("FULL", 1400L)
                return
            }
            hungry++
            weight = min(99, weight + 1)
            if (hungry > 0) {
                hungerZeroSince = 0L
                hungerMistakeCounted = false
            }
            showMessage("MEAL +1 HUNGER", 1400L)
            saveState()
        }

        private fun feedSnack() {
            if (character == Character.EGG || character == Character.DEAD) return
            happy = min(4, happy + 1)
            weight = min(99, weight + 2)
            if (happy > 0) {
                happyZeroSince = 0L
                happyMistakeCounted = false
            }
            showMessage("SNACK +1 HAPPY", 1400L)
            saveState()
        }

        private fun cleanPoop() {
            poopCount = 0
            showMessage("CLEAN!", 1200L)
            saveState()
        }

        private fun giveMedicine() {
            if (!sick) {
                showMessage("NOT SICK", 1200L)
                return
            }
            medicineGiven++
            if (medicineGiven >= character.medicineDoses) {
                sick = false
                sickSince = 0L
                medicineGiven = 0
                showMessage("ALL BETTER", 1800L)
            } else {
                showMessage("MEDICINE ${medicineGiven}/${character.medicineDoses}", 1500L)
            }
            saveState()
        }

        private fun scold() {
            if (misbehaving) {
                discipline = min(4, discipline + 1)
                misbehaving = false
                showMessage("DISCIPLINE +25%", 1600L)
            } else {
                showMessage("NO EFFECT", 1200L)
            }
            saveState()
        }

        private fun startGame() {
            if (misbehaving) {
                showMessage("REFUSES GAME", 1500L)
                return
            }
            screenMode = ScreenMode.GAME
            gameRound = 0
            gameWins = 0
            gameLastResult = "A=LEFT  B=RIGHT"
        }

        private fun gameGuess(left: Boolean) {
            if (screenMode != ScreenMode.GAME) return
            val petLeft = Random.nextBoolean()
            if (left == petLeft) {
                gameWins++
                gameLastResult = "GOOD!  ${gameWins}/${gameRound + 1}"
            } else {
                gameLastResult = "MISS!  ${gameWins}/${gameRound + 1}"
            }
            gameRound++

            if (gameRound >= 5) {
                if (gameWins >= 3) {
                    happy = min(4, happy + 1)
                    if (happy > 0) {
                        happyZeroSince = 0L
                        happyMistakeCounted = false
                    }
                }
                weight = max(character.minWeight, weight - 1)
                val result = if (gameWins >= 3) "WIN ${gameWins}/5  HAPPY +1" else "LOSE ${gameWins}/5"
                screenMode = ScreenMode.MESSAGE
                message = result
                messageUntil = System.currentTimeMillis() + 2600L
                saveState()
            }
        }

        private fun showMessage(text: String, duration: Long) {
            screenMode = ScreenMode.MESSAGE
            message = text
            messageUntil = System.currentTimeMillis() + duration
        }

        private fun attentionActive(now: Long): Boolean {
            return hungry == 0 ||
                happy == 0 ||
                (isAsleep(now) && !lightsOff) ||
                misbehaving
        }

        private fun handleA(now: Long) {
            lastAAt = now
            if (character == Character.DEAD) return
            when (screenMode) {
                ScreenMode.MAIN -> selectedIcon = (selectedIcon + 1) % 7
                ScreenMode.FEED,
                ScreenMode.LIGHT -> submenuChoice = 1 - submenuChoice
                ScreenMode.STATUS -> statusPage = (statusPage + 1) % 4
                ScreenMode.GAME -> gameGuess(true)
                ScreenMode.MESSAGE -> {
                    screenMode = ScreenMode.MAIN
                    selectedIcon = (selectedIcon + 1) % 7
                }
                ScreenMode.DEAD -> Unit
            }
            invalidate()
        }

        private fun handleB(now: Long) {
            if (character == Character.DEAD) {
                showMessage("AGE ${ageYears(now)}  WT ${weight}", 2200L)
                return
            }
            when (screenMode) {
                ScreenMode.MAIN -> when (selectedIcon) {
                    0 -> {
                        screenMode = ScreenMode.FEED
                        submenuChoice = 0
                    }
                    1 -> {
                        screenMode = ScreenMode.LIGHT
                        submenuChoice = if (lightsOff) 1 else 0
                    }
                    2 -> startGame()
                    3 -> giveMedicine()
                    4 -> cleanPoop()
                    5 -> {
                        screenMode = ScreenMode.STATUS
                        statusPage = 0
                    }
                    6 -> scold()
                }
                ScreenMode.FEED -> {
                    if (submenuChoice == 0) feedMeal() else feedSnack()
                }
                ScreenMode.LIGHT -> {
                    lightsOff = submenuChoice == 1
                    if (lightsOff) {
                        sleepCallSince = 0L
                        sleepMistakeCounted = false
                    }
                    screenMode = ScreenMode.MAIN
                    saveState()
                }
                ScreenMode.STATUS -> statusPage = (statusPage + 1) % 4
                ScreenMode.GAME -> gameGuess(false)
                ScreenMode.MESSAGE -> screenMode = ScreenMode.MAIN
                ScreenMode.DEAD -> Unit
            }
            invalidate()
        }

        private fun handleC(now: Long) {
            if (character == Character.DEAD) {
                if (now - lastAAt <= 900L) {
                    prefs.edit().remove("p1_pet_birth_anchor").apply()
                    resetPet(now)
                }
                invalidate()
                return
            }
            when (screenMode) {
                ScreenMode.MAIN -> selectedIcon = 0
                ScreenMode.GAME -> {
                    gameRound = 0
                    gameWins = 0
                    screenMode = ScreenMode.MAIN
                }
                else -> screenMode = ScreenMode.MAIN
            }
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked != MotionEvent.ACTION_UP) return true
            val now = System.currentTimeMillis()
            processElapsedTime(now)

            val buttonY = height - dp(context, 27).toFloat()
            val radius = dp(context, 22).toFloat()
            val centers = floatArrayOf(width * 0.28f, width * 0.50f, width * 0.72f)
            val dxA = event.x - centers[0]
            val dxB = event.x - centers[1]
            val dxC = event.x - centers[2]
            val dy = event.y - buttonY

            when {
                dxA * dxA + dy * dy <= radius * radius -> handleA(now)
                dxB * dxB + dy * dy <= radius * radius -> handleB(now)
                dxC * dxC + dy * dy <= radius * radius -> handleC(now)
            }
            performClick()
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            ensureBirthAnchor()

            val now = System.currentTimeMillis()
            if (screenMode == ScreenMode.MESSAGE && now >= messageUntil) {
                screenMode = if (character == Character.DEAD) ScreenMode.DEAD else ScreenMode.MAIN
            }

            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(195, 190, 181)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(context, 2).toFloat()
            paint.color = Color.WHITE
            canvas.drawRect(dp(context, 2).toFloat(), dp(context, 2).toFloat(),
                width - dp(context, 2).toFloat(), height - dp(context, 2).toFloat(), paint)
            paint.color = Color.rgb(80, 80, 80)
            canvas.drawRect(dp(context, 4).toFloat(), dp(context, 4).toFloat(),
                width - dp(context, 4).toFloat(), height - dp(context, 4).toFloat(), paint)

            val screenLeft = dp(context, 34).toFloat()
            val screenTop = dp(context, 31).toFloat()
            val screenRight = width - dp(context, 34).toFloat()
            val screenBottom = height - dp(context, 74).toFloat()
            val lcd = RectF(screenLeft, screenTop, screenRight, screenBottom)

            paint.style = Paint.Style.FILL
            paint.color = if (lightsOff && isAsleep(now)) {
                Color.rgb(95, 104, 77)
            } else {
                Color.rgb(170, 184, 133)
            }
            canvas.drawRect(lcd, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(context, 2).toFloat()
            paint.color = Color.rgb(68, 68, 60)
            canvas.drawRect(lcd, paint)

            drawMenuIcons(canvas, lcd, now)
            drawLcd(canvas, lcd, now)
            drawButtons(canvas)
        }

        private fun drawMenuIcons(canvas: Canvas, lcd: RectF, now: Long) {
            val labels = arrayOf("FOOD", "LITE", "GAME", "MED", "WASH", "STAT", "DISC", "CALL")
            paint.typeface = Typeface.MONOSPACE
            paint.textSize = dp(context, 7).toFloat()
            paint.textAlign = Paint.Align.CENTER

            for (i in labels.indices) {
                val top = i < 4
                val column = if (top) i else i - 4
                val x = lcd.left + (column + 0.5f) * (lcd.width() / 4f)
                val y = if (top) lcd.top - dp(context, 8) else lcd.bottom + dp(context, 13)

                val active = if (i == 7) {
                    attentionActive(now)
                } else {
                    screenMode == ScreenMode.MAIN && selectedIcon == i
                }
                paint.style = Paint.Style.FILL
                paint.color = if (active) Color.rgb(0, 0, 0) else Color.rgb(75, 75, 70)
                if (active) {
                    canvas.drawRect(
                        x - dp(context, 15),
                        y - dp(context, 8),
                        x + dp(context, 15),
                        y + dp(context, 2),
                        paint
                    )
                    paint.color = Color.rgb(220, 220, 210)
                } else {
                    paint.color = Color.rgb(25, 25, 25)
                }
                canvas.drawText(labels[i], x, y, paint)
            }
            paint.textAlign = Paint.Align.LEFT
        }

        private fun drawLcd(canvas: Canvas, lcd: RectF, now: Long) {
            if (lightsOff && isAsleep(now)) {
                drawCenteredLcdText(canvas, lcd, "LIGHTS OFF", "Z  Z  Z")
                return
            }

            when (screenMode) {
                ScreenMode.FEED -> drawCenteredLcdText(
                    canvas, lcd,
                    if (submenuChoice == 0) "> MEAL" else "  MEAL",
                    if (submenuChoice == 1) "> SNACK" else "  SNACK"
                )
                ScreenMode.LIGHT -> drawCenteredLcdText(
                    canvas, lcd,
                    if (submenuChoice == 0) "> LIGHT ON" else "  LIGHT ON",
                    if (submenuChoice == 1) "> LIGHT OFF" else "  LIGHT OFF"
                )
                ScreenMode.STATUS -> drawStatus(canvas, lcd, now)
                ScreenMode.GAME -> drawCenteredLcdText(
                    canvas, lcd,
                    "ROUND ${min(5, gameRound + 1)}/5",
                    gameLastResult
                )
                ScreenMode.MESSAGE -> drawCenteredLcdText(canvas, lcd, message, "")
                ScreenMode.DEAD -> drawCenteredLcdText(canvas, lcd, "   *   *   ", "A+C  NEW EGG")
                ScreenMode.MAIN -> drawMainPet(canvas, lcd, now)
            }
        }

        private fun drawStatus(canvas: Canvas, lcd: RectF, now: Long) {
            when (statusPage) {
                0 -> drawCenteredLcdText(
                    canvas, lcd,
                    "AGE ${ageYears(now)}   WT ${weight}",
                    character.label
                )
                1 -> drawCenteredLcdText(
                    canvas, lcd,
                    "DISCIPLINE",
                    heartsText(discipline)
                )
                2 -> drawCenteredLcdText(
                    canvas, lcd,
                    "HUNGRY",
                    heartsText(hungry)
                )
                else -> drawCenteredLcdText(
                    canvas, lcd,
                    "HAPPY",
                    heartsText(happy)
                )
            }
        }

        private fun heartsText(value: Int): String {
            val clamped = value.coerceIn(0, 4)
            return buildString {
                repeat(clamped) { append("<3 ") }
                repeat(4 - clamped) { append("-- ") }
            }.trim()
        }

        private fun drawCenteredLcdText(canvas: Canvas, lcd: RectF, line1: String, line2: String) {
            lcdPaint.color = Color.rgb(24, 31, 18)
            lcdPaint.textAlign = Paint.Align.CENTER
            lcdPaint.typeface = Typeface.MONOSPACE
            lcdPaint.textSize = dp(context, 11).toFloat()
            canvas.drawText(
                line1.take(22),
                lcd.centerX(),
                lcd.centerY() - dp(context, 5),
                lcdPaint
            )
            if (line2.isNotEmpty()) {
                canvas.drawText(
                    line2.take(22),
                    lcd.centerX(),
                    lcd.centerY() + dp(context, 13),
                    lcdPaint
                )
            }
            lcdPaint.textAlign = Paint.Align.LEFT
        }

        private fun drawMainPet(canvas: Canvas, lcd: RectF, now: Long) {
            if (character == Character.EGG) {
                drawEgg(canvas, lcd)
                val remain = max(
                    0L,
                    minutes(5) - (now - stageStartedAt)
                )
                drawTinyText(canvas, lcd, "HATCH ${(remain / 60_000L) + 1}m")
                return
            }

            if (character == Character.DEAD) {
                drawCenteredLcdText(canvas, lcd, "   *   *   ", "A+C  NEW EGG")
                return
            }

            drawPetSprite(canvas, lcd)
            drawPoops(canvas, lcd)
            if (sick) drawSickMark(canvas, lcd)
            if (isAsleep(now)) drawSleepMark(canvas, lcd)
            if (misbehaving) drawTinyText(canvas, lcd, "HEY!")
        }

        private fun drawEgg(canvas: Canvas, lcd: RectF) {
            val cell = min(lcd.width() / 32f, lcd.height() / 16f)
            val cx = lcd.centerX()
            val cy = lcd.centerY()
            lcdPaint.color = Color.rgb(25, 32, 20)
            val points = arrayOf(
                -2 to -3, -1 to -4, 0 to -4, 1 to -4, 2 to -3,
                -3 to -2, 3 to -2, -3 to -1, 3 to -1,
                -3 to 0, 3 to 0, -2 to 1, 2 to 1,
                -2 to 2, -1 to 3, 0 to 3, 1 to 3, 2 to 2
            )
            for ((x, y) in points) {
                canvas.drawRect(
                    cx + x * cell,
                    cy + y * cell,
                    cx + (x + 1) * cell,
                    cy + (y + 1) * cell,
                    lcdPaint
                )
            }
        }

        private fun drawPetSprite(canvas: Canvas, lcd: RectF) {
            val cell = min(lcd.width() / 32f, lcd.height() / 16f)
            val cx = lcd.centerX() + if (animationFrame) cell else -cell
            val cy = lcd.centerY()
            lcdPaint.color = Color.rgb(25, 32, 20)

            val sprite = when (character) {
                Character.BABY -> arrayOf(
                    "  ###  ",
                    " ##### ",
                    "## # ##",
                    "#######",
                    " # # # ",
                    "  ###  "
                )
                Character.CHILD -> arrayOf(
                    "  ###  ",
                    " ##### ",
                    "## # ##",
                    "#######",
                    "# ### #",
                    " ## ## ",
                    "  # #  "
                )
                Character.TEEN_GOOD,
                Character.TEEN_BAD -> arrayOf(
                    " #   # ",
                    " ##### ",
                    "## # ##",
                    "#######",
                    " # # # ",
                    "##   ##",
                    " #   # "
                )
                Character.KUCHI_GOOD,
                Character.KUCHI_BAD -> arrayOf(
                    "  ###  ",
                    " ##### ",
                    "## # ##",
                    "###  ##",
                    " ######",
                    "  ###  ",
                    " #   # "
                )
                Character.MAMETCHI -> arrayOf(
                    " #   # ",
                    "##   ##",
                    " ##### ",
                    "## # ##",
                    "#######",
                    " # # # ",
                    "##   ##"
                )
                Character.GINJI -> arrayOf(
                    "  ###  ",
                    "##   ##",
                    "#######",
                    "## # ##",
                    "#######",
                    " ## ## ",
                    "#     #"
                )
                Character.MASK -> arrayOf(
                    "##   ##",
                    "#######",
                    "# ### #",
                    "## # ##",
                    "#######",
                    " # # # ",
                    "#     #"
                )
                Character.KUCHIPATCHI -> arrayOf(
                    "  ###  ",
                    " ##### ",
                    "## # ##",
                    "###  ##",
                    "##   ##",
                    " ####  ",
                    " #  #  "
                )
                Character.NYOROTCHI -> arrayOf(
                    "   ##  ",
                    "  #### ",
                    " ## #  ",
                    "  ###  ",
                    "  ##   ",
                    " ##    ",
                    "##     "
                )
                Character.TARAKOTCHI -> arrayOf(
                    " ##### ",
                    "## # ##",
                    "#######",
                    "  ###  ",
                    " ## ## ",
                    "##   ##",
                    " #   # "
                )
                Character.BILL -> arrayOf(
                    " ##### ",
                    "##   ##",
                    "### ###",
                    "## # ##",
                    "#######",
                    " ## ## ",
                    "##   ##"
                )
                else -> arrayOf("###")
            }

            val rows = sprite.size
            val cols = sprite.maxOf { it.length }
            val startX = cx - cols * cell / 2f
            val startY = cy - rows * cell / 2f
            sprite.forEachIndexed { row, line ->
                line.forEachIndexed { col, c ->
                    if (c == '#') {
                        canvas.drawRect(
                            startX + col * cell,
                            startY + row * cell,
                            startX + (col + 1) * cell,
                            startY + (row + 1) * cell,
                            lcdPaint
                        )
                    }
                }
            }
        }

        private fun drawPoops(canvas: Canvas, lcd: RectF) {
            if (poopCount <= 0) return
            val cell = min(lcd.width() / 32f, lcd.height() / 16f)
            lcdPaint.color = Color.rgb(25, 32, 20)
            repeat(poopCount) { i ->
                val x = lcd.right - cell * (4f + (i % 2) * 3f)
                val y = lcd.bottom - cell * (3f + (i / 2) * 4f)
                canvas.drawRect(x, y, x + cell * 2f, y + cell, lcdPaint)
                canvas.drawRect(x + cell * 0.5f, y - cell, x + cell * 1.5f, y, lcdPaint)
            }
        }

        private fun drawSickMark(canvas: Canvas, lcd: RectF) {
            lcdPaint.color = Color.rgb(25, 32, 20)
            lcdPaint.typeface = Typeface.MONOSPACE
            lcdPaint.textSize = dp(context, 14).toFloat()
            canvas.drawText("+", lcd.left + dp(context, 9), lcd.top + dp(context, 20), lcdPaint)
        }

        private fun drawSleepMark(canvas: Canvas, lcd: RectF) {
            lcdPaint.color = Color.rgb(25, 32, 20)
            lcdPaint.typeface = Typeface.MONOSPACE
            lcdPaint.textSize = dp(context, 11).toFloat()
            canvas.drawText("Zz", lcd.right - dp(context, 26), lcd.top + dp(context, 18), lcdPaint)
        }

        private fun drawTinyText(canvas: Canvas, lcd: RectF, text: String) {
            lcdPaint.color = Color.rgb(25, 32, 20)
            lcdPaint.typeface = Typeface.MONOSPACE
            lcdPaint.textSize = dp(context, 8).toFloat()
            lcdPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(text, lcd.centerX(), lcd.bottom - dp(context, 7), lcdPaint)
            lcdPaint.textAlign = Paint.Align.LEFT
        }

        private fun drawButtons(canvas: Canvas) {
            val y = height - dp(context, 27).toFloat()
            val radius = dp(context, 15).toFloat()
            val centers = floatArrayOf(width * 0.28f, width * 0.50f, width * 0.72f)
            val labels = arrayOf("A", "B", "C")

            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = dp(context, 10).toFloat()
            paint.textAlign = Paint.Align.CENTER

            centers.forEachIndexed { index, x ->
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(128, 128, 128)
                canvas.drawCircle(x + dp(context, 1), y + dp(context, 2), radius, paint)
                paint.color = Color.rgb(220, 220, 215)
                canvas.drawCircle(x, y, radius, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(context, 1).toFloat()
                paint.color = Color.rgb(70, 70, 70)
                canvas.drawCircle(x, y, radius, paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.BLACK
                canvas.drawText(labels[index], x, y + dp(context, 4), paint)
            }
            paint.textAlign = Paint.Align.LEFT
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
