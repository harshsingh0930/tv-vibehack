package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/** Rounded Play Next material. Only the ambient layer is sampled; children stay sharp. */
public final class FrostedPanel extends FrameLayout {
    private static final int SCALE = 6;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable changed = this::schedule;
    private final Runnable refresh = () -> { pending = false; capture(); };
    private final ViewTreeObserver.OnScrollChangedListener scrolled = this::schedule;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tabSheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect visible = new Rect(), area = new Rect();
    private final RectF bounds = new RectF();
    private final Path clip = new Path();
    private final float density;
    private AmbientView ambient;
    private ViewGroup root;
    private Bitmap bitmap;
    private int[] pixels, scratch;
    private boolean pending;
    private long capturedAt;
    private int captures;
    private float shoulderRight;

    public FrostedPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        tint.setColor(0x88171820);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(density);
        drop.setColor(0x28000000);
        innerEdge.setStyle(Paint.Style.STROKE);
        innerEdge.setStrokeWidth(2 * density);
        innerEdge.setColor(0x24000000);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        root = getRootView().findViewById(R.id.root);
        ambient = root == null ? null : root.findViewById(R.id.ambient);
        if (ambient != null) ambient.addColorFrameListener(changed);
        getViewTreeObserver().addOnScrollChangedListener(scrolled);
        schedule();
    }

    @Override protected void onDetachedFromWindow() {
        main.removeCallbacks(refresh); pending = false;
        if (ambient != null) ambient.removeColorFrameListener(changed);
        if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnScrollChangedListener(scrolled);
        ambient = null; root = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) schedule();
        else { main.removeCallbacks(refresh); pending = false; }
    }

    private void schedule() {
        if (pending || ambient == null || !isAttachedToWindow() || getWindowVisibility() != View.VISIBLE
                || !getLocalVisibleRect(visible)) return;
        pending = true;
        main.postDelayed(refresh, Math.max(0, 48 - (SystemClock.uptimeMillis() - capturedAt)));
    }

    private void capture() {
        if (ambient == null || root == null || getWidth() == 0 || getHeight() == 0
                || getWindowVisibility() != View.VISIBLE || !getLocalVisibleRect(visible)) return;
        area.set(0, 0, getWidth(), getHeight());
        root.offsetDescendantRectToMyCoords(this, area);
        int width = Math.max(1, (getWidth() + SCALE - 1) / SCALE);
        int height = Math.max(1, (getHeight() + SCALE - 1) / SCALE);
        if (bitmap == null || bitmap.getWidth() != width || bitmap.getHeight() != height) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            pixels = new int[width * height]; scratch = new int[pixels.length];
        }
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);
        canvas.scale(width / (float) getWidth(), height / (float) getHeight());
        canvas.translate(-area.left, -area.top);
        int layer = canvas.saveLayerAlpha(null, Math.round(255 * ambient.getAlpha()));
        ambient.draw(canvas); canvas.restoreToCount(layer);
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int pass = 0; pass < 3; pass++) {
            BackdropBlur.pass(pixels, scratch, width, height, 4, true);
            BackdropBlur.pass(scratch, pixels, width, height, 4, false);
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        capturedAt = SystemClock.uptimeMillis(); captures++;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bounds.set(density / 2, density / 2, w - density / 2, h - density / 2);
        updateShape();
        sheen.setShader(new LinearGradient(0, 0, 0, Math.max(1, h),
                0x20FFFFFF, 0x03FFFFFF, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, w, Math.max(1, h),
                0x45FFFFFF, 0x16FFFFFF, Shader.TileMode.CLAMP));
        schedule();
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateShape();
    }

    private void updateShape() {
        View title = findViewById(R.id.watchTitle);
        float d = density, l = bounds.left, r = bounds.right, b = bounds.bottom;
        float t = 2 * d, bodyTop = 38 * d;
        float capRight = Math.min(r - 90 * d,
                (title == null ? 126 * d : title.getMeasuredWidth() + 40 * d));
        float capRadius = 24 * d, radius = 26 * d;
        float corner = radius * .448f;
        shoulderRight = capRight + 58 * d;
        // A gently raised crest flows into the tray in one tangent-continuous sweep.
        // Shape and light are precomputed; title/card positions and focus stay unchanged.
        clip.reset();
        clip.moveTo(l + capRadius, t);
        clip.lineTo(capRight - 8 * d, t);
        clip.cubicTo(capRight + 18 * d, t, capRight + 10 * d, bodyTop,
                shoulderRight, bodyTop);
        clip.lineTo(r - radius, bodyTop);
        clip.cubicTo(r - corner, bodyTop, r, bodyTop + corner, r, bodyTop + radius);
        clip.lineTo(r, b - radius);
        clip.cubicTo(r, b - corner, r - corner, b, r - radius, b);
        clip.lineTo(l + radius, b);
        clip.cubicTo(l + corner, b, l, b - corner, l, b - radius);
        clip.lineTo(l, t + capRadius);
        clip.cubicTo(l, t + 9 * d, l + 9 * d, t, l + capRadius, t);
        clip.close();
        tabSheen.setShader(new LinearGradient(l, t, l, bodyTop,
                new int[]{0x20FFFFFF, 0x0AFFFFFF, 0x00FFFFFF},
                new float[]{0f, .45f, 1f}, Shader.TileMode.CLAMP));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save(); canvas.translate(0, 2 * density);
        canvas.drawPath(clip, drop); canvas.restore();
        canvas.save(); canvas.clipPath(clip);
        if (bitmap != null) canvas.drawBitmap(bitmap, null, bounds, bitmapPaint);
        canvas.drawPath(clip, tint);
        canvas.drawPath(clip, sheen);
        canvas.drawRect(0, 0, shoulderRight, 38 * density, tabSheen);
        canvas.restore();
        canvas.drawPath(clip, innerEdge);
        canvas.drawPath(clip, rim);
    }

    void dump(java.io.PrintWriter writer) {
        writer.println("  PlayNextFrost captures=" + captures + " pending=" + pending
                + " bitmapBytes=" + (bitmap == null ? 0 : bitmap.getAllocationByteCount()));
    }
}
