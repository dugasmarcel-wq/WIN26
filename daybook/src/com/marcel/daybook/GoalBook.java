package com.marcel.daybook;

import org.json.*;
import java.time.LocalDate;
import java.util.*;

/** Effective-dated goal revisions keep old journal pages and check-ins stable. */
final class GoalBook {
    static final class Goal {
        final JSONObject original;
        final String id;
        final boolean weekly;
        final TreeMap<String,JSONObject> edits=new TreeMap<>();
        final Set<String> taps=new HashSet<>();
        final Set<String> marks=new HashSet<>();
        Goal(JSONObject g)throws JSONException {original=g;id=g.getString("id");weekly="Weekly".equals(g.getString("period"));}
        JSONObject at(String day){Map.Entry<String,JSONObject> e=edits.floorEntry(day);return e==null?original:e.getValue();}
        boolean active(LocalDate d){String key=d.toString();JSONObject g=at(key);return key.compareTo(g.optString("start"))>=0&&key.compareTo(g.optString("end"))<0;}
        String key(LocalDate d){String k=weekly?Journal.monday(d).toString():d.toString();String first=original.optString("start");return k.compareTo(first)<0?first:k;}
        boolean due(LocalDate d){
            if(!active(d))return false;
            if(!weekly)return true;
            return d.getDayOfWeek().getValue()==7 || d.plusDays(1).toString().equals(at(d.toString()).optString("end"));
        }
        LocalDate first(){return LocalDate.parse(original.optString("start"));}
    }
    final LinkedHashMap<String,Goal> goals=new LinkedHashMap<>();
    GoalBook(Journal db)throws JSONException{
        JSONArray list=db.rows("SELECT * FROM goals ORDER BY start DESC,rowid");
        for(int i=0;i<list.length();i++){Goal g=new Goal(list.getJSONObject(i));goals.put(g.id,g);}
        for(JSONObject edit:items(db.rows("SELECT * FROM goal_edits ORDER BY since")))goals.get(edit.getString("goal")).edits.put(edit.getString("since"),edit);
        for(JSONObject tap:items(db.rows("SELECT goal,day FROM weekly_taps")))goals.get(tap.getString("goal")).taps.add(tap.getString("day"));
        for(JSONObject mark:items(db.rows("SELECT goal,day FROM marks")))goals.get(mark.getString("goal")).marks.add(mark.getString("day"));
    }
    static List<JSONObject> items(JSONArray a)throws JSONException{List<JSONObject> result=new ArrayList<>();for(int i=0;i<a.length();i++)result.add(a.getJSONObject(i));return result;}
    JSONArray snapshot(LocalDate now)throws JSONException{
        JSONArray out=new JSONArray();
        for(Goal g:goals.values()){
            JSONObject v=new JSONObject(g.original.toString()),plan=g.at(now.toString());
            for(String field:new String[]{"start","end","name"})v.put(field,plan.getString(field));
            v.put("created",g.original.getString("start"));
            int prior=0,missed=0;String key=g.key(now);
            boolean active=g.active(now),done=active&&g.marks.contains(key);
            for(LocalDate d=g.first();d.isBefore(now)&&d.isBefore(LocalDate.now().plusDays(1));d=d.plusDays(1)){
                if(!g.due(d))continue;
                if(g.marks.contains(g.key(d)))prior++;else missed++;
            }
            v.put("tapped",g.taps.contains(now.toString())).put("active",active).put("done",done?1:0).put("due",g.due(now)).put("prior",prior).put("missed",missed);
            out.put(v);
        }return out;
    }
    JSONObject month(LocalDate first)throws JSONException{
        JSONObject days=new JSONObject();
        LocalDate end=first.plusMonths(1);
        for(LocalDate d=first;d.isBefore(end);d=d.plusDays(1)){
            int due=0,done=0,taps=0;
            for(Goal goal:goals.values())if(goal.due(d)){due++;if(goal.marks.contains(goal.key(d)))done++;}
            for(Goal goal:goals.values())if(goal.taps.contains(d.toString()))taps++;
            if(due>0||taps>0)days.put(d.toString(),new JSONObject().put("due",due).put("done",done).put("taps",taps));
        }
        return days;
    }
    JSONArray history(String id,LocalDate now)throws JSONException{
        JSONArray out=new JSONArray();Goal g=goals.get(id);if(g==null)return out;
        for(LocalDate d=now;!d.isBefore(g.first());d=d.minusDays(1)){
            if(g.due(d)||(d.equals(now)&&g.active(d)))out.put(new JSONObject().put("day",d.toString()).put("done",g.marks.contains(g.key(d))));
        }return out;
    }
}
