package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ManagedApp
import com.example.data.model.ManagedUser
import com.example.data.model.PranavJsonHelper
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassCard
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.StatusPill

@Composable
fun UsersManagementTab(
    users: List<ManagedUser>,
    apps: List<ManagedApp>,
    onSaveUser: (ManagedUser) -> Unit,
    onToggleBan: (ManagedUser, Boolean, String) -> Unit,
    onDeleteUser: (ManagedUser) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var visibleCount by remember { mutableIntStateOf(15) }
    var editingUser by remember { mutableStateOf<ManagedUser?>(null) }
    var banDialogTarget by remember { mutableStateOf<ManagedUser?>(null) }
    var deleteDialogTarget by remember { mutableStateOf<ManagedUser?>(null) }

    val filtered = remember(users, searchQuery, statusFilter) {
        users.filter { u ->
            val q = searchQuery.trim()
            val matchQ = q.isBlank() ||
                u.uid.contains(q, ignoreCase = true) ||
                u.displayName.contains(q, ignoreCase = true) ||
                u.email.contains(q, ignoreCase = true)
            val matchStatus = statusFilter == "ALL" || u.status == statusFilter
            matchQ && matchStatus
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = RoyalPurple)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("User & Account Control", style = MaterialTheme.typography.titleMedium)
                            Text("UID profiles, per-user JSON overrides & instant ban/unban", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(
                        onClick = {
                            editingUser = ManagedUser(
                                uid = "user_${users.size + 1}",
                                appId = apps.firstOrNull()?.id ?: "global",
                                displayName = "Managed User ${users.size + 1}"
                            )
                        },
                        modifier = Modifier.testTag("add_user_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search UID, display name or email") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "ACTIVE", "BANNED", "SUSPENDED").forEach { st ->
                        FilterChip(selected = statusFilter == st, onClick = { statusFilter = st }, label = { Text(st) })
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("No managed users matched", style = MaterialTheme.typography.titleSmall)
                    Text("Client SDK calls with PranavSdk.setUserId(...) automatically register user entries on the backend, or you can pre-create UIDs above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(filtered.take(visibleCount), key = { it.id.ifBlank { it.uid } }) { u ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(u.displayName.ifBlank { u.uid }, style = MaterialTheme.typography.titleMedium)
                            Text("UID: ${u.uid} • App: ${u.appId}", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                            if (u.email.isNotBlank()) Text(u.email, style = MaterialTheme.typography.bodySmall)
                        }
                        StatusPill(
                            label = u.status,
                            color = when (u.status) {
                                "ACTIVE" -> EmeraldSuccess
                                "SUSPENDED" -> AmberWarning
                                else -> CrimsonError
                            }
                        )
                    }
                    if (u.banReason.isNotBlank()) {
                        Text("Ban Reason: ${u.banReason}", style = MaterialTheme.typography.bodySmall, color = CrimsonError)
                    }
                    if (u.userConfigJson.isNotBlank() && u.userConfigJson != "{}") {
                        Text("Config Override: ${u.userConfigJson}", style = MaterialTheme.typography.labelSmall, color = RoyalPurple)
                    }
                    if (u.note.isNotBlank()) {
                        Text("Note: ${u.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { editingUser = u }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit user")
                        }
                        if (u.status == "BANNED") {
                            TextButton(onClick = { onToggleBan(u, false, "") }) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldSuccess)
                                Spacer(Modifier.width(4.dp))
                                Text("Unban", color = EmeraldSuccess)
                            }
                        } else {
                            TextButton(onClick = { banDialogTarget = u }) {
                                Icon(Icons.Filled.Block, contentDescription = null, tint = CrimsonError)
                                Spacer(Modifier.width(4.dp))
                                Text("Ban", color = CrimsonError)
                            }
                        }
                        IconButton(onClick = { deleteDialogTarget = u }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete user", tint = CrimsonError)
                        }
                    }
                }
            }
            if (filtered.size > visibleCount) {
                item {
                    OutlinedButton(onClick = { visibleCount += 15 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Load More Users (${filtered.size - visibleCount} remaining)")
                    }
                }
            }
        }
    }

    if (editingUser != null) {
        val u = editingUser!!
        var uid by remember(u) { mutableStateOf(u.uid) }
        var appId by remember(u) { mutableStateOf(u.appId) }
        var name by remember(u) { mutableStateOf(u.displayName) }
        var email by remember(u) { mutableStateOf(u.email) }
        var cfgJson by remember(u) { mutableStateOf(u.userConfigJson.ifBlank { "{}" }) }
        var note by remember(u) { mutableStateOf(u.note) }
        var jsonErr by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingUser = null },
            title = { Text(if (u.id.isBlank()) "Create Managed User" else "Edit ${u.uid}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uid, onValueChange = { uid = it }, label = { Text("User UID") }, enabled = u.id.isBlank(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text("App ID") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = cfgJson,
                        onValueChange = { cfgJson = it; jsonErr = null },
                        label = { Text("User Remote Config Override (JSON)") },
                        isError = jsonErr != null,
                        supportingText = jsonErr?.let { err -> { Text(err, color = CrimsonError) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Admin Note") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (!PranavJsonHelper.isValidJsonObject(cfgJson)) {
                        jsonErr = "Enter valid JSON (e.g. {\"vip_bonus\": true})"
                        return@Button
                    }
                    onSaveUser(u.copy(uid = uid.trim().ifBlank { "user" }, appId = appId.trim().ifBlank { "global" }, displayName = name.trim(), email = email.trim(), userConfigJson = cfgJson.trim().ifBlank { "{}" }, note = note.trim()))
                    editingUser = null
                }) { Text("Save User") }
            },
            dismissButton = { TextButton(onClick = { editingUser = null }) { Text("Cancel") } }
        )
    }

    if (banDialogTarget != null) {
        val target = banDialogTarget!!
        var reason by remember { mutableStateOf("Policy violation or chargeback") }
        AlertDialog(
            onDismissRequest = { banDialogTarget = null },
            title = { Text("Ban User ${target.displayName.ifBlank { target.uid }}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Banned users immediately receive HTTP 403 + a non-dismissible SECURITY_LOCK dialog on next SDK handshake.")
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Ban Reason") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = { onToggleBan(target, true, reason.trim()); banDialogTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError, contentColor = Color.White)
                ) { Text("Confirm Ban") }
            },
            dismissButton = { TextButton(onClick = { banDialogTarget = null }) { Text("Cancel") } }
        )
    }

    if (deleteDialogTarget != null) {
        val target = deleteDialogTarget!!
        AlertDialog(
            onDismissRequest = { deleteDialogTarget = null },
            title = { Text("Delete User ${target.uid}?") },
            text = { Text("Removes user profile and config overrides permanently.") },
            confirmButton = {
                Button(
                    onClick = { onDeleteUser(target); deleteDialogTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError, contentColor = Color.White)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteDialogTarget = null }) { Text("Cancel") } }
        )
    }
}
