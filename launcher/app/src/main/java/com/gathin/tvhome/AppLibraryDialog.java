package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;

/** Horizontal, remote-friendly app picker with a one-shot frosted Home backdrop. */
final class AppLibraryDialog {
    private static final int BACKDROP_DIM_ALPHA = 28;
    static final class Item {
        final String label;
        final Drawable icon;
        final boolean onHome;
        final Runnable action;

        Item(String label, Drawable icon, boolean onHome, Runnable action) {
            this.label = label; this.icon = icon; this.onHome = onHome; this.action = action;
        }
    }

    static void show(Activity activity, View anchor, String title, List<Item> items) {
        if (items.isEmpty() || !anchor.isAttachedToWindow()) return;
        float d = activity.getResources().getDisplayMetrics().density;
        View decor = activity.getWindow().getDecorView();
        int width = Math.min(Math.round(800 * d), decor.getWidth() - Math.round(64 * d));
        int height = Math.min(Math.round(270 * d), decor.getHeight() - Math.round(48 * d));
        int x = (decor.getWidth() - width) / 2, y = (decor.getHeight() - height) / 2;

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Math.round(20 * d), Math.round(18 * d), Math.round(20 * d), Math.round(14 * d));
        content.setBackground(new FrostedMenu.Backdrop(null, d, 24, BACKDROP_DIM_ALPHA));
        content.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 24 * d);
            }
        });
        content.setClipToOutline(true);
        TextView heading = text(activity, title, 24, 0xFFFFFFFF);
        if (android.os.Build.VERSION.SDK_INT >= 28) heading.setFontVariationSettings("'wght' 600");
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, -1, 1));
        TextView count = text(activity, items.size() + " apps", 13, 0xA6FFFFFF);
        titleRow.addView(count, new LinearLayout.LayoutParams(-2, -2));
        content.addView(titleRow, new LinearLayout.LayoutParams(-1, Math.round(32 * d)));
        TextView hint = text(activity, "Choose an app to add to Home", 14, 0xBFFFFFFF);
        if (!"App library".equals(title)) hint.setText("Choose an app for this dock slot");
        content.addView(hint, new LinearLayout.LayoutParams(-1, Math.round(26 * d)));

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(Math.round(5 * d), Math.round(10 * d), Math.round(5 * d), Math.round(5 * d));
        scroll.setFillViewport(true);
        scroll.setFocusable(false);
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        PopupWindow popup = new PopupWindow(content, width, height, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(18 * d);

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int index = i;
            LinearLayout tile = new LinearLayout(activity);
            tile.setId(View.generateViewId());
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(Math.round(10 * d), Math.round(12 * d), Math.round(10 * d), Math.round(8 * d));
            tile.setFocusable(true); tile.setClickable(true);
            tile.setContentDescription(item.label + (item.onHome ? ", on Home" : ", add to Home"));
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_pressed}, rounded(0x50FFFFFF, 0xBBFFFFFF, d));
            states.addState(new int[]{android.R.attr.state_focused}, rounded(0x30FFFFFF, 0xBBFFFFFF, d));
            states.addState(new int[]{}, rounded(0x0CFFFFFF, 0x1AFFFFFF, d));
            tile.setBackground(states);
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(item.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tile.addView(icon, new LinearLayout.LayoutParams(Math.round(64 * d), Math.round(64 * d)));
            TextView name = text(activity, item.label, 16, 0xFFFFFFFF);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(false); name.setMaxLines(2);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, Math.round(42 * d));
            nameParams.topMargin = Math.round(8 * d);
            tile.addView(name, nameParams);
            TextView status = text(activity, item.onHome ? "On Home" : "Add to Home", 12, 0xBFFFFFFF);
            status.setGravity(Gravity.CENTER);
            tile.addView(status, new LinearLayout.LayoutParams(-1, Math.round(20 * d)));
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(Math.round(122 * d), -1);
            tileParams.rightMargin = i == items.size() - 1 ? 0 : Math.round(12 * d);
            row.addView(tile, tileParams);
            tile.setOnKeyListener((view, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    int next = Math.max(0, Math.min(items.size() - 1,
                            index + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
                    row.getChildAt(next).requestFocus();
                    return true;
                }
                return key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN;
            });
            tile.setOnFocusChangeListener((view, focused) -> {
                if (!focused) return;
                int margin = Math.round(5 * d), from = scroll.getScrollX(), to = from;
                if (view.getLeft() - margin < from) to = view.getLeft() - margin;
                else if (view.getRight() + margin > from + scroll.getWidth())
                    to = view.getRight() + margin - scroll.getWidth();
                scroll.smoothScrollTo(Math.max(0, to), 0);
            });
            tile.setOnClickListener(view -> { popup.dismiss(); item.action.run(); });
        }
        popup.setOnDismissListener(() -> {
            if (anchor.isAttachedToWindow()) anchor.requestFocus();
        });
        popup.showAtLocation(decor, Gravity.TOP | Gravity.LEFT, x, y);
        row.getChildAt(0).requestFocus();
        FrostedMenu.captureBackdrop(activity, popup, content,
                new Rect(x, y, x + width, y + height), d, 24, BACKDROP_DIM_ALPHA);
    }

    private static TextView text(Activity activity, String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(activity.getResources().getFont(R.font.outfit));
        if (android.os.Build.VERSION.SDK_INT >= 28) view.setFontVariationSettings("'wght' 500");
        view.setIncludeFontPadding(false); view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private static GradientDrawable rounded(int fill, int edge, float density) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill); shape.setCornerRadius(18 * density);
        shape.setStroke(Math.max(1, Math.round(density)), edge);
        return shape;
    }
}
