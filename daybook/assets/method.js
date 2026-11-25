'use strict';
const areaIds=['four-spiritual','four-financial','four-physical','four-mental'];
const colorDefaults={
 'four-spiritual':'#79569b','four-financial':'#32725c','four-physical':'#a24d38','four-mental':'#326998',
 long:'#775286',weekly:'#396b94',daily:'#28755c',today:'#245748',notebook:'#895d42',calendar:'#486191'
};
function colorFor(key){return (state.colors||[]).find(c=>c.target===key)?.value||colorDefaults[key]||'#406e60';}
function applyColors(){
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
 '<button type="button" class="sheet-delete" id="menuDeleteGoal">Delete goal</button>','',()=>false);
 dialog.className='note-sheet';
 dialog.querySelector('.sheet-close').onclick=()=>dialog.close();
 $('#menuEditGoal').onclick=()=>{dialog.close();editGoal(goal);};
 $('#menuHistory').onclick=()=>{dialog.close();showGoal(id);};
 $('#menuDeleteGoal').onclick=()=>{
   dialog.close();
   modal('Delete this goal?','<p><b>'+esc(goal.name)+'</b></p><p>Past check-ins stay in your journal and tracker.</p>','Delete goal',()=>{
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
