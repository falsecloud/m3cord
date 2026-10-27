package com.example.m3cord

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Guild(val id: String, val name: String, val icon: String?)
data class Channel(
    val id: String, val name: String, val type: Int, val parentId: String?, val position: Int,
    val icon: String? = null, val recipientId: String? = null, val topic: String? = null
)
data class Me(val id: String, val username: String, val name: String, val avatar: String?, val nitro: Boolean = false)
data class Att(val url: String, val name: String, val mime: String, val size: Long, val w: Int, val h: Int, val waveform: String? = null, val secs: Float = 0f)
data class Emb(
    val type: String, val url: String?, val title: String?, val desc: String?, val color: Int?,
    val provider: String?, val author: String?, val image: String?, val thumb: String?,
    val video: String?, val footer: String?, val w: Int, val h: Int
)
data class Reply(val id: String, val author: String, val snippet: String)
data class Reaction(val key: String, val label: String, val url: String?, val count: Int, val me: Boolean)
data class Sticker(val id: String, val name: String, val format: Int)
data class Friend(val id: String, val name: String, val avatar: String?, val type: Int)
data class Note(val chId: String, val chName: String, val chType: Int, val author: String, val text: String, val msgId: String)
data class Msg(
    val id: String, val authorId: String, val author: String, val avatarUrl: String?,
    val content: String, val time: String, val ts: Long,
    val atts: List<Att>, val embeds: List<Emb>, val mentions: Map<String, String>,
    val mentionsMe: Boolean, val reply: Reply?,
    val reactions: List<Reaction> = emptyList(), val stickers: List<Sticker> = emptyList(), val edited: Boolean = false
)
data class Profile(
    val id: String, val username: String, val name: String, val avatar: String?, val banner: String?,
    val accent: Int?, val bio: String, val pronouns: String, val created: Long
)
class Upload(val name: String, val mime: String, val bytes: ByteArray, val secs: Float? = null, val waveform: String? = null)

fun avatarUrl(id: String, hash: String?, size: Int = 128): String? =
    hash?.let { "https://cdn.discordapp.com/avatars/$id/${it}.png?size=$size" }

fun snowflakeMs(id: String): Long = ((id.toLongOrNull() ?: 0L) shr 22) + 1420070400000L

fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)

private fun <T> JSONArray.mapObj(f: (JSONObject) -> T): List<T> =
    (0 until length()).map { f(getJSONObject(it)) }

private fun fmtTime(ts: String): String = try {
    java.time.OffsetDateTime.parse(ts)
        .atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm"))
} catch (e: Exception) { "" }

private fun parseTs(ts: String): Long = try {
    java.time.OffsetDateTime.parse(ts).toInstant().toEpochMilli()
} catch (e: Exception) { 0L }

private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; "gif" -> "image/gif"
    "mp4", "mov", "webm", "mkv" -> "video/mp4"
    "mp3", "ogg", "wav", "m4a", "flac", "aac", "opus" -> "audio/mpeg"
    else -> "application/octet-stream"
}

private const val BASE = "https://discord.com/api/v9"
private const val UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
private val JSON_T = "application/json".toMediaType()

class DiscordApi(private val token: String) {
    var myId: String = ""
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun call(path: String, method: String = "GET", json: JSONObject? = null, body: RequestBody? = null): String {
        val rb = body ?: json?.toString()?.toRequestBody(JSON_T)
        val req = Request.Builder().url(BASE + path)
            .header("Authorization", token)
            .header("User-Agent", UA)
            .method(method, if (method == "GET") null else (rb ?: ByteArray(0).toRequestBody(null)))
            .build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string() ?: ""
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            return t
        }
    }

    fun me(): Me {
        val o = JSONObject(call("/users/@me"))
        val id = o.getString("id")
        myId = id
        val un = o.optString("username")
        return Me(id, un, o.str("global_name") ?: un, avatarUrl(id, o.str("avatar"), 128), o.optInt("premium_type", 0) > 0)
    }

    fun guilds(): List<Guild> =
        JSONArray(call("/users/@me/guilds")).mapObj { Guild(it.getString("id"), it.getString("name"), it.str("icon")) }

    fun channels(gid: String): List<Channel> =
        JSONArray(call("/guilds/$gid/channels")).mapObj {
            Channel(it.getString("id"), it.str("name") ?: "", it.getInt("type"), it.str("parent_id"), it.optInt("position"), topic = it.str("topic"))
        }

    fun dms(): List<Channel> {
        val arr = JSONArray(call("/users/@me/channels"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .sortedByDescending { it.str("last_message_id")?.toLongOrNull() ?: 0L }
            .map { parseDm(it) }
    }

    fun openDm(userId: String): Channel =
        parseDm(JSONObject(call("/users/@me/channels", "POST", JSONObject().put("recipient_id", userId))))

    private fun parseDm(o: JSONObject): Channel {
        val rec = o.optJSONArray("recipients")
        val users = (0 until (rec?.length() ?: 0)).map { rec!!.getJSONObject(it) }
        val names = users.joinToString(", ") { it.str("global_name") ?: it.optString("username") }
        val type = o.getInt("type")
        val icon = if (users.size == 1 && type == 1) avatarUrl(users[0].getString("id"), users[0].str("avatar"), 96)
        else o.str("icon")?.let { "https://cdn.discordapp.com/channel-icons/${o.getString("id")}/${it}.png?size=96" }
        return Channel(o.getString("id"), o.str("name") ?: names.ifBlank { "Unknown" }, type, null, 0, icon, users.firstOrNull()?.getString("id"))
    }

    fun profile(id: String): Profile = try {
        val o = JSONObject(call("/users/$id/profile?with_mutual_guilds=false"))
        val up = o.optJSONObject("user_profile")
        mkProfile(o.getJSONObject("user"), up?.str("bio"), up?.str("pronouns"))
    } catch (e: Exception) {
        mkProfile(JSONObject(call("/users/$id")), null, null)
    }

    private fun mkProfile(u: JSONObject, bio: String?, pronouns: String?): Profile {
        val id = u.getString("id")
        val un = u.optString("username")
        return Profile(
            id, un, u.str("global_name") ?: un, avatarUrl(id, u.str("avatar"), 256),
            u.str("banner")?.let { "https://cdn.discordapp.com/banners/$id/${it}.png?size=480" },
            u.optInt("accent_color", -1).takeIf { it >= 0 }, bio ?: u.str("bio") ?: "", pronouns ?: "", snowflakeMs(id)
        )
    }

    // ---- emojis, GIFs, favorites, app icons ----

    fun guildEmojis(gid: String): List<CustomEmoji> {
        val arr = JSONArray(call("/guilds/$gid/emojis"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optBoolean("available", true) }
            .map { CustomEmoji(it.getString("id"), it.optString("name"), it.optBoolean("animated"), gid) }
    }

    /** Trending when [q] is blank, search otherwise. Tiny GIFs for the grid, full GIF kept as the stored src. */
    fun gifs(q: String?): List<FavGif> {
        val path = if (q.isNullOrBlank()) "/gifs/trending-gifs?media_format=tinygif&locale=en-US&limit=48"
        else "/gifs/search?q=" + java.net.URLEncoder.encode(q, "UTF-8") + "&media_format=tinygif&locale=en-US&limit=48"
        return JSONArray(call(path)).mapObj {
            FavGif(
                url = it.getString("url"), src = it.str("gif_src") ?: it.getString("src"),
                w = it.optInt("width"), h = it.optInt("height"), video = false, thumb = it.str("src")
            )
        }
    }

    /** Favorite GIFs live in the account's FrecencyUserSettings proto (type 2). */
    fun favGifs(): List<FavGif> = FavGifProto.decode(JSONObject(call("/users/@me/settings-proto/2")).optString("settings"))

    fun saveFavGifs(list: List<FavGif>) {
        call("/users/@me/settings-proto/2", "PATCH", JSONObject().put("settings", FavGifProto.encode(list)))
    }

    fun appIcon(appId: String): String? {
        val icon = JSONObject(call("/applications/$appId/rpc")).str("icon") ?: return null
        return "https://cdn.discordapp.com/app-icons/" + appId + "/" + icon + ".png?size=256"
    }

    fun patchMe(json: JSONObject) { call("/users/@me", "PATCH", json) }
    fun patchProfile(json: JSONObject) { call("/users/@me/profile", "PATCH", json) }

    fun messages(cid: String, before: String? = null): List<Msg> =
        JSONArray(call("/channels/$cid/messages?limit=50" + (if (before != null) "&before=$before" else ""))).mapObj { parseMsg(it) }

    fun send(cid: String, text: String, replyId: String?, files: List<Upload>): Msg {
        val payload = JSONObject().put("content", text)
        if (replyId != null) payload.put("message_reference", JSONObject().put("message_id", replyId))
        if (files.isEmpty()) return parseMsg(JSONObject(call("/channels/$cid/messages", "POST", json = payload)))
        val atts = JSONArray()
        files.forEachIndexed { i, f ->
            val ao = JSONObject().put("id", i).put("filename", f.name)
            if (f.secs != null) ao.put("duration_secs", f.secs.toDouble()).put("waveform", f.waveform ?: "")
            atts.put(ao)
        }
        payload.put("attachments", atts)
        if (files.any { it.secs != null }) payload.put("flags", 8192)
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("payload_json", null, payload.toString().toRequestBody(JSON_T))
        files.forEachIndexed { i, f ->
            mb.addFormDataPart("files[$i]", f.name, f.bytes.toRequestBody(f.mime.toMediaTypeOrNull()))
        }
        return parseMsg(JSONObject(call("/channels/$cid/messages", "POST", body = mb.build())))
    }

    fun preview(m: Msg): String = when {
        m.content.isNotBlank() -> m.content.take(200)
        m.atts.any { it.waveform != null } -> "Sent a voice message"
        m.atts.isNotEmpty() -> "Sent an attachment"
        m.stickers.isNotEmpty() -> "Sent a sticker"
        else -> "Sent a message"
    }

    /** New DMs since the last poll. `last` holds the last seen message id per DM channel. */
    fun pollDms(last: MutableMap<String, String>, first: Boolean): List<Note> {
        val arr = JSONArray(call("/users/@me/channels"))
        val out = ArrayList<Note>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            val lm = o.str("last_message_id") ?: continue
            val prev = last[id]
            last[id] = lm
            if (first) continue
            if (prev != null && lm == prev) continue
            val m = JSONArray(call("/channels/$id/messages?limit=1")).mapObj { parseMsg(it) }.firstOrNull() ?: continue
            if (m.authorId == myId) continue
            val ch = parseDm(o)
            out.add(Note(id, ch.name, ch.type, m.author, preview(m), m.id))
        }
        return out
    }

    /** New mentions since lastSeen. Returns (newest id, notes). lastSeen null means just set the baseline. */
    fun pollMentions(lastSeen: String?): Pair<String?, List<Note>> {
        val arr = JSONArray(call("/users/@me/mentions?limit=10"))
        val raw = (0 until arr.length()).map { arr.getJSONObject(it) }
        val newest = raw.firstOrNull()?.getString("id") ?: lastSeen
        if (lastSeen == null) return Pair(newest, emptyList())
        val base = lastSeen.toLongOrNull() ?: 0L
        val notes = raw.filter { (it.getString("id").toLongOrNull() ?: 0L) > base }.reversed().mapNotNull { o ->
            val m = parseMsg(o)
            if (m.authorId == myId) null else {
                val cid = o.getString("channel_id")
                val nm = try { "#" + JSONObject(call("/channels/$cid")).optString("name") } catch (e: Exception) { "a server" }
                Note(cid, nm, 0, m.author, preview(m), m.id)
            }
        }
        return Pair(newest, notes)
    }

    fun parseMessage(o: JSONObject): Msg = parseMsg(o)
    fun parseEmbeds(arr: JSONArray): List<Emb> = arr.mapObj { parseEmbed(it) }
    fun typing(cid: String) { call("/channels/$cid/typing", "POST") }

    fun channelInfo(cid: String): Channel {
        val o = JSONObject(call("/channels/$cid"))
        val t = o.getInt("type")
        return if (t == 1 || t == 3) parseDm(o) else Channel(cid, o.optString("name"), t, null, 0)
    }

    fun react(cid: String, mid: String, key: String, add: Boolean) {
        val enc = java.net.URLEncoder.encode(key, "UTF-8").replace("+", "%20")
        call("/channels/$cid/messages/$mid/reactions/$enc/@me", if (add) "PUT" else "DELETE")
    }

    fun edit(cid: String, mid: String, text: String): Msg =
        parseMsg(JSONObject(call("/channels/$cid/messages/$mid", "PATCH", JSONObject().put("content", text))))

    fun delete(cid: String, mid: String) { call("/channels/$cid/messages/$mid", "DELETE") }

    fun friends(): List<Friend> =
        JSONArray(call("/users/@me/relationships")).mapObj { r ->
            val u = r.getJSONObject("user")
            val id = u.getString("id")
            Friend(id, userName(u), avatarUrl(id, u.str("avatar"), 96), r.optInt("type"))
        }.filter { it.type == 1 || it.type == 3 }.sortedBy { it.name.lowercase() }

    fun search(cid: String, guildId: String?, q: String): List<Msg>? {
        val enc = java.net.URLEncoder.encode(q, "UTF-8")
        val path = if (guildId == null) "/channels/$cid/messages/search?content=$enc"
        else "/guilds/$guildId/messages/search?content=$enc&channel_id=$cid"
        val groups = JSONObject(call(path)).optJSONArray("messages") ?: return null
        val out = ArrayList<Msg>()
        for (i in 0 until groups.length()) {
            val g = groups.getJSONArray(i)
            for (j in 0 until g.length()) {
                val m = g.getJSONObject(j)
                if (m.optBoolean("hit")) out.add(parseMsg(m))
            }
        }
        return out
    }

    private fun userName(u: JSONObject) = u.str("global_name") ?: u.optString("username")

    private fun parseEmbed(e: JSONObject): Emb {
        fun sub(k: String, f: String): String? = e.optJSONObject(k)?.str(f)
        val dims = e.optJSONObject("video") ?: e.optJSONObject("image") ?: e.optJSONObject("thumbnail")
        return Emb(
            type = e.optString("type", "rich"),
            url = e.str("url"), title = e.str("title"), desc = e.str("description"),
            color = if (e.has("color") && !e.isNull("color")) e.optInt("color") else null,
            provider = sub("provider", "name"), author = sub("author", "name"),
            image = sub("image", "proxy_url") ?: sub("image", "url"),
            thumb = sub("thumbnail", "proxy_url") ?: sub("thumbnail", "url"),
            video = sub("video", "url") ?: sub("video", "proxy_url"),
            footer = sub("footer", "text"),
            w = dims?.optInt("width") ?: 0, h = dims?.optInt("height") ?: 0
        )
    }

    private fun parseMsg(o: JSONObject): Msg {
        val a = o.getJSONObject("author")
        val aid = a.getString("id")
        val mentions = HashMap<String, String>()
        var me = o.optBoolean("mention_everyone")
        o.optJSONArray("mentions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val u = arr.getJSONObject(i)
                val id = u.getString("id")
                mentions[id] = userName(u)
                if (id == myId) me = true
            }
        }
        val ref = o.optJSONObject("referenced_message")
        val reply = if (o.has("message_reference") && !o.isNull("message_reference")) {
            if (ref != null) {
                val sn = ref.optString("content").replace("\n", " ").take(90)
                Reply(ref.optString("id"), userName(ref.getJSONObject("author")), sn.ifBlank { "Attachment" })
            } else Reply(o.getJSONObject("message_reference").optString("message_id"), "", "Original message unavailable")
        } else null
        val atts = o.optJSONArray("attachments")?.mapObj {
            val fn = it.optString("filename", "file")
            Att(it.getString("url"), fn, it.str("content_type") ?: guessMime(fn), it.optLong("size"), it.optInt("width"), it.optInt("height"), it.str("waveform"), it.optDouble("duration_secs", 0.0).toFloat())
        } ?: emptyList()
        val embeds = o.optJSONArray("embeds")?.mapObj { parseEmbed(it) } ?: emptyList()
        val reactions = o.optJSONArray("reactions")?.mapObj { r ->
            val e = r.getJSONObject("emoji")
            val eid = e.str("id")
            val nm = e.optString("name")
            Reaction(if (eid != null) "$nm:$eid" else nm, nm, eid?.let { "https://cdn.discordapp.com/emojis/${it}.png?size=48" }, r.optInt("count"), r.optBoolean("me"))
        } ?: emptyList()
        val stickers = o.optJSONArray("sticker_items")?.mapObj { Sticker(it.getString("id"), it.optString("name"), it.optInt("format_type")) } ?: emptyList()
        var text = o.optString("content")
        if (text.isBlank() && atts.isEmpty() && embeds.isEmpty() && stickers.isEmpty()) text = "(system message)"
        return Msg(
            o.getString("id"), aid, userName(a), avatarUrl(aid, a.str("avatar"), 64), text,
            fmtTime(o.optString("timestamp")), parseTs(o.optString("timestamp")),
            atts, embeds, mentions, me, reply, reactions, stickers, !o.isNull("edited_timestamp")
        )
    }
}
