package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Offline app search using the same one-shot blur and glass material as the app library. */
final class AppSearchDialog {
    static PopupWindow show(Activity activity, View anchor, AppAdapter apps) {
        if (!anchor.isAttachedToWindow()) return null;
        return new Sheet(activity, anchor, apps).show();
    }

    private static final class Sheet implements TextWatcher {
        private final Activity activity;
        private final View anchor;
        private final AppAdapter apps;
        private final List<AppAdapter.Entry> installed;
        private final List<AppAdapter.Entry> matches = new ArrayList<>();
        private final float density;
        private final LinearLayout panel, tiles;
        private final HorizontalScrollView scroll;
        private final EditText query;
        private final TextView count, empty, hint;
        private final PopupWindow popup;
        private final InputMethodManager keyboard;
        private IBinder inputToken;
        private final int x, y, width, height;

        Sheet(Activity activity, View anchor, AppAdapter apps) {
            this.activity = activity; this.anchor = anchor; this.apps = apps;
            installed = apps.searchableApps();
            density = activity.getResources().getDisplayMetrics().density;
            keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            View decor = activity.getWindow().getDecorView();
            width = Math.min(dp(800), decor.getWidth() - dp(64));
            height = Math.min(dp(292), decor.getHeight() - dp(72));
            x = (decor.getWidth() - width) / 2;
            y = dp(32);

            panel = new LinearLayout(activity);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(22), dp(18), dp(22), dp(16));
            panel.setBackground(new FrostedMenu.Backdrop(null, density, 24));
            panel.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(24));
                }
            });
            panel.setClipToOutline(true);

            LinearLayout title = new LinearLayout(activity);
            title.setGravity(Gravity.CENTER_VERTICAL);
            TextView heading = text(activity.getString(R.string.search_apps), 24, Color.WHITE);
            title.addView(heading, new LinearLayout.LayoutParams(0, -1, 1));
            count = text("", 14, 0xBFFFFFFF);
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-2, -2);
            countParams.rightMargin = dp(16);
            title.addView(count, countParams);
            TextView close = text("×", 28, Color.WHITE);
            close.setId(View.generateViewId());
            close.setGravity(Gravity.CENTER);
            close.setFocusable(true); close.setClickable(true);
            close.setContentDescription(activity.getString(R.string.close_search));
            close.setBackground(states(18));
            title.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
            panel.addView(title, new LinearLayout.LayoutParams(-1, dp(36)));

            query = new EditText(activity);
            query.setId(View.generateViewId());
            query.setSingleLine(true);
            query.setHint(R.string.search_apps_hint);
            query.setTextColor(Color.WHITE); query.setHintTextColor(0xB3FFFFFF);
            query.setTextSize(20);
            query.setTypeface(activity.getResources().getFont(R.font.outfit));
            query.setIncludeFontPadding(false);
            query.setPadding(dp(14), 0, dp(14), 0);
            query.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
            query.setCompoundDrawablePadding(dp(12));
            query.setBackground(states(16));
            query.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            // Show app icons first. OK on the field brings up the TV keyboard.
            query.setShowSoftInputOnFocus(false);
            LinearLayout.LayoutParams queryParams = new LinearLayout.LayoutParams(-1, dp(48));
            queryParams.topMargin = dp(8);
            panel.addView(query, queryParams);

            FrameLayout resultArea = new FrameLayout(activity);
            LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, 0, 1);
            resultParams.topMargin = dp(8);
            panel.addView(resultArea, resultParams);
            scroll = new HorizontalScrollView(activity);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setClipToPadding(false); scroll.setClipChildren(false);
            scroll.setPadding(dp(6), dp(6), dp(6), dp(6));
            scroll.setFillViewport(true); scroll.setFocusable(false);
            tiles = new LinearLayout(activity);
            tiles.setGravity(Gravity.CENTER_VERTICAL);
            tiles.setClipChildren(false);
            scroll.addView(tiles, new HorizontalScrollView.LayoutParams(-2, -1));
            resultArea.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
            empty = text(activity.getString(R.string.no_matching_apps), 18, 0xBFFFFFFF);
            empty.setGravity(Gravity.CENTER);
            resultArea.addView(empty, new FrameLayout.LayoutParams(-1, -1));
            hint = text(activity.getString(R.string.search_keyboard_hint), 12, 0xB3FFFFFF);
            panel.addView(hint, new LinearLayout.LayoutParams(-1, dp(20)));

            popup = new PopupWindow(panel, width, height, true);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popup.setOutsideTouchable(true); popup.setElevation(dp(18));
            popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
            popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            popup.setAnimationStyle(R.style.SearchPopupAnimation);
            popup.setOnDismissListener(() -> {
                hideKeyboard();
                for (int i = 0; i < tiles.getChildCount(); i++) tiles.getChildAt(i).animate().cancel();
                if (anchor.isAttachedToWindow()) anchor.requestFocus();
            });
            close.setOnClickListener(view -> popup.dismiss());
            close.setOnKeyListener((view, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_LEFT) {
                    query.requestFocus(); return true;
                }
                return key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_RIGHT;
            });
            query.setNextFocusUpId(close.getId());
            query.setOnClickListener(view -> {
                query.setShowSoftInputOnFocus(true);
                if (keyboard != null) keyboard.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
            });
            query.setOnKeyListener((view, key, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusResults(); return true;
                }
                return false;
            });
            query.setOnEditorActionListener((view, action, event) -> {
                if (action != EditorInfo.IME_ACTION_SEARCH && action != EditorInfo.IME_ACTION_DONE) return false;
                focusResults(); return true;
            });
            query.addTextChangedListener(this);
            query.setOnFocusChangeListener((view, focused) -> hint.setText(focused
                    ? R.string.search_keyboard_hint : R.string.search_results_hint));
            filter();
        }

        PopupWindow show() {
            popup.showAtLocation(activity.getWindow().getDecorView(), Gravity.TOP | Gravity.LEFT, x, y);
            query.requestFocus();
            inputToken = query.getWindowToken();
            FrostedMenu.captureBackdrop(activity, popup, panel, new Rect(x, y, x + width, y + height), density, 24);
            return popup;
        }

        private void hideKeyboard() {
            if (keyboard != null && inputToken != null) keyboard.hideSoftInputFromWindow(inputToken, 0);
            query.setShowSoftInputOnFocus(false);
        }

        private void focusResults() {
            hideKeyboard();
            if (tiles.getChildCount() > 0) tiles.getChildAt(0).requestFocus();
        }

        private void filter() {
            String needle = query.getText().toString().trim().toLowerCase(Locale.ROOT);
            matches.clear(); tiles.removeAllViews(); scroll.scrollTo(0, 0);
            for (AppAdapter.Entry entry : installed) {
                if (entry.label.toLowerCase(Locale.ROOT).contains(needle)
                        || entry.pkg.toLowerCase(Locale.ROOT).contains(needle)) matches.add(entry);
            }
            count.setText(activity.getResources().getQuantityString(R.plurals.search_app_count, matches.size(), matches.size()));
            empty.setVisibility(matches.isEmpty() ? View.VISIBLE : View.GONE);
            scroll.setVisibility(matches.isEmpty() ? View.GONE : View.VISIBLE);
            for (int index = 0; index < matches.size(); index++) addTile(matches.get(index), index);
        }

        private void addTile(AppAdapter.Entry entry, int index) {
            LinearLayout tile = new LinearLayout(activity);
            tile.setId(View.generateViewId());
            tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(dp(10), dp(10), dp(10), dp(8));
            tile.setFocusable(true); tile.setClickable(true);
            tile.setContentDescription(entry.label);
            tile.setBackground(states(18));
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(entry.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tile.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
            TextView name = text(entry.label, 15, Color.WHITE);
            name.setGravity(Gravity.CENTER); name.setSingleLine(false); name.setMaxLines(2);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, dp(40));
            nameParams.topMargin = dp(6);
            tile.addView(name, nameParams);
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(120), -1);
            tileParams.rightMargin = index == matches.size() - 1 ? 0 : dp(12);
            tiles.addView(tile, tileParams);
            tile.setOnClickListener(view -> { popup.dismiss(); apps.launch(entry); });
            tile.setOnKeyListener((view, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_UP) { query.requestFocus(); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    int next = Math.max(0, Math.min(tiles.getChildCount() - 1,
                            index + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
                    tiles.getChildAt(next).requestFocus(); return true;
                }
                return key == KeyEvent.KEYCODE_DPAD_DOWN;
            });
            tile.setOnFocusChangeListener((view, focused) -> {
                view.animate().cancel();
                view.animate().scaleX(focused ? 1.045f : 1f).scaleY(focused ? 1.045f : 1f)
                        .translationY(focused ? -dp(2) : 0).setDuration(180)
                        .setInterpolator(new DecelerateInterpolator(1.6f)).start();
                if (!focused) return;
                int from = scroll.getScrollX(), to = from;
                if (view.getLeft() - dp(6) < from) to = view.getLeft() - dp(6);
                else if (view.getRight() + dp(6) > from + scroll.getWidth())
                    to = view.getRight() + dp(6) - scroll.getWidth();
                scroll.smoothScrollTo(Math.max(0, to), 0);
            });
        }

        private StateListDrawable states(int radius) {
            StateListDrawable state = new StateListDrawable();
            state.addState(new int[]{android.R.attr.state_pressed}, rounded(0x50FFFFFF, 0xCCFFFFFF, radius));
            state.addState(new int[]{android.R.attr.state_focused}, rounded(0x30FFFFFF, 0xBBFFFFFF, radius));
            state.addState(new int[]{}, rounded(0x0CFFFFFF, 0x26FFFFFF, radius));
            state.setEnterFadeDuration(100); state.setExitFadeDuration(140);
            return state;
        }

        private GradientDrawable rounded(int fill, int edge, int radius) {
            GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{fill, (fill & 0xFFFFFF) | (Math.max(4, Color.alpha(fill) - 8) << 24)});
            shape.setCornerRadius(dp(radius)); shape.setStroke(Math.max(1, dp(1)), edge);
            return shape;
        }

        private TextView text(String value, int size, int color) {
            TextView view = new TextView(activity);
            view.setText(value); view.setTextSize(size); view.setTextColor(color);
            view.setTypeface(activity.getResources().getFont(R.font.outfit));
            if (Build.VERSION.SDK_INT >= 28) view.setFontVariationSettings("'wght' 500");
            view.setGravity(Gravity.CENTER_VERTICAL); view.setIncludeFontPadding(false);
            view.setSingleLine(true); view.setEllipsize(TextUtils.TruncateAt.END);
            return view;
        }

        private int dp(float value) { return Math.round(value * density); }
        @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence text, int start, int before, int count) { filter(); }
        @Override public void afterTextChanged(Editable text) { }
    }
}
