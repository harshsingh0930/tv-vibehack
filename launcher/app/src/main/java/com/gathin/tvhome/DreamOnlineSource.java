package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Session-scoped I/O. No jobs, alarms, location access or work outside the dream. */
final class DreamOnlineSource implements AutoCloseable, DreamPhotoFeed.Backend {
    static final long WEATHER_INTERVAL = 15 * 60_000L;
    private static final int IMAGE_LIMIT = 2 * 1024 * 1024;
    private static final long CACHE_LIMIT = 8 * 1024 * 1024L;
    private final Context context;
    private final File cache;
    private volatile boolean closed;
    private final Set<HttpURLConnection> connections = new HashSet<>();
    private final Object photoLock = new Object();
    private Set<String> protectedIds = Collections.emptySet();

    DreamOnlineSource(Context context) {
        this.context = context;
        cache = new File(context.getCacheDir(), "dream-online");
        cache.mkdirs();
    }

    @Override public List<JSONObject> photos() throws Exception {
        JSONArray catalog;
        try (InputStream input = context.getAssets().open("heritage-wallpapers.json")) {
            catalog = new JSONArray(new String(read(input, 128 * 1024), StandardCharsets.UTF_8));
        }
        String theme = context.getSharedPreferences("dream", 0).getString("theme", "both");
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject item = catalog.getJSONObject(i);
            item.put("bundled", true);
            if ("both".equals(theme) || theme.equals(item.getString("theme"))) result.add(item);
        }
        return result;
    }

    Bitmap photo(JSONObject item, int width, int height) throws Exception {
        synchronized (photoLock) {
            if (closed) throw new InterruptedIOException();
            if (item.optBoolean("bundled")) return decodeAsset(item, width, height);
            File file = preparePhoto(item);
            Bitmap picture = decode(file, width, height);
            if (picture == null) { file.delete(); throw new IOException("Invalid photo"); }
            return picture;
        }
    }

    @Override public void prefetch(JSONObject item) throws Exception {
        synchronized (photoLock) { if (!item.optBoolean("bundled")) preparePhoto(item); }
    }

    @Override public boolean isReady(JSONObject item) {
        String id = item.optString("id");
        return id.matches("c?[0-9]+") && (item.optBoolean("bundled")
                || new File(cache, id + ".jpg").isFile());
    }

    @Override public void protect(Set<String> ids) { protectedIds = new HashSet<>(ids); }

    @Override public DreamPhotoFeed.Batch discover(String topic, int variant, int offset) throws Exception {
        String festival = "(festival OR Holi OR Diwali OR Deepavali OR Puja OR Navratri OR Janmashtami OR Chaturthi OR Tihar OR Prabhalu)";
        String query;
        if ("festivals".equals(topic)) {
            String[] queries = {"deepcat:\"Quality images of Hinduism\" " + festival,
                    festival + " hastemplate:\"Quality image\"",
                    "incategory:\"Featured pictures of Hinduism\" " + festival};
            query = queries[variant];
        } else {
            String[] queries = {"deepcat:\"Hindu temples in India\" hastemplate:\"Quality image\"",
                    "incategory:\"Featured pictures of Hindu temples\"",
                    "deepcat:\"Hindu temples in Nepal\" hastemplate:\"Quality image\""};
            query = queries[variant];
        }
        String address = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search"
                + "&gsrnamespace=6&gsrlimit=30&gsrsort=random&gsroffset=" + offset
                + "&gsrsearch=" + java.net.URLEncoder.encode("filetype:bitmap " + query, "UTF-8")
                + "&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata&iiurlwidth=1920"
                + "&iiextmetadatafilter=ObjectName%7CArtist%7CLicenseShortName%7CLicenseUrl%7CAssessments";
        JSONObject response = new JSONObject(new String(fetch(address, 512 * 1024), StandardCharsets.UTF_8));
        return parseDiscovery(response, topic);
    }

    static DreamPhotoFeed.Batch parseDiscovery(JSONObject response, String topic) throws Exception {
        if (response.has("error")) throw new IOException("Photo search unavailable");
        List<JSONObject> result = new ArrayList<>();
        JSONObject query = response.optJSONObject("query"), pages = query == null ? null : query.optJSONObject("pages");
        if (pages != null) for (Iterator<String> it = pages.keys(); it.hasNext();) {
            JSONObject page = pages.getJSONObject(it.next());
            JSONArray infos = page.optJSONArray("imageinfo");
            if (infos == null || infos.length() == 0) continue;
            JSONObject info = infos.optJSONObject(0), meta = info.optJSONObject("extmetadata");
            if (meta == null || !"image/jpeg".equals(info.optString("mime"))) continue;
            int width = info.optInt("width"), height = info.optInt("height");
            double ratio = width / (double) Math.max(1, height);
            String assessment = metadata(meta, "Assessments").toLowerCase(Locale.ROOT);
            String license = metadata(meta, "LicenseShortName"), author = metadata(meta, "Artist");
            if (width < 2400 || height < 1400 || ratio < 1.4 || ratio > 2.1
                    || !(assessment.contains("quality") || assessment.contains("featured"))
                    || !(license.startsWith("CC BY") || license.equals("CC0") || license.equals("Public domain"))
                    || author.isEmpty() || author.length() > 4096 || metadata(meta, "LicenseUrl").isEmpty()) continue;
            String file = page.optString("title"), url = info.optString("thumburl").split("\\?", 2)[0];
            if (file.toLowerCase(Locale.ROOT).matches(".*\\b(map|plan|diagram|logo|drawing|buddhist|buddha|mosque|church|jain|sikh)\\b.*")) continue;
            URL image = new URL(url);
            if (!"https".equals(image.getProtocol()) || !(image.getHost().equals("thumb.wikimedia.org")
                    || image.getHost().equals("upload.wikimedia.org"))) continue;
            if (info.optInt("thumbwidth") < 1600 || info.optInt("thumbheight") < 900) continue;
            String name = metadata(meta, "ObjectName");
            if (name.isEmpty()) name = file.replaceFirst("^File:", "").replaceFirst("(?i)\\.jpe?g$", "");
            String subject = (file + " " + name).toLowerCase(Locale.ROOT);
            if ("temples".equals(topic) && !subject.matches(".*(temple|mandir|kovil|gopur|devalaya|shiva|vishnu|krishna|ganesh|durga|kali|lakshmi|hanuman|anjaneya|swaminarayan|brihad|meenakshi|kedarnath|badrinath|tirupati|jagannath|pashupati|angkor wat|prambanan|मंदिर|मन्दिर|शिव|মন্দির|கோவில்).*")) continue;
            result.add(new JSONObject().put("id", "c" + page.getLong("pageid"))
                    .put("file", file).put("title", shorten(name, 120)).put("theme", topic)
                    .put("url", url).put("source", info.getString("descriptionurl"))
                    .put("author", author).put("license", license)
                    .put("licenseUrl", metadata(meta, "LicenseUrl"))
                    .put("width", info.optInt("thumbwidth")).put("height", info.optInt("thumbheight")));
        }
        JSONObject more = response.optJSONObject("continue");
        return new DreamPhotoFeed.Batch(result, more == null ? -1 : more.optInt("gsroffset", -1));
    }

    private static String metadata(JSONObject meta, String name) {
        JSONObject value = meta.optJSONObject(name);
        return value == null ? "" : android.text.Html.fromHtml(value.optString("value"), android.text.Html.FROM_HTML_MODE_LEGACY)
                .toString().replaceAll("\\s+", " ").trim();
    }

    private static String shorten(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit - 1) + "…" : text;
    }

    private Bitmap decodeAsset(JSONObject item, int width, int height) throws Exception {
        String id = item.getString("id");
        if (!id.matches("[0-9]+")) throw new IOException("Invalid bundled photo ID");
        String path = "heritage-fallback/" + id + ".jpg";
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap full;
        try (InputStream input = context.getAssets().open(path)) { full = BitmapFactory.decodeStream(input, null, options); }
        if (full == null) throw new IOException("Bundled photo unavailable");
        return scale(full, width, height);
    }

    private File preparePhoto(JSONObject item) throws Exception {
        if (closed) throw new InterruptedIOException();
        String id = item.getString("id");
        if (!id.matches("c[0-9]+")) throw new IOException("Invalid photo ID");
        File file = new File(cache, id + ".jpg");
        if (file.isFile() && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis()); return file;
        }
        byte[] bytes = fetch(item.getString("url"), IMAGE_LIMIT);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth < 1000 || bounds.outHeight < 600
                || bounds.outWidth > 4096 || bounds.outHeight > 4096) throw new IOException("Invalid photo dimensions");
        if (closed) throw new InterruptedIOException();
        trim(CACHE_LIMIT - bytes.length);
        android.util.AtomicFile atomic = new android.util.AtomicFile(file);
        FileOutputStream output = null;
        try { output = atomic.startWrite(); output.write(bytes); atomic.finishWrite(output); }
        catch (IOException failure) { atomic.failWrite(output); throw failure; }
        return file;
    }

    private Bitmap decode(File file, int width, int height) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        if (options.outWidth < 1000 || options.outHeight < 600
                || options.outWidth > 4096 || options.outHeight > 4096) return null;
        options.inSampleSize = 1;
        while (options.outWidth / (options.inSampleSize * 2) >= width
                && options.outHeight / (options.inSampleSize * 2) >= height) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap full = BitmapFactory.decodeFile(file.getPath(), options);
        if (full == null) return null;
        return scale(full, width, height);
    }

    private Bitmap scale(Bitmap full, int width, int height) {
        float scale = Math.max(width / (float) full.getWidth(), height / (float) full.getHeight());
        if (scale >= 1) return full;
        Bitmap small = Bitmap.createScaledBitmap(full, Math.max(1, Math.round(full.getWidth() * scale)),
                Math.max(1, Math.round(full.getHeight() * scale)), true);
        if (small != full) full.recycle();
        return small;
    }

    JSONObject weather(boolean refresh) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("dream", 0);
        if (!prefs.contains("latitude") || !prefs.contains("longitude")) return null;
        double lat = Double.parseDouble(prefs.getString("latitude", ""));
        double lon = Double.parseDouble(prefs.getString("longitude", ""));
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180)
            throw new IOException("Invalid location");
        String key = lat + "," + lon;
        File file = new File(cache, "weather.json");
        JSONObject saved = null;
        try {
            if (file.length() < 128 * 1024) {
                saved = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                if (!key.equals(saved.optString("locationKey"))) saved = null;
            }
        } catch (Exception ignored) { /* A first run or evicted cache is normal. */ }
        if (!refresh || (saved != null && age(saved) >= 0 && age(saved) < WEATHER_INTERVAL)) return saved;
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min"
                + "&forecast_days=1&timezone=auto";
        JSONObject live = new JSONObject(new String(fetch(url, 128 * 1024), StandardCharsets.UTF_8));
        JSONObject current = live.getJSONObject("current");
        double temperature = current.getDouble("temperature_2m");
        if (!Double.isFinite(temperature) || !current.has("weather_code")) throw new IOException("Invalid weather");
        live.put("fetchedAt", System.currentTimeMillis()).put("locationKey", key);
        byte[] bytes = live.toString().getBytes(StandardCharsets.UTF_8);
        if (closed) throw new InterruptedIOException();
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
        return live;
    }

    static long age(JSONObject weather) { return System.currentTimeMillis() - weather.optLong("fetchedAt", 0); }

    byte[] fetch(String address, int limit) throws Exception {
        URL url = new URL(address);
        // Providers and redirects are explicitly bounded; never send traffic to arbitrary photo URLs.
        for (int redirect = 0; redirect < 4; redirect++) {
            String host = url.getHost();
            if (!"https".equals(url.getProtocol()) || !(host.equals("upload.wikimedia.org") || host.equals("thumb.wikimedia.org")
                    || host.equals("commons.wikimedia.org") || host.equals("api.open-meteo.com") || host.equals("geocoding-api.open-meteo.com")))
                throw new IOException("Unapproved provider");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            synchronized (connections) {
                if (closed) throw new InterruptedIOException();
                connections.add(connection);
            }
            try {
                connection.setConnectTimeout(8000); connection.setReadTimeout(12000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "TvHomeScreensaver/3.9 (https://github.com/harshsingh0930/tv-vibehack)");
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing redirect");
                    url = new URL(url, location); continue;
                }
                if (code != 200) throw new IOException("Provider HTTP " + code);
                if (connection.getContentLengthLong() > limit) throw new IOException("Response too large");
                try (InputStream input = connection.getInputStream()) { return read(input, limit); }
            } finally {
                synchronized (connections) { connections.remove(connection); }
                connection.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }

    private byte[] read(InputStream input, int limit) throws IOException {
        long started = android.os.SystemClock.elapsedRealtime();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            if (android.os.SystemClock.elapsedRealtime() - started > 20_000)
                throw new java.net.SocketTimeoutException("Response deadline exceeded");
            if (count > limit - output.size()) throw new IOException("Response too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void trim(long target) {
        File[] files = cache.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long bytes = 0; for (File f : files) bytes += f.length();
        for (File f : files) {
            if (bytes <= target) break;
            if (protectedIds.contains(f.getName().replace(".jpg", ""))) continue;
            long size = f.length(); if (f.delete()) bytes -= size;
        }
        if (bytes > target) throw new IllegalStateException("Photo queue exceeds cache budget");
    }

    @Override public void close() {
        closed = true;
        synchronized (connections) {
            for (HttpURLConnection connection : connections) connection.disconnect();
            connections.clear();
        }
    }
}
