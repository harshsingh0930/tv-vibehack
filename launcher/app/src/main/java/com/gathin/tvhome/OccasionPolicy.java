package com.gathin.tvhome;

/** Platform-free schedule and resource rules, shared with the host-side regression checks. */
final class OccasionPolicy {
    static final long CACHE_LIMIT = 16L * 1024 * 1024;
    static final long SCENE_LIMIT = 7L * 1024 * 1024;
    static final long PHOTO_LIMIT = 1024 * 1024;
    static boolean active(long startsAt, long endsAt, long now) { return startsAt <= now && now < endsAt; }
    static boolean validWindow(long startsAt, long endsAt) {
        return startsAt >= 0 && endsAt > startsAt && endsAt - startsAt <= 7L * 86400000;
    }
    static boolean identifier(String value) { return value != null && value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"); }
    static boolean hash(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    static boolean canStage(long used, long bytes) { return bytes > 0 && bytes <= PHOTO_LIMIT && used >= 0 && used <= CACHE_LIMIT - bytes; }
}
