package com.gathin.tvhome;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads app-published resume links; never invents history or writes to the TV provider. */
final class WatchNextRow {
    interface Navigation {
        void leaveRow(boolean up, int column);
        void focused(View view, int color);
        void ambientChanged(int color);
    }
    private static final Uri URI = Uri.parse("content://android.media.tv/watch_next_program");
    private final Activity activity;
    private final Navigation navigation;
    private final boolean enabled;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(2 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private LinearLayout cards;
    private TextView empty;
    private List<Program> programs = new ArrayList<>();
    private List<Program> pendingPrograms;
    private String pendingStatus;
    private String status = "Shows appear here when your apps share watch progress.";
    private boolean observing, destroyed;
    private int selected;
    private HorizontalScrollView strip;
    private ValueAnimator stripMotion;
    private final Runnable refresh = this::query;
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) {
            main.removeCallbacks(refresh);
            main.postDelayed(refresh, 500);
        }
    };

    WatchNextRow(Activity activity, Navigation navigation, boolean enabled) {
        this.activity = activity;
        this.navigation = navigation;
        this.enabled = enabled;
    }

    boolean isEnabled() { return enabled; }

    void attach(View row) {
        if (!enabled) return;
        LinearLayout next = row.findViewById(R.id.watchCards);
        if (next == cards && cards.getChildCount() > 0) return;
        cards = next;
        empty = row.findViewById(R.id.watchEmpty);
        cards.setClipChildren(false); cards.setClipToPadding(false);
        HorizontalScrollView scroll = row.findViewById(R.id.watchScroll);
        strip = scroll;
        scroll.setClipChildren(false); scroll.setClipToPadding(false);
        render();
    }

    void resume() {
        if (!enabled) return;
        if (!observing) {
            try {
                activity.getContentResolver().registerContentObserver(URI, true, observer);
                observing = true;
            } catch (SecurityException ignored) { }
        }
        query();
    }

    void pause() {
        if (stripMotion != null) stripMotion.cancel();
        main.removeCallbacks(refresh);
        if (observing) activity.getContentResolver().unregisterContentObserver(observer);
        observing = false;
    }

    void destroy() { pause(); destroyed = true; worker.shutdownNow(); images.evictAll(); }

    boolean focus(int column) {
        if (!enabled) return false;
        if (cards == null || !cards.isAttachedToWindow() || cards.getChildCount() == 0) return false;
        selected = Math.max(0, Math.min(column, cards.getChildCount() - 1));
        return cards.getChildAt(selected).findViewById(R.id.card).requestFocus();
    }

    boolean hasPrograms() { return !programs.isEmpty(); }

    private void query() {
        if (destroyed) return;
        worker.execute(() -> {
            List<Program> found = new ArrayList<>();
            String message = "Shows appear here when your apps share watch progress.";
            String[] columns = {"_id", "package_name", "title", "intent_uri", "poster_art_uri",
                    "thumbnail_uri", "last_playback_position_millis", "duration_millis",
                    "last_engagement_time_utc_millis", "browsable"};
            try (Cursor c = activity.getContentResolver().query(URI, columns, null, null, null)) {
                while (c != null && c.moveToNext()) {
                    String pkg = c.getString(1), link = c.getString(3);
                    if (c.getInt(9) == 0 || pkg == null || link == null || link.isEmpty()) continue;
                    try {
                        if (!activity.getPackageManager().getApplicationInfo(pkg, 0).enabled) continue;
                    } catch (Exception missing) { continue; }
                    Program p = new Program();
                    p.id = c.getLong(0); p.pkg = pkg; p.title = c.getString(2); p.link = link;
                    p.art = c.getString(4); if (p.art == null || p.art.isEmpty()) p.art = c.getString(5);
                    p.position = c.getLong(6); p.duration = c.getLong(7); p.time = c.getLong(8);
                    found.add(p);
                }
                found.sort(Comparator.comparingLong((Program p) -> p.time).reversed());
                if (found.size() > 12) found = new ArrayList<>(found.subList(0, 12));
            } catch (SecurityException denied) {
                message = "TV listings access is needed to show shared watch progress.";
                Log.w("WatchNext", "TV listings permission unavailable", denied);
            } catch (Exception failure) {
                message = "Watch progress is temporarily unavailable. Open an app, then return Home.";
                Log.w("WatchNext", "Provider query failed", failure);
            }
            List<Program> result = found;
            String nextStatus = message;
            main.post(() -> {
                if (destroyed) return;
                boolean changed = !signature(programs).equals(signature(result)) || !status.equals(nextStatus);
                if (changed && cards != null && cards.hasFocus()) {
                    pendingPrograms = result; pendingStatus = nextStatus;
                    return;
                }
                pendingPrograms = null; pendingStatus = null;
                programs = result; status = nextStatus;
                Log.i("WatchNext", "Visible shared programs: " + result.size());
                if (changed) render();
            });
        });
    }

    private static String signature(List<Program> values) {
        StringBuilder b = new StringBuilder();
        for (Program p : values) b.append(p.id).append(p.pkg).append(p.title).append(p.link)
                .append(p.art).append(p.position).append('/').append(p.duration).append(';');
        return b.toString();
    }

    private void render() {
        if (cards == null) return;
        boolean restore = cards.hasFocus();
        cards.removeAllViews();
        empty.setText(status);
        empty.setVisibility(programs.isEmpty() ? View.VISIBLE : View.GONE);
        for (int index = 0; index < programs.size(); index++) {
            Program p = programs.get(index);
            final int column = index;
            LinearLayout tile = new LinearLayout(activity);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setClipChildren(false); tile.setClipToPadding(false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(240), dp(192));
            params.rightMargin = dp(26);
            cards.addView(tile, params);
            FrameLayout card = new FrameLayout(activity);
            card.setId(R.id.card);
            card.setFocusable(true); card.setClickable(true);
            card.setRevealOnFocusHint(false);
            card.setContentDescription(p.title);
            card.setBackgroundResource(R.drawable.tile_bg);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
                }
            });
            card.setClipToOutline(true);
            tile.addView(card, new LinearLayout.LayoutParams(-1, dp(135)));
            ImageView art = new ImageView(activity);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(art, new FrameLayout.LayoutParams(-1, -1));
            try { art.setImageDrawable(activity.getPackageManager().getApplicationIcon(p.pkg)); }
            catch (Exception ignored) { }
            TextView title = new TextView(activity);
            title.setText(p.title == null ? "Continue watching" : p.title);
            title.setTextSize(16); title.setTextColor(Color.WHITE); title.setSingleLine(true);
            title.setTypeface(activity.getResources().getFont(R.font.outfit));
            title.setPadding(dp(4), dp(3), dp(4), 0);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(30));
            titleParams.topMargin = dp(8);
            tile.addView(title, titleParams);
            TextView source = new TextView(activity);
            try { source.setText(activity.getPackageManager().getApplicationLabel(
                    activity.getPackageManager().getApplicationInfo(p.pkg, 0))); }
            catch (Exception ignored) { source.setText(""); }
            source.setTextSize(12); source.setTextColor(0xFFA6A6AF); source.setSingleLine(true);
            source.setTypeface(activity.getResources().getFont(R.font.outfit));
            source.setPadding(dp(4), 0, dp(4), 0);
            tile.addView(source, new LinearLayout.LayoutParams(-1, dp(18)));
            if (p.duration > 0 && p.position > 0) {
                ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
                progress.setMax(1000);
                progress.setProgress((int) Math.min(1000, p.position * 1000.0 / p.duration));
                progress.setProgressTintList(ColorStateList.valueOf(0xFFB1DDDE));
                progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x55FFFFFF));
                FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM);
                progressParams.setMargins(dp(12), 0, dp(12), dp(9));
                card.addView(progress, progressParams);
            }
            title.setAlpha(.8f); source.setAlpha(.8f);
            card.setOnFocusChangeListener((v, focused) -> {
                float scale = focused ? AppAdapter.FOCUS_SCALE : 1f;
                v.animate().cancel();
                v.animate().scaleX(scale).scaleY(scale).setDuration(focused ? 260 : 180)
                        .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0f)).start();
                title.animate().alpha(focused ? 1f : .8f).setDuration(180).start();
                source.animate().alpha(focused ? 1f : .8f).setDuration(180).start();
                if (focused) {
                    selected = column;
                    revealCard(column);
                    navigation.focused(card, AppAdapter.vividColor(art.getDrawable()));
                }
                else main.post(this::applyPending);
            });
            card.setOnKeyListener((v, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    focus(column + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1));
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    navigation.leaveRow(key == KeyEvent.KEYCODE_DPAD_UP, column); return true;
                }
                return (key == KeyEvent.KEYCODE_DPAD_LEFT && column == 0)
                        || (key == KeyEvent.KEYCODE_DPAD_RIGHT && column == programs.size() - 1);
            });
            card.setOnClickListener(v -> launch(p));
            loadArt(p.art, art);
        }
        if (restore) {
            if (!focus(selected)) navigation.leaveRow(true, 0);
        }
    }

    private void applyPending() {
        if (destroyed || pendingPrograms == null || cards == null || cards.hasFocus()) return;
        programs = pendingPrograms; status = pendingStatus;
        pendingPrograms = null; pendingStatus = null;
        render();
    }

    private void revealCard(int column) {
        if (strip == null || cards == null || strip.getWidth() == 0) return;
        if (stripMotion != null) stripMotion.cancel();
        View tile = cards.getChildAt(column);
        int margin = dp(18), start = strip.getScrollX(), target = start;
        if (column == 0) target = 0;
        else if (tile.getLeft() - margin < start) target = tile.getLeft() - margin;
        else if (tile.getRight() + margin > start + strip.getWidth()) target = tile.getRight() + margin - strip.getWidth();
        target = Math.max(0, Math.min(target, Math.max(0, cards.getWidth() - strip.getWidth())));
        if (target == start) return;
        stripMotion = ValueAnimator.ofInt(start, target);
        stripMotion.setDuration(260);
        stripMotion.setInterpolator(new DecelerateInterpolator(1.5f));
        stripMotion.addUpdateListener(value -> strip.scrollTo((int) value.getAnimatedValue(), 0));
        stripMotion.start();
    }

    private void launch(Program p) {
        try {
            Intent intent = Intent.parseUri(p.link, Intent.URI_INTENT_SCHEME);
            if (intent.getComponent() != null && !p.pkg.equals(intent.getComponent().getPackageName()))
                throw new SecurityException("Resume link targets another app");
            intent.setSelector(null); intent.setPackage(p.pkg);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception failure) {
            Toast.makeText(activity, "This resume link is unavailable. Open the app instead.", Toast.LENGTH_LONG).show();
        }
    }

    private void loadArt(String source, ImageView view) {
        if (source == null || source.isEmpty()) return;
        view.setTag(source);
        Bitmap cached = images.get(source);
        if (cached != null) { view.setImageBitmap(cached); return; }
        worker.execute(() -> {
            try {
                Uri uri = Uri.parse(source);
                String scheme = uri.getScheme();
                InputStream stream;
                if ("https".equals(scheme) || "http".equals(scheme)) {
                    // Older providers publish HTTP art links. Try the secure equivalent.
                    String secure = "http".equals(scheme) ? "https" + source.substring(4) : source;
                    URLConnection connection = new URL(secure).openConnection();
                    connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
                    stream = connection.getInputStream();
                } else if ("content".equals(scheme) || "android.resource".equals(scheme)) {
                    stream = activity.getContentResolver().openInputStream(uri);
                } else return;
                byte[] bytes;
                try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (input == null) return;
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = input.read(buffer)) != -1) {
                        if (output.size() + n > 3 * 1024 * 1024) return;
                        output.write(buffer, 0, n);
                    }
                    bytes = output.toByteArray();
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                options.inSampleSize = 1;
                while (options.outWidth / options.inSampleSize > 400 || options.outHeight / options.inSampleSize > 240)
                    options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (bitmap == null) return;
                images.put(source, bitmap);
                main.post(() -> {
                    if (!destroyed && source.equals(view.getTag())) {
                        view.setImageBitmap(bitmap);
                        if (view.getParent() instanceof View && ((View) view.getParent()).hasFocus())
                            navigation.ambientChanged(AppAdapter.vividColor(view.getDrawable()));
                    }
                });
            } catch (Exception ignored) { /* App icon remains available without artwork/network. */ }
        });
    }

    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private static final class Program {
        long id, position, duration, time;
        String pkg, title, link, art;
    }
}
