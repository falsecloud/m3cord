@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.example.m3cord

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// ---------- models ----------

data class CustomEmoji(val id: String, val name: String, val animated: Boolean, val guildId: String)
data class EmojiPack(val guildId: String, val name: String, val icon: String?, val emojis: List<CustomEmoji>)

/** The GIF in a message that can be favorited: a Tenor/Giphy "gifv" embed or a .gif attachment. */
fun gifOf(m: Msg): FavGif? {
    m.embeds.firstOrNull { it.type == "gifv" && it.url != null && (it.video != null || it.thumb != null) }?.let {
        return FavGif(it.url!!, it.video ?: it.thumb!!, it.w, it.h, video = it.video != null)
    }
    m.atts.firstOrNull { it.mime == "image/gif" }?.let { return FavGif(it.url, it.url, it.w, it.h, false) }
    return null
}

// ---------- the panel ----------

/** Half-screen picker that sits under the input bar: a GIFs tab (search + account favorites) and an Emojis tab. */
@Composable
fun EmojiPanel(vm: MainViewModel, onEmoji: (String) -> Unit, onGif: (FavGif) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableStateOf(1) }
    val half = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    // when the keyboard is up (typing in the panel's search box) the panel gives some room back to the chat
    val target = if (WindowInsets.isImeVisible) minOf(half, 250.dp) else half
    val h by animateDpAsState(target, bounce(0.8f, 400f), label = "panelh")
    Surface(
        modifier = modifier.fillMaxWidth().height(h).padding(horizontal = 6.dp),
        shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerLow, tonalElevation = 2.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Segmented(listOf("GIFs", "Emojis"), tab) { tab = it }
            }
            AnimatedContent(
                targetState = tab, label = "emojitabs", modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(spring(0.8f, 400f)) { w -> dir * w / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally(spring(0.8f, 400f)) { w -> -dir * w / 3 } + fadeOut())
                }
            ) { t -> if (t == 0) GifTab(vm, onGif) else EmojiTab(vm, onEmoji) }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    TextField(
        value = value, onValueChange = onChange, singleLine = true, shape = RoundedCornerShape(28.dp),
        placeholder = { Text(hint, maxLines = 1) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            AnimatedVisibility(value.isNotEmpty(), enter = scaleIn(bounce(0.5f, 500f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                XIconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Clear") }
            }
        },
        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    )
}

// ---------- GIFs ----------

@Composable
private fun GifTab(vm: MainViewModel, onGif: (FavGif) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var q by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(0) } // 0 = favorites, 1 = trending
    LaunchedEffect(Unit) { vm.loadFavGifs() }
    LaunchedEffect(q, mode) { if (q.isNotBlank() || mode == 1) vm.searchGifs(q) }
    val showFavs = q.isBlank() && mode == 0
    val items = remember(showFavs, vm.favGifs, vm.gifResults) {
        (if (showFavs) vm.favGifs else vm.gifResults).distinctBy { it.url }
    }
    val loading = if (showFavs) (!vm.favReady && !vm.favError) else vm.gifLoading

    Column(Modifier.fillMaxSize()) {
        SearchField(q, { q = it }, "Search GIFs")
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = showFavs, onClick = { q = ""; mode = 0 }, label = { Text("Favorites") },
                leadingIcon = { Icon(Icons.Filled.Favorite, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = q.isBlank() && mode == 1, onClick = { q = ""; mode = 1 }, label = { Text("Trending") },
                leadingIcon = { Icon(Icons.Filled.Whatshot, null, Modifier.size(18.dp)) }
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (loading) {
                        LoadingIndicator(Modifier.size(48.dp))
                    } else {
                        Icon(
                            if (showFavs) Icons.Filled.FavoriteBorder else Icons.Filled.SearchOff, null,
                            Modifier.size(40.dp).pop(), tint = cs.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                showFavs && vm.favError -> "Couldn't load your favorite GIFs.\nClose and reopen this panel to retry."
                                showFavs -> "No favorite GIFs yet.\nTap the heart on any GIF to save it."
                                vm.gifError != null -> vm.gifError!!
                                else -> "No GIFs found"
                            },
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 12.dp),
                    verticalItemSpacing = 8.dp, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items, key = { _, g -> g.url }) { i, g -> GifTile(vm, g, i) { onGif(g) } }
                }
            }
        }
    }
}

@Composable
fun GifImage(g: FavGif, modifier: Modifier) {
    // video favorites: try the GIF twin first, then fall back to a still frame of the video itself
    var useRaw by remember(g.url) { mutableStateOf(false) }
    val url = g.thumb ?: if (useRaw) g.src else gifImageUrl(g.src)
    AsyncImage(
        model = url, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop,
        onError = { if (!useRaw) useRaw = true }
    )
}

@Composable
private fun GifTile(vm: MainViewModel, g: FavGif, index: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val ratio = if (g.w > 0 && g.h > 0) g.w.toFloat() / g.h else 1f
    val fav = vm.isFav(g.url)
    Box(
        Modifier.enter(index).pressScale(src, 0.92f).fillMaxWidth().aspectRatio(ratio.coerceIn(0.6f, 2f))
            .clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHighest)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
    ) {
        GifImage(g, Modifier.fillMaxSize())
        HeartButton(fav, Modifier.align(Alignment.TopEnd).padding(6.dp)) { vm.toggleFavGif(g) }
    }
}

@Composable
private fun HeartButton(fav: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val s = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(fav) {
        if (first) first = false else { s.snapTo(0.5f); s.animateTo(1f, bounce(0.3f, 500f)) }
    }
    val tint by animateColorAsState(if (fav) Color(0xFFFF5C7A) else Color.White, bounce(1f, 500f), label = "heart")
    Box(
        modifier.size(34.dp).graphicsLayer { scaleX = s.value; scaleY = s.value }.clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (fav) "Remove favorite" else "Favorite", Modifier.size(20.dp), tint = tint)
    }
}

// ---------- emojis ----------

private sealed interface EItem {
    val key: String
    data class Head(override val key: String, val title: String, val icon: String?, val glyph: String?) : EItem
    data class Custom(val e: CustomEmoji) : EItem { override val key: String get() = "c" + e.id }
    data class Uni(val e: UEmoji) : EItem { override val key: String get() = "u" + e.char }
}

private fun buildEmojiItems(packs: List<EmojiPack>, q: String): List<EItem> {
    val out = ArrayList<EItem>()
    if (q.isEmpty()) {
        for (p in packs) {
            out += EItem.Head("g" + p.guildId, p.name, p.icon?.let { "https://cdn.discordapp.com/icons/${p.guildId}/${it}.png?size=64" }, null)
            p.emojis.forEach { out += EItem.Custom(it) }
        }
        for ((title, glyph, list) in UnicodeEmoji.categories) {
            out += EItem.Head("k$title", title, null, glyph)
            list.forEach { out += EItem.Uni(it) }
        }
    } else {
        val customs = packs.flatMap { it.emojis }.filter { it.name.contains(q, ignoreCase = true) }
        val unis = UnicodeEmoji.categories.flatMap { it.third }.filter { it.names.contains(q, ignoreCase = true) }
        if (customs.isNotEmpty()) {
            out += EItem.Head("rc", "Server emojis", null, "\u2728")
            customs.take(300).forEach { out += EItem.Custom(it) }
        }
        if (unis.isNotEmpty()) {
            out += EItem.Head("ru", "Emojis", null, "\uD83D\uDE00")
            unis.take(300).forEach { out += EItem.Uni(it) }
        }
    }
    return out
}

@Composable
private fun EmojiTab(vm: MainViewModel, onEmoji: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var q by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadEmojis() }
    val packs = vm.emojiPacks
    val items = remember(packs, q) { buildEmojiItems(packs, q.trim()) }
    val heads = remember(items) { items.mapIndexedNotNull { i, it -> (it as? EItem.Head)?.let { h -> i to h } } }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val current by remember(heads) {
        derivedStateOf { val f = grid.firstVisibleItemIndex; heads.lastOrNull { it.first <= f }?.second?.key }
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(q, { q = it }, "Search emojis")
        AnimatedVisibility(q.isBlank(), enter = scaleIn(bounce(0.6f, 400f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                items(heads, key = { it.second.key }) { (idx, h) ->
                    JumpChip(h, h.key == current) { scope.launch { grid.animateScrollToItem(idx) } }
                }
                if (vm.emojiLoading) item { LoadingIndicator(Modifier.size(30.dp)) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(46.dp), state = grid, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            items(items, key = { it.key }, span = { if (it is EItem.Head) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { item ->
                when (item) {
                    is EItem.Head -> Row(
                        Modifier.fillMaxWidth().padding(start = 6.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.icon != null) AsyncImage(item.icon, null, Modifier.size(20.dp).clip(CircleShape))
                        else Text(item.glyph ?: item.title.take(1).uppercase(), fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    is EItem.Custom -> EmojiCell({ onEmoji(vm.emojiText(item.e)) }) {
                        AsyncImage(
                            "https://cdn.discordapp.com/emojis/${item.e.id}.png?size=64", item.e.name,
                            Modifier.fillMaxSize().padding(5.dp)
                        )
                    }
                    is EItem.Uni -> EmojiCell({ onEmoji(item.e.char) }) {
                        Text(item.e.char, fontSize = 26.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiCell(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).padding(2.dp).pressScale(src, 0.6f).clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center, content = content
    )
}

/** Jump-to-section chip: the selected one springs up and morphs from a circle to a rounded square. */
@Composable
private fun JumpChip(h: EItem.Head, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) cs.primaryContainer else cs.surfaceContainerHighest, bounce(1f, 500f), label = "chipbg")
    val sc by animateFloatAsState(if (selected) 1.12f else 1f, bounce(0.45f, 500f), label = "chipsc")
    val r by animateDpAsState(if (selected) 12.dp else 19.dp, bounce(0.5f, 500f), label = "chipr")
    Box(
        Modifier.size(38.dp).graphicsLayer { scaleX = sc; scaleY = sc }.pressScale(src, 0.8f)
            .clip(RoundedCornerShape(r)).background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (h.icon != null) AsyncImage(h.icon, h.title, Modifier.fillMaxSize().padding(5.dp).clip(CircleShape))
        else Text(h.glyph ?: h.title.take(1).uppercase(), fontSize = 18.sp)
    }
}
