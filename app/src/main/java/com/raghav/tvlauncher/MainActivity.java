package com.raghav.tvlauncher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nova-style TV home screen, v4.
 * Photo wallpaper with a slow drift, glass tiles with real logos, three designs,
 * a Look panel (design + wallpaper) and a Settings panel, and Xiaomi/system bloat hidden by default.
 */
public class MainActivity extends Activity {

    // ------------------------------------------------------------ catalogue

    private static final int T_WHITE = 0, T_BLACK = 1, T_NONE = 2, T_FG = 3;
    private static final int DESIGN_GLASS = 0, DESIGN_ICONS = 1, DESIGN_MINIMAL = 2;
    private static final String[] DESIGN_NAMES = {"Glass", "App Icons", "Minimal"};

    private static class AppDef {
        final String id, name;
        final String[] pkgs;
        final int c1, c2, tint, fg;

        AppDef(String id, String name, String[] pkgs, int c1, int c2, int tint, int fg) {
            this.id = id;
            this.name = name;
            this.pkgs = pkgs;
            this.c1 = c1;
            this.c2 = c2;
            this.tint = tint;
            this.fg = fg;
        }
    }

    private static AppDef d(String id, String name, int c1, int c2, int tint, int fg, String... pkgs) {
        return new AppDef(id, name, pkgs, c1, c2, tint, fg);
    }

    private static final AppDef[] DEFS = {
            d("netflix", "Netflix", 0xFF242424, 0xFF050505, T_FG, 0xFFE50914, "com.netflix.ninja"),
            d("youtube", "YouTube", 0xFFFF2A2A, 0xFFD90000, T_WHITE, 0, "com.google.android.youtube.tv"),
            d("primevideo", "Prime Video", 0xFF1FA8FF, 0xFF0A6FD6, T_WHITE, 0, "com.amazon.amazonvideo.livingroom"),
            d("disneyplus", "Disney+", 0xFF1C3F9E, 0xFF071447, T_WHITE, 0, "com.disney.disneyplus"),
            d("appletv", "Apple TV", 0xFF3A3A3E, 0xFF0B0B0C, T_WHITE, 0, "com.apple.atve.androidtv.appletv"),
            d("hbomax", "HBO Max", 0xFF3D4BF5, 0xFF1A1499, T_WHITE, 0, "com.wbd.stream", "com.hbo.hbonow"),
            d("hulu", "Hulu", 0xFF3DF59A, 0xFF14C96F, T_BLACK, 0, "com.hulu.livingroomplus"),
            d("spotify", "Spotify", 0xFF262626, 0xFF0B0B0B, T_FG, 0xFF1ED760, "com.spotify.tv.android"),
            d("paramountplus", "Paramount+", 0xFF1A7BFF, 0xFF0040B8, T_WHITE, 0, "com.cbs.ott", "com.cbs.app"),
            d("peacock", "Peacock", 0xFFFFFFFF, 0xFFE9E9EC, T_NONE, 0, "com.peacocktv.peacockandroid"),
            d("youtubetv", "YouTube TV", 0xFFFFFFFF, 0xFFECECEC, T_FG, 0xFFFF0000, "com.google.android.youtube.tvunplugged"),
            d("twitch", "Twitch", 0xFFA970FF, 0xFF772CE8, T_WHITE, 0, "tv.twitch.android.app"),
            d("plex", "Plex", 0xFF2B2B2B, 0xFF111111, T_FG, 0xFFEBAF00, "com.plexapp.android"),
            d("crunchyroll", "Crunchyroll", 0xFFFF7A26, 0xFFE84E00, T_WHITE, 0, "com.crunchyroll.crunchyroid"),
            d("tubi", "Tubi", 0xFF8A2BFF, 0xFF5200CC, T_WHITE, 0, "com.tubitv.ott", "com.tubitv"),
            d("youtubemusic", "YouTube Music", 0xFF262626, 0xFF050505, T_FG, 0xFFFF0000, "com.google.android.youtube.tvmusic"),
            d("applemusic", "Apple Music", 0xFFFF6B81, 0xFFF0243C, T_WHITE, 0, "com.apple.android.music"),
    };

    /** Apps without a logo that should still come first (India streaming apps). */
    private static final String[] EXTRA_FIRST = {
            "in.startv.hotstar.dplus", "in.startv.hotstar", "com.jio.media.stb.ondemand",
    };

    /** Preinstalled junk: hidden by default (anything starting with these). */
    private static final String[] BLOAT_PREFIX = {
            "com.xiaomi.", "com.mitv.", "com.miui.", "co.sensara.", "mitv.", "com.mi.",
            "android.autoinstalls.",
    };

    /** Built-in apps that are actually useful, so they stay visible. */
    private static final String[] KEEP_SYSTEM = {
            "com.android.vending", "com.android.chrome", "com.google.android.apps.chromecast.app",
    };

    private static final String[] BG_NAMES = {
            "misty_coast", "quiet_cove", "forest_mist", "mountain_valley",
            "desert_dunes", "blue_peaks", "city_dusk", "golden_hour",
    };

    private static class Item {
        AppDef def;
        ResolveInfo ri;
        String pkg;
        String label;
    }

    private interface Styler {
        /** t = 0 unfocused, 1 focused; animates in between. */
        void apply(float t);
    }

    // ------------------------------------------------------------ state

    private SharedPreferences prefs;
    private int design;
    private int bgIdx;
    private String renderedSig = "";
    private int bgToken = 0;
    private int hiddenCount = 0;
    private boolean introDone = false;
    private boolean panelOpen = false;
    private View firstView;
    private View returnFocus;

    private ScrollView scroll;
    private FrameLayout mainLayer;
    private FrameLayout panelLayer;
    private LinearLayout panelCard;
    private ImageView bgView;
    private View scrim;
    private View headerView;
    private LinearLayout clockBlock;
    private TextView greetView, hdrTime, hdrDate;
    private TextView minTime, minDate;
    private LinearLayout body;
    private ValueAnimator drift;

    private final ArgbEvaluator argb = new ArgbEvaluator();
    private final Map<String, Integer> resCache = new HashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 15000);
        }
    };

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("nova", MODE_PRIVATE);
        design = Math.abs(prefs.getInt("design", DESIGN_GLASS)) % 3;
        bgIdx = Math.abs(prefs.getInt("bg", 0)) % BG_NAMES.length;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        bgView = new ImageView(this);
        bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bgView, match());

        scrim = new View(this);
        root.addView(scrim, match());

        mainLayer = new FrameLayout(this);
        root.addView(mainLayer, match());

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        mainLayer.addView(scroll, match());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setPadding(dp(80), dp(30), dp(80), dp(28));

        headerView = buildHeader();
        content.addView(headerView);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(18);
        content.addView(body, bp);
        scroll.addView(content);

        buildPanelLayer();
        root.addView(panelLayer, match());
        setContentView(root);

        updateScrim();
        applyBackground(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(false, null);
        handler.post(tick);
        startDrift();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        if (drift != null) drift.cancel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bgToken++;
        io.shutdownNow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (panelOpen) closePanel();
        scroll.smoothScrollTo(0, 0);
        if (firstView != null) firstView.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (panelOpen) closePanel();
        // otherwise: we are the home screen, Back does nothing
    }

    // ------------------------------------------------------------ header

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setClipChildren(false);

        clockBlock = new LinearLayout(this);
        clockBlock.setOrientation(LinearLayout.VERTICAL);
        greetView = makeText(13, 0xCCFFFFFF, Typeface.BOLD);
        greetView.setLetterSpacing(0.16f);
        hdrTime = makeText(54, Color.WHITE, Typeface.NORMAL);
        hdrTime.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        hdrDate = makeText(16, 0xDDFFFFFF, Typeface.NORMAL);
        clockBlock.addView(greetView);
        clockBlock.addView(hdrTime);
        clockBlock.addView(hdrDate);
        header.addView(clockBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);

        RoundButton look = new RoundButton(RoundButton.LOOK);
        look.setOnClickListener(v -> showLookPanel());
        RoundButton gear = new RoundButton(RoundButton.GEAR);
        gear.setOnClickListener(v -> showSettingsPanel());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.leftMargin = dp(14);
        buttons.addView(look, bl);
        buttons.addView(gear, bl);

        header.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    /** Glass circle with a drawn icon, like Nova's top-right buttons. */
    private class RoundButton extends View {
        static final int LOOK = 0, GEAR = 1;
        final int type;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float t = 0f;

        RoundButton(int type) {
            super(MainActivity.this);
            this.type = type;
            setFocusable(true);
            setClickable(true);
            attachFocus(this, 1.12f, 8, f -> {
                t = f;
                invalidate();
            });
        }

        @Override
        protected void onMeasure(int w, int h) {
            int s = dp(54);
            setMeasuredDimension(s, s);
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = cx - dpf(1.5f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(lerp(0x40000000, 0xFFFFFFFF, t));
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dpf(1.2f));
            p.setColor(lerp(0x66FFFFFF, 0xFFFFFFFF, t));
            c.drawCircle(cx, cy, r, p);

            int ic = lerp(Color.WHITE, 0xFF111111, t);
            p.setColor(ic);
            p.setStrokeWidth(dpf(2.2f));
            p.setStrokeCap(Paint.Cap.ROUND);
            if (type == GEAR) {
                float ir = cx * 0.30f;
                c.drawCircle(cx, cy, ir, p);
                c.drawCircle(cx, cy, ir * 0.42f, p);
                for (int k = 0; k < 8; k++) {
                    double a = Math.toRadians(k * 45);
                    float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                    c.drawLine(cx + cos * ir * 1.15f, cy + sin * ir * 1.15f,
                            cx + cos * ir * 1.55f, cy + sin * ir * 1.55f, p);
                }
            } else {
                float half = cx * 0.46f, gap = cx * 0.34f;
                float[] knob = {0.30f, 0.68f, 0.45f};
                for (int k = 0; k < 3; k++) {
                    float y = cy + (k - 1) * gap;
                    c.drawLine(cx - half, y, cx + half, y, p);
                    float x = cx - half + 2 * half * knob[k];
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(x, y, dpf(4.2f), p);
                    p.setStyle(Paint.Style.STROKE);
                }
            }
        }
    }

    // ------------------------------------------------------------ panels

    private void buildPanelLayer() {
        panelLayer = new FrameLayout(this);
        panelLayer.setBackgroundColor(0xCC000000);
        panelLayer.setVisibility(View.GONE);
        panelLayer.setClickable(true);

        panelCard = new LinearLayout(this);
        panelCard.setOrientation(LinearLayout.VERTICAL);
        panelCard.setPadding(dp(32), dp(26), dp(32), dp(28));
        panelCard.setMinimumWidth(dp(460));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF2141418);
        g.setCornerRadius(dp(26));
        g.setStroke(dp(1), 0x44FFFFFF);
        panelCard.setBackground(g);

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        panelLayer.addView(panelCard, cp);
    }

    private void openPanel(String title, View content, View focusFirst) {
        returnFocus = getCurrentFocus();
        panelCard.removeAllViews();
        TextView tv = makeText(24, Color.WHITE, Typeface.BOLD);
        tv.setShadowLayer(0, 0, 0, 0);
        tv.setText(title);
        panelCard.addView(tv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        panelCard.addView(content, lp);

        panelOpen = true;
        mainLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        panelLayer.setVisibility(View.VISIBLE);
        panelLayer.setAlpha(0f);
        panelLayer.animate().alpha(1f).setDuration(200).start();
        panelCard.setTranslationY(dp(26));
        panelCard.setScaleX(0.96f);
        panelCard.setScaleY(0.96f);
        panelCard.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        if (focusFirst != null) focusFirst.requestFocus();
    }

    private void closePanel() {
        if (!panelOpen) return;
        panelOpen = false;
        mainLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        panelLayer.animate().alpha(0f).setDuration(160)
                .withEndAction(() -> {
                    if (!panelOpen) panelLayer.setVisibility(View.GONE);
                }).start();
        if (returnFocus != null && returnFocus.getParent() != null) returnFocus.requestFocus();
        else if (firstView != null) firstView.requestFocus();
    }

    private void showSettingsPanel() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setMinimumWidth(dp(400));

        TextView first = panelButton("Wi-Fi & network", () -> openSetting(Settings.ACTION_WIFI_SETTINGS));
        c.addView(first, rowParams());
        c.addView(panelButton("Bluetooth", () -> openSetting(Settings.ACTION_BLUETOOTH_SETTINGS)), rowParams());
        c.addView(panelButton("Display", () -> openSetting(Settings.ACTION_DISPLAY_SETTINGS)), rowParams());
        c.addView(panelButton("Sound", () -> openSetting(Settings.ACTION_SOUND_SETTINGS)), rowParams());
        c.addView(panelButton("All settings", () -> openSetting(Settings.ACTION_SETTINGS)), rowParams());

        boolean showAll = prefs.getBoolean("showAll", false);
        c.addView(panelButton(showAll ? "System apps: shown (tap to hide)" : "System apps: hidden (tap to show)",
                () -> {
                    prefs.edit().putBoolean("showAll", !prefs.getBoolean("showAll", false)).apply();
                    closePanel();
                    render(true, null);
                }), rowParams());

        if (hiddenCount > 0) {
            c.addView(panelButton("Bring back " + hiddenCount + " hidden app" + (hiddenCount > 1 ? "s" : ""),
                    () -> {
                        prefs.edit().putStringSet("hidden", new HashSet<String>()).apply();
                        closePanel();
                        render(true, null);
                    }), rowParams());
        }
        openPanel("Quick settings", c, first);
    }

    private void showLookPanel() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setClipChildren(false);

        c.addView(sectionLabel("Design"));
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setClipChildren(false);
        final List<TextView> chips = new ArrayList<>();
        for (int i = 0; i < DESIGN_NAMES.length; i++) {
            final int idx = i;
            final TextView chip = makeChip(DESIGN_NAMES[i], i == design);
            chip.setOnClickListener(v -> {
                design = idx;
                prefs.edit().putInt("design", design).apply();
                updateScrim();
                for (int k = 0; k < chips.size(); k++) {
                    chips.get(k).setSelected(k == idx);
                    refreshStyle(chips.get(k));
                }
                render(true, null);
            });
            chips.add(chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(12);
            chipRow.addView(chip, lp);
        }
        c.addView(chipRow);

        c.addView(sectionLabel("Wallpaper"));
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipChildren(false);
        hsv.setClipToPadding(false);
        LinearLayout thumbRow = new LinearLayout(this);
        thumbRow.setOrientation(LinearLayout.HORIZONTAL);
        thumbRow.setClipChildren(false);
        thumbRow.setPadding(dp(4), dp(10), dp(4), dp(10));
        final List<FrameLayout> thumbs = new ArrayList<>();
        for (int i = 0; i < BG_NAMES.length; i++) {
            final int idx = i;
            FrameLayout th = makeThumb(i);
            th.setSelected(i == bgIdx);
            refreshStyle(th);
            th.setOnClickListener(v -> {
                bgIdx = idx;
                prefs.edit().putInt("bg", bgIdx).apply();
                applyBackground(true);
                for (int k = 0; k < thumbs.size(); k++) {
                    thumbs.get(k).setSelected(k == idx);
                    refreshStyle(thumbs.get(k));
                }
            });
            thumbs.add(th);
            thumbRow.addView(th);
        }
        hsv.addView(thumbRow);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(640),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        c.addView(hsv, hp);

        openPanel("Look", c, chips.get(design));
    }

    private void showAppMenu(final Item it) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setMinimumWidth(dp(380));

        TextView first = panelButton("Open", () -> {
            closePanel();
            launch(it);
        });
        c.addView(first, rowParams());
        c.addView(panelButton("App info", () -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + it.pkg)));
            } catch (Exception e) {
                toast("App info not available");
            }
        }), rowParams());
        c.addView(panelButton("Hide from home", () -> {
            Set<String> h = hiddenSet();
            h.add(it.pkg);
            prefs.edit().putStringSet("hidden", h).apply();
            closePanel();
            render(true, null);
        }), rowParams());
        boolean system = (it.ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        if (!system) {
            c.addView(panelButton("Uninstall", () -> {
                try {
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + it.pkg)));
                } catch (Exception e) {
                    toast("Can't uninstall this app");
                }
            }), rowParams());
        }
        openPanel(it.label, c, first);
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        return lp;
    }

    private TextView panelButton(String label, final Runnable action) {
        final TextView t = makeText(18, Color.WHITE, Typeface.NORMAL);
        t.setShadowLayer(0, 0, 0, 0);
        t.setText(label);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(22), dp(14), dp(22), dp(14));
        t.setFocusable(true);
        t.setClickable(true);
        attachFocus(t, 1.03f, 0, f -> {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(14));
            g.setColor(lerp(0x26FFFFFF, 0xFFFFFFFF, f));
            t.setBackground(g);
            t.setTextColor(lerp(Color.WHITE, 0xFF111111, f));
        });
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private TextView makeChip(String label, boolean selected) {
        final TextView t = makeText(17, Color.WHITE, Typeface.BOLD);
        t.setShadowLayer(0, 0, 0, 0);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(24), dp(12), dp(24), dp(12));
        t.setFocusable(true);
        t.setClickable(true);
        t.setSelected(selected);
        Styler s = f -> {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(22));
            g.setColor(lerp(t.isSelected() ? 0x66FFFFFF : 0x22FFFFFF, 0xFFFFFFFF, f));
            if (t.isSelected()) g.setStroke(dp(2), lerp(0xCCFFFFFF, 0xFFFFFFFF, f));
            t.setBackground(g);
            t.setTextColor(lerp(Color.WHITE, 0xFF111111, f));
        };
        attachFocus(t, 1.06f, 0, s);
        t.setTag(s);
        return t;
    }

    private FrameLayout makeThumb(int i) {
        final FrameLayout th = new FrameLayout(this);
        th.setFocusable(true);
        th.setClickable(true);
        th.setPadding(dp(3), dp(3), dp(3), dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), dp(90));
        lp.rightMargin = dp(12);
        th.setLayoutParams(lp);

        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ph = new GradientDrawable();
        ph.setColor(0xFF222226);
        ph.setCornerRadius(dp(10));
        iv.setBackground(ph);
        iv.setClipToOutline(true);
        th.addView(iv, match());

        Styler s = f -> {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(13));
            g.setColor(0x00000000);
            int width = th.isSelected() ? 3 : 0;
            if (f > 0.05f) width = 3;
            if (width > 0) {
                g.setStroke(dp(width), th.isSelected() && f < 0.5f ? 0xCCFFFFFF : lerp(0x00FFFFFF, 0xFFFFFFFF, Math.max(f, 0.6f)));
            }
            th.setBackground(g);
        };
        attachFocus(th, 1.08f, 6, s);
        th.setTag(s);

        final int id = resId("bg_" + BG_NAMES[i]);
        try {
            io.execute(() -> {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 8;
                final Bitmap b = BitmapFactory.decodeResource(getResources(), id, o);
                if (b != null) runOnUiThread(() -> iv.setImageBitmap(b));
            });
        } catch (Exception ignored) {
            // executor shut down
        }
        return th;
    }

    private void refreshStyle(View v) {
        Object s = v.getTag();
        if (s instanceof Styler) ((Styler) s).apply(v.hasFocus() ? 1f : 0f);
    }

    private TextView sectionLabel(String text) {
        TextView tv = makeText(12, 0x99FFFFFF, Typeface.BOLD);
        tv.setShadowLayer(0, 0, 0, 0);
        tv.setText(text.toUpperCase(Locale.getDefault()));
        tv.setLetterSpacing(0.16f);
        tv.setPadding(dp(2), dp(18), 0, dp(8));
        return tv;
    }

    private void openSetting(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e2) {
                toast("Settings not available");
            }
        }
    }

    // ------------------------------------------------------------ background

    private void startDrift() {
        if (drift != null) drift.cancel();
        drift = ValueAnimator.ofFloat(0f, 1f);
        drift.setDuration(28000);
        drift.setRepeatMode(ValueAnimator.REVERSE);
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            float s = 1.04f + 0.06f * f;
            bgView.setScaleX(s);
            bgView.setScaleY(s);
            bgView.setTranslationX(-dpf(18) * f);
            bgView.setTranslationY(-dpf(8) * f);
        });
        drift.start();
    }

    /** Decodes the wallpaper off the UI thread, then cross-fades it in. */
    private void applyBackground(final boolean animate) {
        final int id = resId("bg_" + BG_NAMES[bgIdx]);
        if (id == 0) return;
        final int token = ++bgToken;
        final int targetW = getResources().getDisplayMetrics().widthPixels;
        try {
            io.execute(() -> {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeResource(getResources(), id, bounds);
                int sample = 1;
                while (bounds.outWidth / (sample * 2) >= targetW) sample *= 2;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                final Bitmap bmp = BitmapFactory.decodeResource(getResources(), id, o);
                if (bmp == null) return;
                runOnUiThread(() -> {
                    if (token != bgToken || isFinishing()) return;
                    showBackground(new BitmapDrawable(getResources(), bmp), animate);
                });
            });
        } catch (Exception ignored) {
            // executor shut down
        }
    }

    private void showBackground(Drawable next, boolean animate) {
        Drawable old = bgView.getDrawable();
        if (old instanceof TransitionDrawable) old = ((TransitionDrawable) old).getDrawable(1);
        if (!animate || old == null) {
            bgView.setImageDrawable(next);
            if (!animate) {
                bgView.setAlpha(0f);
                bgView.animate().alpha(1f).setDuration(600).start();
            }
            return;
        }
        TransitionDrawable td = new TransitionDrawable(new Drawable[]{old, next});
        td.setCrossFadeEnabled(true);
        bgView.setImageDrawable(td);
        td.startTransition(650);
    }

    private void updateScrim() {
        boolean minimal = design == DESIGN_MINIMAL;
        int top = minimal ? 0x66000000 : 0x40000000;
        int mid = minimal ? 0x33000000 : 0x14000000;
        int bottom = minimal ? 0xAA000000 : 0x88000000;
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, mid, bottom}));
    }

    // ------------------------------------------------------------ clock

    private void updateClock() {
        Date now = new Date();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greet = hour < 12 ? "GOOD MORNING" : (hour < 17 ? "GOOD AFTERNOON" : "GOOD EVENING");
        String t = new SimpleDateFormat("h:mm", Locale.getDefault()).format(now);
        String dt = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now);
        if (greetView != null) greetView.setText(greet);
        if (hdrTime != null) hdrTime.setText(t);
        if (hdrDate != null) hdrDate.setText(dt);
        if (minTime != null) minTime.setText(t);
        if (minDate != null) minDate.setText(dt);
    }

    // ------------------------------------------------------------ apps

    private Set<String> hiddenSet() {
        return new HashSet<>(prefs.getStringSet("hidden", new HashSet<String>()));
    }

    private boolean isVisible(Item it, boolean showAll) {
        if (it.def != null) return true;
        for (String p : EXTRA_FIRST) if (p.equals(it.pkg)) return true;
        if (showAll) return true;
        for (String p : BLOAT_PREFIX) if (it.pkg.startsWith(p)) return false;
        int f = it.ri.activityInfo.applicationInfo.flags;
        boolean system = (f & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean updated = (f & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        if (!system || updated) return true;
        for (String p : KEEP_SYSTEM) if (p.equals(it.pkg)) return true;
        return false;
    }

    private List<Item> queryItems() {
        final PackageManager pm = getPackageManager();
        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!pkg.equals(getPackageName()) && !found.containsKey(pkg)) found.put(pkg, ri);
            }
        }

        List<Item> out = new ArrayList<>();
        for (AppDef def : DEFS) {
            for (String pkg : def.pkgs) {
                ResolveInfo ri = found.remove(pkg);
                if (ri != null) {
                    out.add(makeItem(pm, def, ri));
                    break;
                }
            }
        }
        for (String pkg : EXTRA_FIRST) {
            ResolveInfo ri = found.remove(pkg);
            if (ri != null) out.add(makeItem(pm, null, ri));
        }
        List<ResolveInfo> rest = new ArrayList<>(found.values());
        Collections.sort(rest, (a, b) ->
                a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        for (ResolveInfo ri : rest) out.add(makeItem(pm, null, ri));
        return out;
    }

    private Item makeItem(PackageManager pm, AppDef def, ResolveInfo ri) {
        Item it = new Item();
        it.def = def;
        it.ri = ri;
        it.pkg = ri.activityInfo.packageName;
        it.label = def != null ? def.name : ri.loadLabel(pm).toString();
        return it;
    }

    /** Rebuilds the app area. Skipped on resume if nothing changed, so returning from an app never flickers. */
    private void render(boolean force, View focusTarget) {
        Set<String> hidden = hiddenSet();
        boolean showAll = prefs.getBoolean("showAll", false);
        List<Item> items = new ArrayList<>();
        int hc = 0;
        for (Item it : queryItems()) {
            if (hidden.contains(it.pkg)) {
                hc++;
            } else if (isVisible(it, showAll)) {
                items.add(it);
            }
        }
        hiddenCount = hc;

        StringBuilder sb = new StringBuilder();
        sb.append(design).append('|');
        for (Item it : items) sb.append(it.pkg).append(',');
        String sig = sb.toString();
        if (!force && sig.equals(renderedSig)) return;
        renderedSig = sig;

        body.removeAllViews();
        minTime = null;
        minDate = null;
        clockBlock.setVisibility(design == DESIGN_MINIMAL ? View.INVISIBLE : View.VISIBLE);

        if (design == DESIGN_MINIMAL) {
            firstView = renderMinimal(items);
        } else {
            firstView = renderGrid(items, design == DESIGN_GLASS);
        }
        updateClock();

        DecelerateInterpolator decel = new DecelerateInterpolator(1.6f);
        if (!introDone) {
            introDone = true;
            headerView.setAlpha(0f);
            headerView.setTranslationY(-dp(12));
            headerView.animate().alpha(1f).translationY(0f).setDuration(520).setInterpolator(decel).start();
        }
        for (int i = 0; i < body.getChildCount(); i++) {
            View row = body.getChildAt(i);
            row.setAlpha(0f);
            row.setTranslationY(dp(26));
            row.animate().alpha(1f).translationY(0f)
                    .setStartDelay(Math.min(120 + i * 80L, 520L)).setDuration(420)
                    .setInterpolator(decel).start();
        }

        if (!panelOpen) {
            View target = focusTarget != null ? focusTarget : firstView;
            if (target != null) target.requestFocus();
        }
    }

    private int usableWidth() {
        return getResources().getDisplayMetrics().widthPixels - 2 * dp(80);
    }

    // ---- grid (Glass and App Icons)

    private View renderGrid(List<Item> items, boolean glass) {
        int cols = glass ? 4 : 5;
        int gap = dp(glass ? 18 : 16);
        int w = (usableWidth() - (cols - 1) * gap) / cols;
        View first = null;
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            int col = i % cols;
            if (col == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                body.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            View v = glass ? glassTile(items.get(i), w) : iconTile(items.get(i), w);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
            lp.rightMargin = col < cols - 1 ? gap : 0;
            lp.bottomMargin = gap;
            row.addView(v);
            if (first == null) first = v;
        }
        return first;
    }

    private View glassTile(final Item it, int w) {
        int h = Math.round(w * 0.54f);
        final FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setClipToOutline(true);
        tile.setLayoutParams(new LinearLayout.LayoutParams(w, h));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        inner.addView(logoOrIcon(it, Math.round(w * 0.56f), Math.round(h * 0.30f),
                T_WHITE, 0, Math.round(h * 0.38f)));
        TextView name = makeText(15, Color.WHITE, Typeface.NORMAL);
        name.setText(it.label);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(dp(8), dp(8), dp(8), 0);
        inner.addView(name);
        tile.addView(inner, match());

        attachFocus(tile, 1.09f, 16, f -> {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{lerp(0x38FFFFFF, 0x8CFFFFFF, f), lerp(0x12FFFFFF, 0x4DFFFFFF, f)});
            g.setCornerRadius(dp(20));
            g.setStroke(Math.round(dpf(1f + 1.6f * f)), lerp(0x55FFFFFF, 0xFFFFFFFF, f));
            tile.setBackground(g);
        });
        attachActions(tile, it);
        return tile;
    }

    private View iconTile(final Item it, int w) {
        final LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setClipChildren(false);
        cell.setLayoutParams(new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));

        int h = Math.round(w * 0.58f);
        final FrameLayout tile = new FrameLayout(this);
        tile.setClipToOutline(true);
        cell.addView(tile, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

        Drawable banner = it.def == null ? it.ri.activityInfo.loadBanner(getPackageManager()) : null;
        if (banner != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(iv, match());
        } else {
            int tint = it.def != null ? it.def.tint : T_WHITE;
            int fg = it.def != null ? it.def.fg : 0;
            ImageView logo = logoOrIcon(it, Math.round(w * 0.62f), Math.round(h * 0.42f),
                    tint, fg, Math.round(h * 0.55f));
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ip.gravity = Gravity.CENTER;
            tile.addView(logo, ip);
        }

        TextView name = makeText(14, Color.WHITE, Typeface.NORMAL);
        name.setText(it.label);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(8);
        cell.addView(name, np);

        final int[] colors = it.def != null
                ? new int[]{it.def.c1, it.def.c2}
                : new int[]{0xFF2E2E34, 0xFF141418};
        attachFocus(cell, 1.1f, 16, f -> {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
            g.setCornerRadius(dp(18));
            int sw = Math.round(dpf(3f * f));
            if (sw > 0) g.setStroke(sw, Color.WHITE);
            tile.setBackground(g);
        });
        attachActions(cell, it);
        return cell;
    }

    // ---- minimal (big clock + logo dock)

    private View renderMinimal(List<Item> items) {
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER_HORIZONTAL);
        minTime = makeText(112, Color.WHITE, Typeface.NORMAL);
        minTime.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        minTime.setGravity(Gravity.CENTER);
        minDate = makeText(20, 0xDDFFFFFF, Typeface.NORMAL);
        minDate.setGravity(Gravity.CENTER);
        clock.addView(minTime);
        clock.addView(minDate);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(10);
        body.addView(clock, cp);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setFillViewport(true);
        hsv.setClipChildren(false);
        hsv.setClipToPadding(false);
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setClipChildren(false);
        dock.setPadding(dp(8), dp(12), dp(8), dp(12));

        int n = Math.max(1, items.size());
        int itemW = Math.max(dp(70), Math.min(dp(112), usableWidth() / n - dp(12)));
        View first = null;
        for (Item it : items) {
            View v = dockItem(it, itemW);
            dock.addView(v);
            if (first == null) first = v;
        }
        hsv.addView(dock, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(110);
        body.addView(hsv, hp);
        return first;
    }

    private View dockItem(final Item it, int w) {
        final FrameLayout cell = new FrameLayout(this);
        cell.setFocusable(true);
        cell.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, Math.round(w * 0.64f));
        lp.leftMargin = dp(6);
        lp.rightMargin = dp(6);
        cell.setLayoutParams(lp);

        ImageView logo = logoOrIcon(it, Math.round(w * 0.72f), Math.round(w * 0.30f),
                T_WHITE, 0, Math.round(w * 0.40f));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.gravity = Gravity.CENTER;
        cell.addView(logo, ip);

        attachFocus(cell, 1.2f, 14, f -> {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(18));
            g.setColor(lerp(0x00FFFFFF, 0x47FFFFFF, f));
            int sw = Math.round(dpf(2f * f));
            if (sw > 0) g.setStroke(sw, 0xDDFFFFFF);
            cell.setBackground(g);
        });
        attachActions(cell, it);
        return cell;
    }

    // ------------------------------------------------------------ shared helpers

    private int resId(String name) {
        Integer cached = resCache.get(name);
        if (cached != null) return cached;
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        resCache.put(name, id);
        return id;
    }

    /** Logo PNG if we have one (tinted), otherwise the app's own icon. Sizes are in pixels. */
    private ImageView logoOrIcon(Item it, int maxW, int maxH, int tint, int fg, int iconSize) {
        ImageView iv = new ImageView(this);
        int id = it.def != null ? resId("logo_" + it.def.id) : 0;
        if (id != 0) {
            iv.setImageResource(id);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setMaxWidth(maxW);
            iv.setMaxHeight(maxH);
            if (tint == T_WHITE) iv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            else if (tint == T_BLACK) iv.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
            else if (tint == T_FG) iv.setColorFilter(fg, PorterDuff.Mode.SRC_IN);
            iv.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            iv.setImageDrawable(it.ri.loadIcon(getPackageManager()));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        }
        return iv;
    }

    /** Smooth focus: bouncy scale + an animated 0..1 style progress. */
    private void attachFocus(final View v, final float scale, final int elevDp, final Styler styler) {
        final OvershootInterpolator bounce = new OvershootInterpolator(1.4f);
        final float[] prog = {0f};
        final ValueAnimator[] anim = {null};
        styler.apply(0f);
        v.setOnFocusChangeListener((view, has) -> {
            float s = has ? scale : 1f;
            view.animate().setStartDelay(0).scaleX(s).scaleY(s)
                    .setDuration(has ? 260 : 180).setInterpolator(bounce).start();
            view.setElevation(has ? dp(elevDp) : 0);
            if (anim[0] != null) anim[0].cancel();
            ValueAnimator a = ValueAnimator.ofFloat(prog[0], has ? 1f : 0f);
            a.setDuration(has ? 200 : 150);
            a.addUpdateListener(x -> {
                prog[0] = (Float) x.getAnimatedValue();
                styler.apply(prog[0]);
            });
            anim[0] = a;
            a.start();
        });
    }

    private void launch(Item it) {
        PackageManager pm = getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(it.pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(it.pkg);
        if (launch == null) return;
        try {
            startActivity(launch);
        } catch (Exception e) {
            toast("Can't open this app");
        }
    }

    private void attachActions(View v, final Item it) {
        v.setOnClickListener(view -> launch(it));
        // Long-press: Open / App info / Hide / Uninstall.
        v.setOnLongClickListener(view -> {
            showAppMenu(it);
            return true;
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private TextView makeText(int sp, int color, int style) {
        TextView tv = new TextView(this);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT, style);
        tv.setShadowLayer(dp(6), 0, dp(1), 0x66000000);
        return tv;
    }

    private int lerp(int a, int b, float t) {
        return (Integer) argb.evaluate(Math.max(0f, Math.min(1f, t)), a, b);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private float dpf(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
