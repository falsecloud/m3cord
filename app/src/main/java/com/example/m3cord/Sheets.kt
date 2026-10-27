@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.example.m3cord

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

@Composable
fun ProfileSheet(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = vm::dismissProfile) {
        val p = vm.profile
        if (p == null) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(64.dp))
            }
        } else {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.fillMaxWidth().height(110.dp).background(
                            p.accent?.let { Color(0xFF000000L or it.toLong()) } ?: cs.primaryContainer
                        )
                    ) {
                        p.banner?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                    AvatarImage(
                        p.avatar, p.name, 88.dp,
                        Modifier.align(Alignment.BottomStart).offset(x = 20.dp, y = 44.dp)
                            .border(4.dp, cs.surfaceContainerLow, CircleShape),
                        status = vm.presence[p.id]
                    )
                }
                Spacer(Modifier.height(54.dp))
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.enter(1))
                    Text(
                        "@" + p.username + (if (p.pronouns.isNotBlank()) "  •  " + p.pronouns else ""),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.enter(2)
                    )
                    if (p.bio.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().enter(3)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("ABOUT ME", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text(p.bio, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Joined Discord " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(p.created)),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    if (p.id == vm.me?.id) {
                        XTonalButton(onClick = { vm.dismissProfile(); vm.startEdit() }, modifier = Modifier.fillMaxWidth().height(52.dp).enter(4)) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Edit profile")
                        }
                    } else {
                        XButton(onClick = { vm.openDm(p.id) }, modifier = Modifier.fillMaxWidth().height(52.dp).enter(4)) {
                            Icon(Icons.Filled.Chat, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Message")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditProfileSheet(vm: MainViewModel) {
    val me = vm.me ?: return
    val mp = vm.myProfile
    var name by remember(mp) { mutableStateOf(mp?.name ?: me.name) }
    var bio by remember(mp) { mutableStateOf(mp?.bio ?: "") }
    var pronouns by remember(mp) { mutableStateOf(mp?.pronouns ?: "") }
    var avatar by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) avatar = it }

    ModalBottomSheet(onDismissRequest = { vm.editing = false }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Edit profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.enter(0))
            Box(Modifier.align(Alignment.CenterHorizontally).clickable { picker.launch("image/*") }) {
                AvatarImage(avatar?.toString() ?: me.avatar, name, 96.dp)
                Icon(
                    Icons.Filled.CameraAlt, "Change picture", tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.BottomEnd).size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape).padding(6.dp)
                )
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(32) }, label = { Text("Display name") },
                singleLine = true, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().enter(1)
            )
            OutlinedTextField(
                value = pronouns, onValueChange = { pronouns = it.take(40) }, label = { Text("Pronouns") },
                singleLine = true, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().enter(2)
            )
            OutlinedTextField(
                value = bio, onValueChange = { bio = it.take(190) }, label = { Text("About me") },
                supportingText = { Text("${bio.length}/190") },
                minLines = 3, maxLines = 6, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().enter(3)
            )
            XButton(
                onClick = { vm.saveProfile(name, bio, pronouns, avatar) },
                enabled = !vm.sending, modifier = Modifier.fillMaxWidth().height(56.dp).enter(4)
            ) {
                if (vm.sending) LoadingIndicator(Modifier.size(32.dp), color = LocalContentColor.current)
                else Text("Save changes")
            }
        }
    }
}

@Composable
fun ImageViewer(url: String, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val anim by animateFloatAsState(scale, bounce(0.8f, 900f), label = "zoom")
    val ent by animateFloatAsState(if (shown) 1f else 0f, bounce(0.7f, 350f), label = "viewerin")
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale <= 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f * ent.coerceIn(0f, 1f)))) {
            AsyncImage(
                url, null,
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onClose() },
                            onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }
                        )
                    }
                    .transformable(state)
                    .graphicsLayer {
                        val k = anim * (0.8f + 0.2f * ent)
                        scaleX = k; scaleY = k; translationX = offset.x; translationY = offset.y
                        alpha = ent.coerceIn(0f, 1f)
                    },
                contentScale = ContentScale.Fit
            )
            XIconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp).pop()) {
                Icon(Icons.Filled.Close, "Close", tint = Color.White)
            }
        }
    }
}
