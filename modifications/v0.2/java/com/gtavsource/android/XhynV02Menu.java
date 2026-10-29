package com.gtavsource.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Project information and an About action alongside the System heading. */
public final class XhynV02Menu {
    public static final String TELEGRAM = "https://t.me/xhynv";
    public static final String YOUTUBE = "https://youtube.com/@xhyn_ph";
    public static final String REPOSITORY = "https://github.com/XHYN-PH/XHYN-V-reborn";
    private static final String MARKER = "xhyn-system-about-row";

    public static void decorate(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View root = activity.getWindow().getDecorView();
        root.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed() || root.findViewWithTag(MARKER) != null) return;
            TextView system = heading(root, 0);
            if (system == null || !(system.getParent() instanceof ViewGroup)) return;
            ViewGroup parent = (ViewGroup) system.getParent();
            int index = parent.indexOfChild(system);
            ViewGroup.LayoutParams previous = system.getLayoutParams();
            parent.removeView(system);
            LinearLayout row = new LinearLayout(activity);
            row.setTag(MARKER);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.addView(system, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button about = new Button(activity);
            about.setText("About");
            about.setAllCaps(false);
            about.setTextColor(0xffd9bcff);
            about.setTextSize(12);
            about.setMinWidth(dp(activity, 64));
            about.setMinimumWidth(dp(activity, 64));
            about.setOnClickListener(view -> about(activity));
            row.addView(about, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 42)));
            parent.addView(row, index, previous);
            hideDuplicateAbout(root, about, 0);
        });
    }

    private static TextView heading(View view, int depth) {
        if (depth > 24) return null;
        if (view instanceof TextView && !(view instanceof Button)
                && "System".contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = heading(group.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void hideDuplicateAbout(View view, Button keep, int depth) {
        if (depth > 24 || view == keep) return;
        if (view instanceof TextView && view.isClickable()
                && "About".contentEquals(((TextView) view).getText())) view.setVisibility(View.GONE);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) hideDuplicateAbout(group.getChildAt(i), keep, depth + 1);
        }
    }

    public static void about(Activity activity) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 20);
        content.setPadding(padding, padding, padding, padding);
        TextView description = new TextView(activity);
        description.setText("XHYN V 0.2\nMaintained by XHYN\n\n"
                + "GTA V for Android, with touch controls, graphics and driver settings, and a Story Mode trainer. "
                + "The game engine runs as ARM64 Android code; this edition uses DXVK for graphics translation.\n\n"
                + "Requires your own compatible GTA V Legacy game files. Performance and compatibility depend on your device, driver, and settings.");
        description.setTextColor(0xffeeeeee);
        description.setTextSize(15);
        content.addView(description);
        link(activity, content, "Telegram · XHYN V", TELEGRAM);
        link(activity, content, "YouTube · @xhyn_ph", YOUTUBE);
        link(activity, content, "GitHub · XHYN V", REPOSITORY);
        link(activity, content, "Buy GTA V", "https://store.rockstargames.com/game/buy-gta-v");
        link(activity, content, "Credits and third-party notices", REPOSITORY + "/blob/main/docs/CREDITS.md");
        TextView notice = new TextView(activity);
        notice.setText("Unofficial project. GTA V and its assets belong to their respective owners. Original engine, port, and third-party credits remain applicable.");
        notice.setTextColor(0xffaaaaaa);
        notice.setTextSize(12);
        content.addView(notice);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        new AlertDialog.Builder(activity).setTitle("About XHYN V").setView(scroll).setPositiveButton("Close", null).show();
    }

    private static void link(Activity activity, LinearLayout parent, String title, String url) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(title);
        button.setOnClickListener(view -> {
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (ActivityNotFoundException failure) { Toast.makeText(activity, "No app can open " + url, Toast.LENGTH_LONG).show(); }
        });
        parent.addView(button);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
    private XhynV02Menu() {}
}
