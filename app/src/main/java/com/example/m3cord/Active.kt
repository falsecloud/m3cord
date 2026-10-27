@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.example.m3cord

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONArray

/** A rich-presence activity (game, music, stream...). Custom statuses (type 4) are skipped. */
data class Activity(
    val type: Int, val name: String, val details: String?, val state: String?,
    val appId: String?, val image: String?
)

fun parseActivities(arr: JSONArray?): List<Activity> {
    if (arr == null) return emptyList()
    val out = ArrayList<Activity>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val type = o.optInt("type", 0)
        if (type == 4) continue
        val appId = o.str("application_id")
        out += Activity(
            type, o.optString("name"), o.str("details"), o.str("state"), appId,
            activityImage(appId, o.optJSONObject("assets")?.str("large_image"))
        )
    }
    return out
}

/** Turns a rich-presence asset key into a real image URL (null when it can't be resolved). */
fun activityImage(appId: String?, raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return when {
        raw.startsWith("mp:external/") -> "https://media.discordapp.net/external/" + raw.removePrefix("mp:external/")
        raw.startsWith("mp:") -> "https://media.discordapp.net/" + raw.removePrefix("mp:")
        raw.startsWith("spotify:") -> "https://i.scdn.co/image/" + raw.removePrefix("spotify:")
        raw.startsWith("youtube:") -> "https://i.ytimg.com/vi/" + raw.removePrefix("youtube:") + "/hqdefault_live.jpg"
        raw.startsWith("twitch:") -> "https://static-cdn.jtvnw.net/previews-ttv/live_user_" + raw.removePrefix("twitch:") + "-440x248.jpg"
        appId != null && raw.all { it.isDigit() } -> "https://cdn.discordapp.com/app-assets/" + appId + "/" + raw + ".png?size=256"
        else -> null
    }
}

private fun Activity.summary(): String = when (type) {
    2 -> listOfNotNull(details, state).joinToString(" \u2014 ").ifBlank { name }
    1, 3 -> details ?: name
    else -> name
}

private fun activityIcon(type: Int): ImageVector = when (type) {
    1 -> Icons.Filled.Videocam
    2 -> Icons.Filled.Headphones
    3 -> Icons.Filled.Tv
    5 -> Icons.Filled.EmojiEvents
    else -> Icons.Filled.SportsEsports
}

/**
 * "Active now" strip for the top of the Friends list: one card per friend who is playing, listening or streaming.
 * The carousel itself is Orion Store's Recommended component: an M3 multi-browse carousel whose cards are masked
 * to a rounded shape and whose copy fades out as a card shrinks toward the edge.
 */
@Composable
fun ActiveNow(vm: MainViewModel, active: List<Pair<Friend, Activity>>) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().enter(0).padding(bottom = 6.dp)) {
        Text(
            "Active now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = cs.primary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { active.size },
            preferredItemWidth = 292.dp,
            modifier = Modifier.fillMaxWidth().height(124.dp),
            itemSpacing = 10.dp,
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) { i ->
            val (friend, act) = active[i]
            val info = carouselItemDrawInfo
            val fade = { ((info.size - info.minSize) / (info.maxSize - info.minSize).coerceAtLeast(1f)).coerceIn(0f, 1f) }
            ActiveCard(vm, friend, act, fade, Modifier.fillMaxSize().maskClip(RoundedCornerShape(28.dp)))
        }
    }
}

@Composable
private fun ActiveCard(vm: MainViewModel, f: Friend, a: Activity, fade: () -> Float, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    LaunchedEffect(a.appId, a.image) { if (a.image == null && a.appId != null) vm.resolveAppIcon(a.appId) }
    val img = a.image ?: a.appId?.let { vm.appIcons[it] }?.takeIf { it.isNotBlank() }
    val tint = when (a.type) {
        1 -> Color(0xFF9146FF)
        2 -> Color(0xFF1DB954)
        3 -> cs.primary
        5 -> cs.tertiary
        else -> Color(0xFF23A55A)
    }
    Box(
        modifier.background(cs.surfaceContainer)
            .clickable(interactionSource = src, indication = null) { vm.showProfile(f.id) }
    ) {
        Row(Modifier.fillMaxSize().pressScale(src, 0.96f).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val artShape = RoundedCornerShape(22.dp)
            Box(
                Modifier.size(100.dp).clip(artShape).background(cs.surfaceContainerHighest)
                    .border(2.dp, tint.copy(alpha = 0.55f), artShape),
                contentAlignment = Alignment.Center
            ) {
                if (img != null) AsyncImage(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(activityIcon(a.type), null, Modifier.size(40.dp), tint = cs.onSurfaceVariant)
            }
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier.weight(1f).fillMaxHeight().graphicsLayer { alpha = fade() },
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    AvatarImage(f.avatar, f.name, 40.dp, status = vm.presence[f.id])
                    Spacer(Modifier.weight(1f))
                    Icon(activityIcon(a.type), null, Modifier.size(26.dp).pop(), tint = tint)
                }
                Column {
                    Text(f.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.summary(), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
