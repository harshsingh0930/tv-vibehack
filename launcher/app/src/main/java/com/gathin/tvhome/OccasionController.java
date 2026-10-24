package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** One visible owner, one small worker and one SSE reader. No permanent service or wake lock. */
final class OccasionController {
    private static final Object CACHE_LOCK = new Object();
    private static final AtomicLong ACTIVE_OWNER = new AtomicLong();
    interface Listener { void sceneChanged(OccasionProtocol.Scene scene, Bitmap photo, String effect); void cleared(); }
    private final SharedPreferences prefs;
    private final File cache;
    private final AtomicFile saved;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<HttpURLConnection> connections = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean requested = new AtomicBoolean();
    private ScheduledExecutorService worker;
    private ExecutorService stream;
    private volatile boolean running;
    private volatile long anchorServer, anchorElapsed, offset;
    private volatile OccasionProtocol model;
    private Listener listener;
    private String endpoint, token, acked = "";
    private volatile String shown = "unset";
    private volatile int generation;
    private long owner;
    private boolean online;
    private ScheduledFuture<?> retry;
    private long retryDelay = 2000;
    private long decodeAfter;
    private final Runnable expires = () -> { if (running && listener != null) { listener.cleared(); shown = "expired"; } };

    OccasionController(Context context) {
        prefs = context.getSharedPreferences("occasions", 0);
        cache = new File(context.getFilesDir(), "occasions"); cache.mkdirs();
        saved = new AtomicFile(new File(cache, "manifest.json"));
    }
    static String endpoint(String input) throws Exception {
        URL url = new URL(input.trim());
        if (!url.getProtocol().equals("https") || url.getHost().isEmpty() || url.getUserInfo() != null || url.getQuery() != null
                || url.getRef() != null || !(url.getPath().isEmpty() || url.getPath().equals("/"))) throw new IllegalArgumentException("Use an HTTPS server address without a path");
        return "https://" + url.getAuthority();
    }
    void start(Listener next) {
        stop(); listener = next; owner = ACTIVE_OWNER.incrementAndGet();
        if (!prefs.getBoolean("enabled", false)) { discardDisabledCache(); next.cleared(); return; }
        try { endpoint = endpoint(prefs.getString("endpoint", "")); }
        catch (Exception invalid) { next.cleared(); return; }
        token = prefs.getString("token", ""); if (token.isEmpty()) { discardDisabledCache(); next.cleared(); return; }
        running = true; shown = "unset"; acked = ""; int session = generation;
        worker = Executors.newSingleThreadScheduledExecutor(); stream = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            try { synchronized (CACHE_LOCK) {
                if (!valid(session)) return;
                if (saved.getBaseFile().exists()) {
                    JSONObject json = new JSONObject(new String(read(saved.openRead(), 65536), StandardCharsets.UTF_8));
                    model = new OccasionProtocol(json); offset = json.optLong("localOffset", 0);
                    prune(); verifyCached(); tick(session);
                }
            } } catch (Exception invalid) { if (valid(session)) { model = null; clearFiles(); } }
        });
        worker.scheduleWithFixedDelay(() -> tick(session), 1, 1, TimeUnit.SECONDS);
        stream.execute(() -> listen(session));
    }
    long now() { return anchorServer > 0 ? anchorServer + (SystemClock.elapsedRealtime() - anchorElapsed) : System.currentTimeMillis() + offset; }
    private void refresh(int session) {
        if (!valid(session)) return;
        if (retry != null) { retry.cancel(false); retry = null; }
        try {
            JSONObject value = new JSONObject(new String(request("/api/tv/manifest", null, 65536), StandardCharsets.UTF_8));
            OccasionProtocol incoming = new OccasionProtocol(value);
            if (!valid(session)) return;
            synchronized (CACHE_LOCK) {
            if (!valid(session)) return;
            online = false;
            anchorServer = incoming.serverTime; anchorElapsed = SystemClock.elapsedRealtime(); offset = incoming.serverTime - System.currentTimeMillis();
            model = incoming; value.put("localOffset", offset);
            atomicWrite(saved, value.toString().getBytes(StandardCharsets.UTF_8));
            prune(); tick(session); // Remote cancellations take effect before potentially slow downloads.
            }
            for (OccasionProtocol.Scene scene : incoming.scenes) {
                if (scene.endsAt <= now()) continue;
                for (OccasionProtocol.Photo photo : scene.photos) {
                    if (!valid(session)) return;
                    if (requested.get()) return; // A newer schedule takes priority over prefetch.
                    File file = photoFile(photo);
                    if (good(file, photo)) continue;
                    file.delete();
                    if (!OccasionPolicy.canStage(bytes(), photo.bytes)) throw new IOException("Photo cache budget exceeded");
                    byte[] data = request(photo.path, null, (int) OccasionPolicy.PHOTO_LIMIT);
                    if (!valid(session)) return;
                    if (requested.get()) return;
                    if (data.length != photo.bytes || !hash(data).equals(photo.hash)) throw new IOException("Photo checksum mismatch");
                    BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                    if (bounds.outWidth != 1280 || bounds.outHeight != 720) throw new IOException("Wrong photo dimensions");
                    if (scene.endsAt <= now()) continue;
                    synchronized (CACHE_LOCK) { if (!valid(session)) return; atomicWrite(new AtomicFile(file), data); }
                }
                tick(session);
            }
            if (valid(session)) { online = true; retryDelay = 2000; acked = ""; tick(session); }
        } catch (Unauthorized revoked) { revoke(session); }
        catch (Exception unavailable) {
            if (valid(session)) {
                online = false; android.util.Log.i("Occasions", "Sync unavailable; keeping the local schedule");
                scheduleRetry(session);
            }
        }
    }
    private void tick(int session) {
        synchronized (CACHE_LOCK) { tickLocked(session); }
    }
    private void tickLocked(int session) {
        if (!valid(session)) return;
        try {
            OccasionProtocol current = model;
            OccasionProtocol.Scene scene = current == null ? null : current.active(now());
            if (SystemClock.uptimeMillis() < decodeAfter) scene = null;
            if (scene != null) for (OccasionProtocol.Photo photo : scene.photos) if (!photoFile(photo).isFile()) { scene = null; break; }
            int index = scene == null || scene.photos.isEmpty() ? -1 : scene.mode.equals("fixed") ? 0 : (int) (((now() - scene.startsAt) / scene.interval) % scene.photos.size());
            String key = scene == null ? "none" : scene.identity() + ":" + index;
            if (!key.equals(shown)) {
                Bitmap decoded = null;
                if (index >= 0) {
                    BitmapFactory.Options options = new BitmapFactory.Options(); options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    decoded = BitmapFactory.decodeFile(photoFile(scene.photos.get(index)).getPath(), options);
                    if (decoded == null) { photoFile(scene.photos.get(index)).delete(); throw new IOException("Photo decode failed"); }
                }
                shown = key;
                final OccasionProtocol.Scene visible = scene; final Bitmap picture = decoded;
                main.post(() -> {
                    if (!valid(session) || listener == null) { if (picture != null) picture.recycle(); return; }
                    main.removeCallbacks(expires);
                    if (visible == null || now() >= visible.endsAt) { if (picture != null) picture.recycle(); listener.cleared(); }
                    else {
                        listener.sceneChanged(visible, picture, visible.effectFor(index));
                        main.postDelayed(expires, Math.max(1, visible.endsAt - now()));
                    }
                });
                prune();
            }
            if (online) {
                try { acknowledge(scene != null ? "active" : current != null && !current.scenes.isEmpty() ? "ready" : "idle", scene); }
                catch (Unauthorized revoked) { revoke(session); }
                catch (Exception unavailable) { online = false; scheduleRetry(session); }
            }
        } catch (OutOfMemoryError pressure) {
            decodeAfter = SystemClock.uptimeMillis() + 30000; shown = "memory";
            main.post(() -> { if (valid(session) && listener != null) listener.cleared(); });
            android.util.Log.i("Occasions", "Photo allocation deferred; normal background retained");
        } catch (Exception invalid) { android.util.Log.i("Occasions", "Scene is not ready yet"); scheduleRetry(session); }
    }
    private void listen(int session) {
        long delay = 1000;
        while (valid(session)) {
            HttpURLConnection connection = null;
            try {
                connection = connect("/api/tv/events"); connection.setReadTimeout(25000);
                int status = connection.getResponseCode(); if (status == 401) throw new Unauthorized();
                if (status != 200) throw new IOException("SSE unavailable");
                requestRefresh(session); delay = 1000;
                try (InputStream input = connection.getInputStream()) {
                    String line;
                    while (valid(session) && (line = line(input)) != null) if (line.startsWith("data:")) requestRefresh(session);
                }
            } catch (Unauthorized revoked) { submit(() -> revoke(session)); return; }
            catch (Exception ignored) { }
            finally { if (connection != null) { connections.remove(connection); connection.disconnect(); } }
            if (!valid(session)) return;
            try { Thread.sleep(delay); } catch (InterruptedException stopped) { return; }
            delay = Math.min(30000, delay * 2);
        }
    }
    private void requestRefresh(int session) {
        if (requested.compareAndSet(false, true)) submit(() -> { requested.set(false); refresh(session); });
    }
    private void submit(Runnable work) { try { if (worker != null && !worker.isShutdown()) worker.execute(work); } catch (RejectedExecutionException ignored) {} }
    private void scheduleRetry(int session) {
        if (!valid(session) || (retry != null && !retry.isDone())) return;
        try { retry = worker.schedule(() -> requestRefresh(session), retryDelay, TimeUnit.MILLISECONDS); }
        catch (RejectedExecutionException stopped) { }
        retryDelay = Math.min(60000, retryDelay * 2);
    }
    private void acknowledge(String state, OccasionProtocol.Scene scene) throws Exception {
        if (model == null) return;
        String key = state + model.revision + (scene == null ? "" : scene.id);
        if (key.equals(acked)) return;
        JSONObject json = new JSONObject().put("state", state).put("revision", model.revision).put("eventId", scene == null ? "" : scene.id);
        request("/api/tv/ack", json, 4096); acked = key;
    }
    private void revoke(int session) {
        if (!valid(session)) return;
        prefs.edit().remove("token").putBoolean("enabled", false).apply(); model = null; clearFiles();
        main.post(() -> { if (valid(session)) { if (listener != null) listener.cleared(); stop(); } });
    }
    private boolean valid(int session) { return running && generation == session && ACTIVE_OWNER.get() == owner && !Thread.currentThread().isInterrupted(); }
    private HttpURLConnection connect(String path) throws Exception {
        if (!path.startsWith("/api/tv/")) throw new IOException("Invalid TV endpoint");
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint + path).openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(10000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Authorization", "Bearer " + token); connection.setRequestProperty("Accept", "application/json");
        connections.add(connection); return connection;
    }
    private byte[] request(String path, JSONObject data, int maximum) throws Exception {
        HttpURLConnection connection = connect(path);
        try {
            if (data != null) {
                byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8); connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json"); connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            }
            int status = connection.getResponseCode(); if (status == 401) throw new Unauthorized();
            if (status != 200) throw new IOException("TV request rejected");
            if (connection.getContentLengthLong() > maximum) throw new IOException("Response too large");
            return read(connection.getInputStream(), maximum);
        } finally { connections.remove(connection); connection.disconnect(); }
    }
    static byte[] read(InputStream input, int maximum) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) { if (out.size() + count > maximum) throw new IOException("Response limit exceeded"); out.write(buffer, 0, count); }
            return out.toByteArray();
        }
    }
    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); int c;
        while ((c = input.read()) != -1 && c != '\n') { if (bytes.size() >= 4096) throw new IOException("SSE line limit"); if (c != '\r') bytes.write(c); }
        return c == -1 && bytes.size() == 0 ? null : bytes.toString("UTF-8");
    }
    private static void atomicWrite(AtomicFile file, byte[] data) throws IOException {
        FileOutputStream out = file.startWrite();
        try { out.write(data); file.finishWrite(out); } catch (IOException failure) { file.failWrite(out); throw failure; }
    }
    private File photoFile(OccasionProtocol.Photo photo) { return new File(cache, photo.hash + ".jpg"); }
    private static String hash(byte[] data) throws Exception {
        StringBuilder result = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) result.append(String.format(Locale.ROOT, "%02x", b & 255)); return result.toString();
    }
    private boolean good(File file, OccasionProtocol.Photo photo) throws Exception { return file.isFile() && file.length() == photo.bytes && hash(read(new FileInputStream(file), (int) OccasionPolicy.PHOTO_LIMIT)).equals(photo.hash); }
    private void verifyCached() throws Exception {
        if (model == null) return;
        for (OccasionProtocol.Scene scene : model.scenes) for (OccasionProtocol.Photo photo : scene.photos) if (!good(photoFile(photo), photo)) photoFile(photo).delete();
    }
    private long bytes() { long total = 0; File[] files = cache.listFiles(); if (files != null) for (File file : files) total += file.length(); return total; }
    private void prune() {
        Set<String> keep = new HashSet<>(Arrays.asList("manifest.json", "manifest.json.bak", "manifest.json.new"));
        if (model != null) for (OccasionProtocol.Scene scene : model.scenes) if (scene.endsAt > now()) for (OccasionProtocol.Photo photo : scene.photos) keep.add(photo.hash + ".jpg");
        File[] files = cache.listFiles(); if (files != null) for (File file : files) if (!keep.contains(file.getName())) file.delete();
    }
    private void clearFiles() { File[] files = cache.listFiles(); if (files != null) for (File file : files) file.delete(); }
    private void discardDisabledCache() {
        File[] files = cache.listFiles();
        if (files == null || files.length == 0) return;
        ExecutorService once = Executors.newSingleThreadExecutor();
        long disabledOwner = owner;
        once.execute(() -> { synchronized (CACHE_LOCK) {
            if (ACTIVE_OWNER.get() == disabledOwner && !prefs.getBoolean("enabled", false)) clearFiles();
        } }); once.shutdown();
    }
    void stop() {
        running = false; generation++; main.removeCallbacks(expires); listener = null;
        for (HttpURLConnection connection : connections) connection.disconnect(); connections.clear();
        if (stream != null) stream.shutdownNow(); if (worker != null) worker.shutdownNow();
        stream = null; worker = null; requested.set(false); model = null; anchorServer = 0; anchorElapsed = 0; online = false;
        retry = null; retryDelay = 2000;
        decodeAfter = 0;
    }
    private static final class Unauthorized extends IOException {}
}
