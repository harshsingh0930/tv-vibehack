package com.gathin.tvhome;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Dependency-free device checks with deterministic network failures and a real asset decoder. */
public final class DreamFeedChecks extends Instrumentation {
    private int passed;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            queueAndRestart(); offlineAndMissingFiles(); themeAndQuality(); bundledAndCancellation(); occasionEffects();
            result.putString("stream", "PASS: " + passed + " feed assertions\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace));
            result.putString("stream", "FAIL: " + trace); finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); passed++;
    }
    private void occasionEffects() throws Exception {
        String event="11111111-1111-4111-8111-111111111111", id="22222222-2222-4222-8222-222222222222";
        JSONObject photo=new JSONObject().put("id",id).put("sha256","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").put("bytes",1024)
                .put("width",1280).put("height",720).put("path","/api/tv/media/"+event+"/"+id);
        JSONObject value=new JSONObject().put("id",event).put("title","Test").put("message","").put("color","#d7b46e")
                .put("startsAt",1000L).put("endsAt",3601000L).put("effect","flowers").put("mode","album").put("intervalSeconds",30)
                .put("assets",new JSONArray().put(photo));
        check(new OccasionProtocol.Scene(value).effectFor(0).equals("flowers"),"Photo inherits the occasion by default");
        photo.put("effect","none");
        check(new OccasionProtocol.Scene(value).effectFor(0).equals("none"),"Explicit no effects overrides the occasion");
        photo.put("effect","bubbles");
        check(new OccasionProtocol.Scene(value).effectFor(0).equals("flowers"),"Old bubble settings migrate to flowers");
        OccasionEffects artwork=new OccasionEffects();
        Bitmap overlay=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(overlay);
        for(String effect:new String[]{"diya","gulal","temple","marigold","mantra","starlight","cosmic","peacock","leaves"}) {
            photo.put("effect",effect);
            check(new OccasionProtocol.Scene(value).effectFor(0).equals(effect),"Photo effect reaches the rotation renderer: "+effect);
            overlay.eraseColor(0);
            check(artwork.draw(canvas,effect,12,960,540,true,330),"Festive artwork supports "+effect);
            int[] pixels=new int[960*540]; overlay.getPixels(pixels,0,960,0,0,960,540);
            boolean visible=false; for(int pixel:pixels) if((pixel>>>24)>0) {visible=true;break;}
            check(visible,"Festive artwork draws visible pixels: "+effect);
            if(effect.equals("diya")) {
                int brightAbove=0,brightBehind=0;
                for(int y=0;y<540;y++)for(int x=0;x<960;x++)if((pixels[y*960+x]>>>24)>190) {
                    if(y<330)brightAbove++;else brightBehind++;
                }
                check(brightAbove>100,"Detailed diya artwork remains visible above the app dock");
                check(brightBehind==0,"Opaque diya artwork never falls behind the dock");
            }
        }
        overlay.recycle(); photo.put("effect","unknown");
        boolean rejected=false; try { new OccasionProtocol.Scene(value); } catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"Unknown photo effects are rejected");
    }
    private File state() throws IOException {
        File folder = new File(getTargetContext().getCacheDir(), "feed-checks"); folder.mkdirs();
        File file = File.createTempFile("feed-", ".json", folder); file.delete(); return file;
    }

    private void queueAndRestart() throws Exception {
        Fake backend = new Fake(); File state = state();
        DreamPhotoFeed feed = new DreamPhotoFeed(state, "both", backend);
        JSONObject first = feed.take(); feed.shown(first);
        check(first.optBoolean("bundled"), "First-run offline fallback must be available before discovery");
        feed.fill(); check(feed.readyCount == 3, "Three fully downloaded upcoming photos");
        check(backend.protectedIds.size() == 4, "Current photo plus three queued files are pinned");
        JSONObject online = feed.take(); feed.shown(online);
        check(!online.optBoolean("bundled"), "Online queue replaces the fallback");
        check(backend.downloaded.contains(online.getString("id")), "No undownloaded photo can enter display queue");
        feed.fill(); check(feed.readyCount == 3, "Refill replaces each consumed queue entry");
        int searches = backend.searches;
        DreamPhotoFeed restarted = new DreamPhotoFeed(state, "both", backend);
        JSONObject next = restarted.take(); restarted.shown(next);
        check(!DreamPhotoFeed.key(next).equals(DreamPhotoFeed.key(online)), "Restart must not repeat the last photo");
        check(!next.optBoolean("bundled"), "Restart uses the persisted downloaded queue");
        check(backend.searches == searches, "Displaying saved queue requires no network call");
        Set<String> selected = new HashSet<>(); selected.add(DreamPhotoFeed.key(online)); selected.add(DreamPhotoFeed.key(next));
        for (int i = 0; i < 4; i++) {
            restarted.fill(); JSONObject item = restarted.take(); restarted.shown(item);
            check(selected.add(DreamPhotoFeed.key(item)), "Prefer unseen images over recent online photos");
        }
    }

    private void offlineAndMissingFiles() throws Exception {
        Fake offline = new Fake(); offline.offline = true;
        DreamPhotoFeed feed = new DreamPhotoFeed(state(), "both", offline);
        Set<String> selected = new HashSet<>();
        for (int i = 0; i < 9; i++) {
            JSONObject photo = feed.take(); feed.shown(photo);
            check(photo.optBoolean("bundled"), "Offline display uses built-in photos"); selected.add(photo.getString("id"));
            feed.fill();
        }
        check(selected.size() == 9, "All nine fallbacks remain available offline");
        check(offline.searches <= 2, "Offline discovery retries are rate limited");
        Fake backend = new Fake(); File state = state();
        DreamPhotoFeed saved = new DreamPhotoFeed(state, "both", backend); saved.take(); saved.fill();
        backend.downloaded.clear(); backend.offline = true;
        DreamPhotoFeed evicted = new DreamPhotoFeed(state, "both", backend);
        check(evicted.take().optBoolean("bundled"), "Evicted queued files cannot cause an empty frame");
        File corrupt = state();
        try (FileOutputStream out = new FileOutputStream(corrupt)) { out.write("broken JSON".getBytes()); }
        check(new DreamPhotoFeed(corrupt, "both", backend).take().optBoolean("bundled"), "Corrupt state has an offline fallback");
    }

    private void themeAndQuality() throws Exception {
        DreamPhotoFeed festivals = new DreamPhotoFeed(state(), "festivals", new Fake());
        for (int i = 0; i < 4; i++) check("festivals".equals(festivals.take().getString("theme")), "Fallback respects theme filter");
        JSONObject meta = new JSONObject().put("Assessments", new JSONObject().put("value", "quality"))
                .put("Artist", new JSONObject().put("value", "<b>A photographer</b>"))
                .put("LicenseShortName", new JSONObject().put("value", "CC BY-SA 4.0"))
                .put("LicenseUrl", new JSONObject().put("value", "https://creativecommons.org/licenses/by-sa/4.0/"));
        JSONObject info = new JSONObject().put("mime", "image/jpeg").put("width", 4000).put("height", 2500)
                .put("thumbwidth", 1920).put("thumbheight", 1200).put("extmetadata", meta)
                .put("thumburl", "https://thumb.wikimedia.org/example.jpg").put("descriptionurl", "https://commons.wikimedia.org/wiki/File:Temple.jpg");
        JSONObject page = new JSONObject().put("pageid", 123).put("title", "File:Temple.jpg").put("imageinfo", new JSONArray().put(info));
        JSONObject response = new JSONObject().put("query", new JSONObject().put("pages", new JSONObject().put("123", page)));
        check(DreamOnlineSource.parseDiscovery(response, "temples").photos.size() == 1, "Reviewed high-resolution landscape accepted");
        check(DreamOnlineSource.parseDiscovery(response, "temples").photos.get(0).getString("author").equals("A photographer"), "HTML metadata rendered as plain credits");
        page.put("title", "File:Rocks Golkonda fort.jpg");
        check(DreamOnlineSource.parseDiscovery(response, "temples").photos.isEmpty(), "Unrelated landmark in temple category rejected");
        page.put("title", "File:Buddhist temple.jpg");
        check(DreamOnlineSource.parseDiscovery(response, "temples").photos.isEmpty(), "Other religious landmarks rejected");
        page.put("title", "File:Temple.jpg");
        info.put("width", 1000); check(DreamOnlineSource.parseDiscovery(response, "temples").photos.isEmpty(), "Low-resolution image rejected");
        info.put("width", 4000).put("height", 5000); check(DreamOnlineSource.parseDiscovery(response, "temples").photos.isEmpty(), "Portrait image rejected");
        info.put("height", 2500); meta.getJSONObject("Assessments").put("value", "");
        check(DreamOnlineSource.parseDiscovery(response, "temples").photos.isEmpty(), "Unreviewed result rejected");
    }

    private void bundledAndCancellation() throws Exception {
        android.content.Context isolated = new android.content.ContextWrapper(getTargetContext()) {
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("feed-checks-" + name, mode);
            }
        };
        DreamOnlineSource source = new DreamOnlineSource(isolated);
        List<JSONObject> photos = source.photos(); check(photos.size() == 9, "Nine real bundled fallback assets");
        for (JSONObject photo : photos) {
            Bitmap bitmap = source.photo(photo, 1280, 720);
            check(bitmap != null && bitmap.getWidth() >= 1280 && bitmap.getHeight() >= 720, "Offline photo decodes at TV resolution");
            bitmap.recycle();
        }
        source.close();
        try { source.fetch("https://commons.wikimedia.org/w/api.php", 100); check(false, "Closed source must not connect"); }
        catch (InterruptedIOException expected) { passed++; }
        try { source.photo(photos.get(0), 1280, 720); check(false, "Closed source must not decode"); }
        catch (InterruptedIOException expected) { passed++; }
    }

    private static final class Fake implements DreamPhotoFeed.Backend {
        final Set<String> downloaded = new HashSet<>(); Set<String> protectedIds = new HashSet<>();
        boolean offline; int searches;
        private JSONObject item(int id, String topic, boolean bundled) throws Exception {
            return new JSONObject().put("id", (bundled ? "" : "c") + id).put("file", "File:Photo" + id + ".jpg")
                    .put("theme", topic).put("bundled", bundled);
        }
        public List<JSONObject> photos() throws Exception {
            List<JSONObject> photos = new ArrayList<>();
            for (int i = 1; i <= 9; i++) photos.add(item(i, i % 2 == 0 ? "festivals" : "temples", true));
            return photos;
        }
        public DreamPhotoFeed.Batch discover(String topic, int variant, int offset) throws Exception {
            searches++; if (offline) throw new IOException("Offline test");
            List<JSONObject> photos = new ArrayList<>();
            for (int i = 100; i < 116; i++) if ((i % 2 == 0) == "festivals".equals(topic)) photos.add(item(i, topic, false));
            return new DreamPhotoFeed.Batch(photos, -1);
        }
        public boolean isReady(JSONObject photo) { return photo.optBoolean("bundled") || downloaded.contains(photo.optString("id")); }
        public void prefetch(JSONObject photo) throws Exception {
            if (offline) throw new IOException("Offline test"); downloaded.add(photo.getString("id"));
        }
        public void protect(Set<String> ids) { protectedIds = new HashSet<>(ids); }
    }
}
