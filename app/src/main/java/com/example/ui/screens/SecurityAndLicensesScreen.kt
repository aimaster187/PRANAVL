package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.LicenseRecord
import com.example.data.model.ManagedApp
import com.example.data.model.ManagedDevice
import com.example.data.model.ManagedUser
import com.example.data.model.UiState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassCard
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.StatusPill

@Composable
fun SecurityAndLicensesScreen(
    appsState: UiState<List<ManagedApp>>,
    devicesState: UiState<List<ManagedDevice>>,
    licensesState: UiState<List<LicenseRecord>>,
    usersState: UiState<List<ManagedUser>>,
    onRegisterDevice: (appId: String, label: String, apiLevel: Long, userUid: String) -> Unit,
    onUpdateDeviceApproval: (ManagedDevice, String, String) -> Unit,
    onDeleteDevice: (ManagedDevice) -> Unit,
    onGenerateLicense: (appId: String, tier: String, days: Long, graceDays: Long, paymentVerified: Boolean, cooldownHours: Long, deviceLimit: Long, userUid: String) -> Unit,
    onUpdateLicenseStatus: (LicenseRecord, String, Long, Boolean) -> Unit,
    onDeleteLicense: (LicenseRecord) -> Unit,
    onSaveUser: (ManagedUser) -> Unit,
    onToggleUserBan: (ManagedUser, Boolean, String) -> Unit,
    onDeleteUser: (ManagedUser) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val apps = (appsState as? UiState.Success)?.data.orEmpty()
    val devices = (devicesState as? UiState.Success)?.data.orEmpty()
    val licenses = (licensesState as? UiState.Success)?.data.orEmpty()
    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val context = LocalContext.current

    var confirmDeleteLicense by remember { mutableStateOf<LicenseRecord?>(null) }
    var confirmDeleteDevice by remember { mutableStateOf<ManagedDevice?>(null) }
    var restrictionDevice by remember { mutableStateOf<ManagedDevice?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Keys (${licenses.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Devices (${devices.size})") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Users (${users.size})") })
        }

        when (selectedTab) {
            0 -> {
                var selectedTier by remember { mutableStateOf("VIP_PRO") }
                var durationDaysText by remember { mutableStateOf("30") }
                var graceDaysText by remember { mutableStateOf("3") }
                var cooldownHoursText by remember { mutableStateOf("24") }
                var deviceLimitText by remember { mutableStateOf("1") }
                var userUidText by remember { mutableStateOf("") }
                var paymentVerified by remember { mutableStateOf(true) }
                var targetAppId by remember { mutableStateOf(apps.firstOrNull()?.id ?: "global") }
                var licQuery by remember { mutableStateOf("") }
                var licFilter by remember { mutableStateOf("ALL") }

                val filteredLics = remember(licenses, licQuery, licFilter) {
                    licenses.filter { l ->
                        val matchQ = licQuery.isBlank() || l.licenseKey.contains(licQuery, true) || l.boundDeviceId.contains(licQuery, true)
                        val matchS = licFilter == "ALL" || l.status == licFilter
                        matchQ && matchS
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
                                Icon(Icons.Filled.Key, contentDescription = null, tint = MetallicGold)
                                Spacer(Modifier.width(8.dp))
                                Text("License & VIP Generator", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("STANDARD", "VIP_PRO", "VIP_ELITE").forEach { tier ->
                                    FilterChip(selected = selectedTier == tier, onClick = { selectedTier = tier }, label = { Text(tier) })
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = targetAppId, onValueChange = { targetAppId = it }, label = { Text("App ID") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = userUidText, onValueChange = { userUidText = it }, label = { Text("User UID (opt)") }, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = durationDaysText, onValueChange = { durationDaysText = it }, label = { Text("Days") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = graceDaysText, onValueChange = { graceDaysText = it }, label = { Text("Grace") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = cooldownHoursText, onValueChange = { cooldownHoursText = it }, label = { Text("Cool h") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = deviceLimitText, onValueChange = { deviceLimitText = it }, label = { Text("Limit") }, modifier = Modifier.weight(0.8f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Payment Verified", style = MaterialTheme.typography.bodyMedium)
                                    Text("VIP keys require verified payment before activation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = paymentVerified, onCheckedChange = { paymentVerified = it })
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    onGenerateLicense(
                                        targetAppId.trim().ifBlank { "global" },
                                        selectedTier,
                                        durationDaysText.toLongOrNull()?.coerceIn(1L, 3650L) ?: 30L,
                                        graceDaysText.toLongOrNull()?.coerceIn(0L, 90L) ?: 3L,
                                        paymentVerified,
                                        cooldownHoursText.toLongOrNull()?.coerceIn(0L, 8760L) ?: 24L,
                                        deviceLimitText.toLongOrNull()?.coerceIn(1L, 50L) ?: 1L,
                                        userUidText
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag("generate_license_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Generate License Key")
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = licQuery,
                            onValueChange = { licQuery = it },
                            label = { Text("Filter keys by license code or device ID") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("ALL", "ACTIVE", "UNUSED", "SUSPENDED", "REVOKED").forEach { st ->
                                FilterChip(selected = licFilter == st, onClick = { licFilter = st }, label = { Text(st) })
                            }
                        }
                    }

                    items(filteredLics, key = { it.id }) { lic ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(lic.licenseKey, style = MaterialTheme.typography.titleSmall, color = ElectricBlue)
                                StatusPill(
                                    label = lic.status,
                                    color = when (lic.status) {
                                        "ACTIVE" -> EmeraldSuccess
                                        "UNUSED" -> RoyalPurple
                                        "SUSPENDED" -> AmberWarning
                                        else -> CrimsonError
                                    }
                                )
                            }
                            Text(
                                text = "${lic.planTier} • ${lic.durationDays}d (grace ${lic.graceDays}d) • Limit ${lic.deviceLimit} dev • Pay: ${if (lic.paymentVerified) "OK" else "NO"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (lic.boundDeviceId.isNotBlank() || lic.boundDeviceIds.isNotEmpty()) {
                                val devicesCsv = (lic.boundDeviceIds + lic.boundDeviceId).filter { it.isNotBlank() }.distinct().joinToString(", ")
                                Text("Bound Devices: $devicesCsv", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cb?.setPrimaryClip(ClipData.newPlainText("PRANAV License", lic.licenseKey))
                                }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy")
                                }
                                TextButton(onClick = { onUpdateLicenseStatus(lic, "ACTIVE", 0L, false) }) { Text("Active") }
                                TextButton(onClick = { onUpdateLicenseStatus(lic, "ACTIVE", 30L, false) }) { Text("+30d") }
                                TextButton(onClick = { onUpdateLicenseStatus(lic, "UNUSED", 0L, true) }) { Text("Unbind") }
                                TextButton(onClick = { onUpdateLicenseStatus(lic, "REVOKED", 0L, false) }) { Text("Revoke", color = AmberWarning) }
                                IconButton(onClick = { confirmDeleteLicense = lic }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete license", tint = CrimsonError)
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                var deviceLabel by remember { mutableStateOf("Pixel 9 Pro Test") }
                var deviceAppId by remember { mutableStateOf(apps.firstOrNull()?.id ?: "global") }
                var deviceUserUid by remember { mutableStateOf("") }
                var devQuery by remember { mutableStateOf("") }
                var devFilter by remember { mutableStateOf("ALL") }

                val filteredDevs = remember(devices, devQuery, devFilter) {
                    devices.filter { d ->
                        val q = devQuery.trim()
                        val matchQ = q.isBlank() || d.deviceLabel.contains(q, true) || d.installUuid.contains(q, true) || d.userUid.contains(q, true)
                        val matchS = devFilter == "ALL" || d.approvalStatus == devFilter
                        matchQ && matchS
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Register Device (UUID + Keystore Identity)", style = MaterialTheme.typography.titleMedium)
                            Text("Uses random installation UUID + Android Keystore EC P-256 fingerprint.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = deviceLabel, onValueChange = { deviceLabel = it }, label = { Text("Device Label") }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = deviceAppId, onValueChange = { deviceAppId = it }, label = { Text("App ID") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = deviceUserUid, onValueChange = { deviceUserUid = it }, label = { Text("User UID (opt)") }, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { onRegisterDevice(deviceAppId.trim().ifBlank { "global" }, deviceLabel.trim(), 35L, deviceUserUid.trim()) },
                                modifier = Modifier.fillMaxWidth().testTag("register_device_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Register Installation Identity")
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = devQuery,
                            onValueChange = { devQuery = it },
                            label = { Text("Search devices by label, UUID or UID") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("ALL", "APPROVED", "PENDING", "BLOCKED").forEach { st ->
                                FilterChip(selected = devFilter == st, onClick = { devFilter = st }, label = { Text(st) })
                            }
                        }
                    }

                    items(filteredDevs, key = { it.id }) { dev ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dev.deviceLabel, style = MaterialTheme.typography.titleMedium)
                                    Text("UUID: ${dev.installUuid}", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                                    Text("Keystore SHA: ${dev.keystoreKeyHash.take(24)}...", style = MaterialTheme.typography.labelSmall)
                                }
                                StatusPill(
                                    label = dev.approvalStatus,
                                    color = when (dev.approvalStatus) { "APPROVED" -> EmeraldSuccess; "PENDING" -> AmberWarning; else -> CrimsonError }
                                )
                            }
                            Text(
                                text = "App: ${dev.appId} • User: ${dev.userUid.ifBlank { "-" }} • API ${dev.androidApi} • Risk: ${dev.riskScore}/100",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (dev.restrictions.isNotBlank()) {
                                Text("Restriction: ${dev.restrictions}", style = MaterialTheme.typography.labelSmall, color = AmberWarning)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cb?.setPrimaryClip(ClipData.newPlainText("PRANAV Device ID", dev.installUuid))
                                }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy device ID") }
                                IconButton(onClick = { onUpdateDeviceApproval(dev, "APPROVED", "") }) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Approve device", tint = EmeraldSuccess)
                                }
                                IconButton(onClick = { onUpdateDeviceApproval(dev, "PENDING", dev.restrictions) }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Reset pending", tint = AmberWarning)
                                }
                                IconButton(onClick = { restrictionDevice = dev }) {
                                    Icon(Icons.Filled.Block, contentDescription = "Block device", tint = CrimsonError)
                                }
                                IconButton(onClick = { confirmDeleteDevice = dev }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete device", tint = CrimsonError)
                                }
                            }
                        }
                    }
                }
            }

            else -> UsersManagementTab(
                users = users,
                apps = apps,
                onSaveUser = onSaveUser,
                onToggleBan = onToggleUserBan,
                onDeleteUser = onDeleteUser
            )
        }
    }

    if (restrictionDevice != null) {
        val target = restrictionDevice!!
        var msg by remember { mutableStateOf(target.restrictions.ifBlank { "Device blocked by Root Owner" }) }
        AlertDialog(
            onDismissRequest = { restrictionDevice = null },
            title = { Text("Block ${target.deviceLabel}?") },
            text = {
                OutlinedTextField(value = msg, onValueChange = { msg = it }, label = { Text("Restriction Message") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(
                    onClick = { onUpdateDeviceApproval(target, "BLOCKED", msg.trim()); restrictionDevice = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError, contentColor = Color.White)
                ) { Text("Block Device") }
            },
            dismissButton = { TextButton(onClick = { restrictionDevice = null }) { Text("Cancel") } }
        )
    }

    if (confirmDeleteLicense != null) {
        val target = confirmDeleteLicense!!
        AlertDialog(
            onDismissRequest = { confirmDeleteLicense = null },
            title = { Text("Delete License ${target.licenseKey}?") },
            text = { Text("Removes license record permanently from Firestore & backend.") },
            confirmButton = {
                Button(
                    onClick = { onDeleteLicense(target); confirmDeleteLicense = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError, contentColor = Color.White)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteLicense = null }) { Text("Cancel") } }
        )
    }

    if (confirmDeleteDevice != null) {
        val target = confirmDeleteDevice!!
        AlertDialog(
            onDismissRequest = { confirmDeleteDevice = null },
            title = { Text("Delete Device ${target.deviceLabel}?") },
            text = { Text("Removes device record (${target.installUuid}) permanently.") },
            confirmButton = {
                Button(
                    onClick = { onDeleteDevice(target); confirmDeleteDevice = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError, contentColor = Color.White)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteDevice = null }) { Text("Cancel") } }
        )
    }
}
