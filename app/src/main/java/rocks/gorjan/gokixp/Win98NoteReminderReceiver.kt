package rocks.gorjan.gokixp

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import rocks.gorjan.gokixp.apps.notepad.Note

/** Local note reminders; no network service or polling. */
class Win98NoteReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("note_id") ?: return
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val notes = try {
            val type = object : TypeToken<MutableList<Note>>() {}.type
            Gson().fromJson<MutableList<Note>>(prefs.getString("notepad_notes", "[]"), type)
        } catch (_: Exception) { null } ?: return
        val note = notes.firstOrNull { it.id == id && !it.isArchived && it.reminderAt > 0 } ?: return
        val now = System.currentTimeMillis()
        val due = note.reminderAt
        if (now < due) { schedule(context, id, note.reminderSubject, due); return }
        val elapsed = (now - due) / 3600000L
        if (elapsed >= 24L) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "win26_notes_hourly"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                channel, "Note reminders", NotificationManager.IMPORTANCE_HIGH
            ).apply { enableVibration(true) })
        }
        if (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val open = PendingIntent.getActivity(context, id.hashCode(),
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(id.hashCode(), NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(note.reminderSubject.ifBlank { note.title })
                .setContentText(note.content.take(180).ifBlank { "Notepad reminder" })
                .setContentIntent(open).setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_SOUND)
                .build())
        }
        val next = due + (elapsed + 1L) * 3600000L
        if (next < due + 24L * 3600000L) schedule(context, id, note.reminderSubject, next)
    }

    companion object {
        fun schedule(context: Context, id: String, title: String, at: Long) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = PendingIntent.getBroadcast(context, id.hashCode(),
                Intent(context, Win98NoteReminderReceiver::class.java)
                    .putExtra("note_id", id).putExtra("note_title", title),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarm.cancel(pending)
            if (at > System.currentTimeMillis())
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }
}
