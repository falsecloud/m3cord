package com.example.m3cord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed interface Gw
data class GwState(val connected: Boolean) : Gw
data class GwCreated(val msg: Msg, val channelId: String, val guildId: String?) : Gw
data class GwUpdated(val msg: Msg, val channelId: String) : Gw
data class GwEmbeds(val channelId: String, val msgId: String, val embeds: List<Emb>) : Gw
data class GwDeleted(val channelId: String, val id: String) : Gw
data class GwTyping(val channelId: String, val userId: String, val name: String?) : Gw
data class GwPresence(val userId: String, val status: String, val activities: List<Activity> = emptyList()) : Gw
data class GwReaction(
    val channelId: String, val msgId: String, val userId: String, val key: String,
    val label: String, val url: String?, val add: Boolean
) : Gw

/** One shared Discord gateway connection, used by the UI and by the notification service. */
object GatewayHub {
    private const val UA2 =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val _events = MutableSharedFlow<Gw>(extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Gw> = _events.asSharedFlow()
    val presence = ConcurrentHashMap<String, String>()
    val activities = ConcurrentHashMap<String, List<Activity>>()
    private val channels = ConcurrentHashMap<String, Channel>()

    @Volatile var connected = false
    @Volatile var myId = ""
    @Volatile private var status = "online"
    @Volatile private var seq: Int? = null
    @Volatile private var gen = 0
    private var token: String? = null
    private var api: DiscordApi? = null
    private var ws: WebSocket? = null
    private var sessionId: String? = null
    private var resumeUrl: String? = null
    private var attempt = 0
    private var hb: Job? = null
    private var retry: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun start(tok: String, st: String) {
        status = st
        if (token == tok && ws != null) return
        stopInternal()
        token = tok
        api = DiscordApi(tok)
        sessionId = null
        seq = null
        attempt = 0
        connect()
    }

    @Synchronized
    fun stop() {
        stopInternal()
        token = null
        api = null
        presence.clear()
        activities.clear()
        channels.clear()
    }

    private fun stopInternal() {
        gen++
        retry?.cancel()
        hb?.cancel()
        ws?.close(1000, "bye")
        ws = null
        connected = false
    }

    fun setStatus(s: String) {
        status = s
        ws?.send(
            JSONObject().put("op", 3).put(
                "d", JSONObject().put("status", s).put("since", 0).put("activities", JSONArray()).put("afk", false)
            ).toString()
        )
    }

    fun channelInfo(cid: String): Channel? {
        channels[cid]?.let { return it }
        val c = try { api?.channelInfo(cid) } catch (e: Exception) { null } ?: return null
        channels[cid] = c
        return c
    }

    fun preview(m: Msg): String = api?.preview(m) ?: "New message"

    private fun connect() {
        if (token == null) return
        val my = ++gen
        val base = resumeUrl ?: "wss://gateway.discord.gg"
        val req = Request.Builder().url("$base/?v=9&encoding=json").header("User-Agent", UA2).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (my == gen) try { handle(webSocket, text) } catch (e: Exception) { }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (my == gen) lost(code)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (my == gen) lost(-1)
            }
        })
    }

    private fun lost(code: Int) {
        connected = false
        _events.tryEmit(GwState(false))
        hb?.cancel()
        if (code in intArrayOf(4004, 4010, 4011, 4012, 4013, 4014)) return
        if (code == 4007 || code == 4009) { sessionId = null; seq = null }
        val wait = minOf(30_000L, 1000L shl minOf(attempt, 5))
        attempt++
        retry?.cancel()
        retry = scope.launch {
            delay(wait)
            if (token != null) connect()
        }
    }

    private fun beat(w: WebSocket) {
        w.send("{\"op\":1,\"d\":" + (seq ?: "null") + "}")
    }

    private fun handle(w: WebSocket, text: String) {
        val o = JSONObject(text)
        if (!o.isNull("s")) seq = o.getInt("s")
        when (o.getInt("op")) {
            10 -> {
                val interval = o.getJSONObject("d").getLong("heartbeat_interval")
                hb?.cancel()
                hb = scope.launch {
                    delay((interval * Math.random()).toLong())
                    while (isActive) {
                        beat(w)
                        delay(interval)
                    }
                }
                if (sessionId != null && seq != null) resume(w) else identify(w)
            }
            1 -> beat(w)
            7 -> w.cancel()
            9 -> {
                if (!o.optBoolean("d")) { sessionId = null; seq = null }
                w.cancel()
            }
            0 -> dispatch(o.getString("t"), o.optJSONObject("d") ?: JSONObject())
        }
    }

    private fun identify(w: WebSocket) {
        val d = JSONObject()
            .put("token", token)
            .put("capabilities", 16381)
            .put(
                "properties",
                JSONObject().put("os", "Android").put("browser", "Discord Android")
                    .put("device", "Android").put("system_locale", "en-US")
            )
            .put(
                "presence",
                JSONObject().put("status", status).put("since", 0).put("activities", JSONArray()).put("afk", false)
            )
            .put("compress", false)
            .put("client_state", JSONObject().put("guild_versions", JSONObject()))
        w.send(JSONObject().put("op", 2).put("d", d).toString())
    }

    private fun resume(w: WebSocket) {
        w.send(
            JSONObject().put("op", 6).put(
                "d", JSONObject().put("token", token).put("session_id", sessionId).put("seq", seq)
            ).toString()
        )
    }

    private fun setPresence(id: String, st: String, acts: List<Activity> = emptyList()) {
        presence[id] = st
        if (acts.isEmpty()) activities.remove(id) else activities[id] = acts
        _events.tryEmit(GwPresence(id, st, acts))
    }

    private fun presences(arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val id = p.optJSONObject("user")?.str("id") ?: p.str("user_id") ?: continue
            setPresence(id, p.str("status") ?: continue, parseActivities(p.optJSONArray("activities")))
        }
    }

    private fun dispatch(t: String, d: JSONObject) {
        val parser = api ?: return
        when (t) {
            "READY" -> {
                sessionId = d.optString("session_id")
                resumeUrl = d.str("resume_gateway_url")
                myId = d.getJSONObject("user").getString("id")
                parser.myId = myId
                attempt = 0
                connected = true
                _events.tryEmit(GwState(true))
                presences(d.optJSONArray("presences"))
            }
            "RESUMED" -> { attempt = 0; connected = true; _events.tryEmit(GwState(true)) }
            "READY_SUPPLEMENTAL" -> presences(d.optJSONObject("merged_presences")?.optJSONArray("friends"))
            "PRESENCE_UPDATE" -> {
                val id = d.optJSONObject("user")?.str("id") ?: return
                setPresence(id, d.str("status") ?: return, parseActivities(d.optJSONArray("activities")))
            }
            "MESSAGE_CREATE" -> {
                val msg = parser.parseMessage(d)
                val cid = d.getString("channel_id")
                val gid = d.str("guild_id")
                UnreadStore.onMessage(cid, gid, msg.authorId, msg.mentionsMe, myId, if (AppState.foreground) AppState.openChannel else null)
                _events.tryEmit(GwCreated(msg, cid, gid))
            }
            "MESSAGE_UPDATE" -> {
                val cid = d.getString("channel_id")
                if (d.has("author")) _events.tryEmit(GwUpdated(parser.parseMessage(d), cid))
                else d.optJSONArray("embeds")?.let { _events.tryEmit(GwEmbeds(cid, d.getString("id"), parser.parseEmbeds(it))) }
            }
            "MESSAGE_DELETE" -> _events.tryEmit(GwDeleted(d.getString("channel_id"), d.getString("id")))
            "TYPING_START" -> {
                val name = d.optJSONObject("member")?.optJSONObject("user")?.let { it.str("global_name") ?: it.optString("username") }
                _events.tryEmit(GwTyping(d.getString("channel_id"), d.getString("user_id"), name))
            }
            "MESSAGE_REACTION_ADD", "MESSAGE_REACTION_REMOVE" -> {
                val e = d.getJSONObject("emoji")
                val eid = e.str("id")
                val nm = e.optString("name")
                _events.tryEmit(
                    GwReaction(
                        d.getString("channel_id"), d.getString("message_id"), d.getString("user_id"),
                        if (eid != null) "$nm:$eid" else nm, nm,
                        eid?.let { "https://cdn.discordapp.com/emojis/${it}.png?size=48" }, t == "MESSAGE_REACTION_ADD"
                    )
                )
            }
        }
    }
}
