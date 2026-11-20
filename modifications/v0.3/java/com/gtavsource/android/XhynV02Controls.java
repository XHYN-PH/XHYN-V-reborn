package com.gtavsource.android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

/** Optional guides for the existing touch editor and a live controller input check. */
final class XhynV02Controls {
    private static final int GRID = 20;
    private static final WeakHashMap<View, Guides> OPTIONS = new WeakHashMap<>();

    private static final class Guides {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean snap;
        boolean edges;
    }

    static void editorTools(Context context, LinearLayout toolbar, View canvas) {
        SharedPreferences settings = context.getSharedPreferences("xhyn-editor-guides", 0);
        Guides guides = new Guides();
        guides.snap = settings.getBoolean("snap", false);
        guides.edges = settings.getBoolean("edges", false);
        OPTIONS.put(canvas, guides);
        toolbar.addView(MenuStyle.icon(context, android.R.drawable.ic_menu_crop, "Alignment and screen guides", () -> {
            LinearLayout panel = MenuStyle.column(context);
            panel.addView(MenuStyle.text(context, "Use Scene preview in the editor to check how controls appear on foot and while driving.", 14, 0xfff2f2f0));
            MenuStyle.toggle(panel, "Snap controls to grid", "Applies when you drag a button", guides.snap, enabled -> {
                guides.snap = enabled;
                settings.edit().putBoolean("snap", enabled).apply();
                canvas.invalidate();
            });
            MenuStyle.toggle(panel, "Show safe-area guides", "Marks screen edges and display cutouts", guides.edges, enabled -> {
                guides.edges = enabled;
                settings.edit().putBoolean("edges", enabled).apply();
                canvas.invalidate();
            });
            new MenuDialogBuilder(context).setTitle("Layout guides").setView(panel).setPositiveButton("Done", null).show();
        }));
    }

    static float snap(View canvas, float value) {
        Guides guides = OPTIONS.get(canvas);
        if (guides == null || !guides.snap || !Float.isFinite(value)) return value;
        return Math.round(value * GRID) / (float)GRID;
    }

    static void drawGuides(View view, Canvas canvas) {
        Guides guides = OPTIONS.get(view);
        if (guides == null) return;
        int width = view.getWidth(), height = view.getHeight();
        Paint paint = guides.paint;
        paint.setStyle(Paint.Style.STROKE);
        if (guides.snap) {
            paint.setColor(0x38ffffff);
            paint.setStrokeWidth(1f);
            for (int step = 1; step < GRID; step++) {
                float x = width * (step / (float)GRID);
                float y = height * (step / (float)GRID);
                canvas.drawLine(x, 0, x, height, paint);
                canvas.drawLine(0, y, width, y, paint);
            }
        }
        if (guides.edges) {
            WindowInsets window = view.getRootWindowInsets();
            Insets insets = window == null ? Insets.NONE
                    : window.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int margin = MenuStyle.dp(view.getContext(), 8);
            paint.setColor(0xc2bfb8ff);
            paint.setStrokeWidth(2f);
            canvas.drawRect(Math.max(margin, insets.left), Math.max(margin, insets.top),
                    width - Math.max(margin, insets.right), height - Math.max(margin, insets.bottom), paint);
        }
    }

    static void appendControllerTest(GameMenuActivity activity) {
        if (activity.content.findViewWithTag("xhyn-controller-test") != null) return;
        LinearLayout row = MenuStyle.detailRow(activity.content, "Controller test", "Check buttons, sticks and dead zones");
        row.setTag("xhyn-controller-test");
        Runnable action = () -> testController(activity);
        row.setOnClickListener(view -> action.run());
        row.addView(MenuStyle.icon(activity, android.R.drawable.ic_menu_more, "Open controller test", action));
    }

    private static void testController(GameMenuActivity activity) {
        LinearLayout panel = MenuStyle.column(activity);
        int padding = MenuStyle.dp(activity, 16);
        panel.setPadding(padding, padding, padding, padding);
        TextView devices = MenuStyle.text(activity, devices(), 15, 0xfff2f2f0);
        TextView inputs = MenuStyle.text(activity, "Move a stick or press a controller button.", 15, 0xfff2f2f0);
        TextView label = MenuStyle.text(activity, "Test dead zone · 15%", 14, 0xffb3b5b9);
        SeekBar deadZone = new SeekBar(activity);
        deadZone.setMax(50);
        deadZone.setProgress(15);
        panel.addView(devices);
        panel.addView(inputs);
        panel.addView(label);
        panel.addView(deadZone);
        panel.addView(MenuStyle.text(activity, "This slider only affects the test display.", 13, 0xffb3b5b9));
        deadZone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                label.setText("Test dead zone · " + value + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { panel.requestFocus(); }
        });
        panel.setFocusableInTouchMode(true);
        panel.setOnGenericMotionListener((view, event) -> {
            if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
            devices.setText(devices());
            float cutoff = deadZone.getProgress() / 100f;
            inputs.setText(String.format(Locale.ROOT, "Left %.2f / %.2f   Right %.2f / %.2f",
                    filtered(event, MotionEvent.AXIS_X, cutoff), filtered(event, MotionEvent.AXIS_Y, cutoff),
                    filtered(event, MotionEvent.AXIS_Z, cutoff), filtered(event, MotionEvent.AXIS_RZ, cutoff)));
            return true;
        });
        AlertDialog dialog = new MenuDialogBuilder(activity).setTitle("Controller test")
                .setView(panel).setPositiveButton("Done", null).create();
        dialog.setOnKeyListener((owner, code, event) -> {
            if (!KeyEvent.isGamepadButton(code) || code == KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                devices.setText(devices());
                inputs.setText(KeyEvent.keyCodeToString(code).replace("KEYCODE_", "").replace('_', ' '));
            }
            return true;
        });
        dialog.show();
        panel.requestFocus();
    }

    private static String devices() {
        List<String> names = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && (device.supportsSource(InputDevice.SOURCE_GAMEPAD)
                    || device.supportsSource(InputDevice.SOURCE_JOYSTICK))) names.add(device.getName());
        }
        return names.isEmpty() ? "No controller detected" : String.join("\n", names);
    }

    private static float filtered(MotionEvent event, int axis, float cutoff) {
        float value = event.getAxisValue(axis);
        return Math.abs(value) < cutoff ? 0 : value;
    }

    private XhynV02Controls() { }
}
