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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.AdminSettingsState
import com.example.data.model.AdminModuleCatalog
import com.example.data.model.DynamicModuleDef
import com.example.data.model.PranavJsonHelper
import com.example.data.model.RemoteConfigRecord
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassCard
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.SectionHeader
import com.example.ui.theme.StatusPill

@Composable
fun RemoteConfigTab(
    ownerUid: String,
    configs: List<RemoteConfigRecord>,
    onSaveConfig: (RemoteConfigRecord) -> Unit,
    onRollbackConfig: (RemoteConfigRecord) -> Unit,
    onDeleteConfig: (RemoteConfigRecord) -> Unit
) {
    var editing by remember { mutableStateOf<RemoteConfigRecord?>(null) }
    var confirmDelete by remember { mutableStateOf<RemoteConfigRecord?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Remote Config & Feature Flags",
                subtitle = "Versioned JSON configs, targeting, scheduling & 1-tap rollback",
                trailing = {
                    Button(
                        onClick = {
                            editing = RemoteConfigRecord(
                                ownerUid = ownerUid,
                                appId = "global",
                                configKey = "main_flags",
                                payloadJson = "{\"welcome_banner\": \"PRANAV Ready\", \"max_retries\": 3}",
                                featureFlagsJson = "{\"new_checkout_ui\": true, \"vip_fast_lane\": true}"
                            )
                        },
                        modifier = Modifier.testTag("add_remote_config_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("New Config")
                    }
                }
            )
        }

        if (configs.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("No remote configs published", style = MaterialTheme.typography.titleMedium)
                    Text("Tap 'New Config' to publish live key-value values & boolean feature flags to host APKs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(configs, key = { it.id }) { cfg ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Tune, contentDescription = null, tint = ElectricBlue)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("${cfg.configKey} (v${cfg.version})", style = MaterialTheme.typography.titleMedium)
                                Text("App: ${cfg.appId} • ${cfg.status} • Priority ${cfg.priority}", style = MaterialTheme.typography.labelSmall, color = RoyalPurple)
                            }
                        }
                        Switch(checked = cfg.enabled, onCheckedChange = { onSaveConfig(cfg.copy(enabled = it, previousPayloadJson = cfg.payloadJson, previousFeatureFlagsJson = cfg.featureFlagsJson)) })
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Values: ${cfg.payloadJson}", style = MaterialTheme.typography.bodySmall)
                    Text("Flags: ${cfg.featureFlagsJson}", style = MaterialTheme.typography.bodySmall, color = EmeraldSuccess)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (cfg.previousPayloadJson.isNotBlank() || cfg.previousFeatureFlagsJson.isNotBlank()) {
                            TextButton(onClick = { onRollbackConfig(cfg) }) {
                                Icon(Icons.Filled.Restore, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Rollback")
                            }
                        }
                        TextButton(onClick = { editing = cfg }) { Text("Edit / New Ver") }
                        IconButton(onClick = { confirmDelete = cfg }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete remote config", tint = CrimsonError)
                        }
                    }
                }
            }
        }
    }

    if (editing != null) {
        val c = editing!!
        var appId by remember(c) { mutableStateOf(c.appId) }
        var key by remember(c) { mutableStateOf(c.configKey) }
        var status by remember(c) { mutableStateOf(c.status) }
        var payloadJson by remember(c) { mutableStateOf(c.payloadJson) }
        var flagsJson by remember(c) { mutableStateOf(c.featureFlagsJson) }
        var usersCsv by remember(c) { mutableStateOf(PranavJsonHelper.toCsv(c.targetUserIds)) }
        var versionsCsv by remember(c) { mutableStateOf(PranavJsonHelper.toCsv(c.targetVersions)) }
        var jsonError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (c.id.isBlank()) "Create Remote Config" else "Update ${c.configKey}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Config Key") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text("Target App ID") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("PUBLISHED", "DRAFT").forEach { st -> FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st) }) }
                    }
                    OutlinedTextField(value = payloadJson, onValueChange = { payloadJson = it; jsonError = null }, label = { Text("Values JSON") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(value = flagsJson, onValueChange = { flagsJson = it; jsonError = null }, label = { Text("Feature Flags JSON") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(value = usersCsv, onValueChange = { usersCsv = it }, label = { Text("Target User UIDs (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = versionsCsv, onValueChange = { versionsCsv = it }, label = { Text("Target Versions (optional)") }, modifier = Modifier.fillMaxWidth())
                    if (jsonError != null) Text(jsonError!!, style = MaterialTheme.typography.labelSmall, color = CrimsonError)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (!PranavJsonHelper.isValidJsonObject(payloadJson) || !PranavJsonHelper.isValidJsonObject(flagsJson)) {
                        jsonError = "Both Values and Feature Flags must be valid JSON objects."
                        return@Button
                    }
                    onSaveConfig(
                        c.copy(
                            appId = appId.trim().ifBlank { "global" },
                            configKey = key.trim().ifBlank { "config" },
                            status = status,
                            payloadJson = payloadJson.trim().ifBlank { "{}" },
                            featureFlagsJson = flagsJson.trim().ifBlank { "{}" },
                            targetUserIds = PranavJsonHelper.parseCsv(usersCsv),
                            targetVersions = PranavJsonHelper.parseCsv(versionsCsv),
                            previousPayloadJson = if (c.id.isNotBlank()) c.payloadJson else "",
                            previousFeatureFlagsJson = if (c.id.isNotBlank()) c.featureFlagsJson else ""
                        )
                    )
                    editing = null
                }) { Text("Save & Publish") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    if (confirmDelete != null) {
        val t = confirmDelete!!
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete Config '${t.configKey}'?") },
            text = { Text("Permanently removes this Remote Config from Firestore & backend.") },
            confirmButton = {
                Button(onClick = { onDeleteConfig(t); confirmDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = CrimsonError, contentColor = Color.White)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ModulesAndDynamicBuilderTab(
    ownerUid: String,
    settings: AdminSettingsState,
    dynamicModules: List<DynamicModuleDef>,
    onToggleCoreModule: (String, Boolean) -> Unit,
    onSaveDynamicModule: (DynamicModuleDef) -> Unit,
    onRollbackDynamicModule: (DynamicModuleDef) -> Unit,
    onDeleteDynamicModule: (DynamicModuleDef) -> Unit
) {
    var moduleFilter by remember { mutableStateOf("") }
    var editingDyn by remember { mutableStateOf<DynamicModuleDef?>(null) }
    var previewDyn by remember { mutableStateOf<DynamicModuleDef?>(null) }
    var confirmDeleteDyn by remember { mutableStateOf<DynamicModuleDef?>(null) }

    val filteredCore = remember(moduleFilter) {
        AdminModuleCatalog.allModules.filter {
            moduleFilter.isBlank() || it.title.contains(moduleFilter, true) || it.category.contains(moduleFilter, true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(
                title = "Dynamic Module Builder",
                subtitle = "Create runtime schema-driven modules delivered cleanly in DEX session",
                trailing = {
                    Button(
                        onClick = {
                            editingDyn = DynamicModuleDef(
                                ownerUid = ownerUid,
                                appId = "global",
                                moduleKey = "promo_banner_${dynamicModules.size + 1}",
                                moduleName = "Dynamic Promo ${dynamicModules.size + 1}",
                                category = "Dynamic",
                                fieldsSchemaJson = "{\"headline\":\"string\",\"ctaUrl\":\"url\"}",
                                defaultValuesJson = "{\"headline\":\"VIP Unlock\",\"discountPct\":25}",
                                actionParamsJson = "{\"action\":\"OPEN_URL\"}"
                            )
                        },
                        modifier = Modifier.testTag("add_dynamic_module_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Create Module")
                    }
                }
            )
        }

        items(dynamicModules, key = { "dyn_${it.id}" }) { mod ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = RoyalPurple)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("${mod.moduleName} (${mod.moduleKey})", style = MaterialTheme.typography.titleSmall)
                            Text("${mod.category} • v${mod.version} • App: ${mod.appId}", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                        }
                    }
                    Switch(checked = mod.enabled, onCheckedChange = { onSaveDynamicModule(mod.copy(enabled = it, previousDefaultValuesJson = mod.defaultValuesJson, previousActionParamsJson = mod.actionParamsJson)) })
                }
                Text("Defaults: ${mod.defaultValuesJson}", style = MaterialTheme.typography.bodySmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { previewDyn = mod }) { Text("Preview") }
                    Spacer(Modifier.width(6.dp))
                    if (mod.previousDefaultValuesJson.isNotBlank()) {
                        TextButton(onClick = { onRollbackDynamicModule(mod) }) { Text("Rollback") }
                    }
                    TextButton(onClick = { editingDyn = mod }) { Text("Edit") }
                    IconButton(onClick = { confirmDeleteDyn = mod }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete dynamic module", tint = CrimsonError)
                    }
                }
            }
        }

        item {
            SectionHeader(title = "PRANAV 52 Core Admin Modules", subtitle = "Independent ON/OFF feature toggles")
            OutlinedTextField(
                value = moduleFilter,
                onValueChange = { moduleFilter = it },
                label = { Text("Filter all ${AdminModuleCatalog.allModules.size} PRANAV Core modules") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        items(filteredCore, key = { "core_${it.key}" }) { mod ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${mod.title} [${mod.category}]", style = MaterialTheme.typography.titleSmall)
                        Text(mod.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.isModuleEnabled(mod.key), onCheckedChange = { onToggleCoreModule(mod.key, it) })
                }
            }
        }
    }

    if (editingDyn != null) {
        val m = editingDyn!!
        var key by remember(m) { mutableStateOf(m.moduleKey) }
        var name by remember(m) { mutableStateOf(m.moduleName) }
        var category by remember(m) { mutableStateOf(m.category) }
        var appId by remember(m) { mutableStateOf(m.appId) }
        var schemaJson by remember(m) { mutableStateOf(m.fieldsSchemaJson) }
        var defaultsJson by remember(m) { mutableStateOf(m.defaultValuesJson) }
        var actionJson by remember(m) { mutableStateOf(m.actionParamsJson) }
        var jsonErr by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingDyn = null },
            title = { Text(if (m.id.isBlank()) "Create Dynamic Module" else "Edit ${m.moduleKey}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Module Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Module Key") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text("App ID") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = schemaJson, onValueChange = { schemaJson = it; jsonErr = null }, label = { Text("Fields Schema JSON") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = defaultsJson, onValueChange = { defaultsJson = it; jsonErr = null }, label = { Text("Default Values JSON") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = actionJson, onValueChange = { actionJson = it; jsonErr = null }, label = { Text("Action Params JSON") }, modifier = Modifier.fillMaxWidth())
                    if (jsonErr != null) Text(jsonErr!!, style = MaterialTheme.typography.labelSmall, color = CrimsonError)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (!PranavJsonHelper.isValidJsonObject(schemaJson) || !PranavJsonHelper.isValidJsonObject(defaultsJson) || !PranavJsonHelper.isValidJsonObject(actionJson)) {
                        jsonErr = "Schema, Defaults & Action Params must be valid JSON objects."
                        return@Button
                    }
                    onSaveDynamicModule(
                        m.copy(
                            moduleKey = key.trim().ifBlank { "custom_module" },
                            moduleName = name.trim().ifBlank { "Custom Module" },
                            category = category.trim().ifBlank { "Dynamic" },
                            appId = appId.trim().ifBlank { "global" },
                            fieldsSchemaJson = schemaJson.trim().ifBlank { "{}" },
                            defaultValuesJson = defaultsJson.trim().ifBlank { "{}" },
                            actionParamsJson = actionJson.trim().ifBlank { "{}" },
                            previousDefaultValuesJson = if (m.id.isNotBlank()) m.defaultValuesJson else "",
                            previousActionParamsJson = if (m.id.isNotBlank()) m.actionParamsJson else ""
                        )
                    )
                    editingDyn = null
                }) { Text("Save & Publish") }
            },
            dismissButton = { TextButton(onClick = { editingDyn = null }) { Text("Cancel") } }
        )
    }

    if (previewDyn != null) {
        val m = previewDyn!!
        AlertDialog(
            onDismissRequest = { previewDyn = null },
            title = { Text("Module Payload Preview: ${m.moduleName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(label = "v${m.version} • ${m.status}", color = EmeraldSuccess)
                    Text("Schema: ${m.fieldsSchemaJson}", style = MaterialTheme.typography.bodySmall)
                    Text("Defaults: ${m.defaultValuesJson}", style = MaterialTheme.typography.bodySmall)
                    Text("Action Params: ${m.actionParamsJson}", style = MaterialTheme.typography.bodySmall, color = AmberWarning)
                }
            },
            confirmButton = { Button(onClick = { previewDyn = null }) { Text("Close") } }
        )
    }

    if (confirmDeleteDyn != null) {
        val t = confirmDeleteDyn!!
        AlertDialog(
            onDismissRequest = { confirmDeleteDyn = null },
            title = { Text("Delete Module '${t.moduleName}'?") },
            text = { Text("Removes dynamic module permanently from Firestore and backend.") },
            confirmButton = {
                Button(onClick = { onDeleteDynamicModule(t); confirmDeleteDyn = null }, colors = ButtonDefaults.buttonColors(containerColor = CrimsonError, contentColor = Color.White)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteDyn = null }) { Text("Cancel") } }
        )
    }
}
