"""Patch the checked-in native 1.13-compatible bridge on the isolated branch."""
from pathlib import Path
import shutil
import sys
root=Path(sys.argv[1])
src=root/"src/com/marcel/daybook"
extras=Path(__file__).parent

def change(file, old, new):
    p=src/file
    s=p.read_text()
    if s.count(old)!=1:
        raise ValueError(f"Unexpected source: {file}: {old[:100]}")
    p.write_text(s.replace(old,new,1))

for name in ("Targets.java","BackupStore.java","BackupJob.java"):
    shutil.copyfile(extras/name,src/name)

change("Journal.java",'super(c, "daybook.db", null, 5);','super(c, "daybook.db", null, 6);')
change("Journal.java",'upgradeVisibility(db);Investment.schema(db);Investment.initialize(db);','upgradeVisibility(db);Investment.schema(db);Investment.initialize(db);Targets.schema(db);Targets.initialize(db);Investment.timerSchema(db);')
change("Journal.java",'if(old<5){Investment.schema(db);Investment.initialize(db);} }','if(old<5){Investment.schema(db);Investment.initialize(db);} if(old<6){Targets.schema(db);Targets.initialize(db);Investment.timerSchema(db);} }')
change("Journal.java",'"tracking_epoch","method_time","task_state"}) result.put','"tracking_epoch","method_time","task_state","method_target_schedule","method_target_override"}) result.put')
change("Journal.java",'db.setTransactionSuccessful(); return result;','result.put("pending_timer",Investment.backupTimer(this));db.setTransactionSuccessful(); return result;')
change("Journal.java",'new String[]{"method_timer","method_time","task_state"','new String[]{"method_timer_segment","method_timer","method_target_override","method_target_schedule","method_time","task_state"')
change("Journal.java",'{"task_state","goal","since","deadline","completed","completed_name"}};','{"task_state","goal","since","deadline","completed","completed_name"},{"method_target_schedule","goal","since","weekday","weekend"},{"method_target_override","goal","day","seconds"}};')
change("Journal.java",'db.insertOrThrow(t[0],null,v);','if(t[0].equals("method_target_schedule")||t[0].equals("method_target_override")){if(!Investment.area(row.getString("goal")))throw new JSONException("Invalid target category.");LocalDate.parse(row.getString(t[0].equals("method_target_schedule")?"since":"day"));}db.insertOrThrow(t[0],null,v);')
change("Journal.java",'fillDates(db);seedFour(db);Investment.initialize(db);','fillDates(db);seedFour(db);Investment.initialize(db);Targets.initialize(db);Investment.restoreTimer(this,b.optJSONObject("pending_timer"));')
change("GoalBook.java",'String epoch="",taskSince="",deadline="",completed="",completedName="";','String epoch="",taskSince="",deadline="",completed="",completedName=""; Targets targets;')
change("GoalBook.java",'if(timed(d))return seconds(d)>=3600;','if(timed(d))return target(d)>0&&seconds(d)>=target(d);')
change("GoalBook.java",'int seconds(LocalDate d){return time.getOrDefault(d.toString(),0);}','int seconds(LocalDate d){return time.getOrDefault(d.toString(),0);}\n        int target(LocalDate d){return targets.seconds(id,d);}')
change("GoalBook.java",'String epoch=Investment.epoch(db);for(Goal g:goals.values())g.epoch=epoch;','String epoch=Investment.epoch(db);Targets targets=new Targets(db);for(Goal g:goals.values()){g.epoch=epoch;g.targets=targets;}')
change("GoalBook.java",'.put("seconds",g.seconds(now)).put("legacyDone"','.put("seconds",g.seconds(now)).put("target",g.timed(now)?g.target(now):0).put("legacyDone"')
change("GoalBook.java",'.put("seconds",g.seconds(d)).put("legacyDone"','.put("seconds",g.seconds(d)).put("target",g.timed(d)?g.target(d):0).put("legacyDone"')
change("Widget.java",'static android.graphics.Bitmap bubble(String color,boolean done,int seconds){','static android.graphics.Bitmap bubble(String color,boolean done,int seconds){return bubble(color,done,seconds,3600);}\n    static android.graphics.Bitmap bubble(String color,boolean done,int seconds,int target){')
change("Widget.java",'seconds/3600f','target>0?seconds/(float)target:0f')
change("Widget.java",'String progress=timer.equals(gid)?"Timing":seconds/60+"/60m";','int target=goal.optInt("target",3600);String progress=timer.equals(gid)?(Investment.timer(journal).optBoolean("paused")?"Paused":"Timing"):seconds/60+"/"+target/60+"m";')
change("Widget.java",'bubble(colors.get(gid),seconds>=3600,seconds)','bubble(colors.get(gid),target>0&&seconds>=target,seconds,target)')

# Persist stopwatch segments while paused; the original native stopwatch already survives UI closure.
p=src/"Investment.java";s=p.read_text()
start=s.index("    static JSONObject timer(");end=s.index("    static JSONObject stats(",start)
s=s[:start]+r'''
    static void timerSchema(SQLiteDatabase db){
        db.execSQL("CREATE TABLE method_timer_segment(id INTEGER PRIMARY KEY,started INTEGER NOT NULL,ended INTEGER NOT NULL)");
    }
    static JSONArray segments(Journal j)throws JSONException{return j.rows("SELECT started,ended FROM method_timer_segment ORDER BY id");}
    static void validSegment(long start,long end){
        if(start<=0||end<start||end-start>7L*86400000)throw new IllegalArgumentException("Invalid stopwatch duration. Correct the device clock or discard the stopwatch.");
    }
    static JSONObject timer(Journal j)throws JSONException{
        JSONObject t=j.rows("SELECT goal,started,zone FROM method_timer WHERE slot=1").optJSONObject(0);
        if(t==null)return new JSONObject();
        long elapsed=0;for(JSONObject r:GoalBook.items(segments(j)))elapsed+=r.getLong("ended")-r.getLong("started");
        long start=t.getLong("started");
        if(start>0)elapsed+=Math.max(0,System.currentTimeMillis()-start);
        return t.put("paused",start==0).put("elapsedMillis",elapsed);
    }
    static void start(Journal j,String id,String day){
        today(day);if(!area(id))throw new IllegalArgumentException("Choose a Method of 4 category.");
        SQLiteDatabase db=j.getWritableDatabase();db.beginTransaction();
        try{
            if(DatabaseUtils.longForQuery(db,"SELECT count(*) FROM method_timer",null)>0)throw new IllegalArgumentException("Finish the running stopwatch first.");
            db.delete("method_timer_segment",null,null);
            db.execSQL("INSERT INTO method_timer VALUES(1,?,?,?)",new Object[]{id,System.currentTimeMillis(),ZoneId.systemDefault().getId()});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static void pause(Journal j,String id)throws JSONException{
        SQLiteDatabase db=j.getWritableDatabase();db.beginTransaction();
        try{
            JSONObject t=timer(j);if(!id.equals(t.optString("goal"))||t.optBoolean("paused"))throw new IllegalArgumentException("This stopwatch is not running.");
            long start=t.getLong("started"),end=System.currentTimeMillis();validSegment(start,end);
            db.execSQL("INSERT INTO method_timer_segment(started,ended) VALUES(?,?)",new Object[]{start,end});
            db.execSQL("UPDATE method_timer SET started=0 WHERE slot=1");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static void resume(Journal j,String id)throws JSONException{
        SQLiteDatabase db=j.getWritableDatabase();db.beginTransaction();
        try{
            JSONObject t=timer(j);if(!id.equals(t.optString("goal"))||!t.optBoolean("paused"))throw new IllegalArgumentException("This stopwatch is not paused.");
            db.execSQL("UPDATE method_timer SET started=? WHERE slot=1",new Object[]{System.currentTimeMillis()});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static JSONObject backupTimer(Journal j)throws JSONException{
        JSONObject t=timer(j);if(!t.has("goal"))return new JSONObject();
        JSONArray rows=segments(j);
        if(t.getLong("started")>0)rows.put(new JSONObject().put("started",t.getLong("started")).put("ended",Math.max(t.getLong("started"),System.currentTimeMillis())));
        return new JSONObject().put("goal",t.getString("goal")).put("zone",t.getString("zone")).put("segments",rows);
    }
    static void restoreTimer(Journal j,JSONObject t)throws JSONException{
        if(t==null||!t.has("goal"))return;
        String id=t.getString("goal"),zone=t.getString("zone");
        if(!area(id))throw new JSONException("Invalid stopwatch category.");
        ZoneId.of(zone);SQLiteDatabase db=j.getWritableDatabase();
        db.execSQL("INSERT INTO method_timer VALUES(1,?,0,?)",new Object[]{id,zone});
        long last=0;for(JSONObject r:GoalBook.items(t.getJSONArray("segments"))){
            long start=r.getLong("started"),end=r.getLong("ended");validSegment(start,end);
            if(start<last)throw new JSONException("Overlapping stopwatch segments.");last=end;
            db.execSQL("INSERT INTO method_timer_segment(started,ended) VALUES(?,?)",new Object[]{start,end});
        }
    }
    static void stop(Journal j,String id,boolean discard)throws JSONException{
        SQLiteDatabase db=j.getWritableDatabase();db.beginTransaction();
        try{
            JSONObject t=timer(j);if(!id.equals(t.optString("goal")))throw new IllegalArgumentException("This stopwatch is not active.");
            if(!discard){
                JSONObject saved=backupTimer(j);ZoneId zone=ZoneId.of(saved.getString("zone"));
                Map<String,Long> durations=new TreeMap<>();
                for(JSONObject r:GoalBook.items(saved.getJSONArray("segments"))){
                    long cursor=r.getLong("started"),end=r.getLong("ended");validSegment(cursor,end);
                    while(cursor<end){
                        LocalDate date=Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate();
                        long next=Math.min(end,date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli());
                        durations.merge(date.toString(),next-cursor,Long::sum);cursor=next;
                    }
                }
                for(Map.Entry<String,Long> r:durations.entrySet()){
                    int count=(int)(r.getValue()/1000);if(count>0)write(db,id,r.getKey(),seconds(db,id,r.getKey())+count);
                }
            }
            db.delete("method_timer_segment",null,null);db.delete("method_timer",null,null);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
''' + s[end:]
p.write_text(s)
change("Investment.java",'try(Cursor c=db.rawQuery("SELECT COALESCE(sum(CASE WHEN seconds>3600 THEN seconds-3600 ELSE 0 END),0),COALESCE(sum(CASE WHEN seconds>3600 THEN 3600 ELSE seconds END),0) FROM method_time WHERE day>=? AND day<?",new String[]{a,b})){if(c.moveToFirst()){extra=c.getLong(0);balanced=c.getLong(1);}}',r'''Targets targets=new Targets(j);
        try(Cursor c=db.rawQuery("SELECT goal,day,seconds FROM method_time WHERE day>=? AND day<?",new String[]{a,b})){
            while(c.moveToNext()){int target=targets.seconds(c.getString(0),LocalDate.parse(c.getString(1))),actual=c.getInt(2);extra+=Math.max(0,actual-target);balanced+=Math.min(actual,target);}
        }''')
change("Investment.java",'return new JSONObject().put("areas",totals).put("total",total).put("extra",extra).put("balanced",balanced).put("target",Math.max(0,java.time.temporal.ChronoUnit.DAYS.between(begin,cutoff))*14400)',r'''JSONObject areaTargets=new JSONObject(),plannedTargets=new JSONObject();long totalTarget=0,planned=0;
        for(String id:AREAS){
            long current=0,future=0;
            for(LocalDate d=begin;d.isBefore(until);d=d.plusDays(1)){int n=targets.seconds(id,d);future+=n;if(d.isBefore(cutoff))current+=n;}
            areaTargets.put(id,current);plannedTargets.put(id,future);totalTarget+=current;planned+=future;
        }
        return new JSONObject().put("areas",totals).put("areaTargets",areaTargets).put("plannedTargets",plannedTargets).put("plannedTarget",planned).put("total",total).put("extra",extra).put("balanced",balanced).put("target",totalTarget)''')

change("Main.java",'case "methodStop":Investment.stop','case "methodPause":Investment.pause(journal,a.getString("id"));Widget.refresh(Main.this);break;\n                case "methodResume":Investment.resume(journal,a.getString("id"));Widget.refresh(Main.this);break;\n                case "targetConfig":res=new Targets(journal).config(LocalDate.parse(a.getString("day")));break;\n                case "targetEdit":Targets.edit(journal,a);Widget.refresh(Main.this);break;\n                case "methodStop":Investment.stop')
# Standalone app must never access the previous updater.
change("Main.java",'updates.onResume();','BackupStore.request(this);BackupStore.schedule(this);')
change("Main.java",'@Override protected void onPause(){clock.removeCallbacks(tick);super.onPause();}','@Override protected void onPause(){clock.removeCallbacks(tick);BackupStore.request(this);super.onPause();}')
change("Main.java",'case "check":runOnUiThread(()->updates.check(true));break;','case "check":tell("This separate build has no automatic update channel.");break;')
change("Main.java",'case "backup":if(Investment.timer(journal).has("goal"))throw new IllegalArgumentException("Stop and save the stopwatch before backing up.");','case "autoBackupStatus":res=BackupStore.status(Main.this);break;\n                case "autoBackupFolder":runOnUiThread(()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),12));break;\n                case "autoBackupNow":BackupStore.now(Main.this);break;\n                case "autoBackupRestore":runOnUiThread(()->restoreLocal());break;\n                case "backup":')
change("Main.java",'return new JSONObject().put("ok",true)','if(BackupStore.isMutation(op))BackupStore.request(Main.this);\n                return new JSONObject().put("ok",true)')
change("Main.java",'Uri uri=data.getData(); if(r==10)','Uri uri=data.getData(); if(r==12){try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));BackupStore.folder(this,uri);BackupStore.now(this);tell("Automatic backup folder saved.");event("refresh","");}catch(Exception e){tell("Cannot use this folder: "+e.getMessage());}return;} if(r==10)')
change("Main.java",'journal.restore(new JSONObject(buf.toString("UTF-8")));','BackupStore.beforeRestore(this);journal.restore(new JSONObject(buf.toString("UTF-8")));BackupStore.request(this);')
change("Main.java",'Any running stopwatch is discarded.','A backed-up stopwatch is restored paused.')
change("Main.java",'    @Override protected void onActivityResult','''    void restoreLocal(){
        new AlertDialog.Builder(this).setTitle("Restore latest automatic backup?")
            .setMessage("This replaces the current journal. A safety copy is saved first.")
            .setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->new Thread(()->{
                try{
                    JSONObject data=BackupStore.latest(this);
                    JSONArray old=journal.rows("SELECT id FROM notes WHERE alarm>0");
                    BackupStore.beforeRestore(this);journal.restore(data);
                    for(int k=0;k<old.length();k++)Reminders.plan(this,old.getJSONObject(k).getString("id"),0);
                    getSystemService(NotificationManager.class).cancelAll();Reminders.all(this);Widget.refresh(this);
                    route="today";deliver();tell("Journal restored.");BackupStore.request(this);
                }catch(Exception e){tell("Restore failed: "+e.getMessage());}
            }).start()).show();
    }
    @Override protected void onActivityResult''')

# The source namespace and the manifest package must both change so all Android components
# and content-provider authorities are isolated. The legacy JSON format string is preserved.
for p in src.glob("*.java"):
    s=p.read_text().replace("com.marcel.daybook","com.marcel.daybookenhanced")
    s=s.replace("com.marcel.daybookenhanced/1","com.marcel.daybook/1")
    p.write_text(s)
manifest=root/"AndroidManifest.xml"
s=manifest.read_text().replace("com.marcel.daybook","com.marcel.daybookenhanced")
s=s.replace('android:versionCode="9" android:versionName="1.8"','android:versionCode="15" android:versionName="1.14"')
s=s.replace('android:label="Daybook"','android:label="Daybook Enhanced"')
s=s.replace('    <provider','    <service android:name=".BackupJob" android:permission="android.permission.BIND_JOB_SERVICE" android:exported="false"/>\n    <provider')
s=s.replace('  <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>','')
manifest.write_text(s)
shutil.copyfile(extras/"EnhancedSmoke.java",root/"test/EnhancedSmoke.java")
for p in (root/"test").glob("*"):
    if p.is_file() and p.suffix in (".java",".xml",".sh"):
        s=p.read_text().replace("com.marcel.daybook","com.marcel.daybookenhanced")
        s=s.replace("com.marcel.daybookenhanced/1","com.marcel.daybook/1")
        p.write_text(s)

for name in ("run.sh","AndroidManifest.xml"):
    p=root/"test"/name
    s=p.read_text().replace("test/Smoke.java","test/EnhancedSmoke.java").replace("com.marcel.daybookenhanced.Smoke","com.marcel.daybookenhanced.EnhancedSmoke")
    p.write_text(s)
