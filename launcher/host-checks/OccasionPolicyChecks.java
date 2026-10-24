package com.gathin.tvhome;
/** Laptop-only regression checks; no TV or emulator access. */
public final class OccasionPolicyChecks {
    static int passed;
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); passed++; }
    public static void main(String[] args) {
        long start = 1792765800000L, end = start + 6 * 3600000;
        check(!OccasionPolicy.active(start, end, start - 1), "Must not start early");
        check(OccasionPolicy.active(start, end, start), "Start inclusive");
        check(OccasionPolicy.active(start, end, end - 1), "Last active millisecond");
        check(!OccasionPolicy.active(start, end, end), "End exclusive even offline");
        check(!OccasionPolicy.active(start, end, end + 86400000), "Skip expired after wake");
        check(OccasionPolicy.validWindow(start, end), "Six-hour window");
        check(!OccasionPolicy.validWindow(start, start), "Reject zero duration");
        check(!OccasionPolicy.validWindow(start, start + 8L * 86400000), "Reject oversized window");
        check(!OccasionPolicy.validWindow(-1, end), "Reject negative epoch");
        check(OccasionPolicy.identifier("07ad429f-ddbc-440b-a183-9e1c914361f8"), "Valid UUID");
        check(!OccasionPolicy.identifier("../../shared_prefs"), "Reject path traversal");
        check(!OccasionPolicy.hash("abc/../secret"), "Reject malformed hash");
        check(OccasionPolicy.hash("a".repeat(64)), "SHA256");
        check(OccasionPolicy.canStage(15L * 1024 * 1024, 1024 * 1024), "Exact cache cap");
        check(!OccasionPolicy.canStage(15L * 1024 * 1024 + 1, 1024 * 1024), "Cap cannot overflow");
        check(!OccasionPolicy.canStage(0, 1024 * 1024 + 1), "Photo limit");
        check(!OccasionPolicy.canStage(0, -1), "Negative size rejected");
        System.out.println("PASS: " + passed + " offline timing, validation and cache boundary checks");
    }
}
