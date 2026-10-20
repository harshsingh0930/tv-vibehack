package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/** Small remote-friendly menus, with a one-shot blurred snapshot of the launcher behind them. */
final class FrostedMenu {
    static final class Item {
        final String label;
        final Runnable action;
        final Boolean checked;
        Item(String label, Runnable action) { this(label, action, null); }
        Item(String label, Runnable action, Boolean checked) {
            this.label = label; this.action = action; this.checked = checked;
        }
    }

    static void show(Activity activity, View anchor, String title, List<Item> items) {
        if (items.isEmpty() || !anchor.isAttachedToWindow()) return;
        float density = activity.getResources().getDisplayMetrics().density;
        int width = Math.round((title == null ? 204 : 256) * density);
        int height = Math.round((16 + (title == null ? 0 : 28) + Math.min(240, items.size() * 40)) * density);
        int margin = Math.round(18 * density);
        View decor = activity.getWindow().getDecorView();
        int[] location = new int[2]; anchor.getLocationInWindow(location);
        int x = Math.max(margin, Math.min(location[0] + (anchor.getWidth() - width) / 2, decor.getWidth() - width - margin));
        int y = location[1] - height - Math.round(12 * density);
        if (y < margin) y = location[1] + anchor.getHeight() + Math.round(12 * density);
        y = Math.max(margin, Math.min(y, decor.getHeight() - height - margin));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Math.round(8 * density), Math.round(8 * density), Math.round(8 * density), Math.round(8 * density));
        content.setBackground(new Backdrop(null, density));
        content.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 18 * density);
            }
        });
        content.setClipToOutline(true);
        if (title != null) {
            TextView heading = text(activity, title, 12);
            heading.setTextColor(0xB3FFFFFF);
            heading.setPadding(Math.round(10 * density), 0, Math.round(10 * density), 0);
            content.addView(heading, new LinearLayout.LayoutParams(-1, Math.round(28 * density)));
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout rows = new LinearLayout(activity); rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        PopupWindow popup = new PopupWindow(content, width, height, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true); popup.setElevation(18 * density);
        for (Item item : items) {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(Math.round(10 * density), 0, Math.round(8 * density), 0);
            row.setFocusable(true); row.setClickable(true);
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused}, rounded(0x38FFFFFF, density));
            states.addState(new int[]{android.R.attr.state_pressed}, rounded(0x50FFFFFF, density));
            states.addState(new int[]{}, rounded(Color.TRANSPARENT, density));
            row.setBackground(states);
            TextView label = text(activity, item.label, 15);
            row.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
            row.setContentDescription(item.label + (item.checked == null ? "" : item.checked ? ", on" : ", off"));
            if (item.checked != null) {
                Switch toggle = new Switch(activity);
                toggle.setChecked(item.checked); toggle.setFocusable(false); toggle.setClickable(false);
                toggle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                toggle.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
                toggle.setTrackTintList(ColorStateList.valueOf(item.checked ? 0xFF84B8B6 : 0xFF5B5C65));
                row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
            }
            rows.addView(row, new LinearLayout.LayoutParams(-1, Math.round(40 * density)));
            row.setOnClickListener(v -> { popup.dismiss(); item.action.run(); });
        }
        popup.showAtLocation(decor, Gravity.TOP | Gravity.LEFT, x, y);
        rows.getChildAt(0).requestFocus();
        captureBackdrop(activity, popup, content, new Rect(x, y, x + width, y + height), density);
    }

    private static TextView text(Activity activity, String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER_VERTICAL); view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setTypeface(activity.getResources().getFont(R.font.outfit));
        view.setFontVariationSettings("'wght' 500");
        view.setIncludeFontPadding(false);
        view.setLetterSpacing(.01f);
        return view;
    }

    private static GradientDrawable rounded(int color, float density) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(10 * density); return shape;
    }

    private static void captureBackdrop(Activity activity, PopupWindow popup, View content, Rect area, float density) {
        captureBackdrop(activity, popup, content, area, density, 18);
    }

    static void captureBackdrop(Activity activity, PopupWindow popup, View content, Rect area,
                                float density, float cornerDp) {
        captureBackdrop(activity, popup, content, area, density, cornerDp, 0);
    }

    static void captureBackdrop(Activity activity, PopupWindow popup, View content, Rect area,
                                float density, float cornerDp, int dimAlpha) {
        Bitmap small = Bitmap.createBitmap(Math.max(1, area.width() / 3), Math.max(1, area.height() / 3), Bitmap.Config.ARGB_8888);
        Handler main = new Handler(Looper.getMainLooper());
        try {
            PixelCopy.request(activity.getWindow(), area, small, result -> {
                if (result != PixelCopy.SUCCESS || !popup.isShowing()) return;
                new Thread(() -> {
                    RenderScript context = null;
                    Allocation input = null, output = null;
                    ScriptIntrinsicBlur blur = null;
                    try {
                        context = RenderScript.create(activity.getApplicationContext());
                        input = Allocation.createFromBitmap(context, small);
                        output = Allocation.createTyped(context, input.getType());
                        blur = ScriptIntrinsicBlur.create(context, Element.U8_4(context));
                        blur.setRadius(14f); blur.setInput(input); blur.forEach(output); output.copyTo(small);
                    } catch (Exception ignored) { /* Downsampled frosted backdrop is still available. */ }
                    finally {
                        if (blur != null) blur.destroy();
                        if (input != null) input.destroy();
                        if (output != null) output.destroy();
                        if (context != null) context.destroy();
                    }
                    main.post(() -> {
                        if (popup.isShowing()) {
                            content.setBackground(new Backdrop(small, density, cornerDp, dimAlpha));
                            android.util.Log.i("HomeMenu", "Frosted backdrop ready");
                        }
                    });
                }, "menu-blur").start();
            }, main);
        } catch (Exception ignored) { }
    }

    static final class Backdrop extends Drawable {
        private final Bitmap bitmap;
        private final float density;
        private final float radius;
        private final int dimAlpha;
        // The bitmap must retain full opacity when translucent rim/tint paints change.
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Backdrop(Bitmap bitmap, float density) { this(bitmap, density, 18); }
        Backdrop(Bitmap bitmap, float density, float cornerDp) {
            this(bitmap, density, cornerDp, 0);
        }
        Backdrop(Bitmap bitmap, float density, float cornerDp, int dimAlpha) {
            this.bitmap = bitmap; this.density = density; radius = cornerDp * density;
            this.dimAlpha = dimAlpha;
        }
        @Override public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            Path path = new Path(); path.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            canvas.save(); canvas.clipPath(path);
            paint.setShader(null); paint.setStyle(Paint.Style.FILL);
            if (bitmap != null) canvas.drawBitmap(bitmap, null, bounds, bitmapPaint);
            paint.setColor(bitmap == null ? 0xF2282830 : 0xB51C1C24); canvas.drawRect(bounds, paint);
            paint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom, 0x20FFFFFF, 0x04FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawRect(bounds, paint); paint.setShader(null);
            if (dimAlpha > 0) {
                paint.setColor(dimAlpha << 24); canvas.drawRect(bounds, paint);
            }
            paint.setColor(0x40FFFFFF); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(density);
            bounds.inset(density / 2, density / 2); canvas.drawRoundRect(bounds, radius, radius, paint);
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
