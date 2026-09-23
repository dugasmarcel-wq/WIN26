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

    private class MapView(
        context: Context,
        private val game: Game,
        private val compact: Boolean = true,
        private val onSelection: ((String) -> Unit)? = null
    ) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pixel = Paint().apply { isAntiAlias = false }
        private val handler = Handler(Looper.getMainLooper())
        private var frame = 0
        private var mode = 0

        private val ticker = object : Runnable {
            override fun run() {
                if (isShown) {
                    frame = (frame + 1) % 240
                    invalidate()
                }
                handler.postDelayed(this, if (isShown) {
                    if (compact) 900L else 420L
                } else 4000L)
            }
        }

        init {
            setBackgroundColor(Color.rgb(42, 82, 48))
            isClickable = true
        }

        fun setMode(value: Int) {
            mode = value.coerceIn(0, 3)
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

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    val zone = zoneAt(
                        event.x / width.coerceAtLeast(1),
                        event.y / height.coerceAtLeast(1)
                    )
                    onSelection?.invoke(zone)
                    invalidate()
                    performClick()
                    return true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun zoneAt(nx: Float, ny: Float): String = when {
            nx > .78f -> "River District — fishing water and the eastern trade route."
            nx < .30f && ny < .48f ->
                "Farm District — \${game.unlockedPlots} plots, \${game.farmers} farmer(s)."
            nx < .24f && ny > .56f ->
                "West Woods — \${game.lumber * (2 + game.sawmill)} wood/day."
            nx > .61f && ny < .38f ->
                "Stone Ridge — quarry Lv\${game.quarry}, \${game.miners} miner(s), ore \${game.ore}."
            nx > .52f && ny > .60f ->
                "Market Ward — market Lv\${game.market}, coins \${game.coins}."
            nx > .28f && nx < .62f && ny > .43f ->
                "Village Core — pop \${game.population}/\${game.housing}, happiness \${game.happiness}%."
            else ->
                "Homestead — \${game.seasonName}, \${game.weather()}, Colony Lv\${game.level}."
        }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            drawGround(c, w, h)
            drawRiver(c, w, h)
            drawRoads(c, w, h)
            drawForest(c, w, h)
            drawFields(c, w, h)
            drawSettlement(c, w, h)
            drawWorkers(c, w, h)
            drawMapMode(c, w, h)
            drawWeather(c, w, h)
            drawHud(c, w, h)
        }

        private fun drawGround(c: Canvas, w: Float, h: Float) {
            val base = when (game.season) {
                0 -> Color.rgb(94, 143, 75)
                1 -> Color.rgb(83, 133, 64)
                2 -> Color.rgb(139, 116, 66)
                else -> Color.rgb(159, 166, 154)
            }
            c.drawColor(base)

            val cols = 16
            val rows = if (compact) 8 else 11
            val tw = w / cols
            val th = h / rows
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    val checker = (x + y + game.day) and 3
                    pixel.color = when (game.season) {
                        0 -> if (checker == 0) Color.rgb(103, 153, 82) else Color.rgb(94, 143, 75)
                        1 -> if (checker == 0) Color.rgb(90, 143, 67) else Color.rgb(83, 133, 64)
                        2 -> if (checker == 0) Color.rgb(149, 124, 70) else Color.rgb(139, 116, 66)
                        else -> if (checker == 0) Color.rgb(171, 178, 167) else Color.rgb(159, 166, 154)
                    }
                    c.drawRect(x * tw, y * th, (x + 1) * tw + 1f, (y + 1) * th + 1f, pixel)
                }
            }

            if (!compact) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1f
                p.color = Color.argb(55, 35, 55, 30)
                for (x in 1 until cols) c.drawLine(x * tw, 0f, x * tw, h, p)
                for (y in 1 until rows) c.drawLine(0f, y * th, w, y * th, p)
                p.style = Paint.Style.FILL
            }
        }

        private fun drawRiver(c: Canvas, w: Float, h: Float) {
            val path = Path()
            path.moveTo(w * .79f, -4f)
            path.cubicTo(w * .73f, h * .22f, w * .86f, h * .47f, w * .79f, h * .69f)
            path.cubicTo(w * .75f, h * .82f, w * .82f, h * .92f, w * .78f, h + 4f)
            path.lineTo(w + 4f, h + 4f)
            path.lineTo(w + 4f, -4f)
            path.close()
            p.color = Color.rgb(57, 106, 150)
            c.drawPath(path, p)

            p.style = Paint.Style.STROKE
            p.strokeWidth = if (compact) 1.5f else 2.5f
            p.color = Color.argb(125, 143, 192, 222)
            val offset = (frame % 12).toFloat()
            var y = -12f + offset
            while (y < h) {
                c.drawLine(w * .82f, y, w * .97f, y + 4f, p)
                y += if (compact) 15f else 20f
            }
            p.style = Paint.Style.FILL

            if (!compact) {
                drawLabel(c, "EAST RIVER", w * .855f, h * .18f, Color.WHITE)
                p.color = Color.rgb(117, 87, 53)
                c.drawRect(w * .74f, h * .52f, w * .86f, h * .55f, p)
                c.drawRect(w * .77f, h * .49f, w * .79f, h * .58f, p)
                c.drawRect(w * .82f, h * .49f, w * .84f, h * .58f, p)
            }
        }

        private fun drawRoads(c: Canvas, w: Float, h: Float) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = if (compact) 7f else 12f
            p.color = if (game.season == 3) Color.rgb(173, 160, 133) else Color.rgb(157, 132, 91)
            c.drawLine(w * .12f, h * .66f, w * .68f, h * .61f, p)
            c.drawLine(w * .45f, h * .34f, w * .45f, h * .76f, p)
            if (game.market > 0) c.drawLine(w * .60f, h * .61f, w * .79f, h * .54f, p)
            if (game.quarry > 0) c.drawLine(w * .48f, h * .48f, w * .69f, h * .24f, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        }

        private fun drawForest(c: Canvas, w: Float, h: Float) {
            val trees = if (compact) 8 else 18
            for (i in 0 until trees) {
                val x = w * (.03f + ((i * 37) % 23) / 100f)
                val y = h * (.50f + ((i * 19) % 36) / 100f)
                val size = if (compact) 5f else 8f
                p.color = Color.rgb(73, 48, 29)
                c.drawRect(x - 1.5f, y + size * .45f, x + 1.5f, y + size * 1.15f, p)
                p.color = when (game.season) {
                    2 -> if (i % 2 == 0) Color.rgb(159, 91, 45) else Color.rgb(177, 123, 48)
                    3 -> Color.rgb(70, 91, 70)
                    else -> if (i % 2 == 0) Color.rgb(38, 94, 46) else Color.rgb(48, 112, 50)
                }
                c.drawCircle(x, y, size, p)
            }
            if (!compact) drawLabel(c, "WEST WOODS", w * .13f, h * .91f, Color.WHITE)
        }

        private fun drawFields(c: Canvas, w: Float, h: Float) {
            val left = w * .035f
            val top = h * .06f
            val zoneW = w * .49f
            val zoneH = h * .38f
            val gap = if (compact) 3f else 5f
            val cellW = (zoneW - gap * 3) / 4f
            val cellH = (zoneH - gap) / 2f
            for (i in 0 until game.unlockedPlots) {
                val col = i % 4
                val row = i / 4
                val l = left + col * (cellW + gap)
                val t = top + row * (cellH + gap)
                val r = l + cellW
                val b = t + cellH
                p.color = Color.rgb(105, 75, 45)
                c.drawRect(l, t, r, b, p)
                p.color = Color.rgb(78, 55, 34)
                for (line in 1..3) {
                    val yy = t + line * (cellH / 4f)
                    c.drawRect(l + 2f, yy, r - 2f, yy + 1f, p)
                }

                val plot = game.plots[i]
                val crop = cropMap[plot.crop]
                if (crop != null) {
                    val progress = min(1f, plot.age.toFloat() / crop.days.toFloat())
                    val rows = 2 + (progress * 3f).toInt()
                    p.color = when {
                        progress >= 1f -> Color.rgb(225, 200, 72)
                        crop.id == "tomato" -> Color.rgb(57, 143, 63)
                        crop.id == "pumpkin" -> Color.rgb(104, 151, 48)
                        else -> Color.rgb(71, 154, 61)
                    }
                    repeat(rows) { n ->
                        val px = l + 5f + (n % 3) * max(4f, (cellW - 10f) / 3f)
                        val py = b - 5f - (n / 3) * max(5f, cellH / 3f)
                        c.drawRect(
                            px, py,
                            px + if (compact) 2f else 4f,
                            py + if (compact) 3f else 6f,
                            p
                        )
                    }
                    if (!compact && progress >= 1f) {
                        drawLabel(c, "READY", (l + r) / 2f, b - 3f, Color.rgb(55, 35, 0))
                    }
                }
            }
            if (!compact) drawLabel(c, "FARM DISTRICT", left + zoneW * .5f, top + zoneH + 15f, Color.WHITE)
        }

        private fun drawSettlement(c: Canvas, w: Float, h: Float) {
            drawBuilding(c, w * .34f, h * .57f, w * .10f, h * .13f, "HOME", Color.rgb(212, 197, 157))
            val extraHomes = min(4, game.cottages)
            repeat(extraHomes) { i ->
                drawBuilding(
                    c,
                    w * (.45f + (i % 2) * .105f),
                    h * (.66f + (i / 2) * .13f),
                    w * .085f,
                    h * .105f,
                    "",
                    Color.rgb(202, 187, 149)
                )
            }

            if (game.barn > 0) drawBuilding(c, w * .19f, h * .48f, w * .12f, h * .15f, "BARN " + game.barn, Color.rgb(161, 72, 55))
            if (game.workshop > 0) drawBuilding(c, w * .47f, h * .48f, w * .11f, h * .14f, "SHOP " + game.workshop, Color.rgb(121, 125, 128))
            if (game.market > 0) drawBuilding(c, w * .62f, h * .59f, w * .11f, h * .13f, "MKT " + game.market, Color.rgb(190, 151, 72))
            if (game.granary > 0) drawTower(c, w * .57f, h * .38f, "G" + game.granary, Color.rgb(189, 169, 112))
            if (game.sawmill > 0) drawBuilding(c, w * .08f, h * .58f, w * .10f, h * .12f, "SAW " + game.sawmill, Color.rgb(139, 101, 62))
            if (game.quarry > 0) drawQuarry(c, w * .63f, h * .14f, w * .13f, h * .18f)
            if (game.greenhouse) drawGreenhouse(c, w * .55f, h * .11f, w * .10f, h * .13f)

            if (game.barn > 0) {
                repeat(min(8, game.chickens + game.cows + game.sheep)) { i ->
                    p.color = when {
                        i < game.cows -> Color.rgb(238, 233, 210)
                        i < game.cows + game.sheep -> Color.rgb(230, 230, 220)
                        else -> Color.rgb(215, 177, 65)
                    }
                    val ax = w * .21f + (i % 4) * (if (compact) 4f else 7f)
                    val ay = h * .65f + (i / 4) * (if (compact) 4f else 7f)
                    c.drawRect(
                        ax, ay,
                        ax + if (compact) 2f else 4f,
                        ay + if (compact) 2f else 4f,
                        p
                    )
                }
            }
            if (!compact) drawLabel(c, "VILLAGE CORE", w * .47f, h * .90f, Color.WHITE)
        }

        private fun drawBuilding(
            c: Canvas,
            x: Float,
            y: Float,
            bw: Float,
            bh: Float,
            label: String,
            body: Int
        ) {
            p.color = Color.argb(70, 0, 0, 0)
            c.drawRect(x + 3f, y + 4f, x + bw + 4f, y + bh + 5f, p)
            p.color = body
            c.drawRect(x, y, x + bw, y + bh, p)
            p.color = Color.rgb(86, 49, 37)
            val roof = Path()
            roof.moveTo(x - 3f, y)
            roof.lineTo(x + bw * .5f, y - bh * .33f)
            roof.lineTo(x + bw + 3f, y)
            roof.close()
            c.drawPath(roof, p)
            p.color = Color.rgb(63, 42, 31)
            c.drawRect(x + bw * .42f, y + bh * .55f, x + bw * .61f, y + bh, p)
            if (label.isNotEmpty() && !compact) {
                drawLabel(c, label, x + bw * .5f, y + bh + 11f, Color.WHITE)
            }
        }

        private fun drawTower(c: Canvas, x: Float, y: Float, label: String, color: Int) {
            p.color = color
            c.drawRect(x, y, x + 22f, y + 35f, p)
            p.color = Color.rgb(100, 66, 43)
            c.drawRect(x - 3f, y - 5f, x + 25f, y + 2f, p)
            if (!compact) drawLabel(c, label, x + 11f, y + 23f, Color.BLACK)
        }

        private fun drawQuarry(c: Canvas, x: Float, y: Float, qw: Float, qh: Float) {
            val rings = if (compact) 3 else 5
            repeat(rings) { i ->
                val inset = i * (if (compact) 3f else 5f)
                p.color = if (i % 2 == 0) Color.rgb(120, 120, 114) else Color.rgb(88, 90, 88)
                c.drawOval(
                    android.graphics.RectF(
                        x + inset, y + inset,
                        x + qw - inset, y + qh - inset
                    ),
                    p
                )
            }
            if (!compact) drawLabel(c, "STONE RIDGE", x + qw * .5f, y + qh + 12f, Color.WHITE)
        }

        private fun drawGreenhouse(c: Canvas, x: Float, y: Float, gw: Float, gh: Float) {
            p.color = Color.argb(150, 178, 225, 210)
            c.drawRect(x, y, x + gw, y + gh, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.color = Color.rgb(220, 245, 238)
            c.drawRect(x, y, x + gw, y + gh, p)
            c.drawLine(x, y, x + gw * .5f, y - gh * .25f, p)
            c.drawLine(x + gw, y, x + gw * .5f, y - gh * .25f, p)
            p.style = Paint.Style.FILL
        }

        private fun drawWorkers(c: Canvas, w: Float, h: Float) {
            val count = min(if (compact) 6 else 14, game.population)
            repeat(count) { i ->
                val phase = (frame + i * 17) % 100
                val route = i % 4
                val t = phase / 100f
                val x: Float
                val y: Float
                when (route) {
                    0 -> { x = w * (.33f + .30f * t); y = h * (.60f - .05f * t) }
                    1 -> { x = w * (.44f - .28f * t); y = h * (.57f + .14f * t) }
                    2 -> { x = w * (.43f + .26f * t); y = h * (.55f - .29f * t) }
                    else -> { x = w * (.36f + .19f * t); y = h * (.69f + .08f * t) }
                }
                p.color = Color.rgb(42, 37, 33)
                val size = if (compact) 2.2f else 4.2f
                c.drawCircle(x, y - size, size * .45f, p)
                p.color = when (i % 4) {
                    0 -> Color.rgb(70, 87, 150)
                    1 -> Color.rgb(126, 74, 53)
                    2 -> Color.rgb(66, 110, 64)
                    else -> Color.rgb(130, 91, 130)
                }
                c.drawRect(x - size * .45f, y - size * .4f, x + size * .45f, y + size, p)
            }
        }

        private fun drawMapMode(c: Canvas, w: Float, h: Float) {
            if (compact || mode == 0) return
            when (mode) {
                1 -> {
                    p.color = Color.argb(54, 44, 210, 68)
                    c.drawRect(0f, 0f, w * .55f, h * .48f, p)
                    p.color = Color.argb(48, 225, 183, 52)
                    c.drawRect(w * .54f, h * .42f, w * .77f, h * .82f, p)
                }
                2 -> {
                    p.color = Color.argb(58, 255, 207, 59)
                    val strength = min(1f, (game.coins + game.wood + game.stone).toFloat() / 250f)
                    c.drawCircle(w * .56f, h * .61f, w * (.12f + .13f * strength), p)
                    p.color = Color.argb(55, 129, 129, 129)
                    c.drawCircle(w * .69f, h * .23f, w * .10f, p)
                }
                3 -> {
                    p.color = Color.argb(64, 59, 112, 230)
                    val radius = w * (.10f + min(.18f, game.population * .012f))
                    c.drawCircle(w * .46f, h * .64f, radius, p)
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 3f
                    p.color = Color.argb(140, 255, 255, 255)
                    c.drawCircle(w * .46f, h * .64f, radius, p)
                    p.style = Paint.Style.FILL
                }
            }
        }

        private fun drawWeather(c: Canvas, w: Float, h: Float) {
            when (game.weather()) {
                "Rain" -> {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = if (compact) 1f else 2f
                    p.color = Color.argb(115, 197, 222, 241)
                    repeat(if (compact) 14 else 34) { i ->
                        val x = ((i * 47 + frame * 9) % max(1, width)).toFloat()
                        val y = ((i * 31 + frame * 13) % max(1, height)).toFloat()
                        c.drawLine(x, y, x - 5f, y + 11f, p)
                    }
                    p.style = Paint.Style.FILL
                }
                "Snow" -> {
                    p.color = Color.argb(180, 245, 245, 245)
                    repeat(if (compact) 13 else 30) { i ->
                        val x = ((i * 53 + frame * 4) % max(1, width)).toFloat()
                        val y = ((i * 29 + frame * 7) % max(1, height)).toFloat()
                        c.drawCircle(x, y, if (compact) 1.5f else 2.4f, p)
                    }
                }
                "Heat" -> {
                    p.color = Color.argb(34, 255, 204, 67)
                    c.drawRect(0f, 0f, w, h, p)
                }
            }
        }

        private fun drawHud(c: Canvas, w: Float, h: Float) {
            if (compact) {
                p.color = Color.argb(205, 244, 242, 214)
                c.drawRect(4f, h - 19f, w - 4f, h - 4f, p)
                p.color = Color.rgb(25, 25, 25)
                p.typeface = Typeface.MONOSPACE
                p.textSize = 8.5f * resources.displayMetrics.density
                c.drawText(game.lastEvent.take(40), 8f, h - 8f, p)
                return
            }

            p.color = Color.argb(195, 21, 27, 34)
            c.drawRect(0f, 0f, w, 28f, p)
            drawLabel(
                c,
                when (mode) {
                    1 -> "MAP MODE: AGRICULTURE"
                    2 -> "MAP MODE: ECONOMY"
                    3 -> "MAP MODE: POPULATION"
                    else -> "MAP MODE: TERRAIN"
                },
                w * .5f,
                19f,
                Color.WHITE
            )
        }

        private fun drawLabel(c: Canvas, text: String, x: Float, y: Float, color: Int) {
            p.color = color
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = (if (compact) 7.5f else 9f) * resources.displayMetrics.density
            p.setShadowLayer(2f, 1f, 1f, Color.argb(180, 0, 0, 0))
            c.drawText(text, x, y, p)
            p.clearShadowLayer()
            p.textAlign = Paint.Align.LEFT
        }
    }

    private fun showExpanded(activity: MainActivity, game: Game, changed: () -> Unit) {
        game.catchUp()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = inset(activity, Color.rgb(192, 192, 192))
        }

        val header = TextView(activity).apply {
            setTextColor(Color.BLACK)
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            setPadding(dp(activity, 6), dp(activity, 5), dp(activity, 6), dp(activity, 5))
            background = inset(activity, Color.WHITE)
        }
        root.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val selection = TextView(activity).apply {
            text = "Village Core — tap anywhere on the map to inspect a district."
            setTextColor(Color.BLACK)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(dp(activity, 6), dp(activity, 4), dp(activity, 6), dp(activity, 4))
            background = inset(activity, Color.rgb(236, 233, 222))
            maxLines = 2
        }

        lateinit var render: () -> Unit
        var tab = 0

        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                body,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val map = MapView(activity, game, compact = false) { zone ->
            selection.text = zone
        }
        root.addView(
            map,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, 286)
            )
        )
        root.addView(
            selection,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, 42)
            )
        )

        val mapModes = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(activity, 3), 0, dp(activity, 3))
        }
        arrayOf("Terrain", "Farm", "Economy", "People").forEachIndexed { i, label ->
            mapModes.addView(
                button(activity, label) { map.setMode(i) },
                LinearLayout.LayoutParams(0, dp(activity, 27), 1f).apply {
                    if (i > 0) marginStart = dp(activity, 2)
                }
            )
        }
        root.addView(
            mapModes,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, 33)
            )
        )

        val tabs = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(activity, 2), 0, dp(activity, 4))
        }

        fun toast(value: String) =
            android.widget.Toast.makeText(activity, value, android.widget.Toast.LENGTH_SHORT).show()

        fun section(value: String) = TextView(activity).apply {
            text = value
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(0, 0, 128))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 11.5f
            setPadding(dp(activity, 6), dp(activity, 4), dp(activity, 6), dp(activity, 4))
        }

        fun line(value: String) = TextView(activity).apply {
            text = value
            setTextColor(Color.BLACK)
            textSize = 10.5f
            setPadding(dp(activity, 5), dp(activity, 4), dp(activity, 5), dp(activity, 4))
        }

        fun action(
            title: String,
            detail: String,
            label: String,
            work: () -> String
        ): View {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(activity, 4), dp(activity, 3), dp(activity, 4), dp(activity, 3))
            }
            row.addView(
                TextView(activity).apply {
                    text = title + "\n" + detail
                    setTextColor(Color.BLACK)
                    textSize = 10f
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
            row.addView(
                button(activity, label) {
                    toast(work())
                    render()
                    map.invalidate()
                    changed()
                },
                LinearLayout.LayoutParams(dp(activity, 76), dp(activity, 29))
            )
            return row
        }

        fun jobRow(title: String, id: String, count: () -> Int): View {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(activity, 4), dp(activity, 3), dp(activity, 4), dp(activity, 3))
            }
            row.addView(
                TextView(activity).apply {
                    text = title + ": " + count()
                    setTextColor(Color.BLACK)
                    textSize = 10.5f
                },
                LinearLayout.LayoutParams(0, dp(activity, 29), 1f)
            )
            row.addView(
                button(activity, "-") {
                    toast(game.job(id, -1))
                    render()
                    changed()
                },
                LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 27)).apply {
                    marginEnd = dp(activity, 3)
                }
            )
            row.addView(
                button(activity, "+") {
                    toast(game.job(id, 1))
                    render()
                    changed()
                },
                LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 27))
            )
            return row
        }

        fun overview() {
            body.addView(section("COLONY COMMAND"))
            body.addView(line(game.goal()))
            body.addView(line("Next day ~" + game.nextDayMinutes() + " min | Offline catch-up: " + OFFLINE_DAY_CAP + " days"))
            body.addView(line("Latest: " + game.lastEvent))
            body.addView(section("QUICK ORDERS"))
            body.addView(action("Harvest all", "Collect every mature field", "Harvest") { game.harvest() })
            body.addView(action("Send foragers", "4-minute manual expedition cooldown", "Forage") { game.forage() })
            body.addView(action("Sell food", "10 food -> " + (6 + game.market * 2) + " coins", "Sell") { game.sellFood() })
            if (game.preservation) {
                body.addView(action("Preserve surplus", "15 food -> valuable trade goods", "Make") { game.preserveFood() })
            }
            body.addView(section("PRODUCTION"))
            body.addView(line("Farmers cover " + game.farmers * 3 + " plots/day" + if (game.irrigation) " (Irrigated: all plots)" else ""))
            body.addView(line("Wood +" + game.lumber * (2 + game.sawmill) + "/day | Stone +" + game.miners * (1 + game.quarry) + "/day | Tech +" + game.scientists * (1 + max(0, game.workshop - 1)) + "/day"))
        }

        fun farm() {
            body.addView(section("FARMLAND  " + game.unlockedPlots + "/" + game.plots.size + " PLOTS"))
            for (i in game.plots.indices) body.addView(line(game.plotText(i)))
            body.addView(action("Harvest ready fields", "Clears mature plots and adds food/coin", "Harvest") { game.harvest() })
            body.addView(section("SEED CATALOG — " + game.seasonName.uppercase()))
            game.availableCrops().forEach { crop ->
                val seasons = crop.seasons.joinToString("/") {
                    arrayOf("Sp", "Su", "Fa", "Wi")[it]
                }
                body.addView(
                    action(
                        crop.name,
                        crop.cost.toString() + "c | " + crop.days + "d | +" + crop.food + " food | " + seasons,
                        "Plant"
                    ) { game.plant(crop.id) }
                )
            }
            body.addView(section("RANCH  " + (game.chickens + game.cows + game.sheep) + "/" + game.animalCap))
            body.addView(line("Chicken " + game.chickens + " | Cow " + game.cows + " | Sheep " + game.sheep + " | Ranchers " + game.ranchers))
            body.addView(action("Chicken", "18c | +1 food/day", "Buy") { game.buyAnimal("chicken") })
            body.addView(action("Sheep", "34c | Barn Lv2 | wool income", "Buy") { game.buyAnimal("sheep") })
            body.addView(action("Cow", "42c | Barn Lv2 | +2 food/day", "Buy") { game.buyAnimal("cow") })
        }

        fun build() {
            body.addView(section("SETTLEMENT CONSTRUCTION"))
            body.addView(line("Buildings appear directly on the map as the colony develops."))
            body.addView(action("Cottage", "Housing +2; adds another house to Village Core", "Build") { game.build("house") })
            body.addView(action("Barn Lv" + game.barn, "Animal capacity +4/level; opens ranching", "Build") { game.build("barn") })
            body.addView(action("Sawmill Lv" + game.sawmill, "Improves West Woods lumber output", "Build") { game.build("sawmill") })
            body.addView(action("Quarry Lv" + game.quarry, "Develops Stone Ridge; stone + ore", "Build") { game.build("quarry") })
            body.addView(action("Workshop Lv" + game.workshop, "Unlocks researchers and advanced farming", "Build") { game.build("workshop") })
            body.addView(action("Granary Lv" + game.granary, "Raises storage and passive food surplus", "Build") { game.build("granary") })
            body.addView(action("Market Lv" + game.market, "Builds Market Ward and improves trade", "Build") { game.build("market") })
        }

        fun people() {
            body.addView(section("POPULATION & LABOR"))
            body.addView(line("Settlers " + game.population + "/" + game.housing + " | Assigned " + game.assigned + " | Free " + game.free + " | Happiness " + game.happiness + "%"))
            body.addView(jobRow("Farmers", "farmer") { game.farmers })
            body.addView(jobRow("Lumberjacks", "lumber") { game.lumber })
            body.addView(jobRow("Miners", "miner") { game.miners })
            body.addView(jobRow("Researchers", "science") { game.scientists })
            body.addView(jobRow("Ranchers", "rancher") { game.ranchers })
            body.addView(section("MIGRATION"))
            body.addView(line("Spare housing, stored food and good morale attract new settlers. Repeated shortages can make colonists leave."))
            body.addView(line("Food warning: " + game.starvation + "/4 | Housing spare: " + max(0, game.housing - game.population)))
        }

        fun tech() {
            body.addView(section("RESEARCH — " + game.tech + " POINTS"))
            body.addView(action("Irrigation", "25 tech | Workshop Lv1 | every field grows daily", if (game.irrigation) "Done" else "Research") { game.research("irrigation") })
            body.addView(action("Crop Rotation", "40 tech | Workshop Lv1 | harvest bonus", if (game.rotation) "Done" else "Research") { game.research("rotation") })
            body.addView(action("Deep Mining", "60 tech | Quarry Lv1 | improved ore output", if (game.deepMining) "Done" else "Research") { game.research("deep") })
            body.addView(action("Food Preservation", "55 tech | Granary Lv1 | profitable surplus", if (game.preservation) "Done" else "Research") { game.research("preserve") })
            body.addView(action("Animal Care", "70 tech | Barn Lv2 | livestock morale bonus", if (game.animalCare) "Done" else "Research") { game.research("animals") })
            body.addView(action("Greenhouse", "90 tech | Workshop Lv2 | any crop, any season", if (game.greenhouse) "Done" else "Research") { game.research("greenhouse") })
            body.addView(section("COLONY CHRONICLE"))
            if (game.log.isEmpty()) body.addView(line("No major events recorded yet."))
            game.log.forEach { body.addView(line(it)) }
        }

        render = {
            game.catchUp()
            header.text =
                "Y" + game.year + " " + game.seasonName + " " + game.seasonDay + "  " + game.weather() +
                    "   COLONY LV " + game.level + "\n" +
                    "POP " + game.population + "/" + game.housing + "  HAPPY " + game.happiness + "%  REP " + game.reputation +
                    "   $" + game.coins + "\n" +
                    "FOOD " + game.food + "  WOOD " + game.wood + "  STONE " + game.stone +
                    "  ORE " + game.ore + "  TECH " + game.tech
            body.removeAllViews()
            when (tab) {
                0 -> overview()
                1 -> farm()
                2 -> build()
                3 -> people()
                else -> tech()
            }
            map.invalidate()
        }

        arrayOf("Command", "Farm", "Build", "People", "Tech").forEachIndexed { i, label ->
            tabs.addView(
                button(activity, label) {
                    tab = i
                    render()
                },
                LinearLayout.LayoutParams(0, dp(activity, 29), 1f).apply {
                    if (i > 0) marginStart = dp(activity, 2)
                }
            )
        }
        root.addView(
            tabs,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, 35)
            )
        )
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, 205)
            )
        )

        render()
        Win98Dialogs.showCustom(
            context = activity,
            title = "Homestead Colony — Strategic Map",
            content = root,
            positiveText = "Close",
            negativeText = null,
            onPositive = {
                game.save()
                changed()
            }
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
