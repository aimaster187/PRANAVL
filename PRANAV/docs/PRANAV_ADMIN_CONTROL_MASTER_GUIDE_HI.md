# PRANAV PROJECT — MASTER ADMIN CONTROL & FULL ECOSYSTEM GUIDE (हिंदी + Technical Reference)

## 1. प्रोजेक्ट परिचय (Overview & Architecture)
**PRANAV** एक Single Root Owner (`ai.master.bot.official@gmail.com`) संचालित पूर्ण Android Control Plane और Universal DEX SDK इकोसिस्टम है। इसका उद्देश्य owner द्वारा अधिकृत Android APKs में बिना Google Play या किसी public developer account के केंद्रीय कंट्रोल, रिमोट कॉन्फ़िग, फ़ोर्स अपडेट, डायनामिक मॉड्यूल, लाइसेंस/VIP और सुरक्षा नियम लागू करना है।

### मुख्य घटक (Core Layers)
1. **PRANAV Admin Android App (`/app`)** — Kotlin + Jetpack Compose + Material 3 + Firebase Auth (Google Sign-In `CredentialManager`) + Cloud Firestore + Room Cache + DataStore + Retrofit/Moshi + Local SAF APK Inspector।
2. **PRANAV Node.js Backend (`/PRANAV/backend`)** — Zero-dependency Node.js 20+ ESM REST API + Telegram Multi-Bot Manager + AES-256-GCM Bot Token Vault + Auto-Expiry Scheduler + Per-IP Sliding-Window Rate Limiter।
3. **PRANAV Universal DEX SDK (`/PRANAV/dex`)** — `pranav-sdk-1.4.0.dex` (`SHA-256: 7dce1cff974efb7dd8f19be3e1e6b1581bbfd55a7d161426a0cb42dbc83bd15a`), Android `Application.ActivityLifecycleCallbacks`, Hardware Keystore EC P-256 Identity, Offline Cache (`PranavConfigCache`), APK DownloadManager (`PranavUpdateManager`), Universal Dialog Engine (`PranavDialogEngine`), और Event Logger (`PranavEventLogger`)।
4. **Integration Hooks (`/PRANAV/integration`)** — अलग-अलग Hook टेम्पलेट्स: Application hook, Activity hook, `attachBaseContext` hook, Verified DEX Loader, Kotlin Wrapper, Smali और AndroidManifest स्निपेट।
5. **Firebase Zero-Trust Rules & CI** — `/firestore.rules`, `/firebase-blueprint.json`, `/firestore.test.js`, और `.github/workflows/pranav-ci.yml`।

---

## 2. एडमिन ऐप की सभी स्क्रीन व मॉड्यूल (Admin App Screens & Files)
- **Authentication (`ui/auth/AuthScreen.kt`, `GoogleSignInHelper.kt`)**: केवल Verified `ai.master.bot.official@gmail.com` को अनुमति देता है। अन्य किसी भी Google खाते को `UnauthorizedOwnerScreen` दिखाता है।
- **Core Dashboard (`ui/screens/DashboardScreen.kt`)**:
  - Live KPI कार्ड्स: Managed Apps, Managed Users, Devices, Licenses & VIP।
  - Node.js Backend & VPS `/api/v1/health` लाइव पिंग।
  - **Global Maintenance Switch**: कस्टम मैसेज और समय सीमा (Indefinite, 1h, 6h, 24h) के साथ।
  - **Emergency Kill-Switch**: Confirmation dialog के बाद सभी client handshakes को `SECURITY_LOCK` पर ब्लॉक करता है।
  - Immutable Audit Log व Telemetry feed प्रीव्यू।
- **Apps, Updates & Universal Dialogs (`ui/screens/AppsAndUpdateScreen.kt`, `AppAndUpdateEditors.kt`)**:
  - Search, Status Filter (`ALL`/`ACTIVE`/`DISABLED`/`SUSPENDED`/`ARCHIVED`), Pagination (`Load More`)।
  - Per-App Master switch, Force Update (स्वतः `laterEnabled = false`), Staged Rollout %, Scheduled Window (`scheduledAtMs`/`expiresAtMs`), Target User IDs & Versions, Per-App Maintenance lock।
  - **Verified Client Update History**: `GET /api/v1/admin/updates/history` द्वारा प्राप्त क्लाइंट कन्फ़र्मेशन।
  - **Universal Dialog Engine**: 9 कैटेगरी (`NORMAL_UPDATE`, `FORCE_UPDATE`, `LICENSE_ACTIVATION`, `VIP_UPGRADE`, `DEVICE_APPROVAL`, `CHANNEL_VERIFY`, `MAINTENANCE`, `ANNOUNCEMENT`, `SECURITY_LOCK`), Action Types (`OPEN_URL`, `DOWNLOAD_APK`, `ACTIVATE_LICENSE`, `DISMISS`), Force Mode, User/Device/Version टार्गेटिंग, Preview और Delete confirmation।
- **DEX & Integration Assistant (`ui/screens/IntegrationScreen.kt`)**:
  - SAF Zero-Permission APK Picker -> AXML Binary Manifest & `classes*.dex` स्कैन, App-specific Smali / Java / Manifest हुक जनरेशन।
  - DEX Releases पब्लिशिंग (64-char SHA-256 वेरिफिकेशन के साथ)।
- **Keys, Devices & Users (`ui/screens/SecurityAndLicensesScreen.kt`, `UsersManagementTab.kt`)**:
  - **Licenses & VIP**: `STANDARD` / `VIP_PRO` / `VIP_ELITE`, VIP Payment Gate, `deviceLimit` (1 या अधिक डिवाइस), Transfer Cooldown, Unbind Device, Activate, `+30d` Renew, Revoke, Delete।
  - **Devices**: Installation UUID + Keystore EC P-256 fingerprint, Approve / Pending / Block + custom restriction message + Delete।
  - **Users**: Search, Filter, Create/Edit User UID + Email + `userConfigJson` per-user override + Notes, Ban / Unban (तुरंत `403` + `SECURITY_LOCK` डायलॉग) व Delete।
- **Bots, Comms, Remote Config, Modules & VPS (`ui/screens/BotsAndOpsScreen.kt`, `CommsAndAuditTab.kt`, `RemoteConfigAndModulesTabs.kt`)**:
  - **Telegram Multi-Bot Vault**: `@bot_username` + token केवल HTTPS द्वारा Backend AES-256-GCM Vault में सेव होते हैं (`tokenSecretRef: vault://...`), Firestore में कभी raw token नहीं जाता।
  - **Targeted Announcements**: `GLOBAL` / `APP` / `USER` / `DEVICE` स्कोप और `POPUP` / `BANNER` / `TOAST` / `NOTIFICATION` प्रकार।
  - **Remote Config Engine**: JSON validation, Feature Flags JSON, Version auto-increment, Target User/Version, 1-Tap Rollback व Delete।
  - **Dynamic Module Builder + 52 Core Modules**: कस्टम runtime JSON स्कीमा मॉड्यूल (Preview / Rollback / Delete) और सभी 52 Core Admin Modules के लिए स्वतंत्र ON/OFF स्विच।
  - **Immutable Audit Logs Viewer**: Search + Pagination के साथ हर एडमिन ऑपरेशन का रिकॉर्ड।

---

## 3. Backend API Routes (`/PRANAV/backend/src/routes/`)
सभी `/api/v1/admin/*` व `/api/v1/bots/*` रूट्स के लिए `Authorization: Bearer <OWNER_API_KEY>` अनिवार्य है।
- **Client SDK Routes (`client.js`)**:
  - `POST /api/v1/client/challenge` — ECDSA P-256 चैलेंज nonce जनरेट करता है।
  - `POST /api/v1/client/session` (व alias `/handshake`) — सिग्नेचर वेरिफ़ाई करता है; User ban / Lockdown / Global & Per-App Maintenance चेक करता है; Force Update, Remote Config (userConfig मर्ज सहित), Dynamic Modules, Active License, Targeted Announcements व Dialogs लौटाता है।
  - `POST /api/v1/client/config` — हल्का Runtime Remote Config व Dynamic Modules सिंक।
  - `POST /api/v1/client/license/activate` | `/verify` | `/transfer` — VIP Payment Gate, `deviceLimit`, और Transfer Cooldown एनफोर्स करता है।
  - `POST /api/v1/client/update-confirm` — सफल इंस्टॉल/अपडेट का प्रमाण दर्ज करता है।
  - `POST /api/v1/client/announcements/ack` — Announcement पढ़ा/बंद (`dismissedBy`) ट्रैक करता है।
  - `POST /api/v1/client/events` — Client SDK टेलीमेट्री लॉग करता है।
- **Admin Routes (`admin.js`)**:
  - Apps: `GET/POST /api/v1/admin/apps`, `DELETE /api/v1/admin/apps/:id`, `GET /api/v1/admin/updates/history`
  - Users: `GET/POST /api/v1/admin/users`, `POST /api/v1/admin/users/:uid/ban`, `GET /api/v1/admin/users/:uid/activity`, `DELETE /api/v1/admin/users/:uid`
  - Dialogs: `GET/POST /api/v1/admin/dialogs`, `DELETE /api/v1/admin/dialogs/:id`
  - Devices: `GET/POST /api/v1/admin/devices`, `DELETE /api/v1/admin/devices/:id`
  - Licenses: `GET/POST /api/v1/admin/licenses`, `DELETE /api/v1/admin/licenses/:id`
  - Announcements: `GET/POST /api/v1/admin/announcements`, `DELETE /api/v1/admin/announcements/:id`
  - Remote Configs: `GET/POST /api/v1/admin/configs`, `POST /api/v1/admin/configs/:id/rollback`, `DELETE /api/v1/admin/configs/:id`
  - Dynamic Modules: `GET/POST /api/v1/admin/modules`, `POST /api/v1/admin/modules/:id/rollback`, `DELETE /api/v1/admin/modules/:id`
  - Workspace & Logs: `POST /api/v1/admin/workspace`, `POST /api/v1/admin/events`, `POST /api/v1/admin/events/:id/resolve`, `GET /api/v1/admin/audit-logs`

---

## 4. Universal DEX SDK व Hook Integration गाइड
1. **DEX बिल्ड करना**: `bash PRANAV/dex/build-dex.sh` -> `PRANAV/dex/build/pranav-sdk-1.4.0.dex` बनाता है (SHA-256 सत्यापित)।
2. **Host APK में जोड़ना**:
   - DEX को APK में `classes2.dex` (या अगले उपलब्ध index) के रूप में जोड़ें।
   - `AndroidManifest.xml` में `INTERNET` (और optional APK इन-ऐप अपडेट के लिए `REQUEST_INSTALL_PACKAGES` — ध्यान दें Google Play ऐप्स में केवल साइडलोड डिस्ट्रीब्यूशन पर ही उपयोग करें)।
   - **Startup Initialization (`Application.onCreate` / `Activity.onCreate`)**:
     ```java
     PranavSdk.init(this, "my_app_code", "https://api.pranavsmm.shop");
     PranavSdk.setUserId("user_123"); // Optional user targeting & ban check
     ```
   - `PranavSdk` स्वतः `Application.ActivityLifecycleCallbacks` रजिस्टर करता है ताकि बैकग्राउंड नेटवर्क handshake पूरा होते ही सक्रिय `Activity` पर Dialogs और `BANNER`/`TOAST`/`POPUP` Announcements सुरक्षित रूप से दिखें (async race समाप्त)।
3. **Runtime SDK APIs**:
   - `PranavSdk.getRemoteString(key, default)` / `getRemoteLong(key, default)` / `getFeatureFlag(key, default)`
   - `PranavSdk.getDynamicModule(moduleKey)` / `PranavSdk.getCachedDynamicModules()`
   - `PranavSdk.activateLicense(key, callback)` / `verifyLicense(key, callback)` / `isVipActive()`
   - `PranavSdk.logEvent(category, title, message, severity)`
   - `PranavSdk.confirmUpdateInstalled(versionName)`

---

## 5. सुरक्षा और VPS / Domain Deployment
- **Firestore Zero-Trust Rules (`/firestore.rules`)**: सभी कलेक्शन केवल `/users/{userId}/...` के अंदर हैं जहाँ `request.auth.uid == userId && request.auth.token.email == 'ai.master.bot.official@gmail.com' && request.auth.token.email_verified == true`। `auditLogs` में `update, delete: if false;` लागू है।
- **VPS Hosting (`PRANAV/deployment/`)**: Ubuntu पर Node.js 20, `systemd` सर्विस, `nginx` रिवर्स प्रॉक्सी (`api.pranavsmm.shop`, `bot.pranavsmm.shop`), और `certbot` SSL स्क्रिप्ट्स पहले से उपलब्ध हैं।
- **Automated CI (`.github/workflows/pranav-ci.yml`)**: हर push पर Backend tests, E2E Contract tests, DEX + Sample APK build, Robolectric tests व Admin Debug APK आर्टिफ़ैक्ट्स बनाता है।
