package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class OccasionProtocol {
    static String normaliseEffect(String effect) { return effect.equals("bubbles") ? "flowers" : effect; }
    static boolean validEffect(String effect) {
        return java.util.Arrays.asList("petals", "confetti", "sparkles", "flowers", "none", "diya", "gulal", "temple",
                "marigold", "mantra", "starlight", "cosmic", "peacock", "leaves").contains(effect);
    }
    static final class Photo {
        final String hash, path, effect;
        final long bytes;
        Photo(JSONObject value, String eventId) throws Exception {
            hash = value.getString("sha256"); bytes = value.getLong("bytes");
            String id = value.getString("id"); path = value.getString("path");
            effect = normaliseEffect(value.optString("effect", ""));
            if (!effect.isEmpty() && !validEffect(effect)) throw new IllegalArgumentException("Invalid photo effect");
            if (!OccasionPolicy.identifier(id) || !OccasionPolicy.hash(hash) || bytes <= 0 || bytes > OccasionPolicy.PHOTO_LIMIT
                    || value.getInt("width") != 1280 || value.getInt("height") != 720
                    || !path.equals("/api/tv/media/" + eventId + "/" + id)) throw new IllegalArgumentException("Invalid photo");
        }
    }
    static final class Scene {
        final String id, title, message, color, effect, mode;
        final long startsAt, endsAt, interval;
        final List<Photo> photos = new ArrayList<>();
        private final String key;
        Scene(JSONObject value) throws Exception {
            id = value.getString("id"); title = value.getString("title"); message = value.getString("message");
            startsAt = value.getLong("startsAt"); endsAt = value.getLong("endsAt"); color = value.getString("color");
            effect = normaliseEffect(value.getString("effect"));
            mode = value.getString("mode"); interval = value.getLong("intervalSeconds") * 1000;
            if (!OccasionPolicy.identifier(id) || !OccasionPolicy.validWindow(startsAt, endsAt) || title.length() > 80 || message.length() > 140
                    || !color.matches("#[a-fA-F0-9]{6}") || !(mode.equals("fixed") || mode.equals("album"))
                    || !validEffect(effect)
                    || interval < 30000 || interval > 600000) throw new IllegalArgumentException("Invalid scene");
            JSONArray assets = value.getJSONArray("assets");
            if (assets.length() > 6 || (mode.equals("fixed") && assets.length() > 1)) throw new IllegalArgumentException("Too many photos");
            long total = 0;
            for (int i = 0; i < assets.length(); i++) { Photo photo = new Photo(assets.getJSONObject(i), id); photos.add(photo); total += photo.bytes; }
            if (total > OccasionPolicy.SCENE_LIMIT) throw new IllegalArgumentException("Scene exceeds budget");
            key = value.toString();
        }
        String identity() {
            return key;
        }
        String effectFor(int index) {
            return index >= 0 && index < photos.size() && !photos.get(index).effect.isEmpty() ? photos.get(index).effect : effect;
        }
    }
    final List<Scene> scenes = new ArrayList<>();
    final long revision, serverTime;
    final JSONObject json;
    OccasionProtocol(JSONObject value) throws Exception {
        json = value; revision = value.getLong("revision"); serverTime = value.getLong("serverTime");
        if (value.getInt("schema") != 1 || revision < 0 || serverTime <= 0) throw new IllegalArgumentException("Unsupported manifest");
        JSONArray values = value.getJSONArray("scenes"); if (values.length() > 2) throw new IllegalArgumentException("Too many scenes");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < values.length(); i++) {
            Scene next = new Scene(values.getJSONObject(i)); if (!ids.add(next.id)) throw new IllegalArgumentException("Duplicate scene");
            for (Scene previous : scenes) if (next.startsAt < previous.endsAt && next.endsAt > previous.startsAt) throw new IllegalArgumentException("Overlapping scenes");
            scenes.add(next);
        }
    }
    Scene active(long now) { for (Scene scene : scenes) if (OccasionPolicy.active(scene.startsAt, scene.endsAt, now)) return scene; return null; }
}
