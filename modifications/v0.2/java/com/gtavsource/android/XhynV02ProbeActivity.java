package com.gtavsource.android;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Separate process: a hung/crashing Vulkan driver cannot terminate the launcher. */
public final class XhynV02ProbeActivity extends Activity implements SurfaceHolder.Callback {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean started, completed;
    private RuntimeConfig cfg;
    private String requestKey;
    private long start;
    private JSONObject pending;
    private final Runnable timeout = () -> finishResult("TIMEOUT", "The selected driver did not finish within 20 seconds. The probe process was stopped.");

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(20,20,20,20);
        TextView description = new TextView(this); description.setText("Checking the selected Vulkan driver…\nA triangle will be drawn and presented twice. This tests basic graphics initialization, not game compatibility."); body.addView(description);
        SurfaceView surface = new SurfaceView(this); body.addView(surface, new LinearLayout.LayoutParams(480,270)); surface.getHolder().addCallback(this);
        Button cancel = new Button(this); cancel.setText("Cancel check"); cancel.setOnClickListener(v -> finishResult("CANCELLED", "Cancelled by user")); body.addView(cancel); setContentView(body);
        cfg = XhynV02Runtime.config(); start = System.currentTimeMillis();
        try { requestKey = XhynV02Runtime.driverKey(cfg); } catch (Exception failure) { finishResult("FAILED", failure.getMessage()); return; }
        // Record a pending attempt before loading third-party code. A native crash leaves this honest state.
        try {
            XhynV02Runtime.beforeDriver(this, cfg);
            pending = new JSONObject().put("requestedDriverKey", requestKey).put("fingerprint", Build.FINGERPRINT)
                    .put("bcnMode", cfg.get("vulkan", "bcn_decode_mode", "auto")).put("time_ms", start)
                    .put("result", new JSONObject().put("status", "PENDING").put("error", "Probe did not return; a driver crash or process exit is possible"));
            XhynV02Config.write(XhynV02Runtime.probeFile(this), pending.toString(2));
        } catch (Exception failure) { finishResult("FAILED", failure.getMessage()); return; }
        handler.postDelayed(timeout, 20000);
    }
    @Override public void surfaceCreated(SurfaceHolder holder) {
        if (started || completed) return; started = true;
        new Thread(() -> {
            try {
                if ("custom".equals(cfg.get("vulkan", "driver", "system"))) XhynV02DriverPackage.validate(new java.io.File(cfg.drivers, cfg.get("vulkan", "driver_zip", "")), Build.VERSION.SDK_INT, android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE));
                XhynV02Runtime.beforeDriver(this, cfg); DriverManager.prepareDiagnostics(this, cfg);
                JSONObject result = new JSONObject(XhynV02Native.probe(holder.getSurface()));
                String status = result.optString("status");
                int vendor = result.optInt("vendorId"), driver = result.optInt("driverId");
                if ((driver == 18 && vendor != 0x5143) || (driver == 20 && vendor != 0x13b5)) {
                    result.put("status", "FAILED").put("error", "Driver family does not match the reported GPU"); status = "FAILED";
                }
                if ("custom".equals(cfg.get("vulkan", "driver", "system")) && DriverManager.currentStatus().toLowerCase(java.util.Locale.ROOT).contains("fallback"))
                    result.put("status", "FAILED").put("error", "The requested custom package fell back to System Vulkan");
                final JSONObject output = result;
                handler.post(() -> finishNative(output));
            } catch (Throwable failure) { handler.post(() -> finishResult("FAILED", failure.toString())); }
        }, "xhyn-vulkan-probe").start();
    }
    private void finishNative(JSONObject result) {
        if (completed) return;
        try {
            if (pending == null) pending = new JSONObject();
            pending.put("result", result).put("elapsed_ms", System.currentTimeMillis() - start)
                    .put("requestedDriverKey", requestKey == null ? "" : requestKey).put("fingerprint", Build.FINGERPRINT)
                    .put("bcnMode", cfg == null ? "auto" : cfg.get("vulkan", "bcn_decode_mode", "auto"));
            XhynV02Config.write(XhynV02Runtime.probeFile(this), pending.toString(2));
            setResult(RESULT_OK, new Intent().putExtra("status", result.optString("status")));
        } catch (Exception failure) { setResult(RESULT_CANCELED); }
        completed = true; handler.removeCallbacks(timeout); finish();
        // Every check gets a new driver loader and process. No renderer leaks into the next check.
        handler.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 600);
    }
    private void finishResult(String status, String error) {
        try { finishNative(new JSONObject().put("status", status).put("error", error == null ? "Unknown error" : error)); }
        catch (Exception failure) { android.os.Process.killProcess(android.os.Process.myPid()); }
    }
    @Override public void surfaceChanged(SurfaceHolder h, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder h) { if (!completed) finishResult("CANCELLED", "Test surface was closed"); }
    @Override public void onBackPressed() { finishResult("CANCELLED", "Cancelled by user"); }
}
