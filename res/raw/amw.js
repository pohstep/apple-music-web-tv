(function(){
if(window.__amw&&window.__amw.v===3)return;
var ZOOM=%ZOOM%;
var A=window.__amw={v:3,cur:null,last:null,on:true,lc:{},ps:null};
var SEL='a[href],button,input:not([type=hidden]),select,textarea,summary,[role=button],[role=link],[role=tab],[role=menuitem],[role=option],[role=switch],[role=checkbox],[role=radio],[role=slider],[role=combobox],[role=searchbox],[role=textbox],[contenteditable=true],[tabindex]:not([tabindex="-1"]),[onclick]';

function all(root,out){var l=root.querySelectorAll('*');for(var i=0;i<l.length;i++){var e=l[i];out.push(e);if(e.shadowRoot)all(e.shadowRoot,out);}return out;}
function within(e,p){for(var n=e;n;n=n.parentNode||n.host){if(n===p)return true;}return false;}
function rectOf(e){var r=e.getBoundingClientRect();return {left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height};}
function visible(e,r){
  if(r.width<3||r.height<3)return false;
  var cs=getComputedStyle(e);
  return cs.visibility!=='hidden'&&cs.display!=='none'&&cs.pointerEvents!=='none';
}
function bigForeignFrame(els){
  for(var i=0;i<els.length;i++){var e=els[i];
    if(e.tagName!=='IFRAME')continue;
    var r=e.getBoundingClientRect(); if(r.width<200||r.height<200)continue;
    var src=e.getAttribute('src')||''; if(!src||src==='about:blank')continue;
    if(/idmsa|appleid|iforgot|\/auth/i.test(src))return true;
    try{var u=new URL(src,location.href); if(u.hostname!==location.hostname&&getComputedStyle(e).visibility!=='hidden')return true;}catch(x){}
  }
  return false;
}
function authFrame(els){return bigForeignFrame(els||all(document,[]));}

function cands(els){
  var vw=innerWidth,vh=innerHeight,i,e,modal=null,out=[];
  for(i=0;i<els.length;i++){e=els[i];
    if(e.matches&&e.matches('dialog[open],[aria-modal="true"]')){var mr=e.getBoundingClientRect();if(mr.width>50&&mr.height>50&&getComputedStyle(e).visibility!=='hidden')modal=e;}
  }
  for(i=0;i<els.length;i++){e=els[i];
    if(!e.matches||!e.matches(SEL))continue;
    if(e.disabled||e.getAttribute('aria-disabled')==='true')continue;
    if(e.id==='amw-ring')continue;
    var r=rectOf(e);
    if(r.width<3||r.height<3)continue;
    if(r.bottom<-vh*1.2||r.top>vh*2.2||r.right<-vw*1.2||r.left>vw*2.2)continue;
    if(modal&&!within(e,modal))continue;
    if(!visible(e,r))continue;
    out.push({e:e,r:r});
  }
  return out;
}
function curRect(){
  var c=A.cur;
  if(c&&c.isConnected){var r=rectOf(c);if(r.width>0&&r.height>0)return r;}
  return A.last;
}
function score(c,r,dir){
  var ccx=(c.left+c.right)/2,ccy=(c.top+c.bottom)/2,rcx=(r.left+r.right)/2,rcy=(r.top+r.bottom)/2;
  var prim,gap,diff,lim;
  if(dir==='right'){ if(rcx<=ccx+2||r.left<=c.left+1)return -1; prim=Math.max(0,r.left-c.right); gap=Math.max(0,Math.max(r.top-c.bottom,c.top-r.bottom)); diff=Math.abs(rcy-ccy); lim=innerHeight*0.5;}
  else if(dir==='left'){ if(rcx>=ccx-2||r.right>=c.right-1)return -1; prim=Math.max(0,c.left-r.right); gap=Math.max(0,Math.max(r.top-c.bottom,c.top-r.bottom)); diff=Math.abs(rcy-ccy); lim=innerHeight*0.5;}
  else if(dir==='down'){ if(rcy<=ccy+2||r.top<=c.top+1)return -1; prim=Math.max(0,r.top-c.bottom); gap=Math.max(0,Math.max(r.left-c.right,c.left-r.right)); diff=Math.abs(rcx-ccx); lim=innerWidth*0.6;}
  else { if(rcy>=ccy-2||r.bottom>=c.bottom-1)return -1; prim=Math.max(0,c.top-r.bottom); gap=Math.max(0,Math.max(r.left-c.right,c.left-r.right)); diff=Math.abs(rcx-ccx); lim=innerWidth*0.6;}
  if(gap>lim)return -1;
  return prim+gap*4+diff*0.3;
}

// ---- focus ring + play button ----
var ring=document.createElement('div');ring.id='amw-ring';
ring.style.cssText='position:fixed;pointer-events:none;z-index:2147483647;box-sizing:border-box;display:none;border:4px solid #fff;box-shadow:0 0 0 3px #fa2d48,0 0 0 6px rgba(0,0,0,.55),0 0 24px 10px rgba(250,45,72,.5);';
var btn=document.createElement('button');btn.id='amw-play-btn';btn.setAttribute('aria-label','Play mode');btn.setAttribute('type','button');
btn.textContent='\u266B';
btn.style.cssText='position:fixed;right:18px;bottom:18px;width:52px;height:52px;border-radius:26px;border:2px solid rgba(255,255,255,.4);background:rgba(18,18,22,.72);color:#fff;font-size:26px;line-height:48px;padding:0;margin:0;z-index:2147483646;opacity:.55;cursor:pointer;';
btn.addEventListener('click',function(e){try{AMWNative.togglePlay();}catch(x){}});
var ringFor=null,ringRadius=8;
function tick(){
  requestAnimationFrame(tick);
  var de=document.documentElement; if(!de)return;
  if(!ring.isConnected)de.appendChild(ring);
  if(!btn.isConnected)de.appendChild(btn);
  var c=A.cur;
  if(!A.on||!c||!c.isConnected){ring.style.display='none';return;}
  var r=c.getBoundingClientRect();
  if(r.width<1||r.height<1){ring.style.display='none';return;}
  if(ringFor!==c){ringFor=c;var br=parseFloat(getComputedStyle(c).borderTopLeftRadius)||0;ringRadius=Math.min(Math.max(8,br+6),40);}
  ring.style.display='block';
  ring.style.left=(r.left-6)+'px';ring.style.top=(r.top-6)+'px';
  ring.style.width=(r.width+12)+'px';ring.style.height=(r.height+12)+'px';
  ring.style.borderRadius=ringRadius+'px';
  A.last={left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height};
}
tick();
A.ring=function(on){A.on=!!on;if(!on)ring.style.display='none';return 'ok';};

// ---- scrolling ----
function scrollChain(start){
  var out=[];
  for(var n=start;n;n=n.parentNode||n.host){
    if(n.nodeType!==1)continue;
    var cs=getComputedStyle(n);
    if((/(auto|scroll)/.test(cs.overflowY)&&n.scrollHeight>n.clientHeight+4)||(/(auto|scroll)/.test(cs.overflowX)&&n.scrollWidth>n.clientWidth+4))out.push(n);
  }
  if(document.scrollingElement)out.push(document.scrollingElement);
  return out;
}
function scrollDir(dir,el){
  var start=(el&&el.isConnected)?el:document.elementFromPoint(innerWidth*0.6,innerHeight*0.5);
  var chain=scrollChain(start);
  var dy=dir==='down'?innerHeight*0.6:(dir==='up'?-innerHeight*0.6:0);
  var dx=dir==='right'?innerWidth*0.5:(dir==='left'?-innerWidth*0.5:0);
  for(var i=0;i<chain.length;i++){
    var n=chain[i],b1=n.scrollTop,b2=n.scrollLeft;
    if(dy)n.scrollTop=b1+dy; if(dx)n.scrollLeft=b2+dx;
    if(n.scrollTop!==b1||n.scrollLeft!==b2)return true;
  }
  return false;
}
function ensureVisible(e){
  var r=e.getBoundingClientRect(),vw=innerWidth,vh=innerHeight;
  if(r.top<110||r.bottom>vh-70||r.left<20||r.right>vw-20){
    try{e.scrollIntoView({block:(r.height>vh-220?'start':'center'),inline:'nearest',behavior:'instant'});}catch(x){e.scrollIntoView();}
  }
}
function setCur(e){
  A.cur=e;
  try{e.focus({preventScroll:true});}catch(x){}
  var r=e.getBoundingClientRect();A.last={left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height};
}

A.nav=function(dir){
  try{
    var els=all(document,[]);
    if(authFrame(els))return 'iframe';
    var list=cands(els);
    if(!list.length){
      if(scrollDir(dir,A.cur))return 'scrolled';
      return document.readyState==='complete'?'noel':'wait';
    }
    var c=curRect(),best=null,i,s,bs=1e18;
    if(!c||!A.cur||!A.cur.isConnected){
      var px=innerWidth*0.4,py=innerHeight*0.4;
      for(i=0;i<list.length;i++){var r=list[i].r,cx=(r.left+r.right)/2,cy=(r.top+r.bottom)/2;
        if(cx<0||cy<0||cx>innerWidth||cy>innerHeight)continue;
        s=Math.abs(cx-px)+Math.abs(cy-py)*1.5; if(s<bs){bs=s;best=list[i];}}
      if(!best){if(scrollDir('down',null))return 'scrolled';return 'stuck';}
      setCur(best.e);ensureVisible(best.e);return 'moved';
    }
    for(i=0;i<list.length;i++){
      var it=list[i]; if(it.e===A.cur)continue;
      if(within(A.cur,it.e))continue; // skip ancestors of current
      s=score(c,it.r,dir); if(s<0)continue;
      if(s<bs){bs=s;best=it;}
    }
    if(!best){if(scrollDir(dir,A.cur))return 'scrolled';return 'stuck';}
    setCur(best.e);ensureVisible(best.e);return 'moved';
  }catch(x){return 'err:'+x;}
};

function editable(e){
  var t=e.tagName;
  if(t==='TEXTAREA')return true;
  if(t==='INPUT'){var ty=(e.type||'text').toLowerCase();return !/^(button|submit|reset|checkbox|radio|range|file|image|color)$/.test(ty);}
  return !!e.isContentEditable;
}
function deepHit(x,y){
  var h=document.elementFromPoint(x,y);
  while(h&&h.shadowRoot){var h2=h.shadowRoot.elementFromPoint(x,y);if(!h2||h2===h)break;h=h2;}
  return h;
}
A.act=function(){
  try{
    if(authFrame())return 'iframe';
    var e=A.cur;
    if(!e||!e.isConnected)return 'none';
    try{e.focus({preventScroll:true});}catch(x){}
    var r=e.getBoundingClientRect(),cx=r.left+r.width/2,cy=r.top+r.height/2;
    var inp=editable(e)?1:0;
    if(cx>=0&&cy>=0&&cx<innerWidth&&cy<innerHeight){
      var h=deepHit(cx,cy);
      if(h&&(within(h,e)||within(e,h)))return 'tap:'+cx+':'+cy+':'+innerWidth+':'+inp;
    }
    var o={bubbles:true,cancelable:true,composed:true,view:window,clientX:cx,clientY:cy,button:0};
    try{e.dispatchEvent(new PointerEvent('pointerdown',o));}catch(x){}
    e.dispatchEvent(new MouseEvent('mousedown',o));
    try{e.dispatchEvent(new PointerEvent('pointerup',o));}catch(x){}
    e.dispatchEvent(new MouseEvent('mouseup',o));
    e.dispatchEvent(new MouseEvent('click',o));
    return 'done';
  }catch(x){return 'err:'+x;}
};

// ---- now playing ----
try{
  var ms=navigator.mediaSession;
  if(ms&&ms.setPositionState&&!ms.__amwHook){
    var orig=ms.setPositionState.bind(ms);
    ms.setPositionState=function(s){try{A.ps=s?{d:s.duration,p:s.position,r:s.playbackRate||1,ts:Date.now()}:null;}catch(x){}return orig.apply(ms,arguments);};
    ms.__amwHook=1;
  }
}catch(x){}
function mediaEl(){
  if(A.m&&A.m.isConnected)return A.m;
  var now=Date.now(); if(now-(A.mt||0)<4000)return null; A.mt=now;
  var l=all(document,[]),pick=null;
  for(var i=0;i<l.length;i++){var e=l[i];if(e.tagName==='AUDIO'||e.tagName==='VIDEO'){if(!pick||(!e.paused&&pick.paused)||(e.currentSrc&&!pick.currentSrc))pick=e;}}
  A.m=pick;return pick;
}
function tm(s){
  if(s==null)return NaN; s=String(s).trim(); if(!s)return NaN;
  if(/s$/.test(s))return parseFloat(s);
  var p=s.split(':'),v=0;for(var i=0;i<p.length;i++)v=v*60+parseFloat(p[i]);return v;
}
function parseTTML(tt){
  var doc=new DOMParser().parseFromString(tt,'application/xml');
  var ps=doc.getElementsByTagName('p'),out=[];
  for(var i=0;i<ps.length;i++){
    var b=tm(ps[i].getAttribute('begin')),e=tm(ps[i].getAttribute('end'));
    var t=(ps[i].textContent||'').replace(/\s+/g,' ').trim();
    if(!isFinite(b)||!t)continue;
    out.push([b,isFinite(e)?e:b+4,t]);
  }
  out.sort(function(x,y){return x[0]-y[0];});
  for(var j=0;j<out.length;j++){if(j+1<out.length&&out[j][1]>out[j+1][0])out[j][1]=out[j+1][0];}
  return out;
}
function fetchLyrics(mk,id,sf){
  var key=id,tries=(A.lc[key]&&A.lc[key].tries||0)+1;
  A.lc[key]={pending:1,tries:tries,t:Date.now()};
  var paths=['/v1/catalog/'+sf+'/songs/'+id+'/lyrics','/v1/catalog/'+sf+'/songs/'+id+'/syllable-lyrics'],i=0;
  function next(){
    if(i>=paths.length){A.lc[key]={none:1,tries:tries,t:Date.now()};return;}
    var pr;try{pr=mk.api.music(paths[i++]);}catch(x){return next();}
    Promise.resolve(pr).then(function(res){
      var d=res&&res.data?res.data:res;var arr=(d&&d.data)||[];
      var tt=arr[0]&&arr[0].attributes&&arr[0].attributes.ttml;
      var lines=tt?parseTTML(tt):[];
      if(lines.length>1)A.lc[key]={lines:lines,t:Date.now(),tries:tries};else next();
    }).catch(next);
  }
  next();
}
function domLyric(){
  try{
    var l=all(document,[]),cont=null,i;
    for(i=0;i<l.length;i++){
      var cn=l[i].className;if(cn&&cn.baseVal!==undefined)cn=cn.baseVal;
      if(typeof cn==='string'&&/lyric/i.test(cn)&&l[i].children.length>=4){var r=l[i].getBoundingClientRect();if(r.width>100&&r.height>100){cont=l[i];break;}}
    }
    if(!cont)return null;
    var kids=[].slice.call(cont.children).filter(function(k){return (k.textContent||'').trim();});
    var act=-1;
    for(i=0;i<kids.length;i++){
      var c=kids[i],cl=(typeof c.className==='string'?c.className:'')+' '+((c.firstElementChild&&typeof c.firstElementChild.className==='string')?c.firstElementChild.className:'');
      if(/(^|[\s_-])(active|current|highlight|playing|is-active)([\s_-]|$)/i.test(cl)||c.getAttribute('aria-current')==='true'||c.getAttribute('data-active')==='true'||c.querySelector('[aria-current="true"],[data-active="true"]')){act=i;break;}
    }
    if(act<0)return null;
    return {c:kids[act].textContent.replace(/\s+/g,' ').trim(),n:kids[act+1]?kids[act+1].textContent.replace(/\s+/g,' ').trim():''};
  }catch(x){return null;}
}
A.state=function(known){
  var o={iw:innerWidth},els=null;
  try{
    var ms=navigator.mediaSession,md=ms&&ms.metadata;
    if(md){o.t=md.title||'';o.a=md.artist||'';o.al=md.album||'';
      var best=null,bs=-1;
      for(var i=0;md.artwork&&i<md.artwork.length;i++){var a=md.artwork[i],sz=parseInt((a.sizes||'0').split('x')[0])||0;if(sz>=bs){bs=sz;best=a.src;}}
      if(best)o.art=best;
    }
  }catch(x){}
  var mk=null,item=null;
  try{mk=window.MusicKit&&MusicKit.getInstance&&MusicKit.getInstance();item=mk&&mk.nowPlayingItem;}catch(x){}
  try{
    if(item){
      if(!o.t)o.t=item.title||(item.attributes&&item.attributes.name)||'';
      if(!o.a)o.a=item.artistName||(item.attributes&&item.attributes.artistName)||'';
      if(!o.al)o.al=item.albumName||(item.attributes&&item.attributes.albumName)||'';
      if(!o.art){var u=item.artworkURL||(item.attributes&&item.attributes.artwork&&item.attributes.artwork.url);
        if(u)o.art=String(u).replace('{w}','800').replace('{h}','800').replace('{f}','jpg').replace('{c}','bb');}
    }
  }catch(x){}
  var playing=false,pos=NaN,dur=NaN,rate=1,m=null;
  try{m=mediaEl();}catch(x){}
  try{
    var st=navigator.mediaSession&&navigator.mediaSession.playbackState;
    if(mk&&typeof mk.isPlaying==='boolean')playing=mk.isPlaying;
    else if(st==='playing'||st==='paused')playing=(st==='playing');
    else if(m)playing=!m.paused&&!m.ended;
  }catch(x){}
  try{
    if(mk&&isFinite(mk.currentPlaybackTime)&&isFinite(mk.currentPlaybackDuration)&&mk.currentPlaybackDuration>0){pos=mk.currentPlaybackTime;dur=mk.currentPlaybackDuration;}
    if(!isFinite(dur)&&m&&isFinite(m.duration)&&m.duration>0&&isFinite(m.currentTime)){pos=m.currentTime;dur=m.duration;rate=m.playbackRate||1;}
    if(!isFinite(dur)&&A.ps&&A.ps.d>0){pos=A.ps.p+(playing?(Date.now()-A.ps.ts)/1000*A.ps.r:0);dur=A.ps.d;rate=A.ps.r;}
  }catch(x){}
  o.ps=playing?'playing':((o.t||o.a)?'paused':'none');
  if(isFinite(pos))o.pos=pos; if(isFinite(dur))o.dur=dur; o.rate=rate;
  // lyrics
  try{
    var id=item&&((item.playParams&&(item.playParams.catalogId||item.playParams.id))||item.id);
    id=id?String(id):'';
    if(id&&/^\d+$/.test(id)&&mk&&mk.api&&typeof mk.api.music==='function'){
      var c=A.lc[id],sf=mk.storefrontId||mk.storefrontCountryCode||'us';
      if(!c||(c.none&&c.tries<2&&Date.now()-c.t>20000))fetchLyrics(mk,id,sf);
      else if(c.lines){o.lk=id;if(known!==id)o.ll=c.lines;}
    }
    if(!o.lk){var d=domLyric();if(d){o.lc=d.c;o.ln=d.n;}}
  }catch(x){}
  els=all(document,[]);
  o.af=authFrame(els)?1:0;
  try{return JSON.stringify(o);}catch(x){return '{}';}
};

// ---- page zoom for TV viewing (viewport meta; reflows and stays crisp) ----
setTimeout(function(){try{
  var W0=innerWidth;A.w0=W0;
  if(!(ZOOM>1.01)||W0<=1150)return;
  var N=Math.max(1100,Math.round(W0/ZOOM));
  var m=document.querySelector('meta[name="viewport"]');
  if(!m){m=document.createElement('meta');m.name='viewport';(document.head||document.documentElement).appendChild(m);}
  m.setAttribute('content','width='+N);
  setTimeout(function(){try{if(Math.abs(innerWidth-W0)<W0*0.03&&window.AMWNative&&AMWNative.zoomFailed)AMWNative.zoomFailed();}catch(x){}},900);
}catch(x){}},50);
})();
