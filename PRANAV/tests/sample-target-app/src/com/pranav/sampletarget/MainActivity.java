package com.pranav.sampletarget;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.pranav.integration.hook.PranavActivityHook;
import com.pranav.sdk.PranavSdk;
import org.json.JSONObject;

public class MainActivity extends Activity implements PranavSdk.SessionListener {
    private TextView statusView;
    private TextView configView;
    private TextView diagView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView header = new TextView(this);
        header.setText("PRANAV Authorized Target Host App");
        header.setTextSize(18f);
        root.addView(header);

        statusView = new TextView(this);
        root.addView(statusView);

        configView = new TextView(this);
        root.addView(configView);

        final EditText licenseInput = new EditText(this);
        licenseInput.setHint("Enter License Key (e.g. PRNV-PRO-...)");
        root.addView(licenseInput);

        Button activateBtn = new Button(this);
        activateBtn.setText("Activate License & Verify VIP");
        activateBtn.setOnClickListener(v -> {
            String key = licenseInput.getText().toString().trim();
            PranavSdk.activateLicense(this, key, (ok, resp) -> runOnUiThread(() -> {
                statusView.setText("License activation ok=" + ok + " | " + resp.toString());
                refreshUi(PranavSdk.getLastHandshake(), false);
            }));
        });
        root.addView(activateBtn);

        Button syncBtn = new Button(this);
        syncBtn.setText("Sync PRANAV Remote Config & Session");
        syncBtn.setOnClickListener(v -> {
            PranavSdk.syncSessionNow(this);
            PranavSdk.syncRemoteConfig(this);
        });
        root.addView(syncBtn);

        diagView = new TextView(this);
        root.addView(diagView);

        scroll.addView(root);
        setContentView(scroll);

        PranavSdk.addSessionListener(this);
        refreshUi(PranavSdk.getLastHandshake(), true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PranavActivityHook.onActivityResumed(this);
        refreshUi(PranavSdk.getLastHandshake(), false);
    }

    @Override
    protected void onDestroy() {
        PranavSdk.removeSessionListener(this);
        super.onDestroy();
    }

    @Override
    public void onSessionUpdated(final JSONObject session, final boolean fromCache) {
        runOnUiThread(() -> refreshUi(session, fromCache));
    }

    private void refreshUi(JSONObject session, boolean fromCache) {
        String welcome = PranavSdk.getRemoteString(this, "promoText", "Ready (Remote Config default)");
        boolean showcase = PranavSdk.getFeatureFlag(this, "vipShowcase", false);
        JSONObject mod = PranavSdk.getDynamicModule(this, "dynamic_vip_paywall");
        boolean vip = PranavSdk.isVipActive(this);

        statusView.setText(
                "\nVIP Active: " + vip
                        + " | Source: " + (fromCache ? "Offline Cache" : "Live Backend")
                        + (session != null ? " | Allowed: " + session.optBoolean("allowed", true) : "")
        );
        configView.setText(
                "\nRemote Config promoText: " + welcome
                        + "\nFeature Flag vipShowcase: " + showcase
                        + "\nDynamic Module (dynamic_vip_paywall): " + (mod != null ? mod.toString() : "none")
        );
        diagView.setText("\nDiagnostics:\n" + PranavSdk.getDiagnostics(this).toString());
    }
}
