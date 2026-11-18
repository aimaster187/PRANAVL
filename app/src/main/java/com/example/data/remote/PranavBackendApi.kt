package com.example.data.remote

import com.example.data.model.BackendAuditLogsResponse
import com.example.data.model.BanUserRequest
import com.example.data.model.BotActionRequest
import com.example.data.model.GenericApiResponse
import com.example.data.model.HealthResponse
import com.example.data.model.RegisterBotRequest
import com.example.data.model.RegisterBotResponse
import com.example.data.model.SyncAnnouncementRequest
import com.example.data.model.SyncAppRequest
import com.example.data.model.SyncDeviceRequest
import com.example.data.model.SyncDialogRequest
import com.example.data.model.SyncDynamicModuleRequest
import com.example.data.model.SyncEventRequest
import com.example.data.model.SyncLicenseRequest
import com.example.data.model.SyncRemoteConfigRequest
import com.example.data.model.SyncUserRequest
import com.example.data.model.SyncWorkspaceRequest
import com.example.data.model.UpdateHistoryResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface PranavBackendService {
    @GET("/api/v1/health")
    suspend fun getHealth(): HealthResponse

    @POST("/api/v1/admin/workspace")
    suspend fun syncWorkspace(
        @Header("Authorization") bearerAuth: String,
        @Body request: SyncWorkspaceRequest
    ): GenericApiResponse

    @POST("/api/v1/admin/apps")
    suspend fun syncApp(
        @Header("Authorization") bearerAuth: String,
        @Body request: SyncAppRequest
    ): GenericApiResponse

    @DELETE("/api/v1/admin/apps/{appId}")
    suspend fun deleteApp(
        @Header("Authorization") bearerAuth: String,
        @Path("appId") appId: String
    ): GenericApiResponse

    @GET("/api/v1/admin/updates/history")
    suspend fun getUpdateHistory(
        @Header("Authorization") bearerAuth: String
    ): UpdateHistoryResponse

    @POST("/api/v1/admin/users")
    suspend fun syncUser(
        @Header("Authorization") bearerAuth: String,
        @Body request: SyncUserRequest
    ): GenericApiResponse

    @POST("/api/v1/admin/users/{uid}/ban")
    suspend fun banUser(
        @Header("Authorization") bearerAuth: String,
        @Path("uid") uid: String,
        @Body request: BanUserRequest
    ): GenericApiResponse

    @DELETE("/api/v1/admin/users/{uid}")
    suspend fun deleteUser(
        @Header("Authorization") bearerAuth: String,
        @Path("uid") uid: String
    ): GenericApiResponse

    @POST("/api/v1/admin/dialogs")
    suspend fun syncDialog(
        @Header("Authorization") bearerAuth: String,
        @Body request: SyncDialogRequest
    ): GenericApiResponse

    @DELETE("/api/v1/admin/dialogs/{dialogId}")
    suspend fun deleteDialog(
        @Header("Authorization") bearerAuth: String,
        @Path("dialogId") dialogId: String
    ): GenericApiResponse

    @POST("/api/v1/admin/devices")
    suspend fun syncDevice(
        @Header("Authorization") bearerAuth: String,
        @Body request: SyncDeviceRequest
    ): GenericApiResponse

    @DELETE("/api/v1/admin/devices/{deviceId}")
    suspend fun deleteDevice(
        @Header("Authorization") bearerAuth: String,
        @Path("deviceId") deviceId: String
    ): GenericApiResponse

    @POST("/api/v1/admin/licenses")
    suspend fun syncLicense(
        @Header("Authorization") bearerAuth: String,
        @Body request: SyncLicenseRequest
    ): GenericApiResponse

    @DELETE("/api/v1/admin/licenses/{licenseId}")
    suspend fun deleteLicense(
        @Header("Authorization") bearerAuth: String,
        @Path("licenseId") licenseId: String
    ): GenericApiResponse

    @POST("/api/v1/admin/announcements")
    suspend fun syncAnnouncement(
        @Header("Authorization") bearerAuth: String,
        @Body request: SyncAnnouncementRequest
    ): GenericApiResponse

    @DELETE("/api/v1/admin/announcements/{id}")
    suspend fun deleteAnnouncement(
        @Header("Authorization") bearerAuth: String,
        @Path("id") id: String
    ): GenericApiResponse

    @POST("/api/v1/admin/configs")
    suspend fun syncRemoteConfig(
        @Header("Authorization") bearerAuth: String,
        @Body request: SyncRemoteConfigRequest
    ): GenericApiResponse

    @POST("/api/v1/admin/configs/{id}/rollback")
    suspend fun rollbackRemoteConfig(
        @Header("Authorization") bearerAuth: String,
        @Path("id") id: String
    ): GenericApiResponse

    @DELETE("/api/v1/admin/configs/{id}")
    suspend fun deleteRemoteConfig(
        @Header("Authorization") bearerAuth: String,
        @Path("id") id: String
    ): GenericApiResponse

    @POST("/api/v1/admin/modules")
    suspend fun syncDynamicModule(
        @Header("Authorization") bearerAuth: String,
        @Body request: SyncDynamicModuleRequest
    ): GenericApiResponse

    @POST("/api/v1/admin/modules/{id}/rollback")
    suspend fun rollbackDynamicModule(
        @Header("Authorization") bearerAuth: String,
        @Path("id") id: String
    ): GenericApiResponse

    @DELETE("/api/v1/admin/modules/{id}")
    suspend fun deleteDynamicModule(
        @Header("Authorization") bearerAuth: String,
        @Path("id") id: String
    ): GenericApiResponse

    @POST("/api/v1/admin/events")
    suspend fun syncEvent(
        @Header("Authorization") bearerAuth: String,
        @Body request: SyncEventRequest
    ): GenericApiResponse

    @POST("/api/v1/admin/events/{eventId}/resolve")
    suspend fun resolveEvent(
        @Header("Authorization") bearerAuth: String,
        @Path("eventId") eventId: String
    ): GenericApiResponse

    @GET("/api/v1/admin/audit-logs")
    suspend fun getAuditLogs(
        @Header("Authorization") bearerAuth: String
    ): BackendAuditLogsResponse

    @POST("/api/v1/bots")
    suspend fun registerBot(
        @Header("Authorization") bearerAuth: String,
        @Body request: RegisterBotRequest
    ): RegisterBotResponse

    @POST("/api/v1/bots/{botId}/action")
    suspend fun performBotAction(
        @Header("Authorization") bearerAuth: String,
        @Path("botId") botId: String,
        @Body request: BotActionRequest
    ): GenericApiResponse

    @DELETE("/api/v1/bots/{botId}")
    suspend fun deleteBot(
        @Header("Authorization") bearerAuth: String,
        @Path("botId") botId: String
    ): GenericApiResponse
}

object PranavBackendClient {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    fun create(baseUrl: String): PranavBackendService {
        val normalized = baseUrl.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) "$it/" else "https://$it/"
        }
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PranavBackendService::class.java)
    }
}
