package com.pranav.sdk;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/**
 * Real APK download & installer trigger for PRANAV Normal / Force Updates.
 * Checks unknown-sources install permission on Android 8+ (API 26+) and
 * falls back gracefully to browser ACTION_VIEW if DownloadManager is unavailable.
 */
public final class PranavUpdateManager {
    private static final String TAG = "PranavUpdateManager";

    private PranavUpdateManager() {}

    public static void startUpdateFlow(final Activity activity, final String downloadUrl, final String versionLabel) {
        if (activity == null || downloadUrl == null || downloadUrl.trim().isEmpty()) return;
        final String cleanUrl = downloadUrl.trim();
        final Context appCtx = activity.getApplicationContext();

        if (!cleanUrl.endsWith(".apk")) {
            openExternalUrl(activity, cleanUrl);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !appCtx.getPackageManager().canRequestPackageInstalls()) {
            try {
                Toast.makeText(activity, "Allow install from this source to apply update v" + versionLabel, Toast.LENGTH_LONG).show();
                Intent permIntent = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + appCtx.getPackageName())
                );
                activity.startActivity(permIntent);
            } catch (Exception e) {
                openExternalUrl(activity, cleanUrl);
            }
            return;
        }

        try {
            DownloadManager dm = (DownloadManager) appCtx.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                openExternalUrl(activity, cleanUrl);
                return;
            }
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(cleanUrl));
            req.setTitle("Downloading Update " + (versionLabel != null ? versionLabel : ""));
            req.setDescription("PRANAV Managed Update");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(appCtx, Environment.DIRECTORY_DOWNLOADS, "pranav_update_" + versionLabel + ".apk");
            final long downloadId = dm.enqueue(req);
            Toast.makeText(activity, "Downloading update " + versionLabel + "...", Toast.LENGTH_SHORT).show();

            PranavSdk.logEvent(appCtx, "UPDATE", "UPDATE_DOWNLOAD_STARTED", "v" + versionLabel, "INFO");

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    long finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                    if (finishedId != downloadId) return;
                    try {
                        ctx.unregisterReceiver(this);
                    } catch (Exception ignored) {}
                    try {
                        Uri apkUri = dm.getUriForDownloadedFile(downloadId);
                        if (apkUri != null) {
                            Intent installIntent = new Intent(Intent.ACTION_VIEW);
                            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            ctx.startActivity(installIntent);
                            PranavSdk.logEvent(ctx, "UPDATE", "UPDATE_INSTALL_PROMPTED", "v" + versionLabel, "INFO");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Installer launch failed: " + e.getMessage());
                        openExternalUrl(activity, cleanUrl);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appCtx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                appCtx.registerReceiver(receiver, filter);
            }
        } catch (Exception e) {
            Log.w(TAG, "DownloadManager unavailable, opening external URL: " + e.getMessage());
            openExternalUrl(activity, cleanUrl);
        }
    }

    public static void openExternalUrl(Activity activity, String url) {
        if (activity == null || url == null || url.trim().isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed opening URL: " + e.getMessage());
        }
    }
}
