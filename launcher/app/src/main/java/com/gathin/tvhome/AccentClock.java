package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.TextView;
import java.util.Calendar;

/** Live red-hour clock, refreshed by system time broadcasts rather than a timer. */
public final class AccentClock extends TextView {
    private boolean listening;
    private final BroadcastReceiver timeChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateTime(); }
    };

    public AccentClock(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) getContext().registerReceiver(timeChanged, filter, Context.RECEIVER_NOT_EXPORTED);
        else getContext().registerReceiver(timeChanged, filter);
        listening = true;
        updateTime();
    }

    private void updateTime() {
        Calendar now = Calendar.getInstance();
        String time = DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm", now).toString();
        SpannableString styled = new SpannableString(time);
        int colon = time.indexOf(':');
        if (colon > 0) {
            styled.setSpan(new ForegroundColorSpan(0xFFC62236), 0, colon, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new CenteredColon(), colon, colon + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new ForegroundColorSpan(0xFF97A4AD), time.length() - 1, time.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        setText(styled);
        setContentDescription(DateFormat.getTimeFormat(getContext()).format(now.getTime()));
    }

    /** Centre the two dots on the digits' visible height, independent of the font's punctuation baseline. */
    private static final class CenteredColon extends ReplacementSpan {
        private final Rect digitBounds = new Rect();

        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
            if (metrics != null) paint.getFontMetricsInt(metrics);
            return Math.round(paint.measureText("0") * .52f);
        }

        @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                                   int top, int baseline, int bottom, Paint paint) {
            paint.getTextBounds("0", 0, 1, digitBounds);
            float centerX = x + getSize(paint, text, start, end, null) / 2f;
            float centerY = baseline + (digitBounds.top + digitBounds.bottom) / 2f;
            float offset = paint.getTextSize() * .14f, radius = paint.getTextSize() * .08f;
            int previous = paint.getColor();
            paint.setColor(0xFFA4AFB6);
            canvas.drawCircle(centerX, centerY - offset, radius, paint);
            canvas.drawCircle(centerX, centerY + offset, radius, paint);
            paint.setColor(previous);
        }
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) updateTime();
    }

    @Override protected void onDetachedFromWindow() {
        if (listening) getContext().unregisterReceiver(timeChanged);
        listening = false;
        super.onDetachedFromWindow();
    }
}
