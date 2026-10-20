package com.gathin.tvhome;

import android.graphics.Color;

/** Sliding-window box blur over a small, opaque ambient bitmap; no per-pass allocations. */
final class BackdropBlur {
    private BackdropBlur() { }

    static void pass(int[] source, int[] destination, int width, int height, int radius, boolean horizontal) {
        int lines = horizontal ? height : width, length = horizontal ? width : height;
        int stride = horizontal ? 1 : width, count = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int base = horizontal ? line * width : line;
            int red = 0, green = 0, blue = 0;
            for (int n = -radius; n <= radius; n++) {
                int pixel = source[base + Math.max(0, Math.min(length - 1, n)) * stride];
                red += Color.red(pixel); green += Color.green(pixel); blue += Color.blue(pixel);
            }
            for (int n = 0; n < length; n++) {
                destination[base + n * stride] = Color.rgb(red / count, green / count, blue / count);
                int remove = source[base + Math.max(0, n - radius) * stride];
                int add = source[base + Math.min(length - 1, n + radius + 1) * stride];
                red += Color.red(add) - Color.red(remove);
                green += Color.green(add) - Color.green(remove);
                blue += Color.blue(add) - Color.blue(remove);
            }
        }
    }
}
