"""Connect exact 1.13 frontend to the enhanced native bridge."""
from pathlib import Path
import sys
assets=Path(sys.argv[1])
def change(file,old,new):
    p=assets/file;s=p.read_text()
    if s.count(old)!=1:raise ValueError(f"Unexpected {file}: {old[:100]}")
    p.write_text(s.replace(old,new,1))
change("app.js",'''data-seconds="'+g.seconds+'"''','''data-seconds="'+g.seconds+'" data-target="'+g.target+'"''')
change("app.js","' / 60m'+(g.seconds>3600?' · +'+timeLabel(g.seconds-3600):'')","' / '+timeLabel(g.target)+(g.seconds>g.target?' · +'+timeLabel(g.seconds-g.target):'')")
change("app.js","(state.timer?.goal===g.id?' · Running':'')","(state.timer?.goal===g.id?(state.timer.paused?' · Paused':' · Running'):'')")
change("app.js","setting('App updates','Daybook '+esc(state.version),'Check now','updates')+","")
change("app.js","['updates','check'],","")
change("method.js","Math.min(100,Number(el.dataset.seconds)/36)","(Number(el.dataset.target)>0?Math.min(100,100*Number(el.dataset.seconds)/Number(el.dataset.target)):0)")
change("method.js","done=rows.filter(g=>g.seconds>=3600).length","target=rows.reduce((n,g)=>n+g.target,0),applicable=rows.filter(g=>g.target>0),done=applicable.filter(g=>g.seconds>=g.target).length")
change("method.js","timeLabel(seconds)+' / 4h · '+done+'/4'","timeLabel(seconds)+' / '+timeLabel(target)+' · '+done+'/'+applicable.length")
change("method.js","const day=state.today,running=state.timer||{},own=running.goal===id;","const day=state.today,running=api('state').timer||{},own=running.goal===id,observed=Date.now();")
change("method.js","' / 60 min'+(g.seconds>3600?' · '+esc(timeLabel(g.seconds-3600))+' extra':'')","' / '+esc(timeLabel(g.target))+(g.seconds>g.target?' · '+esc(timeLabel(g.seconds-g.target))+' extra':'')")
change("method.js","(own?'Stop and save':'Start')","(own?(running.paused?'Resume':'Pause'):'Start')")
change("method.js","(own?'<button type=\"button\" id=\"discardTimer\">Discard</button>':'')","(own?'<button type=\"button\" id=\"finishTimer\">Finish</button><button type=\"button\" id=\"discardTimer\">Discard</button>':'')")
change("method.js","if(own)api('methodStop',{id});else api('methodStart',{id,day});dialog.close();render();","if(own)api(running.paused?'methodResume':'methodPause',{id});else api('methodStart',{id,day});dialog.close();render();timeEntry(id);")
change("method.js","if(own){\n   $('#discardTimer')","if(own){\n   $('#finishTimer').onclick=()=>{api('methodStop',{id});dialog.close();render();};\n   $('#discardTimer')")
change("method.js","Math.floor((Date.now()-running.started)/1000)","Math.floor((running.elapsedMillis+(running.paused?0:Date.now()-observed))/1000)")
change("modules.js","const current=moduleDay===state.today,seconds=current?g.seconds:(api('dayGoals',{day:moduleDay}).find(x=>x.id===g.id)?.seconds||0);","const current=moduleDay===state.today,record=current?g:api('dayGoals',{day:moduleDay}).find(x=>x.id===g.id),seconds=record?.seconds||0,target=record?.target||0;")
change("modules.js","' / 60m</b><progress max=\"3600\" value=\"'+Math.min(seconds,3600)","' / '+esc(timeLabel(target))+'</b><progress max=\"'+Math.max(1,target)+'\" value=\"'+Math.min(seconds,target)")
change("journal-ui.js","r.timed?Math.min(1,(r.seconds||0)/3600)","r.timed?(r.target>0?Math.min(1,(r.seconds||0)/r.target):null)")
change("journal-ui.js","g?.timed?Math.min(1,(g.seconds||0)/3600)","g?.timed?(g.target>0?Math.min(1,(g.seconds||0)/g.target):null)")
change("journal-ui.js","timeLabel(g.seconds):v===null","timeLabel(g.seconds)+' / '+timeLabel(g.target):v===null")
change("journal-ui.js","timeLabel(total)+' / 4h'","timeLabel(total)+' / '+timeLabel(method.reduce((sum,g)=>sum+(g?.target||0),0))")
change("journal-ui.js","v===null?'Not tracked'","v===null?'Not applicable'")
change("journal-ui.js","if(!r)return d>=state.trackingSince?0:null;","if(!r)return null;")
change("calendar.js","esc(timeLabel(n.areas[id]))+'</b>","esc(timeLabel(n.areas[id]))+' / '+esc(timeLabel(n.areaTargets[id]))+'</b>")
change("enhanced.js","esc(timeLabel(stats.areas[id]))+'</b>","esc(timeLabel(stats.areas[id]))+' / '+esc(timeLabel(stats.plannedTargets[id]))+'</b>")
change("enhanced.js","Applicable target to date: '+esc(timeLabel(stats.target))","Weekly target: '+esc(timeLabel(stats.plannedTarget))+' · applicable to date: '+esc(timeLabel(stats.target))")
# Extend the original settings only; Today remains unchanged.
p=assets/"enhanced.js"
p.write_text(p.read_text()+r'''
(function(){
 const originalSettings=settings;
 function editTargets(kind){
   const initial=state.today;
   modal(kind==='schedule'?'Weekday and weekend targets':'Individual date targets',
    '<label for="targetDay">'+(kind==='schedule'?'Effective from':'Date')+'</label><input id="targetDay" type="date" min="'+initial+'" required value="'+initial+'"><div id="targetValues"></div><p class="meta">Past dates are preserved. Zero minutes means no requirement.</p>',
    'Save targets',()=>{
      const values={};
      for(const id of areaIds)values[id]=kind==='schedule'?{weekday:Number($('#targetW-'+id).value),weekend:Number($('#targetE-'+id).value)}:{minutes:Number($('#targetD-'+id).value),reset:$('#targetReset-'+id).checked};
      api('targetEdit',{kind,day:$('#targetDay').value,values});render();return true;
    });
   function paint(){
     const date=$('#targetDay').value;if(!date||date<initial)return;
     const config=api('targetConfig',{day:date});
     $('#targetValues').innerHTML=areaIds.map(id=>{const c=config[id];return '<fieldset class="target-field" data-color-key="'+id+'"><legend>'+esc(focusNames[id])+'</legend>'+
       (kind==='schedule'?'<label>Weekdays (minutes)<input type="number" required min="0" max="1440" id="targetW-'+id+'" value="'+c.weekday+'"></label><label>Weekends (minutes)<input type="number" required min="0" max="1440" id="targetE-'+id+'" value="'+c.weekend+'"></label>':
       '<label>Minutes<input type="number" required min="0" max="1440" id="targetD-'+id+'" value="'+c.minutes+'"></label><label><input type="checkbox" id="targetReset-'+id+'" '+(!c.overridden?'':'')+'> Use regular target</label>')+'</fieldset>';}).join('');
   }
   $('#targetDay').onchange=paint;paint();
 }
 settings=function(){
   originalSettings();
   const status=api('autoBackupStatus'),where=status.external?'External folder selected':'Private app storage only';
   const desc=where+(status.lastLocal?' · Last saved '+stamp(status.lastLocal):' · No backup yet')+(status.error?' · '+status.error:'');
   screen.querySelector('.settings-list').insertAdjacentHTML('beforeend',
     setting('Method of 4 targets','Change weekday and weekend requirements from an effective date.','Edit','editTargets')+
     setting('Individual date targets','Adjust one date without affecting the regular schedule.','Edit date','dateTargets')+
     setting('Automatic backups',esc(desc),'Backup now','autoNow')+
     setting('External backup folder','Choose a folder outside this app so backups can survive uninstall.','Choose','autoFolder')+
     setting('Latest automatic backup','Restore the most recent private snapshot.','Restore','autoRestore'));
   $('#editTargets').onclick=()=>editTargets('schedule');$('#dateTargets').onclick=()=>editTargets('date');
   $('#autoNow').onclick=()=>{api('autoBackupNow');msg('Backup requested. Check Settings for the last successful backup.');};
   $('#autoFolder').onclick=()=>api('autoBackupFolder');$('#autoRestore').onclick=()=>api('autoBackupRestore');
 };
})();
''')
