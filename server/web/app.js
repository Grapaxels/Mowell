const $ = (id) => document.getElementById(id);
const API = location.origin;
const state = { token: localStorage.getItem('mowell_web_token'), me: null, conversations: [], contacts: [], active: null, messages: [], view: 'chats', pairing: null, incoming: null, call: null, changeCursor: new Date(Date.now() - 5000).toISOString(), mediaUrls: new Set() };
const headers = () => ({ 'Content-Type': 'application/json', ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}) });
const escapeHtml = (value = '') => String(value).replace(/[&<>'"]/g, char => ({ '&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;' }[char]));
const uuid = () => crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
const absolute = (url) => !url ? '' : url.startsWith('/') ? `${API}${url}` : url;

async function api(path, options = {}) {
  const response = await fetch(`${API}${path}`, { ...options, headers: { ...headers(), ...(options.headers || {}) } });
  const type = response.headers.get('content-type') || '';
  const data = type.includes('json') ? await response.json().catch(() => ({})) : await response.blob();
  if (!response.ok) {
    if (response.status === 401 && state.token) logoutLocal();
    throw new Error(data?.error || `Request failed (${response.status})`);
  }
  return data;
}
function toast(message) { $('toast').textContent = message; $('toast').classList.remove('hidden'); clearTimeout(toast.timer); toast.timer = setTimeout(() => $('toast').classList.add('hidden'), 3200); }
function avatarMarkup(name, url) { return url ? `<img src="${escapeHtml(absolute(url))}" alt="">` : escapeHtml((name || 'M').trim()[0]?.toUpperCase() || 'M'); }
function displayTitle(conversation) { if (conversation.isGroup) return conversation.title || 'Mowell group'; const other = (conversation.members || []).find(member => member._id !== state.me?.id); return other?.displayName || other?.username || 'Mowell user'; }
function otherMember(conversation) { return (conversation.members || []).find(member => member._id !== state.me?.id); }
function conversationAvatar(conversation) { return conversation.isGroup ? conversation.avatarUrl : otherMember(conversation)?.avatarUrl; }
function logoutLocal() { localStorage.removeItem('mowell_web_token'); state.token = null; state.me = null; $('workspace').classList.add('hidden'); $('pairing').classList.remove('hidden'); startPairing(); }

async function startPairing() {
  if (state.token) return;
  clearTimeout(startPairing.timer);
  $('pair-status').textContent = 'Creating a secure code…'; $('qr-wrap').innerHTML = '<div class="spinner"></div>';
  try {
    const deviceName = `${navigator.platform || 'Browser'} · ${navigator.userAgent.includes('Mobile') ? 'Mobile web' : 'Web'}`;
    state.pairing = await api('/v1/link/sessions', { method: 'POST', body: JSON.stringify({ deviceName, userAgent: navigator.userAgent }) });
    $('qr-wrap').innerHTML = `<img src="${state.pairing.qrDataUrl}" alt="Mowell linking QR code">`;
    $('pair-code').textContent = state.pairing.pairingCode.replace(/(.{4})/g, '$1 ').trim();
    $('pair-status').textContent = 'Waiting for your phone…';
    pollPairing();
  } catch (error) { $('pair-status').textContent = error.message; startPairing.timer = setTimeout(startPairing, 3500); }
}
async function pollPairing() {
  const pairing = state.pairing; if (!pairing || state.token) return;
  try {
    const result = await api(`/v1/link/sessions/${encodeURIComponent(pairing.sessionId)}?secret=${encodeURIComponent(pairing.secret)}`);
    if (result.status === 'approved') {
      state.token = result.token; state.me = result.user; localStorage.setItem('mowell_web_token', state.token); state.pairing = null; await boot(); return;
    }
  } catch (error) { if (/expired/i.test(error.message)) { $('pair-status').textContent = 'Code expired. Creating another…'; startPairing.timer = setTimeout(startPairing, 1000); return; } }
  setTimeout(pollPairing, 900);
}

async function boot() {
  if (!state.token) return startPairing();
  try { state.me = (await api('/v1/me')).user; }
  catch { return logoutLocal(); }
  $('pairing').classList.add('hidden'); $('workspace').classList.remove('hidden');
  document.body.classList.toggle('dark', localStorage.getItem('mowell_web_dark') === '1');
  await Promise.all([loadConversations(), loadContacts()]); renderList(); changeLoop();
}
async function loadConversations() { const data = await api('/v1/conversations'); state.conversations = data.conversations || []; renderList(); }
async function loadContacts() { const data = await api('/v1/contacts'); state.contacts = data.users || []; }
function setView(view) {
  state.view = view; document.querySelectorAll('.nav[data-view]').forEach(button => button.classList.toggle('active', button.dataset.view === view));
  $('view-title').textContent = ({ chats:'Chats', calls:'Calls', people:'People', groups:'Groups' })[view];
  $('view-subtitle').textContent = view === 'people' ? 'Connect by username' : 'Synced securely';
  $('new-group').classList.toggle('hidden', view !== 'groups'); $('search').value = ''; renderList();
}
function listItem(conversation, callMode = false) {
  const title = displayTitle(conversation), avatar = conversationAvatar(conversation);
  const subtitle = callMode ? (conversation.isGroup ? 'Group video call' : 'Voice or video call') : (conversation.isGroup ? 'Group' : (otherMember(conversation)?.username ? `@${otherMember(conversation).username}` : 'Conversation'));
  return `<div class="list-item ${state.active?._id === conversation._id ? 'active' : ''}" data-conversation="${conversation._id}"><div class="avatar">${avatarMarkup(title, avatar)}</div><div class="item-copy"><b>${escapeHtml(title)}</b><span>${escapeHtml(subtitle)}</span></div>${callMode ? '<button class="icon-button quick-call" title="Call">☎</button>' : '<span class="item-time">›</span>'}</div>`;
}
async function renderList() {
  const query = $('search').value.trim().toLowerCase();
  if (state.view === 'people') return renderPeople(query);
  let values = state.conversations.filter(item => state.view === 'groups' ? item.isGroup : state.view === 'chats' || state.view === 'calls');
  values = values.filter(item => displayTitle(item).toLowerCase().includes(query));
  $('list').innerHTML = values.length ? values.map(item => listItem(item, state.view === 'calls')).join('') : `<div class="empty-chat"><p>No ${state.view} found.</p></div>`;
  document.querySelectorAll('[data-conversation]').forEach(row => row.addEventListener('click', event => {
    const conversation = state.conversations.find(item => item._id === row.dataset.conversation); if (!conversation) return;
    if (event.target.closest('.quick-call')) startCall(conversation, conversation.isGroup); else openConversation(conversation);
  }));
}
async function renderPeople(query) {
  if (query.length < 2) {
    $('list').innerHTML = state.contacts.map(user => `<div class="list-item"><div class="avatar">${avatarMarkup(user.displayName, user.avatarUrl)}</div><div class="item-copy"><b>${escapeHtml(user.displayName)}</b><span>@${escapeHtml(user.username)}</span></div><span class="item-time">Connected</span></div>`).join('') || '<div class="empty-chat"><p>Search for a username to connect.</p></div>'; return;
  }
  try {
    const users = (await api(`/v1/users/search?q=${encodeURIComponent(query)}`)).users || [];
    $('list').innerHTML = users.map(user => `<div class="list-item"><div class="avatar">${avatarMarkup(user.displayName, user.avatarUrl)}</div><div class="item-copy"><b>${escapeHtml(user.displayName)}</b><span>@${escapeHtml(user.username)}</span></div><button class="primary connect" data-user="${user.id}">${state.contacts.some(item => item.id === user.id) ? 'Connected' : 'Connect'}</button></div>`).join('') || '<div class="empty-chat"><p>No people found.</p></div>';
    document.querySelectorAll('.connect').forEach(button => button.onclick = async () => { try { await api('/v1/contacts/requests', { method:'POST', body:JSON.stringify({ userId: button.dataset.user }) }); button.textContent = 'Requested'; button.disabled = true; } catch(error) { toast(error.message); } });
  } catch (error) { toast(error.message); }
}

async function openConversation(conversation) {
  state.active = conversation; $('empty-chat').classList.add('hidden'); $('chat').classList.remove('hidden');
  const title = displayTitle(conversation); $('chat-name').textContent = title; $('chat-avatar').innerHTML = avatarMarkup(title, conversationAvatar(conversation));
  $('chat-presence').textContent = conversation.isGroup ? 'Group' : 'Mowell contact'; renderList();
  await loadMessages(); api(`/v1/conversations/${conversation._id}/messages/read`, { method:'POST', body:'{}' }).catch(() => {});
}
async function loadMessages() {
  if (!state.active) return; const data = await api(`/v1/conversations/${state.active._id}/messages`); state.messages = data.messages || []; renderMessages();
}
function senderId(message) { return message.sender?._id || message.sender; }
function timeLabel(value) { return new Date(value).toLocaleTimeString([], { hour:'2-digit', minute:'2-digit' }); }
function dayLabel(value) { const date = new Date(value), today = new Date(), yesterday = new Date(Date.now()-86400000); return date.toDateString() === today.toDateString() ? 'Today' : date.toDateString() === yesterday.toDateString() ? 'Yesterday' : date.toLocaleDateString(); }
function renderMessages() {
  state.mediaUrls.forEach(URL.revokeObjectURL); state.mediaUrls.clear(); let lastDay = '';
  $('messages').innerHTML = state.messages.map(message => {
    const day = new Date(message.sentAt).toDateString(); const divider = day !== lastDay ? `<div class="day">${dayLabel(message.sentAt)}</div>` : ''; lastDay = day;
    const mine = senderId(message) === state.me.id; const attachment = message.attachment?._id || message.attachment;
    const call = message.kind === 'call_end' ? 'Call ended' : message.kind === 'call' ? 'Call invitation' : '';
    const body = message.deletedForEveryone ? '<i>This message was deleted</i>' : escapeHtml(call || message.body || '');
    const media = attachment ? `<div class="media" data-media="${attachment}" data-mime="${escapeHtml(message.attachment?.mimeType || '')}" data-name="${escapeHtml(message.attachment?.fileName || 'Attachment')}"><button class="secondary">Open attachment</button></div>` : '';
    return `${divider}<div class="bubble ${mine?'mine':''}" data-message="${message.clientId || message._id}">${media}${body}<div class="meta">${timeLabel(message.sentAt)} ${mine ? ({sent:'✓',delivered:'✓✓',seen:'<span style="color:#67d8ff">✓✓</span>'}[message.delivery] || '✓') : ''}</div></div>`;
  }).join('');
  document.querySelectorAll('.media').forEach(hydrateMedia); const box = $('messages'); box.scrollTop = box.scrollHeight;
}
async function hydrateMedia(element) {
  try {
    const blob = await api(`/v1/attachments/${element.dataset.media}`); const url = URL.createObjectURL(blob); state.mediaUrls.add(url); const mime = element.dataset.mime || blob.type;
    if (mime.startsWith('image/')) element.innerHTML = `<img src="${url}" alt="${element.dataset.name}">`;
    else if (mime.startsWith('video/')) element.innerHTML = `<video src="${url}" controls playsinline></video>`;
    else if (mime.startsWith('audio/')) element.innerHTML = `<audio src="${url}" controls></audio>`;
    else element.innerHTML = `<a class="secondary" href="${url}" download="${escapeHtml(element.dataset.name)}">Download ${escapeHtml(element.dataset.name)}</a>`;
  } catch { element.textContent = 'Attachment unavailable'; }
}
async function sendMessage(body, kind='text', extra={}) {
  if (!state.active || !body.trim()) return; const clientId = uuid(), optimistic = { _id:clientId, clientId, conversation:state.active._id, sender:{_id:state.me.id}, body:body.trim(), kind, sentAt:new Date().toISOString(), delivery:'sent', ...extra };
  state.messages.push(optimistic); renderMessages(); $('message-input').value=''; toggleComposer();
  try { await api(`/v1/conversations/${state.active._id}/messages`, { method:'POST', body:JSON.stringify({ clientId, body:body.trim(), kind, ...extra }) }); await loadConversations(); }
  catch(error) { toast(error.message); }
}
async function uploadFile(file) {
  if (!state.active || !file) return; if (file.size > 2621440) return toast('Files must be 2.5 MB or smaller.');
  const data = await new Promise((resolve,reject) => { const reader=new FileReader(); reader.onload=()=>resolve(reader.result.split(',')[1]); reader.onerror=reject; reader.readAsDataURL(file); });
  try { await api(`/v1/conversations/${state.active._id}/attachments`, { method:'POST', body:JSON.stringify({ clientId:uuid(), fileName:file.name, mimeType:file.type||'application/octet-stream', data }) }); await loadMessages(); }
  catch(error) { toast(error.message); }
}

async function changeLoop() {
  while (state.token) {
    try {
      const data = await api(`/v1/messages/changes?after=${encodeURIComponent(state.changeCursor)}&waitMs=3500`); const changes = data.messages || [];
      if (changes.length) {
        state.changeCursor = new Date(Math.max(...changes.map(item => new Date(item.updatedAt || item.sentAt).getTime())) + 1).toISOString();
        for (const message of changes) {
          if (message.kind === 'call' && senderId(message) !== state.me.id) showIncoming(message);
          if (state.active && message.conversationId === state.active._id) { const index=state.messages.findIndex(item=>(item.clientId||item._id)===(message.clientId||message._id)); if(index>=0)state.messages[index]=message;else state.messages.push(message); }
        }
        if (state.active) renderMessages(); loadConversations();
      }
    } catch { await new Promise(resolve => setTimeout(resolve,1200)); }
  }
}

async function showDevices() {
  try { const devices=(await api('/v1/linked-devices')).devices||[]; $('devices').innerHTML=devices.map(device=>`<div class="device-row"><div><b>${escapeHtml(device.name)}</b><br><small>${escapeHtml(device.platform)} · ${new Date(device.lastSeenAt).toLocaleString()}${device.current?' · This browser':''}</small></div><button class="danger revoke" data-id="${device.id}">Log out</button></div>`).join('')||'<p>No linked browsers.</p>'; document.querySelectorAll('.revoke').forEach(button=>button.onclick=async()=>{await api(`/v1/linked-devices/${button.dataset.id}`,{method:'DELETE'});showDevices()}); $('device-dialog').showModal(); } catch(error){toast(error.message)}
}
async function createGroup(event) { event.preventDefault(); const ids=[...document.querySelectorAll('#group-members input:checked')].map(input=>input.value); if(!ids.length)return toast('Select at least one person.'); try{const result=await api('/v1/conversations',{method:'POST',body:JSON.stringify({title:$('group-name').value,memberIds:ids,groupType:'private'})});$('group-dialog').close();await loadConversations();const created=state.conversations.find(item=>item._id===(result.conversation?._id||result.conversation?.id));if(created)openConversation(created)}catch(error){toast(error.message)} }
function openGroupDialog(){ $('group-members').innerHTML=state.contacts.map(user=>`<label class="member-option"><input type="checkbox" value="${user.id}"><div class="avatar">${avatarMarkup(user.displayName,user.avatarUrl)}</div><span>${escapeHtml(user.displayName)}<br><small>@${escapeHtml(user.username)}</small></span></label>`).join('')||'<p>Connect with people first.</p>'; $('group-dialog').showModal(); }

let recorder, recordChunks=[];
async function toggleRecord(){if(recorder?.state==='recording'){recorder.stop();return}try{const stream=await navigator.mediaDevices.getUserMedia({audio:true});recordChunks=[];recorder=new MediaRecorder(stream);recorder.ondataavailable=e=>{if(e.data.size)recordChunks.push(e.data)};recorder.onstop=()=>{stream.getTracks().forEach(track=>track.stop());uploadFile(new File([new Blob(recordChunks,{type:recorder.mimeType})],`mowell_voice_${Date.now()}.webm`,{type:recorder.mimeType}))};recorder.start();$('record').classList.add('call-red');toast('Recording… tap again to send')}catch{toast('Microphone permission is required.')}}

const call = { peers:new Map(), stream:null, room:'', conversation:null, video:false, lastSequence:0, closed:true, pollTimer:null };
async function callSignal(type,payload={},target=null){return api(`/v1/calls/${encodeURIComponent(call.room)}/signals`,{method:'POST',body:JSON.stringify({clientId:`web-${uuid()}`,type,payload,target})})}
async function makePeer(id,name,offer){if(call.peers.has(id))return call.peers.get(id);const pc=new RTCPeerConnection({iceServers:[{urls:['stun:stun.l.google.com:19302','stun:stun.cloudflare.com:3478']}],iceCandidatePoolSize:3});const remote=new MediaStream(),entry={id,name,pc,remote,pending:[]};call.peers.set(id,entry);call.stream.getTracks().forEach(track=>pc.addTrack(track,call.stream));pc.ontrack=event=>{if(!remote.getTracks().some(track=>track.id===event.track.id))remote.addTrack(event.track);let video=document.getElementById(`remote-${id}`);if(!video){video=document.createElement('video');video.id=`remote-${id}`;video.autoplay=true;video.playsInline=true;$('remote-videos').append(video)}video.srcObject=remote;video.play().catch(()=>{})};pc.onicecandidate=event=>{if(event.candidate)callSignal('ice',event.candidate.toJSON(),id).catch(()=>{})};pc.onconnectionstatechange=()=>{$('call-status').textContent=pc.connectionState==='connected'?'Call connected':pc.connectionState};if(offer)await sendOffer(entry);return entry}
async function sendOffer(entry,restart=false){if(entry.pc.signalingState!=='stable')return;await entry.pc.setLocalDescription(await entry.pc.createOffer(restart?{iceRestart:true}:undefined));await callSignal('offer',{type:entry.pc.localDescription.type,sdp:entry.pc.localDescription.sdp},entry.id)}
async function receiveCallSignal(signal){if(signal.senderId===state.me.id)return;if(signal.type==='join')await makePeer(signal.senderId,signal.senderName,state.me.id.localeCompare(signal.senderId)<0);else if(signal.type==='offer'){const peer=await makePeer(signal.senderId,signal.senderName,false);if(peer.pc.signalingState==='have-local-offer')await peer.pc.setLocalDescription({type:'rollback'}).catch(()=>{});if(peer.pc.signalingState!=='stable')return;await peer.pc.setRemoteDescription(signal.payload);for(const candidate of peer.pending.splice(0))await peer.pc.addIceCandidate(candidate);await peer.pc.setLocalDescription(await peer.pc.createAnswer());await callSignal('answer',{type:peer.pc.localDescription.type,sdp:peer.pc.localDescription.sdp},signal.senderId)}else if(signal.type==='answer'){const peer=await makePeer(signal.senderId,signal.senderName,false);if(peer.pc.signalingState==='have-local-offer'){await peer.pc.setRemoteDescription(signal.payload);for(const candidate of peer.pending.splice(0))await peer.pc.addIceCandidate(candidate)}}else if(signal.type==='ice'){const peer=await makePeer(signal.senderId,signal.senderName,false);if(peer.pc.remoteDescription)await peer.pc.addIceCandidate(signal.payload).catch(()=>{});else peer.pending.push(signal.payload)}else if(signal.type==='leave'){removePeer(signal.senderId);if(!call.conversation?.isGroup&&call.peers.size===0)endCall(false)}}
async function pollCall(){while(!call.closed){try{const data=await api(`/v1/calls/${encodeURIComponent(call.room)}/signals${call.lastSequence?`?afterSequence=${call.lastSequence}`:''}`);for(const signal of data.signals||[]){await receiveCallSignal(signal);call.lastSequence=Math.max(call.lastSequence,Number(signal.sequence)||0)}}catch(error){if(/ended/i.test(error.message)){endCall(false);return}}await new Promise(resolve=>setTimeout(resolve,160))}}
function removePeer(id){const peer=call.peers.get(id);peer?.pc.close();call.peers.delete(id);document.getElementById(`remote-${id}`)?.remove()}
async function startCall(conversation,video){const room=`Mowell-Web-${uuid().replaceAll('-','')}`;try{await api(`/v1/calls/${room}/ring`,{method:'POST',body:JSON.stringify({conversationId:conversation._id,video})});await openCall({room,conversation,video,initiator:true})}catch(error){toast(error.message)}}
async function openCall({room,conversation,video,initiator=false}){try{call.closed=false;call.room=room;call.conversation=conversation;call.video=video;call.lastSequence=0;call.peers.clear();$('incoming').classList.add('hidden');$('call').classList.remove('hidden');$('call-status').textContent='Connecting…';call.stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true},video:video?{facingMode:'user',width:{ideal:1280},height:{ideal:720}}:false});$('local-video').srcObject=call.stream;$('local-video').classList.toggle('hidden',!video);await api(`/v1/calls/${room}/join`,{method:'POST',body:JSON.stringify({conversationId:conversation._id,video,initiator})});pollCall();await callSignal('join',{video})}catch(error){toast(error.message);endCall(false)}}
async function endCall(notify=true){if(call.closed)return;call.closed=true;if(notify)callSignal('leave').catch(()=>{});call.stream?.getTracks().forEach(track=>track.stop());call.peers.forEach(peer=>peer.pc.close());call.peers.clear();$('remote-videos').innerHTML='';$('call').classList.add('hidden')}
function showIncoming(message){if(!message.conversationId||call.closed===false)return;const data=JSON.parse(message.body||'{}'),conversation=state.conversations.find(item=>item._id===message.conversationId);if(!data.room||!conversation)return;state.incoming={message,data,conversation};$('incoming-name').textContent=displayTitle(conversation);$('incoming-kind').textContent=`Incoming ${data.video?'video':'voice'} call`;$('incoming-avatar').innerHTML=avatarMarkup(displayTitle(conversation),conversationAvatar(conversation));$('incoming').classList.remove('hidden')}

document.querySelectorAll('.nav[data-view]').forEach(button=>button.onclick=()=>setView(button.dataset.view));
$('search').addEventListener('input',()=>renderList()); $('new-code').onclick=startPairing; $('new-group').onclick=openGroupDialog; $('group-form').onsubmit=createGroup; document.querySelector('#group-dialog .dialog-close').onclick=()=>$('group-dialog').close(); document.querySelector('#device-dialog .dialog-close').onclick=()=>$('device-dialog').close();
$('theme-toggle').onclick=()=>{document.body.classList.toggle('dark');localStorage.setItem('mowell_web_dark',document.body.classList.contains('dark')?'1':'0')}; $('device-button').onclick=showDevices; $('web-logout').onclick=async()=>{const devices=(await api('/v1/linked-devices')).devices||[];const current=devices.find(device=>device.current);if(current)await api(`/v1/linked-devices/${current.id}`,{method:'DELETE'});logoutLocal();$('device-dialog').close()};
$('mobile-back').onclick=()=>{$('chat').classList.add('hidden');$('empty-chat').classList.remove('hidden')}; $('message-input').addEventListener('input',()=>{toggleComposer();if(state.active)api(`/v1/conversations/${state.active._id}/typing`,{method:'POST',body:JSON.stringify({active:$('message-input').value.trim().length>0})}).catch(()=>{})});
function toggleComposer(){$('send').classList.toggle('hidden',!$('message-input').value.trim());$('record').classList.toggle('hidden',!!$('message-input').value.trim())}
$('message-input').addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();sendMessage($('message-input').value)}});$('send').onclick=()=>sendMessage($('message-input').value);$('attach').onclick=()=>$('file-input').click();$('file-input').onchange=()=>uploadFile($('file-input').files[0]);$('record').onclick=toggleRecord;
$('location').onclick=()=>navigator.geolocation.getCurrentPosition(position=>sendMessage(JSON.stringify({latitude:position.coords.latitude,longitude:position.coords.longitude}),'location'),()=>toast('Location permission is required.'));
$('voice-call').onclick=()=>state.active&&startCall(state.active,false);$('video-call').onclick=()=>state.active&&startCall(state.active,true);$('call-hang').onclick=()=>endCall(true);$('call-mute').onclick=()=>{const track=call.stream?.getAudioTracks()[0];if(track){track.enabled=!track.enabled;$('call-mute').textContent=track.enabled?'Mic':'Unmute'}};$('call-camera').onclick=()=>{const track=call.stream?.getVideoTracks()[0];if(track){track.enabled=!track.enabled;$('call-camera').textContent=track.enabled?'Camera':'Camera off'}};
$('accept-call').onclick=()=>{const incoming=state.incoming;if(incoming)openCall({room:incoming.data.room,conversation:incoming.conversation,video:!!incoming.data.video,initiator:false});state.incoming=null};$('decline-call').onclick=async()=>{const incoming=state.incoming;if(incoming){call.room=incoming.data.room;await callSignal('leave',{reason:'declined'}).catch(()=>{})}state.incoming=null;$('incoming').classList.add('hidden')};
boot();
