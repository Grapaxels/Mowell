package com.grapaxels.mowell

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.location.Location
import android.location.LocationListener
import android.os.Looper
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grapaxels.mowell.auth.AuthRepository
import com.grapaxels.mowell.auth.AuthResult
import com.grapaxels.mowell.auth.AuthSession
import com.grapaxels.mowell.auth.ConnectionRequest
import com.grapaxels.mowell.auth.GroupInvitation
import com.grapaxels.mowell.auth.GroupMemberState
import com.grapaxels.mowell.auth.UserProfile
import com.grapaxels.mowell.call.CallCoordinator
import com.grapaxels.mowell.data.CachedUser
import com.grapaxels.mowell.data.ConversationEntity
import com.grapaxels.mowell.data.MessageEntity
import com.grapaxels.mowell.network.AppUpdater
import com.grapaxels.mowell.network.MessageNotifier
import com.grapaxels.mowell.network.MessageSyncService
import com.grapaxels.mowell.network.NotificationPreferences
import com.grapaxels.mowell.network.UpdateInfo
import com.grapaxels.mowell.transport.BluetoothTransport
import com.grapaxels.mowell.transport.Route
import com.grapaxels.mowell.transport.TransportRouter
import com.grapaxels.mowell.ui.MowellApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.content.FileProvider

data class CallSession(
    val conversationId: String,
    val name: String,
    val room: String,
    val video: Boolean,
    val group: Boolean = false,
    val initiator: Boolean = false,
    val avatarUrl: String? = null
)

class MainActivity : ComponentActivity() {
    private val mowellViewModel: MowellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm = mowellViewModel
            val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                if (result.values.any { it }) vm.bluetooth.startListening()
            }
            LaunchedEffect(Unit) {
                val requested = buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    add(Manifest.permission.CAMERA)
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.READ_CONTACTS)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    if (Build.VERSION.SDK_INT >= 31) {
                        add(Manifest.permission.BLUETOOTH_SCAN)
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                        add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    }
                }
                permissions.launch(requested.toTypedArray())
            }
            MowellApp(vm)
        }
        if (intent.getBooleanExtra("show_update", false)) mowellViewModel.checkForUpdates(showPopup = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("show_update", false)) mowellViewModel.checkForUpdates(showPopup = true)
    }

    override fun onResume() {
        super.onResume()
        mowellViewModel.resumeUpdateInstall(this)
        mowellViewModel.autoCheckForUpdates()
    }
}

class MowellViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as MowellApplication).database.dao()
    val auth = AuthRepository(application)
    val updater = AppUpdater(application, auth)
    private val notifier = MessageNotifier(application)
    val bluetooth = BluetoothTransport(application)
    private val router = TransportRouter(application, bluetooth)
    private val chatLocks = application.getSharedPreferences("mowell_chat_locks", Context.MODE_PRIVATE)
    private val hiddenMessages = application.getSharedPreferences("mowell_hidden_messages", Context.MODE_PRIVATE)
    private val accountState = application.getSharedPreferences("mowell_local_account", Context.MODE_PRIVATE)
    private var voiceRecorder: MediaRecorder? = null
    private var voiceRecordingFile: File? = null
    val conversations = dao.observeConversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val chatLists = dao.observeChatLists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var selectedPeer: String? = null

    private val _session = MutableStateFlow(auth.savedSession)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()
    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()
    private val _verificationEmail = MutableStateFlow<String?>(null)
    val verificationEmail: StateFlow<String?> = _verificationEmail.asStateFlow()
    private val _passwordResetStatus = MutableStateFlow<String?>(null)
    val passwordResetStatus: StateFlow<String?> = _passwordResetStatus.asStateFlow()
    private val _userResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val userResults: StateFlow<List<UserProfile>> = _userResults.asStateFlow()
    private val _connections = MutableStateFlow<List<UserProfile>>(emptyList())
    val connections: StateFlow<List<UserProfile>> = _connections.asStateFlow()
    private val _connectionRequests = MutableStateFlow<List<ConnectionRequest>>(emptyList())
    val connectionRequests: StateFlow<List<ConnectionRequest>> = _connectionRequests.asStateFlow()
    private val _groupInvitations = MutableStateFlow<List<GroupInvitation>>(emptyList())
    val groupInvitations: StateFlow<List<GroupInvitation>> = _groupInvitations.asStateFlow()
    private val _groupMemberStates = MutableStateFlow<Map<String, GroupMemberState>>(emptyMap())
    val groupMemberStates: StateFlow<Map<String, GroupMemberState>> = _groupMemberStates.asStateFlow()
    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()
    private val _showUpdatePopup = MutableStateFlow(false)
    val showUpdatePopup: StateFlow<Boolean> = _showUpdatePopup.asStateFlow()
    private val _updateStatus = MutableStateFlow("Ready to check")
    val updateStatus: StateFlow<String> = _updateStatus.asStateFlow()
    private val _updateDownloading = MutableStateFlow(false)
    val updateDownloading: StateFlow<Boolean> = _updateDownloading.asStateFlow()
    private val _typingUsers = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val typingUsers: StateFlow<Map<String, List<String>>> = _typingUsers.asStateFlow()
    private val _voiceRecording = MutableStateFlow(false)
    val voiceRecording: StateFlow<Boolean> = _voiceRecording.asStateFlow()
    private val typingJobs = ConcurrentHashMap<String, Job>()
    private val syncCursors = ConcurrentHashMap<String, Long>()
    private val syncing = ConcurrentHashMap.newKeySet<String>()
    private val syncingAll = AtomicBoolean(false)
    private val updateCheckRunning = AtomicBoolean(false)
    @Volatile private var lastAutomaticUpdateCheckAt = 0L
    @Volatile private var initialSyncComplete = false

    init {
        bluetooth.startListening()
        autoCheckForUpdates()
        viewModelScope.launch {
            _session.value?.user?.id?.let { prepareLocalAccount(it) } ?: ensureGeneralConversation()
            bluetooth.onMessage = { raw ->
                viewModelScope.launch {
                    val packet = runCatching { JSONObject(raw) }.getOrNull()
                    val body = packet?.optString("body") ?: raw.substringAfter('|', raw)
                    val kind = packet?.optString("kind", "text") ?: "text"
                    val message = MessageEntity(packet?.optString("clientId").takeUnless { it.isNullOrBlank() } ?: UUID.randomUUID().toString(), "general", "Nearby peer", body, System.currentTimeMillis(), false, Route.BLUETOOTH.name, "received", kind)
                    dao.insertMessage(message)
                    val current = conversations.value.find { it.id == "general" }
                    dao.upsertConversation(current?.copy(subtitle = preview(message), updatedAt = message.sentAt) ?: ConversationEntity("general", "Mowell Circle", preview(message), true, message.sentAt))
                    val unread = dao.incrementUnread("general")
                    notifier.show("Mowell Circle", message, unread)
                }
            }
            if (_session.value != null) {
                val validation = auth.validateSession()
                when {
                    validation.session != null -> _session.value = validation.session
                    validation.verificationEmail != null -> { _session.value = null; _verificationEmail.value = validation.verificationEmail; _authError.value = validation.error }
                    else -> Unit
                }
                refreshSocial()
            }
        }
    }

    fun messages(conversationId: String) = dao.observeMessages(conversationId)

    fun createChatList(name: String, conversationIds: Set<String>) {
        viewModelScope.launch { dao.createChatList(name, conversationIds) }
    }

    fun markConversationRead(conversationId: String) {
        viewModelScope.launch {
            dao.markRead(conversationId)
            notifier.clearConversation(conversationId)
        }
    }

    fun send(conversationId: String, body: String) {
        sendTyped(conversationId, body, "text")
    }

    fun sendReply(conversationId: String, body: String, reply: MessageEntity) {
        val preview = reply.body.replace('\n', ' ').take(90)
        sendTyped(conversationId, "↩ ${reply.sender}: $preview\n${body.trim()}", "text")
    }

    private fun sendTyped(conversationId: String, body: String, kind: String) {
        if (body.isBlank()) return
        if (conversations.value.find { it.id == conversationId }?.blocked == true) {
            _authError.value = "Messaging is unavailable because this contact is blocked"
            return
        }
        updateTyping(conversationId, false)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val trimmedBody = body.trim()
        val message = MessageEntity(id, conversationId, "You", trimmedBody, now, true, "ROUTING", "sending", kind)
        // Update the open chat synchronously so the send action feels instant;
        // database persistence and transport acknowledgement never block the UI.
        dao.stageOutgoingMessage(message)
        viewModelScope.launch {
            dao.insertMessage(message)
            val existing = conversations.value.find { it.id == conversationId }
            dao.upsertConversation(existing?.copy(subtitle = preview(message), updatedAt = now) ?: ConversationEntity(conversationId, "Conversation", preview(message), false, now))
            val payload = JSONObject().put("clientId", id).put("body", trimmedBody).put("kind", kind).toString()
            val result = router.send(conversationId, selectedPeer, payload)
            dao.updateDelivery(id, if (result.delivered) "sent" else "stored", result.route.name)
            if (kind == "text" && NotificationPreferences.sendSound(getApplication())) playSendTone()
        }
    }

    fun updateTyping(conversationId: String, active: Boolean) {
        if (conversationId.startsWith("user:")) return
        typingJobs.remove(conversationId)?.cancel()
        if (!active) {
            viewModelScope.launch { auth.setTyping(conversationId, false) }
            return
        }
        typingJobs[conversationId] = viewModelScope.launch {
            auth.setTyping(conversationId, true)
            delay(4_500)
            auth.setTyping(conversationId, false)
            typingJobs.remove(conversationId)
        }
    }

    fun refreshTyping(conversationId: String) {
        if (conversationId.startsWith("user:")) return
        viewModelScope.launch {
            auth.fetchTyping(conversationId).onSuccess { users ->
                _typingUsers.value = _typingUsers.value.toMutableMap().apply { put(conversationId, users) }
            }
        }
    }

    private suspend fun playSendTone() {
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 90)
            delay(120)
            tone.release()
        }
    }

    fun syncConversation(conversationId: String) {
        if (conversationId.startsWith("user:") || !syncing.add(conversationId)) return
        viewModelScope.launch {
            try {
                syncConversationInternal(conversationId, conversations.value.find { it.id == conversationId }?.title ?: "Mowell", false)
            } finally { syncing.remove(conversationId) }
        }
    }

    private suspend fun syncAllConversations() {
        if (!syncingAll.compareAndSet(false, true)) return
        try {
            val remoteConversations = auth.fetchConversations().getOrNull() ?: return
            val notify = initialSyncComplete
            dao.retainSyncedConversations(remoteConversations.map { it.id }.toSet())
            remoteConversations.forEach { remote ->
                val existing = dao.getConversation(remote.id)
                dao.upsertConversation(ConversationEntity(remote.id, remote.title, existing?.subtitle ?: "Start chatting", remote.isGroup, remote.updatedAt, remote.username, remote.avatarUrl, remote.lastSeenAt, remote.members, existing?.unreadCount ?: 0, remote.blocked, remote.blockedByMe))
                syncConversationInternal(remote.id, remote.title, notify)
            }
            initialSyncComplete = true
        } finally { syncingAll.set(false) }
    }

    private suspend fun syncConversationInternal(conversationId: String, title: String, notify: Boolean) {
        val after = syncCursors[conversationId] ?: 0L
        auth.fetchMessages(conversationId, after).onSuccess { remote ->
            remote.forEach { item ->
                if (hiddenMessages.getStringSet(item.conversationId, emptySet()).orEmpty().contains(item.id)) return@forEach
                val isNew = !dao.hasMessage(item.id)
                val message = MessageEntity(
                    item.id, item.conversationId, if (item.outgoing) "You" else item.sender,
                    item.body, item.sentAt, item.outgoing, Route.INTERNET.name, "sent", item.kind,
                    item.attachmentId, item.attachmentMime, item.attachmentName
                )
                dao.insertMessage(message)
                if (isNew && !item.outgoing) dao.revealConversationOnIncoming(conversationId, item.sentAt)
                if (notify && isNew && !item.outgoing) {
                    if (item.kind == "call_end") notifier.clearConversation(conversationId)
                    else {
                        val unread = dao.incrementUnread(conversationId)
                        notifier.show(title, message, unread, conversations.value.find { it.id == conversationId }?.avatarUrl)
                    }
                }
                if (!item.outgoing && item.kind == "call_end") {
                    runCatching { JSONObject(item.body).optString("room") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { CallCoordinator.endIfActive(getApplication(), it) }
                }
            }
            remote.maxOfOrNull { it.sentAt }?.let { syncCursors[conversationId] = maxOf(syncCursors[conversationId] ?: 0L, it) }
            remote.lastOrNull()?.let { last ->
                val message = MessageEntity(last.id, last.conversationId, last.sender, last.body, last.sentAt, last.outgoing, Route.INTERNET.name, "sent", last.kind, last.attachmentId, last.attachmentMime, last.attachmentName)
                val current = dao.getConversation(conversationId)
                dao.upsertConversation(current?.copy(subtitle = preview(message), updatedAt = last.sentAt) ?: ConversationEntity(conversationId, title, preview(message), false, last.sentAt))
            }
        }
    }

    fun createCall(conversationId: String, name: String, video: Boolean): CallSession {
        val room = "Mowell-${UUID.randomUUID().toString().replace("-", "")}"
        val group = conversations.value.find { it.id == conversationId }?.isGroup ?: false
        sendTyped(conversationId, JSONObject().put("room", room).put("video", video).put("group", group).toString(), "call")
        val avatar = conversations.value.find { it.id == conversationId }?.avatarUrl
        return CallSession(conversationId, name, room, video, group, initiator = true, avatarUrl = avatar)
    }

    fun launchCall(context: Context, session: CallSession) = CallCoordinator.launch(context, session)

    fun shareLocation(conversationId: String) {
        viewModelScope.launch {
            val manager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = try {
                if (androidx.core.content.ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) null
                else {
                    val cached = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                        .maxByOrNull { it.time }
                    // A stale or empty cache made location sharing look broken on many phones.
                    // Ask Android for one fresh fix first, then fall back to a recent cached fix.
                    currentLocation(manager) ?: cached
                }
            } catch (_: SecurityException) { null }
            if (location == null) { _authError.value = "Location is unavailable. Turn on location and try again."; return@launch }
            sendTyped(conversationId, JSONObject().put("latitude", location.latitude).put("longitude", location.longitude).toString(), "location")
        }
    }

    private suspend fun currentLocation(manager: LocationManager): Location? = withTimeoutOrNull(12_000) {
        suspendCancellableCoroutine { continuation ->
            val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            if (provider == null) { continuation.resume(null) {}; return@suspendCancellableCoroutine }
            lateinit var listener: LocationListener
            listener = LocationListener { location ->
                if (continuation.isActive) continuation.resume(location) {}
                manager.removeUpdates(listener)
            }
            try {
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            } catch (_: SecurityException) { if (continuation.isActive) continuation.resume(null) {} }
        }
    }

    fun shareContact(conversationId: String, uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val contact = runCatching {
                resolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val id = cursor.getString(0)
                    val name = cursor.getString(1) ?: "Contact"
                    val number = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id), null)?.use { phones -> if (phones.moveToFirst()) phones.getString(0) else "" }.orEmpty()
                    name to number
                }
            }.getOrNull() ?: return@launch
            sendTyped(conversationId, JSONObject().put("name", contact.first).put("phone", contact.second).toString(), "contact")
        }
    }

    fun uploadAttachment(conversationId: String, uri: Uri) {
        if (conversations.value.find { it.id == conversationId }?.blocked == true) {
            _authError.value = "Messaging is unavailable because this contact is blocked"
            return
        }
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@launch
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "attachment"
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val id = UUID.randomUUID().toString()
            val kind = if (mime.startsWith("image/")) "image" else if (mime.startsWith("video/")) "video" else if (mime.startsWith("audio/")) "audio" else "file"
            val now = System.currentTimeMillis()
            dao.insertMessage(MessageEntity(id, conversationId, "You", name, now, true, "INTERNET", "uploading", kind, null, mime, name))
            val existing = conversations.value.find { it.id == conversationId }
            dao.upsertConversation(existing?.copy(subtitle = "Uploading $name", updatedAt = now) ?: ConversationEntity(conversationId, "Conversation", "Uploading $name", false, now))
            auth.uploadAttachment(conversationId, id, name, mime, bytes).onSuccess { item ->
                dao.insertMessage(MessageEntity(item.id, conversationId, "You", item.body, item.sentAt, true, Route.INTERNET.name, "sent", item.kind, item.attachmentId, item.attachmentMime, item.attachmentName))
                val attachmentPreview = preview(MessageEntity(item.id, conversationId, "You", item.body, item.sentAt, true, Route.INTERNET.name, "sent", item.kind))
                dao.upsertConversation(existing?.copy(subtitle = attachmentPreview, updatedAt = item.sentAt) ?: ConversationEntity(conversationId, "Conversation", attachmentPreview, false, item.sentAt))
            }.onFailure { dao.updateDelivery(id, it.message ?: "upload failed", Route.LOCAL_ONLY.name) }
        }
    }

    fun startVoiceRecording(conversationId: String) {
        if (_voiceRecording.value || conversations.value.find { it.id == conversationId }?.blocked == true) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _authError.value = "Microphone permission is required to record a voice message"
            return
        }
        runCatching {
            val directory = File(getApplication<Application>().cacheDir, "voice").apply { mkdirs() }
            val file = File(directory, "mowell_voice_${System.currentTimeMillis()}.m4a")
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(48_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            voiceRecordingFile = file
            voiceRecorder = recorder
            _voiceRecording.value = true
        }.onFailure {
            voiceRecorder?.release()
            voiceRecorder = null
            voiceRecordingFile = null
            _voiceRecording.value = false
            _authError.value = "Could not start voice recording"
        }
    }

    fun stopVoiceRecording(conversationId: String, send: Boolean = true) {
        val recorder = voiceRecorder ?: return
        val file = voiceRecordingFile
        val completed = runCatching { recorder.stop() }.isSuccess
        recorder.release()
        voiceRecorder = null
        voiceRecordingFile = null
        _voiceRecording.value = false
        if (completed && send && file != null && file.length() > 0) {
            val uri = FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.files", file)
            uploadAttachment(conversationId, uri)
        } else if (file != null) {
            file.delete()
            if (send) _authError.value = "Voice recording was too short"
        }
    }

    fun openAttachment(context: Context, message: MessageEntity) {
        val id = message.attachmentId ?: return
        context.startActivity(Intent(context, MowellAttachmentActivity::class.java)
            .putExtra(MowellAttachmentActivity.EXTRA_ID, id)
            .putExtra(MowellAttachmentActivity.EXTRA_MIME, message.attachmentMime)
            .putExtra(MowellAttachmentActivity.EXTRA_NAME, message.attachmentName ?: message.body))
    }

    fun openContact(context: Context, name: String, phone: String) {
        context.startActivity(Intent(context, MowellAttachmentActivity::class.java)
            .putExtra(MowellAttachmentActivity.EXTRA_CONTACT, true)
            .putExtra(MowellAttachmentActivity.EXTRA_NAME, name)
            .putExtra(MowellAttachmentActivity.EXTRA_PHONE, phone))
    }

    fun deleteMessage(message: MessageEntity, everyone: Boolean) {
        viewModelScope.launch {
            if (!everyone) {
                val hidden = hiddenMessages.getStringSet(message.conversationId, emptySet()).orEmpty().toMutableSet().apply { add(message.id) }
                hiddenMessages.edit().putStringSet(message.conversationId, hidden).apply()
                dao.deleteMessage(message.id)
                return@launch
            }
            auth.deleteMessage(message.conversationId, message.id, true).onSuccess {
                dao.insertMessage(message.copy(body = "This message was deleted", kind = "system", sentAt = System.currentTimeMillis(), attachmentId = null, attachmentMime = null, attachmentName = null))
            }.onFailure { _authError.value = it.message ?: "Message could not be deleted" }
        }
    }

    fun floatingNotifications() = NotificationPreferences.floating(getApplication())
    fun setFloatingNotifications(enabled: Boolean) = NotificationPreferences.setFloating(getApplication(), enabled)
    fun sendSoundEnabled() = NotificationPreferences.sendSound(getApplication())
    fun setSendSoundEnabled(enabled: Boolean) = NotificationPreferences.setSendSound(getApplication(), enabled)
    fun setMessageSound(uri: Uri) = NotificationPreferences.setMessageSound(getApplication(), uri.toString())
    fun setCallSound(uri: Uri) = NotificationPreferences.setCallSound(getApplication(), uri.toString())
    fun setConversationSound(conversationId: String, uri: Uri) = NotificationPreferences.setConversationSound(getApplication(), conversationId, uri.toString())

    private fun preview(message: MessageEntity): String = when (message.kind) {
        "image" -> "Photo"
        "video" -> "Video"
        "audio" -> "Audio"
        "file" -> message.attachmentName ?: "File"
        "location" -> "Location"
        "contact" -> "Contact"
        "call" -> if (message.body.contains("\"video\":true")) "Video call" else "Voice call"
        "call_end" -> "Call ended"
        else -> message.body
    }

    fun networkLabel(): String = if (router.hasInternet()) "Internet ready" else "Offline · Bluetooth fallback"
    fun setServerUrl(url: String) { auth.serverUrl = url; _authError.value = null }
    fun login(identity: String, password: String) = authenticate { auth.login(identity, password) }
    fun register(email: String, username: String, displayName: String, password: String) = authenticate { auth.register(email, username, displayName, password) }
    fun googleLogin(idToken: String) = authenticate { auth.google(idToken) }

    private fun authenticate(block: suspend () -> AuthResult) {
        viewModelScope.launch {
            _authBusy.value = true; _authError.value = null
            val result = block()
            _authBusy.value = false
            if (result.session != null) {
                prepareLocalAccount(result.session.user.id)
                _session.value = result.session
                MessageSyncService.start(getApplication())
                _verificationEmail.value = null
                refreshUpdate(showPopup = true)
                refreshSocial()
            }
            else {
                if (!result.verificationEmail.isNullOrBlank()) _verificationEmail.value = result.verificationEmail
                _authError.value = result.error
            }
        }
    }

    fun verifyEmail(code: String) {
        val email = _verificationEmail.value ?: return
        authenticate { auth.verifyEmail(email, code) }
    }

    fun resendVerification() {
        val email = _verificationEmail.value ?: return
        viewModelScope.launch {
            _authBusy.value = true
            val result = auth.resendVerification(email)
            _authBusy.value = false
            _authError.value = result.error
        }
    }

    fun cancelVerification() { _verificationEmail.value = null; _authError.value = null }

    fun requestPasswordReset(email: String, onSent: () -> Unit) {
        viewModelScope.launch {
            _authBusy.value = true; _passwordResetStatus.value = null
            auth.requestPasswordReset(email).onSuccess { message -> _passwordResetStatus.value = message; onSent() }
                .onFailure { _passwordResetStatus.value = it.message ?: "Could not send reset code" }
            _authBusy.value = false
        }
    }

    fun resetPassword(email: String, code: String, password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _authBusy.value = true; _passwordResetStatus.value = null
            auth.resetPassword(email, code, password).onSuccess { message -> _passwordResetStatus.value = message; onDone() }
                .onFailure { _passwordResetStatus.value = it.message ?: "Could not update password" }
            _authBusy.value = false
        }
    }

    fun clearPasswordResetStatus() { _passwordResetStatus.value = null }

    fun searchUsers(query: String) {
        if (query.length < 2) { _userResults.value = emptyList(); return }
        viewModelScope.launch {
            val remote = auth.searchUsers(query)
            if (remote.isSuccess) {
                val users = remote.getOrDefault(emptyList())
                _userResults.value = users
                dao.cacheUsers(users.map { CachedUser(it.id, it.username, it.displayName, it.avatarUrl) })
            } else {
                _userResults.value = dao.searchCachedUsers(query).map { UserProfile(it.id, it.username, "", it.displayName, it.avatarUrl) }
                _authError.value = "Showing cached people while offline"
            }
        }
    }

    fun refreshSocial() {
        if (_session.value == null) return
        viewModelScope.launch {
            auth.fetchConnections().onSuccess { _connections.value = it }
            auth.fetchConnectionRequests().onSuccess { _connectionRequests.value = it }
            auth.fetchGroupInvitations().onSuccess { _groupInvitations.value = it }
        }
    }

    fun sendConnectionRequest(user: UserProfile) {
        viewModelScope.launch {
            auth.sendConnectionRequest(user.id).onSuccess {
                _authError.value = "Connection request sent to @${user.username}"
                refreshSocial()
            }.onFailure { _authError.value = it.message ?: "Could not send connection request" }
        }
    }

    fun respondConnectionRequest(requestId: String, accept: Boolean) {
        viewModelScope.launch {
            auth.respondConnectionRequest(requestId, accept).onSuccess {
                refreshSocial()
                if (accept) syncAllConversations()
            }.onFailure { _authError.value = it.message ?: "Could not update connection request" }
        }
    }

    fun respondGroupInvitation(invitationId: String, accept: Boolean) {
        viewModelScope.launch {
            auth.respondGroupInvitation(invitationId, accept).onSuccess {
                refreshSocial()
                if (accept) syncAllConversations()
            }.onFailure { _authError.value = it.message ?: "Could not update group invitation" }
        }
    }

    fun createGroup(title: String, memberIds: Set<String>, inviteIds: Set<String>, onReady: (String) -> Unit) {
        viewModelScope.launch {
            auth.createGroup(title, memberIds, inviteIds).onSuccess { conversationId ->
                dao.upsertConversation(ConversationEntity(conversationId, title.trim(), "Group created", true, System.currentTimeMillis()))
                syncAllConversations()
                onReady(conversationId)
            }.onFailure { _authError.value = it.message ?: "Could not create group" }
        }
    }

    fun addGroupMembers(conversationId: String, memberIds: Set<String>, inviteIds: Set<String>, onDone: () -> Unit) {
        viewModelScope.launch {
            auth.addGroupMembers(conversationId, memberIds, inviteIds).onSuccess {
                syncAllConversations(); refreshGroupMembers(conversationId); onDone()
            }.onFailure { _authError.value = it.message ?: "Could not add group members" }
        }
    }

    fun refreshGroupMembers(conversationId: String) {
        viewModelScope.launch {
            auth.fetchGroupMembers(conversationId).onSuccess { state ->
                _groupMemberStates.value = _groupMemberStates.value.toMutableMap().apply { put(conversationId, state) }
            }
        }
    }

    fun setGroupAdmin(conversationId: String, userId: String, admin: Boolean) {
        viewModelScope.launch {
            auth.setGroupAdmin(conversationId, userId, admin).onSuccess { refreshGroupMembers(conversationId) }
                .onFailure { _authError.value = it.message ?: "Could not change admin role" }
        }
    }

    fun removeGroupMember(conversationId: String, userId: String) {
        viewModelScope.launch {
            auth.removeGroupMember(conversationId, userId).onSuccess { refreshGroupMembers(conversationId); syncAllConversations() }
                .onFailure { _authError.value = it.message ?: "Could not remove group member" }
        }
    }

    fun hideConversation(conversationId: String) {
        viewModelScope.launch {
            dao.hideConversation(conversationId)
            notifier.clearConversation(conversationId)
        }
    }

    fun leaveGroup(conversationId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            auth.leaveGroup(conversationId).onSuccess {
                dao.deleteConversation(conversationId)
                _groupMemberStates.value = _groupMemberStates.value - conversationId
                syncAllConversations()
                onDone()
            }.onFailure { _authError.value = it.message ?: "Could not exit group" }
        }
    }

    fun deleteGroup(conversationId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            auth.deleteGroup(conversationId).onSuccess {
                dao.deleteConversation(conversationId)
                _groupMemberStates.value = _groupMemberStates.value - conversationId
                syncAllConversations()
                onDone()
            }.onFailure { _authError.value = it.message ?: "Could not delete group" }
        }
    }

    fun startChat(user: UserProfile, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val existing = conversations.value.find { it.username.equals(user.username, ignoreCase = true) }
            if (existing != null) { onReady(existing.id); return@launch }
            auth.sendConnectionRequest(user.id).onSuccess { refreshSocial() }
                .onFailure { _authError.value = it.message ?: "Could not send connection request" }
        }
    }

    fun addUser(user: UserProfile) {
        if (conversations.value.any { it.username.equals(user.username, ignoreCase = true) }) return
        sendConnectionRequest(user)
    }

    fun setUserBlocked(conversationId: String, blocked: Boolean) {
        viewModelScope.launch {
            auth.setBlocked(conversationId, blocked).onSuccess { state ->
                conversations.value.find { it.id == conversationId }?.let { dao.upsertConversation(it.copy(blocked = state.first, blockedByMe = state.second)) }
                if (blocked) {
                    updateTyping(conversationId, false)
                    notifier.clearConversation(conversationId)
                }
            }.onFailure { _authError.value = it.message ?: "Could not update blocked user" }
        }
    }

    fun logout() {
        auth.logout()
        _session.value = null
        _userResults.value = emptyList()
        _connections.value = emptyList()
        _connectionRequests.value = emptyList()
        _groupInvitations.value = emptyList()
        _groupMemberStates.value = emptyMap()
        _typingUsers.value = emptyMap()
        syncCursors.clear()
        notifier.clearAll()
        MessageSyncService.stop(getApplication())
        accountState.edit().remove("owner_id").apply()
        viewModelScope.launch {
            dao.clearAccountData()
            ensureGeneralConversation()
        }
    }

    private suspend fun prepareLocalAccount(userId: String) {
        val previousOwner = accountState.getString("owner_id", null)
        if (previousOwner != userId) {
            dao.clearAccountData()
            hiddenMessages.edit().clear().apply()
            chatLocks.edit().clear().apply()
            syncCursors.clear()
            notifier.clearAll()
        }
        accountState.edit().putString("owner_id", userId).apply()
        ensureGeneralConversation()
    }

    private suspend fun ensureGeneralConversation() {
        val general = conversations.value.find { it.id == "general" }
        dao.upsertConversation(general ?: ConversationEntity("general", "Mowell Circle", "Your private online + nearby space", true, System.currentTimeMillis()))
    }
    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            val result = auth.updateDisplayName(name)
            if (result.session != null) _session.value = result.session else _authError.value = result.error
        }
    }
    fun updateProfilePicture(uri: Uri) {
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { compressedImage(uri) }.fold(onSuccess = { auth.updateAvatar(it) }, onFailure = { AuthResult(error = it.message) })
            }
            if (result.session != null) _session.value = result.session else _authError.value = result.error
        }
    }

    fun updateGroupName(conversationId: String, title: String) {
        viewModelScope.launch {
            auth.updateGroupTitle(conversationId, title).onSuccess { syncAllConversations() }
                .onFailure { _authError.value = it.message ?: "Could not update group name" }
        }
    }

    fun updateGroupPicture(conversationId: String, uri: Uri) {
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { compressedImage(uri) }.fold(
                    onSuccess = { auth.updateGroupAvatar(conversationId, it) },
                    onFailure = { Result.failure<String>(it) }
                )
            }
            result.onSuccess { syncAllConversations() }.onFailure { _authError.value = it.message ?: "Could not update group icon" }
        }
    }

    /** Saves a private alias for a direct contact; no server request is made. */
    fun setLocalContactName(conversationId: String, name: String) {
        viewModelScope.launch {
            val conversation = dao.getConversation(conversationId) ?: return@launch
            if (!conversation.isGroup) dao.upsertConversation(conversation.copy(localTitle = name.trim().take(80).ifBlank { null }))
        }
    }

    private fun compressedImage(uri: Uri): ByteArray {
        val bitmap = getApplication<Application>().contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            ?: error("Could not read image")
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > 1024) {
            val ratio = 1024f / longest
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else bitmap
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 84, output)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return output.toByteArray()
    }

    fun isChatLocked(conversationId: String) = chatLocks.contains(conversationId)
    fun verifyChatPasscode(conversationId: String, passcode: String): Boolean {
        val stored = chatLocks.getString(conversationId, null)?.split(':') ?: return true
        if (stored.size != 2) return false
        return passcodeHash(conversationId, passcode, stored[0]) == stored[1]
    }
    fun setChatPasscode(conversationId: String, passcode: String?) {
        if (passcode.isNullOrBlank()) chatLocks.edit().remove(conversationId).apply()
        else {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            chatLocks.edit().putString(conversationId, "$salt:${passcodeHash(conversationId, passcode, salt)}").apply()
        }
    }
    private fun passcodeHash(conversationId: String, passcode: String, salt: String) =
        MessageDigest.getInstance("SHA-256").digest("$conversationId:$salt:$passcode".toByteArray()).joinToString("") { "%02x".format(it) }
    fun dismissUpdate() { _showUpdatePopup.value = false }
    fun checkForUpdates() = checkForUpdates(showPopup = false)
    fun checkForUpdates(showPopup: Boolean) { viewModelScope.launch { refreshUpdate(showPopup) } }
    fun autoCheckForUpdates() {
        val now = System.currentTimeMillis()
        if (now - lastAutomaticUpdateCheckAt < 5 * 60 * 1000L) return
        if (!updateCheckRunning.compareAndSet(false, true)) return
        lastAutomaticUpdateCheckAt = now
        viewModelScope.launch {
            try { refreshUpdate(showPopup = true) }
            finally { updateCheckRunning.set(false) }
        }
    }
    fun installUpdate(activity: Activity) {
        val available = _update.value ?: return
        if (!BuildConfig.SELF_UPDATE) {
            updater.install(activity, File(""))
            return
        }
        if (_updateDownloading.value) return
        viewModelScope.launch {
            _updateDownloading.value = true
            _updateStatus.value = "Starting secure download…"
            updater.download(available) { progress ->
                _updateStatus.value = if (progress >= 0) "Downloading update… $progress%" else "Downloading update…"
            }.onSuccess { file ->
                _updateStatus.value = "Download verified. Opening Android installer…"
                updater.install(activity, file)
            }.onFailure { error ->
                _updateStatus.value = "Could not download update: ${error.message ?: "unknown error"}"
            }
            _updateDownloading.value = false
        }
    }
    fun resumeUpdateInstall(activity: Activity) = updater.resumePendingInstall(activity)

    private suspend fun refreshUpdate(showPopup: Boolean) {
        _updateStatus.value = "Checking for updates…"
        val found = updater.check()
        _update.value = found
        _updateStatus.value = found?.let { "Version ${it.versionName} is available" } ?: "Mowell is up to date"
        if (showPopup && found != null) _showUpdatePopup.value = true
    }
}
