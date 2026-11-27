package com.marcel.daybook;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.time.*;
import java.util.*;

final class Investment {
    static final String[] AREAS={"four-spiritual","four-financial","four-mental","four-physical"};
    static boolean area(String id){return Arrays.asList(AREAS).contains(id);}
    static void schema(SQLiteDatabase db){
        db.execSQL("CREATE TABLE tracking_epoch(day TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE method_time(goal TEXT NOT NULL REFERENCES goals(id),day TEXT NOT NULL,seconds INTEGER NOT NULL CHECK(seconds BETWEEN 0 AND 86400),PRIMARY KEY(goal,day))");
        db.execSQL("CREATE TABLE method_timer(slot INTEGER PRIMARY KEY CHECK(slot=1),goal TEXT NOT NULL REFERENCES goals(id),started INTEGER NOT NULL,zone TEXT NOT NULL)");
        db.execSQL("CREATE TABLE task_state(goal TEXT PRIMARY KEY REFERENCES goals(id),since TEXT NOT NULL,deadline TEXT NOT NULL,completed TEXT NOT NULL,completed_name TEXT NOT NULL)");
    }
    static void initialize(SQLiteDatabase db){
        if(DatabaseUtils.longForQuery(db,"SELECT count(*) FROM tracking_epoch",null)>0)return;
        String day=LocalDate.now().toString(),week=Journal.monday(LocalDate.now()).toString();
        db.execSQL("INSERT INTO tracking_epoch VALUES(?)",new Object[]{day});
        db.execSQL("INSERT OR REPLACE INTO goal_edits SELECT id,?,COALESCE((SELECT e.name FROM goal_edits e WHERE e.goal=g.id AND e.since<=? ORDER BY e.since DESC LIMIT 1),g.name),g.start,'9999-12-31' FROM goals g WHERE id IN ('four-spiritual','four-financial','four-mental','four-physical')",new Object[]{day,day});
        db.execSQL("INSERT OR REPLACE INTO goal_visibility SELECT id,?,0 FROM goals WHERE id IN ('four-spiritual','four-financial','four-mental','four-physical')",new Object[]{day});
        String query="SELECT g.id,g.period,g.start,COALESCE((SELECT e.end FROM goal_edits e WHERE e.goal=g.id AND e.since<=? ORDER BY e.since DESC LIMIT 1),g.end),COALESCE((SELECT e.name FROM goal_edits e WHERE e.goal=g.id AND e.since<=? ORDER BY e.since DESC LIMIT 1),g.name) FROM goals g WHERE g.period!='Daily' AND COALESCE((SELECT v.hidden FROM goal_visibility v WHERE v.goal=g.id AND v.day<=? ORDER BY v.day DESC LIMIT 1),0)=0";
        try(Cursor c=db.rawQuery(query,new String[]{day,day,day})){
            while(c.moveToNext()){
                String id=c.getString(0),period=c.getString(1),start=c.getString(2),end=c.getString(3),name=c.getString(4);
                if(end.compareTo(day)<=0)continue;
                String deadline=end.equals("9999-12-31")?"":LocalDate.parse(end).minusDays(1).toString(),done="";
                if(period.equals("Weekly")){
                    try(Cursor t=db.rawQuery("SELECT day FROM weekly_taps WHERE goal=? AND week=?",new String[]{id,start.compareTo(week)>0?start:week})){
                        if(t.moveToFirst()&&t.getString(0).compareTo(day)<=0)done=t.getString(0);
                    }
                }
                if(!done.isEmpty()){
                    try(Cursor e=db.rawQuery("SELECT COALESCE((SELECT name FROM goal_edits WHERE goal=? AND since<=? ORDER BY since DESC LIMIT 1),name) FROM goals WHERE id=?",new String[]{id,done,id})){if(e.moveToFirst())name=e.getString(0);}
                }
                db.execSQL("INSERT OR IGNORE INTO task_state VALUES(?,?,?,?,?)",new Object[]{id,day,deadline,done,done.isEmpty()?"":name});
            }
        }
    }
    static String epoch(Journal j){return DatabaseUtils.stringForQuery(j.getReadableDatabase(),"SELECT day FROM tracking_epoch LIMIT 1",null);}
    static boolean tracked(Journal j,LocalDate d){return d.toString().compareTo(epoch(j))>=0;}
    static String deadline(String raw){return raw.isEmpty()?"":LocalDate.parse(raw).toString();}
    static int seconds(SQLiteDatabase db,String id,String day){return (int)DatabaseUtils.longForQuery(db,"SELECT COALESCE(sum(seconds),0) FROM method_time WHERE goal=? AND day=?",new String[]{id,day});}
    static void write(SQLiteDatabase db,String id,String day,int n){
        if(n<0||n>86400)throw new IllegalArgumentException("Time must be between zero and 24 hours per category per day.");
        if(n==0)db.delete("method_time","goal=? AND day=?",new String[]{id,day});
        else db.execSQL("INSERT OR REPLACE INTO method_time VALUES(?,?,?)",new Object[]{id,day,n});
    }
    static void today(String day){if(!LocalDate.now().toString().equals(day))throw new IllegalArgumentException("The day changed. Reopen time entry.");}
    static void log(Journal j,JSONObject a)throws JSONException{
        String id=a.getString("id"),day=a.getString("day");today(day);
        if(!area(id))throw new IllegalArgumentException("Choose a Method of 4 category.");
        int m=a.getInt("minutes");boolean replace=a.optBoolean("replace");
        if(m<0||m>1440||(!replace&&m==0))throw new IllegalArgumentException("Enter valid whole minutes.");
        SQLiteDatabase db=j.getWritableDatabase();db.beginTransaction();
        try{write(db,id,day,replace?m*60:seconds(db,id,day)+m*60);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    static JSONObject timer(Journal j)throws JSONException{
        JSONObject t=j.rows("SELECT goal,started,zone FROM method_timer WHERE slot=1").optJSONObject(0);
        return t==null?new JSONObject():t;
    }
    static void start(Journal j,String id,String day){
        today(day);if(!area(id))throw new IllegalArgumentException("Choose a Method of 4 category.");
        SQLiteDatabase db=j.getWritableDatabase();db.beginTransaction();
        try{
            if(DatabaseUtils.longForQuery(db,"SELECT count(*) FROM method_timer",null)>0)throw new IllegalArgumentException("Stop the running stopwatch first.");
            db.execSQL("INSERT INTO method_timer VALUES(1,?,?,?)",new Object[]{id,System.currentTimeMillis(),ZoneId.systemDefault().getId()});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static void stop(Journal j,String id,boolean discard)throws JSONException{
        SQLiteDatabase db=j.getWritableDatabase();db.beginTransaction();
        try{
            JSONObject t=timer(j);if(!id.equals(t.optString("goal")))throw new IllegalArgumentException("This stopwatch is not running.");
            long start=t.getLong("started"),end=System.currentTimeMillis();
            if(!discard){
                if(end<start||end-start>7L*86400000)throw new IllegalArgumentException("Invalid stopwatch duration. Discard and enter the time manually.");
                ZoneId zone=ZoneId.of(t.getString("zone"));long cursor=start,remaining=(end-start)/1000;
                while(cursor<end){
                    LocalDate date=Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate();
                    long next=Math.min(end,date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli());
                    int count=(int)(next==end?remaining:(next-cursor)/1000);remaining-=count;
                    if(count>0)write(db,id,date.toString(),seconds(db,id,date.toString())+count);cursor=next;
                }
            }
            db.delete("method_timer","slot=1",null);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static JSONObject stats(Journal j,String a,String b)throws JSONException{
        LocalDate from=LocalDate.parse(a),until=LocalDate.parse(b),today=LocalDate.now();
        if(!until.isAfter(from))throw new IllegalArgumentException("Invalid statistics range.");
        SQLiteDatabase db=j.getReadableDatabase();JSONObject totals=new JSONObject();long total=0,extra=0,balanced=0;
        for(String id:AREAS){long n=DatabaseUtils.longForQuery(db,"SELECT COALESCE(sum(seconds),0) FROM method_time WHERE goal=? AND day>=? AND day<?",new String[]{id,a,b});totals.put(id,n);total+=n;}
        try(Cursor c=db.rawQuery("SELECT COALESCE(sum(CASE WHEN seconds>3600 THEN seconds-3600 ELSE 0 END),0),COALESCE(sum(CASE WHEN seconds>3600 THEN 3600 ELSE seconds END),0) FROM method_time WHERE day>=? AND day<?",new String[]{a,b})){if(c.moveToFirst()){extra=c.getLong(0);balanced=c.getLong(1);}}
        GoalBook book=new GoalBook(j);int done=0,missed=0,open=0;
        LocalDate cutoff=until.isBefore(today.plusDays(1))?until:today.plusDays(1);
        for(GoalBook.Goal g:book.goals.values())if(!area(g.id)&&g.original.optString("period").equals("Daily")){
            LocalDate first=from.isAfter(g.first())?from:g.first();
            for(LocalDate d=first;d.isBefore(cutoff);d=d.plusDays(1))if(g.due(d)){if(g.done(d))done++;else if(d.equals(today))open++;else missed++;}
        }
        long weekly=DatabaseUtils.longForQuery(db,"SELECT count(*) FROM task_state t JOIN goals g ON g.id=t.goal WHERE g.period='Weekly' AND t.completed>=? AND t.completed<?",new String[]{a,b});
        long objectives=DatabaseUtils.longForQuery(db,"SELECT count(*) FROM task_state t JOIN goals g ON g.id=t.goal WHERE g.period!='Weekly' AND t.completed>=? AND t.completed<?",new String[]{a,b});
        LocalDate begin=LocalDate.parse(epoch(j));if(from.isAfter(begin))begin=from;
        return new JSONObject().put("areas",totals).put("total",total).put("extra",extra).put("balanced",balanced).put("target",Math.max(0,java.time.temporal.ChronoUnit.DAYS.between(begin,cutoff))*14400).put("dailyDone",done).put("dailyMissed",missed).put("dailyOpen",open).put("weekly",weekly).put("objectives",objectives);
    }
}
