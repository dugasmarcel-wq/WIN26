package com.marcel.daybook;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public final class Main extends Activity {
    WebView web; Journal journal; boolean ready; String route="today"; Updates updates;
    private final Handler clock=new Handler(Looper.getMainLooper());
    private String shownDay="";
    private final Runnable tick=new Runnable(){public void run(){
        String d=LocalDate.now().toString();
        if(!d.equals(shownDay)){shownDay=d;event("refresh","");Widget.refresh(Main.this);}
        clock.postDelayed(this,15000);
    }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);journal=Journal.get(this);updates=new Updates(this);
        getWindow().setStatusBarColor(0xFFF8F6EF);getWindow().setNavigationBarColor(0xFFF8F6EF);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        web=new WebView(this);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackgroundColor(0xFFF8F6EF);
        frame.addView(web);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            frame.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets safe = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        web.setBackgroundColor(0xFFF8F6EF);
        setContentView(frame);
        frame.requestApplyInsets();
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setBlockNetworkLoads(true);
        web.addJavascriptInterface(new Bridge(),"Daybook");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public void onPageFinished(WebView v,String url){ready=true;deliver();}
        });
        readIntent(getIntent());if(state!=null)route=state.getString("route",route);
        web.loadUrl("file:///android_asset/index.html");Reminders.channel(this);
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("route",route);}
    private void readIntent(Intent i){
        Uri u=i.getData(); if(u!=null && "daybook".equals(u.getScheme())&&"open".equals(u.getHost())&&u.getPath()!=null)route=u.getPath().substring(1);
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);readIntent(i);deliver();}
    void deliver(){event("route",route);}
    void event(String type,String value){runOnUiThread(()->{if(ready&&!isDestroyed())web.evaluateJavascript("window.nativeEvent("+JSONObject.quote(type)+","+JSONObject.quote(value)+")",null);});}
    void tell(String message){runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());}
    @Override protected void onResume(){super.onResume();clock.removeCallbacks(tick);tick.run();event("refresh","");new Thread(()->Extras.backup(this,journal,false)).start();
        try{if(Reminders.allowed(this))Reminders.all(this);}catch(JSONException e){tell("Could not restore reminders.");}}
    @Override protected void onPause(){clock.removeCallbacks(tick);new Thread(()->Extras.backup(this,journal,true)).start();super.onPause();}
    @Override public void onBackPressed(){web.evaluateJavascript("window.goBack()",r->{if(!"true".equals(r))super.onBackPressed();});}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);event("refresh","");if(r==7 && !Reminders.allowed(this))tell("Notifications are off. Your reminder is saved.");}
    void notificationSettings(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED && !getPreferences(0).getBoolean("asked",false)){
            getPreferences(0).edit().putBoolean("asked",true).apply();requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},7);
        }else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
    }
    final class Bridge {
        @JavascriptInterface public String call(String op,String arg) {
            try{
                JSONObject a=new JSONObject(arg);Object res=true;
                switch(op){
                case "tracker":{
                    boolean year=a.getBoolean("year");
                    LocalDate first=LocalDate.parse(a.getString("start")).withDayOfMonth(1);
                    if(year)first=first.withDayOfYear(1);
                    res=new Tracker(journal,first,year?first.plusYears(1):first.plusMonths(1),LocalDate.now()).json(year);
                    break;
                }
                case "methodLog":Investment.log(journal,a);Widget.refresh(Main.this);break;
                case "methodStart":Investment.start(journal,a.getString("id"),a.getString("day"));Widget.refresh(Main.this);break;
                case "methodPause":Investment.pause(journal,a.getString("id"));Widget.refresh(Main.this);break;
                case "methodResume":Investment.resume(journal,a.getString("id"));Widget.refresh(Main.this);break;
                case "methodStop":Investment.stop(journal,a.getString("id"),a.optBoolean("discard"));Widget.refresh(Main.this);break;
                case "investmentStats":res=Investment.stats(journal,a.getString("from"),a.getString("until"));break;
                case "removeGoal":journal.removeGoal(a.getString("id"));Widget.refresh(Main.this);break;
                case "goalEdit":journal.editGoal(a);Widget.refresh(Main.this);break;
                case "goalHistory":res=new GoalBook(journal).history(a.getString("id"),LocalDate.now());break;
                case "reflections":res=journal.rows("SELECT goal,body FROM reflections WHERE day=?",LocalDate.parse(a.getString("day")).toString());break;
                case "reflect":journal.getWritableDatabase().execSQL("INSERT OR REPLACE INTO reflections VALUES(?,?,?)",new Object[]{a.getString("id"),LocalDate.parse(a.getString("day")).toString(),a.getString("body")});break;
                case "color":{
                    String color=a.getString("value");if(!color.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Invalid color");
                    journal.getWritableDatabase().execSQL("INSERT OR REPLACE INTO colors VALUES(?,?)",new Object[]{a.getString("target"),color});Widget.refresh(Main.this);break;
                }
                case "calendarGoals":res=new GoalBook(journal).month(LocalDate.parse(a.getString("day")).withDayOfMonth(1));break;
                case "dayGoals":res=journal.goals(LocalDate.parse(a.getString("day")));break;
                case "place":journal.place(a.getString("id"),a.getInt("slot"));break;
                case "rename":journal.getWritableDatabase().execSQL("UPDATE notes SET title=? WHERE id=?",new Object[]{Journal.title(a.getString("title")),a.getString("id")});break;
                case "removeNote":{
                    String id=a.getString("id");
                    journal.getWritableDatabase().delete("notes","id=?",new String[]{id});
                    Reminders.plan(Main.this,id,0);getSystemService(NotificationManager.class).cancel(id,1);break;
                }
                case "event":journal.getWritableDatabase().execSQL("INSERT INTO events VALUES(?,?,?,?)",new Object[]{Journal.uuid(),Journal.title(a.getString("name")),LocalDate.parse(a.getString("day")).toString(),a.getBoolean("annual")?1:0});break;
                case "removeEvent":journal.getWritableDatabase().delete("events","id=?",new String[]{a.getString("id")});break;
                case "state": res=journal.snapshot().put("notifications",Reminders.allowed(Main.this)).put("exact",Reminders.exact(Main.this)).put("version",getPackageManager().getPackageInfo(getPackageName(),0).versionName);break;
                case "habitSchedule":Extras.schedule(journal,a);Widget.refresh(Main.this);break;
                case "taskOptions":Extras.taskOptions(journal,a);Widget.refresh(Main.this);break;
                case "taskOrder":Extras.order(journal,a.getJSONArray("ids"));Widget.refresh(Main.this);break;
                case "goal":res=journal.addGoal(a.getString("name"),a.getString("period"),a.optString("from"),a.optString("through"));Widget.refresh(Main.this);break;
                case "mark":res=journal.mark(a.getString("id"),a.getString("day"),a.getBoolean("done"));Widget.refresh(Main.this);break;
                case "pin":journal.pin(a.getString("id"),a.getBoolean("on"));Widget.refresh(Main.this);break;
                case "history":res=journal.rows("SELECT day FROM marks WHERE goal=? ORDER BY day DESC",a.getString("id"));break;
                case "folder":journal.addFolder(a.getString("name"));break;
                case "uuid":res=Journal.uuid();break;
                case "note":res=journal.note(a.getString("id"));break;
                case "save":{
                    String id=a.getString("id");
                    journal.saveNote(id,a.getString("folder"),a.getString("title"),a.getString("body"));break;
                }
                case "alarm":{
                    String id=a.getString("id");long at=a.getLong("at");if(journal.note(id)==null)throw new Exception("Write a note first.");
                    if(at!=0&&at<=System.currentTimeMillis())throw new Exception("Choose a future time.");
                    journal.setAlarm(id,at);Reminders.plan(Main.this,id,at);
                    getSystemService(NotificationManager.class).cancel(id,1);
                    if(at>0&&!Reminders.allowed(Main.this))runOnUiThread(()->notificationSettings());break;
                }
                case "location":route=a.getString("route");break;
                case "permissions":runOnUiThread(()->notificationSettings());break;
                case "exact":if(Build.VERSION.SDK_INT>=31)runOnUiThread(()->startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))));break;
                case "check":throw new IllegalArgumentException("Standalone edition has no automatic updates.");
                case "backupStatus":res=Extras.backupStatus(Main.this);break;
                case "backupFolder":runOnUiThread(()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),12));break;
                case "backupNow":new Thread(()->{Extras.backup(Main.this,journal,true);event("refresh","");}).start();break;
                case "backup":runOnUiThread(()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"Daybook-"+LocalDate.now()+".json"),10));break;
                case "restore":runOnUiThread(()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),11));break;
                default:throw new Exception("Unknown command.");
                }
                return new JSONObject().put("ok",true).put("value",res==null?JSONObject.NULL:res).toString();
            }catch(Exception e){return "{\"ok\":false,\"error\":"+JSONObject.quote(e instanceof android.database.sqlite.SQLiteConstraintException?"That folder name already exists or the entry is invalid.":String.valueOf(e.getMessage()))+"}";}
        }
    }
    @Override protected void onActivityResult(int r,int result,Intent data){
        super.onActivityResult(r,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData(); if(r==12){try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));getSharedPreferences("standalone_backup",0).edit().putString("folder",uri.toString()).remove("last").apply();new Thread(()->{Extras.backup(this,journal,true);event("refresh","");}).start();}catch(Exception e){tell("Backup folder could not be saved.");}return;} if(r==10){new Thread(()->{
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){out.write(journal.backup().toString().getBytes(StandardCharsets.UTF_8));tell("Backup saved.");}
            catch(Exception e){tell("Backup failed: "+e.getMessage());}
        }).start();} else if(r==11)new AlertDialog.Builder(this).setTitle("Replace this journal?").setMessage("Restoring replaces your current notes, goals, check-ins and recorded time with the selected backup. The selected backup replaces this app’s records, including its stopwatch state.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream buf=new ByteArrayOutputStream()){
                byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){if(buf.size()+n>64*1024*1024)throw new IOException("Backup exceeds 64 MB.");buf.write(bytes,0,n);}
                JSONArray old=journal.rows("SELECT id FROM notes WHERE alarm>0");
                journal.restore(new JSONObject(buf.toString("UTF-8")));
                for(int k=0;k<old.length();k++)Reminders.plan(this,old.getJSONObject(k).getString("id"),0);
                getSystemService(NotificationManager.class).cancelAll();Reminders.all(this);Widget.refresh(this);route="today";deliver();tell("Journal restored.");
            }catch(Exception e){tell("Restore failed: "+e.getMessage());}
        }).start()).show();
    }
}
