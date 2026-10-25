package rocks.gorjan.gokixp.apps.notepad

data class Note(
    val id: String,
    var title: String,
    var content: String,
    var imageUris: MutableList<String> = mutableListOf(),  // List of permanent URI strings
    var isArchived: Boolean = false, // Archive status
    var category: String = "day_to_day",
    var reminderAt: Long = 0L,
    var reminderSubject: String = ""
)
