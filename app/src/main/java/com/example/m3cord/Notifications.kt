package com.example.m3cord

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AppState {
    @Volatile var foreground = false
    @Volatile var openChannel: String? = null
}

object Notifier {
    private const val CH_MSG = "messages"
    private const val CH_BG = "background"

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MSG, "Messages", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_BG, "Background connection", NotificationManager.IMPORTANCE_MIN))
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Starts or stops the background service depending on login, the setting and the permission. */
    fun sync(ctx: Context) {
        try {
            val p = ctx.getSharedPreferences("m3cord", 0)
            val ok = p.getString("token", null) != null && p.getBoolean("notif", true) && canPost(ctx)
            val i = Intent(ctx, NotifyService::class.java)
            if (ok) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i)
        } catch (e: Exception) { /* background start not allowed, ignore */ }
    }

    private fun openIntent(ctx: Context, n: Note?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (n != null) {
                putExtra("ch_id", n.chId)
                putExtra("ch_name", n.chName)
                putExtra("ch_type", n.chType)
            }
        }
        return PendingIntent.getActivity(
            ctx, n?.chId?.hashCode() ?: 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun foregroundNotification(ctx: Context): Notification =
        NotificationCompat.Builder(ctx, CH_BG)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("M3Cord")
            .setContentText("Listening for messages")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openIntent(ctx, null))
            .build()

    fun show(ctx: Context, n: Note) {
        if (!canPost(ctx)) return
        val title = if (n.chType == 1) n.author else n.author + " in " + n.chName
        val notif = NotificationCompat.Builder(ctx, CH_MSG)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, n))
            .build()
        try { NotificationManagerCompat.from(ctx).notify(n.chId.hashCode(), notif) } catch (e: SecurityException) { }
    }

    fun cancel(ctx: Context, chId: String) {
        try { NotificationManagerCompat.from(ctx).cancel(chId.hashCode()) } catch (e: Exception) { }
    }
}

class NotifyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var gwJob: Job? = null
    private val notified: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.channels(this)
        UnreadStore.init(this)
        ServiceCompat.startForeground(
            this, 1, Notifier.foregroundNotification(this),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        if (job == null) job = scope.launch { loop() }
        val p = getSharedPreferences("m3cord", 0)
        p.getString("token", null)?.let { GatewayHub.start(it, p.getString("status", "online") ?: "online") }
        if (gwJob == null) gwJob = scope.launch { GatewayHub.events.collect { onGw(it) } }
        return START_STICKY
    }

    private fun onGw(e: Gw) {
        if (e !is GwCreated || AppState.foreground) return
        val m = e.msg
        if (m.authorId == GatewayHub.myId) return
        val dm = e.guildId == null
        if (!dm && !m.mentionsMe) return
        if (!getSharedPreferences("m3cord", 0).getBoolean("notif", true)) return
        val info = GatewayHub.channelInfo(e.channelId) ?: return
        val note = Note(e.channelId, if (dm) info.name else "#" + info.name, info.type, m.author, GatewayHub.preview(m), m.id)
        if (notified.add(m.id)) Notifier.show(this, note)
    }

    private suspend fun loop() {
        val prefs = getSharedPreferences("m3cord", 0)
        val last = HashMap<String, String>()
        var mentionSeen: String? = null
        var first = true
        var api: DiscordApi? = null
        var tokenUsed: String? = null
        while (true) {
            val token = prefs.getString("token", null)
            if (token == null || !prefs.getBoolean("notif", true)) { stopSelf(); return }
            try {
                var a = api
                if (a == null || token != tokenUsed) {
                    a = DiscordApi(token)
                    a.me()
                    api = a
                    tokenUsed = token
                    first = true
                    last.clear()
                    mentionSeen = null
                }
                if (GatewayHub.connected) {
                    // live gateway is delivering notifications, just keep the polling baseline fresh
                    first = true
                    mentionSeen = null
                    delay(20_000L)
                    continue
                }
                val dmNotes = a.pollDms(last, first)
                val (newest, mentionNotes) = a.pollMentions(mentionSeen)
                mentionSeen = newest ?: "0"
                first = false
                for (n in dmNotes + mentionNotes) {
                    if (notified.add(n.msgId) && !AppState.foreground) Notifier.show(this, n)
                }
            } catch (e: Exception) {
                if (e.message?.contains("401") == true) { stopSelf(); return }
            }
            delay(if (AppState.foreground) 20_000L else 15_000L)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
