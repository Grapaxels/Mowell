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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.toArgb
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
import androidx.core.view.WindowCompat
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

private val CometPurple = Color(0xFF6852D6)
private val CometPurpleDark = Color(0xFF5D45D2)
private val CometLightBackground = Color(0xFFFFFFFF)
private val CometLightSurface = Color(0xFFF8F8FA)
private val CometLightBorder = Color(0xFFE8E8EC)
private val CometDarkBackground = Color(0xFF0F0F10)
private val CometDarkSurface = Color(0xFF1A1A1C)
private val CometDarkBorder = Color(0xFF2A2A2E)

private val Canvas: Color
    @Composable get() = MaterialTheme.colorScheme.background
private val Ink: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground
private val Violet: Color
    @Composable get() = MaterialTheme.colorScheme.primary
private val VioletDark: Color
    @Composable get() = MaterialTheme.colorScheme.primary
private val Lavender: Color
    @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val Lime: Color
    @Composable get() = MaterialTheme.colorScheme.secondary
private val Peach: Color
    @Composable get() = MaterialTheme.colorScheme.errorContainer
private val Muted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ClayWhite: Color
    @Composable get() = MaterialTheme.colorScheme.surface

private fun ConversationEntity.displayTitle(): String =
    if (!isGroup && !localTitle.isNullOrBlank()) localTitle else title

private enum class Page { CHATS, PEOPLE, CALLS, NEARBY, YOU }

@Composable
fun MowellApp(vm: MowellViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val showUpdate by vm.showUpdatePopup.collectAsStateWithLifecycle()
    val updateStatus by vm.updateStatus.collectAsStateWithLifecycle()
    val updateDownloading by vm.updateDownloading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val themePreferences = remember(context) { context.getSharedPreferences("mowell_ui", android.content.Context.MODE_PRIVATE) }
    var darkMode by remember { mutableStateOf(themePreferences.getBoolean("dark_mode", false)) }
    val scheme = if (darkMode) darkColorScheme(
        primary = Color(0xFF8D79F2),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF2B244B),
        onPrimaryContainer = Color(0xFFEAE5FF),
        secondary = Color(0xFF8D79F2),
        background = CometDarkBackground,
        onBackground = Color(0xFFF4F2F8),
        surface = CometDarkSurface,
        onSurface = Color(0xFFF4F2F8),
        surfaceVariant = Color(0xFF222225),
        onSurfaceVariant = Color(0xFFAAA6B2),
        outline = CometDarkBorder,
        errorContainer = Color(0xFF442328)
    ) else lightColorScheme(
        primary = CometPurple,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF0EDFF),
        onPrimaryContainer = Color(0xFF271B63),
        secondary = CometPurple,
        background = CometLightBackground,
        onBackground = Color(0xFF141318),
        surface = CometLightSurface,
        onSurface = Color(0xFF141318),
        surfaceVariant = Color(0xFFF2F2F5),
        onSurfaceVariant = Color(0xFF727078),
        outline = CometLightBorder,
        errorContainer = Color(0xFFFFE9EB)
    )
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1_500); splash = false }
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkMode
                isAppearanceLightNavigationBars = !darkMode
            }
        }
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            when {
                splash -> SplashScreen()
                session == null -> AuthScreen(vm)
                else -> MainExperience(vm, darkMode) { enabled ->
                    darkMode = enabled
                    themePreferences.edit().putBoolean("dark_mode", enabled).apply()
                }
            }
        }
        if (showUpdate && update != null) {
            AlertDialog(
                onDismissRequest = vm::dismissUpdate,
                title = { Text("Mowell ${update!!.versionName} is available", fontWeight = FontWeight.Black) },
                text = { Column { Text(if (BuildConfig.SELF_UPDATE) "A newer signed version is ready. Download and update from inside Mowell." else "A newer version is ready through Google Play."); if (updateDownloading || updateStatus.startsWith("Could not")) { Spacer(Modifier.height(9.dp)); Text(updateStatus, color = if (updateStatus.startsWith("Could not")) Color(0xFFB3261E) else Violet, fontSize = 12.sp) } } },
                confirmButton = { Button(enabled = !updateDownloading, onClick = { (context as? Activity)?.let(vm::installUpdate) }) { if (updateDownloading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("Update now") } },
                dismissButton = { OutlinedButton(enabled = !updateDownloading, onClick = vm::dismissUpdate) { Text("Later") } }
            )
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
    Box(Modifier.fillMaxSize().background(Canvas), contentAlignment = Alignment.Center) {
        Column(Modifier.graphicsLayer { scaleX = reveal.value; scaleY = reveal.value; alpha = fade.value }, horizontalAlignment = Alignment.CenterHorizontally) {
            OrbLogo(104.dp)
            Spacer(Modifier.height(24.dp))
            Text("Mowell", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("from Grapaxels", color = Violet, fontStyle = FontStyle.Italic, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
            Text("FROM GRAPAXELS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
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
                Row(verticalAlignment = Alignment.CenterVertically) { OrbLogo(58.dp); Spacer(Modifier.width(14.dp)); Column { Text("Mowell", fontSize = 31.sp, fontWeight = FontWeight.Black); Text("from Grapaxels", color = Violet) } }
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
private fun MainExperience(vm: MowellViewModel, darkMode: Boolean, onThemeChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(Page.CHATS) }
    var openChat by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<ConversationEntity?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var createGroup by remember { mutableStateOf(false) }
    BackHandler {
        when {
            profile != null -> profile = null
            openChat != null -> openChat = null
            page != Page.CHATS -> page = Page.CHATS
            else -> Unit
        }
    }
    if (createGroup) CreateGroupDialog(vm, { createGroup = false }) { conversationId -> createGroup = false; openChat = conversationId }
    when {
        profile != null -> ProfileScreen(vm, profile!!, { profile = null })
        openChat != null -> ChatScreen(vm, openChat!!, { openChat = null }, { vm.launchCall(context, it) }, { conversation -> profile = conversation })
        else -> Scaffold(
            containerColor = Canvas,
            topBar = { ClayHeader(vm, page, darkMode, onThemeChanged) },
            bottomBar = {
                NavigationBar(containerColor = ClayWhite, tonalElevation = 0.dp, modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline)) {
                    Nav(Page.CHATS, page, "Chats", Icons.Rounded.ChatBubble) { page = it }
                    Nav(Page.PEOPLE, page, "People", Icons.Rounded.Search) { page = it }
                    Nav(Page.CALLS, page, "Calls", Icons.Rounded.Call) { page = it }
                    Nav(Page.NEARBY, page, "Nearby", Icons.Rounded.Bluetooth) { page = it }
                    Nav(Page.YOU, page, "You", Icons.Rounded.Person) { page = it }
                }
            },
            floatingActionButton = {
                if (page == Page.CHATS) Box {
                    FloatingActionButton(onClick = { addMenu = true }, containerColor = Violet, contentColor = Color.White, shape = CircleShape) { Icon(Icons.Rounded.Add, "New") }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(text = { Text("Connect people") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, onClick = { addMenu = false; page = Page.PEOPLE })
                        DropdownMenuItem(text = { Text("Create group") }, leadingIcon = { Icon(Icons.Rounded.Groups, null) }, onClick = { addMenu = false; createGroup = true; vm.refreshSocial() })
                    }
                }
            }
        ) { padding ->
            when (page) {
                Page.CHATS -> ChatsScreen(vm, Modifier.padding(padding)) { openChat = it }
                Page.PEOPLE -> PeopleScreen(vm, Modifier.padding(padding)) { user -> vm.startChat(user) { conversationId -> openChat = conversationId } }
                Page.CALLS -> CallsScreen(vm, Modifier.padding(padding)) { vm.launchCall(context, it) }
                Page.NEARBY -> NearbyScreen(vm, Modifier.padding(padding))
                Page.YOU -> SettingsScreen(vm, Modifier.padding(padding), darkMode, onThemeChanged)
            }
        }
    }
}

@Composable
private fun ClayHeader(vm: MowellViewModel, page: Page, darkMode: Boolean, onThemeChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Canvas).border(1.dp, MaterialTheme.colorScheme.outline).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        OrbLogo(34.dp); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(when (page) { Page.CHATS -> "Chats"; Page.PEOPLE -> "People"; Page.CALLS -> "Calls"; Page.NEARBY -> "Nearby"; Page.YOU -> "You" }, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (vm.networkLabel().startsWith("Internet")) Color(0xFF20B26B) else Color(0xFFFFA928)))
                Spacer(Modifier.width(5.dp))
                Text(if (vm.networkLabel().startsWith("Internet")) "Online" else "Nearby mode", color = Muted, fontSize = 11.sp)
            }
        }
        IconButton(onClick = { onThemeChanged(!darkMode) }) {
            Icon(if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, if (darkMode) "Use light theme" else "Use dark theme", tint = Violet)
        }
    }
}

@Composable
private fun RowScope.Nav(target: Page, selected: Page, label: String, icon: ImageVector, onSelect: (Page) -> Unit) {
    NavigationBarItem(selected = target == selected, onClick = { onSelect(target) }, icon = { Icon(icon, label, Modifier.size(22.dp)) }, label = { Text(label, fontSize = 10.sp, fontWeight = if (target == selected) FontWeight.Bold else FontWeight.Normal) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = VioletDark, selectedTextColor = VioletDark, unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Lavender))
}

@Composable
private fun ChatsScreen(vm: MowellViewModel, modifier: Modifier, open: (String) -> Unit) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val chatLists by vm.chatLists.collectAsStateWithLifecycle()
    val users by vm.userResults.collectAsStateWithLifecycle()
    val requests by vm.connectionRequests.collectAsStateWithLifecycle()
    var peopleQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("all") }
    var creatingList by remember { mutableStateOf(false) }
    var hideTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var listName by remember { mutableStateOf("") }
    var selectedPeople by remember { mutableStateOf(setOf<String>()) }
    val people = conversations.filter { !it.isGroup && it.id != "general" }
    LaunchedEffect(Unit) { while (true) { vm.refreshSocial(); delay(5_000) } }
    val visibleConversations = when (selectedFilter) {
        "unread" -> conversations.filter { it.unreadCount > 0 }
        "groups" -> conversations.filter { it.isGroup }
        "all" -> conversations
        else -> chatLists.find { it.id == selectedFilter }?.conversationIds
            ?.let { memberIds -> conversations.filter { it.id in memberIds } }.orEmpty()
    }

    if (creatingList) {
        AlertDialog(
            onDismissRequest = { creatingList = false },
            title = { Text("Create a people list", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    OutlinedTextField(
                        value = listName,
                        onValueChange = { listName = it.take(30) },
                        label = { Text("List name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Choose people", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(people, key = { "list-person-${it.id}" }) { conversation ->
                            val checked = conversation.id in selectedPeople
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                    selectedPeople = if (checked) selectedPeople - conversation.id else selectedPeople + conversation.id
                                }.padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = {
                                    selectedPeople = if (checked) selectedPeople - conversation.id else selectedPeople + conversation.id
                                })
                                Avatar(conversation.displayTitle(), 36.dp, Ink, conversation.avatarUrl)
                                Spacer(Modifier.width(9.dp))
                                Column { Text(conversation.displayTitle(), fontWeight = FontWeight.Bold); conversation.username?.let { Text("@$it", color = Violet, fontSize = 11.sp) } }
                            }
                        }
                        if (people.isEmpty()) item { Text("Add people to Mowell before creating a list.", color = Muted, modifier = Modifier.padding(vertical = 16.dp)) }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = listName.trim().isNotEmpty() && selectedPeople.isNotEmpty(),
                    onClick = {
                        vm.createChatList(listName, selectedPeople)
                        listName = ""; selectedPeople = emptySet(); creatingList = false
                    }
                ) { Text("Create") }
            },
            dismissButton = { OutlinedButton(onClick = { creatingList = false }) { Text("Cancel") } }
        )
    }
    hideTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { hideTarget = null },
            title = { Text("Remove from chats?", fontWeight = FontWeight.Black) },
            text = { Text("${target.title} will be removed from this screen only. You stay in the group or chat, and it returns automatically when a new incoming message arrives.") },
            confirmButton = { Button(onClick = { vm.hideConversation(target.id); hideTarget = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Text("Remove") } },
            dismissButton = { OutlinedButton(onClick = { hideTarget = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ClayField(peopleQuery, { value -> peopleQuery = value.lowercase(); vm.searchUsers(value) }, "Search people by username", leading = Icons.Rounded.Search)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 6.dp)) {
                item { FilterChip(selected = selectedFilter == "all", onClick = { selectedFilter = "all" }, label = { Text("All") }) }
                item { FilterChip(selected = selectedFilter == "unread", onClick = { selectedFilter = "unread" }, label = { Text("Unread") }) }
                item { FilterChip(selected = selectedFilter == "groups", onClick = { selectedFilter = "groups" }, label = { Text("Groups") }) }
                items(chatLists, key = { "filter-${it.id}" }) { list ->
                    FilterChip(selected = selectedFilter == list.id, onClick = { selectedFilter = list.id }, label = { Text(list.name, maxLines = 1) })
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { listName = ""; selectedPeople = emptySet(); creatingList = true },
                        label = { Text("New list") },
                        leadingIcon = { Icon(Icons.Rounded.Add, "Create list", Modifier.size(18.dp)) }
                    )
                }
            }
        }
        if (peopleQuery.length >= 2) {
            items(users, key = { "person-${it.id}" }) { user ->
                val existing = conversations.find { it.username.equals(user.username, ignoreCase = true) }
                val request = requests.find { it.user.id == user.id }
                ClayCard(ClayWhite) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(user.displayName, 50.dp, Violet, user.avatarUrl); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(user.displayName, fontWeight = FontWeight.Bold); Text("@${user.username}", color = Violet, fontSize = 13.sp) }
                        Button(enabled = existing != null || request?.direction != "outgoing", onClick = {
                            when { existing != null -> open(existing.id); request?.direction == "incoming" -> vm.respondConnectionRequest(request.id, true); request == null -> vm.sendConnectionRequest(user) }
                        }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
                            Icon(if (existing == null) Icons.Rounded.Add else Icons.Rounded.ChatBubble, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(when { existing != null -> "Chat"; request?.direction == "incoming" -> "Accept"; request?.direction == "outgoing" -> "Requested"; else -> "Connect" })
                        }
                    }
                }
            }
            if (users.isEmpty()) item { Text("No matching people found.", color = Muted, modifier = Modifier.padding(10.dp)) }
        } else {
            items(visibleConversations, key = { it.id }) { conversation ->
                ConversationClay(conversation, onClick = { open(conversation.id) }, onLongClick = { hideTarget = conversation })
            }
            if (visibleConversations.isEmpty()) item { Text("No chats in this list yet.", color = Muted, modifier = Modifier.padding(10.dp)) }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ConversationClay(conversation: ConversationEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).clip(RoundedCornerShape(14.dp)).background(if (conversation.isGroup) Lavender else ClayWhite).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(conversation.displayTitle(), 48.dp, if (conversation.isGroup) Violet else Ink, conversation.avatarUrl)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conversation.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (conversation.isGroup) Text("  Group", color = Violet, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                }
                Spacer(Modifier.height(2.dp))
                Text(conversation.subtitle, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(time(conversation.updatedAt), color = if (conversation.unreadCount > 0) Violet else Muted, fontSize = 11.sp, fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal)
                if (conversation.unreadCount > 0) Box(Modifier.padding(top = 5.dp).clip(CircleShape).background(Violet).padding(horizontal = 7.dp, vertical = 2.dp)) { Text(conversation.unreadCount.coerceAtMost(99).toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(vm: MowellViewModel, dismiss: () -> Unit, created: (String) -> Unit) {
    val connections by vm.connections.collectAsStateWithLifecycle()
    val searchResults by vm.userResults.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var inviteQuery by remember { mutableStateOf("") }
    var members by remember { mutableStateOf(setOf<String>()) }
    var invites by remember { mutableStateOf(setOf<String>()) }
    val connectionIds = connections.map { it.id }.toSet()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Create group", fontWeight = FontWeight.Black) },
        text = {
            Column {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp)); Text("Add connected people", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 170.dp)) {
                    items(connections, key = { "group-member-${it.id}" }) { user ->
                        val checked = user.id in members
                        Row(Modifier.fillMaxWidth().clickable { members = if (checked) members - user.id else members + user.id }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, { members = if (checked) members - user.id else members + user.id }); Avatar(user.displayName, 34.dp, Violet, user.avatarUrl); Spacer(Modifier.width(8.dp)); Text(user.displayName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        }
                    }
                    if (connections.isEmpty()) item { Text("No accepted connections yet.", color = Muted, modifier = Modifier.padding(vertical = 10.dp)) }
                }
                Spacer(Modifier.height(7.dp)); Text("Invite someone not connected", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(inviteQuery, { inviteQuery = it.lowercase(); vm.searchUsers(it) }, placeholder = { Text("Search @username") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = if (inviteQuery.isNotEmpty()) {{ IconButton(onClick = { inviteQuery = ""; vm.searchUsers("") }) { Icon(Icons.Rounded.Close, "Close search") } }} else null, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 140.dp)) {
                    items(if (inviteQuery.length >= 2) searchResults.filter { it.id !in connectionIds } else emptyList(), key = { "group-invite-${it.id}" }) { user ->
                        val checked = user.id in invites
                        Row(Modifier.fillMaxWidth().clickable { invites = if (checked) invites - user.id else invites + user.id }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, { invites = if (checked) invites - user.id else invites + user.id }); Avatar(user.displayName, 32.dp, Ink, user.avatarUrl); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(user.displayName, fontWeight = FontWeight.Bold); Text("Invite @${user.username}", color = Violet, fontSize = 11.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(enabled = title.trim().isNotEmpty() && (members.isNotEmpty() || invites.isNotEmpty()), onClick = { vm.createGroup(title, members, invites, created) }) { Text("Create") } },
        dismissButton = { OutlinedButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddGroupMembersDialog(vm: MowellViewModel, conversationId: String, dismiss: () -> Unit) {
    val connections by vm.connections.collectAsStateWithLifecycle()
    val searchResults by vm.userResults.collectAsStateWithLifecycle()
    var inviteQuery by remember { mutableStateOf("") }
    var members by remember { mutableStateOf(setOf<String>()) }
    var invites by remember { mutableStateOf(setOf<String>()) }
    val connectionIds = connections.map { it.id }.toSet()
    LaunchedEffect(Unit) { vm.refreshSocial() }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add group members", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("Connected people join immediately", color = Muted, fontSize = 12.sp)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 190.dp)) {
                    items(connections, key = { "add-member-${it.id}" }) { user ->
                        val checked = user.id in members
                        Row(Modifier.fillMaxWidth().clickable { members = if (checked) members - user.id else members + user.id }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, { members = if (checked) members - user.id else members + user.id }); Avatar(user.displayName, 34.dp, Violet, user.avatarUrl); Spacer(Modifier.width(8.dp)); Text(user.displayName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(7.dp)); Text("Invite a non-contact", color = Muted, fontSize = 12.sp)
                OutlinedTextField(inviteQuery, { inviteQuery = it.lowercase(); vm.searchUsers(it) }, placeholder = { Text("Search @username") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = if (inviteQuery.isNotEmpty()) {{ IconButton(onClick = { inviteQuery = ""; vm.searchUsers("") }) { Icon(Icons.Rounded.Close, "Close search") } }} else null, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                    items(if (inviteQuery.length >= 2) searchResults.filter { it.id !in connectionIds } else emptyList(), key = { "add-invite-${it.id}" }) { user ->
                        val checked = user.id in invites
                        Row(Modifier.fillMaxWidth().clickable { invites = if (checked) invites - user.id else invites + user.id }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, { invites = if (checked) invites - user.id else invites + user.id }); Avatar(user.displayName, 32.dp, Ink, user.avatarUrl); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(user.displayName, fontWeight = FontWeight.Bold); Text("Invite @${user.username}", color = Violet, fontSize = 11.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(enabled = members.isNotEmpty() || invites.isNotEmpty(), onClick = { vm.addGroupMembers(conversationId, members, invites, dismiss) }) { Text("Add") } },
        dismissButton = { OutlinedButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PeopleScreen(vm: MowellViewModel, modifier: Modifier, onUser: (UserProfile) -> Unit) {
    val users by vm.userResults.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val requests by vm.connectionRequests.collectAsStateWithLifecycle()
    val groupInvitations by vm.groupInvitations.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { vm.refreshSocial(); delay(4_000) } }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Both people must accept before chatting.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(7.dp))
            ClayField(query, { query = it.lowercase(); vm.searchUsers(it) }, "Search username", leading = Icons.Rounded.Search)
        }
        items(requests.filter { it.direction == "incoming" }, key = { "request-${it.id}" }) { request ->
            ClayCard(Lavender) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(request.user.displayName, 42.dp, Violet, request.user.avatarUrl); Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) { Text(request.user.displayName, fontWeight = FontWeight.Bold); Text("wants to connect · @${request.user.username}", color = Muted, fontSize = 11.sp) }
                    OutlinedButton(onClick = { vm.respondConnectionRequest(request.id, false) }, contentPadding = PaddingValues(horizontal = 9.dp)) { Text("Decline", fontSize = 11.sp) }
                    Spacer(Modifier.width(5.dp)); Button(onClick = { vm.respondConnectionRequest(request.id, true) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Accept", fontSize = 11.sp) }
                }
            }
        }
        items(groupInvitations, key = { "group-invitation-${it.id}" }) { invitation ->
            ClayCard(Peach) {
                Text("Group invitation", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(invitation.groupTitle, fontWeight = FontWeight.Black)
                Text("Invited by ${invitation.inviter.displayName}", color = Muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = { vm.respondGroupInvitation(invitation.id, false) }) { Text("Decline") }
                    Button(onClick = { vm.respondGroupInvitation(invitation.id, true) }) { Text("Join group") }
                }
            }
        }
        items(users, key = { it.id }) { user ->
            val existing = conversations.find { it.username.equals(user.username, ignoreCase = true) }
            val request = requests.find { it.user.id == user.id }
            ClayCard(ClayWhite) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user.displayName, 50.dp, Violet, user.avatarUrl); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(user.displayName, fontWeight = FontWeight.Bold); Text("@${user.username}", color = Violet, fontSize = 13.sp) }
                    Button(enabled = existing != null || request?.direction != "outgoing", onClick = {
                        when { existing != null -> onUser(user); request?.direction == "incoming" -> vm.respondConnectionRequest(request.id, true); request == null -> vm.sendConnectionRequest(user) }
                    }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
                        Icon(if (existing != null) Icons.Rounded.ChatBubble else Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(when { existing != null -> "Chat"; request?.direction == "incoming" -> "Accept"; request?.direction == "outgoing" -> "Requested"; else -> "Connect" })
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
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Recent voice and video calls", color = Muted, fontSize = 13.sp) }
        items(conversations.filterNot { it.id == "general" }, key = { it.id }) { conversation -> CallHistoryCard(vm, conversation, onCall) }
    }
}

@Composable
private fun CallHistoryCard(vm: MowellViewModel, conversation: ConversationEntity, onCall: (CallSession) -> Unit) {
    val messages by vm.messages(conversation.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val lastEnded = messages.lastOrNull { it.kind == "call_end" }
    val history = lastEnded?.let { callEndText(it.body) } ?: if (conversation.isGroup) "Group call" else "Direct call"
    ClayCard(if (conversation.isGroup) Lavender else ClayWhite) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(conversation.displayTitle(), 52.dp, if (conversation.isGroup) Violet else Ink, conversation.avatarUrl); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(conversation.displayTitle(), fontWeight = FontWeight.Bold)
                Text(history, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onCall(vm.createCall(conversation.id, conversation.displayTitle(), false)) }) { Icon(Icons.Rounded.Call, "Voice", tint = Violet) }
            IconButton(onClick = { onCall(vm.createCall(conversation.id, conversation.displayTitle(), true)) }) { Icon(Icons.Rounded.Videocam, "Video", tint = Violet) }
        }
    }
}

private fun callEndText(body: String): String {
    val json = runCatching { JSONObject(body) }.getOrNull() ?: return "Call ended"
    val seconds = json.optLong("durationSeconds", 0L)
    if (seconds <= 0L) return when (json.optString("reason")) {
        "no_answer" -> "No answer"
        "busy" -> "User was busy"
        else -> "Call ended"
    }
    val minutes = seconds / 60
    val remainder = seconds % 60
    return "Call ended · ${if (minutes > 0) "${minutes}m " else ""}${remainder}s"
}

@Composable
private fun NearbyScreen(vm: MowellViewModel, modifier: Modifier) {
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { vm.bluetooth.startListening() }
    val peers = remember(refresh) { vm.bluetooth.bondedPeers() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Store locally, send to a paired peer, and keep working without internet.", color = Muted)
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
private fun SettingsScreen(vm: MowellViewModel, modifier: Modifier, darkMode: Boolean, onThemeChanged: (Boolean) -> Unit) {
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
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Account, appearance and privacy", color = Muted, fontSize = 13.sp) }
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
                Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (darkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, null, tint = Violet)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (darkMode) "Dark mode" else "Light mode", fontWeight = FontWeight.Bold)
                        Text("Match the CometChat light and dark UI", color = Muted, fontSize = 12.sp)
                    }
                    Switch(checked = darkMode, onCheckedChange = onThemeChanged)
                }
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
                Text("Installed version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}", color = Muted, fontSize = 12.sp)
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
    var addingMembers by remember { mutableStateOf(false) }
    var removeMember by remember { mutableStateOf<Pair<String, String>?>(null) }
    var leavingGroup by remember { mutableStateOf(false) }
    var deletingGroup by remember { mutableStateOf(false) }
    var editingGroupName by remember { mutableStateOf(false) }
    var editingLocalName by remember { mutableStateOf(false) }
    var proposedName by remember(conversation.id) { mutableStateOf(conversation.displayTitle()) }
    val groupStates by vm.groupMemberStates.collectAsStateWithLifecycle()
    val groupState = groupStates[conversation.id]
    val session by vm.session.collectAsStateWithLifecycle()
    LaunchedEffect(conversation.id) { if (conversation.isGroup) vm.refreshGroupMembers(conversation.id) }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { vm.setConversationSound(conversation.id, it) }
    }
    val groupIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.updateGroupPicture(conversation.id, it) } }
    if (addingMembers) AddGroupMembersDialog(vm, conversation.id) { addingMembers = false }
    if (editingGroupName) AlertDialog(
        onDismissRequest = { editingGroupName = false }, title = { Text("Edit group name") },
        text = { ClayField(proposedName, { proposedName = it }, "Group name") },
        confirmButton = { Button(enabled = proposedName.trim().length >= 2, onClick = { vm.updateGroupName(conversation.id, proposedName); editingGroupName = false }) { Text("Save for everyone") } },
        dismissButton = { OutlinedButton(onClick = { editingGroupName = false }) { Text("Cancel") } }
    )
    if (editingLocalName) AlertDialog(
        onDismissRequest = { editingLocalName = false }, title = { Text("Save contact name") },
        text = { Column { Text("Only you can see this name.", color = Muted); ClayField(proposedName, { proposedName = it }, "Name") } },
        confirmButton = { Button(onClick = { vm.setLocalContactName(conversation.id, proposedName); editingLocalName = false }) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = { editingLocalName = false }) { Text("Cancel") } }
    )
    removeMember?.let { target ->
        AlertDialog(onDismissRequest = { removeMember = null }, title = { Text("Remove ${target.second}?") }, text = { Text("They will lose access to this group and its new messages.") }, confirmButton = { Button(onClick = { vm.removeGroupMember(conversation.id, target.first); removeMember = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Text("Remove") } }, dismissButton = { OutlinedButton(onClick = { removeMember = null }) { Text("Cancel") } })
    }
    if (leavingGroup) {
        AlertDialog(
            onDismissRequest = { leavingGroup = false },
            title = { Text("Exit group?", fontWeight = FontWeight.Black) },
            text = { Text("You will stop receiving new messages from ${conversation.displayTitle()}. This does not delete the group for other members.") },
            confirmButton = { Button(onClick = { vm.leaveGroup(conversation.id) { back() }; leavingGroup = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Text("Exit") } },
            dismissButton = { OutlinedButton(onClick = { leavingGroup = false }) { Text("Cancel") } }
        )
    }
    if (deletingGroup) {
        AlertDialog(
            onDismissRequest = { deletingGroup = false },
            title = { Text("Delete group permanently?", fontWeight = FontWeight.Black) },
            text = { Text("This deletes ${conversation.displayTitle()}, its messages, invitations, and uploads for every member. This cannot be undone.") },
            confirmButton = { Button(onClick = { vm.deleteGroup(conversation.id) { back() }; deletingGroup = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Text("Delete group") } },
            dismissButton = { OutlinedButton(onClick = { deletingGroup = false }) { Text("Cancel") } }
        )
    }
    Scaffold(containerColor = Canvas, topBar = {
        Row(Modifier.fillMaxWidth().background(Canvas).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text(if (conversation.isGroup) "Group info" else "Profile", fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                Avatar(conversation.displayTitle(), 116.dp, Violet, conversation.avatarUrl)
                Spacer(Modifier.height(14.dp))
                Text(conversation.displayTitle(), fontSize = 28.sp, fontWeight = FontWeight.Black)
                if (!conversation.username.isNullOrBlank()) Text("@${conversation.username}", color = Violet, fontSize = 16.sp)
                Text(if (conversation.isGroup) "Mowell group" else "Mowell contact", color = Muted)
            }
            item {
                ClayCard(ClayWhite) {
                    Text("About", fontWeight = FontWeight.Bold, color = Violet)
                    Text(if (conversation.isGroup) "Private group conversation stored on this phone." else "Connected through Mowell. Messages are cached privately in SQLite on this phone.", color = Ink)
                }
            }
            if (conversation.isGroup) item {
                ClayCard(Lavender) {
                    Text("Members", fontWeight = FontWeight.Bold, color = Violet)
                    groupState?.members?.forEach { member ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(member.user.displayName, 38.dp, if (member.isAdmin) Violet else Ink, member.user.avatarUrl); Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) { Text(member.user.displayName, fontWeight = FontWeight.Bold); Text(when { member.isCreator -> "Creator · Super admin"; member.isAdmin -> "Admin"; else -> "Member" }, color = if (member.isAdmin) Violet else Muted, fontSize = 11.sp) }
                            val viewerIsCreator = groupState.creatorId == session?.user?.id
                            if (groupState.viewerIsAdmin && member.user.id != session?.user?.id && !member.isCreator) Column(horizontalAlignment = Alignment.End) {
                                if (!member.isAdmin) Text("Make admin", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { vm.setGroupAdmin(conversation.id, member.user.id, true) }.padding(4.dp))
                                else if (viewerIsCreator) Text("Remove admin", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { vm.setGroupAdmin(conversation.id, member.user.id, false) }.padding(4.dp))
                                if (!member.isAdmin || viewerIsCreator) Text("Remove", color = Color(0xFFB3261E), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { removeMember = member.user.id to member.user.displayName }.padding(4.dp))
                            }
                        }
                    }
                    if (groupState == null) Text(conversation.members.ifBlank { "Loading members…" }, color = Muted)
                }
            }
            if (conversation.isGroup && groupState?.viewerIsAdmin == true) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { addingMembers = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("Add members") }
                    OutlinedButton(onClick = { proposedName = conversation.title; editingGroupName = true }, Modifier.fillMaxWidth()) { Text("Edit group name") }
                    OutlinedButton(onClick = { groupIconPicker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(7.dp)); Text("Change group icon") }
                }
            }
            if (conversation.isGroup && conversation.id != "general" && groupState != null) item {
                val viewerIsCreator = groupState?.creatorId == session?.user?.id
                if (viewerIsCreator) {
                    OutlinedButton(onClick = { deletingGroup = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB3261E))) { Text("Delete group permanently") }
                } else {
                    OutlinedButton(onClick = { leavingGroup = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB3261E))) { Text("Exit group") }
                }
            }
            if (!conversation.isGroup) item {
                ClayCard(Lavender) {
                    Text("Activity", fontWeight = FontWeight.Bold, color = Violet)
                    Text(if (conversation.lastSeenAt > 0) "Last seen ${SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date(conversation.lastSeenAt))}" else "Last seen information unavailable", color = Ink)
                    Text("Internet and nearby messaging supported", color = Muted, fontSize = 12.sp)
                    OutlinedButton(onClick = { proposedName = conversation.localTitle ?: conversation.title; editingLocalName = true }, Modifier.fillMaxWidth()) { Text("Save a name on this phone") }
                    OutlinedButton(onClick = { soundPicker.launch(ringtonePicker(RingtoneManager.TYPE_NOTIFICATION, "Sound for ${conversation.displayTitle()}")) }, Modifier.fillMaxWidth()) { Text("Choose notification sound") }
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
            title = { Text("Block ${conversation?.displayTitle() ?: "this user"}?") },
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
            Column(Modifier.fillMaxWidth().background(Canvas).border(1.dp, MaterialTheme.colorScheme.outline)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(Icons.Rounded.ArrowBack, "Back") }
                    Row(Modifier.weight(1f).clickable { conversation?.let(profile) }, verticalAlignment = Alignment.CenterVertically) {
                        Avatar(conversation?.displayTitle() ?: "M", 42.dp, Violet, conversation?.avatarUrl); Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(conversation?.displayTitle() ?: "Conversation", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (typing.isNotEmpty()) TypingLine(typing.joinToString(", ")) else Text(if (vm.networkLabel().startsWith("Internet")) "Online" else "Nearby", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(enabled = conversation?.blocked != true, onClick = { call(vm.createCall(conversationId, conversation?.displayTitle() ?: "Mowell call", false)) }) { Icon(Icons.Rounded.Call, "Voice", tint = if (conversation?.blocked == true) Muted else Violet) }
                    IconButton(enabled = conversation?.blocked != true, onClick = { call(vm.createCall(conversationId, conversation?.displayTitle() ?: "Mowell call", true)) }) { Icon(Icons.Rounded.Videocam, "Video", tint = if (conversation?.blocked == true) Muted else Violet) }
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
                        trailingIcon = { IconButton(onClick = { chatQuery = ""; searchOpen = false }) { Icon(Icons.Rounded.Close, "Close search") } },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Canvas).border(1.dp, MaterialTheme.colorScheme.outline)) {
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
                } else Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.Bottom) {
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
                TextField(value = text, enabled = !voiceRecording, onValueChange = { value -> text = value.take(8000); vm.updateTyping(conversationId, text.isNotBlank()) }, placeholder = { Text(if (voiceRecording) "Recording… tap the mic to send" else "Message") }, modifier = Modifier.weight(1f).heightIn(min = 50.dp, max = 132.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)), minLines = 1, maxLines = 5, shape = RoundedCornerShape(24.dp), colors = TextFieldDefaults.colors(focusedContainerColor = ClayWhite, unfocusedContainerColor = ClayWhite, disabledContainerColor = ClayWhite, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }))
                Spacer(Modifier.width(8.dp)); IconButton(
                    onClick = {
                        if (text.isNotBlank()) send()
                        else if (voiceRecording) vm.stopVoiceRecording(conversationId) else vm.startVoiceRecording(conversationId)
                    },
                    modifier = Modifier.size(50.dp).clip(CircleShape).background(if (voiceRecording) Color(0xFFFFC7CB) else Violet)
                ) { Icon(if (text.isNotBlank()) Icons.Rounded.Send else Icons.Rounded.Mic, if (voiceRecording) "Stop and send recording" else if (text.isNotBlank()) "Send" else "Record voice message", tint = if (voiceRecording) Color(0xFFB3261E) else Color.White) }
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
                    containerColor = Violet,
                    contentColor = Color.White,
                    shape = CircleShape
                ) { Icon(Icons.Rounded.KeyboardArrowDown, "Scroll to latest") }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (chatQuery.isNotBlank() && displayedMessages.isEmpty()) item(key = "no-search-results") { Text("No matching messages or files", color = Muted, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
            items(displayedMessages, key = { it.id }) { message ->
                val callRoom = if (message.kind == "call") runCatching { JSONObject(message.body).optString("room") }.getOrNull() else null
                MessageClay(message, callEnded = !callRoom.isNullOrBlank() && callRoom in endedRooms, onReply = { replyTo = message }, onLongPress = { deleteTarget = message }, openAttachment = { vm.openAttachment(context, message) }, openContact = { name, phone -> vm.openContact(context, name, phone) }, joinCall = { room, video, group -> call(CallSession(conversationId, conversation?.displayTitle() ?: message.sender, room, video, group, avatarUrl = conversation?.avatarUrl)) })
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
        Box(Modifier.widthIn(min = 88.dp, max = 310.dp).pointerInput(message.id) { var drag = 0f; detectHorizontalDragGestures(onDragStart = { drag = 0f }, onHorizontalDrag = { change, amount -> change.consume(); drag += amount }, onDragEnd = { if (drag < -80f) onReply() }) }.combinedClickable(onClick = { if (message.kind in setOf("image", "video", "audio", "file") && message.attachmentId != null) openAttachment() }, onLongClick = onLongPress).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (message.outgoing) 16.dp else 4.dp, bottomEnd = if (message.outgoing) 4.dp else 16.dp)).background(if (message.outgoing) Violet else ClayWhite).border(1.dp, if (message.outgoing) Violet else MaterialTheme.colorScheme.outline, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (message.outgoing) 16.dp else 4.dp, bottomEnd = if (message.outgoing) 4.dp else 16.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
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
                                val maps = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:$lat,$lon?q=$lat,$lon(Shared+location)"))
                                runCatching { context.startActivity(maps) }.getOrElse {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://maps.google.com/?q=$lat,$lon")))
                                }
                            })
                        }
                        Text("Tap map to open in your maps app", color = if (message.outgoing) Lime else Violet, fontSize = 11.sp)
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
                    "call_end" -> Text(callEndText(message.body), color = foreground, fontWeight = FontWeight.Bold)
                    "system" -> Text(
                        message.body,
                        color = if (message.outgoing) Color.White.copy(alpha = .72f) else Muted,
                        fontStyle = if (message.body == "This message was deleted") FontStyle.Italic else FontStyle.Normal
                    )
                    else -> Text(message.body, color = foreground)
                }
                Spacer(Modifier.height(3.dp))
                Row(Modifier.align(Alignment.End)) { Text(time(message.sentAt), color = if (message.outgoing) Color.White.copy(alpha = .72f) else Muted, fontSize = 10.sp); Spacer(Modifier.width(5.dp)); Text(route(message.route), color = if (message.outgoing) Color.White.copy(alpha = .82f) else Violet, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, color: Color, click: () -> Unit) { Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = click, Modifier.size(64.dp).clip(CircleShape).background(color)) { Icon(icon, label, tint = Color.White) }; Text(label, color = Color.White, fontSize = 11.sp) } }

@Composable
private fun ClayCard(color: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun ClayField(value: String, change: (String) -> Unit, label: String, password: Boolean = false, leading: ImageVector? = null) {
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, leadingIcon = leading?.let { icon -> { Icon(icon, null, tint = Violet) } }, trailingIcon = if (password) {{ IconButton(onClick = { reveal = !reveal }) { Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (reveal) "Hide password" else "Show password", tint = Violet) } }} else null, visualTransformation = if (password && !reveal) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(12.dp))
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
    Box(Modifier.size(size).clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) {
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
        Column(Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)).background(ClayWhite).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)).padding(horizontal = 15.dp, vertical = 10.dp)) {
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
