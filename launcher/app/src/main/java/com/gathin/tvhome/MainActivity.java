package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private TrackedGlassView playNextGlass;
    private AmbientView ambient;
    private TextView heroName;
    private WelcomeView welcome;
    private HeaderGlass headerGlass;
    private float welcomeTop;
    private boolean welcomeFits;
    private boolean heroShown;
    private TextView greeting;
    private TextView btnWifi;
    private FocusHighlight highlight;
    private PlayNextHighlight playNextHighlight;
    private View pendingOriginalCard;
    private int focusedSection = -1;
    private RecyclerView.OnScrollListener pendingScrollFocus;
    private int focusRequest;
    private int lastFocused = 0;
    private int requestedTile;
    private int restartGlassAt = -1;
    private boolean lastFocusRestarted;
    private float density;
    private WatchNextRow watchNext;
    private boolean settingsLongPress;
    private boolean upHeld, upStartedBelowDock;
    private boolean homeResumed;
    private int cleanupPass;
    private final Runnable revealWatchNext = () -> {
        if (focusedSection != 1) return;
        RecyclerView.ViewHolder section = grid.findViewHolderForAdapterPosition(COLUMNS);
        if (section == null) return;
        int top = section.itemView.getTop(), bottom = section.itemView.getBottom();
        int delta = Math.max(0, bottom - grid.getHeight() + (int) (16 * density));
        if (top - delta < 0) delta = top;
        if (delta != 0) {
            grid.stopScroll();
            grid.smoothScrollBy(0, delta, new android.view.animation.DecelerateInterpolator(1.5f), 260);
        }
    };
    private final Runnable cleanup = this::runCleanup;

    private void runCleanup() {
        if (!homeResumed || !hasWindowFocus() || !getSharedPreferences("home", 0).getBoolean("cleanup", true)) return;
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager == null || android.os.Build.VERSION.SDK_INT >= 34) return;
        java.util.Set<String> requested = new java.util.HashSet<>();
        for (String pkg : adapter.cleanupCandidates()) {
            try { manager.killBackgroundProcesses(pkg); requested.add(pkg); }
            catch (SecurityException ignored) { }
        }
        getSharedPreferences("home", 0).edit().putLong("last_cleanup_ms", System.currentTimeMillis())
                .putStringSet("last_cleanup_packages", requested).apply();
        android.util.Log.i("HomeCleanup", "Cached-process cleanup requested for " + requested.size() + " apps");
        // A second finite pass lets the previous app finish stopping and publishing progress.
        if (cleanupPass++ == 0) handler.postDelayed(cleanup, 10000);
    }

    private void scheduleCleanup() {
        handler.removeCallbacks(cleanup);
        cleanupPass = 0;
        if (homeResumed && hasWindowFocus()) handler.postDelayed(cleanup, 3000);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        playNextGlass = findViewById(R.id.playNextGlass);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        welcome = findViewById(R.id.welcome);
        greeting = findViewById(R.id.greeting);
        btnWifi = findViewById(R.id.btnWifi);

        grid = findViewById(R.id.grid);
        GridLayoutManager layout = new GridLayoutManager(this, COLUMNS) {
            @Override public boolean onRequestChildFocus(RecyclerView parent, RecyclerView.State state,
                                                        View child, View focused) {
                // The large top padding parks the dock low. Play Next scrolls against the
                // visible screen instead of that smaller padded viewport.
                return parent.getChildAdapterPosition(child) == COLUMNS
                        || super.onRequestChildFocus(parent, state, child, focused);
            }
        };
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return position == COLUMNS || (adapter != null && position == adapter.getItemCount() - 1) ? COLUMNS : 1;
            }
        });
        grid.setLayoutManager(layout);
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(COLUMNS, density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        watchNext = new WatchNextRow(this, new WatchNextRow.Navigation() {
            @Override public void leaveRow(boolean up, int column) {
                grid.removeCallbacks(revealWatchNext);
                focusTile(up ? Math.min(column, COLUMNS - 1)
                        : Math.min(COLUMNS + column % COLUMNS, adapter.appCount() - 1));
            }
            @Override public void focused(View view, int color) {
                if (pendingScrollFocus != null) {
                    grid.removeOnScrollListener(pendingScrollFocus); pendingScrollFocus = null;
                    grid.stopScroll(); focusedSection = -1;
                }
                showPlayNextGlass(view); hideHero();
                setAmbient(color);
                if (focusedSection != 1) {
                    focusedSection = 1;
                    grid.removeCallbacks(revealWatchNext);
                    revealWatchNext.run();
                }
            }
            @Override public void ambientChanged(int color) {
                if (homeResumed && focusedSection == 1) setAmbient(color);
            }
        });
        adapter = new AppAdapter(this, this, COLUMNS, watchNext);
        grid.setAdapter(adapter);

        // tvOS layout: push the dock row to the bottom of the screen; the grid hides below until you press Down.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int dockRow = (int) ((145 + 6 + 12) * density);   // item height plus DockDecoration offsets
            int want = Math.max(0, h - dockRow - (int) (30 * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
        });

        highlight = new FocusHighlight(root, glass);
        playNextHighlight = new PlayNextHighlight(root, grid, playNextGlass);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                highlight.scrolled(dy);
            }
            @Override public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    finishOriginalHandoff();
                    if (homeResumed && heroShown && welcomeFits) welcome.revealAt(welcomeTop);
                }
            }
        });

        View btnInputs = findViewById(R.id.btnInputs);
        View btnSettings = findViewById(R.id.btnSettings);
        headerGlass = new HeaderGlass(root, ambient, findViewById(R.id.topButtons),
                (LiquidGlassButton) btnInputs, (LiquidGlassButton) btnWifi, (LiquidGlassButton) btnSettings);
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnSettings.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
        btnSettings.setOnLongClickListener(v -> { showLauncherOptions(); return true; });

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (v.getId() == R.id.btnSettings && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) settingsLongPress = false;
                if (event.getAction() == KeyEvent.ACTION_UP && settingsLongPress) {
                    settingsLongPress = false; v.setPressed(false); return true;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.isLongPress()) {
                    settingsLongPress = true; v.cancelLongPress(); showLauncherOptions(); return true;
                }
            }
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusTile(Math.min(lastFocused, COLUMNS - 1));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnInputs) return true;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) { focusedSection = -1; grid.removeCallbacks(revealWatchNext); hideGlass(); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        homeResumed = true;
        headerGlass.resume();
        highlight.resume();
        highlight.hide();
        playNextHighlight.resume();
        playNextHighlight.hide();
        focusedSection = -1;
        heroShown = false;
        heroName.setAlpha(0f);
        welcome.reset();
        ambient.setAlpha(0f);
        adapter.reload();
        grid.scheduleLayoutAnimation();
        watchNext.resume();
        scheduleCleanup();
        updateGreeting();
        updateWifiLabel();
        handler.postDelayed(minuteTick, MINUTE_MS);

        // Return to the tile we left from, or the first one.
        grid.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                grid.getViewTreeObserver().removeOnPreDrawListener(this);
                View focused = getCurrentFocus();
                RecyclerView.ViewHolder holder = focused != null
                        ? grid.findContainingViewHolder(focused) : null;
                if (holder != null && holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                    // The tile can keep focus while paused, so no focus-change event will redraw
                    // the highlight that onResume reset. Restore it after the tiles are laid out.
                    int pos = holder.getBindingAdapterPosition();
                    if (adapter.getItemViewType(pos) == 2) {
                        holder.itemView.findViewById(R.id.appLibrary).requestFocus();
                        onLibraryFocused();
                    } else if (pos == COLUMNS) {
                        if (!watchNext.focus(0)) focusTile(lastFocused);
                    } else onTileFocused(adapter.toAppPosition(pos), holder.itemView.findViewById(R.id.card));
                } else {
                    focusTile(lastFocused);
                }
                return true;
            }
        });
    }

    @Override
    protected void onPause() {
        homeResumed = false;
        upHeld = upStartedBelowDock = false;
        headerGlass.pause();
        super.onPause();
        handler.removeCallbacks(minuteTick);
        handler.removeCallbacks(cleanup);
        grid.removeCallbacks(revealWatchNext);
        pendingOriginalCard = null;
        highlight.pause();
        // Also finish a greeting waiting for the dock's return scroll if Settings opens quickly.
        if (heroShown && welcomeFits) welcome.revealAt(welcomeTop);
        welcome.pause();
        playNextHighlight.pause();
        if (pendingScrollFocus != null) { grid.removeOnScrollListener(pendingScrollFocus); pendingScrollFocus = null; }
        focusRequest++;
        watchNext.pause();
        adapter.cancelMove();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) scheduleCleanup();
        else {
            handler.removeCallbacks(cleanup);
            upHeld = upStartedBelowDock = false;
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                upHeld = upStartedBelowDock = false;
            } else if (event.getAction() == KeyEvent.ACTION_DOWN && !upHeld) {
                upHeld = true;
                // Keep the physical press's origin as focus moves through Play Next and the grid.
                upStartedBelowDock = grid != null && grid.hasFocus() && !upOriginIsDock();
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean upOriginIsDock() {
        // A new press during scrolling should use the requested destination, even before layout catches up.
        if (pendingScrollFocus != null) return requestedTile >= 0 && requestedTile < COLUMNS;
        View focused = getCurrentFocus();
        RecyclerView.ViewHolder holder = focused == null ? null : grid.findContainingViewHolder(focused);
        if (holder == null) return false;
        int position = holder.getBindingAdapterPosition();
        return position >= 0 && position < COLUMNS;
    }

    @Override
    public void onBackPressed() {
        adapter.cancelMove();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        watchNext.destroy();
        super.onDestroy();
    }

    private void showLauncherOptions() {
        boolean enabled = getSharedPreferences("home", 0).getBoolean("cleanup", true);
        new AlertDialog.Builder(this).setTitle("Launcher options")
                .setMultiChoiceItems(new String[]{"Clean cached apps when returning Home"}, new boolean[]{enabled},
                        (dialog, which, checked) -> {
                            getSharedPreferences("home", 0).edit().putBoolean("cleanup", checked).apply();
                            if (checked) handler.postDelayed(cleanup, 3000);
                        })
                .setNeutralButton("How it works", (dialog, which) -> new AlertDialog.Builder(this)
                        .setMessage("Android decides which cached processes can close. Music, VPN and casting are protected. Active services may remain.")
                        .setPositiveButton("OK", null).show())
                .setPositiveButton("Done", null).show();
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocusRestarted = position == restartGlassAt;
        restartGlassAt = -1;
        if (lastFocusRestarted) {
            // A horizontal row wrap ends the old highlight in place. The new
            // tile gets the normal entrance instead of a spring across the grid.
            pendingOriginalCard = null;
            playNextHighlight.hideImmediately();
            highlight.hideImmediately();
        }
        lastFocused = position;
        boolean enteringDock = focusedSection != 0;
        focusedSection = position < COLUMNS ? 0 : 2;
        grid.removeCallbacks(revealWatchNext);
        showTileGlass(card);
        setAmbient(adapter.ambientColorAt(position));
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            if (enteringDock && Math.abs(dy) > 1) {
                grid.stopScroll();
                grid.smoothScrollBy(0, dy, new android.view.animation.DecelerateInterpolator(1.5f), 260);
            }
            Rect viewport = new Rect(0, 0, grid.getWidth(), grid.getHeight());
            root.offsetDescendantRectToMyCoords(grid, viewport);
            // Position the shelf against the dock's settled location, even while returning
            // from the taller Play Next section and the scroll is still in flight.
            showHero(position, viewport.top + grid.getPaddingTop() + 6 * density + card.getTop());
        } else {
            hideHero();
            int pad = (int) (6 * density);
            Rect want = new Rect(0, -pad * 2, item.getWidth(), item.getHeight() + pad);
            grid.requestChildRectangleOnScreen(item, want, false);
        }
    }

    private void showTileGlass(View card) {
        // The vertical boundary is a single bounded move. Within ordinary app rows the
        // original independent X/Y springs retain their original cadence and damping.
        if (playNextHighlight.isVisible()) {
            pendingOriginalCard = card;
            playNextHighlight.focus(card, null, this::finishOriginalHandoff);
        } else {
            pendingOriginalCard = null;
            highlight.focus(card);
        }
    }

    private void showPlayNextGlass(View card) {
        pendingOriginalCard = null;
        RectF handoff = new RectF();
        boolean hasHandoff = highlight.copyFillBounds(handoff);
        highlight.hideImmediately();
        playNextHighlight.focus(card, hasHandoff ? handoff : null, null);
    }

    private void finishOriginalHandoff() {
        View card = pendingOriginalCard;
        if (card == null || !card.hasFocus() || playNextHighlight.isAnimating()
                || grid.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) return;
        pendingOriginalCard = null;
        playNextHighlight.hideImmediately();
        highlight.focus(card);
        highlight.revealImmediately();
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        String label = adapter.labelAt(position);
        float y = dockTopInRoot - 14 * density - 26 * density - heroName.getHeight();
        Rect viewport = new Rect(0, 0, grid.getWidth(), grid.getHeight());
        root.offsetDescendantRectToMyCoords(grid, viewport);
        float spaceTop = viewport.top + 12f * density;
        float spaceBottom = y - 18f * density;
        welcomeFits = spaceBottom - spaceTop >= welcome.getHeight();
        if (welcomeFits) {
            welcomeTop = spaceTop + (spaceBottom - spaceTop - welcome.getHeight()) / 2f;
            if (grid.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) welcome.revealAt(welcomeTop);
        } else welcome.hide();
        boolean changed = !label.contentEquals(heroName.getText());

        if (!heroShown) {
            heroName.setText(label);
            heroName.setY(y);
            heroName.setTranslationX(0f);
            heroName.animate().alpha(1f).setDuration(260).start();
            heroShown = true;
        } else if (changed) {
            heroName.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                heroName.setText(label);
                heroName.setY(y);
                heroName.setTranslationX(12 * density);
                heroName.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
        } else {
            heroName.setY(y);
        }
    }

    private void setAmbient(int color) {
        ambient.showColor(color);
    }

    private void hideHero() {
        welcome.hide();
        if (!heroShown) return;
        heroShown = false;
        heroName.animate().alpha(0f).setDuration(160).start();
    }

    private void hideGlass() {
        pendingOriginalCard = null;
        highlight.hide();
        playNextHighlight.hide();
    }

    @Override public void dump(String prefix, java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        highlight.dump(writer);
        playNextHighlight.dump(writer);
        welcome.dump(writer);
        headerGlass.dump(writer);
        writer.println("  AppFocus position=" + lastFocused + " rowWrap=" + lastFocusRestarted
                + " label=" + adapter.labelAt(lastFocused));
        writer.println("  DockUp held=" + upHeld + " startedBelow=" + upStartedBelowDock);
        View panel = grid.findViewById(R.id.watchPanel);
        if (panel instanceof FrostedPanel) ((FrostedPanel) panel).dump(writer);
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileDirectionKey(int position, int keyCode) {
        int count = adapter.appCount();
        if (count == 0) return true;
        int sourcePosition = position;
        if (pendingScrollFocus != null && requestedTile >= 0) position = requestedTile;
        boolean horizontal = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        int target;
        if (horizontal) {
            // The footer follows the final app, including a partially filled last row.
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && position == count - 1) {
                focusLibrary();
                return true;
            }
            // Explicit reading order avoids FocusFinder jumping between the full-width
            // Play Next section, attached grid rows and header at a horizontal edge.
            int first = position < COLUMNS ? 0 : COLUMNS;
            int last = position < COLUMNS ? Math.min(COLUMNS, count) - 1 : count - 1;
            target = Math.max(first, Math.min(last,
                    position + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
            if (target == position) return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) {
                // A held climb stops at the dock. Release and press Up again to reach the header.
                if (upStartedBelowDock) return true;
                findViewById(R.id.btnInputs).requestFocus();
                return true;
            }
            if (position < COLUMNS * 2 && focusWatchNext(position - COLUMNS)) return true;
            target = position - COLUMNS;
        } else {
            if (position < COLUMNS && focusWatchNext(position)) return true;
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) { focusLibrary(); return true; }
            target = Math.min(position + COLUMNS, count - 1);
        }
        boolean rowWrap = horizontal && sourcePosition >= COLUMNS && target >= COLUMNS
                && sourcePosition / COLUMNS != target / COLUMNS;
        focusTile(target, rowWrap);
        return true;
    }

    private void focusTile(int position) {
        focusTile(position, false);
    }

    private void focusTile(int position, boolean restartHighlight) {
        final int request = ++focusRequest;
        if (pendingScrollFocus != null) { grid.removeOnScrollListener(pendingScrollFocus); pendingScrollFocus = null; }
        if (adapter.appCount() == 0) return;
        final int appPos = Math.max(0, Math.min(position, adapter.appCount() - 1));
        requestedTile = appPos;
        restartGlassAt = restartHighlight ? appPos : -1;
        final int pos = adapter.toAdapterPosition(appPos);
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            if (card.isFocused()) onTileFocused(appPos, card);
            else if (card.isInTouchMode()) card.requestFocusFromTouch();
            else card.requestFocus();
            return;
        }
        // Only the latest pending focus request can complete.
        pendingScrollFocus = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                pendingScrollFocus = null;
                if (request != focusRequest) return;
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : null;
                if (target != null) {
                    if (target.isInTouchMode()) target.requestFocusFromTouch();
                    else target.requestFocus();
                } else {
                    // A layout may still be pending. Wait for that exact tile instead
                    // of assigning focus to an unrelated child or the Play Next row.
                    grid.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                        @Override public boolean onPreDraw() {
                            grid.getViewTreeObserver().removeOnPreDrawListener(this);
                            if (request == focusRequest) {
                                RecyclerView.ViewHolder exact = grid.findViewHolderForAdapterPosition(pos);
                                if (exact != null) exact.itemView.findViewById(R.id.card).requestFocus();
                            }
                            return true;
                        }
                    });
                    grid.scrollToPosition(pos);
                }
            }
        };
        grid.addOnScrollListener(pendingScrollFocus);
        grid.smoothScrollToPosition(pos);
    }

    private boolean focusWatchNext(int column) {
        if (!watchNext.hasPrograms()) return false;
        restartGlassAt = -1;
        final int request = ++focusRequest;
        if (watchNext.focus(column)) return true;
        grid.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                grid.getViewTreeObserver().removeOnPreDrawListener(this);
                if (request != focusRequest) return true;
                if (!watchNext.focus(column)) focusTile(COLUMNS + column % COLUMNS);
                return true;
            }
        });
        grid.scrollToPosition(COLUMNS);
        return true;
    }

    @Override public void onOrderMoved(int position) {
        final int request = ++focusRequest;
        grid.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                grid.getViewTreeObserver().removeOnPreDrawListener(this);
                if (request == focusRequest) focusTile(position);
                return true;
            }
        });
    }

    @Override public void onLibraryFocused() {
        focusedSection = 3;
        grid.removeCallbacks(revealWatchNext);
        hideGlass(); hideHero();
    }

    @Override public void onLibraryUp() {
        int count = adapter.appCount();
        if (count == 0) { findViewById(R.id.btnInputs).requestFocus(); return; }
        int lastRow = ((count - 1) / COLUMNS) * COLUMNS;
        // Focus immediately: unlike moving an app, pressing Up does not invalidate the
        // RecyclerView, so a pre-draw callback can otherwise wait indefinitely.
        focusTile(Math.min(lastRow + lastFocused % COLUMNS, count - 1));
    }

    private void focusLibrary() {
        focusRequest++;
        requestedTile = -1;
        restartGlassAt = -1;
        if (pendingScrollFocus != null) { grid.removeOnScrollListener(pendingScrollFocus); pendingScrollFocus = null; }
        int position = adapter.getItemCount() - 1;
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
        if (holder != null) { holder.itemView.findViewById(R.id.appLibrary).requestFocus(); return; }
        pendingScrollFocus = new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this); pendingScrollFocus = null;
                RecyclerView.ViewHolder footer = rv.findViewHolderForAdapterPosition(position);
                if (footer != null) footer.itemView.findViewById(R.id.appLibrary).requestFocus();
            }
        };
        grid.addOnScrollListener(pendingScrollFocus);
        grid.smoothScrollToPosition(position);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        btnWifi.setText(text);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTv));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}
