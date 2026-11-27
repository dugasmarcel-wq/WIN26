package com.marcel.daybook;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;
import org.json.*;
import java.time.LocalDate;
import java.util.*;
import android.os.Build;

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
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,android.os.Bundle options) {draw(c,m,id);}
    @Override public void onReceive(Context c,Intent i) {
        super.onReceive(c,i);
        if("MARK".equals(i.getAction())&&i.hasExtra("goal")&&i.hasExtra("day")&&i.hasExtra("done")) {
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
    static Map<String,String> colors(Journal journal)throws JSONException{
        Map<String,String> colors=new HashMap<>();
        String[][] defaults={{"four-spiritual","#79569b"},{"four-financial","#32725c"},{"four-physical","#a24d38"},{"four-mental","#326998"},{"long","#775286"},{"weekly","#396b94"},{"daily","#28755c"}};
        for(String[] pair:defaults)colors.put(pair[0],pair[1]);
        for(JSONObject option:GoalBook.items(journal.rows("SELECT * FROM colors")))
            if(option.optString("value").matches("#[0-9a-fA-F]{6}"))colors.put(option.getString("target"),option.getString("value"));
        return colors;
    }
    static void draw(Context c,AppWidgetManager manager,int id){
        try{
            AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
            android.os.Bundle options=manager.getAppWidgetOptions(id);
            boolean locked=(info!=null&&info.provider.equals(new ComponentName(c,LockGoals.class)))||
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY)==AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD;
            LocalDate day=LocalDate.now();
            Journal journal=Journal.get(c);
            JSONArray data=journal.goals(day);
            Map<String,String> colors=colors(journal);
            RemoteViews panel=new RemoteViews(c.getPackageName(),locked?R.layout.lock_goals:R.layout.widget);
            String date=day.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d",Locale.getDefault()));
            panel.setTextViewText(R.id.heading,date);
            if(!locked){
                panel.setOnClickPendingIntent(R.id.heading,open(c,"today"));
                panel.setOnClickPendingIntent(R.id.openNote,open(c,"daily-note"));
                drawTracker(c,panel,day);
            }
            panel.removeAllViews(R.id.rows);
            Map<String,JSONObject> areas=new HashMap<>();
            for(JSONObject goal:GoalBook.items(data))if(goal.optBoolean("active")&&Investment.area(goal.optString("id")))areas.put(goal.getString("id"),goal);
            String timer=Investment.timer(journal).optString("goal");
            String[][] pairs={{"four-spiritual","four-financial"},{"four-physical","four-mental"}};
            for(String[] pair:pairs){
                RemoteViews line=new RemoteViews(c.getPackageName(),R.layout.area_pair);
                for(int column=0;column<pair.length;column++){
                    String gid=pair[column];JSONObject goal=areas.get(gid);if(goal==null)continue;
                    String name=gid.substring(5),area=Character.toUpperCase(name.charAt(0))+name.substring(1);
                    int seconds=goal.optInt("seconds");
                    RemoteViews cell=new RemoteViews(c.getPackageName(),R.layout.goal);
                    String progress=timer.equals(gid)?"Timing":seconds/60+"/60m";
                    cell.setTextViewText(R.id.name,area+" · "+progress);
                    cell.setTextViewText(R.id.progressLabel,goal.optString("name"));
                    cell.setImageViewBitmap(R.id.bubble,bubble(colors.get(gid),seconds>=3600,seconds));
                    cell.setContentDescription(R.id.row,area+", "+goal.optString("name")+", "+seconds/60+" minutes recorded. Tap to log time.");
                    cell.setOnClickPendingIntent(R.id.row,open(c,"method/"+gid));
                    line.addView(column==0?R.id.firstArea:R.id.secondArea,cell);
                }
                panel.addView(R.id.rows,line);
            }
            List<JSONObject> tasks=WidgetTasks.active(data);
            int completed=0;for(JSONObject task:tasks)completed+=task.optInt("done");
            panel.setTextViewText(R.id.taskHeading,"Responsibilities · "+completed+"/"+tasks.size());
            panel.setViewVisibility(R.id.taskHeading,tasks.isEmpty()?View.GONE:View.VISIBLE);
            panel.setViewVisibility(R.id.tasks,tasks.isEmpty()?View.GONE:View.VISIBLE);
            Intent template=new Intent(c,Widget.class).setAction("MARK").setData(Uri.parse("daybook://tasks/"+id));
            int flags=PendingIntent.FLAG_UPDATE_CURRENT;
            if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
            panel.setPendingIntentTemplate(R.id.tasks,PendingIntent.getBroadcast(c,id,template,flags));
            if(Build.VERSION.SDK_INT>=31){
                RemoteViews.RemoteCollectionItems.Builder items=new RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1);
                for(int i=0;i<tasks.size();i++)items.addItem(i,WidgetTasks.cell(c,tasks.get(i),day,colors));
                panel.setRemoteAdapter(R.id.tasks,items.build());
                int room=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,240)-(locked?148:176);
                int height=Math.max(38,Math.min(Math.max(76,room),((tasks.size()+1)/2)*39));
                panel.setViewLayoutHeight(R.id.tasks,height,android.util.TypedValue.COMPLEX_UNIT_DIP);
            }else{
                Intent adapter=new Intent(c,WidgetTasks.class).setData(Uri.parse("daybook://adapter/"+id));
                panel.setRemoteAdapter(R.id.tasks,adapter);
            }
            manager.updateAppWidget(id,panel);
            if(Build.VERSION.SDK_INT<31)manager.notifyAppWidgetViewDataChanged(id,R.id.tasks);
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
}
