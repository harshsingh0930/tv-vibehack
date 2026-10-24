package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

/** Header-only glass material. Native text, icons, focus and click handling are retained. */
public final class LiquidGlassButton extends TextView {
    private final Surface surface;
    private float requestedScale = .985f;
    private final Rect iconBounds = new Rect();

    public LiquidGlassButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        surface = new Surface(getResources().getDisplayMetrics().density);
        setBackground(surface);
        setTextColor(Color.WHITE);
        setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
        setScaleX(requestedScale);
        setScaleY(requestedScale);
        updateGlassState();
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (surface != null) updateGlassState();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (getText().length() == 0) {
            Drawable icon = getCompoundDrawablesRelative()[0];
            if (icon != null) {
                iconBounds.set(icon.getBounds());
                int width = Math.max(1, icon.getIntrinsicWidth()), height = Math.max(1, icon.getIntrinsicHeight());
                int left = (getWidth() - width) / 2, top = (getHeight() - height) / 2;
                icon.setBounds(left, top, left + width, top + height);
                icon.draw(canvas);
                icon.setBounds(iconBounds);
                return;
            }
        }
        super.onDraw(canvas);
    }

    private void updateGlassState() {
        surface.focus(hasFocus());
        float next = isPressed() ? .97f : hasFocus() ? 1f : .985f;
        if (requestedScale == next) return;
        requestedScale = next;
        animate().cancel();
        animate().scaleX(next).scaleY(next).setDuration(240)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    void backdrop(Bitmap bitmap, Rect source, int color) { surface.backdrop(bitmap, source, color); }
    void stopEffects() { animate().cancel(); surface.stop(); }
    @Override protected void onDetachedFromWindow() { stopEffects(); super.onDetachedFromWindow(); }

    private static final class Surface extends Drawable {
        private final float density;
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF shape = new RectF(), inner = new RectF();
        private final Rect source = new Rect(), lensSource = new Rect();
        private final Path clip = new Path(), lensRing = new Path();
        private Bitmap bitmap;
        private ValueAnimator lighting;
        private float light, destination;
        private int tint = 0x487E8C;

        Surface(float density) { this.density = density; }

        void focus(boolean focused) {
            float next = focused ? 1f : 0f;
            if (next == destination) return;
            destination = next;
            if (lighting != null) lighting.cancel();
            lighting = ValueAnimator.ofFloat(light, next);
            lighting.setDuration(260);
            lighting.setInterpolator(new DecelerateInterpolator(1.6f));
            lighting.addUpdateListener(value -> { light = (float) value.getAnimatedValue(); invalidateSelf(); });
            lighting.start();
        }

        void stop() { if (lighting != null) lighting.cancel(); light = destination; }

        void backdrop(Bitmap next, Rect area, int color) {
            bitmap = next; source.set(area); tint = color & 0xFFFFFF;
            if (next != null) {
                lensSource.set(source); lensSource.inset(-2, -1);
                lensSource.intersect(0, 0, next.getWidth(), next.getHeight());
            }
            invalidateSelf();
        }

        @Override protected void onBoundsChange(Rect bounds) {
            shape.set(bounds); shape.inset(density, density);
            inner.set(shape); inner.inset(3f * density, 3f * density);
            float radius = shape.height() / 2f;
            clip.reset(); clip.addRoundRect(shape, radius, radius, Path.Direction.CW);
            lensRing.reset(); lensRing.setFillType(Path.FillType.EVEN_ODD);
            lensRing.addRoundRect(shape, radius, radius, Path.Direction.CW);
            lensRing.addRoundRect(inner, inner.height() / 2f, inner.height() / 2f, Path.Direction.CW);
            sheen.setShader(new LinearGradient(0, shape.top, 0, shape.bottom,
                    new int[]{0x35FFFFFF, 0x0CFFFFFF, 0x16FFFFFF}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
            edge.setStyle(Paint.Style.STROKE);
            edge.setShader(new LinearGradient(shape.left, shape.top, shape.right, shape.bottom,
                    new int[]{0xC0FFFFFF, 0x38FFFFFF, 0x12FFFFFF, 0x8AFFFFFF},
                    new float[]{0f, .35f, .68f, 1f}, Shader.TileMode.CLAMP));
            glint.setShader(new RadialGradient(shape.left + shape.width() * .28f, shape.top,
                    Math.max(1f, shape.width() * .7f), 0x4CFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        }

        @Override public void draw(Canvas canvas) {
            float radius = shape.height() / 2f;
            canvas.save(); canvas.clipPath(clip);
            if (bitmap != null && !source.isEmpty()) {
                canvas.drawBitmap(bitmap, source, shape, bitmapPaint);
                canvas.save(); canvas.clipPath(lensRing);
                canvas.drawBitmap(bitmap, lensSource, shape, bitmapPaint);
                canvas.restore();
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((102 << 24) | 0x0D1018);
            canvas.drawRoundRect(shape, radius, radius, paint);
            paint.setColor((12 << 24) | tint);
            canvas.drawRoundRect(shape, radius, radius, paint);
            sheen.setAlpha(180);
            canvas.drawRoundRect(shape, radius, radius, sheen);
            glint.setAlpha(70);
            canvas.drawRoundRect(shape, radius, radius, glint);
            // Shade only the focused material, leaving its outline and white label clear.
            paint.setColor((int) (35f * light) << 24);
            canvas.drawRoundRect(shape, radius, radius, paint);
            canvas.restore();
            edge.setAlpha((int) (145 + 110 * light));
            edge.setStrokeWidth((1f + .35f * light) * density);
            canvas.drawRoundRect(shape, radius, radius, edge);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(.65f * density); paint.setColor(0x4D000000);
            canvas.drawRoundRect(inner, inner.height() / 2f, inner.height() / 2f, paint);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
