package com.gathin.tvhome;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The public fork's bundled looks. Theme selection never rewrites the classic dock's preferences. */
final class HomeTheme {
    final boolean mountain;
    final int columns;
    final List<String> dockApps;

    private HomeTheme(boolean mountain, int columns, List<String> dockApps) {
        this.mountain = mountain;
        this.columns = columns;
        this.dockApps = dockApps;
    }

    static HomeTheme load(Context context) {
        boolean enabled = context.getSharedPreferences("home", 0).getBoolean("mountain_glass",
                context.getResources().getBoolean(R.bool.mountain_glass_default));
        if (enabled) {
            try (InputStream input = context.getAssets().open("mountain-glass/config.json")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                JSONObject config = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
                int columns = config.getInt("columns");
                if (columns < 1 || columns > 8) throw new IllegalArgumentException("columns");
                JSONArray apps = config.getJSONArray("dock_apps");
                List<String> dock = new ArrayList<>();
                for (int i = 0; i < apps.length(); i++) {
                    String pkg = apps.getString(i).trim();
                    if (pkg.isEmpty() || dock.contains(pkg)) throw new IllegalArgumentException("dock_apps");
                    dock.add(pkg);
                }
                return new HomeTheme(true, columns, dock);
            } catch (Exception invalidTheme) {
                android.util.Log.w("HomeTheme", "Bundled theme unavailable; using classic Home", invalidTheme);
            }
        }
        return new HomeTheme(false, Math.max(1, context.getResources().getInteger(R.integer.columns)),
                Arrays.asList(context.getResources().getStringArray(R.array.dock_apps)));
    }

    String orderKey() { return mountain ? "mountain_app_order" : "app_order"; }
    String dockKey() { return mountain ? "mountain_dock_apps" : "dock_apps"; }
}
