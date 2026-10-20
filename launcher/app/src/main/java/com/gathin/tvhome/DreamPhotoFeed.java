package com.gathin.tvhome;

import android.os.SystemClock;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Owned by the dream's single photo worker. Downloads finish before entering the ready queue. */
final class DreamPhotoFeed {
    interface Backend {
        List<JSONObject> photos() throws Exception;
        Batch discover(String topic, int variant, int offset) throws Exception;
        boolean isReady(JSONObject photo);
        void prefetch(JSONObject photo) throws Exception;
        void protect(Set<String> ids);
    }
    static final class Batch {
        final List<JSONObject> photos;
        final int nextOffset;
        Batch(List<JSONObject> photos, int nextOffset) { this.photos = photos; this.nextOffset = nextOffset; }
    }
    private final Backend backend;
    private final String theme;
    private final AtomicFile state;
    private final List<JSONObject> fallback = new ArrayList<>();
    private final List<JSONObject> pool = new ArrayList<>();
    private final Deque<JSONObject> ready = new ArrayDeque<>();
    private final List<String> recent = new ArrayList<>();
    private final Set<String> bundledKeys = new HashSet<>();
    private final Map<String, Long> failed = new HashMap<>(), searched = new HashMap<>();
    private JSONObject cursors = new JSONObject(), lastShown;
    private int fallbackIndex;
    private String currentId = "", lastTopic = "temples";
    private boolean loaded;
    volatile int readyCount, discoveries, candidateCount, recentCount, discoveryErrors;
    volatile String lastSelection = "none";

    DreamPhotoFeed(File stateFile, String theme, Backend backend) {
        this.state = new AtomicFile(stateFile); this.theme = theme; this.backend = backend;
    }

    static String key(JSONObject photo) {
        return photo.optString("file").toLowerCase(Locale.ROOT)
                .replaceAll("\\s*\\((?:cropped|edit|edited)\\)", "")
                .replaceAll("\\s*-\\s*edited(?=\\.)", "").trim();
    }

    private boolean allowed(JSONObject photo) {
        return "both".equals(theme) || theme.equals(photo.optString("theme"));
    }

    private void load() throws Exception {
        if (loaded) return;
        for (JSONObject photo : backend.photos()) if (allowed(photo)) fallback.add(photo);
        if (fallback.isEmpty()) throw new IOException("No offline photographs");
        for (JSONObject photo : fallback) bundledKeys.add(key(photo));
        JSONObject saved = readState(state);
        if (saved != null) {
            fallbackIndex = Math.max(0, saved.optInt("fallbackIndex"));
            JSONArray history = saved.optJSONArray("recent");
            if (history != null) for (int i = Math.max(0, history.length() - 256); i < history.length(); i++)
                recent.add(history.optString(i));
            boolean currentDiscovery = saved.optInt("discoveryVersion") == 3;
            JSONArray candidates = currentDiscovery ? saved.optJSONArray("pool") : null;
            if (candidates != null) for (int i = 0; i < Math.min(128, candidates.length()); i++) {
                JSONObject photo = candidates.optJSONObject(i);
                if (validSaved(photo) && allowed(photo)) pool.add(photo);
            }
            JSONArray queue = currentDiscovery ? saved.optJSONArray("ready") : null;
            if (queue != null) for (int i = 0; i < Math.min(3, queue.length()); i++) {
                JSONObject photo = queue.optJSONObject(i);
                if (validSaved(photo) && allowed(photo) && !recent.contains(key(photo))
                        && !queued(key(photo)) && backend.isReady(photo)) ready.add(photo);
            }
            cursors = currentDiscovery ? saved.optJSONObject("cursors") : null;
            if (cursors == null) cursors = new JSONObject();
            lastShown = saved.optJSONObject("lastShown");
        }
        loaded = true; syncCounts(); protect();
    }

    private static boolean validSaved(JSONObject photo) {
        return photo != null && photo.optString("id").matches("c[0-9]+")
                && !photo.optString("file").isEmpty();
    }

    JSONObject take() throws Exception {
        load();
        JSONObject photo = null;
        while (!ready.isEmpty() && photo == null) {
            JSONObject candidate = ready.removeFirst();
            if (backend.isReady(candidate)) photo = candidate;
        }
        if (photo == null) {
            photo = fallback.get(fallbackIndex++ % fallback.size());
            lastSelection = "bundled";
        } else lastSelection = "online";
        currentId = photo.optString("id"); lastTopic = photo.optString("theme");
        protect(); save(); return photo;
    }

    JSONObject takeFallback() throws Exception {
        load();
        JSONObject photo = fallback.get(fallbackIndex++ % fallback.size());
        currentId = photo.optString("id"); lastTopic = photo.optString("theme"); lastSelection = "bundled";
        protect(); save(); return photo;
    }

    void shown(JSONObject photo) throws Exception {
        String key = key(photo); recent.remove(key); recent.add(key);
        while (recent.size() > 256) recent.remove(0);
        lastShown = photo; save();
    }

    void fill() throws Exception {
        load();
        int attempts = 0;
        while (ready.size() < 3 && attempts++ < 8 && !Thread.currentThread().isInterrupted()) {
            String topic = "both".equals(theme) ? ("temples".equals(lastTopic) ? "festivals" : "temples") : theme;
            JSONObject candidate = find(topic, false);
            if (candidate == null) { discover(topic); candidate = find(topic, false); }
            if (candidate == null && "both".equals(theme)) {
                topic = "temples".equals(topic) ? "festivals" : "temples";
                candidate = find(topic, false);
                if (candidate == null) { discover(topic); candidate = find(topic, false); }
            }
            // A finite provider collection can run out of unseen results. Prefer its oldest photo,
            // excluding the last three; offline failures still use the bundled collection.
            if (candidate == null) candidate = find(topic, true);
            if (candidate == null) break;
            try {
                protect(); backend.prefetch(candidate);
                if (!backend.isReady(candidate)) throw new IOException("Photo not ready");
                ready.addLast(candidate); lastTopic = candidate.optString("theme"); save(); protect();
            } catch (Exception unavailable) {
                if (unavailable instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) throw unavailable;
                failed.put(key(candidate), SystemClock.elapsedRealtime() + 120_000);
                discoveryErrors++;
                // Do not spend all night retrying an offline provider.
                if (ready.isEmpty()) break;
            }
        }
        syncCounts();
    }

    private JSONObject find(String topic, boolean older) {
        JSONObject best = null; int oldest = Integer.MAX_VALUE;
        for (JSONObject photo : pool) {
            String key = key(photo);
            if (!topic.equals(photo.optString("theme")) || bundledKeys.contains(key) || queued(key)
                    || currentId.equals(photo.optString("id"))
                    || failed.getOrDefault(key, 0L) > SystemClock.elapsedRealtime()) continue;
            int index = recent.indexOf(key);
            if (!older && index < 0) return photo;
            if (older && index >= 0 && index < recent.size() - 3 && index < oldest) { best = photo; oldest = index; }
        }
        return best;
    }

    private void discover(String topic) throws Exception {
        long now = SystemClock.elapsedRealtime();
        if (searched.containsKey(topic) && now - searched.get(topic) < 120_000) return;
        searched.put(topic, now);
        JSONObject cursor = cursors.optJSONObject(topic);
        if (cursor == null) cursor = new JSONObject();
        int variant = Math.max(0, cursor.optInt("variant")) % 3;
        int offset = Math.max(0, Math.min(10_000, cursor.optInt("offset")));
        try {
            Batch batch = backend.discover(topic, variant, offset);
            discoveries++;
            for (JSONObject photo : batch.photos) {
                String key = key(photo);
                if (bundledKeys.contains(key) || pool.stream().anyMatch(p -> key(p).equals(key))) continue;
                pool.add(photo);
            }
            // Keep bounded metadata, including ready and currently displayed photos.
            while (pool.size() > 128) {
                int remove = 0;
                while (remove < pool.size() && (queued(key(pool.get(remove)))
                        || currentId.equals(pool.get(remove).optString("id")))) remove++;
                pool.remove(remove);
            }
            Collections.shuffle(pool);
            cursor.put("offset", batch.nextOffset < 0 ? 0 : batch.nextOffset);
            cursor.put("variant", batch.nextOffset < 0 ? (variant + 1) % 3 : variant);
            cursors.put(topic, cursor); save();
        } catch (Exception unavailable) {
            if (unavailable instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) throw unavailable;
            android.util.Log.i("DreamPhotoFeed", "Discovery unavailable", unavailable);
            discoveryErrors++;
        }
    }

    private boolean queued(String key) {
        for (JSONObject photo : ready) if (key(photo).equals(key)) return true;
        return false;
    }

    private void protect() {
        Set<String> ids = new HashSet<>(); ids.add(currentId);
        for (JSONObject photo : ready) ids.add(photo.optString("id"));
        backend.protect(ids); syncCounts();
    }

    private void syncCounts() { readyCount = ready.size(); candidateCount = pool.size(); recentCount = recent.size(); }

    private void save() throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        JSONObject data = new JSONObject().put("ready", new JSONArray(ready)).put("pool", new JSONArray(pool))
                .put("recent", new JSONArray(recent)).put("fallbackIndex", fallbackIndex)
                .put("cursors", cursors).put("lastShown", lastShown).put("discoveryVersion", 3);
        FileOutputStream output = null;
        try {
            output = state.startWrite(); output.write(data.toString().getBytes(StandardCharsets.UTF_8)); state.finishWrite(output);
        } catch (Exception failure) { state.failWrite(output); throw failure; }
        syncCounts();
    }

    static JSONObject readState(AtomicFile file) {
        try (FileInputStream input = file.openRead()) {
            if (input.getChannel().size() > 512 * 1024) return null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > 512 * 1024 - bytes.size()) return null;
                bytes.write(buffer, 0, count);
            }
            return new JSONObject(bytes.toString("UTF-8"));
        } catch (Exception missing) { return null; }
    }
}
