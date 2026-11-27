package com.marcel.daybook;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import org.json.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class StandaloneSmoke extends Instrumentation {
    Main activity;
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    static void check(boolean b,String name){if(!b)throw new AssertionError(name);}
    String js(String code)throws Exception{
        AtomicReference<String> value=new AtomicReference<>();CountDownLatch latch=new CountDownLatch(1);
        runOnMainSync(()->activity.web.evaluateJavascript(code,r->{value.set(r);latch.countDown();}));
        check(latch.await(8,TimeUnit.SECONDS),"WebView callback");return value.get();
    }
    void yes(String code,String title)throws Exception{check("true".equals(js("(()=>{"+code+"})()")),title);}
    void click(String sel)throws Exception{yes("let b=document.querySelector("+JSONObject.quote(sel)+");if(!b)return false;b.click();return true;","Tap "+sel);}
    void waitReady()throws Exception{for(int i=0;i<120;i++){if("true".equals(js("!!document.querySelector('.pv-ring')")))return;Thread.sleep(100);}throw new AssertionError("Standalone Today page did not load: "+js("document.body.innerText"));}
    JSONObject goal(Journal j,String id)throws Exception{return new GoalBook(j).goals.containsKey(id)?find(j.goals(LocalDate.now()),id):null;}
    JSONObject find(JSONArray a,String id)throws Exception{for(JSONObject x:GoalBook.items(a))if(id.equals(x.optString("id")))return x;return null;}
    @Override public void onStart(){
        Bundle results=new Bundle();
        try{
            Intent intent=new Intent(getTargetContext(),Main.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=(Main)startActivitySync(intent);waitReady();Journal db=Journal.get(activity);String day=LocalDate.now().toString();
            check(activity.getPackageName().equals("com.marcel.daybookstandalone"),"isolated package");
            check(activity.checkSelfPermission(android.Manifest.permission.INTERNET)!=android.content.pm.PackageManager.PERMISSION_GRANTED,"no internet permission");
            yes("return document.querySelectorAll('.pv-ring').length===4&&!!document.querySelector('#dailyBody')","four rings and journal");
            yes("let p=document.querySelector('#dailyBody');p.value='Standalone journal test';p.dispatchEvent(new Event('input'));return true;","write front journal");
            check(db.note("day-"+day).getString("body").equals("Standalone journal test"),"front journal stored");
            click(".pv-ring");yes("return !!document.querySelector('#pvDial')&&!!document.querySelector('#pvHour')","dial opens");
            yes("let d=document.querySelector('#pvDial');d.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowUp'}));return Number(document.querySelector('#pvMinutes').value)===31","dial adjustment");
            click("#pvHour");check(goal(db,"four-spiritual").getInt("seconds")==3600,"one tap hour");
            click(".pv-ring");yes("let n=document.querySelector('#pvMinutes');n.value=90;n.dispatchEvent(new Event('change'));return n.value==='90'","custom duration");
            click("#confirm");check(goal(db,"four-spiritual").getInt("seconds")==9000,"overtime retained");
            click(".pv-ring");click("#pvTimer");
            check(Investment.timer(db).getString("goal").equals("four-spiritual"),"persistent start");
            db.getWritableDatabase().execSQL("UPDATE method_timer SET started=? WHERE slot=1",new Object[]{System.currentTimeMillis()-90000});
            click("#pvTimer");check(Investment.timer(db).getBoolean("paused"),"pause persisted");
            long elapsed=Investment.timer(db).getLong("elapsed");check(elapsed>=90000,"paused elapsed time");
            click("#pvTimer");check(!Investment.timer(db).getBoolean("paused"),"resume persisted");
            click("#pvFinish");check(!Investment.timer(db).has("goal")&&goal(db,"four-spiritual").getInt("seconds")>=9090,"save session");
            check(Investment.stats(db,day,LocalDate.now().plusDays(1).toString()).getLong("balanced")==3600,"balanced cap");
            click("[data-new=daily]");yes("document.querySelector('#gname').value='Walk outdoors';return true","enter habit");click("#confirm");
            String habit=db.rows("SELECT id FROM goals WHERE name='Walk outdoors'").getJSONObject(0).getString("id");
            click("[data-mark='"+habit+"']");check(goal(db,habit).getInt("done")==1,"one tap completion");
            click("[data-mark='"+habit+"']");check(goal(db,habit).getInt("done")==0,"one tap undo");
            int bit=1<<(LocalDate.now().getDayOfWeek().getValue()-1);
            Extras.schedule(db,new JSONObject().put("id",habit).put("mask",bit).put("part","Morning"));
            GoalBook book=new GoalBook(db);check(book.goals.get(habit).due(LocalDate.now())&&!book.goals.get(habit).due(LocalDate.now().plusDays(1)),"weekday applicability");
            click("[data-new=weekly]");yes("document.querySelector('#gname').value='Bank appointment';document.querySelector('#pvScheduled').value=state.today;document.querySelector('#pvRepeat').value='weekly';return true","schedule recurring responsibility");click("#confirm");
            String task=db.rows("SELECT id FROM goals WHERE name='Bank appointment'").getJSONObject(0).getString("id");
            yes("return !!document.querySelector('.pv-priority')&&document.querySelector('.pv-priority').innerText.includes('Bank appointment')","automated priority");
            db.getWritableDatabase().execSQL("UPDATE repeat_rules SET next_day=?",new Object[]{day});Extras.generate(db);
            check(db.rows("SELECT id FROM goals WHERE name='Bank appointment'").length()==2,"recurring responsibility generated");
            db.mark(task,day,true);db.removeGoal(task);check(new GoalBook(db).goals.get(task).achieved(LocalDate.now()),"archived task retains actual completion");
            yes("go('today');return true","refresh");click("#pvReview");yes("return document.querySelector('#fields').innerText.includes('Balanced investment')","weekly review");click("#cancel");
            click("#notebook");yes("return !!document.querySelector('#quickBody')&&!!document.querySelector('#pvSearchButton')","quick notes and search");
            click("#journalTab");yes("return !!document.querySelector('.personal-journal textarea')&&document.querySelectorAll('.journal-card').length===4","combined journal");
            check(js("document.querySelector('.personal-journal textarea').value").contains("Standalone journal test"),"same front-page journal");
            click("#today");click(".pv-area-name");yes("return !!document.querySelector('.module-page')&&!!document.querySelector('.module-time')","category page retained");
            click("#calendar");yes("return !!document.querySelector('.account-grid')&&!!document.querySelector('#addReminder')","accountability calendar");
            click("#addReminder");yes("document.querySelector('#remTitle').value='Recurring test';document.querySelector('#remDate').value=moduleOffset(state.today,1);document.querySelector('#remTime').value='09:00';document.querySelector('#remRepeat').value='weekly';document.querySelector('#remNotify').checked=true;return true","recurring reminder input");click("#confirm");
            String reminder=db.rows("SELECT id FROM notes WHERE title='Recurring test'").getJSONObject(0).getString("id");
            JSONObject note=db.note(reminder);check(note.getLong("alarm")>System.currentTimeMillis(),"notification scheduled");
            long next=Extras.nextAlarm(db,reminder,note.getLong("alarm"));check(next>note.getLong("alarm"),"recurring notification calculates next date");
            click("#openStats");yes("return !!document.querySelector('[data-stats-kind=day]')","daily statistics");
            click("#settings");yes("return !!document.querySelector('.pv-backup')&&!document.querySelector('#updates')","automatic backup settings and no update button");
            JSONObject saved=db.backup();check(saved.getJSONArray("habit_schedule").length()>0&&saved.getJSONArray("repeat_rules").length()>0,"new data included in backup");
            db.restore(saved);check(db.note("day-"+day).getString("body").equals("Standalone journal test")&&db.rows("SELECT * FROM repeat_rules").length()==1,"backup round trip");
            results.putString("stream","\nDAYBOOK_STANDALONE_TESTS_PASSED\n");finish(Activity.RESULT_OK,results);
        }catch(Throwable e){results.putString("stream","\n"+android.util.Log.getStackTraceString(e)+"\n");finish(Activity.RESULT_CANCELED,results);}
    }
}
