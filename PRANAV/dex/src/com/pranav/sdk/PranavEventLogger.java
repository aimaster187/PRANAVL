package com.pranav.sdk;

import android.content.Context;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class PranavEventLogger {
    private static final String TAG = "PranavEventLogger";
    private static final ExecutorService LOG_EXECUTOR = Executors.newSingleThreadExecutor();

    private PranavEventLogger() {}

    public static void logAsync(
            final Context context,
            final String baseUrl,
            final String appCode,
            final String category,
            final String title,
            final String message,
            final String severity
    ) {
        if (context == null || baseUrl == null || baseUrl.trim().isEmpty()) return;
        final Context appCtx = context.getApplicationContext();
        LOG_EXECUTOR.execute(() -> {
            try {
                PranavApiClient client = new PranavApiClient(baseUrl);
                JSONObject payload = new JSONObject();
                payload.put("appCode", appCode);
                payload.put("deviceId", PranavDeviceIdentity.getOrCreateInstallUuid(appCtx));
                payload.put("userId", PranavConfigCache.getUserId(appCtx));
                payload.put("category", category);
                payload.put("title", title);
                payload.put("message", message);
                payload.put("severity", severity);
                client.postJson("/api/v1/client/events", payload);
            } catch (Exception e) {
                Log.w(TAG, "Event dispatch skipped: " + e.getMessage());
            }
        });
    }
}
