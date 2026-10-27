package rocks.gorjan.gokixp.apps.notepad

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Field-level updates preserve unknown JSON properties and changes from another open editor. */
internal object NotepadJson {
    private val gson = Gson()

    private fun document(json: String?): JsonArray {
        if (json == null) return JsonArray()
        val parsed = JsonParser.parseString(json)
        require(parsed.isJsonArray) { "Invalid notes database" }
        return parsed.asJsonArray
    }

    private fun string(row: JsonObject, key: String, fallback: String = ""): String =
        row.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString ?: fallback

    fun read(json: String?): MutableList<Note> = document(json).mapNotNull { value ->
        if (!value.isJsonObject) return@mapNotNull null
        val row = value.asJsonObject
        val id = string(row, "id")
        if (id.isEmpty()) return@mapNotNull null
        Note(id, string(row, "title"), string(row, "content"),
            row.get("imageUris")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.map { it.asString }?.toMutableList() ?: mutableListOf(),
            row.get("isArchived")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                ?.asBoolean ?: false,
            string(row, "category", "day_to_day"),
            row.get("reminderAt")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asLong ?: 0L,
            string(row, "reminderSubject"))
    }.toMutableList()

    fun snapshot(notes: List<Note>): List<Note> = notes.map { it.copy(imageUris = it.imageUris.toMutableList()) }

    fun newNote(category: String, archived: Boolean = false): Note = Note(
        UUID.randomUUID().toString(), SimpleDateFormat("yyyyMMMd", Locale.ENGLISH).format(Date()),
        "", isArchived = archived, category = category)

    fun merge(json: String?, before: List<Note>, after: List<Note>): String {
        val source = document(json)
        val previous = before.associateBy { it.id }
        val edited = after.associateBy { it.id }
        val result = JsonArray()
        val seen = mutableSetOf<String>()
        for (value in source) {
            if (!value.isJsonObject) { result.add(value); continue }
            val row = value.asJsonObject
            val id = string(row, "id")
            seen.add(id)
            val old = previous[id]
            val next = edited[id]
            if (old != null && next == null) continue // explicit deletion only
            if (old != null && next != null) {
                val oldFields = gson.toJsonTree(old).asJsonObject
                val newFields = gson.toJsonTree(next).asJsonObject
                for ((key, field) in newFields.entrySet()) {
                    if (field != oldFields.get(key)) row.add(key, field)
                }
            }
            result.add(row)
        }
        for (note in after) {
            // A stale surface must never resurrect a note deleted elsewhere.
            if (note.id !in seen && note.id !in previous) result.add(gson.toJsonTree(note))
        }
        val oldOrder = before.map { it.id }.filter { it in edited }
        val newOrder = after.map { it.id }.filter { it in previous }
        if (oldOrder != newOrder) {
            val byId = result.filter { it.isJsonObject }
                .associateBy { string(it.asJsonObject, "id") }
            val ordered = newOrder.mapNotNull { byId[it] }.iterator()
            val reordered = JsonArray()
            for (row in result) {
                val id = if (row.isJsonObject) string(row.asJsonObject, "id") else ""
                reordered.add(if (id in newOrder && ordered.hasNext()) ordered.next() else row)
            }
            return gson.toJson(reordered)
        }
        return gson.toJson(result)
    }
}

internal class NotepadStore(private val prefs: SharedPreferences) {
    companion object {
        const val KEY = "notepad_notes"
        private val lock = Any()
    }

    fun load(): MutableList<Note> = synchronized(lock) {
        val raw = prefs.getString(KEY, null)
        val notes = NotepadJson.read(raw) // malformed data is never replaced
        if (notes.isEmpty() && raw == null) {
            val first = NotepadJson.newNote("day_to_day")
            first.content = prefs.getString("notepad_content", "").orEmpty()
            val json = NotepadJson.merge(null, emptyList(), listOf(first))
            prefs.edit().putString(KEY, json).remove("notepad_content").apply()
            mutableListOf(first)
        } else notes
    }

    fun save(before: List<Note>, after: List<Note>): MutableList<Note> = synchronized(lock) {
        val merged = NotepadJson.merge(prefs.getString(KEY, null), before, after)
        prefs.edit().putString(KEY, merged).apply()
        NotepadJson.read(merged)
    }
}
