package com.gathin.tvhome;

/** Shared tile geometry, in dp. The classic four-column layout keeps its original sizes. */
final class HomeLayout {
    private HomeLayout() { }

    static int tileWidth(int availableWidth, int columns, boolean mountain) {
        if (!mountain) return 180;
        return Math.max(1, Math.min(180, availableWidth / Math.max(1, columns) - 16));
    }

    static int tileHeight(int width, boolean mountain) {
        return mountain ? Math.max(1, Math.round(width * 9f / 16f)) : 101;
    }

    static int itemHeight(int width, boolean dock, boolean mountain) {
        return mountain ? tileHeight(width, true) + 16 + (dock ? 0 : 30) : 145;
    }
}
