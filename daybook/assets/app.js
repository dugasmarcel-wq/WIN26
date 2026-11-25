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
 state=api('state');$('#today').className=['today','goal'].includes(view)?'active':'';$('#notebook').className=['notes','edit'].includes(view)?'active':'';
 if(view==='today')today();else if(view==='notes')notes();else if(view==='edit')editor();else if(view==='goal')detail();else settings();
}
function today(){
 const active=state.goals.filter(g=>g.active),ended=state.goals.filter(g=>!g.active),done=active.filter(g=>g.done).length;
 screen.innerHTML='<p class="eyebrow">'+esc(pretty(state.today,{weekday:'long',year:'numeric'}))+'</p><h1>'+esc(pretty(state.today,{month:'long',day:'numeric'}))+'</h1><div class="summary"><strong>'+done+' / '+active.length+'</strong> goals checked today<progress aria-label="Daily completion" value="'+done+'" max="'+Math.max(1,active.length)+'"></progress></div><div class="section"><h2>Daily goals</h2><button class="primary" id="addGoal">Add goal</button></div>'
 + (active.length?active.map(goalRow).join(''):'<p class="empty">Add a goal to start your first daily check-in.</p>')+
 (ended.length?'<div class="section"><h2>Goal archive</h2></div>'+ended.map(goalRow).join(''):'');
 $('#addGoal').onclick=()=>modal('New goal','<label for="gname">Goal name</label><input id="gname" maxlength="200" required placeholder="Read for 20 minutes"><label for="period">Check in every day for</label><select id="period">'+['Week','Month','6 months','Year'].map(t=>'<option>'+t+'</option>').join('')+'</select>','Create goal',()=>{api('goal',{name:$('#gname').value,period:$('#period').value});render();});
 screen.querySelectorAll('[data-mark]').forEach(b=>b.onclick=()=>{const g=state.goals.find(g=>g.id===b.dataset.mark);api('mark',{id:g.id,day:state.today,done:!g.done});render();});
 screen.querySelectorAll('[data-goal]').forEach(b=>b.onclick=()=>showGoal(b.dataset.goal));
}
function goalRow(g){return '<div class="card goal">'+(g.active?'<button class="bubble '+(g.done?'on':'')+'" aria-pressed="'+Boolean(g.done)+'" aria-label="'+esc((g.done?'Undo today for ':'Complete today for ')+g.name)+'" data-mark="'+esc(g.id)+'"></button>':'')+'<button class="rowtext" data-goal="'+esc(g.id)+'"><span>'+esc(g.name)+'</span><span class="meta">'+esc(g.period)+' · '+(g.prior+g.done)+' done · <span class="miss">'+g.missed+' missed</span></span></button></div>';}
function showGoal(id){goalId=id;view='goal';setRoute('goal/'+id);render();window.scrollTo(0,0);}
function detail(){const g=state.goals.find(x=>x.id===goalId);if(!g){go('today');return;}
 const marks=new Set(api('history',{id:g.id}).map(x=>x.day));let cells='';
 for(let t=Math.min(utc(state.today),utc(g.end)-86400000);t>=utc(g.start);t-=86400000){const d=dayAt(t),done=marks.has(d),today=d===state.today;
 cells+='<div class="'+(done?'yes':today?'':'no')+'">'+esc(pretty(d,{month:'short',day:'numeric'}))+'<span>'+(done?'Done':today?'Open':'Missed')+'</span></div>';}
 screen.innerHTML='<button class="back" id="back">‹ Today</button><h1>'+esc(g.name)+'</h1><p class="detail">'+esc(g.period)+' · '+esc(pretty(g.start,{dateStyle:'medium'}))+' – '+esc(pretty(dayAt(utc(g.end)-86400000),{dateStyle:'medium'}))+'</p><div class="summary"><strong>'+g.missed+'</strong> missed · '+(g.prior+g.done)+' completed</div><label class="card"><input type="checkbox" id="pin" '+(g.pinned?'checked':'')+'> Show on home-screen widget</label><div class="section"><h2>Check-in history</h2></div><div class="history">'+cells+'</div>';
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
 api('save',entry);draft=false;setRoute('note/'+entry.id);$('#saved').textContent='Saved on device';}
function editor(){
 screen.innerHTML='<div class="editor-head"><button class="back" id="back">‹ Notebook</button><span class="meta" id="saved">'+(draft?'New note':'Saved on device')+'</span></div><label for="ntitle">Title</label><input id="ntitle" placeholder="Untitled note" maxlength="300" value="'+esc(entry.title)+'"><label for="nfolder">Folder</label><select id="nfolder">'+state.folders.map(f=>'<option value="'+esc(f.id)+'" '+(f.id===entry.folder?'selected':'')+'>'+esc(f.name)+'</option>').join('')+'</select><label for="body">Note</label><textarea id="body" placeholder="Start writing…">'+esc(entry.body)+'</textarea><div class="card setting"><div><h3>Reminder</h3><small id="when">'+esc(entry.alarm?stamp(entry.alarm):'No reminder')+'</small></div><button id="remind">'+(entry.alarm?'Change':'Set reminder')+'</button></div>';
 $('#back').onclick=()=>go('notes');$('#body').oninput=()=>save();$('#ntitle').oninput=()=>save();$('#nfolder').onchange=()=>save();$('#remind').onclick=remind;
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
 if(type==='route'){if(dialog.open)dialog.close();if(value==='new')openNote(null,'daily');else if(value.startsWith('note/'))openNote(value.slice(5));else if(value.startsWith('goal/'))showGoal(value.slice(5));else go(['notes','settings'].includes(value)?value:'today');}
 else if(type==='refresh'&&!dialog.open){if(view==='edit'){if(!draft){let n=api('note',{id:entry.id});if(n){entry.alarm=n.alarm;$('#when').textContent=entry.alarm?stamp(entry.alarm):'No reminder';$('#remind').textContent=entry.alarm?'Change':'Set reminder';}}}else render();}
};
window.goBack=()=>{if(dialog.open){dialog.close();return true;}if(view==='edit'){go('notes');return true;}if(view!=='today'){go('today');return true;}return false;};
