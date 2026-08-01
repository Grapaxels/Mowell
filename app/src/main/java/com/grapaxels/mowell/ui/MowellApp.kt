package com.grapaxels.mowell.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
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
import com.grapaxels.mowell.CallSession
import com.grapaxels.mowell.BuildConfig
import com.grapaxels.mowell.MowellMapActivity
import com.grapaxels.mowell.auth.UserProfile
import com.grapaxels.mowell.data.ConversationEntity
import com.grapaxels.mowell.data.MessageEntity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.io.File
import kotlinx.coroutines.launch
import org.json.JSONObject
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
    }
}

@Composable
private fun SplashScreen() {
    val reveal = remember { Animatable(0.72f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(280))
        reveal.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White, Lavender, Canvas))), contentAlignment = Alignment.Center) {
        Column(Modifier.graphicsLayer { scaleX = reveal.value; scaleY = reveal.value; alpha = fade.value }, horizontalAlignment = Alignment.CenterHorizontally) {
            OrbLogo(104.dp)
            Spacer(Modifier.height(24.dp))
            Text("Mowell", fontSize = 42.sp, fontWeight = FontWeight.Black, color = Ink)
            Text("by Grapaxels", color = Violet, fontStyle = FontStyle.Italic, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
            Text("BY GRAPAXELS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun AuthScreen(vm: MowellViewModel) {
    val busy by vm.authBusy.collectAsStateWithLifecycle()
    val error by vm.authError.collectAsStateWithLifecycle()
    val resetStatus by vm.passwordResetStatus.collectAsStateWithLifecycle()
    val verificationEmail by vm.verificationEmail.collectAsStateWithLifecycle()
    var register by remember { mutableStateOf(false) }
    var identity by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var resetStep by remember { mutableStateOf(0) }
    var resetEmail by remember { mutableStateOf("") }
    var resetCode by remember { mutableStateOf("") }
    var resetPassword by remember { mutableStateOf("") }

    if (resetStep > 0) {
        AlertDialog(
            onDismissRequest = { if (!busy) { resetStep = 0; vm.clearPasswordResetStatus() } },
            title = { Text(if (resetStep == 1) "Reset password" else "Enter your reset code", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    if (resetStep == 1) {
                        Text("We will send a 6-digit OTP to your registered email.", color = Muted)
                        ClayField(resetEmail, { resetEmail = it }, "Registered email")
                    } else {
                        Text("Code sent to ${maskEmail(resetEmail)}", color = Muted)
                        ClayField(resetCode, { resetCode = it.filter(Char::isDigit).take(6) }, "6-digit OTP")
                        ClayField(resetPassword, { resetPassword = it }, "New password", password = true)
                    }
                    resetStatus?.let { Text(it, color = if (it.contains("sent", true) || it.contains("updated", true)) Violet else Color(0xFFB3261E), fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(enabled = !busy && if (resetStep == 1) resetEmail.contains('@') else resetCode.length == 6 && resetPassword.length >= 8,
                    onClick = {
                        if (resetStep == 1) vm.requestPasswordReset(resetEmail) { resetStep = 2 }
                        else vm.resetPassword(resetEmail, resetCode, resetPassword) { resetStep = 0; password = "" }
                    }) { Text(if (resetStep == 1) "Send OTP" else "Update password") }
            },
            dismissButton = { OutlinedButton(onClick = { resetStep = 0; vm.clearPasswordResetStatus() }) { Text("Cancel") } }
        )
    }

    if (verificationEmail != null) {
        var code by remember(verificationEmail) { mutableStateOf("") }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.Center) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) { OrbLogo(58.dp); Spacer(Modifier.width(14.dp)); Column { Text("Mowell", fontSize = 31.sp, fontWeight = FontWeight.Black); Text("by Grapaxels", color = Violet) } }
                Spacer(Modifier.height(28.dp))
                ClayCard(Lavender) {
                    Text("Verify your email", fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text("We sent a 6-digit code to ${maskEmail(verificationEmail.orEmpty())}. It expires in 10 minutes.", color = Muted)
                    ClayField(code, { code = it.filter(Char::isDigit).take(6) }, "Verification code")
                    if (error != null) Text(error!!, color = if (error!!.contains("sent", true)) Violet else Color(0xFFB3261E), fontSize = 13.sp)
                    Button(onClick = { vm.verifyEmail(code) }, enabled = code.length == 6 && !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(20.dp)) { if (busy) CircularProgressIndicator(Modifier.size(21.dp), color = Color.White, strokeWidth = 2.dp) else Text("Verify and continue") }
                    OutlinedButton(onClick = vm::resendVerification, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text("Resend code") }
                    TextButtonLine("Use a different account", vm::cancelVerification)
                }
            }
        }
        return
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
                if (!register) TextButtonLine("Forgot password?") { resetEmail = identity.takeIf { it.contains('@') }.orEmpty(); resetStep = 1; vm.clearPasswordResetStatus() }
                if (error != null) Text(error!!, color = Color(0xFFB3261E), fontSize = 13.sp, modifier = Modifier.padding(vertical = 7.dp))
                Button(
                    onClick = { if (register) vm.register(email, username, displayName, password) else vm.login(identity, password) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) { if (busy) CircularProgressIndicator(Modifier.size(21.dp), color = Lime, strokeWidth = 2.dp) else Text(if (register) "Create Mowell account" else "Enter Mowell", fontWeight = FontWeight.Bold) }
                TextButtonLine(if (register) "Already a member? Sign in" else "New here? Create an account") { register = !register }
            }
        }
    }
}

@Composable
private fun MainExperience(vm: MowellViewModel) {
    val context = LocalContext.current
    val update by vm.update.collectAsStateWithLifecycle()
    val showUpdate by vm.showUpdatePopup.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(Page.CHATS) }
    var openChat by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<ConversationEntity?>(null) }
    BackHandler {
        when {
            profile != null -> profile = null
            openChat != null -> openChat = null
            page != Page.CHATS -> page = Page.CHATS
            else -> Unit
        }
    }
    if (showUpdate && update != null) {
        AlertDialog(
            onDismissRequest = vm::dismissUpdate,
            title = { Text("Mowell ${update!!.versionName} is available", fontWeight = FontWeight.Black) },
            text = { Text(if (BuildConfig.SELF_UPDATE) "Download and update from inside Mowell. Android will ask once before installing the signed update." else "Install this verified update through Google Play.") },
            confirmButton = { Button(onClick = { (context as? Activity)?.let(vm::installUpdate) }) { Text("Update now") } },
            dismissButton = { OutlinedButton(onClick = vm::dismissUpdate) { Text("Later") } }
        )
    }
    when {
        profile != null -> ProfileScreen(vm, profile!!, { profile = null })
        openChat != null -> ChatScreen(vm, openChat!!, { openChat = null }, { vm.launchCall(context, it) }, { conversation -> profile = conversation })
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
                Page.CALLS -> CallsScreen(vm, Modifier.padding(padding)) { vm.launchCall(context, it) }
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
    val users by vm.userResults.collectAsStateWithLifecycle()
    var peopleQuery by remember { mutableStateOf("") }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Conversations", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Central identity. Private phone storage. Nearby resilience.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            ClayField(peopleQuery, { value -> peopleQuery = value.lowercase(); vm.searchUsers(value) }, "Search people by username", leading = Icons.Rounded.Search)
        }
        if (peopleQuery.length >= 2) {
            items(users, key = { "person-${it.id}" }) { user ->
                val existing = conversations.find { it.username.equals(user.username, ignoreCase = true) }
                ClayCard(ClayWhite) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(user.displayName, 50.dp, Violet, user.avatarUrl); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(user.displayName, fontWeight = FontWeight.Bold); Text("@${user.username}", color = Violet, fontSize = 13.sp) }
                        Button(onClick = { if (existing != null) open(existing.id) else vm.addUser(user) }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
                            Icon(if (existing == null) Icons.Rounded.Add else Icons.Rounded.ChatBubble, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (existing == null) "Add" else "Chat")
                        }
                    }
                }
            }
            if (users.isEmpty()) item { Text("No matching people found.", color = Muted, modifier = Modifier.padding(10.dp)) }
        } else {
            items(conversations, key = { it.id }) { conversation -> ConversationClay(conversation) { open(conversation.id) } }
        }
    }
}

@Composable
private fun ConversationClay(conversation: ConversationEntity, onClick: () -> Unit) {
    ClayCard(if (conversation.isGroup) Lavender else ClayWhite, Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(conversation.title, 54.dp, if (conversation.isGroup) Violet else Ink, conversation.avatarUrl)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(conversation.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(conversation.subtitle, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(time(conversation.updatedAt), color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (conversation.unreadCount > 0) Box(Modifier.padding(top = 6.dp).clip(CircleShape).background(Violet).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(conversation.unreadCount.coerceAtMost(99).toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun PeopleScreen(vm: MowellViewModel, modifier: Modifier, onUser: (UserProfile) -> Unit) {
    val users by vm.userResults.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Find your people", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Search the central directory by @username.", color = Muted)
            Spacer(Modifier.height(14.dp))
            ClayField(query, { query = it.lowercase(); vm.searchUsers(it) }, "Search username", leading = Icons.Rounded.Search)
        }
        items(users, key = { it.id }) { user ->
            val existing = conversations.find { it.username.equals(user.username, ignoreCase = true) }
            ClayCard(ClayWhite) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user.displayName, 50.dp, Violet, user.avatarUrl); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(user.displayName, fontWeight = FontWeight.Bold); Text("@${user.username}", color = Violet, fontSize = 13.sp) }
                    Button(onClick = { if (existing != null) onUser(user) else vm.addUser(user) }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
                        Icon(if (existing == null) Icons.Rounded.Add else Icons.Rounded.ChatBubble, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (existing == null) "Add" else "Chat")
                    }
                }
            }
        }
        if (query.length >= 2 && users.isEmpty()) item { Text("No matching cached or online users yet.", color = Muted, modifier = Modifier.padding(10.dp)) }
    }
}

@Composable
private fun CallsScreen(vm: MowellViewModel, modifier: Modifier, onCall: (CallSession) -> Unit) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Calls", fontSize = 30.sp, fontWeight = FontWeight.Black); Text("Adaptive real-time media on internet. Voice-first nearby mode.", color = Muted) }
        items(conversations.filterNot { it.id == "general" }, key = { it.id }) { conversation ->
            ClayCard(if (conversation.isGroup) Lavender else ClayWhite) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(conversation.title, 52.dp, if (conversation.isGroup) Violet else Ink, conversation.avatarUrl); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(conversation.title, fontWeight = FontWeight.Bold); Text(if (conversation.isGroup) "Group call" else "Direct call", color = Muted, fontSize = 12.sp) }
                    IconButton(onClick = { onCall(vm.createCall(conversation.id, conversation.title, false)) }) { Icon(Icons.Rounded.Call, "Voice", tint = Violet) }
                    IconButton(onClick = { onCall(vm.createCall(conversation.id, conversation.title, true)) }) { Icon(Icons.Rounded.Videocam, "Video", tint = Violet) }
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
    val update by vm.update.collectAsStateWithLifecycle()
    val updateStatus by vm.updateStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editingName by remember { mutableStateOf(false) }
    var floating by remember { mutableStateOf(vm.floatingNotifications()) }
    var sendSound by remember { mutableStateOf(vm.sendSoundEnabled()) }
    var name by remember(session?.user?.displayName) { mutableStateOf(session?.user?.displayName.orEmpty()) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(vm::updateProfilePicture) }
    val messageSoundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let(vm::setMessageSound)
    }
    val callSoundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let(vm::setCallSound)
    }
    if (editingName) {
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Edit your name", fontWeight = FontWeight.Black) },
            text = { Column { ClayField(name, { name = it }, "Display name"); Text("Your @username is permanent and cannot be edited.", color = Muted, fontSize = 12.sp) } },
            confirmButton = { Button(enabled = name.trim().length in 2..60, onClick = { vm.updateDisplayName(name); editingName = false }) { Text("Save") } },
            dismissButton = { OutlinedButton(onClick = { editingName = false }) { Text("Cancel") } }
        )
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("You", fontSize = 30.sp, fontWeight = FontWeight.Black); Text("Identity and privacy.", color = Muted) }
        item {
            ClayCard(Lavender) {
                Row(verticalAlignment = Alignment.CenterVertically) { Avatar(session?.user?.displayName ?: "M", 62.dp, Violet, session?.user?.avatarUrl); Spacer(Modifier.width(13.dp)); Column { Text(session?.user?.displayName ?: "Mowell user", fontWeight = FontWeight.Black, fontSize = 20.sp); Text("@${session?.user?.username}", color = Violet); Text(session?.user?.email.orEmpty(), color = Muted, fontSize = 12.sp) } }
                Spacer(Modifier.height(12.dp)); Text("You stay signed in on this phone until you use Log out below.", fontSize = 12.sp, color = Muted)
                OutlinedButton(onClick = { photoPicker.launch("image/*") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Update profile picture") }
                OutlinedButton(onClick = { editingName = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Edit display name") }
            }
        }
        item {
            ClayCard(ClayWhite) {
                Text("Notifications and sounds", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Floating notifications", fontWeight = FontWeight.Bold); Text("Show messages as a heads-up card", color = Muted, fontSize = 12.sp) }
                    Switch(checked = floating, onCheckedChange = { floating = it; vm.setFloatingNotifications(it) })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Message sent sound", fontWeight = FontWeight.Bold); Text("Play confirmation after sending", color = Muted, fontSize = 12.sp) }
                    Switch(checked = sendSound, onCheckedChange = { sendSound = it; vm.setSendSoundEnabled(it) })
                }
                OutlinedButton(onClick = { messageSoundPicker.launch(ringtonePicker(RingtoneManager.TYPE_NOTIFICATION, "Choose message sound")) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Choose incoming message sound") }
                OutlinedButton(onClick = { callSoundPicker.launch(ringtonePicker(RingtoneManager.TYPE_RINGTONE, "Choose call ringtone")) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Choose incoming call ringtone") }
                OutlinedButton(onClick = {
                    val intent = if (android.os.Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Open Android notification settings") }
                Text("Mowell keeps a lightweight message connection active so new chats can alert you with sound even when the app is not open. Android may show a small ongoing status notification for this connection.", color = Muted, fontSize = 11.sp)
            }
        }
        item {
            ClayCard(ClayWhite) {
                Text("App updates", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Installed version ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp)
                Text(updateStatus, color = if (update != null) Violet else Muted, fontSize = 13.sp)
                if (update != null) Button(onClick = { (context as? Activity)?.let(vm::installUpdate) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) { Icon(Icons.Rounded.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Update to ${update!!.versionName}") }
                else OutlinedButton(onClick = vm::checkForUpdates, Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) { Text("Check for updates") }
            }
        }
        item { OutlinedButton(onClick = vm::logout, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB3261E))) { Icon(Icons.Rounded.Logout, null); Spacer(Modifier.width(8.dp)); Text("Log out on this phone", fontWeight = FontWeight.Bold) } }
    }
}

@Composable
private fun ProfileScreen(vm: MowellViewModel, conversation: ConversationEntity, back: () -> Unit) {
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { vm.setConversationSound(conversation.id, it) }
    }
    Scaffold(containerColor = Canvas, topBar = {
        Row(Modifier.fillMaxWidth().background(Canvas).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text(if (conversation.isGroup) "Group info" else "Profile", fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                Avatar(conversation.title, 116.dp, Violet, conversation.avatarUrl)
                Spacer(Modifier.height(14.dp))
                Text(conversation.title, fontSize = 28.sp, fontWeight = FontWeight.Black)
                if (!conversation.username.isNullOrBlank()) Text("@${conversation.username}", color = Violet, fontSize = 16.sp)
                Text(if (conversation.isGroup) "Mowell group" else "Mowell contact", color = Muted)
            }
            item {
                ClayCard(ClayWhite) {
                    Text("About", fontWeight = FontWeight.Bold, color = Violet)
                    Text(if (conversation.isGroup) "Private group conversation stored on this phone." else "Connected through Mowell. Messages are cached privately in SQLite on this phone.", color = Ink)
                }
            }
            if (conversation.isGroup && conversation.members.isNotBlank()) item {
                ClayCard(Lavender) {
                    Text("Members", fontWeight = FontWeight.Bold, color = Violet)
                    Text(conversation.members, color = Ink)
                }
            }
            if (!conversation.isGroup) item {
                ClayCard(Lavender) {
                    Text("Activity", fontWeight = FontWeight.Bold, color = Violet)
                    Text(if (conversation.lastSeenAt > 0) "Last seen ${SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date(conversation.lastSeenAt))}" else "Last seen information unavailable", color = Ink)
                    Text("Internet and nearby messaging supported", color = Muted, fontSize = 12.sp)
                    OutlinedButton(onClick = { soundPicker.launch(ringtonePicker(RingtoneManager.TYPE_NOTIFICATION, "Sound for ${conversation.title}")) }, Modifier.fillMaxWidth()) { Text("Choose notification sound") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(vm: MowellViewModel, conversationId: String, back: () -> Unit, call: (CallSession) -> Unit, profile: (ConversationEntity) -> Unit) {
    val messages by vm.messages(conversationId).collectAsStateWithLifecycle(initialValue = emptyList())
    val conversation = vm.conversations.collectAsStateWithLifecycle().value.find { it.id == conversationId }
    val typingState by vm.typingUsers.collectAsStateWithLifecycle()
    val voiceRecording by vm.voiceRecording.collectAsStateWithLifecycle()
    val typing = typingState[conversationId].orEmpty()
    val endedRooms = messages.filter { it.kind == "call_end" }.mapNotNull { runCatching { JSONObject(it.body).optString("room") }.getOrNull() }.toSet()
    var text by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf(false) }
    var unlocked by remember(conversationId) { mutableStateOf(!vm.isChatLocked(conversationId)) }
    var hasLock by remember(conversationId) { mutableStateOf(vm.isChatLocked(conversationId)) }
    var unlockCode by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf(false) }
    var settingLock by remember { mutableStateOf(false) }
    var newCode by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<MessageEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var chatQuery by remember { mutableStateOf("") }
    var headerMenu by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.uploadAttachment(conversationId, it) } }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri -> uri?.let { vm.shareContact(conversationId, it) } }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        if (captured) cameraUri?.let { vm.uploadAttachment(conversationId, it) }
    }
    val displayedMessages = if (chatQuery.isBlank()) messages else messages.filter { message ->
        message.body.contains(chatQuery, ignoreCase = true) ||
            message.attachmentName?.contains(chatQuery, ignoreCase = true) == true ||
            message.sender.contains(chatQuery, ignoreCase = true) ||
            message.kind.contains(chatQuery, ignoreCase = true)
    }
    val showScrollToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: -1) < info.totalItemsCount - 1
        }
    }
    val send = { replyTo?.let { vm.sendReply(conversationId, text, it) } ?: vm.send(conversationId, text); vm.updateTyping(conversationId, false); text = ""; replyTo = null }
    if (!unlocked) {
        BackHandler { back() }
        Box(Modifier.fillMaxSize().background(Canvas).padding(24.dp), contentAlignment = Alignment.Center) {
            ClayCard(Lavender) {
                Icon(Icons.Rounded.Lock, null, tint = Violet, modifier = Modifier.size(38.dp))
                Text("Chat locked", fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("Enter this chat's passcode.", color = Muted)
                ClayField(unlockCode, { unlockCode = it; unlockError = false }, "Passcode", password = true)
                if (unlockError) Text("Incorrect passcode", color = Color(0xFFB3261E))
                Button(onClick = { if (vm.verifyChatPasscode(conversationId, unlockCode)) unlocked = true else unlockError = true }, Modifier.fillMaxWidth()) { Text("Unlock") }
                OutlinedButton(onClick = back, Modifier.fillMaxWidth()) { Text("Back to chats") }
            }
        }
        return
    }
    if (settingLock) {
        AlertDialog(onDismissRequest = { settingLock = false }, title = { Text("Lock this chat") },
            text = { Column { Text("Set a passcode with at least 4 characters.", color = Muted); ClayField(newCode, { newCode = it }, "New passcode", password = true) } },
            confirmButton = { Button(enabled = newCode.length >= 4, onClick = { vm.setChatPasscode(conversationId, newCode); hasLock = true; settingLock = false }) { Text("Lock") } },
            dismissButton = { OutlinedButton(onClick = { settingLock = false }) { Text("Cancel") } })
    }
    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Block ${conversation?.title ?: "this user"}?") },
            text = { Text("Neither person will be able to send messages or start calls until you unblock this contact.") },
            confirmButton = { Button(onClick = { vm.setUserBlocked(conversationId, true); confirmBlock = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Text("Block") } },
            dismissButton = { OutlinedButton(onClick = { confirmBlock = false }) { Text("Cancel") } }
        )
    }
    deleteTarget?.let { target ->
        val canDeleteEveryone = target.outgoing && target.kind == "text" && System.currentTimeMillis() - target.sentAt <= 4 * 60 * 1000
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete message?", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { vm.deleteMessage(target, false); deleteTarget = null }, Modifier.fillMaxWidth()) { Text("Delete for me") }
                    if (canDeleteEveryone) OutlinedButton(onClick = { vm.deleteMessage(target, true); deleteTarget = null }, Modifier.fillMaxWidth()) { Text("Delete for everyone") }
                    if (target.outgoing && target.kind == "text" && !canDeleteEveryone) Text("Delete for everyone expires four minutes after sending.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {}, dismissButton = { OutlinedButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
    LaunchedEffect(conversationId) {
        vm.markConversationRead(conversationId)
        while (true) { vm.syncConversation(conversationId); vm.refreshTyping(conversationId); delay(1_000) }
    }
    DisposableEffect(conversationId) { onDispose { vm.updateTyping(conversationId, false); vm.stopVoiceRecording(conversationId, false) } }
    LaunchedEffect(displayedMessages.size, typing, chatQuery) {
        val target = if (chatQuery.isBlank() && typing.isNotEmpty()) displayedMessages.size else displayedMessages.lastIndex
        if (target >= 0) listState.scrollToItem(target)
        vm.markConversationRead(conversationId)
    }
    Scaffold(
        containerColor = Canvas,
        topBar = {
            Column(Modifier.fillMaxWidth().background(Canvas)) {
                Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(Icons.Rounded.ArrowBack, "Back") }
                    Row(Modifier.weight(1f).clickable { conversation?.let(profile) }, verticalAlignment = Alignment.CenterVertically) {
                        Avatar(conversation?.title ?: "M", 42.dp, Violet, conversation?.avatarUrl); Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(conversation?.title ?: "Conversation", fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (typing.isNotEmpty()) TypingLine(typing.joinToString(", ")) else Text(vm.networkLabel(), color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(enabled = conversation?.blocked != true, onClick = { call(vm.createCall(conversationId, conversation?.title ?: "Mowell call", false)) }) { Icon(Icons.Rounded.Call, "Voice", tint = if (conversation?.blocked == true) Muted else Violet) }
                    IconButton(enabled = conversation?.blocked != true, onClick = { call(vm.createCall(conversationId, conversation?.title ?: "Mowell call", true)) }) { Icon(Icons.Rounded.Videocam, "Video", tint = if (conversation?.blocked == true) Muted else Violet) }
                    Box {
                        IconButton(onClick = { headerMenu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Violet) }
                        DropdownMenu(expanded = headerMenu, onDismissRequest = { headerMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Search chat") },
                                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                onClick = { headerMenu = false; searchOpen = true }
                            )
                            DropdownMenuItem(
                                text = { Text(if (hasLock) "Remove chat lock" else "Lock chat") },
                                leadingIcon = { Icon(if (hasLock) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null) },
                                onClick = {
                                    headerMenu = false
                                    if (hasLock) { vm.setChatPasscode(conversationId, null); hasLock = false } else settingLock = true
                                }
                            )
                            if (conversation?.isGroup == false) DropdownMenuItem(
                                text = { Text(if (conversation.blockedByMe) "Unblock user" else "Block user") },
                                leadingIcon = { Icon(Icons.Rounded.Block, null) },
                                onClick = {
                                    headerMenu = false
                                    if (conversation.blockedByMe) vm.setUserBlocked(conversationId, false) else confirmBlock = true
                                }
                            )
                        }
                    }
                }
                AnimatedVisibility(searchOpen) {
                    OutlinedTextField(
                        value = chatQuery,
                        onValueChange = { chatQuery = it },
                        placeholder = { Text("Search text or files in this chat") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { IconButton(onClick = { chatQuery = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Canvas)) {
                AnimatedVisibility(replyTo != null) {
                    Row(Modifier.fillMaxWidth().background(Lavender).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Replying to ${replyTo?.sender.orEmpty()}", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(replyTo?.body.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 11.sp) }
                        IconButton(onClick = { replyTo = null }) { Text("×", fontSize = 25.sp) }
                    }
                }
                if (conversation?.blocked == true) {
                    Row(Modifier.fillMaxWidth().background(Peach).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Block, null, tint = Color(0xFFB3261E)); Spacer(Modifier.width(9.dp))
                        Text("Messaging and calling are unavailable for this blocked contact.", Modifier.weight(1f), color = Ink, fontSize = 13.sp)
                        if (conversation.blockedByMe) OutlinedButton(onClick = { vm.setUserBlocked(conversationId, false) }) { Text("Unblock") }
                    }
                } else Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
                Box {
                    IconButton(enabled = !voiceRecording, onClick = { attachments = true }) { Icon(Icons.Rounded.AttachFile, "Share", tint = if (voiceRecording) Muted else Violet) }
                    DropdownMenu(expanded = attachments, onDismissRequest = { attachments = false }) {
                        DropdownMenuItem(text = { Text("Camera") }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = {
                            attachments = false
                            val directory = File(context.cacheDir, "camera").apply { mkdirs() }
                            val file = File(directory, "mowell_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            cameraUri = uri
                            cameraPicker.launch(uri)
                        })
                        DropdownMenuItem(text = { Text("Photo, video or file") }, leadingIcon = { Icon(Icons.Rounded.Description, null) }, onClick = { attachments = false; filePicker.launch("*/*") })
                        DropdownMenuItem(text = { Text("Current location") }, leadingIcon = { Icon(Icons.Rounded.LocationOn, null) }, onClick = { attachments = false; vm.shareLocation(conversationId) })
                        DropdownMenuItem(text = { Text("Contact") }, leadingIcon = { Icon(Icons.Rounded.ContactPhone, null) }, onClick = { attachments = false; contactPicker.launch(null) })
                    }
                }
                TextField(value = text, enabled = !voiceRecording, onValueChange = { value -> text = value.take(8000); vm.updateTyping(conversationId, text.isNotBlank()) }, placeholder = { Text(if (voiceRecording) "Recording… tap the mic to send" else "Write something…") }, modifier = Modifier.weight(1f).heightIn(min = 54.dp, max = 132.dp).shadow(6.dp, RoundedCornerShape(22.dp)), minLines = 1, maxLines = 5, shape = RoundedCornerShape(22.dp), colors = TextFieldDefaults.colors(focusedContainerColor = ClayWhite, unfocusedContainerColor = ClayWhite, disabledContainerColor = ClayWhite, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }))
                Spacer(Modifier.width(8.dp)); IconButton(
                    onClick = {
                        if (text.isNotBlank()) send()
                        else if (voiceRecording) vm.stopVoiceRecording(conversationId) else vm.startVoiceRecording(conversationId)
                    },
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(20.dp)).background(if (voiceRecording) Color(0xFFFFC7CB) else Lime)
                ) { Icon(if (text.isNotBlank()) Icons.Rounded.Send else Icons.Rounded.Mic, if (voiceRecording) "Stop and send recording" else if (text.isNotBlank()) "Send" else "Record voice message", tint = if (voiceRecording) Color(0xFFB3261E) else Ink) }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(showScrollToBottom) {
                FloatingActionButton(
                    onClick = {
                        val typingItem = chatQuery.isBlank() && typing.isNotEmpty()
                        val target = displayedMessages.size + if (typingItem) 1 else 0
                        if (target > 0) scope.launch { listState.animateScrollToItem(target - 1) }
                    },
                    containerColor = Lime,
                    contentColor = Ink,
                    shape = RoundedCornerShape(18.dp)
                ) { Icon(Icons.Rounded.KeyboardArrowDown, "Scroll to latest") }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (chatQuery.isNotBlank() && displayedMessages.isEmpty()) item(key = "no-search-results") { Text("No matching messages or files", color = Muted, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
            items(displayedMessages, key = { it.id }) { message ->
                val callRoom = if (message.kind == "call") runCatching { JSONObject(message.body).optString("room") }.getOrNull() else null
                MessageClay(message, callEnded = !callRoom.isNullOrBlank() && callRoom in endedRooms, onReply = { replyTo = message }, onLongPress = { deleteTarget = message }, openAttachment = { vm.openAttachment(context, message) }, openContact = { name, phone -> vm.openContact(context, name, phone) }, joinCall = { room, video, group -> call(CallSession(conversationId, conversation?.title ?: message.sender, room, video, group, avatarUrl = conversation?.avatarUrl)) })
            }
            if (chatQuery.isBlank() && typing.isNotEmpty()) item(key = "typing-indicator") { TypingBubble(typing.joinToString(", ")) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageClay(message: MessageEntity, callEnded: Boolean, onReply: () -> Unit, onLongPress: () -> Unit, openAttachment: () -> Unit, openContact: (String, String) -> Unit, joinCall: (String, Boolean, Boolean) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start) {
        Box(Modifier.widthIn(min = 88.dp, max = 310.dp).pointerInput(message.id) { var drag = 0f; detectHorizontalDragGestures(onDragStart = { drag = 0f }, onHorizontalDrag = { change, amount -> change.consume(); drag += amount }, onDragEnd = { if (drag < -80f) onReply() }) }.combinedClickable(onClick = { if (message.kind in setOf("image", "video", "audio", "file") && message.attachmentId != null) openAttachment() }, onLongClick = onLongPress).shadow(5.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(if (message.outgoing) Violet else ClayWhite).border(1.dp, Color.White.copy(alpha = .7f), RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
            Column {
                val foreground = if (message.outgoing) Color.White else Ink
                when (message.kind) {
                    "image", "video", "audio", "file" -> {
                        Icon(Icons.Rounded.AttachFile, null, tint = if (message.outgoing) Lime else Violet)
                        Text(message.attachmentName ?: message.body, color = foreground, fontWeight = FontWeight.Bold)
                        Text(if (message.delivery == "uploading") "Uploading…" else "Tap to open", color = if (message.outgoing) Color.White.copy(alpha = .75f) else Muted, fontSize = 11.sp, modifier = Modifier.clickable(enabled = message.attachmentId != null, onClick = openAttachment))
                    }
                    "location" -> {
                        val data = runCatching { JSONObject(message.body) }.getOrNull()
                        val lat = data?.optDouble("latitude") ?: 0.0
                        val lon = data?.optDouble("longitude") ?: 0.0
                        Text("Location shared", color = foreground, fontWeight = FontWeight.Bold)
                        Box(Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(14.dp))) {
                            AndroidView(modifier = Modifier.fillMaxSize(), factory = { webContext ->
                                WebView(webContext).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = WebViewClient()
                                    loadUrl(mapUrl(lat, lon))
                                }
                            })
                            Box(Modifier.fillMaxSize().clickable {
                                context.startActivity(Intent(context, MowellMapActivity::class.java).putExtra("latitude", lat).putExtra("longitude", lon))
                            })
                        }
                        Text("Tap map to open inside Mowell", color = if (message.outgoing) Lime else Violet, fontSize = 11.sp)
                    }
                    "contact" -> {
                        val data = runCatching { JSONObject(message.body) }.getOrNull()
                        val contactName = data?.optString("name") ?: "Contact"
                        val contactPhone = data?.optString("phone").orEmpty()
                        Column(Modifier.fillMaxWidth().clickable { openContact(contactName, contactPhone) }) {
                            Text(contactName, color = foreground, fontWeight = FontWeight.Bold)
                            Text(contactPhone, color = if (message.outgoing) Color.White.copy(alpha = .8f) else Muted)
                            Text("Tap to open contact in Mowell", color = if (message.outgoing) Lime else Violet, fontSize = 11.sp)
                        }
                    }
                    "call" -> {
                        val data = runCatching { JSONObject(message.body) }.getOrNull()
                        val video = data?.optBoolean("video") ?: false
                        val group = data?.optBoolean("group") ?: false
                        Text(if (video) "Video call" else "Voice call", color = foreground, fontWeight = FontWeight.Bold)
                        if (callEnded) Text("Call ended", color = if (message.outgoing) Color.White.copy(alpha = .75f) else Muted, fontSize = 12.sp)
                        else Button(onClick = { data?.optString("room")?.takeIf { it.isNotBlank() }?.let { joinCall(it, video, group) } }, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("Join") }
                    }
                    "call_end" -> Text("Call ended", color = foreground, fontWeight = FontWeight.Bold)
                    else -> Text(message.body, color = foreground)
                }
                Row(Modifier.align(Alignment.End)) { Text(time(message.sentAt), color = if (message.outgoing) Color.White.copy(alpha = .7f) else Muted, fontSize = 9.sp); Spacer(Modifier.width(5.dp)); Text(route(message.route), color = if (message.outgoing) Lime else Violet, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
        }
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
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, leadingIcon = leading?.let { icon -> { Icon(icon, null, tint = Violet) } }, trailingIcon = if (password) {{ IconButton(onClick = { reveal = !reveal }) { Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (reveal) "Hide password" else "Show password", tint = Violet) } }} else null, visualTransformation = if (password && !reveal) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(18.dp))
}

@Composable
private fun TextButtonLine(text: String, click: () -> Unit) { Text(text, color = VioletDark, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 9.dp)) }

@Composable
private fun OrbLogo(size: Dp) { Image(painterResource(com.grapaxels.mowell.R.drawable.mowell_logo), "Mowell", Modifier.size(size).shadow(10.dp, RoundedCornerShape(size * .24f)).clip(RoundedCornerShape(size * .24f)), contentScale = ContentScale.Fit) }

@Composable
private fun Avatar(name: String, size: Dp, color: Color, avatarUrl: String? = null) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, avatarUrl) {
        value = if (avatarUrl.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val absolute = if (avatarUrl.startsWith("/")) "https://mowell-api.grapaxels.in$avatarUrl" else avatarUrl
                URL(absolute).openStream().use(android.graphics.BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }
    Box(Modifier.size(size).shadow(5.dp, CircleShape).clip(CircleShape).background(color).border(2.dp, Color.White.copy(alpha = .8f), CircleShape), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.take(1).uppercase(), color = if (color == Lime) Ink else Color.White, fontSize = (size.value * .40f).sp, fontWeight = FontWeight.Black)
    }
}

private fun time(timestamp: Long) = if (timestamp == 0L) "now" else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
private fun maskEmail(email: String): String {
    val local = email.substringBefore('@')
    val domain = email.substringAfter('@', "")
    val suffix = domain.substringAfterLast('.', "").takeLast(2)
    return "${local.take(2).padEnd(2, '*')}****@****.**${suffix.padStart(2, '*')}"
}
@Composable
private fun TypingLine(names: String) {
    var dots by remember(names) { mutableStateOf(1) }
    LaunchedEffect(names) { while (true) { delay(320); dots = dots % 3 + 1 } }
    Text("$names typing${".".repeat(dots)}", color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun TypingBubble(names: String) {
    var phase by remember(names) { mutableStateOf(0) }
    LaunchedEffect(names) { while (true) { delay(180); phase = (phase + 1) % 3 } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(Modifier.shadow(5.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(ClayWhite).border(1.dp, Color.White, RoundedCornerShape(18.dp)).padding(horizontal = 15.dp, vertical = 10.dp)) {
            Text("$names typing", color = Muted, fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { index ->
                    Box(Modifier.padding(top = 5.dp).size(7.dp).graphicsLayer { translationY = if (phase == index) -4f else 0f; alpha = if (phase == index) 1f else .45f }.clip(CircleShape).background(Violet))
                }
            }
        }
    }
}

private fun ringtonePicker(type: Int, title: String) = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)

private fun mapUrl(latitude: Double, longitude: Double): String {
    return "https://maps.google.com/maps?q=$latitude,$longitude&z=16&output=embed"
}

private fun route(route: String) = when (route) { "INTERNET" -> "INTERNET"; "BLUETOOTH" -> "NEARBY"; "LOCAL_ONLY" -> "SAVED"; else -> route }
