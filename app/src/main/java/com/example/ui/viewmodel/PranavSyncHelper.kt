package com.example.ui.viewmodel

import com.example.data.local.PranavPreferences
import com.example.data.model.AnnouncementRecord
import com.example.data.model.AuditLogEntry
import com.example.data.model.BanUserRequest
import com.example.data.model.DynamicModuleDef
import com.example.data.model.LicenseRecord
import com.example.data.model.ManagedApp
import com.example.data.model.ManagedDevice
import com.example.data.model.ManagedUser
import com.example.data.model.RemoteConfigRecord
import com.example.data.model.SyncAnnouncementRequest
import com.example.data.model.SyncAppRequest
import com.example.data.model.SyncDeviceRequest
import com.example.data.model.SyncDialogRequest
import com.example.data.model.SyncDynamicModuleRequest
import com.example.data.model.SyncLicenseRequest
import com.example.data.model.SyncRemoteConfigRequest
import com.example.data.model.SyncUserRequest
import com.example.data.model.UniversalDialog
import com.example.data.remote.PranavBackendClient
import com.example.data.remote.PranavBackendService
import com.example.data.remote.PranavFirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class PranavSyncHelper(
    private val repo: PranavFirestoreRepository,
    private val prefs: PranavPreferences,
    private val ownerUid: String,
    private val ownerEmail: String
) {
    suspend fun logAudit(action: String, targetType: String, targetId: String, summary: String) {
        try {
            repo.recordAuditLog(
                ownerUid,
                AuditLogEntry(
                    ownerUid = ownerUid,
                    actor = ownerEmail,
                    action = action,
                    targetType = targetType,
                    targetId = targetId,
                    summary = summary
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun trySync(
        actionLabel: String,
        call: suspend (PranavBackendService, String) -> Unit
    ): String? {
        val cfg = prefs.settingsFlow.first()
        if (cfg.ownerApiSecret.isBlank()) {
            return "Saved to Firestore (configure Owner API Key in Bots & Ops to mirror to VPS backend)"
        }
        return try {
            val service = PranavBackendClient.create(cfg.backendBaseUrl)
            withContext(Dispatchers.IO) {
                call(service, "Bearer ${cfg.ownerApiSecret}")
            }
            null
        } catch (e: Exception) {
            "Saved to Firestore; VPS backend sync failed ($actionLabel): ${e.localizedMessage ?: "unreachable"}"
        }
    }

    fun toSyncApp(app: ManagedApp) = SyncAppRequest(
        id = app.id,
        appCode = app.appCode,
        packageName = app.packageName,
        displayName = app.displayName,
        lifecycleStatus = app.lifecycleStatus,
        masterEnabled = app.masterEnabled,
        popupEnabled = app.popupEnabled,
        forceUpdateEnabled = app.forceUpdateEnabled,
        laterEnabled = if (app.forceUpdateEnabled) false else app.laterEnabled,
        latestVersion = app.latestVersion,
        minSupportedVersion = app.minSupportedVersion,
        apkDownloadUrl = app.apkDownloadUrl,
        updateTitle = app.updateTitle,
        updateDescription = app.updateDescription,
        releaseNotes = app.releaseNotes,
        whatsNew = app.whatsNew,
        channelLink = app.channelLink,
        button1Text = app.button1Text,
        button2Text = app.button2Text,
        rolloutPercent = app.rolloutPercent,
        scheduledAtMs = app.scheduledAtMs,
        expiresAtMs = app.expiresAtMs,
        targetUserIds = app.targetUserIds,
        targetVersions = app.targetVersions,
        maintenanceEnabled = app.maintenanceEnabled,
        maintenanceMessage = app.maintenanceMessage
    )

    fun toSyncDialog(id: String, d: UniversalDialog) = SyncDialogRequest(
        id = id,
        appId = d.appId,
        dialogType = d.dialogType,
        title = d.title,
        description = d.description,
        primaryButtonText = d.primaryButtonText,
        primaryActionType = d.primaryActionType,
        primaryActionUrl = d.primaryActionUrl,
        secondaryButtonText = d.secondaryButtonText,
        secondaryActionType = d.secondaryActionType,
        animationStyle = d.animationStyle,
        soundName = d.soundName,
        forceDialog = d.forceDialog,
        dismissible = if (d.forceDialog) false else d.dismissible,
        laterAllowed = if (d.forceDialog) false else d.laterAllowed,
        enabled = d.enabled,
        priority = d.priority,
        targetUserIds = d.targetUserIds,
        targetDeviceIds = d.targetDeviceIds,
        targetVersions = d.targetVersions,
        startsAtMs = d.startsAtMs,
        expiresAtMs = d.expiresAtMs
    )

    fun toSyncDevice(id: String, dev: ManagedDevice) = SyncDeviceRequest(
        id = id,
        appId = dev.appId,
        userUid = dev.userUid,
        deviceLabel = dev.deviceLabel,
        installUuid = dev.installUuid,
        keystoreKeyHash = dev.keystoreKeyHash,
        approvalStatus = dev.approvalStatus,
        restrictions = dev.restrictions,
        androidApi = dev.androidApi,
        riskScore = dev.riskScore,
        transferCount = dev.transferCount
    )

    fun toSyncLicense(id: String, lic: LicenseRecord) = SyncLicenseRequest(
        id = id,
        appId = lic.appId,
        userUid = lic.userUid,
        licenseKey = lic.licenseKey,
        planTier = lic.planTier,
        status = lic.status,
        durationDays = lic.durationDays,
        graceDays = lic.graceDays,
        deviceLimit = lic.deviceLimit.coerceAtLeast(1L),
        paymentVerified = lic.paymentVerified,
        transferCooldownHours = lic.transferCooldownHours,
        boundDeviceId = lic.boundDeviceId,
        boundDeviceIds = lic.boundDeviceIds,
        expiresAtEpochMs = lic.expiresAtEpochMs
    )

    fun toSyncUser(id: String, u: ManagedUser) = SyncUserRequest(
        id = id,
        uid = u.uid.ifBlank { id },
        appId = u.appId,
        email = u.email,
        displayName = u.displayName,
        status = u.status,
        banReason = u.banReason,
        userConfigJson = u.userConfigJson,
        note = u.note
    )

    fun toSyncAnnouncement(id: String, a: AnnouncementRecord) = SyncAnnouncementRequest(
        id = id,
        scope = a.scope,
        displayType = a.displayType,
        appId = a.appId,
        targetUserIds = a.targetUserIds,
        targetDeviceIds = a.targetDeviceIds,
        title = a.title,
        message = a.message,
        actionText = a.actionText,
        actionUrl = a.actionUrl,
        priority = a.priority,
        dismissible = a.dismissible,
        enabled = a.enabled,
        startsAtMs = a.startsAtMs,
        expiresAtMs = a.expiresAtMs
    )

    fun toSyncRemoteConfig(id: String, cfg: RemoteConfigRecord) = SyncRemoteConfigRequest(
        id = id,
        appId = cfg.appId,
        configKey = cfg.configKey,
        version = cfg.version,
        status = cfg.status,
        enabled = cfg.enabled,
        priority = cfg.priority,
        payloadJson = cfg.payloadJson,
        featureFlagsJson = cfg.featureFlagsJson,
        targetUserIds = cfg.targetUserIds,
        targetDeviceIds = cfg.targetDeviceIds,
        targetVersions = cfg.targetVersions,
        startsAtMs = cfg.startsAtMs,
        expiresAtMs = cfg.expiresAtMs
    )

    fun toSyncDynamicModule(id: String, mod: DynamicModuleDef) = SyncDynamicModuleRequest(
        id = id,
        appId = mod.appId,
        moduleKey = mod.moduleKey,
        moduleName = mod.moduleName,
        category = mod.category,
        description = mod.description,
        enabled = mod.enabled,
        status = mod.status,
        version = mod.version,
        priority = mod.priority,
        fieldsSchemaJson = mod.fieldsSchemaJson,
        defaultValuesJson = mod.defaultValuesJson,
        actionParamsJson = mod.actionParamsJson,
        targetUserIds = mod.targetUserIds,
        targetDeviceIds = mod.targetDeviceIds,
        targetVersions = mod.targetVersions
    )

    fun buildBanRequest(banned: Boolean, reason: String) = BanUserRequest(banned = banned, reason = reason)
}
