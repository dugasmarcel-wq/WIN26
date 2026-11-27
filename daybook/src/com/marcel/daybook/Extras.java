package com.marcel.daybook;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.*;
import java.time.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class Extras {
    static void schema(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS habit_schedule(goal TEXT NOT NULL REFERENCES goals(id),since TEXT NOT NULL,mask INTEGER NOT NULL CHECK(mask BETWEEN 1 AND 127),part TEXT NOT NULL,PRIMARY KEY(goal,since))");
        db.execSQL("CREATE TABLE IF NOT EXISTS task_order(goal TEXT PRIMARY KEY REFERENCES goals(id),position INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS repeat_rules(id TEXT PRIMARY KEY,cadence TEXT NOT NULL,next_day TEXT NOT NULL,template TEXT NOT NULL REFERENCES goals(id),anchor TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS repeat_members(goal TEXT PRIMARY KEY REFERENCES goals(id),rule TEXT NOT NULL REFERENCES repeat_rules(id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS task_dates(goal TEXT PRIMARY KEY REFERENCES goals(id),day TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS timer_parts(slot INTEGER PRIMARY KEY CHECK(slot=1),paused INTEGER NOT NULL,elapsed INTEGER NOT NULL,segments TEXT NOT NULL)");
    }
    static String day(){return LocalDate.now().toString();}
    static void schedule(Journal j,JSONObject a)throws JSONException{
        String id=a.getString("id"),part=a.optString("part","Anytime");int mask=a.optInt("mask",127);
        GoalBook.Goal g=new GoalBook(j).goals.get(id);
        if(g==null||Investment.area(id)||!g.original.optString("period").equals("Daily")||mask<1||mask>127||!java.util.Arrays.asList("Anytime","Morning","Evening").contains(part))throw new IllegalArgumentException("Invalid habit schedule.");
        j.getWritableDatabase().execSQL("INSERT OR REPLACE INTO habit_schedule VALUES(?,?,?,?)",new Object[]{id,day(),mask,part});
    }
    static void order(Journal j,JSONArray ids)throws JSONException{
        SQLiteDatabase db=j.getWritableDatabase();db.beginTransaction();
        try{for(int k=0;k<ids.length();k++)db.execSQL("INSERT OR REPLACE INTO task_order VALUES(?,?)",new Object[]{ids.getString(k),k});db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    static LocalDate next(LocalDate current,LocalDate anchor,String cadence){
        if(cadence.equals("daily"))return current.plusDays(1);
        if(cadence.equals("weekly"))return current.plusWeeks(1);
        if(cadence.equals("monthly")){LocalDate d=current.plusMonths(1).withDayOfMonth(1);return d.withDayOfMonth(Math.min(anchor.getDayOfMonth(),d.lengthOfMonth()));}
        if(cadence.equals("yearly")){LocalDate d=current.plusYears(1).withMonth(anchor.getMonthValue()).withDayOfMonth(1);return d.withDayOfMonth(Math.min(anchor.getDayOfMonth(),d.lengthOfMonth()));}
        throw new IllegalArgumentException("Invalid recurrence.");
    }
    static void taskOptions(Journal j,JSONObject a)throws JSONException{
        String id=a.getString("id"),cad=a.optString("repeat","none"),scheduled=a.optString("scheduled","");
        GoalBook.Goal g=new GoalBook(j).goals.get(id);if(g==null||!g.task(LocalDate.now()))throw new IllegalArgumentException("Choose a responsibility or objective.");
        if(!scheduled.isEmpty())LocalDate.parse(scheduled);
        SQLiteDatabase db=j.getWritableDatabase();db.beginTransaction();
        try{
            if(scheduled.isEmpty())db.delete("task_dates","goal=?",new String[]{id});else db.execSQL("INSERT OR REPLACE INTO task_dates VALUES(?,?)",new Object[]{id,scheduled});
            JSONObject member=j.rows("SELECT rule FROM repeat_members WHERE goal=?",id).optJSONObject(0);
            if(cad.equals("none")){if(member!=null)db.execSQL("UPDATE repeat_rules SET cadence='none' WHERE id=?",new Object[]{member.getString("rule")});}
            else{
                String base=a.optString("deadline",g.deadline);if(base.isEmpty())base=scheduled;if(base.isEmpty())base=day();
                LocalDate anchor=LocalDate.parse(base),n=next(anchor,anchor,cad);while(!n.isAfter(LocalDate.now()))n=next(n,anchor,cad);
                String rule=member==null?Journal.uuid():member.getString("rule");
                if(member==null)db.execSQL("INSERT INTO repeat_rules VALUES(?,?,?,?,?)",new Object[]{rule,cad,n.toString(),id,anchor.toString()});else db.execSQL("UPDATE repeat_rules SET cadence=?,next_day=?,template=?,anchor=? WHERE id=?",new Object[]{cad,n.toString(),id,anchor.toString(),rule});
                db.execSQL("INSERT OR REPLACE INTO repeat_members VALUES(?,?)",new Object[]{id,rule});
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static synchronized void generate(Journal j)throws JSONException{
        SQLiteDatabase db=j.getWritableDatabase();db.beginTransaction();
        try{
            LocalDate today=LocalDate.now();
            for(JSONObject r:GoalBook.items(j.rows("SELECT * FROM repeat_rules WHERE cadence!='none' AND next_day<=?",today.toString()))){
                String template=r.getString("template"),rule=r.getString("id"),cad=r.getString("cadence");LocalDate d=LocalDate.parse(r.getString("next_day")),anchor=LocalDate.parse(r.getString("anchor"));
                JSONObject t=j.rows("SELECT * FROM goals WHERE id=?",template).optJSONObject(0);if(t==null)continue;
                JSONObject e=j.rows("SELECT name FROM goal_edits WHERE goal=? ORDER BY since DESC LIMIT 1",template).optJSONObject(0);
                String name=e==null?t.getString("name"):e.getString("name"),period=t.getString("period");
                int limit=0;while(!d.isAfter(today)&&limit++<1000){
                    String id=Journal.uuid(),start=d.toString();db.execSQL("INSERT INTO goals VALUES(?,?,?,?,?,1)",new Object[]{id,name,start,Journal.end(d,period).toString(),period});
                    db.execSQL("INSERT INTO task_state VALUES(?,?,?,'','')",new Object[]{id,start,start});db.execSQL("INSERT INTO repeat_members VALUES(?,?)",new Object[]{id,rule});
                    db.execSQL("INSERT INTO task_dates VALUES(?,?)",new Object[]{id,start});d=next(d,anchor,cad);
                }
                db.execSQL("UPDATE repeat_rules SET next_day=? WHERE id=?",new Object[]{d.toString(),rule});
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static JSONObject options(Journal j)throws JSONException{return new JSONObject().put("order",j.rows("SELECT * FROM task_order")).put("schedule",j.rows("SELECT * FROM task_dates")).put("repeat",j.rows("SELECT m.goal,r.cadence,r.next_day FROM repeat_members m JOIN repeat_rules r ON r.id=m.rule")).put("habits",j.rows("SELECT * FROM habit_schedule ORDER BY since"));}
    static JSONObject timerParts(Journal j)throws JSONException{JSONObject p=j.rows("SELECT * FROM timer_parts WHERE slot=1").optJSONObject(0);return p==null?new JSONObject().put("paused",0).put("elapsed",0).put("segments","[]"):p;}
    static long nextAlarm(Journal j,String id,long after)throws JSONException{
        JSONObject n=j.note("module-data-calendar-reminders");if(n==null)return 0;JSONObject meta=new JSONObject(n.getString("body")).optJSONObject(id);if(meta==null||!meta.optBoolean("notify"))return 0;
        String cad=meta.optString("repeat","none");if(cad.equals("none"))return 0;
        LocalDate anchor=LocalDate.parse(meta.getString("date"));LocalTime time=LocalTime.parse(meta.getString("time"));ZoneId zone=ZoneId.systemDefault();
        LocalDate d=anchor;LocalDate cutoff=Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
        if(cad.equals("daily")&&d.isBefore(cutoff))d=cutoff;
        else if(cad.equals("weekly")&&d.isBefore(cutoff))d=d.plusWeeks(java.time.temporal.ChronoUnit.WEEKS.between(d,cutoff));
        else if(cad.equals("monthly")&&d.isBefore(cutoff)){d=d.plusMonths(Math.max(0,java.time.temporal.ChronoUnit.MONTHS.between(d.withDayOfMonth(1),cutoff.withDayOfMonth(1))));d=d.withDayOfMonth(Math.min(anchor.getDayOfMonth(),d.lengthOfMonth()));}
        else if(cad.equals("yearly")&&d.isBefore(cutoff)){d=d.plusYears(Math.max(0,cutoff.getYear()-d.getYear()));d=d.withDayOfMonth(Math.min(anchor.getDayOfMonth(),d.lengthOfMonth()));}
        while(d.atTime(time).atZone(zone).toInstant().toEpochMilli()<=after)d=next(d,anchor,cad);
        return d.atTime(time).atZone(zone).toInstant().toEpochMilli();
    }
    static synchronized void backup(Context c,Journal j,boolean force){
        android.content.SharedPreferences prefs=c.getSharedPreferences("standalone_backup",0);
        String uri=prefs.getString("folder",""),today=day();if(uri.isEmpty()||(!force&&today.equals(prefs.getString("last",""))))return;
        try{
            byte[] data=j.backup().toString().getBytes(StandardCharsets.UTF_8);
            Uri tree=Uri.parse(uri),root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            String filename="Daybook-"+today+".json";Uri file=null;
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            try(android.database.Cursor cur=c.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
                if(cur!=null)while(cur.moveToNext())if(filename.equals(cur.getString(1))){file=DocumentsContract.buildDocumentUriUsingTree(tree,cur.getString(0));break;}
            }
            if(file==null)file=DocumentsContract.createDocument(c.getContentResolver(),root,"application/json",filename);
            if(file==null)throw new IOException("Cannot create backup");
            try(OutputStream out=c.getContentResolver().openOutputStream(file,"wt")){if(out==null)throw new IOException("Cannot open backup");out.write(data);}
            prefs.edit().putString("last",today).putString("error","").apply();
        }catch(Exception e){prefs.edit().putString("error",String.valueOf(e.getMessage())).apply();}
    }
    static JSONObject backupStatus(Context c)throws JSONException{android.content.SharedPreferences p=c.getSharedPreferences("standalone_backup",0);return new JSONObject().put("enabled",!p.getString("folder","").isEmpty()).put("last",p.getString("last","")).put("error",p.getString("error",""));}
}
