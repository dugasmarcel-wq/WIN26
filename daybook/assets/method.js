'use strict';
const areaIds=['four-spiritual','four-financial','four-physical','four-mental'];
const colorDefaults={
 'four-spiritual':'#79569b','four-financial':'#32725c','four-physical':'#a24d38','four-mental':'#326998',
 long:'#775286',weekly:'#396b94',daily:'#28755c',today:'#245748',notebook:'#895d42',calendar:'#486191'
};
function colorFor(key){return (state.colors||[]).find(c=>c.target===key)?.value||colorDefaults[key]||'#406e60';}
function applyColors(){
 for(const el of document.querySelectorAll('[data-seconds]'))el.style.height=Math.min(100,Number(el.dataset.seconds)/36)+'%';
 for(const el of document.querySelectorAll('[data-color-key]')){
   const hex=colorFor(el.dataset.colorKey);
   if(!/^#[a-f0-9]{6}$/i.test(hex))continue;
   const rgb=[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16));
   const ink=rgb.map(v=>v);
   while(ink.reduce((sum,v,i)=>sum+[.2126,.7152,.0722][i]*Math.pow((v/255+.055)/1.055,2.4),0)>.16)
     for(let i=0;i<3;i++)ink[i]*=.92;
   el.style.setProperty('--accent','rgb('+ink.map(Math.round).join(',')+')');
   el.style.setProperty('--tint','rgb('+rgb.map(v=>Math.round(v*.14+255*.86)).join(',')+')');
 }
}
function colorSettings(){
 const sets=[['Navigation',[['today','Today'],['notebook','Notebook'],['calendar','Note Calendar']]],
 ['Goal sections',[...areaIds.map(k=>[k,focusNames[k]]),...groups.map(g=>[g.id,g.label])]],
 ['Notebook folders',state.folders.map(f=>['folder-'+f.id,f.name])]];
 screen.insertAdjacentHTML('beforeend','<section class="palette"><h2>Tab colors</h2>'+sets.map(([title,pairs])=>
 '<fieldset><legend>'+title+'</legend>'+pairs.map(([k,v])=>'<label>'+esc(v)+'<input type="color" data-palette="'+esc(k)+'" value="'+esc(colorFor(k))+'"></label>').join('')+'</fieldset>').join('')+'</section>');
 screen.querySelectorAll('[data-palette]').forEach(input=>input.onchange=()=>{
   api('color',{target:input.dataset.palette,value:input.value});state.colors=api('state').colors;applyColors();
 });
}
function dateFields(start='',through='',min=''){
 return '<details class="goal-dates" '+(start||through?'open':'')+'><summary>Valid dates</summary><div class="date-fields"><label>From<input type="date" id="validFrom" value="'+esc(start)+'" min="'+esc(min)+'"></label><label>Through<input type="date" id="validThrough" value="'+esc(through)+'" min="'+esc(min)+'"></label></div></details>';
}
function editGoal(g){
 if(g.timed||g.task){
   modal(g.timed?'Edit aim':'Edit task','<label for="aim">'+(g.timed?'Current aim':'Task name')+'</label><input required maxlength="200" id="aim" value="'+esc(g.name)+'">'+(g.task?'<label for="deadline">Deadline (optional)</label><input type="date" id="deadline" value="'+esc(g.deadline)+'">':'')+'<p class="meta">Changes apply from today onward. Past investment and check-ins stay recorded.</p>','Save changes',()=>{api('goalEdit',{id:g.id,name:$('#aim').value,deadline:$('#deadline')?.value||''});render();});return;
 }
 const through=g.deleted||g.end==='9999-12-31'?'':dayAt(utc(g.end)-86400000);
 modal(g.deleted?'Set '+focusNames[g.id]+' aim':focusNames[g.id]||'Edit goal','<label for="aim">Goal text</label><input required maxlength="200" id="aim" value="'+esc(g.deleted?'':g.name)+'">'+dateFields(g.deleted?state.today:g.start,through,g.created)+'<p class="meta">Changes apply from today onward.</p>','Save changes',()=>{
   const start=$('#validFrom').value||g.created,end=$('#validThrough').value;
   if(end&&end<start){$('#validThrough').setCustomValidity('Choose the same day or a later one.');$('#validThrough').reportValidity();return false;}
   api('goalEdit',{id:g.id,restore:!!g.deleted,name:$('#aim').value,start,end:end?dayAt(utc(end)+86400000):'9999-12-31'});render();
 });
 $('#validThrough').oninput=()=>$('#validThrough').setCustomValidity('');
 $('#validFrom').oninput=()=>$('#validThrough').setCustomValidity('');
}
function reflectionList(d){
 const rows=api('reflections',{day:d}).filter(r=>r.body.trim());
 if(!rows.length)return '';
 return '<section class="reflections"><h3>Method of 4 journal</h3>'+rows.map(r=>'<article data-color-key="'+esc(r.goal)+'"><b>'+esc(focusNames[r.goal])+'</b><p>'+esc(r.body)+'</p></article>').join('')+'</section>';
}

function goalMenu(id){
 const goal=state.goals.find(g=>g.id===id);
 if(!goal||goal.deleted||dialog.open)return;
 const area=focusNames[id];
 modal(goal.name,'<button type="button" class="sheet-close" aria-label="Close goal options">×</button>'+
 '<button type="button" class="sheet-rename" id="menuEditGoal">Edit goal</button>'+
 '<button type="button" class="sheet-history" id="menuHistory">'+(area?'Reflection and history':'Check-in history')+'</button>'+
 (area?'':'<button type="button" class="sheet-delete" id="menuDeleteGoal">Remove goal</button>'),'',()=>false);
 dialog.className='note-sheet';
 dialog.querySelector('.sheet-close').onclick=()=>dialog.close();
 $('#menuEditGoal').onclick=()=>{dialog.close();editGoal(goal);};
 $('#menuHistory').onclick=()=>{dialog.close();showGoal(id);};
 if($('#menuDeleteGoal'))$('#menuDeleteGoal').onclick=()=>{
   dialog.close();
   modal('Delete this goal?','<p><b>'+esc(goal.name)+'</b></p><p>Past check-ins and accomplishments stay in your journal and tracker.</p>','Delete goal',()=>{
     api('removeGoal',{id});go('today');
   });
   $('#cancel').focus();
 };
}
function bindGoalActions(tile,id){
 let timer,held=false,x=0,y=0;
 const stop=()=>clearTimeout(timer);
 const open=()=>{stop();held=true;goalMenu(id);};
 tile.onpointerdown=e=>{
   if(!e.isPrimary||e.button!==0)return;
   stop();held=false;x=e.clientX;y=e.clientY;timer=setTimeout(open,480);
 };
 tile.onpointermove=e=>{if(Math.hypot(e.clientX-x,e.clientY-y)>10)stop();};
 tile.onpointerup=tile.onpointerleave=tile.onpointercancel=stop;
 tile.oncontextmenu=e=>{e.preventDefault();if(!held)open();};
 tile.addEventListener('click',e=>{
   if(held){held=false;e.preventDefault();e.stopImmediatePropagation();return;}
   if(tile.matches('button')||e.target.closest('[data-goal]')){e.preventDefault();goalMenu(id);}
 },true);
 tile.onkeydown=e=>{if(e.key==='ContextMenu'||(e.shiftKey&&e.key==='F10')){e.preventDefault();goalMenu(id);}};
}

function timeLabel(seconds){
 const m=Math.floor((seconds||0)/60);
 return m>=60?Math.floor(m/60)+'h'+(m%60?' '+m%60+'m':''):m+'m';
}
function methodToday(){
 const rows=state.goals.filter(g=>g.timed),seconds=rows.reduce((n,g)=>n+g.seconds,0),done=rows.filter(g=>g.seconds>=3600).length;
 return esc(timeLabel(seconds)+' / 4h · '+done+'/4');
}
function periodBounds(kind){
 const d=state.today,month=d.slice(0,7)+'-01',year=d.slice(0,4)+'-01-01',tomorrow=dayAt(utc(d)+86400000);
 const monday=dayAt(utc(d)-((new Date(utc(d)).getUTCDay()+6)%7)*86400000);
 return {week:[monday,tomorrow],month:[month,tomorrow],year:[year,tomorrow],life:['0001-01-01',tomorrow]}[kind];
}
function methodDetail(g){
 return '<section class="method-detail"><button class="primary" id="detailTime">Log time</button><div class="method-stats">'+['week','month','year','life'].map(k=>{
   const [from,until]=periodBounds(k),n=api('investmentStats',{from,until}).areas[g.id];
   return '<div><small>'+({week:'This week',month:'This month',year:'This year',life:'Lifetime'}[k])+'</small><b>'+esc(timeLabel(n))+'</b></div>';
 }).join('')+'</div></section>';
}
function timeEntry(id){
 const g=state.goals.find(x=>x.id===id);if(!g||!g.timed)return;
 const day=state.today,running=state.timer||{},own=running.goal===id;
 const html='<p class="aim-text">'+esc(g.name)+'</p><p class="time-total"><b>'+esc(timeLabel(g.seconds))+'</b> / 60 min'+(g.seconds>3600?' · '+esc(timeLabel(g.seconds-3600))+' extra':'')+'</p>'+
 '<div class="quick-times">'+[15,30,45,60].map(m=>'<button type="button" data-add-minutes="'+m+'">+'+m+'m</button>').join('')+'</div>'+
 '<label for="manualMinutes">Minutes</label><input id="manualMinutes" type="number" min="1" max="1440" step="1" required value="15">'+
 '<button type="button" id="correctTime">Correct today’s total</button>'+
 '<div class="timer-controls"><b id="elapsedTime">'+(own?'Running':'Stopwatch')+'</b><button type="button" id="timerAction" '+(running.goal&&!own?'disabled':'')+'>'+(own?'Stop and save':'Start')+'</button>'+(own?'<button type="button" id="discardTimer">Discard</button>':'')+'</div>'+
 (running.goal&&!own?'<p class="meta">Another category has a running stopwatch.</p>':own?'<p class="meta">Stopwatch time is added when you stop it. Only add manual time for separate practice.</p>':'');
 const save=(minutes,replace=false)=>{api('methodLog',{id,day,minutes,replace});render();};
 modal(focusNames[id],html,'Add time',()=>{save(Number($('#manualMinutes').value));});
 dialog.querySelectorAll('[data-add-minutes]').forEach(b=>b.onclick=()=>{save(Number(b.dataset.addMinutes));dialog.close();});
 $('#correctTime').onclick=()=>{dialog.close();modal('Correct today’s time','<label for="correctMinutes">Total minutes today</label><input id="correctMinutes" type="number" min="0" max="1440" step="1" required value="'+Math.floor(g.seconds/60)+'">','Save total',()=>save(Number($('#correctMinutes').value),true));};
 $('#timerAction').onclick=()=>{if(own)api('methodStop',{id});else api('methodStart',{id,day});dialog.close();render();};
 if(own){
   $('#discardTimer').onclick=()=>{dialog.close();modal('Discard stopwatch?','<p>Unrecorded stopwatch time will be discarded.</p>','Discard',()=>{api('methodStop',{id,discard:true});render();});};
   const elapsed=()=>{const el=$('#elapsedTime');if(!dialog.open||!el)return false;const s=Math.max(0,Math.floor((Date.now()-running.started)/1000));el.textContent=Math.floor(s/3600)+':'+String(Math.floor(s/60)%60).padStart(2,'0')+':'+String(s%60).padStart(2,'0');return true;};
   elapsed();const tick=setInterval(()=>{if(!elapsed())clearInterval(tick);},1000);
 }
}
