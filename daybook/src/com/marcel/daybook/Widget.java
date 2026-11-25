package com.marcel.daybook;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;
import org.json.*;
import java.time.LocalDate;
import java.util.ArrayList;

public class Widget extends AppWidgetProvider {
    static final int FLAGS=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
    static PendingIntent open(Context c,String route) {
        Intent i=new Intent(c,Main.class).setAction("com.marcel.daybook.OPEN").setData(Uri.parse("daybook://open/"+route))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c,0,i,FLAGS);
    }
    static void refresh(Context c) {
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        for(Class<?> provider:new Class<?>[]{Widget.class,LockGoals.class})
            for(int id:m.getAppWidgetIds(new ComponentName(c,provider))) draw(c,m,id);
    }
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids) {for(int id:ids) draw(c,m,id);}
    @Override public void onDeleted(Context c,int[] ids) {for(int id:ids)c.getSharedPreferences("widget",0).edit().remove("page"+id).apply();}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,android.os.Bundle options) {draw(c,m,id);}
    @Override public void onReceive(Context c,Intent i) {
        super.onReceive(c,i);
        if("PAGE".equals(i.getAction())) {
            int id=i.getIntExtra("widget",0);
            c.getSharedPreferences("widget",0).edit().putInt("page"+id,i.getIntExtra("page",0)).apply();
            draw(c,AppWidgetManager.getInstance(c),id);
        } else if("MARK".equals(i.getAction())) {
            Journal.get(c).mark(i.getStringExtra("goal"),i.getStringExtra("day"),i.getBooleanExtra("done",false));refresh(c);
        }
    }
    static void drawTracker(Context c,RemoteViews views,LocalDate today) {
        LocalDate first=today.withDayOfMonth(1), week=today.minusDays(6), end=today.plusDays(1);
        Tracker tracker=new Tracker(Journal.get(c),first.isBefore(week)?first:week,end,today);
        int percent=tracker.total(first,end).percent();
        String month=today.format(java.time.format.DateTimeFormatter.ofPattern("MMM",java.util.Locale.getDefault()));
        String rate=month+" · "+(percent<0?"—":percent+"%");
        views.setTextViewText(R.id.trackRate,rate);
        android.text.SpannableStringBuilder dots=new android.text.SpannableStringBuilder();
        StringBuilder description=new StringBuilder(rate).append(". Last seven days. ");
        for(LocalDate d=week;!d.isAfter(today);d=d.plusDays(1)) {
            String status=tracker.on(d).status();
            int color;
            switch(status) {
                case "done": color=0xff28674c; break;
                case "partial": color=0xff946210; break;
                case "missed": color=0xffb14039; break;
                case "open": color=0xff326691; break;
                default: color=0xff868b81;
            }
            int pos=dots.length(); dots.append(status.equals("none")?"○":"●");
            dots.setSpan(new android.text.style.ForegroundColorSpan(color),pos,pos+1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if(!d.equals(today))dots.append(" ");
            description.append(d).append(" ").append(status).append(". ");
        }
        views.setTextViewText(R.id.trackDots,new android.text.SpannedString(dots));
        views.setContentDescription(R.id.tracker,description.toString());
        views.setOnClickPendingIntent(R.id.tracker,open(c,"tracker"));
    }
    static void draw(Context c,AppWidgetManager m,int id) {
        try {
            AppWidgetProviderInfo info=m.getAppWidgetInfo(id);
            boolean locked=(info!=null && info.provider.equals(new ComponentName(c,LockGoals.class))) ||
                m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY)==AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD;
            RemoteViews v=new RemoteViews(c.getPackageName(),locked?R.layout.lock_goals:R.layout.widget);
            if(!locked)v.setOnClickPendingIntent(R.id.heading,open(c,"today"));
            if(!locked)v.setOnClickPendingIntent(R.id.openNote,open(c,"daily-note"));
            v.removeAllViews(R.id.rows);
            LocalDate day=LocalDate.now(); JSONArray data=Journal.get(c).goals(day);
            if(!locked)drawTracker(c,v,day);
            ArrayList<JSONObject> goals=new ArrayList<>();
            for(int i=0;i<data.length();i++) {
                JSONObject g=data.getJSONObject(i);if(g.getBoolean("active"))goals.add(g);
            }
            int height=m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,220);
            int count=Math.max(1,Math.min(16,(height-(locked?54:90))/44));
            int pages=Math.max(1,(goals.size()+count-1)/count);
            int page=Math.floorMod(c.getSharedPreferences("widget",0).getInt("page"+id,0),pages);
            v.setViewVisibility(R.id.next,pages>1?View.VISIBLE:View.GONE);
            if(pages>1) {
                v.setTextViewText(R.id.next,(page+1)+"/"+pages+"  ›");
                Intent next=new Intent(c,Widget.class).setAction("PAGE").setData(Uri.parse("daybook://page/"+id+"/"+page)).putExtra("widget",id).putExtra("page",(page+1)%pages);
                v.setOnClickPendingIntent(R.id.next,PendingIntent.getBroadcast(c,0,next,FLAGS));
            }
            if(goals.isEmpty()){
                RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.goal);
                row.setViewVisibility(R.id.bubble,View.GONE);row.setTextViewText(R.id.name,"Choose goals in Daybook");
                if(!locked)row.setOnClickPendingIntent(R.id.row,open(c,"today"));v.addView(R.id.rows,row);
            }
            for(int n=page*count;n<Math.min((page+1)*count,goals.size());n++) {
                JSONObject g=goals.get(n);boolean done=g.getInt("done")==1;
                String gid=g.getString("id");RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.goal);
                row.setTextViewText(R.id.name,g.getString("name"));
                row.setTextViewText(R.id.missed,g.getInt("missed")+" missed");
                String period=g.getString("period");
                int circle;
                if ("Weekly".equals(period)) circle=done?R.drawable.weekly_full:R.drawable.weekly_open;
                else if ("Daily".equals(period)) circle=done?R.drawable.daily_full:R.drawable.daily_open;
                else circle=done?R.drawable.long_full:R.drawable.long_open;
                row.setInt(R.id.bubble,"setBackgroundResource",circle);
                row.setTextViewText(R.id.bubble,done?"✓":"");
                String group=period.equals("Weekly")?"Weekly":period.equals("Daily")?"Daily":"Long-term";
                row.setContentDescription(R.id.row,group+" goal, "+g.getString("name")+(done?", checked":", unchecked"));
                Intent tap=new Intent(c,Widget.class).setAction("MARK").setData(Uri.parse("daybook://mark/"+gid+"/"+day+"/"+!done))
                    .putExtra("goal",gid).putExtra("day",day.toString()).putExtra("done",!done);
                row.setOnClickPendingIntent(R.id.row,PendingIntent.getBroadcast(c,0,tap,FLAGS));v.addView(R.id.rows,row);
            }
            m.updateAppWidget(id,v);
        } catch(JSONException e){ throw new IllegalStateException(e); }
    }
}
