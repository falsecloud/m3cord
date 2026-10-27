@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.example.m3cord

import android.graphics.Color as AColor
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { Notifier.sync(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Coil.setImageLoader(
            ImageLoader.Builder(this).components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }.crossfade(true).build()
        )
        // ask for notification permission on the very first launch
        val prefs = getSharedPreferences("m3cord", 0)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("asked_notif", false)
        ) {
            prefs.edit().putBoolean("asked_notif", true).apply()
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Notifier.sync(this)
        }
        handleIntent(intent)
        setContent { AppTheme(vm) { App(vm) } }
    }

    override fun onStart() { super.onStart(); AppState.foreground = true }
    override fun onStop() { super.onStop(); AppState.foreground = false }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val id = i?.getStringExtra("ch_id") ?: return
        vm.openFromNotification(id, i.getStringExtra("ch_name") ?: "chat", i.getIntExtra("ch_type", 0))
        i.removeExtra("ch_id")
    }
}

// ---------- theme ----------

val ACCENTS = listOf<Pair<String, Long>>(
    "Violet" to 0xFF6750A4, "Blue" to 0xFF1E6FD9, "Green" to 0xFF2E7D32,
    "Rose" to 0xFFC2185B, "Orange" to 0xFFE65100
)

@Composable
fun AppTheme(vm: MainViewModel, content: @Composable () -> Unit) {
    val dark = when (vm.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = when {
        vm.dynamic && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> {
            val c = Color(ACCENTS[vm.accent.coerceIn(0, ACCENTS.lastIndex)].second)
            if (dark) {
                val pc = lerp(c, Color.Black, 0.45f)
                darkColorScheme(
                    primary = lerp(c, Color.White, 0.45f), onPrimary = Color.Black,
                    primaryContainer = pc, onPrimaryContainer = Color.White,
                    secondaryContainer = pc, tertiaryContainer = pc, onTertiaryContainer = Color.White
                )
            } else {
                val pc = lerp(c, Color.White, 0.8f)
                lightColorScheme(
                    primary = c, onPrimary = Color.White,
                    primaryContainer = pc, onPrimaryContainer = lerp(c, Color.Black, 0.6f),
                    secondaryContainer = pc, tertiaryContainer = pc, onTertiaryContainer = lerp(c, Color.Black, 0.6f)
                )
            }
        }
    }
    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
        else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        (ctx as? ComponentActivity)?.enableEdgeToEdge(style, style)
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), content = content)
}

// ---------- shared helpers ----------

fun <T> bounce(damping: Float = 0.7f, stiffness: Float = 400f) = spring<T>(damping, stiffness)

fun Modifier.pressScale(src: InteractionSource, to: Float = 0.93f): Modifier = composed {
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) to else 1f, bounce(0.45f, 500f), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

@Composable
fun Modifier.enter(index: Int): Modifier {
    val p = remember { Animatable(if (index < 14) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (p.value < 1f) {
            delay((index * 30L).coerceAtMost(300L))
            p.animateTo(1f, bounce(0.7f, 300f))
        }
    }
    return graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = (1f - p.value) * 70f
        scaleX = 0.94f + 0.06f * p.value
        scaleY = 0.94f + 0.06f * p.value
    }
}

@Composable
fun AvatarImage(url: String?, name: String, size: Dp, modifier: Modifier = Modifier, status: String? = null) {
    Box(modifier.size(size)) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (url != null) {
                AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(
                    name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        val dot = when (status) {
            "online" -> Color(0xFF23A55A)
            "idle" -> Color(0xFFF0B232)
            "dnd" -> Color(0xFFF23F43)
            else -> null
        }
        if (dot != null) {
            val c by animateColorAsState(dot, bounce(1f, 400f), label = "status")
            val sc = remember { Animatable(0f) }
            LaunchedEffect(Unit) { sc.animateTo(1f, bounce(0.4f, 500f)) }
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.32f).graphicsLayer { scaleX = sc.value; scaleY = sc.value }
                    .background(MaterialTheme.colorScheme.surface, CircleShape).padding(size * 0.05f).background(c, CircleShape)
            )
        }
    }
}

// ---------- navigation ----------

sealed interface Screen {
    data object Login : Screen
    data object Main : Screen
    data class Chat(val ch: Channel) : Screen
}

private fun Screen.rank() = when (this) {
    Screen.Login -> 0
    Screen.Main -> 1
    is Screen.Chat -> 2
}

@Composable
fun App(vm: MainViewModel) {
    val o = vm.open
    val screen: Screen = when {
        vm.token == null -> Screen.Login
        o == null -> Screen.Main
        else -> Screen.Chat(o)
    }
    BackHandler(enabled = vm.open != null) { vm.closeChannel() }
    BackHandler(enabled = vm.open == null && vm.tab != 0 && vm.token != null) { vm.tab = 0 }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.error) {
        val e = vm.error
        if (e != null && vm.token != null) {
            snack.showSnackbar(e)
            vm.error = null
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = screen,
            contentKey = { it::class },
            transitionSpec = {
                val dir = if (targetState.rank() > initialState.rank()) 1 else -1
                (slideInHorizontally(bounce(0.85f, 500f)) { it * dir } + fadeIn(bounce(1f, 500f))) togetherWith
                    (slideOutHorizontally(bounce(0.85f, 500f)) { -it * dir / 3 } + fadeOut(bounce(1f, 500f)))
            },
            label = "screens"
        ) { s ->
            when (s) {
                Screen.Login -> LoginScreen(vm)
                Screen.Main -> MainScreen(vm)
                is Screen.Chat -> ChatScreen(vm, s.ch)
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding())
    }

    if (vm.profileId != null) ProfileSheet(vm)
    if (vm.editing) EditProfileSheet(vm)
    vm.viewer?.let { ImageViewer(it) { vm.viewer = null } }
    if (vm.searchOpen) SearchSheet(vm)
    vm.actionMsg?.let { MessageActionSheet(it, vm) }
}

// ---------- expressive button wrappers (shape-morphing on press) ----------

@Composable
fun XButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, shapes = ButtonDefaults.shapes(), content = content)
}

@Composable
fun XTonalButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(), content: @Composable RowScope.() -> Unit
) {
    FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled, shapes = ButtonDefaults.shapes(), colors = colors, content = content)
}

@Composable
fun XOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, shapes = ButtonDefaults.shapes(), content = content)
}

@Composable
fun XIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled, shapes = IconButtonDefaults.shapes(), content = content)
}

@Composable
fun XFilledIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    FilledIconButton(onClick = onClick, modifier = modifier, enabled = enabled, shapes = IconButtonDefaults.shapes(), content = content)
}

/** Bouncy scale-and-fade entrance for things that appear (chips, badges, dots). */
@Composable
fun Modifier.pop(): Modifier {
    val s = remember { Animatable(0f) }
    LaunchedEffect(Unit) { s.animateTo(1f, bounce(0.45f, 450f)) }
    return graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = s.value.coerceIn(0f, 1f) }
}
