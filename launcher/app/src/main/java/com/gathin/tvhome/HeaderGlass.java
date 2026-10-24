package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

/** Shared quarter-resolution backdrop, following ambient transitions without idle polling. */
final class HeaderGlass {
    private static final int SCALE = 4;
    private static final long FRAME_INTERVAL_MS = 48;
    private final ViewGroup root;
    private final AmbientView ambient;
    private final View bar;
    private final LiquidGlassButton[] buttons;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refresh = () -> { refreshPending = false; capture(); };
    private Bitmap bitmap;
    private int[] pixels, scratch;
    private int color = 0xFF487E8C;
    private boolean active;
    private boolean refreshPending;
    private long capturedAt;
    private int captures;
    private View wallpaper;

    HeaderGlass(ViewGroup root, AmbientView ambient, View bar, LiquidGlassButton... buttons) {
        this.root = root; this.ambient = ambient; this.bar = bar; this.buttons = buttons;
        ambient.addColorFrameListener(this::colorFrameChanged);
        bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (active && (l != ol || t != ot || r != or || b != ob)) schedule(0);
        });
    }

    void setWallpaper(View wallpaper) { this.wallpaper = wallpaper; }
    void resume() { active = true; schedule(0); }
    void pause() {
        active = false; main.removeCallbacks(refresh);
        refreshPending = false;
        for (LiquidGlassButton button : buttons) button.stopEffects();
    }
    private void colorFrameChanged() {
        // Keep the pending deadline during rapid navigation, rather than postponing it.
        if (active && !refreshPending) {
            schedule(Math.max(0, FRAME_INTERVAL_MS - (SystemClock.uptimeMillis() - capturedAt)));
        }
    }
    private void schedule(long delay) {
        main.removeCallbacks(refresh);
        refreshPending = true;
        main.postDelayed(refresh, delay);
    }

    private void capture() {
        if (!active || !bar.isAttachedToWindow() || bar.getWidth() == 0) return;
        color = ambient.displayedColor();
        Rect area = new Rect(0, 0, bar.getWidth(), bar.getHeight());
        root.offsetDescendantRectToMyCoords(bar, area);
        int padding = Math.round(12 * root.getResources().getDisplayMetrics().density);
        area.inset(-padding, -padding); area.intersect(0, 0, root.getWidth(), root.getHeight());
        int width = Math.max(1, (area.width() + SCALE - 1) / SCALE);
        int height = Math.max(1, (area.height() + SCALE - 1) / SCALE);
        if (bitmap == null || bitmap.getWidth() != width || bitmap.getHeight() != height) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            pixels = new int[width * height]; scratch = new int[pixels.length];
        }
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.BLACK);
        canvas.scale(width / (float) area.width(), height / (float) area.height());
        canvas.translate(-area.left, -area.top);
        if (wallpaper != null) wallpaper.draw(canvas);
        if (ambient.getVisibility() == View.VISIBLE && ambient.getWidth() > 0 && ambient.getHeight() > 0) {
            int layer = canvas.saveLayerAlpha(null, Math.round(255 * ambient.getAlpha()));
            // Foreground text/icons cannot smear into their own backdrop.
            ambient.draw(canvas); canvas.restoreToCount(layer);
        }
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int pass = 0; pass < 3; pass++) {
            BackdropBlur.pass(pixels, scratch, width, height, 4, true);
            BackdropBlur.pass(scratch, pixels, width, height, 4, false);
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        for (LiquidGlassButton button : buttons) {
            Rect bounds = new Rect(0, 0, button.getWidth(), button.getHeight());
            root.offsetDescendantRectToMyCoords(button, bounds);
            Rect source = new Rect(Math.round((bounds.left - area.left) * width / (float) area.width()),
                    Math.round((bounds.top - area.top) * height / (float) area.height()),
                    Math.round((bounds.right - area.left) * width / (float) area.width()),
                    Math.round((bounds.bottom - area.top) * height / (float) area.height()));
            source.intersect(0, 0, width, height);
            button.backdrop(bitmap, source, color);
        }
        captures++;
        capturedAt = SystemClock.uptimeMillis();
    }

    void dump(java.io.PrintWriter writer) {
        writer.println("  HeaderGlass active=" + active + " captures=" + captures
                + " bitmapBytes=" + (bitmap == null ? 0 : bitmap.getAllocationByteCount()));
    }
}
