package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AdminSettingsState
import com.example.data.local.CachedSnapshotEntity
import com.example.data.local.PranavCacheDao
import com.example.data.local.PranavPreferences
import com.example.data.model.AdminEvent
import com.example.data.model.AnnouncementRecord
import com.example.data.model.ApkInspectionReport
import com.example.data.model.AuditLogEntry
import com.example.data.model.BackendAuditLogDto
import com.example.data.model.BotActionRequest
import com.example.data.model.DexRelease
import com.example.data.model.DynamicModuleDef
import com.example.data.model.HealthResponse
import com.example.data.model.LicenseRecord
import com.example.data.model.ManagedApp
import com.example.data.model.ManagedDevice
import com.example.data.model.ManagedUser
import com.example.data.model.OwnerWorkspace
import com.example.data.model.PranavJsonHelper
import com.example.data.model.RegisterBotRequest
import com.example.data.model.RemoteConfigRecord
import com.example.data.model.SyncEventRequest
import com.example.data.model.SyncWorkspaceRequest
import com.example.data.model.TelegramBotConfig
import com.example.data.model.UiState
import com.example.data.model.UniversalDialog
import com.example.data.model.UpdateConfirmationDto
import com.example.data.remote.ApkInspectorEngine
import com.example.data.remote.FirestoreErrorHandler
import com.example.data.remote.PranavBackendClient
import com.example.data.remote.PranavFirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.UUID

class PranavAdminViewModel(
    private val repo: PranavFirestoreRepository,
    private val prefs: PranavPreferences,
    private val cacheDao: PranavCacheDao,
    private val apkInspector: ApkInspectorEngine,
    private val ownerUid: String,
    private val ownerEmail: String
) : ViewModel() {

    companion object {
        const val LATEST_DEX_SHA256 = "7dce1cff974efb7dd8f19be3e1e6b1581bbfd55a7d161426a0cb42dbc83bd15a"
    }

    private val sync = PranavSyncHelper(repo, prefs, ownerUid, ownerEmail)
    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val toastMessages = _toastMessages.asSharedFlow()

    val settingsState: StateFlow<AdminSettingsState> = prefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminSettingsState())

    val workspaceState: StateFlow<UiState<OwnerWorkspace?>> = repo.observeWorkspace(ownerUid)
        .map { it.fold(onSuccess = { v -> UiState.Success(v) }, onFailure = { e -> UiState.Error(FirestoreErrorHandler.toUserMessage(e, "loading workspace")) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val appsState: StateFlow<UiState<List<ManagedApp>>> = repo.observeApps(ownerUid).toUiState("apps") { cacheSummary("apps_count", it.size.toString()) }
    val dialogsState: StateFlow<UiState<List<UniversalDialog>>> = repo.observeDialogs(ownerUid).toUiState("dialogs")
    val devicesState: StateFlow<UiState<List<ManagedDevice>>> = repo.observeDevices(ownerUid).toUiState("devices")
    val licensesState: StateFlow<UiState<List<LicenseRecord>>> = repo.observeLicenses(ownerUid).toUiState("licenses")
    val usersState: StateFlow<UiState<List<ManagedUser>>> = repo.observeManagedUsers(ownerUid).toUiState("users")
    val remoteConfigsState: StateFlow<UiState<List<RemoteConfigRecord>>> = repo.observeRemoteConfigs(ownerUid).toUiState("remote configs")
    val dynamicModulesState: StateFlow<UiState<List<DynamicModuleDef>>> = repo.observeDynamicModules(ownerUid).toUiState("dynamic modules")
    val announcementsState: StateFlow<UiState<List<AnnouncementRecord>>> = repo.observeAnnouncements(ownerUid).toUiState("announcements")
    val botsState: StateFlow<UiState<List<TelegramBotConfig>>> = repo.observeBots(ownerUid).toUiState("bots")
    val dexReleasesState: StateFlow<UiState<List<DexRelease>>> = repo.observeDexReleases(ownerUid).toUiState("DEX releases")
    val eventsState: StateFlow<UiState<List<AdminEvent>>> = repo.observeEvents(ownerUid).toUiState("events")
    val auditLogsState: StateFlow<UiState<List<AuditLogEntry>>> = repo.observeAuditLogs(ownerUid).toUiState("audit logs")

    private val _backendHealth = MutableStateFlow<HealthResponse?>(null)
    val backendHealth: StateFlow<HealthResponse?> = _backendHealth.asStateFlow()
    private val _backendHealthError = MutableStateFlow<String?>(null)
    val backendHealthError: StateFlow<String?> = _backendHealthError.asStateFlow()
    private val _isCheckingHealth = MutableStateFlow(false)
    val isCheckingHealth: StateFlow<Boolean> = _isCheckingHealth.asStateFlow()

    private val _updateHistory = MutableStateFlow<List<UpdateConfirmationDto>>(emptyList())
    val updateHistory: StateFlow<List<UpdateConfirmationDto>> = _updateHistory.asStateFlow()
    private val _backendAuditLogs = MutableStateFlow<List<BackendAuditLogDto>>(emptyList())
    val backendAuditLogs: StateFlow<List<BackendAuditLogDto>> = _backendAuditLogs.asStateFlow()

    private val _apkReport = MutableStateFlow<ApkInspectionReport?>(null)
    val apkReport: StateFlow<ApkInspectionReport?> = _apkReport.asStateFlow()
    private val _isInspectingApk = MutableStateFlow(false)
    val isInspectingApk: StateFlow<Boolean> = _isInspectingApk.asStateFlow()
    private val _apkInspectError = MutableStateFlow<String?>(null)
    val apkInspectError: StateFlow<String?> = _apkInspectError.asStateFlow()

    private fun <T> Flow<Result<List<T>>>.toUiState(label: String, onEachSuccess: (List<T>) -> Unit = {}): StateFlow<UiState<List<T>>> =
        map { res ->
            res.fold(
                onSuccess = { onEachSuccess(it); UiState.Success(it) },
                onFailure = { UiState.Error(FirestoreErrorHandler.toUserMessage(it, "loading $label")) }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    private fun cacheSummary(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try { cacheDao.upsert(CachedSnapshotEntity(key, value, System.currentTimeMillis())) } catch (_: Exception) {}
        }
    }

    fun checkBackendHealth() {
        viewModelScope.launch {
            _isCheckingHealth.value = true
            _backendHealthError.value = null
            try {
                val cfg = prefs.settingsFlow.first()
                val svc = PranavBackendClient.create(cfg.backendBaseUrl)
                val resp = withContext(Dispatchers.IO) { svc.getHealth() }
                _backendHealth.value = resp
                if (cfg.ownerApiSecret.isNotBlank()) {
                    val bearer = "Bearer ${cfg.ownerApiSecret}"
                    withContext(Dispatchers.IO) {
                        runCatching { svc.getUpdateHistory(bearer).updates }.onSuccess { _updateHistory.value = it }
                        runCatching { svc.getAuditLogs(bearer).logs }.onSuccess { _backendAuditLogs.value = it }
                    }
                }
                _toastMessages.tryEmit("Backend online (${resp.environment})")
            } catch (e: Exception) {
                _backendHealth.value = null
                _backendHealthError.value = e.localizedMessage ?: "Unable to reach backend"
            } finally {
                _isCheckingHealth.value = false
            }
        }
    }

    fun bootstrapDefaultWorkspaceIfNeeded() {
        viewModelScope.launch {
            try {
                val currentWs = (workspaceState.value as? UiState.Success)?.data
                if (currentWs == null) {
                    repo.upsertWorkspace(ownerUid, OwnerWorkspace(ownerUid = ownerUid, ownerEmail = ownerEmail, domainHost = "pranavsmm.shop", environment = prefs.settingsFlow.first().environment))
                }
                repo.saveDexRelease(
                    ownerUid,
                    DexRelease(
                        ownerUid = ownerUid, versionName = "1.4.0", versionCode = 140L, minSdk = 24L, targetSdk = 36L,
                        sha256Checksum = LATEST_DEX_SHA256, recommendedHook = "APPLICATION_ON_CREATE",
                        releaseNotes = "PRANAV Core DEX v1.4.0 with ActivityLifecycleCallbacks, Remote Config cache, Update Downloader, Announcements & License runtime.",
                        active = true
                    )
                )
                repo.recordEvent(ownerUid, AdminEvent(ownerUid = ownerUid, category = "CORE", title = "PRANAV Core Initialized", message = "Registered workspace & PRANAV DEX v1.4.0.", severity = "INFO", status = "RESOLVED"))
                sync.logAudit("BOOTSTRAP_CORE", "WORKSPACE", "primary", "Initialized workspace & DEX v1.4.0")
                _toastMessages.tryEmit("PRANAV Core DEX v1.4.0 registered")
            } catch (e: Exception) {
                _toastMessages.tryEmit(FirestoreErrorHandler.toUserMessage(e, "initializing workspace"))
            }
        }
    }

    fun toggleMasterMaintenance(enabled: Boolean, message: String, startsAtMs: Long = 0L, expiresAtMs: Long = 0L) {
        viewModelScope.launch {
            try {
                val cur = (workspaceState.value as? UiState.Success)?.data ?: OwnerWorkspace(ownerUid = ownerUid, ownerEmail = ownerEmail)
                repo.upsertWorkspace(ownerUid, cur.copy(masterMaintenance = enabled, maintenanceMessage = message, maintenanceStartsAtMs = startsAtMs, maintenanceExpiresAtMs = expiresAtMs))
                sync.logAudit(if (enabled) "ENABLE_MAINTENANCE" else "DISABLE_MAINTENANCE", "WORKSPACE", "primary", message)
                val note = sync.trySync("workspace") { svc, b -> svc.syncWorkspace(b, SyncWorkspaceRequest(enabled, message, startsAtMs, expiresAtMs, cur.emergencyLockdown)) }
                _toastMessages.tryEmit(note ?: if (enabled) "Maintenance mode synced (ON)" else "Maintenance mode synced (OFF)")
            } catch (e: Exception) {
                _toastMessages.tryEmit(FirestoreErrorHandler.toUserMessage(e, "toggling maintenance"))
            }
        }
    }

    fun toggleEmergencyLockdown(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val cur = (workspaceState.value as? UiState.Success)?.data ?: OwnerWorkspace(ownerUid = ownerUid, ownerEmail = ownerEmail)
                repo.upsertWorkspace(ownerUid, cur.copy(emergencyLockdown = enabled))
                sync.logAudit(if (enabled) "ENABLE_LOCKDOWN" else "DISABLE_LOCKDOWN", "WORKSPACE", "primary", "Emergency Kill-Switch=$enabled")
                val note = sync.trySync("lockdown") { svc, b -> svc.syncWorkspace(b, SyncWorkspaceRequest(cur.masterMaintenance, cur.maintenanceMessage, cur.maintenanceStartsAtMs, cur.maintenanceExpiresAtMs, enabled)) }
                _toastMessages.tryEmit(note ?: if (enabled) "Emergency lockdown synced" else "Lockdown cleared on backend")
            } catch (e: Exception) {
                _toastMessages.tryEmit(FirestoreErrorHandler.toUserMessage(e, "toggling lockdown"))
            }
        }
    }

    fun saveApp(app: ManagedApp) = launchGuarded("saving app") {
        val savedId = repo.saveApp(ownerUid, app)
        val finalApp = app.copy(id = savedId)
        sync.logAudit(if (app.id.isBlank()) "CREATE_APP" else "UPDATE_APP", "APP", savedId, "${app.displayName} v${app.latestVersion} force=${app.forceUpdateEnabled}")
        val note = sync.trySync("app") { svc, b -> svc.syncApp(b, sync.toSyncApp(finalApp)) }
        _toastMessages.tryEmit(note ?: "Saved & synced ${app.displayName}")
    }

    fun deleteApp(app: ManagedApp) {
        if (app.id.isBlank()) return
        launchGuarded("deleting app") {
            repo.deleteApp(ownerUid, app.id)
            sync.logAudit("DELETE_APP", "APP", app.id, "Deleted ${app.displayName} (${app.packageName})")
            val note = sync.trySync("deleteApp") { svc, b -> svc.deleteApp(b, app.id) }
            _toastMessages.tryEmit(note ?: "Deleted ${app.displayName}")
        }
    }

    fun saveDialog(dialog: UniversalDialog) = launchGuarded("saving dialog") {
        val savedId = repo.saveDialog(ownerUid, dialog)
        sync.logAudit(if (dialog.id.isBlank()) "CREATE_DIALOG" else "UPDATE_DIALOG", "DIALOG", savedId, "${dialog.dialogType}: ${dialog.title}")
        val note = sync.trySync("dialog") { svc, b -> svc.syncDialog(b, sync.toSyncDialog(savedId, dialog)) }
        _toastMessages.tryEmit(note ?: "Saved & synced dialog '${dialog.title}'")
    }

    fun deleteDialog(dialog: UniversalDialog) {
        if (dialog.id.isBlank()) return
        launchGuarded("deleting dialog") {
            repo.deleteDialog(ownerUid, dialog.id)
            sync.logAudit("DELETE_DIALOG", "DIALOG", dialog.id, "Deleted dialog '${dialog.title}'")
            val note = sync.trySync("deleteDialog") { svc, b -> svc.deleteDialog(b, dialog.id) }
            _toastMessages.tryEmit(note ?: "Deleted dialog '${dialog.title}'")
        }
    }

    fun inspectAuthorizedApk(uri: Uri) {
        viewModelScope.launch {
            _isInspectingApk.value = true
            _apkInspectError.value = null
            try {
                val report = withContext(Dispatchers.IO) { apkInspector.inspectUri(uri) }
                _apkReport.value = report
                _toastMessages.tryEmit("Inspected ${report.packageName} (${report.dexFileCount} DEX)")
            } catch (e: Exception) {
                _apkInspectError.value = e.localizedMessage ?: "APK inspection failed"
            } finally {
                _isInspectingApk.value = false
            }
        }
    }

    fun saveDexRelease(release: DexRelease) = launchGuarded("saving DEX release") {
        val id = repo.saveDexRelease(ownerUid, release)
        sync.logAudit("PUBLISH_DEX", "DEX", id, "v${release.versionName} (${release.sha256Checksum.take(12)}...)")
        _toastMessages.tryEmit("Published DEX v${release.versionName}")
    }

    fun registerDeviceRecord(appId: String, label: String, apiLevel: Long, userUid: String = "") = launchGuarded("registering device") {
        val uuid = UUID.randomUUID().toString()
        val ksHash = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        val dev = ManagedDevice(ownerUid = ownerUid, appId = appId, userUid = userUid, deviceLabel = label.ifBlank { "Android Device" }, installUuid = uuid, keystoreKeyHash = ksHash, approvalStatus = "PENDING", androidApi = apiLevel)
        val savedId = repo.saveDevice(ownerUid, dev)
        sync.logAudit("REGISTER_DEVICE", "DEVICE", savedId, dev.deviceLabel)
        val note = sync.trySync("device") { svc, b -> svc.syncDevice(b, sync.toSyncDevice(savedId, dev)) }
        _toastMessages.tryEmit(note ?: "Registered & synced installation identity")
    }

    fun updateDeviceApproval(device: ManagedDevice, status: String, restrictions: String = device.restrictions) = launchGuarded("updating device") {
        val updated = device.copy(approvalStatus = status, restrictions = restrictions)
        val savedId = repo.saveDevice(ownerUid, updated)
        sync.logAudit("UPDATE_DEVICE", "DEVICE", savedId, "${device.deviceLabel} -> $status")
        val note = sync.trySync("deviceApproval") { svc, b -> svc.syncDevice(b, sync.toSyncDevice(savedId, updated)) }
        _toastMessages.tryEmit(note ?: "Device ${device.deviceLabel} -> $status (synced)")
    }

    fun deleteDevice(device: ManagedDevice) {
        if (device.id.isBlank()) return
        launchGuarded("deleting device") {
            repo.deleteDevice(ownerUid, device.id)
            sync.logAudit("DELETE_DEVICE", "DEVICE", device.id, "Removed ${device.deviceLabel}")
            val note = sync.trySync("deleteDevice") { svc, b -> svc.deleteDevice(b, device.id) }
            _toastMessages.tryEmit(note ?: "Removed device ${device.deviceLabel}")
        }
    }

    fun generateLicense(appId: String, tier: String, days: Long, graceDays: Long, paymentVerified: Boolean, cooldownHours: Long, deviceLimit: Long = 1L, userUid: String = "") = launchGuarded("generating license") {
        val key = buildLicenseKey(tier)
        val lic = LicenseRecord(
            ownerUid = ownerUid, appId = appId, userUid = userUid.trim(), licenseKey = key, planTier = tier,
            status = if (paymentVerified || tier == "STANDARD") "UNUSED" else "SUSPENDED",
            durationDays = days, graceDays = graceDays, deviceLimit = deviceLimit.coerceAtLeast(1L),
            paymentVerified = paymentVerified, transferCooldownHours = cooldownHours,
            expiresAtEpochMs = System.currentTimeMillis() + (days * 86_400_000L)
        )
        val savedId = repo.saveLicense(ownerUid, lic)
        sync.logAudit("ISSUE_LICENSE", "LICENSE", savedId, "$key ($tier, limit=$deviceLimit)")
        val note = sync.trySync("license") { svc, b -> svc.syncLicense(b, sync.toSyncLicense(savedId, lic)) }
        _toastMessages.tryEmit(note ?: "Issued & synced key $key")
    }

    fun updateLicenseStatus(license: LicenseRecord, newStatus: String, extraDays: Long, clearBoundDevice: Boolean = false) = launchGuarded("updating license") {
        if (newStatus == "ACTIVE" && license.planTier.startsWith("VIP") && !license.paymentVerified) {
            _toastMessages.tryEmit("VIP license requires verified payment before activation.")
            return@launchGuarded
        }
        val baseExpiry = maxOf(license.expiresAtEpochMs, System.currentTimeMillis())
        val updated = license.copy(
            status = newStatus,
            durationDays = license.durationDays + extraDays,
            boundDeviceId = if (clearBoundDevice) "" else license.boundDeviceId,
            boundDeviceIds = if (clearBoundDevice) emptyList() else license.boundDeviceIds,
            expiresAtEpochMs = if (extraDays > 0L) baseExpiry + extraDays * 86_400_000L else license.expiresAtEpochMs
        )
        val savedId = repo.saveLicense(ownerUid, updated)
        sync.logAudit("UPDATE_LICENSE", "LICENSE", savedId, "${license.licenseKey} -> $newStatus${if (clearBoundDevice) " (unbound)" else ""}")
        val note = sync.trySync("licenseStatus") { svc, b -> svc.syncLicense(b, sync.toSyncLicense(savedId, updated)) }
        _toastMessages.tryEmit(note ?: "License ${license.licenseKey} -> $newStatus (synced)")
    }

    fun deleteLicense(license: LicenseRecord) {
        if (license.id.isBlank()) return
        launchGuarded("deleting license") {
            repo.deleteLicense(ownerUid, license.id)
            sync.logAudit("DELETE_LICENSE", "LICENSE", license.id, "Deleted key ${license.licenseKey}")
            val note = sync.trySync("deleteLicense") { svc, b -> svc.deleteLicense(b, license.id) }
            _toastMessages.tryEmit(note ?: "Deleted license ${license.licenseKey}")
        }
    }

    fun saveManagedUser(user: ManagedUser) = launchGuarded("saving user") {
        if (!PranavJsonHelper.isValidJsonObject(user.userConfigJson)) {
            _toastMessages.tryEmit("Invalid JSON in userConfig")
            return@launchGuarded
        }
        val savedId = repo.saveManagedUser(ownerUid, user)
        sync.logAudit(if (user.id.isBlank()) "CREATE_USER" else "UPDATE_USER", "USER", savedId, "${user.displayName} (${user.status})")
        val note = sync.trySync("user") { svc, b -> svc.syncUser(b, sync.toSyncUser(savedId, user)) }
        _toastMessages.tryEmit(note ?: "Saved user ${user.displayName}")
    }

    fun toggleUserBan(user: ManagedUser, banned: Boolean, reason: String) = launchGuarded("updating user ban") {
        val updated = user.copy(status = if (banned) "BANNED" else "ACTIVE", banReason = if (banned) reason.ifBlank { "Restricted by Root Owner" } else "")
        val savedId = repo.saveManagedUser(ownerUid, updated)
        sync.logAudit(if (banned) "BAN_USER" else "UNBAN_USER", "USER", savedId, "${updated.uid}: ${updated.banReason}")
        val note = sync.trySync("banUser") { svc, b -> svc.banUser(b, updated.uid.ifBlank { savedId }, sync.buildBanRequest(banned, updated.banReason)) }
        _toastMessages.tryEmit(note ?: if (banned) "Banned ${updated.displayName}" else "Unbanned ${updated.displayName}")
    }

    fun deleteManagedUser(user: ManagedUser) {
        if (user.id.isBlank()) return
        launchGuarded("deleting user") {
            repo.deleteManagedUser(ownerUid, user.id)
            sync.logAudit("DELETE_USER", "USER", user.id, "Deleted user ${user.uid}")
            val note = sync.trySync("deleteUser") { svc, b -> svc.deleteUser(b, user.uid.ifBlank { user.id }) }
            _toastMessages.tryEmit(note ?: "Removed user ${user.displayName}")
        }
    }

    fun saveAnnouncement(ann: AnnouncementRecord) = launchGuarded("saving announcement") {
        val savedId = repo.saveAnnouncement(ownerUid, ann)
        sync.logAudit("PUBLISH_ANNOUNCEMENT", "ANNOUNCEMENT", savedId, "[${ann.scope}/${ann.displayType}] ${ann.title}")
        val note = sync.trySync("announcement") { svc, b -> svc.syncAnnouncement(b, sync.toSyncAnnouncement(savedId, ann)) }
        _toastMessages.tryEmit(note ?: "Published announcement '${ann.title}'")
    }

    fun deleteAnnouncement(ann: AnnouncementRecord) {
        if (ann.id.isBlank()) return
        launchGuarded("deleting announcement") {
            repo.deleteAnnouncement(ownerUid, ann.id)
            sync.logAudit("DELETE_ANNOUNCEMENT", "ANNOUNCEMENT", ann.id, "Deleted '${ann.title}'")
            val note = sync.trySync("deleteAnnouncement") { svc, b -> svc.deleteAnnouncement(b, ann.id) }
            _toastMessages.tryEmit(note ?: "Deleted announcement '${ann.title}'")
        }
    }

    fun saveRemoteConfig(cfg: RemoteConfigRecord) = launchGuarded("saving remote config") {
        if (!PranavJsonHelper.isValidJsonObject(cfg.payloadJson) || !PranavJsonHelper.isValidJsonObject(cfg.featureFlagsJson)) {
            _toastMessages.tryEmit("Remote Config JSON and Feature Flags JSON must be valid JSON objects.")
            return@launchGuarded
        }
        val nextVersion = if (cfg.id.isBlank()) 1L else cfg.version + 1L
        val prepared = cfg.copy(version = nextVersion)
        val savedId = repo.saveRemoteConfig(ownerUid, prepared)
        sync.logAudit("SAVE_REMOTE_CONFIG", "REMOTE_CONFIG", savedId, "${cfg.configKey} v$nextVersion (${cfg.status})")
        val note = sync.trySync("remoteConfig") { svc, b -> svc.syncRemoteConfig(b, sync.toSyncRemoteConfig(savedId, prepared)) }
        _toastMessages.tryEmit(note ?: "Published Remote Config '${cfg.configKey}' v$nextVersion")
    }

    fun rollbackRemoteConfig(cfg: RemoteConfigRecord) = launchGuarded("rolling back config") {
        if (cfg.previousPayloadJson.isBlank() && cfg.previousFeatureFlagsJson.isBlank()) {
            _toastMessages.tryEmit("No previous revision saved for '${cfg.configKey}'.")
            return@launchGuarded
        }
        val rolled = cfg.copy(
            version = cfg.version + 1L,
            status = "ROLLED_BACK",
            payloadJson = cfg.previousPayloadJson.ifBlank { cfg.payloadJson },
            featureFlagsJson = cfg.previousFeatureFlagsJson.ifBlank { cfg.featureFlagsJson }
        )
        val savedId = repo.saveRemoteConfig(ownerUid, rolled)
        sync.logAudit("ROLLBACK_REMOTE_CONFIG", "REMOTE_CONFIG", savedId, "Rolled back ${cfg.configKey} -> v${rolled.version}")
        val note = sync.trySync("rollbackConfig") { svc, b -> svc.rollbackRemoteConfig(b, savedId) }
        _toastMessages.tryEmit(note ?: "Rolled back '${cfg.configKey}' to v${rolled.version}")
    }

    fun deleteRemoteConfig(cfg: RemoteConfigRecord) {
        if (cfg.id.isBlank()) return
        launchGuarded("deleting remote config") {
            repo.deleteRemoteConfig(ownerUid, cfg.id)
            sync.logAudit("DELETE_REMOTE_CONFIG", "REMOTE_CONFIG", cfg.id, "Deleted ${cfg.configKey}")
            val note = sync.trySync("deleteRemoteConfig") { svc, b -> svc.deleteRemoteConfig(b, cfg.id) }
            _toastMessages.tryEmit(note ?: "Deleted config '${cfg.configKey}'")
        }
    }

    fun saveDynamicModule(mod: DynamicModuleDef) = launchGuarded("saving dynamic module") {
        if (!PranavJsonHelper.isValidJsonObject(mod.fieldsSchemaJson) ||
            !PranavJsonHelper.isValidJsonObject(mod.defaultValuesJson) ||
            !PranavJsonHelper.isValidJsonObject(mod.actionParamsJson)
        ) {
            _toastMessages.tryEmit("Module schema, defaults, and action params must be valid JSON objects.")
            return@launchGuarded
        }
        val nextVersion = if (mod.id.isBlank()) 1L else mod.version + 1L
        val prepared = mod.copy(version = nextVersion)
        val savedId = repo.saveDynamicModule(ownerUid, prepared)
        sync.logAudit("SAVE_DYNAMIC_MODULE", "DYNAMIC_MODULE", savedId, "${mod.moduleKey} v$nextVersion enabled=${mod.enabled}")
        val note = sync.trySync("dynamicModule") { svc, b -> svc.syncDynamicModule(b, sync.toSyncDynamicModule(savedId, prepared)) }
        _toastMessages.tryEmit(note ?: "Saved module '${mod.moduleName}' v$nextVersion")
    }

    fun rollbackDynamicModule(mod: DynamicModuleDef) = launchGuarded("rolling back module") {
        if (mod.previousDefaultValuesJson.isBlank() && mod.previousActionParamsJson.isBlank()) {
            _toastMessages.tryEmit("No previous revision saved for '${mod.moduleKey}'.")
            return@launchGuarded
        }
        val rolled = mod.copy(
            version = mod.version + 1L,
            status = "ROLLED_BACK",
            defaultValuesJson = mod.previousDefaultValuesJson.ifBlank { mod.defaultValuesJson },
            actionParamsJson = mod.previousActionParamsJson.ifBlank { mod.actionParamsJson }
        )
        val savedId = repo.saveDynamicModule(ownerUid, rolled)
        sync.logAudit("ROLLBACK_DYNAMIC_MODULE", "DYNAMIC_MODULE", savedId, "Rolled back ${mod.moduleKey} -> v${rolled.version}")
        val note = sync.trySync("rollbackModule") { svc, b -> svc.rollbackDynamicModule(b, savedId) }
        _toastMessages.tryEmit(note ?: "Rolled back module '${mod.moduleName}'")
    }

    fun deleteDynamicModule(mod: DynamicModuleDef) {
        if (mod.id.isBlank()) return
        launchGuarded("deleting dynamic module") {
            repo.deleteDynamicModule(ownerUid, mod.id)
            sync.logAudit("DELETE_DYNAMIC_MODULE", "DYNAMIC_MODULE", mod.id, "Deleted ${mod.moduleKey}")
            val note = sync.trySync("deleteModule") { svc, b -> svc.deleteDynamicModule(b, mod.id) }
            _toastMessages.tryEmit(note ?: "Deleted module '${mod.moduleName}'")
        }
    }

    fun addAndValidateBot(username: String, displayName: String, token: String, roleType: String, mode: String, rateLimit: Long) {
        val cleanUser = username.trim().removePrefix("@")
        val cleanToken = token.trim()
        if (cleanUser.isBlank() || cleanToken.isBlank()) {
            _toastMessages.tryEmit("Bot username and token are required.")
            return
        }
        viewModelScope.launch {
            try {
                val cfg = prefs.settingsFlow.first()
                if (cfg.ownerApiSecret.isBlank()) {
                    _toastMessages.tryEmit("Set Owner API Bearer Key in Bots & Ops before registering bots.")
                    return@launch
                }
                val svc = PranavBackendClient.create(cfg.backendBaseUrl)
                val res = withContext(Dispatchers.IO) {
                    svc.registerBot("Bearer ${cfg.ownerApiSecret}", RegisterBotRequest(cleanUser, displayName.trim().ifBlank { cleanUser }, cleanToken, roleType, mode, rateLimit))
                }
                if (!res.ok || res.tokenSecretRef.isBlank()) {
                    _toastMessages.tryEmit(res.message ?: "Telegram getMe validation failed on backend.")
                    return@launch
                }
                repo.saveBot(ownerUid, TelegramBotConfig(id = res.botId, ownerUid = ownerUid, botUsername = res.botUsername.ifBlank { cleanUser }, botDisplayName = displayName.trim().ifBlank { cleanUser }, roleType = roleType, mode = mode, commandsCount = if (roleType == "OWNER_BOT") 25L else 14L, tokenSecretRef = res.tokenSecretRef, healthStatus = res.healthStatus, enabled = true, rateLimitPerMin = rateLimit))
                sync.logAudit("REGISTER_BOT", "BOT", res.botId, "@${res.botUsername} ($roleType)")
                _toastMessages.tryEmit("Verified @${res.botUsername} via Telegram getMe & vaulted token.")
            } catch (e: Exception) {
                _toastMessages.tryEmit("Bot validation failed: ${e.localizedMessage ?: "Backend unreachable"}. Raw token was not stored.")
            }
        }
    }

    fun performBotAction(bot: TelegramBotConfig, action: String) = launchGuarded("performing bot action") {
        val cfg = prefs.settingsFlow.first()
        if (cfg.ownerApiSecret.isBlank()) {
            _toastMessages.tryEmit("Set Owner API Bearer Key in VPS & Auth to control bots.")
            return@launchGuarded
        }
        withContext(Dispatchers.IO) {
            PranavBackendClient.create(cfg.backendBaseUrl).performBotAction("Bearer ${cfg.ownerApiSecret}", bot.id, BotActionRequest(action))
        }
        repo.saveBot(ownerUid, bot.copy(enabled = action != "STOP", healthStatus = if (action == "STOP") "PAUSED" else "ACTIVE"))
        sync.logAudit("BOT_$action", "BOT", bot.id, "@${bot.botUsername}")
        _toastMessages.tryEmit("Bot @${bot.botUsername}: $action")
    }

    fun deleteBot(bot: TelegramBotConfig) {
        if (bot.id.isBlank()) return
        launchGuarded("deleting bot") {
            val cfg = prefs.settingsFlow.first()
            if (cfg.ownerApiSecret.isNotBlank()) {
                runCatching { withContext(Dispatchers.IO) { PranavBackendClient.create(cfg.backendBaseUrl).deleteBot("Bearer ${cfg.ownerApiSecret}", bot.id) } }
            }
            repo.deleteBot(ownerUid, bot.id)
            sync.logAudit("DELETE_BOT", "BOT", bot.id, "Removed @${bot.botUsername}")
            _toastMessages.tryEmit("Removed @${bot.botUsername}")
        }
    }

    fun sendAnnouncementOrTicket(appId: String, category: String, title: String, message: String, severity: String) = launchGuarded("sending event") {
        val id = repo.recordEvent(ownerUid, AdminEvent(ownerUid = ownerUid, appId = appId, category = category, title = title, message = message, severity = severity, status = "OPEN"))
        sync.logAudit("DISPATCH_EVENT", "EVENT", id, "[$category] $title")
        val note = sync.trySync("event") { svc, b -> svc.syncEvent(b, SyncEventRequest(appId, category, title, message, severity)) }
        _toastMessages.tryEmit(note ?: "Dispatched & synced $category")
    }

    fun resolveEvent(event: AdminEvent) = launchGuarded("resolving event") {
        repo.recordEvent(ownerUid, event.copy(status = "RESOLVED"))
        sync.logAudit("RESOLVE_EVENT", "EVENT", event.id, "Resolved '${event.title}'")
        val note = sync.trySync("resolveEvent") { svc, b -> svc.resolveEvent(b, event.id) }
        _toastMessages.tryEmit(note ?: "Resolved & synced '${event.title}'")
    }

    fun toggleAdminModule(key: String, enabled: Boolean) {
        viewModelScope.launch {
            prefs.setModuleEnabled(key, enabled)
            runCatching {
                val ws = (workspaceState.value as? UiState.Success)?.data ?: return@runCatching
                val updated = ws.enabledModules.toMutableMap().apply { put(key, enabled) }
                repo.upsertWorkspace(ownerUid, ws.copy(enabledModules = updated))
            }
        }
    }

    fun saveBackendConfig(baseUrl: String, env: String, secret: String, domainHost: String) {
        viewModelScope.launch {
            prefs.saveBackendConfig(baseUrl, env, secret)
            runCatching {
                val ws = (workspaceState.value as? UiState.Success)?.data ?: OwnerWorkspace(ownerUid = ownerUid, ownerEmail = ownerEmail)
                repo.upsertWorkspace(ownerUid, ws.copy(domainHost = domainHost.trim().ifBlank { "pranavsmm.shop" }, environment = env))
                sync.logAudit("UPDATE_BACKEND_CONFIG", "WORKSPACE", "primary", "$baseUrl ($env)")
            }.fold(
                onSuccess = { _toastMessages.tryEmit("Saved backend & domain configuration") },
                onFailure = { _toastMessages.tryEmit("Saved local backend URL; cloud sync note: ${it.localizedMessage}") }
            )
        }
    }

    fun setDarkTheme(enabled: Boolean) { viewModelScope.launch { prefs.setDarkTheme(enabled) } }
    fun setLowEndFallback(enabled: Boolean) { viewModelScope.launch { prefs.setLowEndFallback(enabled) } }

    private fun launchGuarded(actionLabel: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) {
                _toastMessages.tryEmit(FirestoreErrorHandler.toUserMessage(e, actionLabel))
            }
        }
    }

    private fun buildLicenseKey(tier: String): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rng = SecureRandom()
        fun chunk(len: Int) = (1..len).map { alphabet[rng.nextInt(alphabet.length)] }.joinToString("")
        val prefix = when (tier) {
            "VIP_ELITE" -> "PRNV-ELITE"
            "VIP_PRO" -> "PRNV-PRO"
            else -> "PRNV-STD"
        }
        return "$prefix-${chunk(4)}-${chunk(4)}-${chunk(4)}"
    }
}
