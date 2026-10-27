@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.example.m3cord

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// ---------- login ----------

@Composable
fun LoginScreen(vm: MainViewModel) {
    var tok by remember { mutableStateOf("") }
    val src = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("M3Cord", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, modifier = Modifier.enter(0))
        Spacer(Modifier.height(8.dp))
        Text("paste your account token to jump in", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.enter(1))
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = tok, onValueChange = { tok = it }, label = { Text("Token") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().enter(2)
        )
        AnimatedVisibility(vm.error != null) {
            Text(vm.error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(20.dp))
        XButton(
            onClick = { vm.login(tok) }, enabled = !vm.loading && tok.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(60.dp).enter(3)
        ) {
            if (vm.loading) LoadingIndicator(Modifier.size(34.dp), color = LocalContentColor.current)
            else Text("Log in", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "unofficial client, using a user token can get an account flagged. use at your own risk.",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.enter(4)
        )
    }
}

// ---------- main (tabs) ----------

@Composable
fun MainScreen(vm: MainViewModel) {
    var dockVisible by remember { mutableStateOf(true) }
    val conn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -10f) dockVisible = false else if (available.y > 10f) dockVisible = true
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(vm.tab) { dockVisible = true }
    val hide by animateFloatAsState(if (dockVisible) 0f else 1f, bounce(0.75f, 450f), label = "dock")
    Box(Modifier.fillMaxSize().nestedScroll(conn)) {
        AnimatedContent(
            targetState = vm.tab,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            transitionSpec = {
                val d = if (targetState > initialState) 1 else -1
                (slideInHorizontally(bounce(0.85f, 500f)) { it * d / 4 } + fadeIn(bounce(1f, 500f))) togetherWith
                    (slideOutHorizontally(bounce(0.85f, 500f)) { -it * d / 4 } + fadeOut(bounce(1f, 500f)))
            },
            label = "tabs"
        ) { t ->
            when (t) {
                0 -> ServersTab(vm)
                1 -> DmsTab(vm)
                else -> YouTab(vm)
            }
        }
        PillNav(
            vm,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp).graphicsLayer {
                translationY = hide * (size.height + 90.dp.toPx())
                alpha = 1f - hide * 0.5f
            }
        )
    }
}

@Composable
fun PillNav(vm: MainViewModel, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val items = listOf(
        Triple("Servers", Icons.Filled.Home, Icons.Outlined.Home),
        Triple("Chats", Icons.Filled.Chat, Icons.Outlined.Chat),
        Triple("You", Icons.Filled.Person, Icons.Outlined.Person)
    )
    val serverBadge = UnreadStore.guildBadge.values.sum()
    val serverDot = UnreadStore.guildQuiet.isNotEmpty()
    val dmBadge = vm.dms.sumOf { UnreadStore.badge[it.id] ?: 0 }
    Surface(
        shape = RoundedCornerShape(50), color = cs.surfaceContainerHigh,
        tonalElevation = 6.dp, shadowElevation = 14.dp,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.enter(0)
    ) {
        Row(
            Modifier.padding(8.dp).height(56.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEachIndexed { i, (label, on, off) ->
                PillItem(label, if (vm.tab == i) on else off, vm.tab == i, badge = {
                    val n = if (i == 0) serverBadge else if (i == 1) dmBadge else 0
                    if (n > 0) PopBadge { Text(n.toString()) } else if (i == 0 && serverDot) QuietDot()
                }) { vm.tab = i; if (i == 1) vm.loadDms() }
            }
        }
    }
}

@Composable
private fun PillItem(label: String, icon: ImageVector, selected: Boolean, badge: @Composable BoxScope.() -> Unit, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) cs.primaryContainer else Color.Transparent, bounce(1f, 400f), label = "pillbg")
    val fg by animateColorAsState(if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant, bounce(1f, 400f), label = "pillfg")
    val iconScale by animateFloatAsState(if (selected) 1.15f else 1f, bounce(0.4f, 500f), label = "pilliconscale")
    Row(
        Modifier.fillMaxHeight().pressScale(src, 0.88f).clip(RoundedCornerShape(50)).background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp).animateContentSize(bounce(0.7f, 400f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgedBox(badge = badge) {
            Icon(icon, label, tint = fg, modifier = Modifier.size(26.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
        }
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(bounce(0.7f, 400f)) + fadeIn(),
            exit = shrinkHorizontally(bounce(1f, 400f)) + fadeOut()
        ) {
            Text(
                label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun ScreenTitle(text: String) {
    AnimatedContent(
        targetState = text,
        transitionSpec = { (slideInVertically(bounce(0.8f, 500f)) { it / 2 } + fadeIn(bounce(1f, 500f))) togetherWith (slideOutVertically(bounce(0.8f, 500f)) { -it / 2 } + fadeOut(bounce(1f, 500f))) },
        label = "title"
    ) { t ->
        Text(
            t, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 4.dp)
        )
    }
}

@Composable
fun ServersTab(vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        val gid = vm.selected
        val hasUnread = gid != null && ((UnreadStore.guildBadge[gid] ?: 0) > 0 || UnreadStore.guildQuiet[gid] == true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ScreenTitle(vm.guilds.firstOrNull { it.id == gid }?.name ?: "Servers") }
            AnimatedVisibility(hasUnread, enter = scaleIn(bounce(0.5f, 400f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                XIconButton(onClick = { gid?.let { UnreadStore.clearGuild(it) } }) { Icon(Icons.Filled.DoneAll, "Mark server as read") }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(vm.guilds, key = { _, g -> g.id }) { gi, g ->
                ServerBubble(
                    g.name.take(1).uppercase(),
                    g.icon?.let { "https://cdn.discordapp.com/icons/${g.id}/${it}.png?size=96" },
                    vm.selected == g.id, unread = UnreadStore.guildBadge[g.id] ?: 0, quiet = UnreadStore.guildQuiet[g.id] == true,
                    modifier = Modifier.animateItem().enter(gi)
                ) { vm.selectGuild(g.id) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 110.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    vm.entries,
                    key = { _, e -> when (e) { is Entry.Header -> "h" + e.id; is Entry.Item -> e.ch.id } }
                ) { i, e ->
                    when (e) {
                        is Entry.Header -> Text(
                            e.name.uppercase(), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.animateItem().padding(start = 8.dp, top = 14.dp, bottom = 2.dp)
                        )
                        is Entry.Item -> {
                            val src = remember { MutableInteractionSource() }
                            Surface(
                                onClick = { vm.openChannel(e.ch) }, interactionSource = src,
                                shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.animateItem().enter(i).pressScale(src).fillMaxWidth()
                            ) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (e.ch.type == 5) Icons.Filled.Campaign else Icons.Filled.Tag, null,
                                        Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    val un = UnreadStore.badge[e.ch.id] ?: 0
                                    val q = UnreadStore.quiet[e.ch.id] == true
                                    Text(
                                        e.ch.name, style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (un > 0 || q) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f)
                                    )
                                    if (un > 0) PopBadge { Text(un.toString()) } else if (q) QuietDot()
                                }
                            }
                        }
                    }
                }
            }
            if (vm.loading && vm.entries.isEmpty()) LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp))
        }
    }
}

@Composable
fun QuietDot(modifier: Modifier = Modifier) {
    val sc = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sc.animateTo(1f, bounce(0.4f, 500f)) }
    Box(
        modifier.size(9.dp).graphicsLayer { scaleX = sc.value; scaleY = sc.value }
            .background(Color.White, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
    )
}

@Composable
fun PopBadge(content: @Composable RowScope.() -> Unit) {
    Box(Modifier.pop()) { Badge(content = content) }
}

@Composable
fun ServerBubble(
    label: String, iconUrl: String?, selected: Boolean, unread: Int = 0, quiet: Boolean = false,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val radius by animateDpAsState(if (selected) 18.dp else 28.dp, bounce(0.55f, 400f), label = "radius")
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        bounce(1f, 400f), label = "bg"
    )
    val src = remember { MutableInteractionSource() }
    BadgedBox(modifier = modifier, badge = { if (unread > 0) PopBadge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
        Box {
            Box(
                Modifier.size(56.dp).pressScale(src).clip(RoundedCornerShape(radius)).background(bg)
                    .clickable(interactionSource = src, indication = null, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                if (iconUrl != null) AsyncImage(iconUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            if (quiet && unread == 0) QuietDot(Modifier.align(Alignment.BottomCenter).offset(y = 8.dp))
        }
    }
}

@Composable
fun DmsTab(vm: MainViewModel) {
    var sub by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(if (sub == 0) "Chats" else "Friends")
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Segmented(listOf("Chats", "Friends"), sub) { sub = it; if (it == 1) vm.loadFriends() }
        }
        AnimatedContent(
            targetState = sub, modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val d = if (targetState > initialState) 1 else -1
                (slideInHorizontally(bounce(0.85f, 500f)) { it * d / 4 } + fadeIn(bounce(1f, 500f))) togetherWith
                    (slideOutHorizontally(bounce(0.85f, 500f)) { -it * d / 4 } + fadeOut(bounce(1f, 500f)))
            },
            label = "sub"
        ) { t -> if (t == 0) DmList(vm) else FriendList(vm) }
    }
}

@Composable
fun LazyItemScope.PersonRow(avatar: String?, name: String, index: Int, status: String? = null, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick, interactionSource = src, shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.animateItem().enter(index).pressScale(src).fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(avatar, name, 48.dp, status = status)
            Spacer(Modifier.width(14.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
            trailing()
        }
    }
}

@Composable
fun DmList(vm: MainViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(vm.dms, key = { _, c -> c.id }) { i, c ->
            PersonRow(c.icon, c.name, i, status = c.recipientId?.let { vm.presence[it] }, trailing = {
                val un = UnreadStore.badge[c.id] ?: 0
                if (un > 0) PopBadge { Text(un.toString()) }
                if (c.type == 3) Icon(Icons.Filled.Groups, "Group", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }) { vm.openChannel(c) }
        }
    }
}

@Composable
fun FriendList(vm: MainViewModel) {
    val active = vm.activeFriends()
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (active.isNotEmpty()) item(key = "active-now") { ActiveNow(vm, active) }
        itemsIndexed(vm.friends, key = { _, f -> f.id }) { i, f ->
            PersonRow(f.avatar, f.name, i, status = vm.presence[f.id], trailing = {
                if (f.type == 3) Text("Pending", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }) { vm.showProfile(f.id) }
        }
    }
}

// ---------- you + settings ----------

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            ToggleButton(
                checked = i == selected, onCheckedChange = { onSelect(i) },
                modifier = Modifier.weight(1f), shapes = ToggleButtonDefaults.shapes()
            ) { Text(label) }
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().enter(1).animateContentSize()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), content = content)
    }
}

@Composable
fun YouTab(vm: MainViewModel) {
    val me = vm.me
    val mp = vm.myProfile
    val dynActive = vm.dynamic && Build.VERSION.SDK_INT >= 31
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Notifier.sync(ctx) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("You", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))

        // profile card
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().enter(0).animateContentSize()) {
            Column {
                Box(
                    Modifier.fillMaxWidth().height(100.dp).background(
                        mp?.accent?.let { Color(0xFF000000L or it.toLong()) } ?: MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    mp?.banner?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Row(Modifier.padding(horizontal = 16.dp).offset(y = (-32).dp), verticalAlignment = Alignment.Bottom) {
                    AvatarImage(me?.avatar, me?.name ?: "?", 72.dp, Modifier.border(4.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape), status = vm.status)
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp).offset(y = (-20).dp)) {
                    Text(me?.name ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("@" + (me?.username ?: ""), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!mp?.bio.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(mp!!.bio, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XTonalButton(onClick = { vm.startEdit() }) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Edit profile")
                        }
                        XOutlinedButton(onClick = { me?.let { vm.showProfile(it.id) } }) {
                            Icon(Icons.Filled.Person, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Preview")
                        }
                    }
                }
            }
        }

        // appearance
        SettingsCard {
            SettingRow(Icons.Filled.Palette, "Theme")
            Segmented(listOf("System", "Light", "Dark"), vm.themeMode) { vm.setTheme(it) }
            Spacer(Modifier.height(6.dp))
            if (Build.VERSION.SDK_INT >= 31) {
                SettingRow(Icons.Filled.Brush, "Dynamic color", "Match your wallpaper") {
                    Switch(vm.dynamic, { vm.setDynamic(it) })
                }
            }
            SettingRow(Icons.Filled.ColorLens, "Accent color", if (dynActive) "Turn off dynamic color to use these" else null)
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ACCENTS.forEachIndexed { i, (_, c) ->
                    val sel = vm.accent == i && !dynActive
                    val size by animateDpAsState(if (sel) 46.dp else 38.dp, bounce(0.5f, 400f), label = "accent")
                    Box(
                        Modifier.size(size).clip(CircleShape).background(Color(c).copy(alpha = if (dynActive) 0.35f else 1f))
                            .clickable(enabled = !dynActive) { vm.setAccent(i) },
                        contentAlignment = Alignment.Center
                    ) { if (sel) Icon(Icons.Filled.Check, null, tint = Color.White) }
                }
            }
        }

        // chat
        SettingsCard {
            SettingRow(Icons.Filled.Face, "Status", if (vm.gwConnected) "Live connection active" else "Connecting...")
            Segmented(listOf("Online", "Idle", "DND", "Hidden"), listOf("online", "idle", "dnd", "invisible").indexOf(vm.status).coerceAtLeast(0)) {
                vm.setStatus(listOf("online", "idle", "dnd", "invisible")[it])
            }
            Spacer(Modifier.height(6.dp))
            SettingRow(Icons.Filled.Info, "Server unread dots", "Small white dots for servers and channels with new messages") {
                Switch(UnreadStore.serverDots, { UnreadStore.setServerDots(it) })
            }
            SettingRow(Icons.Filled.Notifications, "Notifications", "DMs and mentions (keeps a quiet status notification)") {
                Switch(vm.notif, { on ->
                    vm.setNotif(on)
                    if (on && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            SettingRow(Icons.Filled.PlayCircle, "Autoplay GIF videos", "Tenor and Giphy style embeds") {
                Switch(vm.autoplay, { vm.setAutoplay(it) })
            }
            SettingRow(Icons.Filled.Chat, "Group messages within")
            Segmented(listOf("1 min", "5 min", "10 min"), listOf(1, 5, 10).indexOf(vm.groupMins).coerceAtLeast(0)) {
                vm.setGroupMins(listOf(1, 5, 10)[it])
            }
            Spacer(Modifier.height(10.dp))
        }

        XTonalButton(
            onClick = vm::logout, modifier = Modifier.fillMaxWidth().height(56.dp).enter(3),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(10.dp)); Text("Log out")
        }
        Spacer(Modifier.height(100.dp))
    }
}
