package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String = "ok",
    val environment: String = "production",
    val uptimeSeconds: Long = 0L,
    val memoryUsedMb: Int = 0,
    val memoryTotalMb: Int = 0,
    val workerCount: Int = 0,
    val loadAvg1m: String = "0.00"
)

@JsonClass(generateAdapter = true)
data class RegisterBotRequest(
    val botUsername: String,
    val botDisplayName: String,
    val botToken: String,
    val roleType: String,
    val mode: String,
    val rateLimitPerMin: Long
)

@JsonClass(generateAdapter = true)
data class RegisterBotResponse(
    val ok: Boolean = false,
    val botId: String = "",
    val botUsername: String = "",
    val tokenSecretRef: String = "",
    val verifiedTelegramId: Long? = null,
    val healthStatus: String = "ACTIVE",
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class BotActionRequest(
    val action: String
)

@JsonClass(generateAdapter = true)
data class GenericApiResponse(
    val ok: Boolean = false,
    val message: String? = null,
    val status: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncWorkspaceRequest(
    val masterMaintenance: Boolean,
    val maintenanceMessage: String,
    val maintenanceStartsAtMs: Long = 0L,
    val maintenanceExpiresAtMs: Long = 0L,
    val emergencyLockdown: Boolean
)

@JsonClass(generateAdapter = true)
data class SyncAppRequest(
    val id: String,
    val appCode: String,
    val packageName: String,
    val displayName: String,
    val lifecycleStatus: String,
    val masterEnabled: Boolean,
    val popupEnabled: Boolean,
    val forceUpdateEnabled: Boolean,
    val laterEnabled: Boolean,
    val latestVersion: String,
    val minSupportedVersion: String,
    val apkDownloadUrl: String,
    val updateTitle: String,
    val updateDescription: String,
    val releaseNotes: String,
    val whatsNew: String,
    val channelLink: String,
    val button1Text: String,
    val button2Text: String,
    val rolloutPercent: Long,
    val scheduledAtMs: Long = 0L,
    val expiresAtMs: Long = 0L,
    val targetUserIds: List<String> = emptyList(),
    val targetVersions: List<String> = emptyList(),
    val maintenanceEnabled: Boolean = false,
    val maintenanceMessage: String = ""
)

@JsonClass(generateAdapter = true)
data class SyncDialogRequest(
    val id: String,
    val appId: String,
    val dialogType: String,
    val title: String,
    val description: String,
    val primaryButtonText: String,
    val primaryActionType: String,
    val primaryActionUrl: String,
    val secondaryButtonText: String,
    val secondaryActionType: String,
    val animationStyle: String,
    val soundName: String,
    val forceDialog: Boolean,
    val dismissible: Boolean,
    val laterAllowed: Boolean,
    val enabled: Boolean,
    val priority: Long,
    val targetUserIds: List<String> = emptyList(),
    val targetDeviceIds: List<String> = emptyList(),
    val targetVersions: List<String> = emptyList(),
    val startsAtMs: Long = 0L,
    val expiresAtMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class SyncDeviceRequest(
    val id: String,
    val appId: String,
    val userUid: String = "",
    val deviceLabel: String,
    val installUuid: String,
    val keystoreKeyHash: String,
    val approvalStatus: String,
    val restrictions: String = "",
    val androidApi: Long,
    val riskScore: Long,
    val transferCount: Long
)

@JsonClass(generateAdapter = true)
data class SyncLicenseRequest(
    val id: String,
    val appId: String,
    val userUid: String = "",
    val licenseKey: String,
    val planTier: String,
    val status: String,
    val durationDays: Long,
    val graceDays: Long,
    val deviceLimit: Long = 1L,
    val paymentVerified: Boolean,
    val transferCooldownHours: Long,
    val boundDeviceId: String,
    val boundDeviceIds: List<String> = emptyList(),
    val expiresAtEpochMs: Long
)

@JsonClass(generateAdapter = true)
data class SyncUserRequest(
    val id: String,
    val uid: String,
    val appId: String,
    val email: String,
    val displayName: String,
    val status: String,
    val banReason: String,
    val userConfigJson: String,
    val note: String
)

@JsonClass(generateAdapter = true)
data class BanUserRequest(
    val banned: Boolean,
    val reason: String
)

@JsonClass(generateAdapter = true)
data class SyncAnnouncementRequest(
    val id: String,
    val scope: String,
    val displayType: String,
    val appId: String,
    val targetUserIds: List<String> = emptyList(),
    val targetDeviceIds: List<String> = emptyList(),
    val title: String,
    val message: String,
    val actionText: String,
    val actionUrl: String,
    val priority: Long,
    val dismissible: Boolean,
    val enabled: Boolean,
    val startsAtMs: Long = 0L,
    val expiresAtMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class SyncRemoteConfigRequest(
    val id: String,
    val appId: String,
    val configKey: String,
    val version: Long,
    val status: String,
    val enabled: Boolean,
    val priority: Long,
    val payloadJson: String,
    val featureFlagsJson: String,
    val targetUserIds: List<String> = emptyList(),
    val targetDeviceIds: List<String> = emptyList(),
    val targetVersions: List<String> = emptyList(),
    val startsAtMs: Long = 0L,
    val expiresAtMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class SyncDynamicModuleRequest(
    val id: String,
    val appId: String,
    val moduleKey: String,
    val moduleName: String,
    val category: String,
    val description: String,
    val enabled: Boolean,
    val status: String,
    val version: Long,
    val priority: Long,
    val fieldsSchemaJson: String,
    val defaultValuesJson: String,
    val actionParamsJson: String,
    val targetUserIds: List<String> = emptyList(),
    val targetDeviceIds: List<String> = emptyList(),
    val targetVersions: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SyncEventRequest(
    val appId: String,
    val category: String,
    val title: String,
    val message: String,
    val severity: String
)

@JsonClass(generateAdapter = true)
data class UpdateConfirmationDto(
    val appId: String = "",
    val appCode: String = "",
    val deviceId: String = "",
    val installedVersion: String = "",
    val confirmedAtMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class UpdateHistoryResponse(
    val ok: Boolean = false,
    val updates: List<UpdateConfirmationDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackendAuditLogDto(
    val id: String = "",
    val actor: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val summary: String = "",
    val atMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class BackendAuditLogsResponse(
    val ok: Boolean = false,
    val logs: List<BackendAuditLogDto> = emptyList()
)
