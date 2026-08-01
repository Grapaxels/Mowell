package com.grapaxels.mowell.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grapaxels.mowell.MowellViewModel
import com.grapaxels.mowell.auth.UserProfile
import com.grapaxels.mowell.data.ConversationEntity
import com.grapaxels.mowell.data.MessageEntity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Canvas = Color(0xFFF7F5F0)
private val Ink = Color(0xFF15131A)
private val Violet = Color(0xFF7357F6)
private val VioletDark = Color(0xFF4C31D5)
private val Lavender = Color(0xFFEDE8FF)
private val Lime = Color(0xFFDFFF4F)
private val Peach = Color(0xFFFFE5D7)
private val Muted = Color(0xFF77727F)
private val ClayWhite = Color(0xFFFFFEFB)

private enum class Page { CHATS, PEOPLE, CALLS, NEARBY, YOU }

@Composable
fun MowellApp(vm: MowellViewModel) {
    val scheme = androidx.compose.material3.lightColorScheme(primary = Violet, secondary = Lime, background = Canvas, surface = ClayWhite, onPrimary = Color.White, onBackground = Ink)
    val session by vm.session.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1_500); splash = false }

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            when {
                splash -> SplashScreen()
                session == null -> AuthScreen(vm)
                else -> MainExperience(vm)
            }
        }
        if (!splash && session != null && update != null) {
            val activity = LocalContext.current as? Activity
            AlertDialog(
                onDismissRequest = { if (!update!!.required) vm.dismissUpdate() },
                icon = { Icon(Icons.Rounded.CloudDownload, null, tint = Violet) },
                title = { Text("A fresh Mowell is ready", fontWeight = FontWeight.Bold) },
                text = { Text("Version ${update!!.versionName} can be downloaded here. Android will ask once before replacing the app; your login and SQLite chats stay on the phone.") },
                confirmButton = { Button(onClick = { activity?.let { vm.updater.downloadAndInstall(it, update!!) } }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text("Update now") } },
                dismissButton = { if (!update!!.required) OutlinedButton(onClick = vm::dismissUpdate) { Text("Later") } }
            )
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White, Lavender, Canvas))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OrbLogo(104.dp)
            Spacer(Modifier.height(24.dp))
            Text("Mowell", fontSize = 42.sp, fontWeight = FontWeight.Black, color = Ink)
            Text("communication, made human.", color = Violet, fontStyle = FontStyle.Italic, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
            Text("BY GRAPAXELS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun AuthScreen(vm: MowellViewModel) {
    val busy by vm.authBusy.collectAsStateWithLifecycle()
    val error by vm.authError.collectAsStateWithLifecycle()
    var register by remember { mutableStateOf(false) }
    var identity by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(vm.auth.serverUrl) }
    var googleClientId by remember { mutableStateOf(vm.auth.googleClientId) }
    var advanced by remember { mutableStateOf(vm.auth.serverUrl.contains("example.invalid")) }
    var googleNotice by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        runCatching { GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java).idToken }
            .getOrNull()?.let(vm::googleLogin)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.Center) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OrbLogo(58.dp); Spacer(Modifier.width(14.dp))
                Column { Text("Mowell", fontSize = 31.sp, fontWeight = FontWeight.Black); Text("Your people. Any connection.", color = Violet, fontStyle = FontStyle.Italic) }
            }
            Spacer(Modifier.height(28.dp))
            ClayCard(Lavender) {
                Text(if (register) "Create your identity" else "Welcome back", fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text(if (register) "Choose a searchable @username." else "Your session stays here until you log out.", color = Muted)
                Spacer(Modifier.height(18.dp))
                if (register) {
                    ClayField(displayName, { displayName = it }, "Display name")
                    ClayField(username, { username = it.lowercase().filter { char -> char.isLetterOrDigit() || char == '_' } }, "Username")
                    ClayField(email, { email = it }, "Email")
                } else ClayField(identity, { identity = it }, "Email or username")
                ClayField(password, { password = it }, "Password", password = true)
                if (error != null) Text(error!!, color = Color(0xFFB3261E), fontSize = 13.sp, modifier = Modifier.padding(vertical = 7.dp))
                Button(
                    onClick = { if (register) vm.register(email, username, displayName, password) else vm.login(identity, password) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) { if (busy) CircularProgressIndicator(Modifier.size(21.dp), color = Lime, strokeWidth = 2.dp) else Text(if (register) "Create Mowell account" else "Enter Mowell", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    if (googleClientId.isBlank()) googleNotice = true
                    else {
                        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestIdToken(googleClientId).requestEmail().build()
                        googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                    }
                }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(20.dp)) { Text("G  Continue with Google", fontWeight = FontWeight.SemiBold) }
                TextButtonLine(if (register) "Already a member? Sign in" else "New here? Create an account") { register = !register }
            }
            Spacer(Modifier.height(14.dp))
            ClayCard(ClayWhite) {
                TextButtonLine(if (advanced) "Hide server setup" else "Server setup") { advanced = !advanced }
                AnimatedVisibility(advanced) {
                    Column {
                        Text("Central MongoDB API URL", fontSize = 12.sp, color = Muted)
                        ClayField(serverUrl, { serverUrl = it }, "https://api.yourdomain.com")
                        Text("Google web client ID", fontSize = 12.sp, color = Muted)
                        ClayField(googleClientId, { googleClientId = it }, "…apps.googleusercontent.com")
                        OutlinedButton(onClick = { vm.setServerUrl(serverUrl); vm.auth.googleClientId = googleClientId }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text("Save connection") }
                    }
                }
            }
            if (googleNotice) AlertDialog(
                onDismissRequest = { googleNotice = false },
                title = { Text("Google configuration") },
                text = { Text("Add the same Google web client ID in Server setup and in GOOGLE_CLIENT_ID on the backend. Mowell will then open Google sign-in and send the verified ID token to your server.") },
                confirmButton = { Button(onClick = { googleNotice = false }) { Text("Got it") } }
            )
        }
    }
}

@Composable
private fun MainExperience(vm: MowellViewModel) {
    var page by remember { mutableStateOf(Page.CHATS) }
    var openChat by remember { mutableStateOf<String?>(null) }
    var call by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    when {
        call != null -> CallScreen(call!!.first, call!!.second) { call = null }
        openChat != null -> ChatScreen(vm, openChat!!, { openChat = null }, { video -> call = "Mowell call" to video })
        else -> Scaffold(
            containerColor = Canvas,
            topBar = { ClayHeader(vm) },
            bottomBar = {
                NavigationBar(containerColor = ClayWhite, tonalElevation = 0.dp) {
                    Nav(Page.CHATS, page, "Chats", Icons.Rounded.ChatBubble) { page = it }
                    Nav(Page.PEOPLE, page, "People", Icons.Rounded.Search) { page = it }
                    Nav(Page.CALLS, page, "Calls", Icons.Rounded.Call) { page = it }
                    Nav(Page.NEARBY, page, "Nearby", Icons.Rounded.Bluetooth) { page = it }
                    Nav(Page.YOU, page, "You", Icons.Rounded.Person) { page = it }
                }
            },
            floatingActionButton = { if (page == Page.CHATS) FloatingActionButton(onClick = { page = Page.PEOPLE }, containerColor = Lime, contentColor = Ink, shape = RoundedCornerShape(20.dp)) { Icon(Icons.Rounded.Add, "Find people") } }
        ) { padding ->
            when (page) {
                Page.CHATS -> ChatsScreen(vm, Modifier.padding(padding)) { openChat = it }
                Page.PEOPLE -> PeopleScreen(vm, Modifier.padding(padding)) { user -> vm.startChat(user) { conversationId -> openChat = conversationId } }
                Page.CALLS -> CallsScreen(Modifier.padding(padding)) { name, video -> call = name to video }
                Page.NEARBY -> NearbyScreen(vm, Modifier.padding(padding))
                Page.YOU -> SettingsScreen(vm, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun ClayHeader(vm: MowellViewModel) {
    Row(Modifier.fillMaxWidth().background(Canvas).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        OrbLogo(44.dp); Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) { Text("Mowell", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("by Grapaxels", color = Violet, fontStyle = FontStyle.Italic, fontSize = 12.sp) }
        Row(Modifier.clip(RoundedCornerShape(18.dp)).background(if (vm.networkLabel().startsWith("Internet")) Lime else Peach).padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (vm.networkLabel().startsWith("Internet")) Icons.Rounded.Wifi else Icons.Rounded.Bluetooth, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(if (vm.networkLabel().startsWith("Internet")) "online" else "nearby", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RowScope.Nav(target: Page, selected: Page, label: String, icon: ImageVector, onSelect: (Page) -> Unit) {
    NavigationBarItem(selected = target == selected, onClick = { onSelect(target) }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 9.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = VioletDark, indicatorColor = Lavender))
}

@Composable
private fun ChatsScreen(vm: MowellViewModel, modifier: Modifier, open: (String) -> Unit) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Conversations", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Central identity. Private phone storage. Nearby resilience.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        items(conversations, key = { it.id }) { conversation -> ConversationClay(conversation) { open(conversation.id) } }
    }
}

@Composable
private fun ConversationClay(conversation: ConversationEntity, onClick: () -> Unit) {
    ClayCard(if (conversation.isGroup) Lavender else ClayWhite, Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(conversation.title, 54.dp, if (conversation.isGroup) Violet else Ink)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(conversation.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(conversation.subtitle, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            }
            Text(time(conversation.updatedAt), color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PeopleScreen(vm: MowellViewModel, modifier: Modifier, onUser: (UserProfile) -> Unit) {
    val users by vm.userResults.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Find your people", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Search the central directory by @username.", color = Muted)
            Spacer(Modifier.height(14.dp))
            ClayField(query, { query = it.lowercase(); vm.searchUsers(it) }, "Search username", leading = Icons.Rounded.Search)
        }
        items(users, key = { it.id }) { user ->
            ClayCard(ClayWhite, Modifier.clickable { onUser(user) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user.displayName, 50.dp, Violet); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(user.displayName, fontWeight = FontWeight.Bold); Text("@${user.username}", color = Violet, fontSize = 13.sp) }
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Lime).padding(10.dp)) { Icon(Icons.Rounded.ChatBubble, "Chat", Modifier.size(20.dp)) }
                }
            }
        }
        if (query.length >= 2 && users.isEmpty()) item { Text("No matching cached or online users yet.", color = Muted, modifier = Modifier.padding(10.dp)) }
    }
}

@Composable
private fun CallsScreen(modifier: Modifier, onCall: (String, Boolean) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Calls", fontSize = 30.sp, fontWeight = FontWeight.Black); Text("Adaptive real-time media on internet. Voice-first nearby mode.", color = Muted) }
        items(listOf("Mowell Circle" to true, "Product crew" to true, "Niya" to false)) { (name, group) ->
            ClayCard(if (group) Lavender else ClayWhite) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name, 52.dp, if (group) Violet else Ink); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(if (group) "Group call" else "Direct call", color = Muted, fontSize = 12.sp) }
                    IconButton(onClick = { onCall(name, false) }) { Icon(Icons.Rounded.Call, "Voice", tint = Violet) }
                    IconButton(onClick = { onCall(name, true) }) { Icon(Icons.Rounded.Videocam, "Video", tint = Violet) }
                }
            }
        }
        item { ClayCard(Peach) { Text("HD/4K is selected only when bandwidth, camera, CPU and thermal limits allow it. Real-time calls use adaptive WebRTC buffering—not media pre-downloading—so latency stays conversational.", fontSize = 13.sp, color = Ink) } }
    }
}

@Composable
private fun NearbyScreen(vm: MowellViewModel, modifier: Modifier) {
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { vm.bluetooth.startListening() }
    val peers = remember(refresh) { vm.bluetooth.bondedPeers() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Nearby mesh", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Store locally, send to a paired peer, and keep working without internet.", color = Muted)
            Spacer(Modifier.height(8.dp))
            ClayCard(Lavender) { Text("Bitchat-inspired principles: offline-first IDs, duplicate-safe packets, hop limits and store-and-forward-ready routing. This Android 7 build uses reliable paired RFCOMM links; BLE relay expansion remains a separate radio layer.", fontSize = 13.sp) }
        }
        items(peers) { (name, address) ->
            val chosen = vm.selectedPeer == address
            ClayCard(if (chosen) Lime else ClayWhite, Modifier.clickable { vm.selectedPeer = address; refresh++ }) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Bluetooth, null, tint = Violet); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(name.ifBlank { "Nearby device" }, fontWeight = FontWeight.Bold); Text(address, color = Muted, fontSize = 11.sp) }; if (chosen) Text("ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Black) }
            }
        }
        if (peers.isEmpty()) item { ClayCard(Peach) { Text("Pair both phones in Android Bluetooth settings, install Mowell on each, allow Nearby Devices, then refresh."); OutlinedButton(onClick = { refresh++ }, shape = RoundedCornerShape(16.dp)) { Text("Refresh") } } }
    }
}

@Composable
private fun SettingsScreen(vm: MowellViewModel, modifier: Modifier) {
    val session by vm.session.collectAsStateWithLifecycle()
    var server by remember { mutableStateOf(vm.auth.serverUrl) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("You", fontSize = 30.sp, fontWeight = FontWeight.Black); Text("Identity, connection and updates.", color = Muted) }
        item {
            ClayCard(Lavender) {
                Row(verticalAlignment = Alignment.CenterVertically) { Avatar(session?.user?.displayName ?: "M", 62.dp, Violet); Spacer(Modifier.width(13.dp)); Column { Text(session?.user?.displayName ?: "Mowell user", fontWeight = FontWeight.Black, fontSize = 20.sp); Text("@${session?.user?.username}", color = Violet); Text(session?.user?.email.orEmpty(), color = Muted, fontSize = 12.sp) } }
                Spacer(Modifier.height(12.dp)); Text("You stay signed in on this phone until you use Log out below.", fontSize = 12.sp, color = Muted)
            }
        }
        item {
            ClayCard(ClayWhite) {
                Text("Central service", fontWeight = FontWeight.Bold)
                ClayField(server, { server = it }, "https://api.yourdomain.com")
                Button(onClick = { vm.setServerUrl(server) }, shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Save server") }
            }
        }
        item {
            ClayCard(ClayWhite) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.CloudDownload, null, tint = Violet); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("App updates", fontWeight = FontWeight.Bold); Text("Download new APKs inside Mowell", color = Muted, fontSize = 12.sp) }; OutlinedButton(onClick = vm::checkForUpdates, shape = RoundedCornerShape(16.dp)) { Text("Check") } }
            }
        }
        item { OutlinedButton(onClick = vm::logout, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB3261E))) { Icon(Icons.Rounded.Logout, null); Spacer(Modifier.width(8.dp)); Text("Log out on this phone", fontWeight = FontWeight.Bold) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(vm: MowellViewModel, conversationId: String, back: () -> Unit, call: (Boolean) -> Unit) {
    val messages by vm.messages(conversationId).collectAsStateWithLifecycle(initialValue = emptyList())
    val conversation = vm.conversations.collectAsStateWithLifecycle().value.find { it.id == conversationId }
    var text by remember { mutableStateOf("") }
    val send = { vm.send(conversationId, text); text = "" }
    LaunchedEffect(conversationId) {
        while (true) { vm.syncConversation(conversationId); delay(2_000) }
    }
    Scaffold(
        containerColor = Canvas,
        topBar = {
            Row(Modifier.fillMaxWidth().background(Canvas).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Avatar(conversation?.title ?: "M", 42.dp, Violet); Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) { Text(conversation?.title ?: "Conversation", fontWeight = FontWeight.Black); Text(vm.networkLabel(), color = Muted, fontSize = 10.sp) }
                IconButton(onClick = { call(false) }) { Icon(Icons.Rounded.Call, "Voice", tint = Violet) }
                IconButton(onClick = { call(true) }) { Icon(Icons.Rounded.Videocam, "Video", tint = Violet) }
            }
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(Canvas).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(value = text, onValueChange = { text = it }, placeholder = { Text("Write something…") }, modifier = Modifier.weight(1f).shadow(6.dp, RoundedCornerShape(22.dp)), shape = RoundedCornerShape(22.dp), colors = TextFieldDefaults.colors(focusedContainerColor = ClayWhite, unfocusedContainerColor = ClayWhite, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }))
                Spacer(Modifier.width(8.dp)); IconButton(onClick = send, Modifier.size(54.dp).clip(RoundedCornerShape(20.dp)).background(Lime)) { Icon(Icons.Rounded.Send, "Send", tint = Ink) }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(messages, key = { it.id }) { MessageClay(it) }
        }
    }
}

@Composable
private fun MessageClay(message: MessageEntity) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start) {
        Box(Modifier.fillMaxWidth(.80f).shadow(5.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(if (message.outgoing) Violet else ClayWhite).border(1.dp, Color.White.copy(alpha = .7f), RoundedCornerShape(18.dp)).padding(12.dp)) {
            Column {
                Text(message.body, color = if (message.outgoing) Color.White else Ink)
                Row(Modifier.align(Alignment.End)) { Text(time(message.sentAt), color = if (message.outgoing) Color.White.copy(alpha = .7f) else Muted, fontSize = 9.sp); Spacer(Modifier.width(5.dp)); Text(route(message.route), color = if (message.outgoing) Lime else Violet, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun CallScreen(name: String, video: Boolean, end: () -> Unit) {
    var info by remember { mutableStateOf(video) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Violet, VioletDark, Ink)))) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(70.dp)); Avatar(name, 102.dp, Lime); Spacer(Modifier.height(18.dp)); Text(name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black); Text(if (video) "Adaptive video · connecting" else "Resilient voice · connecting", color = Color.White.copy(alpha = .7f)); Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { CallButton(Icons.Rounded.Mic, "Mute", Color.White.copy(alpha = .15f)) {}; if (video) CallButton(Icons.Rounded.Videocam, "Camera", Color.White.copy(alpha = .15f)) {}; CallButton(Icons.Rounded.CallEnd, "End", Color(0xFFE34855), end) }
            Spacer(Modifier.height(35.dp))
        }
        if (info) AlertDialog(onDismissRequest = { info = false }, title = { Text("Adaptive streaming") }, text = { Text("Mowell will use WebRTC congestion control, jitter buffering and adaptive resolution. Bluetooth cannot carry HD/4K video, so video requires internet or Wi‑Fi.") }, confirmButton = { Button(onClick = { info = false }) { Text("Continue") } })
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, color: Color, click: () -> Unit) { Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = click, Modifier.size(64.dp).clip(CircleShape).background(color)) { Icon(icon, label, tint = Color.White) }; Text(label, color = Color.White, fontSize = 11.sp) } }

@Composable
private fun ClayCard(color: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(26.dp), ambientColor = Violet.copy(alpha = .16f), spotColor = Violet.copy(alpha = .22f)).clip(RoundedCornerShape(26.dp)).background(color).border(1.5.dp, Color.White.copy(alpha = .85f), RoundedCornerShape(26.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
}

@Composable
private fun ClayField(value: String, change: (String) -> Unit, label: String, password: Boolean = false, leading: ImageVector? = null) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, leadingIcon = leading?.let { icon -> { Icon(icon, null, tint = Violet) } }, visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(18.dp))
}

@Composable
private fun TextButtonLine(text: String, click: () -> Unit) { Text(text, color = VioletDark, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 9.dp)) }

@Composable
private fun OrbLogo(size: Dp) { Box(Modifier.size(size).shadow(10.dp, CircleShape, spotColor = Violet.copy(alpha = .45f)).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFB9A8FF), Violet, VioletDark))).border(2.dp, Color.White.copy(alpha = .8f), CircleShape), contentAlignment = Alignment.Center) { Text("M", color = Color.White, fontSize = (size.value * .45f).sp, fontWeight = FontWeight.Black) } }

@Composable
private fun Avatar(name: String, size: Dp, color: Color) { Box(Modifier.size(size).shadow(5.dp, CircleShape).clip(CircleShape).background(color).border(2.dp, Color.White.copy(alpha = .8f), CircleShape), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = if (color == Lime) Ink else Color.White, fontSize = (size.value * .40f).sp, fontWeight = FontWeight.Black) } }

private fun time(timestamp: Long) = if (timestamp == 0L) "now" else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
private fun route(route: String) = when (route) { "INTERNET" -> "INTERNET"; "BLUETOOTH" -> "NEARBY"; "LOCAL_ONLY" -> "SAVED"; else -> route }
