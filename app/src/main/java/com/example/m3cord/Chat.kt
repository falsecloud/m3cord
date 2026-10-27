@file:OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class, ExperimentalLayoutApi::class
)

package com.example.m3cord

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- helpers ----------

private fun dayKey(ts: Long) = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

private fun dayLabel(ts: Long): String {
    val d = dayKey(ts)
    val today = java.time.LocalDate.now()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}

@Composable
fun DateChip(ts: Long) {
    Box(Modifier.enter(0).fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                dayLabel(ts), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
    }
}

// ---------- screen ----------

@Composable
fun ChatScreen(vm: MainViewModel, ch: Channel) {
    val ls = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(ch.id) {
        while (true) {
            delay(5000)
            if (!vm.gwConnected) vm.refresh()
        }
    }
    LaunchedEffect(vm.messages.firstOrNull()?.id) {
        if (ls.firstVisibleItemIndex <= 2) ls.animateScrollToItem(0)
    }
    LaunchedEffect(ch.id) {
        snapshotFlow { ls.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (vm.messages.isNotEmpty() && last >= vm.messages.size - 6) vm.loadOlder()
        }
    }
    val showFab by remember { derivedStateOf { ls.firstVisibleItemIndex > 3 } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ch.type == 0 || ch.type == 5) {
                            Icon(Icons.Filled.Tag, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        } else {
                            AvatarImage(ch.icon, ch.name, 34.dp, Modifier.clickable { ch.recipientId?.let { vm.showProfile(it) } }, status = ch.recipientId?.let { vm.presence[it] })
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(ch.name, fontWeight = FontWeight.Bold, maxLines = 1)
                            val topic = ch.topic
                            if (!topic.isNullOrBlank()) {
                                Text(topic.replace("\n", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                },
                navigationIcon = { XIconButton(onClick = vm::closeChannel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { XIconButton(onClick = { vm.searchOpen = true }) { Icon(Icons.Filled.Search, "Search") } }
            )
        },
        bottomBar = { InputBar(vm, ch) }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val msgs = vm.messages
            val jump: (String) -> Unit = { id ->
                val idx = msgs.indexOfFirst { it.id == id }
                if (idx >= 0) scope.launch { ls.animateScrollToItem(idx) } else vm.error = "That message isn't loaded yet"
            }
            LazyColumn(state = ls, reverseLayout = true, contentPadding = PaddingValues(12.dp), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(msgs, key = { _, m -> m.id }) { i, m ->
                    val older = msgs.getOrNull(i + 1)
                    val newDay = older == null || dayKey(older.ts) != dayKey(m.ts)
                    val head = older == null || newDay || older.authorId != m.authorId ||
                        m.ts - older.ts > vm.groupMins * 60_000L || m.reply != null
                    Column(Modifier.animateItem()) {
                        if (newDay) DateChip(m.ts)
                        MessageItem(m, head, vm, jump)
                    }
                }
                if (vm.loadingOlder) {
                    item(key = "older") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(Modifier.size(40.dp))
                        }
                    }
                }
            }
            if (!vm.chatLoaded) LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp))
            AnimatedVisibility(
                visible = showFab,
                enter = scaleIn(bounce(0.5f, 400f)) + fadeIn(), exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                SmallFloatingActionButton(onClick = { scope.launch { ls.animateScrollToItem(0) } }) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Latest")
                }
            }
        }
    }
}

// ---------- input ----------

@Composable
fun InputBar(vm: MainViewModel, ch: Channel) {
    var text by remember { mutableStateOf("") }
    var picks by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var panel by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = panel) { panel = false }
    val mentionMap = remember { mutableStateMapOf<String, String>() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        picks = (picks + it).distinct().take(10)
    }
    val tailRegex = remember { Regex("@[^\\s@]*$") }
    val ctx = LocalContext.current
    val recorder = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var recMs by remember { mutableLongStateOf(0L) }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try { recorder.start(); recording = true } catch (e: Exception) { vm.error = "Couldn't start recording" }
        }
    }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    LaunchedEffect(recording) {
        while (recording) {
            recorder.sample()
            recMs = recorder.elapsed()
            delay(100)
        }
    }
    val startRec: () -> Unit = {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try { recorder.start(); recording = true } catch (e: Exception) { vm.error = "Couldn't start recording" }
        } else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }
    val query = tailRegex.find(text)?.value?.drop(1)
    val people = remember(vm.messages, query) {
        if (query == null) emptyList()
        else vm.messages.distinctBy { it.authorId }.filter { it.author.contains(query, true) }.take(8)
    }
    var lastReply by remember { mutableStateOf<Msg?>(null) }
    vm.replyTo?.let { lastReply = it }
    val em = vm.editMsg
    val editing = em != null
    LaunchedEffect(em?.id) { if (em != null) text = em.content }
    val enterX = expandVertically() + fadeIn()
    val exitX = shrinkVertically() + fadeOut()

    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        val typers = vm.typing[ch.id]?.values?.map { it.first } ?: emptyList()
        var lastTypers by remember { mutableStateOf<List<String>>(emptyList()) }
        if (typers.isNotEmpty()) lastTypers = typers
        AnimatedVisibility(typers.isNotEmpty(), enter = enterX, exit = exitX) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when (lastTypers.size) {
                        0 -> ""
                        1 -> lastTypers[0] + " is typing"
                        2 -> lastTypers[0] + " and " + lastTypers[1] + " are typing"
                        else -> "Several people are typing"
                    },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AnimatedVisibility(people.isNotEmpty(), enter = enterX, exit = exitX) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(people, key = { it.authorId }) { p ->
                    AssistChip(
                        onClick = {
                            text = tailRegex.replace(text) { "@${p.author} " }
                            mentionMap[p.author] = p.authorId
                        },
                        label = { Text(p.author) },
                        leadingIcon = { AvatarImage(p.avatarUrl, p.author, 24.dp) }
                    )
                }
            }
        }
        AnimatedVisibility(vm.replyTo != null, enter = enterX, exit = exitX) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
            ) {
                Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Replying to ${lastReply?.author ?: ""}", maxLines = 1, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    XIconButton(onClick = { vm.replyTo = null }) { Icon(Icons.Filled.Close, "Cancel reply") }
                }
            }
        }
        AnimatedVisibility(editing, enter = enterX, exit = exitX) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
            ) {
                Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Editing message", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    XIconButton(onClick = { vm.editMsg = null; text = "" }) { Icon(Icons.Filled.Close, "Cancel edit") }
                }
            }
        }
        AnimatedVisibility(picks.isNotEmpty(), enter = enterX, exit = exitX) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(picks, key = { it.toString() }) { u ->
                    val name = remember(u) { vm.nameOf(u) }
                    InputChip(
                        selected = false, onClick = { picks = picks - u },
                        label = { Text(name, maxLines = 1) },
                        leadingIcon = { Icon(Icons.Filled.AttachFile, null, Modifier.size(18.dp)) },
                        trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(18.dp)) }
                    )
                }
            }
        }
        AnimatedVisibility(vm.sending, enter = enterX, exit = exitX) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        }
        if (recording) {
            RecordingBar(
                ms = recMs,
                onCancel = { recorder.cancel(); recording = false },
                onSend = {
                    val v = recorder.stop()
                    recording = false
                    if (v != null) vm.sendVoice(v) else vm.error = "Recording too short"
                }
            )
        } else Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            XIconButton(onClick = { picker.launch("*/*") }) { Icon(Icons.Filled.AttachFile, "Attach file") }
            TextField(
                value = text, onValueChange = { text = it; if (it.isNotBlank()) vm.typingPing() },
                placeholder = { Text(if (ch.type == 0 || ch.type == 5) "Message #${ch.name}" else "Message ${ch.name}", maxLines = 1) },
                shape = RoundedCornerShape(28.dp), maxLines = 4,
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.weight(1f).animateContentSize().focusRequester(fieldFocus)
                    .onFocusChanged { if (it.isFocused) panel = false }
            )
            XIconButton(onClick = {
                if (panel) { panel = false; fieldFocus.requestFocus() }
                else { focusManager.clearFocus(); keyboard?.hide(); panel = true }
            }) {
                AnimatedContent(
                    targetState = panel, label = "emojiicon",
                    transitionSpec = { (scaleIn(bounce(0.45f, 500f)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) }
                ) { isOpen ->
                    if (isOpen) Icon(Icons.Filled.Keyboard, "Back to keyboard") else Icon(Icons.Filled.EmojiEmotions, "Emojis and GIFs")
                }
            }
            AnimatedVisibility(
                visible = text.isNotBlank() || picks.isNotEmpty(),
                enter = scaleIn(bounce(0.5f, 400f)) + fadeIn() + expandHorizontally(),
                exit = scaleOut() + fadeOut() + shrinkHorizontally()
            ) {
                XFilledIconButton(
                    onClick = {
                        var out = text.trim()
                        mentionMap.entries.sortedByDescending { it.key.length }.forEach { (n, id) -> out = out.replace("@$n", "<@$id>") }
                        if (editing) vm.saveEdit(out) else vm.send(out, picks)
                        text = ""
                        picks = emptyList()
                        mentionMap.clear()
                    },
                    enabled = !vm.sending, modifier = Modifier.padding(start = 8.dp).size(52.dp)
                ) {
                    AnimatedContent(
                        targetState = editing, label = "sendicon",
                        transitionSpec = { (scaleIn(bounce(0.5f, 500f)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) }
                    ) { e ->
                        if (e) Icon(Icons.Filled.Check, "Save edit") else Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            }
            AnimatedVisibility(
                visible = text.isBlank() && picks.isEmpty() && !editing && Build.VERSION.SDK_INT >= 29,
                enter = scaleIn(bounce(0.5f, 400f)) + fadeIn() + expandHorizontally(),
                exit = scaleOut() + fadeOut() + shrinkHorizontally()
            ) {
                XFilledIconButton(onClick = startRec, modifier = Modifier.padding(start = 8.dp).size(52.dp)) {
                    Icon(Icons.Filled.Mic, "Record voice message")
                }
            }
        }
        AnimatedVisibility(
            visible = panel && !recording,
            enter = expandVertically(spring(0.75f, 380f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            EmojiPanel(
                vm, onEmoji = { text += it },
                onGif = { g -> vm.sendGif(g); panel = false }
            )
        }
    }
}

// ---------- swipe to reply ----------

@Composable
fun SwipeToReply(onReply: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val off = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val trigger = with(LocalDensity.current) { 72.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    var fired by remember { mutableStateOf(false) }
    Box(
        modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { fired = false },
                onDragEnd = { scope.launch { off.animateTo(0f, bounce(0.55f, 500f)) } },
                onDragCancel = { scope.launch { off.animateTo(0f, bounce(0.55f, 500f)) } }
            ) { change, dx ->
                change.consume()
                val next = (off.value + dx * 0.6f).coerceIn(0f, trigger * 1.4f)
                scope.launch { off.snapTo(next) }
                if (next >= trigger && !fired) {
                    fired = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onReply()
                }
            }
        }
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(26.dp).graphicsLayer {
                val p = (off.value / trigger).coerceIn(0f, 1f)
                alpha = p
                scaleX = 0.5f + 0.5f * p
                scaleY = 0.5f + 0.5f * p
                rotationZ = (1f - p) * -40f
            }
        )
        Box(Modifier.graphicsLayer { translationX = off.value }) { content() }
    }
}

// ---------- message ----------

@Composable
fun MessageItem(m: Msg, head: Boolean, vm: MainViewModel, onJump: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    SwipeToReply(onReply = { vm.replyTo = m }, modifier = Modifier.padding(top = if (head) 14.dp else 2.dp)) {
        Row(
            Modifier.fillMaxWidth().pressScale(src, 0.97f).combinedClickable(
                interactionSource = src, indication = null, onClick = {},
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.actionMsg = m }
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (head) AvatarImage(m.avatarUrl, m.author, 40.dp, Modifier.clickable { vm.showProfile(m.authorId) }, status = vm.presence[m.authorId])
            else Spacer(Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                if (head) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 4.dp)) {
                        Text(
                            m.author, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { vm.showProfile(m.authorId) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(m.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                MessageBody(m, head, vm, onJump)
            }
        }
    }
}

// markdown + mentions + custom emoji
private val TOKEN = Regex(
    "<@!?(\\d+)>|<@&(\\d+)>|<#(\\d+)>|<(a?):(\\w+):(\\d+)>|@everyone|@here|https?://[^\\s<>]+|" +
        "```([\\s\\S]+?)```|`([^`\\n]+)`|\\*\\*([\\s\\S]+?)\\*\\*|__([\\s\\S]+?)__|~~([\\s\\S]+?)~~|\\*([^*\\n]+)\\*"
)

private class Ctx(
    val m: Msg, val link: Color, val mFg: Color, val mBg: Color, val codeBg: Color, val muted: Color,
    val emojis: MutableSet<String>, val onProfile: (String) -> Unit
)

private fun AnnotatedString.Builder.render(s: String, c: Ctx, depth: Int) {
    var last = 0
    val mention = SpanStyle(color = c.mFg, background = c.mBg, fontWeight = FontWeight.Medium)
    val code = SpanStyle(fontFamily = FontFamily.Monospace, background = c.codeBg)
    for (r in TOKEN.findAll(s)) {
        append(s.substring(last, r.range.first))
        val g = r.groupValues
        when {
            g[1].isNotEmpty() -> {
                val id = g[1]
                withLink(LinkAnnotation.Clickable("u$id", TextLinkStyles(mention)) { c.onProfile(id) }) {
                    append("@" + (c.m.mentions[id] ?: "user"))
                }
            }
            g[2].isNotEmpty() -> withStyle(mention) { append("@role") }
            g[3].isNotEmpty() -> withStyle(mention) { append("#channel") }
            g[6].isNotEmpty() -> { c.emojis.add(g[6]); appendInlineContent("e" + g[6], ":" + g[5] + ":") }
            g[7].isNotEmpty() -> withStyle(code) { append(g[7].trim('\n')) }
            g[8].isNotEmpty() -> withStyle(code) { append(g[8]) }
            g[9].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { if (depth < 3) render(g[9], c, depth + 1) else append(g[9]) }
            g[10].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { if (depth < 3) render(g[10], c, depth + 1) else append(g[10]) }
            g[11].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { if (depth < 3) render(g[11], c, depth + 1) else append(g[11]) }
            g[12].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { if (depth < 3) render(g[12], c, depth + 1) else append(g[12]) }
            r.value.startsWith("http") ->
                withLink(LinkAnnotation.Url(r.value, TextLinkStyles(SpanStyle(color = c.link, textDecoration = TextDecoration.Underline)))) {
                    append(r.value)
                }
            else -> withStyle(mention) { append(r.value) }
        }
        last = r.range.last + 1
    }
    append(s.substring(last))
}

fun buildContent(
    m: Msg, link: Color, mFg: Color, mBg: Color, codeBg: Color, muted: Color,
    emojis: MutableSet<String>, onProfile: (String) -> Unit
): AnnotatedString {
    val c = Ctx(m, link, mFg, mBg, codeBg, muted, emojis, onProfile)
    return buildAnnotatedString {
        render(m.content, c, 0)
        if (m.edited) withStyle(SpanStyle(fontSize = 10.sp, color = muted)) { append("  (edited)") }
    }
}

@Composable
fun MessageBody(m: Msg, head: Boolean, vm: MainViewModel, onJump: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        m.reply?.let { r ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(8.dp)).clickable { if (r.id.isNotEmpty()) onJump(r.id) }
            ) {
                Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    (if (r.author.isNotEmpty()) r.author + ": " else "") + r.snippet,
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1
                )
            }
        }
        if (m.content.isNotBlank()) {
            val emojis = remember(m.id, m.content) { mutableSetOf<String>() }
            val ann = remember(m.id, m.content, m.edited, cs.primary) {
                emojis.clear()
                buildContent(m, cs.primary, cs.onPrimaryContainer, cs.primaryContainer, cs.surfaceContainerHighest, cs.onSurfaceVariant, emojis) { vm.showProfile(it) }
            }
            val inline = remember(ann) {
                emojis.associate { id ->
                    "e$id" to InlineTextContent(Placeholder(22.sp, 22.sp, PlaceholderVerticalAlign.TextCenter)) {
                        AsyncImage("https://cdn.discordapp.com/emojis/$id.png?size=48", null, Modifier.fillMaxSize())
                    }
                }
            }
            Surface(
                shape = if (head) RoundedCornerShape(4.dp, 22.dp, 22.dp, 22.dp) else RoundedCornerShape(22.dp),
                color = if (m.mentionsMe) cs.tertiaryContainer else cs.surfaceContainerHigh,
                modifier = Modifier.animateContentSize(bounce(0.8f, 400f))
            ) {
                Text(
                    ann, inlineContent = inline, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        m.stickers.forEach { s ->
            if (s.format == 3) Text(":" + s.name + ":", style = MaterialTheme.typography.bodyMedium)
            else AsyncImage(
                "https://media.discordapp.net/stickers/${s.id}.${if (s.format == 4) "gif" else "png"}?size=160", s.name,
                Modifier.size(128.dp)
            )
        }
        m.atts.forEach { AttachmentView(it, vm) }
        m.embeds.forEach { EmbedView(it, vm) }
        if (m.reactions.isNotEmpty()) ReactionChips(m, vm)
    }
}

@Composable
fun ReactionChips(m: Msg, vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        m.reactions.forEach { r ->
            val src = remember(r.key) { MutableInteractionSource() }
            val bg by animateColorAsState(if (r.me) cs.primaryContainer else cs.surfaceContainerHigh, bounce(1f, 400f), label = "rbg")
            Surface(
                onClick = { vm.toggleReaction(m, r.key, r.label, r.url) }, interactionSource = src,
                shape = RoundedCornerShape(50), color = bg, modifier = Modifier.pop().pressScale(src, 0.82f)
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (r.url != null) AsyncImage(r.url, null, Modifier.size(18.dp)) else Text(r.label, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    AnimatedContent(
                        targetState = r.count, label = "count",
                        transitionSpec = { (slideInVertically(bounce(0.6f, 500f)) { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }
                    ) { c -> Text(c.toString(), style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

// ---------- action sheet ----------

@Composable
private fun ActionRow(icon: ImageVector, label: String, i: Int, danger: Boolean = false, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, interactionSource = src, shape = RoundedCornerShape(20.dp), color = Color.Transparent,
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().enter(i).pressScale(src, 0.96f)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (danger) cs.error else cs.primary)
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (danger) cs.error else cs.onSurface)
        }
    }
}

@Composable
fun MessageActionSheet(m: Msg, vm: MainViewModel) {
    val clip = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    val mine = m.authorId == vm.me?.id
    val close = { vm.actionMsg = null }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDD25").forEachIndexed { i, e ->
                    val src = remember { MutableInteractionSource() }
                    Box(
                        Modifier.enter(i).pressScale(src, 0.75f).size(50.dp).clip(CircleShape).background(cs.surfaceContainerHigh)
                            .clickable(interactionSource = src, indication = null) { vm.toggleReaction(m, e, e, null); close() },
                        contentAlignment = Alignment.Center
                    ) { Text(e, fontSize = 24.sp) }
                }
            }
            ActionRow(Icons.AutoMirrored.Filled.Reply, "Reply", 1) { vm.replyTo = m; close() }
            ActionRow(Icons.Filled.ContentCopy, "Copy text", 2) { clip.setText(AnnotatedString(m.content)); close() }
            if (mine) {
                ActionRow(Icons.Filled.Edit, "Edit message", 3) { vm.editMsg = m; vm.replyTo = null; close() }
                ActionRow(Icons.Filled.Delete, "Delete message", 4, danger = true) { vm.deleteMessage(m); close() }
            }
            val gif = remember(m.id) { gifOf(m) }
            if (gif != null) {
                LaunchedEffect(Unit) { vm.loadFavGifs() }
                val fav = vm.isFav(gif.url)
                ActionRow(
                    if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (fav) "Remove GIF from favorites" else "Favorite this GIF", 5
                ) { vm.toggleFavGif(gif); close() }
            }
            ActionRow(Icons.Filled.Person, "View profile", 6) { close(); vm.showProfile(m.authorId) }
        }
    }
}

// ---------- media ----------

@Composable
fun MediaImage(url: String, w: Int, h: Int, onClick: () -> Unit) {
    val ratio = if (w > 0 && h > 0) w.toFloat() / h else 1.5f
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.widthIn(max = 300.dp).aspectRatio(ratio.coerceIn(0.7f, 2.2f)).pressScale(src, 0.96f).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
    ) {
        AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun VideoPlayer(url: String, thumb: String?, ratio: Float, autoplay: Boolean, loop: Boolean, muted: Boolean) {
    var playing by remember(url) { mutableStateOf(autoplay) }
    Box(
        Modifier.widthIn(max = 300.dp).aspectRatio(ratio.coerceIn(0.7f, 2.2f)).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (playing) {
            val ctx = LocalContext.current
            val player = remember(url) {
                ExoPlayer.Builder(ctx).build().apply {
                    setMediaItem(MediaItem.fromUri(url))
                    repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    volume = if (muted) 0f else 1f
                    playWhenReady = true
                    prepare()
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(
                factory = { c -> PlayerView(c).apply { this.player = player; useController = !loop } },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            if (thumb != null) AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            XFilledIconButton(onClick = { playing = true }, modifier = Modifier.align(Alignment.Center).size(56.dp)) {
                Icon(Icons.Filled.PlayArrow, "Play", Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun AudioCard(a: Att) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var playing by remember(a.url) { mutableStateOf(false) }
    var pos by remember(a.url) { mutableLongStateOf(0L) }
    var dur by remember(a.url) { mutableLongStateOf((a.secs * 1000).toLong()) }
    val player = remember(a.url) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(a.url))
            prepare()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && duration > 0) dur = duration
                    if (state == Player.STATE_ENDED) { playing = false; seekTo(0); pause(); pos = 0L }
                }
            })
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(playing) {
        while (playing) {
            pos = player.currentPosition
            delay(80)
        }
    }
    val frac by animateFloatAsState(if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f, bounce(1f, 800f), label = "audiofrac")
    val bars = remember(a.waveform) {
        a.waveform?.let { w ->
            try { Base64.decode(w, Base64.DEFAULT).map { (it.toInt() and 0xFF) / 255f } } catch (e: Exception) { null }
        }
    }
    val active = cs.primary
    val inactive = cs.outlineVariant
    Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh, modifier = Modifier.animateContentSize()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            XFilledIconButton(onClick = { playing = !playing; player.playWhenReady = playing }, modifier = Modifier.size(48.dp)) {
                AnimatedContent(
                    targetState = playing, label = "playpause",
                    transitionSpec = { (scaleIn(bounce(0.5f, 500f)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) }
                ) { p -> if (p) Icon(Icons.Filled.Pause, "Pause") else Icon(Icons.Filled.PlayArrow, "Play") }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.width(190.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(36.dp).pointerInput(dur) {
                        detectTapGestures { o ->
                            if (dur > 0) {
                                val f = (o.x / size.width).coerceIn(0f, 1f)
                                val target = (f * dur).toLong()
                                player.seekTo(target)
                                pos = target
                            }
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    if (bars != null && bars.isNotEmpty()) {
                        Canvas(Modifier.fillMaxSize()) {
                            val n = bars.size
                            val w = size.width / n
                            bars.forEachIndexed { i, h ->
                                val bh = maxOf(4f, h * size.height)
                                drawRoundRect(
                                    color = if (i.toFloat() / n <= frac) active else inactive,
                                    topLeft = Offset(i * w + w * 0.15f, (size.height - bh) / 2f),
                                    size = Size(w * 0.7f, bh),
                                    cornerRadius = CornerRadius(w * 0.35f)
                                )
                            }
                        }
                    } else {
                        LinearWavyProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                    }
                }
                Text(
                    fmtMs(pos) + " / " + fmtMs(dur) + "   " + (if (a.waveform != null) "Voice message" else a.name),
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1
                )
            }
        }
    }
}

private fun fmtSize(b: Long): String = when {
    b >= 1_000_000 -> "%.1f MB".format(b / 1_000_000.0)
    b >= 1_000 -> "%.0f KB".format(b / 1_000.0)
    else -> "$b B"
}

@Composable
fun AttachmentView(a: Att, vm: MainViewModel) {
    val uri = LocalUriHandler.current
    val src = remember { MutableInteractionSource() }
    when {
        a.mime.startsWith("image/") -> MediaImage(a.url, a.w, a.h) { vm.viewer = a.url }
        a.mime.startsWith("video/") ->
            VideoPlayer(a.url, null, if (a.w > 0 && a.h > 0) a.w.toFloat() / a.h else 1.78f, false, false, false)
        a.mime.startsWith("audio/") -> AudioCard(a)
        else -> Surface(
            onClick = { uri.openUri(a.url) }, interactionSource = src, shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.pressScale(src, 0.95f)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.widthIn(max = 220.dp)) {
                    Text(a.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(fmtSize(a.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun EmbedView(e: Emb, vm: MainViewModel) {
    val uri = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    val ratio = if (e.w > 0 && e.h > 0) e.w.toFloat() / e.h else 1.78f
    when {
        e.type == "image" && (e.thumb ?: e.url) != null ->
            MediaImage((e.thumb ?: e.url)!!, e.w, e.h) { vm.viewer = (e.thumb ?: e.url)!! }
        e.type == "gifv" && e.video != null ->
            VideoPlayer(e.video, e.thumb, ratio, vm.autoplay, true, true)
        e.type == "video" && e.url != null -> {
            val src = remember { MutableInteractionSource() }
            Box(
                Modifier.widthIn(max = 300.dp).aspectRatio(ratio.coerceIn(0.7f, 2.2f)).pressScale(src, 0.96f).clip(RoundedCornerShape(16.dp))
                    .background(cs.surfaceContainerHighest).clickable(interactionSource = src, indication = null) { uri.openUri(e.url) }
            ) {
                e.thumb?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Icon(
                    Icons.Filled.PlayArrow, "Open video", tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(56.dp).clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(10.dp)
                )
            }
        }
        else -> {
            val bar = e.color?.let { Color(0xFF000000L or it.toLong()) } ?: cs.primary
            Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer, modifier = Modifier.widthIn(max = 320.dp).animateContentSize()) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(bar))
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        e.provider?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
                        e.author?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                        e.title?.let {
                            Text(
                                it, style = MaterialTheme.typography.titleSmall, color = cs.primary,
                                modifier = if (e.url != null) Modifier.clickable { uri.openUri(e.url) } else Modifier
                            )
                        }
                        e.desc?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 6) }
                        (e.image ?: e.thumb)?.let { img -> MediaImage(img, e.w, e.h) { vm.viewer = img } }
                        e.footer?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

private fun fmtMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun RecordingBar(ms: Long, onCancel: () -> Unit, onSend: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot")
    Row(Modifier.fillMaxWidth().padding(8.dp).enter(0), verticalAlignment = Alignment.CenterVertically) {
        XIconButton(onClick = onCancel) { Icon(Icons.Filled.Delete, "Cancel recording", tint = cs.error) }
        Surface(shape = RoundedCornerShape(28.dp), color = cs.errorContainer, modifier = Modifier.weight(1f).height(52.dp)) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).graphicsLayer { alpha = a }.background(cs.error, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(fmtMs(ms), style = MaterialTheme.typography.titleMedium, color = cs.onErrorContainer)
                Spacer(Modifier.weight(1f))
                Text("Recording", style = MaterialTheme.typography.labelMedium, color = cs.onErrorContainer)
            }
        }
        XFilledIconButton(onClick = onSend, modifier = Modifier.padding(start = 8.dp).size(52.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send voice message")
        }
    }
}
