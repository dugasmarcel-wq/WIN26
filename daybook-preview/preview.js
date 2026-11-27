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
let paused=null;
function showDial(id){
 let s=stateNow(),g=(s.goals||[]).find(x=>x.id===id);if(!g||!g.timed)return;
 const own=s.timer?.goal===id,other=s.timer?.goal&&!own;
 modal(names[areas.indexOf(id)],'<div class="pv-time-head"><span>'+E(g.name)+'</span><strong>'+E(fmt(g.seconds))+' <small>/ 1h today</small></strong></div>'+
 '<div class="pv-clock-wrap"><svg id="pvDial" class="pv-clock" viewBox="0 0 240 240" role="slider" tabindex="0" aria-label="Minutes to add" aria-valuemin="1" aria-valuemax="1440"><circle cx="120" cy="120" r="100" class="pv-dial-track"/><circle id="pvArc" cx="120" cy="120" r="100" class="pv-dial-arc" transform="rotate(-90 120 120)"/><g id="pvTicks"></g><circle id="pvKnob" r="9" class="pv-dial-knob"/><text id="pvClockValue" x="120" y="121" text-anchor="middle" class="pv-clock-value"></text><text x="120" y="142" text-anchor="middle" class="pv-clock-sub">SWIPE TO SET</text></svg></div>'+
 '<div class="pv-adjust"><button type="button" id="pvLess">− 1h</button><label>Minutes<input id="pvMinutes" type="number" min="1" max="1440" required value="30"></label><button type="button" id="pvMore">+ 1h</button></div>'+
 '<button type="button" class="pv-hour" id="pvHour">Log 1 hour now</button><div class="pv-stop"><div><small>STOPWATCH</small><strong id="pvElapsed">00:00:00</strong></div><button type="button" id="pvTimer" '+(other?'disabled':'')+'>'+(own?'Pause':paused===id?'Resume':'Start')+'</button>'+(own?'<button type="button" id="pvFinish">Finish</button>':'')+'</div>'+
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
 q('#pvTimer').onclick=()=>{if(own){native('methodStop',{id});paused=id;}else{native('methodStart',{id,day:s.today});paused=null;}dialog.close();refresh();showDial(id);};
 if(own)q('#pvFinish').onclick=()=>{native('methodStop',{id});paused=null;dialog.close();refresh();};
 function clock(){let n=q('#pvElapsed');if(!n||!dialog.open)return;let v=own?Math.max(0,Math.floor((Date.now()-s.timer.started)/1000)):0;n.textContent=[Math.floor(v/3600),Math.floor(v/60)%60,v%60].map(x=>String(x).padStart(2,'0')).join(':');}
 update(total);clock();if(own){let t=setInterval(()=>{if(!q('#pvElapsed')||!dialog.open)clearInterval(t);else clock();},500);}
}
function adornToday(){
 const grid=q('.four-grid');if(!grid)return;
 let s=stateNow(),gs=currentGoals(s);grid.classList.add('pv-four');grid.replaceChildren();
 for(let i=0;i<areas.length;i++){let id=areas[i],g=gs.find(x=>x.id===id);if(!g)continue;
 let cell=el('div','pv-area'),ring=button('pv-ring','',()=>showDial(id));cell.style.setProperty('--pv-area',col(id));ring.setAttribute('aria-label','Log '+names[i]+' time, '+fmt(g.seconds)+' recorded');let fraction=Math.min(1,(g.seconds||0)/3600);
 ring.innerHTML='<svg viewBox="0 0 88 88" aria-hidden="true"><circle cx="44" cy="44" r="36" class="pv-track"/><circle cx="44" cy="44" r="36" class="pv-progress" transform="rotate(-90 44 44)" stroke-dasharray="'+(fraction*226.195)+' 226.195"/></svg><span class="pv-ring-label">'+E(fmt(g.seconds))+'</span>';
 cell.append(ring,button('pv-area-name',names[i],()=>showGoal(id)));if(g.seconds>3600)cell.append(el('small','pv-extra','+'+fmt(g.seconds-3600)));grid.append(cell);}
 let head=q('.method-head');if(head){let p=q('span',head);if(p)p.textContent=fmt(gs.reduce((a,g)=>a+(g.seconds||0),0))+' / 4h';}
 let sec=q('.four-section');if(sec&&!q('.pv-balanced',sec)){let n=gs.reduce((a,g)=>a+Math.min(3600,g.seconds||0),0);sec.append(el('div','pv-balanced','Balanced '+fmt(n)+' of 4h · '+gs.filter(g=>g.seconds>=3600).length+'/4 targets'));}
 for(let section of qa('.goal-group')){section.classList.add('pv-list');for(let tile of qa('.goal-tile',section)){let b=q('.goal-check',tile);if(b)tile.classList.toggle('pv-done',b.getAttribute('aria-pressed')==='true');}}
 let pad=q('.daily-pad');if(pad){let h=q('h2',pad);if(h)h.textContent=new Date(s.today+'T12:00:00').toLocaleDateString(undefined,{weekday:'long'})+'’s journal';let text=q('textarea',pad);if(text){text.rows=4;text.classList.add('pv-paper');}}
 if(!q('#pvReview')){let b=button('pv-review','Weekly review',review);b.id='pvReview';if(pad)pad.after(b);else screen.append(b);}
}
function review(){
 let s=stateNow(),d=s.today,weekday=(new Date(d+'T12:00:00Z').getUTCDay()+6)%7,from=dayOffset(d,-weekday),until=dayOffset(d,1),stats=native('investmentStats',{from,until}),values=stats.areas||{},actual=areas.reduce((n,k)=>n+(values[k]||0),0);
 let balanced=0,habits=0,done=0,tracked=false;for(let i=0;i<=weekday;i++){let day=dayOffset(from,i),rows=native('dayGoals',{day});if(!Array.isArray(rows))continue;tracked=true;for(let g of rows){if(areas.includes(g.id))balanced+=Math.min(3600,g.seconds||0);else if(g.period==='Daily'&&g.active!==false&&(day!==d||g.done)){habits++;if(g.done)done++;}}}
 let pending=(s.goals||[]).filter(g=>g.active&&!g.done&&!areas.includes(g.id)&&g.period!=='Daily');
 modal('Weekly review','<p class="meta">'+E(from)+' – '+E(d)+' · week to date</p><div class="pv-review-total"><strong>'+E(fmt(actual))+'</strong><span>Actual investment</span></div>'+areas.map((k,i)=>'<div class="pv-review-line"><span>'+names[i]+'</span><b>'+E(fmt(values[k]))+'</b><progress max="'+((weekday+1)*3600)+'" value="'+Math.min((weekday+1)*3600,values[k]||0)+'"></progress></div>').join('')+(tracked?'<p>Balanced investment: <b>'+E(fmt(balanced))+'</b></p><p>Daily habits recorded: <b>'+done+' / '+habits+'</b></p>':'')+'<h3>Outstanding responsibilities</h3>'+(pending.length?pending.map(g=>'<p class="pv-review-task">'+E(g.name)+(g.deadline?' · '+E(g.deadline):'')+'</p>').join(''):'<p>No outstanding responsibilities.</p>'),'Close',()=>true);
}
function searchNotes(){
 let s=stateNow();modal('Find in Notebook','<label for="pvSearch">Search notes and journals</label><input type="search" id="pvSearch" placeholder="Search titles or writing"><div id="pvResults"></div>','Close',()=>true);
 const f=q('#pvSearch'),out=q('#pvResults');let timeout;f.oninput=()=>{clearTimeout(timeout);timeout=setTimeout(()=>{let term=f.value.trim().toLowerCase();out.replaceChildren();if(!term)return;let hits=0;for(let n of s.notes||[]){if(hits>=40)break;let detail=native('note',{id:n.id});if(!detail||!((detail.title||'')+' '+(detail.body||'')).toLowerCase().includes(term))continue;hits++;out.append(button('pv-search-result',(detail.title||'Untitled')+' · '+(detail.written||''),()=>{dialog.close();openNote(n.id,detail.folder);}));}if(!hits)out.append(el('p','meta','No matching notes.'));},250);};f.focus();
}
function adornOther(){if(view==='notes'&&!q('#pvSearchButton')){let b=button('pv-search','Search notebook',searchNotes);b.id='pvSearchButton';let h=q('h1',screen);if(h)h.after(b);else screen.prepend(b);}
 if(view==='settings'){let b=q('#updates');if(b){let row=b.closest('.setting')||b.parentElement;row.hidden=true;}let h=q('h1',screen);if(h)h.after(el('p','pv-preview-label','Daybook Preview · independent installation'));}
}
function banner(){
 let s=stateNow(),t=s.timer||{},b=q('#pvLive');if(!t.goal){b?.remove();return;}if(!b){b=el('div','pv-live');b.id='pvLive';document.body.append(b);}b.replaceChildren(el('span','','Running · '+(names[areas.indexOf(t.goal)]||'Timer')),el('strong','pv-live-time',''),button('pv-live-stop','Stop & save',()=>{native('methodStop',{id:t.goal});paused=null;refresh();}));b.dataset.started=t.started;updateBanner();
}
function updateBanner(){let b=q('#pvLive');if(!b)return;let v=Math.max(0,Math.floor((Date.now()-Number(b.dataset.started))/1000));q('.pv-live-time',b).textContent=[Math.floor(v/3600),Math.floor(v/60)%60,v%60].map(x=>String(x).padStart(2,'0')).join(':');}
if(typeof render!=='function'||typeof timeEntry!=='function'||typeof api!=='function')throw Error('Preview baseline not compatible');
const originalRender=render;timeEntry=showDial;render=function(){originalRender();if(view==='today')adornToday();adornOther();banner();};setInterval(updateBanner,500);
})();