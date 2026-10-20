package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** A small wallpaper sample behind the clock; refreshed only on photo/layout changes. */
final class DreamClockGlass extends View {
    private static final int SCALE = 4;
    private final float density;
    private final RectF bounds = new RectF();
    private final RectF innerBounds = new RectF(), refractedBounds = new RectF();
    private final Path clip = new Path();
    private final Path edgeBand = new Path();
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator fade = ValueAnimator.ofFloat(0f, 1f);
    private Bitmap wallpaper, texture, previous;
    private int[] pixels, scratch;
    private float progress = 1f;
    private int captures;

    DreamClockGlass(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
        tint.setColor(0x190E1824);
        shadow.setColor(0x29000000);
        rim.setStyle(Paint.Style.STROKE); rim.setStrokeWidth(density);
        innerRim.setStyle(Paint.Style.STROKE); innerRim.setStrokeWidth(1.2f * density);
        fade.setDuration(900);
        fade.setInterpolator(new android.view.animation.LinearInterpolator());
        fade.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            if (progress >= 1f) previous = null;
            invalidate();
        });
    }

    void follow(View panel, View timeRow) {
        RectF next = new RectF(panel.getX() - 20 * density, panel.getY() - 18 * density,
                panel.getX() + timeRow.getRight() + 20 * density,
                panel.getY() + panel.getHeight() + 18 * density);
        if (next.equals(bounds) || panel.getWidth() == 0 || panel.getHeight() == 0) return;
        bounds.set(next); clip.reset(); clip.addRoundRect(bounds, 30 * density, 30 * density, Path.Direction.CW);
        innerBounds.set(bounds); innerBounds.inset(2 * density, 2 * density);
        edgeBand.reset(); edgeBand.setFillType(Path.FillType.EVEN_ODD);
        edgeBand.addRoundRect(bounds, 30 * density, 30 * density, Path.Direction.CW);
        RectF core = new RectF(bounds); core.inset(4 * density, 4 * density);
        edgeBand.addRoundRect(core, 26 * density, 26 * density, Path.Direction.CW);
        refractedBounds.set(bounds); refractedBounds.inset(-9 * density, -9 * density);
        sheen.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[]{0x18FFFFFF, 0x02FFFFFF, 0x0AFFFFFF}, null, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[]{0xBDFFFFFF, 0x20E3F2FF, 0x75FFFFFF}, null, Shader.TileMode.CLAMP));
        innerRim.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                0x39C8ECFF, 0x60FFF0DA, Shader.TileMode.CLAMP));
        if (wallpaper != null) showPhoto(wallpaper, false);
        invalidate();
    }

    void showPhoto(Bitmap photo, boolean animate) {
        wallpaper = photo;
        if (bounds.isEmpty() || getWidth() == 0 || getHeight() == 0) return;
        Bitmap blurred = capture(photo);
        fade.cancel(); previous = texture; texture = blurred;
        progress = animate && previous != null ? 0f : 1f;
        if (progress == 0f) fade.start(); else { previous = null; invalidate(); }
    }

    private Bitmap capture(Bitmap photo) {
        int width = Math.max(1, (int) Math.ceil(bounds.width() / SCALE));
        int height = Math.max(1, (int) Math.ceil(bounds.height() / SCALE));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.BLACK);
        canvas.scale(width / bounds.width(), height / bounds.height());
        canvas.translate(-bounds.left, -bounds.top);
        // Same centre-crop transform as the full-screen ImageViews; text is never sampled.
        float scale = Math.max(getWidth() / (float) photo.getWidth(), getHeight() / (float) photo.getHeight());
        float left = (getWidth() - photo.getWidth() * scale) / 2f;
        float top = (getHeight() - photo.getHeight() * scale) / 2f;
        image.setAlpha(255);
        canvas.drawBitmap(photo, null, new RectF(left, top, left + photo.getWidth() * scale,
                top + photo.getHeight() * scale), image);
        if (pixels == null || pixels.length != width * height) {
            pixels = new int[width * height]; scratch = new int[pixels.length];
        }
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int pass = 0; pass < 2; pass++) {
            BackdropBlur.pass(pixels, scratch, width, height, 3, true);
            BackdropBlur.pass(scratch, pixels, width, height, 3, false);
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height); captures++;
        return bitmap;
    }

    @Override protected void onDraw(Canvas canvas) {
        if (bounds.isEmpty()) return;
        canvas.save(); canvas.translate(0, 3 * density);
        canvas.drawRoundRect(bounds, 30 * density, 30 * density, shadow); canvas.restore();
        canvas.save(); canvas.clipPath(clip);
        // The original wallpaper remains visible through the material, with a soft blur mixed in.
        image.setAlpha(112);
        if (previous != null) canvas.drawBitmap(previous, null, bounds, image);
        image.setAlpha(Math.round(112 * progress));
        if (texture != null) canvas.drawBitmap(texture, null, bounds, image);
        image.setAlpha(255);
        canvas.drawRect(bounds, tint); canvas.drawRect(bounds, sheen);
        canvas.restore();
        // A narrow magnified sample forms the glass edge without a full-screen shader or capture loop.
        canvas.save(); canvas.clipPath(edgeBand);
        image.setAlpha(150);
        if (previous != null) canvas.drawBitmap(previous, null, refractedBounds, image);
        image.setAlpha(Math.round(150 * progress));
        if (texture != null) canvas.drawBitmap(texture, null, refractedBounds, image);
        image.setAlpha(255); canvas.restore();
        canvas.drawRoundRect(bounds, 30 * density, 30 * density, rim);
        canvas.drawRoundRect(innerBounds, 28 * density, 28 * density, innerRim);
    }

    void clear() {
        fade.cancel(); wallpaper = texture = previous = null; pixels = scratch = null;
        invalidate();
    }

    @Override protected void onDetachedFromWindow() { clear(); super.onDetachedFromWindow(); }

    void dump(java.io.PrintWriter writer) {
        writer.println("ClockGlass captures=" + captures + " animating=" + fade.isRunning()
                + " bitmapBytes=" + ((texture == null ? 0 : texture.getAllocationByteCount())
                + (previous == null ? 0 : previous.getAllocationByteCount())) + " bounds=" + bounds);
    }
}
