package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Explicit opt-in; credentials stay in private, non-backed-up app preferences. */
public final class OccasionSettingsActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private SharedPreferences prefs;
    private TextView status;
    private EditText server;
    private Button pair;
    private volatile HttpURLConnection connection;
    private volatile boolean closed;
    private ScheduledFuture<?> poll;
    private String code, challenge, address;
    private long expires;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs = getSharedPreferences("occasions", 0);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(60), dp(35), dp(60), dp(35)); content.setBackgroundColor(0xFF111B19);
        ScrollView scroll = new ScrollView(this); scroll.addView(content); setContentView(scroll);
        label(content, "Occasion Studio", 30);
        label(content, "Schedule personal wallpapers, messages and effects from your phone. Home and screensaver only.", 16);
        server = new EditText(this); server.setSingleLine(); server.setHint("https://your-server.duckdns.org"); server.setText(prefs.getString("endpoint", ""));
        content.addView(server, new LinearLayout.LayoutParams(-1, dp(55)));
        Switch enabled = new Switch(this); enabled.setText("Allow scheduled occasions"); enabled.setTextSize(18);
        enabled.setChecked(prefs.getBoolean("enabled", false)); content.addView(enabled);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (checked && prefs.getString("token", "").isEmpty()) { enabled.setChecked(false); status.setText("Pair this TV first."); return; }
            prefs.edit().putBoolean("enabled", checked).apply();
        });
        status = label(content, prefs.getString("token", "").isEmpty() ? "Not paired" : "Paired. Occasions are checked when Home or the screensaver is visible.", 18);
        pair = button(content, "Pair with my studio", () -> {
            try { address = OccasionController.endpoint(server.getText().toString()); }
            catch (Exception invalid) { status.setText("Enter an HTTPS server address without a path."); return; }
            pair.setEnabled(false); status.setText("Requesting a pairing code…");
            worker.execute(() -> {
                try {
                    JSONObject result = request("/api/tv/pair", new JSONObject());
                    code = result.getString("code"); challenge = result.getString("challenge"); expires = android.os.SystemClock.elapsedRealtime() + 5 * 60000;
                    main.post(() -> { if (!closed) status.setText("Enter code " + code + " in the dashboard. It expires in five minutes."); });
                    poll = worker.scheduleWithFixedDelay(() -> {
                        if (android.os.SystemClock.elapsedRealtime() >= expires) { fail("Pairing expired. Request another code."); return; }
                        try {
                            JSONObject claim = request("/api/tv/claim", new JSONObject().put("code", code).put("challenge", challenge));
                            if (claim.optBoolean("pending")) return;
                            prefs.edit().putString("endpoint", address).putString("token", claim.getString("token"))
                                    .putString("device", claim.getString("id")).putBoolean("enabled", true).apply();
                            if (poll != null) poll.cancel(false);
                            main.post(() -> { if (!closed) { enabled.setChecked(true); pair.setEnabled(true); status.setText("Connected. Return Home to receive your occasions."); } });
                        } catch (Exception unavailable) { /* Retry until the five-minute window ends. */ }
                    }, 2, 5, TimeUnit.SECONDS);
                } catch (Exception unavailable) { fail("Could not reach your studio. Check its HTTPS address and connection."); }
            });
        });
        button(content, "Disconnect this TV", () -> {
            if (poll != null) poll.cancel(true);
            prefs.edit().clear().apply(); enabled.setChecked(false); pair.setEnabled(true); status.setText("Disconnected. Normal Home and screensaver remain enabled.");
            java.io.File folder = new java.io.File(getFilesDir(), "occasions");
            worker.execute(() -> { java.io.File[] files = folder.listFiles(); if (files != null) for (java.io.File file : files) file.delete(); });
        });
        label(content, "Event photos are temporary and use at most 16 MiB of TV disk space. Expired events are skipped. "
                + "Cloud changes arrive while Home/screensaver is visible; apps and video playback are never covered.", 14);
        button(content, "Done", this::finish);
    }
    private JSONObject request(String path, JSONObject json) throws Exception {
        HttpURLConnection active = (HttpURLConnection) new URL(address + path).openConnection(); connection = active;
        try {
            active.setInstanceFollowRedirects(false); active.setConnectTimeout(5000); active.setReadTimeout(8000);
            active.setRequestMethod("POST"); active.setDoOutput(true); active.setRequestProperty("Content-Type", "application/json");
            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8); active.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = active.getOutputStream()) { out.write(bytes); }
            if (active.getResponseCode() != 200) throw new java.io.IOException("Pairing failed");
            return new JSONObject(new String(OccasionController.read(active.getInputStream(), 8192), StandardCharsets.UTF_8));
        } finally { active.disconnect(); if (connection == active) connection = null; }
    }
    private void fail(String message) { if (poll != null) poll.cancel(false); main.post(() -> { if (!closed) { status.setText(message); pair.setEnabled(true); } }); }
    private TextView label(LinearLayout panel, String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(0xFFE1E9DB);
        view.setTypeface(getResources().getFont(R.font.outfit)); view.setPadding(0, 0, 0, dp(18)); panel.addView(view); return view;
    }
    private Button button(LinearLayout panel, String value, Runnable action) {
        Button view = new Button(this); view.setText(value); view.setAllCaps(false); view.setTypeface(getResources().getFont(R.font.outfit));
        panel.addView(view, new LinearLayout.LayoutParams(-1, dp(52))); view.setOnClickListener(v -> action.run()); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { closed = true; if (connection != null) connection.disconnect(); worker.shutdownNow(); main.removeCallbacksAndMessages(null); super.onDestroy(); }
}
