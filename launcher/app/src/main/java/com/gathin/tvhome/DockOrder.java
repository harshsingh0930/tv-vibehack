package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Places available favourites first, filling missing defaults without stealing another pinned slot. */
final class DockOrder {
    private DockOrder() { }

    static <T> List<T> arrange(List<T> available, List<String> favourites, int slots, boolean fillMissing,
                              Function<T, String> packageName) {
        List<T> remaining = new ArrayList<>(available);
        List<T> ordered = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            String pkg = slot < favourites.size() ? favourites.get(slot) : "";
            T selected = null;
            for (T entry : remaining) if (packageName.apply(entry).equals(pkg)) { selected = entry; break; }
            // Empty saved slots are intentional. Only an unavailable nonempty default gets a replacement.
            if (selected == null && fillMissing && !pkg.isEmpty()) {
                for (T entry : remaining) {
                    if (!favourites.contains(packageName.apply(entry))) { selected = entry; break; }
                }
            }
            ordered.add(selected);
            if (selected != null) remaining.remove(selected);
        }
        ordered.addAll(remaining);
        return ordered;
    }
}
