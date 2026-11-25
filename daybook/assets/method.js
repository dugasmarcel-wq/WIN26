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
 const through=g.end==='9999-12-31'?'':dayAt(utc(g.end)-86400000);
 modal(focusNames[g.id]||'Edit goal','<label for="aim">Goal text</label><input required maxlength="200" id="aim" value="'+esc(g.name)+'">'+dateFields(g.start,through,g.created)+'<p class="meta">Changes apply from today onward.</p>','Save changes',()=>{
   const start=$('#validFrom').value||g.created,end=$('#validThrough').value;
   if(end&&end<start){$('#validThrough').setCustomValidity('Choose the same day or a later one.');$('#validThrough').reportValidity();return false;}
   api('goalEdit',{id:g.id,name:$('#aim').value,start,end:end?dayAt(utc(end)+86400000):'9999-12-31'});render();
 });
 $('#validThrough').oninput=()=>$('#validThrough').setCustomValidity('');
 $('#validFrom').oninput=()=>$('#validThrough').setCustomValidity('');
}
function reflectionList(d){
 const rows=api('reflections',{day:d}).filter(r=>r.body.trim());
 if(!rows.length)return '';
 return '<section class="reflections"><h3>Method of 4 journal</h3>'+rows.map(r=>'<article data-color-key="'+esc(r.goal)+'"><b>'+esc(focusNames[r.goal])+'</b><p>'+esc(r.body)+'</p></article>').join('')+'</section>';
}
