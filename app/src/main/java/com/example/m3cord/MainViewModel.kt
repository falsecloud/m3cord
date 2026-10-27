package com.example.m3cord

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

sealed interface Entry {
    data class Header(val id: String, val name: String) : Entry
    data class Item(val ch: Channel) : Entry
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("m3cord", 0)
    private var api: DiscordApi? = null

    var token by mutableStateOf(prefs.getString("token", null))
        private set
    var me by mutableStateOf<Me?>(null)
    var myProfile by mutableStateOf<Profile?>(null)
    var tab by mutableIntStateOf(0)

    var guilds by mutableStateOf<List<Guild>>(emptyList())
    var selected by mutableStateOf<String?>(null)
    var entries by mutableStateOf<List<Entry>>(emptyList())
    var dms by mutableStateOf<List<Channel>>(emptyList())

    var open by mutableStateOf<Channel?>(null)
    var messages by mutableStateOf<List<Msg>>(emptyList())
    var chatLoaded by mutableStateOf(false)
    var replyTo by mutableStateOf<Msg?>(null)
    var sending by mutableStateOf(false)
    var editMsg by mutableStateOf<Msg?>(null)
    var actionMsg by mutableStateOf<Msg?>(null)
    var loadingOlder by mutableStateOf(false)
    var reachedEnd by mutableStateOf(false)
    var friends by mutableStateOf<List<Friend>>(emptyList())
    var searchOpen by mutableStateOf(false)
    var searchResults by mutableStateOf<List<Msg>>(emptyList())
    var searchStatus by mutableStateOf<String?>(null)

    var profileId by mutableStateOf<String?>(null)
    var profile by mutableStateOf<Profile?>(null)
    var editing by mutableStateOf(false)
    var viewer by mutableStateOf<String?>(null)

    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // settings
    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0)); private set
    var dynamic by mutableStateOf(prefs.getBoolean("dynamic", true)); private set
    var accent by mutableIntStateOf(prefs.getInt("accent", 0)); private set
    var autoplay by mutableStateOf(prefs.getBoolean("autoplay", true)); private set
    var groupMins by mutableIntStateOf(prefs.getInt("group", 5)); private set
    fun setTheme(v: Int) { themeMode = v; prefs.edit().putInt("theme", v).apply() }
    @JvmName("applyDynamic") fun setDynamic(v: Boolean) { dynamic = v; prefs.edit().putBoolean("dynamic", v).apply() }
    @JvmName("applyAccent") fun setAccent(v: Int) { accent = v; prefs.edit().putInt("accent", v).apply() }
    @JvmName("applyAutoplay") fun setAutoplay(v: Boolean) { autoplay = v; prefs.edit().putBoolean("autoplay", v).apply() }
    @JvmName("applyGroupMins") fun setGroupMins(v: Int) { groupMins = v; prefs.edit().putInt("group", v).apply() }

    // ---- gateway / live state ----
    val presence = mutableStateMapOf<String, String>()
    val typing = mutableStateMapOf<String, Map<String, Pair<String, Long>>>()
    private var gwJob: Job? = null
    private var lastTyping = 0L
    var gwConnected by mutableStateOf(false)
    var status by mutableStateOf(prefs.getString("status", "online") ?: "online"); private set

    @JvmName("applyStatus") fun setStatus(s: String) {
        status = s
        prefs.edit().putString("status", s).apply()
        GatewayHub.setStatus(s)
    }

    private fun startGateway() {
        val t = token ?: return
        GatewayHub.start(t, status)
        gwConnected = GatewayHub.connected
        GatewayHub.presence.forEach { (k, v) -> presence[k] = v }
        if (gwJob == null) gwJob = viewModelScope.launch { GatewayHub.events.collect { handle(it) } }
    }

    private fun handle(e: Gw) {
        when (e) {
            is GwState -> {
                gwConnected = e.connected
                if (e.connected) { if (open != null) refresh(); loadDms() }
            }
            is GwPresence -> presence[e.userId] = e.status
            is GwCreated -> onCreated(e)
            is GwUpdated -> if (open?.id == e.channelId) {
                messages = messages.map { if (it.id == e.msg.id) e.msg.copy(reactions = it.reactions) else it }
            }
            is GwEmbeds -> if (open?.id == e.channelId) {
                messages = messages.map { if (it.id == e.msgId) it.copy(embeds = e.embeds) else it }
            }
            is GwDeleted -> if (open?.id == e.channelId) messages = messages.filter { it.id != e.id }
            is GwTyping -> onTyping(e)
            is GwReaction -> onReaction(e)
        }
    }

    private fun onCreated(e: GwCreated) {
        val m = e.msg
        typing[e.channelId]?.let { if (it.containsKey(m.authorId)) typing[e.channelId] = it - m.authorId }
        if (open?.id == e.channelId) {
            if (messages.none { it.id == m.id }) messages = listOf(m) + messages
        }
        if (e.guildId == null) {
            val d = dms.find { it.id == e.channelId }
            if (d != null) dms = listOf(d) + dms.filter { it.id != e.channelId } else loadDms()
        }
    }

    private fun onTyping(e: GwTyping) {
        if (e.userId == me?.id) return
        val name = e.name
            ?: messages.firstOrNull { it.authorId == e.userId }?.author
            ?: dms.find { it.id == e.channelId && it.type == 1 }?.name
            ?: "Someone"
        typing[e.channelId] = (typing[e.channelId] ?: emptyMap()) + (e.userId to (name to System.currentTimeMillis() + 9000L))
        viewModelScope.launch {
            delay(9500)
            val cur = typing[e.channelId] ?: return@launch
            val live = cur.filterValues { it.second > System.currentTimeMillis() }
            if (live.isEmpty()) typing.remove(e.channelId) else typing[e.channelId] = live
        }
    }

    private fun onReaction(e: GwReaction) {
        if (open?.id != e.channelId) return
        val mine = e.userId == me?.id
        messages = messages.map { x ->
            if (x.id != e.msgId) x else {
                val ex = x.reactions.find { it.key == e.key }
                x.copy(
                    reactions = when {
                        e.add && ex == null -> x.reactions + Reaction(e.key, e.label, e.url, 1, mine)
                        e.add && mine && ex!!.me -> x.reactions
                        e.add -> x.reactions.map { if (it.key == e.key) it.copy(count = it.count + 1, me = it.me || mine) else it }
                        ex == null -> x.reactions
                        mine && !ex.me -> x.reactions
                        ex.count <= 1 -> x.reactions.filter { it.key != e.key }
                        else -> x.reactions.map { if (it.key == e.key) it.copy(count = it.count - 1, me = if (mine) false else it.me) else it }
                    }
                )
            }
        }
    }

    fun typingPing() {
        val ch = open ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTyping < 8000) return
        lastTyping = now
        viewModelScope.launch { try { io { api!!.typing(ch.id) } } catch (e: Exception) { } }
    }

    var notif by mutableStateOf(prefs.getBoolean("notif", true)); private set
    @JvmName("applyNotif") fun setNotif(v: Boolean) {
        notif = v
        prefs.edit().putBoolean("notif", v).apply()
        Notifier.sync(getApplication<Application>())
    }

    fun openFromNotification(id: String, name: String, type: Int) {
        openChannel(Channel(id, name, type, null, 0))
    }

    fun sendVoice(v: Voice) {
        val ch = open ?: return
        val reply = replyTo
        replyTo = null
        sending = true
        viewModelScope.launch {
            try {
                val m = io {
                    val up = Upload("voice-message.ogg", "audio/ogg", v.file.readBytes(), v.secs, v.waveform)
                    api!!.send(ch.id, "", reply?.id, listOf(up))
                }
                messages = listOf(m) + messages.filter { it.id != m.id }
            } catch (e: Exception) { error = "Couldn't send voice message (${e.message})" }
            v.file.delete()
            sending = false
        }
    }

    init {
        UnreadStore.init(app)
        token?.let { api = DiscordApi(it); bootstrap() }
    }

    private suspend fun <T> io(f: () -> T): T = withContext(Dispatchers.IO) { f() }

    private fun fail(e: Exception) {
        if (e.message?.contains("401") == true) logout() else error = e.message ?: "Something broke"
    }

    private fun bootstrap() {
        startGateway()
        viewModelScope.launch {
            try {
                me = io { api!!.me() }
                guilds = io { api!!.guilds() }
                if (selected == null) selected = guilds.firstOrNull()?.id
                selected?.let { selectGuild(it) }
                loadDms()
                myProfile = try { io { api!!.profile(me!!.id) } } catch (e: Exception) { null }
            } catch (e: Exception) { fail(e) }
        }
    }

    fun login(raw: String) {
        val t = raw.trim().trim('"')
        if (t.isEmpty()) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val a = DiscordApi(t)
                io { a.me() }
                api = a
                prefs.edit().putString("token", t).apply()
                token = t
                Notifier.sync(getApplication<Application>())
                bootstrap()
            } catch (e: Exception) {
                error = "Login failed (${e.message})"
            }
            loading = false
        }
    }

    fun loadDms() {
        viewModelScope.launch {
            try { dms = io { api!!.dms() } } catch (e: Exception) { fail(e) }
        }
    }

    fun selectGuild(id: String) {
        selected = id
        entries = emptyList()
        loading = true
        viewModelScope.launch {
            try {
                val chs = io { api!!.channels(id) }
                if (selected == id) entries = build(chs)
            } catch (e: Exception) { fail(e) }
            if (selected == id) loading = false
        }
    }

    private fun build(chs: List<Channel>): List<Entry> {
        val text = chs.filter { it.type == 0 || it.type == 5 }
        val out = mutableListOf<Entry>()
        text.filter { it.parentId == null }.sortedBy { it.position }.forEach { out += Entry.Item(it) }
        chs.filter { it.type == 4 }.sortedBy { it.position }.forEach { cat ->
            val kids = text.filter { it.parentId == cat.id }.sortedBy { it.position }
            if (kids.isNotEmpty()) {
                out += Entry.Header(cat.id, cat.name)
                kids.forEach { out += Entry.Item(it) }
            }
        }
        return out
    }

    fun openChannel(ch: Channel) {
        Notifier.cancel(getApplication<Application>(), ch.id)
        UnreadStore.clear(ch.id)
        AppState.openChannel = ch.id
        open = ch
        messages = emptyList()
        chatLoaded = false
        replyTo = null
        editMsg = null
        actionMsg = null
        reachedEnd = false
        loadingOlder = false
        refresh()
    }

    fun closeChannel() {
        AppState.openChannel = null
        open = null
        messages = emptyList()
        replyTo = null
    }

    fun refresh() {
        val ch = open ?: return
        viewModelScope.launch {
            try {
                val m = io { api!!.messages(ch.id) }
                if (open?.id == ch.id) {
                    val oldest = m.lastOrNull()?.ts ?: 0L
                    val merged = m + messages.filter { it.ts < oldest }
                    if (merged != messages) messages = merged
                    chatLoaded = true
                }
            } catch (e: Exception) { fail(e) }
        }
    }

    fun nameOf(u: Uri): String = nameOf(getApplication<Application>().contentResolver, u)

    private fun nameOf(cr: ContentResolver, u: Uri): String =
        cr.query(u, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: "file"

    fun send(text: String, uris: List<Uri>) {
        val ch = open ?: return
        if (text.isBlank() && uris.isEmpty()) return
        val reply = replyTo
        replyTo = null
        sending = true
        viewModelScope.launch {
            try {
                val m = io {
                    val cr = getApplication<Application>().contentResolver
                    val files = uris.map { u ->
                        Upload(
                            nameOf(cr, u), cr.getType(u) ?: "application/octet-stream",
                            cr.openInputStream(u)!!.use { it.readBytes() }
                        )
                    }
                    api!!.send(ch.id, text, reply?.id, files)
                }
                messages = listOf(m) + messages.filter { it.id != m.id }
            } catch (e: Exception) { error = "Couldn't send (${e.message})" }
            sending = false
        }
    }

    fun loadOlder() {
        val ch = open ?: return
        val last = messages.lastOrNull() ?: return
        if (loadingOlder || reachedEnd) return
        loadingOlder = true
        viewModelScope.launch {
            try {
                val m = io { api!!.messages(ch.id, last.id) }
                if (open?.id == ch.id) {
                    if (m.isEmpty()) reachedEnd = true
                    else messages = messages + m.filter { n -> messages.none { it.id == n.id } }
                }
            } catch (e: Exception) { fail(e) }
            loadingOlder = false
        }
    }

    fun toggleReaction(m: Msg, key: String, label: String, url: String?) {
        val ch = open ?: return
        val existing = m.reactions.find { it.key == key }
        val add = existing?.me != true
        messages = messages.map { x ->
            if (x.id != m.id) x else x.copy(
                reactions = when {
                    existing == null -> x.reactions + Reaction(key, label, url, 1, true)
                    add -> x.reactions.map { if (it.key == key) it.copy(count = it.count + 1, me = true) else it }
                    existing.count <= 1 -> x.reactions.filter { it.key != key }
                    else -> x.reactions.map { if (it.key == key) it.copy(count = it.count - 1, me = false) else it }
                }
            )
        }
        viewModelScope.launch {
            try { io { api!!.react(ch.id, m.id, key, add) } } catch (e: Exception) { error = "Reaction failed"; refresh() }
        }
    }

    fun deleteMessage(m: Msg) {
        val ch = open ?: return
        messages = messages.filter { it.id != m.id }
        viewModelScope.launch {
            try { io { api!!.delete(ch.id, m.id) } } catch (e: Exception) { error = "Couldn't delete message"; refresh() }
        }
    }

    fun saveEdit(text: String) {
        val ch = open ?: return
        val em = editMsg ?: return
        editMsg = null
        if (text.isBlank() || text == em.content) return
        viewModelScope.launch {
            try {
                val n = io { api!!.edit(ch.id, em.id, text) }
                messages = messages.map { if (it.id == n.id) n else it }
            } catch (e: Exception) { error = "Couldn't edit message" }
        }
    }

    fun loadFriends() {
        viewModelScope.launch {
            try { friends = io { api!!.friends() } } catch (e: Exception) { fail(e) }
        }
    }

    fun search(q: String) {
        val ch = open ?: return
        if (q.isBlank()) return
        searchStatus = "Searching..."
        viewModelScope.launch {
            try {
                val gid = if (ch.type == 0 || ch.type == 5) selected else null
                val r = io { api!!.search(ch.id, gid, q) }
                if (r == null) searchStatus = "Discord is still indexing this chat, try again in a few seconds"
                else { searchResults = r; searchStatus = if (r.isEmpty()) "No results" else null }
            } catch (e: Exception) { searchStatus = "Search failed (${e.message})" }
        }
    }

    fun showProfile(id: String) {
        profileId = id
        profile = null
        viewModelScope.launch {
            try {
                val p = io { api!!.profile(id) }
                if (profileId == id) profile = p
            } catch (e: Exception) {
                profileId = null
                fail(e)
            }
        }
    }

    fun dismissProfile() {
        profileId = null
        profile = null
    }

    fun openDm(userId: String) {
        viewModelScope.launch {
            try {
                val ch = io { api!!.openDm(userId) }
                dismissProfile()
                openChannel(ch)
            } catch (e: Exception) { fail(e) }
        }
    }

    fun startEdit() {
        editing = true
        viewModelScope.launch {
            try { myProfile = io { api!!.profile(me!!.id) } } catch (e: Exception) { /* keep old */ }
        }
    }

    private fun encodeAvatar(uri: Uri): String {
        val cr = getApplication<Application>().contentResolver
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw RuntimeException("bad image")
        val s = minOf(bmp.width, bmp.height)
        val sq = Bitmap.createBitmap(bmp, (bmp.width - s) / 2, (bmp.height - s) / 2, s, s)
        val target = minOf(s, 512)
        val out = Bitmap.createScaledBitmap(sq, target, target, true)
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 90, bos)
        return "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    fun saveProfile(name: String, bio: String, pronouns: String, avatar: Uri?) {
        sending = true
        viewModelScope.launch {
            try {
                io {
                    val a = api!!
                    val j = JSONObject().put("global_name", if (name.isBlank()) JSONObject.NULL else name.trim())
                    if (avatar != null) j.put("avatar", encodeAvatar(avatar))
                    a.patchMe(j)
                    a.patchProfile(JSONObject().put("bio", bio).put("pronouns", pronouns))
                }
                me = io { api!!.me() }
                myProfile = io { api!!.profile(me!!.id) }
                editing = false
            } catch (e: Exception) { error = "Couldn't save profile (${e.message})" }
            sending = false
        }
    }

    fun logout() {
        prefs.edit().remove("token").apply()
        token = null
        Notifier.sync(getApplication<Application>())
        GatewayHub.stop()
        gwJob?.cancel()
        gwJob = null
        gwConnected = false
        UnreadStore.reset(); presence.clear(); typing.clear()
        api = null
        me = null
        myProfile = null
        open = null
        selected = null
        tab = 0
        guilds = emptyList()
        entries = emptyList()
        dms = emptyList()
        friends = emptyList()
        messages = emptyList()
        loading = false
        profileId = null
        editing = false
    }
}
