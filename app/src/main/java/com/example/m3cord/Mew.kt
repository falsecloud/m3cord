@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.example.m3cord

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

object MotionPrefs {
    var reduced by mutableStateOf(false)
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Minimalist cat head: two ears, round face, eyes and nose cut out. */
val CatHead: ImageVector by lazy {
    ImageVector.Builder("CatHead", 24.dp, 24.dp, 24f, 24f).path(
        fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd
    ) {
        moveTo(3.5f, 3f)
        lineTo(9.2f, 6.6f)
        curveTo(10.1f, 6.4f, 11f, 6.3f, 12f, 6.3f)
        curveTo(13f, 6.3f, 13.9f, 6.4f, 14.8f, 6.6f)
        lineTo(20.5f, 3f)
        lineTo(20.5f, 11.2f)
        curveTo(21.4f, 12.4f, 22f, 13.9f, 22f, 15.4f)
        curveTo(22f, 19.2f, 17.6f, 21.5f, 12f, 21.5f)
        curveTo(6.4f, 21.5f, 2f, 19.2f, 2f, 15.4f)
        curveTo(2f, 13.9f, 2.6f, 12.4f, 3.5f, 11.2f)
        close()
        moveTo(8f, 13.2f)
        arcToRelative(1.3f, 1.3f, 0f, true, false, 2.6f, 0f)
        arcToRelative(1.3f, 1.3f, 0f, true, false, -2.6f, 0f)
        close()
        moveTo(13.4f, 13.2f)
        arcToRelative(1.3f, 1.3f, 0f, true, false, 2.6f, 0f)
        arcToRelative(1.3f, 1.3f, 0f, true, false, -2.6f, 0f)
        close()
        moveTo(11f, 16f)
        lineTo(13f, 16f)
        lineTo(12f, 17.3f)
        close()
    }.build()
}

// ---------- the floating mew button ----------

@Composable
fun MewButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick, interactionSource = src, shape = CircleShape, color = cs.surfaceContainerHigh,
        tonalElevation = 6.dp, shadowElevation = 14.dp,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.size(64.dp).enter(0).pressScale(src, 0.86f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(CatHead, "Mew menu", Modifier.size(30.dp), tint = cs.primary)
        }
    }
}

/** The button grows into the full screen menu, the cat flies to the top and turns into the title. */
@Composable
fun MewMenuOverlay(vm: MainViewModel, r: Rect, p0: Float, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val p = p0.coerceIn(0f, 1f)
    val d = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(d).toFloat()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w0 = constraints.maxWidth.toFloat()
        val h0 = constraints.maxHeight.toFloat()
        val left = lerpF(r.left, 0f, p)
        val top = lerpF(r.top, 0f, p)
        val w = lerpF(r.width, w0, p)
        val h = lerpF(r.height, h0, p)
        val corner = with(d) { lerpF(r.height / 2f, 0f, p).toDp() }
        Surface(
            modifier = Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(d) { w.toDp() }, with(d) { h.toDp() })
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(corner),
            color = lerp(cs.surfaceContainerHigh, cs.surface, p),
            tonalElevation = 6.dp * (1f - p), shadowElevation = 14.dp * (1f - p),
            border = if (p < 0.97f) BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f * (1f - p))) else null
        ) {
            val ca = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
            Box(
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(with(d) { w0.toDp() }, with(d) { h0.toDp() })
                    .graphicsLayer { alpha = ca }
            ) { MewMenuContent(vm, onClose) }
        }
        val iconPx = with(d) { 30.dp.toPx() }
        val startX = with(d) { 20.dp.toPx() }
        val rowH = with(d) { 64.dp.toPx() }
        val cx = lerpF(r.center.x, startX + iconPx / 2f, p)
        val cy = lerpF(r.center.y, statusTop + rowH / 2f, p)
        val t = ((p - 0.55f) / 0.4f).coerceIn(0f, 1f)
        Icon(
            CatHead, null, tint = cs.primary,
            modifier = Modifier.size(30.dp)
                .offset { IntOffset((cx - iconPx / 2f).roundToInt(), (cy - iconPx / 2f).roundToInt()) }
                .graphicsLayer { alpha = 1f - t; scaleX = 1f - 0.5f * t; scaleY = 1f - 0.5f * t }
        )
        Box(
            Modifier.offset { IntOffset(startX.roundToInt(), statusTop.roundToInt()) }.height(64.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "Mew Menu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = cs.onSurface,
                modifier = Modifier.graphicsLayer { alpha = t; scaleX = 0.35f + 0.65f * t; transformOrigin = TransformOrigin(0f, 0.5f) }
            )
        }
    }
}

@Composable
private fun MewMenuContent(vm: MainViewModel, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            XIconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close menu") }
        }
        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)) {
            Segmented(listOf("Mew", "Discord"), tab) { tab = it }
        }
        AnimatedContent(
            targetState = tab, modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(bounce(0.85f, 500f)) { it * dir / 4 } + fadeIn(bounce(1f, 500f))) togetherWith
                    (slideOutHorizontally(bounce(0.85f, 500f)) { -it * dir / 4 } + fadeOut(bounce(1f, 500f)))
            },
            label = "mewtabs"
        ) { t -> if (t == 0) MewSettings(vm) else DiscordSettings(vm) }
    }
}

// ---------- shared setting widgets ----------

@Composable
fun ToggleRow(icon: ImageVector, title: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val tint by animateColorAsState(if (checked) cs.primary else cs.outline, bounce(1f, 400f), label = "toggletint")
    val sc by animateFloatAsState(if (checked) 1f else 0.85f, bounce(0.4f, 500f), label = "togglescale")
    Row(
        Modifier.fillMaxWidth().pressScale(src, 0.97f).clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = src, indication = null) { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.graphicsLayer { scaleX = sc; scaleY = sc })
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            thumbContent = {
                AnimatedVisibility(checked, enter = scaleIn(bounce(0.4f, 500f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Icon(Icons.Filled.Check, null, Modifier.size(SwitchDefaults.IconSize))
                }
            }
        )
    }
}

@Composable
fun ExpandSection(
    icon: ImageVector, title: String, expanded: Boolean, index: Int, onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val rot by animateFloatAsState(if (expanded) 180f else 0f, bounce(0.7f, 400f), label = "chevron")
    val bg by animateColorAsState(if (expanded) cs.surfaceContainerHigh else cs.surfaceContainer, bounce(1f, 400f), label = "secbg")
    Surface(
        shape = RoundedCornerShape(28.dp), color = bg,
        modifier = Modifier.fillMaxWidth().enter(index).animateContentSize(bounce(0.8f, 400f))
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().pressScale(src, 0.98f)
                    .clickable(interactionSource = src, indication = null, onClick = onToggle)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = cs.primary)
                Spacer(Modifier.width(14.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.graphicsLayer { rotationZ = rot })
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(bounce(0.85f, 400f)) + fadeIn(),
                exit = shrinkVertically(bounce(1f, 400f)) + fadeOut()
            ) {
                Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 14.dp), content = content)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NotifToggle(vm: MainViewModel) {
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Notifier.sync(ctx) }
    ToggleRow(Icons.Filled.Notifications, "Notifications", "DMs and mentions (keeps a quiet status notification)", vm.notif) { on ->
        vm.setNotif(on)
        if (on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

// ---------- Mew tab: what M3w changes ----------

@Composable
fun MewSettings(vm: MainViewModel) {
    val dynActive = vm.dynamic && Build.VERSION.SDK_INT >= 31
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsCard {
            SettingRow(Icons.Filled.Palette, "Theme")
            Segmented(listOf("System", "Light", "Dark"), vm.themeMode) { vm.setTheme(it) }
            Spacer(Modifier.height(6.dp))
            if (Build.VERSION.SDK_INT >= 31) {
                ToggleRow(Icons.Filled.Brush, "Dynamic color", "Match your wallpaper", vm.dynamic) { vm.setDynamic(it) }
            }
            SettingRow(Icons.Filled.ColorLens, "Accent color", if (dynActive) "Turn off dynamic color to use these" else null)
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ACCENTS.forEachIndexed { i, (_, c) ->
                    val sel = vm.accent == i && !dynActive
                    val size by androidx.compose.animation.core.animateDpAsState(if (sel) 46.dp else 38.dp, bounce(0.5f, 400f), label = "accent")
                    Box(
                        Modifier.size(size).clip(CircleShape).background(Color(c).copy(alpha = if (dynActive) 0.35f else 1f))
                            .clickable(enabled = !dynActive) { vm.setAccent(i) },
                        contentAlignment = Alignment.Center
                    ) { androidx.compose.animation.AnimatedVisibility(sel, enter = scaleIn(bounce(0.4f, 500f)) + fadeIn(), exit = scaleOut() + fadeOut()) { Icon(Icons.Filled.Check, null, tint = Color.White) } }
                }
            }
        }
        SettingsCard {
            ToggleRow(Icons.Filled.KeyboardArrowDown, "Hide the pill while scrolling", "The dock slides away so lists get more room", vm.dockAutoHide) { vm.setDockAutoHide(it) }
            ToggleRow(Icons.Filled.Info, "Reduce motion", "Swap the bouncy springs for short, calm fades", vm.reduceMotion) { vm.setReduceMotion(it) }
        }
        SettingsCard {
            ToggleRow(Icons.Filled.Chat, "Last message in Chats", "Show the latest message under each DM name", vm.dmPreviews) { vm.setDmPreviews(it) }
            SettingRow(Icons.Filled.Chat, "Group messages within")
            Segmented(listOf("1 min", "5 min", "10 min"), listOf(1, 5, 10).indexOf(vm.groupMins).coerceAtLeast(0)) { vm.setGroupMins(listOf(1, 5, 10)[it]) }
            Spacer(Modifier.height(6.dp))
            ToggleRow(Icons.Filled.PlayCircle, "Autoplay GIF videos", "Tenor and Giphy style embeds", vm.autoplay) { vm.setAutoplay(it) }
            ToggleRow(Icons.Filled.Info, "Server unread dots", "Small white dots for servers and channels with new messages", UnreadStore.serverDots) { UnreadStore.setServerDots(it) }
        }
        SettingsCard {
            SettingRow(Icons.Filled.Face, "Status", if (vm.gwConnected) "Live connection active" else "Connecting...")
            Segmented(listOf("Online", "Idle", "DND", "Hidden"), listOf("online", "idle", "dnd", "invisible").indexOf(vm.status).coerceAtLeast(0)) {
                vm.setStatus(listOf("online", "idle", "dnd", "invisible")[it])
            }
            Spacer(Modifier.height(6.dp))
            NotifToggle(vm)
        }
        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
}

// ---------- Discord tab: your real account settings ----------

private val LOCALES = listOf(
    "en-US" to "English (US)", "en-GB" to "English (UK)", "es-ES" to "Español", "fr" to "Français", "de" to "Deutsch",
    "pt-BR" to "Português (BR)", "it" to "Italiano", "nl" to "Nederlands", "ru" to "Русский", "ja" to "日本語",
    "ko" to "한국어", "zh-CN" to "中文", "tr" to "Türkçe", "pl" to "Polski"
)

@Composable
private fun DToggle(vm: MainViewModel, key: String, icon: ImageVector, title: String, sub: String? = null, default: Boolean = false) {
    ToggleRow(icon, title, sub, vm.dB(key, default)) { vm.setD(key, it) }
}

@Composable
private fun ListSection(items: List<InfoRow>?, emptyText: String) {
    when {
        items == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(40.dp)) }
        items.isEmpty() -> Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        else -> items.forEachIndexed { i, row ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).enter(i)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (row.sub.isNotBlank()) Text(row.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DiscordSettings(vm: MainViewModel) {
    var open by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.loadDiscordSettings() }
    LaunchedEffect(open) {
        when (open) {
            "account" -> vm.loadAccount()
            "connections" -> vm.loadConnections()
            "devices" -> vm.loadSessions()
            "apps" -> vm.loadApps()
        }
    }
    fun toggle(id: String) { open = if (open == id) null else id }
    val me = vm.me
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "These are the real settings on your Discord account, so changes follow you to every device.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
        AnimatedVisibility(vm.dsetLoading) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(36.dp)) }
        }
        ExpandSection(Icons.Filled.AccountCircle, "My Account", open == "account", 0, { toggle("account") }) {
            InfoLine("Display name", me?.name ?: "")
            InfoLine("Username", "@" + (me?.username ?: ""))
            val a = vm.acct
            if (a == null) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(32.dp)) }
            else {
                InfoLine("Email", a.email ?: "Not set")
                InfoLine("Phone", a.phone ?: "Not set")
                InfoLine("Email verified", if (a.verified) "Yes" else "No")
                InfoLine("Two-factor auth", if (a.mfa) "On" else "Off")
                InfoLine("Nitro", if (a.nitro) "Active" else "None")
            }
            Spacer(Modifier.height(8.dp))
            XTonalButton(onClick = { vm.startEdit() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Edit profile")
            }
            Text(
                "Changing email, password or 2FA isn't supported here yet, use discord.com for those.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            XTonalButton(
                onClick = vm::logout, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) { Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(10.dp)); Text("Log out") }
        }
        ExpandSection(Icons.Filled.Palette, "Appearance", open == "appearance", 1, { toggle("appearance") }) {
            SettingRow(Icons.Filled.DarkMode, "Discord theme", "Used by the official Discord apps")
            Segmented(listOf("Dark", "Light"), if (vm.dS("theme", "dark") == "light") 1 else 0) { vm.setD("theme", if (it == 1) "light" else "dark") }
            Spacer(Modifier.height(6.dp))
            DToggle(vm, "message_display_compact", Icons.Filled.Menu, "Compact message display", "Fit more messages on screen")
        }
        ExpandSection(Icons.Filled.Visibility, "Accessibility", open == "access", 2, { toggle("access") }) {
            DToggle(vm, "animate_emoji", Icons.Filled.EmojiEmotions, "Play animated emoji", default = true)
            DToggle(vm, "gif_auto_play", Icons.Filled.PlayCircle, "Play GIFs automatically", default = true)
            DToggle(vm, "convert_emoticons", Icons.Filled.Face, "Convert :) into emoji", default = true)
            SettingRow(Icons.Filled.Image, "Animate stickers")
            Segmented(listOf("Always", "On hover", "Never"), vm.dI("animate_stickers", 0).coerceIn(0, 2)) { vm.setD("animate_stickers", it) }
            Spacer(Modifier.height(6.dp))
        }
        ExpandSection(Icons.Filled.Image, "Text & Images", open == "text", 3, { toggle("text") }) {
            DToggle(vm, "inline_attachment_media", Icons.Filled.Image, "Show uploaded images and videos", default = true)
            DToggle(vm, "inline_embed_media", Icons.Filled.Link, "Show images from links", default = true)
            DToggle(vm, "render_embeds", Icons.Filled.Info, "Show website previews", default = true)
            DToggle(vm, "render_reactions", Icons.Filled.Favorite, "Show emoji reactions", default = true)
        }
        ExpandSection(Icons.Filled.Lock, "Privacy & Safety", open == "privacy", 4, { toggle("privacy") }) {
            SettingRow(Icons.Filled.Lock, "Scan media in DMs")
            Segmented(listOf("Off", "Non-friends", "Everyone"), vm.dI("explicit_content_filter", 1).coerceIn(0, 2)) { vm.setD("explicit_content_filter", it) }
            Spacer(Modifier.height(6.dp))
            ToggleRow(Icons.Filled.Chat, "Allow DMs from server members", "Default for new servers you join", !vm.dB("default_guilds_restricted", false)) { vm.setD("default_guilds_restricted", !it) }
            SettingRow(Icons.Filled.Person, "Who can send you friend requests")
            ToggleRow(Icons.Filled.Groups, "Everyone", null, vm.dFlag("all")) { vm.setFriendFlag("all", it) }
            ToggleRow(Icons.Filled.Groups, "Friends of friends", null, vm.dFlag("mutual_friends")) { vm.setFriendFlag("mutual_friends", it) }
            ToggleRow(Icons.Filled.Groups, "Server members", null, vm.dFlag("mutual_guilds")) { vm.setFriendFlag("mutual_guilds", it) }
            DToggle(vm, "show_current_game", Icons.Filled.PlayCircle, "Share your activity status", "Show what you're playing or listening to", default = true)
        }
        ExpandSection(Icons.Filled.Notifications, "Notifications", open == "notifs", 5, { toggle("notifs") }) { NotifToggle(vm) }
        ExpandSection(Icons.Filled.Language, "Language", open == "lang", 6, { toggle("lang") }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val cur = vm.dS("locale", "en-US")
                LOCALES.forEach { (code, label) ->
                    FilterChip(selected = cur == code, onClick = { vm.setD("locale", code) }, label = { Text(label) })
                }
            }
        }
        ExpandSection(Icons.Filled.Link, "Connections", open == "connections", 7, { toggle("connections") }) { ListSection(vm.connections, "No connected accounts.") }
        ExpandSection(Icons.Filled.Devices, "Devices", open == "devices", 8, { toggle("devices") }) { ListSection(vm.sessions, "No active sessions found.") }
        ExpandSection(Icons.Filled.Apps, "Authorized Apps", open == "apps", 9, { toggle("apps") }) { ListSection(vm.apps, "You haven't authorized any apps.") }
        ExpandSection(Icons.Filled.Build, "Advanced", open == "advanced", 10, { toggle("advanced") }) {
            DToggle(vm, "developer_mode", Icons.Filled.Build, "Developer mode", "Adds extra options like copy ID")
        }
        Text(
            "Not here yet: Nitro and billing, voice and video, keybinds, streamer mode.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
}
