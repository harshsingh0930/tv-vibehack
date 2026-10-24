package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

/** Prepared photographs plus pooled native particles. Glass samples only drawBackdrop(). */
final class OccasionView extends View {
    private final Paint photoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG), glintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(), base = new Paint();
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF destination = new RectF();
    private final Path petal = new Path(), star = new Path(), ribbon = new Path(), vein = new Path();
    private final Path[] blooms = {new Path(), new Path(), new Path()};
    private final int[] bloomCounts = {6,8,10};
    private final float[] positions = new float[96], phases = new float[96], sizes = new float[96];
    private final int[] palette = {0xFFFFF3DB, Color.WHITE, 0xFFD7EDFF, 0xFFFFD5DF};
    private OccasionProtocol.Scene scene;
    private Bitmap current, previous;
    private long entranceAt, photoAt;
    private float fade = 1f;
    private int accent = Color.WHITE;
    private String effect = "none";
    private float homeContentBottom;
    private final OccasionEffects festive = new OccasionEffects();
    private boolean pending, running, dream, messageVisible = true;
    private Runnable backdropChanged = () -> {};
    private StaticLayout message;
    private final Runnable frame = () -> { pending = false; advance(); };

    OccasionView(Context context, boolean isDream) {
        super(context); dream = isDream;
        setFocusable(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        text.setTypeface(getResources().getFont(R.font.outfit)); text.setFontVariationSettings("'wght' 300"); text.setColor(Color.WHITE);
        petal.moveTo(-1, 0); petal.cubicTo(-.3f, -1, .7f, -.8f, 1, 0); petal.cubicTo(.3f, 1, -.7f, .8f, -1, 0); petal.close();
        star.moveTo(0, -1); star.quadTo(0, 0, 1, 0); star.quadTo(0, 0, 0, 1); star.quadTo(0, 0, -1, 0); star.quadTo(0, 0, 0, -1); star.close();
        ribbon.moveTo(-1.5f,-.3f); ribbon.cubicTo(-.5f,-1,.5f,1,1.5f,.3f);
        vein.moveTo(-.65f,0); vein.quadTo(0,-.15f,.65f,0);
        Path bloomPetal = new Path(); bloomPetal.moveTo(0,-.04f);
        bloomPetal.cubicTo(-.62f,-.35f,-.55f,-1.24f,0,-1.05f);
        bloomPetal.cubicTo(.55f,-1.24f,.62f,-.35f,0,-.04f); bloomPetal.close();
        Matrix rotation = new Matrix();
        for(int variant=0;variant<blooms.length;variant++) for(int i=0;i<bloomCounts[variant];i++) {
            rotation.setRotate(i*360f/bloomCounts[variant]); blooms[variant].addPath(bloomPetal,rotation);
        }
        glintPaint.setStyle(Paint.Style.STROKE); glintPaint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < positions.length; i++) { positions[i] = (i * .6180339887f) % 1f; phases[i] = (i * .173205f) % 1f; sizes[i] = 3.5f + positions[i] * 8; }
    }
    void onBackdropChanged(Runnable callback) { backdropChanged = callback; }
    void homeContentBottom(float bottom) { homeContentBottom = bottom; invalidate(); }
    boolean active() { return scene != null; }
    void messageVisible(boolean visible) { if (messageVisible != visible) { messageVisible = visible; invalidate(); } }
    Bitmap picture() { return current; }
    void show(OccasionProtocol.Scene next, Bitmap picture, String nextEffect) {
        boolean changed = scene == null || !scene.identity().equals(next.identity()) || !effect.equals(nextEffect);
        effect = nextEffect;
        if (changed) { entranceAt = SystemClock.uptimeMillis(); scene = next; accent = Color.parseColor(next.color); configure(); }
        previous = current; current = picture; photoAt = SystemClock.uptimeMillis();
        fade = previous != null && ValueAnimator.areAnimatorsEnabled() ? 0f : 1f;
        if (fade == 1f) previous = null;
        running = true; backdropChanged.run(); invalidate(); schedule();
    }
    void clear() {
        running = false; scene = null; current = previous = null; message = null;
        removeCallbacks(frame); pending = false; backdropChanged.run(); invalidate();
    }
    private void configure() {
        if (scene == null || getWidth() == 0) return;
        base.setShader(new RadialGradient(getWidth() * .3f, getHeight() * .35f, getWidth() * .9f,
                new int[]{accent, 0xFF12191D}, null, Shader.TileMode.CLAMP));
        shade.setShader(new LinearGradient(0, 0, 0, getHeight(), new int[]{0x60000000, 0x24000000, 0xA8000000}, null, Shader.TileMode.CLAMP));
        float density = getResources().getDisplayMetrics().density;
        palette[1] = accent;
        haloPaint.setShader(new RadialGradient(0,0,1,
                new int[]{0xAAFFF5DE, (accent & 0x00FFFFFF) | 0x65000000, accent & 0x00FFFFFF},
                new float[]{0,.2f,1}, Shader.TileMode.CLAMP));
        text.setTextSize((dream ? 38 : 44) * density);
        int width = Math.max(1, (int) (getWidth() * .8f));
        message = StaticLayout.Builder.obtain(scene.message, 0, scene.message.length(), text, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setMaxLines(3)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).build();
    }
    void drawBackdrop(Canvas canvas, int width, int height) {
        if (scene == null) return;
        canvas.drawRect(0, 0, width, height, base);
        if (previous != null) { photoPaint.setAlpha(255); drawPhoto(canvas, previous, width, height); }
        if (current != null) { photoPaint.setAlpha(Math.round(255 * fade)); drawPhoto(canvas, current, width, height); }
        canvas.drawRect(0, 0, width, height, shade);
    }
    private void drawPhoto(Canvas canvas, Bitmap bitmap, int width, int height) {
        float scale = Math.max(width / (float) bitmap.getWidth(), height / (float) bitmap.getHeight());
        float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
        destination.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f);
        canvas.drawBitmap(bitmap, null, destination, photoPaint);
    }
    @Override protected void onDraw(Canvas canvas) {
        if (scene == null) return;
        if (dream) drawBackdrop(canvas, getWidth(), getHeight());
        float t = ValueAnimator.areAnimatorsEnabled() ? (SystemClock.uptimeMillis() - entranceAt) / 1000f : 10f;
        float safeBottom = dream ? getHeight()*.98f : homeContentBottom > 0 ? homeContentBottom - 10*getWidth()/960f : getHeight()*.62f;
        if (!effect.equals("none") && !festive.draw(canvas, effect, t, getWidth(), getHeight(), ValueAnimator.areAnimatorsEnabled(), safeBottom)) {
            boolean animated = ValueAnimator.areAnimatorsEnabled();
            boolean flowers = effect.equals("flowers"), petals = effect.equals("petals"), confetti = effect.equals("confetti");
            int steady = flowers ? 36 : petals ? 52 : 60;
            int maximum = flowers ? 52 : petals ? 80 : 96;
            int count = animated ? (t < 10 ? maximum : steady) : 12;
            float density = getResources().getDisplayMetrics().density;
            for (int i = 0; i < count; i++) {
                float depth = .35f + (i % 3) * .325f;
                float arrival = i < steady ? 1 : Math.max(0, Math.min(1, (10-t)/4));
                float x = ((positions[i] + (float)Math.sin(t * .25 + i) * (.025f + depth * .035f)
                        + (float)Math.sin(t * .11) * .025f + 2f) % 1f) * getWidth();
                float speed = flowers ? .008f + depth*.018f : .012f + depth * .021f;
                float y = ((phases[i] + t * speed + 100f) % 1f) * getHeight();
                float size = (flowers ? 13 + positions[i] * 16 : sizes[i]) * density * depth;
                float twinkle = .5f + .5f * (float)Math.sin(t * (1.3f + positions[i]) + i * 1.7f);
                int alpha = (int)(255 * arrival * (.38f + depth * .42f) * (!flowers && !petals && !confetti ? .25f + twinkle * .75f : 1));
                if (alpha <= 0) continue;
                int color = palette[i % palette.length];
                particlePaint.setColor(color); particlePaint.setAlpha(alpha); particlePaint.setStyle(Paint.Style.FILL);
                glintPaint.setColor(color); glintPaint.setAlpha(alpha); glintPaint.setStrokeWidth(density);
                canvas.save(); canvas.translate(x, y);
                if (!flowers && !petals && !confetti && i % 4 == 0) {
                    canvas.save(); float radius = size * 4.4f; canvas.scale(radius,radius);
                    haloPaint.setAlpha((int)(alpha*.35f)); canvas.drawCircle(0,0,1,haloPaint); canvas.restore();
                }
                canvas.rotate((float)Math.toDegrees(t * (flowers ? .12f+depth*.1f : .3f + depth * .25f) + i));
                if (flowers) {
                    int variant=i%blooms.length;
                    canvas.scale(size,size*(.85f+.15f*(float)Math.sin(t*.65+i)));
                    canvas.drawPath(blooms[variant],particlePaint);
                    canvas.save(); canvas.rotate(180f/bloomCounts[variant]); canvas.scale(.62f,.62f);
                    particlePaint.setColor(0xFFFFF3E0); particlePaint.setAlpha((int)(alpha*.65f)); canvas.drawPath(blooms[variant],particlePaint); canvas.restore();
                    particlePaint.setColor(0xFFE9B85D); particlePaint.setAlpha(alpha); canvas.drawCircle(0,0,.23f,particlePaint);
                    particlePaint.setColor(0xFFFFF6D7); particlePaint.setAlpha((int)(alpha*.8f)); canvas.drawCircle(-.06f,-.06f,.08f,particlePaint);
                } else if (confetti) {
                    canvas.scale(.22f + .78f * Math.abs((float)Math.cos(t*1.5+i)),1);
                    if (i%4==0) { canvas.scale(size,size); glintPaint.setStrokeWidth(.3f); canvas.drawPath(ribbon,glintPaint); }
                    else {
                        canvas.drawRoundRect(-size,-size*.3f,size,size*.3f,density*.5f,density*.5f,particlePaint);
                        particlePaint.setColor(0xFFFFF5DF); particlePaint.setAlpha((int)(alpha*.45f)); canvas.drawRect(-size,-size*.3f,size,-size*.18f,particlePaint);
                    }
                } else if (petals) {
                    canvas.scale(size * (.35f + .65f * Math.abs((float)Math.cos(t*.8+i))), size*.85f); canvas.drawPath(petal,particlePaint);
                    glintPaint.setColor(0xFFFFF5E4); glintPaint.setAlpha((int)(alpha*.5f)); glintPaint.setStrokeWidth(.07f); canvas.drawPath(vein,glintPaint);
                } else {
                    canvas.scale(size,size); canvas.drawPath(star,particlePaint);
                    particlePaint.setColor(0xFFFFF9ED); particlePaint.setAlpha((int)(alpha*.85f)); canvas.drawCircle(0,0,.13f,particlePaint);
                    if (i%7==0) { glintPaint.setColor(0xFFFFF5DE); glintPaint.setAlpha((int)(alpha*.5f)); glintPaint.setStrokeWidth(.06f); canvas.drawLine(-1.8f,0,1.8f,0,glintPaint); canvas.drawLine(0,-1.8f,0,1.8f,glintPaint); }
                }
                canvas.restore();
            }
        }
        if (messageVisible && message != null) {
            float enter = ValueAnimator.areAnimatorsEnabled() ? Math.min(1, t / 1.4f) : 1;
            text.setAlpha((int) (enter * 255)); canvas.save();
            canvas.translate((getWidth() - message.getWidth()) / 2f, getHeight() * (dream ? .3f : .29f) + (1 - enter) * 12);
            message.draw(canvas); canvas.restore(); text.setAlpha(255);
        }
    }
    private void advance() {
        if (!running || scene == null || !isAttachedToWindow() || getWindowVisibility() != VISIBLE) return;
        if (fade < 1f) {
            fade = Math.min(1f, (SystemClock.uptimeMillis() - photoAt) / 1200f);
            if (fade == 1f) previous = null;
            backdropChanged.run();
        }
        invalidate(); schedule();
    }
    private void schedule() {
        if (pending || !running || scene == null || !ValueAnimator.areAnimatorsEnabled()) return;
        if (effect.equals("none") && fade >= 1 && SystemClock.uptimeMillis() - entranceAt >= 1500) return;
        pending = true; postDelayed(frame, 34);
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { configure(); backdropChanged.run(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE) schedule(); else { removeCallbacks(frame); pending = false; } }
    @Override protected void onDetachedFromWindow() { removeCallbacks(frame); pending = false; super.onDetachedFromWindow(); }
}
