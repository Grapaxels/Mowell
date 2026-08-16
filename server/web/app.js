const $ = (id) => document.getElementById(id);
const API = location.hostname === 'mowellweb.grapaxels.in' ? 'https://mowell-api.grapaxels.in' : location.origin;
const DEVICE_ID = localStorage.getItem('mowell_web_device_id') || (crypto.randomUUID ? crypto.randomUUID() : `web-${Date.now()}-${Math.random()}`);
localStorage.setItem('mowell_web_device_id', DEVICE_ID);
const state = {
  token: localStorage.getItem('mowell_web_token') || sessionStorage.getItem('mowell_web_token'),
  me: null,
  conversations: [],
  connectionRequests: [],
  active: null,
  messages: [],
  view: 'chats',
  filter: 'all',
  reply: null,
  incoming: null,
  dismissedCallRooms: new Set(),
  knownUpdates: new Map(),
  initialized: false,
  polling: false,
  mediaUrls: new Set(),
  lastMessagesSignature: '',
  lastListHtml: ''
};

const call = {
  room: '', conversation: null, video: false, stream: null, peers: new Map(),
  lastId: '', closed: true, pollTimer: null, startedAt: 0, timer: null,
  iceServers: [{ urls: ['stun:35.154.86.33:3478'] }],
  facingMode: 'user', screenStream: null, controlsTimer: null, heartbeatTimer: null, qualityUpgradeStarted: false
};
const fallbackIceServers = [
  { urls: ['stun:35.154.86.33:3478'] },
  { urls: ['turn:35.154.86.33:3478?transport=udp', 'turn:35.154.86.33:3478?transport=tcp'], username: 'turnuser', credential: '@Grapaxels1338' },
  { urls: ['stun:stun.relay.metered.ca:80'] },
  { urls: ['turn:global.relay.metered.ca:80', 'turn:global.relay.metered.ca:80?transport=tcp', 'turn:global.relay.metered.ca:443', 'turns:global.relay.metered.ca:443?transport=tcp'], username: '9385ce067902b45d0c90d944', credential: 'TS2yMQueZBcqV0yg' }
];

const escapeHtml = (value = '') => String(value).replace(/[&<>'"]/g, (char) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
}[char]));
const uuid = () => crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
const absolute = (url) => !url ? '' : url.startsWith('/') ? `${API}${url}` : url;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
let incomingBrowserNotification = null;

function requestCallNotificationPermission() {
  if ('Notification' in window && Notification.permission === 'default') {
    try {
      const request = Notification.requestPermission();
      request?.catch?.(() => {});
    } catch { /* Older browsers still receive the in-page call screen. */ }
  }
}

async function api(path, options = {}) {
  const headers = { ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}), ...(options.headers || {}) };
  if (options.body && !(options.body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${API}${path}`, { ...options, headers });
  if (options.blob) {
    if (!response.ok) throw new Error('Could not download this attachment');
    return response.blob();
  }
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(payload.error || `Request failed (${response.status})`);
    error.status = response.status;
    error.payload = payload;
    throw error;
  }
  return payload;
}

function setLoading(button, active) {
  button?.classList.toggle('loading', active);
  if (button) button.disabled = active;
}

function toast(message) {
  $('toast').textContent = message;
  $('toast').classList.remove('hidden');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => $('toast').classList.add('hidden'), 3200);
}

function setError(id, message = '') { $(id).textContent = message; }
function userId(user) { return String(user?._id || user?.id || user || ''); }
function senderId(message) { return userId(message?.sender); }
function conversationId(conversation) { return String(conversation?._id || conversation?.id || ''); }
function otherMember(conversation) { return (conversation?.members || []).find((member) => userId(member) !== state.me?.id); }
function displayTitle(conversation) {
  if (!conversation) return 'Mowell';
  if (conversation.isGroup) return conversation.title || 'Mowell group';
  const other = otherMember(conversation);
  return other?.displayName || other?.username || conversation.title || 'Mowell user';
}
function avatarUrl(conversation) { return conversation?.isGroup ? conversation.avatarUrl : otherMember(conversation)?.avatarUrl; }
function avatarMarkup(name, url) {
  return url ? `<img src="${escapeHtml(absolute(url))}" alt="">` : escapeHtml((name || 'M').trim()[0]?.toUpperCase() || 'M');
}
function formatTime(value) { return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }); }
function formatListTime(value) {
  const date = new Date(value), now = new Date();
  if (date.toDateString() === now.toDateString()) return formatTime(value);
  if (date.getFullYear() === now.getFullYear()) return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
  return date.toLocaleDateString([], { year: '2-digit', month: 'short', day: 'numeric' });
}
function dayLabel(value) {
  const date = new Date(value), today = new Date(), yesterday = new Date(Date.now() - 86400000);
  if (date.toDateString() === today.toDateString()) return 'Today';
  if (date.toDateString() === yesterday.toDateString()) return 'Yesterday';
  return date.toLocaleDateString([], { weekday: 'short', day: 'numeric', month: 'short', year: date.getFullYear() === today.getFullYear() ? undefined : 'numeric' });
}
function preview(message) {
  if (!message) return 'Start a conversation';
  const map = { image: 'Photo', video: 'Video', audio: 'Voice message', file: message.attachment?.fileName || message.body || 'Document', location: 'Location', contact: 'Contact', sticker: `Sticker ${message.body || ''}`, call: 'Incoming call', call_end: 'Call ended' };
  return map[message.kind] || message.body || 'Message';
}

function saveSession(token, remember = true) {
  localStorage.removeItem('mowell_web_token');
  sessionStorage.removeItem('mowell_web_token');
  (remember ? localStorage : sessionStorage).setItem('mowell_web_token', token);
  state.token = token;
}

function clearSession() {
  localStorage.removeItem('mowell_web_token');
  sessionStorage.removeItem('mowell_web_token');
  state.token = null;
  state.me = null;
  state.active = null;
  state.conversations = [];
  $('workspace').classList.add('hidden');
  $('auth-screen').classList.remove('hidden');
  $('account-dialog').close();
}

let webLinkTimer = null;
async function openWebQr() {
  clearTimeout(webLinkTimer);
  $('web-qr-image').removeAttribute('src');
  $('web-qr-status').textContent = 'Creating a secure one-time code…';
  $('web-qr-dialog').showModal();
  try {
    const link = await api('/v1/web-link/session', { method: 'POST', body: JSON.stringify({ deviceId: DEVICE_ID, deviceName: navigator.userAgent }) });
    $('web-qr-image').src = absolute(link.qrUrl);
    $('web-qr-status').textContent = 'Open Linked devices in Mowell on your phone and scan this code.';
    const poll = async () => {
      if (!$('web-qr-dialog').open) return;
      try {
        const result = await api(`/v1/web-link/session/${encodeURIComponent(link.token)}`);
        if (result.status === 'approved' && result.token) {
          saveSession(result.token, true); state.me = result.user; $('web-qr-dialog').close(); toast('Mowell Web linked.'); await enterWorkspace(); return;
        }
      } catch (error) { if (error.status === 410) { $('web-qr-status').textContent = 'This code expired. Close and open the scanner again.'; return; } }
      webLinkTimer = setTimeout(poll, 1000);
    };
    poll();
  } catch (error) { $('web-qr-status').textContent = error.message; }
}

function maskEmail(email) {
  const [name = '', domain = ''] = String(email).split('@');
  const visibleName = name.slice(0, 2);
  const domainParts = domain.split('.');
  const suffix = domainParts.length > 1 ? `.${domainParts.pop()}` : '';
  return `${visibleName}${'*'.repeat(Math.max(4, name.length - 2))}@${'*'.repeat(Math.max(2, domainParts.join('.').length))}${suffix}`;
}

async function login(event) {
  event.preventDefault();
  requestCallNotificationPermission();
  setError('auth-error');
  setLoading($('login-button'), true);
  try {
    const data = await api('/v1/auth/login', { method: 'POST', body: JSON.stringify({ identity: $('identity').value.trim(), password: $('password').value, deviceId: DEVICE_ID }) });
    if (data.verificationRequired) {
      openVerification(data.email);
      return;
    }
    if (!data.token || !data.user) throw new Error('The server did not complete sign-in. Deploy the latest Mowell server.');
    saveSession(data.token, $('remember').checked);
    state.me = data.user;
    await enterWorkspace();
  } catch (error) {
    if (error.payload?.verificationRequired) openVerification(error.payload.email);
    else setError('auth-error', error.message);
  } finally { setLoading($('login-button'), false); }
}

async function register(event) {
  event.preventDefault();
  requestCallNotificationPermission();
  setError('register-error');
  setLoading($('register-button'), true);
  const email = $('register-email').value.trim();
  try {
    await api('/v1/auth/register', { method: 'POST', body: JSON.stringify({
      displayName: $('register-name').value.trim(), username: $('register-username').value.trim().toLowerCase(),
      email, password: $('register-password').value
    }) });
    openVerification(email);
  } catch (error) {
    if (error.payload?.verificationRequired) openVerification(error.payload.email || email);
    setError('register-error', error.message);
  } finally { setLoading($('register-button'), false); }
}

function openVerification(email) {
  $('verify-email').value = email;
  $('verify-copy').textContent = `Enter the code sent to ${maskEmail(email)}.`;
  setError('verify-error');
  $('verify-dialog').showModal();
}

async function verifyEmail(event) {
  event.preventDefault();
  setLoading($('verify-button'), true);
  setError('verify-error');
  try {
    const data = await api('/v1/auth/verify-email', { method: 'POST', body: JSON.stringify({ email: $('verify-email').value, code: $('verify-code').value.trim(), deviceId: DEVICE_ID }) });
    saveSession(data.token, true);
    state.me = data.user;
    $('verify-dialog').close();
    await enterWorkspace();
  } catch (error) { setError('verify-error', error.message); }
  finally { setLoading($('verify-button'), false); }
}

async function requestReset() {
  setError('reset-error');
  setLoading($('request-reset'), true);
  try {
    const data = await api('/v1/auth/request-password-reset', { method: 'POST', body: JSON.stringify({ email: $('reset-email').value.trim() }) });
    toast(data.message || 'Reset code sent');
  } catch (error) { setError('reset-error', error.message); }
  finally { setLoading($('request-reset'), false); }
}

async function resetPassword(event) {
  event.preventDefault();
  setError('reset-error');
  setLoading($('reset-button'), true);
  try {
    const data = await api('/v1/auth/reset-password', { method: 'POST', body: JSON.stringify({ email: $('reset-email').value.trim(), code: $('reset-code').value.trim(), password: $('reset-password').value }) });
    $('reset-dialog').close(); toast(data.message || 'Password updated');
  } catch (error) { setError('reset-error', error.message); }
  finally { setLoading($('reset-button'), false); }
}

async function boot() {
  document.body.classList.toggle('dark', localStorage.getItem('mowell_web_theme') === 'dark');
  if (matchMedia('(max-width: 600px) and (pointer: coarse)').matches) {
    $('phone-download').classList.remove('hidden');
    try { const release = await api('/v1/app/version'); $('latest-apk-link').href = release.apkUrl; $('latest-apk-version').textContent = `Latest version ${release.versionName}`; }
    catch { $('latest-apk-version').textContent = 'Latest Android release'; }
    return;
  }
  if (!state.token) return;
  try {
    state.me = (await api('/v1/me')).user;
    await enterWorkspace();
  } catch { clearSession(); }
}

async function enterWorkspace() {
  $('auth-screen').classList.add('hidden');
  $('workspace').classList.remove('hidden');
  const name = state.me.displayName || state.me.username;
  const markup = avatarMarkup(name, state.me.avatarUrl);
  $('account-button').innerHTML = markup;
  $('profile-avatar').innerHTML = markup;
  $('profile-name').textContent = name;
  $('profile-username').textContent = `@${state.me.username}`;
  state.initialized = false;
  await loadConversations();
  state.initialized = true;
  for (const conversation of state.conversations.slice(0, 20)) {
    await inspectLatest(conversation);
    if (state.incoming) break;
  }
  setView('chats');
  startPolling();
}

async function loadConversations() {
  const [data, requestData] = await Promise.all([api('/v1/conversations'), api('/v1/connections/requests').catch(() => ({ requests: [] }))]);
  state.connectionRequests = requestData.requests || [];
  const incoming = (data.conversations || []).filter((conversation) => {
    const id = conversationId(conversation);
    const hiddenAt = Number(localStorage.getItem(`mowell_hide_${id}`) || 0);
    const stamp = new Date(conversation.lastMessageAt || conversation.updatedAt || 0).getTime();
    if (hiddenAt && stamp <= hiddenAt) return false;
    if (hiddenAt) localStorage.removeItem(`mowell_hide_${id}`);
    return true;
  });
  const changed = [];
  for (const conversation of incoming) {
    conversation._lastMessage = conversation.lastMessage || conversation._lastMessage || null;
    const id = conversationId(conversation);
    const stamp = new Date(conversation.lastMessageAt || conversation.updatedAt || 0).getTime();
    const previous = state.knownUpdates.get(id);
    state.knownUpdates.set(id, stamp);
    if (state.initialized && (!previous || stamp > previous)) changed.push(conversation);
  }
  state.conversations = incoming;
  renderList();
  if (state.active) state.active = incoming.find((item) => conversationId(item) === conversationId(state.active)) || state.active;
  for (const conversation of changed) await inspectLatest(conversation);
}

async function inspectLatest(conversation) {
  try {
    const data = await api(`/v1/conversations/${conversationId(conversation)}/messages`);
    const messages = data.messages || [];
    if (state.active && conversationId(state.active) === conversationId(conversation)) {
      state.messages = messages;
      renderMessages(false);
    }
    const endedRooms = new Set(messages.filter((item) => item.kind === 'call_end').map((item) => parseBody(item).room).filter(Boolean));
    const latest = [...messages].reverse().find((item) => {
      if (item.kind !== 'call' || senderId(item) === state.me.id) return false;
      const room = parseBody(item).room;
      return room && !endedRooms.has(room) && !state.dismissedCallRooms.has(room);
    });
    if (latest && call.closed) {
      const body = parseBody(latest);
      if (body.room !== state.incoming?.data?.room) {
        await api(`/v1/calls/${encodeURIComponent(body.room)}/signals`);
        showIncoming(conversation, latest, body);
      }
    }
  } catch { /* Polling retries on the next update. */ }
}

function setView(view) {
  state.view = view;
  state.lastListHtml = '';
  document.querySelectorAll('.nav-button[data-view]').forEach((button) => button.classList.toggle('active', button.dataset.view === view));
  const titles = { chats: ['Chats', 'Search conversations'], calls: ['Calls', 'Search call history'], people: ['People', 'Search username'], groups: ['Groups', 'Search groups'] };
  $('view-title').textContent = titles[view][0];
  $('search-input').placeholder = titles[view][1];
  $('filter-row').classList.toggle('hidden', view !== 'chats');
  $('new-button').title = view === 'groups' ? 'Create group' : 'New conversation';
  $('search-input').value = '';
  renderList();
}

function conversationRow(conversation) {
  const id = conversationId(conversation), title = displayTitle(conversation), active = conversationId(state.active) === id;
  const last = conversation._lastMessage;
  return `<button class="conversation-row ${active ? 'active' : ''}" data-conversation="${escapeHtml(id)}">
    <span class="avatar ${conversation.isGroup ? 'group' : ''}">${avatarMarkup(title, avatarUrl(conversation))}</span>
    <span class="row-copy"><strong>${escapeHtml(title)}${conversation.isGroup ? '<span class="group-tag">Group</span>' : ''}</strong><span>${escapeHtml(preview(last))}</span></span>
    <span class="row-meta">${formatListTime(conversation.lastMessageAt || conversation.updatedAt || Date.now())}</span>
  </button>`;
}

async function renderList() {
  const query = $('search-input').value.trim().toLowerCase();
  if (state.view === 'people') return renderPeople(query);
  let list = [...state.conversations];
  if (state.view === 'groups') list = list.filter((item) => item.isGroup);
  if (state.view === 'calls') list = list.filter((item) => ['call', 'call_end'].includes(item._lastMessage?.kind));
  if (state.view === 'chats' && state.filter === 'direct') list = list.filter((item) => !item.isGroup);
  if (state.view === 'chats' && state.filter === 'groups') list = list.filter((item) => item.isGroup);
  if (query) list = list.filter((item) => displayTitle(item).toLowerCase().includes(query));
  const requests = state.view === 'chats' && !query ? state.connectionRequests.map(connectionRequestMarkup).join('') : '';
  const conversations = list.length ? list.map(conversationRow).join('') : `<div class="list-empty"><div><svg><use href="#i-${state.view === 'groups' ? 'group' : state.view === 'calls' ? 'call' : 'chat'}"/></svg><p>No ${escapeHtml(state.view)} found.</p></div></div>`;
  const nextHtml = requests + conversations;
  if (state.lastListHtml === nextHtml) return;
  state.lastListHtml = nextHtml;
  $('list').innerHTML = nextHtml;
  document.querySelectorAll('[data-conversation]').forEach((button) => button.onclick = () => openConversation(state.conversations.find((item) => conversationId(item) === button.dataset.conversation)));
  document.querySelectorAll('[data-request-action]').forEach((button) => button.onclick = () => respondConnectionRequest(button.dataset.requestId, button.dataset.requestAction));
}

function connectionRequestMarkup(request) {
  const user = request.user || {};
  return `<article class="connection-request"><span class="avatar">${avatarMarkup(user.displayName || user.username, user.avatarUrl)}</span><span class="row-copy"><strong>${escapeHtml(user.displayName || user.username || 'Mowell user')}</strong><span>@${escapeHtml(user.username || '')} wants to connect</span></span><span class="request-actions"><button class="secondary" data-request-id="${escapeHtml(request.id)}" data-request-action="decline">Decline</button><button class="primary" data-request-id="${escapeHtml(request.id)}" data-request-action="accept">Accept</button></span></article>`;
}

async function respondConnectionRequest(id, action) {
  const buttons = [...document.querySelectorAll(`[data-request-id="${CSS.escape(id)}"]`)];
  buttons.forEach((button) => setLoading(button, true));
  try {
    const result = await api(`/v1/connections/requests/${encodeURIComponent(id)}/respond`, { method: 'POST', body: JSON.stringify({ action }) });
    await loadConversations();
    if (result.accepted && result.conversationId) {
      const conversation = state.conversations.find((item) => conversationId(item) === result.conversationId);
      if (conversation) await openConversation(conversation);
    } else toast('Connection request declined.');
  } catch (error) { toast(error.message); }
}

async function renderPeople(query) {
  if (query.length < 2) {
    $('list').innerHTML = '<div class="list-empty"><div><svg><use href="#i-search"/></svg><p>Search by username to find someone.</p></div></div>';
    return;
  }
  $('list').innerHTML = '<div class="list-empty"><div class="button-spinner"></div></div>';
  try {
    const users = (await api(`/v1/users/search?q=${encodeURIComponent(query)}`)).users || [];
    $('list').innerHTML = users.length ? users.map((user) => `<button class="conversation-row person-open" data-user="${escapeHtml(user.id)}"><span class="avatar">${avatarMarkup(user.displayName, user.avatarUrl)}</span><span class="row-copy"><strong>${escapeHtml(user.displayName)}</strong><span>@${escapeHtml(user.username)}</span></span><span class="row-meta">Connect</span></button>`).join('') : '<div class="list-empty"><p>No username matched.</p></div>';
    document.querySelectorAll('.person-open').forEach((button) => button.onclick = () => createDirect(users.find((user) => user.id === button.dataset.user)));
  } catch (error) { $('list').innerHTML = `<div class="list-empty"><p>${escapeHtml(error.message)}</p></div>`; }
}

async function createDirect(user) {
  if (!user) return;
  try {
    const data = await api('/v1/connections/requests', { method: 'POST', body: JSON.stringify({ userId: user.id }) });
    if (!data.connected) { $('new-dialog').close(); toast(data.message || 'Connection request sent.'); return; }
    await loadConversations();
    const conversation = state.conversations.find((item) => conversationId(item) === data.conversationId);
    $('new-dialog').close(); if (conversation) await openConversation(conversation);
  } catch (error) { toast(error.message); }
}

async function openConversation(conversation) {
  if (!conversation) return;
  state.active = conversation;
  state.lastMessagesSignature = '';
  const title = displayTitle(conversation);
  $('chat-title').textContent = title;
  $('chat-status').textContent = conversation.isGroup ? `${conversation.members?.length || 0} members` : 'Mowell conversation';
  $('chat-avatar').classList.toggle('group', Boolean(conversation.isGroup));
  $('chat-avatar').innerHTML = avatarMarkup(title, avatarUrl(conversation));
  $('empty-pane').classList.add('hidden');
  $('chat-pane').classList.remove('hidden');
  renderList();
  await loadMessages();
  $('message-input').focus();
}

async function loadMessages({ preserve = false } = {}) {
  if (!state.active) return;
  try {
    const data = await api(`/v1/conversations/${conversationId(state.active)}/messages?markRead=true`);
    const before = state.messages.at(-1)?._id;
    const clearedAt = Number(localStorage.getItem(`mowell_clear_${conversationId(state.active)}`) || 0);
    const nextMessages = (data.messages || []).filter((message) => new Date(message.sentAt || message.createdAt || 0).getTime() > clearedAt);
    const signature = nextMessages.map((message) => `${message._id || message.id}:${message.body}:${message.kind}:${message.readBy?.length || 0}`).join('|');
    state.messages = nextMessages;
    state.active._lastMessage = state.messages.at(-1);
    if (signature !== state.lastMessagesSignature) {
      state.lastMessagesSignature = signature;
      renderMessages(preserve && before === state.messages.at(-1)?._id);
    }
    renderList();
  } catch (error) { toast(error.message); }
}

function parseBody(message) {
  if (!['location', 'contact', 'call', 'call_end'].includes(message.kind)) return { text: message.body };
  try { return JSON.parse(message.body); } catch { return { text: message.body }; }
}

function mediaMarkup(message) {
  const attachment = message.attachment;
  const id = userId(attachment?._id || attachment);
  const mime = attachment?.mimeType || '';
  if (!id) return `<p class="message-text">${escapeHtml(message.body)}</p>`;
  if (message.kind === 'image') return `<button class="media-loader" data-media="${escapeHtml(id)}" data-kind="image" aria-label="Open photo"><span>Loading photo…</span></button>`;
  if (message.kind === 'video') return `<div class="media-loader" data-media="${escapeHtml(id)}" data-kind="video"><span>Loading video…</span></div>`;
  if (message.kind === 'audio') return `<div class="media-loader" data-media="${escapeHtml(id)}" data-kind="audio"><span>Loading voice message…</span></div>`;
  return `<a class="file-card media-loader" data-media="${escapeHtml(id)}" data-kind="file" data-name="${escapeHtml(attachment?.fileName || message.body || 'Document')}" href="#"><svg><use href="#i-paperclip"/></svg><span>${escapeHtml(attachment?.fileName || message.body || 'Document')}</span></a>`;
}

function contentMarkup(message) {
  const data = parseBody(message);
  if (message.kind === 'sticker') return `<span class="sticker-message" aria-label="Animated sticker">${escapeHtml(message.body)}</span>`;
  if (['image', 'video', 'audio', 'file'].includes(message.kind)) return mediaMarkup(message);
  if (message.kind === 'location') {
    const lat = Number(data.latitude), lon = Number(data.longitude);
    return `<a class="location-card" href="https://www.google.com/maps?q=${lat},${lon}" target="_blank" rel="noopener"><span class="location-map"><svg><use href="#i-location"/></svg></span><b>Shared location</b></a>`;
  }
  if (message.kind === 'contact') return `<div class="file-card"><svg><use href="#i-users"/></svg><span><b>${escapeHtml(data.name || 'Contact')}</b><br>${escapeHtml(data.number || '')}</span></div>`;
  if (message.kind === 'call') return `<button type="button" class="call-message" data-call-room="${escapeHtml(data.room || '')}" data-call-video="${Boolean(data.video)}" data-call-group="${Boolean(data.group)}"><b>${data.video ? 'Video' : 'Voice'} call started</b><span>Tap to open call</span></button>`;
  if (message.kind === 'call_end') return `<p class="message-text">Call ended${data.reason ? ` · ${escapeHtml(String(data.reason).replaceAll('_', ' '))}` : ''}</p>`;
  return `${message.reply ? `<div class="reply-quote"><b>${escapeHtml(message.reply.sender || 'Reply')}</b><span>${escapeHtml(message.reply.body || '')}</span></div>` : ''}<p class="message-text">${escapeHtml(message.body)}</p>`;
}

function renderMessages(preserveScroll = false) {
  const container = $('messages');
  const nearBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 110;
  let previousDay = '';
  container.innerHTML = state.messages.map((message) => {
    const date = new Date(message.sentAt || message.createdAt || Date.now()).toDateString();
    const day = date !== previousDay ? `<div class="day-badge">${escapeHtml(dayLabel(message.sentAt || message.createdAt))}</div>` : '';
    previousDay = date;
    const outgoing = senderId(message) === state.me.id;
    const system = ['call', 'call_end', 'system'].includes(message.kind);
    const sender = message.sender?.displayName || message.sender?.username || '';
    return `${day}<div class="message-line ${outgoing ? 'outgoing' : ''} ${system ? 'system' : ''}" data-message="${escapeHtml(message._id || message.id || '')}"><div class="bubble">${state.active?.isGroup && !outgoing && !system ? `<span class="sender-name">${escapeHtml(sender)}</span>` : ''}${contentMarkup(message)}<div class="message-meta"><span>${formatTime(message.sentAt || message.createdAt)}</span>${outgoing && !system ? '<span class="ticks">✓✓</span>' : ''}</div></div></div>`;
  }).join('') || '<div class="list-empty"><p>No messages yet. Say hello.</p></div>';
  container.querySelectorAll('.call-message[data-call-room]').forEach((button) => {
    button.onclick = () => openCallMessage(button);
  });
  hydrateMedia();
  if (!preserveScroll || nearBottom) requestAnimationFrame(() => { container.scrollTop = container.scrollHeight; });
}

async function openCallMessage(button) {
  const room = button.dataset.callRoom;
  if (!room || !state.active) return;
  if (!call.closed && call.room === room) {
    $('incoming-call').classList.add('hidden');
    $('call-screen').classList.remove('hidden');
    revealWebCallControls();
    return;
  }
  try {
    await api(`/v1/calls/${encodeURIComponent(room)}/signals`);
    showIncoming(state.active, null, { room, video: button.dataset.callVideo === 'true', group: button.dataset.callGroup === 'true' });
  } catch (error) {
    toast([404, 410].includes(error.status) ? 'This call has already ended.' : error.message);
  }
}

async function hydrateMedia() {
  for (const element of document.querySelectorAll('.media-loader[data-media]')) {
    const id = element.dataset.media;
    element.removeAttribute('data-media');
    try {
      const blob = await api(`/v1/attachments/${id}`, { blob: true });
      const url = URL.createObjectURL(blob);
      state.mediaUrls.add(url);
      if (element.dataset.kind === 'image') {
        const image = document.createElement('img'); image.className = 'message-media'; image.src = url; image.alt = 'Shared photo';
        element.replaceWith(image); image.onclick = () => window.open(url, '_blank', 'noopener');
      } else if (element.dataset.kind === 'video') {
        const video = document.createElement('video'); video.className = 'message-media'; video.src = url; video.controls = true; video.playsInline = true; element.replaceWith(video);
      } else if (element.dataset.kind === 'audio') {
        const audio = document.createElement('audio'); audio.className = 'message-media audio'; audio.src = url; audio.controls = true; element.replaceWith(audio);
      } else {
        element.href = url; element.download = element.dataset.name || 'Mowell attachment';
      }
    } catch { element.textContent = 'Attachment unavailable'; }
  }
}

async function sendMessage(body, kind = 'text') {
  if (!state.active || !body.trim()) return;
  const clientId = `web-${uuid()}`;
  const optimistic = { _id: clientId, clientId, sender: state.me, body: body.trim(), kind, sentAt: new Date().toISOString() };
  state.messages.push(optimistic);
  renderMessages();
  try {
    await api(`/v1/conversations/${conversationId(state.active)}/messages`, { method: 'POST', body: JSON.stringify({ clientId, body: body.trim(), kind }) });
    await loadMessages({ preserve: true });
    await loadConversations();
  } catch (error) {
    state.messages = state.messages.filter((message) => message.clientId !== clientId);
    renderMessages(); toast(error.message);
  }
}

async function submitComposer() {
  const text = $('message-input').value.trim();
  if (!text) return;
  $('message-input').value = '';
  updateComposer();
  await sendMessage(text);
  setTyping(false);
}

async function uploadFile(file) {
  if (!state.active || !file) return;
  if (file.size > 2621440) return toast('Attachments must be 2.5 MB or smaller.');
  toast('Uploading attachment…');
  try {
    const data = await fileToBase64(file);
    await api(`/v1/conversations/${conversationId(state.active)}/attachments`, { method: 'POST', body: JSON.stringify({ clientId: `web-${uuid()}`, fileName: file.name, mimeType: file.type || 'application/octet-stream', data }) });
    await loadMessages(); await loadConversations();
  } catch (error) { toast(error.message); }
}

function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(',')[1] || ''); reader.onerror = reject; reader.readAsDataURL(file);
  });
}

async function shareLocation() {
  if (!state.active || !navigator.geolocation) return toast('Location is not available in this browser.');
  toast('Getting your location…');
  navigator.geolocation.getCurrentPosition(
    ({ coords }) => sendMessage(JSON.stringify({ latitude: coords.latitude, longitude: coords.longitude }), 'location'),
    () => toast('Allow location permission to share your position.'),
    { enableHighAccuracy: true, timeout: 12000 }
  );
}

let recorder = null, recordChunks = [], recordStarted = 0, recordTimer = null;
async function toggleRecording() {
  if (recorder?.state === 'recording') { recorder.stop(); return; }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } });
    recordChunks = [];
    recorder = new MediaRecorder(stream);
    recorder.ondataavailable = (event) => { if (event.data.size) recordChunks.push(event.data); };
    recorder.onstop = async () => {
      clearInterval(recordTimer); $('record-button').classList.remove('recording'); $('record-button').querySelector('.record-time').textContent = '';
      stream.getTracks().forEach((track) => track.stop());
      const type = recorder.mimeType || 'audio/webm';
      await uploadFile(new File([new Blob(recordChunks, { type })], `mowell_voice_${Date.now()}.webm`, { type }));
    };
    recorder.start(250); recordStarted = Date.now(); $('record-button').classList.add('recording');
    recordTimer = setInterval(() => { $('record-button').querySelector('.record-time').textContent = `${Math.floor((Date.now() - recordStarted) / 1000)}s`; }, 250);
  } catch { toast('Allow microphone permission to record a voice message.'); }
}

let typingTimer = null;
function setTyping(active) {
  if (!state.active) return;
  clearTimeout(typingTimer);
  api(`/v1/conversations/${conversationId(state.active)}/typing`, { method: 'POST', body: JSON.stringify({ active }) }).catch(() => {});
  if (active) typingTimer = setTimeout(() => setTyping(false), 4500);
}

async function pollTyping() {
  if (!state.active) return;
  try {
    const data = await api(`/v1/conversations/${conversationId(state.active)}/typing`);
    const users = data.users || [];
    $('typing-indicator').classList.toggle('hidden', !users.length);
    $('typing-indicator').querySelector('b').textContent = users.length ? `${users.join(', ')} typing` : 'typing';
  } catch { $('typing-indicator').classList.add('hidden'); }
}

function updateComposer() {
  const hasText = Boolean($('message-input').value.trim());
  $('send-button').classList.toggle('hidden', !hasText);
  $('record-button').classList.toggle('hidden', hasText);
  $('message-input').style.height = '44px';
  $('message-input').style.height = `${Math.min(130, $('message-input').scrollHeight)}px`;
}

async function searchPeopleDialog() {
  const query = $('people-search').value.trim();
  if (query.length < 2) { $('people-results').innerHTML = '<p class="empty-copy">Type at least two characters.</p>'; return; }
  $('people-results').innerHTML = '<p class="empty-copy">Searching…</p>';
  try {
    const users = (await api(`/v1/users/search?q=${encodeURIComponent(query)}`)).users || [];
    $('people-results').innerHTML = users.length ? users.map((user) => `<div class="person-row"><span class="avatar">${avatarMarkup(user.displayName, user.avatarUrl)}</span><div><strong>${escapeHtml(user.displayName)}</strong><small>@${escapeHtml(user.username)}</small></div><button class="secondary dialog-chat" data-user="${escapeHtml(user.id)}">Connect</button></div>`).join('') : '<p class="empty-copy">No user found.</p>';
    document.querySelectorAll('.dialog-chat').forEach((button) => button.onclick = () => createDirect(users.find((user) => user.id === button.dataset.user)));
  } catch (error) { $('people-results').innerHTML = `<p class="form-error">${escapeHtml(error.message)}</p>`; }
}

function openNewDialog() { $('new-dialog').showModal(); $('people-search').value = ''; searchPeopleDialog(); setTimeout(() => $('people-search').focus(), 50); }
function knownPeople() {
  const map = new Map();
  for (const conversation of state.conversations) for (const member of conversation.members || []) if (userId(member) !== state.me.id) map.set(userId(member), member);
  return [...map.values()];
}
function openGroupDialog() {
  $('new-dialog').close();
  const users = knownPeople();
  $('group-members').innerHTML = users.length ? users.map((user) => `<label class="member-option"><input type="checkbox" value="${escapeHtml(userId(user))}"><span class="avatar">${avatarMarkup(user.displayName, user.avatarUrl)}</span><span><b>${escapeHtml(user.displayName || user.username)}</b><br><small class="muted">@${escapeHtml(user.username || '')}</small></span></label>`).join('') : '<p class="empty-copy">Start a direct conversation before creating a group.</p>';
  $('group-dialog').showModal();
}

async function createGroup(event) {
  event.preventDefault();
  const memberIds = [...document.querySelectorAll('#group-members input:checked')].map((input) => input.value);
  if (!memberIds.length) return setError('group-error', 'Select at least one person.');
  setLoading($('create-group'), true); setError('group-error');
  try {
    const data = await api('/v1/conversations', { method: 'POST', body: JSON.stringify({ title: $('group-name').value.trim(), memberIds, isGroup: true }) });
    $('group-dialog').close(); await loadConversations();
    await openConversation(state.conversations.find((item) => conversationId(item) === conversationId(data.conversation)) || data.conversation);
  } catch (error) { setError('group-error', error.message); }
  finally { setLoading($('create-group'), false); }
}

async function callSignal(type, payload = {}, target = null) {
  return api(`/v1/calls/${encodeURIComponent(call.room)}/signals`, { method: 'POST', body: JSON.stringify({ type, payload, target }) });
}

async function loadIceConfiguration() {
  try {
    const data = await api('/v1/calls/ice-servers');
    if (Array.isArray(data.iceServers) && data.iceServers.length) call.iceServers = data.iceServers;
  } catch {
    call.iceServers = fallbackIceServers;
  }
}

function hdVideoConstraints(facingMode, exact = false) {
  return {
    facingMode: exact ? { exact: facingMode } : { ideal: facingMode },
    width: { ideal: 1280 },
    height: { ideal: 720 },
    aspectRatio: { ideal: 16 / 9 },
    frameRate: { ideal: 24, max: 30 }
  };
}

function updateRemoteVideoLayout() {
  const container = $('remote-videos');
  const videos = [...container.querySelectorAll('video[data-remote-media]')]
    .filter((video) => video.videoWidth > 0 && video.videoHeight > 0);
  const hasVideo = videos.length > 0;
  container.classList.toggle('has-remote-video', hasVideo);
  container.classList.toggle('single-remote-video', videos.length === 1);
  container.querySelector('.call-waiting')?.classList.toggle('hidden', hasVideo);
  container.style.display = videos.length === 1 ? 'block' : 'grid';
  container.querySelectorAll('video[data-remote-media]').forEach((video) => {
    const single = videos.length === 1 && videos[0] === video;
    video.style.position = single ? 'absolute' : '';
    video.style.inset = single ? '0' : '';
  });
  if (hasVideo) $('call-status').textContent = 'Video connected';
}

function revealWebCallControls() {
  const screen = $('call-screen');
  screen.classList.remove('controls-hidden');
  clearTimeout(call.controlsTimer);
  call.controlsTimer = setTimeout(() => screen.classList.add('controls-hidden'), 5000);
}

function setWebPrimary(local) {
  $('call-screen').classList.toggle('local-primary', local);
  revealWebCallControls();
}

async function tuneSender(sender, kind, screen = false) {
  try {
    const parameters = sender.getParameters();
    if (!parameters.encodings?.length) parameters.encodings = [{}];
    if (kind === 'video') {
      parameters.degradationPreference = screen ? 'maintain-resolution' : 'balanced';
      parameters.encodings[0].maxBitrate = screen ? 6000000 : 4000000;
      parameters.encodings[0].maxFramerate = 30;
      delete parameters.encodings[0].scaleResolutionDownBy;
    } else {
      parameters.encodings[0].maxBitrate = 96000;
    }
    await sender.setParameters(parameters);
  } catch { /* Older browsers retain their adaptive defaults. */ }
}

async function upgradeWebVideo() {
  if (call.qualityUpgradeStarted || call.closed || !call.video || call.screenStream) return;
  call.qualityUpgradeStarted = true;
  try {
    const track = call.stream?.getVideoTracks()[0];
    if (!track || track.readyState !== 'live') return;
    await track.applyConstraints({ width: { ideal: 1920, max: 3840 }, height: { ideal: 1080, max: 2160 }, frameRate: { ideal: 30, max: 30 } });
    for (const peer of call.peers.values()) {
      const sender = peer.pc.getSenders().find((item) => item.track?.kind === 'video');
      if (sender) await tuneSender(sender, 'video');
    }
  } catch { /* Keep the already-live HD stream when a camera cannot upgrade. */ }
}

async function makePeer(id, name, shouldOffer) {
  if (call.peers.has(id)) return call.peers.get(id);
  const pc = new RTCPeerConnection({ iceServers: call.iceServers, iceTransportPolicy: 'all' });
  const remote = new MediaStream();
  const entry = { id, name, pc, remote, pending: [], restartAttempts: 0, restartTimer: null, makingOffer: false, needsOffer: false, needsIceRestart: false };
  call.peers.set(id, entry);
  call.stream?.getTracks().forEach((track) => {
    const sender = pc.addTrack(track, call.stream);
    tuneSender(sender, track.kind);
  });
  pc.ontrack = (event) => {
    if (!remote.getTracks().some((track) => track.id === event.track.id)) remote.addTrack(event.track);
    let media = document.getElementById(`remote-${id}`);
    if (media && event.track.kind === 'video' && !(media instanceof HTMLVideoElement)) {
      media.remove();
      media = null;
    }
    if (!media) {
      media = document.createElement(event.track.kind === 'video' || call.video ? 'video' : 'audio');
      media.id = `remote-${id}`; media.dataset.remoteMedia = 'true'; media.autoplay = true; media.playsInline = true;
      if (media instanceof HTMLVideoElement) {
        media.addEventListener('loadeddata', updateRemoteVideoLayout);
        media.addEventListener('playing', updateRemoteVideoLayout);
        media.addEventListener('resize', updateRemoteVideoLayout);
      }
      $('remote-videos').append(media);
    }
    media.srcObject = remote; media.play().catch(() => {});
    event.track.addEventListener('unmute', updateRemoteVideoLayout);
    event.track.addEventListener('ended', updateRemoteVideoLayout);
    $('call-status').textContent = call.video ? 'Call connected — receiving video' : 'Call connected';
  };
  pc.onicecandidate = (event) => { if (event.candidate) callSignal('ice', event.candidate.toJSON(), id).catch(() => {}); };
  pc.oniceconnectionstatechange = () => {
    if (pc.iceConnectionState === 'disconnected') scheduleIceRecovery(entry, 1400);
    if (pc.iceConnectionState === 'failed') scheduleIceRecovery(entry, 100);
  };
  pc.onconnectionstatechange = () => {
    if (pc.connectionState === 'connected') {
      clearTimeout(entry.restartTimer);
      entry.restartAttempts = 0;
      startConnectedTimer();
      upgradeWebVideo();
      $('call-status').textContent = call.video
        ? ($('remote-videos').classList.contains('has-remote-video') ? 'Video connected' : 'Call connected — receiving video')
        : 'Call connected';
    }
    if (pc.connectionState === 'disconnected') scheduleIceRecovery(entry, 1400);
    if (pc.connectionState === 'failed') scheduleIceRecovery(entry, 100);
  };
  if (shouldOffer) await sendOffer(entry);
  return entry;
}

function scheduleIceRecovery(entry, delay) {
  if (call.closed || entry.restartTimer) return;
  if (entry.restartAttempts >= 3) {
    $('call-status').textContent = 'Unable to restore this call';
    toast('The WebRTC connection could not be restored.');
    return;
  }
  $('call-status').textContent = 'Restoring secure connection…';
  entry.restartTimer = setTimeout(async () => {
    entry.restartTimer = null;
    if (call.closed || entry.pc.connectionState === 'connected') return;
    entry.restartAttempts += 1;
    // One deterministic offerer prevents both peers restarting simultaneously.
    if (state.me.id.localeCompare(entry.id) < 0) await sendOffer(entry, true).catch(() => {});
    else await callSignal('join', { video: call.video, recovery: entry.restartAttempts }, entry.id).catch(() => {});
    entry.restartTimer = setTimeout(() => {
      entry.restartTimer = null;
      if (entry.pc.connectionState !== 'connected') scheduleIceRecovery(entry, 100);
    }, 5000);
  }, delay);
}

async function sendOffer(entry, restart = false) {
  if (entry.makingOffer || entry.pc.signalingState !== 'stable') {
    entry.needsOffer = true;
    entry.needsIceRestart = entry.needsIceRestart || restart;
    return;
  }
  try {
    entry.makingOffer = true;
    await entry.pc.setLocalDescription(await entry.pc.createOffer(restart ? { iceRestart: true } : undefined));
    await callSignal('offer', { type: entry.pc.localDescription.type, sdp: entry.pc.localDescription.sdp }, entry.id);
  } finally {
    entry.makingOffer = false;
  }
  if (entry.needsOffer && entry.pc.signalingState === 'stable') {
    const needsRestart = entry.needsIceRestart;
    entry.needsOffer = false;
    entry.needsIceRestart = false;
    await sendOffer(entry, needsRestart);
  }
}

async function receiveSignal(signal) {
  if (signal.senderId === state.me.id) return;
  if (signal.type === 'join') {
    const peer = await makePeer(signal.senderId, signal.senderName, false);
    if (state.me.id.localeCompare(signal.senderId) < 0) await sendOffer(peer).catch(() => {});
  } else if (signal.type === 'offer') {
    const peer = await makePeer(signal.senderId, signal.senderName, false);
    if (peer.pc.signalingState === 'have-local-offer') await peer.pc.setLocalDescription({ type: 'rollback' }).catch(() => {});
    if (peer.pc.signalingState !== 'stable') return;
    await peer.pc.setRemoteDescription(signal.payload);
    for (const candidate of peer.pending.splice(0)) await peer.pc.addIceCandidate(candidate).catch(() => {});
    await peer.pc.setLocalDescription(await peer.pc.createAnswer());
    await callSignal('answer', { type: peer.pc.localDescription.type, sdp: peer.pc.localDescription.sdp }, signal.senderId);
    if (peer.needsOffer) {
      const needsRestart = peer.needsIceRestart;
      peer.needsOffer = false; peer.needsIceRestart = false;
      await sendOffer(peer, needsRestart).catch(() => {});
    }
  } else if (signal.type === 'answer') {
    const peer = await makePeer(signal.senderId, signal.senderName, false);
    if (peer.pc.signalingState === 'have-local-offer') {
      await peer.pc.setRemoteDescription(signal.payload);
      for (const candidate of peer.pending.splice(0)) await peer.pc.addIceCandidate(candidate).catch(() => {});
      if (peer.needsOffer) {
        const needsRestart = peer.needsIceRestart;
        peer.needsOffer = false; peer.needsIceRestart = false;
        await sendOffer(peer, needsRestart).catch(() => {});
      }
    }
  } else if (signal.type === 'ice') {
    const peer = await makePeer(signal.senderId, signal.senderName, false);
    if (peer.pc.remoteDescription) await peer.pc.addIceCandidate(signal.payload).catch(() => {});
    else peer.pending.push(signal.payload);
  } else if (signal.type === 'leave') {
    removePeer(signal.senderId);
    if (!call.conversation?.isGroup) endCall(false);
  } else if (signal.type === 'media') {
    call.video = Boolean(signal.payload?.video);
  }
}

async function pollCall() {
  while (!call.closed) {
    try {
      const suffix = call.lastId ? `?afterId=${encodeURIComponent(call.lastId)}` : '';
      const data = await api(`/v1/calls/${encodeURIComponent(call.room)}/signals${suffix}`);
      for (const signal of data.signals || []) {
        call.lastId = signal.id;
        try { await receiveSignal(signal); } catch { /* A stale signal must not stop newer negotiation data. */ }
      }
    } catch (error) {
      if ([404, 410].includes(error.status)) { endCall(false); return; }
    }
    await sleep(75);
  }
}

function removePeer(id) { const peer = call.peers.get(id); clearTimeout(peer?.restartTimer); peer?.pc.close(); call.peers.delete(id); document.getElementById(`remote-${id}`)?.remove(); updateRemoteVideoLayout(); }
function showIncoming(conversation, message, data) {
  if (!data?.room || (!call.closed && call.room === data.room)) return;
  state.incoming = { conversation, message, data };
  const title = displayTitle(conversation);
  $('incoming-name').textContent = title;
  $('incoming-kind').textContent = `INCOMING ${data.video ? 'VIDEO' : 'VOICE'} CALL`;
  $('incoming-avatar').innerHTML = avatarMarkup(title, avatarUrl(conversation));
  $('accept-call').querySelector('use').setAttribute('href', data.video ? '#i-video' : '#i-call');
  $('incoming-call').classList.remove('hidden');
  document.title = `Incoming ${data.video ? 'video' : 'voice'} call - Mowell`;
  if ('Notification' in window && Notification.permission === 'granted' && document.hidden) {
    try {
      incomingBrowserNotification?.close();
      incomingBrowserNotification = new Notification(`Incoming ${data.video ? 'video' : 'voice'} call`, {
        body: `${title} is calling`, icon: '/mowell_logo.png', tag: `mowell-call-${data.room}`, requireInteraction: true
      });
      incomingBrowserNotification.onclick = () => {
        window.focus();
        incomingBrowserNotification?.close();
        $('incoming-call').classList.remove('hidden');
      };
    } catch { /* The in-page incoming screen remains available. */ }
  }
}

async function startCall(video) {
  if (!state.active || !call.closed) return;
  requestCallNotificationPermission();
  const room = `Mowell-Web-${uuid().replaceAll('-', '')}`;
  await openCall({ room, conversation: state.active, video, initiator: true });
}

async function joinCallRoom(room, conversation, video, initiator) {
  let lastError;
  const attempts = initiator ? 1 : 7;
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      return await api(`/v1/calls/${encodeURIComponent(room)}/join`, { method: 'POST', body: JSON.stringify({ conversationId: conversationId(conversation), video, initiator }) });
    } catch (error) {
      lastError = error;
      if (attempt + 1 < attempts) await sleep(500);
    }
  }
  throw lastError;
}

async function openCall({ room, conversation, video, initiator }) {
  incomingBrowserNotification?.close(); incomingBrowserNotification = null; document.title = 'Mowell Web';
  call.closed = false; call.room = room; call.conversation = conversation; call.video = video; call.lastId = ''; call.facingMode = 'user'; call.screenStream = null; call.qualityUpgradeStarted = false; call.peers.clear();
  $('remote-videos').classList.remove('has-remote-video', 'single-remote-video');
  $('remote-videos').style.display = 'grid'; $('remote-videos').querySelector('.call-waiting')?.classList.remove('hidden');
  $('share-screen-call').classList.remove('active'); $('share-screen-call').querySelector('span').textContent = 'Share';
  $('incoming-call').classList.add('hidden'); $('call-screen').classList.remove('hidden');
  $('call-screen').classList.remove('local-primary', 'controls-hidden'); revealWebCallControls();
  const title = displayTitle(conversation);
  $('call-name').textContent = title; $('call-avatar').innerHTML = avatarMarkup(title, avatarUrl(conversation)); $('call-status').textContent = 'Connecting securely…';
  try {
    const iceConfiguration = loadIceConfiguration();
    const mediaPromise = acquireCallMedia(video);
    const roomPromise = joinCallRoom(room, conversation, video, initiator);
    const ready = await Promise.all([mediaPromise, iceConfiguration, roomPromise]);
    call.stream = ready[0];
    const joined = ready[2];
    const localVideoTrack = call.stream.getVideoTracks()[0];
    if (localVideoTrack) localVideoTrack.contentHint = 'motion';
    $('local-video').srcObject = call.stream; $('local-video').classList.toggle('hidden', !video); $('local-video').classList.remove('rear');
    if (initiator) await sendMessage(JSON.stringify({ room, video, group: Boolean(conversation.isGroup) }), 'call');
    call.startedAt = 0; clearInterval(call.timer); call.timer = null; updateCallTimer();
    pollCall(); await callSignal('join', { video });
    await Promise.all((joined.peers || []).map(async (member) => {
      const peer = await makePeer(member.id, member.name, false);
      if (state.me.id.localeCompare(member.id) < 0) await sendOffer(peer).catch(() => {});
    }));
    setTimeout(() => { if (!call.closed && !call.startedAt) callSignal('join', { video, retry: 1 }).catch(() => {}); }, 900);
    setTimeout(() => { if (!call.closed && !call.startedAt) callSignal('join', { video, retry: 2 }).catch(() => {}); }, 2500);
    clearInterval(call.heartbeatTimer);
    call.heartbeatTimer = setInterval(() => { if (!call.closed) callSignal('heartbeat').catch(() => {}); }, 5000);
    $('call-status').textContent = initiator ? 'Ringing…' : 'Connecting…';
  } catch (error) {
    toast(error.name === 'NotAllowedError' ? 'Allow camera and microphone permission to make calls.' : error.message);
    await endCall(false);
  }
}

async function acquireCallMedia(video) {
  const audio = { echoCancellation: true, noiseSuppression: true, autoGainControl: true };
  if (!video) return navigator.mediaDevices.getUserMedia({ audio, video: false });
  let lastError;
  for (const constraints of [
    hdVideoConstraints(call.facingMode),
    { facingMode: { ideal: call.facingMode } }
  ]) {
    try {
      const media = await navigator.mediaDevices.getUserMedia({ audio, video: constraints });
      const track = media.getVideoTracks()[0];
      if (track?.readyState === 'live') { track.contentHint = 'motion'; return media; }
      media.getTracks().forEach((track) => track.stop());
    } catch (error) { lastError = error; }
  }
  throw lastError || new Error('The camera could not start.');
}

function updateCallTimer() {
  if (!call.startedAt) { $('call-timer').textContent = '00:00'; return; }
  const seconds = Math.max(0, Math.floor((Date.now() - call.startedAt) / 1000));
  $('call-timer').textContent = `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}

function startConnectedTimer() {
  if (call.startedAt) return;
  call.startedAt = Date.now();
  updateCallTimer();
  clearInterval(call.timer);
  call.timer = setInterval(updateCallTimer, 1000);
}

async function endCall(notify = true, reason = 'ended') {
  if (call.closed) return;
  const room = call.room;
  call.closed = true; clearInterval(call.timer); clearInterval(call.heartbeatTimer); call.heartbeatTimer = null; clearTimeout(call.controlsTimer);
  if (notify) await callSignal('leave', { reason }).catch(() => {});
  call.screenStream?.getTracks().forEach((track) => { track.onended = null; track.stop(); }); call.screenStream = null;
  call.stream?.getTracks().forEach((track) => track.stop());
  call.peers.forEach((peer) => { clearTimeout(peer.restartTimer); peer.pc.close(); }); call.peers.clear();
  $('remote-videos').querySelectorAll('[data-remote-media]').forEach((media) => media.remove());
  $('remote-videos').classList.remove('has-remote-video', 'single-remote-video');
  $('remote-videos').style.display = 'grid'; $('remote-videos').querySelector('.call-waiting')?.classList.remove('hidden');
  $('local-video').srcObject = null; $('call-screen').classList.add('hidden'); $('call-screen').classList.remove('local-primary', 'controls-hidden');
  if (state.incoming?.data?.room === room) state.incoming = null;
  if (state.active) loadMessages({ preserve: true }).catch(() => {});
}

function toggleMute() {
  const track = call.stream?.getAudioTracks()[0]; if (!track) return;
  track.enabled = !track.enabled; $('mute-call').classList.toggle('off', !track.enabled); $('mute-call').querySelector('span').textContent = track.enabled ? 'Mute' : 'Unmute';
}
function toggleCamera() {
  if (call.screenStream) return toast('Stop screen sharing before changing the camera.');
  const track = call.stream?.getVideoTracks()[0]; if (!track) return toast('This is a voice call.');
  track.enabled = !track.enabled; $('camera-call').classList.toggle('off', !track.enabled); $('camera-call').querySelector('span').textContent = track.enabled ? 'Camera' : 'Start video';
  callSignal('media', { video: track.enabled }).catch(() => {});
}

async function stopScreenShare(notify = true) {
  const shared = call.screenStream;
  if (!shared) return;
  call.screenStream = null;
  const cameraTrack = call.stream?.getVideoTracks()[0] || null;
  for (const peer of call.peers.values()) {
    const sender = peer.pc.getSenders().find((item) => item.track?.kind === 'video');
    if (sender) await sender.replaceTrack(cameraTrack).catch(() => {});
  }
  shared.getTracks().forEach((track) => { track.onended = null; track.stop(); });
  $('local-video').srcObject = call.stream;
  $('local-video').classList.toggle('hidden', !cameraTrack);
  $('local-video').classList.toggle('rear', call.facingMode === 'environment');
  $('share-screen-call').classList.remove('active');
  $('share-screen-call').querySelector('span').textContent = 'Share';
  if (notify) callSignal('media', { video: Boolean(cameraTrack), screen: false }).catch(() => {});
  toast('Screen sharing stopped.');
}

async function toggleScreenShare() {
  if (call.screenStream) return stopScreenShare();
  if (!navigator.mediaDevices?.getDisplayMedia) return toast('Screen sharing is not supported by this browser.');
  const button = $('share-screen-call');
  setLoading(button, true);
  try {
    const shared = await navigator.mediaDevices.getDisplayMedia({
      video: { width: { ideal: 1920 }, height: { ideal: 1080 }, frameRate: { ideal: 30, max: 30 } },
      audio: false
    });
    const screenTrack = shared.getVideoTracks()[0];
    if (!screenTrack) throw new Error('No screen was selected.');
    screenTrack.contentHint = 'detail';
    call.screenStream = shared;
    call.video = true;
    await callSignal('media', { video: true, screen: true }).catch(() => {});
    for (const peer of call.peers.values()) {
      let sender = peer.pc.getSenders().find((item) => item.track?.kind === 'video');
      if (sender) {
        await sender.replaceTrack(screenTrack);
        await tuneSender(sender, 'video', true);
      } else {
        sender = peer.pc.addTrack(screenTrack, shared);
        await tuneSender(sender, 'video', true);
        await sendOffer(peer);
      }
    }
    $('local-video').srcObject = shared;
    $('local-video').classList.remove('hidden');
    $('local-video').classList.add('rear');
    button.classList.add('active');
    button.querySelector('span').textContent = 'Stop share';
    screenTrack.onended = () => stopScreenShare();
    toast('Your screen is now visible to call members.');
  } catch (error) {
    if (error.name !== 'NotAllowedError') toast(error.message || 'Screen sharing could not start.');
  } finally {
    setLoading(button, false);
  }
}

async function flipCamera() {
  if (call.screenStream) return toast('Stop screen sharing before switching the camera.');
  const oldTrack = call.stream?.getVideoTracks()[0];
  if (!oldTrack) return toast('Start video before flipping the camera.');
  const nextFacing = call.facingMode === 'user' ? 'environment' : 'user';
  try {
    const camera = await navigator.mediaDevices.getUserMedia({ video: hdVideoConstraints(nextFacing, true), audio: false });
    const newTrack = camera.getVideoTracks()[0];
    newTrack.contentHint = 'motion';
    for (const peer of call.peers.values()) {
      const sender = peer.pc.getSenders().find((item) => item.track?.kind === 'video');
      if (sender) { await sender.replaceTrack(newTrack); tuneSender(sender, 'video'); }
    }
    call.stream.removeTrack(oldTrack); oldTrack.stop(); call.stream.addTrack(newTrack);
    call.facingMode = nextFacing;
    $('local-video').classList.toggle('rear', nextFacing === 'environment');
    $('local-video').srcObject = call.stream;
  } catch { toast('Another camera is not available on this device.'); }
}

async function addCallMember() {
  if (call.closed) return;
  const username = prompt('Enter the exact Mowell username to add to this call:')?.trim().replace(/^@/, '').toLowerCase();
  if (!username) return;
  const button = $('add-call-member'); setLoading(button, true);
  try { const result = await api(`/v1/calls/${encodeURIComponent(call.room)}/invite`, { method: 'POST', body: JSON.stringify({ username }) }); toast(`Invited ${result.displayName || username}`); }
  catch (error) { toast(error.message); }
  finally { setLoading(button, false); }
}

function startPolling() {
  if (state.polling) return;
  state.polling = true;
  (async () => {
    while (state.token) {
      try { await loadConversations(); if (state.active) { await loadMessages({ preserve: true }); await pollTyping(); } }
      catch (error) { if (error.status === 401) { clearSession(); break; } }
      await sleep(document.hidden ? 1500 : (state.active ? 250 : 750));
    }
    state.polling = false;
  })();
}

$('login-form').addEventListener('submit', login);
$('register-form').addEventListener('submit', register);
$('verify-form').addEventListener('submit', verifyEmail);
$('reset-form').addEventListener('submit', resetPassword);
$('group-form').addEventListener('submit', createGroup);
$('show-password').onclick = () => { const input = $('password'); input.type = input.type === 'password' ? 'text' : 'password'; };
$('show-register').onclick = () => { $('login-form').classList.add('hidden'); $('register-form').classList.remove('hidden'); };
$('back-login').onclick = () => { $('register-form').classList.add('hidden'); $('login-form').classList.remove('hidden'); };
$('forgot-link').onclick = () => $('reset-dialog').showModal();
$('request-reset').onclick = requestReset;
$('resend-code').onclick = async () => { try { await api('/v1/auth/resend-verification', { method: 'POST', body: JSON.stringify({ email: $('verify-email').value }) }); toast('A new code was requested.'); } catch (error) { setError('verify-error', error.message); } };
document.querySelectorAll('[data-close]').forEach((button) => button.onclick = () => $(button.dataset.close).close());
document.querySelectorAll('.nav-button[data-view]').forEach((button) => button.onclick = () => setView(button.dataset.view));
$('theme-toggle').onclick = () => { document.body.classList.toggle('dark'); localStorage.setItem('mowell_web_theme', document.body.classList.contains('dark') ? 'dark' : 'light'); };
$('account-button').onclick = () => $('account-dialog').showModal();
$('chat-more').onclick = () => {
  if (!state.active) return;
  const title = displayTitle(state.active);
  $('conversation-profile-avatar').innerHTML = avatarMarkup(title, avatarUrl(state.active));
  $('conversation-profile-name').textContent = title;
  $('conversation-profile-kind').textContent = state.active.isGroup ? 'Mowell group conversation' : 'Mowell contact';
  $('conversation-block').classList.toggle('hidden', Boolean(state.active.isGroup));
  $('conversation-dialog').showModal();
};
$('conversation-voice-call').onclick = () => { $('conversation-dialog').close(); startCall(false); };
$('conversation-video-call').onclick = () => { $('conversation-dialog').close(); startCall(true); };
$('conversation-clear').onclick = async () => {
  if (!state.active) return;
  if (!call.closed && conversationId(call.conversation) === conversationId(state.active)) await endCall(true);
  localStorage.setItem(`mowell_clear_${conversationId(state.active)}`, String(Date.now()));
  state.messages = []; renderMessages(false); $('conversation-dialog').close();
};
$('conversation-delete').onclick = () => {
  if (!state.active) return;
  localStorage.setItem(`mowell_hide_${conversationId(state.active)}`, String(new Date(state.active.lastMessageAt || state.active.updatedAt || Date.now()).getTime()));
  const removed = conversationId(state.active); state.active = null; state.conversations = state.conversations.filter((item) => conversationId(item) !== removed); state.lastListHtml = ''; renderList(); $('chat-pane').classList.add('hidden'); $('empty-pane').classList.remove('hidden'); $('conversation-dialog').close();
};
$('conversation-block').onclick = async () => {
  if (!state.active || state.active.isGroup) return;
  await api(`/v1/conversations/${conversationId(state.active)}/block`, { method: 'POST', body: JSON.stringify({ blocked: true }) });
  toast('User blocked'); $('conversation-dialog').close();
};
$('show-web-qr-login').onclick = openWebQr;
$('show-web-qr-account').onclick = () => { $('account-dialog').close(); openWebQr(); };
$('logout-button').onclick = () => clearSession();
$('new-button').onclick = () => state.view === 'groups' ? openGroupDialog() : openNewDialog();
$('empty-new').onclick = openNewDialog;
$('open-group').onclick = openGroupDialog;
$('search-input').addEventListener('input', renderList);
$('people-search').addEventListener('input', () => { clearTimeout(searchPeopleDialog.timer); searchPeopleDialog.timer = setTimeout(searchPeopleDialog, 280); });
document.querySelectorAll('.chip').forEach((button) => button.onclick = () => { state.filter = button.dataset.filter; document.querySelectorAll('.chip').forEach((item) => item.classList.toggle('active', item === button)); renderList(); });
$('chat-back').onclick = () => { $('chat-pane').classList.add('hidden'); state.active = null; renderList(); };
$('send-button').onclick = submitComposer;
$('message-input').addEventListener('input', () => { updateComposer(); setTyping(Boolean($('message-input').value.trim())); });
$('message-input').addEventListener('keydown', (event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); submitComposer(); } });
$('attach-button').onclick = () => $('file-input').click();
$('sticker-button').onclick = () => $('sticker-picker').classList.toggle('hidden');
document.querySelectorAll('#sticker-picker button').forEach((button) => button.onclick = async () => {
  $('sticker-picker').classList.add('hidden');
  await sendMessage(button.textContent, 'sticker');
});
$('file-input').onchange = () => { uploadFile($('file-input').files[0]); $('file-input').value = ''; };
$('location-button').onclick = shareLocation;
$('record-button').onclick = toggleRecording;
$('cancel-reply').onclick = () => { state.reply = null; $('reply-bar').classList.add('hidden'); };
$('audio-call').onclick = () => { requestCallNotificationPermission(); startCall(false); };
$('video-call').onclick = () => { requestCallNotificationPermission(); startCall(true); };
$('decline-call').onclick = async () => { if (state.incoming) { state.dismissedCallRooms.add(state.incoming.data.room); call.room = state.incoming.data.room; call.closed = false; await callSignal('leave', { reason: 'declined' }).catch(() => {}); call.closed = true; } incomingBrowserNotification?.close(); incomingBrowserNotification = null; document.title = 'Mowell Web'; $('incoming-call').classList.add('hidden'); state.incoming = null; };
$('accept-call').onclick = () => { if (!state.incoming) return; const incoming = state.incoming; state.incoming = null; openCall({ room: incoming.data.room, conversation: incoming.conversation, video: Boolean(incoming.data.video), initiator: false }); };
$('hangup-call').onclick = () => endCall(true, 'cancelled');
$('mute-call').onclick = toggleMute;
$('camera-call').onclick = toggleCamera;
$('flip-call').onclick = flipCamera;
$('add-call-member').onclick = addCallMember;
$('share-screen-call').onclick = toggleScreenShare;
$('call-screen').addEventListener('click', revealWebCallControls);
$('local-video').addEventListener('click', (event) => { event.stopPropagation(); setWebPrimary(true); });
$('remote-videos').addEventListener('click', (event) => { event.stopPropagation(); setWebPrimary(false); });
window.addEventListener('beforeunload', () => { call.stream?.getTracks().forEach((track) => track.stop()); state.mediaUrls.forEach((url) => URL.revokeObjectURL(url)); });
document.addEventListener('visibilitychange', () => { if (!document.hidden && state.token) loadConversations().catch(() => {}); });

boot();
