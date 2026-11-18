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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material3.Switch
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
import com.example.data.model.AdminEvent
import com.example.data.model.AnnouncementRecord
import com.example.data.model.AuditLogEntry
import com.example.data.model.BackendAuditLogDto
import com.example.data.model.PranavJsonHelper
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassCard
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.SectionHeader
import com.example.ui.theme.StatusPill

@Composable
fun CommsAndAuditTab(
    ownerUid: String,
    announcements: List<AnnouncementRecord>,
    events: List<AdminEvent>,
    auditLogs: List<AuditLogEntry>,
    backendAuditLogs: List<BackendAuditLogDto>,
    onSaveAnnouncement: (AnnouncementRecord) -> Unit,
    onDeleteAnnouncement: (AnnouncementRecord) -> Unit,
    onSendCommsEvent: (appId: String, category: String, title: String, message: String, severity: String) -> Unit,
    onResolveEvent: (AdminEvent) -> Unit
) {
    var scope by remember { mutableStateOf("GLOBAL") }
    var displayType by remember { mutableStateOf("POPUP") }
    var annAppId by remember { mutableStateOf("global") }
    var targetUsersCsv by remember { mutableStateOf("") }
    var targetDevicesCsv by remember { mutableStateOf("") }
    var annTitle by remember { mutableStateOf("") }
    var annMessage by remember { mutableStateOf("") }
    var annActionText by remember { mutableStateOf("Open") }
    var annActionUrl by remember { mutableStateOf("") }
    var annDismissible by remember { mutableStateOf(true) }

    var ticketTitle by remember { mutableStateOf("") }
    var ticketBody by remember { mutableStateOf("") }
    var ticketCategory by remember { mutableStateOf("SUPPORT") }
    var auditQuery by remember { mutableStateOf("") }
    var auditVisibleCount by remember { mutableIntStateOf(15) }
    var confirmDeleteAnn by remember { mutableStateOf<AnnouncementRecord?>(null) }

    val filteredAudit = remember(auditLogs, auditQuery) {
        auditLogs.filter {
            auditQuery.isBlank() ||
                it.action.contains(auditQuery, true) ||
                it.summary.contains(auditQuery, true) ||
                it.targetType.contains(auditQuery, true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Campaign, contentDescription = null, tint = ElectricBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Targeted In-App Announcements", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text("Target Scope", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("GLOBAL", "APP", "USER", "DEVICE").forEach { s ->
                        FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s) })
                    }
                }
                Text("Render Type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("POPUP", "BANNER", "TOAST", "NOTIFICATION").forEach { dt ->
                        FilterChip(selected = displayType == dt, onClick = { displayType = dt }, label = { Text(dt) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = annAppId, onValueChange = { annAppId = it }, label = { Text("App ID (global or specific app)") }, modifier = Modifier.fillMaxWidth())
                if (scope == "USER" || scope == "APP") {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = targetUsersCsv, onValueChange = { targetUsersCsv = it }, label = { Text("Target User UIDs (comma separated)") }, modifier = Modifier.fillMaxWidth())
                }
                if (scope == "DEVICE") {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = targetDevicesCsv, onValueChange = { targetDevicesCsv = it }, label = { Text("Target Device UUIDs (comma separated)") }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = annTitle, onValueChange = { annTitle = it }, label = { Text("Announcement Title") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = annMessage, onValueChange = { annMessage = it }, label = { Text("Message Body") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = annActionText, onValueChange = { annActionText = it }, label = { Text("Button Text") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = annActionUrl, onValueChange = { annActionUrl = it }, label = { Text("Action URL (optional)") }, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Dismissible by User", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = annDismissible, onCheckedChange = { annDismissible = it })
                }
                Button(
                    onClick = {
                        if (annTitle.isNotBlank()) {
                            onSaveAnnouncement(
                                AnnouncementRecord(
                                    ownerUid = ownerUid,
                                    scope = scope,
                                    displayType = displayType,
                                    appId = annAppId.trim().ifBlank { "global" },
                                    targetUserIds = PranavJsonHelper.parseCsv(targetUsersCsv),
                                    targetDeviceIds = PranavJsonHelper.parseCsv(targetDevicesCsv),
                                    title = annTitle.trim(),
                                    message = annMessage.trim(),
                                    actionText = annActionText.trim().ifBlank { "OK" },
                                    actionUrl = annActionUrl.trim(),
                                    dismissible = annDismissible,
                                    enabled = true
                                )
                            )
                            annTitle = ""
                            annMessage = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("publish_announcement_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White)
                ) { Text("Publish Targeted $displayType") }
            }
        }

        items(announcements, key = { "ann_${it.id}" }) { ann ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ann.title, style = MaterialTheme.typography.titleSmall)
                        Text("${ann.scope} • ${ann.displayType} • App: ${ann.appId}", style = MaterialTheme.typography.labelSmall, color = RoyalPurple)
                    }
                    Switch(checked = ann.enabled, onCheckedChange = { onSaveAnnouncement(ann.copy(enabled = it)) })
                }
                Text(ann.message, style = MaterialTheme.typography.bodySmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { confirmDeleteAnn = ann }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete announcement", tint = CrimsonError)
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Support Tickets & System Notices", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("SUPPORT", "NOTIFICATION", "ANNOUNCEMENT").forEach { c ->
                        FilterChip(selected = ticketCategory == c, onClick = { ticketCategory = c }, label = { Text(c) })
                    }
                }
                OutlinedTextField(value = ticketTitle, onValueChange = { ticketTitle = it }, label = { Text("Ticket / Event Title") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = ticketBody, onValueChange = { ticketBody = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (ticketTitle.isNotBlank()) {
                            onSendCommsEvent("global", ticketCategory, ticketTitle.trim(), ticketBody.trim(), "INFO")
                            ticketTitle = ""
                            ticketBody = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White)
                ) { Text("Dispatch $ticketCategory") }
            }
        }

        items(events.take(12), key = { "evt_${it.id}" }) { evt ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(evt.title, style = MaterialTheme.typography.titleSmall)
                        Text(evt.message, style = MaterialTheme.typography.bodySmall)
                    }
                    StatusPill(label = evt.status, color = if (evt.status == "RESOLVED") EmeraldSuccess else AmberWarning)
                }
                if (evt.status == "OPEN") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { onResolveEvent(evt) }) { Text("Mark Resolved") }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Immutable Audit Logs", subtitle = "Zero-tamper owner history (${auditLogs.size} cloud • ${backendAuditLogs.size} VPS)")
            OutlinedTextField(
                value = auditQuery,
                onValueChange = { auditQuery = it },
                label = { Text("Filter audit logs by action or summary") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        items(filteredAudit.take(auditVisibleCount), key = { "al_${it.id}" }) { log ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = ElectricBlue)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${log.action} • ${log.targetType}", style = MaterialTheme.typography.titleSmall)
                        Text(log.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (filteredAudit.size > auditVisibleCount) {
            item {
                OutlinedButton(onClick = { auditVisibleCount += 15 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Load More Audit Logs (${filteredAudit.size - auditVisibleCount} remaining)")
                }
            }
        }
    }

    if (confirmDeleteAnn != null) {
        val target = confirmDeleteAnn!!
        AlertDialog(
            onDismissRequest = { confirmDeleteAnn = null },
            title = { Text("Delete '${target.title}'?") },
            text = { Text("Removes this targeted announcement from Firestore and backend delivery.") },
            confirmButton = {
                Button(
                    onClick = { onDeleteAnnouncement(target); confirmDeleteAnn = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError, contentColor = Color.White)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAnn = null }) { Text("Cancel") } }
        )
    }
}
