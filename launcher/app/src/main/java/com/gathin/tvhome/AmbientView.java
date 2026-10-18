package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Soft artwork-coloured light, rendered without allocating or blurring full-screen bitmaps. */
public final class AmbientView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator transition;
    private int color = 0xFF487E8C;

    public AmbientView(Context context, AttributeSet attrs) { super(context, attrs); }

    void showColor(int next) {
        if (transition != null) transition.cancel();
        int previous = color;
        transition = ValueAnimator.ofArgb(previous, next);
        transition.setDuration(420);
        transition.addUpdateListener(value -> { color = (int) value.getAnimatedValue(); invalidate(); });
        transition.start();
        animate().cancel();
        animate().alpha(1f).setDuration(280).start();
    }

    @Override protected void onDraw(Canvas canvas) {
        float radius = getWidth() * .62f;
        int rgb = color & 0xFFFFFF;
        paint.setShader(new RadialGradient(getWidth() * .32f, getHeight() * .42f, radius,
                new int[]{0x5C000000 | rgb, 0x26000000 | rgb, rgb}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }

    @Override protected void onDetachedFromWindow() {
        if (transition != null) transition.cancel();
        super.onDetachedFromWindow();
    }
}
