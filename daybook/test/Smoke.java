package com.marcel.daybook;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import org.json.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

public final class Smoke extends Instrumentation {
    private Main activity;
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
    static void until(BooleanSupplier test,String name)throws Exception{for(int i=0;i<100;i++){if(test.getAsBoolean())return;Thread.sleep(100);}throw new AssertionError(name);}
    String js(String code){
        AtomicReference<String> result=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);
        runOnMainSync(()->activity.web.evaluateJavascript(code,r->{result.set(r);done.countDown();}));
        try{check(done.await(6,TimeUnit.SECONDS),"JS callback");}catch(Exception e){throw new RuntimeException(e);}return result.get();
    }
    void tap(String selector){check("true".equals(js("(()=>{let b=document.querySelector("+JSONObject.quote(selector)+");if(!b)return false;b.click();return true;})()")),"tap "+selector);}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            Context c=getTargetContext();Journal db=Journal.get(c);
            activity=(Main)startActivitySync(new Intent(c,Main.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            until(()->"true".equals(js("!!document.querySelector('#addGoal')")),"today rendered");
            tap("#addGoal");js("document.querySelector('#gname').value='Read 20 minutes'");tap("#confirm");
            until(()->js("document.body.innerText").contains("Read 20 minutes"),"goal displayed");
            tap("[data-mark]");check(db.goals(LocalDate.now()).getJSONObject(0).getInt("done")==1,"goal bubble persisted");
            tap("[data-mark]");check(db.goals(LocalDate.now()).getJSONObject(0).getInt("done")==0,"undo today");
            String d=LocalDate.now().toString(),id=db.goals(LocalDate.now()).getJSONObject(0).getString("id");
            LocalDate past=LocalDate.now().minusDays(2);db.getWritableDatabase().execSQL("UPDATE goals SET start=?,end=? WHERE id=?",new Object[]{past.toString(),past.plusWeeks(1).toString(),id});
            check(!db.mark(id,past.toString(),true),"no backfill");db.mark(id,d,true);db.mark(id,d,true);
            JSONObject g=db.goals(LocalDate.now()).getJSONObject(0);check(g.getInt("missed")==2&&g.getInt("done")==1,"permanent missed count");
            check(Journal.end(LocalDate.of(2024,2,29),"Year").equals(LocalDate.of(2025,2,28)),"leap horizon");
            js("window.nativeEvent('route','new')");js("document.querySelector('#ntitle').value='Test note';document.querySelector('#ntitle').dispatchEvent(new Event('input'));document.querySelector('#body').value='Remember this';document.querySelector('#body').dispatchEvent(new Event('input'))");
            JSONObject n=db.rows("SELECT * FROM notes").getJSONObject(0);String nid=n.getString("id");
            check(n.getString("body").equals("Remember this")&&n.getString("folder").equals("daily"),"autosave daily note");
            js("document.querySelector('#nfolder').value='long';document.querySelector('#nfolder').dispatchEvent(new Event('change'))");
            check(db.note(nid).getString("folder").equals("long"),"move to Long Term");
            long at=System.currentTimeMillis()+2000;
            db.setAlarm(nid,at);Reminders.plan(c,nid,at);
            until(()->c.getSystemService(NotificationManager.class).getActiveNotifications().length>0,"actual reminder notification");
            android.service.notification.StatusBarNotification notice=c.getSystemService(NotificationManager.class).getActiveNotifications()[0];
            notice.getNotification().actions[0].actionIntent.send();
            until(()->{try{return db.note(nid).getLong("alarm")>System.currentTimeMillis();}catch(Exception e){return false;}},"snooze");
            notice.getNotification().contentIntent.send();until(()->js("document.querySelector('#ntitle')?.value").contains("Test note"),"notification opens note");
            JSONObject backup=db.backup();db.restore(backup);check(db.goals(LocalDate.now()).getJSONObject(0).getInt("missed")==2&&db.note(nid)!=null,"backup round trip");
            JSONObject invalid=new JSONObject(backup.toString());invalid.getJSONArray("notes").getJSONObject(0).put("folder","missing");
            try{db.restore(invalid);throw new AssertionError("invalid restore accepted");}catch(android.database.sqlite.SQLiteConstraintException expected){}
            check(db.note(nid)!=null,"restore rolls back");
            final AppWidgetHost host=new AppWidgetHost(c,29870);AppWidgetManager m=AppWidgetManager.getInstance(c);int wid=host.allocateAppWidgetId();
            ComponentName component=new ComponentName(c,Widget.class);
            check(m.bindAppWidgetIdIfAllowed(wid,component),"bind Android widget");
            final AppWidgetHostView[] views=new AppWidgetHostView[1];
            runOnMainSync(()->{host.startListening();views[0]=host.createView(activity,wid,m.getAppWidgetInfo(wid));activity.addContentView(views[0],new ViewGroup.LayoutParams(620,480));Widget.refresh(c);});
            until(()->{AtomicBoolean found=new AtomicBoolean();runOnMainSync(()->found.set(views[0].findViewById(R.id.row)!=null));return found.get();},"widget row inflated");
            runOnMainSync(()->views[0].findViewById(R.id.row).performClick());
            until(()->{try{return db.goals(LocalDate.now()).getJSONObject(0).getInt("done")==0;}catch(Exception e){return false;}},"interactive widget bubble");
            runOnMainSync(()->views[0].findViewById(R.id.openNote).performClick());
            until(()->"\"\"".equals(js("document.querySelector('#ntitle')?.value")),"widget opens blank Daily note");
            runOnMainSync(()->{host.stopListening();host.deleteHost();});
            result.putString("stream","DAYBOOK_TESTS_PASSED");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
