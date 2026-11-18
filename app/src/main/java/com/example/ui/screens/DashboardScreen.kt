package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AdminSettingsState
import com.example.data.model.AdminEvent
import com.example.data.model.AuditLogEntry
import com.example.data.model.DexRelease
import com.example.data.model.HealthResponse
import com.example.data.model.LicenseRecord
import com.example.data.model.ManagedApp
import com.example.data.model.ManagedDevice
import com.example.data.model.ManagedUser
import com.example.data.model.OwnerWorkspace
import com.example.data.model.TelegramBotConfig
import com.example.data.model.UiState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AnimatedSystemCoreOrb
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassCard
import com.example.ui.theme.HoloGradientButton
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.RosePink
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.SectionHeader
import com.example.ui.theme.StatusPill
import com.example.ui.theme.TelemetryLineChart

@Composable
fun DashboardScreen(
    ownerEmail: String,
    workspaceState: UiState<OwnerWorkspace?>,
    appsState: UiState<List<ManagedApp>>,
    usersState: UiState<List<ManagedUser>>,
    devicesState: UiState<List<ManagedDevice>>,
    licensesState: UiState<List<LicenseRecord>>,
    botsState: UiState<List<TelegramBotConfig>>,
    dexState: UiState<List<DexRelease>>,
    eventsState: UiState<List<AdminEvent>>,
    auditLogsState: UiState<List<AuditLogEntry>>,
    settings: AdminSettingsState,
    backendHealth: HealthResponse?,
    backendError: String?,
    isCheckingHealth: Boolean,
    isOffline: Boolean,
    onCheckBackendHealth: () -> Unit,
    onToggleMaintenance: (Boolean, String, Long, Long) -> Unit,
    onToggleLockdown: (Boolean) -> Unit,
    onBootstrapWorkspace: () -> Unit,
    onNavigateToRoute: (String) -> Unit
) {
    val workspace = (workspaceState as? UiState.Success)?.data
    val apps = (appsState as? UiState.Success)?.data.orEmpty()
    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val devices = (devicesState as? UiState.Success)?.data.orEmpty()
    val licenses = (licensesState as? UiState.Success)?.data.orEmpty()
    val bots = (botsState as? UiState.Success)?.data.orEmpty()
    val dexReleases = (dexState as? UiState.Success)?.data.orEmpty()
    val events = (eventsState as? UiState.Success)?.data.orEmpty()
    val auditLogs = (auditLogsState as? UiState.Success)?.data.orEmpty()

    var pendingLockdownToggle by remember { mutableStateOf<Boolean?>(null) }
    var maintMsgDraft by remember(workspace?.maintenanceMessage) {
        mutableStateOf(workspace?.maintenanceMessage ?: "Scheduled maintenance in progress.")
    }
    var maintWindowHours by remember { mutableStateOf(0L) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentGradient = listOf(RoyalPurple, ElectricBlue, LuminousCyan, MetallicGold)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(136.dp).clip(RoundedCornerShape(16.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_pranav_hero_core),
                        contentDescription = stringResource(id = R.string.cd_hero_core),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedSystemCoreOrb(
                        isHealthy = !(workspace?.emergencyLockdown ?: false) && !isOffline,
                        lowEndMode = settings.lowEndFallbackMode
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("PRANAV Core", style = MaterialTheme.typography.headlineMedium)
                            StatusPill(label = settings.environment.uppercase(), color = ElectricBlue)
                        }
                        Text(
                            text = "Root Owner: $ownerEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (dexReleases.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HoloGradientButton(
                        text = "Initialize PRANAV Core DEX v1.4.0",
                        icon = Icons.Filled.Bolt,
                        onClick = onBootstrapWorkspace,
                        modifier = Modifier.testTag("bootstrap_core_button")
                    )
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiMiniCard("Managed Apps", apps.size.toString(), "${apps.count { it.masterEnabled }} active", Icons.Filled.Apps, RoyalPurple, Modifier.weight(1f))
                KpiMiniCard("Managed Users", users.size.toString(), "${users.count { it.status == "BANNED" }} banned", Icons.Filled.Group, RosePink, Modifier.weight(1f))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiMiniCard("Devices", devices.size.toString(), "${devices.count { it.approvalStatus == "APPROVED" }} approved", Icons.Filled.Devices, ElectricBlue, Modifier.weight(1f))
                KpiMiniCard("Licenses & VIP", licenses.size.toString(), "${licenses.count { it.status == "ACTIVE" }} active", Icons.Filled.Key, MetallicGold, Modifier.weight(1f))
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(
                    title = "Ecosystem Telemetry",
                    subtitle = "Live distribution across apps, users, devices, keys, bots & audit logs",
                    trailing = {
                        StatusPill(
                            label = if (isOffline) "OFFLINE CACHE" else "FIRESTORE SYNCED",
                            color = if (isOffline) AmberWarning else EmeraldSuccess
                        )
                    }
                )
                Spacer(Modifier.height(12.dp))
                TelemetryLineChart(
                    values = listOf(
                        apps.size.toFloat().coerceAtLeast(0.2f),
                        users.size.toFloat().coerceAtLeast(0.2f),
                        devices.size.toFloat().coerceAtLeast(0.2f),
                        licenses.size.toFloat().coerceAtLeast(0.2f),
                        bots.size.toFloat().coerceAtLeast(0.2f),
                        auditLogs.size.toFloat().coerceAtLeast(0.2f)
                    ),
                    labels = listOf("Apps", "Users", "Devices", "Keys", "Bots", "Audit")
                )
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), accentGradient = listOf(ElectricBlue, LuminousCyan)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (backendHealth != null) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = if (backendHealth != null) EmeraldSuccess else AmberWarning
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Node.js Backend & VPS Health", style = MaterialTheme.typography.titleMedium)
                            Text(settings.backendBaseUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onCheckBackendHealth, enabled = !isCheckingHealth, modifier = Modifier.testTag("ping_backend_button")) {
                        if (isCheckingHealth) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, contentDescription = "Ping backend")
                    }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    backendHealth != null -> Text(
                        text = "Status: ${backendHealth.status.uppercase()} • RAM ${backendHealth.memoryUsedMb}/${backendHealth.memoryTotalMb} MB • Workers: ${backendHealth.workerCount} • Load: ${backendHealth.loadAvg1m}",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldSuccess
                    )
                    backendError != null -> Text(
                        text = "Endpoint check: $backendError (configure live VPS URL in Bots & Ops)",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarning
                    )
                    else -> Text(
                        text = "Tap refresh to run live /api/v1/health verification and pull update/audit history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), accentGradient = listOf(RosePink, CrimsonError, MetallicGold)) {
                SectionHeader(title = "Emergency & Maintenance Controls", subtitle = "Global circuit breakers & scheduled maintenance window")
                Spacer(Modifier.height(10.dp))
                val maintenanceOn = workspace?.masterMaintenance ?: false
                OutlinedTextField(
                    value = maintMsgDraft,
                    onValueChange = { maintMsgDraft = it },
                    label = { Text("Maintenance Banner / Dialog Message") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0L to "Indefinite", 1L to "1h", 6L to "6h", 24L to "24h").forEach { (hrs, lbl) ->
                        FilterChip(selected = maintWindowHours == hrs, onClick = { maintWindowHours = hrs }, label = { Text(lbl) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Global Maintenance Mode", style = MaterialTheme.typography.titleSmall)
                        Text("Blocks non-owner traffic across all managed apps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = maintenanceOn,
                        onCheckedChange = { enabled ->
                            val now = System.currentTimeMillis()
                            val exp = if (enabled && maintWindowHours > 0L) now + maintWindowHours * 3_600_000L else 0L
                            onToggleMaintenance(enabled, maintMsgDraft.trim().ifBlank { "Scheduled maintenance in progress." }, if (enabled) now else 0L, exp)
                        },
                        modifier = Modifier.testTag("global_maintenance_switch")
                    )
                }
                Spacer(Modifier.height(8.dp))
                val lockdownOn = workspace?.emergencyLockdown ?: false
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Emergency Kill-Switch", style = MaterialTheme.typography.titleSmall, color = if (lockdownOn) CrimsonError else MaterialTheme.colorScheme.onSurface)
                        Text("Suspends license issuance & forces security lock dialog", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = lockdownOn,
                        onCheckedChange = { pendingLockdownToggle = it },
                        modifier = Modifier.testTag("emergency_lockdown_switch")
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onNavigateToRoute(ROUTE_INTEGRATION) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Inspect APK")
                    }
                    Button(
                        onClick = { onNavigateToRoute(ROUTE_APPS) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Manage Apps")
                    }
                }
            }
        }

        if (auditLogs.isNotEmpty() || events.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Immutable Audit & Ops Feed",
                    subtitle = "Latest verified owner actions (${auditLogs.size}) & telemetry events (${events.size})",
                    trailing = {
                        TextButton(onClick = { onNavigateToRoute(ROUTE_BOTS) }) {
                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("All Logs")
                        }
                    }
                )
            }
            items(auditLogs.take(4), key = { "audit_${it.id}" }) { log ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${log.action} • ${log.targetType}", style = MaterialTheme.typography.titleSmall, color = ElectricBlue)
                            Text(log.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(label = log.targetType, color = RoyalPurple)
                    }
                }
            }
            items(events.sortedByDescending { it.createdAt?.seconds ?: 0L }.take(3), key = { "evt_${it.id}" }) { evt ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (evt.severity == "CRITICAL" || evt.severity == "WARNING") Icons.Filled.Warning else Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = when (evt.severity) { "CRITICAL" -> CrimsonError; "WARNING" -> AmberWarning; else -> ElectricBlue },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(evt.title, style = MaterialTheme.typography.titleSmall)
                                Text(evt.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        StatusPill(label = evt.category, color = MetallicGold)
                    }
                }
            }
        }
    }

    if (pendingLockdownToggle != null) {
        val next = pendingLockdownToggle!!
        AlertDialog(
            onDismissRequest = { pendingLockdownToggle = null },
            title = { Text(if (next) "Activate Emergency Kill-Switch?" else "Disable Emergency Kill-Switch?") },
            text = {
                Text(
                    if (next) "All client SDK handshakes will immediately receive a non-dismissible SECURITY_LOCK dialog and license activation will pause."
                    else "Normal client verification and license checks will resume across all managed applications."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleLockdown(next)
                        pendingLockdownToggle = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (next) CrimsonError else EmeraldSuccess, contentColor = Color.White)
                ) { Text(if (next) "Activate Kill-Switch" else "Resume Operations") }
            },
            dismissButton = { TextButton(onClick = { pendingLockdownToggle = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun KpiMiniCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        accentGradient = listOf(accent.copy(alpha = 0.45f), LuminousCyan.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = accent)
    }
}
