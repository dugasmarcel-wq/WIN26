(function(){
'use strict';
const q=(s,r=document)=>r.querySelector(s);
const qa=(s,r=document)=>Array.from(r.querySelectorAll(s));
const E=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fmt=s=>{let m=Math.floor(Math.max(0,s||0)/60);return (m>=60?Math.floor(m/60)+'h ':'')+(m%60||m<60?m%60+'m':'');};
const areas=['four-spiritual','four-financial','four-physical','four-mental'];
const names=['Spiritual','Financial','Physical','Mental'];
const defaults=['#82649A','#36715B','#A3543E','#426E99'];
const dayOffset=(d,n)=>new Date(Date.parse(d+'T12:00:00Z')+n*86400000).toISOString().slice(0,10);
const native=(op,a={})=>api(op,a);
function col(id){let i=areas.indexOf(id);let c=typeof colorFor==='function'?colorFor(id):defaults[i];return /^#[0-9a-f]{6}$/i.test(c)?c:defaults[i];}
function stateNow(){return native('state');}
function currentGoals(s){return (s.goals||[]).filter(g=>areas.includes(g.id));}
function el(tag,cls,txt){let e=document.createElement(tag);if(cls)e.className=cls;if(txt!==undefined)e.textContent=txt;return e;}
function button(cls,txt,fn){let b=el('button',cls,txt);b.type='button';b.onclick=fn;return b;}
function refresh(){render();}
function addTime(id,minutes,replace=false){native('methodLog',{id,day:stateNow().today,minutes,replace});if(dialog.open)dialog.close();refresh();}
const timerSeconds=t=>Math.max(0,Math.floor(((Number(t.elapsed)||0)+(t.paused?0:Date.now()-Number(t.started||Date.now())))/1000));
function showDial(id){
 let s=stateNow(),g=(s.goals||[]).find(x=>x.id===id);if(!g||!g.timed)return;
 const own=s.timer?.goal===id,other=s.timer?.goal&&!own,paused=own&&s.timer.paused;
 modal(names[areas.indexOf(id)],'<div class="pv-time-head"><span>'+E(g.name)+'</span><strong>'+E(fmt(g.seconds))+' <small>/ 1h today</small></strong></div>'+
 '<div class="pv-clock-wrap"><svg id="pvDial" class="pv-clock" viewBox="0 0 240 240" role="slider" tabindex="0" aria-label="Minutes to add" aria-valuemin="1" aria-valuemax="1440"><circle cx="120" cy="120" r="100" class="pv-dial-track"/><circle id="pvArc" cx="120" cy="120" r="100" class="pv-dial-arc" transform="rotate(-90 120 120)"/><g id="pvTicks"></g><circle id="pvKnob" r="9" class="pv-dial-knob"/><text id="pvClockValue" x="120" y="121" text-anchor="middle" class="pv-clock-value"></text><text x="120" y="142" text-anchor="middle" class="pv-clock-sub">SWIPE TO SET</text></svg></div>'+
 '<div class="pv-adjust"><button type="button" id="pvLess">− 1h</button><label>Minutes<input id="pvMinutes" type="number" min="1" max="1440" required value="30"></label><button type="button" id="pvMore">+ 1h</button></div>'+
 '<button type="button" class="pv-hour" id="pvHour">Log 1 hour now</button><div class="pv-stop"><div><small>STOPWATCH</small><strong id="pvElapsed">00:00:00</strong></div><button type="button" id="pvTimer" '+(other?'disabled':'')+'>'+(own?(paused?'Resume':'Pause'):'Start')+'</button>'+(own?'<button type="button" id="pvFinish">Finish &amp; save</button><button type="button" id="pvDiscard" class="pv-text-btn">Discard</button>':'')+'</div>'+
 (other?'<p class="meta">Another category is running.</p>':'')+'<button type="button" id="pvCorrect" class="pv-text-btn">Correct today’s total</button>','Save time',()=>addTime(id,Number(q('#pvMinutes').value)));
 const dial=q('#pvDial'),field=q('#pvMinutes');dial.style.color=col(id);let total=30,last=null,drag=false;
 let ticks='';for(let i=0;i<60;i++){let a=i*Math.PI/30,r=i%5===0?81:87;ticks+='<line x1="'+(120+91*Math.sin(a))+'" y1="'+(120-91*Math.cos(a))+'" x2="'+(120+r*Math.sin(a))+'" y2="'+(120-r*Math.cos(a))+'" class="'+(i%5===0?'major':'minor')+'"/>';};q('#pvTicks').innerHTML=ticks;
 function update(v){total=Math.max(1,Math.min(1440,Math.round(v)||1));field.value=total;let a=total*Math.PI/30,frac=total%60/60||1;q('#pvKnob').setAttribute('cx',120+100*Math.sin(a));q('#pvKnob').setAttribute('cy',120-100*Math.cos(a));q('#pvArc').setAttribute('stroke-dasharray',(frac*628.319)+' 628.319');q('#pvClockValue').textContent=fmt(total*60);dial.setAttribute('aria-valuenow',total);dial.setAttribute('aria-valuetext',fmt(total*60));}
 function angle(e){let b=dial.getBoundingClientRect();return (Math.atan2(e.clientX-b.left-b.width/2,-(e.clientY-b.top-b.height/2))*30/Math.PI+60)%60;}
 dial.onpointerdown=e=>{e.preventDefault();dial.setPointerCapture(e.pointerId);drag=true;last=angle(e);let v=Math.floor(total/60)*60+last;if(total%60===0&&last>45)v-=60;update(v);};
 dial.onpointermove=e=>{if(!drag)return;let a=angle(e),delta=a-last;if(delta>30)delta-=60;if(delta< -30)delta+=60;if(Math.abs(delta)>=1){update(total+delta);last=a;}};
 dial.onpointerup=dial.onpointercancel=()=>{drag=false;last=null;};
 dial.onkeydown=e=>{if(['ArrowUp','ArrowRight','ArrowDown','ArrowLeft'].includes(e.key)){e.preventDefault();update(total+(['ArrowUp','ArrowRight'].includes(e.key)?1:-1));}};
 field.onchange=()=>update(Number(field.value));q('#pvLess').onclick=()=>update(total-60);q('#pvMore').onclick=()=>update(total+60);q('#pvHour').onclick=()=>addTime(id,60);
 q('#pvCorrect').onclick=()=>{dialog.close();modal('Correct today’s total','<label>Total minutes<input id="pvCorrection" type="number" required min="0" max="1440" value="'+Math.floor((g.seconds||0)/60)+'"></label>','Save total',()=>addTime(id,Number(q('#pvCorrection').value),true));};
 q('#pvTimer').onclick=()=>{if(own){native(paused?'methodResume':'methodPause',{id});}else{native('methodStart',{id,day:s.today});}dialog.close();refresh();showDial(id);};
 if(own)q('#pvFinish').onclick=()=>{native('methodStop',{id});dialog.close();refresh();};
 if(own)q('#pvDiscard').onclick=()=>{dialog.close();modal('Discard this session?','<p>No time from this stopwatch session will be saved.</p>','Discard',()=>{native('methodStop',{id,discard:true});refresh();});};
 function clock(){let n=q('#pvElapsed');if(!n||!dialog.open)return;let v=own?timerSeconds(s.timer):0;n.textContent=[Math.floor(v/3600),Math.floor(v/60)%60,v%60].map(x=>String(x).padStart(2,'0')).join(':');}
 update(total);clock();if(own){let t=setInterval(()=>{if(!q('#pvElapsed')||!dialog.open)clearInterval(t);else clock();},500);}
}
function adornToday(){
 const grid=q('.four-grid');if(!grid)return;
 let s=stateNow(),gs=currentGoals(s);grid.classList.add('pv-four');grid.replaceChildren();
 for(let i=0;i<areas.length;i++){let id=areas[i],g=gs.find(x=>x.id===id);if(!g)continue;
 let cell=el('div','pv-area'),ring=button('pv-ring','',()=>showDial(id));cell.style.setProperty('--pv-area',col(id));ring.setAttribute('aria-label','Log '+names[i]+' time, '+fmt(g.seconds)+' recorded');let fraction=Math.min(1,(g.seconds||0)/3600);
 ring.innerHTML='<svg viewBox="0 0 88 88" aria-hidden="true"><circle cx="44" cy="44" r="36" class="pv-track"/><circle cx="44" cy="44" r="36" class="pv-progress" transform="rotate(-90 44 44)" stroke-dasharray="'+(fraction*226.195)+' 226.195"/></svg><span class="pv-ring-label">'+E(fmt(g.seconds))+'</span>';
 cell.append(ring,button('pv-area-name',names[i],()=>showModule(id)));if(g.seconds>3600)cell.append(el('small','pv-extra','+'+fmt(g.seconds-3600)));grid.append(cell);}
 let head=q('.method-head');if(head){let p=q('span',head);if(p)p.textContent=fmt(gs.reduce((a,g)=>a+(g.seconds||0),0))+' / 4h';}
 let sec=q('.four-section');if(sec&&!q('.pv-balanced',sec)){let n=gs.reduce((a,g)=>a+Math.min(3600,g.seconds||0),0);sec.append(el('div','pv-balanced','Balanced '+fmt(n)+' of 4h · '+gs.filter(g=>g.seconds>=3600).length+'/4 targets'));}
 for(let section of qa('.goal-group')){section.classList.add('pv-list');for(let tile of qa('.goal-tile',section)){let b=q('.goal-check',tile);if(b)tile.classList.toggle('pv-done',b.getAttribute('aria-pressed')==='true');}}
 let pad=q('.daily-pad');if(pad){let h=q('h2',pad);if(h)h.textContent=new Date(s.today+'T12:00:00').toLocaleDateString(undefined,{weekday:'long'})+'’s journal';let text=q('textarea',pad);if(text){text.rows=4;text.classList.add('pv-paper');}}
 if(!q('#pvReview')){let b=button('pv-review','Weekly review',review);b.id='pvReview';if(pad)pad.after(b);else screen.append(b);}
}
function review(){
 let s=stateNow(),d=s.today,weekday=(new Date(d+'T12:00:00Z').getUTCDay()+6)%7,from=dayOffset(d,-weekday),until=dayOffset(d,1),stats=native('investmentStats',{from,until}),values=stats.areas||{},actual=areas.reduce((n,k)=>n+(values[k]||0),0);
 let balanced=stats.balanced||0,done=stats.dailyDone||0,habits=done+(stats.dailyMissed||0),tracked=true;
 let pending=(s.goals||[]).filter(g=>g.active&&!g.done&&!areas.includes(g.id)&&g.period!=='Daily');
 modal('Weekly review','<p class="meta">'+E(from)+' – '+E(d)+' · week to date</p><div class="pv-review-total"><strong>'+E(fmt(actual))+'</strong><span>Actual investment</span></div>'+areas.map((k,i)=>'<div class="pv-review-line"><span>'+names[i]+'</span><b>'+E(fmt(values[k]))+'</b><progress max="'+((weekday+1)*3600)+'" value="'+Math.min((weekday+1)*3600,values[k]||0)+'"></progress></div>').join('')+(tracked?'<p>Balanced investment: <b>'+E(fmt(balanced))+'</b></p><p>Daily habits completed: <b>'+done+' / '+habits+'</b> · '+(stats.dailyOpen||0)+' pending today</p><p>Weekly tasks completed: <b>'+(stats.weekly||0)+'</b> · Objectives achieved: <b>'+(stats.objectives||0)+'</b></p>':'')+'<h3>Outstanding responsibilities</h3>'+(pending.length?pending.map(g=>'<p class="pv-review-task">'+E(g.name)+(g.deadline?' · '+E(g.deadline):'')+'</p>').join(''):'<p>No outstanding responsibilities.</p>'),'Close',()=>true);
}
function searchNotes(){
 let s=stateNow();modal('Find in Notebook','<label for="pvSearch">Search notes and journals</label><input type="search" id="pvSearch" placeholder="Search titles or writing"><div id="pvResults"></div>','Close',()=>true);
 const f=q('#pvSearch'),out=q('#pvResults');let timeout;f.oninput=()=>{clearTimeout(timeout);timeout=setTimeout(()=>{let term=f.value.trim().toLowerCase();out.replaceChildren();if(!term)return;let hits=0;for(let n of s.notes||[]){if(n.id.startsWith('module-data-'))continue;if(hits>=80)break;let detail=native('note',{id:n.id});if(!detail||!((detail.title||'')+' '+(detail.body||'')).toLowerCase().includes(term))continue;hits++;out.append(button('pv-search-result',(detail.title||'Untitled')+' · '+(detail.written||''),()=>{dialog.close();openNote(n.id,detail.folder);}));}if(!hits)out.append(el('p','meta','No matching notes.'));},250);};f.focus();
}
function adornOther(){if((view==='notes'||view==='journal')&&!q('#pvSearchButton')){let b=button('pv-search','Search notebook',searchNotes);b.id='pvSearchButton';let h=q('h1',screen);if(h)h.after(b);else screen.prepend(b);}
 if(view==='settings'){let b=q('#updates');if(b)b.closest('.setting')?.remove();let h=q('h1',screen);if(h)h.after(el('p','pv-preview-label','Daybook · standalone edition · no online updates'));const status=native('backupStatus');let card=el('section','pv-backup card');card.append(el('h3','','Automatic backups'),el('p','meta',status.enabled?(status.error?'Backup error: '+status.error:'Last backup: '+(status.last||'Not yet saved')):'Choose a folder outside Daybook. A dated JSON copy is saved when you use or leave the app.'),button('primary',status.enabled?'Change backup folder':'Choose backup folder',()=>native('backupFolder')));if(status.enabled)card.append(button('','Back up now',()=>native('backupNow')));q('.settings-list')?.append(card);}
}
let liveTimer=null;
function banner(){
 let s=stateNow(),t=s.timer||{},b=q('#pvLive');liveTimer=t;if(!t.goal){b?.remove();document.body.classList.remove('pv-timing');return;}document.body.classList.add('pv-timing');if(!b){b=el('div','pv-live');b.id='pvLive';document.body.append(b);}b.replaceChildren(button('pv-live-open',(t.paused?'Paused · ':'Timing · ')+(names[areas.indexOf(t.goal)]||'Timer'),()=>showDial(t.goal)),el('strong','pv-live-time',''),button('pv-live-stop',t.paused?'Resume':'Pause',()=>{native(t.paused?'methodResume':'methodPause',{id:t.goal});refresh();}),button('pv-live-stop','Save',()=>{native('methodStop',{id:t.goal});refresh();}));updateBanner();
}
function updateBanner(){let b=q('#pvLive');if(!b||!liveTimer)return;let v=timerSeconds(liveTimer);q('.pv-live-time',b).textContent=[Math.floor(v/3600),Math.floor(v/60)%60,v%60].map(x=>String(x).padStart(2,'0')).join(':');}
function weekdayFields(g){return '<div id="pvHabitFields"><label>Repeat on</label><div class="pv-days">'+['M','T','W','T','F','S','S'].map((d,i)=>'<label><input type="checkbox" data-habit-day="'+i+'" '+((Number(g?.habitMask||127)&(1<<i))?'checked':'')+'><span>'+d+'</span></label>').join('')+'</div><label>Time of day<select id="pvHabitPart">'+['Anytime','Morning','Evening'].map(x=>'<option '+(x===(g?.habitPart||'Anytime')?'selected':'')+'>'+x+'</option>').join('')+'</select></label></div>';}
function cadenceFields(g){let opt=state.extras||{},scheduled=opt.schedule?.find(x=>x.goal===g?.id)?.day||'',cad=opt.repeat?.find(x=>x.goal===g?.id)?.cadence||'none';return '<div id="pvTaskFields"><label>Scheduled date (optional)<input type="date" id="pvScheduled" value="'+E(scheduled)+'"></label><label>Deadline (optional)<input type="date" id="pvDeadline" value="'+E(g?.deadline||'')+'"></label><label>Repeat<select id="pvRepeat">'+[['none','Never'],['weekly','Weekly'],['monthly','Monthly'],['yearly','Yearly'],['daily','Daily']].map(([k,n])=>'<option value="'+k+'" '+(cad===k?'selected':'')+'>'+n+'</option>').join('')+'</select></label></div>';}
function habitArgs(id){let mask=qa('[data-habit-day]:checked',dialog).reduce((n,x)=>n|(1<<Number(x.dataset.habitDay)),0);if(!mask){msg('Select at least one day.');return null;}return {id,mask,part:q('#pvHabitPart').value};}
function taskArgs(id){return {id,scheduled:q('#pvScheduled').value,deadline:q('#pvDeadline').value,repeat:q('#pvRepeat').value};}
function goalCreate(group){
 modal('Add '+(group==='daily'?'daily habit':group==='weekly'?'weekly responsibility':'objective'),'<label for="gname">Name</label><input id="gname" maxlength="200" required placeholder="Write a goal or responsibility" autocomplete="off"><details class="pv-options"><summary>Schedule and options</summary>'+(group==='daily'?weekdayFields(null):cadenceFields(null))+'</details>','Add',()=>{let habit=group==='daily',args=habit?habitArgs(''):taskArgs('');if(!args)return false;let name=q('#gname').value.trim();if(!name)return false;let id=native('goal',{name,period:habit?'Daily':group==='weekly'?'Weekly':'Year',from:'',through:habit?'':args.deadline});args.id=id;native(habit?'habitSchedule':'taskOptions',args);refresh();});q('#gname').focus();
}
const originalEdit=editGoal;
editGoal=function(g){
 if(areas.includes(g.id)||(!g.task&&g.period!=='Daily'))return originalEdit(g);
 const habit=g.period==='Daily';
 modal(habit?'Edit habit':'Edit responsibility','<label>Name<input id="pvGoalName" required maxlength="200" value="'+E(g.name)+'"></label>'+(habit?weekdayFields(g):cadenceFields(g))+'<p class="meta">Changes apply from today. Past completion records remain.</p>','Save',()=>{let args=habit?habitArgs(g.id):taskArgs(g.id);if(!args)return false;let name=q('#pvGoalName').value.trim();if(!name)return false;native('goalEdit',habit?{id:g.id,name,start:g.start,end:g.end}:{id:g.id,name,deadline:args.deadline});native(habit?'habitSchedule':'taskOptions',args);refresh();});
};
newGoal=goalCreate;
function prioritize(){
 let s=state,extras=s.extras||{},lookup=new Map((extras.schedule||[]).map(x=>[x.goal,x.day])),order=new Map((extras.order||[]).map(x=>[x.goal,x.position])),repeats=new Map((extras.repeat||[]).map(x=>[x.goal,x.cadence]));
 const isDue=g=>g.active&&!g.done&&g.task&&((g.deadline&&g.deadline<=s.today)||(lookup.get(g.id)&&lookup.get(g.id)<=s.today));
 let due=(s.goals||[]).filter(isDue);
 const reminders=(s.notes||[]).filter(n=>n.alarm&&dayOfTime(n.alarm)===s.today);
 if(due.length||reminders.length){
   let section=el('section','pv-priority');let head=el('div','pv-priority-head');head.append(el('h2','','Today’s priorities'),el('small','',''+(due.length+reminders.length)+' due'));section.append(head);
   for(let g of due){let row=el('div','pv-priority-row');let mark=button('pv-priority-check','',()=>{if(native('mark',{id:g.id,day:s.today,done:true}))refresh();});mark.setAttribute('aria-label','Complete '+g.name);row.append(mark,el('span','',g.name),el('small','',g.deadline&&g.deadline<s.today?'Overdue':'Today'));section.append(row);}
   for(let n of reminders){let row=button('pv-priority-reminder',n.title+' · '+new Date(n.alarm).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'}),()=>openNote(n.id,n.folder));section.append(row);}
   q('.four-section')?.after(section);
 }
 for(let section of qa('.goal-group')){
   const list=q('.bubbles',section);if(!list)continue;
   let tiles=qa('.goal-tile',list),goal=t=>s.goals.find(g=>g.id===t.dataset.menuGoal);
   let rank=t=>order.has(t.dataset.menuGoal)?Number(order.get(t.dataset.menuGoal)):100000+tiles.indexOf(t);
   tiles.sort((a,b)=>{let ga=goal(a),gb=goal(b);if(ga?.period==='Daily'){let parts={Morning:0,Anytime:1,Evening:2},diff=(parts[ga.habitPart]??1)-(parts[gb.habitPart]??1);if(diff)return diff;}return rank(a)-rank(b);});
   list.replaceChildren(...tiles);
   let prev=null;
   for(let tile of tiles){
     let g=goal(tile);if(!g)continue;const part=g.habitPart||'Anytime';
     if(g.period==='Daily'&&tiles.some(t=>goal(t)?.habitPart!=='Anytime')&&part!==prev){tile.before(el('div','pv-habit-label',part));prev=part;}
     const detail=q('.goal-name',tile),scheduled=lookup.get(g.id),repeat=repeats.get(g.id);
     if(detail&&g.task&&!g.done){let labels=[];if(scheduled)labels.push('Scheduled '+pretty(scheduled,{month:'short',day:'numeric'}));if(repeat&&repeat!=='none')labels.push('Repeats '+repeat);if(labels.length)detail.append(el('small','pv-task-meta',labels.join(' · ')));if(g.deadline&&g.deadline<s.today)tile.classList.add('pv-overdue');}
     if(tiles.length>1){const handle=button('pv-drag','⋮⋮',()=>{});handle.setAttribute('aria-label','Reorder '+g.name);tile.append(handle);let dragging=false,start=0;
       handle.onpointerdown=e=>{e.stopPropagation();handle.setPointerCapture(e.pointerId);start=e.clientY;dragging=true;tile.classList.add('pv-dragging');};
       handle.onpointermove=e=>{if(!dragging||Math.abs(e.clientY-start)<12)return;let under=document.elementFromPoint(e.clientX,e.clientY)?.closest('.goal-tile');if(under&&under!==tile&&under.parentElement===list){let box=under.getBoundingClientRect();if(e.clientY<box.top+box.height/2)under.before(tile);else under.after(tile);}};
       handle.onpointerup=handle.onpointercancel=e=>{if(!dragging)return;dragging=false;tile.classList.remove('pv-dragging');let ids=qa('.goal-tile',list).map(t=>t.dataset.menuGoal);native('taskOrder',{ids});refresh();};
       handle.onkeydown=e=>{if(!['ArrowUp','ArrowDown'].includes(e.key))return;e.preventDefault();let all=qa('.goal-tile',list),k=all.indexOf(tile),next=all[k+(e.key==='ArrowUp'?-1:1)];if(!next)return;if(e.key==='ArrowUp')next.before(tile);else next.after(tile);native('taskOrder',{ids:qa('.goal-tile',list).map(t=>t.dataset.menuGoal)});refresh();};
     }
   }
 }
 let scheduledHeading=qa('h2.section',screen).find(h=>h.textContent.includes('Scheduled and past goals'));
 if(scheduledHeading){let d=el('details','pv-scheduled');d.append(el('summary','','Scheduled and inactive goals'));let next=scheduledHeading.nextElementSibling;while(next&&next.matches('[data-menu-goal]')){let n=next.nextElementSibling;d.append(next);next=n;}scheduledHeading.replaceWith(d);}
}
const originalModule=modulePage;
modulePage=function(){originalModule();let timer=q('.module-time');if(timer&&moduleDay===state.today){let b=button('pv-module-hour','+1 hour',()=>addTime(moduleArea,60));timer.append(b);}};
if(typeof render!=='function'||typeof timeEntry!=='function'||typeof api!=='function')throw Error('Preview baseline not compatible');
const originalRender=render;timeEntry=showDial;render=function(){originalRender();if(view==='today'){adornToday();prioritize();}adornOther();banner();};setInterval(updateBanner,500);
})();