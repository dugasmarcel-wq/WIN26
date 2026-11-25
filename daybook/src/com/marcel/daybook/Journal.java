package com.marcel.daybook;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

final class Journal extends SQLiteOpenHelper {
    private static Journal instance;
    static synchronized Journal get(Context c) {
        if (instance == null) instance = new Journal(c.getApplicationContext());
        return instance;
    }
    private Journal(Context c) { super(c, "daybook.db", null, 3); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE folders(id TEXT PRIMARY KEY,name TEXT NOT NULL UNIQUE COLLATE NOCASE)");
        db.execSQL("CREATE TABLE goals(id TEXT PRIMARY KEY,name TEXT NOT NULL,start TEXT NOT NULL,end TEXT NOT NULL,period TEXT NOT NULL,pinned INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE marks(goal TEXT REFERENCES goals(id),day TEXT NOT NULL,PRIMARY KEY(goal,day))");
        db.execSQL("CREATE TABLE notes(id TEXT PRIMARY KEY,folder TEXT NOT NULL REFERENCES folders(id),title TEXT NOT NULL,body TEXT NOT NULL,changed INTEGER NOT NULL,alarm INTEGER NOT NULL)");
        db.execSQL("INSERT INTO folders VALUES('daily','Daily'),('long','Long Term')");
        upgradeCalendar(db);upgradeMethod(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next) { if(old<2)upgradeCalendar(db);if(old<3)upgradeMethod(db); }
    private void upgradeMethod(SQLiteDatabase db){
        db.execSQL("CREATE TABLE goal_edits(goal TEXT REFERENCES goals(id),since TEXT,name TEXT NOT NULL,start TEXT NOT NULL,end TEXT NOT NULL,PRIMARY KEY(goal,since))");
        db.execSQL("CREATE TABLE reflections(goal TEXT REFERENCES goals(id),day TEXT,body TEXT NOT NULL,PRIMARY KEY(goal,day))");
        db.execSQL("CREATE TABLE weekly_taps(goal TEXT,week TEXT,day TEXT NOT NULL,PRIMARY KEY(goal,week),FOREIGN KEY(goal,week) REFERENCES marks(goal,day) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE colors(target TEXT PRIMARY KEY,value TEXT NOT NULL)");
    }
    void editGoal(JSONObject a)throws JSONException{
        String id=a.getString("id"),name=title(a.getString("name"));
        LocalDate today=LocalDate.now();GoalBook.Goal g=new GoalBook(this).goals.get(id);
        if(g==null)throw new IllegalArgumentException("Goal missing");
        LocalDate start=LocalDate.parse(a.getString("start")),end=LocalDate.parse(a.getString("end"));
        if(!end.isAfter(start))throw new IllegalArgumentException("End date must be on or after the start date.");
        if(start.isBefore(g.first()))throw new IllegalArgumentException("A goal cannot start before it was created.");
        if(g.active(today)&&g.marks.contains(g.key(today))&&(today.isBefore(start)||!today.isBefore(end)))throw new IllegalArgumentException("Undo the current check-in before moving this goal.");
        getWritableDatabase().execSQL("INSERT OR REPLACE INTO goal_edits VALUES(?,?,?,?,?)",new Object[]{id,today.toString(),name,start.toString(),end.toString()});
    }
    private void upgradeCalendar(SQLiteDatabase db){
        db.execSQL("ALTER TABLE notes ADD COLUMN written TEXT");
        db.execSQL("ALTER TABLE notes ADD COLUMN slot INTEGER");
        db.execSQL("CREATE UNIQUE INDEX notebook_slot ON notes(slot)");
        db.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY, name TEXT NOT NULL, day TEXT NOT NULL, annual INTEGER NOT NULL)");
        fillDates(db);seedFour(db);
    }
    private void fillDates(SQLiteDatabase db){
        db.execSQL("UPDATE notes SET written=CASE WHEN id LIKE 'day-%' THEN substr(id,5) ELSE date(changed/1000,'unixepoch','localtime') END WHERE written IS NULL");
    }
    private void seedFour(SQLiteDatabase db){
        String[][] four={{"spiritual","Read the Bible"},{"financial","Study finances"},{"mental","Study Japanese"},{"physical","Go to the gym"}};
        for(String[] item:four)db.execSQL("INSERT OR IGNORE INTO goals VALUES(?,?,?,'9999-12-31','Daily',1)",
            new Object[]{"four-"+item[0],item[1],LocalDate.now().toString()});
    }
    void place(String id,int slot){
        if(slot!=0 && slot!=2 && slot!=3)throw new IllegalArgumentException("Invalid slot");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            if(DatabaseUtils.longForQuery(db,"SELECT count(*) FROM notes WHERE id=?",new String[]{id})!=1)throw new IllegalArgumentException("Note missing");
            if(slot!=0)db.execSQL("UPDATE notes SET slot=NULL WHERE slot=?",new Object[]{slot});
            db.execSQL("UPDATE notes SET slot=? WHERE id=?",new Object[]{slot==0?null:slot,id});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static String uuid() { return UUID.randomUUID().toString(); }
    static String title(String s) { s=s.trim(); if(s.isEmpty() || s.length()>200) throw new IllegalArgumentException("Use a name between 1 and 200 characters."); return s; }
    static LocalDate end(LocalDate start,String period) {
        switch(period) {
            case "Daily":
            case "Weekly": return LocalDate.of(9999,12,31);
            case "Week": return start.plusWeeks(1);
            case "Month": return start.plusMonths(1);
            case "6 months": return start.plusMonths(6);
            case "Year": return start.plusYears(1);
            default: throw new IllegalArgumentException("Unknown goal period.");
        }
    }
    void addGoal(String name,String period) {
        addGoal(name,period,"","");
    }
    void addGoal(String name,String period,String from,String through){
        LocalDate created=LocalDate.now(), start=from.isEmpty()?created:LocalDate.parse(from);
        LocalDate end=through.isEmpty()?end(start,period):LocalDate.parse(through).plusDays(1);
        if(start.isBefore(created)||!end.isAfter(start))throw new IllegalArgumentException("Choose an available date range.");
        String id=uuid(),label=title(name);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            db.execSQL("INSERT INTO goals VALUES(?,?,?,?,?,1)",new Object[]{id,label,created.toString(),end(created,period).toString(),period});
            if(!from.isEmpty()||!through.isEmpty())db.execSQL("INSERT INTO goal_edits VALUES(?,?,?,?,?)",new Object[]{id,created.toString(),label,start.toString(),end.toString()});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void addFolder(String name) { getWritableDatabase().execSQL("INSERT INTO folders VALUES(?,?)",new Object[]{uuid(),title(name)}); }
    boolean mark(String id,String shownDay,boolean done) {
        String today=LocalDate.now().toString();
        if (!today.equals(shownDay)) return false;
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            GoalBook.Goal goal=new GoalBook(this).goals.get(id);
            if(goal==null||!goal.active(LocalDate.parse(today)))return false;
            String[] key={id,goal.key(LocalDate.parse(today))};
            if(done){
                db.execSQL("INSERT OR IGNORE INTO marks VALUES(?,?)",key);
                if(goal.weekly&&!goal.marks.contains(key[1]))db.execSQL("INSERT OR IGNORE INTO weekly_taps VALUES(?,?,?)",new Object[]{id,key[1],today});
            }
            else db.delete("marks","goal=? AND day=?",key);
            db.setTransactionSuccessful();
        }catch(JSONException e){throw new IllegalStateException(e);}finally{db.endTransaction();}
        return true;
    }
    void pin(String id,boolean pin) {getWritableDatabase().execSQL("UPDATE goals SET pinned=? WHERE id=?",new Object[]{pin?1:0,id});}
    JSONArray rows(String query,String... args) throws JSONException {
        JSONArray out=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery(query,args)) {
            while(c.moveToNext()) {
                JSONObject row=new JSONObject(); for(int i=0;i<c.getColumnCount();i++) {
                    row.put(c.getColumnName(i),c.getType(i)==Cursor.FIELD_TYPE_INTEGER?(Object)c.getLong(i):c.getString(i));
                } out.put(row);
            }
        } return out;
    }
    static LocalDate monday(LocalDate day) { return day.minusDays(day.getDayOfWeek().getValue()-1); }
    JSONArray goals(LocalDate now) throws JSONException {
        return new GoalBook(this).snapshot(now);
    }
    String ensureDailyNote(LocalDate day) {
        String id="day-"+day;
        String title=day.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale.getDefault());
        getWritableDatabase().execSQL("INSERT OR IGNORE INTO notes(id,folder,title,body,changed,alarm,written) VALUES(?,'daily',?,'',?,0,?)",
            new Object[]{id,title,System.currentTimeMillis(),day.toString()});
        return id;
    }
    JSONObject snapshot() throws JSONException {
        LocalDate d=LocalDate.now();
        String dailyId=ensureDailyNote(d);
        return new JSONObject().put("dailyId",dailyId).put("today",d.toString()).put("goals",goals(d))
            .put("colors",rows("SELECT * FROM colors")).put("events",rows("SELECT * FROM events ORDER BY day,name")).put("folders",rows("SELECT * FROM folders ORDER BY rowid"))
            .put("notes",rows("SELECT id,folder,title,substr(body,1,180) AS excerpt,changed,alarm,written,slot FROM notes ORDER BY changed DESC"));
    }
    JSONObject note(String id) throws JSONException {return rows("SELECT * FROM notes WHERE id=?",id).optJSONObject(0);}
    synchronized void saveNote(String id,String folder,String title,String body) {
        ContentValues v=new ContentValues();v.put("folder",folder);v.put("title",title);v.put("body",body);v.put("changed",System.currentTimeMillis());
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            if(db.update("notes",v,"id=?",new String[]{id})==0){v.put("id",id);v.put("alarm",0);v.put("written",LocalDate.now().toString());db.insertOrThrow("notes",null,v);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    synchronized long setAlarm(String id,long time) {
        getWritableDatabase().execSQL("UPDATE notes SET alarm=? WHERE id=?",new Object[]{time,id}); return time;
    }
    boolean clearIfDue(String id,long time) {
        ContentValues values=new ContentValues();values.put("alarm",0);
        return getWritableDatabase().update("notes",values,"id=? AND alarm=?",new String[]{id,String.valueOf(time)})==1;
    }
    JSONObject backup() throws JSONException {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            JSONObject result=new JSONObject().put("format","com.marcel.daybook/1");
            for(String table:new String[]{"folders","goals","marks","notes","events","goal_edits","reflections","colors","weekly_taps"}) result.put(table,rows("SELECT * FROM "+table+" ORDER BY rowid"));
            db.setTransactionSuccessful(); return result;
        } finally {db.endTransaction();}
    }
    // Restore in one transaction. A malformed file cannot partially replace a journal.
    void restore(JSONObject b) throws JSONException {
        if(!"com.marcel.daybook/1".equals(b.getString("format"))) throw new JSONException("Choose a Daybook backup.");
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            for(String t:new String[]{"weekly_taps","colors","reflections","goal_edits","events","marks","notes","goals","folders"}) db.delete(t,null,null);
            String[][] schema={{"folders","id","name"},{"goals","id","name","start","end","period","pinned"},{"marks","goal","day"},{"notes","id","folder","title","body","changed","alarm"}};
            for(String[] fields:schema) {
                JSONArray list=b.getJSONArray(fields[0]);
                for(int i=0;i<list.length();i++) {
                    JSONObject row=list.getJSONObject(i); ContentValues v=new ContentValues();
                    for(int j=1;j<fields.length;j++) v.put(fields[j],row.getString(fields[j]));
                    if(fields[0].equals("goals")) {
                        LocalDate start=LocalDate.parse(row.getString("start"));
                        if(!end(start,row.getString("period")).toString().equals(row.getString("end"))) throw new JSONException("Invalid goal dates.");
                    }
                    if(fields[0].equals("marks")) LocalDate.parse(row.getString("day"));
                    if(fields[0].equals("notes")) {row.getLong("alarm");row.getLong("changed");if(!row.isNull("written"))v.put("written",LocalDate.parse(row.getString("written")).toString());if(!row.isNull("slot"))v.put("slot",row.getInt("slot"));}
                    db.insertOrThrow(fields[0],null,v);
                }
            }
            JSONArray events=b.optJSONArray("events");
            if(events!=null)for(int i=0;i<events.length();i++){
                JSONObject e=events.getJSONObject(i);
                db.execSQL("INSERT INTO events VALUES(?,?,?,?)",new Object[]{e.getString("id"),title(e.getString("name")),LocalDate.parse(e.getString("day")).toString(),e.getInt("annual")});
            }
            String[][] optional={{"weekly_taps","goal","week","day"},{"goal_edits","goal","since","name","start","end"},{"reflections","goal","day","body"},{"colors","target","value"}};
            for(String[] t:optional)if(b.has(t[0]))for(JSONObject row:GoalBook.items(b.getJSONArray(t[0]))){
                ContentValues v=new ContentValues();for(int i=1;i<t.length;i++)v.put(t[i],row.getString(t[i]));db.insertOrThrow(t[0],null,v);
            }
            fillDates(db);seedFour(db);
            if(DatabaseUtils.longForQuery(db,"SELECT count(*) FROM folders WHERE id IN ('daily','long')",null)!=2) throw new JSONException("Missing default folders.");
            if(DatabaseUtils.longForQuery(db,"SELECT count(*) FROM marks JOIN goals ON goal=id WHERE day<start",null)!=0) throw new JSONException("Invalid check-in date.");
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
}
