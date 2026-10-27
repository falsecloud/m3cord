package com.example.m3cord

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Unread markers, fed straight from the gateway so it keeps counting while the UI is closed.
 * badge/guildBadge = noticeable markers (DMs and mentions, with a count)
 * quiet/guildQuiet = small white dots (any other new message in a server channel)
 */
object UnreadStore {
    val badge = mutableStateMapOf<String, Int>()
    val quiet = mutableStateMapOf<String, Boolean>()
    val guildBadge = mutableStateMapOf<String, Int>()
    val guildQuiet = mutableStateMapOf<String, Boolean>()
    private val guildOf = ConcurrentHashMap<String, String>()
    private var prefs: SharedPreferences? = null
    var serverDots by mutableStateOf(true)
        private set

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("m3cord_unread", 0)
        prefs = p
        serverDots = p.getBoolean("dots", true)
        try {
            val o = JSONObject(p.getString("u", "{}") ?: "{}")
            o.optJSONObject("badge")?.let { j -> j.keys().forEach { k -> badge[k] = j.getInt(k) } }
            o.optJSONObject("gb")?.let { j -> j.keys().forEach { k -> guildBadge[k] = j.getInt(k) } }
            o.optJSONObject("go")?.let { j -> j.keys().forEach { k -> guildOf[k] = j.getString(k) } }
            o.optJSONArray("quiet")?.let { a -> for (i in 0 until a.length()) quiet[a.getString(i)] = true }
            o.optJSONArray("gq")?.let { a -> for (i in 0 until a.length()) guildQuiet[a.getString(i)] = true }
        } catch (e: Exception) { }
    }

    private fun save() {
        val p = prefs ?: return
        try {
            val o = JSONObject()
                .put("badge", JSONObject(badge.toMap()))
                .put("gb", JSONObject(guildBadge.toMap()))
                .put("go", JSONObject(guildOf.toMap()))
                .put("quiet", JSONArray(quiet.keys.toList()))
                .put("gq", JSONArray(guildQuiet.keys.toList()))
            p.edit().putString("u", o.toString()).apply()
        } catch (e: Exception) { }
    }

    @Synchronized
    fun onMessage(cid: String, guildId: String?, authorId: String, mentionsMe: Boolean, myId: String, openId: String?) {
        if (authorId == myId || cid == openId) return
        if (guildId == null || mentionsMe) {
            badge[cid] = (badge[cid] ?: 0) + 1
            if (guildId != null) {
                guildOf[cid] = guildId
                guildBadge[guildId] = (guildBadge[guildId] ?: 0) + 1
            }
        } else if (serverDots) {
            guildOf[cid] = guildId
            quiet[cid] = true
            guildQuiet[guildId] = true
        }
        save()
    }

    @Synchronized
    fun clear(cid: String) {
        val n = badge.remove(cid) ?: 0
        quiet.remove(cid)
        val g = guildOf[cid]
        if (g != null) {
            if (n > 0) {
                val left = (guildBadge[g] ?: 0) - n
                if (left <= 0) guildBadge.remove(g) else guildBadge[g] = left
            }
            if (quiet.keys.none { guildOf[it] == g }) guildQuiet.remove(g)
        }
        save()
    }

    @Synchronized
    fun clearGuild(g: String) {
        guildOf.filterValues { it == g }.keys.forEach { badge.remove(it); quiet.remove(it) }
        guildBadge.remove(g)
        guildQuiet.remove(g)
        save()
    }

    @Synchronized
    @JvmName("applyServerDots") fun setServerDots(v: Boolean) {
        serverDots = v
        prefs?.edit()?.putBoolean("dots", v)?.apply()
        if (!v) { quiet.clear(); guildQuiet.clear(); save() }
    }

    @Synchronized
    fun reset() {
        badge.clear(); quiet.clear(); guildBadge.clear(); guildQuiet.clear(); guildOf.clear()
        save()
    }
}
