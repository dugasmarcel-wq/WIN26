package com.marcel.daybook;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import org.json.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class EnhancedSmoke extends Instrumentation {
    Main activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);}
    String js(String code)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();
        runOnMainSync(()->activity.web.evaluateJavascript(code,value->{result.set(value);latch.countDown();}));
        check(latch.await(10,TimeUnit.SECONDS),"WebView callback");return result.get();
    }
    void expect(String code,String name)throws Exception{check("true".equals(js(code)),name);}
    void waitFor(String code,String name)throws Exception{
        for(int i=0;i<100;i++){if("true".equals(js(code)))return;Thread.sleep(100);}
        throw new AssertionError(name);
    }
    void launch()throws Exception{
        Intent i=new Intent(getTargetContext(),Main.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(Main)startActivitySync(i);waitFor("typeof state!=='undefined' && !!state && !!document.querySelector('.four-grid')","Today renders");
    }
    static JSONObject goal(Journal db,String id,LocalDate date)throws Exception{
        for(JSONObject row:GoalBook.items(db.goals(date)))if(id.equals(row.getString("id")))return row;
        throw new AssertionError("Category missing");
    }
    JSONObject values(int weekday,int weekend)throws Exception{
        JSONObject result=new JSONObject();
        for(String id:Investment.AREAS)result.put(id,new JSONObject().put("weekday",weekday).put("weekend",weekend));
        return result;
    }
    @Override public void onStart(){
        Bundle out=new Bundle();
        try{
            Context c=getTargetContext();Journal db=Journal.get(c);
            LocalDate today=LocalDate.now(),yesterday=today.minusDays(1),future=today.plusDays(14);
            String id="four-spiritual";
            check(new Targets(db).seconds(id,today)==(today.getDayOfWeek().getValue()>=6?1200:3600),"default weekday/weekend");
            check(new Targets(db).seconds(id,yesterday)==3600,"old targets unchanged");
            Targets.edit(db,new JSONObject().put("kind","schedule").put("day",future.toString()).put("values",values(2700/60,600/60)));
            check(new Targets(db).seconds(id,yesterday)==3600,"future revision does not alter past");
            check(new Targets(db).seconds(id,future)==(future.getDayOfWeek().getValue()>=6?600:2700),"future requirement effective");
            JSONObject override=new JSONObject();
            for(String area:Investment.AREAS)override.put(area,new JSONObject().put("minutes",area.equals(id)?10:20));
            Targets.edit(db,new JSONObject().put("kind","date").put("day",today.toString()).put("values",override));
            check(goal(db,id,today).getInt("target")==600,"date override applied");
            Investment.log(db,new JSONObject().put("id",id).put("day",today.toString()).put("minutes",60));
            JSONObject stats=Investment.stats(db,today.toString(),today.plusDays(1).toString());
            check(stats.getLong("total")==3600&&stats.getLong("extra")==3000&&stats.getLong("balanced")==600&&stats.getLong("target")==4200,"actual, extra, balanced and date-specific total");
            // Keep an active timer through Activity destruction, then pause and resume natively.
            Investment.start(db,id,today.toString());
            db.getWritableDatabase().execSQL("UPDATE method_timer SET started=?",new Object[]{System.currentTimeMillis()-90000});
            launch();
            waitFor("state.timer.goal==='four-spiritual'","persisted active timer in UI");
            runOnMainSync(()->activity.finish());Thread.sleep(300);launch();
            check(Investment.timer(db).getString("goal").equals(id),"timer persists Activity closure");
            Investment.pause(db,id);long paused=Investment.timer(db).getLong("elapsedMillis");
            Thread.sleep(150);check(Investment.timer(db).getLong("elapsedMillis")==paused,"pause excludes inactive time");
            JSONObject during=db.backup();db.restore(during);
            check(Investment.timer(db).getBoolean("paused"),"backup restores stopwatch paused");
            Investment.resume(db,id);Thread.sleep(120);check(Investment.timer(db).getLong("elapsedMillis")>=paused,"resume retains segments");
            Investment.stop(db,id,false);
            check(goal(db,id,today).getInt("seconds")>=3690,"finish saves elapsed once");
            check(!Investment.timer(db).has("goal"),"finished stopwatch cleared");
            db.saveNote("day-"+today,"daily","Personal journal","Unique search phrase from original record");
            db.saveNote("search-fixture","daily","Quick note","Another unique search phrase");
            js("window.nativeEvent('route','today')");
            expect("!!document.querySelector('.four-grid')&&!!document.querySelector('#dailyBody')","original Today and journal");
            js("showModule('four-spiritual');document.querySelector('#moduleLog').click()");
            expect("!!document.querySelector('.duration-dial')&&document.querySelector('[data-add-minutes]').textContent==='+1 Hour'","clock and immediate hour controls");
            js("document.querySelector('#manualMinutes').value=95;document.querySelector('#manualMinutes').dispatchEvent(new Event('input'))");
            expect("document.querySelector('.duration-dial').getAttribute('aria-valuenow')==='95'","duration beyond one hour");
            js("document.querySelector('[data-add-minutes]').click()");
            check(goal(db,id,today).getInt("seconds")>=7290,"one-hour button adds 60 minutes");
            js("timeEntry('four-spiritual');document.querySelector('#timerAction').click()");
            expect("!!document.querySelector('#finishTimer')&&document.querySelector('#timerAction').textContent==='Pause'","start and pause controls");
            js("document.querySelector('#timerAction').click()");
            expect("document.querySelector('#timerAction').textContent==='Resume'","resume control");
            js("document.querySelector('#timerAction').click();document.querySelector('#finishTimer').click()");
            expect("!state.timer.goal","finish clears timer");
            js("openJournal(state.today);document.querySelector('#findJournal').click();document.querySelector('#findQuery').value='unique search phrase';document.querySelector('#findQuery').dispatchEvent(new Event('input'))");
            expect("document.querySelectorAll('.find-hit').length===2","keyword searches journal and quick notes");
            js("document.querySelector('.find-hit').click()");
            expect("view==='edit'&&document.querySelector('#body').value.toLowerCase().includes('unique search phrase')","open original matching record");
            js("openJournal(state.today);document.querySelector('#findJournal').click();document.querySelector('#findCalendar').click()");
            expect("!!document.querySelector('.has-journal[data-find-date=\"'+state.today+'\"]')","calendar marks written day");
            js("document.querySelector('.has-journal[data-find-date=\"'+state.today+'\"]').click()");
            expect("view==='journal'&&document.querySelector('.personal-journal textarea').value.includes('Unique search phrase')","calendar retrieves original journal");
            js("go('statistics');document.querySelector('#weeklyReview').click()");
            expect("!!document.querySelector('#weeklyReflection')&&document.querySelector('#reviewBody').textContent.includes('Weekly target')","weekly review generated");
            js("document.querySelector('#weeklyReflection').value='Review fixture';document.querySelector('#weeklyReflection').dispatchEvent(new Event('input'));document.querySelector('#reviewPrev').click();document.querySelector('#reviewNext').click()");
            expect("document.querySelector('#weeklyReflection').value==='Review fixture'","weekly reflection preserved");
            js("dialog.close();showModule('four-spiritual',state.today,'bible')");
            expect("getPlan('four-spiritual').books.find(b=>b.id==='bible').pace===2","Bible default two chapters");
            int before=goal(db,id,today).getInt("seconds");js("document.querySelector('#readComplete').click()");
            expect("readingProgress(getPlan('four-spiritual').books.find(b=>b.id==='bible'))===2","Bible actual reading recorded");
            check(goal(db,id,today).getInt("seconds")==before,"reading independent of spiritual time");
            BackupStore.write(c,false);
            JSONObject saved=BackupStore.latest(c);
            check(saved.getJSONArray("method_target_schedule").length()>=4&&saved.getJSONArray("method_time").length()>0,"automatic backup contains targets and investment");
            db.restore(saved);check(db.note("search-fixture")!=null&&goal(db,id,today).getInt("seconds")==before,"automatic backup round trip");
            JSONObject old=new JSONObject(saved.toString());old.remove("method_target_schedule");old.remove("method_target_override");old.remove("pending_timer");
            db.restore(old);check(db.note("search-fixture")!=null&&goal(db,id,today).getInt("seconds")==before,"legacy backup imports without loss");
            db.restore(saved);check(goal(db,id,today).getInt("target")==600,"enhanced backup restores specific date target");
            out.putString("stream","DAYBOOK_TESTS_PASSED");finish(Activity.RESULT_OK,out);
        }catch(Throwable e){out.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}
    }
}
