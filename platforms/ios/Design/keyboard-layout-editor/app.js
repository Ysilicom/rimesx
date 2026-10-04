(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const clone = value => JSON.parse(JSON.stringify(value));
  const LETTERS = 'abcdefghijklmnopqrstuvwxyz'.split('');
  const ACTIONS = {
    'fn:numbers': { label: '123', name: '数字切换' },
    'fn:buffer': { label: 'Buffer', name: 'Buffer' },
    'fn:language': { label: '中 / 英', name: '中英切换' },
    'fn:space': { label: '空格', name: '空格' },
    'fn:backspace': { label: '⌫', name: '删除' },
    'fn:return': { label: '换行', name: '回车 / 换行' },
    'fn:shift': { label: '⇧', name: '大写切换' },
    'fn:emoji': { label: '☺', name: '表情' },
    'fn:settings': { label: '⚙', name: '设置' },
    'fn:comma': { label: '，', name: '逗号' },
    'fn:period': { label: '。', name: '句号' }
  };
  const GROUP = text => text.length > 1 ? `group:${text.toLowerCase()}` : text.toLowerCase();
  const templates = [
    { id: 'qwerty', name: '26 键 · 经典', short: '26 键 QWERTY', description: '熟悉的位置，轻松开始', rows: ['qwertyuiop','asdfghjkl','zxcvbnm'].map(x=>x.split('')), stagger:true },
    { id: 'orthogonal', name: '26 键 · 正交', short: '26 键 正交', description: '上下对齐，更加规整', rows: ['qwertyuiop','asdfghjkl','zxcvbnm'].map(x=>x.split('')) },
    { id: 'split', name: '26 键 · 分体', short: '26 键 分体', description: '双手分区，拇指舒展', rows: ['qwertyuiop','asdfghjkl','zxcvbnm'].map(x=>x.split('')), split:true },
    { id: 'nine', name: '9 键 · 九宫格', short: '9 键 九宫格', description: '大键布局 · 分组预览', rows: [['.', 'abc','def'],['ghi','jkl','mno'],['pqrs','tuv','wxyz']].map(row=>row.map(GROUP)), grouped:true },
    { id: 'fourteen', name: '14 键 · 双字母', short: '14 键 双字母', description: '熟悉分组 · 布局预览', rows: [['qw','er','ty','ui','op'],['as','df','gh','jk','l'],['zx','cv','bn','m']].map(row=>row.map(GROUP)), grouped:true },
    { id: 'eighteen', name: '18 键 · 共键', short: '18 键 共键', description: '保留独立键 · 布局预览', rows: [['q','we','rt','y','u','io','p'],['a','sd','fg','h','jk','l'],['z','xc','v','bn','m']].map(row=>row.map(GROUP)), grouped:true }
  ];
  let sequence=0;
  const slot = (key=null, width=1) => ({id:`slot-${Date.now().toString(36)}-${sequence++}`,key,width});
  function makeLayout(template) {
    return { version:1, name:template.name, template:template.id, grouped:!!template.grouped,
      rows:template.rows.map(row=>row.map(key=>slot(key))),
      functions:['fn:numbers','fn:buffer','fn:language','fn:space','fn:backspace','fn:return'].map(key=>slot(key,key==='fn:space'?2.8:1)),
      settings:{height:46,gap:6,padding:6,splitGap:14,split:!!template.split,stagger:!!template.stagger} };
  }
  const STORE='rimes.layoutLab.v1';
  const DRAFT='rimes.layoutLab.draft.v1';
  let layout=makeLayout(templates[0]), baseline=JSON.stringify(layout), saved=[], selected=null;
  let undoStack=[],redoStack=[],mode='edit',paletteTab='letters',typed='',upper=false,numeric=false,english=false;
  let dragging=null,ignoreClickUntil=0,sliderHistory=false,pendingSwitch=null,toastTimer;
  let storageAvailable=true,previewScale=1;
  const validLayout = value => value && value.version===1 && templates.some(t=>t.id===value.template) && Array.isArray(value.rows) && value.rows.length>0 && value.rows.length<=5 && value.rows.every(row=>Array.isArray(row)&&row.length>0&&row.length<=12&&row.every(validSlot)) && Array.isArray(value.functions)&&value.functions.length<=12&&value.functions.every(validSlot) && value.settings && ['height','gap','padding','splitGap'].every(k=>Number.isFinite(value.settings[k]));
  function validSlot(s) { return s&&typeof s.id==='string'&&Number.isFinite(s.width)&&s.width>=0.5&&s.width<=4&&(s.key===null||LETTERS.includes(s.key)||s.key==='.'||Object.hasOwn(ACTIONS,s.key)||/^group:[a-z]{2,4}$/.test(s.key)); }
  try {
    saved=JSON.parse(localStorage.getItem(STORE)||'[]').filter(item=>typeof item.id==='string'&&validLayout(item.layout));
    const draft=JSON.parse(localStorage.getItem(DRAFT)||'null');
    if(draft&&validLayout(draft.layout)){layout=draft.layout;baseline=typeof draft.baseline==='string'?draft.baseline:JSON.stringify(layout);}
  } catch { storageAvailable=false; }
  function persist() { try{localStorage.setItem(DRAFT,JSON.stringify({layout,baseline}));localStorage.setItem(STORE,JSON.stringify(saved));}catch{storageAvailable=false;toast('浏览器存储不可用，请导出配置备份');} }
  function allRows(){return [...layout.rows,layout.functions];}
  function allSlots(){return allRows().flat();}
  function findSlot(id){return allSlots().find(s=>s.id===id);}
  function position(id){for(let r=0;r<allRows().length;r++){const c=allRows()[r].findIndex(s=>s.id===id);if(c>=0)return{row:r,col:c};}return null;}
  function label(key){return key==null?'＋':ACTIONS[key]?.label??key.replace('group:','').toUpperCase();}
  function dirty(){return JSON.stringify(layout)!==baseline;}
  function toast(message,undo){$('toast').textContent=message;if(undo){const b=document.createElement('button');b.textContent='撤销';b.onclick=undo;$('toast').append(b);}$('toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').classList.remove('visible'),undo?6500:2500);}
  function record(){undoStack.push(JSON.stringify(layout));if(undoStack.length>80)undoStack.shift();redoStack=[];}
  function change(fn,message){record();fn();persist();render();if(message)toast(message);}
  function applyTemplate(id,blank=false){
    const perform=()=>{const t=templates.find(t=>t.id===id)||templates[0];change(()=>{layout=makeLayout(t);if(blank){layout.name='我的空白布局';layout.rows.forEach(row=>row.forEach(s=>s.key=null));}baseline=JSON.stringify(layout);selected=null;typed='';numeric=false;upper=false;},blank?'空白槽位已准备好，从键位库拖入字母':'已切换模板');};
    if(dirty()){pendingSwitch=perform;$('switch-dialog').showModal();}else perform();
  }
  function saveCopy(name=layout.name){
    const item={id:`saved-${Date.now()}-${Math.random().toString(36).slice(2,6)}`,layout:clone(layout),updatedAt:new Date().toISOString()};
    item.layout.name=name.trim()||'我的布局';saved.unshift(item);layout.name=item.layout.name;baseline=JSON.stringify(layout);persist();render();toast(`已保存「${item.layout.name}」`);
  }
  function sourceTokens(){const t=templates.find(t=>t.id===layout.template);return layout.grouped?[...new Set(t.rows.flat())]:LETTERS;}
  function assign(key,targetID){
    const target=findSlot(targetID);if(!target)return;
    if(target.key===key)return;
    change(()=>{if(key!==null){const source=allSlots().find(s=>s.key===key);if(source)source.key=null;}target.key=key;selected=target.id;},key===null?'键已回到键位库':`已放入 ${label(key)}`);
  }
  function swap(firstID,secondID){if(firstID===secondID)return;const first=findSlot(firstID),second=findSlot(secondID);if(!first||!second)return;change(()=>{[first.key,second.key]=[second.key,first.key];selected=second.id;},'已交换键位');}
  function reorderDirection(dr,dc){
    const p=position(selected);if(!p)return;const rows=allRows(),nextRow=p.row+dr;if(nextRow<0||nextRow>=rows.length)return;const nextCol=dr?Math.min(p.col,rows[nextRow].length-1):p.col+dc;if(nextCol<0||nextCol>=rows[nextRow].length)return;swap(selected,rows[nextRow][nextCol].id);
  }
  function applyRows(raw){
    const parts=raw.trim().split(/[,，/／\s]+/);if(!parts.length||parts.length>5||parts.some(x=>!/^\d+$/.test(x)||+x<1||+x>12)){toast('请输入 1–5 行，每行 1–12 个槽位，例如 10,9,7');return;}
    const counts=parts.map(Number);if(counts.join(',')===layout.rows.map(r=>r.length).join(','))return;
    change(()=>{const old=layout.rows.flat();let i=0;layout.rows=counts.map(n=>Array.from({length:n},()=>old[i++]||slot()));selected=null;},'槽位已更新；未容纳的键可从键位库重新放入');
  }
  function displayKey(key,index){if(mode==='type'&&numeric&&key&&!ACTIONS[key])return '1234567890'[index%10];return mode==='type'&&!upper&&!ACTIONS[key]&&!key?.startsWith('group:')?label(key).toLowerCase():label(key);}
  function renderTemplates(){
    $('templates').replaceChildren(...templates.map(t=>{const b=document.createElement('button');b.className=`template${layout.template===t.id?' active':''}`;b.dataset.template=t.id;b.setAttribute('aria-pressed',layout.template===t.id);b.innerHTML=`<span class="mini-keyboard" aria-hidden="true">${t.rows.map(row=>`<span class="mini-row">${row.map(()=>'<span class="mini-key"></span>').join('')}</span>`).join('')}</span><span><span class="template-title">${t.short}</span><span class="template-subtitle">${t.description}</span></span>${layout.template===t.id?'<span class="template-check">✓</span>':''}`;b.onclick=()=>applyTemplate(t.id);return b;}));
  }
  function renderSaved(){
    $('saved-count').textContent=saved.length;
    $('saved-layouts').replaceChildren();
    if(!saved.length){const p=document.createElement('div');p.className='empty-saved';p.textContent='第一套顺手的布局，从这里开始。';$('saved-layouts').append(p);return;}
    saved.forEach(item=>{const row=document.createElement('div');row.className='saved-row';const b=document.createElement('button');b.className='saved-load';b.dataset.savedId=item.id;b.textContent=item.layout.name;b.title=item.layout.name;b.onclick=()=>{const perform=()=>change(()=>{layout=clone(item.layout);baseline=JSON.stringify(layout);selected=null;},'已载入保存的布局');if(dirty()){pendingSwitch=perform;$('switch-dialog').showModal();}else perform();};const del=document.createElement('button');del.className='saved-delete';del.textContent='×';del.title=`删除「${item.layout.name}」`;del.setAttribute('aria-label',del.title);del.onclick=()=>{const index=saved.findIndex(x=>x.id===item.id);saved=saved.filter(x=>x.id!==item.id);persist();renderSaved();toast('已删除保存副本',()=>{saved.splice(index,0,item);persist();renderSaved();toast('已恢复保存的布局');});};row.append(b,del);$('saved-layouts').append(row);});
  }
  function renderKeyboard(){
    const k=$('keyboard'),s=layout.settings;k.style.padding=`2px ${s.padding}px 4px`;k.style.gap=`${s.gap}px`;k.replaceChildren();
    const available=+$('device-width').value-14-s.padding*2;
    const groups=s.split?layout.rows.flatMap(row=>[row.slice(0,Math.ceil(row.length/2)),row.slice(Math.ceil(row.length/2))]):layout.rows;
    const columns=Math.max(...groups.map(row=>row.length));
    const maxWeight=Math.max(...groups.map(row=>row.reduce((sum,item)=>sum+item.width,0)));
    const unit=Math.max(1,((s.split?(available-s.splitGap)/2:available)-(columns-1)*s.gap)/maxWeight);
    let flatIndex=0;
    allRows().forEach((row,r)=>{
      const line=document.createElement('div');line.className='key-row';line.dataset.rowIndex=r;line.style.gap=`${s.gap}px`;const isFunction=r===layout.rows.length;
      if(s.stagger&&!s.split&&!isFunction){const used=row.reduce((sum,item)=>sum+item.width*unit,0)+(row.length-1)*s.gap;line.style.marginInline=`${Math.max(0,(available-used)/2)}px`;}
      let left=line,right=line;
      if(s.split&&!isFunction){line.style.gap=`${s.splitGap}px`;left=document.createElement('div');right=document.createElement('div');for(const half of [left,right]){half.className='key-half';half.style.gap=`${s.gap}px`;line.append(half);}}
      row.forEach((item,c)=>{
        const b=document.createElement('button');b.type='button';b.className='key'+(item.key===null?' empty-key':'')+(ACTIONS[item.key]?' function-key':'')+(item.key?.startsWith('group:')?' group-key':'')+(selected===item.id?' selected':'');
        b.style.flexGrow=isFunction?item.width:0;b.style.flexShrink='0';b.style.flexBasis=isFunction?'0px':`${item.width*unit}px`;b.style.height=`${s.height}px`;b.dataset.slotId=item.id;b.dataset.slotIndex=`${r}:${c}`;b.dataset.testid='keyboard-slot';b.dataset.key=item.key??'';b.dataset.keyIndex=flatIndex;
        b.setAttribute('aria-label',`${isFunction?'功能行':`第 ${r+1} 行`}第 ${c+1} 槽位：${item.key===null?'空':ACTIONS[item.key]?.name??label(item.key)}`);b.setAttribute('aria-pressed',selected===item.id);b.textContent=displayKey(item.key,flatIndex);
        b.onclick=()=>{if(Date.now()<ignoreClickUntil)return;if(mode==='type'){typeKey(item.key,Number(b.dataset.keyIndex));}else{selected=item.id;render();}};
        (s.split&&!isFunction&&c>=Math.ceil(row.length/2)?right:left).append(b);flatIndex++;
      });k.append(line);
    });
    $('device').classList.toggle('typing',mode==='type');
    $('keyboard-mode-label').textContent=layout.grouped?`${layout.rows.flat().length} 槽位 · 分组预览`:`${english?'英文':'全拼'} · ${layout.rows.flat().length} 槽位`;
    requestAnimationFrame(()=>{updatePreviewScale();renderWarnings();});
  }
  function updatePreviewScale(){
    const scroll=document.querySelector('.device-scroll'),device=$('device'),viewport=$('device-viewport');
    const style=getComputedStyle(scroll),available=scroll.clientWidth-parseFloat(style.paddingLeft)-parseFloat(style.paddingRight);
    const logical=+$('device-width').value;
    previewScale=logical===700?1:Math.min(1,available/logical);
    device.style.width=`${logical}px`;device.style.transform=`scale(${previewScale})`;
    viewport.style.width=`${logical*previewScale}px`;viewport.style.height=`${device.offsetHeight*previewScale}px`;
    $('preview-scale').textContent=previewScale<0.995?`${Math.round(previewScale*100)}%`:'';
    $('geometry-summary').textContent=`键盘高度 ${document.querySelector('.keyboard-shell').offsetHeight} pt`;
  }
  function renderWarnings(){
    const messages=[];const used=allSlots().map(s=>s.key).filter(Boolean);const alphabet=new Set(used.filter(k=>!ACTIONS[k]).join('').replaceAll('group:','').split(''));
    const missing=LETTERS.filter(k=>!alphabet.has(k));if(missing.length)messages.push(`未放入的字母：${missing.join(' ').toUpperCase()}。可从键位库补齐。`);
    const needed=['fn:space','fn:backspace','fn:return'].filter(k=>!used.includes(k));if(needed.length)messages.push(`缺少常用功能：${needed.map(k=>ACTIONS[k].name).join('、')}。`);
    const small=[...$('keyboard').querySelectorAll('.key')].filter(b=>b.getBoundingClientRect().width/previewScale<26);if(small.length)messages.push(`${small.length} 个键宽度小于 26 pt，建议减小间距或减少每行槽位。`);
    const overflow=[...$('keyboard').querySelectorAll('.key-row')].some(row=>row.scrollWidth>row.clientWidth+2);if(overflow)messages.push('部分键位超出可用宽度，请调整槽位或间距。');
    if(layout.grouped)messages.push('共键模板仅预览固定分组与布局；试打不会生成中文候选。');
    if(!storageAvailable)messages.push('浏览器存储不可用，请导出配置备份。');
    $('warnings').replaceChildren(...messages.map(message=>{const p=document.createElement('p');p.textContent=message;return p;}));
  }
  function renderPalette(){
    const used=new Set(allSlots().map(s=>s.key));let keys=paletteTab==='functions'?Object.keys(ACTIONS):sourceTokens();if(paletteTab==='unused')keys=[...sourceTokens(),...Object.keys(ACTIONS)].filter(k=>!used.has(k));
    $('palette-letters').textContent=layout.grouped?'字母组':'字母';['letters','functions','unused'].forEach(t=>$(`palette-${t}`).classList.toggle('active',paletteTab===t));
    $('palette').replaceChildren(...keys.map(key=>{const b=document.createElement('button');b.className='palette-key'+(used.has(key)?' assigned':'');b.textContent=label(key);b.dataset.paletteKey=key;b.title=`${ACTIONS[key]?.name??label(key)}${used.has(key)?' · 已放入，可移动':' · 点击或拖入槽位'}`;b.onclick=()=>{if(Date.now()<ignoreClickUntil)return;if(mode==='type'){toast('切换到「排列键位」后可调整布局');return;}if(!selected){const firstEmpty=allSlots().find(s=>s.key===null);if(firstEmpty){assign(key,firstEmpty.id);}else toast('先点击一个槽位，再选择要放入的键');}else assign(key,selected);};return b;}));
    if(!keys.length){const p=document.createElement('span');p.className='palette-empty';p.textContent='所有键位都已放入。';$('palette').append(p);}
  }
  function renderInspector(){
    const counts=layout.rows.map(r=>r.length).join(',');$('row-counts').value=counts;const matching=[...$('row-preset').options].find(o=>o.value===counts);$('row-preset').value=matching?counts:'custom';$('slot-total').textContent=`${layout.rows.flat().length} 个槽位`;
    ['height','gap','padding','splitGap'].forEach(key=>{$(`setting-${key}`).value=layout.settings[key];$(`value-${key}`).textContent=`${layout.settings[key]} pt`;});$('split').checked=layout.settings.split;$('stagger').checked=layout.settings.stagger;
    $('setting-splitGap').disabled=!layout.settings.split;
    const item=findSlot(selected),p=position(selected);$('selected-position').textContent=p?`${p.row===layout.rows.length?'功能行':`第 ${p.row+1} 行`} · ${p.col+1}`:'未选择';$('selected-key').textContent=item?item.key===null?'空槽位':ACTIONS[item.key]?.name??label(item.key):'点击预览中的一个键';
    const select=$('key-action');select.replaceChildren();const choices=[null,...sourceTokens(),...Object.keys(ACTIONS)];if(item?.key&&!choices.includes(item.key))choices.push(item.key);choices.forEach(key=>{const o=document.createElement('option');o.value=key??'';o.textContent=key===null?'空槽位':ACTIONS[key]?`${ACTIONS[key].label} · ${ACTIONS[key].name}`:label(key);select.append(o);});select.value=item?.key??'';select.disabled=!item||mode!=='edit';
    $('key-width').value=item?.width??1;$('key-width-value').textContent=`${item?.width??1}×`;['key-width','clear-slot','move-left','move-up','move-down','move-right'].forEach(id=>$(id).disabled=!item||mode!=='edit');
  }
  function render(){
    $('layout-name').textContent=layout.name;$('dirty-badge').textContent=dirty()?'未保存':'已保存';if(!dirty()&&!saved.some(i=>i.layout.name===layout.name))$('dirty-badge').textContent='模板';$('dirty-badge').classList.toggle('dirty',dirty());
    $('undo').disabled=!undoStack.length;$('redo').disabled=!redoStack.length;renderTemplates();renderSaved();renderKeyboard();renderPalette();renderInspector();
    ['edit','type'].forEach(m=>{$(`mode-${m}`).classList.toggle('active',mode===m);$(`mode-${m}`).setAttribute('aria-pressed',mode===m);});
    $('canvas-hint').textContent=mode==='edit'?'拖动交换位置 · 点击选择键位':layout.grouped?'分组布局预览 · 尚未接入拼音解码':'点击键盘试打 · 字母与功能键实时响应';
    if(!typed)$('typing-text').innerHTML=`<span class="placeholder">${mode==='type'?'点击下方键盘试打':'切换「试打预览」体验手感'}</span>`;
  }
  function typeKey(key,index){
    if(!key)return;let feedback='字母试打，不进行中文转换';
    if(numeric&&!ACTIONS[key])typed+='1234567890'[index%10];
    else if(key.startsWith('group:')){feedback=`已点击 ${label(key)} 字母组 · 等待后续接入拼音解码`;}
    else if(!ACTIONS[key])typed+=upper?key.toUpperCase():key;
    else switch(key){
      case 'fn:backspace':typed=Array.from(typed).slice(0,-1).join('');break;
      case 'fn:space':typed+=' ';break;
      case 'fn:return':typed+='\n';break;
      case 'fn:comma':typed+='，';break;
      case 'fn:period':typed+='。';break;
      case 'fn:emoji':typed+='🙂';break;
      case 'fn:shift':upper=!upper;feedback=upper?'大写已开启':'小写已开启';break;
      case 'fn:numbers':numeric=!numeric;feedback=numeric?'数字层预览':'已返回字母层';break;
      case 'fn:language':english=!english;feedback=english?'英文模式预览':'拼音模式预览 · 暂不转换中文';break;
      case 'fn:buffer':$('buffer-preview').hidden=!$('buffer-preview').hidden;feedback=$('buffer-preview').hidden?'Buffer 已收起':'Buffer 面板预览';break;
      case 'fn:settings':feedback='布局设置在右侧面板，可返回「排列键位」调整';toast(feedback);break;
    }
    $('typing-text').textContent=typed;$('preview-feedback').textContent=feedback;renderKeyboard();
  }
  function buildSliders(){
    [{key:'height',name:'键高',min:32,max:64},{key:'gap',name:'键间距',min:2,max:12},{key:'padding',name:'左右边距',min:2,max:28},{key:'splitGap',name:'分区间距',min:6,max:40}].forEach(s=>{
      const label=document.createElement('label');label.className='range-label';label.htmlFor=`setting-${s.key}`;label.innerHTML=`<span>${s.name}</span><output id="value-${s.key}"></output>`;const input=document.createElement('input');input.type='range';input.id=`setting-${s.key}`;input.min=s.min;input.max=s.max;input.step=1;input.addEventListener('input',()=>{if(!sliderHistory){record();sliderHistory=true;}layout.settings[s.key]=+input.value;persist();render();});input.addEventListener('change',()=>sliderHistory=false);input.addEventListener('blur',()=>sliderHistory=false);$('sliders').append(label,input);
    });
  }
  // Pointer events work with mouse, pen and touch. Drop only over a known slot;
  // pointercancel and release outside the keyboard leave the model unchanged.
  document.addEventListener('pointerdown',event=>{
    if(mode!=='edit'||event.button!==0)return;const source=event.target.closest('[data-slot-id],[data-palette-key]');if(!source)return;
    dragging={pointer:event.pointerId,x:event.clientX,y:event.clientY,source,slot:source.dataset.slotId,palette:source.dataset.paletteKey,active:false,target:null};
  });
  document.addEventListener('pointermove',event=>{
    if(!dragging||event.pointerId!==dragging.pointer)return;const d=dragging;if(!d.active&&Math.hypot(event.clientX-d.x,event.clientY-d.y)<7)return;
    if(!d.active){d.active=true;d.source.classList.add('drag-source');d.ghost=document.createElement('div');d.ghost.className='drag-ghost';d.ghost.textContent=label(d.palette??findSlot(d.slot)?.key);document.body.append(d.ghost);}
    event.preventDefault();d.ghost.style.left=`${event.clientX}px`;d.ghost.style.top=`${event.clientY}px`;const target=document.elementFromPoint(event.clientX,event.clientY)?.closest('[data-slot-id]');if(d.target!==target){d.target?.classList.remove('drop-target');d.target=target;target?.classList.add('drop-target');}
  },{passive:false});
  function endDrag(event,cancelled=false){
    if(!dragging||event.pointerId!==dragging.pointer)return;const d=dragging;dragging=null;d.source.classList.remove('drag-source');d.target?.classList.remove('drop-target');d.ghost?.remove();if(!d.active)return;ignoreClickUntil=Date.now()+350;if(cancelled||!d.target){toast('已取消移动，键位保持原位');return;}const target=d.target.dataset.slotId;if(d.palette)assign(d.palette,target);else swap(d.slot,target);
  }
  document.addEventListener('pointerup',event=>endDrag(event));document.addEventListener('pointercancel',event=>endDrag(event,true));
  document.addEventListener('keydown',event=>{
    if(event.key==='Escape'&&dragging){endDrag({pointerId:dragging.pointer},true);return;}
    if(event.target.matches('input,select,textarea')||document.querySelector('dialog[open]'))return;
    if((event.metaKey||event.ctrlKey)&&event.key.toLowerCase()==='z'){event.preventDefault();$(event.shiftKey?'redo':'undo').click();return;}
    if(mode==='edit'&&selected&&['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Delete','Backspace'].includes(event.key)){event.preventDefault();if(['Delete','Backspace'].includes(event.key))assign(null,selected);else{const d={ArrowLeft:[0,-1],ArrowRight:[0,1],ArrowUp:[-1,0],ArrowDown:[1,0]}[event.key];reorderDirection(...d);}}
  });
  $('blank').onclick=()=>applyTemplate('qwerty',true);
  $('undo').onclick=()=>{if(!undoStack.length)return;redoStack.push(JSON.stringify(layout));layout=JSON.parse(undoStack.pop());if(!findSlot(selected))selected=null;persist();render();};
  $('redo').onclick=()=>{if(!redoStack.length)return;undoStack.push(JSON.stringify(layout));layout=JSON.parse(redoStack.pop());if(!findSlot(selected))selected=null;persist();render();};
  $('reset').onclick=()=>applyTemplate(layout.template);
  $('mode-edit').onclick=()=>{mode='edit';numeric=false;$('preview-feedback').textContent='拖动键位，或选中后从字母库放入';render();};
  $('mode-type').onclick=()=>{mode='type';$('preview-feedback').textContent=layout.grouped?'分组仅供布局预览，不生成中文候选':'字母试打，不进行中文转换';render();};
  $('device-width').onchange=()=>{$('device').style.width=`${$('device-width').value}px`;renderKeyboard();};
  $('row-preset').onchange=()=>{if($('row-preset').value==='custom'){$('row-counts').focus();$('row-counts').select();}else applyRows($('row-preset').value);};
  $('apply-rows').onclick=()=>applyRows($('row-counts').value);
  $('row-counts').addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();applyRows(e.target.value);}});
  ['split','stagger'].forEach(key=>$(key).onchange=()=>change(()=>layout.settings[key]=$(key).checked));
  $('compact-preset').onclick=()=>change(()=>{Object.assign(layout.settings,{height:38,gap:4,padding:4,splitGap:10});},'已应用紧凑尺寸');
  $('key-action').onchange=()=>assign($('key-action').value||null,selected);
  $('key-width').oninput=()=>{const item=findSlot(selected);if(!item)return;if(!sliderHistory){record();sliderHistory=true;}item.width=+$('key-width').value;persist();render();};
  $('key-width').onchange=()=>sliderHistory=false;$('key-width').onblur=()=>sliderHistory=false;
  $('clear-slot').onclick=()=>assign(null,selected);
  $('move-left').onclick=()=>reorderDirection(0,-1);$('move-right').onclick=()=>reorderDirection(0,1);$('move-up').onclick=()=>reorderDirection(-1,0);$('move-down').onclick=()=>reorderDirection(1,0);
  ['letters','functions','unused'].forEach(t=>$(`palette-${t}`).onclick=()=>{paletteTab=t;renderPalette();});
  $('clear-text').onclick=()=>{typed='';$('typing-text').textContent='';$('preview-feedback').textContent='试打内容已清空';render();};
  $('save').onclick=()=>{$('save-name').value=layout.name;$('save-dialog').showModal();$('save-name').select();};
  $('save-dialog').addEventListener('close',()=>{if($('save-dialog').returnValue==='save')saveCopy($('save-name').value);});
  $('switch-save').onclick=()=>{saveCopy(`${layout.name} · 副本`);$('switch-dialog').close();const action=pendingSwitch;pendingSwitch=null;action?.();};
  $('switch-discard').onclick=()=>{$('switch-dialog').close();const action=pendingSwitch;pendingSwitch=null;action?.();};
  $('switch-cancel').onclick=()=>{pendingSwitch=null;$('switch-dialog').close();};
  $('export').onclick=()=>{
    const exported={...clone(layout),prototypeOnly:true,exportedAt:new Date().toISOString(),coordinateUnit:'pt',previewWidth:+$('device-width').value};
    const blob=new Blob([JSON.stringify(exported,null,2)],{type:'application/json'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`RIMES-${layout.name.replace(/[\\/:*?"<>|]/g,'-')}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);toast('布局配置已导出；尚未应用到 iOS 键盘');
  };
  window.addEventListener('resize',()=>{updatePreviewScale();renderWarnings();});
  buildSliders();render();
  // Read-only snapshots for prototype verification and future native-schema work.
  window.rimesLayoutLab={getState:()=>clone({layout,saved,selected,mode,typed,dirty:dirty(),undoCount:undoStack.length,redoCount:redoStack.length}),getGeometry:()=>[...$('keyboard').querySelectorAll('.key')].map(b=>({slot:b.dataset.slotIndex,key:b.dataset.key,x:b.getBoundingClientRect().x,y:b.getBoundingClientRect().y,width:b.getBoundingClientRect().width,height:b.getBoundingClientRect().height})),templates:clone(templates)};
})();
