package com.pranav.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;

/**
 * Universal Dialog & Announcement Engine inside the PRANAV DEX runtime.
 * Supports blocking FORCE_UPDATE / SECURITY_LOCK / MAINTENANCE dialogs,
 * multi-action buttons, and BANNER / TOAST / POPUP / NOTIFICATION announcements.
 */
public final class PranavDialogEngine {
    private static final String TAG_BANNER_VIEW = "pranav_runtime_banner";
    private static final Set<String> SHOWN_NON_BLOCKING_KEYS =
            Collections.synchronizedSet(new HashSet<>());

    private PranavDialogEngine() {}

    public static void render(final Activity activity, final JSONObject dialogJson) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || dialogJson == null) return;
        if (!dialogJson.optBoolean("enabled", true)) return;

        final String type = dialogJson.optString("dialogType", "ANNOUNCEMENT");
        final boolean forceUpdate = "FORCE_UPDATE".equalsIgnoreCase(type)
                || dialogJson.optBoolean("forceUpdateEnabled", false);
        final boolean forceBlock = forceUpdate
                || "SECURITY_LOCK".equalsIgnoreCase(type)
                || "MAINTENANCE".equalsIgnoreCase(type)
                || dialogJson.optBoolean("forceDialog", false);
        final boolean dismissible = !forceBlock && dialogJson.optBoolean("dismissible", true);
        final boolean laterAllowed = !forceBlock && dialogJson.optBoolean("laterAllowed", true);

        final String dialogId = dialogJson.optString("id", type + ":" + dialogJson.optString("title", ""));
        if (!forceBlock && !SHOWN_NON_BLOCKING_KEYS.add(dialogId)) {
            return;
        }

        final String title = dialogJson.optString("title", "PRANAV Notice");
        final String whatsNew = dialogJson.optString("whatsNew", "");
        final String baseDesc = dialogJson.optString("description", "");
        final String description = whatsNew.isEmpty() ? baseDesc : (baseDesc + "\n\nWhat's New:\n" + whatsNew);
        final String primaryBtn = dialogJson.optString("primaryButtonText", forceUpdate ? "Update Now" : "Continue");
        final String primaryActionType = dialogJson.optString("primaryActionType", "OPEN_URL");
        final String primaryUrl = dialogJson.optString("primaryActionUrl", "");
        final String secondaryBtn = dialogJson.optString("secondaryButtonText", "Later");
        final String latestVer = dialogJson.optString("latestVersion", "");

        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(description)
                    .setCancelable(dismissible)
                    .setPositiveButton(primaryBtn, null);

            if (laterAllowed) {
                builder.setNegativeButton(secondaryBtn, (d, which) -> d.dismiss());
            }

            final AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(dismissible);
            dialog.setOnCancelListener(d -> {
                if (forceBlock) {
                    // Keep blocking enforcement intact
                    render(activity, dialogJson);
                }
            });
            dialog.show();

            Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (positive != null) {
                positive.setOnClickListener(v -> {
                    executeAction(activity, primaryActionType, primaryUrl, latestVer);
                    PranavSdk.logEvent(activity, "DIALOG", "DIALOG_PRIMARY_CLICK", title, "INFO");
                    if (!forceBlock) {
                        dialog.dismiss();
                    }
                });
            }
            PranavSdk.logEvent(activity, "DIALOG", "DIALOG_SHOWN", type + ": " + title, "INFO");
        });
    }

    public static void renderAnnouncement(final Activity activity, final JSONObject annJson) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || annJson == null) return;
        if (!annJson.optBoolean("enabled", true)) return;

        final String id = annJson.optString("id", "");
        if (!id.isEmpty() && PranavConfigCache.isAnnouncementDismissed(activity, id)) return;
        if (!id.isEmpty() && !SHOWN_NON_BLOCKING_KEYS.add("ann:" + id)) return;

        final String displayType = annJson.optString("displayType", "POPUP");
        final String title = annJson.optString("title", "Announcement");
        final String message = annJson.optString("message", "");
        final String actionText = annJson.optString("actionText", "OK");
        final String actionUrl = annJson.optString("actionUrl", "");
        final boolean dismissible = annJson.optBoolean("dismissible", true);

        if ("TOAST".equalsIgnoreCase(displayType)) {
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, title + ": " + message, Toast.LENGTH_LONG).show();
                PranavSdk.ackAnnouncement(activity, id, true);
            });
            return;
        }

        if ("BANNER".equalsIgnoreCase(displayType)) {
            activity.runOnUiThread(() -> attachTopBanner(activity, id, title, message, actionText, actionUrl, dismissible));
            return;
        }

        // POPUP or NOTIFICATION fallback inside running Activity
        JSONObject asDialog = new JSONObject();
        try {
            asDialog.put("id", "ann_" + id);
            asDialog.put("dialogType", "ANNOUNCEMENT");
            asDialog.put("title", title);
            asDialog.put("description", message);
            asDialog.put("primaryButtonText", actionText);
            asDialog.put("primaryActionUrl", actionUrl);
            asDialog.put("dismissible", dismissible);
            asDialog.put("laterAllowed", dismissible);
        } catch (Exception ignored) {}
        render(activity, asDialog);
        if (!id.isEmpty()) {
            PranavSdk.ackAnnouncement(activity, id, false);
        }
    }

    private static void attachTopBanner(
            final Activity activity,
            final String annId,
            final String title,
            final String message,
            final String actionText,
            final String actionUrl,
            final boolean dismissible
    ) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        if (content.findViewWithTag(TAG_BANNER_VIEW) != null) return;

        final LinearLayout banner = new LinearLayout(activity);
        banner.setTag(TAG_BANNER_VIEW);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setBackgroundColor(Color.parseColor("#1E1B4B"));
        int pad = dp(activity, 14);
        banner.setPadding(pad, pad, pad, pad);

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15f);
        banner.addView(titleView);

        if (!message.isEmpty()) {
            TextView msgView = new TextView(activity);
            msgView.setText(message);
            msgView.setTextColor(Color.parseColor("#CBD5E1"));
            msgView.setTextSize(13f);
            msgView.setPadding(0, dp(activity, 4), 0, dp(activity, 8));
            banner.addView(msgView);
        }

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);

        if (actionUrl != null && !actionUrl.isEmpty()) {
            Button openBtn = new Button(activity);
            openBtn.setText(actionText.isEmpty() ? "Open" : actionText);
            openBtn.setOnClickListener(v -> {
                PranavUpdateManager.openExternalUrl(activity, actionUrl);
                PranavSdk.ackAnnouncement(activity, annId, true);
                content.removeView(banner);
            });
            actions.addView(openBtn);
        }

        if (dismissible) {
            Button dismissBtn = new Button(activity);
            dismissBtn.setText("Dismiss");
            dismissBtn.setOnClickListener(v -> {
                PranavSdk.ackAnnouncement(activity, annId, true);
                content.removeView(banner);
            });
            actions.addView(dismissBtn);
        }

        banner.addView(actions);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        int margin = dp(activity, 8);
        lp.setMargins(margin, margin, margin, 0);
        content.addView(banner, lp);
        PranavSdk.ackAnnouncement(activity, annId, false);
    }

    private static void executeAction(Activity activity, String actionType, String actionUrl, String versionLabel) {
        if ("DOWNLOAD_APK".equalsIgnoreCase(actionType) || (actionUrl != null && actionUrl.endsWith(".apk"))) {
            PranavUpdateManager.startUpdateFlow(activity, actionUrl, versionLabel);
            return;
        }
        if ("CONFIRM_UPDATE".equalsIgnoreCase(actionType)) {
            if (!versionLabel.isEmpty()) {
                PranavSdk.confirmUpdateInstalled(activity, versionLabel);
            }
            return;
        }
        if ("COPY_TEXT".equalsIgnoreCase(actionType) && actionUrl != null && !actionUrl.isEmpty()) {
            ClipboardManager cb = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb != null) {
                cb.setPrimaryClip(ClipData.newPlainText("PRANAV", actionUrl));
                Toast.makeText(activity, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (actionUrl != null && !actionUrl.isEmpty()) {
            PranavUpdateManager.openExternalUrl(activity, actionUrl);
        }
    }

    private static int dp(Context ctx, int value) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    public static void clearSessionShownKeys() {
        SHOWN_NON_BLOCKING_KEYS.clear();
    }
}
