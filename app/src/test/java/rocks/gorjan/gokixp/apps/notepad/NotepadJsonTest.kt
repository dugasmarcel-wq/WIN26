package rocks.gorjan.gokixp.apps.notepad

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class NotepadJsonTest {
    private val original = """[{"id":"old","title":"Trip","content":"Existing personal note","category":"day_to_day","reminderAt":123456,"reminderSubject":"Subject","imageUris":["content://image"],"future":{"keep":true}}]"""

    @Test fun editingAnOlderNoteNeverCreatesOrRenamesAnotherNote() {
        val before = NotepadJson.read(original)
        val edited = NotepadJson.snapshot(before).toMutableList()
        edited[0].content += "\nMore text"
        val raw = NotepadJson.merge(original, before, edited)
        val notes = NotepadJson.read(raw)
        assertEquals(1, notes.size)
        assertEquals("old", notes[0].id)
        assertEquals("Trip", notes[0].title)
        assertEquals("Existing personal note\nMore text", notes[0].content)
        val row = JsonParser.parseString(raw).asJsonArray[0].asJsonObject
        assertTrue(row["future"].asJsonObject["keep"].asBoolean)
        assertEquals(123456L, notes[0].reminderAt)
        assertEquals(listOf("content://image"), notes[0].imageUris)
    }

    @Test fun addCreatesImmediateLocalDateNamesWithIndependentIds() {
        val a = NotepadJson.newNote("day_to_day")
        val b = NotepadJson.newNote("day_to_day")
        assertEquals(java.text.SimpleDateFormat("yyyyMMMd", java.util.Locale.ENGLISH).format(java.util.Date()), a.title)
        assertEquals(a.title, b.title)
        assertNotEquals(a.id, b.id)
        assertEquals("", a.content)
    }

    @Test fun staleEditorPreservesAnotherSurfacesNewNoteAndReminderChanges() {
        val before = NotepadJson.read(original)
        val other = NotepadJson.snapshot(before).toMutableList()
        other[0].reminderSubject = "Changed elsewhere"
        other[0].reminderAt = 999999L
        val added = NotepadJson.newNote("reminder_notes")
        other.add(added)
        val newer = NotepadJson.merge(original, before, other)
        val stale = NotepadJson.snapshot(before).toMutableList()
        stale[0].content = "Edited in widget"
        val merged = NotepadJson.read(NotepadJson.merge(newer, before, stale))
        assertEquals(2, merged.size)
        assertEquals("Edited in widget", merged[0].content)
        assertEquals("Changed elsewhere", merged[0].reminderSubject)
        assertEquals(999999L, merged[0].reminderAt)
        assertEquals(added.id, merged[1].id)
    }

    @Test fun staleEditorDoesNotResurrectDeletedNotes() {
        val before = NotepadJson.read(original)
        val stale = NotepadJson.snapshot(before).toMutableList()
        stale[0].content = "late edit"
        assertTrue(NotepadJson.read(NotepadJson.merge("[]", before, stale)).isEmpty())
    }

    @Test fun categoriesAndRenamesPersistWithoutDroppingUnknownFields() {
        val before = NotepadJson.read(original)
        val edited = NotepadJson.snapshot(before).toMutableList()
        edited[0].title = "Renamed"
        for (category in listOf("day_to_day", "long_term", "reminder_notes")) edited.add(NotepadJson.newNote(category))
        val raw = NotepadJson.merge(original, before, edited)
        assertEquals("Renamed", NotepadJson.read(raw)[0].title)
        assertEquals(4, NotepadJson.read(raw).size)
        assertTrue(JsonParser.parseString(raw).asJsonArray[0].asJsonObject.has("future"))
    }

    @Test(expected = Exception::class) fun corruptStorageIsRejectedInsteadOfReset() {
        NotepadJson.merge("{broken", emptyList(), listOf(NotepadJson.newNote("day_to_day")))
    }

    @Test fun oldRecordsWithoutOptionalFieldsStayReadable() {
        val notes = NotepadJson.read("""[{"id":"legacy","title":"Old","content":"Keep me","imageUris":null}]""")
        assertEquals("day_to_day", notes[0].category)
        assertTrue(notes[0].imageUris.isEmpty())
        assertEquals("Keep me", notes[0].content)
    }
}
