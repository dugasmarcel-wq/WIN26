package com.marcel.daybook;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import org.json.*;
import java.time.LocalDate;
import java.util.*;

/** Collection adapter for widget hosts on Android 9–11. */
public final class WidgetTasks extends RemoteViewsService {
    static List<JSONObject> active(JSONArray data)throws JSONException {
        List<JSONObject> result=new ArrayList<>();
        for(JSONObject goal:GoalBook.items(data))
            if(goal.getBoolean("active")&&!Investment.area(goal.getString("id")))result.add(goal);
        result.sort(Comparator.comparingInt(g->g.optInt("done")));
        return result;
    }
    static RemoteViews cell(Context context,JSONObject g,LocalDate day,Map<String,String> colors) {
        RemoteViews row=new RemoteViews(context.getPackageName(),R.layout.responsibility);
        boolean done=g.optInt("done")==1;
        String name=g.optString("name"),period=g.optString("period");
        String key=period.equals("Daily")?"daily":period.equals("Weekly")?"weekly":"long";
        String group=key.equals("daily")?"Daily":key.equals("weekly")?"Weekly":"Objective";
        String deadline=g.optString("deadline"),status=group;
        if(done)status+=" · Done";
        else if(!deadline.isEmpty())status+=" · "+deadline.substring(5);
        row.setTextViewText(R.id.taskMark,done?"✓":"○");
        row.setTextColor(R.id.taskMark,android.graphics.Color.parseColor(colors.get(key)));
        row.setTextViewText(R.id.taskName,name);
        row.setTextViewText(R.id.taskInfo,status);
        row.setContentDescription(R.id.taskCell,group+", "+name+(done?", checked":", unchecked")+(deadline.isEmpty()?"":", due "+deadline));
        row.setOnClickFillInIntent(R.id.taskCell,new Intent().putExtra("goal",g.optString("id")).putExtra("day",day.toString()).putExtra("done",!done));
        return row;
    }
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent){
        return new RemoteViewsFactory(){
            List<JSONObject> goals=Collections.emptyList();
            LocalDate day;
            Map<String,String> colors;
            public void onCreate(){}
            public void onDataSetChanged(){
                day=LocalDate.now();
                try{Journal j=Journal.get(WidgetTasks.this);goals=active(j.goals(day));colors=Widget.colors(j);}
                catch(JSONException e){throw new IllegalStateException(e);}
            }
            public void onDestroy(){goals=Collections.emptyList();}
            public int getCount(){return goals.size();}
            public RemoteViews getViewAt(int index){return index>=0&&index<goals.size()?cell(WidgetTasks.this,goals.get(index),day,colors):null;}
            public RemoteViews getLoadingView(){return null;}
            public int getViewTypeCount(){return 1;}
            public long getItemId(int index){return index;}
            public boolean hasStableIds(){return false;}
        };
    }
}
