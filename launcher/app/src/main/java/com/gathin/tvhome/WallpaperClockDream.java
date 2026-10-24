package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.widget.TextView;
import org.json.JSONObject;
import android.graphics.Bitmap;

import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Online curated photography, native clock/date and cached live weather. */
public final class WallpaperClockDream extends DreamService {
    private static final long PHOTO_INTERVAL_MS = 60_000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private DreamPhotoFeed photoFeed;
    private final Runnable rotate = this::nextPicture;
    private DreamOnlineSource source;
    private DreamOnlineSource weatherSource;
    private OccasionController occasions;
    private OccasionView occasionView;
    private TextView provider;
    private boolean occasionActive;
    private int photoGeneration;
    private TextView weather, credit, photoTitle;
    private final Runnable refreshWeather = this::updateWeather;
    private int weatherCode = -1;
    private boolean weatherFresh;
    private volatile int prefetched;
    private ExecutorService decoder, weatherWorker;
    private ImageView[] layers;
    private DreamClockGlass clockGlass;
    private View clockFace;
    private int session, photoIndex, visibleLayer;
    private boolean dreaming, hasPicture, rotationScheduled;
    private int targetWidth, targetHeight;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(false);
        targetWidth = Math.min(1920, getResources().getDisplayMetrics().widthPixels);
        targetHeight = Math.min(1080, getResources().getDisplayMetrics().heightPixels);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF19283B, 0xFF252137, 0xFF0A0D14}));
        layers = new ImageView[]{new ImageView(this), new ImageView(this)};
        for (ImageView image : layers) {
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setAlpha(0f);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        }
        occasionView = new OccasionView(this, true);
        root.addView(occasionView, new FrameLayout.LayoutParams(-1, -1));
        occasions = new OccasionController(this);
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x08000000, 0x73000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        clockGlass = new DreamClockGlass(this);
        root.addView(clockGlass, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout clockPanel = new LinearLayout(this);
        clockPanel.setOrientation(LinearLayout.VERTICAL);
        clockPanel.setPadding(0, 0, 0, 0);
        LinearLayout timeRow = new LinearLayout(this);
        clockFace = timeRow;
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextClock time = clock(92, 300);
        time.setLetterSpacing(-.035f);
        time.setFormat12Hour("h:mm"); time.setFormat24Hour("HH:mm");
        timeRow.addView(time, new LinearLayout.LayoutParams(-2, -2));
        if (!DateFormat.is24HourFormat(this)) {
            TextClock period = clock(16, 500);
            period.setFormat12Hour("a"); period.setFormat24Hour("");
            period.setTextColor(0xCFFFFFFF);
            LinearLayout.LayoutParams periodParams = new LinearLayout.LayoutParams(-2, -2);
            periodParams.leftMargin = dp(9);
            timeRow.addView(period, periodParams);
        }
        clockPanel.addView(timeRow, new LinearLayout.LayoutParams(-2, -2));
        TextClock date = clock(18, 400);
        String datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setFormat12Hour(datePattern); date.setFormat24Hour(datePattern);
        date.setTextColor(0xDAFFFFFF);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-1, -2);
        dateParams.topMargin = dp(5);
        clockPanel.addView(date, dateParams);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(dp(390), -2,
                Gravity.BOTTOM | Gravity.START);
        panelParams.setMargins(dp(54), 0, dp(54), dp(44));
        weather = text("Connecting to weather...", 16, 500);
        weather.setPadding(dp(15), dp(10), dp(15), dp(10));
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x701E2430, 0x9010141C});
        glass.setCornerRadius(dp(18)); glass.setStroke(dp(1), 0x35FFFFFF);
        weather.setBackground(glass);
        LinearLayout.LayoutParams weatherParams = new LinearLayout.LayoutParams(-2, -2);
        weatherParams.topMargin = dp(18);
        clockPanel.addView(weather, weatherParams);
        root.addView(clockPanel, panelParams);
        clockPanel.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> clockGlass.follow(clockPanel, clockFace));
        timeRow.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> clockGlass.follow(clockPanel, clockFace));
        LinearLayout caption = new LinearLayout(this); caption.setOrientation(LinearLayout.VERTICAL);
        caption.setGravity(Gravity.END);
        photoTitle = text("Loading temple & festival photography...", 18, 500); photoTitle.setGravity(Gravity.END);
        caption.addView(photoTitle);
        credit = text("", 11, 400);
        credit.setTextColor(0xCFFFFFFF); credit.setGravity(Gravity.END);
        caption.addView(credit);
        provider = text("Weather: Open-Meteo  •  Photos: Wikimedia Commons", 10, 400);
        provider.setTextColor(0xACFFFFFF); provider.setGravity(Gravity.END);
        caption.addView(provider);
        FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(dp(440), -2,
                Gravity.BOTTOM | Gravity.END);
        captionParams.setMargins(dp(54), 0, dp(54), dp(48));
        root.addView(caption, captionParams);
        setContentView(root);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (dreaming) return;
        dreaming = true;
        photoIndex = 0;
        weatherSource = new DreamOnlineSource(this);
        weatherWorker = Executors.newSingleThreadExecutor();
        occasions.start(new OccasionController.Listener() {
            @Override public void sceneChanged(OccasionProtocol.Scene scene, Bitmap photo, String effect) {
                if (!dreaming) return;
                if (!occasionActive) {
                    occasionActive = true; photoGeneration++;
                    main.removeCallbacks(rotate); rotationScheduled = false;
                    if (source != null) source.close(); source = null;
                    if (decoder != null) decoder.shutdownNow(); decoder = null;
                    for (ImageView image : layers) { image.animate().cancel(); image.setImageDrawable(null); image.setAlpha(0f); }
                    hasPicture = false;
                }
                occasionView.show(scene, photo, effect);
                if (photo != null) clockGlass.showPhoto(photo, true); else clockGlass.clear();
                photoTitle.setText(scene.title); credit.setText(""); provider.setText("Weather: Open-Meteo");
            }
            @Override public void cleared() {
                occasionActive = false; occasionView.clear();
                if (dreaming && source == null) {
                    clockGlass.clear(); provider.setText("Weather: Open-Meteo  •  Photos: Wikimedia Commons");
                    source = new DreamOnlineSource(WallpaperClockDream.this);
                    decoder = Executors.newSingleThreadExecutor();
                    photoFeed = new DreamPhotoFeed(new java.io.File(getFilesDir(), "dream-feed.json"),
                            getSharedPreferences("dream", 0).getString("theme", "both"), source);
                    nextPicture();
                }
            }
        });
        updateWeather();
    }

    private void nextPicture() {
        rotationScheduled = false;
        if (!dreaming || occasionActive || decoder == null || photoFeed == null) return;
        final int token = session;
        final int photoToken = photoGeneration;
        final DreamOnlineSource activeSource = source;
        final DreamPhotoFeed feed = photoFeed;
        decoder.execute(() -> {
            Bitmap decoded = null;
            JSONObject selected = null;
            try {
                selected = feed.take();
                try { decoded = activeSource.photo(selected, targetWidth, targetHeight); }
                catch (Exception unavailable) {
                    // A missing/corrupt queued download must not leave an empty frame.
                    if (Thread.currentThread().isInterrupted()) throw unavailable;
                    selected = feed.takeFallback();
                    decoded = activeSource.photo(selected, targetWidth, targetHeight);
                }
                feed.shown(selected);
            } catch (Exception failure) { Log.i("WallpaperClock", "Photo unavailable; keeping previous image", failure); }
            final Bitmap picture = decoded;
            final JSONObject item = selected;
            main.post(() -> {
                if (!dreaming || occasionActive || token != session || photoToken != photoGeneration) {
                    if (picture != null) picture.recycle();
                    return;
                }
                if (picture != null) {
                    ImageView old = layers[visibleLayer], next = layers[1 - visibleLayer];
                    next.animate().cancel(); old.animate().cancel();
                    next.setImageBitmap(picture);
                    boolean fade = hasPicture && ValueAnimator.areAnimatorsEnabled();
                    next.setAlpha(fade ? 0f : 1f);
                    if (fade) {
                        next.animate().alpha(1f).setDuration(900).start();
                        old.animate().alpha(0f).setDuration(900)
                                .withEndAction(() -> old.setImageDrawable(null)).start();
                    } else {
                        old.setAlpha(0f); old.setImageDrawable(null);
                    }
                    visibleLayer = 1 - visibleLayer;
                    photoIndex++;
                    hasPicture = true;
                    photoTitle.setText(item.optString("title"));
                    credit.setText(item.optString("author").replaceAll("\\s+", " ") + "  •  " + item.optString("license"));
                    // Shift the overlay a few pixels between photos rather than keeping it static all night.
                    View panel = (View) weather.getParent();
                    panel.setTranslationY(dp(photoIndex % 2 == 0 ? -6 : 0));
                    clockGlass.follow(panel, clockFace);
                    clockGlass.showPhoto(picture, fade);
                }
                if (picture == null && !hasPicture) photoTitle.setText("Photos unavailable · retrying");
                rotationScheduled = true;
                main.postDelayed(rotate, picture == null && !hasPicture ? 10_000 : PHOTO_INTERVAL_MS);
            });
            // Fill from new online search results after the visible photo is on screen.
            // The three ready entries contain compressed disk files, not decoded bitmaps.
            try { feed.fill(); }
            catch (Exception unavailable) { if (!Thread.currentThread().isInterrupted()) Log.i("DreamPhotoFeed", "Queue refill unavailable", unavailable); }
            prefetched = feed.readyCount;
        });
    }

    private void updateWeather() {
        if (!dreaming || weatherWorker == null) return;
        final int token = session;
        final DreamOnlineSource activeSource = weatherSource;
        weatherWorker.execute(() -> {
            JSONObject result = null;
            try { result = activeSource.weather(false); } catch (Exception ignored) {}
            final JSONObject cached = result;
            main.post(() -> { if (dreaming && token == session) showWeather(cached); });
            boolean failed = false;
            try { result = activeSource.weather(true); }
            catch (Exception unavailable) { failed = true; Log.i("WallpaperClock", "Weather refresh unavailable", unavailable); }
            final JSONObject live = result;
            final boolean retry = failed;
            main.post(() -> {
                if (!dreaming || token != session) return;
                showWeather(live);
                main.postDelayed(refreshWeather, retry ? 60_000 : DreamOnlineSource.WEATHER_INTERVAL);
            });
        });
    }

    private void showWeather(JSONObject data) {
        String city = getSharedPreferences("dream", 0).getString("city", "");
        if (city.isEmpty()) { weather.setText("Choose a weather city in Screensaver settings"); return; }
        long age = data == null ? Long.MAX_VALUE : DreamOnlineSource.age(data);
        weatherFresh = age >= 0 && age < 45 * 60_000L;
        if (data == null || !weatherFresh) {
            weather.setText(city + "  •  Weather unavailable"); return;
        }
        try {
            JSONObject current = data.getJSONObject("current");
            weatherCode = current.getInt("weather_code");
            double c = current.getDouble("temperature_2m");
            String temperature = Math.round(c) + "°C";
            weather.setText(city + "   " + temperature + "  ·  " + condition(weatherCode, current.optInt("is_day", 1) == 1));
        } catch (Exception invalid) { weather.setText(city + "  •  Weather unavailable"); }
    }

    static String condition(int code, boolean day) {
        if (code == 0) return day ? "Clear sky" : "Clear night";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95 && code <= 99) return "Thunderstorms";
        return "Weather";
    }

    @Override public void onDreamingStopped() {
        stopPictures();
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        stopPictures();

        super.onDetachedFromWindow();
    }

    private void stopPictures() {
        dreaming = false; session++;
        if (occasions != null) occasions.stop();
        if (occasionView != null) occasionView.clear(); occasionActive = false;
        if (weatherSource != null) weatherSource.close(); weatherSource = null;
        rotationScheduled = false;
        main.removeCallbacks(rotate);
        main.removeCallbacks(refreshWeather);
        if (source != null) source.close();
        source = null;
        if (decoder != null) decoder.shutdownNow();
        decoder = null;
        if (weatherWorker != null) weatherWorker.shutdownNow();
        weatherWorker = null;
        if (layers != null) for (ImageView image : layers) {
            image.animate().cancel(); image.setImageDrawable(null); image.setAlpha(0f);
        }
        hasPicture = false;
        if (clockGlass != null) clockGlass.clear();
    }

    @Override public void onDestroy() {
        stopPictures();
        super.onDestroy();
    }

    private TextView text(String value, int size, int weight) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.WHITE);
        view.setTypeface(getResources().getFont(R.font.outfit));
        view.setFontVariationSettings("'wght' " + weight);
        view.setIncludeFontPadding(false);
        view.setShadowLayer(dp(4), 0, dp(1), 0xA0000000);
        return view;
    }

    private TextClock clock(int size, int weight) {
        TextClock view = new TextClock(this);
        view.setTextSize(size); view.setTextColor(Color.WHITE);
        view.setTypeface(getResources().getFont(R.font.outfit));
        view.setFontVariationSettings("'wght' " + weight);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setShadowLayer(dp(5), 0, dp(2), 0x90000000);
        return view;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(fd, writer, args);
        long bytes = 0;
        if (layers != null) for (ImageView image : layers)
            if (image.getDrawable() instanceof BitmapDrawable)
                bytes += ((BitmapDrawable) image.getDrawable()).getBitmap().getAllocationByteCount();
        writer.println("WallpaperClock dreaming=" + dreaming
                + " prefetched=" + prefetched
                + " ready=" + (photoFeed == null ? 0 : photoFeed.readyCount)
                + " discoveredBatches=" + (photoFeed == null ? 0 : photoFeed.discoveries)
                + " candidates=" + (photoFeed == null ? 0 : photoFeed.candidateCount)
                + " recent=" + (photoFeed == null ? 0 : photoFeed.recentCount)
                + " photoOrigin=" + (photoFeed == null ? "none" : photoFeed.lastSelection)
                + " feedErrors=" + (photoFeed == null ? 0 : photoFeed.discoveryErrors)
                + " weatherCode=" + weatherCode + " weatherFresh=" + weatherFresh + " worker=" + (decoder != null)
                + " shown=" + photoIndex + " bitmapBytes=" + bytes + " rotationScheduled=" + rotationScheduled);
        if (clockGlass != null) clockGlass.dump(writer);
    }
}
