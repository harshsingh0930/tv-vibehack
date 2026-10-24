package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

public class DockOrderTest {
    private List<String> arrange(List<String> installed, List<String> pinned, int slots, boolean fill) {
        return DockOrder.arrange(installed, pinned, slots, fill, value -> value);
    }

    @Test public void preservesReferenceOrderRegardlessOfInstalledOrder() {
        assertEquals(Arrays.asList("netflix", "disney", "prime", "youtube", "apple", "spotify", "store", "extra"),
                arrange(Arrays.asList("extra", "youtube", "netflix", "store", "spotify", "apple", "prime", "disney"),
                        Arrays.asList("netflix", "disney", "prime", "youtube", "apple", "spotify", "store"), 7, true));
    }

    @Test public void missingAppDoesNotStealLaterFavourite() {
        List<String> installed = Arrays.asList("youtube", "local-player", "store", "extra");
        List<String> result = arrange(installed, Arrays.asList("missing", "youtube", "store"), 3, true);
        assertEquals(Arrays.asList("local-player", "youtube", "store", "extra"), result);
        assertEquals(installed.size(), new HashSet<>(result).size());
        assertEquals(Arrays.asList("youtube", "local-player", "store", "extra"), installed);
    }

    @Test public void explicitEmptySlotStaysEmpty() {
        assertEquals(Arrays.asList(null, "youtube", "extra"),
                arrange(Arrays.asList("extra", "youtube"), Arrays.asList("", "youtube"), 2, true));
    }

    @Test public void classicDoesNotFillMissingFavourites() {
        assertEquals(Arrays.asList(null, "youtube", "extra"),
                arrange(Arrays.asList("extra", "youtube"), Arrays.asList("missing", "youtube"), 2, false));
    }

    @Test public void noInstalledAppsLeavesSelectableAddSlots() {
        List<String> result = arrange(Collections.emptyList(), Arrays.asList("a", "b", "c"), 3, true);
        assertEquals(Arrays.asList(null, null, null), result);
    }

    @Test public void allLaterFavouritesRemainReservedWhenNoFallbackExists() {
        assertEquals(Arrays.asList(null, "youtube", "store"),
                arrange(Arrays.asList("store", "youtube"), Arrays.asList("missing", "youtube", "store"), 3, true));
    }
}
