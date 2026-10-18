package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;

import java.io.PrintWriter;

/** Stable direct position tracker, deliberately limited to the horizontally scrolling Play Next row. */
final class PlayNextHighlight implements ViewTreeObserver.OnPreDrawListener {
    private static final long DURATION_MS = 260;
    private final ViewGroup root;
    private final View viewport;
    private final TrackedGlassView glass;
    private final RectF target = new RectF();
    private final RectF shown = new RectF();
    private final RectF origin = new RectF();
    private final RectF clip = new RectF();
    private final Matrix transform = new Matrix();
    private final ValueAnimator motion = ValueAnimator.ofFloat(0f, 1f);
    private View card;
    private float fraction = 1f;
    private boolean attached;
    private boolean visible;
    private Runnable settled;

    PlayNextHighlight(ViewGroup root, View viewport, TrackedGlassView glass) {
        this.root = root;
        this.viewport = viewport;
        this.glass = glass;
        motion.setDuration(DURATION_MS);
        motion.setInterpolator(new DecelerateInterpolator(1.5f));
        motion.addUpdateListener(value -> { fraction = (float) value.getAnimatedValue(); update(); });
        motion.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                Runnable next = settled;
                settled = null;
                if (next != null) root.post(next);
            }
        });
    }

    void resume() {
        if (!attached) { root.getViewTreeObserver().addOnPreDrawListener(this); attached = true; }
    }

    void pause() {
        hide();
        if (attached && root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
        attached = false;
    }

    void focus(View next, RectF incomingBounds, Runnable onSettled) {
        if (next == null || next.getWidth() == 0) return;
        update();
        settled = null;
        motion.cancel();
        card = next;
        bounds(card, target);
        fixedFocusBounds(card, target);
        boolean animate = incomingBounds != null && !incomingBounds.isEmpty();
        if (!animate) animate = visible && !shown.isEmpty();
        RectF source = incomingBounds != null ? incomingBounds : shown;
        origin.set(animate ? source : target);
        fraction = animate ? 0f : 1f;
        visible = true;
        glass.animate().cancel();
        glass.setAlpha(animate ? 1f : 0f);
        if (!animate) glass.animate().alpha(1f).setDuration(180).start();
        update();
        settled = onSettled;
        if (animate) motion.start();
        else if (settled != null) { root.post(settled); settled = null; }
    }

    void hide() {
        settled = null;
        motion.cancel();
        card = null;
        visible = false;
        glass.animate().cancel();
        glass.animate().alpha(0f).setDuration(160).start();
    }

    void hideImmediately() {
        settled = null;
        motion.cancel();
        card = null;
        visible = false;
        glass.animate().cancel();
        glass.setAlpha(0f);
    }

    boolean isVisible() { return visible; }
    boolean isAnimating() { return motion.isRunning(); }

    @Override public boolean onPreDraw() { update(); return true; }

    private void update() {
        if (!visible || card == null) return;
        if (!card.isAttachedToWindow() || card.getWidth() == 0) { hide(); return; }
        bounds(card, target);
        fixedFocusBounds(card, target);
        shown.set(origin.left + (target.left - origin.left) * fraction,
                origin.top + (target.top - origin.top) * fraction,
                origin.right + (target.right - origin.right) * fraction,
                origin.bottom + (target.bottom - origin.bottom) * fraction);
        bounds(viewport, clip);
        clip.left = 0;
        clip.right = root.getWidth();
        float density = root.getResources().getDisplayMetrics().density;
        glass.setHighlight(shown, (16f * AppAdapter.FOCUS_SCALE + 6f) * density, clip);
    }

    private void fixedFocusBounds(View view, RectF rect) {
        float width = rect.width() * AppAdapter.FOCUS_SCALE / view.getScaleX();
        float height = rect.height() * AppAdapter.FOCUS_SCALE / view.getScaleY();
        float cx = rect.centerX();
        float cy = rect.centerY();
        rect.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
    }

    private void bounds(View view, RectF destination) {
        transform.reset();
        mapToRoot(view, transform);
        destination.set(0, 0, view.getWidth(), view.getHeight());
        transform.mapRect(destination);
    }

    private void mapToRoot(View view, Matrix matrix) {
        ViewParent parent = view.getParent();
        if (parent instanceof View && parent != root) {
            View ancestor = (View) parent;
            mapToRoot(ancestor, matrix);
            matrix.preTranslate(-ancestor.getScrollX(), -ancestor.getScrollY());
        }
        matrix.preTranslate(view.getLeft(), view.getTop());
        if (!view.getMatrix().isIdentity()) matrix.preConcat(view.getMatrix());
    }

    void dump(PrintWriter writer) {
        writer.println("  PlayNextTracker visible=" + visible + " animating=" + motion.isRunning());
        writer.println("  PlayNextTracker target=" + target + " shown=" + shown);
    }
}
