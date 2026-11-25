'use strict';
let trackerMode='month', trackerMonth='', pickedDay='';
function shiftMonth(iso,months) {
 const d=new Date(iso.slice(0,7)+'-01T12:00:00Z');
 d.setUTCMonth(d.getUTCMonth()+months);
 return d.toISOString().slice(0,10);
}
function trackerCaption(n){
 return n.done+' done · '+n.missed+' missed'+(n.open?' · '+n.open+' open':'');
}
function showTracker(){
 const host=$('#trackerPanel');
 const current=state.today.slice(0,7)+'-01';
 const month=trackerMonth||current,year=trackerMode==='year';
 const start=year?month.slice(0,4)+'-01-01':month;
 const data=api('tracker',{start,year});
 const rate=data.percent<0?'—':data.percent+'%';
 const earliest=state.goals.reduce((first,g)=>g.start<first?g.start:first,state.today);
 const title=pretty(start,year?{year:'numeric'}:{month:'long',year:'numeric'});
 const modeButton=(name,label)=>'<button type="button" data-mode="'+name+'" aria-pressed="'+(trackerMode===name)+'">'+label+'</button>';
 host.innerHTML='<div class="tracker-top"><h2>Completion tracker</h2><div class="tracker-switch">'+modeButton('month','Month')+modeButton('year','Year')+'</div></div>'+
 '<div class="tracker-period"><button id="earlier" aria-label="Previous '+trackerMode+'">‹</button><b>'+esc(title)+'</b><button id="later" aria-label="Next '+trackerMode+'">›</button></div>'+
 '<div class="tracker-score"><strong id="completionRate">'+rate+'</strong><div><b>Completion</b><span>'+trackerCaption(data)+'</span></div></div>'+
 (year?'<div class="year-months" id="trackerGrid"></div>':'<div class="month-days" id="trackerGrid"></div><div class="tracker-legend"><span class="done">Done</span><span class="partial">Partial</span><span class="missed">Missed</span><span class="open">Open</span><span class="none">No goals due</span></div><p id="dayCounts" aria-live="polite"></p>')+
 '<details class="tracker-help"><summary>How it’s counted</summary><p>Completion is done ÷ (done + missed). Open check-ins are left out. Daily and long-term goals count each day. Each weekly goal counts once on Sunday.</p></details>';
 const periodKey=(date)=>date.slice(0,year?4:7);
 $('#earlier').disabled=periodKey(start)<=periodKey(earliest);
 $('#later').disabled=periodKey(start)>=periodKey(current);
 const move=n=>{const next=shiftMonth(month,n*(year?12:1));trackerMonth=next>current?current:next;pickedDay='';showTracker();};
 $('#earlier').onclick=()=>move(-1);$('#later').onclick=()=>move(1);
 host.querySelectorAll('[data-mode]').forEach(b=>b.onclick=()=>{trackerMode=b.dataset.mode;showTracker();});
 const grid=$('#trackerGrid');
 if(year){
   grid.innerHTML=data.buckets.map(n=>'<button data-month="'+n.date+'" '+(n.date>current?'disabled':'')+' aria-label="'+esc(pretty(n.date,{month:'long'})+', '+trackerCaption(n))+'"><span>'+esc(pretty(n.date,{month:'short'}))+'</span><b>'+(n.percent<0?'—':n.percent+'%')+'</b></button>').join('');
   grid.querySelectorAll('[data-month]').forEach(b=>b.onclick=()=>{trackerMode='month';trackerMonth=b.dataset.month;pickedDay='';showTracker();});
   return;
 }
 const blanks=(new Date(utc(start)).getUTCDay()+6)%7;
 grid.innerHTML=['M','T','W','T','F','S','S'].map(x=>'<small aria-hidden="true">'+x+'</small>').join('')+'<i aria-hidden="true"></i>'.repeat(blanks)+
 data.buckets.map(n=>'<button class="track-day '+n.status+'" data-date="'+n.date+'" '+(n.date>state.today?'disabled':'')+' aria-label="'+esc(pretty(n.date,{dateStyle:'long'})+', '+trackerCaption(n))+'"><i aria-hidden="true"></i><span>'+Number(n.date.slice(8))+'</span></button>').join('');
 const initial=data.buckets.find(n=>n.date===pickedDay)||data.buckets.find(n=>n.date===state.today)||data.buckets.filter(n=>n.done+n.missed>0).pop()||data.buckets[0];
 function select(n){
   pickedDay=n.date;
   grid.querySelectorAll('[data-date]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.date===n.date)));
   $('#dayCounts').textContent=pretty(n.date,{month:'short',day:'numeric'})+' · '+(n.done+n.missed+n.open?trackerCaption(n):'No goals due');
 }
 select(initial);
 grid.querySelectorAll('[data-date]').forEach(b=>b.onclick=()=>select(data.buckets.find(n=>n.date===b.dataset.date)));
}
function jumpToTracker(){
 const panel=$('#trackerPanel');if(panel){panel.scrollIntoView({block:'start'});panel.focus({preventScroll:true});}
}
