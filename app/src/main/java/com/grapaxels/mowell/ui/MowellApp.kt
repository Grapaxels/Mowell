package com.grapaxels.mowell.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Translate
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
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
import com.grapaxels.mowell.auth.GroupMember
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
private val CometLightSurface = Color(0xFFFFFFFF)
private val CometLightBorder = Color(0xFFF5F5F5)
private val CometDarkBackground = Color(0xFF141414)
private val CometDarkSurface = Color(0xFF1F1F1F)
private val CometDarkBorder = Color(0xFF303030)

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
private val ChatCanvas: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < .4f) Color(0xFF242424) else Color(0xFFF5F5F5)

private fun ConversationEntity.displayTitle(): String =
    if (!isGroup && !localTitle.isNullOrBlank()) localTitle else title

private enum class Page { CHATS, CALLS, USERS, GROUPS }
private enum class AppPanel { PROFILE, LISTS, LINKED_DEVICES }
private enum class AppMenuAction { NEW_CHAT, NEW_GROUP, PROFILE, LISTS, LINKED_DEVICES, NEARBY, SETTINGS }

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
        surfaceVariant = Color(0xFF333333),
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
        surfaceVariant = Color(0xFFE8E8E8),
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
    var createGroup by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var nearbyOpen by remember { mutableStateOf(false) }
    var callInfo by remember { mutableStateOf<ConversationEntity?>(null) }
    var appPanel by remember { mutableStateOf<AppPanel?>(null) }
    BackHandler {
        when {
            settingsOpen -> settingsOpen = false
            nearbyOpen -> nearbyOpen = false
            appPanel != null -> appPanel = null
            callInfo != null -> callInfo = null
            profile != null -> profile = null
            openChat != null -> openChat = null
            page != Page.CHATS -> page = Page.CHATS
            else -> Unit
        }
    }
    if (createGroup) CreateGroupDialog(vm, { createGroup = false }) { conversationId -> createGroup = false; openChat = conversationId }
    when {
        settingsOpen -> Scaffold(containerColor = Canvas, topBar = { CometBackHeader("Settings", { settingsOpen = false }) }) { padding -> SettingsScreen(vm, Modifier.padding(padding).fillMaxSize(), darkMode, onThemeChanged) }
        nearbyOpen -> Scaffold(containerColor = Canvas, topBar = { CometBackHeader("Nearby", { nearbyOpen = false }) }) { padding -> NearbyScreen(vm, Modifier.padding(padding)) }
        appPanel == AppPanel.PROFILE -> OwnProfileScreen(vm, { appPanel = null }) { appPanel = null; settingsOpen = true }
        appPanel == AppPanel.LISTS -> ListsScreen(vm, { appPanel = null }) { conversationId -> appPanel = null; openChat = conversationId }
        appPanel == AppPanel.LINKED_DEVICES -> LinkedDevicesScreen({ appPanel = null })
        callInfo != null -> CallInfoScreen(vm, callInfo!!, { callInfo = null }) { vm.launchCall(context, it) }
        profile != null -> ProfileScreen(vm, profile!!, { profile = null })
        openChat != null -> ChatScreen(vm, openChat!!, { openChat = null }, { vm.launchCall(context, it) }, { conversation -> profile = conversation })
        else -> Scaffold(
            containerColor = Canvas,
            topBar = {
                ClayHeader(vm, page, darkMode, onThemeChanged) { action ->
                    when (action) {
                        AppMenuAction.NEW_CHAT -> page = Page.USERS
                        AppMenuAction.NEW_GROUP -> { createGroup = true; vm.refreshSocial() }
                        AppMenuAction.PROFILE -> appPanel = AppPanel.PROFILE
                        AppMenuAction.LISTS -> appPanel = AppPanel.LISTS
                        AppMenuAction.LINKED_DEVICES -> appPanel = AppPanel.LINKED_DEVICES
                        AppMenuAction.NEARBY -> nearbyOpen = true
                        AppMenuAction.SETTINGS -> settingsOpen = true
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Canvas, tonalElevation = 0.dp, modifier = Modifier.height(72.dp)) {
                    Nav(Page.CHATS, page, "Chats", Icons.Rounded.ChatBubble) { page = it }
                    Nav(Page.CALLS, page, "Calls", Icons.Rounded.Call) { page = it }
                    Nav(Page.USERS, page, "Users", Icons.Rounded.Person) { page = it }
                    Nav(Page.GROUPS, page, "Groups", Icons.Rounded.Groups) { page = it }
                }
            }
        ) { padding ->
            when (page) {
                Page.CHATS -> ChatsScreen(vm, Modifier.padding(padding)) { openChat = it }
                Page.CALLS -> CallsScreen(vm, Modifier.padding(padding), { callInfo = it }) { vm.launchCall(context, it) }
                Page.USERS -> PeopleScreen(vm, Modifier.padding(padding)) { user -> vm.startChat(user) { conversationId -> openChat = conversationId } }
                Page.GROUPS -> GroupsScreen(vm, Modifier.padding(padding), { createGroup = true; vm.refreshSocial() }) { openChat = it }
            }
        }
    }
}

@Composable
private fun ClayHeader(vm: MowellViewModel, page: Page, darkMode: Boolean, onThemeChanged: (Boolean) -> Unit, onAction: (AppMenuAction) -> Unit) {
    val session by vm.session.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Canvas)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(when (page) { Page.CHATS -> "Chats"; Page.CALLS -> "Calls"; Page.USERS -> "Users"; Page.GROUPS -> "Groups" }, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (page == Page.USERS || page == Page.GROUPS) Text(if (vm.networkLabel().startsWith("Internet")) "Online" else "Nearby", color = Muted, fontSize = 10.sp)
            }
            if (page == Page.GROUPS) IconButton(onClick = { onAction(AppMenuAction.NEW_GROUP) }) { Icon(Icons.Rounded.PersonAdd, "Create group", tint = Violet) }
            if (page == Page.CHATS) {
                Box(Modifier.clickable { onAction(AppMenuAction.PROFILE) }) { Avatar(session?.user?.displayName ?: "M", 40.dp, Violet, session?.user?.avatarUrl) }
                Spacer(Modifier.width(2.dp))
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "More options", tint = Ink) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        fun choose(action: AppMenuAction) { menuOpen = false; onAction(action) }
                        DropdownMenuItem(text = { Text("New chat") }, leadingIcon = { Icon(Icons.Rounded.ChatBubble, null) }, onClick = { choose(AppMenuAction.NEW_CHAT) })
                        DropdownMenuItem(text = { Text("New group") }, leadingIcon = { Icon(Icons.Rounded.Groups, null) }, onClick = { choose(AppMenuAction.NEW_GROUP) })
                        DropdownMenuItem(text = { Text("Profile") }, leadingIcon = { Icon(Icons.Rounded.Person, null) }, onClick = { choose(AppMenuAction.PROFILE) })
                        DropdownMenuItem(text = { Text("Lists") }, leadingIcon = { Icon(Icons.Rounded.Forum, null) }, onClick = { choose(AppMenuAction.LISTS) })
                        DropdownMenuItem(text = { Text("Linked devices") }, leadingIcon = { Icon(Icons.Rounded.Wifi, null) }, onClick = { choose(AppMenuAction.LINKED_DEVICES) })
                        DropdownMenuItem(text = { Text("Nearby devices") }, leadingIcon = { Icon(Icons.Rounded.Bluetooth, null) }, onClick = { choose(AppMenuAction.NEARBY) })
                        DropdownMenuItem(text = { Text(if (darkMode) "Light mode" else "Dark mode") }, leadingIcon = { Icon(if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, null) }, onClick = { menuOpen = false; onThemeChanged(!darkMode) })
                        DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Rounded.Settings, null) }, onClick = { choose(AppMenuAction.SETTINGS) })
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
    }
}

@Composable
private fun RowScope.Nav(target: Page, selected: Page, label: String, icon: ImageVector, onSelect: (Page) -> Unit) {
    NavigationBarItem(selected = target == selected, onClick = { onSelect(target) }, icon = { Icon(icon, label, Modifier.size(24.dp)) }, label = { Text(label, fontSize = 12.sp, fontWeight = if (target == selected) FontWeight.Medium else FontWeight.Normal) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Violet, selectedTextColor = Violet, unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Color.Transparent))
}

@Composable
private fun CometBackHeader(title: String, back: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().background(Canvas)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            action?.invoke()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun OwnProfileScreen(vm: MowellViewModel, back: () -> Unit, edit: () -> Unit) {
    val session by vm.session.collectAsStateWithLifecycle()
    Scaffold(containerColor = Canvas, topBar = { CometBackHeader("Profile", back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().background(Canvas), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(session?.user?.displayName ?: "M", 104.dp, Violet, session?.user?.avatarUrl)
                    Spacer(Modifier.height(14.dp))
                    Text(session?.user?.displayName ?: "Mowell user", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("@${session?.user?.username.orEmpty()}", color = Muted, fontSize = 14.sp)
                }
            }
            item { ProfileValueRow(Icons.Rounded.Person, "Name", session?.user?.displayName.orEmpty()) }
            item { ProfileValueRow(Icons.Rounded.Forum, "Username", "@${session?.user?.username.orEmpty()}") }
            item { ProfileValueRow(Icons.Rounded.Description, "Email", session?.user?.email.orEmpty()) }
            item {
                Button(onClick = edit, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp).height(48.dp), shape = RoundedCornerShape(8.dp)) {
                    Text("Edit profile", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ProfileValueRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = Violet)
        Spacer(Modifier.width(12.dp))
        Column { Text(label, color = Muted, fontSize = 12.sp); Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun ListsScreen(vm: MowellViewModel, back: () -> Unit, openChat: (String) -> Unit) {
    val lists by vm.chatLists.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var listName by remember { mutableStateOf("") }
    var selectedPeople by remember { mutableStateOf(setOf<String>()) }
    val selectedList = lists.find { it.id == selectedListId }
    if (creating) AlertDialog(
        onDismissRequest = { creating = false },
        title = { Text("New list", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(listName, { listName = it.take(30) }, label = { Text("List name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text("Select chats", color = Muted, fontSize = 12.sp)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(conversations.filter { it.id != "general" }, key = { "list-choice-${it.id}" }) { conversation ->
                        val checked = conversation.id in selectedPeople
                        Row(Modifier.fillMaxWidth().clickable { selectedPeople = if (checked) selectedPeople - conversation.id else selectedPeople + conversation.id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, { selectedPeople = if (checked) selectedPeople - conversation.id else selectedPeople + conversation.id })
                            Avatar(conversation.displayTitle(), 36.dp, Violet, conversation.avatarUrl)
                            Spacer(Modifier.width(10.dp)); Text(conversation.displayTitle(), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(enabled = listName.trim().isNotEmpty() && selectedPeople.isNotEmpty(), onClick = { vm.createChatList(listName, selectedPeople); listName = ""; selectedPeople = emptySet(); creating = false }) { Text("Create") } },
        dismissButton = { OutlinedButton(onClick = { creating = false }) { Text("Cancel") } }
    )
    Scaffold(containerColor = Canvas, topBar = {
        CometBackHeader(selectedList?.name ?: "Lists", { if (selectedList != null) selectedListId = null else back() }) {
            if (selectedList == null) IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "New list", tint = Violet) }
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().background(Canvas)) {
            if (selectedList == null) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Violet), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = Color.White) }
                        Spacer(Modifier.width(12.dp)); Column { Text("New list", fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text("Organize important chats", color = Muted, fontSize = 14.sp) }
                    }
                }
                items(lists, key = { "list-${it.id}" }) { list ->
                    Row(Modifier.fillMaxWidth().height(72.dp).clickable { selectedListId = list.id }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Forum, null, tint = Violet) }
                        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(list.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text("${list.conversationIds.size} chats", color = Muted, fontSize = 14.sp) }
                        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                    }
                }
                if (lists.isEmpty()) item { EmptyState(Icons.Rounded.Forum, "No Lists Yet", "Create a list to keep related conversations together.") }
            } else {
                items(conversations.filter { it.id in selectedList.conversationIds }, key = { "list-chat-${it.id}" }) { conversation ->
                    ConversationClay(conversation, { openChat(conversation.id) }, {})
                }
            }
        }
    }
}

@Composable
private fun LinkedDevicesScreen(back: () -> Unit) {
    var linkInfo by remember { mutableStateOf(false) }
    if (linkInfo) AlertDialog(
        onDismissRequest = { linkInfo = false },
        title = { Text("Link a device") },
        text = { Text("Secure pairing requires another signed-in Mowell device. Open Linked devices there and scan the pairing code when multi-device pairing is enabled on your server.") },
        confirmButton = { Button(onClick = { linkInfo = false }) { Text("Got it") } }
    )
    Scaffold(containerColor = Canvas, topBar = { CometBackHeader("Linked devices", back) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Canvas).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Wifi, null, tint = Violet, modifier = Modifier.size(42.dp)) }
            Spacer(Modifier.height(18.dp)); Text("Use Mowell on your other devices", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp)); Text("Messages stay protected and this phone remains your primary device.", color = Muted, fontSize = 14.sp)
            Button(onClick = { linkInfo = true }, Modifier.fillMaxWidth().padding(vertical = 20.dp).height(48.dp), shape = RoundedCornerShape(8.dp)) { Text("Link a device") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Person, null, tint = Violet, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("This phone", fontWeight = FontWeight.SemiBold); Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", color = Muted, fontSize = 12.sp) }
                Text("Active", color = Color(0xFF09C26F), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChatsScreen(vm: MowellViewModel, modifier: Modifier, open: (String) -> Unit) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    var hideTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    LaunchedEffect(Unit) { while (true) { vm.refreshSocial(); delay(5_000) } }
    hideTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { hideTarget = null },
            title = { Text("Remove from chats?", fontWeight = FontWeight.Black) },
            text = { Text("${target.title} will be removed from this screen only. You stay in the group or chat, and it returns automatically when a new incoming message arrives.") },
            confirmButton = { Button(onClick = { vm.hideConversation(target.id); hideTarget = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Text("Remove") } },
            dismissButton = { OutlinedButton(onClick = { hideTarget = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(modifier.fillMaxSize().background(Canvas)) {
        items(conversations, key = { it.id }) { conversation ->
            ConversationClay(conversation, onClick = { open(conversation.id) }, onLongClick = { hideTarget = conversation })
        }
        if (conversations.isEmpty()) item { EmptyState(Icons.Rounded.ChatBubble, "No Conversations Yet", "Start a new chat or invite others to join the conversation.") }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ConversationClay(conversation: ConversationEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(72.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick).background(Canvas).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(conversation.displayTitle(), 48.dp, if (conversation.isGroup) Violet else Ink, conversation.avatarUrl)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conversation.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (conversation.isGroup) Text("  Group", color = Muted, fontWeight = FontWeight.Normal, fontSize = 10.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(conversation.subtitle, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(time(conversation.updatedAt), color = if (conversation.unreadCount > 0) Violet else Muted, fontSize = 12.sp, fontWeight = FontWeight.Normal)
                if (conversation.unreadCount > 0) Box(Modifier.padding(top = 4.dp).size(20.dp).clip(CircleShape).background(Violet), contentAlignment = Alignment.Center) { Text(conversation.unreadCount.coerceAtMost(99).toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium) }
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
    var groupType by remember { mutableStateOf("private") }
    var groupPassword by remember { mutableStateOf("") }
    val connectionIds = connections.map { it.id }.toSet()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Create group", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("Type", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("public", "private", "password")) { type -> FilterChip(selected = groupType == type, onClick = { groupType = type }, label = { Text(type.replaceFirstChar { it.uppercase() }) }) }
                }
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (groupType == "password") OutlinedTextField(groupPassword, { groupPassword = it.take(80) }, label = { Text("Group password") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
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
        confirmButton = { Button(enabled = title.trim().isNotEmpty() && (members.isNotEmpty() || invites.isNotEmpty()) && (groupType != "password" || groupPassword.length >= 4), onClick = { vm.createGroup(title, members, invites, groupType, groupPassword, created) }) { Text("Create") } },
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
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("Both people must accept before chatting.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(7.dp))
                ClayField(query, { query = it.lowercase(); vm.searchUsers(it) }, "Search username", leading = Icons.Rounded.Search)
            }
        }
        items(requests.filter { it.direction == "incoming" }, key = { "request-${it.id}" }) { request ->
            ClayCard(Lavender, Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(request.user.displayName, 42.dp, Violet, request.user.avatarUrl); Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) { Text(request.user.displayName, fontWeight = FontWeight.Bold); Text("wants to connect · @${request.user.username}", color = Muted, fontSize = 11.sp) }
                    OutlinedButton(onClick = { vm.respondConnectionRequest(request.id, false) }, contentPadding = PaddingValues(horizontal = 9.dp)) { Text("Decline", fontSize = 11.sp) }
                    Spacer(Modifier.width(5.dp)); Button(onClick = { vm.respondConnectionRequest(request.id, true) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Accept", fontSize = 11.sp) }
                }
            }
        }
        items(groupInvitations, key = { "group-invitation-${it.id}" }) { invitation ->
            ClayCard(Peach, Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
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
            Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user.displayName, 48.dp, Violet, user.avatarUrl); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(user.displayName, fontWeight = FontWeight.Medium, fontSize = 16.sp); Spacer(Modifier.height(4.dp)); Text("@${user.username}", color = Muted, fontSize = 14.sp) }
                    Button(enabled = existing != null || request?.direction != "outgoing", onClick = {
                        when { existing != null -> onUser(user); request?.direction == "incoming" -> vm.respondConnectionRequest(request.id, true); request == null -> vm.sendConnectionRequest(user) }
                    }, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                        Icon(if (existing != null) Icons.Rounded.ChatBubble else Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(when { existing != null -> "Chat"; request?.direction == "incoming" -> "Accept"; request?.direction == "outgoing" -> "Requested"; else -> "Connect" })
                    }
            }
        }
        if (query.length >= 2 && users.isEmpty()) item { EmptyState(Icons.Rounded.Search, "No people found", "Check the username and try again.") }
    }
}

@Composable
private fun GroupsScreen(vm: MowellViewModel, modifier: Modifier, createGroup: () -> Unit, open: (String) -> Unit) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val groups = conversations.filter { it.isGroup && it.id != "general" && it.displayTitle().contains(query, true) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        items(groups, key = { "group-${it.id}" }) { group ->
            Row(Modifier.fillMaxWidth().height(72.dp).clickable { open(group.id) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(group.displayTitle(), 48.dp, Violet, group.avatarUrl); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(group.displayTitle(), fontWeight = FontWeight.Medium, fontSize = 16.sp); Spacer(Modifier.height(4.dp)); Text(group.members.ifBlank { "Mowell group" }, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (groups.isEmpty()) item { EmptyState(Icons.Rounded.Groups, "No Groups Yet", "Create a group and invite people to start a shared conversation.") }
    }
}

@Composable
private fun CallsScreen(vm: MowellViewModel, modifier: Modifier, onInfo: (ConversationEntity) -> Unit, onCall: (CallSession) -> Unit) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    LazyColumn(modifier.fillMaxSize().background(Canvas)) {
        items(conversations.filterNot { it.id == "general" }, key = { it.id }) { conversation -> CallHistoryCard(vm, conversation, { onInfo(conversation) }, onCall) }
        if (conversations.none { it.id != "general" }) item { EmptyState(Icons.Rounded.Call, "No Calls Yet", "Your voice and video call history will appear here.") }
    }
}

@Composable
private fun CallHistoryCard(vm: MowellViewModel, conversation: ConversationEntity, onInfo: () -> Unit, onCall: (CallSession) -> Unit) {
    val messages by vm.messages(conversation.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val lastEnded = messages.lastOrNull { it.kind == "call_end" }
    val history = lastEnded?.let { callEndText(it.body) } ?: if (conversation.isGroup) "Group call" else "Direct call"
    Row(Modifier.fillMaxWidth().height(72.dp).clickable(onClick = onInfo).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(conversation.displayTitle(), 48.dp, if (conversation.isGroup) Violet else Ink, conversation.avatarUrl); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(conversation.displayTitle(), fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("↗", color = Color(0xFF09C26F), fontSize = 14.sp); Spacer(Modifier.width(4.dp)); Text(history, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            IconButton(onClick = { onCall(vm.createCall(conversation.id, conversation.displayTitle(), if (conversation.isGroup) true else false)) }) { Icon(if (conversation.isGroup) Icons.Rounded.Videocam else Icons.Rounded.Call, if (conversation.isGroup) "Video" else "Voice", tint = Ink, modifier = Modifier.size(24.dp)) }
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
private fun CallInfoScreen(vm: MowellViewModel, conversation: ConversationEntity, back: () -> Unit, onCall: (CallSession) -> Unit) {
    val messages by vm.messages(conversation.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val groupStates by vm.groupMemberStates.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("Participants") }
    LaunchedEffect(conversation.id) { if (conversation.isGroup) vm.refreshGroupMembers(conversation.id) }
    Scaffold(containerColor = Canvas, topBar = { CometBackHeader("Call Info", back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(conversation.displayTitle(), 84.dp, Violet, conversation.avatarUrl); Spacer(Modifier.height(10.dp)); Text(conversation.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(top = 12.dp)) {
                        IconButton(onClick = { onCall(vm.createCall(conversation.id, conversation.displayTitle(), false)) }, Modifier.clip(CircleShape).background(Violet)) { Icon(Icons.Rounded.Call, "Voice", tint = Color.White) }
                        IconButton(onClick = { onCall(vm.createCall(conversation.id, conversation.displayTitle(), true)) }, Modifier.clip(CircleShape).background(Violet)) { Icon(Icons.Rounded.Videocam, "Video", tint = Color.White) }
                    }
                }
            }
            item {
                LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Participants", "Recording", "History")) { label -> FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label) }) }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outline)
            }
            when (tab) {
                "Participants" -> {
                    val members = groupStates[conversation.id]?.members.orEmpty()
                    if (conversation.isGroup && members.isNotEmpty()) items(members, key = { "call-participant-${it.user.id}" }) { member ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(member.user.displayName, 44.dp, Violet, member.user.avatarUrl); Spacer(Modifier.width(12.dp)); Column { Text(member.user.displayName, fontWeight = FontWeight.SemiBold); Text(if (member.isAdmin) "Admin" else "Member", color = Muted, fontSize = 11.sp) } }
                    } else item { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(conversation.displayTitle(), 44.dp, Violet, conversation.avatarUrl); Spacer(Modifier.width(12.dp)); Text(conversation.displayTitle(), fontWeight = FontWeight.SemiBold) } }
                }
                "Recording" -> item { EmptyState(Icons.Rounded.Mic, "No Recordings", "Call recordings appear here only when every participant has consented.") }
                else -> {
                    val history = messages.filter { it.kind == "call_end" }.reversed()
                    items(history, key = { "call-history-${it.id}" }) { item -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) { Icon(Icons.Rounded.Call, null, tint = Violet); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(callEndText(item.body), fontWeight = FontWeight.SemiBold); Text(SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date(item.sentAt)), color = Muted, fontSize = 11.sp) } } }
                    if (history.isEmpty()) item { EmptyState(Icons.Rounded.Call, "No Call History", "Completed calls will appear here.") }
                }
            }
        }
    }
}

@Composable
private fun NearbyScreen(vm: MowellViewModel, modifier: Modifier) {
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { vm.bluetooth.startListening() }
    val peers = remember(refresh) { vm.bluetooth.bondedPeers() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Store locally, send to a paired peer, and keep working without internet.", color = Muted)
        }
        items(peers) { (name, address) ->
            val chosen = vm.selectedPeer == address
            ClayCard(if (chosen) Lavender else Canvas, Modifier.clickable { vm.selectedPeer = address; refresh++ }) {
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
    var groupSection by remember { mutableStateOf<String?>(null) }
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
    if (conversation.isGroup && groupSection != null) {
        GroupRosterScreen(
            title = if (groupSection == "banned") "Banned Members" else "Members",
            members = groupState?.members.orEmpty(),
            banned = groupState?.bannedMembers.orEmpty(),
            showBanned = groupSection == "banned",
            viewerId = session?.user?.id,
            creatorId = groupState?.creatorId,
            viewerIsAdmin = groupState?.viewerIsAdmin == true,
            back = { groupSection = null },
            makeAdmin = { userId, enabled -> vm.setGroupAdmin(conversation.id, userId, enabled) },
            remove = { userId, name -> removeMember = userId to name },
            ban = { userId, enabled -> vm.setGroupMemberBanned(conversation.id, userId, enabled) }
        )
        return
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
                Text(conversation.displayTitle(), fontSize = if (conversation.isGroup) 20.sp else 24.sp, fontWeight = FontWeight.Bold)
                if (!conversation.username.isNullOrBlank()) Text("@${conversation.username}", color = Violet, fontSize = 16.sp)
                Text(if (conversation.isGroup) "${groupState?.members?.size ?: 0} Members" else "Mowell contact", color = Muted, fontSize = 12.sp)
            }
            if (!conversation.isGroup) item {
                ClayCard(ClayWhite) {
                    Text("About", fontWeight = FontWeight.Bold, color = Violet)
                    Text(if (conversation.isGroup) "Private group conversation stored on this phone." else "Connected through Mowell. Messages are cached privately in SQLite on this phone.", color = Ink)
                }
            }
            if (conversation.isGroup) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupInfoAction(Icons.Rounded.PersonAdd, "Add Members", Modifier.weight(1f), enabled = groupState?.viewerIsAdmin == true) { addingMembers = true }
                    GroupInfoAction(Icons.Rounded.Groups, "View Members", Modifier.weight(1f)) { groupSection = "members" }
                    GroupInfoAction(Icons.Rounded.Block, "Banned members", Modifier.weight(1f)) { groupSection = "banned" }
                }
            }
            if (false && conversation.isGroup) item {
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
                                if (!member.isAdmin || viewerIsCreator) Text("Ban", color = Color(0xFFB3261E), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { vm.setGroupMemberBanned(conversation.id, member.user.id, true) }.padding(4.dp))
                            }
                        }
                    }
                    if (groupState == null) Text(conversation.members.ifBlank { "Loading members…" }, color = Muted)
                }
            }
            if (false && conversation.isGroup && groupState?.bannedMembers?.isNotEmpty() == true) item {
                ClayCard(ClayWhite) {
                    Text("Banned members", fontWeight = FontWeight.Bold, color = Violet)
                    groupState.bannedMembers.forEach { user ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(user.displayName, 38.dp, Ink, user.avatarUrl); Spacer(Modifier.width(9.dp)); Text(user.displayName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("Unban", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clickable { vm.setGroupMemberBanned(conversation.id, user.id, false) }.padding(8.dp))
                        }
                    }
                }
            }
            if (conversation.isGroup && groupState?.viewerIsAdmin == true) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { proposedName = conversation.title; editingGroupName = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text("Edit group name") }
                    OutlinedButton(onClick = { groupIconPicker.launch("image/*") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(7.dp)); Text("Change group icon") }
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

@Composable
private fun RowScope.GroupInfoAction(icon: ImageVector, label: String, modifier: Modifier, enabled: Boolean = true, click: () -> Unit) {
    Column(
        modifier.height(78.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = click).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (enabled) Violet else Muted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(7.dp)); Text(label, color = if (enabled) Muted else Muted.copy(alpha = .55f), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun GroupRosterScreen(
    title: String,
    members: List<GroupMember>,
    banned: List<UserProfile>,
    showBanned: Boolean,
    viewerId: String?,
    creatorId: String?,
    viewerIsAdmin: Boolean,
    back: () -> Unit,
    makeAdmin: (String, Boolean) -> Unit,
    remove: (String, String) -> Unit,
    ban: (String, Boolean) -> Unit
) {
    Scaffold(containerColor = Canvas, topBar = { CometBackHeader(title, back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().background(Canvas)) {
            if (showBanned) {
                items(banned, key = { "banned-${it.id}" }) { user ->
                    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(user.displayName, 40.dp, Ink, user.avatarUrl); Spacer(Modifier.width(12.dp)); Text(user.displayName, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        if (viewerIsAdmin) Text("Unban", color = Violet, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { ban(user.id, false) }.padding(8.dp))
                    }
                }
                if (banned.isEmpty()) item { EmptyState(Icons.Rounded.Block, "No Banned Members", "People removed from this group can be managed here.") }
            } else {
                items(members, key = { "member-${it.user.id}" }) { member ->
                    Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(member.user.displayName, 44.dp, if (member.isAdmin) Violet else Ink, member.user.avatarUrl); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(member.user.displayName, fontWeight = FontWeight.Medium, fontSize = 16.sp); Text(when { member.isCreator -> "Creator · Super admin"; member.isAdmin -> "Admin"; else -> "Member" }, color = Muted, fontSize = 12.sp) }
                        val viewerIsCreator = creatorId == viewerId
                        if (viewerIsAdmin && member.user.id != viewerId && !member.isCreator) Box {
                            var menu by remember(member.user.id) { mutableStateOf(false) }
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Member actions") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (!member.isAdmin) DropdownMenuItem(text = { Text("Make admin") }, onClick = { menu = false; makeAdmin(member.user.id, true) })
                                else if (viewerIsCreator) DropdownMenuItem(text = { Text("Remove admin") }, onClick = { menu = false; makeAdmin(member.user.id, false) })
                                if (!member.isAdmin || viewerIsCreator) DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; remove(member.user.id, member.user.displayName) })
                                if (!member.isAdmin || viewerIsCreator) DropdownMenuItem(text = { Text("Ban") }, onClick = { menu = false; ban(member.user.id, true) })
                            }
                        }
                    }
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
    val groupStates by vm.groupMemberStates.collectAsStateWithLifecycle()
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
    var actionTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var infoTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var reactionTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var editTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var editText by remember { mutableStateOf("") }
    var translateTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var threadRoot by remember { mutableStateOf<MessageEntity?>(null) }
    var aiOpen by remember { mutableStateOf(false) }
    var extrasOpen by remember { mutableStateOf(false) }
    var mediaOpen by remember { mutableStateOf(false) }
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
    val timelineMessages = messages.filter { it.threadRootId.isNullOrBlank() }
    val displayedMessages = if (chatQuery.isBlank()) timelineMessages else timelineMessages.filter { message ->
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
    val mentionQuery = text.substringAfterLast(' ', text).takeIf { it.startsWith("@") }?.drop(1).orEmpty()
    val mentionSuggestions = if (conversation?.isGroup == true && mentionQuery.isNotBlank()) groupStates[conversationId]?.members.orEmpty().filter {
        it.user.username.startsWith(mentionQuery, true) || it.user.displayName.startsWith(mentionQuery, true)
    }.take(5) else emptyList()
    threadRoot?.let { root ->
        ThreadView(vm, conversationId, root, messages.filter { it.threadRootId == root.id }, { threadRoot = null })
        return
    }
    actionTarget?.let { target ->
        MessageOptionsSheet(
            message = target,
            dismiss = { actionTarget = null },
            react = { emoji -> vm.reactToMessage(target, emoji); actionTarget = null },
            info = { infoTarget = target; actionTarget = null },
            copy = { actionTarget = null },
            edit = { editTarget = target; editText = target.body; actionTarget = null },
            reply = { replyTo = target; actionTarget = null },
            thread = { threadRoot = target; actionTarget = null },
            translate = { translateTarget = target; actionTarget = null },
            delete = { deleteTarget = target; actionTarget = null }
        )
    }
    infoTarget?.let { target -> MessageInfoSheet(target) { infoTarget = null } }
    reactionTarget?.let { target -> ReactionInfoSheet(target) { reactionTarget = null } }
    if (editTarget != null) AlertDialog(
        onDismissRequest = { editTarget = null },
        title = { Text("Edit message", fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(editText, { editText = it.take(8000) }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6) },
        confirmButton = { Button(enabled = editText.trim().isNotBlank(), onClick = { editTarget?.let { vm.editMessage(it, editText) }; editTarget = null }) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = { editTarget = null }) { Text("Cancel") } }
    )
    translateTarget?.let { target -> TranslationSheet(target) { translateTarget = null } }
    if (aiOpen) LocalAiSheet(messages = timelineMessages, dismiss = { aiOpen = false }, send = { value -> text = value; aiOpen = false })
    if (mediaOpen) ChatMediaSheet(timelineMessages, dismiss = { mediaOpen = false }) { message -> vm.openAttachment(context, message) }
    if (extrasOpen) RichMessageSheet(
        dismiss = { extrasOpen = false },
        send = { body, kind, metadata -> vm.sendRich(conversationId, body, kind, metadata); extrasOpen = false }
    )
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
        if (conversation?.isGroup == true) vm.refreshGroupMembers(conversationId)
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
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(Icons.Rounded.ArrowBack, "Back") }
                    Row(Modifier.weight(1f).clickable { conversation?.let(profile) }, verticalAlignment = Alignment.CenterVertically) {
                        Avatar(conversation?.displayTitle() ?: "M", 40.dp, Violet, conversation?.avatarUrl); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(conversation?.displayTitle() ?: "Conversation", fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (typing.isNotEmpty()) TypingLine(typing.joinToString(", ")) else Text(if (vm.networkLabel().startsWith("Internet")) "Online" else "Nearby", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(enabled = conversation?.blocked != true, onClick = { call(vm.createCall(conversationId, conversation?.displayTitle() ?: "Mowell call", false)) }) { Icon(Icons.Rounded.Call, "Voice", tint = if (conversation?.blocked == true) Muted else Ink) }
                    IconButton(enabled = conversation?.blocked != true, onClick = { call(vm.createCall(conversationId, conversation?.displayTitle() ?: "Mowell call", true)) }) { Icon(Icons.Rounded.Videocam, "Video", tint = if (conversation?.blocked == true) Muted else Ink) }
                    Box {
                        IconButton(onClick = { headerMenu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Ink) }
                        DropdownMenu(expanded = headerMenu, onDismissRequest = { headerMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (conversation?.isGroup == true) "Group info" else "View profile") },
                                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                                onClick = { headerMenu = false; conversation?.let(profile) }
                            )
                            DropdownMenuItem(
                                text = { Text("Media, links and docs") },
                                leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) },
                                onClick = { headerMenu = false; mediaOpen = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Export chat") },
                                leadingIcon = { Icon(Icons.Rounded.Description, null) },
                                onClick = {
                                    headerMenu = false
                                    val transcript = timelineMessages.joinToString("\n") { item -> "${SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(Date(item.sentAt))} · ${item.sender}: ${item.body}" }
                                    val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Mowell chat with ${conversation?.displayTitle().orEmpty()}"); putExtra(Intent.EXTRA_TEXT, transcript) }
                                    context.startActivity(Intent.createChooser(share, "Export chat"))
                                }
                            )
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
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Canvas)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
                AnimatedVisibility(replyTo != null) {
                    Row(Modifier.fillMaxWidth().background(Lavender).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Replying to ${replyTo?.sender.orEmpty()}", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(replyTo?.body.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 11.sp) }
                        IconButton(onClick = { replyTo = null }) { Text("×", fontSize = 25.sp) }
                    }
                }
                AnimatedVisibility(mentionSuggestions.isNotEmpty()) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 210.dp).background(Canvas)) {
                        items(mentionSuggestions, key = { "mention-${it.user.id}" }) { member ->
                            Row(Modifier.fillMaxWidth().clickable {
                                val marker = text.lastIndexOf('@')
                                if (marker >= 0) text = text.substring(0, marker) + "@${member.user.username} "
                            }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(member.user.displayName, 36.dp, Violet, member.user.avatarUrl); Spacer(Modifier.width(10.dp)); Column { Text(member.user.displayName, fontWeight = FontWeight.SemiBold); Text("@${member.user.username}", color = Muted, fontSize = 11.sp) }
                            }
                        }
                    }
                }
                if (conversation?.blocked == true) {
                    Row(Modifier.fillMaxWidth().background(Peach).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Block, null, tint = Color(0xFFB3261E)); Spacer(Modifier.width(9.dp))
                        Text("Messaging and calling are unavailable for this blocked contact.", Modifier.weight(1f), color = Ink, fontSize = 13.sp)
                        if (conversation.blockedByMe) OutlinedButton(onClick = { vm.setUserBlocked(conversationId, false) }) { Text("Unblock") }
                    }
                } else Column(Modifier.fillMaxWidth().background(ClayWhite).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    TextField(
                        value = text,
                        enabled = !voiceRecording,
                        onValueChange = { value -> text = value.take(8000); vm.updateTyping(conversationId, text.isNotBlank()) },
                        placeholder = { Text(if (voiceRecording) "Recording… tap the microphone to send" else "Type your message...", color = Muted, fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp, max = 96.dp),
                        minLines = 1, maxLines = 4, shape = RoundedCornerShape(0.dp),
                        colors = TextFieldDefaults.colors(focusedContainerColor = ClayWhite, unfocusedContainerColor = ClayWhite, disabledContainerColor = ClayWhite, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() })
                    )
                    Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(enabled = !voiceRecording, onClick = { attachments = true }, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Add, "Share", tint = Muted, modifier = Modifier.size(21.dp)) }
                            DropdownMenu(expanded = attachments, onDismissRequest = { attachments = false }) {
                                DropdownMenuItem(text = { Text("Camera") }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = {
                                    attachments = false
                                    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
                                    val file = File(directory, "mowell_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                    cameraUri = uri; cameraPicker.launch(uri)
                                })
                                DropdownMenuItem(text = { Text("Photo, video or document") }, leadingIcon = { Icon(Icons.Rounded.Description, null) }, onClick = { attachments = false; filePicker.launch("*/*") })
                                DropdownMenuItem(text = { Text("Location") }, leadingIcon = { Icon(Icons.Rounded.LocationOn, null) }, onClick = { attachments = false; vm.shareLocation(conversationId) })
                                DropdownMenuItem(text = { Text("Contact") }, leadingIcon = { Icon(Icons.Rounded.ContactPhone, null) }, onClick = { attachments = false; contactPicker.launch(null) })
                                DropdownMenuItem(text = { Text("Poll or collaborative item") }, leadingIcon = { Icon(Icons.Rounded.Poll, null) }, onClick = { attachments = false; extrasOpen = true })
                            }
                        }
                        IconButton(onClick = { if (voiceRecording) vm.stopVoiceRecording(conversationId) else vm.startVoiceRecording(conversationId) }, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Mic, if (voiceRecording) "Stop and send recording" else "Record voice message", tint = if (voiceRecording) Color(0xFFF44649) else Muted, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { extrasOpen = true }, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.EmojiEmotions, "Emoji and stickers", tint = Muted, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { extrasOpen = true }, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Forum, "Stickers", tint = Muted, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { aiOpen = true }, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.SmartToy, "AI tools", tint = Violet, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { if (text.isNotBlank()) send() }, modifier = Modifier.size(36.dp).clip(CircleShape).background(if (text.isNotBlank()) Violet else MaterialTheme.colorScheme.surfaceVariant)) { Icon(Icons.Rounded.Send, "Send", tint = if (text.isNotBlank()) Color.White else Muted, modifier = Modifier.size(19.dp)) }
                    }
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
        LazyColumn(Modifier.padding(padding).fillMaxSize().background(ChatCanvas), state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (chatQuery.isNotBlank() && displayedMessages.isEmpty()) item(key = "no-search-results") { Text("No matching messages or files", color = Muted, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
            items(displayedMessages, key = { it.id }) { message ->
                val callRoom = if (message.kind == "call") runCatching { JSONObject(message.body).optString("room") }.getOrNull() else null
                MessageClay(message, callEnded = !callRoom.isNullOrBlank() && callRoom in endedRooms, onReply = { replyTo = message }, onLongPress = { actionTarget = message }, onReactionInfo = { reactionTarget = message }, onPollVote = { option -> vm.votePoll(message, option) }, openAttachment = { vm.openAttachment(context, message) }, openContact = { name, phone -> vm.openContact(context, name, phone) }, joinCall = { room, video, group -> call(CallSession(conversationId, conversation?.displayTitle() ?: message.sender, room, video, group, avatarUrl = conversation?.avatarUrl)) })
            }
            if (chatQuery.isBlank() && typing.isNotEmpty()) item(key = "typing-indicator") { TypingBubble(typing.joinToString(", ")) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageClay(message: MessageEntity, callEnded: Boolean, onReply: () -> Unit, onLongPress: () -> Unit, onReactionInfo: () -> Unit, onPollVote: (Int) -> Unit, openAttachment: () -> Unit, openContact: (String, String) -> Unit, joinCall: (String, Boolean, Boolean) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start) {
        Box(Modifier.widthIn(min = 64.dp, max = 286.dp).pointerInput(message.id) { var drag = 0f; detectHorizontalDragGestures(onDragStart = { drag = 0f }, onHorizontalDrag = { change, amount -> change.consume(); drag += amount }, onDragEnd = { if (drag < -80f) onReply() }) }.combinedClickable(onClick = { if (message.kind in setOf("image", "video", "audio", "file") && message.attachmentId != null) openAttachment() }, onLongClick = onLongPress).clip(RoundedCornerShape(12.dp)).background(if (message.outgoing) Violet else MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
            Column {
                val foreground = if (message.outgoing) Color.White else Ink
                if (!message.replyToId.isNullOrBlank()) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (message.outgoing) Color.White.copy(alpha = .12f) else Violet.copy(alpha = .08f)).padding(7.dp)) {
                        Icon(Icons.Rounded.Reply, null, tint = if (message.outgoing) Color.White else Violet, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)); Text("Reply", color = if (message.outgoing) Color.White.copy(alpha = .82f) else Violet, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(5.dp))
                }
                when (message.kind) {
                    "sticker" -> Text(message.body, fontSize = 50.sp, modifier = Modifier.padding(4.dp))
                    "poll" -> {
                        val data = runCatching { JSONObject(message.metadata) }.getOrNull()
                        Text(data?.optString("question").takeUnless { it.isNullOrBlank() } ?: message.body, color = foreground, fontWeight = FontWeight.Bold)
                        val options = data?.optJSONArray("options")
                        val votes = data?.optJSONObject("votes")
                        if (options != null) repeat(options.length()) { index ->
                            val voteCount = votes?.keys()?.asSequence()?.count { key -> votes.optInt(key, -1) == index } ?: 0
                            Row(Modifier.fillMaxWidth().padding(top = 7.dp).border(1.dp, if (message.outgoing) Color.White.copy(alpha = .45f) else Violet, RoundedCornerShape(8.dp)).clickable { onPollVote(index) }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(16.dp).border(1.dp, if (message.outgoing) Color.White else Violet, CircleShape)); Spacer(Modifier.width(8.dp)); Text(options.optString(index), color = foreground, fontSize = 13.sp)
                                Spacer(Modifier.weight(1f)); if (voteCount > 0) Text(voteCount.toString(), color = foreground, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                    "link" -> {
                        val data = runCatching { JSONObject(message.metadata) }.getOrNull()
                        Icon(Icons.Rounded.Link, null, tint = if (message.outgoing) Color.White else Violet)
                        Text(data?.optString("title").takeUnless { it.isNullOrBlank() } ?: "Shared link", color = foreground, fontWeight = FontWeight.Bold)
                        Text(message.body, color = if (message.outgoing) Color.White.copy(alpha = .82f) else Violet, fontSize = 12.sp)
                    }
                    "collaborative_document", "collaborative_whiteboard" -> {
                        Icon(if (message.kind == "collaborative_document") Icons.Rounded.InsertDriveFile else Icons.Rounded.Brush, null, tint = if (message.outgoing) Color.White else Violet)
                        Text(if (message.kind == "collaborative_document") "Collaborative Document" else "Collaborative Whiteboard", color = foreground, fontWeight = FontWeight.Bold)
                        Text(message.body, color = if (message.outgoing) Color.White.copy(alpha = .78f) else Muted, fontSize = 12.sp)
                    }
                    "audio" -> AudioMessageContent(message, foreground)
                    "image", "video", "file" -> {
                        if (message.kind == "image") AttachmentImage(message)
                        else Icon(if (message.kind == "video") Icons.Rounded.Videocam else Icons.Rounded.AttachFile, null, tint = if (message.outgoing) Color.White else Violet)
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
                    else -> Text(message.body, color = foreground, fontSize = 14.sp, lineHeight = 19.sp)
                }
                val reactionObject = runCatching { JSONObject(message.reactions) }.getOrNull()
                if (reactionObject != null && reactionObject.length() > 0) {
                    Row(Modifier.padding(top = 5.dp).clip(RoundedCornerShape(12.dp)).background(if (message.outgoing) Color.White.copy(alpha = .16f) else Canvas).clickable(onClick = onReactionInfo).padding(horizontal = 7.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        reactionObject.keys().forEach { emoji -> Text("$emoji ${reactionObject.optInt(emoji)}", color = foreground, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    if (message.editedAt > 0L) { Text("Edited", color = if (message.outgoing) Color.White.copy(alpha = .72f) else Muted, fontSize = 9.sp, fontStyle = FontStyle.Italic); Spacer(Modifier.width(4.dp)) }
                    Text(time(message.sentAt), color = if (message.outgoing) Color.White.copy(alpha = .72f) else Muted, fontSize = 9.sp)
                    if (message.outgoing) {
                        Spacer(Modifier.width(4.dp))
                        Text(if (message.delivery == "sending") "✓" else "✓✓", color = if (message.delivery == "sent") Color(0xFF5DE2C1) else Color.White.copy(alpha = .7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentImage(message: MessageEntity) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, message.attachmentId) {
        val id = message.attachmentId ?: return@produceState
        value = com.grapaxels.mowell.auth.AuthRepository(context).downloadAttachment(id).getOrNull()?.second?.let { bytes ->
            withContext(Dispatchers.Default) { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        }
    }
    Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap == null) CircularProgressIndicator(Modifier.size(24.dp), color = Violet, strokeWidth = 2.dp)
        else Image(bitmap!!.asImageBitmap(), message.attachmentName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun AudioMessageContent(message: MessageEntity, foreground: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember(message.id) { mutableStateOf<File?>(null) }
    var downloading by remember(message.id) { mutableStateOf(false) }
    var player by remember(message.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(message.id) { mutableStateOf(false) }
    DisposableEffect(message.id) { onDispose { player?.release() } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (file == null && !downloading) {
                val id = message.attachmentId ?: return@IconButton
                downloading = true
                scope.launch {
                    com.grapaxels.mowell.auth.AuthRepository(context).downloadAttachment(id).onSuccess { (_, bytes) ->
                        file = withContext(Dispatchers.IO) { File(context.cacheDir, "audio_${message.id}.bin").apply { writeBytes(bytes) } }
                    }
                    downloading = false
                }
            } else file?.let { audioFile ->
                if (player == null) {
                    player = MediaPlayer().apply { setDataSource(audioFile.absolutePath); prepare(); setOnCompletionListener { playing = false } }
                }
                player?.let { media -> if (media.isPlaying) { media.pause(); playing = false } else { media.start(); playing = true } }
            }
        }) {
            when { downloading -> CircularProgressIndicator(Modifier.size(22.dp), color = foreground, strokeWidth = 2.dp); file == null -> Icon(Icons.Rounded.CloudDownload, "Download audio", tint = foreground); else -> Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = foreground) }
        }
        Column(Modifier.weight(1f)) { Text(message.attachmentName ?: "Audio", color = foreground, fontWeight = FontWeight.SemiBold); Text(when { downloading -> "Downloading…"; file == null -> "Tap to download"; playing -> "Playing"; else -> "Ready to play" }, color = foreground.copy(alpha = .72f), fontSize = 11.sp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatMediaSheet(messages: List<MessageEntity>, dismiss: () -> Unit, open: (MessageEntity) -> Unit) {
    var tab by remember { mutableStateOf("Media") }
    val visible = when (tab) {
        "Links" -> messages.filter { it.kind == "link" || it.body.startsWith("http", true) }
        "Docs" -> messages.filter { it.kind == "file" || it.kind == "document" }
        else -> messages.filter { it.kind in setOf("image", "video", "audio", "sticker") }
    }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Canvas) {
        Text("Media, links and docs", Modifier.padding(horizontal = 20.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Media", "Links", "Docs")) { label -> FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label) }) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
            items(visible, key = { "media-${it.id}" }) { message ->
                Row(Modifier.fillMaxWidth().clickable(enabled = message.attachmentId != null) { open(message); dismiss() }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Icon(when (message.kind) { "image" -> Icons.Rounded.PhotoCamera; "video" -> Icons.Rounded.Videocam; "audio" -> Icons.Rounded.Mic; "link" -> Icons.Rounded.Link; else -> Icons.Rounded.Description }, null, tint = Violet)
                    }
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(message.attachmentName ?: message.body.take(60), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date(message.sentAt)), color = Muted, fontSize = 12.sp) }
                }
            }
            if (visible.isEmpty()) item { Text("No ${tab.lowercase()} shared in this chat.", color = Muted, modifier = Modifier.padding(24.dp)) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageOptionsSheet(
    message: MessageEntity,
    dismiss: () -> Unit,
    react: (String) -> Unit,
    info: () -> Unit,
    copy: () -> Unit,
    edit: () -> Unit,
    reply: () -> Unit,
    thread: () -> Unit,
    translate: () -> Unit,
    delete: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Canvas) {
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            items(listOf("😍", "👍", "🔥", "😊", "❤️", "😄")) { emoji ->
                Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { react(emoji) }, contentAlignment = Alignment.Center) { Text(emoji, fontSize = 23.sp) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SheetAction(Icons.Rounded.Info, "Info", click = info)
        SheetAction(Icons.Rounded.ContentCopy, "Copy") { clipboard.setText(AnnotatedString(message.body)); copy(); dismiss() }
        if (message.outgoing && message.kind == "text") SheetAction(Icons.Rounded.Edit, "Edit", click = edit)
        SheetAction(Icons.Rounded.Reply, "Reply", click = reply)
        SheetAction(Icons.Rounded.Forum, "Reply in Thread", click = thread)
        if (message.kind == "text") SheetAction(Icons.Rounded.Translate, "Translate", click = translate)
        SheetAction(Icons.Rounded.Delete, "Delete", color = Color(0xFFFF3B4F), click = delete)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, color: Color = Ink, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(14.dp)); Text(label, color = color, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageInfoSheet(message: MessageEntity, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Canvas) {
        Text("Message Info", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message.body, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
            InfoLine("Sent", SimpleDateFormat("dd MMM yyyy, h:mm:ss a", Locale.getDefault()).format(Date(message.sentAt)))
            InfoLine("Delivered by", route(message.route).lowercase().replaceFirstChar { it.uppercase() })
            InfoLine("Status", message.delivery.replaceFirstChar { it.uppercase() })
            if (message.editedAt > 0) InfoLine("Edited", SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date(message.editedAt)))
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), color = Muted); Text(value, color = Ink, fontWeight = FontWeight.Medium) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReactionInfoSheet(message: MessageEntity, dismiss: () -> Unit) {
    val counts = runCatching { JSONObject(message.reactions) }.getOrElse { JSONObject() }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Canvas) {
        Text("Reactions", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(10.dp))
        if (counts.length() == 0) Text("No reactions yet", color = Muted, modifier = Modifier.padding(20.dp))
        else counts.keys().forEach { emoji ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) { Text(emoji, fontSize = 25.sp); Spacer(Modifier.width(14.dp)); Text("${counts.optInt(emoji)} reaction${if (counts.optInt(emoji) == 1) "" else "s"}", Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslationSheet(message: MessageEntity, dismiss: () -> Unit) {
    var language by remember { mutableStateOf("Hindi") }
    val translated = remember(message.body, language) { offlineTranslate(message.body, language) }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Canvas) {
        Text("Message Translated", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Hindi", "Spanish", "French")) { item -> FilterChip(selected = language == item, onClick = { language = item }, label = { Text(item) }) }
        }
        Column(Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp)) {
            Text(message.body, color = Muted, fontSize = 12.sp); Spacer(Modifier.height(9.dp)); Text(translated, color = Ink, fontSize = 15.sp)
        }
        Text("Quick translation runs locally on this phone.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(18.dp))
    }
}

private fun offlineTranslate(value: String, language: String): String {
    val dictionaries = mapOf(
        "Hindi" to mapOf("hello" to "नमस्ते", "thank you" to "धन्यवाद", "yes" to "हाँ", "no" to "नहीं", "how are you" to "आप कैसे हैं"),
        "Spanish" to mapOf("hello" to "hola", "thank you" to "gracias", "yes" to "sí", "no" to "no", "how are you" to "cómo estás"),
        "French" to mapOf("hello" to "bonjour", "thank you" to "merci", "yes" to "oui", "no" to "non", "how are you" to "comment allez-vous")
    )
    val normalized = value.trim().lowercase()
    return dictionaries[language]?.get(normalized) ?: "$value · $language"
}

@Composable
private fun ThreadView(vm: MowellViewModel, conversationId: String, root: MessageEntity, replies: List<MessageEntity>, back: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Scaffold(containerColor = Canvas, topBar = { CometBackHeader("Thread", back) }, bottomBar = {
        Row(Modifier.fillMaxWidth().background(Canvas).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it.take(8000) }, placeholder = { Text("Reply in thread") }, modifier = Modifier.weight(1f), maxLines = 4)
            IconButton(enabled = text.isNotBlank(), onClick = { vm.sendRich(conversationId, text, "text", threadRootId = root.id); text = "" }, modifier = Modifier.size(50.dp).clip(CircleShape).background(Violet)) { Icon(Icons.Rounded.Send, null, tint = Color.White) }
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(root.sender, color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(root.body, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)); HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outline) }
            items(replies, key = { "thread-${it.id}" }) { reply ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(reply.sender, color = if (reply.outgoing) Violet else Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text(reply.body, fontSize = 14.sp); Text(time(reply.sentAt), color = Muted, fontSize = 10.sp) }
            }
            if (replies.isEmpty()) item { Text("No replies yet", color = Muted) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalAiSheet(messages: List<MessageEntity>, dismiss: () -> Unit, send: (String) -> Unit) {
    var view by remember { mutableStateOf("menu") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    val lastIncoming = messages.lastOrNull { !it.outgoing }?.body.orEmpty()
    val suggestions = when {
        lastIncoming.contains("?", true) -> listOf("Yes, that works for me.", "Let me check and get back to you.", "Could you share a few more details?")
        lastIncoming.contains("thank", true) -> listOf("You’re welcome!", "Happy to help.", "Anytime!")
        else -> listOf("Sounds good!", "Got it, thanks.", "I’ll get back to you shortly.")
    }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Canvas) {
        when (view) {
            "reply" -> { Text("Suggest a reply", Modifier.padding(20.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp); suggestions.forEach { value -> SheetAction(Icons.Rounded.ChatBubble, value) { send(value) } } }
            "summary" -> { Text("Conversation Summary", Modifier.padding(20.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp); Text(messages.takeLast(8).joinToString(" ") { "${it.sender}: ${it.body.take(80)}" }.ifBlank { "There are no messages to summarize yet." }, Modifier.padding(horizontal = 20.dp), color = Muted, lineHeight = 20.sp) }
            "starter" -> { Text("Conversation Starter", Modifier.padding(20.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp); listOf("How has your day been?", "What are you working on today?", "Want to catch up this week?").forEach { SheetAction(Icons.Rounded.Forum, it) { send(it) } } }
            "bot" -> {
                Text("Ask AI Bot", Modifier.padding(20.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(question, { question = it.take(300) }, label = { Text("Ask about this conversation") }, modifier = Modifier.fillMaxWidth())
                    Button(enabled = question.isNotBlank(), onClick = {
                        val terms = question.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
                        val relevant = messages.asReversed().firstOrNull { item -> terms.any { item.body.contains(it, true) } }
                        answer = relevant?.let { "${it.sender} said: ${it.body}" } ?: "I couldn’t find that in this conversation."
                    }, modifier = Modifier.fillMaxWidth()) { Text("Ask") }
                    if (answer.isNotBlank()) Text(answer, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp), color = Ink)
                    Text("Answers are generated locally from messages stored on this phone.", color = Muted, fontSize = 11.sp)
                }
            }
            else -> { SheetAction(Icons.Rounded.ChatBubble, "Suggest a reply") { view = "reply" }; SheetAction(Icons.Rounded.SmartToy, "Conversation summary") { view = "summary" }; SheetAction(Icons.Rounded.Forum, "Conversation starter") { view = "starter" }; SheetAction(Icons.Rounded.SmartToy, "Ask AI Bot") { view = "bot" } }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RichMessageSheet(dismiss: () -> Unit, send: (String, String, JSONObject) -> Unit) {
    var type by remember { mutableStateOf("sticker") }
    var title by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Canvas) {
        Text("Create", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("sticker", "poll", "link", "document", "whiteboard")) { item -> FilterChip(selected = type == item, onClick = { type = item; title = ""; second = "" }, label = { Text(item.replaceFirstChar { it.uppercase() }) }) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (type == "sticker") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) { items(listOf("😀", "🥳", "❤️", "👍", "🔥", "🎉")) { emoji -> Text(emoji, fontSize = 46.sp, modifier = Modifier.clickable { send(emoji, "sticker", JSONObject().put("shape", "square")) }) } }
            } else {
                OutlinedTextField(title, { title = it.take(500) }, label = { Text(when (type) { "poll" -> "Question"; "link" -> "Link title"; else -> "Name" }) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(second, { second = it.take(1000) }, label = { Text(when (type) { "poll" -> "Options, separated by commas"; "link" -> "https://…"; else -> "Description" }) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Button(enabled = title.isNotBlank() && second.isNotBlank(), onClick = {
                    when (type) {
                        "poll" -> send(title, "poll", JSONObject().put("question", title).put("options", org.json.JSONArray(second.split(',').map(String::trim).filter(String::isNotBlank))))
                        "link" -> send(second, "link", JSONObject().put("title", title).put("url", second))
                        "document" -> send(title, "collaborative_document", JSONObject().put("description", second))
                        else -> send(title, "collaborative_whiteboard", JSONObject().put("description", second))
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Send") }
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, color: Color, click: () -> Unit) { Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = click, Modifier.size(64.dp).clip(CircleShape).background(color)) { Icon(icon, label, tint = Color.White) }; Text(label, color = Color.White, fontSize = 11.sp) } }

@Composable
private fun ClayCard(color: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 360.dp).padding(horizontal = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Muted.copy(alpha = .35f), modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(18.dp))
        Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
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
