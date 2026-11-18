package com.pranav.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * PRANAV Universal DEX SDK Entrypoint (v1.4.0).
 * Never runs automatically on DEX copy; must be initialized via a separate Hook.
 * Automatically tracks resumed Activity via ActivityLifecycleCallbacks so
 * asynchronous Remote Config, Dialogs, Updates, and Bans render reliably.
 */
public final class PranavSdk {
    private static final String TAG = "PranavSdk";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean LIFECYCLE_REGISTERED = new AtomicBoolean(false);
    private static final List<SessionListener> LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile String configuredBaseUrl = "";
    private static volatile String configuredAppCode = "";
    private static volatile JSONObject lastHandshake = null;
    private static volatile WeakReference<Activity> currentActivityRef = new WeakReference<>(null);

    public interface SessionListener {
        void onSessionUpdated(JSONObject session, boolean fromCache);
    }

    public interface LicenseCallback {
        void onResult(boolean ok, JSONObject response);
    }

    private PranavSdk() {}

    public static void init(Context context, String appCode, String baseUrl) {
        initWithEntryPoint(context, appCode, baseUrl, "DIRECT_INIT");
    }

    public static void initWithEntryPoint(Context context, String appCode, String baseUrl, String entryPoint) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();

        if (!PranavInitGuard.tryAcquire(appCode, entryPoint)) {
            Log.w(TAG, "Duplicate PRANAV init ignored (already initialized at "
                    + PranavInitGuard.getInitEntryPoint() + ")");
            if (context instanceof Activity) {
                currentActivityRef = new WeakReference<>((Activity) context);
            }
            return;
        }

        configuredAppCode = appCode == null ? "" : appCode;
        configuredBaseUrl = baseUrl == null ? "" : baseUrl;

        // Restore cached session immediately so offline startup still enforces cached state
        JSONObject cached = PranavConfigCache.getLastSession(appCtx);
        if (cached != null) {
            lastHandshake = cached;
            notifyListeners(cached, true);
        }

        if (context instanceof Activity) {
            currentActivityRef = new WeakReference<>((Activity) context);
        }
        registerLifecycleCallbacks(appCtx);
        EXECUTOR.execute(() -> performHandshake(appCtx));
    }

    private static void registerLifecycleCallbacks(Context appCtx) {
        if (!(appCtx instanceof Application)) return;
        if (!LIFECYCLE_REGISTERED.compareAndSet(false, true)) return;
        ((Application) appCtx).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                currentActivityRef = new WeakReference<>(activity);
            }

            @Override
            public void onActivityStarted(Activity activity) {
                currentActivityRef = new WeakReference<>(activity);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                currentActivityRef = new WeakReference<>(activity);
                if (lastHandshake != null) {
                    checkSessionAndRenderDialogs(activity);
                }
            }

            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override
            public void onActivityDestroyed(Activity activity) {
                Activity cur = currentActivityRef.get();
                if (cur == activity) {
                    currentActivityRef.clear();
                }
            }
        });
    }

    public static void syncSessionNow(Context context) {
        if (context == null || configuredBaseUrl.isEmpty()) return;
        final Context appCtx = context.getApplicationContext();
        EXECUTOR.execute(() -> performHandshake(appCtx));
    }

    public static void syncRemoteConfig(Context context) {
        if (context == null || configuredBaseUrl.isEmpty()) return;
        final Context appCtx = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                PranavApiClient client = new PranavApiClient(configuredBaseUrl);
                PackageInfo pkg = appCtx.getPackageManager().getPackageInfo(appCtx.getPackageName(), 0);
                JSONObject req = new JSONObject();
                req.put("appCode", configuredAppCode);
                req.put("packageName", appCtx.getPackageName());
                req.put("deviceId", PranavDeviceIdentity.getOrCreateInstallUuid(appCtx));
                req.put("userId", PranavConfigCache.getUserId(appCtx));
                req.put("versionName", pkg.versionName != null ? pkg.versionName : "1.0.0");
                JSONObject res = client.postJson("/api/v1/client/config", req);
                if (res.optBoolean("ok", false)) {
                    PranavConfigCache.saveRemoteConfig(
                            appCtx,
                            res.optJSONObject("remoteConfig"),
                            res.optJSONArray("dynamicModules")
                    );
                    if (lastHandshake != null) {
                        if (res.has("remoteConfig")) lastHandshake.put("remoteConfig", res.optJSONObject("remoteConfig"));
                        if (res.has("dynamicModules")) lastHandshake.put("dynamicModules", res.optJSONArray("dynamicModules"));
                        notifyListeners(lastHandshake, false);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Remote config sync skipped: " + e.getMessage());
            }
        });
    }

    private static void performHandshake(Context appCtx) {
        try {
            if (configuredBaseUrl.isEmpty()) return;
            PranavApiClient client = new PranavApiClient(configuredBaseUrl);
            String uuid = PranavDeviceIdentity.getOrCreateInstallUuid(appCtx);
            String keyHash = "";
            String spki = "";
            try {
                keyHash = PranavDeviceIdentity.getPublicKeySha256Hex();
                spki = PranavDeviceIdentity.getPublicKeySpkiBase64();
            } catch (Exception ksErr) {
                Log.w(TAG, "Keystore unavailable, continuing with UUID identity: " + ksErr.getMessage());
            }

            PackageInfo pkg = appCtx.getPackageManager().getPackageInfo(appCtx.getPackageName(), 0);
            String currentVer = pkg.versionName != null ? pkg.versionName : "1.0.0";
            long currentVerCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pkg.getLongVersionCode() : pkg.versionCode;

            JSONObject challengeReq = new JSONObject();
            challengeReq.put("appCode", configuredAppCode);
            challengeReq.put("packageName", appCtx.getPackageName());
            challengeReq.put("deviceId", uuid);
            challengeReq.put("installUuid", uuid);

            JSONObject challengeRes = client.postJson("/api/v1/client/challenge", challengeReq);
            String nonce = challengeRes.optString("nonce", challengeRes.optString("challenge", ""));
            if (!nonce.isEmpty()) {
                JSONObject sessionReq = new JSONObject();
                sessionReq.put("appCode", configuredAppCode);
                sessionReq.put("packageName", appCtx.getPackageName());
                sessionReq.put("deviceId", uuid);
                sessionReq.put("installUuid", uuid);
                sessionReq.put("userId", PranavConfigCache.getUserId(appCtx));
                sessionReq.put("deviceModel", PranavDeviceIdentity.getDeviceLabel());
                sessionReq.put("androidApi", Build.VERSION.SDK_INT);
                sessionReq.put("versionName", currentVer);
                sessionReq.put("versionCode", currentVerCode);
                if (!spki.isEmpty()) {
                    try {
                        String signature = PranavDeviceIdentity.signChallenge(nonce);
                        sessionReq.put("keystoreHash", keyHash);
                        sessionReq.put("keystorePublicKeyB64", spki);
                        sessionReq.put("challengeNonce", nonce);
                        sessionReq.put("challengeSignatureB64", signature);
                    } catch (Exception signErr) {
                        Log.w(TAG, "Challenge signing skipped: " + signErr.getMessage());
                    }
                }

                JSONObject sessionRes = client.postJson("/api/v1/client/session", sessionReq);
                lastHandshake = sessionRes;
                PranavConfigCache.saveSession(appCtx, sessionRes);
                PranavDialogEngine.clearSessionShownKeys();
                notifyListeners(sessionRes, false);

                // Auto-render any pending dialogs or announcements on the active resumed Activity
                Activity active = currentActivityRef.get();
                if (active != null && !active.isFinishing() && !active.isDestroyed()) {
                    checkSessionAndRenderDialogs(active);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "PRANAV handshake failed: " + e.getMessage());
        }
    }

    public static void setUserId(Context context, String userId) {
        if (context == null) return;
        PranavConfigCache.setUserId(context, userId);
        syncSessionNow(context);
    }

    public static void activateLicense(final Context context, final String licenseKey, final LicenseCallback callback) {
        if (context == null || licenseKey == null || configuredBaseUrl.isEmpty()) {
            if (callback != null) callback.onResult(false, new JSONObject());
            return;
        }
        final Context appCtx = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                PranavApiClient client = new PranavApiClient(configuredBaseUrl);
                JSONObject req = new JSONObject();
                req.put("appCode", configuredAppCode);
                req.put("licenseKey", licenseKey.trim());
                req.put("deviceId", PranavDeviceIdentity.getOrCreateInstallUuid(appCtx));
                req.put("userId", PranavConfigCache.getUserId(appCtx));
                JSONObject res = client.postJson("/api/v1/client/license/activate", req);
                boolean ok = res.optBoolean("ok", false);
                if (ok) {
                    res.put("licenseKey", licenseKey.trim());
                    PranavConfigCache.saveLicense(appCtx, res);
                    if (lastHandshake != null) {
                        lastHandshake.put("license", res);
                        notifyListeners(lastHandshake, false);
                    }
                }
                if (callback != null) callback.onResult(ok, res);
            } catch (Exception e) {
                if (callback != null) {
                    JSONObject err = new JSONObject();
                    try { err.put("error", e.getMessage()); } catch (Exception ignored) {}
                    callback.onResult(false, err);
                }
            }
        });
    }

    public static void verifyLicense(final Context context, final String licenseKey, final LicenseCallback callback) {
        if (context == null || configuredBaseUrl.isEmpty()) {
            if (callback != null) callback.onResult(false, new JSONObject());
            return;
        }
        final Context appCtx = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                PranavApiClient client = new PranavApiClient(configuredBaseUrl);
                JSONObject req = new JSONObject();
                req.put("appCode", configuredAppCode);
                req.put("licenseKey", licenseKey == null ? PranavConfigCache.getSavedLicenseKey(appCtx) : licenseKey.trim());
                req.put("deviceId", PranavDeviceIdentity.getOrCreateInstallUuid(appCtx));
                JSONObject res = client.postJson("/api/v1/client/license/verify", req);
                boolean valid = res.optBoolean("valid", false);
                if (valid) {
                    PranavConfigCache.saveLicense(appCtx, res);
                }
                if (callback != null) callback.onResult(valid, res);
            } catch (Exception e) {
                if (callback != null) callback.onResult(false, new JSONObject());
            }
        });
    }

    public static void ackAnnouncement(final Context context, final String announcementId, final boolean dismissed) {
        if (context == null || announcementId == null || announcementId.isEmpty()) return;
        final Context appCtx = context.getApplicationContext();
        if (dismissed) {
            PranavConfigCache.markAnnouncementDismissed(appCtx, announcementId);
        }
        if (configuredBaseUrl.isEmpty()) return;
        EXECUTOR.execute(() -> {
            try {
                PranavApiClient client = new PranavApiClient(configuredBaseUrl);
                JSONObject body = new JSONObject();
                body.put("announcementId", announcementId);
                body.put("deviceId", PranavDeviceIdentity.getOrCreateInstallUuid(appCtx));
                body.put("userId", PranavConfigCache.getUserId(appCtx));
                body.put("dismissed", dismissed);
                client.postJson("/api/v1/client/announcements/ack", body);
            } catch (Exception e) {
                Log.w(TAG, "Announcement ack skipped: " + e.getMessage());
            }
        });
    }

    public static void confirmUpdateInstalled(final Context context, final String installedVersion) {
        if (context == null || configuredBaseUrl.isEmpty()) return;
        final Context appCtx = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                PranavApiClient client = new PranavApiClient(configuredBaseUrl);
                String uuid = PranavDeviceIdentity.getOrCreateInstallUuid(appCtx);
                JSONObject body = new JSONObject();
                body.put("appCode", configuredAppCode);
                body.put("deviceId", uuid);
                body.put("installedVersion", installedVersion);
                client.postJson("/api/v1/client/update-confirm", body);
            } catch (Exception e) {
                Log.e(TAG, "Update confirmation failed: " + e.getMessage());
            }
        });
    }

    public static void checkSessionAndRenderDialogs(final Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        currentActivityRef = new WeakReference<>(activity);
        JSONObject session = lastHandshake != null ? lastHandshake : PranavConfigCache.getLastSession(activity);
        if (session == null) return;

        JSONObject updateDialog = session.optJSONObject("updateDialog");
        if (updateDialog != null) {
            PranavDialogEngine.render(activity, updateDialog);
        } else {
            JSONArray dialogs = session.optJSONArray("dialogs");
            if (dialogs != null && dialogs.length() > 0) {
                PranavDialogEngine.render(activity, dialogs.optJSONObject(0));
            }
        }

        JSONArray announcements = session.optJSONArray("announcements");
        if (announcements != null && announcements.length() > 0) {
            PranavDialogEngine.renderAnnouncement(activity, announcements.optJSONObject(0));
        }
    }

    public static void logEvent(Context context, String category, String title, String message, String severity) {
        PranavEventLogger.logAsync(context, configuredBaseUrl, configuredAppCode, category, title, message, severity);
    }

    public static boolean getFeatureFlag(Context context, String key, boolean fallback) {
        return PranavConfigCache.getFeatureFlag(context, key, fallback);
    }

    public static String getRemoteString(Context context, String key, String fallback) {
        return PranavConfigCache.getRemoteString(context, key, fallback);
    }

    public static long getRemoteLong(Context context, String key, long fallback) {
        return PranavConfigCache.getRemoteLong(context, key, fallback);
    }

    public static JSONObject getDynamicModule(Context context, String moduleKey) {
        return PranavConfigCache.getDynamicModule(context, moduleKey);
    }

    public static boolean isVipActive(Context context) {
        JSONObject lic = PranavConfigCache.getCachedLicense(context);
        if (lic == null) return false;
        String status = lic.optString("status", "");
        String tier = lic.optString("planTier", "");
        long exp = lic.optLong("expiresAtEpochMs", 0L);
        boolean notExpired = exp == 0L || System.currentTimeMillis() <= exp;
        return "ACTIVE".equalsIgnoreCase(status) && tier.startsWith("VIP") && notExpired;
    }

    public static void addSessionListener(SessionListener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
            if (lastHandshake != null) {
                listener.onSessionUpdated(lastHandshake, false);
            }
        }
    }

    public static void removeSessionListener(SessionListener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyListeners(JSONObject session, boolean fromCache) {
        for (SessionListener listener : LISTENERS) {
            try {
                listener.onSessionUpdated(session, fromCache);
            } catch (Exception ignored) {}
        }
    }

    public static JSONObject getLastHandshake() {
        return lastHandshake;
    }

    public static JSONObject getDiagnostics(Context context) {
        JSONObject diag = PranavDiagnostics.collect(context, configuredAppCode);
        try {
            diag.put("backendBaseUrl", configuredBaseUrl);
            diag.put("remoteConfigVersion", PranavConfigCache.getRemoteConfig(context).optLong("configVersion", 0L));
            diag.put("lastSyncedAtMs", PranavConfigCache.getLastSyncedAtMs(context));
            diag.put("vipActive", isVipActive(context));
        } catch (Exception ignored) {}
        return diag;
    }
}
