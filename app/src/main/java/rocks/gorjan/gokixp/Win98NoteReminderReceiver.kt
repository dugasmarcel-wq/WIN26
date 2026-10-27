package rocks.gorjan.gokixp

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import rocks.gorjan.gokixp.apps.notepad.NotepadJson
import rocks.gorjan.gokixp.apps.notepad.ReminderSchedule

/** Local alarms only. Inexact idle alarms remain subject to Android's delivery limits. */
class Win98NoteReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            restore(context)
            return
        }
        val id = intent.getStringExtra("note_id") ?: return
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val notes = try { NotepadJson.read(prefs.getString("notepad_notes", null)) } catch (_: Exception) { return }
        val note = notes.firstOrNull { it.id == id && !it.isArchived && it.reminderAt > 0L }
        if (note == null) { cancel(context, id); return }
        val now = System.currentTimeMillis()
        val due = note.reminderAt
        if (now < due) { schedule(context, id, note.reminderSubject, due); return }
        if (now >= due + ReminderSchedule.WINDOW) { cancel(context, id); return }
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
            val openIntent = Intent(context, MainActivity::class.java)
                .setData(Uri.Builder().scheme("win26").authority("notepad").appendPath(id).build())
                .putExtra("win26_open_note", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val open = PendingIntent.getActivity(context, id.hashCode(), openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(id, 26, NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(note.reminderSubject.ifBlank { note.title })
                .setContentText(note.content.take(180).ifBlank { "Notepad reminder" })
                .setContentIntent(open).setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_SOUND)
                .build())
        }
        val next = ReminderSchedule.next(due, now)
        if (next > 0L) schedule(context, id, note.reminderSubject, next)
        else alarm(context).cancel(pending(context, id))
    }

    companion object {
        private fun alarm(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        private fun pending(context: Context, id: String) = PendingIntent.getBroadcast(context, id.hashCode(),
            Intent(context, Win98NoteReminderReceiver::class.java)
                .setData(Uri.Builder().scheme("win26").authority("note-reminder").appendPath(id).build())
                .putExtra("note_id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private fun cancelLegacy(context: Context, id: String) {
            val old = PendingIntent.getBroadcast(context, id.hashCode(),
                Intent(context, Win98NoteReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (old != null) { alarm(context).cancel(old); old.cancel() }
        }

        fun cancel(context: Context, id: String) {
            cancelLegacy(context, id)
            val pending = pending(context, id)
            alarm(context).cancel(pending)
            pending.cancel()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).apply {
                cancel(id, 26)
                cancel(id.hashCode())
            }
        }

        @Suppress("UNUSED_PARAMETER")
        fun schedule(context: Context, id: String, title: String, at: Long) {
            cancelLegacy(context, id)
            val pending = pending(context, id)
            alarm(context).cancel(pending)
            if (at > 0L) alarm(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                maxOf(at, System.currentTimeMillis() + 1000L), pending)
        }

        fun restore(context: Context) {
            val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val notes = try { NotepadJson.read(prefs.getString("notepad_notes", null)) } catch (_: Exception) { return }
            val now = System.currentTimeMillis()
            for (note in notes) {
                if (!note.isArchived && note.reminderAt > 0L && now < note.reminderAt + ReminderSchedule.WINDOW) {
                    schedule(context, note.id, note.reminderSubject, maxOf(note.reminderAt, now + 1000L))
                }
            }
        }
    }
}
