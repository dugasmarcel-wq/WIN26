'use strict';
const focusNames={'four-spiritual':'Spiritual','four-financial':'Financial','four-mental':'Mental','four-physical':'Physical'};
let calDay='',scale='month',returnCal=false;
const dayOfTime=t=>{const d=new Date(t);return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');};
const occasions=d=>state.events.filter(e=>e.day===d||(e.annual&&d>=e.day&&d.slice(5)===e.day.slice(5)));
function daySummary(d,compact=false){
 const goals=api('dayGoals',{day:d}).filter(g=>g.due);
 goals.sort((a,b)=>(areaIds.includes(a.id)?areaIds.indexOf(a.id):4)-(areaIds.includes(b.id)?areaIds.indexOf(b.id):4));
 const done=goals.filter(g=>g.done&&d<=state.today).length;
 const chips=goals.map(g=>{
   const status=d>state.today?'Scheduled':g.done?'Done':d<state.today?'Missed':'Pending';
   const label=focusNames[g.id]||g.name;
   return '<span class="result-chip" data-status="'+status+'" data-color-key="'+esc(focusNames[g.id]?g.id:groupOf(g))+'" aria-label="'+esc(label+', '+g.name+', '+status)+'" title="'+esc(g.name+' · '+status)+'"><i aria-hidden="true">'+(status==='Done'?'✓':status==='Missed'?'×':'')+'</i>'+esc(compact?label:(focusNames[g.id]?label+' · ':'')+g.name)+'</span>';
 }).join('');
 return '<section class="result-strip '+(compact?'compact':'')+'" aria-label="Check-ins for '+esc(d)+'"><b>Check-ins <span>'+done+'/'+goals.length+'</span></b><div class="result-chips">'+(chips||'<small>No goals due</small>')+'</div></section>';
}
function calendarPage(){
 if(!calDay)calDay=state.today;
 const y=Number(calDay.slice(0,4)),m=Number(calDay.slice(5,7))-1;
 const iso=(yy,mm,dd)=>dayAt(Date.UTC(yy,mm,dd));
 const heading=scale==='year'?String(y):pretty(calDay,scale==='day'?{dateStyle:'full'}:{month:'long',year:'numeric'});
 screen.innerHTML='<h1>Note Calendar</h1><div class="cal-controls"><button id="prevCal" aria-label="Previous">‹</button><button id="upCal">'+esc(heading)+'</button><button id="nextCal" aria-label="Next">›</button></div><div class="cal-modes">'+['year','month','day'].map(k=>'<button data-scale="'+k+'" aria-pressed="'+(k===scale)+'">'+k+'</button>').join('')+'<button id="calNow">Today</button></div><div id="calContent"></div>';
 const paint=()=>{calendarPage();applyColors();};
 function move(n){calDay=scale==='year'?iso(y+n,m,1):scale==='month'?iso(y,m+n,1):iso(y,m,Number(calDay.slice(8))+n);paint();}
 $('#prevCal').onclick=()=>move(-1);$('#nextCal').onclick=()=>move(1);
 $('#upCal').onclick=()=>{scale=scale==='day'?'month':'year';paint();};
 $('#calNow').onclick=()=>{calDay=state.today;scale='day';paint();};
 screen.querySelectorAll('[data-scale]').forEach(b=>b.onclick=()=>{scale=b.dataset.scale;paint();});
 const content=$('#calContent');
 if(scale==='year'){
   content.className='year-months';content.innerHTML=Array.from({length:12},(_,i)=>'<button data-month="'+iso(y,i,1)+'">'+esc(pretty(iso(y,i,1),{month:'long'}))+'</button>').join('');
   content.querySelectorAll('button').forEach(b=>b.onclick=()=>{calDay=b.dataset.month;scale='month';paint();});return;
 }
 if(scale==='month'){
   const scheduled=api('calendarGoals',{day:iso(y,m,1)});
   const marked=new Set(state.notes.flatMap(n=>n.alarm?[n.written,dayOfTime(n.alarm)]:[n.written]));
   content.className='month-days';content.innerHTML=['M','T','W','T','F','S','S'].map(t=>'<small>'+t+'</small>').join('')+'<i></i>'.repeat((new Date(utc(iso(y,m,1))).getUTCDay()+6)%7)+Array.from({length:new Date(Date.UTC(y,m+1,0)).getUTCDate()},(_,i)=>{let d=iso(y,m,i+1);return '<button data-date="'+d+'" class="'+(d===state.today?'cal-today':'')+'">'+(i+1)+(scheduled[d]?'<i class="calendar-dot '+(d>state.today?'future':scheduled[d].done===scheduled[d].due?'done':scheduled[d].done?'partial':d===state.today?'open':'missed')+'" aria-label="'+scheduled[d].done+' of '+scheduled[d].due+' goals complete"></i>':'')+(marked.has(d)||occasions(d).length?'<sup>•</sup>':'')+'</button>';}).join('');
   content.querySelectorAll('button').forEach(b=>b.onclick=()=>{calDay=b.dataset.date;scale='day';paint();});return;
 }
 const d=calDay;content.className='';
 const noteRows=state.notes.filter(n=>n.written===d),alarms=state.notes.filter(n=>n.alarm&&dayOfTime(n.alarm)===d);
 const noteLink=(n,label)=>'<button class="item card" data-open="'+esc(n.id)+'">'+esc(label)+'</button>';
 content.innerHTML='<h2>Journal</h2>'+(noteRows.map(n=>noteLink(n,n.title||'Untitled note')).join('')||'<p class="meta">No notes for this date.</p>')+daySummary(d)+reflectionList(d)+'<div class="section"><h2>Important dates</h2><button id="addDate">Add date</button></div>'+
 occasions(d).map(e=>'<div class="card setting"><span>'+esc(e.name)+(e.annual?' · Yearly':'')+'</span><button data-remove="'+esc(e.id)+'">Remove</button></div>').join('')+
 '<h2>Reminders</h2>'+(alarms.map(n=>noteLink(n,(n.title||'Untitled note')+' · '+stamp(n.alarm))).join('')||'<small>No scheduled reminders</small>');
 content.querySelectorAll('[data-open]').forEach(b=>b.onclick=()=>{returnCal=true;openNote(b.dataset.open);});
 $('#addDate').onclick=()=>modal('Important date','<label for="eventName">Name</label><input id="eventName" required maxlength="200"><label for="eventDay">Date</label><input id="eventDay" type="date" required value="'+d+'"><label><input type="checkbox" id="annual"> Repeat yearly</label>','Save',()=>{calDay=$('#eventDay').value;api('event',{name:$('#eventName').value,day:calDay,annual:$('#annual').checked});render();});
 content.querySelectorAll('[data-remove]').forEach(b=>b.onclick=()=>modal('Remove this date?','','Remove',()=>{api('removeEvent',{id:b.dataset.remove});render();}));
}
function noteMenu(id){
 const n=api('note',{id});if(!n)return;
 const pin=id===state.dailyId?'':'<fieldset class="pin-choices"><legend>Pin to notebook</legend>'+[2,3].map(k=>'<button type="button" data-action="'+(n.slot===k?0:k)+'">'+(n.slot===k?'Unpin slot ':'Slot ')+k+'</button>').join('')+'</fieldset>';
 modal(n.title||'Untitled note','<button type="button" class="sheet-close" aria-label="Close note options">×</button><button type="button" class="sheet-rename" data-action="rename">Rename note</button>'+pin+'<button type="button" class="sheet-delete" data-action="delete">Delete note</button>','',()=>false);
 dialog.className='note-sheet';dialog.querySelector('.sheet-close').onclick=()=>dialog.close();dialog.querySelector('.sheet-close').focus();
 dialog.querySelectorAll('[data-action]').forEach(b=>b.onclick=()=>{
   const action=b.dataset.action;dialog.close();
   if(action==='delete')modal('Delete note?',id===state.dailyId?'Today’s pad will reset to a blank note.':'This also removes its reminder.','Delete',()=>{api('removeNote',{id});go('notes');});
   else if(action==='rename')modal('Rename note','<label for="newTitle">Title</label><input required maxlength="200" id="newTitle" value="'+esc(n.title)+'">','Save',()=>{api('rename',{id,title:$('#newTitle').value});if(view==='edit')openNote(id);else render();});
   else{api('place',{id,slot:Number(action)});render();}
 });
}
function bindNoteRows(){
 screen.querySelectorAll('[data-note]').forEach(b=>{
   let hold,held=false,x,y;
   const stop=()=>clearTimeout(hold);
   b.onpointerdown=e=>{stop();held=false;x=e.clientX;y=e.clientY;hold=setTimeout(()=>{held=true;noteMenu(b.dataset.note);},520);};
   b.onpointermove=e=>{if(Math.abs(e.clientX-x)+Math.abs(e.clientY-y)>14)stop();};
   b.onpointercancel=b.onpointerup=b.onpointerleave=stop;
   b.oncontextmenu=e=>{e.preventDefault();stop();if(!held){held=true;noteMenu(b.dataset.note);}};
   b.onclick=()=>{if(!held){returnCal=false;openNote(b.dataset.note);}held=false;};
 });
}
$('#calendar').onclick=()=>{returnCal=false;go('calendar');};
