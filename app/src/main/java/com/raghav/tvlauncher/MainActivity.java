package com.raghav.tvlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    // Apps shown first, in this order (skipped if not installed).
    private static final String[] FAVORITES = {
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.google.android.youtube.tv",
            "in.startv.hotstar.dplus",
            "in.startv.hotstar",
            "com.jio.media.stb.ondemand",
    };

    private TextView greetView;
    private TextView timeView;
    private TextView dateView;
    private LinearLayout row;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF8B0A18, 0xFF1A0508, 0xFF000000}));
        root.setPadding(dp(64), dp(48), dp(64), dp(24));

        greetView = makeText(26, 0xCCFFFFFF, Typeface.NORMAL);
        timeView = makeText(88, Color.WHITE, Typeface.BOLD);
        dateView = makeText(22, 0xAAFFFFFF, Typeface.NORMAL);
        root.addView(greetView);
        root.addView(timeView);
        root.addView(dateView);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);

        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(dp(12), dp(28), dp(12), dp(36));
        scroll.addView(row);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        populate();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onBackPressed() {
        // This is the home screen: Back does nothing.
    }

    private void updateClock() {
        Date now = new Date();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greet = hour < 12 ? "Good morning" : (hour < 17 ? "Good afternoon" : "Good evening");
        greetView.setText(greet);
        timeView.setText(new SimpleDateFormat("h:mm a", Locale.getDefault()).format(now));
        dateView.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now));
    }

    private void populate() {
        final PackageManager pm = getPackageManager();

        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : categories) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!pkg.equals(getPackageName()) && !found.containsKey(pkg)) {
                    found.put(pkg, ri);
                }
            }
        }

        List<ResolveInfo> ordered = new ArrayList<>();
        for (String fav : FAVORITES) {
            ResolveInfo ri = found.remove(fav);
            if (ri != null) ordered.add(ri);
        }
        List<ResolveInfo> rest = new ArrayList<>(found.values());
        Collections.sort(rest, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });
        ordered.addAll(rest);

        row.removeAllViews();
        for (ResolveInfo ri : ordered) {
            row.addView(makeAppTile(pm, ri));
        }
        row.addView(makeSettingsTile());

        if (row.getChildCount() > 0) {
            row.getChildAt(0).requestFocus();
        }
    }

    private View makeAppTile(final PackageManager pm, final ResolveInfo ri) {
        final String pkg = ri.activityInfo.packageName;
        String label = ri.loadLabel(pm).toString();

        FrameLayout tile = newTile();

        Drawable banner = ri.activityInfo.loadBanner(pm);
        if (banner != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(ri.loadIcon(pm));
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(64), dp(64));
            ip.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
            ip.topMargin = dp(22);
            tile.addView(icon, ip);

            TextView tv = makeText(16, Color.WHITE, Typeface.NORMAL);
            tv.setText(label);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.gravity = Gravity.BOTTOM;
            tp.bottomMargin = dp(14);
            tile.addView(tv, tp);
        }

        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
                if (launch != null) startActivity(launch);
            }
        });
        return tile;
    }

    private View makeSettingsTile() {
        FrameLayout tile = newTile();
        TextView tv = makeText(22, Color.WHITE, Typeface.BOLD);
        tv.setText("Settings");
        tv.setGravity(Gravity.CENTER);
        tile.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        return tile;
    }

    private FrameLayout newTile() {
        final FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setClipToOutline(true);
        styleTile(tile, false);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(240), dp(135));
        lp.rightMargin = dp(20);
        tile.setLayoutParams(lp);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.12f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(120).start();
                v.setElevation(hasFocus ? dp(12) : 0);
                styleTile(tile, hasFocus);
            }
        });
        return tile;
    }

    private void styleTile(View tile, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF262626);
        bg.setCornerRadius(dp(14));
        if (focused) bg.setStroke(dp(3), Color.WHITE);
        tile.setBackground(bg);
    }

    private TextView makeText(int sp, int color, int style) {
        TextView tv = new TextView(this);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT, style);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
