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
    static android.graphics.Bitmap bubble(String color,boolean done){return bubble(color,done,-1);}
    static android.graphics.Bitmap bubble(String color,boolean done,int seconds){
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        int ink=android.graphics.Color.parseColor(color);
        if(android.graphics.Color.luminance(ink)>.62f)ink=android.graphics.Color.rgb(android.graphics.Color.red(ink)*2/3,android.graphics.Color.green(ink)*2/3,android.graphics.Color.blue(ink)*2/3);
        if(seconds>=0){
            paint.setColor(ink);paint.setAlpha(35);canvas.drawCircle(32,32,27,paint);paint.setAlpha(255);
            canvas.save();android.graphics.Path circle=new android.graphics.Path();circle.addCircle(32,32,27,android.graphics.Path.Direction.CW);canvas.clipPath(circle);
            canvas.drawRect(5,59-54*Math.min(1f,seconds/3600f),59,59,paint);canvas.restore();
            paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(3);canvas.drawCircle(32,32,27,paint);return bitmap;
        }
        paint.setColor(ink);paint.setStyle(done?android.graphics.Paint.Style.FILL:android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(4);
        canvas.drawCircle(32,32,27,paint);
        if(done){
            paint.setColor(android.graphics.Color.WHITE);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(5);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            android.graphics.Path mark=new android.graphics.Path();mark.moveTo(18,33);mark.lineTo(28,43);mark.lineTo(46,22);canvas.drawPath(mark,paint);
        }return bitmap;
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
            goals.sort((a,b)->Boolean.compare(!a.optString("id").startsWith("four-"),!b.optString("id").startsWith("four-")));
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
            java.util.Map<String,String> colors=new java.util.HashMap<>();
            String[][] defaults={{"four-spiritual","#79569b"},{"four-financial","#32725c"},{"four-physical","#a24d38"},{"four-mental","#326998"},{"long","#775286"},{"weekly","#396b94"},{"daily","#28755c"}};
            for(String[] pair:defaults)colors.put(pair[0],pair[1]);
            for(JSONObject option:GoalBook.items(Journal.get(c).rows("SELECT * FROM colors")))if(option.optString("value").matches("#[0-9a-fA-F]{6}"))colors.put(option.getString("target"),option.getString("value"));
            for(int n=page*count;n<Math.min((page+1)*count,goals.size());n++) {
                JSONObject g=goals.get(n);boolean done=g.getInt("done")==1;
                String gid=g.getString("id");RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.goal);
                String label=g.getString("name");
                if(gid.startsWith("four-")){
                    String area=gid.substring(5);
                    label=Character.toUpperCase(area.charAt(0))+area.substring(1)+" · "+label;
                }
                row.setTextViewText(R.id.name,label);
                boolean timed=g.optBoolean("timed"),task=g.optBoolean("task");int seconds=g.optInt("seconds");
                row.setTextViewText(R.id.missed,timed?(seconds/60)+" / 60 min":task?(done?"Completed":g.optString("deadline").isEmpty()?"Open":"Due "+g.optString("deadline")):g.getInt("missed")+" missed");
                String period=g.getString("period");
                String key=gid.startsWith("four-")?gid:period.equals("Weekly")?"weekly":period.equals("Daily")?"daily":"long";
                row.setImageViewBitmap(R.id.bubble,bubble(colors.getOrDefault(key,"#245748"),done,timed?seconds:-1));
                String group=period.equals("Weekly")?"Weekly":period.equals("Daily")?"Daily":"Long-term";
                row.setContentDescription(R.id.row,group+" goal, "+g.getString("name")+(timed?", "+seconds/60+" minutes recorded":done?", checked":", unchecked"));
                Intent tap=new Intent(c,Widget.class).setAction("MARK").setData(Uri.parse("daybook://mark/"+gid+"/"+day+"/"+!done))
                    .putExtra("goal",gid).putExtra("day",day.toString()).putExtra("done",!done);
                row.setOnClickPendingIntent(R.id.row,timed?open(c,"method/"+gid):PendingIntent.getBroadcast(c,0,tap,FLAGS));v.addView(R.id.rows,row);
            }
            m.updateAppWidget(id,v);
        } catch(JSONException e){ throw new IllegalStateException(e); }
    }
}
