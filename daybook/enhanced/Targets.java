package com.marcel.daybook;

import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.time.LocalDate;
import java.util.*;

/** Effective-dated requirements. A schedule revision never reinterprets earlier days. */
final class Targets {
    private final Map<String,TreeMap<LocalDate,int[]>> schedules=new HashMap<>();
    private final Map<String,Map<LocalDate,Integer>> overrides=new HashMap<>();
    static void schema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE method_target_schedule(goal TEXT NOT NULL REFERENCES goals(id),since TEXT NOT NULL,weekday INTEGER NOT NULL CHECK(weekday BETWEEN 0 AND 86400),weekend INTEGER NOT NULL CHECK(weekend BETWEEN 0 AND 86400),PRIMARY KEY(goal,since))");
        db.execSQL("CREATE TABLE method_target_override(goal TEXT NOT NULL REFERENCES goals(id),day TEXT NOT NULL,seconds INTEGER NOT NULL CHECK(seconds BETWEEN 0 AND 86400),PRIMARY KEY(goal,day))");
    }
    static void initialize(SQLiteDatabase db) {
        String today=LocalDate.now().toString();
        for(String id:Investment.AREAS)
            db.execSQL("INSERT INTO method_target_schedule SELECT ?,?,3600,1200 WHERE NOT EXISTS (SELECT 1 FROM method_target_schedule WHERE goal=?)",new Object[]{id,today,id});
    }
    Targets(Journal journal)throws JSONException {
        for(JSONObject r:GoalBook.items(journal.rows("SELECT * FROM method_target_schedule ORDER BY since"))) {
            String id=r.getString("goal");
            schedules.computeIfAbsent(id,k->new TreeMap<>()).put(LocalDate.parse(r.getString("since")),new int[]{r.getInt("weekday"),r.getInt("weekend")});
        }
        for(JSONObject r:GoalBook.items(journal.rows("SELECT * FROM method_target_override"))) {
            overrides.computeIfAbsent(r.getString("goal"),k->new HashMap<>()).put(LocalDate.parse(r.getString("day")),r.getInt("seconds"));
        }
    }
    int[] schedule(String id,LocalDate day) {
        TreeMap<LocalDate,int[]> history=schedules.get(id);
        Map.Entry<LocalDate,int[]> row=history==null?null:history.floorEntry(day);
        // Imported historic records retain the original 60-minute daily requirement.
        return row==null?new int[]{3600,3600}:row.getValue();
    }
    int seconds(String id,LocalDate day) {
        Map<LocalDate,Integer> dates=overrides.get(id);
        if(dates!=null&&dates.containsKey(day))return dates.get(day);
        return schedule(id,day)[day.getDayOfWeek().getValue()>=6?1:0];
    }
    JSONObject config(LocalDate day)throws JSONException {
        JSONObject out=new JSONObject();
        for(String id:Investment.AREAS){
            int[] pair=schedule(id,day);
            Map<LocalDate,Integer> dates=overrides.get(id);
            out.put(id,new JSONObject().put("weekday",pair[0]/60).put("weekend",pair[1]/60).put("minutes",seconds(id,day)/60).put("overridden",dates!=null&&dates.containsKey(day)));
        }
        return out;
    }
    private static int minutes(JSONObject a,String key)throws JSONException {
        int m=a.getInt(key);if(m<0||m>1440)throw new IllegalArgumentException("Target must be between 0 and 1440 minutes.");return m*60;
    }
    static void edit(Journal journal,JSONObject args)throws JSONException {
        LocalDate day=LocalDate.parse(args.getString("day"));
        if(day.isBefore(LocalDate.now()))throw new IllegalArgumentException("Past requirements are locked.");
        String kind=args.getString("kind");JSONObject values=args.getJSONObject("values");
        SQLiteDatabase db=journal.getWritableDatabase();db.beginTransaction();
        try{
            for(String id:Investment.AREAS){
                JSONObject row=values.getJSONObject(id);
                if("schedule".equals(kind))db.execSQL("INSERT OR REPLACE INTO method_target_schedule VALUES(?,?,?,?)",new Object[]{id,day.toString(),minutes(row,"weekday"),minutes(row,"weekend")});
                else if("date".equals(kind)){
                    if(row.optBoolean("reset"))db.delete("method_target_override","goal=? AND day=?",new String[]{id,day.toString()});
                    else db.execSQL("INSERT OR REPLACE INTO method_target_override VALUES(?,?,?)",new Object[]{id,day.toString(),minutes(row,"minutes")});
                }else throw new IllegalArgumentException("Unknown target type.");
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
