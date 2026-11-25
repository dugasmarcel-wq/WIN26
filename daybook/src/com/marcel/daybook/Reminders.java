package com.marcel.daybook;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import org.json.*;

public final class Reminders extends BroadcastReceiver {
    static final String CHANNEL="note-reminders";
    static void channel(Context c) {
        c.getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Note reminders",NotificationManager.IMPORTANCE_HIGH));
    }
    static boolean allowed(Context c) {
        channel(c);NotificationManager nm=c.getSystemService(NotificationManager.class);
        return nm.areNotificationsEnabled()&&nm.getNotificationChannel(CHANNEL).getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    static boolean exact(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    static Intent event(Context c,String action,String id) {
        return new Intent(c,Reminders.class).setAction(action).setData(Uri.parse("daybook://reminder/"+id)).putExtra("id",id);
    }
    static void plan(Context c,String id,long time) {
        AlarmManager am=c.getSystemService(AlarmManager.class);
        PendingIntent p=PendingIntent.getBroadcast(c,0,event(c,"DUE",id).putExtra("at",time),Widget.FLAGS);
        am.cancel(p);
        if(time<=0)return;
        long at=Math.max(System.currentTimeMillis()+200,time);
        if(exact(c)) {
            try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);return; }catch(SecurityException ignored) {}
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);
    }
    static void all(Context c) throws JSONException {
        JSONArray notes=Journal.get(c).rows("SELECT id,alarm FROM notes WHERE alarm>0");
        for(int i=0;i<notes.length();i++){JSONObject n=notes.getJSONObject(i);plan(c,n.getString("id"),n.getLong("alarm"));}
    }
    @Override public void onReceive(Context c,Intent intent) {
        String action=intent.getAction(), id=intent.getStringExtra("id");Journal db=Journal.get(c);
        try {
            if("SNOOZE".equals(action)) {
                if(db.note(id)==null)return;
                long at=System.currentTimeMillis()+600000;
                db.setAlarm(id,at);plan(c,id,at);c.getSystemService(NotificationManager.class).cancel(id,1);
            } else if("DUE".equals(action)) {
                JSONObject n=db.note(id);long at=intent.getLongExtra("at",0);
                if(n==null||at<=0||n.getLong("alarm")!=at)return;
                if(at>System.currentTimeMillis()+1000){plan(c,id,at);return;}
                if(!allowed(c))return;
                String title=n.getString("title").trim(), body=n.getString("body").trim();
                PendingIntent snooze=PendingIntent.getBroadcast(c,0,event(c,"SNOOZE",id),Widget.FLAGS);
                Notification notice=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.book)
                    .setContentTitle(title.isEmpty()?"Daybook reminder":title).setContentText(body.isEmpty()?"Open note":body)
                    .setContentIntent(Widget.open(c,"note/"+id)).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                    .addAction(new Notification.Action.Builder(null,"Snooze 10 min",snooze).build()).build();
                c.getSystemService(NotificationManager.class).notify(id,1,notice);db.clearIfDue(id,at);
            } else {all(c);Widget.refresh(c);}
        } catch(JSONException e) {throw new IllegalStateException(e);}
    }
}
