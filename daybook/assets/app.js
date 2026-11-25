'use strict';
const $=s=>document.querySelector(s),screen=$('#screen'),dialog=$('#dialog');
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let state,view='today',folder='daily',entry=null,goalId='',draft=false,submitForm=null,timer;
function msg(s){$('#status').textContent=s;clearTimeout(timer);timer=setTimeout(()=>$('#status').textContent='',3400);}
function api(op,a={}){const r=JSON.parse(Daybook.call(op,JSON.stringify(a)));if(!r.ok){msg(r.error);throw Error(r.error);}return r.value;}
const pretty=(s,opt)=>new Date(s+'T12:00:00').toLocaleDateString(undefined,opt);
const stamp=t=>new Date(t).toLocaleString(undefined,{dateStyle:'medium',timeStyle:'short'});
const utc=s=>Date.parse(s+'T00:00:00Z');
const dayAt=n=>new Date(n).toISOString().slice(0,10);
function setRoute(r){api('location',{route:r});}
function go(v){view=v;entry=null;setRoute(v);render();window.scrollTo(0,0);}
function modal(title,html,confirm,callback){$('#fields').innerHTML='<h2>'+esc(title)+'</h2>'+html;$('#confirm').textContent=confirm;submitForm=callback;dialog.showModal();}
$('#cancel').onclick=()=>dialog.close();
$('#form').onsubmit=e=>{e.preventDefault();if(submitForm()!==false)dialog.close();};
function render(){
 document.body.classList.toggle('writing',view==='edit');
 state=api('state');$('#today').className=['today','goal'].includes(view)?'active':'';$('#notebook').className=['notes','edit'].includes(view)?'active':'';
 if(view==='today')today();else if(view==='notes')notes();else if(view==='edit')editor();else if(view==='goal')detail();else settings();
}
const groups = [
 {id:'long', label:'Long-term', hint:'Check in daily'},
 {id:'weekly', label:'Weekly', hint:'Once each week'},
 {id:'daily', label:'Daily', hint:'Every day'}
];
function groupOf(g){return g.period==='Daily'?'daily':g.period==='Weekly'?'weekly':'long';}
function today(){
 const active=state.goals.filter(g=>g.active), ended=state.goals.filter(g=>!g.active);
 screen.innerHTML='<div class="day-heading"><div><p class="eyebrow">'+esc(pretty(state.today,{weekday:'long'}))+'</p><h1>'+esc(pretty(state.today,{month:'long',day:'numeric'}))+'</h1></div><div class="goal-actions"><button id="jumpTracker">Tracker</button><button class="primary" id="addGoal">Add goal</button></div></div>'+
 groups.map(group=>{
   const items=active.filter(g=>groupOf(g)===group.id);
   return '<section class="goal-group '+group.id+'"><div class="group-head"><div><h2>'+group.label+'</h2><small>'+group.hint+'</small></div><span class="count">'+items.filter(g=>g.done).length+'/'+items.length+'</span></div><div class="bubbles">'+items.map(goalBubble).join('')+
     '<button class="new-bubble" data-new="'+group.id+'" aria-label="Add '+group.label+' goal"><span aria-hidden="true">+</span>Add goal</button></div></section>';
 }).join('')+(ended.length?'<h2 class="section">Goal archive</h2>'+ended.map(g=>'<button class="item card" data-goal="'+esc(g.id)+'">'+esc(g.name)+'</button>').join(''):'');
 screen.insertAdjacentHTML('beforeend','<section id="trackerPanel" tabindex="-1" aria-label="Completion tracker"></section>');
 showTracker();
 $('#jumpTracker').onclick=jumpToTracker;
 $('#addGoal').onclick=()=>newGoal('long');
 screen.querySelectorAll('[data-new]').forEach(b=>b.onclick=()=>newGoal(b.dataset.new));
 screen.querySelectorAll('[data-mark]').forEach(b=>b.onclick=()=>{
   const g=state.goals.find(g=>g.id===b.dataset.mark);
   api('mark',{id:g.id,day:state.today,done:!g.done});render();
 });
 screen.querySelectorAll('[data-goal]').forEach(b=>b.onclick=()=>showGoal(b.dataset.goal));
}
function goalBubble(g){
 const unit=groupOf(g)==='weekly'?'this week':'today';
 return '<div class="goal-tile"><button class="goal-check" data-mark="'+esc(g.id)+'" aria-pressed="'+Boolean(g.done)+'" aria-label="'+esc(g.name+', '+unit)+'"><span class="orb" aria-hidden="true">'+(g.done?'✓':'')+'</span><span class="goal-name">'+esc(g.name)+'</span></button>'+
 '<button class="goal-history" data-goal="'+esc(g.id)+'" aria-label="'+esc('History of '+g.name)+'">'+g.missed+' missed · History</button></div>';
}
function newGoal(group){
 modal('New goal','<label for="gname">Goal name</label><input id="gname" required maxlength="200" placeholder="Read for 20 minutes">'+
 '<label for="kind">Goal type</label><select id="kind">'+groups.map(g=>'<option value="'+g.id+'" '+(g.id===group?'selected':'')+'>'+g.label+'</option>').join('')+'</select>'+
 '<div id="horizon"><label for="period">Check in daily for</label><select id="period">'+['Week','Month','6 months','Year'].map(p=>'<option>'+p+'</option>').join('')+'</select></div><p class="meta" id="scheduleHint"></p>',
 'Create goal',()=>{const kind=$('#kind').value;api('goal',{name:$('#gname').value,period:kind==='long'?$('#period').value:kind==='weekly'?'Weekly':'Daily'});render();});
 function schedule(){const k=$('#kind').value;$('#horizon').hidden=k!=='long';$('#scheduleHint').textContent=k==='weekly'?'Check in once each Monday–Sunday week.':k==='daily'?'A fresh check-in opens every day.':'';}
 $('#kind').onchange=schedule;schedule();
}
function showGoal(id){goalId=id;view='goal';setRoute('goal/'+id);render();window.scrollTo(0,0);}
function detail(){
 const g=state.goals.find(x=>x.id===goalId);if(!g){go('today');return;}
 const category=groupOf(g),weekly=category==='weekly',ongoing=category!=='long';
 const marks=new Set(api('history',{id:g.id}).map(x=>x.day));
 const day=86400000,start=utc(g.start),now=utc(state.today);
 const monday=t=>t-((new Date(t).getUTCDay()+6)%7)*day;
 const last=weekly?monday(now):Math.min(now,utc(g.end)-day),first=weekly?monday(start):start;
 let cells='';
 for(let t=last;t>=first;t-=weekly?7*day:day){
   const date=dayAt(Math.max(start,t)),done=marks.has(date),open=g.active&&t===last;
   const range=esc(pretty(date,{month:'short',day:'numeric'}))+(weekly?' – '+esc(pretty(dayAt(t+6*day),{month:'short',day:'numeric'})):'');
   cells+='<div class="'+(done?'yes':open?'':'no')+'">'+range+'<span>'+(done?'Done':open?'Open':'Missed')+'</span></div>';
 }
 const duration=ongoing?'Since '+pretty(g.start,{dateStyle:'medium'}):g.period+' · '+pretty(g.start,{dateStyle:'medium'})+' – '+pretty(dayAt(utc(g.end)-day),{dateStyle:'medium'});
 screen.innerHTML='<button class="back" id="back">‹ Today</button><h1>'+esc(g.name)+'</h1><p class="detail">'+esc(duration)+'</p><div class="summary"><strong>'+g.missed+'</strong> missed '+(weekly?'weeks':'days')+' · '+(g.prior+g.done)+' completed</div><label class="card"><input type="checkbox" id="pin" '+(g.pinned?'checked':'')+'> Show on home-screen widget</label><div class="section"><h2>Check-in history</h2></div><div class="history">'+cells+'</div>';
 $('#back').onclick=()=>go('today');$('#pin').onchange=e=>api('pin',{id:g.id,on:e.target.checked});
}
function notes(){
 if(!state.folders.some(f=>f.id===folder))folder='daily';
 const list=state.notes.filter(n=>n.folder===folder);
 screen.innerHTML='<h1>Notebook</h1><div class="chips">'+state.folders.map(f=>'<button class="'+(f.id===folder?'chosen':'')+'" data-folder="'+esc(f.id)+'">'+esc(f.name)+'</button>').join('')+'<button id="newFolder">+ Folder</button></div><div class="section"><h2>'+list.length+' '+(list.length===1?'note':'notes')+'</h2><button class="primary" id="newNote">New note</button></div>'+
 (list.length?list.map(n=>'<button class="item card" data-note="'+esc(n.id)+'"><b>'+esc(n.title||'Untitled note')+'</b><div class="note-preview">'+esc(n.excerpt)+'</div><small>'+esc(n.alarm?'Reminder · '+stamp(n.alarm):stamp(n.changed))+'</small></button>').join(''):'<p class="empty">Write your first note in this folder.</p>');
 $('#newFolder').onclick=()=>modal('New folder','<label for="fname">Folder name</label><input id="fname" maxlength="200" required>','Create folder',()=>{const name=$('#fname').value.trim();api('folder',{name});folder=api('state').folders.find(f=>f.name===name).id;render();});
 $('#newNote').onclick=()=>openNote(null,folder);screen.querySelectorAll('[data-folder]').forEach(b=>b.onclick=()=>{folder=b.dataset.folder;render();});screen.querySelectorAll('[data-note]').forEach(b=>b.onclick=()=>openNote(b.dataset.note));
}
function openNote(id,where='daily'){
 entry=id?api('note',{id}):null;if(id&&!entry){go('notes');return;}
 draft=!entry;if(!entry)entry={id:api('uuid'),folder:where,title:'',body:'',alarm:0};
 folder=entry.folder;view='edit';setRoute(draft?'new':'note/'+entry.id);render();window.scrollTo(0,0);
}
function save(force=false){entry.title=$('#ntitle').value;entry.body=$('#body').value;entry.folder=$('#nfolder').value;folder=entry.folder;
 if(draft&&!force&&!entry.title.trim()&&!entry.body.trim())return;
 api('save',entry);if(draft)setRoute('note/'+entry.id);draft=false;$('#saved').textContent='Saved';}
function editor(){
 screen.innerHTML='<div class="editor-head"><button class="back" id="back">‹ Notebook</button><span id="saved" class="meta">'+(draft?'New note':'Saved')+'</span><button id="remind" aria-label="Set or change reminder"></button></div>'+
 '<div class="note-meta"><input id="ntitle" aria-label="Note title" placeholder="Untitled note" maxlength="300"><select id="nfolder" aria-label="Folder">'+state.folders.map(f=>'<option value="'+esc(f.id)+'" '+(f.id===entry.folder?'selected':'')+'>'+esc(f.name)+'</option>').join('')+'</select></div>'+
 '<textarea id="body" aria-label="Note text" placeholder="Start writing…" spellcheck="true"></textarea>';
 $('#ntitle').value=entry.title;$('#body').value=entry.body;
 $('#back').onclick=()=>go('notes');$('#body').oninput=()=>save();$('#ntitle').oninput=()=>save();$('#nfolder').onchange=()=>save();$('#remind').onclick=remind;
 reminderLabel();
}
function reminderLabel(){
 const b=$('#remind');if(!b)return;
 b.textContent=entry.alarm?new Date(entry.alarm).toLocaleDateString(undefined,{month:'short',day:'numeric'})+' · '+new Date(entry.alarm).toLocaleTimeString(undefined,{hour:'numeric',minute:'2-digit'}):'Reminder';
 b.setAttribute('aria-label',entry.alarm?'Change reminder for '+stamp(entry.alarm):'Set reminder');
}
function remind(){const dt=new Date(entry.alarm||Date.now()+3600000);const local=new Date(dt-dt.getTimezoneOffset()*60000).toISOString().slice(0,16);
 modal('Note reminder','<label for="at">Local date and time</label><input type="datetime-local" required id="at" value="'+local+'">'+(entry.alarm?'<button type="button" id="removeAlarm">Remove reminder</button>':''),'Set reminder',()=>{
 const at=new Date($('#at').value).getTime();if(!Number.isFinite(at)||at<=Date.now()){msg('Choose a future time.');return false;}save(true);api('alarm',{id:entry.id,at});entry.alarm=at;editor();});
 if($('#removeAlarm'))$('#removeAlarm').onclick=()=>{api('alarm',{id:entry.id,at:0});entry.alarm=0;dialog.close();editor();};
}
function settings(){screen.innerHTML='<h1>Settings</h1><div class="settings-list">'+setting('Reminders',state.notifications?'Notifications enabled':'Notifications disabled','Manage','perms')+
 setting('Reminder timing',state.exact?'Exact alarms enabled':'Android may delay reminders','Timing','timing')+
 setting('App updates','Daybook '+esc(state.version),'Check now','updates')+setting('Back up journal','Save notes, goals and check-ins.','Export','export')+
 setting('Restore journal','Replace this journal from a backup.','Restore','import')+'</div>';
 for(const [id,op] of [['perms','permissions'],['timing','exact'],['updates','check'],['export','backup'],['import','restore']])$('#'+id).onclick=()=>api(op);
 if(state.exact)$('#timing').hidden=true;}
function setting(title,desc,label,id){return '<div class="card setting"><div><h3>'+title+'</h3><small class="meta">'+desc+'</small></div><button id="'+id+'">'+label+'</button></div>';}
$('#today').onclick=()=>go('today');$('#notebook').onclick=()=>go('notes');$('#settings').onclick=()=>go('settings');
window.nativeEvent=(type,value)=>{
 if(type==='route'){if(dialog.open)dialog.close();if(value==='tracker'){trackerMode='month';trackerMonth='';pickedDay='';go('today');jumpToTracker();}else if(value==='new')openNote(null,'daily');else if(value.startsWith('note/'))openNote(value.slice(5));else if(value.startsWith('goal/'))showGoal(value.slice(5));else go(['notes','settings'].includes(value)?value:'today');}
 else if(type==='refresh'&&!dialog.open){if(view==='edit'){if(!draft){let n=api('note',{id:entry.id});if(n){entry.alarm=n.alarm;reminderLabel();}}}else render();}
};
window.goBack=()=>{if(dialog.open){dialog.close();return true;}if(view==='edit'){go('notes');return true;}if(view!=='today'){go('today');return true;}return false;};
