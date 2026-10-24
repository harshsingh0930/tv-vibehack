package com.gathin.tvhome;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.LinearGradient;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.text.TextPaint;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A pastel greeting with one finite entrance animation; no idle animation or background work. */
public class WelcomeView extends View {
    private final float density;
    private boolean shown;
    private float restingTop;
    private final TextPaint letteringPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final String message;
    private String lettering;
    private float textLeft;
    private float baseline;
    private static final float WORD_FADE_MS = 900f;
    private static final float WORD_DELAY_MS = 220f;
    private final List<Word> words = new ArrayList<>();
    private final ValueAnimator wordEntrance = ValueAnimator.ofFloat(0f, 1f);
    private final DecelerateInterpolator wordEase = new DecelerateInterpolator(1.8f);
    private float wordProgress = 1f;

    private static final class Word {
        final int start, end;
        final float offset;
        Word(int start, int end, float offset) {
            this.start = start; this.end = end; this.offset = offset;
        }
    }

    public WelcomeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        message = getResources().getString(R.string.welcome_home);
        lettering = message;
        letteringPaint.setTypeface(getResources().getFont(R.font.outfit));
        if (android.os.Build.VERSION.SDK_INT >= 28) letteringPaint.setFontVariationSettings("'wght' 300");
        letteringPaint.setTextSize(62f * getResources().getDisplayMetrics().scaledDensity);
        letteringPaint.setLetterSpacing(-.035f);
        letteringPaint.setColor(0xFFFFFFFF);
        setContentDescription(message);
        setWillNotDraw(false);
        // Rasterise only this small text layer for consistent gradients on the tested TV.
        // Translation and alpha remain normal view animations; nothing repaints while idle.
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        wordEntrance.setInterpolator(new LinearInterpolator());
        wordEntrance.addUpdateListener(animation -> {
            wordProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        Paint.FontMetrics font = letteringPaint.getFontMetrics();
        setMeasuredDimension(resolveSize((int) Math.ceil(letteringPaint.measureText(message)), widthSpec),
                resolveSize((int) Math.ceil(font.descent - font.ascent), heightSpec));
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        lettering = TextUtils.ellipsize(message, letteringPaint, width, TextUtils.TruncateAt.END).toString();
        float textWidth = Math.max(1f, letteringPaint.measureText(lettering));
        textLeft = (width - textWidth) / 2f;
        Paint.FontMetrics font = letteringPaint.getFontMetrics();
        baseline = (height - font.ascent - font.descent) / 2f;
        words.clear();
        Matcher word = Pattern.compile("\\S+").matcher(lettering);
        while (word.find()) {
            words.add(new Word(word.start(), word.end(), letteringPaint.measureText(lettering, 0, word.start())));
        }
        letteringPaint.setShader(new LinearGradient(textLeft, 0, textLeft + textWidth, 0,
                new int[]{0xFF9EDBFF, 0xFFC4BCFF, 0xFFF0BDE0, 0xFFFFD4B3},
                null, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        if (wordProgress >= 1f) {
            canvas.drawText(lettering, textLeft, baseline, letteringPaint);
            return;
        }
        float elapsed = wordProgress * (WORD_FADE_MS + Math.max(0, words.size() - 1) * WORD_DELAY_MS);
        for (int i = 0; i < words.size(); i++) {
            Word word = words.get(i);
            float fraction = Math.max(0f, Math.min(1f, (elapsed - i * WORD_DELAY_MS) / WORD_FADE_MS));
            float eased = wordEase.getInterpolation(fraction);
            letteringPaint.setAlpha(Math.round(255f * eased));
            canvas.drawText(lettering, word.start, word.end, textLeft + word.offset,
                    baseline + 8f * density * (1f - eased), letteringPaint);
        }
        letteringPaint.setAlpha(255);
    }

    void dump(java.io.PrintWriter writer) {
        writer.println("  Welcome shown=" + shown + " alpha=" + getAlpha() + " text=" + lettering
                + " width=" + getWidth() + " top=" + getTranslationY()
                + " paintAlpha=" + letteringPaint.getAlpha() + " wordProgress=" + wordProgress
                + " wordAnimating=" + wordEntrance.isRunning());
    }

    void revealAt(float top) {
        if (shown) {
            if (Math.abs(top - restingTop) > .5f) setTranslationY(top);
            restingTop = top;
            return;
        }
        shown = true;
        restingTop = top;
        animate().cancel();
        wordEntrance.cancel();
        wordProgress = ValueAnimator.areAnimatorsEnabled() ? 0f : 1f;
        if (wordProgress == 0f) {
            wordEntrance.setDuration((long) (WORD_FADE_MS + Math.max(0, words.size() - 1) * WORD_DELAY_MS));
            wordEntrance.start();
        } else invalidate();
        setVisibility(View.VISIBLE);
        setAlpha(0f);
        setTranslationY(top + 12f * density);
        animate().alpha(1f).translationY(top).setDuration(1100)
                .setInterpolator(new DecelerateInterpolator(1.8f)).withEndAction(null).start();
    }

    void hide() {
        if (!shown) return;
        shown = false;
        wordEntrance.cancel();
        animate().cancel();
        animate().alpha(0f).setDuration(180).withEndAction(() -> {
            if (!shown) setVisibility(View.INVISIBLE);
        }).start();
    }

    void pause() {
        wordEntrance.cancel();
        animate().cancel();
        // TV Settings is translucent: retain the shelf behind it, with no animation work.
        wordProgress = 1f;
        setAlpha(shown ? 1f : 0f);
        setVisibility(shown ? View.VISIBLE : View.INVISIBLE);
        if (shown) setTranslationY(restingTop);
        invalidate();
    }

    void reset() {
        shown = false;
        wordEntrance.cancel();
        animate().cancel();
        setAlpha(0f);
        setVisibility(View.INVISIBLE);
    }
}
