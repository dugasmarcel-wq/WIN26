'use strict';
// Targeted additions to the released 1.13 views. Existing record IDs and bridge remain unchanged.
(function () {
  const originalJournal = journalPage;
  const originalStatistics = statisticsPage;
  const originalTimeEntry = timeEntry;

  function durationFromAngle(x,y) {
    return ((Math.atan2(x,-y)/(2*Math.PI)*60)+60)%60;
  }
  function durationDelta(previous,next) {
    return ((next-previous+90)%60)-30;
  }
  function journalSearch() {
    let month=(journalDay||state.today).slice(0,7)+'-01';
    let mode='keyword';
    const notes=state.notes.filter(n=>!hiddenModuleNote(n));
    const dated=new Set(notes.filter(n=>journalDate(n)&&api('note',{id:n.id})?.body?.trim()).map(journalDate));
    modal('Find Journal Entry',
      '<div class="notebook-tabs"><button type="button" id="findKeyword" aria-pressed="true">Keyword</button><button type="button" id="findCalendar" aria-pressed="false">Calendar</button></div><div id="findContents"></div>',
      'Close',()=>true);
    const contents=$('#findContents');
    function select(n) {
      dialog.close();
      // Open the exact stored note, including older spiritual entries.
      openNote(n.id,n.folder);
    }
    function keyword() {
      contents.innerHTML='<label for="findQuery">Word or phrase</label><input type="search" id="findQuery" autocomplete="off"><div id="findResults" class="find-results"></div>';
      const query=$('#findQuery'), results=$('#findResults');
      let cached=null;
      function paint() {
        const q=query.value.trim().toLocaleLowerCase();
        if(!q){results.textContent='Enter a word or phrase.';return;}
        if(!cached)cached=notes.map(n=>({n,body:api('note',{id:n.id})?.body||''}));
        const hits=cached.filter(({n,body})=>(n.title||'').toLocaleLowerCase().includes(q)||body.toLocaleLowerCase().includes(q));
        hits.sort((a,b)=>(journalDate(b.n)||b.n.written||'').localeCompare(journalDate(a.n)||a.n.written||''));
        results.replaceChildren();
        if(!hits.length){results.textContent='No matching entries.';return;}
        for(const {n,body} of hits) {
          const b=document.createElement('button');b.type='button';b.className='item card find-hit';
          const title=document.createElement('b');title.textContent=n.title||'Untitled note';
          const date=document.createElement('small');date.textContent=journalDate(n)||n.written||'';
          const text=document.createElement('span'),at=body.toLocaleLowerCase().indexOf(q);
          const start=at<0?0:Math.max(0,at-45),end=Math.min(body.length,(at<0?0:at)+q.length+100);
          text.textContent=(start?'…':'')+body.slice(start,end)+(end<body.length?'…':'');
          b.append(title,date,text);b.onclick=()=>select(n);results.append(b);
        }
      }
      query.oninput=paint;paint();query.focus();
    }
    function calendar() {
      const y=Number(month.slice(0,4)),m=Number(month.slice(5,7))-1;
      const last=new Date(Date.UTC(y,m+1,0)).getUTCDate(),offset=(new Date(utc(month)).getUTCDay()+6)%7;
      contents.innerHTML='<div class="cal-controls"><button type="button" id="findPrev" aria-label="Previous month">‹</button><input type="month" id="findMonth" aria-label="Month and year" value="'+esc(month.slice(0,7))+'"><button type="button" id="findNext" aria-label="Next month">›</button></div><div class="account-grid find-grid">'+
        ['M','T','W','T','F','S','S'].map(x=>'<small>'+x+'</small>').join('')+'<span></span>'.repeat(offset)+
        Array.from({length:last},(_,i)=>{const d=moduleOffset(month,i);return '<button type="button" class="account-day '+(dated.has(d)?'has-journal':'')+'" data-find-date="'+d+'" aria-label="'+esc(d+(dated.has(d)?', journal entry':''))+'">'+(i+1)+'</button>';}).join('')+'</div><p class="meta">Highlighted dates contain written journal entries.</p>';
      function move(n){month=dayAt(Date.UTC(y,m+n,1));calendar();}
      $('#findPrev').onclick=()=>move(-1);$('#findNext').onclick=()=>move(1);
      $('#findMonth').onchange=e=>{if(e.target.value){month=e.target.value+'-01';calendar();}};
      contents.querySelectorAll('[data-find-date]').forEach(b=>b.onclick=()=>{dialog.close();openJournal(b.dataset.findDate);});
    }
    function change(next){mode=next;$('#findKeyword').setAttribute('aria-pressed',String(mode==='keyword'));$('#findCalendar').setAttribute('aria-pressed',String(mode==='calendar'));if(mode==='keyword')keyword();else calendar();}
    $('#findKeyword').onclick=()=>change('keyword');$('#findCalendar').onclick=()=>change('calendar');change(mode);
  }
  journalPage=function(){
    originalJournal();
    const b=document.createElement('button');b.id='findJournal';b.className='find-journal';b.textContent='Find Journal Entry';b.onclick=journalSearch;screen.append(b);
  };
  timeEntry=function(id) {
    originalTimeEntry(id);
    const input=$('#manualMinutes'),quick=dialog.querySelector('.quick-times');
    if(!input||!quick)return;
    const plus=quick.querySelector('[data-add-minutes="60"]');
    if(!plus)return;
    quick.replaceChildren(plus);plus.textContent='+1 Hour';
    const dial=document.createElement('div');dial.className='duration-dial';dial.setAttribute('role','slider');dial.setAttribute('tabindex','0');dial.setAttribute('aria-label','Activity duration in minutes');dial.setAttribute('aria-valuemin','1');dial.setAttribute('aria-valuemax','1440');
    const face=document.createElement('span');face.className='duration-face';const hand=document.createElement('span');hand.className='duration-hand';const label=document.createElement('b');label.className='duration-label';
    dial.append(face,hand,label);
    for(const [n,c] of [[0,'top'],[15,'right'],[30,'bottom'],[45,'left']]){const t=document.createElement('small');t.className='duration-tick '+c;t.textContent=String(n);dial.append(t);}
    input.before(dial);
    function update(n) {
      n=Math.min(1440,Math.max(1,Math.round(Number(n)||1)));
      input.value=n;label.textContent=timeLabel(n*60);hand.style.transform='rotate('+(n*6)+'deg)';
      dial.setAttribute('aria-valuenow',String(n));dial.setAttribute('aria-valuetext',timeLabel(n*60));return n;
    }
    function angle(e){const r=dial.getBoundingClientRect();return durationFromAngle(e.clientX-r.left-r.width/2,e.clientY-r.top-r.height/2);}
    let pointer=null,previous=0,total=0;
    dial.onpointerdown=e=>{if(pointer!==null||e.button!==0)return;const r=dial.getBoundingClientRect();if(Math.hypot(e.clientX-r.left-r.width/2,e.clientY-r.top-r.height/2)<r.width*.18)return;pointer=e.pointerId;dial.setPointerCapture(pointer);previous=angle(e);const current=Number(input.value)||1;total=update(current+durationDelta(current%60,previous));e.preventDefault();};
    dial.onpointermove=e=>{if(e.pointerId!==pointer)return;const next=angle(e);total=Math.max(1,Math.min(1440,total+durationDelta(previous,next)));previous=next;update(total);};
    dial.onpointerup=dial.onpointercancel=dial.onlostpointercapture=e=>{if(e.pointerId===pointer)pointer=null;};
    dial.onkeydown=e=>{const increments={ArrowUp:1,ArrowRight:1,ArrowDown:-1,ArrowLeft:-1,PageUp:60,PageDown:-60};if(e.key in increments){e.preventDefault();update(Number(input.value)+increments[e.key]);}};
    input.oninput=()=>{const n=Number(input.value);if(Number.isInteger(n)&&n>=1&&n<=1440){label.textContent=timeLabel(n*60);hand.style.transform='rotate('+(n*6)+'deg)';dial.setAttribute('aria-valuenow',String(n));dial.setAttribute('aria-valuetext',timeLabel(n*60));}};
    update(input.value);
  };
  function weeklyReview(initial) {
    let week=mondayOf(initial>state.today?state.today:initial);
    modal('Weekly review','<div class="cal-controls"><button type="button" id="reviewPrev" aria-label="Previous week">‹</button><strong id="reviewWeek"></strong><button type="button" id="reviewNext" aria-label="Next week">›</button></div><div id="reviewBody"></div>','Close',()=>true);
    function paint() {
      const end=moduleOffset(week,7),last=moduleOffset(week,6),asOf=last<state.today?last:state.today;
      $('#reviewWeek').textContent=pretty(week,{month:'short',day:'numeric'})+' – '+pretty(last,{month:'short',day:'numeric',year:'numeric'});
      $('#reviewNext').disabled=week>=mondayOf(state.today);
      const stats=api('investmentStats',{from:week,until:end}),rows=api('dayGoals',{day:asOf});
      const doneRows=rows.filter(g=>g.completedOn>=week&&g.completedOn<end);
      const outstanding=rows.filter(g=>g.period==='Weekly'&&g.task&&g.active&&!g.done);
      const tasks=doneRows.filter(g=>g.period==='Weekly'),objectives=doneRows.filter(g=>g.period!=='Weekly'&&g.task);
      const name=g=>esc(g.completedName||g.name);
      const list=(items,label,completed=false)=>'<h3>'+label+'</h3>'+(items.length?'<ul>'+items.map(g=>'<li>'+(completed?name(g):esc(g.name))+'</li>').join('')+'</ul>':'<p class="meta">None</p>');
      const key='module-data-week-review-'+week;
      const note=api('note',{id:key});
      $('#reviewBody').innerHTML='<section><h3>Time investment</h3>'+areaIds.map(id=>'<div class="review-row" data-color-key="'+id+'"><span>'+esc(focusNames[id])+'</span><b>'+esc(timeLabel(stats.areas[id]))+'</b></div>').join('')+'<div class="review-row"><strong>Total recorded</strong><b>'+esc(timeLabel(stats.total))+'</b></div><p class="meta">Applicable target to date: '+esc(timeLabel(stats.target))+'</p></section><section><h3>Daily habits</h3><p>'+stats.dailyDone+' completed · '+stats.dailyMissed+' missed · '+stats.dailyOpen+' pending</p></section><section>'+list(tasks,'Weekly responsibilities completed',true)+list(outstanding,'Weekly responsibilities outstanding')+list(objectives,'Objectives achieved',true)+'</section><label for="weeklyReflection">Weekly reflection (optional)</label><textarea id="weeklyReflection" rows="4" placeholder="Write here…">'+esc(note?.body||'')+'</textarea>';
      $('#weeklyReflection').oninput=e=>{if(e.target.value.trim()||api('note',{id:key}))api('save',{id:key,folder:'daily',title:'Weekly reflection '+week,body:e.target.value});};
      applyColors();
    }
    $('#reviewPrev').onclick=()=>{week=moduleOffset(week,-7);paint();};$('#reviewNext').onclick=()=>{week=moduleOffset(week,7);paint();};paint();
  }
  statisticsPage=function(){originalStatistics();const b=document.createElement('button');b.className='find-journal';b.id='weeklyReview';b.textContent='Weekly review';b.onclick=()=>weeklyReview(statsDay||state.today);screen.append(b);};
})();