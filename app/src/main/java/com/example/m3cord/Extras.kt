@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.example.m3cord

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun SearchSheet(vm: MainViewModel) {
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { vm.searchOpen = false; vm.searchResults = emptyList(); vm.searchStatus = null }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true, shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search this chat") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(q) }),
                modifier = Modifier.fillMaxWidth().enter(0)
            )
            XButton(onClick = { vm.search(q) }, enabled = q.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp).enter(1)) { Text("Search") }
            AnimatedContent(targetState = vm.searchStatus, label = "status") { st ->
                if (st != null) Text(st, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(vm.searchResults, key = { _, m -> m.id }) { i, m ->
                    Surface(
                        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.animateItem().enter(i).fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AvatarImage(m.avatarUrl, m.author, 36.dp)
                            Column {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(m.author, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(m.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(m.content.ifBlank { "(attachment)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                            }
                        }
                    }
                }
            }
        }
    }
}
