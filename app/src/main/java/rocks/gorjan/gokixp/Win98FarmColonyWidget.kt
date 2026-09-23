package rocks.gorjan.gokixp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min

object Win98FarmColonyWidget {
    private const val STATE_KEY = "win98_homestead_colony_v1"
    private const val GAME_DAY_MS = 45L * 60L * 1000L
    private const val OFFLINE_DAY_CAP = 72

    private data class Crop(
        val id: String,
        val name: String,
        val cost: Int,
        val days: Int,
        val food: Int,
        val coins: Int,
        val seasons: Set<Int>,
        val rep: Int = 0
    )

    private data class Plot(var crop: String = "", var age: Int = 0)

    private val crops = listOf(
        Crop("carrot", "Carrot", 2, 2, 6, 1, setOf(0, 1)),
        Crop("potato", "Potato", 3, 3, 9, 1, setOf(0)),
        Crop("wheat", "Wheat", 4, 3, 10, 2, setOf(0, 2)),
        Crop("corn", "Corn", 5, 4, 14, 3, setOf(1, 2), 25),
        Crop("tomato", "Tomato", 6, 4, 12, 5, setOf(1), 40),
        Crop("pumpkin", "Pumpkin", 8, 5, 22, 6, setOf(2), 60),
        Crop("winterroot", "Winter Root", 5, 4, 11, 2, setOf(3), 35),
        Crop("mushroom", "Cellar Mushroom", 7, 3, 8, 6, setOf(0, 1, 2, 3), 80)
    )
    private val cropMap = crops.associateBy { it.id }

    private class Game(private val context: Context) {
        private val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

        var day = 0
        var coins = 50
        var food = 34
        var wood = 26
        var stone = 16
        var ore = 0
        var tech = 0
        var reputation = 0
        var population = 3
        var housing = 4
        var happiness = 74
        var starvation = 0

        var farmers = 1
        var lumber = 1
        var miners = 0
        var scientists = 0
        var ranchers = 0

        var barn = 0
        var sawmill = 0
        var quarry = 0
        var workshop = 0
        var granary = 0
        var market = 0
        var cottages = 0

        var chickens = 0
        var cows = 0
        var sheep = 0

        var irrigation = false
        var rotation = false
        var greenhouse = false
        var deepMining = false
        var preservation = false
        var animalCare = false

        var lastReal = System.currentTimeMillis()
        var lastForage = 0L
        var lastEvent = "Three settlers claimed an abandoned farm."
        val plots = MutableList(8) { Plot() }
        val log = ArrayDeque<String>()

        init {
            load()
            val caught = catchUp()
            if (caught > 0) {
                record("Caught up " + caught + " day(s) while you were away.")
                save()
            }
        }

        val season: Int get() = (day / 14) % 4
        val seasonDay: Int get() = day % 14 + 1
        val year: Int get() = day / 56 + 1
        val seasonName: String get() = arrayOf("Spring", "Summer", "Fall", "Winter")[season]
        val assigned: Int get() = farmers + lumber + miners + scientists + ranchers
        val free: Int get() = max(0, population - assigned)
        val level: Int get() = 1 + reputation / 100
        val unlockedPlots: Int get() = min(plots.size, 4 + level * 2)
        val animalCap: Int get() = barn * 4

        fun nextDayMinutes(): Int {
            val elapsed = max(0L, System.currentTimeMillis() - lastReal)
            val left = max(0L, GAME_DAY_MS - elapsed % GAME_DAY_MS)
            return max(1, ((left + 59_999L) / 60_000L).toInt())
        }

        fun weather(forDay: Int = day): String {
            val seasonAtDay = (forDay / 14) % 4
            val n = ((((forDay.toLong() + 17L) * 1103515245L + 12345L) ushr 16).toInt() and 7)
            return when {
                seasonAtDay == 3 && n <= 1 -> "Snow"
                n == 0 -> "Rain"
                n == 1 -> "Cloudy"
                n == 2 && seasonAtDay == 1 -> "Heat"
                n == 3 -> "Wind"
                else -> "Clear"
            }
        }

        fun catchUp(): Int {
            val now = System.currentTimeMillis()
            if (lastReal <= 0L || lastReal > now) lastReal = now
            val raw = ((now - lastReal) / GAME_DAY_MS).toInt()
            val count = min(OFFLINE_DAY_CAP, max(0, raw))
            repeat(count) { simulateDay() }
            if (count > 0) {
                lastReal += count * GAME_DAY_MS
                save()
            }
            return count
        }

        private fun simulateDay() {
            val oldSeason = season
            day++
            val currentWeather = weather()

            val active = plots.take(unlockedPlots).filter { it.crop.isNotEmpty() }
            val workable = if (irrigation) active.size else min(active.size, farmers * 3)
            active.take(workable).forEach { it.age += if (currentWeather == "Rain") 2 else 1 }

            if (oldSeason != season && !greenhouse) {
                var lost = 0
                plots.forEach {
                    val crop = cropMap[it.crop]
                    if (crop != null && season !in crop.seasons) {
                        it.crop = ""
                        it.age = 0
                        lost++
                    }
                }
                if (lost > 0) record(lost.toString() + " out-of-season crop(s) withered.")
            }

            wood += lumber * (2 + sawmill)
            stone += miners * (1 + quarry)
            if (miners > 0 && (deepMining || day % 2 == 0)) ore += miners * if (deepMining) 2 else 1
            tech += scientists * (1 + max(0, workshop - 1))

            var care = min(animalCap, ranchers * 4)
            val caredCows = min(cows, care)
            care -= caredCows
            val caredSheep = min(sheep, care)
            care -= caredSheep
            val caredChickens = min(chickens, care)
            food += caredCows * 2 + caredChickens
            if (day % 2 == 0) coins += caredSheep * 2
            if (animalCare && caredCows + caredSheep + caredChickens > 0) happiness = min(100, happiness + 1)
            if (granary > 0 && day % 3 == 0) food += granary * 2

            val need = max(1, (population + 1) / 2)
            if (food >= need) {
                food -= need
                starvation = 0
                if (housing >= population) happiness = min(100, happiness + 1)
            } else {
                food = 0
                starvation++
                happiness = max(0, happiness - 9)
                record("Food stores ran short. Morale fell.")
                if (starvation >= 4 && population > 1) {
                    population--
                    normalizeJobs()
                    starvation = 0
                    record("A settler left the hungry colony.")
                }
            }
            if (population > housing) happiness = max(0, happiness - 3)

            if (day % 5 == 0 && population < housing && happiness >= 65 && food >= population * 2) {
                population++
                reputation += 6
                record("A new settler moved into the homestead.")
            }
            if (day % 4 == 0) randomEvent()
            clamp()
        }

        private fun randomEvent() {
            when ((((day.toLong() * 1664525L) + 1013904223L) ushr 16).toInt() and 7) {
                0 -> { val g = 5 + level * 2; food += g; record("Foragers found +" + g + " food.") }
                1 -> { val g = 5 + lumber; wood += g; record("A fallen grove yielded +" + g + " wood.") }
                2 -> {
                    if (wood >= 4) { wood -= 4; record("A storm damaged fencing (-4 wood).") }
                    else { happiness = max(0, happiness - 4); record("A storm rattled the colony.") }
                }
                3 -> { val g = 8 + market * 3; coins += g; record("A trader paid " + g + " coins.") }
                4 -> if (workshop > 0) { tech += 4 + workshop; record("Workshop experiments produced research.") }
                5 -> if (population < housing && happiness >= 75) { population++; reputation += 5; record("Travelers decided to stay.") }
                6 -> { val g = if (deepMining) 4 else 2; ore += g; record("A shallow seam yielded +" + g + " ore.") }
                else -> record("A quiet day passed without incident.")
            }
        }

        private fun record(message: String) {
            lastEvent = message
            log.addFirst("Y" + year + " " + seasonName.take(3) + " " + seasonDay + ": " + message)
            while (log.size > 14) log.removeLast()
        }

        fun forage(): String {
            val now = System.currentTimeMillis()
            val wait = 4L * 60L * 1000L
            if (now - lastForage < wait) {
                val mins = ((wait - (now - lastForage) + 59_999L) / 60_000L).toInt()
                return "Foragers need " + mins + " more minute(s)."
            }
            lastForage = now
            val seed = ((now / wait) xor day.toLong()).toInt()
            val f = 3 + (seed and 3)
            val w = 2 + ((seed ushr 2) and 3)
            food += f
            wood += w
            reputation++
            val msg = "Foraged +" + f + " food, +" + w + " wood."
            record(msg)
            save()
            return msg
        }

        fun availableCrops(): List<Crop> = crops.filter {
            reputation >= it.rep &&
                (season in it.seasons || greenhouse) &&
                (it.id != "mushroom" || workshop >= 1)
        }

        fun plant(id: String): String {
            val crop = cropMap[id] ?: return "Unknown crop."
            if (reputation < crop.rep) return "Need " + crop.rep + " reputation."
            if (season !in crop.seasons && !greenhouse) return crop.name + " is out of season."
            if (crop.id == "mushroom" && workshop < 1) return "Build a Workshop first."
            val plot = plots.take(unlockedPlots).firstOrNull { it.crop.isEmpty() }
                ?: return "All unlocked plots are occupied."
            if (coins < crop.cost) return "Need " + crop.cost + " coins for seed."
            coins -= crop.cost
            plot.crop = crop.id
            plot.age = 0
            record("Planted " + crop.name + ".")
            save()
            return crop.name + " planted."
        }

        fun harvest(): String {
            var count = 0
            var foodGain = 0
            var coinGain = 0
            plots.take(unlockedPlots).forEach {
                val crop = cropMap[it.crop] ?: return@forEach
                if (it.age >= crop.days) {
                    count++
                    foodGain += crop.food + if (rotation) 2 else 0
                    coinGain += crop.coins
                    it.crop = ""
                    it.age = 0
                }
            }
            if (count == 0) return "Nothing is ready to harvest."
            food += foodGain
            coins += coinGain
            reputation += count * 3
            clamp()
            val msg = "Harvested " + count + " plot(s): +" + foodGain + " food, +" + coinGain + " coins."
            record(msg)
            save()
            return msg
        }

        fun sellFood(): String {
            if (food < 10) return "Need 10 food."
            food -= 10
            val value = 6 + market * 2
            coins += value
            record("Sold 10 food for " + value + " coins.")
            save()
            return "Market sale complete."
        }

        fun preserveFood(): String {
            if (!preservation) return "Research Food Preservation first."
            if (food < 15) return "Need 15 food."
            food -= 15
            coins += 12 + market * 2
            reputation += 2
            record("Preserved surplus food for trade.")
            save()
            return "Preserved goods sold."
        }

        fun job(name: String, delta: Int): String {
            if (delta > 0 && free <= 0) return "No unassigned settlers."
            val current = when (name) {
                "farmer" -> farmers
                "lumber" -> lumber
                "miner" -> miners
                "science" -> scientists
                "rancher" -> ranchers
                else -> return "Unknown job."
            }
            if (delta < 0 && current <= 0) return "Nobody has that job."
            when (name) {
                "farmer" -> farmers = current + delta
                "lumber" -> lumber = current + delta
                "miner" -> miners = current + delta
                "science" -> scientists = current + delta
                "rancher" -> ranchers = current + delta
            }
            save()
            return "Work roster updated."
        }

        private fun spend(w: Int = 0, s: Int = 0, o: Int = 0, c: Int = 0): Boolean {
            if (wood < w || stone < s || ore < o || coins < c) return false
            wood -= w; stone -= s; ore -= o; coins -= c
            return true
        }

        fun build(kind: String): String {
            val result = when (kind) {
                "house" -> {
                    val w = 24 + cottages * 8
                    val s = 8 + cottages * 4
                    if (!spend(w, s)) "Need " + w + " wood and " + s + " stone."
                    else { cottages++; housing += 2; reputation += 8; "Built a cottage. Housing " + housing + "." }
                }
                "barn" -> upgrade("Barn", barn, 32, 14) { barn++ }
                "sawmill" -> upgrade("Sawmill", sawmill, 28, 12) { sawmill++ }
                "quarry" -> upgrade("Quarry", quarry, 24, 20) { quarry++ }
                "granary" -> upgrade("Granary", granary, 26, 10) { granary++ }
                "workshop" -> {
                    if (workshop >= 3) "Workshop is already max level."
                    else {
                        val w = 35 + workshop * 18
                        val s = 18 + workshop * 10
                        val o = workshop * 3
                        if (!spend(w, s, o)) "Need " + w + " wood, " + s + " stone and " + o + " ore."
                        else { workshop++; reputation += 12; "Workshop upgraded to level " + workshop + "." }
                    }
                }
                "market" -> {
                    if (market >= 3) "Market is already max level."
                    else {
                        val w = 30 + market * 15
                        val s = 15 + market * 8
                        val c = 15 + market * 10
                        if (!spend(w, s, 0, c)) "Need " + w + " wood, " + s + " stone and " + c + " coins."
                        else { market++; reputation += 12; "Market upgraded to level " + market + "." }
                    }
                }
                else -> "Unknown building."
            }
            if (!result.startsWith("Need") && !result.contains("max level")) record(result)
            save()
            return result
        }

        private fun upgrade(name: String, current: Int, bw: Int, bs: Int, action: () -> Unit): String {
            if (current >= 3) return name + " is already max level."
            val w = bw + current * 15
            val s = bs + current * 9
            if (!spend(w, s)) return "Need " + w + " wood and " + s + " stone."
            action()
            reputation += 9
            return name + " upgraded to level " + (current + 1) + "."
        }

        fun buyAnimal(kind: String): String {
            if (barn <= 0) return "Build a Barn first."
            if (chickens + cows + sheep >= animalCap) return "Barn capacity is full."
            val result = when (kind) {
                "chicken" -> if (!spend(c = 18)) "Need 18 coins." else { chickens++; "Chicken added." }
                "sheep" -> if (barn < 2) "Barn level 2 required." else if (!spend(c = 34)) "Need 34 coins." else { sheep++; "Sheep added." }
                "cow" -> if (barn < 2) "Barn level 2 required." else if (!spend(c = 42)) "Need 42 coins." else { cows++; "Cow added." }
                else -> "Unknown animal."
            }
            if (result.endsWith("added.")) record(result)
            save()
            return result
        }

        fun research(id: String): String {
            val data = when (id) {
                "irrigation" -> arrayOf("25", "Irrigation", if (irrigation) "1" else "0", if (workshop >= 1) "1" else "0")
                "rotation" -> arrayOf("40", "Crop Rotation", if (rotation) "1" else "0", if (workshop >= 1) "1" else "0")
                "deep" -> arrayOf("60", "Deep Mining", if (deepMining) "1" else "0", if (quarry >= 1) "1" else "0")
                "preserve" -> arrayOf("55", "Food Preservation", if (preservation) "1" else "0", if (granary >= 1) "1" else "0")
                "animals" -> arrayOf("70", "Animal Care", if (animalCare) "1" else "0", if (barn >= 2) "1" else "0")
                "greenhouse" -> arrayOf("90", "Greenhouse", if (greenhouse) "1" else "0", if (workshop >= 2) "1" else "0")
                else -> return "Unknown research."
            }
            val cost = data[0].toInt()
            val label = data[1]
            if (data[2] == "1") return label + " already researched."
            if (data[3] != "1") return "Required building level not met."
            if (tech < cost) return "Need " + cost + " research."
            tech -= cost
            when (id) {
                "irrigation" -> irrigation = true
                "rotation" -> rotation = true
                "deep" -> deepMining = true
                "preserve" -> preservation = true
                "animals" -> animalCare = true
                "greenhouse" -> greenhouse = true
            }
            reputation += 15
            record("Research completed: " + label + ".")
            save()
            return label + " unlocked."
        }

        fun plotText(i: Int): String {
            if (i >= unlockedPlots) return "Plot " + (i + 1) + ": locked"
            val p = plots[i]
            if (p.crop.isEmpty()) return "Plot " + (i + 1) + ": empty"
            val c = cropMap[p.crop] ?: return "Plot " + (i + 1) + ": unknown"
            return if (p.age >= c.days) "Plot " + (i + 1) + ": " + c.name + " READY"
            else "Plot " + (i + 1) + ": " + c.name + " " + p.age + "/" + c.days + "d"
        }

        fun goal(): String = when {
            barn == 0 -> "Goal: build a Barn and begin ranching."
            workshop == 0 -> "Goal: build a Workshop and start research."
            population < 6 -> "Goal: support 6 settlers with food, housing and morale."
            !irrigation -> "Goal: research Irrigation."
            quarry == 0 -> "Goal: build a Quarry and assign a miner."
            !greenhouse -> "Goal: unlock the Greenhouse."
            reputation < 300 -> "Goal: reach 300 reputation and Colony Lv. 4."
            else -> "Goal: max buildings, animals and research."
        }

        private fun normalizeJobs() {
            while (assigned > population) {
                when {
                    scientists > 0 -> scientists--
                    miners > 0 -> miners--
                    ranchers > 0 -> ranchers--
                    lumber > 0 -> lumber--
                    farmers > 0 -> farmers--
                    else -> return
                }
            }
        }

        private fun clamp() {
            food = min(120 + granary * 120, food)
            wood = min(150 + level * 50, wood)
            stone = min(150 + level * 50, stone)
            ore = min(100 + quarry * 60, ore)
        }

        fun save() {
            val j = JSONObject()
            j.put("day", day); j.put("coins", coins); j.put("food", food); j.put("wood", wood)
            j.put("stone", stone); j.put("ore", ore); j.put("tech", tech); j.put("rep", reputation)
            j.put("pop", population); j.put("housing", housing); j.put("happy", happiness); j.put("starve", starvation)
            j.put("farmers", farmers); j.put("lumber", lumber); j.put("miners", miners); j.put("scientists", scientists); j.put("ranchers", ranchers)
            j.put("barn", barn); j.put("sawmill", sawmill); j.put("quarry", quarry); j.put("workshop", workshop); j.put("granary", granary); j.put("market", market); j.put("cottages", cottages)
            j.put("chickens", chickens); j.put("cows", cows); j.put("sheep", sheep)
            j.put("irrigation", irrigation); j.put("rotation", rotation); j.put("greenhouse", greenhouse); j.put("deep", deepMining); j.put("preserve", preservation); j.put("animalCare", animalCare)
            j.put("lastReal", lastReal); j.put("lastForage", lastForage); j.put("event", lastEvent)
            val pa = JSONArray()
            plots.forEach { pa.put(JSONObject().put("crop", it.crop).put("age", it.age)) }
            j.put("plots", pa)
            val la = JSONArray()
            log.forEach { la.put(it) }
            j.put("log", la)
            prefs.edit().putString(STATE_KEY, j.toString()).apply()
        }

        private fun load() {
            val raw = prefs.getString(STATE_KEY, null) ?: return
            try {
                val j = JSONObject(raw)
                day = j.optInt("day", day); coins = j.optInt("coins", coins); food = j.optInt("food", food)
                wood = j.optInt("wood", wood); stone = j.optInt("stone", stone); ore = j.optInt("ore", ore)
                tech = j.optInt("tech", tech); reputation = j.optInt("rep", reputation); population = j.optInt("pop", population)
                housing = j.optInt("housing", housing); happiness = j.optInt("happy", happiness); starvation = j.optInt("starve", starvation)
                farmers = j.optInt("farmers", farmers); lumber = j.optInt("lumber", lumber); miners = j.optInt("miners", miners)
                scientists = j.optInt("scientists", scientists); ranchers = j.optInt("ranchers", ranchers)
                barn = j.optInt("barn", barn); sawmill = j.optInt("sawmill", sawmill); quarry = j.optInt("quarry", quarry)
                workshop = j.optInt("workshop", workshop); granary = j.optInt("granary", granary); market = j.optInt("market", market); cottages = j.optInt("cottages", cottages)
                chickens = j.optInt("chickens", chickens); cows = j.optInt("cows", cows); sheep = j.optInt("sheep", sheep)
                irrigation = j.optBoolean("irrigation", irrigation); rotation = j.optBoolean("rotation", rotation)
                greenhouse = j.optBoolean("greenhouse", greenhouse); deepMining = j.optBoolean("deep", deepMining)
                preservation = j.optBoolean("preserve", preservation); animalCare = j.optBoolean("animalCare", animalCare)
                lastReal = j.optLong("lastReal", lastReal); lastForage = j.optLong("lastForage", lastForage); lastEvent = j.optString("event", lastEvent)
                val pa = j.optJSONArray("plots")
                if (pa != null) for (i in 0 until min(plots.size, pa.length())) {
                    val p = pa.optJSONObject(i) ?: continue
                    plots[i].crop = p.optString("crop", "")
                    plots[i].age = p.optInt("age", 0)
                }
                log.clear()
                val la = j.optJSONArray("log")
                if (la != null) for (i in 0 until min(14, la.length())) {
                    val line = la.optString(i, "")
                    if (line.isNotEmpty()) log.addLast(line)
                }
                normalizeJobs()
            } catch (_: Exception) {
            }
        }
    }

    fun create(activity: MainActivity): View = Compact(activity, Game(activity))

    private class Compact(
        private val activity: MainActivity,
        private val game: Game
    ) : LinearLayout(activity) {
        private val status = TextView(activity)
        private val resources = TextView(activity)
        private val map = MapView(activity, game)
        private val handler = Handler(Looper.getMainLooper())
        private val ticker = object : Runnable {
            override fun run() {
                game.catchUp()
                refresh()
                handler.postDelayed(this, if (isShown) 60_000L else 300_000L)
            }
        }

        init {
            orientation = VERTICAL
            setPadding(dp(activity, 5), dp(activity, 5), dp(activity, 5), dp(activity, 6))
            background = inset(activity, Color.rgb(192, 192, 192))
            status.setTextColor(Color.BLACK)
            status.textSize = 10.5f
            status.typeface = Typeface.MONOSPACE
            addView(status, LayoutParams(LayoutParams.MATCH_PARENT, dp(activity, 22)))
            addView(map, LayoutParams(LayoutParams.MATCH_PARENT, dp(activity, 142)))
            resources.setTextColor(Color.BLACK)
            resources.textSize = 9.5f
            resources.typeface = Typeface.MONOSPACE
            resources.gravity = Gravity.CENTER
            resources.maxLines = 2
            addView(resources, LayoutParams(LayoutParams.MATCH_PARENT, dp(activity, 38)))
            val buttons = LinearLayout(activity).apply { orientation = HORIZONTAL }
            buttons.addView(button(activity, "Harvest") { toast(game.harvest()); refresh() }, LayoutParams(0, dp(activity, 29), 1f).apply { marginEnd = dp(activity, 3) })
            buttons.addView(button(activity, "Forage") { toast(game.forage()); refresh() }, LayoutParams(0, dp(activity, 29), 1f).apply { marginStart = dp(activity, 2); marginEnd = dp(activity, 2) })
            buttons.addView(button(activity, "Expand") { showExpanded(activity, game) { refresh() } }, LayoutParams(0, dp(activity, 29), 1f).apply { marginStart = dp(activity, 3) })
            addView(buttons, LayoutParams(LayoutParams.MATCH_PARENT, dp(activity, 32)))
            refresh()
        }

        private fun toast(s: String) = android.widget.Toast.makeText(activity, s, android.widget.Toast.LENGTH_SHORT).show()

        private fun refresh() {
            status.text = "Y" + game.year + " " + game.seasonName.uppercase() + " " + game.seasonDay + "  " + game.weather() + "  " + game.nextDayMinutes() + "m"
            resources.text = "POP " + game.population + "/" + game.housing + "  FOOD " + game.food + "  COIN " + game.coins + "\nWOOD " + game.wood + "  STONE " + game.stone + "  REP " + game.reputation
            map.invalidate()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }

        override fun onDetachedFromWindow() {
            game.save()
            handler.removeCallbacks(ticker)
            super.onDetachedFromWindow()
        }
    }

    private class MapView(context: Context, private val game: Game) : View(context) {
        private val p = Paint().apply { isAntiAlias = false }

        init { setBackgroundColor(Color.rgb(70, 118, 70)) }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            c.drawColor(when (game.season) {
                0 -> Color.rgb(93, 151, 74)
                1 -> Color.rgb(76, 145, 63)
                2 -> Color.rgb(142, 117, 59)
                else -> Color.rgb(150, 163, 152)
            })

            p.color = Color.rgb(61, 111, 155)
            c.drawRect(w * .76f, 0f, w, h, p)
            p.color = Color.rgb(77, 132, 176)
            for (y in 6 until height step 15) c.drawRect(w * .78f, y.toFloat(), w * .95f, y + 2f, p)

            for (i in 0 until game.unlockedPlots) {
                val col = i % 4
                val row = i / 4
                val l = 8f + col * (w * .145f)
                val t = 10f + row * (h * .32f)
                val r = l + w * .12f
                val b = t + h * .24f
                p.color = Color.rgb(105, 76, 48)
                c.drawRect(l, t, r, b, p)
                val plot = game.plots[i]
                val crop = cropMap[plot.crop]
                if (crop != null) {
                    val pct = min(1f, plot.age.toFloat() / crop.days.toFloat())
                    p.color = if (pct >= 1f) Color.rgb(226, 205, 81) else Color.rgb(75, 151, 62)
                    val plants = 1 + (pct * 4f).toInt()
                    repeat(plants) { n ->
                        val x = l + 4 + (n % 3) * ((r - l - 8) / 3f)
                        val y = b - 5 - (n / 3) * 8
                        c.drawRect(x, y, x + 4, y + 5, p)
                    }
                }
            }

            building(c, w * .08f, h * .72f, "H", Color.rgb(206, 191, 154))
            if (game.barn > 0) building(c, w * .31f, h * .70f, "B", Color.rgb(154, 74, 56))
            if (game.workshop > 0) building(c, w * .53f, h * .72f, "W", Color.rgb(116, 116, 116))

            p.color = Color.rgb(35, 35, 35)
            repeat(min(8, game.population)) { i ->
                val x = 16f + ((game.day * 17 + i * 31) % max(20, (width * .68f).toInt())).toFloat()
                val y = h * .57f + ((i * 13 + game.day * 5) % max(8, (h * .12f).toInt())).toFloat()
                c.drawRect(x, y, x + 3, y + 5, p)
            }

            p.color = Color.argb(210, 245, 245, 220)
            c.drawRect(4f, h - 18f, w - 4f, h - 4f, p)
            p.color = Color.BLACK
            p.typeface = Typeface.MONOSPACE
            p.textSize = 9f * resources.displayMetrics.density
            c.drawText(game.lastEvent.take(42), 8f, h - 8f, p)
        }

        private fun building(c: Canvas, x: Float, y: Float, label: String, color: Int) {
            p.color = color
            c.drawRect(x, y, x + 34, y + 24, p)
            p.color = Color.rgb(85, 53, 39)
            val roof = Path()
            roof.moveTo(x - 3, y); roof.lineTo(x + 17, y - 12); roof.lineTo(x + 37, y); roof.close()
            c.drawPath(roof, p)
            p.color = Color.BLACK
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = 10f * resources.displayMetrics.density
            c.drawText(label, x + 12, y + 17, p)
        }
    }

    private fun showExpanded(activity: MainActivity, game: Game, changed: () -> Unit) {
        game.catchUp()
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val header = TextView(activity).apply {
            setTextColor(Color.BLACK)
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            setPadding(dp(activity, 5), dp(activity, 4), dp(activity, 5), dp(activity, 6))
            background = inset(activity, Color.WHITE)
        }
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(activity, 5), 0, dp(activity, 5)) }
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        var tab = 0
        lateinit var render: () -> Unit

        fun toast(s: String) = android.widget.Toast.makeText(activity, s, android.widget.Toast.LENGTH_SHORT).show()
        fun section(s: String) = TextView(activity).apply {
            text = s; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(0, 0, 128))
            typeface = Typeface.DEFAULT_BOLD; textSize = 12f; setPadding(dp(activity, 6), dp(activity, 4), dp(activity, 6), dp(activity, 4))
        }
        fun line(s: String) = TextView(activity).apply {
            text = s; setTextColor(Color.BLACK); textSize = 11f; setPadding(dp(activity, 5), dp(activity, 4), dp(activity, 5), dp(activity, 4))
        }
        fun action(title: String, detail: String, label: String, work: () -> String): View {
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4)) }
            row.addView(TextView(activity).apply {
                text = title + "\n" + detail; setTextColor(Color.BLACK); textSize = 10.5f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(button(activity, label) { toast(work()); render(); changed() }, LinearLayout.LayoutParams(dp(activity, 78), dp(activity, 30)))
            return row
        }
        fun jobRow(title: String, id: String, count: () -> Int): View {
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(activity, 4), dp(activity, 3), dp(activity, 4), dp(activity, 3)) }
            row.addView(TextView(activity).apply { text = title + ": " + count(); setTextColor(Color.BLACK); textSize = 11f }, LinearLayout.LayoutParams(0, dp(activity, 30), 1f))
            row.addView(button(activity, "-") { toast(game.job(id, -1)); render(); changed() }, LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 28)).apply { marginEnd = dp(activity, 3) })
            row.addView(button(activity, "+") { toast(game.job(id, 1)); render(); changed() }, LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 28)))
            return row
        }

        fun overview() {
            body.addView(section("COLONY STATUS"))
            body.addView(line(game.goal()))
            body.addView(line("Next game day in about " + game.nextDayMinutes() + " min. Up to " + OFFLINE_DAY_CAP + " offline days catch up when WIN26 opens."))
            body.addView(line("Latest: " + game.lastEvent))
            body.addView(section("QUICK ACTIONS"))
            body.addView(action("Forage", "4-minute manual cooldown", "Go") { game.forage() })
            body.addView(action("Harvest", "Collect every mature field", "Harvest") { game.harvest() })
            body.addView(action("Market", "Sell 10 food; Market raises the price", "Sell") { game.sellFood() })
            if (game.preservation) body.addView(action("Preserve Food", "15 food becomes higher-value trade goods", "Make") { game.preserveFood() })
            body.addView(section("DAILY PRODUCTION"))
            body.addView(line("Farmers cover " + game.farmers * 3 + " plots/day. Lumber +" + game.lumber * (2 + game.sawmill) + ". Stone +" + game.miners * (1 + game.quarry) + "."))
            body.addView(line("Scientists +" + game.scientists * (1 + max(0, game.workshop - 1)) + " tech/day. Ranchers care for " + game.ranchers * 4 + " animals."))
        }

        fun farm() {
            body.addView(section("FIELDS  " + game.unlockedPlots + "/" + game.plots.size + " UNLOCKED"))
            for (i in game.plots.indices) body.addView(line(game.plotText(i)))
            body.addView(action("Harvest Ready", "Collect all mature fields", "Harvest") { game.harvest() })
            body.addView(section("SEED CATALOG"))
            game.availableCrops().forEach { crop ->
                val ss = crop.seasons.joinToString("/") { arrayOf("Sp", "Su", "Fa", "Wi")[it] }
                body.addView(action(crop.name, crop.cost.toString() + "c | " + crop.days + "d | +" + crop.food + " food +" + crop.coins + "c | " + ss, "Plant") { game.plant(crop.id) })
            }
            body.addView(section("RANCH  " + (game.chickens + game.cows + game.sheep) + "/" + game.animalCap))
            body.addView(line("Chicken " + game.chickens + " | Cow " + game.cows + " | Sheep " + game.sheep + ". One rancher handles four animals."))
            body.addView(action("Chicken", "18c | +1 food/day", "Buy") { game.buyAnimal("chicken") })
            body.addView(action("Sheep", "34c | Barn Lv2 | wool income", "Buy") { game.buyAnimal("sheep") })
            body.addView(action("Cow", "42c | Barn Lv2 | +2 food/day", "Buy") { game.buyAnimal("cow") })
        }

        fun people() {
            body.addView(section("WORK ROSTER"))
            body.addView(line("Settlers " + game.population + " | Assigned " + game.assigned + " | Free " + game.free))
            body.addView(jobRow("Farmers", "farmer") { game.farmers })
            body.addView(jobRow("Lumberjacks", "lumber") { game.lumber })
            body.addView(jobRow("Miners", "miner") { game.miners })
            body.addView(jobRow("Researchers", "science") { game.scientists })
            body.addView(jobRow("Ranchers", "rancher") { game.ranchers })
            body.addView(section("SETTLEMENT"))
            body.addView(line("Happiness " + game.happiness + "% | Housing " + game.population + "/" + game.housing + " | Hunger warning " + game.starvation + "/4"))
            body.addView(line("Spare housing, food and morale attract settlers over time. Persistent shortages drive them away."))
        }

        fun build() {
            body.addView(section("CONSTRUCTION"))
            body.addView(action("Cottage", "Housing +2; cost rises as the colony grows", "Build") { game.build("house") })
            body.addView(action("Barn Lv" + game.barn, "Animal capacity +4/level", "Build") { game.build("barn") })
            body.addView(action("Sawmill Lv" + game.sawmill, "More wood per lumberjack", "Build") { game.build("sawmill") })
            body.addView(action("Quarry Lv" + game.quarry, "Stone and ore progression", "Build") { game.build("quarry") })
            body.addView(action("Workshop Lv" + game.workshop, "Research efficiency and unlocks", "Build") { game.build("workshop") })
            body.addView(action("Granary Lv" + game.granary, "More food storage and passive surplus", "Build") { game.build("granary") })
            body.addView(action("Market Lv" + game.market, "Better food sales and trader payouts", "Build") { game.build("market") })
        }

        fun tech() {
            body.addView(section("RESEARCH  " + game.tech + " POINTS"))
            body.addView(action("Irrigation", "25 tech | Workshop Lv1 | all fields grow", if (game.irrigation) "Done" else "Research") { game.research("irrigation") })
            body.addView(action("Crop Rotation", "40 tech | Workshop Lv1 | harvest bonus", if (game.rotation) "Done" else "Research") { game.research("rotation") })
            body.addView(action("Deep Mining", "60 tech | Quarry Lv1 | more ore", if (game.deepMining) "Done" else "Research") { game.research("deep") })
            body.addView(action("Food Preservation", "55 tech | Granary Lv1 | trade surplus", if (game.preservation) "Done" else "Research") { game.research("preserve") })
            body.addView(action("Animal Care", "70 tech | Barn Lv2 | ranch morale bonus", if (game.animalCare) "Done" else "Research") { game.research("animals") })
            body.addView(action("Greenhouse", "90 tech | Workshop Lv2 | any crop, any season", if (game.greenhouse) "Done" else "Research") { game.research("greenhouse") })
            body.addView(section("EVENT LOG"))
            if (game.log.isEmpty()) body.addView(line("No events recorded yet."))
            game.log.forEach { body.addView(line(it)) }
        }

        render = {
            game.catchUp()
            header.text = "Y" + game.year + " " + game.seasonName + " " + game.seasonDay + " / " + game.weather() + "\n" +
                "Pop " + game.population + "/" + game.housing + "  Happy " + game.happiness + "%  Lv " + game.level + "  Rep " + game.reputation + "\n" +
                "$" + game.coins + "  Food " + game.food + "  Wood " + game.wood + "  Stone " + game.stone + "  Ore " + game.ore + "  Tech " + game.tech
            body.removeAllViews()
            when (tab) { 0 -> overview(); 1 -> farm(); 2 -> people(); 3 -> build(); else -> tech() }
        }

        arrayOf("Status", "Farm", "People", "Build", "Tech").forEachIndexed { i, label ->
            tabs.addView(button(activity, label) { tab = i; render() }, LinearLayout.LayoutParams(0, dp(activity, 29), 1f).apply { if (i > 0) marginStart = dp(activity, 2) })
        }
        root.addView(tabs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 39)))
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 430)))

        render()
        Win98Dialogs.showCustom(
            context = activity,
            title = "Homestead Colony Manager",
            content = root,
            positiveText = "Close",
            negativeText = null,
            onPositive = { game.save(); changed() }
        )
    }

    private fun inset(context: Context, fill: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(context, 1), Color.rgb(92, 92, 92))
    }

    private fun button(context: Context, label: String, action: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Color.BLACK)
        textSize = 10.5f
        minHeight = dp(context, 27)
        setPadding(dp(context, 4), dp(context, 2), dp(context, 4), dp(context, 2))
        background = AppCompatResources.getDrawable(context, R.drawable.window_button_background)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
