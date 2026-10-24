package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Remote-accessible preferences; location is chosen explicitly, never inferred. */
public final class DreamSettingsActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private DreamOnlineSource source;
    private SharedPreferences prefs;
    private LinearLayout content;
    private Button cityButton;
    private boolean searching;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("dream", 0);
        source = new DreamOnlineSource(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(64), dp(32), dp(64), dp(32));
        content.setBackgroundColor(0xFF10151F);
        scroll.addView(content); setContentView(scroll);
        label("Wallpaper + clock", 30);
        button("Cloud occasions · wallpapers & messages", () -> startActivity(new android.content.Intent(this, OccasionSettingsActivity.class)));
        label("Hindu temples and festivals • fresh online photography", 17);
        cityButton = button(cityLabel(), this::chooseCity);
        button("Photo collection", () -> {
            String[] values = {"both", "temples", "festivals"};
            String selected = prefs.getString("theme", "both");
            int index = java.util.Arrays.asList(values).indexOf(selected);
            new AlertDialog.Builder(this).setTitle("Photo collection")
                    .setSingleChoiceItems(new String[]{"Temples + festivals", "Temples", "Festivals"}, index,
                            (dialog, which) -> { prefs.edit().putString("theme", values[which]).apply(); dialog.dismiss(); })
                    .setNegativeButton("Cancel", null).show();
        });
        button("Photo credits", this::credits);
        label("Discovers fresh reviewed photographs from Wikimedia Commons and keeps the next three ready. "
                + "Recent photos and the queue are remembered across restarts. Nine built-in photos cover slow or offline connections; "
                + "online downloads use up to 8 MiB of cache.", 14);
        label("Weather: Open-Meteo (CC BY 4.0). City lookup: GeoNames via Open-Meteo. "
                + "No GPS access. Weather refreshes every 15 minutes during the screensaver; old data is labelled cached.", 14);
        label("Choose Wallpaper + clock in the TV's screensaver selector. Your existing inactivity timing is preserved. "
                + "Press any remote button to return. Photo/clock work stops when you leave.", 14);
        button("Done", this::finish);
        cityButton.requestFocus();
    }

    private String cityLabel() { return "Weather city: " + prefs.getString("city", "Not set") + " · °C"; }

    private void chooseCity() {
        if (searching) return;
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setHint("City name");
        input.setText(prefs.getString("city", ""));
        new AlertDialog.Builder(this).setTitle("Choose weather city").setView(input)
                .setPositiveButton("Search", (dialog, which) -> search(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null).show();
    }

    private void search(String query) {
        if (query.isEmpty() || query.length() > 100) return;
        searching = true; cityButton.setText("Searching cities...");
        worker.execute(() -> {
            JSONArray results = null;
            try {
                String url = "https://geocoding-api.open-meteo.com/v1/search?name="
                        + URLEncoder.encode(query, "UTF-8") + "&count=10&language=en&format=json";
                results = new JSONObject(new String(source.fetch(url, 128 * 1024), StandardCharsets.UTF_8)).optJSONArray("results");
            } catch (Exception ignored) {}
            final JSONArray found = results;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                searching = false; cityButton.setText(cityLabel());
                if (found == null || found.length() == 0) {
                    Toast.makeText(this, "No cities found. Check the connection or spelling.", Toast.LENGTH_LONG).show(); return;
                }
                String[] labels = new String[found.length()];
                for (int i = 0; i < labels.length; i++) {
                    JSONObject item = found.optJSONObject(i);
                    labels[i] = item.optString("name") + ", " + item.optString("admin1") + ", " + item.optString("country");
                }
                new AlertDialog.Builder(this).setTitle("Select your city").setItems(labels, (dialog, which) -> {
                    JSONObject city = found.optJSONObject(which);
                    prefs.edit().putString("city", city.optString("name"))
                            .putString("latitude", city.optString("latitude"))
                            .putString("longitude", city.optString("longitude")).apply();
                    cityButton.setText(cityLabel());
                }).setNegativeButton("Cancel", null).show();
            });
        });
    }

    private void credits() {
        try {
            StringBuilder text = new StringBuilder();
            java.util.List<JSONObject> photos = new java.util.ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            // Include all built-ins and the current/queued online photographs.
            try (java.io.InputStream input = getAssets().open("heritage-wallpapers.json")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                JSONArray all = new JSONArray(bytes.toString("UTF-8"));
                for (int i = 0; i < all.length(); i++) photos.add(all.getJSONObject(i));
            }
            JSONObject feed = DreamPhotoFeed.readState(new android.util.AtomicFile(
                    new java.io.File(getFilesDir(), "dream-feed.json")));
            if (feed != null) {
                JSONObject current = feed.optJSONObject("lastShown");
                if (current != null) photos.add(current);
                JSONArray queued = feed.optJSONArray("ready");
                if (queued != null) for (int i = 0; i < queued.length(); i++) photos.add(queued.getJSONObject(i));
            }
            for (JSONObject item : photos) {
                if (!seen.add(DreamPhotoFeed.key(item))) continue;
                text.append(item.getString("title")).append('\n').append(item.getString("author"))
                        .append(" • ").append(item.getString("license")).append('\n')
                        .append(item.getString("source")).append('\n').append(item.getString("licenseUrl"))
                        .append("\nDisplayed with a centre crop; colour unchanged.\n\n");
            }
            ScrollView scroll = new ScrollView(this);
            TextView credits = new TextView(this);
            credits.setTextSize(14); credits.setPadding(dp(20), dp(16), dp(20), dp(16));
            credits.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
            credits.setText(text);
            scroll.addView(credits);
            new AlertDialog.Builder(this).setTitle("Photographs & licences").setView(scroll)
                    .setPositiveButton("Close", null).show();
        } catch (Exception failure) { Toast.makeText(this, "Credits unavailable", Toast.LENGTH_SHORT).show(); }
    }

    private void label(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(size >= 17 ? Color.WHITE : 0xFFBAC5D6);
        view.setTypeface(getResources().getFont(R.font.outfit));
        view.setFontVariationSettings("'wght' " + (size >= 30 ? 400 : 500));
        view.setPadding(0, 0, 0, dp(12)); content.addView(view);
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false);
        button.setTextSize(17); button.setTypeface(getResources().getFont(R.font.outfit));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.bottomMargin = dp(8); content.addView(button, params);
        button.setOnClickListener(v -> action.run()); return button;
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onDestroy() {
        if (source != null) source.close();
        worker.shutdownNow(); main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
