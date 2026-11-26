'use strict';
function trackerCaption(n){
 return n.done+' done · '+n.missed+' missed'+(n.open?' · '+n.open+' open':'');
}
function calendarScore(stats,period){
 const rate=stats.percent<0?'—':stats.percent+'%';
 return '<div class="tracker-score"><strong id="completionRate">'+rate+'</strong><div><b>'+esc(period)+' completion</b><span>'+trackerCaption(stats)+'</span></div></div>';
}
function trackerLegend(){
 return '<div class="tracker-legend"><span class="done">Done</span><span class="partial">Partial</span><span class="missed">Missed</span><span class="open">Open</span><span class="scheduled">Scheduled</span><span class="none">No goals due</span></div>'+
 '<details class="tracker-help"><summary>How it’s counted</summary><p>Completion is done ÷ (done + missed). Open check-ins are left out. Weekly goals count on Sunday, or on their last valid day if they end earlier.</p></details>';
}
function jumpToTracker(){calDay=api('state').today;scale='month';returnCal=false;go('calendar');}
