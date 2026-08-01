package com.grapaxels.mowell

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.grapaxels.mowell.auth.AuthRepository
import com.grapaxels.mowell.auth.AuthResult
import com.grapaxels.mowell.auth.AuthSession
import com.grapaxels.mowell.auth.UserProfile
import com.grapaxels.mowell.data.CachedUser
import com.grapaxels.mowell.data.ConversationEntity
import com.grapaxels.mowell.data.MessageEntity
import com.grapaxels.mowell.network.AppUpdater
import com.grapaxels.mowell.network.MessageNotifier
import com.grapaxels.mowell.network.UpdateInfo
import com.grapaxels.mowell.transport.BluetoothTransport
import com.grapaxels.mowell.transport.Route
import com.grapaxels.mowell.transport.TransportRouter
import com.grapaxels.mowell.ui.MowellApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.content.FileProvider

data class CallSession(val conversationId: String, val name: String, val room: String, val video: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MowellViewModel = viewModel()
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
    }
}

class MowellViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as MowellApplication).database.dao()
    val auth = AuthRepository(application)
    val updater = AppUpdater(application, auth)
    private val notifier = MessageNotifier(application)
    val bluetooth = BluetoothTransport(application)
    private val router = TransportRouter(application, bluetooth)
    val conversations = dao.observeConversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var selectedPeer: String? = null

    private val _session = MutableStateFlow(auth.savedSession)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()
    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()
    private val _userResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val userResults: StateFlow<List<UserProfile>> = _userResults.asStateFlow()
    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()
    private val syncCursors = ConcurrentHashMap<String, Long>()
    private val syncing = ConcurrentHashMap.newKeySet<String>()
    private val syncingAll = AtomicBoolean(false)
    @Volatile private var initialSyncComplete = false

    init {
        bluetooth.startListening()
        viewModelScope.launch {
            dao.upsertConversation(ConversationEntity("general", "Mowell Circle", "Your private online + nearby space", true, System.currentTimeMillis()))
            bluetooth.onMessage = { raw ->
                viewModelScope.launch {
                    val packet = runCatching { JSONObject(raw) }.getOrNull()
                    val body = packet?.optString("body") ?: raw.substringAfter('|', raw)
                    val kind = packet?.optString("kind", "text") ?: "text"
                    val message = MessageEntity(packet?.optString("clientId").takeUnless { it.isNullOrBlank() } ?: UUID.randomUUID().toString(), "general", "Nearby peer", body, System.currentTimeMillis(), false, Route.BLUETOOTH.name, "received", kind)
                    dao.insertMessage(message)
                    dao.upsertConversation(ConversationEntity("general", "Mowell Circle", preview(message), true, message.sentAt))
                    notifier.show("Mowell Circle", message)
                }
            }
            if (_session.value != null) _update.value = updater.check()
        }
        viewModelScope.launch {
            while (isActive) {
                if (_session.value != null) syncAllConversations()
                delay(1_000)
            }
        }
    }

    fun messages(conversationId: String) = dao.observeMessages(conversationId)

    fun send(conversationId: String, body: String) {
        sendTyped(conversationId, body, "text")
    }

    private fun sendTyped(conversationId: String, body: String, kind: String) {
        if (body.isBlank()) return
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val message = MessageEntity(id, conversationId, "You", body.trim(), now, true, "ROUTING", "sending", kind)
            dao.insertMessage(message)
            val existing = conversations.value.find { it.id == conversationId }
            dao.upsertConversation(ConversationEntity(conversationId, existing?.title ?: "Conversation", preview(message), existing?.isGroup ?: false, now))
            val payload = JSONObject().put("clientId", id).put("body", body.trim()).put("kind", kind).toString()
            val result = router.send(conversationId, selectedPeer, payload)
            dao.updateDelivery(id, if (result.delivered) "sent" else "stored", result.route.name)
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
            remoteConversations.forEach { remote ->
                val existing = conversations.value.find { it.id == remote.id }
                dao.upsertConversation(ConversationEntity(remote.id, remote.title, existing?.subtitle ?: "Start chatting", remote.isGroup, remote.updatedAt))
                syncConversationInternal(remote.id, remote.title, notify)
            }
            initialSyncComplete = true
        } finally { syncingAll.set(false) }
    }

    private suspend fun syncConversationInternal(conversationId: String, title: String, notify: Boolean) {
        val after = syncCursors[conversationId] ?: 0L
        auth.fetchMessages(conversationId, after).onSuccess { remote ->
            remote.forEach { item ->
                val message = MessageEntity(
                    item.id, item.conversationId, if (item.outgoing) "You" else item.sender,
                    item.body, item.sentAt, item.outgoing, Route.INTERNET.name, "sent", item.kind,
                    item.attachmentId, item.attachmentMime, item.attachmentName
                )
                dao.insertMessage(message)
                if (notify && !item.outgoing) notifier.show(title, message)
            }
            remote.maxOfOrNull { it.sentAt }?.let { syncCursors[conversationId] = maxOf(syncCursors[conversationId] ?: 0L, it) }
            remote.lastOrNull()?.let { last ->
                val message = MessageEntity(last.id, last.conversationId, last.sender, last.body, last.sentAt, last.outgoing, Route.INTERNET.name, "sent", last.kind, last.attachmentId, last.attachmentMime, last.attachmentName)
                val current = conversations.value.find { it.id == conversationId }
                dao.upsertConversation(ConversationEntity(conversationId, current?.title ?: title, preview(message), current?.isGroup ?: false, last.sentAt))
            }
        }
    }

    fun createCall(conversationId: String, name: String, video: Boolean): CallSession {
        val room = "Mowell-${UUID.randomUUID().toString().replace("-", "")}"
        sendTyped(conversationId, JSONObject().put("room", room).put("video", video).toString(), "call")
        return CallSession(conversationId, name, room, video)
    }

    fun shareLocation(conversationId: String) {
        viewModelScope.launch {
            val manager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = try {
                if (androidx.core.content.ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) null
                else listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }.maxByOrNull { it.time }
            } catch (_: SecurityException) { null }
            if (location == null) return@launch
            sendTyped(conversationId, JSONObject().put("latitude", location.latitude).put("longitude", location.longitude).toString(), "location")
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
            dao.upsertConversation(ConversationEntity(conversationId, existing?.title ?: "Conversation", "Uploading $name", existing?.isGroup ?: false, now))
            auth.uploadAttachment(conversationId, id, name, mime, bytes).onSuccess { item ->
                dao.insertMessage(MessageEntity(item.id, conversationId, "You", item.body, item.sentAt, true, Route.INTERNET.name, "sent", item.kind, item.attachmentId, item.attachmentMime, item.attachmentName))
                dao.upsertConversation(ConversationEntity(conversationId, existing?.title ?: "Conversation", preview(MessageEntity(item.id, conversationId, "You", item.body, item.sentAt, true, Route.INTERNET.name, "sent", item.kind)), existing?.isGroup ?: false, item.sentAt))
            }.onFailure { dao.updateDelivery(id, it.message ?: "upload failed", Route.LOCAL_ONLY.name) }
        }
    }

    fun openAttachment(context: Context, message: MessageEntity) {
        val id = message.attachmentId ?: return
        viewModelScope.launch {
            auth.downloadAttachment(id).onSuccess { (mime, bytes) ->
                val folder = File(context.cacheDir, "mowell_media").apply { mkdirs() }
                val safeName = (message.attachmentName ?: "attachment").replace(Regex("[^A-Za-z0-9._-]"), "_")
                val file = File(folder, "${id}_$safeName").apply { writeBytes(bytes) }
                val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(contentUri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun preview(message: MessageEntity): String = when (message.kind) {
        "image" -> "Photo"
        "video" -> "Video"
        "audio" -> "Audio"
        "file" -> message.attachmentName ?: "File"
        "location" -> "Location"
        "contact" -> "Contact"
        "call" -> if (message.body.contains("\"video\":true")) "Video call" else "Voice call"
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
            if (result.session != null) { _session.value = result.session; _update.value = updater.check() }
            else _authError.value = result.error
        }
    }

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

    fun startChat(user: UserProfile, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val conversationId = auth.createConversation(user.id).getOrElse { "user:${user.id}" }
            dao.upsertConversation(ConversationEntity(conversationId, user.displayName, "@${user.username}", false, System.currentTimeMillis()))
            onReady(conversationId)
        }
    }

    fun logout() { auth.logout(); _session.value = null; _userResults.value = emptyList() }
    fun dismissUpdate() { _update.value = null }
    fun checkForUpdates() { viewModelScope.launch { _update.value = updater.check() } }
}
