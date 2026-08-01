package com.grapaxels.mowell

import android.Manifest
import android.app.Application
import android.os.Build
import android.os.Bundle
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
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
                    if (Build.VERSION.SDK_INT >= 31) {
                        add(Manifest.permission.BLUETOOTH_SCAN)
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                        add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    } else add(Manifest.permission.ACCESS_FINE_LOCATION)
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

    init {
        bluetooth.startListening()
        viewModelScope.launch {
            dao.upsertConversation(ConversationEntity("general", "Mowell Circle", "Your private online + nearby space", true, System.currentTimeMillis()))
            bluetooth.onMessage = { raw ->
                viewModelScope.launch {
                    val body = raw.substringAfter('|', raw)
                    dao.insertMessage(MessageEntity(UUID.randomUUID().toString(), "general", "Nearby peer", body, System.currentTimeMillis(), false, Route.BLUETOOTH.name, "received"))
                }
            }
            if (_session.value != null) _update.value = updater.check()
        }
    }

    fun messages(conversationId: String) = dao.observeMessages(conversationId)

    fun send(conversationId: String, body: String) {
        if (body.isBlank()) return
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            dao.insertMessage(MessageEntity(id, conversationId, "You", body.trim(), System.currentTimeMillis(), true, "ROUTING", "sending"))
            val result = router.send(conversationId, selectedPeer, "$id|${body.trim().replace('|', ' ')}")
            dao.updateDelivery(id, if (result.delivered) "sent" else "stored", result.route.name)
            val existing = conversations.value.find { it.id == conversationId }
            dao.upsertConversation(ConversationEntity(conversationId, existing?.title ?: "Conversation", body.trim(), existing?.isGroup ?: false, System.currentTimeMillis()))
        }
    }

    fun syncConversation(conversationId: String) {
        if (conversationId.startsWith("user:") || !syncing.add(conversationId)) return
        viewModelScope.launch {
            try {
                val after = syncCursors[conversationId] ?: dao.latestMessageTime(conversationId)
                auth.fetchMessages(conversationId, after).onSuccess { remote ->
                    remote.forEach { message ->
                        dao.insertMessage(MessageEntity(
                            message.id, message.conversationId, if (message.outgoing) "You" else message.sender,
                            message.body, message.sentAt, message.outgoing, Route.INTERNET.name, "sent"
                        ))
                    }
                    remote.maxOfOrNull { it.sentAt }?.let { syncCursors[conversationId] = it }
                }
            } finally { syncing.remove(conversationId) }
        }
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
