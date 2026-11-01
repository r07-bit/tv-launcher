package com.raghav.tvlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nova-style home screen: photo wallpaper + three looks (Glass grid, App Icons, Minimal).
 * Launches real apps. "Wallpaper" and "Design" pills cycle the look and are remembered.
 */
public class MainActivity extends Activity {

    // ------------------------------------------------------------ app catalogue

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

    // Order here = order on screen (for installed apps).
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

    // Apps without a logo that should still come first (India streaming apps).
    private static final String[] EXTRA_FIRST = {
            "in.startv.hotstar.dplus", "in.startv.hotstar", "com.jio.media.stb.ondemand",
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
        void apply(boolean focused);
    }

    // ------------------------------------------------------------ state

    private SharedPreferences prefs;
    private int design;
    private int bgIdx;
    private String renderedSig = "";
    private int bgToken = 0;
    private View firstView;

    private ScrollView scroll;
    private ImageView bgView;
    private View scrim;
    private LinearLayout clockBlock;
    private TextView hdrTime, hdrDate;
    private TextView minTime, minDate;
    private TextView designPill;
    private LinearLayout body;

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
        root.addView(bgView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scrim = new View(this);
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setPadding(dp(64), dp(28), dp(64), dp(24));

        content.addView(buildHeader());

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(16);
        content.addView(body, bp);

        scroll.addView(content);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        applyBackground(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(false, null);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
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
        // Home pressed while we're already the home screen: glide back to the top.
        scroll.smoothScrollTo(0, 0);
        if (firstView != null) firstView.requestFocus();
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back does nothing.
    }

    // ------------------------------------------------------------ header

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setClipChildren(false);

        clockBlock = new LinearLayout(this);
        clockBlock.setOrientation(LinearLayout.VERTICAL);
        hdrTime = makeText(52, Color.WHITE, Typeface.NORMAL);
        hdrTime.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        hdrDate = makeText(16, 0xDDFFFFFF, Typeface.NORMAL);
        clockBlock.addView(hdrTime);
        clockBlock.addView(hdrDate);
        header.addView(clockBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.VERTICAL);
        pills.setGravity(Gravity.END);
        pills.setClipChildren(false);

        LinearLayout row1 = pillRow();
        row1.addView(makePill("Wallpaper", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bgIdx = (bgIdx + 1) % BG_NAMES.length;
                prefs.edit().putInt("bg", bgIdx).apply();
                applyBackground(true);
            }
        }));
        designPill = makePill("Design: " + DESIGN_NAMES[design], new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                design = (design + 1) % 3;
                prefs.edit().putInt("design", design).apply();
                designPill.setText("Design: " + DESIGN_NAMES[design]);
                updateScrim();
                render(true, designPill);
            }
        });
        row1.addView(designPill);
        row1.addView(settingPill("Settings", Settings.ACTION_SETTINGS));
        pills.addView(row1);

        LinearLayout row2 = pillRow();
        ((LinearLayout.LayoutParams) row2.getLayoutParams()).topMargin = dp(8);
        row2.addView(settingPill("Wi-Fi", Settings.ACTION_WIFI_SETTINGS));
        row2.addView(settingPill("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS));
        row2.addView(settingPill("Display", Settings.ACTION_DISPLAY_SETTINGS));
        row2.addView(settingPill("Sound", Settings.ACTION_SOUND_SETTINGS));
        pills.addView(row2);

        header.addView(pills, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    private LinearLayout pillRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private TextView settingPill(String label, final String action) {
        return makePill(label, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSetting(action);
            }
        });
    }

    private TextView makePill(String label, View.OnClickListener click) {
        final TextView t = makeText(14, Color.WHITE, Typeface.BOLD);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        t.setFocusable(true);
        t.setClickable(true);
        stylePill(t, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        t.setLayoutParams(lp);
        attachFocus(t, 1.08f, 0, new Styler() {
            @Override
            public void apply(boolean f) {
                stylePill(t, f);
            }
        });
        t.setOnClickListener(click);
        return t;
    }

    private void stylePill(TextView t, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(focused ? Color.WHITE : 0x40000000);
        g.setStroke(dp(1), focused ? Color.WHITE : 0x55FFFFFF);
        t.setBackground(g);
        t.setTextColor(focused ? 0xFF111111 : Color.WHITE);
        t.setShadowLayer(focused ? 0 : dp(6), 0, dp(1), focused ? 0 : 0x66000000);
    }

    private void openSetting(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e2) {
                Toast.makeText(this, "Settings not available", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ------------------------------------------------------------ background

    /** Decodes the wallpaper off the UI thread, then cross-fades it in. */
    private void applyBackground(final boolean animate) {
        updateScrim();
        final int id = resId("bg_" + BG_NAMES[bgIdx]);
        if (id == 0) return;
        final int token = ++bgToken;
        final int targetW = getResources().getDisplayMetrics().widthPixels;
        try {
            io.execute(new Runnable() {
                @Override
                public void run() {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeResource(getResources(), id, bounds);
                    int sample = 1;
                    while (bounds.outWidth / (sample * 2) >= targetW) sample *= 2;
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = sample;
                    final Bitmap bmp = BitmapFactory.decodeResource(getResources(), id, o);
                    if (bmp == null) return;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (token != bgToken || isFinishing()) return;
                            showBackground(new BitmapDrawable(getResources(), bmp), animate);
                        }
                    });
                }
            });
        } catch (Exception ignored) {
            // executor already shut down
        }
    }

    private void showBackground(Drawable next, boolean animate) {
        Drawable old = bgView.getDrawable();
        if (old instanceof TransitionDrawable) old = ((TransitionDrawable) old).getDrawable(1);
        if (!animate || old == null) {
            bgView.setImageDrawable(next);
            if (!animate) {
                bgView.setAlpha(0f);
                bgView.animate().alpha(1f).setDuration(500).start();
            }
            return;
        }
        TransitionDrawable td = new TransitionDrawable(new Drawable[]{old, next});
        td.setCrossFadeEnabled(true);
        bgView.setImageDrawable(td);
        td.startTransition(550);
    }

    private void updateScrim() {
        boolean minimal = design == DESIGN_MINIMAL;
        int top = minimal ? 0x55000000 : 0x33000000;
        int mid = minimal ? 0x33000000 : 0x14000000;
        int bottom = minimal ? 0x99000000 : 0x77000000;
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, mid, bottom}));
    }

    // ------------------------------------------------------------ clock

    private void updateClock() {
        Date now = new Date();
        String t = new SimpleDateFormat("h:mm", Locale.getDefault()).format(now);
        String dt = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now);
        if (hdrTime != null) hdrTime.setText(t);
        if (hdrDate != null) hdrDate.setText(dt);
        if (minTime != null) minTime.setText(t);
        if (minDate != null) minDate.setText(dt);
    }

    // ------------------------------------------------------------ apps

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
        Collections.sort(rest, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });
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
        List<Item> items = queryItems();
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

        // Rows glide in one after another.
        DecelerateInterpolator decel = new DecelerateInterpolator(1.6f);
        for (int i = 0; i < body.getChildCount(); i++) {
            View row = body.getChildAt(i);
            row.setAlpha(0f);
            row.setTranslationY(dp(22));
            row.animate().alpha(1f).translationY(0f)
                    .setStartDelay(Math.min(i * 70L, 420L)).setDuration(380)
                    .setInterpolator(decel).start();
        }

        View target = focusTarget != null ? focusTarget : firstView;
        if (target != null) target.requestFocus();
    }

    private int usableWidth() {
        return getResources().getDisplayMetrics().widthPixels - 2 * dp(64);
    }

    // ---- grid (Glass and App Icons)

    private View renderGrid(List<Item> items, boolean glass) {
        int cols = 5;
        int gap = dp(14);
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
        int h = Math.round(w * 0.56f);
        final FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setClipToOutline(true);
        tile.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        styleGlass(tile, false);

        Drawable banner = it.def == null ? it.ri.activityInfo.loadBanner(getPackageManager()) : null;
        if (banner != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            inner.addView(logoOrIcon(it, Math.round(w * 0.62f), Math.round(h * 0.34f),
                    T_WHITE, 0, Math.round(h * 0.40f)));
            TextView name = makeText(14, Color.WHITE, Typeface.NORMAL);
            name.setText(it.label);
            name.setSingleLine(true);
            name.setGravity(Gravity.CENTER);
            name.setPadding(dp(6), dp(6), dp(6), 0);
            inner.addView(name);
            tile.addView(inner, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        attachFocus(tile, 1.08f, 14, new Styler() {
            @Override
            public void apply(boolean f) {
                styleGlass(tile, f);
            }
        });
        attachActions(tile, it);
        return tile;
    }

    private void styleGlass(View v, boolean focused) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                focused ? new int[]{0x77FFFFFF, 0x4DFFFFFF} : new int[]{0x33FFFFFF, 0x18FFFFFF});
        g.setCornerRadius(dp(14));
        g.setStroke(dp(focused ? 2 : 1), focused ? 0xEEFFFFFF : 0x66FFFFFF);
        v.setBackground(g);
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
        styleIconTile(tile, it, false);
        cell.addView(tile, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

        Drawable banner = it.def == null ? it.ri.activityInfo.loadBanner(getPackageManager()) : null;
        if (banner != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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

        TextView name = makeText(13, Color.WHITE, Typeface.NORMAL);
        name.setText(it.label);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(6);
        cell.addView(name, np);

        attachFocus(cell, 1.1f, 14, new Styler() {
            @Override
            public void apply(boolean f) {
                styleIconTile(tile, it, f);
            }
        });
        attachActions(cell, it);
        return cell;
    }

    private void styleIconTile(View tile, Item it, boolean focused) {
        int[] colors = it.def != null
                ? new int[]{it.def.c1, it.def.c2}
                : new int[]{0xFF2E2E34, 0xFF141418};
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        g.setCornerRadius(dp(14));
        if (focused) g.setStroke(dp(3), Color.WHITE);
        tile.setBackground(g);
    }

    // ---- minimal (big clock + logo dock)

    private View renderMinimal(List<Item> items) {
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER_HORIZONTAL);
        minTime = makeText(104, Color.WHITE, Typeface.NORMAL);
        minTime.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        minTime.setGravity(Gravity.CENTER);
        minDate = makeText(20, 0xDDFFFFFF, Typeface.NORMAL);
        minDate.setGravity(Gravity.CENTER);
        clock.addView(minTime);
        clock.addView(minDate);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(24);
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
        int itemW = Math.max(dp(70), Math.min(dp(108), usableWidth() / n - dp(12)));
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
        hp.topMargin = dp(120);
        body.addView(hsv, hp);
        return first;
    }

    private View dockItem(final Item it, int w) {
        final FrameLayout cell = new FrameLayout(this);
        cell.setFocusable(true);
        cell.setClickable(true);
        styleDock(cell, false);
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

        attachFocus(cell, 1.18f, 14, new Styler() {
            @Override
            public void apply(boolean f) {
                styleDock(cell, f);
            }
        });
        attachActions(cell, it);
        return cell;
    }

    private void styleDock(View v, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(focused ? 0x44FFFFFF : 0x00000000);
        if (focused) g.setStroke(dp(2), 0xCCFFFFFF);
        v.setBackground(g);
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

    private void attachFocus(final View v, final float scale, final int elevDp, final Styler styler) {
        final OvershootInterpolator bounce = new OvershootInterpolator(1.6f);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                float s = hasFocus ? scale : 1f;
                view.animate().setStartDelay(0).scaleX(s).scaleY(s)
                        .setDuration(hasFocus ? 240 : 160).setInterpolator(bounce).start();
                view.setElevation(hasFocus ? dp(elevDp) : 0);
                styler.apply(hasFocus);
            }
        });
    }

    private void attachActions(View v, final Item it) {
        final PackageManager pm = getPackageManager();
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent launch = pm.getLeanbackLaunchIntentForPackage(it.pkg);
                if (launch == null) launch = pm.getLaunchIntentForPackage(it.pkg);
                if (launch == null) return;
                try {
                    startActivity(launch);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Can't open this app", Toast.LENGTH_SHORT).show();
                }
            }
        });
        // Long-press: app info page (uninstall / disable / clear data).
        v.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + it.pkg)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "App info not available", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
    }

    private TextView makeText(int sp, int color, int style) {
        TextView tv = new TextView(this);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT, style);
        tv.setShadowLayer(dp(6), 0, dp(1), 0x66000000);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
