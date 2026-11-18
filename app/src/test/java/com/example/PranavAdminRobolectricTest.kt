package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AdminModuleCatalog
import com.example.data.model.AnnouncementRecord
import com.example.data.model.AuditLogEntry
import com.example.data.model.DynamicModuleDef
import com.example.data.model.LicenseRecord
import com.example.data.model.ManagedApp
import com.example.data.model.ManagedUser
import com.example.data.model.PranavJsonHelper
import com.example.data.model.RemoteConfigRecord
import com.example.data.model.TelegramBotConfig
import com.example.data.model.UniversalDialog
import com.example.data.remote.ApkInspectorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PranavAdminRobolectricTest {

    @Test
    fun `catalog contains all 52 independent admin modules and dialog types`() {
        assertEquals(52, AdminModuleCatalog.allModules.size)
        assertEquals(52, AdminModuleCatalog.allModules.map { it.key }.distinct().size)
        assertTrue(AdminModuleCatalog.dialogTypes.contains("FORCE_UPDATE"))
        assertTrue(AdminModuleCatalog.dialogTypes.contains("VIP_UPGRADE"))
    }

    @Test
    fun `managed app serialization enforces force update suppresses later and omits id field`() {
        val app = ManagedApp(
            id = "doc_123",
            ownerUid = "owner_uid",
            appCode = "smm_pro",
            packageName = "com.pranav.smmpro",
            displayName = "SMM Pro",
            forceUpdateEnabled = true,
            laterEnabled = true,
            rolloutPercent = 75L,
            maintenanceEnabled = true,
            maintenanceMessage = "Upgrading cluster"
        )
        val map = app.toFirestoreMap(isNew = true)
        assertFalse(map.containsKey("id"))
        assertEquals(false, map["laterEnabled"])
        assertEquals(75L, map["rolloutPercent"])
        assertEquals(true, map["maintenanceEnabled"])
    }

    @Test
    fun `new control models match zero-trust firestore schema invariants`() {
        val dlg = UniversalDialog(
            ownerUid = "owner_uid",
            appId = "app_1",
            dialogType = "SECURITY_LOCK",
            title = "Locked",
            forceDialog = true,
            dismissible = true,
            laterAllowed = true
        ).toFirestoreMap(isNew = true)
        assertEquals(false, dlg["dismissible"])
        assertEquals(false, dlg["laterAllowed"])

        val user = ManagedUser(ownerUid = "owner_uid", uid = "u_99", email = "u@test.com", displayName = "Riya", status = "BANNED").toFirestoreMap(isNew = true)
        assertEquals("u_99", user["uid"])
        assertEquals("BANNED", user["status"])

        val cfg = RemoteConfigRecord(ownerUid = "owner_uid", appId = "app_1", configKey = "ui", version = 2L, status = "PUBLISHED").toFirestoreMap(isNew = true)
        assertEquals(2L, cfg["version"])
        assertEquals("PUBLISHED", cfg["status"])

        val mod = DynamicModuleDef(ownerUid = "owner_uid", appId = "app_1", moduleKey = "promo", moduleName = "Promo", category = "Dynamic", version = 1L, status = "PUBLISHED").toFirestoreMap(isNew = true)
        assertEquals("promo", mod["moduleKey"])

        val ann = AnnouncementRecord(ownerUid = "owner_uid", scope = "USER", displayType = "BANNER", appId = "app_1", title = "Sale").toFirestoreMap(isNew = true)
        assertEquals("USER", ann["scope"])
        assertEquals("BANNER", ann["displayType"])

        val lic = LicenseRecord(ownerUid = "owner_uid", licenseKey = "PRNV-PRO-1", deviceLimit = 0L).toFirestoreMap(isNew = true)
        assertEquals(1L, lic["deviceLimit"])

        val audit = AuditLogEntry(ownerUid = "owner_uid", actor = "ai.master.bot.official@gmail.com", action = "BAN_USER", targetType = "USER", summary = "Banned u_99").toFirestoreMap()
        assertEquals("BAN_USER", audit["action"])
    }

    @Test
    fun `json and csv validation helpers accept valid objects and reject malformed json`() {
        assertTrue(PranavJsonHelper.isValidJsonObject("{\"darkMode\": true, \"limit\": 5}"))
        assertTrue(PranavJsonHelper.isValidJsonObject(""))
        assertFalse(PranavJsonHelper.isValidJsonObject("{not-valid-json"))
        assertEquals(listOf("u1", "u2", "u3"), PranavJsonHelper.parseCsv("u1, u2 , , u3"))
    }

    @Test
    fun `telegram bot firestore map uses secret reference and never raw token`() {
        val bot = TelegramBotConfig(
            id = "bot_1",
            ownerUid = "owner_uid",
            botUsername = "pranav_ops_bot",
            botDisplayName = "PRANAV Ops Bot",
            roleType = "OWNER_BOT",
            mode = "POLLING",
            commandsCount = 25L,
            tokenSecretRef = "vault://bots/bot_1"
        )
        val map = bot.toFirestoreMap(isNew = true)
        assertFalse(map.containsKey("botToken"))
        assertTrue((map["tokenSecretRef"] as String).startsWith("vault://"))
    }

    @Test
    fun `apk inspector parses valid synthetic apk zip manifest and dex count`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inspector = ApkInspectorEngine(context)
        val tempApk = File.createTempFile("pranav_test_target", ".apk")
        try {
            ZipOutputStream(tempApk.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write(
                    """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="com.authorized.target"
                        android:versionName="2.1.0">
                        <application android:name=".TargetApplication">
                            <activity android:name=".MainActivity" />
                        </application>
                    </manifest>
                    """.trimIndent().toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write("dex\n035\u0000Lcom/authorized/target/MainActivity;".toByteArray(Charsets.ISO_8859_1))
                zos.closeEntry()
            }

            val report = inspector.inspectFile(tempApk)
            assertEquals("com.authorized.target", report.packageName)
            assertEquals(1, report.dexFileCount)
            assertEquals(false, report.hasPranavSdkDex)
            assertEquals("COMPATIBLE", report.compatibilityVerdict)
            assertEquals("APPLICATION_ON_CREATE", report.recommendedHookPoint)
            assertTrue(report.smaliSnippet.contains("Lcom/pranav/sdk/PranavSdk;->init"))
        } finally {
            tempApk.delete()
        }
    }
}
