package com.pranav.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Persistent local cache for PRANAV Remote Config, Dynamic Modules,
 * License entitlement, and dismissed announcements so host apps remain
 * resilient offline without blocking startup.
 */
public final class PranavConfigCache {
    private static final String PREFS = "pranav_sdk_cache_v1";
    private static final String KEY_LAST_SESSION = "last_session_json";
    private static final String KEY_REMOTE_CONFIG = "remote_config_json";
    private static final String KEY_DYNAMIC_MODULES = "dynamic_modules_json";
    private static final String KEY_LICENSE = "license_json";
    private static final String KEY_LICENSE_KEY = "active_license_key";
    private static final String KEY_USER_ID = "bound_user_id";
    private static final String KEY_SYNCED_AT = "last_synced_ms";
    private static final String KEY_DISMISSED_ANNS = "dismissed_ann_ids";

    private PranavConfigCache() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveSession(Context context, JSONObject session) {
        if (context == null || session == null) return;
        SharedPreferences.Editor ed = prefs(context).edit();
        ed.putString(KEY_LAST_SESSION, session.toString());
        ed.putLong(KEY_SYNCED_AT, System.currentTimeMillis());

        JSONObject rc = session.optJSONObject("remoteConfig");
        if (rc != null) {
            ed.putString(KEY_REMOTE_CONFIG, rc.toString());
        }
        JSONArray mods = session.optJSONArray("dynamicModules");
        if (mods != null) {
            ed.putString(KEY_DYNAMIC_MODULES, mods.toString());
        }
        if (session.has("license")) {
            JSONObject lic = session.optJSONObject("license");
            if (lic != null) {
                ed.putString(KEY_LICENSE, lic.toString());
                String k = lic.optString("licenseKey", "");
                if (!k.isEmpty()) ed.putString(KEY_LICENSE_KEY, k);
            } else {
                ed.remove(KEY_LICENSE);
            }
        }
        ed.apply();
    }

    public static void saveRemoteConfig(Context context, JSONObject remoteConfig, JSONArray dynamicModules) {
        if (context == null) return;
        SharedPreferences.Editor ed = prefs(context).edit();
        if (remoteConfig != null) ed.putString(KEY_REMOTE_CONFIG, remoteConfig.toString());
        if (dynamicModules != null) ed.putString(KEY_DYNAMIC_MODULES, dynamicModules.toString());
        ed.putLong(KEY_SYNCED_AT, System.currentTimeMillis());
        ed.apply();
    }

    public static JSONObject getLastSession(Context context) {
        if (context == null) return null;
        String raw = prefs(context).getString(KEY_LAST_SESSION, null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject getRemoteConfig(Context context) {
        if (context == null) return new JSONObject();
        String raw = prefs(context).getString(KEY_REMOTE_CONFIG, null);
        if (raw == null || raw.isEmpty()) return new JSONObject();
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static boolean getFeatureFlag(Context context, String key, boolean fallback) {
        JSONObject rc = getRemoteConfig(context);
        JSONObject flags = rc.optJSONObject("featureFlags");
        if (flags != null && flags.has(key)) {
            return flags.optBoolean(key, fallback);
        }
        JSONObject values = rc.optJSONObject("values");
        if (values != null && values.has(key)) {
            return values.optBoolean(key, fallback);
        }
        return fallback;
    }

    public static String getRemoteString(Context context, String key, String fallback) {
        JSONObject rc = getRemoteConfig(context);
        JSONObject values = rc.optJSONObject("values");
        if (values != null && values.has(key)) {
            return values.optString(key, fallback);
        }
        return fallback;
    }

    public static long getRemoteLong(Context context, String key, long fallback) {
        JSONObject rc = getRemoteConfig(context);
        JSONObject values = rc.optJSONObject("values");
        if (values != null && values.has(key)) {
            return values.optLong(key, fallback);
        }
        return fallback;
    }

    public static JSONObject getDynamicModule(Context context, String moduleKey) {
        if (context == null || moduleKey == null) return null;
        String raw = prefs(context).getString(KEY_DYNAMIC_MODULES, null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item != null && moduleKey.equals(item.optString("moduleKey"))) {
                    return item;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static JSONObject getCachedLicense(Context context) {
        if (context == null) return null;
        String raw = prefs(context).getString(KEY_LICENSE, null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    public static void saveLicense(Context context, JSONObject lic) {
        if (context == null || lic == null) return;
        SharedPreferences.Editor ed = prefs(context).edit();
        ed.putString(KEY_LICENSE, lic.toString());
        String k = lic.optString("licenseKey", "");
        if (!k.isEmpty()) ed.putString(KEY_LICENSE_KEY, k);
        ed.apply();
    }

    public static String getSavedLicenseKey(Context context) {
        return context == null ? "" : prefs(context).getString(KEY_LICENSE_KEY, "");
    }

    public static void setUserId(Context context, String userId) {
        if (context == null) return;
        prefs(context).edit().putString(KEY_USER_ID, userId == null ? "" : userId.trim()).apply();
    }

    public static String getUserId(Context context) {
        return context == null ? "" : prefs(context).getString(KEY_USER_ID, "");
    }

    public static void markAnnouncementDismissed(Context context, String id) {
        if (context == null || id == null || id.isEmpty()) return;
        Set<String> set = new HashSet<>(prefs(context).getStringSet(KEY_DISMISSED_ANNS, new HashSet<>()));
        set.add(id);
        prefs(context).edit().putStringSet(KEY_DISMISSED_ANNS, set).apply();
    }

    public static boolean isAnnouncementDismissed(Context context, String id) {
        if (context == null || id == null || id.isEmpty()) return false;
        Set<String> set = prefs(context).getStringSet(KEY_DISMISSED_ANNS, null);
        return set != null && set.contains(id);
    }

    public static long getLastSyncedAtMs(Context context) {
        return context == null ? 0L : prefs(context).getLong(KEY_SYNCED_AT, 0L);
    }
}
