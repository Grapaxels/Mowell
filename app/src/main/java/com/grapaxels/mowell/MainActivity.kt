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
import com.grapaxels.mowell.call.CallCoordinator
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private val chatLocks = application.getSharedPreferences("mowell_chat_locks", Context.MODE_PRIVATE)
    val conversations = dao.observeConversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var selectedPeer: String? = null

    private val _session = MutableStateFlow(auth.savedSession)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()
    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()
    private val _verificationEmail = MutableStateFlow<String?>(null)
    val verificationEmail: StateFlow<String?> = _verificationEmail.asStateFlow()
    private val _userResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val userResults: StateFlow<List<UserProfile>> = _userResults.asStateFlow()
    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()
    private val _showUpdatePopup = MutableStateFlow(false)
    val showUpdatePopup: StateFlow<Boolean> = _showUpdatePopup.asStateFlow()
    private val _updateStatus = MutableStateFlow("Ready to check")
    val updateStatus: StateFlow<String> = _updateStatus.asStateFlow()
    private val syncCursors = ConcurrentHashMap<String, Long>()
    private val syncing = ConcurrentHashMap.newKeySet<String>()
    private val syncingAll = AtomicBoolean(false)
    @Volatile private var initialSyncComplete = false

    init {
        bluetooth.startListening()
        viewModelScope.launch {
            val general = conversations.value.find { it.id == "general" }
            dao.upsertConversation(general ?: ConversationEntity("general", "Mowell Circle", "Your private online + nearby space", true, System.currentTimeMillis()))
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
                    validation.session != null -> { _session.value = validation.session; refreshUpdate(showPopup = true) }
                    validation.verificationEmail != null -> { _session.value = null; _verificationEmail.value = validation.verificationEmail; _authError.value = validation.error }
                    else -> refreshUpdate(showPopup = true)
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                if (_session.value != null) syncAllConversations()
                delay(1_000)
            }
        }
    }

    fun messages(conversationId: String) = dao.observeMessages(conversationId)

    fun markConversationRead(conversationId: String) {
        viewModelScope.launch {
            dao.markRead(conversationId)
            notifier.clearConversation(conversationId)
        }
    }

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
            dao.upsertConversation(existing?.copy(subtitle = preview(message), updatedAt = now) ?: ConversationEntity(conversationId, "Conversation", preview(message), false, now))
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
                dao.upsertConversation(ConversationEntity(remote.id, remote.title, existing?.subtitle ?: "Start chatting", remote.isGroup, remote.updatedAt, remote.username, remote.avatarUrl, remote.lastSeenAt, remote.members, existing?.unreadCount ?: 0))
                syncConversationInternal(remote.id, remote.title, notify)
            }
            initialSyncComplete = true
        } finally { syncingAll.set(false) }
    }

    private suspend fun syncConversationInternal(conversationId: String, title: String, notify: Boolean) {
        val after = syncCursors[conversationId] ?: 0L
        auth.fetchMessages(conversationId, after).onSuccess { remote ->
            remote.forEach { item ->
                val isNew = !dao.hasMessage(item.id)
                val message = MessageEntity(
                    item.id, item.conversationId, if (item.outgoing) "You" else item.sender,
                    item.body, item.sentAt, item.outgoing, Route.INTERNET.name, "sent", item.kind,
                    item.attachmentId, item.attachmentMime, item.attachmentName
                )
                dao.insertMessage(message)
                if (notify && isNew && !item.outgoing) {
                    val unread = dao.incrementUnread(conversationId)
                    notifier.show(title, message, unread, conversations.value.find { it.id == conversationId }?.avatarUrl)
                }
                if (!item.outgoing && item.kind == "call_end") {
                    runCatching { JSONObject(item.body).optString("room") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { CallCoordinator.endIfActive(getApplication(), it) }
                }
            }
            remote.maxOfOrNull { it.sentAt }?.let { syncCursors[conversationId] = maxOf(syncCursors[conversationId] ?: 0L, it) }
            remote.lastOrNull()?.let { last ->
                val message = MessageEntity(last.id, last.conversationId, last.sender, last.body, last.sentAt, last.outgoing, Route.INTERNET.name, "sent", last.kind, last.attachmentId, last.attachmentMime, last.attachmentName)
                val current = conversations.value.find { it.id == conversationId }
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
            dao.upsertConversation(existing?.copy(subtitle = "Uploading $name", updatedAt = now) ?: ConversationEntity(conversationId, "Conversation", "Uploading $name", false, now))
            auth.uploadAttachment(conversationId, id, name, mime, bytes).onSuccess { item ->
                dao.insertMessage(MessageEntity(item.id, conversationId, "You", item.body, item.sentAt, true, Route.INTERNET.name, "sent", item.kind, item.attachmentId, item.attachmentMime, item.attachmentName))
                val attachmentPreview = preview(MessageEntity(item.id, conversationId, "You", item.body, item.sentAt, true, Route.INTERNET.name, "sent", item.kind))
                dao.upsertConversation(existing?.copy(subtitle = attachmentPreview, updatedAt = item.sentAt) ?: ConversationEntity(conversationId, "Conversation", attachmentPreview, false, item.sentAt))
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
            if (result.session != null) { _session.value = result.session; _verificationEmail.value = null; refreshUpdate(showPopup = true) }
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
            dao.upsertConversation(ConversationEntity(conversationId, user.displayName, "@${user.username}", false, System.currentTimeMillis(), user.username, user.avatarUrl))
            onReady(conversationId)
        }
    }

    fun logout() { auth.logout(); _session.value = null; _userResults.value = emptyList() }
    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            val result = auth.updateDisplayName(name)
            if (result.session != null) _session.value = result.session else _authError.value = result.error
        }
    }
    fun updateProfilePicture(uri: Uri) {
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching {
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
                    output.toByteArray()
                }.fold(onSuccess = { auth.updateAvatar(it) }, onFailure = { AuthResult(error = it.message) })
            }
            if (result.session != null) _session.value = result.session else _authError.value = result.error
        }
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
    fun checkForUpdates() { viewModelScope.launch { refreshUpdate(showPopup = false) } }
    fun installUpdate(activity: Activity) { _update.value?.let { updater.downloadAndInstall(activity, it) } }

    private suspend fun refreshUpdate(showPopup: Boolean) {
        _updateStatus.value = "Checking for updates…"
        val found = updater.check()
        _update.value = found
        _updateStatus.value = found?.let { "Version ${it.versionName} is available" } ?: "Mowell is up to date"
        if (showPopup && found != null) _showUpdatePopup.value = true
    }
}
