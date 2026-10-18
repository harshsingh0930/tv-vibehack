package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Full-screen glass canvas used only while focus is inside Play Next. */
public class TrackedGlassView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF viewport = new RectF();
    private final RectF dirty = new RectF();
    private final Path clip = new Path();
    private final float density;
    private float radius;

    public TrackedGlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0xCCFFFFFF);
        glow.setStyle(Paint.Style.STROKE);
    }

    void setHighlight(RectF bounds, float cornerRadius, RectF visibleArea) {
        if (rect.equals(bounds) && viewport.equals(visibleArea) && radius == cornerRadius) return;
        dirty.set(rect);
        dirty.union(bounds);
        dirty.inset(-8 * density, -8 * density);
        rect.set(bounds);
        viewport.set(visibleArea);
        radius = cornerRadius;
        fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                0x3DFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
        specular.setShader(new LinearGradient(0, rect.top, 0, rect.top + rect.height() * .45f,
                0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        invalidate((int) Math.floor(dirty.left), (int) Math.floor(dirty.top),
                (int) Math.ceil(dirty.right), (int) Math.ceil(dirty.bottom));
    }

    @Override protected void onDraw(Canvas canvas) {
        if (rect.isEmpty()) return;
        canvas.save();
        canvas.clipRect(viewport);
        for (int i = 1; i <= 3; i++) {
            glow.setStrokeWidth(i * 2f * density);
            glow.setColor((0x30 / i) << 24 | 0xFFFFFF);
            float offset = i * density;
            canvas.drawRoundRect(rect.left - offset, rect.top - offset, rect.right + offset, rect.bottom + offset,
                    radius + offset, radius + offset, glow);
        }
        canvas.drawRoundRect(rect, radius, radius, fill);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + rect.height() * .45f, specular);
        canvas.restore();
        canvas.drawRoundRect(rect, radius, radius, rim);
        canvas.restore();
    }
}
