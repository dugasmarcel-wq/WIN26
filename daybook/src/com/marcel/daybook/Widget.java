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

public final class Widget extends AppWidgetProvider {
    static final int FLAGS=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
    static PendingIntent open(Context c,String route) {
        Intent i=new Intent(c,Main.class).setAction("com.marcel.daybook.OPEN").setData(Uri.parse("daybook://open/"+route))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c,0,i,FLAGS);
    }
    static void refresh(Context c) {
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        for(int id:m.getAppWidgetIds(new ComponentName(c,Widget.class))) draw(c,m,id);
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
    static void draw(Context c,AppWidgetManager m,int id) {
        try {
            RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);
            v.setOnClickPendingIntent(R.id.heading,open(c,"today"));
            v.setOnClickPendingIntent(R.id.openNote,open(c,"new"));
            v.removeAllViews(R.id.rows);
            LocalDate day=LocalDate.now(); JSONArray data=Journal.get(c).goals(day);
            ArrayList<JSONObject> goals=new ArrayList<>();
            for(int i=0;i<data.length();i++) {
                JSONObject g=data.getJSONObject(i);if(g.getBoolean("active")&&g.getInt("pinned")==1)goals.add(g);
            }
            int height=m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,220);
            int count=Math.max(1,Math.min(3,(height-100)/44));
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
                row.setOnClickPendingIntent(R.id.row,open(c,"today"));v.addView(R.id.rows,row);
            }
            for(int n=page*count;n<Math.min((page+1)*count,goals.size());n++) {
                JSONObject g=goals.get(n);boolean done=g.getInt("done")==1;
                String gid=g.getString("id");RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.goal);
                row.setTextViewText(R.id.name,g.getString("name"));
                row.setTextViewText(R.id.missed,g.getInt("missed")+" missed");
                row.setInt(R.id.bubble,"setBackgroundResource",done?R.drawable.filled:R.drawable.empty);
                row.setTextViewText(R.id.bubble,done?"✓":"");
                row.setContentDescription(R.id.row,(done?"Undo today: ":"Complete today: ")+g.getString("name"));
                Intent tap=new Intent(c,Widget.class).setAction("MARK").setData(Uri.parse("daybook://mark/"+gid+"/"+day+"/"+!done))
                    .putExtra("goal",gid).putExtra("day",day.toString()).putExtra("done",!done);
                row.setOnClickPendingIntent(R.id.row,PendingIntent.getBroadcast(c,0,tap,FLAGS));v.addView(R.id.rows,row);
            }
            m.updateAppWidget(id,v);
        } catch(JSONException e){ throw new IllegalStateException(e); }
    }
}
