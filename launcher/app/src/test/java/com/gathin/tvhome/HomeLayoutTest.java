package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeLayoutTest {
    @Test public void sevenTilesAndFocusFitTypicalTvWidths() {
        for (int screen : new int[]{640, 720, 854, 960, 1280, 1920}) {
            int available = screen - 64;
            int tile = HomeLayout.tileWidth(available, 7, true);
            assertTrue("tiles overlap at " + screen, tile * AppAdapter.FOCUS_SCALE <= available / 7f);
            assertTrue(tile > 0);
            assertTrue(HomeLayout.itemHeight(tile, true, true) >= HomeLayout.tileHeight(tile, true) + 16);
        }
    }

    @Test public void fewerColumnsCannotCreateOversizedTiles() {
        for (int columns = 1; columns <= 8; columns++) {
            int width = HomeLayout.tileWidth(896, columns, true);
            assertTrue(width <= 180);
            assertTrue(width * columns <= 896);
        }
    }

    @Test public void gridHasRoomForNamesWhileDockStaysCompact() {
        int width = HomeLayout.tileWidth(896, 7, true);
        assertTrue(HomeLayout.itemHeight(width, false, true) - HomeLayout.itemHeight(width, true, true) >= 22);
    }

    @Test public void classicGeometryRemainsUnchanged() {
        assertEquals(180, HomeLayout.tileWidth(896, 4, false));
        assertEquals(101, HomeLayout.tileHeight(180, false));
        assertEquals(145, HomeLayout.itemHeight(180, true, false));
    }

    @Test public void transientZeroWidthDoesNotProduceInvalidDimensions() {
        assertTrue(HomeLayout.tileWidth(0, 7, true) > 0);
        assertTrue(HomeLayout.tileHeight(HomeLayout.tileWidth(0, 7, true), true) > 0);
    }

}
