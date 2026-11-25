package com.marcel.daybook;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One scheduled check-in per daily goal/day, or per weekly goal/Sunday. */
final class Tracker {
    static final class Counts {
        int done, missed, open;
        void add(Counts n) { done+=n.done; missed+=n.missed; open+=n.open; }
        int percent() { return done+missed==0 ? -1 : (int)Math.round(100.0*done/(done+missed)); }
        String status() {
            if(open>0) return "open";
            if(missed>0) return done>0 ? "partial" : "missed";
            return done>0 ? "done" : "none";
        }
        JSONObject json() throws JSONException {
            return new JSONObject().put("done",done).put("missed",missed).put("open",open)
                .put("percent",percent()).put("status",status());
        }
    }
    private final LocalDate start;
    private final Counts[] days;
    Tracker(Journal journal,LocalDate start,LocalDate end,LocalDate today) {
        this.start=start;
        long length=end.toEpochDay()-start.toEpochDay();
        if(length<1 || length>366) throw new IllegalArgumentException("Choose one month or year.");
        days=new Counts[(int)length];
        for(int i=0;i<days.length;i++)days[i]=new Counts();
        LocalDate cutoff=end.isBefore(today.plusDays(1))?end:today.plusDays(1);
        if(!start.isBefore(cutoff))return;
        SQLiteDatabase db=journal.getReadableDatabase();
        HashMap<String,HashSet<String>> marks=new HashMap<>();
        try(Cursor c=db.rawQuery("SELECT goal,day FROM marks WHERE day>=? AND day<?",
                new String[]{start.minusDays(6).toString(),cutoff.toString()})) {
            while(c.moveToNext())marks.computeIfAbsent(c.getString(0),k->new HashSet<>()).add(c.getString(1));
        }
        try(Cursor c=db.rawQuery("SELECT id,start,end,period FROM goals WHERE start<? AND end>?",
                new String[]{cutoff.toString(),start.toString()})) {
            while(c.moveToNext()) {
                LocalDate created=LocalDate.parse(c.getString(1)), expires=LocalDate.parse(c.getString(2));
                LocalDate d=start.isAfter(created)?start:created;
                LocalDate limit=cutoff.isBefore(expires)?cutoff:expires;
                boolean weekly="Weekly".equals(c.getString(3));
                if(weekly)d=Journal.monday(d).plusDays(6);
                HashSet<String> checked=marks.get(c.getString(0));
                for(;d.isBefore(limit);d=d.plusDays(weekly?7:1)) {
                    LocalDate key=weekly?Journal.monday(d):d;
                    if(key.isBefore(created))key=created;
                    Counts n=on(d);
                    if(checked!=null && checked.contains(key.toString()))n.done++;
                    else if(d.equals(today))n.open++;
                    else n.missed++;
                }
            }
        }
    }
    Counts on(LocalDate date) {return days[(int)(date.toEpochDay()-start.toEpochDay())];}
    Counts total(LocalDate from,LocalDate until) {
        Counts result=new Counts();
        for(LocalDate d=from;d.isBefore(until);d=d.plusDays(1)) result.add(on(d));
        return result;
    }
    JSONObject json(boolean year) throws JSONException {
        LocalDate end=start.plusDays(days.length);
        Counts sum=total(start,end);
        JSONArray buckets=new JSONArray();
        for(LocalDate d=start;d.isBefore(end);d=year?d.plusMonths(1):d.plusDays(1)) {
            Counts n=year?total(d,d.plusMonths(1)):on(d);
            buckets.put(n.json().put("date",d.toString()));
        }
        return sum.json().put("buckets",buckets);
    }
}
